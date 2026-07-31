/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.ac3d

import com.abajar.avleditor.avl.AVLGeometry
import scala.collection.JavaConverters._

/**
 * Writes a simple AC3D (.ac) visual mesh from the AVL geometry: each lifting
 * surface becomes flat quad panels between consecutive sections (mirrored when the
 * surface is symmetric). Enough for a recognisable shape in FlightGear; the same
 * format the app's [[AC3DLoader]] reads, so output can be round-tripped to validate.
 *
 * Panels are built in the AVL frame (x aft, y right, z up) and converted to the
 * FlightGear model frame on output — see [[toFlightGearFrame]].
 */
object AC3DWriter {

  /**
   * AVL frame (x aft, y right, z up) to the FlightGear model frame, where +x is right,
   * +y is up and +z is aft, so the nose points at -z. Emitting AVL coordinates directly
   * puts the fuselage across the model and stands the wings on edge.
   *
   * The mapping is a cyclic permutation, so its determinant is +1: handedness is
   * preserved and the quad winding computed in the AVL frame still faces outward.
   */
  private[ac3d] def toFlightGearFrame(v: (Float, Float, Float)): (Float, Float, Float) =
    (v._2, v._3, v._1)

  private case class Quad(v: Array[(Float, Float, Float)]) // 4 vertices, CCW

  private val RadialSegments = 12

  def fromGeometry(geo: AVLGeometry): String =
    render(collect(geo))

  private def collect(geo: AVLGeometry): Seq[Quad] = collectQuads(geo) ++ collectBodyQuads(geo)

  /**
   * Bounding box of the emitted mesh, in the FlightGear model frame, as (min, max). Callers use
   * it to size things that depend on how big the aircraft actually is — view offsets, chase
   * distance — instead of guessing. None when the geometry produces no panels.
   */
  def boundsFromGeometry(geo: AVLGeometry): Option[((Float, Float, Float), (Float, Float, Float))] = {
    val verts = collect(geo).flatMap(_.v).map(toFlightGearFrame)
    if (verts.isEmpty) None
    else Some((
      (verts.map(_._1).min, verts.map(_._2).min, verts.map(_._3).min),
      (verts.map(_._1).max, verts.map(_._2).max, verts.map(_._3).max)
    ))
  }

  /** Fuselage/bodies as surfaces of revolution from their profile points. */
  private def collectBodyQuads(geo: AVLGeometry): Seq[Quad] = {
    val out = scala.collection.mutable.ArrayBuffer[Quad]()
    for (body <- geo.getBodies.asScala) {
      val pts = body.getProfilePoints.asScala.map(p => (p.getX, p.getRadius)).toIndexedSeq
      if (pts.length >= 2) {
        val dX = body.getdX; val dY = body.getdY; val dZ = body.getdZ
        def vertex(x: Float, r: Float, j: Int): (Float, Float, Float) = {
          val a = 2.0 * math.Pi * j / RadialSegments
          (dX + x, (dY + r * math.cos(a)).toFloat, (dZ + r * math.sin(a)).toFloat)
        }
        for (i <- 0 until pts.length - 1; j <- 0 until RadialSegments) {
          val (xa, ra) = pts(i); val (xb, rb) = pts(i + 1); val j1 = (j + 1) % RadialSegments
          out += Quad(Array(vertex(xa, ra, j), vertex(xb, rb, j), vertex(xb, rb, j1), vertex(xa, ra, j1)))
        }
      }
    }
    out.toSeq
  }

  private def collectQuads(geo: AVLGeometry): Seq[Quad] = {
    val out = scala.collection.mutable.ArrayBuffer[Quad]()
    for (surface <- geo.getSurfaces.asScala) {
      val dX = surface.getdX; val dY = surface.getdY; val dZ = surface.getdZ
      val sections = surface.getSections.asScala.toIndexedSeq
      for (i <- 0 until sections.length - 1) {
        val a = sections(i); val b = sections(i + 1)
        val leA = (a.getXle + dX, a.getYle + dY, a.getZle + dZ)
        val leB = (b.getXle + dX, b.getYle + dY, b.getZle + dZ)
        val teA = (leA._1 + a.getChord, leA._2, leA._3)
        val teB = (leB._1 + b.getChord, leB._2, leB._3)
        out += Quad(Array(leA, leB, teB, teA))
        if (surface.isSymmetric) {
          def mir(v: (Float, Float, Float)) = (v._1, -v._2, v._3)
          // reverse winding on the mirrored panel to keep it facing outward
          out += Quad(Array(mir(teA), mir(teB), mir(leB), mir(leA)))
        }
      }
    }
    out.toSeq
  }

  /**
   * Drops the corners that collapse onto each other before writing a surface. A body's nose and
   * tail are fans of panels whose outer edge shrinks to a point, so those panels arrive as quads
   * with two coincident corners: written as `refs 4` they are degenerate faces, which waste
   * geometry and can render as artefacts. Emitted as triangles instead, and skipped entirely when
   * fewer than three corners remain.
   */
  private def distinctCorners(quad: Quad): Seq[(Float, Float, Float)] = {
    val corners = quad.v.toSeq
    corners.zipWithIndex.collect {
      case (v, i) if v != corners((i + corners.length - 1) % corners.length) => v
    }
  }

  private def render(quads: Seq[Quad]): String = {
    val sb = new StringBuilder
    sb.append("AC3Db\n")
    sb.append("MATERIAL \"surface\" rgb 0.80 0.82 0.86 amb 0.4 0.4 0.4 emis 0 0 0 " +
      "spec 0.3 0.3 0.3 shi 32 trans 0\n")
    sb.append("OBJECT world\n")
    sb.append("kids 1\n")
    sb.append("OBJECT poly\n")
    sb.append("name \"aircraft\"\n")

    val faces = quads.map(distinctCorners).filter(_.length >= 3)
    val verts = faces.flatten.map(toFlightGearFrame)
    sb.append(s"numvert ${verts.length}\n")
    verts.foreach { case (x, y, z) => sb.append(s"$x $y $z\n") }

    sb.append(s"numsurf ${faces.length}\n")
    var base = 0
    for (face <- faces) {
      sb.append("SURF 0x0\n")
      sb.append("mat 0\n")
      sb.append(s"refs ${face.length}\n")
      // Texture coordinates around the face; unused by the flat material but required by the format.
      val uv = Seq((0, 0), (1, 0), (1, 1), (0, 1))
      face.indices.foreach { i =>
        val (u, v) = uv(i % uv.length)
        sb.append(s"${base + i} $u $v\n")
      }
      base += face.length
    }
    sb.append("kids 0\n")
    sb.toString
  }
}
