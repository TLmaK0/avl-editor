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
public class MassInertia implements Serializable {
    private String version = "1";
    private String units = "1";

    @Override
    public String toString() {
        return "mass";
    }

    @AvlEditorField(text="Mass",
        help="total aireplane mass"
    )
    private float Mass;

    @AvlEditorField(text="Roll inertia (I_xx)",
        help="Moment of Inertia xx"
    )
    private float I_xx;

    @AvlEditorField(text="Pitch inertia (I_yy)",
        help="Moment of Inertia yy"
    )
    private float I_yy;

    @AvlEditorField(text="Yaw inertia (I_zz)",
        help="Moment of Inertia zz"
    )
    private float I_zz;

    @AvlEditorField(text="Roll-yaw product (I_xz)",
        help="Product of Inertia xz"
    )
    private float I_xz;

    public MassInertia() {
    }

    /**
     * @return the version
     */
    @XmlAttribute(name="version")
    public String getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @return the units
     */
    @XmlAttribute(name="units")
    public String getUnits() {
        return units;
    }

    /**
     * @param units the units to set
     */
    public void setUnits(String units) {
        this.units = units;
    }

    /**
     * @return the Mass
     */
     @XmlAttribute(name="Mass")
    public float getMass() {
        return Mass;
    }

    /**
     * @param Mass the Mass to set
     */
    public void setMass(float Mass) {
        this.Mass = Mass;
    }

    /**
     * @return the I_xx
     */
    @XmlAttribute(name="I_xx")
    public float getI_xx() {
        return I_xx;
    }

    /**
     * @param I_xx the I_xx to set
     */
    public void setI_xx(float I_xx) {
        this.I_xx = I_xx;
    }

    /**
     * @return the I_yy
     */
    @XmlAttribute(name="I_yy")
    public float getI_yy() {
        return I_yy;
    }

    /**
     * @param I_yy the I_yy to set
     */
    public void setI_yy(float I_yy) {
        this.I_yy = I_yy;
    }

    /**
     * @return the I_zz
     */
    @XmlAttribute(name="I_zz")
    public float getI_zz() {
        return I_zz;
    }

    /**
     * @param I_zz the I_zz to set
     */
    public void setI_zz(float I_zz) {
        this.I_zz = I_zz;
    }

    /**
     * @return the I_xz
     */
    @XmlAttribute(name="I_xz")
    public float getI_xz() {
        return I_xz;
    }

    /**
     * @param I_xz the I_xz to set
     */
    public void setI_xz(float I_xz) {
        this.I_xz = I_xz;
    }
}