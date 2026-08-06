/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.avl;

import com.abajar.avleditor.ModelUnits;
import com.abajar.avleditor.UnitConversor;
import com.abajar.avleditor.avl.runcase.AvlCalculation;
import com.abajar.avleditor.view.annotations.AvlEditorField;
import com.abajar.avleditor.view.annotations.AvlEditorNode;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.Serializable;

/**
 *
 * @author hfreire
 */
public class AVL implements Serializable{
    static final long serialVersionUID = 791092777497735586L;
    public static final float DEFAULT_REYNOLDS_NUMBER = 1.225f;
    public static final String DEFAULT_LENGTH_UNIT = "m";
    public static final String DEFAULT_MASS_UNIT = "kg";
    public static final String DEFAULT_TIME_UNIT = "s";
    public static final float DEFAULT_VELOCITY = 30; // 30m/s

    private AVLGeometry geometry = new AVLGeometry();

    @AvlEditorField(text="Length unit (default m)",
        help="Choose cm, m, in"
    )
    private String lengthUnit = DEFAULT_LENGTH_UNIT;

    @AvlEditorField(text="Mass unit (default kg)",
        help="Choose g, kg, oz"
    )
    private String massUnit = DEFAULT_MASS_UNIT;

    @AvlEditorField(text="Time unit (default s)",
        help="Choose s, m, h"
    )
    private String timeUnit = DEFAULT_TIME_UNIT;

    /**
     * Air density, in kg/m³. The field is called `reynoldsNumber` because that is what the files written
     * before anyone read the writer call it, and renaming it would lose the value in every saved model —
     * but it is the density: it goes into the AVL mass file as `rho`, and 1.225 is air at sea level, not
     * a Reynolds number (which would be around 10^5 for a model wing).
     */
    @AvlEditorField(text="Air density (rho, kg/m3)",
        help="Density of the air the analysis runs in, in kg/m3: 1.225 at sea level.\n"
            + "AVL takes it as 'rho' in the mass file, and with the speed it decides what lift the\n"
            + "wing makes."
    )
    private float reynoldsNumber = DEFAULT_REYNOLDS_NUMBER;

    /**
     * The lift coefficient AVL is asked to trim at. <b>Not editable, and no longer a stated figure.</b>
     *
     * It used to be a field the user typed, defaulting to 0 — and a default is exactly what it stayed in
     * every model nobody thought to change it in, the sample included, which holds {@code alpha: 0.0}: an
     * aircraft whose wings carry nothing, nose down, with the trim, the deflections and the modes all
     * measured there. An invented input, which is the one thing this project refuses.
     *
     * In level flight the lift equals the weight, so the coefficient is not a choice at all — it follows
     * from the weight, the speed, the air density and the reference area. {@link
     * #analysisLiftCoefficient()} is the single place that works it out, and the analysis takes it from
     * there. The field survives so that a model that saved one still loads, and nothing else reads it.
     */
    private float alpha = 0;

    @AvlEditorField(text="Velocity",
        help="Simulation velocity in m/s"
    )
    private float velocity = DEFAULT_VELOCITY;

    // Transient: not saved to file, only available during session
    private transient AvlCalculation lastCalculation;

    /**
     * @return the geometry
     */
    @AvlEditorNode
    public AVLGeometry getGeometry() {
        // The geometry has to be able to reach the units — the weight from materials is worked out in
        // kilograms and has to be written down in the model's own unit — and this is the one place every
        // caller already goes through. Live rather than pushed once: a cached copy is how a unit the user
        // changed afterwards goes stale.
        if (geometry != null) geometry.setUnitsSource(this);
        return geometry;
    }

    /**
     * @param geometry the geometry to set
     */
    public void setGeometry(AVLGeometry geometry) {
        this.geometry = geometry;
        if (geometry != null) geometry.setUnitsSource(this);
    }

    /**
     * The units this model states its figures in. The single source: the three fields above are where the user
     * sets them, so everything that converts asks here rather than keeping a unit string of its own.
     */
    public ModelUnits units() {
        return new ModelUnits(lengthUnit, massUnit, timeUnit);
    }

    public int getAileronPosition() throws Exception{
        return this.geometry.getAileronPosition();
    }

    public int getElevatorPosition() throws Exception{
        return this.geometry.getElevatorPosition();
    }

    public int getRudderPosition() throws Exception{
        return this.geometry.getRudderPosition();
    }

    @Override
    public String toString() {
        return "AVL";
    }

    /**
     * @return the lengthUnit
     */
    public String getLengthUnit() {
        return lengthUnit;
    }

    /**
     * @param lengthUnit the lengthUnit to set
     */
    public void setLengthUnit(String lengthUnit) {
        this.lengthUnit = lengthUnit;
    }

    /**
     * @return the massUnit
     */
    public String getMassUnit() {
        return massUnit;
    }

    /**
     * @param massUnit the massUnit to set
     */
    public void setMassUnit(String massUnit) {
        this.massUnit = massUnit;
    }

    /**
     * @return the timeUnit
     */
    public String getTimeUnit() {
        return timeUnit;
    }

    /**
     * @param timeUnit the timeUnit to set
     */
    public void setTimeUnit(String timeUnit) {
        this.timeUnit = timeUnit;
    }

    void writeAVLMassData(FileOutputStream fos) {
        PrintStream ps = new PrintStream(fos);

        // Straight from the units themselves, rather than a second table of factors written out by hand
        // here — which is how "1.0 s" ended up standing for every time unit and a minute for 36 seconds.
        ModelUnits units = units();
        String lunit = units.avlLengthUnit();
        String munit = units.avlMassUnit();
        String tunit = units.avlTimeUnit();
        
        ps.print("Lunit = " + lunit + "\n" +
                    "Munit = " + munit + "\n" +
                    "Tunit = " + tunit + "\n" +
                    "g   = 9.81\n" +
                    "rho = " + this.getReynoldsNumber() + "\n");
        ps.print("#mass     x       y        z        Ixx      Iyy      Izz\n");

        this.getGeometry().writeAVLMassData(ps);
    }

    /** Standard gravity, for turning a mass into the weight the wings have to hold. */
    public static final double GRAVITY = 9.80665;

    /**
     * What the model weighs, in kg. Transient and pushed in from the masses themselves — the analysis
     * needs the number, and the number lives with the masses, not here. {@link
     * com.abajar.avleditor.crrcsim.CRRCSim#getAnalysisWeightKg()} is the one that adds them up, and it is
     * pushed from {@code calculate()}, the same funnel that derives the mass, the inertias and the centre
     * of gravity, so an analysis cannot run on a stale weight.
     */
    private transient float analysisWeightKg;

    public void setAnalysisWeightKg(float analysisWeightKg) {
        this.analysisWeightKg = analysisWeightKg;
    }

    public float getAnalysisWeightKg() {
        return analysisWeightKg;
    }

    /**
     * The lift coefficient the analysis runs at: the one this aircraft needs to hold its own weight at the
     * stated speed. In level flight the lift equals the weight, so
     *
     * <pre>  CL = W / (rho/2 V^2 Sref)  </pre>
     *
     * This is the <b>only</b> place that works it out. Everything that asks AVL for an operating point —
     * the stability run, the eigenvalue pass, the plots — comes here, so they cannot end up analysing
     * different aircraft.
     *
     * @return null when it cannot be derived — no weight yet, no reference area, no speed, no air — rather
     *         than a number that would look like an answer. The requirements refuse before the analysis
     *         reaches this state; the null is what makes a mistake loud instead of silent.
     */
    public Float analysisLiftCoefficient() {
        float sref = this.getGeometry() == null ? 0f : this.getGeometry().getSref();
        if (analysisWeightKg <= 0f || sref <= 0f || velocity <= 0f || getAirDensity() <= 0f) return null;

        double liftPerCl = 0.5 * getAirDensity() * velocity * velocity * sref;
        return (float) (analysisWeightKg * GRAVITY / liftPerCl);
    }

    /**
     * The operating point in words, for the log.
     *
     * Deliberately <b>not</b> a row in the properties table. The coefficient is not a setting and not an
     * answer — it is how the analysis expresses the speed the user already chose, one line above it — so
     * showing it would only invite the reader to configure something that configures itself. It goes to the
     * log, where it belongs when a run has to be explained after the fact.
     */
    public String describeAnalysisPoint() {
        Float cl = analysisLiftCoefficient();
        if (cl == null) return "needs a weight, a speed, an air density and a reference area";
        return String.format(java.util.Locale.ENGLISH,
            "CL = %.3f for %.3f kg at %.1f m/s", cl, analysisWeightKg, velocity);
    }

    /** Air density in kg/m³ — the meaningful name for what {@link #getReynoldsNumber()} holds. */
    public float getAirDensity() {
        return reynoldsNumber;
    }

    /**
     * @return the reynoldsNumber
     */
    public float getReynoldsNumber() {
        return reynoldsNumber;
    }

    /**
     * @param reynoldsNumber the reynoldsNumber to set
     */
    public void setReynoldsNumber(float reynoldsNumber) {
        this.reynoldsNumber = reynoldsNumber;
    }

    /**
     * @return the alpha
     */
    public float getLiftCoefficient() {
        return alpha;
    }

    /**
     * @param alpha the alpha to set
     */
    public void setLiftCoefficient(float alpha) {
        this.alpha = alpha;
    }

    /**
     * @return the velocity
     */
    public float getVelocity() {
        return velocity;
    }

    /**
     * @param velocity the velocity to set
     */
    public void setVelocity(float velocity) {
        this.velocity = velocity;
    }

    /**
     * @return the last AVL calculation results (null if not yet calculated)
     */
    @AvlEditorNode(name = "Last Results")
    public AvlCalculation getLastCalculation() {
        return lastCalculation;
    }

    /**
     * @param calculation the AVL calculation to store
     */
    public void setLastCalculation(AvlCalculation calculation) {
        this.lastCalculation = calculation;
    }

}
