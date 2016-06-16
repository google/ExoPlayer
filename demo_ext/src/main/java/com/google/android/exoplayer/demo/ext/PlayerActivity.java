/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.demo.ext;

import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.ext.flac.FlacExtractor;
import com.google.android.exoplayer.ext.flac.LibflacAudioTrackRenderer;
import com.google.android.exoplayer.ext.opus.LibopusAudioTrackRenderer;
import com.google.android.exoplayer.ext.vp9.LibvpxVideoTrackRenderer;
import com.google.android.exoplayer.ext.vp9.VpxDecoderException;
import com.google.android.exoplayer.ext.vp9.VpxVideoSurfaceView;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.extractor.ogg.OggExtractor;
import com.google.android.exoplayer.extractor.webm.WebmExtractor;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.PlayerControl;
import com.google.android.exoplayer.util.Util;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Sample player that shows how to use ExoPlayer Extensions to playback VP9 Video and Opus Audio.
 */
public class PlayerActivity extends Activity implements
    LibvpxVideoTrackRenderer.EventListener, ExoPlayer.Listener {

  /*package*/ static final String CONTENT_TYPE_EXTRA = "content_type";
  /*package*/ static final String USE_OPENGL_ID_EXTRA = "use_opengl";

  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
  private static final int BUFFER_SEGMENT_COUNT = 160;

  private Uri contentUri;
  private int contentType;
  private boolean useOpenGL;

  private ExoPlayer player;
  private Handler handler;
  private MediaController mediaController;
  private AspectRatioFrameLayout videoFrame;
  private SurfaceView surfaceView;
  private VpxVideoSurfaceView vpxVideoSurfaceView;
  private TextView debugInfoView;
  private String debugInfo;
  private String playerState;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Intent intent = getIntent();
    contentUri = intent.getData();
    contentType = intent.getIntExtra(CONTENT_TYPE_EXTRA,
        Util.inferContentType(contentUri.toString()));
    useOpenGL = intent.getBooleanExtra(USE_OPENGL_ID_EXTRA, true);

    handler = new Handler();

    setContentView(R.layout.activity_video_player);
    View root = findViewById(R.id.root);
    root.setOnTouchListener(new OnTouchListener() {
      @Override
      public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
          toggleControlsVisibility();
        } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
          view.performClick();
        }
        return true;
      }
    });

    mediaController = new MediaController(this);
    mediaController.setAnchorView(root);
    videoFrame = (AspectRatioFrameLayout) findViewById(R.id.video_frame);
    surfaceView = (SurfaceView) findViewById(R.id.surface_view);
    vpxVideoSurfaceView = (VpxVideoSurfaceView) findViewById(R.id.vpx_surface_view);
    debugInfoView = (TextView) findViewById(R.id.debug_info);
    debugInfo = "";
    playerState = "";
    updateDebugInfoTextView();

    if (!maybeRequestPermission()) {
      startPlayback();
    }
  }

  private void startPlayback() {
    if (contentType != Util.TYPE_DASH) {
      startBasicPlayback();
    } else {
      startDashPlayback();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    stopPlayback();
  }

  private void startBasicPlayback() {
    player = ExoPlayer.Factory.newInstance(4);
    player.addListener(this);
    mediaController.setMediaPlayer(new PlayerControl(player));
    mediaController.setEnabled(true);
    ExtractorSampleSource sampleSource = new ExtractorSampleSource(
        contentUri,
        new DefaultUriDataSource(this, Util.getUserAgent(this, "ExoPlayerExtWebMDemo")),
        new DefaultAllocator(BUFFER_SEGMENT_SIZE), BUFFER_SEGMENT_SIZE * BUFFER_SEGMENT_COUNT,
        new WebmExtractor(), new FlacExtractor(), new OggExtractor());
    TrackRenderer videoRenderer =
        new LibvpxVideoTrackRenderer(sampleSource, true, handler, this, 50);
    if (useOpenGL) {
      player.sendMessage(videoRenderer, LibvpxVideoTrackRenderer.MSG_SET_OUTPUT_BUFFER_RENDERER,
          vpxVideoSurfaceView);
      surfaceView.setVisibility(View.GONE);
    } else {
      player.sendMessage(
          videoRenderer, LibvpxVideoTrackRenderer.MSG_SET_SURFACE,
          surfaceView.getHolder().getSurface());
      vpxVideoSurfaceView.setVisibility(View.GONE);
    }
    TrackRenderer opusAudioTrackRenderer = new LibopusAudioTrackRenderer(sampleSource);
    TrackRenderer flacAudioTrackRenderer = new LibflacAudioTrackRenderer(sampleSource);
    TrackRenderer mediaCodecAudioTrackRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
        MediaCodecSelector.DEFAULT);

    player.prepare(videoRenderer, opusAudioTrackRenderer, flacAudioTrackRenderer,
        mediaCodecAudioTrackRenderer);
    player.setPlayWhenReady(true);
  }

  private void startDashPlayback() {
    playerState = "Initializing";
    updateDebugInfoTextView();
    final String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like"
        + " Gecko) Chrome/38.0.2125.104 Safari/537.36";
    DashRendererBuilder rendererBuilder = new DashRendererBuilder(contentUri.toString(),
        userAgent, this);
    rendererBuilder.build();
  }

  public void onRenderersBuilt(TrackRenderer[] renderers) {
    surfaceView.setVisibility(View.GONE);
    player = ExoPlayer.Factory.newInstance(renderers.length);
    player.addListener(this);
    mediaController.setMediaPlayer(new PlayerControl(player));
    mediaController.setEnabled(true);
    player.sendMessage(renderers[0], LibvpxVideoTrackRenderer.MSG_SET_OUTPUT_BUFFER_RENDERER,
        vpxVideoSurfaceView);
    player.prepare(renderers);
    player.setPlayWhenReady(true);
  }

  @Override
  public void onDroppedFrames(int count, long elapsed) {
    // do nothing.
  }

  @Override
  public void onVideoSizeChanged(int width, int height) {
    videoFrame.setAspectRatio(height == 0 ? 1 : (width * 1.0f) / height);
    debugInfo = "Video: " + width + " x " + height;
    updateDebugInfoTextView();
  }

  @Override
  public void onDrawnToSurface(Surface surface) {
    // do nothing.
  }

  @Override
  public void onDecoderError(VpxDecoderException e) {
    debugInfo = "Libvpx decode failure. Giving up.";
    updateDebugInfoTextView();
  }

  @Override
  public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs,
      long initializationDurationMs) {
    // do nothing.
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int state) {
    switch (player.getPlaybackState()) {
      case ExoPlayer.STATE_BUFFERING:
        playerState = "buffering";
        break;
      case ExoPlayer.STATE_ENDED:
        playerState = "ended";
        break;
      case ExoPlayer.STATE_IDLE:
        playerState = "idle";
        break;
      case ExoPlayer.STATE_PREPARING:
        playerState = "preparing";
        break;
      case ExoPlayer.STATE_READY:
        playerState = "ready";
        break;
    }
    updateDebugInfoTextView();
  }

  @Override
  public void onPlayerError(ExoPlaybackException exception) {
    debugInfo = "Exoplayer Playback error. Giving up.";
    updateDebugInfoTextView();
    // TODO: show a retry button here.
  }

  @Override
  public void onPlayWhenReadyCommitted() {
    // Do nothing.
  }

  public Handler getMainHandler() {
    return handler;
  }

  // Permission management methods

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions,
      int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      startPlayback();
    } else {
      Toast.makeText(getApplicationContext(), R.string.storage_permission_denied,
          Toast.LENGTH_LONG).show();
      finish();
    }
  }

  /**
   * Checks whether it is necessary to ask for permission to read storage. If necessary, it also
   * requests permission.
   *
   * @return true if a permission request is made. False if it is not necessary.
   */
  @TargetApi(23)
  private boolean maybeRequestPermission() {
    if (requiresPermission(contentUri)) {
      requestPermissions(new String[] {permission.READ_EXTERNAL_STORAGE}, 0);
      return true;
    } else {
      return false;
    }
  }

  @TargetApi(23)
  private boolean requiresPermission(Uri uri) {
    return Util.SDK_INT >= 23 && Util.isLocalFileUri(uri)
        && checkSelfPermission(permission.READ_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED;
  }

  // Internal methods

  private void stopPlayback() {
    if (player != null) {
      player.stop();
      player.release();
      player = null;
    }
  }

  private void toggleControlsVisibility() {
    if (mediaController != null && player != null) {
      if (mediaController.isShowing()) {
        mediaController.hide();
      } else {
        mediaController.show(0);
      }
    }
  }

  private void updateDebugInfoTextView() {
    StringBuilder debugInfoText = new StringBuilder();
    debugInfoText.append(
        getString(R.string.libvpx_version, LibvpxVideoTrackRenderer.getLibvpxVersion()));
    debugInfoText.append(" ");
    debugInfoText.append(
        getString(R.string.libopus_version, LibopusAudioTrackRenderer.getLibopusVersion()));
    debugInfoText.append("\n");
    debugInfoText.append(getString(R.string.current_path, contentUri.toString()));
    debugInfoText.append(" ");
    debugInfoText.append(debugInfo);
    debugInfoText.append(" ");
    debugInfoText.append(playerState);
    debugInfoView.setText(debugInfoText.toString());
  }

}
