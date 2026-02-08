package com.example.filmatlas.view;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.filmatlas.R;
import com.example.filmatlas.model.GenreCacheEntity;
import com.example.filmatlas.model.MovieFilterOptions;
import com.example.filmatlas.viewmodel.MainActivityViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MovieFilterBottomSheet extends BottomSheetDialogFragment {

    // =====================
    // State keys (rotation-safe UI edits)
    // =====================

    private static final String KEY_SORT_CHECKED_ID = "filter_sort_checked_id";
    private static final String KEY_GENRE_CHECKED = "filter_genre_checked";
    private static final String KEY_GENRE_LABEL = "filter_genre_label";

    // =====================
    // Fields
    // =====================

    private MainActivityViewModel viewModel;
    private Callback callback;

    // Backed by Room (genres table)
    private List<GenreCacheEntity> genres = new ArrayList<>();
    private boolean[] checked = new boolean[0];

    // View refs (needed for onSaveInstanceState)
    private TextInputLayout genreLayout;
    private MaterialButtonToggleGroup toggleSort;
    private MaterialAutoCompleteTextView genreDropdown;

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
        return inflater.inflate(R.layout.movie_filter_bottom_sheet, container, false);
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
                behavior.setHideable(true);

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

        // View refs
        genreLayout = view.findViewById(R.id.genre_input_layout);
        toggleSort = view.findViewById(R.id.toggle_sort);
        genreDropdown = view.findViewById(R.id.genre_dropdown);

        final MaterialButton btnClear = view.findViewById(R.id.btn_clear);
        final MaterialButton btnClose = view.findViewById(R.id.btn_close);
        final MaterialButton btnApply = view.findViewById(R.id.btn_apply);

        final Runnable updateClearVisibility = () -> {
            boolean show = viewModel.isMovieFilterApplied() || hasPendingChanges();
            btnClear.setVisibility(show ? View.VISIBLE : View.GONE);
        };

        // Track whether we restored UI edits from rotation
        final boolean restoredFromState = (savedInstanceState != null);

        // Restore in-progress UI edits (if any)
        if (savedInstanceState != null) {

            int savedCheckedId =
                    savedInstanceState.getInt(KEY_SORT_CHECKED_ID, R.id.btn_sort_popularity);
            if (toggleSort != null) toggleSort.check(savedCheckedId);

            boolean[] savedChecked = savedInstanceState.getBooleanArray(KEY_GENRE_CHECKED);
            if (savedChecked != null) checked = savedChecked;

            String savedLabel = savedInstanceState.getString(KEY_GENRE_LABEL, "Any");
            if (genreDropdown != null) genreDropdown.setText(savedLabel, false);
        }

        // ---- Helper: sync UI from active filter (used when not restoring) ----
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
                checked = new boolean[0];
                genreDropdown.setText("Any", false);
            }
        };

        // Initial UI setup:
        // - If we restored from rotation, do NOT overwrite restored edits.
        // - Otherwise, sync from active filter/defaults.
        if (!restoredFromState) {
            syncUiFromActiveFilter.run();
        }

        updateClearVisibility.run();

        toggleSort.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            updateClearVisibility.run();
        });

        // Observe genres from Room and keep UI in sync (careful with restored edits)
        viewModel.getGenresLiveData().observe(getViewLifecycleOwner(), list -> {
            if (list == null) return;

            genres = list;

            if (!restoredFromState) {
                // Normal path: genres arrived, now we can fully sync.
                syncUiFromActiveFilter.run();
                return;
            }

            // Restored path: try to keep restored checked[] meaningful.
            // If sizes mismatch, fall back to active filter (can't safely map).
            if (checked == null || checked.length != genres.size()) {
                syncUiFromActiveFilter.run();
                return;
            }

            // Sizes match: refresh dropdown label from restored checked[] for accuracy.
            genreDropdown.setText(formatSelectedGenres(genres, checked), false);
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
                    .setPositiveButton("OK", (dialog, which) -> {
                        genreDropdown.setText(formatSelectedGenres(genres, checked), false);
                        updateClearVisibility.run();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        };

        genreDropdown.setOnClickListener(openGenres);
        if (genreLayout != null) {
            genreLayout.setEndIconOnClickListener(openGenres);
        }

        btnApply.setOnClickListener(v -> {
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
            updateClearVisibility.run();
            v.postDelayed(this::dismiss, 120);
        });

        btnClear.setOnClickListener(v -> {

            // Reset UI state to defaults (do NOT dismiss)
            toggleSort.check(R.id.btn_sort_popularity);

            if (genres != null) {
                checked = new boolean[genres.size()];
            } else {
                checked = new boolean[0];
            }
            genreDropdown.setText("Any", false);

            // Clear filter state in ViewModel
            viewModel.clearMovieFilter();

            // Tell Activity to show empty state immediately
            viewModel.requestShowFilterEmptyState();
            updateClearVisibility.run();
        });

        btnClose.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (toggleSort != null) {
            outState.putInt(KEY_SORT_CHECKED_ID, toggleSort.getCheckedButtonId());
        }

        if (checked != null) {
            outState.putBooleanArray(KEY_GENRE_CHECKED, checked);
        }

        if (genreDropdown != null) {
            outState.putString(KEY_GENRE_LABEL, String.valueOf(genreDropdown.getText()));
        }
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    private boolean hasPendingChanges() {

        // Default sort = popularity
        int checkedId = (toggleSort != null) ? toggleSort.getCheckedButtonId() : View.NO_ID;

        // If nothing is checked yet (startup moment), treat as NOT changed.
        boolean sortChanged =
                (checkedId != View.NO_ID) && (checkedId != R.id.btn_sort_popularity);

        // Default genres = none selected
        boolean genreChanged = false;
        if (checked != null) {
            for (boolean b : checked) {
                if (b) {
                    genreChanged = true;
                    break;
                }
            }
        }

        return sortChanged || genreChanged;
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
