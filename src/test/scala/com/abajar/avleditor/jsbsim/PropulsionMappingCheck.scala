/*
 * Validates JsbsimExporter.buildPropulsion against a constructed electric powertrain.
 * Run: sbt "test:runMain com.abajar.avleditor.jsbsim.PropulsionMappingCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim._
import java.util.ArrayList

object PropulsionMappingCheck {
  def main(args: Array[String]): Unit = {
    val c = new CRRCSimFactory().create()

    val prop = new Propeller(); prop.setD(0.25f); prop.setBlades(3)
    val engine = new Engine()
    val ed1 = new EngineData(); ed1.setU_K(14.0f); ed1.setI_M(1.0f); ed1.setRpms(9000f)
    val ed2 = new EngineData(); ed2.setU_K(14.0f); ed2.setI_M(20.0f); ed2.setRpms(6000f)
    engine.getData.add(ed1); engine.getData.add(ed2)

    val shaft = new Shaft()
    shaft.getPropellers.add(prop)
    shaft.getEngines.add(engine)

    val battery = new Battery(); battery.setU_0(14.8f)
    val shafts = new ArrayList[Shaft](); shafts.add(shaft); battery.setShafts(shafts)
    c.getConfig.getPower.getBateries.add(battery)

    val p = JsbsimExporter.buildPropulsion(c)
    println("propulsion=" + p)
    val ok = p.exists { pr =>
      math.abs(pr.propDiameterM - 0.25) < 0.001 &&
      pr.numBlades == 3 &&                                  // the blade count, not n_fold
      (pr.motor match {                                     // strongest point of the curve
        case JsbsimWriter.ElectricMotor(w) => math.abs(w - 14.0 * 20.0) < 0.5
        case _ => false
      })
    }
    println(if (ok) "PROP_MAP_OK" else "PROP_MAP_FAIL")
    if (!ok) sys.exit(1)
  }
}
