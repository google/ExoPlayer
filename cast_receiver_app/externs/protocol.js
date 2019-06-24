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

/**
 * @fileoverview Externs for messages sent by a sender app in JSON format.
 *
 * Fields defined here are prevented from being renamed by the js compiler.
 *
 * @externs
 */

/**
 * An uri bundle with an uri and request parameters.
 *
 * @record
 */
class UriBundle {
  constructor() {
    /**
     * The URI.
     *
     * @type {string}
     */
    this.uri;

    /**
     * The request headers.
     *
     * @type {?Object<string,string>}
     */
    this.requestHeaders;
  }
}

/**
 * @record
 */
class  DrmScheme {
  constructor() {
    /**
     * The DRM UUID.
     *
     * @type {string}
     */
    this.uuid;

    /**
     * The license URI.
     *
     * @type {?UriBundle}
     */
    this.licenseServer;
  }
}

/**
 * @record
 */
class MediaItem {
  constructor() {
    /**
     * The uuid of the item.
     *
     * @type {string}
     */
    this.uuid;

    /**
     * The mime type.
     *
     * @type {string}
     */
    this.mimeType;

    /**
     * The media uri bundle.
     *
     * @type {!UriBundle}
     */
    this.media;

    /**
     * The DRM schemes.
     *
     * @type {!Array<!DrmScheme>}
     */
    this.drmSchemes;

    /**
     * The position to start playback from.
     *
     * @type {number}
     */
    this.startPositionUs;

    /**
     * The position at which to end playback.
     *
     * @type {number}
     */
    this.endPositionUs;

    /**
     * The title of the media item.
     *
     * @type {string}
     */
    this.title;

    /**
     * The description of the media item.
     *
     * @type {string}
     */
    this.description;
  }
}

/**
 * Constraint parameters for track selection.
 *
 * @record
 */
class TrackSelectionParameters {
  constructor() {
    /**
     * The preferred audio language.
     *
     * @type {string|undefined}
     */
    this.preferredAudioLanguage;

    /**
     * The preferred text language.
     *
     * @type {string|undefined}
     */
    this.preferredTextLanguage;

    /**
     * List of selection flags that are disabled for text track selections.
     *
     * @type {!Array<string>}
     */
    this.disabledTextTrackSelectionFlags;

    /**
     * Whether a text track with undetermined language should be selected if no
     * track with `preferredTextLanguage` is available, or if
     * `preferredTextLanguage` is unset.
     *
     * @type {boolean}
     */
    this.selectUndeterminedTextLanguage;
  }
}

/**
 * The PlaybackPosition defined by the position, the uuid of the media item and
 * the period id.
 *
 * @record
 */
class PlaybackPosition {
  constructor() {
    /**
     * The current playback position in milliseconds.
     *
     * @type {number}
     */
    this.positionMs;

    /**
     * The uuid of the media item.
     *
     * @type {string}
     */
    this.uuid;

    /**
     * The id of the currently playing period.
     *
     * @type {string}
     */
    this.periodId;

    /**
     * The reason of a position discontinuity if any.
     *
     * @type {?string}
     */
    this.discontinuityReason;
  }
}

/**
 * The playback parameters.
 *
 * @record
 */
class PlaybackParameters {
  constructor() {
    /**
     * The playback speed.
     *
     * @type {number}
     */
    this.speed;

    /**
     * The playback pitch.
     *
     * @type {number}
     */
    this.pitch;

    /**
     * Whether silence is skipped.
     *
     * @type {boolean}
     */
    this.skipSilence;
  }
}
/**
 * The player state.
 *
 * @record
 */
class PlayerState {
  constructor() {
    /**
     * The playback state.
     *
     * @type {string}
     */
    this.playbackState;

    /**
     * The playback parameters.
     *
     * @type {!PlaybackParameters}
     */
    this.playbackParameters;

    /**
     * Playback starts when ready if true.
     *
     * @type {boolean}
     */
    this.playWhenReady;

    /**
     * The current position within the media.
     *
     * @type {?PlaybackPosition}
     */
    this.playbackPosition;

    /**
     * The current window index.
     *
     * @type {number}
     */
    this.windowIndex;

    /**
     * The number of windows.
     *
     * @type {number}
     */
    this.windowCount;

    /**
     * The audio tracks.
     *
     * @type {!Array<string>}
     */
    this.audioTracks;

    /**
     * The video tracks in case of adaptive media.
     *
     * @type {!Array<!Object<string,*>>}
     */
    this.videoTracks;

    /**
     * The repeat mode.
     *
     * @type {string}
     */
    this.repeatMode;

    /**
     * Whether the shuffle mode is enabled.
     *
     * @type {boolean}
     */
    this.shuffleModeEnabled;

    /**
     * The playback order to use when shuffle mode is enabled.
     *
     * @type {!Array<number>}
     */
    this.shuffleOrder;

    /**
     * The queue of media items.
     *
     * @type {!Array<!MediaItem>}
     */
    this.mediaQueue;

    /**
     * The media item info of the queue items if available.
     *
     * @type {!Object<string, !MediaItemInfo>}
     */
    this.mediaItemsInfo;

    /**
     * The sequence number of the sender.
     *
     * @type {number}
     */
    this.sequenceNumber;

    /**
     * The player error.
     *
     * @type {?PlayerError}
     */
    this.error;
  }
}

/**
 * The error description.
 *
 * @record
 */
class PlayerError {
  constructor() {
    /**
     * The error message.
     *
     * @type {string}
     */
    this.message;

    /**
     * The error code.
     *
     * @type {number}
     */
    this.code;

    /**
     * The error category.
     *
     * @type {number}
     */
    this.category;
  }
}

/**
 * A period.
 *
 * @record
 */
class Period {
  constructor() {
    /**
     * The id of the period. Must be unique within a media item.
     *
     * @type {string}
     */
    this.id;

    /**
     * The duration of the period in microseconds.
     *
     * @type {number}
     */
    this.durationUs;
  }
}
/**
 * Holds dynamic information for a MediaItem.
 *
 * <p>Holds information related to preparation for a specific {@link MediaItem}.
 * Unprepared items are associated with an {@link #EMPTY} info object until
 * prepared.
 *
 * @record
 */
class MediaItemInfo {
  constructor() {
    /**
     * The duration of the window in microseconds.
     *
     * @type {number}
     */
    this.windowDurationUs;

    /**
     * The default start position relative to the start of the window in
     * microseconds.
     *
     * @type {number}
     */
    this.defaultStartPositionUs;

    /**
     * The periods conforming the media item.
     *
     * @type {!Array<!Period>}
     */
    this.periods;

    /**
     * The position of the window in the first period in microseconds.
     *
     * @type {number}
     */
    this.positionInFirstPeriodUs;

    /**
     * Whether it is possible to seek within the window.
     *
     * @type {boolean}
     */
    this.isSeekable;

    /**
     * Whether the window may change when the timeline is updated.
     *
     * @type {boolean}
     */
    this.isDynamic;
  }
}

/**
 * The message envelope send by a sender app.
 *
 * @record
 */
class ExoCastMessage {
  constructor() {
    /**
     * The clients message sequenec number.
     *
     * @type {number}
     */
    this.sequenceNumber;

    /**
     * The name of the method.
     *
     * @type {string}
     */
    this.method;

    /**
     * The arguments of the method.
     *
     * @type {!Object<string,*>}
     */
    this.args;
  }
};

