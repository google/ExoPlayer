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
package com.google.android.exoplayer.demo;

import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.DefaultTrackSelectionPolicy;
import com.google.android.exoplayer.DefaultTrackSelector;
import com.google.android.exoplayer.DefaultTrackSelector.TrackInfo;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.ExoPlayerFactory;
import com.google.android.exoplayer.MediaCodecTrackRenderer.DecoderInitializationException;
import com.google.android.exoplayer.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer.SimpleExoPlayer;
import com.google.android.exoplayer.TrackGroupArray;
import com.google.android.exoplayer.drm.DrmSessionManager;
import com.google.android.exoplayer.drm.StreamingDrmSessionManager;
import com.google.android.exoplayer.drm.UnsupportedDrmException;
import com.google.android.exoplayer.metadata.id3.GeobFrame;
import com.google.android.exoplayer.metadata.id3.Id3Frame;
import com.google.android.exoplayer.metadata.id3.PrivFrame;
import com.google.android.exoplayer.metadata.id3.TxxxFrame;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleLayout;
import com.google.android.exoplayer.upstream.DataSourceFactory;
import com.google.android.exoplayer.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer.util.DebugTextViewHelper;
import com.google.android.exoplayer.util.PlayerControl;
import com.google.android.exoplayer.util.Util;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.accessibility.CaptioningManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.List;
import java.util.UUID;

/**
 * An activity that plays media using {@link SimpleExoPlayer}.
 */
public class PlayerActivity extends Activity implements SurfaceHolder.Callback, OnClickListener,
    ExoPlayer.EventListener, SimpleExoPlayer.VideoListener, SimpleExoPlayer.CaptionListener,
    SimpleExoPlayer.Id3MetadataListener, DefaultTrackSelector.EventListener {

  // For use within demo app code.
  public static final String CONTENT_TYPE_EXTRA = "content_type";
  public static final String DRM_SCHEME_UUID_EXTRA = "drm_scheme_uuid";
  public static final String DRM_CONTENT_ID_EXTRA = "drm_content_id";
  public static final String DRM_PROVIDER_EXTRA = "drm_provider";
  public static final String PREFER_EXTENSION_DECODERS = "prefer_extension_decoders";

  // For use when launching the demo app using adb.
  public static final String ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW";
  private static final String ACTION_VIEW_LIST =
      "com.google.android.exoplayer.demo.action.VIEW_LIST";
  private static final String CONTENT_EXT_EXTRA = "type";
  private static final String URIS_LIST_EXTRA = "uris";

  private static final String TAG = "PlayerActivity";

  private static final CookieManager defaultCookieManager;

  static {
    defaultCookieManager = new CookieManager();
    defaultCookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  private Handler mainHandler;
  private EventLogger eventLogger;
  private MediaController mediaController;
  private View rootView;
  private LinearLayout debugRootView;
  private View shutterView;
  private AspectRatioFrameLayout videoFrame;
  private SurfaceView surfaceView;
  private TextView debugTextView;
  private SubtitleLayout subtitleLayout;
  private Button retryButton;

  private DataSourceFactory dataSourceFactory;
  private SimpleExoPlayer player;
  private DefaultTrackSelector trackSelector;
  private TrackSelectionHelper trackSelectionHelper;
  private DebugTextViewHelper debugViewHelper;
  private boolean playerNeedsSource;

  private long playerPosition;

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    String userAgent = Util.getUserAgent(this, "ExoPlayerDemo");
    dataSourceFactory = new DefaultDataSourceFactory(this, userAgent);
    mainHandler = new Handler();

    setContentView(R.layout.player_activity);
    rootView = findViewById(R.id.root);
    rootView.setOnTouchListener(new OnTouchListener() {
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
    rootView.setOnKeyListener(new OnKeyListener() {
      @Override
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        return keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_ESCAPE
            && keyCode != KeyEvent.KEYCODE_MENU && mediaController.dispatchKeyEvent(event);
      }
    });

    shutterView = findViewById(R.id.shutter);
    debugRootView = (LinearLayout) findViewById(R.id.controls_root);

    videoFrame = (AspectRatioFrameLayout) findViewById(R.id.video_frame);
    surfaceView = (SurfaceView) findViewById(R.id.surface_view);
    surfaceView.getHolder().addCallback(this);
    debugTextView = (TextView) findViewById(R.id.debug_text_view);
    subtitleLayout = (SubtitleLayout) findViewById(R.id.subtitles);
    mediaController = new KeyCompatibleMediaController(this);
    retryButton = (Button) findViewById(R.id.retry_button);
    retryButton.setOnClickListener(this);

    CookieHandler currentHandler = CookieHandler.getDefault();
    if (currentHandler != defaultCookieManager) {
      CookieHandler.setDefault(defaultCookieManager);
    }

    configureSubtitleView();
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
    if (Util.SDK_INT > 23) {
      initializePlayer();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if ((Util.SDK_INT <= 23 || player == null)) {
      initializePlayer();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      releasePlayer();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      releasePlayer();
    }
  }

  // OnClickListener methods

  @Override
  public void onClick(View view) {
    if (view == retryButton) {
      initializePlayer();
    } else if (view.getParent() == debugRootView) {
      trackSelectionHelper.showSelectionDialog(this, ((Button) view).getText(),
          trackSelector.getTrackInfo(), (int) view.getTag());
    }
  }

  // Permission request listener method

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions,
      int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      initializePlayer();
    } else {
      showToast(R.string.storage_permission_denied);
      finish();
    }
  }

  // Internal methods

  private void initializePlayer() {
    Intent intent = getIntent();
    if (player == null) {
      boolean preferExtensionDecoders = intent.getBooleanExtra(PREFER_EXTENSION_DECODERS, false);
      UUID drmSchemeUuid = (UUID) intent.getSerializableExtra(DRM_SCHEME_UUID_EXTRA);
      DrmSessionManager drmSessionManager = null;
      if (drmSchemeUuid != null) {
        String drmContentId = intent.getStringExtra(DRM_CONTENT_ID_EXTRA);
        String drmProvider = intent.getStringExtra(DRM_PROVIDER_EXTRA);
        try {
          drmSessionManager = buildDrmSessionManager(drmSchemeUuid, drmContentId, drmProvider);
        } catch (UnsupportedDrmException e) {
          onUnsupportedDrmError(e);
          return;
        }
      }
      eventLogger = new EventLogger();
      eventLogger.startSession();
      trackSelector = new DefaultTrackSelector(new DefaultTrackSelectionPolicy(), mainHandler);
      trackSelector.addListener(this);
      trackSelector.addListener(eventLogger);
      trackSelectionHelper = new TrackSelectionHelper(trackSelector);
      player = ExoPlayerFactory.newSimpleInstance(this, trackSelector, drmSessionManager,
          preferExtensionDecoders);
      player.addListener(this);
      player.addListener(eventLogger);
      player.setDebugListener(eventLogger);
      player.setVideoListener(this);
      player.setCaptionListener(this);
      player.setMetadataListener(this);
      player.seekTo(playerPosition);
      player.setSurface(surfaceView.getHolder().getSurface());
      player.setPlayWhenReady(true);
      mediaController.setMediaPlayer(new PlayerControl(player));
      mediaController.setAnchorView(rootView);
      debugViewHelper = new DebugTextViewHelper(player, debugTextView);
      debugViewHelper.start();
      playerNeedsSource = true;
    }
    if (playerNeedsSource) {
      String action = intent.getAction();
      Uri[] uris;
      UriSampleSourceProvider sourceProvider;
      if (ACTION_VIEW.equals(action)) {
        uris = new Uri[] {intent.getData()};
        sourceProvider = new UriSampleSourceProvider(player, dataSourceFactory, intent.getData(),
            intent.getIntExtra(CONTENT_TYPE_EXTRA, UriSampleSourceProvider.UNKNOWN_TYPE),
            intent.getStringExtra(CONTENT_EXT_EXTRA), mainHandler, eventLogger);
      } else if (ACTION_VIEW_LIST.equals(action)) {
        String[] uriStrings = intent.getStringArrayExtra(URIS_LIST_EXTRA);
        uris = new Uri[uriStrings.length];
        for (int i = 0; i < uriStrings.length; i++) {
          uris[i] = Uri.parse(uriStrings[i]);
        }
        sourceProvider = new UriSampleSourceProvider(player, dataSourceFactory, uris, mainHandler,
            eventLogger);
      } else {
        Log.w(TAG, "Unexpected intent action: " + action);
        return;
      }
      if (maybeRequestPermission(uris)) {
        // The player will be reinitialized if permission is granted.
        return;
      }
      player.setSourceProvider(sourceProvider);
      playerNeedsSource = false;
      updateButtonVisibilities();
    }
  }

  private DrmSessionManager buildDrmSessionManager(UUID uuid, String id, String provider)
      throws UnsupportedDrmException {
    if (Util.SDK_INT < 18) {
      return null;
    }
    if (C.PLAYREADY_UUID.equals(uuid)) {
      return StreamingDrmSessionManager.newPlayReadyInstance(
          TestMediaDrmCallback.newPlayReadyInstance(), null, mainHandler, eventLogger);
    } else if (C.WIDEVINE_UUID.equals(uuid)) {
      return StreamingDrmSessionManager.newWidevineInstance(
          TestMediaDrmCallback.newWidevineInstance(id, provider), null, mainHandler, eventLogger);
    } else {
      throw new UnsupportedDrmException(UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME);
    }
  }

  private void onUnsupportedDrmError(UnsupportedDrmException e) {
    int errorStringId = Util.SDK_INT < 18 ? R.string.error_drm_not_supported
        : e.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
        ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown;
    showToast(errorStringId);
  }

  /**
   * Checks whether it is necessary to ask for permission to read storage. If necessary, it also
   * requests permission.
   *
   * @param uris URIs that may require {@link permission#READ_EXTERNAL_STORAGE} to play.
   * @return True if a permission request is made. False if it is not necessary.
   */
  @TargetApi(23)
  private boolean maybeRequestPermission(Uri... uris) {
    if (Util.SDK_INT >= 23) {
      for (Uri uri : uris) {
        if (Util.isLocalFileUri(uri)) {
          if (checkSelfPermission(permission.READ_EXTERNAL_STORAGE)
              != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {permission.READ_EXTERNAL_STORAGE}, 0);
            return true;
          }
          break;
        }
      }
    }
    return false;
  }

  private void releasePlayer() {
    if (player != null) {
      shutterView.setVisibility(View.VISIBLE);
      debugViewHelper.stop();
      debugViewHelper = null;
      playerPosition = player.getCurrentPosition();
      player.release();
      player = null;
      trackSelector = null;
      trackSelectionHelper = null;
      eventLogger.endSession();
      eventLogger = null;
    }
  }

  // ExoPlayer.EventListener implementation

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
    playerNeedsSource = true;
    updateButtonVisibilities();
    showControls();
  }

  // SimpleExoPlayer.VideoListener implementation

  @Override
  public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
      float pixelWidthAspectRatio) {
    videoFrame.setAspectRatio(
        height == 0 ? 1 : (width * pixelWidthAspectRatio) / height);
  }

  @Override
  public void onDrawnToSurface(Surface surface) {
    shutterView.setVisibility(View.GONE);
  }

  // DefaultTrackSelector.EventListener implementation

  @Override
  public void onTracksChanged(TrackInfo trackInfo) {
    updateButtonVisibilities();
    // Print toasts if there exist only unplayable video or audio tracks.
    int videoRendererSupport = TrackInfo.RENDERER_SUPPORT_NO_TRACKS;
    int audioRendererSupport = TrackInfo.RENDERER_SUPPORT_NO_TRACKS;
    for (int i = 0; i < trackInfo.rendererCount; i++) {
      if (player.getRendererType(i) == C.TRACK_TYPE_VIDEO) {
        videoRendererSupport = Math.max(videoRendererSupport, trackInfo.getRendererSupport(i));
      } else if (player.getRendererType(i) == C.TRACK_TYPE_AUDIO) {
        audioRendererSupport = Math.max(audioRendererSupport, trackInfo.getRendererSupport(i));
      }
    }
    if (videoRendererSupport == TrackInfo.RENDERER_SUPPORT_UNPLAYABLE_TRACKS) {
      showToast(R.string.error_unsupported_video);
    }
    if (audioRendererSupport == TrackInfo.RENDERER_SUPPORT_UNPLAYABLE_TRACKS) {
      showToast(R.string.error_unsupported_audio);
    }
  }

  // User controls

  private void updateButtonVisibilities() {
    debugRootView.removeAllViews();

    retryButton.setVisibility(playerNeedsSource ? View.VISIBLE : View.GONE);
    debugRootView.addView(retryButton);

    if (player == null) {
      return;
    }

    TrackInfo trackInfo = trackSelector.getTrackInfo();
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
          case C.TRACK_TYPE_AUDIO: label = R.string.audio; break;
          case C.TRACK_TYPE_VIDEO: label = R.string.video; break;
          case C.TRACK_TYPE_TEXT: label = R.string.text; break;
          default: continue;
        }
        button.setText(label);
        button.setTag(i);
        button.setOnClickListener(this);
        debugRootView.addView(button);
      }
    }
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

  // SimpleExoPlayer.CaptionListener implementation

  @Override
  public void onCues(List<Cue> cues) {
    subtitleLayout.setCues(cues);
  }

  // SimpleExoPlayer.MetadataListener implementation

  @Override
  public void onId3Metadata(List<Id3Frame> id3Frames) {
    for (Id3Frame id3Frame : id3Frames) {
      if (id3Frame instanceof TxxxFrame) {
        TxxxFrame txxxFrame = (TxxxFrame) id3Frame;
        Log.i(TAG, String.format("ID3 TimedMetadata %s: description=%s, value=%s", txxxFrame.id,
            txxxFrame.description, txxxFrame.value));
      } else if (id3Frame instanceof PrivFrame) {
        PrivFrame privFrame = (PrivFrame) id3Frame;
        Log.i(TAG, String.format("ID3 TimedMetadata %s: owner=%s", privFrame.id, privFrame.owner));
      } else if (id3Frame instanceof GeobFrame) {
        GeobFrame geobFrame = (GeobFrame) id3Frame;
        Log.i(TAG, String.format("ID3 TimedMetadata %s: mimeType=%s, filename=%s, description=%s",
            geobFrame.id, geobFrame.mimeType, geobFrame.filename, geobFrame.description));
      } else {
        Log.i(TAG, String.format("ID3 TimedMetadata %s", id3Frame.id));
      }
    }
  }

  // SurfaceHolder.Callback implementation

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    if (player != null) {
      player.setSurface(holder.getSurface());
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    // Do nothing.
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    if (player != null) {
      player.setSurface(null);
    }
  }

  private void configureSubtitleView() {
    CaptionStyleCompat style;
    float fontScale;
    if (Util.SDK_INT >= 19) {
      style = getUserCaptionStyleV19();
      fontScale = getUserCaptionFontScaleV19();
    } else {
      style = CaptionStyleCompat.DEFAULT;
      fontScale = 1.0f;
    }
    subtitleLayout.setStyle(style);
    subtitleLayout.setFractionalTextSize(SubtitleLayout.DEFAULT_TEXT_SIZE_FRACTION * fontScale);
  }

  @TargetApi(19)
  private float getUserCaptionFontScaleV19() {
    CaptioningManager captioningManager =
        (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
    return captioningManager.getFontScale();
  }

  @TargetApi(19)
  private CaptionStyleCompat getUserCaptionStyleV19() {
    CaptioningManager captioningManager =
        (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
    return CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle());
  }

  private void showToast(int messageId) {
    showToast(getString(messageId));
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

  private static final class KeyCompatibleMediaController extends MediaController {

    private MediaController.MediaPlayerControl playerControl;

    public KeyCompatibleMediaController(Context context) {
      super(context);
    }

    @Override
    public void setMediaPlayer(MediaController.MediaPlayerControl playerControl) {
      super.setMediaPlayer(playerControl);
      this.playerControl = playerControl;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
      int keyCode = event.getKeyCode();
      if (playerControl.canSeekForward() && (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
          || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
          playerControl.seekTo(playerControl.getCurrentPosition() + 15000); // milliseconds
          show();
        }
        return true;
      } else if (playerControl.canSeekBackward() && (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
          || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
          playerControl.seekTo(playerControl.getCurrentPosition() - 5000); // milliseconds
          show();
        }
        return true;
      }
      return super.dispatchKeyEvent(event);
    }
  }

}
