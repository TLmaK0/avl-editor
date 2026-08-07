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

import com.abajar.avleditor.UnitConversor;
import com.abajar.avleditor.avl.mass.Mass;
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
@AvlEditor(buttons={ENABLE_BUTTONS.DUPLICATE, ENABLE_BUTTONS.DELETE})
public class Config  implements Serializable{
    static final long serialVersionUID = 2660699319046872464L;

    @AvlEditorField(text="short description",
        help="Short description of the config"
    )
    private String descr_short = "new config";

    @AvlEditorField(text="long description",
        help="Long description of the config"
    )
    private String descr_long;

    private MassInertia mass_inertia = new MassInertia();
    private Sound sound = new Sound();
    private Power power = new Power();

    @Override
    public String toString() {
        return this.descr_short;
    }


    /**
     * @return the descr_long
     */
    public String getDescr_long() {
        return descr_long;
    }

    /**
     * @param descr_long the descr_long to set
     */
    public void setDescr_long(String descr_long) {
        this.descr_long = descr_long;
    }

    /**
     * @return the descr_short
     */
    public String getDescr_short() {
        return descr_short;
    }

    /**
     * @param descr_short the descr_short to set
     */
    public void setDescr_short(String descr_short) {
        this.descr_short = descr_short;
    }

    /**
     * @return the mass_inertia
     */
    public MassInertia getMass_inertia() {
        return mass_inertia;
    }

    /**
     * @param mass_inertia the mass_inertia to set
     */
    public void setMass_inertia(MassInertia mass_inertia) {
        this.mass_inertia = mass_inertia;
    }

    /**
     * @return the sound
     */
    @AvlEditorNode
    @XmlElement
    public Sound getSound() {
        return sound;
    }

    /**
     * @param sound the sound to set
     */
    public void setSound(Sound sound) {
        this.sound = sound;
    }

    void setMass_inertiaFromMasses(ArrayList<Mass> masses, com.abajar.avleditor.ModelUnits units) {
        this.calculateInertiasMasses(masses, units);
    }

    private double calculateMomentInertiaFromAxis(float coord1, float coord2, float originalMomentInertia, float mass){
        //Parallel Axis Teorem http://en.wikipedia.org/wiki/Parallel_axis_theorem
        //Ixx_0 = I_xx + m * r^2
        //r = square(y^2 + z^2)
        //Ixx_0 = I_xx + m * square(y^2 + z^2)^2
        //Ixx_0 = I_xx + m * y^2 + z^2
        return originalMomentInertia + mass * (Math.pow(coord1, 2) + Math.pow(coord2, 2));
    }

    private double calculateProductInertiaFromAxis(float coord1, float coord2, float originalProductInertia, float mass){
        //Parallel Axes Theorem for Products of Inertia http://homepages.wmich.edu/~kamman/Me659InertiaMatrix.pdf
        //I_xz_0 = Ixz + m * x * z
        return originalProductInertia + mass * coord1 * coord2;
    }

    private void calculateInertiasMasses(ArrayList<Mass> masses, com.abajar.avleditor.ModelUnits units) {
        float I_xx = 0;
        float I_yy = 0;
        float I_zz = 0;
        float I_xz = 0;
        float totalMass = 0;
        // About the CENTRE OF GRAVITY, not about the origin of the drawing.
        //
        // These used to be summed about the origin, and the origin is wherever the user happened to start
        // drawing — usually the nose. The parallel-axis terms m*x_cg^2 and m*z_cg^2 were therefore left in,
        // so every inertia except I_xx came out too large, by more the further forward the origin sat.
        //
        // It matters because this is what the JSBSim export writes into <mass_balance>, and JSBSim reads
        // those as inertias about the CG it is given on the next line. An aircraft whose origin is 0.3 m
        // ahead of its CG was exported with a pitch and yaw inertia several times what it has, and flew
        // sluggishly in a way nothing in the file pointed at. AVL was never affected: it is handed the point
        // masses themselves and works its own inertias out, which is how the disagreement was found: a lateral
        // model built from these inertias gave roots that did not match the ones AVL had already returned.
        float xCg = 0, yCg = 0, zCg = 0;
        for (Mass mass : masses) {
            totalMass += mass.getMass();
            xCg += mass.getMass() * mass.getX();
            yCg += mass.getMass() * mass.getY();
            zCg += mass.getMass() * mass.getZ();
        }
        if (totalMass > 0) {
            xCg /= totalMass;
            yCg /= totalMass;
            zCg /= totalMass;
        }

        for(Mass mass: masses){
            float x = mass.getX() - xCg;
            float y = mass.getY() - yCg;
            float z = mass.getZ() - zCg;
            I_xx += calculateMomentInertiaFromAxis(y, z, mass.getIxx(), mass.getMass());
            I_yy += calculateMomentInertiaFromAxis(x, z, mass.getIyy(), mass.getMass());
            I_zz += calculateMomentInertiaFromAxis(x, y, mass.getIzz(), mass.getMass());
            I_xz += calculateProductInertiaFromAxis(x, z, mass.getIxz(), mass.getMass());
        }

        // Into kg and kg*m2, through the model's own units rather than a unit string picked up here.
        this.mass_inertia.setI_xx(units.toKilogramsSquareMetres(I_xx));
        this.mass_inertia.setI_yy(units.toKilogramsSquareMetres(I_yy));
        this.mass_inertia.setI_zz(units.toKilogramsSquareMetres(I_zz));
        this.mass_inertia.setI_xz(units.toKilogramsSquareMetres(I_xz));
        this.mass_inertia.setMass(units.toKilograms(totalMass));
    }

    /**
     * @return the power
     */
    @AvlEditorNode
    @XmlElement
    public Power getPower() {
        return power;
    }

    /**
     * @param power the power to set
     */
    public void setPower(Power power) {
        this.power = power;
    }

    
    
}