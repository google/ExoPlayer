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
package com.google.android.exoplayer2.ui;

import android.annotation.SuppressLint;
import android.os.Looper;
import android.util.Pair;
import android.widget.TextView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A helper class for periodically updating a {@link TextView} with debug information obtained from
 * a {@link SimpleExoPlayer}.
 */
public class DebugTextViewHelper implements AnalyticsListener, Runnable {

  private static final int REFRESH_INTERVAL_MS = 1000;

  private final SimpleExoPlayer player;
  private final TextView textView;

  private boolean started;
  private long bitrateEstimate;

  private long networkActiveTime;
  private long totalBytesLoaded;
  private long startTime;
  private Format loadingVideoFormat;

  /**
   * @param player The {@link SimpleExoPlayer} from which debug information should be obtained. Only
   *     players which are accessed on the main thread are supported ({@code
   *     player.getApplicationLooper() == Looper.getMainLooper()}).
   * @param textView The {@link TextView} that should be updated to display the information.
   */
  public DebugTextViewHelper(SimpleExoPlayer player, TextView textView) {
    Assertions.checkArgument(player.getApplicationLooper() == Looper.getMainLooper());
    this.player = player;
    this.textView = textView;
  }

  /**
   * Starts periodic updates of the {@link TextView}. Must be called from the application's main
   * thread.
   */
  public final void start() {
    if (started) {
      return;
    }
    started = true;
    networkActiveTime = 0;
    totalBytesLoaded = 0;
    startTime = Clock.DEFAULT.elapsedRealtime();
    player.addAnalyticsListener(this);
    updateAndPost();
  }

  /**
   * Stops periodic updates of the {@link TextView}. Must be called from the application's main
   * thread.
   */
  public final void stop() {
    if (!started) {
      return;
    }
    started = false;
    player.removeAnalyticsListener(this);
    textView.removeCallbacks(this);
  }

  // Analytics implementation.
  @Override
  public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int playbackState) {
    updateAndPost();
  }

  @Override
  public void onPositionDiscontinuity(EventTime eventTime, int reason) {
    updateAndPost();
  }

  @Override
  public void onTimelineChanged(EventTime eventTime, int reason) {
    Timeline timeline = eventTime.timeline;

    if (timeline.getWindowCount() > 0) {
      Timeline.Window window = new Timeline.Window();
      timeline.getWindow(0, window);
      DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.S");
      Date start = new Date(window.windowStartTimeMs);
      String epochDate = format.format(start);

      Log.d("DEBUG", "TimeLine changed: first window start: " + epochDate + " position: " + window.positionInFirstPeriodUs);
    }
    updateAndPost();
  }

  @Override
  public void onBandwidthEstimate(EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
    this.bitrateEstimate = bitrateEstimate;
    this.networkActiveTime += totalLoadTimeMs;
    this.totalBytesLoaded += totalBytesLoaded;
  }

  @Override
  public void onTracksChanged(EventTime eventTime, TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
    Log.d("DebugText", "tracksChanegd: " + trackSelections);
  }

  @Override
  public void onDownstreamFormatChanged(EventTime eventTime, MediaSourceEventListener.MediaLoadData mediaLoadData) {
    if (mediaLoadData.trackType != C.TRACK_TYPE_AUDIO) {
      this.loadingVideoFormat = mediaLoadData.trackFormat;
    }
  }

  // Runnable implementation.

  @Override
  public final void run() {
    updateAndPost();
  }

  // Protected methods.

  @SuppressLint("SetTextI18n")
  protected final void updateAndPost() {
    textView.setText(getDebugString());
    textView.removeCallbacks(this);
    textView.postDelayed(this, REFRESH_INTERVAL_MS);
  }

  /** Returns the debugging information string to be shown by the target {@link TextView}. */
  protected String getDebugString() {
    return getPlayerStateString() + getVideoString() + getAudioString();
  }

  /** Returns a string containing player state debugging information. */
  protected String getPlayerStateString() {
    String playbackStateString;
    switch (player.getPlaybackState()) {
      case Player.STATE_BUFFERING:
        playbackStateString = "buffering";
        break;
      case Player.STATE_ENDED:
        playbackStateString = "ended";
        break;
      case Player.STATE_IDLE:
        playbackStateString = "idle";
        break;
      case Player.STATE_READY:
        playbackStateString = "ready";
        break;
      default:
        playbackStateString = "unknown";
        break;
    }

    long bandwidthUsed = networkActiveTime == 0 ? 0 : totalBytesLoaded / networkActiveTime;
    long timeSinceStart = Clock.DEFAULT.elapsedRealtime() - startTime;
    double percentNet = ((double) networkActiveTime / (double) timeSinceStart) * 100.0;

    return String.format(Locale.getDefault(),"playWhenReady:%s bw:%d (%d KBps) totalBw: %d netu: %3.2f window:%s cp:%s playbackState:%s",
        player.getPlayWhenReady(), bitrateEstimate, bitrateEstimate / 8000, bandwidthUsed, percentNet, player.getCurrentWindowIndex(), getPositionString(), playbackStateString);
  }

  protected String getPositionString() {
    long position = player.getCurrentPosition();
    String time = "";

    Timeline timeline = player.getCurrentTimeline();
    if (timeline != Timeline.EMPTY) {
      int windowIndex = player.getCurrentWindowIndex();
      Timeline.Window currentWindow = new Timeline.Window();
      Pair<Object, Long> periodPosition = timeline.getPeriodPosition(currentWindow, new Timeline.Period(), windowIndex, position);
      long absTime;
      DateFormat format;
      if (currentWindow.windowStartTimeMs == C.TIME_UNSET) {
        format = new SimpleDateFormat("HH:mm:ss.S Z", Locale.getDefault());
        absTime = position;
      } else {
        format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.S Z", Locale.getDefault());
        absTime = currentWindow.windowStartTimeMs + position;
      }
      Date currentMediaTime = new Date(absTime);
      time = format.format(currentMediaTime);
    }

    return time + " (" + position + ")";
  }

  /** Returns a string containing video debugging information. */
  protected String getVideoString() {
    Format format = player.getVideoFormat();
    DecoderCounters decoderCounters = player.getVideoDecoderCounters();
    if (format == null || decoderCounters == null || loadingVideoFormat == null) {
      return "";
    }
    return "\n"
        + format.sampleMimeType
        + "(id:"
        + format.id
        + " r:"
        + format.width
        + "x"
        + format.height
        + getPixelAspectRatioString(format.pixelWidthHeightRatio)
        + " bitrate:"+format.bitrate+" "
        + " loading (bitrate:"+ loadingVideoFormat.bitrate+", id:"+ loadingVideoFormat.id+") "
        + getDecoderCountersBufferCountString(decoderCounters)
        + ")";
  }

  /** Returns a string containing audio debugging information. */
  protected String getAudioString() {
    Format format = player.getAudioFormat();
    DecoderCounters decoderCounters = player.getAudioDecoderCounters();
    if (format == null || decoderCounters == null) {
      return "";
    }
    return "\n"
        + format.sampleMimeType
        + "(id:"
        + format.id
        + " hz:"
        + format.sampleRate
        + " ch:"
        + format.channelCount
        + getDecoderCountersBufferCountString(decoderCounters)
        + ")";
  }

  private static String getDecoderCountersBufferCountString(DecoderCounters counters) {
    if (counters == null) {
      return "";
    }
    counters.ensureUpdated();
    return " qib:" + counters.inputBufferCount
        + " sib:" + counters.skippedInputBufferCount
        + " sb:" + counters.skippedOutputBufferCount
        + " rb:" + counters.renderedOutputBufferCount
        + " db:" + counters.droppedBufferCount
        + " mcdb:" + counters.maxConsecutiveDroppedBufferCount
        + " dk:" + counters.droppedToKeyframeCount;
  }

  private static String getPixelAspectRatioString(float pixelAspectRatio) {
    return pixelAspectRatio == Format.NO_VALUE || pixelAspectRatio == 1f ? ""
        : (" par:" + String.format(Locale.US, "%.02f", pixelAspectRatio));
  }

}
