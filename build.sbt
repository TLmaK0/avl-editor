import sbt.ProjectRef

lazy val avlEditor = project
  .in(file("."))

name := "AVL Editor"

version := "1.0.0"

scalaVersion := "2.10.4"

resolvers += "jogamp-remote" at "https://jogamp.org/deployment/maven"

Compile / run / mainClass := Some("com.abajar.avleditor.Main")

enablePlugins(JavaAppPackaging)

maintainer := "Hugo Freire <hfreire@abajar.com>"

ThisBuild / scalacOptions ++= Seq("-feature")

// javacOptions ++= Seq("-Xlint:unchecked")
scalacOptions ++= Seq("-language:postfixOps", "-language:reflectiveCalls")

run / fork := true

run / envVars := Map("SWT_GTK3" -> "0")

// ---------------------------------------------------------------------------------------------
// Running the checks
//
// `sbt test` runs NOTHING in this build, and prints `[success]` while doing it. There is no test
// framework on the classpath — no ScalaTest, no MUnit, not even `junit-interface`, and `@Test`
// appears nowhere — so `Test / definedTests` is empty. Measured: `sbt test` compiles 58 test
// sources and exits 0 in 58 seconds having executed not one check.
//
// That is because a check here is not a framework test. It is an executable `object` with a
// `main` that prints `PASS`/`FAIL` per assertion and calls `sys.exit(1)` if any failed (55 of
// them do), and each file's header says how to run it: `sbt "Test/runMain <class>"`. The shape is
// deliberate — a check drives AVL, XFOIL or JSBSim and reads their output — so this task runs
// them as what they are rather than rewriting sixty files to suit a framework.
//
// `checks` is therefore the real gate, and `sbt test` is not. Anything that wants to know whether
// this project is sound runs `sbt checks`.
// ---------------------------------------------------------------------------------------------

lazy val checks = taskKey[Unit]("Run every executable check; fail if any of them fails.")

// A `main` under src/test that is not a check: it reports and asserts nothing, so it can neither
// pass nor fail. Named here rather than skipped by a naming convention, because a convention is
// how a real check comes to be excluded by being renamed.
lazy val notChecks = Set(
  "com.abajar.avleditor.avl.runcase.ReportInspection"
)

// Checks that need a program which cannot be installed on a build runner. FlightGear is gigabytes
// of scenery and aircraft data, and `FlightGearManagerCheck` asserts that `fgfs` is *found* — so it
// cannot pass where FlightGear is absent, and it is the only check in that position.
//
// It is skipped only when `fgfs` is genuinely not on the machine, and the skip is COUNTED AND
// NAMED in the summary. That distinction is the whole point: this project has been bitten by
// refusals that only reached the log, and a check quietly not running is the same failure — a
// skipped check and a passing one must never read alike.
lazy val checksNeedingFlightGear = Set(
  "com.abajar.avleditor.FlightGearManagerCheck"
)

checks := {
  val log = streams.value.log
  val classpath = (Test / fullClasspath).value.files.mkString(java.io.File.pathSeparator)
  val all = (Test / discoveredMainClasses).value.sorted

  val haveFlightGear =
    sys.env.getOrElse("PATH", "").split(java.io.File.pathSeparator)
      .exists(dir => new java.io.File(dir, "fgfs").canExecute)

  val skipped = if (haveFlightGear) Set.empty[String] else checksNeedingFlightGear
  val toRun = all.filterNot(notChecks.contains).filterNot(skipped.contains)

  val excluded = all.count(notChecks.contains)
  log.info(s"Running ${toRun.size} checks of ${all.size} mains ($excluded not checks, ${skipped.size} skipped).")
  skipped.foreach(name => log.warn(s"  SKIPPED $name — FlightGear (fgfs) is not installed on this machine"))

  // Every check runs, and the failures are collected rather than aborting on the first one: a
  // pull request should report everything that is wrong with it, not the alphabetically first
  // thing. Each runs in its own JVM, which is what makes `sys.exit(1)` readable as a result
  // instead of killing sbt.
  //
  // The output is read as well as the exit code, because some checks EXCUSE THEMSELVES and exit 0.
  // `JsbsimCurveCheck` and `DuctedFanFlightCheck` print `..._SKIPPED` and return success when
  // JSBSim is not installed — and those two are the evidence for the whole export path: they run
  // JSBSim on the exported aircraft and compare its forces against the tables in the file it
  // loaded. A runner without JSBSim would report them green having measured nothing, which is the
  // one outcome this project treats as worse than a failure.
  val javaBin = new java.io.File(new java.io.File(sys.props("java.home"), "bin"), "java").getPath
  val selfSkipMarker = """(?m)^\s*\w+_SKIPPED\s*$""".r

  val results = toRun.map { name =>
    val output = new StringBuilder
    val code = scala.sys.process.Process(Seq(javaBin, "-cp", classpath, name)).!(
      scala.sys.process.ProcessLogger(line => { output.append(line).append('\n'); log.info(line) })
    )
    val excusedItself = code == 0 && selfSkipMarker.findFirstIn(output.toString).isDefined
    if (code != 0) log.error(s"  FAIL $name (exit $code)")
    else if (excusedItself) log.warn(s"  DID NOT RUN $name — it excused itself and still exited 0")
    else log.info(s"  PASS $name")
    (name, code, excusedItself)
  }

  val failed = results.collect { case (name, code, _) if code != 0 => name }
  val excused = results.collect { case (name, _, true) => name }

  // Informative locally — a developer without JSBSim installed should not be blocked — and fatal
  // where the environment is supposed to be complete. The workflow sets it.
  val demandEveryCheck = sys.env.get("CHECKS_REQUIRE_ALL").exists(v => v == "1" || v == "true")

  if (excused.nonEmpty) {
    log.warn(s"${excused.size} checks excused themselves and did not measure anything:")
    excused.foreach(name => log.warn(s"  $name"))
    if (demandEveryCheck) {
      sys.error("CHECKS_REQUIRE_ALL is set, so a check that excuses itself is a failure:\n" +
        excused.map("  " + _).mkString("\n") +
        "\nInstall what it needs (JSBSim: `pip install jsbsim`, and make sure it is on the PATH).")
    }
  }

  val skippedNote = if (skipped.isEmpty) "" else s", ${skipped.size} skipped (no FlightGear)"

  if (failed.nonEmpty) {
    sys.error(s"${failed.size} of ${toRun.size} checks failed$skippedNote:\n" +
      failed.map("  " + _).mkString("\n"))
  }
  log.info(s"All ${toRun.size} checks passed$skippedNote.")
}

val osName = System.getProperty("os.name").toLowerCase
val osArch = System.getProperty("os.arch")

// SWT 3.127.0 from Maven Central (org.eclipse.platform group)
val swtDependency: Option[sbt.ModuleID] = {
  if (osName.contains("linux"))
    Some(("org.eclipse.platform" % "org.eclipse.swt.gtk.linux.x86_64" % "3.127.0")
      .exclude("org.eclipse.platform", "org.eclipse.swt"))
  else if (osName.contains("windows"))
    Some(("org.eclipse.platform" % "org.eclipse.swt.win32.win32.x86_64" % "3.127.0")
      .exclude("org.eclipse.platform", "org.eclipse.swt"))
  else if (osName.contains("mac"))
    if (osArch.contains("aarch64"))
      Some(("org.eclipse.platform" % "org.eclipse.swt.cocoa.macosx.aarch64" % "3.127.0")
        .exclude("org.eclipse.platform", "org.eclipse.swt"))
    else
      Some(("org.eclipse.platform" % "org.eclipse.swt.cocoa.macosx.x86_64" % "3.127.0")
        .exclude("org.eclipse.platform", "org.eclipse.swt"))
  else
    None
}

libraryDependencies ++= Seq(
  "org.eclipse.persistence" % "org.eclipse.persistence.moxy" % "2.5.2",
  "junit" % "junit" % "4.12",
  "javax.xml.bind" % "jaxb-api" % "2.3.1",
  "org.jogamp.gluegen" % "gluegen-rt-main" % "2.5.0",
  "org.jogamp.jogl" % "jogl-all-main" % "2.5.0",
  "org.yaml" % "snakeyaml" % "2.0"
) ++ swtDependency.toSeq
