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

import com.abajar.avleditor.avl.AVL;
import com.abajar.avleditor.view.annotations.AvlEditor;
import com.abajar.avleditor.view.annotations.AvlEditorField;
import com.abajar.avleditor.view.annotations.AvlEditorNode;
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS;
import java.beans.Transient;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import org.eclipse.persistence.oxm.annotations.XmlPath;

/**
 *
 * @author Hugo
 */
@XmlRootElement(name="CRRCSim_airplane")
@XmlType(propOrder={"description","aero","changelog","config","graphics","centerOfMass","wheels"})
@AvlEditor(buttons={ENABLE_BUTTONS.ADD_CHANGELOG, ENABLE_BUTTONS.ADD_COLLISION_POINT})
public class CRRCSim implements Serializable{
    static final long serialVersionUID = 5069158912723554271L;

    private transient CenterOfMass centerOfMass;
    private transient Path originPath;

    /**
     * @return the config
     */
    @AvlEditorNode
    public Config getConfig() {
        return config;
    }

    /**
     * @param config the config to set
     */
    public void setConfig(Config config) {
        this.config = config;
    }

    public Change createChange() {
        Change change = new Change();
        this.getChangelog().add(change);
        return change;
    }

    public Battery createBattery() {
        Battery battery = new Battery();
        this.config.getPower().getBateries().add(null);
        return battery;
    }

    @XmlElement(name="CG")
    public CenterOfMass getCenterOfMass(){
        return this.centerOfMass;
    }

    private String wheelsVersion = "1";
    private String wheelsUnits = "1";
    /**
     * @return the wells
     */
    @AvlEditorNode(name = "Collision points")
    @XmlElementWrapper(name = "wheels")
    @XmlElement(name="wheel")
    public ArrayList<Wheel> getWheels() {
        return wheels;
    }

    @XmlTransient
    public int internalVersion = 21;

    /**
     * @param wells the wells to set
     */
    public void setWheels(ArrayList<Wheel> wheels) {
        this.wheels = wheels;
    }

    public Object createWheel() {
        Wheel wheel = new Wheel();
        this.wheels.add(wheel);
        return wheel;
    }

    /**
     * @return the wheelsVersion
     */
    @XmlPath("wheels/@version")
    public String getWheelsVersion() {
        return wheelsVersion;
    }

    /**
     * @param wheelsVersion the wheelsVersion to set
     */
    public void setWheelsVersion(String wheelsVersion) {
        this.wheelsVersion = wheelsVersion;
    }



    /**
    <CRRCSim_airplane version="2">
  <description>
    <en>
        This plane has been automatically converted from superzagi.air.
        Please update this text if you know more about it.
    </en>
  </description>
  <changelog>
    <change>
      <date>Unknown</date>
      <author>CRRCSim 0.9.5</author>
      <en>Automatically converted from .air file.</en>
    </change>
    <change>
      <date>Please write date.</date>
      <author>Please write your name and email.</author>
      <en>Please write down what you changed.</en>
    </change>
  </changelog>
     <aero version="1" units="0">
    <ref chord="0.551667" span="6.55" area="3.61111" speed="19.685" />
    <misc Alpha_0="0.0349066" eta_loc="0.3" CG_arm="0.25" span_eff="0.95" />
    <m Cm_0="-0.0112663" Cm_a="-0.575335" Cm_q="-11.4975" Cm_de="-0.597537" />
    <lift CL_0="0.563172" CL_max="1.1" CL_min="-0.6" CL_a="5.5036" CL_q="7.50999"
       CL_de="0.162" CL_drop="0.5" CL_CD0="0" />
    <drag CD_prof="0.02" Uexp_CD="-0.5" CD_stall="0" CD_CLsq="0.01" CD_AIsq="0"
       CD_ELsq="0" />
    <Y CY_b="-0.41561" CY_p="-0.42382" CY_r="0.29754" CY_dr="0" CY_da="-0.13589" />
    <l Cl_b="-0.250926" Cl_p="-0.611798" Cl_r="0.139581" Cl_dr="0"
       Cl_da="-0.00307921" />
    <n Cn_b="0.0567069" Cn_p="-0.0740898" Cn_r="-0.0687755" Cn_dr="0"
       Cn_da="0.0527143" />
  </aero>
  <config version="1">
    <descr_long>
      <en>Automatically converted from sovereign.air.</en>
    </descr_long>
    <descr_short>
      <en>default</en>
    </descr_short>
    <mass_inertia version="1" units="0" Mass="0.0450782" I_xx="0.0664259"
       I_yy="0.0162819" I_zz="0.081474" I_xz="0.000771958" />
    <sound version="1">
      <sample filename="" type="0" pitchfactor="0" maxvolume="1" />
    </sound>
  </config>
       */
    
    private String version = "2";
    private final Description description = new Description();

    private Changelog changelog1 = new Changelog();
    private transient Aero aero;
    private final AVL avl;
    private Config config = new Config();
    private ArrayList<Wheel> wheels = new ArrayList<Wheel>();
    private Graphics graphics = new Graphics();

    protected CRRCSim(AVL avl){
        this.avl = avl;
        this.centerOfMass = new CenterOfMass(this);
    }

    protected CRRCSim(){
        this(new AVL());
    }

    public void calculate(String avlPath, Path originPath) throws IOException, InterruptedException, Exception{
        this.aero = new AeroFactory().createFromAvl(avlPath, this.avl, originPath);
        this.config.setMass_inertiaFromMasses(avl.getGeometry().getMassesRecursive(), avl.getLengthUnit(), avl.getMassUnit());
    }

    @Override
    public String toString(){
        return "Airplane";
    }

    /**
     * @return the description
     */
    @XmlElement
    public Description getDescription() {
        return description;
    }
    
    /**
     * @return the avl
     */
    @AvlEditorNode
    public AVL getAvl() {
        return avl;
    }

    /**
     * @return the wheelsUnit
     */
    @XmlPath("wheels/@units")
    public String getWheelsUnits() {
        return wheelsUnits;
    }

    /**
     * @param wheelsUnit the wheelsUnit to set
     */
    public void setWheelsUnits(String wheelsUnits) {
        this.wheelsUnits = wheelsUnits;
    }

    /**
     * @return the graphics
     */
    @XmlElement
    @AvlEditorNode
    public Graphics getGraphics() {
        return graphics;
    }

    /**
     * @param graphics the graphics to set
     */
    public void setGraphics(Graphics graphics) {
        this.graphics = graphics;
    }

    void setCenterOfMass(CenterOfMass centerOfMass) {
        this.centerOfMass = centerOfMass;
    }

    void setOriginPath(Path toPath) {
        this.originPath = toPath;
    }

    @XmlTransient
    public Path getOriginPath(){
        return this.originPath;
    }
    
    public static class Description implements Serializable{
        private String en;

        public Description() {
        }

        @Override
        public String toString(){
            return "description";
        }

        /**
         * @return the en
         */
        public String getEn() {
            return en;
        }

        /**
         * @param en the en to set
         */
        public void setEn(String en) {
            this.en = en;
        }
    }
    
    /**
     * @return the version
     */
    @XmlAttribute
    public String getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(String version) {
        this.version = version;
    }
    
    /**
     * @return the changelog
     */
    @XmlElement(name="changelog")
    @AvlEditorNode(name="Changes")
    public Changelog getChangelog() {
        return changelog1;
    }

    //After migrate some old files doesn't have changelog setted
    //calling this will fix the null pointer exception
    protected void fixChangelog(){
      changelog1 = new Changelog();
    }

    /**
     * @return the aero
     */

    @XmlElement(name="aero")
    public Aero getAero() {
        return aero;
    }

    
}
