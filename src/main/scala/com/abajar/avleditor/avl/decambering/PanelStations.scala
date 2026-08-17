/*
 * Where AVL puts its spanwise panels, deduced from AVL's own output rather than from its manual.
 *
 * The decambering method of Mukherjee, Gopalarathnam & Kim (AIAA 2003-1097) acts one variable per
 * *section*, and AVL's sections are the nodes the user drew — three on the check aircraft's wing, which
 * AVL then solves with twelve strips. Three unknowns cannot describe a stall that starts at the root and
 * spreads outward, so the analysis geometry has to carry one node per strip.
 *
 * That refinement is only harmless if the nodes land exactly where AVL's panels already end. Measured:
 * refining the wing to twelve intervals of `Nspan 1` moves a tip strip's `cl` from 0.1073 to 0.2293 and
 * the whole aircraft's `CLtot` from 0.74796 to 0.76833 — a 2.5 % different aeroplane before any stall is
 * modelled. Adding the nodes at AVL's own panel edges and leaving the surface's `Nspan`/`Sspace` alone
 * reproduces every printed digit instead. See {@code PanelRefinementCheck}.
 *
 * So the law matters, and it is read off AVL's printed stations for `Nspan 12`, `Sspace 1.0` over a
 * semispan of 0.6: 0.0026, 0.0228, 0.0620, 0.1174, 0.1852, 0.2608, 0.3392, 0.4148, 0.4826, 0.5380,
 * 0.5772, 0.5974.
 *
 * Two distinct sequences come out of that, and confusing them is the trap:
 *
 *   - the panel **edges**, `y_k = s (1 - cos(k pi / N)) / 2` for k = 0..N, which is where a node may be
 *     added without moving anything;
 *   - the station AVL **prints** for each strip, `y_i = s (1 - cos((i + 1/2) pi / N)) / 2`, the
 *     *mid-angle* of the panel — which is **not** the panel's midpoint (0.0051, 0.0252, 0.0640 ...
 *     against the printed 0.0026, 0.0228, 0.0620 ...). Anything reconstructing panel geometry from the
 *     printed stations is therefore quietly wrong at the root and the tip, which is exactly where the
 *     stall starts.
 *
 * Only `Sspace 1.0` is measured here, and nothing else is guessed at: {@link #spacingIsMeasured} gates
 * the whole refinement, and a surface spaced any other way is refused by name rather than refined by a
 * law nobody has checked. AVL's other spacings (equal, sine, negatives) cluster panels differently and
 * would put the nodes in the wrong places while looking like they worked.
 */
package com.abajar.avleditor.avl.decambering

object PanelStations {

  /** `Sspace 1.0`: cosine spacing, clustered at both ends of the span. The only law measured here. */
  val CosineSpacing = 1.0f

  /** How far a stated `Sspace` may sit from a measured one and still be treated as it. */
  private val SpacingTolerance = 1e-4f

  /**
   * Whether the panels of a surface spaced this way land where {@link #edgeFractions} says.
   *
   * Measured for cosine spacing and for nothing else. A surface answering false is refused rather than
   * refined: the refinement's whole claim is that it does not change the aeroplane, and that claim is
   * void if the nodes miss the panel edges.
   */
  def spacingIsMeasured(sspace: Float): Boolean =
    math.abs(sspace - CosineSpacing) <= SpacingTolerance

  /**
   * The panel edges, as fractions of the span, from the root (0) to the tip (1) inclusive — so `count`
   * panels give `count + 1` fractions.
   */
  def edgeFractions(count: Int): Seq[Double] =
    if (count < 1) Seq.empty
    else (0 to count).map(k => (1.0 - math.cos(k * math.Pi / count)) / 2.0)

  /**
   * The station AVL prints for each strip, as fractions of the span: the panel's mid-**angle**, not its
   * midpoint. Used to check that a refinement landed where it was supposed to, against what AVL says.
   */
  def printedStationFractions(count: Int): Seq[Double] =
    if (count < 1) Seq.empty
    else (0 until count).map(i => (1.0 - math.cos((i + 0.5) * math.Pi / count)) / 2.0)
}
