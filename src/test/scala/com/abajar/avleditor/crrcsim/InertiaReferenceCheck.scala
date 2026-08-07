/*
 * An inertia is about a point, and this one is about the CENTRE OF GRAVITY.
 *
 * `Config.calculateInertiasMasses` used to sum them about the **origin of the drawing** — wherever the user
 * happened to start, usually the nose — so every inertia but I_xx carried a parallel-axis term m*d^2 that
 * does not belong to the aircraft. `Config.mass_inertia` is what the JSBSim export writes into
 * <mass_balance>, and JSBSim reads those as inertias about the CG on the very next line, so an aircraft
 * drawn with its origin 0.3 m ahead of its CG was exported far too reluctant to pitch and yaw, with nothing
 * in the file to point at.
 *
 * AVL was never affected — it is handed the point masses and works its own out — which is how the two came
 * to disagree and how this was found: a lateral model assembled from these inertias gave roots that did not
 * match the ones AVL had already returned.
 *
 * The property asserted here is **translation invariance**, not a number: moving the whole aeroplane must
 * not change what it weighs about its own centre. That is the exact statement of the bug and it survives
 * any rescaling.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.crrcsim.InertiaReferenceCheck"
 */
package com.abajar.avleditor.crrcsim

import com.abajar.avleditor.ModelUnits
import com.abajar.avleditor.avl.mass.Mass
import java.util.ArrayList

object InertiaReferenceCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def mass(m: Float, x: Float, y: Float, z: Float): Mass = {
    val result = new Mass()
    result.setMass(m)
    result.setX(x)
    result.setY(y)
    result.setZ(z)
    result
  }

  private def inertiasOf(masses: List[Mass]): MassInertia = {
    val config = new Config()
    val list = new ArrayList[Mass]()
    masses.foreach(list.add)
    config.setMass_inertiaFromMasses(list, ModelUnits.DEFAULTS)
    config.getMass_inertia
  }

  def main(args: Array[String]): Unit = {
    println("a dumbbell: two equal masses a metre apart on the x-axis")
    // About their own centre, Izz = 2 * m * (d/2)^2 = m d^2 / 2 = 0.5 for m = 1, d = 1.
    // About the origin, with the pair sitting from x=0 to x=1, it would be m d^2 = 1.0 — twice as much.
    val dumbbell = List(mass(1f, 0f, 0f, 0f), mass(1f, 1f, 0f, 0f))
    val plain = inertiasOf(dumbbell)
    println(f"    Izz ${plain.getI_zz}%.6f   (about the CG 0.5, about the origin 1.0)")
    check("it is about the CG", math.abs(plain.getI_zz - 0.5) < 1e-5)
    check("and I_yy the same, the masses being in the x-z plane", math.abs(plain.getI_yy - 0.5) < 1e-5)
    check("and I_xx is zero, both masses being on the axis", math.abs(plain.getI_xx) < 1e-5)

    println("and moving the whole aeroplane changes nothing about it")
    // The property, not the number: an inertia about the CG cannot know where the origin is.
    List((10f, 0f, 0f), (0f, 0f, 7f), (-3.5f, 0f, 2.25f)).foreach { case (dx, dy, dz) =>
      val moved = inertiasOf(dumbbell.map(m => mass(m.getMass, m.getX + dx, m.getY + dy, m.getZ + dz)))
      check(f"shifted by ($dx%.1f, $dy%.1f, $dz%.1f): I_zz unchanged",
        math.abs(moved.getI_zz - plain.getI_zz) < 1e-4)
      check(f"shifted by ($dx%.1f, $dy%.1f, $dz%.1f): I_yy unchanged",
        math.abs(moved.getI_yy - plain.getI_yy) < 1e-4)
      check(f"shifted by ($dx%.1f, $dy%.1f, $dz%.1f): I_xx unchanged",
        math.abs(moved.getI_xx - plain.getI_xx) < 1e-4)
      check(f"shifted by ($dx%.1f, $dy%.1f, $dz%.1f): I_xz unchanged",
        math.abs(moved.getI_xz - plain.getI_xz) < 1e-4)
      check(f"shifted by ($dx%.1f, $dy%.1f, $dz%.1f): the mass is the mass",
        math.abs(moved.getMass - plain.getMass) < 1e-5)
    }

    println("the product of inertia is about the CG too")
    // Two masses on a diagonal through their own centre: I_xz = sum m x z about that centre.
    val diagonal = List(mass(2f, -1f, 0f, -0.5f), mass(2f, 1f, 0f, 0.5f))
    val diag = inertiasOf(diagonal)
    println(f"    I_xz ${diag.getI_xz}%.6f   (2 * 2 * 1 * 0.5 = 2.0)")
    check("it comes out about their own centre", math.abs(diag.getI_xz - 2.0) < 1e-5)
    val diagMoved = inertiasOf(diagonal.map(m => mass(m.getMass, m.getX + 4f, m.getY, m.getZ - 3f)))
    check("and moving them does not change it", math.abs(diagMoved.getI_xz - diag.getI_xz) < 1e-4)

    println("an aircraft with no mass at all is left at zero, not divided by it")
    val empty = inertiasOf(Nil)
    check("nothing blows up", !empty.getI_zz.isNaN && !empty.getI_xz.isNaN)

    println(if (ok) "INERTIA_REFERENCE_OK" else "INERTIA_REFERENCE_FAIL")
    if (!ok) sys.exit(1)
  }
}
