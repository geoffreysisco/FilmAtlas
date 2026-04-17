package com.geoffreysisco.filmatlas.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.geoffreysisco.filmatlas.R;
import com.geoffreysisco.filmatlas.model.Movie;
import com.geoffreysisco.filmatlas.model.Suggestion;

import java.util.ArrayList;
import java.util.List;

public class SearchSuggestionsAdapter extends RecyclerView.Adapter<SearchSuggestionsAdapter.VH> {

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_HEADER = 1;

    public interface Callback {
        void onSuggestionClicked(@NonNull Suggestion suggestion);
        void onSuggestionRemoveClicked(@NonNull Suggestion suggestion);
    }

    private final Callback callback;
    private final List<Suggestion> items = new ArrayList<>();

    public SearchSuggestionsAdapter(@NonNull Callback callback) {
        this.callback = callback;
    }

    public void submit(@NonNull List<Suggestion> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Suggestion suggestion = items.get(position);
        return suggestion.isHeader() ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = (viewType == VIEW_TYPE_HEADER)
                ? R.layout.item_search_suggestion_header
                : R.layout.item_search_suggestion;

        View v = LayoutInflater.from(parent.getContext())
                .inflate(layoutRes, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Suggestion suggestion = items.get(position);

        if (suggestion.isHeader()) {
            String text = "";

            Suggestion.HeaderKind kind = suggestion.getHeaderKind();
            Context context = holder.itemView.getContext();

            if (kind == Suggestion.HeaderKind.RECENT_SEARCHES) {
                text = context.getString(R.string.header_recent_searches);
            } else if (kind == Suggestion.HeaderKind.SUGGESTIONS) {
                text = context.getString(R.string.header_suggestions);
            }

            holder.title.setText(text);

            if (holder.remove != null) {
                holder.remove.setVisibility(View.GONE);
                holder.remove.setOnClickListener(null);
            }

            holder.itemView.setOnClickListener(null);

            return;
        }

        holder.remove.setOnClickListener(null);
        holder.itemView.setOnClickListener(null);

        boolean isHistorySuggestion = suggestion.isHistory();
        holder.title.setText(suggestion.getDisplayLabel());

        if (holder.remove != null) {
            holder.remove.setVisibility(isHistorySuggestion ? View.VISIBLE : View.GONE);

            holder.remove.setOnClickListener(v -> {
                if (!isHistorySuggestion) return;
                callback.onSuggestionRemoveClicked(suggestion);
            });
        }

        holder.itemView.setOnClickListener(v -> callback.onSuggestionClicked(suggestion));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;
        @Nullable
        final ImageView remove;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_suggestion_title);
            remove = itemView.findViewById(R.id.iv_suggestion_remove);
        }
    }
}