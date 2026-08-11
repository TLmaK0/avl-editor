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
import java.util.logging.{Level, Logger}

/**
 * Finds JSBSim, and installs it into the user's own directory when it is not there — the same
 * bargain [[AvlManager]] and [[XfoilManager]] already make for AVL and XFOIL, and for the same
 * reason: a tool this project drives has to arrive with the project rather than with a machine.
 *
 * **Nothing here touches the system**, and that is the whole point. It was learned the expensive
 * way: JSBSim was once put on a machine by hand with
 * `sudo ln -sf <a session scratch directory>/bin/jsbsim /usr/local/bin/JSBSim`. That worked on that
 * machine, that day. It did not travel with the repository, it did not exist on the CI runner, and
 * it pointed inside `/tmp` — so the day that directory is cleaned the link dangles, the checks that
 * fly the exported aircraft stop finding JSBSim, **excuse themselves and exit 0**, and the suite
 * reports green having measured nothing. An arrangement made on a host is invisible, does not
 * travel, and expires without saying so.
 *
 * So: no `sudo`, nothing under `/usr`, `/etc` or `/opt`, no symlink, no system packages. The
 * install goes under `~/.avleditor`, beside the AVL and XFOIL this project already keeps there.
 */
object JsbsimManager {

  private val logger = Logger.getLogger(getClass.getName)

  /** Beside `~/.avleditor/avl` and the XFOIL cache: one place for the tools this project drives. */
  val JSBSIM_DIR: String = System.getProperty("user.home") + "/.avleditor/jsbsim"

  private def executable(path: String): Option[String] = {
    val f = new File(path)
    if (f.isFile && f.canExecute) Some(f.getPath) else None
  }

  /** Where our own install puts the binary. */
  private def installed: Option[String] = executable(s"$JSBSIM_DIR/bin/jsbsim")

  /**
   * A JSBSim already on the machine, whoever put it there: the user's own `~/.local/bin`, anything
   * the PATH offers under either spelling, and the fixed places a Debian or Ubuntu package uses.
   * Both spellings matter — `pip` installs `jsbsim`, the Debian package installs `JSBSim`.
   */
  private def alreadyPresent: Option[String] = {
    val pathDirs = Option(System.getenv("PATH")).getOrElse("")
      .split(File.pathSeparator).toSeq.filter(_.nonEmpty)
    val homeBin = System.getProperty("user.home") + "/.local/bin"
    val candidates =
      (for {
        dir <- homeBin +: pathDirs
        name <- Seq("jsbsim", "JSBSim")
      } yield new File(dir, name).getPath) ++
        Seq("/usr/games/JSBSim", "/usr/bin/JSBSim", "/usr/local/bin/JSBSim", "/usr/bin/jsbsim")
    candidates.flatMap(executable).headOption
  }

  /**
   * JSBSim's path, installing it first when the machine has none.
   *
   * `None` means it could not be made available — no Python, or no network — and the caller must
   * say so rather than carry on: a check that cannot run JSBSim has measured nothing about the
   * exported aircraft, and reporting that as success is the failure this whole object exists to
   * prevent.
   */
  def ensureJsbsimAvailable(): Option[String] =
    installed.orElse(alreadyPresent).orElse(install())

  /**
   * Installs JSBSim into `~/.avleditor/jsbsim`, in a virtual environment of its own.
   *
   * A virtual environment rather than `pip install --user`, and the reason is not preference: a
   * current Debian or Ubuntu marks the system Python as externally managed (PEP 668), so
   * `pip install --user` is refused outright, and the flag that overrides it is called
   * `--break-system-packages`. A virtual environment under this project's own directory needs no
   * override, cannot disturb the machine's Python, and is removed by deleting one directory.
   */
  private def install(): Option[String] = {
    val python = Seq("python3", "python").find(runs(_, "--version"))
    if (python.isEmpty) {
      logger.log(Level.WARNING, "JSBSim is not installed and no python3 was found to install it with")
      return None
    }
    logger.log(Level.INFO, s"JSBSim not found; installing it into $JSBSIM_DIR")
    val venv = new File(JSBSIM_DIR)
    val created = run(Seq(python.get, "-m", "venv", venv.getPath))
    if (!created) {
      logger.log(Level.WARNING, s"Could not create a virtual environment at $JSBSIM_DIR")
      return None
    }
    val pip = new File(venv, "bin/pip")
    val pipPath = if (pip.exists) pip.getPath else new File(venv, "Scripts/pip.exe").getPath
    if (!run(Seq(pipPath, "install", "--quiet", "jsbsim"))) {
      logger.log(Level.WARNING, "pip could not install jsbsim (no network?)")
      return None
    }
    val where = installed.orElse(executable(s"$JSBSIM_DIR/Scripts/jsbsim.exe"))
    where.foreach(p => logger.log(Level.INFO, s"JSBSim installed at $p"))
    where
  }

  private def runs(command: String, arg: String): Boolean = run(Seq(command, arg))

  private def run(command: Seq[String]): Boolean =
    try {
      val pb = new ProcessBuilder(command: _*)
      pb.redirectErrorStream(true)
      val process = pb.start()
      // Read the output, or a chatty command fills the pipe and blocks for ever waiting for us.
      scala.io.Source.fromInputStream(process.getInputStream).mkString
      process.waitFor() == 0
    } catch {
      case _: Exception => false
    }
}
