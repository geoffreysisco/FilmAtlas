package com.example.filmatlas.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents the user's refinement settings for the Discover tab.
 * This is UI/query state (not a TMDB response model).
 */
public class MovieFilterOptions {

    // =====================
    // Enums
    // =====================

    public enum Sort {
        POPULARITY_DESC("popularity.desc"),
        RELEASE_DATE_DESC("primary_release_date.desc"),
        VOTE_AVERAGE_DESC("vote_average.desc");

        public final String apiValue;

        Sort(String apiValue) {
            this.apiValue = apiValue;
        }
    }

    // =====================
    // Fields
    // =====================

    private Sort sort = Sort.POPULARITY_DESC;

    // 0..10
    private float minRating = 0f;

    // null = any year
    private Integer year = null;

    // TMDB genre ids
    private final Set<Integer> genreIds = new HashSet<>();

    // =====================
    // Factory
    // =====================

    public static MovieFilterOptions defaults() {
        return new MovieFilterOptions();
    }

    // =====================
    // Derived Properties
    // =====================

    public boolean isDefault() {
        return sort == Sort.POPULARITY_DESC
                && minRating == 0f
                && year == null
                && genreIds.isEmpty();
    }

    public MovieFilterOptions copy() {
        MovieFilterOptions f = new MovieFilterOptions();
        f.sort = this.sort;
        f.minRating = this.minRating;
        f.year = this.year;
        f.genreIds.addAll(this.genreIds);
        return f;
    }

    public String getSortApiValue() {
        return (sort == null) ? Sort.POPULARITY_DESC.apiValue : sort.apiValue;
    }

    public Set<Integer> getGenreIdsReadOnly() {
        return Collections.unmodifiableSet(genreIds);
    }

    // =====================
    // Getters
    // =====================

    public Sort getSort() {
        return sort;
    }

    public float getMinRating() {
        return minRating;
    }

    public Integer getYear() {
        return year;
    }

    public Set<Integer> getGenreIds() {
        return Collections.unmodifiableSet(genreIds);
    }

    // =====================
    // Setters
    // =====================

    public void setSort(Sort sort) {
        this.sort = (sort == null) ? Sort.POPULARITY_DESC : sort;
    }

    public void setMinRating(float minRating) {
        if (minRating < 0f) minRating = 0f;
        if (minRating > 10f) minRating = 10f;
        this.minRating = minRating;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public void setGenreIds(Set<Integer> ids) {
        genreIds.clear();
        if (ids != null) {
            genreIds.addAll(ids);
        }
    }

    // =====================
    // Mutators
    // =====================

    public void addGenreId(int id) {
        genreIds.add(id);
    }

    public void removeGenreId(int id) {
        genreIds.remove(id);
    }

    public void clearGenres() {
        genreIds.clear();
    }

    // =====================
    // Helpers
    // =====================

    public String getWithGenresParam() {
        if (genreIds.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (Integer id : genreIds) {
            if (id == null) continue;
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        return (sb.length() == 0) ? null : sb.toString();
    }
}
