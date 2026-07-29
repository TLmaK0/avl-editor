/*
 * Verification that a virtual properties table can be asked for a row it has no backing
 * object for without throwing — an exception raised inside the SWT SetData callback
 * escapes readAndDispatch and tears down the event loop.
 * Run with:  sbt "test:runMain com.abajar.avleditor.swt.dsl.TableFieldEmptyCheck"
 */
package com.abajar.avleditor.swt.dsl

object TableFieldEmptyCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** Mirrors AvlEditor.propertiesSourceHandler over an explicit source. */
  private def sourceHandler(source: Option[Array[TableField]], index: Int): TableField =
    source match {
      case Some(fields) if index >= 0 && index < fields.length => fields(index)
      case _ => TableFieldEmpty
    }

  def main(args: Array[String]): Unit = {
    val row = new TableFieldReadOnly(
      "some instance", classOf[String].getMethod("length"), "Length", "help")
    val fields = Array[TableField](row)

    check("resolves a row when the source is set", sourceHandler(Some(fields), 0) eq row)

    // No source: happens while a virtual tree/table is being refreshed.
    val noSource = try { Some(sourceHandler(None, 0)) } catch { case _: Throwable => None }
    check("no source does not throw", noSource.isDefined)
    check("no source yields the empty placeholder", noSource.exists(_ eq TableFieldEmpty))

    // Stale repaint asking past the end of the current field list.
    val outOfRange = try { Some(sourceHandler(Some(fields), 7)) } catch { case _: Throwable => None }
    check("index past the end does not throw", outOfRange.isDefined)
    check("index past the end yields the empty placeholder", outOfRange.exists(_ eq TableFieldEmpty))

    // The placeholder must be renderable and inert for the SetData handler.
    check("placeholder renders blank text", TableFieldEmpty.text() == "" && TableFieldEmpty.value == "")
    check("placeholder is not editable", !TableFieldEmpty.editable)
    val assignable = try { TableFieldEmpty.value = "ignored"; true } catch { case _: Throwable => false }
    check("placeholder swallows writes", assignable && TableFieldEmpty.value == "")

    println(if (ok) "TABLE_FIELD_EMPTY_OK" else "TABLE_FIELD_EMPTY_FAIL")
    if (!ok) sys.exit(1)
  }
}
