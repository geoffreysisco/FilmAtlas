package com.example.filmatlas.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SearchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SearchQueryEntity entity);

    @Query("SELECT * FROM search_history ORDER BY lastUsedEpoch DESC LIMIT :limit")
    LiveData<List<SearchQueryEntity>> observeRecent(int limit);

    @Query("DELETE FROM search_history")
    void clearAll();

    @Query("DELETE FROM search_history WHERE query = :query")
    void deleteByQuery(String query);
}