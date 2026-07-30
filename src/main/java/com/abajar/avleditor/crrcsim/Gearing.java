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

import com.abajar.avleditor.view.annotations.AvlEditorField;
import java.io.Serializable;
import javax.xml.bind.annotation.XmlAttribute;

/**
 *
 * @author Hugo
 */
public class Gearing implements Serializable{

    @Override
    public String toString() {
        return "Gearing";
    }

    @AvlEditorField(text="Inertia (J)",
        help="Inertia"
    )
    private float J=0;

    @AvlEditorField(text="Gear ratio (i)",
        help="Given omega is the speed of the shaft, i*omega is the speed of the device which is connected to the shaft using this gearing. "
    )
    private float i=1;
    public Gearing() {
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
     * @return the i
     */
    @XmlAttribute
    public float getI() {
        return i;
    }

    /**
     * @param i the i to set
     */
    public void setI(float i) {
        this.i = i;
    }
}