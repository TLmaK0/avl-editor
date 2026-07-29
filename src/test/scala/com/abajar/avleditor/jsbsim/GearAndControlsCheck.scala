/*
 * Validates per-control deflection (JsbsimExporter.detectControls) and gear from wheels
 * (JsbsimExporter.buildContacts).
 * Run: sbt "test:runMain com.abajar.avleditor.jsbsim.GearAndControlsCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim._
import JsbsimWriter._

object GearAndControlsCheck {
  def main(args: Array[String]): Unit = {
    val c = new CRRCSimFactory().create()
    val geo = c.getAvl.getGeometry
    geo.getSurfaces.clear() // isolate from the factory's default aircraft
    val surface = geo.createSurface()
    val section = surface.createSection()
    // Add controls directly (createControl also copies to adjacent sections with defaults).
    val elev = new com.abajar.avleditor.avl.geometry.Control(); elev.setType(1); elev.setMaxDeflection(30f)
    val ail = new com.abajar.avleditor.avl.geometry.Control(); ail.setType(0); ail.setMaxDeflection(18f)
    section.getControls.add(elev); section.getControls.add(ail)

    val controls = JsbsimExporter.detectControls(geo)
    println("controls=" + controls)
    val elevOk = controls.exists(cs => cs.axis == ControlAxis.Elevator && math.abs(cs.maxDeflectionRad - math.toRadians(30)) < 1e-4)
    val ailOk = controls.exists(cs => cs.axis == ControlAxis.Aileron && math.abs(cs.maxDeflectionRad - math.toRadians(18)) < 1e-4)

    val w = new Wheel(); w.setName("main gear")
    w.getPos.setX(0.1f); w.getPos.setY(0.3f); w.getPos.setZ(-0.2f)
    c.getWheels.add(w)
    val contacts = JsbsimExporter.buildContacts(c, Vec3(0, 0, 0))
    println("contacts=" + contacts)
    val gearOk = contacts.exists(ct =>
      ct.name == "main_gear" && math.abs(ct.at.y - 0.3) < 1e-4 && math.abs(ct.at.z + 0.2) < 1e-4)

    val ok = elevOk && ailOk && gearOk
    println(s"elevOk=$elevOk ailOk=$ailOk gearOk=$gearOk")
    println(if (ok) "GEAR_CONTROLS_OK" else "GEAR_CONTROLS_FAIL")
    if (!ok) sys.exit(1)
  }
}
