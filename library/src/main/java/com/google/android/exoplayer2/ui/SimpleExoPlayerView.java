/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.R;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.TextRenderer;

import java.util.List;

/**
 * Displays a video stream.
 */
@TargetApi(16)
public final class SimpleExoPlayerView extends FrameLayout {

  private final View surfaceView;
  private final View shutterView;
  private final SubtitleView subtitleLayout;
  private final AspectRatioFrameLayout layout;
  private final PlaybackControlView controller;
  private final ComponentListener componentListener;
  private SimpleExoPlayer player;
  private boolean useController = true;

  public SimpleExoPlayerView(Context context) {
    this(context, null);
  }

  public SimpleExoPlayerView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SimpleExoPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);

    boolean useTextureView = false;
    if (attrs != null) {
      TypedArray a = context.getTheme().obtainStyledAttributes(attrs,
          R.styleable.SimpleExoPlayerView, 0, 0);
      try {
        useController = a.getBoolean(R.styleable.SimpleExoPlayerView_use_controller,
            useController);
        useTextureView = a.getBoolean(R.styleable.SimpleExoPlayerView_use_texture_view,
            useTextureView);
      } finally {
        a.recycle();
      }
    }

    LayoutInflater.from(context).inflate(R.layout.exoplayer_video_view, this);
    componentListener = new ComponentListener();
    layout = (AspectRatioFrameLayout) findViewById(R.id.video_frame);
    controller = (PlaybackControlView) findViewById(R.id.control);
    shutterView = findViewById(R.id.shutter);
    subtitleLayout = (SubtitleView) findViewById(R.id.subtitles);
    subtitleLayout.setUserDefaultStyle();
    subtitleLayout.setUserDefaultTextSize();

    View view = useTextureView ? new TextureView(context) : new SurfaceView(context);
    ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT);
    view.setLayoutParams(params);
    surfaceView = view;
    layout.addView(surfaceView, 0);
  }

  /**
   * Set the {@link SimpleExoPlayer} to use. The {@link SimpleExoPlayer#setTextOutput} and
   * {@link SimpleExoPlayer#setVideoListener} method of the player will be called and previous
   * assignments are overridden.
   *
   * @param player The {@link SimpleExoPlayer} to use.
   */
  public void setPlayer(SimpleExoPlayer player) {
    if (this.player != null) {
      this.player.setTextOutput(null);
      this.player.setVideoListener(null);
      this.player.removeListener(componentListener);
      this.player.setVideoSurface(null);
    }
    this.player = player;

    if (player != null) {
      if (surfaceView instanceof TextureView) {
        player.setVideoTextureView((TextureView) surfaceView);
      } else if (surfaceView instanceof SurfaceView) {
        player.setVideoSurfaceView((SurfaceView) surfaceView);
      }
      player.setVideoListener(componentListener);
      player.addListener(componentListener);
      player.setTextOutput(componentListener);
    }
    setUseController(useController);
  }

  /**
   * Set the {@code useController} flag which indicates whether the playback control view should
   * be used or not. If set to {@code false} the controller is never visible and is disconnected
   * from the player.
   *
   * @param useController If {@code false} the playback control is never used.
   */
  public void setUseController(boolean useController) {
    this.useController = useController;
    if (useController) {
      controller.setPlayer(player);
    } else {
      controller.hide();
      controller.setPlayer(null);
    }
  }

  /**
   * Set the {@link PlaybackControlView.VisibilityListener}.
   *
   * @param listener The listener to be notified about visibility changes.
   */
  public void setControllerVisibilityListener(PlaybackControlView.VisibilityListener listener) {
    controller.setVisibilityListener(listener);
  }

  /**
   * Sets the rewind increment in milliseconds.
   *
   * @param rewindMs The rewind increment in milliseconds.
   */
  public void setRewindIncrementMs(int rewindMs) {
    controller.setRewindIncrementMs(rewindMs);
  }

  /**
   * Sets the fast forward increment in milliseconds.
   *
   * @param fastForwardMs The fast forward increment in milliseconds.
   */
  public void setFastForwardIncrementMs(int fastForwardMs) {
    controller.setFastForwardIncrementMs(fastForwardMs);
  }

  /**
   * Sets the duration to show the playback control in milliseconds.
   *
   * @param showDurationMs The duration in milliseconds.
   */
  public void setControlShowDurationMs(int showDurationMs) {
    controller.setShowDurationMs(showDurationMs);
  }

  /**
   * Get the view onto which video is rendered. This is either a {@link SurfaceView} (default)
   * or a {@link TextureView} if the {@code use_texture_view} view attribute has been set to true.
   *
   * @return either a {@link SurfaceView} or a {@link TextureView}.
   */
  public View getVideoSurfaceView() {
    return surfaceView;
  }

  @Override
  public boolean onTouchEvent(MotionEvent ev) {
    if (useController && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
      if (controller.isVisible()) {
        controller.hide();
      } else {
        controller.show();
      }
    }
    return true;
  }
  @Override
  public boolean onTrackballEvent(MotionEvent ev) {
    if (!useController) {
      return false;
    }
    controller.show();
    return true;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    return useController ? controller.dispatchKeyEvent(event) : super.dispatchKeyEvent(event);
  }

  private final class ComponentListener implements SimpleExoPlayer.VideoListener,
      TextRenderer.Output, ExoPlayer.EventListener {

    // TextRenderer.Output implementation

    @Override
    public void onCues(List<Cue> cues) {
      subtitleLayout.onCues(cues);
    }

    // SimpleExoPlayer.VideoListener implementation

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
        float pixelWidthHeightRatio) {
      layout.setAspectRatio(height == 0 ? 1 : (width * pixelWidthHeightRatio) / height);
    }

    @Override
    public void onRenderedFirstFrame(Surface surface) {
      shutterView.setVisibility(GONE);
    }

    @Override
    public void onVideoDisabled(DecoderCounters counters) {
      shutterView.setVisibility(VISIBLE);
    }

    // ExoPlayer.EventListener implementation

    @Override
    public void onLoadingChanged(boolean isLoading) {
      // Do nothing.
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      if (useController && playbackState == ExoPlayer.STATE_ENDED) {
        controller.show(0);
      }
    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
      // Do nothing.
    }

    @Override
    public void onPositionDiscontinuity() {
      // Do nothing.
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
      // Do nothing.
    }

  }

}
