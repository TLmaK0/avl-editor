/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.crrcsim;

import com.abajar.avleditor.view.annotations.AvlEditorField;
import java.io.Serializable;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * A combustion engine (glow, petrol or diesel), as a sibling of the electric {@link Engine} under
 * a {@link Shaft}. Everything here is metric and stated by the model: the JSBSim export converts
 * to the units its piston model wants, and refuses to guess anything that is missing.
 *
 * Unlike the electric motor, which is described by a voltage/current/rpm table, a combustion
 * engine is described by its displacement, its power and its fuel consumption; the fuel itself
 * lives in a {@link FuelTank} under Power, because its mass changes as it burns.
 *
 * @author Hugo
 */
public class CombustionEngine implements Serializable {
    static final long serialVersionUID = 6011938471223905011L;

    @Override
    public String toString() {
        return "Combustion engine";
    }

    @AvlEditorField(text="Displacement (cm3)",
        help="Swept volume of all cylinders, in cubic centimetres. A 10 cc glow engine is 10."
    )
    private float displacement;

    @AvlEditorField(text="Max power (W)",
        help="Shaft power at maximum rpm, in watts. Manufacturers often quote horsepower:"
        + " 1 hp = 745.7 W."
    )
    private float maxPower;

    @AvlEditorField(text="Idle rpm",
        help="Lowest rpm the engine runs at with the throttle closed."
    )
    private float idleRpm;

    @AvlEditorField(text="Max rpm",
        help="Highest rpm the engine reaches at full throttle."
    )
    private float maxRpm;

    @AvlEditorField(text="Stroke cycles (2 or 4)",
        help="Two for a two-stroke (most model glow engines), four for a four-stroke."
    )
    private int cycles = 2;

    @AvlEditorField(text="Fuel consumption (g/kWh)",
        help="Brake specific fuel consumption: grams of fuel per kilowatt-hour of shaft work."
        + " Model glow engines are in the 400-900 range; a petrol four-stroke is nearer 350."
    )
    private float fuelConsumption;

    public CombustionEngine() {
    }

    /**
     * @return the displacement in cm3
     */
    @XmlAttribute
    public float getDisplacement() {
        return displacement;
    }

    /**
     * @param displacement the displacement in cm3 to set
     */
    public void setDisplacement(float displacement) {
        this.displacement = displacement;
    }

    /**
     * @return the maximum shaft power in watts
     */
    @XmlAttribute
    public float getMaxPower() {
        return maxPower;
    }

    /**
     * @param maxPower the maximum shaft power in watts to set
     */
    public void setMaxPower(float maxPower) {
        this.maxPower = maxPower;
    }

    /**
     * @return the idle rpm
     */
    @XmlAttribute
    public float getIdleRpm() {
        return idleRpm;
    }

    /**
     * @param idleRpm the idle rpm to set
     */
    public void setIdleRpm(float idleRpm) {
        this.idleRpm = idleRpm;
    }

    /**
     * @return the maximum rpm
     */
    @XmlAttribute
    public float getMaxRpm() {
        return maxRpm;
    }

    /**
     * @param maxRpm the maximum rpm to set
     */
    public void setMaxRpm(float maxRpm) {
        this.maxRpm = maxRpm;
    }

    /**
     * @return the number of stroke cycles (2 or 4)
     */
    @XmlAttribute
    public int getCycles() {
        return cycles;
    }

    /**
     * @param cycles the number of stroke cycles (2 or 4) to set
     */
    public void setCycles(int cycles) {
        this.cycles = cycles;
    }

    /**
     * @return the brake specific fuel consumption in g/kWh
     */
    @XmlAttribute
    public float getFuelConsumption() {
        return fuelConsumption;
    }

    /**
     * @param fuelConsumption the brake specific fuel consumption in g/kWh to set
     */
    public void setFuelConsumption(float fuelConsumption) {
        this.fuelConsumption = fuelConsumption;
    }
}
