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

import com.abajar.avleditor.avl.AVLGeometry;
import com.abajar.avleditor.view.annotations.AvlEditorReadOnly;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 *
 * @author Hugo
 */
public class CenterOfMass {
    private final CRRCSim crrcsim;

    @XmlAttribute
    public final String units = "1";

    @Override
    public String toString() {
        return "Center of Mass";
    }

    public CenterOfMass(){
        this.crrcsim = null;
    }

    public CenterOfMass(CRRCSim crrcsim){
        this.crrcsim = crrcsim;
    }

    private AVLGeometry getGeometry() {
        if (this.crrcsim == null || this.crrcsim.getAvl() == null) {
            return null;
        }
        return this.crrcsim.getAvl().getGeometry();
    }

    @AvlEditorReadOnly(text="X position",
        help="X position of center of masses"
    )
    @XmlAttribute(name="x")
    @XmlJavaTypeAdapter(MetersConversorInverted.class)
    public float getX(){
        AVLGeometry geometry = getGeometry();
        return geometry == null ? 0 : geometry.getXref();
    }

    @AvlEditorReadOnly(text="Y position",
        help="Y position of center of masses"
    )
    @XmlAttribute(name="y")
    @XmlJavaTypeAdapter(MetersConversor.class)
    public float getY(){
        AVLGeometry geometry = getGeometry();
        return geometry == null ? 0 : geometry.getYref();
    }

    @AvlEditorReadOnly(text="Z position",
        help="Z position of center of masses"
    )
    @XmlAttribute(name="z")
    @XmlJavaTypeAdapter(MetersConversorInverted.class)
    public float getZ(){
        AVLGeometry geometry = getGeometry();
        return geometry == null ? 0 : geometry.getZref();
    }
}
