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

import com.abajar.avleditor.crrcsim.{Battery, CRRCSim, CombustionEngine, FuelTank, MassInertia, Propeller, Power}
import com.abajar.avleditor.avl.geometry.Control
import com.abajar.avleditor.avl.runcase.AvlCalculation
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
          // A propeller wider than the wingspan is a units mistake (inches typed as metres),
          // not a design: JSBSim would compute thrust from a rotor the size of the aircraft.
          val span = Option(crrcsim.getAvl).map(_.getGeometry.getBref.toDouble).getOrElse(0.0)
          val oversized =
            if (span <= 0 || p.getD <= 0 || p.getD < span) Nil
            else Seq(s"'${label(classOf[Propeller], "D")}' on the Propeller (${p.getD} m) is not " +
              s"smaller than the wingspan 'Bref' ($span); check the units.")
          diameter ++ blades ++ oversized
      }
      val piston = shaft.flatMap(sh =>
        Option(sh.getCombustionEngines).map(_.asScala).getOrElse(Nil).headOption)

      val engineProblems = (engine, piston) match {
        case (Some(_), Some(_)) =>
          Seq("The shaft carries both an electric and a combustion engine; keep one, since only " +
            "one can drive the propeller.")
        case (None, Some(p)) => combustionProblems(p, crrcsim)
        case (None, None) =>
          // A SimpleTrust is a CRRCsim thrust model the JSBSim export does not map. Saying so is
          // the point: the '+ Trust' button exists, so a model built that way otherwise gets told
          // to add an engine with no hint that its thrust source is simply ignored.
          val trusts = shaft.map(sh =>
            Option(sh.getSimpleTrusts).map(_.asScala).getOrElse(Nil)).getOrElse(Nil)
          if (trusts.nonEmpty)
            Seq("The shaft's thrust comes from a Simple Trust, which the JSBSim export does not " +
              "support. Replace it with an electric engine ('+ Engine') or a combustion one " +
              "('+ Piston').")
          else
            Seq("The shaft has no engine. Add an electric one with '+ Engine' or a combustion one " +
              "with '+ Piston'.")
        case (Some(e), None) =>
          // The exporter derives the motor's power from voltage x current, so a point with a
          // zero current yields no power: requiring the same thing here keeps the validation
          // from passing a model the export then quietly turns back into a glider.
          val usable = Option(e.getData).map(_.asScala).getOrElse(Nil)
            .count(d => d.getU_K > 0 && d.getI_M > 0 && d.getRpms > 0)
          if (usable > 0) Nil
          else Seq("The engine needs a Data row with 'Voltage', 'Current' and 'Rpms' all above " +
            "zero; the motor's power is derived from them. Add it with '+ Data'.")
      }
      val voltage = battery.filter(_.getU_0 <= 0).map(b =>
        s"'${label(classOf[Battery], "U_0")}' on the Battery must be greater than zero " +
          s"(found ${b.getU_0} V).").toSeq

      voltage ++ propellerProblems ++ engineProblems
    }
  }

  /**
   * A combustion engine is described by figures JSBSim's piston model needs, plus the fuel it
   * burns. None of them can be guessed: an invented displacement or consumption gives an engine
   * that runs and lies about its performance and endurance.
   */
  private def combustionProblems(e: CombustionEngine, crrcsim: CRRCSim): Seq[String] = {
    def positive(field: String, value: Double): Seq[String] =
      if (value > 0) Nil
      else Seq(s"'${label(classOf[CombustionEngine], field)}' on the Combustion engine must be " +
        s"greater than zero (found $value).")

    val revs =
      if (e.getMaxRpm > e.getIdleRpm) Nil
      else Seq(s"'${label(classOf[CombustionEngine], "maxRpm")}' (${e.getMaxRpm}) must be above " +
        s"'${label(classOf[CombustionEngine], "idleRpm")}' (${e.getIdleRpm}).")

    val cycles =
      if (e.getCycles == 2 || e.getCycles == 4) Nil
      else Seq(s"'${label(classOf[CombustionEngine], "cycles")}' must be 2 or 4 (found ${e.getCycles}).")

    positive("displacement", e.getDisplacement) ++
      positive("maxPower", e.getMaxPower) ++
      positive("idleRpm", e.getIdleRpm) ++
      revs ++ cycles ++
      positive("fuelConsumption", e.getFuelConsumption) ++
      fuelProblems(crrcsim)
  }

  /** A combustion engine with no fuel cannot run, and JSBSim needs the tank's mass and position. */
  private def fuelProblems(crrcsim: CRRCSim): Seq[String] = {
    val tanks = Option(crrcsim.getConfig.getPower)
      .map(p => Option(p.getFuelTanks).map(_.asScala).getOrElse(Nil)).getOrElse(Nil)
    if (tanks.isEmpty)
      Seq("A combustion engine needs a fuel tank under Power. Add one with '+ Fuel tank'.")
    else {
      val capacity = tanks.filter(_.getCapacity <= 0).map(t =>
        s"'${label(classOf[FuelTank], "capacity")}' on a Fuel tank must be greater than zero " +
          s"(found ${t.getCapacity} kg).")
      val overfilled = tanks.filter(t => t.getCapacity > 0 && t.getContents > t.getCapacity).map(t =>
        s"'${label(classOf[FuelTank], "contents")}' (${t.getContents} kg) cannot exceed " +
          s"'${label(classOf[FuelTank], "capacity")}' (${t.getCapacity} kg).")
      (capacity ++ overfilled).toSeq
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

  /**
   * Controls must state how far they deflect. The channel range is what the pilot's stick maps
   * to, so a control without it used to be flown with an invented 25 deg: the aircraft responds
   * to the wrong authority, which looks like a handling quirk rather than missing data.
   */
  private def controlProblems(crrcsim: CRRCSim): Seq[String] = {
    val geo = Option(crrcsim.getAvl).map(_.getGeometry).orNull
    if (geo == null) return Nil // already reported by referenceProblems

    val axes = Set(0, 1, 2) // aileron, elevator, rudder
    val flying = geo.getSurfaces.asScala.flatMap(_.getSections.asScala).flatMap(_.getControls.asScala)
      .filter(c => axes.contains(c.getType))

    val noDeflection = flying.filter(_.getMaxDeflection <= 0).map(_.getName)
      .filter(n => n != null && n.nonEmpty).toSeq.distinct
    val deflectionProblems =
      if (noDeflection.isEmpty && flying.forall(_.getMaxDeflection > 0)) Nil
      else Seq(s"'${label(classOf[Control], "maxDeflection")}' must be greater than zero on every " +
        "control" + (if (noDeflection.isEmpty) "." else s": ${noDeflection.mkString(", ")}."))

    if (JsbsimExporter.detectControls(geo).isEmpty && deflectionProblems.isEmpty)
      Seq("The model has no control surface on any axis (elevator, aileron or rudder), so it " +
        "cannot be flown. Add a control to a section.")
    else deflectionProblems
  }

  /**
   * What the AVL run itself must have produced, checked after running AVL and before writing the
   * model. Unlike everything above, these are AVL's outputs rather than the editor's fields, so
   * they cannot be checked up front — and they used to be replaced by round numbers.
   */
  def validateCalculation(calc: AvlCalculation): Seq[String] = {
    if (calc == null) return Seq("AVL produced no results for this model.")
    val derivatives =
      if (calc.getStabilityDerivatives != null) Nil
      else Seq("AVL produced no stability derivatives; the aerodynamic model cannot be built.")
    val efficiency =
      if (JsbsimExporter.spanEfficiency(calc).isDefined) Nil
      else Seq("AVL reported no span efficiency ('e'), which sets the induced drag. Re-run AVL; " +
        "it is not substituted with a typical value.")
    derivatives ++ efficiency
  }
}
