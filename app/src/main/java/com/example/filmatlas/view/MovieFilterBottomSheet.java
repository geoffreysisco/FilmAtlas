package com.example.filmatlas.view;

import android.app.Dialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.example.filmatlas.R;
import com.example.filmatlas.model.MovieFilterOptions;
import com.example.filmatlas.model.GenreCacheEntity;
import com.example.filmatlas.viewmodel.MainActivityViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MovieFilterBottomSheet extends BottomSheetDialogFragment {

    private MainActivityViewModel viewModel;
    private Callback callback;


    // Backed by Room (genres table)
    private List<GenreCacheEntity> genres = new ArrayList<>();
    private boolean[] checked = new boolean[0];

    public interface Callback {
        void onFilterCleared();
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.bottom_sheet_movie_filter, container, false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsDialog = (BottomSheetDialog) d;

            FrameLayout sheet = bsDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;

            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);

            boolean isLandscape =
                    requireContext().getResources().getConfiguration().orientation
                            == Configuration.ORIENTATION_LANDSCAPE;

            if (isLandscape) {
                behavior.setSkipCollapsed(true);
                behavior.setHideable(false);

                // Set immediately + again after layout (covers race conditions)
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                sheet.post(() -> behavior.setState(BottomSheetBehavior.STATE_EXPANDED));
            }
        });

        return dialog;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainActivityViewModel.class);

        TextInputLayout genreLayout = view.findViewById(R.id.genre_input_layout);
        MaterialButtonToggleGroup toggleSort = view.findViewById(R.id.toggle_sort);
        MaterialAutoCompleteTextView genreDropdown = view.findViewById(R.id.genre_dropdown);

        // ---- Helper: sync UI from active filter ----
        final Runnable syncUiFromActiveFilter = () -> {
            MovieFilterOptions latest = viewModel.getActiveMovieFilterOptions().getValue();
            if (latest == null) latest = MovieFilterOptions.defaults();

            // Sort init
            if (latest.getSort() == MovieFilterOptions.Sort.VOTE_AVERAGE_DESC) {
                toggleSort.check(R.id.btn_sort_rating);
            } else {
                toggleSort.check(R.id.btn_sort_popularity);
            }

            // Genres init (only if genres loaded)
            if (genres != null) {
                checked = new boolean[genres.size()];

                Set<Integer> selected = new HashSet<>(latest.getGenreIdsReadOnly());
                for (int i = 0; i < genres.size(); i++) {
                    checked[i] = selected.contains(genres.get(i).id);
                }

                genreDropdown.setText(formatSelectedGenres(genres, checked), false);
            } else {
                // genres not loaded yet
                checked = new boolean[0];
                genreDropdown.setText("Any", false);
            }
        };

        // Initial UI sync (sort + "Any" if genres not loaded yet)
        syncUiFromActiveFilter.run();

        // Observe genres from Room and keep UI in sync
        viewModel.getGenresLiveData().observe(getViewLifecycleOwner(), list -> {
            if (list == null) return;

            genres = list;

            // Now that we have genres, sync again so checked[] + dropdown reflect active filter
            syncUiFromActiveFilter.run();
        });

        View.OnClickListener openGenres = v -> {
            if (genres == null || genres.isEmpty()) {
                new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_FilmAtlas_GenreDialog)
                        .setTitle("Genres not loaded yet")
                        .setMessage("Give it a second and try again.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }

            // Defensive: ensure checked[] matches current list size
            if (checked == null || checked.length != genres.size()) {
                checked = new boolean[genres.size()];
            }

            String[] items = new String[genres.size()];
            for (int i = 0; i < genres.size(); i++) {
                items[i] = genres.get(i).name;
            }

            new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_FilmAtlas_GenreDialog)
                    .setTitle("Select genres")
                    .setMultiChoiceItems(
                            items,
                            checked,
                            (dialog, which, isChecked) -> checked[which] = isChecked
                    )
                    .setPositiveButton("OK", (dialog, which) ->
                            genreDropdown.setText(formatSelectedGenres(genres, checked), false)
                    )
                    .setNegativeButton("Cancel", null)
                    .show();
        };

        genreDropdown.setOnClickListener(openGenres);
        if (genreLayout != null) {
            genreLayout.setEndIconOnClickListener(openGenres);
        }

        view.findViewById(R.id.btn_apply).setOnClickListener(v -> {
            MovieFilterOptions f = MovieFilterOptions.defaults();

            int checkedId = toggleSort.getCheckedButtonId();
            if (checkedId == R.id.btn_sort_rating) {
                f.setSort(MovieFilterOptions.Sort.VOTE_AVERAGE_DESC);
            } else {
                f.setSort(MovieFilterOptions.Sort.POPULARITY_DESC);
            }

            if (genres != null && checked != null) {
                for (int i = 0; i < genres.size(); i++) {
                    if (checked[i]) {
                        f.addGenreId(genres.get(i).id);
                    }
                }
            }

            viewModel.applyMovieFilter(f);
            v.postDelayed(this::dismiss, 120);
        });

        view.findViewById(R.id.btn_clear).setOnClickListener(v -> {

            // If nothing is applied, Clear = Close
            if (!viewModel.isMovieFilterApplied()) {
                v.postDelayed(this::dismiss, 120);
                return;
            }

            // Filter WAS applied: Clear it, but keep the sheet open.

            // Reset UI state to defaults first (so user sees it cleared)
            toggleSort.check(R.id.btn_sort_popularity);

            // Clear checked genres
            if (genres != null) {
                checked = new boolean[genres.size()];
            } else {
                checked = new boolean[0];
            }
            genreDropdown.setText("Any", false);

            // Clear filter state (clears results via repository now)
            viewModel.clearMovieFilter();

            // IMPORTANT: tell Activity to show the filter empty-state immediately
            viewModel.requestShowFilterEmptyState();

            // Intentionally do NOT dismiss the bottom sheet
        });
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    private String formatSelectedGenres(List<GenreCacheEntity> genres, boolean[] checked) {
        if (genres == null || genres.isEmpty() || checked == null) return "Any";

        StringBuilder sb = new StringBuilder();
        int n = Math.min(genres.size(), checked.length);

        for (int i = 0; i < n; i++) {
            if (!checked[i]) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(genres.get(i).name);
        }

        return (sb.length() == 0) ? "Any" : sb.toString();
    }
}
