/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.avl.geometry;

import com.abajar.avleditor.view.annotations.AvlEditorField;
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS;
import com.abajar.avleditor.view.annotations.AvlEditor;
import java.io.Serializable;

/**
 * Represents a point in the body profile.
 * The profile is defined by a series of (x, radius) pairs where:
 * - x is the position along the body (0.0 = nose, 1.0 = tail)
 * - radius is the body radius at that position
 *
 * @author hfreire
 */
@AvlEditor(buttons={ENABLE_BUTTONS.DUPLICATE, ENABLE_BUTTONS.DELETE})
public class BodyProfilePoint implements Serializable {
    static final long serialVersionUID = 1L;

    // Reference to parent body (transient, not serialized)
    private transient Body parentBody;

    @AvlEditorField(text="X",
        help="Position along body (0.0 = nose, 1.0 = tail)")
    private float x;

    @AvlEditorField(text="Radius",
        help="Body radius at this position")
    private float radius;

    public BodyProfilePoint() {
        this.x = 0.5f;
        this.radius = 0.1f;
    }

    public BodyProfilePoint(float x, float radius) {
        this.x = x;
        this.radius = radius;
    }

    /**
     * @return the x position
     */
    public float getX() {
        return x;
    }

    /**
     * @param x the x position to set
     */
    public void setX(float x) {
        this.x = x;
    }

    /**
     * @return the radius
     */
    public float getRadius() {
        return radius;
    }

    /**
     * @param radius the radius to set
     */
    public void setRadius(float radius) {
        this.radius = radius;
    }

    /**
     * @return the parent body
     */
    public Body getParentBody() {
        return parentBody;
    }

    /**
     * @param parentBody the parent body to set
     */
    public void setParentBody(Body parentBody) {
        this.parentBody = parentBody;
    }

    @Override
    public String toString() {
        return String.format("x=%.2f, r=%.2f", x, radius);
    }
}
