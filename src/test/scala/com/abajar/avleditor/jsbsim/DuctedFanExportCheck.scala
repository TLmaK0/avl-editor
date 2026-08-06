/*
 * A ducted fan reaches JSBSim as the same element a propeller does, carrying its own curves instead of the
 * generic propeller's — because that element is a machine that turns shaft power into thrust against advance
 * ratio, and the curves are the whole of what distinguishes a shrouded rotor from a free one.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.DuctedFanExportCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim._
import scala.collection.JavaConverters._

object DuctedFanExportCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Double, b: Double, tol: Double = 1e-4): Boolean = math.abs(a - b) < tol

  /** A model with a motor and either a propeller or a 70 mm fan on its shaft. */
  private def model(withFan: Boolean, withPropeller: Boolean = false,
                    staticThrustKg: Float = 1.6f): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()
    crrcsim.getAvl.getGeometry.setSref(0.3909f)
    crrcsim.getAvl.getGeometry.setBref(1.0692f)
    val battery = new Battery
    battery.setU_0(22.2f)
    battery.setMass(0.4f)
    val shaft = new Shaft
    val engine = new Engine
    engine.setMass(0.2f)
    val data = engine.createData()
    data.setU_K(22.2f); data.setI_M(60f); data.setRpms(38000f)
    shaft.getEngines.add(engine)
    if (withFan) {
      val fan = shaft.createDuctedFan()
      fan.setInnerDiameterMm(68f)
      fan.setBlades(12)
      fan.setStaticThrust(staticThrustKg)
      fan.setMass(0.19f)
    }
    if (withPropeller) {
      val prop = shaft.createPropeller()
      prop.setD(0.25f); prop.setBlades(2)
    }
    battery.getShafts.add(shaft)
    crrcsim.getConfig.getPower.getBateries.add(battery)
    crrcsim
  }

  private def propellerFileOf(crrcsim: CRRCSim): String = {
    val calc = ControlEffectivenessCheckSupport.calculation
    val aircraft = JsbsimWriter.Aircraft("fan",
      JsbsimWriter.Metrics(0.3909, 1.0692, 0.3655, JsbsimWriter.Vec3(0, 0, 0)),
      JsbsimWriter.MassBalance(1.5, 0.02, 0.03, 0.04, 0.0, JsbsimWriter.Vec3(0, 0, 0)),
      Nil, Nil, calc, propulsion = JsbsimExporter.buildPropulsion(crrcsim))
    JsbsimWriter.generate(aircraft).engineFiles.find(_._1.contains("prop")).map(_._2).getOrElse("")
  }

  private def tableRows(xml: String, name: String): Seq[(Double, Double)] = {
    val block = xml.split(s"""<table name="$name"""").drop(1).headOption.getOrElse("")
    val data = """(?s)<tableData>(.*?)</tableData>""".r.findFirstMatchIn(block)
      .map(_.group(1)).getOrElse("")
    data.split("\n").map(_.trim).filter(_.nonEmpty).map { line =>
      val parts = line.split("\\s+"); (parts(0).toDouble, parts(1).toDouble)
    }.toList
  }

  def main(args: Array[String]): Unit = {
    println("the fan's own numbers reach the exported thruster")
    val withFan = model(withFan = true)
    val propulsion = JsbsimExporter.buildPropulsion(withFan)
    check("there is propulsion", propulsion.isDefined)
    val pr = propulsion.get
    check("its diameter is the fan's bore, in metres", near(pr.propDiameterM, 0.068))
    check("and its blade count the fan's", pr.numBlades == 12)
    check("it carries curves of its own", pr.curves.isDefined)

    println("and they are the fan's, not the generic propeller's")
    val file = propellerFileOf(withFan)
    val ct = tableRows(file, "C_THRUST")
    val cp = tableRows(file, "C_POWER")
    println(f"  ${ct.length}%d rows, thrust running out at J = ${ct.last._1}%.4f")
    check("the thrust table is the fan's length", ct.length == DuctedFanCurves.Rows)
    check("the power table too", cp.length == DuctedFanCurves.Rows)
    // The generic propeller's table is spent by J = 0.73; a fan holds on well past it.
    check("its thrust runs out past where a free propeller's does", ct.last._1 > 0.73)
    check("and it ends at zero thrust", near(ct.last._2, 0.0, 1e-6))
    check("the generic propeller curve is nowhere in the file", !file.contains("0.1288"))

    println("the curve in the file is the one the derivation produced")
    val derived = JsbsimExporter.ductedFanCurves(
      withFan.getConfig.getPower.getBateries.get(0).getShafts.get(0),
      withFan.getConfig.getPower.getBateries.get(0).getShafts.get(0).getDuctedFans.get(0),
      withFan.getAvl.units()).right.get
    check("the advance ratio it runs out at is k", near(ct.last._1, derived.k, 1e-3))
    check("row for row", ct.zip(derived.ct).forall { case ((j, c), (dj, dc)) =>
      near(j, dj, 1e-3) && near(c, dc, 1e-3)
    })

    println("a propeller is untouched by any of this")
    val withProp = model(withFan = false, withPropeller = true)
    val propFile = propellerFileOf(withProp)
    check("it still gets the generic curves", propFile.contains("0.1288"))
    check("and states no curves of its own",
      JsbsimExporter.buildPropulsion(withProp).get.curves.isEmpty)

    println("what the requirements say")
    def problems(crrcsim: CRRCSim): Seq[String] = SimulationRequirements.validate(crrcsim)
    check("a fan with a stated thrust raises nothing about the thruster",
      !problems(model(withFan = true)).exists(p => p.contains("fan") || p.contains("propeller")))
    val noThrust = problems(model(withFan = true, staticThrustKg = 0f))
    noThrust.filter(_.contains("thrust")).foreach(p => println("  ! " + p))
    check("without one it is refused", noThrust.exists(_.contains("static thrust")))
    check("and told why: the ideal is about twice the truth",
      noThrust.exists(_.contains("twice")))
    val both = problems(model(withFan = true, withPropeller = true))
    check("a shaft with both a propeller and a fan is refused",
      both.exists(p => p.contains("both a propeller and a ducted fan")))
    val neither = problems(model(withFan = false))
    check("a shaft with neither is told about both buttons",
      neither.exists(p => p.contains("'+ Propeller'") && p.contains("'+ Fan'")))

    println("the fan is a part of the aircraft like any other")
    val m = model(withFan = true)
    m.calculate()
    val markers = com.abajar.avleditor.mass.MassMarkers.from(m)
    check("its position is shown in the 3D view", markers.exists(_.label == "Ducted fan"))
    check("its mass counts towards the total",
      m.getConfig.getMass_inertia.getMass > 0.7f)
    val shapes = com.abajar.avleditor.mass.ComponentShapes.from(m, markers)
    check("and it is drawn as the disc its blades sweep",
      shapes.exists(s => s.kind == com.abajar.avleditor.mass.ComponentShapes.Disc &&
        near(s.sizeY, 0.068, 1e-6) && s.blades == 12))
    check("which cannot be dragged, since the bore decides the thrust",
      shapes.filter(_.kind == com.abajar.avleditor.mass.ComponentShapes.Disc).forall(!_.resizable))

    println(if (ok) "DUCTED_FAN_EXPORT_OK" else "DUCTED_FAN_EXPORT_FAIL")
    if (!ok) sys.exit(1)
  }
}

/** The stability figures an exported aircraft needs, so this check can talk about propulsion only. */
private object ControlEffectivenessCheckSupport {
  def calculation: JsbsimWriter.AeroDerivatives = new JsbsimWriter.AeroDerivatives(
    cl0 = 0.2, cla = 4.5, clq = 6.0, clde = 0.2,
    cd0 = 0.03, spanEfficiency = 0.8, aspectRatio = 5.0, cdde = 0.0,
    cm0 = 0.0, cma = -0.5, cmq = -8.0, cmde = -0.9,
    cyb = -0.3, cyp = 0.0, cyr = 0.2, cydr = 0.15, cyda = 0.0,
    clb = -0.05, clp = -0.45, clr = 0.1, cldr = 0.01, clda = 0.2,
    cnb = 0.06, cnp = -0.03, cnr = -0.06, cndr = -0.07, cnda = -0.01)
}
