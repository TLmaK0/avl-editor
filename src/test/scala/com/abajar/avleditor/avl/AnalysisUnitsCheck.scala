/*
 * The aircraft AVL is asked about must be the same aeroplane whatever the model writes its figures down in.
 *
 * It was not. The operating point — the lift coefficient the whole analysis is measured at — was built from
 * a weight in newtons and a density in kg/m3 divided by a speed and a reference area **in the model's own
 * units**, and the speed handed to AVL was the field as typed. A model stated in metres and seconds was
 * right, which is every check in this project and every sample, and that is exactly why it survived: a model
 * in centimetres was flown a hundred times too fast at a ten-thousandth of the lift coefficient.
 *
 * Two halves here, and the first is the one that could not be reasoned out. **AVL's run-case velocity is in
 * m/s whatever `Lunit` says** — that is a fact about AVL, so it is asked rather than assumed, the same way
 * `EigenvectorUnitsCheck` asked what its mode shapes are in. One aeroplane is written out twice, in metres
 * and in centimetres, and AVL is made to fly both.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.AnalysisUnitsCheck"
 */
package com.abajar.avleditor.avl

import com.abajar.avleditor.{AvlManager, ModelUnits, UnitConversor}
import com.abajar.avleditor.crrcsim.{CRRCSim, CRRCSimFactory}
import java.io.PrintWriter
import java.nio.file.{Files, Path}
import java.util.Properties
import scala.io.Source

object AnalysisUnitsCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def relative(a: Double, b: Double): Double =
    if (b == 0.0) math.abs(a) else math.abs(a / b - 1.0)

  // -------------------------------------------------------------------------------------------------
  // The editor's own arithmetic
  // -------------------------------------------------------------------------------------------------

  /**
   * One physical aeroplane — 0.94 kg over 0.3909 m2 at 20 m/s in sea-level air — written down in whichever
   * units are asked for. Only the unit-bearing figures are scaled; the weight and the density are in SI
   * whatever the model says, which is the whole reason the conversion has to happen somewhere.
   */
  private def model(lengthUnit: String, timeUnit: String): CRRCSim = {
    val units = new ModelUnits(lengthUnit, "kg", timeUnit)
    val crrcsim = new CRRCSimFactory().create()
    val avl = crrcsim.getAvl
    avl.setLengthUnit(lengthUnit)
    avl.setTimeUnit(timeUnit)
    // 20 m/s, as this model would write it: metres per second divided by the length unit, times the time.
    avl.setVelocity(20f / units.metresPerLengthUnit * units.secondsPerTimeUnit)
    avl.setReynoldsNumber(1.225f)
    avl.getGeometry.setSref(0.3909f / (units.metresPerLengthUnit * units.metresPerLengthUnit))
    avl.setAnalysisWeightKg(0.9397f)
    crrcsim
  }

  private def theEditorsArithmetic(): Unit = {
    println("one aeroplane, written down five ways")
    val metres = model("m", "s")
    val reference = metres.getAvl.analysisLiftCoefficient.floatValue
    println(f"  in metres and seconds: CL $reference%.6f at " +
      f"${metres.getAvl.analysisVelocityMetresPerSecond}%.3f m/s over " +
      f"${metres.getAvl.analysisReferenceAreaSquareMetres}%.4f m2")

    Seq(("cm", "s"), ("in", "s"), ("m", "m"), ("cm", "h")).foreach { case (length, time) =>
      val other = model(length, time).getAvl
      println(f"  in $length%s and $time%s: velocity ${other.getVelocity}%.4f -> " +
        f"${other.analysisVelocityMetresPerSecond}%.3f m/s, Sref ${other.getGeometry.getSref}%.4f -> " +
        f"${other.analysisReferenceAreaSquareMetres}%.4f m2, CL ${other.analysisLiftCoefficient}%.6f")
      check(s"$length/$time flies at the same 20 m/s",
        relative(other.analysisVelocityMetresPerSecond, 20.0) < 1e-5)
      check(s"$length/$time has the same 0.3909 m2 of wing",
        relative(other.analysisReferenceAreaSquareMetres, 0.3909) < 1e-5)
      check(s"$length/$time needs the same lift coefficient",
        relative(other.analysisLiftCoefficient.floatValue, reference) < 1e-4)
    }

    // The lift coefficient is what it claims to be: the lift equals the weight at that speed.
    val lift = 0.5 * 1.225 * 20.0 * 20.0 * 0.3909 * reference
    println(f"  which holds ${lift}%.4f N against ${0.9397 * AVL.GRAVITY}%.4f N of aeroplane")
    check("the lift it needs equals the weight it has", relative(lift, 0.9397 * AVL.GRAVITY) < 1e-4)

    println("the two ways to a speed in m/s, which must not drift apart")
    // AVL converts it for the run; a Configuration converts it again afterwards from the factors written
    // beside Bref. They are the same number by two routes, and a run whose record disagreed with the run
    // would be worse than either being wrong.
    Seq(("m", "s"), ("cm", "s"), ("in", "m")).foreach { case (length, time) =>
      val avl = model(length, time).getAvl
      val config = new com.abajar.avleditor.avl.runcase.Configuration
      config.setVelocity(avl.getVelocity)
      config.setSref(avl.getGeometry.getSref)
      config.setMetresPerLengthUnit(avl.units.metresPerLengthUnit)
      config.setSecondsPerTimeUnit(avl.units.secondsPerTimeUnit)
      check(s"$length/$time: the run's record agrees with the run",
        relative(config.getVelocityMetresPerSecond, avl.analysisVelocityMetresPerSecond) < 1e-5 &&
          relative(config.getReferenceAreaSquareMetres, avl.analysisReferenceAreaSquareMetres) < 1e-5)
    }

    println("the table of factors, which is one table")
    val conversor = new UnitConversor
    // It returned the factor and threw the quantity away. secondsPerTimeUnit() passes 1, so it was right
    // where it was used and a landmine everywhere else.
    check("five minutes is three hundred seconds", conversor.convertToSeconds(5f, "m") == 300f)
    check("five hours is eighteen thousand", conversor.convertToSeconds(5f, "h") == 18000f)
    // A length multiplies, a time in a denominator divides.
    check("one centimetre per second is a hundredth of a metre per second",
      relative(conversor.convertToMetersPerSecond(1f, "cm", "s"), 0.01) < 1e-6)
    check("sixty metres per minute is one metre per second",
      relative(conversor.convertToMetersPerSecond(60f, "m", "m"), 1.0) < 1e-6)
    check("and metres per second are already metres per second",
      conversor.convertToMetersPerSecond(20f, "m", "s") == 20f)

    println("what the mass file states")
    val written = new java.io.ByteArrayOutputStream
    metres.getAvl.writeAVLMassData(written)
    val text = written.toString
    // g and rho are in m, kg, s whatever Lunit says — AVL's own comment — so neither is converted, and g
    // is the same constant the lift coefficient is derived with rather than a second, rounder copy of it.
    check("gravity is the one constant, not a rounder copy of it",
      text.contains("g   = " + AVL.GRAVITY))
    check("the density goes out in kg/m3, unconverted", text.contains("rho = 1.225"))
    check("and the unit lines are what convert the geometry", text.contains("Lunit = 1 m"))
  }

  // -------------------------------------------------------------------------------------------------
  // What AVL itself does with the velocity, asked rather than assumed
  // -------------------------------------------------------------------------------------------------

  /** A plain wing, tailplane and fin, every length multiplied by `scale`. */
  private def geometryFile(scale: Double): String = {
    def l(metres: Double) = f"${metres * scale}%.6f"
    s"""Probe
       |0.0
       |0     0     0.0
       |${l(0.2 * scale)}   ${l(0.2)}   ${l(1.0)}
       |${l(0.05)}   0.0   0.0
       |0.01
       |
       |SURFACE
       |Wing
       |8  1.0  12  1.0
       |YDUPLICATE
       |0.0
       |SECTION
       |0.0 0.0 0.0 ${l(0.2)} 0.0
       |NACA
       |2412
       |SECTION
       |0.0 ${l(0.5)} 0.0 ${l(0.2)} 0.0
       |NACA
       |2412
       |
       |SURFACE
       |Tail
       |6  1.0  8  1.0
       |YDUPLICATE
       |0.0
       |SECTION
       |${l(0.6)} 0.0 0.0 ${l(0.1)} 0.0
       |NACA
       |0010
       |SECTION
       |${l(0.6)} ${l(0.2)} 0.0 ${l(0.1)} 0.0
       |NACA
       |0010
       |
       |SURFACE
       |Fin
       |6  1.0  6  1.0
       |SECTION
       |${l(0.6)} 0.0 0.0 ${l(0.12)} 0.0
       |NACA
       |0010
       |SECTION
       |${l(0.62)} 0.0 ${l(0.16)} ${l(0.09)} 0.0
       |NACA
       |0010
       |""".stripMargin
  }

  /** The same masses, positioned in the same units, with `Lunit` telling AVL what they mean. */
  private def massFile(scale: Double, lunit: String): String = {
    val rows = Seq((0.6, 0.05, 0.0), (0.2, 0.05, -0.3), (0.2, 0.05, 0.3))
    val body = rows.map { case (m, x, y) =>
      f" $m%.6f  ${x * scale}%.6f  ${y * scale}%.6f  0.0   ${0.002 * scale * scale}%.8f  " +
        f"${0.004 * scale * scale}%.8f  ${0.005 * scale * scale}%.8f"
    }.mkString("\n")
    s"""Lunit = $lunit m
       |Munit = 1 kg
       |Tunit = 1 s
       |g   = 9.80665
       |rho = 1.225
       |#mass     x       y        z        Ixx      Iyy      Izz
       |$body
       |""".stripMargin
  }

  private def write(where: Path, name: String, content: String): Path = {
    val file = where.resolve(name)
    val out = new PrintWriter(file.toFile)
    try out.print(content) finally out.close()
    file
  }

  /** AVL's eigenvalues for one model at one velocity, in 1/s. */
  private def eigenvaluesAt(avlPath: String, where: Path, stem: String, velocity: Double): Seq[Double] = {
    val out = where.resolve(stem + "_" + velocity + ".eig")
    val builder = new ProcessBuilder(avlPath, where.resolve(stem + ".avl").toString)
    builder.directory(where.toFile)
    builder.redirectErrorStream(true)
    val process = builder.start()
    val commands = Seq("mset 0", "oper", "c1", "v", velocity.toString, "", "a c 0.5", "x", "",
      "mode", "n", "w", out.toString, "", "", "quit")
    val writer = new java.io.OutputStreamWriter(process.getOutputStream)
    try { commands.foreach(c => writer.write(c + "\n")); writer.flush() }
    finally { try writer.close() catch { case _: Exception => } }
    val reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream))
    try { while (reader.readLine() != null) {} } finally { reader.close() }
    process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
    if (!out.toFile.exists) return Seq.empty
    val source = Source.fromFile(out.toFile)
    try {
      source.getLines().toList.filterNot(_.trim.startsWith("#")).flatMap { line =>
        val columns = line.trim.split("\\s+")
        if (columns.length >= 3) try Some(columns(1).toDouble) catch { case _: Exception => None } else None
      }.sorted
    } finally source.close()
  }

  private def whatAvlDoesWithIt(avlPath: String): Unit = {
    val where = Files.createTempDirectory("avl_units_")
    write(where, "m.avl", geometryFile(1.0))
    write(where, "m.mass", massFile(1.0, "1"))
    write(where, "cm.avl", geometryFile(100.0))
    write(where, "cm.mass", massFile(100.0, "0.01"))

    println("the same aeroplane, written in metres and in centimetres")
    val inMetres = eigenvaluesAt(avlPath, where, "m", 14.0)
    val sameNumber = eigenvaluesAt(avlPath, where, "cm", 14.0)
    val hundredTimes = eigenvaluesAt(avlPath, where, "cm", 1400.0)
    println(f"  metres at 14:        ${inMetres.map(v => f"$v%.4f").mkString(", ")}%s")
    println(f"  centimetres at 14:   ${sameNumber.map(v => f"$v%.4f").mkString(", ")}%s")
    println(f"  centimetres at 1400: ${hundredTimes.map(v => f"$v%.4f").mkString(", ")}%s")

    check("AVL answered for all three", inMetres.nonEmpty && sameNumber.nonEmpty && hundredTimes.nonEmpty)
    // This is the measurement the conversion rests on. Lunit converts the geometry; the run case does not
    // go through it, so the velocity is in m/s and the same number means the same speed.
    check("the run-case velocity is in m/s, not in Lunit per Tunit",
      inMetres.length == sameNumber.length &&
        inMetres.zip(sameNumber).forall { case (a, b) => math.abs(a - b) < 1e-4 })
    check("and a hundred times the number is a hundred times the speed, not the same aeroplane",
      hundredTimes.map(math.abs).max > 10.0 * inMetres.map(math.abs).max)
    // And Lunit really is being applied: without it the centimetre aircraft would be a hundred metres
    // across and its motions far slower, not identical.
    check("the geometry, meanwhile, is converted by Lunit",
      sameNumber.map(math.abs).max > 0.5 * inMetres.map(math.abs).max)
  }

  def main(args: Array[String]): Unit = {
    theEditorsArithmetic()

    val props = new Properties()
    if (AvlManager.ensureAvlAvailable(props)) whatAvlDoesWithIt(props.getProperty("avl.path"))
    else println("  AVL is not available here; what AVL does with the velocity needs it")

    println(if (ok) "ANALYSIS_UNITS_OK" else "ANALYSIS_UNITS_FAIL")
    if (!ok) sys.exit(1)
  }
}
