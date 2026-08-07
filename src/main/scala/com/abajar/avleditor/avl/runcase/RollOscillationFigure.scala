/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.avl.runcase

/**
 * MIL-F-8785C FIGURE 4 (p. 25): the roll-rate oscillation limits of 3.3.2.2.1, as `posc/pav` against the
 * phase angle `psi_beta`.
 *
 * **These vertices are ours, not the standard's.** Figures 1 to 3 carry their value printed up the
 * right-hand edge, so reading them was transcription; this one is a genuine graph and its corners had to be
 * measured off the page. The vertical values land on the plotted grid and are exact; the horizontal ones do
 * not, and are good to about ten degrees. That is recorded in `docs/mil-f-8785c.md` beside the table, and
 * it is why [[within]] answers `Unclear` near a boundary rather than asserting a Level: a digitised vertex
 * must never be used to *widen* the pass band, which would let through aircraft the standard fails.
 *
 * The middle boundary serves **two** requirements — Category B Level 1 and Categories A&C Level 2 — which
 * is the same coincidence the 3.3.2.2 table shows, where both are 25 %.
 */
object RollOscillationFigure {

  /** How far off a boundary the reading is worth trusting, in the units of the plot's vertical axis. */
  val ReadingUncertainty = 0.02

  /** Degrees of `psi_beta` the horizontal reading is good to. */
  val PhaseUncertaintyDegrees = 10.0

  sealed trait Verdict
  case object Inside extends Verdict
  case object Outside extends Verdict
  /** Within the accuracy the figure was read to. No Level is asserted here, and the verdict says why. */
  case object Unclear extends Verdict

  /**
   * One boundary, as the four corners of `flat, rise, plateau, fall` plus the value it ends at. `psi_beta`
   * runs from 0 to -360 degrees, so the breakpoints are increasingly negative.
   */
  final case class Boundary(name: String, flat: Double, flatUntil: Double, plateauFrom: Double,
                            plateau: Double, fallFrom: Double, endValue: Double) {

    /** The limit at a given phase angle, interpolated along the polyline. */
    def at(psiBeta: Double): Double = {
      val psi = math.max(-360.0, math.min(0.0, psiBeta))
      if (psi >= flatUntil) flat
      else if (psi >= plateauFrom) interpolate(psi, flatUntil, flat, plateauFrom, plateau)
      else if (psi >= fallFrom) plateau
      else interpolate(psi, fallFrom, plateau, -360.0, endValue)
    }

    private def interpolate(x: Double, x0: Double, y0: Double, x1: Double, y1: Double): Double =
      if (math.abs(x1 - x0) < 1e-9) y0 else y0 + (y1 - y0) * (x - x0) / (x1 - x0)
  }

  /** Figure 4's three drawn boundaries. See `docs/mil-f-8785c.md` for the reading and its uncertainty. */
  val Upper = Boundary("Category B Level 2", 0.20, -110.0, -200.0, 1.00, -290.0, 0.20)
  val Middle = Boundary("Category B Level 1 and Categories A&C Level 2", 0.10, -120.0, -200.0, 0.60, -270.0, 0.10)
  val Lower = Boundary("Categories A&C Level 1", 0.05, -130.0, -180.0, 0.25, -270.0, 0.05)

  /** The boundary a given Flight Phase and Level is judged against, or None where the figure states none. */
  def boundaryFor(category: FlightPhaseCategory, level: Int): Option[Boundary] =
    (category, level) match {
      case (FlightPhaseCategory.B, 1) => Some(Middle)
      case (FlightPhaseCategory.B, 2) => Some(Upper)
      case (_, 1)                     => Some(Lower)
      case (_, 2)                     => Some(Middle)
      case _                          => None
    }

  /**
   * Is the aircraft inside the boundary, outside it, or too close to the line to say?
   *
   * The horizontal uncertainty matters as much as the vertical one, because on the steep part of the
   * polyline ten degrees is worth a great deal of `posc/pav`. So the boundary is evaluated across the phase
   * angle's own uncertainty band and the answer only committed to when it holds right across it.
   */
  def within(boundary: Boundary, psiBeta: Double, ratio: Double): Verdict = {
    val limits = List(psiBeta - PhaseUncertaintyDegrees, psiBeta, psiBeta + PhaseUncertaintyDegrees)
      .map(boundary.at)
    val lowest = limits.min - ReadingUncertainty
    val highest = limits.max + ReadingUncertainty
    if (ratio <= lowest) Inside
    else if (ratio >= highest) Outside
    else Unclear
  }

  /**
   * `psi_beta`, from MIL-F-8785C 6.2.6 (p. 81): "phase angle expressed as a lag for a cosine representation
   * of the Dutch roll oscillation in sideslip",
   *
   * {{{ psi_beta = -(360/Td) t_n_beta + (n - 1) 360   degrees }}}
   *
   * with `t_n_beta` "the time for the Dutch roll oscillation in the sideslip response to reach the nth local
   * maximum for a right step ... roll-control command". Taken at the first peak, and wrapped into the range
   * the figure is drawn over.
   *
   * The standard warns about this itself: "care must be taken to select a peak far enough downstream that
   * the position of the peak is not influenced by the roll mode". The first peak is used and the caveat is
   * carried, rather than a later one being picked and the choice hidden.
   */
  def phaseAngle(firstSideslipPeakTime: Double, dutchRollPeriod: Double): Option[Double] = {
    if (dutchRollPeriod <= 0.0 || firstSideslipPeakTime < 0.0) return None
    var psi = -(360.0 / dutchRollPeriod) * firstSideslipPeakTime
    while (psi <= -360.0) psi += 360.0
    while (psi > 0.0) psi -= 360.0
    Some(psi)
  }
}
