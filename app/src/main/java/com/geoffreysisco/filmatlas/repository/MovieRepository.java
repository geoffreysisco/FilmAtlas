package com.geoffreysisco.filmatlas.repository;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.geoffreysisco.filmatlas.BuildConfig;
import com.geoffreysisco.filmatlas.model.MovieFilterOptions;
import com.geoffreysisco.filmatlas.model.Movie;
import com.geoffreysisco.filmatlas.model.Trailer;
import com.geoffreysisco.filmatlas.network.MoviesResponse;
import com.geoffreysisco.filmatlas.network.TrailerResponse;
import com.geoffreysisco.filmatlas.serviceapi.MovieApiService;
import com.geoffreysisco.filmatlas.serviceapi.RetrofitInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for movie browse and search operations.
 * Manages pagination state and exposes results via LiveData.
 */
public class MovieRepository {

    // Constants
    private static final int MAX_EMPTY_PAGE_SKIPS = 5;
    private static final int DISCOVER_RANDOM_START_PAGE_MAX = 500;
    private static final int DISCOVER_BATCH_TARGET_COUNT = 10;

    // App Context
    private final Application application;

    // Browse State
    private enum BrowseMode {
        DISCOVER_RANDOM,
        MOVIES_FILTERED,
        MOVIES_POPULAR,
        MOVIES_NEW
    }

    private BrowseMode browseMode = BrowseMode.MOVIES_POPULAR;
    private MovieFilterOptions lastMovieFilterOptions = MovieFilterOptions.defaults();

    private final ArrayList<Movie> browseMovies = new ArrayList<>();
    private final MutableLiveData<List<Movie>> browseLiveData = new MutableLiveData<>();
    private final Set<Integer> filteredSeenMovieIds = new HashSet<>();

    private int movieFilteredEmptySkips = 0;
    private int discoverEmptySkips = 0;
    private int discoverDedupeEmptySkips = 0;

    private int browseCurrentPage = 1;
    private int browseTotalPages = Integer.MAX_VALUE;
    private boolean browseLoading = false;
    private boolean isPagingBrowse = false;

    private int discoverStartPage = 1;
    private final List<Movie> discoverBatchBuffer = new ArrayList<>();
    private boolean discoverBatchFillInProgress = false;
    private final Set<Integer> discoverSeenMovieIds = new HashSet<>();
    private final Random random = new Random();


    // Loading Stat
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);

    // Callbacks
    public interface LoadingCallback {
        void onDone();
    }

    public interface TrailerFetchCallback {
        void onTrailerKeyReady(@NonNull String youtubeKey);
        void onNoTrailerFound();
        void onFetchFailed();
    }

    // Constructor
    public MovieRepository(@NonNull Application application) {
        this.application = application;
        browseLiveData.setValue(new ArrayList<>());
    }

    // LiveData Streams
    public LiveData<List<Movie>> getBrowseLiveData() {
        return browseLiveData;
    }

    public LiveData<Boolean> getLoadingLiveData() {
        return loadingLiveData;
    }

    // Browse API
    public void loadFirstPagePopular(@NonNull LoadingCallback cb) {
        setBrowseMode(BrowseMode.MOVIES_POPULAR);
        loadFirstPageBrowseInternal(cb);
    }

    public void loadFirstPageNowPlaying(@NonNull LoadingCallback cb) {
        setBrowseMode(BrowseMode.MOVIES_NEW);
        loadFirstPageBrowseInternal(cb);
    }

    public void loadFirstPageDiscoverRandom(@NonNull LoadingCallback cb) {
        setBrowseMode(BrowseMode.DISCOVER_RANDOM);
        browseLoading = false;
        isPagingBrowse = false;
        discoverStartPage = 1 + random.nextInt(DISCOVER_RANDOM_START_PAGE_MAX);
        browseCurrentPage = discoverStartPage;
        browseMovies.clear();
        // Do not emit an empty list here; it causes a visible "flash" before new results publish.
        discoverBatchBuffer.clear();
        discoverBatchFillInProgress = false;
        discoverSeenMovieIds.clear();
        discoverEmptySkips = 0;
        discoverDedupeEmptySkips = 0;
        movieFilteredEmptySkips = 0;
        browseTotalPages = Integer.MAX_VALUE;
        loadNextPageBrowse(cb);
    }

    public void loadFirstPageMovieFiltered(
            @NonNull MovieFilterOptions filter,
            @NonNull LoadingCallback cb
    ) {
        setBrowseMode(BrowseMode.MOVIES_FILTERED);
        lastMovieFilterOptions = filter.copy();
        movieFilteredEmptySkips = 0;
        browseMovies.clear();
        browseLiveData.setValue(new ArrayList<>());
        browseCurrentPage = 1;
        browseTotalPages = Integer.MAX_VALUE;
        filteredSeenMovieIds.clear();
        loadNextPageBrowse(cb);
    }

    public void loadNextPageBrowse(@NonNull LoadingCallback cb) {
        if (isPagingBrowse) return;

        if (browseMode == BrowseMode.DISCOVER_RANDOM && discoverBatchFillInProgress) {
            return;
        }

        isPagingBrowse = true;

        if (browseLoading || browseCurrentPage > browseTotalPages) {
            isPagingBrowse = false;
            cb.onDone();
            return;
        }

        browseLoading = true;
        setLoading(true);

        MovieApiService api = RetrofitInstance.getService();
        Call<MoviesResponse> call = buildBrowseCall(api, browseCurrentPage);

        if (call == null) {
            browseLoading = false;
            setLoading(false);
            isPagingBrowse = false;
            cb.onDone();
            return;
        }

        call.enqueue(new Callback<MoviesResponse>() {
            @Override
            public void onResponse(Call<MoviesResponse> call, Response<MoviesResponse> response) {
                browseLoading = false;
                setLoading(false);
                isPagingBrowse = false;

                // For DISCOVER_RANDOM, we may chain additional fetches to fill a batch.
                // Only signal "done" after we actually publish results or stop retrying.
                boolean shouldDeferDoneCallback = (browseMode == BrowseMode.DISCOVER_RANDOM);
                if (!shouldDeferDoneCallback) {
                    cb.onDone();
                }

                if (!response.isSuccessful() || response.body() == null) return;

                MoviesResponse body = response.body();
                if (body.getTotalPages() != null) {
                    browseTotalPages = body.getTotalPages();
                }

                if (browseMode == BrowseMode.DISCOVER_RANDOM
                        && browseTotalPages > 0
                        && browseCurrentPage > browseTotalPages) {

                    int maxPage = Math.min(browseTotalPages, DISCOVER_RANDOM_START_PAGE_MAX);
                    browseCurrentPage = 1 + random.nextInt(maxPage);
                    loadNextPageBrowse(cb);
                    return;
                }

                List<Movie> filtered = filterOutMissingPosters(body.getMovies());

                if (browseMode == BrowseMode.MOVIES_FILTERED) {
                    List<Movie> deduped = new ArrayList<>();

                    for (Movie m : filtered) {
                        if (m == null) continue;

                        Integer id = m.getId();
                        if (id == null) continue;

                        if (filteredSeenMovieIds.add(id)) {
                            deduped.add(m);
                        }
                    }

                    filtered = deduped;
                }

                if (browseMode == BrowseMode.DISCOVER_RANDOM) {
                    List<Movie> deduped = new ArrayList<>();

                    for (Movie m : filtered) {
                        if (m == null) continue;

                        Integer id = m.getId();
                        if (id == null) continue;

                        if (discoverSeenMovieIds.add(id)) {
                            deduped.add(m);
                        }
                    }

                    filtered = deduped;
                }

                if (browseMode == BrowseMode.DISCOVER_RANDOM) {
                    Collections.shuffle(filtered);
                }

                boolean isFilteredMode = (browseMode == BrowseMode.MOVIES_FILTERED);

                if (browseMode == BrowseMode.DISCOVER_RANDOM && filtered.isEmpty()
                        && browseCurrentPage < browseTotalPages
                        && discoverEmptySkips < MAX_EMPTY_PAGE_SKIPS) {

                    discoverDedupeEmptySkips++;

                    int maxPage = Math.min(browseTotalPages, DISCOVER_RANDOM_START_PAGE_MAX);

                    // Jump to a different random page before retrying, otherwise we can loop on the same empty page.
                    int nextPage = browseCurrentPage;
                    for (int i = 0; i < 3; i++) {
                        int candidate = 1 + random.nextInt(maxPage);
                        if (candidate != browseCurrentPage) {
                            nextPage = candidate;
                            break;
                        }
                    }
                    browseCurrentPage = nextPage;

                    loadNextPageBrowse(cb);
                    return;
                }

                if (browseMode == BrowseMode.DISCOVER_RANDOM && filtered.isEmpty()) {

                    // If we buffered anything during this paging cycle, publish it
                    // so RecyclerView never hits a "fake end" bounce.
                    if (!discoverBatchBuffer.isEmpty()) {
                        browseMovies.addAll(discoverBatchBuffer);
                        browseLiveData.setValue(new ArrayList<>(browseMovies));
                    }

                    discoverBatchFillInProgress = false;
                    discoverBatchBuffer.clear();
                    discoverEmptySkips = 0;
                    discoverDedupeEmptySkips = 0;

                    cb.onDone();
                    return;
                }

                boolean shouldSkipEmptyPage =
                        filtered.isEmpty()
                                && browseCurrentPage < browseTotalPages
                                && (
                                (isFilteredMode && movieFilteredEmptySkips < MAX_EMPTY_PAGE_SKIPS)
                                        || (!isFilteredMode && discoverEmptySkips < MAX_EMPTY_PAGE_SKIPS)
                        );

                if (shouldSkipEmptyPage) {
                    if (isFilteredMode) {
                        movieFilteredEmptySkips++;
                        browseCurrentPage++;
                        loadNextPageBrowse(cb);
                        return;
                    }

                    // DISCOVER_RANDOM (and other non-filtered browse modes) should not "++" the page here.
                    // Discover already selects next pages randomly in its retry / next-page logic.
                    discoverEmptySkips++;

                    int maxPage = Math.min(browseTotalPages, DISCOVER_RANDOM_START_PAGE_MAX);

                    int nextPage = browseCurrentPage;
                    for (int i = 0; i < 3; i++) {
                        int candidate = 1 + random.nextInt(maxPage)
                                ;
                        if (candidate != browseCurrentPage) {
                            nextPage = candidate;
                            break;
                        }
                    }
                    browseCurrentPage = nextPage;

                    loadNextPageBrowse(cb);
                    return;
                }

                movieFilteredEmptySkips = 0;
                discoverEmptySkips = 0;
                discoverDedupeEmptySkips = 0;

                if (browseMode == BrowseMode.DISCOVER_RANDOM) {

                    // Start/continue filling a mixed batch (pulled from multiple random pages).
                    if (!discoverBatchFillInProgress) {
                        discoverBatchFillInProgress = true;
                        discoverBatchBuffer.clear();
                    }

                    int remaining = DISCOVER_BATCH_TARGET_COUNT - discoverBatchBuffer.size();
                    int take = Math.min(remaining, filtered.size());

                    for (int i = 0; i < take; i++) {
                        discoverBatchBuffer.add(filtered.get(i));
                    }

                    if (discoverBatchBuffer.size() >= DISCOVER_BATCH_TARGET_COUNT) {
                        browseMovies.addAll(discoverBatchBuffer);
                        browseLiveData.setValue(new ArrayList<>(browseMovies));
                        discoverBatchFillInProgress = false;
                        discoverBatchBuffer.clear();
                        cb.onDone();
                    } else {
                        // Not enough yet — immediately fetch another random page to mix into the same batch.
                        int maxPage = Math.min(browseTotalPages, DISCOVER_RANDOM_START_PAGE_MAX);

                        int nextPage = browseCurrentPage;
                        for (int i = 0; i < 3; i++) {
                            int candidate = 1 + random.nextInt(maxPage);
                            if (candidate != browseCurrentPage) {
                                nextPage = candidate;
                                break;
                            }
                        }
                        browseCurrentPage = nextPage;

                        loadNextPageBrowse(cb);
                        return;
                    }

                } else {
                    browseMovies.addAll(filtered);
                    browseLiveData.setValue(new ArrayList<>(browseMovies));
                }

                if (browseMode == BrowseMode.DISCOVER_RANDOM && browseTotalPages > 1) {

                    int maxPage = Math.min(browseTotalPages, DISCOVER_RANDOM_START_PAGE_MAX);

                    // Pick a new random page for the next fetch (avoid re-picking the same page if possible).
                    int nextPage = browseCurrentPage;
                    for (int i = 0; i < 3; i++) {
                        int candidate = 1 + random.nextInt(maxPage);
                        if (candidate != browseCurrentPage) {
                            nextPage = candidate;
                            break;
                        }
                    }

                    browseCurrentPage = nextPage;

                } else {
                    browseCurrentPage++;
                }
            }

            @Override
            public void onFailure(Call<MoviesResponse> call, Throwable t) {
                browseLoading = false;
                setLoading(false);
                isPagingBrowse = false;

                // If Discover already has results, don't escalate a rare paging failure into a full error state.
                if (browseMode == BrowseMode.DISCOVER_RANDOM && !browseMovies.isEmpty()) {
                    discoverBatchFillInProgress = false;
                    discoverBatchBuffer.clear();
                    discoverEmptySkips = 0;
                    cb.onDone();
                    return;
                }

                cb.onDone();
            }
        });
    }

    public void fetchBestTrailerKey(int movieId, @NonNull TrailerFetchCallback cb) {
        if (movieId <= 0) {
            cb.onFetchFailed();
            return;
        }

        MovieApiService api = RetrofitInstance.getService();
        String apiKey = BuildConfig.TMDB_API_KEY;

        api.getMovieVideos(movieId, apiKey).enqueue(new Callback<TrailerResponse>() {
            @Override
            public void onResponse(
                    @NonNull Call<TrailerResponse> call,
                    @NonNull Response<TrailerResponse> response
            ) {
                TrailerResponse body = response.body();
                if (!response.isSuccessful() || body == null || body.getResults() == null) {
                    cb.onNoTrailerFound();
                    return;
                }

                String key = pickBestYouTubeKey(body);
                if (key == null) {
                    cb.onNoTrailerFound();
                    return;
                }

                cb.onTrailerKeyReady(key);
            }

            @Override
            public void onFailure(
                    @NonNull Call<TrailerResponse> call,
                    @NonNull Throwable t
            ) {
                cb.onFetchFailed();
            }
        });
    }

    private void loadFirstPageBrowseInternal(@NonNull LoadingCallback cb) {
        browseMovies.clear();
        discoverEmptySkips = 0;
        browseLiveData.setValue(new ArrayList<>());
        browseCurrentPage = 1;
        browseTotalPages = Integer.MAX_VALUE;
        loadNextPageBrowse(cb);
    }

    private Call<MoviesResponse> buildBrowseCall(@NonNull MovieApiService api, int page) {
        String apiKey = BuildConfig.TMDB_API_KEY;

        if (browseMode == BrowseMode.MOVIES_POPULAR) {
            return api.discoverMoviesByRating(
                    apiKey, "vote_average.desc", 500,
                    "1990-01-01", getTodayDate(), "99",
                    page, null, null, null, null
            );
        }

        if (browseMode == BrowseMode.MOVIES_NEW) {
            return api.discoverMoviesByRating(
                    apiKey, "primary_release_date.desc", 20,
                    getDateDaysAgo(365), getTodayDate(), "0",
                    page, "US", "US", "3|4", "en"
            );
        }

        if (browseMode == BrowseMode.MOVIES_FILTERED) {
            MovieFilterOptions f = lastMovieFilterOptions;
            return api.discoverMoviesFiltered(
                    apiKey,
                    f.getSortApiValue(),
                    200,
                    (f.getMinRating() <= 0f) ? null : f.getMinRating(),
                    f.getYear(),
                    f.getWithGenresParam(),
                    page
            );
        }

        return api.discoverMoviesByRating(
                apiKey, "popularity.desc", 300,
                "1980-01-01", getTodayDate(), "99",
                page, null, null, null, null
        );
    }

    private void setBrowseMode(@NonNull BrowseMode mode) {

        boolean leavingDiscover = (browseMode == BrowseMode.DISCOVER_RANDOM && mode != BrowseMode.DISCOVER_RANDOM);
        if (leavingDiscover) {
            discoverBatchFillInProgress = false;
            discoverBatchBuffer.clear();
            discoverSeenMovieIds.clear();
            discoverEmptySkips = 0;
        }

        browseMode = mode;
        browseLoading = false;
        browseCurrentPage = 1;
        browseTotalPages = Integer.MAX_VALUE;
    }

    // =====================
    // Helpers
    // =====================

    private void setLoading(boolean loading) {
        loadingLiveData.postValue(loading);
    }

    private String getTodayDate() {
        return java.time.LocalDate.now().toString();
    }

    private String getDateDaysAgo(int days) {
        return java.time.LocalDate.now().minusDays(days).toString();
    }

    private List<Movie> filterOutMissingPosters(List<Movie> in) {
        ArrayList<Movie> out = new ArrayList<>();
        if (in == null) return out;

        for (Movie m : in) {
            if (m != null && m.getPosterPath() != null && !m.getPosterPath().trim().isEmpty()) {
                out.add(m);
            }
        }
        return out;
    }

    @Nullable
    private String pickBestYouTubeKey(@NonNull TrailerResponse body) {
        for (Trailer t : body.getResults()) {
            if ("YouTube".equalsIgnoreCase(t.getSite())
                    && "Trailer".equalsIgnoreCase(t.getType())) {
                return t.getKey();
            }
        }

        for (Trailer t : body.getResults()) {
            if ("YouTube".equalsIgnoreCase(t.getSite())) {
                return t.getKey();
            }
        }

        return null;
    }

    public void clearBrowseResults() {
        browseMovies.clear();
        browseLiveData.setValue(new ArrayList<>());
        filteredSeenMovieIds.clear();
    }
}