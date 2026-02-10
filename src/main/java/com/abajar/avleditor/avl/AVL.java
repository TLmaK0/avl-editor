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
    public static final String DEFAULT_LENGTH_UNIT = "cm";
    public static final String DEFAULT_MASS_UNIT = "g";
    public static final String DEFAULT_TIME_UNIT = "s";
    public static final float DEFAULT_VELOCITY = 30; // 30m/s

    private AVLGeometry geometry = new AVLGeometry();

    @AvlEditorField(text="Length unit (default cm)",
        help="Choose cm, m, in"
    )
    private String lengthUnit = DEFAULT_LENGTH_UNIT;

    @AvlEditorField(text="Mass unit (default g)",
        help="Choose g, kg, oz"
    )
    private String massUnit = DEFAULT_MASS_UNIT;

    @AvlEditorField(text="Time unit (default s)",
        help="Choose s, m, h"
    )
    private String timeUnit = DEFAULT_TIME_UNIT;

    @AvlEditorField(text="Reynolds number",
        help="http://en.wikipedia.org/wiki/Reynolds_number"
    )
    private float reynoldsNumber = DEFAULT_REYNOLDS_NUMBER;

    @AvlEditorField(text="Lift coefficient",
        help="Lift coefficient used to calculate stability derivatives for the simulation"
    )
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
        return geometry;
    }

    /**
     * @param geometry the geometry to set
     */
    public void setGeometry(AVLGeometry geometry) {
        this.geometry = geometry;
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

        String lunit = "0.01 m";
        if(this.getLengthUnit().equals("m")) lunit = "1 m";
        else if(this.getLengthUnit().equals("in")) lunit = UnitConversor.INCHES_TO_METERS + " m";

        String munit = "0.001 kg";
        if(this.getMassUnit().equals("kg")) munit = "1 kg";
        else if(this.getMassUnit().equals("oz")) munit = UnitConversor.OUNCES_TO_KILOGRAMS + " kg";

        String tunit = "1.0 s";
        if(this.getTimeUnit().equals("h")) tunit = "3600 s";
        else if(this.getTimeUnit().equals("m")) tunit = "60 s";
        
        ps.print("Lunit = " + lunit + "\n" +
                    "Munit = " + munit + "\n" +
                    "Tunit = " + tunit + "\n" +
                    "g   = 9.81\n" +
                    "rho = " + this.getReynoldsNumber() + "\n");
        ps.print("#mass     x       y        z        Ixx      Iyy      Izz\n");

        this.getGeometry().writeAVLMassData(ps);
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