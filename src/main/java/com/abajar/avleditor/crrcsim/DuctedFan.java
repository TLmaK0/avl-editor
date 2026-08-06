/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.crrcsim;

import com.abajar.avleditor.view.annotations.AvlEditor;
import com.abajar.avleditor.view.annotations.AvlEditorField;
import com.abajar.avleditor.view.annotations.AvlEditorNode;
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS;
import java.io.Serializable;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * An electric ducted fan, driven by the motor on the same shaft.
 *
 * A sibling of {@link Propeller} rather than a flag on it: a fan has no pitch and no folding threshold, and
 * offering a propeller's fields for it would be offering controls that mean nothing.
 *
 * It states only what a fan's own listing gives: the duct's inner diameter, the blade count, and a length that
 * is drawn and enters no calculation. The revolutions and the power are <b>not</b> repeated here — they belong
 * to the motor that drives it ({@link Engine}'s data rows, voltage times current and its rpm), and asking for
 * them twice is asking for two answers.
 *
 * <b>No thrust is asked for.</b> A fan bought as a rotor and a housing has none published, because the thrust
 * depends on the motor fitted — and it does not need to be: with the bore, the revolutions and the power,
 * {@link com.abajar.avleditor.jsbsim.DuctedFanCurves} derives the thrust at every speed from momentum theory,
 * with one documented figure of merit for the duct's losses.
 */
@AvlEditor(buttons={ENABLE_BUTTONS.DELETE})
public class DuctedFan implements Serializable {
    static final long serialVersionUID = 20260806L;


    /** A 70 mm fan, the commonest size: 68 mm of bore, 12 blades. */
    public static final float DEFAULT_INNER_DIAMETER_MM = 68f;
    public static final int DEFAULT_BLADES = 12;
    /** A fan unit is about as long as it is wide. */
    public static final float DEFAULT_LENGTH_MM = 70f;
    public static final float MIN_SIZE_MM = 1f;

    @AvlEditorField(text="Inner diameter (mm)",
        help="The duct's inner diameter in mm: the disc the air actually passes through, which is the\n"
        + "figure the thrust follows from. It is the smallest of the three a fan is sold with — not the\n"
        + "housing's outside diameter, and not the inlet lip.\n\n"
        + "A '70 mm' fan is about 68 mm here; a '90 mm' one about 91 mm."
    )
    private float innerDiameterMm = DEFAULT_INNER_DIAMETER_MM;

    @AvlEditorField(text="Blades",
        help="How many blades the fan has: 5 to 12 on the usual units."
    )
    private int blades = DEFAULT_BLADES;

    @AvlEditorField(text="Length (mm)",
        help="How long the fan unit is, in mm, for the 3D view: it is drawn as a cylinder of the bore over\n"
        + "this length, so it can be seen whether it fits where it is being put.\n\n"
        + "It enters no calculation. The thrust follows from the bore, the revolutions and the power; a duct\n"
        + "of a different length would need a different measured thrust, not a different sum here."
    )
    private float lengthMm = DEFAULT_LENGTH_MM;

    @AvlEditorField(text="Mass",
        help="Mass of the fan unit and its motor, in the model's mass unit. A 70 mm fan with its motor is\n"
        + "around 200 g; a 90 mm one 250 to 400 g. Counts towards the total mass, the centre of gravity\n"
        + "and the inertias."
    )
    private float mass;

    private Pos pos = new Pos();

    public DuctedFan() {
    }

    @XmlAttribute(name="inner_diameter_mm")
    public float getInnerDiameterMm() {
        return innerDiameterMm;
    }

    public void setInnerDiameterMm(float innerDiameterMm) {
        this.innerDiameterMm = Math.max(0f, innerDiameterMm);
    }

    @XmlAttribute(name="blades")
    public int getBlades() {
        return blades;
    }

    public void setBlades(int blades) {
        this.blades = blades;
    }

    @XmlAttribute(name="length_mm")
    public float getLengthMm() {
        return lengthMm;
    }

    public void setLengthMm(float lengthMm) {
        this.lengthMm = Math.max(MIN_SIZE_MM, lengthMm);
    }

    @XmlAttribute(name="mass")
    public float getMass() {
        return mass;
    }

    public void setMass(float mass) {
        this.mass = Math.max(0f, mass);
    }

    @AvlEditorNode(name="Pos")
    public Pos getPos() {
        return pos;
    }

    public void setPos(Pos pos) {
        this.pos = pos;
    }

    @Override
    public String toString() {
        return "Ducted fan";
    }
}
