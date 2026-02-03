package com.example.filmatlas;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
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
import com.example.filmatlas.model.MovieFilterOptions;
import com.example.filmatlas.view.MovieAdapter;
import com.example.filmatlas.view.MovieDetailsDialogFragment;
import com.example.filmatlas.view.MovieFilterBottomSheet;
import com.example.filmatlas.view.SearchSuggestionsAdapter;
import com.example.filmatlas.viewmodel.MainActivityViewModel;
import com.google.android.material.tabs.TabLayout;

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

public class MainActivity extends AppCompatActivity {

    // =====================
    // Constants
    // =====================

    // Saved state keys
    private static final String KEY_UI_MODE = "ui_mode";
    private static final String KEY_RECYCLER_LAYOUT_STATE = "recycler_layout_state";
    private static final String KEY_SELECTED_BROWSE_TAB = "selected_browse_tab";
    private static final String KEY_SELECTED_MODE_TAB = "selected_mode_tab";

    // Main tabs: indices
    private static final int TAB_DISCOVER = 0;
    private static final int TAB_POPULAR = 1;
    private static final int TAB_NOW_PLAYING = 2;

    // Mode tabs: indices
    private static final int MODE_TAB_FAVORITES = 0;
    private static final int MODE_TAB_FILTER = 1;

    // UI constants
    private static final int TAB_INDICATOR_HEIGHT_DP = 4;

    // Swipe constants
    private static final int SWIPE_MIN_DISTANCE_DP = 72;   // horizontal distance
    private static final int SWIPE_EDGE_GUARD_DP = 32;     // ignore swipes near edges
    private static final int SWIPE_MIN_VELOCITY_DP = 650;  // fling velocity

    private enum UiMode {
        BROWSE,
        FAVORITES,
        FILTER
    }

    // =====================
    // Fields
    // =====================

    // Core
    private ActivityMainBinding binding;
    private MainActivityViewModel viewModel;
    private MovieAdapter movieAdapter;
    private GridLayoutManager gridLayoutManager;
    private Parcelable pendingRvState;

    // Search
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private boolean suppressSuggestionFetch = false;
    private boolean skipSuggestionFetchBecauseRotation = false;
    private Runnable searchSuggestionsRunnable;
    private EditText input;
    private ImageView clear;
    private RecyclerView suggestionsList;
    private SearchSuggestionsAdapter suggestionsAdapter;

    // Tabs + mode
    private TabLayout mainTabs;
    private TabLayout modeTabs;
    private UiMode uiMode = UiMode.BROWSE;
    private int selectedTabIndex = TAB_DISCOVER;
    private int lastBrowseTabIndex = TAB_DISCOVER;
    private int lastModeTabIndex = -1; // -1 = came from browse tabs
    private boolean restoringTabs = false;
    private CharSequence[] originalMainTabTitles;
    private CharSequence[] originalModeTabTitles;

    // Favorites
    private List<Movie> latestFavorites = java.util.Collections.emptyList();

    // Tab visuals
    private ColorStateList mainTabsTextColors;
    private int mainTabsNormalTextColor;
    private ColorStateList modeTabsTextColors;
    private int modeTabsNormalTextColor;

    // Swipe
    private GestureDetector swipeDetector;

    // =====================
    // Lifecycle
    // =====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        viewModel = new ViewModelProvider(this).get(MainActivityViewModel.class);

        mainTabs = binding.mainTabs;
        modeTabs = binding.modeTabs;

        setSupportActionBar(binding.topAppBar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Restore saved state (visual only)
        skipSuggestionFetchBecauseRotation = (savedInstanceState != null);

        String restoredUiModeName = UiMode.BROWSE.name();
        int restoredModeTabPos = -1;

        if (savedInstanceState != null) {
            selectedTabIndex = savedInstanceState.getInt(KEY_SELECTED_BROWSE_TAB, TAB_DISCOVER);
            pendingRvState = savedInstanceState.getParcelable(KEY_RECYCLER_LAYOUT_STATE);

            restoredUiModeName = savedInstanceState.getString(KEY_UI_MODE, UiMode.BROWSE.name());
            restoredModeTabPos = savedInstanceState.getInt(KEY_SELECTED_MODE_TAB, -1);
        }

        // Base UI setup
        setupSystemInsets();
        setupRecycler();
        setupFabToTop();
        setupSwipeRefresh();

        // Search setup
        setupSearchPill();
        setupSearchSuggestions();

        // Tabs, observers, and back navigation
        setupTabSystem();
        setupObservers();
        setupBackPress();

        // Swipe navigation
        setupSwipeDetector();
        installSwipeObserversOnRootAndRecycler();

        // Click listeners
        binding.btnExitSearch.setOnClickListener(v -> exitSearchBackToLastTab());
        binding.btnOpenFilter.setOnClickListener(v -> openMovieFilterBottomSheet(true));

        binding.fabFilterApplied.setOnClickListener(v -> openMovieFilterBottomSheet(false));
        binding.fabFilterApplied.setVisibility(View.GONE);

        // Initial UI state
        if (savedInstanceState == null) {
            enterBrowseModeAndSelectTab(TAB_DISCOVER, false);
        } else {
            restoreInitialUi(restoredUiModeName, restoredModeTabPos, selectedTabIndex);
        }

        // Final UI adjustments
        updateFilterFabVisibility();
        applyMainTabTitles();

        // Never show stale suggestions on rotate
        hideSuggestions();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {

        super.onSaveInstanceState(outState);

        int selectedBrowseTabPosition =
                (mainTabs != null) ? mainTabs.getSelectedTabPosition() : selectedTabIndex;
        if (selectedBrowseTabPosition < 0) selectedBrowseTabPosition = lastBrowseTabIndex;
        outState.putInt(KEY_SELECTED_BROWSE_TAB, selectedBrowseTabPosition);

        outState.putString(KEY_UI_MODE, (uiMode != null) ? uiMode.name() : UiMode.BROWSE.name());

        int selectedModeTabPosition =
                (modeTabs != null) ? modeTabs.getSelectedTabPosition() : -1;
        outState.putInt(KEY_SELECTED_MODE_TAB, selectedModeTabPosition);

        RecyclerView.LayoutManager lm = binding.recyclerView.getLayoutManager();
        if (lm != null) {
            outState.putParcelable(KEY_RECYCLER_LAYOUT_STATE, lm.onSaveInstanceState());
        }
    }

    private void restoreInitialUi(@NonNull String restoredUiModeName,
                                  int restoredModeTabPosition,
                                  int restoredBrowseTabPosition) {

        boolean inSearch = isInSearchMode();

        if (UiMode.FILTER.name().equals(restoredUiModeName)) {

            restoringTabs = true;
            selectTabSafe(modeTabs, (restoredModeTabPosition >= 0) ? restoredModeTabPosition : MODE_TAB_FILTER);
            restoringTabs = false;

            if (inSearch) {
                uiMode = UiMode.FILTER;
                setMainTabsVisualsEnabled(false);
                clearMainTabsSelection();
                setModeTabsVisualsEnabled(true);
                hideFilterEmptyState();
            } else {
                enterFilterMode(false, false);
            }
            return;
        }

        if (UiMode.FAVORITES.name().equals(restoredUiModeName)) {

            restoringTabs = true;
            selectTabSafe(modeTabs, (restoredModeTabPosition >= 0) ? restoredModeTabPosition : MODE_TAB_FAVORITES);
            restoringTabs = false;

            if (inSearch) {
                uiMode = UiMode.FAVORITES;
                setMainTabsVisualsEnabled(false);
                clearMainTabsSelection();
                setModeTabsVisualsEnabled(true);
                hideFilterEmptyState();
                binding.recyclerView.setVisibility(View.VISIBLE);
            } else {
                enterFavoritesMode();
            }
            return;
        }

        uiMode = UiMode.BROWSE;

        setModeTabsVisualsEnabled(false);
        clearModeTabsSelection();
        setMainTabsVisualsEnabled(true);

        restoringTabs = true;
        selectTabSafe(mainTabs, restoredBrowseTabPosition);
        restoringTabs = false;

        if (!inSearch) {

            // On rotation, do NOT reload the tab contents.
            // Let LiveData re-emit and restore scroll via pendingRvState.
            if (pendingRvState == null) {
                applyBrowseTabSelection(restoredBrowseTabPosition, false);
            }
        }

        exitFilterModeUi();
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
                if (uiMode != UiMode.BROWSE) return;

                int visible = gridLayoutManager.getChildCount();
                int total = gridLayoutManager.getItemCount();
                int first = gridLayoutManager.findFirstVisibleItemPosition();

                if ((visible + first) >= total - 4) {
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

            binding.recyclerView.post(() -> binding.recyclerView.smoothScrollToPosition(0));
        });
    }

    private void setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.black);
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {

            if (isInSearchMode()) {
                refreshSearchAndHideSuggestions();
                binding.swipeRefreshLayout.setRefreshing(false);
                return;
            }

            if (uiMode == UiMode.FAVORITES) {
                binding.swipeRefreshLayout.setRefreshing(false);
                return;
            }

            if (uiMode == UiMode.FILTER) {
                openMovieFilterBottomSheet(!viewModel.isMovieFilterApplied());
                binding.swipeRefreshLayout.setRefreshing(false);
                return;
            }

            applyBrowseTabSelection(selectedTabIndex, true);
            binding.swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void setupBackPress() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {

                if (uiMode == UiMode.FAVORITES || uiMode == UiMode.FILTER) {
                    enterBrowseModeAndSelectTab(selectedTabIndex, false);
                    return;
                }

                if (isInSearchMode()) {
                    exitSearchUiAndMode();
                } else {
                    finish();
                }
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
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                if (isInSearchMode()) return false;

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
                    // Ignored edge swipe
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
            if (swipeDetector != null) swipeDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) v.performClick();
            return false;
        });

        binding.recyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (swipeDetector != null) swipeDetector.onTouchEvent(e);
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (swipeDetector != null) swipeDetector.onTouchEvent(e);
            }
        });
    }

    private void handleSwipeTab(boolean swipeLeft) {

        final int currentNode;
        if (uiMode == UiMode.FAVORITES) {
            currentNode = 1;
        } else if (uiMode == UiMode.FILTER) {
            currentNode = 2;
        } else {
            if (selectedTabIndex == TAB_DISCOVER) currentNode = 0;
            else if (selectedTabIndex == TAB_POPULAR) currentNode = 4;
            else currentNode = 3;
        }

        final int nextNode;
        if (swipeLeft) {
            nextNode = (currentNode - 1 + 5) % 5;
        } else {
            nextNode = (currentNode + 1) % 5;
        }

        switch (nextNode) {
            case 0:
                enterBrowseModeAndSelectTab(TAB_DISCOVER, false);
                return;

            case 1:
                restoringTabs = true;
                selectTabSafe(modeTabs, MODE_TAB_FAVORITES);
                restoringTabs = false;
                enterFavoritesMode();
                return;

            case 2:
                restoringTabs = true;
                selectTabSafe(modeTabs, MODE_TAB_FILTER);
                restoringTabs = false;
                enterFilterMode(true, true);
                return;

            case 3:
                enterBrowseModeAndSelectTab(TAB_NOW_PLAYING, false);
                return;

            case 4:
                enterBrowseModeAndSelectTab(TAB_POPULAR, false);
                return;
        }
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

                String query = (s == null) ? "" : s.toString().trim();

                if (clear != null) {
                    clear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                }

                if (suppressSuggestionFetch) return;

                if (skipSuggestionFetchBecauseRotation && !query.isEmpty()) {
                    skipSuggestionFetchBecauseRotation = false;
                    hideSuggestions();
                    return;
                }
                skipSuggestionFetchBecauseRotation = false;

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

        suggestionsAdapter = new SearchSuggestionsAdapter(movie -> {

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

            String label = year.isEmpty() ? title : (title + " " + year);

            suppressSuggestionFetch = true;
            input.setText(label);
            input.setSelection(input.getText().length());
            suppressSuggestionFetch = false;

            if (!isInSearchMode()) {
                rememberLastPlaceBeforeSearch();
            }

            viewModel.setQuery(label);

            hideSuggestions();

            hideKeyboard(input);
            input.clearFocus();
        });

        suggestionsList.setLayoutManager(new LinearLayoutManager(this));
        suggestionsList.setAdapter(suggestionsAdapter);
    }

    // =====================
    // Setup: Tabs
    // =====================

    private void setupTabSystem() {
        setupMainTabs();
        setupModeTabs();
        applyTabPipes(mainTabs);
        applyTabPipes(modeTabs);

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

                selectedTabIndex = tab.getPosition();

                enterBrowseMode();
                exitSearchUiAndMode();

                applyBrowseTabSelection(selectedTabIndex, false);
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
                applyBrowseTabSelection(tab.getPosition(), true);
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
                } else {
                    enterFilterMode();
                }

                applyMainTabTitles();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

                if (isInSearchMode()) {
                    refreshSearchAndHideSuggestions();
                    return;
                }

                if (tab.getPosition() == MODE_TAB_FILTER) {
                    if (uiMode != UiMode.FILTER) {
                        enterFilterMode();
                        return;
                    }

                    openMovieFilterBottomSheet(!viewModel.isMovieFilterApplied());
                    return;
                }

                enterFavoritesMode();
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
                    // IMPORTANT:
                    // Restore AFTER adapter commit so LayoutManager has children
                    restoreRecyclerStateIfReady(movies);
                    updateEmptyState();
                });

                return;
            }

            if (uiMode == UiMode.FAVORITES) return;

            if (uiMode == UiMode.BROWSE || uiMode == UiMode.FILTER) {

                movieAdapter.submitList(movies, () -> {
                    // IMPORTANT:
                    // Restore AFTER adapter commit so LayoutManager has children
                    restoreRecyclerStateIfReady(movies);

                    if (uiMode == UiMode.FILTER) {
                        if (movies != null && !movies.isEmpty()) {
                            hideFilterEmptyState();
                        } else {
                            // Case 1: no filter applied -> "no filter applied" message
                            // Case 3: filter applied but 0 results -> "no matches" message
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
            binding.swipeRefreshLayout.setRefreshing(Boolean.TRUE.equals(loading));
            updateEmptyState();
        });

        viewModel.isSearchMode().observe(this, inSearch -> applyMainTabTitles());

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

        viewModel.getFilterEmptyStateEvent().observe(this, show -> {
            if (Boolean.TRUE.equals(show)) {
                showFilterEmptyState();
                updateFilterFabVisibility();
            }
        });
    }

    // =====================
    // Core behavior: mode transitions
    // =====================

    private void enterBrowseModeAndSelectTab(int tabIndex, boolean reselected) {
        enterBrowseMode();

        // IMPORTANT:
        // Avoid selecting the same tab again, because TabLayout will treat it as a RESELECT
        // and fire onTabReselected(), which can cause a duplicate reload.
        if (mainTabs != null) {
            int current = mainTabs.getSelectedTabPosition();
            if (current != tabIndex) {
                restoringTabs = true;
                selectTabSafe(mainTabs, tabIndex);
                restoringTabs = false;
            }
        }

        selectedTabIndex = tabIndex;
        lastBrowseTabIndex = tabIndex;

        applyBrowseTabSelection(tabIndex, reselected);
        applyMainTabTitles();
    }

    private void enterBrowseMode() {
        uiMode = UiMode.BROWSE;

        setModeTabsVisualsEnabled(false);
        clearModeTabsSelection();
        setMainTabsVisualsEnabled(true);

        exitFilterModeUi();

        applyMainTabTitles();
        updateFilterFabVisibility();
    }

    private void enterFavoritesMode() {
        exitSearchUiAndMode();
        uiMode = UiMode.FAVORITES;

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

        updateFilterFabVisibility();
    }

    private void enterFilterMode(boolean showBottomSheet, boolean callViewModel) {
        uiMode = UiMode.FILTER;

        setMainTabsVisualsEnabled(false);
        clearMainTabsSelection();
        setModeTabsVisualsEnabled(true);

        if (callViewModel) {
            viewModel.enterMovieFilterMode();
        }

        if (!viewModel.isMovieFilterApplied()) {
            showFilterEmptyState();
        } else {
            hideFilterEmptyState();
        }

        if (showBottomSheet) {
            if (getSupportFragmentManager().findFragmentByTag("MovieFilterBottomSheet") == null) {
                new MovieFilterBottomSheet()
                        .show(getSupportFragmentManager(), "MovieFilterBottomSheet");
            }
        }

        applyMainTabTitles();
        updateFilterFabVisibility();
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

        if (tabIndex == TAB_NOW_PLAYING) {
            viewModel.selectNowPlaying(reselected);
        }
    }

    // =====================
    // Callbacks
    // =====================

    private void openMovieDetails(@NonNull Movie movie) {
        MovieDetailsDialogFragment.newInstance(
                movie.getId(),
                movie.getTitle(),
                movie.getVoteAverage(),
                movie.getOverview(),
                movie.getPosterPath(),
                movie.getBackdropPath(),
                movie.getReleaseYear()
        ).show(getSupportFragmentManager(), "movie_details");
    }

    // =====================
    // Helpers
    // =====================

    // --- Share ---

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

        String text = label + "\n" + url + "\nShared from FilmAtlas";

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);

        startActivity(Intent.createChooser(intent, "Share a Movie"));
    }

    // --- Filter ---

    private void refreshFilterOrShowEmptyState() {
        if (viewModel.isMovieFilterApplied()) {
            MovieFilterOptions opts = viewModel.getActiveMovieFilterOptions().getValue();
            if (opts == null) opts = MovieFilterOptions.defaults();
            viewModel.applyMovieFilter(opts);
        } else {
            showFilterEmptyState();
        }
    }

    private void openMovieFilterBottomSheet(boolean resetToDefaults) {
        if (resetToDefaults) {
            viewModel.clearMovieFilter();
        }

        if (getSupportFragmentManager().findFragmentByTag("MovieFilterBottomSheet") == null) {
            new MovieFilterBottomSheet()
                    .show(getSupportFragmentManager(), "MovieFilterBottomSheet");
        }

        updateFilterFabVisibility();
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

    private void selectTabSafe(@NonNull TabLayout tabs, int index) {
        if (index < 0 || index >= tabs.getTabCount()) return;
        TabLayout.Tab tab = tabs.getTabAt(index);
        if (tab != null) tab.select();
    }

    private void applyTabPipes(@NonNull TabLayout tabs) {
        // TabLayout's first child is the sliding tab indicator (a LinearLayout)
        View child = tabs.getChildAt(0);
        if (!(child instanceof LinearLayout)) return;

        LinearLayout strip = (LinearLayout) child;

        strip.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        strip.setDividerDrawable(
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.tab_divider_vertical)
        );
        strip.setDividerPadding(0); // keep the pipe tight
    }

    // --- Recycler state ---

    private void restoreRecyclerStateIfReady(List<Movie> movies) {
        if (pendingRvState == null || movies == null || movies.isEmpty()) return;

        RecyclerView.LayoutManager lm = binding.recyclerView.getLayoutManager();
        if (lm != null) lm.onRestoreInstanceState(pendingRvState);

        pendingRvState = null;
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

        boolean show =
                (uiMode == UiMode.FILTER)
                        && !isInSearchMode()
                        && viewModel.isMovieFilterApplied();

        binding.fabFilterApplied.setVisibility(show ? View.VISIBLE : View.GONE);
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

    private void showFilterEmptyState() {
        // Default behavior: "no filter applied"
        showFilterEmptyState(false);
    }

    private void showFilterEmptyState(boolean filterApplied) {
        binding.recyclerView.setVisibility(View.GONE);

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
            binding.btnOpenFilter.setVisibility(filterApplied ? View.GONE : View.VISIBLE);
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
            case TAB_NOW_PLAYING:
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