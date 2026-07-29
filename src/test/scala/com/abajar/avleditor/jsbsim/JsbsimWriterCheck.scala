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

  def main(args: Array[String]): Unit = {
    val xml = write(sampleAircraft)
    if (args.nonEmpty) {
      val pw = new java.io.PrintWriter(args(0))
      try pw.write(xml) finally pw.close()
      System.err.println(s"Wrote ${args(0)} (${xml.length} chars)")
    } else print(xml)
  }
}
