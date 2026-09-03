/*
 * Telling a section's stall from a laminar separation bubble bursting, which used to be read as one.
 *
 * A thin section at the Reynolds numbers a model aeroplane flies at grows a laminar separation bubble that
 * bursts and then reattaches: the polar rises, drops sharply, and climbs again for the rest of the sweep.
 * The global maximum before that drop satisfied every test `SectionStall` applied — it is the largest `cl`
 * in the polar, and there are far more than two converged points past it — so it was accepted as the
 * section's `clmax`. On the check aircraft's own tailplane section that is a stall declared at **8.5 deg**,
 * which would make `WingMaximumLift.onsetBySurface` name the tailplane as the surface that gives up first
 * on an ordinary model, and read the whole aircraft's maximum lift off it. Issue #43.
 *
 * <b>This check needs no XFOIL, deliberately.</b> What is under test is the arithmetic of reading a polar,
 * and that is right or wrong for reasons that have nothing to do with which aerofoil produced the numbers.
 * Building the polars here makes it deterministic and makes it run on a machine with no XFOIL on it —
 * which matters, because a check that excuses itself when a tool is missing reports success having measured
 * nothing. The shapes come from six real XFOIL polars measured in #43, and the figures each one reproduces
 * are named beside it.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.xfoil.BubbleNotStallCheck"
 */
package com.abajar.avleditor.xfoil

object BubbleNotStallCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def point(alphaDeg: Double, cl: Double) =
    XfoilPolarPoint(alphaDeg.toFloat, cl.toFloat, 0.02f, 0.004f, -0.05f)

  /** A polar from a list of `(alpha, cl)` corners, filled in at the half-degree steps XFOIL is asked for. */
  private def polarThrough(corners: Seq[(Double, Double)]): Seq[XfoilPolarPoint] = {
    val sorted = corners.sortBy(_._1)
    val steps = (0 to ((sorted.last._1 - sorted.head._1) * 2).toInt).map(i => sorted.head._1 + i * 0.5)
    steps.map { alpha =>
      val (below, above) = sorted.partition(_._1 <= alpha)
      val cl =
        if (below.isEmpty) sorted.head._2
        else if (above.isEmpty) sorted.last._2
        else {
          val (a0, c0) = below.last
          val (a1, c1) = above.head
          if (a1 == a0) c0 else c0 + (c1 - c0) * (alpha - a0) / (a1 - a0)
        }
      point(alpha, cl)
    }
  }

  /** NACA 2412 at Re 190,000: rises to 1.324 at 14 deg, falls to 0.633, and creeps back 4 % of the fall. */
  private val genuineStall = polarThrough(Seq(
    (-8.0, -0.55), (0.0, 0.26), (10.0, 1.18), (14.0, 1.3237), (17.5, 0.6333), (20.0, 0.6625)))

  /** NACA 2412 at Re 500,000: falls 0.113 after 15.5 deg and takes none of it back. */
  private val sharpStall = polarThrough(Seq(
    (-8.0, -0.60), (0.0, 0.28), (12.0, 1.29), (15.5, 1.3832), (18.0, 1.2702), (20.0, 1.2702)))

  /** NACA 0010 at Re 75,000: peaks at 8.5 deg, drops to 0.530, and reattaches to 0.604 — 25 % of the fall. */
  private val burstBubble = polarThrough(Seq(
    (-8.0, -0.80), (0.0, 0.0), (8.5, 0.8320), (10.0, 0.553), (15.0, 0.5301), (20.0, 0.6044)))

  /** NACA 0010 at Re 200,000: the same shape recovering 82 % of its fall, at a Reynolds a larger model flies. */
  private val strongRecovery = polarThrough(Seq(
    (-8.0, -0.90), (0.0, 0.0), (10.0, 0.9405), (12.5, 0.5620), (20.0, 0.8733)))

  /**
   * NACA 2412 at Re 100,000 — the check aircraft's own wing, at a speed it really flies: it stalls
   * genuinely at 12° and still takes back 26 % of its fall, which is what killed the recovery test.
   */
  private val lowReynoldsWing = polarThrough(Seq(
    (-8.0, -0.50), (0.0, 0.24), (9.0, 1.086), (12.0, 1.2760), (15.0, 0.8272), (20.0, 0.9461)))

  /** NACA 0010 at Re 59,429: still climbing at the top of the sweep, which is not a maximum at all. */
  private val stillClimbing = polarThrough(Seq(
    (-8.0, -0.80), (0.0, 0.0), (9.5, 0.636), (14.0, 0.722), (19.5, 0.8718)))

  private def refusalFor(polar: Seq[XfoilPolarPoint]): Option[String] =
    SectionStall.fromPolar(polar, 1e5).left.toOption

  def main(args: Array[String]): Unit = {

    println("a stall is still found, which is the half that must not break")
    check("the wing section's stall is read at its peak",
      SectionStall.fromPolar(genuineStall, 1.9e5).right.exists(d =>
        math.abs(d.clMax - 1.3237) < 1e-3 && math.abs(d.alphaAtClMaxDeg - 14.0) < 1e-6))
    check("and so is a sharp stall that takes nothing back",
      SectionStall.fromPolar(sharpStall, 5e5).right.exists(d =>
        math.abs(d.clMax - 1.3832) < 1e-3 && math.abs(d.alphaAtClMaxDeg - 15.5) < 1e-6))

    println("what the four candidate tests do on these same polars, all measured in #43")
    // Property 1, proposed in the issue: "the polar comes back above its peak". Inert — it never happens.
    check("neither bubble polar ever climbs back above its peak, so that test would accept both",
      Seq(burstBubble, strongRecovery).forall { p =>
        val peak = p.maxBy(_.cl); p.filter(_.alpha > peak.alpha).forall(_.cl <= peak.cl) })
    // Property 3: the recovery after the trough. It separates Reynolds number, not mechanism: the check
    // aircraft's own wing section recovers 26-34 % of its fall at Re 60,000-100,000 while stalling
    // genuinely, so a gate on it throws the wing's stall away. This is that fact, as a fixture.
    val (wingFall, wingRecovery) = SectionStall.shapeAfterPeak(lowReynoldsWing.sortBy(_.alpha))
    check("a genuine stall at a model's Reynolds number recovers as much as a bubble does",
      wingRecovery / wingFall > 0.20)
    check("so the wing's stall is still read, which a recovery gate would have refused",
      SectionStall.fromPolar(lowReynoldsWing, 1e5).right.exists(d =>
        math.abs(d.alphaAtClMaxDeg - 12.0) < 1e-6))
    // And the bubble's own peak is still reported, because nothing here can honestly refuse it yet.
    check("the bubble's peak is still returned, with no invented classification",
      SectionStall.fromPolar(burstBubble, 7.5e4).right.exists(d =>
        math.abs(d.alphaAtClMaxDeg - 8.5) < 1e-6))

    println("what is measured is the fall and the recovery after the trough, in that order")
    val (fall, recovery) = SectionStall.shapeAfterPeak(burstBubble.sortBy(_.alpha))
    println(f"    the bubble falls ${fall}%.3f and recovers ${recovery}%.3f — ${100 * recovery / fall}%.0f %%")
    check("the recovery is taken after the trough, not from the peak's own neighbour",
      recovery < fall && recovery > 0.0 && math.abs(recovery / fall - 0.25) < 0.06)
    val (stallFall, stallRecovery) = SectionStall.shapeAfterPeak(genuineStall.sortBy(_.alpha))
    check("a genuine stall at a high Reynolds number recovers a small fraction of a large fall",
      stallFall > 0.5 && stallRecovery / stallFall < 0.10)
    check("a stall that recovers nothing at all reports exactly zero",
      SectionStall.shapeAfterPeak(sharpStall.sortBy(_.alpha))._2 == 0.0)

    println("and the answer for a polar still rising is unchanged")
    check("a maximum at the top of the sweep is still refused as where the run stopped",
      refusalFor(stillClimbing).exists(_.contains("still rising")))

    println(if (ok) "BUBBLE_NOT_STALL_OK" else "BUBBLE_NOT_STALL_FAIL")
    if (!ok) sys.exit(1)
  }
}
