/*
 * MIL-F-8785C 3.2.1.3, flight-path stability: the criterion, and the physics under it.
 *
 * The physics is one identity and it is most of this check: at a fixed throttle the flight-path angle
 * slopes with speed as the drag does, so `dgamma/dV` crosses zero at the minimum-drag speed and nowhere
 * else. Everything the criterion says — Level 1 on the front of the drag curve, worse on the back —
 * follows from that, so it is asserted directly rather than through a verdict's wording.
 *
 * The aircraft here are synthetic on purpose. A parabolic polar has a minimum-drag speed that can be
 * worked out on paper, which is what makes the crossing checkable at all; AVL's own polar is checked
 * against JSBSim elsewhere, and this file is about what is done with one. Two of them, because the
 * standard is written for full-scale aircraft and applied verbatim there: an 11 m light aircraft is judged
 * by MIL-F-8785C's own numbers, and a 1.2 m model by what its size makes of them.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.runcase.FlightPathStabilityCheck"
 */
package com.abajar.avleditor.avl.runcase

object FlightPathStabilityCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private val Density = 1.225
  private val Gravity = 9.80665

  /** An aeroplane: span, mass, wing, a parabolic polar `CD = CD0 + k CL^2`, and where it stalls. */
  private case class Aeroplane(spanMetres: Float, massKg: Float, areaM2: Float,
                               cd0: Double, k: Double, clMax: Double) {
    def weight: Double = massKg * Gravity
    /** Minimum drag is where the parasite and induced parts are equal: `CD0 = k CL^2`. */
    def liftAtMinimumDrag: Double = math.sqrt(cd0 / k)
    def speedAt(cl: Double): Double = math.sqrt(2.0 * weight / (Density * areaM2 * cl))
    def minimumDragSpeed: Double = speedAt(liftAtMinimumDrag)
    def stallSpeed: Double = speedAt(clMax)

    def calculation: AvlCalculation = {
      val calc = new AvlCalculation(0, 1, 2)
      val config = new Configuration
      config.setBref(spanMetres)
      config.setSref(areaM2)
      config.setMetresPerLengthUnit(1f)
      config.setSecondsPerTimeUnit(1f)
      config.setVelocity(14f)
      config.setCLtot(0.4f)
      config.setAnalysisInertias(massKg, 0.03f, 0.05f, 0f, Density.toFloat)
      config.setStall(stallSpeed.toFloat, clMax.toFloat, "wing at y = 0.300")
      calc.setConfiguration(config)

      // A lift curve of 0.1 per degree with 2 degrees of zero-lift angle, over the thirteen attitudes the
      // real sweep measures.
      val sweep = new java.util.ArrayList[AlphaSweepPoint]()
      for (i <- -4 to 8) {
        val alpha = 2.5f * i
        val cl = 0.1 * (alpha + 2.0)
        sweep.add(new AlphaSweepPoint(alpha, cl.toFloat, (cd0 + k * cl * cl).toFloat, 0f,
          5.7f, -0.8f, 0.07f, -0.04f))
      }
      calc.setAlphaSweep(sweep)
      calc.setEigenvalues(new java.util.ArrayList[AvlEigenvalue]())
      val stab = new StabilityDerivatives
      stab.initControls(3)
      stab.setCLa(5.7f); stab.setCnb(0.07f); stab.setCYb(-0.2f); stab.setClb(-0.04f); stab.setClp(-0.4f)
      calc.setStabilityDerivatives(stab)
      calc.setControlGains(Array(1f, 1f, 20f))
      calc.setControlMaxDeflections(Array(30f, 30f, 25f))
      calc
    }
  }

  private def row(plane: Aeroplane, category: FlightPhaseCategory): ModalNormRow =
    MilF8785cEvaluator.evaluate(plane.calculation, category).find(_.modeName == "Flight-path stability").get

  private def slope(plane: Aeroplane, speed: Double): Either[String, Double] =
    MilF8785cEvaluator.flightPathSlopeDegreesPerKnot(plane.calculation, speed)

  /** Where the slope changes sign, found by bisection between a back-side and a front-side speed. */
  private def crossing(plane: Aeroplane): Double = {
    var low = plane.minimumDragSpeed * 0.6
    var high = plane.minimumDragSpeed * 1.6
    for (_ <- 1 to 60) {
      val middle = 0.5 * (low + high)
      if (slope(plane, middle).right.getOrElse(0.0) > 0.0) low = middle else high = middle
    }
    0.5 * (low + high)
  }

  def main(args: Array[String]): Unit = {
    // An ordinary 1.17 kg model: 1.2 m of span over 0.24 m2, and a polar to match.
    val model = Aeroplane(1.2f, 1.17f, 0.24f, 0.020, 0.059, 1.2)
    // And a full-size one, which the standard is written for and is quoted verbatim at: a one-tonne light
    // aircraft on 16 m2, with the flaps down. That is not an arbitrary choice — 3.2.1.3 is a landing-approach
    // requirement, its limit is 0.06 deg/knot, and `dgamma/dV` carries a 1/V in it, so it only ever bites on
    // an aircraft that approaches slowly. A jet at 130 knots cannot fail it whatever its polar looks like.
    val fullSize = Aeroplane(11f, 1000f, 16f, 0.035, 0.060, 2.5)

    println("the slope of the flight path against speed")
    println(f"  the model's minimum drag is at CL ${model.liftAtMinimumDrag}%.3f, " +
      f"which is ${model.minimumDragSpeed}%.2f m/s")
    val below = slope(model, model.minimumDragSpeed * 0.85)
    val above = slope(model, model.minimumDragSpeed * 1.15)
    println(f"  at 0.85 and 1.15 of it: ${below.right.get}%+.4f, ${above.right.get}%+.4f deg/knot")
    check("positive below it — the back of the drag curve", below.right.exists(_ > 0.01))
    check("negative above it — the front", above.right.exists(_ < -0.01))

    // The crossing is not asserted to sit exactly on the analytic minimum-drag speed, and the difference
    // is the point: the drag here is AVL's measured curve read between the attitudes it measured, and a
    // straight line between two points of a parabola does not have the parabola's minimum. It has to land
    // close to it, and it does — which is what says the derivation is drag and nothing else.
    // Sharper than a percentage: with `CD` linear in `CL` on each segment, `D = a q + b W` is linear in
    // `q` there, so the drag has no minimum inside a segment at all — the sign of `a` changes at a knot,
    // and the crossing lands on one. The right assertion is therefore that it lands on the segment
    // containing the true minimum-drag speed, which is as much as thirteen attitudes can resolve.
    val where = crossing(model)
    val knots = model.calculation.getAlphaSweep.toArray
      .map(_.asInstanceOf[AlphaSweepPoint]).map(p => model.speedAt(p.getCl.toDouble))
      .filter(v => !v.isNaN && !v.isInfinite).sorted
    val bracket = (knots.filter(_ <= model.minimumDragSpeed).lastOption,
                   knots.find(_ >= model.minimumDragSpeed))
    println(f"  the slope changes sign at $where%.2f m/s, ${100 * (where / model.minimumDragSpeed - 1)}%+.1f %% " +
      "from the polar's own minimum-drag speed")
    println(f"  the swept attitudes either side of that speed are at " +
      f"${bracket._1.getOrElse(Double.NaN)}%.2f and ${bracket._2.getOrElse(Double.NaN)}%.2f m/s")
    check("the sign changes within the segment holding the minimum-drag speed",
      bracket._1.exists(low => bracket._2.exists(high => where >= low - 1e-6 && where <= high + 1e-6)))

    // Which way the crossing moves is a property of the aeroplane, and the two kinds of drag move it in
    // opposite directions: CL at minimum drag is sqrt(CD0/k).
    val draggier = model.copy(cd0 = 2 * model.cd0)
    val dirtier = model.copy(k = 2 * model.k)
    println(f"  twice the parasite drag: ${crossing(draggier)}%.2f m/s; " +
      f"twice the induced: ${crossing(dirtier)}%.2f m/s")
    check("more parasite drag moves the crossing down in speed", crossing(draggier) < where)
    check("more induced drag moves it up", crossing(dirtier) > where)

    println("the criterion, in the Flight Phase it is stated for")
    val backSide = row(fullSize, FlightPhaseCategory.C)
    println(f"  approach ${1.3 * fullSize.stallSpeed}%.1f m/s against a minimum-drag " +
      f"${fullSize.minimumDragSpeed}%.1f m/s: on the back")
    println("  " + backSide.verdict)
    check("a back-side approach does not reach Level 1", backSide.level != Some(1))
    check("but is still flyable rather than off the bottom of the standard", backSide.level.isDefined)
    check("and says the aircraft is on the back of the drag curve",
      backSide.verdict.contains("back of the drag curve"))

    val front = fullSize.copy(clMax = 1.2)
    val frontSide = row(front, FlightPhaseCategory.C)
    println(f"  approach ${1.3 * front.stallSpeed}%.1f m/s against the same " +
      f"${front.minimumDragSpeed}%.1f m/s: on the front")
    println("  " + frontSide.verdict)
    check("a front-side approach reaches Level 1",
      frontSide.level == Some(1) && frontSide.pass == Some(true))
    check("it names the speed it was measured at, and what that is a multiple of",
      frontSide.verdict.contains("times the") && frontSide.verdict.contains("stall"))
    check("and reports the standard's second sentence separately",
      frontSide.verdict.contains("second sentence"))

    println("the Flight Phases it is not stated for")
    Seq(FlightPhaseCategory.A, FlightPhaseCategory.B).foreach { category =>
      val other = row(fullSize, category)
      check(s"Category ${category.label} is not judged, and not passed either",
        other.outcome == RowOutcome.DoesNotApply && other.level.isEmpty && other.pass.isEmpty)
      check(s"Category ${category.label} says which phase would be judged",
        other.verdict.contains("landing approach") && other.verdict.contains("Category C"))
    }

    println("the aircraft's own size")
    val small = row(model, FlightPhaseCategory.C)
    println("  1.2 m: " + small.applied.getOrElse("(quoted as written)"))
    println("   11 m: " + backSide.applied.getOrElse("(quoted as written)"))
    // Degrees per knot is an angle over a speed. Under Froude scaling a speed goes as sqrt(b), so an
    // inverse speed carries the same power of the span as a frequency does and takes the same factor.
    check("a full-size aircraft is judged by the standard as written", backSide.applied.isEmpty)
    val ratio = math.sqrt(9.80665 / 1.2)
    check("a model is allowed a steeper slope, by exactly sqrt(bref/b)",
      small.applied.exists(_.contains(f"${0.06 * ratio}%.2f")))
    check("which is more permissive than the stated figure, never less", 0.06 * ratio > 0.06)

    println("what it refuses to answer")
    val noStall = fullSize.calculation
    noStall.getConfiguration.setStallProblem("XFOIL is not configured.")
    val unmeasured = MilF8785cEvaluator.evaluate(noStall, FlightPhaseCategory.C)
      .find(_.modeName == "Flight-path stability").get
    println("  " + unmeasured.verdict)
    check("without a stall speed there is no approach speed, and it says so",
      unmeasured.outcome == RowOutcome.NotJudged && unmeasured.level.isEmpty)
    check("and it repeats the reason the stall was not measured",
      unmeasured.verdict.contains("XFOIL is not configured"))

    val noSweep = fullSize.calculation
    noSweep.setAlphaSweep(new java.util.ArrayList[AlphaSweepPoint]())
    check("without a drag curve there is no slope, and it says so",
      MilF8785cEvaluator.evaluate(noSweep, FlightPhaseCategory.C)
        .find(_.modeName == "Flight-path stability").get.outcome == RowOutcome.NotJudged)

    // An approach that needs more lift than AVL was ever asked about: reading the drag out there would be
    // extrapolating the very curve this measurement replaces.
    val beyond = row(fullSize.copy(clMax = 5.0), FlightPhaseCategory.C)
    println("  " + beyond.verdict)
    check("an approach outside the measured attitudes is refused rather than extrapolated",
      beyond.outcome == RowOutcome.NotJudged && beyond.verdict.contains("AVL measured"))

    println("the approach speed itself")
    check("1.3 times the stall, from 14 CFR 23.73 and not from MIL-F-8785C",
      MilF8785cEvaluator.ApproachSpeedFactorOfStall == 1.3)
    check("the row states the multiple it used", frontSide.verdict.contains("1.3 times"))

    println(if (ok) "FLIGHT_PATH_STABILITY_OK" else "FLIGHT_PATH_STABILITY_FAIL")
    if (!ok) sys.exit(1)
  }
}
