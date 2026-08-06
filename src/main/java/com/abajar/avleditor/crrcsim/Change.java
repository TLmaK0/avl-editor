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

import java.util.Date;
import com.abajar.avleditor.view.annotations.AvlEditorField;
import com.abajar.avleditor.view.annotations.AvlEditor;
import com.abajar.avleditor.view.avl.SelectorMutableTreeNode.ENABLE_BUTTONS;

@AvlEditor(buttons={ENABLE_BUTTONS.DUPLICATE, ENABLE_BUTTONS.DELETE})
public class Change implements java.io.Serializable {
    static final long serialVersionUID = 20260806L;


    private Date date = new Date();


    @AvlEditorField(text="Author",
        help="Author's changes"
    )
    private String author;

    @AvlEditorField(text="Description",
        help="Description of the change"
    )
    private String en;

    /**
     * @return the date
     */
    public Date getDate() {
        return date;
    }

    /**
     * @param date the date to set
     */
    public void setDate(Date date) {
        this.date = date;
    }

    /**
     * @return the author
     */
    public String getAuthor() {
        return author;
    }

    /**
     * @param author the author to set
     */
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * @return the en
     */
    public String getEn() {
        return en;
    }

    /**
     * @param en the en to set
     */
    public void setEn(String en) {
        this.en = en;
    }


    @Override
    public String toString(){
        return this.date.toString();
    }
}