/*
 * Making the Propeller's pitch required (issue #19) is a contract change: every model saved before
 * this fix has a propeller whose pitch was never filled in, because nothing read it. This is what
 * happens to one of those files now — it must still load, and refuse only at export, by name.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.LegacyPropellerPitchCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.crrcsim._
import java.io.File

object LegacyPropellerPitchCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def shaftOf(model: CRRCSim): Shaft =
    model.getConfig.getPower.getBateries.get(0).getShafts.get(0)

  /**
   * A model exactly as every version before this fix would have saved it: a propeller with a
   * diameter (the one figure the export already read) and no pitch at all — `Propeller.H` has
   * existed in this class since the project's earliest commits, so there is no older file format
   * that lacks the field; there is only every file that never had a reason to fill it in.
   */
  private def withUnfilledPitch(): CRRCSim = {
    val model = new CRRCSimFactory().create()
    val power = model.getConfig.getPower
    power.getBateries.clear()
    val battery = power.createBattery()
    battery.setU_0(11.1f)
    battery.createShaft()
    val propeller = shaftOf(model).createPropeller()
    propeller.setD(0.25f) // the pitch, H, is left at its default: 0f
    val engine = shaftOf(model).createEngine()
    val point = new EngineData
    point.setU_K(11.1f); point.setI_M(20f); point.setRpms(9000f)
    engine.getData.add(point)
    val idle = engine.createDataIdle()
    idle.setU_K(11.1f); idle.setI_M(0.4f)
    model
  }

  def main(args: Array[String]): Unit = {
    println("a model as every version before this fix would have saved it")
    val original = withUnfilledPitch()
    check("its propeller has a diameter", shaftOf(original).getPropellers.get(0).getD > 0)
    check("but no pitch, same as every file on disk today", shaftOf(original).getPropellers.get(0).getH == 0f)

    println("saving and reloading it is unaffected: nothing but the export reads the pitch")
    val repo = new CRRCSimRepository
    val out = File.createTempFile("avleditor-legacy-pitch", ".avle")
    val reloaded =
      try { repo.storeToFile(out, original); repo.restoreFromFile(out) }
      finally out.delete()
    check("the propeller is still there", shaftOf(reloaded).getPropellers.size == 1)
    check("with its diameter intact", math.abs(shaftOf(reloaded).getPropellers.get(0).getD - 0.25f) < 1e-6)
    check("and still no pitch — loading never invents one", shaftOf(reloaded).getPropellers.get(0).getH == 0f)

    println("it refuses at export, by name, rather than three steps downstream as a NaN")
    val problems = SimulationRequirements.validate(reloaded)
    val pitchLabel = {
      import com.abajar.avleditor.view.annotations.AvlEditorField
      Option(classOf[Propeller].getDeclaredField("H").getAnnotation(classOf[AvlEditorField]))
        .map(_.text()).getOrElse("H")
    }
    println(s"  '$pitchLabel' expected in: ${problems.mkString(" | ")}")
    check("the requirements name the missing pitch", problems.exists(_.contains(pitchLabel)))
    check("and the export refuses rather than exporting a generic curve for it", {
      try { JsbsimExporter.buildPropulsion(reloaded); false }
      catch { case e: IllegalStateException => e.getMessage.toLowerCase.contains("pitch") }
    })

    println(if (ok) "LEGACY_PROPELLER_PITCH_OK" else "LEGACY_PROPELLER_PITCH_FAIL")
    if (!ok) sys.exit(1)
  }
}
