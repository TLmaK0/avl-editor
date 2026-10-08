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

    println("it is close to the three propellers it was measured against, named one by one")
    PitchStretchMeasurements.foreach { m =>
      println(f"  ${m.source}")
      println(f"    pitch ratio ${m.pitchRatio}%.4f, measured J0 ${m.measuredJ0}%.4f, ratio ${m.ratio}%.4f")
      val predicted = targetJ0(1.0, m.pitchRatio)
      check(f"predicted J0 (${predicted}%.4f) is within 10%% of this one's own measurement",
        math.abs(predicted - m.measuredJ0) / m.measuredJ0 < 0.10)
    }
    check("three measurements, not one", PitchStretchMeasurements.length == 3)
    check("each from its own named source", PitchStretchMeasurements.map(_.source).distinct.length == 3)

    println("the mean carries its own spread, not a bare number")
    val (lo, hi) = PitchStretchRatioRange
    println(f"  PitchStretchRatio = ${PitchStretchRatio}%.4f, range ${lo}%.4f - ${hi}%.4f")
    check("the mean sits inside the range it was built from", PitchStretchRatio > lo && PitchStretchRatio < hi)
    check("the range is the three measurements' own min and max",
      near(lo, PitchStretchMeasurements.map(_.ratio).min) && near(hi, PitchStretchMeasurements.map(_.ratio).max))
    check("the three measurements really do disagree: not a one-point range pretending to be a spread",
      hi - lo > 0.05)

    println("what it refuses rather than inventing")
    check("no diameter", from(0.0, 0.127, 2).left.getOrElse("").contains("diameter"))
    check("no pitch", from(0.254, 0.0, 2).left.getOrElse("").contains("pitch"))
    check("one blade is not a propeller", from(0.254, 0.127, 1).left.getOrElse("").contains("blades"))
    check("a good propeller is refused nothing", from(0.254, 0.127, 2).isRight)

    println("targetJ0 called directly, bypassing from, fails by name and not by NaN")
    def refusesCleanly(thunk: => Double): Boolean =
      try { val r = thunk; false /* should have thrown */ }
      catch {
        case e: IllegalArgumentException => !e.getMessage.contains("NaN") && e.getMessage.nonEmpty
        case _: Throwable => false
      }
    check("no diameter names the diameter, not a NaN", refusesCleanly(targetJ0(0.0, 0.127)))
    check("no pitch names the pitch, not a NaN", refusesCleanly(targetJ0(0.254, 0.0)))
    check("neither stated names one of them, not a 0/0 NaN", refusesCleanly(targetJ0(0.0, 0.0)))
    check("a real propeller is not refused", { targetJ0(0.254, 0.127); true })

    println(if (ok) "PROPELLER_CURVES_OK" else "PROPELLER_CURVES_FAIL")
    if (!ok) sys.exit(1)
  }
}
