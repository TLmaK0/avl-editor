/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor;

/**
 * The units a model states its own figures in, and the only place that converts between them and the physical
 * ones.
 *
 * A model chooses a length, a mass and a time unit, and every number it holds is in those: a
 * {@link com.abajar.avleditor.avl.mass.Mass} of 0.18 is 0.18 kg in a model stated in kilograms and 0.18 g in
 * one stated in grams. The conversion cannot therefore be left to whoever happens to hold a figure, because
 * some of them cannot see the units at all — which is exactly how the weight derived from materials came to be
 * written in kilograms into a field the rest of the editor read as grams, making the aircraft weigh a
 * thousandth of what it should.
 *
 * So: {@link com.abajar.avleditor.avl.AVL} holds the three names — it is where the user sets them — and hands
 * out this object; everything that needs a conversion asks it rather than reaching for a unit string and a
 * factor of its own. The factors themselves stay in {@link UnitConversor}, so there is still one table of them.
 */
public final class ModelUnits {

    /** A model that has not said otherwise: metres, kilograms, seconds — the same defaults AVL declares. */
    public static final ModelUnits DEFAULTS = new ModelUnits("m", "kg", "s");

    private final String lengthUnit;
    private final String massUnit;
    private final String timeUnit;
    private final UnitConversor conversor = new UnitConversor();

    public ModelUnits(String lengthUnit, String massUnit, String timeUnit) {
        this.lengthUnit = lengthUnit == null ? DEFAULTS.lengthUnit : lengthUnit;
        this.massUnit = massUnit == null ? DEFAULTS.massUnit : massUnit;
        this.timeUnit = timeUnit == null ? DEFAULTS.timeUnit : timeUnit;
    }

    public String lengthUnit() {
        return lengthUnit;
    }

    public String massUnit() {
        return massUnit;
    }

    public String timeUnit() {
        return timeUnit;
    }

    /** How many metres one of the model's length units is: 1 for metres, 0.01 for centimetres. */
    public float metresPerLengthUnit() {
        return conversor.convertToMeters(1f, lengthUnit);
    }

    /** How many kilograms one of the model's mass units is: 1 for kg, 0.001 for grams. */
    public float kilogramsPerMassUnit() {
        return conversor.convertToKilograms(1f, massUnit);
    }

    /** How many seconds one of the model's time units is. */
    public float secondsPerTimeUnit() {
        return conversor.convertToSeconds(1f, timeUnit);
    }

    public float toMetres(float stated) {
        return conversor.convertToMeters(stated, lengthUnit);
    }

    public float toKilograms(float stated) {
        return conversor.convertToKilograms(stated, massUnit);
    }

    public float toKilogramsSquareMetres(float stated) {
        return conversor.convertToKilogramsSquareMeters(stated, massUnit, lengthUnit);
    }

    /** An area stated in the model's length unit squared, in m². The factor is squared with it. */
    public float toSquareMetres(float stated) {
        return conversor.convertToSquareMeters(stated, lengthUnit);
    }

    /**
     * A speed stated in the model's length per the model's time, in m/s.
     *
     * Both units enter it, and in opposite directions. This is what AVL has to be handed: its run-case
     * velocity is in <b>metres per second whatever {@code Lunit} says</b> — measured, not assumed, by
     * flying one aircraft written in metres and again in centimetres and finding that the second matched
     * at the same number rather than at a hundred times it.
     */
    public float toMetresPerSecond(float stated) {
        return conversor.convertToMetersPerSecond(stated, lengthUnit, timeUnit);
    }

    /** A physical length in metres, as the model would state it. */
    public float fromMetres(float metres) {
        float factor = metresPerLengthUnit();
        return factor == 0f ? metres : metres / factor;
    }

    /**
     * A physical weight in kilograms, as the model would state it.
     *
     * This is the one the materials need: their weight is worked out in kilograms from a density in g/cm³, and
     * it has to be written down in whatever unit the model speaks.
     */
    public float fromKilograms(float kilograms) {
        float factor = kilogramsPerMassUnit();
        return factor == 0f ? kilograms : kilograms / factor;
    }

    /** One millimetre in the model's length unit: what a part stated in mm measures on the drawing. */
    public float millimetre() {
        return fromMetres(0.001f);
    }

    /**
     * The unit line AVL's mass file wants, as "&lt;factor&gt; &lt;SI unit&gt;" — the same factor as above,
     * rather than a second table of them written out by hand.
     */
    public String avlLengthUnit() {
        return trim(metresPerLengthUnit()) + " m";
    }

    public String avlMassUnit() {
        return trim(kilogramsPerMassUnit()) + " kg";
    }

    public String avlTimeUnit() {
        return trim(secondsPerTimeUnit()) + " s";
    }

    /** Whole numbers without a decimal point, so the file reads "1 m" and not "1.0 m". */
    private static String trim(float value) {
        if (value == Math.rint(value) && !Float.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    @Override
    public String toString() {
        return lengthUnit + "/" + massUnit + "/" + timeUnit;
    }
}
