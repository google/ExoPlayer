package com.google.android.exoplayer2.demo;

import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.Timeline;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.MediaControllerPrevNextClickListener;
import com.google.android.exoplayer2.ui.PlayerControl;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

public class PlayerImp implements IPlayer {
    private final IPlayerUI playerUI;
    private SimpleExoPlayer player;
    private EventLogger eventLogger;
    private String userAgent;
    private DefaultDataSourceFactory mediaDataSourceFactory;
    private MappingTrackSelector trackSelector;
    private boolean playerNeedsSource;
    private boolean shouldRestorePosition;
    private int playerPeriodIndex;
    private long playerPosition;
    private Handler mainHandler = new Handler();

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private float speed = 1.0f;
    private TrackSelection.Factory videoTrackSelectionFactory;
    private Uri uri;

    public PlayerImp(IPlayerUI playerUI) {
        this.playerUI = playerUI;
    }

    @Override
    public void setSpeed(float speed) {
        this.speed = speed;
        if (player != null) {
            flagThePosition();
            if (Util.SDK_INT >= 23) {
                player.setPlaybackSpeed(speed);
            } else {
                realReleasePlayer();
                initPlayer(uri);
            }
        }
    }

    @Override
    public void initPlayer(Uri uri) {
        this.uri = uri;
        if (!hasPlayer()) {
            boolean preferExtensionDecoders = false;
            eventLogger = new EventLogger();
            videoTrackSelectionFactory = new AdaptiveVideoTrackSelection.Factory(BANDWIDTH_METER);
            trackSelector = new DefaultTrackSelector(mainHandler, videoTrackSelectionFactory);
            trackSelector.addListener(playerUI);
            trackSelector.addListener(eventLogger);
            newPlayer(preferExtensionDecoders);
            playerUI.getMyMediaController().setMediaPlayer(new PlayerControl(player));
            playerUI.getMyMediaController().setPrevNextListeners(new MediaControllerPrevNextClickListener(player, true),
                    new MediaControllerPrevNextClickListener(player, false));
            playerUI.getMyMediaController().setAnchorView(playerUI.getRootView());
            playerNeedsSource = true;
        }
        if (playerNeedsSource) {
            MediaSource mediaSource = buildMediaSource(uri,"");
            player.setMediaSource(mediaSource, !shouldRestorePosition);
            playerNeedsSource = false;
            playerUI.updateButtonVisibilities();
        }
    }

    @Override
    public boolean hasPlayer() {
        return player != null;
    }

    @Override
    public boolean isRenderingVideo(MappingTrackSelector.TrackInfo trackInfo, int index) {
        return player.getRendererType(index) == C.TRACK_TYPE_VIDEO
                && trackInfo.getTrackSelection(index) != null;
    }

    @Override
    public void realReleasePlayer() {
        Timeline playerTimeline = player.getCurrentTimeline();
        shouldRestorePosition = playerTimeline != null && playerTimeline.isFinal();
        player.release();
        player = null;
        eventLogger = null;
        trackSelector = null;
    }

    private void newPlayer(boolean preferExtensionDecoders) {
        player = ExoPlayerFactory.newSimpleInstance(playerUI.getContext(), trackSelector, new DefaultLoadControl(),
                null, preferExtensionDecoders);
        player.addListener(playerUI);
        player.addListener(eventLogger);
        player.setDebugListener(eventLogger);
        player.setId3Output(eventLogger);
        player.setTextOutput(playerUI.getSubtitleView());
        player.setVideoListener(playerUI);
        player.setVideoSurfaceHolder(playerUI.getHolder());

        if (shouldRestorePosition) {
            player.seekTo(playerPeriodIndex, playerPosition);
        }
        player.setPlayWhenReady(true);
        player.setPlaybackSpeed(speed);
    }


    @Override
    public void onCreate() {
        userAgent = Util.getUserAgent(playerUI.getContext(), "ExoPlayerDemo");
        mediaDataSourceFactory = new DefaultDataSourceFactory(playerUI.getContext(), userAgent, BANDWIDTH_METER);
    }

    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
        int type = Util.inferContentType(!TextUtils.isEmpty(overrideExtension) ? "." + overrideExtension
                : uri.getLastPathSegment());
        switch (type) {
            case Util.TYPE_SS:
                return new SsMediaSource(uri, new DefaultDataSourceFactory(playerUI.getContext(), userAgent),
                        new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, eventLogger);
            case Util.TYPE_DASH:
                return new DashMediaSource(uri, new DefaultDataSourceFactory(playerUI.getContext(), userAgent),
                        new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, eventLogger);
            case Util.TYPE_HLS:
                return new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, eventLogger);
            case Util.TYPE_OTHER:
                return new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(),
                        mainHandler, eventLogger);
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }

    @Override
    public MappingTrackSelector.TrackInfo getTrackInfo() {
        return trackSelector.getTrackInfo();
    }

    @Override
    public boolean isMediaNeddSource() {
        return playerNeedsSource;
    }

    @Override
    public int getRendererType(int rendererIndx) {
        return player.getRendererType(rendererIndx);
    }

    @Override
    public int getCurrentPeriodIndex() {
        return player.getCurrentPeriodIndex();
    }

    @Override
    public long getCurrentPosition() {
        return player.getCurrentPosition();
    }

    @Override
    public SimpleExoPlayer getExoPlayer() {
        return player;
    }

    @Override
    public void onError() {
        playerNeedsSource = true;
    }

    @Override
    public TrackSelectionHelper createTrackSelectionHelper() {
        return new TrackSelectionHelper(trackSelector, videoTrackSelectionFactory);
    }

    @Override
    public void resetPosition() {
        playerPosition = 0;
    }

    private void flagThePosition() {
      playerPeriodIndex = getCurrentPeriodIndex();
      playerPosition = getCurrentPosition();
    }
}