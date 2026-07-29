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
 * Values derived from XFOIL polars for the empirical aero-model parameters that
 * AVL cannot compute. Only the physically well-grounded, attached-flow quantities
 * are produced here; each is an `Option`, so a value that cannot be derived
 * confidently is left to its existing default rather than fabricated.
 *
 * Deliberately NOT derived here (see notes):
 *  - CD_prof: comes from AVL (viscous drag), not XFOIL.
 *  - CL_drop / CD_stall / eta_loc: CRRCsim-stall-model-specific empirical params
 *    with no first-principles mapping from a polar; left to the user/defaults.
 */
case class DerivedAero(
  clMax: Option[Float] = None,
  clMin: Option[Float] = None,
  clCD0: Option[Float] = None,
  cdCLsq: Option[Float] = None,
  uexpCD: Option[Float] = None
)

object AeroDerivation {

  /**
   * Empirical finite-wing correction: 3D CL_max is lower than the 2D section
   * cl_max. 0.9 is a common first-order factor; the accurate value comes from a
   * critical-section analysis using AVL's spanwise loading (future refinement).
   */
  val CLMAX_3D_FACTOR: Float = 0.9f

  /**
   * @param polar        viscous polar at the operating Reynolds (ordered by alpha)
   * @param higherRe      optional (polar, ReLow, ReHigh) for the Reynolds drag-scaling exponent
   */
  def deriveFromPolar(
      polar: Seq[XfoilPolarPoint],
      higherRe: Option[(Seq[XfoilPolarPoint], Double, Double)] = None
  ): DerivedAero = {
    if (polar.isEmpty) return DerivedAero()

    val cls = polar.map(_.cl)
    val clMax2D = cls.max
    val clMin2D = cls.min
    val clAtMinCd = polar.minBy(_.cd).cl

    DerivedAero(
      clMax = Some(CLMAX_3D_FACTOR * clMax2D),
      clMin = Some(CLMAX_3D_FACTOR * clMin2D),
      clCD0 = Some(clAtMinCd),
      cdCLsq = fitCdVsClSquared(polar),
      uexpCD = higherRe.flatMap { case (p2, reLow, reHigh) => fitReynoldsExponent(polar, p2, reLow, reHigh) }
    )
  }

  /**
   * Slope of the parabolic profile polar: CD ≈ CD0 + k·CL², k = CD_CLsq.
   * Least-squares fit of CD on CL² over the attached-flow region (CL ≤ 0.9·CLmax),
   * which is where the polar is parabolic. Returns None if too few points.
   */
  private def fitCdVsClSquared(polar: Seq[XfoilPolarPoint]): Option[Float] = {
    val clMax = polar.map(_.cl).max
    val attached = polar.filter(p => p.cl <= 0.9f * clMax)
    if (attached.length < 3) return None

    val xs = attached.map(p => (p.cl * p.cl).toDouble)   // CL²
    val ys = attached.map(_.cd.toDouble)                 // CD
    val n = xs.length
    val sx = xs.sum
    val sy = ys.sum
    val sxx = xs.map(x => x * x).sum
    val sxy = xs.zip(ys).map { case (x, y) => x * y }.sum
    val denom = n * sxx - sx * sx
    if (math.abs(denom) < 1e-12) return None
    val slope = (n * sxy - sx * sy) / denom
    if (slope.isNaN || slope <= 0.0) None else Some(slope.toFloat)
  }

  /**
   * Reynolds scaling exponent of profile drag: CD ∝ Re^n. In CRRCsim,
   * CD_prof ~ (U/U_ref)^Uexp_CD and Re ∝ U for fixed geometry, so Uexp_CD ≈ n.
   * Estimated from the minimum CD at two Reynolds numbers.
   */
  private def fitReynoldsExponent(
      polarLow: Seq[XfoilPolarPoint], polarHigh: Seq[XfoilPolarPoint],
      reLow: Double, reHigh: Double
  ): Option[Float] = {
    if (polarLow.isEmpty || polarHigh.isEmpty || reLow <= 0 || reHigh <= 0 || reLow == reHigh) return None
    val cdLow = polarLow.map(_.cd).min.toDouble
    val cdHigh = polarHigh.map(_.cd).min.toDouble
    if (cdLow <= 0 || cdHigh <= 0) return None
    val n = math.log(cdHigh / cdLow) / math.log(reHigh / reLow)
    if (n.isNaN || n.isInfinite) None else Some(n.toFloat)
  }
}
