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

import com.abajar.avleditor.view.annotations.AvlEditor;
import com.abajar.avleditor.view.annotations.AvlEditorField;
import com.abajar.avleditor.view.annotations.AvlEditorNode;
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS;
import java.io.Serializable;
import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author Hugo
 */
@AvlEditor(buttons={ENABLE_BUTTONS.ADD_BATTERY, ENABLE_BUTTONS.ADD_FUEL_TANK})
public class Power implements Serializable {

    public Battery createBattery() {
        Battery battery = new Battery();
        this.getBateries().add(battery);
        return battery;
    }

    public FuelTank createFuelTank() {
        FuelTank tank = new FuelTank();
        this.getFuelTanks().add(tank);
        return tank;
    }

    /**
     * @return the fuel tanks
     */
    @AvlEditorNode(name="Fuel tanks")
    @XmlElement(name="fueltank")
    public ArrayList<FuelTank> getFuelTanks() {
        return fuelTanks;
    }

    /**
     * @param fuelTanks the fuel tanks to set
     */
    public void setFuelTanks(ArrayList<FuelTank> fuelTanks) {
        this.fuelTanks = fuelTanks;
    }

    /**
     * @return the bateries
     */
    @AvlEditorNode(name="batteries")
    @XmlElement(name="battery")
    public ArrayList<Battery> getBateries() {
        return bateries;
    }

    /**
     * @param bateries the bateries to set
     */
    public void setBateries(ArrayList<Battery> bateries) {
        this.bateries = bateries;
    }

    

    
    private ArrayList<FuelTank> fuelTanks = new ArrayList<FuelTank>();
    private ArrayList<Battery> bateries = new ArrayList<Battery>();
    public Power() {
    }

    @Override
    public String toString() {
        return "Power";
    }

    

    
}