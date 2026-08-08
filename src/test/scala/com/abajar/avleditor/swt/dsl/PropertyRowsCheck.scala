/*
 * Every row of every editable node, built and read — which is exactly what the property table does when
 * it paints a cell.
 *
 * This exists because the editor died the first time anybody selected the AVL node. `AVL.flightPhase` is
 * a `String` carrying a constant list of choices; it was handed to `TableFieldOptions`, which reads its
 * field as an **index** with `Field.getInt`, and that throws on a String. The throw came out of the SWT
 * event loop mid-paint, so the whole window went with it — and no check could see it, because building
 * the rows lived inside the window and nothing without a display could reach it.
 *
 * So the two halves: the choice of row is out in `TableFieldFactory` where it can be called, and this
 * reads every row of every class the tree can select. A field whose annotation and whose type disagree
 * fails here now instead of in front of the user.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.swt.dsl.PropertyRowsCheck"
 */
package com.abajar.avleditor.swt.dsl

import com.abajar.avleditor.TestAircraft
import com.abajar.avleditor.view.PropertyRows
import com.abajar.avleditor.view.annotations.AvlEditorField
import scala.collection.JavaConverters._

object PropertyRowsCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** The rows the editor would build for one object, through the same factory the window uses. */
  private def rowsOf(data: Any): Seq[TableField] =
    PropertyRows.annotatedFields(data.getClass).map { field =>
      val annotation = field.getAnnotation(classOf[AvlEditorField])
      val dynamic =
        if (annotation.optionsFrom().nonEmpty) PropertyRows.dynamicOptions(data, annotation.optionsFrom())
        else None
      TableFieldFactory.forAnnotatedField(
        data, field, annotation.text(), annotation.help(), annotation.options(), dynamic)
    }

  /** Reading a row is what painting a cell does, and it is where the crash was. */
  private def readable(data: Any, where: String): Boolean =
    rowsOf(data).forall { row =>
      try { row.value; row.text(); row.help(); true }
      catch {
        case ex: Throwable =>
          println(s"  FAIL $where: ${ex.getClass.getSimpleName}: ${ex.getMessage}")
          false
      }
    }

  /**
   * An object with a `String` carrying a constant list of choices — the shape that crashed the editor, kept
   * alive here now that no field in the model has it. `TableFieldOptions` reads its field as an index with
   * `Field.getInt`, which throws on a String; the factory has to pick the named row instead.
   */
  class StringChoiceFixture {
    @AvlEditorField(text = "a choice by name", options = Array("first", "second", "third"))
    private var choice: String = "second"
    def setChoice(value: String): Unit = choice = value
    def readChoice: String = choice
  }

  def main(args: Array[String]): Unit = {
    // Everything the tree can select, reached from a real aircraft rather than listed by hand, so a node
    // type nobody remembered is still walked.
    val crrcsim = TestAircraft.conventional()
    val avl = crrcsim.getAvl
    val geometry = avl.getGeometry
    val surfaces = geometry.getSurfaces.asScala.toSeq
    val sections = surfaces.flatMap(_.getSections.asScala)
    val controls = sections.flatMap(_.getControls.asScala)
    val battery = crrcsim.getConfig.getPower.getBateries.get(0)
    val shaft = battery.getShafts.get(0)

    val nodes: Seq[(String, Any)] =
      Seq(("CRRCSim", crrcsim), ("AVL", avl), ("AVLGeometry", geometry),
          ("Config", crrcsim.getConfig), ("MassInertia", crrcsim.getConfig.getMass_inertia),
          ("Power", crrcsim.getConfig.getPower), ("Battery", battery), ("Shaft", shaft),
          ("Pos", shaft.getPos)) ++
      surfaces.map(s => ("Surface " + s.getName, s: Any)) ++
      sections.take(3).map(s => ("Section", s: Any)) ++
      controls.take(3).map(c => ("Control", c: Any)) ++
      geometry.getMasses.asScala.take(2).map(m => ("Mass", m: Any)) ++
      crrcsim.getWheels.asScala.take(1).map(w => ("Wheel", w: Any)) ++
      shaft.getEngines.asScala.map(e => ("Engine", e: Any)) ++
      shaft.getPropellers.asScala.map(p => ("Propeller", p: Any))

    println("every row of every node the tree can select, built and read")
    nodes.foreach { case (where, data) =>
      val rows = rowsOf(data)
      check(f"$where%-22s ${rows.length}%2d rows read", readable(data, where))
    }

    println("what a list of choices means, which depends on the field's own type")
    // Two fields in the whole model carry a constant list, and they mean opposite things.
    val control = controls.head
    val typeRow = rowsOf(control).find(_.text() == "type of control")
    println("  Control.type   -> " + typeRow.map(_.getClass.getSimpleName).getOrElse("(no row)") +
      " = " + typeRow.map(_.value).getOrElse(""))
    check("an int field is an index into the list",
      typeRow.exists(_.isInstanceOf[TableFieldOptions]))
    check("and reads back as the name at that index", typeRow.exists(_.value == "Aileron"))

    // No field in the model carries a constant list on a `String` any more — `AVL.flightPhase` was the
    // only one and it is gone, because all three Flight Phases are judged instead of one being chosen. The
    // guarantee has to outlive it: what took the editor down was the **rule**, not that one field, and the
    // next String choice somebody adds must not rediscover it.
    val fixture = new StringChoiceFixture
    val fixtureRow = rowsOf(fixture).head
    println("  a String field  -> " + fixtureRow.getClass.getSimpleName + " = " + fixtureRow.value)
    check("a String field is the chosen name itself",
      fixtureRow.isInstanceOf[TableFieldNamedOptions])
    check("and reads back as what the object holds", fixtureRow.value == "second")
    check("and reading it does not throw, which is what killed the window",
      readable(fixture, "StringChoiceFixture"))

    println("and choosing one writes it back where the model reads it")
    fixtureRow.value = "third"
    check("a named choice writes the name", fixture.readChoice == "third")
    typeRow.foreach(_.value = "Rudder")
    check("and an indexed choice writes the index", control.getType == 2)

    println(if (ok) "PROPERTY_ROWS_OK" else "PROPERTY_ROWS_FAIL")
    if (!ok) sys.exit(1)
  }
}
