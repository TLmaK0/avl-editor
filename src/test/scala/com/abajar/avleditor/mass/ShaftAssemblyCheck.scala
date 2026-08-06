/*
 * A shaft is an assembly and moves as one: the motor, the propeller or the fan state where they sit within it,
 * so moving the shaft carries them all and each can still be placed inside it afterwards. Pinned because that
 * only works if every place a position is read — the masses, the 3D view, the export — adds the shaft's own
 * exactly once.
 * Run with:  sbt "test:runMain com.abajar.avleditor.mass.ShaftAssemblyCheck"
 */
package com.abajar.avleditor.mass

import com.abajar.avleditor.crrcsim._
import com.abajar.avleditor.jsbsim.JsbsimExporter
import scala.collection.JavaConverters._

object ShaftAssemblyCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Double, b: Double, tol: Double = 1e-5): Boolean = math.abs(a - b) < tol

  /** A motor and a fan mounted on one shaft, each somewhere within it. */
  private def model(): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()
    crrcsim.getAvl.getGeometry.setSref(0.39f)
    crrcsim.getAvl.getGeometry.setBref(1.07f)
    val power = crrcsim.getConfig.getPower
    power.getBateries.clear()
    val battery = power.createBattery()
    battery.setU_0(22.2f); battery.setMass(0.4f)
    battery.getPos.setX(0.2f)
    battery.createShaft()
    val shaft = battery.getShafts.get(0)
    val engine = shaft.createEngine()
    engine.setMass(0.2f)
    engine.getPos.setX(0.05f) // 5 cm forward within the assembly
    val point = new EngineData
    point.setU_K(22.2f); point.setI_M(60f); point.setRpms(38000f)
    engine.getData.add(point)
    val fan = shaft.createDuctedFan()
    fan.setInnerDiameterMm(68f); fan.setBlades(12); fan.setMass(0.19f)
    fan.getPos.setX(0.10f)     // 10 cm forward of the shaft
    fan.getExhaust.setX(0.06f) // and its exhaust 6 cm forward of the fan
    fan.getExhaust.setZ(0.03f)
    crrcsim
  }

  private def shaftOf(crrcsim: CRRCSim): Shaft =
    crrcsim.getConfig.getPower.getBateries.get(0).getShafts.get(0)

  private def markerFor(crrcsim: CRRCSim, label: String): MassMarker =
    MassMarkers.from(crrcsim).find(_.label == label).get

  def main(args: Array[String]): Unit = {
    println("a shaft at the origin changes nothing, which is how old models are unaffected")
    val crrcsim = model()
    check("the motor is where it says it is", near(markerFor(crrcsim, "Engine").x, 0.05))
    check("and so is the fan", near(markerFor(crrcsim, "Ducted fan").x, 0.10))

    println("moving the shaft carries everything on it")
    val shaft = shaftOf(crrcsim)
    shaft.getPos.setX(0.50f)
    shaft.getPos.setZ(0.02f)
    check("the motor moved with it", near(markerFor(crrcsim, "Engine").x, 0.55))
    check("in every direction", near(markerFor(crrcsim, "Engine").z, 0.02))
    check("the fan too", near(markerFor(crrcsim, "Ducted fan").x, 0.60))
    check("and the battery, which is not on the shaft, did not",
      near(markerFor(crrcsim, "Battery").x, 0.20))

    println("and the weights follow, so the centre of gravity does")
    crrcsim.calculate()
    val cgAfter = crrcsim.getCenterOfMass.getX
    val masses = crrcsim.getAllMasses.asScala
    check("the motor's mass is where the motor is",
      masses.exists(m => m.getName == "electric motor" && near(m.getX, 0.55)))
    check("the fan's too", masses.exists(m => m.getName == "ducted fan" && near(m.getX, 0.60)))
    shaft.getPos.setX(0f)
    crrcsim.calculate()
    check("moving the shaft back moves the centre of gravity back",
      crrcsim.getCenterOfMass.getX < cgAfter)

    println("then each part can still be placed within the assembly")
    shaft.getPos.setX(0.50f)
    val fanMarker = markerFor(crrcsim, "Ducted fan")
    // A drag in the 3D view reports absolute coordinates; the part stores where it sits in the assembly.
    fanMarker.moveTo(0.65f, 0f, 0.02f)
    val fan = shaft.getDuctedFans.get(0)
    check("dragging it writes back where it sits within the shaft", near(fan.getPos.getX, 0.15))
    check("and it shows up where it was dragged to", near(markerFor(crrcsim, "Ducted fan").x, 0.65))
    check("without moving the motor", near(markerFor(crrcsim, "Engine").x, 0.55))
    check("or the shaft", near(shaft.getPos.getX, 0.50))

    println("the shaft itself can be grabbed, though it weighs nothing")
    val shaftMarker = markerFor(crrcsim, "Shaft")
    check("it has a marker", near(shaftMarker.x, 0.50))
    check("with no weight of its own, because a shaft is not a part", shaftMarker.mass == 0f)
    shaftMarker.moveTo(0.30f, 0f, 0f)
    check("dragging it moves the assembly", near(shaft.getPos.getX, 0.30))
    check("and everything on it", near(markerFor(crrcsim, "Ducted fan").x, 0.45))

    println("the exported thrust is applied through all three levels")
    // shaft 0.30 + fan 0.15 + exhaust 0.06 = 0.51, and the exhaust's own 3 cm of height.
    val at = JsbsimExporter.buildPropulsion(crrcsim).get.at
    println(f"  shaft ${shaft.getPos.getX}%.2f + fan ${fan.getPos.getX}%.2f + exhaust " +
      f"${fan.getExhaust.getX}%.2f -> ${at.x}%.3f m")
    check("the shaft, the fan and the exhaust add up once each", near(at.x, 0.51))
    check("and the height comes through too", near(at.z, 0.03))

    println(if (ok) "SHAFT_ASSEMBLY_OK" else "SHAFT_ASSEMBLY_FAIL")
    if (!ok) sys.exit(1)
  }
}
