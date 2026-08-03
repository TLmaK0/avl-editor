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
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 *
 * @author Hugo
 */
@AvlEditor(buttons={ENABLE_BUTTONS.ADD_DATA, ENABLE_BUTTONS.ADD_DATA_IDLE})
public class Engine implements Serializable{

    @Override
    public String toString() {
        return "Engine";
    }

    @AvlEditorField(text="Rotor inertia (J_M)",
        help="J_M, the engine's rotor's inertia, can be found in the manufacturer's data sheet,\r\n" +
            "or it has to be guessed. You can estimate it by regarding\r\n" +
            "the rotor as a solid iron cylinder of mass m (in kg) and diameter d (in m) using the formula:\r\n" +
            "J_M = 0.5 * m * d^2 / 4"
    )
    private float J_M;

    private ArrayList<EngineData> data = new ArrayList<EngineData>();
    private ArrayList<EngineDataIdle> dataIdle = new ArrayList<EngineDataIdle>();

    private Gearing gearing = new Gearing();

    private int Calc = 1;


    @AvlEditorField(text="Mass",
        help="Mass of the electric motor, in the model's mass unit. It counts towards the centre of gravity and the"
        + " inertias, so weigh it rather than compensating with ballast elsewhere."
    )
    private float mass;

    private Pos pos = new Pos();

    public Engine() {
    }

    /**
     * @return the J_M
     */
    @XmlAttribute(name="J_M")
    public float getJ_M() {
        return J_M;
    }

    /**
     * @param J_M the J_M to set
     */
    public void setJ_M(float J_M) {
        this.J_M = J_M;
    }

    /**
     * @return the gearing
     */
    @AvlEditorNode
    @XmlElement
    public Gearing getGearing() {
        return gearing;
    }

    /**
     * @param gearing the gearing to set
     */
    public void setGearing(Gearing gearing) {
        this.gearing = gearing;
    }

    /**
     * @return the data
     */
    @AvlEditorNode(name="Data")
    @XmlElement(name="data")
    @XmlElementWrapper(name="data")
    public ArrayList<EngineData> getData() {
        return data;
    }

    /**
     * @param data the data to set
     */
    public void setData(ArrayList<EngineData> data) {
        this.data = data;
    }

    /**
     * @return the Calc
     */
    @XmlAttribute
    public int getCalc() {
        return Calc;
    }

    /**
     * @return the dataIdle
     */
    @AvlEditorNode(name="DataIdle")
    @XmlElement(name="data")
    @XmlElementWrapper(name="data_idle")
    public ArrayList<EngineDataIdle> getDataIdle() {
        return dataIdle;
    }

    /**
     * @param dataIdle the dataIdle to set
     */
    public void setDataIdle(ArrayList<EngineDataIdle> dataIdle) {
        this.dataIdle = dataIdle;
    }

    public EngineData createData() {
        EngineData newData = new EngineData();
        this.getData().add(newData);
        return newData;
    }

    public EngineDataIdle createDataIdle() {
        EngineDataIdle newDataIdle = new EngineDataIdle();
        this.getDataIdle().add(newDataIdle);
        return newDataIdle;
    }

    /**
     * @return the mass of the motor
     */
    @XmlAttribute
    public float getMass() {
        return mass;
    }

    /**
     * @param mass the mass of the motor to set
     */
    public void setMass(float mass) {
        this.mass = mass;
    }

    /**
     * @return where the motor sits
     */
    @AvlEditorNode(name="Position")
    @XmlElement(name="pos")
    public Pos getPos() {
        return pos;
    }

    /**
     * @param pos where the motor sits
     */
    public void setPos(Pos pos) {
        this.pos = pos;
    }
}
