/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.tools

import com.abajar.avleditor.avl.AVL
import com.abajar.avleditor.avl.AVLGeometry
import com.abajar.avleditor.avl.connectivity.AvlRunner
import com.abajar.avleditor.avl.mass.Mass
import com.abajar.avleditor.avl.runcase.MilF8785cEvaluator
import com.abajar.avleditor.avl.runcase.ModalNormRow
import com.abajar.avleditor.crrcsim.CRRCSimRepository
import java.io.File
import java.io.FileInputStream
import java.io.PrintWriter
import java.nio.file.Path
import java.util.Properties
import scala.collection.JavaConverters._
import scala.util.Try

object EnvelopeSweepTool {
  private val Gravity = 9.80665
  private val DefaultDensity = 1.225
  private val DefaultSpeeds = List(15.0, 20.0, 25.0, 30.0)
  private val NormalizedControlSaturationThreshold = 1.0
  private val DegreesToRadians = math.Pi / 180.0
  private val RecoveryAlphaStepRad = 2.0 * DegreesToRadians
  private val RecoveryBetaStepRad = 5.0 * DegreesToRadians
  private val RecoveryPPrimeStep = 0.05
  private val RecoveryQPrimeStep = 0.03
  private val RecoveryRPrimeStep = 0.05
  private val DerivativeTolerance = 1.0e-6

  private case class TrimAuthority(
    controlName: String,
    maxAbsCommand: Double,
    maxAbsDeflectionDeg: Double,
    likelySaturated: Boolean
  )

  private case class AxisRecovery(
    controlName: String,
    requiredAbsCommand: Double,
    availableAbsMargin: Double,
    sufficient: Option[Boolean]
  )

  private case class RecoveryAuthority(
    pitch: AxisRecovery,
    roll: AxisRecovery,
    yaw: AxisRecovery
  )

  private case class Scenario(
    massScale: Double,
    cgxOffsetMeters: Double
  )

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) {
      printUsage()
      sys.exit(1)
    }

    val modelFile = new File(args(0))
    if (!modelFile.exists() || !modelFile.isFile) {
      System.err.println("Model file does not exist: " + modelFile.getAbsolutePath)
      sys.exit(1)
    }

    val speeds = parseSpeeds(args.lift(1).getOrElse(""))
    if (speeds.isEmpty) {
      System.err.println("Invalid speed list. Use comma-separated positive numbers, e.g. 15,20,25,30")
      sys.exit(1)
    }

    val density = parseDensity(args.lift(2).getOrElse(""))
    val avlPath = resolveAvlPath(args.lift(3))
    val outputCsv = args.lift(4).map(_.trim).filter(_.nonEmpty)
    val massScales = parseMassScales(args.lift(5).getOrElse(""))
    if (massScales.isEmpty) {
      System.err.println("Invalid mass scale list. Use comma-separated positive numbers, e.g. 1.0,0.95,1.05")
      sys.exit(1)
    }
    val cgxOffsets = parseCgxOffsets(args.lift(6).getOrElse(""))
    if (cgxOffsets.isEmpty) {
      System.err.println("Invalid CGx offset list. Use comma-separated numbers in meters, e.g. -0.02,0,0.02")
      sys.exit(1)
    }
    val scenarios = (for {
      massScale <- massScales
      cgxOffset <- cgxOffsets
    } yield Scenario(massScale, cgxOffset)).distinct
    if (avlPath.isEmpty) {
      System.err.println("AVL path is not configured. Pass it as the 4th argument or set avl.path in ~/.avleditor/configuration.xml")
      sys.exit(1)
    }

    val crrcsim = new CRRCSimRepository().restoreFromFile(modelFile)
    val avl = crrcsim.getAvl
    val geometry = avl.getGeometry
    val validationErrors = geometry.validate().asScala.toList
    if (validationErrors.nonEmpty) {
      System.err.println("Geometry validation failed:")
      validationErrors.foreach(error => System.err.println(" - " + error))
      sys.exit(1)
    }

    val masses = geometry.getMassesRecursive.asScala.toList
    val baselineMasses = masses.map(_.getMass.toDouble)
    val baselineMassKg = baselineMasses.sum
    if (baselineMassKg <= 0.0) {
      System.err.println("Total mass is zero. Define masses first.")
      sys.exit(1)
    }
    val baselineXref = geometry.getXref.toDouble

    val sref = geometry.getSref.toDouble
    if (sref <= 0.0) {
      System.err.println("Sref is zero after geometry validation.")
      sys.exit(1)
    }

    val originPath = Option(modelFile.getParentFile).map(_.toPath).getOrElse(new File(".").toPath)
    val originalVelocity = avl.getVelocity
    val originalCl = avl.getLiftCoefficient

    val outputLines = scala.collection.mutable.ArrayBuffer[String]()
    val header = "mass_scale,cgx_offset_m,V_mps,CL_target,alpha_deg,Cma,Cnb,Cnr,unstable_modes,max_sigma,short_period,dutch_roll,phugoid,trim_control,trim_max_abs_cmd,trim_max_abs_deflection_deg,trim_authority,recovery_pitch_cmd,recovery_roll_cmd,recovery_yaw_cmd,recovery_margin_min,recovery_authority"
    outputLines += header
    println(header)

    try {
      scenarios.foreach { scenario =>
        applyScenario(geometry, masses, baselineMasses, scenario.massScale, baselineXref + scenario.cgxOffsetMeters)
        val totalMassKg = masses.map(_.getMass.toDouble).sum

        speeds.foreach { speed =>
          val clTarget = levelFlightCl(totalMassKg, density, speed, sref)
          avl.setVelocity(speed.toFloat)
          avl.setLiftCoefficient(clTarget.toFloat)

          val row = runPoint(avlPath.get, avl, originPath, speed, clTarget)
          val rowWithScenario = f"${scenario.massScale}%.3f,${scenario.cgxOffsetMeters}%.4f,$row"
          outputLines += rowWithScenario
          println(rowWithScenario)
        }
      }
    } finally {
      avl.setVelocity(originalVelocity)
      avl.setLiftCoefficient(originalCl)
      applyScenario(geometry, masses, baselineMasses, 1.0, baselineXref)
    }

    outputCsv.foreach(path => writeCsv(path, outputLines.toList))
  }

  private def runPoint(
      avlPath: String,
      avl: AVL,
      originPath: Path,
      speed: Double,
      clTarget: Double
  ): String = {
    try {
      val runner = new AvlRunner(avlPath, avl, originPath)
      val calc = runner.getCalculation
      val milRows = MilF8785cEvaluator.evaluate(calc)
      val shortResult = modeResult(milRows, "Short-period")
      val dutchResult = modeResult(milRows, "Dutch-roll")
      val phugoidResult = modeResult(milRows, "Phugoid")

      val eigenvalues = calc.getEigenvalues.asScala.toList
      val unstableModes = eigenvalues.count(_.getSigma > 0.0f)
      val maxSigma = if (eigenvalues.nonEmpty) eigenvalues.map(_.getSigma.toDouble).max else Double.NaN

      val stab = calc.getStabilityDerivatives
      val config = calc.getConfiguration
      val trimAuthority = evaluateTrimAuthority(calc)
      val trimAuthorityResult =
        if (trimAuthority.controlName == "N/A") "N/A"
        else if (trimAuthority.likelySaturated) "LIKELY_SATURATION"
        else "OK"

      val trimMaxCmd = formatOptionalDouble(trimAuthority.maxAbsCommand)
      val trimMaxDeflection = formatOptionalDouble(trimAuthority.maxAbsDeflectionDeg)
      val recoveryAuthority = evaluateRecoveryAuthority(calc)
      val recoveryMinMargin = formatOptionalDouble(minAvailableMargin(recoveryAuthority))
      val recoveryStatus = recoveryAuthorityStatus(recoveryAuthority)
      val recoveryPitch = formatOptionalDouble(recoveryAuthority.pitch.requiredAbsCommand)
      val recoveryRoll = formatOptionalDouble(recoveryAuthority.roll.requiredAbsCommand)
      val recoveryYaw = formatOptionalDouble(recoveryAuthority.yaw.requiredAbsCommand)

      f"$speed%.3f,$clTarget%.6f,${config.getAlpha}%.6f,${stab.getCma}%.6f,${stab.getCnb}%.6f,${stab.getCnr}%.6f,$unstableModes,$maxSigma%.6f,$shortResult,$dutchResult,$phugoidResult,${trimAuthority.controlName},$trimMaxCmd,$trimMaxDeflection,$trimAuthorityResult,$recoveryPitch,$recoveryRoll,$recoveryYaw,$recoveryMinMargin,$recoveryStatus"
    } catch {
      case ex: Exception =>
        val message = sanitizeCsv(ex.getMessage)
        f"$speed%.3f,$clTarget%.6f,,,,,,,ERROR:$message,ERROR,ERROR,,,,ERROR,,,,,ERROR"
    }
  }

  private def evaluateTrimAuthority(calc: com.abajar.avleditor.avl.runcase.AvlCalculation): TrimAuthority = {
    val names = Option(calc.getControlNames).map(_.toList).getOrElse(Nil)
    val commands = Option(calc.getTrimControlValues).map(_.toList).getOrElse(Nil)
    val deflections = Option(calc.getTrimControlDeflections).map(_.toList).getOrElse(Nil)
    val maxSize = Seq(names.size, commands.size, deflections.size).min

    var bestControl = "N/A"
    var maxAbsCommand = Double.NaN
    var maxAbsDeflection = Double.NaN

    for (i <- 0 until maxSize) {
      val command = commands(i).toDouble
      if (isFinite(command)) {
        val absCommand = math.abs(command)
        if (!isFinite(maxAbsCommand) || absCommand > maxAbsCommand) {
          bestControl = names(i)
          maxAbsCommand = absCommand
          val deflection = deflections(i).toDouble
          maxAbsDeflection = if (isFinite(deflection)) math.abs(deflection) else Double.NaN
        }
      }
    }

    val likelySaturated = isFinite(maxAbsCommand) && maxAbsCommand > NormalizedControlSaturationThreshold
    TrimAuthority(bestControl, maxAbsCommand, maxAbsDeflection, likelySaturated)
  }

  private def evaluateRecoveryAuthority(calc: com.abajar.avleditor.avl.runcase.AvlCalculation): RecoveryAuthority = {
    val stab = calc.getStabilityDerivatives
    val trimCommands = Option(calc.getTrimControlValues).map(_.toList).getOrElse(Nil)

    def trimMargin(index: Int): Double = {
      if (index < 0 || index >= trimCommands.length) Double.NaN
      else {
        val command = trimCommands(index).toDouble
        if (!isFinite(command)) Double.NaN
        else math.max(0.0, 1.0 - math.abs(command))
      }
    }

    def axisRecovery(index: Int, controlName: String, destabilizingMoment: Double, controlDerivative: Double): AxisRecovery = {
      if (index < 0 || !isFinite(controlDerivative) || math.abs(controlDerivative) < DerivativeTolerance) {
        AxisRecovery(controlName, Double.NaN, Double.NaN, None)
      } else {
        val required =
          if (isFinite(destabilizingMoment)) math.abs(destabilizingMoment) / math.abs(controlDerivative)
          else Double.NaN
        val margin = trimMargin(index)
        val sufficient =
          if (isFinite(required) && isFinite(margin)) Some(required <= margin)
          else None
        AxisRecovery(controlName, required, margin, sufficient)
      }
    }

    val pitchIndex = calc.getElevatorPosition
    val rollIndex = calc.getAileronPosition
    val yawIndex = calc.getRudderPosition
    val names = Option(calc.getControlNames).map(_.toList).getOrElse(Nil)

    def controlNameAt(index: Int, fallback: String): String = {
      if (index >= 0 && index < names.length) names(index) else fallback
    }

    val pitchDestabilizing =
      destabilizingComponent(stab.getCma, RecoveryAlphaStepRad, stableWhenDerivativeNegative = true) +
      destabilizingComponent(stab.getCmq, RecoveryQPrimeStep, stableWhenDerivativeNegative = true)
    val rollDestabilizing =
      destabilizingComponent(stab.getClb, RecoveryBetaStepRad, stableWhenDerivativeNegative = true) +
      destabilizingComponent(stab.getClp, RecoveryPPrimeStep, stableWhenDerivativeNegative = true)
    val yawDestabilizing =
      destabilizingComponent(stab.getCnb, RecoveryBetaStepRad, stableWhenDerivativeNegative = false) +
      destabilizingComponent(stab.getCnr, RecoveryRPrimeStep, stableWhenDerivativeNegative = true)

    val pitchDerivative = derivativeAt(stab.getCmd, pitchIndex)
    val rollDerivative = derivativeAt(stab.getCld, rollIndex)
    val yawDerivative = derivativeAt(stab.getCnd, yawIndex)

    val pitch = axisRecovery(pitchIndex, controlNameAt(pitchIndex, "pitch_ctrl"), pitchDestabilizing, pitchDerivative)
    val roll = axisRecovery(rollIndex, controlNameAt(rollIndex, "roll_ctrl"), rollDestabilizing, rollDerivative)
    val yaw = axisRecovery(yawIndex, controlNameAt(yawIndex, "yaw_ctrl"), yawDestabilizing, yawDerivative)

    RecoveryAuthority(pitch, roll, yaw)
  }

  private def derivativeAt(values: Array[Float], index: Int): Double = {
    if (values == null || index < 0 || index >= values.length) Double.NaN
    else values(index).toDouble
  }

  private def destabilizingComponent(
      derivative: Double,
      perturbation: Double,
      stableWhenDerivativeNegative: Boolean
  ): Double = {
    if (!isFinite(derivative) || !isFinite(perturbation) || perturbation <= 0.0) {
      0.0
    } else {
      val tendency = derivative * perturbation
      if (stableWhenDerivativeNegative) {
        math.max(0.0, tendency)
      } else {
        math.max(0.0, -tendency)
      }
    }
  }

  private def minAvailableMargin(recovery: RecoveryAuthority): Double = {
    val margins = List(
      recovery.pitch.availableAbsMargin,
      recovery.roll.availableAbsMargin,
      recovery.yaw.availableAbsMargin
    ).filter(isFinite)
    if (margins.nonEmpty) margins.min else Double.NaN
  }

  private def recoveryAuthorityStatus(recovery: RecoveryAuthority): String = {
    val sufficiencies = List(recovery.pitch.sufficient, recovery.roll.sufficient, recovery.yaw.sufficient).flatten
    if (sufficiencies.isEmpty) {
      "N/A"
    } else if (sufficiencies.forall(identity)) {
      "OK"
    } else {
      "INSUFFICIENT"
    }
  }

  private def modeResult(rows: List[ModalNormRow], modeName: String): String = {
    rows.find(_.modeName == modeName) match {
      case Some(row) =>
        row.pass match {
          case Some(true) => "PASS"
          case Some(false) => "FAIL"
          case None => "N/A"
        }
      case None => "N/A"
    }
  }

  private def levelFlightCl(totalMassKg: Double, density: Double, speed: Double, sref: Double): Double = {
    (2.0 * totalMassKg * Gravity) / (density * speed * speed * sref)
  }

  private def parseSpeeds(raw: String): List[Double] = {
    if (raw.trim.isEmpty) {
      DefaultSpeeds
    } else {
      raw.split(",").toList
        .map(_.trim)
        .filter(_.nonEmpty)
        .flatMap(value => Try(value.toDouble).toOption)
        .filter(_ > 0.0)
    }
  }

  private def parseDensity(raw: String): Double = {
    if (raw.trim.isEmpty) DefaultDensity
    else Try(raw.toDouble).toOption.filter(_ > 0.0).getOrElse(DefaultDensity)
  }

  private def parseMassScales(raw: String): List[Double] = {
    parseCsvDoubles(raw, _ > 0.0, List(1.0))
  }

  private def parseCgxOffsets(raw: String): List[Double] = {
    parseCsvDoubles(raw, _ => true, List(0.0))
  }

  private def parseCsvDoubles(raw: String, predicate: Double => Boolean, defaults: List[Double]): List[Double] = {
    if (raw.trim.isEmpty) {
      defaults
    } else {
      raw.split(",").toList
        .map(_.trim)
        .filter(_.nonEmpty)
        .flatMap(value => Try(value.toDouble).toOption)
        .filter(predicate)
    }
  }

  private def applyScenario(
      geometry: AVLGeometry,
      masses: List[Mass],
      baselineMasses: List[Double],
      massScale: Double,
      xref: Double
  ): Unit = {
    masses.zip(baselineMasses).foreach { case (mass, baseline) =>
      mass.setMass((baseline * massScale).toFloat)
    }
    geometry.setXref(xref.toFloat)
  }

  private def resolveAvlPath(explicitPath: Option[String]): Option[String] = {
    explicitPath.map(_.trim).filter(_.nonEmpty).filter(path => isUsableExecutable(path))
      .orElse(loadConfiguredAvlPath())
  }

  private def loadConfiguredAvlPath(): Option[String] = {
    val configPath = new File(System.getProperty("user.home") + "/.avleditor/configuration.xml")
    if (!configPath.exists() || !configPath.isFile) {
      return None
    }

    val properties = new Properties()
    val input = new FileInputStream(configPath)
    try {
      properties.loadFromXML(input)
      Option(properties.getProperty("avl.path"))
        .map(_.trim)
        .filter(_.nonEmpty)
        .filter(path => isUsableExecutable(path))
    } catch {
      case _: Exception => None
    } finally {
      input.close()
    }
  }

  private def sanitizeCsv(raw: String): String = {
    Option(raw).getOrElse("unknown_error").replace(",", ";")
  }

  private def formatOptionalDouble(value: Double): String = {
    if (isFinite(value)) f"$value%.6f" else ""
  }

  private def isFinite(value: Double): Boolean = {
    !java.lang.Double.isNaN(value) && !java.lang.Double.isInfinite(value)
  }

  private def isUsableExecutable(path: String): Boolean = {
    val file = new File(path)
    file.exists() && file.isFile && file.canExecute
  }

  private def printUsage(): Unit = {
    println("Usage: sbt \"runMain com.abajar.avleditor.tools.EnvelopeSweepTool <model.avle> [speeds_csv] [rho] [avl_path] [output_csv] [mass_scales_csv] [cgx_offsets_csv_m]\"")
    println("Example:")
    println("  sbt \"runMain com.abajar.avleditor.tools.EnvelopeSweepTool samples/eurofighter/eurofighter.avle 15,20,25,30 1.225 ~/.avleditor/avl/avl envelope.csv 0.95,1.0,1.05 -0.02,0,0.02\"")
  }

  private def writeCsv(path: String, lines: List[String]): Unit = {
    val file = new File(path)
    val parent = file.getAbsoluteFile.getParentFile
    if (parent != null && !parent.exists()) {
      parent.mkdirs()
    }

    val writer = new PrintWriter(file)
    try {
      lines.foreach(writer.println)
    } finally {
      writer.close()
    }
  }
}
