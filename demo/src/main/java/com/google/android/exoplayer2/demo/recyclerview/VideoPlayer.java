package com.google.android.exoplayer2.demo.recyclerview;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.demo.R;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelections;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import static com.google.android.exoplayer2.ExoPlayer.STATE_READY;

/* PACKAGE */ class VideoPlayer {

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();

    private final Context context;
    private final Handler mainHandler;
    private final DataSource.Factory mediaDataSourceFactory;
    private MappingTrackSelector trackSelector;
    private SimpleExoPlayer player;
    private int playerWindow;
    private VideoAdapter.VideoViewHolder currentlyPlayingView;
    private LoopingMediaSource loopingSource;

    /* PACKAGE */ VideoPlayer(Context context) {
        this.context = context;
        this.mediaDataSourceFactory = new DefaultDataSourceFactory(context,
                Util.getUserAgent(context,
                        context.getString(
                                R.string.application_name)),
                BANDWIDTH_METER);
        this.mainHandler = new Handler();
    }

    public void play(@NonNull VideoAdapter.VideoViewHolder videoView) {

        if (currentlyPlayingView == videoView && player != null) {
            // just make sure we are playing
            player.setPlayWhenReady(true);
            return;
        }
        // I found that playing on scrolling did not work well without releasing
        // and initing a new player for each ViewHolder. My guess is that this is
        // to the async nature of the Handler the player uses internally for
        // initing and loading. R.Pina 20161018
        releasePlayer(); // nulls out player
        initializePlayer(); // creates player

        if (loopingSource != null) {
            loopingSource.releaseSource();
        }
        currentlyPlayingView = videoView;
        currentlyPlayingView.getVideoView()
                .setPlayer(player);
        if (currentlyPlayingView.shouldRestorePosition()) {
            if (currentlyPlayingView.getPlayerPosition() == C.TIME_UNSET) {
                player.seekToDefaultPosition(playerWindow);
            } else {
                player.seekTo(playerWindow, currentlyPlayingView.getPlayerPosition());
            }
        }
        MediaSource mediaSource = new HlsMediaSource(Uri.parse(currentlyPlayingView.getStreamingUrl()),
                mediaDataSourceFactory,
                mainHandler,
                null);
        loopingSource = new LoopingMediaSource(mediaSource);
        player.prepare(loopingSource);
        player.setPlayWhenReady(true);
        player.setVolume(0);
    }

    public void pause() {
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    private void initializePlayer() {
        if (player == null) {
            // this needs to be done for each video
            TrackSelection.Factory videoTrackSelectionFactory =
                    new AdaptiveVideoTrackSelection.Factory(BANDWIDTH_METER);
            trackSelector = new DefaultTrackSelector(mainHandler, videoTrackSelectionFactory);
            trackSelector.addListener(new TrackSelectorEventListener());
            final DefaultLoadControl loadControl = new DefaultLoadControl();
            player = ExoPlayerFactory.newSimpleInstance(context,
                    trackSelector,
                    loadControl);
            player.addListener(new ExoPlayerEventListener());
        }
    }

    /* PACKAGE */ void releasePlayer() {
        if (player != null) {
            if (currentlyPlayingView != null) {
                currentlyPlayingView.clearPlayingPosition();
                Timeline timeline = player.getCurrentTimeline();
                if (timeline != null) {
                    playerWindow = player.getCurrentWindowIndex();
                    Timeline.Window window = timeline.getWindow(playerWindow,
                            new Timeline.Window());
                    if (!window.isDynamic) {
                        currentlyPlayingView.setPlayerPosition(window.isSeekable ? player.getCurrentPosition() : C.TIME_UNSET);
                    }
                }
                currentlyPlayingView = null;
            }
            player.release();
            player = null;
            trackSelector = null;
        }
    }

    /* PACKAGE */ void onViewRecycled(VideoAdapter.VideoViewHolder viewHolder) {
        if (this.currentlyPlayingView == viewHolder) {
            releasePlayer();
            this.currentlyPlayingView = null;
        }
    }

    private class TrackSelectorEventListener
            implements TrackSelector.EventListener<MappingTrackSelector.MappedTrackInfo> {

        @Override
        public void onTrackSelectionsChanged(TrackSelections<? extends MappingTrackSelector.MappedTrackInfo> trackSelections) {
            MappingTrackSelector.MappedTrackInfo trackInfo = trackSelections.info;
            if (trackInfo.hasOnlyUnplayableTracks(C.TRACK_TYPE_VIDEO) ||
                    trackInfo.hasOnlyUnplayableTracks(C.TRACK_TYPE_AUDIO)) {
                if (currentlyPlayingView != null) {
                    currentlyPlayingView.setOnError();
                }
            }
        }
    }

    private class ExoPlayerEventListener implements ExoPlayer.EventListener {

        @Override
        public void onLoadingChanged(boolean isLoading) {
            // noop
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (currentlyPlayingView != null) {
                currentlyPlayingView.setupViewState(true, playbackState == STATE_READY);
            }
        }

        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest) {
            // noop
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            if (currentlyPlayingView != null) {
                currentlyPlayingView.setOnError();
            }
        }

        @Override
        public void onPositionDiscontinuity() {
            // noop
        }
    }
}
