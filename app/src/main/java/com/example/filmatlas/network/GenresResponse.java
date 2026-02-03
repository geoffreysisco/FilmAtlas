package com.example.filmatlas.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GenresResponse {
    @SerializedName("genres")
    public List<GenreDto> genres;
}