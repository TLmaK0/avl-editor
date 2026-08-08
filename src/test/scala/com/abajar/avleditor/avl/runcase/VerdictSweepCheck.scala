/*
 * Every branch of the evaluator, walked.
 *
 * The verdicts are total by construction — every if/else chain ends in an else — so the evaluator always
 * answers. That is the cheapest possible guarantee and it is exactly the dangerous one: a function that
 * always answers cannot tell you it has met a case nobody thought about. Roughly half the text branches had
 * never been executed by any check.
 *
 * So this walks a grid of what AVL can return — every sign of sigma against every kind of omega, with and
 * without mode shapes, at three sizes — and asserts the properties that must hold whatever comes back,
 * rather than the wording of any particular case.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.runcase.VerdictSweepCheck"
 */
package com.abajar.avleditor.avl.runcase

import scala.collection.JavaConverters._

object VerdictSweepCheck {

  private var ok = true
  private var cases = 0

  private def check(name: String, cond: Boolean): Unit = {
    if (!cond) { println("  FAIL " + name); ok = false }
  }

  /** The mode shapes AVL can give, including none at all and one with nothing dominant. */
  private val shapes: List[(String, AvlEigenvalue => Unit)] = List(
    ("none", _ => ()),
    ("pitch", m => { m.setModeStateAmplitude("w", 1f); m.setModeStateAmplitude("q", 0.9f)
                     m.setModeStateAmplitude("the", 0.7f) }),
    ("speed", m => { m.setModeStateAmplitude("u", 1f); m.setModeStateAmplitude("the", 0.6f)
                     m.setModeStateAmplitude("w", 0.05f) }),
    ("yaw", m => { m.setModeStateAmplitude("v", 1f); m.setModeStateAmplitude("r", 0.9f)
                   m.setModeStateAmplitude("phi", 0.3f) }),
    ("roll rate", m => { m.setModeStateAmplitude("p", 1f); m.setModeStateAmplitude("phi", 0.2f) }),
    ("bank", m => { m.setModeStateAmplitude("phi", 1f); m.setModeStateAmplitude("psi", 0.8f)
                    m.setModeStateAmplitude("p", 0.05f) }),
    ("mixed", m => { m.setModeStateAmplitude("q", 1f); m.setModeStateAmplitude("r", 1f) })
  )

  private val sigmas = List(-15.0f, -1.0f, -0.05f, 0.0f, 0.05f, 1.0f, 15.0f)
  private val omegas = List(0.0f, 0.3f, 3.0f, 12.0f)
  private val spans = List(0f, 1.5f, 30f)

  private def calculation(sigma: Float, omega: Float, shape: AvlEigenvalue => Unit,
                          span: Float): AvlCalculation = {
    val calc = new AvlCalculation(0, 1, 2)
    val config = new Configuration
    config.setBref(span)
    config.setVelocity(20f)
    config.setCLtot(0.4f)
    config.setMetresPerLengthUnit(1f)
    config.setSecondsPerTimeUnit(1f)
    calc.setConfiguration(config)
    val stab = new StabilityDerivatives
    stab.initControls(3)
    stab.setCLa(4.5f); stab.setCnb(0.07f); stab.setCYb(-0.2f); stab.setClb(-0.04f); stab.setClp(-0.4f)
    stab.getCld()(2) = 0.05f
    calc.setStabilityDerivatives(stab)
    calc.setControlGains(Array(1f, 1f, 20f))
    calc.setControlMaxDeflections(Array(30f, 30f, 25f))
    val mode = new AvlEigenvalue(sigma, omega)
    shape(mode)
    val modes = new java.util.ArrayList[AvlEigenvalue]()
    modes.add(mode)
    if (omega != 0f) { // a real system's complex roots arrive in pairs
      val conjugate = new AvlEigenvalue(sigma, -omega)
      shape(conjugate)
      modes.add(conjugate)
    }
    calc.setEigenvalues(modes)
    calc
  }

  def main(args: Array[String]): Unit = {
    println("every sign of sigma, every kind of omega, every mode shape, at three sizes")
    val categories = List(FlightPhaseCategory.A, FlightPhaseCategory.B, FlightPhaseCategory.C)
    var rowCount = -1

    for (sigma <- sigmas; omega <- omegas; (shapeName, shape) <- shapes; span <- spans;
         category <- categories) {
      cases += 1
      val calc = calculation(sigma, omega, shape, span)
      val where = f"sigma $sigma%.2f omega $omega%.2f shape $shapeName%s span $span%.1f cat ${category.label}%s"
      val rows = MilF8785cEvaluator.evaluate(calc, category)

      if (rowCount < 0) rowCount = rows.size
      check(s"the same rows come back every time ($where)", rows.size == rowCount)

      // The report judges all three Flight Phases and joins them by position, which is only sound if the
      // three passes return the same motions in the same order. Asserted here rather than assumed there,
      // across every case in the sweep.
      val everyPhase = MilF8785cEvaluator.evaluateEveryCategory(calc)
      check(s"all three Categories are judged, in order ($where)",
        everyPhase.forall(_.byCategory.map(_._1) == FlightPhaseCategory.All))
      check(s"and they name the same motions in the same order ($where)",
        everyPhase.map(_.modeName) == rows.map(_.modeName) &&
          everyPhase.forall(m => m.byCategory.forall(_._2.modeName == m.modeName)))
      check(s"each column is the single judgement for that Category ($where)",
        everyPhase.forall(m => m.rowFor(category).map(_.verdict) ==
          rows.find(_.modeName == m.modeName).map(_.verdict)))

      rows.foreach { row =>
        check(s"'${row.modeName}' always says something ($where)", row.verdict.trim.nonEmpty)
        check(s"'${row.modeName}' names the motion ($where)", row.whatItIs.trim.nonEmpty)
        check(s"'${row.modeName}' states its rule ($where)", row.requirement.trim.nonEmpty)
        // The Level and the pass flag are two views of one fact and must not disagree.
        check(s"'${row.modeName}' agrees with itself about Level 1 ($where)",
          (row.pass == Some(true)) == (row.level == Some(1)))
        check(s"'${row.modeName}' reports a Level the standard has ($where)",
          row.level.forall(n => n >= 1 && n <= 3))
        // Nothing may leak a formatting failure into a verdict a user reads.
        check(s"'${row.modeName}' has no NaN or Infinity in it ($where)",
          !row.verdict.contains("NaN") && !row.verdict.contains("Infinity") &&
            !row.verdict.contains("null"))
        check(s"'${row.modeName}' has no NaN in what was applied ($where)",
          row.applied.forall(a => !a.contains("NaN") && !a.contains("Infinity")))
        // The outcome is a sealed set, so this reads it rather than sniffing the verdict's opening words —
        // which is what it did until the set existed, and is the same failure as the if/else it came from.
        row.outcome match {
          case RowOutcome.Reached(n) =>
            check(s"'${row.modeName}' reaching Level $n agrees with its Level field ($where)",
              row.level == Some(n) && n >= 1 && n <= 3)
            check(s"'${row.modeName}' reaching a Level does not say it could not judge ($where)",
              !row.verdict.startsWith("Not judged") && !row.verdict.startsWith("Not found"))
          case RowOutcome.WorseThanLevelThree =>
            check(s"'${row.modeName}' below Level 3 claims no Level ($where)", row.level.isEmpty)
            check(s"'${row.modeName}' below Level 3 is not a pass ($where)", row.pass != Some(true))
          case RowOutcome.NotFound =>
            check(s"'${row.modeName}' not found claims no Level ($where)",
              row.level.isEmpty && row.pass.isEmpty)
            check(s"'${row.modeName}' not found says so ($where)", row.verdict.startsWith("Not found"))
          case RowOutcome.NotJudged =>
            check(s"'${row.modeName}' unjudged claims no Level ($where)",
              row.level.isEmpty && row.pass.isEmpty)
            check(s"'${row.modeName}' unjudged names what is missing ($where)",
              row.verdict.startsWith("Not judged") || row.verdict.startsWith("Partly judged"))
          case RowOutcome.OnTheBoundary =>
            check(s"'${row.modeName}' on the boundary claims no Level ($where)",
              row.level.isEmpty && row.pass.isEmpty)
            check(s"'${row.modeName}' on the boundary says which figure ($where)",
              row.verdict.contains("figure"))
          case RowOutcome.DoesNotApply =>
            check(s"'${row.modeName}' not applying claims no Level ($where)",
              row.level.isEmpty && row.pass.isEmpty)
            check(s"'${row.modeName}' not applying says so, and says for which phase ($where)",
              row.verdict.startsWith("Does not apply") && row.verdict.contains("Category"))
        }
      }

      // The runaway report and the table must tell the same story about the same aircraft.
      val runaways = MilF8785cEvaluator.divergences(calc)
      check(s"a growing motion is reported as one ($where)", (sigma > 0f) == runaways.nonEmpty)
      check(s"the headline appears exactly when something runs away ($where)",
        MilF8785cEvaluator.runawaySummary(calc).isDefined == runaways.nonEmpty)
      check(s"every runaway says how fast it doubles ($where)",
        runaways.forall(d => d.doublingTime > 0.0 && d.says.contains("doubles")))
      check(s"a neutral mode is reported exactly when one exists ($where)",
        (sigma == 0f) == MilF8785cEvaluator.neutralModes(calc).nonEmpty)

      // The hole this file exists to hunt: a mode that **grows** must never be described as one that is
      // merely poorly damped. Its damping ratio is negative, and "too lightly damped at -0.12" reads like
      // an aircraft that wants a bigger fin rather than one that is leaving.
      if (sigma > 0f && omega != 0f) {
        rows.filter(_.zeta.exists(_ < 0.0)).foreach { row =>
          check(s"'${row.modeName}' does not call a growing oscillation badly damped ($where): " +
            row.verdict, !row.verdict.toLowerCase.contains("lightly damped"))
        }
      }
    }

    println(f"  $cases%d cases, $rowCount%d rows each")
    println(if (ok) "VERDICT_SWEEP_OK" else "VERDICT_SWEEP_FAIL")
    if (!ok) sys.exit(1)
  }
}
