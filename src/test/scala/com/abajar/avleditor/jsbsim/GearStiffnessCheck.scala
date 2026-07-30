/*
 * Gear stiffness must scale with the aircraft's weight. A fixed spring rate sank light models
 * below the runway (only the fin showed): 100 N/M under 2.8 kg compresses ~9 cm, more than the
 * gear length. The target is the static compression of FlightGear's stock c172p, ~3 cm.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.GearStiffnessCheck"
 */
package com.abajar.avleditor.jsbsim

import JsbsimWriter._

object GearStiffnessCheck {

  private val Gravity = 9.80665
  private val TargetCompressionM = 1.0 / 32.0 // c172p: 1467 lb over 14400 LBS/FT

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** Zero derivatives: this check is about the gear, not the aerodynamics. */
  private def flatAero: AeroDerivatives = new AeroDerivatives(
    0, 0, 0, 0,  0, 0.85, 5.0, 0,  0, 0, 0, 0,
    0, 0, 0, 0, 0,  0, 0, 0, 0, 0,  0, 0, 0, 0, 0)

  /** A tricycle-gear aircraft of a given mass, straight into the writer. */
  private def aircraftWithMass(massKg: Double): Aircraft = Aircraft(
    name = "check",
    metrics = Metrics(0.4, 1.1, 0.37, Vec3(0.68, 0, 0)),
    mass = MassBalance(massKg, 0.05, 0.08, 0.12, 0.0, Vec3(0.68, 0, 0.03)),
    contacts = Seq(
      Contact("NOSE", Vec3(0.2, 0.0, -0.18)),
      Contact("LEFT_MAIN", Vec3(0.8, -0.3, -0.18)),
      Contact("RIGHT_MAIN", Vec3(0.8, 0.3, -0.18))),
    controls = Seq(ControlSurface(ControlAxis.Elevator, math.toRadians(20))),
    aero = flatAero)

  /** All spring_coeff values emitted in the FDM, in N/M. */
  private def springs(xml: String): Seq[Double] =
    """<spring_coeff unit="N/M">([0-9.eE+-]+)</spring_coeff>""".r
      .findAllMatchIn(xml).map(_.group(1).toDouble).toSeq

  private def dampings(xml: String): Seq[Double] =
    """<damping_coeff unit="N/M/SEC">([0-9.eE+-]+)</damping_coeff>""".r
      .findAllMatchIn(xml).map(_.group(1).toDouble).toSeq

  /** Static compression with the weight shared by every contact. */
  private def compression(massKg: Double, springs: Seq[Double]): Double =
    massKg * Gravity / springs.sum

  def main(args: Array[String]): Unit = {
    Seq(2.83, 25.0).foreach { mass =>
      println(f"model of $mass%.2f kg")
      val fdm = JsbsimWriter.generate(aircraftWithMass(mass)).aircraftXml
      val k = springs(fdm)
      val c = dampings(fdm)

      check("one spring per contact", k.length == 3)
      val delta = compression(mass, k)
      println(f"  static compression: ${delta * 100}%.2f cm  (spring ${k.headOption.getOrElse(0.0)}%.1f N/M)")
      check("static compression is about 3 cm", math.abs(delta - TargetCompressionM) < 0.005)
      check("gear is far stiffer than the old fixed 100 N/M", k.forall(_ > 100.0))
      check("damping is a third of the spring rate",
        c.zip(k).forall { case (d, s) => math.abs(d - s / 3.0) < 0.01 })
    }

    println(if (ok) "GEAR_STIFFNESS_OK" else "GEAR_STIFFNESS_FAIL")
    if (!ok) sys.exit(1)
  }
}
