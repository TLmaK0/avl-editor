/*
 * The combustion path, flown under power — the third and last thruster type, and the one #25 named as
 * still covered by nothing. `CombustionPackageCheck` reads the piston package the exporter *writes*;
 * this starts that engine in JSBSim and flies it, which is a different question. The one thing only a
 * flight can answer here is whether the engine **burns fuel**: a tank the exporter fills and the engine
 * never draws from looks identical, in every written file, to one that feeds it.
 * Run with:  sbt "Test/runMain com.abajar.avleditor.jsbsim.CombustionFlightCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.{AvlManager, JsbsimManager}
import com.abajar.avleditor.avl.connectivity.AvlRunner
import com.abajar.avleditor.crrcsim.CRRCSim
import java.io.{File, PrintWriter}
import java.util.Properties

object CombustionFlightCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private val SlugFt3ToKgM3 = 515.378818
  private val LbsToNewtons = 4.4482216
  private val LbsToKg = 0.45359237

  private def write(file: File, content: String): Unit = {
    Option(file.getParentFile).foreach(_.mkdirs())
    val pw = new PrintWriter(file)
    try pw.write(content) finally pw.close()
  }

  private def tag(xml: String, name: String): Option[Double] =
    s"""<$name[^>]*>([-\\d.eE+]+)</$name>""".r.findFirstMatchIn(xml).map(_.group(1).toDouble)

  /**
   * The check aircraft with a small two-stroke where its electric motor was, and a tank to feed it.
   * The airframe is left alone so this measures the propulsion and not a second aeroplane: 10 cm3 at
   * 1.2 kW is an ordinary .60-size glow motor, which is what a 1.1 kg sport model would carry.
   */
  private def pistonAircraft(): CRRCSim = {
    val crrcsim = com.abajar.avleditor.TestAircraft.conventional()
    val power = crrcsim.getConfig.getPower
    val shaft = power.getBateries.get(0).getShafts.get(0)
    shaft.getEngines.clear() // the electric motor goes; the propeller it drove stays
    val engine = shaft.createCombustionEngine()
    engine.setMass(0.20f); engine.getPos.setX(0.05f)
    engine.setDisplacement(10.0f)
    engine.setMaxPower(1200.0f)
    engine.setIdleRpm(2500.0f)
    engine.setMaxRpm(14000.0f)
    engine.setCycles(2)
    engine.setFuelConsumption(700.0f)
    power.getFuelTanks.clear()
    val tank = power.createFuelTank()
    tank.setCapacity(0.5f)
    tank.setContents(0.5f)
    tank.getPos.setX(0.25f); tank.getPos.setY(0f); tank.getPos.setZ(0f)
    crrcsim.calculate()
    crrcsim
  }

  def main(args: Array[String]): Unit = {
    val jsbsim = JsbsimManager.ensureJsbsimAvailable()
    val props = new Properties()
    val avl = AvlManager.ensureAvlAvailable(props)
    if (jsbsim.isEmpty || !avl) {
      println(s"  JSBSim: ${jsbsim.getOrElse("not found")}; AVL: $avl — this check needs both")
      println("COMBUSTION_FLIGHT_SKIPPED")
      return
    }

    val root = new File(System.getProperty("java.io.tmpdir"), "avleditor-combustion-flight")
    def deleteTree(f: File): Unit = {
      if (f.isDirectory) Option(f.listFiles).foreach(_.foreach(deleteTree))
      f.delete()
    }
    deleteTree(root)

    println("exporting the check aircraft with a glow motor where its electric one was")
    val model = pistonAircraft()
    check("the model is fit to export", SimulationRequirements.validate(model).isEmpty)
    val calc = new AvlRunner(props.getProperty("avl.path"), model.getAvl, model.getOriginPath).getCalculation()
    JsbsimExporter.export(root, "glow", model, calc)

    val engineXml = scala.io.Source.fromFile(new File(root, "engine/glow_motor.xml")).mkString
    check("the exported engine is a piston engine", engineXml.contains("<piston_engine"))
    // The speeds the rotor is judged against are the ones the exported file itself states, so this
    // stops measuring the export the day the export changes rather than quietly measuring nothing.
    val idleRpm = tag(engineXml, "idlerpm")
    val maxRpm = tag(engineXml, "maxrpm")
    check("and it states the speeds it runs between", idleRpm.isDefined && maxRpm.isDefined)

    println("flying it at full throttle from a slow, level start")
    write(new File(root, "aircraft/glow/reset00.xml"),
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
        |<output name="glow.csv" type="CSV" rate="20">
        |  <property> propulsion/engine/propeller-rpm </property>
        |  <property> propulsion/engine/thrust-lbs </property>
        |  <property> propulsion/engine/engine-rpm </property>
        |  <property> propulsion/total-fuel-lbs </property>
        |  <property> velocities/vtrue-fps </property>
        |  <property> atmosphere/rho-slugs_ft3 </property>
        |</output>
        |""".stripMargin)
    // A piston engine needs its mixture and magnetos as well as a throttle: `set-running` arms it, and
    // an engine left lean or with the magnetos off simply never fires, which would read here as an
    // aeroplane that glides.
    write(new File(root, "run.xml"),
      """<?xml version="1.0"?>
        |<runscript name="full power from a slow start">
        |  <use aircraft="glow" initialize="reset00"/>
        |  <run start="0.0" end="8.0" dt="0.0041666">
        |    <event name="start and open the throttle">
        |      <condition> simulation/sim-time-sec >= 0.1 </condition>
        |      <set name="propulsion/set-running" value="-1"/>
        |      <set name="fcs/throttle-cmd-norm" value="1.0"/>
        |      <set name="fcs/throttle-pos-norm" value="1.0"/>
        |      <set name="fcs/mixture-cmd-norm" value="1.0"/>
        |      <set name="fcs/mixture-pos-norm" value="1.0"/>
        |      <set name="propulsion/magneto_cmd" value="3"/>
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
    val csv = new File(root, "glow.csv")
    if (!csv.exists) {
      println(output.linesIterator.toList.takeRight(15).map("    " + _).mkString("\n"))
      check("JSBSim ran", false)
      println("COMBUSTION_FLIGHT_FAIL")
      sys.exit(1)
    }

    val lines = scala.io.Source.fromFile(csv).getLines().toList
    val header = lines.head.split(",").map(_.trim)
    def col(fragment: String): Int = header.indexWhere(_.endsWith(fragment))
    val (rpmc, tc, ercp, fuelc, vc, rhoc) = (col("propeller-rpm"), col("thrust-lbs"),
      col("engine-rpm"), col("total-fuel-lbs"), col("vtrue-fps"), col("rho-slugs_ft3"))

    def finite(cell: String): Option[Double] =
      scala.util.Try(cell.trim.toDouble).toOption.filter(d => !d.isNaN && !d.isInfinite)

    var divergedAt: Option[String] = None
    var turning = 0
    var maxEngineRpm = 0.0
    var minRunningEngineRpm = Double.MaxValue
    var firstFuelKg = 0.0
    var lastFuelKg = 0.0
    var firstSpeed = 0.0
    var lastSpeed = 0.0
    var maxThrustN = 0.0

    println(f"${"t"}%6s ${"engine rpm"}%11s ${"prop rpm"}%9s ${"T (N)"}%8s ${"fuel kg"}%8s ${"V m/s"}%7s")
    lines.tail.zipWithIndex.foreach { case (line, i) =>
      val cells = line.split(",")
      val timeSec = finite(cells(0)).getOrElse(0.0)
      val row = Seq(rpmc, tc, ercp, fuelc, vc, rhoc).map(idx => finite(cells(idx)))
      if (row.exists(_.isEmpty) && divergedAt.isEmpty)
        divergedAt = Some(s"t=${cells.headOption.getOrElse("?")}s, row ${i + 1}: ${line.take(120)}")

      if (row.forall(_.isDefined)) {
        val Seq(propRpm, thrustLbs, engineRpm, fuelLbs, vFps, _) = row.map(_.get)
        val fuelKg = fuelLbs * LbsToKg
        val speed = vFps * 0.3048
        if (i == 0) { firstFuelKg = fuelKg; firstSpeed = speed }
        lastFuelKg = fuelKg; lastSpeed = speed
        if (propRpm > 0) {
          turning += 1
          maxEngineRpm = math.max(maxEngineRpm, engineRpm)
          if (timeSec >= 1.0) minRunningEngineRpm = math.min(minRunningEngineRpm, engineRpm)
          maxThrustN = math.max(maxThrustN, thrustLbs * LbsToNewtons)
          if (i % 40 == 0)
            println(f"$timeSec%6.2f $engineRpm%11.0f $propRpm%9.0f ${thrustLbs * LbsToNewtons}%8.2f $fuelKg%8.4f $speed%7.2f")
        }
      }
    }

    println(f"  $turning%d samples with the engine turning, top ${maxEngineRpm}%.0f rpm")
    println(f"  fuel went from ${firstFuelKg}%.4f to ${lastFuelKg}%.4f kg — ${(firstFuelKg - lastFuelKg) * 1000}%.2f g burnt")
    println(f"  speed went from ${firstSpeed}%.2f to ${lastSpeed}%.2f m/s, pushing up to ${maxThrustN}%.2f N")

    divergedAt.foreach { where =>
      println("  the flight trace stopped being numbers here:")
      println(s"    $where")
      println("  that is JSBSim's integration giving up, not a measurement — see issue #24")
    }
    check("the flight stayed finite from start to finish", divergedAt.isEmpty)
    check("the engine ran", turning > 50)

    // Same guard as the propeller check, and for the same reason it exists: with the engine
    // deliberately broken there was no flight to read, and assertions about a flight that never
    // happened printed PASS across nought rows. A dead flight is one failure that says so.
    val flownEnough = divergedAt.isEmpty && turning > 50
    if (!flownEnough) {
      println("  not judging the flight itself: there was no flight to read")
      check("the exported model could be flown at all", false)
    } else {
      // The bound is the engine's own, read from the file JSBSim loaded.
      for (idle <- idleRpm; top <- maxRpm) {
        println(f"  the exported engine states it runs between ${idle}%.0f and ${top}%.0f rpm")
        check("the engine stayed within the speeds it states", maxEngineRpm <= top * 1.02)
        check("and never fell below its own idle while running", minRunningEngineRpm >= idle * 0.98)
      }
      // The assertion only a flight can make: an engine that produces thrust while burning nothing
      // is a tank the export filled and the engine never drew from, and every written file agrees
      // with itself in that case.
      check("it burnt fuel to do it", firstFuelKg - lastFuelKg > 0)
      check("and there is fuel left, so it was not simply drained", lastFuelKg > 0)
      check("it pushed", maxThrustN > 0)
      check("the aeroplane accelerated at full power", lastSpeed > firstSpeed)
    }

    println(if (ok) "COMBUSTION_FLIGHT_OK" else "COMBUSTION_FLIGHT_FAIL")
    if (!ok) sys.exit(1)
  }
}
