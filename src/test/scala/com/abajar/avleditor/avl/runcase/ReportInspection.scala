/*
 * The results window's contents, in text, so they can be read and argued with.
 *
 * Not a check — it asserts nothing. It exists because the report is fifteen rows of prose across three
 * Flight Phases and the only way to review it was to open a window and scroll.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.runcase.ReportInspection <file.avle>"
 * With no argument it uses the check's own aircraft.
 */
package com.abajar.avleditor.avl.runcase

import com.abajar.avleditor.{AvlManager, TestAircraft, XfoilManager}
import com.abajar.avleditor.avl.connectivity.AvlRunner
import com.abajar.avleditor.crrcsim.{CRRCSim, CRRCSimRepository}
import java.io.File
import java.util.Properties
import scala.collection.JavaConverters._

object ReportInspection {

  private def wrap(text: String, width: Int, indent: String): String =
    text.split(" ").foldLeft(List(indent)) { (lines, word) =>
      if ((lines.head + " " + word).length > width) (indent + word) :: lines
      else (if (lines.head == indent) lines.head + word else lines.head + " " + word) :: lines.tail
    }.reverse.mkString("\n")

  def main(args: Array[String]): Unit = {
    val props = new Properties()
    if (!AvlManager.ensureAvlAvailable(props)) { println("AVL is not available"); return }
    XfoilManager.ensureXfoilAvailable(props)

    val (crrcsim, what) =
      if (args.nonEmpty) {
        val file = new File(args(0))
        val model = new CRRCSimRepository().restoreFromFile(file)
        model.getAvl.getGeometry.getSurfaces.asScala.foreach(_.initSectionParents())
        model.getAvl.getGeometry.getBodies.asScala.foreach(_.initProfilePointParents())
        (model, file.getName)
      } else (TestAircraft.conventional(), "the check's own aircraft")

    val avl = crrcsim.getAvl
    crrcsim.calculate()
    println("=" * 110)
    println(s"$what — ${avl.describeAnalysisPoint}")
    println("=" * 110)

    val calculation = new AvlRunner(props.getProperty("avl.path"), avl, crrcsim.getOriginPath)
      .getCalculation()

    XfoilManager.usable(props) match {
      case Left(why) => calculation.getConfiguration.setStallProblem(why)
      case Right(path) =>
        com.abajar.avleditor.xfoil.StallAnalysis.analyse(avl, calculation, path, crrcsim.getOriginPath) match {
          case Left(why) => calculation.getConfiguration.setStallProblem(why)
          case Right(result) =>
            calculation.getConfiguration.setStall(result.stallSpeedMetresPerSecond.toFloat,
              result.clMax.toFloat, result.critical.station.describe)
            println(f"STALL: CLmax ${result.clMax}%.3f at ${result.alphaDeg}%.2f deg, " +
              f"Vs ${result.stallSpeedMetresPerSecond}%.2f m/s; first to give up: " +
              result.critical.station.describe)
            result.notes.foreach(note => println(wrap(note, 106, "       ")))
        }
    }

    println()
    println("--- what the window puts above the table " + "-" * 68)
    MilF8785cEvaluator.runawaySummary(calculation).foreach(s => println(wrap(s, 106, "")))
    val runaways = MilF8785cEvaluator.divergences(calculation)
    val neutrals = MilF8785cEvaluator.neutralModes(calculation)
    if (runaways.nonEmpty || neutrals.nonEmpty) {
      println("What the motions do — read from AVL's eigenvalues, not from MIL-F-8785C:")
      runaways.foreach(d => println(wrap("• " + d.says, 106, "    ")))
      neutrals.foreach(l => println(wrap("• " + l, 106, "    ")))
    }
    val size = MilF8785cEvaluator.sizeOf(calculation)
    println(wrap("Judged in all three Flight Phase Categories at once: " +
      FlightPhaseCategory.All.map(c => f"${c.label}%s, ${c.describes}%s").mkString("; ") + "." +
      (if (size.scales) f" This aircraft spans ${size.spanMetres}%.2f m, below the " +
        f"${size.ReferenceSpanMetres}%.2f m of the smallest airplane the standard was written for, so its " +
        "frequencies and times are scaled to that size; both figures are given on each row."
       else if (size.known) f" This aircraft spans ${size.spanMetres}%.2f m, within the range the standard " +
         "covers, so every figure is exactly as it states it."
       else ""), 106, ""))
    if (MilF8785cEvaluator.oscillatoryPositiveModes(calculation).isEmpty)
      MilF8785cEvaluator.whyNoModes(calculation).foreach(l => println(wrap(l, 106, "")))

    // The same strings the window's cells carry. Composed here rather than shared with it, which is a
    // duplication worth removing if this file survives the review it was written for.
    def levelled(row: ModalNormRow): String = row.outcome match {
      case RowOutcome.Reached(n)          => f"LEVEL $n%d — " + row.verdict
      case RowOutcome.WorseThanLevelThree => "WORSE THAN LEVEL 3 — " + row.verdict
      case RowOutcome.OnTheBoundary       => "ON THE BOUNDARY — " + row.verdict
      case _                              => row.verdict
    }

    println()
    MilF8785cEvaluator.evaluateEveryCategory(calculation).zipWithIndex.foreach { case (motion, i) =>
      println("-" * 110)
      println(f"${i + 1}%2d. ${motion.modeName}%s")
      println(wrap(motion.whatItIs, 106, "    what it is:  "))
      println("    how often:   " + motion.period.map(p => f"every $p%.2f s").getOrElse("—"))
      println("    damping:     " + ((motion.zeta, motion.swingsToHalf) match {
        case (Some(z), Some(s)) => f"$z%.2f  (half in $s%.1f swings)"
        case (Some(z), None) if z >= 1.0 => f"$z%.2f  (no swing at all)"
        case (Some(z), None) => f"$z%.2f"
        case _ => "—"
      }))
      // Said once when the three agree, as the window says it once across the three columns.
      val identical = motion.byCategory.map(r => (r._2.verdict, r._2.outcome, r._2.pass)).distinct.length == 1
      if (identical) println(wrap(levelled(motion.byCategory.head._2), 106, "    [A B C] "))
      else motion.byCategory.foreach { case (category, row) =>
        println(wrap(levelled(row), 106, s"    [${category.label}]   "))
      }
      val distinct = motion.byCategory.map(_._2.requirement).distinct
      val rules = motion.byCategory.map { case (c, r) =>
        f"${c.label}%s: " + r.requirement + r.applied.map(a => " (" + a + ")").getOrElse("")
      }
      println(wrap(motion.wn.map(w => f"wn ${w}%.3f rad/s · ").getOrElse("") +
        "MIL-F-8785C Level 1 wants " +
        (if (distinct.length == 1) rules.head.dropWhile(_ != ' ').trim else rules.mkString(" · ")),
        106, "    rule:  "))
    }
    println("-" * 110)
  }
}
