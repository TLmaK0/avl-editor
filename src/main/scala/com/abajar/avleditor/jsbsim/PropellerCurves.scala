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
 * The thrust and power curves of a free, fixed-pitch propeller, stretched to the diameter and
 * pitch the model actually states — see issue #19.
 *
 * ==What was wrong==
 *
 * Every exported propeller, whatever its diameter, pitch and blade count, wrote the same eleven
 * rows: an APC 9.4x5 / JSBSim `DJI_9450` sample, copied rather than scaled (every row byte-identical
 * to that reference's own table). `Ct` and `Cp` against `J = V/(nD)` are dimensionless, so the
 * diameter at least enters correctly through `T = Ct rho n^2 D^4` outside the table — but nothing
 * carried the **pitch**, which the model already asks for (`Propeller.H`, "Pitch in meters") and
 * which nothing read: `grep -rn "getH()"` returned one hit, its own getter. The pitch is exactly
 * the figure that decides where the curve's thrust runs out — the top speed, the climb rate, and
 * whether the throttle still does anything at cruise — and it was being thrown away.
 *
 * ==Why this is not the ducted fan's derivation==
 *
 * [[DuctedFanCurves]] derives its curve from momentum theory alone: given the disc area and the
 * power and revolutions the fan is bought at, Froude's actuator-disc theory gives `Ct(J)` and
 * `Cp(J)` with no geometry beyond the disc's own area. That works for a duct because the duct's
 * exit area *is* the parameter that matters.
 *
 * It does not carry over to a free propeller, and that is not a detail — it is the whole reason
 * this issue could not be closed the same way. Actuator-disc theory is blind to blade pitch: given
 * the same diameter and the same shaft power, it returns the identical curve for a fine-pitch racing
 * prop and a coarse-pitch cruise prop, because momentum theory only ever sees the area the air is
 * pushed through. The one thing a free propeller's pitch does that a duct's geometry does not is
 * fix *where the blade's own angle of attack reaches zero* — and that is a blade-element fact, not a
 * momentum one. Blade-element theory needs the blade's chord to turn an angle of attack into a
 * force, and the model states no chord — only diameter, pitch and blade count — so a blade-element
 * derivation here would have to invent one, which is the exact failure this file exists to retire.
 *
 * ==The derivation actually used==
 *
 * What the model's own fields can fix, without inventing anything, is **where the curve crosses
 * zero** — the advance ratio past which the propeller stops pushing. By the definition of geometric
 * pitch, a propeller advancing by exactly its pitch every revolution meets its own blade at zero
 * angle of attack: the "no-slip" advance ratio is `J = H/D`. A real blade does not stop exactly
 * there — profile drag means a small positive angle of attack, and so a small positive thrust,
 * persists a little past it — so the true zero-thrust advance ratio runs a little ahead of `H/D`
 * rather than sitting on it. That gap is measured, not guessed, from three real propellers: the
 * generic table's own APC 9.4x5 (`H/D` = 0.532, zero at `J` = 0.7291, a ratio of 1.371) and two
 * propellers pulled fresh from the UIUC Propeller Data Site's wind-tunnel measurements
 * (`m-selig.ae.illinois.edu/props/volume-2`, public domain, no login required):
 *
 *  - APC Free Flight 9x4 at 4033 rpm (`apcff_9x4_1018ga_4033.txt`): `H/D` = 0.444, `Ct` crosses
 *    zero between `J` = 0.5586 (`Ct` = 0.00155) and `J` = 0.6014 (`Ct` = -0.00667), i.e. at
 *    `J` ≈ 0.5666 — a ratio of 1.275.
 *  - APC Sport 9x6 at 4049 rpm (`apcsp_9x6_0760ga_4049.txt`): `H/D` = 0.667, `Ct` crosses zero
 *    between `J` = 0.7507 (`Ct` = 0.00638) and `J` = 0.7879 (`Ct` = -0.00117), i.e. at `J` ≈ 0.7821
 *    — a ratio of 1.173.
 *
 * Three propellers, three different pitch ratios, three ratios clustered at 1.17–1.37 — close
 * enough, and consistently on the same side (true zero always past the geometric one), that their
 * mean is the one stated assumption here ([[PitchStretchRatio]]), in the same spirit as
 * [[DuctedFanCurves.FigureOfMerit]]: a documented constant standing for an effect too small to model
 * from first principles with the fields available, bounded by measurement rather than invented.
 * Each of the three measurements is [[PitchStretchMeasurements]], a value in code rather than only
 * in this prose, and [[PitchStretchRatioRange]] is how far they actually disagreed — a constant with
 * no dispersion recorded beside it reads as more certain than the three propellers that produced it.
 *
 * The curve's **shape** — how `Ct` and `Cp` fall off between zero advance and the point they run
 * out — is kept from the generic sample, since nothing in the model says it should differ, and only
 * the sample's own `J` axis is stretched so its zero crossing lands at `PitchStretchRatio * H/D`
 * instead of at the APC 9.4x5's own 0.7291. `Cp` is stretched by the same factor on the same axis,
 * so the two curves still agree about which row belongs to which advance ratio.
 *
 * ==What this does not fix==
 *
 * Blade count is validated (at least two) but does not enter the curve, the same simplification
 * [[DuctedFanCurves]] makes for a fan's blade count — a known limitation, stated rather than
 * hidden, and nothing here claims a three-blade prop performs like a two-blade one of the same
 * diameter and pitch. Nor does the static thrust at `J` = 0 move with pitch: there is no measurement
 * here saying how it should, so it is left at the generic sample's own value, scaled only by
 * diameter and revolutions as `T = Ct rho n^2 D^4` already does outside this table.
 *
 * Pinned by [[PropellerCurvesCheck]].
 */
object PropellerCurves {

  /**
   * The generic sample's own `J` grid and the `Ct`/`Cp` it carries at each point: an APC 9.4x5 /
   * JSBSim `DJI_9450`, `H/D` = 5.0/9.4 = 0.5319, whose own zero crossing sits at `ReferenceJ0`.
   * [[from]] stretches this grid's `J` axis to a different propeller's pitch; it never touches the
   * coefficients themselves, which is the "shape kept, reach stretched" rule the class doc states.
   */
  private val ReferenceJ0 = 0.7291

  /**
   * One propeller used to measure [[PitchStretchRatio]]: its pitch-to-diameter ratio, the advance
   * ratio its thrust was actually measured crossing zero at, and where that measurement came from
   * — named so the constant it feeds is never a bare number with no way to check it.
   */
  final case class PitchStretchMeasurement(source: String, pitchRatio: Double, measuredJ0: Double) {
    /** How far past the geometric, no-slip advance ratio (`pitchRatio`) this one actually ran out. */
    def ratio: Double = measuredJ0 / pitchRatio
  }

  /**
   * The three propellers [[PitchStretchRatio]] is the mean of. Each is independently sourced and
   * spans a different pitch ratio, so the constant rests on a spread rather than one sample:
   *
   *  - the generic table itself needs no outside source — its own last row is where it already
   *    crosses zero;
   *  - the other two are read straight off real wind-tunnel runs published by the UIUC Propeller
   *    Data Site (`m-selig.ae.illinois.edu/props/volume-2`, public domain, no login required), with
   *    the file named so the figures can be checked against the source rather than this comment.
   */
  val PitchStretchMeasurements: Seq[PitchStretchMeasurement] = Seq(
    PitchStretchMeasurement(
      source = "the generic sample itself: an APC 9.4x5, JSBSim's own DJI_9450 reference",
      pitchRatio = 5.0 / 9.4,
      measuredJ0 = ReferenceJ0),
    PitchStretchMeasurement(
      source = "UIUC Propeller Data Site, volume 2, APC Free Flight 9x4 at 4033 rpm " +
        "(data/apcff_9x4_1018ga_4033.txt): Ct crosses zero between J=0.5586 (Ct=0.00155) and " +
        "J=0.6014 (Ct=-0.00667), interpolated",
      pitchRatio = 4.0 / 9.0,
      measuredJ0 = 0.56662),
    PitchStretchMeasurement(
      source = "UIUC Propeller Data Site, volume 2, APC Sport 9x6 at 4049 rpm " +
        "(data/apcsp_9x6_0760ga_4049.txt): Ct crosses zero between J=0.7507 (Ct=0.00638) and " +
        "J=0.7879 (Ct=-0.00117), interpolated",
      pitchRatio = 6.0 / 9.0,
      measuredJ0 = 0.78213)
  )

  /**
   * How far past the geometric, no-slip advance ratio (`H/D`) a real propeller's thrust actually
   * runs out, averaged over [[PitchStretchMeasurements]] — see there for each one's own ratio and
   * where it came from.
   */
  val PitchStretchRatio: Double =
    PitchStretchMeasurements.map(_.ratio).sum / PitchStretchMeasurements.length

  /**
   * The lowest and highest of the three measured ratios — 1.17 to 1.37, not a single agreed figure
   * — so [[PitchStretchRatio]] is never read as more certain than the three propellers behind it.
   */
  val PitchStretchRatioRange: (Double, Double) = {
    val ratios = PitchStretchMeasurements.map(_.ratio)
    (ratios.min, ratios.max)
  }

  private val GenericCt: Seq[(Double, Double)] = Seq(
    0.0000 -> 0.1288, 0.0730 -> 0.1230, 0.1470 -> 0.1153, 0.2287 -> 0.1053, 0.3022 -> 0.0932,
    0.3757 -> 0.0794, 0.4496 -> 0.0644, 0.5296 -> 0.0483, 0.6039 -> 0.0310, 0.6774 -> 0.0128,
    0.7291 -> -0.0001)

  private val GenericCp: Seq[(Double, Double)] = Seq(
    0.0000 -> 0.0666, 0.0730 -> 0.0611, 0.1470 -> 0.0567, 0.2287 -> 0.0531, 0.3022 -> 0.0498,
    0.3757 -> 0.0456, 0.4496 -> 0.0402, 0.5296 -> 0.0332, 0.6039 -> 0.0243, 0.6774 -> 0.0137,
    0.7291 -> 0.0061)

  /**
   * The advance ratio this propeller's thrust should run out at, from its own stated pitch.
   *
   * Guarded here, not only in [[from]]: `pitchM / diameterM` with either at zero is a `NaN` or an
   * `Infinity`, and a `NaN` reached through this path used to surface three steps downstream, as a
   * stretched table whose every row read `NaN` — exactly the failure issue #24 named for the motor
   * and this file exists not to repeat for the propeller. A caller that reaches this function with
   * nothing stated gets a named refusal here, at the division, rather than a number that only looks
   * wrong once it has already travelled into a generated file.
   */
  def targetJ0(diameterM: Double, pitchM: Double): Double = {
    require(diameterM > 0, "PropellerCurves.targetJ0: the propeller needs its diameter.")
    require(pitchM > 0, "PropellerCurves.targetJ0: the propeller needs its pitch.")
    PitchStretchRatio * pitchM / diameterM
  }

  /**
   * The curves, or one line saying which stated figure is missing. Nothing is substituted: the
   * diameter, the pitch and the blade count are all on the Propeller, and a propeller the user has
   * not described this far is one the export must refuse rather than hand a generic curve to.
   *
   * These checks and [[targetJ0]]'s own say the same thing for the same two fields, deliberately:
   * this is the friendly, UI-facing refusal for the path every real export takes, and `targetJ0`'s
   * `require` is the backstop for anything that calls it directly without going through here first.
   */
  def from(diameterM: Double, pitchM: Double, blades: Int): Either[String, JsbsimWriter.ThrusterCurves] = {
    if (diameterM <= 0)
      return Left("The propeller needs its diameter: the thrust and power follow from it.")
    if (pitchM <= 0)
      return Left("The propeller needs its pitch: it is what decides where the thrust runs out, " +
        "and the generic sample it used to borrow is somebody else's propeller.")
    if (blades < 2)
      return Left("The propeller needs at least 2 blades.")
    val scale = targetJ0(diameterM, pitchM) / ReferenceJ0
    Right(JsbsimWriter.ThrusterCurves(
      GenericCt.map { case (j, c) => (j * scale, c) },
      GenericCp.map { case (j, c) => (j * scale, c) }))
  }
}
