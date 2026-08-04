/*
 * A mirrored element stores one mass; the other half is derived. It shows in the 3D view, follows
 * whichever half is moved, and reaches the aircraft when a model is generated — AVL and JSBSim mirror
 * geometry but never mass, so the copy has to be written into what they read.
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.mass.MassMirrorCheck"
 */
package com.abajar.avleditor.avl.mass

import com.abajar.avleditor.avl.AVLGeometry
import com.abajar.avleditor.avl.geometry.{Body, BodyProfilePoint, Control, Section, Surface}
import com.abajar.avleditor.crrcsim.{CRRCSim, CRRCSimFactory}
import com.abajar.avleditor.mass.MassMarkers
import java.io.ByteArrayOutputStream
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
    val stored = body.createMass()
    stored.setName("pod ballast")
    stored.setMass(0.0765f)
    check("is stored once", body.getMasses.size == 1)
    val mirror = body.virtualMirrorOf(stored)
    check("and implies a mirrored half", mirror != null)
    check("on the other side, same station and weight",
      near(mirror.getY, 0.55) && near(mirror.getX, stored.getX) && near(mirror.getZ, stored.getZ) &&
        near(mirror.getMass, 0.0765))
    check("named as the mirror it is", mirror.getName.endsWith(Mass.MirrorSuffix))
    check("the weight is stated once and carried twice",
      near(body.getEffectiveMasses.asScala.map(_.getMass).sum, 0.153))
    check("while the model still stores one mass", body.getMassesRecursive.size == 1)

    println("moving the stored half")
    stored.setX(1.25f); stored.setZ(0.03f); stored.setY(-0.6f)
    val movedMirror = body.virtualMirrorOf(stored)
    check("the mirror is simply derived again, so it cannot drift",
      near(movedMirror.getX, 1.25) && near(movedMirror.getZ, 0.03) && near(movedMirror.getY, 0.6))
    stored.setMass(0.09f)
    check("weight included", near(body.virtualMirrorOf(stored).getMass, 0.09))

    println("deleting it")
    body.getMasses.remove(stored)
    check("takes the mirror with it, there being nothing else to delete",
      body.getEffectiveMasses.isEmpty)

    println("a mass on a symmetric wing")
    val symmetricWing = wing(symmetric = true)
    val half = symmetricWing.createMass()
    half.setMass(0.2f)
    check("starts on the side the wing defines, where that half balances", half.getY > 0.1f)
    check("so it weighs one wing and its mirror weighs the other",
      symmetricWing.hasVirtualMirror(half) &&
        near(symmetricWing.getEffectiveMasses.asScala.map(_.getMass).sum, 0.4))

    println("and one moved onto the plane of symmetry")
    half.setY(0f)
    check("has no mirror: it already stands for both halves",
      symmetricWing.virtualMirrorOf(half) == null &&
        near(symmetricWing.getEffectiveMasses.asScala.map(_.getMass).sum, 0.2))

    println("a model that states both sides itself")
    // Every '+Y'/'-Y' pair written before the mirror was implied: mirroring them again would double
    // the aircraft's weight.
    val bothSides = pod()
    val left = bothSides.addMassAt(1.1f, -0.55f, 0.02f); left.setMass(0.05f)
    val right = bothSides.addMassAt(1.1f, 0.55f, 0.02f); right.setMass(0.05f)
    check("neither half is mirrored again",
      bothSides.virtualMirrorOf(left) == null && bothSides.virtualMirrorOf(right) == null)
    check("so the weight is what the file says",
      near(bothSides.getEffectiveMasses.asScala.map(_.getMass).sum, 0.1))
    check("even when the two sides weigh different things", {
      right.setMass(0.08f)
      bothSides.virtualMirrorOf(left) == null && bothSides.virtualMirrorOf(right) == null
    })

    println("what a generated AVL mass file carries")
    val geometry = new AVLGeometry
    geometry.getSurfaces.clear(); geometry.getBodies.clear(); geometry.getMasses.clear()
    geometry.getBodies.add(pod())
    val podMass = geometry.getBodies.get(0).createMass()
    podMass.setName("pod ballast"); podMass.setMass(0.0765f)
    val out = new ByteArrayOutputStream
    geometry.writeAVLMassData(out)
    val lines = out.toString.split("\n").filter(_.trim.nonEmpty)
    lines.foreach(l => println("  " + l.trim))
    check("both halves are written", lines.length == 2)
    check("one of them is the mirror", lines.exists(_.contains(Mass.MirrorSuffix.trim)))
    check("on opposite sides", lines.exists(_.contains("-0.55")) && lines.exists(_.contains(" 0.55")))

    println("what the exported mass balance and the CG see")
    val model = flyableModel()
    val podBody = model.getAvl.getGeometry.getBodies.get(0)
    val ballast = podBody.createMass()
    ballast.setName("pod ballast"); ballast.setMass(0.1f)
    check("the mirrored half counts towards the total",
      near(model.getAllMasses.asScala.map(_.getMass).sum, 1.2))
    model.getAvl.getGeometry.calculateCenterOfMassFromMasses()
    check("and the CG stays on the centreline, as a mirrored pair should leave it",
      near(model.getAvl.getGeometry.getYref, 0.0))

    println("both halves reach the 3D view")
    val markers = MassMarkers.from(model)
    val storedMarker = MassMarkers.indexOf(markers, ballast).get
    val mirrorIndexes = MassMarkers.mirrorIndexes(markers)
    val virtualMarker = mirrorIndexes(storedMarker)
    check("the stored one and its mirror are both drawn", virtualMarker >= 0)
    check("the mirror is marked as derived, and only the stored one can be selected",
      markers(virtualMarker).virtual && !markers(storedMarker).virtual)
    check("they are on opposite sides",
      near(markers(virtualMarker).y, -markers(storedMarker).y))
    check("dragging the mirror moves the stored mass, reflected", {
      markers(virtualMarker).moveTo(1.3f, 0.6f, 0.05f)
      near(ballast.getY, -0.6) && near(ballast.getX, 1.3)
    })

    println("masses generated from volume")
    val auto = new AVLGeometry
    auto.getSurfaces.clear(); auto.getBodies.clear(); auto.getMasses.clear()
    auto.getSurfaces.add(wing(symmetric = true))
    auto.getBodies.add(pod())
    check("are generated", auto.autoMassesFromVolume())
    val storedMasses = auto.getMassesRecursive.asScala
    println("  stored: " + storedMasses.map(m => f"${m.getName}%s(${m.getY}%.2f)").mkString(", "))
    check("one per element, not one per side", storedMasses.size == 2)
    val effective = auto.getEffectiveMassesRecursive.asScala
    check("but both sides reach a generated model", effective.size == 4)
    check("and the mirrored halves balance about the centreline",
      near(effective.map(m => m.getMass * m.getY).sum, 0.0))
    val total = effective.map(_.getMass).sum
    check("running it again keeps the aircraft's weight", {
      auto.autoMassesFromVolume()
      near(auto.getEffectiveMassesRecursive.asScala.map(_.getMass).sum, total)
    })

    // A body a hair off the centreline is mirrored by the YDUPLICATE rule, but a mass at its centre
    // sits on the plane of symmetry and has no mirror. Counting on one anyway loses that body's other
    // half: its one mass has to weigh the whole thing.
    println("an element whose defined side balances on the plane of symmetry")
    val sloppy = new AVLGeometry
    sloppy.getSurfaces.clear(); sloppy.getBodies.clear(); sloppy.getMasses.clear()
    val nearlyCentred = pod(); nearlyCentred.setdY(1.0e-6f)
    sloppy.getBodies.add(nearlyCentred)
    val seed = sloppy.addMassAt(0f, 0f, 0f); seed.setMass(2.0f)
    sloppy.autoMassesFromVolume()
    val sloppyMasses = sloppy.getEffectiveMassesRecursive.asScala
    println(f"  ${sloppyMasses.size}%d masses, ${sloppyMasses.map(_.getMass).sum}%.4f kg")
    check("its one mass weighs both halves, so the weight is not lost",
      near(sloppyMasses.map(_.getMass).sum, 2.0))

    println(if (ok) "MASS_MIRROR_OK" else "MASS_MIRROR_FAIL")
    if (!ok) sys.exit(1)
  }

  /** A model with a mirrored pod and 1 kg on the centreline, so a lateral CG shift would show. */
  private def flyableModel(): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()
    val geometry = crrcsim.getAvl.getGeometry
    geometry.getSurfaces.clear(); geometry.getBodies.clear(); geometry.getMasses.clear()
    geometry.getBodies.add(pod())
    val fuselage = geometry.addMassAt(0.4f, 0f, 0f)
    fuselage.setName("airframe")
    fuselage.setMass(1.0f)
    crrcsim
  }
}
