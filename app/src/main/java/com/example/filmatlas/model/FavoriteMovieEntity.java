package com.example.filmatlas.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Minimal persisted favorite movie data required to:
 * - Render movie_list_item.xml identically (poster, title, releaseYear, rating)
 * - Render details dialog in both portrait and landscape without refetch
 */
@Entity(tableName = "favorite_movies")
public class FavoriteMovieEntity {

    @PrimaryKey
    public int movieId;

    public String title;
    public String posterPath;

    // Used by landscape details dialog background image
    public String backdropPath;

    // Used by details dialog overview text
    public String overview;

    // Used by movie_list_item.xml for releaseYear
    public String releaseDate;

    public double voteAverage;

    // Sort favorites by most-recently added
    public long addedAt;

    public FavoriteMovieEntity(int movieId,
                               String title,
                               String posterPath,
                               String backdropPath,
                               String overview,
                               String releaseDate,
                               double voteAverage,
                               long addedAt) {
        this.movieId = movieId;
        this.title = title;
        this.posterPath = posterPath;
        this.backdropPath = backdropPath;
        this.overview = overview;
        this.releaseDate = releaseDate;
        this.voteAverage = voteAverage;
        this.addedAt = addedAt;
    }
}
