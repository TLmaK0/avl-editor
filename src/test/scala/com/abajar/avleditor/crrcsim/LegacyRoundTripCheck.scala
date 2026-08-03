/*
 * Data the editor no longer offers to create must still survive being loaded and saved. The
 * Simple Trust is the case in point: the '+ Trust' button is gone (a CRRCsim thrust model with no
 * export left to consume it), but files saved with one must not lose it silently.
 * Run with:  sbt "test:runMain com.abajar.avleditor.crrcsim.LegacyRoundTripCheck <file.avle>"
 */
package com.abajar.avleditor.crrcsim

import java.io.File

object LegacyRoundTripCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def trusts(model: CRRCSim): Int =
    model.getConfig.getPower.getBateries.get(0).getShafts.get(0).getSimpleTrusts.size

  def main(args: Array[String]): Unit = {
    val source = new File(if (args.nonEmpty) args(0) else "samples/eurofighter/eurofighter.avle")
    val repo = new CRRCSimRepository

    val loaded = repo.restoreFromFile(source)
    val before = trusts(loaded)
    println(s"  Simple Trusts in ${source.getName}: $before")
    check("the sample still carries the legacy node", before > 0)

    val out = File.createTempFile("avleditor-roundtrip", ".avle")
    try {
      repo.storeToFile(out, loaded)
      val after = trusts(repo.restoreFromFile(out))
      println(s"  after save and reload: $after")
      check("saving and reloading keeps it", after == before)
    } finally out.delete()

    println(if (ok) "LEGACY_ROUNDTRIP_OK" else "LEGACY_ROUNDTRIP_FAIL")
    if (!ok) sys.exit(1)
  }
}
