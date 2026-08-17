/*
 * The refinement the decambering needs, against real AVL output: one node per strip, and the same aeroplane.
 *
 * This is the load-bearing check of #17's step 1. Iterated decambering acts one unknown per section, so the
 * analysis geometry has to carry one section node per strip — and if adding those nodes moves the aeroplane,
 * every measurement downstream of it is of a different aircraft, including the invariant the whole feature
 * rests on: that below the stall the answer is AVL's own answer unchanged.
 *
 * So it asserts a **property**, not numbers: refine the geometry, run AVL on both, and require that every
 * strip's station, every strip's area and `CLtot` come back identical. It survives any rescaling of the check
 * aircraft, and it is the exact statement of what the refinement claims.
 *
 * The first attempt failed it, which is why it exists. Refining to one panel per interval (`Nspan 1`) put the
 * strips at even spacing where the surface's own `Sspace 1.0` clusters them at the root and the tip: `CLtot`
 * went 0.74796 -> 0.76833 and a tip strip's `cl` 0.1073 -> 0.2293. Adding the nodes at AVL's own panel edges
 * and leaving `Nspan`/`Sspace` alone is what makes it free.
 *
 * It also pins the spacing law itself against AVL rather than against this project's arithmetic: AVL's printed
 * station is the panel's **mid-angle**, `s (1 - cos((i + 1/2) pi / N)) / 2`, and not the panel's midpoint. The
 * two differ by a factor of two at the root — 0.0026 against 0.0051 on the check wing — and anything that
 * reconstructed panel geometry from the printed stations would be quietly wrong exactly where the stall starts.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.decambering.PanelRefinementCheck"
 */
package com.abajar.avleditor.avl.decambering

import com.abajar.avleditor.{AvlManager, TestAircraft}
import com.abajar.avleditor.avl.connectivity.AvlRunner
import com.abajar.avleditor.avl.runcase.{AvlCalculation, StripForce}
import com.abajar.avleditor.crrcsim.CRRCSim
import java.util.Properties
import scala.collection.JavaConverters._

object PanelRefinementCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def run(props: Properties, model: CRRCSim): AvlCalculation =
    new AvlRunner(props.getProperty("avl.path"), model.getAvl, model.getOriginPath, 45f, 20f)
      .getCalculation()

  /** Every strip of one attitude, in the order AVL printed them. */
  private def stripsAt(calc: AvlCalculation, alphaDeg: Float): Seq[StripForce] =
    calc.getAlphaSweep.asScala.find(p => math.abs(p.getAlphaDeg - alphaDeg) < 1e-3)
      .map(_.getStrips.asScala.toSeq).getOrElse(Seq.empty)

  private def liftAt(calc: AvlCalculation, alphaDeg: Float): Option[Float] =
    calc.getAlphaSweep.asScala.find(p => math.abs(p.getAlphaDeg - alphaDeg) < 1e-3).map(_.getCl)

  def main(args: Array[String]): Unit = {
    println("AVL's panel spacing, which is arithmetic and needs no AVL")
    val edges = PanelStations.edgeFractions(12)
    check("twelve panels have thirteen edges, root to tip", edges.length == 13)
    check("they start at the root and end at the tip",
      math.abs(edges.head) < 1e-12 && math.abs(edges.last - 1.0) < 1e-12)
    check("cosine spacing clusters them at both ends, not evenly",
      edges(1) < 0.5 / 12.0 && (edges.last - edges(edges.length - 2)) < 0.5 / 12.0)
    check("it is symmetric about mid-span",
      edges.zip(edges.reverse).forall { case (a, b) => math.abs(a + b - 1.0) < 1e-12 })
    // The trap this exists to close: what AVL prints is the mid-angle, not the panel's midpoint.
    val printed = PanelStations.printedStationFractions(12)
    val midpoints = edges.sliding(2).map { case Seq(a, b) => (a + b) / 2.0 }.toSeq
    check("the printed station is not the panel midpoint, and differs most at the root",
      math.abs(printed.head - midpoints.head) > 0.4 * midpoints.head)
    check("only the cosine spacing is treated as measured",
      PanelStations.spacingIsMeasured(1.0f) && !PanelStations.spacingIsMeasured(0f) &&
        !PanelStations.spacingIsMeasured(-1.5f) && !PanelStations.spacingIsMeasured(3f))

    println("what the refinement says it did, before AVL is asked")
    val refinement = DecamberingGeometry.refine(TestAircraft.conventional(), Set("wing"))
    refinement.report.foreach(println)
    check("the wing was refined", refinement.refined.exists(_.name == "wing"))
    check("and every other surface is accounted for as refused, never silently skipped",
      refinement.refused.map(_.name).toSet == Set("tailplane", "fin"))
    val wing = refinement.refined.find(_.name == "wing").get
    check("it ends with one node per panel edge", wing.nodesAfter == wing.panels + 1)
    check("and it added nodes rather than replacing the drawn ones", wing.nodesAfter > wing.nodesBefore)
    // The user's own model must be untouched: the refinement works on a copy.
    check("the model it was given still has the wing the user drew",
      TestAircraft.conventional().getAvl.getGeometry.getSurfaces.asScala
        .find(_.getName == "wing").get.getSections.size == 3)

    println("a surface whose spacing has not been measured is refused rather than refined")
    val evenlySpaced = TestAircraft.conventional()
    evenlySpaced.getAvl.getGeometry.getSurfaces.asScala.find(_.getName == "wing").get.setSspace(0f)
    val refusedSpacing = DecamberingGeometry.refine(evenlySpaced, Set("wing"))
    check("it is left as drawn", refusedSpacing.refined.isEmpty)
    check("and the reason names the spacing",
      refusedSpacing.refused.exists(r => r.name == "wing" && r.reason.contains("Sspace")))

    println("a wing that changes aerofoil along the span is refused, not blended by guesswork")
    val mixedAerofoil = TestAircraft.conventional()
    mixedAerofoil.getAvl.getGeometry.getSurfaces.asScala.find(_.getName == "wing").get
      .getSections.asScala.last.setNACA("0012")
    val refusedBlend = DecamberingGeometry.refine(mixedAerofoil, Set("wing"))
    check("it is left as drawn", refusedBlend.refined.isEmpty)
    check("and the reason names both aerofoils",
      refusedBlend.refused.exists(r => r.name == "wing" &&
        r.reason.contains("NACA 2412") && r.reason.contains("NACA 0012")))

    val props = new Properties()
    if (!AvlManager.ensureAvlAvailable(props)) {
      // Not a pass: the whole point of this check is what AVL says. Said out loud rather than exited 0 on.
      println("  AVL is not available here, so the aeroplane was never compared. Nothing about the")
      println("  refinement has been measured by this run.")
      println("PANEL_REFINEMENT_SKIPPED")
      if (!ok) sys.exit(1)
      return
    }

    println("the same aeroplane, out of AVL, refined and as drawn")
    val drawn = run(props, TestAircraft.conventional())
    val refined = run(props, DecamberingGeometry.refine(TestAircraft.conventional(), Set("wing")).model)

    val alpha = 7.5f
    val drawnStrips = stripsAt(drawn, alpha)
    val refinedStrips = stripsAt(refined, alpha)
    println(f"  ${drawnStrips.length}%d strips as drawn, ${refinedStrips.length}%d refined, at $alpha%.1f deg")
    check("both came back with strips", drawnStrips.nonEmpty && refinedStrips.nonEmpty)
    check("refining the sections does not change how many strips AVL solves",
      drawnStrips.length == refinedStrips.length)

    if (drawnStrips.length == refinedStrips.length && drawnStrips.nonEmpty) {
      val pairs = drawnStrips.zip(refinedStrips)
      val worstStation = pairs.map { case (a, b) => math.abs(a.getStationY - b.getStationY) }.max
      val worstArea = pairs.map { case (a, b) => math.abs(a.getArea - b.getArea) }.max
      val worstCl = pairs.map { case (a, b) => math.abs(a.getCl - b.getCl) }.max
      println(f"  worst difference: station $worstStation%.6f, area $worstArea%.6f, cl $worstCl%.6f")
      // AVL prints these to four decimals, so identical means identical at the printing resolution.
      check("every strip sits at the same station", worstStation < 1e-4)
      check("every strip has the same area", worstArea < 1e-4)
      check("and every strip carries the same lift", worstCl < 1e-4)
    }

    val drawnCl = liftAt(drawn, alpha)
    val refinedCl = liftAt(refined, alpha)
    println(f"  CLtot as drawn ${drawnCl.getOrElse(Float.NaN)}%.5f, refined ${refinedCl.getOrElse(Float.NaN)}%.5f")
    check("the whole aircraft's lift is unchanged",
      drawnCl.isDefined && refinedCl.isDefined && math.abs(drawnCl.get - refinedCl.get) < 1e-4)
    check("across every attitude of the sweep, not just one",
      drawn.getAlphaSweep.asScala.forall { point =>
        liftAt(refined, point.getAlphaDeg).exists(cl => math.abs(cl - point.getCl) < 1e-4)
      })

    println("and the nodes landed where AVL puts its panels")
    val wingStrips = refinedStrips.filter(s => s.getSurfaceName == "wing" && !s.isMirrored)
      .sortBy(_.getStationY)
    val semispan = TestAircraft.Span / 2.0
    val predicted = PanelStations.printedStationFractions(wing.panels).map(_ * semispan).sorted
    println("  AVL's stations: " + wingStrips.map(s => f"${s.getStationY}%.4f").mkString(" "))
    println("  predicted:      " + predicted.map(y => f"$y%.4f").mkString(" "))
    check("one strip per panel on the half the model draws", wingStrips.length == wing.panels)
    check("and the mid-angle law reproduces every station AVL printed",
      wingStrips.length == predicted.length &&
        wingStrips.map(_.getStationY.toDouble).zip(predicted)
          .forall { case (avl, law) => math.abs(avl - law) < 1e-3 })

    println(if (ok) "PANEL_REFINEMENT_OK" else "PANEL_REFINEMENT_FAIL")
    if (!ok) sys.exit(1)
  }
}
