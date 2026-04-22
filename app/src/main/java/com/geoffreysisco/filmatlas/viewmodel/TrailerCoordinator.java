package com.geoffreysisco.filmatlas.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.geoffreysisco.filmatlas.repository.MovieRepository;

public class TrailerCoordinator {

    public enum TrailerEventType {
        OPEN_TRAILER,
        TRAILER_UNAVAILABLE,
        NETWORK_ERROR,
        INVALID_MOVIE_ID
    }

    public static final class TrailerEvent {
        @NonNull
        public final TrailerEventType type;

        @Nullable
        public final String youtubeKey;

        public TrailerEvent(@NonNull TrailerEventType type, @Nullable String youtubeKey) {
            this.type = type;
            this.youtubeKey = youtubeKey;
        }
    }

    private final MovieRepository movieRepository;

    private final MutableLiveData<Boolean> trailerLoadingLiveData =
            new MutableLiveData<>(false);

    private final MutableLiveData<TrailerEvent> trailerEventLiveData =
            new MutableLiveData<>(null);

    public TrailerCoordinator(@NonNull MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public LiveData<Boolean> getTrailerLoadingLiveData() {
        return trailerLoadingLiveData;
    }

    public LiveData<TrailerEvent> getTrailerEventLiveData() {
        return trailerEventLiveData;
    }

    public void clearTrailerEvent() {
        trailerEventLiveData.setValue(null);
    }

    public void requestTrailer(int movieId) {
        if (movieId <= 0) {
            trailerEventLiveData.setValue(
                    new TrailerEvent(TrailerEventType.INVALID_MOVIE_ID, null)
            );
            return;
        }

        trailerLoadingLiveData.setValue(true);

        movieRepository.fetchBestTrailerKey(movieId, new MovieRepository.TrailerFetchCallback() {
            @Override
            public void onTrailerKeyReady(@NonNull String youtubeKey) {
                trailerLoadingLiveData.postValue(false);
                trailerEventLiveData.postValue(
                        new TrailerEvent(TrailerEventType.OPEN_TRAILER, youtubeKey)
                );
            }

            @Override
            public void onNoTrailerFound() {
                trailerLoadingLiveData.postValue(false);
                trailerEventLiveData.postValue(
                        new TrailerEvent(TrailerEventType.TRAILER_UNAVAILABLE, null)
                );
            }

            @Override
            public void onFetchFailed() {
                trailerLoadingLiveData.postValue(false);
                trailerEventLiveData.postValue(
                        new TrailerEvent(TrailerEventType.NETWORK_ERROR, null)
                );
            }
        });
    }
}