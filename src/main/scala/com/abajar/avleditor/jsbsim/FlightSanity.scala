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

import com.abajar.avleditor.crrcsim.CRRCSim
import com.abajar.avleditor.avl.runcase.AvlCalculation
import scala.collection.JavaConverters._

/**
 * Whether the aircraft the model describes would actually fly — as opposed to whether it can be
 * handed to a simulator at all, which is [[SimulationRequirements]]'s business.
 *
 * These are **warnings, not refusals**. Nothing here is missing data: the model states a motor, a
 * propeller and a weight, and every figure is the user's to choose. What the editor can do is the
 * arithmetic the user would otherwise do on paper, and say so before FlightGear opens on an aircraft
 * that will sit on the runway at full throttle — the case that sent us hunting for the wrong thing:
 * 3 W and a 10 cm propeller on a kilogram of aeroplane, which reads on screen exactly like a
 * simulator that ignores the throttle.
 *
 * Every threshold is a rule of thumb from model flying, stated as such and quoted in the message so
 * it can be argued with. Nothing is changed behind the user's back and the launch goes ahead.
 */
object FlightSanity {

  /** Air at sea level, kg/m³. */
  private val AirDensity = 1.225

  /**
   * How much of the ideal static thrust a model propeller really delivers. Momentum theory gives the
   * best any disc could do; a real propeller standing still reaches about this fraction of it (the
   * static figure of merit, 0.5 to 0.7 for the usual model propellers). Used only to judge, never to
   * fly: what JSBSim flies is the propeller's own tables.
   */
  private val StaticFigureOfMerit = 0.6

  /** Below this the aircraft cannot accelerate to flying speed on its own wheels. */
  private val MinThrustToWeightForTakeoff = 0.25

  /** Below this it flies, but it will not climb out of anything. */
  private val ComfortableThrustToWeight = 0.4

  /** Watts per kilogram: under this it does not fly, and 100 or so is a gentle trainer. */
  private val MinWattsPerKg = 60.0
  private val TrainerWattsPerKg = 100.0

  /** Grams per square decimetre: past this a model needs a runway and a strong arm. */
  private val HighWingLoading = 120.0

  /** One line per doubt about whether this will fly; empty when nothing looks wrong. */
  def warnings(crrcsim: CRRCSim, calc: AvlCalculation): Seq[String] =
    thrustWarnings(crrcsim) ++ wingLoadingWarnings(crrcsim) ++ stabilityWarnings(calc)

  /**
   * Static thrust against weight, from the motor's power and the propeller's disc.
   *
   * Momentum theory: a disc of area A pushing air with power P can at best produce
   * `T = (2 ρ A P²)^(1/3)`, and a real propeller standing still gets a fraction of that. It is the
   * only estimate available without spinning the propeller up, and it is enough to tell a 3 W
   * installation from a 130 W one.
   */
  private def thrustWarnings(crrcsim: CRRCSim): Seq[String] = {
    val weightKg = crrcsim.getConfig.getMass_inertia.getMass.toDouble
    val propulsion = JsbsimExporter.buildPropulsion(crrcsim)
    if (weightKg <= 0 || propulsion.isEmpty) return Nil

    val prop = propulsion.get
    val watts = prop.motor match {
      case em: JsbsimWriter.ElectricMotor => em.maxPowerWatts
      case piston: JsbsimWriter.PistonEngine => piston.maxPowerWatts
      case _ => 0.0
    }
    val diameter = prop.propDiameterM
    if (watts <= 0 || diameter <= 0) return Nil

    val discArea = math.Pi * diameter * diameter / 4.0
    val idealThrust = math.cbrt(2.0 * AirDensity * discArea * watts * watts)
    val thrust = StaticFigureOfMerit * idealThrust
    val weightN = weightKg * 9.80665
    val ratio = thrust / weightN
    val wattsPerKg = watts / weightKg

    val thrustWarning =
      if (ratio < MinThrustToWeightForTakeoff)
        Seq(f"The propulsion gives about ${thrust}%.2f N of static thrust for ${weightN}%.1f N of " +
          f"aircraft (${ratio}%.2f thrust to weight): it will not accelerate to flying speed on the " +
          f"ground. ${watts}%.0f W through a ${diameter * 100}%.0f cm propeller. A model needs " +
          f"${MinThrustToWeightForTakeoff}%.2f to unstick and ${ComfortableThrustToWeight}%.1f to fly " +
          "comfortably; raise the battery voltage and the motor's current, or fit a larger propeller.")
      else if (ratio < ComfortableThrustToWeight)
        Seq(f"Static thrust is about ${thrust}%.2f N against ${weightN}%.1f N of aircraft " +
          f"(${ratio}%.2f): enough to fly level, little to spare for a climb.")
      else Nil

    val powerWarning =
      if (wattsPerKg < MinWattsPerKg)
        Seq(f"${watts}%.0f W for ${weightKg}%.2f kg is ${wattsPerKg}%.0f W/kg. Model aircraft fly from " +
          f"about ${MinWattsPerKg}%.0f W/kg, and ${TrainerWattsPerKg}%.0f W/kg is a gentle trainer.")
      else Nil

    thrustWarning ++ powerWarning
  }

  /** Weight over wing area, in the units a modeller sizes a wing in. */
  private def wingLoadingWarnings(crrcsim: CRRCSim): Seq[String] = {
    val weightKg = crrcsim.getConfig.getMass_inertia.getMass.toDouble
    // In m², not in whatever the model writes its lengths in: the weight beside it is in kilograms, so a
    // model stated in centimetres would otherwise be told its wing loading was ten thousand times what it is.
    val sref = Option(crrcsim.getAvl).map(_.analysisReferenceAreaSquareMetres.toDouble).getOrElse(0.0)
    if (weightKg <= 0 || sref <= 0) return Nil

    // 1 kg/m² is 10 g/dm².
    val gramsPerSquareDecimetre = weightKg * 1000.0 / (sref * 100.0)
    if (gramsPerSquareDecimetre > HighWingLoading)
      Seq(f"The wing carries ${gramsPerSquareDecimetre}%.0f g/dm² (${weightKg}%.2f kg over " +
        f"${sref}%.3f m²). Past about ${HighWingLoading}%.0f g/dm² a model has to be flown fast and " +
        "landed fast; check the weight and the reference area before blaming the handling.")
    else Nil
  }

  /**
   * What AVL says about the aircraft's own stability, which the CG decides. Sign conventions: `Cma`
   * negative is pitch-stable, `Cnb` positive is directionally stable, `Clb` negative gives roll-righting
   * dihedral effect. A model can be flown slightly unstable, so these are said and not enforced.
   */
  private def stabilityWarnings(calc: AvlCalculation): Seq[String] = {
    if (calc == null || calc.getStabilityDerivatives == null) return Nil
    val std = calc.getStabilityDerivatives

    val pitch =
      if (std.getCma >= 0)
        Seq(f"AVL reports Cma = ${std.getCma}%.3f: the aircraft is not stable in pitch about its centre " +
          "of gravity, so it will diverge rather than settle. Move the CG forward — with weight in the " +
          "nose, or by moving what is already there.")
      else Nil

    val yaw =
      if (std.getCnb <= 0)
        Seq(f"AVL reports Cnb = ${std.getCnb}%.3f: nothing brings the nose back into the wind, so it " +
          "will wander in yaw. A larger fin, or one further back.")
      else Nil

    val roll =
      if (std.getClb > 0)
        Seq(f"AVL reports Clb = ${std.getClb}%.3f: a sideslip rolls it further into the slip instead of " +
          "levelling it. Some dihedral, or less anhedral.")
      else Nil

    pitch ++ yaw ++ roll
  }
}
