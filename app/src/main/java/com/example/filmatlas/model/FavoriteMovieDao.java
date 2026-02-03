package com.example.filmatlas.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * DAO for Favorites.
 */
@Dao
public interface FavoriteMovieDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FavoriteMovieEntity entity);

    @Query("DELETE FROM favorite_movies WHERE movieId = :movieId")
    void deleteById(int movieId);

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_movies WHERE movieId = :movieId)")
    LiveData<Boolean> isFavoriteLive(int movieId);

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_movies WHERE movieId = :movieId)")
    boolean isFavoriteSync(int movieId);

    @Query("SELECT * FROM favorite_movies ORDER BY addedAt DESC")
    LiveData<List<FavoriteMovieEntity>> getAllFavorites();

    @Query("SELECT movieId FROM favorite_movies")
    LiveData<List<Integer>> getAllFavoriteIds();
}