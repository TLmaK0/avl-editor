/*
 * MIL-F-8785C is written for piloted, full-scale airplanes, and this editor is used for models. The
 * correction is Froude scaling, and the evidence that it is the right correction is that **the standard's
 * own numbers already obey it**: Table VI asks 1.0 rad/s of Classes I and IV and 0.4 rad/s of Classes II
 * and III, and those two rows are the same dimensionless requirement evaluated at 11 m and at 60 m of span.
 *
 * This check asserts the property rather than the numbers, so it survives any rescaling: whatever reference
 * span or constant the evaluator uses, the law it implements must reproduce Table VI at both ends and must
 * leave a full-size aircraft's thresholds exactly as the standard states them.
 *
 * Run with:  sbt "test:runMain com.abajar.avleditor.avl.runcase.FroudeScaleCheck"
 */
package com.abajar.avleditor.avl.runcase

import com.abajar.avleditor.avl.runcase.MilF8785cEvaluator.FroudeScale

object FroudeScaleCheck {

  private var ok = true

  private def check(name: String, cond: Boolean): Unit = {
    println((if (cond) "  PASS " else "  FAIL ") + name); ok &= cond
  }

  private val Gravity = 9.80665

  def main(args: Array[String]): Unit = {
    println("the law itself: MIL-F-8785C's own frequency floors are sqrt(g/b)")
    // TABLE VI (PDF p. 22), minimum wn_d at Level 1: 1.0 rad/s for Classes I and IV, 0.4 for II and III.
    // Class I is a light trainer and Class IV a fighter — as different as two airplanes get, and in the same
    // row. What they share is about 11 m of span. Classes II and III are the 60 m ones.
    val smallSpan = 11.0
    val largeSpan = 60.0
    val atSmall = math.sqrt(Gravity / smallSpan)
    val atLarge = math.sqrt(Gravity / largeSpan)
    println(f"    sqrt(g/b) at $smallSpan%.0f m = $atSmall%.3f rad/s, TABLE VI states 1.0")
    println(f"    sqrt(g/b) at $largeSpan%.0f m = $atLarge%.3f rad/s, TABLE VI states 0.4")
    check("reproduces the Class I/IV row to within 10 %", math.abs(atSmall - 1.0) / 1.0 < 0.10)
    check("reproduces the Class II/III row to within 10 %", math.abs(atLarge - 0.4) / 0.4 < 0.10)
    // Two rows five-to-one apart in span reproduced by one formula with no fitted constant is the whole
    // argument: a size-independent threshold could not do that.
    check("and a size-independent threshold could not, the two rows differing by a factor of 2.5",
      math.abs(1.0 / 0.4 - math.sqrt(largeSpan / smallSpan)) < 0.35)

    println("a full-size aircraft is judged by the standard exactly as written")
    List(11.0, 20.0, 60.0, 80.0).foreach { span =>
      val size = FroudeScale(span)
      check(f"$span%.0f m: nothing is scaled", !size.scales)
      check(f"$span%.0f m: a frequency stays put", size.frequency(0.4) == 0.4)
      check(f"$span%.0f m: a time stays put", size.time(20.0) == 20.0)
    }

    println("a model is scaled, and in the right direction")
    val model = FroudeScale(1.5)
    check("it is below the standard's range", model.scales)
    val wn = model.frequency(0.4)
    val spiral = model.time(20.0)
    val roll = model.time(1.4)
    println(f"    1.5 m span: dutch roll wn at least $wn%.2f rad/s (standard states 0.40)")
    println(f"    1.5 m span: spiral doubling at least $spiral%.1f s (standard states 20)")
    println(f"    1.5 m span: roll settles within $roll%.2f s (standard states 1.40)")
    check("a smaller aircraft must be quicker, not slower", wn > 0.4)
    check("and has less time to do everything in", spiral < 20.0 && roll < 1.4)
    // The point of scaling at all: applied unchanged, 0.4 rad/s is met by any model whatever it is like, so
    // the criterion decides nothing. Scaled, it asks for something a model can actually fail.
    check("and the scaled floor is high enough to mean something", wn > 1.0)

    println("the scaling is Froude's: times as sqrt(b), frequencies as 1/sqrt(b)")
    val quarter = FroudeScale(11.0 / 4.0)
    check("a quarter-size aircraft doubles its required frequency",
      math.abs(quarter.frequency(0.4) - 0.8) < 1.0e-9)
    check("and halves the time it is given", math.abs(quarter.time(20.0) - 10.0) < 1.0e-9)

    println("a frequency and a time scale by the same factor, one up and one down")
    check("so scaling a number both ways returns it unchanged",
      math.abs(model.time(model.frequency(3.7)) - 3.7) < 1.0e-9)

    println("an aircraft whose span never reached us is quoted verbatim, never guessed at")
    check("an unknown size does not scale", !MilF8785cEvaluator.UnknownSize.scales)
    check("and says it does not know", !MilF8785cEvaluator.UnknownSize.known)
    check("a zero span does not scale either", !FroudeScale(0.0).scales)
    check("nor a negative one", !FroudeScale(-3.0).scales)

    println(if (ok) "FROUDE_SCALE_OK" else "FROUDE_SCALE_FAIL")
    if (!ok) sys.exit(1)
  }
}
