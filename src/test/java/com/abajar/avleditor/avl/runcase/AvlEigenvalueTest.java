package com.abajar.avleditor.avl.runcase;

import static org.junit.Assert.*;
import org.junit.Test;

public class AvlEigenvalueTest {

    @Test
    public void testModeParticipationRatios() {
        AvlEigenvalue eig = new AvlEigenvalue(-0.5f, 2.0f);

        assertFalse(eig.hasModeShape());

        eig.setModeStateAmplitude("u", 1.0f);
        eig.setModeStateAmplitude("w", 2.0f);
        eig.setModeStateAmplitude("q", 3.0f);
        eig.setModeStateAmplitude("the", 4.0f);
        eig.setModeStateAmplitude("v", 1.0f);
        eig.setModeStateAmplitude("p", 1.0f);
        eig.setModeStateAmplitude("r", 1.0f);
        eig.setModeStateAmplitude("phi", 1.0f);
        eig.setModeStateAmplitude("psi", 1.0f);

        assertTrue(eig.hasModeShape());

        assertEquals(10.0f, eig.getLongitudinalParticipation(), 1.0e-6f);
        assertEquals(5.0f, eig.getLateralParticipation(), 1.0e-6f);
        assertEquals(9.0f, eig.getPitchParticipation(), 1.0e-6f);
        assertEquals(5.0f, eig.getSpeedParticipation(), 1.0e-6f);
        assertEquals(15.0f, eig.getTotalParticipation(), 1.0e-6f);

        assertEquals(10.0f / 15.0f, eig.getLongitudinalRatio(), 1.0e-6f);
        assertEquals(5.0f / 15.0f, eig.getLateralRatio(), 1.0e-6f);
        assertEquals(9.0f / 10.0f, eig.getPitchRatio(), 1.0e-6f);
        assertEquals(5.0f / 10.0f, eig.getSpeedRatio(), 1.0e-6f);
    }
}
