# MIL-F-8785C — the flying qualities criteria, and where each number comes from

The editor judges what AVL computes against **MIL-F-8785C**, *Military Specification: Flying Qualities
of Piloted Airplanes*, 5 November 1980 (superseding MIL-F-8785B of 7 August 1969). The specification
itself is in this directory as [`MIL-F-8785C.pdf`](MIL-F-8785C.pdf) — a US Department of Defense
document, approved for use by all Departments and Agencies, in the public domain. It came from
[EverySpec](https://everyspec.com/MIL-SPECS/MIL-SPECS-MIL-F/MIL-F-8785C_5295/).

**Every threshold in `MilF8785cEvaluator` must cite a section and a table in this file, and this file
must cite a page in that PDF.** A number that cannot trace itself back to a page is either a stated
assumption, said out loud as one, or a bug. The 10-second threshold that used to decide whether a
lateral divergence was "a slow spiral" was neither: it read as if the standard had said it, and the
standard says 20 seconds.

The page numbers below are the document's own printed page numbers, and the PDF's pages carry the same
numbering, so a page reference works in either.

---

## What the specification is organised by

Three things pick which numbers apply, and the editor has to state which it is using rather than
assume one.

**Aircraft Class** (§1.3.1, PDF p. 4-5)

| Class | What it is |
|-------|-----------|
| I | Small, light airplanes |
| II | Medium weight, low-to-medium maneuverability |
| III | Large, heavy, low-to-medium maneuverability |
| IV | High-maneuverability airplanes: fighter, attack, tactical reconnaissance, observation |

A suffix `-L` means land-based and `-C` carrier-based (§1.3.1.1).

**Flight Phase Category** (§1.3.2, PDF p. 5-6)

| Category | What it is |
|----------|-----------|
| A | Nonterminal phases needing rapid maneuvering, precision tracking or precise flight-path control: air-to-air combat (CO), ground attack (GA), weapon delivery (WD), reconnaissance (RC), in-flight refuelling receiver (RR), terrain following (TF), close formation (FF) |
| B | Nonterminal phases flown with gradual maneuvers and without precision tracking: climb (CL), **cruise (CR)**, loiter (LO), descent (D), emergency descent (ED), aerial delivery (AD) |
| C | Terminal phases: takeoff, approach, landing |

**Level** (§1.3.3)

| Level | What it means |
|-------|---------------|
| 1 | Flying qualities clearly adequate for the mission Flight Phase |
| 2 | Adequate, but with an increase in pilot workload or a degradation in mission effectiveness |
| 3 | Degraded, but the airplane can still be controlled |

The editor reports **all three Levels in all three Categories**: one row per motion, one column per
Flight Phase. The Category is the one thing about the judgement that cannot be derived from the aircraft,
and judging one costs nothing over judging three — so it is not asked for. See AGENTS.md, "The Flight
Phase Category is the one thing the aircraft cannot tell us".

---

## Longitudinal

### §3.2.1.2 Phugoid stability — PDF p. 12

Verbatim: *"the following requirements: a. Level 1 — ζp at least 0.04; b. Level 2 — ζp at least 0;
c. Level 3 — T2 at least 55 seconds."*

| Level | Requirement |
|-------|-------------|
| 1 | ζp ≥ 0.04 |
| 2 | ζp ≥ 0 |
| 3 | T2 ≥ 55 s |

Applies with the pitch control both free and fixed.

### §3.2.2.1.1 Short-period frequency and acceleration sensitivity — FIGURES 1-3, pp. 13-16

The requirement is **drawn**, not tabulated: ωnsp against the acceleration sensitivity `n/α`, on log-log
axes, one figure per Flight Phase Category. That makes it look as though it needs a scanned plot measured
by eye, and it does not — **the boundaries are lines of constant `ωnsp² / (n/α)`, the Control Anticipation
Parameter, and each line carries its own value printed up the right-hand edge of the figure.** The plots
are a table of four numbers per Category.

| Category | Figure | Level 1 | Level 2 | Level 3 |
|----------|--------|---------|---------|---------|
| A | 1, p. 14 | 0.28 ≤ CAP ≤ 3.6 | 0.16 ≤ CAP ≤ 10.0 | CAP ≥ 0.16 |
| **B** | **2, p. 15** | **0.085 ≤ CAP ≤ 3.6** | **0.038 ≤ CAP ≤ 10.0** | **CAP ≥ 0.038** |
| C | 3, p. 16 | 0.16 ≤ CAP ≤ 3.6 | 0.036 ≤ CAP ≤ 10.0 | CAP ≥ 0.036 |

All three figures say the boundaries continue outside the plotted range as straight-line extensions, which
for lines of constant CAP means the limits above apply at any `n/α`.

**`n/α` needs no weight, no air and no wing area.** It is `ρ V² S CLα / (2 W)` — but in level flight the
lift equals the weight, so `W = ½ρV²S·CL_trim` and the whole thing collapses to `n/α = CLα / CL_trim`. Both
come straight back from AVL. It is the same identity that lets `AVL.analysisLiftCoefficient()` derive the
trim point from the weight, read the other way round.

**Implemented.** CAP has units of 1/s², so it follows the aircraft's size like a frequency squared: `n/α`
is dimensionless and does not scale, while ωnsp goes as `1/sqrt(b)`.

**Not implemented from those figures**: the additional ωnsp floors that Figures 1 and 3 draw as horizontal
and vertical lines at low `n/α`, which depend on the aircraft Class — including Figure 3's note that ωnsp
shall always exceed 0.6 rad/s for Level 3 in Classes I, II-C and IV. Category B has none of them.

### §3.2.2.1.2 Short-period damping — TABLE IV, PDF p. 13

> TABLE IV. Short-period damping ratio limits

| Level | Category A and C — min | max | Category B — min | max |
|-------|------------------------|-----|------------------|-----|
| 1 | 0.35 | 1.30 | **0.30** | **2.00** |
| 2 | 0.25 | 2.00 | 0.20 | 2.00 |
| 3 | 0.15\* | — | 0.15\* | — |

\* May be reduced at altitudes above 20,000 ft if approved by the procuring activity.

**Implemented**, in all three Categories.

### §3.2.1.1 Longitudinal static stability — p. 11

**It is not `Cma < 0`, and it is not the static margin.** Section 3.2.1 is "longitudinal stability *with
respect to speed*", and 3.2.1.1 reads: "For Levels 1 and 2 there shall be **no tendency for airspeed to
diverge aperiodically** when the airplane is disturbed from trim". The quantitative part is a Level 3
relaxation — "in no event shall its time to double amplitude be less than **6 seconds**".

| Level | Requirement |
|-------|-------------|
| 1 and 2 | no aperiodic airspeed divergence at all |
| 3 | if one exists, T2 ≥ 6 s |

**Implemented.** An aircraft either has an aperiodic speed divergence or it does not, and the eigenvalues
already say which: it is the runaway whose mode shape is speed-dominated. The rest of the section is
stated in pitch control **force and position gradients**, which a radio-controlled model has none of.

### §3.2.1.3 Flight-path stability — p. 12

Verbatim:

> Flight-path stability is defined in terms of flight-path-angle change where the airspeed is changed by
> the use of pitch control only (throttle setting not changed by the crew). For the landing approach
> Flight Phase, the curve of flight-path angle versus true airspeed shall have a local slope at `Vomin`
> which is negative or less positive than:
>
> a. Level 1 ----- 0.06 degrees/knot
> b. Level 2 ----- 0.15 degrees/knot
> c. Level 3 ----- 0.24 degrees/knot.
>
> The thrust setting shall be that required for the normal approach glide path at `Vomin`. The slope of
> the curve of flight-path angle versus airspeed at 5 knots slower than `Vomin` shall not be more than
> 0.05 degrees per knot more positive than the slope at `Vomin`, as illustrated by: [figure]

| Level | Max `dgamma/dV` at `Vomin` |
|-------|----------------------------|
| 1 | 0.06 deg/knot |
| 2 | 0.15 deg/knot |
| 3 | 0.24 deg/knot |

Plus the second sentence, which carries **no Level**: five knots slower, the slope may not be more than
0.05 deg/knot more positive than at `Vomin`.

**Implemented**, and it is a **Category C** requirement — stated for the landing approach and for nothing
else. Asked of a model flown as Category A or B the row reports `RowOutcome.DoesNotApply`, which is a
different answer from a pass and from "not judged".

#### What the criterion is actually about

The back side of the drag curve. Below the minimum-drag speed, slowing down *increases* drag, so easing
the nose down to descend makes the aircraft accelerate instead of go down, and the approach has to be
flown on the throttle.

#### Why no new input is asked for

In a shallow steady descent at a fixed throttle — which is what "throttle setting not changed by the crew"
means — `sin(gamma) = (T - D)/W`, so with `T` constant

```
dgamma/dV = -(1/W) (dD/dV) / cos(gamma)
```

Three things fall out of that:

- **The thrust cancels.** It sets where the glide path is, not how it slopes with speed, so "the thrust
  required for the normal approach glide path" never has to be worked out.
- **The glide path cancels with it**, to within `cos(gamma)` — 0.14 % at 3 degrees — so the approach angle
  need not be assumed either. It is taken as 1, and said so.
- **`D(V)` is already measured.** Lift equals weight, so the attitude at any speed follows from
  `CL = 2W/(rho V^2 S)`, and the alpha sweep gives `CD` there. Both curves are AVL's, at neutral controls,
  over the range it was asked about — and outside that range the row **refuses** rather than extrapolating.

A propeller at a fixed throttle loses thrust as speed rises, which makes `dgamma/dV` *more* negative than
this. Holding the thrust constant, as the standard's own illustration does for a jet, is therefore the
conservative reading and not a convenience.

#### `Vomin`, which is ours and cited

TABLE I (p. 7) defines `Vomin` per Flight Phase, and for APPROACH it declines to give a formula: it says
**"Minimum Normal Approach Speed"** — how the aircraft is flown, not something its geometry decides.
Everywhere the table *does* give one it is a multiple of `Vs`: 1.1, 1.2, 1.3, 1.4, 1.5.

So the shape of the answer is the standard's and the number is taken from the airworthiness rule that
fixes it for exactly this Flight Phase — **14 CFR § 23.73**, in the repository as
[`references/cfr-14-23.73.pdf`](references/cfr-14-23.73.pdf):

> the reference landing approach speed, VREF, must not be less than the greater of VMC ... and **1.3 VSO**.

VMC is a two-engine minimum-control speed and does not apply here.

**A field was deliberately not offered instead.** To choose an approach speed sensibly you have to know
where the aircraft stalls; a user who entered one below the stall would be handed a confident Level 1 for
a condition the aeroplane cannot reach. That is an invented default with the pen moved into the user's
hand, and worse than ours because it looks like data. This criterion therefore waited on the stall speed
being **measured** — see `xfoil.StallAnalysis` and AGENTS.md.

#### Size

`dgamma/dV` is an angle over a speed. Under Froude scaling a speed goes as `sqrt(b)`, so an inverse speed
carries the same power of the span as a frequency does and takes the same factor: `size.frequency`. A 1.2 m
model is allowed 0.17 deg/knot where the standard states 0.06. The 5-knot window is a speed and scales the
other way, like a time.

#### One thing worth knowing before reading a verdict

The limit only bites on an aircraft that approaches **slowly**: `dgamma/dV` carries a `1/V`. A jet at 130
knots cannot fail 3.2.1.3 whatever its polar looks like, and a light aircraft with the flaps down at 50
knots can fail it easily. That is the requirement working as intended, not a scaling artefact.

---

## Lateral-directional

### §3.3.1.1 Lateral-directional oscillations (Dutch roll) — TABLE VI, PDF p. 22

> TABLE VI. Minimum Dutch roll frequency and damping

| Level | Category | Class | Min ζd | Min ζd·ωnd (rad/s) | Min ωnd (rad/s) |
|-------|----------|-------|--------|--------------------|-----------------|
| 1 | A (CO and GA) | IV | 0.4 | — | 1.0 |
| 1 | A | I, IV | 0.19 | 0.35 | 1.0 |
| 1 | A | II, III | 0.19 | 0.35 | 0.4\*\* |
| 1 | **B** | **All** | **0.08** | **0.15** | **0.4**\*\* |
| 1 | C | I, II-C, IV | 0.08 | 0.15 | 1.0 |
| 1 | C | II-L, III | 0.08 | 0.10 | 0.4\*\* |
| 2 | All | All | 0.02 | 0.05 | 0.4\*\* |
| 3 | All | All | 0 | — | 0.4\*\* |

\* The governing damping requirement is the one yielding the larger ζd, except that ζd of 0.7 is the
maximum required for Class III.
\*\* Class III airplanes may be excepted from the minimum ωnd requirement, subject to approval, if
§3.3.2 through §3.3.2.4.1, §3.3.5 and §3.3.9.4 are met.

**And the part the editor ignores.** When `ωnd² |φ/β|d` exceeds 20 (rad/s)², the minimum `ζd·ωnd` is
*increased* above the values above by

| Level | Increase |
|-------|----------|
| 1 | Δζd·ωnd = 0.014 (ωnd² \|φ/β\|d − 20) |
| 2 | Δζd·ωnd = 0.009 (ωnd² \|φ/β\|d − 20) |
| 3 | Δζd·ωnd = 0.005 (ωnd² \|φ/β\|d − 20) |

with ωnd in rad/s.

`|φ/β|d` is not something AVL reports. It reports the mode's **lateral velocity**, and
`EigenvectorUnitsCheck` established by running AVL at two speeds that those velocity components are
**dimensional** — so `β = v / V`, and `|φ/β| = |φ| V / |v|`. Guessing between that and an already-divided
`v` would have been a factor of V, twenty on a 20 m/s model, on a quantity compared against a fixed 20.

**It cannot be assumed on or off.** An earlier draft of this file said a model's ωnd is high so the product
"clears 20 easily"; the measurement says otherwise. On the check aircraft `ωnd²|φ/β|` is **15.4 at 15 m/s
and 28.8 at 45 m/s** — the same airframe straddles the trigger depending on how fast it is flown, because
`|φ/β|` itself falls with speed rather than being a constant of the aircraft. So it has to be computed at
the condition being analysed.

**Implemented** (Category B, Level 1) and correct — apart from the augmentation above.

### §3.3.1.2 Roll mode — TABLE VII, PDF p. 23

> TABLE VII. Maximum roll-mode time constant, seconds

| Category | Class | Level 1 | Level 2 | Level 3 |
|----------|-------|---------|---------|---------|
| A | I, IV | 1.0 | 1.4 | 10 |
| A | II, III | 1.4 | 3.0 | 10 |
| **B** | **All** | **1.4** | **3.0** | **10** |
| C | I, II-C, IV | 1.0 | 1.4 | 10 |
| C | II-L, III | 1.4 | 3.0 | 10 |

(The Level 3 column of 10 s spans all rows in the original.)

**Not implemented.** τR is a real root, and real roots are invisible in the editor today unless they
diverge.

### §3.3.1.3 Spiral stability — TABLE VIII, PDF p. 23

Following a disturbance in bank of up to 20 degrees, the time for the bank angle to double shall be
greater than:

> TABLE VIII. Spiral stability — minimum time to double amplitude

| Category | Level 1 | Level 2 | Level 3 |
|----------|---------|---------|---------|
| A & C | 12 s | 8 s | 4 s |
| **B** | **20 s** | **8 s** | **4 s** |

**Not implemented.** This is the table the invented 10 s stands in for.

### §3.3.1.4 Coupled roll-spiral oscillation — PDF p. 23

Not permitted at all for Flight Phases involving more than gentle maneuvering (CO, GA). Permitted for
Categories B and C provided:

| Level | ζRS·ωnRS (rad/s) |
|-------|------------------|
| 1 | ≥ 0.5 |
| 2 | ≥ 0.3 |
| 3 | ≥ 0.15 |

**Not implemented.**

### §3.3.4 Roll control effectiveness — TABLE IXa, PDF p. 27

> TABLE IXa. Roll performance for Class I and II airplanes — time to achieve the following bank angle
> change (seconds)

| Class | Level | Category A | Category B | Category C |
|-------|-------|------------|------------|------------|
| **I** | **1** | **1.3 (60°)** | **1.7 (60°)** | **1.3 (30°)** |
| I | 2 | 1.7 (60°) | 2.5 (60°) | 1.8 (30°) |
| I | 3 | 2.6 (60°) | 3.4 (60°) | 2.6 (30°) |
| II-L | 1 | 1.4 (45°) | 1.9 (45°) | 1.8 (30°) |
| II-L | 2 | 1.9 (45°) | 2.8 (45°) | 2.5 (30°) |
| II-L | 3 | 2.8 (45°) | 3.8 (45°) | 3.6 (30°) |
| II-C | 1 | 1.4 (45°) | 1.9 (45°) | 1.0 (25°) |
| II-C | 2 | 1.9 (45°) | 2.8 (45°) | 1.5 (25°) |
| II-C | 3 | 2.8 (45°) | 3.8 (45°) | 2.0 (25°) |

The bank angle is part of the requirement and travels with it: Class I is measured over 60° in Categories
A and B and over 30° in Category C. Class IV has its own table (IXb, PDF p. 28) with four speed ranges.

**Implemented** for Class I, and derived rather than assumed. At a steady roll the ailerons' rolling
moment balances the roll damping, `Clδ·δ + Clp·(p b / 2V) = 0`, which gives the final roll rate; the roll
mode's time constant (Table VII, above) says how long it takes to arrive; and the bank angle follows from
integrating a first-order roll response once, `φ(t) = p (t − τ (1 − e^−t/τ))`.

Two things about it:

- `Clδ` is per unit of **AVL's control variable**, not per degree, so it is converted through the control's
  gain — the same factor whose absence made every exported JSBSim model's controls three times too weak.
- If the aircraft has no aileron, no gain, no stated aileron travel, no roll mode or no measured roll
  damping, it **says which one is missing** rather than filling it in. A roll rate computed from an
  invented deflection would look exactly like a measured one.

### §3.3.6 Lateral-directional characteristics in steady sideslips — pp. 32-33

Most of this family cannot be applied to a model, and the editor does not pretend otherwise.

- **§3.3.6.1** (yawing moments) and **§3.3.6.2** (side forces) are written in **yaw-control-pedal deflection
  and force**, and what they quantify is that the response be "essentially linear" between ±15° and ±10° of
  sideslip. AVL is a **linear** solver, so it cannot fail a linearity requirement — asserting it would be
  asserting nothing. What survives is the **sign convention** they encode.
- **§3.3.6.3** (rolling moments) likewise, plus the same linearity.
- **§3.3.6.3.2, the positive effective dihedral limit**, is the one requirement here that is quantitative
  and needs no forces: positive effective dihedral "shall never be so great that more than **75 percent of
  roll control power** available to the pilot ... are required for sideslip angles which might be
  experienced in service employment". §3.3.7.1 (p. 33) puts a number on that angle for the approach — "at
  least **10 degrees of sideslip**", again with roll control not exceeding 75 percent of control power.

**Implemented** as one row: the three signs, and the dihedral limit at 10° of sideslip.

| Quantity | Sign wanted | What it means |
|----------|-------------|---------------|
| `Cnb` | positive | the nose weathercocks into the sideslip |
| `CYb` | negative | the side force opposes the sideslip |
| `Clb` | negative | positive effective dihedral |

The aileron needed is `abs(Clb) x 10°` against the rolling moment the ailerons make at their stop,
`abs(Cldelta) x (travel / gain)` — the same roll control power §3.3.4 uses. Both are ratios, so nothing
here scales with the aircraft's size.

---

## What does not apply, and why

Stated here rather than silently skipped.

| Section | Why not |
|---------|---------|
| §3.2.2.2, §3.2.3.x forces, §3.3.4.3, **§3.3.5 in full** — all control-force requirements | A radio-controlled model has no stick feel system. There is no force to specify. §3.3.5.1, §3.3.5.1.1 and §3.3.5.2 (pp. 31-32) are stated entirely in pounds of yaw-control-pedal force. |
| §3.3.2.2 roll rate oscillations, §3.3.2.3 bank angle oscillations, §3.3.2.4 sideslip excursions (p. 24, definitions on p. 78) | Not blocked by physics — blocked by machinery the editor does not have. See below. |
| §3.3.9 asymmetric thrust, §3.3.9.5 two engines inoperative | Single-engine models. |
| §3.4.2 flight at high angle of attack, stalls | AVL is inviscid and cannot see one. Where a wing **stops** lifting is now measured (XFOIL plus AVL's spanwise loading — see AGENTS.md and §3.2.1.3 above), but what it does *past* that point is not modelled at all. |
| §3.7 atmospheric disturbances | Needs a turbulence model the editor does not have. |
| Carrier-based, catapult takeoff, dives, wave-off | Not applicable. |

---

### §3.3.2.2.1 Roll rate oscillations for small inputs — FIGURE 4, p. 25

Unlike figures 1-3, this one carries **no printed constant**: it is a genuine graph, four polylines of
`posc/pav` against `ψβ`, and its vertices had to be read off the page. They are recorded here as **our
reading**, not as a quotation.

Each boundary has the same shape — flat, rise, plateau, fall — and the middle one **serves two
requirements at once**, Category B Level 1 and Categories A&C Level 2. That is the same coincidence the
§3.3.2.2 table shows, where both are 25 %.

| Boundary | Applies to | Left flat | Rise ends | Plateau | Fall begins | At −360° |
|----------|-----------|-----------|-----------|---------|-------------|----------|
| upper | Cat. B Level 2 | 0.20 to −110° | −200° | **1.00** | −290° | 0.20 |
| middle | Cat. B Level 1, **and** Cat. A&C Level 2 | 0.10 to −120° | −200° | **0.60** | −270° | 0.10 |
| lower | Cat. A&C Level 1 | 0.05 to −130° | −180° | **0.25** | −270° | 0.05 |

### §3.3.2.3 Bank angle oscillations — FIGURE 5, p. 25

The same reading, on `φosc/φav`. Its shape has **five** segments rather than four: it stops falling short
of the right-hand edge and runs level again.

| Boundary | Applies to | Left flat | Rise ends | Plateau | Fall begins | Level again from | Right flat |
|----------|-----------|-----------|-----------|---------|-------------|------------------|------------|
| upper | Cat. B Level 2 | 0.20 to −15° | −85° | **1.00** | −175° | −245° | 0.20 |
| middle | Cat. B Level 1, **and** Cat. A&C Level 2 | 0.10 to −25° | −105° | **0.60** | −175° | −255° | 0.10 |
| lower | Cat. A&C Level 1 | 0.05 to −35° | −105° | **0.25** | −195° | −265° | 0.05 |

**The two figures share their values.** 0.20/1.00/0.20, 0.10/0.60/0.10 and 0.05/0.25/0.05 on both, each
curve returning to the level it left. Only the phase angles at which they turn differ, which is what the
pair is for: the same limits on the roll rate and on the bank angle, at their own phasings.

**Uncertainty, stated because it is ours and not the document's**: the vertical values land on the
plotted grid and are read exactly; the horizontal ones do not, and are good to about **±10°**.

That is what the **band of indecision** is for. A digitised vertex does not widen the pass band — widening
it would let through aircraft the standard fails. Within the reading uncertainty of a boundary the verdict
says so and asserts no Level.

## §3.3.2.2 and §3.3.2.4: a formula exists, just not for what is asked

Worth setting out, because the obvious summary — "these need a simulation, there is no formula" — is wrong,
and it was written here before the definitions were read.

The lateral-directional system is **linear**, so the roll rate after a step aileron input has a closed form:
a sum of exponentials plus a damped sinusoid, straight out of the `p/δa` transfer function. There is a
formula for the curve.

What there is no formula for is **the point on the curve the standard asks about**. §6.2.6 (p. 78) defines
the quantity on the **peaks of the trace**:

```
zeta_d <= 0.2:   posc/pav = (p1 + p3 - 2 p2) / (p1 + p3 + 2 p2)
zeta_d >  0.2:   posc/pav = (p1 - p2) / (p1 + p2)
```

"where p1, p2 and p3 are roll rates at the first, second and third peaks". A peak is `dp/dt = 0`, which
mixes exponentials with sines: transcendental, with no algebraic solution. So the route is **evaluate the
closed form and find its peaks numerically** — root-finding, not time-marching integration.

Likewise `Δβ` is "the maximum change in sideslip occurring within 2 seconds or one half-period of the Dutch
roll, whichever is greater", and `k` is "the ratio of command roll performance to the applicable roll
performance requirement of 3.3.4" — which reuses the roll response already implemented.

**What is actually missing** is the transfer function: the editor never forms a state-space or transfer
function model of the aircraft. It takes AVL's eigenvalues as given. Building `p/δa` and `β/δa` from the
derivatives and the inertias is the real cost, and it is algebra.

The limits then come from **figures 4 and 5** (p. 25), piecewise-linear boundaries of `posc/pav` and
`φosc/φav` against `ψβ`. Unlike figures 1-3 those carry no printed constant — they are polylines and would
have to be digitised at their breakpoints.

One argument against doing it inside the editor survives, weaker than it was first put: the verdict would
come from a second dynamic model of the aircraft, while the one the user flies is the JSBSim export. Two
models are free to disagree. Running JSBSim on the exported aircraft — as `JsbsimCurveCheck` and
`DuctedFanFlightCheck` already do — keeps one source of truth.

## The assumption the specification cannot state for us

**MIL-F-8785C is written for piloted, full-scale airplanes.** Applying it to a 1 kg model is an
extrapolation, and it is not uniformly valid. Under Froude scaling — which is the right similarity for
an aircraft flying under gravity — a model at scale 1/n has:

- **frequencies √n times higher**, and times √n shorter;
- **the same damping ratio**, ζ being dimensionless.

So each criterion has to be marked for what it is:

| Kind | Criteria | Does it transfer to a model? |
|------|----------|------------------------------|
| Dimensionless | short-period ζ (Table IV), phugoid ζ (§3.2.1.2), dutch-roll ζ (Table VI), coupled roll-spiral is partly dimensional | **Yes**, unchanged |
| Dimensional | dutch-roll `ωnd ≥ 0.4` and `ζd·ωnd ≥ 0.15` (Table VI), roll-mode τR (Table VII), spiral T2 (Table VIII), phugoid `T2 ≥ 55 s` | **No** — they scale with √n |

Two consequences worth knowing before reading a verdict:

1. Applied unchanged, a model clears the dutch-roll frequency floors (0.4 rad/s, 0.15 rad/s) by a wide
   margin whatever it is like, so **two of the three criteria deciding that verdict become vacuous** and
   only `ζd ≥ 0.08` really bites.
2. The same applies at the other end. This editor is used for large aircraft as well as small ones, and
   a criterion written around a 10-metre airplane is no more valid for a 60-metre one.

### The standard's own numbers are already Froude-scaled

They are not stated that way, but they behave that way, and that is what makes an automatic correction a
derivation rather than a guess.

Table VI's minimum ωnd is **1.0 rad/s for Classes I and IV, and 0.4 rad/s for Classes II and III**.
Class I is a light trainer and Class IV a fighter — as different as two airplanes get — and the standard
puts them in the same row. What they have in common is size: both are around 11 m of span. Classes II
and III are the big ones.

Test the hypothesis that the real rule is the pendulum frequency of the span, `ωnd ≥ sqrt(g/b)`. The spans
below are **not recalled**: they are read off [NASA CR-2144, *Aircraft Handling Qualities
Data*](https://ntrs.nasa.gov/citations/19730003312) (Heffley and Jewell, 1972) — a contemporary of the
standard, tabulating the fleet it was written around.

| Aircraft | Class | Span | Source | `sqrt(g/b)` | Table VI asks |
|----------|-------|------|--------|-------------|---------------|
| F-104A | IV | 21.94 ft = 6.687 m | CR-2144 fig. III-2, p. 35 | **1.211** | 1.0 |
| F-4C | IV | 38.67 ft = 11.787 m | CR-2144 fig. IV-2, p. 64 | **0.912** | 1.0 |
| C-5A | III | 219.2 ft = 66.812 m | CR-2144 fig. X-2, p. 246 | **0.383** | 0.4 |

The two Class IV aircraft **bracket** their row's 1.0 rad/s — one above, one below — rather than one of
them approximating it, and the Class III figure lands within 4 % of its row. Two rows an order of
magnitude apart in span, reproduced by one formula with **no fitted constant at all**. A size-independent
threshold could not do that.

An earlier version of this section used spans recalled from memory, and one of them was picked because it
flattered the fit. These are cited; `FroudeScaleCheck` holds the arithmetic.

### What the editor therefore does

The **dimensionless** criteria — every damping ratio — are applied exactly as written, at any size.

The **dimensional** ones are scaled, but **only below the range the standard covers**. The evidence above
says that size matters; it is not licence to rewrite the standard where it already applies. So:

- for any aircraft at least as big as the **reference span**, every threshold is used **exactly as
  written**;
- below it, with `r = sqrt(b_ref / b)`, a threshold in rad/s is multiplied by `r` and one in seconds is
  divided by it.

**The reference is derived, not chosen.** `sqrt(g/b)` equals the 1.0 rad/s that Table VI asks of Classes I
and IV exactly when `b = g = 9.81 m`. That is where the standard's own floor and the law agree, and it
lands between the two Class IV aircraft above rather than on either of them. Nothing about it is a
judgement call: change the table entry and the reference follows.

| Criterion | Section | As stated (Cat. B, Level 1) | At 1.5 m of span |
|-----------|---------|------------------------------|------------------|
| Dutch-roll min ωnd | Table VI | 0.40 rad/s | 1.02 rad/s |
| Roll-mode max τR | Table VII | 1.40 s | 0.55 s |
| Spiral min T2 | Table VIII | 20 s | 7.8 s |
| Phugoid min T2 (Level 3) | §3.2.1.2 c | 55 s | 21.5 s |
| Short-period CAP floor | Figures 1-3 | 0.085 | 0.56 (a frequency squared) |

**A full-size aircraft is therefore judged by the standard verbatim** — nothing is scaled at 9.81 m, at
30 m or at 60 m. Scaling bites only where the standard stops being able to speak for itself, and that is
exactly where it goes vacuous: applied unchanged, a model clears both dutch-roll frequency floors whatever
it is like, leaving one of the three criteria doing any work.

The 7.8 s is worth noticing. The 10 s that was invented for the spiral, with no derivation behind it, lands
close to it. It was a decent guess. The difference is that this one can be checked — and `FroudeScaleCheck`
asserts the **property**, that the law brackets Table VI's Class I/IV row and reproduces its Class III one
and leaves a full-size aircraft alone, rather than any of the numbers, so it survives rescaling.

Both figures are always reported: what the standard states, and what is applied here after scaling. A
verdict that silently moved the goalposts would be worse than one that never moved them.

---

## What is ours and not the standard's

The editor also prints, in the same results window, the axis a divergence is running away in and what to
change about it — a fin, a centre of gravity, more dihedral. **None of that is MIL-F-8785C.** It is
model-flying judgement, and the standard has no opinion on it. It must not be presented as if the
specification said it.
