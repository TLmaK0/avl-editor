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
import com.abajar.avleditor.UnitConversor
import java.io.{File, PrintWriter}
import scala.collection.JavaConverters._
import JsbsimWriter._

/**
 * Builds a [[JsbsimWriter.Aircraft]] from the editor model plus an AVL calculation and
 * writes the JSBSim files (metric) to a root directory: the aircraft XML under
 * `aircraft/<name>/` and any motor/propeller files under `engine/`.
 *
 * Assumptions (documented for later refinement):
 *  - `MassInertia` is SI (kg, kg·m²); CG and AERORP share the geometry frame (converted
 *    to metres). Control max deflections default per surface (AVL carries no limit).
 */
object JsbsimExporter {

  def buildAircraft(name: String, crrcsim: CRRCSim, calc: AvlCalculation): Aircraft = {
    val avl = crrcsim.getAvl
    val geo = avl.getGeometry
    val lu = avl.getLengthUnit
    val uc = new UnitConversor

    val sref = uc.convertToSquareMeters(geo.getSref, lu).toDouble
    val bref = uc.convertToMeters(geo.getBref, lu).toDouble
    val cref = uc.convertToMeters(geo.getCref, lu).toDouble
    val aeroRp = Vec3(
      uc.convertToMeters(geo.getXref, lu), uc.convertToMeters(geo.getYref, lu), uc.convertToMeters(geo.getZref, lu))

    val mi = crrcsim.getConfig.getMass_inertia
    val com = crrcsim.getCenterOfMass
    val cg = Vec3(com.getX.toDouble, com.getY.toDouble, com.getZ.toDouble)
    val mass = MassBalance(mi.getMass.toDouble, mi.getI_xx.toDouble, mi.getI_yy.toDouble,
      mi.getI_zz.toDouble, mi.getI_xz.toDouble, cg)

    val aero = buildAero(calc, sref, bref)
    val controls = detectControls(geo)
    val contacts = buildContacts(crrcsim)

    Aircraft(name, Metrics(sref, bref, cref, aeroRp), mass, contacts, controls, aero,
      propulsion = buildPropulsion(crrcsim))
  }

  /**
   * Landing gear / contact points from the model's collision points (wheels). Each wheel's
   * position (metric, structural frame) becomes a JSBSim BOGEY contact.
   *
   * A model without usable collision points yields none: there is deliberately no invented
   * belly contact, because a single fabricated point cannot support the aircraft in pitch and
   * roll — JSBSim fails to trim and the aircraft sinks through the runway while the export
   * still reports success. [[SimulationRequirements]] rejects such a model up front.
   */
  def buildContacts(crrcsim: CRRCSim): Seq[Contact] = {
    val wheels = Option(crrcsim.getWheels).map(_.asScala).getOrElse(Nil)
    wheels.zipWithIndex.flatMap { case (w, i) =>
      Option(w.getPos).map { p =>
        val name = Option(w.getName).filter(_.nonEmpty).getOrElse(s"GEAR$i")
        Contact(name.replaceAll("\\s+", "_"), Vec3(p.getX.toDouble, p.getY.toDouble, p.getZ.toDouble))
      }
    }
  }

  /**
   * Map the model's electric propulsion (Power → Battery → Shaft → Propeller/Engine) to a
   * JSBSim brushless motor + propeller, using the model's own battery voltage, propeller
   * diameter, blade count and motor data curve.
   *
   * Nothing is substituted for a missing value: an invented voltage or propeller produces
   * plausible-looking but wrong thrust. [[SimulationRequirements]] rejects such a model before
   * the export runs, so None here means the caller skipped that validation.
   */
  def buildPropulsion(crrcsim: CRRCSim): Option[Propulsion] = {
    val power = crrcsim.getConfig.getPower
    if (power == null) return None
    for {
      battery <- power.getBateries.asScala.headOption
      shaft <- Option(battery.getShafts).map(_.asScala).getOrElse(Nil).headOption
      prop <- Option(shaft.getPropellers).map(_.asScala).getOrElse(Nil).headOption
      motor <- buildMotor(shaft)
    } yield Propulsion(motor, prop.getD.toDouble, prop.getBlades, Vec3(0, 0, 0),
      buildFuelTanks(power))
  }

  /**
   * The shaft's motor: a combustion engine when present, else the electric one. A shaft carrying
   * both is rejected by [[SimulationRequirements]] rather than silently resolved here.
   */
  private def buildMotor(shaft: com.abajar.avleditor.crrcsim.Shaft): Option[Motor] = {
    val piston = Option(shaft.getCombustionEngines).map(_.asScala).getOrElse(Nil).headOption
      .map(e => PistonEngine(e.getDisplacement.toDouble, e.getMaxPower.toDouble,
        e.getIdleRpm.toDouble, e.getMaxRpm.toDouble, e.getCycles, e.getFuelConsumption.toDouble))
    piston.orElse {
      for {
        engine <- Option(shaft.getEngines).map(_.asScala).getOrElse(Nil).headOption
        watts <- maxPowerWatts(engine)
      } yield ElectricMotor(watts)
    }
  }

  /** Fuel tanks, with the mass the model states; a combustion engine burns from them. */
  private def buildFuelTanks(power: com.abajar.avleditor.crrcsim.Power): Seq[FuelTank] =
    Option(power.getFuelTanks).map(_.asScala).getOrElse(Nil).flatMap { t =>
      Option(t.getPos).map(p =>
        FuelTank(t.getCapacity.toDouble, t.getContents.toDouble,
          Vec3(p.getX.toDouble, p.getY.toDouble, p.getZ.toDouble)))
    }.toSeq

  /**
   * Motor power from the CRRCsim data curve: the largest electrical input power over its points
   * (terminal voltage x current). Nothing is assumed about efficiency — JSBSim's electric engine
   * is rated by shaft power, and using the electrical input overstates it slightly, which is
   * stated here rather than hidden behind a fudge factor.
   *
   * None when no point carries both a voltage and an rpm; the caller must not invent one, and
   * [[SimulationRequirements]] rejects such a model up front.
   */
  private def maxPowerWatts(engine: com.abajar.avleditor.crrcsim.Engine): Option[Double] = {
    val data = Option(engine.getData).map(_.asScala.toSeq).getOrElse(Nil)
      .filter(d => d.getU_K > 0 && d.getRpms > 0)
    val watts = data.map(d => d.getU_K.toDouble * d.getI_M.toDouble).filter(_ > 0)
    if (watts.isEmpty) None else Some(watts.max)
  }

  private def buildAero(calc: AvlCalculation, sref: Double, bref: Double): AeroDerivatives = {
    val std = calc.getStabilityDerivatives
    val cfg = calc.getConfiguration
    val ep = calc.getElevatorPosition
    val rp = calc.getRudderPosition
    val ap = calc.getAileronPosition
    def at(a: Array[Float], i: Int): Double = if (a != null && i >= 0 && i < a.length) a(i).toDouble else 0.0
    val ar = if (sref > 0) bref * bref / sref else 5.0
    val e = Option(cfg.getE).map(_.doubleValue).filter(_ > 0).getOrElse(0.85)
    new AeroDerivatives(
      cl0 = cfg.getCLtot, cla = std.getCLa, clq = std.getCLq, clde = at(std.getCLd, ep),
      cd0 = cfg.getCDvis, spanEfficiency = e, aspectRatio = ar, cdde = 0.0,
      cm0 = cfg.getCmtot, cma = std.getCma, cmq = std.getCmq, cmde = at(std.getCmd, ep),
      cyb = std.getCYb, cyp = std.getCYp, cyr = std.getCYr, cydr = at(std.getCYd, rp), cyda = at(std.getCYd, ap),
      clb = std.getClb, clp = std.getClp, clr = std.getClr, cldr = at(std.getCld, rp), clda = at(std.getCld, ap),
      cnb = std.getCnb, cnp = std.getCnp, cnr = std.getCnr, cndr = at(std.getCnd, rp), cnda = at(std.getCnd, ap)
    )
  }

  /**
   * One FCS channel per control axis present. The control's `type` selects the axis
   * (0 = Aileron, 1 = Elevator, 2 = Rudder) and its editable per-control `maxDeflection`
   * (degrees) sets the channel range; when several controls share an axis the largest wins.
   */
  def detectControls(geo: com.abajar.avleditor.avl.AVLGeometry): Seq[ControlSurface] = {
    val controls = geo.getSurfaces.asScala
      .flatMap(_.getSections.asScala)
      .flatMap(_.getControls.asScala)
    val axisFor = Map(0 -> ControlAxis.Aileron, 1 -> ControlAxis.Elevator, 2 -> ControlAxis.Rudder)
    val maxDeflByAxis = scala.collection.mutable.Map.empty[ControlAxis.Value, Double]
    for (c <- controls; axis <- axisFor.get(c.getType)) {
      val defl = math.toRadians(if (c.getMaxDeflection > 0) c.getMaxDeflection.toDouble else 25.0)
      maxDeflByAxis(axis) = math.max(maxDeflByAxis.getOrElse(axis, 0.0), defl)
    }
    // Stable order: Elevator, Aileron, Rudder.
    Seq(ControlAxis.Elevator, ControlAxis.Aileron, ControlAxis.Rudder)
      .flatMap(a => maxDeflByAxis.get(a).map(d => ControlSurface(a, d)))
  }

  /** Write the aircraft + engine files under `rootDir` in JSBSim's expected layout. */
  def export(rootDir: File, name: String, crrcsim: CRRCSim, calc: AvlCalculation): Unit = {
    val model = generate(buildAircraft(name, crrcsim, calc))
    writeFile(new File(rootDir, s"aircraft/$name/$name.xml"), model.aircraftXml)
    model.engineFiles.foreach { case (fn, content) => writeFile(new File(rootDir, s"engine/$fn"), content) }
  }

  private def writeFile(f: File, content: String): Unit = {
    Option(f.getParentFile).foreach(_.mkdirs())
    val pw = new PrintWriter(f)
    try pw.write(content) finally pw.close()
  }
}
