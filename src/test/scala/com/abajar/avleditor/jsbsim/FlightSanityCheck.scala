/*
 * Whether the aircraft would fly is a warning, never a refusal: the launch goes ahead and the user is
 * told. Pinned because the case that prompted it — 3 W through a 10 cm propeller on a kilogram of
 * aeroplane — looks, from inside FlightGear, exactly like a simulator ignoring the throttle.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.FlightSanityCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.avl.geometry.Control
import com.abajar.avleditor.avl.runcase.{AvlCalculation, Configuration, StabilityDerivatives}
import com.abajar.avleditor.crrcsim.{CRRCSim, CRRCSimFactory, EngineData, Wheel}

object FlightSanityCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def mentions(warnings: Seq[String], fragment: String): Boolean =
    warnings.exists(_.toLowerCase.contains(fragment.toLowerCase))

  /**
   * A model with a stated weight, wing and propulsion. `volts`/`amps` set the motor's power, since the
   * export reads it from the data curve, and `propDiameter` the disc it pushes through.
   */
  private def model(massKg: Float, volts: Float, amps: Float, propDiameter: Float,
                    sref: Float = 0.4f): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()
    val mi = crrcsim.getConfig.getMass_inertia
    mi.setMass(massKg); mi.setI_xx(0.05f); mi.setI_yy(0.08f); mi.setI_zz(0.12f)

    val geo = crrcsim.getAvl.getGeometry
    geo.setSref(sref); geo.setBref(1.1f); geo.setCref(0.37f)
    geo.getSurfaces.clear()
    val section = geo.createSurface().createSection()
    section.getControls.clear()
    val control = new Control
    control.setType(1)
    control.setMaxDeflection(20f)
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
    battery.setU_0(volts)
    battery.createShaft()
    val shaft = battery.getShafts.get(0)
    val propeller = shaft.createPropeller()
    propeller.setD(propDiameter)
    val engine = shaft.createEngine()
    val point = new EngineData
    point.setU_K(volts); point.setI_M(amps); point.setRpms(9000f)
    engine.getData.add(point)
    crrcsim
  }

  /** An AVL run that says the aircraft is stable in every axis. */
  private def stableRun(): AvlCalculation = {
    val calc = new AvlCalculation(0, 0, 0)
    calc.setConfiguration(new Configuration)
    calc.getConfiguration.setE(java.lang.Float.valueOf(0.9f))
    val std = new StabilityDerivatives
    std.setCma(-0.8f)
    std.setCnb(0.09f)
    std.setClb(-0.05f)
    calc.setStabilityDerivatives(std)
    calc
  }

  def main(args: Array[String]): Unit = {
    println("the installation that started this: 3 W and a 10 cm propeller on a kilogram")
    val hopeless = FlightSanity.warnings(model(1.0f, 3.2f, 1.0f, 0.10f), stableRun())
    hopeless.foreach(w => println("  ! " + w))
    check("it is reported", hopeless.nonEmpty)
    check("as thrust against weight", mentions(hopeless, "thrust to weight"))
    check("with the power it has", mentions(hopeless, "W/kg"))
    check("and what to do about it",
      mentions(hopeless, "propeller") && mentions(hopeless, "current"))

    println("a sensible 3S installation on the same aircraft")
    val sensible = FlightSanity.warnings(model(1.0f, 11.1f, 12f, 0.20f), stableRun())
    sensible.foreach(w => println("  ! " + w))
    check("nothing to say about the propulsion",
      !mentions(sensible, "thrust to weight") && !mentions(sensible, "W/kg"))

    println("enough to fly, nothing to climb with")
    val marginal = FlightSanity.warnings(model(1.6f, 11.1f, 6f, 0.20f), stableRun())
    marginal.foreach(w => println("  ! " + w))
    check("is said, without saying it will not fly",
      mentions(marginal, "little to spare") && !mentions(marginal, "will not accelerate"))

    println("a brick of a wing")
    val loaded = FlightSanity.warnings(model(3.0f, 11.1f, 30f, 0.25f, sref = 0.1f), stableRun())
    loaded.foreach(w => println("  ! " + w))
    check("the wing loading is reported", mentions(loaded, "g/dm"))
    check("a normal wing is not", !mentions(
      FlightSanity.warnings(model(1.0f, 11.1f, 12f, 0.20f), stableRun()), "g/dm"))

    println("what AVL says about stability")
    def withDerivatives(cma: Float, cnb: Float, clb: Float): Seq[String] = {
      val calc = stableRun()
      calc.getStabilityDerivatives.setCma(cma)
      calc.getStabilityDerivatives.setCnb(cnb)
      calc.getStabilityDerivatives.setClb(clb)
      FlightSanity.warnings(model(1.0f, 11.1f, 12f, 0.20f), calc)
    }
    val unstablePitch = withDerivatives(0.4f, 0.09f, -0.05f)
    unstablePitch.foreach(w => println("  ! " + w))
    check("pitch instability is reported, with the CG as the cure",
      mentions(unstablePitch, "pitch") && mentions(unstablePitch, "centre of gravity"))
    check("so is yaw", mentions(withDerivatives(-0.8f, -0.02f, -0.05f), "yaw"))
    check("and roll", mentions(withDerivatives(-0.8f, 0.09f, 0.03f), "slip"))
    check("a stable aircraft is left alone", withDerivatives(-0.8f, 0.09f, -0.05f).isEmpty)

    println("what it does not do")
    check("nothing to say when there is no propulsion to judge", {
      val noPower = model(1.0f, 11.1f, 12f, 0.20f)
      noPower.getConfig.getPower.getBateries.clear()
      !mentions(FlightSanity.warnings(noPower, stableRun()), "thrust")
    })
    check("nor when the weight is not stated: that is a refusal elsewhere", {
      val weightless = model(1.0f, 11.1f, 12f, 0.20f)
      weightless.getConfig.getMass_inertia.setMass(0f)
      FlightSanity.warnings(weightless, stableRun()).isEmpty
    })
    check("and it never refuses: the requirements are what refuse",
      SimulationRequirements.validate(model(1.0f, 3.2f, 1.0f, 0.10f)).isEmpty)
    check("no AVL run yet is not something to complain about",
      !mentions(FlightSanity.warnings(model(1.0f, 11.1f, 12f, 0.20f), null), "AVL reports"))

    println(if (ok) "FLIGHT_SANITY_OK" else "FLIGHT_SANITY_FAIL")
    if (!ok) sys.exit(1)
  }
}
