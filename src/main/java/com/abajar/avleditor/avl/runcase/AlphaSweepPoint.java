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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * What AVL answered with the aircraft held at one attitude, with the controls at neutral.
 *
 * The exported flight model used to carry a single measurement plus a rate — so much lift per degree of
 * nose-up — and JSBSim continued that straight line to any attitude it liked. A list of these is the
 * measured curve instead, so the model describes the aircraft over a range rather than at a point nobody
 * chose deliberately.
 *
 * Controls neutral is not incidental: JSBSim adds the elevator's effect itself through Cmde x elevator, so a
 * point measured with the elevator trimmed would carry that trim inside Cm and count the elevator twice.
 */
public class AlphaSweepPoint implements Serializable {
    static final long serialVersionUID = 20260805L;

    private final float alphaDeg;
    private final float cl;
    private final float cd;
    private final float cm;

    /** The derivatives at this attitude, for the report on which of them actually move with it. */
    private final float cla;
    private final float cma;
    private final float cnb;
    private final float clb;

    public AlphaSweepPoint(float alphaDeg, float cl, float cd, float cm,
                           float cla, float cma, float cnb, float clb) {
        this.alphaDeg = alphaDeg;
        this.cl = cl;
        this.cd = cd;
        this.cm = cm;
        this.cla = cla;
        this.cma = cma;
        this.cnb = cnb;
        this.clb = clb;
    }

    public float getAlphaDeg() { return alphaDeg; }

    public float getAlphaRad() { return (float) Math.toRadians(alphaDeg); }

    public float getCl() { return cl; }

    public float getCd() { return cd; }

    public float getCm() { return cm; }

    public float getCla() { return cla; }

    public float getCma() { return cma; }

    public float getCnb() { return cnb; }

    public float getClb() { return clb; }

    /**
     * How the lift is spread across the span at this attitude, one entry per spanwise strip
     * (AVL's OPER {@code fs}). Empty when the strip forces were not asked for or did not arrive.
     *
     * It rides on the attitude rather than on the calculation because the pair is the measurement:
     * a spanwise loading means nothing without the attitude it was measured at, and the whole of the
     * critical-section analysis is the slope of each strip's {@code cl} against that attitude.
     */
    private List<StripForce> strips = new ArrayList<StripForce>();

    public List<StripForce> getStrips() { return strips; }

    public void setStrips(List<StripForce> strips) {
        this.strips = strips == null ? new ArrayList<StripForce>() : strips;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ENGLISH,
            "alpha %6.2f deg: CL %8.4f  CD %8.5f  Cm %8.4f", alphaDeg, cl, cd, cm);
    }

    /**
     * How far a derivative that is exported as <b>one number</b> actually moved across the sweep.
     *
     * The lift, drag and pitching moment are exported as curves; everything else is exported as a single
     * value, on the grounds that AVL is a linear solver and thirteen copies of the same number would only
     * hide that fact. That is a claim about the aircraft, not a law, so the sweep measures it and says so —
     * otherwise the reason for the choice lives in a comment and nobody ever finds out that on some aircraft
     * it was wrong.
     *
     * One line per derivative: the range, and the spread as a percentage of the mean. A derivative that has
     * moved by a large fraction of itself is the signal that it deserves a curve of its own.
     */
    public static List<String> constantsReport(List<AlphaSweepPoint> points) {
        List<String> lines = new ArrayList<String>();
        if (points == null || points.size() < 2) return lines;

        lines.add("Exported as curves in attitude: CL, CD, Cm. Exported as one number, and how far each "
            + "actually moved across the sweep:");
        lines.add(describe("CLa", values(points, "CLa")));
        lines.add(describe("Cma", values(points, "Cma")));
        lines.add(describe("Cnb", values(points, "Cnb")));
        lines.add(describe("Clb", values(points, "Clb")));
        return lines;
    }

    private static List<Float> values(List<AlphaSweepPoint> points, String name) {
        List<Float> values = new ArrayList<Float>();
        for (AlphaSweepPoint point : points) {
            float value;
            if ("CLa".equals(name)) value = point.getCla();
            else if ("Cma".equals(name)) value = point.getCma();
            else if ("Cnb".equals(name)) value = point.getCnb();
            else value = point.getClb();
            if (!Float.isNaN(value) && !Float.isInfinite(value)) values.add(value);
        }
        return values;
    }

    private static String describe(String name, List<Float> values) {
        if (values.isEmpty()) {
            return String.format(java.util.Locale.ENGLISH, "  %-4s not reported by AVL", name);
        }
        float min = values.get(0);
        float max = values.get(0);
        float sum = 0f;
        for (float value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
        }
        float mean = sum / values.size();
        float spread = max - min;
        String relative = Math.abs(mean) > 1e-9f
            ? String.format(java.util.Locale.ENGLISH, "%.0f%% of its mean", 100f * spread / Math.abs(mean))
            : "its mean is zero";
        // A derivative that moved by more than its own size across the range is not one number.
        String verdict = Math.abs(mean) > 1e-9f && spread > Math.abs(mean)
            ? " <- moves more than itself: a single value misrepresents it"
            : "";
        return String.format(java.util.Locale.ENGLISH,
            "  %-4s %9.4f to %9.4f, spread %.4f (%s)%s", name, min, max, spread, relative, verdict);
    }
}
