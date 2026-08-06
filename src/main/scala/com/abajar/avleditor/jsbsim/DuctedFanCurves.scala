/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.jsbsim

/**
 * The thrust and power curves of a ducted fan, derived from the figures a ducted fan is sold with.
 *
 * An EDF is neither a propeller nor a turbine, and JSBSim needs it to be neither: the element JSBSim calls
 * `propeller` is really a machine that absorbs shaft power and produces thrust as a function of advance ratio,
 * and everything it does comes out of two tables — `C_THRUST` and `C_POWER` against `J = V/(nD)`. Give it a
 * free propeller's curves and it is a propeller; give it a shrouded rotor's and it is a ducted fan. The
 * generic curves the exporter writes for a propeller are wrong for a fan in the way that matters: a free
 * propeller's thrust falls to zero at J ≈ 0.73, and a ducted fan — which exists to fly fast — holds it much
 * further.
 *
 * ==The derivation==
 *
 * A duct does not contract the wake, so the exit area is the fan area. That is the whole difference. With an
 * exit velocity `Ve`:
 *
 * {{{
 *   mass flow = rho A Ve
 *   thrust    = rho A Ve (Ve - V)
 *   power     = rho A Ve (Ve^2 - V^2) / 2
 * }}}
 *
 * Writing `k = Ve/(nD)` — how much air the fan throws per revolution, its effective pitch — and substituting
 * into JSBSim's own definitions (`T = Ct rho n^2 D^4`, `P = Cp rho n^3 D^5`) with `A = pi D^2 / 4`:
 *
 * {{{
 *   Ct(J) = (pi/4) k (k - J)
 *   Cp(J) = (pi/8) k (k^2 - J^2)
 * }}}
 *
 * One parameter. Three things say this is the right model and not a convenient one, and all three are asserted
 * in `DuctedFanCurvesCheck`:
 *
 *  - its efficiency, `J Ct/Cp`, comes out as `2J/(k + J)` — Froude's ideal propulsive efficiency, which is
 *    what a ducted fan's is;
 *  - `Ct` reaches zero at `J = k`, so `k` is the advance ratio at which the fan stops pushing rather than a
 *    fitting constant;
 *  - at `J = 0` it gives `2^(1/3)` = 1.26 times the static thrust of a free propeller of the same diameter on
 *    the same power, which is the known advantage of a shrouded rotor and follows here from the wake not
 *    being contracted.
 *
 * ==The losses: measured when they can be, stated when they cannot==
 *
 * Momentum theory knows nothing about tip clearance, the inlet lip or the diffuser, and the power it is given
 * here is electrical rather than shaft power, so the ideal curves overstate the thrust by about half.
 *
 * When the fan states a measured static thrust the correction is the user's own two numbers divided by each
 * other, which is better than any constant:
 *
 * {{{ figure of merit = stated static thrust / ideal static thrust }}}
 *
 * But a fan is often bought as a rotor and a housing with no motor, and then no thrust is published, because
 * the thrust depends on the motor fitted. So the figure of merit falls back to [[DefaultFigureOfMerit]], a
 * stated assumption in the same class as the 0.6 [[FlightSanity]] already uses for a propeller's static thrust:
 * about 0.8 for the motor turning electrical power into shaft power and about 0.65 for the duct, which is also
 * what published thrust figures for complete units come to when worked through this derivation. The export log
 * says which of the two happened and what thrust it implies, so an assumption is never mistaken for a
 * measurement.
 *
 * Either way `Ct` is scaled and `Cp` is left alone, because a loss costs thrust for the same shaft power. With
 * a measured thrust the exported fan then draws the current the listing states and pushes what it says it
 * pushes, and the shape of the curve — the part nobody publishes — is the physics.
 */
object DuctedFanCurves {

  /** Air at sea level, the condition a fan's static thrust is quoted at. */
  val AirDensity = 1.225

  /** How many points the tables carry. Enough for the curve, few enough to read in the file. */
  val Rows = 11

  /**
   * What a ducted fan achieves of the ideal, when it has not been measured.
   *
   * A stated assumption, and the same kind as the 0.6 [[FlightSanity]] uses for a propeller: roughly 0.8 for
   * the motor turning electrical watts into shaft watts — the editor states the electrical figure, as
   * documented in AGENTS.md — times about 0.65 for the duct itself. Complete units whose published thrust is
   * worked back through this derivation land between 0.45 and 0.55, which is where this comes from.
   */
  val DefaultFigureOfMerit = 0.5

  /**
   * What a fan states about itself, all of it from the listing it was bought from: the duct's inner diameter
   * (the disc the air passes through, not the housing or the inlet lip), the blade count, the revolutions its
   * motor turns at on the stated cells, the electrical power it draws and — when it is published, which a bare
   * rotor and housing never is — the static thrust it produces. Zero means not stated.
   */
  final case class Fan(innerDiameterM: Double,
                       blades: Int,
                       rpm: Double,
                       powerWatts: Double,
                       staticThrustN: Double)

  /**
   * The fan as JSBSim needs it stated. `k` is where the thrust runs out, `figureOfMerit` how much of the ideal
   * the fan actually achieves, and the two tables are indexed by advance ratio.
   */
  final case class Curves(k: Double,
                          figureOfMerit: Double,
                          /** True when the figure of merit came from a stated thrust rather than the default. */
                          lossesMeasured: Boolean,
                          idealStaticThrustN: Double,
                          ct: Seq[(Double, Double)],
                          cp: Seq[(Double, Double)])

  /** Thrust coefficient at zero advance, ideal: the fan pushing on undisturbed air. */
  private def idealCtAtRest(k: Double): Double = math.Pi / 4 * k * k

  /**
   * The curves, or one line saying which stated figure is missing. Nothing is substituted: every quantity here
   * is on the listing, and a fan the user has not described is one the export must refuse.
   */
  def from(fan: Fan): Either[String, Curves] = {
    if (fan.innerDiameterM <= 0)
      return Left("The ducted fan needs its inner duct diameter: it is the disc the air passes through, and " +
        "the thrust follows from its area.")
    if (fan.blades < 2)
      return Left("The ducted fan needs at least 2 blades.")
    if (fan.rpm <= 0)
      return Left("The ducted fan needs the revolutions it turns at, which follow from the motor's kV and " +
        "the cells it runs on.")
    if (fan.powerWatts <= 0)
      return Left("The ducted fan needs the power it draws, which is the stated voltage times the stated " +
        "current.")
    val d = fan.innerDiameterM
    val n = fan.rpm / 60.0 // JSBSim's coefficients are per revolution per second
    val cpAtRest = fan.powerWatts / (AirDensity * n * n * n * math.pow(d, 5))
    val k = math.cbrt(8.0 * cpAtRest / math.Pi)
    if (!(k > 0) || k.isNaN || k.isInfinite)
      return Left("The ducted fan's figures do not describe a fan: the power and the revolutions given " +
        "cannot be produced by a disc of that diameter.")

    val idealStatic = idealCtAtRest(k) * AirDensity * n * n * math.pow(d, 4)
    // Measured when the fan states a thrust; the stated assumption when it does not, which is the usual case
    // for a rotor and housing bought without a motor.
    val measured = fan.staticThrustN > 0
    val figureOfMerit = if (measured) fan.staticThrustN / idealStatic else DefaultFigureOfMerit

    // The grid runs to k, where the thrust runs out. Beyond it JSBSim holds the last row, so the fan neither
    // pushes nor drags at absurd speeds — the same behaviour as the propeller tables, and honest in the same
    // way: a windmilling fan does produce drag, and this model does not claim to know how much.
    val js = (0 until Rows).map(i => k * i / (Rows - 1).toDouble)
    val ct = js.map(j => (j, figureOfMerit * math.Pi / 4 * k * (k - j)))
    // Cp is left at its ideal: a loss costs thrust for the same shaft power, it does not reduce the power the
    // fan draws. It reaches zero with Ct, at the advance ratio where the fan stops working on the air.
    val cp = js.map(j => (j, math.Pi / 8 * k * (k * k - j * j)))

    Right(Curves(k, figureOfMerit, measured, idealStatic, ct, cp))
  }

  /**
   * The static thrust a free propeller of the same diameter would ideally give on the same power, for the
   * comparison that says the duct is worth having: `T = (2 rho A P^2)^(1/3)`, the same expression
   * [[FlightSanity]] uses to judge a propeller.
   */
  def freePropellerIdealStaticThrustN(diameterM: Double, powerWatts: Double): Double = {
    val area = math.Pi * diameterM * diameterM / 4.0
    math.cbrt(2.0 * AirDensity * area * powerWatts * powerWatts)
  }

  /** The efficiency the model implies at an advance ratio: Froude's, which is what it should be. */
  def froudeEfficiency(k: Double, j: Double): Double = 2.0 * j / (k + j)
}
