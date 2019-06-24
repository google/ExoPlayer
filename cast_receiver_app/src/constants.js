/**
 * Copyright (C) 2019 The Android Open Source Project
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

goog.module('exoplayer.cast.constants');

/**
 * The underyling player.
 *
 * @enum {number}
 */
const PlaybackType = {
  VIDEO_ELEMENT: 1,
  SHAKA_PLAYER: 2,
  UNKNOWN: 999,
};

/**
 * Supported mime types and their playback mode.
 *
 * @type {!Object<string, !PlaybackType>}
 */
const SUPPORTED_MIME_TYPES = Object.freeze({
  'application/dash+xml': PlaybackType.SHAKA_PLAYER,
  'application/vnd.apple.mpegurl': PlaybackType.SHAKA_PLAYER,
  'application/vnd.ms-sstr+xml': PlaybackType.SHAKA_PLAYER,
  'application/x-mpegURL': PlaybackType.SHAKA_PLAYER,
});

/**
 * Returns the playback type required for a given mime type, or
 * PlaybackType.UNKNOWN if the mime type is not recognized.
 *
 * @param {string} mimeType The mime type.
 * @return {!PlaybackType} The required playback type, or PlaybackType.UNKNOWN
 *     if the mime type is not recognized.
 */
const getPlaybackType = function(mimeType) {
  if (mimeType.startsWith('video/') || mimeType.startsWith('audio/')) {
    return PlaybackType.VIDEO_ELEMENT;
  } else {
    return SUPPORTED_MIME_TYPES[mimeType] || PlaybackType.UNKNOWN;
  }
};

/**
 * Error messages.
 *
 * @enum {string}
 */
const ErrorMessages = {
  SHAKA_LOAD_ERROR: 'Error while loading media with Shaka.',
  SHAKA_UNKNOWN_ERROR: 'Shaka error event captured.',
  MEDIA_ELEMENT_UNKNOWN_ERROR: 'Media element error event captured.',
  UNKNOWN_FATAL_ERROR: 'Fatal playback error. Shaka instance replaced.',
  UNKNOWN_ERROR: 'Unknown error',
};

/**
 * ExoPlayer's repeat modes.
 *
 * @enum {string}
 */
const RepeatMode = {
  OFF: 'OFF',
  ONE: 'ONE',
  ALL: 'ALL',
};

/**
 * Error categories. Error categories coming from Shaka are defined in [Shaka
 * source
 * code](https://shaka-player-demo.appspot.com/docs/api/shaka.util.Error.html).
 *
 * @enum {number}
 */
const ErrorCategory = {
  MEDIA_ELEMENT: 0,
  FATAL_SHAKA_ERROR: 1000,
};

/**
 * An error object to be used if no media error is assigned to the `error`
 * field of the media element when an error event is fired
 *
 * @type {!PlayerError}
 */
const UNKNOWN_ERROR = /** @type {!PlayerError} */ (Object.freeze({
  message: ErrorMessages.UNKNOWN_ERROR,
  code: 0,
  category: 0,
}));

/**
 * UUID for the Widevine DRM scheme.
 *
 * @type {string}
 */
const WIDEVINE_UUID = 'edef8ba9-79d6-4ace-a3c8-27dcd51d21ed';

/**
 * UUID for the PlayReady DRM scheme.
 *
 * @type {string}
 */
const PLAYREADY_UUID = '9a04f079-9840-4286-ab92-e65be0885f95';

/** @type {!Object<string, string>} */
const drmSystems = {};
drmSystems[WIDEVINE_UUID] = 'com.widevine.alpha';
drmSystems[PLAYREADY_UUID] = 'com.microsoft.playready';

/**
 * The uuids of the supported DRM systems.
 *
 * @type {!Object<string, string>}
 */
const DRM_SYSTEMS = Object.freeze(drmSystems);

exports.PlaybackType = PlaybackType;
exports.ErrorMessages = ErrorMessages;
exports.ErrorCategory = ErrorCategory;
exports.RepeatMode = RepeatMode;
exports.getPlaybackType = getPlaybackType;
exports.WIDEVINE_UUID = WIDEVINE_UUID;
exports.PLAYREADY_UUID = PLAYREADY_UUID;
exports.DRM_SYSTEMS = DRM_SYSTEMS;
exports.UNKNOWN_ERROR = UNKNOWN_ERROR;
