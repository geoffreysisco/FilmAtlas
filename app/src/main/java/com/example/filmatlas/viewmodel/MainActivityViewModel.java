package com.example.filmatlas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.filmatlas.BuildConfig;
import com.example.filmatlas.model.AppDatabase;
import com.example.filmatlas.model.GenreCacheEntity;
import com.example.filmatlas.model.Movie;
import com.example.filmatlas.model.MovieFilterOptions;
import com.example.filmatlas.network.MoviesResponse;
import com.example.filmatlas.repository.FavoritesRepository;
import com.example.filmatlas.repository.GenresRepository;
import com.example.filmatlas.repository.MovieRepository;
import com.example.filmatlas.serviceapi.MovieApiService;
import com.example.filmatlas.serviceapi.RetrofitInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * MainActivityViewModel
 *
 * Responsibilities:
 * - Browse paging lives in MovieRepository (single stream of "grid results")
 * - Search is ViewModel-owned (separate stream to avoid repo signature churn)
 * - Genres are cached in Room (GenreCacheEntity) for MovieFilterBottomSheet
 *
 * IMPORTANT:
 * - MovieFilterOptions is NOT persisted in Room (ever). It is in-memory UI state only.
 *
 * Display routing (prevents list flashing):
 * - BROWSE + FILTER => repository browse stream
 * - SEARCH          => ViewModel search stream
 */
public class MainActivityViewModel extends AndroidViewModel {

    // =====================
    // Display mode routing
    // =====================

    private enum DisplayMode {
        BROWSE,
        SEARCH,
        FILTER
    }

    private final MutableLiveData<DisplayMode> displayMode =
            new MutableLiveData<>(DisplayMode.BROWSE);

    // =====================
    // Repos / Services
    // =====================

    private final MovieRepository movieRepository;
    private final FavoritesRepository favoritesRepository;
    private final GenresRepository genresRepository;
    private final MovieApiService movieApiService;

    // =====================
    // Streams
    // =====================

    private final MediatorLiveData<List<Movie>> displayMovies = new MediatorLiveData<>();

    // Repo browse stream (used by BROWSE and FILTER)
    private final LiveData<List<Movie>> browseLiveData;

    // ViewModel search stream (used by SEARCH)
    private final MutableLiveData<List<Movie>> searchLiveData =
            new MutableLiveData<>(new ArrayList<>());

    // Loading: mirrors repo when not searching; search updates directly
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);

    // Search mode flag (Activity depends on this existing)
    private final MutableLiveData<Boolean> searchMode = new MutableLiveData<>(false);

    // observeForever reference to avoid leaks
    private final Observer<Boolean> repoLoadingObserver;

    // =====================
    // Filter state (IN-MEMORY ONLY)
    // =====================

    private final MutableLiveData<MovieFilterOptions> activeMovieFilterOptions =
            new MutableLiveData<>(MovieFilterOptions.defaults());

    private boolean movieFilterApplied = false;

    // =====================
    // Genres cache (Room) for bottom sheet only
    // =====================

    private final LiveData<List<GenreCacheEntity>> genresLiveData;

    // =====================
    // Search state
    // =====================

    private boolean searchLoading = false;
    private int searchCurrentPage = 1;
    private int searchTotalPages = Integer.MAX_VALUE;

    private String lastSearchQuery = "";
    private Integer lastSearchYear = null;

    // =====================
    // Autocomplete suggestions
    // =====================

    private final MutableLiveData<List<Movie>> suggestionsLiveData =
            new MutableLiveData<>(new ArrayList<>());

    private Call<MoviesResponse> suggestionsCall;

    // =====================
    // Filter UI events (Activity shows empty state)
    // =====================

    private final MutableLiveData<Boolean> filterEmptyStateEvent = new MutableLiveData<>(false);

    public LiveData<Boolean> getFilterEmptyStateEvent() {
        return filterEmptyStateEvent;
    }

    public void requestShowFilterEmptyState() {
        filterEmptyStateEvent.setValue(true);
    }

    // =====================
    // Constructor
    // =====================

    public MainActivityViewModel(@NonNull Application application) {
        super(application);

        movieApiService = RetrofitInstance.getService();

        movieRepository = new MovieRepository(application);
        favoritesRepository = new FavoritesRepository(application);

        // Repo stream
        browseLiveData = movieRepository.getBrowseLiveData();

        // Display routing:
        // - BROWSE + FILTER => browseLiveData
        // - SEARCH          => searchLiveData
        displayMovies.addSource(browseLiveData, list -> {
            DisplayMode mode = displayMode.getValue();
            if (mode == DisplayMode.BROWSE || mode == DisplayMode.FILTER) {
                displayMovies.setValue(list);
            }
        });

        displayMovies.addSource(searchLiveData, list -> {
            if (displayMode.getValue() == DisplayMode.SEARCH) {
                displayMovies.setValue(list);
            }
        });

        // Mirror repository loading when NOT in SEARCH mode
        repoLoadingObserver = isLoading -> {
            if (displayMode.getValue() != DisplayMode.SEARCH) {
                loadingLiveData.postValue(isLoading);
            }
        };
        movieRepository.getLoadingLiveData().observeForever(repoLoadingObserver);

        // Genres: Room cache ONLY (not filter state)
        AppDatabase db = AppDatabase.getInstance(application);
        genresLiveData = db.genreDao().observeAll();

        genresRepository = new GenresRepository(db, movieApiService);
        genresRepository.refreshGenres(BuildConfig.TMDB_API_KEY);
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        // Prevent observeForever leak
        movieRepository.getLoadingLiveData().removeObserver(repoLoadingObserver);

        cancelSuggestionsCall();
    }

    // =====================
    // Primary UI streams
    // =====================

    public LiveData<List<Movie>> getDisplayMovies() {
        return displayMovies;
    }

    public LiveData<Boolean> getLoadingLiveData() {
        return loadingLiveData;
    }

    // MainActivity uses: viewModel.isSearchMode().getValue()
    public LiveData<Boolean> isSearchMode() {
        return searchMode;
    }

    public LiveData<List<Movie>> getSuggestionsLiveData() {
        return suggestionsLiveData;
    }

    // =====================
    // Genres (Room cache)
    // =====================

    public LiveData<List<GenreCacheEntity>> getGenresLiveData() {
        return genresLiveData;
    }

    // =====================
    // Filter API (IN-MEMORY ONLY)
    // =====================

    public LiveData<MovieFilterOptions> getActiveMovieFilterOptions() {
        return activeMovieFilterOptions;
    }

    public boolean isMovieFilterApplied() {
        return movieFilterApplied;
    }

    /**
     * Called when UI ENTERS filter mode (before Apply).
     * Clears visible repo list so old browse results don't "flash" underneath filter UI.
     */
    public void enterMovieFilterMode() {
        // Filter and Search are mutually exclusive
        exitSearchModeInternal();

        displayMode.setValue(DisplayMode.FILTER);

        // Clear list to prevent stale browse results blink
        movieRepository.clearBrowseResults();
    }

    public void applyMovieFilter(@NonNull MovieFilterOptions filter) {
        MovieFilterOptions safe =
                (filter == null) ? MovieFilterOptions.defaults() : filter.copy();

        // Filter and Search are mutually exclusive
        exitSearchModeInternal();

        displayMode.setValue(DisplayMode.FILTER);

        activeMovieFilterOptions.setValue(safe);
        movieFilterApplied = true;

        // Clear first to avoid old list blink
        movieRepository.clearBrowseResults();

        // Load filtered data into repo stream
        movieRepository.loadFirstPageMovieFiltered(safe, () -> {});
    }

    /**
     * Clears filter state only.
     * Activity decides which browse tab to load next (Discover/Popular/New).
     */
    public void clearMovieFilter() {
        movieFilterApplied = false;
        activeMovieFilterOptions.setValue(MovieFilterOptions.defaults());

        // Filter and Search are mutually exclusive
        exitSearchModeInternal();

        // Clear list so no stale filtered list remains
        movieRepository.clearBrowseResults();

        displayMode.setValue(DisplayMode.BROWSE);
    }

    // =====================
    // Browse passthroughs
    // =====================

    public void loadMore() {
        if (displayMode.getValue() == DisplayMode.SEARCH) {
            loadNextPageSearch();
            return;
        }
        movieRepository.loadNextPageBrowse(() -> {});
    }

    public LiveData<Boolean> getLoading() {
        return getLoadingLiveData();
    }

    public void selectDiscover(boolean reselected) {
        exitSearchModeInternal();
        clearFilterStateInternal();
        displayMode.setValue(DisplayMode.BROWSE);

        movieRepository.loadFirstPageDiscoverRandom(() -> {});
    }

    public void selectPopular(boolean reselected) {
        exitSearchModeInternal();
        clearFilterStateInternal();
        displayMode.setValue(DisplayMode.BROWSE);

        movieRepository.loadFirstPagePopular(() -> {});
    }

    public void selectNowPlaying(boolean reselected) {
        exitSearchModeInternal();
        clearFilterStateInternal();
        displayMode.setValue(DisplayMode.BROWSE);

        movieRepository.loadFirstPageNowPlaying(() -> {});
    }

    // =====================
    // Search API (ViewModel-owned)
    // =====================

    public void setQuery(@NonNull String query) {
        String raw = (query == null) ? "" : query.trim();

        // Extract trailing year like "Home 2017" or "Home (2017)"
        Integer year = null;

        java.util.regex.Matcher mParen =
                java.util.regex.Pattern.compile("\\((\\d{4})\\)\\s*$").matcher(raw);
        if (mParen.find()) {
            year = Integer.parseInt(mParen.group(1));
            raw = raw.substring(0, mParen.start()).trim();
        } else {
            java.util.regex.Matcher mTrail =
                    java.util.regex.Pattern.compile("\\b(\\d{4})\\s*$").matcher(raw);
            if (mTrail.find()) {
                year = Integer.parseInt(mTrail.group(1));
                raw = raw.substring(0, mTrail.start()).trim();
            }
        }

        if (raw.isEmpty()) {
            clearSearch();
            return;
        }

        searchFirstPage(raw, year);
    }

    public void searchFirstPage(@NonNull String title, Integer year) {
        String q = (title == null) ? "" : title.trim();
        if (q.isEmpty()) {
            clearSearch();
            return;
        }

        // Enter search mode
        searchMode.setValue(true);
        displayMode.setValue(DisplayMode.SEARCH);

        // Search and Filter are mutually exclusive
        clearFilterStateInternal();

        lastSearchQuery = q;
        lastSearchYear = year;

        resetSearchPagingInternal();
        searchLiveData.setValue(new ArrayList<>());

        loadNextPageSearch();
    }

    public void refreshSearch() {
        if (lastSearchQuery == null || lastSearchQuery.trim().isEmpty()) return;
        searchFirstPage(lastSearchQuery, lastSearchYear);
    }

    public void clearSearch() {
        exitSearchModeInternal();
        displayMode.setValue(DisplayMode.BROWSE);
    }

    /**
     * Activity calls this to exit search without forcing a browse reload.
     * IMPORTANT: do not push browse results directly (avoid flash).
     */
    public void exitSearchMode() {
        exitSearchModeInternal();

        if (displayMode.getValue() == DisplayMode.SEARCH) {
            displayMode.setValue(DisplayMode.BROWSE);
        }
    }

    private void loadNextPageSearch() {
        if (displayMode.getValue() != DisplayMode.SEARCH) return;

        if (searchLoading) return;
        if (searchCurrentPage > searchTotalPages) return;

        String q = lastSearchQuery;
        if (q == null || q.trim().isEmpty()) return;

        searchLoading = true;
        loadingLiveData.postValue(true);

        Call<MoviesResponse> call = movieApiService.searchMoviesByTitle(
                BuildConfig.TMDB_API_KEY,
                q,
                searchCurrentPage,
                false,
                lastSearchYear
        );

        call.enqueue(new Callback<MoviesResponse>() {
            @Override
            public void onResponse(Call<MoviesResponse> call, Response<MoviesResponse> response) {
                searchLoading = false;
                loadingLiveData.postValue(false);

                if (!response.isSuccessful() || response.body() == null) return;

                MoviesResponse body = response.body();

                if (body.getTotalPages() != null) {
                    searchTotalPages = body.getTotalPages();
                }

                List<Movie> incoming = body.getMovies();
                List<Movie> filtered = filterOutMissingPosters(incoming);

                List<Movie> current = searchLiveData.getValue();
                if (current == null) current = Collections.emptyList();

                ArrayList<Movie> next = new ArrayList<>(current);
                next.addAll(filtered);

                searchLiveData.setValue(next);
                searchCurrentPage++;
            }

            @Override
            public void onFailure(Call<MoviesResponse> call, Throwable t) {
                searchLoading = false;
                loadingLiveData.postValue(false);
            }
        });
    }

    // =====================
    // Suggestions
    // =====================

    public void fetchSuggestions(@NonNull String raw) {
        String q = (raw == null) ? "" : raw.trim();

        if (q.length() < 2) {
            suggestionsLiveData.setValue(new ArrayList<>());
            cancelSuggestionsCall();
            return;
        }

        cancelSuggestionsCall();

        suggestionsCall = movieApiService.searchMoviesByTitle(
                BuildConfig.TMDB_API_KEY,
                q,
                1,
                false,
                null
        );

        suggestionsCall.enqueue(new Callback<MoviesResponse>() {
            @Override
            public void onResponse(Call<MoviesResponse> call, Response<MoviesResponse> response) {
                if (call.isCanceled()) return;

                if (!response.isSuccessful() || response.body() == null) {
                    suggestionsLiveData.setValue(new ArrayList<>());
                    return;
                }

                List<Movie> incoming = response.body().getMovies();
                if (incoming == null) incoming = new ArrayList<>();

                int limit = Math.min(8, incoming.size());
                suggestionsLiveData.setValue(new ArrayList<>(incoming.subList(0, limit)));
            }

            @Override
            public void onFailure(Call<MoviesResponse> call, Throwable t) {
                if (call.isCanceled()) return;
                suggestionsLiveData.setValue(new ArrayList<>());
            }
        });
    }

    public void clearSuggestions() {
        cancelSuggestionsCall();
        suggestionsLiveData.setValue(new ArrayList<>());
    }

    public void clearSearchResultsOnly() {
        resetSearchPagingInternal();
        lastSearchQuery = "";
        lastSearchYear = null;

        searchLiveData.setValue(new ArrayList<>());
        loadingLiveData.postValue(false);
    }

    private void cancelSuggestionsCall() {
        if (suggestionsCall != null) {
            suggestionsCall.cancel();
            suggestionsCall = null;
        }
    }

    // =====================
    // Favorites
    // =====================

    public LiveData<List<Movie>> getFavoriteMovies() {
        return favoritesRepository.getFavoriteMovies();
    }

    public LiveData<Boolean> isFavoriteLive(int movieId) {
        return favoritesRepository.isFavoriteLive(movieId);
    }

    public void setFavorite(Movie movie, boolean shouldBeFavorite) {
        favoritesRepository.setFavorite(movie, shouldBeFavorite);
    }

    public void toggleFavorite(@NonNull Movie movie) {
        favoritesRepository.toggleFavorite(movie);
    }

    public LiveData<java.util.Set<Integer>> getFavoriteIds() {
        return favoritesRepository.getFavoriteIds();
    }

    // =====================
    // Internal helpers
    // =====================

    private void exitSearchModeInternal() {
        searchMode.setValue(false);

        resetSearchPagingInternal();

        lastSearchQuery = "";
        lastSearchYear = null;

        searchLiveData.setValue(new ArrayList<>());
        loadingLiveData.postValue(false);
    }

    private void resetSearchPagingInternal() {
        searchLoading = false;
        searchCurrentPage = 1;
        searchTotalPages = Integer.MAX_VALUE;
    }

    private void clearFilterStateInternal() {
        movieFilterApplied = false;
        activeMovieFilterOptions.setValue(MovieFilterOptions.defaults());
    }

    private List<Movie> filterOutMissingPosters(List<Movie> in) {
        ArrayList<Movie> out = new ArrayList<>();
        if (in == null) return out;

        for (Movie m : in) {
            if (m == null) continue;
            String p = m.getPosterPath();
            if (p != null && !p.trim().isEmpty() && !"null".equalsIgnoreCase(p.trim())) {
                out.add(m);
            }
        }
        return out;
    }
}