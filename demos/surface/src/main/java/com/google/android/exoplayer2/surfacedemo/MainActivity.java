/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.surfacedemo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
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
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.UUID;

/** Activity that demonstrates use of {@link SurfaceControl} with ExoPlayer. */
public final class MainActivity extends Activity {

  private static final String DEFAULT_MEDIA_URI =
      "https://storage.googleapis.com/exoplayer-test-media-1/mkv/android-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv";
  private static final String SURFACE_CONTROL_NAME = "surfacedemo";

  private static final String ACTION_VIEW = "com.google.android.exoplayer.surfacedemo.action.VIEW";
  private static final String EXTENSION_EXTRA = "extension";
  private static final String DRM_SCHEME_EXTRA = "drm_scheme";
  private static final String DRM_LICENSE_URL_EXTRA = "drm_license_url";
  private static final String OWNER_EXTRA = "owner";

  private boolean isOwner;
  @Nullable private PlayerControlView playerControlView;
  @Nullable private SurfaceView fullScreenView;
  @Nullable private SurfaceView nonFullScreenView;
  @Nullable private SurfaceView currentOutputView;

  @Nullable private static SimpleExoPlayer player;
  @Nullable private static SurfaceControl surfaceControl;
  @Nullable private static Surface videoSurface;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main_activity);
    playerControlView = findViewById(R.id.player_control_view);
    fullScreenView = findViewById(R.id.full_screen_view);
    fullScreenView.setOnClickListener(
        v -> {
          setCurrentOutputView(nonFullScreenView);
          Assertions.checkNotNull(fullScreenView).setVisibility(View.GONE);
        });
    attachSurfaceListener(fullScreenView);
    isOwner = getIntent().getBooleanExtra(OWNER_EXTRA, /* defaultValue= */ true);
    GridLayout gridLayout = findViewById(R.id.grid_layout);
    for (int i = 0; i < 9; i++) {
      View view;
      if (i == 0) {
        Button button = new Button(/* context= */ this);
        view = button;
        button.setText(getString(R.string.no_output_label));
        button.setOnClickListener(v -> reparent(/* surfaceView= */ null));
      } else if (i == 1) {
        Button button = new Button(/* context= */ this);
        view = button;
        button.setText(getString(R.string.full_screen_label));
        button.setOnClickListener(
            v -> {
              setCurrentOutputView(fullScreenView);
              Assertions.checkNotNull(fullScreenView).setVisibility(View.VISIBLE);
            });
      } else if (i == 2) {
        Button button = new Button(/* context= */ this);
        view = button;
        button.setText(getString(R.string.new_activity_label));
        button.setOnClickListener(
            v ->
                startActivity(
                    new Intent(MainActivity.this, MainActivity.class)
                        .putExtra(OWNER_EXTRA, /* value= */ false)));
      } else {
        SurfaceView surfaceView = new SurfaceView(this);
        view = surfaceView;
        attachSurfaceListener(surfaceView);
        surfaceView.setOnClickListener(
            v -> {
              setCurrentOutputView(surfaceView);
              nonFullScreenView = surfaceView;
            });
        if (nonFullScreenView == null) {
          nonFullScreenView = surfaceView;
        }
      }
      gridLayout.addView(view);
      GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams();
      layoutParams.width = 0;
      layoutParams.height = 0;
      layoutParams.columnSpec = GridLayout.spec(i % 3, 1f);
      layoutParams.rowSpec = GridLayout.spec(i / 3, 1f);
      layoutParams.bottomMargin = 10;
      layoutParams.leftMargin = 10;
      layoutParams.topMargin = 10;
      layoutParams.rightMargin = 10;
      view.setLayoutParams(layoutParams);
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    if (isOwner && player == null) {
      initializePlayer();
    }

    setCurrentOutputView(nonFullScreenView);

    PlayerControlView playerControlView = Assertions.checkNotNull(this.playerControlView);
    playerControlView.setPlayer(player);
    playerControlView.show();
  }

  @Override
  public void onPause() {
    super.onPause();

    Assertions.checkNotNull(playerControlView).setPlayer(null);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (isOwner && isFinishing()) {
      if (surfaceControl != null) {
        surfaceControl.release();
        surfaceControl = null;
      }
      if (videoSurface != null) {
        videoSurface.release();
        videoSurface = null;
      }
      if (player != null) {
        player.release();
        player = null;
      }
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
    if (intent.hasExtra(DRM_SCHEME_EXTRA)) {
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
    player.prepare(mediaSource);
    player.setPlayWhenReady(true);
    player.setRepeatMode(Player.REPEAT_MODE_ALL);

    surfaceControl =
        new SurfaceControl.Builder()
            .setName(SURFACE_CONTROL_NAME)
            .setBufferSize(/* width= */ 0, /* height= */ 0)
            .build();
    videoSurface = new Surface(surfaceControl);
    player.setVideoSurface(videoSurface);
    MainActivity.player = player;
  }

  private void setCurrentOutputView(@Nullable SurfaceView surfaceView) {
    currentOutputView = surfaceView;
    if (surfaceView != null && surfaceView.getHolder().getSurface() != null) {
      reparent(surfaceView);
    }
  }

  private void attachSurfaceListener(SurfaceView surfaceView) {
    surfaceView
        .getHolder()
        .addCallback(
            new SurfaceHolder.Callback() {
              @Override
              public void surfaceCreated(SurfaceHolder surfaceHolder) {
                if (surfaceView == currentOutputView) {
                  reparent(surfaceView);
                }
              }

              @Override
              public void surfaceChanged(
                  SurfaceHolder surfaceHolder, int format, int width, int height) {}

              @Override
              public void surfaceDestroyed(SurfaceHolder surfaceHolder) {}
            });
  }

  private static void reparent(@Nullable SurfaceView surfaceView) {
    SurfaceControl surfaceControl = Assertions.checkNotNull(MainActivity.surfaceControl);
    if (surfaceView == null) {
      new SurfaceControl.Transaction()
          .reparent(surfaceControl, /* newParent= */ null)
          .setBufferSize(surfaceControl, /* w= */ 0, /* h= */ 0)
          .setVisibility(surfaceControl, /* visible= */ false)
          .apply();
    } else {
      SurfaceControl newParentSurfaceControl = surfaceView.getSurfaceControl();
      new SurfaceControl.Transaction()
          .reparent(surfaceControl, newParentSurfaceControl)
          .setBufferSize(surfaceControl, surfaceView.getWidth(), surfaceView.getHeight())
          .setVisibility(surfaceControl, /* visible= */ true)
          .apply();
    }
  }
}
