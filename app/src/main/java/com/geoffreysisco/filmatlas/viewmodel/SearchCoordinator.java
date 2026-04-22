package com.geoffreysisco.filmatlas.viewmodel;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.geoffreysisco.filmatlas.model.Movie;
import com.geoffreysisco.filmatlas.model.Suggestion;
import com.geoffreysisco.filmatlas.repository.SearchHistoryRepository;
import com.geoffreysisco.filmatlas.repository.SearchRepository;

import java.util.ArrayList;
import java.util.List;

public class SearchCoordinator {

    private final SearchRepository searchRepository;
    private final SearchHistoryRepository searchHistoryRepository;

    private final LiveData<List<String>> recentSearchQueriesLiveData;
    private final MutableLiveData<List<Suggestion>> suggestionsLiveData =
            new MutableLiveData<>();

    private final Observer<List<Movie>> searchRepoSuggestionsObserver;
    private final Observer<List<String>> recentSearchQueriesObserver;

    private String lastSuggestionQuery = "";
    private String lastSearchQuery = "";
    private String lastSearchLabel = "";
    private Integer lastSearchYear = null;
    private boolean searchHistoryRecordedThisSession = false;
    private boolean suggestionsSessionActive = false;

    public SearchCoordinator(
            @NonNull SearchRepository searchRepository,
            @NonNull SearchHistoryRepository searchHistoryRepository
    ) {
        this.searchRepository = searchRepository;
        this.searchHistoryRepository = searchHistoryRepository;
        this.recentSearchQueriesLiveData = searchHistoryRepository.observeRecentQueries(10);

        searchRepoSuggestionsObserver = list -> {
            if (!suggestionsSessionActive) return;
            ArrayList<Suggestion> historyItems = buildHistorySuggestions(lastSuggestionQuery);
            ArrayList<Suggestion> merged = new ArrayList<>();

            if (!historyItems.isEmpty()) {
                merged.add(Suggestion.header(Suggestion.HeaderKind.RECENT_SEARCHES));
                merged.addAll(historyItems);
            }

            ArrayList<Suggestion> movieItems = new ArrayList<>();
            if (list != null) {
                for (Movie m : list) {
                    if (m == null) continue;
                    movieItems.add(Suggestion.movie(m));
                }
            }

            if (!movieItems.isEmpty()) {
                merged.add(Suggestion.header(Suggestion.HeaderKind.SUGGESTIONS));
                merged.addAll(movieItems);
            }

            suggestionsLiveData.postValue(merged);
        };

        recentSearchQueriesObserver = history -> {
            if (!suggestionsSessionActive) return;

            if (lastSuggestionQuery.length() < 2) {
                suggestionsLiveData.postValue(buildHistorySection(lastSuggestionQuery));
            }
        };

        this.searchRepository.getSuggestionsLiveData().observeForever(searchRepoSuggestionsObserver);
        this.recentSearchQueriesLiveData.observeForever(recentSearchQueriesObserver);
    }

    public LiveData<List<Suggestion>> getSuggestionsLiveData() {
        return suggestionsLiveData;
    }

    public LiveData<List<String>> getRecentSearchQueries() {
        return recentSearchQueriesLiveData;
    }

    public void fetchSuggestions(@NonNull String raw) {
        suggestionsSessionActive = true;
        String q = raw.trim();
        lastSuggestionQuery = q;

        if (q.isEmpty()) {
            searchRepository.cancelSuggestionsCall();
            suggestionsLiveData.setValue(buildHistorySection(""));
            return;
        }

        if (q.length() < 2) {
            searchRepository.cancelSuggestionsCall();
            suggestionsLiveData.setValue(buildHistorySection(q));
            return;
        }

        searchRepository.fetchSuggestions(q);
    }

    public void removeSearchHistoryEntry(@NonNull String label) {
        String q = label.trim();
        if (q.isEmpty()) return;

        searchHistoryRepository.deleteQuery(q);
    }

    public void onSuggestionsSessionChanged(boolean active) {
        suggestionsSessionActive = active;

        if (!active) {
            lastSuggestionQuery = "";
        }
    }

    public void setQuery(@NonNull String query) {
        String label = query.trim();
        String raw = query.trim();

        Integer year = null;

        java.util.regex.Matcher mParen =
                java.util.regex.Pattern.compile("\\((\\d{4})\\)\\s*$").matcher(raw);
        if (mParen.find()) {
            String yearStr = mParen.group(1);
            if (yearStr != null) {
                year = Integer.parseInt(yearStr);
            }
            raw = raw.substring(0, mParen.start()).trim();
        } else {
            java.util.regex.Matcher mTrail =
                    java.util.regex.Pattern.compile("\\b(\\d{4})\\s*$").matcher(raw);
            if (mTrail.find()) {
                String yearStr = mTrail.group(1);
                if (yearStr != null) {
                    year = Integer.parseInt(yearStr);
                }
                raw = raw.substring(0, mTrail.start()).trim();
            }
        }

        if (raw.isEmpty()) {
            clearSearchResultsOnly();
            return;
        }

        lastSearchLabel = label;
        searchFirstPage(raw, year);
    }

    public void searchFirstPage(@NonNull String title, Integer year) {
        String q = title.trim();
        if (q.isEmpty()) {
            clearSearchResultsOnly();
            return;
        }

        lastSearchQuery = q;
        lastSearchYear = year;
        searchHistoryRecordedThisSession = false;

        searchRepository.searchFirstPage(q, year, new SearchRepository.SearchPolicyCallback() {
            @Override
            public void onFirstPageReturnedRealResults() {
                recordSearchHistoryIfNeeded();
            }
        });
    }

    public void refreshSearch() {
        if (lastSearchQuery == null || lastSearchQuery.trim().isEmpty()) return;

        searchRepository.refreshSearch(new SearchRepository.SearchPolicyCallback() {
            @Override
            public void onFirstPageReturnedRealResults() {
                recordSearchHistoryIfNeeded();
            }
        });
    }

    public void clearSearchResultsOnly() {
        searchRepository.clearSearchResultsOnly();
        lastSearchQuery = "";
        lastSearchYear = null;
        searchHistoryRecordedThisSession = false;
    }

    public void loadNextPageSearch() {
        searchRepository.loadNextPageSearch(this::recordSearchHistoryIfNeeded);
    }

    public LiveData<List<Movie>> getSearchResultsLiveData() {
        return searchRepository.getSearchLiveData();
    }

    public LiveData<Boolean> getSearchLoadingLiveData() {
        return searchRepository.getLoadingLiveData();
    }

    public void onCleared() {
        searchRepository.getSuggestionsLiveData().removeObserver(searchRepoSuggestionsObserver);
        recentSearchQueriesLiveData.removeObserver(recentSearchQueriesObserver);
        searchRepository.cancelSuggestionsCall();
    }

    private void recordSearchHistoryIfNeeded() {
        if (searchHistoryRecordedThisSession) return;

        searchHistoryRecordedThisSession = true;

        String label = lastSearchLabel == null ? "" : lastSearchLabel.trim();
        if (!label.isEmpty()) {
            Log.d("SEARCH_HISTORY", "recordQuery label=[" + label + "]");
            searchHistoryRepository.recordQuery(label);
        }
    }

    @NonNull
    private ArrayList<Suggestion> buildHistorySuggestions(@NonNull String rawQuery) {
        String q = rawQuery.trim().toLowerCase();
        List<String> history = recentSearchQueriesLiveData.getValue();
        ArrayList<Suggestion> items = new ArrayList<>();

        if (history == null) return items;

        for (String h : history) {
            if (h == null) continue;

            String label = h.trim();
            if (label.isEmpty()) continue;

            if (!q.isEmpty() && !label.toLowerCase().contains(q)) continue;

            items.add(Suggestion.history(label));
        }

        return items;
    }

    @NonNull
    private ArrayList<Suggestion> buildHistorySection(@NonNull String rawQuery) {
        ArrayList<Suggestion> historyItems = buildHistorySuggestions(rawQuery);
        ArrayList<Suggestion> section = new ArrayList<>();

        if (!historyItems.isEmpty()) {
            section.add(Suggestion.header(Suggestion.HeaderKind.RECENT_SEARCHES));
            section.addAll(historyItems);
        }

        return section;
    }
}