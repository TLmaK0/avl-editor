/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.avl.runcase;

import com.abajar.avleditor.view.annotations.AvlEditorReadOnly;

/**
 *
 * @author hfreire
 */
public class Configuration {
    private float Sref;
    private float Cref;
    private float Bref;
    private float Velocity;
    private float Alpha;
    private float Cmtot;
    private float CLtot;
    private float CDvis;
    private float Clb;
    private Float e;

    /**
     * How many metres one of the model's length units is, so that {@link #getSpanMetres()} can answer in
     * physical units without the reference span stopping being displayed in the units the user works in.
     *
     * It is a stored factor rather than a link back to the model, and it is allowed to be one here for a
     * reason the rest of the editor's units rule does not cover: a {@code Configuration} is the record of
     * one AVL run, not a live part of the model. The factor is written at the same moment as {@code Bref},
     * from the same run, so the two cannot disagree with each other however the user edits the model
     * afterwards — and a stale pair is exactly what would be wrong here.
     *
     * One metre per unit is what a model that has not said otherwise states, the same default as
     * {@link com.abajar.avleditor.ModelUnits#DEFAULTS}.
     */
    private float metresPerLengthUnit = 1f;

    public void setMetresPerLengthUnit(float metresPerLengthUnit) {
        this.metresPerLengthUnit = metresPerLengthUnit;
    }

    public float getMetresPerLengthUnit() {
        return metresPerLengthUnit;
    }

    /**
     * The reference span in metres, whatever the model states its lengths in.
     *
     * This is what the flying-qualities criteria need: MIL-F-8785C's frequencies and times depend on how big
     * the aircraft is, and an aircraft's size is a physical quantity rather than a number in the user's
     * chosen unit. See {@code MilF8785cEvaluator.FroudeScale}.
     */
    public float getSpanMetres() {
        return Bref * metresPerLengthUnit;
    }

    /** Written beside {@link #metresPerLengthUnit}, and for the same reason. */
    private float secondsPerTimeUnit = 1f;

    public void setSecondsPerTimeUnit(float secondsPerTimeUnit) {
        this.secondsPerTimeUnit = secondsPerTimeUnit;
    }

    public float getSecondsPerTimeUnit() {
        return secondsPerTimeUnit;
    }

    /**
     * The speed in metres per second, whatever the model states its lengths and times in.
     *
     * Roll performance is a rate, so both units enter it: {@code 2V/b} is a frequency, and it is only in
     * radians per second if V and b are in the same length unit and V's time unit is the second.
     */
    public float getVelocityMetresPerSecond() {
        return secondsPerTimeUnit == 0f ? Velocity * metresPerLengthUnit
                                        : Velocity * metresPerLengthUnit / secondsPerTimeUnit;
    }

//Cmtot
//Cma
//Cmq
//Cmd2

    /**
     * @return the Sref
     */
    @AvlEditorReadOnly(text = "Sref", help = "Reference area")
    public float getSref() {
        return Sref;
    }

    /**
     * @param Sref the Sref to set
     */
    public void setSref(float Sref) {
        this.Sref = Sref;
    }

    /**
     * @return the Cref
     */
    @AvlEditorReadOnly(text = "Cref", help = "Reference chord")
    public float getCref() {
        return Cref;
    }

    /**
     * @param Cref the Cref to set
     */
    public void setCref(float Cref) {
        this.Cref = Cref;
    }

    /**
     * @return the Bref
     */
    @AvlEditorReadOnly(text = "Bref", help = "Reference span")
    public float getBref() {
        return Bref;
    }

    /**
     * @param Bref the Bref to set
     */
    public void setBref(float Bref) {
        this.Bref = Bref;
    }

    /**
     * @return the Velocity
     */
    @AvlEditorReadOnly(text = "Velocity", help = "Reference velocity")
    public float getVelocity() {
        return Velocity;
    }

    /**
     * @param Velocity the Velocity to set
     */
    public void setVelocity(float Velocity) {
        this.Velocity = Velocity;
    }

    /**
     * @return the Alpha
     */
    @AvlEditorReadOnly(text = "Alpha", help = "Angle of attack")
    public float getAlpha() {
        return Alpha;
    }

    /**
     * @param Alpha the Alpha to set
     */
    public void setAlpha(float Alpha) {
        this.Alpha = Alpha;
    }

    /**
     * @return the Cmtot
     */
    @AvlEditorReadOnly(text = "Cmtot", help = "Total pitching moment coefficient")
    public float getCmtot() {
        return Cmtot;
    }

    /**
     * @param Cmtot the Cmtot to set
     */
    public void setCmtot(float Cmtot) {
        this.Cmtot = Cmtot;
    }

    /**
     * @return the CLtot
     */
    @AvlEditorReadOnly(text = "CLtot", help = "Total lift coefficient")
    public float getCLtot() {
        return CLtot;
    }

    /**
     * @param CLtot the CLtot to set
     */
    public void setCLtot(float CLtot) {
        this.CLtot = CLtot;
    }

    /**
     * @return the CDvis
     */
    @AvlEditorReadOnly(text = "CDvis", help = "Viscous drag coefficient")
    public float getCDvis() {
        return CDvis;
    }

    /**
     * @param CDvis the CDvis to set
     */
    public void setCDvis(float CDvis) {
        this.CDvis = CDvis;
    }

    /**
     * @return the Clb
     */
    @AvlEditorReadOnly(text = "Clb", help = "Roll moment due to sideslip")
    public float getClb() {
        return Clb;
    }

    /**
     * @param Clb the Clb to set
     */
    public void setClb(float Clb) {
        this.Clb = Clb;
    }

    public void setE(Float e) {
        this.e = e;
    }

    /**
     * @return the e
     */
    @AvlEditorReadOnly(text = "e (efficiency)", help = "Oswald efficiency factor")
    public Float getE() {
        return e;
    }

    @Override
    public String toString() {
        return "Configuration";
    }
}