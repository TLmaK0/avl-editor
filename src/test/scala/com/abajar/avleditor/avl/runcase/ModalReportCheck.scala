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
    check("names the divergence", says(lines, "divergence"))
    // ln(2) / 4.92 = 0.141 s.
    check("with the time it doubles in", says(lines, "0.14 s"))
    check("and the centre of gravity as the usual cause", says(lines, "aft"))
    check("no oscillatory mode is claimed",
      MilF8785cEvaluator.oscillatoryPositiveModes(run(real)).isEmpty)

    println("a stable aircraft with a divergence in it")
    val mixed = MilF8785cEvaluator.whyNoModes(run(Seq((-2.0f, 0f), (0.5f, 0f))))
    check("one line per divergence, and only for those",
      mixed.count(_.contains("divergence")) == 1)

    println("when there are oscillatory modes, this is not used")
    val oscillatory = run(Seq((-1.0f, 3.0f), (-0.1f, 0.4f)))
    check("the evaluator has modes to judge",
      MilF8785cEvaluator.oscillatoryPositiveModes(oscillatory).size == 2)
    // Without the mode shapes AVL prints alongside each eigenvalue there is nothing to tell a short
    // period from a dutch roll, and the evaluator says so rather than picking one.
    check("but it will not name a mode it cannot identify",
      MilF8785cEvaluator.evaluate(oscillatory).forall(row =>
        row.pass.isEmpty && row.criterion.contains("not identifiable")))

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
    check("and the dutch roll is judged too",
      judged.exists(r => r.modeName == "Dutch-roll" && r.pass.isDefined))

    println(if (ok) "MODAL_REPORT_OK" else "MODAL_REPORT_FAIL")
    if (!ok) sys.exit(1)
  }
}
