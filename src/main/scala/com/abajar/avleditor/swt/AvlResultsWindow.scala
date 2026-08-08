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
import com.abajar.avleditor.avl.runcase.{ModalNormComparison, ModalNormRow}
import com.abajar.avleditor.avl.runcase.FlightPhaseCategory
import com.abajar.avleditor.avl.runcase.RowOutcome
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

    showing = calculation
    shell = new Shell(display, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX)
    shell.setText("AVL Results")
    shell.setSize(1860, 900)

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
    modalGroup.setText("Flying qualities vs MIL-F-8785C")
    modalGroup.setLayout(new GridLayout(ModalColumns, false))
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
      grid.horizontalSpan = ModalColumns
      grid.widthHint = 1750
      headline.setLayoutData(grid)
    }
    def wideLine(text: String, indent: Int): Unit = {
      val line = new Label(modalGroup, SWT.WRAP)
      line.setText(text)
      val grid = new GridData(SWT.FILL, SWT.CENTER, true, false)
      grid.horizontalSpan = ModalColumns
      grid.widthHint = 1750
      grid.horizontalIndent = indent
      line.setLayoutData(grid)
    }

    // What runs away, and what a neutral mode means. Both are ours rather than the standard's: MIL-F-8785C
    // says how much damping a motion needs and has no opinion about fins or centres of gravity, so they are
    // said under their own heading instead of inside the table of its verdicts.
    val runaways = MilF8785cEvaluator.divergences(calculation)
    val neutrals = MilF8785cEvaluator.neutralModes(calculation)
    if (runaways.nonEmpty || neutrals.nonEmpty) {
      wideLine("What the motions do — read from AVL's eigenvalues, not from MIL-F-8785C:", 0)
      runaways.foreach(divergence => wideLine("• " + divergence.says, 12))
      neutrals.foreach(line => wideLine("• " + line, 12))
    }

    // Every Flight Phase at once, and whether the aircraft's size moved any of the thresholds. The Category
    // is the one thing here that cannot be derived from the model — it is the mission, not the machine — so
    // rather than ask for it and grade against one, all three are graded and the reader picks the column
    // that is their aeroplane's life.
    val size = MilF8785cEvaluator.sizeOf(calculation)
    wideLine("Judged in all three Flight Phase Categories at once: " +
      FlightPhaseCategory.All.map(c => f"${c.label}%s, ${c.describes}%s").mkString("; ") + "." +
      (if (size.scales)
        f" This aircraft spans ${size.spanMetres}%.2f m, below the ${size.ReferenceSpanMetres}%.2f m of the " +
          "smallest airplane the standard was written for, so its frequencies and times are scaled to that " +
          "size; both figures are given on each row."
      else if (size.known)
        f" This aircraft spans ${size.spanMetres}%.2f m, within the range the standard covers, so every " +
          "figure is exactly as it states it."
      else ""), 0)

    val modes = MilF8785cEvaluator.oscillatoryPositiveModes(calculation)
    if (modes.isEmpty) {
      // What AVL actually answered, not a guess at what the user forgot.
      MilF8785cEvaluator.whyNoModes(calculation).foreach(line => wideLine(line, 0))
    }

    // The table is drawn whenever AVL answered at all, not only when something oscillates: the roll mode and
    // the spiral are **real roots**, so an aircraft with nothing but real roots still has two of the six
    // motions to judge. It used to be all or nothing, and those two were thrown away with the rest.
    if (!calculation.getEigenvalues.isEmpty) {
      addModalHeader(modalGroup, "Motion")
      addModalHeader(modalGroup, "What it is")
      addModalHeader(modalGroup, "How often")
      addModalHeader(modalGroup, "Damping")
      FlightPhaseCategory.All.foreach(c => addModalHeader(modalGroup, s"${c.label} — ${c.describes}"))

      MilF8785cEvaluator.evaluateEveryCategory(calculation)
        .foreach(motion => addModalNormRow(modalGroup, motion))
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

  /** How wide each column of the modal table is. Named because the footnote has to span all of them. */
  private val ModalColumns = 4 + FlightPhaseCategory.All.length

  /**
   * One motion per row, in the order a reader wants it: what it is, how fast it swings, how quickly it dies
   * down, and then **one verdict per Flight Phase** — because what was measured is the aeroplane and what it
   * is compared against is the mission, and the aircraft has no opinion about which mission it will fly.
   *
   * It used to be `wn [rad/s]`, `zeta` and `MIL-F-8785C L1 Phase B: 0.30 <= zeta <= 2.00` repeated on every
   * row, which names the standard three times, states the rule in symbols and leaves the reader to compare it
   * with the number themselves.
   */
  private def addModalNormRow(parent: Composite, motion: ModalNormComparison): Unit = {
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

    cell(motion.modeName, 165, font = headerFont)
    cell(motion.whatItIs, 230, wrap = true)
    // The period is what a stopwatch would measure, and it means something; radians per second does not.
    cell(motion.period.map(p => f"every $p%.2f s").getOrElse("—"), 105, font = monoFont)
    val damping = (motion.zeta, motion.swingsToHalf) match {
      case (Some(z), Some(swings)) => f"$z%.2f  (half in ${swings}%.1f swings)"
      case (Some(z), None) if z >= 1.0 => f"$z%.2f  (no swing at all)"
      case (Some(z), None) => f"$z%.2f"
      case _ => "—"
    }
    cell(damping, 175, font = monoFont)

    // One verdict per Flight Phase — except when the three say exactly the same thing, which is most of
    // them, and then it is said once across all three columns. Three identical paragraphs side by side are
    // not three answers; they are one answer that makes the reader check whether it really is one, and
    // they bury the rows where the Categories genuinely differ, which are the interesting ones.
    val identical = motion.byCategory.map(r => (r._2.verdict, r._2.outcome, r._2.pass)).distinct.length == 1
    val shown = if (identical) motion.byCategory.take(1) else motion.byCategory
    shown.foreach { case (_, row) =>
      val verdict = new Label(parent, SWT.WRAP)
      // The Level, not a pass: the standard is written in three of them, and an aircraft that misses Level 1
      // is usually flyable rather than broken. The verdict says which one it reached; the colour still marks
      // Level 1 apart, because that is the one to aim at.
      // Matched on the sealed set, so a new kind of outcome is a compile error here rather than something
      // that quietly falls through to the plain text.
      verdict.setText(row.outcome match {
        case RowOutcome.Reached(n)           => f"LEVEL $n%d — " + row.verdict
        case RowOutcome.WorseThanLevelThree  => "WORSE THAN LEVEL 3 — " + row.verdict
        case RowOutcome.OnTheBoundary        => "ON THE BOUNDARY — " + row.verdict
        case RowOutcome.NotFound             => row.verdict
        case RowOutcome.NotJudged            => row.verdict
        case RowOutcome.DoesNotApply         => row.verdict
      })
      verdict.setBackground(row.pass match {
        case Some(true)  => passBgColor
        case Some(false) => failBgColor
        case None        => naBgColor
      })
      verdict.setForeground(row.pass match {
        case Some(true) => passFgColor
        case Some(false) => failFgColor
        case None => naFgColor
      })
      verdict.setFont(headerFont)
      val verdictGrid = new GridData(SWT.FILL, SWT.FILL, true, false)
      verdictGrid.widthHint = if (identical) 380 * FlightPhaseCategory.All.length else 380
      if (identical) verdictGrid.horizontalSpan = FlightPhaseCategory.All.length
      verdict.setLayoutData(verdictGrid)
    }

    // The figures the standard is written in, and the rule it asks for, on their own line under the row: worth
    // having to quote, not worth reading first. One rule per Category, because that is the half of the
    // comparison that changes with the mission — the measurement above it does not.
    val footnote = new Label(parent, SWT.WRAP)
    val rules = motion.byCategory.map { case (category, row) =>
      // What the requirement became at this aircraft's size, when its size moved it. Both are always shown:
      // a verdict that silently moved the goalposts would be worse than one that never moved them.
      f"${category.label}%s: " + row.requirement + row.applied.map(a => " (" + a + ")").getOrElse("")
    }
    // Said once when the three agree, which is most of them: repeating an identical rule three times is
    // noise, and the reader is meant to notice the ones that differ.
    val distinct = motion.byCategory.map(_._2.requirement).distinct
    footnote.setText(motion.wn.map(w => f"wn ${w}%.3f rad/s · ").getOrElse("") +
      "MIL-F-8785C Level 1 wants " +
      (if (distinct.length == 1) rules.head.dropWhile(_ != ' ').trim else rules.mkString(" · ")))
    // Neither background nor foreground: the theme's own text colour, whatever it is. Grey on a dark theme
    // is as unreadable as white on white was, and this line is already set apart by sitting under the row.
    val footGrid = new GridData(SWT.FILL, SWT.CENTER, true, false)
    footGrid.horizontalSpan = ModalColumns
    footGrid.horizontalIndent = 12
    footnote.setLayoutData(footGrid)
  }

  def isOpen: Boolean = {
    shell != null && !shell.isDisposed && shell.isVisible
  }

  /** The calculation currently on screen, so a late arrival cannot redraw a window about another run. */
  private var showing: AvlCalculation = _

  /**
   * Redraw, because something the report shows arrived after the report did.
   *
   * The stall is measured after the window opens — it is an external XFOIL process per aerofoil and the
   * slowest thing in the analysis path, and holding the whole report back for it looked like a hang. So the
   * flight-path stability row is drawn without it and filled in here.
   *
   * It does nothing if the window has been closed, or if the user has run AVL again in the meantime: a
   * result that arrives late belongs to the run it came from and to no other.
   */
  def refresh(calculation: AvlCalculation): Unit = {
    if (isOpen && (showing eq calculation)) open(calculation)
  }
}
