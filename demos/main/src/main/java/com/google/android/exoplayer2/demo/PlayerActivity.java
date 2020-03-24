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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaDrm;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.demo.Sample.UriSample;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdsLoader;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.RandomTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.DebugTextViewHelper;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.spherical.SphericalGLSurfaceView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;
import java.lang.reflect.Constructor;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** An activity that plays media using {@link SimpleExoPlayer}. */
public class PlayerActivity extends AppCompatActivity
    implements OnClickListener, PlaybackPreparer, PlayerControlView.VisibilityListener {

  // Activity extras.

  public static final String SPHERICAL_STEREO_MODE_EXTRA = "spherical_stereo_mode";
  public static final String SPHERICAL_STEREO_MODE_MONO = "mono";
  public static final String SPHERICAL_STEREO_MODE_TOP_BOTTOM = "top_bottom";
  public static final String SPHERICAL_STEREO_MODE_LEFT_RIGHT = "left_right";

  // Actions.

  public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
  public static final String ACTION_VIEW_LIST =
      "com.google.android.exoplayer.demo.action.VIEW_LIST";

  // Player configuration extras.

  public static final String ABR_ALGORITHM_EXTRA = "abr_algorithm";
  public static final String ABR_ALGORITHM_DEFAULT = "default";
  public static final String ABR_ALGORITHM_RANDOM = "random";

  // Media item configuration extras.

  public static final String URI_EXTRA = "uri";
  public static final String EXTENSION_EXTRA = "extension";
  public static final String IS_LIVE_EXTRA = "is_live";

  public static final String DRM_SCHEME_EXTRA = "drm_scheme";
  public static final String DRM_LICENSE_URL_EXTRA = "drm_license_url";
  public static final String DRM_KEY_REQUEST_PROPERTIES_EXTRA = "drm_key_request_properties";
  public static final String DRM_SESSION_FOR_CLEAR_TYPES_EXTRA = "drm_session_for_clear_types";
  public static final String DRM_MULTI_SESSION_EXTRA = "drm_multi_session";
  public static final String PREFER_EXTENSION_DECODERS_EXTRA = "prefer_extension_decoders";
  public static final String TUNNELING_EXTRA = "tunneling";
  public static final String AD_TAG_URI_EXTRA = "ad_tag_uri";
  public static final String SUBTITLE_URI_EXTRA = "subtitle_uri";
  public static final String SUBTITLE_MIME_TYPE_EXTRA = "subtitle_mime_type";
  public static final String SUBTITLE_LANGUAGE_EXTRA = "subtitle_language";
  // For backwards compatibility only.
  public static final String DRM_SCHEME_UUID_EXTRA = "drm_scheme_uuid";

  // Saved instance state keys.

  private static final String KEY_TRACK_SELECTOR_PARAMETERS = "track_selector_parameters";
  private static final String KEY_WINDOW = "window";
  private static final String KEY_POSITION = "position";
  private static final String KEY_AUTO_PLAY = "auto_play";

  private static final CookieManager DEFAULT_COOKIE_MANAGER;

  static {
    DEFAULT_COOKIE_MANAGER = new CookieManager();
    DEFAULT_COOKIE_MANAGER.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  private PlayerView playerView;
  private LinearLayout debugRootView;
  private Button selectTracksButton;
  private TextView debugTextView;
  private boolean isShowingTrackSelectionDialog;

  private DataSource.Factory dataSourceFactory;
  private SimpleExoPlayer player;
  private List<MediaSource> mediaSources;
  private DefaultTrackSelector trackSelector;
  private DefaultTrackSelector.Parameters trackSelectorParameters;
  private DebugTextViewHelper debugViewHelper;
  private TrackGroupArray lastSeenTrackGroupArray;
  private DefaultMediaSourceFactory mediaSourceFactory;
  private boolean startAutoPlay;
  private int startWindow;
  private long startPosition;

  // Fields used only for ad playback. The ads loader is loaded via reflection.

  private AdsLoader adsLoader;
  private Uri loadedAdTagUri;

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Intent intent = getIntent();
    String sphericalStereoMode = intent.getStringExtra(SPHERICAL_STEREO_MODE_EXTRA);
    if (sphericalStereoMode != null) {
      setTheme(R.style.PlayerTheme_Spherical);
    }
    super.onCreate(savedInstanceState);
    dataSourceFactory = buildDataSourceFactory();
    mediaSourceFactory =
        DefaultMediaSourceFactory.newInstance(/* context= */ this, dataSourceFactory);
    if (CookieHandler.getDefault() != DEFAULT_COOKIE_MANAGER) {
      CookieHandler.setDefault(DEFAULT_COOKIE_MANAGER);
    }

    setContentView(R.layout.player_activity);
    debugRootView = findViewById(R.id.controls_root);
    debugTextView = findViewById(R.id.debug_text_view);
    selectTracksButton = findViewById(R.id.select_tracks_button);
    selectTracksButton.setOnClickListener(this);

    playerView = findViewById(R.id.player_view);
    playerView.setControllerVisibilityListener(this);
    playerView.setErrorMessageProvider(new PlayerErrorMessageProvider());
    playerView.requestFocus();
    if (sphericalStereoMode != null) {
      int stereoMode;
      if (SPHERICAL_STEREO_MODE_MONO.equals(sphericalStereoMode)) {
        stereoMode = C.STEREO_MODE_MONO;
      } else if (SPHERICAL_STEREO_MODE_TOP_BOTTOM.equals(sphericalStereoMode)) {
        stereoMode = C.STEREO_MODE_TOP_BOTTOM;
      } else if (SPHERICAL_STEREO_MODE_LEFT_RIGHT.equals(sphericalStereoMode)) {
        stereoMode = C.STEREO_MODE_LEFT_RIGHT;
      } else {
        showToast(R.string.error_unrecognized_stereo_mode);
        finish();
        return;
      }
      ((SphericalGLSurfaceView) playerView.getVideoSurfaceView()).setDefaultStereoMode(stereoMode);
    }

    if (savedInstanceState != null) {
      trackSelectorParameters = savedInstanceState.getParcelable(KEY_TRACK_SELECTOR_PARAMETERS);
      startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY);
      startWindow = savedInstanceState.getInt(KEY_WINDOW);
      startPosition = savedInstanceState.getLong(KEY_POSITION);
    } else {
      DefaultTrackSelector.ParametersBuilder builder =
          new DefaultTrackSelector.ParametersBuilder(/* context= */ this);
      boolean tunneling = intent.getBooleanExtra(TUNNELING_EXTRA, false);
      if (Util.SDK_INT >= 21 && tunneling) {
        builder.setTunnelingAudioSessionId(C.generateAudioSessionIdV21(/* context= */ this));
      }
      trackSelectorParameters = builder.build();
      clearStartPosition();
    }
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    releasePlayer();
    releaseAdsLoader();
    clearStartPosition();
    setIntent(intent);
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

  @Override
  public void onDestroy() {
    super.onDestroy();
    releaseAdsLoader();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (grantResults.length == 0) {
      // Empty results are triggered if a permission is requested while another request was already
      // pending and can be safely ignored in this case.
      return;
    }
    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      initializePlayer();
    } else {
      showToast(R.string.storage_permission_denied);
      finish();
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    updateTrackSelectorParameters();
    updateStartPosition();
    outState.putParcelable(KEY_TRACK_SELECTOR_PARAMETERS, trackSelectorParameters);
    outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay);
    outState.putInt(KEY_WINDOW, startWindow);
    outState.putLong(KEY_POSITION, startPosition);
  }

  // Activity input

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // See whether the player view wants to handle media or DPAD keys events.
    return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
  }

  // OnClickListener methods

  @Override
  public void onClick(View view) {
    if (view == selectTracksButton
        && !isShowingTrackSelectionDialog
        && TrackSelectionDialog.willHaveContent(trackSelector)) {
      isShowingTrackSelectionDialog = true;
      TrackSelectionDialog trackSelectionDialog =
          TrackSelectionDialog.createForTrackSelector(
              trackSelector,
              /* onDismissListener= */ dismissedDialog -> isShowingTrackSelectionDialog = false);
      trackSelectionDialog.show(getSupportFragmentManager(), /* tag= */ null);
    }
  }

  // PlaybackControlView.PlaybackPreparer implementation

  @Override
  public void preparePlayback() {
    player.retry();
  }

  // PlaybackControlView.VisibilityListener implementation

  @Override
  public void onVisibilityChange(int visibility) {
    debugRootView.setVisibility(visibility);
  }

  // Internal methods

  private void initializePlayer() {
    if (player == null) {
      Intent intent = getIntent();
      mediaSources = createTopLevelMediaSources(intent);
      if (mediaSources.isEmpty()) {
        return;
      }
      TrackSelection.Factory trackSelectionFactory;
      String abrAlgorithm = intent.getStringExtra(ABR_ALGORITHM_EXTRA);
      if (abrAlgorithm == null || ABR_ALGORITHM_DEFAULT.equals(abrAlgorithm)) {
        trackSelectionFactory = new AdaptiveTrackSelection.Factory();
      } else if (ABR_ALGORITHM_RANDOM.equals(abrAlgorithm)) {
        trackSelectionFactory = new RandomTrackSelection.Factory();
      } else {
        showToast(R.string.error_unrecognized_abr_algorithm);
        finish();
        return;
      }

      boolean preferExtensionDecoders =
          intent.getBooleanExtra(PREFER_EXTENSION_DECODERS_EXTRA, false);
      RenderersFactory renderersFactory =
          ((DemoApplication) getApplication()).buildRenderersFactory(preferExtensionDecoders);

      trackSelector = new DefaultTrackSelector(/* context= */ this, trackSelectionFactory);
      trackSelector.setParameters(trackSelectorParameters);
      lastSeenTrackGroupArray = null;

      player =
          new SimpleExoPlayer.Builder(/* context= */ this, renderersFactory)
              .setTrackSelector(trackSelector)
              .build();
      player.addListener(new PlayerEventListener());
      player.setPlayWhenReady(startAutoPlay);
      player.addAnalyticsListener(new EventLogger(trackSelector));
      playerView.setPlayer(player);
      playerView.setPlaybackPreparer(this);
      debugViewHelper = new DebugTextViewHelper(player, debugTextView);
      debugViewHelper.start();
      if (adsLoader != null) {
        adsLoader.setPlayer(player);
      }
    }
    boolean haveStartPosition = startWindow != C.INDEX_UNSET;
    if (haveStartPosition) {
      player.seekTo(startWindow, startPosition);
    }
    player.setMediaSources(mediaSources, /* resetPosition= */ !haveStartPosition);
    player.prepare();
    updateButtonVisibility();
  }

  private List<MediaSource> createTopLevelMediaSources(Intent intent) {
    String action = intent.getAction();
    boolean actionIsListView = ACTION_VIEW_LIST.equals(action);
    if (!actionIsListView && !ACTION_VIEW.equals(action)) {
      showToast(getString(R.string.unexpected_intent_action, action));
      finish();
      return Collections.emptyList();
    }

    Sample intentAsSample = Sample.createFromIntent(intent);
    UriSample[] samples =
        intentAsSample instanceof Sample.PlaylistSample
            ? ((Sample.PlaylistSample) intentAsSample).children
            : new UriSample[] {(UriSample) intentAsSample};

    boolean seenAdsTagUri = false;
    for (UriSample sample : samples) {
      seenAdsTagUri |= sample.adTagUri != null;
      if (!Util.checkCleartextTrafficPermitted(sample.uri)) {
        showToast(R.string.error_cleartext_not_permitted);
        return Collections.emptyList();
      }
      if (Util.maybeRequestReadExternalStoragePermission(/* activity= */ this, sample.uri)) {
        // The player will be reinitialized if the permission is granted.
        return Collections.emptyList();
      }
    }

    List<MediaSource> mediaSources = new ArrayList<>();
    for (UriSample sample : samples) {
      MediaSource mediaSource = createLeafMediaSource(sample);
      if (mediaSource == null) {
        continue;
      }
      Sample.SubtitleInfo subtitleInfo = sample.subtitleInfo;
      if (subtitleInfo != null) {
        if (Util.maybeRequestReadExternalStoragePermission(
            /* activity= */ this, subtitleInfo.uri)) {
          // The player will be reinitialized if the permission is granted.
          return Collections.emptyList();
        }
        Format subtitleFormat =
            new Format.Builder()
                .setSampleMimeType(subtitleInfo.mimeType)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .setLanguage(subtitleInfo.language)
                .build();
        MediaSource subtitleMediaSource =
            new SingleSampleMediaSource.Factory(dataSourceFactory)
                .createMediaSource(subtitleInfo.uri, subtitleFormat, C.TIME_UNSET);
        mediaSource = new MergingMediaSource(mediaSource, subtitleMediaSource);
      }
      mediaSources.add(mediaSource);
    }
    if (seenAdsTagUri && mediaSources.size() == 1) {
      Uri adTagUri = samples[0].adTagUri;
      if (!adTagUri.equals(loadedAdTagUri)) {
        releaseAdsLoader();
        loadedAdTagUri = adTagUri;
      }
      MediaSource adsMediaSource = createAdsMediaSource(mediaSources.get(0), adTagUri);
      if (adsMediaSource != null) {
        mediaSources.set(0, adsMediaSource);
      } else {
        showToast(R.string.ima_not_loaded);
      }
    } else if (seenAdsTagUri && mediaSources.size() > 1) {
      showToast(R.string.unsupported_ads_in_concatenation);
      releaseAdsLoader();
    } else {
      releaseAdsLoader();
    }

    return mediaSources;
  }

  @Nullable
  private MediaSource createLeafMediaSource(UriSample parameters) {
    MediaItem.Builder builder = new MediaItem.Builder().setSourceUri(parameters.uri);
    builder.setMimeType(Sample.inferAdaptiveStreamMimeType(parameters.uri, parameters.extension));
    int[] drmSessionForClearTypes = new int[0];
    HttpDataSource.Factory drmDataSourceFactory = null;
    if (parameters.drmInfo != null) {
      if (Util.SDK_INT < 18) {
        showToast(R.string.error_drm_unsupported_before_api_18);
        finish();
        return null;
      } else if (!MediaDrm.isCryptoSchemeSupported(parameters.drmInfo.drmScheme)) {
        showToast(R.string.error_drm_unsupported_scheme);
        finish();
        return null;
      }
      builder
          .setDrmLicenseUri(parameters.drmInfo.drmLicenseUrl)
          .setDrmLicenseRequestHeaders(
              createLicenseHeaders(parameters.drmInfo.drmKeyRequestProperties))
          .setDrmUuid(parameters.drmInfo.drmScheme)
          .setDrmMultiSession(parameters.drmInfo.drmMultiSession);
      drmSessionForClearTypes = parameters.drmInfo.drmSessionForClearTypes;
      drmDataSourceFactory = ((DemoApplication) getApplication()).buildHttpDataSourceFactory();
    }

    DownloadRequest downloadRequest =
        ((DemoApplication) getApplication())
            .getDownloadTracker()
            .getDownloadRequest(parameters.uri);
    if (downloadRequest != null) {
      return DownloadHelper.createMediaSource(downloadRequest, dataSourceFactory);
    }
    return mediaSourceFactory
        .setDrmHttpDataSourceFactory(drmDataSourceFactory)
        .setUseDrmSessionForClearContent(drmSessionForClearTypes)
        .createMediaSource(builder.build());
  }

  @Nullable
  private Map<String, String> createLicenseHeaders(@Nullable String[] drmKeyRequestProperties) {
    if (drmKeyRequestProperties == null || drmKeyRequestProperties.length == 0) {
      return null;
    }
    Map<String, String> headers = new HashMap<>();
    for (int i = 0; i < drmKeyRequestProperties.length; i += 2) {
      headers.put(drmKeyRequestProperties[i], drmKeyRequestProperties[i + 1]);
    }
    return headers;
  }

  private void releasePlayer() {
    if (player != null) {
      updateTrackSelectorParameters();
      updateStartPosition();
      debugViewHelper.stop();
      debugViewHelper = null;
      player.release();
      player = null;
      mediaSources = null;
      trackSelector = null;
    }
    if (adsLoader != null) {
      adsLoader.setPlayer(null);
    }
  }

  private void releaseAdsLoader() {
    if (adsLoader != null) {
      adsLoader.release();
      adsLoader = null;
      loadedAdTagUri = null;
      playerView.getOverlayFrameLayout().removeAllViews();
    }
  }

  private void updateTrackSelectorParameters() {
    if (trackSelector != null) {
      trackSelectorParameters = trackSelector.getParameters();
    }
  }

  private void updateStartPosition() {
    if (player != null) {
      startAutoPlay = player.getPlayWhenReady();
      startWindow = player.getCurrentWindowIndex();
      startPosition = Math.max(0, player.getContentPosition());
    }
  }

  private void clearStartPosition() {
    startAutoPlay = true;
    startWindow = C.INDEX_UNSET;
    startPosition = C.TIME_UNSET;
  }

  /** Returns a new DataSource factory. */
  private DataSource.Factory buildDataSourceFactory() {
    return ((DemoApplication) getApplication()).buildDataSourceFactory();
  }

  /** Returns an ads media source, reusing the ads loader if one exists. */
  @Nullable
  private MediaSource createAdsMediaSource(MediaSource mediaSource, Uri adTagUri) {
    // Load the extension source using reflection so the demo app doesn't have to depend on it.
    // The ads loader is reused for multiple playbacks, so that ad playback can resume.
    try {
      Class<?> loaderClass = Class.forName("com.google.android.exoplayer2.ext.ima.ImaAdsLoader");
      if (adsLoader == null) {
        // Full class names used so the lint rule triggers should any of the classes move.
        // LINT.IfChange
        Constructor<? extends AdsLoader> loaderConstructor =
            loaderClass
                .asSubclass(AdsLoader.class)
                .getConstructor(android.content.Context.class, android.net.Uri.class);
        // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
        adsLoader = loaderConstructor.newInstance(this, adTagUri);
      }
      return new AdsMediaSource(mediaSource, mediaSourceFactory, adsLoader, playerView);
    } catch (ClassNotFoundException e) {
      // IMA extension not loaded.
      return null;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // User controls

  private void updateButtonVisibility() {
    selectTracksButton.setEnabled(
        player != null && TrackSelectionDialog.willHaveContent(trackSelector));
  }

  private void showControls() {
    debugRootView.setVisibility(View.VISIBLE);
  }

  private void showToast(int messageId) {
    showToast(getString(messageId));
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

  private static boolean isBehindLiveWindow(ExoPlaybackException e) {
    if (e.type != ExoPlaybackException.TYPE_SOURCE) {
      return false;
    }
    Throwable cause = e.getSourceException();
    while (cause != null) {
      if (cause instanceof BehindLiveWindowException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  private class PlayerEventListener implements Player.EventListener {

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
      if (playbackState == Player.STATE_ENDED) {
        showControls();
      }
      updateButtonVisibility();
    }

    @Override
    public void onPlayerError(@NonNull ExoPlaybackException e) {
      if (isBehindLiveWindow(e)) {
        clearStartPosition();
        initializePlayer();
      } else {
        updateButtonVisibility();
        showControls();
      }
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public void onTracksChanged(
        @NonNull TrackGroupArray trackGroups, @NonNull TrackSelectionArray trackSelections) {
      updateButtonVisibility();
      if (trackGroups != lastSeenTrackGroupArray) {
        MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
          if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO)
              == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
            showToast(R.string.error_unsupported_video);
          }
          if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO)
              == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
            showToast(R.string.error_unsupported_audio);
          }
        }
        lastSeenTrackGroupArray = trackGroups;
      }
    }
  }

  private class PlayerErrorMessageProvider implements ErrorMessageProvider<ExoPlaybackException> {

    @Override
    @NonNull
    public Pair<Integer, String> getErrorMessage(@NonNull ExoPlaybackException e) {
      String errorString = getString(R.string.error_generic);
      if (e.type == ExoPlaybackException.TYPE_RENDERER) {
        Exception cause = e.getRendererException();
        if (cause instanceof DecoderInitializationException) {
          // Special case for decoder initialization failures.
          DecoderInitializationException decoderInitializationException =
              (DecoderInitializationException) cause;
          if (decoderInitializationException.codecInfo == null) {
            if (decoderInitializationException.getCause() instanceof DecoderQueryException) {
              errorString = getString(R.string.error_querying_decoders);
            } else if (decoderInitializationException.secureDecoderRequired) {
              errorString =
                  getString(
                      R.string.error_no_secure_decoder, decoderInitializationException.mimeType);
            } else {
              errorString =
                  getString(R.string.error_no_decoder, decoderInitializationException.mimeType);
            }
          } else {
            errorString =
                getString(
                    R.string.error_instantiating_decoder,
                    decoderInitializationException.codecInfo.name);
          }
        }
      }
      return Pair.create(0, errorString);
    }
  }
}
