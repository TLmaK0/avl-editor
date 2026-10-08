/*
 * A free propeller's exported curve has to run out where ITS pitch says, not where a borrowed
 * sample's does. The property is checked against three propellers at different pitch ratios, not
 * against one set of numbers, so it survives a model stating a propeller the generic sample never
 * saw.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.PropellerCurvesCheck"
 */
package com.abajar.avleditor.jsbsim

import PropellerCurves._

object PropellerCurvesCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Double, b: Double, tol: Double = 1e-6): Boolean = math.abs(a - b) < tol

  def main(args: Array[String]): Unit = {
    println("the 10x5 the check aircraft states")
    val tenByFive = from(0.254, 0.127, 2) match {
      case Left(problem) => println("  FAIL " + problem); ok = false; return
      case Right(c) => c
    }
    println("  J        Ct        Cp")
    tenByFive.ct.zip(tenByFive.cp).foreach { case ((j, ct), (_, cp)) =>
      println(f"  ${j}%.4f  ${ct}%8.5f  ${cp}%8.5f")
    }

    println("the thrust falls with advance ratio and is positive before it runs out")
    check("Ct(0) is positive", tenByFive.ct.head._2 > 0)
    check("Ct falls all the way down",
      tenByFive.ct.map(_._2).sliding(2).forall(p => p.length < 2 || p(1) < p(0)))
    check("and is positive everywhere before the last row",
      tenByFive.ct.init.forall(_._2 > 0))
    check("Cp stays positive throughout: a stopped propeller still absorbs power",
      tenByFive.cp.forall(_._2 > 0))

    println("it reaches zero near THIS propeller's pitch speed, not a borrowed one's")
    val expected = targetJ0(0.254, 0.127)
    println(f"  H/D = ${0.127 / 0.254}%.4f, expected J0 = ${expected}%.4f, table's last J = " +
      f"${tenByFive.ct.last._1}%.4f")
    check("the table's last row is at the target J0", near(tenByFive.ct.last._1, expected))
    check("which is not the generic sample's own 0.7291 for this propeller", !near(expected, 0.7291, 1e-3))

    println("a finer pitch runs out sooner, a coarser one later — the whole point of the issue")
    val finePitch = from(0.254, 0.064, 2).right.get // a 10x2.5
    val coarsePitch = from(0.254, 0.203, 2).right.get // a 10x8
    println(f"  10x2.5 runs out at ${finePitch.ct.last._1}%.4f, " +
      f"10x5 at ${tenByFive.ct.last._1}%.4f, 10x8 at ${coarsePitch.ct.last._1}%.4f")
    check("finer pitch runs out sooner than 10x5", finePitch.ct.last._1 < tenByFive.ct.last._1)
    check("coarser pitch runs out later than 10x5", coarsePitch.ct.last._1 > tenByFive.ct.last._1)
    check("same diameter, so the static thrust coefficient is unmoved by pitch",
      near(finePitch.ct.head._2, tenByFive.ct.head._2) && near(coarsePitch.ct.head._2, tenByFive.ct.head._2))

    println("it is close to the three propellers it was measured against")
    // APC Free Flight 9x4 (H/D = 0.444, measured zero crossing 0.5666) and APC Sport 9x6
    // (H/D = 0.667, measured zero crossing 0.7821), both from the UIUC Propeller Data Site
    // (m-selig.ae.illinois.edu/props/volume-2); the generic sample's own APC 9.4x5 (H/D = 0.5319,
    // zero crossing 0.7291). PitchStretchRatio is their mean, so none lands exactly on it.
    val ff9x4 = targetJ0(1.0, 4.0 / 9.0)
    val sp9x6 = targetJ0(1.0, 6.0 / 9.0)
    println(f"  predicted J0 at H/D=0.444: ${ff9x4}%.4f (measured 0.5666); " +
      f"at H/D=0.667: ${sp9x6}%.4f (measured 0.7821)")
    check("within 10% of the APC 9x4 measurement", math.abs(ff9x4 - 0.5666) / 0.5666 < 0.10)
    check("within 10% of the APC 9x6 measurement", math.abs(sp9x6 - 0.7821) / 0.7821 < 0.10)

    println("what it refuses rather than inventing")
    check("no diameter", from(0.0, 0.127, 2).left.getOrElse("").contains("diameter"))
    check("no pitch", from(0.254, 0.0, 2).left.getOrElse("").contains("pitch"))
    check("one blade is not a propeller", from(0.254, 0.127, 1).left.getOrElse("").contains("blades"))
    check("a good propeller is refused nothing", from(0.254, 0.127, 2).isRight)

    println(if (ok) "PROPELLER_CURVES_OK" else "PROPELLER_CURVES_FAIL")
    if (!ok) sys.exit(1)
  }
}
