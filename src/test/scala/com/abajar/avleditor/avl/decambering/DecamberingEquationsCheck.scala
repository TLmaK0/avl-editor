/*
 * The decambering equations, checked against the properties the paper states about them.
 *
 * These are four lines of algebra out of AIAA 2003-1097, and four lines of algebra are exactly the kind of
 * thing that gets transcribed with a sign wrong and then measured against nothing for months. So they are
 * pinned by **properties**, not by numbers copied from the same source they came from:
 *
 *   - the inversion really inverts: a pair of deltas produces residuals, and Eqs. 1-2 recover the same pair;
 *   - the moment residual is answered by `delta2` alone, which is why Eq. 1 stands on its own;
 *   - the one-variable reduction the paper sanctions on p. 7 collapses to `dCl / 2 pi`, thin-aerofoil theory's
 *     own answer;
 *   - Eq. 10 with nothing decambered is `cl = 2 pi alpha`, which is AVL's own section slope to 0.3 % as
 *     measured earlier in issue #17;
 *   - and all of it holds across the paper's stated hinge range of 0.5 to 0.9, not merely at 0.8.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.decambering.DecamberingEquationsCheck"
 */
package com.abajar.avleditor.avl.decambering

object DecamberingEquationsCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  import DecamberingEquations._

  /** AVL's own measured section lift slope, per degree, from the AR 1000 run recorded in issue #17. */
  private val AvlSectionSlopePerDegree = 0.11000

  /** The hinge positions the paper says all work (p. 5), walked rather than trusted at one value. */
  private val Hinges = Seq(0.5, 0.6, 0.7, 0.8, 0.9)

  def main(args: Array[String]): Unit = {
    println("Eq. 3: where the second decambering function starts")
    println(f"  x2 = $SecondFunctionHinge%.2f gives theta2 = ${theta2()}%.4f rad = ${degreesOf(theta2())}%.2f deg")
    check("theta2 = acos(1 - 2 x2), which at x2 = 0.8 is acos(-0.6)",
      math.abs(theta2() - math.acos(-0.6)) < 1e-12)
    check("the hinge at mid-chord puts theta2 at a right angle",
      math.abs(theta2(0.5) - math.Pi / 2) < 1e-12)
    check("a hinge further back puts it further round, monotonically",
      Hinges.sliding(2).forall { case Seq(a, b) => theta2(a) < theta2(b) })
    check("and the paper's stated range is the one the code names",
      UsableHingeRange == (0.5, 0.9) && Hinges.head == UsableHingeRange._1 &&
        Hinges.last == UsableHingeRange._2)

    println("the coefficients, and the one that is zero for a reason")
    println(f"  lift per delta1 = $liftPerDelta1%.6f (2 pi), per delta2 = ${liftPerDelta2()}%.6f")
    println(f"  moment per delta1 = $momentPerDelta1%.6f, per delta2 = ${momentPerDelta2()}%.6f")
    check("delta1 lifts by 2 pi per radian, thin-aerofoil theory", math.abs(liftPerDelta1 - 2 * math.Pi) < 1e-12)
    // A whole-chord incidence change adds its lift at the quarter chord, so it makes no moment about it.
    check("delta1 makes no moment about the quarter chord, which is why Eq. 1 solves delta2 alone",
      momentPerDelta1 == 0.0)
    check("a flap behind the mid-chord lifts less than the whole chord does",
      Hinges.forall(h => liftPerDelta2(h) < liftPerDelta1 && liftPerDelta2(h) > 0))
    check("and it pitches nose-down for positive camber, so the moment coefficient is negative",
      Hinges.forall(h => momentPerDelta2(h) < 0))
    check("a flap further back lifts less, monotonically",
      Hinges.sliding(2).forall { case Seq(a, b) => liftPerDelta2(a) > liftPerDelta2(b) })
    // This is where an assumption of mine was wrong and the measurement is more interesting than the guess.
    // Moment authority about the quarter chord is *not* monotonic in hinge position: a flap too far forward is
    // little more than an incidence change and makes almost no moment, and one too far back has too little
    // chord behind it to make any either. It peaks in between.
    val scanned = (40 to 95).map(i => (i / 100.0, math.abs(momentPerDelta2(i / 100.0))))
    val (bestHinge, bestMoment) = scanned.maxBy(_._2)
    println(f"  moment authority peaks at x2 = $bestHinge%.2f (${bestMoment}%.4f); the paper's " +
      f"$SecondFunctionHinge%.2f gives ${math.abs(momentPerDelta2())}%.4f, " +
      f"${100 * math.abs(momentPerDelta2()) / bestMoment}%.1f %% of it")
    check("moment authority is not monotonic in hinge position but peaks near three-quarter chord",
      bestHinge > 0.7 && bestHinge < 0.8)
    // Which is very likely why the paper chose 0.8 and why it says 0.5 to 0.9 all work: the 2x2 system is
    // solved for delta2 from the moment residual, so a hinge with little moment authority would divide by a
    // small number and be ill-conditioned.
    check("the paper's x2 = 0.8 sits within a couple of percent of that peak",
      math.abs(momentPerDelta2()) > 0.97 * bestMoment)
    check("and its whole stated range keeps most of that authority, which is what makes the range usable",
      Hinges.forall(h => math.abs(momentPerDelta2(h)) > 0.75 * bestMoment))

    println("Eqs. 1 and 2 invert the relation they are the inversion of")
    val pairs = Seq((-0.05, -0.02), (0.03, 0.01), (-0.2, 0.0), (0.0, -0.03), (-0.11, 0.07))
    val worst = Hinges.flatMap { hinge =>
      pairs.map { case (delta1, delta2) =>
        val (deltaCl, deltaCm) = residualsFrom(delta1, delta2, hinge)
        val (backOne, backTwo) = deltasFrom(deltaCl, deltaCm, hinge)
        math.max(math.abs(backOne - delta1), math.abs(backTwo - delta2))
      }
    }.max
    println(f"  ${Hinges.length * pairs.length}%d cases across the whole hinge range: worst error $worst%.3e")
    check("every pair of deltas is recovered from the residuals it produces", worst < 1e-12)

    println("the one-variable case the paper sanctions on p. 7")
    check("with delta2 = 0, Eq. 2 is dCl / 2 pi",
      Seq(-0.3, -0.05, 0.0, 0.08).forall(dCl =>
        math.abs(delta1From(dCl, 0.0) - delta1Only(dCl)) < 1e-15))
    check("and that is the incidence change thin-aerofoil theory says makes that much lift",
      Seq(-0.3, -0.05, 0.08).forall(dCl =>
        math.abs(liftPerDelta1 * delta1Only(dCl) - dCl) < 1e-15))
    check("a zero moment residual asks for no second function at all", delta2From(0.0) == 0.0)

    println("Eq. 10, the effective attitude the section data is looked up at")
    check("with nothing decambered it is thin-aerofoil theory read backwards",
      Seq(0.2, 0.6, 1.1).forall(cl =>
        math.abs(effectiveAttitude(cl, 0.0, 0.0) - cl / (2 * math.Pi)) < 1e-15))
    // Measured against AVL rather than asserted from theory: an AR 1000 NACA 2412 gave 0.11000 per degree.
    val theoretical = degreesOf(1.0) * 0 + liftPerDelta1 * radiansOf(1.0)
    println(f"  2 pi per radian is $theoretical%.5f per degree, against AVL's measured " +
      f"$AvlSectionSlopePerDegree%.5f — a ratio of ${AvlSectionSlopePerDegree / theoretical}%.4f")
    check("2 pi is AVL's own section slope to better than half a percent",
      math.abs(AvlSectionSlopePerDegree / theoretical - 1.0) < 0.005)
    // Decambering removes lift at a given attitude, which is the same statement as: to carry the lift it
    // still carries, a decambered section must be sitting at a higher attitude than an undecambered one.
    check("decambering raises the attitude a given section lift corresponds to",
      effectiveAttitude(0.8, -0.05, 0.0) > effectiveAttitude(0.8, 0.0, 0.0))
    check("both functions push it the same way", Hinges.forall(h =>
      effectiveAttitude(0.8, 0.0, -0.05, h) > effectiveAttitude(0.8, 0.0, 0.0, h)))
    check("and the second function's share of it is between none and all of the first's",
      Hinges.forall { h =>
        val fromSecond = effectiveAttitude(0.8, 0.0, -0.05, h) - effectiveAttitude(0.8, 0.0, 0.0, h)
        val fromFirst = effectiveAttitude(0.8, -0.05, 0.0, h) - effectiveAttitude(0.8, 0.0, 0.0, h)
        fromSecond > 0 && fromSecond < fromFirst
      })

    println("radians in, radians out, and one place that converts")
    check("the conversions are each other's inverse",
      Seq(-8.0, 0.0, 12.5, 35.0).forall(d => math.abs(degreesOf(radiansOf(d)) - d) < 1e-12))
    check("a degree is not a radian, which is the whole reason this is stated",
      math.abs(radiansOf(1.0) - 1.0) > 0.9)

    // The sign policy is Hugo's open decision and is deliberately absent from the algebra: the equations
    // answer with whatever sign the residual implies, in both directions.
    println("the algebra takes no view on the sign, which is a decision held elsewhere")
    check("a positive lift residual gives a positive delta1 and a negative one a negative delta1",
      delta1Only(0.1) > 0 && delta1Only(-0.1) < 0)
    check("and the two are exact mirrors, so nothing is clamped in here",
      math.abs(delta1Only(0.1) + delta1Only(-0.1)) < 1e-18)

    println(if (ok) "DECAMBERING_EQUATIONS_OK" else "DECAMBERING_EQUATIONS_FAIL")
    if (!ok) sys.exit(1)
  }
}
