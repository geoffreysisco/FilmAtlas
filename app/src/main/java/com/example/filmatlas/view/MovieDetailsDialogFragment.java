package com.example.filmatlas.view;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.filmatlas.BuildConfig;
import com.example.filmatlas.R;
import com.example.filmatlas.model.MovieActionPayload;
import com.example.filmatlas.model.Trailer;
import com.example.filmatlas.network.TrailerResponse;
import com.example.filmatlas.serviceapi.MovieApiService;
import com.example.filmatlas.serviceapi.RetrofitInstance;
import com.example.filmatlas.viewmodel.MainActivityViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import java.util.Locale;

import jp.wasabeef.glide.transformations.BlurTransformation;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * DialogFragment for movie details with artwork, overview, and TMDB/trailer actions.
 * Centers responsively and supports swipe-up-to-dismiss in portrait.
 */
public class MovieDetailsDialogFragment extends DialogFragment {

    private static final String ARG_PAYLOAD = "arg_payload";

    private static final float WIDTH_RATIO_LANDSCAPE = 0.92f;
    private static final float WIDTH_RATIO_PORTRAIT = 0.94f;

    private static final int POSTER_BLUR_RADIUS = 25;
    private static final int POSTER_BLUR_SAMPLING = 3;
    private static final int CLOSE_SAMPLE_SIZE_PX = 22;
    private static final int CLOSE_SAMPLE_INSET_PX = 14;

    public interface OnFavoriteClickListener {
        void onFavoriteClick(@NonNull MovieActionPayload payload);
    }

    public interface OnShareClickListener {
        void onShareClick(@NonNull MovieActionPayload payload);
    }

    @Nullable
    private OnFavoriteClickListener favoriteClickListener;

    @Nullable
    private OnShareClickListener shareClickListener;

    public static MovieDetailsDialogFragment newInstance(@NonNull MovieActionPayload payload) {
        MovieDetailsDialogFragment frag = new MovieDetailsDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(ARG_PAYLOAD, payload);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (getParentFragment() instanceof OnFavoriteClickListener) {
            favoriteClickListener = (OnFavoriteClickListener) getParentFragment();
        } else if (context instanceof OnFavoriteClickListener) {
            favoriteClickListener = (OnFavoriteClickListener) context;
        }

        if (getParentFragment() instanceof OnShareClickListener) {
            shareClickListener = (OnShareClickListener) getParentFragment();
        } else if (context instanceof OnShareClickListener) {
            shareClickListener = (OnShareClickListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        favoriteClickListener = null;
        shareClickListener = null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = getLayoutInflater().inflate(R.layout.dialog_movie_details, null, false);

        View dialogCard = view.findViewById(R.id.dialog_card);
        SwipeDismissLayout swipeRoot = view.findViewById(R.id.swipe_root);
        NestedScrollView overviewScroll = view.findViewById(R.id.content_scroll);

        TextView titleTv = view.findViewById(R.id.dialog_title);
        TextView overviewTv = view.findViewById(R.id.dialog_overview);

        ImageView poster = view.findViewById(R.id.dialog_poster);
        ImageView posterBg = view.findViewById(R.id.dialog_poster_bg);
        ImageView backdrop = view.findViewById(R.id.dialog_backdrop);

        MaterialButton closeBtn = view.findViewById(R.id.btn_close);
        MaterialButton shareBtn = view.findViewById(R.id.btn_share);
        MaterialButton favoriteBtn = view.findViewById(R.id.btn_favorite);
        MaterialButton ratingBtn = view.findViewById(R.id.btn_rating);
        MaterialButton trailerBtn = view.findViewById(R.id.btn_run_trailer);
        MaterialButton whereToWatchBtn = view.findViewById(R.id.btn_where_to_watch);

        swipeRoot.setup(dialogCard, overviewScroll, this::dismissAllowingStateLoss);

        MovieActionPayload payload = readPayload();

        titleTv.setText(formatTitleWithYear(payload.getTitle(), payload.getReleaseYear()));
        ratingBtn.setText(String.format(Locale.US, "%.2f", payload.getRating()));
        overviewTv.setText(payload.getOverview());

        closeBtn.setOnClickListener(v -> dismissAllowingStateLoss());
        ratingBtn.setOnClickListener(v -> openTmdbMoviePage(payload.getMovieId()));
        trailerBtn.setOnClickListener(v -> fetchAndOpenTrailer(payload.getMovieId(), trailerBtn));
        whereToWatchBtn.setOnClickListener(v -> openTmdbWatchPage(payload.getMovieId()));

        if (shareBtn != null) {
            shareBtn.setOnClickListener(v -> {
                if (shareClickListener != null) {
                    shareClickListener.onShareClick(payload);
                }
            });
        }

        if (favoriteBtn != null) {
            favoriteBtn.setOnClickListener(v -> {
                if (favoriteClickListener != null) {
                    favoriteClickListener.onFavoriteClick(payload);
                }
            });
        }

        String posterUrl = buildImageUrl("w500", payload.getPosterPath());
        String backdropUrl = buildImageUrl("w780", payload.getBackdropPath());

        loadPosterImages(poster, posterBg, posterUrl);
        loadBackdropImage(backdrop, backdropUrl);

        applyOverviewInsets(overviewScroll);

        if (isLandscape()) {
            int tint = MaterialColors.getColor(
                    closeBtn,
                    com.google.android.material.R.attr.colorOnSurfaceVariant
            );

            int blended = ColorUtils.blendARGB(tint, Color.WHITE, 0.72f);
            ColorStateList tintList = ColorStateList.valueOf(blended);

            closeBtn.setIconTint(tintList);

            if (shareBtn != null) shareBtn.setIconTint(tintList);
            if (favoriteBtn != null) favoriteBtn.setIconTint(tintList);
        } else {
            applyAdaptiveActionTintFromBlurRegion(
                    posterUrl,
                    closeBtn,
                    shareBtn,
                    favoriteBtn
            );
        }

        MainActivityViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(MainActivityViewModel.class);

        if (favoriteBtn != null && payload.getMovieId() > 0) {
            viewModel.isFavoriteLive(payload.getMovieId()).observe(this, isFav -> {
                favoriteBtn.setSelected(Boolean.TRUE.equals(isFav));
            });
        }

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog d = getDialog();
        if (d == null) return;

        Window w = d.getWindow();
        if (w == null) return;

        int screenW = getResources().getDisplayMetrics().widthPixels;

        float ratio = isLandscape() ? WIDTH_RATIO_LANDSCAPE : WIDTH_RATIO_PORTRAIT;
        int width = (int) (screenW * ratio);

        w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        w.setGravity(Gravity.CENTER);
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    }

    private void loadPosterImages(
            @NonNull ImageView poster,
            @Nullable ImageView posterBg,
            @Nullable String posterUrl
    ) {
        Glide.with(requireContext())
                .load(posterUrl)
                .fitCenter()
                .into(poster);

        if (posterBg != null) {
            Glide.with(requireContext())
                    .load(posterUrl)
                    .transform(new BlurTransformation(POSTER_BLUR_RADIUS, POSTER_BLUR_SAMPLING))
                    .centerCrop()
                    .into(posterBg);
        }
    }

    private void loadBackdropImage(@Nullable ImageView backdrop, @Nullable String backdropUrl) {
        if (backdrop == null) return;

        Glide.with(requireContext())
                .load(backdropUrl)
                .centerCrop()
                .into(backdrop);
    }

    private void applyAdaptiveActionTintFromBlurRegion(
            @Nullable String posterUrl,
            @NonNull MaterialButton closeBtn,
            @Nullable MaterialButton shareBtn,
            @Nullable MaterialButton favoriteBtn
    ) {
        if (posterUrl == null) return;

        Glide.with(requireContext())
                .asBitmap()
                .load(posterUrl)
                .transform(new BlurTransformation(POSTER_BLUR_RADIUS, POSTER_BLUR_SAMPLING))
                .centerCrop()
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(
                            @NonNull Bitmap bitmap,
                            @Nullable Transition<? super Bitmap> transition
                    ) {
                        int sampleColor = sampleTopRightColor(bitmap, CLOSE_SAMPLE_SIZE_PX, CLOSE_SAMPLE_INSET_PX);

                        int white = 0xEFFFFFFF;
                        int black = 0xE6000000;

                        double cw = ColorUtils.calculateContrast(white, sampleColor);
                        double cb = ColorUtils.calculateContrast(black, sampleColor);

                        int tint = (cw >= cb) ? white : black;
                        ColorStateList tintList = ColorStateList.valueOf(tint);

                        closeBtn.setIconTint(tintList);

                        if (shareBtn != null) shareBtn.setIconTint(tintList);
                        if (favoriteBtn != null) favoriteBtn.setIconTint(tintList);
                    }

                    @Override
                    public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                        // no-op
                    }
                });
    }

    private int sampleTopRightColor(@NonNull Bitmap bmp, int sizePx, int insetPx) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();

        int x0 = clamp(w - insetPx - sizePx, 0, w - 1);
        int y0 = clamp(insetPx, 0, h - 1);

        int x1 = clamp(x0 + sizePx, 0, w);
        int y1 = clamp(y0 + sizePx, 0, h);

        long sumR = 0;
        long sumG = 0;
        long sumB = 0;
        long count = 0;

        for (int y = y0; y < y1; y += 2) {
            for (int x = x0; x < x1; x += 2) {
                int c = bmp.getPixel(x, y);
                sumR += Color.red(c);
                sumG += Color.green(c);
                sumB += Color.blue(c);
                count++;
            }
        }

        if (count == 0) return 0xFF777777;

        int r = (int) (sumR / count);
        int g = (int) (sumG / count);
        int b = (int) (sumB / count);

        return Color.rgb(r, g, b);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private void fetchAndOpenTrailer(int movieId, @NonNull MaterialButton trailerBtn) {
        if (movieId <= 0) {
            Toast.makeText(requireContext(), R.string.error_missing_movie_id, Toast.LENGTH_SHORT).show();
            return;
        }

        setTrailerLoading(trailerBtn, true);

        MovieApiService api = RetrofitInstance.getService();
        String apiKey = BuildConfig.TMDB_API_KEY;

        api.getMovieVideos(movieId, apiKey).enqueue(new Callback<TrailerResponse>() {
            @Override
            public void onResponse(
                    @NonNull Call<TrailerResponse> call,
                    @NonNull Response<TrailerResponse> response
            ) {
                setTrailerLoading(trailerBtn, false);

                TrailerResponse body = response.body();
                if (!response.isSuccessful() || body == null || body.getResults() == null) {
                    Toast.makeText(requireContext(), R.string.error_trailer_unavailable, Toast.LENGTH_SHORT).show();
                    return;
                }

                String key = pickBestYouTubeKey(body);
                if (key == null) {
                    Toast.makeText(requireContext(), R.string.error_trailer_unavailable, Toast.LENGTH_SHORT).show();
                    return;
                }

                openTrailer(key);
            }

            @Override
            public void onFailure(@NonNull Call<TrailerResponse> call, @NonNull Throwable t) {
                setTrailerLoading(trailerBtn, false);
                Toast.makeText(requireContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setTrailerLoading(@NonNull MaterialButton trailerBtn, boolean loading) {
        trailerBtn.setEnabled(!loading);
        trailerBtn.setAlpha(loading ? 0.6f : 1f);
    }

    private void applyOverviewInsets(@NonNull NestedScrollView overviewScroll) {
        ViewCompat.setOnApplyWindowInsetsListener(overviewScroll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int extra = dpToPx(v, 6);

            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    10 + bars.bottom + extra
            );
            return insets;
        });

        ViewCompat.requestApplyInsets(overviewScroll);
    }

    private void openTmdbWatchPage(int movieId) {
        if (movieId <= 0) {
            Toast.makeText(requireContext(), R.string.error_missing_movie_id, Toast.LENGTH_SHORT).show();
            return;
        }

        String localeParam = Locale.getDefault().getCountry();
        if (localeParam == null || localeParam.isEmpty()) localeParam = "US";
        localeParam = localeParam.toLowerCase(Locale.US);

        String url = "https://www.themoviedb.org/movie/" + movieId + "/watch?locale=" + localeParam;

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.error_open_watch_page, Toast.LENGTH_SHORT).show();
        }
    }

    private void openTmdbMoviePage(int movieId) {
        if (movieId <= 0) {
            Toast.makeText(requireContext(), R.string.error_missing_movie_id, Toast.LENGTH_SHORT).show();
            return;
        }

        String locale = Locale.getDefault().getCountry().toLowerCase(Locale.US);
        if (locale.isEmpty()) locale = "us";

        Uri uri = Uri.parse("https://www.themoviedb.org/movie/" + movieId)
                .buildUpon()
                .appendQueryParameter("locale", locale)
                .build();

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.error_open_movie_page, Toast.LENGTH_SHORT).show();
        }
    }

    private void openTrailer(@NonNull String youtubeKey) {
        Context context = requireContext();

        Intent appIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + youtubeKey));
        Intent webIntent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/watch?v=" + youtubeKey)
        );

        try {
            context.startActivity(appIntent);
        } catch (ActivityNotFoundException e) {
            context.startActivity(webIntent);
        }
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

    private int dpToPx(@NonNull View v, int dp) {
        return (int) (dp * v.getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @NonNull
    private String formatTitleWithYear(@NonNull String title, @Nullable String yearRaw) {
        if (yearRaw == null) return title;

        String year = yearRaw.trim();
        if (year.isEmpty()) return title;

        return title + " (" + year + ")";
    }

    @Nullable
    private String buildImageUrl(@NonNull String size, @Nullable String path) {
        if (path == null || path.isEmpty()) return null;
        return "https://image.tmdb.org/t/p/" + size + path;
    }

    @NonNull
    private MovieActionPayload readPayload() {
        Bundle b = getArguments();
        MovieActionPayload p = (b != null) ? b.getParcelable(ARG_PAYLOAD) : null;

        if (p == null) {
            // Safe fallback so we never crash
            return new MovieActionPayload(-1, "", 0.0, "", null, null, null);
        }

        return p;
    }
}