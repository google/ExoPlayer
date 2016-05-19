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
import com.google.android.exoplayer.DefaultTrackSelector.TrackInfo;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecTrackRenderer.DecoderInitializationException;
import com.google.android.exoplayer.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackGroupArray;
import com.google.android.exoplayer.dash.DashSampleSource;
import com.google.android.exoplayer.demo.player.DemoPlayer;
import com.google.android.exoplayer.demo.ui.TrackSelectionHelper;
import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.drm.UnsupportedDrmException;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.metadata.id3.GeobFrame;
import com.google.android.exoplayer.metadata.id3.Id3Frame;
import com.google.android.exoplayer.metadata.id3.PrivFrame;
import com.google.android.exoplayer.metadata.id3.TxxxFrame;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingSampleSource;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleLayout;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSourceFactory;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer.util.DebugTextViewHelper;
import com.google.android.exoplayer.util.Util;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
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

/**
 * An activity that plays media using {@link DemoPlayer}.
 */
public class PlayerActivity extends Activity implements SurfaceHolder.Callback, OnClickListener,
    DemoPlayer.Listener, DemoPlayer.CaptionListener, DemoPlayer.Id3MetadataListener {

  // For use within demo app code.
  public static final String CONTENT_ID_EXTRA = "content_id";
  public static final String CONTENT_TYPE_EXTRA = "content_type";
  public static final String PROVIDER_EXTRA = "provider";
  public static final String USE_EXTENSION_DECODERS = "use_extension_decoders";

  // For use when launching the demo app using adb.
  private static final String CONTENT_EXT_EXTRA = "type";

  private static final String TAG = "PlayerActivity";

  private static final CookieManager defaultCookieManager;

  static {
    defaultCookieManager = new CookieManager();
    defaultCookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  }

  private EventLogger eventLogger;
  private MediaController mediaController;
  private LinearLayout debugRootView;
  private View shutterView;
  private AspectRatioFrameLayout videoFrame;
  private SurfaceView surfaceView;
  private TextView debugTextView;
  private TextView playerStateTextView;
  private SubtitleLayout subtitleLayout;
  private Button retryButton;

  private DataSourceFactory dataSourceFactory;
  private DemoPlayer player;
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

    setContentView(R.layout.player_activity);
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
    root.setOnKeyListener(new OnKeyListener() {
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

    playerStateTextView = (TextView) findViewById(R.id.player_state_view);
    subtitleLayout = (SubtitleLayout) findViewById(R.id.subtitles);

    mediaController = new KeyCompatibleMediaController(this);
    mediaController.setAnchorView(root);
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
          player.getTrackInfo(), (int) view.getTag());
    }
  }

  // Permission request listener method

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions,
      int[] grantResults) {
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      initializePlayer();
    } else {
      Toast.makeText(getApplicationContext(), R.string.storage_permission_denied,
          Toast.LENGTH_LONG).show();
      finish();
    }
  }

  // Permission management methods

  /**
   * Checks whether it is necessary to ask for permission to read storage. If necessary, it also
   * requests permission.
   *
   * @param uri A URI that may require {@link permission#READ_EXTERNAL_STORAGE} to play.
   * @return True if a permission request is made. False if it is not necessary.
   */
  @TargetApi(23)
  private boolean maybeRequestPermission(Uri uri) {
    if (requiresPermission(uri)) {
      requestPermissions(new String[] {permission.READ_EXTERNAL_STORAGE}, 0);
      return true;
    } else {
      return false;
    }
  }

  @TargetApi(23)
  private boolean requiresPermission(Uri uri) {
    return Util.SDK_INT >= 23
        && Util.isLocalFileUri(uri)
        && checkSelfPermission(permission.READ_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED;
  }

  // Internal methods

  private void initializePlayer() {
    Intent intent = getIntent();
    Uri uri = intent.getData();
    int type = intent.getIntExtra(CONTENT_TYPE_EXTRA,
        inferContentType(uri, intent.getStringExtra(CONTENT_EXT_EXTRA)));
    if (player == null) {
      String id = intent.getStringExtra(CONTENT_ID_EXTRA);
      String provider = intent.getStringExtra(PROVIDER_EXTRA);
      boolean useExtensionDecoders = intent.getBooleanExtra(USE_EXTENSION_DECODERS, false);
      player = new DemoPlayer(this, buildDrmCallback(type, id, provider), useExtensionDecoders);
      player.addListener(this);
      player.setCaptionListener(this);
      player.setMetadataListener(this);
      player.seekTo(playerPosition);
      trackSelectionHelper = new TrackSelectionHelper(player.getTrackSelector());
      playerNeedsSource = true;
      mediaController.setMediaPlayer(player.getPlayerControl());
      mediaController.setEnabled(true);
      eventLogger = new EventLogger();
      eventLogger.startSession();
      player.addListener(eventLogger);
      player.setInfoListener(eventLogger);
      player.setInternalErrorListener(eventLogger);
      player.setSurface(surfaceView.getHolder().getSurface());
      player.setPlayWhenReady(true);
      debugViewHelper = new DebugTextViewHelper(player, debugTextView);
      debugViewHelper.start();
    }
    if (playerNeedsSource) {
      if (maybeRequestPermission(uri)) {
        // The player will be reinitialized if permission is granted.
        return;
      }
      player.setSource(buildSource(type, uri));
      playerNeedsSource = false;
      updateButtonVisibilities();
    }
  }

  private MediaDrmCallback buildDrmCallback(int type, String id, String provider) {
    if (Util.SDK_INT < 18) {
      return null;
    }
    switch (type) {
      case Util.TYPE_SS:
        return new SmoothStreamingTestMediaDrmCallback();
      case Util.TYPE_DASH:
        return new WidevineTestMediaDrmCallback(id, provider);
      default:
        return null;
    }
  }

  private SampleSource buildSource(int type, Uri uri) {
    switch (type) {
      case Util.TYPE_SS:
        return new SmoothStreamingSampleSource(uri, dataSourceFactory, player.getBandwidthMeter(),
            player.getMainHandler(), player);
      case Util.TYPE_DASH:
        return new DashSampleSource(uri, dataSourceFactory, player.getBandwidthMeter(),
            player.getMainHandler(), player);
      case Util.TYPE_HLS:
        return new HlsSampleSource(uri, dataSourceFactory, player.getBandwidthMeter(),
            player.getMainHandler(), player);
      case Util.TYPE_OTHER:
        Allocator allocator = new DefaultAllocator(C.DEFAULT_BUFFER_SEGMENT_SIZE);
        DataSource dataSource = dataSourceFactory.createDataSource(player.getBandwidthMeter());
        return new ExtractorSampleSource(uri, dataSource, allocator, C.DEFAULT_MUXED_BUFFER_SIZE,
            player.getMainHandler(), player, 0, ExtractorSampleSource.newDefaultExtractors());
      default:
        throw new IllegalStateException("Unsupported type: " + type);
    }
  }

  private void releasePlayer() {
    if (player != null) {
      shutterView.setVisibility(View.VISIBLE);
      debugViewHelper.stop();
      debugViewHelper = null;
      playerPosition = player.getCurrentPosition();
      player.release();
      player = null;
      eventLogger.endSession();
      eventLogger = null;
    }
  }

  // DemoPlayer.Listener implementation

  @Override
  public void onTracksChanged(TrackInfo trackSet) {
    updateButtonVisibilities();
  }

  @Override
  public void onStateChanged(boolean playWhenReady, int playbackState) {
    if (playbackState == ExoPlayer.STATE_ENDED) {
      showControls();
    }
    String text = "playWhenReady=" + playWhenReady + ", playbackState=";
    switch(playbackState) {
      case ExoPlayer.STATE_BUFFERING:
        text += "buffering";
        break;
      case ExoPlayer.STATE_ENDED:
        text += "ended";
        break;
      case ExoPlayer.STATE_IDLE:
        text += "idle";
        break;
      case ExoPlayer.STATE_READY:
        text += "ready";
        break;
      default:
        text += "unknown";
        break;
    }
    playerStateTextView.setText(text);
    updateButtonVisibilities();
  }

  @Override
  public void onError(ExoPlaybackException e) {
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
      } else if (cause instanceof UnsupportedDrmException) {
        // Special case DRM failures.
        UnsupportedDrmException unsupportedDrmException = (UnsupportedDrmException) cause;
        errorString = getString(Util.SDK_INT < 18 ? R.string.error_drm_not_supported
            : unsupportedDrmException.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
            ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
      }
    }
    if (errorString != null) {
      Toast.makeText(getApplicationContext(), errorString, Toast.LENGTH_LONG).show();
    }
    playerNeedsSource = true;
    updateButtonVisibilities();
    showControls();
  }

  @Override
  public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
      float pixelWidthAspectRatio) {
    shutterView.setVisibility(View.GONE);
    videoFrame.setAspectRatio(
        height == 0 ? 1 : (width * pixelWidthAspectRatio) / height);
  }

  // User controls

  private void updateButtonVisibilities() {
    debugRootView.removeAllViews();

    retryButton.setVisibility(playerNeedsSource ? View.VISIBLE : View.GONE);
    debugRootView.addView(retryButton);

    TrackInfo trackInfo;
    if (player == null || (trackInfo = player.getTrackInfo()) == null) {
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

  // DemoPlayer.CaptionListener implementation

  @Override
  public void onCues(List<Cue> cues) {
    subtitleLayout.setCues(cues);
  }

  // DemoPlayer.MetadataListener implementation

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
      player.blockingClearSurface();
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

  /**
   * Makes a best guess to infer the type from a media {@link Uri} and an optional overriding file
   * extension.
   *
   * @param uri The {@link Uri} of the media.
   * @param fileExtension An overriding file extension.
   * @return The inferred type.
   */
  private static int inferContentType(Uri uri, String fileExtension) {
    String lastPathSegment = !TextUtils.isEmpty(fileExtension) ? "." + fileExtension
        : uri.getLastPathSegment();
    return Util.inferContentType(lastPathSegment);
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
