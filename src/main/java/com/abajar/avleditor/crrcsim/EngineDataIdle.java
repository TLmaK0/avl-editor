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
import java.io.Serializable;
import javax.xml.bind.annotation.XmlAttribute;

/**
 *
 * @author Hugo
 */
public class EngineDataIdle implements Serializable {

        @Override
        public String toString() {
            return "DataIdle";
        }

        @AvlEditorField(text="Voltage at idle",
            help="Voltage at idle"
        )
        private float U_K;

        @AvlEditorField(text="Current at idle",
            help="current at idle"
        )
        private float I_M;

        public EngineDataIdle() {
        }

        /**
         * @return the U_K
         */
        @XmlAttribute(name="U_K")
        public float getU_K() {
            return U_K;
        }

        /**
         * @param U_K the U_K to set
         */
        public void setU_K(float U_K) {
            this.U_K = U_K;
        }

        /**
         * @return the I_M
         */
        @XmlAttribute(name="I_M")
        public float getI_M() {
            return I_M;
        }

        /**
         * @param I_M the I_M to set
         */
        public void setI_M(float I_M) {
            this.I_M = I_M;
        }
    }