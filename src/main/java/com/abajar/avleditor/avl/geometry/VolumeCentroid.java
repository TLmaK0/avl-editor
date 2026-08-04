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

/**
 * A volume and where it balances: the sum of the pieces an element is made of, each with its own
 * volume and centroid.
 *
 * At uniform density the centroid is the centre of gravity, which is why one estimate serves both the
 * masses generated from volume and the position a mass starts at. Two of them would drift apart, and
 * a mass would then start somewhere the auto masses do not agree with.
 */
public class VolumeCentroid {

    private static final float EPSILON = 1.0e-8f;

    private float volume;
    private float xMoment;
    private float yMoment;
    private float zMoment;

    public void add(float deltaVolume, float x, float y, float z) {
        volume += deltaVolume;
        xMoment += deltaVolume * x;
        yMoment += deltaVolume * y;
        zMoment += deltaVolume * z;
    }

    /** Adds everything another one holds, as it stands. */
    public void add(VolumeCentroid other) {
        if (other == null || other.volume <= EPSILON) return;
        add(other.volume, other.getX(), other.getY(), other.getZ());
    }

    /** The same volume on the other side of a plane in y: what YDUPLICATE draws. */
    public VolumeCentroid mirroredAcrossY(float planeY) {
        VolumeCentroid mirrored = new VolumeCentroid();
        if (volume <= EPSILON) return mirrored;
        mirrored.add(volume, getX(), 2f * planeY - getY(), getZ());
        return mirrored;
    }

    public boolean isEmpty() {
        return volume <= EPSILON;
    }

    public float getVolume() {
        return volume;
    }

    public float getX() {
        return volume <= EPSILON ? 0f : xMoment / volume;
    }

    public float getY() {
        return volume <= EPSILON ? 0f : yMoment / volume;
    }

    public float getZ() {
        return volume <= EPSILON ? 0f : zMoment / volume;
    }

    public float[] centre() {
        return new float[]{getX(), getY(), getZ()};
    }
}
