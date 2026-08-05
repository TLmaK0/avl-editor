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

import com.abajar.avleditor.material.{Material, MaterialLibrary, Materials}
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.{TableEditor, CCombo}
import org.eclipse.swt.events.{SelectionAdapter, SelectionEvent}
import org.eclipse.swt.layout.{GridData, GridLayout}
import org.eclipse.swt.widgets._
import scala.collection.JavaConverters._

/**
 * The materials the editor offers: what each one weighs, and room to add more.
 *
 * Editing this list does not change any model that was saved: a model keeps the figures it was given,
 * so it weighs the same on a machine whose list differs. What is edited here is what the dropdowns
 * offer from now on.
 */
class MaterialsDialog(parent: Shell) {

  private val ColumnWidths = Seq(220, 120, 140, 320)

  def open(): Unit = {
    val library = Materials.library()
    val shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE)
    shell.setText("Materials")
    shell.setLayout(new GridLayout(1, false))
    shell.setSize(880, 560)

    val explain = new Label(shell, SWT.WRAP)
    explain.setText(
      "A material is either weighed by volume — its density, for what a part is filled with — or by " +
      "the area it covers, for a skin whose thickness is the point. Editing the list leaves saved " +
      "models as they are: a model keeps the figures it was given.")
    val explainData = new GridData(SWT.FILL, SWT.CENTER, true, false)
    explainData.widthHint = 840
    explain.setLayoutData(explainData)

    val table = new Table(shell, SWT.FULL_SELECTION | SWT.BORDER | SWT.MULTI)
    table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
    table.setHeaderVisible(true)
    table.setLinesVisible(true)
    Seq("Material", "Density (g/cm3)", "Skin weight (g/m2)", "Notes").zip(ColumnWidths).foreach {
      case (title, width) =>
        val column = new TableColumn(table, SWT.NONE)
        column.setText(title)
        column.setWidth(width)
    }

    def show(materials: Seq[Material]): Unit = {
      table.removeAll()
      materials.foreach { material =>
        val item = new TableItem(table, SWT.NONE)
        item.setText(Array(material.getName, f"${material.getDensity}%.3f",
          f"${material.getArealWeight}%.0f", Option(material.getNotes).getOrElse("")))
        item.setData(material)
      }
    }
    show(library.getMaterials.asScala)

    // Editing a cell writes straight to the material: this list is the library, not a copy of it.
    val editor = new TableEditor(table)
    editor.horizontalAlignment = SWT.LEFT
    editor.grabHorizontal = true
    table.addListener(SWT.MouseDown, new Listener {
      override def handleEvent(event: Event): Unit = {
        Option(editor.getEditor).foreach(old => if (!old.isDisposed) old.dispose())
        val point = new org.eclipse.swt.graphics.Point(event.x, event.y)
        val item = table.getItem(point)
        if (item == null) return
        val column = (0 until table.getColumnCount).find(c => item.getBounds(c).contains(point))
        column.foreach { columnIndex =>
          val material = item.getData.asInstanceOf[Material]
          val text = new Text(table, SWT.NONE)
          text.setText(item.getText(columnIndex))
          text.selectAll()
          val commit = new Listener {
            override def handleEvent(e: Event): Unit = {
              if (!text.isDisposed) {
                val typed = text.getText.trim
                columnIndex match {
                  case 0 => if (typed.nonEmpty) material.setName(typed)
                  case 1 => material.setDensity(asFloat(typed, material.getDensity))
                  case 2 => material.setArealWeight(asFloat(typed, material.getArealWeight))
                  case _ => material.setNotes(typed)
                }
                item.setText(Array(material.getName, f"${material.getDensity}%.3f",
                  f"${material.getArealWeight}%.0f", Option(material.getNotes).getOrElse("")))
                text.dispose()
              }
            }
          }
          text.addListener(SWT.FocusOut, commit)
          text.addListener(SWT.DefaultSelection, commit)
          editor.setEditor(text, item, columnIndex)
          text.setFocus()
        }
      }
    })

    val buttons = new Composite(shell, SWT.NONE)
    buttons.setLayout(new GridLayout(5, false))
    buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))

    def button(text: String, tooltip: String)(action: => Unit): Unit = {
      val b = new Button(buttons, SWT.PUSH)
      b.setText(text)
      b.setToolTipText(tooltip)
      b.addSelectionListener(new SelectionAdapter {
        override def widgetSelected(e: SelectionEvent): Unit = action
      })
    }

    button("Add", "A new material, to be filled in") {
      val material = new Material()
      library.getMaterials.add(material)
      show(library.getMaterials.asScala)
      table.setSelection(table.getItemCount - 1)
    }
    button("Duplicate", "Copy the selected material, to vary it") {
      table.getSelection.headOption.foreach { item =>
        val source = item.getData.asInstanceOf[Material]
        library.getMaterials.add(new Material(source.getName + " (copy)", source.getDensity,
          source.getArealWeight, source.getNotes))
        show(library.getMaterials.asScala)
        table.setSelection(table.getItemCount - 1)
      }
    }
    button("Delete", "Remove the selected materials") {
      table.getSelection.foreach(item => library.getMaterials.remove(item.getData))
      show(library.getMaterials.asScala)
    }
    button("Restore defaults", "Back to the materials the editor ships with") {
      val confirm = new MessageBox(shell, SWT.ICON_WARNING | SWT.YES | SWT.NO)
      confirm.setText("Restore defaults")
      confirm.setMessage("Replace the list with the materials the editor ships with? " +
        "Models already saved keep their own figures.")
      if (confirm.open() == SWT.YES) {
        library.getMaterials.clear()
        library.getMaterials.addAll(MaterialLibrary.defaults().getMaterials)
        show(library.getMaterials.asScala)
      }
    }
    button("Close", "Save the list and close") {
      Option(editor.getEditor).foreach(old => if (!old.isDisposed) old.dispose())
      Materials.save()
      shell.close()
    }

    shell.open()
    val display = parent.getDisplay
    while (!shell.isDisposed) {
      if (!display.readAndDispatch()) display.sleep()
    }
  }

  /** A figure that cannot be read leaves the old one: a typo must not silently zero a material. */
  private def asFloat(text: String, fallback: Float): Float =
    try text.replace(',', '.').toFloat catch { case _: NumberFormatException => fallback }
}
