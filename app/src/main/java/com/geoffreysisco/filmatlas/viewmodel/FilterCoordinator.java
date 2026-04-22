package com.geoffreysisco.filmatlas.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.geoffreysisco.filmatlas.model.MovieFilterOptions;

public class FilterCoordinator {

    private final MutableLiveData<MovieFilterOptions> activeMovieFilterOptions =
            new MutableLiveData<>(MovieFilterOptions.defaults());

    private final MutableLiveData<Boolean> filterEmptyStateEvent =
            new MutableLiveData<>(false);

    private boolean movieFilterApplied = false;

    public LiveData<MovieFilterOptions> getActiveMovieFilterOptions() {
        return activeMovieFilterOptions;
    }

    public boolean isMovieFilterApplied() {
        return movieFilterApplied;
    }

    public LiveData<Boolean> getFilterEmptyStateEvent() {
        return filterEmptyStateEvent;
    }

    public void requestShowFilterEmptyState() {
        filterEmptyStateEvent.setValue(true);
    }

    public void requestShowFilterEmptyState(boolean show) {
        filterEmptyStateEvent.setValue(show);
    }

    public void restoreMovieFilterApplied(boolean applied) {
        movieFilterApplied = applied;
    }

    public void setActiveMovieFilterOptions(MovieFilterOptions options) {
        activeMovieFilterOptions.setValue(
                options == null ? MovieFilterOptions.defaults() : options
        );
    }

    public void clearMovieFilterState() {
        movieFilterApplied = false;
        activeMovieFilterOptions.setValue(MovieFilterOptions.defaults());
    }

    public void markMovieFilterApplied(MovieFilterOptions options) {
        activeMovieFilterOptions.setValue(
                options == null ? MovieFilterOptions.defaults() : options
        );
        movieFilterApplied = true;
    }
}