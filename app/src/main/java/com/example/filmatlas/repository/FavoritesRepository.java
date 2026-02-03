package com.example.filmatlas.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.filmatlas.model.AppDatabase;
import com.example.filmatlas.model.FavoriteMovieDao;
import com.example.filmatlas.model.FavoriteMovieEntity;
import com.example.filmatlas.model.Movie;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Repository responsible for managing user favorite movies.
 * Bridges Room persistence with UI-facing Movie objects.
 */
public class FavoritesRepository {

    private final FavoriteMovieDao favoriteDao;

    // Single-thread executor to serialize DB writes
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    public FavoritesRepository(Context context) {
        AppDatabase db = AppDatabase.getInstance(context.getApplicationContext());
        this.favoriteDao = db.favoriteMovieDao();
    }

    // Expose favorites as Movie objects so MovieAdapter can be reused unchanged
    public LiveData<List<Movie>> getFavoriteMovies() {
        return Transformations.map(
                favoriteDao.getAllFavorites(),
                FavoritesMapper::toMovies
        );
    }

    // Observe whether a movie is currently favorited
    public LiveData<Boolean> isFavoriteLive(int movieId) {
        return favoriteDao.isFavoriteLive(movieId);
    }

    // Insert or replace a favorite
    public void addFavorite(Movie movie) {
        FavoriteMovieEntity entity = FavoritesMapper.toEntity(movie);
        if (entity == null) return;

        dbExecutor.execute(() -> favoriteDao.insert(entity));
    }

    // Remove a favorite by movie id
    public void removeFavorite(int movieId) {
        dbExecutor.execute(() -> favoriteDao.deleteById(movieId));
    }

    // Set favorite state explicitly (UI decides add vs remove)
    public void setFavorite(Movie movie, boolean shouldBeFavorite) {
        if (movie == null || movie.getId() == null) return;

        int id = movie.getId();
        if (shouldBeFavorite) {
            addFavorite(movie);
        } else {
            removeFavorite(id);
        }
    }

    public void toggleFavorite(Movie movie) {
        if (movie == null || movie.getId() == null) return;

        final int id = movie.getId();

        dbExecutor.execute(() -> {
            boolean exists = favoriteDao.isFavoriteSync(id);

            if (exists) {
                favoriteDao.deleteById(id);
            } else {
                FavoriteMovieEntity entity = FavoritesMapper.toEntity(movie);
                if (entity != null) {
                    favoriteDao.insert(entity);
                }
            }
        });
    }

    public LiveData<java.util.Set<Integer>> getFavoriteIds() {
        return Transformations.map(
                favoriteDao.getAllFavoriteIds(),
                list -> {
                    java.util.HashSet<Integer> set = new java.util.HashSet<>();
                    if (list != null) set.addAll(list);
                    return set;
                }
        );
    }

}