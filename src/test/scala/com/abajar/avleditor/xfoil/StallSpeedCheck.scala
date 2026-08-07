/*
 * The whole chain, end to end and for real: AVL for the spanwise loading, XFOIL for what each section can
 * do, and a stall speed out of the two.
 *
 * It needs both binaries and it is the slowest check in the project, which is the honest cost of the only
 * number in the editor that AVL cannot produce. What it asserts is internal consistency and shape — that
 * lift equals weight at the speed it reports, that the wing uses less than its sections offer, that the
 * Reynolds iteration settled — plus every refusal by name, because a stall speed decides a MIL-F-8785C
 * Level and a plausible invented one would be worse than none.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.xfoil.StallSpeedCheck"
 */
package com.abajar.avleditor.xfoil

import com.abajar.avleditor.{AvlManager, TestAircraft, XfoilManager}
import com.abajar.avleditor.avl.connectivity.AvlRunner
import com.abajar.avleditor.avl.runcase.AvlCalculation
import com.abajar.avleditor.crrcsim.CRRCSim
import java.util.Properties
import scala.collection.JavaConverters._

object StallSpeedCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private def run(props: Properties, model: CRRCSim): AvlCalculation =
    new AvlRunner(props.getProperty("avl.path"), model.getAvl, model.getOriginPath, 45f, 20f)
      .getCalculation()

  def main(args: Array[String]): Unit = {
    val props = new Properties()
    val avlThere = AvlManager.ensureAvlAvailable(props)
    XfoilManager.ensureXfoilAvailable(props)
    val xfoil = XfoilManager.usable(props)

    println("XFOIL has to actually run, not merely exist")
    xfoil match {
      case Left(why) => println("  " + why)
      case Right(path) => println("  " + path)
    }
    // A file with its executable bit set is not a working XFOIL: the published Linux build can need a
    // newer C library than the machine has, and then every polar comes back empty — which reads exactly
    // like an aerofoil that never stalls.
    check("a path that is not there is refused by name", {
      val absent = new Properties(); absent.setProperty("xfoil.path", "/nonexistent/xfoil")
      XfoilManager.usable(absent).left.exists(_.contains("nothing there"))
    })
    check("no path at all is refused by name, and says what XFOIL is for",
      XfoilManager.usable(new Properties()).left.exists(_.contains("not configured")))
    check("something that is not XFOIL is refused rather than believed", {
      val notXfoil = new Properties(); notXfoil.setProperty("xfoil.path", "/bin/true")
      XfoilManager.usable(notXfoil).left.exists(_.contains("did not load a NACA 0012"))
    })

    if (!avlThere || xfoil.isLeft) {
      println("  AVL and a working XFOIL are both needed for the rest of this check")
      println(if (ok) "STALL_SPEED_OK" else "STALL_SPEED_FAIL")
      if (!ok) sys.exit(1)
      return
    }
    val xfoilPath = xfoil.right.get

    println("the check's own aircraft, all the way through")
    val model = TestAircraft.conventional()
    val calc = run(props, model)
    val result = StallAnalysis.analyse(model.getAvl, calc, xfoilPath, model.getOriginPath)
    result match {
      case Left(why) => println("  refused: " + why)
      case Right(stall) =>
        println(f"  CLmax ${stall.clMax}%.3f at ${stall.alphaDeg}%.2f deg, " +
          f"Vs ${stall.stallSpeedMetresPerSecond}%.2f m/s")
        println(f"  first to give up: ${stall.critical.station.describe}%s, against a section limit of " +
          f"${stall.critical.sectionLimit}%.3f at Re ${stall.reynoldsAtCriticalSection}%.0f")
        stall.notes.foreach(note => println("  note: " + note))
    }
    check("it comes back with a stall", result.isRight)

    result.right.foreach { stall =>
      val config = calc.getConfiguration
      val weight = config.getAnalysisMassKg * 9.80665
      val area = config.getReferenceAreaSquareMetres
      val density = config.getAirDensity
      val lift = 0.5 * density * stall.stallSpeedMetresPerSecond * stall.stallSpeedMetresPerSecond *
        area * stall.clMax
      println(f"  at that speed the wing makes $lift%.3f N and the aircraft weighs $weight%.3f N")
      check("lift equals weight there, which is what a stall speed means",
        math.abs(lift - weight) / weight < 1e-6)
      check("the wing gives up before its sections do",
        stall.clMax < stall.critical.sectionLimit && stall.clMax > 0.0)
      check("the attitude it stalls at is one AVL measured",
        calc.getAlphaSweep.asScala.map(_.getAlphaDeg.toDouble).min <= stall.alphaDeg &&
          stall.alphaDeg <= calc.getAlphaSweep.asScala.map(_.getAlphaDeg.toDouble).max)
      // The Reynolds number the sections were read at has to be the one the aircraft is flying at when it
      // stalls, or the polars belong to a different aeroplane. That is what the iteration is for.
      val expected = StandardAir.reynolds(density, stall.stallSpeedMetresPerSecond,
        stall.critical.station.chordMetres).get
      println(f"  the critical section was read at Re ${stall.reynoldsAtCriticalSection}%.0f, and the " +
        f"aircraft stalls at Re $expected%.0f")
      check("the Reynolds iteration settled on the speed it reports",
        math.abs(stall.reynoldsAtCriticalSection / expected - 1.0) < 0.02)
      check("and it says so rather than leaving the reader to check", stall.notes.forall(!_.contains("still moving")))

      // Where the aircraft stalls is written onto the calculation, which is what the flying-qualities row
      // reads. A run without the analysis has to be distinguishable from one with it.
      println("what reaches the report")
      config.setStall(stall.stallSpeedMetresPerSecond.toFloat, stall.clMax.toFloat,
        stall.critical.station.describe)
      check("the stall speed reaches the calculation", config.getStallSpeedMetresPerSecond > 0f)
      check("with nothing claiming it is missing", config.getStallProblem == null)
      config.setStallProblem("nothing ran")
      check("and a refusal clears the speed rather than leaving a stale one",
        config.getStallSpeedMetresPerSecond == 0f && config.getStallProblem != null)
    }

    println("a heavier aeroplane stalls faster")
    val heavy = TestAircraft.conventional()
    heavy.getAvl.getGeometry.getMasses.asScala.foreach(m => m.setMass(m.getMass * 2f))
    heavy.calculate()
    val heavyResult = StallAnalysis.analyse(heavy.getAvl, run(props, heavy), xfoilPath, heavy.getOriginPath)
    (result.right.toOption, heavyResult.right.toOption) match {
      case (Some(light), Some(loaded)) =>
        println(f"  ${light.stallSpeedMetresPerSecond}%.2f m/s at one weight, " +
          f"${loaded.stallSpeedMetresPerSecond}%.2f m/s at twice it")
        check("twice the weight is about sqrt(2) times the stall speed",
          math.abs(loaded.stallSpeedMetresPerSecond / light.stallSpeedMetresPerSecond - math.sqrt(2.0)) < 0.1)
      case _ => check("the heavier aeroplane also came back with a stall", false)
    }

    println("what it refuses to answer")
    val noAerofoil = TestAircraft.conventional()
    noAerofoil.getAvl.getGeometry.getSurfaces.get(0).getSections.asScala.foreach { s =>
      s.setNACA(""); s.setAFILE("")
    }
    check("a section that states no aerofoil is named, not guessed at",
      StallAnalysis.analyse(noAerofoil.getAvl, calc, xfoilPath, noAerofoil.getOriginPath)
        .left.exists(_.contains("neither a NACA number nor an aerofoil file")))

    val noXfoil = StallAnalysis.analyse(model.getAvl, calc, "/nonexistent/xfoil", model.getOriginPath)
    check("no XFOIL is named, and says what XFOIL is for",
      noXfoil.left.exists(w => w.contains("XFOIL") && w.contains("inviscid")))

    val noLoading = run(props, model)
    noLoading.getAlphaSweep.asScala.foreach(_.setStrips(new java.util.ArrayList()))
    check("no spanwise loading is named",
      StallAnalysis.analyse(model.getAvl, noLoading, xfoilPath, model.getOriginPath)
        .left.exists(_.contains("spanwise loading")))

    println(if (ok) "STALL_SPEED_OK" else "STALL_SPEED_FAIL")
    if (!ok) sys.exit(1)
  }
}
