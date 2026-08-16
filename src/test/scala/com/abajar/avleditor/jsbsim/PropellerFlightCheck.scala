/*
 * The propeller path, flown under power. `JsbsimCurveCheck` flies an exported model but never starts the
 * engine — its manoeuvre is a pull-up on a glide — and `DuctedFanFlightCheck` is the only check in the suite
 * that opens a throttle, on a thruster type most models do not use. So until this existed, "the exported model
 * flies" meant "it glides with the right lift and drag", in a tool whose output is a powered aircraft, and that
 * is how #24 survived: the export produced `nan` the moment the throttle opened and nothing ever opened one.
 * Run with:  sbt "Test/runMain com.abajar.avleditor.jsbsim.PropellerFlightCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.{AvlManager, JsbsimManager}
import com.abajar.avleditor.avl.connectivity.AvlRunner
import java.io.{File, PrintWriter}
import java.util.Properties

object PropellerFlightCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private val SlugFt3ToKgM3 = 515.378818
  private val LbsToNewtons = 4.4482216
  private val FtToM = 0.3048

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

  private def tag(xml: String, name: String): Option[Double] =
    s"""<$name[^>]*>([-\\d.eE+]+)</$name>""".r.findFirstMatchIn(xml).map(_.group(1).toDouble)

  def main(args: Array[String]): Unit = {
    val jsbsim = JsbsimManager.ensureJsbsimAvailable()
    val props = new Properties()
    val avl = AvlManager.ensureAvlAvailable(props)
    if (jsbsim.isEmpty || !avl) {
      println(s"  JSBSim: ${jsbsim.getOrElse("not found")}; AVL: $avl — this check needs both")
      println("PROPELLER_FLIGHT_SKIPPED")
      return
    }

    val root = new File(System.getProperty("java.io.tmpdir"), "avleditor-propeller-flight")
    def deleteTree(f: File): Unit = {
      if (f.isDirectory) Option(f.listFiles).foreach(_.foreach(deleteTree))
      f.delete()
    }
    deleteTree(root)

    println("exporting the check aircraft, which drives an ordinary propeller")
    // The check's own aeroplane, not a sample: those are the user's and they get edited between runs.
    val model = com.abajar.avleditor.TestAircraft.conventional()
    check("the model is fit to export", SimulationRequirements.validate(model).isEmpty)
    val calc = new AvlRunner(props.getProperty("avl.path"), model.getAvl, model.getOriginPath).getCalculation()
    JsbsimExporter.export(root, "sport", model, calc)

    val propFile = scala.io.Source.fromFile(new File(root, "engine/sport_prop.xml")).mkString
    val engineFile = scala.io.Source.fromFile(new File(root, "engine/sport_motor.xml")).mkString
    val ct = tableRows(propFile, "C_THRUST")
    val diameter = tag(propFile, "diameter").getOrElse(0.0)
    check("the exported thruster is the model's propeller", math.abs(diameter - 0.254) < 1e-6)
    check("with a thrust curve to fly on", ct.length > 2)

    // No rotor can turn faster than the motor's own no-load speed, and both numbers are read out of the
    // file JSBSim was handed rather than restated here: a check that carried its own copy of them would
    // stop measuring the export the day the export changed.
    val noLoadRpm = for (kv <- tag(engineFile, "velocityconstant"); volts <- tag(engineFile, "maxvolts"))
      yield kv * volts
    check("the exported engine states the constants a speed can be bounded by", noLoadRpm.isDefined)

    println("flying it at full throttle from a slow, level start")
    // Slow enough that a 250 W motor on a 1.1 kg model has something to accelerate: at 16 m/s this aeroplane
    // is already near where its drag balances the thrust, and "does it accelerate" would measure the choice
    // of starting speed rather than the propulsion.
    write(new File(root, "aircraft/sport/reset00.xml"),
      """<?xml version="1.0"?>
        |<initialize name="level and slow">
        |  <ubody unit="M/SEC"> 10.0 </ubody>
        |  <vbody unit="M/SEC"> 0.0 </vbody>
        |  <wbody unit="M/SEC"> 0.0 </wbody>
        |  <altitude unit="M"> 100.0 </altitude>
        |  <phi unit="DEG"> 0.0 </phi><theta unit="DEG"> 0.0 </theta><psi unit="DEG"> 0.0 </psi>
        |</initialize>
        |""".stripMargin)
    write(new File(root, "log.xml"),
      """<?xml version="1.0"?>
        |<output name="propeller.csv" type="CSV" rate="20">
        |  <property> propulsion/engine/advance-ratio </property>
        |  <property> propulsion/engine/thrust-coefficient </property>
        |  <property> propulsion/engine/propeller-rpm </property>
        |  <property> propulsion/engine/thrust-lbs </property>
        |  <property> atmosphere/rho-slugs_ft3 </property>
        |  <property> velocities/vtrue-fps </property>
        |</output>
        |""".stripMargin)
    write(new File(root, "run.xml"),
      """<?xml version="1.0"?>
        |<runscript name="full power from a slow start">
        |  <use aircraft="sport" initialize="reset00"/>
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
    val csv = new File(root, "propeller.csv")
    if (!csv.exists) {
      println(output.linesIterator.toList.takeRight(15).map("    " + _).mkString("\n"))
      check("JSBSim ran", false)
      println("PROPELLER_FLIGHT_FAIL")
      sys.exit(1)
    }

    val lines = scala.io.Source.fromFile(csv).getLines().toList
    val header = lines.head.split(",").map(_.trim)
    def col(fragment: String): Int = header.indexWhere(_.endsWith(fragment))
    val (jc, ctc, rpmc, tc, rhoc, vc) = (col("advance-ratio"), col("thrust-coefficient"),
      col("propeller-rpm"), col("thrust-lbs"), col("rho-slugs_ft3"), col("vtrue-fps"))

    // A `nan` in a flight trace is the finding, not an exception: the aircraft did not fly. It is read as
    // one, and the instant it started is reported, because when it happened says whether the model diverged
    // on the first step or ten seconds in.
    def finite(cell: String): Option[Double] =
      scala.util.Try(cell.trim.toDouble).toOption.filter(d => !d.isNaN && !d.isInfinite)

    var divergedAt: Option[String] = None
    var turning = 0
    var worstCt = 0.0
    var maxRpm = 0.0
    var settledRpm = 0.0
    var firstSpeed = 0.0
    var lastSpeed = 0.0
    // The thrust identity is gathered first and judged afterwards, because how wrong it is has to be
    // measured against something that does not itself go to zero — see below.
    var thrustPairs = List.empty[(Double, Double)]

    // The thrust identity is measured once the rotor is up to speed. Both sides of it are logged 20 times a
    // second while JSBSim integrates at 240, so across the spin-up the rpm and the thrust in one row are not
    // from the same instant and the comparison would measure the sampling rather than the definition.
    val SettledAfterSec = 2.0

    println(f"${"t"}%6s ${"rpm"}%8s ${"J"}%8s ${"Ct flown"}%9s ${"Ct table"}%9s ${"T flown"}%9s ${"V m/s"}%7s")
    lines.tail.zipWithIndex.foreach { case (line, i) =>
      val cells = line.split(",")
      val timeSec = finite(cells(0)).getOrElse(0.0)
      val row = Seq(jc, ctc, rpmc, tc, rhoc, vc).map(idx => finite(cells(idx)))
      if (row.exists(_.isEmpty) && divergedAt.isEmpty)
        divergedAt = Some(s"t=${cells.headOption.getOrElse("?")}s, row ${i + 1}: ${line.take(120)}")

      if (row.forall(_.isDefined)) {
        val Seq(j, ctFlown, rpm, thrustLbs, rhoSlugs, vFps) = row.map(_.get)
        val thrustN = thrustLbs * LbsToNewtons
        val rho = rhoSlugs * SlugFt3ToKgM3
        val speed = vFps * FtToM
        if (i == 0) firstSpeed = speed
        lastSpeed = speed
        if (rpm > 0) {
          val n = rpm / 60.0
          val fromDefinition = ctFlown * rho * n * n * math.pow(diameter, 4)
          turning += 1
          maxRpm = math.max(maxRpm, rpm)
          worstCt = math.max(worstCt, math.abs(ctFlown - lookup(ct, j)))
          if (timeSec >= SettledAfterSec) {
            thrustPairs ::= ((thrustN, fromDefinition))
            settledRpm = rpm
          }
          if (i % 40 == 0)
            println(f"$timeSec%6.2f $rpm%8.0f $j%8.4f $ctFlown%9.5f ${lookup(ct, j)}%9.5f $thrustN%9.3f $speed%7.2f")
        }
      }
    }

    // A propeller **runs out of thrust** as the aircraft speeds up: this one reaches the advance ratio where
    // its `C_THRUST` crosses zero before the run ends, which a ducted fan never does inside its own curve. So
    // the gap cannot be taken relative to the thrust of each row — at the crossing that divides by nothing and
    // reports hundreds of thousands of per cent for a row where both sides agree to a milli-newton. It is taken
    // against the largest thrust of the run, which is the scale of the quantity being compared.
    val peakThrustN = thrustPairs.map { case (flown, _) => math.abs(flown) }.reduceOption(_ max _).getOrElse(0.0)
    val worstThrust = thrustPairs.map { case (flown, fromDefinition) =>
      math.abs(flown - fromDefinition) / math.max(peakThrustN, 1e-9)
    }.reduceOption(_ max _).getOrElse(Double.MaxValue)

    println(f"  $turning%d samples with the propeller turning, top ${maxRpm}%.0f rpm, settled ${settledRpm}%.0f rpm")
    println(f"  speed went from ${firstSpeed}%.2f to ${lastSpeed}%.2f m/s at full power")
    println(f"  worst gap in Ct against the exported table: $worstCt%.2e")
    println(f"  worst gap in thrust against Ct rho n^2 D^4, against a ${peakThrustN}%.2f N peak: ${worstThrust * 100}%.4f %%")

    divergedAt.foreach { where =>
      println("  the flight trace stopped being numbers here:")
      println(s"    $where")
      println("  that is JSBSim's integration giving up, not a measurement — see issue #24")
    }

    // This is the assertion the whole check exists for: it is exactly what #24 broke, on exactly the thruster
    // #24 broke it on, and nothing in the suite was looking.
    check("the flight stayed finite from start to finish", divergedAt.isEmpty)
    check("the propeller turned", turning > 50)

    // Everything below reads the flight, so none of it can be judged on a flight that did not happen.
    // Proving this check goes red is what found the need for the guard: with the engine deliberately broken
    // back to #24's `electric_engine`, the trace was `nan` from t = 0.2 s and three of these still printed
    // PASS — the exported curve matched "row for row" across nought rows, and the aeroplane "accelerated" to
    // 1,254 m/s. A vacuous pass is worse than a failure, because it is the suite reporting agreement about
    // something it never measured. A dead flight is now one failure that says so.
    val flownEnough = divergedAt.isEmpty && turning > 50
    if (!flownEnough) {
      println("  not judging the flight itself: there was no flight to read")
      check("the exported model could be flown at all", false)
    } else {
      check("JSBSim read the exported curve, row for row", worstCt < 1e-9)
      check("and its thrust is that coefficient times rho n^2 D^4", worstThrust < 1e-3)
      // A speed above what the motor could turn unloaded means the rotor was never held back by anything the
      // export described — it passed through its own physics rather than settling against it.
      noLoadRpm.foreach { limit =>
        println(f"  the exported motor cannot turn faster than ${limit}%.0f rpm unloaded")
        check("the rotor settled at a speed the exported motor could turn", maxRpm > 0 && maxRpm <= limit)
      }
      check("and it settled rather than passing through", settledRpm > 0.3 * maxRpm)
      check("the aeroplane accelerated at full power", lastSpeed > firstSpeed)
    }

    println(if (ok) "PROPELLER_FLIGHT_OK" else "PROPELLER_FLIGHT_FAIL")
    if (!ok) sys.exit(1)
  }
}
