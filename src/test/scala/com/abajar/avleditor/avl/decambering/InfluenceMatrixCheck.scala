/*
 * The influence matrix the decambering's Newton iteration is built on, out of real AVL runs.
 *
 * `d(cl_i)/d(delta_k)` — how every strip's lift answers every section's decambering — is what fills the
 * Jacobian of Eq. 4 (Mukherjee, Gopalarathnam & Kim, AIAA 2003-1097, p. 6). Four things about it have to be
 * true, and none of them can be established without AVL:
 *
 *   - **Declaring the controls does not change the aeroplane.** At zero deflection the refined-and-controlled
 *     aircraft must be the one the editor already measures, or the pre-stall curve has moved before anything
 *     is solved.
 *   - **The mapping from AVL's control number to the strip it acts on is right.** AVL numbers controls by first
 *     appearance as it reads the file, and the rest of the editor already assumes that. Here it is *measured*:
 *     deflecting control k must move strip k more than any other strip. If the numbering were off by one, every
 *     column of the Jacobian would belong to its neighbour and the iteration would converge onto a wing whose
 *     stall is in the wrong place — while every number still looked plausible.
 *   - **Superposition holds**, which is what makes a one-degree difference a legitimate derivative: three
 *     degrees gives three times the response, and two controls together give the sum of each alone.
 *   - **And the matrix drifts with attitude**, which falsifies the claim made earlier in #17 that it could be
 *     measured once and reused. Asserted as a fact so that nobody re-introduces the caching: AVL is linear in
 *     its freestream *vector*, not in alpha.
 *
 * The cross-coupling is the point of the whole method, and it shows up here as a *banded* matrix rather than a
 * diagonal one: reducing one section's camber changes the downwash over its neighbours, which is precisely what
 * the paper says the older section-by-section methods got wrong.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.decambering.InfluenceMatrixCheck"
 */
package com.abajar.avleditor.avl.decambering

import com.abajar.avleditor.{AvlManager, TestAircraft}
import com.abajar.avleditor.avl.connectivity.AvlRunner
import java.util.Properties
import scala.collection.JavaConverters._

object InfluenceMatrixCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** The attitude the aircraft trims near, where the matrix is measured for comparison. */
  private val TrimAttitude = 7.5
  private val StallAttitudes = Seq(20.0, 35.0)

  private def controlled(): (com.abajar.avleditor.crrcsim.CRRCSim, Seq[StripControl]) = {
    val refinement = DecamberingGeometry.refine(TestAircraft.conventional(), Set("wing"))
    val controls = DecamberingGeometry.declareStripControls(refinement, 0f)
    (refinement.model, controls)
  }

  def main(args: Array[String]): Unit = {
    println("what is typed at AVL, which needs no AVL to read")
    val (model, controls) = controlled()
    val typed = StripInfluence.commands(14f, TrimAttitude,
      Seq(Map.empty[Int, Double], Map(controls.head.index -> 1.0)), Seq("base.fs", "one.fs"))
    println("  " + typed.mkString(" | "))
    check("the attitude is imposed rather than asked for as a lift coefficient",
      typed.contains("a a " + TrimAttitude))
    check("a control is set by its own number and put back to zero afterwards",
      typed.containsSlice(Seq(s"d${controls.head.index} d${controls.head.index} 1.0", "x", "fs", "one.fs",
        s"d${controls.head.index} d${controls.head.index} 0")))
    // OPER does not know `q`: it prints "Option not recognized", AVL dies on end-of-input and the files it
    // wrote are never closed. The blank line leaves OPER and `quit` leaves AVL.
    check("it leaves OPER with a blank line and quits, never with q",
      typed.takeRight(2) == Seq("", "quit") && !typed.contains("q"))

    println("the controls the decambering acts through")
    controls.take(3).foreach(c => println(f"  ${c.name}%s is AVL's d${c.index}%d, panel ${c.panel}%d " +
      f"of ${c.surface}%s, hinged at ${c.hingeFraction}%.2f"))
    check("one control per panel", controls.length == 12)
    check("their AVL numbers are consecutive and distinct",
      controls.map(_.index).distinct.length == controls.length)
    check("each is declared on both nodes bounding its panel, without which AVL silently ignores it",
      controls.forall { control =>
        val sections = model.getAvl.getGeometry.getSurfaces.asScala
          .find(_.getName == "wing").get.getSections.asScala
        sections.count(_.getControls.asScala.exists(_.getName == control.name)) == 2
      })
    check("the gain is 1, so AVL's control variable is the deflection in degrees",
      model.getAvl.getGeometry.getSurfaces.asScala.find(_.getName == "wing").get
        .getSections.asScala.flatMap(_.getControls.asScala)
        .filter(c => controls.exists(_.name == c.getName)).forall(_.getGain == 1f))

    val props = new Properties()
    if (!AvlManager.ensureAvlAvailable(props)) {
      println("  AVL is not available here, so no influence coefficient has been measured by this run and")
      println("  nothing above the geometry has been checked.")
      println("INFLUENCE_MATRIX_SKIPPED")
      if (!ok) sys.exit(1)
      return
    }
    val avlPath = props.getProperty("avl.path")

    println("declaring the controls must not change the aeroplane")
    val undeflected = StripInfluence.solve(avlPath, model, TrimAttitude, Seq(Map.empty[Int, Double])).head
    val plain = new AvlRunner(avlPath, TestAircraft.conventional().getAvl,
      TestAircraft.conventional().getOriginPath, 45f, 20f).getCalculation()
    val plainStrips = plain.getAlphaSweep.asScala.find(p => math.abs(p.getAlphaDeg - TrimAttitude) < 1e-3)
      .map(_.getStrips.asScala.toSeq).getOrElse(Seq.empty)
    check("the same strips come back", undeflected.length == plainStrips.length)
    if (undeflected.length == plainStrips.length) {
      val worst = undeflected.zip(plainStrips).map { case (a, b) => math.abs(a.getCl - b.getCl) }.max
      println(f"  worst strip cl difference against the aircraft as drawn: $worst%.6f")
      check("carrying the same lift, so 26 controls at neutral are 26 controls that do nothing", worst < 1e-4)
    }

    println(f"the influence matrix at $TrimAttitude%.1f deg")
    val started = System.currentTimeMillis()
    val matrix = StripInfluence.measure(avlPath, model, controls, TrimAttitude)
    val seconds = (System.currentTimeMillis() - started) / 1000.0
    println(f"  ${controls.length}%d controls x ${matrix.strips.length}%d strips in one AVL session, " +
      f"$seconds%.2f s")
    val diagonal = controls.indices.map(k => matrix.influence(k)(k))
    println("  the diagonal, per degree: " + diagonal.map(d => f"$d%.4f").mkString(" "))
    check("every control lifts its own strip", diagonal.forall(_ > 0))
    // The mapping, measured rather than assumed. Off by one and every column belongs to its neighbour.
    check("and moves its own strip more than any other, which is what says the numbering is right",
      controls.indices.forall(k => matrix.strongestStrip(k) == k))
    // The cross-coupling the paper exists to account for: not a diagonal matrix.
    val neighbours = controls.indices.dropRight(1).map(k => math.abs(matrix.influence(k)(k + 1)))
    println("  and the coupling to the next strip: " + neighbours.map(d => f"$d%.4f").mkString(" "))
    check("a neighbour responds too, which is the cross-coupling the method exists for",
      neighbours.forall(_ > 0.001))
    check("but less than the strip itself, so the matrix is banded rather than full",
      controls.indices.dropRight(1).forall(k =>
        math.abs(matrix.influence(k)(k + 1)) < math.abs(matrix.influence(k)(k))))
    // The strips are not equally wide — the cosine spacing showing up where it should.
    check("the diagonal is not uniform, because the strips are not equally wide",
      diagonal.max > 2 * diagonal.min)

    println("superposition, which is what makes a one-degree difference a derivative")
    val tripled = StripInfluence.measure(avlPath, model, controls.take(1), TrimAttitude, 3.0)
    val worstScaling = matrix.strips.indices
      .map(i => math.abs(tripled.influence(0)(i) - matrix.influence(0)(i))).max
    println(f"  three degrees against three times one degree: worst $worstScaling%.5f of local cl, " +
      f"against a response of ${math.abs(matrix.influence(0)(0))}%.4f")
    // AVL prints cl to four decimals, so 2e-4 is the quantisation and not a departure from linearity.
    check("three degrees is three times one degree", worstScaling < 5e-4)

    val first = controls.head
    val other = controls(controls.length / 2)
    val together = StripInfluence.solve(avlPath, model, TrimAttitude,
      Seq(Map.empty[Int, Double], Map(first.index -> 1.0, other.index -> 1.0))).last
    val worstSum = matrix.strips.indices.map { i =>
      val summed = matrix.influence(0)(i) + matrix.influence(controls.length / 2)(i)
      math.abs((together(i).getCl - matrix.strips(i).getCl) - summed)
    }.max
    println(f"  two controls together against the sum of each alone: worst $worstSum%.5f of local cl")
    check("two controls together are the sum of each alone", worstSum < 5e-4)

    println("and the matrix drifts with attitude, so it cannot be measured once and reused")
    val drift = StallAttitudes.map { attitude =>
      val there = StripInfluence.measure(avlPath, model, controls, attitude)
      val worst = controls.indices.flatMap(k =>
        matrix.strips.indices.map(i => math.abs(there.influence(k)(i) - matrix.influence(k)(i)))).max
      println(f"  measured at $attitude%.0f deg: worst coefficient differs by $worst%.4f from the " +
        f"$TrimAttitude%.1f deg matrix, ${100 * worst / matrix.largestCoefficient}%.0f %% of the largest term")
      (attitude, worst)
    }
    check("at 20 degrees it has already moved well beyond AVL's printing resolution",
      drift.head._2 > 20 * 2e-4)
    check("and past the stall it is a large fraction of the coefficient itself, not a rounding error",
      drift.last._2 > 0.25 * matrix.largestCoefficient)
    check("further from the trim point means further from the trim matrix", drift.last._2 > drift.head._2)

    println(if (ok) "INFLUENCE_MATRIX_OK" else "INFLUENCE_MATRIX_FAIL")
    if (!ok) sys.exit(1)
  }
}
