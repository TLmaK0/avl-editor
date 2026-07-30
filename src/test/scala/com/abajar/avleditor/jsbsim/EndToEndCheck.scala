/*
 * End-to-end: load a .avle, run AVL, export to JSBSim.
 * Run: sbt "test:runMain com.abajar.avleditor.jsbsim.EndToEndCheck <file.avle> <outDir>"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim.CRRCSimRepository
import com.abajar.avleditor.avl.connectivity.AvlRunner
import com.abajar.avleditor.AvlManager
import java.io.File
import java.util.Properties
import scala.collection.JavaConverters._

object EndToEndCheck {
  def main(args: Array[String]): Unit = {
    val avle = args(0); val outDir = args(1)
    val crrcsim = new CRRCSimRepository().restoreFromFile(new File(avle))
    val avl = crrcsim.getAvl()
    // Re-establish transient parent links (the editor does this after load).
    avl.getGeometry().getSurfaces().asScala.foreach(_.initSectionParents())
    avl.getGeometry().getBodies().asScala.foreach(_.initProfilePointParents())

    val props = new Properties()
    val ok = AvlManager.ensureAvlAvailable(props)
    System.err.println(s"AVL available=$ok path=${props.getProperty("avl.path")}")

    crrcsim.calculate() // derives mass and inertias from the model's mass objects

    // What the editor checks before letting the model reach a simulator. Reported rather than
    // enforced here, since this harness exercises the writers directly.
    val requirements = SimulationRequirements.validate(crrcsim)
    println(s"SIM_REQUIREMENTS_MET=${requirements.isEmpty}")
    requirements.foreach(p => println(s"  unmet: $p"))

    val calc = new AvlRunner(props.getProperty("avl.path"), avl, crrcsim.getOriginPath()).getCalculation()
    JsbsimExporter.export(new File(outDir), "eurofighter", crrcsim, calc)
    System.err.println(s"EXPORT_DONE -> $outDir/aircraft/eurofighter/eurofighter.xml")

    // FlightGear package + structural validation of the new pieces (.ac + set.xml).
    val fgRoot = new File(outDir, "fg")
    FlightGearExporter.export(fgRoot, "eurofighter", crrcsim, calc)
    val acPath = new File(fgRoot, "eurofighter/Models/eurofighter.ac").getPath
    val acModel = com.abajar.avleditor.ac3d.AC3DLoader.load(acPath)
    val nSurf = acModel.map(m => countSurfaces(m.rootObject)).getOrElse(0)
    println(s"AC3D_ROUNDTRIP ok=${acModel.isDefined} surfaces=$nSurf")
    val setXml = new File(fgRoot, "eurofighter/eurofighter-set.xml")
    // Parses AND declares an FDM FlightGear knows — a well-formed file with a bogus
    // flight-model is rejected at startup, so well-formedness alone proves nothing.
    val xmlOk = try {
      val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(setXml)
      val fdm = doc.getElementsByTagName("flight-model").item(0).getTextContent
      if (fdm != FlightGearExporter.FlightModel) System.err.println(s"unexpected flight-model: $fdm")
      fdm == FlightGearExporter.FlightModel
    } catch { case _: Throwable => false }
    println(s"SET_XML_VALID=$xmlOk")
    println(if (acModel.isDefined && nSurf > 0 && xmlOk) "FG_PACKAGE_OK" else "FG_PACKAGE_FAIL")
  }

  private def countSurfaces(o: com.abajar.avleditor.ac3d.AC3DObject): Int =
    o.surfaces.length + o.children.map(countSurfaces).sum
}
