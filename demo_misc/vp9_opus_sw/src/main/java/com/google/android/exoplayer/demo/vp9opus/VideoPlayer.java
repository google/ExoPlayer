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
package com.google.android.exoplayer.demo.vp9opus;

import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.ext.opus.LibopusAudioTrackRenderer;
import com.google.android.exoplayer.ext.vp9.LibvpxVideoTrackRenderer;
import com.google.android.exoplayer.ext.vp9.VpxDecoderException;
import com.google.android.exoplayer.ext.vp9.VpxVideoSurfaceView;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.extractor.webm.WebmExtractor;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.PlayerControl;
import com.google.android.exoplayer.util.Util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * Sample player that shows how to use ExoPlayer Extensions to playback VP9 Video and Opus Audio.
 */
public class VideoPlayer extends Activity implements OnClickListener,
       LibvpxVideoTrackRenderer.EventListener, ExoPlayer.Listener {

  public static final String DASH_MANIFEST_URL_ID_EXTRA = "manifest_url";
  public static final String USE_OPENGL_ID_EXTRA = "use_opengl";

  private static final int FILE_PICKER_REQUEST = 1;
  private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
  private static final int BUFFER_SEGMENT_COUNT = 160;

  private boolean isDash;
  private String manifestUrl;
  private boolean useOpenGL;
  private String filename;

  private ExoPlayer player;
  private Handler handler;
  private MediaController mediaController;
  private AspectRatioFrameLayout videoFrame;
  private SurfaceView surfaceView;
  private VpxVideoSurfaceView vpxVideoSurfaceView;
  private TextView debugInfoView;
  private TextView playerStateView;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Intent intent = getIntent();
    manifestUrl = intent.getStringExtra(DASH_MANIFEST_URL_ID_EXTRA);
    isDash = manifestUrl != null;
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
    playerStateView = (TextView) findViewById(R.id.player_state);

    // Set the buttons' onclick listeners.
    ((Button) findViewById(R.id.choose_file)).setOnClickListener(this);
    ((Button) findViewById(R.id.play)).setOnClickListener(this);

    // In case of DASH, start playback right away.
    if (isDash) {
      findViewById(R.id.buttons).setVisibility(View.GONE);
      ((TextView) findViewById(R.id.filename)).setVisibility(View.GONE);
      startDashPlayback();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    stopPlayback();
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
      case R.id.choose_file:
        Intent intent = new Intent();
        intent.setClass(this, FilePickerActivity.class);
        startActivityForResult(intent, FILE_PICKER_REQUEST);
        break;
      case R.id.play:
        startBasicPlayback();
        break;
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case FILE_PICKER_REQUEST:
        if (resultCode == Activity.RESULT_OK) {
          filename = data.getStringExtra(FilePickerActivity.FILENAME_EXTRA_ID);
          ((TextView) findViewById(R.id.filename)).setText(
              getString(R.string.current_path, filename));
        }
        break;
    }
  }

  private void startBasicPlayback() {
    if (filename == null) {
      Toast.makeText(this, "Choose a file!", Toast.LENGTH_SHORT).show();
      return;
    }
    findViewById(R.id.buttons).setVisibility(View.GONE);
    player = ExoPlayer.Factory.newInstance(2);
    player.addListener(this);
    mediaController.setMediaPlayer(new PlayerControl(player));
    mediaController.setEnabled(true);
    ExtractorSampleSource sampleSource = new ExtractorSampleSource(
        Uri.fromFile(new File(filename)),
        new DefaultUriDataSource(this, Util.getUserAgent(this, "ExoPlayerExtWebMDemo")),
        new DefaultAllocator(BUFFER_SEGMENT_SIZE), BUFFER_SEGMENT_SIZE * BUFFER_SEGMENT_COUNT,
        new WebmExtractor());
    TrackRenderer videoRenderer =
        new LibvpxVideoTrackRenderer(sampleSource, true, handler, this, 50);
    if (useOpenGL) {
      player.sendMessage(videoRenderer, LibvpxVideoTrackRenderer.MSG_SET_VPX_SURFACE_VIEW,
          vpxVideoSurfaceView);
      surfaceView.setVisibility(View.GONE);
    } else {
      player.sendMessage(
          videoRenderer, LibvpxVideoTrackRenderer.MSG_SET_SURFACE,
          surfaceView.getHolder().getSurface());
      vpxVideoSurfaceView.setVisibility(View.GONE);
    }
    TrackRenderer audioRenderer = new LibopusAudioTrackRenderer(sampleSource);
    player.prepare(videoRenderer, audioRenderer);
    player.setPlayWhenReady(true);
  }

  private void startDashPlayback() {
    playerStateView.setText("Initializing");
    final String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like"
        + " Gecko) Chrome/38.0.2125.104 Safari/537.36";
    DashRendererBuilder rendererBuilder = new DashRendererBuilder(manifestUrl, userAgent, this);
    rendererBuilder.build();
  }

  public void onRenderersBuilt(TrackRenderer[] renderers) {
    surfaceView.setVisibility(View.GONE);
    player = ExoPlayer.Factory.newInstance(renderers.length);
    player.addListener(this);
    mediaController.setMediaPlayer(new PlayerControl(player));
    mediaController.setEnabled(true);
    player.sendMessage(renderers[0], LibvpxVideoTrackRenderer.MSG_SET_VPX_SURFACE_VIEW,
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
    debugInfoView.setText("Video: " + width + " x " + height);
  }

  @Override
  public void onDrawnToSurface(Surface surface) {
    // do nothing.
  }

  @Override
  public void onDecoderError(VpxDecoderException e) {
    debugInfoView.setText("Libvpx decode failure. Giving up.");
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int state) {
    String playerState = "";
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
    playerStateView.setText("Player State: " + playerState);
  }

  @Override
  public void onPlayerError(ExoPlaybackException exception) {
    debugInfoView.setText("Exoplayer Playback error. Giving up.");
    // TODO: show a retry button here.
  }

  @Override
  public void onPlayWhenReadyCommitted() {
    // Do nothing.
  }

  public Handler getMainHandler() {
    return handler;
  }

  private void stopPlayback() {
    if (player != null) {
      player.stop();
      player.release();
      player = null;
    }
  }

  private void toggleControlsVisibility()  {
    if (mediaController != null && player != null) {
      if (mediaController.isShowing()) {
        mediaController.hide();
      } else {
        mediaController.show(0);
      }
    }
  }

}
