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
 * Generates a JSBSim aircraft definition (`<fdm_config>`) in **metric** units.
 *
 * The aerodynamics are built from AVL stability derivatives (linear model). Where
 * an XFOIL-derived lift table is supplied it overrides the linear CL(alpha) so the
 * model captures CLmax/stall. Propulsion is optional (a glider is valid).
 *
 * The generated model targets JSBSim standalone, FlightGear and PX4 SITL
 * (px4-jsbsim-bridge), which all consume the same format.
 */
object JsbsimWriter {

  // ---- Input data (simulator-agnostic; built from the editor model elsewhere) ----

  final case class Vec3(x: Double, y: Double, z: Double)

  /** Reference geometry (metric). aeroRp = aerodynamic reference point. */
  final case class Metrics(wingAreaM2: Double, wingSpanM: Double, chordM: Double, aeroRp: Vec3)

  /** Mass & inertia (kg, kg·m²) and CG location (m). */
  final case class MassBalance(massKg: Double, ixx: Double, iyy: Double, izz: Double,
                               ixz: Double, cg: Vec3)

  /** A landing-gear / collision contact point (m). */
  final case class Contact(name: String, at: Vec3)

  /** A control surface driven by a pilot command, with its max deflection (rad). */
  final case class ControlSurface(axis: ControlAxis.Value, maxDeflectionRad: Double)

  /**
   * Linear aerodynamic model from AVL. Angles in rad, rates non-dimensionalised by
   * JSBSim's bi2vel/ci2vel. Control derivatives are per-radian of surface deflection.
   */
  // Regular class (not case): Scala 2.10 caps case classes at 22 params.
  final class AeroDerivatives(
    val cl0: Double, val cla: Double, val clq: Double, val clde: Double,
    val cd0: Double, val spanEfficiency: Double, val aspectRatio: Double, val cdde: Double,
    val cm0: Double, val cma: Double, val cmq: Double, val cmde: Double,
    val cyb: Double, val cyp: Double, val cyr: Double, val cydr: Double, val cyda: Double,
    val clb: Double, val clp: Double, val clr: Double, val cldr: Double, val clda: Double,
    val cnb: Double, val cnp: Double, val cnr: Double, val cndr: Double, val cnda: Double
  )

  /**
   * The aircraft measured across a range of attitudes, controls at neutral: what AVL answered at each one,
   * on a single grid so the three curves cannot disagree about which attitude a row belongs to.
   *
   * This is what a flight model states instead of one measurement plus a rate. A rate is the tangent at the
   * point it was measured, and JSBSim continues it to any attitude it likes; a curve is the aircraft over
   * the range it will actually be flown in. JSBSim holds the end value beyond a table's last row rather
   * than extrapolating, so lift also stops growing without bound at absurd attitudes — which is not a
   * stall, only the absence of a fiction. A real stall is viscous and AVL cannot see one.
   *
   * Controls neutral matters: the control terms are separate products in the same axis, so a curve measured
   * with the elevator trimmed would carry that trim and be counted twice.
   */
  final case class AeroCurves(alphaRad: Seq[Double], cl: Seq[Double], cd: Seq[Double], cm: Seq[Double]) {

    /** Fewer than three points is a line, not a curve, and a line is what the constants already say. */
    def isCurve: Boolean =
      alphaRad.length >= 3 && cl.length == alphaRad.length &&
        cd.length == alphaRad.length && cm.length == alphaRad.length

    def liftRows: Seq[(Double, Double)] = alphaRad.zip(cl)
    def dragRows: Seq[(Double, Double)] = alphaRad.zip(cd)
    def pitchRows: Seq[(Double, Double)] = alphaRad.zip(cm)
  }

  /**
   * What drives the propeller. Kept as a sum type so the two are never conflated: an electric
   * motor is rated by power alone, while a combustion engine needs its displacement, rev range
   * and fuel consumption, and burns fuel from a tank.
   *
   * Do not emit a `brushless_dc_motor` for the electric case: that element exists in newer JSBSim
   * only, and the JSBSim inside FlightGear 2020.3 rejects the whole aircraft with "Unknown engine
   * type" and aborts on load.
   */
  sealed trait Motor

  final case class ElectricMotor(maxPowerWatts: Double) extends Motor

  /** Combustion engine, metric; the writer converts to the units JSBSim's piston model wants. */
  final case class PistonEngine(displacementCm3: Double, maxPowerWatts: Double, idleRpm: Double,
                                maxRpm: Double, cycles: Int, fuelConsumptionGPerKWh: Double) extends Motor

  /** A fuel tank. Its contents are mass that leaves the aircraft as it burns. */
  final case class FuelTank(capacityKg: Double, contentsKg: Double, at: Vec3)

  final case class Propulsion(motor: Motor, propDiameterM: Double, numBlades: Int, at: Vec3,
                              tanks: Seq[FuelTank] = Nil)

  /** The generated aircraft plus the auxiliary engine/thruster files it references. */
  final case class GeneratedModel(name: String, aircraftXml: String, engineFiles: Seq[(String, String)])

  final case class Aircraft(
    name: String,
    metrics: Metrics,
    mass: MassBalance,
    contacts: Seq[Contact],
    controls: Seq[ControlSurface],
    aero: AeroDerivatives,
    curves: Option[AeroCurves] = None,
    propulsion: Option[Propulsion] = None
  )

  object ControlAxis extends Enumeration {
    val Elevator, Aileron, Rudder = Value
  }

  // ---- Rendering ----

  private def engineName(ac: Aircraft) = ac.name + "_motor"
  private def propName(ac: Aircraft) = ac.name + "_prop"

  /** Full export: the aircraft XML plus any engine/thruster files it references. */
  def generate(ac: Aircraft): GeneratedModel = {
    val sb = new StringBuilder
    sb.append("""<?xml version="1.0"?>""").append("\n")
    sb.append(s"""<fdm_config name="${xml(ac.name)}" version="2.0" release="ALPHA">""").append("\n")
    sb.append(fileHeader(ac))
    sb.append(metrics(ac.metrics))
    sb.append(massBalance(ac.mass))
    sb.append(groundReactions(ac.contacts, ac.mass.massKg))
    sb.append(propulsion(ac))
    sb.append(flightControl(ac.controls))
    sb.append(aerodynamics(ac))
    sb.append("</fdm_config>\n")

    val engineFiles = ac.propulsion match {
      case None => Seq.empty
      case Some(pr) => Seq(
        engineName(ac) + ".xml" -> engineFile(engineName(ac), pr.motor),
        propName(ac) + ".xml" -> propellerFile(propName(ac), pr.propDiameterM, pr.numBlades)
      )
    }
    GeneratedModel(ac.name, sb.toString, engineFiles)
  }

  /** Convenience: the aircraft XML only (no auxiliary files). */
  def write(ac: Aircraft): String = generate(ac).aircraftXml

  private def f(v: Double): String = {
    if (v == 0.0) "0" else new java.math.BigDecimal(v).round(new java.math.MathContext(8)).toPlainString
  }

  private def xml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  private def fileHeader(ac: Aircraft): String =
    s"""  <fileheader>
    |    <author>AVL Editor</author>
    |    <description>Auto-generated from AVL geometry + stability derivatives</description>
    |  </fileheader>
    |""".stripMargin

  private def metrics(m: Metrics): String =
    s"""  <metrics>
    |    <wingarea unit="M2">${f(m.wingAreaM2)}</wingarea>
    |    <wingspan unit="M">${f(m.wingSpanM)}</wingspan>
    |    <chord unit="M">${f(m.chordM)}</chord>
    |    <location name="AERORP" unit="M"><x>${f(m.aeroRp.x)}</x><y>${f(m.aeroRp.y)}</y><z>${f(m.aeroRp.z)}</z></location>
    |  </metrics>
    |""".stripMargin

  private def massBalance(m: MassBalance): String =
    s"""  <mass_balance>
    |    <ixx unit="KG*M2">${f(m.ixx)}</ixx>
    |    <iyy unit="KG*M2">${f(m.iyy)}</iyy>
    |    <izz unit="KG*M2">${f(m.izz)}</izz>
    |    <ixz unit="KG*M2">${f(m.ixz)}</ixz>
    |    <emptywt unit="KG">${f(m.massKg)}</emptywt>
    |    <location name="CG" unit="M"><x>${f(m.cg.x)}</x><y>${f(m.cg.y)}</y><z>${f(m.cg.z)}</z></location>
    |  </mass_balance>
    |""".stripMargin

  /**
   * Gear stiffness scaled to the aircraft's weight, so the struts compress by a fixed small
   * fraction of their travel whatever the model weighs. A fixed spring rate does not survive
   * scaling: 100 N/M under a 2.8 kg model gives ~9 cm of static compression, more than the gear
   * length, so the airframe ends up under the runway with only the fin showing.
   *
   * The ratios come from FlightGear's stock c172p, which sits correctly on the ground:
   * 1467 lb over 14400 LBS/FT of total spring is ~32 times its weight per metre, i.e. ~3 cm of
   * static compression, with damping at a third of the spring rate.
   */
  private val SpringPerWeight = 32.0
  private val DampingFraction = 1.0 / 3.0
  private val Gravity = 9.80665

  private def groundReactions(contacts: Seq[Contact], massKg: Double): String = {
    val weight = math.max(massKg, 0.001) * Gravity
    val spring = math.max(SpringPerWeight * weight / math.max(contacts.length, 1), 1.0)
    val damping = spring * DampingFraction
    val body =
      if (contacts.isEmpty) ""
      else contacts.map { c =>
        s"""    <contact type="BOGEY" name="${xml(c.name)}">
      |      <location unit="M"><x>${f(c.at.x)}</x><y>${f(c.at.y)}</y><z>${f(c.at.z)}</z></location>
      |      <static_friction>0.8</static_friction>
      |      <dynamic_friction>0.5</dynamic_friction>
      |      <rolling_friction>0.02</rolling_friction>
      |      <spring_coeff unit="N/M">${f(spring)}</spring_coeff>
      |      <damping_coeff unit="N/M/SEC">${f(damping)}</damping_coeff>
      |    </contact>
      |""".stripMargin
      }.mkString
    s"  <ground_reactions>\n$body  </ground_reactions>\n"
  }

  private def propulsion(ac: Aircraft): String = ac.propulsion match {
    case None => "  <propulsion/>\n"
    case Some(pr) =>
      s"""  <propulsion>
      |    <engine file="${engineName(ac)}">
      |${feeds(pr.tanks)}      <thruster file="${propName(ac)}">
      |        <location unit="M"><x>${f(pr.at.x)}</x><y>${f(pr.at.y)}</y><z>${f(pr.at.z)}</z></location>
      |        <orient unit="DEG"><roll>0</roll><pitch>0</pitch><yaw>0</yaw></orient>
      |      </thruster>
      |    </engine>
      |${pr.tanks.map(fuelTank).mkString}  </propulsion>
      |""".stripMargin
  }

  private def engineFile(name: String, motor: Motor): String = motor match {
    case ElectricMotor(watts) =>
      s"""<?xml version="1.0"?>
      |<electric_engine name="${xml(name)}">
      |  <power unit="WATTS">${f(watts)}</power>
      |</electric_engine>
      |""".stripMargin
    case pe: PistonEngine => pistonEngineFile(name, pe)
  }

  private val WattsPerHp = 745.699872
  private val Cm3PerIn3 = 16.387064
  /** JSBSim wants brake specific fuel consumption in lb/(hp*h); the model states g/kWh. */
  private val GPerKWhPerLbPerHpH = 608.277

  private def pistonEngineFile(name: String, pe: PistonEngine): String =
    s"""<?xml version="1.0"?>
    |<piston_engine name="${xml(name)}">
    |  <displacement unit="IN3">${f(pe.displacementCm3 / Cm3PerIn3)}</displacement>
    |  <maxhp>${f(pe.maxPowerWatts / WattsPerHp)}</maxhp>
    |  <cycles>${pe.cycles}</cycles>
    |  <idlerpm>${f(pe.idleRpm)}</idlerpm>
    |  <maxrpm>${f(pe.maxRpm)}</maxrpm>
    |  <bsfc>${f(pe.fuelConsumptionGPerKWh / GPerKWhPerLbPerHpH)}</bsfc>
    |</piston_engine>
    |""".stripMargin

  /**
   * The tanks an engine draws from, by index. Without a `<feed>` an engine has no fuel source: it
   * cranks under the starter and produces a little thrust, but never runs and burns nothing, which
   * reads as a mysterious ignition problem rather than a missing element.
   */
  private def feeds(tanks: Seq[FuelTank]): String =
    tanks.indices.map(i => s"      <feed>$i</feed>\n").mkString

  private def fuelTank(tank: FuelTank): String =
    s"""    <tank type="FUEL">
    |      <location unit="M"><x>${f(tank.at.x)}</x><y>${f(tank.at.y)}</y><z>${f(tank.at.z)}</z></location>
    |      <capacity unit="KG">${f(tank.capacityKg)}</capacity>
    |      <contents unit="KG">${f(tank.contentsKg)}</contents>
    |    </tank>
    |""".stripMargin

  private def propellerFile(name: String, diameterM: Double, numBlades: Int): String = {
    val ixx = 1.06e-3 * diameterM * diameterM // scaled from the DJI 9450 reference prop
    s"""<?xml version="1.0"?>
    |<propeller name="${xml(name)}" version="1.1">
    |  <ixx unit="KG*M2">${f(ixx)}</ixx>
    |  <diameter unit="M">${f(diameterM)}</diameter>
    |  <numblades>$numBlades</numblades>
    |  <constspeed>0</constspeed>
    |  <table name="C_THRUST" type="internal">
    |    <tableData>
    |$GENERIC_CT
    |    </tableData>
    |  </table>
    |  <table name="C_POWER" type="internal">
    |    <tableData>
    |$GENERIC_CP
    |    </tableData>
    |  </table>
    |</propeller>
    |""".stripMargin
  }

  // Generic small fixed-pitch propeller coefficients vs advance ratio J
  // (from the APC 9x4.5e / JSBSim DJI_9450 reference; normalised by J so reusable).
  private val GENERIC_CT =
    """      0.0000 0.1288
      |      0.0730 0.1230
      |      0.1470 0.1153
      |      0.2287 0.1053
      |      0.3022 0.0932
      |      0.3757 0.0794
      |      0.4496 0.0644
      |      0.5296 0.0483
      |      0.6039 0.0310
      |      0.6774 0.0128
      |      0.7291 -0.0001""".stripMargin

  private val GENERIC_CP =
    """      0.0000 0.0666
      |      0.0730 0.0611
      |      0.1470 0.0567
      |      0.2287 0.0531
      |      0.3022 0.0498
      |      0.3757 0.0456
      |      0.4496 0.0402
      |      0.5296 0.0332
      |      0.6039 0.0243
      |      0.6774 0.0137
      |      0.7291 0.0061""".stripMargin

  private def flightControl(controls: Seq[ControlSurface]): String = {
    val channels = controls.map { c =>
      val (cmd, pos) = c.axis match {
        case ControlAxis.Elevator => ("fcs/elevator-cmd-norm", "fcs/elevator-pos-rad")
        case ControlAxis.Aileron  => ("fcs/aileron-cmd-norm", "fcs/aileron-pos-rad")
        case ControlAxis.Rudder   => ("fcs/rudder-cmd-norm", "fcs/rudder-pos-rad")
      }
      s"""    <channel name="${c.axis}">
      |      <aerosurface_scale name="${pos}-scale">
      |        <input>${cmd}</input>
      |        <range><min>${f(-c.maxDeflectionRad)}</min><max>${f(c.maxDeflectionRad)}</max></range>
      |        <output>${pos}</output>
      |      </aerosurface_scale>
      |    </channel>
      |""".stripMargin
    }.mkString
    s"""  <flight_control name="FCS">
    |$channels  </flight_control>
    |""".stripMargin
  }

  // -- Aerodynamics: build force/moment axes from the linear derivatives --

  private def term(name: String, factors: String): String =
    s"""      <function name="aero/$name">
    |        <product>
    |$factors        </product>
    |      </function>
    |""".stripMargin

  /**
   * A coefficient stated as a curve in attitude: the factors, then a lookup table on `aero/alpha-rad`.
   * The grid is in radians because that is the property it is looked up against, and the rows are written
   * in the order given, which the caller keeps ascending — JSBSim needs a monotonic independent variable.
   */
  private def tableTerm(name: String, factors: String, rows: Seq[(Double, Double)]): String = {
    val data = rows.map { case (alphaRad, value) => s"            ${f(alphaRad)} ${f(value)}" }.mkString("\n")
    s"""      <function name="aero/$name">
    |        <product>
    |$factors          <table>
    |            <independentVar lookup="row">aero/alpha-rad</independentVar>
    |            <tableData>
    |$data
    |            </tableData>
    |          </table>
    |        </product>
    |      </function>
    |""".stripMargin
  }

  private def p(prop: String): String = s"          <property>$prop</property>\n"
  private def v(value: Double): String = s"          <value>${f(value)}</value>\n"

  private def aerodynamics(ac: Aircraft): String = {
    val a = ac.aero
    val qA = "aero/qbar-area"

    // LIFT: the measured curve when there is one, or the tangent at the single point when there is not.
    // A curve replaces both constants; leaving them in would count the lift twice.
    val curves = ac.curves.filter(_.isCurve)
    val liftFns = curves match {
      case Some(c) => tableTerm("force/lift", p(qA), c.liftRows)
      case None =>
        term("force/lift_0", p(qA) + v(a.cl0)) +
        term("force/lift_alpha", p(qA) + v(a.cla) + p("aero/alpha-rad"))
    }
    val liftRates =
      term("force/lift_q", p(qA) + v(a.clq) + p("aero/ci2vel") + p("velocities/q-aero-rad_sec")) +
      term("force/lift_de", p(qA) + v(a.clde) + p("fcs/elevator-pos-rad"))

    // DRAG. With a curve this is AVL's total drag at each attitude — viscous and induced together — and it
    // replaces both the parasite constant and the induced term. Driving drag by attitude rather than by the
    // square of the computed lift is not a detail: cl-squared follows the lift, so the day the lift curve
    // bends over at a stall, a cl-squared drag would fall with it and a stalled aircraft would have less
    // drag than in normal flight.
    val k = 1.0 / (math.Pi * math.max(a.aspectRatio, 1e-6) * math.max(a.spanEfficiency, 1e-6))
    val dragCore = curves match {
      case Some(c) => tableTerm("force/drag", p(qA), c.dragRows)
      case None =>
        term("force/drag_0", p(qA) + v(a.cd0)) +
        s"""      <function name="aero/force/drag_induced">
        |        <product>
        |          <property>$qA</property>
        |          <value>${f(k)}</value>
        |          <property>aero/cl-squared</property>
        |        </product>
        |      </function>
        |""".stripMargin
    }
    val drag = dragCore + term("force/drag_de", p(qA) + v(a.cdde) + p("fcs/elevator-pos-rad"))

    // SIDE
    val side =
      term("force/side_beta", p(qA) + v(a.cyb) + p("aero/beta-rad")) +
      term("force/side_p", p(qA) + v(a.cyp) + p("aero/bi2vel") + p("velocities/p-aero-rad_sec")) +
      term("force/side_r", p(qA) + v(a.cyr) + p("aero/bi2vel") + p("velocities/r-aero-rad_sec")) +
      term("force/side_dr", p(qA) + v(a.cydr) + p("fcs/rudder-pos-rad")) +
      term("force/side_da", p(qA) + v(a.cyda) + p("fcs/aileron-pos-rad"))

    val span = "metrics/bw-ft"
    val chord = "metrics/cbarw-ft"

    // ROLL (about body x): qbar-area * span * Cl
    val roll =
      term("moment/roll_beta", p(qA) + p(span) + v(a.clb) + p("aero/beta-rad")) +
      term("moment/roll_p", p(qA) + p(span) + v(a.clp) + p("aero/bi2vel") + p("velocities/p-aero-rad_sec")) +
      term("moment/roll_r", p(qA) + p(span) + v(a.clr) + p("aero/bi2vel") + p("velocities/r-aero-rad_sec")) +
      term("moment/roll_dr", p(qA) + p(span) + v(a.cldr) + p("fcs/rudder-pos-rad")) +
      term("moment/roll_da", p(qA) + p(span) + v(a.clda) + p("fcs/aileron-pos-rad"))

    // PITCH (about body y): qbar-area * chord * Cm
    val pitchCore = curves match {
      case Some(c) => tableTerm("moment/pitch", p(qA) + p(chord), c.pitchRows)
      case None =>
        term("moment/pitch_0", p(qA) + p(chord) + v(a.cm0)) +
        term("moment/pitch_alpha", p(qA) + p(chord) + v(a.cma) + p("aero/alpha-rad"))
    }
    val pitch = pitchCore +
      term("moment/pitch_q", p(qA) + p(chord) + v(a.cmq) + p("aero/ci2vel") + p("velocities/q-aero-rad_sec")) +
      term("moment/pitch_de", p(qA) + p(chord) + v(a.cmde) + p("fcs/elevator-pos-rad"))

    // YAW (about body z): qbar-area * span * Cn
    val yaw =
      term("moment/yaw_beta", p(qA) + p(span) + v(a.cnb) + p("aero/beta-rad")) +
      term("moment/yaw_p", p(qA) + p(span) + v(a.cnp) + p("aero/bi2vel") + p("velocities/p-aero-rad_sec")) +
      term("moment/yaw_r", p(qA) + p(span) + v(a.cnr) + p("aero/bi2vel") + p("velocities/r-aero-rad_sec")) +
      term("moment/yaw_dr", p(qA) + p(span) + v(a.cndr) + p("fcs/rudder-pos-rad")) +
      term("moment/yaw_da", p(qA) + p(span) + v(a.cnda) + p("fcs/aileron-pos-rad"))

    s"""  <aerodynamics>
    |    <axis name="LIFT">
    |$liftFns$liftRates    </axis>
    |    <axis name="DRAG">
    |$drag    </axis>
    |    <axis name="SIDE">
    |$side    </axis>
    |    <axis name="ROLL">
    |$roll    </axis>
    |    <axis name="PITCH">
    |$pitch    </axis>
    |    <axis name="YAW">
    |$yaw    </axis>
    |  </aerodynamics>
    |""".stripMargin
  }
}
