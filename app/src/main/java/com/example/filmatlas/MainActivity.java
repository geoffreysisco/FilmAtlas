package com.example.filmatlas;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.filmatlas.databinding.ActivityMainBinding;
import com.example.filmatlas.model.Movie;
import com.example.filmatlas.model.MovieActionPayload;
import com.example.filmatlas.model.MovieFilterOptions;
import com.example.filmatlas.view.MovieAdapter;
import com.example.filmatlas.view.MovieDetailsDialogFragment;
import com.example.filmatlas.view.MovieFilterBottomSheet;
import com.example.filmatlas.view.SearchSuggestionsAdapter;
import com.example.filmatlas.viewmodel.MainActivityViewModel;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Main UI controller for FilmAtlas.
 *
 * This Activity hosts the primary movie grid and coordinates all top-level UI
 * behavior, including browse tabs, favorites, movie filtering, search, swipe
 * navigation, empty states, and system UI integration.
 *
 * It acts as the orchestration layer between view components and
 * MainActivityViewModel, handling user interaction, visual state restoration,
 * and mode transitions without owning data or business logic.
 */
public class MainActivity extends AppCompatActivity
        implements MovieDetailsDialogFragment.OnFavoriteClickListener,
        MovieDetailsDialogFragment.OnShareClickListener {

    // =====================
    // Constants
    // =====================

    private static final String KEY_RECYCLER_LAYOUT_STATE = "recycler_layout_state";
    private static final String KEY_SELECTED_NAV_INDEX = "selected_nav_index";
    private static final String KEY_NAV_BACK_STACK = "nav_back_stack";
    private static final String KEY_WAS_IN_SEARCH_MODE = "was_in_search_mode";
    private static final String KEY_SEARCH_PILL_TEXT = "search_pill_text";
    private static final String KEY_WAS_SEARCH_PILL_FOCUSED = "was_search_pill_focused";
    private static final String KEY_FILTER_MOVIES_JSON = "filter_movies_json";
    private static final String KEY_BROWSE_MOVIES_JSON = "browse_movies_json";
    private static final String KEY_FILTER_APPLIED = "key_filter_applied";

    // Unified index constants
    private static final int NAV_DISCOVER = 0;
    private static final int NAV_POPULAR = 1;
    private static final int NAV_NEW = 2;
    private static final int NAV_FAVORITES = 3;
    private static final int NAV_FILTER = 4;

    private static final int TAB_DISCOVER = 0;
    private static final int TAB_POPULAR = 1;
    private static final int TAB_NEW = 2;

    private static final int MODE_TAB_FAVORITES = 0;
    private static final int MODE_TAB_FILTER = 1;

    private static final int TAB_INDICATOR_HEIGHT_DP = 4;

    private static final int SWIPE_MIN_DISTANCE_DP = 72;
    private static final int SWIPE_EDGE_GUARD_DP = 32;
    private static final int SWIPE_MIN_VELOCITY_DP = 650;

    private enum UiMode {
        BROWSE,
        FAVORITES,
        FILTER
    }

    // =====================
    // Fields
    // =====================
    private ActivityMainBinding binding;
    private MainActivityViewModel viewModel;
    private MovieAdapter movieAdapter;
    private GridLayoutManager gridLayoutManager;
    private Parcelable pendingRvState;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private boolean suppressSuggestionFetch = false;
    private boolean restoringSearchUi = false;
    private boolean restoringBrowseUi = false;
    private boolean restoredBrowseSnapshot = false;
    private Runnable searchSuggestionsRunnable;
    private EditText input;
    private ImageView clear;
    private RecyclerView suggestionsList;
    private SearchSuggestionsAdapter suggestionsAdapter;

    private TabLayout mainTabs;
    private TabLayout modeTabs;
    private TabLayout unifiedTabs;

    private UiMode uiMode = UiMode.BROWSE;
    private int selectedTabIndex = TAB_DISCOVER;
    private int lastBrowseTabIndex = TAB_DISCOVER;
    private int selectedNavIndex = NAV_DISCOVER;
    private final Deque<Integer> navBackStack = new ArrayDeque<>();
    private boolean handlingBackNav = false;
    private int restoredBrowseNavIndex = -1;
    private int pendingRvNavIndex = -1;
    private int lastModeTabIndex = -1;
    private boolean restoringTabs = false;
    private CharSequence[] originalMainTabTitles;
    private CharSequence[] originalModeTabTitles;

    private List<Movie> latestFavorites = java.util.Collections.emptyList();

    private ColorStateList mainTabsTextColors;
    private int mainTabsNormalTextColor;
    private ColorStateList modeTabsTextColors;
    private int modeTabsNormalTextColor;

    private GestureDetector swipeDetector;
    private boolean restoringFromRotation = false;
    private boolean suppressFilterObserverOnceAfterStateRestore = false;
    private boolean didResumeRestore = false;

    // =====================
    // Lifecycle
    // =====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoringBrowseUi = (savedInstanceState != null);

        Intent i = getIntent();
        String action = (i == null) ? "null" : i.getAction();
        Uri data = (i == null) ? null : i.getData();

        EdgeToEdge.enable(this);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        viewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);

        Intent i2 = getIntent();
        String action2 = (i2 == null) ? null : i2.getAction();
        boolean isNormalLauncherStart = Intent.ACTION_MAIN.equals(action2);

        // Resume restore (process death / external return):
        // Only resume to Filter if we have a list snapshot; otherwise fall back to normal startup (Discover).
        if (savedInstanceState == null && !isNormalLauncherStart) {

            SharedPreferences sp = getSharedPreferences("filmatlas_ui", MODE_PRIVATE);

            int resumeNav = sp.getInt("resume_nav", -1);
            int resumeFirst = sp.getInt("resume_first", 0);
            boolean resumeFilterApplied = sp.getBoolean("resume_filter_applied", false);

            String resumeJson = sp.getString("resume_filter_movies_json", "");
            boolean hasResumeJson = (resumeJson != null && !resumeJson.trim().isEmpty());

            if (resumeNav == NAV_FILTER && resumeFilterApplied && hasResumeJson) {

                didResumeRestore = true;

                // Enter filter UI mode without opening bottom sheet / without clearing list.
                selectNavIndex(NAV_FILTER, false);

                // Restore VM applied flag so paging logic is consistent.
                viewModel.restoreMovieFilterApplied(true);

                try {
                    java.lang.reflect.Type type =
                            new com.google.gson.reflect.TypeToken<java.util.List<com.example.filmatlas.model.Movie>>() {}.getType();

                    java.util.List<com.example.filmatlas.model.Movie> restored =
                            new com.google.gson.Gson().fromJson(resumeJson, type);

                    if (restored != null && !restored.isEmpty()) {
                        movieAdapter.submitList(restored, () ->
                                binding.recyclerView.post(() ->
                                        gridLayoutManager.scrollToPosition(Math.max(0, resumeFirst))
                                )
                        );
                    }
                } catch (Exception ignored) {
                    // Ignore restore errors — resume state is non-critical
                }
            }
        }

        mainTabs = binding.mainTabs;
        modeTabs = binding.modeTabs;
        unifiedTabs = binding.unifiedTabs;

        setSupportActionBar(binding.topAppBar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        restoringFromRotation = (savedInstanceState != null);

        if (savedInstanceState != null) {

            // Restore unified nav index first (drives unifiedTabs selection in landscape).
            selectedNavIndex = savedInstanceState.getInt(
                    KEY_SELECTED_NAV_INDEX,
                    mapBrowseTabToNavIndex(selectedTabIndex)
            );

            // Rotation restore: capture which nav this RV state belongs to.
            pendingRvNavIndex = selectedNavIndex;

            pendingRvState = savedInstanceState.getParcelable(KEY_RECYCLER_LAYOUT_STATE);

            // Restore navigation history (if present).
            navBackStack.clear();
            int[] restoredStack = savedInstanceState.getIntArray(KEY_NAV_BACK_STACK);
            if (restoredStack != null && restoredStack.length > 0) {
                for (int v : restoredStack) {
                    navBackStack.addLast(v);
                }
            } else {
                // Fallback: seed with current nav.
                navBackStack.addLast(selectedNavIndex);
            }
        }

        setupSystemInsets();
        setupRecycler();

        if (savedInstanceState != null) {
            String json = savedInstanceState.getString(KEY_FILTER_MOVIES_JSON);

            boolean restoredFilterApplied = savedInstanceState.getBoolean(KEY_FILTER_APPLIED, false);

            if (json != null && !json.trim().isEmpty()) {
                try {
                    java.lang.reflect.Type type =
                            new com.google.gson.reflect.TypeToken<java.util.List<com.example.filmatlas.model.Movie>>() {}.getType();
                    java.util.List<com.example.filmatlas.model.Movie> restored =
                            new com.google.gson.Gson().fromJson(json, type);

                    if (restored != null && !restored.isEmpty()) {

                        // If we restored filter results from state, we must also restore the VM “applied” flag,
                        // otherwise paging will be blocked (canPage=false) after process death restore.
                        if (selectedNavIndex == NAV_FILTER && restoredFilterApplied) {
                            viewModel.restoreMovieFilterApplied(true);
                        }

                        suppressFilterObserverOnceAfterStateRestore = true;

                        movieAdapter.submitList(restored, () -> {
                            binding.recyclerView.post(() -> {

                                hideFilterEmptyState();
                                binding.recyclerView.setVisibility(View.VISIBLE);

                                RecyclerView.LayoutManager lm = binding.recyclerView.getLayoutManager();
                                int lmCount = (lm == null) ? -1 : lm.getItemCount();

                                if (pendingRvState != null && pendingRvNavIndex == selectedNavIndex && lm != null) {
                                    lm.onRestoreInstanceState(pendingRvState);
                                    pendingRvState = null;
                                    pendingRvNavIndex = -1;
                                }

                                int firstNow = gridLayoutManager.findFirstVisibleItemPosition();

                                updateFilterFabVisibility();
                            });
                        });
                    }
                } catch (Exception ignored) {
                    // no-op
                }
            }
        }

        setupFabToTop();

        setupSearchPill();
        setupSearchSuggestions();

        setupTabSystem();
        setupObservers();

        if (savedInstanceState != null) {

            boolean wasInSearch =
                    savedInstanceState.getBoolean(KEY_WAS_IN_SEARCH_MODE, false);

            String pillText =
                    savedInstanceState.getString(KEY_SEARCH_PILL_TEXT, "");

            if (wasInSearch) {

                restoringSearchUi = true;   // ← move it HERE

                viewModel.restoreSearchUiStateOnly();

                boolean wasFocused = savedInstanceState.getBoolean(KEY_WAS_SEARCH_PILL_FOCUSED, false);

                if (input != null) {
                    input.setText(pillText);
                    input.setSelection(pillText.length());

                    if (wasFocused) {
                        input.requestFocus();
                        viewModel.fetchSuggestions(""); // show history list again
                        if (suggestionsList != null) suggestionsList.setVisibility(View.VISIBLE);
                    } else {
                        input.clearFocus();
                        if (suggestionsList != null) suggestionsList.setVisibility(View.GONE);
                    }
                }

                restoringSearchUi = false;  // ← stays here
            }
        }

        setupBackPress();

        setupSwipeDetector();
        installSwipeObserversOnRootAndRecycler();

        binding.btnExitSearch.setOnClickListener(v -> exitSearchBackToLastTab());

        binding.fabRefreshDiscover.setOnClickListener(v -> onDiscoverRefreshClicked());
        binding.fabRefreshDiscover.setVisibility(View.GONE);

        if (binding.btnOpenFilter != null) {
            binding.btnOpenFilter.setOnClickListener(v -> openMovieFilterBottomSheet(false));
        }

        binding.fabFilterApplied.setOnClickListener(v -> openMovieFilterBottomSheet(false));
        binding.fabFilterApplied.setVisibility(View.GONE);

        if (savedInstanceState == null) {

            // Cold start default:
            // If we already resumed/restored to a prior nav (e.g., Filter), do NOT force Discover.
            if (!didResumeRestore) {
                selectNavIndex(NAV_DISCOVER, false);
                recordNavSelection(NAV_DISCOVER);
            }
        } else {
            // Rotation restore: for browse tabs, do not trigger a new fetch.
            if (pendingRvState != null && selectedNavIndex <= NAV_NEW) {

                // Just sync tab visuals to match restored nav.
                if (selectedNavIndex == NAV_DISCOVER) {
                    restoringTabs = true;
                    selectTabSafe(mainTabs, TAB_DISCOVER);
                    if (unifiedTabs != null) selectTabSafe(unifiedTabs, NAV_DISCOVER);
                    restoringTabs = false;
                } else if (selectedNavIndex == NAV_POPULAR) {
                    restoringTabs = true;
                    selectTabSafe(mainTabs, TAB_POPULAR);
                    if (unifiedTabs != null) selectTabSafe(unifiedTabs, NAV_POPULAR);
                    restoringTabs = false;
                } else if (selectedNavIndex == NAV_NEW) {
                    restoringTabs = true;
                    selectTabSafe(mainTabs, TAB_NEW);
                    if (unifiedTabs != null) selectTabSafe(unifiedTabs, NAV_NEW);
                    restoringTabs = false;
                }

                final String json = savedInstanceState.getString(KEY_BROWSE_MOVIES_JSON, "");
                final Parcelable state = savedInstanceState.getParcelable(KEY_RECYCLER_LAYOUT_STATE);

                binding.recyclerView.post(() -> {

                    if (movieAdapter == null) return;

                    if (json == null || json.trim().isEmpty()) return;

                    try {
                        java.lang.reflect.Type type =
                                new com.google.gson.reflect.TypeToken<java.util.List<Movie>>() {}.getType();

                        List<Movie> restored = new com.google.gson.Gson().fromJson(json, type);

                        if (restored == null || restored.isEmpty()) return;

                        restoredBrowseNavIndex = selectedNavIndex;
                        restoredBrowseSnapshot = true;

                        movieAdapter.submitList(restored, () -> {
                            if (state == null) return;

                            RecyclerView.LayoutManager lm = binding.recyclerView.getLayoutManager();
                            if (lm != null) {
                                lm.onRestoreInstanceState(state);
                            }
                        });

                    } catch (Exception ignored) {
                        // no-op
                    }
                });

                uiMode = UiMode.BROWSE;
                setModeTabsVisualsEnabled(false);
                clearModeTabsSelection();
                setMainTabsVisualsEnabled(true);
                hideFilterEmptyState();

                applyMainTabTitles();

            } else {

                boolean wasInSearch =
                        savedInstanceState.getBoolean(KEY_WAS_IN_SEARCH_MODE, false);

                if (!wasInSearch) {

                    selectNavIndex(selectedNavIndex, false);

                    if (restoringBrowseUi) {
                        kickBrowseLoadIfNeeded();
                    }
                }

                // Rotation restore: ONLY re-apply if we have nothing to display (e.g., process death).
                if (selectedNavIndex == NAV_FILTER && viewModel.isMovieFilterApplied()) {

                    List<Movie> current = (movieAdapter == null) ? null : movieAdapter.getCurrentList();
                    boolean hasList = (current != null && !current.isEmpty());

                    if (!hasList) {
                        MovieFilterOptions opts = viewModel.getActiveMovieFilterOptions().getValue();
                        if (opts == null) opts = MovieFilterOptions.defaults();
                        viewModel.applyMovieFilter(opts);
                    }
                }

                // Rotation restore: if we're on Filter and no filter is applied, ensure the correct empty state is visible.
                if (selectedNavIndex == NAV_FILTER && !viewModel.isMovieFilterApplied()) {
                    showFilterEmptyState(false);
                    updateFilterFabVisibility();
                }
            }
        }

        updateFilterFabVisibility();
        applyMainTabTitles();

        // Seed unified navigation history (only if restore did not provide it).
        if (navBackStack.isEmpty()) {
            recordNavSelection(selectedNavIndex);
        }

        restoringFromRotation = false;
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        restoringSearchUi = false; // restore window ends here
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selectedNavIndex == NAV_FILTER && !isInSearchMode()) {
            updateFilterFabVisibility();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Minimal resume hint for cold start (savedInstanceState == null).
        int first = (gridLayoutManager == null) ? 0 : gridLayoutManager.findFirstVisibleItemPosition();
        boolean applied = (viewModel != null && viewModel.isMovieFilterApplied());

        SharedPreferences sp = getSharedPreferences("filmatlas_ui", MODE_PRIVATE);
        SharedPreferences.Editor e = sp.edit();

        e.putInt("resume_nav", selectedNavIndex);
        e.putInt("resume_first", Math.max(0, first));
        e.putBoolean("resume_filter_applied", applied);

        // Only persist list snapshot for Filter+Applied (so we can restore without network)
        if (selectedNavIndex == NAV_FILTER && applied) {

            List<Movie> current = (movieAdapter == null) ? null : movieAdapter.getCurrentList();
            String json = (current == null || current.isEmpty())
                    ? ""
                    : new com.google.gson.Gson().toJson(current);

            e.putString("resume_filter_movies_json", json);
        } else {
            e.remove("resume_filter_movies_json");
        }

        e.apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        RecyclerView.LayoutManager lm = binding.recyclerView.getLayoutManager();
        List<Movie> current = (movieAdapter != null) ? movieAdapter.getCurrentList() : null;

        boolean hasItems = (current != null && !current.isEmpty());
        boolean isGridVisible = (binding.recyclerView.getVisibility() == View.VISIBLE);

        if (uiMode == UiMode.BROWSE
                && selectedNavIndex <= NAV_NEW
                && hasItems) {
            try {
                String json = new com.google.gson.Gson().toJson(current);
                outState.putString(KEY_BROWSE_MOVIES_JSON, json);
            } catch (Exception ignored) {
                // no-op: snapshot is best-effort only
            }
        }

        if (lm != null && hasItems) {

            // For BROWSE tabs, the detail overlay may hide the grid view, but we still want scroll restore
            // when returning from external links (Activity recreate).
            if (uiMode == UiMode.BROWSE && selectedNavIndex <= NAV_NEW) {
                outState.putParcelable(KEY_RECYCLER_LAYOUT_STATE, lm.onSaveInstanceState());
            } else if (isGridVisible) {
                outState.putParcelable(KEY_RECYCLER_LAYOUT_STATE, lm.onSaveInstanceState());
            }
        }

        outState.putInt(KEY_SELECTED_NAV_INDEX, selectedNavIndex);

        int[] stack = new int[navBackStack.size()];
        int i = 0;
        for (Integer v : navBackStack) {
            stack[i++] = (v == null) ? NAV_DISCOVER : v;
        }
        outState.putIntArray(KEY_NAV_BACK_STACK, stack);

        outState.putBoolean(KEY_WAS_IN_SEARCH_MODE, isInSearchMode());

        String pillText = (input == null || input.getText() == null)
                ? ""
                : input.getText().toString();
        outState.putString(KEY_SEARCH_PILL_TEXT, pillText);

        boolean wasFocused = (input != null && input.hasFocus());
        outState.putBoolean(KEY_WAS_SEARCH_PILL_FOCUSED, wasFocused);

        if (uiMode == UiMode.FILTER && movieAdapter != null && movieAdapter.getCurrentList() != null) {
            try {
                String json = new com.google.gson.Gson().toJson(movieAdapter.getCurrentList());
                outState.putString(KEY_FILTER_MOVIES_JSON, json);
                outState.putBoolean(KEY_FILTER_APPLIED, viewModel.isMovieFilterApplied());
            } catch (Exception ignored) {
                // no-op: snapshot is best-effort only
            }

            Parcelable lmState = null;
            RecyclerView.LayoutManager layoutManager = binding.recyclerView.getLayoutManager();
            if (lm != null) lmState = layoutManager.onSaveInstanceState();
            outState.putParcelable(KEY_RECYCLER_LAYOUT_STATE, lmState);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        restoringSearchUi = true;          // keep ON through super restore
        restoringBrowseUi = true;
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeDetector != null) {
            swipeDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    // =====================
    // Setup: base UI
    // =====================

    private void setupSystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupRecycler() {
        int span = getResources().getInteger(R.integer.movie_grid_span);

        gridLayoutManager = new GridLayoutManager(this, span);
        binding.recyclerView.setLayoutManager(gridLayoutManager);

        binding.recyclerView.setItemAnimator(null);

        movieAdapter = new MovieAdapter(
                this::openMovieDetails,
                movie -> viewModel.toggleFavorite(movie),
                this::shareMovie
        );

        movieAdapter.setStateRestorationPolicy(
                RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        );

        binding.recyclerView.setAdapter(movieAdapter);

        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateFabVisibility(dy);

                if (dy <= 0) return;

                boolean canPage =
                        (uiMode == UiMode.BROWSE)
                                || (uiMode == UiMode.FILTER && viewModel.isMovieFilterApplied());

                int visible = gridLayoutManager.getChildCount();
                int total = gridLayoutManager.getItemCount();
                int first = gridLayoutManager.findFirstVisibleItemPosition();

                if (!canPage) return;

                if ((visible + first) >= total - 4) {

                    boolean blockPagingForRotationRestore =
                            restoringFromRotation
                                    && pendingRvState != null
                                    && pendingRvNavIndex == selectedNavIndex;

                    if (blockPagingForRotationRestore) return;

                    viewModel.loadMore();
                }
            }
        });
    }

    private void setupFabToTop() {
        binding.fabToTop.setOnClickListener(v -> {
            binding.fabToTop.hide();

            int first = gridLayoutManager.findFirstVisibleItemPosition();
            if (first > 33) {
                gridLayoutManager.scrollToPositionWithOffset(33, 0);
            }

            if (pendingRvState == null && !restoringFromRotation) {
                binding.recyclerView.post(() -> binding.recyclerView.smoothScrollToPosition(0));
            }
        });
    }

    private void setupBackPress() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {

                if (isInSearchMode()) {
                    exitSearchUiAndMode();
                    return;
                }

                if (popBackStackAndNavigate()) {
                    return;
                }

                finish();
            }
        });
    }

    // =====================
    // Swipe-to-tab navigation
    // =====================

    private void setupSwipeDetector() {
        final int minDistancePx = dpToPx(SWIPE_MIN_DISTANCE_DP);
        final int minVelocityPx = dpToPx(SWIPE_MIN_VELOCITY_DP);
        final int edgeGuardPx = dpToPx(SWIPE_EDGE_GUARD_DP);
        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        swipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @Nullable MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                if (getSupportFragmentManager().findFragmentByTag("MovieFilterBottomSheet") != null) {
                    return false;
                }

                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();

                if (Math.abs(dx) <= Math.abs(dy)) return false;

                if (Math.abs(dx) < Math.max(touchSlop * 2, minDistancePx)) return false;

                if (Math.abs(velocityX) < minVelocityPx) return false;

                View surface = (binding.rootLayout != null) ? binding.rootLayout : binding.getRoot();
                int w = surface.getWidth();
                float startX = e1.getX();
                if (startX <= edgeGuardPx || startX >= (w - edgeGuardPx)) {
                    return false;
                }

                boolean swipeLeft = (dx < 0);
                handleSwipeTab(swipeLeft);
                return true;
            }
        });
    }

    private void installSwipeObserversOnRootAndRecycler() {
        final View root = (binding.rootLayout != null) ? binding.rootLayout : binding.getRoot();

        root.setOnTouchListener((v, event) -> {

            // Always feed swipe detector first
            if (swipeDetector != null) swipeDetector.onTouchEvent(event);

            // Tap-out dismiss (only when suggestions are showing)
            if (event.getAction() == MotionEvent.ACTION_DOWN) {

                boolean suggestionsVisible =
                        (suggestionsList != null && suggestionsList.getVisibility() == View.VISIBLE);

                if (suggestionsVisible) {

                    boolean insideSuggestions = isRawTouchInsideView(event, suggestionsList);
                    boolean insideSearchPill = (binding != null)
                            && (binding.searchActionRoot != null)
                            && isRawTouchInsideView(event, binding.searchActionRoot);

                    // Tap anywhere else → dismiss overlay + drop focus (this is the "tap out")
                    if (!insideSuggestions && !insideSearchPill) {
                        hideSuggestions();

                        if (input != null) input.clearFocus();
                        if (input != null) hideKeyboard(input);
                    }
                }
            }

            if (event.getAction() == MotionEvent.ACTION_UP) v.performClick();
            return false; // don't consume; let tabs/recycler still receive clicks
        });

        binding.recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {

                // Forward touches to swipe detector first
                if (swipeDetector != null) swipeDetector.onTouchEvent(e);

                if (e.getAction() != MotionEvent.ACTION_DOWN) return false;

                boolean suggestionsVisible =
                        (suggestionsList != null && suggestionsList.getVisibility() == View.VISIBLE);

                boolean searchHasFocus = (input != null && input.hasFocus());

                if (!suggestionsVisible && !searchHasFocus) return false;

                // Dismiss suggestions + focus when touching the movie grid
                hideSuggestions();
                if (input != null) input.clearFocus();
                if (input != null) hideKeyboard(input);

                return false; // don't consume → allow normal clicks/swipes
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (swipeDetector != null) swipeDetector.onTouchEvent(e);
            }
        });
    }

    private void handleSwipeTab(boolean swipeLeft) {

        if (getSupportFragmentManager().findFragmentByTag("MovieFilterBottomSheet") != null) {
            return;
        }

        // Swiping while searching should behave like: "leave search and navigate"
        if (isInSearchMode()) {
            exitSearchUiAndMode();
        }

        int current = selectedNavIndex;

        int next;
        if (swipeLeft) {
            next = (current + 1) % 5;
        } else {
            next = (current - 1 + 5) % 5;
        }

        selectNavIndex(next, false);

        // Record swipe navigation in unified history.
        recordNavSelection(next);
    }

    // =====================
    // Setup: search UI
    // =====================

    private void setupSearchPill() {
        input = binding.searchActionInput;
        clear = binding.searchActionClear;

        if (clear != null) clear.setVisibility(View.GONE);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {

                if (restoringSearchUi) return;

                String query = (s == null) ? "" : s.toString().trim();

                if (clear != null) {
                    clear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                }

                if (suppressSuggestionFetch) return;

                if (searchSuggestionsRunnable != null) {
                    searchHandler.removeCallbacks(searchSuggestionsRunnable);
                }

                if (query.length() < 2) {
                    hideSuggestions();
                    return;
                }

                searchSuggestionsRunnable = () -> viewModel.fetchSuggestions(query);
                searchHandler.postDelayed(searchSuggestionsRunnable, 300);
            }
        });

        if (clear != null) {
            clear.setOnClickListener(v -> {
                if (searchSuggestionsRunnable != null) {
                    searchHandler.removeCallbacks(searchSuggestionsRunnable);
                    searchSuggestionsRunnable = null;
                }

                suppressSuggestionFetch = true;
                input.setText("");
                suppressSuggestionFetch = false;

                hideSuggestions();
                viewModel.clearSearchResultsOnly();

                // Clearing the pill does NOT mean leaving search.
                // If the pill is focused and empty, restore recent history suggestions immediately.
                viewModel.fetchSuggestions("");
                if (suggestionsList != null) suggestionsList.setVisibility(View.VISIBLE);

                input.requestFocus();
                showKeyboard(input);

            });
        }

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;

            if (!isInSearchMode()) {
                rememberLastPlaceBeforeSearch();
            }

            String q = v.getText().toString();
            viewModel.setQuery(q);

            if (searchSuggestionsRunnable != null) {
                searchHandler.removeCallbacks(searchSuggestionsRunnable);
                searchSuggestionsRunnable = null;
            }

            hideSuggestions();

            hideKeyboard(v);
            v.clearFocus();
            return true;
        });
    }

    private void setupSearchSuggestions() {
        suggestionsList = binding.searchSuggestionsList;

        suggestionsAdapter = new SearchSuggestionsAdapter(new SearchSuggestionsAdapter.Callback() {
            @Override
            public void onSuggestionClicked(@NonNull Movie movie) {

                if (searchSuggestionsRunnable != null) {
                    searchHandler.removeCallbacks(searchSuggestionsRunnable);
                    searchSuggestionsRunnable = null;
                }

                String title = movie.getTitle();
                if (title == null) title = "";
                title = title.trim();

                String year = movie.getReleaseYear();
                if (year == null) year = "";
                year = year.trim();

                boolean isHistorySuggestion = (movie.getId() != null && movie.getId() == -1);

                String label = year.isEmpty() ? title : (title + " " + year);

                suppressSuggestionFetch = true;
                input.setText(label);
                input.setSelection(input.getText().length());
                suppressSuggestionFetch = false;

                if (!isInSearchMode()) {
                    rememberLastPlaceBeforeSearch();
                }

                if (isHistorySuggestion) {
                    viewModel.setQuery(title);
                } else {
                    viewModel.setQuery(label);
                }

                hideSuggestions();
                hideKeyboard(input);
                input.clearFocus();
            }

            @Override
            public void onSuggestionRemoveClicked(@NonNull Movie movie) {
                viewModel.removeSearchHistoryEntry(movie.getTitle());
            }
        });

        suggestionsList.setLayoutManager(new LinearLayoutManager(this));
        suggestionsList.setAdapter(suggestionsAdapter);

        suggestionsList.addItemDecoration(new RecyclerView.ItemDecoration() {

            private final int heightPx = Math.max(1, dpToPx(1));

            @Override
            public void onDrawOver(@NonNull android.graphics.Canvas c,
                                   @NonNull RecyclerView parent,
                                   @NonNull RecyclerView.State state) {

                RecyclerView.Adapter<?> adapter = parent.getAdapter();
                if (adapter == null) return;

                int itemCount = adapter.getItemCount();
                if (itemCount <= 1) return;

                int left = parent.getPaddingLeft();
                int right = parent.getWidth() - parent.getPaddingRight();

                for (int i = 0; i < parent.getChildCount(); i++) {
                    View child = parent.getChildAt(i);
                    int pos = parent.getChildAdapterPosition(child);
                    if (pos == RecyclerView.NO_POSITION) continue;

                    // Skip last adapter row (no divider below it)
                    if (pos >= itemCount - 1) continue;

                    float top = child.getBottom();
                    float bottom = top + heightPx;

                    // divider color theme-correct
                    android.graphics.Paint p = new android.graphics.Paint();
                    p.setStyle(android.graphics.Paint.Style.FILL);

                    int dividerColor = com.google.android.material.color.MaterialColors.getColor(
                            parent,
                            com.google.android.material.R.attr.colorOutlineVariant,
                            0x33000000
                    );

                    p.setColor(dividerColor);

                    c.drawRect(left, top, right, bottom, p);
                }
            }
        });

        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                hideSuggestions();
                return;
            }

            String text = (input.getText() == null) ? "" : input.getText().toString().trim();
            if (text.isEmpty()) {
                // Empty query → show recent search history suggestions immediately
                viewModel.fetchSuggestions("");
                if (suggestionsList != null) suggestionsList.setVisibility(View.VISIBLE);
            }
        });
    }

    // =====================
    // Setup: Tabs
    // =====================

    private void setupTabSystem() {
        setupMainTabs();
        setupModeTabs();
        setupUnifiedTabs();

        applyTabPipes(mainTabs);
        applyTabPipes(modeTabs);
        applyTabPipes(unifiedTabs);

        setMainTabsVisualsEnabled(true);
        setModeTabsVisualsEnabled(false);
        clearModeTabsSelection();

    }

    private void setupMainTabs() {
        if (mainTabs == null) return;

        mainTabs.clearOnTabSelectedListeners();
        mainTabs.removeAllTabs();

        mainTabs.setTabMode(TabLayout.MODE_FIXED);
        mainTabs.setTabGravity(TabLayout.GRAVITY_FILL);
        mainTabs.setTabIndicatorFullWidth(false);

        mainTabs.addTab(mainTabs.newTab().setText("Discover"));
        mainTabs.addTab(mainTabs.newTab().setText("Popular"));
        mainTabs.addTab(mainTabs.newTab().setText("New"));

        originalMainTabTitles = new CharSequence[mainTabs.getTabCount()];
        for (int i = 0; i < mainTabs.getTabCount(); i++) {
            TabLayout.Tab t = mainTabs.getTabAt(i);
            originalMainTabTitles[i] = (t != null) ? t.getText() : "";
        }

        mainTabsTextColors = mainTabs.getTabTextColors();
        mainTabsNormalTextColor = (mainTabsTextColors != null)
                ? mainTabsTextColors.getDefaultColor()
                : 0;

        int safe = selectedTabIndex;
        if (safe < 0 || safe >= mainTabs.getTabCount()) safe = TAB_DISCOVER;

        restoringTabs = true;
        TabLayout.Tab restored = mainTabs.getTabAt(safe);
        if (restored != null) restored.select();
        restoringTabs = false;

        mainTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {

                if (restoringTabs) return;

                int browsePos = tab.getPosition();
                selectedTabIndex = browsePos;

                // Unify through 0–4 nav index pipeline.
                int nav = mapBrowseTabToNavIndex(browsePos);
                selectNavIndex(nav, false);
                recordNavSelection(nav);

                applyMainTabTitles();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

                if (restoringTabs) return;

                if (isInSearchMode()) {
                    refreshSearchAndHideSuggestions();
                    return;
                }

                if (uiMode == UiMode.FILTER) {
                    refreshFilterOrShowEmptyState();
                    return;
                }

                exitSearchUiAndMode();

                int nav = mapBrowseTabToNavIndex(tab.getPosition());
                selectNavIndex(nav, true);
            }
        });
    }

    private void setupModeTabs() {
        if (modeTabs == null) return;

        modeTabs.clearOnTabSelectedListeners();
        modeTabs.removeAllTabs();

        modeTabs.setTabMode(TabLayout.MODE_FIXED);
        modeTabs.setTabGravity(TabLayout.GRAVITY_FILL);
        modeTabs.setTabIndicatorFullWidth(false);

        modeTabs.addTab(modeTabs.newTab().setText("Favorites"));
        modeTabs.addTab(modeTabs.newTab().setText("Movie Filter"));

        originalModeTabTitles = new CharSequence[modeTabs.getTabCount()];
        for (int i = 0; i < modeTabs.getTabCount(); i++) {
            TabLayout.Tab t = modeTabs.getTabAt(i);
            originalModeTabTitles[i] = (t != null) ? t.getText() : "";
        }

        modeTabsTextColors = modeTabs.getTabTextColors();
        modeTabsNormalTextColor = (modeTabsTextColors != null)
                ? modeTabsTextColors.getDefaultColor()
                : 0;

        modeTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (restoringTabs) return;

                exitSearchUiAndMode();

                if (tab.getPosition() == MODE_TAB_FAVORITES) {
                    enterFavoritesMode();
                    recordNavSelection(NAV_FAVORITES);
                } else {
                    enterFilterMode();
                    recordNavSelection(NAV_FILTER);
                }

                applyMainTabTitles();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                if (restoringTabs) return;
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                if (restoringTabs) return;

                if (isInSearchMode()) {
                    refreshSearchAndHideSuggestions();
                    return;
                }

                if (tab.getPosition() == MODE_TAB_FILTER) {
                    if (uiMode != UiMode.FILTER) {
                        selectNavIndex(NAV_FILTER, false);
                        return;
                    }

                    openMovieFilterBottomSheet(!viewModel.isMovieFilterApplied());
                    return;
                }

                selectNavIndex(NAV_FAVORITES, true);
            }
        });
    }

    private void setupUnifiedTabs() {
        if (unifiedTabs == null) return;

        unifiedTabs.clearOnTabSelectedListeners();
        unifiedTabs.removeAllTabs();

        unifiedTabs.setTabMode(TabLayout.MODE_FIXED);
        unifiedTabs.setTabGravity(TabLayout.GRAVITY_FILL);
        unifiedTabs.setTabIndicatorFullWidth(false);

        unifiedTabs.addTab(unifiedTabs.newTab().setText("Discover"));
        unifiedTabs.addTab(unifiedTabs.newTab().setText("Popular"));
        unifiedTabs.addTab(unifiedTabs.newTab().setText("New"));
        unifiedTabs.addTab(unifiedTabs.newTab().setText("Favorites"));
        unifiedTabs.addTab(unifiedTabs.newTab().setText("Movie Filter"));

        int safe = selectedNavIndex;
        if (safe < 0 || safe >= unifiedTabs.getTabCount()) safe = NAV_DISCOVER;

        restoringTabs = true;
        TabLayout.Tab restored = unifiedTabs.getTabAt(safe);
        if (restored != null) restored.select();
        restoringTabs = false;

        unifiedTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {

                if (restoringTabs) return;

                int nav = tab.getPosition(); // unifiedTabs is already 0–4 in order

                if (nav == NAV_FILTER && isInSearchMode()) {
                    exitSearchUiAndMode();
                }

                selectNavIndex(nav, false);
                recordNavSelection(nav);

                applyMainTabTitles();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

                if (restoringTabs) return;

                if (isInSearchMode()) {
                    refreshSearchAndHideSuggestions();
                    return;
                }

                int nav = tab.getPosition();

                if (nav == NAV_FILTER) {
                    if (uiMode != UiMode.FILTER) {
                        selectNavIndex(NAV_FILTER, false);
                        return;
                    }

                    openMovieFilterBottomSheet(!viewModel.isMovieFilterApplied());
                    return;
                }

                // For non-filter tabs, treat reselection as "reselected" behavior.
                selectNavIndex(nav, true);
            }
        });
    }

    // =====================
    // Observer
    // =====================

    private void setupObservers() {

        viewModel.getDisplayMovies().observe(this, movies -> {

            boolean inSearch = isInSearchMode();

            if (inSearch) {
                hideFilterEmptyState();

                movieAdapter.submitList(movies, () -> {
                    restoreRecyclerStateIfReady(movies);
                    updateEmptyState();
                });

                return;
            }

            if (uiMode == UiMode.FAVORITES) return;

            if (uiMode == UiMode.BROWSE || uiMode == UiMode.FILTER) {

                if (uiMode == UiMode.FILTER
                        && suppressFilterObserverOnceAfterStateRestore
                        && (movies == null || movies.isEmpty())
                        && movieAdapter != null
                        && movieAdapter.getCurrentList() != null
                        && !movieAdapter.getCurrentList().isEmpty()) {

                    suppressFilterObserverOnceAfterStateRestore = false;
                    return;
                }

                movieAdapter.submitList(movies, () -> {
                    restoreRecyclerStateIfReady(movies);

                    if (uiMode == UiMode.FILTER) {

                        // Rotation restore guard:
                        // While restoring RV state, avoid incorrect empty-state transitions.
                        // But if NO filter is applied, we MUST show the "no filter applied" empty state even during restore.
                        if (pendingRvState != null) {

                            // Rotation restore + FILTER APPLIED + 0 RESULTS:
                            // There's nothing to restore, so clear pending restore and show the correct empty state.
                            if (viewModel.isMovieFilterApplied() && (movies == null || movies.isEmpty())) {
                                pendingRvState = null;
                                pendingRvNavIndex = -1;

                                showFilterEmptyState(true);
                                updateFilterFabVisibility();
                                return;
                            }

                            // During rotation restore, keep the list surface visible.
                            if (binding != null && binding.recyclerView != null) {
                                binding.recyclerView.setVisibility(View.VISIBLE);
                            }

                            hideFilterEmptyState();
                            updateFilterFabVisibility();
                            return;
                        }

                        if (movies != null && !movies.isEmpty()) {
                            hideFilterEmptyState();
                        } else {
                            showFilterEmptyState(viewModel.isMovieFilterApplied());
                        }

                        updateFilterFabVisibility();
                        return;
                    }

                    updateEmptyState();
                });
            }
        });

        viewModel.getFavoriteMovies().observe(this, favs -> {
            if (favs == null) favs = java.util.Collections.emptyList();

            latestFavorites = favs;

            if (isInSearchMode()) return;

            if (uiMode == UiMode.FAVORITES) {
                hideFilterEmptyState();
                binding.recyclerView.setVisibility(View.VISIBLE);
                movieAdapter.submitList(favs, this::updateEmptyState);
            }
        });

        viewModel.getFavoriteIds().observe(this, ids -> {
            if (ids == null) ids = java.util.Collections.emptySet();
            movieAdapter.setFavoriteIds(ids);
        });

        viewModel.getLoading().observe(this, loading -> {
            updateEmptyState();
        });

        viewModel.isSearchMode().observe(this, inSearch -> {
            applyMainTabTitles();

            // Search is a top-level UI state: all contextual FABs must react immediately.
            resetToTopFabVisibility();
            updateFilterFabVisibility();
            updateDiscoverRefreshFabVisibility();
        });

        viewModel.getSuggestionsLiveData().observe(this, list -> {

            boolean has = list != null && !list.isEmpty();
            if (has) {
                suggestionsAdapter.submit(list);
                if (suggestionsList != null) suggestionsList.setVisibility(View.VISIBLE);
            } else {
                suggestionsAdapter.clear();
                if (suggestionsList != null) suggestionsList.setVisibility(View.GONE);
            }
        });

        viewModel.getRecentSearchQueries().observe(this, history -> {

            // Only refresh the suggestions surface if the user is currently in the search box with an empty query.
            if (input == null) return;

            boolean focused = input.hasFocus();
            String text = (input.getText() == null) ? "" : input.getText().toString().trim();

            if (focused && text.isEmpty()) {
                viewModel.fetchSuggestions("");
            }
        });

        viewModel.getFilterEmptyStateEvent().observe(this, show -> {
            if (!Boolean.TRUE.equals(show)) return;

            showFilterEmptyState(viewModel.isMovieFilterApplied());
            updateFilterFabVisibility();

            viewModel.requestShowFilterEmptyState(false);
        });
    }

    // =====================
    // Core behavior: mode transitions
    // =====================

    private void selectNavIndex(int navIndex, boolean reselected) {

        boolean restoringSearchState = restoringFromRotation && isInSearchMode();

        // Navigation implies leaving the search field / suggestions context.
        // Keep state (empty/search) intact, just dismiss the overlay.
        if (!restoringSearchState) {
            hideSuggestions();
        }

        // If we're in SEARCH and the user navigates to a browse tab, fully exit search UI + mode.
        if (!restoringSearchState && isInSearchMode() && navIndex <= NAV_NEW) {
            exitSearchUiAndMode();
        }

        // Defensive: if we're moving into browse, the pill should never retain stale text.
        if (!restoringSearchState && navIndex <= NAV_NEW) {
            clearSearchBarUi();
        }

        if (input != null) input.clearFocus();

        selectedNavIndex = navIndex;

        // Rotation restore safety:
        // Only apply a pending RV snapshot when we're still on the nav that snapshot belongs to.
        // (We post to ensure the list/layout is ready; restoring too early often falls back to top.)
        if (pendingRvState != null && pendingRvNavIndex == selectedNavIndex) {
            final Parcelable state = pendingRvState;
            final int stateNav = pendingRvNavIndex;

            binding.recyclerView.post(() -> {
                if (stateNav != selectedNavIndex) return;

                RecyclerView.LayoutManager lm2 = binding.recyclerView.getLayoutManager();
                if (lm2 != null) {
                    lm2.onRestoreInstanceState(state);

                    pendingRvState = null;
                    pendingRvNavIndex = -1;
                }
            });
        }

        handlingBackNav = true;

        switch (navIndex) {
            case NAV_DISCOVER:
                if (mainTabs != null) {
                    restoringTabs = true;
                    selectTabSafe(mainTabs, TAB_DISCOVER);
                    restoringTabs = false;
                }
                enterBrowseModeAndSelectTab(TAB_DISCOVER, reselected);
                break;

            case NAV_POPULAR:
                if (mainTabs != null) {
                    restoringTabs = true;
                    selectTabSafe(mainTabs, TAB_POPULAR);
                    restoringTabs = false;
                }
                enterBrowseModeAndSelectTab(TAB_POPULAR, reselected);
                break;

            case NAV_NEW:
                if (mainTabs != null) {
                    restoringTabs = true;
                    selectTabSafe(mainTabs, TAB_NEW);
                    restoringTabs = false;
                }
                enterBrowseModeAndSelectTab(TAB_NEW, reselected);
                break;

            case NAV_FAVORITES:
                if (modeTabs != null) {
                    restoringTabs = true;
                    selectTabSafe(modeTabs, MODE_TAB_FAVORITES);
                    restoringTabs = false;
                }
                enterFavoritesMode();
                break;

            case NAV_FILTER:
                if (modeTabs != null) {
                    restoringTabs = true;
                    selectTabSafe(modeTabs, MODE_TAB_FILTER);
                    restoringTabs = false;
                }

                // Rotation restore: do NOT auto-open the bottom sheet.
                // Also do NOT call ViewModel enter logic on restore.
                if (restoringFromRotation) {
                    enterFilterMode(false, false);
                } else {
                    enterFilterMode(true, true);
                }
                break;
        }

        handlingBackNav = false;
    }

    private void enterBrowseModeAndSelectTab(int tabIndex, boolean reselected) {
        enterBrowseMode();

        if (mainTabs != null) {
            int current = mainTabs.getSelectedTabPosition();
            if (current != tabIndex) {
                restoringTabs = true;
                selectTabSafe(mainTabs, tabIndex);
                restoringTabs = false;
            }
        }

        selectedTabIndex = tabIndex;
        updateDiscoverRefreshFabVisibility();
        lastBrowseTabIndex = tabIndex;

        selectedNavIndex = mapBrowseTabToNavIndex(tabIndex);

        if (unifiedTabs != null) {
            int current = unifiedTabs.getSelectedTabPosition();
            if (current != selectedNavIndex) {
                restoringTabs = true;
                selectTabSafe(unifiedTabs, selectedNavIndex);
                restoringTabs = false;
            }
        }

        applyBrowseTabSelection(tabIndex, reselected);
        applyMainTabTitles();
    }

    private void enterBrowseMode() {
        uiMode = UiMode.BROWSE;

        resetToTopFabVisibility();
        setModeTabsVisualsEnabled(false);
        clearModeTabsSelection();
        setMainTabsVisualsEnabled(true);

        exitFilterModeUi();

        applyMainTabTitles();
    }

    private void enterFavoritesMode() {

        // Remember where we came from (for Back behavior).
        if (mainTabs != null) {
            int pos = mainTabs.getSelectedTabPosition();
            if (pos >= 0) lastBrowseTabIndex = pos;
        } else {
            lastBrowseTabIndex = selectedTabIndex;
        }

        exitSearchUiAndMode();
        uiMode = UiMode.FAVORITES;

        selectedNavIndex = NAV_FAVORITES;

        if (unifiedTabs != null) {
            int current = unifiedTabs.getSelectedTabPosition();
            if (current != NAV_FAVORITES) {
                restoringTabs = true;
                selectTabSafe(unifiedTabs, NAV_FAVORITES);
                restoringTabs = false;
            }
        }

        resetToTopFabVisibility();
        setMainTabsVisualsEnabled(false);
        clearMainTabsSelection();
        setModeTabsVisualsEnabled(true);

        hideFilterEmptyState();

        binding.emptyStateText.setVisibility(View.GONE);
        binding.btnExitSearch.setVisibility(View.GONE);
        if (suggestionsList != null) suggestionsList.setVisibility(View.GONE);

        binding.recyclerView.setVisibility(View.VISIBLE);

        List<Movie> favs =
                (latestFavorites == null) ? java.util.Collections.emptyList() : latestFavorites;

        movieAdapter.submitList(favs, () -> {
            updateEmptyState();
            applyMainTabTitles();
        });

        updateDiscoverRefreshFabVisibility();
    }

    private void enterFilterMode(boolean showBottomSheet, boolean callViewModel) {

        // Safety: pendingRvState is only meaningful for rotation restore of the SAME nav.
        // If we’re not restoring from rotation and this snapshot belongs to another nav,
        // clear it so it can’t suppress/filter UI behavior.
        if (!restoringFromRotation && pendingRvState != null && pendingRvNavIndex != NAV_FILTER) {
            pendingRvState = null;
            pendingRvNavIndex = -1;
        }

        // Remember where we came from (for Back behavior).
        if (mainTabs != null) {
            int pos = mainTabs.getSelectedTabPosition();
            if (pos >= 0) lastBrowseTabIndex = pos;
        } else {
            lastBrowseTabIndex = selectedTabIndex;
        }

        uiMode = UiMode.FILTER;

        selectedNavIndex = NAV_FILTER;

        if (unifiedTabs != null) {
            int current = unifiedTabs.getSelectedTabPosition();
            if (current != NAV_FILTER) {
                restoringTabs = true;
                selectTabSafe(unifiedTabs, NAV_FILTER);
                restoringTabs = false;
            }
        }

        resetToTopFabVisibility();
        setMainTabsVisualsEnabled(false);
        clearMainTabsSelection();
        setModeTabsVisualsEnabled(true);

        if (callViewModel) {
            viewModel.enterMovieFilterMode();
        }

        // Only treat pendingRvState as a rotation-restore guard
        boolean isRotationRestoreForThisNav =
                restoringFromRotation
                        && pendingRvState != null
                        && pendingRvNavIndex == NAV_FILTER;

        // Rotation restore guard: never force-open the bottom sheet
        if (isRotationRestoreForThisNav) {
            showBottomSheet = false;
        }

        // Rotation restore guard: don't show filter empty state until data re-binds
        if (isRotationRestoreForThisNav) {
            hideFilterEmptyState();
        } else {
            if (!viewModel.isMovieFilterApplied()) {
                showFilterEmptyState(false);
            } else {
                hideFilterEmptyState();
            }
        }

        if (showBottomSheet) {

            resetToTopFabVisibility();

            if (getSupportFragmentManager().findFragmentByTag("MovieFilterBottomSheet") == null) {
                new MovieFilterBottomSheet()
                        .show(getSupportFragmentManager(), "MovieFilterBottomSheet");
            }
        }

        if (binding.recyclerView.getVisibility() == View.VISIBLE
                && movieAdapter != null
                && movieAdapter.getItemCount() == 0) {

            binding.contentContainer.post(() -> showFilterEmptyState(true));
        }

        applyMainTabTitles();
        updateFilterFabVisibility();
        updateDiscoverRefreshFabVisibility();
    }

    private void enterFilterMode() {
        enterFilterMode(true, true);
    }

    // =====================
    // Core behavior: tab titles
    // =====================

    private void applyMainTabTitles() {
        restoreAllMainTabTitles();
        restoreAllModeTabTitles();

        // Landscape (unified tabs): restore baseline titles before applying "Search"
        if (unifiedTabs != null) {
            for (int i = 0; i < unifiedTabs.getTabCount(); i++) {
                TabLayout.Tab t = unifiedTabs.getTabAt(i);
                if (t == null) continue;

                switch (i) {
                    case NAV_DISCOVER:
                        t.setText("Discover");
                        break;
                    case NAV_POPULAR:
                        t.setText("Popular");
                        break;
                    case NAV_NEW:
                        t.setText("New");
                        break;
                    case NAV_FAVORITES:
                        t.setText("Favorites");
                        break;
                    case NAV_FILTER:
                        t.setText("Movie Filter");
                        break;
                }
            }
        }

        if (!isInSearchMode()) return;

        if (mainTabs != null) {
            int selectedPos = mainTabs.getSelectedTabPosition();
            if (selectedPos >= 0 && selectedPos < mainTabs.getTabCount()) {
                TabLayout.Tab t = mainTabs.getTabAt(selectedPos);
                if (t != null) t.setText("Search");
            }
        }

        if (modeTabs != null) {
            int selectedPos = modeTabs.getSelectedTabPosition();
            if (selectedPos >= 0 && selectedPos < modeTabs.getTabCount()) {
                TabLayout.Tab t = modeTabs.getTabAt(selectedPos);
                if (t != null) t.setText("Search");
            }
        }

        if (unifiedTabs != null) {
            int selectedPos = unifiedTabs.getSelectedTabPosition();
            if (selectedPos >= 0 && selectedPos < unifiedTabs.getTabCount()) {
                TabLayout.Tab t = unifiedTabs.getTabAt(selectedPos);
                if (t != null) t.setText("Search");
            }
        }
    }

    private void restoreAllMainTabTitles() {
        if (mainTabs == null || originalMainTabTitles == null) return;

        for (int i = 0; i < mainTabs.getTabCount(); i++) {
            TabLayout.Tab t = mainTabs.getTabAt(i);
            if (t != null) t.setText(originalMainTabTitles[i]);
        }
    }

    private void restoreAllModeTabTitles() {
        if (modeTabs == null || originalModeTabTitles == null) return;

        for (int i = 0; i < modeTabs.getTabCount(); i++) {
            TabLayout.Tab t = modeTabs.getTabAt(i);
            if (t != null) t.setText(originalModeTabTitles[i]);
        }
    }

    // =====================
    // Core behavior: browse selection + filter UI exit
    // =====================

    private void exitFilterModeUi() {
        hideFilterEmptyState();
    }

    private void applyBrowseTabSelection(int tabIndex, boolean reselected) {

        int navForTab = mapBrowseTabToNavIndex(tabIndex);

        if (!reselected && restoredBrowseSnapshot) {

            // Only skip the fetch for the exact nav we restored (one-shot).
            if (navForTab == restoredBrowseNavIndex) {
                restoredBrowseSnapshot = false;
                restoredBrowseNavIndex = -1;
                return;
            }

            // Switching to a different browse tab: do NOT skip. Clear restore flags so normal fetch happens.
            restoredBrowseSnapshot = false;
            restoredBrowseNavIndex = -1;
        }

        if (tabIndex == TAB_DISCOVER) {

            if (viewModel.isMovieFilterApplied() && reselected) {
                viewModel.clearMovieFilter();
                updateFilterFabVisibility();
                return;
            }

            viewModel.selectDiscover(reselected);
            return;
        }

        if (tabIndex == TAB_POPULAR) {
            viewModel.selectPopular(reselected);
            return;
        }

        if (tabIndex == TAB_NEW) {
            viewModel.selectNew(reselected);
        }
    }

    // =====================
    // Callbacks
    // =====================

    private void openMovieDetails(@NonNull Movie movie) {

        int id = (movie.getId() == null) ? -1 : movie.getId();

        MovieActionPayload payload = new MovieActionPayload(
                id,
                (movie.getTitle() == null) ? "" : movie.getTitle(),
                (movie.getVoteAverage() == null) ? 0.0 : movie.getVoteAverage(),
                (movie.getOverview() == null) ? "" : movie.getOverview(),
                movie.getPosterPath(),
                movie.getBackdropPath(),
                movie.getReleaseDate()
        );

        MovieDetailsDialogFragment
                .newInstance(payload)
                .show(getSupportFragmentManager(), "movie_details");
    }

    @Override
    public void onFavoriteClick(@NonNull MovieActionPayload payload) {
        viewModel.toggleFavorite(buildMinimalFavoriteMovieFromPayload(payload));
    }

    @Override
    public void onShareClick(@NonNull MovieActionPayload payload) {
        shareText(buildShareTextFromPayload(payload));
    }

    // =====================
    // Helpers
    // =====================

    // --- Share ---

    private void shareText(@NonNull String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, "Share a Movie"));
    }

    @NonNull
    private String buildShareTextFromPayload(@NonNull MovieActionPayload payload) {

        String title = payload.getTitle().trim();

        String year = payload.getReleaseYear();
        year = (year == null) ? "" : year.trim();

        String label;
        if (!title.isEmpty() && !year.isEmpty()) {
            label = title + " (" + year + ")";
        } else if (!title.isEmpty()) {
            label = title;
        } else {
            label = "Movie";
        }

        int id = payload.getMovieId();
        String url = (id <= 0)
                ? "https://www.themoviedb.org"
                : ("https://www.themoviedb.org/movie/" + id);

        return label + "\n" + url + "\nShared from FilmAtlas";
    }

    private void shareMovie(@NonNull Movie movie) {

        String title = movie.getTitle();
        if (title == null) title = "";
        title = title.trim();

        String year = movie.getReleaseYear();
        if (year == null) year = "";
        year = year.trim();

        String label;
        if (!title.isEmpty() && !year.isEmpty()) {
            label = title + " (" + year + ")";
        } else if (!title.isEmpty()) {
            label = title;
        } else {
            label = "Movie";
        }

        Integer id = movie.getId();
        String url = (id == null)
                ? "https://www.themoviedb.org"
                : ("https://www.themoviedb.org/movie/" + id);

        shareText(label + "\n" + url + "\nShared from FilmAtlas");
    }

    // --- Favorites ---

    @NonNull
    private Movie buildMinimalFavoriteMovieFromPayload(@NonNull MovieActionPayload payload) {
        Movie movie = new Movie();
        movie.setId(payload.getMovieId());
        movie.setTitle(payload.getTitle());
        movie.setPosterPath(payload.getPosterPath());
        movie.setBackdropPath(payload.getBackdropPath());
        movie.setOverview(payload.getOverview());
        movie.setVoteAverage(payload.getRating());
        movie.setReleaseDate(payload.getReleaseDate());
        return movie;
    }

    // --- Discover ---

    private void onDiscoverRefreshClicked() {
        if (uiMode != UiMode.BROWSE) return;
        if (isInSearchMode()) return;
        if (selectedTabIndex != TAB_DISCOVER) return;

        viewModel.selectDiscover(true);

        hideSuggestions();
    }

    // --- Filter ---

    private void refreshFilterOrShowEmptyState() {
        if (viewModel.isMovieFilterApplied()) {
            MovieFilterOptions opts = viewModel.getActiveMovieFilterOptions().getValue();
            if (opts == null) opts = MovieFilterOptions.defaults();
            viewModel.applyMovieFilter(opts);
        } else {
            showFilterEmptyState(false);
        }
    }

    private void openMovieFilterBottomSheet(boolean resetToDefaults) {

        resetToTopFabVisibility();

        if (resetToDefaults) {
            viewModel.clearMovieFilter();
        }

        if (getSupportFragmentManager().findFragmentByTag("MovieFilterBottomSheet") == null) {
            new MovieFilterBottomSheet()
                    .show(getSupportFragmentManager(), "MovieFilterBottomSheet");
        }

        updateFilterFabVisibility();
    }

    private void clearFilterIfLeavingFilterMode() {
        if (uiMode == UiMode.FILTER && viewModel.isMovieFilterApplied()) {
            viewModel.clearMovieFilter();
        }
    }

    // --- Tabs visuals + selection ---

    private void setMainTabsVisualsEnabled(boolean enabled) {
        if (mainTabs == null) return;

        if (enabled) {
            mainTabs.setSelectedTabIndicatorHeight(dpToPx(TAB_INDICATOR_HEIGHT_DP));
            if (mainTabsTextColors != null) mainTabs.setTabTextColors(mainTabsTextColors);
        } else {
            mainTabs.setSelectedTabIndicatorHeight(0);
            if (mainTabsTextColors != null) {
                mainTabs.setTabTextColors(mainTabsNormalTextColor, mainTabsNormalTextColor);
            }
        }

        mainTabs.invalidate();
    }

    private void setModeTabsVisualsEnabled(boolean enabled) {
        if (modeTabs == null) return;

        if (enabled) {
            modeTabs.setSelectedTabIndicatorHeight(dpToPx(TAB_INDICATOR_HEIGHT_DP));
            if (modeTabsTextColors != null) modeTabs.setTabTextColors(modeTabsTextColors);
        } else {
            modeTabs.setSelectedTabIndicatorHeight(0);
            if (modeTabsTextColors != null) {
                modeTabs.setTabTextColors(modeTabsNormalTextColor, modeTabsNormalTextColor);
            }
        }

        modeTabs.invalidate();
    }

    private void clearModeTabsSelection() {
        if (modeTabs == null) return;
        restoringTabs = true;
        modeTabs.selectTab(null);
        restoringTabs = false;
        modeTabs.invalidate();
    }

    private void clearMainTabsSelection() {
        if (mainTabs == null) return;
        restoringTabs = true;
        mainTabs.selectTab(null);
        restoringTabs = false;
        mainTabs.invalidate();
    }

    private void selectTabSafe(@Nullable TabLayout tabs, int index) {
        if (tabs == null) return;
        if (index < 0 || index >= tabs.getTabCount()) return;

        TabLayout.Tab tab = tabs.getTabAt(index);
        if (tab != null) tab.select();
    }

    private void applyTabPipes(@Nullable TabLayout tabs) {
        if (tabs == null) return;

        View child = tabs.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;

        LinearLayout strip = (LinearLayout) child;

        strip.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        strip.setDividerDrawable(
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.tab_divider_vertical)
        );
        strip.setDividerPadding(0);
    }

    // --- Unified navigation index (0–4) ---

    private int mapBrowseTabToNavIndex(int browseTabIndex) {
        if (browseTabIndex == TAB_POPULAR) return NAV_POPULAR;
        if (browseTabIndex == TAB_NEW) return NAV_NEW;
        return NAV_DISCOVER;
    }

    private int mapNavIndexToBrowseTab(int navIndex) {
        if (navIndex == NAV_POPULAR) return TAB_POPULAR;
        if (navIndex == NAV_NEW) return TAB_NEW;
        return TAB_DISCOVER;
    }

    // --- Unified navigation back stack (0–4) ---

    private void recordNavSelection(int navIndex) {
        if (handlingBackNav || restoringTabs) return;

        Integer top = navBackStack.peekLast();
        if (top != null && top == navIndex) return;

        navBackStack.addLast(navIndex);

        // Keep it bounded.
        while (navBackStack.size() > 25) {
            navBackStack.removeFirst();
        }
    }

    private boolean popBackStackAndNavigate() {
        if (navBackStack.size() <= 1) return false;

        // Drop current node.
        navBackStack.removeLast();

        Integer target = navBackStack.peekLast();
        if (target == null) return false;

        handlingBackNav = true;

        selectNavIndex(target, false);
        return true;
    }

    // --- Recycler state ---

    private void restoreRecyclerStateIfReady(List<Movie> movies) {

        if (pendingRvState == null) return;
        if (pendingRvNavIndex != selectedNavIndex) return;
        if (movies == null || movies.isEmpty()) return;

        RecyclerView.LayoutManager lm = binding.recyclerView.getLayoutManager();
        if (lm != null) {
            lm.onRestoreInstanceState(pendingRvState);
        }

        // One-shot: clear after restore so it can't be overwritten by later submits.
        pendingRvState = null;
        pendingRvNavIndex = -1;

        // Post a no-op rebind tick to stabilize the list after state restore (rotation).
        binding.recyclerView.post(() -> movieAdapter.notifyDataSetChanged());
    }

    private void kickBrowseLoadIfNeeded() {
        if (uiMode != UiMode.BROWSE) return;

        // If we restored a snapshot this restore cycle, do not refetch.
        if (restoredBrowseSnapshot) {
            restoringBrowseUi = false;
            return;
        }

        Boolean loadingObj = viewModel.getLoading().getValue();
        boolean loading = Boolean.TRUE.equals(loadingObj);

        List<Movie> current = viewModel.getDisplayMovies().getValue();
        List<Movie> adapterList = (movieAdapter == null) ? null : movieAdapter.getCurrentList();

        boolean empty =
                (current == null || current.isEmpty())
                        && (adapterList == null || adapterList.isEmpty());

        if (loading || !empty) {
            restoringBrowseUi = false;
            return;
        }

        // Still empty + not loading: re-trigger the current browse tab fetch once.
        if (selectedNavIndex == NAV_DISCOVER) {
            viewModel.selectDiscover(false);
        } else if (selectedNavIndex == NAV_POPULAR) {
            viewModel.selectPopular(false);
        } else if (selectedNavIndex == NAV_NEW) {
            viewModel.selectNew(false);
        }

        restoringBrowseUi = false;
    }

    // --- Keyboard ---

    private void hideKeyboard(@NonNull View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showKeyboard(View view) {
        if (view == null) return;

        view.requestFocus();

        view.post(() -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    // --- FAB ---

    private void updateFabVisibility(int dy) {
        int offset = binding.recyclerView.computeVerticalScrollOffset();
        if (offset == 0) {
            binding.fabToTop.hide();
        } else if (dy < 0 && offset > 200) {
            binding.fabToTop.show();
        } else if (dy > 0) {
            binding.fabToTop.hide();
        }
    }

    private void updateFilterFabVisibility() {
        if (binding == null || binding.fabFilterApplied == null) return;

        boolean inSearch = isInSearchMode();

        boolean adapterHasItems =
                (movieAdapter != null) && (movieAdapter.getItemCount() > 0);

        boolean show =
                (selectedNavIndex == NAV_FILTER)
                        && !inSearch
                        && adapterHasItems;

        int desired = show ? View.VISIBLE : View.GONE;
        int current = binding.fabFilterApplied.getVisibility();

        if (current == desired) return;

        binding.fabFilterApplied.setVisibility(desired);
    }

    private void resetToTopFabVisibility() {
        if (binding == null || binding.fabToTop == null) return;
        binding.fabToTop.hide();
    }

    private void updateDiscoverRefreshFabVisibility() {
        if (binding == null || binding.fabRefreshDiscover == null) return;

        // Prefer the tab strip that is actually visible/active.
        int effectiveTabIndex = selectedTabIndex;

        // Landscape/unified: derive browse tab from the unified nav index.
        if (unifiedTabs != null && unifiedTabs.getVisibility() == View.VISIBLE) {
            int navPos = unifiedTabs.getSelectedTabPosition();
            if (navPos >= 0 && navPos <= NAV_NEW) {
                effectiveTabIndex = mapNavIndexToBrowseTab(navPos);
            }
        } else if (mainTabs != null && mainTabs.getVisibility() == View.VISIBLE) {
            int pos = mainTabs.getSelectedTabPosition();
            if (pos >= 0) {
                effectiveTabIndex = pos;
            }
        }

        final boolean isBrowse = (uiMode == UiMode.BROWSE);
        final boolean inSearch = isInSearchMode();
        final boolean isDiscoverTab = (effectiveTabIndex == TAB_DISCOVER);

        final boolean show = isBrowse && !inSearch && isDiscoverTab;

        binding.fabRefreshDiscover.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // --- Empty state ---

    private void updateEmptyState() {

        boolean inSearch = isInSearchMode();

        if (!inSearch && uiMode == UiMode.FAVORITES) {
            int count = (movieAdapter.getCurrentList() == null)
                    ? 0
                    : movieAdapter.getCurrentList().size();

            boolean emptyFavs = (count == 0);

            binding.emptyStateText.setText(R.string.empty_favorites);
            binding.emptyStateText.setVisibility(emptyFavs ? View.VISIBLE : View.GONE);

            binding.btnExitSearch.setVisibility(View.GONE);
            binding.recyclerView.setVisibility(emptyFavs ? View.GONE : View.VISIBLE);
            return;
        }

        if (!inSearch && uiMode != UiMode.BROWSE) return;

        boolean loading = Boolean.TRUE.equals(viewModel.getLoading().getValue());

        boolean empty = viewModel.getDisplayMovies().getValue() == null
                || viewModel.getDisplayMovies().getValue().isEmpty();

        boolean showEmpty = !loading && empty;

        if (uiMode == UiMode.BROWSE && restoringBrowseUi) {
            // Stay in restore window until browse either starts loading OR has data again.
            if (loading || !empty) {
                restoringBrowseUi = false;
            } else {
                showEmpty = false;
            }
        }

        binding.emptyStateText.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(showEmpty ? View.GONE : View.VISIBLE);

        boolean showExit = showEmpty && inSearch;
        binding.btnExitSearch.setVisibility(showExit ? View.VISIBLE : View.GONE);

        if (showExit) {
            binding.btnExitSearch.setText(getBackToBrowseLabel());
        }

        if (!showEmpty) return;

        if (inSearch) {
            String q = (input == null || input.getText() == null)
                    ? ""
                    : input.getText().toString().trim();

            if (q.isEmpty()) {
                binding.emptyStateText.setText(R.string.empty_search_cleared);
            } else {
                binding.emptyStateText.setText(R.string.empty_search_results);
            }
        } else if (uiMode == UiMode.BROWSE) {
            binding.emptyStateText.setText(R.string.empty_browse);
        }
    }

    private void showFilterEmptyState(boolean filterApplied) {
        binding.recyclerView.setVisibility(View.GONE);
        resetToTopFabVisibility();

        binding.emptyStateText.setVisibility(View.GONE);
        binding.btnExitSearch.setVisibility(View.GONE);

        if (binding.emptyStateFilterText != null) {
            binding.emptyStateFilterText.setVisibility(View.VISIBLE);

            binding.emptyStateFilterText.setText(
                    filterApplied
                            ? R.string.empty_filter_no_results
                            : R.string.empty_filter_not_applied
            );
        }

        if (binding.btnOpenFilter != null) {
            binding.btnOpenFilter.setVisibility(View.VISIBLE);
        }
    }

    private void hideFilterEmptyState() {
        if (binding.emptyStateFilterText != null) {
            binding.emptyStateFilterText.setVisibility(View.GONE);
        }

        if (binding.btnOpenFilter != null) {
            binding.btnOpenFilter.setVisibility(View.GONE);
        }

        binding.recyclerView.setVisibility(View.VISIBLE);
    }

    private String getBackToBrowseLabel() {

        if (lastModeTabIndex == MODE_TAB_FAVORITES) return "Back to Favorites";
        if (lastModeTabIndex == MODE_TAB_FILTER) return "Back to Movie Filter";

        switch (lastBrowseTabIndex) {
            case TAB_DISCOVER:
                return "Back to Discover";
            case TAB_POPULAR:
                return "Back to Popular";
            case TAB_NEW:
                return "Back to New";
            default:
                return "Back to Browse";
        }
    }

    // --- Search UI helpers ---

    private void clearSearchBarUi() {
        if (input == null) return;

        suppressSuggestionFetch = true;
        input.setText("");
        suppressSuggestionFetch = false;

        input.clearFocus();
        hideKeyboard(input);

        if (clear != null) clear.setVisibility(View.GONE);
    }

    private void exitSearchUiAndMode() {
        clearSearchBarUi();
        viewModel.exitSearchMode();
        hideSuggestions();
    }

    private void exitSearchBackToLastTab() {
        exitSearchUiAndMode();

        binding.emptyStateText.setVisibility(View.GONE);
        binding.btnExitSearch.setVisibility(View.GONE);

        if (lastModeTabIndex == MODE_TAB_FAVORITES) {
            enterFavoritesMode();
            return;
        }

        if (lastModeTabIndex == MODE_TAB_FILTER) {
            enterFilterMode();
            return;
        }

        selectedTabIndex = lastBrowseTabIndex;
        enterBrowseModeAndSelectTab(lastBrowseTabIndex, false);
    }

    private void rememberLastPlaceBeforeSearch() {
        if (uiMode == UiMode.BROWSE) {
            lastBrowseTabIndex = selectedTabIndex;
            lastModeTabIndex = -1;
        } else if (uiMode == UiMode.FAVORITES) {
            lastModeTabIndex = MODE_TAB_FAVORITES;
        } else if (uiMode == UiMode.FILTER) {
            lastModeTabIndex = MODE_TAB_FILTER;
        }
    }

    private void hideSuggestions() {
        viewModel.clearSuggestions();
        if (suggestionsList != null) suggestionsList.setVisibility(View.GONE);
    }

    private void refreshSearchAndHideSuggestions() {
        viewModel.refreshSearch();
        hideSuggestions();
    }

    // --- Misc ---

    private boolean isRawTouchInsideView(@NonNull MotionEvent e, @Nullable View v) {
        if (v == null) return false;

        int[] loc = new int[2];
        v.getLocationOnScreen(loc);

        float x = e.getRawX();
        float y = e.getRawY();

        return x >= loc[0]
                && x <= loc[0] + v.getWidth()
                && y >= loc[1]
                && y <= loc[1] + v.getHeight();
    }

    private int dpToPx(int dp) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (dp * d + 0.5f);
    }

    private boolean isInSearchMode() {
        return Boolean.TRUE.equals(viewModel.isSearchMode().getValue());
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }
}