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
@AvlEditor(buttons={ENABLE_BUTTONS.DELETE})
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

    void setMass_inertiaFromMasses(ArrayList<Mass> masses, String lengthUnit, String massUnit) {
        this.calculateInertiasMasses(masses, lengthUnit, massUnit);
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

    private void calculateInertiasMasses(ArrayList<Mass> masses, String lengthUnit, String massUnit) {
        float I_xx = 0;
        float I_yy = 0;
        float I_zz = 0;
        float I_xz = 0;
        float totalMass = 0;
        for(Mass mass: masses){
            I_xx += calculateMomentInertiaFromAxis(mass.getY(), mass.getZ(), mass.getIxx(), mass.getMass());
            I_yy += calculateMomentInertiaFromAxis(mass.getX(), mass.getZ(), mass.getIyy(), mass.getMass());
            I_zz += calculateMomentInertiaFromAxis(mass.getX(), mass.getY(), mass.getIzz(), mass.getMass());
            I_xz += calculateProductInertiaFromAxis(mass.getX(), mass.getZ(), mass.getIxz(), mass.getMass());
            totalMass += mass.getMass();
        }

        //setting and convert to kg * m2
        UnitConversor uc = new UnitConversor();
        this.mass_inertia.setI_xx(uc.convertToKilogramsSquareMeters(I_xx, massUnit, lengthUnit));
        this.mass_inertia.setI_yy(uc.convertToKilogramsSquareMeters(I_yy, massUnit, lengthUnit));
        this.mass_inertia.setI_zz(uc.convertToKilogramsSquareMeters(I_zz, massUnit, lengthUnit));
        this.mass_inertia.setI_xz(uc.convertToKilogramsSquareMeters(I_xz, massUnit, lengthUnit));
        this.mass_inertia.setMass(uc.convertToKilograms(totalMass, massUnit));
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