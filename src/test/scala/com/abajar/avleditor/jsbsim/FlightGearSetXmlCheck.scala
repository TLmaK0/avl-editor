/*
 * Validates the *content* of the FlightGear -set.xml, not just that it parses. A
 * well-formed file with a flight-model FlightGear does not know is rejected at startup with
 * "Unrecognized flight model '...', cannot init flight dynamics model".
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.FlightGearSetXmlCheck"
 */
package com.abajar.avleditor.jsbsim

import javax.xml.parsers.DocumentBuilderFactory
import java.io.ByteArrayInputStream

object FlightGearSetXmlCheck {

  /** FDM identifiers FlightGear accepts (its own table; "jsbsim" is not one of them). */
  private val ValidFlightModels = Set("jsb", "yasim", "larcsim", "ufo", "magic", "balloon", "external", "network", "null")

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def textOf(xml: String, tag: String): Option[String] = {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
      .parse(new ByteArrayInputStream(xml.getBytes("UTF-8")))
    val nodes = doc.getElementsByTagName(tag)
    if (nodes.getLength == 0) None else Option(nodes.item(0).getTextContent)
  }

  def main(args: Array[String]): Unit = {
    val name = "eurofighter"
    val xml = FlightGearExporter.setXml(name)

    val parsed = try { textOf(xml, "flight-model") } catch { case _: Throwable => None }
    check("set.xml is well-formed and declares a flight-model", parsed.isDefined)

    val fdm = parsed.getOrElse("")
    check(s"flight-model '$fdm' is one FlightGear accepts", ValidFlightModels.contains(fdm))
    check("flight-model is jsb (JSBSim), not jsbsim", fdm == "jsb")
    check("FlightModel constant matches the emitted value", FlightGearExporter.FlightModel == fdm)

    check("aero points at the JSBSim model name", textOf(xml, "aero").exists(_ == name))

    // Generated models have no cockpit interior: starting in view 0 shows an empty scene.
    check("starts in an external view",
      textOf(xml, "view-number").exists(_.trim == FlightGearExporter.ChaseView.toString))
    // FlightGear resolves this relative to the aircraft's own directory; prefixing it with
    // the package name makes it fall back to glider.ac.
    check("model path is relative to the aircraft directory",
      textOf(xml, "path").exists(_ == s"Models/$name.ac"))

    println(if (ok) "FG_SET_XML_OK" else "FG_SET_XML_FAIL")
    if (!ok) sys.exit(1)
  }
}
