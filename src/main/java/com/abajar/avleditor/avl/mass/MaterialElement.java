/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.avl.mass;

import com.abajar.avleditor.avl.geometry.VolumeCentroid;
import com.abajar.avleditor.material.Material;
import com.abajar.avleditor.material.Materials;
import com.abajar.avleditor.view.annotations.AvlEditorField;
import com.abajar.avleditor.view.annotations.AvlEditorReadOnly;
import java.util.Locale;

/**
 * An element with a shape, and therefore a weight of its own: a surface or a body. What it is made of
 * says how much it weighs, so the editor does not have to be told.
 *
 * Two figures, because a model aircraft part is built two ways at once. What it is **filled** with is
 * weighed by volume — balsa, foam, a solid print — scaled by how much of the enclosed volume the
 * structure actually occupies: a built-up wing of ribs, spars and sheeting is mostly air. What it is
 * **covered** with is weighed by the area it covers: a 0.2 mm carbon laminate weighs about 310 g per
 * square metre whatever it is wrapped around, so its thickness reaches the aircraft through the area
 * rather than through the volume, and a single density could only approximate it.
 *
 * The density and the areal weight are **stored on the element**, not looked up from the library by
 * name. The name is the label; the numbers are what the model weighs. A model has to weigh the same on
 * a machine whose library does not have that material, or whose library was edited since.
 */
public abstract class MaterialElement extends MassObject {

    protected static final Locale locale = Mass.locale;

    /** A built-up surface of ribs, spars and sheeting occupies about this much of its own volume. */
    public static final float DEFAULT_SURFACE_FILL_PERCENT = 15f;
    /** A fuselage is emptier still: formers, longerons and skin around a hollow. */
    public static final float DEFAULT_BODY_FILL_PERCENT = 12f;

    public static final String NO_SKIN = "None";

    @AvlEditorField(text = "Material",
        optionsFrom = "materialOptions",
        help = "What this part is filled with. Choosing one writes its density below, and that is what\n"
            + "the model keeps: a model weighs the same on a machine whose material list differs.\n"
            + "Edit the list from Edit > Materials."
    )
    private String materialName = "Balsa, medium";

    @AvlEditorField(text = "Density (g/cm3)",
        help = "Weight of the material itself. Set by choosing a material, and editable on its own for\n"
            + "a part whose material is not in the list."
    )
    private float materialDensity = 0.16f;

    @AvlEditorField(text = "Fill (%)",
        help = "How much of the volume this part encloses is actually material: 100 for a solid block,\n"
            + "15 or so for a built-up wing of ribs, spars and sheeting, a few per cent for a hollow\n"
            + "moulded shell. 0 weighs nothing."
    )
    private float fillPercent = DEFAULT_SURFACE_FILL_PERCENT;

    @AvlEditorField(text = "Skin",
        optionsFrom = "skinOptions",
        help = "What this part is covered with. A skin is weighed by the area it covers, so its\n"
            + "thickness is what the entry states: 'Carbon skin 0.20 mm' weighs 310 g per square metre.\n"
            + "'None' for a part with no covering worth counting."
    )
    private String skinName = NO_SKIN;

    @AvlEditorField(text = "Skin weight (g/m2)",
        help = "Weight of one covering of the skin. Set by choosing a skin, and editable on its own."
    )
    private float skinArealWeight = 0f;

    /** The volume of the side this element defines, and where it balances. */
    public abstract VolumeCentroid definedSideVolume();

    /** The area of the side this element defines, as air sees it: both faces of a wing, the outside of
      * a body. What a skin covers. */
    public abstract float wettedArea();

    /**
     * The volume the element's stored mass has to account for.
     *
     * A mirrored element stores one mass and the mirror carries the other half, so the stored mass
     * weighs the side the element defines — unless it sits on the plane of symmetry, where there is no
     * mirror and the one mass weighs the whole element. The same rule decides the skin's area, and the
     * same rule again decides the volume the masses generated from materials account for: assuming a
     * mirror that never appears loses exactly that element's other half.
     */
    public float massVolume() {
        VolumeCentroid side = definedSideVolume();
        return side.getVolume() * sidesOneMassStandsFor(side);
    }

    public float massWettedArea() {
        return wettedArea() * sidesOneMassStandsFor(definedSideVolume());
    }

    private float sidesOneMassStandsFor(VolumeCentroid side) {
        Float planeY = mirrorPlaneY();
        if (planeY == null || side.isEmpty()) return 1f;
        boolean offThePlane = Math.abs(side.getY() - planeY) > MIRROR_TOLERANCE;
        return offThePlane ? 1f : 2f;
    }

    /**
     * What this element weighs, in the model's mass unit: its filling plus its covering.
     *
     * A cubic metre of a material at 1 g/cm³ weighs 1000 kg, and a square metre of a skin at 1000 g/m²
     * weighs 1 kg — the two conversions this rests on, both pinned by MaterialWeightCheck.
     */
    public float materialWeight() {
        float fill = Math.max(0f, Math.min(100f, fillPercent)) / 100f;
        float filling = massVolume() * 1000f * materialDensity * fill;
        float covering = massWettedArea() * skinArealWeight / 1000f;
        return filling + covering;
    }

    /**
     * A mass created on this element starts with the weight its material says, so weighing a wing is
     * choosing what it is made of rather than typing a number. It is a starting point like the
     * position: the mass keeps it, and editing it sticks.
     */
    @Override
    public Mass createMass() {
        Mass mass = super.createMass();
        mass.setMass(materialWeight());
        return mass;
    }

    @AvlEditorReadOnly(text = "Weight from material",
        help = "What the material, the fill and the skin come to for this element's own volume and area.\n"
            + "A mass created here starts at this weight, and 'Masses from materials' sets every\n"
            + "element's mass from it."
    )
    public String getMaterialWeightSummary() {
        VolumeCentroid side = definedSideVolume();
        if (side.isEmpty()) return "no volume yet";
        float sides = sidesOneMassStandsFor(side);
        String bothSides = sides > 1f ? " (both sides)" : "";
        return String.format(locale,
            "%.4f kg = %.6f m3%s x %.3f g/cm3 x %.0f%% + %.4f m2 x %.0f g/m2",
            materialWeight(), massVolume(), bothSides, materialDensity, fillPercent,
            massWettedArea(), skinArealWeight);
    }

    /** The filling materials the library offers, for the properties table's dropdown. */
    public String[] materialOptions() {
        return Materials.library().solidNames().toArray(new String[0]);
    }

    /** The skins the library offers, 'None' included. */
    public String[] skinOptions() {
        return Materials.library().skinNames().toArray(new String[0]);
    }

    public String getMaterialName() {
        return materialName;
    }

    /**
     * Choosing a material writes its density onto the element. A name the library does not have leaves
     * the density alone: a model that names a material this machine has never heard of keeps its weight.
     */
    public void setMaterialName(String materialName) {
        this.materialName = materialName;
        Material material = Materials.library().find(materialName);
        if (material != null) this.materialDensity = material.getDensity();
    }

    public float getMaterialDensity() {
        return materialDensity;
    }

    public void setMaterialDensity(float materialDensity) {
        this.materialDensity = Math.max(0f, materialDensity);
    }

    public float getFillPercent() {
        return fillPercent;
    }

    public void setFillPercent(float fillPercent) {
        this.fillPercent = Math.max(0f, Math.min(100f, fillPercent));
    }

    public String getSkinName() {
        return skinName;
    }

    /** Choosing a skin writes its areal weight onto the element; 'None' clears it. */
    public void setSkinName(String skinName) {
        this.skinName = skinName;
        if (skinName == null || NO_SKIN.equals(skinName)) {
            this.skinArealWeight = 0f;
            return;
        }
        Material material = Materials.library().find(skinName);
        if (material != null) this.skinArealWeight = material.getArealWeight();
    }

    public float getSkinArealWeight() {
        return skinArealWeight;
    }

    public void setSkinArealWeight(float skinArealWeight) {
        this.skinArealWeight = Math.max(0f, skinArealWeight);
    }
}
