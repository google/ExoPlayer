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

import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.VideoSurfaceView;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer.demo.player.DashRendererBuilder;
import com.google.android.exoplayer.demo.player.DefaultRendererBuilder;
import com.google.android.exoplayer.demo.player.DemoPlayer;
import com.google.android.exoplayer.demo.player.DemoPlayer.RendererBuilder;
import com.google.android.exoplayer.demo.player.ExtractorRendererBuilder;
import com.google.android.exoplayer.demo.player.HlsRendererBuilder;
import com.google.android.exoplayer.demo.player.SmoothStreamingRendererBuilder;
import com.google.android.exoplayer.demo.player.UnsupportedDrmException;
import com.google.android.exoplayer.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer.metadata.GeobMetadata;
import com.google.android.exoplayer.metadata.PrivMetadata;
import com.google.android.exoplayer.metadata.TxxxMetadata;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.SubtitleView;
import com.google.android.exoplayer.util.Util;
import com.google.android.exoplayer.util.VerboseLogUtil;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.accessibility.CaptioningManager;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Map;

/**
 * An activity that plays media using {@link DemoPlayer}.
 */
public class PlayerActivity extends Activity implements SurfaceHolder.Callback, OnClickListener,
    DemoPlayer.Listener, DemoPlayer.TextListener, DemoPlayer.Id3MetadataListener,
    AudioCapabilitiesReceiver.Listener {

  public static final String CONTENT_TYPE_EXTRA = "content_type";
  public static final String CONTENT_ID_EXTRA = "content_id";

  private static final String TAG = "PlayerActivity";

  private static final float CAPTION_LINE_HEIGHT_RATIO = 0.0533f;
  private static final int MENU_GROUP_TRACKS = 1;
  private static final int ID_OFFSET = 2;

  private EventLogger eventLogger;
  private MediaController mediaController;
  private View debugRootView;
  private View shutterView;
  private VideoSurfaceView surfaceView;
  private TextView debugTextView;
  private TextView playerStateTextView;
  private SubtitleView subtitleView;
  private Button videoButton;
  private Button audioButton;
  private Button textButton;
  private Button retryButton;

  private DemoPlayer player;
  private boolean playerNeedsPrepare;

  private long playerPosition;
  private boolean enableBackgroundAudio;

  private Uri contentUri;
  private int contentType;
  private String contentId;

  private AudioCapabilitiesReceiver audioCapabilitiesReceiver;
  private AudioCapabilities audioCapabilities;

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Intent intent = getIntent();
    contentUri = intent.getData();
    contentType = intent.getIntExtra(CONTENT_TYPE_EXTRA, DemoUtil.TYPE_OTHER);
    contentId = intent.getStringExtra(CONTENT_ID_EXTRA);

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
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
          return mediaController.dispatchKeyEvent(event);
        }
        return false;
      }
    });
    audioCapabilitiesReceiver = new AudioCapabilitiesReceiver(getApplicationContext(), this);

    shutterView = findViewById(R.id.shutter);
    debugRootView = findViewById(R.id.controls_root);

    surfaceView = (VideoSurfaceView) findViewById(R.id.surface_view);
    surfaceView.getHolder().addCallback(this);
    debugTextView = (TextView) findViewById(R.id.debug_text_view);

    playerStateTextView = (TextView) findViewById(R.id.player_state_view);
    subtitleView = (SubtitleView) findViewById(R.id.subtitles);

    mediaController = new MediaController(this);
    mediaController.setAnchorView(root);
    retryButton = (Button) findViewById(R.id.retry_button);
    retryButton.setOnClickListener(this);
    videoButton = (Button) findViewById(R.id.video_controls);
    audioButton = (Button) findViewById(R.id.audio_controls);
    textButton = (Button) findViewById(R.id.text_controls);

    DemoUtil.setDefaultCookieManager();
  }

  @Override
  public void onResume() {
    super.onResume();
    configureSubtitleView();

    // The player will be prepared on receiving audio capabilities.
    audioCapabilitiesReceiver.register();
  }

  @Override
  public void onPause() {
    super.onPause();
    if (!enableBackgroundAudio) {
      releasePlayer();
    } else {
      player.setBackgrounded(true);
    }
    audioCapabilitiesReceiver.unregister();
    shutterView.setVisibility(View.VISIBLE);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    releasePlayer();
  }

  // OnClickListener methods

  @Override
  public void onClick(View view) {
    if (view == retryButton) {
      preparePlayer();
    }
  }

  // AudioCapabilitiesReceiver.Listener methods

  @Override
  public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
    boolean audioCapabilitiesChanged = !audioCapabilities.equals(this.audioCapabilities);
    if (player == null || audioCapabilitiesChanged) {
      this.audioCapabilities = audioCapabilities;
      releasePlayer();
      preparePlayer();
    } else if (player != null) {
      player.setBackgrounded(false);
    }
  }

  // Internal methods

  private RendererBuilder getRendererBuilder() {
    String userAgent = DemoUtil.getUserAgent(this);
    switch (contentType) {
      case DemoUtil.TYPE_SS:
        return new SmoothStreamingRendererBuilder(userAgent, contentUri.toString(),
            new SmoothStreamingTestMediaDrmCallback(), debugTextView);
      case DemoUtil.TYPE_DASH:
        return new DashRendererBuilder(userAgent, contentUri.toString(),
            new WidevineTestMediaDrmCallback(contentId), debugTextView, audioCapabilities);
      case DemoUtil.TYPE_HLS:
        return new HlsRendererBuilder(userAgent, contentUri.toString(), debugTextView);
      case DemoUtil.TYPE_MP4:
        return new ExtractorRendererBuilder(userAgent, contentUri, debugTextView,
            new Mp4Extractor());
      case DemoUtil.TYPE_MP3:
        return new ExtractorRendererBuilder(userAgent, contentUri, debugTextView,
            new Mp3Extractor());
      case DemoUtil.TYPE_AAC:
        return new ExtractorRendererBuilder(userAgent, contentUri, debugTextView,
            new AdtsExtractor());
      default:
        return new DefaultRendererBuilder(this, contentUri, debugTextView);
    }
  }

  private void preparePlayer() {
    if (player == null) {
      player = new DemoPlayer(getRendererBuilder());
      player.addListener(this);
      player.setTextListener(this);
      player.setMetadataListener(this);
      player.seekTo(playerPosition);
      playerNeedsPrepare = true;
      mediaController.setMediaPlayer(player.getPlayerControl());
      mediaController.setEnabled(true);
      eventLogger = new EventLogger();
      eventLogger.startSession();
      player.addListener(eventLogger);
      player.setInfoListener(eventLogger);
      player.setInternalErrorListener(eventLogger);
    }
    if (playerNeedsPrepare) {
      player.prepare();
      playerNeedsPrepare = false;
      updateButtonVisibilities();
    }
    player.setSurface(surfaceView.getHolder().getSurface());
    player.setPlayWhenReady(true);
  }

  private void releasePlayer() {
    if (player != null) {
      playerPosition = player.getCurrentPosition();
      player.release();
      player = null;
      eventLogger.endSession();
      eventLogger = null;
    }
  }

  // DemoPlayer.Listener implementation

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
      case ExoPlayer.STATE_PREPARING:
        text += "preparing";
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
  public void onError(Exception e) {
    if (e instanceof UnsupportedDrmException) {
      // Special case DRM failures.
      UnsupportedDrmException unsupportedDrmException = (UnsupportedDrmException) e;
      int stringId = unsupportedDrmException.reason == UnsupportedDrmException.REASON_NO_DRM
          ? R.string.drm_error_not_supported
          : unsupportedDrmException.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
          ? R.string.drm_error_unsupported_scheme
          : R.string.drm_error_unknown;
      Toast.makeText(getApplicationContext(), stringId, Toast.LENGTH_LONG).show();
    }
    playerNeedsPrepare = true;
    updateButtonVisibilities();
    showControls();
  }

  @Override
  public void onVideoSizeChanged(int width, int height, float pixelWidthAspectRatio) {
    shutterView.setVisibility(View.GONE);
    surfaceView.setVideoWidthHeightRatio(
        height == 0 ? 1 : (width * pixelWidthAspectRatio) / height);
  }

  // User controls

  private void updateButtonVisibilities() {
    retryButton.setVisibility(playerNeedsPrepare ? View.VISIBLE : View.GONE);
    videoButton.setVisibility(haveTracks(DemoPlayer.TYPE_VIDEO) ? View.VISIBLE : View.GONE);
    audioButton.setVisibility(haveTracks(DemoPlayer.TYPE_AUDIO) ? View.VISIBLE : View.GONE);
    textButton.setVisibility(haveTracks(DemoPlayer.TYPE_TEXT) ? View.VISIBLE : View.GONE);
  }

  private boolean haveTracks(int type) {
    return player != null && player.getTracks(type) != null;
  }

  public void showVideoPopup(View v) {
    PopupMenu popup = new PopupMenu(this, v);
    configurePopupWithTracks(popup, null, DemoPlayer.TYPE_VIDEO);
    popup.show();
  }

  public void showAudioPopup(View v) {
    PopupMenu popup = new PopupMenu(this, v);
    Menu menu = popup.getMenu();
    menu.add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.enable_background_audio);
    final MenuItem backgroundAudioItem = menu.findItem(0);
    backgroundAudioItem.setCheckable(true);
    backgroundAudioItem.setChecked(enableBackgroundAudio);
    OnMenuItemClickListener clickListener = new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        if (item == backgroundAudioItem) {
          enableBackgroundAudio = !item.isChecked();
          return true;
        }
        return false;
      }
    };
    configurePopupWithTracks(popup, clickListener, DemoPlayer.TYPE_AUDIO);
    popup.show();
  }

  public void showTextPopup(View v) {
    PopupMenu popup = new PopupMenu(this, v);
    configurePopupWithTracks(popup, null, DemoPlayer.TYPE_TEXT);
    popup.show();
  }

  public void showVerboseLogPopup(View v) {
    PopupMenu popup = new PopupMenu(this, v);
    Menu menu = popup.getMenu();
    menu.add(Menu.NONE, 0, Menu.NONE, R.string.logging_normal);
    menu.add(Menu.NONE, 1, Menu.NONE, R.string.logging_verbose);
    menu.setGroupCheckable(Menu.NONE, true, true);
    menu.findItem((VerboseLogUtil.areAllTagsEnabled()) ? 1 : 0).setChecked(true);
    popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == 0) {
          VerboseLogUtil.setEnableAllTags(false);
        } else {
          VerboseLogUtil.setEnableAllTags(true);
        }
        return true;
      }
    });
    popup.show();
  }

  private void configurePopupWithTracks(PopupMenu popup,
      final OnMenuItemClickListener customActionClickListener,
      final int trackType) {
    if (player == null) {
      return;
    }
    String[] tracks = player.getTracks(trackType);
    if (tracks == null) {
      return;
    }
    popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
      @Override
      public boolean onMenuItemClick(MenuItem item) {
        return (customActionClickListener != null
            && customActionClickListener.onMenuItemClick(item))
            || onTrackItemClick(item, trackType);
      }
    });
    Menu menu = popup.getMenu();
    // ID_OFFSET ensures we avoid clashing with Menu.NONE (which equals 0)
    menu.add(MENU_GROUP_TRACKS, DemoPlayer.DISABLED_TRACK + ID_OFFSET, Menu.NONE, R.string.off);
    if (tracks.length == 1 && TextUtils.isEmpty(tracks[0])) {
      menu.add(MENU_GROUP_TRACKS, DemoPlayer.PRIMARY_TRACK + ID_OFFSET, Menu.NONE, R.string.on);
    } else {
      for (int i = 0; i < tracks.length; i++) {
        menu.add(MENU_GROUP_TRACKS, i + ID_OFFSET, Menu.NONE, tracks[i]);
      }
    }
    menu.setGroupCheckable(MENU_GROUP_TRACKS, true, true);
    menu.findItem(player.getSelectedTrackIndex(trackType) + ID_OFFSET).setChecked(true);
  }

  private boolean onTrackItemClick(MenuItem item, int type) {
    if (player == null || item.getGroupId() != MENU_GROUP_TRACKS) {
      return false;
    }
    player.selectTrack(type, item.getItemId() - ID_OFFSET);
    return true;
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

  // DemoPlayer.TextListener implementation

  @Override
  public void onText(String text) {
    if (TextUtils.isEmpty(text)) {
      subtitleView.setVisibility(View.INVISIBLE);
    } else {
      subtitleView.setVisibility(View.VISIBLE);
      subtitleView.setText(text);
    }
  }

  // DemoPlayer.MetadataListener implementation

  @Override
  public void onId3Metadata(Map<String, Object> metadata) {
    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
      if (TxxxMetadata.TYPE.equals(entry.getKey())) {
        TxxxMetadata txxxMetadata = (TxxxMetadata) entry.getValue();
        Log.i(TAG, String.format("ID3 TimedMetadata %s: description=%s, value=%s",
            TxxxMetadata.TYPE, txxxMetadata.description, txxxMetadata.value));
      } else if (PrivMetadata.TYPE.equals(entry.getKey())) {
        PrivMetadata privMetadata = (PrivMetadata) entry.getValue();
        Log.i(TAG, String.format("ID3 TimedMetadata %s: owner=%s",
            PrivMetadata.TYPE, privMetadata.owner));
      } else if (GeobMetadata.TYPE.equals(entry.getKey())) {
        GeobMetadata geobMetadata = (GeobMetadata) entry.getValue();
        Log.i(TAG, String.format("ID3 TimedMetadata %s: mimeType=%s, filename=%s, description=%s",
            GeobMetadata.TYPE, geobMetadata.mimeType, geobMetadata.filename,
            geobMetadata.description));
      } else {
        Log.i(TAG, String.format("ID3 TimedMetadata %s", entry.getKey()));
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
    CaptionStyleCompat captionStyle;
    float captionTextSize = getCaptionFontSize();
    if (Util.SDK_INT >= 19) {
      captionStyle = getUserCaptionStyleV19();
      captionTextSize *= getUserCaptionFontScaleV19();
    } else {
      captionStyle = CaptionStyleCompat.DEFAULT;
    }
    subtitleView.setStyle(captionStyle);
    subtitleView.setTextSize(captionTextSize);
  }

  private float getCaptionFontSize() {
    Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
        .getDefaultDisplay();
    Point displaySize = new Point();
    display.getSize(displaySize);
    return Math.max(getResources().getDimension(R.dimen.subtitle_minimum_font_size),
        CAPTION_LINE_HEIGHT_RATIO * Math.min(displaySize.x, displaySize.y));
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

}
