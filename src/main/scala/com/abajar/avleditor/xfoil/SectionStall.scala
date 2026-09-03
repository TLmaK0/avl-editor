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
   *
   * It is **not** enough on its own to tell a stall from a bursting laminar bubble, which also falls for
   * two points and then some — see the note below and issue #43.
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

  /**
   * What the curve does after its peak: how far it falls, and how much of that it takes back afterwards.
   *
   * <b>Reported, and deliberately not acted on.</b> A curve that falls and then climbs again looks like a
   * laminar separation bubble bursting and reattaching rather than a stall, and telling the two apart from
   * the polar alone is **not established**: four candidate tests were measured against eleven real XFOIL
   * polars in issue #43 and every one of them fails on data this editor's own aircraft produce. In
   * particular the recovery is large for a *genuine* stall at the Reynolds numbers a model flies at — the
   * check aircraft's own wing section takes back 26-34 % of its fall between Re 60,000 and 100,000 — so
   * refusing a peak on account of it would throw the wing's stall away. So this states the shape and
   * nothing is reclassified by it.
   *
   * The fall is from the peak to the lowest point after it; the recovery is from there to the highest point
   * after **that**, which is the shape a reattaching flow leaves behind. Both come back zero for a curve
   * that falls and stays down, which is what a stall looks like.
   *
   * The order matters and is the whole reason this is one function rather than two expressions: measured
   * the other way round — the highest point anywhere after the peak — a bubble that bursts at 10 deg reports
   * its own peak's neighbour as the recovery and comes out at 99 % on a curve that never recovers at all.
   */
  def shapeAfterPeak(ordered: Seq[XfoilPolarPoint]): (Double, Double) = {
    if (ordered.isEmpty) return (0.0, 0.0)
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
