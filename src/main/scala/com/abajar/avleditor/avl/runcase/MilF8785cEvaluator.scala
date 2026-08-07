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

/**
 * One line of the modal report, in terms a model flyer reads rather than in the ones the standard is written
 * in.
 *
 * `wn` and `zeta` are what AVL and MIL-F-8785C deal in, and they stay because they are the numbers to quote.
 * But a natural frequency in radians per second tells nobody how the aeroplane behaves: the **period** does —
 * how often the motion swings — and so does **how many swings it takes to die down to half**, which is what
 * damping means when you are flying the thing. Both are derived here rather than in the window, so they can be
 * checked without a display.
 *
 * `whatItIs` says which motion this is in plain words, `requirement` states the rule without repeating the
 * name of the standard on every row, and `verdict` says whether it is met and by how much.
 */
case class ModalNormRow(
  modeName: String,
  whatItIs: String,
  wn: Option[Double],
  zeta: Option[Double],
  period: Option[Double],
  swingsToHalf: Option[Double],
  requirement: String,
  verdict: String,
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

  /** How long one swing takes: the damped period, which is the one you would count with a stopwatch. */
  private def periodOf(wn: Double, zeta: Double): Option[Double] = {
    val damped = wn * math.sqrt(math.max(0.0, 1.0 - zeta * zeta))
    if (damped > 1.0e-9) Some(2.0 * math.Pi / damped) else None
  }

  /**
   * How many swings before the motion is half the size it was — what damping means to someone flying it.
   * Under about 0.3 the aeroplane rocks visibly; over 1 it does not swing at all, it just returns.
   */
  private def swingsToHalfOf(zeta: Double): Option[Double] =
    if (zeta <= 1.0e-9 || zeta >= 1.0) None
    else Some(math.log(2.0) * math.sqrt(1.0 - zeta * zeta) / (2.0 * math.Pi * zeta))

  /**
   * A motion that was not found, saying which of the two reasons it was — because they call for opposite
   * responses, and saying the wrong one sends the reader after something that is already there.
   *
   * Without mode shapes nothing can be told apart, and that is about the run. With them, the motions AVL did
   * find simply do not include this one, and that is about the aircraft.
   */
  private def unidentified(name: String, what: String, requirement: String, absent: String,
                           shapesReported: Boolean): ModalNormRow =
    ModalNormRow(name, what, None, None, None, None, requirement,
      if (!shapesReported)
        "Not judged: AVL reported no mode shapes for this run, so there is nothing to tell one motion from " +
          "another. The eigenvalues below are still its answer."
      else "Not found: " + absent, None)

  def evaluate(calculation: AvlCalculation): List[ModalNormRow] = {
    val modes = oscillatoryPositiveModes(calculation)
    val longitudinalModes = longitudinalOscillatoryModes(modes)
    val shortPeriod = findShortPeriodCandidate(longitudinalModes)
    val phugoid = findPhugoidCandidate(longitudinalModes, shortPeriod)

    // Whether AVL gave the mode shapes at all: without them nothing can be identified, and saying so is a
    // different statement from saying the aircraft has no such motion.
    val shapesReported = modes.exists(_.hasModeShape)
    val consumed = shortPeriod.toList ++ phugoid.toList
    val dutchPool = lateralOscillatoryModes(modes).filterNot(mode => consumed.exists(c => c eq mode))
    val dutchRoll = findDutchRollCandidate(dutchPool)

    val shortPeriodIs = "the quick nose bob after a gust or a stick input, at nearly constant speed"
    val shortPeriodWants = "damping between 0.30 and 2.00"
    val shortRow = shortPeriod match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val wn = mode.getNaturalFrequency.toDouble
        val pass = zeta >= 0.30 && zeta <= 2.00
        val verdict =
          if (pass) f"Meets it: damping $zeta%.2f."
          else if (zeta < 0.30)
            f"Too lightly damped at $zeta%.2f, against the 0.30 wanted: the nose keeps bobbing after a gust. " +
              "More tailplane, a longer tail arm or a centre of gravity further forward."
          else
            f"Too heavily damped at $zeta%.2f, against the 2.00 allowed: the aircraft answers the elevator " +
              "sluggishly."
        ModalNormRow("Short-period", shortPeriodIs, Some(wn), Some(zeta),
          periodOf(wn, zeta), swingsToHalfOf(zeta), shortPeriodWants, verdict, Some(pass))
      case None => unidentified("Short-period", shortPeriodIs, shortPeriodWants,
        "none of the oscillating motions AVL found is a pitch one. On a strongly damped model the short " +
          "period can split into two motions that do not swing at all, which is not a fault.", shapesReported)
    }

    val dutchRollIs = "the tail wagging: the nose swings side to side while the wings rock with it"
    val dutchRollWants = "damping at least 0.08, and quick enough with it"
    val dutchRow = dutchRoll match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val wn = mode.getNaturalFrequency.toDouble
        val pass = zeta >= 0.08 && wn >= 0.40 && (zeta * wn) >= 0.15
        val verdict =
          if (pass) f"Meets it: damping $zeta%.2f."
          else if (zeta < 0.08)
            f"Too lightly damped at $zeta%.2f, against the 0.08 wanted: the tail keeps wagging. A bigger fin, " +
              "or further back, is what settles it."
          else
            f"Damped enough at $zeta%.2f but too slow to settle (${zeta * wn}%.2f against the 0.15 wanted): " +
              "the wag dies away, but it takes a long time about it."
        ModalNormRow("Dutch-roll", dutchRollIs, Some(wn), Some(zeta),
          periodOf(wn, zeta), swingsToHalfOf(zeta), dutchRollWants, verdict, Some(pass))
      case None => unidentified("Dutch-roll", dutchRollIs, dutchRollWants,
        "none of the oscillating motions AVL found is a yaw-and-roll one. With a large fin the wag can be " +
          "damped out of existence, which is not a fault either.", shapesReported)
    }

    val phugoidIs = "the slow rollercoaster: the aircraft trades height for speed and back, over many seconds"
    val phugoidWants = "damping at least 0.04"
    val phugoidRow = phugoid match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val wn = mode.getNaturalFrequency.toDouble
        val pass = zeta >= 0.04
        val verdict =
          if (pass) f"Meets it: damping $zeta%.2f."
          else
            f"Too lightly damped at $zeta%.2f, against the 0.04 wanted: the aircraft keeps porpoising and the " +
              "pilot has to fly it out. It is a slow motion and easy to correct, so this is the least " +
              "pressing of the three."
        ModalNormRow("Phugoid", phugoidIs, Some(wn), Some(zeta),
          periodOf(wn, zeta), swingsToHalfOf(zeta), phugoidWants, verdict, Some(pass))
      case None => unidentified("Phugoid", phugoidIs, phugoidWants,
        "none of the remaining oscillating motions is a slow speed-and-height one. It is the easiest of the " +
          "three to lose in the numbers, and the least consequential to fly without.", shapesReported)
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
