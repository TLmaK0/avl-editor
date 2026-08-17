/*
 * The decambering trajectory, pinned by the properties the two published schemes are supposed to have — and
 * by the one behaviour the 2014 scheme exists for: making multiple post-stall solutions visible.
 *
 * <b>The curves here are shapes, not measurements, and that is deliberate.</b> What is under test is an
 * intersection, and an intersection is right or wrong for reasons that have nothing to do with which aerofoil
 * the numbers came from. Building the curves in this file makes the check deterministic and makes it run
 * without XFOIL — which matters here, because a check that excuses itself when a tool is missing is a check
 * that reports success having measured nothing, and this repository has been bitten by that twice. The real
 * aerofoils are `SectionCurveCheck`'s subject, where the numbers are XFOIL's own.
 *
 * The properties:
 *
 *   - a crossing really is on both the line and the curve, to 1e-12 — the strongest statement available, and
 *     it is what "exact, not iterated" means;
 *   - an unstalled (straight) curve has exactly one target whatever the trajectory, which is why nothing
 *     ambiguous happens in normal flight;
 *   - a curve that has turned over can have **two**, which is the multiple-solution behaviour, and both are
 *     reported rather than one of them being landed on;
 *   - a line parallel to the curve has no target, and no curve is extended to invent one;
 *   - a very steep trajectory tends to the 2003/2006 vertical lookup, so the older scheme is the limiting
 *     case of the newer one rather than a different thing;
 *   - the provenance of the curve travels with the target, so a target read past XFOIL's validity says so;
 *   - and no slope is invented when the perturbation moved nothing.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.decambering.DecamberingTrajectoryCheck"
 */
package com.abajar.avleditor.avl.decambering

import com.abajar.avleditor.xfoil.{Measured, PastXfoilValidity, SectionCurve, XfoilPolarPoint}

object DecamberingTrajectoryCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** A section that has not stalled: `cl = 0.11 alpha`, half a degree apart, as XFOIL's own steps are. */
  private def straightCurve: SectionCurve = curveOf("straight",
    (0 to 40).map { i =>
      val a = i * 0.5
      XfoilPolarPoint(a.toFloat, (0.11 * a).toFloat, 0.01f, 0.002f, -0.05f)
    })

  /**
   * A section that gives up: straight to 14 deg, then falling away.
   *
   * The shape — a peak near 14 deg and a fall of about 0.7 of `cl` after it — is the shape a real NACA 2412
   * polar had at Re 190,000 earlier in issue #17. It is used because a curve that never turns over cannot
   * exhibit the thing being tested, not because these are that aerofoil's numbers.
   */
  private def stalledCurve: SectionCurve = curveOf("stalled",
    (0 to 40).map { i =>
      val a = i * 0.5
      val cl = if (a <= 14.0) 0.0946 * a else 1.324 - 0.115 * (a - 14.0)
      XfoilPolarPoint(a.toFloat, cl.toFloat, 0.02f, 0.004f, -0.05f)
    })

  private def curveOf(label: String, points: Seq[XfoilPolarPoint]): SectionCurve =
    SectionCurve.fromPolar(points, 190000.0, label) match {
      case Right(curve) => curve
      case Left(why) => throw new IllegalStateException(s"the check's own curve is not a curve: $why")
    }

  private def targetsOf(outcome: TargetOutcome): Seq[ViscousTarget] = outcome match {
    case OneTarget(t) => Seq(t)
    case SeveralTargets(ts) => ts
    case NoTargetWithin(_, _) => Seq()
  }

  def main(args: Array[String]): Unit = {
    val straight = straightCurve
    val stalled = stalledCurve

    println("the 2003/2006 scheme: straight down, one answer by construction")
    val vertical = DecamberingTrajectory.targetsFor(stalled, 12.0, 1.6, VerticalLookup)
    check("one target, and it is the curve's own value at that attitude", vertical match {
      case OneTarget(t) => t.alphaDeg == 12.0 && math.abs(t.cl - stalled.cl(12.0).value) < 1e-12
      case _ => false
    })

    println("a crossing is on the line and on the curve, which is what makes it exact rather than iterated")
    val slope = -0.05
    val fromCl = 1.2
    val fromAlpha = 12.0
    val crossings = targetsOf(DecamberingTrajectory.targetsFor(stalled, fromAlpha, fromCl, AlongTrajectory(slope)))
    check("every reported crossing lies on the curve to 1e-12",
      crossings.nonEmpty && crossings.forall(t => math.abs(t.cl - stalled.cl(t.alphaDeg).value) < 1e-12))
    check("and on the line of travel to 1e-12",
      crossings.forall(t => math.abs(t.cl - (fromCl + slope * (t.alphaDeg - fromAlpha))) < 1e-12))

    println("an unstalled section has one target, whatever the trajectory — nothing ambiguous in normal flight")
    check("one target at every slope from steeply down to gently up",
      Seq(-2.0, -0.5, -0.2, -0.05, 0.0, 0.05).forall { s =>
        DecamberingTrajectory.targetsFor(straight, 10.0, 1.4, AlongTrajectory(s)) match {
          case OneTarget(_) => true
          case _ => false
        }
      })

    println("a section past its peak can have two, and that is the whole point of the 2014 scheme")
    val ambiguous = DecamberingTrajectory.targetsFor(stalled, fromAlpha, fromCl, AlongTrajectory(slope))
    check("two targets are reported, not one", ambiguous match {
      case SeveralTargets(ts) => ts.length == 2
      case _ => false
    })
    check("one either side of the peak, so they are genuinely different flow states",
      targetsOf(ambiguous) match {
        case Seq(before, after) => before.alphaDeg < 14.0 && after.alphaDeg > 14.0
        case _ => false
      })
    check("they are reported in increasing attitude",
      targetsOf(ambiguous).map(_.alphaDeg) == targetsOf(ambiguous).map(_.alphaDeg).sorted)
    println("    " + DecamberingTrajectory.statementOf(ambiguous))

    // Worth stating as a criterion rather than as the one anchor above, because it says *when* a solver has to
    // be careful: a straight line meets a curve that rises and then falls more than once only if it is
    // shallower than the fall. Steeper than that — which includes the vertical lookup, whose slope is
    // infinite — the answer is unique whatever the section is doing, and the ambiguity the 2014 scheme exists
    // to surface cannot arise at all.
    println("and multiplicity has a criterion: it needs a trajectory shallower than the post-stall fall")
    val anchors = for (a <- 2 to 38; c <- 1 to 30) yield (a * 0.5, c * 0.1)
    def most(slopePerDeg: Double): Int = anchors.map { case (a, c) =>
      targetsOf(DecamberingTrajectory.targetsFor(stalled, a, c, AlongTrajectory(slopePerDeg))).length
    }.max
    check("steeper than the fall, no anchor anywhere gives more than one target",
      Seq(-0.2, -0.5, -2.0, -50.0).forall(s => most(s) == 1))
    check("shallower than the fall, some anchors give two",
      Seq(-0.02, -0.05, -0.08).forall(s => most(s) == 2))

    println("no target is invented where the line does not meet the curve")
    check("a line parallel to the straight curve and below it finds nothing",
      DecamberingTrajectory.targetsFor(straight, 10.0, 0.5, AlongTrajectory(0.11)) match {
        case NoTargetWithin(low, high) => low == straight.lowestMeasuredAlphaDeg &&
          high == straight.highestMeasuredAlphaDeg
        case _ => false
      })

    println("the older scheme is the limit of the newer one, not a different thing")
    val verticalTarget = targetsOf(DecamberingTrajectory.targetsFor(stalled, 12.0, 1.6, VerticalLookup)).head
    val steep = targetsOf(DecamberingTrajectory.targetsFor(stalled, 12.0, 1.6, AlongTrajectory(-1e6))).head
    check("a trajectory of -1e6 lands within 1e-4 deg of the vertical lookup",
      math.abs(steep.alphaDeg - verticalTarget.alphaDeg) < 1e-4)
    check("and within 1e-4 of its lift",
      math.abs(steep.cl - verticalTarget.cl) < 1e-4)

    println("and the two schemes disagree by an amount worth knowing, on the same section")
    val measured = targetsOf(DecamberingTrajectory.targetsFor(stalled, 12.0, 1.6, AlongTrajectory(-0.2))).head
    val gap = measured.cl - verticalTarget.cl
    println(f"    vertical lookup aims at cl ${verticalTarget.cl}%.4f at ${verticalTarget.alphaDeg}%.2f deg; " +
      f"along a -0.2/deg trajectory, cl ${measured.cl}%.4f at ${measured.alphaDeg}%.2f deg — a difference of " +
      f"${gap}%.4f of section lift")
    check("the disagreement is real and not rounding", math.abs(gap) > 1e-3)

    println("the provenance travels with the target, so a target XFOIL cannot vouch for says so")
    check("a target below the stall is Measured",
      targetsOf(DecamberingTrajectory.targetsFor(stalled, 5.0, 0.5, AlongTrajectory(-0.5)))
        .forall(_.provenance.provenance == Measured))
    check("of the two ambiguous targets, the one past where the aerofoil gave up says so",
      targetsOf(ambiguous) match {
        case Seq(before, after) =>
          before.provenance.provenance == Measured && after.provenance.provenance == PastXfoilValidity
        case _ => false
      })

    println("no slope is invented")
    check("a perturbation that moved the attitude gives the slope through the two points",
      DecamberingTrajectory.slopeFrom(10.0, 1.0, 12.0, 1.2).exists(s => math.abs(s - 0.1) < 1e-12))
    check("a perturbation that moved nothing gives no slope at all",
      DecamberingTrajectory.slopeFrom(10.0, 1.0, 10.0, 0.8).isEmpty)

    println(if (ok) "DECAMBERING_TRAJECTORY_OK" else "DECAMBERING_TRAJECTORY_FAIL")
    if (!ok) sys.exit(1)
  }
}
