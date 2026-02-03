package com.example.filmatlas.binding;

import android.widget.ImageView;

import androidx.databinding.BindingAdapter;

import com.bumptech.glide.Glide;

/**
 * Data Binding adapters for loading TMDB images into ImageViews.
 */
public final class ImageBindingAdapters {

    private ImageBindingAdapters() {
    }

    @BindingAdapter({"posterPath"})
    public static void loadPoster(ImageView imageView, String imageURL) {

        if (imageURL == null || imageURL.trim().isEmpty() || "null".equalsIgnoreCase(imageURL.trim())) {
            imageView.setImageDrawable(null);
            return;
        }

        String imagePath = "https://image.tmdb.org/t/p/w500" + imageURL;

        Glide.with(imageView.getContext())
                .load(imagePath)
                .into(imageView);
    }

    @BindingAdapter({"backdropPath"})
    public static void loadBackdrop(ImageView imageView, String imageURL) {

        if (imageURL == null || imageURL.trim().isEmpty()) {
            imageView.setImageDrawable(null);
            return;
        }

        String imagePath = "https://image.tmdb.org/t/p/w780" + imageURL;

        Glide.with(imageView.getContext())
                .load(imagePath)
                .into(imageView);
    }
}