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

import com.abajar.avleditor.crrcsim.{Battery, CRRCSim, MassInertia, Propeller}
import com.abajar.avleditor.avl.AVLGeometry
import com.abajar.avleditor.view.annotations.AvlEditorField
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

  /**
   * The label the properties table shows for a field, read from its annotation rather than
   * repeated here: a message naming something the user cannot find in the table is useless, and
   * hardcoding the label lets the two drift apart when a field is renamed.
   */
  private def label(cls: Class[_], field: String): String =
    try {
      val f = cls.getDeclaredField(field)
      Option(f.getAnnotation(classOf[AvlEditorField])).map(_.text()).filter(_.nonEmpty).getOrElse(field)
    } catch { case _: NoSuchFieldException => field }

  /** Smallest horizontal footprint accepted for the contact points, in m². */
  private val MinFootprintArea = 1.0e-6

  private val MinContacts = 3

  /** One message per unmet requirement; empty when the model can be simulated. */
  def validate(crrcsim: CRRCSim): Seq[String] =
    massProblems(crrcsim) ++ referenceProblems(crrcsim) ++ contactProblems(crrcsim) ++
      controlProblems(crrcsim) ++ propulsionProblems(crrcsim)

  /**
   * Propulsion is required, not optional. Without an engine the exported model cannot take off
   * and FlightGear reports "Throttle 0 does not exist! 0 engines exist" on every launch. The
   * values below all feed the JSBSim motor and propeller directly, so a missing one used to be
   * replaced by an invented constant that produced plausible-looking but wrong thrust.
   */
  private def propulsionProblems(crrcsim: CRRCSim): Seq[String] = {
    val power = Option(crrcsim.getConfig.getPower)
    val battery = power.flatMap(p => Option(p.getBateries).map(_.asScala).getOrElse(Nil).headOption)
    val shaft = battery.flatMap(b => Option(b.getShafts).map(_.asScala).getOrElse(Nil).headOption)
    val propeller = shaft.flatMap(s => Option(s.getPropellers).map(_.asScala).getOrElse(Nil).headOption)
    val engine = shaft.flatMap(s => Option(s.getEngines).map(_.asScala).getOrElse(Nil).headOption)

    if (battery.isEmpty)
      Seq("The model has no propulsion: it needs a battery under Power. Add it with '+ Battery'.")
    else if (shaft.isEmpty)
      Seq("The battery drives no shaft. Add one with '+ Shaft'.")
    else {
      val propellerProblems = propeller match {
        case None => Seq("The shaft has no propeller. Add one with '+ Propeller'.")
        case Some(p) =>
          val diameter =
            if (p.getD > 0) Nil
            else Seq(s"'${label(classOf[Propeller], "D")}' on the Propeller must be greater than " +
              s"zero (found ${p.getD} m).")
          // n_fold is the folding-prop threshold, not a blade count: the export used to feed it
          // to JSBSim as the number of blades.
          val blades =
            if (p.getBlades >= 2) Nil
            else Seq(s"'${label(classOf[Propeller], "blades")}' on the Propeller must be at least 2 " +
              s"(found ${p.getBlades}).")
          diameter ++ blades
      }
      val engineProblems = engine match {
        case None => Seq("The shaft has no engine. Add one with '+ Engine'.")
        case Some(e) =>
          val usable = Option(e.getData).map(_.asScala).getOrElse(Nil)
            .count(d => d.getU_K > 0 && d.getRpms > 0)
          if (usable > 0) Nil
          else Seq("The engine has no usable data points ('Voltage' and 'Rpms' above zero on a " +
            "Data row); the motor's Kv is derived from them. Add them with '+ Data'.")
      }
      val voltage = battery.filter(_.getU_0 <= 0).map(b =>
        s"'${label(classOf[Battery], "U_0")}' on the Battery must be greater than zero " +
          s"(found ${b.getU_0} V).").toSeq

      voltage ++ propellerProblems ++ engineProblems
    }
  }

  private def massProblems(crrcsim: CRRCSim): Seq[String] = {
    val mi = crrcsim.getConfig.getMass_inertia
    val inertias = Seq(("I_xx", mi.getI_xx), ("I_yy", mi.getI_yy), ("I_zz", mi.getI_zz))
      .map { case (field, value) => (label(classOf[MassInertia], field), value) }
    val massProblem =
      if (mi.getMass > 0) Nil
      else Seq(s"'${label(classOf[MassInertia], "Mass")}' under Config > Mass inertia must be " +
        s"greater than zero (found ${mi.getMass}).")
    val inertiaProblems = inertias.collect {
      case (name, value) if value <= 0 =>
        s"'$name' under Config > Mass inertia must be greater than zero (found $value)."
    }
    massProblem ++ inertiaProblems
  }

  private def referenceProblems(crrcsim: CRRCSim): Seq[String] = {
    val geo = Option(crrcsim.getAvl).map(_.getGeometry).orNull
    if (geo == null) Seq("The model has no AVL geometry.")
    else Seq(("reference area", "Sref", geo.getSref), ("reference span", "Bref", geo.getBref),
      ("reference chord", "Cref", geo.getCref)).collect {
      case (what, field, value) if value <= 0 =>
        s"The $what '${label(classOf[AVLGeometry], field)}' on the AVL node must be greater than " +
          s"zero (found $value); the aero coefficients are normalised by it."
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
