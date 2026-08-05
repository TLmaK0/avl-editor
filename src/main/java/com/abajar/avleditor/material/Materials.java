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

/**
 * The library the editor is using, so a surface can offer its list without knowing where the editor
 * keeps its configuration. The application loads the file at startup and sets it here; anything asking
 * before then — a check, a headless export — gets the built-in defaults rather than nothing.
 */
public class Materials {

    private static MaterialLibrary library;
    private static File file;

    public static synchronized MaterialLibrary library() {
        if (library == null) library = MaterialLibrary.defaults();
        return library;
    }

    /** Points the editor at a file, loading it and remembering it for later saves. */
    public static synchronized MaterialLibrary useFile(File materialsFile) {
        file = materialsFile;
        library = MaterialLibrary.load(materialsFile);
        return library;
    }

    public static synchronized void save() {
        if (library != null && file != null) library.save(file);
    }

    /** For checks: hand it a library of its own. */
    public static synchronized void use(MaterialLibrary replacement) {
        library = replacement;
        file = null;
    }
}
