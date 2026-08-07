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

/**
 * The criteria come from MIL-F-8785C, which is in the repository: `docs/MIL-F-8785C.pdf`, with the tables
 * transcribed and the pages cited in `docs/mil-f-8785c.md`. **Every threshold below names its section and
 * table**, so a reader can check it against the page it came from rather than take it on trust — which is
 * how the one number here that is *not* in the standard went unnoticed for so long (see `SpiralGuess`).
 *
 * All of them are Level 1, Flight Phase Category B — gradual maneuvering, which is cruise. Neither the Level
 * nor the Category is a choice the user can make yet, and Category A wants noticeably more of the aircraft.
 */
object MilF8785cEvaluator {
  private val ModeDominanceThreshold = 0.55f
  private val OscillatoryOmegaThreshold = 1.0e-6f

  // MIL-F-8785C 3.2.2.1.2, TABLE IV (PDF p. 13): Category B, Level 1.
  private val ShortPeriodMinZeta = 0.30
  private val ShortPeriodMaxZeta = 2.00

  // MIL-F-8785C 3.3.1.1, TABLE VI (PDF p. 22): Category B, all Classes, Level 1.
  // The table's footnote raises the min zeta*wn when wn^2 |phi/beta| exceeds 20 (rad/s)^2, which a model
  // clears easily — that augmentation is not applied here yet.
  private val DutchRollMinZeta = 0.08
  private val DutchRollMinWn = 0.40
  private val DutchRollMinZetaWn = 0.15

  // MIL-F-8785C 3.2.1.2 a (PDF p. 12): Level 1.
  private val PhugoidMinZeta = 0.04

  /**
   * **This one is not from the standard.** MIL-F-8785C 3.3.1.3, TABLE VIII (PDF p. 23) asks a Category B
   * aircraft's spiral to take at least 20 s to double at Level 1, 8 s at Level 2 and 4 s at Level 3. This is
   * a guess at where a *model's* spiral stops being flyable, and it is used only to word a divergence, never
   * to pass or fail anything.
   *
   * It may even be about right — 20 s full scale is about 9 s on a 1/5 model, since times scale with the
   * square root of the scale — but nobody wrote that down, so nobody could check it. Temporary: it goes when
   * the spiral is judged against TABLE VIII properly.
   */
  private val SpiralGuess = 10.0

  /**
   * A motion that runs away: a real root with a positive real part, which grows without ever swinging back.
   *
   * This is the most important thing an AVL run can say, and it used to be the easiest to miss. The
   * divergences were only reported when the modal table came out **empty** — so an aircraft with one oscillatory
   * mode and three divergences showed a green PASS and nothing else, which is exactly backwards. They are
   * reported now whatever else was found.
   *
   * `axis` comes from the mode shape when AVL gave one, because which axis is running away decides what to
   * change: a pitch divergence is the centre of gravity, a fast yaw one is the fin, and a slow lateral one is
   * the spiral mode, which most models have and most pilots fly through.
   */
  final case class Divergence(sigma: Double, doublingTime: Double, axis: String, says: String)

  def divergences(calculation: AvlCalculation): List[Divergence] = {
    val all = Option(calculation).map(_.getEigenvalues.asScala.toList).getOrElse(Nil)
    all.filter(e => e.getSigma > 0f && e.getOmega <= OscillatoryOmegaThreshold)
      .sortBy(e => -e.getSigma)
      .map { mode =>
        // A real positive root doubles every ln(2)/sigma seconds.
        val doubling = math.log(2.0) / mode.getSigma.toDouble
        val (axis, remedy) =
          if (!mode.hasModeShape)
            ("unknown", "AVL gave no mode shape for it, so which axis runs away cannot be told from here.")
          else if (mode.getLongitudinalRatio >= ModeDominanceThreshold && mode.getPitchRatio >= mode.getSpeedRatio)
            ("pitch", "The centre of gravity is behind the neutral point: move it forward, with weight in " +
              "the nose or by moving what is already there.")
          else if (mode.getLongitudinalRatio >= ModeDominanceThreshold)
            ("speed", "The aircraft cannot hold a speed: it accelerates or decays away from the trimmed " +
              "point. Usually the trim itself, or a centre of gravity far from where it was analysed.")
          else if (doubling > SpiralGuess)
            ("spiral", "A slow spiral. Most models have one and it is flown through easily; more dihedral " +
              "or a smaller fin tightens it.")
          else
            ("yaw and roll", "The nose is not held into the wind. A larger fin, or the same fin further " +
              "back, is what settles this.")
        val urgency =
          if (doubling < 0.5) "There is no flying this: it is gone before a pilot can react. "
          else if (doubling < 3.0) "A pilot would be fighting it constantly. "
          else ""
        Divergence(mode.getSigma.toDouble, doubling, axis,
          f"$axis%s runs away: the motion doubles every $doubling%.2f s (sigma +${mode.getSigma}%.3f). " +
            urgency + remedy)
      }
  }

  /** The headline: what the whole run says in one sentence, when something is running away. */
  def runawaySummary(calculation: AvlCalculation): Option[String] = {
    val found = divergences(calculation)
    if (found.isEmpty) None
    else {
      val worst = found.minBy(_.doublingTime)
      val count = if (found.size == 1) "one of its motions runs away" else s"${found.size} of its motions run away"
      Some(f"This aircraft will not fly as it stands: $count%s, the fastest doubling every " +
        f"${worst.doublingTime}%.2f s in ${worst.axis}%s. Whatever the modes below say, this comes first.")
    }
  }

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

    val header = s"AVL returned ${all.size} modes and none of them oscillates, so there is no short " +
      "period, phugoid or dutch roll to measure: this aircraft's motions grow or decay without " +
      "swinging. The mass and the inertias did reach AVL — the eigenvalues below are its answer."

    // The divergences themselves are listed above this, whether or not any mode was found, so they are not
    // repeated here: they used to appear only in this branch, which hid them the moment one mode was found.
    List(header)
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
    val shortPeriodWants = f"damping between $ShortPeriodMinZeta%.2f and $ShortPeriodMaxZeta%.2f"
    val shortRow = shortPeriod match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val wn = mode.getNaturalFrequency.toDouble
        val pass = zeta >= ShortPeriodMinZeta && zeta <= ShortPeriodMaxZeta
        val verdict =
          if (pass) f"Meets it: damping $zeta%.2f."
          else if (zeta < ShortPeriodMinZeta)
            f"Too lightly damped at $zeta%.2f, against the $ShortPeriodMinZeta%.2f wanted: the nose keeps " +
              "bobbing after a gust. More tailplane, a longer tail arm or a centre of gravity further forward."
          else
            f"Too heavily damped at $zeta%.2f, against the $ShortPeriodMaxZeta%.2f allowed: the aircraft " +
              "answers the elevator sluggishly."
        ModalNormRow("Short-period", shortPeriodIs, Some(wn), Some(zeta),
          periodOf(wn, zeta), swingsToHalfOf(zeta), shortPeriodWants, verdict, Some(pass))
      case None => unidentified("Short-period", shortPeriodIs, shortPeriodWants,
        "none of the oscillating motions AVL found is a pitch one. On a strongly damped model the short " +
          "period can split into two motions that do not swing at all, which is not a fault.", shapesReported)
    }

    val dutchRollIs = "the tail wagging: the nose swings side to side while the wings rock with it"
    val dutchRollWants = f"damping at least $DutchRollMinZeta%.2f, and quick enough with it"
    val dutchRow = dutchRoll match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val wn = mode.getNaturalFrequency.toDouble
        val pass = zeta >= DutchRollMinZeta && wn >= DutchRollMinWn && (zeta * wn) >= DutchRollMinZetaWn
        val verdict =
          if (pass) f"Meets it: damping $zeta%.2f."
          else if (zeta < DutchRollMinZeta)
            f"Too lightly damped at $zeta%.2f, against the $DutchRollMinZeta%.2f wanted: the tail keeps " +
              "wagging. A bigger fin, or further back, is what settles it."
          else
            f"Damped enough at $zeta%.2f but too slow to settle (${zeta * wn}%.2f against the " +
              f"$DutchRollMinZetaWn%.2f wanted): the wag dies away, but it takes a long time about it."
        ModalNormRow("Dutch-roll", dutchRollIs, Some(wn), Some(zeta),
          periodOf(wn, zeta), swingsToHalfOf(zeta), dutchRollWants, verdict, Some(pass))
      case None => unidentified("Dutch-roll", dutchRollIs, dutchRollWants,
        "none of the oscillating motions AVL found is a yaw-and-roll one. With a large fin the wag can be " +
          "damped out of existence, which is not a fault either.", shapesReported)
    }

    val phugoidIs = "the slow rollercoaster: the aircraft trades height for speed and back, over many seconds"
    val phugoidWants = f"damping at least $PhugoidMinZeta%.2f"
    val phugoidRow = phugoid match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val wn = mode.getNaturalFrequency.toDouble
        val pass = zeta >= PhugoidMinZeta
        val verdict =
          if (pass) f"Meets it: damping $zeta%.2f."
          else
            f"Too lightly damped at $zeta%.2f, against the $PhugoidMinZeta%.2f wanted: the aircraft keeps " +
              "porpoising and the pilot has to fly it out. It is a slow motion and easy to correct, so this " +
              "is the least pressing of the three."
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
