package com.example.filmatlas.network;

import com.example.filmatlas.model.Trailer;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Represents the TMDB videos payload used by the app to select the best available trailer.
 * The results list may contain multiple video types, but the app filters for a trailer to play.
 */
public class TrailerResponse {

    @SerializedName("results")
    @Expose
    private List<Trailer> results;

    public List<Trailer> getResults() {
        return results;
    }
}
