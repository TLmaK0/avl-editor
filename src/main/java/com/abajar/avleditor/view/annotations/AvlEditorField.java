/*
 * Copyright (C) 2015  Hugo Freire Gil
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 */

package com.abajar.avleditor.view.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author Hugo
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AvlEditorField {
    String text();
    String help() default "No help";
    String[] options() default {};
    /**
     * Name of a no-argument method on the same object returning the choices, for a list that is not
     * known when the class is written: the materials the user can edit and add to. The field holds the
     * chosen name, not an index into a list that can change under it.
     */
    String optionsFrom() default "";
}