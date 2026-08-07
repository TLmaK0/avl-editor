/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.xfoil

import java.io.{BufferedReader, File, InputStreamReader, OutputStreamWriter}
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import java.util.logging.{Level, Logger}
import scala.collection.mutable.ArrayBuffer

/** One converged point of an XFOIL viscous polar. */
case class XfoilPolarPoint(alpha: Float, cl: Float, cd: Float, cdp: Float, cm: Float)

/** Airfoil input for XFOIL: either a NACA 4/5-digit code or a coordinate file. */
sealed trait XfoilAirfoil
case class NacaAirfoil(code: String) extends XfoilAirfoil
case class DatAirfoil(path: String) extends XfoilAirfoil

/**
 * Drives XFOIL as an external process to compute a viscous polar, mirroring how
 * [[com.abajar.avleditor.avl.connectivity.AvlRunner]] drives AVL. Graphics are
 * disabled and results are read from the polar accumulation (PACC) file.
 *
 * Requires a stable XFOIL binary (see XfoilManager / the xfoil-binaries CI); the
 * stock Debian build crashes and is not usable.
 */
class XfoilRunner(xfoilPath: String) {

  private val logger = Logger.getLogger(classOf[XfoilRunner].getName)

  /**
   * Compute a viscous polar sweep. Non-converged points near stall simply do not
   * appear in the result. Returns points ordered by alpha.
   */
  def computePolar(
      airfoil: XfoilAirfoil,
      reynolds: Double,
      mach: Double,
      alphaStart: Double,
      alphaEnd: Double,
      alphaStep: Double,
      iterations: Int = 200,
      timeoutSeconds: Int = 120
  ): Seq[XfoilPolarPoint] = {
    val workDir = Files.createTempDirectory("xfoil_run")
    val polarFile = workDir.resolve("polar.txt")
    try {
      val script = buildScript(airfoil, reynolds, mach, alphaStart, alphaEnd, alphaStep,
        iterations, polarFile)
      runProcess(script, workDir, timeoutSeconds)
      parsePolar(polarFile)
    } finally {
      deleteRecursively(workDir.toFile)
    }
  }

  private def buildScript(
      airfoil: XfoilAirfoil, reynolds: Double, mach: Double,
      alphaStart: Double, alphaEnd: Double, alphaStep: Double,
      iterations: Int, polarFile: Path
  ): String = {
    val load = airfoil match {
      case NacaAirfoil(code) => s"NACA $code"
      case DatAirfoil(path)  => s"LOAD $path"
    }
    val sb = new StringBuilder
    sb.append("PLOP\n").append("G\n").append("\n")   // disable graphics
    sb.append(load).append("\n")
    sb.append("PANE\n")                               // clean paneling
    sb.append("OPER\n")
    sb.append(s"ITER $iterations\n")
    sb.append(f"VISC $reynolds%.1f\n")                // set Reynolds + enable viscous
    if (mach > 0.0) sb.append(f"MACH $mach%.4f\n")
    sb.append("PACC\n")                               // start polar accumulation
    sb.append(polarFile.toString).append("\n")        // save file
    sb.append("\n")                                   // no dump file
    // A sweep that spans zero is run outwards from zero in two halves, with the boundary layer
    // reinitialised between them. XFOIL carries the previous point's solution into the next one, which is
    // what lets it converge at all near the stall — and equally what makes one failed point poison every
    // point after it. Marching from -8 straight through to +20 puts the hardest attitudes at the far end
    // of a chain of them; marching outwards from where the aerofoil is easiest puts each stall at the end
    // of its own short chain. The polar accumulates across both, so the file is the same one either way.
    if (alphaStart < 0.0 && alphaEnd > 0.0) {
      sb.append(f"ASEQ 0.000 $alphaEnd%.3f ${math.abs(alphaStep)}%.3f\n")
      sb.append("INIT\n")
      sb.append(f"ASEQ 0.000 $alphaStart%.3f ${-math.abs(alphaStep)}%.3f\n")
    } else {
      sb.append(f"ASEQ $alphaStart%.3f $alphaEnd%.3f $alphaStep%.3f\n")
    }
    sb.append("PACC\n")                               // flush + stop accumulation
    sb.append("\n")
    sb.append("QUIT\n")
    sb.toString
  }

  private def runProcess(script: String, workDir: Path, timeoutSeconds: Int): Unit = {
    val pb = new ProcessBuilder(xfoilPath)
    pb.directory(workDir.toFile)
    pb.redirectErrorStream(true)
    val process = pb.start()

    // Drain stdout in a separate thread so XFOIL never blocks on a full pipe.
    val drainer = new Thread(new Runnable {
      override def run(): Unit = {
        val reader = new BufferedReader(new InputStreamReader(process.getInputStream))
        try { while (reader.readLine() != null) {} }
        catch { case _: Exception => }
        finally { try { reader.close() } catch { case _: Exception => } }
      }
    })
    drainer.setDaemon(true)
    drainer.start()

    val writer = new OutputStreamWriter(process.getOutputStream)
    try {
      writer.write(script)
      writer.flush()
    } catch {
      case _: Exception => // process may have exited; polar file is what matters
    } finally {
      try { writer.close() } catch { case _: Exception => }
    }

    val finished = process.waitFor(timeoutSeconds.toLong, TimeUnit.SECONDS)
    if (!finished) {
      logger.log(Level.WARNING, s"XFOIL timed out after ${timeoutSeconds}s; destroying")
      process.destroyForcibly()
    }
    drainer.join(2000)
  }

  private def parsePolar(polarFile: Path): Seq[XfoilPolarPoint] = {
    if (!Files.exists(polarFile)) {
      logger.log(Level.WARNING, s"XFOIL polar file not produced: $polarFile")
      return Seq.empty
    }
    val points = new ArrayBuffer[XfoilPolarPoint]()
    val lines = Files.readAllLines(polarFile)
    var i = 0
    var inData = false
    while (i < lines.size) {
      val line = lines.get(i).trim
      if (!inData) {
        // Data begins after the dashed separator line under the column header.
        if (line.startsWith("---")) inData = true
      } else if (line.nonEmpty) {
        val cols = line.split("\\s+")
        if (cols.length >= 5) {
          try {
            points += XfoilPolarPoint(
              cols(0).toFloat, cols(1).toFloat, cols(2).toFloat, cols(3).toFloat, cols(4).toFloat)
          } catch {
            case _: NumberFormatException => // skip malformed line
          }
        }
      }
      i += 1
    }
    points.sortBy(_.alpha).toSeq
  }

  private def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) Option(file.listFiles).foreach(_.foreach(deleteRecursively))
    file.delete()
  }
}
