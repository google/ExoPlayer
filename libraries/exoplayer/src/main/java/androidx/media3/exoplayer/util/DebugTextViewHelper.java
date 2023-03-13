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
package androidx.media3.exoplayer.util;

import android.annotation.SuppressLint;
import android.os.Looper;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.util.Assertions;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.Locale;

/**
 * A helper class for periodically updating a {@link TextView} with debug information obtained from
 * an {@link ExoPlayer}.
 */
public class DebugTextViewHelper {

  private static final int REFRESH_INTERVAL_MS = 1000;

  private final ExoPlayer player;
  private final TextView textView;
  private final Updater updater;

  private boolean started;

  /**
   * @param player The {@link ExoPlayer} from which debug information should be obtained. Only
   *     players which are accessed on the main thread are supported ({@code
   *     player.getApplicationLooper() == Looper.getMainLooper()}).
   * @param textView The {@link TextView} that should be updated to display the information.
   */
  public DebugTextViewHelper(ExoPlayer player, TextView textView) {
    Assertions.checkArgument(player.getApplicationLooper() == Looper.getMainLooper());
    this.player = player;
    this.textView = textView;
    this.updater = new Updater();
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
    player.addListener(updater);
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
    player.removeListener(updater);
    textView.removeCallbacks(updater);
  }

  // Protected methods.

  @UnstableApi
  @SuppressLint("SetTextI18n")
  protected final void updateAndPost() {
    textView.setText(getDebugString());
    textView.removeCallbacks(updater);
    textView.postDelayed(updater, REFRESH_INTERVAL_MS);
  }

  /** Returns the debugging information string to be shown by the target {@link TextView}. */
  @UnstableApi
  protected String getDebugString() {
    return getPlayerStateString() + getVideoString() + getAudioString();
  }

  /** Returns a string containing player state debugging information. */
  @UnstableApi
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
    return String.format(
        "playWhenReady:%s playbackState:%s item:%s",
        player.getPlayWhenReady(), playbackStateString, player.getCurrentMediaItemIndex());
  }

  /** Returns a string containing video debugging information. */
  @UnstableApi
  protected String getVideoString() {
    Format format = player.getVideoFormat();
    DecoderCounters decoderCounters = player.getVideoDecoderCounters();
    if (format == null || decoderCounters == null) {
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
        + getColorInfoString(format.colorInfo)
        + getPixelAspectRatioString(format.pixelWidthHeightRatio)
        + getDecoderCountersBufferCountString(decoderCounters)
        + " vfpo: "
        + getVideoFrameProcessingOffsetAverageString(
            decoderCounters.totalVideoFrameProcessingOffsetUs,
            decoderCounters.videoFrameProcessingOffsetCount)
        + ")";
  }

  /** Returns a string containing audio debugging information. */
  @UnstableApi
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
    return " sib:"
        + counters.skippedInputBufferCount
        + " sb:"
        + counters.skippedOutputBufferCount
        + " rb:"
        + counters.renderedOutputBufferCount
        + " db:"
        + counters.droppedBufferCount
        + " mcdb:"
        + counters.maxConsecutiveDroppedBufferCount
        + " dk:"
        + counters.droppedToKeyframeCount;
  }

  private static String getColorInfoString(@Nullable ColorInfo colorInfo) {
    return colorInfo != null && colorInfo.isValid() ? " colr:" + colorInfo.toLogString() : "";
  }

  private static String getPixelAspectRatioString(float pixelAspectRatio) {
    return pixelAspectRatio == Format.NO_VALUE || pixelAspectRatio == 1f
        ? ""
        : (" par:" + String.format(Locale.US, "%.02f", pixelAspectRatio));
  }

  private static String getVideoFrameProcessingOffsetAverageString(
      long totalOffsetUs, int frameCount) {
    if (frameCount == 0) {
      return "N/A";
    } else {
      long averageUs = (long) ((double) totalOffsetUs / frameCount);
      return String.valueOf(averageUs);
    }
  }

  private final class Updater implements Player.Listener, Runnable {

    // Player.Listener implementation.

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
      updateAndPost();
    }

    @Override
    public void onPlayWhenReadyChanged(
        boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
      updateAndPost();
    }

    @Override
    public void onPositionDiscontinuity(
        Player.PositionInfo oldPosition,
        Player.PositionInfo newPosition,
        @Player.DiscontinuityReason int reason) {
      updateAndPost();
    }

    // Runnable implementation.

    @Override
    public void run() {
      updateAndPost();
    }
  }
}
