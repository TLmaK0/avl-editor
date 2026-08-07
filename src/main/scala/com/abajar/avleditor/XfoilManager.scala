/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor

import java.io.{File, FileOutputStream, InputStream}
import java.net.{HttpURLConnection, URL}
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties
import java.util.logging.{Level, Logger}
import scala.collection.JavaConverters._

/**
 * Resolves and provides the XFOIL executable, mirroring [[AvlManager]].
 *
 * XFOIL is not distributed as a reliable per-OS binary upstream (the Debian
 * package crashes; the MIT site ships Windows-only). We compile it from source
 * in CI (see .github/workflows/xfoil-binaries.yml) and publish the binaries to a
 * fixed GitHub release; this manager downloads and caches them like AvlManager
 * does for AVL. Until that release exists, the user can point `xfoil.path` at a
 * local build via the "Set XFOIL executable" menu.
 */
object XfoilManager {

  val logger = Logger.getLogger(XfoilManager.getClass.getName)

  // Fixed release produced by the xfoil-binaries workflow.
  val XFOIL_RELEASE_BASE =
    "https://github.com/TLmaK0/avl-editor/releases/download/xfoil-6.99/"
  val XFOIL_DIR = System.getProperty("user.home") + "/.avleditor/xfoil"

  case class XfoilBinary(fileName: String, assetName: String)

  def getXfoilBinaryForOS: Option[XfoilBinary] = {
    val os = System.getProperty("os.name").toLowerCase

    logger.log(Level.INFO, s"Detecting OS for XFOIL: $os")

    if (os.contains("linux")) {
      Some(XfoilBinary("xfoil", "xfoil-linux"))
    } else if (os.contains("mac") || os.contains("darwin")) {
      Some(XfoilBinary("xfoil", "xfoil-macos"))
    } else if (os.contains("win")) {
      Some(XfoilBinary("xfoil.exe", "xfoil-windows.exe"))
    } else {
      logger.log(Level.WARNING, s"Unsupported operating system for XFOIL: $os")
      None
    }
  }

  def getXfoilPath: String = {
    val binary = getXfoilBinaryForOS.getOrElse(throw new Exception("Unsupported OS"))
    s"$XFOIL_DIR/${binary.fileName}"
  }

  def ensureXfoilAvailable(configuration: Properties): Boolean = {
    val configuredPath = Option(configuration.getProperty("xfoil.path"))

    configuredPath match {
      case Some(path) if new File(path).exists() && new File(path).canExecute() =>
        // Configured is not the same as working, and the difference is silent — see `usable`.
        runs(path) match {
          case Right(_) =>
            logger.log(Level.INFO, s"XFOIL already configured at: $path")
            true
          case Left(why) =>
            logger.log(Level.WARNING, why + " No stall speed will be measured until it does.")
            false
        }
      case _ =>
        logger.log(Level.INFO, "XFOIL not found or not configured, attempting to download...")
        downloadAndConfigureXfoil(configuration)
    }
  }

  /**
   * The path to an XFOIL that **actually runs on this machine**, or the reason there is not one.
   *
   * A file that exists and has its executable bit set is not an XFOIL that works, and the difference is
   * not academic: the published Linux binary is built by CI on whatever `ubuntu-latest` currently is, so
   * it can need a newer C library than the machine running the editor has. It then fails at the loader —
   * `version GLIBC_2.38 not found` — before printing a word of its own, and every polar comes back empty.
   * Read through the analysis that is exactly what a stall it could not converge looks like, so the user
   * would be told their aerofoil never stalls rather than that their XFOIL never started.
   *
   * So it is asked to do the smallest real piece of work there is — build a NACA 0012 and report the
   * buffer aerofoil it made of it — and the answer is remembered per path, because it cannot change while
   * the editor is running and it costs a process.
   */
  def usable(configuration: Properties): Either[String, String] = {
    val configured = Option(configuration.getProperty("xfoil.path")).map(_.trim).filter(_.nonEmpty)
    configured match {
      case None =>
        Left("XFOIL is not configured. It is what says where an aerofoil stops lifting — AVL is inviscid " +
          "and cannot see a stall at all. Set it under 'Set XFOIL executable', or let the editor download " +
          "it on the next start.")
      case Some(path) =>
        val file = new File(path)
        if (!file.exists) Left(s"XFOIL is configured at '$path', and there is nothing there.")
        else if (!file.canExecute) Left(s"XFOIL at '$path' is not executable.")
        else runs(path).right.map(_ => path)
    }
  }

  private val verified = scala.collection.mutable.Map[String, Either[String, Unit]]()

  private def runs(path: String): Either[String, Unit] = verified.synchronized {
    verified.getOrElseUpdate(path, smokeTest(path))
  }

  private def smokeTest(path: String): Either[String, Unit] = {
    try {
      val builder = new ProcessBuilder(path)
      builder.redirectErrorStream(true)
      val process = builder.start()
      val writer = new java.io.OutputStreamWriter(process.getOutputStream)
      try {
        writer.write("PLOP\nG\n\nNACA 0012\nQUIT\n")
        writer.flush()
      } catch { case _: Exception => /* it died before reading; the output says why */ }
      finally { try { writer.close() } catch { case _: Exception => } }

      val output = new StringBuilder
      val reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream))
      try {
        var line = reader.readLine()
        while (line != null) { output.append(line).append('\n'); line = reader.readLine() }
      } finally { try { reader.close() } catch { case _: Exception => } }

      if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return Left(s"XFOIL at '$path' did not answer within 30 seconds of being asked to load a NACA 0012.")
      }
      val said = output.toString
      // "Buffer airfoil set using 245 points" is XFOIL having built the section. Nothing short of that
      // proves it ran: a banner can come from anything, and a loader failure prints no banner at all.
      if (said.contains("Buffer airfoil set") && said.contains("Max thickness")) Right(())
      else Left(s"XFOIL at '$path' did not load a NACA 0012. It said: " +
        said.split("\n").filter(_.trim.nonEmpty).takeRight(3).mkString(" / ").take(300))
    } catch {
      case ex: Exception => Left(s"XFOIL at '$path' could not be started: ${ex.getMessage}")
    }
  }

  private def downloadAndConfigureXfoil(configuration: Properties): Boolean = {
    getXfoilBinaryForOS match {
      case Some(binary) =>
        try {
          val xfoilDir = new File(XFOIL_DIR)
          if (!xfoilDir.exists()) {
            xfoilDir.mkdirs()
            logger.log(Level.INFO, s"Created XFOIL directory: $XFOIL_DIR")
          }

          val xfoilPath = s"$XFOIL_DIR/${binary.fileName}"
          val xfoilFile = new File(xfoilPath)

          if (!xfoilFile.exists()) {
            val url = XFOIL_RELEASE_BASE + binary.assetName
            logger.log(Level.INFO, s"Downloading XFOIL from: $url")
            downloadFile(url, xfoilFile)
            logger.log(Level.INFO, s"XFOIL downloaded to: $xfoilPath")
          }

          setExecutablePermissions(xfoilFile)

          configuration.setProperty("xfoil.path", xfoilPath)
          logger.log(Level.INFO, s"XFOIL configured at: $xfoilPath")

          true
        } catch {
          case ex: Exception =>
            logger.log(Level.WARNING,
              "Failed to download XFOIL. Set it manually via 'Set XFOIL executable'.", ex)
            false
        }
      case None =>
        logger.log(Level.SEVERE, "Unsupported operating system for XFOIL download")
        false
    }
  }

  private def downloadFile(urlString: String, destinationFile: File): Unit = {
    var connection: HttpURLConnection = null
    var inputStream: InputStream = null
    var outputStream: FileOutputStream = null

    try {
      val url = new URL(urlString)
      connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      connection.setConnectTimeout(10000)
      connection.setReadTimeout(30000)
      connection.setInstanceFollowRedirects(true)

      val responseCode = connection.getResponseCode
      if (responseCode != HttpURLConnection.HTTP_OK) {
        throw new Exception(s"HTTP error code: $responseCode")
      }

      inputStream = connection.getInputStream
      outputStream = new FileOutputStream(destinationFile)

      val buffer = new Array[Byte](8192)
      var bytesRead = 0
      var totalBytesRead = 0L

      while ({bytesRead = inputStream.read(buffer); bytesRead != -1}) {
        outputStream.write(buffer, 0, bytesRead)
        totalBytesRead += bytesRead
      }

      logger.log(Level.INFO, s"Downloaded $totalBytesRead bytes")

    } finally {
      if (outputStream != null) outputStream.close()
      if (inputStream != null) inputStream.close()
      if (connection != null) connection.disconnect()
    }
  }

  private def setExecutablePermissions(file: File): Unit = {
    try {
      val path = file.toPath
      val permissions = Set(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE,
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.GROUP_EXECUTE,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OTHERS_EXECUTE
      )
      Files.setPosixFilePermissions(path, permissions.asJava)
      logger.log(Level.INFO, s"Set executable permissions on: ${file.getAbsolutePath}")
    } catch {
      case ex: UnsupportedOperationException =>
        file.setExecutable(true, false)
        logger.log(Level.INFO, s"Set executable flag on: ${file.getAbsolutePath}")
      case ex: Exception =>
        logger.log(Level.WARNING, "Failed to set executable permissions", ex)
    }
  }
}
