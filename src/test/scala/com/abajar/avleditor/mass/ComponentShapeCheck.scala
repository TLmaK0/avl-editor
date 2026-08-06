/*
 * A battery is a brick, a motor is a cylinder and a propeller sweeps a disc. The editor used to state only
 * where each centre was, so whether a 105 mm pack fits where it is being put was a question the model could
 * not answer. Pinned here: the sizes, the unit they are drawn in, that the shape is centred on the mass, and
 * that nothing about the shape touches the weight.
 * Run with:  sbt "test:runMain com.abajar.avleditor.mass.ComponentShapeCheck"
 */
package com.abajar.avleditor.mass

import com.abajar.avleditor.crrcsim._

object ComponentShapeCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Double, b: Double, tol: Double = 1e-5): Boolean = math.abs(a - b) < tol

  /** A model with one battery, one motor on its shaft and one propeller, each somewhere of its own. */
  private def model(lengthUnit: String = "m"): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()
    crrcsim.getAvl.setLengthUnit(lengthUnit)
    val power = crrcsim.getConfig.getPower
    val battery = new Battery
    battery.setMass(0.18f)
    battery.getPos.setX(0.3f); battery.getPos.setY(0f); battery.getPos.setZ(0.02f)
    val shaft = new Shaft
    val engine = new Engine
    engine.setMass(0.06f)
    engine.getPos.setX(0.05f)
    val propeller = new Propeller
    propeller.setD(0.24f)
    propeller.setBlades(3)
    propeller.getPos.setX(0.01f)
    shaft.getEngines.add(engine)
    shaft.getPropellers.add(propeller)
    battery.getShafts.add(shaft)
    power.getBateries.add(battery)
    crrcsim
  }

  private def shapes(crrcsim: CRRCSim) = {
    val markers = MassMarkers.from(crrcsim)
    (markers, ComponentShapes.from(crrcsim, markers))
  }

  def main(args: Array[String]): Unit = {
    println("what a fresh battery and motor say about themselves")
    val battery = new Battery
    println(f"  battery ${battery.getLengthMm}%.0f x ${battery.getWidthMm}%.0f x ${battery.getHeightMm}%.0f mm")
    // A 3S 1000 mAh pack, about 90 g: a real small battery rather than a round number.
    check("the battery defaults to a small pack",
      battery.getLengthMm == 75f && battery.getWidthMm == 35f && battery.getHeightMm == 22f)
    val engine = new Engine
    println(f"  motor ${engine.getDiameterMm}%.0f dia x ${engine.getLengthMm}%.0f mm")
    check("the motor defaults to a 2212-class can",
      engine.getDiameterMm == 28f && engine.getLengthMm == 30f)
    battery.setLengthMm(0f)
    check("a size cannot be dragged down to nothing", battery.getLengthMm == Battery.MIN_SIZE_MM)

    println("the shapes the 3D view is given")
    val (markers, list) = shapes(model())
    list.foreach(s => println(f"  ${s.kind}%-8s at point ${s.pointIndex}%d: " +
      f"${s.sizeX}%.4f x ${s.sizeY}%.4f x ${s.sizeZ}%.4f (model units)"))
    check("one for the battery, one for the motor, one for the propeller", list.length == 3)
    check("the battery is a box", list.exists(_.kind == ComponentShapes.Box))
    check("the motor is a cylinder", list.exists(_.kind == ComponentShapes.Cylinder))
    check("the propeller is a disc", list.exists(_.kind == ComponentShapes.Disc))

    println("each shape belongs to the mass it is drawn around")
    list.foreach { shape =>
      check(f"${shape.kind}%s points at a real marker",
        shape.pointIndex >= 0 && shape.pointIndex < markers.length)
    }
    val boxShape = list.find(_.kind == ComponentShapes.Box).get
    val boxMarker = markers(boxShape.pointIndex)
    check("the box is the battery's, at the battery's position",
      boxMarker.x == 0.3f && boxMarker.z == 0.02f)
    // The centre of the shape IS the mass, so the faces sit at plus and minus half of each size.
    check("so its faces are half a size either side of the mass",
      near(boxMarker.x + boxShape.sizeX / 2, 0.3f + 0.0375f))

    println("millimetres, drawn in the model's own length unit")
    check("in metres, 75 mm is 0.075", near(boxShape.sizeX, 0.075f))
    val (_, inCm) = shapes(model("cm"))
    check("in centimetres it is 7.5", near(inCm.find(_.kind == ComponentShapes.Box).get.sizeX, 7.5f))
    val (_, inInches) = shapes(model("in"))
    check("in inches it is 2.953", near(inInches.find(_.kind == ComponentShapes.Box).get.sizeX, 2.9527559f, 1e-4))
    check("one millimetre in metres", near(ComponentShapes.millimetre("m"), 0.001f))
    check("in centimetres", near(ComponentShapes.millimetre("cm"), 0.1f))
    check("in inches", near(ComponentShapes.millimetre("in"), 0.03937008f, 1e-6))

    println("the motor is round: one diameter, two sizes across")
    val cylinder = list.find(_.kind == ComponentShapes.Cylinder).get
    check("both of its cross sizes are the diameter",
      near(cylinder.sizeY, 0.028f) && near(cylinder.sizeZ, 0.028f))
    check("and its length is along the thrust axis", near(cylinder.sizeX, 0.030f))

    println("the propeller states nothing new")
    val disc = list.find(_.kind == ComponentShapes.Disc).get
    check("its size is the diameter the model already holds", near(disc.sizeY, 0.24f))
    check("it has no thickness, because nothing states one", disc.sizeX == 0f)
    check("it shows the blades the model states", disc.blades == 3)
    // Its diameter decides the exported thrust, so dragging it in the 3D view would be editing the
    // propulsion by eye. The box and the cylinder are shapes; this is a performance figure.
    check("and it is not resizable by hand", !disc.resizable)
    check("while the battery and the motor are",
      boxShape.resizable && cylinder.resizable)

    println("a propeller with no diameter is not drawn as a point")
    val noDiameter = model()
    noDiameter.getConfig.getPower.getBateries.get(0).getShafts.get(0).getPropellers.get(0).setD(0f)
    check("nothing is drawn for it",
      ComponentShapes.from(noDiameter, MassMarkers.from(noDiameter))
        .count(_.kind == ComponentShapes.Disc) == 0)

    println("the fuel tank is deliberately absent")
    val tanked = model()
    val tank = new FuelTank
    tank.setContents(0.2f)
    tank.getPos.setX(0.4f)
    tanked.getConfig.getPower.getFuelTanks.add(tank)
    val (tankMarkers, tankShapes) = shapes(tanked)
    check("it still has a mass marker, so its position can be moved",
      tankMarkers.exists(_.node eq tank))
    // A tank's shape is whatever space it fills, and the model states nothing about it.
    check("but no shape is invented for it", tankShapes.length == 3)

    println("resizing about a fixed centre")
    // Pushing one face out has to push its opposite out too, or the mass would no longer be at the centre
    // of the volume.
    check("the size grows by twice what the face moved",
      near(ComponentShapes.resizedExtent(0.075f, 0.01f, 0.001f), 0.095f))
    check("and shrinks by twice, the other way",
      near(ComponentShapes.resizedExtent(0.075f, -0.01f, 0.001f), 0.055f))
    check("a drag past nothing stops at the minimum",
      near(ComponentShapes.resizedExtent(0.075f, -1f, 0.001f), 0.001f))

    println("what a shape does to the aircraft: nothing")
    val weighed = model()
    weighed.calculate()
    val massBefore = weighed.getConfig.getMass_inertia.getMass
    val ixxBefore = weighed.getConfig.getMass_inertia.getI_xx
    val cgBefore = weighed.getCenterOfMass.getX
    val pack = weighed.getConfig.getPower.getBateries.get(0)
    pack.setLengthMm(200f); pack.setWidthMm(120f); pack.setHeightMm(90f)
    weighed.calculate()
    check("a much bigger box leaves the mass alone",
      weighed.getConfig.getMass_inertia.getMass == massBefore)
    check("and the inertias", weighed.getConfig.getMass_inertia.getI_xx == ixxBefore)
    check("and the centre of gravity", weighed.getCenterOfMass.getX == cgBefore)

    println(if (ok) "COMPONENT_SHAPE_OK" else "COMPONENT_SHAPE_FAIL")
    if (!ok) sys.exit(1)
  }
}
