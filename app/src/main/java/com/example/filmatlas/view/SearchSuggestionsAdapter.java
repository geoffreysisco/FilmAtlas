package com.example.filmatlas.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.filmatlas.R;
import com.example.filmatlas.model.Movie;

import java.util.ArrayList;
import java.util.List;

public class SearchSuggestionsAdapter extends RecyclerView.Adapter<SearchSuggestionsAdapter.VH> {

    public interface Callback {
        void onSuggestionClicked(@NonNull Movie movie);
        void onSuggestionRemoveClicked(@NonNull Movie movie);
    }

    private final Callback callback;
    private final List<Movie> items = new ArrayList<>();

    public SearchSuggestionsAdapter(@NonNull Callback callback) {
        this.callback = callback;
    }

    public void submit(@NonNull List<Movie> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_suggestion, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Movie m = items.get(position);

        String title = (m.getTitle() == null) ? "" : m.getTitle().trim();
        String year = (m.getReleaseYear() == null) ? "" : m.getReleaseYear().trim();

        boolean isHistorySuggestion = year.isEmpty();

        String label = isHistorySuggestion ? title : (title + " (" + year + ")");
        holder.title.setText(label);

        // Remove icon: only visible for history entries.
        holder.remove.setVisibility(isHistorySuggestion ? View.VISIBLE : View.GONE);

        holder.remove.setOnClickListener(v -> {
            if (!isHistorySuggestion) return;
            callback.onSuggestionRemoveClicked(m);
        });

        holder.itemView.setOnClickListener(v -> callback.onSuggestionClicked(m));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        final ImageView remove;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_suggestion_title);
            remove = itemView.findViewById(R.id.iv_suggestion_remove);
        }
    }
}