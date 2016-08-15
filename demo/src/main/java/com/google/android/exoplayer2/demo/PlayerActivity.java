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
package com.google.android.exoplayer2.demo;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.source.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.TextRenderer;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.TrackInfo;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.KeyCompatibleMediaController;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.util.Util;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

/**
 * An activity that plays media using {@link SimpleExoPlayer}.
 */
public class PlayerActivity extends Activity implements OnKeyListener, OnTouchListener,
    OnClickListener, ExoPlayer.EventListener, SimpleExoPlayer.VideoListener,
    MappingTrackSelector.EventListener, IPlayerUI {

  public static final String DRM_SCHEME_UUID_EXTRA = "drm_scheme_uuid";
  public static final String DRM_LICENSE_URL = "drm_license_url";
  public static final String PREFER_EXTENSION_DECODERS = "prefer_extension_decoders";

  public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
  public static final String EXTENSION_EXTRA = "extension";

  public static final String ACTION_VIEW_LIST =
      "com.google.android.exoplayer.demo.action.VIEW_LIST";
  public static final String URI_LIST_EXTRA = "uri_list";
  public static final String EXTENSION_LIST_EXTRA = "extension_list";

  private static final CookieManager DEFAULT_COOKIE_MANAGER;
  private TrackSelectionHelper trackSelectionHelper;
  static {
    DEFAULT_COOKIE_MANAGER = new CookieManager();
    DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  private IPlayer player = new PlayerImp(this);

  private MediaController mediaController;
  private View rootView;
  private LinearLayout debugRootView;
  private View shutterView;
  private AspectRatioFrameLayout videoFrame;
  private SurfaceView surfaceView;
  private TextView debugTextView;
  private SubtitleView subtitleView;
  private Button retryButton;

  private int playerPeriodIndex;
  private long playerPosition;

  private DebugTextViewHelper debugViewHelper;
  private Spinner spinnerSpeeds;

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    player.onCreate();
    if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
      CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
    }

    setContentView(R.layout.player_activity);
    rootView = findViewById(R.id.root);
    spinnerSpeeds = ((Spinner) findViewById(R.id.spinner_speeds));
    rootView.setOnTouchListener(this);
    rootView.setOnKeyListener(this);
    final String[] speeds = getResources().getStringArray(R.array.speed_values);
    spinnerSpeeds.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        flagThePosition();
        player.setSpeed(Float.valueOf(speeds[position]));
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {

      }
    });
    shutterView = findViewById(R.id.shutter);
    debugRootView = (LinearLayout) findViewById(R.id.controls_root);
    videoFrame = (AspectRatioFrameLayout) findViewById(R.id.video_frame);
    surfaceView = (SurfaceView) findViewById(R.id.surface_view);
    debugTextView = (TextView) findViewById(R.id.debug_text_view);
    subtitleView = (SubtitleView) findViewById(R.id.subtitles);
    subtitleView.setUserDefaultStyle();
    subtitleView.setUserDefaultTextSize();
    mediaController = new KeyCompatibleMediaController(this);
    mediaController.setPrevNextListeners(this, this);
    retryButton = (Button) findViewById(R.id.retry_button);
    retryButton.setOnClickListener(this);
    player.setSpeed(1.0f);
  }

  @Override
  public void onNewIntent(Intent intent) {
    releasePlayer();
    playerPosition = 0;
    setIntent(intent);
  }

  @Override
  public void onStart() {
    super.onStart();
    initAfter23();
  }

  private void initAfter23() {
    if (Util.SDK_INT > 23) {
      initPlayer();
    }
  }

  private void initPlayer() {
    Uri uri = Uri.parse("asset:///cf752b1c12ce452b3040cab2f90bc265_h264818000nero_aac32-1.mp4");
    player.initPlayer(uri);
    trackSelectionHelper = player.createTrackSelectionHelper();
    debugViewHelper = new DebugTextViewHelper(player.getExoPlayer(), debugTextView);
    debugViewHelper.start();
  }

  @Override
  public void onResume() {
    super.onResume();
    initPre23();
  }

  private void initPre23() {
    if ((Util.SDK_INT <= 23 || !player.hasPlayer())) {
      initPlayer();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    releasePre23();
  }

  private void releasePre23() {
    if (Util.SDK_INT <= 23) {
      releasePlayer();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    releaseAfter23();
  }

  private void releaseAfter23() {
    if (Util.SDK_INT > 23) {
      releasePlayer();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions,
      int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      initPlayer();
    } else {
      showToast(R.string.storage_permission_denied);
      finish();
    }
  }

  // OnTouchListener methods

  @Override
  public boolean onTouch(View view, MotionEvent motionEvent) {
    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
      toggleControlsVisibility();
    } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
      view.performClick();
    }
    return true;
  }

  // OnKeyListener methods

  @Override
  public boolean onKey(View v, int keyCode, KeyEvent event) {
    return keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_ESCAPE
        && keyCode != KeyEvent.KEYCODE_MENU && mediaController.dispatchKeyEvent(event);
  }

  // OnClickListener methods

  @Override
  public void onClick(View view) {
    if (view == retryButton) {
      initPlayer();
    } else if (view.getParent() == debugRootView) {
      trackSelectionHelper.showSelectionDialog(this, ((Button) view).getText(),
              player.getTrackInfo(), (int) view.getTag());
    }
  }


  @Override
  public void onLoadingChanged(boolean isLoading) {
    // Do nothing.
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
    if (playbackState == ExoPlayer.STATE_ENDED) {
      showControls();
    }
    updateButtonVisibilities();
  }

  @Override
  public void onPlayWhenReadyCommitted() {
    // Do nothing.
  }

  @Override
  public void onPositionDiscontinuity(int periodIndex, long positionMs) {
    if (mediaController.isShowing()) {
      // The MediaController is visible, so force it to show the updated position immediately.
      mediaController.show();
    }
  }

  @Override
  public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
    // Do nothing.
  }

  @Override
  public void onPlayerError(ExoPlaybackException e) {
    String errorString = null;
    if (e.type == ExoPlaybackException.TYPE_RENDERER) {
      Exception cause = e.getRendererException();
      if (cause instanceof DecoderInitializationException) {
        // Special case for decoder initialization failures.
        DecoderInitializationException decoderInitializationException =
            (DecoderInitializationException) cause;
        if (decoderInitializationException.decoderName == null) {
          if (decoderInitializationException.getCause() instanceof DecoderQueryException) {
            errorString = getString(R.string.error_querying_decoders);
          } else if (decoderInitializationException.secureDecoderRequired) {
            errorString = getString(R.string.error_no_secure_decoder,
                decoderInitializationException.mimeType);
          } else {
            errorString = getString(R.string.error_no_decoder,
                decoderInitializationException.mimeType);
          }
        } else {
          errorString = getString(R.string.error_instantiating_decoder,
              decoderInitializationException.decoderName);
        }
      }
    }
    if (errorString != null) {
      showToast(errorString);
    }
    player.onError();
    updateButtonVisibilities();
    showControls();
  }

  // SimpleExoPlayer.VideoListener implementation

  @Override
  public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
      float pixelWidthAspectRatio) {
    videoFrame.setAspectRatio(height == 0 ? 1 : (width * pixelWidthAspectRatio) / height);
  }

  @Override
  public void onRenderedFirstFrame(Surface surface) {
    shutterView.setVisibility(View.GONE);
  }

  // MappingTrackSelector.EventListener implementation

  @Override
  public void onTracksChanged(TrackInfo trackInfo) {
    updateButtonVisibilities();
    if (trackInfo.hasOnlyUnplayableTracks(C.TRACK_TYPE_VIDEO)) {
      showToast(R.string.error_unsupported_video);
    }
    if (trackInfo.hasOnlyUnplayableTracks(C.TRACK_TYPE_AUDIO)) {
      showToast(R.string.error_unsupported_audio);
    }
    boolean renderingVideo = false;
    for (int i = 0; i < trackInfo.rendererCount; i++) {
      if (player.isRenderingVideo(trackInfo, i)) {
        renderingVideo = true;
        break;
      }
    }
    if (!renderingVideo) {
      shutterView.setVisibility(View.VISIBLE);
    }
  }

  // User controls

  public void updateButtonVisibilities() {
    debugRootView.removeAllViews();

    retryButton.setVisibility(player.isMediaNeddSource() ? View.VISIBLE : View.GONE);
    debugRootView.addView(retryButton);

    if (!player.hasPlayer()) {
      return;
    }

    TrackInfo trackInfo = player.getTrackInfo();
    if (trackInfo == null) {
      return;
    }

    int rendererCount = trackInfo.rendererCount;
    for (int i = 0; i < rendererCount; i++) {
      TrackGroupArray trackGroups = trackInfo.getTrackGroups(i);
      if (trackGroups.length != 0) {
        Button button = new Button(this);
        int label;
        switch (player.getRendererType(i)) {
          case C.TRACK_TYPE_AUDIO:
            label = R.string.audio;
            break;
          case C.TRACK_TYPE_VIDEO:
            label = R.string.video;
            break;
          case C.TRACK_TYPE_TEXT:
            label = R.string.text;
            break;
          default:
            continue;
        }
        button.setText(label);
        button.setTag(i);
        button.setOnClickListener(this);
        debugRootView.addView(button);
      }
    }
  }

  @Override
  public MediaController getMyMediaController() {
    return mediaController;
  }

  @Override
  public TextRenderer.Output getSubtitleView() {
    return subtitleView;
  }

  @Override
  public SurfaceHolder getHolder() {
    return surfaceView.getHolder();
  }

  @Override
  public int getPlayerPeriodIndex() {
    return playerPeriodIndex;
  }

  @Override
  public long getPlayerPosition() {
    return playerPosition;
  }

  @Override
  public String getMyString(int id, Object... action) {
    return getString(id, action);
  }

  @Override
  public View getRootView() {
    return rootView;
  }

  @Override
  public TextView getDebugTextView() {
    return debugTextView;
  }

  private void toggleControlsVisibility()  {
    if (mediaController.isShowing()) {
      mediaController.hide();
      debugRootView.setVisibility(View.GONE);
    } else {
      showControls();
    }
  }

  private void showControls() {
    mediaController.show(0);
    debugRootView.setVisibility(View.VISIBLE);
  }

  public void showToast(int messageId) {
    showToast(getString(messageId));
  }

  public void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

  private void releasePlayer() {
    if (player.hasPlayer()) {
      shutterView.setVisibility(View.VISIBLE);
      flagThePosition();
      player.realReleasePlayer();
      trackSelectionHelper = null;
      debugViewHelper.stop();
      debugViewHelper = null;
    }
  }

  private void flagThePosition() {
    playerPeriodIndex = player.getCurrentPeriodIndex();
    playerPosition = player.getCurrentPosition();
  }

  @Override
  public Activity getContext() {
    return this;
  }
}
