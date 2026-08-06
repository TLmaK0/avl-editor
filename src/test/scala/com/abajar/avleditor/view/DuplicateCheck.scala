/*
 * Duplicating a node brings its whole subtree, beside the original, and it is generic for the same reason the
 * delete is: it finds the parent's list by reflection. The traps are the ones a blind copy walks into — names
 * that AVL reads must be unique, names that mean "the same control" must not be, and the transient links a copy
 * loses have to be restored or a duplicated wing stops mirroring its masses in silence.
 * Run with:  sbt "test:runMain com.abajar.avleditor.view.DuplicateCheck"
 */
package com.abajar.avleditor.view

import com.abajar.avleditor.TestAircraft
import com.abajar.avleditor.avl.geometry.{Body, Control, Section, Surface}
import com.abajar.avleditor.crrcsim.CRRCSim
import com.abajar.avleditor.view.avl.{DeepCopy, SelectorMutableTreeNode}
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS
import scala.collection.JavaConverters._

object DuplicateCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def duplicate(node: Any, parent: Any): Unit = ENABLE_BUTTONS.DUPLICATE.click(node, parent)

  def main(args: Array[String]): Unit = {
    println("a wing, with its sections, controls and masses")
    val model = TestAircraft.conventional()
    val geometry = model.getAvl.getGeometry
    val wing = geometry.getSurfaces.get(0)
    val sections = wing.getSections.size
    val controls = wing.getSections.asScala.map(_.getControls.size).sum
    duplicate(wing, geometry)

    check("there are two surfaces where there was one",
      geometry.getSurfaces.asScala.count(_.getName.startsWith("wing")) == 2)
    val copy = geometry.getSurfaces.asScala.find(s => s.getName != "wing" && s.getName.startsWith("wing")).get
    println(s"  the copy is called '${copy.getName}'")
    check("it lands next to the original",
      geometry.getSurfaces.indexOf(copy) == geometry.getSurfaces.indexOf(wing) + 1)
    check("with all its sections", copy.getSections.size == sections)
    check("and all their controls", copy.getSections.asScala.map(_.getControls.size).sum == controls)
    check("and the same shape", copy.getSections.get(1).getYle == wing.getSections.get(1).getYle)

    println("but it is a different aeroplane part, not the same one twice")
    copy.getSections.get(0).setChord(0.30f)
    check("editing the copy leaves the original alone", wing.getSections.get(0).getChord != 0.30f)
    copy.getMasses.asScala.headOption.foreach(_.setMass(9f))
    check("and so does editing its masses",
      wing.getMasses.asScala.headOption.forall(_.getMass != 9f))

    println("the names AVL reads are made unique")
    check("the copy is not called what the original is", copy.getName != wing.getName)
    // A control's name is what makes two sections part of the same control: renaming it would split an
    // aileron into two half-ailerons that move independently.
    val originalControl = wing.getSections.asScala.flatMap(_.getControls.asScala).head.getName
    val copiedControl = copy.getSections.asScala.flatMap(_.getControls.asScala).head.getName
    println(s"  the control is called '$copiedControl' in both")
    check("but the control keeps its name, because that is what makes it one control",
      copiedControl == originalControl)

    println("a duplicated wing still knows itself, so its masses still mirror")
    // The parent links are transient and do not survive a copy. Without them a section does not know which
    // plane it mirrors about, and a mass on a symmetric wing quietly stops being mirrored.
    geometry.initParents()
    check("its sections know their surface",
      copy.getSections.asScala.forall(_.getParentSurface eq copy))
    check("and it mirrors, being symmetric", copy.isSymmetric && copy.mirrorPlaneY != null)

    println("a body gets its own name and its own profile file")
    val body = geometry.createBody()
    body.setName("fuselage"); body.setBFILE("fuselage.dat")
    duplicate(body, geometry)
    val bodyCopy = geometry.getBodies.asScala.find(_ ne body).get
    println(s"  the copy is '${bodyCopy.getName}' with profile '${bodyCopy.getBFILE}'")
    check("a name of its own", bodyCopy.getName != body.getName)
    // Two bodies sharing a BFILE would overwrite each other's profile on export.
    check("and a profile file of its own", bodyCopy.getBFILE != body.getBFILE)
    // AVL opens the profile by name from the directory beside the .avl, so the file name carries no spaces.
    check("named after itself, without a space in the file name",
      bodyCopy.getBFILE == bodyCopy.getName.replaceAll("\\s+", "_") + ".dat" &&
        !bodyCopy.getBFILE.contains(" "))

    println("a shaft brings its motor, its propeller and their positions")
    val battery = model.getConfig.getPower.getBateries.get(0)
    val shaft = battery.getShafts.get(0)
    shaft.getPos.setX(0.12f)
    duplicate(shaft, battery)
    check("there are two shafts", battery.getShafts.size == 2)
    val shaftCopy = battery.getShafts.get(1)
    check("the copy carries the motor", shaftCopy.getEngines.size == shaft.getEngines.size)
    check("and the propeller", shaftCopy.getPropellers.size == shaft.getPropellers.size)
    check("and the assembly's own position", shaftCopy.getPos.getX == 0.12f)
    check("and the motor's place within it",
      shaftCopy.getEngines.get(0).getPos.getX == shaft.getEngines.get(0).getPos.getX)
    check("but a motor of its own", !(shaftCopy.getEngines.get(0) eq shaft.getEngines.get(0)))

    println("a section, a control and a mass, one at a time")
    val before = wing.getSections.size
    duplicate(wing.getSections.get(0), wing)
    check("a section duplicates within its surface", wing.getSections.size == before + 1)
    val section = wing.getSections.get(0)
    val controlsBefore = section.getControls.size
    duplicate(new Control, section) // not in the list: nothing must happen
    check("something that is in no list is not duplicated", section.getControls.size == controlsBefore)

    println("what the tree offers it on")
    val offering = Seq(classOf[Surface], classOf[Section], classOf[Control], classOf[Body],
      classOf[com.abajar.avleditor.avl.mass.Mass], classOf[com.abajar.avleditor.crrcsim.Battery],
      classOf[com.abajar.avleditor.crrcsim.Shaft], classOf[com.abajar.avleditor.crrcsim.Engine],
      classOf[com.abajar.avleditor.crrcsim.Propeller], classOf[com.abajar.avleditor.crrcsim.DuctedFan],
      classOf[com.abajar.avleditor.crrcsim.Wheel], classOf[com.abajar.avleditor.crrcsim.EngineData])
    offering.foreach { cls =>
      val buttons = Option(cls.getAnnotation(classOf[com.abajar.avleditor.view.annotations.AvlEditor]))
        .map(_.buttons.toSeq).getOrElse(Nil)
      check(s"${cls.getSimpleName} offers it",
        buttons.contains(ENABLE_BUTTONS.DUPLICATE) && buttons.contains(ENABLE_BUTTONS.DELETE))
    }

    println("and what a copy is, underneath")
    val deep = DeepCopy.of(wing)
    check("a deep copy is not the same object", !(deep eq wing))
    check("nor are the things inside it",
      !(deep.getSections.get(0) eq wing.getSections.get(0)))
    check("something uncopyable is refused, not half-copied", DeepCopy.of(new Object) == null)
    check("and the refusal says what could not be copied",
      DeepCopy.whyNot(wing).contains("Surface"))

    println(if (ok) "DUPLICATE_OK" else "DUPLICATE_FAIL")
    if (!ok) sys.exit(1)
  }
}
