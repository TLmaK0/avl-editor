/*
 * The spanwise loading, out of a real AVL run, and what the critical-section method makes of it.
 *
 * Two things here cannot be checked any other way. The first is that a strip's local lift coefficient is a
 * **straight line in attitude** — NACA TR 572's split into a basic distribution that does not change with
 * attitude and an additional one proportional to it. The whole method rests on that and this measures it
 * rather than assuming it.
 *
 * The second is Anderson's own finding, on two aeroplanes alike in everything but the taper — same span,
 * same area, same aerofoil, same everything else, so it is the taper. A rectangular wing gives up at the
 * **root**; a 4:1 tapered one gives up **two thirds of the way out**. That is the classic reason a taper is
 * watched: the wing runs out of lift where the ailerons are.
 *
 * The check deliberately does **not** assert that the tapered wing reaches less total lift, because on this
 * aeroplane it does not — see the note at the bottom. That is the finding, not a disappointment: how much
 * of its sections' lift a wing can use is a property of its own loading, and the flat 0.9 this replaces
 * happens to be about right for one of these two planforms and 6 % out on the other.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.xfoil.SpanwiseLoadingCheck"
 */
package com.abajar.avleditor.xfoil

import com.abajar.avleditor.{AvlManager, TestAircraft}
import com.abajar.avleditor.avl.connectivity.AvlRunner
import com.abajar.avleditor.avl.runcase.AvlCalculation
import com.abajar.avleditor.crrcsim.CRRCSim
import java.util.Properties
import scala.collection.JavaConverters._

object SpanwiseLoadingCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** The same section limit everywhere, so nothing but the loading can decide which station goes first. */
  private val UniformSectionLimit = 1.35

  private def run(props: Properties, model: CRRCSim): AvlCalculation =
    new AvlRunner(props.getProperty("avl.path"), model.getAvl, model.getOriginPath, 45f, 20f)
      .getCalculation()

  private def loadingOf(calc: AvlCalculation): Seq[(Double, Seq[com.abajar.avleditor.avl.runcase.StripForce])] =
    calc.getAlphaSweep.asScala.toSeq.map(p => (p.getAlphaDeg.toDouble, p.getStrips.asScala.toSeq))

  def main(args: Array[String]): Unit = {
    // What is typed at AVL to get the loading is checked in AlphaSweepCheck, which lives in the package
    // those commands are package-private to.
    val props = new Properties()
    if (!AvlManager.ensureAvlAvailable(props)) {
      println("  AVL is not available here; the rest of this check needs it")
      println(if (ok) "SPANWISE_LOADING_OK" else "SPANWISE_LOADING_FAIL")
      if (!ok) sys.exit(1)
      return
    }

    println("a real run of the check's own aircraft")
    val straight = run(props, TestAircraft.conventional())
    val sweep = straight.getAlphaSweep.asScala.toList
    check("every attitude came back with strips", sweep.forall(_.getStrips.size > 0))
    val strips = sweep.head.getStrips.asScala.toSeq
    println(f"  ${strips.length}%d strips at each of ${sweep.length}%d attitudes")
    check("every surface the model states is there",
      Set("wing", "tailplane", "fin").forall(name => strips.exists(_.getSurfaceName == name)))
    // A mirrored surface is a separate block in AVL's file, at negative y. Both halves are real strips of
    // one aeroplane and both have to arrive; the fin, which is not mirrored, must not.
    check("a mirrored surface arrives as its own half, at negative y",
      strips.exists(s => s.getSurfaceName == "wing" && s.isMirrored && s.getYle < 0) &&
        strips.exists(s => s.getSurfaceName == "wing" && !s.isMirrored && s.getYle > 0))
    check("and it is reflected back onto the half the model states",
      strips.filter(_.getSurfaceName == "wing").forall(_.getStationY >= -1e-6))
    check("an unmirrored surface has no mirrored half",
      !strips.exists(s => s.getSurfaceName == "fin" && s.isMirrored))
    check("the wing's strips carry the wing's chord",
      strips.filter(_.getSurfaceName == "wing")
        .forall(s => math.abs(s.getChord - TestAircraft.Chord) < 1e-4))

    println("how straight the loading really is, which the method does not have to assume")
    val stations = WingMaximumLift.stations(loadingOf(straight), 1.0)
    val worst = stations.map(_.worstResidual).max
    val span = stations.map(s => math.abs(s.clPerDegree)).max
    println(f"  ${stations.length}%d stations; the worst departure from a straight line is $worst%.5f of " +
      f"local cl, against a lift slope of $span%.4f per degree")
    check("every station is followed across every attitude", stations.length == strips.length)
    // NACA TR 572 draws a straight line through this and works with two numbers per station. It is nearly
    // straight and not exactly: AVL's solution is linear in the freestream vector, whose components are
    // cos(alpha) and sin(alpha). Over 30 degrees that is a few percent of local cl — about a degree of
    // attitude at the stall — so the crossing is read off the measurements instead, and the residual is
    // reported rather than assumed away.
    check("the loading is straight in attitude to a few percent, and not exactly",
      worst > 1e-4 && worst < 0.05 * span * 30.0)
    check("the wing's lift rises with attitude",
      stations.filter(_.surface == "wing").forall(_.clPerDegree > 0))
    // A fin is a surface in the vertical plane: nothing it does answers the angle of attack.
    check("the fin's does not",
      stations.filter(_.surface == "fin").forall(s => math.abs(s.clPerDegree) < 0.002))
    check("the two halves of a mirrored surface load identically",
      stations.filter(s => s.surface == "wing" && !s.mirrored).forall { left =>
        stations.exists(right => right.surface == "wing" && right.mirrored &&
          math.abs(right.station - left.station) < 1e-4 &&
          math.abs(right.clPerDegree - left.clPerDegree) < 1e-4)
      })

    println("where each wing works hardest, and what the taper does to it")
    def criticalWing(calc: AvlCalculation): (String, Double, Double) = {
      val all = WingMaximumLift.stations(loadingOf(calc), 1.0)
      val at = WingMaximumLift.onset(all.map(s => StationLimits(s, Some(UniformSectionLimit), None))).right.get
      val curve = calc.getAlphaSweep.asScala.toSeq.map(p => (p.getAlphaDeg.toDouble, p.getCl.toDouble))
      (at.station.surface, math.abs(at.station.station) / (TestAircraft.Span / 2.0),
        WingMaximumLift.liftAt(curve, at.alphaDeg).get)
    }

    val tapered = run(props, TestAircraft.tapered())
    val (straightSurface, straightAt, straightClMax) = criticalWing(straight)
    val (taperedSurface, taperedAt, taperedClMax) = criticalWing(tapered)
    println(f"  rectangular: $straightSurface%s reaches $UniformSectionLimit%.2f first, at " +
      f"${100 * straightAt}%.0f %% of the semispan, with the aircraft at CL $straightClMax%.3f " +
      f"(${straightClMax / UniformSectionLimit}%.3f of the section limit)")
    println(f"  4:1 tapered: $taperedSurface%s reaches $UniformSectionLimit%.2f first, at " +
      f"${100 * taperedAt}%.0f %% of the semispan, with the aircraft at CL $taperedClMax%.3f " +
      f"(${taperedClMax / UniformSectionLimit}%.3f of the section limit)")
    check("it is the wing that gives up on both, not the tail or the fin",
      straightSurface == "wing" && taperedSurface == "wing")
    check("a rectangular wing gives up inboard, which is what makes one docile", straightAt < 0.25)
    check("a tapered wing gives up outboard, out where the ailerons are", taperedAt > 0.5)
    check("and further out than the rectangular one", taperedAt > straightAt)
    // The whole reason a constant cannot stand in for a loading: two wings of the same span, area and
    // aerofoil use different fractions of what their sections can do. The 0.9 this replaces is within a
    // percent of right for one of them and 6 % out on the other, and nothing about either wing says which.
    check("the two wings use different fractions of their sections' lift",
      math.abs(straightClMax / UniformSectionLimit - taperedClMax / UniformSectionLimit) > 0.02)
    check("both are below the section limit, because no wing loads uniformly",
      straightClMax < UniformSectionLimit && taperedClMax < UniformSectionLimit)

    println(if (ok) "SPANWISE_LOADING_OK" else "SPANWISE_LOADING_FAIL")
    if (!ok) sys.exit(1)
  }
}
