package com.geoffreysisco.filmatlas.repository;

import com.geoffreysisco.filmatlas.BuildConfig;
import com.geoffreysisco.filmatlas.model.AppDatabase;
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

    public GenresRepository(AppDatabase db) {
        this.db = db;
        this.api = RetrofitInstance.getService();
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
