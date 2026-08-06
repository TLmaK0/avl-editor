/*
 * Generates a sample JSBSim aircraft from representative small-aircraft stability
 * derivatives and writes it to args(0), for validation against JSBSim standalone.
 * Run: sbt "test:runMain com.abajar.avleditor.jsbsim.JsbsimWriterCheck /path/out.xml"
 */
package com.abajar.avleditor.jsbsim

import JsbsimWriter._

object JsbsimWriterCheck {
  def sampleAircraft: Aircraft = Aircraft(
    name = "gen",
    metrics = Metrics(wingAreaM2 = 0.5, wingSpanM = 1.6, chordM = 0.33, aeroRp = Vec3(0.30, 0, 0.0)),
    mass = MassBalance(massKg = 2.0, ixx = 0.06, iyy = 0.09, izz = 0.14, ixz = 0.0, cg = Vec3(0.30, 0, 0.0)),
    contacts = Seq(Contact("MAIN", Vec3(0.32, 0, -0.10)), Contact("NOSE", Vec3(0.05, 0, -0.10))),
    controls = Seq(
      ControlSurface(ControlAxis.Elevator, math.toRadians(25)),
      ControlSurface(ControlAxis.Aileron, math.toRadians(20)),
      ControlSurface(ControlAxis.Rudder, math.toRadians(25))
    ),
    aero = new AeroDerivatives(
      cl0 = 0.2, cla = 5.0, clq = 6.0, clde = 0.4,
      cd0 = 0.025, spanEfficiency = 0.85, aspectRatio = 5.12, cdde = 0.0,
      cm0 = 0.02, cma = -0.6, cmq = -8.0, cmde = -0.9,
      cyb = -0.30, cyp = 0.0, cyr = 0.20, cydr = 0.15, cyda = 0.0,
      clb = -0.05, clp = -0.45, clr = 0.10, cldr = 0.01, clda = 0.20,
      cnb = 0.06, cnp = -0.03, cnr = -0.06, cndr = -0.07, cnda = -0.01
    )
  )

  /** The aircraft across attitudes, as the AVL sweep produces it: lift, drag and pitching moment. */
  def sampleCurves: AeroCurves = AeroCurves(
    alphaRad = Seq(-10.0, -5.0, 0.0, 5.0, 10.0, 15.0, 20.0).map(math.toRadians),
    cl = Seq(-0.24, 0.11, 0.29, 0.45, 0.61, 0.75, 0.88),
    cd = Seq(0.058, 0.069, 0.088, 0.114, 0.145, 0.178, 0.212),
    cm = Seq(0.063, 0.085, 0.106, 0.125, 0.144, 0.159, 0.172))

  private def dump(path: String, content: String): Unit = {
    val f = new java.io.File(path)
    Option(f.getParentFile).foreach(_.mkdirs())
    val pw = new java.io.PrintWriter(f)
    try pw.write(content) finally pw.close()
    System.err.println(s"Wrote $path (${content.length} chars)")
  }

  /** args: rootDir name [table]. Writes the full model (aero + optional lift table +
   *  electric propulsion) plus its engine files into a JSBSim root directory. */
  def main(args: Array[String]): Unit = {
    if (args.length < 2) { print(write(sampleAircraft)); return }
    val root = args(0); val name = args(1); val withTable = args.length > 2
    val base = sampleAircraft.copy(
      name = name,
      propulsion = Some(Propulsion(ElectricMotor(290.0), propDiameterM = 0.24, numBlades = 2,
        at = Vec3(0.0, 0, 0.0)))
    )
    val ac = if (withTable) base.copy(curves = Some(sampleCurves)) else base
    val gm = generate(ac)
    dump(s"$root/aircraft/$name/$name.xml", gm.aircraftXml)
    gm.engineFiles.foreach { case (fn, content) => dump(s"$root/engine/$fn", content) }
  }
}
