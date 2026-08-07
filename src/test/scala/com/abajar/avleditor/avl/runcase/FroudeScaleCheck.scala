/*
 * MIL-F-8785C is written for piloted, full-scale airplanes, and this editor is used for models. The
 * correction is Froude scaling, and the evidence that it is the right correction is that **the standard's
 * own numbers already obey it**: Table VI asks 1.0 rad/s of Classes I and IV and 0.4 rad/s of Classes II
 * and III, and those two rows are the same dimensionless requirement evaluated at the spans of the aircraft they cover.
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
    // TABLE VI (p. 22), minimum wn_d at Level 1: 1.0 rad/s for Classes I and IV, 0.4 for II and III.
    //
    // The spans are **not** recalled: they are read off NASA CR-2144, *Aircraft Handling Qualities Data*
    // (Heffley and Jewell, 1972), a contemporary of the standard tabulating the fleet it was written
    // around. An earlier version of this check used remembered figures, and one of them was picked because
    // it flattered the fit.
    val Foot = 0.3048
    val f104a = 21.94 * Foot   // CR-2144 figure III-2, p. 35.  Class IV
    val f4c = 38.67 * Foot     // CR-2144 figure IV-2,  p. 64.  Class IV
    val c5a = 219.2 * Foot     // CR-2144 figure X-2,   p. 246. Class III
    def law(span: Double) = math.sqrt(Gravity / span)
    println(f"    F-104A  b = ${f104a}%6.3f m   sqrt(g/b) = ${law(f104a)}%.3f   TABLE VI asks 1.0 of I and IV")
    println(f"    F-4C    b = ${f4c}%6.3f m   sqrt(g/b) = ${law(f4c)}%.3f")
    println(f"    C-5A    b = ${c5a}%6.3f m   sqrt(g/b) = ${law(c5a)}%.3f   TABLE VI asks 0.4 of II and III")

    // The strongest form of the claim: the two Class IV aircraft **bracket** the row's 1.0, rather than one
    // of them approximating it. That cannot be arranged by choosing a convenient aircraft.
    check("the Class I/IV row falls between the two Class IV aircraft",
      law(f4c) < 1.0 && law(f104a) > 1.0)
    check("and the Class II/III row is reproduced within 10 %", math.abs(law(c5a) - 0.4) / 0.4 < 0.10)
    // Two rows an order of magnitude apart in span, reproduced by one formula with no fitted constant.
    check("a size-independent threshold could not do that, the rows differing by a factor of 2.5",
      math.abs(1.0 / 0.4 - math.sqrt(c5a / f4c)) < 0.35)

    println("so the reference is derived, not chosen")
    // sqrt(g/b) = 1.0 exactly when b = g. That is where the standard's own Class I/IV floor and the law
    // agree, and it lands between the two fighters rather than on either.
    val reference = FroudeScale(1.0).ReferenceSpanMetres
    println(f"    reference span ${reference}%.2f m, between the F-104A's ${f104a}%.2f and the F-4C's ${f4c}%.2f")
    check("it is the span at which the law equals TABLE VI's 1.0 rad/s",
      math.abs(law(reference) - 1.0) < 1.0e-9)
    check("and it sits between the two aircraft that share that row",
      reference > f104a && reference < f4c)

    println("a full-size aircraft is judged by the standard exactly as written")
    List(9.81, 11.0, 20.0, 60.0, 80.0).foreach { span =>
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
    val quarter = FroudeScale(FroudeScale(1.0).ReferenceSpanMetres / 4.0)
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
