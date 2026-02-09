package com.example.filmatlas.repository;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.filmatlas.model.AppDatabase;
import com.example.filmatlas.model.SearchHistoryDao;
import com.example.filmatlas.model.SearchQueryEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SearchHistoryRepository
 *
 * Local-only persistence for recent search queries.
 *
 * Notes:
 * - Uses primary key (query) to dedupe automatically via REPLACE upsert.
 * - Stores lastUsedEpoch for ordering by recency.
 */
public class SearchHistoryRepository {

    // =====================
    // Fields
    // =====================

    private final SearchHistoryDao dao;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    // =====================
    // Constructor
    // =====================

    public SearchHistoryRepository(@NonNull Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        dao = db.searchHistoryDao();
    }

    // =====================
    // Observe
    // =====================

    public LiveData<List<String>> observeRecentQueries(int limit) {
        LiveData<List<SearchQueryEntity>> entities = dao.observeRecent(limit);

        return Transformations.map(entities, list -> {
            ArrayList<String> out = new ArrayList<>();
            if (list == null) return out;

            for (SearchQueryEntity e : list) {
                if (e == null) continue;
                String q = (e.query == null) ? "" : e.query.trim();
                if (!q.isEmpty()) out.add(q);
            }
            return out;
        });
    }

    // =====================
    // Write
    // =====================

    public void recordQuery(@NonNull String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) return;

        long now = System.currentTimeMillis();
        SearchQueryEntity entity = new SearchQueryEntity(q, now);

        ioExecutor.execute(() -> dao.upsert(entity));
    }

    public void deleteQuery(@NonNull String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) return;

        ioExecutor.execute(() -> dao.deleteByQuery(q));
    }

    public void clearAll() {
        ioExecutor.execute(dao::clearAll);
    }
}
