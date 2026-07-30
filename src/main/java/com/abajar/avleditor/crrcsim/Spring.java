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
public class Spring implements Serializable {

    @Override
    public String toString() {
        return "Spring";
    }

    @AvlEditorField(text="Spring constant",
        help="Spring constant, has to be positive.\r\n"
    )
    private float constant=1;

    @AvlEditorField(text="Damping",
        help="damping, has to be positive.\r\n"
    )
    private float damping;

    @AvlEditorField(text="Max force",
        help="The flight dynamics model calculates the forces on "
        + " each hardpoint resulting from interaction with the ground"
        + " or solid objects. If this value exceeds the specified max_force,"
        + " the plane will be considered as crashed."
        + " The max_force attribute is optional; if it is not specified,"
        + " it will internally be set to a very high default value so"
        + " that this hardpoint will only cause a crash on insanely high load.\r\n"
    )
    private float max_force;

    /**
     * @return the constant
     */
    @XmlAttribute
    public float getConstant() {
        return constant;
    }

    /**
     * @param constant the constant to set
     */
    public void setConstant(float constant) {
        this.constant = constant;
    }

    /**
     * @return the damping
     */
    @XmlAttribute
    public float getDamping() {
        return damping;
    }

    /**
     * @param damping the damping to set
     */
    public void setDamping(float damping) {
        this.damping = damping;
    }

    /**
     * @return the max_force
     */
    @XmlAttribute(name="max_force")
    public float getMax_force() {
        return max_force;
    }

    /**
     * @param max_force the max_force to set
     */
    public void setMax_force(float max_force) {
        this.max_force = max_force;
    }
}