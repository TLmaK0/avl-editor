package com.abajar.avleditor.undo

import scala.collection.mutable.ArrayBuffer

class UndoManager(val maxHistory: Int = 50) {
  private val undoStack = new ArrayBuffer[UndoCommand]()
  private val redoStack = new ArrayBuffer[UndoCommand]()

  private var listeners = List[() => Unit]()

  def addListener(listener: () => Unit): Unit = {
    listeners = listener :: listeners
  }

  private def notifyListeners(): Unit = {
    listeners.foreach(_())
  }

  def push(command: UndoCommand): Unit = {
    undoStack += command
    redoStack.clear()
    if (undoStack.size > maxHistory) {
      undoStack.remove(0)
    }
    notifyListeners()
  }

  def undo(): Unit = {
    if (canUndo) {
      val command = undoStack.remove(undoStack.size - 1)
      command.undo()
      redoStack += command
      notifyListeners()
    }
  }

  def redo(): Unit = {
    if (canRedo) {
      val command = redoStack.remove(redoStack.size - 1)
      command.redo()
      undoStack += command
      notifyListeners()
    }
  }

  def canUndo: Boolean = undoStack.nonEmpty

  def canRedo: Boolean = redoStack.nonEmpty

  def clear(): Unit = {
    undoStack.clear()
    redoStack.clear()
    notifyListeners()
  }

  def undoDescription: Option[String] =
    if (canUndo) Some(undoStack.last.description) else None

  def redoDescription: Option[String] =
    if (canRedo) Some(redoStack.last.description) else None
}
