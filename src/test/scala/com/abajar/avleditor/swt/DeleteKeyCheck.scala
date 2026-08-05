/*
 * Del deletes the selected element, and only what the model says can be deleted: the key does the
 * same as the Delete button, so it cannot delete something the button would refuse.
 * Run with:  sbt "test:runMain com.abajar.avleditor.swt.DeleteKeyCheck"
 */
package com.abajar.avleditor.swt

import com.abajar.avleditor.avl.AVLGeometry
import com.abajar.avleditor.avl.geometry.{Section, Surface}
import com.abajar.avleditor.avl.mass.Mass
import com.abajar.avleditor.crrcsim._
import com.abajar.avleditor.view.annotations.AvlEditor
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS
import org.eclipse.swt.SWT
import scala.collection.JavaConverters._

object DeleteKeyCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** Whether the Delete button is enabled for a node, which is what the key follows. */
  private def deletable(cls: Class[_]): Boolean =
    Option(cls.getAnnotation(classOf[AvlEditor]))
      .exists(_.buttons().contains(ENABLE_BUTTONS.DELETE))

  def main(args: Array[String]): Unit = {
    println("which keypress deletes")
    check("Del, while the Delete button is enabled",
      MainWindow.deletesSelection(SWT.DEL, deleteEnabled = true))
    check("not while it is disabled: the node decides, not the keyboard",
      !MainWindow.deletesSelection(SWT.DEL, deleteEnabled = false))
    check("not Backspace", !MainWindow.deletesSelection(SWT.BS, deleteEnabled = true))
    check("not Enter", !MainWindow.deletesSelection(SWT.CR, deleteEnabled = true))
    check("not a letter", !MainWindow.deletesSelection('d'.toInt, deleteEnabled = true))

    println("what the button is enabled for, and so the key too")
    check("a mass", deletable(classOf[Mass]))
    check("a surface", deletable(classOf[Surface]))
    check("a section", deletable(classOf[Section]))
    check("a collision point", deletable(classOf[Wheel]))
    // The geometry itself is the aircraft: there is nothing it would mean to delete it.
    check("but not the geometry", !deletable(classOf[AVLGeometry]))

    // Everything the tree lists can be added; everything that can be added has to be removable, or the
    // only way out of a wrong click is to edit the file by hand.
    println("the propulsion, which could be added and never removed")
    Seq(classOf[Battery], classOf[Shaft], classOf[Engine], classOf[CombustionEngine],
      classOf[Propeller], classOf[SimpleTrust], classOf[FuelTank], classOf[EngineData])
      .foreach(cls => check(s"a ${cls.getSimpleName}", deletable(cls)))
    // A component's own position and gearing are not list items: without them it is broken, not lighter.
    check("but not a position", !deletable(classOf[Pos]))
    check("nor a gearing", !deletable(classOf[Gearing]))

    println("and deleting actually removes it, whatever holds it")
    val model = new CRRCSimFactory().create()
    val power = model.getConfig.getPower
    power.getBateries.clear()
    val battery = power.createBattery()
    battery.createShaft()
    val shaft = battery.getShafts.get(0)
    val engine = shaft.createEngine()
    val propeller = shaft.createPropeller()
    val trust = shaft.createSimpleTrust()
    val tank = power.createFuelTank()
    val data = new EngineData
    engine.getData.add(data)

    def delete(node: Any, parent: Any): Unit = ENABLE_BUTTONS.DELETE.click(node, parent)

    delete(trust, shaft)
    check("a Simple Trust, the one that could not be", shaft.getSimpleTrusts.isEmpty)
    delete(data, engine)
    check("a data row", engine.getData.isEmpty)
    delete(propeller, shaft)
    check("a propeller", shaft.getPropellers.isEmpty)
    delete(engine, shaft)
    check("an engine", shaft.getEngines.isEmpty)
    delete(tank, power)
    check("a fuel tank", power.getFuelTanks.isEmpty)
    delete(shaft, battery)
    check("a shaft", battery.getShafts.isEmpty)
    delete(battery, power)
    check("a battery", power.getBateries.isEmpty)

    // What the tree already handled has to keep working: the generic path replaced the special cases.
    val geometry = model.getAvl.getGeometry
    val surface = geometry.getSurfaces.get(0)
    val section = surface.getSections.get(0)
    val mass = surface.createMass()
    delete(mass, surface)
    check("a mass on a surface", surface.getMasses.isEmpty)
    delete(section, surface)
    check("a section", !surface.getSections.asScala.exists(_ eq section))
    delete(surface, geometry)
    check("a surface", !geometry.getSurfaces.asScala.exists(_ eq surface))
    val wheel = new Wheel
    model.getWheels.add(wheel)
    delete(wheel, model)
    check("a collision point", !model.getWheels.asScala.exists(_ eq wheel))

    println(if (ok) "DELETE_KEY_OK" else "DELETE_KEY_FAIL")
    if (!ok) sys.exit(1)
  }
}
