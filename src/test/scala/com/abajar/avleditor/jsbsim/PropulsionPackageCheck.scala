/*
 * Exports a FlightGear package for a model WITH propulsion — a path that had never been
 * exercised, so it shipped an engine element JSBSim does not know ("Unknown engine type",
 * FlightGear aborts on load) and referenced a thruster file.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.PropulsionPackageCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim.{CRRCSim, CRRCSimFactory, EngineData, Wheel}
import com.abajar.avleditor.avl.geometry.Control

object PropulsionPackageCheck {

  /**
   * Engine and thruster elements JSBSim accepts. `brushless_dc_motor` is in the list because it is
   * what the electric export writes and what JSBSim has read since January 2022; see the note on
   * [[JsbsimWriter.Motor]] for why it is that and not `electric_engine`, and for the cost.
   */
  private val EngineTypes = Set(
    "brushless_dc_motor", "electric_engine", "piston_engine", "turbine_engine",
    "turboprop_engine", "rocket_engine")
  private val ThrusterTypes = Set("propeller", "nozzle", "rotor", "direct")

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def rootElement(xml: String): String =
    """<([a-z_]+)[ >]""".r.findFirstMatchIn(xml.replaceAll("""<\?xml[^>]*\?>""", ""))
      .map(_.group(1)).getOrElse("")

  private def modelWithPropulsion(): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()
    val mi = crrcsim.getConfig.getMass_inertia
    mi.setMass(2.5f); mi.setI_xx(0.05f); mi.setI_yy(0.08f); mi.setI_zz(0.12f)

    val geo = crrcsim.getAvl.getGeometry
    geo.setSref(0.4f); geo.setBref(1.1f); geo.setCref(0.37f)
    geo.getSurfaces.clear()
    val section = geo.createSurface().createSection()
    section.getControls.clear()
    val control = new Control
    control.setType(1); control.setMaxDeflection(20f)
    section.getControls.add(control)

    crrcsim.getWheels.clear()
    Seq((0.2f, 0.0f), (0.8f, -0.3f), (0.8f, 0.3f)).zipWithIndex.foreach { case ((x, y), i) =>
      val w = new Wheel
      w.setName(s"GEAR$i")
      w.getPos.setX(x); w.getPos.setY(y); w.getPos.setZ(-0.05f)
      crrcsim.getWheels.add(w)
    }

    val power = crrcsim.getConfig.getPower
    power.getBateries.clear()
    val battery = power.createBattery()
    battery.setU_0(11.1f)
    battery.createShaft()
    val shaft = battery.getShafts.get(0)
    battery.setMass(0.45f); battery.getPos.setX(0.25f)
    val propeller = shaft.createPropeller()
    propeller.setD(0.25f); propeller.setMass(0.03f); propeller.getPos.setX(0.02f)
    val engine = shaft.createEngine()
    engine.setMass(0.18f); engine.getPos.setX(0.08f)
    Seq((11.1f, 18.0f, 9000f), (11.1f, 0.8f, 11000f)).foreach { case (u, i, rpm) =>
      val p = new EngineData
      p.setU_K(u); p.setI_M(i); p.setRpms(rpm)
      engine.getData.add(p)
    }
    val idle = engine.createDataIdle()
    idle.setU_K(11.1f); idle.setI_M(0.5f)
    crrcsim
  }

  def main(args: Array[String]): Unit = {
    val model = modelWithPropulsion()

    val unmet = SimulationRequirements.validate(model)
    if (unmet.nonEmpty) unmet.foreach(p => println(s"    unexpected: $p"))
    check("the model meets the requirements", unmet.isEmpty)

    val propulsion = JsbsimExporter.buildPropulsion(model)
    check("propulsion is built from the model", propulsion.isDefined)

    val generated = JsbsimWriter.generate(
      JsbsimWriter.Aircraft("check", JsbsimWriter.Metrics(0.4, 1.1, 0.37, JsbsimWriter.Vec3(0.68, 0, 0)),
        JsbsimWriter.MassBalance(2.5, 0.05, 0.08, 0.12, 0, JsbsimWriter.Vec3(0.68, 0, 0)),
        Nil, Nil,
        new JsbsimWriter.AeroDerivatives(0, 0, 0, 0, 0, 0.85, 5.0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        propulsion = propulsion))

    // Every file the aircraft XML references must exist in the package.
    val referenced = """(?:engine|thruster) file="([^"]+)"""".r
      .findAllMatchIn(generated.aircraftXml).map(_.group(1)).toSeq
    println(s"  referenced files: ${referenced.mkString(", ")}")
    val written = generated.engineFiles.map(_._1.replaceAll("\\.xml$", ""))
    println(s"  written files:    ${written.mkString(", ")}")
    check("both an engine and a thruster are referenced", referenced.length == 2)
    check("every referenced file is written", referenced.forall(written.contains))

    generated.engineFiles.foreach { case (fileName, content) =>
      val element = rootElement(content)
      val known = EngineTypes.contains(element) || ThrusterTypes.contains(element)
      println(s"  $fileName root element: <$element>")
      check(s"$fileName declares a type JSBSim knows", known)
    }

    // The engine element must be one this JSBSim knows, and carry a positive rating.
    val engineXml = generated.engineFiles.find(_._1.endsWith("_motor.xml")).map(_._2).getOrElse("")
    // A brushless motor is stated by its constants rather than by a power, so what is asserted is
    // the same property in the terms the element now uses: every figure in it traces to something
    // the model states, and none of them is invented here.
    def number(tag: String): Option[Double] =
      s"""<$tag>([0-9.eE+-]+)</$tag>""".r.findFirstMatchIn(engineXml).map(_.group(1).toDouble)
    val kv = number("velocityconstant")
    val i0 = number("noloadcurrent")
    val volts = number("maxvolts")
    println(s"  motor: Kv=${kv.getOrElse(0.0)} rpm/V, no-load=${i0.getOrElse(0.0)} A, " +
      s"maxvolts=${volts.getOrElse(0.0)} V")
    check("the motor is rated by its constants", kv.exists(_ > 0) && volts.exists(_ > 0))
    // 11,000 rpm at 11.1 V is the most unloaded point of the fixture's curve.
    check("Kv comes from the model's data curve",
      kv.exists(k => math.abs(k - 11000.0 / 11.1) < 0.5))
    // The idle row states 0.5 A, and the lightest loaded point states 0.8 A: reading the loaded one
    // as no-load is the defect this change fixes, so the check pins which of the two arrives.
    check("the no-load current comes from the idle row", i0.exists(c => math.abs(c - 0.5) < 1e-6))
    check("the pack voltage comes from the battery", volts.exists(v => math.abs(v - 11.1) < 1e-6))

    println(if (ok) "PROPULSION_PACKAGE_OK" else "PROPULSION_PACKAGE_FAIL")
    if (!ok) sys.exit(1)
  }
}
