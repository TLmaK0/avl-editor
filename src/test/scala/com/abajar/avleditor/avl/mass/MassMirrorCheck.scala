/*
 * A mirrored element carries its masses in pairs: AVL and JSBSim mirror geometry but never mass, so
 * one mass on a wing pod that AVL draws twice states half the weight, off the centreline.
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.mass.MassMirrorCheck"
 */
package com.abajar.avleditor.avl.mass

import com.abajar.avleditor.avl.AVLGeometry
import com.abajar.avleditor.avl.geometry.{Body, BodyProfilePoint, Control, Section, Surface}
import scala.collection.JavaConverters._

object MassMirrorCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Float, b: Double, tol: Double = 1e-4): Boolean = math.abs(a - b) < tol

  /** A wing pod: a body 0.25 m long sitting 0.55 m out on the left, so AVL duplicates it. */
  private def pod(): Body = {
    val body = new Body
    body.setName("pod")
    body.setLength(0.25f)
    body.setdX(1.0f); body.setdY(-0.55f); body.setdZ(0f)
    body.getProfilePoints.clear()
    body.getProfilePoints.add(new BodyProfilePoint(0.0f, 0.0f))
    body.getProfilePoints.add(new BodyProfilePoint(1.0f, 0.03f))
    body
  }

  private def wing(symmetric: Boolean): Surface = {
    val surface = new Surface
    surface.setName("wing")
    surface.setSymmetric(symmetric)
    surface.getSections.clear()
    Seq(0.0f, 1.0f).foreach { y =>
      val s = new Section
      s.setXle(0.5f); s.setYle(y); s.setZle(0f); s.setChord(0.4f)
      surface.getSections.add(s)
    }
    surface.initSectionParents()
    surface
  }

  def main(args: Array[String]): Unit = {
    println("which elements are mirrored")
    check("a symmetric surface, about y = 0", wing(symmetric = true).mirrorPlaneY() == 0f)
    check("an asymmetric one is not", wing(symmetric = false).mirrorPlaneY() == null)
    check("a body off the centreline is", pod().mirrorPlaneY() != null)
    val centred = pod(); centred.setdY(0f)
    check("a body on the centreline is not", centred.mirrorPlaneY() == null)
    check("a section inherits its surface's plane",
      wing(symmetric = true).getSections.get(0).mirrorPlaneY() == 0f)
    val control = new Control
    val section = wing(symmetric = true).getSections.get(0)
    section.getControls.add(control); section.initControlParents()
    check("and so does a control", control.mirrorPlaneY() == 0f)
    check("the geometry's own masses are absolute", new AVLGeometry().mirrorPlaneY() == null)

    println("a mass created on a mirrored body")
    val body = pod()
    val left = body.createMass()
    val right = body.mirrorMassOf(left)
    check("comes as a pair", body.getMasses.size == 2 && right != null)
    check("the twin is on the other side", near(right.getY, 0.55) && near(left.getY, -0.55))
    check("at the same station", near(right.getX, left.getX) && near(right.getZ, left.getZ))
    // The pod is weighed once and the aircraft carries two of them.
    left.setMass(0.0765f)
    body.syncMirrorOf(left)
    check("the weight is stated once and carried twice",
      near(right.getMass, 0.0765) && near(body.getMassesRecursive.asScala.map(_.getMass).sum, 0.153))
    check("named by the side they are on",
      left.getName.endsWith("-Y") && right.getName.endsWith("+Y"))
    check("and the pair is found from either half", body.mirrorMassOf(right) eq left)

    println("a mass created on a symmetric surface")
    val symmetricWing = wing(symmetric = true)
    val single = symmetricWing.createMass()
    check("stays a single mass on the plane of symmetry",
      symmetricWing.getMasses.size == 1 && near(single.getY, 0.0))
    check("with no twin, because it already stands for both halves",
      symmetricWing.mirrorMassOf(single) == null)
    check("and it is not reported as missing one", !symmetricWing.isMassMissingItsMirror(single))

    println("moving one half")
    left.setX(1.2f); left.setZ(0.04f); left.setY(-0.6f)
    body.syncMirrorOf(left)
    check("the other follows, mirrored",
      near(right.getX, 1.2) && near(right.getZ, 0.04) && near(right.getY, 0.6))
    left.setMass(0.09f)
    body.syncMirrorOf(left)
    check("and so does its weight", near(right.getMass, 0.09))

    println("deleting one half")
    val removed = body.removeMassWithMirror(left)
    check("takes both", removed.size == 2 && body.getMasses.isEmpty)

    println("a model that already states one side only")
    val single_sided = pod()
    val lonely = single_sided.addMassAt(1.1f, -0.55f, 0f)
    lonely.setName("pod ballast")
    lonely.setMass(0.0765f)
    check("is reported as missing its mirror", single_sided.isMassMissingItsMirror(lonely))
    check("and nothing is invented for it", single_sided.getMasses.size == 1)

    println("links survive a reload, where the pointers are gone")
    val reloaded = pod()
    val a = reloaded.addMassAt(1.1f, -0.55f, 0.02f); a.setMass(0.05f); a.setName("pod -Y")
    val b = reloaded.addMassAt(1.1f, 0.55f, 0.02f); b.setMass(0.05f); b.setName("pod +Y")
    a.setMirror(null); b.setMirror(null)
    reloaded.initMassMirrors()
    check("a matching pair is re-paired", (a.getMirror eq b) && (b.getMirror eq a))
    check("neither is reported as missing a mirror",
      !reloaded.isMassMissingItsMirror(a) && !reloaded.isMassMissingItsMirror(b))

    println("masses generated from volume")
    val geometry = new AVLGeometry
    geometry.getSurfaces.clear(); geometry.getBodies.clear()
    geometry.getSurfaces.add(wing(symmetric = true))
    geometry.getBodies.add(pod())
    geometry.getMasses.clear()
    check("are generated", geometry.autoMassesFromVolume())
    val all = geometry.getMassesRecursive.asScala
    println(s"  ${all.size} masses: " + all.map(m => f"${m.getName}%s(${m.getY}%.2f)").mkString(", "))
    check("one per side and no strays: two for the wing, two for the pod", all.size == 4)
    check("every off-centre one has its mirror",
      geometry.getMassOwners.asScala.forall(owner =>
        owner.getMasses.asScala.forall(m => !owner.isMassMissingItsMirror(m))))
    val podMasses = geometry.getBodies.get(0).getMasses.asScala.map(_.getMass)
    check("the two halves of the pod weigh the same, and the pod is counted whole",
      podMasses.size == 2 && near(podMasses(0), podMasses(1)) && podMasses.sum > 0f)

    println("finding the element a mass belongs to")
    val owner = geometry.findMassOwner(all.head)
    check("is answered", owner != null)
    check("and an unrelated mass has no owner", geometry.findMassOwner(new Mass) == null)

    println(if (ok) "MASS_MIRROR_OK" else "MASS_MIRROR_FAIL")
    if (!ok) sys.exit(1)
  }
}
