package com.example.filmatlas.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.filmatlas.R;
import com.example.filmatlas.databinding.MovieListItemBinding;
import com.example.filmatlas.model.Movie;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * RecyclerView adapter for displaying movie items using data binding.
 */
public class MovieAdapter extends ListAdapter<Movie, MovieAdapter.MovieViewHolder> {

    public interface OnMovieClickListener {
        void onMovieClick(Movie movie);
    }

    public interface OnFavoriteClickListener {
        void onFavoriteClick(Movie movie);
    }

    public interface OnShareClickListener {
        void onShareClick(Movie movie);
    }

    private static final Object PAYLOAD_FAVORITE_STATE = new Object();

    private static final DiffUtil.ItemCallback<Movie> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Movie>() {
                @Override
                public boolean areItemsTheSame(@NonNull Movie oldItem, @NonNull Movie newItem) {
                    return Objects.equals(oldItem.getId(), newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Movie oldItem, @NonNull Movie newItem) {
                    return safeEquals(oldItem.getTitle(), newItem.getTitle())
                            && safeEquals(oldItem.getPosterPath(), newItem.getPosterPath())
                            && Double.compare(oldItem.getVoteAverage(), newItem.getVoteAverage()) == 0
                            && safeEquals(oldItem.getOverview(), newItem.getOverview())
                            && safeEquals(oldItem.getReleaseYear(), newItem.getReleaseYear())
                            && safeEquals(oldItem.getBackdropPath(), newItem.getBackdropPath());
                }
            };

    private final OnMovieClickListener listener;
    private final OnFavoriteClickListener favoriteClickListener;
    private final OnShareClickListener shareClickListener;

    // Current favorite IDs (drives heart state)
    private final Set<Integer> favoriteIds = new HashSet<>();

    public MovieAdapter(
            @NonNull OnMovieClickListener listener,
            @NonNull OnFavoriteClickListener favoriteClickListener,
            @NonNull OnShareClickListener shareClickListener
    ) {
        super(DIFF_CALLBACK);
        this.listener = listener;
        this.favoriteClickListener = favoriteClickListener;
        this.shareClickListener = shareClickListener;
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    /**
     * Updates favorite IDs WITHOUT rebinding the whole list.
     * Only rows whose favorite state changed get notified.
     */
    public void setFavoriteIds(@Nullable Set<Integer> ids) {

        // Snapshot old set
        Set<Integer> old = new HashSet<>(favoriteIds);

        // Replace with new set
        favoriteIds.clear();
        if (ids != null) favoriteIds.addAll(ids);

        // Find which IDs changed (symmetric difference)
        Set<Integer> changed = new HashSet<>(old);
        changed.addAll(favoriteIds);

        Set<Integer> intersection = new HashSet<>(old);
        intersection.retainAll(favoriteIds);

        changed.removeAll(intersection);

        if (changed.isEmpty()) return;

        // Notify only affected rows
        for (Integer movieId : changed) {
            int pos = findPositionByMovieId(movieId);
            if (pos != RecyclerView.NO_POSITION) {
                notifyItemChanged(pos, PAYLOAD_FAVORITE_STATE);
            }
        }
    }

    @NonNull
    @Override
    public MovieViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MovieListItemBinding binding = DataBindingUtil.inflate(
                LayoutInflater.from(parent.getContext()),
                R.layout.movie_list_item,
                parent,
                false
        );
        return new MovieViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MovieViewHolder holder, int position) {
        bindFull(holder, getItem(position));
    }

    @Override
    public void onBindViewHolder(
            @NonNull MovieViewHolder holder,
            int position,
            @NonNull java.util.List<Object> payloads
    ) {
        if (payloads.contains(PAYLOAD_FAVORITE_STATE)) {
            // Only update favorite button state (no full rebind)
            Movie movie = getItem(position);
            bindFavoriteStateOnly(holder, movie);
            return;
        }

        super.onBindViewHolder(holder, position, payloads);
    }

    private void bindFull(@NonNull MovieViewHolder holder, @Nullable Movie movie) {
        holder.binding.setMovie(movie);
        holder.binding.executePendingBindings();

        holder.binding.getRoot().setOnClickListener(v -> listener.onMovieClick(movie));

        View favBtn = holder.binding.getRoot().findViewById(R.id.btn_favorite);
        if (favBtn != null) {
            bindFavoriteButton(favBtn, movie);
            favBtn.setOnClickListener(v -> favoriteClickListener.onFavoriteClick(movie));
        }

        View shareBtn = holder.binding.getRoot().findViewById(R.id.btn_share);
        if (shareBtn != null) {
            shareBtn.setOnClickListener(v -> shareClickListener.onShareClick(movie));
        }
    }

    private void bindFavoriteStateOnly(@NonNull MovieViewHolder holder, @Nullable Movie movie) {
        View favBtn = holder.binding.getRoot().findViewById(R.id.btn_favorite);
        if (favBtn != null) {
            bindFavoriteButton(favBtn, movie);
        }
    }

    private void bindFavoriteButton(@NonNull View favBtn, @Nullable Movie movie) {
        boolean isFav = false;
        if (movie != null && movie.getId() != null) {
            isFav = favoriteIds.contains(movie.getId());
        }
        favBtn.setSelected(isFav);
    }

    private int findPositionByMovieId(@Nullable Integer id) {
        if (id == null) return RecyclerView.NO_POSITION;

        // Current list is small enough to linear scan; avoids extra maps.
        for (int i = 0; i < getItemCount(); i++) {
            Movie m = getItem(i);
            if (m != null && Objects.equals(m.getId(), id)) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    private static boolean safeEquals(@Nullable Object a, @Nullable Object b) {
        return Objects.equals(a, b);
    }

    static class MovieViewHolder extends RecyclerView.ViewHolder {
        final MovieListItemBinding binding;

        MovieViewHolder(@NonNull MovieListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}