/*
 * The analysis geometry the decambering acts on: one section node per strip, and the same aeroplane.
 *
 * Iterated decambering (Mukherjee, Gopalarathnam & Kim, AIAA 2003-1097, the AIAA version of the
 * Journal of Aircraft 43(3) 2006 paper, approved for #17 in full) reduces each *section's* camber until
 * that section sits on its own viscous curve. Its unknowns are therefore per section, and AVL's sections
 * are the nodes the user drew: the check aircraft's wing is drawn with three and solved with twelve
 * strips, so as drawn there are three unknowns per half to describe a stall that begins at the root and
 * spreads outward. Three cannot localise anything.
 *
 * So the geometry handed to AVL is refined to one node per strip — and the refinement's entire claim is
 * that **it does not change the aeroplane**. That claim is the load-bearing one in this whole feature: a
 * method whose first act is to move the pre-stall curve by 2.5 % would poison everything measured
 * downstream of it, including the invariant #17 rests on, that below the stall the answer is AVL's own
 * answer unchanged. {@code PanelRefinementCheck} asserts it against real AVL output — every strip's
 * station, every strip's area and `CLtot` — rather than trusting this comment.
 *
 * What makes it free is one line of geometry writing, and it took a wrong attempt to find:
 *
 *   - refining to twelve intervals of `Nspan 1` **changes the aeroplane**: `CLtot` 0.74796 -> 0.76833,
 *     and the tip strip's `cl` 0.1073 -> 0.2293. One panel per interval spaces the strips evenly, while
 *     the surface's own `Sspace 1.0` clusters them at the root and the tip;
 *   - adding the nodes **at AVL's own panel edges** and leaving the surface's `Nspan`/`Sspace` untouched
 *     leaves AVL distributing its panels exactly as before. The extra nodes then merely give the
 *     decambering somewhere to act. Identical to every printed digit, on all twelve strips.
 *
 * Nothing here is refined on a guess. A surface is refused, by name and with the reason, when:
 *
 *   - its spacing is not the cosine law measured in {@link PanelStations} — the nodes would miss the
 *     panel edges while looking like they worked;
 *   - a section states its own `Nspan`, which overrides the surface's distribution for that interval and
 *     changes where the edges are;
 *   - the two ends of an interval state **different aerofoils**. AVL blends between them, and a node
 *     inserted inside that blend needs the blended aerofoil at its own station, which neither the model
 *     nor AVL states. Inventing one is exactly the silent fallback this project refuses; a wing built
 *     from one aerofoil refines, one that changes section along the span says so and is left alone.
 *
 * A refusal costs the decambering that surface's resolution, which is a stated limitation, and never
 * quietly a different aeroplane.
 */
package com.abajar.avleditor.avl.decambering

import com.abajar.avleditor.avl.AVL
import com.abajar.avleditor.avl.geometry.{Section, Surface}
import com.abajar.avleditor.crrcsim.CRRCSim
import com.abajar.avleditor.view.avl.DeepCopy
import scala.collection.JavaConverters._

/** What the refinement did to one surface, so the caller can say it rather than assume it. */
case class RefinedSurface(name: String, panels: Int, nodesBefore: Int, nodesAfter: Int,
                          stationFractions: Seq[Double])

/** A surface left as drawn, and why — never silently. */
case class RefusedSurface(name: String, reason: String)

/** The refined model, with an account of every surface: refined with its nodes, or refused with its reason. */
case class Refinement(model: CRRCSim, refined: Seq[RefinedSurface], refused: Seq[RefusedSurface]) {

  /** Lines for the log: what was refined, what was not, and why. */
  def report: Seq[String] =
    refined.map(s => f"  ${s.name}%s: ${s.panels}%d panels, ${s.nodesBefore}%d nodes -> " +
      f"${s.nodesAfter}%d, one per strip") ++
    refused.map(s => s"  ${s.name}: left as drawn, ${s.reason}")
}

object DecamberingGeometry {

  /** Two nodes closer than this fraction of the span are the same node, and none is added. */
  private val SameNodeFraction = 1e-6

  /**
   * A copy of the model whose named surfaces carry one section node per strip.
   *
   * A **copy**: the user's model is never touched, and the copy's transient parent links are restored the
   * way loading a file does, because a section that does not know its surface does not know which plane it
   * mirrors its masses about (the lesson `DuplicateCheck` pins).
   *
   * @param surfaceNames the surfaces to refine — the ones the decambering has section data for. Others
   *                     are reported as refused with that as the reason, so the account is complete.
   */
  def refine(model: CRRCSim, surfaceNames: Set[String]): Refinement = {
    val copy = DeepCopy.of(model)
    if (copy == null) throw new IllegalStateException(
      "The model could not be copied for the decambering analysis, so it cannot be refined. " +
      "A half-copied aircraft is worse than a refusal.")
    copy.getAvl.getGeometry.initParents()

    val results = copy.getAvl.getGeometry.getSurfaces.asScala.toSeq.map { surface =>
      if (!surfaceNames.contains(surface.getName))
        Right(RefusedSurface(surface.getName, "no section data for it, so nothing to decamber there"))
      else refineSurface(surface)
    }

    // The new nodes need to know their surface, and then the aircraft has to be weighed again. Measured the
    // hard way: the copy's first AVL run refused with "weight 0.0 kg". What a model weighs, what its inertias
    // are and where it balances are all **derived** by `calculate()` and pushed, not stored — so they are as
    // transient as the parent links, and a deep copy arrives with none of them. Deriving them again here is
    // the same rule the export path already follows: validate after `calculate()`, or every model reports
    // zero mass.
    copy.getAvl.getGeometry.initParents()
    copy.calculate()
    Refinement(copy,
      results.collect { case Left(done) => done },
      results.collect { case Right(refused) => refused })
  }

  /** The surfaces a decambering of the whole aircraft would want: every one that answers the attitude. */
  def refine(model: CRRCSim): Refinement =
    refine(model, model.getAvl.getGeometry.getSurfaces.asScala.map(_.getName).toSet)

  private def refineSurface(surface: Surface): Either[RefinedSurface, RefusedSurface] = {
    val sections = surface.getSections.asScala.toList
    val panels = surface.getNspan

    if (sections.size < 2)
      return Right(RefusedSurface(surface.getName, "it has fewer than two sections, so it has no span"))
    if (panels < 1)
      return Right(RefusedSurface(surface.getName,
        s"it states no Nspan of its own (Nspan $panels), so AVL's panel edges are not known here"))
    if (!PanelStations.spacingIsMeasured(surface.getSspace))
      return Right(RefusedSurface(surface.getName,
        f"its spacing is Sspace ${surface.getSspace}%.3f, and only the cosine law " +
        f"(Sspace ${PanelStations.CosineSpacing}%.1f) has been measured against AVL's own stations"))
    if (sections.exists(_.getNspan != 0))
      return Right(RefusedSurface(surface.getName,
        "a section states its own Nspan, which overrides the surface's distribution and moves the " +
        "panel edges"))

    val arcLengths = leadingEdgeArcLengths(sections)
    val span = arcLengths.last
    if (span <= 0f)
      return Right(RefusedSurface(surface.getName, "its sections are all at one station, so it has no span"))

    val mixed = sections.sliding(2).collectFirst {
      case Seq(inboard, outboard) if !sameAerofoil(inboard, outboard) => (inboard, outboard)
    }
    if (mixed.isDefined) {
      val (inboard, outboard) = mixed.get
      return Right(RefusedSurface(surface.getName,
        s"the interval between y = ${inboard.getYle} and ${outboard.getYle} blends " +
        s"${aerofoilName(inboard)} into ${aerofoilName(outboard)}, and the aerofoil at a station " +
        "inside that blend is stated by neither the model nor AVL"))
    }

    val nodesBefore = sections.size
    val wanted = PanelStations.edgeFractions(panels)
    val added = wanted.filter(fraction =>
      !arcLengths.exists(existing => math.abs(existing / span - fraction) < SameNodeFraction))

    added.foreach(fraction => insertNode(surface, (fraction * span).toFloat))

    Left(RefinedSurface(surface.getName, panels, nodesBefore,
      surface.getSections.size, PanelStations.printedStationFractions(panels)))
  }

  /**
   * How far along the leading edge each drawn section sits, from the root.
   *
   * Along the leading edge rather than across the span, so a wing with dihedral or a fin is measured the
   * way AVL measures it. That much is reasoning about AVL rather than a measurement of it: the check
   * aircraft's wing is flat, where arc length and y are the same number, so what
   * {@code PanelRefinementCheck} proves is the cosine law and not this choice. A non-planar surface is
   * refined on the same law and its stations are compared against AVL's the same way, so a wrong guess
   * here shows up as a failed check on such an aircraft rather than as a wrong aeroplane.
   */
  private def leadingEdgeArcLengths(sections: List[Section]): List[Float] =
    sections.sliding(2).foldLeft(List(0f)) { case (lengths, Seq(inboard, outboard)) =>
      val step = math.hypot(outboard.getYle - inboard.getYle, outboard.getZle - inboard.getZle).toFloat
      lengths :+ (lengths.last + step)
    }

  /**
   * A node at a given distance along the leading edge, interpolated linearly inside the interval that
   * contains it — which is what AVL does between defining sections, and why the aeroplane is unchanged.
   */
  private def insertNode(surface: Surface, arcLength: Float): Unit = {
    val sections = surface.getSections
    val lengths = leadingEdgeArcLengths(sections.asScala.toList)
    val interval = lengths.indices.dropRight(1).find(i =>
      arcLength > lengths(i) && arcLength < lengths(i + 1))
    interval.foreach { i =>
      val inboard = sections.get(i)
      val outboard = sections.get(i + 1)
      val span = lengths(i + 1) - lengths(i)
      val fraction = (arcLength - lengths(i)) / span

      val node = new Section()
      node.setParentSurface(surface)
      node.setXle(mix(inboard.getXle, outboard.getXle, fraction))
      node.setYle(mix(inboard.getYle, outboard.getYle, fraction))
      node.setZle(mix(inboard.getZle, outboard.getZle, fraction))
      node.setChord(mix(inboard.getChord, outboard.getChord, fraction))
      node.setAinc(mix(inboard.getAinc, outboard.getAinc, fraction))
      // Both ends state the same aerofoil — a mixed interval is refused above — so the node carries it
      // rather than a blend nobody stated.
      node.setNACA(inboard.getNACA)
      node.setAFILE(inboard.getAFILE)
      node.setX1(inboard.getX1)
      node.setX2(inboard.getX2)
      sections.add(i + 1, node)
    }
  }

  private def mix(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

  private def aerofoilName(section: Section): String =
    if (section.getNACA != null && !section.getNACA.isEmpty) "NACA " + section.getNACA
    else if (section.getAFILE != null && !section.getAFILE.isEmpty) section.getAFILE
    else "a flat plate"

  private def sameAerofoil(one: Section, other: Section): Boolean =
    text(one.getNACA) == text(other.getNACA) && text(one.getAFILE) == text(other.getAFILE) &&
      one.getX1 == other.getX1 && one.getX2 == other.getX2

  private def text(value: String): String = if (value == null) "" else value
}
