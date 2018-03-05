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
package com.google.android.exoplayer2.util;

import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.metadata.emsg.EventMessage;
import com.google.android.exoplayer2.metadata.id3.ApicFrame;
import com.google.android.exoplayer2.metadata.id3.CommentFrame;
import com.google.android.exoplayer2.metadata.id3.GeobFrame;
import com.google.android.exoplayer2.metadata.id3.Id3Frame;
import com.google.android.exoplayer2.metadata.id3.PrivFrame;
import com.google.android.exoplayer2.metadata.id3.TextInformationFrame;
import com.google.android.exoplayer2.metadata.id3.UrlLinkFrame;
import com.google.android.exoplayer2.metadata.scte35.SpliceCommand;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.ads.AdsMediaSource;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

/** Logs events from {@link Player} and other core components using {@link Log}. */
public class EventLogger
    implements Player.EventListener,
        MetadataOutput,
        AudioRendererEventListener,
        VideoRendererEventListener,
        MediaSourceEventListener,
        AdsMediaSource.EventListener,
        DefaultDrmSessionManager.EventListener {

  private static final String TAG = "EventLogger";
  private static final int MAX_TIMELINE_ITEM_LINES = 3;
  private static final NumberFormat TIME_FORMAT;
  static {
    TIME_FORMAT = NumberFormat.getInstance(Locale.US);
    TIME_FORMAT.setMinimumFractionDigits(2);
    TIME_FORMAT.setMaximumFractionDigits(2);
    TIME_FORMAT.setGroupingUsed(false);
  }

  private final MappingTrackSelector trackSelector;
  private final Timeline.Window window;
  private final Timeline.Period period;
  private final long startTimeMs;

  public EventLogger(MappingTrackSelector trackSelector) {
    this.trackSelector = trackSelector;
    window = new Timeline.Window();
    period = new Timeline.Period();
    startTimeMs = SystemClock.elapsedRealtime();
  }

  // Player.EventListener

  @Override
  public void onLoadingChanged(boolean isLoading) {
    logd("loading [" + isLoading + "]");
  }

  @Override
  public void onPlayerStateChanged(boolean playWhenReady, int state) {
    logd(
        "state ["
            + getSessionTimeString()
            + ", "
            + playWhenReady
            + ", "
            + getStateString(state)
            + "]");
  }

  @Override
  public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
    logd("repeatMode [" + getRepeatModeString(repeatMode) + "]");
  }

  @Override
  public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
    logd("shuffleModeEnabled [" + shuffleModeEnabled + "]");
  }

  @Override
  public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
    logd("positionDiscontinuity [" + getDiscontinuityReasonString(reason) + "]");
  }

  @Override
  public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
    logd(
        "playbackParameters "
            + String.format(
                "[speed=%.2f, pitch=%.2f]", playbackParameters.speed, playbackParameters.pitch));
  }

  @Override
  public void onTimelineChanged(
      Timeline timeline, Object manifest, @Player.TimelineChangeReason int reason) {
    int periodCount = timeline.getPeriodCount();
    int windowCount = timeline.getWindowCount();
    logd(
        "timelineChanged [periodCount="
            + periodCount
            + ", windowCount="
            + windowCount
            + ", reason="
            + getTimelineChangeReasonString(reason));
    for (int i = 0; i < Math.min(periodCount, MAX_TIMELINE_ITEM_LINES); i++) {
      timeline.getPeriod(i, period);
      logd("  " + "period [" + getTimeString(period.getDurationMs()) + "]");
    }
    if (periodCount > MAX_TIMELINE_ITEM_LINES) {
      logd("  ...");
    }
    for (int i = 0; i < Math.min(windowCount, MAX_TIMELINE_ITEM_LINES); i++) {
      timeline.getWindow(i, window);
      logd(
          "  "
              + "window ["
              + getTimeString(window.getDurationMs())
              + ", "
              + window.isSeekable
              + ", "
              + window.isDynamic
              + "]");
    }
    if (windowCount > MAX_TIMELINE_ITEM_LINES) {
      logd("  ...");
    }
    logd("]");
  }

  @Override
  public void onPlayerError(ExoPlaybackException e) {
    loge("playerFailed [" + getSessionTimeString() + "]", e);
  }

  @Override
  public void onTracksChanged(TrackGroupArray ignored, TrackSelectionArray trackSelections) {
    MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
    if (mappedTrackInfo == null) {
      logd("Tracks []");
      return;
    }
    logd("Tracks [");
    // Log tracks associated to renderers.
    for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.length; rendererIndex++) {
      TrackGroupArray rendererTrackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
      TrackSelection trackSelection = trackSelections.get(rendererIndex);
      if (rendererTrackGroups.length > 0) {
        logd("  Renderer:" + rendererIndex + " [");
        for (int groupIndex = 0; groupIndex < rendererTrackGroups.length; groupIndex++) {
          TrackGroup trackGroup = rendererTrackGroups.get(groupIndex);
          String adaptiveSupport =
              getAdaptiveSupportString(
                  trackGroup.length,
                  mappedTrackInfo.getAdaptiveSupport(rendererIndex, groupIndex, false));
          logd("    Group:" + groupIndex + ", adaptive_supported=" + adaptiveSupport + " [");
          for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
            String status = getTrackStatusString(trackSelection, trackGroup, trackIndex);
            String formatSupport =
                getFormatSupportString(
                    mappedTrackInfo.getTrackFormatSupport(rendererIndex, groupIndex, trackIndex));
            logd(
                "      "
                    + status
                    + " Track:"
                    + trackIndex
                    + ", "
                    + Format.toLogString(trackGroup.getFormat(trackIndex))
                    + ", supported="
                    + formatSupport);
          }
          logd("    ]");
        }
        // Log metadata for at most one of the tracks selected for the renderer.
        if (trackSelection != null) {
          for (int selectionIndex = 0; selectionIndex < trackSelection.length(); selectionIndex++) {
            Metadata metadata = trackSelection.getFormat(selectionIndex).metadata;
            if (metadata != null) {
              logd("    Metadata [");
              printMetadata(metadata, "      ");
              logd("    ]");
              break;
            }
          }
        }
        logd("  ]");
      }
    }
    // Log tracks not associated with a renderer.
    TrackGroupArray unassociatedTrackGroups = mappedTrackInfo.getUnassociatedTrackGroups();
    if (unassociatedTrackGroups.length > 0) {
      logd("  Renderer:None [");
      for (int groupIndex = 0; groupIndex < unassociatedTrackGroups.length; groupIndex++) {
        logd("    Group:" + groupIndex + " [");
        TrackGroup trackGroup = unassociatedTrackGroups.get(groupIndex);
        for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
          String status = getTrackStatusString(false);
          String formatSupport =
              getFormatSupportString(RendererCapabilities.FORMAT_UNSUPPORTED_TYPE);
          logd(
              "      "
                  + status
                  + " Track:"
                  + trackIndex
                  + ", "
                  + Format.toLogString(trackGroup.getFormat(trackIndex))
                  + ", supported="
                  + formatSupport);
        }
        logd("    ]");
      }
      logd("  ]");
    }
    logd("]");
  }

  @Override
  public void onSeekProcessed() {
    logd("seekProcessed");
  }

  // MetadataOutput

  @Override
  public void onMetadata(Metadata metadata) {
    logd("onMetadata [");
    printMetadata(metadata, "  ");
    logd("]");
  }

  // AudioRendererEventListener

  @Override
  public void onAudioEnabled(DecoderCounters counters) {
    logd("audioEnabled [" + getSessionTimeString() + "]");
  }

  @Override
  public void onAudioSessionId(int audioSessionId) {
    logd("audioSessionId [" + audioSessionId + "]");
  }

  @Override
  public void onAudioDecoderInitialized(
      String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {
    logd("audioDecoderInitialized [" + getSessionTimeString() + ", " + decoderName + "]");
  }

  @Override
  public void onAudioInputFormatChanged(Format format) {
    logd("audioFormatChanged [" + getSessionTimeString() + ", " + Format.toLogString(format) + "]");
  }

  @Override
  public void onAudioDisabled(DecoderCounters counters) {
    logd("audioDisabled [" + getSessionTimeString() + "]");
  }

  @Override
  public void onAudioSinkUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    printInternalError("audioTrackUnderrun [" + bufferSize + ", " + bufferSizeMs + ", "
        + elapsedSinceLastFeedMs + "]", null);
  }

  // VideoRendererEventListener

  @Override
  public void onVideoEnabled(DecoderCounters counters) {
    logd("videoEnabled [" + getSessionTimeString() + "]");
  }

  @Override
  public void onVideoDecoderInitialized(
      String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {
    logd("videoDecoderInitialized [" + getSessionTimeString() + ", " + decoderName + "]");
  }

  @Override
  public void onVideoInputFormatChanged(Format format) {
    logd("videoFormatChanged [" + getSessionTimeString() + ", " + Format.toLogString(format) + "]");
  }

  @Override
  public void onVideoDisabled(DecoderCounters counters) {
    logd("videoDisabled [" + getSessionTimeString() + "]");
  }

  @Override
  public void onDroppedFrames(int count, long elapsed) {
    logd("droppedFrames [" + getSessionTimeString() + ", " + count + "]");
  }

  @Override
  public void onVideoSizeChanged(
      int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
    logd("videoSizeChanged [" + width + ", " + height + "]");
  }

  @Override
  public void onRenderedFirstFrame(Surface surface) {
    logd("renderedFirstFrame [" + surface + "]");
  }

  // DefaultDrmSessionManager.EventListener

  @Override
  public void onDrmSessionManagerError(Exception e) {
    printInternalError("drmSessionManagerError", e);
  }

  @Override
  public void onDrmKeysRestored() {
    logd("drmKeysRestored [" + getSessionTimeString() + "]");
  }

  @Override
  public void onDrmKeysRemoved() {
    logd("drmKeysRemoved [" + getSessionTimeString() + "]");
  }

  @Override
  public void onDrmKeysLoaded() {
    logd("drmKeysLoaded [" + getSessionTimeString() + "]");
  }

  // MediaSourceEventListener

  @Override
  public void onLoadStarted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @Override
  public void onLoadError(
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {
    printInternalError("loadError", error);
  }

  @Override
  public void onLoadCanceled(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @Override
  public void onLoadCompleted(LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @Override
  public void onUpstreamDiscarded(MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @Override
  public void onDownstreamFormatChanged(MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  // AdsMediaSource.EventListener

  @Override
  public void onAdLoadError(IOException error) {
    printInternalError("adLoadError", error);
  }

  @Override
  public void onInternalAdLoadError(RuntimeException error) {
    printInternalError("internalAdLoadError", error);
  }

  @Override
  public void onAdClicked() {
    // Do nothing.
  }

  @Override
  public void onAdTapped() {
    // Do nothing.
  }

  /**
   * Logs a debug message.
   *
   * @param msg The message to log.
   */
  protected void logd(String msg) {
    Log.d(TAG, msg);
  }

  /**
   * Logs an error message and exception.
   *
   * @param msg The message to log.
   * @param tr The exception to log.
   */
  protected void loge(String msg, Throwable tr) {
    Log.e(TAG, msg, tr);
  }

  // Internal methods

  private void printInternalError(String type, Exception e) {
    loge("internalError [" + getSessionTimeString() + ", " + type + "]", e);
  }

  private void printMetadata(Metadata metadata, String prefix) {
    for (int i = 0; i < metadata.length(); i++) {
      Metadata.Entry entry = metadata.get(i);
      if (entry instanceof TextInformationFrame) {
        TextInformationFrame textInformationFrame = (TextInformationFrame) entry;
        logd(
            prefix
                + String.format(
                    "%s: value=%s", textInformationFrame.id, textInformationFrame.value));
      } else if (entry instanceof UrlLinkFrame) {
        UrlLinkFrame urlLinkFrame = (UrlLinkFrame) entry;
        logd(prefix + String.format("%s: url=%s", urlLinkFrame.id, urlLinkFrame.url));
      } else if (entry instanceof PrivFrame) {
        PrivFrame privFrame = (PrivFrame) entry;
        logd(prefix + String.format("%s: owner=%s", privFrame.id, privFrame.owner));
      } else if (entry instanceof GeobFrame) {
        GeobFrame geobFrame = (GeobFrame) entry;
        logd(
            prefix
                + String.format(
                    "%s: mimeType=%s, filename=%s, description=%s",
                    geobFrame.id, geobFrame.mimeType, geobFrame.filename, geobFrame.description));
      } else if (entry instanceof ApicFrame) {
        ApicFrame apicFrame = (ApicFrame) entry;
        logd(
            prefix
                + String.format(
                    "%s: mimeType=%s, description=%s",
                    apicFrame.id, apicFrame.mimeType, apicFrame.description));
      } else if (entry instanceof CommentFrame) {
        CommentFrame commentFrame = (CommentFrame) entry;
        logd(
            prefix
                + String.format(
                    "%s: language=%s, description=%s",
                    commentFrame.id, commentFrame.language, commentFrame.description));
      } else if (entry instanceof Id3Frame) {
        Id3Frame id3Frame = (Id3Frame) entry;
        logd(prefix + id3Frame.id);
      } else if (entry instanceof EventMessage) {
        EventMessage eventMessage = (EventMessage) entry;
        logd(
            prefix
                + String.format(
                    "EMSG: scheme=%s, id=%d, value=%s",
                    eventMessage.schemeIdUri, eventMessage.id, eventMessage.value));
      } else if (entry instanceof SpliceCommand) {
        String description =
            String.format("SCTE-35 splice command: type=%s.", entry.getClass().getSimpleName());
        logd(prefix + description);
      }
    }
  }

  private String getSessionTimeString() {
    return getTimeString(SystemClock.elapsedRealtime() - startTimeMs);
  }

  private static String getTimeString(long timeMs) {
    return timeMs == C.TIME_UNSET ? "?" : TIME_FORMAT.format((timeMs) / 1000f);
  }

  private static String getStateString(int state) {
    switch (state) {
      case Player.STATE_BUFFERING:
        return "B";
      case Player.STATE_ENDED:
        return "E";
      case Player.STATE_IDLE:
        return "I";
      case Player.STATE_READY:
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
      case RendererCapabilities.FORMAT_UNSUPPORTED_DRM:
        return "NO_UNSUPPORTED_DRM";
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

  // Suppressing reference equality warning because the track group stored in the track selection
  // must point to the exact track group object to be considered part of it.
  @SuppressWarnings("ReferenceEquality")
  private static String getTrackStatusString(TrackSelection selection, TrackGroup group,
      int trackIndex) {
    return getTrackStatusString(selection != null && selection.getTrackGroup() == group
        && selection.indexOf(trackIndex) != C.INDEX_UNSET);
  }

  private static String getTrackStatusString(boolean enabled) {
    return enabled ? "[X]" : "[ ]";
  }

  private static String getRepeatModeString(@Player.RepeatMode int repeatMode) {
    switch (repeatMode) {
      case Player.REPEAT_MODE_OFF:
        return "OFF";
      case Player.REPEAT_MODE_ONE:
        return "ONE";
      case Player.REPEAT_MODE_ALL:
        return "ALL";
      default:
        return "?";
    }
  }

  private static String getDiscontinuityReasonString(@Player.DiscontinuityReason int reason) {
    switch (reason) {
      case Player.DISCONTINUITY_REASON_PERIOD_TRANSITION:
        return "PERIOD_TRANSITION";
      case Player.DISCONTINUITY_REASON_SEEK:
        return "SEEK";
      case Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
        return "SEEK_ADJUSTMENT";
      case Player.DISCONTINUITY_REASON_AD_INSERTION:
        return "AD_INSERTION";
      case Player.DISCONTINUITY_REASON_INTERNAL:
        return "INTERNAL";
      default:
        return "?";
    }
  }

  private static String getTimelineChangeReasonString(@Player.TimelineChangeReason int reason) {
    switch (reason) {
      case Player.TIMELINE_CHANGE_REASON_PREPARED:
        return "PREPARED";
      case Player.TIMELINE_CHANGE_REASON_RESET:
        return "RESET";
      case Player.TIMELINE_CHANGE_REASON_DYNAMIC:
        return "DYNAMIC";
      default:
        return "?";
    }
  }

}
