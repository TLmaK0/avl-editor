/*
 * Del deletes the selected element, and only what the model says can be deleted: the key does the
 * same as the Delete button, so it cannot delete something the button would refuse.
 * Run with:  sbt "test:runMain com.abajar.avleditor.swt.DeleteKeyCheck"
 */
package com.abajar.avleditor.swt

import com.abajar.avleditor.avl.AVLGeometry
import com.abajar.avleditor.avl.geometry.{Section, Surface}
import com.abajar.avleditor.avl.mass.Mass
import com.abajar.avleditor.crrcsim.Wheel
import com.abajar.avleditor.view.annotations.AvlEditor
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS
import org.eclipse.swt.SWT

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

    println(if (ok) "DELETE_KEY_OK" else "DELETE_KEY_FAIL")
    if (!ok) sys.exit(1)
  }
}
