/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.avl;

import com.abajar.avleditor.avl.mass.Mass;
import com.abajar.avleditor.avl.mass.MassObject;
import com.abajar.avleditor.avl.geometry.Body;
import com.abajar.avleditor.avl.geometry.Control;
import com.abajar.avleditor.avl.geometry.Section;
import com.abajar.avleditor.avl.geometry.Surface;
import com.abajar.avleditor.view.annotations.AvlEditor;
import com.abajar.avleditor.view.annotations.AvlEditorField;
import com.abajar.avleditor.view.annotations.AvlEditorNode;
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import com.abajar.avleditor.avl.mass.Mass;

/**
 *
 * @author hfreire
 */
@XmlRootElement
@XmlSeeAlso({
    MassObject.class
})
@AvlEditor(buttons={ENABLE_BUTTONS.ADD_SURFACE, ENABLE_BUTTONS.ADD_MASS, ENABLE_BUTTONS.ADD_BODY, ENABLE_BUTTONS.CALCULATE_CG, ENABLE_BUTTONS.AUTO_MASSES_FROM_VOLUME})
public class AVLGeometry extends MassObject implements AVLSerializable{
    final static Logger logger = Logger.getLogger(AVLGeometry.class.getName());
    private static final float EPSILON = 1.0e-8f;
    private static final float DEFAULT_UNIFORM_DENSITY = 1.0f;
    private static final float AIRFOIL_AREA_FACTOR = 0.68f;
    private static final float AIRFOIL_CENTROID_CHORD_FRACTION = 0.42f;
    private static final float DEFAULT_THICKNESS_RATIO = 0.12f;


    @AvlEditorField(text="Name",
        help="Project name"
    )
    private String name = "Geometry";

    @AvlEditorField(text="Mach",
        help="default freestream Mach number for Prandtl-Glauert correction. http://en.wikipedia.org/wiki/Mach_number. 0 should be ok for RC airplanes"
    )
    private float Mach;


    @AvlEditorField(text="iYsym",
        help="Allow you to draw only a part of the airplane\r\n"
            + "and left AVL to mirror about Y axis\r\n"
            + "1 case is symmetric about Y=0, (X-Z plane is a solid wall)\r\n"
            + "-1  case is antisymmetric about Y=0, (X-Z plane is at const. Cp)\r\n"
            + "0  no Y-symmetry is assumed\r\n"
            + "use 0 by default and draw all elements"
    )
    private  int iYsym;

    @AvlEditorField(text="iZsym",
        help="Allow you to draw only a part of the airplane\r\n"
            + "and left AVL to mirror about Z axis"
            + "1  case is symmetric about Z=Zsym, (X-Y plane is a solid wall)\r\n"
            + "-1  case is antisymmetric about Z=Zsym, (X-Y plane is at const. Cp)\r\n"
            + "0  no Z-symmetry is assumed (Zsym ignored)\r\n"
            + "use 0 by default and draw all elements"
    )
    private  int iZsym;

    @AvlEditorField(text="Zsym",
        help="Zsym"
    )
    private float Zsym;


    @AvlEditorField(text="Sref",
        help="reference area used to define all coefficients (CL, CD, Cm, etc)\r\n"
            + "the area of the wing in square units"
    )
    private  float Sref;

    @AvlEditorField(text="Cref",
        help="reference chord used to define pitching moment (Cm).\r\n"
            + "the chord of the wing http://en.wikipedia.org/wiki/Chord_(aircraft)"
    )
    private  float Cref;

    @AvlEditorField(text="Bref",
        help="reference span  used to define roll,yaw moments\r\n"
            + "the wing span http://en.wikipedia.org/wiki/Wingspan"
    )
    private  float Bref;


    @AvlEditorField(text="Xref",
        help="default location about which moments and rotation rates are defined\r\n"
            + "Center of Gravity X axis position"
    )
    private  float Xref;

    @AvlEditorField(text="Yref",
        help="default location about which moments and rotation rates are defined\r\n"
            + "Center of Gravity Y axis position"
    )
    private  float Yref;

    @AvlEditorField(text="Zref",
        help="default location about which moments and rotation rates are defined\r\n"
            + "Center of Gravity Z axis position"
    )
    private  float Zref;

    
    @AvlEditorField(text="CDp",
        help="default profile drag coefficient added to geometry, applied at XYZref\r\n"
            + "http://en.wikipedia.org/wiki/Drag_coefficient\r\n"
            + "0.020 seems to be a common default value"
    )
    private float CDp = 0.02f;
    
    private final ArrayList<Surface> surfaces = new ArrayList<Surface>(){{
        add(new Surface());
    }};
    private final ArrayList<Body> body = new ArrayList<Body>();

    static final long serialVersionUID = 7590357473387179207L;

    
    /**
     * @return the Mach
     */
    public float getMach() {
        return Mach;
    }

    /**
     * @param Mach the Mach to set
     */
    public void setMach(float Mach) {
        this.Mach = Mach;
    }
    
    /**
     * @return the CDp
     */
    public float getCDp() {
        return CDp;
    }

    /**
     * @return the surfaces
     */
    @XmlElementWrapper
    @XmlElement(name="surface")
    @AvlEditorNode(name="surfaces")
    public ArrayList<Surface> getSurfaces() {
        return surfaces;
    }

    /**
     * @return the body
     */
    @XmlElement(name="body")
    @AvlEditorNode(name="bodies")
    public ArrayList<Body> getBodies() {
        return body;
    }

    @Override
    public String toString() {
        return this.name;
    }

    /**
     * @param CDp the CDp to set
     */
    public void setCDp(float CDp) {
        this.CDp = CDp;
    }

    @Override
    public void writeAVLData(OutputStream out) {
        PrintStream ps = new PrintStream(out);
        ps.print("#Created with AVL Editor https://github.com/TLmaK0/avl-crrcsim-editor \n");
        ps.printf(locale, "%1$s\n", this.getName());
        ps.printf(locale, "#Mach\n%1$-19.4g\n", this.getMach());                                                         //0.0                 | Mach
        ps.printf(locale, "#iYsym   iZsym    Zsym\n" + formatInteger(2) + formatFloat(1,3) + "\n", this.getiYsym(), (int)this.getiZsym(), (int)this.getZsym());          //1     0     0.0     | iYsym  iZsym  Zsym
        ps.printf(locale, "#Sref     Cref      Bref\n" + formatFloat(3) + "\n", this.getSref(), this.getCref(), this.getBref());          //4.0   0.4   0.1     | Sref   Cref   Bref
        ps.printf(locale, "#Xref     Yref      Zref\n" + formatFloat(3) + "\n", this.getXref(), this.getYref(), this.getZref());          //0.1   0.0   0.0     | Xref   Yref   Zref
        
        if(this.CDp != 0){
            ps.printf(locale, "#CDp\n%1$-19.4g\n", this.CDp);                                                         //0.020               | CDp  (optional)
        }
        
        for(Surface surf : this.getSurfaces()){
            surf.writeAVLData(out);
        }

        for(Body body : this.getBodies()){
            body.writeAVLData(out);
        }
    }

    public static String formatInteger(int numberOfValues, int startValue){
        String format ="";
        for(int n=startValue; n < startValue + numberOfValues; n++){
            format += "%" + n + "$-8d ";
        }
        return format;
    }

    public static String formatInteger(int numberOfValues){
        return formatInteger(numberOfValues, 1);
    }

    public static String formatFloat(int numberOfValues){
        return formatFloat(numberOfValues, 1);
    }

    public static String formatFloat(int numberOfValues, int startValue){
        String format ="";
        for(int n=startValue; n < startValue + numberOfValues; n++){
            format += "%" + n + "$-9s ";
        }
        return format;
    }

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
     * @return the Zsym
     */
    public float getZsym() {
        return Zsym;
    }

    /**
     * @param Zsym the Zsym to set
     */
    public void setZsym(float Zsym) {
        this.Zsym = Zsym;
    }

    /**
     * @return the iYsym
     */
    public int getiYsym() {
        return iYsym;
    }

    /**
     * @param iYsym the iYsym to set
     */
    public void setiYsym(int iYsym) {
        this.iYsym = iYsym;
    }

    /**
     * @return the iZsym
     */
    public int getiZsym() {
        return iZsym;
    }

    /**
     * @param iZsym the iZsym to set
     */
    public void setiZsym(int iZsym) {
        this.iZsym = iZsym;
    }

    /**
     * @return the Sref
     */
    public float getSref() {
        return Sref;
    }

    /**
     * @param Sref the Sref to set
     */
    public void setSref(float Sref) {
        this.Sref = Sref;
    }

    /**
     * @return the Cref
     */
    public float getCref() {
        return Cref;
    }

    /**
     * @param Cref the Cref to set
     */
    public void setCref(float Cref) {
        this.Cref = Cref;
    }

    /**
     * @return the Bref
     */
    public float getBref() {
        return Bref;
    }

    /**
     * @param Bref the Bref to set
     */
    public void setBref(float Bref) {
        this.Bref = Bref;
    }

    /**
     * @return the Xref
     */
    public float getXref() {
        return Xref;
    }

    /**
     * @param Xref the Xref to set
     */
    public void setXref(float Xref) {
        this.Xref = Xref;
    }

    /**
     * @return the Yref
     */
    public float getYref() {
        return Yref;
    }

    /**
     * @param Yref the Yref to set
     */
    public void setYref(float Yref) {
        this.Yref = Yref;
    }

    /**
     * @return the Zref
     */
    public float getZref() {
        return Zref;
    }

    /**
     * @param Zref the Zref to set
     */
    public void setZref(float Zref) {
        this.Zref = Zref;
    }

    int getAileronPosition() throws Exception {
        return getControlPosition(Control.AILERON);
    }

    int getElevatorPosition() throws Exception {
        return getControlPosition(Control.ELEVATOR);
    }

    int getRudderPosition() throws Exception {
        return getControlPosition(Control.RUDDER);
    }

    private int getControlPosition(int controlType) throws Exception{
        // AVL assigns control positions by unique NAME order, not by type
        // So we need to find the position of the first control with the given type
        // based on where its NAME appears in the list of unique names
        java.util.LinkedHashSet<String> uniqueNames = new java.util.LinkedHashSet<>();
        String targetName = null;

        for(Surface surface: this.getSurfaces()){
            for(Section section: surface.getSections()){
                for(Control control: section.getControls()){
                    String name = control.getName();
                    if (!uniqueNames.contains(name)) {
                        if (control.getType() == controlType && targetName == null) {
                            targetName = name;
                        }
                        uniqueNames.add(name);
                    }
                }
            }
        }

        if (targetName == null) {
            return -1;
        }

        int position = 0;
        for (String name : uniqueNames) {
            if (name.equals(targetName)) {
                logger.log(Level.FINE, "Control type {0} ({1}) found at position {2}",
                    new Object[]{controlType, targetName, position});
                return position;
            }
            position++;
        }
        return -1;
    }

    public Surface createSurface() {
        Surface surface = new Surface();
        this.getSurfaces().add(surface);
        return surface;
    }

    public Body createBody() {
        Body body = new Body();
        // Generate unique body name and BFILE
        int bodyNum = this.getBodies().size() + 1;
        body.setName("body" + bodyNum);
        body.setBFILE("body" + bodyNum + ".dat");
        // Initialize with default profile
        body.initDefaultProfile();
        this.getBodies().add(body);
        return body;
    }

    public ArrayList<Mass> getMassesRecursive() {
        ArrayList<Mass> masses = new ArrayList<Mass>(getMasses());
        for(Surface surface: getSurfaces()){
          masses.addAll(surface.getMassesRecursive());
        }

        for(Body body: getBodies()){
          masses.addAll(body.getMassesRecursive());
        }

        return masses;
    }

    public boolean calculateCenterOfMassFromMasses() {
        ArrayList<Mass> masses = getMassesRecursive();
        float totalMass = 0f;
        float xWeighted = 0f;
        float yWeighted = 0f;
        float zWeighted = 0f;

        for (Mass mass : masses) {
            float massValue = mass.getMass();
            totalMass += massValue;
            xWeighted += mass.getX() * massValue;
            yWeighted += mass.getY() * massValue;
            zWeighted += mass.getZ() * massValue;
        }

        if (Math.abs(totalMass) < 1.0e-8f) {
            logger.log(Level.WARNING, "Cannot calculate CG: total mass is zero");
            return false;
        }

        this.setXref(xWeighted / totalMass);
        this.setYref(yWeighted / totalMass);
        this.setZref(zWeighted / totalMass);
        logger.log(Level.INFO, "CG calculated from masses: Xref={0}, Yref={1}, Zref={2}",
                new Object[]{this.getXref(), this.getYref(), this.getZref()});
        return true;
    }

    public boolean autoMassesFromVolume() {
        float currentTotalMass = getTotalMass(getMassesRecursive());
        ArrayList<VolumeDescriptor> descriptors = new ArrayList<VolumeDescriptor>();
        float totalVolume = 0f;

        for (Surface surface : getSurfaces()) {
            VolumeCentroid definedSideVolume = estimateSurfaceDefinedSideVolume(surface);
            if (definedSideVolume.volume > EPSILON) {
                if (surface.isSymmetric()) {
                    VolumeCentroid mirroredSideVolume = mirrorAcrossY(definedSideVolume, 0f);
                    String baseName = "auto mass " + surface.getName();
                    descriptors.add(new VolumeDescriptor(surface, formatAutoMassName(baseName, definedSideVolume.getY()), definedSideVolume));
                    descriptors.add(new VolumeDescriptor(surface, formatAutoMassName(baseName, mirroredSideVolume.getY()), mirroredSideVolume));
                    totalVolume += definedSideVolume.volume + mirroredSideVolume.volume;
                } else {
                    descriptors.add(new VolumeDescriptor(surface, "auto mass " + surface.getName(), definedSideVolume));
                    totalVolume += definedSideVolume.volume;
                }
            }
        }

        for (Body bodyPart : getBodies()) {
            VolumeCentroid bodyDefinedSideVolume = estimateBodyDefinedSideVolume(bodyPart);
            if (bodyDefinedSideVolume.volume > EPSILON) {
                if (isBodyDuplicated(bodyPart)) {
                    float mirrorPlaneY = bodyPart.getYdupl();
                    VolumeCentroid mirroredSideVolume = mirrorAcrossY(bodyDefinedSideVolume, mirrorPlaneY);
                    String baseName = "auto mass " + bodyPart.getName();
                    descriptors.add(new VolumeDescriptor(bodyPart, formatAutoMassName(baseName, bodyDefinedSideVolume.getY()), bodyDefinedSideVolume));
                    descriptors.add(new VolumeDescriptor(bodyPart, formatAutoMassName(baseName, mirroredSideVolume.getY()), mirroredSideVolume));
                    totalVolume += bodyDefinedSideVolume.volume + mirroredSideVolume.volume;
                } else {
                    descriptors.add(new VolumeDescriptor(bodyPart, "auto mass " + bodyPart.getName(), bodyDefinedSideVolume));
                    totalVolume += bodyDefinedSideVolume.volume;
                }
            }
        }

        if (totalVolume <= EPSILON || descriptors.isEmpty()) {
            logger.log(Level.WARNING, "Auto masses from volume failed: zero total volume");
            return false;
        }

        clearAllMasses();

        float uniformDensity;
        if (currentTotalMass > EPSILON) {
            uniformDensity = currentTotalMass / totalVolume;
        } else {
            uniformDensity = DEFAULT_UNIFORM_DENSITY;
        }

        for (VolumeDescriptor descriptor : descriptors) {
            Mass mass = descriptor.owner.createMass();
            mass.setName(descriptor.name);
            mass.setMass(descriptor.volumeCentroid.volume * uniformDensity);
            mass.setX(descriptor.volumeCentroid.getX());
            mass.setY(descriptor.volumeCentroid.getY());
            mass.setZ(descriptor.volumeCentroid.getZ());
        }

        logger.log(Level.INFO, "Auto masses generated from volume. density={0}, totalVolume={1}, totalMass={2}",
                new Object[]{uniformDensity, totalVolume, getTotalMass(getMassesRecursive())});
        return true;
    }

    private void clearAllMasses() {
        getMasses().clear();

        for (Surface surface : getSurfaces()) {
            surface.getMasses().clear();
            for (Section section : surface.getSections()) {
                section.getMasses().clear();
                for (Control control : section.getControls()) {
                    control.getMasses().clear();
                }
            }
        }

        for (Body bodyPart : getBodies()) {
            bodyPart.getMasses().clear();
        }
    }

    private float getTotalMass(ArrayList<Mass> masses) {
        float total = 0f;
        for (Mass mass : masses) {
            total += mass.getMass();
        }
        return total;
    }

    private VolumeCentroid estimateSurfaceDefinedSideVolume(Surface surface) {
        VolumeCentroid definedSideVolume = new VolumeCentroid();
        ArrayList<Section> sections = surface.getSections();
        if (sections.size() < 2) {
            return definedSideVolume;
        }

        for (int i = 0; i < sections.size() - 1; i++) {
            Section root = sections.get(i);
            Section tip = sections.get(i + 1);
            float rootChord = Math.max(0f, root.getChord());
            float tipChord = Math.max(0f, tip.getChord());
            float span = (float) Math.hypot(
                tip.getYle() - root.getYle(),
                tip.getZle() - root.getZle()
            );

            if (span <= EPSILON || (rootChord <= EPSILON && tipChord <= EPSILON)) {
                continue;
            }

            float averageChord = 0.5f * (rootChord + tipChord);
            float averageThicknessRatio = 0.5f
                * (estimateSectionThicknessRatio(root) + estimateSectionThicknessRatio(tip));

            float segmentVolume = AIRFOIL_AREA_FACTOR
                * averageThicknessRatio
                * averageChord
                * averageChord
                * span;

            if (segmentVolume <= EPSILON) {
                continue;
            }

            float x = 0.5f * (root.getXle() + tip.getXle())
                + AIRFOIL_CENTROID_CHORD_FRACTION * averageChord
                + surface.getdX();
            float y = 0.5f * (root.getYle() + tip.getYle()) + surface.getdY();
            float z = 0.5f * (root.getZle() + tip.getZle()) + surface.getdZ();

            definedSideVolume.add(segmentVolume, x, y, z);
        }
        return definedSideVolume;
    }

    private float estimateSectionThicknessRatio(Section section) {
        String naca = section.getNACA();
        if (naca == null) {
            return DEFAULT_THICKNESS_RATIO;
        }

        String digits = naca.replaceAll("[^0-9]", "");
        if (digits.length() < 2) {
            return DEFAULT_THICKNESS_RATIO;
        }

        try {
            int thicknessPercent = Integer.parseInt(digits.substring(digits.length() - 2));
            float ratio = thicknessPercent / 100f;
            if (ratio > EPSILON) {
                return ratio;
            }
        } catch (NumberFormatException ignored) {
        }
        return DEFAULT_THICKNESS_RATIO;
    }

    private VolumeCentroid estimateBodyDefinedSideVolume(Body bodyPart) {
        VolumeCentroid definedSideVolume = new VolumeCentroid();
        ArrayList<com.abajar.avleditor.avl.geometry.BodyProfilePoint> points = bodyPart.getProfilePoints();
        if (points.size() < 2 || bodyPart.getLength() <= EPSILON) {
            return definedSideVolume;
        }

        ArrayList<com.abajar.avleditor.avl.geometry.BodyProfilePoint> sortedPoints =
            new ArrayList<com.abajar.avleditor.avl.geometry.BodyProfilePoint>(points);
        java.util.Collections.sort(sortedPoints, new java.util.Comparator<com.abajar.avleditor.avl.geometry.BodyProfilePoint>() {
            @Override
            public int compare(com.abajar.avleditor.avl.geometry.BodyProfilePoint a, com.abajar.avleditor.avl.geometry.BodyProfilePoint b) {
                return Float.compare(a.getX(), b.getX());
            }
        });

        for (int i = 0; i < sortedPoints.size() - 1; i++) {
            com.abajar.avleditor.avl.geometry.BodyProfilePoint p1 = sortedPoints.get(i);
            com.abajar.avleditor.avl.geometry.BodyProfilePoint p2 = sortedPoints.get(i + 1);

            float x1 = p1.getX();
            float x2 = p2.getX();
            if (x2 <= x1) {
                continue;
            }

            float length = (x2 - x1) * bodyPart.getLength();
            float r1 = Math.max(0f, p1.getRadius());
            float r2 = Math.max(0f, p2.getRadius());
            float segmentVolume = (float) (Math.PI * length * (r1 * r1 + r1 * r2 + r2 * r2) / 3.0);
            if (segmentVolume <= EPSILON) {
                continue;
            }

            float denominator = 4f * (r1 * r1 + r1 * r2 + r2 * r2);
            if (Math.abs(denominator) <= EPSILON) {
                continue;
            }

            float centroidFromSegmentStart = length * (r1 * r1 + 2f * r1 * r2 + 3f * r2 * r2) / denominator;
            float x = bodyPart.getdX() + x1 * bodyPart.getLength() + centroidFromSegmentStart;
            float y = bodyPart.getdY();
            float z = bodyPart.getdZ();
            definedSideVolume.add(segmentVolume, x, y, z);
        }
        return definedSideVolume;
    }

    private boolean isBodyDuplicated(Body bodyPart) {
        return bodyPart.getYdupl() != 0f || bodyPart.getdY() != 0f;
    }

    private VolumeCentroid mirrorAcrossY(VolumeCentroid source, float mirrorPlaneY) {
        VolumeCentroid mirrored = new VolumeCentroid();
        if (source.volume <= EPSILON) {
            return mirrored;
        }
        float x = source.getX();
        float y = 2f * mirrorPlaneY - source.getY();
        float z = source.getZ();
        mirrored.add(source.volume, x, y, z);
        return mirrored;
    }

    private String formatAutoMassName(String baseName, float y) {
        if (Math.abs(y) <= EPSILON) {
            return baseName + " center";
        }
        return y > 0f ? baseName + " +Y" : baseName + " -Y";
    }

    private static class VolumeCentroid {
        private float volume;
        private float xMoment;
        private float yMoment;
        private float zMoment;

        void add(float deltaVolume, float x, float y, float z) {
            volume += deltaVolume;
            xMoment += deltaVolume * x;
            yMoment += deltaVolume * y;
            zMoment += deltaVolume * z;
        }

        float getX() {
            return volume <= EPSILON ? 0f : xMoment / volume;
        }

        float getY() {
            return volume <= EPSILON ? 0f : yMoment / volume;
        }

        float getZ() {
            return volume <= EPSILON ? 0f : zMoment / volume;
        }
    }

    private static class VolumeDescriptor {
        private final MassObject owner;
        private final String name;
        private final VolumeCentroid volumeCentroid;

        VolumeDescriptor(MassObject owner, String name, VolumeCentroid volumeCentroid) {
            this.owner = owner;
            this.name = name;
            this.volumeCentroid = volumeCentroid;
        }
    }

    /**
     * Validates the geometry for AVL analysis.
     * If Sref, Cref, Bref are 0, attempts to calculate them automatically.
     * @return list of validation errors, empty if valid
     */
    public ArrayList<String> validate() {
        ArrayList<String> errors = new ArrayList<String>();

        // Check surfaces first
        if (surfaces.isEmpty()) {
            errors.add("At least one surface is required");
            return errors;
        }

        for (Surface surface : surfaces) {
            if (surface.getSections().size() < 2) {
                errors.add("Surface '" + surface.getName() + "' needs at least 2 sections");
            }
        }

        // If we have surface errors, don't try to calculate
        if (!errors.isEmpty()) {
            return errors;
        }

        // Auto-calculate if values are 0
        if (Sref <= 0 || Cref <= 0 || Bref <= 0) {
            logger.log(Level.INFO, "Reference values are 0, calculating automatically...");
            calculateReferenceValues();
        }

        // Validate after calculation
        if (Sref <= 0) {
            errors.add("Sref must be > 0 (could not calculate from surfaces)");
        }
        if (Cref <= 0) {
            errors.add("Cref must be > 0 (could not calculate from surfaces)");
        }
        if (Bref <= 0) {
            errors.add("Bref must be > 0 (could not calculate from surfaces)");
        }

        return errors;
    }

    /**
     * Checks if the geometry is valid for AVL analysis.
     * @return true if valid
     */
    public boolean isValid() {
        return validate().isEmpty();
    }

    /**
     * Calculates reference values (Sref, Cref, Bref) from surface geometry.
     * Uses trapezoidal integration for area calculation.
     * Assumes YDUPLICATE symmetry (multiplies by 2).
     */
    public void calculateReferenceValues() {
        float totalArea = 0;
        float maxSpan = 0;

        for (Surface surface : surfaces) {
            ArrayList<Section> sections = surface.getSections();
            if (sections.size() < 2) continue;

            // Sort sections by Yle for proper integration
            ArrayList<Section> sortedSections = new ArrayList<Section>(sections);
            java.util.Collections.sort(sortedSections, new java.util.Comparator<Section>() {
                public int compare(Section s1, Section s2) {
                    return Float.compare(s1.getYle(), s2.getYle());
                }
            });

            // Calculate area using trapezoidal rule
            for (int i = 0; i < sortedSections.size() - 1; i++) {
                Section s1 = sortedSections.get(i);
                Section s2 = sortedSections.get(i + 1);
                float dy = Math.abs(s2.getYle() - s1.getYle());
                float avgChord = (s1.getChord() + s2.getChord()) / 2;
                totalArea += dy * avgChord;
            }

            // Track max span
            for (Section section : sections) {
                float absY = Math.abs(section.getYle());
                if (absY > maxSpan) {
                    maxSpan = absY;
                }
            }
        }

        // Apply YDUPLICATE symmetry (double the values)
        this.Bref = maxSpan * 2;
        this.Sref = totalArea * 2;

        // Mean chord = Area / Span
        if (this.Bref > 0) {
            this.Cref = this.Sref / this.Bref;
        }

        logger.log(Level.INFO, "Calculated reference values: Sref={0}, Cref={1}, Bref={2}",
                   new Object[]{this.Sref, this.Cref, this.Bref});
    }
}
