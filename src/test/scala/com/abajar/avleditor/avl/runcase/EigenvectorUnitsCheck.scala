/*
 * What units AVL's mode shapes are in — which nothing in this project had established, and three
 * flying-qualities criteria are blocked behind.
 *
 * AVL prints, for each mode, the amplitudes of u, v, w, p, q, r, phi, theta, psi. Those have different
 * physical dimensions, and the editor needs the sideslip angle beta out of them: if `v` is a lateral
 * velocity in m/s then beta = v/V, and if AVL already divided by V then `v` IS beta. The two answers
 * differ by a factor of V — twenty times on a 20 m/s model — and TABLE VI's augmentation compares
 * `wn^2 |phi/beta|` against 20, so that factor decides whether the criterion applies at all.
 *
 * Guessing was not an option, so this asks AVL. The same aircraft is flown at two speeds and the answer
 * is read off how things scale:
 *
 *   - a dimensional `v` makes `v/phi` scale with V; an already-divided one leaves it alone;
 *   - and the eigenvalues themselves should scale with V, which is the assumption the whole modal report
 *     already rests on (sigma read as 1/s) and had never been tested either.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.runcase.EigenvectorUnitsCheck"
 */
package com.abajar.avleditor.avl.runcase

import com.abajar.avleditor.AvlManager
import com.abajar.avleditor.avl.connectivity.AvlRunner
import java.util.Properties
import scala.collection.JavaConverters._

object EigenvectorUnitsCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private val SlowSpeed = 15f
  private val FastSpeed = 45f

  private def runAt(avlPath: String, speed: Float): List[AvlEigenvalue] = {
    // The check's own aircraft: a sample is the user's aeroplane and changes under the check's feet.
    val model = com.abajar.avleditor.TestAircraft.conventional()
    model.getAvl.setVelocity(speed)
    val runner = new AvlRunner(avlPath, model.getAvl, model.getOriginPath)
    runner.getCalculation().getEigenvalues.asScala.toList
  }

  /** The lateral oscillation: the dutch roll, which is the one carrying both sideslip and bank. */
  private def dutchRoll(modes: List[AvlEigenvalue]): Option[AvlEigenvalue] = {
    val lateral = modes.filter(m => m.getOmega > 1.0e-6f && m.hasModeShape && m.getLateralRatio >= 0.55f)
    if (lateral.isEmpty) None else Some(lateral.maxBy(_.getNaturalFrequency))
  }

  private def describe(label: String, modes: List[AvlEigenvalue]): Unit = {
    println(f"  $label%s: ${modes.size}%d modes, ${modes.count(_.hasModeShape)}%d with a mode shape")
    modes.filter(m => m.getOmega > 1.0e-6f && m.hasModeShape).foreach { m =>
      println(f"    wn ${m.getNaturalFrequency}%7.3f  zeta ${m.getDampingRatio}%6.3f  " +
        f"lateral ${m.getLateralRatio}%.2f  v ${m.getLateralParticipation}%8.4f  " +
        f"phi+psi ${m.getBankParticipation}%8.4f  p ${m.getRollRateParticipation}%8.4f")
    }
  }

  def main(args: Array[String]): Unit = {
    val props = new Properties()
    if (!AvlManager.ensureAvlAvailable(props)) {
      println("  AVL is not available here; this check exists to ask it a question and cannot")
      println("EIGENVECTOR_UNITS_SKIPPED")
      return
    }
    val avlPath = props.getProperty("avl.path")

    println(f"the same aircraft at $SlowSpeed%.0f m/s and at $FastSpeed%.0f m/s")
    val slow = runAt(avlPath, SlowSpeed)
    val fast = runAt(avlPath, FastSpeed)
    describe(f"$SlowSpeed%.0f m/s", slow)
    describe(f"$FastSpeed%.0f m/s", fast)

    val speedRatio = FastSpeed / SlowSpeed
    println(f"  speed ratio ${speedRatio}%.2f")

    (dutchRoll(slow), dutchRoll(fast)) match {
      case (Some(a), Some(b)) =>
        println("the eigenvalues themselves")
        val frequencyRatio = b.getNaturalFrequency / a.getNaturalFrequency
        println(f"    wn ${a.getNaturalFrequency}%.3f -> ${b.getNaturalFrequency}%.3f, " +
          f"ratio ${frequencyRatio}%.2f against a speed ratio of ${speedRatio}%.2f")
        // A dutch roll's frequency goes as sqrt(Nbeta), and Nbeta goes as V^2, so wn goes as V — but only
        // if the eigenvalues are in 1/s. If AVL returned them non-dimensionalised they would not move.
        check("they are in 1/s: the frequency scales with speed",
          math.abs(frequencyRatio - speedRatio) / speedRatio < 0.25)

        println("the mode shape's velocity components")
        val slowRatio = a.getLateralParticipation / a.getBankParticipation
        val fastRatio = b.getLateralParticipation / b.getBankParticipation
        val shapeRatio = fastRatio / slowRatio
        println(f"    v/(phi+psi): ${slowRatio}%.4f -> ${fastRatio}%.4f, ratio ${shapeRatio}%.2f")
        println(f"    dimensional v would give about ${speedRatio}%.2f; an already-divided one about 1.00")

        val dimensional = math.abs(shapeRatio - speedRatio) < math.abs(shapeRatio - 1.0)
        if (dimensional) {
          println("  => AVL's velocity components are DIMENSIONAL: beta = v / V")
        } else {
          println("  => AVL's velocity components are ALREADY DIVIDED BY V: beta = v")
        }
        check("the answer is one or the other, not halfway between",
          math.min(math.abs(shapeRatio - speedRatio), math.abs(shapeRatio - 1.0)) <
            0.4 * math.abs(speedRatio - 1.0))
        check("and it is the dimensional one", dimensional)

        println("and the angles are in radians, which is the other factor that could have been hiding")
        // |phi/beta| is a ratio of two angles, so it only comes out dimensionless if both are in the same
        // unit. A conventional aeroplane's dutch roll carries something of order one; if AVL printed phi in
        // degrees this would come back 57 times larger, which no aircraft has.
        val slowPhiBeta = a.phiOverBeta(SlowSpeed)
        val fastPhiBeta = b.phiOverBeta(FastSpeed)
        println(f"    |phi/beta|: ${slowPhiBeta}%.3f at $SlowSpeed%.0f m/s, ${fastPhiBeta}%.3f at $FastSpeed%.0f m/s")
        check("it is a number an aeroplane could have, not one 57 times too big",
          slowPhiBeta > 0.05 && slowPhiBeta < 10.0 && fastPhiBeta > 0.05 && fastPhiBeta < 10.0)
        // It is **not** a constant of the aircraft: the faster it flies, the less it banks per unit of
        // sideslip, and this one falls by a factor of four over a three-to-one speed range. Worth writing
        // down, because assuming it fixed is the obvious shortcut and it is wrong.
        check("and it is a property of the flight condition, not of the airframe alone",
          math.abs(fastPhiBeta - slowPhiBeta) / slowPhiBeta > 0.2)

        println("what that does to TABLE VI")
        // The footnote raises the minimum zeta*wn once wn^2 |phi/beta| passes 20 (rad/s)^2.
        val slowProduct = a.getNaturalFrequency * a.getNaturalFrequency * slowPhiBeta
        val fastProduct = b.getNaturalFrequency * b.getNaturalFrequency * fastPhiBeta
        println(f"    wn^2 |phi/beta| = ${slowProduct}%.1f at $SlowSpeed%.0f m/s, ${fastProduct}%.1f at " +
          f"$FastSpeed%.0f m/s, against the 20 the footnote triggers at")
        // The two straddle it, on the same aircraft. So the augmentation can be neither assumed on nor
        // assumed off: it has to be computed at the condition being analysed, which is the whole reason
        // this file exists.
        check("the same aircraft falls on both sides of the trigger, depending on how fast it is flown",
          math.min(slowProduct, fastProduct) < 20.0 && math.max(slowProduct, fastProduct) > 20.0)

      case _ =>
        println("  no dutch roll with a mode shape came back at one of the two speeds")
        check("a lateral oscillation was found at both speeds", false)
    }

    println(if (ok) "EIGENVECTOR_UNITS_OK" else "EIGENVECTOR_UNITS_FAIL")
    if (!ok) sys.exit(1)
  }
}
