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

import com.abajar.avleditor.view.annotations.AvlEditorNode;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author hfreire
 */
public class AvlCalculation {
    private Configuration configuration = new Configuration();
    private StabilityDerivatives stabilityDerivatives = new StabilityDerivatives();
    private final int elevatorPosition;
    private final int rudderPosition;
    private final int aileronPosition;
    private String[] controlNames = new String[]{"d1", "d2", "d3"};
    private float[] controlGains = new float[]{1f, 1f, 1f};
    private float[] trimControlValues = new float[]{Float.NaN, Float.NaN, Float.NaN};
    private float[] trimControlDeflections = new float[]{Float.NaN, Float.NaN, Float.NaN};
    private List<AvlEigenvalue> eigenvalues = new ArrayList<AvlEigenvalue>();

    public AvlCalculation(int elevatorPosition, int rudderPosition, int aileronPosition){
        this.elevatorPosition = elevatorPosition;
        this.rudderPosition = rudderPosition;
        this.aileronPosition = aileronPosition;
    }

    public void setControlNames(String[] names) {
        this.controlNames = names;
    }

    public String[] getControlNames() {
        return controlNames;
    }

    public void setControlGains(float[] controlGains) {
        this.controlGains = controlGains;
    }

    public float[] getControlGains() {
        return controlGains;
    }

    public void setTrimControlValues(float[] trimControlValues) {
        this.trimControlValues = trimControlValues;
    }

    public float[] getTrimControlValues() {
        return trimControlValues;
    }

    public void setTrimControlDeflections(float[] trimControlDeflections) {
        this.trimControlDeflections = trimControlDeflections;
    }

    public float[] getTrimControlDeflections() {
        return trimControlDeflections;
    }

    @AvlEditorNode(name = "Eigenvalues")
    public List<AvlEigenvalue> getEigenvalues() {
        return eigenvalues;
    }

    public void setEigenvalues(List<AvlEigenvalue> eigenvalues) {
        this.eigenvalues = eigenvalues;
    }
    /**
     * @return the stabilityDerivatives
     */
    @AvlEditorNode
    public StabilityDerivatives getStabilityDerivatives() {
        return stabilityDerivatives;
    }

    /**
     * @param stabilityDerivatives the stabilityDerivatives to set
     */
    public void setStabilityDerivatives(StabilityDerivatives stabilityDerivatives) {
        this.stabilityDerivatives = stabilityDerivatives;
    }

    /**
     * @return the configuration
     */
    @AvlEditorNode
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * @param configuration the configuration to set
     */
    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * @return the elevatorPosition
     */
    public int getElevatorPosition() {
        return elevatorPosition;
    }

    /**
     * @return the rudderPosition
     */
    public int getRudderPosition() {
        return rudderPosition;
    }

    /**
     * @return the aileronPosition
     */
    public int getAileronPosition() {
        return aileronPosition;
    }

    @Override
    public String toString() {
        return "AVL Results";
    }
}
