package com.example.filmatlas.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lightweight Parcelable payload for dialog actions (favorite/share) without requiring a full Movie.
 * Stores the full release date (nullable) so the release year can be derived consistently.
 */
public class MovieActionPayload implements Parcelable {

    private final int movieId;
    @NonNull private final String title;
    private final double rating;
    @NonNull private final String overview;

    @Nullable private final String posterPath;
    @Nullable private final String backdropPath;

    @Nullable private final String releaseDate;

    public MovieActionPayload(
            int movieId,
            @NonNull String title,
            double rating,
            @NonNull String overview,
            @Nullable String posterPath,
            @Nullable String backdropPath,
            @Nullable String releaseDate
    ) {
        this.movieId = movieId;
        this.title = safeNonNull(title);
        this.rating = rating;
        this.overview = safeNonNull(overview);
        this.posterPath = posterPath;
        this.backdropPath = backdropPath;
        this.releaseDate = normalizeNullable(releaseDate);
    }

    protected MovieActionPayload(@NonNull Parcel in) {
        movieId = in.readInt();
        title = safeNonNull(in.readString());
        rating = in.readDouble();
        overview = safeNonNull(in.readString());
        posterPath = in.readString();
        backdropPath = in.readString();
        releaseDate = normalizeNullable(in.readString());
    }

    public static final Creator<MovieActionPayload> CREATOR = new Creator<MovieActionPayload>() {
        @Override
        public MovieActionPayload createFromParcel(Parcel in) {
            return new MovieActionPayload(in);
        }

        @Override
        public MovieActionPayload[] newArray(int size) {
            return new MovieActionPayload[size];
        }
    };

    public int getMovieId() {
        return movieId;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    public double getRating() {
        return rating;
    }

    @NonNull
    public String getOverview() {
        return overview;
    }

    @Nullable
    public String getPosterPath() {
        return posterPath;
    }

    @Nullable
    public String getBackdropPath() {
        return backdropPath;
    }

    @Nullable
    public String getReleaseDate() {
        return releaseDate;
    }

    @Nullable
    public String getReleaseYear() {
        if (releaseDate == null) return null;
        return releaseDate.substring(0, 4);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(movieId);
        dest.writeString(title);
        dest.writeDouble(rating);
        dest.writeString(overview);
        dest.writeString(posterPath);
        dest.writeString(backdropPath);
        dest.writeString(releaseDate);
    }

    @NonNull
    private static String safeNonNull(@Nullable String s) {
        return (s == null) ? "" : s;
    }

    @Nullable
    private static String normalizeNullable(@Nullable String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() < 4) return null;
        return trimmed;
    }
}