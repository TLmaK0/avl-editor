/*
 * Verification for undoable 3D drag gestures (MultiFieldChangeCommand + UndoManager).
 * Run with:  sbt "test:runMain com.abajar.avleditor.undo.UndoDragCheck"
 */
package com.abajar.avleditor.undo

import com.abajar.avleditor.avl.geometry.{Body, Section}

object UndoDragCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def newSection(xle: Float, chord: Float): Section = {
    val s = new Section
    s.setXle(xle); s.setYle(0f); s.setZle(0f); s.setChord(chord)
    s
  }

  /** Mirrors AvlEditor.pushDrag: one call == one complete gesture (viewer reports on release). */
  private def drag(um: UndoManager, obj: AnyRef, desc: String, fields: Seq[String])(mutate: => Unit): Unit =
    MultiFieldChangeCommand.capture(obj, desc, fields)(mutate).foreach(um.push)

  private val sectionFields = Seq("Xle", "Yle", "Zle", "Chord")

  def main(args: Array[String]): Unit = {
    singleGestureIsOneStep()
    noOpGestureIsNotRecorded()
    twoDragsOnDifferentSections()
    dragThenPropertyEditUndoOrder()
    collisionPointDrag()

    println(if (ok) "UNDO_DRAG_OK" else "UNDO_DRAG_FAIL")
    if (!ok) sys.exit(1)
  }

  /** A gesture that moves and resizes a section undoes/redoes in a single step. */
  private def singleGestureIsOneStep(): Unit = {
    println("singleGestureIsOneStep")
    val um = new UndoManager
    val s = newSection(1.0f, 2.0f)

    drag(um, s, "Move section", sectionFields) { s.setXle(5.0f); s.setChord(3.0f) }

    check("one undo step for the gesture", um.canUndo)
    um.undo()
    check("undo restores Xle", s.getXle == 1.0f)
    check("undo restores Chord", s.getChord == 2.0f)
    check("history exhausted after one undo", !um.canUndo)

    um.redo()
    check("redo reapplies Xle", s.getXle == 5.0f)
    check("redo reapplies Chord", s.getChord == 3.0f)
  }

  /** A click without movement must not leave an empty entry in the history. */
  private def noOpGestureIsNotRecorded(): Unit = {
    println("noOpGestureIsNotRecorded")
    val um = new UndoManager
    val s = newSection(1.0f, 2.0f)

    drag(um, s, "Move section", sectionFields) { s.setXle(9.0f) }
    drag(um, s, "Move section", sectionFields) { s.setXle(9.0f) } // released where it was

    um.undo()
    check("no-op gesture not pushed (undo goes back to 1.0)", s.getXle == 1.0f)
    check("no leftover empty step", !um.canUndo)
  }

  /**
   * Regression: two consecutive gestures on *different* sections. Both share the same
   * description, and nothing is pushed in between (3D selection pushes no command), so
   * coalescing by description used to fold the second gesture into the first section's
   * command and lose the second section's move entirely.
   */
  private def twoDragsOnDifferentSections(): Unit = {
    println("twoDragsOnDifferentSections")
    val um = new UndoManager
    val a = newSection(1.0f, 2.0f)
    val b = newSection(10.0f, 2.0f)

    drag(um, a, "Move section", sectionFields) { a.setXle(5.0f) }
    drag(um, b, "Move section", sectionFields) { b.setXle(50.0f) }

    um.undo()
    check("first undo reverts section B", b.getXle == 10.0f)
    check("first undo leaves section A moved", a.getXle == 5.0f)

    um.undo()
    check("second undo reverts section A", a.getXle == 1.0f)
    check("both gestures were recorded", !um.canUndo)
  }

  /**
   * Dragging a collision point in the 3D view, undone in one step. Pins the field names the
   * editor passes to reflection ("x", "y", "z" on Pos): a typo there compiles fine and only
   * fails when the user drags.
   */
  private def collisionPointDrag(): Unit = {
    println("collisionPointDrag")
    val um = new UndoManager
    val pos = new com.abajar.avleditor.crrcsim.Pos
    pos.setX(0.1f); pos.setY(0.0f); pos.setZ(-0.05f)

    drag(um, pos, "Move collision point", Seq("x", "y", "z")) {
      pos.setX(0.6f); pos.setZ(-0.12f)
    }

    check("the gesture is recorded", um.canUndo)
    um.undo()
    check("undo restores x", pos.getX == 0.1f)
    check("undo restores z", pos.getZ == -0.05f)
    um.redo()
    check("redo reapplies the drag", pos.getX == 0.6f && pos.getZ == -0.12f)
  }

  /** A drag followed by a property edit must undo in reverse order (edit first, then drag). */
  private def dragThenPropertyEditUndoOrder(): Unit = {
    println("dragThenPropertyEditUndoOrder")
    val um = new UndoManager
    val body = new Body
    body.setdX(0f); body.setdY(0f); body.setdZ(0f)

    drag(um, body, "Move body", Seq("dX", "dY", "dZ")) { body.setdX(4.0f) }

    val field = classOf[Body].getDeclaredField("dY")
    field.setAccessible(true)
    val oldY = body.getdY
    body.setdY(7.0f)
    um.push(new PropertyChangeCommand(body, field, java.lang.Float.valueOf(oldY), java.lang.Float.valueOf(7.0f)))

    um.undo()
    check("undo reverts the property edit first", body.getdY == 0f && body.getdX == 4.0f)
    um.undo()
    check("undo then reverts the drag", body.getdX == 0f)
  }
}
