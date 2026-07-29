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
        logger.log(Level.INFO, s"XFOIL already configured at: $path")
        true
      case _ =>
        logger.log(Level.INFO, "XFOIL not found or not configured, attempting to download...")
        downloadAndConfigureXfoil(configuration)
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
