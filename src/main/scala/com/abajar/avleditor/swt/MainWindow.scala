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

import org.eclipse.swt.widgets.{Event, TreeItem, Listener, Display, TableItem}
import org.eclipse.swt.custom.StyledText
import org.eclipse.swt.layout._
import org.eclipse.swt._
import org.eclipse.swt.events._;
import org.eclipse.swt.widgets._

import dsl.Widget._
import dsl.Shell
import dsl.Shell._
import dsl.TableField
import com.abajar.avleditor.crrcsim.CRRCSim
import java.util.ArrayList
import org.eclipse.swt.widgets.Widget
import org.eclipse.swt.custom.SashForm
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS
import java.io.File;

object MenuOption extends Enumeration {
  type MenuOption = Value
  val Save, SaveAs, Open, ExportAsAvl, ExportAsCRRCSim, RunAvl, SetAvlExecutable, ClearAvlConfiguration = Value
}

import MenuOption._

class MainWindow(
      buttonClickHandler: (ENABLE_BUTTONS) => Unit,
      treeUpdateHandler: (Option[Any], Integer) => (String, Any, Integer),
      treeClickHandler: (Any) => Unit,
      menuClickHandler: (MenuOption) => Unit,
      tableUpdateHandler: (Integer) => TableField,
      tableClickHandler: (Any) => Unit
      ) {

  private val toolItems = collection.mutable.LinkedHashMap[ENABLE_BUTTONS, ToolItem]()

  val display = new Display

  var tree: Tree = _
  var properties: Table = _
  var help: StyledText = _
  var footerLabel: Label = _
  var viewer3D: Viewer3DGL = _

  private def notifyButtonClick(buttonType: ENABLE_BUTTONS) =
        (se: SelectionEvent) => buttonClickHandler(buttonType)

  private def notifyTreeClick(se: SelectionEvent) =
        treeClickHandler(se.item.getData)

  private def notifyTableClick(se: SelectionEvent) =
        tableClickHandler(se.item.getData)

  private def notifyMenuClick(menuOption: MenuOption) =
        (se: SelectionEvent) => menuClickHandler(menuOption)

  def disableAllButtons: Unit = {
    toolItems.values.foreach(_.setEnabled(false))
  }

  def buttonsEnableOnly(
        buttons: scala.collection.immutable.List[ENABLE_BUTTONS]): Unit = {
    toolItems.foreach { case (btnType, item) =>
      item.setEnabled(buttons.contains(btnType))
    }
  }

  private def addToolItem(toolbar: ToolBar, text: String, tooltip: String,
        buttonType: ENABLE_BUTTONS): Unit = {
    val item = new ToolItem(toolbar, SWT.PUSH)
    item.setText(text)
    item.setToolTipText(tooltip)
    item.setEnabled(false)
    item.addSelectionListener(new SelectionAdapter {
      override def widgetSelected(se: SelectionEvent) = buttonClickHandler(buttonType)
    })
    toolItems(buttonType) = item
  }

  def showOpenDialog(
        path: String, description: String, extension: String): Option[File] =
    showOpenDialog(path, Array(description), Array(extension))

  def showOpenDialog(
        path: String,
        descriptions: Array[String],
        extensions: Array[String]): Option[File] =
    shell.openFileDialog.setNameExtensions(descriptions).setExtensions(addWildcard(extensions)).show

  def showSaveDialog(
        path: String, description: String, extension: String): Option[File] =
    showSaveDialog(path, Array(description), Array(extension))

  def showSaveDialog(
        path: String,
        descriptions: Array[String],
        extensions: Array[String]): Option[File]
    = shell.saveFileDialog.setNameExtensions(descriptions).setExtensions(addWildcard(extensions)).show

  def refreshTree: Unit = {
    val expandedData = collectExpandedData(tree.getItems)
    val selectedData = treeNodeSelected
    pendingExpansions = expandedData ++ selectedData
    pendingSelection = selectedData
    tree.setData("expandCallback", (data: Any) => pendingExpansions.contains(data))
    tree.setData("pendingSelection", pendingSelection.orNull)
    tree.removeAll()
    tree.setItemCount(1)
    tree.clearAll(true)
  }

  private var pendingSelection: Option[Any] = None

  private var pendingExpansions: Set[Any] = Set.empty

  def shouldExpand(data: Any): Boolean = pendingExpansions.contains(data)

  private def collectExpandedData(items: Array[TreeItem]): Set[Any] = {
    var result = Set[Any]()
    for (item <- items) {
      if (item.getExpanded) {
        val data = item.getData
        if (data != null) result += data
        result ++= collectExpandedData(item.getItems)
      }
    }
    result
  }

  private def addWildcard(extensions: Array[String]) =
    extensions.map(
      extension =>  if (extension.contains("*")) { extension }
                    else { "*." + extension }
    )

  private val shell = Shell( display, { shell => {
    TreeIcons.init(display)

    val mainLayout = new GridLayout(1, false)
    mainLayout.marginWidth = 4
    mainLayout.marginHeight = 4
    mainLayout.verticalSpacing = 4
    shell.setLayout(mainLayout)

    shell.addMenu(menu => {
        menu.addSubmenu("File")
          .addItem("Save", notifyMenuClick(MenuOption.Save))
          .addItem("Save as...", notifyMenuClick(MenuOption.SaveAs))
          .addItem("Open...", notifyMenuClick(MenuOption.Open))
          .addItem("Export As Avl", notifyMenuClick(MenuOption.ExportAsAvl))
          .addItem(
                "Export As CRRCSim",
                notifyMenuClick(MenuOption.ExportAsCRRCSim)
          )
          .addItem("Run AVL", notifyMenuClick(MenuOption.RunAvl))

        menu.addSubmenu("Edit")
          .addItem("Set AVL executable", notifyMenuClick(MenuOption.SetAvlExecutable))
          .addItem("Clear AVL configuration", notifyMenuClick(MenuOption.ClearAvlConfiguration))
     })

    val toolbar = new ToolBar(shell, SWT.HORIZONTAL | SWT.WRAP)
    toolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))

    // Geometry actions
    addToolItem(toolbar, "+ Surface", "Add a new surface", ENABLE_BUTTONS.ADD_SURFACE)
    addToolItem(toolbar, "+ Body", "Add a new body", ENABLE_BUTTONS.ADD_BODY)
    addToolItem(toolbar, "+ Section", "Add a section to the selected surface", ENABLE_BUTTONS.ADD_SECTION)
    addToolItem(toolbar, "+ Control", "Add a control to the selected section", ENABLE_BUTTONS.ADD_CONTROL)
    addToolItem(toolbar, "+ Profile Pt", "Add a profile point to the selected body", ENABLE_BUTTONS.ADD_PROFILE_POINT)
    addToolItem(toolbar, "Import BFILE", "Import body shape from file", ENABLE_BUTTONS.IMPORT_BFILE)
    new ToolItem(toolbar, SWT.SEPARATOR)

    // Mass actions
    addToolItem(toolbar, "+ Mass", "Add a mass element", ENABLE_BUTTONS.ADD_MASS)
    addToolItem(toolbar, "Auto Masses", "Generate masses from geometry volumes", ENABLE_BUTTONS.AUTO_MASSES_FROM_VOLUME)
    addToolItem(toolbar, "Calc CG", "Calculate center of gravity from masses", ENABLE_BUTTONS.CALCULATE_CG)
    new ToolItem(toolbar, SWT.SEPARATOR)

    // Config actions
    addToolItem(toolbar, "+ Changelog", "Add a changelog entry", ENABLE_BUTTONS.ADD_CHANGELOG)
    addToolItem(toolbar, "+ Battery", "Add a battery", ENABLE_BUTTONS.ADD_BATTERY)
    addToolItem(toolbar, "+ Shaft", "Add a shaft", ENABLE_BUTTONS.ADD_SHAFT)
    addToolItem(toolbar, "+ Engine", "Add an engine", ENABLE_BUTTONS.ADD_ENGINE)
    addToolItem(toolbar, "+ Data", "Add engine data point", ENABLE_BUTTONS.ADD_DATA)
    addToolItem(toolbar, "+ Idle", "Add idle data point", ENABLE_BUTTONS.ADD_DATA_IDLE)
    addToolItem(toolbar, "+ Trust", "Add simple trust", ENABLE_BUTTONS.ADD_SYMPLE_TRUST)
    addToolItem(toolbar, "+ Collision", "Add a collision point", ENABLE_BUTTONS.ADD_COLLISION_POINT)
    new ToolItem(toolbar, SWT.SEPARATOR)

    // Delete
    addToolItem(toolbar, "Delete", "Delete the selected element", ENABLE_BUTTONS.DELETE)

    // Resizable 3-pane layout
    val sash = new SashForm(shell, SWT.HORIZONTAL)
    sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))

    // Pane 1: Tree
    tree = new Tree(sash, SWT.VIRTUAL | SWT.BORDER)
    tree.addSelectionListener(new SelectionAdapter {
      override def widgetSelected(se: SelectionEvent) = notifyTreeClick(se)
    })
    tree.layoutData(new GridData(GridData.FILL_BOTH))
      .setSourceHandler(treeUpdateHandler)
    tree.setItemCount(1)

    // Pane 2: Properties + Help stacked vertically
    val propsHelpComposite = new Composite(sash, SWT.NONE)
    val propsHelpLayout = new GridLayout(1, false)
    propsHelpLayout.marginWidth = 0
    propsHelpLayout.marginHeight = 0
    propsHelpComposite.setLayout(propsHelpLayout)

    properties = new Table(propsHelpComposite, SWT.VIRTUAL | SWT.FULL_SELECTION | SWT.BORDER)
    properties.setLinesVisible(true)
    properties.setHeaderVisible(true)
    properties.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
    properties.addColumn("Property")
      .addColumn("Value", true)
      .setSourceHandler(tableUpdateHandler)
    properties.addSelectionListener(new SelectionAdapter {
      override def widgetSelected(e: SelectionEvent) = notifyTableClick(e)
    })

    help = new StyledText(propsHelpComposite, SWT.READ_ONLY | SWT.BORDER | SWT.WRAP)
    val helpGridData = new GridData(SWT.FILL, SWT.CENTER, true, false)
    helpGridData.heightHint = 80
    help.setLayoutData(helpGridData)

    // Pane 3: 3D Viewer (OpenGL)
    viewer3D = new Viewer3DGL(sash, SWT.BORDER)

    // Set pane proportions: 20% tree, 25% properties, 55% 3D viewer
    sash.setWeights(Array(20, 25, 55): _*)

    footerLabel = new Label(shell, SWT.BORDER)
    footerLabel.setText("Ready")
    footerLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))
    footerLabel.setCursor(display.getSystemCursor(SWT.CURSOR_HAND))

    shell.pack()
    shell.setMaximized(true)
  }})

  def treeNodeSelected: Option[Any] = {
    Option(tree).flatMap { tree =>
      val items = tree.getSelection
      if (items.length > 0) { Option(items(0).getData) }
      else { None }
    }
  }

  def treeNodeSelectedParent: Option[Any] = {
    Option(tree).flatMap { tree =>
      val items = tree.getSelection
      if (items.length > 0 && Option(items(0).getParentItem).isDefined) {
        Option(items(0).getParentItem.getData)
      } else { None }
    }
  }

  def getShell: org.eclipse.swt.widgets.Shell = shell

  def show: Unit = shell.start
}
