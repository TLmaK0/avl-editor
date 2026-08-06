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
import com.abajar.avleditor.view.annotations.AvlEditorNode;
import java.io.Serializable;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS;
import com.abajar.avleditor.view.annotations.AvlEditor;

/**
 *
 * @author Hugo
 */
@AvlEditor(buttons={ENABLE_BUTTONS.DUPLICATE, ENABLE_BUTTONS.DELETE})
public class Wheel implements Serializable {
    static final long serialVersionUID = 2233264509711545342L;
    
    @Override
    public String toString() {
        return name;
    }

    /**
     * <wheel percent_brake="0.5" caster_angle_rad="0">
     *   <pos x="-2.7" y="0.69999999" z="-0.083300002" />
     *   <spring constant="65" damping="0.25" />
     * </wheel>
     */
    @AvlEditorField(text="Name",
        help="Name of the collision point"
    )
    private String name = "Collision point";

    @AvlEditorField(text="Percent brake",
        help="Percentage of max braking applied initially"
    )
    private float percent_brake = 1f;

    @AvlEditorField(text="Caster angle (rad)",
        help="Rads. The caster angle is specified with respect to the plane body's z-axis, a value of zero means that the wheel is oriented straight ahead (which should be the case for most gears)."
    )
    private float caster_angle_rad = 0;
    private Pos pos = new Pos();
    private Spring spring = new Spring();
    
    /**
     * @return the percent_brake
     */
    @XmlAttribute(name="percent_brake")
    public float getPercent_brake() {
        return percent_brake;
    }

    /**
     * @param percent_brake the percent_brake to set
     */
    public void setPercent_brake(float percent_brake) {
        this.percent_brake = percent_brake;
    }

    /**
     * @return the caster_angle_rad
     */
    @XmlAttribute(name="caster_angle_rad")
    public float getCaster_angle_rad() {
        return caster_angle_rad;
    }

    /**
     * @param caster_angle_rad the caster_angle_rad to set
     */
    public void setCaster_angle_rad(float caster_angle_rad) {
        this.caster_angle_rad = caster_angle_rad;
    }

    /**
     * @return the pos
     */
    @XmlElement
    @AvlEditorNode
    public Pos getPos() {
        return pos;
    }

    /**
     * @param pos the pos to set
     */
    public void setPos(Pos pos) {
        this.pos = pos;
    }

    /**
     * @return the spring
     */
    @XmlElement
    @AvlEditorNode
    public Spring getSpring() {
        return spring;
    }

    /**
     * @param spring the spring to set
     */
    public void setSpring(Spring spring) {
        this.spring = spring;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

}