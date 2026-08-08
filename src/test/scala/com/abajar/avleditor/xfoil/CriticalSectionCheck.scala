/*
 * The arithmetic that decides where an aeroplane stalls, checked without AVL and without XFOIL.
 *
 * Everything here is a property rather than a number: which station goes first, which way a threshold
 * moves when the aircraft changes, and — as much as anything — what the analysis refuses to answer. A
 * stall speed is about to decide a MIL-F-8785C Level, so a plausible one invented where the measurement
 * ran out would be worse than none.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.xfoil.CriticalSectionCheck"
 */
package com.abajar.avleditor.xfoil

import com.abajar.avleditor.avl.runcase.StripForce

object CriticalSectionCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** Every strip is this wide, so its area follows its chord as a real strip's does. */
  private val StripWidth = 0.05

  /** A strip whose local lift is exactly `intercept + slope * alpha`, sampled at a list of attitudes. */
  private def loading(alphas: Seq[Double])(strips: Seq[(String, Int, Double, Double, Double, Double)]
                     ): Seq[(Double, Seq[StripForce])] =
    alphas.map { alpha =>
      (alpha, strips.map { case (surface, index, station, chord, intercept, slope) =>
        new StripForce(surface, false, index, station.toFloat, chord.toFloat,
          (chord * StripWidth).toFloat, (intercept + slope * alpha).toFloat)
      })
    }

  private val Attitudes = Seq(-10.0, -5.0, 0.0, 5.0, 10.0, 15.0, 20.0)

  def main(args: Array[String]): Unit = {
    println("one straight line per station, fitted across the attitudes")
    val uniform = WingMaximumLift.stations(loading(Attitudes)(Seq(
      ("wing", 1, 0.1, 0.2, 0.20, 0.070),
      ("wing", 2, 0.3, 0.2, 0.20, 0.070),
      ("wing", 3, 0.5, 0.2, 0.20, 0.070))), 1.0)
    check("every station comes back", uniform.length == 3)
    // A strip's cl arrives from AVL as a single-precision number, so "exactly" is to about 1e-7 of it.
    check("the line is recovered exactly",
      uniform.forall(s => math.abs(s.clPerDegree - 0.070) < 1e-6 && math.abs(s.clAtZeroDeg - 0.20) < 1e-6))
    check("a straight loading reports no residual", uniform.forall(_.worstResidual < 1e-6))
    check("the chord is converted to metres",
      WingMaximumLift.stations(loading(Attitudes)(Seq(("wing", 1, 0.1, 20.0, 0.2, 0.07))), 0.01)
        .head.chordMetres == 0.2)

    // A loading that is not straight has to say so, because "one line per station" is a claim about the
    // aircraft (NACA TR 572's additional distribution "maintains the same form") and not a licence.
    val bent = Attitudes.map { alpha =>
      (alpha, Seq(new StripForce("wing", false, 1, 0.1f, 0.2f, 0.01f,
        (0.20 + 0.070 * alpha + 0.05 * math.sin(alpha)).toFloat)))
    }
    check("a bent loading reports a residual", WingMaximumLift.stations(bent, 1.0).head.worstResidual > 0.01)

    println("which station gives up first")
    // Same aerofoil everywhere: the station carrying the most lift per degree reaches the limit first.
    val tipLoaded = WingMaximumLift.stations(loading(Attitudes)(Seq(
      ("wing", 1, 0.1, 0.2, 0.20, 0.060),
      ("wing", 2, 0.3, 0.2, 0.20, 0.070),
      ("wing", 3, 0.5, 0.2, 0.20, 0.085))), 1.0)
    val sameAerofoil = tipLoaded.map(s => StationLimits(s, Some(1.4), Some(-0.9)))
    val first = WingMaximumLift.onset(sameAerofoil)
    check("it is the hardest-worked station", first.right.exists(_.station.index == 3))
    check("and the attitude is where its line meets its limit",
      first.right.exists(o => math.abs(o.alphaDeg - (1.4 - 0.20) / 0.085) < 1e-4))
    check("stalling upwards is reported as such", first.right.exists(!_.downward))

    // A stronger aerofoil out at the tip moves the stall inboard, which is what washout and a thicker
    // root section are for. Nothing about the loading changed.
    val strongerTip = tipLoaded.map(s =>
      StationLimits(s, Some(if (s.index == 3) 1.9 else 1.4), Some(-0.9)))
    check("a stronger tip section moves the stall inboard",
      WingMaximumLift.onset(strongerTip).right.exists(_.station.index == 2))

    println("a fin, a tailplane, and the things that are not the wing")
    // A vertical surface's lift does not answer attitude at all. It must never be the critical station,
    // and it must not need a special case to be kept out of the way.
    val withFin = WingMaximumLift.stations(loading(Attitudes)(Seq(
      ("wing", 1, 0.3, 0.2, 0.20, 0.070),
      ("fin", 1, 0.0, 0.1, 0.00, 0.000))), 1.0)
    check("a surface whose lift ignores attitude is never critical",
      WingMaximumLift.onset(withFin.map(s => StationLimits(s, Some(1.4), Some(-0.9))))
        .right.exists(_.station.surface == "wing"))

    // A tailplane carrying a download stalls the other way up, and only if its aerofoil has a negative
    // stall to be judged against.
    val download = WingMaximumLift.stations(loading(Attitudes)(Seq(
      ("wing", 1, 0.3, 0.2, 0.20, 0.070),
      ("tailplane", 1, 0.1, 0.1, -0.30, -0.090))), 1.0)
    check("a downward-loaded station is judged against its negative stall",
      WingMaximumLift.onset(download.map(s => StationLimits(s, Some(1.4), Some(-0.8))))
        .right.exists(o => o.station.surface == "tailplane" && o.downward))
    check("and left out when its aerofoil never stalled downwards in the sweep",
      WingMaximumLift.onset(download.map(s => StationLimits(s, Some(1.4), None)))
        .right.exists(o => o.station.surface == "wing" && !o.downward))

    println("which surface's stall is the aircraft's")
    // The defect this closes: the first station **anywhere** decided the aircraft's maximum lift. A canard
    // set at 25 degrees of incidence reaches its section limit almost at once — by design, that being what
    // a close-coupled canard is for — and the sample's stall came out at CL 0.311, which is not a stall.
    val canardAircraft = WingMaximumLift.stations(loading(Attitudes)(Seq(
      // A big wing carrying the lift, working up to its limit gradually.
      ("wing", 1, 0.3, 0.25, 0.20, 0.070),
      ("wing", 2, 0.6, 0.25, 0.18, 0.068),
      // A small canard already most of the way to its limit at zero attitude.
      ("canard", 1, 0.15, 0.10, 1.20, 0.060))), 1.0)
    val limitsOf = canardAircraft.map(s => StationLimits(s, Some(1.4), None))
    val perSurface = WingMaximumLift.onsetBySurface(limitsOf)
    println("  per surface: " + perSurface.toSeq.sortBy(_._1)
      .map { case (s, o) => f"$s%s -> " + o.right.map(x => f"${x.alphaDeg}%.2f deg").right.getOrElse("none") }
      .mkString(", "))
    check("each surface gets its own answer", perSurface.keySet == Set("wing", "canard"))
    check("and the canard gives up long before the wing",
      perSurface("canard").right.get.alphaDeg < perSurface("wing").right.get.alphaDeg)

    // Which one is the aircraft's is decided by a measurement — the share of the lift each is carrying —
    // and never by its name or by which is bigger.
    val shares = WingMaximumLift.liftShareBySurface(canardAircraft, 10.0)
    println("  lift share at 10 deg: " + shares.toSeq.sortBy(_._1)
      .map { case (s, f) => f"$s%s ${f * 100}%.0f %%" }.mkString(", "))
    check("the wing is carrying most of the lift", shares("wing") > shares("canard"))
    check("and the shares are a share, adding to one", math.abs(shares.values.sum - 1.0) < 1e-9)
    // A surface pushing down is not a candidate for carrying the aeroplane.
    val withDownload = WingMaximumLift.stations(loading(Attitudes)(Seq(
      ("wing", 1, 0.3, 0.25, 0.20, 0.070),
      ("tailplane", 1, 0.1, 0.10, -0.40, 0.020))), 1.0)
    check("a surface holding the nose up carries a negative share",
      WingMaximumLift.liftShareBySurface(withDownload, 0.0)("tailplane") < 0.0)
    check("with no lift anywhere, no surface is claimed to carry it",
      WingMaximumLift.liftShareBySurface(WingMaximumLift.stations(loading(Attitudes)(Seq(
        ("fin", 1, 0.0, 0.1, 0.0, 0.0))), 1.0), 5.0).forall(_._2 == 0.0))

    println("what it refuses to answer")
    check("nothing to match, nothing to say",
      WingMaximumLift.onset(Seq.empty).isLeft)
    check("a wing that reaches no limit in the measured range is refused, not extrapolated",
      WingMaximumLift.onset(uniform.map(s => StationLimits(s, Some(4.0), None)))
        .left.exists(_.contains("within the attitudes AVL measured")))
    check("a wing already past its limit at the bottom of the range is refused too",
      WingMaximumLift.onset(uniform.map(s => StationLimits(s, Some(-1.0), None)))
        .left.exists(_.contains("already at its aerofoil's limit")))
    check("a station whose lift ignores attitude entirely, and nothing else, is refused",
      WingMaximumLift.onset(withFin.filter(_.surface == "fin").map(s => StationLimits(s, Some(1.4), None)))
        .isLeft)
    // XFOIL does not always show an aerofoil giving up — a model tailplane at Re 60,000 comes back with a
    // polar still climbing. Such a station takes no part rather than being credited with an invented limit.
    check("a station whose aerofoil has no known limit takes no part",
      WingMaximumLift.onset(tipLoaded.map(s =>
        StationLimits(s, if (s.index == 3) None else Some(1.4), None)))
        .right.exists(_.station.index == 2))
    check("and if none of them has one, nothing is claimed",
      WingMaximumLift.onset(tipLoaded.map(s => StationLimits(s, None, None)))
        .left.exists(_.contains("nothing on this aircraft can be said to stall")))
    check("one attitude is not a line", WingMaximumLift.stations(loading(Seq(3.0))(Seq(
      ("wing", 1, 0.3, 0.2, 0.20, 0.070))), 1.0).isEmpty)
    // A strip that AVL answered for at some attitudes and not others has no line through it.
    val patchy = Seq(
      (0.0, Seq(new StripForce("wing", false, 1, 0.3f, 0.2f, 0.01f, 0.2f),
                new StripForce("wing", false, 2, 0.5f, 0.2f, 0.01f, 0.2f))),
      (5.0, Seq(new StripForce("wing", false, 1, 0.3f, 0.2f, 0.01f, 0.55f))))
    check("a station missing from an attitude is dropped rather than half-fitted",
      WingMaximumLift.stations(patchy, 1.0).map(_.index) == Seq(1))

    println("reading the lift coefficient at the stall off AVL's own curve")
    val curve = Seq((-5.0, 0.0), (0.0, 0.4), (5.0, 0.8), (10.0, 1.2))
    check("it interpolates", WingMaximumLift.liftAt(curve, 2.5).exists(v => math.abs(v - 0.6) < 1e-9))
    check("it lands on a measured point exactly",
      WingMaximumLift.liftAt(curve, 5.0).exists(v => math.abs(v - 0.8) < 1e-9))
    check("and never beyond the last one",
      WingMaximumLift.liftAt(curve, 12.0).isEmpty && WingMaximumLift.liftAt(curve, -6.0).isEmpty)

    println("the stall speed, as a shape rather than a number")
    val base = WingMaximumLift.stallSpeed(11.47, 1.225, 0.24, 1.2).get
    println(f"  11.47 N over 0.24 m2 at CLmax 1.2: $base%.3f m/s")
    def ratio(v: Option[Double]) = v.get / base
    check("four times the weight doubles it",
      math.abs(ratio(WingMaximumLift.stallSpeed(4 * 11.47, 1.225, 0.24, 1.2)) - 2.0) < 1e-9)
    check("four times the wing halves it",
      math.abs(ratio(WingMaximumLift.stallSpeed(11.47, 1.225, 4 * 0.24, 1.2)) - 0.5) < 1e-9)
    check("four times the maximum lift halves it",
      math.abs(ratio(WingMaximumLift.stallSpeed(11.47, 1.225, 0.24, 4 * 1.2)) - 0.5) < 1e-9)
    check("thinner air raises it",
      WingMaximumLift.stallSpeed(11.47, 0.9, 0.24, 1.2).get > base)
    check("lift equals weight there, which is what a stall speed means",
      math.abs(0.5 * 1.225 * base * base * 0.24 * 1.2 - 11.47) < 1e-9)
    check("nothing missing is filled in",
      Seq(WingMaximumLift.stallSpeed(0, 1.225, 0.24, 1.2),
          WingMaximumLift.stallSpeed(11.47, 0, 0.24, 1.2),
          WingMaximumLift.stallSpeed(11.47, 1.225, 0, 1.2),
          WingMaximumLift.stallSpeed(11.47, 1.225, 0.24, 0)).forall(_.isEmpty))

    println("the air, which the model does not state")
    println(f"  Sutherland at ${StandardAir.SeaLevelTemperatureK}%.2f K: ${StandardAir.DynamicViscosity}%.6e Pa s")
    // U.S. Standard Atmosphere 1976 eq. (51) at sea level. The constants are cited where they are defined;
    // this is the arithmetic they produce, which is the standard figure for air at 15 C.
    check("it is 1.7894e-5 Pa s", math.abs(StandardAir.DynamicViscosity - 1.7894e-5) < 1e-9)
    check("hotter air is thicker", StandardAir.dynamicViscosity(320.0) > StandardAir.DynamicViscosity)
    check("Reynolds is linear in every one of its three",
      math.abs(StandardAir.reynolds(2.45, 20, 0.2).get / StandardAir.reynolds(1.225, 20, 0.2).get - 2.0) < 1e-9 &&
      math.abs(StandardAir.reynolds(1.225, 40, 0.2).get / StandardAir.reynolds(1.225, 20, 0.2).get - 2.0) < 1e-9 &&
      math.abs(StandardAir.reynolds(1.225, 20, 0.4).get / StandardAir.reynolds(1.225, 20, 0.2).get - 2.0) < 1e-9)
    check("and refuses rather than returning zero",
      StandardAir.reynolds(1.225, 0, 0.2).isEmpty && StandardAir.reynolds(0, 20, 0.2).isEmpty &&
        StandardAir.reynolds(1.225, 20, 0).isEmpty)

    println("what a polar has to show before a stall is read off it")
    def point(alpha: Double, cl: Double) = XfoilPolarPoint(alpha.toFloat, cl.toFloat, 0.01f, 0.005f, -0.05f)
    val stalls = (0 to 20).map(i => point(i, if (i <= 14) 0.1 * i else 1.4 - 0.05 * (i - 14)))
    check("a polar that turns over gives its peak",
      SectionStall.fromPolar(stalls, 2e5).right.exists(d => math.abs(d.clMax - 1.4) < 1e-6 &&
        math.abs(d.alphaAtClMaxDeg - 14.0) < 1e-6))
    check("a polar still rising at the top of the sweep is refused",
      SectionStall.fromPolar((0 to 20).map(i => point(i, 0.1 * i)), 2e5)
        .left.exists(_.contains("still rising")))
    check("an empty polar is refused", SectionStall.fromPolar(Seq.empty, 2e5).isLeft)
    check("a handful of scattered points is refused",
      SectionStall.fromPolar(Seq(point(0, 0.2), point(1, 0.3)), 2e5).isLeft)
    check("a negative stall is only claimed when the polar turned over downwards too",
      SectionStall.fromPolar(stalls, 2e5).right.exists(_.clMin.isEmpty) &&
        SectionStall.fromPolar(
          ((-8) to 20).map(i => point(i, if (i < -5) -0.5 + 0.05 * (-5 - i)
                                         else if (i <= 14) 0.1 * i else 1.4 - 0.05 * (i - 14))), 2e5)
          .right.exists(_.clMin.isDefined))

    println(if (ok) "CRITICAL_SECTION_OK" else "CRITICAL_SECTION_FAIL")
    if (!ok) sys.exit(1)
  }
}
