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
package androidx.media3.demo.gl;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.DrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.PlayerView;
import java.util.UUID;

/**
 * Activity that demonstrates playback of video to an {@link android.opengl.GLSurfaceView} with
 * postprocessing of the video content using GL.
 */
public final class MainActivity extends Activity {

  private static final String TAG = "MainActivity";

  private static final String DEFAULT_MEDIA_URI =
      "https://storage.googleapis.com/exoplayer-test-media-1/mkv/android-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv";

  private static final String ACTION_VIEW = "androidx.media3.demo.gl.action.VIEW";
  private static final String EXTENSION_EXTRA = "extension";
  private static final String DRM_SCHEME_EXTRA = "drm_scheme";
  private static final String DRM_LICENSE_URL_EXTRA = "drm_license_url";

  @Nullable private PlayerView playerView;
  @Nullable private VideoProcessingGLSurfaceView videoProcessingGLSurfaceView;

  @Nullable private ExoPlayer player;

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
    checkNotNull(playerView);
    FrameLayout contentFrame = playerView.findViewById(R.id.exo_content_frame);
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
    DrmSessionManager drmSessionManager;
    if (intent.hasExtra(DRM_SCHEME_EXTRA)) {
      String drmScheme = Assertions.checkNotNull(intent.getStringExtra(DRM_SCHEME_EXTRA));
      String drmLicenseUrl = Assertions.checkNotNull(intent.getStringExtra(DRM_LICENSE_URL_EXTRA));
      UUID drmSchemeUuid = Assertions.checkNotNull(Util.getDrmUuid(drmScheme));
      DataSource.Factory licenseDataSourceFactory = new DefaultHttpDataSource.Factory();
      HttpMediaDrmCallback drmCallback =
          new HttpMediaDrmCallback(drmLicenseUrl, licenseDataSourceFactory);
      drmSessionManager =
          new DefaultDrmSessionManager.Builder()
              .setUuidAndExoMediaDrmProvider(drmSchemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
              .build(drmCallback);
    } else {
      drmSessionManager = DrmSessionManager.DRM_UNSUPPORTED;
    }

    DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
    MediaSource mediaSource;
    @Nullable String fileExtension = intent.getStringExtra(EXTENSION_EXTRA);
    @C.ContentType
    int type =
        TextUtils.isEmpty(fileExtension)
            ? Util.inferContentType(uri)
            : Util.inferContentTypeForExtension(fileExtension);
    if (type == C.CONTENT_TYPE_DASH) {
      mediaSource =
          new DashMediaSource.Factory(dataSourceFactory)
              .setDrmSessionManagerProvider(unusedMediaItem -> drmSessionManager)
              .createMediaSource(MediaItem.fromUri(uri));
    } else if (type == C.CONTENT_TYPE_OTHER) {
      mediaSource =
          new ProgressiveMediaSource.Factory(dataSourceFactory)
              .setDrmSessionManagerProvider(unusedMediaItem -> drmSessionManager)
              .createMediaSource(MediaItem.fromUri(uri));
    } else {
      throw new IllegalStateException();
    }

    ExoPlayer player = new ExoPlayer.Builder(getApplicationContext()).build();
    player.setRepeatMode(Player.REPEAT_MODE_ALL);
    player.setMediaSource(mediaSource);
    player.prepare();
    player.play();
    VideoProcessingGLSurfaceView videoProcessingGLSurfaceView =
        Assertions.checkNotNull(this.videoProcessingGLSurfaceView);
    videoProcessingGLSurfaceView.setPlayer(player);
    Assertions.checkNotNull(playerView).setPlayer(player);
    player.addAnalyticsListener(new EventLogger());
    this.player = player;
  }

  private void releasePlayer() {
    Assertions.checkNotNull(playerView).setPlayer(null);
    Assertions.checkNotNull(videoProcessingGLSurfaceView).setPlayer(null);
    if (player != null) {
      player.release();
      player = null;
    }
  }
}
