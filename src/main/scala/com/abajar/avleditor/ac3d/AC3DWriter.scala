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
 * Coordinates are passed through in the AVL frame (x aft, y right, z up). If the
 * model appears mis-oriented in FlightGear, adjust the model rotation in the
 * `-set.xml`; the mesh topology is independent of that.
 */
object AC3DWriter {

  private case class Quad(v: Array[(Float, Float, Float)]) // 4 vertices, CCW

  private val RadialSegments = 12

  def fromGeometry(geo: AVLGeometry): String =
    render(collectQuads(geo) ++ collectBodyQuads(geo))

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

  private def render(quads: Seq[Quad]): String = {
    val sb = new StringBuilder
    sb.append("AC3Db\n")
    sb.append("MATERIAL \"surface\" rgb 0.80 0.82 0.86 amb 0.4 0.4 0.4 emis 0 0 0 " +
      "spec 0.3 0.3 0.3 shi 32 trans 0\n")
    sb.append("OBJECT world\n")
    sb.append("kids 1\n")
    sb.append("OBJECT poly\n")
    sb.append("name \"aircraft\"\n")

    val verts = quads.flatMap(_.v)
    sb.append(s"numvert ${verts.length}\n")
    verts.foreach { case (x, y, z) => sb.append(s"$x $y $z\n") }

    sb.append(s"numsurf ${quads.length}\n")
    var base = 0
    for (_ <- quads) {
      sb.append("SURF 0x0\n")
      sb.append("mat 0\n")
      sb.append("refs 4\n")
      sb.append(s"$base 0 0\n")
      sb.append(s"${base + 1} 1 0\n")
      sb.append(s"${base + 2} 1 1\n")
      sb.append(s"${base + 3} 0 1\n")
      base += 4
    }
    sb.append("kids 0\n")
    sb.toString
  }
}
