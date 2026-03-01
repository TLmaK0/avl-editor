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

import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Display
import com.abajar.avleditor.avl.geometry.{Surface, Section, Control, Body, BodyProfilePoint}
import com.abajar.avleditor.avl.mass.Mass
import com.abajar.avleditor.avl.{AVL, AVLGeometry}
import com.abajar.avleditor.crrcsim.{CRRCSim, Config, Graphics}

object TreeIcons {
  private var icons: Map[String, Image] = Map.empty
  private var initialized = false

  private val iconPath = "/com/abajar/avleditor/icons/"

  def init(display: Display): Unit = {
    if (initialized) return
    initialized = true

    val names = List(
      "surface", "section", "control", "body", "profile_point",
      "mass", "avl", "geometry", "config", "airplane",
      "graphics", "wheel", "changelog", "default"
    )

    icons = names.flatMap { name =>
      val stream = getClass.getResourceAsStream(iconPath + name + ".png")
      if (stream != null) {
        val image = new Image(display, stream)
        stream.close()
        Some(name -> image)
      } else {
        System.err.println("TreeIcons: icon not found: " + name + ".png")
        None
      }
    }.toMap
  }

  def iconFor(data: Any): Image = {
    val key = data match {
      case _: Surface          => "surface"
      case _: Section          => "section"
      case _: Control          => "control"
      case _: Body             => "body"
      case _: BodyProfilePoint => "profile_point"
      case _: Mass             => "mass"
      case _: AVL              => "avl"
      case _: AVLGeometry      => "geometry"
      case _: Config           => "config"
      case _: Graphics         => "graphics"
      case _: CRRCSim          => "airplane"
      case _                   => "default"
    }
    icons.getOrElse(key, icons("default"))
  }

  def dispose(): Unit = {
    icons.values.foreach(_.dispose())
    icons = Map.empty
    initialized = false
  }
}
