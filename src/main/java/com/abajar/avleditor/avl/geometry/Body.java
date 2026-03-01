/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.avl.geometry;

import com.abajar.avleditor.view.annotations.AvlEditorField;
import com.abajar.avleditor.view.annotations.AvlEditorNode;
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS;
import com.abajar.avleditor.view.annotations.AvlEditor;
import com.abajar.avleditor.avl.AVLSerializable;
import com.abajar.avleditor.avl.mass.MassObject;
import java.io.OutputStream;
import java.io.PrintStream;
import static com.abajar.avleditor.avl.AVLGeometry.formatFloat;
import static com.abajar.avleditor.avl.AVLGeometry.formatInteger;
import java.util.ArrayList;
import java.util.Comparator;
import com.abajar.avleditor.avl.mass.Mass;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 *
 * @author hfreire
 */
@AvlEditor(buttons={ENABLE_BUTTONS.ADD_PROFILE_POINT, ENABLE_BUTTONS.IMPORT_BFILE, ENABLE_BUTTONS.DELETE, ENABLE_BUTTONS.ADD_MASS})
public class Body extends MassObject implements AVLSerializable  {
    static final long serialVersionUID = -8843371548047761516L;

    private final ArrayList<BodyProfilePoint> profilePoints = new ArrayList<>();

    @AvlEditorField(text="Body name",
        help="Body name"
    )
    private String name = "new body";

    @AvlEditorField(text="Nbody",
        help="number of source-line nodes"
    )
    private int Nbody = 20;  // Default to 20 nodes for body rendering

    @AvlEditorField(text="Bspace",
        help="lengthwise node spacing parameter\r\n\r\n"
            + "3.0        equal         |   |   |   |   |   |   |   |   |\r\n"
            + "2.0        sine          || |  |   |    |    |     |     |\r\n"
            + "1.0        cosine        ||  |    |      |      |    |  ||\r\n"
            + "0.0        equal         |   |   |   |   |   |   |   |   |\r\n"
            + "-1.0        cosine        ||  |    |      |      |    |  ||\r\n"
            + "-2.0       -sine          |     |     |    |    |   |  | ||\r\n"
            + "-3.0        equal         |   |   |   |   |   |   |   |   |\r\n"
            + "The most efficient distribution (best accuracy for a given number of \r\n"
            + "vortices) is usually the cosine (1.0)"
    )
    private float Bspace = 1.0f;  // Default to cosine spacing

    @AvlEditorField(text="Ydupl",
        help="Y position of X-Z plane about which the current surface is\r\n"
            + "reflected to make the duplicate geometric-image surface.")
    private float Ydupl;

    @AvlEditorField(text="Translate dX",
        help="offset added on to all X value in this body")
    private float dX;

    @AvlEditorField(text="Translate dY",
        help="offset added on to all Y values in this body")
    private float dY;

    @AvlEditorField(text="Translate dZ",
        help="offset added on to all Z values in this body")
    private float dZ;

    @AvlEditorField(text="Length",
        help="Length of the body in model units")
    private float length = 0.3f;

    @AvlEditorField(text="BFILE",
        help="the shape of the body as an 'airfoil' file\r\n"
            + "which gives the top or side view of the body, which is\r\n"
            + "assumed to have a round cross-section")
    private String BFILE;

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the NBody
     */
    public int getNbody() {
        return Nbody;
    }

    /**
     * @return the Bspace
     */
    public float getBspace() {
        return Bspace;
    }

    /**
     * @return the Ydupl
     */
    public float getYdupl() {
        return Ydupl;
    }

    /**
     * @return the dX
     */
    public float getdX() {
        return dX;
    }

    /**
     * @return the dY
     */
    public float getdY() {
        return dY;
    }

    /**
     * @return the dZ
     */
    public float getdZ() {
        return dZ;
    }

    /**
     * @return the BFILE
     */
    public String getBFILE() {
        return BFILE;
    }

    /**
     * @return the profile points
     */
    @XmlElementWrapper
    @XmlElement(name = "profilePoint")
    @AvlEditorNode(name = "profile")
    public ArrayList<BodyProfilePoint> getProfilePoints() {
        return profilePoints;
    }

    /**
     * Creates a new profile point with interpolated values.
     * @return the new profile point
     */
    public BodyProfilePoint createProfilePoint() {
        BodyProfilePoint point = new BodyProfilePoint();

        if (profilePoints.isEmpty()) {
            point.setX(0.5f);
            point.setRadius(0.1f);
        } else {
            // Find a gap to insert - use midpoint of largest gap
            profilePoints.sort(Comparator.comparingDouble(BodyProfilePoint::getX));
            float maxGap = 0;
            int insertIdx = 0;
            float newX = 0.5f;
            float newRadius = 0.1f;

            for (int i = 0; i < profilePoints.size() - 1; i++) {
                float gap = profilePoints.get(i + 1).getX() - profilePoints.get(i).getX();
                if (gap > maxGap) {
                    maxGap = gap;
                    insertIdx = i;
                    newX = (profilePoints.get(i).getX() + profilePoints.get(i + 1).getX()) / 2;
                    newRadius = (profilePoints.get(i).getRadius() + profilePoints.get(i + 1).getRadius()) / 2;
                }
            }
            point.setX(newX);
            point.setRadius(newRadius);
        }

        point.setParentBody(this);
        profilePoints.add(point);
        profilePoints.sort(Comparator.comparingDouble(BodyProfilePoint::getX));
        return point;
    }

    /**
     * Initialize parent references after deserialization
     */
    public void initProfilePointParents() {
        for (BodyProfilePoint point : profilePoints) {
            point.setParentBody(this);
        }
    }

    /**
     * Initialize with default profile if empty
     */
    public void initDefaultProfile() {
        if (profilePoints.isEmpty()) {
            // Simple fuselage shape with realistic proportions
            // X is normalized (0-1), radius is in model units
            addProfilePoint(0.00f, 0.000f);   // Nose tip
            addProfilePoint(0.10f, 0.020f);   // Nose cone
            addProfilePoint(0.30f, 0.030f);   // Max radius
            addProfilePoint(0.70f, 0.030f);   // Main body
            addProfilePoint(0.90f, 0.018f);   // Tail taper
            addProfilePoint(1.00f, 0.006f);   // Tail tip
        }
    }

    private void addProfilePoint(float x, float radius) {
        BodyProfilePoint point = new BodyProfilePoint(x, radius);
        point.setParentBody(this);
        profilePoints.add(point);
    }

    /**
     * Import profile points from parsed BFILE data
     * @param points array of (x, radius) pairs
     */
    public void importProfilePoints(float[][] points) {
        profilePoints.clear();
        for (float[] p : points) {
            addProfilePoint(p[0], p[1]);
        }
    }

    @Override
    public String toString() {
        return this.getName();
    }
    
    @Override
    public void writeAVLData(OutputStream out) {
        PrintStream ps = new PrintStream(out);
        ps.print("BODY\n");
        ps.printf(locale, "%1$s\n", this.getName());

        // Use sensible defaults if values are 0
        int nbodyToUse = this.getNbody() > 0 ? this.getNbody() : 20;
        float bspaceToUse = this.getBspace() != 0 ? this.getBspace() : 1.0f;

        ps.printf(locale, "#Nbody  Bspace\n" + formatInteger(1) + formatFloat(1,2),
                nbodyToUse, bspaceToUse);

        ps.print("\n");

        // Write YDUPLICATE if body needs mirroring (Ydupl set or body is off-center)
        if (this.getYdupl() != 0.0f || this.getdY() != 0.0f) {
            ps.print("YDUPLICATE\n");
            ps.printf(locale, formatFloat(1) + "\n", this.getYdupl());
        }

        if (this.getdX() != 0 ||  this.getdY() != 0 || this.getdZ() != 0){
            ps.print("TRANSLATE\n");
            ps.printf(locale, "#dX  dY  dZ\n" + formatFloat(3) + "\n",
                    this.getdX(), this.getdY(), this.getdZ());
        }

        ps.print("BFILE\n");
        ps.print(this.getEffectiveBFILE() + "\n");
    }

    /**
     * Get the effective BFILE name.
     * If BFILE is set (and not "null"), use it. Otherwise generate from body name.
     */
    public String getEffectiveBFILE() {
        if (BFILE != null && !BFILE.isEmpty() && !BFILE.equals("null")) {
            return BFILE;
        }
        // Generate filename from body name
        String safeName = this.getName().replaceAll("[^a-zA-Z0-9_-]", "_");
        return safeName + "_body.dat";
    }

    /**
     * @param Nbody the Nbody to set
     */
    public void setNbody(int Nbody) {
        this.Nbody = Nbody;
    }

    /**
     * @param Bspace the Bspace to set
     */
    public void setBspace(float Bspace) {
        this.Bspace = Bspace;
    }

    /**
     * @param Ydupl the Ydupl to set
     */
    public void setYdupl(float Ydupl) {
        this.Ydupl = Ydupl;
    }

    /**
     * @param dX the dX to set
     */
    public void setdX(float dX) {
        this.dX = dX;
    }

    /**
     * @param dY the dY to set
     */
    public void setdY(float dY) {
        this.dY = dY;
    }

    /**
     * @param dZ the dZ to set
     */
    public void setdZ(float dZ) {
        this.dZ = dZ;
    }

    /**
     * @param BFILE the BFILE to set
     */
    public void setBFILE(String BFILE) {
        this.BFILE = BFILE;
    }

    /**
     * @return the length
     */
    public float getLength() {
        return length;
    }

    /**
     * @param length the length to set
     */
    public void setLength(float length) {
        this.length = length;
    }

    public ArrayList<Mass> getMassesRecursive(){
        return new ArrayList<Mass>(getMasses());
    }
}
