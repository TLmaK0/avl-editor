/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.swt

import org.eclipse.swt.SWT
import org.eclipse.swt.layout.{GridLayout, GridData}
import org.eclipse.swt.custom.ScrolledComposite
import org.eclipse.swt.widgets.{Display, Shell, Table, TableColumn, TableItem, Label, Composite, Button, Group}
import org.eclipse.swt.events.{SelectionAdapter, SelectionEvent}
import com.abajar.avleditor.avl.runcase.AvlCalculation
import com.abajar.avleditor.avl.runcase.MilF8785cEvaluator
import com.abajar.avleditor.avl.runcase.ModalNormRow
import scala.collection.JavaConverters._

class AvlResultsWindow(display: Display) {
  private var shell: Shell = _

  def open(calculation: AvlCalculation): Unit = {
    if (shell != null && !shell.isDisposed) {
      shell.dispose()
    }

    shell = new Shell(display, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX)
    shell.setText("AVL Results")
    shell.setSize(760, 820)

    shell.setLayout(new GridLayout(1, false))

    val scrolled = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL)
    scrolled.setLayoutData(new GridData(GridData.FILL_BOTH))
    scrolled.setExpandHorizontal(true)
    scrolled.setExpandVertical(true)

    val content = new Composite(scrolled, SWT.NONE)
    content.setLayout(new GridLayout(2, true))
    scrolled.setContent(content)

    val config = calculation.getConfiguration
    val stab = calculation.getStabilityDerivatives

    // Configuration group
    val configGroup = new Group(content, SWT.NONE)
    configGroup.setText("Configuration")
    configGroup.setLayout(new GridLayout(2, false))
    configGroup.setLayoutData(new GridData(GridData.FILL_BOTH))

    addRow(configGroup, "Sref", f"${config.getSref}%.4f")
    addRow(configGroup, "Cref", f"${config.getCref}%.4f")
    addRow(configGroup, "Bref", f"${config.getBref}%.4f")
    addRow(configGroup, "Velocity", f"${config.getVelocity}%.4f")
    addRow(configGroup, "Alpha", f"${config.getAlpha}%.4f")
    addRow(configGroup, "CLtot", f"${config.getCLtot}%.6f")
    addRow(configGroup, "CDvis", f"${config.getCDvis}%.6f")
    addRow(configGroup, "Cmtot", f"${config.getCmtot}%.6f")
    if (config.getE != null) {
      addRow(configGroup, "e (efficiency)", f"${config.getE}%.4f")
    }

    // Stability Derivatives group
    val stabGroup = new Group(content, SWT.NONE)
    stabGroup.setText("Stability Derivatives")
    stabGroup.setLayout(new GridLayout(2, false))
    stabGroup.setLayoutData(new GridData(GridData.FILL_BOTH))

    addRow(stabGroup, "CLa", f"${stab.getCLa}%.6f")
    addRow(stabGroup, "CLq", f"${stab.getCLq}%.6f")
    addRow(stabGroup, "Cma", f"${stab.getCma}%.6f")
    addRow(stabGroup, "Cmq", f"${stab.getCmq}%.6f")
    addRow(stabGroup, "CYb", f"${stab.getCYb}%.6f")
    addRow(stabGroup, "CYp", f"${stab.getCYp}%.6f")
    addRow(stabGroup, "CYr", f"${stab.getCYr}%.6f")
    addRow(stabGroup, "Clb", f"${stab.getClb}%.6f")
    addRow(stabGroup, "Clp", f"${stab.getClp}%.6f")
    addRow(stabGroup, "Clr", f"${stab.getClr}%.6f")
    addRow(stabGroup, "Cnb", f"${stab.getCnb}%.6f")
    addRow(stabGroup, "Cnp", f"${stab.getCnp}%.6f")
    addRow(stabGroup, "Cnr", f"${stab.getCnr}%.6f")

    // Modal analysis vs MIL-F-8785C
    val modalGroup = new Group(content, SWT.NONE)
    modalGroup.setText("Modal Analysis vs MIL-F-8785C (Level 1, Phase B)")
    modalGroup.setLayout(new GridLayout(5, false))
    val modalGridData = new GridData(GridData.FILL_HORIZONTAL)
    modalGridData.horizontalSpan = 2
    modalGroup.setLayoutData(modalGridData)

    val modes = MilF8785cEvaluator.oscillatoryPositiveModes(calculation)
    if (modes.isEmpty) {
      val noData = new Label(modalGroup, SWT.WRAP)
      noData.setText("No oscillatory eigenmodes available. Define mass/inertia and run AVL again.")
      val noDataGrid = new GridData(SWT.FILL, SWT.CENTER, true, false)
      noDataGrid.horizontalSpan = 5
      noData.setLayoutData(noDataGrid)
    } else {
      addModalHeader(modalGroup, "Mode")
      addModalHeader(modalGroup, "wn [rad/s]")
      addModalHeader(modalGroup, "zeta")
      addModalHeader(modalGroup, "Criterion")
      addModalHeader(modalGroup, "Result")

      val rows = MilF8785cEvaluator.evaluate(calculation)
      rows.foreach(row => addModalNormRow(modalGroup, row))
    }

    // Raw eigenvalues table from AVL .eig
    val eigGroup = new Group(content, SWT.NONE)
    eigGroup.setText("AVL Eigenvalues")
    eigGroup.setLayout(new GridLayout(4, false))
    val eigGridData = new GridData(GridData.FILL_HORIZONTAL)
    eigGridData.horizontalSpan = 2
    eigGroup.setLayoutData(eigGridData)

    addModalHeader(eigGroup, "sigma [1/s]")
    addModalHeader(eigGroup, "omega [rad/s]")
    addModalHeader(eigGroup, "wn [rad/s]")
    addModalHeader(eigGroup, "zeta")

    calculation.getEigenvalues.asScala.foreach { eig =>
      val sigma = new Label(eigGroup, SWT.NONE)
      sigma.setText(f"${eig.getSigma}%.6f")

      val omega = new Label(eigGroup, SWT.NONE)
      omega.setText(f"${eig.getOmega}%.6f")

      val wn = new Label(eigGroup, SWT.NONE)
      wn.setText(f"${eig.getNaturalFrequency}%.6f")

      val zeta = new Label(eigGroup, SWT.NONE)
      zeta.setText(f"${eig.getDampingRatio}%.6f")
    }

    // Control Derivatives group
    val controlGroup = new Group(content, SWT.NONE)
    controlGroup.setText("Control Derivatives")
    val numCtrls = calculation.getControlNames.length
    controlGroup.setLayout(new GridLayout(numCtrls + 1, false))  // +1 for row label
    val controlGridData = new GridData(GridData.FILL_HORIZONTAL)
    controlGridData.horizontalSpan = 2
    controlGroup.setLayoutData(controlGridData)

    addControlHeader(controlGroup, calculation.getControlNames)
    addControlRow(controlGroup, "CL", stab.getCLd)
    addControlRow(controlGroup, "CY", stab.getCYd)
    addControlRow(controlGroup, "Cl", stab.getCld)
    addControlRow(controlGroup, "Cm", stab.getCmd)
    addControlRow(controlGroup, "Cn", stab.getCnd)

    // Close button
    val closeButton = new Button(content, SWT.PUSH)
    closeButton.setText("Close")
    val buttonGridData = new GridData(SWT.RIGHT, SWT.CENTER, false, false)
    buttonGridData.horizontalSpan = 2
    closeButton.setLayoutData(buttonGridData)
    closeButton.addSelectionListener(new SelectionAdapter {
      override def widgetSelected(e: SelectionEvent): Unit = {
        shell.close()
      }
    })

    scrolled.setMinSize(content.computeSize(SWT.DEFAULT, SWT.DEFAULT))
    shell.open()
  }

  private def addRow(parent: Composite, name: String, value: String): Unit = {
    val nameLabel = new Label(parent, SWT.NONE)
    nameLabel.setText(name)
    nameLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false))

    val valueLabel = new Label(parent, SWT.NONE)
    valueLabel.setText(value)
    valueLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false))
  }

  private var numControls = 3

  private def addControlHeader(parent: Composite, controlNames: Array[String]): Unit = {
    numControls = controlNames.length
    val empty = new Label(parent, SWT.NONE)
    empty.setText("")

    for (i <- 0 until numControls) {
      val label = new Label(parent, SWT.NONE)
      label.setText(controlNames(i))
      label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false))
    }
  }

  private def addControlRow(parent: Composite, name: String, values: Array[Float]): Unit = {
    val nameLabel = new Label(parent, SWT.NONE)
    nameLabel.setText(name)

    for (i <- 0 until numControls) {
      val valueLabel = new Label(parent, SWT.NONE)
      valueLabel.setText(f"${values(i)}%.6f")
      valueLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false))
    }
  }

  private def addModalHeader(parent: Composite, text: String): Unit = {
    val header = new Label(parent, SWT.NONE)
    header.setText(text)
    header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))
  }

  private def addModalNormRow(parent: Composite, row: ModalNormRow): Unit = {
    val mode = new Label(parent, SWT.NONE)
    mode.setText(row.modeName)

    val wn = new Label(parent, SWT.NONE)
    wn.setText(row.wn.map(v => f"$v%.4f").getOrElse("N/A"))

    val zeta = new Label(parent, SWT.NONE)
    zeta.setText(row.zeta.map(v => f"$v%.4f").getOrElse("N/A"))

    val criterion = new Label(parent, SWT.WRAP)
    criterion.setText(row.criterion)
    criterion.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))

    val result = new Label(parent, SWT.NONE)
    row.pass match {
      case Some(true) =>
        result.setText("PASS")
        result.setForeground(display.getSystemColor(SWT.COLOR_DARK_GREEN))
      case Some(false) =>
        result.setText("FAIL")
        result.setForeground(display.getSystemColor(SWT.COLOR_DARK_RED))
      case None =>
        result.setText("N/A")
    }
  }

  def isOpen: Boolean = {
    shell != null && !shell.isDisposed && shell.isVisible
  }
}
