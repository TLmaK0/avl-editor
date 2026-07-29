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

/**
 * Empirical aerodynamic modeling parameters that AVL cannot compute and that
 * were previously hardcoded in {@link AeroFactory}. They are persisted with the
 * aircraft ({@code .avle}) so the user can tune them, and are baked into the
 * generated {@code <aero>} element on CRRCsim export.
 *
 * Defaults match the values that {@code AeroFactory} used to hardcode.
 *
 * @author Hugo
 */
public class AeroModel {

    // --- Miscellaneous (stall model) ---
    @AvlEditorField(text = "eta_loc",
        help = "Local stall model coefficient (pseudorapidity-like). Typical 0.3.")
    private float eta_loc = 0.3f;

    @AvlEditorField(text = "CG_arm",
        help = "Point of application of the averaged dCL, as a fraction of chord "
             + "ahead of the CG. Typical 0.25.")
    private float CG_arm = 0.25f;

    // --- Lift ---
    @AvlEditorField(text = "CL_max",
        help = "Maximum lift coefficient (positive stall). Typical 1.1.")
    private float CL_max = 1.1f;

    @AvlEditorField(text = "CL_min",
        help = "Minimum lift coefficient (negative stall). Typical -0.6.")
    private float CL_min = -0.6f;

    @AvlEditorField(text = "CL_drop",
        help = "CL drop during the stall break. Typical 0.1.")
    private float CL_drop = 0.1f;

    @AvlEditorField(text = "CL_CD0",
        help = "CL at minimum profile drag. 0.30 for 7037, 0.15 MH32, 0.0 RG15/AGxx.")
    private float CL_CD0 = 0.0f;

    // --- Drag ---
    @AvlEditorField(text = "Uexp_CD",
        help = "Exponent for Reynolds re-scaling of profile drag: CD_prof ~ (U/U_ref)^Uexp_CD.")
    private float Uexp_CD = 0.5f;

    @AvlEditorField(text = "CD_stall",
        help = "Drag coefficient during stalling. Typical -0.5.")
    private float CD_stall = -0.5f;

    @AvlEditorField(text = "CD_CLsq",
        help = "d(CD)/d(CL^2), curvature of the parabolic profile polar. "
             + "0.01 composites, 0.015 saggy ships, 0.02 beat-up ship.")
    private float CD_CLsq = 0.01f;

    @AvlEditorField(text = "CD_AIsq",
        help = "Drag due to aileron deflection d(CD)/d(aileron^2). "
             + "Curvature ~ 0.01/(max_aileron)^2.")
    private float CD_AIsq = 0.01f;

    @AvlEditorField(text = "CD_ELsq",
        help = "Drag due to elevator/elevon deflection d(CD)/d(elevator^2). "
             + "~ 0.01/(max_elevator)^2 for Zagi, otherwise 0.")
    private float CD_ELsq = 0.0f;

    public float getEta_loc() {
        return eta_loc;
    }

    public void setEta_loc(float eta_loc) {
        this.eta_loc = eta_loc;
    }

    public float getCG_arm() {
        return CG_arm;
    }

    public void setCG_arm(float CG_arm) {
        this.CG_arm = CG_arm;
    }

    public float getCL_max() {
        return CL_max;
    }

    public void setCL_max(float CL_max) {
        this.CL_max = CL_max;
    }

    public float getCL_min() {
        return CL_min;
    }

    public void setCL_min(float CL_min) {
        this.CL_min = CL_min;
    }

    public float getCL_drop() {
        return CL_drop;
    }

    public void setCL_drop(float CL_drop) {
        this.CL_drop = CL_drop;
    }

    public float getCL_CD0() {
        return CL_CD0;
    }

    public void setCL_CD0(float CL_CD0) {
        this.CL_CD0 = CL_CD0;
    }

    public float getUexp_CD() {
        return Uexp_CD;
    }

    public void setUexp_CD(float Uexp_CD) {
        this.Uexp_CD = Uexp_CD;
    }

    public float getCD_stall() {
        return CD_stall;
    }

    public void setCD_stall(float CD_stall) {
        this.CD_stall = CD_stall;
    }

    public float getCD_CLsq() {
        return CD_CLsq;
    }

    public void setCD_CLsq(float CD_CLsq) {
        this.CD_CLsq = CD_CLsq;
    }

    public float getCD_AIsq() {
        return CD_AIsq;
    }

    public void setCD_AIsq(float CD_AIsq) {
        this.CD_AIsq = CD_AIsq;
    }

    public float getCD_ELsq() {
        return CD_ELsq;
    }

    public void setCD_ELsq(float CD_ELsq) {
        this.CD_ELsq = CD_ELsq;
    }

    @Override
    public String toString() {
        return "Aero Model";
    }
}
