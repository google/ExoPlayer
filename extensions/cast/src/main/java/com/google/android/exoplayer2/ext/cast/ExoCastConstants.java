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

/** Defines constants used by the Cast extension. */
public final class ExoCastConstants {

  private ExoCastConstants() {}

  public static final int PROTOCOL_VERSION = 0;

  // String representations.

  public static final String STR_STATE_IDLE = "IDLE";
  public static final String STR_STATE_BUFFERING = "BUFFERING";
  public static final String STR_STATE_READY = "READY";
  public static final String STR_STATE_ENDED = "ENDED";

  public static final String STR_REPEAT_MODE_OFF = "REPEAT_MODE_OFF";
  public static final String STR_REPEAT_MODE_ONE = "REPEAT_MODE_ONE";
  public static final String STR_REPEAT_MODE_ALL = "REPEAT_MODE_ALL";

  // Methods.

  public static final String METHOD_BASE = "player.";

  public static final String METHOD_ADD_ITEMS = METHOD_BASE + "addItems";
  public static final String METHOD_MOVE_ITEM = METHOD_BASE + "moveItem";
  public static final String METHOD_REMOVE_ITEMS = METHOD_BASE + "removeItems";
  public static final String METHOD_SET_PLAY_WHEN_READY = METHOD_BASE + "setPlayWhenReady";
  public static final String METHOD_SET_REPEAT_MODE = METHOD_BASE + "setRepeatMode";
  public static final String METHOD_SET_SHUFFLE_MODE_ENABLED =
      METHOD_BASE + "setShuffleModeEnabled";
  public static final String METHOD_SEEK_TO = METHOD_BASE + "seekTo";
  public static final String METHOD_SET_PLAYBACK_PARAMETERS = METHOD_BASE + "setPlaybackParameters";

  // JSON message keys.

  public static final String KEY_PROTOCOL_VERSION = "protocolVersion";
  public static final String KEY_METHOD = "method";
  public static final String KEY_SEQUENCE_NUMBER = "sequenceNumber";
  public static final String KEY_INDEX = "index";
  public static final String KEY_ITEMS = "items";
  public static final String KEY_ARGS = "args";
  public static final String KEY_UUID = "uuid";
  public static final String KEY_UUIDS = "uuids";
  public static final String KEY_LICENSE_SERVER = "licenseServer";
  public static final String KEY_TITLE = "title";
  public static final String KEY_URI = "uri";
  public static final String KEY_REQUEST_HEADERS = "requestHeaders";
  public static final String KEY_DESCRIPTION = "description";
  public static final String KEY_MEDIA = "media";
  public static final String KEY_DRM_SCHEMES = "drmSchemes";
  public static final String KEY_START_POSITION_US = "startPositionUs";
  public static final String KEY_END_POSITION_US = "endPositionUs";
  public static final String KEY_MIME_TYPE = "mimeType";
  public static final String KEY_PLAY_WHEN_READY = "playWhenReady";
  public static final String KEY_REPEAT_MODE = "repeatMode";
  public static final String KEY_SHUFFLE_MODE_ENABLED = "shuffleModeEnabled";
  public static final String KEY_POSITION_MS = "positionMs";
  public static final String KEY_SPEED = "speed";
  public static final String KEY_PITCH = "pitch";
  public static final String KEY_SKIP_SILENCE = "skipSilence";
  public static final String KEY_PLAYBACK_STATE = "playbackState";
  public static final String KEY_MEDIA_QUEUE = "mediaQueue";
  public static final String KEY_IS_LOADING = "isLoading";
  public static final String KEY_PLAYBACK_ERROR = "error";
  public static final String KEY_PLAYBACK_POSITION = "playbackPosition";
  public static final String KEY_PLAYBACK_PARAMETERS = "playbackParameters";
}
