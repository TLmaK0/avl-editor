/*
 * Data the editor no longer offers to create must still survive being loaded and saved. The
 * Simple Trust is the case in point: the '+ Trust' button is gone (a CRRCsim thrust model with no
 * export left to consume it), but files saved with one must not lose it silently.
 *
 * The model with the legacy node is built here rather than taken from a sample: a sample is data the
 * user edits — the eurofighter's own Simple Trust was deleted the day deleting one became possible —
 * and a compatibility guarantee cannot rest on nobody tidying it up.
 * Run with:  sbt "test:runMain com.abajar.avleditor.crrcsim.LegacyRoundTripCheck [file.avle]"
 */
package com.abajar.avleditor.crrcsim

import java.io.File

object LegacyRoundTripCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def shaftOf(model: CRRCSim): Shaft =
    model.getConfig.getPower.getBateries.get(0).getShafts.get(0)

  private def trusts(model: CRRCSim): Int = shaftOf(model).getSimpleTrusts.size

  /** A model as an older version of the editor would have saved it: thrust from a Simple Trust. */
  private def withLegacyThrust(): CRRCSim = {
    val model = new CRRCSimFactory().create()
    val power = model.getConfig.getPower
    power.getBateries.clear()
    val battery = power.createBattery()
    battery.setU_0(11.1f)
    battery.createShaft()
    val trust = battery.getShafts.get(0).createSimpleTrust()
    trust.setK_F(0.021f)
    trust.setK_M(0.0007f)
    trust.getGearing.setI(1.5f)
    model
  }

  def main(args: Array[String]): Unit = {
    val repo = new CRRCSimRepository

    println("a model saved with a Simple Trust")
    val original = withLegacyThrust()
    check("has one to lose", trusts(original) == 1)

    val out = File.createTempFile("avleditor-roundtrip", ".avle")
    try {
      repo.storeToFile(out, original)
      val reloaded = repo.restoreFromFile(out)
      check("saving and reloading keeps it", trusts(reloaded) == 1)
      val trust = shaftOf(reloaded).getSimpleTrusts.get(0)
      println(f"  k_F ${trust.getK_F}%.4f, k_M ${trust.getK_M}%.5f, gearing ${trust.getGearing.getI}%.2f")
      check("with its own figures, not a blank one",
        math.abs(trust.getK_F - 0.021f) < 1e-6 && math.abs(trust.getK_M - 0.0007f) < 1e-7 &&
          math.abs(trust.getGearing.getI - 1.5f) < 1e-6)

      // Saved again from the reloaded model: a node the editor cannot create must not be lost on the
      // second pass either, which is where a load that quietly drops it would show.
      val twice = File.createTempFile("avleditor-roundtrip-twice", ".avle")
      try {
        repo.storeToFile(twice, reloaded)
        check("and again on the next save", trusts(repo.restoreFromFile(twice)) == 1)
      } finally twice.delete()
    } finally out.delete()

    // Any file given on the command line is reported, not required to have one: this is how a real
    // model can be checked by hand.
    args.headOption.foreach { path =>
      val file = new File(path)
      if (file.exists()) {
        println(s"\n  Simple Trusts in ${file.getName}: ${trusts(repo.restoreFromFile(file))}")
      }
    }

    println(if (ok) "LEGACY_ROUNDTRIP_OK" else "LEGACY_ROUNDTRIP_FAIL")
    if (!ok) sys.exit(1)
  }
}
