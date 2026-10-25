# AVL Editor

An editor for radio-controlled and small-UAV aircraft. Geometry, masses, materials and
propulsion are edited in a GUI; **AVL** measures the aerodynamics, **XFOIL** supplies the
viscous data AVL cannot, and the export produces a **JSBSim** flight dynamics model (metric)
that runs in JSBSim standalone, in FlightGear, and in PX4 SITL through `px4-jsbsim-bridge`.
The editor also judges the aircraft's flying qualities against MIL-F-8785C.

## Scope

**In:** the geometry, mass, material and propulsion model (electric, glow/petrol piston,
free propeller, ducted fan); AVL analysis and the MIL-F-8785C report; the stall analysis;
the JSBSim export and the FlightGear package around it.

**Out, on purpose:** the CRRCsim export, retired. Files carrying its `SimpleTrust` node still
load and save unchanged (`LegacyRoundTripCheck`), and are told plainly they cannot be exported —
the editor offers no control that builds something no export can consume.
**What is out of scope is not a gap and is never counted as one.**

## Stack

- **Scala 2.10.4 + Java**, built with sbt (CI uses Temurin 17). GUI: SWT 3.127.0, JOGL 2.5.0.
- Model files are YAML (snakeyaml 2.0); `skipMissingProperties` keeps files from older versions loading.
- External engines: **AVL 3.40, XFOIL 6.99, JSBSim** — each installed on demand by `AvlManager`,
  `XfoilManager` and `JsbsimManager` into `~/.avleditor`, never onto the machine.
- Entry point `com.abajar.avleditor.Main`; run it with `./run.sh` (from a CLI agent: `./run.sh &`).
- Compile: `sbt compile`.

## Running the checks — and why `sbt test` is not how

**`sbt test` runs nothing in this build, and prints `[success]` while doing it**: there is no test
framework on the classpath, so `Test / definedTests` is empty. A check here is an executable
`object` that prints `PASS`/`FAIL` per assertion and exits 1 if any failed — the shape that lets
it drive AVL, XFOIL or JSBSim as a subprocess. Each check file's header states its own command.

```bash
sbt checks                                                        # the real gate: every check
sbt "Test/runMain com.abajar.avleditor.avl.mass.MassMirrorCheck"  # one check
```

**Read the output, not only the exit code**: a check that cannot find what it drives excuses
itself (`..._SKIPPED`) and exits 0. The `checks` task names those, and CI sets
`CHECKS_REQUIRE_ALL=1` so an excused check fails the build there.

**The checks fly `TestAircraft`, never the files under `samples/`.** Those are the user's
aeroplanes and get edited while the editor is tried out: a guarantee cannot rest on a sample
nobody tidies up.

## Quality gates

| Gate | Tool | Command |
|---|---|---|
| Every executable check | the `checks` task in `build.sbt` | `sbt checks` |
| Lint (advisory; warnings only, not in CI) | scalastyle 1.0.0, `scalastyle-config.xml` | `sbt scalastyle` |

CI: `.github/workflows/tests.yml` runs `sbt checks` on every pull request and on master, pinned to
ubuntu-22.04 under xvfb. `release.yml` builds and **publishes a release on every push to master,
with no path filter** — a documentation-only push rebuilds it too. `xfoil-binaries.yml` builds
XFOIL from source, pinned to ubuntu-22.04: a binary built on `-latest` dies at the loader on older
machines and every polar comes back empty, which reads as an aerofoil that never stalls.

Load the skill `quality-gates` before interpreting any gate's output or claiming one passes.

## No silent fallbacks

**Never invent data for a model that does not provide it.** If a model cannot be handed to a
simulator, the attempt stops and raises a visible alert listing what is missing — never a default.
A fabricated value produces an aircraft that loads, looks plausible and behaves wrongly, which is
far harder to diagnose than a refusal.

- Requirements live in `jsbsim/SimulationRequirements`, **one rule per observed failure**.
- Validation runs in two stages, because some inputs are AVL's outputs rather than the editor's
  fields: `validate(model)` before AVL runs, `validateCalculation(calc)` after it. The funnel for
  every export and launch is `AvlEditor.withAvlCalculation`.
- Refusals go through `MainWindow.showError` — **a refusal that only reaches the log is
  indistinguishable from success**.
- A default you did not write is still a default: what the exported file leaves out, JSBSim fills
  in from its own constructors, so every element the export does not state has to be a decision.

### What remains are stated assumptions

A value the model genuinely cannot express may be assumed — **documented where it is defined**,
derived from a reference that demonstrably works, and pinned by a `*Check.scala` asserting the
property rather than the number, so it survives rescaling. Replace one only with a better-sourced
derivation, never with a guess. Two conventions these figures follow:

- The power a propulsion component states is the **electrical** figure — what a listing quotes —
  never shaft power; the loss down to shaft power is part of each stated figure of merit.
- A constant with measurements behind it records its **spread** beside it, in code, so it never
  reads as more certain than the data that produced it.

## Units

A model states its own length, mass and time unit, and every figure it holds is in those. The
**only** thing that converts is the `ModelUnits` object `AVL` hands out, with its factors from the
one table in `UnitConversor` — **a second table is what lets the first stay wrong**. Elements
follow the model by reference (`initParents()`), never by copy. Pinned by `ModelUnitsCheck`,
`ExportUnitsCheck` and `AnalysisUnitsCheck`.

## Curves, not one tangent

The exported flight model states **what AVL measured across a range of attitudes** — thirteen,
from −10° to +20°, in one AVL session (`AvlRunner.sweepAlpha`) — not one measurement plus a rate.
The `CL(α)`, `CD(α)` and `Cm(α)` tables each **replace** the constants they stand for, drag is
driven by attitude rather than by `cl²`, and a table never extrapolates: past the stall it carries
what is modelled there (next section), and a run where the stall could not be measured holds its
last row instead and says so in the log. Pinned by `AlphaSweepCheck`, `AeroTableCheck` and
`JsbsimCurveCheck`, which runs JSBSim for real on the exported file.

## Where the wing gives up, which AVL cannot say

The stall comes from the **critical-section method** (NACA Report No. 572, committed at
`docs/references/naca-tr-572.pdf`): AVL's spanwise loading against each section's own XFOIL
`clmax` at that section's own Reynolds number, read from the surface that carries the lift. The
exported post-stall lift is the critical section's own curve scaled by **the fraction of the
section limit** — AVL's CL at the stall onset over the section's own limit, 1 at the join by
construction. The arithmetic is `xfoil.WingMaximumLift`, the plumbing `xfoil.StallAnalysis`, the
export side `JsbsimExporter`. A missing XFOIL costs one row of the report, never the run.

## Documentation

- [`docs/mil-f-8785c.md`](docs/mil-f-8785c.md) — every flying-qualities threshold with its
  section, its table and its page in [`docs/MIL-F-8785C.pdf`](docs/MIL-F-8785C.pdf). A number
  that cannot trace itself to a page is either a stated assumption, said out loud, or a bug.
- [`docs/references/`](docs/references/) — the cited sources themselves (NACA TR 572, USSA 1976,
  14 CFR 23.73), with a README saying what is taken from each page.
- `PLAN.md` is **history, and local only** (gitignored): the record of the CRRCsim→JSBSim pivot.
  Code comments that say "see PLAN.md" point at it. It is not where work is planned.

## Feature Planning

**Nothing new is built until Hugo says so.** One feature, one issue, one authorisation — and an
approval never carries over to the next thing. His words:

> *"debes actualizar tu documentación y la de los agentes para que no hagan features sin pedir
> permiso."*

> *"yo no he pedido que arregle el avión, no quiero que se haga nada sin mi consentimiento."*

**Proposing is not permission**, however good the proposal and however well measured. Measuring,
and fixing defects in what is already built, need no authorisation; **adding** does. A proposal is
put to him in his language: one sentence he can judge without opening anything — what it is, what
it is for, what becomes possible — never a phase name or a bare issue number.

## One session owns this repo, end to end (2026-10-25)

**This repo is worked by a single session that owns it end to end:** measuring, ordering the work,
implementing, delivering. There is no separate coordinator to escalate to and no queue to hand a
measurement to — **the owner is talked to directly.** Any instruction, in this repo or inherited
from elsewhere, that says to report work to a coordinator, to queue a question for a relay, or to
wait for a periodic review round, is **obsolete** — decide it here, or say it to the owner.

⛔ **And none of this becomes waiting.** What belongs to the owner — money, credentials, deletions,
labels, opening issues, publishing a release, visibility changes — is not a blocker: say it, and
carry on with whatever does not depend on it.

## How we work here

**The state does not live in this file: it lives in GitHub.** Open issues are the state, closed
ones are the history; a change of plan is a comment on its issue. This document contains only what
will still be true tomorrow without anyone editing it.

**Every task is worked in its own git worktree**, never the main checkout — the main checkout stays
on master and is only read, and there may be worktrees left from earlier tasks:

```bash
git worktree add .claude/worktrees/<branch> -b <branch> origin/master
```

Work goes back through a branch and a pull request, never a push to master. `.claude/` is
gitignored so the worktrees do not leave the repository permanently dirty.

**No `sudo`, nothing under `/usr`, `/etc` or `/opt`, no symlink onto the machine.** Whatever needs
a tool installs it itself, under `~/.avleditor` — the `*Manager` classes are the shape to copy. If
you think the host has to be touched, ask and wait; asking and doing at the same time is not asking.

🔑 **Skills are this house's method, and they are loaded BEFORE acting, not after.** They live in
`~/.claude/skills` (a link to `management/skills`: one source in git, never copied into this repo)
and the **superpowers** plugin is active at user level. If there is any chance a skill applies to
what you are about to do, invoke it first.

| You are about to… | Load |
|---|---|
| decide where to record something, open an issue, resume after a restart | `github-is-the-state` |
| turn an idea or a bug into work, write a closing criterion | `designing-into-issues` |
| run or interpret a gate, or claim that something passes | `quality-gates` |
| write tests, checks or fixtures | `bdd-integration-first` |
| write an assertion, or read a green, a zero or a "does not reproduce" | `rules-of-evidence` |
| open a PR, merge, or publish | `delivering-a-branch` |
| create, archive or **delete** a repo, a package or an image | `managing-repos` |
| install anything on this machine, sudo, symlinks, dependencies | `host-tools` |
| review security or respond to a suspected compromise | `security-review-and-incidents` |
| write user-visible text or render a failure | `ui-texts-and-failures` |
| email, or anything that goes outside | `operator-preferences` |

📌 **The history of how each of these rules was arrived at is in this file's git history** (it was
1,298 lines). `git log -p AGENTS.md` holds the case behind every rule; none of it is needed to work.
