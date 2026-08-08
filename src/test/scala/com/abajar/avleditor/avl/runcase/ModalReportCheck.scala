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

  /**
   * `spanMetres` of 0 is an aircraft whose size never reached us, which is what a calculation built by hand
   * has — and it is quoted verbatim, never guessed at, so most of these cases are judged by the standard's
   * own numbers.
   */
  private def run(eigenvalues: Seq[(Float, Float)], spanMetres: Float = 0f): AvlCalculation = {
    val calc = new AvlCalculation(0, 0, 0)
    val config = new Configuration
    config.setBref(spanMetres)
    config.setMetresPerLengthUnit(1f)
    calc.setConfiguration(config)
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
      pitched.axis == RunawayAxis.Pitch && pitched.says.contains("centre of gravity"))
    val yawRunaway = run(Seq((2.0f, 0f)))
    yawRunaway.getEigenvalues.get(0).setModeStateAmplitude("v", 1.0f)
    yawRunaway.getEigenvalues.get(0).setModeStateAmplitude("r", 0.9f)
    yawRunaway.getEigenvalues.get(0).setModeStateAmplitude("phi", 0.8f)
    val yawed = MilF8785cEvaluator.divergences(yawRunaway).head
    println("  ! " + yawed.says)
    check("a fast lateral one names the fin", yawed.says.contains("fin"))
    check("and without a mode shape it says it cannot tell",
      MilF8785cEvaluator.divergences(run(Seq((1.0f, 0f)))).head.axis == RunawayAxis.Unknown)
    // A mode shape with nothing dominant is a third case, and it used to fall into the final `else` and be
    // announced as "yaw and roll" with total confidence.
    val mixedRunaway = run(Seq((1.5f, 0f)))
    mixedRunaway.getEigenvalues.get(0).setModeStateAmplitude("q", 1.0f)
    mixedRunaway.getEigenvalues.get(0).setModeStateAmplitude("r", 1.0f)
    val mixed = MilF8785cEvaluator.divergences(mixedRunaway).head
    println("  ! " + mixed.says)
    check("and a mode with no dominant axis is not assigned one",
      mixed.axis == RunawayAxis.Mixed && mixed.says.contains("No one axis"))

    println("an oscillation that grows is a runaway, not a badly damped mode")
    // The hole this closes: sigma > 0 with omega > 0 was excluded from the divergences (which demanded
    // omega ~ 0) and went into the table instead, where zeta comes out negative and the verdict read
    // "too lightly damped" — as though it merely wanted a bigger fin, rather than growing every swing.
    val growing = run(Seq((0.5f, 2.0f)))
    val growingDivergence = MilF8785cEvaluator.divergences(growing)
    growingDivergence.foreach(d => println("  ! " + d.says))
    check("it is reported as a runaway at all", growingDivergence.size == 1)
    check("it says the swings grow", growingDivergence.head.says.contains("swings grow"))
    check("it knows it oscillates, and says how often",
      growingDivergence.head.oscillates && growingDivergence.head.period.isDefined)
    check("and it reaches the headline",
      MilF8785cEvaluator.runawaySummary(growing).exists(_.contains("will not fly")))

    println("a mode that neither grows nor decays is said out loud")
    // The other hole: sigma exactly 0 was excluded by `sigma > 0` from the runaways and by `omega > 0` from
    // the table, so it vanished from both.
    val neutral = MilF8785cEvaluator.neutralModes(run(Seq((0.0f, 0f), (-1.0f, 2.0f))))
    neutral.foreach(l => println("  ~ " + l))
    check("the neutral mode is reported", neutral.size == 1)
    check("and named as the boundary of stability, not as a fault",
      neutral.head.contains("boundary of stability"))
    check("a neutral mode is not called a runaway",
      MilF8785cEvaluator.divergences(run(Seq((0.0f, 0f)))).isEmpty)

    println("when there are oscillatory modes, this is not used")
    val oscillatory = run(Seq((-1.0f, 3.0f), (-0.1f, 0.4f)))
    check("the evaluator has modes to judge",
      MilF8785cEvaluator.oscillatoryPositiveModes(oscillatory).size == 2)
    // Without the mode shapes AVL prints alongside each eigenvalue there is nothing to tell a short
    // period from a dutch roll, and the evaluator says so rather than picking one. The roll mode and the
    // spiral are real roots, so they are absent here for a different reason and are not part of this.
    val oscillatoryRows = MilF8785cEvaluator.evaluate(oscillatory, FlightPhaseCategory.B)
      .filter(r => Set("Short-period", "Dutch-roll", "Phugoid").contains(r.modeName))
    check("but it will not name a mode it cannot identify",
      oscillatoryRows.forall(row => row.pass.isEmpty && row.verdict.contains("Not judged")))
    check("and says it is the run that cannot tell them apart, not the aircraft that lacks them",
      oscillatoryRows.forall(_.verdict.contains("no mode shapes")))

    println("and the row says what the motion is, not only what the standard calls it")
    val unnamed = MilF8785cEvaluator.evaluate(oscillatory, FlightPhaseCategory.B)
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
    val judged = MilF8785cEvaluator.evaluate(shaped, FlightPhaseCategory.B)
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

    val failing = MilF8785cEvaluator.evaluate(shaped, FlightPhaseCategory.B).find(_.pass == Some(false))
    failing.foreach { r =>
      println("    " + r.modeName + ": " + r.verdict)
      // Not "FAIL" and a symbol: which way it misses, by how much, and what moves it.
      check("a failing row says which way it falls short, and by how much",
        r.verdict.length > 60 && r.verdict.exists(_.isDigit) && r.verdict.contains("wanted"))
    }
    check("and the dutch roll is judged too",
      judged.exists(r => r.modeName == "Dutch-roll" && r.pass.isDefined))

    println("TABLE VI's footnote, which used to be ignored")
    // wn 2.0, zeta 0.15 -> zeta*wn = 0.30, comfortably over the tabulated 0.15 for Category B Level 1.
    // But at 20 m/s with v = 1.0 and phi = 0.5, beta = 0.05 and |phi/beta| = 10, so wn^2|phi/beta| = 40 —
    // twice the 20 the footnote triggers at. The excess of 20 raises the minimum by 0.014 x 20 = 0.28, to
    // 0.43, and 0.30 no longer reaches it. The aircraft drops a Level because of the footnote alone.
    val augmented = run(Seq((-0.3f, 1.97737f)))
    augmented.getConfiguration.setVelocity(20f)
    val wag = augmented.getEigenvalues.get(0)
    wag.setModeStateAmplitude("v", 1.0f)
    wag.setModeStateAmplitude("phi", 0.5f)
    wag.setModeStateAmplitude("r", 0.3f)
    check("beta comes from the lateral velocity and the speed, as the experiment established",
      math.abs(wag.phiOverBeta(20f) - 10.0f) < 0.01f)
    val augmentedRow = MilF8785cEvaluator.evaluate(augmented, FlightPhaseCategory.B).find(_.modeName == "Dutch-roll").get
    println("    " + augmentedRow.verdict)
    println("    " + augmentedRow.applied.getOrElse("(no augmentation)"))
    check("the requirement is raised above the tabulated one",
      augmentedRow.applied.exists(a => a.contains("0.43") && a.contains("footnote")))
    check("and it says what triggered it", augmentedRow.applied.exists(_.contains("40")))
    check("the aircraft drops to Level 2 because of it", augmentedRow.level == Some(2))
    // Below the trigger the footnote must not touch anything: same aircraft, a tenth of the bank angle.
    val untouched = run(Seq((-0.3f, 1.97737f)))
    untouched.getConfiguration.setVelocity(20f)
    untouched.getEigenvalues.get(0).setModeStateAmplitude("v", 1.0f)
    untouched.getEigenvalues.get(0).setModeStateAmplitude("phi", 0.05f)
    untouched.getEigenvalues.get(0).setModeStateAmplitude("r", 0.3f)
    val untouchedRow = MilF8785cEvaluator.evaluate(untouched, FlightPhaseCategory.B).find(_.modeName == "Dutch-roll").get
    println("    below the trigger: " + untouchedRow.verdict)
    check("under the trigger nothing is raised and the same aircraft meets Level 1",
      untouchedRow.level == Some(1) && untouchedRow.applied.isEmpty)

    println("the roll mode and the spiral, which are real roots and used to be thrown away")
    // Neither oscillates, so neither was ever reached: the table was drawn from the oscillatory modes alone
    // and an aircraft with nothing but real roots got no table at all.
    val lateralReal = run(Seq((-3.0f, 0f), (0.05f, 0f)))
    val rollRoot = lateralReal.getEigenvalues.get(0)
    rollRoot.setModeStateAmplitude("p", 1.0f)
    rollRoot.setModeStateAmplitude("phi", 0.2f)
    val spiralRoot = lateralReal.getEigenvalues.get(1)
    spiralRoot.setModeStateAmplitude("p", 0.1f)
    spiralRoot.setModeStateAmplitude("phi", 1.0f)
    spiralRoot.setModeStateAmplitude("psi", 0.8f)
    val realRows = MilF8785cEvaluator.evaluate(lateralReal, FlightPhaseCategory.B)
    realRows.foreach(r => println(f"    ${r.modeName}%-20s level=${r.level}%s  ${r.verdict}%s"))
    // tau = 1/3 s against TABLE VII's 1.4 s for Category B.
    val rollRow = realRows.find(_.modeName == "Roll mode").get
    check("the roll mode is found and judged", rollRow.level == Some(1))
    check("and says how long the roll takes to arrive", rollRow.verdict.contains("0.33 s"))
    // ln(2)/0.05 = 13.86 s, against TABLE VIII's 20 s for Level 1 and 8 s for Level 2.
    val spiralRow = realRows.find(_.modeName == "Spiral").get
    check("the spiral is found and judged", spiralRow.level == Some(2))
    // The Level lives in the field, not in the sentence: whoever displays this puts it in front, and the
    // window said "LEVEL 2 — Level 2 — flyable..." until somebody opened it and looked.
    check("and a Level below 1 still says what kept it from Level 1",
      spiralRow.level == Some(2) && spiralRow.verdict.contains("wanted") &&
        !spiralRow.verdict.contains("Level 2"))
    check("a table is drawn even though nothing oscillates", realRows.size == 15)
    // The spiral diverges, so it is also a runaway — and the two reports must agree about which it is.
    check("a divergent spiral is reported as a runaway as well",
      MilF8785cEvaluator.divergences(lateralReal).exists(_.axis == RunawayAxis.Spiral))

    println("a stable spiral meets every Level at once, and says so rather than being judged")
    val stableSpiral = run(Seq((-0.05f, 0f)))
    stableSpiral.getEigenvalues.get(0).setModeStateAmplitude("phi", 1.0f)
    stableSpiral.getEigenvalues.get(0).setModeStateAmplitude("psi", 0.8f)
    val stableRow = MilF8785cEvaluator.evaluate(stableSpiral, FlightPhaseCategory.B).find(_.modeName == "Spiral").get
    println("    " + stableRow.verdict)
    check("it meets Level 1", stableRow.level == Some(1))
    check("and says the bank rights itself", stableRow.verdict.contains("rights itself"))

    println("the same aircraft, judged at its own size")
    // A spiral doubling in 13.86 s misses TABLE VIII's 20 s. At 1.5 m of span that 20 s becomes 7.4 s, and
    // the same motion now meets Level 1 — which is the point: 20 s was written for an airplane forty times
    // the size, and applying it unchanged fails a model for being small.
    val small = run(Seq((-3.0f, 0f), (0.05f, 0f)), spanMetres = 1.5f)
    small.getEigenvalues.get(0).setModeStateAmplitude("p", 1.0f)
    small.getEigenvalues.get(0).setModeStateAmplitude("phi", 0.2f)
    small.getEigenvalues.get(1).setModeStateAmplitude("p", 0.1f)
    small.getEigenvalues.get(1).setModeStateAmplitude("phi", 1.0f)
    small.getEigenvalues.get(1).setModeStateAmplitude("psi", 0.8f)
    val smallSpiral = MilF8785cEvaluator.evaluate(small, FlightPhaseCategory.B).find(_.modeName == "Spiral").get
    println("    " + smallSpiral.verdict)
    println("    " + smallSpiral.applied.getOrElse("(not scaled)"))
    check("the same motion reaches Level 1 at model size", smallSpiral.level == Some(1))
    check("and the row states what the requirement became", smallSpiral.applied.isDefined)
    check("while still stating what the standard says",
      smallSpiral.requirement.contains("20 s"))
    check("a full-size aircraft has nothing scaled",
      MilF8785cEvaluator.evaluate(run(Seq((0.05f, 0f)), spanMetres = 30f), FlightPhaseCategory.B)
        .forall(_.applied.isEmpty))

    println("a response integrated out of a divergence is not a measurement")
    // Both 3.3.2.2 and 3.3.2.4 are features of a time history, and a time history of a divergent system
    // measures how long it was integrated. The sample quoted "25.2 degrees of proverse sideslip" and "the
    // roll rate holds 100 % of its peak" on an aeroplane whose yaw doubles every 0.12 s.
    def lateralRows(sigma: Float): List[ModalNormRow] = {
      val calc = run(Seq((sigma, 0f), (-8.0f, 0f)), spanMetres = 1.2f)
      calc.getEigenvalues.get(0).setModeStateAmplitude("v", 1.0f)
      calc.getEigenvalues.get(0).setModeStateAmplitude("r", 0.9f)
      calc.getEigenvalues.get(0).setModeStateAmplitude("phi", 0.3f)
      val config = calc.getConfiguration
      config.setVelocity(14f); config.setSecondsPerTimeUnit(1f); config.setCLtot(0.4f)
      config.setSref(0.24f); config.setCref(0.2f); config.setAlpha(3.4f)
      config.setAnalysisInertias(1.17f, 0.03f, 0.05f, 0f, 1.225f)
      val stab = new StabilityDerivatives
      stab.initControls(3)
      stab.setCLa(5.7f); stab.setCnb(0.07f); stab.setCYb(-0.2f); stab.setClb(-0.04f)
      stab.setClp(-0.4f); stab.setCnr(-0.06f); stab.setClr(0.08f); stab.setCnp(-0.02f)
      stab.getCld()(2) = 0.05f
      calc.setStabilityDerivatives(stab)
      calc.setControlGains(Array(1f, 1f, 20f))
      calc.setControlMaxDeflections(Array(30f, 30f, 25f))
      MilF8785cEvaluator.evaluate(calc, FlightPhaseCategory.B)
    }
    // Doubling every 0.12 s, far inside any window these are read over.
    val fast = lateralRows(5.568f)
    Seq("Roll rate oscillation", "Sideslip in a roll").foreach { name =>
      val row = fast.find(_.modeName == name).get
      println("  " + name + ": " + row.verdict.take(96))
      check(s"'$name' is not judged on a divergent response",
        row.outcome == RowOutcome.NotJudged && row.level.isEmpty && row.pass.isEmpty)
      check(s"'$name' says it is the window and not the aircraft",
        row.verdict.contains("runs away") && row.verdict.contains("how long it was integrated"))
      check(s"'$name' quotes the doubling time and the window", row.verdict.contains("doubles every"))
    }
    // A slow spiral is what most models have and is flown through; it must not stop the measurement.
    val slow = lateralRows(0.044f)
    Seq("Roll rate oscillation", "Sideslip in a roll").foreach { name =>
      val row = slow.find(_.modeName == name).get
      check(s"'$name' is still measured through a slow spiral",
        !row.verdict.contains("how long it was integrated"))
    }

    println("the Flight Phase changes what is asked, and all three are answered rather than chosen")
    // TABLE IV: Category B wants 0.30 of short-period damping, Category A wants 0.35. Same aircraft.
    val marginal = run(Seq((-1.0f, 3.05f)))
    marginal.getEigenvalues.get(0).setModeStateAmplitude("w", 1.0f)
    marginal.getEigenvalues.get(0).setModeStateAmplitude("q", 0.9f)
    marginal.getEigenvalues.get(0).setModeStateAmplitude("the", 0.7f)
    def shortLevel(category: FlightPhaseCategory): Option[Int] =
      MilF8785cEvaluator.evaluate(marginal, category).find(_.modeName == "Short-period").get.level
    println(f"    zeta ${marginal.getEigenvalues.get(0).getDampingRatio}%.3f: " +
      f"Category B -> ${shortLevel(FlightPhaseCategory.B)}%s, Category A -> ${shortLevel(FlightPhaseCategory.A)}%s")
    check("gentle flying accepts it", shortLevel(FlightPhaseCategory.B) == Some(1))
    check("and rapid maneuvering does not", shortLevel(FlightPhaseCategory.A) == Some(2))

    // It used to be a field on the model, so this same aircraft answered one of those two and kept the
    // other to itself. Judging a Category is arithmetic over what the run already produced, so all three
    // are answered and the reader takes the column that is their aeroplane's life.
    val everyPhase = MilF8785cEvaluator.evaluateEveryCategory(marginal)
    val shortPeriodEverywhere = everyPhase.find(_.modeName == "Short-period").get
    println("    all three: " + shortPeriodEverywhere.byCategory
      .map { case (c, row) => f"${c.label}%s -> ${row.level.map(_.toString).getOrElse("-")}%s" }
      .mkString(", "))
    check("every motion carries all three Categories",
      everyPhase.forall(_.byCategory.map(_._1) == FlightPhaseCategory.All))
    check("and the same motions come back as judging them one at a time",
      everyPhase.map(_.modeName) ==
        MilF8785cEvaluator.evaluate(marginal, FlightPhaseCategory.B).map(_.modeName))
    check("the three verdicts are the three single judgements",
      FlightPhaseCategory.All.forall(category =>
        shortPeriodEverywhere.rowFor(category).map(_.level) == Some(shortLevel(category))))
    // What was measured is the aeroplane and does not change with the mission; what it is compared against
    // does. That split is the whole reason the row holds one measurement and three verdicts.
    check("the measurement is shared, being a property of the mode",
      shortPeriodEverywhere.byCategory.map(_._2.zeta).distinct.length == 1 &&
        shortPeriodEverywhere.zeta == shortPeriodEverywhere.byCategory.head._2.zeta)
    check("and the rule is not",
      shortPeriodEverywhere.byCategory.map(_._2.requirement).distinct.length > 1)
    // 3.2.1.3 is stated for the landing approach and for nothing else, which used to read as "does not
    // apply" to everybody who never found the field. Now it is answered in the column where it applies.
    val flightPath = everyPhase.find(_.modeName == "Flight-path stability").get
    check("a Category C requirement says so in A and B",
      Seq(FlightPhaseCategory.A, FlightPhaseCategory.B).forall(c =>
        flightPath.rowFor(c).exists(_.outcome == RowOutcome.DoesNotApply)))
    check("and is actually judged in C",
      flightPath.rowFor(FlightPhaseCategory.C).exists(_.outcome != RowOutcome.DoesNotApply))

    println(if (ok) "MODAL_REPORT_OK" else "MODAL_REPORT_FAIL")
    if (!ok) sys.exit(1)
  }
}
