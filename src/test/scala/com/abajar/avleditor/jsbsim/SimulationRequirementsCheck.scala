/*
 * Verifies the requirements a model must meet before it is handed to a simulator, and that
 * no invented data is substituted for what is missing.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.SimulationRequirementsCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim.{Battery, CRRCSim, CRRCSimFactory, EngineData, MassInertia, Propeller, Wheel}
import com.abajar.avleditor.view.annotations.AvlEditorField
import com.abajar.avleditor.avl.geometry.Control
import com.abajar.avleditor.avl.runcase.{AvlCalculation, Configuration}
import scala.collection.JavaConverters._

object SimulationRequirementsCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def mentions(problems: Seq[String], fragment: String): Boolean =
    problems.exists(_.toLowerCase.contains(fragment.toLowerCase))

  /** The label the properties table shows, straight from the annotation. */
  private def uiLabel(cls: Class[_], field: String): String =
    cls.getDeclaredField(field).getAnnotation(classOf[AvlEditorField]).text()

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

    battery.setMass(0.45f); battery.getPos.setX(0.25f)

    // Same path the "+ Propeller" toolbar button uses.
    val propeller = shaft.createPropeller()
    propeller.setD(0.25f)
    propeller.setMass(0.03f); propeller.getPos.setX(0.02f)

    val engine = shaft.createEngine()
    engine.setMass(0.18f); engine.getPos.setX(0.08f)
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
    check("zero I_yy is reported by the label the table shows",
      mentions(SimulationRequirements.validate(noInertia), uiLabel(classOf[MassInertia], "I_yy")))

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
    // Not "no propeller" any more: a shaft can be driven by a ducted fan instead, so the message names both.
    check("a shaft with nothing to make thrust is reported, naming both options",
      mentions(SimulationRequirements.validate(noProp), "'+ Propeller'") &&
        mentions(SimulationRequirements.validate(noProp), "'+ Fan'"))

    val noEngine = flyableModel()
    noEngine.getConfig.getPower.getBateries.get(0).getShafts.get(0).getEngines.clear()
    check("a shaft with no engine is reported",
      mentions(SimulationRequirements.validate(noEngine), "no engine"))

    val noCurve = flyableModel()
    noCurve.getConfig.getPower.getBateries.get(0).getShafts.get(0).getEngines.get(0).getData.clear()
    check("an engine with no data points is reported",
      mentions(SimulationRequirements.validate(noCurve), "needs a Data row"))

    // A row with zero current yields no power, which the export needs: the two must agree.
    val zeroCurrent = flyableModel()
    val curve = zeroCurrent.getConfig.getPower.getBateries.get(0).getShafts.get(0)
      .getEngines.get(0).getData
    curve.get(0).setI_M(0f)
    check("a data row with no current is reported",
      mentions(SimulationRequirements.validate(zeroCurrent), "'Current'"))
    check("and no propulsion is exported for it",
      JsbsimExporter.buildPropulsion(zeroCurrent).isEmpty)

    // A propeller wider than the wingspan is a units mistake.
    val hugeProp = flyableModel()
    hugeProp.getConfig.getPower.getBateries.get(0).getShafts.get(0)
      .getPropellers.get(0).setD(10.0f)
    check("a propeller wider than the wingspan is reported",
      mentions(SimulationRequirements.validate(hugeProp), "check the units"))
    check("no motor parameters are invented", JsbsimExporter.buildPropulsion(noCurve).isEmpty)

    val zeroVolts = flyableModel()
    zeroVolts.getConfig.getPower.getBateries.get(0).setU_0(0f)
    val voltageLabel = uiLabel(classOf[Battery], "U_0")
    println(s"  battery voltage label: '$voltageLabel'")
    check("zero battery voltage is reported by the label the table shows",
      mentions(SimulationRequirements.validate(zeroVolts), voltageLabel))
    check("that label is readable, not just a symbol", voltageLabel.split(" ").length > 1)

    // A propeller straight from the toolbar: 2 blades is the only default, the diameter is asked
    // for rather than invented.
    val freshProp = flyableModel()
    val freshShaft = freshProp.getConfig.getPower.getBateries.get(0).getShafts.get(0)
    freshShaft.getPropellers.clear()
    freshShaft.createPropeller()
    val freshProblems = SimulationRequirements.validate(freshProp)
    check("a newly added propeller comes with 2 blades",
      freshShaft.getPropellers.get(0).getBlades == 2 &&
        !mentions(freshProblems, uiLabel(classOf[Propeller], "blades")))
    check("its diameter is asked for",
      mentions(freshProblems, uiLabel(classOf[Propeller], "D")))

    val zeroDiameter = flyableModel()
    zeroDiameter.getConfig.getPower.getBateries.get(0).getShafts.get(0).getPropellers.get(0).setD(0f)
    check("zero propeller diameter is reported by the label the table shows",
      mentions(SimulationRequirements.validate(zeroDiameter), uiLabel(classOf[Propeller], "D")))

    // The '+ Trust' button builds a thrust model the export cannot use: say so, do not just ask
    // for an engine.
    val onlyTrust = flyableModel()
    val trustShaft = onlyTrust.getConfig.getPower.getBateries.get(0).getShafts.get(0)
    trustShaft.getEngines.clear()
    trustShaft.createSimpleTrust()
    check("a Simple Trust is reported as unsupported, not as a missing engine",
      mentions(SimulationRequirements.validate(onlyTrust), "cannot export"))

    println("control deflection")
    val noDeflection = flyableModel()
    // The factory's surfaces carry default sections, so find the one holding the control.
    val allControls = noDeflection.getAvl.getGeometry.getSurfaces.asScala
      .flatMap(_.getSections.asScala).flatMap(_.getControls.asScala)
    allControls.foreach(_.setMaxDeflection(0f))
    check("the fixture has a control to blank", allControls.nonEmpty)
    val deflProblems = SimulationRequirements.validate(noDeflection)
    check("a control with no deflection is reported",
      mentions(deflProblems, uiLabel(classOf[Control], "maxDeflection")))
    check("and no 25 deg default is invented",
      JsbsimExporter.detectControls(noDeflection.getAvl.getGeometry).isEmpty)

    println("a mass on a mirrored element")
    // Its mirrored half is implied, so there is nothing to ask the user for: the export supplies it.
    // See MassMirrorCheck for what a generated model then carries.
    val withPod = flyableModel()
    val pod = withPod.getAvl.getGeometry.createBody()
    pod.setName("pod")
    pod.setdY(-0.55f)
    val ballast = pod.addMassAt(1.0f, -0.55f, 0f)
    ballast.setName("pod ballast")
    ballast.setMass(0.08f)
    check("does not hold up the export", SimulationRequirements.validate(withPod).isEmpty)
    check("and both halves are in what the export weighs",
      withPod.getAllMasses.asScala.count(_.getName.startsWith("pod ballast")) == 2)

    println("what AVL itself must produce")
    check("a missing calculation is reported",
      mentions(SimulationRequirements.validateCalculation(null), "no results"))

    val calc = new AvlCalculation(0, 0, 0)
    calc.setConfiguration(new Configuration)
    check("a run with no span efficiency is reported",
      mentions(SimulationRequirements.validateCalculation(calc), "span efficiency"))
    check("and it is not replaced by a typical value",
      JsbsimExporter.spanEfficiency(calc).isEmpty)

    calc.getConfiguration.setE(java.lang.Float.valueOf(0.9f))
    check("a span efficiency AVL reported is used",
      JsbsimExporter.spanEfficiency(calc).exists(v => math.abs(v - 0.9) < 1e-6))

    println(if (ok) "SIM_REQ_OK" else "SIM_REQ_FAIL")
    if (!ok) sys.exit(1)
  }
}
