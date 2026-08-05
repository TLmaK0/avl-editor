/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.view

import com.abajar.avleditor.view.annotations.AvlEditorField
import java.lang.reflect.Field
import java.util.logging.{Level, Logger}
import scala.collection.JavaConverters._

/**
 * Which fields of an object the properties table shows, and where a dropdown's choices come from.
 *
 * Kept out of the SWT code so it can be checked without a display: what a surface offers is a rule
 * about annotations, not about widgets.
 */
object PropertyRows {

  private val logger = Logger.getLogger(PropertyRows.getClass.getName)

  /**
   * The annotated fields of a class and of everything it inherits from, the class's own first: a
   * surface's geometry reads before the material fields it gets from `MaterialElement`. Inherited
   * fields have to be included or a surface would not show what it is made of at all.
   */
  def annotatedFields(objClass: Class[_]): Seq[Field] = {
    def upwards(cls: Class[_]): Seq[Field] =
      if (cls == null) Seq.empty
      else cls.getDeclaredFields.toSeq ++ upwards(cls.getSuperclass)
    upwards(objClass).filter(_.isAnnotationPresent(classOf[AvlEditorField]))
  }

  /**
   * The choices a field's `optionsFrom` method offers, asked for now rather than fixed when the class
   * was written: the materials are a list the user edits and adds to.
   */
  def dynamicOptions(data: Any, methodName: String): Option[Array[String]] =
    try {
      data.getClass.getMethod(methodName).invoke(data) match {
        case names: Array[String] => Some(names)
        case names: java.util.List[_] => Some(names.asScala.map(String.valueOf).toArray)
        case _ => None
      }
    } catch {
      case e: Exception =>
        logger.log(Level.WARNING,
          s"No options from '$methodName' on ${data.getClass.getSimpleName}", e)
        None
    }

  /** The label each row shows, in the order the table lists them. */
  def rowLabels(objClass: Class[_]): Seq[String] =
    annotatedFields(objClass).map(_.getAnnotation(classOf[AvlEditorField]).text())
}
