package com.abajar.avleditor.crrcsim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.abajar.avleditor.avl.AVL;
import com.abajar.avleditor.avl.AVLGeometry;
import com.abajar.avleditor.avl.geometry.Body;
import com.abajar.avleditor.avl.geometry.Control;
import com.abajar.avleditor.avl.geometry.Section;
import com.abajar.avleditor.avl.geometry.Surface;
import com.abajar.avleditor.avl.mass.Mass;
import java.util.ArrayList;
import org.junit.Test;

public class CenterOfMassTest {

    @Test
    public void testCalculateCenterOfMassFromMassesUsesRecursiveMasses() {
        AVL avl = new AVL();
        CRRCSim crrcsim = new CRRCSimFactory().create(avl);
        AVLGeometry geometry = avl.getGeometry();

        Surface surface = geometry.getSurfaces().get(0);
        Section section = surface.getSections().get(0);
        Control control = section.createControl();
        Body body = geometry.createBody();

        createMass(geometry, 2f, 0f, 0f, 0f);
        createMass(surface, 1f, 1f, 10f, 100f);
        createMass(section, 3f, 3f, 30f, 300f);
        createMass(control, 4f, 4f, 40f, 400f);
        createMass(body, 5f, 5f, 50f, 500f);

        boolean calculated = geometry.calculateCenterOfMassFromMasses();
        assertTrue(calculated);

        CenterOfMass centerOfMass = crrcsim.getCenterOfMass();
        assertNotNull(centerOfMass);
        assertEquals(3.4f, centerOfMass.getX(), 1.0e-6f);
        assertEquals(34.0f, centerOfMass.getY(), 1.0e-6f);
        assertEquals(340.0f, centerOfMass.getZ(), 1.0e-6f);
    }

    @Test
    public void testCalculateCenterOfMassFromMassesReturnsFalseWhenMassIsZero() {
        AVL avl = new AVL();
        AVLGeometry geometry = avl.getGeometry();
        boolean calculated = geometry.calculateCenterOfMassFromMasses();
        assertFalse(calculated);
    }

    @Test
    public void testGetMassesRecursiveDoesNotMutateMassLists() {
        AVL avl = new AVL();
        AVLGeometry geometry = avl.getGeometry();
        Surface surface = geometry.getSurfaces().get(0);
        Section section = surface.getSections().get(0);

        createMass(geometry, 1f, 0f, 0f, 0f);
        createMass(surface, 1f, 0f, 0f, 0f);
        createMass(section, 1f, 0f, 0f, 0f);

        int geometryOwnMasses = geometry.getMasses().size();
        int surfaceOwnMasses = surface.getMasses().size();
        int sectionOwnMasses = section.getMasses().size();

        ArrayList<Mass> recursiveMasses = geometry.getMassesRecursive();
        assertEquals(3, recursiveMasses.size());
        assertEquals(geometryOwnMasses, geometry.getMasses().size());
        assertEquals(surfaceOwnMasses, surface.getMasses().size());
        assertEquals(sectionOwnMasses, section.getMasses().size());

        ArrayList<Mass> recursiveMassesSecondCall = geometry.getMassesRecursive();
        assertEquals(3, recursiveMassesSecondCall.size());
        assertEquals(geometryOwnMasses, geometry.getMasses().size());
        assertEquals(surfaceOwnMasses, surface.getMasses().size());
        assertEquals(sectionOwnMasses, section.getMasses().size());
    }

    @Test
    public void testCenterOfMassMirrorsXrefYrefZref() {
        AVL avl = new AVL();
        CRRCSim crrcsim = new CRRCSimFactory().create(avl);
        AVLGeometry geometry = avl.getGeometry();

        geometry.setXref(0.91f);
        geometry.setYref(-0.07f);
        geometry.setZref(0.12f);

        CenterOfMass centerOfMass = crrcsim.getCenterOfMass();
        assertEquals(0.91f, centerOfMass.getX(), 1.0e-6f);
        assertEquals(-0.07f, centerOfMass.getY(), 1.0e-6f);
        assertEquals(0.12f, centerOfMass.getZ(), 1.0e-6f);
    }

    @Test
    public void testAutoMassesFromVolumePreservesTotalMass() {
        AVL avl = new AVL();
        AVLGeometry geometry = avl.getGeometry();
        Surface surface = geometry.getSurfaces().get(0);
        Section section = surface.getSections().get(0);
        Body body = geometry.createBody();

        createMass(geometry, 2f, 0.0f, 0.0f, 0.0f);
        createMass(surface, 3f, 0.5f, 0.0f, 0.0f);
        createMass(section, 4f, 1.0f, 0.0f, 0.0f);
        createMass(body, 1f, 0.2f, 0.0f, 0.0f);

        float totalBefore = totalMass(geometry.getMassesRecursive());
        assertEquals(10.0f, totalBefore, 1.0e-6f);

        boolean generated = geometry.autoMassesFromVolume();
        assertTrue(generated);

        float totalAfter = totalMass(geometry.getMassesRecursive());
        assertEquals(totalBefore, totalAfter, 1.0e-4f);

        assertEquals(0, geometry.getMasses().size());
        assertTrue(surface.getMasses().size() > 0);
        assertEquals(0, section.getMasses().size());
        assertTrue(body.getMasses().size() > 0);
        assertTrue(surface.getMasses().get(0).getName().startsWith("auto mass "));
        assertTrue(body.getMasses().get(0).getName().startsWith("auto mass "));
    }

    @Test
    public void testAutoMassesFromVolumeDoesNotDeleteMassesOnFailure() {
        AVL avl = new AVL();
        AVLGeometry geometry = avl.getGeometry();
        Surface surface = geometry.getSurfaces().get(0);
        Section section0 = surface.getSections().get(0);
        Section section1 = surface.getSections().get(1);

        section0.setChord(0f);
        section1.setChord(0f);

        createMass(surface, 2f, 0.2f, 0.0f, 0.0f);
        int massesBefore = surface.getMasses().size();
        float totalBefore = totalMass(geometry.getMassesRecursive());

        boolean generated = geometry.autoMassesFromVolume();
        assertFalse(generated);

        assertEquals(massesBefore, surface.getMasses().size());
        assertEquals(totalBefore, totalMass(geometry.getMassesRecursive()), 1.0e-6f);
    }

    private void createMass(com.abajar.avleditor.avl.mass.MassObject massObject, float massValue, float x, float y, float z) {
        Mass mass = massObject.createMass();
        mass.setMass(massValue);
        mass.setX(x);
        mass.setY(y);
        mass.setZ(z);
    }

    private float totalMass(ArrayList<Mass> masses) {
        float total = 0f;
        for (Mass mass : masses) {
            total += mass.getMass();
        }
        return total;
    }
}
