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
