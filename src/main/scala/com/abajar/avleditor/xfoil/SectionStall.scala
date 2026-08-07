/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.xfoil

/**
 * Where one aerofoil section stops lifting, read off a viscous XFOIL polar.
 *
 * This is the **two-dimensional** `clmax`, unscaled — not [[AeroDerivation.CLMAX_3D_FACTOR]]'s 0.9 times
 * it. The factor exists because a finite wing does not reach its section's maximum everywhere at once;
 * that is a property of the *loading*, and the loading is something AVL measures rather than something to
 * approximate with a constant. So the section keeps its own number here and `WingMaximumLift` asks the
 * wing where it first reaches it.
 *
 * `clMin` is the same question the other way up. A tailplane usually lifts downwards, and a strip working
 * hard downwards stalls exactly as a wing does.
 */
case class SectionStallData(
  clMax: Double,
  alphaAtClMaxDeg: Double,
  clMin: Option[Double],
  alphaAtClMinDeg: Option[Double],
  convergedPoints: Int,
  reynolds: Double
)

object SectionStall {

  /**
   * The sweep asked of XFOIL. It has to go **past** the stall in both directions, because a maximum that
   * sits at the end of the range is not a maximum — it is where we stopped looking, and reporting it as
   * one is how a wing gets credited with lift it does not have.
   *
   * Half a degree, rather than the whole degree a polar is usually plotted at, because the peak is flat
   * and the value there is what the whole stall speed is built on.
   */
  val AlphaStartDeg: Double = -8.0
  val AlphaEndDeg: Double = 20.0
  val AlphaStepDeg: Double = 0.5

  /**
   * How many converged points past the peak count as having seen the aerofoil turn over. One could be
   * noise on a nearly flat curve; two in a row is a fall.
   */
  val PointsPastThePeak: Int = 2

  /** Below this the "polar" is a handful of scattered points XFOIL happened to converge, not a curve. */
  val LeastUsablePoints: Int = 5

  /**
   * @return the section's stall, or the reason it could not be read — never a number standing in for one.
   */
  def fromPolar(polar: Seq[XfoilPolarPoint], reynolds: Double): Either[String, SectionStallData] = {
    if (polar.isEmpty)
      return Left("XFOIL converged at no attitude at all")
    if (polar.length < LeastUsablePoints)
      return Left(f"XFOIL converged at only ${polar.length}%d attitudes, too few to read a stall from")

    val ordered = polar.sortBy(_.alpha)
    val peak = ordered.maxBy(_.cl)
    val past = ordered.count(_.alpha > peak.alpha)
    if (past < PointsPastThePeak)
      return Left(f"the polar is still rising at ${peak.alpha}%.1f deg, the top of the sweep — XFOIL never " +
        f"reached the stall, so the largest lift it converged at (${peak.cl}%.3f) is where the run stopped " +
        "rather than where the aerofoil gives up")

    val trough = ordered.minBy(_.cl)
    val below = ordered.count(_.alpha < trough.alpha)
    val negative =
      if (below >= PointsPastThePeak) (Some(trough.cl.toDouble), Some(trough.alpha.toDouble))
      else (None, None)

    Right(SectionStallData(peak.cl.toDouble, peak.alpha.toDouble, negative._1, negative._2,
      ordered.length, reynolds))
  }
}
