package com.example.filmatlas.repository;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.filmatlas.BuildConfig;
import com.example.filmatlas.model.Movie;
import com.example.filmatlas.network.MoviesResponse;
import com.example.filmatlas.serviceapi.MovieApiService;
import com.example.filmatlas.serviceapi.RetrofitInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for movie search operations.
 * Owns search paging + suggestions networking and exposes results via LiveData.
 */
public class SearchRepository {

    // Callbacks
    // Used by ViewModel to enforce UI policy (e.g., history recording) while repository owns paging/networking.
    public interface SearchPolicyCallback {
        void onFirstPageReturnedRealResults();
    }

    // Service
    private final MovieApiService movieApiService;

    // Search Results State
    private boolean searchLoading = false;
    private int searchCurrentPage = 1;
    private int searchTotalPages = Integer.MAX_VALUE;

    private String lastSearchQuery = "";
    private Integer lastSearchYear = null;

    private boolean firstPagePolicyFiredThisSession = false;

    private final MutableLiveData<List<Movie>> searchLiveData = new MutableLiveData<>();

    // Suggestions State (placeholders; wired later)
    private final MutableLiveData<List<Movie>> suggestionsLiveData = new MutableLiveData<>();
    private Call<MoviesResponse> suggestionsCall;

    // Loading State
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);

    // Constructor
    public SearchRepository(@NonNull Application application) {
        this.movieApiService = RetrofitInstance.getService();

        // Initialize streams with empty lists to avoid null observers / first-render flashes.
        searchLiveData.setValue(new ArrayList<>());
        suggestionsLiveData.setValue(new ArrayList<>());
    }

    // LiveData Streams
    public LiveData<List<Movie>> getSearchLiveData() {
        return searchLiveData;
    }

    public LiveData<List<Movie>> getSuggestionsLiveData() {
        return suggestionsLiveData;
    }

    public LiveData<Boolean> getLoadingLiveData() {
        return loadingLiveData;
    }

    // Search API (network-owned)
    public void searchFirstPage(
            @NonNull String title,
            Integer year,
            SearchPolicyCallback policyCb
    ) {
        String q = (title == null) ? "" : title.trim();
        if (q.isEmpty()) {
            clearSearchResultsOnly();
            return;
        }

        lastSearchQuery = q;
        lastSearchYear = year;

        firstPagePolicyFiredThisSession = false;

        resetSearchPagingInternal();
        searchLiveData.setValue(new ArrayList<>());

        loadNextPageSearch(policyCb);
    }

    public void refreshSearch(SearchPolicyCallback policyCb) {
        if (lastSearchQuery == null || lastSearchQuery.trim().isEmpty()) return;
        searchFirstPage(lastSearchQuery, lastSearchYear, policyCb);
    }

    public void loadNextPageSearch(SearchPolicyCallback policyCb) {
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

                // Signal policy only once per search session, only on page 1, only if real results arrived.
                if (!firstPagePolicyFiredThisSession
                        && searchCurrentPage == 1
                        && filtered != null
                        && !filtered.isEmpty()) {

                    firstPagePolicyFiredThisSession = true;

                    if (policyCb != null) {
                        policyCb.onFirstPageReturnedRealResults();
                    }
                }

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

    public void clearSearchResultsOnly() {
        resetSearchPagingInternal();

        lastSearchQuery = "";
        lastSearchYear = null;

        firstPagePolicyFiredThisSession = false;

        searchLiveData.setValue(new ArrayList<>());
        loadingLiveData.postValue(false);
    }

    // Suggestions (network-owned)
    public void fetchSuggestions(@NonNull String raw) {
        String q = (raw == null) ? "" : raw.trim();

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

    public void cancelSuggestionsCall() {
        if (suggestionsCall != null) {
            suggestionsCall.cancel();
            suggestionsCall = null;
        }
    }

    // =====================
    // Helpers
    // =====================

    private void resetSearchPagingInternal() {
        searchLoading = false;
        searchCurrentPage = 1;
        searchTotalPages = Integer.MAX_VALUE;
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