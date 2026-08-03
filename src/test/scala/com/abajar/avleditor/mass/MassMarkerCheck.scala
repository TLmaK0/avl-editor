/*
 * Every mass the model states a position for must be offered to the 3D view, and moving a marker
 * must move the aircraft rather than a copy of it.
 * Run with:  sbt "test:runMain com.abajar.avleditor.mass.MassMarkerCheck"
 */
package com.abajar.avleditor.mass

import com.abajar.avleditor.crrcsim.{CRRCSim, CRRCSimFactory, Wheel}

object MassMarkerCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Float, b: Double, tol: Double = 1e-3): Boolean = math.abs(a - b) < tol

  /** A wing carrying one mass, a battery with a motor and a propeller, a fuel tank, and a wheel. */
  private def model(): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()

    val geometry = crrcsim.getAvl.getGeometry
    geometry.getSurfaces.clear()
    val wing = geometry.createSurface()
    wing.getMasses.clear()
    val wingMass = wing.createMass()
    wingMass.setName("wing ballast")
    wingMass.setMass(0.6f)
    wingMass.setX(0.85f); wingMass.setY(0f); wingMass.setZ(-0.02f)

    val power = crrcsim.getConfig.getPower
    power.getBateries.clear()
    val battery = power.createBattery()
    battery.setMass(0.45f)
    battery.getPos.setX(0.25f); battery.getPos.setZ(0.01f)
    battery.createShaft()
    val shaft = battery.getShafts.get(0)

    val engine = shaft.createEngine()
    engine.setMass(0.18f)
    engine.getPos.setX(0.08f)

    // A propeller weighing nothing: its position is still real, so it must still be shown.
    val propeller = shaft.createPropeller()
    propeller.getPos.setX(0.02f)

    val tank = power.createFuelTank()
    tank.setContents(0.3f)
    tank.getPos.setX(0.4f)

    val wheel = new Wheel
    wheel.getPos.setX(0.2f); wheel.getPos.setZ(-0.05f)
    crrcsim.getWheels.clear()
    crrcsim.getWheels.add(wheel)

    crrcsim
  }

  def main(args: Array[String]): Unit = {
    val crrcsim = model()
    val markers = MassMarkers.from(crrcsim)
    println("the model's masses")
    markers.foreach(m => println(f"  ${m.label}%-18s ${m.mass}%6.3f kg at (${m.x}%.3f, ${m.y}%.3f, ${m.z}%.3f)"))

    check("one marker per mass and per positioned component", markers.size == 5)
    check("the wing's mass is there, by name and weight",
      markers.exists(m => m.label == "wing ballast" && near(m.mass, 0.6) && near(m.x, 0.85)))
    check("so is the battery", markers.exists(m => m.label == "Battery" && near(m.mass, 0.45) && near(m.x, 0.25)))
    check("so is the motor", markers.exists(m => m.label == "Engine" && near(m.mass, 0.18)))
    check("a component with no stated mass is still shown, at its own position",
      markers.exists(m => m.label == "Propeller" && near(m.mass, 0.0) && near(m.x, 0.02)))
    check("the fuel tank is shown with its contents",
      markers.exists(m => m.label == "Fuel tank" && near(m.mass, 0.3) && near(m.x, 0.4)))

    // The wheels are drawn as collision points, with their own handles: listing them here as well
    // would give the same point two meanings.
    val wheelPos = crrcsim.getWheels.get(0).getPos
    check("a wheel is not a mass", MassMarkers.indexOf(markers, wheelPos).isEmpty)

    println("moving a marker")
    val battery = crrcsim.getConfig.getPower.getBateries.get(0)
    val batteryMarker = markers.find(_.node eq battery).get
    batteryMarker.moveTo(0.31f, 0.02f, 0.05f)
    check("writes to the model, not to a copy",
      near(battery.getPos.getX, 0.31) && near(battery.getPos.getY, 0.02) && near(battery.getPos.getZ, 0.05))

    val wingMass = crrcsim.getAvl.getGeometry.getSurfaces.get(0).getMasses.get(0)
    MassMarkers.from(crrcsim).find(_.node eq wingMass).get.moveTo(0.9f, 0.1f, 0f)
    check("and so does moving a geometry mass",
      near(wingMass.getX, 0.9) && near(wingMass.getY, 0.1))

    println("finding the marker a tree node refers to")
    val fresh = MassMarkers.from(crrcsim)
    check("by the mass itself", MassMarkers.indexOf(fresh, wingMass).isDefined)
    check("by the component", MassMarkers.indexOf(fresh, battery).isDefined)
    check("by the component's Position node", MassMarkers.indexOf(fresh, battery.getPos).isDefined)
    check("and nothing for an unrelated object", MassMarkers.indexOf(fresh, "not a mass").isEmpty)
    check("the component and its Position are the same marker",
      MassMarkers.indexOf(fresh, battery) == MassMarkers.indexOf(fresh, battery.getPos))

    println("a model with nothing in it")
    check("no masses, no crash", MassMarkers.from(new CRRCSimFactory().create()) != null)
    check("and null is answered with an empty list", MassMarkers.from(null).isEmpty)

    println(if (ok) "MASS_MARKER_OK" else "MASS_MARKER_FAIL")
    if (!ok) sys.exit(1)
  }
}
