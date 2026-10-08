/*
 * Validates the *content* of the generated FlightGear HUD (issue #20: the exported package has
 * no cockpit and no readout, so there is nothing to compare a flight against). A well-formed
 * Huds/hud.xml that FlightGear's HUD parser rejects is as useless as none at all, and would
 * look identical to success from this project's build.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.FlightGearHudCheck"
 */
package com.abajar.avleditor.jsbsim

import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream

object FlightGearHudCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def parses(xml: String): Boolean =
    try {
      DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(new ByteArrayInputStream(xml.getBytes("UTF-8")))
      true
    } catch { case _: Throwable => false }

  private def textsOf(xml: String, tag: String): Seq[String] = {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
      .parse(new ByteArrayInputStream(xml.getBytes("UTF-8")))
    val nodes = doc.getElementsByTagName(tag)
    (0 until nodes.getLength).map(nodes.item(_).getTextContent)
  }

  def main(args: Array[String]): Unit = {
    val name = "eurofighter"
    val hud = FlightGearExporter.hudXml(name)

    check("Huds/hud.xml is well-formed", parses(hud))

    val properties = textsOf(hud, "property")
    val formats = textsOf(hud, "format")
    val objectCount = textsOf(hud, "type").count(_ == "text")

    // The four things issue #20 asks to read while flying: airspeed, altitude, throttle,
    // attitude (pitch and roll). Never anything the export invents: every one of these is a
    // generic FlightGear property, populated by FlightGear itself from the FDM's state, not
    // written by this exporter.
    check("one object per declared readout", objectCount == FlightGearExporter.HudReadouts.size)
    check("reads indicated airspeed", properties.contains("/velocities/airspeed-kt"))
    check("reads altitude", properties.contains("/position/altitude-ft"))
    check("reads the pilot's own throttle command (not an invented one)",
      properties.contains("/controls/engines/engine[0]/throttle"))
    check("reads pitch attitude", properties.contains("/orientation/pitch-deg"))
    check("reads roll attitude", properties.contains("/orientation/roll-deg"))
    check("every property is a bare FlightGear path, not something this export made up",
      properties.forall(p => p.startsWith("/velocities/") || p.startsWith("/position/") ||
        p.startsWith("/controls/engines/") || p.startsWith("/orientation/")))
    check("every readout has a format string to pair with its property",
      formats.size == properties.size)
    // enable3d-hud is false: the generated model has no instrument panel geometry for a 3D HUD
    // to project onto, so a 3D HUD here would be configuring something that cannot exist.
    check("the HUD is 2D only (no 3D panel exists to project it onto)",
      hud.contains("<enable3d-hud>false</enable3d-hud>"))

    val setXml = FlightGearExporter.setXml(name)
    check("-set.xml is well-formed", parses(setXml))
    check("-set.xml points at the generated HUD", textsOf(setXml, "path").contains("Huds/hud.xml"))
    check("the HUD is forced visible — this model has no cockpit to fall back to if toggled off",
      setXml.contains("\"/sim/hud/visibility[0]\", 1"))
    check("-set.xml does not ask for a 3D HUD either",
      textsOf(setXml, "enable3d").contains("false"))

    println(if (ok) "FG_HUD_OK" else "FG_HUD_FAIL")
    if (!ok) sys.exit(1)
  }
}
