AVL Editor
==========

![AVL Editor Screenshot](2026-01-04_18-20.png)

An editor for radio-controlled and small-UAV aircraft. It builds the geometry, the masses and the
propulsion, analyses them with [AVL](https://web.mit.edu/drela/Public/web/avl/) (Athena Vortex
Lattice) and [XFOIL](https://web.mit.edu/drela/Public/web/xfoil/), and exports a **JSBSim** flight
dynamics model in metric units — one that runs in JSBSim standalone and in
[FlightGear](https://www.flightgear.org/), both of which the editor's own checks fly it in.

Download
--------

| Platform | Download |
|----------|----------|
| Windows | [avl-editor-windows.exe](https://github.com/TLmaK0/avl-editor/releases/latest/download/avl-editor-windows.exe) |
| Linux | [avl-editor-linux.deb](https://github.com/TLmaK0/avl-editor/releases/latest/download/avl-editor-linux.deb) |
| macOS | [avl-editor-macos.dmg](https://github.com/TLmaK0/avl-editor/releases/latest/download/avl-editor-macos.dmg) |

Or browse [all releases](https://github.com/TLmaK0/avl-editor/releases).

Create your airplane, then export it as an **AVL** file, as a **JSBSim** flight model, or as a
complete **FlightGear** package you can fly from the editor with one click.

This software is in a early beta fase, so be careful with the result.

Please, help me to improve it.


Flying qualities
----------------

After running AVL, the editor judges the aircraft against **MIL-F-8785C**, *Flying Qualities of
Piloted Airplanes* (5 November 1980) — the short period and its quickness, the phugoid, the dutch
roll, the roll mode, the spiral, the coupled roll-spiral, roll response, and the static rows — and
reports a **Level** for each, in all three Flight Phase Categories at once, rather than a pass. The
specification is in this repository, so every threshold the editor applies can be traced to the page
it came from:

- [`docs/MIL-F-8785C.pdf`](docs/MIL-F-8785C.pdf) — the specification itself, a US Department of Defense
  document in the public domain, from [EverySpec](https://everyspec.com/MIL-SPECS/MIL-SPECS-MIL-F/MIL-F-8785C_5295/).
- [`docs/mil-f-8785c.md`](docs/mil-f-8785c.md) — the criteria the editor uses, each with its section,
  table and page; what the specification requires that the editor does not yet compute; and what does
  not apply to a radio-controlled model at all.

That last document also states the assumption the specification cannot state for itself: MIL-F-8785C is
written for **piloted, full-scale** airplanes. The damping criteria carry over to a model unchanged, the
frequency and time ones do not — they scale with the square root of the model's scale.


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
