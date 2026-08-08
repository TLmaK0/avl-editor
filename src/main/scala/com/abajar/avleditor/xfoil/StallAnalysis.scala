/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.xfoil

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.logging.{Level, Logger}
import scala.collection.JavaConverters._
import scala.collection.mutable

import com.abajar.avleditor.avl.AVL
import com.abajar.avleditor.avl.geometry.{Section, Surface}
import com.abajar.avleditor.avl.runcase.AvlCalculation

/**
 * The one place that puts AVL's spanwise loading and XFOIL's aerofoil data together and comes back with a
 * stall speed.
 *
 * It is a **separate stage** from the AVL run for a plain reason: XFOIL is an external process per
 * aerofoil, it is the slowest thing in the analysis path, and a run of AVL that answers everything else
 * must not fail because a binary is missing. So it takes a finished calculation, and everything it cannot
 * do it reports by name — never by substituting a figure.
 *
 * Reynolds is **iterated**, because it is circular: a section's `clmax` depends on the Reynolds number,
 * which depends on the speed, and the speed we want is the one at which that `clmax` is reached. It
 * converges in two or three passes — `clmax` moves a few percent for a factor of two in Reynolds and the
 * speed goes as its square root — and polars are cached by aerofoil and Reynolds, so the passes after the
 * first mostly cost nothing.
 */
case class StallResult(
  clMax: Double,
  alphaDeg: Double,
  stallSpeedMetresPerSecond: Double,
  critical: StallOnset,
  reynoldsAtCriticalSection: Double,
  worstResidual: Double,
  notes: Seq[String]
)

object StallAnalysis {

  private val logger = Logger.getLogger(StallAnalysis.getClass.getName)

  /** How close two passes of the Reynolds iteration must come before the answer is taken. */
  private val SpeedTolerance = 0.01
  private val MostPasses = 4

  /**
   * Two Reynolds numbers within this factor of each other are the same run. XFOIL's `clmax` moves by a
   * few percent for a factor of two, so 2 % of Reynolds is far below anything that shows in the answer,
   * and it is what stops thirteen strips of one wing from becoming thirteen XFOIL processes.
   */
  private val ReynoldsBucket = 0.02

  def analyse(avl: AVL, calculation: AvlCalculation, xfoilPath: String,
              originPath: Path): Either[String, StallResult] = {
    if (xfoilPath == null || xfoilPath.isEmpty || !new File(xfoilPath).canExecute)
      return Left("XFOIL is not available, and it is what says where an aerofoil stops lifting. AVL is " +
        "inviscid and cannot see a stall at all.")

    val config = calculation.getConfiguration
    if (config == null) return Left("AVL returned no configuration for this run.")

    val sweep = calculation.getAlphaSweep.asScala.toSeq
    val loading = sweep.map(point => (point.getAlphaDeg.toDouble, point.getStrips.asScala.toSeq))
    if (loading.count(_._2.nonEmpty) < 2)
      return Left("AVL returned the spanwise loading at fewer than two attitudes, so no station has a " +
        "line of lift against attitude to follow.")

    val metresPerLengthUnit = config.getMetresPerLengthUnit.toDouble
    val stations = WingMaximumLift.stations(loading, metresPerLengthUnit)
    if (stations.isEmpty) return Left("AVL's spanwise loading names no station this analysis can follow.")

    val liftCurve = sweep.map(point => (point.getAlphaDeg.toDouble, point.getCl.toDouble))

    val density = config.getAirDensity.toDouble
    val weightNewtons = config.getAnalysisMassKg.toDouble * AVL.GRAVITY
    val wingAreaM2 = surfaceReferenceArea(avl, metresPerLengthUnit)
    if (density <= 0.0 || weightNewtons <= 0.0 || wingAreaM2 <= 0.0)
      return Left("the run recorded no air density, weight or reference area, and a stall speed is all " +
        "three of them.")

    val runner = new XfoilRunner(xfoilPath)
    val polars = mutable.Map[(String, Long), SectionOutcome]()

    var speed = config.getVelocityMetresPerSecond.toDouble
    if (speed <= 0.0) return Left("the run recorded no speed, so no station has a Reynolds number.")

    var answer: Either[String, StallResult] = Left("the stall was never worked out")
    var pass = 0
    var settled = false
    while (pass < MostPasses && !settled) {
      pass += 1
      answer = onePass(avl, stations, liftCurve, density, speed, weightNewtons, wingAreaM2,
        metresPerLengthUnit, originPath, runner, polars)
      answer match {
        case Left(_) => settled = true
        case Right(result) =>
          val moved = math.abs(result.stallSpeedMetresPerSecond - speed) / speed
          logger.log(Level.INFO, f"Stall pass $pass%d: CLmax ${result.clMax}%.3f at " +
            f"${result.alphaDeg}%.2f deg, Vs ${result.stallSpeedMetresPerSecond}%.2f m/s " +
            f"(sections read at ${speed}%.2f m/s, ${moved * 100}%.1f %% away)")
          settled = moved < SpeedTolerance
          speed = result.stallSpeedMetresPerSecond
      }
    }

    answer.right.map { result =>
      if (settled) result
      else result.copy(notes = result.notes :+
        f"The Reynolds iteration was still moving by more than ${SpeedTolerance * 100}%.0f %% after " +
          f"$MostPasses%d passes; the last answer is the one reported.")
    }
  }

  private def onePass(avl: AVL, stations: Seq[LoadedStation],
                      liftCurve: Seq[(Double, Double)], density: Double, speed: Double,
                      weightNewtons: Double, wingAreaM2: Double, metresPerLengthUnit: Double,
                      originPath: Path, runner: XfoilRunner,
                      polars: mutable.Map[(String, Long), SectionOutcome]
                     ): Either[String, StallResult] = {

    val sections = sectionsBySurface(avl, metresPerLengthUnit)
    val resolvedOrProblem = stations.map { station =>
      sections.get(station.surface) match {
        case None =>
          Left(f"AVL reports a strip of a surface called '${station.surface}%s', which the model has no " +
            "surface of that name for.")
        case Some(list) =>
          bracketing(list, station.station).right.flatMap { case (low, high, fraction) =>
            for {
              a <- sectionStall(low, density, speed, originPath, runner, polars).right
              b <- sectionStall(high, density, speed, originPath, runner, polars).right
            } yield (a, b) match {
              // Between two sections, a limit is only known where both ends of the interpolation are.
              case (SectionKnown(one), SectionKnown(two)) =>
                (StationLimits(station,
                  Some(one.clMax + (two.clMax - one.clMax) * fraction),
                  for (aMin <- one.clMin; bMin <- two.clMin) yield aMin + (bMin - aMin) * fraction), None)
              case _ =>
                val why = Seq(a, b).collect { case SectionUnknown(reason) => reason }.distinct.mkString("; ")
                (StationLimits(station, None, None), Some(station.surface + ": " + why))
            }
          }
      }
    }

    // A model problem — a surface with no aerofoil, a missing file — stops everything, because it is
    // something to go and fix. An aerofoil XFOIL could not see the end of is a limit of the measurement:
    // that station simply takes no part, and the note says which and why.
    val problems = resolvedOrProblem.collect { case Left(why) => why }.distinct
    if (problems.nonEmpty) return Left(problems.mkString("; "))
    val resolved = resolvedOrProblem.collect { case Right((limits, _)) => limits }
    val unknown = resolvedOrProblem.collect { case Right((_, Some(why))) => why }.distinct

    // A station whose lift falls with attitude carries a download, and it stalls the other way up. Its
    // aerofoil only has a negative stall to be judged against if XFOIL found one within the sweep, and a
    // cambered section often does not by -8 degrees. Saying how many were left out is the difference
    // between a limit of the measurement and a silent omission.
    val unjudgedDownward = resolved.count(limit =>
      limit.station.clPerDegree < 0.0 && limit.clMax.isDefined && limit.clMin.isEmpty)
    val notes =
      (if (unknown.isEmpty) Nil
       else List("Some sections have no known limit and took no part in this, which means the aircraft " +
         "could give up there first without this saying so: " + unknown.mkString(" / "))) ++
      (if (unjudgedDownward == 0) Nil
       else List(f"$unjudgedDownward%d of ${resolved.length}%d stations lift downwards and their " +
         f"aerofoils did not stall within the sweep XFOIL was asked for (down to " +
         f"${SectionStall.AlphaStartDeg}%.0f deg); they were left out of the search. This is about a " +
         "tailplane's own stall, not the wing's."))

    WingMaximumLift.onset(resolved).right.flatMap { onset =>
      WingMaximumLift.liftAt(liftCurve, onset.alphaDeg) match {
        case None =>
          Left(f"the stall onset falls at ${onset.alphaDeg}%.1f deg, which is outside the attitudes AVL " +
            "measured, so there is no lift coefficient to read there.")
        case Some(clMax) =>
          WingMaximumLift.stallSpeed(weightNewtons, density, wingAreaM2, clMax) match {
            case None => Left("the stall speed needs a weight, an air density, a reference area and a " +
              f"positive maximum lift coefficient; the maximum came out as $clMax%.3f.")
            case Some(vs) =>
              val re = StandardAir.reynolds(density, speed, onset.station.chordMetres).getOrElse(0.0)
              Right(StallResult(clMax, onset.alphaDeg, vs, onset, re,
                stations.map(_.worstResidual).max, notes))
          }
      }
    }
  }

  /**
   * The reference area in m². It is the area every coefficient AVL prints is referred to, so it is the
   * area the stall speed has to use — the same `Sref` and not a wing area worked out again from the
   * geometry, which would put a `CL` from one aeroplane over the area of another.
   */
  private def surfaceReferenceArea(avl: AVL, metresPerLengthUnit: Double): Double =
    avl.analysisReferenceAreaSquareMetres.toDouble

  /** Each surface's sections with their absolute spanwise stations — the surface's own `TRANSLATE` added. */
  private def sectionsBySurface(avl: AVL, metresPerLengthUnit: Double): Map[String, Seq[(Double, Section, Double)]] = {
    val geometry = avl.getGeometry
    if (geometry == null) return Map.empty
    geometry.getSurfaces.asScala.map { surface: Surface =>
      val stations = surface.getSections.asScala.toSeq.map { section =>
        (section.getYle.toDouble + surface.getdY.toDouble, section,
         section.getChord.toDouble * metresPerLengthUnit)
      }.sortBy(_._1)
      (surface.getName, stations)
    }.toMap
  }

  /**
   * The two sections a station lies between, and how far along it is.
   *
   * A station a hair outside the outermost section — AVL puts a strip's control point inside the panel,
   * not on its edge, so this happens at every tip — takes that section's aerofoil rather than being
   * refused. Anything further out than the surface itself is a mismatch worth saying out loud.
   */
  private def bracketing(sections: Seq[(Double, Section, Double)], station: Double
                        ): Either[String, ((Section, Double), (Section, Double), Double)] = {
    if (sections.isEmpty) return Left("a surface AVL reported strips for states no sections.")
    if (sections.length == 1) {
      val only = sections.head
      return Right(((only._2, only._3), (only._2, only._3), 0.0))
    }
    val first = sections.head
    val last = sections.last
    if (station <= first._1) return Right(((first._2, first._3), (first._2, first._3), 0.0))
    if (station >= last._1) return Right(((last._2, last._3), (last._2, last._3), 0.0))
    val pair = sections.sliding(2).collectFirst {
      case Seq(low, high) if station >= low._1 && station <= high._1 =>
        val span = high._1 - low._1
        ((low._2, low._3), (high._2, high._3), if (span <= 0.0) 0.0 else (station - low._1) / span)
    }
    pair.toRight("a spanwise station fell between no two sections of its surface.")
  }

  /**
   * What came back about one section: its stall, or the reason there is not one to be had.
   *
   * `SectionUnknown` is not a failure of the model — it is XFOIL declining to show an aerofoil giving up,
   * which at a model tailplane's Reynolds number is a normal answer rather than an error.
   */
  private sealed trait SectionOutcome
  private case class SectionKnown(data: SectionStallData) extends SectionOutcome
  private case class SectionUnknown(why: String) extends SectionOutcome

  /** One section's stall, from XFOIL, cached by aerofoil and Reynolds. */
  private def sectionStall(section: (Section, Double), density: Double, speed: Double, originPath: Path,
                           runner: XfoilRunner,
                           polars: mutable.Map[(String, Long), SectionOutcome]
                          ): Either[String, SectionOutcome] = {
    val (which, chordMetres) = section
    aerofoilOf(which, originPath).right.flatMap { case (airfoil, label) =>
      StandardAir.reynolds(density, speed, chordMetres) match {
        case None =>
          Left(f"a section states a chord of $chordMetres%.4f m, which gives no Reynolds number at " +
            f"$speed%.2f m/s in air of $density%.4f kg/m3.")
        case Some(re) =>
          val bucket = math.round(math.log(re) / math.log(1.0 + ReynoldsBucket))
          Right(polars.getOrElseUpdate((label, bucket), {
            logger.log(Level.INFO, f"XFOIL: $label%s at Re ${re}%.0f")
            val polar = runner.computePolar(airfoil, re, 0.0,
              SectionStall.AlphaStartDeg, SectionStall.AlphaEndDeg, SectionStall.AlphaStepDeg,
              iterations = 200, timeoutSeconds = 300)
            SectionStall.fromPolar(polar, re) match {
              case Right(data) => SectionKnown(data)
              case Left(why) => SectionUnknown(s"$label at Re ${re.toLong}, $why")
            }
          }))
      }
    }
  }

  /** What a section says it is made of, as something XFOIL can load — or why it does not say. */
  private def aerofoilOf(section: Section, originPath: Path): Either[String, (XfoilAirfoil, String)] = {
    val naca = Option(section.getNACA).map(_.trim).getOrElse("")
    val afile = Option(section.getAFILE).map(_.trim).getOrElse("")
    if (naca.nonEmpty) {
      val digits = naca.replaceAll("[^0-9]", "")
      if (digits.length != 4 && digits.length != 5)
        Left(s"a section states NACA '$naca', and XFOIL knows the 4- and 5-digit families only.")
      else Right((NacaAirfoil(digits), s"NACA $digits"))
    } else if (afile.nonEmpty) {
      val path = if (Paths.get(afile).isAbsolute) Paths.get(afile)
                 else if (originPath == null) Paths.get(afile)
                 else originPath.resolve(afile)
      if (!path.toFile.exists) Left(s"a section states the aerofoil file '$afile', which is not there.")
      else Right((DatAirfoil(path.toString), path.getFileName.toString))
    } else {
      Left("a section states neither a NACA number nor an aerofoil file, so there is nothing to ask " +
        "XFOIL about.")
    }
  }
}
