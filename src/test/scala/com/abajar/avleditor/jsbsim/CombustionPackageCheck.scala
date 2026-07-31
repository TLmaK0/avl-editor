/*
 * Combustion propulsion: the piston engine and fuel tank a model states in metric units must come
 * out as the elements and units JSBSim's piston model reads, and a model missing any of them must
 * be refused rather than completed with invented figures.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.CombustionPackageCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim.{CRRCSim, CRRCSimFactory, Wheel}
import com.abajar.avleditor.avl.geometry.Control

object CombustionPackageCheck {

  /** Elements this JSBSim (FlightGear 2020.3) reads for a piston engine, verified in the binary. */
  private val PistonElements = Seq("displacement", "maxhp", "cycles", "idlerpm", "maxrpm", "bsfc")

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def mentions(problems: Seq[String], fragment: String): Boolean =
    problems.exists(_.toLowerCase.contains(fragment.toLowerCase))

  private def valueOf(xml: String, tag: String): Option[Double] =
    ("<" + tag + """(?: unit="[^"]*")?>([0-9.eE+-]+)</""" + tag + ">").r
      .findFirstMatchIn(xml).map(_.group(1).toDouble)

  /** A 10 cc two-stroke: 1.2 kW at 14000 rpm, 700 g/kWh, with a 0.5 kg tank. */
  private def pistonModel(): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()
    val mi = crrcsim.getConfig.getMass_inertia
    mi.setMass(4.0f); mi.setI_xx(0.08f); mi.setI_yy(0.12f); mi.setI_zz(0.18f)

    val geo = crrcsim.getAvl.getGeometry
    geo.setSref(0.5f); geo.setBref(1.6f); geo.setCref(0.32f)
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
    battery.setU_0(11.1f) // the ignition supply; the shaft carries no electric motor
    battery.createShaft()
    val shaft = battery.getShafts.get(0)
    shaft.createPropeller().setD(0.30f)
    val engine = shaft.createCombustionEngine()
    engine.setDisplacement(10.0f)
    engine.setMaxPower(1200.0f)
    engine.setIdleRpm(2500.0f)
    engine.setMaxRpm(14000.0f)
    engine.setCycles(2)
    engine.setFuelConsumption(700.0f)

    power.getFuelTanks.clear()
    val tank = power.createFuelTank()
    tank.setCapacity(0.5f)
    tank.setContents(0.5f)
    tank.getPos.setX(0.6f); tank.getPos.setY(0f); tank.getPos.setZ(0.02f)
    crrcsim
  }

  private def generate(model: CRRCSim) = JsbsimWriter.generate(
    JsbsimWriter.Aircraft("check",
      JsbsimWriter.Metrics(0.5, 1.6, 0.32, JsbsimWriter.Vec3(0.6, 0, 0)),
      JsbsimWriter.MassBalance(4.0, 0.08, 0.12, 0.18, 0, JsbsimWriter.Vec3(0.6, 0, 0)),
      Nil, Nil,
      new JsbsimWriter.AeroDerivatives(0, 0, 0, 0, 0, 0.85, 5.0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
      propulsion = JsbsimExporter.buildPropulsion(model)))

  def main(args: Array[String]): Unit = {
    val model = pistonModel()

    val unmet = SimulationRequirements.validate(model)
    if (unmet.nonEmpty) unmet.foreach(p => println(s"    unexpected: $p"))
    check("a complete combustion model validates", unmet.isEmpty)

    val propulsion = JsbsimExporter.buildPropulsion(model)
    check("it maps to a piston engine", propulsion.exists(_.motor match {
      case _: JsbsimWriter.PistonEngine => true
      case _ => false
    }))
    check("its fuel tank is carried over", propulsion.exists(_.tanks.length == 1))

    val generated = generate(model)
    val engineXml = generated.engineFiles.find(_._1.endsWith("_motor.xml")).map(_._2).getOrElse("")
    check("the engine element is <piston_engine>", engineXml.contains("<piston_engine"))
    PistonElements.foreach(tag =>
      check(s"it declares <$tag>", engineXml.contains("<" + tag + ">") || engineXml.contains("<" + tag + " ")))

    // Metric in, JSBSim's units out.
    val in3 = valueOf(engineXml, "displacement")
    println(f"  displacement: ${in3.getOrElse(0.0)}%.4f in3 (from 10 cm3)")
    check("displacement is converted to cubic inches",
      in3.exists(v => math.abs(v - 10.0 / 16.387064) < 1e-4))
    val hp = valueOf(engineXml, "maxhp")
    println(f"  max power: ${hp.getOrElse(0.0)}%.4f hp (from 1200 W)")
    check("power is converted to horsepower", hp.exists(v => math.abs(v - 1200.0 / 745.699872) < 1e-3))
    val bsfc = valueOf(engineXml, "bsfc")
    println(f"  fuel consumption: ${bsfc.getOrElse(0.0)}%.4f lb/hp/h (from 700 g/kWh)")
    check("consumption is converted to lb/hp/h", bsfc.exists(v => math.abs(v - 700.0 / 608.277) < 1e-3))
    check("the rev range is passed through",
      valueOf(engineXml, "idlerpm").exists(_ == 2500.0) && valueOf(engineXml, "maxrpm").exists(_ == 14000.0))

    // Without a <feed> the engine has no fuel source: it cranks but never runs.
    val feeds = """<feed>([0-9]+)</feed>""".r.findAllMatchIn(generated.aircraftXml).map(_.group(1)).toSeq
    println(s"  engine feeds from tank(s): ${feeds.mkString(", ")}")
    check("the engine is fed from every tank", feeds == Seq("0"))

    // The tank goes in the aircraft file, where JSBSim expects it.
    check("a fuel tank is emitted", generated.aircraftXml.contains("""<tank type="FUEL">"""))
    check("with its mass in kilograms",
      valueOf(generated.aircraftXml, "capacity").exists(_ == 0.5) &&
        valueOf(generated.aircraftXml, "contents").exists(_ == 0.5))

    println("incomplete models are refused")
    val noFuel = pistonModel()
    noFuel.getConfig.getPower.getFuelTanks.clear()
    check("no fuel tank is reported", mentions(SimulationRequirements.validate(noFuel), "needs a fuel tank"))

    val noDisplacement = pistonModel()
    noDisplacement.getConfig.getPower.getBateries.get(0).getShafts.get(0)
      .getCombustionEngines.get(0).setDisplacement(0f)
    check("zero displacement is reported",
      mentions(SimulationRequirements.validate(noDisplacement), "Displacement"))

    val badRevs = pistonModel()
    badRevs.getConfig.getPower.getBateries.get(0).getShafts.get(0)
      .getCombustionEngines.get(0).setMaxRpm(1000f)
    check("a max rpm below idle is reported", mentions(SimulationRequirements.validate(badRevs), "must be above"))

    val badCycles = pistonModel()
    badCycles.getConfig.getPower.getBateries.get(0).getShafts.get(0)
      .getCombustionEngines.get(0).setCycles(3)
    check("a stroke count other than 2 or 4 is reported",
      mentions(SimulationRequirements.validate(badCycles), "must be 2 or 4"))

    val overfilled = pistonModel()
    overfilled.getConfig.getPower.getFuelTanks.get(0).setContents(2.0f)
    check("more fuel than the tank holds is reported",
      mentions(SimulationRequirements.validate(overfilled), "cannot exceed"))

    val bothEngines = pistonModel()
    bothEngines.getConfig.getPower.getBateries.get(0).getShafts.get(0).createEngine()
    check("a shaft with both engine kinds is reported",
      mentions(SimulationRequirements.validate(bothEngines), "both an electric and a combustion"))

    println(if (ok) "COMBUSTION_PACKAGE_OK" else "COMBUSTION_PACKAGE_FAIL")
    if (!ok) sys.exit(1)
  }
}
