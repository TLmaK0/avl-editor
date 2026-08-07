/*
 * The aircraft has to be measured across attitudes, not at one. Two things are easy to get wrong and both
 * are pinned here: the attitude must be imposed rather than asked for as a lift coefficient, and the controls
 * must be at neutral, because JSBSim adds the elevator's effect itself and would otherwise count it twice.
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.connectivity.AlphaSweepCheck"
 */
package com.abajar.avleditor.avl.connectivity

import com.abajar.avleditor.AvlManager
import java.io.File
import java.util.Properties
import scala.collection.JavaConverters._

object AlphaSweepCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  def main(args: Array[String]): Unit = {
    println("what is typed at AVL")
    val angles = AvlRunner.SWEEP_ANGLES_DEG
    val commands = AvlRunner.sweepCommands(30f, angles, "/tmp/sweep").asScala.toList
    println("  " + commands.take(10).map(c => if (c.trim.isEmpty) "<blank>" else c.trim).mkString(" | ") + " | ...")

    check("the attitude is imposed, not asked for as a coefficient",
      commands.count(_.startsWith("a a ")) == angles.length && !commands.exists(_.startsWith("a c ")))
    // No 'd<n> pm 0': trimming the elevator would put the trim inside Cm, which JSBSim adds again.
    check("the controls are left at neutral", !commands.exists(_.contains("pm 0")))
    check("one solve and one stability file per attitude",
      commands.count(_ == "x") == angles.length && commands.count(_ == "st") == angles.length)
    check("each file has its own name",
      commands.filter(_.endsWith(".st")).distinct.size == angles.length)
    // The spanwise loading rides along in the same session: the solve that produces the totals already
    // contains it, and a second run of AVL to fetch it would cost far more than the file does. It is what
    // the stall is found from — see SpanwiseLoadingCheck.
    check("the spanwise loading is asked for at every attitude too",
      commands.count(_ == "fs") == angles.length &&
        commands.filter(_.endsWith(".fs")).distinct.size == angles.length)
    check("still in one session, and one only",
      commands.count(_ == "oper") == 1 && commands.count(_ == "quit") == 1)
    check("it ends by leaving OPER and quitting",
      commands.takeRight(2) == List("", "quit") && !commands.contains("q"))

    println("the attitudes measured")
    println("  " + angles.mkString(", "))
    check("they are ordered, without repeats",
      angles.toList == angles.toList.sorted && angles.distinct.length == angles.length)
    check("they reach below level flight and past where a wing stops lifting",
      angles.min <= -10f && angles.max >= 15f)
    check("at least three, or it is not a curve", angles.length >= 3)

    println("running AVL on a real model")
    val props = new Properties()
    if (!AvlManager.ensureAvlAvailable(props)) {
      println("  AVL is not available here; the run part of this check needs it")
      println(if (ok) "ALPHA_SWEEP_OK" else "ALPHA_SWEEP_FAIL")
      if (!ok) sys.exit(1)
      return
    }

    // The check's own aircraft: a sample is the user's aeroplane and changes under the check's feet.
    val model = com.abajar.avleditor.TestAircraft.conventional()
    val runner = new AvlRunner(props.getProperty("avl.path"), model.getAvl, model.getOriginPath, 45f, 20f)
    val calc = runner.getCalculation()
    val sweep = calc.getAlphaSweep.asScala.toList
    sweep.foreach(p => println("  " + p))

    check("every attitude came back", sweep.length == angles.length)
    check("at the attitudes asked for",
      sweep.map(_.getAlphaDeg).toList == angles.toList)
    check("lift rises with attitude",
      sweep.map(_.getCl).sliding(2).forall(pair => pair.length < 2 || pair(1) > pair(0)))
    // Positive drag is physics. Where the minimum falls is not: on a canard delta the wing and the canard
    // can carry opposing loads, so the least-drag attitude need not be inside the swept range — asserting
    // otherwise would be a plausibility heuristic judging AVL's answer, which is what this project refuses.
    check("drag is positive everywhere", sweep.forall(_.getCd > 0))

    // The sweep and the stability file describe the same aircraft in two different states: the sweep with
    // the controls at neutral, the stability file trimmed. So they do not agree directly — they agree once
    // the control terms are added, which is exactly the sum JSBSim will compute from the exported model.
    // Getting this identity to close is what says the curve and the control derivatives belong together.
    val trimCl = calc.getConfiguration.getCLtot
    val trimAlpha = calc.getConfiguration.getAlpha
    val deflections = calc.getTrimControlValues
    val clDelta = calc.getStabilityDerivatives.getCLd
    val controlLift = (0 until math.min(deflections.length, clDelta.length))
      .map(i => if (deflections(i).isNaN) 0f else clDelta(i) * deflections(i)).sum
    val bracketing = sweep.filter(_.getAlphaDeg <= trimAlpha).lastOption
      .flatMap(low => sweep.find(_.getAlphaDeg > trimAlpha).map(high => (low, high)))
    println(f"  the trimmed point: alpha $trimAlpha%.3f deg, CL $trimCl%.4f")
    println("  trimmed deflections: " + calc.getControlNames.zip(deflections)
      .map { case (n, d) => f"$n%s $d%.4f" }.mkString(", "))
    check("the curve brackets the trimmed point", bracketing.isDefined)
    bracketing.foreach { case (low, high) =>
      val fraction = (trimAlpha - low.getAlphaDeg) / (high.getAlphaDeg - low.getAlphaDeg)
      val neutral = low.getCl + fraction * (high.getCl - low.getCl)
      println(f"  neutral-controls curve at that attitude: CL $neutral%.4f")
      println(f"  plus the trimmed controls (${controlLift}%.4f): CL ${neutral + controlLift}%.4f")
      check("the curve plus the control terms reconstructs the trimmed aircraft",
        math.abs(neutral + controlLift - trimCl) < 0.02f)
    }

    // The choice of what becomes a curve and what stays one number is a claim about the aircraft, so the
    // sweep measures it and the log says so. This is that report, as the user reads it after a run.
    println("what the derivatives exported as one number actually did")
    val report = com.abajar.avleditor.avl.runcase.AlphaSweepPoint
      .constantsReport(calc.getAlphaSweep).asScala.toList
    report.foreach(l => println("  " + l))
    check("the report says which coefficients became curves",
      report.exists(l => l.contains("CL") && l.contains("CD") && l.contains("Cm")))
    check("and gives a range and a spread for each of the others",
      Seq("CLa", "Cma", "Cnb", "Clb").forall(name =>
        report.exists(l => l.contains(name) && l.contains("spread"))))
    check("with a warning on any that moved more than itself",
      report.filter(_.contains("misrepresents")).forall(l => l.contains("Cnb") || l.contains("Clb")))
    check("a single point says nothing rather than dividing by nothing",
      com.abajar.avleditor.avl.runcase.AlphaSweepPoint
        .constantsReport(calc.getAlphaSweep.subList(0, 1)).isEmpty)

    println(if (ok) "ALPHA_SWEEP_OK" else "ALPHA_SWEEP_FAIL")
    if (!ok) sys.exit(1)
  }
}
