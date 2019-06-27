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
package com.google.android.exoplayer2.ext.cast;

import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_AD_INSERTION;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_INTERNAL;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_PERIOD_TRANSITION;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_OFF;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ONE;
import static com.google.android.exoplayer2.Player.STATE_BUFFERING;
import static com.google.android.exoplayer2.Player.STATE_ENDED;
import static com.google.android.exoplayer2.Player.STATE_IDLE;
import static com.google.android.exoplayer2.Player.STATE_READY;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DEFAULT_START_POSITION_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DESCRIPTION;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DISABLED_TEXT_TRACK_SELECTION_FLAGS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DISCONTINUITY_REASON;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DRM_SCHEMES;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DURATION_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_END_POSITION_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_ERROR_MESSAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_ID;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_IS_DYNAMIC;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_IS_LOADING;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_IS_SEEKABLE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_LICENSE_SERVER;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_MEDIA;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_MEDIA_ITEMS_INFO;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_MEDIA_QUEUE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_MIME_TYPE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PERIODS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PERIOD_ID;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PITCH;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PLAYBACK_PARAMETERS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PLAYBACK_POSITION;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PLAYBACK_STATE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PLAY_WHEN_READY;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_POSITION_IN_FIRST_PERIOD_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_POSITION_MS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PREFERRED_AUDIO_LANGUAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PREFERRED_TEXT_LANGUAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_REPEAT_MODE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_REQUEST_HEADERS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SELECT_UNDETERMINED_TEXT_LANGUAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SEQUENCE_NUMBER;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SHUFFLE_MODE_ENABLED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SHUFFLE_ORDER;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SKIP_SILENCE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SPEED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_START_POSITION_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_TITLE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_TRACK_SELECTION_PARAMETERS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_URI;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_UUID;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_WINDOW_DURATION_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_DISCONTINUITY_REASON_AD_INSERTION;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_DISCONTINUITY_REASON_INTERNAL;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_DISCONTINUITY_REASON_PERIOD_TRANSITION;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_DISCONTINUITY_REASON_SEEK;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_REPEAT_MODE_OFF;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_REPEAT_MODE_ONE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_STATE_BUFFERING;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_STATE_ENDED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_STATE_IDLE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_STATE_READY;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Holds a playback state update from the receiver app. */
public final class ReceiverAppStateUpdate {

  /** Builder for {@link ReceiverAppStateUpdate}. */
  public static final class Builder {

    private final long sequenceNumber;
    private @MonotonicNonNull Boolean playWhenReady;
    private @MonotonicNonNull Integer playbackState;
    private @MonotonicNonNull List<MediaItem> items;
    private @MonotonicNonNull Integer repeatMode;
    private @MonotonicNonNull Boolean shuffleModeEnabled;
    private @MonotonicNonNull Boolean isLoading;
    private @MonotonicNonNull PlaybackParameters playbackParameters;
    private @MonotonicNonNull TrackSelectionParameters trackSelectionParameters;
    private @MonotonicNonNull String errorMessage;
    private @MonotonicNonNull Integer discontinuityReason;
    private @MonotonicNonNull UUID currentPlayingItemUuid;
    private @MonotonicNonNull String currentPlayingPeriodId;
    private @MonotonicNonNull Long currentPlaybackPositionMs;
    private @MonotonicNonNull List<Integer> shuffleOrder;
    private Map<UUID, MediaItemInfo> mediaItemsInformation;

    private Builder(long sequenceNumber) {
      this.sequenceNumber = sequenceNumber;
      mediaItemsInformation = Collections.emptyMap();
    }

    /** See {@link ReceiverAppStateUpdate#playWhenReady}. */
    public Builder setPlayWhenReady(Boolean playWhenReady) {
      this.playWhenReady = playWhenReady;
      return this;
    }

    /** See {@link ReceiverAppStateUpdate#playbackState}. */
    public Builder setPlaybackState(Integer playbackState) {
      this.playbackState = playbackState;
      return this;
    }

    /** See {@link ReceiverAppStateUpdate#items}. */
    public Builder setItems(List<MediaItem> items) {
      this.items = Collections.unmodifiableList(items);
      return this;
    }

    /** See {@link ReceiverAppStateUpdate#repeatMode}. */
    public Builder setRepeatMode(Integer repeatMode) {
      this.repeatMode = repeatMode;
      return this;
    }

    /** See {@link ReceiverAppStateUpdate#shuffleModeEnabled}. */
    public Builder setShuffleModeEnabled(Boolean shuffleModeEnabled) {
      this.shuffleModeEnabled = shuffleModeEnabled;
      return this;
    }

    /** See {@link ReceiverAppStateUpdate#isLoading}. */
    public Builder setIsLoading(Boolean isLoading) {
      this.isLoading = isLoading;
      return this;
    }

    /** See {@link ReceiverAppStateUpdate#playbackParameters}. */
    public Builder setPlaybackParameters(PlaybackParameters playbackParameters) {
      this.playbackParameters = playbackParameters;
      return this;
    }

    /** See {@link ReceiverAppStateUpdate#trackSelectionParameters} */
    public Builder setTrackSelectionParameters(TrackSelectionParameters trackSelectionParameters) {
      this.trackSelectionParameters = trackSelectionParameters;
      return this;
    }

    /** See {@link ReceiverAppStateUpdate#errorMessage}. */
    public Builder setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    /** See {@link ReceiverAppStateUpdate#discontinuityReason}. */
    public Builder setDiscontinuityReason(Integer discontinuityReason) {
      this.discontinuityReason = discontinuityReason;
      return this;
    }

    /**
     * See {@link ReceiverAppStateUpdate#currentPlayingItemUuid} and {@link
     * ReceiverAppStateUpdate#currentPlaybackPositionMs}.
     */
    public Builder setPlaybackPosition(
        UUID currentPlayingItemUuid,
        String currentPlayingPeriodId,
        Long currentPlaybackPositionMs) {
      this.currentPlayingItemUuid = currentPlayingItemUuid;
      this.currentPlayingPeriodId = currentPlayingPeriodId;
      this.currentPlaybackPositionMs = currentPlaybackPositionMs;
      return this;
    }

    /**
     * See {@link ReceiverAppStateUpdate#currentPlayingItemUuid} and {@link
     * ReceiverAppStateUpdate#currentPlaybackPositionMs}.
     */
    public Builder setMediaItemsInformation(Map<UUID, MediaItemInfo> mediaItemsInformation) {
      this.mediaItemsInformation = Collections.unmodifiableMap(mediaItemsInformation);
      return this;
    }

    /** See {@link ReceiverAppStateUpdate#shuffleOrder}. */
    public Builder setShuffleOrder(List<Integer> shuffleOrder) {
      this.shuffleOrder = Collections.unmodifiableList(shuffleOrder);
      return this;
    }

    /**
     * Returns a new {@link ReceiverAppStateUpdate} instance with the current values in this
     * builder.
     */
    public ReceiverAppStateUpdate build() {
      return new ReceiverAppStateUpdate(
          sequenceNumber,
          playWhenReady,
          playbackState,
          items,
          repeatMode,
          shuffleModeEnabled,
          isLoading,
          playbackParameters,
          trackSelectionParameters,
          errorMessage,
          discontinuityReason,
          currentPlayingItemUuid,
          currentPlayingPeriodId,
          currentPlaybackPositionMs,
          mediaItemsInformation,
          shuffleOrder);
    }
  }

  /** Returns a {@link ReceiverAppStateUpdate} builder. */
  public static Builder builder(long sequenceNumber) {
    return new Builder(sequenceNumber);
  }

  /**
   * Creates an instance from parsing a state update received from the Receiver App.
   *
   * @param jsonMessage The state update encoded as a JSON string.
   * @return The parsed state update.
   * @throws JSONException If an error is encountered when parsing the {@code jsonMessage}.
   */
  public static ReceiverAppStateUpdate fromJsonMessage(String jsonMessage) throws JSONException {
    JSONObject stateAsJson = new JSONObject(jsonMessage);
    Builder builder = builder(stateAsJson.getLong(KEY_SEQUENCE_NUMBER));

    if (stateAsJson.has(KEY_PLAY_WHEN_READY)) {
      builder.setPlayWhenReady(stateAsJson.getBoolean(KEY_PLAY_WHEN_READY));
    }

    if (stateAsJson.has(KEY_PLAYBACK_STATE)) {
      builder.setPlaybackState(
          playbackStateStringToConstant(stateAsJson.getString(KEY_PLAYBACK_STATE)));
    }

    if (stateAsJson.has(KEY_MEDIA_QUEUE)) {
      builder.setItems(
          toMediaItemArrayList(Assertions.checkNotNull(stateAsJson.optJSONArray(KEY_MEDIA_QUEUE))));
    }

    if (stateAsJson.has(KEY_REPEAT_MODE)) {
      builder.setRepeatMode(stringToRepeatMode(stateAsJson.getString(KEY_REPEAT_MODE)));
    }

    if (stateAsJson.has(KEY_SHUFFLE_MODE_ENABLED)) {
      builder.setShuffleModeEnabled(stateAsJson.getBoolean(KEY_SHUFFLE_MODE_ENABLED));
    }

    if (stateAsJson.has(KEY_IS_LOADING)) {
      builder.setIsLoading(stateAsJson.getBoolean(KEY_IS_LOADING));
    }

    if (stateAsJson.has(KEY_PLAYBACK_PARAMETERS)) {
      builder.setPlaybackParameters(
          toPlaybackParameters(
              Assertions.checkNotNull(stateAsJson.optJSONObject(KEY_PLAYBACK_PARAMETERS))));
    }

    if (stateAsJson.has(KEY_TRACK_SELECTION_PARAMETERS)) {
      JSONObject trackSelectionParametersJson =
          stateAsJson.getJSONObject(KEY_TRACK_SELECTION_PARAMETERS);
      TrackSelectionParameters parameters =
          TrackSelectionParameters.DEFAULT
              .buildUpon()
              .setPreferredTextLanguage(
                  trackSelectionParametersJson.getString(KEY_PREFERRED_TEXT_LANGUAGE))
              .setPreferredAudioLanguage(
                  trackSelectionParametersJson.getString(KEY_PREFERRED_AUDIO_LANGUAGE))
              .setSelectUndeterminedTextLanguage(
                  trackSelectionParametersJson.getBoolean(KEY_SELECT_UNDETERMINED_TEXT_LANGUAGE))
              .setDisabledTextTrackSelectionFlags(
                  jsonArrayToSelectionFlags(
                      trackSelectionParametersJson.getJSONArray(
                          KEY_DISABLED_TEXT_TRACK_SELECTION_FLAGS)))
              .build();
      builder.setTrackSelectionParameters(parameters);
    }

    if (stateAsJson.has(KEY_ERROR_MESSAGE)) {
      builder.setErrorMessage(stateAsJson.getString(KEY_ERROR_MESSAGE));
    }

    if (stateAsJson.has(KEY_PLAYBACK_POSITION)) {
      JSONObject playbackPosition = stateAsJson.getJSONObject(KEY_PLAYBACK_POSITION);
      String discontinuityReason = playbackPosition.optString(KEY_DISCONTINUITY_REASON);
      if (!discontinuityReason.isEmpty()) {
        builder.setDiscontinuityReason(stringToDiscontinuityReason(discontinuityReason));
      }
      UUID currentPlayingItemUuid = UUID.fromString(playbackPosition.getString(KEY_UUID));
      String currentPlayingPeriodId = playbackPosition.getString(KEY_PERIOD_ID);
      Long currentPlaybackPositionMs = playbackPosition.getLong(KEY_POSITION_MS);
      builder.setPlaybackPosition(
          currentPlayingItemUuid, currentPlayingPeriodId, currentPlaybackPositionMs);
    }

    if (stateAsJson.has(KEY_MEDIA_ITEMS_INFO)) {
      HashMap<UUID, MediaItemInfo> mediaItemInformation = new HashMap<>();
      JSONObject mediaItemsInfo = stateAsJson.getJSONObject(KEY_MEDIA_ITEMS_INFO);
      for (Iterator<String> i = mediaItemsInfo.keys(); i.hasNext(); ) {
        String key = i.next();
        mediaItemInformation.put(
            UUID.fromString(key), jsonToMediaitemInfo(mediaItemsInfo.getJSONObject(key)));
      }
      builder.setMediaItemsInformation(mediaItemInformation);
    }

    if (stateAsJson.has(KEY_SHUFFLE_ORDER)) {
      ArrayList<Integer> shuffleOrder = new ArrayList<>();
      JSONArray shuffleOrderJson = stateAsJson.getJSONArray(KEY_SHUFFLE_ORDER);
      for (int i = 0; i < shuffleOrderJson.length(); i++) {
        shuffleOrder.add(shuffleOrderJson.getInt(i));
      }
      builder.setShuffleOrder(shuffleOrder);
    }

    return builder.build();
  }

  /** The sequence number of the status update. */
  public final long sequenceNumber;
  /** Optional {@link Player#getPlayWhenReady playWhenReady} value. */
  @Nullable public final Boolean playWhenReady;
  /** Optional {@link Player#getPlaybackState() playbackState}. */
  @Nullable public final Integer playbackState;
  /** Optional list of media items. */
  @Nullable public final List<MediaItem> items;
  /** Optional {@link Player#getRepeatMode() repeatMode}. */
  @Nullable public final Integer repeatMode;
  /** Optional {@link Player#getShuffleModeEnabled() shuffleMode}. */
  @Nullable public final Boolean shuffleModeEnabled;
  /** Optional {@link Player#isLoading() isLoading} value. */
  @Nullable public final Boolean isLoading;
  /** Optional {@link Player#getPlaybackParameters() playbackParameters}. */
  @Nullable public final PlaybackParameters playbackParameters;
  /** Optional {@link TrackSelectionParameters}. */
  @Nullable public final TrackSelectionParameters trackSelectionParameters;
  /** Optional error message string. */
  @Nullable public final String errorMessage;
  /**
   * Optional reason for a {@link Player.EventListener#onPositionDiscontinuity(int) discontinuity }
   * in the playback position.
   */
  @Nullable public final Integer discontinuityReason;
  /** Optional {@link UUID} of the {@link Player#getCurrentWindowIndex() currently played item}. */
  @Nullable public final UUID currentPlayingItemUuid;
  /** Optional id of the current {@link Player#getCurrentPeriodIndex() period being played}. */
  @Nullable public final String currentPlayingPeriodId;
  /** Optional {@link Player#getCurrentPosition() playbackPosition} in milliseconds. */
  @Nullable public final Long currentPlaybackPositionMs;
  /** Holds information about the {@link MediaItem media items} in the media queue. */
  public final Map<UUID, MediaItemInfo> mediaItemsInformation;
  /** Holds the indices of the media queue items in shuffle order. */
  @Nullable public final List<Integer> shuffleOrder;

  /** Creates an instance with the given values. */
  private ReceiverAppStateUpdate(
      long sequenceNumber,
      @Nullable Boolean playWhenReady,
      @Nullable Integer playbackState,
      @Nullable List<MediaItem> items,
      @Nullable Integer repeatMode,
      @Nullable Boolean shuffleModeEnabled,
      @Nullable Boolean isLoading,
      @Nullable PlaybackParameters playbackParameters,
      @Nullable TrackSelectionParameters trackSelectionParameters,
      @Nullable String errorMessage,
      @Nullable Integer discontinuityReason,
      @Nullable UUID currentPlayingItemUuid,
      @Nullable String currentPlayingPeriodId,
      @Nullable Long currentPlaybackPositionMs,
      Map<UUID, MediaItemInfo> mediaItemsInformation,
      @Nullable List<Integer> shuffleOrder) {
    this.sequenceNumber = sequenceNumber;
    this.playWhenReady = playWhenReady;
    this.playbackState = playbackState;
    this.items = items;
    this.repeatMode = repeatMode;
    this.shuffleModeEnabled = shuffleModeEnabled;
    this.isLoading = isLoading;
    this.playbackParameters = playbackParameters;
    this.trackSelectionParameters = trackSelectionParameters;
    this.errorMessage = errorMessage;
    this.discontinuityReason = discontinuityReason;
    this.currentPlayingItemUuid = currentPlayingItemUuid;
    this.currentPlayingPeriodId = currentPlayingPeriodId;
    this.currentPlaybackPositionMs = currentPlaybackPositionMs;
    this.mediaItemsInformation = mediaItemsInformation;
    this.shuffleOrder = shuffleOrder;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ReceiverAppStateUpdate that = (ReceiverAppStateUpdate) other;

    return sequenceNumber == that.sequenceNumber
        && Util.areEqual(playWhenReady, that.playWhenReady)
        && Util.areEqual(playbackState, that.playbackState)
        && Util.areEqual(items, that.items)
        && Util.areEqual(repeatMode, that.repeatMode)
        && Util.areEqual(shuffleModeEnabled, that.shuffleModeEnabled)
        && Util.areEqual(isLoading, that.isLoading)
        && Util.areEqual(playbackParameters, that.playbackParameters)
        && Util.areEqual(trackSelectionParameters, that.trackSelectionParameters)
        && Util.areEqual(errorMessage, that.errorMessage)
        && Util.areEqual(discontinuityReason, that.discontinuityReason)
        && Util.areEqual(currentPlayingItemUuid, that.currentPlayingItemUuid)
        && Util.areEqual(currentPlayingPeriodId, that.currentPlayingPeriodId)
        && Util.areEqual(currentPlaybackPositionMs, that.currentPlaybackPositionMs)
        && Util.areEqual(mediaItemsInformation, that.mediaItemsInformation)
        && Util.areEqual(shuffleOrder, that.shuffleOrder);
  }

  @Override
  public int hashCode() {
    int result = (int) (sequenceNumber ^ (sequenceNumber >>> 32));
    result = 31 * result + (playWhenReady != null ? playWhenReady.hashCode() : 0);
    result = 31 * result + (playbackState != null ? playbackState.hashCode() : 0);
    result = 31 * result + (items != null ? items.hashCode() : 0);
    result = 31 * result + (repeatMode != null ? repeatMode.hashCode() : 0);
    result = 31 * result + (shuffleModeEnabled != null ? shuffleModeEnabled.hashCode() : 0);
    result = 31 * result + (isLoading != null ? isLoading.hashCode() : 0);
    result = 31 * result + (playbackParameters != null ? playbackParameters.hashCode() : 0);
    result =
        31 * result + (trackSelectionParameters != null ? trackSelectionParameters.hashCode() : 0);
    result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
    result = 31 * result + (discontinuityReason != null ? discontinuityReason.hashCode() : 0);
    result = 31 * result + (currentPlayingItemUuid != null ? currentPlayingItemUuid.hashCode() : 0);
    result = 31 * result + (currentPlayingPeriodId != null ? currentPlayingPeriodId.hashCode() : 0);
    result =
        31 * result
            + (currentPlaybackPositionMs != null ? currentPlaybackPositionMs.hashCode() : 0);
    result = 31 * result + mediaItemsInformation.hashCode();
    result = 31 * result + (shuffleOrder != null ? shuffleOrder.hashCode() : 0);
    return result;
  }

  // Internal methods.

  @VisibleForTesting
  /* package */ static List<MediaItem> toMediaItemArrayList(JSONArray mediaItemsAsJson)
      throws JSONException {
    ArrayList<MediaItem> mediaItems = new ArrayList<>();
    for (int i = 0; i < mediaItemsAsJson.length(); i++) {
      mediaItems.add(toMediaItem(mediaItemsAsJson.getJSONObject(i)));
    }
    return mediaItems;
  }

  private static MediaItem toMediaItem(JSONObject mediaItemAsJson) throws JSONException {
    MediaItem.Builder builder = new MediaItem.Builder();
    builder.setUuid(UUID.fromString(mediaItemAsJson.getString(KEY_UUID)));
    builder.setTitle(mediaItemAsJson.getString(KEY_TITLE));
    builder.setDescription(mediaItemAsJson.getString(KEY_DESCRIPTION));
    builder.setMedia(jsonToUriBundle(mediaItemAsJson.getJSONObject(KEY_MEDIA)));
    // TODO(Internal b/118431961): Add attachment management.

    builder.setDrmSchemes(jsonArrayToDrmSchemes(mediaItemAsJson.getJSONArray(KEY_DRM_SCHEMES)));
    if (mediaItemAsJson.has(KEY_START_POSITION_US)) {
      builder.setStartPositionUs(mediaItemAsJson.getLong(KEY_START_POSITION_US));
    }
    if (mediaItemAsJson.has(KEY_END_POSITION_US)) {
      builder.setEndPositionUs(mediaItemAsJson.getLong(KEY_END_POSITION_US));
    }
    builder.setMimeType(mediaItemAsJson.getString(KEY_MIME_TYPE));
    return builder.build();
  }

  private static PlaybackParameters toPlaybackParameters(JSONObject parameters)
      throws JSONException {
    float speed = (float) parameters.getDouble(KEY_SPEED);
    float pitch = (float) parameters.getDouble(KEY_PITCH);
    boolean skipSilence = parameters.getBoolean(KEY_SKIP_SILENCE);
    return new PlaybackParameters(speed, pitch, skipSilence);
  }

  private static int playbackStateStringToConstant(String string) {
    switch (string) {
      case STR_STATE_IDLE:
        return STATE_IDLE;
      case STR_STATE_BUFFERING:
        return STATE_BUFFERING;
      case STR_STATE_READY:
        return STATE_READY;
      case STR_STATE_ENDED:
        return STATE_ENDED;
      default:
        throw new AssertionError("Unexpected state string: " + string);
    }
  }

  private static Integer stringToRepeatMode(String repeatModeStr) {
    switch (repeatModeStr) {
      case STR_REPEAT_MODE_OFF:
        return REPEAT_MODE_OFF;
      case STR_REPEAT_MODE_ONE:
        return REPEAT_MODE_ONE;
      case STR_REPEAT_MODE_ALL:
        return REPEAT_MODE_ALL;
      default:
        throw new AssertionError("Illegal repeat mode: " + repeatModeStr);
    }
  }

  private static Integer stringToDiscontinuityReason(String discontinuityReasonStr) {
    switch (discontinuityReasonStr) {
      case STR_DISCONTINUITY_REASON_PERIOD_TRANSITION:
        return DISCONTINUITY_REASON_PERIOD_TRANSITION;
      case STR_DISCONTINUITY_REASON_SEEK:
        return DISCONTINUITY_REASON_SEEK;
      case STR_DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
        return DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
      case STR_DISCONTINUITY_REASON_AD_INSERTION:
        return DISCONTINUITY_REASON_AD_INSERTION;
      case STR_DISCONTINUITY_REASON_INTERNAL:
        return DISCONTINUITY_REASON_INTERNAL;
      default:
        throw new AssertionError("Illegal discontinuity reason: " + discontinuityReasonStr);
    }
  }

  @C.SelectionFlags
  private static int jsonArrayToSelectionFlags(JSONArray array) throws JSONException {
    int result = 0;
    for (int i = 0; i < array.length(); i++) {
      switch (array.getString(i)) {
        case ExoCastConstants.STR_SELECTION_FLAG_AUTOSELECT:
          result |= C.SELECTION_FLAG_AUTOSELECT;
          break;
        case ExoCastConstants.STR_SELECTION_FLAG_FORCED:
          result |= C.SELECTION_FLAG_FORCED;
          break;
        case ExoCastConstants.STR_SELECTION_FLAG_DEFAULT:
          result |= C.SELECTION_FLAG_DEFAULT;
          break;
        default:
          // Do nothing.
          break;
      }
    }
    return result;
  }

  private static List<MediaItem.DrmScheme> jsonArrayToDrmSchemes(JSONArray drmSchemesAsJson)
      throws JSONException {
    ArrayList<MediaItem.DrmScheme> drmSchemes = new ArrayList<>();
    for (int i = 0; i < drmSchemesAsJson.length(); i++) {
      JSONObject drmSchemeAsJson = drmSchemesAsJson.getJSONObject(i);
      MediaItem.UriBundle uriBundle =
          drmSchemeAsJson.has(KEY_LICENSE_SERVER)
              ? jsonToUriBundle(drmSchemeAsJson.getJSONObject(KEY_LICENSE_SERVER))
              : null;
      drmSchemes.add(
          new MediaItem.DrmScheme(UUID.fromString(drmSchemeAsJson.getString(KEY_UUID)), uriBundle));
    }
    return Collections.unmodifiableList(drmSchemes);
  }

  private static MediaItem.UriBundle jsonToUriBundle(JSONObject json) throws JSONException {
    Uri uri = Uri.parse(json.getString(KEY_URI));
    JSONObject requestHeadersAsJson = json.getJSONObject(KEY_REQUEST_HEADERS);
    HashMap<String, String> requestHeaders = new HashMap<>();
    for (Iterator<String> i = requestHeadersAsJson.keys(); i.hasNext(); ) {
      String key = i.next();
      requestHeaders.put(key, requestHeadersAsJson.getString(key));
    }
    return new MediaItem.UriBundle(uri, requestHeaders);
  }

  private static MediaItemInfo jsonToMediaitemInfo(JSONObject json) throws JSONException {
    long durationUs = json.getLong(KEY_WINDOW_DURATION_US);
    long defaultPositionUs = json.optLong(KEY_DEFAULT_START_POSITION_US, /* fallback= */ 0L);
    JSONArray periodsJson = json.getJSONArray(KEY_PERIODS);
    ArrayList<MediaItemInfo.Period> periods = new ArrayList<>();
    long positionInFirstPeriodUs = json.getLong(KEY_POSITION_IN_FIRST_PERIOD_US);

    long windowPositionUs = -positionInFirstPeriodUs;
    for (int i = 0; i < periodsJson.length(); i++) {
      JSONObject periodJson = periodsJson.getJSONObject(i);
      long periodDurationUs = periodJson.optLong(KEY_DURATION_US, C.TIME_UNSET);
      periods.add(
          new MediaItemInfo.Period(
              periodJson.getString(KEY_ID), periodDurationUs, windowPositionUs));
      windowPositionUs += periodDurationUs;
    }
    boolean isDynamic = json.getBoolean(KEY_IS_DYNAMIC);
    boolean isSeekable = json.getBoolean(KEY_IS_SEEKABLE);
    return new MediaItemInfo(
        durationUs, defaultPositionUs, periods, positionInFirstPeriodUs, isSeekable, isDynamic);
  }
}
