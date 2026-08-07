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

import com.abajar.avleditor.avl.runcase.StripForce

/**
 * Where an aeroplane stops lifting, by the **critical-section method**: not a factor times a section's
 * `clmax`, but the attitude at which the hardest-working strip of the wing first reaches the maximum its
 * own aerofoil can produce.
 *
 * The method is NACA Report No. 572, *Determination of the Characteristics of Tapered Wings*
 * (R. F. Anderson, 1936) — `docs/references/naca-tr-572.pdf`, and its final section, "Estimation of
 * maximum lift coefficient". Its statement of the rule is the whole of this file:
 *
 * > As soon as the `c_l` curve becomes tangent to the stalling `c_lmax` curve, the section at that point
 * > reaches its maximum lift coefficient and stalling should soon spread over a considerable part of the
 * > wing. (p. 12)
 *
 * Anderson's own check of it: a 4:1 tapered wing came out at 1.31 against a measured 1.32.
 *
 * **The straight line the report draws is not assumed here.** TR 572 splits the loading into a basic
 * distribution "that depends principally on the twist of the wing and occurs when the total lift of the
 * wing is zero" and an additional one that "maintains the same form throughout the reasonably straight
 * part of the lift curve" (pp. 1-2) — which makes each strip's `c_l` a straight line, and lets the whole
 * thing be done with two numbers per station. It nearly is. Measured across thirteen attitudes on the
 * check aircraft it is straight to **0.034 of local `c_l` over 30 degrees**, about 2.5 %, and it bends the
 * way it must: AVL's solution is linear in the freestream *vector*, whose components are `cos(alpha)` and
 * `sin(alpha)`, and not in `alpha`.
 *
 * Two and a half percent of `c_l` is about a degree of attitude at the stall, and there is no reason to
 * spend it: the sweep has already measured every station at thirteen attitudes, so the crossing is read
 * off those measurements directly. The straight line survives as `clPerDegree` — which says which way a
 * station answers attitude at all, and so tells a wing from a fin — and as `worstResidual`, which is the
 * report's approximation **measured** rather than taken on trust.
 *
 * Nothing here runs XFOIL or reads the model: the section limits arrive already resolved, so the
 * arithmetic that decides where an aeroplane stalls can be checked without either.
 */

/** One spanwise strip, at every attitude the sweep measured it. */
case class LoadedStation(
  surface: String,
  mirrored: Boolean,
  index: Int,
  /** Spanwise station in the model's length unit, reflected back onto the half the model states. */
  station: Double,
  chordMetres: Double,
  /** The attitudes, in degrees, in increasing order. */
  attitudesDeg: Seq[Double],
  /** The local lift coefficient at each of them, referred to this strip's own area and chord. */
  liftCoefficients: Seq[Double],
  clAtZeroDeg: Double,
  clPerDegree: Double,
  /** How far the measurements fall from the straight line TR 572 draws through them. */
  worstResidual: Double
) {
  def describe: String =
    f"$surface%s${if (mirrored) " (mirrored)" else ""}%s at y = $station%.3f"

  private def sample(i: Int): (Double, Double) = (attitudesDeg(i), liftCoefficients(i))

  /**
   * The lowest attitude at which this station's lift reaches `limit`, by interpolation between the
   * attitudes measured — `Left` when it is already there at the bottom of the sweep, `None` when it never
   * gets there within it. Rising for a limit above the loading, falling for one below.
   */
  def reaches(limit: Double, rising: Boolean): Either[String, Option[Double]] = {
    def past(cl: Double) = if (rising) cl >= limit else cl <= limit
    if (liftCoefficients.isEmpty) return Right(None)
    if (past(liftCoefficients.head))
      return Left(f"$describe%s is already at its aerofoil's limit of $limit%.3f at " +
        f"${attitudesDeg.head}%.1f deg, the bottom of the attitudes AVL measured")
    val crossing = (1 until liftCoefficients.length).find(i => past(liftCoefficients(i)))
    Right(crossing.map { i =>
      val (a1, c1) = sample(i - 1)
      val (a2, c2) = sample(i)
      if (c2 == c1) a2 else a1 + (a2 - a1) * (limit - c1) / (c2 - c1)
    })
  }
}

/**
 * What one station's aerofoil can do, at that station's own Reynolds number.
 *
 * Both limits are optional, and `clMax` being absent is a real answer rather than a hole: XFOIL will not
 * always show an aerofoil giving up. A 10 % symmetric section at Re 60,000 — an ordinary model tailplane —
 * comes back with a polar still climbing at 20 degrees, because the code does not model the separation
 * that would end it. That station then has **no known limit**, so it takes no part in deciding where the
 * aircraft stalls, and whoever asked is told which stations those were.
 */
case class StationLimits(station: LoadedStation, clMax: Option[Double], clMin: Option[Double])

/** The strip that reaches its aerofoil's limit first, and the attitude at which it does. */
case class StallOnset(
  station: LoadedStation,
  sectionLimit: Double,
  alphaDeg: Double,
  downward: Boolean
)

object WingMaximumLift {

  /** Below this a strip's lift does not respond to attitude at all — a fin, and never the critical one. */
  private val NegligibleSlopePerDegree = 1.0e-6

  /**
   * One station per strip, carrying every attitude it was measured at.
   *
   * A strip is identified by its surface, its side and AVL's own strip number, so the same physical strip
   * is followed from one attitude to the next. Strips that do not appear at every attitude are dropped:
   * a station measured at some attitudes and not others has no curve through it, and one built from the
   * ones that happen to be there would be a different aeroplane at each end.
   */
  def stations(sweep: Seq[(Double, Seq[StripForce])], metresPerLengthUnit: Double): Seq[LoadedStation] = {
    val usable = sweep.filter(_._2.nonEmpty)
    if (usable.length < 2) return Seq.empty

    def key(strip: StripForce) = (strip.getSurfaceName, strip.isMirrored, strip.getIndex)
    val everywhere = usable.map(_._2.map(key).toSet).reduce(_ intersect _)

    val byKey = usable.flatMap { case (alpha, strips) =>
      strips.filter(strip => everywhere.contains(key(strip))).map(strip => (key(strip), (alpha, strip)))
    }.groupBy(_._1).mapValues(_.map(_._2))

    byKey.toSeq.map { case ((surface, mirrored, index), samples) =>
      val ordered = samples.sortBy(_._1)
      val alphas = ordered.map(_._1)
      val cls = ordered.map(_._2.getCl.toDouble)
      val n = alphas.length
      val meanAlpha = alphas.sum / n
      val meanCl = cls.sum / n
      val sxx = alphas.map(a => (a - meanAlpha) * (a - meanAlpha)).sum
      val sxy = alphas.zip(cls).map { case (a, c) => (a - meanAlpha) * (c - meanCl) }.sum
      val slope = if (sxx <= 0.0) 0.0 else sxy / sxx
      val intercept = meanCl - slope * meanAlpha
      val residual = alphas.zip(cls).map { case (a, c) => math.abs(c - (intercept + slope * a)) }.max
      val head = ordered.head._2
      LoadedStation(surface, mirrored, index, head.getStationY.toDouble,
        head.getChord.toDouble * metresPerLengthUnit, alphas, cls, intercept, slope, residual)
    }.sortBy(s => (s.surface, s.mirrored, s.index))
  }

  /**
   * The first station to reach its aerofoil's limit, searching **upwards in attitude** — which is the
   * direction an aeroplane stalls in.
   *
   * A strip whose lift falls with attitude (a tailplane carrying a download) stalls the other way up, and
   * it is judged against its aerofoil's `clMin` when there is one. A strip whose lift barely answers
   * attitude at all — a fin — never becomes critical, and needs no special case beyond not dividing by its
   * slope: nothing it does crosses anything.
   *
   * Everything is read inside the attitudes AVL was asked about. A wing that reaches nothing within them
   * is **refused**, and the refusal says how close it came, because continuing the loading out past the
   * last measurement is exactly the extrapolation this whole feature exists to stop doing.
   */
  def onset(limits: Seq[StationLimits]): Either[String, StallOnset] = {
    if (limits.isEmpty) return Left("no spanwise station could be matched to an aerofoil")

    val answers = limits.map { limit =>
      val s = limit.station
      if (s.clPerDegree > NegligibleSlopePerDegree)
        limit.clMax match {
          case None => Right(None)
          case Some(max) => s.reaches(max, rising = true).right.map(_.map(a =>
            StallOnset(s, max, a, downward = false)))
        }
      else if (s.clPerDegree < -NegligibleSlopePerDegree)
        limit.clMin match {
          case None => Right(None)
          case Some(min) => s.reaches(min, rising = false).right.map(_.map(a =>
            StallOnset(s, min, a, downward = true)))
        }
      else Right(None)
    }

    answers.collectFirst { case Left(why) => why } match {
      case Some(why) =>
        return Left(why + " — this aircraft has no attitude AVL measured at which it is not stalled")
      case None =>
    }

    val candidates = answers.collect { case Right(Some(onset)) => onset }
    if (candidates.nonEmpty) return Right(candidates.minBy(_.alphaDeg))

    val highest = limits.flatMap(_.station.attitudesDeg).reduceOption(_ max _).getOrElse(Double.NaN)
    val nearest = limits.filter(_.station.clPerDegree > NegligibleSlopePerDegree)
      .flatMap(l => l.clMax.map(max => (l.station, max, max - l.station.liftCoefficients.max)))
      .reduceOption((a, b) => if (a._3 <= b._3) a else b)
    nearest match {
      case None => Left("no spanwise station has both a lift that answers attitude and an aerofoil " +
        "limit to reach, so nothing on this aircraft can be said to stall")
      case Some((station, max, short)) =>
        Left(f"no station reaches its aerofoil's limit within the attitudes AVL measured (up to " +
          f"$highest%.1f deg); the nearest is ${station.describe}%s, still $short%.3f of local cl " +
          f"short of its $max%.3f. Continuing its loading out past the last measurement would be " +
          "the extrapolation this measurement replaces")
    }
  }

  /**
   * `CL` at an attitude, interpolated between the attitudes AVL measured — never beyond the last of them.
   */
  def liftAt(curve: Seq[(Double, Double)], alphaDeg: Double): Option[Double] = {
    val ordered = curve.sortBy(_._1)
    if (ordered.length < 2) return None
    if (alphaDeg < ordered.head._1 || alphaDeg > ordered.last._1) return None
    ordered.sliding(2).collectFirst {
      case Seq((a1, c1), (a2, c2)) if alphaDeg >= a1 && alphaDeg <= a2 =>
        if (a2 == a1) c1 else c1 + (c2 - c1) * (alphaDeg - a1) / (a2 - a1)
    }
  }

  /**
   * The speed at which level flight needs exactly `clMax`: `Vs = sqrt(2 W / (rho S CLmax))`.
   *
   * Everything in SI, and `weightNewtons` is a weight rather than a mass, because that is what the lift
   * has to equal. Returns None rather than a number when any of it is missing.
   */
  def stallSpeed(weightNewtons: Double, densityKgPerM3: Double, wingAreaM2: Double,
                 clMax: Double): Option[Double] = {
    if (weightNewtons <= 0.0 || densityKgPerM3 <= 0.0 || wingAreaM2 <= 0.0 || clMax <= 0.0) None
    else Some(math.sqrt(2.0 * weightNewtons / (densityKgPerM3 * wingAreaM2 * clMax)))
  }
}
