/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor

import java.util.logging.{Handler, Level, LogRecord, Formatter}
import org.eclipse.swt.widgets.{Display, Label}
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.SWT
import scala.collection.mutable.ListBuffer
import java.text.SimpleDateFormat
import java.util.Date

class LogFooterHandler(display: Display, footerLabel: Label) extends Handler {

  val logHistory = new ListBuffer[String]()
  private val dateFormat = new SimpleDateFormat("HH:mm:ss")
  private val warningColor = new Color(display, 180, 100, 0)
  private val errorColor = new Color(display, 200, 40, 40)
  private val infoColor = display.getSystemColor(SWT.COLOR_DARK_GREEN)

  setFormatter(new Formatter {
    override def format(record: LogRecord): String = {
      val level = record.getLevel.getName
      val message = record.getMessage
      val loggerName = record.getLoggerName.split("\\.").lastOption.getOrElse("")
      val timestamp = dateFormat.format(new Date(record.getMillis))
      s"[$timestamp] [$level] $loggerName: $message"
    }
  })

  override def publish(record: LogRecord): Unit = {
    if (isLoggable(record)) {
      val formattedMessage = getFormatter.format(record)

      logHistory += formattedMessage
      if (logHistory.size > 1000) {
        logHistory.remove(0)
      }

      display.asyncExec(new Runnable {
        def run(): Unit = {
          if (!footerLabel.isDisposed) {
            val shortMessage = if (formattedMessage.length > 150) {
              formattedMessage.substring(0, 147) + "..."
            } else {
              formattedMessage
            }
            footerLabel.setText(shortMessage)
            val level = record.getLevel
            if (level.intValue >= Level.SEVERE.intValue) {
              footerLabel.setForeground(errorColor)
            } else if (level.intValue >= Level.WARNING.intValue) {
              footerLabel.setForeground(warningColor)
            } else {
              footerLabel.setForeground(infoColor)
            }
            footerLabel.getParent.layout()
          }
        }
      })
    }
  }

  override def flush(): Unit = {}

  override def close(): Unit = {
    warningColor.dispose()
    errorColor.dispose()
  }
}
