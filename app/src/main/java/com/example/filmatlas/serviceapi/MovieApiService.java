package com.example.filmatlas.serviceapi;

import com.example.filmatlas.network.GenresResponse;
import com.example.filmatlas.network.MoviesResponse;
import com.example.filmatlas.network.TrailerResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Retrofit definitions for TMDB movie discovery, search, and detail endpoints.
 */
public interface MovieApiService {

    @GET("discover/movie")
    Call<MoviesResponse> discoverMoviesByRating(
            @Query("api_key") String apiKey,
            @Query("sort_by") String sortBy,
            @Query("vote_count.gte") int minVoteCount,
            @Query("primary_release_date.gte") String releaseDateGte,
            @Query("primary_release_date.lte") String releaseDateLte,
            @Query("without_genres") String withoutGenres,
            @Query("page") int page,
            @Query("region") String region,
            @Query("watch_region") String watchRegion,
            @Query("with_release_type") String withReleaseType,
            @Query("with_original_language") String originalLanguage
    );

    @GET("discover/movie")
    Call<MoviesResponse> discoverMoviesFiltered(
            @Query("api_key") String apiKey,
            @Query("sort_by") String sortBy,
            @Query("vote_count.gte") Integer minVoteCount,
            @Query("vote_average.gte") Float minRating,
            @Query("primary_release_year") Integer year,
            @Query("with_genres") String withGenres,
            @Query("page") int page
    );

    @GET("search/movie")
    Call<MoviesResponse> searchMoviesByTitle(
            @Query("api_key") String apiKey,
            @Query("query") String query,
            @Query("page") int page,
            @Query("include_adult") boolean includeAdult,
            @Query("year") Integer year
    );

    @GET("movie/{movie_id}/videos")
    Call<TrailerResponse> getMovieVideos(
            @Path("movie_id") int movieId,
            @Query("api_key") String apiKey
    );

    @GET("genre/movie/list")
    Call<GenresResponse> getMovieGenres(
            @Query("api_key") String apiKey
    );

}
