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
    // The no-load current comes from the idle rows and from nowhere else. Note it is deliberately
    // not the 1.0 A of the lightest loaded point: that point is loaded, and reading it as no-load
    // is the defect this check now pins.
    val idle = new EngineDataIdle(); idle.setU_K(14.0f); idle.setI_M(0.6f)
    engine.getDataIdle.add(idle)

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
      (pr.motor match {
        case em: JsbsimWriter.ElectricMotor =>
          math.abs(em.maxPowerWatts - 14.0 * 20.0) < 0.5 &&   // strongest point of the curve
          math.abs(em.kvRpmPerVolt - 9000.0 / 14.0) < 0.5 &&  // Kv from the most unloaded point
          math.abs(em.noLoadCurrentA - 0.6) < 1e-6 &&         // the idle row, not the lightest load
          math.abs(em.maxVolts - 14.8) < 1e-6                 // the battery's full charge
        case _ => false
      })
    }
    println(if (ok) "PROP_MAP_OK" else "PROP_MAP_FAIL")
    if (!ok) sys.exit(1)
  }
}
