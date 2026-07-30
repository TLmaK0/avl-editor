/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim.CRRCSim
import scala.collection.JavaConverters._

/**
 * What a model must provide before it can be handed to a flight simulator. Each rule exists
 * because its absence produced a broken simulation, not because it is nice to have.
 *
 * The exporters deliberately do not substitute missing data: a fabricated value yields an
 * aircraft that loads and looks plausible while behaving wrongly, which is harder to diagnose
 * than a refusal. Callers report these problems and abort the launch.
 */
object SimulationRequirements {

  /** Smallest horizontal footprint accepted for the contact points, in m². */
  private val MinFootprintArea = 1.0e-6

  private val MinContacts = 3

  /** One message per unmet requirement; empty when the model can be simulated. */
  def validate(crrcsim: CRRCSim): Seq[String] =
    massProblems(crrcsim) ++ referenceProblems(crrcsim) ++ contactProblems(crrcsim) ++ controlProblems(crrcsim)

  private def massProblems(crrcsim: CRRCSim): Seq[String] = {
    val mi = crrcsim.getConfig.getMass_inertia
    val inertias = Seq(("Ixx", mi.getI_xx), ("Iyy", mi.getI_yy), ("Izz", mi.getI_zz))
    val massProblem =
      if (mi.getMass > 0) Nil
      else Seq(s"Mass must be greater than zero (found ${mi.getMass}). Set it under Config > Mass inertia.")
    val inertiaProblems = inertias.collect {
      case (name, value) if value <= 0 =>
        s"Moment of inertia $name must be greater than zero (found $value). Set it under Config > Mass inertia."
    }
    massProblem ++ inertiaProblems
  }

  private def referenceProblems(crrcsim: CRRCSim): Seq[String] = {
    val geo = Option(crrcsim.getAvl).map(_.getGeometry).orNull
    if (geo == null) Seq("The model has no AVL geometry.")
    else Seq(("reference area Sref", geo.getSref), ("reference span Bref", geo.getBref),
      ("reference chord Cref", geo.getCref)).collect {
      case (name, value) if value <= 0 =>
        s"The $name must be greater than zero (found $value); the aero coefficients are normalised by it."
    }
  }

  /**
   * Contact points must be at least three and span a real footprint. A single point, or
   * collinear ones, leave pitch and/or roll unsupported: JSBSim cannot trim the aircraft on
   * the ground and it sinks through the runway.
   */
  private def contactProblems(crrcsim: CRRCSim): Seq[String] = {
    val positions = Option(crrcsim.getWheels).map(_.asScala).getOrElse(Nil)
      .flatMap(w => Option(w.getPos))
      .map(p => (p.getX.toDouble, p.getY.toDouble))

    if (positions.length < MinContacts)
      Seq(s"The model needs at least $MinContacts collision points to stand on the ground " +
        s"(found ${positions.length}). Add them under Collision points.")
    else if (largestFootprintArea(positions) <= MinFootprintArea)
      Seq(s"The ${positions.length} collision points are aligned, so they cannot support the " +
        "aircraft in pitch and roll. Spread them out to form a triangle.")
    else Nil
  }

  /** Largest triangle spanned by the points projected on the horizontal plane. */
  private def largestFootprintArea(points: Seq[(Double, Double)]): Double = {
    val triples = for {
      i <- points.indices; j <- i + 1 until points.length; k <- j + 1 until points.length
    } yield {
      val (ax, ay) = points(i); val (bx, by) = points(j); val (cx, cy) = points(k)
      math.abs((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)) / 2.0
    }
    if (triples.isEmpty) 0.0 else triples.max
  }

  private def controlProblems(crrcsim: CRRCSim): Seq[String] = {
    val geo = Option(crrcsim.getAvl).map(_.getGeometry).orNull
    if (geo == null) Nil // already reported by referenceProblems
    else if (JsbsimExporter.detectControls(geo).isEmpty)
      Seq("The model has no control surface on any axis (elevator, aileron or rudder), so it " +
        "cannot be flown. Add a control to a section.")
    else Nil
  }
}
