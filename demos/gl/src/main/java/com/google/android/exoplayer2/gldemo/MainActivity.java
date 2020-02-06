/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.gldemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaCrypto;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Util;
import java.util.UUID;

/**
 * Activity that demonstrates playback of video to an {@link android.opengl.GLSurfaceView} with
 * postprocessing of the video content using GL.
 */
public final class MainActivity extends Activity {

  private static final String DEFAULT_MEDIA_URI =
      "https://storage.googleapis.com/exoplayer-test-media-1/mkv/android-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv";

  private static final String ACTION_VIEW = "com.google.android.exoplayer.gldemo.action.VIEW";
  private static final String EXTENSION_EXTRA = "extension";
  private static final String DRM_SCHEME_EXTRA = "drm_scheme";
  private static final String DRM_LICENSE_URL_EXTRA = "drm_license_url";

  @Nullable private PlayerView playerView;
  @Nullable private VideoProcessingGLSurfaceView videoProcessingGLSurfaceView;

  @Nullable private SimpleExoPlayer player;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main_activity);
    playerView = findViewById(R.id.player_view);

    Context context = getApplicationContext();
    boolean requestSecureSurface = getIntent().hasExtra(DRM_SCHEME_EXTRA);
    if (requestSecureSurface && !GlUtil.isProtectedContentExtensionSupported(context)) {
      Toast.makeText(
              context, R.string.error_protected_content_extension_not_supported, Toast.LENGTH_LONG)
          .show();
    }

    VideoProcessingGLSurfaceView videoProcessingGLSurfaceView =
        new VideoProcessingGLSurfaceView(
            context, requestSecureSurface, new BitmapOverlayVideoProcessor(context));
    FrameLayout contentFrame = findViewById(R.id.exo_content_frame);
    contentFrame.addView(videoProcessingGLSurfaceView);
    this.videoProcessingGLSurfaceView = videoProcessingGLSurfaceView;
  }

  @Override
  public void onStart() {
    super.onStart();
    if (Util.SDK_INT > 23) {
      initializePlayer();
      if (playerView != null) {
        playerView.onResume();
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (Util.SDK_INT <= 23 || player == null) {
      initializePlayer();
      if (playerView != null) {
        playerView.onResume();
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      if (playerView != null) {
        playerView.onPause();
      }
      releasePlayer();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      if (playerView != null) {
        playerView.onPause();
      }
      releasePlayer();
    }
  }

  private void initializePlayer() {
    Intent intent = getIntent();
    String action = intent.getAction();
    Uri uri =
        ACTION_VIEW.equals(action)
            ? Assertions.checkNotNull(intent.getData())
            : Uri.parse(DEFAULT_MEDIA_URI);
    String userAgent = Util.getUserAgent(this, getString(R.string.application_name));
    DrmSessionManager<ExoMediaCrypto> drmSessionManager;
    if (Util.SDK_INT >= 18 && intent.hasExtra(DRM_SCHEME_EXTRA)) {
      String drmScheme = Assertions.checkNotNull(intent.getStringExtra(DRM_SCHEME_EXTRA));
      String drmLicenseUrl = Assertions.checkNotNull(intent.getStringExtra(DRM_LICENSE_URL_EXTRA));
      UUID drmSchemeUuid = Assertions.checkNotNull(Util.getDrmUuid(drmScheme));
      HttpDataSource.Factory licenseDataSourceFactory = new DefaultHttpDataSourceFactory(userAgent);
      HttpMediaDrmCallback drmCallback =
          new HttpMediaDrmCallback(drmLicenseUrl, licenseDataSourceFactory);
      drmSessionManager =
          new DefaultDrmSessionManager.Builder()
              .setUuidAndExoMediaDrmProvider(drmSchemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
              .build(drmCallback);
    } else {
      drmSessionManager = DrmSessionManager.getDummyDrmSessionManager();
    }

    DataSource.Factory dataSourceFactory =
        new DefaultDataSourceFactory(
            this, Util.getUserAgent(this, getString(R.string.application_name)));
    MediaSource mediaSource;
    @C.ContentType int type = Util.inferContentType(uri, intent.getStringExtra(EXTENSION_EXTRA));
    if (type == C.TYPE_DASH) {
      mediaSource =
          new DashMediaSource.Factory(dataSourceFactory)
              .setDrmSessionManager(drmSessionManager)
              .createMediaSource(uri);
    } else if (type == C.TYPE_OTHER) {
      mediaSource =
          new ProgressiveMediaSource.Factory(dataSourceFactory)
              .setDrmSessionManager(drmSessionManager)
              .createMediaSource(uri);
    } else {
      throw new IllegalStateException();
    }

    SimpleExoPlayer player = new SimpleExoPlayer.Builder(getApplicationContext()).build();
    player.setRepeatMode(Player.REPEAT_MODE_ALL);
    player.prepare(mediaSource);
    player.setPlayWhenReady(true);
    VideoProcessingGLSurfaceView videoProcessingGLSurfaceView =
        Assertions.checkNotNull(this.videoProcessingGLSurfaceView);
    videoProcessingGLSurfaceView.setVideoComponent(
        Assertions.checkNotNull(player.getVideoComponent()));
    Assertions.checkNotNull(playerView).setPlayer(player);
    player.addAnalyticsListener(new EventLogger(/* trackSelector= */ null));
    this.player = player;
  }

  private void releasePlayer() {
    Assertions.checkNotNull(playerView).setPlayer(null);
    if (player != null) {
      player.release();
      Assertions.checkNotNull(videoProcessingGLSurfaceView).setVideoComponent(null);
      player = null;
    }
  }
}
