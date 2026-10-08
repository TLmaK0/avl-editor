/*
 * Evidence for issue #17: the exported model modelled past the stall instead of holding its last row, and
 * JSBSim flies on exactly what the table says there too — not an assertion about the join's arithmetic in
 * isolation, the same route `JsbsimCurveCheck` already proved for the pre-stall curve.
 *
 * Needs AVL, XFOIL and JSBSim, all three: the whole point is the chain from AVL's spanwise loading through
 * XFOIL's section data to the file JSBSim actually loads and flies on.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.PostStallCurveCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.{AvlManager, JsbsimManager, TestAircraft, XfoilManager}
import com.abajar.avleditor.avl.connectivity.AvlRunner
import com.abajar.avleditor.xfoil.{SectionStall, StallAnalysis}
import java.io.{File, PrintWriter}
import java.util.Properties
import scala.collection.JavaConverters._

object PostStallCurveCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

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

  private def lookup(rows: Seq[(Double, Double)], x: Double): Double =
    if (x <= rows.head._1) rows.head._2
    else if (x >= rows.last._1) rows.last._2
    else rows.sliding(2).collectFirst {
      case Seq((x0, y0), (x1, y1)) if x >= x0 && x <= x1 => y0 + (x - x0) * (y1 - y0) / (x1 - x0)
    }.getOrElse(rows.last._2)

  def main(args: Array[String]): Unit = {
    val props = new Properties()
    val avlThere = AvlManager.ensureAvlAvailable(props)
    XfoilManager.ensureXfoilAvailable(props)
    val xfoil = XfoilManager.usable(props)
    val jsbsim = JsbsimManager.ensureJsbsimAvailable()

    if (!avlThere || xfoil.isLeft || jsbsim.isEmpty) {
      println(s"  AVL available: $avlThere; XFOIL usable: $xfoil; JSBSim found: ${jsbsim.getOrElse("no")}")
      println("  this check needs all three")
      println("POST_STALL_CURVE_SKIPPED")
      return
    }
    val xfoilPath = xfoil.right.get

    println("the check's own aircraft, all the way through: AVL, then XFOIL for the critical section")
    val model = TestAircraft.conventional()
    val calc = new AvlRunner(props.getProperty("avl.path"), model.getAvl, model.getOriginPath).getCalculation()
    check("the sweep measured a curve", calc.getAlphaSweep.size >= 3)
    val sweepTopDeg = calc.getAlphaSweep.asScala.map(_.getAlphaDeg.toDouble).max

    val extension = StallAnalysis.analyseWithCurve(model.getAvl, calc, xfoilPath, model.getOriginPath)
    extension match {
      case Left(why) => println("  refused: " + why)
      case Right(ext) =>
        println(f"  onset ${ext.result.alphaDeg}%.2f deg, section limit ${ext.result.critical.sectionLimit}%.3f, " +
          f"AVL's own CL there ${ext.result.clMax}%.3f")
    }
    check("it comes back with a stall and a curve to model past it", extension.isRight)
    if (extension.isLeft) {
      println(if (ok) "POST_STALL_CURVE_OK" else "POST_STALL_CURVE_FAIL")
      if (!ok) sys.exit(1)
      return
    }
    val ext = extension.right.get
    check("the onset is within the attitudes AVL swept, not past them",
      ext.result.alphaDeg <= sweepTopDeg + 1e-6)
    check("the critical section does not stall downward on this aircraft (the only case modelled so far)",
      !ext.result.critical.downward)

    val root = new File(System.getProperty("java.io.tmpdir"), "avleditor-poststall-curve")
    def deleteTree(f: File): Unit = {
      if (f.isDirectory) Option(f.listFiles).foreach(_.foreach(deleteTree))
      f.delete()
    }
    deleteTree(root)

    println("exporting with the stall extension, and without it, to compare the two tables")
    JsbsimExporter.export(root, "withstall", model, calc, Some(ext))
    JsbsimExporter.export(root, "baseline", model, calc, None)
    val withStallXml = scala.io.Source.fromFile(new File(root, "aircraft/withstall/withstall.xml")).mkString
    val baselineXml = scala.io.Source.fromFile(new File(root, "aircraft/baseline/baseline.xml")).mkString

    val liftRows = tableRows(withStallXml, "aero/force/lift")
    val dragRows = tableRows(withStallXml, "aero/force/drag")
    val pitchRows = tableRows(withStallXml, "aero/moment/pitch")
    val baselineLiftRows = tableRows(baselineXml, "aero/force/lift")

    check("the extended table has more rows than the plain 13-point sweep",
      liftRows.length > calc.getAlphaSweep.size)
    check("and more than the baseline export without the extension",
      liftRows.length > baselineLiftRows.length)
    check("it reaches out to where the critical section's curve was asked to go",
      math.toDegrees(liftRows.last._1) >= StallAnalysis.PostStallEndDeg - SectionStall.AlphaStepDeg - 1e-6)

    println(f"  lift table: ${liftRows.length}%d rows, ${math.toDegrees(liftRows.head._1)}%.1f to " +
      f"${math.toDegrees(liftRows.last._1)}%.1f deg")

    // The join's whole point: no step at the boundary. Sampled a tenth of a degree either side of the
    // onset, which the table's own two neighbouring rows there would otherwise straddle with a jump.
    val onsetRad = math.toRadians(ext.result.alphaDeg)
    val justBelow = lookup(liftRows, onsetRad - math.toRadians(0.1))
    val justAbove = lookup(liftRows, onsetRad + math.toRadians(0.1))
    println(f"  lift a tenth of a degree either side of the onset: $justBelow%.4f / $justAbove%.4f")
    check("lift does not step at the stall onset", math.abs(justAbove - justBelow) < 0.01)

    // And the point this issue exists for: the aircraft does not gain lift at the attitude it is supposed
    // to be giving it up. Scaling by the fraction of the section limit is exactly what rules this out.
    val liftAtOnset = lookup(liftRows, onsetRad)
    check("lift at the onset does not jump above AVL's own measured value there",
      liftAtOnset <= ext.result.clMax + 0.01)

    // Past the stall the curve is not flat: this is the actual modelling, not the hold-last-row the
    // baseline export (no extension) still does.
    val wellPast = onsetRad + math.toRadians(10.0)
    check("past the stall the lift is not flat the way the baseline's held last row is",
      math.abs(lookup(liftRows, wellPast) - lookup(baselineLiftRows, wellPast)) > 0.01)
    check("the baseline (no extension) is unchanged: still holds its last row",
      lookup(baselineLiftRows, wellPast) == baselineLiftRows.last._2)

    println("flying it in JSBSim, well past the old +20 deg table edge")
    write(new File(root, "aircraft/withstall/reset00.xml"),
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
    write(new File(root, "log.xml"),
      """<?xml version="1.0"?>
        |<output name="curve.csv" type="CSV" rate="20">
        |  <property> aero/alpha-deg </property>
        |  <property> aero/qbar-area </property>
        |  <property> aero/force/lift </property>
        |  <property> aero/force/drag </property>
        |  <property> aero/moment/pitch </property>
        |</output>
        |""".stripMargin)
    write(new File(root, "pullup.xml"),
      """<?xml version="1.0"?>
        |<runscript name="pullup">
        |  <use aircraft="withstall" initialize="reset00"/>
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
      println("POST_STALL_CURVE_FAIL")
      sys.exit(1)
    }

    val lines = scala.io.Source.fromFile(csv).getLines().toList
    val header = lines.head.split(",").map(_.trim)
    def column(fragment: String): Int = header.indexWhere(_.endsWith(fragment))
    val (aCol, qCol, lCol, dCol, mCol) =
      (column("aero/alpha-deg"), column("aero/qbar-area"),
        column("aero/force/lift"), column("aero/force/drag"), column("aero/moment/pitch"))

    // "aero/moment/pitch" is qbar*area*chord*Cm (tableTerm("moment/pitch", p(qA) + p(chord), ...)), not
    // qbar*area*Cm like lift and drag — one more factor to divide out, read from the file itself rather
    // than assumed, so a change to how the chord is written here cannot go unnoticed.
    val chordM = """<chord unit="M">([-0-9.eE]+)</chord>""".r.findFirstMatchIn(withStallXml)
      .map(_.group(1).toDouble).getOrElse(throw new RuntimeException("no <chord> in the exported metrics"))
    val chordFt = chordM / 0.3048

    var worstLift = 0.0
    var worstDrag = 0.0
    var worstPitch = 0.0
    var samples = 0
    var pastOldEdge = 0
    lines.tail.foreach { line =>
      val cells = line.split(",")
      val alphaDeg = cells(aCol).toDouble
      val qbarArea = cells(qCol).toDouble
      if (qbarArea > 0) {
        val alphaRad = math.toRadians(alphaDeg)
        worstLift = math.max(worstLift, math.abs(cells(lCol).toDouble / qbarArea - lookup(liftRows, alphaRad)))
        worstDrag = math.max(worstDrag, math.abs(cells(dCol).toDouble / qbarArea - lookup(dragRows, alphaRad)))
        worstPitch = math.max(worstPitch,
          math.abs(cells(mCol).toDouble / (qbarArea * chordFt) - lookup(pitchRows, alphaRad)))
        samples += 1
        if (alphaDeg > sweepTopDeg) pastOldEdge += 1
      }
    }
    println(f"  $samples%d samples, $pastOldEdge%d of them past the old ${sweepTopDeg}%.0f deg table edge")
    println(f"  worst disagreement: lift $worstLift%.2e, drag $worstDrag%.2e, pitch $worstPitch%.2e")

    check("the pull-up went past the old table edge", pastOldEdge > 0)
    check("JSBSim flew on the exported lift curve past the stall, not on something else", worstLift < 1e-6)
    check("and on the exported drag curve", worstDrag < 1e-6)
    check("and on the exported pitching-moment curve", worstPitch < 1e-6)

    println(if (ok) "POST_STALL_CURVE_OK" else "POST_STALL_CURVE_FAIL")
    if (!ok) sys.exit(1)
  }
}
