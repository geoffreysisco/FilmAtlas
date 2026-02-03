package com.example.filmatlas.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "genres")
public class GenreCacheEntity {

    @PrimaryKey
    public int id;

    @NonNull
    public String name;

    public GenreCacheEntity(int id, @NonNull String name) {
        this.id = id;
        this.name = name;
    }
}
