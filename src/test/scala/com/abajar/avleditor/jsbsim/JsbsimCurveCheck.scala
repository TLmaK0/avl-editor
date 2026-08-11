/*
 * Evidence, not assertions: the exported curves have to reach the simulator and be the numbers it flies on.
 * This exports the model for real — AVL sweep and all — then runs JSBSim from the command line on a pull-up
 * and compares the lift and drag JSBSim computes at each instant against the tables in the file it loaded.
 * The route that proved the landing gear stiffness and the trim before.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.JsbsimCurveCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.{AvlManager, JsbsimManager}
import com.abajar.avleditor.avl.connectivity.AvlRunner
import java.io.{File, PrintWriter}
import java.util.Properties
import scala.collection.JavaConverters._

object JsbsimCurveCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /**
   * JSBSim is no longer looked for on the machine here: having it is [[JsbsimManager]]'s job, and
   * it installs one under `~/.avleditor` when the machine has none. A check that drives a tool has
   * to bring the tool with it, or what it measures depends on what somebody once arranged by hand
   * on that host — see the note in the manager, which is the bug this replaced.
   */

  private def write(file: File, content: String): Unit = {
    Option(file.getParentFile).foreach(_.mkdirs())
    val pw = new PrintWriter(file)
    try pw.write(content) finally pw.close()
  }

  /** The rows of one table function out of the exported aircraft, as (alpha rad, value). */
  private def tableRows(xml: String, function: String): Seq[(Double, Double)] = {
    val block = xml.split("""<function name="""").find(_.startsWith(function + "\"")).getOrElse("")
    val data = """(?s)<tableData>(.*?)</tableData>""".r.findFirstMatchIn(block)
      .map(_.group(1)).getOrElse("")
    data.split("\n").map(_.trim).filter(_.nonEmpty).map { line =>
      val parts = line.split("\\s+"); (parts(0).toDouble, parts(1).toDouble)
    }.toList
  }

  /** What JSBSim's own table lookup does: linear between rows, and hold the end value beyond them. */
  private def lookup(rows: Seq[(Double, Double)], x: Double): Double =
    if (x <= rows.head._1) rows.head._2
    else if (x >= rows.last._1) rows.last._2
    else rows.sliding(2).collectFirst {
      case Seq((x0, y0), (x1, y1)) if x >= x0 && x <= x1 => y0 + (x - x0) * (y1 - y0) / (x1 - x0)
    }.getOrElse(rows.last._2)

  def main(args: Array[String]): Unit = {
    val jsbsim = JsbsimManager.ensureJsbsimAvailable()
    val props = new Properties()
    val avlAvailable = AvlManager.ensureAvlAvailable(props)
    if (jsbsim.isEmpty || !avlAvailable) {
      println(s"  JSBSim found: ${jsbsim.getOrElse("no")}; AVL available: $avlAvailable")
      println("  this check needs both, since its whole point is running the exported model")
      println("JSBSIM_CURVE_SKIPPED")
      return
    }

    val root = new File(System.getProperty("java.io.tmpdir"), "avleditor-jsbsim-curve")
    def deleteTree(f: File): Unit = {
      if (f.isDirectory) Option(f.listFiles).foreach(_.foreach(deleteTree))
      f.delete()
    }
    deleteTree(root)

    println("exporting the model, sweep and all")
    // The check's own aircraft, stable and ordinary, rather than a sample the user edits.
    val model = com.abajar.avleditor.TestAircraft.conventional()
    val calc = new AvlRunner(props.getProperty("avl.path"), model.getAvl, model.getOriginPath).getCalculation()
    check("the sweep measured a curve", calc.getAlphaSweep.size >= 3)
    JsbsimExporter.export(root, "testcraft", model, calc)

    val aircraftXml = scala.io.Source.fromFile(new File(root, "aircraft/testcraft/testcraft.xml")).mkString
    val liftRows = tableRows(aircraftXml, "aero/force/lift")
    val dragRows = tableRows(aircraftXml, "aero/force/drag")
    check("the exported model states a lift curve", liftRows.length == calc.getAlphaSweep.size)
    check("and a drag curve", dragRows.length == liftRows.length)

    println("flying it in JSBSim")
    write(new File(root, "aircraft/testcraft/reset00.xml"),
      """<?xml version="1.0"?>
        |<initialize name="in the air, level, at the analysed speed">
        |  <ubody unit="M/SEC"> 16.0 </ubody>
        |  <vbody unit="M/SEC"> 0.0 </vbody>
        |  <wbody unit="M/SEC"> 0.0 </wbody>
        |  <altitude unit="M"> 300.0 </altitude>
        |  <phi unit="DEG"> 0.0 </phi>
        |  <theta unit="DEG"> 0.0 </theta>
        |  <psi unit="DEG"> 0.0 </psi>
        |</initialize>
        |""".stripMargin)
    // The function names are the property names, so this logs what the aerodynamics actually produced —
    // not a recomputation of it.
    write(new File(root, "log.xml"),
      """<?xml version="1.0"?>
        |<output name="curve.csv" type="CSV" rate="20">
        |  <property> aero/alpha-deg </property>
        |  <property> aero/qbar-area </property>
        |  <property> aero/force/lift </property>
        |  <property> aero/force/drag </property>
        |</output>
        |""".stripMargin)
    write(new File(root, "pullup.xml"),
      """<?xml version="1.0"?>
        |<runscript name="pullup">
        |  <use aircraft="testcraft" initialize="reset00"/>
        |  <run start="0.0" end="6.0" dt="0.0041666">
        |    <event name="hold the stick back">
        |      <condition> simulation/sim-time-sec >= 0.5 </condition>
        |      <set name="fcs/elevator-cmd-norm" value="-1.0"/>
        |    </event>
        |  </run>
        |</runscript>
        |""".stripMargin)

    val pb = new ProcessBuilder(jsbsim.get, "--root=.", "--script=pullup.xml",
      "--logdirectivefile=log.xml", "--nohighlight", "--end=6")
    pb.directory(root)
    pb.redirectErrorStream(true)
    val process = pb.start()
    val output = scala.io.Source.fromInputStream(process.getInputStream).mkString
    process.waitFor()
    val csv = new File(root, "curve.csv")
    check("JSBSim loaded the model and ran", csv.exists && csv.length > 0)
    if (!csv.exists) {
      println(output.linesIterator.toList.takeRight(20).map("    " + _).mkString("\n"))
      println("JSBSIM_CURVE_FAIL")
      sys.exit(1)
    }

    val lines = scala.io.Source.fromFile(csv).getLines().toList
    val header = lines.head.split(",").map(_.trim)
    def column(fragment: String): Int = header.indexWhere(_.endsWith(fragment))
    val (tCol, aCol, qCol, lCol, dCol) =
      (0, column("aero/alpha-deg"), column("aero/qbar-area"),
        column("aero/force/lift"), column("aero/force/drag"))

    var worstLift = 0.0
    var worstDrag = 0.0
    var samples = 0
    var beyondTable = 0
    var minAlpha = Double.MaxValue
    var maxAlpha = Double.MinValue
    println(f"${"t"}%6s ${"alpha"}%8s ${"CL flown"}%10s ${"CL table"}%10s")
    lines.tail.zipWithIndex.foreach { case (line, i) =>
      val cells = line.split(",")
      val alphaDeg = cells(aCol).toDouble
      val qbarArea = cells(qCol).toDouble
      if (qbarArea > 0) {
        val alphaRad = math.toRadians(alphaDeg)
        val clFlown = cells(lCol).toDouble / qbarArea
        val cdFlown = cells(dCol).toDouble / qbarArea
        val clTable = lookup(liftRows, alphaRad)
        worstLift = math.max(worstLift, math.abs(clFlown - clTable))
        worstDrag = math.max(worstDrag, math.abs(cdFlown - lookup(dragRows, alphaRad)))
        samples += 1
        minAlpha = math.min(minAlpha, alphaDeg)
        maxAlpha = math.max(maxAlpha, alphaDeg)
        if (alphaRad > liftRows.last._1) beyondTable += 1
        if (i % 20 == 0)
          println(f"${cells(tCol).toDouble}%6.2f $alphaDeg%8.2f $clFlown%10.5f $clTable%10.5f")
      }
    }
    println(f"  attitudes flown: $minAlpha%.1f to $maxAlpha%.1f degrees, $samples%d samples")
    println(f"  worst disagreement: lift $worstLift%.2e, drag $worstDrag%.2e")

    check("JSBSim flew on the exported lift curve, not on something else", samples > 50 && worstLift < 1e-6)
    check("and on the exported drag curve", worstDrag < 1e-6)

    // The straight line it replaces had no end: at 70 degrees it would have claimed CL 2.6 and held it
    // there for ever. A table holds its last row instead. That is not a stall — a stall is viscous and AVL
    // cannot see one — it is only the absence of a fiction, and it is worth knowing which of the two it is.
    check("the attitudes flown went past the end of the table", beyondTable > 0)
    val liftMax = liftRows.map(_._2).max
    val flownMax = lines.tail.map { line =>
      val cells = line.split(",")
      val q = cells(qCol).toDouble
      if (q > 0) cells(lCol).toDouble / q else 0.0
    }.max
    check("and the lift never exceeded the highest measured value",
      flownMax <= liftMax + 1e-6)

    println(if (ok) "JSBSIM_CURVE_OK" else "JSBSIM_CURVE_FAIL")
    if (!ok) sys.exit(1)
  }
}
