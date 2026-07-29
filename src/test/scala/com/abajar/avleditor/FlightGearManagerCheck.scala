package com.abajar.avleditor
object FlightGearManagerCheck {
  def main(args: Array[String]): Unit = {
    val p = new java.util.Properties()
    val found = FlightGearManager.findExecutable(p)
    val saved = p.getProperty(FlightGearManager.CONFIG_KEY)
    println(s"found=$found saved=$saved")
    val ok = found.isDefined && found.get == saved && saved.endsWith("fgfs")
    println(if (ok) "FGMGR_OK" else "FGMGR_FAIL")
    if (!ok) sys.exit(1)
  }
}
