/*
 * Both AVL plots have to reach the results window. The Trefftz one did not: its PostScript page was
 * written but never finished, because AVL was left to die on end-of-input instead of being told to quit,
 * and Ghostscript makes nothing of a page with no 'showpage'.
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.connectivity.AvlPlotCheck"
 */
package com.abajar.avleditor.avl.connectivity

import com.abajar.avleditor.AvlManager
import com.abajar.avleditor.crrcsim.CRRCSimRepository
import java.io.File
import java.util.Properties
import scala.collection.JavaConverters._

object AvlPlotCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  def main(args: Array[String]): Unit = {
    println("what is typed at AVL")
    val commands = AvlRunner.plotCommands(0, 30f, 0.3f, 45f, 20f).asScala.toList
    println("  " + commands.map(c => if (c.trim.isEmpty) "<blank>" else c.trim).mkString(" | "))
    check("a hardcopy is asked for twice: geometry and Trefftz",
      commands.count(_ == "h") == 2 && commands.contains("t") && commands.contains("g"))
    // The ending is the fix: AVL must be told to quit, or the last page is never closed.
    check("it ends by leaving both menus and quitting",
      commands.takeRight(3) == List("", "", "quit"))
    check("and never with 'q', which OPER does not know",
      !commands.contains("q"))

    println("running AVL on a real model")
    val props = new Properties()
    if (!AvlManager.ensureAvlAvailable(props)) {
      println("  AVL is not available here; the run part of this check needs it")
      println(if (ok) "AVL_PLOT_OK" else "AVL_PLOT_FAIL")
      if (!ok) sys.exit(1)
      return
    }

    val model = new CRRCSimRepository().restoreFromFile(new File("samples/eurofighter/eurofighter.avle"))
    val runner = new AvlRunner(props.getProperty("avl.path"), model.getAvl, model.getOriginPath, 45f, 20f)
    runner.getCalculation()

    val geometry = Option(runner.getGeometryPlotPath())
    val trefftz = Option(runner.getTrefftzPlotPath())
    println(s"  geometry: ${geometry.getOrElse("none")}")
    println(s"  trefftz:  ${trefftz.getOrElse("none")}")
    check("the geometry plot is produced", geometry.exists(p => p.toFile.exists && p.toFile.length > 1000))
    check("and so is the Trefftz plot", trefftz.exists(p => p.toFile.exists && p.toFile.length > 1000))

    geometry.foreach { path =>
      val ps = new File(path.getParent.toFile, "plot.ps")
      val text = if (ps.exists) scala.io.Source.fromFile(ps, "ISO-8859-1").mkString else ""
      val pages = "(?m)^%%Page:".r.findAllIn(text).size
      val showpages = "showpage".r.findAllIn(text).size
      println(f"  plot.ps: $pages%d pages, $showpages%d showpage, trailer ${text.contains("%%Trailer")}%s")
      check("the PostScript has both pages", pages == 2)
      // One 'showpage' per page, which is what was missing: a page without it converts to nothing.
      check("each page is finished", showpages == 2)
      check("and the file is closed properly", text.contains("%%Trailer"))
    }

    println(if (ok) "AVL_PLOT_OK" else "AVL_PLOT_FAIL")
    if (!ok) sys.exit(1)
  }
}
