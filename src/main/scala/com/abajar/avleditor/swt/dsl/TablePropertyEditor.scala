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

import java.lang.reflect.Field
import java.lang.reflect.Method

// A TableField adapts a model member (a reflected field or getter) to the
// property-table view. `editable` lets the view decide interactivity without
// knowing the concrete subtype; the view still matches concrete types when it
// needs a specific editor widget (file dialog, combo, checkbox) or undo command.
trait TableField {
  def text(): String
  def help(): String
  def value: String
  def value_=(value: String)
  def editable: Boolean = true
}

/**
 * Which kind of row one `@AvlEditorField` becomes.
 *
 * It lives here, and not inside the window that draws the table, so that it can be exercised without a
 * display. It had been inside the window, and the consequence was not theoretical: `AVL.flightPhase` is a
 * `String` carrying a constant list of choices, it was handed to [[TableFieldOptions]] — which reads its
 * field as an **index** with `Field.getInt` — and that throws on a String. The throw came out of the SWT
 * event loop while the table was painting a cell, so **selecting the AVL node killed the editor**, and
 * nothing caught it because no check can open a window. `PropertyRowsCheck` now builds every row of every
 * editable class and reads it, which is exactly what painting does.
 */
object TableFieldFactory {

  /**
   * @param dynamic the choices a field's `optionsFrom` method returned, when it has one.
   */
  def forAnnotatedField(data: Any, field: Field, text: String, help: String,
                        options: Array[String], dynamic: Option[Array[String]]): TableField = {
    if (dynamic.nonEmpty) {
      // A list the user can edit cannot be an index into anything: the same index means a different
      // thing after an edit, so the field keeps the chosen name.
      new TableFieldNamedOptions(data, field, text, help, dynamic.get)
    } else if (options.nonEmpty) {
      // What a constant list of choices means depends on what the field holds, and the two are not
      // interchangeable: an `int` is an **index** into the list (`Control.type`), a `String` is the
      // **chosen name itself** (`AVL.flightPhase`). The field's own type decides, so a choice field
      // added later cannot get it wrong by being declared the other way.
      if (field.getType == classOf[String]) new TableFieldNamedOptions(data, field, text, help, options)
      else new TableFieldOptions(data, field, text, help, options)
    } else {
      new TableFieldWritable(data, field, text, help)
    }
  }
}

/** Placeholder row for a virtual table that is asked to render data it has no backing
  * object for — e.g. a repaint that lands while the selection is being rebuilt. Keeps the
  * SWT SetData callback from throwing, which would tear down the event loop. */
object TableFieldEmpty extends TableField {
  def text(): String = ""
  def help(): String = ""
  def value: String = ""
  def value_=(value: String): Unit = ()
  override def editable: Boolean = false
}

class TableFieldWritable(val instance: Any, val field: Field, val textArg: String, helpArg: String) extends TableField{
  def text(): String = textArg
  def help(): String = helpArg

  def isBoolean: Boolean = field.getType == classOf[Boolean] || field.getType == java.lang.Boolean.TYPE

  def booleanValue: Boolean = {
    field.setAccessible(true)
    field.getBoolean(instance)
  }

  def booleanValue_=(value: Boolean): Unit = {
    field.setAccessible(true)
    field.setBoolean(instance, value)
  }

  def value: String = {
    field.setAccessible(true)
    Option(field.get(instance)) match {
      case Some(result) => result.toString
      case None => ""
    }
  }

  def value_=(value: String): Unit = {
    val parsedValue = field.get(instance).asInstanceOf[Any] match {
      case float: Float => value.toFloat
      case int: Int => value.toInt
      case bool: Boolean => value.toBoolean
      case _ => value
    }
    field.setAccessible(true)
    field.set(instance, parsedValue)
  }
}

class TableFieldReadOnly(protected val instance: Any, protected val method: Method, val textArg: String, helpArg: String) extends TableField{
  def text(): String = textArg
  def help(): String = helpArg

  def value: String = {
    method.setAccessible(true)
    Option(method.invoke(instance)) match {
      case Some(result) => result.toString
      case None => ""
    }
  }

  override def editable: Boolean = false

  final def value_=(value: String): Unit =
    throw new UnsupportedOperationException(s"Read-only field cannot be modified: ${textArg}")
}

class TableFieldFile(
    val instance: Any,
    val field: Field,
    val textArg: String,
    helpArg: String,
    val extensions: Array[String],
    val extensionDescription: String
) extends TableField {
  def text(): String = textArg
  def help(): String = helpArg

  def value: String = {
    field.setAccessible(true)
    Option(field.get(instance)) match {
      case Some(result) => result.toString
      case None => ""
    }
  }

  def value_=(value: String): Unit = {
    field.setAccessible(true)
    field.set(instance, value)
  }

  def isFileField: Boolean = true
}

class TableFieldOptions(
    val instance: Any,
    val field: Field,
    val textArg: String,
    helpArg: String,
    val options: Array[String]
) extends TableField {
  def text(): String = textArg
  def help(): String = helpArg

  // Find setter method for this field (e.g., setType for field "type")
  val setterMethod: Option[Method] = {
    val setterName = "set" + field.getName.capitalize
    try {
      Some(instance.getClass.getMethod(setterName, classOf[Int]))
    } catch {
      case _: NoSuchMethodException => None
    }
  }

  def selectedIndex: Int = {
    field.setAccessible(true)
    field.getInt(instance)
  }

  def selectedIndex_=(index: Int): Unit = {
    // Use setter if available (allows side effects like updating related fields)
    setterMethod match {
      case Some(setter) =>
        setter.invoke(instance, Integer.valueOf(index))
      case None =>
        field.setAccessible(true)
        field.setInt(instance, index)
    }
  }

  def value: String = {
    val idx = selectedIndex
    if (idx >= 0 && idx < options.length) options(idx) else ""
  }

  def value_=(value: String): Unit = {
    val idx = options.indexOf(value)
    if (idx >= 0) selectedIndex = idx
  }
}

/**
 * A dropdown whose choices come from the object itself, at the moment the cell is opened, and whose
 * value is the chosen **name**. `TableFieldOptions` stores an index into a constant array, which cannot
 * work for a list the user edits: the same index would mean a different thing after an edit.
 */
class TableFieldNamedOptions(
    val instance: Any,
    val field: Field,
    val textArg: String,
    helpArg: String,
    val options: Array[String]
) extends TableField {
  def text(): String = textArg
  def help(): String = helpArg

  /** Set through the setter when there is one: choosing a material writes its figures onto the
    * element, and that only happens if the setter runs. */
  val setterMethod: Option[Method] =
    try {
      Some(instance.getClass.getMethod("set" + field.getName.capitalize, classOf[String]))
    } catch {
      case _: NoSuchMethodException => None
    }

  def value: String = {
    field.setAccessible(true)
    Option(field.get(instance)).map(_.toString).getOrElse("")
  }

  def value_=(value: String): Unit = {
    setterMethod match {
      case Some(setter) => setter.invoke(instance, value)
      case None =>
        field.setAccessible(true)
        field.set(instance, value)
    }
  }
}
