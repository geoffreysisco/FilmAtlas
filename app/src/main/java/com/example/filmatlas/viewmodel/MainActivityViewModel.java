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
import com.example.filmatlas.repository.FavoritesRepository;
import com.example.filmatlas.repository.GenresRepository;
import com.example.filmatlas.repository.MovieRepository;
import com.example.filmatlas.repository.SearchHistoryRepository;
import com.example.filmatlas.repository.SearchRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Responsibilities:
 * - Browse paging lives in MovieRepository (single stream of "grid results")
 * - Search networking + paging + suggestions live in SearchRepository
 * - ViewModel owns UI policy + routing (DisplayMode, no-flash rules, restore behavior)
 * - Genres are cached in Room (GenreCacheEntity) for MovieFilterBottomSheet
 *
 * IMPORTANT:
 * - MovieFilterOptions is NOT persisted in Room (ever). It is in-memory UI state only.
 *
 * Display routing (prevents list flashing):
 * - BROWSE + FILTER => repository browse stream
 * - SEARCH          => SearchRepository search stream
 */
public class MainActivityViewModel extends AndroidViewModel {

    // Display mode routing

    private enum DisplayMode {
        BROWSE,
        SEARCH,
        FILTER
    }

    private final MutableLiveData<DisplayMode> displayMode =
            new MutableLiveData<>(DisplayMode.BROWSE);

    // Repos / services

    private final MovieRepository movieRepository;
    private final FavoritesRepository favoritesRepository;
    private final SearchRepository searchRepository;
    private final GenresRepository genresRepository;

    private final SearchHistoryRepository searchHistoryRepository;

    // Streams

    private final MediatorLiveData<List<Movie>> displayMovies = new MediatorLiveData<>();

    // Repo browse stream (used by BROWSE and FILTER)
    private final LiveData<List<Movie>> browseLiveData;

    // Loading: mirrors MovieRepository when not searching; mirrors SearchRepository when searching
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);

    // Search mode flag (Activity depends on this existing)
    private final MutableLiveData<Boolean> searchMode = new MutableLiveData<>(false);

    // observeForever observers (stored so they can be removed in onCleared() to avoid leaks)
    private final Observer<Boolean> repoLoadingObserver;
    private final Observer<Boolean> searchRepoLoadingObserver;
    private final Observer<List<Movie>> searchRepoSuggestionsObserver;

    // Filter state (IN-MEMORY ONLY)

    private final MutableLiveData<MovieFilterOptions> activeMovieFilterOptions =
            new MutableLiveData<>(MovieFilterOptions.defaults());

    private boolean movieFilterApplied = false;

    // Genres cache (Room)

    private final LiveData<List<GenreCacheEntity>> genresLiveData;

    // Search state

    private String lastSearchQuery = "";
    private String lastSearchLabel = "";
    private Integer lastSearchYear = null;

    private boolean searchHistoryRecordedThisSession = false;

    // Autocomplete suggestions

    private final MutableLiveData<List<Movie>> suggestionsLiveData =
            new MutableLiveData<>(new ArrayList<>());

    // Search history (Room)

    private final LiveData<List<String>> recentSearchQueriesLiveData;

    public LiveData<List<String>> getRecentSearchQueries() {
        return recentSearchQueriesLiveData;
    }

    // Filter UI events (Activity shows empty state)

    private final MutableLiveData<Boolean> filterEmptyStateEvent = new MutableLiveData<>(false);

    public LiveData<Boolean> getFilterEmptyStateEvent() {
        return filterEmptyStateEvent;
    }

    public void requestShowFilterEmptyState() {
        filterEmptyStateEvent.setValue(true);
    }

    public void requestShowFilterEmptyState(boolean show) {
        filterEmptyStateEvent.setValue(show);
    }

    // Constructor

    public MainActivityViewModel(@NonNull Application application) {
        super(application);

        movieRepository = new MovieRepository(application);
        searchRepository = new SearchRepository(application);
        favoritesRepository = new FavoritesRepository(application);

        searchHistoryRepository = new SearchHistoryRepository(application);
        recentSearchQueriesLiveData = searchHistoryRepository.observeRecentQueries(10);

        browseLiveData = movieRepository.getBrowseLiveData();

        displayMovies.addSource(browseLiveData, list -> {
            DisplayMode mode = displayMode.getValue();
            if (mode == DisplayMode.BROWSE || mode == DisplayMode.FILTER) {
                displayMovies.setValue(list);
            }
        });

        displayMovies.addSource(searchRepository.getSearchLiveData(), list -> {
            if (displayMode.getValue() == DisplayMode.SEARCH) {
                displayMovies.setValue(list);
            }
        });

        repoLoadingObserver = isLoading -> {
            if (displayMode.getValue() != DisplayMode.SEARCH) {
                loadingLiveData.postValue(isLoading);
            }
        };
        movieRepository.getLoadingLiveData().observeForever(repoLoadingObserver);

        searchRepoLoadingObserver = isLoading -> {
            if (displayMode.getValue() == DisplayMode.SEARCH) {
                loadingLiveData.postValue(isLoading);
            }
        };
        searchRepository.getLoadingLiveData().observeForever(searchRepoLoadingObserver);

        searchRepoSuggestionsObserver = list -> suggestionsLiveData.postValue(list);
        searchRepository.getSuggestionsLiveData().observeForever(searchRepoSuggestionsObserver);

        // Genres: ViewModel observes Room cache only; GenresRepository handles network refresh
        AppDatabase db = AppDatabase.getInstance(application);
        genresLiveData = db.genreDao().observeAll();

        genresRepository = new GenresRepository(db);
        genresRepository.refreshGenres(BuildConfig.TMDB_API_KEY);
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        movieRepository.getLoadingLiveData().removeObserver(repoLoadingObserver);
        searchRepository.getLoadingLiveData().removeObserver(searchRepoLoadingObserver);
        searchRepository.getSuggestionsLiveData().removeObserver(searchRepoSuggestionsObserver);
        searchRepository.cancelSuggestionsCall();
    }

    // Primary UI streams

    public LiveData<List<Movie>> getDisplayMovies() {
        return displayMovies;
    }

    public LiveData<Boolean> getLoadingLiveData() {
        return loadingLiveData;
    }

    public LiveData<Boolean> isSearchMode() {
        return searchMode;
    }

    public LiveData<List<Movie>> getSuggestionsLiveData() {
        return suggestionsLiveData;
    }

    // Genres (Room cache)

    public LiveData<List<GenreCacheEntity>> getGenresLiveData() {
        return genresLiveData;
    }

    // Filter API (IN-MEMORY ONLY)

    public LiveData<MovieFilterOptions> getActiveMovieFilterOptions() {
        return activeMovieFilterOptions;
    }

    public boolean isMovieFilterApplied() {
        return movieFilterApplied;
    }

    // Restore-only: used by Activity state restore when we already have filter results on screen.
    public void restoreMovieFilterApplied(boolean applied) {
        movieFilterApplied = applied;
        if (applied) {
            displayMode.setValue(DisplayMode.FILTER);
        }
    }

    // Called when UI enters filter mode (before Apply); clears repo list to prevent browse flash under filter UI.
    public void enterMovieFilterMode() {
        exitSearchModeInternal();
        displayMode.setValue(DisplayMode.FILTER);
        movieRepository.clearBrowseResults();
    }

    public void applyMovieFilter(@NonNull MovieFilterOptions filter) {
        MovieFilterOptions safe =
                (filter == null) ? MovieFilterOptions.defaults() : filter.copy();

        exitSearchModeInternal();
        displayMode.setValue(DisplayMode.FILTER);

        activeMovieFilterOptions.setValue(safe);
        movieFilterApplied = true;

        movieRepository.clearBrowseResults();
        movieRepository.loadFirstPageMovieFiltered(safe, () -> {});
    }

    // Activity decides which browse tab to load next (Discover/Popular/New).
    public void clearMovieFilter() {
        movieFilterApplied = false;
        activeMovieFilterOptions.setValue(MovieFilterOptions.defaults());

        exitSearchModeInternal();
        movieRepository.clearBrowseResults();

        displayMode.setValue(DisplayMode.BROWSE);
    }

    // Browse passthroughs

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

    public void selectNew(boolean reselected) {
        exitSearchModeInternal();
        clearFilterStateInternal();
        displayMode.setValue(DisplayMode.BROWSE);

        movieRepository.loadFirstPageNowPlaying(() -> {});
    }

    // Search UI policy + routing (ViewModel-owned)
    // Parses a trailing year for TMDB query params, but preserves the user's original label for history display.
    public void setQuery(@NonNull String query) {
        String label = (query == null) ? "" : query.trim();
        String raw = (query == null) ? "" : query.trim();

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

        lastSearchLabel = label;
        searchFirstPage(raw, year);
    }

    public void searchFirstPage(@NonNull String title, Integer year) {
        String q = (title == null) ? "" : title.trim();
        if (q.isEmpty()) {
            clearSearch();
            return;
        }

        searchMode.setValue(true);
        displayMode.setValue(DisplayMode.SEARCH);

        clearFilterStateInternal();

        lastSearchQuery = q;
        lastSearchYear = year;

        searchHistoryRecordedThisSession = false;

        searchRepository.searchFirstPage(q, year, new SearchRepository.SearchPolicyCallback() {
            @Override
            public void onFirstPageReturnedRealResults() {
                // Record history ONLY after first successful page returns real results.
                recordSearchHistoryIfNeeded();
            }
        });
    }

    public void refreshSearch() {
        if (lastSearchQuery == null || lastSearchQuery.trim().isEmpty()) return;

        searchRepository.refreshSearch(new SearchRepository.SearchPolicyCallback() {
            @Override
            public void onFirstPageReturnedRealResults() {
                // Record history ONLY after first successful page returns real results.
                recordSearchHistoryIfNeeded();
            }
        });
    }

    public void clearSearch() {
        exitSearchModeInternal();
        displayMode.setValue(DisplayMode.BROWSE);
    }

    // Exits search mode without forcing a browse reload; do not push browse results directly (prevents flash).
    public void exitSearchMode() {
        exitSearchModeInternal();

        if (displayMode.getValue() == DisplayMode.SEARCH) {
            displayMode.setValue(DisplayMode.BROWSE);
        }
    }

    public void restoreSearchUiStateOnly() {
        searchMode.setValue(true);
        displayMode.setValue(DisplayMode.SEARCH);
    }

    private void loadNextPageSearch() {
        if (displayMode.getValue() != DisplayMode.SEARCH) return;

        searchRepository.loadNextPageSearch(() -> {
            // Record history ONLY after first successful page returns real results.
            recordSearchHistoryIfNeeded();
        });
    }

    // Suggestions
    public void fetchSuggestions(@NonNull String raw) {
        String q = (raw == null) ? "" : raw.trim();

        if (q.isEmpty()) {
            searchRepository.cancelSuggestionsCall();

            List<String> history = recentSearchQueriesLiveData.getValue();
            ArrayList<Movie> pseudo = new ArrayList<>();

            if (history != null) {
                for (String h : history) {
                    if (h == null) continue;

                    String label = h.trim();
                    if (label.isEmpty()) continue;

                    Movie m = new Movie();
                    m.setId(-1);        // Sentinel: this row is a history entry, not a real TMDB movie
                    m.setTitle(label);  // Store EXACT label the user searched for
                    pseudo.add(m);
                }
            }

            suggestionsLiveData.setValue(pseudo);
            return;
        }

        if (q.length() < 2) {
            suggestionsLiveData.setValue(new ArrayList<>());
            searchRepository.cancelSuggestionsCall();
            return;
        }

        searchRepository.fetchSuggestions(q);
    }

    public void clearSuggestions() {
        searchRepository.clearSuggestions();
        suggestionsLiveData.setValue(new ArrayList<>());
    }

    public void clearSearchResultsOnly() {
        searchRepository.clearSearchResultsOnly();
        lastSearchQuery = "";
        lastSearchYear = null;

        searchHistoryRecordedThisSession = false;
    }

    // Search History
    public void removeSearchHistoryEntry(@NonNull String label) {
        String q = (label == null) ? "" : label.trim();
        if (q.isEmpty()) return;

        searchHistoryRepository.deleteQuery(q);
    }

    // Favorites
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
    // Helpers
    // =====================

    private void exitSearchModeInternal() {
        searchMode.setValue(false);

        searchRepository.clearSearchResultsOnly();

        lastSearchQuery = "";
        lastSearchYear = null;

        searchHistoryRecordedThisSession = false;
    }

    private void clearFilterStateInternal() {
        movieFilterApplied = false;
        activeMovieFilterOptions.setValue(MovieFilterOptions.defaults());
    }

    private void recordSearchHistoryIfNeeded() {
        if (searchHistoryRecordedThisSession) return;

        searchHistoryRecordedThisSession = true;

        String label = (lastSearchLabel == null) ? "" : lastSearchLabel.trim();
        if (!label.isEmpty()) {
            searchHistoryRepository.recordQuery(label);
        }
    }
}