/*
 * The Jacobian the decambering's Newton iteration needs: d(cl_i)/d(delta_k), out of one AVL session.
 *
 * The iterated decambering of Mukherjee, Gopalarathnam & Kim (AIAA 2003-1097) solves `J . dx = -F` (Eq. 4,
 * p. 6) with a Jacobian built from how every strip's lift responds to every section's decambering (Eqs. 5-9,
 * p. 7). The paper's flow solver is a VLM it wrote and can differentiate directly. Ours is AVL, driven as a
 * subprocess, so the response is **measured**: deflect one control by a known amount, solve, and read what
 * happened to every strip.
 *
 * Two claims made about this earlier in #17 turned out to be false when measured, and both are recorded here
 * because they are the sort of thing that reads as obvious and is not:
 *
 *   - **"Measure the Jacobian once and reuse it at every attitude"** — wrong. AVL is linear in its freestream
 *     *vector*, not in `alpha`, so the influence coefficients drift with attitude: against the matrix measured
 *     at 7.5 deg, the worst coefficient differs by 25 % at 20 deg and **68 %** at 35 deg. A Jacobian carried up
 *     from the trim point would point in the wrong direction exactly where the stall lives. It is therefore
 *     re-measured at every attitude — 1.42 s each on the check aircraft, about 28 s for a twenty-attitude
 *     post-stall range. Affordable, but not the free lunch that was claimed.
 *   - **"AVL prints the derivatives itself, so the Jacobian is free"** — wrong. AVL prints `CLd`, `Cmd`, `Cld`
 *     per control, which are the *aircraft's* derivatives. This method needs them per **strip**, and AVL
 *     prints those for nothing but the case it just solved. Hence one solve per control.
 *
 * What does hold is superposition, which is what makes a finite deflection a legitimate derivative rather than
 * a difference: three degrees on one control gives three times the response of one, and two controls together
 * give the sum of each alone, both to 2.0e-4 of local `cl` — the quantisation of AVL's four printed decimals.
 * {@code InfluenceMatrixCheck} measures that rather than assuming it, and it is the property that would break
 * first if AVL were not doing what this rests on.
 */
package com.abajar.avleditor.avl.decambering

import com.abajar.avleditor.avl.AVLS
import com.abajar.avleditor.avl.connectivity.AvlRunner
import com.abajar.avleditor.avl.runcase.StripForce
import com.abajar.avleditor.crrcsim.CRRCSim
import java.io.{BufferedReader, File, InputStreamReader, OutputStream}
import java.nio.file.{Files, Paths}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._

/**
 * How every strip responded to every decambering variable at one attitude.
 *
 * @param strips    the strips of the undeflected aircraft, in the order AVL printed them
 * @param influence `influence(k)(i)` is `d(cl_i)/d(delta_k)` per degree
 */
case class StripInfluenceMatrix(alphaDeg: Double, stepDeg: Double, controls: Seq[StripControl],
                                strips: Seq[StripForce], influence: Seq[Seq[Double]]) {

  /** The strip a control moves more than any other — the mapping, read from the measurement. */
  def strongestStrip(k: Int): Int = influence(k).zipWithIndex.maxBy { case (d, _) => math.abs(d) }._2

  def largestCoefficient: Double = influence.flatten.map(math.abs).max
}

object StripInfluence {

  /** One degree. Small enough that AVL's own nonlinearity in attitude does not enter, large enough to print. */
  val DefaultStepDeg = 1.0

  /**
   * Measures the influence matrix at one attitude, in a single AVL session.
   *
   * The controls are set as OPER constraints and reset after each solve, so the aircraft's geometry is written
   * once and AVL is started once. Everything else about the case matches the alpha sweep the rest of the
   * editor measures with: the attitude is **imposed** (`a a`), not asked for as a lift coefficient, and the
   * aircraft's real controls stay at neutral.
   *
   * A strip file AVL did not write is a missing measurement and is refused, never filled in: an influence
   * matrix with an invented row would converge the Newton iteration onto an aeroplane nobody has.
   */
  def measure(avlPath: String, model: CRRCSim, controls: Seq[StripControl], alphaDeg: Double,
              stepDeg: Double = DefaultStepDeg): StripInfluenceMatrix = {
    val cases = Map.empty[Int, Double] +: controls.map(control => Map(control.index -> stepDeg))
    val solved = solve(avlPath, model, alphaDeg, cases)
    val base = solved.head
    val influence = controls.zip(solved.tail).map { case (control, deflected) =>
      if (deflected.length != base.length) throw new IllegalStateException(
        f"AVL returned ${deflected.length}%d strips with ${control.name}%s deflected and " +
        f"${base.length}%d without it, at $alphaDeg%.2f deg. The two cases are not the same aircraft.")
      base.zip(deflected).map { case (before, after) => (after.getCl - before.getCl) / stepDeg }
    }
    StripInfluenceMatrix(alphaDeg, stepDeg, controls, base, influence)
  }

  /**
   * The spanwise loading for each of several sets of deflections, at one attitude, in **one** AVL session.
   *
   * The primitive everything here is built on, so the influence matrix and the superposition that legitimises
   * it are measured through the same code rather than through two paths that could disagree. A case is a map
   * from AVL's control number to its deflection in degrees; the empty map is the undeflected aircraft.
   */
  def solve(avlPath: String, model: CRRCSim, alphaDeg: Double,
            cases: Seq[Map[Int, Double]]): Seq[Seq[StripForce]] = {
    val directory = Files.createTempDirectory("decambering_")
    val stem = directory.toString + "/decambering"
    val geometryFile = Paths.get(stem + ".avl")
    AVLS.avlToFile(model.getAvl, geometryFile, model.getOriginPath)

    val files = cases.indices.map(i => stem + "_case" + i + ".fs")
    val process = new ProcessBuilder(avlPath, geometryFile.toString)
      .directory(directory.toFile.getAbsoluteFile).redirectErrorStream(true).start()
    try {
      val in = process.getOutputStream
      commands(model.getAvl.analysisVelocityMetresPerSecond(), alphaDeg, cases, files)
        .foreach(command => write(in, command))
      in.flush(); in.close()
      val out = new BufferedReader(new InputStreamReader(process.getInputStream))
      while (out.readLine() != null) {}
      if (!process.waitFor(120, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        throw new java.io.IOException("The AVL influence-matrix session timed out")
      }
    } finally {
      process.destroyForcibly()
    }

    cases.zip(files).map { case (deflections, file) =>
      strips(file, alphaDeg,
        if (deflections.isEmpty) "the undeflected aircraft"
        else deflections.map { case (index, value) => f"d$index%d = $value%.3f deg" }.mkString(" with ", ", ", ""))
    }
  }

  private def strips(file: String, alphaDeg: Double, what: String): Seq[StripForce] = {
    val handle = new File(file)
    if (!handle.exists) throw new IllegalStateException(
      f"AVL wrote no spanwise loading for $what%s at $alphaDeg%.2f deg, so its row of the influence " +
      "matrix was never measured. Nothing is assumed in its place.")
    val forces = AvlRunner.parseStripForces(handle).asScala.toSeq
    if (forces.isEmpty) throw new IllegalStateException(
      f"AVL's spanwise loading for $what%s at $alphaDeg%.2f deg came back empty.")
    forces
  }

  /**
   * What is typed at AVL, as a list so it can be read and checked without running AVL.
   *
   * `d<k> d<k> <value>` sets control k to that value — the same `<variable> <constraint> <value>` form as the
   * `d1 pm 0` the trim pass uses, and with the gain at 1.0 the value **is** degrees. Each control is put back
   * to zero after its solve, so every column is measured against the same undeflected aircraft rather than
   * against the last one.
   *
   * It ends the way every other pass ends: a blank line out of OPER and `quit`, never `q`, which OPER does not
   * know — it prints "Option not recognized", AVL dies on end-of-input and the files never get closed.
   */
  private[decambering] def commands(velocity: Float, alphaDeg: Double, cases: Seq[Map[Int, Double]],
                                    files: Seq[String]): Seq[String] = {
    val setUp = Seq("oper", "c1", "v", velocity + "\n", "a a " + alphaDeg)
    val solves = cases.zip(files).flatMap { case (deflections, file) =>
      val set = deflections.toSeq.sortBy(_._1).map { case (index, value) => s"d$index d$index $value" }
      val reset = deflections.toSeq.sortBy(_._1).map { case (index, _) => s"d$index d$index 0" }
      set ++ Seq("x", "fs", file) ++ reset
    }
    setUp ++ solves ++ Seq("", "quit")
  }

  private def write(in: OutputStream, command: String): Unit = {
    in.write((command + "\n").getBytes)
    in.flush()
  }
}
