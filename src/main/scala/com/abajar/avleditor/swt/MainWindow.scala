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

  implicit class AddButtonCompositeRegister(composite: Composite){
    def addButtonRegister(
          text: String,
          callback: (ENABLE_BUTTONS) => (SelectionEvent) => Unit,
          button: ENABLE_BUTTONS): Composite = {
      allButtons += ButtonData(text, button, callback(button), composite)
      buttonContainers += composite
      composite
    }
  }

  case class ButtonData(text: String, buttonType: ENABLE_BUTTONS, callback: (SelectionEvent) => Unit, container: Composite)

  val allButtons = collection.mutable.ListBuffer[ButtonData]()
  val buttonContainers = collection.mutable.Set[Composite]()
  val activeButtons = collection.mutable.ListBuffer[Button]()

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
    for (btn <- activeButtons) {
      if (!btn.isDisposed) btn.dispose()
    }
    activeButtons.clear()
  }

  def buttonsEnableOnly(
        buttons: scala.collection.immutable.List[ENABLE_BUTTONS]): Unit = {
    disableAllButtons
    for (buttonData <- allButtons) {
      if (buttons.contains(buttonData.buttonType)) {
        val btn = new Button(buttonData.container, SWT.PUSH)
        btn.setText(buttonData.text)
        btn.addSelectionListener(new SelectionAdapter {
          override def widgetSelected(se: SelectionEvent) = {
            buttonData.callback(se)
          }
        })
        activeButtons += btn
      }
    }
    // Force layout update after adding buttons
    for (container <- buttonContainers) {
      container.layout(true)
      container.getParent.layout(true)
    }
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
    tree.removeAll()
    tree.setItemCount(1)
    tree.clearAll(true)
  }

  private def addWildcard(extensions: Array[String]) =
    extensions.map(
      extension =>  if (extension.contains("*")) { extension }
                    else { "*." + extension }
    )

  private val shell = Shell( display, { shell => {
    val layout = new GridLayout
    layout.numColumns = 3
    shell setLayout layout

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

    val buttonBar1 = shell.addButtonBar()
    buttonBar1.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1))
    buttonBar1.addButtonRegister("+ Surface", notifyButtonClick, ENABLE_BUTTONS.ADD_SURFACE)
      .addButtonRegister("+ Body", notifyButtonClick, ENABLE_BUTTONS.ADD_BODY)
      .addButtonRegister("Auto Masses", notifyButtonClick, ENABLE_BUTTONS.AUTO_MASSES_FROM_VOLUME)
      .addButtonRegister("Calc CG", notifyButtonClick, ENABLE_BUTTONS.CALCULATE_CG)
      .addButtonRegister("+ Section", notifyButtonClick, ENABLE_BUTTONS.ADD_SECTION)
      .addButtonRegister("+ Control", notifyButtonClick, ENABLE_BUTTONS.ADD_CONTROL)
      .addButtonRegister("+ Profile Point", notifyButtonClick, ENABLE_BUTTONS.ADD_PROFILE_POINT)
      .addButtonRegister("Import BFILE", notifyButtonClick, ENABLE_BUTTONS.IMPORT_BFILE)
      .addButtonRegister("+ Mass", notifyButtonClick, ENABLE_BUTTONS.ADD_MASS)
      .addButtonRegister("Delete", notifyButtonClick, ENABLE_BUTTONS.DELETE)

    val buttonBar2 = shell.addButtonBar()
    buttonBar2.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 1))
    buttonBar2.addButtonRegister("+ Change Log", notifyButtonClick, ENABLE_BUTTONS.ADD_CHANGELOG)
      .addButtonRegister("+ Battery", notifyButtonClick, ENABLE_BUTTONS.ADD_BATTERY)
      .addButtonRegister("+ Shaft", notifyButtonClick, ENABLE_BUTTONS.ADD_SHAFT)
      .addButtonRegister("+ Engine", notifyButtonClick, ENABLE_BUTTONS.ADD_ENGINE)
      .addButtonRegister("+ Data", notifyButtonClick, ENABLE_BUTTONS.ADD_DATA)
      .addButtonRegister("+ Idle Data", notifyButtonClick, ENABLE_BUTTONS.ADD_DATA_IDLE)
      .addButtonRegister("+ Simple Trust", notifyButtonClick, ENABLE_BUTTONS.ADD_SYMPLE_TRUST)
      .addButtonRegister("+ Collision Point", notifyButtonClick, ENABLE_BUTTONS.ADD_COLLISION_POINT)



    // Column 1: Tree
    tree = shell.addTree(SWT.VIRTUAL | SWT.BORDER, notifyTreeClick)
      .layoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL))
      .setSourceHandler(treeUpdateHandler)

    tree.setItemCount(1)

    // Column 2: Properties + Help stacked vertically
    val propsHelpComposite = new Composite(shell, SWT.NONE)
    val propsHelpLayout = new GridLayout(1, false)
    propsHelpLayout.marginWidth = 0
    propsHelpLayout.marginHeight = 0
    propsHelpComposite.setLayout(propsHelpLayout)
    propsHelpComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL))

    properties = new Table(propsHelpComposite, SWT.VIRTUAL | SWT.FULL_SELECTION | SWT.BORDER)
    properties.setLinesVisible(true)
    properties.setHeaderVisible(true)
    val propsGridData = new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL)
    propsGridData.heightHint = 300
    properties.setLayoutData(propsGridData)
    properties.addColumn("Property")
      .addColumn("Value", true)
      .setSourceHandler(tableUpdateHandler)
    properties.addSelectionListener(new SelectionAdapter {
      override def widgetSelected(e: SelectionEvent) = notifyTableClick(e)
    })

    help = new StyledText(propsHelpComposite, SWT.READ_ONLY | SWT.BORDER | SWT.WRAP)
    val helpGridData = new GridData(GridData.FILL_HORIZONTAL)
    helpGridData.heightHint = 80
    help.setLayoutData(helpGridData)

    // Column 3: 3D Viewer (OpenGL)
    viewer3D = new Viewer3DGL(shell, SWT.BORDER)
    val viewerGridData = new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL)
    viewerGridData.widthHint = 400
    viewer3D.setLayoutData(viewerGridData)

    footerLabel = new Label(shell, SWT.BORDER)
    footerLabel.setText("Ready")
    footerLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1))
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
