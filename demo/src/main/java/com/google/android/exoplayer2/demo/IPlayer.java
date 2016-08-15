package com.google.android.exoplayer2.demo;

import android.net.Uri;
import android.view.View;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;

import java.util.UUID;

/**
 * Created by ymr on 16/8/12.
 */

public interface IPlayer {
    void setSpeed(float speed);

    void initPlayer(Uri uri);

    boolean hasPlayer();

    boolean isRenderingVideo(MappingTrackSelector.TrackInfo trackInfo, int index);

    void realReleasePlayer();

    void onCreate();

    void clickOther(View view);

    MediaSource buildMediaSource(Uri uri, String overrideExtension);

    MappingTrackSelector.TrackInfo getTrackInfo();

    boolean isMediaNeddSource();

    int getRendererType(int rendererIndx);

    int getCurrentPeriodIndex();

    long getCurrentPosition();

    SimpleExoPlayer getExoPlayer();

    void onError();
}
