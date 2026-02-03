package com.example.filmatlas.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface GenreCacheDao {

    @Query("SELECT * FROM genres ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<GenreCacheEntity>> observeAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<GenreCacheEntity> genres);

    @Query("DELETE FROM genres")
    void clearAll();
}
