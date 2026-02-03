package com.example.filmatlas.serviceapi;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Provides a lazily initialized Retrofit instance for TMDB API access.
 */
public class RetrofitInstance {

    private static final String BASE_URL = "https://api.themoviedb.org/3/";

    private static Retrofit retrofit;

    public static MovieApiService getService() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit.create(MovieApiService.class);
    }
}
