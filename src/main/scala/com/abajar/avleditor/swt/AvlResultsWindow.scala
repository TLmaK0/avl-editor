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
import org.eclipse.swt.graphics.{Font, Color}
import org.eclipse.swt.events.{SelectionAdapter, SelectionEvent}
import com.abajar.avleditor.avl.runcase.AvlCalculation
import com.abajar.avleditor.avl.runcase.MilF8785cEvaluator
import com.abajar.avleditor.avl.runcase.ModalNormRow
import scala.collection.JavaConverters._

class AvlResultsWindow(display: Display) {
  private var shell: Shell = _
  private val boldFont = new Font(display, "Sans", 10, SWT.BOLD)
  private val monoFont = new Font(display, "Monospace", 9, SWT.NORMAL)
  private val headerFont = new Font(display, "Sans", 9, SWT.BOLD)
  // Backgrounds, and the text colours that go on them. Always in pairs: a background on its own leaves the
  // text to the theme, and a dark theme's text on these is invisible.
  private val passBgColor = new Color(display, 220, 245, 220)
  private val failBgColor = new Color(display, 250, 220, 220)
  private val naBgColor = new Color(display, 240, 240, 230)
  private val passFgColor = new Color(display, 0, 90, 0)
  private val failFgColor = new Color(display, 150, 0, 0)
  private val naFgColor = new Color(display, 60, 60, 60)

  def open(calculation: AvlCalculation): Unit = {
    if (shell != null && !shell.isDisposed) {
      shell.dispose()
    }

    shell = new Shell(display, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX)
    shell.setText("AVL Results")
    shell.setSize(1000, 820)

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

    // What runs away, first and always. This used to be shown only when no mode at all was found, so an
    // aircraft with one passing mode and three divergences reported a green PASS and said nothing about them.
    MilF8785cEvaluator.runawaySummary(calculation).foreach { summary =>
      val headline = new Label(modalGroup, SWT.WRAP)
      headline.setText(summary)
      headline.setFont(headerFont)
      headline.setBackground(failBgColor)
      headline.setForeground(failFgColor)
      val grid = new GridData(SWT.FILL, SWT.CENTER, true, false)
      grid.horizontalSpan = 5
      grid.widthHint = 900
      headline.setLayoutData(grid)
    }
    MilF8785cEvaluator.divergences(calculation).foreach { divergence =>
      val line = new Label(modalGroup, SWT.WRAP)
      line.setText("• " + divergence.says)
      val grid = new GridData(SWT.FILL, SWT.CENTER, true, false)
      grid.horizontalSpan = 5
      grid.widthHint = 900
      grid.horizontalIndent = 12
      line.setLayoutData(grid)
    }

    val modes = MilF8785cEvaluator.oscillatoryPositiveModes(calculation)
    if (modes.isEmpty) {
      // What AVL actually answered, not a guess at what the user forgot.
      MilF8785cEvaluator.whyNoModes(calculation).foreach { line =>
        val label = new Label(modalGroup, SWT.WRAP)
        label.setText(line)
        val grid = new GridData(SWT.FILL, SWT.CENTER, true, false)
        grid.horizontalSpan = 5
        grid.widthHint = 640
        label.setLayoutData(grid)
      }
    } else {
      addModalHeader(modalGroup, "Motion")
      addModalHeader(modalGroup, "What it is")
      addModalHeader(modalGroup, "How often")
      addModalHeader(modalGroup, "Damping")
      addModalHeader(modalGroup, "Verdict")

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
      sigma.setFont(monoFont)

      val omega = new Label(eigGroup, SWT.NONE)
      omega.setText(f"${eig.getOmega}%.6f")
      omega.setFont(monoFont)

      val wn = new Label(eigGroup, SWT.NONE)
      wn.setText(f"${eig.getNaturalFrequency}%.6f")
      wn.setFont(monoFont)

      val zeta = new Label(eigGroup, SWT.NONE)
      zeta.setText(f"${eig.getDampingRatio}%.6f")
      zeta.setFont(monoFont)
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
    valueLabel.setFont(monoFont)
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
      label.setFont(headerFont)
      label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false))
    }
  }

  private def addControlRow(parent: Composite, name: String, values: Array[Float]): Unit = {
    val nameLabel = new Label(parent, SWT.NONE)
    nameLabel.setText(name)

    for (i <- 0 until numControls) {
      val valueLabel = new Label(parent, SWT.NONE)
      valueLabel.setText(f"${values(i)}%.6f")
      valueLabel.setFont(monoFont)
      valueLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false))
    }
  }

  private def addModalHeader(parent: Composite, text: String): Unit = {
    val header = new Label(parent, SWT.NONE)
    header.setText(text)
    header.setFont(headerFont)
    header.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))
  }

  /**
   * One motion per row, in the order a reader wants it: what it is, how fast it swings, how quickly it dies
   * down, and whether that is good enough — with the numbers the standard is written in kept alongside.
   *
   * It used to be `wn [rad/s]`, `zeta` and `MIL-F-8785C L1 Phase B: 0.30 <= zeta <= 2.00` repeated on every
   * row, which names the standard three times, states the rule in symbols and leaves the reader to compare it
   * with the number themselves.
   */
  private def addModalNormRow(parent: Composite, row: ModalNormRow): Unit = {
    val bgColor = row.pass match {
      case Some(true)  => passBgColor
      case Some(false) => failBgColor
      case None        => naBgColor
    }

    // No background on the data cells. Setting one without setting a foreground is how this table came out
    // white on white: the colours below are light, and a dark theme's default text is light too. The rule is
    // that whoever sets a background sets the text colour with it — so these set neither and inherit both.
    def cell(text: String, width: Int, wrap: Boolean = false, font: Font = null): Label = {
      val label = new Label(parent, if (wrap) SWT.WRAP else SWT.NONE)
      label.setText(text)
      if (font != null) label.setFont(font)
      val grid = new GridData(SWT.FILL, SWT.CENTER, wrap, false)
      if (width > 0) grid.widthHint = width
      label.setLayoutData(grid)
      label
    }

    cell(row.modeName, 110, font = headerFont)
    cell(row.whatItIs, 260, wrap = true)
    // The period is what a stopwatch would measure, and it means something; radians per second does not.
    cell(row.period.map(p => f"every $p%.2f s").getOrElse("—"), 110, font = monoFont)
    val damping = (row.zeta, row.swingsToHalf) match {
      case (Some(z), Some(swings)) => f"$z%.2f  (half in ${swings}%.1f swings)"
      case (Some(z), None) if z >= 1.0 => f"$z%.2f  (no swing at all)"
      case (Some(z), None) => f"$z%.2f"
      case _ => "—"
    }
    cell(damping, 190, font = monoFont)

    // The verdict is the one cell that carries a colour, and it carries both of them: a light background and
    // a dark text on it, chosen together so the pair is readable whatever the desktop theme is.
    val verdict = new Label(parent, SWT.WRAP)
    verdict.setText(row.pass match {
      case Some(true) => "PASS — " + row.verdict
      case Some(false) => "FAIL — " + row.verdict
      case None => row.verdict
    })
    verdict.setBackground(bgColor)
    verdict.setForeground(row.pass match {
      case Some(true) => passFgColor
      case Some(false) => failFgColor
      case None => naFgColor
    })
    verdict.setFont(headerFont)
    val verdictGrid = new GridData(SWT.FILL, SWT.CENTER, true, false)
    verdictGrid.widthHint = 380
    verdict.setLayoutData(verdictGrid)

    // The figures the standard is written in, and the rule it asks for, on their own line under the row: worth
    // having to quote, not worth reading first.
    val footnote = new Label(parent, SWT.WRAP)
    footnote.setText(row.wn.map(w => f"wn ${w}%.3f rad/s · ").getOrElse("") +
      "MIL-F-8785C Level 1 Phase B wants " + row.requirement)
    // Neither: the theme's own text colour, whatever it is. Grey on a dark theme is as unreadable as white
    // on white was, and this line is already set apart by sitting under the row and indented.
    val footGrid = new GridData(SWT.FILL, SWT.CENTER, true, false)
    footGrid.horizontalSpan = 5
    footGrid.horizontalIndent = 12
    footnote.setLayoutData(footGrid)
  }

  def isOpen: Boolean = {
    shell != null && !shell.isDisposed && shell.isVisible
  }
}
