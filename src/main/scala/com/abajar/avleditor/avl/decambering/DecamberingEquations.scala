/*
 * The decambering equations, read off the paper and expressed once.
 *
 * Mukherjee, Gopalarathnam & Kim, *An Iterative Decambering Approach for Post-Stall Prediction of Wing
 * Characteristics Using Known Section Data*, **AIAA 2003-1097** (the AIAA version of the Journal of Aircraft
 * 43(3) 2006 paper Hugo approved in full). Every equation below names its number and its page, because a
 * coefficient with no page behind it is exactly what this feature cannot contain.
 *
 * The method reduces each section's camber until the potential-flow solution's section lift and moment match
 * the section's known viscous data. Two decambering functions do it:
 *
 *   - **`delta1`**, a whole-chord camber change — for a thin section, a change of incidence;
 *   - **`delta2`**, a flap hinged at `x2`, with `theta2 = acos(1 - 2 x2)` and `x2 = 0.8` (Eq. 3, p. 5; the
 *     paper states 0.5 to 0.9 all work).
 *
 * <b>What is in radians and what is not.</b> The `2 pi` of thin-aerofoil theory is lift per **radian**, so
 * every angle here — the deltas, the effective attitude, the residuals' implied angles — is in radians, and
 * nothing in this file is in degrees. It is stated because this project's scars are unit scars: a control gain
 * that made every exported surface three times too weak, a `MINUTES_TO_SECONDS` of 36, a lift coefficient
 * computed from newtons over kg/m3. {@link #degreesOf} and {@link #radiansOf} are the only conversions, and
 * the caller converts at the boundary rather than in the middle of an equation.
 *
 * <b>The forward relations are the primary statement, and Eqs. 1-2 are their inversion.</b> The paper gives
 * the inversion — solve `delta2` from the moment residual, then `delta1` from the lift residual with `delta2`
 * already known — and this file writes both directions from **one** pair of coefficients
 * ({@link #liftPerDelta2} and {@link #momentPerDelta2}), so the two cannot drift apart. A second table of the
 * same numbers is what lets the first stay wrong.
 *
 * <b>One property worth understanding rather than just asserting:</b> the moment residual depends on `delta2`
 * alone. A whole-chord incidence change adds its lift at the quarter chord, so it produces no moment about the
 * quarter chord — which is why Eq. 1 can solve `delta2` on its own before `delta1` is known at all, and why
 * the two-variable system is not the tangle it looks like.
 *
 * <b>What is deliberately not here:</b> any restriction on the sign of the deltas. Whether the iteration may
 * add camber where the viscous curve lies above the potential one — the fork put to Hugo on 17-08 — is a policy
 * about what the residual is allowed to do, applied where the residual is formed. It is not part of the
 * algebra, so putting it here would bury a decision inside arithmetic that has a page number.
 */
package com.abajar.avleditor.avl.decambering

object DecamberingEquations {

  /** Where the second decambering function is hinged, as a fraction of the chord. Eq. 3, p. 5. */
  val SecondFunctionHinge: Double = 0.8

  /** The paper's stated range for `x2`: 0.5 to 0.9 all work (p. 5). */
  val UsableHingeRange: (Double, Double) = (0.5, 0.9)

  /** `theta2 = acos(1 - 2 x2)`, in radians. Eq. 3, p. 5. */
  def theta2(hinge: Double = SecondFunctionHinge): Double = math.acos(1.0 - 2.0 * hinge)

  /**
   * The lift a unit `delta1` produces: `2 pi` per radian, thin-aerofoil theory.
   *
   * Measured against AVL itself earlier in issue #17 — a NACA 2412 camber line on an aspect ratio 1000 wing
   * gives a section slope of 0.11000 per degree, which is 1.0031 times `2 pi` per radian. So the `2 pi` the
   * equations assume is AVL's own section slope to 0.3 %, and that is a measurement rather than an appeal to
   * theory.
   */
  val liftPerDelta1: Double = 2.0 * math.Pi

  /** The lift a unit `delta2` produces: `2 (pi - theta2) + 2 sin theta2`, from Eq. 2, p. 5. */
  def liftPerDelta2(hinge: Double = SecondFunctionHinge): Double = {
    val t = theta2(hinge)
    2.0 * (math.Pi - t) + 2.0 * math.sin(t)
  }

  /** The moment a unit `delta2` produces: `1/4 sin 2 theta2 - 1/2 sin theta2`, from Eq. 1, p. 5. */
  def momentPerDelta2(hinge: Double = SecondFunctionHinge): Double = {
    val t = theta2(hinge)
    0.25 * math.sin(2.0 * t) - 0.5 * math.sin(t)
  }

  /**
   * The moment a unit `delta1` produces: **none**.
   *
   * A whole-chord incidence change adds its lift at the quarter chord, so it makes no moment about the quarter
   * chord. Named rather than left implicit, because it is the reason Eq. 1 can solve `delta2` before `delta1`
   * is known.
   */
  val momentPerDelta1: Double = 0.0

  /** What lift and moment residuals a pair of deltas accounts for — the relation Eqs. 1-2 invert. */
  def residualsFrom(delta1: Double, delta2: Double, hinge: Double = SecondFunctionHinge): (Double, Double) =
    (liftPerDelta1 * delta1 + liftPerDelta2(hinge) * delta2,
     momentPerDelta1 * delta1 + momentPerDelta2(hinge) * delta2)

  /**
   * `delta2` from the moment residual. **Eq. 1, p. 5:**
   * `delta2 = dCm / (1/4 sin 2 theta2 - 1/2 sin theta2)`.
   */
  def delta2From(deltaCm: Double, hinge: Double = SecondFunctionHinge): Double =
    deltaCm / momentPerDelta2(hinge)

  /**
   * `delta1` from the lift residual with `delta2` already known. **Eq. 2, p. 5:**
   * `delta1 = (dCl - [2 (pi - theta2) + 2 sin theta2] delta2) / (2 pi)`.
   */
  def delta1From(deltaCl: Double, delta2: Double, hinge: Double = SecondFunctionHinge): Double =
    (deltaCl - liftPerDelta2(hinge) * delta2) / liftPerDelta1

  /**
   * Both deltas from both residuals: Eq. 1 then Eq. 2, in that order, because the moment fixes `delta2` alone.
   */
  def deltasFrom(deltaCl: Double, deltaCm: Double,
                 hinge: Double = SecondFunctionHinge): (Double, Double) = {
    val second = delta2From(deltaCm, hinge)
    (delta1From(deltaCl, second, hinge), second)
  }

  /**
   * The **one-variable** case the paper itself sanctions (p. 7): where the section data carries no `Cm`, or
   * the flow solver produces no section moments, `delta2 = 0` and the scheme becomes an incidence reduction —
   * *"however, in the current approach, the cross coupling between the sections was still accounted for"*.
   *
   * With `delta2` zero, Eq. 2 collapses to `delta1 = dCl / 2 pi`, which is precisely the incidence change
   * thin-aerofoil theory says produces that much lift. So the reduced scheme is not an approximation of the
   * method — it is the method with one term switched off, and the cross-coupling, which lives in the flow
   * solution rather than in these equations, is untouched by it.
   */
  def delta1Only(deltaCl: Double): Double = deltaCl / liftPerDelta1

  /**
   * The section's effective attitude. **Eq. 10, p. 7:**
   * `alpha_sec = (Cl)_sec / 2 pi - delta1 - delta2 [1 - theta2/pi + sin theta2 / pi]`.
   *
   * In radians, and this is where the known section data is looked up: the residuals are formed against the
   * viscous curve evaluated **here**, not at the aircraft's attitude, which is the whole reason a decambered
   * section can sit past its own stall while the wing as a whole has not.
   */
  def effectiveAttitude(sectionCl: Double, delta1: Double, delta2: Double,
                        hinge: Double = SecondFunctionHinge): Double = {
    val t = theta2(hinge)
    sectionCl / liftPerDelta1 - delta1 - delta2 * (1.0 - t / math.Pi + math.sin(t) / math.Pi)
  }

  def degreesOf(radians: Double): Double = radians * 180.0 / math.Pi
  def radiansOf(degrees: Double): Double = degrees * math.Pi / 180.0
}
