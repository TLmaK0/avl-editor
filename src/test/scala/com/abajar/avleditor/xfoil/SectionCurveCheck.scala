/*
 * The section data the decambering feeds on, out of real XFOIL runs, and what it is honest about.
 *
 * The iterated decambering evaluates the known section curve at every station at every iteration (Eq. 10,
 * p. 7 of AIAA 2003-1097), so the curve — not just its peak — is an input to the whole method. Three things
 * have to be true of it and only XFOIL can say whether they are:
 *
 *   - the wing's aerofoil gives up somewhere inside the sweep, and the curve is a viscous measurement below
 *     that and **declares itself not to be one above it**, because XFOIL's coupled formulation does not model
 *     massive separation;
 *   - a model tailplane's section, at its own Reynolds number, comes back **still climbing** — which is a real
 *     answer, not an error, and must be reported as having no known limit rather than credited with a stall at
 *     the top of the sweep;
 *   - no value ever comes back without saying where it came from.
 *
 * It also measures the shape behind issue #43 on the two Reynolds numbers that found it: a NACA 0010 at
 * Re 59,429 and at Re 75,000, where a bursting laminar bubble produces a peak that is not a stall. The
 * classification belongs to #43; what this asserts is that the curve *reports* the recovery instead of
 * swallowing it.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.xfoil.SectionCurveCheck"
 */
package com.abajar.avleditor.xfoil

import com.abajar.avleditor.XfoilManager
import java.util.Properties

object SectionCurveCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** The check aircraft's wing section, and roughly the Reynolds it flies its 0.20 m chord at. */
  private val WingAerofoil = "2412"
  private val WingReynolds = 190000.0
  /** Its tailplane's, at the two Reynolds numbers issue #43 was measured at. */
  private val TailAerofoil = "0010"
  private val TailReynolds = Seq(59429.0, 75000.0)

  /**
   * A polar built by hand, to test the curve's own arithmetic without spending XFOIL on it: a straight lift
   * line at 0.1 per degree from -4 to 14 deg, an unambiguous peak at 15, and a fall past it. One degree of
   * step throughout, so the curve's own idea of what counts as a gap is this polar's spacing and not a
   * constant borrowed from somewhere else.
   */
  private def madeUpPolar(): Seq[XfoilPolarPoint] =
    ((-4 to 14).map(a => XfoilPolarPoint(a.toFloat, (0.1 * a).toFloat, 0.01f, 0.008f,
      (-0.05 - 0.001 * a).toFloat)) ++
      Seq(XfoilPolarPoint(15f, 1.45f, 0.02f, 0.015f, -0.07f),
          XfoilPolarPoint(16f, 1.20f, 0.05f, 0.04f, -0.09f),
          XfoilPolarPoint(17f, 1.10f, 0.08f, 0.07f, -0.11f))).toSeq

  /** `cl` is stored as a float, so a value interpolated between two of them agrees to about that. */
  private val FloatTolerance = 1e-6

  def main(args: Array[String]): Unit = {
    println("the curve's own arithmetic, on a polar built by hand")
    val curve = SectionCurve.fromPolar(madeUpPolar(), 150000.0, "a made-up section").right.get
    check("it found the stall at the peak, not at the top of the sweep", curve.stallAlphaDeg == Some(15.0))
    check("and is a measurement only up to there", curve.validToDeg == 15.0)

    val inside = curve.cl(5.0)
    check("a value between two converged points is measured", inside.isMeasured)
    check("and interpolated linearly between them", math.abs(inside.value - 0.5) < FloatTolerance)

    val pastStall = curve.cl(16.5)
    check("past the stall the value is XFOIL's but is not called a measurement",
      pastStall.provenance == PastXfoilValidity && !pastStall.isMeasured)
    check("and it is still the right number", math.abs(pastStall.value - 1.15) < FloatTolerance)

    val beyond = curve.cl(25.0)
    check("beyond anything XFOIL converged at, the curve holds its last point and says by how much",
      beyond.provenance == HeldAtLastPoint(8.0) && math.abs(beyond.value - 1.10) < FloatTolerance)
    val below = curve.cl(-10.0)
    check("and the same at the bottom", below.provenance == HeldAtLastPoint(6.0))

    // A hole in the polar is where the decambering will be looking, so it cannot pass as a neighbourly step.
    val holed = SectionCurve.fromPolar(
      madeUpPolar().filterNot(p => p.alpha > 8.5f && p.alpha < 12.5f), 150000.0, "a holed section").right.get
    val acrossTheHole = holed.cl(11.0)
    check("a value interpolated across a gap says so, with the gap's width",
      acrossTheHole.provenance == AcrossAGap(5.0))
    check("a value between real neighbours in the same polar does not", holed.cl(3.0).isMeasured)

    check("a polar with too few points is refused rather than curved",
      SectionCurve.fromPolar(madeUpPolar().take(3), 150000.0, "a stub").isLeft)
    check("and an empty one names the aerofoil in the refusal",
      SectionCurve.fromPolar(Seq.empty, 150000.0, "nothing at all").left.get.contains("nothing at all"))

    println("cm comes along with it, which step 4's second decambering variable needs")
    check("it is sampled the same way and carries the same provenance",
      curve.cm(5.0).isMeasured && curve.cm(16.5).provenance == PastXfoilValidity)
    check("and it is a different number from cl", math.abs(curve.cm(5.0).value - curve.cl(5.0).value) > 0.1)

    val props = new Properties()
    if (!XfoilManager.ensureXfoilAvailable(props)) {
      println("  XFOIL is not available here, so no real aerofoil has been measured by this run.")
      println("SECTION_CURVE_SKIPPED")
      if (!ok) sys.exit(1)
      return
    }
    XfoilManager.usable(props) match {
      case Left(why) =>
        // A file with its executable bit set is not a working XFOIL: the published Linux binary needs a newer
        // C library than Ubuntu 22.04 has and dies at the loader, and every polar then comes back empty —
        // which reads exactly like an aerofoil that never stalls.
        println("  XFOIL is present but not usable here: " + why)
        println("SECTION_CURVE_SKIPPED")
        if (!ok) sys.exit(1)
        return
      case Right(_) =>
    }
    val runner = new XfoilRunner(props.getProperty("xfoil.path"))

    def measured(code: String, reynolds: Double): Either[String, SectionCurve] = {
      val polar = runner.computePolar(NacaAirfoil(code), reynolds, 0.0,
        SectionStall.AlphaStartDeg, SectionStall.AlphaEndDeg, SectionStall.AlphaStepDeg,
        iterations = 200, timeoutSeconds = 60)
      SectionCurve.fromPolar(polar, reynolds, s"NACA $code")
    }

    println(f"the wing's own section, NACA $WingAerofoil%s at Re ${WingReynolds.toLong}%d")
    measured(WingAerofoil, WingReynolds) match {
      case Left(why) =>
        check("the wing's section gave a curve: " + why, cond = false)
      case Right(wing) =>
        println("  " + wing.validityStatement)
        check("XFOIL showed it giving up inside the sweep", wing.stallAlphaDeg.isDefined)
        check("the peak is a real lift coefficient for a cambered section at this Reynolds",
          wing.peakCl > 1.0 && wing.peakCl < 1.8)
        check("it is a measurement below the stall", wing.cl(4.0).isMeasured)
        check("and declares itself not one above it",
          wing.stallAlphaDeg.forall(a => wing.cl(a + 2.0).provenance == PastXfoilValidity))
        check("the lift rises with attitude where it is attached",
          wing.cl(0.0).value < wing.cl(6.0).value)
        // The plateau this is about: the one source in this Reynolds range reports post-stall CL of 1.18-1.31
        // against XFOIL's 0.74-0.77, so XFOIL probably underestimates. Reported, never quietly relied on.
        val plateau = wing.cl(wing.highestMeasuredAlphaDeg).value
        println(f"  at the top of the sweep XFOIL holds cl $plateau%.3f, against the 1.18-1.31 the only " +
          f"measured source in this Reynolds range reports past the stall")
        check("which is a discrepancy this run states rather than buries", plateau < 1.18)
    }

    println(f"the tailplane's section, NACA $TailAerofoil%s, at the two Reynolds numbers of issue #43")
    TailReynolds.foreach { reynolds =>
      measured(TailAerofoil, reynolds) match {
        case Left(why) =>
          println(f"  Re ${reynolds.toLong}%d: no curve — $why%s")
          check(f"a refusal at Re ${reynolds.toLong}%d names the aerofoil", why.contains("NACA"))
        case Right(tail) =>
          println("  " + tail.validityStatement)
          tail.stallAlphaDeg match {
            case None =>
              // The honest answer for a thin symmetric section at model Reynolds: XFOIL does not model the
              // separation that would end it, so it climbs to the top of the sweep.
              check(f"Re ${reynolds.toLong}%d has no known limit, and the curve says so rather than " +
                "crediting it with a stall at the top of the sweep",
                tail.validToDeg == tail.highestMeasuredAlphaDeg)
            case Some(alpha) =>
              println(f"  a peak at $alpha%.1f deg, with the curve recovering " +
                f"${tail.recoveryAfterPeak}%.3f of cl afterwards")
              // Whether that peak is a stall or a bursting laminar bubble is issue #43's question. What is
              // asserted here is that the evidence is on the record rather than swallowed.
              check(f"Re ${reynolds.toLong}%d reports the recovery after its peak, which is what tells a " +
                "bubble from a stall", tail.recoveryAfterPeak >= 0.0)
          }
      }
    }

    println(if (ok) "SECTION_CURVE_OK" else "SECTION_CURVE_FAIL")
    if (!ok) sys.exit(1)
  }
}
