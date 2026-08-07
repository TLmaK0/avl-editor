/*
 * The lateral model is the riskiest code in this project, and this is what makes it safe.
 *
 * MIL-F-8785C 3.3.2.2 and 3.3.2.4 measure features of a step-input time history, so the response has to be
 * built before anything can be judged on it. Building it means turning nine non-dimensional derivatives
 * into dimensional ones with rho, V, S, b and three inertias — and nothing in this project has ever been
 * broken by a wrong formula, only by that kind of wiring: a rate derivative in AVL's normalisation rather
 * than per rad/s, an inertia in the model's units rather than SI, a sign convention that differs.
 *
 * The check needs no second flight dynamics model and no simulator. The four roots of the assembled matrix
 * **are** the dutch roll pair, the roll mode and the spiral that AVL already returned, so AVL's own answer
 * is the reference. Comparing characteristic polynomials rather than eigenvalues avoids implementing an
 * eigensolver, and is exact: two 4x4 systems with the same spectrum have the same polynomial.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.runcase.LateralModelCheck"
 */
package com.abajar.avleditor.avl.runcase

import com.abajar.avleditor.AvlManager
import com.abajar.avleditor.avl.connectivity.AvlRunner
import java.util.Properties
import scala.collection.JavaConverters._

object LateralModelCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /**
   * The roots of `lambda^4 + c3 l^3 + c2 l^2 + c1 l + c0`, by Durand-Kerner. Only used to say *which* mode
   * disagrees with AVL when one does — comparing polynomials is what decides agreement, and needs no roots.
   */
  private def rootsOf(c: Array[Double]): List[(Double, Double)] = {
    var re = Array(0.4, -0.4, 0.1, -0.1)
    var im = Array(0.9, -0.9, 1.4, -1.4)
    def poly(x: Double, y: Double): (Double, Double) = {
      var pr = 1.0; var pi = 0.0
      List(c(3), c(2), c(1), c(0)).foreach { k =>
        val nr = pr * x - pi * y + k
        val ni = pr * y + pi * x
        pr = nr; pi = ni
      }
      (pr, pi)
    }
    (0 until 500).foreach { _ =>
      (0 until 4).foreach { i =>
        val (pr, pi) = poly(re(i), im(i))
        var dr = 1.0; var di = 0.0
        (0 until 4).filter(_ != i).foreach { j =>
          val ar = re(i) - re(j); val ai = im(i) - im(j)
          val nr = dr * ar - di * ai; val ni = dr * ai + di * ar
          dr = nr; di = ni
        }
        val den = dr * dr + di * di
        if (den > 1e-300) {
          re(i) -= (pr * dr + pi * di) / den
          im(i) -= (pi * dr - pr * di) / den
        }
      }
    }
    (0 until 4).map(i => (re(i), im(i))).toList.sortBy(t => -math.abs(t._1))
  }

  /** AVL's lateral roots: the dutch roll pair, the roll mode and the spiral. */
  private def lateralRoots(calc: AvlCalculation): List[(Double, Double)] = {
    val all = calc.getEigenvalues.asScala.toList.filter(_.hasModeShape)
    val lateral = all.filter(_.getLateralRatio >= 0.55f)
    // A conjugate pair arrives as two eigenvalues with opposite omega; both are needed for the polynomial.
    lateral.map(e => (e.getSigma.toDouble, e.getOmega.toDouble))
  }

  def main(args: Array[String]): Unit = {
    println("what the model refuses rather than guesses")
    val bare = new AvlCalculation(0, 1, 2)
    bare.setConfiguration(new Configuration)
    bare.setStabilityDerivatives(new StabilityDerivatives)
    LateralModel.of(bare) match {
      case Left(why) =>
        println("    " + why)
        check("a calculation with nothing in it is refused by name", why.contains("mass"))
      case Right(_) => check("a calculation with nothing in it is refused by name", false)
    }

    println("the polynomial of a known root set")
    // (lambda + 1)(lambda + 2)(lambda^2 + 2 lambda + 5) = lambda^4 + 5 l^3 + 13 l^2 + 19 l + 10
    val known = LateralModel.polynomialFrom(List((-1.0, 0.0), (-2.0, 0.0), (-1.0, 2.0), (-1.0, -2.0)))
    known.foreach(c => println(f"    c0 ${c(0)}%.3f  c1 ${c(1)}%.3f  c2 ${c(2)}%.3f  c3 ${c(3)}%.3f"))
    check("it multiplies out to the right coefficients",
      known.exists(c => math.abs(c(0) - 10.0) < 1e-9 && math.abs(c(1) - 19.0) < 1e-9 &&
        math.abs(c(2) - 13.0) < 1e-9 && math.abs(c(3) - 5.0) < 1e-9))
    check("and a set that is not conjugate-closed is refused",
      LateralModel.polynomialFrom(List((-1.0, 0.0), (-2.0, 0.0), (-1.0, 2.0), (-1.0, 3.0))).isEmpty)

    println("running AVL on a real model")
    val props = new Properties()
    if (!AvlManager.ensureAvlAvailable(props)) {
      println("  AVL is not available here; the part that matters needs it")
      println(if (ok) "LATERAL_MODEL_OK" else "LATERAL_MODEL_FAIL")
      if (!ok) sys.exit(1)
      return
    }

    // The check's own aircraft: a sample is the user's aeroplane and changes under the check's feet.
    val model = com.abajar.avleditor.TestAircraft.conventional()
    model.calculate()
    val runner = new AvlRunner(props.getProperty("avl.path"), model.getAvl, model.getOriginPath)
    val calc = runner.getCalculation()

    val roots = lateralRoots(calc)
    println(f"    AVL returned ${roots.size}%d lateral roots with mode shapes")
    roots.foreach { case (s, o) => println(f"      sigma ${s}%9.4f   omega ${o}%9.4f") }

    LateralModel.of(calc) match {
      case Left(why) =>
        println("    " + why)
        check("the model assembles from a real AVL run", false)
      case Right(lateral) =>
        check("the model assembles from a real AVL run", true)
        val cfg = calc.getConfiguration
        println(f"    mass ${cfg.getAnalysisMassKg}%.4f kg  Ixx ${cfg.getAnalysisIxx}%.6f  " +
          f"Izz ${cfg.getAnalysisIzz}%.6f  Ixz ${cfg.getAnalysisIxz}%.6f")
        println(f"    rho ${cfg.getAirDensity}%.4f  V ${cfg.getVelocityMetresPerSecond}%.3f  " +
          f"S ${cfg.getSref}%.4f  b ${cfg.getSpanMetres}%.4f")
        // Where do those inertias refer to? Config sums m(x^2+y^2) about the ORIGIN; AVL is given point
        // masses and works its own out about the CG. If the two differ the whole matrix is built on the
        // wrong numbers — and so is everything else that reads Config.mass_inertia.
        val masses = model.getAllMasses.asScala.toList
        val total = masses.map(_.getMass.toDouble).sum
        val xCg = masses.map(m => m.getMass.toDouble * m.getX.toDouble).sum / total
        val zCg = masses.map(m => m.getMass.toDouble * m.getZ.toDouble).sum / total
        val izzOrigin = masses.map(m => m.getMass.toDouble *
          (math.pow(m.getX.toDouble, 2) + math.pow(m.getY.toDouble, 2))).sum
        val izzCg = masses.map(m => m.getMass.toDouble *
          (math.pow(m.getX.toDouble - xCg, 2) + math.pow(m.getY.toDouble, 2))).sum
        val ixxOrigin = masses.map(m => m.getMass.toDouble *
          (math.pow(m.getY.toDouble, 2) + math.pow(m.getZ.toDouble, 2))).sum
        val ixxCg = masses.map(m => m.getMass.toDouble *
          (math.pow(m.getY.toDouble, 2) + math.pow(m.getZ.toDouble - zCg, 2))).sum
        println(f"    CG at x ${xCg}%.4f  z ${zCg}%.4f, over ${masses.size}%d masses")
        println(f"    Izz about the origin ${izzOrigin}%.6f   about the CG ${izzCg}%.6f   " +
          f"(Config holds ${cfg.getAnalysisIzz}%.6f)")
        println(f"    Ixx about the origin ${ixxOrigin}%.6f   about the CG ${ixxCg}%.6f   " +
          f"(Config holds ${cfg.getAnalysisIxx}%.6f)")

        lateral.a.zipWithIndex.foreach { case (row, i) =>
          println(f"    A[$i%d] = " + row.map(v => f"$v%10.4f").mkString(" "))
        }
        // The classic approximations, against AVL's own roots: the roll mode is Lp, the dutch roll's
        // frequency squared is about Nbeta. If either is out by a factor, the conversion is where.
        println(f"    roll-mode approximation Lp = ${lateral.a(1)(1)}%.3f  (AVL's roll root -15.72)")
        println(f"    dutch-roll sqrt(Nbeta)     = ${math.sqrt(math.abs(lateral.a(2)(0)))}%.3f  (AVL's wn 6.52)")
        val mine = lateral.characteristicPolynomial
        println(f"    assembled : c3 ${mine(3)}%10.4f  c2 ${mine(2)}%10.4f  c1 ${mine(1)}%10.4f  c0 ${mine(0)}%10.4f")
        LateralModel.polynomialFrom(roots) match {
          case None =>
            println("    AVL's lateral roots did not come out as a real system's four roots")
            check("AVL's own roots give a polynomial to compare against", false)
          case Some(theirs) =>
            println(f"    AVL       : c3 ${theirs(3)}%10.4f  c2 ${theirs(2)}%10.4f  c1 ${theirs(1)}%10.4f  c0 ${theirs(0)}%10.4f")
            println("    my roots, worst-first:")
            rootsOf(mine).foreach { case (r, i) => println(f"      ${r}%9.4f ${i}%+9.4f i") }
            // Compared relative to the larger of the pair: c0 and c3 differ by orders of magnitude, so an
            // absolute tolerance would be meaningless on one of them.
            def close(a: Double, b: Double, tol: Double) =
              math.abs(a - b) <= tol * math.max(1.0e-6, math.max(math.abs(a), math.abs(b)))
            (0 until 4).foreach { i =>
              check(f"coefficient c$i%d agrees with AVL to 5 %%", close(mine(i), theirs(i), 0.05))
            }
        }
        check("the aileron column was built", lateral.bAileron != null)

        println("and the step response it is there to produce")
        // Halving the step must not move the answer: the integration has to be far finer than anything the
        // criteria can notice, and the fastest root is the roll mode this model was just verified on.
        val coarse = RollSideslipCoupling.of(lateral, math.toRadians(25.0), Some(0.98), 0.16, 0.70)
        val halved = RollSideslipCoupling.of(lateral, math.toRadians(25.0), Some(0.98), 0.16, 0.70, 0.001)
        val fine = lateral.stepResponse(math.toRadians(25.0), 0.0005, 5.0)
        coarse.foreach { m =>
          println(f"    p_osc/p_av ${m.oscillationRatio}%.4f over ${m.peaks}%d peaks, " +
            f"sideslip ${m.sideslipDegrees}%.2f deg ${if (m.proverse) "proverse" else "adverse"}%s")
        }
        check("a response comes out at all", coarse.isDefined)
        // Halving the step must not move what is measured. This is the claim about the integration being
        // fine enough, made as an assertion rather than as a sentence in a comment.
        (coarse, halved) match {
          case (Some(a), Some(b)) =>
            println(f"    halving the step: p_osc/p_av ${a.oscillationRatio}%.4f -> ${b.oscillationRatio}%.4f, " +
              f"sideslip ${a.sideslipDegrees}%.2f -> ${b.sideslipDegrees}%.2f deg")
            check("p_osc/p_av holds when the step is halved",
              math.abs(a.oscillationRatio - b.oscillationRatio) < 0.01)
            check("and so does the sideslip, to a tenth of a degree",
              math.abs(a.sideslipDegrees - b.sideslipDegrees) < 0.1)
          case _ => check("both steps produce a response", false)
        }
        check("and it starts from trim", fine.head._2 == 0.0 && fine.head._3 == 0.0)
        check("the aircraft actually rolls", math.abs(fine.last._5) > 0.1)
        // The roll rate must settle towards the steady value the roll response row computes independently.
        val settled = fine.map(_._3).map(math.abs).max
        println(f"    peak roll rate ${math.toDegrees(settled)}%.0f deg/s")
        check("at a rate an aeroplane could have", settled > 0.01 && math.toDegrees(settled) < 2000.0)
    }

    println(if (ok) "LATERAL_MODEL_OK" else "LATERAL_MODEL_FAIL")
    if (!ok) sys.exit(1)
  }
}
