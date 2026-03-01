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

//TODO: This needs some kind of abstraction away from model and view

trait TableField {
  def text(): String
  def help(): String
  def value: String
  def value_=(value: String)
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

  //TODO: Raise exception
  final def value_=(value: String): Unit = { }
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
