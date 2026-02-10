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