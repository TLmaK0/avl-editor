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

/**
 *
 * @author Hugo
 */
@AvlEditor(buttons={ENABLE_BUTTONS.ADD_ENGINE, ENABLE_BUTTONS.ADD_COMBUSTION_ENGINE,
    ENABLE_BUTTONS.ADD_PROPELLER, ENABLE_BUTTONS.ADD_SYMPLE_TRUST})
public class Shaft implements Serializable {
    static final long serialVersionUID = -4669977187731929600L;
    @Override
    public String toString() {
        return "Shaft";
    }

    @AvlEditorField(text="Inertia (J)",
        help="Inertia in kg m^2"
    )
    private float J;

    @AvlEditorField(text="Brake when throttle at zero (brake)",
        help="if brake is not zero, this shaft will stop rotating as soon as the throttle command is zero. This is needed for folding props."
    )
    private int brake;

    private ArrayList<Engine> engines = new ArrayList<Engine>();
    private ArrayList<CombustionEngine> combustionEngines = new ArrayList<CombustionEngine>();
    private ArrayList<Propeller> propellers = new ArrayList<Propeller>();
    private ArrayList<SimpleTrust> simpleTrusts = new ArrayList<SimpleTrust>();


    public Shaft() {
    }

    /**
     * @return the J
     */
    @XmlAttribute
    public float getJ() {
        return J;
    }

    /**
     * @param J the J to set
     */
    public void setJ(float J) {
        this.J = J;
    }

    /**
     * @return the brake
     */
    @XmlAttribute
    public int getBrake() {
        return brake;
    }

    /**
     * @param brake the brake to set
     */
    public void setBrake(int brake) {
        this.brake = brake;
    }

    /**
     * @return the engines
     */
    @AvlEditorNode(name="Engines")
    @XmlElement(name="engine_dcm")
    public ArrayList<Engine> getEngines() {
        return engines;
    }

    /**
     * @param engines the engines to set
     */
    public void setEngines(ArrayList<Engine> engines) {
        this.engines = engines;
    }

    public Engine createEngine() {
        Engine engine = new Engine();
        this.getEngines().add(engine);
        return engine;
    }

    /**
     * @return the combustion engines
     */
    @AvlEditorNode(name="Combustion engines")
    @XmlElement(name="combustionengine")
    public ArrayList<CombustionEngine> getCombustionEngines() {
        return combustionEngines;
    }

    /**
     * @param combustionEngines the combustion engines to set
     */
    public void setCombustionEngines(ArrayList<CombustionEngine> combustionEngines) {
        this.combustionEngines = combustionEngines;
    }

    public CombustionEngine createCombustionEngine() {
        CombustionEngine engine = new CombustionEngine();
        this.getCombustionEngines().add(engine);
        return engine;
    }

    /**
     * @return the propellers
     */
    @AvlEditorNode(name="Propellers")
    @XmlElement(name="propeller")
    public ArrayList<Propeller> getPropellers() {
        return propellers;
    }

    /**
     * @param propellers the propellers to set
     */
    public void setPropellers(ArrayList<Propeller> propellers) {
        this.propellers = propellers;
    }

    /**
     * The JSBSim export drives a fixed-pitch propeller from the motor, so it needs the
     * propeller's diameter and blade count. The blade count starts at the usual two (see
     * Propeller); a diameter cannot be guessed, so SimulationRequirements asks for it.
     */
    public Propeller createPropeller() {
        Propeller propeller = new Propeller();
        this.getPropellers().add(propeller);
        return propeller;
    }

    /**
     * @return the simpleTrusts
     */
    @AvlEditorNode(name="Simple Trusts")
    @XmlElement(name="simpletrust")
    public ArrayList<SimpleTrust> getSimpleTrusts() {
        return simpleTrusts;
    }

    /**
     * @param simpleTrusts the simpleTrusts to set
     */
    public void setSimpleTrusts(ArrayList<SimpleTrust> simpleTrusts) {
        this.simpleTrusts = simpleTrusts;
    }

    public SimpleTrust createSimpleTrust() {
        SimpleTrust trust = new SimpleTrust();
        this.getSimpleTrusts().add(trust);
        return trust;
    }
}