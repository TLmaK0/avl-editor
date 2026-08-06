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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * A copy of a model node with everything under it.
 *
 * By serialization, round-tripped in memory, for the same reason {@link TreeModificator}'s delete works by
 * reflection: it copies classes it has never heard of, so duplicating a node nobody thought about works
 * without anyone maintaining a list. A hand-written copy per class would be the chain of {@code instanceof}
 * cases that the delete stopped being.
 *
 * <b>Transient fields do not survive</b>, and that is not incidental: a section's parent surface, an element's
 * units source and a body's profile-point parents are all transient, restored on load by
 * {@code AVLGeometry.initParents()}. A duplicated wing whose sections do not know their surface stops
 * mirroring its masses, silently — so whoever inserts a copy has to restore those links, exactly as loading a
 * file does.
 */
public final class DeepCopy {

    private DeepCopy() {
    }

    /**
     * @return a deep copy of {@code node}, or null when it cannot be copied — which the caller must report
     *         rather than paper over. A half-copied aircraft is worse than a refusal.
     */
    @SuppressWarnings("unchecked")
    public static <T> T of(T node) {
        if (node == null) return null;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bytes);
            out.writeObject(node);
            out.close();
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            Object copy = in.readObject();
            in.close();
            return (T) copy;
        } catch (Exception ex) {
            return null;
        }
    }

    /** Why a copy failed, in words a user can act on. */
    public static String whyNot(Object node) {
        if (node == null) return "There is nothing selected to duplicate.";
        return "A " + node.getClass().getSimpleName() + " cannot be duplicated: it holds something that "
            + "cannot be copied. This is a limitation of the editor, not of the model.";
    }
}
