/*
 * The same aircraft is the same aircraft whatever units the model writes it down in. So the exported flight
 * model must come out identical from a model stated in metres and kilograms and from the same model stated in
 * centimetres and grams — and it did not: the centre of gravity, the landing gear, the fuel tank and the
 * propeller's diameter all went out unconverted while the reference geometry beside them was converted.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.ExportUnitsCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim._
import com.abajar.avleditor.avl.geometry.Control
import com.abajar.avleditor.avl.runcase.{AvlCalculation, Configuration, StabilityDerivatives}

object ExportUnitsCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Double, b: Double, tol: Double = 1e-4): Boolean = math.abs(a - b) < tol

  /**
   * One aircraft, stated in whatever units are asked for. Every figure is the same physical aeroplane: a
   * 0.9 m span, a wheel 0.2 m along and 0.05 m down, a 200 g tank, a 0.25 m propeller.
   */
  private def model(lengthUnit: String, massUnit: String): CRRCSim = {
    val perMetre = lengthUnit match {
      case "cm" => 100f
      case "in" => 39.3700787f
      case _ => 1f
    }
    val perKg = massUnit match {
      case "g" => 1000f
      case "oz" => 35.2739619f
      case _ => 1f
    }
    val crrcsim = new CRRCSimFactory().create()
    val avl = crrcsim.getAvl
    avl.setLengthUnit(lengthUnit)
    avl.setMassUnit(massUnit)
    avl.setVelocity(20f)

    val geo = avl.getGeometry
    geo.setSref(0.4f * perMetre * perMetre)
    geo.setBref(0.9f * perMetre)
    geo.setCref(0.35f * perMetre)
    geo.setXref(0.30f * perMetre); geo.setYref(0f); geo.setZref(0.02f * perMetre)

    geo.getSurfaces.clear()
    val section = geo.createSurface().createSection()
    section.getControls.clear()
    val control = new Control
    control.setType(1); control.setMaxDeflection(20f)
    section.getControls.add(control)

    crrcsim.getWheels.clear()
    Seq((0.2f, 0f), (0.7f, -0.25f), (0.7f, 0.25f)).zipWithIndex.foreach { case ((x, y), i) =>
      val w = new Wheel
      w.setName(s"GEAR$i")
      w.getPos.setX(x * perMetre); w.getPos.setY(y * perMetre); w.getPos.setZ(-0.05f * perMetre)
      crrcsim.getWheels.add(w)
    }

    // Mass inertia is the one thing already in SI: calculate() writes kilograms and kg*m2 into it whatever
    // the model states, which is why it is not scaled here.
    val mi = crrcsim.getConfig.getMass_inertia
    mi.setMass(1.5f); mi.setI_xx(0.02f); mi.setI_yy(0.03f); mi.setI_zz(0.04f)
    // The centre of mass is a view onto the geometry's reference point, set above: the same physical point,
    // which the export used to write twice — converted as the reference point, unconverted as the CG.

    val power = crrcsim.getConfig.getPower
    power.getBateries.clear()
    val battery = power.createBattery()
    battery.setU_0(11.1f)
    battery.createShaft()
    val shaft = battery.getShafts.get(0)
    val propeller = shaft.createPropeller()
    propeller.setD(0.25f * perMetre)
    propeller.setBlades(2)
    // A propeller mounted above the centreline: the offset that decides whether throttle pitches the nose.
    propeller.getPos.setX(0.05f * perMetre)
    propeller.getPos.setZ(0.04f * perMetre)
    val engine = shaft.createEngine()
    val point = new EngineData
    point.setU_K(11.1f); point.setI_M(20f); point.setRpms(9000f)
    engine.getData.add(point)
    val idle = engine.createDataIdle()
    idle.setU_K(11.1f); idle.setI_M(0.4f)

    val tank = new FuelTank
    tank.setCapacity(0.25f * perKg); tank.setContents(0.2f * perKg)
    tank.getPos.setX(0.4f * perMetre); tank.getPos.setZ(0.01f * perMetre)
    power.getFuelTanks.add(tank)

    crrcsim
  }

  private def calculation(): AvlCalculation = {
    val calc = new AvlCalculation(0, 2, 1)
    val cfg = new Configuration
    cfg.setSref(0.4f); cfg.setBref(0.9f); cfg.setCref(0.35f)
    cfg.setE(0.85f); cfg.setCLtot(0.3f); cfg.setCmtot(0f); cfg.setCDvis(0.02f)
    calc.setConfiguration(cfg)
    val std = new StabilityDerivatives
    std.initControls(3)
    std.setCLa(4.5f); std.setCma(-0.5f)
    calc.setStabilityDerivatives(std)
    calc.setControlNames(Array("elevator", "aileron", "rudder"))
    calc.setControlGains(Array(20f, 20f, 20f))
    calc.setTrimControlValues(Array(0f, 0f, 0f))
    calc
  }

  /**
   * The whole export: the aircraft and the engine and propeller files it references. All of it, because the
   * propeller's diameter lives in its own file and a conversion missing there would be invisible otherwise.
   */
  private def exported(lengthUnit: String, massUnit: String): String = {
    val generated = JsbsimWriter.generate(
      JsbsimExporter.buildAircraft("units", model(lengthUnit, massUnit), calculation()))
    generated.aircraftXml + generated.engineFiles.sortBy(_._1).map(_._2).mkString
  }

  private def number(xml: String, tag: String): Double =
    s"<$tag[^>]*>([-\\d.eE+]+)</$tag>".r.findFirstMatchIn(xml).map(_.group(1).toDouble).getOrElse(Double.NaN)

  /** The x of the named location block. */
  private def locationX(xml: String, name: String): Double =
    s"""(?s)<location name="$name"[^>]*><x>([-\\d.eE+]+)</x>""".r
      .findFirstMatchIn(xml).map(_.group(1).toDouble).getOrElse(Double.NaN)

  /** The x of the named contact's location. */
  private def contactX(xml: String, name: String): Double =
    s"""(?s)<contact type="BOGEY" name="$name">.*?<x>([-\\d.eE+]+)</x>""".r
      .findFirstMatchIn(xml).map(_.group(1).toDouble).getOrElse(Double.NaN)

  /** Every number in the file, in order: what two statements of the same aircraft must agree on. */
  private def numbers(xml: String): Seq[Double] =
    """-?\d+\.?\d*(?:[eE][-+]?\d+)?""".r.findAllIn(xml).toList.flatMap(t =>
      try Some(t.toDouble) catch { case _: NumberFormatException => None })

  def main(args: Array[String]): Unit = {
    println("the same aeroplane, written down three ways")
    val metric = exported("m", "kg")
    val centimetres = exported("cm", "g")
    val inches = exported("in", "oz")

    println("what the exported file says about it, in metres and kilograms")
    println(f"  CG x            ${locationX(metric, "CG")}%.4f")
    println(f"  gear x          ${contactX(metric, "GEAR0")}%.4f")
    println(f"  thruster x      ${number(metric.split("<thruster").last, "x")}%.4f")
    println(f"  wingspan        ${number(metric, "wingspan")}%.4f")

    // The strong property: the aircraft is the same, so the file is the same. Anything left unconverted
    // shows up here as a factor of a hundred or a thousand.
    // Compared as numbers rather than as text: a round trip through centimetres moves the last bit of a
    // float, which is not a difference in the aircraft. A unit left unconverted is a factor of a hundred.
    def agrees(other: String, what: String): Boolean = {
      val a = numbers(metric)
      val b = numbers(other)
      if (a.length != b.length) { println(s"  $what has a different shape of file"); return false }
      val worst = a.zip(b).map { case (x, y) => math.abs(x - y) / math.max(math.abs(x), 1e-9) }
      a.zip(b).filter { case (x, y) => math.abs(x - y) > 1e-5 * math.max(math.abs(x), 1e-9) }
        .take(6).foreach { case (x, y) => println(f"  $what%s: $x%.6f became $y%.6f") }
      println(f"  worst relative difference against $what%s: ${worst.max}%.2e")
      worst.max < 1e-5
    }
    check("a model stated in centimetres and grams exports the same aircraft",
      agrees(centimetres, "centimetres and grams"))
    check("and so does one stated in inches and ounces", agrees(inches, "inches and ounces"))

    println("and the figures are the physical ones, not the stated ones")
    check("the centre of gravity is in metres", near(locationX(metric, "CG"), 0.30))
    check("and it agrees with the aerodynamic reference point, which is the same point",
      near(locationX(metric, "CG"), locationX(metric, "AERORP")))
    check("the landing gear too", near(contactX(metric, "GEAR0"), 0.2))
    check("the wingspan", near(number(metric, "wingspan"), 0.9))
    check("the propeller's diameter", near(number(metric, "diameter"), 0.25))
    check("the tank's contents are in kilograms", near(number(metric, "contents"), 0.2))

    println("the thrust is applied where the component is, not at the origin")
    val thruster = metric.split("<thruster").last
    println(f"  thruster at x ${number(thruster, "x")}%.3f, z ${number(thruster, "z")}%.3f")
    check("along the fuselage", near(number(thruster, "x"), 0.05))
    // The one that matters for the moment: an axial thrust makes a pitching moment through its height only.
    check("and at its height, which is what makes a pitching moment",
      near(number(thruster, "z"), 0.04))
    check("it is not the structural origin any more",
      number(thruster, "z") != 0.0)

    println(if (ok) "EXPORT_UNITS_OK" else "EXPORT_UNITS_FAIL")
    if (!ok) sys.exit(1)
  }
}
