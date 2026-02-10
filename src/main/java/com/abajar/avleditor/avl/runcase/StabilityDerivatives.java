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

import com.abajar.avleditor.view.annotations.AvlEditorReadOnly;

/**
 *
 * @author hfreire
 */
public class StabilityDerivatives {
    private float Cma;
    private float Cmq;

    private float[] Cmd;
    private float CLa;
    private float CLq; //TODO: Be sure about this parameter (CLq or Clq)
    private float[] CLd;
    private float CYb;
    private float CYp;
    private float CYr;
    private float[] CYd;
    private float Clb;
    private float Clp;
    private float Clr;
    private float[] Cld;
    private float Cnb;
    private float Cnp;
    private float Cnr;
    private float[] Cnd;

    public void initControls(int numControls) {
        Cmd = new float[numControls];
        CLd = new float[numControls];
        CYd = new float[numControls];
        Cld = new float[numControls];
        Cnd = new float[numControls];
    }

    /**
     * @return the Cma
     */
    @AvlEditorReadOnly(text = "Cma", help = "Pitching moment due to angle of attack")
    public float getCma() {
        return Cma;
    }

    /**
     * @param Cma the Cma to set
     */
    public void setCma(float Cma) {
        this.Cma = Cma;
    }

    /**
     * @return the Cmq
     */
    @AvlEditorReadOnly(text = "Cmq", help = "Pitching moment due to pitch rate")
    public float getCmq() {
        return Cmq;
    }

    /**
     * @param Cmq the Cmq to set
     */
    public void setCmq(float Cmq) {
        this.Cmq = Cmq;
    }

    /**
     * @return the Cmd
     */
    public float[] getCmd() {
        return Cmd;
    }

    /**
     * @return the CLa
     */
    @AvlEditorReadOnly(text = "CLa", help = "Lift coefficient due to angle of attack")
    public float getCLa() {
        return CLa;
    }

    /**
     * @param CLa the CLa to set
     */
    public void setCLa(float CLa) {
        this.CLa = CLa;
    }

    /**
     * @return the CLq
     */
    @AvlEditorReadOnly(text = "CLq", help = "Lift coefficient due to pitch rate")
    public float getCLq() {
        return CLq;
    }

    /**
     * @param CLq the CLq to set
     */
    public void setCLq(float CLq) {
        this.CLq = CLq;
    }

    /**
     * @return the CLd
     */
    public float[] getCLd() {
        return CLd;
    }

    /**
     * @return the CYb
     */
    @AvlEditorReadOnly(text = "CYb", help = "Side force due to sideslip")
    public float getCYb() {
        return CYb;
    }

    /**
     * @param CYb the CYb to set
     */
    public void setCYb(float CYb) {
        this.CYb = CYb;
    }

    /**
     * @return the CYp
     */
    @AvlEditorReadOnly(text = "CYp", help = "Side force due to roll rate")
    public float getCYp() {
        return CYp;
    }

    /**
     * @param CYp the CYp to set
     */
    public void setCYp(float CYp) {
        this.CYp = CYp;
    }

    /**
     * @return the CYr
     */
    @AvlEditorReadOnly(text = "CYr", help = "Side force due to yaw rate")
    public float getCYr() {
        return CYr;
    }

    /**
     * @param CYr the CYr to set
     */
    public void setCYr(float CYr) {
        this.CYr = CYr;
    }

    /**
     * @return the CYd
     */
    public float[] getCYd() {
        return CYd;
    }

    /**
     * @return the Clb
     */
    @AvlEditorReadOnly(text = "Clb", help = "Roll moment due to sideslip")
    public float getClb() {
        return Clb;
    }

    /**
     * @param Clb the Clb to set
     */
    public void setClb(float Clb) {
        this.Clb = Clb;
    }

    /**
     * @return the Clp
     */
    @AvlEditorReadOnly(text = "Clp", help = "Roll moment due to roll rate")
    public float getClp() {
        return Clp;
    }

    /**
     * @param Clp the Clp to set
     */
    public void setClp(float Clp) {
        this.Clp = Clp;
    }

    /**
     * @return the Clr
     */
    @AvlEditorReadOnly(text = "Clr", help = "Roll moment due to yaw rate")
    public float getClr() {
        return Clr;
    }

    /**
     * @param Clr the Clr to set
     */
    public void setClr(float Clr) {
        this.Clr = Clr;
    }

    /**
     * @return the Cld
     */
    public float[] getCld() {
        return Cld;
    }

    /**
     * @return the Cnb
     */
    @AvlEditorReadOnly(text = "Cnb", help = "Yaw moment due to sideslip")
    public float getCnb() {
        return Cnb;
    }

    /**
     * @param Cnb the Cnb to set
     */
    public void setCnb(float Cnb) {
        this.Cnb = Cnb;
    }

    /**
     * @return the Cnp
     */
    @AvlEditorReadOnly(text = "Cnp", help = "Yaw moment due to roll rate")
    public float getCnp() {
        return Cnp;
    }

    /**
     * @param Cnp the Cnp to set
     */
    public void setCnp(float Cnp) {
        this.Cnp = Cnp;
    }

    /**
     * @return the Cnr
     */
    @AvlEditorReadOnly(text = "Cnr", help = "Yaw moment due to yaw rate")
    public float getCnr() {
        return Cnr;
    }

    /**
     * @param Cnr the Cnr to set
     */
    public void setCnr(float Cnr) {
        this.Cnr = Cnr;
    }

    /**
     * @return the Cnd
     */
    public float[] getCnd() {
        return Cnd;
    }

    @Override
    public String toString() {
        return "Stability Derivatives";
    }
}