/*
 * The point AVL measures the aircraft at is a speed, not a taste: in level flight the lift equals the
 * weight, so the lift coefficient follows from the aircraft. Pinned because it used to be a field the user
 * typed, defaulting to 0 — and 0.0 is what the sample carried — so every derivative, deflection and mode was
 * read from an aircraft whose wings held nothing, nose 5.6 degrees down.
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.AnalysisPointCheck"
 */
package com.abajar.avleditor.avl

import com.abajar.avleditor.crrcsim.{CRRCSim, CRRCSimFactory}
import com.abajar.avleditor.jsbsim.SimulationRequirements
import com.abajar.avleditor.view.annotations.{AvlEditorField, AvlEditorReadOnly}

object AnalysisPointCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def near(a: Double, b: Double, tol: Double): Boolean = math.abs(a - b) < tol

  /** The eurofighter's own figures: 0.9397 kg over 0.3909 m². */
  private def model(velocity: Float, mass: Float = 0.9397f, density: Float = 1.225f): CRRCSim = {
    val crrcsim = new CRRCSimFactory().create()
    val avl = crrcsim.getAvl
    avl.setVelocity(velocity)
    avl.setReynoldsNumber(density)
    avl.getGeometry.setSref(0.3909f)
    avl.setAnalysisWeightKg(mass)
    crrcsim
  }

  def main(args: Array[String]): Unit = {
    println("the coefficient level flight needs, for the eurofighter")
    Seq((30f, 0.0428), (20f, 0.0963), (12f, 0.2675), (9f, 0.4756)).foreach { case (v, expected) =>
      val cl = model(v).getAvl.analysisLiftCoefficient()
      println(f"  ${v}%5.1f m/s -> CL ${cl}%.4f")
      check(f"$v%.0f m/s needs CL $expected%.4f", cl != null && near(cl.floatValue, expected, 5e-3))
    }

    println("and it follows the aircraft, not a setting")
    val heavier = model(12f, mass = 1.8794f).getAvl.analysisLiftCoefficient()
    check("twice the weight needs twice the coefficient", near(heavier.floatValue, 0.5350, 5e-3))
    val thinner = model(12f, density = 1.0f).getAvl.analysisLiftCoefficient()
    check("thinner air needs more of it", thinner.floatValue > 0.2675f)

    println("what it will not do: invent one")
    check("no weight, no point", model(12f, mass = 0f).getAvl.analysisLiftCoefficient() == null)
    check("no speed, no point", model(0f).getAvl.analysisLiftCoefficient() == null)
    check("no air, no point", model(12f, density = 0f).getAvl.analysisLiftCoefficient() == null)
    val noArea = model(12f)
    noArea.getAvl.getGeometry.setSref(0f)
    check("no reference area, no point", noArea.getAvl.analysisLiftCoefficient() == null)

    println("and the requirements refuse before a run reaches that state")
    def mentions(problems: Seq[String], what: String): Boolean =
      problems.exists(_.toLowerCase.contains(what))
    val noSpeed = SimulationRequirements.validate(model(0f))
    check("a speed of zero is refused", mentions(noSpeed, "velocity") || mentions(noSpeed, "speed"))
    check("and the message says what it decides",
      noSpeed.exists(_.contains("lift coefficient")))
    check("an air density of zero is refused too",
      mentions(SimulationRequirements.validate(model(12f, density = 0f)), "density"))
    check("a good model raises none of these",
      !SimulationRequirements.validate(model(12f)).exists(_.contains("lift coefficient")))

    println("the line the log gets")
    val row = model(12f).getAvl.describeAnalysisPoint
    println("  " + row)
    check("names the coefficient", row.contains("CL = 0.267"))
    check("and where it comes from", row.contains("0.940 kg") && row.contains("12.0 m/s"))
    val unknown = new CRRCSimFactory().create().getAvl
    check("and says what is missing when it cannot be derived",
      unknown.describeAnalysisPoint.contains("needs a weight"))

    println("the coefficient is no longer something the user types")
    val alpha = classOf[AVL].getDeclaredField("alpha")
    check("the field carries no editor annotation",
      alpha.getAnnotation(classOf[AvlEditorField]) == null)
    // It has to stay a field: saved models write it out by its name, so removing it would fail to load them.
    check("but the field itself survives, for the files that state one",
      alpha.getType == classOf[Float] || alpha.getType.getName == "float")
    check("the load factor is gone entirely",
      !classOf[AVL].getDeclaredFields.exists(_.getName == "loadFactor"))
    // Nor is it shown: a number that configures itself has no business being a row the user reads and
    // wonders whether to change. It goes to the log.
    check("and the derived point is not a row either",
      !classOf[AVL].getMethods.exists(m =>
        m.getAnnotation(classOf[AvlEditorReadOnly]) != null && m.getName.toLowerCase.contains("analysis")))

    println("the masses are in the unit the model states, and the weight is in kilograms")
    // A Mass holds whatever the model's mass unit says, which is what Config converts for the inertias.
    // Summing them as if they were kilograms made a model stated in grams weigh a thousand times what it
    // does, and every coefficient the analysis measures follows from that number.
    val inGrams = new CRRCSimFactory().create()
    inGrams.getAvl.setMassUnit("g")
    inGrams.getAvl.getGeometry.setSref(0.3909f)
    inGrams.getAvl.setVelocity(12f)
    val gramMass = new com.abajar.avleditor.avl.mass.Mass
    gramMass.setMass(939.7f) // 0.9397 kg, stated in grams
    inGrams.getAvl.getGeometry.getMasses.add(gramMass)
    inGrams.calculate()
    println(f"  939.7 g -> ${inGrams.getAnalysisWeightKg}%.4f kg, CL ${inGrams.getAvl.analysisLiftCoefficient()}%.4f")
    check("grams are converted to kilograms", near(inGrams.getAnalysisWeightKg, 0.9397, 1e-4))
    check("so the coefficient is the same as the model stated in kilograms",
      near(inGrams.getAvl.analysisLiftCoefficient().floatValue, 0.2675, 5e-3))
    check("and the total mass agrees with the one the inertias were derived from",
      near(inGrams.getConfig.getMass_inertia.getMass, inGrams.getAnalysisWeightKg, 1e-6))

    println("the weight comes from the masses, through the same funnel as the inertias")
    val calculated = new CRRCSimFactory().create()
    calculated.getAvl.getGeometry.setSref(0.3909f)
    calculated.getAvl.setVelocity(12f)
    calculated.calculate()
    check("calculate() pushes it, so an analysis cannot run on a stale weight",
      calculated.getAvl.getAnalysisWeightKg == calculated.getAnalysisWeightKg)

    println(if (ok) "ANALYSIS_POINT_OK" else "ANALYSIS_POINT_FAIL")
    if (!ok) sys.exit(1)
  }
}
