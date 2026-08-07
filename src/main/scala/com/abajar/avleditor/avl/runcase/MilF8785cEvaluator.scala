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
 *
 * `level` is the MIL-F-8785C Level the motion reaches — 1, 2 or 3, or `None` for worse than Level 3 or not
 * measured. It replaces a bare pass/fail, because "Level 2: flyable, more work for the pilot" is a far more
 * useful answer than FAIL. `pass` stays as Level 1, which is what colours the row.
 *
 * `applied` is what the requirement became at this aircraft's size, and is `None` when the criterion is a
 * damping ratio and so does not depend on size at all. Both are always shown: a verdict that silently moved
 * the goalposts would be worse than one that never moved them.
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
  pass: Option[Boolean],
  level: Option[Int] = None,
  applied: Option[String] = None
)

/**
 * Which Flight Phase the aircraft is being judged in (MIL-F-8785C 1.3.2, PDF p. 5-6).
 *
 * This is the one thing about the judgement that **cannot be derived from the aircraft**: it is the mission,
 * not the machine. The same airframe flown gently is Category B and thrown around is Category A, and Category
 * A wants noticeably more of it — short-period damping from 0.35 rather than 0.30, dutch-roll damping 0.19
 * rather than 0.08. So it is a choice, and its default is B: gradual maneuvering, which is cruise.
 */
sealed abstract class FlightPhaseCategory(val label: String, val describes: String)

object FlightPhaseCategory {
  /** Rapid maneuvering, precision tracking: combat, ground attack, terrain following. */
  case object A extends FlightPhaseCategory("A", "thrown around: aerobatics, rapid maneuvering")
  /** Gradual maneuvers without precision tracking: climb, cruise, loiter, descent. */
  case object B extends FlightPhaseCategory("B", "flown gently: climb, cruise, loiter, descent")
  /** Terminal: takeoff, approach, landing. */
  case object C extends FlightPhaseCategory("C", "takeoff, approach and landing")

  val Default: FlightPhaseCategory = B

  /**
   * The model's own words for it, from `AVL.flightPhase`. Anything unrecognised — including a file written
   * before the field existed — is the default, which is what such a file was being judged as anyway.
   */
  def fromModelLabel(label: String): FlightPhaseCategory = label match {
    case com.abajar.avleditor.avl.AVL.FLIGHT_PHASE_AEROBATIC => A
    case com.abajar.avleditor.avl.AVL.FLIGHT_PHASE_TERMINAL  => C
    case _                                                   => Default
  }
}

/**
 * What a motion running away means, and what to do about it.
 *
 * **None of this is MIL-F-8785C.** The standard says how much damping a motion needs; it has no opinion about
 * fins or centres of gravity. This is model-flying judgement, and it is kept in its own type so that it cannot
 * be mistaken for a quotation — it is reported under its own heading, not inside the table of the standard's
 * verdicts.
 *
 * It is a sealed set on purpose. The axis used to be decided by a chain of `if/else` ending in a bare `else`,
 * and an `else` answers every case with confidence, including the ones nobody thought about — which is how a
 * mixed mode with no dominant axis came to be reported as "yaw and roll" with no hint that nothing dominated.
 */
sealed abstract class RunawayAxis(val label: String, val remedy: String)

object RunawayAxis {
  case object Pitch extends RunawayAxis("pitch",
    "The centre of gravity is behind the neutral point: move it forward, with weight in the nose or by " +
      "moving what is already there.")

  case object Speed extends RunawayAxis("speed",
    "The aircraft cannot hold a speed: it accelerates or decays away from the trimmed point. Usually the " +
      "trim itself, or a centre of gravity far from where it was analysed.")

  case object Spiral extends RunawayAxis("spiral",
    "A slow spiral. Most models have one and it is flown through easily; more dihedral or a smaller fin " +
      "tightens it.")

  case object YawAndRoll extends RunawayAxis("yaw and roll",
    "The nose is not held into the wind. A larger fin, or the same fin further back, is what settles this.")

  /** Mode shapes were reported, but nothing dominates. Saying which axis would be inventing one. */
  case object Mixed extends RunawayAxis("several axes at once",
    "No one axis dominates the motion, so there is no single thing to change. A divergence that mixes " +
      "pitch with yaw usually means the aircraft is far from where it was trimmed.")

  /** AVL gave no mode shape at all. About the run, not about the aircraft. */
  case object Unknown extends RunawayAxis("unknown",
    "AVL gave no mode shape for it, so which axis runs away cannot be told from here.")
}

/**
 * The criteria come from MIL-F-8785C, which is in the repository: `docs/MIL-F-8785C.pdf`, with the tables
 * transcribed and the pages cited in `docs/mil-f-8785c.md`. **Every threshold below names its section and
 * table**, so a reader can check it against the page it came from rather than take it on trust — which is
 * how the one number here that was *not* in the standard went unnoticed for years.
 *
 * Two things this object does that the standard does not do for itself.
 *
 * It reports a **Level**, not a pass. The standard is written in three of them and only the first is "clearly
 * adequate"; an aircraft that misses it is usually flyable rather than broken, and saying so is more useful
 * than FAIL.
 *
 * And it **follows the aircraft's size**. See [[FroudeScale]]: this editor is used for large aircraft and for
 * models, and a frequency in radians per second written around a ten-metre airplane means nothing to a metre
 * and a half of one.
 */
object MilF8785cEvaluator {
  private val ModeDominanceThreshold = 0.55f
  private val OscillatoryOmegaThreshold = 1.0e-6f

  /** Below this a real part is neither growing nor decaying, and that is worth saying rather than ignoring. */
  private val NeutralSigmaThreshold = 1.0e-6f

  private val Gravity = 9.80665

  // ---------------------------------------------------------------------------------------------------
  // Size
  // ---------------------------------------------------------------------------------------------------

  /**
   * What the aircraft's own size does to a threshold the standard states in seconds or radians per second.
   *
   * MIL-F-8785C is written for piloted, full-scale airplanes. Under Froude scaling — the right similarity for
   * a machine flying under gravity — an aircraft `n` times smaller has frequencies `sqrt(n)` times higher and
   * times `sqrt(n)` shorter, while damping ratios, being dimensionless, are unchanged.
   *
   * **The standard's own numbers already behave this way**, which is what makes this a derivation rather than
   * a guess. Table VI asks 1.0 rad/s of Classes I and IV and 0.4 rad/s of Classes II and III. A light trainer
   * and a fighter share a row — as different as two airplanes get — and what they have in common is about
   * 11 m of span; the other row is the 60 m ones. Test `wn >= sqrt(g/b)`: 11 m gives 0.94 rad/s against the
   * table's 1.0, and 60 m gives 0.40 against its 0.4. Two rows, spans differing by a factor of five,
   * reproduced to 6 % with no fitted constant. `FroudeScaleCheck` asserts exactly that.
   *
   * **Applied conservatively.** The rule is evidence that size matters, not licence to rewrite the standard
   * where it already applies. So a threshold is used **exactly as written** for any aircraft at least as big
   * as the smallest the standard contemplates — Class I, "small, light airplanes", about 11 m — and scaled
   * only below that, which is the range the standard never covered and where it goes vacuous: applied
   * unchanged, a model clears the dutch-roll frequency floors whatever it is like, leaving one of the three
   * criteria doing any work.
   */
  final case class FroudeScale(spanMetres: Double) {

    /**
     * The smallest airplane MIL-F-8785C contemplates: Class I, "small, light airplanes" (1.3.1, PDF p. 3-4),
     * a Cessna 172 at 11.0 m of span. Thresholds are quoted verbatim at this size and above.
     */
    val ReferenceSpanMetres = 11.0

    def known: Boolean = spanMetres > 0.0 && !spanMetres.isNaN && !spanMetres.isInfinite

    /** Below the standard's own range, and therefore scaled. */
    def scales: Boolean = known && spanMetres < ReferenceSpanMetres

    /** `sqrt(b/g)`, the time this aircraft's size sets. Times scale with it, frequencies against it. */
    def froudeTime: Double = math.sqrt(spanMetres / Gravity)

    private def ratio: Double = math.sqrt(ReferenceSpanMetres / spanMetres)

    /** A threshold in rad/s: a smaller aircraft has to be quicker, by `sqrt(bref/b)`. */
    def frequency(stated: Double): Double = if (scales) stated * ratio else stated

    /** A threshold in seconds: a smaller aircraft has less of them, by `sqrt(b/bref)`. */
    def time(stated: Double): Double = if (scales) stated / ratio else stated
  }

  /** An aircraft whose span never reached us: everything is quoted exactly as the standard states it. */
  val UnknownSize = FroudeScale(Double.NaN)

  def sizeOf(calculation: AvlCalculation): FroudeScale =
    Option(calculation).flatMap(c => Option(c.getConfiguration))
      .map(config => FroudeScale(config.getSpanMetres.toDouble))
      .getOrElse(UnknownSize)

  // ---------------------------------------------------------------------------------------------------
  // The criteria, as data. Every one cites its section, its table and the PDF page it is on.
  // ---------------------------------------------------------------------------------------------------

  /** MIL-F-8785C 3.2.2.1.2, TABLE IV (PDF p. 13): equivalent short-period damping ratio limits. */
  private def shortPeriodLimits(category: FlightPhaseCategory): List[(Int, Double, Double)] =
    category match {
      case FlightPhaseCategory.B => List((1, 0.30, 2.00), (2, 0.20, 2.00), (3, 0.15, Double.MaxValue))
      case _                     => List((1, 0.35, 1.30), (2, 0.25, 2.00), (3, 0.15, Double.MaxValue))
    }

  /**
   * MIL-F-8785C 3.2.2.1.1, FIGURES 1-3 (pp. 14-16): short-period frequency, as `(level, min CAP, max CAP)`.
   *
   * The requirement is drawn rather than tabulated, and that is the only reason it looks hard. The
   * boundaries on those log-log plots are **lines of constant `wn_sp^2 / (n/alpha)`** — the Control
   * Anticipation Parameter — and each line carries its own value printed up the right-hand edge, so the
   * figures are a table with four numbers per Flight Phase. Nothing has to be measured off the paper.
   *
   * What is **not** implemented from those figures: the additional `wn_sp` floors that Figures 1 and 3 draw
   * as horizontal and vertical lines at low `n/alpha`, which depend on the aircraft Class. Category B — the
   * default, and Figure 2 — has none of them: it says in as many words that its boundaries continue as
   * straight-line extensions outside the range shown.
   */
  private def shortPeriodFrequencyLimits(category: FlightPhaseCategory): List[(Int, Double, Double)] =
    category match {
      case FlightPhaseCategory.A => List((1, 0.28, 3.6), (2, 0.16, 10.0), (3, 0.16, Double.MaxValue))
      case FlightPhaseCategory.B => List((1, 0.085, 3.6), (2, 0.038, 10.0), (3, 0.038, Double.MaxValue))
      case FlightPhaseCategory.C => List((1, 0.16, 3.6), (2, 0.036, 10.0), (3, 0.036, Double.MaxValue))
    }

  /** MIL-F-8785C 3.2.1.2 (PDF p. 12): phugoid. Level 3 is a doubling time, not a damping ratio. */
  private val PhugoidMinZetaLevel1 = 0.04
  private val PhugoidMinZetaLevel2 = 0.0
  private val PhugoidLevel3DoublingSeconds = 55.0

  /**
   * MIL-F-8785C 3.3.1.1, TABLE VI (PDF p. 22): minimum dutch roll damping and frequency, as
   * `(level, min zeta, min zeta*wn, min wn)`.
   *
   * Class is not asked for. Where the table splits by it, the Class I / IV row is taken — "small, light
   * airplanes" and fighters, which is what this editor is used to design and the stricter of the two.
   */
  private def dutchRollLimits(category: FlightPhaseCategory): List[(Int, Double, Double, Double)] =
    category match {
      case FlightPhaseCategory.A => List((1, 0.19, 0.35, 1.0), (2, 0.02, 0.05, 0.4), (3, 0.0, 0.0, 0.4))
      case FlightPhaseCategory.B => List((1, 0.08, 0.15, 0.4), (2, 0.02, 0.05, 0.4), (3, 0.0, 0.0, 0.4))
      case FlightPhaseCategory.C => List((1, 0.08, 0.15, 1.0), (2, 0.02, 0.05, 0.4), (3, 0.0, 0.0, 0.4))
    }

  /** MIL-F-8785C 3.3.1.2, TABLE VII (PDF p. 23): maximum roll-mode time constant, seconds. */
  private def rollModeLimits(category: FlightPhaseCategory): List[(Int, Double)] =
    category match {
      case FlightPhaseCategory.B => List((1, 1.4), (2, 3.0), (3, 10.0))
      case _                     => List((1, 1.0), (2, 1.4), (3, 10.0))
    }

  /** MIL-F-8785C 3.3.1.3, TABLE VIII (PDF p. 23): spiral, minimum time to double amplitude, seconds. */
  private def spiralLimits(category: FlightPhaseCategory): List[(Int, Double)] =
    category match {
      case FlightPhaseCategory.B => List((1, 20.0), (2, 8.0), (3, 4.0))
      case _                     => List((1, 12.0), (2, 8.0), (3, 4.0))
    }

  /** MIL-F-8785C 3.3.1.4 (PDF p. 23): coupled roll-spiral, minimum `zeta*wn` in rad/s. */
  private val CoupledRollSpiralLimits = List((1, 0.5), (2, 0.3), (3, 0.15))

  /**
   * MIL-F-8785C 3.3.4, TABLE IXa (PDF p. 27): roll performance for Class I and II airplanes, as seconds to
   * reach a bank angle with the roll control hard over. The Class I row is taken — "small, light airplanes".
   *
   * Class I is measured over 60 degrees in Categories A and B and over 30 in Category C, so the angle is
   * part of the requirement and travels with it.
   */
  private def rollPerformanceLimits(category: FlightPhaseCategory): (Double, List[(Int, Double)]) =
    category match {
      case FlightPhaseCategory.A => (60.0, List((1, 1.3), (2, 1.7), (3, 2.6)))
      case FlightPhaseCategory.B => (60.0, List((1, 1.7), (2, 2.5), (3, 3.4)))
      case FlightPhaseCategory.C => (30.0, List((1, 1.3), (2, 1.8), (3, 2.6)))
    }

  // ---------------------------------------------------------------------------------------------------
  // What runs away
  // ---------------------------------------------------------------------------------------------------

  /**
   * A motion that grows: any root with a positive real part.
   *
   * This is the most important thing an AVL run can say, and it used to be the easiest to miss — twice over.
   * The divergences were only reported when the modal table came out **empty**, so one oscillatory mode was
   * enough to hide three runaways behind a green PASS. And a **growing oscillation** — positive real part and
   * a frequency — was not counted as a runaway at all: it went into the table, where its damping ratio comes
   * out negative and the verdict read "Too lightly damped at -0.12", as though it merely needed a bigger fin.
   * An oscillation that grows is a runaway that happens to swing on its way out.
   *
   * `axis` comes from the mode shape when AVL gave one, because which axis is running away decides what to
   * change: a pitch divergence is the centre of gravity, a fast yaw one is the fin, and a slow lateral one is
   * the spiral mode, which most models have and most pilots fly through.
   */
  final case class Divergence(sigma: Double, doublingTime: Double, axis: RunawayAxis, oscillates: Boolean,
                              period: Option[Double], says: String) {
    def axisLabel: String = axis.label
  }

  private def axisOf(mode: AvlEigenvalue, doubling: Double): RunawayAxis = {
    if (!mode.hasModeShape) RunawayAxis.Unknown
    else if (mode.getLongitudinalRatio >= ModeDominanceThreshold)
      if (mode.getPitchRatio >= mode.getSpeedRatio) RunawayAxis.Pitch else RunawayAxis.Speed
    else if (mode.getLateralRatio >= ModeDominanceThreshold)
      if (doubling > 10.0) RunawayAxis.Spiral else RunawayAxis.YawAndRoll
    else RunawayAxis.Mixed
  }

  def divergences(calculation: AvlCalculation): List[Divergence] = {
    val all = Option(calculation).map(_.getEigenvalues.asScala.toList).getOrElse(Nil)
    all.filter(_.getSigma > NeutralSigmaThreshold)
      .sortBy(e => -e.getSigma)
      .map { mode =>
        // A root with a positive real part doubles every ln(2)/sigma seconds, whether it swings or not.
        val doubling = math.log(2.0) / mode.getSigma.toDouble
        val axis = axisOf(mode, doubling)
        val oscillates = mode.getOmega > OscillatoryOmegaThreshold
        val period = if (oscillates) Some(2.0 * math.Pi / mode.getOmega.toDouble) else None
        val urgency =
          if (doubling < 0.5) "There is no flying this: it is gone before a pilot can react. "
          else if (doubling < 3.0) "A pilot would be fighting it constantly. "
          else ""
        val what =
          if (oscillates)
            f"${axis.label}%s oscillates and the swings grow: each one doubles every $doubling%.2f s " +
              f"(sigma +${mode.getSigma}%.3f, a swing every ${period.get}%.2f s). "
          else
            f"${axis.label}%s runs away: the motion doubles every $doubling%.2f s (sigma +${mode.getSigma}%.3f). "
        Divergence(mode.getSigma.toDouble, doubling, axis, oscillates, period, what + urgency + axis.remedy)
      }
  }

  /**
   * A motion that neither grows nor decays. It is not a divergence and it is not damped, and it used to fall
   * between the two filters and vanish: `sigma > 0` excluded it from the runaways and `omega > 0` from the
   * table. An aircraft on the edge of stability is worth a sentence.
   */
  def neutralModes(calculation: AvlCalculation): List[String] = {
    val all = Option(calculation).map(_.getEigenvalues.asScala.toList).getOrElse(Nil)
    all.filter(e => math.abs(e.getSigma) <= NeutralSigmaThreshold)
      .map { mode =>
        if (mode.getOmega > OscillatoryOmegaThreshold)
          f"A motion swings on for ever without dying away (a swing every ${2.0 * math.Pi / mode.getOmega}%.2f s, " +
            "sigma 0). It will not run away, but nothing damps it either: it sits exactly on the edge."
        else
          "A motion neither grows nor decays (sigma 0). Displace the aircraft in it and it simply stays " +
            "displaced, which is the boundary of stability rather than a fault."
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
        f"${worst.doublingTime}%.2f s in ${worst.axisLabel}%s. Whatever the modes below say, this comes first.")
    }
  }

  def oscillatoryPositiveModes(calculation: AvlCalculation): List[AvlEigenvalue] = {
    calculation.getEigenvalues.asScala.toList
      .filter(e => e.getOmega > OscillatoryOmegaThreshold)
      .sortBy(e => -e.getNaturalFrequency)
  }

  /** The real roots, which carry the roll mode and the spiral and used to be thrown away unless they grew. */
  private def realModes(calculation: AvlCalculation): List[AvlEigenvalue] = {
    Option(calculation).map(_.getEigenvalues.asScala.toList).getOrElse(Nil)
      .filter(e => e.getOmega <= OscillatoryOmegaThreshold)
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

  /** The first Level whose limits the measurement meets, worst-first order being 1, 2, 3. */
  private def levelMet(levels: List[(Int, Boolean)]): Option[Int] =
    levels.find(_._2).map(_._1)

  /**
   * A Level below 1 still has to say **what kept it from Level 1**. Reporting the Level alone would be the
   * old bare FAIL with a number on it: it tells the reader where they are and not which way to move.
   */
  private def levelVerdict(level: Option[Int], meets: String, misses: String): String = level match {
    case Some(1) => "Meets it: " + meets
    case Some(n) => f"Level $n%d — flyable, but more work for the pilot. Short of Level 1: " + misses
    case None    => "Worse than Level 3: " + misses
  }

  def evaluate(calculation: AvlCalculation): List[ModalNormRow] =
    evaluate(calculation, FlightPhaseCategory.Default)

  def evaluate(calculation: AvlCalculation, category: FlightPhaseCategory): List[ModalNormRow] = {
    val size = sizeOf(calculation)
    val modes = oscillatoryPositiveModes(calculation)
    val longitudinalModes = longitudinalOscillatoryModes(modes)
    val shortPeriod = findShortPeriodCandidate(longitudinalModes)
    val phugoid = findPhugoidCandidate(longitudinalModes, shortPeriod)

    // Whether AVL gave the mode shapes at all: without them nothing can be identified, and saying so is a
    // different statement from saying the aircraft has no such motion.
    val shapesReported = modes.exists(_.hasModeShape) || realModes(calculation).exists(_.hasModeShape)
    val consumed = shortPeriod.toList ++ phugoid.toList
    val lateralOscillatory = lateralOscillatoryModes(modes).filterNot(mode => consumed.exists(c => c eq mode))
    val coupledRollSpiral = findCoupledRollSpiralCandidate(lateralOscillatory, size)
    val dutchPool = lateralOscillatory.filterNot(mode => coupledRollSpiral.exists(c => c eq mode))
    val dutchRoll = findDutchRollCandidate(dutchPool)

    val rollMode = findRollModeCandidate(realModes(calculation))
    List(
      shortPeriodRow(shortPeriod, category, shapesReported),
      shortPeriodFrequencyRow(calculation, shortPeriod, category, size, shapesReported),
      dutchRollRow(dutchRoll, category, size, shapesReported),
      phugoidRow(phugoid, category, size, shapesReported),
      rollModeRow(rollMode, category, size, shapesReported),
      spiralRow(findSpiralCandidate(realModes(calculation)), category, size, shapesReported),
      coupledRollSpiralRow(coupledRollSpiral, category, size),
      rollPerformanceRow(calculation, category, rollMode, size)
    )
  }

  // ---------------------------------------------------------------------------------------------------
  // One row per motion
  // ---------------------------------------------------------------------------------------------------

  private def shortPeriodRow(candidate: Option[AvlEigenvalue], category: FlightPhaseCategory,
                             shapesReported: Boolean): ModalNormRow = {
    val is = "the quick nose bob after a gust or a stick input, at nearly constant speed"
    val limits = shortPeriodLimits(category)
    val (_, minLevel1, maxLevel1) = limits.head
    val wants = f"damping between $minLevel1%.2f and $maxLevel1%.2f"
    candidate match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val wn = mode.getNaturalFrequency.toDouble
        val level = levelMet(limits.map { case (n, lo, hi) => (n, zeta >= lo && zeta <= hi) })
        val miss =
          if (zeta < minLevel1)
            f"too lightly damped at $zeta%.2f — the nose keeps bobbing after a gust. More tailplane, a " +
              "longer tail arm or a centre of gravity further forward."
          else f"too heavily damped at $zeta%.2f — the aircraft answers the elevator sluggishly."
        ModalNormRow("Short-period", is, Some(wn), Some(zeta), periodOf(wn, zeta), swingsToHalfOf(zeta),
          wants, levelVerdict(level, f"damping $zeta%.2f.", miss), Some(level == Some(1)), level)
      case None => unidentified("Short-period", is, wants,
        "none of the oscillating motions AVL found is a pitch one. On a strongly damped model the short " +
          "period can split into two motions that do not swing at all, which is not a fault.", shapesReported)
    }
  }

  private def dutchRollRow(candidate: Option[AvlEigenvalue], category: FlightPhaseCategory,
                           size: FroudeScale, shapesReported: Boolean): ModalNormRow = {
    val is = "the tail wagging: the nose swings side to side while the wings rock with it"
    val limits = dutchRollLimits(category)
    val (_, minZeta1, minZetaWn1, minWn1) = limits.head
    val wants = f"damping at least $minZeta1%.2f, and quick enough with it " +
      f"(wn at least $minWn1%.2f rad/s, damping x wn at least $minZetaWn1%.2f)"
    val applied =
      if (size.scales) Some(f"at this size: wn at least ${size.frequency(minWn1)}%.2f rad/s, " +
        f"damping x wn at least ${size.frequency(minZetaWn1)}%.2f")
      else None
    candidate match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val wn = mode.getNaturalFrequency.toDouble
        val level = levelMet(limits.map { case (n, mz, mzw, mw) =>
          (n, zeta >= mz && wn >= size.frequency(mw) && (zeta * wn) >= size.frequency(mzw))
        })
        val miss =
          if (zeta < minZeta1)
            f"too lightly damped at $zeta%.2f — the tail keeps wagging. A bigger fin, or further back, is " +
              "what settles it."
          else
            f"damped enough at $zeta%.2f but too slow to settle (${zeta * wn}%.2f against the " +
              f"${size.frequency(minZetaWn1)}%.2f wanted) — the wag dies away, but it takes a long time about it."
        ModalNormRow("Dutch-roll", is, Some(wn), Some(zeta), periodOf(wn, zeta), swingsToHalfOf(zeta),
          wants, levelVerdict(level, f"damping $zeta%.2f.", miss), Some(level == Some(1)), level, applied)
      case None => unidentified("Dutch-roll", is, wants,
        "none of the oscillating motions AVL found is a yaw-and-roll one. With a large fin the wag can be " +
          "damped out of existence, which is not a fault either.", shapesReported)
    }
  }

  private def phugoidRow(candidate: Option[AvlEigenvalue], category: FlightPhaseCategory,
                         size: FroudeScale, shapesReported: Boolean): ModalNormRow = {
    val is = "the slow rollercoaster: the aircraft trades height for speed and back, over many seconds"
    val wants = f"damping at least $PhugoidMinZetaLevel1%.2f"
    val level3Seconds = size.time(PhugoidLevel3DoublingSeconds)
    val applied = if (size.scales) Some(f"at this size, Level 3 wants doubling no faster than $level3Seconds%.0f s")
                  else None
    candidate match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val wn = mode.getNaturalFrequency.toDouble
        // Level 3 is stated as a doubling time rather than a damping ratio: a phugoid may grow, provided it
        // takes long enough about it that the pilot simply flies it out.
        val doubling = if (mode.getSigma > 0f) math.log(2.0) / mode.getSigma.toDouble else Double.MaxValue
        val level = levelMet(List(
          (1, zeta >= PhugoidMinZetaLevel1),
          (2, zeta >= PhugoidMinZetaLevel2),
          (3, doubling >= level3Seconds)))
        val miss =
          f"too lightly damped at $zeta%.2f — the aircraft keeps porpoising and the pilot has to fly it " +
            "out. It is a slow motion and easy to correct, so this is the least pressing of the three."
        ModalNormRow("Phugoid", is, Some(wn), Some(zeta), periodOf(wn, zeta), swingsToHalfOf(zeta),
          wants, levelVerdict(level, f"damping $zeta%.2f.", miss), Some(level == Some(1)), level, applied)
      case None => unidentified("Phugoid", is, wants,
        "none of the remaining oscillating motions is a slow speed-and-height one. It is the easiest of the " +
          "three to lose in the numbers, and the least consequential to fly without.", shapesReported)
    }
  }

  /**
   * MIL-F-8785C 3.3.1.2, TABLE VII: how quickly the roll rate settles after the stick is moved. It is a real
   * root, not an oscillation, which is why it was invisible until the real roots were kept.
   */
  private def rollModeRow(candidate: Option[AvlEigenvalue], category: FlightPhaseCategory,
                          size: FroudeScale, shapesReported: Boolean): ModalNormRow = {
    val is = "how quickly the roll settles: move the stick and the roll rate takes this long to arrive"
    val limits = rollModeLimits(category)
    val wants = f"settling within ${limits.head._2}%.1f s"
    val applied = if (size.scales) Some(f"at this size: within ${size.time(limits.head._2)}%.2f s") else None
    candidate match {
      case Some(mode) =>
        // The roll mode is a decaying real root: tau = -1/sigma.
        val tau = -1.0 / mode.getSigma.toDouble
        val level = levelMet(limits.map { case (n, maxTau) => (n, tau <= size.time(maxTau)) })
        val miss =
          f"the roll takes $tau%.2f s to build up, against the ${size.time(limits.head._2)}%.2f s wanted: the " +
            "aircraft feels slow and mushy in roll. More aileron, or less roll damping — a shorter span or " +
            "less dihedral."
        ModalNormRow("Roll mode", is, None, None, None, None, wants,
          levelVerdict(level, f"the roll settles in $tau%.2f s.", miss), Some(level == Some(1)), level, applied)
      case None => unidentified("Roll mode", is, wants,
        "none of the real roots AVL found is a rolling one. Without ailerons, or with a mode shape that does " +
          "not single out roll rate, there is nothing here to measure.", shapesReported)
    }
  }

  /**
   * MIL-F-8785C 3.3.1.3, TABLE VIII: the spiral. A stable one meets every Level at once; an unstable one is
   * judged on how long it takes to double, which is what most models have and most pilots fly through.
   *
   * This row is what retired the invented 10-second threshold that used to stand in for the whole table.
   */
  private def spiralRow(candidate: Option[AvlEigenvalue], category: FlightPhaseCategory,
                        size: FroudeScale, shapesReported: Boolean): ModalNormRow = {
    val is = "the slow bank that will not right itself: let go and the aircraft gradually rolls further in"
    val limits = spiralLimits(category)
    val wants = f"if it diverges, taking at least ${limits.head._2}%.0f s to double the bank"
    val applied = if (size.scales) Some(f"at this size: at least ${size.time(limits.head._2)}%.1f s") else None
    candidate match {
      case Some(mode) if mode.getSigma <= 0f =>
        val halving = if (mode.getSigma < 0f) math.log(2.0) / -mode.getSigma.toDouble else Double.PositiveInfinity
        ModalNormRow("Spiral", is, None, None, None, None, wants,
          if (halving.isPosInfinity) "Meets it: the spiral neither tightens nor rights itself."
          else f"Meets it: the bank rights itself, halving every $halving%.1f s. A stable spiral meets every Level.",
          Some(true), Some(1), applied)
      case Some(mode) =>
        val doubling = math.log(2.0) / mode.getSigma.toDouble
        val level = levelMet(limits.map { case (n, minT2) => (n, doubling >= size.time(minT2)) })
        val miss =
          f"the bank doubles every $doubling%.1f s, against the ${size.time(limits.head._2)}%.1f s wanted: it " +
            "tightens faster than a pilot would want to keep correcting. More dihedral, or a smaller fin."
        ModalNormRow("Spiral", is, None, None, None, None, wants,
          levelVerdict(level, f"it diverges, but slowly — the bank doubles every $doubling%.1f s.", miss),
          Some(level == Some(1)), level, applied)
      case None => unidentified("Spiral", is, wants,
        "none of the real roots AVL found is a slow banking one. A model with plenty of dihedral can have " +
          "no distinct spiral at all.", shapesReported)
    }
  }

  /**
   * MIL-F-8785C 3.3.1.4: the roll mode and the spiral merged into one oscillation, which happens when the
   * two real roots meet. Not permitted at all for Category A; judged on `zeta*wn` for B and C.
   */
  private def coupledRollSpiralRow(candidate: Option[AvlEigenvalue],
                                   category: FlightPhaseCategory, size: FroudeScale): ModalNormRow = {
    val is = "roll and spiral merged into one slow wallow, instead of settling separately"
    val wants =
      if (category == FlightPhaseCategory.A) "not to exist at all in this Flight Phase"
      else f"damping x wn at least ${CoupledRollSpiralLimits.head._2}%.2f rad/s"
    val applied =
      if (size.scales && category != FlightPhaseCategory.A)
        Some(f"at this size: at least ${size.frequency(CoupledRollSpiralLimits.head._2)}%.2f rad/s")
      else None
    candidate match {
      case Some(mode) =>
        val zeta = mode.getDampingRatio.toDouble
        val wn = mode.getNaturalFrequency.toDouble
        val product = zeta * wn
        if (category == FlightPhaseCategory.A)
          ModalNormRow("Coupled roll-spiral", is, Some(wn), Some(zeta), periodOf(wn, zeta),
            swingsToHalfOf(zeta), wants,
            f"Not allowed: the standard forbids a coupled roll-spiral outright for rapid maneuvering, and " +
              f"this aircraft has one (damping x wn $product%.2f).", Some(false), None, applied)
        else {
          val level = levelMet(CoupledRollSpiralLimits.map { case (n, m) => (n, product >= size.frequency(m)) })
          ModalNormRow("Coupled roll-spiral", is, Some(wn), Some(zeta), periodOf(wn, zeta),
            swingsToHalfOf(zeta), wants,
            levelVerdict(level, f"damping x wn $product%.2f.",
              f"damping x wn is only $product%.2f — the wallow takes far too long to settle."),
            Some(level == Some(1)), level, applied)
        }
      case None =>
        ModalNormRow("Coupled roll-spiral", is, None, None, None, None, wants,
          "Not present, which is what is wanted: the roll and the spiral settle separately.",
          Some(true), Some(1), applied)
    }
  }

  /**
   * How long the aircraft takes to bank, with the roll control hard over — MIL-F-8785C 3.3.4, TABLE IXa.
   *
   * The whole of it comes from figures the model already states, so nothing is assumed. At a steady roll the
   * ailerons' rolling moment balances the roll damping, `Cldelta*delta + Clp*(p b / 2V) = 0`, which gives the
   * final roll rate; the roll mode's own time constant says how long it takes to arrive; and the bank angle
   * follows from `phi(t) = p (t - tau (1 - e^-t/tau))`, a first-order roll response integrated once.
   *
   * Two things it will **not** do. `Cldelta` is per unit of AVL's control variable, not per degree, so it is
   * converted through the control's gain — the same factor the JSBSim export was missing for years and which
   * makes a surface look three times weaker than it is. And if the aircraft has no aileron, no gain, no roll
   * mode or no measured roll damping, it says which one is missing rather than filling it in: a roll rate
   * invented from a default deflection would look exactly like a measured one.
   */
  private def rollPerformanceRow(calculation: AvlCalculation, category: FlightPhaseCategory,
                                 rollMode: Option[AvlEigenvalue], size: FroudeScale): ModalNormRow = {
    val is = "how long it takes to bank with the stick hard over — the aircraft's roll authority"
    val (angleDeg, limits) = rollPerformanceLimits(category)
    val wants = f"$angleDeg%.0f degrees of bank within ${limits.head._2}%.1f s"
    val applied = if (size.scales) Some(f"at this size: within ${size.time(limits.head._2)}%.2f s") else None

    def cannot(why: String) =
      ModalNormRow("Roll response", is, None, None, None, None, wants, "Not judged: " + why, None, None, applied)

    val config = calculation.getConfiguration
    val stab = calculation.getStabilityDerivatives
    if (config == null || stab == null) return cannot("AVL returned no configuration for this run.")
    val aileron = calculation.getAileronPosition
    val gains = calculation.getControlGains
    val stops = calculation.getControlMaxDeflections
    val cld = stab.getCld

    // `initControls` is what creates these arrays, and a calculation that never reached AVL has not had it
    // called. Reading past a null here is how this row would take the whole results window down with it.
    if (cld == null || gains == null || stops == null)
      return cannot("the control derivatives did not reach the calculation.")
    if (aileron < 0 || aileron >= cld.length) return cannot("no aileron was identified among the controls.")
    if (aileron >= gains.length || aileron >= stops.length)
      return cannot("the aileron's gain and travel did not reach the calculation.")

    val gain = gains(aileron).toDouble
    val stop = stops(aileron).toDouble
    val clDelta = cld(aileron).toDouble
    val clp = stab.getClp.toDouble
    val span = config.getSpanMetres.toDouble
    val speed = config.getVelocityMetresPerSecond.toDouble

    if (gain == 0.0) return cannot("the aileron's gain is zero, so AVL never deflects it and it does nothing.")
    if (stop.isNaN || stop <= 0.0) return cannot("the aileron states no maximum deflection.")
    if (clp >= 0.0) return cannot(f"AVL reports roll damping Clp of $clp%.4f, which is not a damping at all.")
    if (span <= 0.0 || speed <= 0.0) return cannot("the reference span or the speed is missing.")
    if (clDelta == 0.0) return cannot("AVL reports the aileron producing no rolling moment.")

    // Cl at full deflection: the control variable that reaches `stop` degrees is stop/gain.
    val clAtStop = math.abs(clDelta * (stop / gain))
    // Clp is per (p b / 2V), so the steady roll rate is in radians per second once b and V are in metres.
    val rollRate = clAtStop / math.abs(clp) * (2.0 * speed / span)
    val tau = rollMode.map(mode => -1.0 / mode.getSigma.toDouble)

    tau match {
      case None => cannot("AVL found no roll mode, so how quickly the roll builds up is not known.")
      case Some(timeConstant) =>
        val target = math.toRadians(angleDeg)
        // phi(t) = p (t - tau (1 - e^-t/tau)) rises monotonically, so bisection cannot miss.
        def bankAfter(t: Double): Double = rollRate * (t - timeConstant * (1.0 - math.exp(-t / timeConstant)))
        val reached = timeToReach(target, bankAfter)
        reached match {
          case None => cannot("the roll rate is too low to reach that bank angle at all.")
          case Some(t) =>
            val level = levelMet(limits.map { case (n, maxT) => (n, t <= size.time(maxT)) })
            val meets = f"$angleDeg%.0f degrees in $t%.2f s, rolling at " +
              f"${math.toDegrees(rollRate)}%.0f deg/s with $stop%.0f degrees of aileron."
            val miss = f"it takes $t%.2f s to bank $angleDeg%.0f degrees, against the " +
              f"${size.time(limits.head._2)}%.2f s wanted — the roll is slow. More aileron travel, more " +
              "aileron span, or less roll damping."
            ModalNormRow("Roll response", is, None, None, None, None, wants,
              levelVerdict(level, meets, miss), Some(level == Some(1)), level, applied)
        }
    }
  }

  /**
   * The other half of the short-period criterion: is it **quick** enough, not just damped enough
   * (MIL-F-8785C 3.2.2.1.1, FIGURES 1-3). Only the damping was ever judged.
   *
   * The quantity is the Control Anticipation Parameter, `CAP = wn_sp^2 / (n/alpha)`, where `n/alpha` is how
   * many g the aircraft pulls per radian of angle of attack. That sounds like it needs the weight, the air
   * and the wing area, and it does not: **in level flight the lift equals the weight**, so
   * `n/alpha = CLalpha / CL_trim` exactly, and both come straight back from AVL. The same identity is why
   * `AVL.analysisLiftCoefficient()` exists — this is it read the other way round.
   *
   * `CAP` has units of 1/s², so it follows the aircraft's size like any other frequency, squared: `n/alpha`
   * is dimensionless and does not scale, while `wn_sp` goes as `1/sqrt(b)`.
   */
  private def shortPeriodFrequencyRow(calculation: AvlCalculation, candidate: Option[AvlEigenvalue],
                                      category: FlightPhaseCategory, size: FroudeScale,
                                      shapesReported: Boolean): ModalNormRow = {
    val is = "how sharply the nose answers the elevator, against how much g the wing makes when it does"
    val limits = shortPeriodFrequencyLimits(category)
    val (_, minCap, maxCap) = limits.head
    val wants = f"a control anticipation parameter between $minCap%.3f and $maxCap%.1f"
    // CAP is a frequency squared, so it scales as the square of a frequency threshold.
    def scaled(cap: Double): Double = { val f = size.frequency(1.0); cap * f * f }
    val applied = if (size.scales) Some(f"at this size: between ${scaled(minCap)}%.2f and ${scaled(maxCap)}%.1f")
                  else None

    def cannot(why: String) =
      ModalNormRow("Short-period quickness", is, None, None, None, None, wants,
        "Not judged: " + why, None, None, applied)

    candidate match {
      case None => unidentified("Short-period quickness", is, wants,
        "none of the oscillating motions AVL found is a pitch one, so there is no frequency to measure.",
        shapesReported)
      case Some(mode) =>
        val stab = calculation.getStabilityDerivatives
        val config = calculation.getConfiguration
        if (stab == null || config == null) return cannot("AVL returned no derivatives for this run.")
        val clAlpha = stab.getCLa.toDouble
        val clTrim = config.getCLtot.toDouble
        if (clAlpha <= 0.0) return cannot(f"AVL reports a lift slope of $clAlpha%.3f, which is not one.")
        if (clTrim <= 0.0)
          return cannot(f"the aircraft trims at a lift coefficient of $clTrim%.3f, so it is not holding " +
            "level flight and there is no load factor per angle of attack to speak of.")

        val loadPerAlpha = clAlpha / clTrim
        val wn = mode.getNaturalFrequency.toDouble
        val cap = wn * wn / loadPerAlpha
        val level = levelMet(limits.map { case (n, lo, hi) => (n, cap >= scaled(lo) && cap <= scaled(hi)) })
        val meets = f"CAP $cap%.2f, at ${loadPerAlpha}%.1f g per radian."
        val miss =
          if (cap < scaled(minCap))
            f"too sluggish: CAP $cap%.3f against the ${scaled(minCap)}%.3f wanted. The nose answers slowly " +
              "for the g the wing makes — a bigger tailplane, or a longer tail arm."
          else
            f"too sharp: CAP $cap%.2f against the ${scaled(maxCap)}%.1f allowed. The aircraft is twitchy in " +
              "pitch for the g it produces, which is tiring to fly precisely."
        ModalNormRow("Short-period quickness", is, Some(wn), None, None, None, wants,
          levelVerdict(level, meets, miss), Some(level == Some(1)), level, applied)
    }
  }

  /** The first time a monotonically rising quantity reaches a target, or None if it never does. */
  private def timeToReach(target: Double, of: Double => Double): Option[Double] = {
    var high = 0.1
    while (of(high) < target && high < 600.0) high *= 2.0
    if (of(high) < target) return None
    var low = 0.0
    var i = 0
    while (i < 200) {
      val mid = 0.5 * (low + high)
      if (of(mid) < target) low = mid else high = mid
      i += 1
    }
    Some(0.5 * (low + high))
  }

  // ---------------------------------------------------------------------------------------------------
  // Which motion is which
  // ---------------------------------------------------------------------------------------------------

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

  /**
   * A coupled roll-spiral is the roll mode and the spiral having merged, so it is a lateral oscillation with
   * a great deal of bank in it and a frequency far below the dutch roll's — the dutch roll is a yawing motion
   * and this one is a wallowing roll. Below the Froude frequency of the aircraft's own size, which is where
   * the roll and spiral roots live, and bank-dominated rather than yaw-dominated.
   */
  private def findCoupledRollSpiralCandidate(modes: List[AvlEigenvalue], size: FroudeScale): Option[AvlEigenvalue] = {
    if (!size.known) return None
    val slowLimit = 1.0 / size.froudeTime
    val candidates = modes.filter(mode =>
      mode.getNaturalFrequency < slowLimit && mode.getBankRatio > mode.getRollRateRatio)
    if (candidates.isEmpty) None else Some(candidates.minBy(_.getNaturalFrequency))
  }

  /**
   * The roll subsidence: a decaying real root dominated by roll **rate**. It is the fastest of the lateral
   * real roots, because that is what "subsidence" means — the roll rate settling almost at once.
   */
  private def findRollModeCandidate(modes: List[AvlEigenvalue]): Option[AvlEigenvalue] = {
    val candidates = modes.filter(mode => mode.hasModeShape && mode.getSigma < 0f &&
      mode.getLateralRatio >= ModeDominanceThreshold &&
      mode.getRollRateRatio >= mode.getBankRatio)
    if (candidates.isEmpty) None else Some(candidates.minBy(_.getSigma))
  }

  /**
   * The spiral: the slowest lateral real root, dominated by bank and heading rather than by roll rate. It may
   * be stable or divergent, and both are ordinary — which is why it is picked before its sign is looked at.
   */
  private def findSpiralCandidate(modes: List[AvlEigenvalue]): Option[AvlEigenvalue] = {
    val candidates = modes.filter(mode => mode.hasModeShape &&
      mode.getLateralRatio >= ModeDominanceThreshold &&
      mode.getBankRatio > mode.getRollRateRatio)
    if (candidates.isEmpty) None else Some(candidates.maxBy(_.getSigma))
  }
}
