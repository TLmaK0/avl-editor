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
 * A fuel tank, under Power alongside the batteries. A combustion engine needs one: unlike a
 * battery, its contents are mass that leaves the aircraft as it burns, so the position matters —
 * a tank far from the CG shifts the balance in flight.
 *
 * @author Hugo
 */
@AvlEditor(buttons={ENABLE_BUTTONS.DELETE})
public class FuelTank implements Serializable {
    static final long serialVersionUID = 7716330419900158733L;

    @Override
    public String toString() {
        return "Fuel tank";
    }

    @AvlEditorField(text="Capacity (kg)",
        help="Fuel mass the tank holds when full, in kilograms. Model glow fuel is about"
        + " 0.9 kg per litre, petrol about 0.75."
    )
    private float capacity;

    @AvlEditorField(text="Contents (kg)",
        help="Fuel mass at the start of the simulation, in kilograms. Cannot exceed the capacity."
    )
    private float contents;

    private Pos pos = new Pos();

    public FuelTank() {
    }

    /**
     * @return the capacity in kg
     */
    @XmlAttribute
    public float getCapacity() {
        return capacity;
    }

    /**
     * @param capacity the capacity in kg to set
     */
    public void setCapacity(float capacity) {
        this.capacity = capacity;
    }

    /**
     * @return the initial contents in kg
     */
    @XmlAttribute
    public float getContents() {
        return contents;
    }

    /**
     * @param contents the initial contents in kg to set
     */
    public void setContents(float contents) {
        this.contents = contents;
    }

    /**
     * @return the position of the tank
     */
    @AvlEditorNode(name="Position")
    @XmlElement(name="pos")
    public Pos getPos() {
        return pos;
    }

    /**
     * @param pos the position of the tank to set
     */
    public void setPos(Pos pos) {
        this.pos = pos;
    }
}
