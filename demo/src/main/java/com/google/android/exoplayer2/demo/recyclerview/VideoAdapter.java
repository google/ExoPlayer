package com.google.android.exoplayer2.demo.recyclerview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.facebook.rebound.SimpleSpringListener;
import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringSystem;
import com.facebook.rebound.SpringUtil;
import com.google.android.exoplayer2.demo.R;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.util.Util;

import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.google.android.exoplayer2.demo.recyclerview.PercentVisibilityOnScrollListener.isCompletelyShowing;

/* PACKAGE */ class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private final List<String> urlSources;
    private final VideoPlayer videoPlayer;

    /* PACKAGE */ VideoAdapter(Context context, List<String> urlSources) {
        this.urlSources = urlSources;
        videoPlayer = new VideoPlayer(context);
    }

    /* PACKAGE */ void onPause() {
        if (Util.SDK_INT <= Build.VERSION_CODES.M) {
            videoPlayer.releasePlayer();
        }
    }

    /* PACKAGE */ void onStop() {
        // noop if player is released
        videoPlayer.releasePlayer();
    }

    @Override
    public void onViewRecycled(VideoViewHolder holder) {
        holder.onViewRecycled();
    }

    @Override
    public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.layout_video_view,
                        parent,
                        false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(VideoViewHolder holder, int position) {
        holder.onBind(urlSources.get(position), position);
    }

    @Override
    public int getItemCount() {
        return urlSources.size();
    }

    /* PACKAGE */ void pauseVideo() {
        videoPlayer.pause();
    }

    /* PACKAGE */ class VideoViewHolder extends RecyclerView.ViewHolder {

        @Bind(R.id.header_title)
        TextView headerTitle;
        @Bind(R.id.player_view)
        SimpleExoPlayerView videoView;
        @Bind(R.id.cover_photo_default)
        ImageView coverPhoto;
        @Bind(R.id.video_loading_progress_bar)
        ProgressBar progressBar;
        @Bind(R.id.error_view)
        ImageView errorView;

        private final Spring spring;
        private final CoverPhotoSpringListener coverPhotoSpringListener;
        private final Rect rect;
        private boolean shouldRestorePosition;
        private long playerPosition;
        private String urlSource;

        /* PACKAGE */ VideoViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            rect = new Rect();
            // to prevent yanking when laying out view
            setVideoViewLayoutListener();
            // setup Springs for hiding cover photo
            final SpringSystem springSystem = SpringSystem.create();
            spring = springSystem.createSpring();
            spring.setSpringConfig(SpringConfig.fromOrigamiTensionAndFriction(200, 50));
            coverPhotoSpringListener = new CoverPhotoSpringListener();
        }

        /* PACKAGE */
        @SuppressLint("SetTextI18n")
        void onBind(String urlSource, int position) {
            headerTitle.setText("Position " + position);
            this.urlSource = urlSource;
            this.spring.removeAllListeners();
            this.spring.addListener(coverPhotoSpringListener);
            // clear play position
            clearPlayingPosition();
            // reset playing state UI
            setupViewState(false, false);
        }

        /* PACKAGE */ void onViewRecycled() {
            videoPlayer.onViewRecycled(this);
            spring.removeAllListeners();
            clearPlayingPosition();
        }

        /* PACKAGE */ void clearPlayingPosition() {
            this.shouldRestorePosition = false;
            this.playerPosition = 0L;
        }

        /* PACKAGE */ void playVideo() {
            videoPlayer.play(this);
        }

        /* PACKAGE */ void setupViewState(boolean withSpinner, boolean isReadyToPlay) {
            errorView.setVisibility(GONE);
            if (!isReadyToPlay) {
                if (withSpinner) {
                    progressBar.setVisibility(VISIBLE);
                } else {
                    progressBar.setVisibility(GONE);
                }
                coverPhoto.setAlpha(1.0f);
                coverPhoto.setVisibility(VISIBLE);
            } else {
                progressBar.setVisibility(GONE);
                spring.setCurrentValue(0.0);
                spring.setEndValue(1.0);
            }
        }

        /* PACKAGE */ void setOnError() {
            errorView.setVisibility(VISIBLE);
        }

        // PercentVisibilityOnScrollListener will use this view to determine
        // how much video is on screen. Not using .itemView as that includes
        // the ViewHolder's header.
        /* PACKAGE */ SimpleExoPlayerView getVideoView() {
            return videoView;
        }

        @NonNull
        /* PACKAGE */ String getStreamingUrl() {
            return this.urlSource;
        }

        private void setVideoViewLayoutListener() {
            videoView.getViewTreeObserver()
                    .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

                        @Override
                        public void onGlobalLayout() {
                            ViewTreeObserver obs = videoView.getViewTreeObserver();
                            obs.removeOnGlobalLayoutListener(this);
                            coverPhoto.getLayoutParams().height = videoView.getHeight();
                            if (isCompletelyShowing(itemView, rect)) {
                                playVideo();
                            }
                        }
                    });
        }

        /* PACKAGE */ boolean shouldRestorePosition() {
            return shouldRestorePosition;
        }

        /* PACKAGE */ long getPlayerPosition() {
            return playerPosition;
        }

        /* PACKAGE */ void setPlayerPosition(long playerPosition) {
            this.shouldRestorePosition = true;
            this.playerPosition = playerPosition;
        }

        // ----- Spring ----------------------------------------------------------------------------

        private class CoverPhotoSpringListener extends SimpleSpringListener {

            @Override
            public void onSpringUpdate(Spring spring) {
                float alpha = (float) SpringUtil.mapValueFromRangeToRange(spring.getCurrentValue(),
                        0,
                        1,
                        1.0,
                        0.0);
                if (coverPhoto.getVisibility() == VISIBLE) {
                    coverPhoto.setAlpha(alpha);
                }
            }

            @Override
            public void onSpringAtRest(Spring spring) {
                coverPhoto.setVisibility(GONE);
            }
        }
    }
}
