package com.example.filmatlas.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "search_history")
public class SearchQueryEntity {

    @PrimaryKey
    @NonNull
    public String query;

    public long lastUsedEpoch;

    public SearchQueryEntity(@NonNull String query, long lastUsedEpoch) {
        this.query = query;
        this.lastUsedEpoch = lastUsedEpoch;
    }
}
