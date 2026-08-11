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

### Running the checks — and why `sbt test` is not how

**`sbt test` runs nothing in this build, and prints `[success]` while doing it.** Measured: it
compiles the 58 sources under `src/test` and exits 0 in under a minute, having executed not one
check. `show Test/definedTests` comes back empty.

That is not a fault to be fixed by running it harder. There is **no test framework on the
classpath** — no ScalaTest, no MUnit, and not even `junit-interface`, so the `junit` dependency
cannot be discovered either — and `@Test` appears in no file. sbt has nothing to find.

A check here is an **executable object**, not a framework test:

```bash
sbt "Test/runMain com.abajar.avleditor.avl.mass.MassMirrorCheck"
```

The header of every check file states its own command that way. It prints `PASS`/`FAIL` per
assertion and calls `sys.exit(1)` if any failed, so **its exit code is the result** — which is what
lets a check drive AVL, XFOIL or JSBSim as a subprocess and read what came back, rather than being
confined to what a test framework can express. `show Test/discoveredMainClasses` lists them all.

Two things follow, and the first is the one that costs people time:

- **A green `sbt test` means nothing.** It says the tests compile. Anybody — human or agent —
  concluding from it that the checks pass has been told the opposite of the truth by a tool that
  looked like it was answering. This project already holds that a refusal reaching only the log is
  indistinguishable from success; this is the same failure wearing a success message.
- **A check that cannot find what it drives may excuse itself.** `JsbsimCurveCheck` and
  `DuctedFanFlightCheck` print `..._SKIPPED` and exit **0** when JSBSim is not installed, and they
  are the two that fly the exported aircraft. A run that reports success having skipped them has
  measured nothing about the export. Read the output, not only the exit code.

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

### One worktree per task, never the main checkout

**Every task is worked on in its own git worktree**, branched from the main branch:

```bash
git worktree add .claude/worktrees/<branch> -b <branch> origin/master
```

Never edit, branch or commit in the main checkout at the top of this repository.

**Why:** more than one agent can be working in this repository at the same time, and in the main
checkout they overwrite each other. There is one working tree there and one `HEAD`: whoever
switches branch, stages a file or stashes moves the ground under everyone else, and the damage is
silent — the second agent's `git status` shows changes it did not make, its commit picks up
another task's files, and a `git checkout` throws away work nobody has pushed yet. A worktree gives
each task its own directory, its own branch and its own index, so two tasks cannot collide however
they interleave.

The main checkout stays on the main branch and is only read. Work goes back through a branch and a
pull request, never a push to the main branch. A finished worktree is left in place unless the user
asks for it to be removed — `git worktree remove` deletes files, and deleting is the one thing that
cannot be undone.

`.claude/` is in `.gitignore`, and it has to be: the worktrees live under it, so an untracked
`.claude/` leaves this repository permanently dirty in `git status`. That is not cosmetic. Anything
watching for work at risk reads `git status`, and a repository that is always dirty is one that has
stopped being able to report the day something really is uncommitted — the alarm rings constantly,
so nobody hears the real one.

### Recent UI Changes

- The button to add a new section to a surface has been changed to a `+` button for a more intuitive user experience.
- Every mass the model states a position for — the geometry's masses and the propulsion components'
  and fuel tank's own positions — is drawn in the 3D view, sized by weight, and the selected one is
  moved with an axis handle per direction. A mass with no stated weight still gets a marker: its
  position is real. See `mass.MassMarkers`.

## Feature Planning

### Features, bugs and plans live in GitHub issues

**One issue per thing**, and a change of plan is a **comment on its issue** — not a rewritten file.

Before starting any task, the agent opens or updates the issue it is working on, in English, with
enough context that somebody else could pick it up: what is wanted, why, and what has been ruled
out. The issue is the plan; the branch and the pull request reference it.

**Why:** a plan in a working file is visible to whoever happens to hold that checkout, and it is
rewritten when the next feature starts, so the reasoning behind the last one is gone. Issues are
one per subject, they survive being finished, they can be read by everybody at once, and they can
be prioritised against each other — which a single file describing one feature cannot.

The same applies to work the repository already writes down in prose. A "not implemented" line in
`docs/mil-f-8785c.md` or a stated assumption in this file records a decision; the **work** it
implies belongs in an issue, or nobody schedules it.

### PLAN.md

`PLAN.md` is **history, and local only** (it is in `.gitignore`). It holds the record of the
CRRCsim-to-JSBSim pivot. It is not where new work is planned — see above — and it is not read to
find out what is outstanding, because it says nothing about anything done after it was last
written.

A scratch file for the notes of a task in progress is fine and stays out of git; what may not live
there is the plan itself.

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

**The exported flight model is the same aircraft whatever the model states it in**, which is the property
`ExportUnitsCheck` asserts: the same aeroplane written in metres and kilograms, in centimetres and grams and in
inches and ounces produces three files that agree to 1e-7 — a unit left unconverted shows up as a factor of a
hundred. It found four that were: the centre of gravity, the landing gear, the fuel tank's position and
contents, and the propeller's diameter all went out in the model's units while the reference geometry beside
them was converted. The CG was the sharpest, since it and the aerodynamic reference point are the **same
point** read twice — one converted, one not. Everything now goes through `JsbsimExporter.metres`.

The one thing already in SI is `Config.mass_inertia`: `calculate()` writes kilograms and kg·m² into it whatever
the model states, and that is why nothing converts it again.

**And the aircraft AVL is asked about is the same aeroplane too** — which it was not, in the one place it
matters most. `AVL.analysisLiftCoefficient()` is where the whole analysis's operating point comes from, and it
divided a weight in **newtons** by a density in **kg/m³** times a speed and a reference area **in the model's
own units**. A model in metres and seconds was right — every check here and every sample — which is precisely
why it survived. One in centimetres was analysed at a ten-thousandth of the lift coefficient it needs.

The other half of it could not be reasoned out and was **asked**, the way `EigenvectorUnitsCheck` asked what
AVL's mode shapes are in: **AVL's run-case velocity is in m/s whatever `Lunit` says.** `Lunit` converts the
geometry; the run case is stated in SI beside `g` and `rho`, as AVL's own mass-file comment says ("must be in
the unit names given above, i.e. m,kg,s"). `AnalysisUnitsCheck` writes one aeroplane out twice, in metres and
in centimetres, and flies both: at the same velocity *number* AVL returns identical eigenvalues, and at a
hundred times it, motions a hundred times faster. So the speed handed to AVL was a hundred times too high for a
centimetre model, and every derivative, mode and deflection came from that aeroplane.

`AVL.analysisVelocityMetresPerSecond()` and `analysisReferenceAreaSquareMetres()` are the one place each is
converted, and `Configuration.getVelocityMetresPerSecond()`/`getReferenceAreaSquareMetres()` are the same two
numbers reached from the run's own record — the check asserts the two routes agree, because a run whose record
disagreed with the run would be worse than either being wrong.

Two more of the same family fell out of it:

- `UnitConversor.convertToSeconds()` **returned the factor and threw the quantity away**: five minutes came
  back as sixty. It was right everywhere it was used, because `ModelUnits.secondsPerTimeUnit()` passes 1.
- `convertToMetersPerSecond()` **multiplied** by the time factor instead of dividing — the sibling of
  `MINUTES_TO_SECONDS = 36`, and hidden the same way: nothing called it.

`FlightSanity`'s wing loading read `Sref` as m² beside a weight in kilograms, and the mass file wrote
`g = 9.81` by hand next to a `GRAVITY = 9.80665` used to derive the coefficient. Both now go through the one
place, for the reason the whole section exists: **a second table is what lets the first stay wrong.**

**A section can be dragged by an axis as well as by its centre.** The centre drag snaps to the nearest vertex,
which is what puts a station exactly on a fuselage; an axis handle moves it along one direction and nothing
else, which is what a wing's geometry is usually built from. Both are wanted, so both are offered and the
pointer takes whichever handle is nearest — the trailing edge for the chord, the three arrows for the
directions, the leading edge for the snapping drag. The projection arithmetic is the same one the masses and
the bodies use (`Viewer3DGL.axisDragDelta`, pinned by `AxisDragCheck`); it was never mass-specific, and it is
no longer named as if it were.

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

## The aircraft the checks fly

`TestAircraft` builds one in code: a plain 1.1 kg sport model — rectangular wing with ailerons, tailplane with
an elevator, fin with a rudder, its centre of gravity ahead of the neutral point. The checks that need a real
aeroplane to run AVL or JSBSim on use it, and **not** the files under `samples/`.

Those are the user's aeroplanes. They get edited while the editor is being tried out, and a check that loads one
fails for reasons that have nothing to do with the code: a ducted fan appeared in the eurofighter between two
runs of `DuctedFanFlightCheck` and broke it, because the check added a second fan and the export took the first.
`LegacyRoundTripCheck` already followed this rule for the same reason.

It is also a better subject. The eurofighter is unstable in all three axes, has a span efficiency of 0.19 and no
oscillatory modes at all; the test aircraft trims at +3.4° with `Cma` −0.81, `Cnb` +0.076, `Clb` −0.045,
`e` = 0.96 and two oscillatory modes, so a check about the aerodynamics is not fighting a divergence at the same
time.

Two things it had to get right, both found by AVL and JSBSim refusing to play:

- **Sections must be appended in span order.** `Surface.createSection()` inserts a station *between the last
  two* and interpolates it — right for the editor's `+` button on an existing wing, wrong for building one.
  Used that way it produced a wing running root, tip, mid, and AVL built a folded surface out of it and reported
  a **negative span efficiency**.
- **Mass has to be spread, including at the tail.** With every mass piled around the wing the pitch inertia came
  out four times too small, the pitch dynamics became far faster than any real model's, and JSBSim's integration
  gave up 0.2 s into a full-throttle run.

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
implied. Where the wing gives up **is** now measured — see the next section — but nothing yet models what it
does past that point, and the exported tables are unchanged.

The evidence is not the assertions. `JsbsimCurveCheck` exports the model for real, runs **JSBSim from the
command line** on a pull-up and compares the lift and drag JSBSim computes at each instant against the tables
in the file it loaded — they agree to 1e-16, and the aircraft holds 0.885 of lift past +20° instead of
inventing more.

## Where the wing gives up, which AVL cannot say

AVL is inviscid: it will hand you lift at forty degrees as confidently as at four. XFOIL is viscous and knows
where **one aerofoil** stops lifting. Between the two is the question that matters — where does *this
aeroplane* stop lifting — and it is answered by the **critical-section method**, from NACA Report No. 572,
*Determination of the Characteristics of Tapered Wings* (Anderson, 1936), which is in the repository at
`docs/references/naca-tr-572.pdf` along with everything else the editor cites:

> As soon as the `c_l` curve becomes tangent to the stalling `c_lmax` curve, the section at that point reaches
> its maximum lift coefficient and stalling should soon spread over a considerable part of the wing.

So: AVL's `fs` gives the **spanwise loading** — every strip's local `c_l`, referred to that strip's own area
and chord, which is exactly what a section `clmax` is measured against, so nothing has to be rescaled between
them. It rides along in the alpha sweep's own AVL session, because the solve that produces the totals already
contains it. XFOIL gives each section's `clmax` at that section's own Reynolds number. The wing stalls at the
attitude where the first strip reaches its own section's limit, and `CLmax` is read off AVL's measured `CL(α)`
there. `xfoil.WingMaximumLift` is the arithmetic, `xfoil.StallAnalysis` the plumbing.

**The stall is read from the wing, and which surface that is, is measured.** It was read from the first
station *anywhere* on the aircraft, which is not what TR 572's method is about: a canard or a tailplane
reaching its section limit first is not the aeroplane giving up, and on a close-coupled canard it is the
design intent — the canard is there to give up first and hold the wing below its own limit. On the sample,
whose canard sits at `dAinc 25`, that produced a `CLmax` of **0.311** and a stall speed out of it that then
set the approach speed 3.2.1.3 was judged at. `WingMaximumLift.onsetBySurface` now answers per surface and
`liftShareBySurface` picks the one carrying the lift — by `sum of cl x area` out of the total, never by a
name or by which surface is biggest. Surfaces that give up earlier are named with their attitude, because
past that attitude AVL is still solving them as though they were lifting.

This **replaces `AeroDerivation.CLMAX_3D_FACTOR`**, a flat 0.9 whose own comment said the accurate route was
this one. The factor is not nonsense — on the check aircraft's rectangular wing the answer is 0.901 of the
section limit — but on the same aeroplane with a 4:1 tapered wing of the same span and area it is 0.953, and
nothing about either wing tells you which. `SpanwiseLoadingCheck` runs both through AVL and pins the other
half of Anderson's finding as well: the rectangular wing gives up at the **root**, the tapered one **two
thirds of the way out**, where the ailerons are.

Six things this had to get right, none of them guessable:

- **A mirrored surface is a separate block** in AVL's file, named `wing (YDUP)`, with its stations at negative
  `y`. Both halves are real strips of one aeroplane; the sections that state the aerofoil belong to the half
  the model draws, so a mirrored strip is reflected back before it is told which two sections it lies between.
- **A strip's `c_l` is not quite straight in attitude.** TR 572 splits the loading into a basic distribution
  that does not change with attitude and an additional one proportional to it, which makes each strip a
  straight line and the whole method two numbers per station. Measured across thirteen attitudes it is
  straight to 2.5 % — and it bends the way it must, because AVL's solution is linear in the freestream
  **vector**, whose components are `cos α` and `sin α`. Two and a half percent is about a degree of attitude
  at the stall and there is no reason to spend it, so the crossing is read off the measurements and the
  straight line survives only as a **reported residual**: the report's approximation measured, not assumed.
- **Reynolds is circular and is iterated.** A section's `clmax` depends on Reynolds, which depends on the
  speed, and the speed wanted is the one at which that `clmax` is reached. Two or three passes close it, and
  polars are cached by aerofoil and Reynolds so the later passes mostly cost nothing.
- **Air's viscosity is a stated assumption, and a derived one.** The model states a density and no
  temperature, and `mu` depends on temperature and nothing else, so it is standard sea level — 288.15 K
  through the U.S. Standard Atmosphere 1976's own Sutherland equation, `mu = beta T^(3/2)/(T+S)`, both
  constants cited to the pages in `docs/references/ussa1976-excerpt.pdf`. It is a mild assumption and the
  reason is worth stating: a factor of two in Reynolds moves `clmax` by a few percent and the stall speed by
  the square root of that.
- **XFOIL does not always show an aerofoil giving up**, and that is a real answer rather than an error. A 10 %
  symmetric section at Re 60,000 — an ordinary model tailplane — comes back still climbing at 20°, because the
  code does not model the separation that would end it. Such a station has **no known limit** and takes no
  part in deciding where the aircraft stalls; refusing the whole aeroplane for it would throw away a wing that
  answered perfectly well. The result **says which stations those were and what it means**, because the
  aircraft could give up there first without this noticing.
- **A file with its executable bit set is not a working XFOIL.** The published Linux binary was built by CI on
  `ubuntu-latest` and needs a newer C library than Ubuntu 22.04 has; it dies at the loader before printing a
  word, and every polar comes back empty — which, read through the analysis, looks exactly like an aerofoil
  that never stalls. `XfoilManager.usable` now asks it to build a NACA 0012 and checks that it did, and the
  workflow pins the build to `ubuntu-22.04`.

The stall is a **stage of its own and never a refusal**: nothing AVL reports depends on it, so a missing XFOIL
costs one row of the report rather than the run, and the row says which of these it was, by name, in place of
a stall speed nobody measured (`Configuration.setStallProblem`).

What it unblocks is MIL-F-8785C **3.2.1.3, flight-path stability** — the last longitudinal criterion, and the
one that had been waiting on this rather than on a field. It is measured at `Vomin`, and asking the user for
that would have been a trap: to choose an approach speed you have to know where the aircraft stalls, and a
speed entered below the stall would have been answered with a confident Level 1 for a condition the aeroplane
cannot reach. See `docs/mil-f-8785c.md` for the derivation, including why the thrust and the glide path both
cancel out of the slope, and where the 1.3 comes from (14 CFR 23.73, also in the repository).

## A ducted fan is a propeller with different curves

JSBSim's `propeller` is not "a propeller": it is a machine that absorbs shaft power and produces thrust as a
function of advance ratio, and the whole of what it does comes out of two tables, `C_THRUST` and `C_POWER`
against `J = V/(nD)`. So an EDF needs neither a new engine type nor a fabricated turbine — it needs the right
two curves. What the exporter writes for a propeller is a generic APC 9x4.5, declared as an assumption; for a
fan those curves are wrong in the way that matters, since a free propeller's thrust is spent by `J ≈ 0.73` and a
fan holds its own far past it.

`DuctedFanCurves` derives them. A duct does not contract the wake, so the exit area is the fan area, and with
`k = Ve/(nD)` — the air the fan throws per revolution — momentum theory gives

```
Ct(J) = (pi/4) k (k - J)      Cp(J) = (pi/8) k (k^2 - J^2)
```

One parameter, and three properties say it is the right model rather than a convenient one, all asserted in
`DuctedFanCurvesCheck`: its efficiency is `2J/(k+J)`, Froude's ideal; `Ct` reaches zero at `J = k`, so `k` is
the advance ratio at which the fan stops pushing — the speed at which the aircraft matches the exhaust; and at
rest it gives `2^(1/3)` = 1.26 times a free propeller's static thrust on the same power and diameter, the known
advantage of a shrouded rotor.

**No thrust is asked for, because the specifications already determine it.** A fan is usually bought as a rotor
and a housing — a listing that quotes an inside, an outside and a lip diameter is that kind — and no thrust is
published for one, since the thrust depends on the motor fitted. It does not need to be: with the bore, the
revolutions and the power, momentum theory gives the thrust at every speed.

What is left is the losses. Momentum theory knows nothing about tip clearance, the lip or the diffuser, and the
power it is given is electrical rather than shaft power, so the ideal overstates the thrust by about half.
`FigureOfMerit` is 0.5 — about 0.8 for the motor turning electrical watts into shaft watts, times about 0.65 for
the duct — a **stated assumption**, of exactly the kind `FlightSanity` has always used with its 0.6 for a
propeller's static thrust, and it is also where complete units' published thrust figures land when worked back
through this derivation. It scales `Ct` and leaves `Cp` alone, since a loss costs thrust for the same shaft
power, and it scales only the height of the curve: `k`, where the thrust runs out, is physics and the constant
must not touch it. The export log states the thrust that comes out, the ideal it came from and that the ratio
between them is assumed rather than measured.

A field for a measured static thrust was tried and removed. It made the aircraft depend on a number that only
complete units publish, which is not what people buy.

Everything else the fan states is its own: the duct's **inner** diameter (not the housing, not the inlet lip),
the blades, and a length that is drawn and enters no calculation. The revolutions and the power are **not**
asked for again — they belong to the motor that drives it, and a figure stated twice is a figure with two
answers.

**The thrust acts at the exhaust, not at the fan.** The momentum forces act where the air crosses the
aircraft's boundary — the intake lip and the nozzle — and everything between them is internal pressure the
structure carries. A duct that enters and leaves 10 cm up with the fan low pushes as if the thrust were 10 cm
up, because that is where the air leaves; the fan is a pump and its own height is not the line of action of
anything. Standing still the pitching moment is exactly `gross thrust × exhaust height`, with nothing from the
fan's own position.

**A shaft is an assembly and moves as one.** It has a position of its own, and the motor, the propeller or the
fan mounted on it state where they sit **within it** — so dragging the shaft carries the lot, and each part can
still be placed inside it afterwards. It starts at zero, which is why a model written before this is unchanged:
relative to nothing is absolute. `Shaft.absoluteX/Y/Z` is the one place that adds the two together, so nothing
that weighs, draws or exports a component can disagree with the rest about where it is, and a drag in the 3D
view writes back where the part sits within the assembly rather than where it is in the world.

The shaft gets a **marker of its own** in the 3D view, with no weight — a shaft is not a part that weighs
something — because a group has to be grabbable to be a group. `ShaftAssemblyCheck` pins all of it, including
that the exported thrust adds the shaft, the fan and the exhaust exactly once each.

So a fan states **two** positions: its own, where it weighs, and an exhaust, where it pushes. The exhaust is an
**offset** from the fan while `exhaustFollowsFan` is set — zero for a short duct, so the ordinary case needs no
thought, and moving the fan carries it along — and absolute when it is not, for modelling a real duct that ends
somewhere else. Toggling converts the numbers, so the exhaust never jumps. In the 3D view the exhaust is drawn
as the disc the air leaves through, with a line to the fan: the duct's shape is not stated, so it is a line.

What that gets right is the **height**: for a thrust along the fuselage axis the pitching moment is
`T × (offset across the axis)`, so an exhaust above the centre of gravity drops the nose under power and one
below it lifts it. The station along the fuselage never entered that moment and still does not — front or back
changes the balance, through the unit's mass, and not the thrust.

One limitation, stated rather than hidden: JSBSim applies one force at one point per engine, so an intake at a
different height from the exhaust cannot be expressed. The exact equivalent height is
`(Ve·z_exhaust − V·z_intake)/(Ve − V)`, which depends on speed and diverges as the aircraft approaches the jet
velocity — an S-duct pushes and pitches independently. At rest the exhaust alone is exact, which is why it is
the point the export uses.

In the 3D view it is a cylinder of the bore over that length. Only the length can be dragged: a shape's sizes
are resizable **per axis**, because the bore is what the thrust is derived from and editing it by eye would be
editing the propulsion, while the length is a drawing and nothing else. A handle for a size that cannot change
would be a lie, so none is drawn.

The derivation is expressed in JSBSim's own definitions, so it is worth nothing until JSBSim agrees:
`DuctedFanFlightCheck` exports a model with a fan, runs **JSBSim from the command line** at full throttle, and
finds its thrust is `Ct x rho x n^2 x D^4` with the coefficient from the file it loaded, to 0.0012 %. Had
JSBSim meant radians per second, or another power of the diameter, every other assertion would still have
passed and the aircraft would have had the wrong thrust.

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

## The lateral model, and the three things AVL does that a textbook does not say

MIL-F-8785C 3.3.2.2 and 3.3.2.4 measure features of a **time history** — the roll rate at the peaks of a
step-aileron response, the sideslip excursion — so the response has to be built before anything can be
judged on it. `LateralModel` assembles the four-state lateral system from AVL's derivatives and the
inertias, and `LateralModelCheck` verifies it the only way that needs no second flight dynamics model:
**the four roots of that matrix are the dutch roll pair, the roll mode and the spiral AVL already
returned.** Comparing characteristic polynomials avoids writing an eigensolver and is exact.

It disagreed on the first run, which is what it is for. Three causes, all wiring rather than formula, and
none of them guessable:

- **The inertias were about the origin, not the CG** — see below. Fixed there; it is not a lateral-model
  bug, it was reaching the JSBSim export too.
- **AVL adds the apparent mass of the air.** It prints an "Apparent mass, inertia" tensor beside the
  airframe's and solves the modes with the sum. On the check aircraft the apparent roll inertia is
  **15.6 %** of the airframe's, and leaving it out put the roll mode at −18.1 against AVL's −15.7. It is
  read from what AVL prints rather than derived here, so the two cannot drift apart. On a full-size
  aeroplane it is negligible; a 1.1 kg model with 1.2 m of span moves an appreciable amount of air.
- **AVL states its derivatives in stability axes and solves its modes in body axes.** Its own output says
  `Stability-axis derivatives...` and primes the moments `Cl'`, `Cn'`, while the eigenvector it prints is
  `u,v,w,p,q,r,phi,theta,psi`. The derivative set is therefore rotated by the trim angle of attack on
  **both** indices — the moment produced and the rate it is taken against. Three degrees sounds ignorable
  and is not: `Clp` is five times `Clr`, so the term it lends to `Clr` is a third of `Clr` itself, and
  `Clr` is one of the two nearly-cancelling products that decide whether the spiral converges. That term
  alone moved the determinant from −50.4 to −28.7 against AVL's −29.4.

With all three, every coefficient agrees with AVL to better than 2.5 %.

**A peak is where the slope crosses zero, not the biggest sample.** The measured quantities are all
turning points of the response, and they were first found by scanning a finely sampled array for a local
maximum. That is wrong, not merely wasteful: near a peak the curve is almost flat, so comparing three
consecutive samples fires in the wrong place. The model holds `A` and `B`, so the derivative is exact at
any instant — the crossing is located by secant on it and the value taken by cubic Hermite, which uses
both endpoints' values *and* slopes. On the check aircraft that moved `p_osc/p_av` from 0.0077 to
**0.0459**, a factor of six, and the answer now holds identically when the step is halved, which it did
not before. `LateralModel.turningPoints`.

**Neither is measured on an aircraft that is running away.** Both are features of a *time history*, and a
time history of a divergent system measures how long it was integrated, not the aeroplane: the sample
reported "25.2 degrees of proverse sideslip" and "the roll rate holds 100 % of its peak" on an aircraft
whose yaw doubles every 0.12 s — an exponential read off at an arbitrary moment, and a sag that never
happened because nothing was oscillating. A lateral motion that **doubles at least once inside the window**
therefore refuses both rows and says so, which needs no invented threshold and is the same language the
runaway list is already written in. A slow spiral — which most models have and fly through — doubles in
tens of seconds and does not trigger it.

On that model **3.3.2.2 and 3.3.2.4 are then measured, not derived**: a step aileron input is integrated,
the peaks of the roll-rate trace give `p_osc/p_av` by 6.2.6's own formula, and the largest sideslip in the
stated window gives `delta_beta`. Two things about the window, both of which changed the answer:

- The command is held *"until the bank angle has changed **at least** 90 degrees"* — at least, a minimum.
  Cut at exactly ninety, a model that rolls at 450 deg/s reaches it in a fifth of a second and the dutch
  roll never gets to put the sag in the roll rate that the criterion is entirely about. One peak, nothing
  to measure. Held for three dutch-roll periods there are three.
- The sideslip window's "2 seconds" is a **dimensional** time and scales with the aircraft like every other
  one here — and is then cut at ninety degrees of bank anyway, because the term that drives sideslip is
  `g cos(alpha) phi / V`, linear in `phi`, and a model that has rolled through three hundred degrees is
  being asked a question its equations cannot answer. Read out that far it claimed 38 degrees of sideslip;
  cut where the linearisation still holds, 16.6. The verdict says which window it used.

## An inertia is about a point, and that point is the centre of gravity

`Config.calculateInertiasMasses` summed the moments of inertia about the **origin of the drawing** — wherever
the user happened to start, usually the nose — so every inertia but `I_xx` carried a parallel-axis term
`m d²` that does not belong to the aircraft. It is what the JSBSim export writes into `<mass_balance>`, and
JSBSim reads those as inertias **about the CG** it is given on the very next line. An aircraft drawn with its
origin 0.3 m ahead of its balance point went out far too reluctant to pitch and yaw, and nothing in the file
pointed at it: every number looked plausible.

AVL was never affected — it is handed the point masses themselves and works its own inertias out — and that
is how the two came to disagree and how this was found: a lateral model assembled from these inertias gave
roots that did not match the ones AVL had already returned for the same aeroplane. On the check aircraft
`I_zz` was 16 % too large, and that aircraft's origin happens to sit close to its CG.

`InertiaReferenceCheck` pins the **property**, not a number: **moving the whole aeroplane must not change
what it weighs about its own centre**. That is the exact statement of the bug and it survives any rescaling.
Forty-four checks were green before the fix and forty-four after — none of them was looking at the inertias
at all, which is its own lesson.

## The standard is in the repository

The flying-qualities criteria come from **MIL-F-8785C** (5 November 1980), and it is committed as
`docs/MIL-F-8785C.pdf` with its tables transcribed and its pages cited in `docs/mil-f-8785c.md`. **Every
threshold in `MilF8785cEvaluator` names its section, its table and its PDF page.**

That is not ceremony. The three limits the editor already applied turned out to be exactly right, and one
number sitting beside them — a 10-second threshold deciding whether a lateral divergence counted as a slow
spiral — was in no table at all; the standard says 20 s (TABLE VIII). A reader had no way to tell the two
apart, because neither cited anything. A number that cannot trace itself to a page is either a stated
assumption, said out loud as one, or a bug.

**Six motions are judged, not three.** The roll mode (3.3.1.2, TABLE VII) and the spiral (3.3.1.3,
TABLE VIII) are **real roots**, and the table used to be built from the oscillatory modes alone, so both were
thrown away with the rest of the real roots — and the table was drawn only when something oscillated, which
is exactly when an aircraft with nothing but real roots has least to say for itself. The coupled roll-spiral
(3.3.1.4) is the sixth.

### What units AVL's mode shapes are in, which nobody had established

AVL prints each mode's amplitudes of `u, v, w, p, q, r, phi, theta, psi`, and those have **different physical
dimensions**. TABLE VI's footnote is written in terms of `|phi/beta|`, and `beta` is not something AVL
prints: it prints the lateral velocity. If `v` is a velocity then `beta = v/V`; if AVL already divided by
`V` then `v` **is** `beta`. The two readings differ by a factor of V — twenty on a 20 m/s model — on a
quantity the standard compares against a fixed 20.

So it was **asked rather than assumed**. `EigenvectorUnitsCheck` flies the same aircraft at 15 and 45 m/s
and reads the answer off how things scale. Three findings, all of them things the editor had been taking on
trust:

- **The eigenvalues are in 1/s.** The dutch-roll frequency went 6.885 -> 19.582, a ratio of 2.84 against a
  speed ratio of 3.00. The whole modal report reads `sigma` as 1/s and had never checked.
- **The velocity components are dimensional**, so `beta = v/V`. `v/(phi+psi)` scaled by 3.20, not by 1.
- **`|phi/beta|` is not a constant of the airframe.** It fell from 0.325 to 0.075 over that speed range, so
  `wn^2 |phi/beta|` came out **15.4 at 15 m/s and 28.8 at 45 m/s** — the same aircraft on both sides of the
  footnote's trigger. An earlier draft of `docs/mil-f-8785c.md` claimed a model "clears 20 easily"; the
  measurement says it straddles it, which is why the augmentation is computed at the condition analysed
  rather than assumed on or off.

The footnote itself is now applied. Its coefficients and its 20 are verbatim from p. 22; `beta = v/V` is
measured; and the **size scaling of it is ours** — the 20 is a frequency squared and the increment is a
frequency, so the trigger becomes `20 r^2` and the excess is divided by `r`, which at full size is the
footnote exactly as written.

**The short period is judged twice, because the standard asks two things of it.** TABLE IV is the damping,
and it was all the editor ever checked; 3.2.2.1.1 is the **frequency**, and an aircraft can be beautifully
damped and still answer the elevator far too slowly or far too sharply for the g its wing makes. The
quantity is the Control Anticipation Parameter, `CAP = wn_sp^2 / (n/alpha)`.

That requirement is **drawn** rather than tabulated — three log-log figures — which is why it looked like it
needed a scan measured by eye. It does not: the boundaries are lines of **constant CAP** and each carries
its value printed up the right-hand edge, so the figures are four numbers per Flight Phase. And `n/alpha`
needs no weight, no air and no wing area: in level flight the lift equals the weight, so it collapses to
`CLalpha / CL_trim`, which is the same identity `AVL.analysisLiftCoefficient()` uses, read backwards.
`ShortPeriodQuicknessCheck` pins it, including that CAP scales as a frequency **squared** with the
aircraft's size.

**Two static rows, and both were nearly written from a wrong reading.** 3.2.1.1 is *not* `Cma < 0` and not
the static margin, whatever its title suggests: section 3.2.1 is longitudinal stability **with respect to
speed**, and it says there shall be **no aperiodic airspeed divergence** for Levels 1 and 2, with Level 3
relaxed to a doubling time of 6 s. The eigenvalues already answer it — it is the runaway whose mode shape is
speed-dominated. And 3.3.6's steady-sideslip requirements turn out to be written in pedal forces and in a
linearity that **AVL, being a linear solver, cannot fail**; asserting it would be asserting nothing. What
survives is the sign convention (`Cnb` positive, `CYb` negative, `Clb` negative) plus 3.3.6.3.2, the one
requirement in that family that is quantitative and needs no forces: the dihedral effect must not cost more
than **75 % of the aileron** at the 10 degrees of sideslip 3.3.7.1 asks for (`StaticStabilityCheck`).

**And a seventh row that is not a motion at all: roll response** (3.3.4, TABLE IXa) — how long the aircraft
takes to bank 60 degrees with the stick hard over, which is the first thing a model flyer would notice and
the editor never computed. It is derived, not assumed: the ailerons' rolling moment balancing the roll
damping gives the final roll rate, the roll mode's time constant says how long it takes to arrive, and
`phi(t) = p (t - tau (1 - e^-t/tau))` gives the angle. `Cldelta` is converted through the control's **gain**,
because AVL states it per control variable and not per degree — the same factor whose absence made every
exported JSBSim model's controls three times too weak. And when the aileron, its gain, its travel, the roll
mode or the roll damping is missing, it **names the one that is missing** instead of filling it in: a roll
rate computed from an invented deflection would look exactly like a measured one (`RollPerformanceCheck`).

**A Level, not a pass.** The standard is written in three of them and only Level 1 is "clearly adequate";
an aircraft that misses it is usually flyable rather than broken. A row now says which Level it reached
**and what kept it from Level 1** — a bare Level would be the old FAIL with a number on it, telling the
reader where they are and not which way to move.

**The Flight Phase Category is the one thing the aircraft cannot tell us**, and it is not asked for either.
It is the mission, not the machine: the same airframe flown gently is Category B and thrown around is
Category A, which wants 0.35 of short-period damping rather than 0.30, 0.19 of dutch-roll damping rather
than 0.08, and 60 degrees of bank in 1.3 s rather than 1.7.

It was a **field on the model** — `AVL.flightPhase`, "How it is flown" — and that was the wrong shape for
it. Judging a Category is arithmetic over numbers the run has already produced: no second AVL pass, no
second XFOIL. So asking the user to pick one bought nothing and cost them a setting to understand, and it
quietly answered 3.2.1.3 — stated for the landing approach and for nothing else — with "does not apply" to
everyone who never found the field.

**All three are judged, and the report has a column each** (`MilF8785cEvaluator.evaluateEveryCategory`).
The answer is better than the one a choice gave: not "Level 1", but *"Level 1 flown gently, Level 2 thrown
around"* — a fact about the aeroplane rather than a configuration of the report. The split it rests on is
that **what was measured is the aircraft and what it is compared against is the mission**, so a row carries
one measurement and three verdicts. Two do not, and the shape says so rather than pretending: roll response
is measured against a different bank angle in each Category (TABLE IXa), and flight-path stability exists
only in C. There is deliberately **no default Category** — a default is what a caller falls into without
deciding — so every caller names the one it means, and `EnvelopeSweepTool` says in its header that its CSV
is Category B.

A file saved with the old `flightPhase` key still loads: the YAML reader skips properties that no longer
exist, which is what `CRRCSimRepository` set `skipMissingProperties` for in the first place.

### The criteria follow the aircraft's size

This editor designs large aircraft and small ones, and MIL-F-8785C is written for **piloted, full-scale**
airplanes. A frequency floor in radians per second written around a ten-metre airplane means nothing to a
metre and a half of one: applied unchanged, a model clears both dutch-roll frequency floors whatever it is
like, leaving one of the three criteria doing any work. So the aircraft's own span sets them — no scale
field, no class to pick, nothing to know in advance.

**The standard's own numbers already work this way**, which is what makes this a derivation rather than a
guess. TABLE VI asks 1.0 rad/s of Classes I and IV and 0.4 rad/s of Classes II and III. Against spans read
off NASA CR-2144, *Aircraft Handling Qualities Data* (1972) — a contemporary of the standard, tabulating
the fleet it was written around — `sqrt(g/b)` gives:

| Aircraft | Class | Span | `sqrt(g/b)` | TABLE VI asks |
|----------|-------|------|-------------|---------------|
| F-104A | IV | 6.687 m | 1.211 | 1.0 |
| F-4C | IV | 11.787 m | 0.912 | 1.0 |
| C-5A | III | 66.812 m | 0.383 | 0.4 |

The two Class IV aircraft **bracket** their row rather than one of them approximating it, and the Class III
figure is within 4 %. Two rows an order of magnitude apart in span, one formula, **no fitted constant**. A
size-independent threshold could not do that.

**The reference span is derived too**: `sqrt(g/b)` equals TABLE VI's 1.0 rad/s exactly when `b = g =
9.81 m`, which is where the standard's own floor and the law agree — and it lands between the two fighters
rather than on either. It was 11 m, a Cessna's span recalled rather than looked up, until the spans were
verified. `FroudeScaleCheck` asserts the property, not the numbers, so it survives any rescaling.

It is applied **conservatively**: a threshold is used exactly as written at the reference span and above,
and scaled only below it — the range the standard never covered. A full-size aircraft is therefore judged
by the standard verbatim, and a 1.5 m model gets a dutch roll wanting 1.02 rad/s and a spiral that must take
7.8 s to double. Both figures are always shown, the stated one and the applied one: a verdict that silently
moved the goalposts would be worse than one that never moved them.

The span reaches the evaluator through `Configuration.getSpanMetres()`, written beside `Bref` from the same
AVL run. It is the one place in the editor that stores a unit factor rather than following the model, and
deliberately: a `Configuration` is the record of one run, so the pair cannot go stale against each other
however the model is edited afterwards. An aircraft whose span never arrived is quoted verbatim, never
guessed at.

**What runs away is the headline, whatever else was found.** A root with a positive real part grows, and it
is the most important thing an AVL run can say — and it was the easiest to miss, twice over. The divergences
were reported *only when the modal table came out empty*, so an aircraft with one passing oscillatory mode
and three runaways showed a green PASS and said nothing about them. And a **growing oscillation** — positive
real part *and* a frequency — was not counted as a runaway at all: it went into the table, where its damping
ratio comes out negative and the verdict read "too lightly damped at −0.12", as though it merely wanted a
bigger fin. An oscillation that grows is a runaway that swings on its way out. `MilF8785cEvaluator
.divergences` and `runawaySummary` are always shown, above the table, and they now include both kinds.

A mode with **no real part at all** used to vanish from both: `sigma > 0` excluded it from the runaways and
`omega > 0` from the table. `neutralModes` says it sits exactly on the boundary of stability, which is not a
fault but is worth a sentence.

Each one names **which axis** is running away, from the mode shape AVL gives, because that is what decides what
to change: pitch is the centre of gravity, a fast lateral one is the fin, a slow one is the spiral mode most
models have and most pilots fly through, and without a mode shape it says it cannot tell rather than guessing.
The doubling time carries the urgency — under half a second there is no flying it at all.

**A growing motion is not a badly damped one, in the row as well as the headline.** That hole was closed
by halves: a mode with `sigma > 0` and a frequency was added to the runaways, so the headline says the
aircraft will not fly — but the row went on describing it in the language of damping, *"too lightly damped
at −0.16, more tailplane"*, which reads like an aeroplane that wants a bigger tail rather than one that is
leaving. Wherever `zeta` comes out negative the row now says the motion **grows**, every swing bigger than
the last, and points at the runaway list. `MilF8785cEvaluator.growingInstead`.

**Every branch is walked, because a total function cannot report a case nobody thought of.** Each verdict
comes from an `if/else` chain ending in an `else`, so the evaluator always answers — the cheapest guarantee
there is, and the one that hides gaps. `VerdictSweepCheck` walks **1764 cases**: every sign of sigma against
every kind of omega, seven mode shapes, three sizes, three Flight Phases. It asserts the properties that
must hold whatever comes back — a Level and its pass flag agreeing, no `NaN` reaching a sentence a user
reads, no Level claimed by a row that says it could not judge, the runaway list and the table telling the
same story — rather than the wording of any one case. It found the half-closed hole above on its first run.

**`RowOutcome` is sealed too, and that was the other half of the lesson.** What a row concluded used to be
readable only by sniffing the verdict's opening words — `startsWith("Not judged")` — which is the same
failure as the `if/else` chain it came from: a wording nobody thought about reads as some other outcome, in
silence. `Reached(level)`, `WorseThanLevelThree`, `NotFound`, `NotJudged`, `OnTheBoundary`. The results
window matches on it exhaustively, so a new kind of outcome is a compile error there rather than something
that falls through to the plain text, and `VerdictSweepCheck` asserts the outcome agrees with the Level and
the pass flag across all 1764 cases.

`RunawayAxis` is a **sealed set**, not a chain of `if/else` ending in a bare `else`. An `else` answers every
case with confidence, including the ones nobody thought about — which is how a mode with no dominant axis
came to be announced as "yaw and roll". It has a `Mixed` case now, which says so.

And none of it is MIL-F-8785C. The standard says how much damping a motion needs; it has no opinion about
fins or centres of gravity. The remedies are model-flying judgement and are reported under their own
heading — *"read from AVL's eigenvalues, not from MIL-F-8785C"* — rather than inside the table of the
standard's verdicts, where they used to sit and read like quotations.

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
gear stiffness rule taken from FlightGear's c172p, and the motor's coil resistance. Replace one
only with a better-sourced derivation, never with a guess.

### An electric motor is stated by its constants, never by a power

The electric export writes a **`brushless_dc_motor`** — `velocityconstant` from the most unloaded
point of the motor's data curve, `noloadcurrent` from its idle row, `maxvolts` from the battery, and
the coil resistance as the one assumption above. It briefly wrote an `electric_engine` instead, rated
in watts, and that is a mistake worth never repeating: **constant power at zero rpm is unbounded
torque**, so the rotor could not be spun up from rest at all. Measured, 116 of 121 samples came back
`nan` within 0.15 s of the throttle opening — on the ordinary propeller as readily as on a ducted fan
— and no timestep, no rotor inertia and no throttle rate limit removes it, because the singularity is
in the engine model rather than in the integration. A brushless motor's torque at zero rpm is its
stall torque, which is finite; in the words of the comment that the swap deleted, *the model
self-limits RPM via back-EMF, which a bare power model does not*.

The **declared cost, accepted deliberately: FlightGear 2020.3 and earlier will not load the model**,
reporting "Unknown engine type". That is a matter of dates rather than of support — JSBSim gained the
element in January 2022 and that FlightGear is from December 2020 — so every JSBSim since reads it,
including current FlightGear's. Swapping the element back to satisfy an old FlightGear trades a model
that will not load for one that loads and cannot fly, which is worse: nothing about it looks broken
until the throttle is opened. See issue #24, and `JsbsimWriter.Motor`, which carries the whole of
this beside the code.

The **no-load current comes from the idle row and from nowhere else**. Taking it as the lowest
current of the loaded curve — which on a single-row curve is the operating current — counts the whole
draw as loss and costs most of the torque: measured, 7,122 rpm against the 9,500 the model states. A
model that states no idle row is refused rather than defaulted, because it is a value the model can
express.

`SimpleTrust` is a CRRCsim thrust model, and with the CRRCsim export gone it has no destination at
all, so the editor no longer offers to create one: the `+ Trust` button is removed. The node and
its serialization stay, so files saved with one still load and save it unchanged
(`LegacyRoundTripCheck` pins that with a model it builds itself — a compatibility guarantee cannot rest
on a sample nobody tidies up), and a model that has one is told plainly that it cannot be exported.
**Do not offer a control that builds something no export can consume** — that is the same failure as an
invented default, moved into the UI.

And whatever the tree can delete, the tree can **duplicate** — with everything under it. `Duplicate` is the
mirror image of `Delete` and generic for the same reason: it finds whichever `@AvlEditorNode` list of the parent
holds the node, by reflection, and inserts a deep copy right after it, so it works for nodes nobody thought
about. The copy itself is a serialization round trip (`DeepCopy`), which copies classes it has never heard of;
one that cannot be copied is reported rather than half-copied.

Three things a blind copy would get wrong, and `DuplicateCheck` pins all of them:

- **Names AVL reads are made unique**, and names that mean *the same thing* are not. Two surfaces called `wing`
  is a file nobody can read, and two bodies sharing a `BFILE` overwrite each other's profile — so those get
  their own (without spaces: AVL opens the profile by file name). A **control keeps its name**, because the name
  is what makes two sections part of the same control; renaming it would quietly split one aileron into two.
- **The transient links are restored** afterwards, the same `initParents()` a load makes. A duplicated wing
  whose sections do not know their surface stops mirroring its masses, in silence.
- **The copy lands on top of the original** and the log says so. Offsetting it would mean inventing a position,
  and the sensible offset differs for a wing, a battery and a fan.

It is undoable, and for free: `handleAddWithUndo` already notices whatever appears in a tracked list and pushes
an `AddCommand`, which is how every `+` button and the delete are undoable too.

And whatever the tree can add, the tree can **delete**. A node is deletable when its class asks for
`ENABLE_BUTTONS.DELETE`, and `Delete` then removes it from whichever `@AvlEditorNode` list of its parent
holds it — found by reflection, so the rule needs no maintenance. It used to be a chain of instanceof
cases naming each parent and child type, and everything the chain did not name fell through to a message
on stderr while the tree looked as if the delete had simply not worked: a battery, a shaft, an engine, a
propeller, a fuel tank, a data row and a Simple Trust were all addable and none of them removable. A
component's own `Pos` or `Gearing` is not a list item and stays undeletable — without one it is broken,
not lighter. `DeleteKeyCheck` pins both halves: which classes offer it, and that deleting really removes
from every list the tree shows.
