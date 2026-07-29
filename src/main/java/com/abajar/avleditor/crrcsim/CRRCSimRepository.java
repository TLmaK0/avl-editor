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
import com.abajar.avleditor.avl.AVLGeometry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

/**
 *
 * @author Hugo
 */
public class CRRCSimRepository {
    static final Logger logger = Logger.getLogger(CRRCSimRepository.class.getName());

    public CRRCSim restoreFromFile(File file) throws FileNotFoundException{
        CRRCSim crrcsim = null;

        try {
            // Check if file is YAML
            String content = new String(Files.readAllBytes(file.toPath()));
            if (content.startsWith("!!") || content.contains("\n!!") ||
                (content.contains(":") && !content.contains("<?xml"))) {
                // Load as YAML
                logger.log(Level.INFO, "Loading YAML file");
                LoaderOptions loaderOptions = new LoaderOptions();
                loaderOptions.setTagInspector(tag -> true);
                Constructor constructor = new Constructor(CRRCSim.class, loaderOptions);
                Yaml yaml = new Yaml(constructor);
                yaml.setBeanAccess(BeanAccess.FIELD);
                FileInputStream fis = new FileInputStream(file);
                crrcsim = yaml.load(fis);
                fis.close();
            } else {
                // Legacy formats (XML or binary)
                FileInputStream data = new FileInputStream(file);
                JAXBContext context;
                Unmarshaller u;
                int version = this.getVersion(data);

                logger.log(Level.INFO, "Loading file version {0}", version);
                switch(version){
                    case 10:
                        context = JAXBContext.newInstance(AVLGeometry.class);
                        u = context.createUnmarshaller();
                        AVL avl = new AVL();
                        avl.setGeometry((AVLGeometry)u.unmarshal(data));
                        crrcsim = new CRRCSimFactory().create(avl);
                        break;
                    case 13:
                        context = JAXBContext.newInstance(CRRCSim.class);
                        u = context.createUnmarshaller();
                        crrcsim = (CRRCSim)u.unmarshal(data);
                        break;
                    default:
                        ObjectInput input = new ObjectInputStream(data);
                        crrcsim = (CRRCSim)input.readObject();
                        break;
                }
                data.close();
            }

            fixCrrcsimDefaultsNewVersions(crrcsim);
            crrcsim.setOriginPath(file.getParentFile().toPath());
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.log(Level.SEVERE, null, ex);
        }
        return crrcsim;
    }

    public void storeToFile(File file, CRRCSim crrcsim){
        try {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

            Representer representer = new Representer(options);
            representer.addClassTag(CRRCSim.class, Tag.MAP);

            Yaml yaml = new Yaml(representer, options);
            yaml.setBeanAccess(BeanAccess.FIELD);

            FileWriter writer = new FileWriter(file);
            yaml.dump(crrcsim, writer);
            writer.close();

            logger.log(Level.INFO, "Saved to YAML: {0}", file.getAbsolutePath());
        } catch (IOException ex) {
            ex.printStackTrace();
            Logger.getLogger(CRRCSimRepository.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private int getVersion(FileInputStream xml) throws UnsupportedEncodingException, IOException{
        FileChannel fc = xml.getChannel();
        char[] buf = new char[1000];
        Reader r = new InputStreamReader(xml, "UTF-8");
        StringBuilder s = new StringBuilder();
        int n = r.read(buf);
        s.append(buf, 0, n);
        fc.position(0);
        int version = 15;
        if (s.toString().contains("<crrcSim")) version = 13;
        else if (s.toString().contains("<avlGeometry>")) version = 10;
        return version;
    }

    private void fixCrrcsimDefaultsNewVersions(CRRCSim crrcsim) {
        AVL avl = crrcsim.getAvl();

        if (avl.getVelocity() == 0) avl.setVelocity(AVL.DEFAULT_VELOCITY);
        if (avl.getLengthUnit() == null) avl.setLengthUnit(AVL.DEFAULT_LENGTH_UNIT);
        if (avl.getMassUnit() == null) avl.setMassUnit(AVL.DEFAULT_MASS_UNIT);
        if (avl.getTimeUnit() == null) avl.setTimeUnit(AVL.DEFAULT_TIME_UNIT);
        if (avl.getReynoldsNumber() == 0) avl.setReynoldsNumber(AVL.DEFAULT_REYNOLDS_NUMBER);

        if (crrcsim.getGraphics() == null) crrcsim.setGraphics(new Graphics());
        if (crrcsim.getWheelsVersion() == null) crrcsim.setWheelsVersion("1");
        if (crrcsim.getWheelsUnits() == null) crrcsim.setWheelsUnits("1");
        if (crrcsim.getConfig().getPower() != null && crrcsim.getConfig().getPower().getBateries().size() > 0){
            for(Battery battery: crrcsim.getConfig().getPower().getBateries()){
                if (battery.getU_0rel() == null) battery.setU_0rel(Battery.U_0_REL);
                if (battery.getC() == 0) battery.setC(Battery.DEFAULT_C);
            }
        }
        for(Wheel collision: crrcsim.getWheels()){
            if (collision.getName() == null) collision.setName("Collision point");
        }
        if (crrcsim.getCenterOfMass() == null) crrcsim.setCenterOfMass(new CenterOfMass(crrcsim));
        if(crrcsim.internalVersion < 21) for(Wheel collision: crrcsim.getWheels()){
            collision.getPos().setX(-collision.getPos().getX());
            collision.getPos().setZ(-collision.getPos().getZ());
        }

        if(crrcsim.getChangelog() == null) crrcsim.fixChangelog(); 
        crrcsim.internalVersion = 21;
    }

    
}