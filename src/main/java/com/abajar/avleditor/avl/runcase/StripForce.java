/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.avl.runcase;

import java.io.Serializable;

/**
 * One spanwise strip of one surface, as AVL's OPER {@code fs} command writes it.
 *
 * The whole point of it is {@link #getCl()}: AVL states it <b>referred to the strip's own area and
 * chord</b>, which is exactly the quantity a two-dimensional section {@code clmax} is measured
 * against. No rescaling stands between AVL's spanwise loading and XFOIL's aerofoil data, so the
 * comparison that decides where a wing stalls first is a comparison of like with like.
 *
 * A mirrored surface is listed <b>separately</b>, as {@code name (YDUP)}, with its stations at
 * negative y. That is why {@link #isMirrored()} exists: the sections that state the aerofoil belong
 * to the surface as drawn, so a mirrored strip has to be reflected back before it can be told which
 * two sections it lies between. Treating the two halves as one list of stations would put half the
 * wing outside every section it has.
 */
public class StripForce implements Serializable {
    static final long serialVersionUID = 20260807L;

    private final String surfaceName;
    private final boolean mirrored;
    private final int index;
    private final float yle;
    private final float chord;
    private final float area;
    private final float cl;

    public StripForce(String surfaceName, boolean mirrored, int index,
                      float yle, float chord, float area, float cl) {
        this.surfaceName = surfaceName;
        this.mirrored = mirrored;
        this.index = index;
        this.yle = yle;
        this.chord = chord;
        this.area = area;
        this.cl = cl;
    }

    /** The surface's name as the model states it, with AVL's {@code (YDUP)} marker removed. */
    public String getSurfaceName() { return surfaceName; }

    /** True when this strip belongs to the half {@code YDUPLICATE} draws rather than to the drawn half. */
    public boolean isMirrored() { return mirrored; }

    public int getIndex() { return index; }

    /** The strip's spanwise station, in the model's length unit, as AVL printed it. */
    public float getYle() { return yle; }

    /**
     * The station reflected back onto the half the model actually states, so it can be placed
     * between two of that surface's sections.
     */
    public float getStationY() { return mirrored ? -yle : yle; }

    /** The strip's chord, in the model's length unit — what its Reynolds number is built on. */
    public float getChord() { return chord; }

    public float getArea() { return area; }

    /** The local lift coefficient, referred to this strip's own area and chord. */
    public float getCl() { return cl; }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ENGLISH,
            "%s%s strip %d: y %7.4f  chord %7.4f  cl %7.4f",
            surfaceName, mirrored ? " (mirrored)" : "", index, yle, chord, cl);
    }
}
