/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_OFF;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ONE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_ARGS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DESCRIPTION;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DISABLED_TEXT_TRACK_SELECTION_FLAGS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_DRM_SCHEMES;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_END_POSITION_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_INDEX;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_ITEMS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_LICENSE_SERVER;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_MEDIA;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_METHOD;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_MIME_TYPE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PITCH;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PLAY_WHEN_READY;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_POSITION_MS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PREFERRED_AUDIO_LANGUAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PREFERRED_TEXT_LANGUAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_PROTOCOL_VERSION;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_REPEAT_MODE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_REQUEST_HEADERS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_RESET;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SELECT_UNDETERMINED_TEXT_LANGUAGE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SEQUENCE_NUMBER;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SHUFFLE_MODE_ENABLED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SHUFFLE_ORDER;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SKIP_SILENCE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_SPEED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_START_POSITION_US;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_TITLE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_URI;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_UUID;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.KEY_UUIDS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_ADD_ITEMS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_MOVE_ITEM;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_ON_CLIENT_CONNECTED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_PREPARE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_REMOVE_ITEMS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_SEEK_TO;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_SET_PLAYBACK_PARAMETERS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_SET_PLAY_WHEN_READY;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_SET_REPEAT_MODE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_SET_SHUFFLE_MODE_ENABLED;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_SET_TRACK_SELECTION_PARAMETERS;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.METHOD_STOP;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.PROTOCOL_VERSION;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_REPEAT_MODE_OFF;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_REPEAT_MODE_ONE;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_SELECTION_FLAG_AUTOSELECT;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_SELECTION_FLAG_DEFAULT;
import static com.google.android.exoplayer2.ext.cast.ExoCastConstants.STR_SELECTION_FLAG_FORCED;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.cast.MediaItem.UriBundle;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// TODO(Internal b/118432277): Evaluate using a proto for sending to the receiver app.
/** A serializable message for operating a media player. */
public abstract class ExoCastMessage {

  /** Notifies the receiver app of the connection of a sender app to the message bus. */
  public static final class OnClientConnected extends ExoCastMessage {

    public OnClientConnected() {
      super(METHOD_ON_CLIENT_CONNECTED);
    }

    @Override
    protected JSONObject getArgumentsAsJsonObject() {
      // No arguments needed.
      return new JSONObject();
    }
  }

  /** Transitions the player out of {@link Player#STATE_IDLE}. */
  public static final class Prepare extends ExoCastMessage {

    public Prepare() {
      super(METHOD_PREPARE);
    }

    @Override
    protected JSONObject getArgumentsAsJsonObject() {
      // No arguments needed.
      return new JSONObject();
    }
  }

  /** Transitions the player to {@link Player#STATE_IDLE} and optionally resets its state. */
  public static final class Stop extends ExoCastMessage {

    /** Whether the player state should be reset. */
    public final boolean reset;

    public Stop(boolean reset) {
      super(METHOD_STOP);
      this.reset = reset;
    }

    @Override
    protected JSONObject getArgumentsAsJsonObject() throws JSONException {
      return new JSONObject().put(KEY_RESET, reset);
    }
  }

  /** Adds items to a media player queue. */
  public static final class AddItems extends ExoCastMessage {

    /**
     * The index at which the {@link #items} should be inserted. If {@link C#INDEX_UNSET}, the items
     * are appended to the queue.
     */
    public final int index;
    /** The {@link MediaItem items} to add to the media queue. */
    public final List<MediaItem> items;
    /**
     * The shuffle order to use for the media queue that results of adding the items to the queue.
     */
    public final ShuffleOrder shuffleOrder;

    /**
     * @param index See {@link #index}.
     * @param items See {@link #items}.
     * @param shuffleOrder See {@link #shuffleOrder}.
     */
    public AddItems(int index, List<MediaItem> items, ShuffleOrder shuffleOrder) {
      super(METHOD_ADD_ITEMS);
      this.index = index;
      this.items = Collections.unmodifiableList(new ArrayList<>(items));
      this.shuffleOrder = shuffleOrder;
    }

    @Override
    protected JSONObject getArgumentsAsJsonObject() throws JSONException {
      JSONObject arguments =
          new JSONObject()
              .put(KEY_ITEMS, getItemsAsJsonArray())
              .put(KEY_SHUFFLE_ORDER, getShuffleOrderAsJson(shuffleOrder));
      maybePutValue(arguments, KEY_INDEX, index, C.INDEX_UNSET);
      return arguments;
    }

    private JSONArray getItemsAsJsonArray() throws JSONException {
      JSONArray result = new JSONArray();
      for (MediaItem item : items) {
        result.put(mediaItemAsJsonObject(item));
      }
      return result;
    }
  }

  /** Moves an item in a player media queue. */
  public static final class MoveItem extends ExoCastMessage {

    /** The {@link MediaItem#uuid} of the item to move. */
    public final UUID uuid;
    /** The index in the queue to which the item should be moved. */
    public final int index;
    /** The shuffle order to use for the media queue that results of moving the item. */
    public ShuffleOrder shuffleOrder;

    /**
     * @param uuid See {@link #uuid}.
     * @param index See {@link #index}.
     * @param shuffleOrder See {@link #shuffleOrder}.
     */
    public MoveItem(UUID uuid, int index, ShuffleOrder shuffleOrder) {
      super(METHOD_MOVE_ITEM);
      this.uuid = uuid;
      this.index = index;
      this.shuffleOrder = shuffleOrder;
    }

    @Override
    protected JSONObject getArgumentsAsJsonObject() throws JSONException {
      return new JSONObject()
          .put(KEY_UUID, uuid)
          .put(KEY_INDEX, index)
          .put(KEY_SHUFFLE_ORDER, getShuffleOrderAsJson(shuffleOrder));
    }
  }

  /** Removes items from a player queue. */
  public static final class RemoveItems extends ExoCastMessage {

    /** The {@link MediaItem#uuid} of the items to remove from the queue. */
    public final List<UUID> uuids;

    /** @param uuids See {@link #uuids}. */
    public RemoveItems(List<UUID> uuids) {
      super(METHOD_REMOVE_ITEMS);
      this.uuids = Collections.unmodifiableList(new ArrayList<>(uuids));
    }

    @Override
    protected JSONObject getArgumentsAsJsonObject() throws JSONException {
      return new JSONObject().put(KEY_UUIDS, new JSONArray(uuids));
    }
  }

  /** See {@link Player#setPlayWhenReady(boolean)}. */
  public static final class SetPlayWhenReady extends ExoCastMessage {

    /** The {@link Player#setPlayWhenReady(boolean) playWhenReady} value to set. */
    public final boolean playWhenReady;

    /** @param playWhenReady See {@link #playWhenReady}. */
    public SetPlayWhenReady(boolean playWhenReady) {
      super(METHOD_SET_PLAY_WHEN_READY);
      this.playWhenReady = playWhenReady;
    }

    @Override
    protected JSONObject getArgumentsAsJsonObject() throws JSONException {
      return new JSONObject().put(KEY_PLAY_WHEN_READY, playWhenReady);
    }
  }

  /**
   * Sets the repeat mode of the media player.
   *
   * @see Player#setRepeatMode(int)
   */
  public static final class SetRepeatMode extends ExoCastMessage {

    /** The {@link Player#setRepeatMode(int) repeatMode} to set. */
    @Player.RepeatMode public final int repeatMode;

    /** @param repeatMode See {@link #repeatMode}. */
    public SetRepeatMode(@Player.RepeatMode int repeatMode) {
      super(METHOD_SET_REPEAT_MODE);
      this.repeatMode = repeatMode;
    }

    @Override
    protected JSONObject getArgumentsAsJsonObject() throws JSONException {
      return new JSONObject().put(KEY_REPEAT_MODE, repeatModeToString(repeatMode));
    }

    private static String repeatModeToString(@Player.RepeatMode int repeatMode) {
      switch (repeatMode) {
        case REPEAT_MODE_OFF:
          return STR_REPEAT_MODE_OFF;
        case REPEAT_MODE_ONE:
          return STR_REPEAT_MODE_ONE;
        case REPEAT_MODE_ALL:
          return STR_REPEAT_MODE_ALL;
        default:
          throw new AssertionError("Illegal repeat mode: " + repeatMode);
      }
    }
  }

  /**
   * Enables and disables shuffle mode in the media player.
   *
   * @see Player#setShuffleModeEnabled(boolean)
   */
  public static final class SetShuffleModeEnabled extends ExoCastMessage {

    /** The {@link Player#setShuffleModeEnabled(boolean) shuffleModeEnabled} value to set. */
    public boolean shuffleModeEnabled;

    /** @param shuffleModeEnabled See {@link #shuffleModeEnabled}. */
    public SetShuffleModeEnabled(boolean shuffleModeEnabled) {
      super(METHOD_SET_SHUFFLE_MODE_ENABLED);
      this.shuffleModeEnabled = shuffleModeEnabled;
    }

    @Override
    protected JSONObject getArgumentsAsJsonObject() throws JSONException {
      return new JSONObject().put(KEY_SHUFFLE_MODE_ENABLED, shuffleModeEnabled);
    }
  }

  /** See {@link Player#seekTo(int, long)}. */
  public static final class SeekTo extends ExoCastMessage {

    /** The {@link MediaItem#uuid} of the item to seek to. */
    public final UUID uuid;
    /**
     * The seek position in milliseconds in the specified item. If {@link C#TIME_UNSET}, the target
     * position is the item's default position.
     */
    public final long positionMs;

    /**
     * @param uuid See {@link #uuid}.
     * @param positionMs See {@link #positionMs}.
     */
    public SeekTo(UUID uuid, long positionMs) {
      super(METHOD_SEEK_TO);
      this.uuid = uuid;
      this.positionMs = positionMs;
    }

    @Override
    protected JSONObject getArgumentsAsJsonObject() throws JSONException {
      JSONObject result = new JSONObject().put(KEY_UUID, uuid);
      ExoCastMessage.maybePutValue(result, KEY_POSITION_MS, positionMs, C.TIME_UNSET);
      return result;
    }
  }

  /** See {@link Player#setPlaybackParameters(PlaybackParameters)}. */
  public static final class SetPlaybackParameters extends ExoCastMessage {

    /** The {@link Player#setPlaybackParameters(PlaybackParameters) parameters} to set. */
    public final PlaybackParameters playbackParameters;

    /** @param playbackParameters See {@link #playbackParameters}. */
    public SetPlaybackParameters(PlaybackParameters playbackParameters) {
      super(METHOD_SET_PLAYBACK_PARAMETERS);
      this.playbackParameters = playbackParameters;
    }

    @Override
    protected JSONObject getArgumentsAsJsonObject() throws JSONException {
      return new JSONObject()
          .put(KEY_SPEED, playbackParameters.speed)
          .put(KEY_PITCH, playbackParameters.pitch)
          .put(KEY_SKIP_SILENCE, playbackParameters.skipSilence);
    }
  }

  /** See {@link ExoCastPlayer#setTrackSelectionParameters(TrackSelectionParameters)}. */
  public static final class SetTrackSelectionParameters extends ExoCastMessage {

    /**
     * The {@link ExoCastPlayer#setTrackSelectionParameters(TrackSelectionParameters) parameters} to
     * set
     */
    public final TrackSelectionParameters trackSelectionParameters;

    public SetTrackSelectionParameters(TrackSelectionParameters trackSelectionParameters) {
      super(METHOD_SET_TRACK_SELECTION_PARAMETERS);
      this.trackSelectionParameters = trackSelectionParameters;
    }

    @Override
    protected JSONObject getArgumentsAsJsonObject() throws JSONException {
      JSONArray disabledTextSelectionFlagsJson = new JSONArray();
      int disabledSelectionFlags = trackSelectionParameters.disabledTextTrackSelectionFlags;
      if ((disabledSelectionFlags & C.SELECTION_FLAG_AUTOSELECT) != 0) {
        disabledTextSelectionFlagsJson.put(STR_SELECTION_FLAG_AUTOSELECT);
      }
      if ((disabledSelectionFlags & C.SELECTION_FLAG_FORCED) != 0) {
        disabledTextSelectionFlagsJson.put(STR_SELECTION_FLAG_FORCED);
      }
      if ((disabledSelectionFlags & C.SELECTION_FLAG_DEFAULT) != 0) {
        disabledTextSelectionFlagsJson.put(STR_SELECTION_FLAG_DEFAULT);
      }
      return new JSONObject()
          .put(KEY_PREFERRED_AUDIO_LANGUAGE, trackSelectionParameters.preferredAudioLanguage)
          .put(KEY_PREFERRED_TEXT_LANGUAGE, trackSelectionParameters.preferredTextLanguage)
          .put(KEY_DISABLED_TEXT_TRACK_SELECTION_FLAGS, disabledTextSelectionFlagsJson)
          .put(
              KEY_SELECT_UNDETERMINED_TEXT_LANGUAGE,
              trackSelectionParameters.selectUndeterminedTextLanguage);
    }
  }

  public final String method;

  /**
   * Creates a message with the given method.
   *
   * @param method The method of the message.
   */
  protected ExoCastMessage(String method) {
    this.method = method;
  }

  /**
   * Returns a string containing a JSON representation of this message.
   *
   * @param sequenceNumber The sequence number to associate with this message.
   * @return A string containing a JSON representation of this message.
   */
  public final String toJsonString(long sequenceNumber) {
    try {
      JSONObject message =
          new JSONObject()
              .put(KEY_PROTOCOL_VERSION, PROTOCOL_VERSION)
              .put(KEY_METHOD, method)
              .put(KEY_SEQUENCE_NUMBER, sequenceNumber)
              .put(KEY_ARGS, getArgumentsAsJsonObject());
      return message.toString();
    } catch (JSONException e) {
      throw new AssertionError(e);
    }
  }

  /** Returns a {@link JSONObject} representation of the given item. */
  protected static JSONObject mediaItemAsJsonObject(MediaItem item) throws JSONException {
    JSONObject itemAsJson = new JSONObject();
    itemAsJson.put(KEY_UUID, item.uuid);
    itemAsJson.put(KEY_TITLE, item.title);
    itemAsJson.put(KEY_DESCRIPTION, item.description);
    itemAsJson.put(KEY_MEDIA, uriBundleAsJsonObject(item.media));
    // TODO(Internal b/118431961): Add attachment management.

    JSONArray drmSchemesAsJson = new JSONArray();
    for (MediaItem.DrmScheme drmScheme : item.drmSchemes) {
      JSONObject drmSchemeAsJson = new JSONObject();
      drmSchemeAsJson.put(KEY_UUID, drmScheme.uuid);
      if (drmScheme.licenseServer != null) {
        drmSchemeAsJson.put(KEY_LICENSE_SERVER, uriBundleAsJsonObject(drmScheme.licenseServer));
      }
      drmSchemesAsJson.put(drmSchemeAsJson);
    }
    itemAsJson.put(KEY_DRM_SCHEMES, drmSchemesAsJson);
    maybePutValue(itemAsJson, KEY_START_POSITION_US, item.startPositionUs, C.TIME_UNSET);
    maybePutValue(itemAsJson, KEY_END_POSITION_US, item.endPositionUs, C.TIME_UNSET);
    itemAsJson.put(KEY_MIME_TYPE, item.mimeType);
    return itemAsJson;
  }

  /** Returns a {@link JSONObject JSON object} containing the arguments of the message. */
  protected abstract JSONObject getArgumentsAsJsonObject() throws JSONException;

  /** Returns a JSON representation of the given {@link UriBundle}. */
  protected static JSONObject uriBundleAsJsonObject(UriBundle uriBundle) throws JSONException {
    return new JSONObject()
        .put(KEY_URI, uriBundle.uri)
        .put(KEY_REQUEST_HEADERS, new JSONObject(uriBundle.requestHeaders));
  }

  private static JSONArray getShuffleOrderAsJson(ShuffleOrder shuffleOrder) {
    JSONArray shuffleOrderJson = new JSONArray();
    int index = shuffleOrder.getFirstIndex();
    while (index != C.INDEX_UNSET) {
      shuffleOrderJson.put(index);
      index = shuffleOrder.getNextIndex(index);
    }
    return shuffleOrderJson;
  }

  private static void maybePutValue(JSONObject target, String key, long value, long unsetValue)
      throws JSONException {
    if (value != unsetValue) {
      target.put(key, value);
    }
  }
}
