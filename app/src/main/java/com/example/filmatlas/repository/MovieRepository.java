package com.example.filmatlas.repository;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.filmatlas.BuildConfig;
import com.example.filmatlas.model.MovieFilterOptions;
import com.example.filmatlas.model.Movie;
import com.example.filmatlas.network.MoviesResponse;
import com.example.filmatlas.serviceapi.MovieApiService;
import com.example.filmatlas.serviceapi.RetrofitInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for movie browse and search operations.
 * Manages pagination state and exposes results via LiveData.
 */
public class MovieRepository {

    // =====================
    // Constants
    // =====================

    private static final int MAX_EMPTY_PAGE_SKIPS = 5;
    private static final int RANDOM_START_PAGE_MAX = 200;

    // =====================
    // App Context
    // =====================

    private final Application application;

    // =====================
    // Browse State
    // =====================

    private enum BrowseMode {
        DISCOVER_RANDOM,
        MOVIES_FILTERED,
        MOVIES_POPULAR,
        MOVIES_NEW
    }

    private BrowseMode browseMode = BrowseMode.MOVIES_POPULAR;
    private MovieFilterOptions lastMovieFilterOptions = MovieFilterOptions.defaults();

    private final ArrayList<Movie> browseMovies = new ArrayList<>();
    private final MutableLiveData<List<Movie>> browseLiveData = new MutableLiveData<>();

    private int movieFilteredEmptySkips = 0;
    private int browseCurrentPage = 1;
    private int browseTotalPages = Integer.MAX_VALUE;
    private boolean browseLoading = false;
    private boolean isPagingBrowse = false;


    private int discoverStartPage = 1;

    // =====================
    // Search State
    // =====================

    private final ArrayList<Movie> searchMovies = new ArrayList<>();
    private final MutableLiveData<List<Movie>> searchLiveData = new MutableLiveData<>();

    private int searchCurrentPage = 1;
    private int searchTotalPages = Integer.MAX_VALUE;
    private boolean searchLoading = false;

    private String lastSearchQuery = "";
    private Integer lastSearchYear = null;
    private int searchEmptyPageSkips = 0;

    // =====================
    // Loading State
    // =====================

    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);

    // =====================
    // Callbacks
    // =====================

    public interface LoadingCallback {
        void onDone();
    }

    // =====================
    // Constructor
    // =====================

    public MovieRepository(@NonNull Application application) {
        this.application = application;
        browseLiveData.setValue(new ArrayList<>());
        searchLiveData.setValue(new ArrayList<>());
    }

    // =====================
    // LiveData Streams
    // =====================

    public LiveData<List<Movie>> getBrowseLiveData() {
        return browseLiveData;
    }

    public LiveData<List<Movie>> getSearchLiveData() {
        return searchLiveData;
    }

    public LiveData<Boolean> getLoadingLiveData() {
        return loadingLiveData;
    }

    // =====================
    // Browse API
    // =====================

    public void loadFirstPagePopular(@NonNull LoadingCallback cb) {
        setBrowseMode(BrowseMode.MOVIES_POPULAR);
        loadFirstPageBrowseInternal(cb);
    }

    public void loadFirstPageNowPlaying(@NonNull LoadingCallback cb) {
        setBrowseMode(BrowseMode.MOVIES_NEW);
        loadFirstPageBrowseInternal(cb);
    }

    public void loadFirstPageDiscoverRandom(@NonNull LoadingCallback cb) {
        setBrowseMode(BrowseMode.DISCOVER_RANDOM);
        discoverStartPage = 1 + new Random().nextInt(RANDOM_START_PAGE_MAX);
        browseCurrentPage = discoverStartPage;
        browseMovies.clear();
        browseLiveData.setValue(new ArrayList<>());
        loadNextPageBrowse(cb);
    }

    public void loadFirstPageMovieFiltered(
            @NonNull MovieFilterOptions filter,
            @NonNull LoadingCallback cb
    ) {
        setBrowseMode(BrowseMode.MOVIES_FILTERED);
        lastMovieFilterOptions = filter.copy();
        movieFilteredEmptySkips = 0;
        browseMovies.clear();
        browseLiveData.setValue(new ArrayList<>());
        browseCurrentPage = 1;
        browseTotalPages = Integer.MAX_VALUE;
        loadNextPageBrowse(cb);
    }

    public void loadNextPageBrowse(@NonNull LoadingCallback cb) {
        if (isPagingBrowse) return;

        isPagingBrowse = true;

        if (browseLoading || browseCurrentPage > browseTotalPages) {
            isPagingBrowse = false;
            cb.onDone();
            return;
        }

        browseLoading = true;
        setLoading(true);

        MovieApiService api = RetrofitInstance.getService();
        Call<MoviesResponse> call = buildBrowseCall(api, browseCurrentPage);

        if (call == null) {
            browseLoading = false;
            setLoading(false);
            isPagingBrowse = false;
            cb.onDone();
            return;
        }

        call.enqueue(new Callback<MoviesResponse>() {
            @Override
            public void onResponse(Call<MoviesResponse> call, Response<MoviesResponse> response) {
                browseLoading = false;
                setLoading(false);
                isPagingBrowse = false;
                cb.onDone();

                if (!response.isSuccessful() || response.body() == null) return;

                MoviesResponse body = response.body();
                if (body.getTotalPages() != null) {
                    browseTotalPages = body.getTotalPages();
                }

                List<Movie> filtered = filterOutMissingPosters(body.getMovies());

                if (filtered.isEmpty()
                        && browseMode == BrowseMode.MOVIES_FILTERED
                        && browseCurrentPage < browseTotalPages
                        && movieFilteredEmptySkips < MAX_EMPTY_PAGE_SKIPS) {

                    movieFilteredEmptySkips++;
                    browseCurrentPage++;
                    loadNextPageBrowse(cb);
                    return;
                }

                movieFilteredEmptySkips = 0;

                browseMovies.addAll(filtered);
                browseLiveData.setValue(new ArrayList<>(browseMovies));
                browseCurrentPage++;
            }

            @Override
            public void onFailure(Call<MoviesResponse> call, Throwable t) {
                browseLoading = false;
                setLoading(false);
                isPagingBrowse = false;
                cb.onDone();
            }
        });
    }

    private void loadFirstPageBrowseInternal(@NonNull LoadingCallback cb) {
        browseMovies.clear();
        browseLiveData.setValue(new ArrayList<>());
        browseCurrentPage = 1;
        browseTotalPages = Integer.MAX_VALUE;
        loadNextPageBrowse(cb);
    }

    private Call<MoviesResponse> buildBrowseCall(@NonNull MovieApiService api, int page) {
        String apiKey = BuildConfig.TMDB_API_KEY;

        if (browseMode == BrowseMode.MOVIES_POPULAR) {
            return api.discoverMoviesByRating(
                    apiKey, "vote_average.desc", 500,
                    "1990-01-01", getTodayDate(), "99",
                    page, null, null, null, null
            );
        }

        if (browseMode == BrowseMode.MOVIES_NEW) {
            return api.discoverMoviesByRating(
                    apiKey, "primary_release_date.desc", 20,
                    getDateDaysAgo(365), getTodayDate(), "0",
                    page, "US", "US", "3|4", "en"
            );
        }

        if (browseMode == BrowseMode.MOVIES_FILTERED) {
            MovieFilterOptions f = lastMovieFilterOptions;
            return api.discoverMoviesFiltered(
                    apiKey,
                    f.getSortApiValue(),
                    200,
                    (f.getMinRating() <= 0f) ? null : f.getMinRating(),
                    f.getYear(),
                    f.getWithGenresParam(),
                    page
            );
        }

        return api.discoverMoviesByRating(
                apiKey, "vote_average.desc", 300,
                "1980-01-01", getTodayDate(), "99",
                page, null, null, null, null
        );
    }

    private void setBrowseMode(@NonNull BrowseMode mode) {
        browseMode = mode;
        browseLoading = false;
        browseCurrentPage = 1;
        browseTotalPages = Integer.MAX_VALUE;
    }

    // =====================
    // Helpers
    // =====================

    private void setLoading(boolean loading) {
        loadingLiveData.postValue(loading);
    }

    private String getTodayDate() {
        return java.time.LocalDate.now().toString();
    }

    private String getDateDaysAgo(int days) {
        return java.time.LocalDate.now().minusDays(days).toString();
    }

    private List<Movie> filterOutMissingPosters(List<Movie> in) {
        ArrayList<Movie> out = new ArrayList<>();
        if (in == null) return out;

        for (Movie m : in) {
            if (m != null && m.getPosterPath() != null && !m.getPosterPath().trim().isEmpty()) {
                out.add(m);
            }
        }
        return out;
    }

    public void clearBrowseResults() {
        browseMovies.clear();
        browseLiveData.setValue(new ArrayList<>());
    }
}