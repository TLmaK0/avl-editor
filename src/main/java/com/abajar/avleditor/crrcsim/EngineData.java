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
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS;
import java.io.Serializable;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

/**
 *
 * @author Hugo
 */
@AvlEditor(buttons={ENABLE_BUTTONS.DELETE})
public class EngineData implements Serializable {

    @Override
    public String toString() {
        return "Data";
    }

    private static final String helpData = "Add 2 engine data at least. Use http://www.ecalc.ch/ to calculate Current, Voltage and rpms (you don't need to touch general settings)."
        + "First row use manufacturer data:"
        + "     Voltage = no-load Current: V"
        + "     Current = no-load Current: A"
        + "     Rpms = KV (w/o torque) * Voltage"
        + "Second row use Motor Optimum Efficiency"
        + "     Current"
        + "     Voltage"
        + "     Rpm"
        + "Third row use Motor Maximum"
        + "     Current"
        + "     Voltage"
        + "     Rpm";

    @AvlEditorField(text="Voltage",
        help=helpData
    )
    private float U_K;

    @AvlEditorField(text="Current",
        help=helpData
    )
    private float I_M;

    @AvlEditorField(text="Rpms",
        help=helpData
    )
    private float rpms;

    public EngineData() {
    }

    /**
     * @return the U_K
     */
    @XmlAttribute(name="U_K")
    public float getU_K() {
        return U_K;
    }

    /**
     * @param U_K the U_K to set
     */
    public void setU_K(float U_K) {
        this.U_K = U_K;
    }

    /**
     * @return the I_M
     */
    @XmlAttribute(name="I_M")
    public float getI_M() {
        return I_M;
    }

    /**
     * @param I_M the I_M to set
     */
    public void setI_M(float I_M) {
        this.I_M = I_M;
    }

    /**
     * @return the n
     */
    @XmlAttribute(name="n")
    public int getN() {
        return Math.round(rpms / 60);
    }


    /**
     * @return the rpms
     */
    @XmlTransient
    public float getRpms() {
        return rpms;
    }

    /**
     * @param rpms the rpms to set
     */
    public void setRpms(float rpms) {
        this.rpms = rpms;
    }


}