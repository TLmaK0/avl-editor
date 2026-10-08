/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim.CRRCSim
import com.abajar.avleditor.avl.runcase.AvlCalculation
import com.abajar.avleditor.ac3d.AC3DWriter
import com.abajar.avleditor.xfoil.StallExtension
import java.io.{File, PrintWriter}
import JsbsimWriter._

/**
 * Assembles a FlightGear-flyable aircraft package so the exported JSBSim model can
 * be piloted with a 3D view:
 * {{{
 *   <root>/<name>/
 *     <name>-set.xml     top-level FlightGear config (fdm = jsb, FlightGear's name for JSBSim)
 *     <name>.xml         the JSBSim FDM
 *     Engines/           motor and propeller files (if any)
 *     Models/<name>.ac   AC3D visual mesh from the AVL geometry
 * }}}
 * Fly with:  `fgfs --fg-aircraft=<root> --aircraft=<name>`
 *
 * `/sim/model/path` is resolved by FlightGear relative to the aircraft's own directory,
 * so it stays a bare `Models/<name>.ac`. Prefixing it with the package name makes
 * FlightGear log "Failed to find aircraft model" and fall back to glider.ac.
 *
 * The package also carries a `Huds/hud.xml` (see [[hudXml]]): the generated model has no
 * cockpit and is flown from the external view (see [[ChaseView]] below), so there is
 * otherwise nothing on screen to read while flying — no airspeed, no altitude, no throttle
 * position, no attitude. Issue #20.
 */
object FlightGearExporter {

  def export(rootDir: File, name: String, crrcsim: CRRCSim, calc: AvlCalculation,
            stallExtension: Option[StallExtension] = None): Unit = {
    val ac = JsbsimExporter.buildAircraft(name, crrcsim, calc, stallExtension)
    val model = generate(ac)
    val dir = new File(rootDir, name)

    write(new File(dir, s"$name.xml"), model.aircraftXml)
    model.engineFiles.foreach { case (fn, content) => write(new File(dir, s"Engines/$fn"), content) }
    val geo = crrcsim.getAvl.getGeometry
    write(new File(dir, s"Models/$name.ac"), AC3DWriter.fromGeometry(geo))
    write(new File(dir, s"Huds/hud.xml"), hudXml(name))
    write(new File(dir, s"$name-set.xml"), setXml(name, AC3DWriter.boundsFromGeometry(geo)))
  }

  /** FlightGear's identifier for the JSBSim FDM. Not "jsbsim": anything outside FlightGear's
    * table (jsb, yasim, larcsim, ufo, magic, balloon, external, null) is rejected at startup
    * with "Unrecognized flight model '...', cannot init flight dynamics model". */
  private[jsbsim] val FlightModel = "jsb"

  /**
   * FlightGear's external "Chase View". Generated models have no cockpit interior, so starting
   * in the default cockpit view (0) puts the camera inside the airframe and shows an empty
   * scene — the aircraft appears to be missing. Passing this on the command line does not work:
   * `--prop:` is applied before FlightGear builds its views.
   */
  private[jsbsim] val ChaseView = 2

  /**
   * The Nasal module is emitted as a sibling of `<sim>`, not inside it: aircraft Nasal lives at
   * `/nasal`, and nested under `<sim>` the module is silently never loaded — the script simply
   * does not run, with nothing in the log to say so.
   */
  private[jsbsim] val NasalModule = "avleditor"

  /**
   * Camera distance derived from the mesh's own bounding box. FlightGear's default chase
   * distance is 25 m, which turns a 2 m model into a speck; stock aircraft are ten times
   * bigger. Offsets are in the model frame: +x right, +y up, +z aft.
   *
   * Note: this does NOT fix "the aircraft is not visible in FlightGear" — see PLAN.md. With a
   * stock FlightGear model substituted into this package the camera stays glued to the
   * aircraft too, so the cause is elsewhere in the package, not in the mesh or this distance.
   */
  private[jsbsim] final case class ViewGeometry(eyeUp: Double, eyeAft: Double, chaseDistance: Double)

  private[jsbsim] def viewGeometry(
      bounds: Option[((Float, Float, Float), (Float, Float, Float))]): ViewGeometry =
    bounds match {
      case Some(((_, minUp, minAft), (_, maxUp, maxAft))) =>
        val length = math.max(maxAft - minAft, 0.1)
        val height = math.max(maxUp - minUp, 0.05)
        // Eye just above the top of the airframe, a quarter of the length behind the nose.
        ViewGeometry(maxUp + 0.1 * height, minAft + 0.25 * length, math.max(6.0, 5.0 * length))
      case None =>
        ViewGeometry(0.5, 0.0, 10.0)
    }

  /**
   * One line of the HUD: a fixed label, the FlightGear property it reads and a printf-style
   * `format` for the value. The properties are FlightGear's own generic ones — the same for
   * every FDM, `jsb` included — never anything this export invents: `/velocities/airspeed-kt`,
   * `/position/altitude-ft`, `/orientation/{roll,pitch}-deg` are populated by FlightGear itself
   * from the FDM's state, and `/controls/engines/engine[0]/throttle` is the pilot's own throttle
   * command (0..1), set by the same input that drives the JSBSim FCS.
   */
  private[jsbsim] final case class HudReadout(property: String, format: String, y: Int)

  /** Airspeed, altitude, throttle and attitude — the four things issue #20 asks to read while
    * flying, in that order, each a plain number rather than a tape or a ladder: there is no
    * cockpit to frame them in, so the simplest readout that cannot be misread is the right one. */
  private[jsbsim] val HudReadouts: Seq[HudReadout] = Seq(
    HudReadout("/velocities/airspeed-kt", "IAS %5.1f kt", 700),
    HudReadout("/position/altitude-ft", "ALT %6.1f ft", 670),
    HudReadout("/controls/engines/engine[0]/throttle", "THR %5.0f %%", 640),
    HudReadout("/orientation/pitch-deg", "PITCH %5.1f deg", 610),
    HudReadout("/orientation/roll-deg", "ROLL  %5.1f deg", 580)
  )

  /**
   * A FlightGear classic 2D HUD definition (the mechanism behind the 'h' key on every stock
   * aircraft since the 1990s): plain text objects, each bound to one property via a printf
   * `format`, drawn over whichever view is active — unlike a 3D panel, it does not require a
   * cockpit. `enable3d-hud` is left false because the generated model has no instrument panel
   * geometry for a 3D HUD to project onto.
   */
  private[jsbsim] def hudXml(name: String): String = {
    val objects = HudReadouts.map { r =>
      f"""  <object>
         |    <type>text</type>
         |    <x>20</x>
         |    <y>${r.y}</y>
         |    <width>220</width>
         |    <height>26</height>
         |    <justify>left</justify>
         |    <point-size>14</point-size>
         |    <format>${r.format}</format>
         |    <property>${r.property}</property>
         |  </object>"""
    }.mkString("\n")
    s"""<?xml version="1.0"?>
       |<PropertyList>
       |  <name>$name HUD</name>
       |  <x-start>0</x-start>
       |  <y-start>0</y-start>
       |  <x-end>1024</x-end>
       |  <y-end>768</y-end>
       |  <color>
       |    <red>0.1</red>
       |    <green>0.9</green>
       |    <blue>0.1</blue>
       |  </color>
       |  <line-width>1</line-width>
       |  <enable3d-hud>false</enable3d-hud>
       |$objects
       |</PropertyList>
       |""".stripMargin
  }

  private[jsbsim] def setXml(
      name: String,
      bounds: Option[((Float, Float, Float), (Float, Float, Float))] = None): String = {
    val view = viewGeometry(bounds)
    f"""<?xml version="1.0"?>
    |<PropertyList>
    |  <sim>
    |    <description>$name (generated by AVL Editor)</description>
    |    <author>AVL Editor</author>
    |    <flight-model>$FlightModel</flight-model>
    |    <aero>$name</aero>
    |    <model>
    |      <path>Models/$name.ac</path>
    |    </model>
    |    <chase-distance-m archive="y">${-view.chaseDistance}%.3f</chase-distance-m>
    |    <hud>
    |      <path>Huds/hud.xml</path>
    |      <visibility archive="y">true</visibility>
    |      <enable3d archive="y">false</enable3d>
    |    </hud>
    |  </sim>
    |  <nasal>
    |    <$NasalModule>
    |      <script><![CDATA[
    |        # Select an external view once FlightGear has built its views. Setting
    |        # /sim/current-view/view-number in the property tree instead is applied too early:
    |        # FlightGear reports "Invalid /sim/current-view/view-number, views.size()=0" and
    |        # falls back to view 0, the cockpit, which shows nothing for a model with no interior.
    |        #
    |        # CDATA because Nasal uses characters XML would otherwise parse as markup.
    |        setlistener("/sim/signals/fdm-initialized", func {
    |          setprop("/sim/current-view/view-number", $ChaseView);
    |          # The HUD defaults to on, but is user-togglable ('h') and this model has no
    |          # cockpit to fall back to, so the readout is forced on rather than assumed.
    |          setprop("/sim/hud/visibility[0]", 1);
    |        });
    |      ]]></script>
    |    </$NasalModule>
    |  </nasal>
    |</PropertyList>
    |""".stripMargin
  }

  private def write(f: File, content: String): Unit = {
    Option(f.getParentFile).foreach(_.mkdirs())
    val pw = new PrintWriter(f)
    try pw.write(content) finally pw.close()
  }
}
