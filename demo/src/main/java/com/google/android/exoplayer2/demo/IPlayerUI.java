package com.google.android.exoplayer2.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.MediaController;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;

/**
 * Created by ymr on 16/8/12.
 */
public interface IPlayerUI extends MappingTrackSelector.EventListener, ExoPlayer.EventListener, SimpleExoPlayer.VideoListener {
    Activity getContext();

    void showToast(int errorStringId);

    void updateButtonVisibilities();

    MediaController getMyMediaController();

    TextRenderer.Output getSubtitleView();

    SurfaceHolder getHolder();

    View getRootView();

    void showToast(String myString);
}
