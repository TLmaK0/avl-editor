/*
 * End to end: an aircraft, a real AVL run, and the two packages the editor exports — JSBSim's and
 * FlightGear's — read back off disk.
 *
 * It ran on nothing. It took a `.avle` path and an output directory as arguments and threw
 * ArrayIndexOutOfBounds without them, so it was the one check in the project that could not be run
 * unattended; and given arguments it printed FG_PACKAGE_FAIL and still exited 0, so it could not fail
 * either. Both halves are fixed here: with no arguments it builds the check's own aircraft into a
 * temporary directory, and it asserts rather than narrates.
 *
 * The aircraft is `TestAircraft` and deliberately not a sample. Those are the user's aeroplanes: they get
 * edited while the editor is being tried out, and a check that loads one fails for reasons that have
 * nothing to do with the code — which is exactly how `DuctedFanFlightCheck` broke when a ducted fan
 * appeared in the eurofighter between one run and the next.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.EndToEndCheck"
 * Or on a real model:  sbt "test:runMain com.abajar.avleditor.jsbsim.EndToEndCheck <file.avle> <outDir>"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.{AvlManager, TestAircraft}
import com.abajar.avleditor.avl.connectivity.AvlRunner
import com.abajar.avleditor.crrcsim.{CRRCSim, CRRCSimRepository}
import java.io.File
import java.nio.file.Files
import java.util.Properties
import scala.collection.JavaConverters._

object EndToEndCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** The model this run is about: the check's own aircraft, or the file named on the command line. */
  private def aircraft(args: Array[String]): (CRRCSim, String, File, Boolean) =
    if (args.length >= 2) {
      val file = new File(args(0))
      val crrcsim = new CRRCSimRepository().restoreFromFile(file)
      val avl = crrcsim.getAvl
      // Re-establish the transient parent links, which is what the editor does after a load. Without them
      // a section does not know its surface, and a mass does not know which plane it mirrors about.
      avl.getGeometry.getSurfaces.asScala.foreach(_.initSectionParents())
      avl.getGeometry.getBodies.asScala.foreach(_.initProfilePointParents())
      (crrcsim, file.getName.replaceAll("\\.[^.]+$", ""), new File(args(1)), false)
    } else {
      (TestAircraft.conventional(), "testaircraft",
        Files.createTempDirectory("end_to_end_").toFile, true)
    }

  def main(args: Array[String]): Unit = {
    val props = new Properties()
    if (!AvlManager.ensureAvlAvailable(props)) {
      println("  AVL is not available here, and this check is about what comes out of a real run of it")
      println("END_TO_END_SKIPPED")
      return
    }

    val (crrcsim, name, outDir, ours) = aircraft(args)
    println(s"$name into ${outDir.getPath}")

    // The mass, the inertias and the centre of gravity are derived from the mass objects, so this has to
    // happen before anything is validated or exported or every model reports zero mass.
    crrcsim.calculate()

    // What the editor demands before letting a model reach a simulator. The check's own aircraft has to
    // meet them — that is what makes it a fit subject. A model named on the command line is the user's,
    // and its shortcomings are reported rather than turned into a failure of the editor.
    val requirements = SimulationRequirements.validate(crrcsim)
    requirements.foreach(problem => println("  unmet: " + problem))
    if (ours) check("the check's own aircraft meets the requirements to be simulated", requirements.isEmpty)
    else println(s"  SIM_REQUIREMENTS_MET=${requirements.isEmpty}")

    val calc = new AvlRunner(props.getProperty("avl.path"), crrcsim.getAvl, crrcsim.getOriginPath)
      .getCalculation()
    check("AVL answered with a configuration and derivatives",
      calc != null && calc.getConfiguration != null && calc.getStabilityDerivatives != null)

    println("the JSBSim package")
    JsbsimExporter.export(outDir, name, crrcsim, calc)
    val aircraftXml = new File(outDir, s"aircraft/$name/$name.xml")
    check("the aircraft file is written where JSBSim looks for it", aircraftXml.isFile)
    val xml = if (aircraftXml.isFile) new String(Files.readAllBytes(aircraftXml.toPath)) else ""
    check("with the mass balance, the aerodynamics and the propulsion in it",
      Seq("<mass_balance", "<aerodynamics", "<propulsion").forall(xml.contains))
    check("and it parses", parses(aircraftXml))

    println("the FlightGear package")
    val fgRoot = new File(outDir, "fg")
    FlightGearExporter.export(fgRoot, name, crrcsim, calc)

    val acPath = new File(fgRoot, s"$name/Models/$name.ac").getPath
    val acModel = com.abajar.avleditor.ac3d.AC3DLoader.load(acPath)
    val surfaces = acModel.map(model => countSurfaces(model.rootObject)).getOrElse(0)
    println(f"  the 3D model reads back with $surfaces%d surfaces")
    check("the 3D model is written and reads back", acModel.isDefined)
    check("with geometry in it", surfaces > 0)

    val setXml = new File(fgRoot, s"$name/$name-set.xml")
    check("the FlightGear launch file is written", setXml.isFile)
    // Parsing is not enough: a well-formed file naming a flight model FlightGear does not know is
    // accepted silently and then flown as a glider — which is exactly what `jsbsim` instead of `jsb` did.
    val declared = try {
      val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(setXml)
      Option(doc.getElementsByTagName("flight-model").item(0)).map(_.getTextContent)
    } catch { case _: Throwable => None }
    println(s"  it declares flight-model ${declared.getOrElse("(none)")}")
    check("declaring an FDM FlightGear knows", declared.exists(_ == FlightGearExporter.FlightModel))

    println(if (ok) "END_TO_END_OK" else "END_TO_END_FAIL")
    if (!ok) sys.exit(1)
  }

  private def parses(file: File): Boolean =
    try {
      javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
      true
    } catch { case _: Throwable => false }

  private def countSurfaces(o: com.abajar.avleditor.ac3d.AC3DObject): Int =
    o.surfaces.length + o.children.map(countSurfaces).sum
}
