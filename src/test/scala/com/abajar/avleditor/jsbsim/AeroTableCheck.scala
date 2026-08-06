/*
 * The exported flight model has to state the measured curves, and state them instead of the single-point
 * constants — a curve left alongside the constant it replaces counts the same lift twice. And the drag has to
 * be driven by attitude, not by the square of the computed lift, or the day the lift curve bends over a
 * stalled aircraft would have less drag than in normal flight.
 * Run with:  sbt "test:runMain com.abajar.avleditor.jsbsim.AeroTableCheck"
 */
package com.abajar.avleditor.jsbsim

import com.abajar.avleditor.jsbsim.JsbsimWriter._

object AeroTableCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private val curves = JsbsimWriterCheck.sampleCurves

  private def functionNames(xml: String): Seq[String] =
    """<function name="([^"]+)"""".r.findAllMatchIn(xml).map(_.group(1)).toList

  /** The rows of one table function, as (independent, value) pairs. */
  private def tableRows(xml: String, functionName: String): Seq[(Double, Double)] = {
    val block = xml.split("""<function name="""").find(_.startsWith(functionName + "\"")).getOrElse("")
    val data = """(?s)<tableData>(.*?)</tableData>""".r.findFirstMatchIn(block).map(_.group(1)).getOrElse("")
    data.split("\n").map(_.trim).filter(_.nonEmpty).map { line =>
      val parts = line.split("\\s+")
      (parts(0).toDouble, parts(1).toDouble)
    }.toList
  }

  def main(args: Array[String]): Unit = {
    println("without a sweep, the model states what it always stated")
    val flat = write(JsbsimWriterCheck.sampleAircraft)
    val flatNames = functionNames(flat)
    check("the tangent at the trimmed point: lift as an anchor plus a rate",
      flatNames.contains("aero/force/lift_0") && flatNames.contains("aero/force/lift_alpha"))
    check("and drag from the square of the lift, as before",
      flatNames.contains("aero/force/drag_induced") && flat.contains("aero/cl-squared"))
    check("no tables anywhere", !flat.contains("<tableData>"))

    println("with a sweep, it states the curves")
    val withCurves = write(JsbsimWriterCheck.sampleAircraft.copy(curves = Some(curves)))
    val names = functionNames(withCurves)
    check("one curve for lift", names.contains("aero/force/lift"))
    check("one for drag", names.contains("aero/force/drag"))
    check("one for the pitching moment", names.contains("aero/moment/pitch"))

    // The point of the whole change: a curve REPLACES the constants. Both would be counted.
    check("the lift anchor and rate are gone",
      !names.contains("aero/force/lift_0") && !names.contains("aero/force/lift_alpha"))
    check("the pitch anchor and rate are gone",
      !names.contains("aero/moment/pitch_0") && !names.contains("aero/moment/pitch_alpha"))
    check("the parasite and induced drag terms are gone",
      !names.contains("aero/force/drag_0") && !names.contains("aero/force/drag_induced"))
    // The trap: cl-squared follows the computed lift, so a bent lift curve would drag less, not more.
    check("and drag is driven by attitude, not by the square of the lift",
      !withCurves.contains("aero/cl-squared"))

    println("what the rates and controls do")
    check("the rate terms stay, one number each",
      names.contains("aero/force/lift_q") && names.contains("aero/moment/pitch_q"))
    check("and so do the control terms, which JSBSim adds itself",
      names.contains("aero/force/lift_de") && names.contains("aero/moment/pitch_de"))
    check("the other axes are untouched",
      names.contains("aero/moment/roll_beta") && names.contains("aero/moment/yaw_beta") &&
        names.contains("aero/force/side_beta"))

    println("the tables themselves")
    Seq(("aero/force/lift", curves.cl), ("aero/force/drag", curves.cd),
        ("aero/moment/pitch", curves.cm)).foreach { case (name, expected) =>
      val rows = tableRows(withCurves, name)
      check(s"$name has one row per attitude", rows.length == curves.alphaRad.length)
      check(s"$name is looked up on the attitude in radians",
        rows.forall { case (a, _) => math.abs(a) <= math.Pi / 2 } &&
          math.abs(rows.last._1 - math.toRadians(20)) < 1e-6)
      check(s"$name is ordered by attitude", rows.map(_._1) == rows.map(_._1).sorted)
      check(s"$name states the values measured",
        rows.map(_._2).zip(expected).forall { case (written, measured) =>
          math.abs(written - measured) < 1e-6
        })
    }
    check("every table is looked up against the same property",
      """<independentVar lookup="row">aero/alpha-rad</independentVar>""".r
        .findAllMatchIn(withCurves).size == 3)

    println("a curve is three points or it is a line")
    val two = curves.copy(alphaRad = curves.alphaRad.take(2), cl = curves.cl.take(2),
      cd = curves.cd.take(2), cm = curves.cm.take(2))
    check("two points are refused as a curve", !two.isCurve)
    check("and the model falls back to the constants, not to half a table",
      !write(JsbsimWriterCheck.sampleAircraft.copy(curves = Some(two))).contains("<tableData>"))
    val ragged = curves.copy(cl = curves.cl.drop(1))
    check("a grid and a curve of different lengths is refused", !ragged.isCurve)
    check("thirteen real attitudes are a curve", curves.isCurve)

    println("the shape JSBSim needs")
    check("each table sits inside the product with its factors",
      """(?s)<function name="aero/moment/pitch">.*?metrics/cbarw-ft.*?<table>""".r
        .findFirstIn(withCurves).isDefined)
    check("and the lift table is scaled by the dynamic pressure and area",
      """(?s)<function name="aero/force/lift">.*?aero/qbar-area.*?<table>""".r
        .findFirstIn(withCurves).isDefined)

    println(if (ok) "AERO_TABLE_OK" else "AERO_TABLE_FAIL")
    if (!ok) sys.exit(1)
  }
}
