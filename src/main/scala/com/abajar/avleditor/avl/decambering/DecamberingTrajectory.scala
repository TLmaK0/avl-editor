/*
 * Where a section's operating point is trying to get to — the one thing the two published schemes disagree
 * about, and the place multiple post-stall solutions become visible instead of being landed on by accident.
 *
 * Hugo's instruction on approving the method was explicit: *"El trabajo de 2014 sobre esquemas de iteracion y
 * soluciones multiples pasada la perdida entra en el alcance: leelo antes de elegir esquema, no despues."* So
 * it was read before this file was written:
 *
 *   - Paul & Gopalarathnam, *Iteration schemes for rapid post-stall aerodynamic prediction of wings using a
 *     decambering approach*, **Int. J. Numer. Meth. Fluids 76(4), 2014, pp. 199-222** (doi 10.1002/fld.3931).
 *     Paywalled, and not quoted here from its own pages.
 *   - Hosangadi & Gopalarathnam, *A Low-Order Method for Prediction of Separation and Stall on Unswept Wings*,
 *     **arXiv:2006.00107v3** (4 Aug 2020), NC State — the same group's open successor, which states the 2014
 *     scheme it inherits and is where every equation and quotation below is read from. Its Sec. VI.C and
 *     Fig. 6 are the trajectory; its Eq. (10) is the slope; its Eqs. (14)-(15) are the residuals.
 *
 * <b>What the 2014 scheme changed, and why it is not a detail.</b> Both schemes drive each section's operating
 * point onto its viscous curve. They differ in **where on that curve the section is aiming**:
 *
 *   - The 2003/2006 scheme reads the viscous curve **straight down** at the section's effective attitude:
 *     the target is `cl_visc(alpha_eff)`, and the residual is that minus what the potential solve gave.
 *   - The 2014 scheme first **measures how the operating point actually moves** when decambering is applied,
 *     and takes the target where that line of travel meets the viscous curve.
 *
 * The measurement is the reason. Decambering a section does not move it straight down, because taking lift off
 * one strip strengthens the trailing vortices at its edges, and their upwash gives some of the lift back
 * (arXiv:2006.00107, Sec. VI.C, Fig. 6b: trajectory `T1` for one strip alone, the shallower `T*` when the whole
 * wing decambers together). A section aiming straight down is aiming at a point it will not arrive at.
 *
 * <b>And this is what makes multiple solutions visible.</b> Past the stall the viscous curve turns over, so a
 * straight line of travel can cross it **more than once** — the same section, at the same attitude, with two or
 * three legitimate viscous operating points. That is the multiple-solution behaviour the 2014 paper is about:
 * with the older scheme the iteration simply converges on whichever one the initial condition led to, and the
 * dependence is invisible. Here every crossing is reported ({@link SeveralTargets}), because a solver that
 * silently picks one of three answers is this project's oldest failure mode wearing new clothes.
 *
 * <b>What this file deliberately does not do:</b> pick. Choosing among several targets, and whether the
 * resulting decambering may have either sign — the fork put to Hugo on 17-08 — belong to the iteration, not
 * here. This file answers "where could this section be aiming, and how did each answer come about", which is
 * the same question under either arm of that fork.
 *
 * <b>Degrees, not radians</b>, throughout — unlike {@link DecamberingEquations}, which is radians throughout.
 * A section curve is measured, stored and sampled in degrees ({@link SectionCurve}), and converting it here so
 * that a file could claim to be uniform would put a conversion in the middle of an intersection rather than at
 * the boundary. Every name in this file therefore carries `Deg` or `PerDeg` and there is nowhere it can be
 * mistaken.
 */
package com.abajar.avleditor.avl.decambering

import com.abajar.avleditor.xfoil.{SectionCurve, SectionSample}

/**
 * How a section's target viscous operating point is found. Sealed and with no default: the two published
 * schemes are both defensible, so every caller says which one it means rather than falling into one.
 */
sealed trait TargetScheme

/**
 * The 2003/2006 scheme: read the viscous curve straight down at the section's effective attitude.
 *
 * The paper this project implements (AIAA 2003-1097 / J. Aircraft 43(3) 2006) forms its residuals this way,
 * and it is kept as a first-class option rather than as history, because it is what the approved method says
 * and because a scheme cannot be compared against one that is not there.
 */
case object VerticalLookup extends TargetScheme

/**
 * The 2014 scheme: follow the section's own measured line of travel until it meets the viscous curve.
 *
 * @param slopePerDeg `d(cl)/d(alpha_eff)` for this section, **measured** by one perturbation of the
 *                    decambering and not assumed (arXiv:2006.00107 Eq. 10). See {@link
 *                    DecamberingTrajectory#slopeFrom}.
 */
case class AlongTrajectory(slopePerDeg: Double) extends TargetScheme

/**
 * One viscous operating point a section could be aiming at, and where the curve's value there came from.
 *
 * The provenance travels with the target for the reason the whole feature exists: a target read off a part of
 * the curve XFOIL cannot vouch for is not a measurement, and the iteration that lands on it must be able to
 * say so afterwards.
 */
case class ViscousTarget(alphaDeg: Double, cl: Double, provenance: SectionSample) {
  /** How far the section has to travel in attitude to reach this target, signed. */
  def alphaTravelFrom(alphaDeg0: Double): Double = alphaDeg - alphaDeg0
}

/** What the search for a target found. Sealed, so a case nobody thought about is a compile error. */
sealed trait TargetOutcome

/** Exactly one crossing: the ordinary pre-stall case, and the case with no ambiguity to report. */
case class OneTarget(target: ViscousTarget) extends TargetOutcome

/**
 * More than one crossing — the multiple solutions the 2014 work is about, in the order the curve meets them.
 *
 * Reported rather than resolved. Which one the iteration should take depends on where it came from, and that
 * is the iteration's business; what must not happen is that three answers become one without anybody knowing.
 */
case class SeveralTargets(targets: Seq[ViscousTarget]) extends TargetOutcome

/**
 * The line of travel never meets the curve inside the attitudes XFOIL converged at.
 *
 * A real answer and not an error: a trajectory nearly parallel to the curve, or a section demanding lift past
 * anything the aerofoil was measured at, has no target on the measured curve. Extending the curve to invent
 * one is precisely the fabrication this feature is not allowed to make, so it says so and names the range it
 * searched.
 */
case class NoTargetWithin(lowestAlphaDeg: Double, highestAlphaDeg: Double) extends TargetOutcome

object DecamberingTrajectory {

  /**
   * The trajectory slope of one section, measured from the two operating points of one perturbation pass.
   *
   * `dcl/dalpha_eff = (cl_p - cl_0) / (alpha_p - alpha_0)`, arXiv:2006.00107 Eq. (10): the inviscid solve
   * gives `(alpha_0, cl_0)`, one round of decambering gives the perturbed `(alpha_p, cl_p)`, and the line
   * through them is how this section really moves.
   *
   * `None` when the perturbation did not move the section's attitude — there is no slope to measure, and
   * returning a large number or a textbook one instead would be inventing the very thing this call exists to
   * measure.
   *
   * The paper measures these once per geometry, at a high attitude where every section needs some decambering
   * (`alpha = 30 deg` for all its published results), and reports that subsequent iterations travel along the
   * same lines. That is its observation, made on its own solver, and it is a cost saving rather than part of
   * the formulation — so nothing here caches, and whoever iterates decides how often to re-measure.
   */
  def slopeFrom(alpha0Deg: Double, cl0: Double, alphaPerturbedDeg: Double, clPerturbed: Double): Option[Double] = {
    val travel = alphaPerturbedDeg - alpha0Deg
    if (travel == 0.0) None else Some((clPerturbed - cl0) / travel)
  }

  /**
   * Where a section sitting at `(alphaDeg, cl)` could be aiming on its own viscous curve.
   *
   * Under {@link VerticalLookup} there is one answer by construction — the curve is a function of attitude —
   * so the outcome is always {@link OneTarget}. Under {@link AlongTrajectory} the answer is every crossing of
   * the line of travel with the curve, which is one before the stall and can be several past it.
   */
  def targetsFor(curve: SectionCurve, alphaDeg: Double, cl: Double, scheme: TargetScheme): TargetOutcome =
    scheme match {
      case VerticalLookup =>
        val sample = curve.cl(alphaDeg)
        OneTarget(ViscousTarget(alphaDeg, sample.value, sample))

      case AlongTrajectory(slopePerDeg) =>
        val crossings = crossingsOf(curve, alphaDeg, cl, slopePerDeg)
        crossings match {
          case Seq() => NoTargetWithin(curve.lowestMeasuredAlphaDeg, curve.highestMeasuredAlphaDeg)
          case Seq(only) => OneTarget(only)
          case several => SeveralTargets(several)
        }
    }

  /**
   * Every attitude at which the line of travel meets the curve, in increasing attitude.
   *
   * <b>The intersection is exact, not iterated.</b> {@link SectionCurve} samples linearly between the points
   * XFOIL converged at — deliberately, and for reasons recorded there — so the curve **is** a polyline, and a
   * straight line crosses a polyline segment in closed form. A root-finder here would be slower and less
   * accurate than the arithmetic the curve's own definition already gives.
   */
  private def crossingsOf(curve: SectionCurve, alphaDeg: Double, cl: Double,
                          slopePerDeg: Double): Seq[ViscousTarget] = {
    val points = curve.points.sortBy(_.alpha.toDouble)
    def lineAt(a: Double): Double = cl + slopePerDeg * (a - alphaDeg)
    def gapAt(a: Double, curveCl: Double): Double = curveCl - lineAt(a)

    val alphas = points.map(_.alpha.toDouble)
    val gaps = points.map(p => gapAt(p.alpha.toDouble, p.cl.toDouble))

    val found = scala.collection.mutable.ArrayBuffer[Double]()
    alphas.indices.foreach { i =>
      // A node the line passes exactly through is one crossing, counted at the node and not again in either
      // segment beside it.
      if (gaps(i) == 0.0) found += alphas(i)
      else if (i + 1 < alphas.length && gaps(i + 1) != 0.0 && gaps(i) * gaps(i + 1) < 0.0) {
        val fraction = gaps(i) / (gaps(i) - gaps(i + 1))
        found += alphas(i) + fraction * (alphas(i + 1) - alphas(i))
      }
    }

    found.toSeq.distinct.sorted.map { crossingDeg =>
      val sample = curve.cl(crossingDeg)
      ViscousTarget(crossingDeg, lineAt(crossingDeg), sample)
    }
  }

  /**
   * What was found, in one line, for the log and for the record of a converged solution.
   *
   * Several targets are named individually with their attitudes: the sentence a reader needs is not "this was
   * ambiguous" but "it was ambiguous between 12.4 and 17.9 degrees", which is the difference between a wing
   * that is about to stall and one that already has.
   */
  def statementOf(outcome: TargetOutcome): String = outcome match {
    case OneTarget(t) =>
      f"one viscous target, cl ${t.cl}%.4f at ${t.alphaDeg}%.2f deg (${t.provenance.provenance}%s)"
    case SeveralTargets(ts) =>
      f"${ts.length}%d viscous targets — the line of travel crosses the curve at " +
        ts.map(t => f"${t.alphaDeg}%.2f deg (cl ${t.cl}%.4f)").mkString(", ") +
        ": the section has more than one legitimate operating point and which one is reached depends on where " +
        "the iteration started"
    case NoTargetWithin(low, high) =>
      f"no viscous target between ${low}%.1f and ${high}%.1f deg — the line of travel does not meet the " +
        "measured curve, and no curve is extended to make it"
  }
}
