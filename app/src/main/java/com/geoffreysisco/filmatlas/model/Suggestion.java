package com.geoffreysisco.filmatlas.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class Suggestion {

    public enum Type {
        HEADER,
        MOVIE,
        HISTORY
    }

    public enum HeaderKind {
        RECENT_SEARCHES,
        SUGGESTIONS
    }

    @NonNull
    private final Type type;

    @Nullable
    private final HeaderKind headerKind;

    @Nullable
    private final Movie movie;

    @Nullable
    private final String text;

    private Suggestion(
            @NonNull Type type,
            @Nullable HeaderKind headerKind,
            @Nullable Movie movie,
            @Nullable String text
    ) {
        this.type = type;
        this.headerKind = headerKind;
        this.movie = movie;
        this.text = text;
    }

    @NonNull
    public static Suggestion header(@NonNull HeaderKind headerKind) {
        return new Suggestion(Type.HEADER, headerKind, null, null);
    }

    @NonNull
    public static Suggestion movie(@NonNull Movie movie) {
        return new Suggestion(Type.MOVIE, null, movie, null);
    }

    @NonNull
    public static Suggestion history(@NonNull String text) {
        return new Suggestion(Type.HISTORY, null, null, text);
    }

    @NonNull
    public Type getType() {
        return type;
    }

    @Nullable
    public HeaderKind getHeaderKind() {
        return headerKind;
    }

    @Nullable
    public Movie getMovie() {
        return movie;
    }

    @Nullable
    public String getText() {
        return text;
    }

    @NonNull
    public String getDisplayLabel() {
        if (isHistory()) {
            return (text == null) ? "" : text.trim();
        }

        if (isMovie()) {
            String title = (movie != null && movie.getTitle() != null)
                    ? movie.getTitle().trim()
                    : "";

            String year = (movie != null && movie.getReleaseYear() != null)
                    ? movie.getReleaseYear().trim()
                    : "";

            return year.isEmpty() ? title : (title + " (" + year + ")");
        }

        return "";
    }

    @NonNull
    public String getQueryLabel() {
        if (isHistory()) {
            return (text == null) ? "" : text.trim();
        }

        if (isMovie()) {
            String title = (movie != null && movie.getTitle() != null)
                    ? movie.getTitle().trim()
                    : "";

            String year = (movie != null && movie.getReleaseYear() != null)
                    ? movie.getReleaseYear().trim()
                    : "";

            return year.isEmpty() ? title : (title + " " + year);
        }

        return "";
    }

    public boolean isHeader() {
        return type == Type.HEADER;
    }

    public boolean isMovie() {
        return type == Type.MOVIE;
    }

    public boolean isHistory() {
        return type == Type.HISTORY;
    }
}