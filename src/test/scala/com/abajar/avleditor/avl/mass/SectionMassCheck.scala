/*
 * A section is where the wing's shape is defined, not a part that weighs anything: the weight belongs
 * on the surface. Models written before that keep their masses — they are moved, not dropped.
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.mass.SectionMassCheck"
 */
package com.abajar.avleditor.avl.mass

import com.abajar.avleditor.avl.AVL
import com.abajar.avleditor.avl.geometry.{Section, Surface}
import com.abajar.avleditor.crrcsim.{CRRCSim, CRRCSimFactory, CRRCSimRepository}
import com.abajar.avleditor.view.annotations.AvlEditor
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS
import java.io.File
import java.nio.file.Files
import scala.collection.JavaConverters._

object SectionMassCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Float, b: Double, tol: Double = 1e-4): Boolean = math.abs(a - b) < tol

  private def buttons(cls: Class[_]): Seq[ENABLE_BUTTONS] =
    cls.getAnnotation(classOf[AvlEditor]).buttons().toSeq

  /** What the tree shows under a node: the same reflection the editor uses. */
  private def childNodes(node: AnyRef): Seq[AnyRef] =
    node.getClass.getMethods.toSeq
      .filter(_.isAnnotationPresent(classOf[com.abajar.avleditor.view.annotations.AvlEditorNode]))
      .flatMap { method =>
        method.invoke(node) match {
          case list: java.util.List[_] => list.asScala.toSeq.map(_.asInstanceOf[AnyRef])
          case null => Nil
          case other => Seq(other)
        }
      }

  def main(args: Array[String]): Unit = {
    println("what each element offers")
    check("a surface can be given a mass", buttons(classOf[Surface]).contains(ENABLE_BUTTONS.ADD_MASS))
    check("a section cannot", !buttons(classOf[Section]).contains(ENABLE_BUTTONS.ADD_MASS))
    check("it can still be given a control", buttons(classOf[Section]).contains(ENABLE_BUTTONS.ADD_CONTROL))

    println("a model that kept masses on its sections")
    val geometry = new AVL().getGeometry
    val surface = geometry.getSurfaces.get(0)
    surface.setSymmetric(false)
    val section = surface.getSections.get(0)
    val onSection = section.addMassAt(0.3f, 0.15f, 0.02f)
    onSection.setName("rib")
    onSection.setMass(0.25f)
    val onSurface = surface.addMassAt(0.5f, 0.4f, 0f)
    onSurface.setMass(0.75f)

    val totalBefore = geometry.getEffectiveMassesRecursive.asScala.map(_.getMass).sum
    val moved = geometry.moveSectionMassesToSurfaces()
    check("the mass is moved", moved == 1)
    check("onto its surface", surface.getMasses.asScala.exists(_ eq onSection))
    check("and off the section", section.getMasses.isEmpty)
    check("nothing about it changes: same weight, same place",
      near(onSection.getMass, 0.25) && near(onSection.getX, 0.3) && near(onSection.getY, 0.15) &&
        near(onSection.getZ, 0.02))
    check("so the aircraft weighs the same",
      near(geometry.getEffectiveMassesRecursive.asScala.map(_.getMass).sum, totalBefore))
    check("moving them again is a no-op", geometry.moveSectionMassesToSurfaces() == 0)

    println("the tree")
    check("a section shows no masses", childNodes(section).forall(!_.isInstanceOf[Mass]))
    check("the surface shows both", childNodes(surface).count(_.isInstanceOf[Mass]) == 2)

    println("mirroring follows the surface, as it did on the section")
    val mirrored = new AVL().getGeometry
    mirrored.initParents()  // what a load does, and what a section needs to know its mirror plane
    val wing = mirrored.getSurfaces.get(0)
    wing.setSymmetric(true)
    val rib = wing.getSections.get(0).addMassAt(0.3f, 0.2f, 0f)
    rib.setMass(0.1f)
    val beforeMirror = mirrored.getEffectiveMassesRecursive.asScala.map(_.getMass).sum
    mirrored.moveSectionMassesToSurfaces()
    check("the implied mirror survives the move",
      wing.hasVirtualMirror(rib) &&
        near(mirrored.getEffectiveMassesRecursive.asScala.map(_.getMass).sum, beforeMirror))

    println("loading a file that has them")
    val model = new CRRCSimFactory().create()
    val loadSurface = model.getAvl.getGeometry.getSurfaces.get(0)
    val loadSection = loadSurface.getSections.get(0)
    val legacy = loadSection.addMassAt(0.2f, 0.1f, 0f)
    legacy.setName("legacy rib")
    legacy.setMass(0.4f)
    val file = Files.createTempFile("avleditor-sectionmass", ".avle").toFile
    new CRRCSimRepository().storeToFile(file, model)
    val reloaded = new CRRCSimRepository().restoreFromFile(file)
    val reloadedGeometry = reloaded.getAvl.getGeometry
    val reloadedSurface = reloadedGeometry.getSurfaces.get(0)
    check("the load moves it, so no model reaches the editor with a mass on a section",
      reloadedSurface.getSections.asScala.forall(_.getMasses.isEmpty))
    check("and the weight is still there",
      reloadedSurface.getMasses.asScala.exists(m => m.getName == "legacy rib" && near(m.getMass, 0.4)))
    // The load also restores the parent links, so mirroring is right without the caller remembering.
    check("a loaded section knows which plane it mirrors about",
      reloadedSurface.getSections.get(0).mirrorPlaneY() == reloadedSurface.mirrorPlaneY())
    file.delete()

    println(if (ok) "SECTION_MASS_OK" else "SECTION_MASS_FAIL")
    if (!ok) sys.exit(1)
  }
}
