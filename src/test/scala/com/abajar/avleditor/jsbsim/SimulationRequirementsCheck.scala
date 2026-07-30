/*
 * Verifies the requirements a model must meet before it is handed to a simulator, and that
 * no invented data is substituted for what is missing.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.SimulationRequirementsCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim.{CRRCSim, CRRCSimFactory, EngineData, Wheel}
import com.abajar.avleditor.avl.geometry.Control

object SimulationRequirementsCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def mentions(problems: Seq[String], fragment: String): Boolean =
    problems.exists(_.toLowerCase.contains(fragment.toLowerCase))

  /** A model that meets every requirement: mass, inertias, references, gear and a control. */
  private def flyableModel(): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()
    val mi = crrcsim.getConfig.getMass_inertia
    mi.setMass(2.5f); mi.setI_xx(0.05f); mi.setI_yy(0.08f); mi.setI_zz(0.12f)

    val geo = crrcsim.getAvl.getGeometry
    geo.setSref(0.4f); geo.setBref(1.1f); geo.setCref(0.37f)

    // One elevator control, on an isolated surface (the factory ships a default aircraft).
    geo.getSurfaces.clear()
    val section = geo.createSurface().createSection()
    section.getControls.clear()
    val control = new Control
    control.setType(1) // elevator
    control.setMaxDeflection(20f)
    section.getControls.add(control)

    addWheels(crrcsim, Seq((0.2f, 0.0f), (0.8f, -0.3f), (0.8f, 0.3f)))
    addPropulsion(crrcsim)
    crrcsim
  }

  /** Battery → shaft → propeller + engine with a usable data curve. */
  private def addPropulsion(crrcsim: CRRCSim): Unit = {
    val power = crrcsim.getConfig.getPower
    power.getBateries.clear()
    val battery = power.createBattery()
    battery.setU_0(11.1f)
    battery.createShaft()
    val shaft = battery.getShafts.get(0)

    // Same path the "+ Propeller" toolbar button uses.
    val propeller = shaft.createPropeller()
    propeller.setD(0.25f)

    val engine = shaft.createEngine()
    val point = new EngineData
    point.setU_K(11.1f); point.setI_M(0.45f); point.setRpms(10000f)
    engine.getData.add(point)
  }

  private def addWheels(crrcsim: CRRCSim, at: Seq[(Float, Float)]): Unit = {
    crrcsim.getWheels.clear()
    at.zipWithIndex.foreach { case ((x, y), i) =>
      val w = new Wheel
      w.setName(s"GEAR$i")
      w.getPos.setX(x); w.getPos.setY(y); w.getPos.setZ(-0.05f)
      crrcsim.getWheels.add(w)
    }
  }

  def main(args: Array[String]): Unit = {
    println("a model meeting every requirement")
    val good = flyableModel()
    val noProblems = SimulationRequirements.validate(good)
    if (noProblems.nonEmpty) noProblems.foreach(p => println(s"    unexpected: $p"))
    check("validates clean", noProblems.isEmpty)
    check("its contacts are the model's own collision points", JsbsimExporter.buildContacts(good).length == 3)

    println("missing collision points")
    val noGear = flyableModel()
    noGear.getWheels.clear()
    val gearProblems = SimulationRequirements.validate(noGear)
    check("is reported", mentions(gearProblems, "collision points"))
    check("no belly contact is invented", JsbsimExporter.buildContacts(noGear).isEmpty)

    println("too few collision points")
    val twoWheels = flyableModel()
    addWheels(twoWheels, Seq((0.2f, 0.0f), (0.8f, 0.3f)))
    check("is reported", mentions(SimulationRequirements.validate(twoWheels), "at least 3"))

    println("collinear collision points")
    val aligned = flyableModel()
    addWheels(aligned, Seq((0.2f, 0.0f), (0.5f, 0.0f), (0.9f, 0.0f)))
    check("is reported as aligned", mentions(SimulationRequirements.validate(aligned), "aligned"))

    println("mass and inertia")
    val noMass = flyableModel()
    noMass.getConfig.getMass_inertia.setMass(0f)
    check("zero mass is reported", mentions(SimulationRequirements.validate(noMass), "mass"))
    val noInertia = flyableModel()
    noInertia.getConfig.getMass_inertia.setI_yy(0f)
    check("zero Iyy is reported", mentions(SimulationRequirements.validate(noInertia), "Iyy"))

    println("reference geometry")
    val noSref = flyableModel()
    noSref.getAvl.getGeometry.setSref(0f)
    check("zero Sref is reported", mentions(SimulationRequirements.validate(noSref), "reference area"))

    println("controls")
    val noControls = flyableModel()
    val sections = noControls.getAvl.getGeometry.getSurfaces.get(0).getSections
    for (i <- 0 until sections.size) sections.get(i).getControls.clear()
    check("absent control surfaces are reported", mentions(SimulationRequirements.validate(noControls), "control surface"))

    println("propulsion")
    val noPower = flyableModel()
    noPower.getConfig.getPower.getBateries.clear()
    check("a model with no battery is reported",
      mentions(SimulationRequirements.validate(noPower), "no propulsion"))
    check("no propulsion is invented for it", JsbsimExporter.buildPropulsion(noPower).isEmpty)

    val noProp = flyableModel()
    noProp.getConfig.getPower.getBateries.get(0).getShafts.get(0).getPropellers.clear()
    check("a shaft with no propeller is reported",
      mentions(SimulationRequirements.validate(noProp), "no propeller"))

    val noEngine = flyableModel()
    noEngine.getConfig.getPower.getBateries.get(0).getShafts.get(0).getEngines.clear()
    check("a shaft with no engine is reported",
      mentions(SimulationRequirements.validate(noEngine), "no engine"))

    val noCurve = flyableModel()
    noCurve.getConfig.getPower.getBateries.get(0).getShafts.get(0).getEngines.get(0).getData.clear()
    check("an engine with no data points is reported",
      mentions(SimulationRequirements.validate(noCurve), "no usable data points"))
    check("no motor parameters are invented", JsbsimExporter.buildPropulsion(noCurve).isEmpty)

    val zeroVolts = flyableModel()
    zeroVolts.getConfig.getPower.getBateries.get(0).setU_0(0f)
    check("zero battery voltage is reported",
      mentions(SimulationRequirements.validate(zeroVolts), "battery voltage"))

    // A propeller straight from the toolbar: 2 blades is the only default, the diameter is asked
    // for rather than invented.
    val freshProp = flyableModel()
    val freshShaft = freshProp.getConfig.getPower.getBateries.get(0).getShafts.get(0)
    freshShaft.getPropellers.clear()
    freshShaft.createPropeller()
    val freshProblems = SimulationRequirements.validate(freshProp)
    check("a newly added propeller comes with 2 blades",
      freshShaft.getPropellers.get(0).getN_fold == 2 && !mentions(freshProblems, "blades"))
    check("its diameter is asked for", mentions(freshProblems, "propeller diameter"))

    val zeroDiameter = flyableModel()
    zeroDiameter.getConfig.getPower.getBateries.get(0).getShafts.get(0).getPropellers.get(0).setD(0f)
    check("zero propeller diameter is reported",
      mentions(SimulationRequirements.validate(zeroDiameter), "propeller diameter"))

    println(if (ok) "SIM_REQ_OK" else "SIM_REQ_FAIL")
    if (!ok) sys.exit(1)
  }
}
