# AVL Editor

This project is an editor for radio-controlled / small-UAV aircraft. It integrates with AVL
for aerodynamic analysis and exports **JSBSim** flight dynamics models (metric), which run in
JSBSim standalone, FlightGear and PX4 SITL (px4-jsbsim-bridge). It can also build XFOIL from
source (CI) to derive viscous data. The legacy CRRCsim export has been removed.
It allows users to modify aircraft geometry, mass and configuration for simulation.
Developed in Java and Scala, it provides a GUI for streamlined editing and analysis.

## Building and Running

### Compilation

To compile the project, run the following command:

```bash
sbt compile
```

### Running the application

Use the provided script to start the application:

```bash
./run.sh
```

This script will:
- Kill any existing instance of the application
- Start the application in background mode

The application will start in full-screen mode.

**Note for agents:** When running from a CLI agent, use background execution to avoid blocking:
```bash
./run.sh &
```

### Commit Message Guidelines

Commit messages should be concise, clear, and a maximum of two lines. The first line should be a brief summary, and the second line can optionally provide more context if needed.

### Git Push Policy

**IMPORTANT:** Never push to remote without explicit user permission. Always ask before running `git push`.

### Recent UI Changes

- The button to add a new section to a surface has been changed to a `+` button for a more intuitive user experience.
- Every mass the model states a position for — the geometry's masses and the propulsion components'
  and fuel tank's own positions — is drawn in the 3D view, sized by weight, and the selected one is
  moved with an axis handle per direction. A mass with no stated weight still gets a marker: its
  position is real. See `mass.MassMarkers`.

## Feature Planning

### PLAN.md

**IMPORTANT:** Before starting any task, the agent MUST first create or update the plan in `PLAN.md`. No implementation work should begin until the plan is written and reviewed.

The project uses a local `PLAN.md` file (not tracked in git) to maintain context across sessions for the current feature being developed.

**Usage:**
- Plans must be written in English
- This file should be rewritten/reset when starting a new feature
- It contains the feature description, current status, next steps, and relevant context
- Helps the agent continue work after context cleanup
- Should include task lists, important decisions, and files being modified

## Engineering Policy

- Never ship "quick patches" as final fixes.
- Always solve the root cause, even if the implementation takes longer.
- If a temporary workaround is unavoidable, label it explicitly as temporary, explain the risk, and propose the permanent fix immediately.
- Prefer robust, evidence-based behavior over UI heuristics that can misclassify results.

### No silent fallbacks

**Never invent data for a model that does not provide it.** If a model cannot be handed to a
simulator (AVL, JSBSim, FlightGear, PX4 SITL), the attempt must **stop and raise a visible
alert** listing what is missing — never substitute a default and carry on.

Why: a fabricated value produces an aircraft that loads, looks plausible and behaves wrongly.
That is far harder to diagnose than a refusal, and the user cannot tell a good export from a
broken one. Every FlightGear bug in this project so far came from a hardcoded value, not from
missing code:

- `<flight-model>jsbsim</flight-model>` instead of `jsb` — FlightGear silently fell back to
  `glider.ac` and drew the wrong aircraft.
- An invented single `BELLY` contact when the model had no collision points — one point cannot
  support pitch or roll, so JSBSim never trimmed.
- A fixed `spring_coeff` of 100 N/M regardless of weight — a 2.8 kg model compressed 9 cm on
  18 cm of gear and sat under the runway with only its fin showing.

Rules:

- Requirements live in `jsbsim/SimulationRequirements`, one rule per observed failure. Add a rule
  when a missing input breaks a simulation; do not paper over it with a default.
- Validation runs in `AvlEditor.withAvlCalculation` (the funnel for every export and launch) and
  in `runAvl`. Refusals go through `MainWindow.showError` — **a refusal that only reaches the log
  is indistinguishable from success**.
- Derived quantities must be derived: `crrcsim.calculate()` populates the mass, the inertias **and
  the centre of gravity** from the mass objects, so validate *after* it or every model reports zero
  mass. The CG belongs there with the rest: it is written to the reference point, which AVL takes its
  moments about and which the JSBSim export writes as the CG, so a stale one exports an aircraft with
  the weight of one model and the balance point of another. A model whose masses total zero keeps the
  reference point it had.
- When a physical constant is unavoidable, take it from a reference that demonstrably works and
  record the derivation in a comment. Gear stiffness, for instance, comes from FlightGear's stock
  c172p: total spring ≈ 32 × weight per metre (~3 cm static compression), damping = spring / 3.
- Pin the rule with a `*Check.scala` and assert the property, not the number, so it survives
  rescaling (see `GearStiffnessCheck`, which checks compression at 2.8 kg and 25 kg).

Propulsion components carry their **own mass and position**, and the total mass, the inertias and
the centre of gravity are computed from all of them (`CRRCSim.getAllMasses`). They used to have no
mass field, so a motor or a battery could only be represented as ballast somewhere else.

These masses are **optional**: a component may be accounted for elsewhere or be negligible, and a
zero is a stated value rather than missing data, so it is accepted and contributes nothing. Two
things to keep right: the fuel is not part of the empty weight (JSBSim adds it from the tank's
contents), and the propulsion masses stay out of `getMassesRecursive()`, because
`massesFromMaterials()` rewrites the mass of every element from what that element is made of, and a
motor swept into that would come back as balsa.

`YDUPLICATE` mirrors the **geometry, never the masses**: AVL's mass file and JSBSim's mass balance are
lists of absolute point masses. So a mass off the plane of symmetry of a mirrored element, with
nothing on the other side, states half the weight the element carries, puts the CG off the centreline
and understates the roll inertia.

The other half is therefore **virtual**: the model stores one mass, and the mirrored copy is derived
— drawn in the 3D view, where it follows whichever half is moved, and written into whatever is
generated. `getMassesRecursive()` is what the model stores; `getEffectiveMassesRecursive()` is what a
generated model sees, and it is the one the generation paths use: `MassObject.writeAVLMassData` (the
AVL mass file), `CRRCSim.getAllMasses` (JSBSim mass balance, inertias) and
`calculateCenterOfMassFromMasses()`. Nothing to keep in step, nothing extra to delete, nothing new in
the file. See `MassObject.mirrorPlaneY/virtualMirrorOf/getEffectiveMasses` and `MassMirrorCheck`.

There is **one** estimate of where an element balances, and it is the element's own:
`Surface.definedSideVolume()` and `Body.definedSideVolume()` return a `VolumeCentroid` — the volume of
the side the element defines and its centroid, which at uniform density is its centre of gravity. Both
the position a new mass starts at (`geometricCenter()`) and the masses generated from volume come from
it, so they cannot disagree. They used to: `+ Mass` on a wing put the mass on the fuselage centreline
at mid-chord (area-weighted, y forced to 0) while the auto mass put it on the wing at 42% of the chord
(volume-weighted). A mass now starts **on the side the element defines**, where that half balances, and
its mirror weighs the other half — put both on the centreline instead and the aircraft balances the
same but rolls as if the wings weighed nothing.

Whatever decides that a mirror will exist has to be the same test everywhere. One mass per element is
stored, weighing the side that element defines, and its mirror carries the other half — but for an
element whose defined side balances **on** the plane of symmetry (a body a hair off the centreline, a
surface whose defined side straddles it) there is no mirror, so that single mass has to weigh the whole
element. Assuming a mirror that never appears silently loses that element's other half.
`MaterialElement.massVolume()` and `massWettedArea()` apply that rule once, and everything that needs
it — the weight from materials, the masses generated from them — asks them rather than deciding again.

Masses live on **surfaces and bodies**, not on sections. A section is a station where the wing's shape
is defined — leading edge, chord, airfoil — and not a part with weight, so the editor does not offer it
a mass (a control never did). Models that kept masses there are not dropped: every load moves them
onto the surface, which changes nothing physically — a mass states an absolute position and a section
mirrors about its surface's plane — and says so in the log
(`AVLGeometry.moveSectionMassesToSurfaces`, `SectionMassCheck`). The same load restores the transient
parent links first (`AVLGeometry.initParents`), because a section that does not know its surface does
not know which plane it mirrors about.

Two rules keep it honest. A mass **already stated on the other side** is not mirrored again — that is
how every `+Y`/`-Y` pair written by older versions keeps its weight instead of doubling — and a mass
**on the plane of symmetry** has no mirror, since it already stands for both halves, which is why
`Surface.geometricCenter()` puts a new mass there. A genuinely one-sided item belongs on the
geometry's own masses, which are absolute and never mirrored.

## What units the model is in

A model states its own **length, mass and time unit**, and every figure it holds is in those: a `Mass` of 0.18
is 0.18 kg in a model stated in kilograms and 0.18 g in one stated in grams. So a conversion cannot be left to
whoever happens to be holding a figure — some of them cannot see the units at all.

`AVL` holds the three names, because that is where the user sets them, and hands out a **`ModelUnits`**; that
object is the only thing that converts, and it takes its factors from `UnitConversor`, so there is still one
table of them. Everything else asks: `Config` for the mass and inertias, `AVLGeometry.massesFromMaterials()`
and `MaterialElement.createMass()` for the weight from materials, `CRRCSim.getAnalysisWeightKg()` for the
operating point, `ComponentShapes` for the millimetres a battery is stated in, and `AVL.writeAVLMassData` for
the mass file's own `Lunit`/`Munit`/`Tunit` lines.

It is followed, never copied. `AVLGeometry` and every `MaterialElement` hold a **reference** back to the model
rather than the three names, restored by `initParents()` with the other transient links, because a copy is how
a unit the user changed afterwards goes stale. An element with no link — a surface a check built on its own —
answers with the defaults, which is what an all-defaults model states anyway.

Three things this fixed, all of them silent:

- `massesFromMaterials()` worked its weight out in kilograms (`kg = m³ × 1000 × g/cm³ × fill + …`) and wrote it
  straight into a field the rest of the editor read in the model's unit, so **a model stated in grams weighed a
  thousandth of what it should**, and one in ounces was out by 28. The same wing now weighs the same however
  the model chooses to write it down (`ModelUnitsCheck`).
- `CRRCSim.getAnalysisWeightKg()` summed the masses as if they were kilograms, and the lift coefficient the
  whole analysis is measured at is derived from that number.
- `UnitConversor.MINUTES_TO_SECONDS` was **36**. It had been hidden for years because `writeAVLMassData` wrote
  `"60 s"` by hand instead of using the factor — the second table is what let the first stay wrong.

A message that names a unit has to name the right one: the log after generating masses reports both what the
aircraft weighs and what the model writes down (`1.18 kg, stated as 1,179.936 g`), because it used to say `kg`
whatever the model was in.

## What an aircraft is made of

A surface and a body state their **material**: a density in g/cm³ for what the part is filled with, a
**fill percentage** for how much of the volume it encloses is actually structure, and a **skin** weighed
by the area it covers, because a 0.2 mm carbon laminate weighs about 310 g/m² whatever it is wrapped
around and a single density could only approximate that.

A skin is a **material of a thickness**, not a weight out of nowhere: it states its own density in g/cm³
and how thick it is in mm, and the weight per square metre is derived from the two, so they cannot
disagree with a third stored figure. The whole weight is then
`kg = m³ × 1000 × g/cm³ × fill + m² × mm × g/cm³`, both conversions pinned by `MaterialWeightCheck`: a
cubic metre at 1 g/cm³ is 1000 kg, and a square metre of a millimetre at 1 g/cm³ is 1 kg.

Choosing a material writes **its** density onto the element, and choosing a skin writes its density and
its thickness. Rows derived from other rows have to be re-rendered when one changes, or the table shows
what the user chose and not what the choice did — the properties table clears itself after every
property change for that reason.

The element stores **the figures, not a reference to a library entry**. Choosing a material copies its
density onto the surface; the name is a label. A model has to weigh the same on a machine whose library
differs or was edited since, and a model naming a material this machine has never heard of keeps its
weight (`MaterialLibraryCheck`). The library itself lives in `~/.avleditor/materials.yaml`, seeded with
about thirty entries the first time it is asked for and editable under **Edit > Materials…**; a file
that cannot be read falls back to the built-in list rather than to nothing.

This **replaced** spreading a stated all-up weight over the volume. `massesFromMaterials()` gives every
element one mass weighing what it is made of, so the total is a result — what the aircraft would weigh
if it were built as described — instead of a figure the user has to know in advance for the editor to
redistribute. Masses stated by hand on the geometry itself are left alone: they are not made of
anything the geometry knows about. The defaults are stated assumptions, documented where they are
defined: balsa medium at 15% for a surface, 12% for a body, since a built-up structure of ribs, spars
and sheeting is mostly air. An old file has no material fields, so it loads with those defaults and
nothing changes weight until the button is pressed.

A dropdown whose entries the user can edit cannot be an index into a constant array, which is what
`@AvlEditorField(options=…)` gives: `optionsFrom` names a method on the object that returns the choices
when the cell is opened, and the field keeps the chosen **name** (`TableFieldNamedOptions`). Undo goes
through the setter (`NamedChoiceChangeCommand`), because choosing a material writes its density too and
putting only the name back would leave an element named one thing and weighing another.

Propulsion is **required**, not optional: without an engine the model cannot take off and
FlightGear reports `Throttle 0 does not exist! 0 engines exist`. The battery voltage, propeller
diameter, blade count and the motor's data curve are all validated, and `buildPropulsion` no
longer substitutes anything for them.

Validation runs in **two stages**, because some inputs are AVL's outputs rather than the editor's
fields: `SimulationRequirements.validate(model)` before AVL runs, and `validateCalculation(calc)`
after it (the span efficiency comes from AVL's totals and used to be replaced by 0.85).

Driving another program through its keyboard menus is not a place to guess. AVL's plot pass used to end
with `q`, which OPER does not know: it printed `Option not recognized`, AVL died on end-of-input, and the
Trefftz page — drawn and appended to `plot.ps` — never got its `showpage`, so Ghostscript produced nothing
and the results window showed one plot instead of two. Xplot11 closes the file when AVL **exits**, so the
sequence has to leave the plot menu, leave OPER (both with a blank line) and `quit`. The keystrokes live
in `AvlRunner.plotCommands` as a list, so they can be read and checked without running AVL, and
`AvlPlotCheck` then runs AVL for real and asserts two pages, two `showpage`s and a closed file.

## What point the aircraft is measured at

**The lift coefficient is derived, never typed.** In level flight the lift equals the weight, so
`CL = W / (rho/2 V² Sref)` — once the speed is chosen the coefficient follows from the aircraft, and there is
nothing to choose. It used to be an editable field defaulting to 0, and a default is what it stayed in every
model nobody edited it in, the sample included (`alpha: 0.0`): an aircraft whose wings carry nothing, nose
5.6° down, with the trim, the deflections and the modes all measured there. `AVL.analysisLiftCoefficient()` is
the **only** place that works it out, and the stability run, the eigenvalue pass and the plots all take it
from there, so they cannot end up describing different aircraft. It returns null rather than a number when
the weight, speed, air or reference area is missing; `SimulationRequirements.analysisPointProblems` refuses
first, and `AvlRunner` throws if it is somehow reached without one (`AnalysisPointCheck`).

The weight comes from `CRRCSim.getAnalysisWeightKg()` — the same mass list `calculate()` derives the mass,
the inertias and the centre of gravity from — and `calculate()` pushes it, so an analysis cannot run against a
weight the model no longer has. `runAvl` now goes through that funnel too: it used to analyse with whatever
mass and **centre of gravity** were left from the last time, and AVL takes its moments about that centre. What
it validates there is deliberately narrower than an export's (`analysisRequirements`): running AVL is not
exporting an aircraft, and demanding propulsion before answering an aerodynamic question would refuse a
glider for not being a powered aeroplane.

Nothing shows the coefficient. A read-only row was tried and removed: a number that configures itself has no
business being read and wondered about, and the speed it comes from sits one line above. It goes to the log,
for when a run has to be explained afterwards. The load factor went with it — the g an aircraft pulls is an
**output** of flying, which JSBSim computes itself, so as an input it was a field to understand in order to
leave alone.

## Curves, not one tangent

The exported flight model states **what AVL measured across a range of attitudes**, not one measurement plus a
rate. A rate is the tangent where it was measured and JSBSim continues it to any attitude it likes, which is
how a 0.94 kg model came to hold 40° of angle of attack. `AvlRunner.sweepAlpha`/`SWEEP_ANGLES_DEG` measures
thirteen attitudes from −10° to +20° in **one AVL session**, so it costs about what the single measurement it
replaces cost — opening AVL is the slow part, solving is milliseconds.

Two things about the sweep are the whole point of it, and both are pinned by `AlphaSweepCheck`:

- The attitude is **imposed** (`a a`), not asked for as a lift coefficient. The curve's independent variable
  has to be the one we chose, not one AVL picked.
- The controls stay at **neutral**. JSBSim adds the elevator itself through `Cmde·δe`, so a curve measured
  with the elevator trimmed would carry that trim inside `Cm` and count the elevator twice. The sweep and the
  trimmed stability file therefore do **not** agree directly — they agree once the control terms are added,
  which is the identity `AlphaSweepCheck` asserts against real AVL output, and the same sum JSBSim computes.

`JsbsimWriter.AeroCurves` holds one α grid and three curves on it, so they cannot disagree about which
attitude a row belongs to, and each **replaces** the constants it stands for: with a `CL(α)` table there is no
`cl0 + cla·α` as well, or the lift is counted twice (`AeroTableCheck`). Everything else stays one number,
because AVL is a linear solver and thirteen copies of the same number would only hide that; the sweep reports
what each derivative did across the range instead. Fewer than three points is a line, not a curve, and the
export says in the log which of the two it wrote.

**Drag is driven by attitude, not by the square of the lift.** JSBSim's `aero/cl-squared` follows the lift the
model just computed, so the day a lift curve bends over at a stall, a `cl-squared` drag would fall with it and
a stalled aircraft would have *less* drag than in normal flight. `CD(α)` from AVL — viscous and induced
together — replaces both the parasite constant and the induced term.

A table also **holds its last row** rather than extrapolating, so lift stops growing at absurd attitudes. That
is not a stall: a stall is viscous, AVL cannot see one, and the difference has to be stated rather than
implied. Adding a real one needs XFOIL (present in the project, wired to nothing) and is a separate feature.

The evidence is not the assertions. `JsbsimCurveCheck` exports the model for real, runs **JSBSim from the
command line** on a pull-up and compares the lift and drag JSBSim computes at each instant against the tables
in the file it loaded — they agree to 1e-16, and the aircraft holds 0.885 of lift past +20° instead of
inventing more.

## AVL's control variable is not an angle

AVL states control derivatives **per unit of its control variable**, and that variable is dimensionless: the
`.avl` CONTROL line carries a gain whose units are, in the editor's own words, *degrees deflection / control
variable*. JSBSim drives the aerodynamics from the deflection in **radians** (`fcs/elevator-pos-rad` and its
siblings), so a derivative handed over unconverted understates every control by `180/(π·gain)` — 2.9 times at
the editor's default gain of 20, 57 times at a gain of 1, 1.9 times for the eurofighter's canard.

That was the state of every exported model until `JsbsimExporter.buildAero` started converting. It is not a
rounding error: the eurofighter needs 25° of canard to trim, and an aircraft whose surfaces are three times
weaker than the model states will not trim in the simulator while trimming perfectly on paper. The invariant
to hold onto is that AVL's `Cmd·d` and JSBSim's `cmde·δ_rad` describe the same rotation of the same surface
and must produce the same moment; `ControlEffectivenessCheck` asserts exactly that, plus the factor at three
different gains. A gain of zero contributes nothing, because a surface AVL never deflects does nothing
whatever the units.

An empty result has to say what the analysis **answered**, not what the user might have forgotten. The
modal table used to read *'No oscillatory eigenmodes available. Define mass/inertia and run AVL again'*
whenever it came out empty — including when AVL had the masses, had answered, and the answer was eight
real roots with one divergent: an aircraft trimmed far from where it balances has no oscillatory pitch
mode at all. `MilF8785cEvaluator.whyNoModes` reports what came back, names each divergence with the time
its motion doubles in, and points at the centre of gravity (`ModalReportCheck`). A message that sends the
reader after an input that is already there costs more than no message at all.

**Whether it will fly is a warning, never a refusal.** `FlightSanity.warnings` answers a different
question from the requirements: nothing is missing, every figure is one the user chose, and the editor
is only doing the arithmetic they would do on paper — static thrust against weight (momentum theory,
`T = (2 ρ A P²)^(1/3)`, times a 0.6 static figure of merit), watts per kilogram, wing loading in g/dm²,
and what AVL says about stability in each axis. They are put to the user as a question — go on, or stop
and fix it — with 'no' as the safe default, and a model with nothing wrong is never interrupted. Both
answers reach the log, so the footer says what was decided and a cancelled launch cannot be mistaken
for a finished one. This exists because 3 W through a 10 cm propeller on a kilogram of aeroplane looks, from inside
FlightGear, exactly like a simulator that ignores the throttle — an hour went into the keyboard before
anyone questioned the motor. Every threshold is a model-flying rule of thumb, quoted in the message so
it can be argued with, and never applied to the model itself.

No silent fallbacks remain in the export path. The last two were removed: `detectControls` no
longer flies a control with an invented 25° deflection, and `buildAero` derives the aspect ratio
from the reference geometry instead of assuming 5.0.

What remains are **stated assumptions**: values the model genuinely cannot express, each documented
where it is defined, and none of them standing in for something the user should have entered.
Currently, all in `JsbsimWriter`/`JsbsimExporter`: the propeller's generic thrust and power
coefficient tables and its inertia (scaled from a DJI 9450), the gear friction coefficients, the
gear stiffness rule taken from FlightGear's c172p, and treating the motor curve's electrical input
power as shaft power. Replace one only with a better-sourced derivation, never with a guess.

`SimpleTrust` is a CRRCsim thrust model, and with the CRRCsim export gone it has no destination at
all, so the editor no longer offers to create one: the `+ Trust` button is removed. The node and
its serialization stay, so files saved with one still load and save it unchanged
(`LegacyRoundTripCheck` pins that with a model it builds itself — a compatibility guarantee cannot rest
on a sample nobody tidies up), and a model that has one is told plainly that it cannot be exported.
**Do not offer a control that builds something no export can consume** — that is the same failure as an
invented default, moved into the UI.

And whatever the tree can add, the tree can **delete**. A node is deletable when its class asks for
`ENABLE_BUTTONS.DELETE`, and `Delete` then removes it from whichever `@AvlEditorNode` list of its parent
holds it — found by reflection, so the rule needs no maintenance. It used to be a chain of instanceof
cases naming each parent and child type, and everything the chain did not name fell through to a message
on stderr while the tree looked as if the delete had simply not worked: a battery, a shaft, an engine, a
propeller, a fuel tank, a data row and a Simple Trust were all addable and none of them removable. A
component's own `Pos` or `Gearing` is not a list item and stays undeletable — without one it is broken,
not lighter. `DeleteKeyCheck` pins both halves: which classes offer it, and that deleting really removes
from every list the tree shows.
