/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.material;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.representer.Representer;

/**
 * The materials on offer, kept outside any one model.
 *
 * The library is for choosing: what a model keeps is the figure it was given, not the name of a
 * library entry, so a model opened on another machine — or after this list was edited — weighs what it
 * weighed. See {@link com.abajar.avleditor.avl.mass.MaterialElement}.
 *
 * It lives in a file next to the rest of the editor's configuration, seeded with the defaults below the
 * first time it is asked for, and the user can edit it and add to it.
 */
public class MaterialLibrary implements Serializable {
    static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(MaterialLibrary.class.getName());

    private final ArrayList<Material> materials = new ArrayList<Material>();

    public ArrayList<Material> getMaterials() {
        return materials;
    }

    /**
     * Twenty generic materials to start from, and the carbon, glass and film skins whose thickness is
     * what decides their weight. The densities are the usual figures for model aircraft materials;
     * every one of them can be edited, and the notes say what each is.
     */
    public static MaterialLibrary defaults() {
        MaterialLibrary library = new MaterialLibrary();
        ArrayList<Material> m = library.getMaterials();

        m.add(Material.solid("Balsa, light", 0.11f, "Contest-grade sheet and ribs"));
        m.add(Material.solid("Balsa, medium", 0.16f, "The usual sheet balsa; the default for a surface"));
        m.add(Material.solid("Balsa, hard", 0.22f, "Spars and leading edges"));
        m.add(Material.solid("Spruce", 0.45f, "Spars, longerons"));
        m.add(Material.solid("Pine", 0.52f, "Engine bearers"));
        m.add(Material.solid("Obechi", 0.55f, "Veneer over foam cores"));
        m.add(Material.solid("Liteply", 0.45f, "Poplar-core lite plywood"));
        m.add(Material.solid("Birch plywood", 0.68f, "Formers, firewalls"));
        m.add(Material.solid("EPS foam", 0.020f, "Expanded polystyrene, cut cores"));
        m.add(Material.solid("XPS foam / Depron", 0.035f, "Extruded polystyrene sheet"));
        m.add(Material.solid("EPP foam", 0.030f, "Tough foam for slope and combat models"));
        m.add(Material.solid("PVC structural foam", 0.060f, "Divinycell H60 and the like"));
        m.add(Material.solid("Carbon fibre laminate", 1.55f, "Cured epoxy laminate, about 60% fibre"));
        m.add(Material.solid("Glass fibre laminate", 1.90f, "Cured epoxy laminate"));
        m.add(Material.solid("Aramid laminate", 1.35f, "Kevlar in epoxy"));
        m.add(Material.solid("Epoxy resin, cured", 1.15f, "Fillets, joints, filler"));
        m.add(Material.solid("ABS", 1.04f, "Vacuum-formed parts, cowls"));
        m.add(Material.solid("PLA, solid print", 1.24f, "Solid; use the fill percentage for infill"));
        m.add(Material.solid("Nylon PA12", 1.01f, "Sintered parts"));
        m.add(Material.solid("Aluminium 6061", 2.70f, "Undercarriage legs, fittings"));
        m.add(Material.solid("Steel", 7.85f, "Wire, pushrods, ballast"));

        // Skins: the thickness is the point, and it reaches the aircraft through the area it covers.
        m.add(Material.skin("Carbon skin 0.10 mm", 0.10f, 1.55f, "One light layer of cloth in epoxy"));
        m.add(Material.skin("Carbon skin 0.20 mm", 0.20f, 1.55f, "The usual single-layer wing skin"));
        m.add(Material.skin("Carbon skin 0.40 mm", 0.40f, 1.55f, "Two layers, or one heavy cloth"));
        m.add(Material.skin("Carbon skin 0.80 mm", 0.80f, 1.55f, "Structural shell, D-box"));
        m.add(Material.skin("Glass skin 0.10 mm", 0.10f, 1.90f, "Light glass cloth in epoxy"));
        m.add(Material.skin("Glass skin 0.20 mm", 0.20f, 1.90f, "Standard glass wing skin"));
        m.add(Material.skin("Glass skin 0.40 mm", 0.40f, 1.90f, "Two layers"));
        m.add(Material.skin("Covering film", 0.05f, 1.20f, "Heat-shrink film such as Oracover"));
        m.add(Material.solid("None", 0f, "Nothing: state the weight by hand"));

        return library;
    }

    /** The material of that name, or null when the library does not have one. */
    public Material find(String name) {
        if (name == null) return null;
        for (Material material : materials) {
            if (name.equals(material.getName())) return material;
        }
        return null;
    }

    public ArrayList<String> names() {
        ArrayList<String> names = new ArrayList<String>();
        for (Material material : materials) {
            names.add(material.getName());
        }
        return names;
    }

    /** The materials weighed by volume, for choosing what a part is filled with. */
    public ArrayList<String> solidNames() {
        ArrayList<String> names = new ArrayList<String>();
        for (Material material : materials) {
            if (!material.isSkin()) names.add(material.getName());
        }
        return names;
    }

    /** The materials weighed by area, for choosing what a part is covered with. */
    public ArrayList<String> skinNames() {
        ArrayList<String> names = new ArrayList<String>();
        names.add("None");
        for (Material material : materials) {
            if (material.isSkin()) names.add(material.getName());
        }
        return names;
    }

    /**
     * Reads the library from a file, falling back to the defaults when there is none yet — and writing
     * them out, so the file is there to be edited. A file that cannot be read is reported and the
     * defaults are used: a broken list must not stop the editor from opening a model.
     */
    public static MaterialLibrary load(File file) {
        if (file == null || !file.exists()) {
            MaterialLibrary library = defaults();
            library.save(file);
            return library;
        }
        InputStream in = null;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setTagInspector(tag -> true);
            Constructor constructor = new Constructor(MaterialLibrary.class, options);
            Yaml yaml = new Yaml(constructor);
            yaml.setBeanAccess(BeanAccess.FIELD);
            in = new FileInputStream(file);
            MaterialLibrary library = yaml.load(in);
            if (library == null || library.getMaterials().isEmpty()) return defaults();
            return library;
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Could not read the materials from " + file
                + ", using the built-in list", ex);
            return defaults();
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
        }
    }

    public void save(File file) {
        if (file == null) return;
        FileWriter writer = null;
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setIndent(2);
            Yaml yaml = new Yaml(new Representer(options), options);
            yaml.setBeanAccess(BeanAccess.FIELD);
            writer = new FileWriter(file);
            yaml.dump(this, writer);
            logger.log(Level.INFO, "Materials saved to {0}", file.getAbsolutePath());
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Could not save the materials to " + file, ex);
        } finally {
            try { if (writer != null) writer.close(); } catch (Exception ignored) { }
        }
    }
}
