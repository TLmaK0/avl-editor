/*
 * An empty modal table has to say what AVL answered, not what the user might have forgotten. It used to
 * read 'No oscillatory eigenmodes available. Define mass/inertia and run AVL again' whatever the reason,
 * which sends the reader after masses AVL already had — the eurofighter's 8 real roots, one of them
 * divergent, being exactly that case.
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.runcase.ModalReportCheck"
 */
package com.abajar.avleditor.avl.runcase

import scala.collection.JavaConverters._

object ModalReportCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def says(lines: List[String], fragment: String): Boolean =
    lines.exists(_.toLowerCase.contains(fragment.toLowerCase))

  private def run(eigenvalues: Seq[(Float, Float)]): AvlCalculation = {
    val calc = new AvlCalculation(0, 0, 0)
    calc.setConfiguration(new Configuration)
    calc.setEigenvalues(new java.util.ArrayList[AvlEigenvalue](
      eigenvalues.map { case (sigma, omega) => new AvlEigenvalue(sigma, omega) }.asJava))
    calc
  }

  def main(args: Array[String]): Unit = {
    println("no eigenvalues at all")
    val none = MilF8785cEvaluator.whyNoModes(run(Nil))
    none.foreach(l => println("  " + l))
    check("says AVL returned none", says(none, "no eigenvalues"))
    check("and that the masses are where they come from", says(none, "mass"))

    println("the eurofighter's answer: 8 real roots, one divergent")
    val real = Seq((-42.64f, 0f), (-19.48f, 0f), (-20.85f, 0f), (4.92f, 0f),
      (-1.32f, 0f), (-0.68f, 0f), (-0.14f, 0f), (-0.05f, 0f))
    val lines = MilF8785cEvaluator.whyNoModes(run(real))
    lines.foreach(l => println("  " + l))
    check("counts what AVL returned", says(lines, "8 modes"))
    check("says none of them oscillates", says(lines, "none of them oscillates"))
    check("and does not blame the masses", says(lines, "did reach AVL"))
    // The divergences are reported on their own, whatever else was found: they used to appear only when the
    // modal table came out empty, so one oscillatory mode was enough to hide three runaways behind a PASS.
    val runaways = MilF8785cEvaluator.divergences(run(real))
    runaways.foreach(d => println("  ! " + d.says))
    check("the divergence is reported", runaways.size == 1)
    // ln(2) / 4.92 = 0.141 s.
    check("with the time it doubles in", runaways.head.says.contains("0.14 s"))
    check("and it is the headline, above whatever the modes say",
      MilF8785cEvaluator.runawaySummary(run(real)).exists(_.contains("will not fly")))
    check("said in the aircraft's own terms, not in sigma alone",
      runaways.head.says.contains("runs away"))
    check("no oscillatory mode is claimed",
      MilF8785cEvaluator.oscillatoryPositiveModes(run(real)).isEmpty)

    println("a stable aircraft with a divergence in it")
    check("one line per divergence, and only for those",
      MilF8785cEvaluator.divergences(run(Seq((-2.0f, 0f), (0.5f, 0f)))).size == 1)

    println("and a divergence alongside a mode that oscillates and passes")
    // The case from the screenshot: a phugoid that passes, and a runaway nobody was being told about.
    val withOscillation = run(Seq((-1.0f, 3.0f), (5.567f, 0f), (0.443f, 0f)))
    check("both divergences are still reported",
      MilF8785cEvaluator.divergences(withOscillation).size == 2)
    check("and the headline says the aircraft will not fly",
      MilF8785cEvaluator.runawaySummary(withOscillation).exists(_.contains("0.12 s")))
    check("even though a mode was found and could pass",
      MilF8785cEvaluator.oscillatoryPositiveModes(withOscillation).size == 1)
    check("an aircraft with nothing running away gets no headline",
      MilF8785cEvaluator.runawaySummary(run(Seq((-1.0f, 3.0f), (-0.5f, 0f)))).isEmpty)

    println("which axis is running away, when AVL says enough to tell")
    val pitchRunaway = run(Seq((3.0f, 0f)))
    pitchRunaway.getEigenvalues.get(0).setModeStateAmplitude("w", 1.0f)
    pitchRunaway.getEigenvalues.get(0).setModeStateAmplitude("q", 0.9f)
    pitchRunaway.getEigenvalues.get(0).setModeStateAmplitude("the", 0.8f)
    val pitched = MilF8785cEvaluator.divergences(pitchRunaway).head
    println("  ! " + pitched.says)
    check("a pitch runaway names the centre of gravity",
      pitched.axis == "pitch" && pitched.says.contains("centre of gravity"))
    val yawRunaway = run(Seq((2.0f, 0f)))
    yawRunaway.getEigenvalues.get(0).setModeStateAmplitude("v", 1.0f)
    yawRunaway.getEigenvalues.get(0).setModeStateAmplitude("r", 0.9f)
    yawRunaway.getEigenvalues.get(0).setModeStateAmplitude("phi", 0.8f)
    val yawed = MilF8785cEvaluator.divergences(yawRunaway).head
    println("  ! " + yawed.says)
    check("a fast lateral one names the fin", yawed.says.contains("fin"))
    check("and without a mode shape it says it cannot tell",
      MilF8785cEvaluator.divergences(run(Seq((1.0f, 0f)))).head.axis == "unknown")

    println("when there are oscillatory modes, this is not used")
    val oscillatory = run(Seq((-1.0f, 3.0f), (-0.1f, 0.4f)))
    check("the evaluator has modes to judge",
      MilF8785cEvaluator.oscillatoryPositiveModes(oscillatory).size == 2)
    // Without the mode shapes AVL prints alongside each eigenvalue there is nothing to tell a short
    // period from a dutch roll, and the evaluator says so rather than picking one.
    check("but it will not name a mode it cannot identify",
      MilF8785cEvaluator.evaluate(oscillatory).forall(row =>
        row.pass.isEmpty && row.verdict.contains("Not judged")))
    check("and says it is the run that cannot tell them apart, not the aircraft that lacks them",
      MilF8785cEvaluator.evaluate(oscillatory).forall(_.verdict.contains("no mode shapes")))

    println("and the row says what the motion is, not only what the standard calls it")
    val unnamed = MilF8785cEvaluator.evaluate(oscillatory)
    check("each one is described in words",
      unnamed.forall(r => r.whatItIs.length > 20 && !r.whatItIs.contains("zeta")))
    check("and the rule is stated without repeating the standard's name on every row",
      unnamed.forall(r => !r.requirement.contains("MIL-F")))

    println("and with the mode shapes, it judges them")
    val shaped = run(Seq((-1.0f, 3.0f), (-0.1f, 0.4f)))
    val longitudinal = shaped.getEigenvalues.get(0)
    longitudinal.setModeStateAmplitude("w", 1.0f)
    longitudinal.setModeStateAmplitude("q", 0.9f)
    longitudinal.setModeStateAmplitude("the", 0.7f)
    val lateral = shaped.getEigenvalues.get(1)
    lateral.setModeStateAmplitude("v", 1.0f)
    lateral.setModeStateAmplitude("p", 0.8f)
    lateral.setModeStateAmplitude("r", 0.6f)
    val judged = MilF8785cEvaluator.evaluate(shaped)
    judged.foreach(r => println(f"    ${r.modeName}%-14s wn=${r.wn.getOrElse(Double.NaN)}%6.3f " +
      f"zeta=${r.zeta.getOrElse(Double.NaN)}%6.3f pass=${r.pass}"))
    // wn = sqrt(1 + 9) = 3.162, zeta = 1 / 3.162 = 0.316, inside MIL-F-8785C's 0.30..2.00.
    check("the short period is identified and passes",
      judged.exists(r => r.modeName == "Short-period" && r.pass == Some(true)))
    // A frequency in radians per second is not something anyone can picture; how often it swings is.
    val shortPeriod = judged.find(_.modeName == "Short-period").get
    println(f"    period ${shortPeriod.period.getOrElse(0.0)}%.2f s, " +
      f"half in ${shortPeriod.swingsToHalf.getOrElse(0.0)}%.1f swings: ${shortPeriod.verdict}")
    // wn 3.162, zeta 0.316 -> damped 3.0 rad/s -> 2.09 s a swing.
    check("it says how often it swings", shortPeriod.period.exists(p => math.abs(p - 2.09) < 0.02))
    check("and how many swings it takes to die down to half",
      shortPeriod.swingsToHalf.exists(s => math.abs(s - 0.33) < 0.02))
    check("and the verdict is a sentence, not a symbol", shortPeriod.verdict.contains("damping"))
    // With the shapes present but no phugoid among them, the reason is about the aircraft and not the run:
    // saying "AVL reported no mode shapes" there would send the reader after something already in hand.
    val phugoid = judged.find(_.modeName == "Phugoid").get
    println("    " + phugoid.verdict)
    check("a motion AVL did not find is reported as not found, not as not measured",
      phugoid.pass.isEmpty && phugoid.verdict.startsWith("Not found") &&
        !phugoid.verdict.contains("no mode shapes"))

    val failing = MilF8785cEvaluator.evaluate(shaped).find(_.pass == Some(false))
    failing.foreach { r =>
      println("    " + r.modeName + ": " + r.verdict)
      // Not "FAIL" and a symbol: which way it misses, by how much, and what moves it.
      check("a failing row says which way it falls short, and by how much",
        r.verdict.length > 60 && r.verdict.exists(_.isDigit) && r.verdict.contains("wanted"))
    }
    check("and the dutch roll is judged too",
      judged.exists(r => r.modeName == "Dutch-roll" && r.pass.isDefined))

    println(if (ok) "MODAL_REPORT_OK" else "MODAL_REPORT_FAIL")
    if (!ok) sys.exit(1)
  }
}
