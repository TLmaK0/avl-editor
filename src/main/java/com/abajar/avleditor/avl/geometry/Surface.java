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

import com.abajar.avleditor.view.annotations.AvlEditorNode;
import com.abajar.avleditor.view.annotations.AvlEditorField;
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS;
import com.abajar.avleditor.view.annotations.AvlEditor;
import javax.xml.bind.annotation.XmlElement;
import com.abajar.avleditor.avl.AVLSerializable;
import com.abajar.avleditor.avl.mass.MassObject;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import javax.xml.bind.annotation.XmlElementWrapper;
import static com.abajar.avleditor.avl.AVLGeometry.formatFloat;
import static com.abajar.avleditor.avl.AVLGeometry.formatInteger;
import com.abajar.avleditor.avl.mass.Mass;

/**
 *
 * @author hfreire
 */
@AvlEditor(buttons={ENABLE_BUTTONS.ADD_SECTION, ENABLE_BUTTONS.DELETE, ENABLE_BUTTONS.ADD_MASS})
public class Surface extends MassObject implements AVLSerializable {
    static final long serialVersionUID = 1138674039288253507L;
    //TODO: NOWAKE
    //TODO: NOALBE
    //TODO: NOLOAD

    //SECTION
    @AvlEditorField(text="surface name",
        help= "Surface name, ex. Wing"
    )
    private String name = "new surface";

    @AvlEditorField(text="Nchord",
        help="number of chordwise horseshoe vortices placed on the surface\r\n"
            + "8 is a good number, more vortices more acurate but more calculation time. http://en.wikipedia.org/wiki/Horseshoe_vortex"
    )
    private int Nchord = 8;

    @AvlEditorField(text="Cspace",
        help="chordwise vortex spacing parameter\r\n"
            + "3.0        equal         |   |   |   |   |   |   |   |   |\r\n"
            + "2.0        sine          || |  |   |    |    |     |     |\r\n"
            + "1.0        cosine        ||  |    |      |      |    |  ||\r\n"
            + "0.0        equal         |   |   |   |   |   |   |   |   |\r\n"
            + "-1.0        cosine        ||  |    |      |      |    |  ||\r\n"
            + "-2.0       -sine          |     |     |    |    |   |  | ||\r\n"
            + "-3.0        equal         |   |   |   |   |   |   |   |   |\r\n"
            + "The most efficient distribution (best accuracy for a given number of \r\n"
            + "vortices) is usually the cosine (1.0) chordwise and spanwise"
    )
    private float Cspace = 1f;

    @AvlEditorField(text="Nspan",
        help="number of spanwise horseshoe vortices placed on the surface\r\n"
            + "8 is a good number, more vortices more acurate but more calculation time. http://en.wikipedia.org/wiki/Horseshoe_vortex"
    )
    private int Nspan = 8;

    @AvlEditorField(text="Sspace",
        help="spanwise vortex spacing parameter\r\n"
            + "3.0        equal         |   |   |   |   |   |   |   |   |\r\n"
            + "2.0        sine          || |  |   |    |    |     |     |\r\n"
            + "1.0        cosine        ||  |    |      |      |    |  ||\r\n"
            + "0.0        equal         |   |   |   |   |   |   |   |   |\r\n"
            + "-1.0        cosine        ||  |    |      |      |    |  ||\r\n"
            + "-2.0       -sine          |     |     |    |    |   |  | ||\r\n"
            + "-3.0        equal         |   |   |   |   |   |   |   |   |\r\n"
            + "The most efficient distribution (best accuracy for a given number of \r\n"
            + "vortices) is usually the cosine (1.0) chordwise and spanwise"
    )
    private float Sspace=1.0f;

    //TODO: COMPONENT
    //TODO: SCALE


    @AvlEditorField(text="Symmetric (Ydupl)",
        help="If checked, the surface is reflected across the X-Z plane (Y=0)\r\n"
            + "to make the duplicate geometric-image surface."
    )
    private boolean symmetric = true;

    // Deprecated: kept for backward compatibility with old YAML files
    // Old files have Ydupl: 0.0 meaning symmetric at Y=0
    @SuppressWarnings("unused")
    private float Ydupl = 0.0f;

    @AvlEditorField(text="Translate dX",
        help="offset added on to all X,Y,Z values in this surface"
    )
    private float dX;

    @AvlEditorField(text="Translate dY",
        help="offset added on to all X,Y,Z values in this surface"
    )
    private float dY;

    @AvlEditorField(text="Translate dZ",
        help="offset added on to all X,Y,Z values in this surface"
    )
    private float dZ;

    @AvlEditorField(text="ANGLE dAinc",
        help="allows convenient changing of the incidence angle\r\n"
            + "of the entire surface without the need to change the Ainc values\r\n"
            + "for all the defining sections.  The rotation is performed about\r\n"
            + "the spanwise axis projected onto the y-z plane"
    )
    private float dAinc;
    
    private final ArrayList<Section> sections = new ArrayList<Section>(){{
      Section root = new Section();
      root.setYle(0);
      root.setChord(1);
      add(root);

      Section tip = new Section();
      tip.setYle(0.5f);
      tip.setChord(0.5f);
      add(tip);
    }};

    @Override
    public String toString() {
        return this.getName();
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
     * @return the Nchord
     */
    public int getNchord() {
        return Nchord;
    }

    /**
     * @param Nchord the Nchord to set
     */
    public void setNchord(int Nchord) {
        this.Nchord = Nchord;
    }

    /**
     * @return the Cspace
     */
    public float getCspace() {
        return Cspace;
    }

    /**
     * @param Cspace the Cspace to set
     */
    public void setCspace(float Cspace) {
        this.Cspace = Cspace;
    }

    /**
     * @return the Nspan
     */
    public int getNspan() {
        return Nspan;
    }

    /**
     * @param Nspan the Nspan to set
     */
    public void setNspan(int Nspan) {
        this.Nspan = Nspan;
    }

    /**
     * @return the Sspace
     */
    public float getSspace() {
        return Sspace;
    }

    /**
     * @param Sspace the Sspace to set
     */
    public void setSspace(float Sspace) {
        this.Sspace = Sspace;
    }

    /**
     * @return the dAinc
     */
    public float getdAinc() {
        return dAinc;
    }

    /**
     * @param dAinc the dAinc to set
     */
    public void setdAinc(float dAinc) {
        this.dAinc = dAinc;
    }

    public Section createSection(){
        Section section = new Section();
        section.setParentSurface(this);
        ArrayList<Section> sects = this.getSections();

        if (sects.size() >= 2) {
            int insertIdx = sects.size() - 1;
            Section prev = sects.get(insertIdx - 1);
            Section next = sects.get(insertIdx);

            // Interpolate position and chord between adjacent sections
            section.setXle((prev.getXle() + next.getXle()) / 2);
            section.setYle((prev.getYle() + next.getYle()) / 2);
            section.setZle((prev.getZle() + next.getZle()) / 2);
            section.setChord((prev.getChord() + next.getChord()) / 2);
            section.setAinc((prev.getAinc() + next.getAinc()) / 2);

            sects.add(insertIdx, section);
        } else {
            sects.add(section);
        }
        return section;
    }

    // Initialize parent references for all sections and controls (call after loading from file)
    public void initSectionParents() {
        for (Section section : this.getSections()) {
            section.setParentSurface(this);
            section.initControlParents();
        }
    }
    /**
     * @return the sections
     */
    @XmlElementWrapper
    @XmlElement(name = "section")
    @AvlEditorNode(name = "sections")
    public ArrayList<Section> getSections() {
        return sections;
    }

    @Override
    public void writeAVLData(OutputStream out) {
        PrintStream ps = new PrintStream(out);
        ps.print("\n#=======================================\n");                             //        SURFACE              | (keyword)
        ps.print("SURFACE\n");                             //        SURFACE              | (keyword)
        ps.printf(locale, "%1$s\n", this.getName());                                        //Main Wing            | surface name string
        
        ps.printf(locale, "#Nchord  Cspace    [Nspan    Sspace]\n" + formatInteger(1) + formatFloat(1,2),
                this.getNchord(), this.getCspace());

        if (this.getNspan() != 0){
            ps.printf(locale,  formatInteger(1) + formatFloat(1,2),
                    this.getNspan(), this.getSspace());                                 //12   1.0  20  -1.5   | Nchord  Cspace   [ Nspan Sspace ]
        }
        ps.print("\n");

        if (this.isSymmetric()) {
            ps.print("YDUPLICATE\n");                              //YDUPLICATE      | (keyword)
            ps.printf(locale, formatFloat(1) + "\n", 0.0f);        //0.0             | Ydupl (always mirror at Y=0)
        }

        if (this.getdX() != 0 ||  this.getdY() != 0 || this.getdZ() != 0){
            ps.print("TRANSLATE\n");                                 //TRANSLATE         |  (keyword)
            ps.printf(locale, "#dX       dY       dZ\n" + formatFloat(3) + "\n",
                    this.getdX(), this.getdY(), this.getdZ());              //10.0  0.0  0.5    | dX  dY  dZ
        }
        
        if (this.getdAinc() != 0){
            ps.print("ANGLE\n");                                         //ANGLE       |  (keyword)
            ps.printf(locale, "#dAinc\n" + formatFloat(1) + "\n", this.getdAinc());                                                     //2.0         | dAinc
        }

        for(Section sect : this.getSections()){
            sect.writeAVLData(out);
        }
    }

    /**
     * @return true if surface is symmetric (mirrored across Y=0)
     */
    public boolean isSymmetric() {
        return symmetric;
    }

    /**
     * @param symmetric whether the surface should be mirrored
     */
    public void setSymmetric(boolean symmetric) {
        this.symmetric = symmetric;
    }

    /**
     * @return the dX
     */
    public float getdX() {
        return dX;
    }

    /**
     * @param dX the dX to set
     */
    public void setdX(float dX) {
        this.dX = dX;
    }

    /**
     * @return the dY
     */
    public float getdY() {
        return dY;
    }

    /**
     * @param dY the dY to set
     */
    public void setdY(float dY) {
        this.dY = dY;
    }

    /**
     * @return the dZ
     */
    public float getdZ() {
        return dZ;
    }

    /**
     * @param dZ the dZ to set
     */
    public void setdZ(float dZ) {
        this.dZ = dZ;
    }

    public ArrayList<Mass> getMassesRecursive() {
        ArrayList<Mass> masses = new ArrayList<Mass>(getMasses());
        for(Section section: getSections()){
          masses.addAll(section.getMassesRecursive());
        }

        return masses;
    }
}
