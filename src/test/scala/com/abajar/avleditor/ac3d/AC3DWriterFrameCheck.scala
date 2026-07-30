/*
 * Checks that the exported mesh is in the FlightGear model frame (+x right, +y up, +z aft,
 * nose at -z) rather than the AVL frame. Emitting AVL coordinates puts the fuselage across
 * the model and stands the wings on edge, so the aircraft is not where FlightGear draws it.
 * Run with:  sbt "test:runMain com.abajar.avleditor.ac3d.AC3DWriterFrameCheck"
 */
package com.abajar.avleditor.ac3d

import com.abajar.avleditor.avl.AVLGeometry
import com.abajar.avleditor.avl.geometry.{Section, Surface}

object AC3DWriterFrameCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private val HalfSpan = 4.0f
  private val Chord = 1.5f
  private val RootX = 2.0f

  /** A single flat symmetric wing: half-span 4 m along AVL y, chord 1.5 m along AVL x. */
  private def wingGeometry(): AVLGeometry = {
    val geo = new AVLGeometry
    geo.getSurfaces.clear() // AVLGeometry ships with a default Surface
    val surface = new Surface
    def section(yle: Float): Section = {
      val s = new Section
      s.setXle(RootX); s.setYle(yle); s.setZle(0f); s.setChord(Chord)
      s
    }
    surface.getSections.clear() // Surface ships with a default root/tip pair
    surface.getSections.add(section(0f))
    surface.getSections.add(section(HalfSpan))
    geo.getSurfaces.add(surface)
    geo
  }

  private def vertices(ac: String): Seq[(Float, Float, Float)] = {
    val lines = ac.split("\n").toIndexedSeq
    val start = lines.indexWhere(_.startsWith("numvert"))
    val n = lines(start).split(" ")(1).toInt
    lines.slice(start + 1, start + 1 + n).map { l =>
      val p = l.trim.split("\\s+"); (p(0).toFloat, p(1).toFloat, p(2).toFloat)
    }
  }

  private def extent(vs: Seq[Float]): Float = vs.max - vs.min

  def main(args: Array[String]): Unit = {
    check("the frame mapping is the (y, z, x) permutation",
      AC3DWriter.toFlightGearFrame((1f, 2f, 3f)) == ((2f, 3f, 1f)))

    val vs = vertices(AC3DWriter.fromGeometry(wingGeometry()))
    check("mesh has vertices", vs.nonEmpty)

    val spanExtent = extent(vs.map(_._1))
    val upExtent = extent(vs.map(_._2))
    val aftExtent = extent(vs.map(_._3))
    println(f"  bbox: x(right)=$spanExtent%.2f y(up)=$upExtent%.2f z(aft)=$aftExtent%.2f")

    // Symmetric surface, so the span runs -HalfSpan..+HalfSpan along FlightGear's +x.
    check("span lies along x (right)", math.abs(spanExtent - 2 * HalfSpan) < 0.001f)
    check("a flat wing has no extent along y (up)", upExtent < 0.001f)
    check("chord lies along z (aft)", math.abs(aftExtent - Chord) < 0.001f)

    // Nose at -z: the leading edge must sit at a smaller z than the trailing edge.
    check("leading edge is forward of the trailing edge", vs.map(_._3).min < vs.map(_._3).max)
    check("wing is mirrored about x=0", math.abs(vs.map(_._1).min + vs.map(_._1).max) < 0.001f)
    check("chord starts at the AVL root x offset", math.abs(vs.map(_._3).min - RootX) < 0.001f)

    println(if (ok) "AC3D_FRAME_OK" else "AC3D_FRAME_FAIL")
    if (!ok) sys.exit(1)
  }
}
