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

The page numbers below are **PDF pages**, which run two ahead of the printed page numbers.

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

The editor currently reports **Level 1, Category B** and nothing else, which is the right default for a
model in cruise but is not stated as a choice anywhere. Levels 2 and 3 are far more useful answers than
a bare FAIL, and an aerobatic model flies Category A.

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

### §3.2.2.1.1 Short-period frequency and acceleration sensitivity — PDF p. 13

ωnsp is required to lie within limits given as **graphs** (figures 1, 2 and 3) against the acceleration
sensitivity `n/α`, not as a table. This is the Control Anticipation Parameter criterion. Not implemented;
implementing it means digitising three figures. `n/α = ρ V² S CLα / (2 W)`, all of which the editor has.

### §3.2.2.1.2 Short-period damping — TABLE IV, PDF p. 13

> TABLE IV. Short-period damping ratio limits

| Level | Category A and C — min | max | Category B — min | max |
|-------|------------------------|-----|------------------|-----|
| 1 | 0.35 | 1.30 | **0.30** | **2.00** |
| 2 | 0.25 | 2.00 | 0.20 | 2.00 |
| 3 | 0.15\* | — | 0.15\* | — |

\* May be reduced at altitudes above 20,000 ft if approved by the procuring activity.

**Implemented** (Category B, Level 1) and correct.

### §3.2.1.1 Longitudinal static stability, §3.2.1.3 Flight-path stability

Not judged. The editor displays `Cma` raw. Flight-path stability (`dγ/dV`) is a Category C requirement
and needs the drag polar, which the alpha sweep now provides.

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

with ωnd in rad/s. This matters more for a model than for the airplane the standard was written for: a
model's ωnd is high, so `ωnd² |φ/β|` clears 20 easily and the requirement tightens. `|φ/β|d` comes from
the dutch-roll mode shape, which AVL reports and the editor already parses.

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

### §3.3.4 Roll control effectiveness — TABLES IXa/IXb, PDF p. 28-29

Time to bank a given angle. Not yet transcribed. Computable from what AVL returns: the steady roll rate
follows from `Clδa` and `Clp`, and the roll-mode time constant gives the time to reach a bank angle.
This is probably the single most useful criterion for a model, and it is missing.

### §3.3.6 Lateral-directional characteristics in steady sideslips — PDF p. 32-33

§3.3.6.1 yawing moments, §3.3.6.2 side forces, §3.3.6.3 rolling moments (positive effective dihedral),
§3.3.6.3.2 the upper limit on it. All are sign and magnitude conditions on `Cnb`, `Cyb` and `Clb`, which
the editor already displays raw and never judges.

---

## What does not apply, and why

Stated here rather than silently skipped.

| Section | Why not |
|---------|---------|
| §3.2.2.2, §3.2.3.x forces, §3.3.4.3 — all control-force requirements | A radio-controlled model has no stick feel system. There is no force to specify. |
| §3.3.9 asymmetric thrust, §3.3.9.5 two engines inoperative | Single-engine models. |
| §3.4.2 flight at high angle of attack, stalls | AVL is inviscid and cannot see a stall. See AGENTS.md. |
| §3.7 atmospheric disturbances | Needs a turbulence model the editor does not have. |
| Carrier-based, catapult takeoff, dives, wave-off | Not applicable. |

---

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

Test the hypothesis that the real rule is the pendulum frequency of the span, `ωnd ≥ sqrt(g/b)`:

| Class | Representative span | `sqrt(g/b)` | Table VI states |
|-------|--------------------|-------------|-----------------|
| I (Cessna 172), IV (F-16, F-4, F-15) | ~11 m | **0.94 rad/s** | 1.0 |
| III (B-52, C-5, 747) | ~60 m | **0.40 rad/s** | 0.4 |
| II (Gulfstream, C-130) | ~28 m | 0.59 rad/s | 0.4, in a row shared with Class III |

Two rows of the standard, airplanes differing by a factor of five in span, reproduced to within 6 % with
**no fitted constant at all**. The Class II mismatch is the expected direction: II and III share a row,
so the row is set by its largest member.

The representative spans are ours, not the standard's, and that is the assumption in this derivation.

### What the editor therefore does

The **dimensionless** criteria — every damping ratio — are applied exactly as written, at any size.

The **dimensional** ones are expressed in units of the aircraft's own Froude time `tF = sqrt(b/g)`,
calibrated so that a Class I/IV airplane reproduces the table:

| Criterion | Section | As stated | As applied |
|-----------|---------|-----------|------------|
| Dutch-roll min ωnd | Table VI | 0.4 rad/s (Cat. B) | `ωnd >= 1/tF` |
| Dutch-roll min ζd·ωnd | Table VI | 0.15 rad/s | `ζd·ωnd >= 0.15 · tF_I / tF` |
| Roll-mode max τR | Table VII | 1.4 s (Cat. B) | `τR <= 1.35 · tF` |
| Spiral min T2 | Table VIII | 20 s (Cat. B L1) | `T2 >= 19.3 · tF` |
| Phugoid min T2 | §3.2.1.2 c | 55 s (L3) | `T2 >= 53 · tF` |

with `tF_I = sqrt(11 m / g) = 1.06 s` the Froude time of the airplane the table was written around.

**For a full-size aircraft this changes nothing** — an 11 m airplane gets 1.0 rad/s and a 60 m one gets
0.4 rad/s, which is what the table says. It only bites outside the range the standard covers, which is
exactly where the standard stops being able to speak for itself. A 1.5 m model gets `ωnd >= 2.6 rad/s`
and a spiral that must take at least 7.6 s to double.

That last number is worth noticing: the 10 s that was invented for the spiral, with no derivation behind
it, lands close to the 7.6 s this produces. It was a decent guess. The difference is that this one can be
checked, and a check asserts the property — that the rule reproduces Table VI at both ends — rather than
the number, so it survives rescaling.

Both figures are always reported: what the standard states, and what is applied here after scaling. A
verdict that silently moved the goalposts would be worse than one that never moved them.

---

## What is ours and not the standard's

The editor also prints, in the same results window, the axis a divergence is running away in and what to
change about it — a fin, a centre of gravity, more dihedral. **None of that is MIL-F-8785C.** It is
model-flying judgement, and the standard has no opinion on it. It must not be presented as if the
specification said it.
