/*
 * The strip pitching moment AVL prints and the parser used to throw away — the other half of what the
 * decambering iteration compares against section data.
 *
 * The method's residuals are a **pair**: `dCl = (Cl)visc - (Cl)sec` and `dCm = (Cm)visc - (Cm)sec` (Mukherjee,
 * Gopalarathnam & Kim, AIAA 2003-1097, p. 7), and the moment one is what solves the second decambering
 * variable — Eq. 1, p. 5, which stands on its own precisely because a whole-chord incidence change makes no
 * moment about the quarter chord. Without `cm_c/4` per strip the two-variable method cannot be run at all, and
 * the one-variable reduction the paper sanctions is all that is available.
 *
 * Two questions here, and they need different evidence:
 *
 * <b>1. Is the right column being read?</b> AVL prints `cm_c/4` and `cm_LE` side by side, and reading the
 * wrong one gives a number that looks entirely plausible — on the check aircraft they are 0.027 and 0.649,
 * both perfectly ordinary-looking moments. So the column is identified by **its label in the table's own
 * header**, and the decisive property is asserted against real AVL output: the quantity read must be the small
 * one, near the aerodynamic centre, and not the large one referred to the leading edge. That is a fact about
 * aerofoils rather than a tolerance: the aerodynamic centre of a thin section is close to the quarter chord,
 * so `|cm_c/4|` is far smaller than the `cl/4` that separates the two references.
 *
 * <b>2. Does the parser survive a table shaped differently?</b> That needs no AVL and must not need it: the
 * point is a file this build has never seen. Files written here with the columns **reordered**, with extra
 * columns, and with the moment column **missing** establish that the values follow their labels and that a
 * table which does not name what the parser needs is refused by name rather than read as zeros.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.decambering.StripMomentCheck"
 */
package com.abajar.avleditor.avl.decambering

import com.abajar.avleditor.{AvlManager, TestAircraft}
import com.abajar.avleditor.avl.connectivity.AvlRunner
import com.abajar.avleditor.avl.runcase.StripForce
import java.io.{File, IOException, PrintWriter}
import java.nio.file.Files
import java.util.Properties
import scala.collection.JavaConverters._

object StripMomentCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  /** The attitude the check aircraft trims near, and one well past where its wing gives up. */
  private val TrimAttitude = 7.5
  private val StalledAttitude = 20.0

  /** A strip table written here, with whatever column order and labels the caller wants to try. */
  private def fileWith(header: String, rows: Seq[String]): File = {
    val file = Files.createTempFile("striptable_", ".fs").toFile
    val out = new PrintWriter(file)
    try {
      out.println(" Surface and Strip Forces by surface")
      out.println("  Surface # 1     wing")
      out.println(" Strip Forces referred to Strip Area, Chord")
      out.println(header)
      rows.foreach(out.println)
    } finally out.close()
    file
  }

  private def parsed(file: File): Seq[StripForce] = AvlRunner.parseStripForces(file).asScala.toSeq

  def main(args: Array[String]): Unit = {

    println("a table whose columns are not where AVL puts them, which no AVL run can produce")
    // AVL's own order is j Xle Yle Zle Chord Area c_cl ai cl_norm cl cd cdv cm_c/4 cm_LE C.P.x/c. Here every
    // quantity the parser needs has moved, and two of the labels it must not confuse sit either side of it.
    val shuffled = parsed(fileWith(
      "    j    cm_LE    cl      c_cl    cm_c/4   Chord   cl_norm   Area     Yle",
      Seq("     3  -0.6488   0.8004   0.1587  -0.0908   0.1998   0.8024   0.0006   0.3210")))
    check("one strip is read", shuffled.length == 1)
    check("the lift follows its label rather than its position",
      shuffled.head.getCl == 0.8004f)
    check("and so does the moment, with cm_LE and c_cl and cl_norm sitting right beside it",
      shuffled.head.getCm == -0.0908f)
    check("as do the station, the chord and the area",
      shuffled.head.getYle == 0.3210f && shuffled.head.getChord == 0.1998f &&
        shuffled.head.getArea == 0.0006f && shuffled.head.getIndex == 3)

    println("a table that does not name what the parser needs is refused by name")
    val without = fileWith(
      "    j     Xle      Yle      Zle      Chord    Area     c_cl     ai     cl_norm    cl",
      Seq("     1   0.0000   0.0008   0.0000   0.1998   0.0006   0.1587   0.1099   0.8024   0.8004"))
    val refusal = try { parsed(without); None } catch { case ex: IOException => Some(ex.getMessage) }
    check("it throws rather than returning strips with a moment of zero", refusal.isDefined)
    check("and the message names the column and quotes the header it was given",
      refusal.exists(m => m.contains("cm_c/4") && m.contains("cl_norm")))
    refusal.foreach(m => println("    " + m))

    println("what AVL really prints, which needs AVL")
    val props = new Properties()
    if (!AvlManager.ensureAvlAvailable(props)) {
      println("STRIP_MOMENT_FAIL: AVL could not be installed, so the moment was never measured")
      sys.exit(1)
    }
    val avlPath = props.getProperty("avl.path")
    val refinement = DecamberingGeometry.refine(TestAircraft.conventional(), Set("wing"))
    val model = refinement.model

    val measured = Seq(TrimAttitude, StalledAttitude).map { attitude =>
      val strips = StripInfluence.solve(avlPath, model, attitude, Seq(Map.empty[Int, Double])).head
      val wing = strips.filter(s => s.getSurfaceName == "wing" && !s.isMirrored)
      println(f"  at $attitude%.1f deg, ${strips.length}%d strips over ${strips.map(_.getSurfaceName).distinct.length}%d surfaces")
      println("    wing cm_c/4: " + wing.map(s => f"${s.getCm}%.4f").mkString(" "))

      check(f"at $attitude%.1f deg every strip has a moment AVL actually printed",
        strips.nonEmpty && strips.forall(s => !s.getCm.isNaN))
      check(f"at $attitude%.1f deg the moments are not all zero, which is what a misread column of blanks would give",
        strips.exists(s => math.abs(s.getCm) > 1e-4))

      // A symmetric aircraft at a symmetric flight condition: the mirrored half must carry the same moment,
      // strip for strip. This is what would break if the (YDUP) block were parsed with the columns of the
      // block before it, or if the two halves' strips were paired by position rather than by index.
      val mirrored = strips.filter(s => s.getSurfaceName == "wing" && s.isMirrored)
      val pairs = wing.flatMap(left => mirrored.find(_.getIndex - mirrored.map(_.getIndex).min ==
        left.getIndex - wing.map(_.getIndex).min).map((left, _)))
      check(f"at $attitude%.1f deg both halves of the wing are found, ${pairs.length}%d pairs",
        pairs.length == wing.length && pairs.nonEmpty)
      check(f"at $attitude%.1f deg the mirrored half carries the same moment, strip for strip",
        pairs.forall { case (left, right) => math.abs(left.getCm - right.getCm) < 1e-4 })

      (attitude, wing)
    }

    // The discriminator between the two moment columns, and the reason it is stated across attitudes rather
    // than as a ratio at one. The two references differ by the lift acting a quarter chord away, so
    // `cm_LE = cm_c/4 - cl/4` for a thin section: a quarter-chord moment barely moves with attitude, because
    // the aerodynamic centre it is taken about is where the extra lift arrives, while a leading-edge moment
    // moves by the whole of `cl/4`. Comparing `|cm|` against `cl/4` at a single attitude was the first way this
    // was written and it is not scale-free — at low lift the ratio grows without the column being wrong, and
    // it passed at 0.246 against a threshold of 0.25, which is a threshold sitting on top of the data.
    println("the column is the quarter-chord one, established by how little it moves with attitude")
    val ((lowAttitude, low), (highAttitude, high)) = (measured.head, measured.last)
    // Strip by strip, and that matters: the first way this was written took the largest moment change over the
    // wing against the smallest lift change over the wing, which are different strips — the tip barely changes
    // its lift while an inboard strip changes its moment most, and the comparison came out a factor of 1.2 and
    // failed. Nothing was wrong with the parsing; the property had been stated about the wing when it is a
    // property of a section.
    // Stated with no threshold in it, because there is a threshold-free way to say it: a moment referred to
    // the aerodynamic centre is the one that moves *least* with lift, so shifting the reference a quarter chord
    // **either way** must make it move more. Reading `cm_LE` instead would fail this immediately — it is this
    // moment shifted by one of those two amounts, so shifting it back would make it stationary. Which way AVL's
    // own sign convention runs need not be known, and is deliberately not assumed: both shifts are tried.
    val transferred = low.zip(high).map { case (before, after) =>
      val moved = (after.getCm - before.getCm).toDouble
      val quarterLift = (after.getCl - before.getCl).toDouble / 4.0
      val shifted = math.min(math.abs(moved - quarterLift), math.abs(moved + quarterLift))
      (before.getIndex, math.abs(moved), shifted)
    }
    println(f"  from ${lowAttitude}%.1f to ${highAttitude}%.1f deg, per strip: how far the moment moves, and how" +
      " far it would move about a point a quarter chord away")
    transferred.foreach { case (index, moved, shifted) =>
      println(f"    strip $index%2d: as read ${moved}%.4f, shifted ${shifted}%.4f — ${shifted / moved}%5.1f times more")
    }
    val worst = transferred.minBy { case (_, moved, shifted) => shifted / moved }
    check(f"every strip's moment moves less than it would about a point a quarter chord away — worst " +
      f"${worst._3 / worst._2}%.1f times at strip ${worst._1}%d",
      transferred.forall { case (_, moved, shifted) => shifted > moved })

    println(if (ok) "STRIP_MOMENT_OK" else "STRIP_MOMENT_FAIL")
    if (!ok) sys.exit(1)
  }
}
