/*
 * Resizing a component in the 3D view: the centre of the shape is the centre of the mass, so a face drag has
 * to grow the shape about that centre and leave the weight exactly where it was. And a gesture is one
 * undoable step, as a position drag is — three fields changing is not three undos.
 * Run with:  sbt "test:runMain com.abajar.avleditor.mass.ComponentResizeCheck"
 */
package com.abajar.avleditor.mass

import com.abajar.avleditor.crrcsim._
import com.abajar.avleditor.undo.{MultiFieldChangeCommand, UndoManager}

object ComponentResizeCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Double, b: Double, tol: Double = 1e-5): Boolean = math.abs(a - b) < tol

  private def model(): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()
    val battery = new Battery
    battery.setMass(0.18f)
    battery.getPos.setX(0.3f); battery.getPos.setZ(0.02f)
    val shaft = new Shaft
    val engine = new Engine
    engine.setMass(0.06f)
    engine.getPos.setX(0.05f)
    shaft.getEngines.add(engine)
    battery.getShafts.add(shaft)
    crrcsim.getConfig.getPower.getBateries.add(battery)
    crrcsim
  }

  private def shapesOf(crrcsim: CRRCSim) = {
    val markers = MassMarkers.from(crrcsim)
    (markers, ComponentShapes.from(crrcsim, markers))
  }

  /** Mirrors what AvlEditor does on release: apply the sizes the viewer reports, as one undoable step. */
  private def applyResize(um: UndoManager, shape: ComponentShape,
                          sizeX: Float, sizeY: Float, sizeZ: Float): Unit =
    MultiFieldChangeCommand
      .capture(shape.owner, "Resize", shape.dimensionFields)(shape.resizeTo(sizeX, sizeY, sizeZ))
      .foreach(um.push)

  def main(args: Array[String]): Unit = {
    println("a face drag grows the shape about its centre")
    val crrcsim = model()
    val (markers, shapes) = shapesOf(crrcsim)
    val box = shapes.find(_.kind == ComponentShapes.Box).get
    val marker = markers(box.pointIndex)
    val (centreX, centreY, centreZ) = (marker.x, marker.y, marker.z)

    // Dragging the +x face out by 10 mm: the length grows by 20 mm and the centre does not move.
    val faceDelta = 0.010f
    val grown = ComponentShapes.resizedExtent(box.sizeX, faceDelta, box.minSize)
    println(f"  length ${box.sizeX}%.4f -> $grown%.4f after a ${faceDelta}%.4f face drag")
    check("the length grows by twice the drag", near(grown, 0.095f))

    val um = new UndoManager
    applyResize(um, box, grown, box.sizeY, box.sizeZ)
    val battery = crrcsim.getConfig.getPower.getBateries.get(0)
    check("and the model states it in millimetres", near(battery.getLengthMm, 95f, 1e-3))
    check("the mass has not moved",
      battery.getPos.getX == centreX && battery.getPos.getY == centreY &&
        battery.getPos.getZ == centreZ)

    println("so the faces end up half a size either side of the mass, still")
    val (_, afterShapes) = shapesOf(crrcsim)
    val afterBox = afterShapes.find(_.kind == ComponentShapes.Box).get
    check("the front face", near(centreX + afterBox.sizeX / 2, 0.3f + 0.0475f))
    check("and the back one", near(centreX - afterBox.sizeX / 2, 0.3f - 0.0475f))

    println("one gesture, one undo")
    check("the history has a single step", um.canUndo && um.undoDescription.isDefined)
    um.undo()
    check("undoing puts all three dimensions back",
      near(battery.getLengthMm, 75f) && near(battery.getWidthMm, 35f) && near(battery.getHeightMm, 22f))
    check("and there is nothing left to undo", !um.canUndo)
    um.redo()
    check("redo brings the resize back", near(battery.getLengthMm, 95f, 1e-3))

    println("a drag that changes nothing is not a step")
    val um2 = new UndoManager
    val (_, sameShapes) = shapesOf(crrcsim)
    val same = sameShapes.find(_.kind == ComponentShapes.Box).get
    applyResize(um2, same, same.sizeX, same.sizeY, same.sizeZ)
    check("a click without movement leaves the history alone", !um2.canUndo)

    println("dragging a face inwards past nothing")
    val squashed = ComponentShapes.resizedExtent(same.sizeY, -10f, same.minSize)
    applyResize(um2, same, same.sizeX, squashed, same.sizeZ)
    check("stops at one millimetre rather than turning the box inside out",
      near(battery.getWidthMm, Battery.MIN_SIZE_MM, 1e-3))
    check("which is still a positive size", battery.getWidthMm > 0f)

    println("the motor is round: one diameter, both sizes across")
    val cylinder = shapesOf(crrcsim)._2.find(_.kind == ComponentShapes.Cylinder).get
    val engine = crrcsim.getConfig.getPower.getBateries.get(0).getShafts.get(0).getEngines.get(0)
    // The viewer moves sizeY and sizeZ together for a cylinder; the model takes one diameter either way.
    applyResize(um2, cylinder, cylinder.sizeX, 0.040f, 0.040f)
    check("dragging across it sets the diameter", near(engine.getDiameterMm, 40f, 1e-3))
    val (_, roundShapes) = shapesOf(crrcsim)
    val round = roundShapes.find(_.kind == ComponentShapes.Cylinder).get
    check("and it stays round afterwards", near(round.sizeY, round.sizeZ))
    check("its length is untouched", near(engine.getLengthMm, 30f))

    println("what a resize does not touch")
    crrcsim.calculate()
    val cg = crrcsim.getCenterOfMass.getX
    val mass = crrcsim.getConfig.getMass_inertia.getMass
    applyResize(um2, shapesOf(crrcsim)._2.find(_.kind == ComponentShapes.Box).get, 0.2f, 0.12f, 0.09f)
    crrcsim.calculate()
    check("not the centre of gravity", crrcsim.getCenterOfMass.getX == cg)
    check("and not the weight", crrcsim.getConfig.getMass_inertia.getMass == mass)

    println(if (ok) "COMPONENT_RESIZE_OK" else "COMPONENT_RESIZE_FAIL")
    if (!ok) sys.exit(1)
  }
}
