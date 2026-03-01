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
