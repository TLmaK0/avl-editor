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

import java.io.File
import java.util.Properties
import java.util.logging.{Level, Logger}

/**
 * Locates the FlightGear executable (`fgfs`) and remembers it in the configuration,
 * mirroring how [[AvlManager]] handles AVL. Unlike AVL, FlightGear is a large
 * end-user install and is NOT auto-downloaded: if it can't be found the caller
 * should prompt the user to point at it (and the chosen path is then persisted).
 */
object FlightGearManager {

  val logger = Logger.getLogger(FlightGearManager.getClass.getName)

  /** Configuration key under which the resolved `fgfs` path is stored. */
  val CONFIG_KEY = "flightgear.path"

  private def isWindows = System.getProperty("os.name").toLowerCase.contains("win")
  private def exeName = if (isWindows) "fgfs.exe" else "fgfs"

  /**
   * Return a usable `fgfs` path: the configured one if still valid, otherwise search
   * PATH and the platform's common install locations. A newly discovered path is
   * written back into `configuration` so it is remembered.
   */
  def findExecutable(configuration: Properties): Option[String] = {
    val configured = Option(configuration.getProperty(CONFIG_KEY)).filter(isRunnable)
    configured.orElse {
      val discovered = searchOnPath.orElse(commonLocations.find(isRunnable))
      discovered.foreach { p =>
        configuration.setProperty(CONFIG_KEY, p)
        logger.log(Level.INFO, s"FlightGear found at: $p")
      }
      if (discovered.isEmpty) logger.log(Level.INFO, "FlightGear (fgfs) not found automatically")
      discovered
    }
  }

  /** Persist a user-chosen path (from a file dialog). */
  def setExecutable(configuration: Properties, path: String): Unit =
    configuration.setProperty(CONFIG_KEY, path)

  private def isRunnable(path: String): Boolean = {
    val f = new File(path)
    f.exists && f.canExecute
  }

  private def searchOnPath: Option[String] =
    Option(System.getenv("PATH")).toSeq
      .flatMap(_.split(File.pathSeparator))
      .map(dir => new File(dir, exeName))
      .find(f => f.exists && f.canExecute)
      .map(_.getAbsolutePath)

  private def commonLocations: Seq[String] = {
    val os = System.getProperty("os.name").toLowerCase
    if (os.contains("mac") || os.contains("darwin"))
      Seq("/Applications/FlightGear.app/Contents/MacOS/fgfs")
    else if (isWindows)
      Seq(
        "C:\\Program Files\\FlightGear\\bin\\fgfs.exe",
        "C:\\Program Files\\FlightGear\\bin\\Win64\\fgfs.exe")
    else
      Seq("/usr/bin/fgfs", "/usr/games/fgfs", "/usr/local/bin/fgfs", "/snap/bin/fgfs")
  }
}
