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
import javax.xml.bind.annotation.XmlElement;

/**
 *
 * @author Hugo
 */
@AvlEditor(buttons={ENABLE_BUTTONS.DELETE})
public class Propeller implements Serializable {

    @Override
    public String toString() {
        return "Propeller";
    }

    @AvlEditorField(text="Diameter (D)",
        help="Diameter in meters"
    )
    private float D;

    @AvlEditorField(text="Pitch (H)",
        help="Pitch in meters"
    )
    private float H;

    @AvlEditorField(text="Inertia (J)",
        help="Inertia"
    )
    private float J;

    @AvlEditorField(text="Folding threshold (n_fold)",
        help="The Propeller can be configured to be a folding prop, which folds as soon as it rotates slower than omega_fold."
        + " From the xml config, n_fold is read and converted using (omega_fold = n_fold * 2 * pi)"
    )
    private int n_fold;

    @AvlEditorField(text="Number of blades",
        help="Blade count of the propeller. Used by the JSBSim export, which models a fixed-pitch"
        + " propeller driven by the motor. Two is the usual RC configuration and the initial value;"
        + " it is shown here so it can be corrected rather than assumed."
    )
    private int blades = 2;


    @AvlEditorField(text="Mass",
        help="Mass of the propeller, spinner included, in the model's mass unit. Counts towards the total mass, the"
        + " centre of gravity and the inertias. Leave it at zero if it is accounted for elsewhere."
    )
    private float mass;

    private Pos pos = new Pos();

    public Propeller() {
    }

    /**
     * @return the blade count
     */
    @XmlAttribute(name="blades")
    public int getBlades() {
        return blades;
    }

    /**
     * @param blades the blade count to set
     */
    public void setBlades(int blades) {
        this.blades = blades;
    }

    /**
     * @return the D
     */
    @XmlAttribute
    public float getD() {
        return D;
    }

    /**
     * @param D the D to set
     */
    public void setD(float D) {
        this.D = D;
    }

    /**
     * @return the H
     */
    @XmlAttribute
    public float getH() {
        return H;
    }

    /**
     * @param H the H to set
     */
    public void setH(float H) {
        this.H = H;
    }

    /**
     * @return the J
     */
    @XmlAttribute
    public float getJ() {
        return J;
    }

    /**
     * @param J the J to set
     */
    public void setJ(float J) {
        this.J = J;
    }

    /**
     * @return the n_fold
     */
    @XmlAttribute(name="n_fold")
    public int getN_fold() {
        return n_fold;
    }

    /**
     * @param n_fold the n_fold to set
     */
    public void setN_fold(int n_fold) {
        this.n_fold = n_fold;
    }

    /**
     * @return the mass of the propeller
     */
    @XmlAttribute
    public float getMass() {
        return mass;
    }

    /**
     * @param mass the mass of the propeller to set
     */
    public void setMass(float mass) {
        this.mass = mass;
    }

    /**
     * @return where the propeller sits
     */
    @AvlEditorNode(name="Position")
    @XmlElement(name="pos")
    public Pos getPos() {
        return pos;
    }

    /**
     * @param pos where the propeller sits
     */
    public void setPos(Pos pos) {
        this.pos = pos;
    }
}
