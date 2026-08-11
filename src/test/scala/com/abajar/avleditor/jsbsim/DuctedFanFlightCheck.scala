/*
 * Evidence, not assertions. The whole ducted-fan derivation is expressed in JSBSim's own definitions of Ct and
 * Cp, so it is worth nothing until JSBSim agrees: if it defined them per radian, or against a different power
 * of the diameter, every assertion elsewhere would still pass and the aircraft would have the wrong thrust.
 * So this exports a model with a fan, runs JSBSim from the command line at full throttle, and checks that the
 * thrust it computes is Ct x rho x n^2 x D^4 with the Ct in the file it loaded.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.DuctedFanFlightCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.AvlManager
import com.abajar.avleditor.avl.connectivity.AvlRunner
import java.io.{File, PrintWriter}
import java.util.Properties
import scala.collection.JavaConverters._

object DuctedFanFlightCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /**
   * The four fixed locations a Debian or Ubuntu package uses, and then **whatever the PATH offers**,
   * under both spellings — see the same list in [[JsbsimCurveCheck]]. `pip install jsbsim` lands in
   * `/usr/local/bin/jsbsim`, which the fixed list missed, so this check excused itself and exited 0
   * on any machine that had JSBSim installed that way.
   */
  private val JsbsimCandidates: Seq[String] =
    Seq("/usr/games/JSBSim", "/usr/bin/JSBSim", "/usr/local/bin/JSBSim", "/usr/bin/jsbsim") ++
      (for {
        dir <- Option(System.getenv("PATH")).getOrElse("").split(File.pathSeparator).toSeq
        name <- Seq("JSBSim", "jsbsim")
      } yield new File(dir, name).getPath)

  private val SlugFt3ToKgM3 = 515.378818
  private val LbsToNewtons = 4.4482216

  private def write(file: File, content: String): Unit = {
    Option(file.getParentFile).foreach(_.mkdirs())
    val pw = new PrintWriter(file)
    try pw.write(content) finally pw.close()
  }

  private def tableRows(xml: String, name: String): Seq[(Double, Double)] = {
    val block = xml.split(s"""<table name="$name"""").drop(1).headOption.getOrElse("")
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
    val jsbsim = JsbsimCandidates.find(p => new File(p).canExecute)
    val props = new Properties()
    val avl = AvlManager.ensureAvlAvailable(props)
    if (jsbsim.isEmpty || !avl) {
      println(s"  JSBSim: ${jsbsim.getOrElse("not found")}; AVL: $avl — this check needs both")
      println("DUCTED_FAN_FLIGHT_SKIPPED")
      return
    }

    val root = new File(System.getProperty("java.io.tmpdir"), "avleditor-ducted-fan")
    def deleteTree(f: File): Unit = {
      if (f.isDirectory) Option(f.listFiles).foreach(_.foreach(deleteTree))
      f.delete()
    }
    deleteTree(root)

    println("exporting an aircraft with a 70 mm fan where its propeller was")
    // The check's own aircraft, with a fan where its propeller was. Not a sample: those are the user's, and
    // one of them grew a ducted fan of its own between two runs of this check.
    val model = com.abajar.avleditor.TestAircraft.ductedFan()
    val shaft = model.getConfig.getPower.getBateries.get(0).getShafts.get(0)
    val fan = shaft.getDuctedFans.get(0)
    check("the model is fit to export", SimulationRequirements.validate(model).isEmpty)
    val calc = new AvlRunner(props.getProperty("avl.path"), model.getAvl, model.getOriginPath).getCalculation()
    JsbsimExporter.export(root, "fanjet", model, calc)

    val propFile = scala.io.Source.fromFile(new File(root, "engine/fanjet_prop.xml")).mkString
    val ct = tableRows(propFile, "C_THRUST")
    val diameter = """<diameter unit="M">([-\d.eE+]+)</diameter>""".r
      .findFirstMatchIn(propFile).map(_.group(1).toDouble).getOrElse(0.0)
    check("the exported thruster is the fan's bore", math.abs(diameter - 0.068) < 1e-6)
    check("with a curve of its own", ct.length == DuctedFanCurves.Rows)

    // Under power but not at full throttle: 1.6 kg of thrust on a 1.1 kg aeroplane is 1.45 g of acceleration,
    // and a light model at full power with the stick centred departs rather than flies. What this check needs
    // is the fan turning across a range of advance ratios, which a level run gives.
    println("flying it under power")
    write(new File(root, "aircraft/fanjet/reset00.xml"),
      """<?xml version="1.0"?>
        |<initialize name="level, at speed">
        |  <ubody unit="M/SEC"> 16.0 </ubody>
        |  <vbody unit="M/SEC"> 0.0 </vbody>
        |  <wbody unit="M/SEC"> 0.0 </wbody>
        |  <altitude unit="M"> 100.0 </altitude>
        |  <phi unit="DEG"> 0.0 </phi><theta unit="DEG"> 0.0 </theta><psi unit="DEG"> 0.0 </psi>
        |</initialize>
        |""".stripMargin)
    write(new File(root, "log.xml"),
      """<?xml version="1.0"?>
        |<output name="fan.csv" type="CSV" rate="20">
        |  <property> propulsion/engine/advance-ratio </property>
        |  <property> propulsion/engine/thrust-coefficient </property>
        |  <property> propulsion/engine/propeller-rpm </property>
        |  <property> propulsion/engine/thrust-lbs </property>
        |  <property> atmosphere/rho-slugs_ft3 </property>
        |</output>
        |""".stripMargin)
    write(new File(root, "run.xml"),
      """<?xml version="1.0"?>
        |<runscript name="a level run under power">
        |  <use aircraft="fanjet" initialize="reset00"/>
        |  <run start="0.0" end="8.0" dt="0.0041666">
        |    <event name="start and open the throttle">
        |      <condition> simulation/sim-time-sec >= 0.1 </condition>
        |      <set name="propulsion/set-running" value="-1"/>
        |      <set name="fcs/throttle-cmd-norm" value="1.0"/>
        |      <set name="fcs/throttle-pos-norm" value="1.0"/>
        |    </event>
        |  </run>
        |</runscript>
        |""".stripMargin)

    val pb = new ProcessBuilder(jsbsim.get, "--root=.", "--script=run.xml",
      "--logdirectivefile=log.xml", "--nohighlight", "--end=8")
    pb.directory(root)
    pb.redirectErrorStream(true)
    val process = pb.start()
    val output = scala.io.Source.fromInputStream(process.getInputStream).mkString
    process.waitFor()
    val csv = new File(root, "fan.csv")
    if (!csv.exists) {
      println(output.linesIterator.toList.takeRight(15).map("    " + _).mkString("\n"))
      check("JSBSim ran", false)
      println("DUCTED_FAN_FLIGHT_FAIL")
      sys.exit(1)
    }

    val lines = scala.io.Source.fromFile(csv).getLines().toList
    val header = lines.head.split(",").map(_.trim)
    def col(fragment: String): Int = header.indexWhere(_.endsWith(fragment))
    val (jc, ctc, rpmc, tc, rhoc) = (col("advance-ratio"), col("thrust-coefficient"),
      col("propeller-rpm"), col("thrust-lbs"), col("rho-slugs_ft3"))

    var turning = 0
    var worstCt = 0.0
    var worstThrust = 0.0
    var maxThrustN = 0.0

    // Once JSBSim's integration gives up it writes `nan` into every column, and `toDouble` on that
    // raises a NumberFormatException — a stack trace where a verdict belongs, naming nothing and
    // saying nothing about the aeroplane. A `nan` in a flight trace IS the finding: the aircraft
    // did not fly. So it is read as one, and the row it started on is reported, because when it
    // happens is what says whether the model diverged on the first step or ten seconds in.
    def finite(cell: String): Option[Double] =
      scala.util.Try(cell.trim.toDouble).toOption.filter(d => !d.isNaN && !d.isInfinite)

    var divergedAt: Option[String] = None

    // The identity below is measured once the rotor is up to speed, not across the spin-up. Both
    // sides of it are logged 20 times a second while JSBSim integrates at 240, so during the
    // acceleration the thrust and the rpm in one row are not from quite the same instant and the
    // comparison measures the sampling rather than the definition: one row, at t = 0.15 s, comes
    // out 1.83 % apart while every row after the rotor settles agrees to 0.012 %. There was no
    // spin-up to sample while the export wrote a constant-power engine, because that reached full
    // speed inside one sample and then diverged (#24) — a real motor takes about a second.
    val SettledAfterSec = 1.5

    println(f"${"rpm"}%8s ${"J"}%8s ${"Ct flown"}%9s ${"Ct table"}%9s ${"T flown"}%9s ${"Ct rho n2 D4"}%13s")
    lines.tail.zipWithIndex.foreach { case (line, i) =>
      val cells = line.split(",")
      val rpmOrNot = finite(cells(rpmc))
      if (rpmOrNot.isEmpty && divergedAt.isEmpty)
        divergedAt = Some(s"t=${cells.headOption.getOrElse("?")}s, row ${i + 1}: ${line.take(120)}")
      val rpm = rpmOrNot.getOrElse(0.0)
      // A row can carry a finite rpm and `nan` beside it, so every column is read the same way and
      // a row that is not wholly finite is recorded and skipped rather than averaged into the
      // worst-case gaps, where one NaN would swallow every real measurement in the run.
      val row = Seq(jc, ctc, tc, rhoc).map(idx => finite(cells(idx)))
      if (rpm > 0 && row.exists(_.isEmpty) && divergedAt.isEmpty)
        divergedAt = Some(s"t=${cells.headOption.getOrElse("?")}s, row ${i + 1}: ${line.take(120)}")

      if (rpm > 0 && row.forall(_.isDefined)) {
        val Seq(j, ctFlown, thrustLbs, rhoSlugs) = row.map(_.get)
        val thrustN = thrustLbs * LbsToNewtons
        val rho = rhoSlugs * SlugFt3ToKgM3
        val timeSec = finite(cells(0)).getOrElse(0.0)
        val n = rpm / 60.0
        // The definition under test: JSBSim's own thrust against the coefficient it looked up.
        val fromDefinition = ctFlown * rho * n * n * math.pow(diameter, 4)
        turning += 1
        worstCt = math.max(worstCt, math.abs(ctFlown - lookup(ct, j)))
        if (timeSec >= SettledAfterSec)
          worstThrust = math.max(worstThrust, math.abs(thrustN - fromDefinition) / math.max(thrustN, 1e-9))
        maxThrustN = math.max(maxThrustN, thrustN)
        if (i % 40 == 0)
          println(f"$rpm%8.0f $j%8.4f $ctFlown%9.5f ${lookup(ct, j)}%9.5f $thrustN%9.3f $fromDefinition%13.3f")
      }
    }
    println(f"  $turning%d samples with the fan turning")
    println(f"  worst gap in Ct against the exported table: $worstCt%.2e")
    println(f"  worst gap in thrust against Ct rho n^2 D^4, once settled: ${worstThrust * 100}%.4f %%")

    divergedAt.foreach { where =>
      println("  the flight trace stopped being numbers here:")
      println(s"    $where")
      println("  that is JSBSim's integration giving up, not a measurement — see issue #24")
    }
    check("the flight stayed finite from start to finish", divergedAt.isEmpty)

    check("the fan turned", turning > 50)
    check("JSBSim read the exported curve, row for row", worstCt < 1e-9)
    // This is the one that matters: it says JSBSim's Ct means what the derivation assumed — per revolution
    // per second, against the fourth power of the diameter. If it did not, the thrust would be out by orders
    // of magnitude and every other check would still pass.
    // A tenth of a per cent: the question is whether JSBSim means the same thing by Ct — per revolution per
    // second, against the fourth power of the diameter — and a different meaning is out by factors of tens or
    // thousands, not by a rounding difference in how it applies the force.
    check("and its thrust is that coefficient times rho n^2 D^4", worstThrust < 1e-3)
    // A duct keeps most of its static thrust at speed, which is the point of one. The static figure is itself
    // derived from the bore, the revolutions and the power, so this compares against that derivation.
    val derived = JsbsimExporter.ductedFanCurves(shaft, fan, model.getAvl.units()).right.get
    val staticKg = derived.staticThrustN / 9.80665
    println(f"  it pushed up to ${maxThrustN / 9.80665}%.2f kg, against ${staticKg}%.2f kg derived static")
    // At part throttle it pushes less than its static figure, and at speed on full power it can push more:
    // a fan unloads as the aircraft speeds up, so it spins faster on the same watts. Both are right, which is
    // why this only asks that it be pushing in the same league rather than pinning a number.
    check("it pushes a useful part of its static thrust",
      maxThrustN / 9.80665 > 0.4 * staticKg && maxThrustN / 9.80665 < 1.5 * staticKg)

    println(if (ok) "DUCTED_FAN_FLIGHT_OK" else "DUCTED_FAN_FLIGHT_FAIL")
    if (!ok) sys.exit(1)
  }
}
