/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.material;

import java.io.Serializable;

/**
 * What a part is made of, as the library holds it.
 *
 * Every material states a **density**, because that is what a material is. A material meant as a skin
 * states a **thickness** as well, and its weight per square metre follows from the two: 0.2 mm of a
 * 1.55 g/cm³ laminate is 310 g/m². That is derived rather than stored, so a skin cannot say one thing
 * with its density and thickness and another with its weight.
 *
 * `notes` says where the figure comes from, so it can be judged rather than trusted.
 */
public class Material implements Serializable {
    static final long serialVersionUID = 1L;

    private String name = "new material";

    /** Weight of the material itself, in grams per cubic centimetre. */
    private float density;

    /** How thick a covering of it is, in millimetres. Zero for a material that is not used as a skin. */
    private float thicknessMm;

    private String notes = "";

    public Material() {
    }

    public Material(String name, float density, float thicknessMm, String notes) {
        this.name = name;
        this.density = density;
        this.thicknessMm = thicknessMm;
        this.notes = notes;
    }

    /** A material with no thickness of its own: what a part is filled with. */
    public static Material solid(String name, float density, String notes) {
        return new Material(name, density, 0f, notes);
    }

    /** A material used as a covering, of a stated thickness. */
    public static Material skin(String name, float thicknessMm, float density, String notes) {
        return new Material(name, density, thicknessMm, notes);
    }

    /** A skin is a material with a thickness; without one there is no area to spread it over. */
    public boolean isSkin() {
        return thicknessMm > 0f && density > 0f;
    }

    /**
     * What one covering of this material weighs per square metre, from its density and thickness:
     * a millimetre of a 1 g/cm³ material over a square metre is 1000 g, since 1 m² x 1 mm = 1000 cm³.
     */
    public float arealWeight() {
        return thicknessMm * density * 1000f;
    }

    @Override
    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getDensity() {
        return density;
    }

    public void setDensity(float density) {
        this.density = density;
    }

    public float getThicknessMm() {
        return thicknessMm;
    }

    public void setThicknessMm(float thicknessMm) {
        this.thicknessMm = Math.max(0f, thicknessMm);
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
