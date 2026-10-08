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

    println("FIGURE 1 draws a floor under its Level 1 CAP line, which the CAP test alone would miss — issue #16")
    // n/alpha = 2 (CLalpha 2.0, CLtrim 1.0): Level 1's stated CAP bound alone wants wn >= sqrt(0.28*2) = 0.75,
    // but the figure's 1.0 rad/s floor (it sits exactly on the gridline, not derived from CAP) wants more:
    // wn >= 1.0. wn 0.95 clears the stated CAP bound (cap 0.45 > 0.28) and would have passed Level 1 without
    // the floor.
    val floorBoundA1 = row(aircraft(wn = 0.95, clAlpha = 2.0f, clTrim = 1.0f), FlightPhaseCategory.A)
    println("    " + floorBoundA1.verdict)
    check("the floor keeps it out of Level 1 even though CAP alone would admit it",
      floorBoundA1.level == Some(2))
    check("and the verdict names the floor, not just CAP", floorBoundA1.verdict.contains("1.00 rad/s floor"))
    // The same aircraft just over the floor reaches Level 1.
    val overFloorA1 = row(aircraft(wn = 1.05, clAlpha = 2.0f, clTrim = 1.0f), FlightPhaseCategory.A)
    check("and just over the floor it does reach Level 1", overFloorA1.level == Some(1))

    println("FIGURE 1's Levels 2 & 3 share one floor too, and missing it is worse than Level 3 — not Level 2")
    // n/alpha = 1.5: the stated CAP bound for Levels 2 & 3 (0.16) alone wants only wn >= sqrt(0.16*1.5) =
    // 0.49, which wn 0.55 would have cleared under the old, floor-less test (cap 0.2017 > 0.16 -> "Level 2").
    // The figure's 0.6 rad/s floor (shared by both Levels, since one curve serves them both) wants more.
    val floorBoundA23 = row(aircraft(wn = 0.55, clAlpha = 3.0f, clTrim = 2.0f), FlightPhaseCategory.A)
    println("    " + floorBoundA23.verdict)
    check("below the shared floor it is worse than Level 3, not Level 2 as CAP alone would have said",
      floorBoundA23.level.isEmpty && floorBoundA23.verdict.contains("Worse than Level 3"))

    println("FIGURE 3's own floor is 0.6 rad/s too, shared by Levels 2 and 3 the same way")
    // n/alpha = 5: Levels 2 & 3's stated CAP bound (0.036) alone wants wn >= sqrt(0.036*5) = 0.424, which wn
    // 0.5 would clear (cap 0.05 > 0.036). The figure's 0.6 rad/s floor wants more.
    val floorBoundC = row(aircraft(wn = 0.5, clAlpha = 2.5f, clTrim = 0.5f), FlightPhaseCategory.C)
    println("    " + floorBoundC.verdict)
    check("Category C's floor fails it the same way", floorBoundC.level.isEmpty)

    println("FIGURE 3 also draws a VERTICAL floor: below it, Level 2 (and Level 1 inside it) are shut off " +
      "at any frequency — but Level 3 carries no such floor, and the figure says so explicitly")
    // n/alpha = 1.0, well under the 1.8 g/rad FIGURE 3 draws for Classes I, II-C, IV. cap = 0.81/1.0 = 0.81,
    // inside Level 1's stated 0.16..3.6 band, so by CAP alone this would be Level 1.
    val lowLoadPerAlpha = row(aircraft(wn = 0.9, clAlpha = 1.0f, clTrim = 1.0f), FlightPhaseCategory.C)
    println("    " + lowLoadPerAlpha.verdict)
    check("the vertical floor shuts out Level 1 and Level 2, leaving only Level 3, which has none",
      lowLoadPerAlpha.level == Some(3))
    check("and the verdict says why: too little g per radian, not a frequency complaint",
      lowLoadPerAlpha.verdict.contains("g per radian of angle of attack"))
    // The same frequency and lift slope at a high enough n/alpha (3.0, clear of the 1.8 floor) reaches Level 1.
    val clearLoadPerAlpha = row(aircraft(wn = 0.9, clAlpha = 3.0f, clTrim = 1.0f), FlightPhaseCategory.C)
    check("clear of the vertical floor the same frequency reaches Level 1",
      clearLoadPerAlpha.level == Some(1))
    // Category B draws neither kind of floor: the same aircraft that the vertical floor downgraded to
    // Level 3 in Category C reaches Level 1 there, since 0.81 is within B's own 0.085..3.6 band untouched.
    check("Category B draws neither floor, so the same aircraft reaches Level 1 there",
      row(aircraft(wn = 0.9, clAlpha = 1.0f, clTrim = 1.0f), FlightPhaseCategory.B).level == Some(1))

    println("FIGURE 3's requirement names the floor even where Level 1 carries none of its own")
    val catCWants = row(aircraft(wn = 0.9, clAlpha = 3.0f, clTrim = 1.0f), FlightPhaseCategory.C).requirement
    println("    " + catCWants)
    check("the Levels 2 & 3 floor is disclosed", catCWants.contains("0.60 rad/s for Level 2 & 3"))
    check("and so is the vertical one", catCWants.contains("1.8 g per radian for Level 1 & 2"))

    println("the vertical floor is read within its own uncertainty band, like FIGURES 4 and 5's own lines")
    // n/alpha = 1.8 sits exactly on FIGURE 3's own line, inside its +-10% reading band [1.62, 1.98]. At
    // this n/alpha the floor-raised CAP for Levels 2 & 3 is 0.36/1.8 = 0.20, above Level 1's own stated
    // 0.16 — so a CAP of 0.18 clears Level 1's stated bound but not Levels 2 & 3's floor-raised one, and
    // nothing else is left standing: the row cannot even fall back on Level 3.
    val onBoundary = row(aircraft(wn = math.sqrt(0.18 * 1.8), clAlpha = 1.8f, clTrim = 1.0f),
      FlightPhaseCategory.C)
    println("    " + onBoundary.verdict)
    check("no Level is claimed", onBoundary.level.isEmpty)
    check("the outcome says it is a boundary, not a failure", onBoundary.outcome == RowOutcome.OnTheBoundary)
    check("and the verdict names the reading uncertainty", onBoundary.verdict.contains("On the boundary") &&
      onBoundary.verdict.contains("10%"))

    println("but a Level already proven by a weaker one is still reported, even if Level 1 is the unclear part")
    // Same n/alpha (1.8, on the line), CAP 0.25 this time: clears Level 1's stated 0.16 (so the frequency
    // itself is adequate) and clears Level 3's floor-raised 0.20 too — the aircraft has a Level. Level 1
    // itself stays unresolved rather than confidently denied, because the vertical reading at n/alpha 1.8
    // cannot tell which side of 1.8 it is really on.
    val unresolvedLevel1 = row(aircraft(wn = math.sqrt(0.25 * 1.8), clAlpha = 1.8f, clTrim = 1.0f),
      FlightPhaseCategory.C)
    println("    " + unresolvedLevel1.verdict)
    check("Level 3 is still reported", unresolvedLevel1.level == Some(3))
    check("and the miss names Level 1's own ambiguity, not a frequency or a confident vertical failure",
      unresolvedLevel1.verdict.contains("on the boundary for Level 1") &&
        unresolvedLevel1.verdict.contains("unresolved"))

    println("the floor is a frequency, so it scales with the aircraft's size like the CAP boundary does")
    val smallFloored = row(aircraft(wn = 0.95, clAlpha = 2.0f, clTrim = 1.0f, span = 1.5f), FlightPhaseCategory.A)
    println("    " + smallFloored.requirement)
    println("    " + smallFloored.applied.getOrElse("(not scaled)"))
    check("the requirement states the standard's own floor", smallFloored.requirement.contains("wn at least 1.00 rad/s"))
    // The floor scales linearly with frequency ratio sqrt(9.80665/1.5) = 2.557, so 1.0 rad/s becomes 2.56.
    check("and the applied one is scaled by the same frequency ratio as CAP", smallFloored.applied.exists(_.contains("2.56 rad/s")))

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
