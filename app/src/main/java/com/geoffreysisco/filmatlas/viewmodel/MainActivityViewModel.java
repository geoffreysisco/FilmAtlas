package com.geoffreysisco.filmatlas.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.geoffreysisco.filmatlas.model.Genre;
import com.geoffreysisco.filmatlas.model.Movie;
import com.geoffreysisco.filmatlas.model.MovieFilterOptions;
import com.geoffreysisco.filmatlas.model.Suggestion;
import com.geoffreysisco.filmatlas.repository.FavoritesRepository;
import com.geoffreysisco.filmatlas.repository.GenresRepository;
import com.geoffreysisco.filmatlas.repository.MovieRepository;
import com.geoffreysisco.filmatlas.repository.SearchHistoryRepository;
import com.geoffreysisco.filmatlas.repository.SearchRepository;

import java.util.List;

/**
 * Responsibilities:
 * - Browse paging lives in MovieRepository (single stream of "grid results")
 * - Search networking + paging + suggestions live in SearchRepository
 * - ViewModel owns UI policy + routing (DisplayMode, no-flash rules, restore behavior)
 * - Genres are cached in Room and exposed to UI as Genre models for MovieFilterBottomSheet
 *
 * IMPORTANT:
 * - MovieFilterOptions is NOT persisted in Room (ever). It is in-memory UI state only.
 *
 * Display routing (prevents list flashing):
 * - BROWSE + FILTER => repository browse stream
 * - SEARCH          => SearchRepository search stream
 */
public class MainActivityViewModel extends AndroidViewModel {

    // =====================
    // Nested types
    // =====================

    private enum DisplayMode {
        BROWSE,
        SEARCH,
        FILTER
    }

    // =====================
    // Instance variables
    // =====================

    // Display mode routing
    private final MutableLiveData<DisplayMode> displayMode =
            new MutableLiveData<>(DisplayMode.BROWSE);

    // Repos / services
    private final MovieRepository movieRepository;
    private final FavoritesRepository favoritesRepository;
    private final GenresRepository genresRepository;
    private final TrailerCoordinator trailerCoordinator;
    private final SearchCoordinator searchCoordinator;
    private final FilterCoordinator filterCoordinator;

    // Streams
    private final MediatorLiveData<List<Movie>> displayMovies = new MediatorLiveData<>();

    // Repo browse stream (used by BROWSE and FILTER)
    private final LiveData<List<Movie>> browseLiveData;

    // Loading: mirrors MovieRepository when not searching; mirrors SearchCoordinator when searching
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);

    // Search mode flag (Activity depends on this existing)
    private final MutableLiveData<Boolean> searchMode = new MutableLiveData<>(false);

    // observeForever observers (stored so they can be removed in onCleared() to avoid leaks)
    private final Observer<Boolean> repoLoadingObserver;
    private final Observer<Boolean> searchRepoLoadingObserver;

    // Genres cache (Room)
    private final LiveData<List<Genre>> genresLiveData;

    // =====================
    // Constructor
    // =====================

    public MainActivityViewModel(@NonNull Application application) {
        super(application);

        movieRepository = new MovieRepository(application);
        favoritesRepository = new FavoritesRepository(application);

        SearchRepository searchRepository = new SearchRepository(application);
        SearchHistoryRepository searchHistoryRepository = new SearchHistoryRepository(application);

        filterCoordinator = new FilterCoordinator();
        trailerCoordinator = new TrailerCoordinator(movieRepository);
        searchCoordinator = new SearchCoordinator(searchRepository, searchHistoryRepository);

        browseLiveData = movieRepository.getBrowseLiveData();

        displayMovies.addSource(browseLiveData, list -> {
            DisplayMode mode = displayMode.getValue();
            if (mode == DisplayMode.BROWSE || mode == DisplayMode.FILTER) {
                displayMovies.setValue(list);
            }
        });

        displayMovies.addSource(searchCoordinator.getSearchResultsLiveData(), list -> {
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
        searchCoordinator.getSearchLoadingLiveData().observeForever(searchRepoLoadingObserver);

        // Genres: repository owns Room observation + network refresh
        genresRepository = new GenresRepository(application);
        genresLiveData = genresRepository.observeGenres();
        genresRepository.refreshGenres();
    }

    // =====================
    // Public methods
    // =====================

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

    // Trailer
    public LiveData<Boolean> getTrailerLoadingLiveData() {
        return trailerCoordinator.getTrailerLoadingLiveData();
    }

    public LiveData<TrailerCoordinator.TrailerEvent> getTrailerEventLiveData() {
        return trailerCoordinator.getTrailerEventLiveData();
    }

    public void clearTrailerEvent() {
        trailerCoordinator.clearTrailerEvent();
    }

    public void requestTrailer(int movieId) {
        trailerCoordinator.requestTrailer(movieId);
    }

    // Genres (Room cache)
    public LiveData<List<Genre>> getGenresLiveData() {
        return genresLiveData;
    }

    // Filter UI events
    public LiveData<Boolean> getFilterEmptyStateEvent() {
        return filterCoordinator.getFilterEmptyStateEvent();
    }

    public void requestShowFilterEmptyState() {
        filterCoordinator.requestShowFilterEmptyState();
    }

    public void requestShowFilterEmptyState(boolean show) {
        filterCoordinator.requestShowFilterEmptyState(show);
    }

    // Filter API (IN-MEMORY ONLY)
    public LiveData<MovieFilterOptions> getActiveMovieFilterOptions() {
        return filterCoordinator.getActiveMovieFilterOptions();
    }

    public boolean isMovieFilterApplied() {
        return filterCoordinator.isMovieFilterApplied();
    }

    // Restore-only: used by Activity state restore when we already have filter results on screen.
    public void restoreMovieFilterApplied(boolean applied) {
        filterCoordinator.restoreMovieFilterApplied(applied);
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
        MovieFilterOptions safe = filter.copy();

        exitSearchModeInternal();
        displayMode.setValue(DisplayMode.FILTER);

        filterCoordinator.markMovieFilterApplied(safe);

        movieRepository.clearBrowseResults();
        movieRepository.loadFirstPageMovieFiltered(safe, () -> {});
    }

    // Activity decides which browse tab to load next (Discover/Popular/New).
    public void clearMovieFilter() {
        filterCoordinator.clearMovieFilterState();

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
    public void setQuery(@NonNull String query) {
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            clearSearch();
            return;
        }

        searchMode.setValue(true);
        displayMode.setValue(DisplayMode.SEARCH);
        clearFilterStateInternal();

        searchCoordinator.setQuery(trimmed);
    }

    public void searchFirstPage(@NonNull String title, Integer year) {
        String q = title.trim();
        if (q.isEmpty()) {
            clearSearch();
            return;
        }

        searchMode.setValue(true);
        displayMode.setValue(DisplayMode.SEARCH);
        clearFilterStateInternal();

        searchCoordinator.searchFirstPage(q, year);
    }

    public void refreshSearch() {
        searchCoordinator.refreshSearch();
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
        searchCoordinator.clearSearchResultsOnly();
    }

    // Suggestions
    public LiveData<List<Suggestion>> getSuggestionsLiveData() {
        return searchCoordinator.getSuggestionsLiveData();
    }

    public void fetchSuggestions(@NonNull String raw) {
        searchCoordinator.fetchSuggestions(raw);
    }

    public void clearSearchResultsOnly() {
        searchCoordinator.clearSearchResultsOnly();
    }

    public void onSuggestionsSessionChanged(boolean active) {
        searchCoordinator.onSuggestionsSessionChanged(active);
    }

    // Search History
    public LiveData<List<String>> getRecentSearchQueries() {
        return searchCoordinator.getRecentSearchQueries();
    }

    public void removeSearchHistoryEntry(@NonNull String label) {
        searchCoordinator.removeSearchHistoryEntry(label);
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
    // Private helpers
    // =====================

    private void loadNextPageSearch() {
        if (displayMode.getValue() != DisplayMode.SEARCH) return;
        searchCoordinator.loadNextPageSearch();
    }

    private void exitSearchModeInternal() {
        searchMode.setValue(false);
        searchCoordinator.clearSearchResultsOnly();
    }

    private void clearFilterStateInternal() {
        filterCoordinator.clearMovieFilterState();
    }

    // =====================
    // Overrides
    // =====================

    @Override
    protected void onCleared() {
        super.onCleared();

        movieRepository.getLoadingLiveData().removeObserver(repoLoadingObserver);
        searchCoordinator.getSearchLoadingLiveData().removeObserver(searchRepoLoadingObserver);

        searchCoordinator.onCleared();
    }
}