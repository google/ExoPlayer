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

import static androidx.media3.common.util.Util.getFormatSupportString;
import static java.lang.Math.min;

import android.os.SystemClock;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.PlaybackSuppressionReason;
import androidx.media3.common.Timeline;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.drm.DrmSession;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;

/** Logs events from {@link Player} and other core components using {@link Log}. */
@SuppressWarnings("UngroupedOverloads")
public class EventLogger implements AnalyticsListener {

  private static final String DEFAULT_TAG = "EventLogger";
  private static final int MAX_TIMELINE_ITEM_LINES = 3;
  private static final NumberFormat TIME_FORMAT;

  static {
    TIME_FORMAT = NumberFormat.getInstance(Locale.US);
    TIME_FORMAT.setMinimumFractionDigits(2);
    TIME_FORMAT.setMaximumFractionDigits(2);
    TIME_FORMAT.setGroupingUsed(false);
  }

  private final String tag;
  private final Timeline.Window window;
  private final Timeline.Period period;
  private final long startTimeMs;

  /** Creates an instance. */
  public EventLogger() {
    this(DEFAULT_TAG);
  }

  /**
   * Creates an instance.
   *
   * @param tag The tag used for logging.
   */
  public EventLogger(String tag) {
    this.tag = tag;
    window = new Timeline.Window();
    period = new Timeline.Period();
    startTimeMs = SystemClock.elapsedRealtime();
  }

  /**
   * Creates an instance.
   *
   * @param trackSelector This parameter is ignored.
   * @deprecated Use {@link EventLogger()}
   */
  @UnstableApi
  @Deprecated
  public EventLogger(@Nullable MappingTrackSelector trackSelector) {
    this(DEFAULT_TAG);
  }

  /**
   * Creates an instance.
   *
   * @param trackSelector This parameter is ignored.
   * @param tag The tag used for logging.
   * @deprecated Use {@link EventLogger(String)}
   */
  @UnstableApi
  @Deprecated
  public EventLogger(@Nullable MappingTrackSelector trackSelector, String tag) {
    this(tag);
  }

  // AnalyticsListener

  @UnstableApi
  @Override
  public void onIsLoadingChanged(EventTime eventTime, boolean isLoading) {
    logd(eventTime, "loading", Boolean.toString(isLoading));
  }

  @UnstableApi
  @Override
  public void onPlaybackStateChanged(EventTime eventTime, @Player.State int state) {
    logd(eventTime, "state", getStateString(state));
  }

  @UnstableApi
  @Override
  public void onPlayWhenReadyChanged(
      EventTime eventTime, boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
    logd(
        eventTime,
        "playWhenReady",
        playWhenReady + ", " + getPlayWhenReadyChangeReasonString(reason));
  }

  @UnstableApi
  @Override
  public void onPlaybackSuppressionReasonChanged(
      EventTime eventTime, @PlaybackSuppressionReason int playbackSuppressionReason) {
    logd(
        eventTime,
        "playbackSuppressionReason",
        getPlaybackSuppressionReasonString(playbackSuppressionReason));
  }

  @UnstableApi
  @Override
  public void onIsPlayingChanged(EventTime eventTime, boolean isPlaying) {
    logd(eventTime, "isPlaying", Boolean.toString(isPlaying));
  }

  @UnstableApi
  @Override
  public void onRepeatModeChanged(EventTime eventTime, @Player.RepeatMode int repeatMode) {
    logd(eventTime, "repeatMode", getRepeatModeString(repeatMode));
  }

  @UnstableApi
  @Override
  public void onShuffleModeChanged(EventTime eventTime, boolean shuffleModeEnabled) {
    logd(eventTime, "shuffleModeEnabled", Boolean.toString(shuffleModeEnabled));
  }

  @UnstableApi
  @Override
  public void onPositionDiscontinuity(
      EventTime eventTime,
      Player.PositionInfo oldPosition,
      Player.PositionInfo newPosition,
      @Player.DiscontinuityReason int reason) {
    StringBuilder builder = new StringBuilder();
    builder
        .append("reason=")
        .append(getDiscontinuityReasonString(reason))
        .append(", PositionInfo:old [")
        .append("mediaItem=")
        .append(oldPosition.mediaItemIndex)
        .append(", period=")
        .append(oldPosition.periodIndex)
        .append(", pos=")
        .append(oldPosition.positionMs);
    if (oldPosition.adGroupIndex != C.INDEX_UNSET) {
      builder
          .append(", contentPos=")
          .append(oldPosition.contentPositionMs)
          .append(", adGroup=")
          .append(oldPosition.adGroupIndex)
          .append(", ad=")
          .append(oldPosition.adIndexInAdGroup);
    }
    builder
        .append("], PositionInfo:new [")
        .append("mediaItem=")
        .append(newPosition.mediaItemIndex)
        .append(", period=")
        .append(newPosition.periodIndex)
        .append(", pos=")
        .append(newPosition.positionMs);
    if (newPosition.adGroupIndex != C.INDEX_UNSET) {
      builder
          .append(", contentPos=")
          .append(newPosition.contentPositionMs)
          .append(", adGroup=")
          .append(newPosition.adGroupIndex)
          .append(", ad=")
          .append(newPosition.adIndexInAdGroup);
    }
    builder.append("]");
    logd(eventTime, "positionDiscontinuity", builder.toString());
  }

  @UnstableApi
  @Override
  public void onPlaybackParametersChanged(
      EventTime eventTime, PlaybackParameters playbackParameters) {
    logd(eventTime, "playbackParameters", playbackParameters.toString());
  }

  @UnstableApi
  @Override
  public void onTimelineChanged(EventTime eventTime, @Player.TimelineChangeReason int reason) {
    int periodCount = eventTime.timeline.getPeriodCount();
    int windowCount = eventTime.timeline.getWindowCount();
    logd(
        "timeline ["
            + getEventTimeString(eventTime)
            + ", periodCount="
            + periodCount
            + ", windowCount="
            + windowCount
            + ", reason="
            + getTimelineChangeReasonString(reason));
    for (int i = 0; i < min(periodCount, MAX_TIMELINE_ITEM_LINES); i++) {
      eventTime.timeline.getPeriod(i, period);
      logd("  " + "period [" + getTimeString(period.getDurationMs()) + "]");
    }
    if (periodCount > MAX_TIMELINE_ITEM_LINES) {
      logd("  ...");
    }
    for (int i = 0; i < min(windowCount, MAX_TIMELINE_ITEM_LINES); i++) {
      eventTime.timeline.getWindow(i, window);
      logd(
          "  "
              + "window ["
              + getTimeString(window.getDurationMs())
              + ", seekable="
              + window.isSeekable
              + ", dynamic="
              + window.isDynamic
              + "]");
    }
    if (windowCount > MAX_TIMELINE_ITEM_LINES) {
      logd("  ...");
    }
    logd("]");
  }

  @UnstableApi
  @Override
  public void onMediaItemTransition(
      EventTime eventTime, @Nullable MediaItem mediaItem, int reason) {
    logd(
        "mediaItem ["
            + getEventTimeString(eventTime)
            + ", reason="
            + getMediaItemTransitionReasonString(reason)
            + "]");
  }

  @UnstableApi
  @Override
  public void onPlayerError(EventTime eventTime, PlaybackException error) {
    loge(eventTime, "playerFailed", error);
  }

  @UnstableApi
  @Override
  public void onTracksChanged(EventTime eventTime, Tracks tracks) {
    logd("tracks [" + getEventTimeString(eventTime));
    // Log tracks associated to renderers.
    ImmutableList<Tracks.Group> trackGroups = tracks.getGroups();
    for (int groupIndex = 0; groupIndex < trackGroups.size(); groupIndex++) {
      Tracks.Group trackGroup = trackGroups.get(groupIndex);
      logd("  group [");
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        String status = getTrackStatusString(trackGroup.isTrackSelected(trackIndex));
        String formatSupport = getFormatSupportString(trackGroup.getTrackSupport(trackIndex));
        logd(
            "    "
                + status
                + " Track:"
                + trackIndex
                + ", "
                + Format.toLogString(trackGroup.getTrackFormat(trackIndex))
                + ", supported="
                + formatSupport);
      }
      logd("  ]");
    }
    // TODO: Replace this with an override of onMediaMetadataChanged.
    // Log metadata for at most one of the selected tracks.
    boolean loggedMetadata = false;
    for (int groupIndex = 0; !loggedMetadata && groupIndex < trackGroups.size(); groupIndex++) {
      Tracks.Group trackGroup = trackGroups.get(groupIndex);
      for (int trackIndex = 0; !loggedMetadata && trackIndex < trackGroup.length; trackIndex++) {
        if (trackGroup.isTrackSelected(trackIndex)) {
          @Nullable Metadata metadata = trackGroup.getTrackFormat(trackIndex).metadata;
          if (metadata != null && metadata.length() > 0) {
            logd("  Metadata [");
            printMetadata(metadata, "    ");
            logd("  ]");
            loggedMetadata = true;
          }
        }
      }
    }
    logd("]");
  }

  @UnstableApi
  @Override
  public void onMetadata(EventTime eventTime, Metadata metadata) {
    logd("metadata [" + getEventTimeString(eventTime));
    printMetadata(metadata, "  ");
    logd("]");
  }

  @UnstableApi
  @Override
  public void onAudioEnabled(EventTime eventTime, DecoderCounters decoderCounters) {
    logd(eventTime, "audioEnabled");
  }

  @UnstableApi
  @Override
  public void onAudioDecoderInitialized(
      EventTime eventTime, String decoderName, long initializationDurationMs) {
    logd(eventTime, "audioDecoderInitialized", decoderName);
  }

  @UnstableApi
  @Override
  public void onAudioInputFormatChanged(
      EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
    logd(eventTime, "audioInputFormat", Format.toLogString(format));
  }

  @UnstableApi
  @Override
  public void onAudioUnderrun(
      EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    loge(
        eventTime,
        "audioTrackUnderrun",
        bufferSize + ", " + bufferSizeMs + ", " + elapsedSinceLastFeedMs,
        /* throwable= */ null);
  }

  @UnstableApi
  @Override
  public void onAudioDecoderReleased(EventTime eventTime, String decoderName) {
    logd(eventTime, "audioDecoderReleased", decoderName);
  }

  @UnstableApi
  @Override
  public void onAudioDisabled(EventTime eventTime, DecoderCounters decoderCounters) {
    logd(eventTime, "audioDisabled");
  }

  @UnstableApi
  @Override
  public void onAudioSessionIdChanged(EventTime eventTime, int audioSessionId) {
    logd(eventTime, "audioSessionId", Integer.toString(audioSessionId));
  }

  @UnstableApi
  @Override
  public void onAudioAttributesChanged(EventTime eventTime, AudioAttributes audioAttributes) {
    logd(
        eventTime,
        "audioAttributes",
        audioAttributes.contentType
            + ","
            + audioAttributes.flags
            + ","
            + audioAttributes.usage
            + ","
            + audioAttributes.allowedCapturePolicy);
  }

  @UnstableApi
  @Override
  public void onSkipSilenceEnabledChanged(EventTime eventTime, boolean skipSilenceEnabled) {
    logd(eventTime, "skipSilenceEnabled", Boolean.toString(skipSilenceEnabled));
  }

  @UnstableApi
  @Override
  public void onVolumeChanged(EventTime eventTime, float volume) {
    logd(eventTime, "volume", Float.toString(volume));
  }

  @UnstableApi
  @Override
  public void onAudioTrackInitialized(
      EventTime eventTime, AudioSink.AudioTrackConfig audioTrackConfig) {
    logd(eventTime, "audioTrackInit", getAudioTrackConfigString(audioTrackConfig));
  }

  @UnstableApi
  @Override
  public void onAudioTrackReleased(
      EventTime eventTime, AudioSink.AudioTrackConfig audioTrackConfig) {
    logd(eventTime, "audioTrackReleased", getAudioTrackConfigString(audioTrackConfig));
  }

  @UnstableApi
  @Override
  public void onVideoEnabled(EventTime eventTime, DecoderCounters decoderCounters) {
    logd(eventTime, "videoEnabled");
  }

  @UnstableApi
  @Override
  public void onVideoDecoderInitialized(
      EventTime eventTime, String decoderName, long initializationDurationMs) {
    logd(eventTime, "videoDecoderInitialized", decoderName);
  }

  @UnstableApi
  @Override
  public void onVideoInputFormatChanged(
      EventTime eventTime, Format format, @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {
    logd(eventTime, "videoInputFormat", Format.toLogString(format));
  }

  @UnstableApi
  @Override
  public void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {
    logd(eventTime, "droppedFrames", Integer.toString(droppedFrames));
  }

  @UnstableApi
  @Override
  public void onVideoDecoderReleased(EventTime eventTime, String decoderName) {
    logd(eventTime, "videoDecoderReleased", decoderName);
  }

  @UnstableApi
  @Override
  public void onVideoDisabled(EventTime eventTime, DecoderCounters decoderCounters) {
    logd(eventTime, "videoDisabled");
  }

  @UnstableApi
  @Override
  public void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {
    logd(eventTime, "renderedFirstFrame", String.valueOf(output));
  }

  @UnstableApi
  @Override
  public void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {
    logd(eventTime, "videoSize", videoSize.width + ", " + videoSize.height);
  }

  @UnstableApi
  @Override
  public void onLoadStarted(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @UnstableApi
  @Override
  public void onLoadError(
      EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {
    printInternalError(eventTime, "loadError", error);
  }

  @UnstableApi
  @Override
  public void onLoadCanceled(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @UnstableApi
  @Override
  public void onLoadCompleted(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
    // Do nothing.
  }

  @UnstableApi
  @Override
  public void onBandwidthEstimate(
      EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
    // Do nothing.
  }

  @UnstableApi
  @Override
  public void onSurfaceSizeChanged(EventTime eventTime, int width, int height) {
    logd(eventTime, "surfaceSize", width + ", " + height);
  }

  @UnstableApi
  @Override
  public void onUpstreamDiscarded(EventTime eventTime, MediaLoadData mediaLoadData) {
    logd(eventTime, "upstreamDiscarded", Format.toLogString(mediaLoadData.trackFormat));
  }

  @UnstableApi
  @Override
  public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
    logd(eventTime, "downstreamFormat", Format.toLogString(mediaLoadData.trackFormat));
  }

  @UnstableApi
  @Override
  public void onDrmSessionAcquired(EventTime eventTime, @DrmSession.State int state) {
    logd(eventTime, "drmSessionAcquired", "state=" + state);
  }

  @UnstableApi
  @Override
  public void onDrmSessionManagerError(EventTime eventTime, Exception error) {
    printInternalError(eventTime, "drmSessionManagerError", error);
  }

  @UnstableApi
  @Override
  public void onDrmKeysRestored(EventTime eventTime) {
    logd(eventTime, "drmKeysRestored");
  }

  @UnstableApi
  @Override
  public void onDrmKeysRemoved(EventTime eventTime) {
    logd(eventTime, "drmKeysRemoved");
  }

  @UnstableApi
  @Override
  public void onDrmKeysLoaded(EventTime eventTime) {
    logd(eventTime, "drmKeysLoaded");
  }

  @UnstableApi
  @Override
  public void onDrmSessionReleased(EventTime eventTime) {
    logd(eventTime, "drmSessionReleased");
  }

  /**
   * Logs a debug message.
   *
   * @param msg The message to log.
   */
  @UnstableApi
  protected void logd(String msg) {
    Log.d(tag, msg);
  }

  /**
   * Logs an error message.
   *
   * @param msg The message to log.
   */
  @UnstableApi
  protected void loge(String msg) {
    Log.e(tag, msg);
  }

  // Internal methods

  private void logd(EventTime eventTime, String eventName) {
    logd(getEventString(eventTime, eventName, /* eventDescription= */ null, /* throwable= */ null));
  }

  private void logd(EventTime eventTime, String eventName, String eventDescription) {
    logd(getEventString(eventTime, eventName, eventDescription, /* throwable= */ null));
  }

  private void loge(EventTime eventTime, String eventName, @Nullable Throwable throwable) {
    loge(getEventString(eventTime, eventName, /* eventDescription= */ null, throwable));
  }

  private void loge(
      EventTime eventTime,
      String eventName,
      String eventDescription,
      @Nullable Throwable throwable) {
    loge(getEventString(eventTime, eventName, eventDescription, throwable));
  }

  private void printInternalError(EventTime eventTime, String type, Exception e) {
    loge(eventTime, "internalError", type, e);
  }

  private void printMetadata(Metadata metadata, String prefix) {
    for (int i = 0; i < metadata.length(); i++) {
      logd(prefix + metadata.get(i));
    }
  }

  private String getEventString(
      EventTime eventTime,
      String eventName,
      @Nullable String eventDescription,
      @Nullable Throwable throwable) {
    String eventString = eventName + " [" + getEventTimeString(eventTime);
    if (throwable instanceof PlaybackException) {
      eventString += ", errorCode=" + ((PlaybackException) throwable).getErrorCodeName();
    }
    if (eventDescription != null) {
      eventString += ", " + eventDescription;
    }
    @Nullable String throwableString = Log.getThrowableString(throwable);
    if (!TextUtils.isEmpty(throwableString)) {
      eventString += "\n  " + throwableString.replace("\n", "\n  ") + '\n';
    }
    eventString += "]";
    return eventString;
  }

  private String getEventTimeString(EventTime eventTime) {
    String windowPeriodString = "window=" + eventTime.windowIndex;
    if (eventTime.mediaPeriodId != null) {
      windowPeriodString +=
          ", period=" + eventTime.timeline.getIndexOfPeriod(eventTime.mediaPeriodId.periodUid);
      if (eventTime.mediaPeriodId.isAd()) {
        windowPeriodString += ", adGroup=" + eventTime.mediaPeriodId.adGroupIndex;
        windowPeriodString += ", ad=" + eventTime.mediaPeriodId.adIndexInAdGroup;
      }
    }
    return "eventTime="
        + getTimeString(eventTime.realtimeMs - startTimeMs)
        + ", mediaPos="
        + getTimeString(eventTime.eventPlaybackPositionMs)
        + ", "
        + windowPeriodString;
  }

  private static String getTimeString(long timeMs) {
    return timeMs == C.TIME_UNSET ? "?" : TIME_FORMAT.format((timeMs) / 1000f);
  }

  private static String getStateString(int state) {
    switch (state) {
      case Player.STATE_BUFFERING:
        return "BUFFERING";
      case Player.STATE_ENDED:
        return "ENDED";
      case Player.STATE_IDLE:
        return "IDLE";
      case Player.STATE_READY:
        return "READY";
      default:
        return "?";
    }
  }

  private static String getTrackStatusString(boolean selected) {
    return selected ? "[X]" : "[ ]";
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
      case Player.DISCONTINUITY_REASON_AUTO_TRANSITION:
        return "AUTO_TRANSITION";
      case Player.DISCONTINUITY_REASON_SEEK:
        return "SEEK";
      case Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
        return "SEEK_ADJUSTMENT";
      case Player.DISCONTINUITY_REASON_REMOVE:
        return "REMOVE";
      case Player.DISCONTINUITY_REASON_SKIP:
        return "SKIP";
      case Player.DISCONTINUITY_REASON_INTERNAL:
        return "INTERNAL";
      default:
        return "?";
    }
  }

  private static String getTimelineChangeReasonString(@Player.TimelineChangeReason int reason) {
    switch (reason) {
      case Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE:
        return "SOURCE_UPDATE";
      case Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED:
        return "PLAYLIST_CHANGED";
      default:
        return "?";
    }
  }

  private static String getMediaItemTransitionReasonString(
      @Player.MediaItemTransitionReason int reason) {
    switch (reason) {
      case Player.MEDIA_ITEM_TRANSITION_REASON_AUTO:
        return "AUTO";
      case Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED:
        return "PLAYLIST_CHANGED";
      case Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT:
        return "REPEAT";
      case Player.MEDIA_ITEM_TRANSITION_REASON_SEEK:
        return "SEEK";
      default:
        return "?";
    }
  }

  private static String getPlaybackSuppressionReasonString(
      @PlaybackSuppressionReason int playbackSuppressionReason) {
    switch (playbackSuppressionReason) {
      case Player.PLAYBACK_SUPPRESSION_REASON_NONE:
        return "NONE";
      case Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS:
        return "TRANSIENT_AUDIO_FOCUS_LOSS";
      default:
        return "?";
    }
  }

  private static String getPlayWhenReadyChangeReasonString(
      @Player.PlayWhenReadyChangeReason int reason) {
    switch (reason) {
      case Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY:
        return "AUDIO_BECOMING_NOISY";
      case Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS:
        return "AUDIO_FOCUS_LOSS";
      case Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE:
        return "REMOTE";
      case Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST:
        return "USER_REQUEST";
      case Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM:
        return "END_OF_MEDIA_ITEM";
      default:
        return "?";
    }
  }

  private static String getAudioTrackConfigString(AudioSink.AudioTrackConfig audioTrackConfig) {
    return audioTrackConfig.encoding
        + ","
        + audioTrackConfig.channelConfig
        + ","
        + audioTrackConfig.sampleRate
        + ","
        + audioTrackConfig.tunneling
        + ","
        + audioTrackConfig.offload
        + ","
        + audioTrackConfig.bufferSize;
  }
}
