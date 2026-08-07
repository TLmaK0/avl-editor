# The sources, in the repository

Every figure the editor applies that it did not measure itself comes from one of these, and cites the page
it is on. They are committed rather than linked because a link is not a source: it moves, it is edited, and
a reader six months from now cannot check what the code was written against.

The same rule as `docs/MIL-F-8785C.pdf`, which is the flying-qualities standard itself and lives one
directory up because the whole of `MilF8785cEvaluator` cites it.

| File | What it is | What is taken from it |
|------|------------|-----------------------|
| `naca-tr-572.pdf` | NACA Report No. 572, *Determination of the Characteristics of Tapered Wings*, R. F. Anderson, 1936. NASA NTRS 19930091647. | The **critical-section method**: the span loading splits into a basic and an additional distribution (pp. 1-2), and the wing stalls where the local `c_l` curve first becomes tangent to the section `c_lmax` curve (p. 12, "Estimation of maximum lift coefficient"). Used by `xfoil.WingMaximumLift`. |
| `cfr-14-23.73.pdf` | 14 CFR § 23.73, *Reference landing approach speed*, 2002 edition. U.S. GPO / govinfo. | `VREF` "must not be less than the greater of VMC ... and **1.3 VSO**". That 1.3 is the multiple `MilF8785cEvaluator.ApproachSpeedFactorOfStall` uses to turn a stall speed into the `Vomin` that MIL-F-8785C 3.2.1.3 is measured at — because MIL-F-8785C's own TABLE I declines to give one for the approach. |
| `ussa1976-excerpt.pdf` | *U.S. Standard Atmosphere, 1976* (NOAA-S/T 76-1562), pages 2 and 19 only. NASA NTRS 19770009539 / NOAA NGDC. | Air's **dynamic viscosity**: `mu = beta T^(3/2) / (T + S)`, eq. (51) on p. 19, with `beta = 1.458e-6 kg/(s m K^1/2)` and `S = 110.4 K` from table 2 on p. 2. Used by `xfoil.StandardAir` to turn a chord and a speed into the Reynolds number XFOIL is asked for. |

## Why the atmosphere is an excerpt

The full document is 243 scanned pages and 18 MB, and its text layer is unusable — the constants cannot be
searched for, only read. The two pages that matter are here in full; the whole document is at
<https://ntrs.nasa.gov/citations/19770009539>.

One thing to know if you check it: **table 2 on p. 2 prints Sutherland's constant as 110**, and p. 19's own
text says **110.4**. NASA's errata sheet, bound at the end of the scan, confirms the table is the typo:
"The value for Sutherland's constant S is too small by 0.4. It should be 110.4 Kelvin." The editor uses
110.4.
