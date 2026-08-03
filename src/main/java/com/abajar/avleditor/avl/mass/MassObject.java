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

    /**
     * A new mass starts at the middle of this element rather than at the origin: weighing a wing
     * and having its mass appear in the middle of the wing is the useful default, and the origin
     * is the nose, which is wrong for everything except a nose-mounted item.
     *
     * The position is only a starting point. It belongs to the mass from then on and can be changed
     * either in the properties table or by dragging the mass in the 3D view; nothing recomputes it,
     * so moving it sticks.
     */
    public Mass createMass() {
        Mass mass = new Mass();
        float[] centre = geometricCenter();
        mass.setX(centre[0]);
        mass.setY(centre[1]);
        mass.setZ(centre[2]);
        this.getMasses().add(mass);
        return mass;
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