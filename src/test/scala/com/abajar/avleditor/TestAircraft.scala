/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor

import com.abajar.avleditor.avl.geometry.{Control, Section, Surface}
import com.abajar.avleditor.crrcsim.{CRRCSim, CRRCSimFactory, EngineData, Wheel}

/**
 * The aircraft the checks fly.
 *
 * Built here rather than loaded from `samples/`, because the samples are the user's aeroplanes: they get edited
 * while the editor is being tried out, and a check that loads one fails for reasons that have nothing to do
 * with the code — which is exactly what happened when a ducted fan appeared in the eurofighter between one run
 * and the next. The same reasoning `LegacyRoundTripCheck` already follows, and `AGENTS.md` states: a guarantee
 * cannot rest on a sample nobody tidies up.
 *
 * It is also a better subject than the eurofighter. This is a plain, **stable** model aeroplane — a wing with
 * ailerons, a tailplane with an elevator, a fin with a rudder, its centre of gravity ahead of the neutral point
 * — so it trims, it has oscillatory modes to judge, and a check about the aerodynamics is not fighting a
 * divergence at the same time. Every figure below is an ordinary 1.2 kg 1.2 m sport model:
 *
 *  - wing 1.2 m span, 0.20 m chord, NACA 2412, ailerons on the outer half
 *  - tailplane 0.40 m span at 0.62 m aft, NACA 0010, all of it elevator
 *  - fin 0.16 m tall, NACA 0010, all of it rudder
 *  - 1.17 kg over 0.24 m2, and three wheels to stand on
 *  - a 3S pack driving a 250 W motor and a 10 x 5 propeller
 */
object TestAircraft {

  /** The span, chord and stations, in metres — the model states metres. */
  val Span = 1.2f
  val Chord = 0.20f
  val TailArm = 0.62f
  /** What it comes to: the airframe, the ballast and the propulsion. */
  val MassKg = 1.17f

  def conventional(): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()
    val avl = crrcsim.getAvl
    avl.setVelocity(14f)
    val geometry = avl.getGeometry
    geometry.getSurfaces.clear()
    geometry.getBodies.clear()
    geometry.getMasses.clear()

    // Reference geometry: the wing's own area and span, the chord as the reference chord, and the moments
    // taken about the balance point — which calculate() overwrites from the masses anyway.
    geometry.setSref(Span * Chord)
    geometry.setBref(Span)
    geometry.setCref(Chord)
    geometry.setXref(0.075f); geometry.setYref(0f); geometry.setZref(0f)
    geometry.setCDp(0.012f)

    wing(geometry.createSurface())
    tailplane(geometry.createSurface())
    fin(geometry.createSurface())
    geometry.initParents()

    // The weights. Spread across the span, not piled on the centreline: an aircraft whose masses all sit at
    // y = z = 0 has no roll inertia at all, which the requirements refuse and no aeroplane has. These are the
    // geometry's own masses, which are absolute and never mirrored, so both wing halves are stated.
    mass(crrcsim, "fuselage", 0.30f, 0.10f, 0f, 0f)
    mass(crrcsim, "left wing", 0.20f, 0.10f, -0.30f, 0f)
    mass(crrcsim, "right wing", 0.20f, 0.10f, 0.30f, 0f)
    // The tail weighs something, and that matters more than its 80 grams suggest: with every mass piled around
    // the wing this aircraft's pitch inertia came out four times too small, the pitch dynamics with it became
    // far faster than any real model's, and JSBSim's integration gave up 0.2 s into a full-throttle run.
    mass(crrcsim, "tail", 0.08f, 0.60f, 0f, 0f)
    // And the ballast that puts the centre of gravity ahead of the neutral point, which is what makes this
    // aircraft stable and therefore a good subject for a check about anything else.
    mass(crrcsim, "nose ballast", 0.08f, -0.12f, 0f, 0f)

    wheels(crrcsim)
    propulsion(crrcsim)
    crrcsim.calculate()
    crrcsim
  }

  /**
   * The same aeroplane with a **strongly tapered** wing — 4:1, the same span and the same area, so the
   * only thing that has changed is where the lift sits across the span.
   *
   * It exists for one check and it is the check's whole point. NACA Report No. 572 says a tapered wing
   * reaches its sections' limit at the tip while a rectangular one does so near the middle, and that this
   * costs it maximum lift. Two aeroplanes alike in every other respect are the only way to say that is the
   * taper and not something else.
   */
  def tapered(): CRRCSim = {
    val crrcsim = conventional()
    val wing = crrcsim.getAvl.getGeometry.getSurfaces.get(0)
    // Same mean chord, so the same area: cr = 2c*t/(1+t) with t = 4 gives 1.6c at the root, 0.4c at the tip.
    val root = 1.6f * Chord
    val tip = 0.4f * Chord
    val sections = wing.getSections
    for (i <- 0 until sections.size) {
      val section = sections.get(i)
      val fraction = section.getYle / (Span / 2f)
      section.setChord(root + (tip - root) * fraction)
      // Leading edge swept so the quarter-chord line stays straight, which is what keeps this a taper
      // change and not also a sweep change.
      section.setXle(0.25f * (root - section.getChord))
    }
    crrcsim.calculate()
    crrcsim
  }

  /** The same aeroplane with a 70 mm ducted fan in place of the propeller. */
  def ductedFan(): CRRCSim = {
    val crrcsim = conventional()
    val shaft = crrcsim.getConfig.getPower.getBateries.get(0).getShafts.get(0)
    shaft.getPropellers.clear()
    val fan = shaft.createDuctedFan()
    fan.setInnerDiameterMm(68f)
    fan.setBlades(12)
    fan.setLengthMm(70f)
    fan.setMass(0.19f)
    fan.getPos.setX(0.05f)
    val engine = shaft.getEngines.get(0)
    engine.getData.clear()
    val row = engine.createData()
    row.setU_K(22.2f); row.setI_M(60f); row.setRpms(38000f)
    crrcsim.calculate()
    crrcsim
  }

  private def wing(surface: Surface): Unit = {
    surface.setName("wing")
    surface.setSymmetric(true)
    surface.setNchord(8); surface.setNspan(12)
    surface.getSections.clear()
    // Root, then the aileron's inboard end, then the tip: a straight rectangular wing.
    section(surface, xle = 0f, yle = 0f, chord = Chord, naca = "2412")
    val mid = section(surface, xle = 0f, yle = Span / 4f, chord = Chord, naca = "2412")
    val tip = section(surface, xle = 0f, yle = Span / 2f, chord = Chord, naca = "2412")
    Seq(mid, tip).foreach(s => control(s, "aileron", axis = 0, hinge = 0.75f, signDup = -1f))
  }

  private def tailplane(surface: Surface): Unit = {
    surface.setName("tailplane")
    surface.setSymmetric(true)
    surface.setNchord(6); surface.setNspan(8)
    surface.getSections.clear()
    val root = section(surface, xle = TailArm, yle = 0f, chord = 0.11f, naca = "0010")
    val tip = section(surface, xle = TailArm, yle = 0.20f, chord = 0.11f, naca = "0010")
    Seq(root, tip).foreach(s => control(s, "elevator", axis = 1, hinge = 0.4f, signDup = 1f))
  }

  private def fin(surface: Surface): Unit = {
    surface.setName("fin")
    surface.setSymmetric(false)
    surface.setNchord(6); surface.setNspan(6)
    surface.getSections.clear()
    // A fin is a surface in the vertical plane: its span runs up in z, so the sections share a y.
    val root = section(surface, xle = TailArm, yle = 0f, chord = 0.12f, naca = "0010", zle = 0f)
    val top = section(surface, xle = TailArm + 0.02f, yle = 0f, chord = 0.09f, naca = "0010", zle = 0.16f)
    Seq(root, top).foreach(s => control(s, "rudder", axis = 2, hinge = 0.5f, signDup = 1f, hvecZ = 1f))
  }

  /**
   * A station, appended in the order given.
   *
   * Deliberately not `Surface.createSection()`: that inserts a station *between the last two* and interpolates
   * it, which is what the editor's `+` button should do to an existing wing and the wrong thing for building
   * one. Used that way it produced a wing whose sections ran root, tip, mid — and AVL, which needs them
   * ordered along the span, built a folded surface out of it and reported a negative span efficiency.
   */
  private def section(surface: Surface, xle: Float, yle: Float, chord: Float, naca: String,
                      zle: Float = 0f): Section = {
    val s = new Section
    s.setParentSurface(surface)
    s.getControls.clear()
    s.setXle(xle); s.setYle(yle); s.setZle(zle)
    s.setChord(chord)
    s.setAinc(0f)
    s.setNACA(naca)
    surface.getSections.add(s)
    s
  }

  private def control(section: Section, name: String, axis: Int, hinge: Float, signDup: Float,
                      hvecZ: Float = 0f): Unit = {
    val c = new Control
    c.setName(name)
    c.setType(axis) // 0 aileron, 1 elevator, 2 rudder
    c.setGain(20f)
    c.setMaxDeflection(20f)
    c.setXhinge(hinge)
    c.setXhvec(0f); c.setYhvec(if (hvecZ == 0f) 1f else 0f); c.setZhvec(hvecZ)
    c.setSgnDup(signDup)
    section.getControls.add(c)
  }

  private def mass(crrcsim: CRRCSim, name: String, kg: Float, x: Float, y: Float, z: Float): Unit = {
    val m = crrcsim.getAvl.getGeometry.createMass()
    m.setName(name)
    m.setMass(kg)
    m.setX(x); m.setY(y); m.setZ(z)
  }

  /** Three wheels spanning a real footprint, which is what JSBSim needs to stand the aircraft up. */
  private def wheels(crrcsim: CRRCSim): Unit = {
    crrcsim.getWheels.clear()
    Seq(("NOSE", -0.10f, 0f), ("LEFT", 0.16f, -0.14f), ("RIGHT", 0.16f, 0.14f)).foreach {
      case (name, x, y) =>
        val w = new Wheel
        w.setName(name)
        w.getPos.setX(x); w.getPos.setY(y); w.getPos.setZ(-0.08f)
        crrcsim.getWheels.add(w)
    }
  }

  /** A 3S pack, a 250 W motor and a 10 x 5 propeller: 210 W/kg, which flies. */
  private def propulsion(crrcsim: CRRCSim): Unit = {
    val power = crrcsim.getConfig.getPower
    power.getBateries.clear()
    val battery = power.createBattery()
    battery.setU_0(12.6f)
    battery.setC(2.2f)
    battery.setU_off(9.9f)
    battery.setMass(0.18f)
    battery.getPos.setX(0.02f)
    battery.setLengthMm(105f); battery.setWidthMm(34f); battery.setHeightMm(24f)
    battery.createShaft()
    val shaft = battery.getShafts.get(0)
    shaft.getPos.setX(-0.06f) // the assembly, just ahead of the wing

    val engine = shaft.createEngine()
    engine.setMass(0.06f)
    engine.setDiameterMm(28f); engine.setLengthMm(30f)
    val row = new EngineData
    row.setU_K(11.1f); row.setI_M(22.5f); row.setRpms(9500f)
    engine.getData.add(row)

    val propeller = shaft.createPropeller()
    propeller.setD(0.254f) // 10 inches
    propeller.setH(0.127f) // 5 inches of pitch
    propeller.setBlades(2)
    propeller.setMass(0.012f)
    propeller.getPos.setX(-0.03f)
  }
}
