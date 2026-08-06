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
import java.util.logging.{Level, Logger}
import scala.collection.JavaConverters._
import JsbsimWriter._

/**
 * Builds a [[JsbsimWriter.Aircraft]] from the editor model plus an AVL calculation and
 * writes the JSBSim files (metric) to a root directory: the aircraft XML under
 * `aircraft/<name>/` and any motor/propeller files under `engine/`.
 *
 * Assumptions (documented for later refinement):
 *  - `MassInertia` is SI (kg, kg·m²); CG and AERORP share the geometry frame (converted
 *    to metres).
 *
 * Nothing here substitutes a value the model does not provide: [[SimulationRequirements]] gates
 * every export, in two stages — the editor's own fields before AVL runs, and AVL's outputs after.
 */
object JsbsimExporter {

  def buildAircraft(name: String, crrcsim: CRRCSim, calc: AvlCalculation): Aircraft = {
    val avl = crrcsim.getAvl
    val geo = avl.getGeometry
    val lu = avl.getLengthUnit
    val uc = new UnitConversor
    val units = avl.units()

    val sref = uc.convertToSquareMeters(geo.getSref, lu).toDouble
    val bref = uc.convertToMeters(geo.getBref, lu).toDouble
    val cref = uc.convertToMeters(geo.getCref, lu).toDouble
    val aeroRp = metres(units, geo.getXref, geo.getYref, geo.getZref)

    val mi = crrcsim.getConfig.getMass_inertia
    val com = crrcsim.getCenterOfMass
    // The centre of gravity is stated in the model's length unit like every other position, and JSBSim wants
    // metres. It used to go out unconverted while the reference point beside it was converted, so a model
    // stated in centimetres exported its geometry in one place and its balance point in another.
    val cg = metres(units, com.getX, com.getY, com.getZ)
    val mass = MassBalance(mi.getMass.toDouble, mi.getI_xx.toDouble, mi.getI_yy.toDouble,
      mi.getI_zz.toDouble, mi.getI_xz.toDouble, cg)

    val aero = buildAero(calc, sref, bref)
    val controls = detectControls(geo)
    val contacts = buildContacts(crrcsim)

    Aircraft(name, Metrics(sref, bref, cref, aeroRp), mass, contacts, controls, aero,
      curves = buildCurves(calc), propulsion = buildPropulsion(crrcsim))
  }

  /** A position from the model, in metres: the one conversion every exported coordinate goes through. */
  private def metres(units: com.abajar.avleditor.ModelUnits, x: Float, y: Float, z: Float): Vec3 =
    Vec3(units.toMetres(x).toDouble, units.toMetres(y).toDouble, units.toMetres(z).toDouble)

  private val logger = Logger.getLogger(JsbsimExporter.getClass.getName)

  /**
   * The lift, drag and pitching-moment curves from the attitude sweep, when it produced one.
   *
   * Fewer than three attitudes is not a curve, and the export then states the constants it always stated —
   * the tangent at the trimmed point. Which of the two was written goes to the log, because the difference
   * is not visible in a flying aircraft until it is far from that point, and by then nobody remembers.
   */
  private def buildCurves(calc: AvlCalculation): Option[AeroCurves] = {
    val points = Option(calc.getAlphaSweep).map(_.asScala.toSeq).getOrElse(Nil).sortBy(_.getAlphaDeg)
    if (points.length < 3) {
      logger.log(Level.WARNING, s"The attitude sweep returned ${points.length} points, too few for a " +
        "curve: the flight model states the derivatives at the trimmed point instead, and is only valid " +
        "near it.")
      None
    } else {
      logger.log(Level.INFO, f"The flight model states measured curves over " +
        f"${points.head.getAlphaDeg}%.1f..${points.last.getAlphaDeg}%.1f degrees of attitude " +
        f"(${points.length} points), replacing the single-point derivatives for lift, drag and pitch.")
      Some(AeroCurves(
        alphaRad = points.map(_.getAlphaRad.toDouble),
        cl = points.map(_.getCl.toDouble),
        cd = points.map(_.getCd.toDouble),
        cm = points.map(_.getCm.toDouble)))
    }
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
    val units = Option(crrcsim.getAvl).map(_.units()).getOrElse(com.abajar.avleditor.ModelUnits.DEFAULTS)
    val wheels = Option(crrcsim.getWheels).map(_.asScala).getOrElse(Nil)
    wheels.zipWithIndex.flatMap { case (w, i) =>
      Option(w.getPos).map { p =>
        val name = Option(w.getName).filter(_.nonEmpty).getOrElse(s"GEAR$i")
        Contact(name.replaceAll("\\s+", "_"), metres(units, p.getX, p.getY, p.getZ))
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
    val units = crrcsim.getAvl.units()
    for {
      battery <- power.getBateries.asScala.headOption
      shaft <- Option(battery.getShafts).map(_.asScala).getOrElse(Nil).headOption
      motor <- buildMotor(shaft)
      thruster <- buildThruster(shaft, units)
    } yield Propulsion(motor, thruster.diameterM, thruster.blades, thruster.at,
      buildFuelTanks(power, units), thruster.curves)
  }

  /**
   * What turns the shaft's power into thrust: a propeller, or a ducted fan.
   *
   * Both come out as JSBSim's `propeller` element, because that element is a machine that absorbs shaft power
   * and produces thrust against advance ratio and nothing more — a free propeller and a shrouded rotor differ
   * in their two coefficient curves, not in their kind. The fan therefore states its own curves, derived from
   * the figures it is sold with, and the propeller takes the generic ones.
   *
   * A shaft carrying both is rejected by [[SimulationRequirements]] rather than resolved silently here, and a
   * fan whose curves cannot be derived throws: reaching here without them means the requirements were skipped,
   * and the ideal curves would give an aircraft about twice the thrust it really has.
   */
  /**
   * Where the thrust is applied and what produces it. The position is the component's own: it used to be
   * hardcoded to the structural origin, so a fan mounted high pushed as if it were on the centreline. For a
   * force along the fuselage axis only the offset across that axis makes a moment, so the height is what this
   * gets right; the station along the fuselage never mattered to the moment and still does not.
   */
  private final case class Thruster(diameterM: Double, blades: Int, at: Vec3,
                                    curves: Option[ThrusterCurves])

  private def buildThruster(shaft: com.abajar.avleditor.crrcsim.Shaft,
                            units: com.abajar.avleditor.ModelUnits): Option[Thruster] = {
    val fan = Option(shaft.getDuctedFans).map(_.asScala).getOrElse(Nil).headOption
    fan match {
      case Some(f) =>
        val curves = ductedFanCurves(shaft, f, units).fold(
          problem => throw new IllegalStateException(problem),
          identity)
        // What it works out to, said plainly: the thrust is derived, and the one constant in it is stated.
        logger.log(Level.INFO, f"Ducted fan: ${f.getInnerDiameterMm}%.1f mm bore at " +
          f"${rpmOf(shaft).getOrElse(0.0)}%.0f rpm on ${wattsOf(shaft).getOrElse(0.0)}%.0f W — " +
          f"${units.fromKilograms((curves.staticThrustN / com.abajar.avleditor.avl.AVL.GRAVITY).toFloat)}%.2f " +
          f"${units.massUnit}%s of static thrust, thrust running out at J = ${curves.k}%.2f. " +
          f"That is ${100 * DuctedFanCurves.FigureOfMerit}%.0f%% of the ideal for that disc " +
          f"(${curves.idealStaticThrustN / com.abajar.avleditor.avl.AVL.GRAVITY}%.2f kg): a stated figure of " +
          "merit for the duct and the motor, not a measurement.")
        // At the exhaust, not at the fan: the momentum forces act where the air crosses the boundary, so a
        // duct that carries the air upwards pushes from up there whatever height the fan itself sits at.
        Some(Thruster(f.getInnerDiameterMm / 1000.0, f.getBlades,
          // The exhaust, within its shaft: the fan states where it sits on the assembly, the exhaust where it
          // sits relative to the fan, and the shaft where the assembly is.
          metres(units, shaft.absoluteX(f.exhaustX()), shaft.absoluteY(f.exhaustY()),
            shaft.absoluteZ(f.exhaustZ())),
          Some(ThrusterCurves(curves.ct, curves.cp))))
      case None =>
        Option(shaft.getPropellers).map(_.asScala).getOrElse(Nil).headOption
          // The diameter is stated in the model's length unit; JSBSim's propeller states it in metres.
          .map(p => Thruster(units.toMetres(p.getD).toDouble, p.getBlades, at(shaft, p.getPos, units), None))
    }
  }

  /**
   * The fan's curves, from its own figures plus the motor's: the revolutions and the power belong to the motor
   * that drives it, so they are read from there rather than stated twice.
   */
  private def at(shaft: com.abajar.avleditor.crrcsim.Shaft, pos: com.abajar.avleditor.crrcsim.Pos,
                 units: com.abajar.avleditor.ModelUnits): Vec3 =
    Option(pos).map(p =>
      metres(units, shaft.absoluteX(p.getX), shaft.absoluteY(p.getY), shaft.absoluteZ(p.getZ)))
      .getOrElse(Vec3(0, 0, 0))

  /** The power the shaft's motor states, from its data rows. */
  private def wattsOf(shaft: com.abajar.avleditor.crrcsim.Shaft): Option[Double] =
    Option(shaft.getEngines).map(_.asScala).getOrElse(Nil).headOption.flatMap(maxPowerWatts)

  /** The revolutions the shaft's motor states, the largest of its data rows. */
  private def rpmOf(shaft: com.abajar.avleditor.crrcsim.Shaft): Option[Double] =
    Option(shaft.getEngines).map(_.asScala).getOrElse(Nil).headOption
      .flatMap(e => Option(e.getData).map(_.asScala).getOrElse(Nil)
        .filter(_.getRpms > 0).map(_.getRpms.toDouble).reduceOption(_ max _))

  def ductedFanCurves(shaft: com.abajar.avleditor.crrcsim.Shaft,
                      fan: com.abajar.avleditor.crrcsim.DuctedFan,
                      units: com.abajar.avleditor.ModelUnits
                     ): Either[String, DuctedFanCurves.Curves] = {
    val engine = Option(shaft.getEngines).map(_.asScala).getOrElse(Nil).headOption
    val rpm = rpmOf(shaft)
    val watts = wattsOf(shaft)
    (rpm, watts) match {
      case (None, _) => Left("The ducted fan is driven by the motor, so the motor needs a data row with " +
        "'Rpms' above zero: it is what sets how much air the fan throws per revolution.")
      case (_, None) => Left("The ducted fan is driven by the motor, so the motor needs a data row with " +
        "'Voltage' and 'Current' above zero: their product is the power the fan turns into thrust.")
      case (Some(r), Some(w)) =>
        DuctedFanCurves.from(DuctedFanCurves.Fan(
          innerDiameterM = fan.getInnerDiameterMm / 1000.0,
          blades = fan.getBlades,
          rpm = r,
          powerWatts = w))
    }
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
  private def buildFuelTanks(power: com.abajar.avleditor.crrcsim.Power,
                             units: com.abajar.avleditor.ModelUnits): Seq[FuelTank] =
    Option(power.getFuelTanks).map(_.asScala).getOrElse(Nil).flatMap { t =>
      // JSBSim states a tank in kilograms and metres; the model states it in its own units.
      Option(t.getPos).map(p =>
        FuelTank(units.toKilograms(t.getCapacity).toDouble, units.toKilograms(t.getContents).toDouble,
          metres(units, p.getX, p.getY, p.getZ)))
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

  /** Span efficiency as AVL reported it, or None when the run did not produce a usable one. */
  def spanEfficiency(calc: AvlCalculation): Option[Double] =
    Option(calc.getConfiguration).flatMap(c => Option(c.getE)).map(_.doubleValue).filter(_ > 0)

  def buildAero(calc: AvlCalculation, sref: Double, bref: Double): AeroDerivatives = {
    val std = calc.getStabilityDerivatives
    val cfg = calc.getConfiguration
    val ep = calc.getElevatorPosition
    val rp = calc.getRudderPosition
    val ap = calc.getAileronPosition
    def at(a: Array[Float], i: Int): Double = if (a != null && i >= 0 && i < a.length) a(i).toDouble else 0.0

    /**
     * A control derivative, converted from AVL's units to JSBSim's.
     *
     * AVL states them per unit of its control *variable*, and that variable is not an angle: the `.avl`
     * CONTROL line carries a gain whose units are, in the editor's own words, "degrees deflection / control
     * variable" — which is why [[com.abajar.avleditor.avl.connectivity.AvlRunner]] multiplies by it to report
     * a trimmed deflection. JSBSim drives the aerodynamics from the deflection in radians
     * (`fcs/elevator-pos-rad` and its siblings), so handing the derivative over unconverted understates the control by
     * 180 / (pi * gain): 2.9 times at the editor's default gain of 20, 57 times at a gain of 1.
     *
     * That is not a rounding error. The eurofighter needs 25 degrees of canard to trim; with surfaces three
     * times weaker than the model states, an aircraft that trims on paper will not trim in the simulator,
     * and nothing in the exported file says why. Measured against AVL directly: one unit of the canard
     * variable moves CL by 0.117, and the same rotation expressed in radians has to move it by the same
     * amount or the two are not describing the same surface.
     */
    def perRadian(a: Array[Float], i: Int): Double = {
      val gain = at(calc.getControlGains, i)
      // A gain of zero is a surface AVL never deflects, so it contributes nothing whatever the units.
      if (gain == 0.0) 0.0 else at(a, i) * 180.0 / (math.Pi * gain)
    }
    // Both of these used to fall back to round numbers (aspect ratio 5, span efficiency 0.85),
    // which silently replaced the aircraft's own aerodynamics. They are derived or nothing:
    // the reference area is validated before the AVL run, the span efficiency after it, so
    // reaching either failure here means a caller skipped [[SimulationRequirements]].
    require(sref > 0, "reference area must be positive to derive the aspect ratio")
    val ar = bref * bref / sref
    val e = spanEfficiency(calc).getOrElse(
      throw new IllegalStateException("AVL produced no span efficiency for this model"))
    new AeroDerivatives(
      cl0 = cfg.getCLtot, cla = std.getCLa, clq = std.getCLq, clde = perRadian(std.getCLd, ep),
      cd0 = cfg.getCDvis, spanEfficiency = e, aspectRatio = ar, cdde = 0.0,
      cm0 = cfg.getCmtot, cma = std.getCma, cmq = std.getCmq, cmde = perRadian(std.getCmd, ep),
      cyb = std.getCYb, cyp = std.getCYp, cyr = std.getCYr,
      cydr = perRadian(std.getCYd, rp), cyda = perRadian(std.getCYd, ap),
      clb = std.getClb, clp = std.getClp, clr = std.getClr,
      cldr = perRadian(std.getCld, rp), clda = perRadian(std.getCld, ap),
      cnb = std.getCnb, cnp = std.getCnp, cnr = std.getCnr,
      cndr = perRadian(std.getCnd, rp), cnda = perRadian(std.getCnd, ap)
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
    // A control with no stated deflection is skipped, not given an invented 25 deg: the channel
    // range is what the pilot's stick maps to, so a guess flies the aircraft wrongly.
    // [[SimulationRequirements]] reports such a control instead.
    for (c <- controls; axis <- axisFor.get(c.getType) if c.getMaxDeflection > 0) {
      maxDeflByAxis(axis) = math.max(maxDeflByAxis.getOrElse(axis, 0.0),
        math.toRadians(c.getMaxDeflection.toDouble))
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
