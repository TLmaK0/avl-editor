/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.avl.mass;

import com.abajar.avleditor.avl.mass.Mass;
import com.abajar.avleditor.view.annotations.AvlEditor;
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Locale;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.abajar.avleditor.view.annotations.AvlEditorNode;

/**
 *
 * @author hfreire
 */
public abstract class MassObject implements Serializable{
    static final long serialVersionUID = 7611917382679386660L;
    protected static final Locale locale = Mass.locale;
    private final ArrayList<Mass> masses = new ArrayList<Mass>();

    /**
     * @return the masses
     */
    @AvlEditorNode(name="masses")
    @XmlElementWrapper
    @XmlElement(name="mass")
    public ArrayList<Mass> getMasses() {
        return masses;
    }

    public void writeAVLMassData(OutputStream out) {
        for(Mass mass : this.getMassesRecursive()){
            mass.writeAVLMassData(out);
        }
    }

    /** Two masses count as a pair when they agree to within this, in metres and kilograms. */
    public static final float MIRROR_TOLERANCE = 1.0e-5f;

    /**
     * A new mass starts at the middle of this element rather than at the origin: weighing a wing
     * and having its mass appear in the middle of the wing is the useful default, and the origin
     * is the nose, which is wrong for everything except a nose-mounted item.
     *
     * The position is only a starting point. It belongs to the mass from then on and can be changed
     * either in the properties table or by dragging the mass in the 3D view; nothing recomputes it,
     * so moving it sticks.
     *
     * On a mirrored element whose middle is off the plane of symmetry the pair is created, because
     * the element itself is a pair: AVL and JSBSim mirror the geometry but never the masses, so one
     * mass would state half the weight, off the centreline. The returned mass is the one on the
     * defined side; its twin follows it from then on.
     */
    public Mass createMass() {
        Mass mass = new Mass();
        float[] centre = geometricCenter();
        mass.setX(centre[0]);
        mass.setY(centre[1]);
        mass.setZ(centre[2]);
        this.getMasses().add(mass);

        Float planeY = mirrorPlaneY();
        if (planeY != null && Math.abs(centre[1] - planeY) > MIRROR_TOLERANCE) {
            String baseName = mass.getName();
            Mass twin = new Mass();
            mass.mirrorInto(twin, planeY);
            mass.setName(sideName(baseName, mass.getY() - planeY));
            twin.setName(sideName(baseName, twin.getY() - planeY));
            link(mass, twin);
            this.getMasses().add(twin);
        }
        return mass;
    }

    /**
     * Adds a single mass at a stated position, without pairing it. For callers that lay out both
     * sides themselves — {@link com.abajar.avleditor.avl.AVLGeometry#autoMassesFromVolume()} —
     * where {@link #createMass()} would add a twin to each half and end up with four.
     */
    public Mass addMassAt(float x, float y, float z) {
        Mass mass = new Mass();
        mass.setX(x);
        mass.setY(y);
        mass.setZ(z);
        this.getMasses().add(mass);
        return mass;
    }

    /**
     * The plane in y this element is mirrored about, or null when it is not mirrored. Elements that
     * are drawn on both sides carry their masses in pairs; the aircraft's own masses are absolute,
     * which is where a genuinely one-sided item belongs.
     */
    public Float mirrorPlaneY() {
        return null;
    }

    /**
     * The twin of a mass: the one on the other side of the mirror plane. A mass sitting on the plane
     * has none — it already stands for both halves.
     *
     * The link is remembered, and re-derived by matching when it is not: a pair is kept in step, so
     * at rest the two always have the same weight and station with opposite y.
     */
    public Mass mirrorMassOf(Mass mass) {
        if (mass == null) return null;
        Float planeY = mirrorPlaneY();
        if (planeY == null) return null;

        Mass linked = mass.getMirror();
        if (linked != null && linked != mass && this.getMasses().contains(linked)) return linked;

        if (Math.abs(mass.getY() - planeY) <= MIRROR_TOLERANCE) return null;
        for (Mass other : this.getMasses()) {
            if (other == mass) continue;
            if (mass.mirrors(other, planeY, MIRROR_TOLERANCE)) {
                link(mass, other);
                return other;
            }
        }
        return null;
    }

    /** True when a mass states one side of a mirrored element with no twin: half the weight the
      * element actually carries. */
    public boolean isMassMissingItsMirror(Mass mass) {
        Float planeY = mirrorPlaneY();
        if (planeY == null) return false;
        if (Math.abs(mass.getY() - planeY) <= MIRROR_TOLERANCE) return false;
        return mirrorMassOf(mass) == null;
    }

    /** Copies a mass onto its twin, reflected. Does nothing when it has none. */
    public Mass syncMirrorOf(Mass mass) {
        Mass twin = mirrorMassOf(mass);
        if (twin == null) return null;
        mass.mirrorInto(twin, mirrorPlaneY());
        return twin;
    }

    /**
     * Removes a mass and, when it has one, its twin: while the element is mirrored there is no such
     * thing as half a pair. Returns every mass removed.
     */
    public ArrayList<Mass> removeMassWithMirror(Mass mass) {
        ArrayList<Mass> removed = new ArrayList<Mass>();
        Mass twin = mirrorMassOf(mass);
        if (this.getMasses().remove(mass)) removed.add(mass);
        if (twin != null && this.getMasses().remove(twin)) removed.add(twin);
        mass.setMirror(null);
        if (twin != null) twin.setMirror(null);
        return removed;
    }

    /** Re-pairs this element's masses after a load, where the transient links are gone. */
    public void initMassMirrors() {
        Float planeY = mirrorPlaneY();
        if (planeY == null) return;
        for (Mass mass : this.getMasses()) mass.setMirror(null);
        for (Mass mass : this.getMasses()) {
            if (mass.getMirror() != null) continue;
            if (Math.abs(mass.getY() - planeY) <= MIRROR_TOLERANCE) continue;
            for (Mass other : this.getMasses()) {
                if (other == mass || other.getMirror() != null) continue;
                if (mass.mirrors(other, planeY, MIRROR_TOLERANCE)) {
                    link(mass, other);
                    break;
                }
            }
        }
    }

    private static void link(Mass one, Mass other) {
        one.setMirror(other);
        other.setMirror(one);
    }

    /**
     * How a mass names the side it is on. Shared with the masses generated from volume, so a pair
     * created by hand and a pair generated automatically read the same.
     */
    public static String sideName(String baseName, float offsetFromPlane) {
        if (Math.abs(offsetFromPlane) <= MIRROR_TOLERANCE) return baseName + " center";
        return offsetFromPlane > 0f ? baseName + " +Y" : baseName + " -Y";
    }

    /**
     * The centre of this element's own geometry, as {x, y, z}. Elements that have geometry work it
     * out from their own shape; the default is the origin, for anything that has none.
     */
    public float[] geometricCenter() {
        return new float[]{0f, 0f, 0f};
    }

    public abstract ArrayList<Mass> getMassesRecursive();
}