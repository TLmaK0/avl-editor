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

    /**
     * How the aircraft is meant to be flown, which is what the flying-qualities criteria are graded against
     * (MIL-F-8785C 1.3.2). See {@code docs/mil-f-8785c.md}.
     *
     * <b>It is the one thing about that judgement the aircraft cannot tell us.</b> Everything else the
     * editor reads off the model — the span sets the frequencies and the times, the derivatives set the
     * rest — but this is the mission and not the machine: the same airframe flown gently is Category B and
     * thrown around is Category A, and Category A wants 0.35 of short-period damping rather than 0.30 and
     * 0.19 of dutch-roll damping rather than 0.08.
     *
     * "Gentle" is the default because it is what most models spend most of their time doing, and because it
     * is the one an old file that has never heard of this field loads as.
     */
    @AvlEditorField(text="How it is flown",
        help="Which MIL-F-8785C Flight Phase Category the flying qualities are judged against.\n"
            + "Gentle (B): climb, cruise, loiter, descent — gradual maneuvers.\n"
            + "Aerobatic (A): rapid maneuvering and precise tracking. Asks noticeably more of the aircraft.\n"
            + "Takeoff and landing (C): the terminal phases.",
        options={"Gentle (cruise)", "Aerobatic", "Takeoff and landing"}
    )
    private String flightPhase = FLIGHT_PHASE_GENTLE;

    public static final String FLIGHT_PHASE_GENTLE = "Gentle (cruise)";
    public static final String FLIGHT_PHASE_AEROBATIC = "Aerobatic";
    public static final String FLIGHT_PHASE_TERMINAL = "Takeoff and landing";

    public String getFlightPhase() {
        return flightPhase == null ? FLIGHT_PHASE_GENTLE : flightPhase;
    }

    public void setFlightPhase(String flightPhase) {
        this.flightPhase = flightPhase;
    }

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

    /** An {@code OutputStream} and not a file, so what goes into the mass file can be read back and checked. */
    void writeAVLMassData(java.io.OutputStream out) {
        PrintStream ps = new PrintStream(out);

        // Straight from the units themselves, rather than a second table of factors written out by hand
        // here — which is how "1.0 s" ended up standing for every time unit and a minute for 36 seconds.
        ModelUnits units = units();
        String lunit = units.avlLengthUnit();
        String munit = units.avlMassUnit();
        String tunit = units.avlTimeUnit();
        
        // g and rho are stated in m, kg, s whatever Lunit/Munit/Tunit say — AVL's own mass-file comment
        // is "must be in the unit names given above (i.e. m,kg,s)", and the units named there are the SI
        // ones on the right of each line. So neither is converted, and g is the same constant the lift
        // coefficient is derived with rather than a second, rounder copy of it.
        ps.print("Lunit = " + lunit + "\n" +
                    "Munit = " + munit + "\n" +
                    "Tunit = " + tunit + "\n" +
                    "g   = " + GRAVITY + "\n" +
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

    /**
     * The inertias the aircraft was analysed with, in kg·m², pushed here by
     * {@link com.abajar.avleditor.crrcsim.CRRCSim#calculate()} beside the weight and for the same reason:
     * they are derived from one list of masses, and an analysis run against inertias the model no longer
     * has describes a different aeroplane.
     *
     * They are here rather than fetched from {@code Config} because {@code AVL} is what
     * {@link com.abajar.avleditor.avl.connectivity.AvlRunner} is handed, and a run has to be able to record
     * what it was flown with. Transient, like the weight and every other derived link.
     */
    private transient float analysisIxx;
    private transient float analysisIzz;
    private transient float analysisIxz;

    public void setAnalysisInertias(float ixx, float izz, float ixz) {
        this.analysisIxx = ixx;
        this.analysisIzz = izz;
        this.analysisIxz = ixz;
    }

    public float getAnalysisIxx() {
        return analysisIxx;
    }

    public float getAnalysisIzz() {
        return analysisIzz;
    }

    public float getAnalysisIxz() {
        return analysisIxz;
    }

    public float getAnalysisWeightKg() {
        return analysisWeightKg;
    }

    /**
     * The speed the aircraft is analysed at, in <b>metres per second</b>, whatever the model states its
     * lengths and times in.
     *
     * This is the number AVL is handed, and it is the one place that converts it. AVL's run-case velocity
     * is in m/s <b>regardless of the {@code Lunit} declared in the mass file</b> — that was established by
     * asking it: the same aeroplane written in metres and again in centimetres returns identical
     * eigenvalues at the same velocity, and eigenvalues a hundred times faster at a hundred times it. The
     * geometry is what {@code Lunit} converts; the run case is stated in SI beside {@code g} and
     * {@code rho}, exactly as AVL's own mass-file comment says ("must be in the unit names given above,
     * i.e. m, kg, s").
     *
     * Handing AVL the raw field is what this replaces, and it was silent: a model stated in centimetres was
     * flown a hundred times too fast, and every derivative, mode and deflection came from that aeroplane.
     * A model in metres and seconds — every check in this project, and every sample — was unaffected, which
     * is why it survived.
     *
     * @return 0 when there is no speed to convert, so the callers' "not yet" test stays one comparison.
     */
    public float analysisVelocityMetresPerSecond() {
        return velocity <= 0f ? 0f : units().toMetresPerSecond(velocity);
    }

    /** The reference area in m², whatever the model states its lengths in. An area, so the factor squares. */
    public float analysisReferenceAreaSquareMetres() {
        return this.getGeometry() == null ? 0f : units().toSquareMetres(this.getGeometry().getSref());
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
     * <b>Every term of it is in SI</b>, and that is not decoration. The weight is in newtons and the
     * density in kg/m³ — neither follows the model's units — so the speed and the area must not either.
     * They used to: the field and {@code Sref} went in as the user wrote them, so the coefficient the whole
     * analysis is measured at was out by 10⁴ for a model stated in centimetres and by 2.4 x 10⁵ for one in
     * ounces and inches. The same family as the four {@code ExportUnitsCheck} found, and the same fix: one
     * conversion, in the one place that needs it.
     *
     * @return null when it cannot be derived — no weight yet, no reference area, no speed, no air — rather
     *         than a number that would look like an answer. The requirements refuse before the analysis
     *         reaches this state; the null is what makes a mistake loud instead of silent.
     */
    public Float analysisLiftCoefficient() {
        float sref = analysisReferenceAreaSquareMetres();
        float speed = analysisVelocityMetresPerSecond();
        if (analysisWeightKg <= 0f || sref <= 0f || speed <= 0f || getAirDensity() <= 0f) return null;

        double liftPerCl = 0.5 * getAirDensity() * speed * speed * sref;
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
        // Both the speed the user wrote and the speed it is: a message that names a unit has to name the
        // right one, and this is the line that would have said "14 m/s" for a model stated in cm/s.
        return String.format(java.util.Locale.ENGLISH,
            "CL = %.3f for %.3f kg at %.2f m/s, stated as %s %s/%s",
            cl, analysisWeightKg, analysisVelocityMetresPerSecond(),
            String.valueOf(velocity), units().lengthUnit(), units().timeUnit());
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
