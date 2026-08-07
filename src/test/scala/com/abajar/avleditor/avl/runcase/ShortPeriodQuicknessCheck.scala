/*
 * MIL-F-8785C 3.2.2.1.1, FIGURES 1-3 (pp. 14-16): the other half of the short-period criterion. Only the
 * damping (TABLE IV) was ever judged, and an aircraft can be beautifully damped and still answer the
 * elevator far too slowly or far too sharply for the g its wing makes.
 *
 * The requirement is drawn rather than tabulated, which is why it looked like it needed a scanned plot
 * measured by eye. It does not: the boundaries are lines of constant CAP and each one carries its value
 * printed up the right-hand edge of the figure, so the plots are a table of four numbers per Flight Phase.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.runcase.ShortPeriodQuicknessCheck"
 */
package com.abajar.avleditor.avl.runcase

object ShortPeriodQuicknessCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** A pitch oscillation of the given natural frequency, with the lift figures that set n/alpha. */
  private def aircraft(wn: Double, clAlpha: Float = 4.5f, clTrim: Float = 0.45f,
                       span: Float = 0f): AvlCalculation = {
    val calc = new AvlCalculation(0, 1, 2)
    val config = new Configuration
    config.setBref(span)
    config.setCLtot(clTrim)
    config.setMetresPerLengthUnit(1f)
    config.setSecondsPerTimeUnit(1f)
    calc.setConfiguration(config)

    val stab = new StabilityDerivatives
    stab.initControls(3)
    stab.setCLa(clAlpha)
    calc.setStabilityDerivatives(stab)

    // zeta 0.5 puts the damping comfortably inside TABLE IV, so only the frequency is under test.
    val sigma = -0.5 * wn
    val omega = wn * math.sqrt(1.0 - 0.25)
    val mode = new AvlEigenvalue(sigma.toFloat, omega.toFloat)
    mode.setModeStateAmplitude("w", 1.0f)
    mode.setModeStateAmplitude("q", 0.9f)
    mode.setModeStateAmplitude("the", 0.7f)
    val modes = new java.util.ArrayList[AvlEigenvalue]()
    modes.add(mode)
    calc.setEigenvalues(modes)
    calc
  }

  private def row(calc: AvlCalculation,
                  category: FlightPhaseCategory = FlightPhaseCategory.B): ModalNormRow =
    MilF8785cEvaluator.evaluate(calc, category).find(_.modeName == "Short-period quickness").get

  def main(args: Array[String]): Unit = {
    println("n/alpha needs no weight, no air and no wing area")
    // In level flight the lift equals the weight, so n/alpha = CLalpha / CL_trim exactly. With CLalpha 4.5
    // per radian and the aircraft trimmed at CL 0.45 that is 10 g per radian.
    val judged = row(aircraft(wn = 3.0))
    println("    " + judged.verdict)
    check("it is derived from the lift slope and the trim alone",
      judged.verdict.contains("10.0 g per radian"))
    // CAP = wn^2 / (n/alpha) = 9 / 10 = 0.9, inside Category B's 0.085 .. 3.6.
    check("and CAP is the frequency squared over it", judged.verdict.contains("CAP 0.90"))
    check("which reaches Level 1", judged.level == Some(1))

    println("the boundaries are the ones printed on FIGURE 2, not measured off it")
    check("the requirement quotes them", judged.requirement.contains("0.085") &&
      judged.requirement.contains("3.6"))
    // wn 0.9 -> CAP 0.081, just under Category B's Level 1 floor of 0.085.
    val sluggish = row(aircraft(wn = 0.9))
    println("    " + sluggish.verdict)
    check("just below the floor is Level 2", sluggish.level == Some(2))
    check("and it says the nose answers slowly", sluggish.verdict.contains("too sluggish"))
    // wn 6.1 -> CAP 3.72, just over the ceiling of 3.6.
    val twitchy = row(aircraft(wn = 6.1))
    println("    " + twitchy.verdict)
    check("just above the ceiling is Level 2 as well", twitchy.level == Some(2))
    check("and it says the aircraft is twitchy", twitchy.verdict.contains("too sharp"))

    println("the Flight Phase moves the floor, as the three figures do")
    // FIGURE 1 (Category A) puts Level 1 at 0.28, FIGURE 3 (Category C) at 0.16, FIGURE 2 (B) at 0.085.
    val modest = aircraft(wn = 1.1) // CAP = 1.21/10 = 0.121: over B's 0.085, under C's 0.16 and A's 0.28.
    println(f"    CAP 0.121: B -> ${row(modest, FlightPhaseCategory.B).level}%s, " +
      f"C -> ${row(modest, FlightPhaseCategory.C).level}%s, A -> ${row(modest, FlightPhaseCategory.A).level}%s")
    check("gentle flying accepts it", row(modest, FlightPhaseCategory.B).level == Some(1))
    check("landing does not quite", row(modest, FlightPhaseCategory.C).level == Some(2))
    // Category A's Level 2 floor is 0.16 as well, and 0.121 is under it: for aerobatics this aircraft is
    // not merely short of Level 1, it is off the bottom of FIGURE 1 altogether.
    check("and for aerobatics it is off the bottom of the figure",
      row(modest, FlightPhaseCategory.A).level.isEmpty &&
        row(modest, FlightPhaseCategory.A).verdict.contains("Worse than Level 3"))

    println("CAP is a frequency squared, so it follows the aircraft's size squared")
    val small = row(aircraft(wn = 3.0, span = 1.5f))
    println("    " + small.applied.getOrElse("(not scaled)"))
    check("a model's requirement is stated as well as the standard's", small.applied.isDefined)
    // The floor scales by the square of the frequency ratio, (9.81/1.5) = 6.54, so 0.085 becomes 0.556.
    check("and it is the square of the frequency ratio", small.applied.exists(_.contains("0.56")))
    check("a full-size aircraft has nothing scaled", row(aircraft(wn = 3.0, span = 30f)).applied.isEmpty)

    println("and nothing is invented when the aircraft is not in level flight")
    val noLift = row(aircraft(wn = 3.0, clTrim = 0f))
    println("    " + noLift.verdict)
    check("a trim carrying no lift is refused",
      noLift.level.isEmpty && noLift.verdict.contains("not holding level flight"))
    val noSlope = row(aircraft(wn = 3.0, clAlpha = 0f))
    check("and so is a lift slope that is not one",
      noSlope.level.isEmpty && noSlope.verdict.contains("not one"))

    println("with no pitch mode there is no frequency to judge, and it says so")
    val noMode = new AvlCalculation(0, 1, 2)
    noMode.setConfiguration(new Configuration)
    noMode.setStabilityDerivatives(new StabilityDerivatives)
    noMode.setEigenvalues(new java.util.ArrayList[AvlEigenvalue]())
    check("it reports the motion as not found",
      row(noMode).level.isEmpty && row(noMode).verdict.startsWith("Not"))

    println(if (ok) "SHORT_PERIOD_QUICKNESS_OK" else "SHORT_PERIOD_QUICKNESS_FAIL")
    if (!ok) sys.exit(1)
  }
}
