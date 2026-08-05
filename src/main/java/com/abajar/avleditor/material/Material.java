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
 * A material is either something a part is filled with, weighed by volume — balsa, foam, a solid print
 * — or a skin, weighed by the area it covers: a 0.2 mm carbon laminate weighs about 310 g per square
 * metre whatever it is wrapped around, so its thickness reaches the aircraft through the area, not
 * through the volume. A material may state both, and one of the two is zero for most of them.
 *
 * `notes` says where the figure comes from, so it can be judged rather than trusted.
 */
public class Material implements Serializable {
    static final long serialVersionUID = 1L;

    private String name = "new material";

    /** Weight of the material itself, in grams per cubic centimetre. */
    private float density;

    /** Weight of one covering of the material, in grams per square metre. */
    private float arealWeight;

    private String notes = "";

    public Material() {
    }

    public Material(String name, float density, float arealWeight, String notes) {
        this.name = name;
        this.density = density;
        this.arealWeight = arealWeight;
        this.notes = notes;
    }

    /** A material weighed by volume. */
    public static Material solid(String name, float density, String notes) {
        return new Material(name, density, 0f, notes);
    }

    /** A material weighed by the area it covers, from a thickness of a material of known density. */
    public static Material skin(String name, float thicknessMm, float density, String notes) {
        // 1 mm of a material at 1 g/cm3 weighs 1000 g/m2: 1 m2 x 1 mm = 1000 cm3.
        return new Material(name, 0f, thicknessMm * density * 1000f, notes);
    }

    public boolean isSkin() {
        return arealWeight > 0f;
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

    public float getArealWeight() {
        return arealWeight;
    }

    public void setArealWeight(float arealWeight) {
        this.arealWeight = arealWeight;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
