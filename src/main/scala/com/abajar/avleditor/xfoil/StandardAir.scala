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

/**
 * The one property of air the model does not state and a viscous calculation cannot do without: its
 * **dynamic viscosity**, which is what turns a size and a speed into a Reynolds number.
 *
 * XFOIL is asked for a section's `clmax` at a Reynolds number, and `Re = rho V c / mu`. The model states
 * `rho` (it is an editable field), `V` and `c`; it states no temperature at all, and `mu` depends on
 * temperature and on nothing else.
 *
 * So the temperature is a **stated assumption** — 288.15 K, standard sea level — and the viscosity that
 * follows from it is derived rather than remembered, from the U.S. Standard Atmosphere, 1976:
 *
 * {{{
 *   mu = beta T^(3/2) / (T + S)         eq. (51), p. 19
 *   beta = 1.458e-6 kg/(s m K^(1/2))    table 2, p. 2
 *   S    = 110.4 K                      Sutherland's constant, stated on p. 19
 * }}}
 *
 * (Table 2 prints S as 110; the same document's p. 19 says 110.4, and NASA's errata sheet bound with the
 * scan confirms the table is the typo. Both pages are in `docs/references/ussa1976-excerpt.pdf`.)
 *
 * At 288.15 K that gives 1.7894e-5 Pa s. The assumption is a mild one and it is worth saying why rather
 * than only that it is made: `mu` moves about 10 % across the whole troposphere, a section's `clmax`
 * moves by a few percent for a **factor of two** in Reynolds, and the stall speed goes as the square root
 * of that. An aircraft flown on a hot day is not a different aeroplane. A density typed into the model is
 * a far larger lever, and that one the user holds.
 */
object StandardAir {

  /** U.S. Standard Atmosphere, 1976, table 2 (p. 2): Sutherland's `beta`, in kg/(s·m·K^0.5). */
  val SutherlandBeta: Double = 1.458e-6

  /** U.S. Standard Atmosphere, 1976, p. 19: Sutherland's constant `S`, in K. */
  val SutherlandS: Double = 110.4

  /** U.S. Standard Atmosphere, 1976, table 2 (p. 2): sea-level temperature `T0`, in K. */
  val SeaLevelTemperatureK: Double = 288.15

  /** Dynamic viscosity in Pa·s at a temperature in K — eq. (51). */
  def dynamicViscosity(temperatureK: Double): Double =
    SutherlandBeta * math.pow(temperatureK, 1.5) / (temperatureK + SutherlandS)

  /** What the editor uses, the model stating no temperature: 1.7894e-5 Pa·s. */
  val DynamicViscosity: Double = dynamicViscosity(SeaLevelTemperatureK)

  /**
   * `Re = rho V c / mu`, with every argument in SI. Returns None rather than a number when any of them
   * is absent or absurd: a Reynolds number of zero is one XFOIL would answer for, inviscidly, and the
   * answer would look like data.
   */
  def reynolds(densityKgPerM3: Double, speedMetresPerSecond: Double, chordMetres: Double): Option[Double] = {
    if (densityKgPerM3 <= 0.0 || speedMetresPerSecond <= 0.0 || chordMetres <= 0.0) None
    else Some(densityKgPerM3 * speedMetresPerSecond * chordMetres / DynamicViscosity)
  }
}
