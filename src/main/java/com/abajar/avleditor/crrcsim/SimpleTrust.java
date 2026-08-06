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
@AvlEditor(buttons={ENABLE_BUTTONS.DUPLICATE, ENABLE_BUTTONS.DELETE})
public class SimpleTrust implements Serializable{

    @Override
    public String toString() {
        return "SimpleTrust";
    }

    @AvlEditorField(text="Thrust constant (k_F)",
        help="Trust = k_F * omega_p\n"
        + "omega_p = i * omega\n"
        + "omega is the speed of the shaft\n"
        + "i * omega is the speed of the device connected to the shaft using the gearing"
    )
    private float k_F;

    @AvlEditorField(text="Torque constant (k_M)",
        help="Torque = -1 * k_M * omega_p * i\n"
        + "omega_p = i * omega\n"
        + "omega is the speed of the shaft\n"
        + "i * omega is the speed of the device connected to the shaft using the gearing"
    )
    private float k_M;

    private Gearing gearing = new Gearing();
    
    /**
     * @return the k_F
     */
    @XmlAttribute(name="k_F")
    public float getK_F() {
        return k_F;
    }

    /**
     * @param k_F the k_F to set
     */
    public void setK_F(float k_F) {
        this.k_F = k_F;
    }

    /**
     * @return the k_M
     */
    @XmlAttribute(name="k_M")
    public float getK_M() {
        return k_M;
    }

    /**
     * @param k_M the k_M to set
     */
    public void setK_M(float k_M) {
        this.k_M = k_M;
    }

    /**
     * @return the gearing
     */
    @AvlEditorNode
    @XmlElement
    public Gearing getGearing() {
        return gearing;
    }

    /**
     * @param gearing the gearing to set
     */
    public void setGearing(Gearing gearing) {
        this.gearing = gearing;
    }


}