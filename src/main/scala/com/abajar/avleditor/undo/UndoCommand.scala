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

/**
 * Restores a chosen name through the object's own setter, so whatever that setter derives comes back
 * with it: choosing a material writes its density onto the element, and an undo that only put the name
 * back would leave the element named one thing and weighing another.
 */
class NamedChoiceChangeCommand(
    instance: AnyRef,
    setter: Option[java.lang.reflect.Method],
    field: Field,
    oldValue: String,
    newValue: String
) extends UndoCommand {

  def description: String = s"Change ${field.getName}"

  private def apply(value: String): Unit = setter match {
    case Some(method) => method.invoke(instance, value)
    case None =>
      field.setAccessible(true)
      field.set(instance, value)
  }

  def undo(): Unit = apply(oldValue)

  def redo(): Unit = apply(newValue)
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

/** Atomic change of several float fields of one object, as produced by a single 3D drag
  * gesture (press - move - release). The 3D viewer reports a drag once, on release, so the
  * whole gesture is one command and one undo step. */
class MultiFieldChangeCommand(
    instance: AnyRef,
    fields: Seq[Field],
    originalValues: Seq[Float],
    newValues: Seq[Float],
    val description: String
) extends UndoCommand {

  private def setValues(values: Seq[Float]): Unit = {
    fields.zip(values).foreach { case (field, value) =>
      field.setAccessible(true)
      field.setFloat(instance, value)
    }
  }

  def undo(): Unit = setValues(originalValues)

  def redo(): Unit = setValues(newValues)
}

object MultiFieldChangeCommand {

  /** Runs `mutate`, capturing `fieldNames` before and after it. Returns None when the
    * mutation left every field untouched (e.g. a click without movement), so no-op
    * gestures never reach the undo history. */
  def capture(
      instance: AnyRef,
      description: String,
      fieldNames: Seq[String]
  )(mutate: => Unit): Option[MultiFieldChangeCommand] = {
    val fields = fieldNames.map { name =>
      val field = instance.getClass.getDeclaredField(name)
      field.setAccessible(true)
      field
    }
    val originalValues = fields.map(_.getFloat(instance))
    mutate
    val newValues = fields.map(_.getFloat(instance))

    if (originalValues == newValues) None
    else Some(new MultiFieldChangeCommand(instance, fields, originalValues, newValues, description))
  }
}

object ListSnapshotCommand {
  /** Take a snapshot of multiple lists. Returns (list, copy) pairs. */
  def snapshot(lists: Seq[JArrayList[Any]]): Seq[(JArrayList[Any], JArrayList[Any])] = {
    lists.map(list => (list, new JArrayList[Any](list)))
  }
}
