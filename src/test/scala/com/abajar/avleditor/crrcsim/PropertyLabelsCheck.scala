/*
 * Property labels must read as words, not as bare symbols. "U_0" and "J" tell the user nothing
 * about which row to edit, which is what made a validation message ("the battery voltage must be
 * greater than zero") impossible to act on: no such row existed in the table.
 * Run with:  sbt "test:runMain com.abajar.avleditor.crrcsim.PropertyLabelsCheck"
 */
package com.abajar.avleditor.crrcsim

import com.abajar.avleditor.view.annotations.AvlEditorField

object PropertyLabelsCheck {

  /** The propulsion chain and the collision points: everything reachable from the toolbar. */
  private val classes: Seq[Class[_]] = Seq(
    classOf[Battery], classOf[Shaft], classOf[Propeller], classOf[Engine],
    classOf[EngineData], classOf[EngineDataIdle], classOf[SimpleTrust], classOf[Gearing],
    classOf[Wheel], classOf[Spring], classOf[Pos], classOf[MassInertia])

  /** Readable means at least one word of three or more letters: "Inertia (J)" yes, "J" no. */
  private val Word = """[A-Za-z]{3,}""".r

  def main(args: Array[String]): Unit = {
    var ok = true
    classes.foreach { cls =>
      val labels = cls.getDeclaredFields
        .flatMap(f => Option(f.getAnnotation(classOf[AvlEditorField])).map(a => (f.getName, a.text())))
      labels.foreach { case (field, text) =>
        if (Word.findFirstIn(text).isEmpty) {
          println(s"  FAIL ${cls.getSimpleName}.$field is labelled '$text'")
          ok = false
        }
      }
      println(f"  ${cls.getSimpleName}%-16s ${labels.length}%2d labels")
    }
    println(if (ok) "PROPERTY_LABELS_OK" else "PROPERTY_LABELS_FAIL")
    if (!ok) sys.exit(1)
  }
}
