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

import android.os.SystemClock;
import android.util.Log;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.StreamingDrmSessionManager;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.metadata.id3.ApicFrame;
import com.google.android.exoplayer2.metadata.id3.GeobFrame;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;
import com.google.android.exoplayer2.metadata.id3.PrivFrame;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.metadata.id3.TxxxFrame;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.TrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Logs player events using {@link Log}.
 */
/* package */ final class EventLogger implements ExoPlayer.EventListener,
    SimpleExoPlayer.DebugListener, AdaptiveMediaSourceEventListener,
    ExtractorMediaSource.EventListener, StreamingDrmSessionManager.EventListener,
    MappingTrackSelector.EventListener, MetadataRenderer.Output<List<Id3Frame>> {

  private static final String TAG = "EventLogger";
  private static final NumberFormat TIME_FORMAT;
  static {
    TIME_FORMAT = NumberFormat.getInstance(Locale.US);
    TIME_FORMAT.setMinimumFractionDigits(2);
    TIME_FORMAT.setMaximumFractionDigits(2);
  }

  private final long startTimeMs;

  public EventLogger() {
    startTimeMs = SystemClock.elapsedRealtime();
  }

  // ExoPlayer.EventListener

  @Override
  public void onLoadingChanged(boolean isLoading) {
    Log.d(TAG, "loading [" + isLoading + "]");
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int state) {
    Log.d(TAG, "state [" + getSessionTimeString() + ", " + playWhenReady + ", "
        + getStateString(state) + "]");
  }

  @Override
  public void onPositionDiscontinuity(int periodIndex, long positionMs) {
    Log.d(TAG, "discontinuity [" + periodIndex + ", " + positionMs + "]");
  }

  @Override
  public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
    int periodCount = timeline.getPeriodCount();
    int windowCount = timeline.getWindowCount();
    Log.d(TAG, "sourceInfo[startTime=" + timeline.getAbsoluteStartTime() + ", periodCount="
        + periodCount + ", windows: " + windowCount);
    for (int windowIndex = 0; windowIndex < windowCount; windowIndex++) {
      Log.d(TAG, "  " + timeline.getWindow(windowIndex));
    }
    Log.d(TAG, "]");
  }

  @Override
  public void onPlayerError(ExoPlaybackException e) {
    Log.e(TAG, "playerFailed [" + getSessionTimeString() + "]", e);
  }

  // MappingTrackSelector.EventListener

  @Override
  public void onTracksChanged(TrackInfo trackInfo) {
    Log.d(TAG, "Tracks [");
    // Log tracks associated to renderers.
    for (int rendererIndex = 0; rendererIndex < trackInfo.rendererCount; rendererIndex++) {
      TrackGroupArray trackGroups = trackInfo.getTrackGroups(rendererIndex);
      TrackSelection trackSelection = trackInfo.getTrackSelection(rendererIndex);
      if (trackGroups.length > 0) {
        Log.d(TAG, "  Renderer:" + rendererIndex + " [");
        for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
          TrackGroup trackGroup = trackGroups.get(groupIndex);
          String adaptiveSupport = getAdaptiveSupportString(
              trackGroup.length, trackInfo.getAdaptiveSupport(rendererIndex, groupIndex, false));
          Log.d(TAG, "    Group:" + groupIndex + ", adaptive_supported=" + adaptiveSupport + " [");
          for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
            String status = getTrackStatusString(trackSelection, trackGroup, trackIndex);
            String formatSupport = getFormatSupportString(
                trackInfo.getTrackFormatSupport(rendererIndex, groupIndex, trackIndex));
            Log.d(TAG, "      " + status + " Track:" + trackIndex + ", "
                + getFormatString(trackGroup.getFormat(trackIndex))
                + ", supported=" + formatSupport);
          }
          Log.d(TAG, "    ]");
        }
        Log.d(TAG, "  ]");
      }
    }
    // Log tracks not associated with a renderer.
    TrackGroupArray trackGroups = trackInfo.getUnassociatedTrackGroups();
    if (trackGroups.length > 0) {
      Log.d(TAG, "  Renderer:None [");
      for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
        Log.d(TAG, "    Group:" + groupIndex + " [");
        TrackGroup trackGroup = trackGroups.get(groupIndex);
        for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
          String status = getTrackStatusString(false);
          String formatSupport = getFormatSupportString(
              RendererCapabilities.FORMAT_UNSUPPORTED_TYPE);
          Log.d(TAG, "      " + status + " Track:" + trackIndex + ", "
              + getFormatString(trackGroup.getFormat(trackIndex))
              + ", supported=" + formatSupport);
        }
        Log.d(TAG, "    ]");
      }
      Log.d(TAG, "  ]");
    }
    Log.d(TAG, "]");
  }

  // MetadataRenderer.Output<List<Id3Frame>>

  @Override
  public void onMetadata(List<Id3Frame> id3Frames) {
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
      } else if (id3Frame instanceof ApicFrame) {
        ApicFrame apicFrame = (ApicFrame) id3Frame;
        Log.i(TAG, String.format("ID3 TimedMetadata %s: mimeType=%s, description=%s",
            apicFrame.id, apicFrame.mimeType, apicFrame.description));
      } else if (id3Frame instanceof TextInformationFrame) {
        TextInformationFrame textInformationFrame = (TextInformationFrame) id3Frame;
        Log.i(TAG, String.format("ID3 TimedMetadata %s: description=%s", textInformationFrame.id,
            textInformationFrame.description));
      } else {
        Log.i(TAG, String.format("ID3 TimedMetadata %s", id3Frame.id));
      }
    }
  }

  // SimpleExoPlayer.DebugListener

  @Override
  public void onAudioEnabled(DecoderCounters counters) {
    Log.d(TAG, "audioEnabled [" + getSessionTimeString() + "]");
  }

  @Override
  public void onAudioSessionId(int audioSessionId) {
    Log.d(TAG, "audioSessionId [" + audioSessionId + "]");
  }

  @Override
  public void onAudioDecoderInitialized(String decoderName, long elapsedRealtimeMs,
      long initializationDurationMs) {
    Log.d(TAG, "audioDecoderInitialized [" + getSessionTimeString() + ", " + decoderName + "]");
  }

  @Override
  public void onAudioFormatChanged(Format format) {
    Log.d(TAG, "audioFormatChanged [" + getSessionTimeString() + ", " + getFormatString(format)
        + "]");
  }

  @Override
  public void onAudioDisabled(DecoderCounters counters) {
    Log.d(TAG, "audioDisabled [" + getSessionTimeString() + "]");
  }

  @Override
  public void onVideoEnabled(DecoderCounters counters) {
    Log.d(TAG, "videoEnabled [" + getSessionTimeString() + "]");
  }

  @Override
  public void onVideoDecoderInitialized(String decoderName, long elapsedRealtimeMs,
      long initializationDurationMs) {
    Log.d(TAG, "videoDecoderInitialized [" + getSessionTimeString() + ", " + decoderName + "]");
  }

  @Override
  public void onVideoFormatChanged(Format format) {
    Log.d(TAG, "videoFormatChanged [" + getSessionTimeString() + ", " + getFormatString(format)
        + "]");
  }

  @Override
  public void onVideoDisabled(DecoderCounters counters) {
    Log.d(TAG, "videoDisabled [" + getSessionTimeString() + "]");
  }

  @Override
  public void onDroppedFrames(int count, long elapsed) {
    Log.d(TAG, "droppedFrames [" + getSessionTimeString() + ", " + count + "]");
  }

  @Override
  public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    printInternalError("audioTrackUnderrun [" + bufferSize + ", " + bufferSizeMs + ", "
        + elapsedSinceLastFeedMs + "]", null);
  }

  // StreamingDrmSessionManager.EventListener

  @Override
  public void onDrmSessionManagerError(Exception e) {
    printInternalError("drmSessionManagerError", e);
  }

  @Override
  public void onDrmKeysLoaded() {
    Log.d(TAG, "drmKeysLoaded [" + getSessionTimeString() + "]");
  }

  // ExtractorMediaSource.EventListener

  @Override
  public void onLoadError(IOException error) {
    printInternalError("loadError", error);
  }

  // AdaptiveMediaSourceEventListener

  @Override
  public void onLoadStarted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
      int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
      long mediaEndTimeMs, long elapsedRealtimeMs) {
    // Do nothing.
  }

  @Override
  public void onLoadError(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
      int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
      long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded,
      IOException error, boolean wasCanceled) {
    printInternalError("loadError", error);
  }

  @Override
  public void onLoadCanceled(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
      int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
      long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {
    // Do nothing.
  }

  @Override
  public void onLoadCompleted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
      int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
      long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {
    // Do nothing.
  }

  @Override
  public void onUpstreamDiscarded(int trackType, long mediaStartTimeMs, long mediaEndTimeMs) {
    // Do nothing.
  }

  @Override
  public void onDownstreamFormatChanged(int trackType, Format trackFormat, int trackSelectionReason,
      Object trackSelectionData, long mediaTimeMs) {
    // Do nothing.
  }

  // Internal methods

  private void printInternalError(String type, Exception e) {
    Log.e(TAG, "internalError [" + getSessionTimeString() + ", " + type + "]", e);
  }

  private String getSessionTimeString() {
    return getTimeString(SystemClock.elapsedRealtime() - startTimeMs);
  }

  private static String getTimeString(long timeMs) {
    return TIME_FORMAT.format((timeMs) / 1000f);
  }

  private static String getStateString(int state) {
    switch (state) {
      case ExoPlayer.STATE_BUFFERING:
        return "B";
      case ExoPlayer.STATE_ENDED:
        return "E";
      case ExoPlayer.STATE_IDLE:
        return "I";
      case ExoPlayer.STATE_READY:
        return "R";
      default:
        return "?";
    }
  }

  private static String getFormatSupportString(int formatSupport) {
    switch (formatSupport) {
      case RendererCapabilities.FORMAT_HANDLED:
        return "YES";
      case RendererCapabilities.FORMAT_EXCEEDS_CAPABILITIES:
        return "NO_EXCEEDS_CAPABILITIES";
      case RendererCapabilities.FORMAT_UNSUPPORTED_SUBTYPE:
        return "NO_UNSUPPORTED_TYPE";
      case RendererCapabilities.FORMAT_UNSUPPORTED_TYPE:
        return "NO";
      default:
        return "?";
    }
  }

  private static String getAdaptiveSupportString(int trackCount, int adaptiveSupport) {
    if (trackCount < 2) {
      return "N/A";
    }
    switch (adaptiveSupport) {
      case RendererCapabilities.ADAPTIVE_SEAMLESS:
        return "YES";
      case RendererCapabilities.ADAPTIVE_NOT_SEAMLESS:
        return "YES_NOT_SEAMLESS";
      case RendererCapabilities.ADAPTIVE_NOT_SUPPORTED:
        return "NO";
      default:
        return "?";
    }
  }

  private static String getFormatString(Format format) {
    if (format == null) {
      return "null";
    }
    StringBuilder builder = new StringBuilder();
    builder.append("id=").append(format.id).append(", mimeType=").append(format.sampleMimeType);
    if (format.bitrate != Format.NO_VALUE) {
      builder.append(", bitrate=").append(format.bitrate);
    }
    if (format.width != -1 && format.height != -1) {
      builder.append(", res=").append(format.width).append("x").append(format.height);
    }
    if (format.frameRate != -1) {
      builder.append(", fps=").append(format.frameRate);
    }
    if (format.channelCount != -1) {
      builder.append(", channels=").append(format.channelCount);
    }
    if (format.sampleRate != -1) {
      builder.append(", sample_rate=").append(format.sampleRate);
    }
    if (format.language != null) {
      builder.append(", language=").append(format.language);
    }
    return builder.toString();
  }

  private static String getTrackStatusString(TrackSelection selection, TrackGroup group,
      int trackIndex) {
    return getTrackStatusString(selection != null && selection.getTrackGroup() == group
        && selection.indexOf(trackIndex) != -1);
  }

  private static String getTrackStatusString(boolean enabled) {
    return enabled ? "[X]" : "[ ]";
  }

}
