/*
 * Dragging a mass along one axis must follow that axis as it appears on screen. The viewer is always
 * looking at the model from an angle, so "horizontal means x" moves the mass the wrong way.
 * Run with:  sbt "test:runMain com.abajar.avleditor.swt.AxisDragCheck"
 */
package com.abajar.avleditor.swt

object AxisDragCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Float, b: Double, tol: Double = 1e-3): Boolean = math.abs(a - b) < tol

  def main(args: Array[String]): Unit = {
    // The handle is 0.1 m along the axis and appears 50 px to the right of the mass.
    val unit = 0.1f
    println("an axis lying along the screen")
    check("dragging the full 50 px moves the whole 0.1 m",
      near(Viewer3DGL.axisDragDelta(50f, 0f, 50f, 0f, unit), 0.1))
    check("half the distance moves half as far",
      near(Viewer3DGL.axisDragDelta(50f, 0f, 25f, 0f, unit), 0.05))
    check("dragging the other way moves back",
      near(Viewer3DGL.axisDragDelta(50f, 0f, -25f, 0f, unit), -0.05))
    check("dragging across the axis does not move it",
      near(Viewer3DGL.axisDragDelta(50f, 0f, 0f, 40f, unit), 0.0))

    println("an axis running diagonally, as a rotated view shows it")
    // Handle at (30, 40) px: 50 px long. A drag along it by the same 50 px is the full unit.
    check("a drag along the diagonal moves the whole unit",
      near(Viewer3DGL.axisDragDelta(30f, 40f, 30f, 40f, unit), 0.1))
    check("a drag perpendicular to it moves nothing",
      near(Viewer3DGL.axisDragDelta(30f, 40f, 40f, -30f, unit), 0.0))
    check("only the part along the axis counts",
      near(Viewer3DGL.axisDragDelta(30f, 40f, 30f, 40f + 100f, unit), 0.1 + 100 * 40 / 2500.0 * 0.1))

    println("an axis pointing into the screen")
    // Nearly end-on: a couple of pixels stand for the whole 0.1 m, so a pixel of mouse movement
    // would fling the mass. Refusing is the honest answer; the view can be rotated.
    check("cannot be dragged", near(Viewer3DGL.axisDragDelta(2f, 1f, 40f, 40f, unit), 0.0))
    check("exactly end-on cannot either", near(Viewer3DGL.axisDragDelta(0f, 0f, 40f, 40f, unit), 0.0))
    check("just past the threshold can be", Viewer3DGL.axisDragDelta(7f, 0f, 7f, 0f, unit) > 0f)

    println("scale")
    check("a bigger model moves further for the same drag",
      Viewer3DGL.axisDragDelta(50f, 0f, 50f, 0f, 2.0f) > Viewer3DGL.axisDragDelta(50f, 0f, 50f, 0f, 0.1f))

    println(if (ok) "AXIS_DRAG_OK" else "AXIS_DRAG_FAIL")
    if (!ok) sys.exit(1)
  }
}
