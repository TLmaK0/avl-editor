/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.swt.dsl

import org.eclipse.swt.custom._
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.eclipse.swt.widgets._
import org.eclipse.swt._
import org.eclipse.swt.events._
import org.eclipse.swt.graphics.{Color, Font}
import java.io.File
import org.eclipse.swt.widgets.{Button, Composite}
import com.abajar.avleditor.undo.{UndoManager, PropertyChangeCommand, OptionsChangeCommand}

object Widget{
  implicit class SetLayoutDataWrapper[T <: Control](val subject:T) {
    def layoutData(data: GridData): T = {
      subject.setLayoutData(data)
      subject
    }
  }

  implicit class SetAddListenerWrapper(tree: Tree){
    def setSourceHandler(listenerMethod: (Option[Any], Integer) => (String, Any, Integer)): Tree = {
      tree.addListener(SWT.SetData, new Listener {
        def handleEvent(event: Event) = {
          val item = event.item.asInstanceOf[TreeItem]
          val parentItem = Option(item.getParentItem).map(_.getData)
          val (title, data, itemsCount) = listenerMethod(parentItem, event.index)
          item.setData(data)
          item.setText(title)
          item.setItemCount(itemsCount)
          val isRoot = parentItem.isEmpty
          val wasExpanded = Option(tree.getData("expandCallback"))
            .map(_.asInstanceOf[Any => Boolean])
            .exists(_(data))
          item.setExpanded(isRoot || wasExpanded)
          item.setImage(com.abajar.avleditor.swt.TreeIcons.iconFor(data))
          val pendingSel = Option(tree.getData("pendingSelection"))
          if (pendingSel.exists(s => s.asInstanceOf[AnyRef] eq data.asInstanceOf[AnyRef])) {
            tree.setSelection(item)
            tree.setData("pendingSelection", null)
          }
        }
      })
      tree
    }
  }

  implicit class SetAddListenerTableWrapper(table: Table){
    def setSourceHandler(listenerMethod: (Integer) => TableField): Table = {
      val display = table.getDisplay
      val evenRowColor = new Color(display, 245, 245, 250)
      val oddRowColor = display.getSystemColor(SWT.COLOR_WHITE)
      val readOnlyColor = new Color(display, 235, 235, 235)
      val readOnlyFg = new Color(display, 100, 100, 100)
      val monoFont = new Font(display, "Monospace", 9, SWT.NORMAL)

      table.addDisposeListener(new DisposeListener {
        def widgetDisposed(e: DisposeEvent): Unit = {
          evenRowColor.dispose()
          readOnlyColor.dispose()
          readOnlyFg.dispose()
          monoFont.dispose()
        }
      })

      table.addListener(SWT.SetData, new Listener {
        def handleEvent(event: Event) = {
          val item = event.item.asInstanceOf[TableItem]
          val index = table.indexOf(item)
          if (index >= 0) {
            val tableField = listenerMethod(index)
            item.setText(0, tableField.text)
            val displayValue = tableField match {
              case boolField: TableFieldWritable if boolField.isBoolean =>
                if (boolField.booleanValue) "☑" else "☐"
              case optionsField: TableFieldOptions =>
                optionsField.value
              case _ => tableField.value
            }
            item.setText(1, displayValue)
            item.setData(tableField)
            item.setFont(1, monoFont)

            val black = display.getSystemColor(SWT.COLOR_BLACK)
            tableField match {
              case _: TableFieldReadOnly =>
                item.setBackground(readOnlyColor)
                item.setForeground(0, black)
                item.setForeground(1, readOnlyFg)
              case _ =>
                item.setBackground(if (index % 2 == 0) oddRowColor else evenRowColor)
                item.setForeground(black)
            }

            table.getColumn(0).pack
            table.getColumn(1).pack
          }
        }
      })
      table
    }
  }

  // Companion object to store shared state for property change callbacks
  private var propertyChangeCallback: Option[() => Unit] = None
  private var _undoManager: Option[UndoManager] = None

  def setPropertyChangeCallback(callback: () => Unit): Unit = {
    propertyChangeCallback = Some(callback)
  }

  def setUndoManager(manager: UndoManager): Unit = {
    _undoManager = Some(manager)
  }

  def undoManager: Option[UndoManager] = _undoManager

  implicit class AddColumnTableWrapper(table: Table){
    val minimumWidth = 50

    val editor = new TableEditor(table)
    editor.horizontalAlignment = SWT.LEFT
    editor.grabHorizontal = true
    editor.minimumWidth = minimumWidth

    def addColumn(title: String, editable: Boolean = false): Table = {
      val column = new TableColumn(table, SWT.NONE)
      column.setText(title)
      column.pack

      val columnNumber = table.getColumnCount - 1

      if (editable) table.addSelectionListener(new SelectionAdapter{
        override def widgetSelected(e: SelectionEvent) = {
          Option(editor.getEditor).map(_.dispose)

          val item = e.item.asInstanceOf[TableItem]
          val tableField = item.getData.asInstanceOf[TableField]
          println(s"DEBUG: tableField class = ${tableField.getClass.getName}")

          tableField match {
            case fileField: TableFieldFile =>
              val dialog = new FileDialog(table.getShell, SWT.OPEN)
              dialog.setFilterExtensions(fileField.extensions.map(ext => "*." + ext))
              dialog.setFilterNames(Array(fileField.extensionDescription))
              Option(dialog.open).foreach { path =>
                val oldValue = fileField.field.get(fileField.instance)
                item.setText(columnNumber, path)
                fileField.value = path
                _undoManager.foreach(_.push(
                  new PropertyChangeCommand(fileField.instance, fileField.field, oldValue, path)))
                propertyChangeCallback.foreach(callback => callback())
              }

            case boolField: TableFieldWritable if boolField.isBoolean =>
              val oldValue = boolField.booleanValue
              val newValue = !oldValue
              boolField.booleanValue = newValue
              item.setText(columnNumber, if (newValue) "☑" else "☐")
              boolField.field.setAccessible(true)
              _undoManager.foreach(_.push(
                new PropertyChangeCommand(boolField.instance, boolField.field,
                  java.lang.Boolean.valueOf(oldValue), java.lang.Boolean.valueOf(newValue))))
              propertyChangeCallback.foreach(callback => callback())

            case optionsField: TableFieldOptions =>
              val combo = new CCombo(table, SWT.READ_ONLY | SWT.FLAT)
              optionsField.options.foreach(combo.add)
              val oldIndex = optionsField.selectedIndex
              combo.select(oldIndex)
              combo.addSelectionListener(new SelectionAdapter {
                override def widgetSelected(e: SelectionEvent): Unit = {
                  val selectedIdx = combo.getSelectionIndex
                  optionsField.selectedIndex = selectedIdx
                  item.setText(columnNumber, optionsField.value)
                  combo.dispose()
                  _undoManager.foreach(_.push(
                    new OptionsChangeCommand(optionsField.instance, optionsField.field,
                      optionsField.setterMethod, oldIndex, selectedIdx)))
                  propertyChangeCallback.foreach(callback => callback())
                }
              })
              combo.addListener(SWT.FocusOut, new Listener {
                override def handleEvent(e: Event): Unit = {
                  if (!combo.isDisposed) combo.dispose()
                }
              })
              editor.setEditor(combo, item, columnNumber)
              combo.setFocus()
              // Open dropdown immediately
              combo.setListVisible(true)

            case _: TableFieldReadOnly =>
              // Read-only fields are inspectable but not editable.
              ()

            case writableField: TableFieldWritable =>
              val oldRawValue = {
                writableField.field.setAccessible(true)
                writableField.field.get(writableField.instance)
              }
              val newEditor = new Text(table, SWT.NONE)
              newEditor.setText(item.getText(columnNumber))
              newEditor.addModifyListener(new ModifyListener{
                override def modifyText(me: ModifyEvent) = {
                  val text = editor.getEditor.asInstanceOf[Text]
                  editor.getItem.setText(columnNumber, text.getText)
                }
              })
              val confirmValue = new Listener {
                override def handleEvent(e: Event) = {
                  val editorControl = editor.getEditor
                  if (editorControl != null && !editorControl.isDisposed) {
                    val text = editorControl.asInstanceOf[Text]
                    tableField.value = text.getText
                    val newRawValue = {
                      writableField.field.setAccessible(true)
                      writableField.field.get(writableField.instance)
                    }
                    _undoManager.foreach(_.push(
                      new PropertyChangeCommand(writableField.instance, writableField.field,
                        oldRawValue, newRawValue)))
                    editorControl.dispose()
                    propertyChangeCallback.foreach(callback => callback())
                  }
                }
              }
              newEditor.addListener(SWT.FocusOut, confirmValue)
              newEditor.addListener(SWT.Traverse, new Listener {
                override def handleEvent(e: Event) = {
                  if (e.detail == SWT.TRAVERSE_RETURN) {
                    confirmValue.handleEvent(e)
                  }
                }
              })
              newEditor.selectAll
              newEditor.setFocus

              editor.setEditor(newEditor, item, columnNumber)
          }
        }
      })
      table
    }
  }

  implicit class AddButtonCoolBar(toolBar: ToolBar){
    def addButtonAndReturn(text: String, callback: (SelectionEvent) => Unit): ToolItem = {
      val item = new ToolItem(toolBar, SWT.PUSH)
      item.setText(text)
      item.addSelectionListener(new SelectionAdapter{
        override def widgetSelected(se: SelectionEvent){
          callback(se)
        }
      })
      item
    }
  }

  implicit class AddMenuWrapper(menu: Menu){
    def addSubmenu(text: String): Menu = {
      val menuItem = new MenuItem(menu, SWT.CASCADE)
      menuItem.setText(text)
      val submenu = new Menu(menu.getShell, SWT.DROP_DOWN)
      menuItem.setMenu(submenu)
      submenu
    }

  }

  implicit class AddMenuItemWrapper(menu: Menu){
    def addItem(text: String, callback: (SelectionEvent) => Unit): Menu = {
      val item = new MenuItem(menu, SWT.PUSH)
      item.setText(text)
      item.addSelectionListener(new SelectionAdapter{
        override def widgetSelected(se: SelectionEvent){
          callback(se)
        }
      })
      menu
    }
  }

  implicit class FileDialogWrapper(fileDialog: FileDialog){
    def show: Option[File] = Option(fileDialog.open).map(new File(_))

    def setExtensions(extensions: Array[String]): FileDialog = {
      fileDialog.setFilterExtensions(extensions)
      fileDialog
    }

    def setNameExtensions(names: Array[String]): FileDialog = {
      fileDialog.setFilterNames(names)
      fileDialog
    }
  }
}
