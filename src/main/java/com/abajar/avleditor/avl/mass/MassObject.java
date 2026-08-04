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

    /**
     * The mass data of a generated AVL model, which is where the mirrored halves appear: the file is
     * a list of absolute point masses, so what {@code YDUPLICATE} draws twice has to be weighed
     * twice here.
     */
    public void writeAVLMassData(OutputStream out) {
        for(Mass mass : this.getEffectiveMassesRecursive()){
            mass.writeAVLMassData(out);
        }
    }

    /** How close two masses have to be, in metres, to be the same station. */
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
     * One mass is stored even on a mirrored element: the other half is implied, and appears when a
     * model is generated. See {@link #getEffectiveMasses()}.
     */
    public Mass createMass() {
        float[] centre = geometricCenter();
        return addMassAt(centre[0], centre[1], centre[2]);
    }

    /** Adds a mass at a stated position. */
    public Mass addMassAt(float x, float y, float z) {
        Mass mass = new Mass();
        mass.setX(x);
        mass.setY(y);
        mass.setZ(z);
        this.getMasses().add(mass);
        return mass;
    }

    /**
     * The plane in y this element is mirrored about, or null when it is not mirrored. What is drawn
     * on both sides is weighed on both sides; the aircraft's own masses are absolute, which is where
     * a genuinely one-sided item belongs.
     */
    public Float mirrorPlaneY() {
        return null;
    }

    /**
     * The copy of a mass on the other side of this element, or null when there is none: the element
     * is not mirrored, the mass sits on the plane of symmetry and so already stands for both halves,
     * or the model states the other side itself.
     *
     * The copy is derived, not stored. It is built fresh each time, so it cannot drift from the mass
     * it comes from, there is nothing to keep in step and nothing extra to delete.
     */
    public Mass virtualMirrorOf(Mass mass) {
        Float planeY = mirrorPlaneY();
        if (mass == null || planeY == null) return null;
        if (Math.abs(mass.getY() - planeY) <= MIRROR_TOLERANCE) return null;
        for (Mass other : this.getMasses()) {
            if (other == mass) continue;
            // Already stated on the other side: mirroring it again would count it twice. This is how
            // files written before the mirror was implied — every '+Y'/'-Y' pair — keep their weight.
            if (mass.isAtMirroredPositionOf(other, planeY, MIRROR_TOLERANCE)) return null;
        }
        return mass.mirroredCopy(planeY);
    }

    public boolean hasVirtualMirror(Mass mass) {
        return virtualMirrorOf(mass) != null;
    }

    /** This element's masses as a generated model sees them: the stored ones and the implied copies. */
    public ArrayList<Mass> getEffectiveMasses() {
        ArrayList<Mass> effective = new ArrayList<Mass>();
        for (Mass mass : this.getMasses()) {
            effective.add(mass);
            Mass mirror = virtualMirrorOf(mass);
            if (mirror != null) effective.add(mirror);
        }
        return effective;
    }

    /**
     * The same over this element and everything under it that can hold masses. This is the list to
     * generate from — an AVL mass file, a JSBSim mass balance, a centre of gravity — while
     * {@link #getMassesRecursive()} stays what the model stores.
     */
    public ArrayList<Mass> getEffectiveMassesRecursive() {
        ArrayList<Mass> effective = new ArrayList<Mass>();
        for (MassObject element : getMassElements()) {
            effective.addAll(element.getEffectiveMasses());
        }
        return effective;
    }

    /**
     * This element and every element under it that can hold masses. Each one knows its own mirror
     * plane, which is why the expansion is done per element rather than over a flattened list.
     */
    public ArrayList<MassObject> getMassElements() {
        ArrayList<MassObject> elements = new ArrayList<MassObject>();
        elements.add(this);
        return elements;
    }

    /**
     * How a mass names the side it is on, for the masses generated from volume.
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