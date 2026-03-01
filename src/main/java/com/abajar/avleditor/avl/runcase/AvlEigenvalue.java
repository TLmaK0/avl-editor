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

public class AvlEigenvalue {
    private final float sigma;
    private final float omega;
    private float uAmplitude = Float.NaN;
    private float vAmplitude = Float.NaN;
    private float wAmplitude = Float.NaN;
    private float pAmplitude = Float.NaN;
    private float qAmplitude = Float.NaN;
    private float rAmplitude = Float.NaN;
    private float thetaAmplitude = Float.NaN;
    private float phiAmplitude = Float.NaN;
    private float psiAmplitude = Float.NaN;

    public AvlEigenvalue(float sigma, float omega) {
        this.sigma = sigma;
        this.omega = omega;
    }

    @AvlEditorReadOnly(text = "sigma", help = "Real part of eigenvalue (1/s)")
    public float getSigma() {
        return sigma;
    }

    @AvlEditorReadOnly(text = "omega", help = "Imaginary part of eigenvalue (rad/s)")
    public float getOmega() {
        return omega;
    }

    @AvlEditorReadOnly(text = "wn", help = "Natural frequency sqrt(sigma^2 + omega^2)")
    public float getNaturalFrequency() {
        return (float) Math.sqrt(sigma * sigma + omega * omega);
    }

    @AvlEditorReadOnly(text = "zeta", help = "Damping ratio -sigma/wn (oscillatory modes)")
    public float getDampingRatio() {
        float wn = getNaturalFrequency();
        if (wn == 0) return 0;
        return -sigma / wn;
    }

    public void setModeStateAmplitude(String state, float amplitude) {
        if (state == null) return;
        String normalized = state.trim().toLowerCase();
        if (normalized.equals("the") || normalized.equals("theta")) {
            thetaAmplitude = amplitude;
            return;
        }
        if (normalized.equals("u")) {
            uAmplitude = amplitude;
        } else if (normalized.equals("v")) {
            vAmplitude = amplitude;
        } else if (normalized.equals("w")) {
            wAmplitude = amplitude;
        } else if (normalized.equals("p")) {
            pAmplitude = amplitude;
        } else if (normalized.equals("q")) {
            qAmplitude = amplitude;
        } else if (normalized.equals("r")) {
            rAmplitude = amplitude;
        } else if (normalized.equals("phi")) {
            phiAmplitude = amplitude;
        } else if (normalized.equals("psi")) {
            psiAmplitude = amplitude;
        }
    }

    public boolean hasModeShape() {
        return isFinite(uAmplitude) || isFinite(vAmplitude) || isFinite(wAmplitude)
            || isFinite(pAmplitude) || isFinite(qAmplitude) || isFinite(rAmplitude)
            || isFinite(thetaAmplitude) || isFinite(phiAmplitude) || isFinite(psiAmplitude);
    }

    public float getLongitudinalParticipation() {
        return sumFinite(uAmplitude, wAmplitude, qAmplitude, thetaAmplitude);
    }

    public float getLateralParticipation() {
        return sumFinite(vAmplitude, pAmplitude, rAmplitude, phiAmplitude, psiAmplitude);
    }

    public float getPitchParticipation() {
        return sumFinite(wAmplitude, qAmplitude, thetaAmplitude);
    }

    public float getSpeedParticipation() {
        return sumFinite(uAmplitude, thetaAmplitude);
    }

    public float getTotalParticipation() {
        return sumFinite(
            uAmplitude, vAmplitude, wAmplitude,
            pAmplitude, qAmplitude, rAmplitude,
            thetaAmplitude, phiAmplitude, psiAmplitude
        );
    }

    public float getLongitudinalRatio() {
        float total = getTotalParticipation();
        return total == 0 ? 0 : getLongitudinalParticipation() / total;
    }

    public float getLateralRatio() {
        float total = getTotalParticipation();
        return total == 0 ? 0 : getLateralParticipation() / total;
    }

    public float getPitchRatio() {
        float longitudinal = getLongitudinalParticipation();
        return longitudinal == 0 ? 0 : getPitchParticipation() / longitudinal;
    }

    public float getSpeedRatio() {
        float longitudinal = getLongitudinalParticipation();
        return longitudinal == 0 ? 0 : getSpeedParticipation() / longitudinal;
    }

    private static float sumFinite(float... values) {
        float sum = 0f;
        for (float value : values) {
            if (isFinite(value)) sum += value;
        }
        return sum;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    @Override
    public String toString() {
        return String.format("lambda = %.5f %+.5fi", sigma, omega);
    }
}
