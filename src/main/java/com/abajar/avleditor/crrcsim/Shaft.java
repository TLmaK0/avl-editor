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
    ENABLE_BUTTONS.ADD_PROPELLER, ENABLE_BUTTONS.ADD_DUCTED_FAN, ENABLE_BUTTONS.DUPLICATE, ENABLE_BUTTONS.DELETE})
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
    private ArrayList<DuctedFan> ductedFans = new ArrayList<DuctedFan>();
    private ArrayList<SimpleTrust> simpleTrusts = new ArrayList<SimpleTrust>();

    /**
     * Where the shaft is, and with it everything mounted on it.
     *
     * A shaft is an assembly — a motor turning a propeller or a fan — and it moves as one: the positions its
     * components state are <b>relative to this</b>, so moving the shaft carries them all and each can still be
     * placed within it afterwards. The same arrangement as a ducted fan and its exhaust, one level up.
     *
     * It starts at zero, which is why a model written before the shaft had a position is unchanged by this:
     * relative to nothing is absolute.
     */
    private Pos pos = new Pos();

    @AvlEditorNode(name="Pos")
    public Pos getPos() {
        return pos;
    }

    public void setPos(Pos pos) {
        this.pos = pos;
    }

    /**
     * Where something mounted on this shaft actually is. The one place that adds the two together, so nothing
     * that draws, weighs or exports a component can disagree with the rest about where it sits.
     */
    public float absoluteX(float relativeX) {
        return pos.getX() + relativeX;
    }

    public float absoluteY(float relativeY) {
        return pos.getY() + relativeY;
    }

    public float absoluteZ(float relativeZ) {
        return pos.getZ() + relativeZ;
    }


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
     * @return the ducted fans
     */
    @AvlEditorNode(name="Ducted fans")
    @XmlElement(name="ductedfan")
    public ArrayList<DuctedFan> getDuctedFans() {
        return ductedFans;
    }

    public void setDuctedFans(ArrayList<DuctedFan> ductedFans) {
        this.ductedFans = ductedFans;
    }

    /**
     * A ducted fan instead of a propeller, driven by the same motor. It states its bore, its blades and the
     * static thrust it is sold with; the revolutions and the power come from the motor, so they are not asked
     * for twice.
     */
    public DuctedFan createDuctedFan() {
        DuctedFan fan = new DuctedFan();
        this.getDuctedFans().add(fan);
        return fan;
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

    /**
     * Kept so models saved with a Simple Trust still load and save it unchanged, but no longer
     * offered in the toolbar: it is a CRRCsim thrust model, the CRRCsim export is gone, and the
     * JSBSim one cannot use it — so creating a new one would build something with no destination.
     */
    public SimpleTrust createSimpleTrust() {
        SimpleTrust trust = new SimpleTrust();
        this.getSimpleTrusts().add(trust);
        return trust;
    }
}