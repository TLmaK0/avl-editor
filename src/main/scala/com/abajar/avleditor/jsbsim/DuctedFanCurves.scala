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
 * ==The losses are measured, not assumed==
 *
 * Momentum theory knows nothing about tip clearance, the inlet lip or the diffuser, so the ideal curves
 * overstate the thrust — for a 90 mm fan drawing 1.8 kW the ideal comes to about 4.7 kg where the listing says
 * 2.2. The correction is the user's own two numbers divided by each other, so it is measured:
 *
 * {{{ figure of merit = stated static thrust / ideal static thrust }}}
 *
 * `Ct` is scaled by it and `Cp` is left alone, because a loss costs thrust for the same shaft power. The
 * exported fan then draws the current the listing states and pushes what the listing states it pushes, and the
 * shape of the curve — the part nobody publishes — is the physics.
 *
 * Without a stated thrust there is no figure of merit and this refuses, rather than handing over the ideal: an
 * aircraft with twice its real thrust flies, looks plausible and is wrong.
 */
object DuctedFanCurves {

  /** Air at sea level, the condition a fan's static thrust is quoted at. */
  val AirDensity = 1.225

  /** How many points the tables carry. Enough for the curve, few enough to read in the file. */
  val Rows = 11

  /**
   * What a fan states about itself, all of it from the listing it was bought from: the duct's inner diameter
   * (the disc the air passes through, not the housing or the inlet lip), the blade count, the revolutions its
   * motor turns at on the stated cells, the electrical power it draws and the static thrust it produces.
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
    if (fan.staticThrustN <= 0)
      return Left("The ducted fan needs the static thrust it is sold with. Without it there is nothing to " +
        "measure the duct's losses against, and momentum theory alone overstates the thrust by about twice.")

    val d = fan.innerDiameterM
    val n = fan.rpm / 60.0 // JSBSim's coefficients are per revolution per second
    val cpAtRest = fan.powerWatts / (AirDensity * n * n * n * math.pow(d, 5))
    val k = math.cbrt(8.0 * cpAtRest / math.Pi)
    if (!(k > 0) || k.isNaN || k.isInfinite)
      return Left("The ducted fan's figures do not describe a fan: the power and the revolutions given " +
        "cannot be produced by a disc of that diameter.")

    val idealStatic = idealCtAtRest(k) * AirDensity * n * n * math.pow(d, 4)
    val figureOfMerit = fan.staticThrustN / idealStatic

    // The grid runs to k, where the thrust runs out. Beyond it JSBSim holds the last row, so the fan neither
    // pushes nor drags at absurd speeds — the same behaviour as the propeller tables, and honest in the same
    // way: a windmilling fan does produce drag, and this model does not claim to know how much.
    val js = (0 until Rows).map(i => k * i / (Rows - 1).toDouble)
    val ct = js.map(j => (j, figureOfMerit * math.Pi / 4 * k * (k - j)))
    // Cp is left at its ideal: a loss costs thrust for the same shaft power, it does not reduce the power the
    // fan draws. It reaches zero with Ct, at the advance ratio where the fan stops working on the air.
    val cp = js.map(j => (j, math.Pi / 8 * k * (k * k - j * j)))

    Right(Curves(k, figureOfMerit, idealStatic, ct, cp))
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
