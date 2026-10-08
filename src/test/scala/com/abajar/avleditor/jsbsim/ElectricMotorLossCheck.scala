/*
 * The other half of issue #19, measured rather than assumed. That electrical power is no longer written
 * as shaft power is a fact about what the export stopped doing (issue #24's brushless-motor change); it is
 * not yet a fact about what JSBSim actually computes. The issue's own body asks for the second: "shaft
 * power is below electrical power at every point on the curve", so it survives the day someone edits
 * FGBrushLessDCMotor's constants and nobody notices the two have swapped order.
 *
 * JSBSim exposes no voltage property for this engine (`--catalog` on the exported model has
 * `current-amperes` and `power-hp` and nothing volts-shaped), so the applied voltage is reconstructed from
 * the two things the motor file and the flight both state: `<maxvolts>` and the commanded throttle —
 * exactly `FGBrushLessDCMotor::Calculate`'s own `V = MaxVolts * ThrottlePos` (JSBSim source,
 * `src/models/propulsion/FGBrushLessDCMotor.cpp`). Electrical power is that voltage times the current the
 * motor reports drawing; shaft power is `power-hp` converted to watts — JSBSim's own name for what the
 * motor hands the propeller.
 *
 * Run with:  sbt "Test/runMain com.abajar.avleditor.jsbsim.ElectricMotorLossCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.{AvlManager, JsbsimManager}
import com.abajar.avleditor.avl.connectivity.AvlRunner
import java.io.{File, PrintWriter}
import java.util.Properties

object ElectricMotorLossCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** JSBSim's own factor, matching `JsbsimWriter.WattsPerHp`: it has to be the same number the export uses
   *  to turn the motor's data curve into `<maxhp>`/the equivalent, or the two could disagree about watts. */
  private val HpToWatts = 745.699872

  private def write(file: File, content: String): Unit = {
    Option(file.getParentFile).foreach(_.mkdirs())
    val pw = new PrintWriter(file)
    try pw.write(content) finally pw.close()
  }

  private def tag(xml: String, name: String): Option[Double] =
    s"""<$name[^>]*>([-\\d.eE+]+)</$name>""".r.findFirstMatchIn(xml).map(_.group(1).toDouble)

  def main(args: Array[String]): Unit = {
    val jsbsim = JsbsimManager.ensureJsbsimAvailable()
    val props = new Properties()
    val avl = AvlManager.ensureAvlAvailable(props)
    if (jsbsim.isEmpty || !avl) {
      println(s"  JSBSim: ${jsbsim.getOrElse("not found")}; AVL: $avl — this check needs both")
      println("ELECTRIC_MOTOR_LOSS_SKIPPED")
      return
    }

    val root = new File(System.getProperty("java.io.tmpdir"), "avleditor-electric-motor-loss")
    def deleteTree(f: File): Unit = {
      if (f.isDirectory) Option(f.listFiles).foreach(_.foreach(deleteTree))
      f.delete()
    }
    deleteTree(root)

    println("exporting the check aircraft's own brushless motor")
    val model = com.abajar.avleditor.TestAircraft.conventional()
    check("the model is fit to export", SimulationRequirements.validate(model).isEmpty)
    val calc = new AvlRunner(props.getProperty("avl.path"), model.getAvl, model.getOriginPath).getCalculation()
    JsbsimExporter.export(root, "sport", model, calc)

    val engineFile = scala.io.Source.fromFile(new File(root, "engine/sport_motor.xml")).mkString
    check("the export wrote a brushless_dc_motor, not an electric_engine",
      engineFile.contains("<brushless_dc_motor") && !engineFile.contains("<electric_engine"))
    val maxVolts = tag(engineFile, "maxvolts")
    check("the exported motor states its maximum volts", maxVolts.isDefined)

    println("flying it at full throttle from a slow, level start")
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
        |<output name="motor.csv" type="CSV" rate="20">
        |  <property> propulsion/engine/propeller-rpm </property>
        |  <property> propulsion/engine/current-amperes </property>
        |  <property> propulsion/engine/power-hp </property>
        |  <property> fcs/throttle-pos-norm </property>
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
    val csv = new File(root, "motor.csv")
    if (!csv.exists) {
      println(output.linesIterator.toList.takeRight(15).map("    " + _).mkString("\n"))
      check("JSBSim ran", false)
      println("ELECTRIC_MOTOR_LOSS_FAIL")
      sys.exit(1)
    }

    val lines = scala.io.Source.fromFile(csv).getLines().toList
    val header = lines.head.split(",").map(_.trim)
    def col(fragment: String): Int = header.indexWhere(_.endsWith(fragment))
    val (rpmc, ic, hpc, thrc) =
      (col("propeller-rpm"), col("current-amperes"), col("power-hp"), col("throttle-pos-norm"))

    def finite(cell: String): Option[Double] =
      scala.util.Try(cell.trim.toDouble).toOption.filter(d => !d.isNaN && !d.isInfinite)

    var divergedAt: Option[String] = None
    // Pairs of (shaft watts, electrical watts) at every sampled instant the motor was actually drawing
    // current — the "curve" the issue's body means, spanning the rpm this one spin-up actually visits.
    var powerPairs = List.empty[(Double, Double)]
    var maxRpm = 0.0

    println(f"${"t"}%6s ${"rpm"}%8s ${"throttle"}%9s ${"I (A)"}%7s ${"P shaft (W)"}%12s ${"P elec (W)"}%11s")
    lines.tail.zipWithIndex.foreach { case (line, i) =>
      val cells = line.split(",")
      val row = Seq(rpmc, ic, hpc, thrc).map(idx => finite(cells(idx)))
      if (row.exists(_.isEmpty) && divergedAt.isEmpty)
        divergedAt = Some(s"t=${cells.headOption.getOrElse("?")}s, row ${i + 1}: ${line.take(120)}")

      if (row.forall(_.isDefined)) {
        val Seq(rpm, current, hp, throttle) = row.map(_.get)
        maxRpm = math.max(maxRpm, rpm)
        if (current > 0 && maxVolts.isDefined) {
          val shaftW = hp * HpToWatts
          val elecW = maxVolts.get * throttle * current
          powerPairs ::= ((shaftW, elecW))
          if (i % 40 == 0)
            println(f"${cells(0)}%6s $rpm%8.0f $throttle%9.2f $current%7.3f $shaftW%12.3f $elecW%11.3f")
        }
      }
    }

    divergedAt.foreach { where =>
      println("  the flight trace stopped being numbers here:")
      println(s"    $where")
    }
    check("the flight stayed finite from start to finish", divergedAt.isEmpty)
    check("the motor turned and drew current", maxRpm > 100 && powerPairs.nonEmpty)

    if (divergedAt.isDefined || powerPairs.isEmpty) {
      println("  not judging the property itself: there was nothing to read it from")
      check("the exported motor could be measured at all", false)
    } else {
      println(s"  ${powerPairs.length} sampled points with current flowing")
      val worst = powerPairs.map { case (shaft, elec) => (shaft - elec, shaft, elec) }.maxBy(_._1)
      val (worstGap, worstShaft, worstElec) = worst
      println(f"  largest (shaft - electrical): $worstGap%.4f W, at shaft=$worstShaft%.3f W, " +
        f"electrical=$worstElec%.3f W")
      check("shaft power is below electrical power at every sampled point", worstGap < 0.0)

      val efficiencies = powerPairs.map { case (shaft, elec) => shaft / elec }
      println(f"  efficiency (shaft/electrical) ranges ${efficiencies.min}%.3f to ${efficiencies.max}%.3f")
      // Not a tight bound: this is an ordinary small brushless motor losing current to
      // coilresistance and noloadcurrent, not a claim about any particular number.
      check("every efficiency is a real fraction, never zero and never above one",
        efficiencies.forall(e => e > 0.0 && e < 1.0))
    }

    println(if (ok) "ELECTRIC_MOTOR_LOSS_OK" else "ELECTRIC_MOTOR_LOSS_FAIL")
    if (!ok) sys.exit(1)
  }
}
