package com.geoffreysisco.filmatlas.model;

import androidx.annotation.NonNull;

public class Genre {

    public final int id;

    @NonNull
    public final String name;

    public Genre(int id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }
}