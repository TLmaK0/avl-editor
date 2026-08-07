/*
 * MIL-F-8785C 3.2.1.1 (p. 11) and 3.3.6 (pp. 32-33): the two static requirements that survive the jump to a
 * radio-controlled model.
 *
 * Both were nearly written from a wrong reading. 3.2.1.1 is *not* "Cma < 0" or a static margin — it is
 * longitudinal stability **with respect to speed**, and says there shall be no aperiodic airspeed
 * divergence, with a Level 3 relaxation of 6 seconds to double. And 3.3.6.1 and 3.3.6.2 turn out to be
 * written in pedal forces and in a linearity that AVL, a linear solver, cannot fail — so what is asserted
 * here is the sign convention they encode, plus 3.3.6.3.2, which is the one requirement in that family that
 * is quantitative and needs no forces.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.runcase.StaticStabilityCheck"
 */
package com.abajar.avleditor.avl.runcase

object StaticStabilityCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** Conventional signs: the nose weathercocks, the side force opposes, the dihedral effect is positive. */
  private def aircraft(cnb: Float = 0.076f, cyb: Float = -0.30f, clb: Float = -0.045f,
                       cld: Float = 0.05f, gain: Float = 20f, stop: Float = 25f,
                       span: Float = 0f, modes: Seq[(Float, Float)] = Nil): AvlCalculation = {
    val calc = new AvlCalculation(0, 1, 2)
    val config = new Configuration
    config.setBref(span)
    config.setVelocity(20f)
    config.setMetresPerLengthUnit(1f)
    config.setSecondsPerTimeUnit(1f)
    calc.setConfiguration(config)

    val stab = new StabilityDerivatives
    stab.initControls(3)
    stab.setCnb(cnb)
    stab.setCYb(cyb)
    stab.setClb(clb)
    stab.getCld()(2) = cld
    calc.setStabilityDerivatives(stab)

    calc.setControlGains(Array(1f, 1f, gain))
    calc.setControlMaxDeflections(Array(30f, 30f, stop))
    calc.setEigenvalues(new java.util.ArrayList[AvlEigenvalue](
      scala.collection.JavaConverters.seqAsJavaListConverter(
        modes.map { case (s, o) => new AvlEigenvalue(s, o) }).asJava))
    calc
  }

  private def row(calc: AvlCalculation, name: String): ModalNormRow =
    MilF8785cEvaluator.evaluate(calc).find(_.modeName == name).get

  /** A real divergence whose mode shape is speed-dominated: u and theta, no pitch rate to speak of. */
  private def speedRunaway(sigma: Float, calc: AvlCalculation): AvlCalculation = {
    val mode = calc.getEigenvalues.get(0)
    mode.setModeStateAmplitude("u", 1.0f)
    mode.setModeStateAmplitude("the", 0.6f)
    mode.setModeStateAmplitude("w", 0.05f)
    calc
  }

  def main(args: Array[String]): Unit = {
    println("3.2.1.1 is about speed, not about Cma")
    val settled = row(aircraft(modes = Seq((-1.0f, 2.0f))), "Speed stability")
    println("    " + settled.verdict)
    check("an aircraft with nothing running away in speed meets it", settled.level == Some(1))
    check("and the requirement is stated as the standard states it",
      settled.requirement.contains("no aperiodic speed divergence"))

    // ln(2)/0.05 = 13.86 s, over the 6 s Level 3 allows: flyable, but Levels 1 and 2 allow none at all.
    val slow = speedRunaway(0.05f, aircraft(modes = Seq((0.05f, 0f))))
    val slowRow = row(slow, "Speed stability")
    println("    " + slowRow.verdict)
    check("a slow speed divergence is Level 3, never Level 1", slowRow.level == Some(3))
    check("and it says Levels 1 and 2 allow none of it",
      slowRow.verdict.contains("Levels 1 and 2 allow none"))

    // ln(2)/0.3 = 2.31 s, under the 6 s floor.
    val fast = speedRunaway(0.3f, aircraft(modes = Seq((0.3f, 0f))))
    val fastRow = row(fast, "Speed stability")
    println("    " + fastRow.verdict)
    check("a fast one is worse than Level 3", fastRow.level.isEmpty)
    check("and says how fast it doubles", fastRow.verdict.contains("2.31 s"))

    println("and the 6 seconds is a time, so it follows the aircraft's size")
    val small = row(speedRunaway(0.05f, aircraft(span = 1.5f, modes = Seq((0.05f, 0f)))), "Speed stability")
    println("    " + small.applied.getOrElse("(not scaled)"))
    check("a model's allowance is stated too", small.applied.exists(_.contains("2.3 s")))
    check("a full-size aircraft has nothing scaled",
      row(speedRunaway(0.05f, aircraft(span = 30f, modes = Seq((0.05f, 0f)))), "Speed stability")
        .applied.isEmpty)

    println("3.3.6.3.2: what the dihedral effect costs in aileron")
    // Roll control at the stop: Cl = 0.05 x (25/20) = 0.0625. Holding 10 degrees of sideslip needs
    // |Clb| x 0.1745 = 0.045 x 0.1745 = 0.007854, which is 12.6 % of it.
    val judged = row(aircraft(), "Steady sideslip")
    println("    " + judged.verdict)
    check("it reaches Level 1", judged.level == Some(1))
    check("and says what fraction of the aileron it costs", judged.verdict.contains("13%"))
    check("the requirement quotes the standard's 75 %", judged.requirement.contains("75%"))
    check("and the ten degrees 3.3.7.1 puts on it", judged.requirement.contains("10 degrees"))

    // Clb of -0.30 needs 0.30 x 0.1745 = 0.05236, which is 84 % of 0.0625 — over the 75 % allowed.
    val tooMuchDihedral = row(aircraft(clb = -0.30f), "Steady sideslip")
    println("    " + tooMuchDihedral.verdict)
    check("too much dihedral for the ailerons is refused", tooMuchDihedral.level.isEmpty)
    check("and it says which way to move", tooMuchDihedral.verdict.contains("Less dihedral, or more aileron"))

    println("and the signs those sections encode")
    def signCase(name: String, calc: AvlCalculation, fragment: String): Unit = {
      val r = row(calc, "Steady sideslip")
      println("    " + name + ": " + r.verdict)
      check(name + " is caught", r.level.isEmpty && r.verdict.contains(fragment))
    }
    signCase("no weathercock", aircraft(cnb = -0.01f), "does not weathercock")
    signCase("side force the wrong way", aircraft(cyb = 0.10f), "does not oppose")
    signCase("negative dihedral effect", aircraft(clb = 0.02f), "no positive effective dihedral")

    println("and nothing is invented when the aileron is not known")
    val noAileron = new AvlCalculation(0, 1, -1)
    noAileron.setConfiguration(aircraft().getConfiguration)
    noAileron.setStabilityDerivatives(aircraft().getStabilityDerivatives)
    noAileron.setControlGains(aircraft().getControlGains)
    noAileron.setControlMaxDeflections(aircraft().getControlMaxDeflections)
    noAileron.setEigenvalues(new java.util.ArrayList[AvlEigenvalue]())
    val partial = row(noAileron, "Steady sideslip")
    println("    " + partial.verdict)
    check("the signs are still reported, and the rest is refused by name",
      partial.level.isEmpty && partial.verdict.startsWith("Partly judged") &&
        partial.verdict.contains("no aileron"))

    println(if (ok) "STATIC_STABILITY_OK" else "STATIC_STABILITY_FAIL")
    if (!ok) sys.exit(1)
  }
}
