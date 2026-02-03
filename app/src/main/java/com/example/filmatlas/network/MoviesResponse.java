package com.example.filmatlas.network;

import java.util.List;

import com.example.filmatlas.model.Movie;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Represents a paged response of movies returned from the TMDB API.
 * Includes pagination metadata and the list of movies.
 */
public class MoviesResponse {

    @SerializedName("page")
    @Expose
    private Integer page;

    @SerializedName("total_pages")
    @Expose
    private Integer totalPages;

    @SerializedName("total_results")
    @Expose
    private Integer totalResults;

    @SerializedName("results")
    @Expose
    private List<Movie> movies = null;

    public MoviesResponse() {
    }

    // =====================
    // Getters
    // =====================

    public Integer getPage() {
        return page;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public Integer getTotalResults() {
        return totalResults;
    }

    public List<Movie> getMovies() {
        return movies;
    }

    // =====================
    // Setters
    // =====================

    public void setPage(Integer page) {
        this.page = page;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public void setTotalResults(Integer totalResults) {
        this.totalResults = totalResults;
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
    }
}
