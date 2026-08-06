/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.mass

import com.abajar.avleditor.crrcsim.{Battery, CRRCSim, DuctedFan, Engine, Propeller}
import scala.collection.JavaConverters._

/**
 * The shape a propulsion component has, for the 3D view.
 *
 * A battery is a brick, a motor is a cylinder and a propeller sweeps a disc, and until now the editor stated
 * only where each one's centre was — so whether a 105 mm pack fits behind the canard was a question the model
 * could not answer. These are the sizes it does state, in the length unit the rest of the model uses, so the
 * viewer can draw them in the same space as the geometry.
 *
 * `pointIndex` ties a shape to the mass marker at the same position, which is how the viewer knows which
 * shape belongs to the selection and where to put its handles. The mass itself stays a point at the centre of
 * the shape: nothing here changes the weight, the inertias or the centre of gravity.
 */
final case class ComponentShape(pointIndex: Int,
                                kind: String,
                                sizeX: Float,
                                sizeY: Float,
                                sizeZ: Float,
                                blades: Int,
                                /**
                                 * Which of the three sizes a drag may change. Per axis, not per shape: a
                                 * ducted fan's length is a drawing and can be dragged freely, while its bore
                                 * decides the exported thrust and must not be edited by eye.
                                 */
                                resizableAxes: Set[Int],
                                dimensionFields: Seq[String],
                                owner: AnyRef,
                                resizeTo: (Float, Float, Float) => Unit,
                                /** The smallest a side may be dragged to, in model units: one millimetre. */
                                minSize: Float = 0.001f) {

  def resizable: Boolean = resizableAxes.nonEmpty

  def resizable(axis: Int): Boolean = resizableAxes.contains(axis)
}

object ComponentShapes {

  val Box = "box"
  val Cylinder = "cylinder"
  val Disc = "disc"

  /** One millimetre expressed in the model's own length unit, asked of the model rather than worked out here. */
  def millimetre(units: com.abajar.avleditor.ModelUnits): Float = units.millimetre()

  /** The same, for a bare unit name — the checks state one directly. */
  def millimetre(lengthUnit: String): Float =
    new com.abajar.avleditor.ModelUnits(lengthUnit, "kg", "s").millimetre()

  /**
   * Every propulsion component whose shape the model states, sized in the model's length unit.
   *
   * The fuel tank is deliberately absent: it has a position and a mass like the others, but its shape is
   * whatever space it fills and the model says nothing about it. Inventing one would put a number in the 3D
   * view that the aircraft does not contain.
   */
  def from(crrcsim: CRRCSim, markers: IndexedSeq[MassMarker]): IndexedSeq[ComponentShape] = {
    if (crrcsim == null) return IndexedSeq.empty
    val mm = millimetre(
      Option(crrcsim.getAvl).map(_.units()).getOrElse(com.abajar.avleditor.ModelUnits.DEFAULTS))

    val power = Option(crrcsim.getConfig).flatMap(config => Option(config.getPower))
    power.map { p =>
      p.getBateries.asScala.toIndexedSeq.flatMap { battery =>
        batteryShape(battery, markers, mm) ++
          battery.getShafts.asScala.toIndexedSeq.flatMap { shaft =>
            shaft.getEngines.asScala.toIndexedSeq.flatMap(e => engineShape(e, markers, mm)) ++
              shaft.getPropellers.asScala.toIndexedSeq.flatMap(pr => propellerShape(pr, markers)) ++
              shaft.getDuctedFans.asScala.toIndexedSeq.flatMap(f => ductedFanShape(f, markers, mm))
          }
      }
    }.getOrElse(IndexedSeq.empty)
  }

  /** The pack, as a box on the aircraft's own axes: length down the fuselage, width across, height up. */
  private def batteryShape(battery: Battery, markers: IndexedSeq[MassMarker],
                           mm: Float): IndexedSeq[ComponentShape] =
    MassMarkers.indexOf(markers, battery).toIndexedSeq.map { index =>
      ComponentShape(index, Box,
        battery.getLengthMm * mm, battery.getWidthMm * mm, battery.getHeightMm * mm,
        blades = 0, resizableAxes = Set(0, 1, 2),
        dimensionFields = Seq("lengthMm", "widthMm", "heightMm"),
        owner = battery,
        resizeTo = (x, y, z) => {
          battery.setLengthMm(x / mm)
          battery.setWidthMm(y / mm)
          battery.setHeightMm(z / mm)
        },
        minSize = Battery.MIN_SIZE_MM * mm)
    }

  /** The motor can, as a cylinder about the thrust axis. Its two sizes across are the same diameter. */
  private def engineShape(engine: Engine, markers: IndexedSeq[MassMarker],
                          mm: Float): IndexedSeq[ComponentShape] =
    MassMarkers.indexOf(markers, engine).toIndexedSeq.map { index =>
      val diameter = engine.getDiameterMm * mm
      ComponentShape(index, Cylinder, engine.getLengthMm * mm, diameter, diameter,
        blades = 0, resizableAxes = Set(0, 1, 2),
        dimensionFields = Seq("lengthMm", "diameterMm"),
        owner = engine,
        resizeTo = (x, y, z) => {
          engine.setLengthMm(x / mm)
          // Either handle across the axis sets the diameter: a cylinder has one.
          engine.setDiameterMm(math.max(y, z) / mm)
        },
        minSize = Engine.MIN_SIZE_MM * mm)
    }

  /**
   * A ducted fan, as a cylinder of its bore over its length: the air path, drawn so it can be seen whether
   * the unit fits. It is the bore and not the housing, because the housing's outer diameter is on the listing
   * but not in the model, and drawing what is not stated is how a number nobody entered ends up looking
   * authoritative.
   */
  // (see ductedFanShape below)

  /**
   * The propeller, as the disc it sweeps.
   *
   * Nothing new is stated for it: the diameter is already a required field, already validated — a zero is
   * refused and one wider than the wingspan is reported as a units mistake — and the blade count is stated
   * too. Deliberately a disc and not a solid cylinder: a propeller has no stated thickness, and inventing one
   * to make the drawing look solid would show a number the model does not hold. The swept disc is also the
   * thing worth seeing, since it is what has to clear the ground and the fuselage.
   *
   * So it is not resizable here either. Its size is the diameter, which is a field with consequences for the
   * exported thrust; dragging it in the 3D view would be editing the propulsion by eye.
   */
  private def propellerShape(propeller: Propeller,
                             markers: IndexedSeq[MassMarker]): IndexedSeq[ComponentShape] =
    MassMarkers.indexOf(markers, propeller).toIndexedSeq
      .filter(_ => propeller.getD > 0f)
      .map { index =>
        ComponentShape(index, Disc, 0f, propeller.getD, propeller.getD,
          blades = math.max(2, propeller.getBlades), resizableAxes = Set.empty,
          dimensionFields = Nil, owner = propeller, resizeTo = (_, _, _) => ())
      }

  /**
   * A ducted fan, as the disc its blades sweep — the duct's inner diameter, which is the figure the model
   * states and the one the thrust follows from.
   *
   * Not a housing: the outer diameter and the duct's length are on the listing but not in the model, and
   * drawing a cylinder would mean inventing both. Not resizable either, for the propeller's reason — the bore
   * decides the exported thrust, so dragging it by eye would be editing the propulsion.
   */
  private def ductedFanShape(fan: DuctedFan, markers: IndexedSeq[MassMarker],
                             mm: Float): IndexedSeq[ComponentShape] =
    MassMarkers.indexOf(markers, fan).toIndexedSeq
      .filter(_ => fan.getInnerDiameterMm > 0f)
      .map { index =>
        val bore = fan.getInnerDiameterMm * mm
        ComponentShape(index, Cylinder, fan.getLengthMm * mm, bore, bore,
          blades = math.max(2, fan.getBlades),
          // Only the length. The bore is the disc the thrust is derived from, so dragging it would be
          // editing the propulsion by eye; the length is a drawing and nothing else.
          resizableAxes = Set(0),
          dimensionFields = Seq("lengthMm"),
          owner = fan,
          resizeTo = (x, _, _) => fan.setLengthMm(x / mm),
          minSize = DuctedFan.MIN_SIZE_MM * mm)
      }

  /**
   * A face drag changes the size by twice what the face moved, because the centre of the shape is the centre
   * of the mass and has to stay where it is: pushing one face out pushes its opposite out as well.
   *
   * Clamped so a drag inwards stops at a point rather than turning the box inside out. In the model's length
   * unit, like everything else the viewer works in.
   */
  def resizedExtent(current: Float, faceDelta: Float, minimum: Float): Float =
    math.max(minimum, current + 2f * faceDelta)
}
