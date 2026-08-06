/*
 * A ducted fan's curves have to come out of the figures it is sold with, and be the right curves rather than a
 * convenient pair. Three properties say they are: the efficiency is Froude's, the thrust runs out exactly at k,
 * and the static thrust beats a free propeller of the same diameter by the cube root of two. All three are
 * asserted against the physics, not against numbers copied from the implementation.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.DuctedFanCurvesCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.jsbsim.DuctedFanCurves._

object DuctedFanCurvesCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Double, b: Double, tol: Double = 1e-6): Boolean = math.abs(a - b) < tol

  /** A real 90 mm fan: 91.2 mm bore, 12 blades, 3000 kV on 6S, 80 A, 2.2 kg of thrust. */
  private val n90 = 3000.0 * 22.2 * 0.8 // kV x volts, pulled down by the load
  private val fan90 = Fan(innerDiameterM = 0.0912, blades = 12, rpm = n90,
    powerWatts = 22.2 * 80, staticThrustN = 2.2 * 9.80665)

  def main(args: Array[String]): Unit = {
    println("the fan on the listing")
    val curves = from(fan90) match {
      case Left(problem) => println("  FAIL " + problem); ok = false; return
      case Right(c) => c
    }
    println(f"  k = ${curves.k}%.4f, ideal static thrust ${curves.idealStaticThrustN / 9.80665}%.2f kg, " +
      f"figure of merit ${curves.figureOfMerit}%.3f")
    println("  J        Ct        Cp     efficiency")
    curves.ct.zip(curves.cp).foreach { case ((j, ct), (_, cp)) =>
      val eff = if (cp > 0) j * ct / cp else 0.0
      println(f"  ${j}%.4f  ${ct}%8.5f  ${cp}%8.5f  ${eff}%8.4f")
    }

    println("k is where the thrust runs out")
    check("the last row is at k", near(curves.ct.last._1, curves.k))
    check("and its thrust is zero there", near(curves.ct.last._2, 0.0, 1e-12))
    check("the thrust falls all the way down",
      curves.ct.map(_._2).sliding(2).forall(p => p.length < 2 || p(1) < p(0)))
    check("and is positive everywhere before it",
      curves.ct.init.forall(_._2 > 0))
    // The point of the whole thing: a free propeller's generic table is spent by J = 0.73.
    check("a ducted fan holds its thrust past where a free propeller's has gone", curves.k > 0.73)

    println("the efficiency is Froude's, which is what a ducted fan's is")
    curves.ct.zip(curves.cp).drop(1).init.foreach { case ((j, ct), (_, cp)) =>
      val implied = j * ct / cp
      // The figure of merit scales the thrust, so it scales the efficiency with it.
      val froude = curves.figureOfMerit * froudeEfficiency(curves.k, j)
      check(f"at J = $j%.3f", near(implied, froude, 1e-9))
    }

    println("the duct is worth having: the cube root of two, at rest")
    val freeProp = freePropellerIdealStaticThrustN(fan90.innerDiameterM, fan90.powerWatts)
    val ratio = curves.idealStaticThrustN / freeProp
    println(f"  ducted ${curves.idealStaticThrustN}%.2f N against free ${freeProp}%.2f N: ${ratio}%.4f times")
    check("1.26 times a free propeller of the same diameter on the same power",
      near(ratio, math.cbrt(2.0), 1e-3))

    println("the losses come from the listing, not from a constant")
    val atRest = curves.ct.head._2 * AirDensity * math.pow(fan90.rpm / 60.0, 2) * math.pow(fan90.innerDiameterM, 4)
    println(f"  the curve at rest gives ${atRest / 9.80665}%.3f kg; the listing says " +
      f"${fan90.staticThrustN / 9.80665}%.3f kg")
    check("the exported curve reproduces the stated static thrust exactly",
      near(atRest, fan90.staticThrustN, 1e-9))
    check("and the figure of merit is a measurement, below one",
      curves.figureOfMerit > 0 && curves.figureOfMerit < 1)
    check("and it says it was measured", curves.lossesMeasured)

    println("a rotor and housing bought without a motor: no thrust is published for it")
    // The thrust of a bare fan depends on the motor fitted, so no listing quotes one. The derivation still
    // has to work, with a stated figure of merit in place of a measured one.
    val unmeasured = from(fan90.copy(staticThrustN = 0)).right.get
    println(f"  figure of merit ${unmeasured.figureOfMerit}%.2f, assumed rather than measured")
    check("the curves are still derived", unmeasured.ct.length == Rows)
    check("with the stated figure of merit", near(unmeasured.figureOfMerit, DefaultFigureOfMerit))
    check("and it says it was not measured", !unmeasured.lossesMeasured)
    check("the shape of the curve is untouched by it: k is the same",
      near(unmeasured.k, curves.k))
    check("only the thrust is scaled",
      near(unmeasured.ct.head._2 / curves.ct.head._2, DefaultFigureOfMerit / curves.figureOfMerit, 1e-9))
    check("and the power it draws is the same either way",
      near(unmeasured.cp.head._2, curves.cp.head._2))

    println("the power is left alone, because a loss costs thrust and not current")
    val powerAtRest = curves.cp.head._2 * AirDensity * math.pow(fan90.rpm / 60.0, 3) *
      math.pow(fan90.innerDiameterM, 5)
    println(f"  the curve at rest draws ${powerAtRest}%.1f W; the listing says ${fan90.powerWatts}%.1f W")
    check("the fan draws the power the listing states", near(powerAtRest, fan90.powerWatts, 1e-6))

    println("a bigger duct on the same power pushes harder, and runs out sooner")
    val wider = from(fan90.copy(innerDiameterM = 0.128)).right.get
    check("more static thrust for the same watts", wider.idealStaticThrustN > curves.idealStaticThrustN)
    check("and a lower k, because it throws more air more slowly", wider.k < curves.k)

    println("what it refuses rather than inventing")
    def refusal(fan: Fan): String = from(fan).left.getOrElse("")
    check("no diameter", refusal(fan90.copy(innerDiameterM = 0)).contains("diameter"))
    check("no revolutions", refusal(fan90.copy(rpm = 0)).contains("revolutions"))
    check("no power", refusal(fan90.copy(powerWatts = 0)).contains("power"))
    check("one blade is not a fan", refusal(fan90.copy(blades = 1)).contains("blades"))
    check("a good fan is refused nothing", from(fan90).isRight)

    println("the 50 mm fan this aircraft should have")
    // 0.94 kg wants 300-600 W, not the 1.8 kW a 90 mm fan asks for.
    val small = Fan(0.05, 5, 4200.0 * 14.8 * 0.8, 14.8 * 30, 0.6 * 9.80665)
    from(small) match {
      case Left(problem) => check("it derives", false); println("    " + problem)
      case Right(c) =>
        println(f"  k = ${c.k}%.3f, figure of merit ${c.figureOfMerit}%.3f, " +
          f"ideal ${c.idealStaticThrustN / 9.80665}%.2f kg")
        check("it derives too", c.k > 0 && c.ct.length == Rows)
    }

    println(if (ok) "DUCTED_FAN_CURVES_OK" else "DUCTED_FAN_CURVES_FAIL")
    if (!ok) sys.exit(1)
  }
}
