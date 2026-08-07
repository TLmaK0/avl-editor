/*
 * MIL-F-8785C 3.3.4, TABLE IXa (PDF p. 27): how long the aircraft takes to bank with the roll control hard
 * over. It is the criterion a model flyer would notice first and the editor never computed it, because a
 * derivative says how much rolling moment per degree and nothing at all about how many degrees there are.
 *
 * Everything it needs the model already states, so the point of this check is that nothing is invented:
 * the roll rate is the one that balances the roll damping, the time comes from a first-order roll response,
 * and every input that is missing is refused by name instead of defaulted.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.runcase.RollPerformanceCheck"
 */
package com.abajar.avleditor.avl.runcase

import scala.collection.JavaConverters._

object RollPerformanceCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  // A sport model: 1.6 m of span at 20 m/s, ailerons on 20 deg/unit gain stopping at 25 degrees.
  private val Span = 1.6f
  private val Speed = 20.0f
  private val Clp = -0.5f
  private val Cld = 0.05f
  private val Gain = 20.0f
  private val Stop = 25.0f
  private val RollModeSigma = -10.0f

  private def aircraft(clp: Float = Clp, cld: Float = Cld, gain: Float = Gain, stop: Float = Stop,
                       span: Float = Span, speed: Float = Speed,
                       withRollMode: Boolean = true): AvlCalculation = {
    // Controls are elevator 0, rudder 1, aileron 2.
    val calc = new AvlCalculation(0, 1, 2)
    val config = new Configuration
    config.setBref(span)
    config.setVelocity(speed)
    config.setMetresPerLengthUnit(1f)
    config.setSecondsPerTimeUnit(1f)
    calc.setConfiguration(config)

    val stab = new StabilityDerivatives
    stab.initControls(3)
    stab.setClp(clp)
    stab.getCld()(2) = cld
    calc.setStabilityDerivatives(stab)

    calc.setControlGains(Array(1f, 1f, gain))
    calc.setControlMaxDeflections(Array(30f, 30f, stop))

    val modes = new java.util.ArrayList[AvlEigenvalue]()
    if (withRollMode) {
      val roll = new AvlEigenvalue(RollModeSigma, 0f)
      roll.setModeStateAmplitude("p", 1.0f)
      roll.setModeStateAmplitude("phi", 0.2f)
      modes.add(roll)
    }
    calc.setEigenvalues(modes)
    calc
  }

  private def row(calc: AvlCalculation,
                  category: FlightPhaseCategory = FlightPhaseCategory.B): ModalNormRow =
    MilF8785cEvaluator.evaluate(calc, category).find(_.modeName == "Roll response").get

  def main(args: Array[String]): Unit = {
    println("the roll rate is the one that balances the roll damping")
    // Cl at the stop: the control variable reaching 25 deg on a gain of 20 deg/unit is 1.25, so Cl = 0.0625.
    // Steady roll: Cl = -Clp (p b / 2V)  ->  p = 0.0625/0.5 * 2*20/1.6 = 3.125 rad/s = 179 deg/s.
    val expectedRate = math.toDegrees(0.0625 / 0.5 * (2.0 * Speed / Span))
    val judged = row(aircraft())
    println("    " + judged.verdict)
    println(f"    closed form: ${expectedRate}%.0f deg/s")
    check("the roll rate AVL's derivatives imply is the one reported",
      judged.verdict.contains(f"${expectedRate}%.0f deg/s"))
    check("and the aileron travel it used is stated", judged.verdict.contains("25 degrees of aileron"))

    println("and the time to bank comes from a first-order roll response, not from the rate alone")
    // phi(t) = p (t - tau (1 - e^-t/tau)) with tau = 0.1 s reaches 60 deg at t = 0.435 s; the rate alone
    // would say 1.047/3.125 = 0.335 s, and the difference is the roll taking time to build up.
    val rate = 0.0625 / 0.5 * (2.0 * Speed / Span)
    val tau = -1.0 / RollModeSigma
    def bank(t: Double) = rate * (t - tau * (1.0 - math.exp(-t / tau)))
    val naive = math.toRadians(60.0) / rate
    println(f"    ignoring the build-up would give $naive%.3f s")
    check("the answer is later than the roll rate alone would say", judged.verdict.contains("0.44 s") ||
      judged.verdict.contains("0.43 s"))
    check("and the response really reaches 60 degrees there",
      math.abs(math.toDegrees(bank(0.435)) - 60.0) < 1.0)

    println("judged against TABLE IXa, at the aircraft's own size")
    // Class I, Category B, Level 1 wants 60 degrees in 1.7 s. At 1.6 m of span that is 0.69 s, and this
    // model banks in 0.44 s.
    check("it reaches Level 1", judged.level == Some(1))
    check("the requirement is stated as the standard writes it", judged.requirement.contains("1.7 s"))
    check("and as it applies at this size", judged.applied.exists(_.contains("0.69 s")))

    println("a sluggish aircraft is marked down rather than passed")
    val sluggish = row(aircraft(cld = 0.004f))
    println("    " + sluggish.verdict)
    check("it does not reach Level 1", sluggish.level != Some(1))
    check("and says which way it falls short", sluggish.verdict.contains("wanted"))
    check("and what to change about it", sluggish.verdict.contains("aileron"))

    println("the Flight Phase changes what is asked of the same aircraft")
    // Category A wants 60 degrees in 1.3 s, Category C only 30 degrees but in 1.3 s.
    check("Category A asks more than Category B",
      row(aircraft(), FlightPhaseCategory.A).requirement.contains("1.3 s"))
    check("and Category C measures a different angle",
      row(aircraft(), FlightPhaseCategory.C).requirement.contains("30 degrees"))

    println("and nothing is invented when an input is missing")
    def refusal(name: String, calc: AvlCalculation, fragment: String): Unit = {
      val r = row(calc)
      println("    " + name + ": " + r.verdict)
      check(name + " is refused by name",
        r.level.isEmpty && r.verdict.startsWith("Not judged") && r.verdict.contains(fragment))
    }
    refusal("no roll mode", aircraft(withRollMode = false), "no roll mode")
    refusal("a gain of zero", aircraft(gain = 0f), "gain is zero")
    refusal("no aileron travel", aircraft(stop = Float.NaN), "maximum deflection")
    refusal("roll damping that is not one", aircraft(clp = 0.2f), "not a damping")
    refusal("an aileron doing nothing", aircraft(cld = 0f), "no rolling moment")
    refusal("no span or speed", aircraft(span = 0f), "span or the speed")

    println("an aircraft with no aileron at all is refused too, not scored zero")
    val noAileron = aircraft()
    val noAileronCalc = new AvlCalculation(0, 1, -1)
    noAileronCalc.setConfiguration(noAileron.getConfiguration)
    noAileronCalc.setStabilityDerivatives(noAileron.getStabilityDerivatives)
    noAileronCalc.setControlGains(noAileron.getControlGains)
    noAileronCalc.setControlMaxDeflections(noAileron.getControlMaxDeflections)
    noAileronCalc.setEigenvalues(noAileron.getEigenvalues)
    val none = row(noAileronCalc)
    println("    " + none.verdict)
    check("it says there is no aileron", none.verdict.contains("no aileron"))

    println(if (ok) "ROLL_PERFORMANCE_OK" else "ROLL_PERFORMANCE_FAIL")
    if (!ok) sys.exit(1)
  }
}
