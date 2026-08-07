AVL Editor
==========

![AVL Editor Screenshot](2026-01-04_18-20.png)

An editor for AVL (Athena Vortex Lattice) and an exporter for CRRCsim, an open source model airplane simulator http://sourceforge.net/projects/crrcsim/

Download
--------

| Platform | Download |
|----------|----------|
| Windows | [avl-editor-windows.exe](https://github.com/TLmaK0/avl-crrcsim-editor/releases/latest/download/avl-editor-windows.exe) |
| Linux | [avl-editor-linux.deb](https://github.com/TLmaK0/avl-crrcsim-editor/releases/latest/download/avl-editor-linux.deb) |
| macOS | [avl-editor-macos.dmg](https://github.com/TLmaK0/avl-crrcsim-editor/releases/latest/download/avl-editor-macos.dmg) |

Or browse [all releases](https://github.com/TLmaK0/avl-crrcsim-editor/releases).

Create your airplane, and then export as AVL file or CRRCsim XML.

This software is in a early beta fase, so be careful with the result.

Please, help me to improve it.


Flying qualities
----------------

After running AVL, the editor judges the aircraft's modes — short period, phugoid, dutch roll — against
**MIL-F-8785C**, *Flying Qualities of Piloted Airplanes* (5 November 1980). The specification is in this
repository, so every threshold the editor applies can be traced to the page it came from:

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

    git clone https://github.com/TLmaK0/avl-crrcsim-editor.git

The first time you run sbt it will download and install a whole bunch of dependencies, which can take a long time on a slow connection. The following command will list the available tasks after bootstrapping the environment:

    cd avl-crrcsim-editor
    sbt tasks

Run with 

	sbt run
