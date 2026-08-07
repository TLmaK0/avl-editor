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
 * What the aircraft does when the stick goes hard over and the rudder is left alone: the manoeuvre
 * MIL-F-8785C 3.3.2.2 and 3.3.2.4 are both written about (p. 24), measured the way 6.2.6 defines it (p. 78).
 *
 * Neither is a coefficient. `p_osc/p_av` is defined **on the peaks of the roll-rate trace**, and `delta_beta`
 * is the largest sideslip within a stated window, so the response has to be built and then measured. There
 * is a closed form for the response — the system is linear — but not for its peaks: `dp/dt = 0` mixes
 * exponentials with sines and does not solve. That is why this reads a trajectory rather than an equation,
 * and it is not a simulation of a second aircraft: the system it integrates is the one
 * [[LateralModel]] verified root-for-root against AVL.
 */
final case class RollSideslipCoupling(oscillationRatio: Double, sideslipDegrees: Double,
                                      peaks: Int, proverse: Boolean, windowCutAtNinetyDegrees: Boolean,
                                      windowSeconds: Double)

object RollSideslipCoupling {

  /**
   * MIL-F-8785C 3.3.2.2 (p. 24), Level 1: the roll rate at the first minimum after the first peak must keep
   * its sign and hold this fraction of the peak. Stated as a percentage in the table.
   */
  def rollOscillationLimits(category: FlightPhaseCategory): List[(Int, Double)] =
    category match {
      case FlightPhaseCategory.B => List((1, 0.25), (2, 0.00))
      case _                     => List((1, 0.60), (2, 0.25))
    }

  /**
   * MIL-F-8785C 3.3.2.4 (p. 24), in degrees: adverse first, then proverse. Adverse sideslip is the nose
   * going the wrong way — right stick giving right sideslip — and the standard is far stricter about
   * proverse, which is the rarer and more confusing of the two.
   */
  def sideslipLimits(category: FlightPhaseCategory): List[(Int, Double, Double)] =
    category match {
      case FlightPhaseCategory.A => List((1, 6.0, 2.0), (2, 15.0, 4.0))
      case _                     => List((1, 10.0, 3.0), (2, 15.0, 4.0))
    }

  /**
   * Fly it and measure it.
   *
   * The command is held "until the bank angle has changed at least 90 degrees" (3.3.2.2), and the sideslip
   * is read over "2 seconds or one half-period of the Dutch roll, whichever is greater" (6.2.6). Both are
   * taken from the aircraft rather than assumed: the run ends when the bank reaches 90 degrees, and the
   * window comes from the dutch roll this same run found.
   */
  def of(model: LateralModel, deflectionRad: Double, dutchRollPeriod: Option[Double],
         dampingRatio: Double, secondsWindow: Double): Option[RollSideslipCoupling] = {
    if (model.bAileron == null || deflectionRad == 0.0) return None
    // A step small enough that the roll mode — the fastest root, and the one the model was verified on —
    // is resolved many times over.
    val dt = 0.001
    val trace = model.stepResponse(deflectionRad, dt, 30.0)
    if (trace.isEmpty) return None

    // "The roll command shall be held fixed until the bank angle has changed **at least** 90 degrees"
    // (3.3.2.2). At least — so a model that reaches 90 degrees in a fifth of a second is still held, and
    // has to be, or the dutch roll never gets a chance to put the sag in the roll rate that this whole
    // criterion is about. The hold runs to whichever is longer: ninety degrees of bank, or three periods of
    // the dutch roll doing its worst.
    val quarterTurn = math.toRadians(90.0)
    val timeToQuarter = trace.find { case (_, _, _, _, phi) => math.abs(phi) >= quarterTurn }.map(_._1)
    val hold = math.max(timeToQuarter.getOrElse(trace.last._1), dutchRollPeriod.getOrElse(1.0) * 3.0)
    val flown = trace.takeWhile { case (t, _, _, _, _) => t <= hold }
    if (flown.size < 10) return None

    // The peaks of the roll rate, in the sense 6.2.6 means: local maxima of its magnitude.
    val rolls = flown.map { case (_, _, p, _, _) => p }.toArray
    val peakValues = scala.collection.mutable.ListBuffer[Double]()
    var i = 1
    while (i < rolls.length - 1 && peakValues.size < 3) {
      val magnitude = math.abs(rolls(i))
      if (magnitude >= math.abs(rolls(i - 1)) && magnitude > math.abs(rolls(i + 1))) peakValues += rolls(i)
      i += 1
    }

    // 6.2.6 (p. 78): below 0.2 of damping the ratio uses three peaks, above it two.
    val ratio =
      if (peakValues.size >= 3 && dampingRatio <= 0.2) {
        val (p1, p2, p3) = (peakValues(0), peakValues(1), peakValues(2))
        val denominator = p1 + p3 + 2 * p2
        if (math.abs(denominator) < 1e-12) 0.0 else math.abs((p1 + p3 - 2 * p2) / denominator)
      } else if (peakValues.size >= 2) {
        val (p1, p2) = (peakValues(0), peakValues(1))
        val denominator = p1 + p2
        if (math.abs(denominator) < 1e-12) 0.0 else math.abs((p1 - p2) / denominator)
      } else 0.0

    // The sideslip window: two seconds, or half a dutch-roll period if that is longer.
    // 6.2.6: "the maximum change in sideslip occurring within 2 seconds or one half-period of the Dutch
    // roll, whichever is greater". Measured from the input, not from wherever the bank happens to reach 90.
    // The "2 seconds" is a **dimensional** time, and follows the aircraft's size like every other one in
    // this project. It has to: held for two real seconds a 1.2 m model at full aileron turns through nearly
    // three rotations, and a small-perturbation model has no business being read that far out. Scaled, the
    // window is what two seconds is worth to an aeroplane this size.
    val stated = math.max(secondsWindow, dutchRollPeriod.map(_ / 2.0).getOrElse(0.0))
    // ...but never past ninety degrees of bank, which is both the extent 3.3.2.2 states for the manoeuvre
    // and the edge of where a small-perturbation model means anything: the gravity term that drives sideslip
    // is g cos(alpha) phi / V, linear in phi, and a model that has rolled through three hundred degrees is
    // being asked a question its equations cannot answer. Reading it out there would be inventing a number.
    val window = math.min(stated, timeToQuarter.getOrElse(stated))
    val truncated = timeToQuarter.exists(_ < stated)
    val inWindow = trace.takeWhile { case (t, _, _, _, _) => t <= window }
    val sideslips = (if (inWindow.size < 2) trace else inWindow).map { case (_, beta, _, _, _) => beta }
    // Adverse is sideslip **against** the roll: right stick, right sideslip. The sign of the commanded roll
    // says which way is which, so the aircraft is asked rather than a convention assumed.
    val rollDirection = if (flown.last._5 >= 0) 1.0 else -1.0
    val worstAdverse = sideslips.map(_ * rollDirection).max
    val worstProverse = -sideslips.map(_ * rollDirection).min
    val adverse = worstAdverse >= worstProverse
    val excursion = math.toDegrees(math.max(worstAdverse, worstProverse))

    Some(RollSideslipCoupling(ratio, excursion, peakValues.size, !adverse, truncated, window))
  }
}
