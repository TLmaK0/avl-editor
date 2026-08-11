/*
 * The centre of gravity must come from the components' own mass and position — the motor, the
 * battery and the propeller included. Before this, they had no mass at all and the only way to move
 * the CG was ballast: the eurofighter sample carried 450 g of "manual nose ballast target cgx", and
 * its CG ended up 0.8 chords ahead of the main wheels, where the elevator cannot rotate it.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.PropulsionMassCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim.{Battery, CRRCSim, CRRCSimFactory, EngineData, Propeller}
import com.abajar.avleditor.avl.mass.Mass
import scala.collection.JavaConverters._

object PropulsionMassCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** An airframe of 1 kg at x = 1.0, with nothing else. */
  private def airframe(): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()
    val geo = crrcsim.getAvl.getGeometry
    geo.setSref(0.4f); geo.setBref(1.1f); geo.setCref(0.37f)
    geo.getMasses.clear()
    geo.getSurfaces.clear()
    geo.getBodies.clear()
    val airframeMass = new Mass
    airframeMass.setName("airframe"); airframeMass.setMass(1.0f)
    airframeMass.setX(1.0f); airframeMass.setY(0f); airframeMass.setZ(0f)
    geo.getMasses.add(airframeMass)
    crrcsim.getConfig.getPower.getBateries.clear()
    crrcsim
  }

  /** Battery, motor and propeller, each with a mass at a position. */
  private def withPropulsion(batteryX: Float): CRRCSim = {
    val crrcsim = airframe()
    val power = crrcsim.getConfig.getPower
    val battery = power.createBattery()
    battery.setU_0(11.1f)
    battery.setMass(0.5f); battery.getPos.setX(batteryX)
    battery.createShaft()
    val shaft = battery.getShafts.get(0)

    val propeller = shaft.createPropeller()
    propeller.setD(0.25f); propeller.setMass(0.04f); propeller.getPos.setX(0.0f)

    val engine = shaft.createEngine()
    engine.setMass(0.2f); engine.getPos.setX(0.05f)
    val point = new EngineData
    point.setU_K(11.1f); point.setI_M(20f); point.setRpms(10000f)
    engine.getData.add(point)
    val idle = engine.createDataIdle()
    idle.setU_K(11.1f); idle.setI_M(0.4f)
    crrcsim
  }

  private def cgX(crrcsim: CRRCSim): Float = {
    crrcsim.getAvl.getGeometry.calculateCenterOfMassFromMasses(crrcsim.getAllMasses())
    crrcsim.getAvl.getGeometry.getXref
  }

  def main(args: Array[String]): Unit = {
    println("the components carry their own mass")
    val model = withPropulsion(0.10f)
    val names = crrcsimNames(model)
    println(s"  propulsion masses: ${names.mkString(", ")}")
    check("battery, motor and propeller each contribute", names.length == 3)
    check("their total is the sum of the stated masses",
      math.abs(model.getPropulsionMasses.asScala.map(_.getMass).sum - 0.74f) < 1e-4)

    // Auto Masses redistributes the geometry total by volume, so the propulsion must stay out of it.
    check("the geometry's own list excludes them",
      model.getAvl.getGeometry.getMassesRecursive.size == 1)
    check("the full list includes them", model.getAllMasses.size == 4)

    println("the CG is a result, not a target")
    val forward = cgX(withPropulsion(0.10f))
    val aft = cgX(withPropulsion(0.60f))
    println(f"  battery at 0.10 m -> CG $forward%.4f ;  battery at 0.60 m -> CG $aft%.4f")
    // 1 kg at 1.0 plus 0.74 kg of propulsion: moving the battery aft must move the CG aft.
    check("moving the battery aft moves the CG aft", aft > forward)
    check("the CG sits between the airframe and the propulsion", forward > 0.1f && forward < 1.0f)
    val expected = (1.0f * 1.0f + 0.5f * 0.10f + 0.2f * 0.05f + 0.04f * 0.0f) / 1.74f
    check("it is the weighted average of every component", math.abs(forward - expected) < 1e-3)

    println("the total mass reaches the FDM")
    val m = withPropulsion(0.10f)
    // Something else entirely, as if it had been left over from an earlier layout.
    m.getAvl.getGeometry.setXref(0.123f)
    m.calculate()
    println(f"  mass_inertia total: ${m.getConfig.getMass_inertia.getMass}%.4f kg, " +
      f"reference point x: ${m.getAvl.getGeometry.getXref}%.4f")
    check("mass includes the propulsion",
      math.abs(m.getConfig.getMass_inertia.getMass - 1.74f) < 1e-3)
    // The reference point is what AVL takes its moments about and what the export writes as the CG:
    // a stale one means an aircraft with the weight of one model and the balance point of another.
    check("and the reference point is brought onto the masses' CG",
      math.abs(m.getAvl.getGeometry.getXref - expected) < 1e-3)

    println("a model whose masses total zero")
    val weightless = new CRRCSimFactory().create()
    weightless.getAvl.getGeometry.setXref(0.321f)
    weightless.calculate()
    check("keeps the reference point it had, rather than being moved to nowhere",
      math.abs(weightless.getAvl.getGeometry.getXref - 0.321f) < 1e-6)

    println("fuel is not counted twice")
    val fuelled = withPropulsion(0.10f)
    val tank = fuelled.getConfig.getPower.createFuelTank()
    tank.setCapacity(0.5f); tank.setContents(0.5f); tank.getPos.setX(0.5f)
    fuelled.calculate()
    check("the tank's fuel stays out of the empty weight",
      math.abs(fuelled.getConfig.getMass_inertia.getMass - 1.74f) < 1e-3)

    // A mass is optional: a component may be accounted for elsewhere, or be negligible. A zero is
    // a stated value, not missing data, so it is accepted and simply contributes nothing.
    println("a component with no mass")
    val noMass = withPropulsion(0.10f)
    noMass.getConfig.getPower.getBateries.get(0).setMass(0f)
    // Compare the whole problem list with and without the battery's mass: zeroing it must change
    // nothing. (Checking for the word "mass" would catch the unrelated total-mass rule.)
    val withMass = SimulationRequirements.validate(withPropulsion(0.10f))
    check("is not reported as a problem", SimulationRequirements.validate(noMass) == withMass)
    check("contributes nothing to the balance",
      math.abs(noMass.getPropulsionMasses.asScala.map(_.getMass).sum - 0.24f) < 1e-4)
    noMass.calculate()
    check("and the total mass is the rest of the model",
      math.abs(noMass.getConfig.getMass_inertia.getMass - 1.24f) < 1e-3)

    println(if (ok) "PROPULSION_MASS_OK" else "PROPULSION_MASS_FAIL")
    if (!ok) sys.exit(1)
  }

  private def crrcsimNames(model: CRRCSim): Seq[String] =
    model.getPropulsionMasses.asScala.map(_.getName).toSeq
}
