package com.geoffreysisco.filmatlas.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.geoffreysisco.filmatlas.BuildConfig;
import com.geoffreysisco.filmatlas.model.AppDatabase;
import com.geoffreysisco.filmatlas.model.Genre;
import com.geoffreysisco.filmatlas.model.GenreCacheEntity;
import com.geoffreysisco.filmatlas.network.GenreDto;
import com.geoffreysisco.filmatlas.network.GenresResponse;
import com.geoffreysisco.filmatlas.serviceapi.MovieApiService;
import com.geoffreysisco.filmatlas.serviceapi.RetrofitInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GenresRepository {

    private final AppDatabase db;
    private final MovieApiService api;
    private final Executor dbExecutor = Executors.newSingleThreadExecutor();

    public GenresRepository(Application application) {
        this.db = AppDatabase.getInstance(application);
        this.api = RetrofitInstance.getService();
    }

    public LiveData<List<Genre>> observeGenres() {
        androidx.lifecycle.MediatorLiveData<List<Genre>> result =
                new androidx.lifecycle.MediatorLiveData<>();

        result.addSource(db.genreDao().observeAll(), entities -> {
            if (entities == null) {
                result.setValue(null);
                return;
            }

            List<Genre> mapped = new ArrayList<>(entities.size());
            for (GenreCacheEntity e : entities) {
                mapped.add(new Genre(e.id, e.name));
            }

            result.setValue(mapped);
        });

        return result;
    }

    public void refreshGenres() {
        api.getMovieGenres(BuildConfig.TMDB_API_KEY).enqueue(new Callback<GenresResponse>() {
            @Override
            public void onResponse(Call<GenresResponse> call, Response<GenresResponse> response) {
                GenresResponse body = response.body();
                if (!response.isSuccessful() || body == null || body.genres == null) return;

                List<GenreCacheEntity> entities = new ArrayList<>();
                for (GenreDto dto : body.genres) {
                    entities.add(new GenreCacheEntity(dto.id, dto.name));
                }

                dbExecutor.execute(() ->
                        db.genreDao().upsertAll(entities)
                );
            }

            @Override
            public void onFailure(Call<GenresResponse> call, Throwable t) {
                // Safe to ignore for now (cached data)
            }
        });
    }
}
