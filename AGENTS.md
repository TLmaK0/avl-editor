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
- Derived quantities must be derived: `crrcsim.calculate()` populates mass and inertias from the
  mass objects, so validate *after* it or every model reports zero mass.
- When a physical constant is unavoidable, take it from a reference that demonstrably works and
  record the derivation in a comment. Gear stiffness, for instance, comes from FlightGear's stock
  c172p: total spring ≈ 32 × weight per metre (~3 cm static compression), damping = spring / 3.
- Pin the rule with a `*Check.scala` and assert the property, not the number, so it survives
  rescaling (see `GearStiffnessCheck`, which checks compression at 2.8 kg and 25 kg).

Propulsion is **required**, not optional: without an engine the model cannot take off and
FlightGear reports `Throttle 0 does not exist! 0 engines exist`. The battery voltage, propeller
diameter, blade count and the motor's data curve are all validated, and `buildPropulsion` no
longer substitutes anything for them.

Validation runs in **two stages**, because some inputs are AVL's outputs rather than the editor's
fields: `SimulationRequirements.validate(model)` before AVL runs, and `validateCalculation(calc)`
after it (the span efficiency comes from AVL's totals and used to be replaced by 0.85).

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
(`LegacyRoundTripCheck` pins that), and a model that has one is told plainly that it cannot be
exported. **Do not offer a control that builds something no export can consume** — that is the same
failure as an invented default, moved into the UI.
