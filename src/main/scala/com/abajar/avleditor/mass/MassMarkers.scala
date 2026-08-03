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

import com.abajar.avleditor.avl.mass.Mass
import com.abajar.avleditor.crrcsim.{CRRCSim, Pos}
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
 */
case class MassMarker(node: AnyRef,
                      position: AnyRef,
                      label: String,
                      mass: Float,
                      x: Float,
                      y: Float,
                      z: Float,
                      moveTo: (Float, Float, Float) => Unit)

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

  /** The masses on the geometry: the aircraft's own, and every surface's, section's and body's. */
  private def geometryMasses(crrcsim: CRRCSim): IndexedSeq[MassMarker] =
    Option(crrcsim.getAvl)
      .flatMap(avl => Option(avl.getGeometry))
      .map(_.getMassesRecursive.asScala.toIndexedSeq)
      .getOrElse(IndexedSeq.empty)
      .map(fromMass)

  private def fromMass(mass: Mass): MassMarker =
    MassMarker(mass, mass, Option(mass.getName).getOrElse("mass"), mass.getMass,
      mass.getX, mass.getY, mass.getZ,
      (x, y, z) => { mass.setX(x); mass.setY(y); mass.setZ(z) })

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
            shaft.getEngines.asScala.toIndexedSeq.flatMap(e => fromComponent(e, e.getMass, e.getPos)) ++
              shaft.getCombustionEngines.asScala.toIndexedSeq.flatMap(e => fromComponent(e, e.getMass, e.getPos)) ++
              shaft.getPropellers.asScala.toIndexedSeq.flatMap(pr => fromComponent(pr, pr.getMass, pr.getPos))
          }
      }
      val tanks = p.getFuelTanks.asScala.toIndexedSeq.flatMap { tank =>
        fromComponent(tank, tank.getContents, tank.getPos)
      }
      batteries ++ tanks
    }.getOrElse(IndexedSeq.empty)
  }

  /** A component contributes a marker when it has a position to show; without one there is
    * nothing to draw and nothing to move. */
  private def fromComponent(component: AnyRef, mass: Float, pos: Pos): IndexedSeq[MassMarker] =
    Option(pos).toIndexedSeq.map { p =>
      MassMarker(component, p, component.toString, mass, p.getX, p.getY, p.getZ,
        (x, y, z) => { p.setX(x); p.setY(y); p.setZ(z) })
    }

  /** The index of the marker a tree node refers to, whether the node is the mass itself, a
    * propulsion component, or that component's Position node. */
  def indexOf(markers: IndexedSeq[MassMarker], node: Any): Option[Int] = {
    val target = node.asInstanceOf[AnyRef]
    val found = markers.indexWhere(m => (m.node eq target) || (m.position eq target))
    if (found == -1) None else Some(found)
  }
}
