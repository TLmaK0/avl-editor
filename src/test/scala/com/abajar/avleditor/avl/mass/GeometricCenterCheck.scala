/*
 * A new mass must land at the middle of the element it belongs to, not on the nose. Weighing a wing
 * and finding its mass already in the middle of the wing is the useful default; the origin is only
 * right for a nose-mounted item.
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.mass.GeometricCenterCheck"
 */
package com.abajar.avleditor.avl.mass

import com.abajar.avleditor.avl.AVLGeometry
import com.abajar.avleditor.avl.geometry.{Body, BodyProfilePoint, Control, Section, Surface}

object GeometricCenterCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Float, b: Double, tol: Double = 1e-3): Boolean = math.abs(a - b) < tol

  /** A rectangular wing: span 2 x 1 m, chord 0.4 m, leading edge at x = 0.5, offset dZ = 0.1. */
  private def wing(symmetric: Boolean): Surface = {
    val surface = new Surface
    surface.setSymmetric(symmetric)
    surface.setdX(0f); surface.setdY(0f); surface.setdZ(0.1f)
    surface.getSections.clear()
    Seq(0.0f, 1.0f).foreach { y =>
      val s = new Section
      s.setXle(0.5f); s.setYle(y); s.setZle(0f); s.setChord(0.4f)
      surface.getSections.add(s)
    }
    surface.initSectionParents()
    surface
  }

  /** A cone-nosed body 1 m long: radius 0 at the tip, 0.1 m at the tail. */
  private def coneBody(): Body = {
    val body = new Body
    body.setLength(1.0f)
    body.setdX(0.2f); body.setdY(0f); body.setdZ(0f)
    body.getProfilePoints.clear()
    body.getProfilePoints.add(new BodyProfilePoint(0.0f, 0.0f))
    body.getProfilePoints.add(new BodyProfilePoint(1.0f, 0.1f))
    body
  }

  def main(args: Array[String]): Unit = {
    println("a wing")
    val asymmetric = wing(symmetric = false)
    val wc = asymmetric.geometricCenter()
    println(f"  centre: (${wc(0)}%.4f, ${wc(1)}%.4f, ${wc(2)}%.4f)")
    check("x is at mid-chord", near(wc(0), 0.5 + 0.2))
    check("y is at mid-span", near(wc(1), 0.5))
    check("z carries the surface offset", near(wc(2), 0.1))

    val symmetric = wing(symmetric = true)
    println(f"  symmetric centre y: ${symmetric.geometricCenter()(1)}%.4f")
    check("a symmetric surface centres on the plane of symmetry",
      near(symmetric.geometricCenter()(1), 0.0))

    println("a mass created on it")
    val mass = symmetric.createMass()
    check("starts at the wing's centre",
      near(mass.getX, symmetric.geometricCenter()(0)) && near(mass.getY, 0.0) &&
        near(mass.getZ, 0.1))
    check("and it is the only mass there", symmetric.getMasses.size == 1)

    // The position belongs to the mass from then on.
    mass.setX(1.234f)
    check("moving it afterwards sticks", near(mass.getX, 1.234))
    check("creating another does not move the first", {
      symmetric.createMass(); near(mass.getX, 1.234)
    })

    println("a section")
    val section = symmetric.getSections.get(0)
    val sc = section.geometricCenter()
    println(f"  centre: (${sc(0)}%.4f, ${sc(1)}%.4f, ${sc(2)}%.4f)")
    check("x is its own mid-chord", near(sc(0), 0.5 + 0.2))
    check("z includes the parent surface's offset", near(sc(2), 0.1))

    println("a body")
    val body = coneBody()
    val bc = body.geometricCenter()
    println(f"  centre: (${bc(0)}%.4f, ${bc(1)}%.4f, ${bc(2)}%.4f)")
    // A cone's centroid is three quarters of the way to the base, plus the body offset.
    check("x is three quarters along the cone", near(bc(0), 0.2 + 0.75))
    check("a mass on it starts there", near(body.createMass().getX, 0.2 + 0.75))

    println("a control")
    val control = new Control
    control.setXhinge(0.7f)
    section.getControls.add(control)
    section.initControlParents()
    val cc = control.geometricCenter()
    println(f"  centre x: ${cc(0)}%.4f")
    // Section centre 0.7 (mid-chord), shifted back to halfway between hinge and trailing edge.
    check("it sits behind the hinge", cc(0) > sc(0))

    println("the whole aircraft")
    val geo = new AVLGeometry
    geo.getSurfaces.clear(); geo.getBodies.clear()
    geo.getSurfaces.add(wing(symmetric = true))
    geo.getBodies.add(coneBody())
    val ac = geo.geometricCenter()
    println(f"  centre: (${ac(0)}%.4f, ${ac(1)}%.4f, ${ac(2)}%.4f)")
    check("it lies between the wing's and the body's centres",
      ac(0) > 0.6 && ac(0) < 1.0)
    check("a mass added to the geometry starts there", near(geo.createMass().getX, ac(0)))

    println("an element with no geometry")
    val empty = new Surface
    empty.getSections.clear()
    check("falls back to its own offsets, not a crash",
      empty.geometricCenter().length == 3)

    println(if (ok) "GEOMETRIC_CENTER_OK" else "GEOMETRIC_CENTER_FAIL")
    if (!ok) sys.exit(1)
  }
}
