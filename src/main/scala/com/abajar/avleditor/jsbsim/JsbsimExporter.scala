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
 *  - Propulsion is not mapped yet from the electric power model; a glider is exported.
 *    JsbsimWriter already supports BLDC propulsion once that mapping is wired.
 */
object JsbsimExporter {

  private val DefaultElevatorDeflRad = math.toRadians(25)
  private val DefaultAileronDeflRad = math.toRadians(20)
  private val DefaultRudderDeflRad = math.toRadians(25)

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
    // A single belly contact keeps the model on the ground; real gear can replace it.
    val contacts = Seq(Contact("BELLY", Vec3(cg.x, cg.y, cg.z - 0.1)))

    Aircraft(name, Metrics(sref, bref, cref, aeroRp), mass, contacts, controls, aero,
      propulsion = buildPropulsion(crrcsim))
  }

  /**
   * Map the model's electric propulsion (Power → Battery → Shaft → Propeller/Engine) to a
   * JSBSim brushless motor + propeller. Uses the real battery voltage, propeller diameter and
   * blade count; derives Kv/R/I0 from the motor's data curve when present, else sensible
   * defaults. Returns None when the model has no propulsion (→ glider).
   */
  def buildPropulsion(crrcsim: CRRCSim): Option[Propulsion] = {
    val power = crrcsim.getConfig.getPower
    if (power == null) return None
    for {
      battery <- power.getBateries.asScala.headOption
      shaft <- Option(battery.getShafts).map(_.asScala).getOrElse(Nil).headOption
      prop <- Option(shaft.getPropellers).map(_.asScala).getOrElse(Nil).headOption
    } yield {
      val volts = if (battery.getU_0 > 0) battery.getU_0.toDouble else 11.1
      val diameter = if (prop.getD > 0) prop.getD.toDouble else 0.25
      val blades = if (prop.getN_fold >= 2) prop.getN_fold else 2
      val engine = Option(shaft.getEngines).map(_.asScala).getOrElse(Nil).headOption
      val (kv, r, i0) = engine.map(deriveMotorParams).getOrElse((960.0, 0.117, 0.45))
      Propulsion(kv, volts, r, i0, diameter, blades, Vec3(0, 0, 0))
    }
  }

  /**
   * Estimate brushless-motor (Kv, coil resistance, no-load current) from the CRRCsim motor
   * data curve (points of terminal voltage U_K, current I_M, rpm). Kv ≈ rpm/back-EMF; the
   * no-load current is the lowest-current point. Falls back to DJI-E305-like defaults.
   */
  private def deriveMotorParams(engine: com.abajar.avleditor.crrcsim.Engine): (Double, Double, Double) = {
    val data = Option(engine.getData).map(_.asScala.toSeq).getOrElse(Nil)
      .filter(d => d.getU_K > 0 && d.getRpms > 0)
    if (data.isEmpty) return (960.0, 0.117, 0.45)
    val i0 = data.map(_.getI_M.toDouble).min.max(0.1)
    // Kv from the most-unloaded (highest-rpm) point; back-EMF ≈ U_K (IR drop ignored).
    val fastest = data.maxBy(_.getRpms)
    val kv = (fastest.getRpms.toDouble / fastest.getU_K.toDouble).max(1.0)
    (kv, 0.1, i0)
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

  /** Detect the aileron/elevator/rudder surfaces present, one FCS channel each. */
  private def detectControls(geo: com.abajar.avleditor.avl.AVLGeometry): Seq[ControlSurface] = {
    val names = geo.getSurfaces.asScala
      .flatMap(_.getSections.asScala)
      .flatMap(_.getControls.asScala)
      .map(c => Option(c.getName).getOrElse("").toLowerCase)
      .toSet
    def has(keys: String*) = keys.exists(k => names.exists(_.contains(k)))
    var out = List.empty[ControlSurface]
    if (has("elev", "ele", "pitch")) out ::= ControlSurface(ControlAxis.Elevator, DefaultElevatorDeflRad)
    if (has("ail", "flap", "roll")) out ::= ControlSurface(ControlAxis.Aileron, DefaultAileronDeflRad)
    if (has("rud", "yaw")) out ::= ControlSurface(ControlAxis.Rudder, DefaultRudderDeflRad)
    out
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
