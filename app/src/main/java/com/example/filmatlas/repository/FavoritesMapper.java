package com.example.filmatlas.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.filmatlas.model.FavoriteMovieEntity;
import com.example.filmatlas.model.Movie;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps between FavoriteMovieEntity <-> Movie.
 *
 * RULE:
 * If it's in the favorites table, it shows in the Favorites tab.
 * Do NOT filter favorites here (no "missing poster" filtering, etc).
 */
public final class FavoritesMapper {

    private FavoritesMapper() { }

    @Nullable
    public static FavoriteMovieEntity toEntity(@Nullable Movie m) {
        if (m == null || m.getId() == null) return null;

        return new FavoriteMovieEntity(
                m.getId(),
                m.getTitle(),
                m.getPosterPath(),
                m.getBackdropPath(),
                m.getOverview(),
                m.getReleaseDate(),
                (m.getVoteAverage() != null) ? m.getVoteAverage() : 0.0,
                System.currentTimeMillis()
        );
    }

    @NonNull
    public static List<Movie> toMovies(@Nullable List<FavoriteMovieEntity> entities) {
        ArrayList<Movie> out = new ArrayList<>();
        if (entities == null) return out;

        for (FavoriteMovieEntity e : entities) {
            if (e == null) continue;

            Movie m = new Movie();
            m.setId(e.movieId);
            m.setTitle(e.title);
            m.setPosterPath(e.posterPath);
            m.setBackdropPath(e.backdropPath);
            m.setOverview(e.overview);
            m.setReleaseDate(e.releaseDate);
            m.setVoteAverage(e.voteAverage);

            out.add(m);
        }

        return out;
    }
}
