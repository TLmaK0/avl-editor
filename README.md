AVL Editor
==========

![AVL Editor Screenshot](2026-01-04_18-20.png)

An editor for **radio-controlled and small-UAV aircraft**. You draw the aeroplane — geometry, masses,
materials and propulsion — and it does the aerodynamics for you: it analyses the design with
[AVL](https://web.mit.edu/drela/Public/web/avl/) (Athena Vortex Lattice) and
[XFOIL](https://web.mit.edu/drela/Public/web/xfoil/), tells you how it will fly, and exports a
**JSBSim** flight dynamics model you can actually fly in a simulator.

It is for someone designing a model or a small drone who wants to know, before building it, whether it
will fly and how it will behave — not for a wind-tunnel department. Everything is in metric units, and
the model states its own units, so the same aeroplane written in metres or in inches exports identically.

**Nothing is invented.** If the model is missing something an export needs, the editor stops and says
what is missing rather than filling in a plausible default. That rule is the reason to trust the output,
and it is written up under *No silent fallbacks* in [`AGENTS.md`](AGENTS.md) with the three FlightGear
bugs that produced it.

Download
--------

The current release is **1.0.0**:

| Platform | Download |
|----------|----------|
| Windows | [avl-editor-windows.exe](https://github.com/TLmaK0/avl-editor/releases/download/1.0.0/avl-editor-windows.exe) |
| Linux | [avl-editor-linux.deb](https://github.com/TLmaK0/avl-editor/releases/download/1.0.0/avl-editor-linux.deb) |
| macOS | [avl-editor-macos.dmg](https://github.com/TLmaK0/avl-editor/releases/download/1.0.0/avl-editor-macos.dmg) |

Or browse [all releases](https://github.com/TLmaK0/avl-editor/releases). These link to 1.0.0 by name
rather than to `releases/latest`, because GitHub's "latest" currently resolves to the **XFOIL binaries**
release the CI publishes — so the `latest/download/...` links this table used to carry returned 404 for
every platform. Reported in [#35](https://github.com/TLmaK0/avl-editor/issues/35).

**AVL, XFOIL and JSBSim do not have to be installed.** Whatever needs one fetches it into
`~/.avleditor` on first use. Nothing is installed on the machine itself and nothing needs `sudo`.

This software is in early beta. It will tell you when it cannot answer; it can still be wrong when it
does answer, so treat the numbers as a good estimate and not as a measurement of a real aeroplane.


What it exports
---------------

Three things, all from **File** in the menu bar:

| Export | What it is for | State |
|--------|----------------|-------|
| **AVL** file | The geometry as AVL reads it, for anyone who wants to run AVL themselves. | Works. |
| **JSBSim** flight model | The flight dynamics model: aerodynamics, mass, inertias, propulsion, undercarriage. Runs in JSBSim standalone. | Works, and three checks fly it for real — see below. |
| **FlightGear** package | The JSBSim model plus what FlightGear needs to load it. **File > Fly in FlightGear** then launches FlightGear on it. | Works. No instrument panel and no liveries yet ([#20](https://github.com/TLmaK0/avl-editor/issues/20)). |

**What the aerodynamics in the exported model are.** Not one number plus a slope, which is what a
simple export gives: AVL measures thirteen attitudes from −10° to +20° in one session, and the file
carries the whole `CL(α)`, `CD(α)` and `Cm(α)` curves. A table also holds its last row rather than
carrying on rising, so the aeroplane stops gaining lift at absurd attitudes instead of inventing more.
See *Curves, not one tangent* in [`AGENTS.md`](AGENTS.md).

**Electric aircraft, and one cost worth knowing before someone reports it as a bug.** An electric motor
is exported as a JSBSim `brushless_dc_motor`, stated by its own constants. **FlightGear 2020.3 and
earlier will not load it** and will say "Unknown engine type"; every JSBSim since January 2022 reads it,
current FlightGear included. That is a deliberate trade: the element it replaced was rated in watts,
which means unbounded torque at zero rpm, and 116 of 121 measured samples returned `nan` within 0.15 s
of the throttle opening. See [#24](https://github.com/TLmaK0/avl-editor/issues/24), which is closed.


What it does **not** do
-----------------------

This list exists so that nobody has to guess, and every line has an issue behind it.

| Not done | What that means in practice | Where |
|----------|------------------------------|-------|
| **Nothing is modelled past the stall** | The exported aeroplane cannot really stall, drop a wing or depart. Past the last measured attitude the lift tables just hold. Where the wing *stops* lifting **is** measured; what it does afterwards is not modelled. | [#17](https://github.com/TLmaK0/avl-editor/issues/17) |
| **PX4 SITL has never been validated** | It is a plausible target — PX4 SITL uses JSBSim — but nobody has flown an exported model through it, so it is not claimed. | [#18](https://github.com/TLmaK0/avl-editor/issues/18) |
| **No check flies an exported model in FlightGear** | Three checks fly it in JSBSim from the command line. FlightGear is only checked as far as finding its executable, so "it loads in FlightGear" rests on hand testing. | — |
| **Some propulsion figures are assumed, not derived** | The propeller's thrust and power curves are a generic APC 9x4.5, its inertia is scaled from a DJI 9450, the motor's coil resistance and efficiency are assumed, and a ducted fan's losses are a stated figure of merit of 0.5. Each is documented where it is defined. | [#19](https://github.com/TLmaK0/avl-editor/issues/19) |
| **A thin section's laminar bubble can be mistaken for its stall** | On a small tailplane at low Reynolds number XFOIL shows a peak at 8.5° that is a bursting bubble rather than a stall, and it is currently read as the section giving up. | [#43](https://github.com/TLmaK0/avl-editor/issues/43) |
| **One MIL-F-8785C requirement is not applied** | The extra short-period frequency floors of the specification's Figures 1 and 3. Everything else it judges is listed in `docs/mil-f-8785c.md`. | [#16](https://github.com/TLmaK0/avl-editor/issues/16) |
| **The CRRCsim export is gone** | It was removed. A model containing a *Simple Trust* node still loads and saves unchanged, but no export can consume one and the editor says so plainly. | — |

`docs/mil-f-8785c.md` has its own section on what the specification asks for that the editor does not
compute, and what does not apply to a radio-controlled model at all.


Flying qualities
----------------

After running AVL, the editor judges the aircraft against **MIL-F-8785C**, *Flying Qualities of Piloted
Airplanes* (5 November 1980) — the short period and its quickness, the phugoid, the dutch roll, the roll
mode, the spiral, the coupled roll-spiral, roll response, and the static rows — and reports a **Level**
for each, in all three Flight Phase Categories at once, rather than a pass. A Level says where the
aeroplane is; a pass would only say yes or no. Anything that **runs away** is reported above the table
whatever else was found, with the time its motion doubles in and which axis is diverging.

The specification is in this repository, so every threshold the editor applies can be traced to the page
it came from:

- [`docs/MIL-F-8785C.pdf`](docs/MIL-F-8785C.pdf) — the specification itself, a US Department of Defense
  document in the public domain, from [EverySpec](https://everyspec.com/MIL-SPECS/MIL-SPECS-MIL-F/MIL-F-8785C_5295/).
- [`docs/mil-f-8785c.md`](docs/mil-f-8785c.md) — the criteria the editor uses, each with its section,
  table and page; what the specification requires that the editor does not yet compute; and what does
  not apply to a radio-controlled model at all.

That last document also states the assumption the specification cannot state for itself: MIL-F-8785C is
written for **piloted, full-scale** airplanes. The damping criteria carry over to a model unchanged, the
frequency and time ones do not — they scale with the square root of the model's scale.


Where each thing is written down
--------------------------------

Every figure the editor applies that it did not measure itself cites the page it came from. So a
question about *why* the editor says something can be answered without opening the code:

| Question | Where the answer is |
|----------|---------------------|
| Why does it apply this threshold, and where does the number come from? | [`docs/mil-f-8785c.md`](docs/mil-f-8785c.md) — section, table and PDF page for each. |
| Where do the physical constants come from? | [`docs/references/`](docs/references/) — the sources themselves, committed, with a [README](docs/references/README.md) saying what is taken from each page. |
| How does it decide where the wing stalls? | NACA Report 572's critical-section method, [`docs/references/naca-tr-572.pdf`](docs/references/naca-tr-572.pdf), plus XFOIL per section. Described under *Where the wing gives up* in [`AGENTS.md`](AGENTS.md). |
| Why does the export refuse my model? | *No silent fallbacks* in [`AGENTS.md`](AGENTS.md), and `jsbsim/SimulationRequirements`, which holds one rule per observed failure. |
| What is assumed rather than derived, and why? | *What remains are stated assumptions* in [`AGENTS.md`](AGENTS.md). Retiring two of them is [#19](https://github.com/TLmaK0/avl-editor/issues/19). |
| What is being worked on, and what was already ruled out? | The **issues**. One issue per subject, and a change of plan is a comment on its issue — see below. |

**Plans and progress live in GitHub issues, not in files.** Each issue holds what is wanted, why, what
has been measured and what has been discarded, written as the work happens. The largest one open is
[#17](https://github.com/TLmaK0/avl-editor/issues/17) — modelling the aeroplane past the stall — and it
is worth knowing how to read it, because it is where the current questions come from:

| In issue #17 | Link |
|--------------|------|
| The literature search, which contradicted the method first proposed | [comment](https://github.com/TLmaK0/avl-editor/issues/17#issuecomment-5315892642) |
| The approved plan: iterated decambering (Mukherjee & Gopalarathnam, *J. Aircraft* 43(3) 2006), with every equation traced to its page | [comment](https://github.com/TLmaK0/avl-editor/issues/17#issuecomment-5316162758) |
| **The open question**: the published method does not leave the pre-stall curve alone. Two options, with what each costs | [comment](https://github.com/TLmaK0/avl-editor/issues/17#issuecomment-5316519222) |
| The 2014 follow-up on iteration schemes, and where a wing section has two valid answers at once | [comment](https://github.com/TLmaK0/avl-editor/issues/17#issuecomment-5319839637) |

Nothing new is built without it being asked for first, one thing at a time — the rule and the case that
produced it are under *Feature Planning* in [`AGENTS.md`](AGENTS.md).


How the checks are run, and why `sbt test` is not how
-----------------------------------------------------

**`sbt test` runs nothing in this build and prints `[success]` while doing it.** There is no test
framework on the classpath, so sbt has nothing to find, and a green `sbt test` means only that the tests
compiled. Anybody concluding from it that the checks pass has been told the opposite of the truth.

A check here is an **executable object** — there are 63 of them — so that it can drive AVL, XFOIL or
JSBSim as a real subprocess and read what came back:

    sbt "Test/runMain com.abajar.avleditor.avl.mass.MassMirrorCheck"

Each prints `PASS`/`FAIL` per assertion and exits non-zero if any failed. `sbt "show Test/discoveredMainClasses"`
lists them all, and each file's header states its own command.

Three of them fly an exported aeroplane in **JSBSim** from the command line and compare what JSBSim
computes against the tables in the file it loaded: `JsbsimCurveCheck`, `PropellerFlightCheck` and
`DuctedFanFlightCheck`. **All three excuse themselves and exit 0 when JSBSim is not installed**, printing
`..._SKIPPED` — so a run that reports success having skipped them has measured nothing about the export.
Read the output, not only the exit code.

CI runs on every push ([`tests.yml`](.github/workflows/tests.yml)), including building XFOIL from source
([`xfoil-binaries.yml`](.github/workflows/xfoil-binaries.yml)).


Building from sources on Debian/Ubuntu
--------------------------------------

Building AVL Editor requires scala with sbt

To install these in debian or ubuntu, download and install the .deb from scala-sbt.org:

    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo tee /etc/apt/trusted.gpg.d/sbt.asc
    sudo apt-get update
    sudo apt-get install sbt

Of course you will need the AVL Editor sources too:

    git clone https://github.com/TLmaK0/avl-editor.git

The first time you run sbt it will download and install a whole bunch of dependencies, which can take a long time on a slow connection. The following command will list the available tasks after bootstrapping the environment:

    cd avl-editor
    sbt tasks

Run with 

	sbt run
