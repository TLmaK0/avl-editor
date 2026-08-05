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

import scala.collection.JavaConverters._

case class ModalNormRow(
  modeName: String,
  wn: Option[Double],
  zeta: Option[Double],
  criterion: String,
  pass: Option[Boolean]
)

object MilF8785cEvaluator {
  private val ModeDominanceThreshold = 0.55f
  private val OscillatoryOmegaThreshold = 1.0e-6f

  def oscillatoryPositiveModes(calculation: AvlCalculation): List[AvlEigenvalue] = {
    calculation.getEigenvalues.asScala.toList
      .filter(e => e.getOmega > OscillatoryOmegaThreshold)
      .sortBy(e => -e.getNaturalFrequency)
  }

  /**
   * Why there is nothing to judge, when there is nothing to judge — in the aircraft's own terms.
   *
   * 'No oscillatory eigenmodes, define mass/inertia' used to be said whenever the modal table came out
   * empty, and it sent the reader after the masses even when AVL had been given them and had answered:
   * an aircraft trimmed far from where it balances has no oscillatory pitch mode at all, its short
   * period having split into two real roots with one of them running away. That is a finding, not a
   * missing input, and it is worth saying out loud.
   */
  def whyNoModes(calculation: AvlCalculation): List[String] = {
    val all = Option(calculation).map(_.getEigenvalues).map(_.asScala.toList).getOrElse(Nil)
    if (all.isEmpty)
      return List("AVL returned no eigenvalues for this run. It computes them from the mass and the " +
        "inertias, so check that the model has masses and that the run converged.")

    val divergent = all.filter(_.getSigma > 0f).sortBy(e => -e.getSigma)
    val header = s"AVL returned ${all.size} modes and none of them oscillates, so there is no short " +
      "period, phugoid or dutch roll to measure: this aircraft's motions grow or decay without " +
      "swinging. The mass and the inertias did reach AVL — the eigenvalues below are its answer."

    val divergences = divergent.map { mode =>
      // A real positive root doubles every ln(2)/sigma seconds.
      val doublingTime = math.log(2.0) / mode.getSigma.toDouble
      f"sigma = +${mode.getSigma}%.3f /s is a divergence: the motion doubles every " +
        f"${doublingTime}%.2f s. Nothing damps it, so it is not a mode a pilot flies through. The " +
        "usual cause is a centre of gravity too far aft; move it forward and run AVL again."
    }

    header :: divergences
  }

  def evaluate(calculation: AvlCalculation): List[ModalNormRow] = {
    val modes = oscillatoryPositiveModes(calculation)
    val longitudinalModes = longitudinalOscillatoryModes(modes)
    val shortPeriod = findShortPeriodCandidate(longitudinalModes)
    val phugoid = findPhugoidCandidate(longitudinalModes, shortPeriod)

    val consumed = shortPeriod.toList ++ phugoid.toList
    val dutchPool = lateralOscillatoryModes(modes).filterNot(mode => consumed.exists(c => c eq mode))
    val dutchRoll = findDutchRollCandidate(dutchPool)

    val shortRow = shortPeriod match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val pass = zeta >= 0.30 && zeta <= 2.00
        ModalNormRow(
          "Short-period",
          Some(mode.getNaturalFrequency.toDouble),
          Some(zeta),
          "MIL-F-8785C L1 Phase B: 0.30 <= zeta <= 2.00",
          Some(pass)
        )
      case None =>
        ModalNormRow(
          "Short-period",
          None,
          None,
          "MIL-F-8785C L1 Phase B: 0.30 <= zeta <= 2.00 (mode not identifiable from modal content)",
          None
        )
    }

    val dutchRow = dutchRoll match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val wn = mode.getNaturalFrequency.toDouble
        val pass = zeta >= 0.08 && wn >= 0.40 && (zeta * wn) >= 0.15
        ModalNormRow(
          "Dutch-roll",
          Some(wn),
          Some(zeta),
          "MIL-F-8785C L1 Phase B: zeta >= 0.08, wn >= 0.40, zeta*wn >= 0.15",
          Some(pass)
        )
      case None =>
        ModalNormRow(
          "Dutch-roll",
          None,
          None,
          "MIL-F-8785C L1 Phase B: zeta >= 0.08, wn >= 0.40, zeta*wn >= 0.15 (mode not identifiable from modal content)",
          None
        )
    }

    val phugoidRow = phugoid match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val pass = zeta >= 0.04
        ModalNormRow(
          "Phugoid",
          Some(mode.getNaturalFrequency.toDouble),
          Some(zeta),
          "MIL-F-8785C L1 Phase B: zeta >= 0.04",
          Some(pass)
        )
      case None =>
        ModalNormRow(
          "Phugoid",
          None,
          None,
          "MIL-F-8785C L1 Phase B: zeta >= 0.04 (mode not identifiable from modal content)",
          None
        )
    }

    List(shortRow, dutchRow, phugoidRow)
  }

  private def longitudinalOscillatoryModes(modes: List[AvlEigenvalue]): List[AvlEigenvalue] = {
    modes.filter(mode => mode.hasModeShape && mode.getLongitudinalRatio >= ModeDominanceThreshold)
  }

  private def lateralOscillatoryModes(modes: List[AvlEigenvalue]): List[AvlEigenvalue] = {
    modes.filter(mode => mode.hasModeShape && mode.getLateralRatio >= ModeDominanceThreshold)
  }

  private def findShortPeriodCandidate(modes: List[AvlEigenvalue]): Option[AvlEigenvalue] = {
    val pitchDominant = modes.filter(mode => mode.getPitchRatio >= mode.getSpeedRatio)
    if (pitchDominant.nonEmpty) Some(pitchDominant.maxBy(_.getNaturalFrequency)) else None
  }

  private def findPhugoidCandidate(modes: List[AvlEigenvalue], shortPeriod: Option[AvlEigenvalue]): Option[AvlEigenvalue] = {
    val remaining = modes.filterNot(mode => shortPeriod.exists(sp => sp eq mode))
    val speedDominant = remaining.filter(mode => mode.getSpeedRatio >= mode.getPitchRatio)
    if (speedDominant.nonEmpty) Some(speedDominant.minBy(_.getNaturalFrequency)) else None
  }

  private def findDutchRollCandidate(modes: List[AvlEigenvalue]): Option[AvlEigenvalue] = {
    if (modes.isEmpty) None
    else Some(modes.maxBy(_.getNaturalFrequency))
  }
}
