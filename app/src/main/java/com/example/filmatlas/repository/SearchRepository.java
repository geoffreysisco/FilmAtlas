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
 * Owns search-related network calls and exposes results via LiveData.
 */
public class SearchRepository {

    // =====================
    // Callbacks
    // =====================

    /**
     * Used by ViewModel to enforce UI policy (ex: history recording)
     * while the repository owns paging and networking.
     */
    public interface SearchPolicyCallback {
        void onFirstPageReturnedRealResults();
    }

    // =====================
    // App Context
    // =====================

    private final Application application;

    // =====================
    // Service
    // =====================

    private final MovieApiService movieApiService;

    // =====================
    // Search Results State
    // =====================

    private boolean searchLoading = false;
    private int searchCurrentPage = 1;
    private int searchTotalPages = Integer.MAX_VALUE;

    private String lastSearchQuery = "";
    private Integer lastSearchYear = null;

    private boolean firstPagePolicyFiredThisSession = false;

    private final MutableLiveData<List<Movie>> searchLiveData = new MutableLiveData<>();

    // =====================
    // Suggestions State (placeholders; wired later)
    // =====================

    private final MutableLiveData<List<Movie>> suggestionsLiveData = new MutableLiveData<>();

    // =====================
    // Loading State
    // =====================

    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);

    // =====================
    // Constructor
    // =====================

    public SearchRepository(@NonNull Application application) {
        this.application = application;
        this.movieApiService = RetrofitInstance.getService();

        searchLiveData.setValue(new ArrayList<>());
        suggestionsLiveData.setValue(new ArrayList<>());
    }

    // =====================
    // LiveData Streams
    // =====================

    public LiveData<List<Movie>> getSearchLiveData() {
        return searchLiveData;
    }

    public LiveData<List<Movie>> getSuggestionsLiveData() {
        return suggestionsLiveData;
    }

    public LiveData<Boolean> getLoadingLiveData() {
        return loadingLiveData;
    }

    // =====================
    // Search API (network-owned)
    // =====================

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

    // =====================
    // Internal helpers
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