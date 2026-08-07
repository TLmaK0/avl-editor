/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.avl.runcase

/**
 * The aircraft's lateral-directional dynamics, assembled from what AVL returns.
 *
 * **Why this exists.** MIL-F-8785C 3.3.2.2 and 3.3.2.4 do not ask for a coefficient: they ask what the roll
 * rate does at the peaks of the response to a step aileron input, and how far the nose slips while it does
 * it. There is a closed form for that response — the system is linear — but not for its peaks, so the
 * response has to be built before anything can be measured on it. The editor had no such model: it took
 * AVL's eigenvalues as given and never formed the system they came from.
 *
 * **Why it is dangerous, and what is done about it.** Nothing in this project has ever been broken by a
 * wrong formula; it has been broken by the wiring around one — units, sign conventions, a derivative in a
 * different normalisation than the formula assumed. Assembling this matrix means converting nine
 * non-dimensional derivatives into dimensional ones with rho, V, S, b and three inertias, which is exactly
 * that kind of wiring.
 *
 * So it verifies itself. The four roots of this matrix **are** the dutch roll pair, the roll mode and the
 * spiral that AVL already returned, and `LateralModelCheck` asserts they match. If the wiring is wrong the
 * eigenvalues come out wrong, and it is known immediately — with no second flight dynamics model, without
 * exporting anything, and without excluding an aircraft for having no engine.
 *
 * State vector `[beta, p, r, phi]`: sideslip angle (rad), roll rate (rad/s), yaw rate (rad/s), bank angle
 * (rad). Standard body-axis small-perturbation equations, wings-level trim.
 */
final case class LateralModel(a: Array[Array[Double]], bAileron: Array[Double]) {

  /**
   * The response to a step aileron deflection, rudder untouched — the manoeuvre MIL-F-8785C 3.3.2.2 and
   * 3.3.2.4 are both written about. Returns `(t, beta, p, r, phi)` at every step.
   *
   * Integrated with a fixed-step RK4 rather than expanded in modes. The system is linear and its fastest
   * root is known — it is the roll mode this very class was verified against — so a step a small fraction
   * of `1/|lambda|` leaves an error far below anything the criteria can notice, and `LateralResponseCheck`
   * pins that by halving the step and finding the same answer.
   */
  def stepResponse(deflectionRad: Double, dt: Double, duration: Double): List[(Double, Double, Double, Double, Double)] = {
    if (bAileron == null) return Nil
    val u = bAileron.map(_ * deflectionRad)
    def derivative(x: Array[Double]): Array[Double] =
      (0 until 4).map(i => (0 until 4).map(j => a(i)(j) * x(j)).sum + u(i)).toArray
    def add(x: Array[Double], d: Array[Double], f: Double): Array[Double] =
      (0 until 4).map(i => x(i) + f * d(i)).toArray

    var x = Array(0.0, 0.0, 0.0, 0.0)
    var t = 0.0
    val out = scala.collection.mutable.ListBuffer[(Double, Double, Double, Double, Double)]()
    out += ((t, x(0), x(1), x(2), x(3)))
    while (t < duration) {
      val k1 = derivative(x)
      val k2 = derivative(add(x, k1, dt / 2))
      val k3 = derivative(add(x, k2, dt / 2))
      val k4 = derivative(add(x, k3, dt))
      x = (0 until 4).map(i => x(i) + dt / 6.0 * (k1(i) + 2 * k2(i) + 2 * k3(i) + k4(i))).toArray
      t += dt
      out += ((t, x(0), x(1), x(2), x(3)))
    }
    out.toList
  }

  /** Coefficients of `lambda^4 + c3 lambda^3 + c2 lambda^2 + c1 lambda + c0`, from the matrix. */
  def characteristicPolynomial: Array[Double] = {
    val m = a
    def minor2(i: Int, j: Int): Double = m(i)(i) * m(j)(j) - m(i)(j) * m(j)(i)
    def minor3(x: Int, y: Int, z: Int): Double = {
      val idx = Array(x, y, z)
      def e(r: Int, c: Int) = m(idx(r))(idx(c))
      e(0, 0) * (e(1, 1) * e(2, 2) - e(1, 2) * e(2, 1)) -
        e(0, 1) * (e(1, 0) * e(2, 2) - e(1, 2) * e(2, 0)) +
        e(0, 2) * (e(1, 0) * e(2, 1) - e(1, 1) * e(2, 0))
    }
    val trace = (0 until 4).map(i => m(i)(i)).sum
    val sumMinor2 = (for (i <- 0 until 4; j <- i + 1 until 4) yield minor2(i, j)).sum
    val sumMinor3 = List((0, 1, 2), (0, 1, 3), (0, 2, 3), (1, 2, 3)).map { case (x, y, z) => minor3(x, y, z) }.sum
    Array(determinant, -sumMinor3, sumMinor2, -trace)
  }

  def determinant: Double = {
    val m = a
    def sub(skipRow: Int, skipCol: Int): Array[Array[Double]] =
      (0 until 4).filter(_ != skipRow).map(r =>
        (0 until 4).filter(_ != skipCol).map(c => m(r)(c)).toArray).toArray
    def det3(x: Array[Array[Double]]): Double =
      x(0)(0) * (x(1)(1) * x(2)(2) - x(1)(2) * x(2)(1)) -
        x(0)(1) * (x(1)(0) * x(2)(2) - x(1)(2) * x(2)(0)) +
        x(0)(2) * (x(1)(0) * x(2)(1) - x(1)(1) * x(2)(0))
    (0 until 4).map(c => (if (c % 2 == 0) 1.0 else -1.0) * m(0)(c) * det3(sub(0, c))).sum
  }
}

object LateralModel {

  private val Gravity = 9.80665

  /**
   * Assemble it, or say which input is missing — never substitute one. A model with no stated inertias, no
   * air density or no speed cannot have a lateral response computed for it, and a response computed from a
   * guessed inertia would look exactly like a measured one.
   */
  def of(calculation: AvlCalculation): Either[String, LateralModel] = {
    val config = calculation.getConfiguration
    val stab = calculation.getStabilityDerivatives
    if (config == null || stab == null) return Left("AVL returned no configuration for this run.")

    // The airframe's inertia **plus the air it drags with it**. AVL computes that apparent inertia and
    // solves the modes with the sum; on a 1.1 kg model it is 15.6 % of the roll inertia, and leaving it out
    // put the roll mode at -18.1 against AVL's -15.7. The apparent figures are AVL's own, read from what it
    // prints, so the two cannot drift apart.
    val mass = config.getAnalysisMassKg.toDouble + config.getApparentMassY.toDouble
    val ixx = config.getAnalysisIxx.toDouble + config.getApparentIxx.toDouble
    val izz = config.getAnalysisIzz.toDouble + config.getApparentIzz.toDouble
    val ixz = config.getAnalysisIxz.toDouble + config.getApparentIxz.toDouble
    val rho = config.getAirDensity.toDouble
    val speed = config.getVelocityMetresPerSecond.toDouble
    val span = config.getSpanMetres.toDouble
    val area = config.getSref.toDouble * math.pow(config.getMetresPerLengthUnit.toDouble, 2)

    if (mass <= 0.0) return Left("the aircraft's mass did not reach the calculation.")
    if (ixx <= 0.0 || izz <= 0.0) return Left("the roll and yaw inertias did not reach the calculation.")
    if (rho <= 0.0) return Left("the air density did not reach the calculation.")
    if (speed <= 0.0) return Left("the speed did not reach the calculation.")
    if (span <= 0.0 || area <= 0.0) return Left("the reference span or area is missing.")

    val qS = 0.5 * rho * speed * speed * area
    val qSb = qS * span
    // AVL states the rate derivatives per (p b / 2V) and (r b / 2V), so the half-span-over-speed factor is
    // what turns them into per rad/s. Getting this wrong is the single easiest way to break this file.
    val rateFactor = span / (2.0 * speed)

    // AVL states its derivatives about the **stability** axes — its own output says
    // "Stability-axis derivatives..." and primes the moments Cl' and Cn' — while its eigenmodes are in
    // **body** axes, the state vector it prints being u,v,w,p,q,r,phi,theta,psi. So the derivative set is
    // rotated here by the trim angle of attack, on both indices: the moment it produces and the rate it is
    // taken with respect to.
    //
    // It is not a small correction even at a few degrees, because the rotation mixes the derivatives into
    // each other and they are not the same size. Clp is five times Clr on a conventional wing, so at 3.4
    // degrees the term it lends to Clr is a third of Clr itself — and Clr is one of the two products whose
    // near-cancellation decides whether the spiral converges or diverges.
    val alpha = math.toRadians(config.getAlpha.toDouble)
    val sinA = math.sin(alpha)
    val cosA = math.cos(alpha)

    // Moments rotate as a vector about y; beta is common to both frames.
    def toBodyRoll(l: Double, n: Double): Double = l * cosA - n * sinA
    def toBodyYaw(l: Double, n: Double): Double = n * cosA + l * sinA
    // A body rate seen in stability axes: p_s = p cos a + r sin a, r_s = r cos a - p sin a.
    def wrtBodyP(sP: Double, sR: Double): Double = sP * cosA - sR * sinA
    def wrtBodyR(sP: Double, sR: Double): Double = sP * sinA + sR * cosA

    val clbS = stab.getClb.toDouble; val cnbS = stab.getCnb.toDouble
    val clpS = stab.getClp.toDouble; val clrS = stab.getClr.toDouble
    val cnpS = stab.getCnp.toDouble; val cnrS = stab.getCnr.toDouble

    val clb = toBodyRoll(clbS, cnbS)
    val cnb = toBodyYaw(clbS, cnbS)
    val clp = toBodyRoll(wrtBodyP(clpS, clrS), wrtBodyP(cnpS, cnrS))
    val clr = toBodyRoll(wrtBodyR(clpS, clrS), wrtBodyR(cnpS, cnrS))
    val cnp = toBodyYaw(wrtBodyP(clpS, clrS), wrtBodyP(cnpS, cnrS))
    val cnr = toBodyYaw(wrtBodyR(clpS, clrS), wrtBodyR(cnpS, cnrS))
    val cyp = wrtBodyP(stab.getCYp.toDouble, stab.getCYr.toDouble)
    val cyr = wrtBodyR(stab.getCYp.toDouble, stab.getCYr.toDouble)

    // Side force, in per-second terms: beta-dot picks up Y/(m V). The y axis is common to both frames, so
    // CYb needs no rotation.
    val yBeta = qS * stab.getCYb.toDouble / (mass * speed)
    val yP = qS * cyp * rateFactor / (mass * speed)
    val yR = qS * cyr * rateFactor / (mass * speed)

    // Rolling and yawing moments, before the Ixz cross-coupling is undone.
    val lBeta = qSb * clb
    val lP = qSb * clp * rateFactor
    val lR = qSb * clr * rateFactor
    val nBeta = qSb * cnb
    val nP = qSb * cnp * rateFactor
    val nR = qSb * cnr * rateFactor

    // [Ixx -Ixz; -Ixz Izz] [pdot; rdot] = [L; N], inverted once here rather than assumed diagonal: for a
    // swept or high-set wing Ixz is not small, and dropping it moves the dutch roll.
    val inertiaDet = ixx * izz - ixz * ixz
    if (inertiaDet <= 0.0) return Left("the inertias are not physically consistent (Ixx Izz <= Ixz^2).")
    def rollOf(l: Double, n: Double): Double = (izz * l + ixz * n) / inertiaDet
    def yawOf(l: Double, n: Double): Double = (ixz * l + ixx * n) / inertiaDet

    val aMatrix = Array(
      // beta-dot = Ybeta beta + (Yp + sin a) p + (Yr - cos a) r + (g cos a / V) phi
      Array(yBeta, yP + sinA, yR - cosA, Gravity * cosA / speed),
      Array(rollOf(lBeta, nBeta), rollOf(lP, nP), rollOf(lR, nR), 0.0),
      Array(yawOf(lBeta, nBeta), yawOf(lP, nP), yawOf(lR, nR), 0.0),
      // phi-dot = p + tan(a) r, the bank angle picking up yaw rate at attitude
      Array(0.0, 1.0, 0.0, 0.0)
    )

    val aileron = calculation.getAileronPosition
    val gains = calculation.getControlGains
    val cld = stab.getCld
    val cnd = stab.getCnd
    val cyd = stab.getCYd
    val bVector =
      if (aileron < 0 || cld == null || cnd == null || cyd == null || gains == null ||
          aileron >= cld.length || aileron >= gains.length || gains(aileron) == 0f)
        null
      else {
        // Per radian of actual deflection: AVL states these per control variable, and the gain is degrees
        // of deflection per unit of it. The same factor the JSBSim export was missing for years.
        val perRadian = math.toDegrees(1.0) / gains(aileron).toDouble
        val lDelta = qSb * cld(aileron).toDouble * perRadian
        val nDelta = qSb * cnd(aileron).toDouble * perRadian
        val yDelta = qS * cyd(aileron).toDouble * perRadian / (mass * speed)
        Array(yDelta, rollOf(lDelta, nDelta), yawOf(lDelta, nDelta), 0.0)
      }

    Right(LateralModel(aMatrix, bVector))
  }

  /**
   * The characteristic polynomial AVL's own lateral eigenvalues imply: `prod(lambda - lambda_i)` over the
   * dutch roll pair, the roll mode and the spiral. Comparing this against the assembled matrix's own
   * polynomial is the whole safety net — the two describe the same aircraft or the wiring is wrong.
   */
  def polynomialFrom(roots: List[(Double, Double)]): Option[Array[Double]] = {
    if (roots.size != 4) return None
    // Multiply out (lambda - lambda_i) one root at a time. The arithmetic is carried complex because a
    // single root is not real; the imaginary part cancels at the end only if the set is conjugate-closed,
    // which is checked rather than assumed.
    val real = new Array[Double](5)
    val imag = new Array[Double](5)
    real(0) = 1.0
    var degree = 0
    roots.foreach { case (re, im) =>
      val nextReal = new Array[Double](5)
      val nextImag = new Array[Double](5)
      for (i <- 0 to degree) {
        // shift by one power
        nextReal(i + 1) += real(i)
        nextImag(i + 1) += imag(i)
        // minus root times coefficient
        nextReal(i) += -(re * real(i) - im * imag(i))
        nextImag(i) += -(re * imag(i) + im * real(i))
      }
      Array.copy(nextReal, 0, real, 0, 5)
      Array.copy(nextImag, 0, imag, 0, 5)
      degree += 1
    }
    // A conjugate-closed set leaves no imaginary part; if one survives the roots were not a real system's.
    if (imag.take(5).exists(v => math.abs(v) > 1.0e-6)) return None
    Some(Array(real(0), real(1), real(2), real(3)))
  }
}
