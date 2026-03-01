package com.abajar.avleditor.undo

import java.lang.reflect.Field
import java.util.{ArrayList => JArrayList}
import scala.collection.mutable.ArrayBuffer

trait UndoCommand {
  def undo(): Unit
  def redo(): Unit
  def description: String
}

class PropertyChangeCommand(
    instance: Any,
    field: Field,
    oldValue: Any,
    newValue: Any
) extends UndoCommand {

  def description: String = s"Change ${field.getName}"

  def undo(): Unit = {
    field.setAccessible(true)
    field.set(instance, oldValue)
  }

  def redo(): Unit = {
    field.setAccessible(true)
    field.set(instance, newValue)
  }
}

class OptionsChangeCommand(
    instance: Any,
    field: Field,
    setterMethod: Option[java.lang.reflect.Method],
    oldIndex: Int,
    newIndex: Int
) extends UndoCommand {

  def description: String = s"Change ${field.getName}"

  private def setIndex(index: Int): Unit = {
    setterMethod match {
      case Some(setter) =>
        setter.invoke(instance, Integer.valueOf(index))
      case None =>
        field.setAccessible(true)
        field.setInt(instance, index)
    }
  }

  def undo(): Unit = setIndex(oldIndex)
  def redo(): Unit = setIndex(newIndex)
}

class AddCommand[T](
    list: JArrayList[T],
    item: T,
    index: Int
) extends UndoCommand {

  def description: String = s"Add ${item.getClass.getSimpleName}"

  def undo(): Unit = {
    list.remove(index)
  }

  def redo(): Unit = {
    list.add(index, item)
  }
}

class RemoveCommand[T](
    list: JArrayList[T],
    item: T,
    index: Int
) extends UndoCommand {

  def description: String = s"Delete ${item.getClass.getSimpleName}"

  def undo(): Unit = {
    list.add(index, item)
  }

  def redo(): Unit = {
    list.remove(index)
  }
}

class CompoundCommand(
    commands: Seq[UndoCommand],
    val description: String
) extends UndoCommand {

  def undo(): Unit = {
    commands.reverse.foreach(_.undo())
  }

  def redo(): Unit = {
    commands.foreach(_.redo())
  }
}

/** Snapshots multiple ArrayLists and restores them on undo/redo. */
class ListSnapshotCommand(
    snapshots: Seq[(JArrayList[Any], JArrayList[Any], JArrayList[Any])],
    val description: String
) extends UndoCommand {

  def undo(): Unit = snapshots.foreach { case (list, old, _) =>
    list.clear()
    list.addAll(old)
  }

  def redo(): Unit = snapshots.foreach { case (list, _, neu) =>
    list.clear()
    list.addAll(neu)
  }
}

object ListSnapshotCommand {
  /** Take a snapshot of multiple lists. Returns (list, copy) pairs. */
  def snapshot(lists: Seq[JArrayList[Any]]): Seq[(JArrayList[Any], JArrayList[Any])] = {
    lists.map(list => (list, new JArrayList[Any](list)))
  }
}
