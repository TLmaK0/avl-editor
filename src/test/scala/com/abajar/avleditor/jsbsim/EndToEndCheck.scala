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

    crrcsim.calculate()
    val calc = new AvlRunner(props.getProperty("avl.path"), avl, crrcsim.getOriginPath()).getCalculation()
    JsbsimExporter.export(new File(outDir), "eurofighter", crrcsim, calc)
    System.err.println(s"EXPORT_DONE -> $outDir/aircraft/eurofighter/eurofighter.xml")
  }
}
