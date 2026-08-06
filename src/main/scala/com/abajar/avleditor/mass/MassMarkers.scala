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

import com.abajar.avleditor.avl.mass.{Mass, MassObject}
import com.abajar.avleditor.crrcsim.{CRRCSim, Pos, Shaft}
import scala.collection.JavaConverters._

/**
 * One mass as the 3D view needs it: where it is, how heavy it is, and how to move it.
 *
 * `node` is the tree node that owns the mass and `position` the object that holds the coordinates —
 * the same object for a [[Mass]], the component's [[Pos]] for a propulsion component. Both are kept
 * because the tree can select either, and `position` is what an undoable drag records.
 *
 * `moveTo` writes to the model, so a drag in the viewer changes the aircraft rather than a copy of
 * it.
 *
 * A `virtual` marker is the mirrored half of a mass on a mirrored element: nothing stores it, so it
 * follows whichever half is moved and it appears in the aircraft only when a model is generated.
 * Both halves of such a pair share the same `node` and `mirrorPlaneY`, which is how the viewer knows
 * to move them together.
 */
case class MassMarker(node: AnyRef,
                      position: AnyRef,
                      label: String,
                      mass: Float,
                      x: Float,
                      y: Float,
                      z: Float,
                      moveTo: (Float, Float, Float) => Unit,
                      virtual: Boolean = false,
                      mirrorPlaneY: Option[Float] = None)

/**
 * Every mass the model states a position for, as a flat list for the 3D view.
 *
 * A component that states no mass is listed all the same: its position is real — it may be
 * accounted for elsewhere or be negligible — and it is worth placing either way. Nothing is
 * invented here: a marker exists only where the model already holds coordinates.
 *
 * This lives outside the SWT and OpenGL code so the list can be built, and checked, without a
 * display.
 */
object MassMarkers {

  def from(crrcsim: CRRCSim): IndexedSeq[MassMarker] =
    if (crrcsim == null) IndexedSeq.empty else geometryMasses(crrcsim) ++ propulsionMasses(crrcsim)

  /** The masses on the geometry: the aircraft's own, and every surface's, section's, control's and
    * body's, each alongside the element that holds it — which is what knows about mirroring. */
  private def geometryMasses(crrcsim: CRRCSim): IndexedSeq[MassMarker] =
    Option(crrcsim.getAvl)
      .flatMap(avl => Option(avl.getGeometry))
      .map(_.getMassElements.asScala.toIndexedSeq)
      .getOrElse(IndexedSeq.empty)
      .flatMap(element => element.getMasses.asScala.toIndexedSeq.flatMap(mass => fromMass(element, mass)))

  /** A stored mass, and the mirrored half it implies when its element is mirrored. */
  private def fromMass(element: MassObject, mass: Mass): IndexedSeq[MassMarker] = {
    val plane = Option(element.mirrorPlaneY()).map(_.floatValue)
    val stored = MassMarker(mass, mass, Option(mass.getName).getOrElse("mass"), mass.getMass,
      mass.getX, mass.getY, mass.getZ,
      (x, y, z) => { mass.setX(x); mass.setY(y); mass.setZ(z) },
      mirrorPlaneY = plane)

    Option(element.virtualMirrorOf(mass)) match {
      case None => IndexedSeq(stored)
      case Some(mirror) =>
        val planeY = plane.getOrElse(0f)
        // The mirror has nothing of its own to write to: moving it moves the stored mass, reflected.
        val virtual = MassMarker(mass, mass, mirror.getName, mirror.getMass,
          mirror.getX, mirror.getY, mirror.getZ,
          (x, y, z) => { mass.setX(x); mass.setY(2f * planeY - y); mass.setZ(z) },
          virtual = true, mirrorPlaneY = plane)
        IndexedSeq(stored, virtual)
    }
  }

  /**
   * The propulsion components' own positions. The fuel tank is listed with its contents, which
   * JSBSim adds on top of the empty weight rather than as part of it — the marker says where the
   * fuel is, and moving it moves the tank.
   */
  private def propulsionMasses(crrcsim: CRRCSim): IndexedSeq[MassMarker] = {
    val power = Option(crrcsim.getConfig).flatMap(config => Option(config.getPower))
    power.map { p =>
      val batteries = p.getBateries.asScala.toIndexedSeq.flatMap { battery =>
        fromComponent(battery, battery.getMass, battery.getPos) ++
          battery.getShafts.asScala.toIndexedSeq.flatMap { shaft =>
            // The shaft itself, so the assembly can be moved as one. It states no mass — a shaft is not a part
            // that weighs something — but its position is real, and everything on it follows.
            fromComponent(shaft, 0f, shaft.getPos) ++
              shaft.getEngines.asScala.toIndexedSeq.flatMap(e => onShaft(shaft, e, e.getMass, e.getPos)) ++
              shaft.getCombustionEngines.asScala.toIndexedSeq
                .flatMap(e => onShaft(shaft, e, e.getMass, e.getPos)) ++
              shaft.getPropellers.asScala.toIndexedSeq.flatMap(pr => onShaft(shaft, pr, pr.getMass, pr.getPos)) ++
              shaft.getDuctedFans.asScala.toIndexedSeq.flatMap(f => onShaft(shaft, f, f.getMass, f.getPos))
          }
      }
      val tanks = p.getFuelTanks.asScala.toIndexedSeq.flatMap { tank =>
        fromComponent(tank, tank.getContents, tank.getPos)
      }
      batteries ++ tanks
    }.getOrElse(IndexedSeq.empty)
  }

  /**
   * A component mounted on a shaft: shown where it really is — within the shaft — and moved by writing back
   * where it sits within it. Drag the shaft and everything on it follows, because their positions are relative
   * to it; drag one of them and only it moves.
   */
  private def onShaft(shaft: Shaft, component: AnyRef, mass: Float, pos: Pos): IndexedSeq[MassMarker] =
    Option(pos).toIndexedSeq.map { p =>
      MassMarker(component, p, component.toString, mass,
        shaft.absoluteX(p.getX), shaft.absoluteY(p.getY), shaft.absoluteZ(p.getZ),
        (x, y, z) => {
          p.setX(x - shaft.getPos.getX)
          p.setY(y - shaft.getPos.getY)
          p.setZ(z - shaft.getPos.getZ)
        })
    }

  /** A component contributes a marker when it has a position to show; without one there is
    * nothing to draw and nothing to move. */
  private def fromComponent(component: AnyRef, mass: Float, pos: Pos): IndexedSeq[MassMarker] =
    Option(pos).toIndexedSeq.map { p =>
      MassMarker(component, p, component.toString, mass, p.getX, p.getY, p.getZ,
        (x, y, z) => { p.setX(x); p.setY(y); p.setZ(z) })
    }

  /** The index of the marker a tree node refers to, whether the node is the mass itself, a
    * propulsion component, or that component's Position node. Never the mirrored half: it is not a
    * thing the tree can select, since the model does not store it. */
  def indexOf(markers: IndexedSeq[MassMarker], node: Any): Option[Int] = {
    val target = node.asInstanceOf[AnyRef]
    val found = markers.indexWhere(m => !m.virtual && ((m.node eq target) || (m.position eq target)))
    if (found == -1) None else Some(found)
  }

  /** For each marker, the index of the marker holding its other half, or -1. The viewer uses it to
    * move both halves together while one of them is being dragged. */
  def mirrorIndexes(markers: IndexedSeq[MassMarker]): IndexedSeq[Int] =
    markers.indices.map { i =>
      val marker = markers(i)
      markers.indices.find(j =>
        j != i && (markers(j).node eq marker.node) && markers(j).virtual != marker.virtual
      ).getOrElse(-1)
    }
}
