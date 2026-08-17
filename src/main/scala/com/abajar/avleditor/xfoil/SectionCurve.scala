/*
 * The known section data the decambering feeds on: one aerofoil's viscous curve, and where it stops being
 * a measurement.
 *
 * The iterated decambering of Mukherjee, Gopalarathnam & Kim (AIAA 2003-1097) forms, at every iteration and
 * every station, the residual `dCl = (Cl)visc - (Cl)sec` against the **known section data** evaluated at that
 * station's effective attitude (Eq. 10, p. 7). `SectionStallData` cannot answer that: it is one number, the
 * peak. What is needed is the curve, at any attitude the iteration asks about — and, because this project
 * refuses to let a fabricated value pass as a measured one, an answer that says **where it came from**.
 *
 * Hence {@link SectionSample}: every value carries its provenance. Four kinds, and the distinctions are the
 * whole point of this class:
 *
 *   - `Measured` — inside XFOIL's converged range and below the stall. XFOIL is a coupled viscous-inviscid
 *     method and this is where it is a viscous measurement.
 *   - `PastXfoilValidity` — XFOIL's own output, past the stall, where **XFOIL is not valid**. Massive
 *     separation is not modelled by the coupled formulation and is reachable only through empirical
 *     corrections; the literature search in issue #17 established this, and the earlier draft of that issue
 *     called such numbers "measured" when they were not. They are still the best section data available and
 *     the paper's own figures are XFOIL polars, so they are *used* — but never described as measurements.
 *   - `AcrossAGap` — XFOIL failed to converge on both sides of the attitude asked about, so the value is an
 *     interpolation across a hole rather than between neighbours. The gap is reported in degrees, because near
 *     the stall the holes are exactly where the decambering will be looking.
 *   - `HeldAtLastPoint` — beyond anything XFOIL converged at all. It holds rather than extrapolating, the same
 *     rule the exported tables already follow, and it says so.
 *
 * And one number is reported rather than judged: **how much the curve comes back up after its peak**. A
 * genuine stall does not recover; a laminar separation bubble bursting drops the lift and then the flow
 * reattaches turbulently and the lift climbs again. That is the defect in issue #43 — a bubble bursting at
 * 8.5 deg read as the tailplane's stall — and the classification belongs there, with the measurement that
 * found it. Here the shape is simply stated, so nothing downstream has to guess at it: no invented threshold,
 * and no silence about it either.
 */
package com.abajar.avleditor.xfoil

/** Where a value on a section curve came from. Never absent, so nothing can be mistaken for a measurement. */
sealed trait SectionProvenance
case object Measured extends SectionProvenance
case object PastXfoilValidity extends SectionProvenance
case class AcrossAGap(widthDeg: Double) extends SectionProvenance
case class HeldAtLastPoint(byDeg: Double) extends SectionProvenance

/** One value off a section curve, with where it came from. */
case class SectionSample(alphaDeg: Double, value: Double, provenance: SectionProvenance) {
  /** Whether this is a viscous measurement of an attached aerofoil, which only `Measured` is. */
  def isMeasured: Boolean = provenance == Measured
}

/**
 * One aerofoil's viscous curve at one Reynolds number, with its validity.
 *
 * @param stallAlphaDeg where the aerofoil gives up, when XFOIL showed it giving up at all. `None` is a real
 *                      answer and not an error: a 10 % symmetric section at Re 60,000 — an ordinary model
 *                      tailplane — comes back still climbing at 20 deg, because the code does not model the
 *                      separation that would end it. Such a station has no known limit, takes no part in
 *                      deciding where the aircraft stalls, and is named.
 */
case class SectionCurve(label: String, reynolds: Double, points: Seq[XfoilPolarPoint],
                        stallAlphaDeg: Option[Double], recoveryAfterPeak: Double,
                        dropAfterPeak: Double) {

  private val ordered = points.sortBy(_.alpha.toDouble)

  /**
   * A step wider than this is a hole XFOIL failed to converge across, rather than a step of the sweep.
   *
   * Taken from **this polar's own** typical spacing — half again the median step — and not from the sweep
   * constant the editor happens to ask for. That was the first way it was written and it is wrong in a way
   * worth recording: with the threshold pinned to the 0.5 deg sweep, a polar measured at any coarser step
   * reports *every* value as interpolated across a gap, and a warning that fires on everything carries no
   * information at all. A curve that describes its own resolution cannot make that mistake.
   */
  val widestStepThatIsNotAGap: Double = {
    val steps = ordered.sliding(2).collect {
      case Seq(low, high) => high.alpha.toDouble - low.alpha.toDouble
    }.toSeq.sorted
    if (steps.isEmpty) Double.MaxValue else 1.5 * steps(steps.length / 2)
  }

  def lowestMeasuredAlphaDeg: Double = ordered.head.alpha.toDouble
  def highestMeasuredAlphaDeg: Double = ordered.last.alpha.toDouble

  /** The largest lift XFOIL converged at, whether or not it is a stall. */
  def peakCl: Double = ordered.map(_.cl.toDouble).max

  /** Up to here the curve is a viscous measurement of an attached aerofoil; past it, it is not. */
  def validToDeg: Double = stallAlphaDeg.getOrElse(highestMeasuredAlphaDeg)

  def cl(alphaDeg: Double): SectionSample = sample(alphaDeg, _.cl.toDouble)
  def cm(alphaDeg: Double): SectionSample = sample(alphaDeg, _.cm.toDouble)
  def cd(alphaDeg: Double): SectionSample = sample(alphaDeg, _.cd.toDouble)

  /**
   * The curve at one attitude, linearly between the two converged points bracketing it.
   *
   * Linear, and deliberately not a spline: XFOIL's points are half a degree apart where they converge, so a
   * straight line between neighbours is well inside the scatter, while a spline through a polar with holes in
   * it near the stall overshoots exactly where the answer matters.
   */
  private def sample(alphaDeg: Double, of: XfoilPolarPoint => Double): SectionSample = {
    val provenanceAt = (from: SectionProvenance) =>
      if (from == Measured && alphaDeg > validToDeg) PastXfoilValidity else from

    if (alphaDeg <= lowestMeasuredAlphaDeg)
      return SectionSample(alphaDeg, of(ordered.head),
        if (alphaDeg == lowestMeasuredAlphaDeg) provenanceAt(Measured)
        else HeldAtLastPoint(lowestMeasuredAlphaDeg - alphaDeg))
    if (alphaDeg >= highestMeasuredAlphaDeg)
      return SectionSample(alphaDeg, of(ordered.last),
        if (alphaDeg == highestMeasuredAlphaDeg) provenanceAt(Measured)
        else HeldAtLastPoint(alphaDeg - highestMeasuredAlphaDeg))

    val bracket = ordered.sliding(2).collectFirst {
      case Seq(low, high) if alphaDeg >= low.alpha.toDouble && alphaDeg <= high.alpha.toDouble => (low, high)
    }.get
    val (low, high) = bracket
    val width = high.alpha.toDouble - low.alpha.toDouble
    val fraction = if (width <= 0.0) 0.0 else (alphaDeg - low.alpha.toDouble) / width
    val value = of(low) + (of(high) - of(low)) * fraction
    val gapped = width > widestStepThatIsNotAGap
    SectionSample(alphaDeg, value,
      if (gapped) AcrossAGap(width) else provenanceAt(Measured))
  }

  /** What this curve is and is not, in one line, for the log and for the exported file's provenance. */
  def validityStatement: String = {
    val stall = stallAlphaDeg match {
      case Some(alpha) => f"gives up at $alpha%.1f deg with cl ${peakCl}%.3f"
      case None => f"has no known limit — still climbing at ${highestMeasuredAlphaDeg}%.1f deg, " +
        "so XFOIL never showed it stall"
    }
    f"$label%s at Re ${reynolds.toLong}%d: ${points.length}%d converged points from " +
      f"${lowestMeasuredAlphaDeg}%.1f to ${highestMeasuredAlphaDeg}%.1f deg, $stall%s. " +
      f"Viscous measurement to ${validToDeg}%.1f deg; past that the values are XFOIL's own and XFOIL does " +
      "not model massive separation." +
      (if (recoveryAfterPeak > 0.0)
        f" After its peak the curve falls ${dropAfterPeak}%.3f of cl and then recovers " +
        f"${recoveryAfterPeak}%.3f, ${100 * recoveryFraction}%.0f %% of the fall; whether such a peak is the " +
        "stall or a bursting laminar bubble is issue #43's question, decided there and not here."
      else "")
  }

  /**
   * How much of the fall after the peak the curve takes back — the discriminator, as a number rather than as
   * a verdict. A stall falls and stays down; a bubble bursting drops the lift and then the flow reattaches.
   */
  def recoveryFraction: Double = if (dropAfterPeak <= 0.0) 0.0 else recoveryAfterPeak / dropAfterPeak
}

object SectionCurve {

  /**
   * The curve of one aerofoil, out of an XFOIL polar — or the reason there is not one, never a curve standing
   * in for one.
   *
   * The stall is read through `SectionStall`, so where the aerofoil gives up is decided in exactly one place
   * and this cannot come to a different answer from the stall speed the editor already reports.
   */
  def fromPolar(polar: Seq[XfoilPolarPoint], reynolds: Double, label: String)
              : Either[String, SectionCurve] = {
    if (polar.isEmpty) return Left(s"$label: XFOIL converged at no attitude at all")
    if (polar.length < SectionStall.LeastUsablePoints)
      return Left(f"$label%s: XFOIL converged at only ${polar.length}%d attitudes, too few to be a curve")

    val ordered = polar.sortBy(_.alpha.toDouble)
    val stall = SectionStall.fromPolar(polar, reynolds).right.toOption.map(_.alphaAtClMaxDeg)
    val (fall, recovery) = shapeAfterPeak(ordered)
    Right(SectionCurve(label, reynolds, ordered, stall, recovery, fall))
  }

  /**
   * What the curve does after its peak: how far it falls, and how much of that it takes back.
   *
   * The fall is from the peak to the lowest point after it; the recovery is from there to the highest point
   * after *that* — the shape a reattaching flow leaves behind. Both zero for a curve that falls and stays
   * down, which is what a stall looks like. Reported and not acted on here: see issue #43.
   */
  private def shapeAfterPeak(ordered: Seq[XfoilPolarPoint]): (Double, Double) = {
    val peakIndex = ordered.indices.maxBy(i => ordered(i).cl)
    val after = ordered.drop(peakIndex + 1)
    if (after.length < 2) return (0.0, 0.0)
    val troughIndex = after.indices.minBy(i => after(i).cl)
    val trough = after(troughIndex).cl.toDouble
    val fall = math.max(0.0, ordered(peakIndex).cl.toDouble - trough)
    val past = after.drop(troughIndex + 1)
    val recovery = if (past.isEmpty) 0.0 else math.max(0.0, past.map(_.cl.toDouble).max - trough)
    (fall, recovery)
  }
}
