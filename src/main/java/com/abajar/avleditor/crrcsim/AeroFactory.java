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

import com.abajar.avleditor.UnitConversor;
import com.abajar.avleditor.avl.AVL;
import com.abajar.avleditor.avl.connectivity.AvlRunner;
import com.abajar.avleditor.avl.runcase.AvlCalculation;
import com.abajar.avleditor.avl.runcase.Configuration;
import com.abajar.avleditor.avl.runcase.StabilityDerivatives;
import java.io.IOException;
import java.nio.file.Path;

/**
 *
 * @author Hugo
 */
public class AeroFactory {
    public Aero createFromAvl(String avlPath, AVL avl, Path originPath, AeroModel aeroModel) throws IOException, InterruptedException, Exception{
        if (aeroModel == null) aeroModel = new AeroModel();

        AvlRunner avlRunner = new AvlRunner(avlPath, avl, originPath);
        
        AvlCalculation avlCalculation = avlRunner.getCalculation();
        Aero aero = new Aero();

        int elevatorPosition = avlCalculation.getElevatorPosition();
        int rudderPosition = avlCalculation.getRudderPosition();
        int aileronPosition = avlCalculation.getAileronPosition();

        StabilityDerivatives std = avlCalculation.getStabilityDerivatives();
        Configuration config = avlCalculation.getConfiguration();

        Reference ref = aero.getRef();
        UnitConversor uc = new UnitConversor();
        ref.setChord(uc.convertToMeters(config.getCref(), avl.getLengthUnit()));
        ref.setSpan(uc.convertToMeters(config.getBref(), avl.getLengthUnit()));
        ref.setArea(uc.convertToSquareMeters(config.getSref(), avl.getLengthUnit()));
        ref.setSpeed(config.getVelocity());

        Miscellaneous misc = aero.getMisc();
        misc.setAlpha_0((float)(config.getAlpha() * Math.PI / 180));

        misc.setEta_loc(aeroModel.getEta_loc()); //eta_loc for stall model http://en.wikipedia.org/wiki/Pseudorapidity
        misc.setCG_arm(aeroModel.getCG_arm()); //The point of application of the averaged dCL, as a fraction of chord ahead of the CG.
        misc.setSpan_eff(config.getE()); //span efficiency: derived from AVL (effective span, 0.95 most planes, 0.85 flying wing).

        PitchMoment pitchMoment = aero.getPitchMoment();
        pitchMoment.setCm_0(config.getCmtot());
        pitchMoment.setCm_a(std.getCma());
        pitchMoment.setCm_q(std.getCmq());
        if (elevatorPosition != -1) pitchMoment.setCm_de(std.getCmd()[elevatorPosition]);

        Lift lift = aero.getLift();
        lift.setCL_0(config.getCLtot());

        lift.setCL_max(aeroModel.getCL_max());
        lift.setCL_min(aeroModel.getCL_min());

        lift.setCL_a(std.getCLa());
        lift.setCL_q(std.getCLq());
        if (elevatorPosition != -1) lift.setCL_de(std.getCLd()[elevatorPosition]);
        lift.setCL_drop(aeroModel.getCL_drop());     //CL drop during stall break
        lift.setCL_CD0(aeroModel.getCL_CD0());      //CL at minimum profile
        lift.setCL_0(config.getCLtot());

        Drag drag = aero.getDrag();
        drag.setCD_prof(config.getCDvis());

        drag.setUexp_CD(aeroModel.getUexp_CD()); //for Re-scaling of CD_prof  ~ (U/U_ref)^Uexp_CD
        drag.setCD_stall(aeroModel.getCD_stall()); //drag coeff. during stalling
        drag.setCD_CLsq(aeroModel.getCD_CLsq()); //d(CD)/d(CL^2), curvature of parabolic profile polar
        drag.setCD_AIsq(aeroModel.getCD_AIsq()); //drag due to aileron deflection d(CD)/d(aileron^2)
        drag.setCD_ELsq(aeroModel.getCD_ELsq()); //drag due to elevon deflection d(CD)/d(elevator^2)

        Y Y = aero.getSideForce();
        Y.setCY_b(std.getCYb());
        Y.setCY_p(std.getCYp());
        Y.setCY_r(std.getCYr());
        if (rudderPosition != -1) Y.setCY_dr(std.getCYd()[rudderPosition]);
        if (aileronPosition != -1)Y.setCY_da(std.getCYd()[aileronPosition]);

        l l = aero.getRollMomment();
        l.setCl_b(std.getClb());
        l.setCl_p(std.getClp());
        l.setCl_r(std.getClr());
        if (rudderPosition != -1) l.setCl_dr(std.getCld()[rudderPosition]);
        if (aileronPosition != -1) l.setCl_da(std.getCld()[aileronPosition]);

        n n = aero.getYawMomment();
        n.setCn_b(std.getCnb());
        n.setCn_p(std.getCnp());
        n.setCn_r(std.getCnr());
        if (rudderPosition != -1) n.setCn_dr(std.getCnd()[rudderPosition]);
        if (aileronPosition != -1)n.setCn_da(std.getCnd()[aileronPosition]);

        //TODO: add flap section
        //TODO: add spoilder section
        //TODO: add retract section

        return aero;
    }
}