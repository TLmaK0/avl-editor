/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.view.avl;

import com.abajar.avleditor.crrcsim.Shaft;
import com.abajar.avleditor.crrcsim.Power;
import com.abajar.avleditor.view.annotations.AvlEditorNode;
import com.abajar.avleditor.avl.AVLGeometry;
import com.abajar.avleditor.avl.geometry.Body;
import com.abajar.avleditor.avl.geometry.Section;
import com.abajar.avleditor.avl.geometry.Surface;
import com.abajar.avleditor.crrcsim.CRRCSim;
import com.abajar.avleditor.avl.mass.MassObject;
import com.abajar.avleditor.avl.mass.Mass;
import com.abajar.avleditor.crrcsim.Battery;
import com.abajar.avleditor.crrcsim.Engine;
import java.util.ArrayList;

interface TreeModificator{
  void modify(Object node, Object parent);
}

class AddSurface implements TreeModificator{
  public void modify(Object node, Object parent){
    ((AVLGeometry)node).createSurface();
  }
}

class AddBody implements TreeModificator{
  public void modify(Object node, Object parent){
    ((AVLGeometry)node).createBody();
  }
}

class CalculateCenterOfMass implements TreeModificator{
  public void modify(Object node, Object parent){
    // The editor passes the CRRCSim as the parent so the propulsion masses count too: the centre
    // of gravity has to come from every component, not just the airframe. Without the motor and
    // the battery it lands wherever the geometry alone puts it, and the only way to move it is
    // ballast.
    AVLGeometry geometry = (AVLGeometry)node;
    if (parent instanceof CRRCSim) {
      geometry.calculateCenterOfMassFromMasses(((CRRCSim)parent).getAllMasses());
    } else {
      geometry.calculateCenterOfMassFromMasses();
    }
  }
}

class MassesFromMaterials implements TreeModificator{
  public void modify(Object node, Object parent){
    ((AVLGeometry)node).massesFromMaterials();
  }
}

class AddSection implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Surface)node).createSection();
  }
}

class AddControl implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Section)node).createControl();
  }
}

class AddMass implements TreeModificator{
  public void modify(Object node, Object parent){
    ((MassObject)node).createMass();
  }
}

class AddChangeLog implements TreeModificator{
  public void modify(Object node, Object parent){
    ((CRRCSim)node).createChange();
  }
}

//class AddConfig implements TreeModificator{
//  public void modify(Object node, Object parent){
//    ((CRRCSim)node).createConfig();
//  }
//}
//
//class AddSound implements TreeModificator{
//  public void modify(Object node, Object parent){
//    ((Config)node).createSound();
//  }
//}
//
class AddBattery implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Power)node).createBattery();
  }
}

class AddShaft implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Battery)node).createShaft();
  }
}

class AddEngine implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Shaft)node).createEngine();
  }
}

class AddCombustionEngine implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Shaft)node).createCombustionEngine();
  }
}

class AddFuelTank implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Power)node).createFuelTank();
  }
}

class AddPropeller implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Shaft)node).createPropeller();
  }
}

/**
 * Duplicates a node with everything under it, beside the original.
 *
 * The mirror image of {@link Delete}, and generic for the same reason: it finds whichever {@code @AvlEditorNode}
 * list of the parent holds the node, by reflection, and inserts a deep copy right after it. So it works for
 * nodes nobody thought about, which is how the delete stopped being a chain of instanceof cases.
 *
 * Two things it must do beyond copying, and both are about not leaving a broken aircraft behind:
 *
 * <ul>
 *   <li><b>Names that identify a part are made unique.</b> AVL's file names surfaces, and two called `wing` is
 *       a model nobody can read; a body's BFILE is a real file written beside it, and two bodies sharing one
 *       would overwrite each other's profile. A control's name is <b>left alone</b>: it is what makes two
 *       sections part of the same control, so renaming it would quietly split an aileron in two.</li>
 *   <li><b>The transient links are restored</b>, the same call a load makes. A duplicated wing whose sections
 *       do not know their surface stops mirroring its masses without saying so.</li>
 * </ul>
 *
 * The copy lands exactly on top of the original in space. Offsetting it would mean inventing a position, and
 * the sensible offset differs for a wing, a battery and a fan.
 */
class Duplicate implements TreeModificator{
  public void modify(Object node, Object parent){
    if (node == null) return;

    java.util.List list = listHolding(node, parent);
    if (list == null) {
      System.err.println("Duplicate: " + node.getClass().getName() + " is not in any list of "
          + (parent == null ? "nothing" : parent.getClass().getName()));
      return;
    }

    Object copy = DeepCopy.of(node);
    if (copy == null) {
      System.err.println(DeepCopy.whyNot(node));
      return;
    }

    makeNamesUnique(copy, list);
    int at = list.indexOf(node);
    if (at < 0) list.add(copy); else list.add(at + 1, copy);
  }

  /** The parent's list that holds the node, or the parent itself when the tree selected the list. */
  private java.util.List listHolding(Object node, Object parent) {
    if (parent instanceof java.util.List && ((java.util.List) parent).contains(node)) {
      return (java.util.List) parent;
    }
    if (parent == null) return null;
    for (java.lang.reflect.Method method : parent.getClass().getMethods()) {
      if (!method.isAnnotationPresent(AvlEditorNode.class) || method.getParameterCount() > 0) continue;
      try {
        Object value = method.invoke(parent);
        if (value instanceof java.util.List && ((java.util.List) value).contains(node)) {
          return (java.util.List) value;
        }
      } catch (Exception ignored) {
        // A node that cannot be read cannot hold what we are duplicating either.
      }
    }
    return null;
  }

  /**
   * A surface's or a body's name identifies a part, and AVL reads those names, so the copy gets its own. A
   * body's BFILE is a file on disk and gets its own too. Everything else keeps its name.
   */
  private void makeNamesUnique(Object copy, java.util.List siblings) {
    if (copy instanceof Surface) {
      Surface surface = (Surface) copy;
      surface.setName(freeName(surface.getName(), siblings));
    } else if (copy instanceof Body) {
      Body body = (Body) copy;
      String name = freeName(body.getName(), siblings);
      body.setName(name);
      // A file name, so no spaces: AVL opens this by name from the directory beside the .avl.
      body.setBFILE(name.replaceAll("\\s+", "_") + ".dat");
    }
  }

  private String freeName(String name, java.util.List siblings) {
    String base = name == null || name.isEmpty() ? "copy" : name;
    for (int i = 2; i < 1000; i++) {
      String candidate = base + " " + i;
      if (!taken(candidate, siblings)) return candidate;
    }
    return base + " copy";
  }

  private boolean taken(String candidate, java.util.List siblings) {
    for (Object sibling : siblings) {
      String name = sibling instanceof Surface ? ((Surface) sibling).getName()
          : sibling instanceof Body ? ((Body) sibling).getName() : null;
      if (candidate.equals(name)) return true;
    }
    return false;
  }
}

class AddDuctedFan implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Shaft)node).createDuctedFan();
  }
}

class AddData implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Engine)node).createData();
  }
}

class AddDataIdle implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Engine)node).createDataIdle();
  }
}

class AddSimpleTrust implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Shaft)node).createSimpleTrust();
  }
}

class AddCollisionPoint implements TreeModificator{
  public void modify(Object node, Object parent){
    ((CRRCSim)node).createWheel();
  }
}

/**
 * Removes a node from whatever list of its parent holds it.
 *
 * Found by reflection over the parent's {@code @AvlEditorNode} lists, which are the same lists the tree
 * shows the node in — so anything the tree can show, the tree can delete. This replaced a chain of
 * instanceof cases naming each parent and child type: everything the chain did not name fell through to
 * a message on stderr and nothing was removed, which is how a battery, a shaft, an engine, a propeller,
 * a fuel tank, a data row and a Simple Trust all became impossible to delete while looking deletable.
 */
class Delete implements TreeModificator{
  public void modify(Object node, Object parent){
    if (parent == null || node == null) return;

    // The tree sometimes selects a list itself as the parent.
    if (parent instanceof java.util.List) {
      ((java.util.List)parent).remove(node);
      return;
    }

    for (java.lang.reflect.Method method : parent.getClass().getMethods()) {
      if (!method.isAnnotationPresent(AvlEditorNode.class) || method.getParameterCount() > 0) continue;
      try {
        Object value = method.invoke(parent);
        if (!(value instanceof java.util.List)) continue;
        java.util.List<?> list = (java.util.List<?>) value;
        for (Object candidate : list) {
          if (candidate == node) {
            list.remove(node);
            return;
          }
        }
      } catch (Exception ignored) {
        // A node that cannot be read cannot hold what we are deleting either.
      }
    }
    System.err.println("Delete: " + node.getClass().getName() + " is not in any list of "
        + parent.getClass().getName());
  }
}

class AddProfilePoint implements TreeModificator{
  public void modify(Object node, Object parent){
    ((Body)node).createProfilePoint();
  }
}

// ImportBfile is handled specially in the UI layer (Widget.scala)
// because it requires opening a FileDialog
class ImportBfile implements TreeModificator{
  public void modify(Object node, Object parent){
    // This is a no-op - actual import is handled in Widget.scala
    // which opens a FileDialog and calls body.importProfilePoints()
  }
}
