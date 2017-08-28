/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.cast;

import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.cast.CastStatusCodes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.MediaTrack;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.cast.framework.media.RemoteMediaClient.MediaChannelResult;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.ResultCallback;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * {@link Player} implementation that communicates with a Cast receiver app.
 *
 * <p>Calls to the methods in this class depend on the availability of an underlying cast session.
 * If no session is available, method calls have no effect. To keep track of the underyling session,
 * {@link #isCastSessionAvailable()} can be queried and {@link SessionAvailabilityListener} can be
 * implemented and attached to the player.
 *
 * <p>Methods should be called on the application's main thread.
 *
 * <p>Known issues:
 * <ul>
 *   <li>Part of the Cast API is not exposed through this interface. For instance, volume settings
 *   and track selection.</li>
 *   <li> Repeat mode is not working. See [internal: b/64137174].</li>
 * </ul>
 */
public final class CastPlayer implements Player {

  /**
   * Listener of changes in the cast session availability.
   */
  public interface SessionAvailabilityListener {

    /**
     * Called when a cast session becomes available to the player.
     */
    void onCastSessionAvailable();

    /**
     * Called when the cast session becomes unavailable.
     */
    void onCastSessionUnavailable();

  }

  private static final String TAG = "CastPlayer";

  private static final int RENDERER_COUNT = 3;
  private static final int RENDERER_INDEX_VIDEO = 0;
  private static final int RENDERER_INDEX_AUDIO = 1;
  private static final int RENDERER_INDEX_TEXT = 2;
  private static final long PROGRESS_REPORT_PERIOD_MS = 1000;
  private static final TrackGroupArray EMPTY_TRACK_GROUP_ARRAY = new TrackGroupArray();
  private static final TrackSelectionArray EMPTY_TRACK_SELECTION_ARRAY =
      new TrackSelectionArray(null, null, null);
  private static final long[] EMPTY_TRACK_ID_ARRAY = new long[0];

  private final CastContext castContext;
  private final Timeline.Window window;

  // Result callbacks.
  private final StatusListener statusListener;
  private final RepeatModeResultCallback repeatModeResultCallback;
  private final SeekResultCallback seekResultCallback;

  // Listeners.
  private final CopyOnWriteArraySet<EventListener> listeners;
  private SessionAvailabilityListener sessionAvailabilityListener;

  // Internal state.
  private RemoteMediaClient remoteMediaClient;
  private Timeline currentTimeline;
  private TrackGroupArray currentTrackGroups;
  private TrackSelectionArray currentTrackSelection;
  private long lastReportedPositionMs;
  private long pendingSeekPositionMs;

  /**
   * @param castContext The context from which the cast session is obtained.
   */
  public CastPlayer(CastContext castContext) {
    this.castContext = castContext;
    window = new Timeline.Window();
    statusListener = new StatusListener();
    repeatModeResultCallback = new RepeatModeResultCallback();
    seekResultCallback = new SeekResultCallback();
    listeners = new CopyOnWriteArraySet<>();
    SessionManager sessionManager = castContext.getSessionManager();
    sessionManager.addSessionManagerListener(statusListener, CastSession.class);
    CastSession session = sessionManager.getCurrentCastSession();
    remoteMediaClient = session != null ? session.getRemoteMediaClient() : null;
    pendingSeekPositionMs = C.TIME_UNSET;
    updateInternalState();
  }

  /**
   * Loads media into the receiver app.
   *
   * @param title The title of the media sample.
   * @param url The url from which the media is obtained.
   * @param contentMimeType The mime type of the content to play.
   * @param positionMs The position at which the playback should start in milliseconds.
   * @param playWhenReady Whether the player should start playback as soon as it is ready to do so.
   */
  public void load(String title, String url, String contentMimeType, long positionMs,
      boolean playWhenReady) {
    lastReportedPositionMs = 0;
    if (remoteMediaClient != null) {
      MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
      movieMetadata.putString(MediaMetadata.KEY_TITLE, title);
      MediaInfo mediaInfo = new MediaInfo.Builder(url).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
          .setContentType(contentMimeType).setMetadata(movieMetadata).build();
      remoteMediaClient.load(mediaInfo, playWhenReady, positionMs);
    }
  }

  /**
   * Returns whether a cast session is available for playback.
   */
  public boolean isCastSessionAvailable() {
    return remoteMediaClient != null;
  }

  /**
   * Sets a listener for updates on the cast session availability.
   *
   * @param listener The {@link SessionAvailabilityListener}.
   */
  public void setSessionAvailabilityListener(SessionAvailabilityListener listener) {
    sessionAvailabilityListener = listener;
  }

  // Player implementation.

  @Override
  public void addListener(EventListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(EventListener listener) {
    listeners.remove(listener);
  }

  @Override
  public int getPlaybackState() {
    if (remoteMediaClient == null) {
      return STATE_IDLE;
    }
    int receiverAppStatus = remoteMediaClient.getPlayerState();
    switch (receiverAppStatus) {
      case MediaStatus.PLAYER_STATE_BUFFERING:
        return STATE_BUFFERING;
      case MediaStatus.PLAYER_STATE_PLAYING:
      case MediaStatus.PLAYER_STATE_PAUSED:
        return STATE_READY;
      case MediaStatus.PLAYER_STATE_IDLE:
      case MediaStatus.PLAYER_STATE_UNKNOWN:
      default:
        return STATE_IDLE;
    }
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    if (remoteMediaClient == null) {
      return;
    }
    if (playWhenReady) {
      remoteMediaClient.play();
    } else {
      remoteMediaClient.pause();
    }
  }

  @Override
  public boolean getPlayWhenReady() {
    return remoteMediaClient != null && !remoteMediaClient.isPaused();
  }

  @Override
  public void seekToDefaultPosition() {
    seekTo(0);
  }

  @Override
  public void seekToDefaultPosition(int windowIndex) {
    seekTo(windowIndex, 0);
  }

  @Override
  public void seekTo(long positionMs) {
    seekTo(0, positionMs);
  }

  @Override
  public void seekTo(int windowIndex, long positionMs) {
    if (remoteMediaClient != null) {
      remoteMediaClient.seek(positionMs).setResultCallback(seekResultCallback);
      pendingSeekPositionMs = positionMs;
      for (EventListener listener : listeners) {
        listener.onPositionDiscontinuity();
      }
    }
  }

  @Override
  public void setPlaybackParameters(@Nullable PlaybackParameters playbackParameters) {
    // Unsupported by the RemoteMediaClient API. Do nothing.
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    return PlaybackParameters.DEFAULT;
  }

  @Override
  public void stop() {
    if (remoteMediaClient != null) {
      remoteMediaClient.stop();
    }
  }

  @Override
  public void release() {
    castContext.getSessionManager().removeSessionManagerListener(statusListener, CastSession.class);
  }

  @Override
  public int getRendererCount() {
    // We assume there are three renderers: video, audio, and text.
    return RENDERER_COUNT;
  }

  @Override
  public int getRendererType(int index) {
    switch (index) {
      case RENDERER_INDEX_VIDEO:
        return C.TRACK_TYPE_VIDEO;
      case RENDERER_INDEX_AUDIO:
        return C.TRACK_TYPE_AUDIO;
      case RENDERER_INDEX_TEXT:
        return C.TRACK_TYPE_TEXT;
      default:
        throw new IndexOutOfBoundsException();
    }
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    if (remoteMediaClient != null) {
      int castRepeatMode;
      switch (repeatMode) {
        case REPEAT_MODE_ONE:
          castRepeatMode = MediaStatus.REPEAT_MODE_REPEAT_SINGLE;
          break;
        case REPEAT_MODE_ALL:
          castRepeatMode = MediaStatus.REPEAT_MODE_REPEAT_ALL;
          break;
        case REPEAT_MODE_OFF:
          castRepeatMode = MediaStatus.REPEAT_MODE_REPEAT_OFF;
          break;
        default:
          throw new IllegalArgumentException();
      }
      remoteMediaClient.queueSetRepeatMode(castRepeatMode, null)
          .setResultCallback(repeatModeResultCallback);
    }
  }

  @Override
  @RepeatMode public int getRepeatMode() {
    if (remoteMediaClient == null) {
      return REPEAT_MODE_OFF;
    }
    MediaStatus mediaStatus = getMediaStatus();
    if (mediaStatus == null) {
      // No media session active, yet.
      return REPEAT_MODE_OFF;
    }
    int castRepeatMode = mediaStatus.getQueueRepeatMode();
    switch (castRepeatMode) {
      case MediaStatus.REPEAT_MODE_REPEAT_SINGLE:
        return REPEAT_MODE_ONE;
      case MediaStatus.REPEAT_MODE_REPEAT_ALL:
      case MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE:
          return REPEAT_MODE_ALL;
      case MediaStatus.REPEAT_MODE_REPEAT_OFF:
        return REPEAT_MODE_OFF;
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    // TODO: Support shuffle mode.
  }

  @Override
  public boolean getShuffleModeEnabled() {
    // TODO: Support shuffle mode.
    return false;
  }

  @Override
  public TrackSelectionArray getCurrentTrackSelections() {
    return currentTrackSelection;
  }

  @Override
  public TrackGroupArray getCurrentTrackGroups() {
    return currentTrackGroups;
  }

  @Override
  public Timeline getCurrentTimeline() {
    return currentTimeline;
  }

  @Override
  @Nullable public Object getCurrentManifest() {
    return null;
  }

  @Override
  public int getCurrentPeriodIndex() {
    return 0;
  }

  @Override
  public int getCurrentWindowIndex() {
    return 0;
  }

  @Override
  public long getDuration() {
    return currentTimeline.isEmpty() ? C.TIME_UNSET
        : currentTimeline.getWindow(0, window).getDurationMs();
  }

  @Override
  public long getCurrentPosition() {
    return remoteMediaClient == null ? lastReportedPositionMs
        : pendingSeekPositionMs != C.TIME_UNSET ? pendingSeekPositionMs
        : remoteMediaClient.getApproximateStreamPosition();
  }

  @Override
  public long getBufferedPosition() {
    return getCurrentPosition();
  }

  @Override
  public int getBufferedPercentage() {
    long position = getBufferedPosition();
    long duration = getDuration();
    return position == C.TIME_UNSET || duration == C.TIME_UNSET ? 0
        : duration == 0 ? 100
        : Util.constrainValue((int) ((position * 100) / duration), 0, 100);
  }

  @Override
  public boolean isCurrentWindowDynamic() {
    return !currentTimeline.isEmpty()
        && currentTimeline.getWindow(getCurrentWindowIndex(), window).isDynamic;
  }

  @Override
  public boolean isCurrentWindowSeekable() {
    return !currentTimeline.isEmpty()
        && currentTimeline.getWindow(getCurrentWindowIndex(), window).isSeekable;
  }

  @Override
  public boolean isPlayingAd() {
    return false;
  }

  @Override
  public int getCurrentAdGroupIndex() {
    return C.INDEX_UNSET;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    return C.INDEX_UNSET;
  }

  @Override
  public boolean isLoading() {
    return false;
  }

  @Override
  public long getContentPosition() {
    return getCurrentPosition();
  }

  // Internal methods.

  private void setRemoteMediaClient(@Nullable RemoteMediaClient remoteMediaClient) {
    if (this.remoteMediaClient == remoteMediaClient) {
      // Do nothing.
      return;
    }
    if (this.remoteMediaClient != null) {
      this.remoteMediaClient.removeListener(statusListener);
      this.remoteMediaClient.removeProgressListener(statusListener);
    }
    this.remoteMediaClient = remoteMediaClient;
    if (remoteMediaClient != null) {
      if (sessionAvailabilityListener != null) {
        sessionAvailabilityListener.onCastSessionAvailable();
      }
      remoteMediaClient.addListener(statusListener);
      remoteMediaClient.addProgressListener(statusListener, PROGRESS_REPORT_PERIOD_MS);
    } else {
      if (sessionAvailabilityListener != null) {
        sessionAvailabilityListener.onCastSessionUnavailable();
      }
    }
  }

  private @Nullable MediaStatus getMediaStatus() {
    return remoteMediaClient != null ? remoteMediaClient.getMediaStatus() : null;
  }

  private @Nullable MediaInfo getMediaInfo() {
    return remoteMediaClient != null ? remoteMediaClient.getMediaInfo() : null;
  }

  private void updateInternalState() {
    currentTimeline = Timeline.EMPTY;
    currentTrackGroups = EMPTY_TRACK_GROUP_ARRAY;
    currentTrackSelection = EMPTY_TRACK_SELECTION_ARRAY;
    MediaInfo mediaInfo = getMediaInfo();
    if (mediaInfo == null) {
      return;
    }
    long streamDurationMs = mediaInfo.getStreamDuration();
    boolean isSeekable = streamDurationMs != MediaInfo.UNKNOWN_DURATION;
    currentTimeline = new SinglePeriodTimeline(
        isSeekable ? C.msToUs(streamDurationMs) : C.TIME_UNSET, isSeekable);

    List<MediaTrack> tracks = mediaInfo.getMediaTracks();
    if (tracks == null) {
      return;
    }

    MediaStatus mediaStatus = getMediaStatus();
    long[] activeTrackIds = mediaStatus != null ? mediaStatus.getActiveTrackIds() : null;
    if (activeTrackIds == null) {
      activeTrackIds = EMPTY_TRACK_ID_ARRAY;
    }

    TrackGroup[] trackGroups = new TrackGroup[tracks.size()];
    TrackSelection[] trackSelections = new TrackSelection[RENDERER_COUNT];
    for (int i = 0; i < tracks.size(); i++) {
      MediaTrack mediaTrack = tracks.get(i);
      trackGroups[i] = new TrackGroup(CastUtils.mediaTrackToFormat(mediaTrack));

      long id = mediaTrack.getId();
      int trackType = MimeTypes.getTrackType(mediaTrack.getContentType());
      int rendererIndex = getRendererIndexForTrackType(trackType);
      if (isTrackActive(id, activeTrackIds) && rendererIndex != C.INDEX_UNSET
          && trackSelections[rendererIndex] == null) {
        trackSelections[rendererIndex] = new FixedTrackSelection(trackGroups[i], 0);
      }
    }
    currentTrackSelection = new TrackSelectionArray(trackSelections);
    currentTrackGroups = new TrackGroupArray(trackGroups);
  }

  private static boolean isTrackActive(long id, long[] activeTrackIds) {
    for (long activeTrackId : activeTrackIds) {
      if (activeTrackId == id) {
        return true;
      }
    }
    return false;
  }

  private static int getRendererIndexForTrackType(int trackType) {
    return trackType == C.TRACK_TYPE_VIDEO ? RENDERER_INDEX_VIDEO
        : trackType == C.TRACK_TYPE_AUDIO ? RENDERER_INDEX_AUDIO
        : trackType == C.TRACK_TYPE_TEXT ? RENDERER_INDEX_TEXT
        : C.INDEX_UNSET;
  }

  private final class StatusListener implements RemoteMediaClient.Listener,
      SessionManagerListener<CastSession>, RemoteMediaClient.ProgressListener {

    // RemoteMediaClient.ProgressListener implementation.

    @Override
    public void onProgressUpdated(long progressMs, long unusedDurationMs) {
      lastReportedPositionMs = progressMs;
    }

    // RemoteMediaClient.Listener implementation.

    @Override
    public void onStatusUpdated() {
      boolean playWhenReady = getPlayWhenReady();
      int playbackState = getPlaybackState();
      for (EventListener listener : listeners) {
        listener.onPlayerStateChanged(playWhenReady, playbackState);
      }
    }

    @Override
    public void onMetadataUpdated() {
      updateInternalState();
      for (EventListener listener : listeners) {
        listener.onTracksChanged(currentTrackGroups, currentTrackSelection);
        listener.onTimelineChanged(currentTimeline, null);
      }
    }

    @Override
    public void onQueueStatusUpdated() {}

    @Override
    public void onPreloadStatusUpdated() {}

    @Override
    public void onSendingRemoteMediaRequest() {}

    @Override
    public void onAdBreakStatusUpdated() {}


    // SessionManagerListener implementation.

    @Override
    public void onSessionStarted(CastSession castSession, String s) {
      setRemoteMediaClient(castSession.getRemoteMediaClient());
    }

    @Override
    public void onSessionResumed(CastSession castSession, boolean b) {
      setRemoteMediaClient(castSession.getRemoteMediaClient());
    }

    @Override
    public void onSessionEnded(CastSession castSession, int i) {
      setRemoteMediaClient(null);
    }

    @Override
    public void onSessionSuspended(CastSession castSession, int i) {
      setRemoteMediaClient(null);
    }

    @Override
    public void onSessionResumeFailed(CastSession castSession, int statusCode) {
      Log.e(TAG, "Session resume failed. Error code " + statusCode + ": "
          + CastUtils.getLogString(statusCode));
    }

    @Override
    public void onSessionStarting(CastSession castSession) {
      // Do nothing.
    }

    @Override
    public void onSessionStartFailed(CastSession castSession, int statusCode) {
      Log.e(TAG, "Session start failed. Error code " + statusCode + ": "
          + CastUtils.getLogString(statusCode));
    }

    @Override
    public void onSessionEnding(CastSession castSession) {
      // Do nothing.
    }

    @Override
    public void onSessionResuming(CastSession castSession, String s) {
      // Do nothing.
    }

  }

  // Result callbacks hooks.

  private final class RepeatModeResultCallback implements ResultCallback<MediaChannelResult> {

    @Override
    public void onResult(MediaChannelResult result) {
      int statusCode = result.getStatus().getStatusCode();
      if (statusCode == CommonStatusCodes.SUCCESS) {
        int repeatMode = getRepeatMode();
        for (EventListener listener : listeners) {
          listener.onRepeatModeChanged(repeatMode);
        }
      } else {
        Log.e(TAG, "Set repeat mode failed. Error code " + statusCode + ": "
            + CastUtils.getLogString(statusCode));
      }
    }

  }

  private final class SeekResultCallback implements ResultCallback<MediaChannelResult> {

    @Override
    public void onResult(MediaChannelResult result) {
      int statusCode = result.getStatus().getStatusCode();
      if (statusCode == CommonStatusCodes.SUCCESS) {
        pendingSeekPositionMs = C.TIME_UNSET;
      } else if (statusCode == CastStatusCodes.REPLACED) {
        // A seek was executed before this one completed. Do nothing.
      } else {
        Log.e(TAG, "Seek failed. Error code " + statusCode + ": "
            + CastUtils.getLogString(statusCode));
      }
    }

  }

}
