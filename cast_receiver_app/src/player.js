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

goog.module('exoplayer.cast.Player');

const ConfigurationFactory = goog.require('exoplayer.cast.ConfigurationFactory');
const NetworkingEngine = goog.require('shaka.net.NetworkingEngine');
const ShakaError = goog.require('shaka.util.Error');
const ShakaPlayer = goog.require('shaka.Player');
const asserts = goog.require('goog.dom.asserts');
const googArray = goog.require('goog.array');
const safedom = goog.require('goog.dom.safe');
const {ErrorMessages, ErrorCategory, PlaybackType, RepeatMode, getPlaybackType, UNKNOWN_ERROR} = goog.require('exoplayer.cast.constants');
const {UuidComparator, createUuidComparator, log} = goog.require('exoplayer.cast.util');
const {assert, fail} = goog.require('goog.asserts');
const {clamp} = goog.require('goog.math');

/**
 * Value indicating that no window index is currently set.
 */
const INDEX_UNSET = -1;

/**
 * Estimated time for processing the manifest after download in millisecconds.
 *
 * See: https://github.com/google/shaka-player/issues/1734
 */
const MANIFEST_PROCESSING_ESTIMATE_MS = 350;

/**
 * Media element events to listen to.
 *
 * @enum {string}
 */
const MediaElementEvent = {
  ERROR: 'error',
  LOADED_DATA: 'loadeddata',
  PAUSE: 'pause',
  PLAYING: 'playing',
  SEEKED: 'seeked',
  SEEKING: 'seeking',
  WAITING: 'waiting',
};

/**
 * Shaka events to listen to.
 *
 * @enum {string}
 */
const ShakaEvent = {
  ERROR: 'error',
  STREAMING: 'streaming',
  TRACKS_CHANGED: 'trackschanged',
};

/**
 * ExoPlayer's playback states.
 *
 * @enum {string}
 */
const PlaybackState = {
  IDLE: 'IDLE',
  BUFFERING: 'BUFFERING',
  READY: 'READY',
  ENDED: 'ENDED',
};

/**
 * ExoPlayer's position discontinuity reasons.
 *
 * @enum {string}
 */
const DiscontinuityReason = {
  PERIOD_TRANSITION: 'PERIOD_TRANSITION',
  SEEK: 'SEEK',
};

/**
 * A dummy `MediaIteminfo` to be used while the actual period is not
 * yet available.
 *
 * @const
 * @type {!MediaItemInfo}
 */
const DUMMY_MEDIA_ITEM_INFO = Object.freeze({
  isSeekable: false,
  isDynamic: true,
  positionInFirstPeriodUs: 0,
  defaultStartPositionUs: 0,
  windowDurationUs: 0,
  periods: [{
    id: 1,
    durationUs: 0,
  }],
});

/**
 * The Player wraps a Shaka player and maintains a queue of media items.
 *
 * After construction the player is in `IDLE` state. Calling `#prepare` prepares
 * the player with the queue item at the given window index and position. The
 * state transitions to `BUFFERING`. When 'playWhenReady' is set to `true`
 * playback start when the player becomes 'READY'.
 *
 * When the player needs to rebuffer the state goes to 'BUFFERING' and becomes
 * 'READY' again when playback can be resumed.
 *
 * The state transitions to `ENDED` when playback reached the end of the last
 * item in the queue, when the last item has been removed from the queue if
 * `!IDLE`, or when `prepare` is called with an empty queue. Seeking makes the
 * player transition away from `ENDED` again.
 *
 * When `#stop` is called or when a fatal playback error occurs, the player
 * transition to `IDLE` state and needs to be prepared again to resume playback.
 *
 * `playWhenReady`, `repeatMode`, `shuffleModeEnabled` can be manipulated in any
 * state, just as media items can be added, moved and removed.
 *
 * @constructor
 * @param {!ShakaPlayer} shakaPlayer The shaka player to wrap.
 * @param {!ConfigurationFactory} configurationFactory A factory to create a
 *     configuration for the Shaka player.
 */
const Player = function(shakaPlayer, configurationFactory) {
  /** @private @const {?HTMLMediaElement} */
  this.videoElement_ = shakaPlayer.getMediaElement();
  /** @private @const {!ConfigurationFactory} */
  this.configurationFactory_ = configurationFactory;
  /** @private @const {!Array<function(!PlayerState): undefined>} */
  this.playerListeners_ = [];
  /**
   * @private
   * @const
   * {?function(NetworkingEngine.RequestType<number>, (?|null))}
   */
  this.manifestResponseFilter_ = (type, response) => {
    if (type === NetworkingEngine.RequestType.MANIFEST) {
      setTimeout(() => {
        this.updateWindowMediaItemInfo_();
        this.invalidate();
      }, MANIFEST_PROCESSING_ESTIMATE_MS);
    }
  };

  /** @private {!ShakaPlayer} */
  this.shakaPlayer_ = shakaPlayer;
  /** @private {boolean} */
  this.playWhenReady_ = false;
  /** @private {boolean} */
  this.shuffleModeEnabled_ = false;
  /** @private {!RepeatMode} */
  this.repeatMode_ = RepeatMode.OFF;
  /** @private {!TrackSelectionParameters} */
  this.trackSelectionParameters_ = /** @type {!TrackSelectionParameters} */ ({
    preferredAudioLanguage: '',
    preferredTextLanguage: '',
    disabledTextTrackSelectionFlags: [],
    selectUndeterminedTextLanguage: false,
  });
  /** @private {number} */
  this.windowIndex_ = INDEX_UNSET;
  /** @private {!Array<!MediaItem>} */
  this.queue_ = [];
  /** @private {!Object<string, number>} */
  this.queueUuidIndexMap_ = {};
  /** @private {!UuidComparator} */
  this.uuidComparator_ = createUuidComparator(this.queueUuidIndexMap_);

  /** @private {!PlaybackState} */
  this.playbackState_ = PlaybackState.IDLE;
  /** @private {!MediaItemInfo} */
  this.windowMediaItemInfo_ = DUMMY_MEDIA_ITEM_INFO;
  /** @private {number} */
  this.windowPeriodIndex_ = 0;
  /** @private {!Object<string, !MediaItemInfo>} */
  this.mediaItemInfoMap_ = {};
  /** @private {?PlayerError} */
  this.playbackError_ = null;
  /** @private {?DiscontinuityReason} */
  this.discontinuityReason_ = null;
  /** @private {!Array<number>} */
  this.shuffleOrder_ = [];
  /** @private {number} */
  this.shuffleIndex_ = 0;
  /** @private {!PlaybackType} */
  this.playbackType_ = PlaybackType.UNKNOWN;
  /** @private {boolean} */
  this.isManifestFilterRegistered_ = false;
  /** @private {?string} */
  this.uuidToPrepare_ = null;

  if (!this.shakaPlayer_ || !this.videoElement_) {
    throw new Error('an instance of Shaka player with a media element ' +
        'attached to it needs to be passed to the constructor.');
  }

  /** @private @const {function(!Event)} */
  this.playbackStateListener_ = (ev) => {
    log(['handle event: ', ev.type]);
    let invalid = false;
    switch (ev.type) {
      case ShakaEvent.STREAMING: {
        // Arrives once after prepare when the manifest is available.
        const uuid = this.queue_[this.windowIndex_].uuid;
        const cachedMediaItemInfo = this.mediaItemInfoMap_[uuid];
        if (!cachedMediaItemInfo || cachedMediaItemInfo.isDynamic) {
          this.updateWindowMediaItemInfo_();
          if (this.windowMediaItemInfo_.isDynamic) {
            this.registerManifestResponseFilter_();
          }
          invalid = true;
        }
        break;
      }
      case ShakaEvent.TRACKS_CHANGED: {
        // Arrives when tracks have changed either initially or at a period
        // boundary.
        const periods = this.windowMediaItemInfo_.periods;
        const previousPeriodIndex = this.windowPeriodIndex_;
        this.evaluateAndSetCurrentPeriod_(periods);
        invalid = previousPeriodIndex !== this.windowPeriodIndex_;
        if (periods.length && this.windowPeriodIndex_ > 0) {
          // Player transitions to next period in multiperiod stream.
          this.discontinuityReason_ = this.discontinuityReason_ ||
              DiscontinuityReason.PERIOD_TRANSITION;
          invalid = true;
        }
        if (this.videoElement_.paused && this.playWhenReady_) {
          this.videoElement_.play();
        }
        break;
      }
      case MediaElementEvent.LOADED_DATA: {
        // Arrives once when the first frame has been rendered.
        if (this.playbackType_ === PlaybackType.VIDEO_ELEMENT) {
          const uuid = this.queue_[this.windowIndex_].uuid;
          let mediaItemInfo = this.mediaItemInfoMap_[uuid];
          if (!mediaItemInfo || mediaItemInfo.isDynamic) {
            mediaItemInfo = this.buildMediaItemInfoFromElement_();
            if (mediaItemInfo !== null) {
              this.mediaItemInfoMap_[uuid] = mediaItemInfo;
              this.windowMediaItemInfo_ = mediaItemInfo;
            }
          }
          this.evaluateAndSetCurrentPeriod_(mediaItemInfo.periods);
          invalid = true;
        }
        if (this.videoElement_.paused && this.playWhenReady_) {
          // Restart after automatic skip to next queue item.
          this.videoElement_.play();
        } else if (this.videoElement_.paused) {
          // If paused, the PLAYING event will not be fired, hence we transition
          // to state READY right here.
          this.playbackState_ = PlaybackState.READY;
          invalid = true;
        }
        break;
      }
      case MediaElementEvent.WAITING:
      case MediaElementEvent.SEEKING: {
        // Arrives at a user seek or when re-buffering starts.
        if (this.playbackState_ !== PlaybackState.BUFFERING) {
          this.playbackState_ = PlaybackState.BUFFERING;
          invalid = true;
        }
        break;
      }
      case MediaElementEvent.PLAYING:
      case MediaElementEvent.SEEKED: {
        // Arrives at the end of a user seek or after re-buffering.
        if (this.playbackState_ !== PlaybackState.READY) {
          this.playbackState_ = PlaybackState.READY;
          invalid = true;
        }
        break;
      }
      case MediaElementEvent.PAUSE: {
        // Detects end of media and either skips to next or transitions to ended
        // state.
        if (this.videoElement_.ended) {
          let nextWindowIndex = this.getNextWindowIndex();
          if (nextWindowIndex !== INDEX_UNSET) {
            this.seekToWindowInternal_(nextWindowIndex, undefined);
          } else {
            this.playbackState_ = PlaybackState.ENDED;
            invalid = true;
          }
        }
        break;
      }
    }
    if (invalid) {
      this.invalidate();
    }
  };
  /** @private @const {function(!Event)} */
  this.mediaElementErrorHandler_ = (ev) => {
    console.error('Media element error reported in handler');
    this.playbackError_ = !this.videoElement_.error ? UNKNOWN_ERROR : {
      message: this.videoElement_.error.message,
      code: this.videoElement_.error.code,
      category: ErrorCategory.MEDIA_ELEMENT,
    };
    this.playbackState_ = PlaybackState.IDLE;
    this.uuidToPrepare_ = this.queue_[this.windowIndex_] ?
        this.queue_[this.windowIndex_].uuid :
        null;
    this.invalidate();
  };
  /** @private @const {function(!Event)} */
  this.shakaErrorHandler_ = (ev) => {
    const shakaError = /** @type {!ShakaError} */ (ev['detail']);
    if (shakaError.severity !== ShakaError.Severity.RECOVERABLE) {
      this.fatalShakaError_(shakaError, 'Shaka error reported by error event');
      this.invalidate();
    } else {
      console.error('Recoverable Shaka error reported in handler');
    }
  };

  this.shakaPlayer_.addEventListener(
      ShakaEvent.STREAMING, this.playbackStateListener_);
  this.shakaPlayer_.addEventListener(
      ShakaEvent.TRACKS_CHANGED, this.playbackStateListener_);

  this.videoElement_.addEventListener(
      MediaElementEvent.LOADED_DATA, this.playbackStateListener_);
  this.videoElement_.addEventListener(
      MediaElementEvent.WAITING, this.playbackStateListener_);
  this.videoElement_.addEventListener(
      MediaElementEvent.PLAYING, this.playbackStateListener_);
  this.videoElement_.addEventListener(
      MediaElementEvent.PAUSE, this.playbackStateListener_);
  this.videoElement_.addEventListener(
      MediaElementEvent.SEEKING, this.playbackStateListener_);
  this.videoElement_.addEventListener(
      MediaElementEvent.SEEKED, this.playbackStateListener_);

  // Attach error handlers.
  this.shakaPlayer_.addEventListener(ShakaEvent.ERROR, this.shakaErrorHandler_);
  this.videoElement_.addEventListener(
      MediaElementEvent.ERROR, this.mediaElementErrorHandler_);
};

/**
 * Adds a listener to the player.
 *
 * @param {function(!PlayerState)} listener The player listener.
 */
Player.prototype.addPlayerListener = function(listener) {
  this.playerListeners_.push(listener);
};

/**
 * Removes a listener.
 *
 * @param {function(!Object)} listener The player listener.
 */
Player.prototype.removePlayerListener = function(listener) {
  for (let i = 0; i < this.playerListeners_.length; i++) {
    if (this.playerListeners_[i] === listener) {
      this.playerListeners_.splice(i, 1);
      break;
    }
  }
};

/**
 * Gets the current PlayerState.
 *
 * @return {!PlayerState}
 */
Player.prototype.getPlayerState = function() {
  return this.buildPlayerState_();
};

/**
 * Sends the current playback state to clients.
 */
Player.prototype.invalidate = function() {
  const playbackState = this.buildPlayerState_();
  for (let i = 0; i < this.playerListeners_.length; i++) {
    this.playerListeners_[i](playbackState);
  }
};

/**
 * Get the audio tracks.
 *
 * @return {!Array<string>} An array with the track names}.
 */
Player.prototype.getAudioTracks = function() {
  return this.windowMediaItemInfo_ !== DUMMY_MEDIA_ITEM_INFO ?
      this.shakaPlayer_.getAudioLanguages() :
      [];
};

/**
 * Gets the video tracks.
 *
 * @return {!Array<!Object>} An array with the video tracks.
 */
Player.prototype.getVideoTracks = function() {
  return this.windowMediaItemInfo_ !== DUMMY_MEDIA_ITEM_INFO ?
      this.shakaPlayer_.getVariantTracks() :
      [];
};

/**
 * Gets the playback state.
 *
 * @return {!PlaybackState} The playback state.
 */
Player.prototype.getPlaybackState = function() {
  return this.playbackState_;
};

/**
 * Gets the playback error if any.
 *
 * @return {?Object} The playback error.
 */
Player.prototype.getPlaybackError = function() {
  return this.playbackError_;
};

/**
 * Gets the duration in milliseconds or a negative value if unknown.
 *
 * @return {number} The duration in milliseconds.
 */
Player.prototype.getDurationMs = function() {
  return this.windowMediaItemInfo_ ?
      this.windowMediaItemInfo_.windowDurationUs / 1000 : -1;
};

/**
 * Gets the current position in milliseconds or a negative value if not known.
 *
 * @return {number} The current position in milliseconds.
 */
Player.prototype.getCurrentPositionMs = function() {
  if (!this.videoElement_.currentTime) {
    return 0;
  }
  return (this.videoElement_.currentTime * 1000) -
      (this.windowMediaItemInfo_.positionInFirstPeriodUs / 1000);
};

/**
 * Gets the current window index.
 *
 * @return {number} The current window index.
 */
Player.prototype.getCurrentWindowIndex = function() {
  if (this.playbackState_ === PlaybackState.IDLE) {
    return this.queueUuidIndexMap_[this.uuidToPrepare_ || ''] || 0;
  }
  return Math.max(0, this.windowIndex_);
};

/**
 * Gets the media item of the current window or null if the queue is empty.
 *
 * @return {?MediaItem} The media item of the current window.
 */
Player.prototype.getCurrentMediaItem = function() {
  return this.windowIndex_ >= 0 ? this.queue_[this.windowIndex_] : null;
};

/**
 * Gets the media item info of the current window index or null if not yet
 * available.
 *
 * @return {?MediaItemInfo} The current media item info or undefined.
 */
Player.prototype.getCurrentMediaItemInfo = function () {
  return this.windowMediaItemInfo_;
};

/**
 * Gets the text tracks.
 *
 * @return {!TextTrackList} The text tracks.
 */
Player.prototype.getTextTracks = function() {
  return this.videoElement_.textTracks;
};

/**
 * Gets whether the player should play when ready.
 *
 * @return {boolean} True when it plays when ready.
 */
Player.prototype.getPlayWhenReady = function() {
  return this.playWhenReady_;
};

/**
 * Sets whether to play when ready.
 *
 * @param {boolean} playWhenReady Whether to play when ready.
 * @return {boolean} Whether calling this method causes a change of the player
 *     state.
 */
Player.prototype.setPlayWhenReady = function(playWhenReady) {
  if (this.playWhenReady_ === playWhenReady) {
    return false;
  }
  this.playWhenReady_ = playWhenReady;
  this.invalidate();
  if (this.playbackState_ === PlaybackState.IDLE ||
      this.playbackState_ === PlaybackState.ENDED) {
    return true;
  }
  if (this.playWhenReady_) {
    this.videoElement_.play();
  } else {
    this.videoElement_.pause();
  }
  return true;
};

/**
 * Gets the repeat mode.
 *
 * @return {!RepeatMode} The repeat mode.
 */
Player.prototype.getRepeatMode = function() {
  return this.repeatMode_;
};

/**
 * Sets the repeat mode. Must be a value of the enum Player.RepeatMode.
 *
 * @param {!RepeatMode} mode The repeat mode.
 * @return {boolean} Whether calling this method causes a change of the player
 *     state.
 */
Player.prototype.setRepeatMode = function(mode) {
  if (this.repeatMode_ === mode) {
    return false;
  }
  if (mode === Player.RepeatMode.OFF ||
      mode === Player.RepeatMode.ONE ||
      mode === Player.RepeatMode.ALL) {
    this.repeatMode_ = mode;
  } else {
    throw new Error('illegal repeat mode: ' + mode);
  }
  this.invalidate();
  return true;
};

/**
 * Enables or disables the shuffle mode.
 *
 * @param {boolean} enabled Whether the shuffle mode is enabled or not.
 * @return {boolean} Whether calling this method causes a change of the player
 *     state.
 */
Player.prototype.setShuffleModeEnabled = function(enabled) {
  if (this.shuffleModeEnabled_ === enabled) {
    return false;
  }
  this.shuffleModeEnabled_ = enabled;
  this.invalidate();
  return true;
};

/**
 * Sets the track selection parameters.
 *
 * @param {!TrackSelectionParameters} trackSelectionParameters The parameters.
 * @return {boolean} Whether calling this method causes a change of the player
 *     state.
 */
Player.prototype.setTrackSelectionParameters = function(
    trackSelectionParameters) {
  this.trackSelectionParameters_ = trackSelectionParameters;
  /** @type {!PlayerConfiguration} */
  const configuration = /** @type {!PlayerConfiguration} */ ({});
  this.configurationFactory_.mapLanguageConfiguration(
      trackSelectionParameters, configuration);
  /** @type {!PlayerConfiguration} */
  const currentConfiguration = this.shakaPlayer_.getConfiguration();
  /** @type {boolean} */
  let isStateChange = false;
  if (currentConfiguration.preferredAudioLanguage !==
      configuration.preferredAudioLanguage) {
    this.shakaPlayer_.selectAudioLanguage(configuration.preferredAudioLanguage);
    isStateChange = true;
  }
  if (currentConfiguration.preferredTextLanguage !==
      configuration.preferredTextLanguage) {
    this.shakaPlayer_.selectTextLanguage(configuration.preferredTextLanguage);
    isStateChange = true;
  }
  return isStateChange;
};

/**
 * Gets the previous window index or a negative number if no item previous to
 * the current item is available.
 *
 * @return {number} The previous window index or a negative number if the
 *     current item is the first item.
 */
Player.prototype.getPreviousWindowIndex = function() {
  if (this.playbackType_ === PlaybackType.UNKNOWN) {
    return INDEX_UNSET;
  }
  switch (this.repeatMode_) {
    case RepeatMode.ONE:
      return this.windowIndex_;
    case RepeatMode.ALL:
      if (this.shuffleModeEnabled_) {
        const previousIndex = this.shuffleIndex_ > 0 ?
            this.shuffleIndex_ - 1 : this.queue_.length - 1;
        return this.shuffleOrder_[previousIndex];
      } else {
        const previousIndex = this.windowIndex_ > 0 ?
            this.windowIndex_ - 1 : this.queue_.length - 1;
        return previousIndex;
      }
      break;
    case RepeatMode.OFF:
      if (this.shuffleModeEnabled_) {
        const previousIndex = this.shuffleIndex_ - 1;
        return previousIndex < 0 ? -1 : this.shuffleOrder_[previousIndex];
      } else {
        const previousIndex = this.windowIndex_ - 1;
        return previousIndex < 0 ? -1 : previousIndex;
      }
      break;
    default:
      throw new Error('illegal state of repeat mode: ' + this.repeatMode_);
  }
};

/**
 * Gets the next window index or a negative number if the current item is the
 * last item.
 *
 * @return {number} The next window index or a negative number if the current
 *     item is the last item.
 */
Player.prototype.getNextWindowIndex = function() {
  if (this.playbackType_ === PlaybackType.UNKNOWN) {
    return INDEX_UNSET;
  }
  switch (this.repeatMode_) {
    case RepeatMode.ONE:
      return this.windowIndex_;
    case RepeatMode.ALL:
      if (this.shuffleModeEnabled_) {
        const nextIndex = (this.shuffleIndex_ + 1) % this.queue_.length;
        return this.shuffleOrder_[nextIndex];
      } else {
        return (this.windowIndex_ + 1) % this.queue_.length;
      }
      break;
    case RepeatMode.OFF:
      if (this.shuffleModeEnabled_) {
        const nextIndex = this.shuffleIndex_ + 1;
        return nextIndex < this.shuffleOrder_.length ?
            this.shuffleOrder_[nextIndex] : -1;
      } else {
        const nextIndex = this.windowIndex_ + 1;
        return nextIndex < this.queue_.length ? nextIndex : -1;
      }
      break;
    default:
      throw new Error('illegal state of repeat mode: ' + this.repeatMode_);
  }
};

/**
 * Gets whether the current window is seekable.
 *
 * @return {boolean} True if seekable.
 */
Player.prototype.isCurrentWindowSeekable = function() {
  return !!this.videoElement_.seekable;
};

/**
 * Seeks to the positionMs of the media item with the given uuid.
 *
 * @param {string} uuid The uuid of the media item to seek to.
 * @param {number|undefined} positionMs The position in milliseconds to seek to.
 * @return {boolean} True if a seek operation has been processed, false
 *     otherwise.
 */
Player.prototype.seekToUuid = function(uuid, positionMs) {
  if (this.playbackState_ === PlaybackState.IDLE) {
    this.uuidToPrepare_ = uuid;
    this.videoElement_.currentTime =
        this.getPosition_(positionMs, INDEX_UNSET) / 1000;
    this.invalidate();
    return true;
  }
  const windowIndex = this.queueUuidIndexMap_[uuid];
  if (windowIndex !== undefined) {
    positionMs = this.getPosition_(positionMs, windowIndex);
    this.discontinuityReason_ = DiscontinuityReason.SEEK;
    this.seekToWindowInternal_(windowIndex, positionMs);
    return true;
  }
  return false;
};

/**
 * Seeks to the positionMs of the given window.
 *
 * The index must be a valid index of the current queue, else this method does
 * nothing.
 *
 * @param {number} windowIndex The index of the window to seek to.
 * @param {number|undefined} positionMs The position to seek to within the
 *     window.
 */
Player.prototype.seekToWindow = function(windowIndex, positionMs) {
  if (windowIndex < 0 || windowIndex >= this.queue_.length) {
    return;
  }
  this.seekToUuid(this.queue_[windowIndex].uuid, positionMs);
};

/**
 * Gets the number of media items in the queue.
 *
 * @return {number} The size of the queue.
 */
Player.prototype.getQueueSize = function() {
  return this.queue_.length;
};

/**
 * Adds an array of items at the given index of the queue.
 *
 * Items are expected to have been validated with `validation#validateMediaItem`
 * or `validation#validateMediaItems` before being passed to this method.
 *
 * @param {number} index The index where to insert the media item.
 * @param {!Array<!MediaItem>} mediaItems The media items.
 * @param {!Array<number>|undefined} shuffleOrder The new shuffle order.
 * @return {number} The number of added items.
 */
Player.prototype.addQueueItems = function(index, mediaItems, shuffleOrder) {
  if (index < 0 || mediaItems.length === 0) {
    return 0;
  }
  let addedItemCount = 0;
  index = Math.min(this.queue_.length, index);
  mediaItems.forEach((itemToAdd) => {
    if (this.queueUuidIndexMap_[itemToAdd.uuid] === undefined) {
      this.queue_.splice(index + addedItemCount, 0, itemToAdd);
      this.queueUuidIndexMap_[itemToAdd.uuid] = index + addedItemCount;
      addedItemCount++;
    }
  });
  if (addedItemCount === 0) {
    return 0;
  }
  this.buildUuidIndexMap_(index + addedItemCount);
  this.setShuffleOrder_(shuffleOrder);
  if (this.queue_.length === addedItemCount) {
    this.windowIndex_ = 0;
    this.updateShuffleIndex_();
  } else if (
      index <= this.windowIndex_ &&
      this.playbackType_ !== PlaybackType.UNKNOWN) {
    this.windowIndex_ += mediaItems.length;
    this.updateShuffleIndex_();
  }
  this.invalidate();
  return addedItemCount;
};

/**
 * Removes the queue items with the given uuids.
 *
 * @param {!Array<string>} uuids The uuids of the queue items to remove.
 * @return {number} The number of items removed from the queue.
 */
Player.prototype.removeQueueItems = function(uuids) {
  let currentWindowRemoved = false;
  let lowestIndexRemoved = this.queue_.length - 1;
  const initialQueueSize = this.queue_.length;
  // Sort in descending order to start removing from the end.
  uuids = uuids.sort(this.uuidComparator_);
  uuids.forEach((uuid) => {
    const indexToRemove = this.queueUuidIndexMap_[uuid];
    if (indexToRemove === undefined) {
      return;
    }
    // Remove the item from the queue.
    this.queue_.splice(indexToRemove, 1);
    // Remove the corresponding media item info.
    delete this.mediaItemInfoMap_[uuid];
    // Remove the mapping to the window index.
    delete this.queueUuidIndexMap_[uuid];
    lowestIndexRemoved = Math.min(lowestIndexRemoved, indexToRemove);
    currentWindowRemoved =
        currentWindowRemoved || indexToRemove === this.windowIndex_;
    // The window index needs to be decreased when the item which has been
    // removed was before the current item, when the current item at the last
    // position has been removed, or when the queue has been emptied.
    if (indexToRemove < this.windowIndex_ ||
        (indexToRemove === this.windowIndex_ &&
         indexToRemove === this.queue_.length) ||
        this.queue_.length === 0) {
      this.windowIndex_--;
    }
    // Adjust the shuffle order.
    let shuffleIndexToRemove;
    this.shuffleOrder_.forEach((windowIndex, index) => {
      if (windowIndex > indexToRemove) {
        // Decrease the index in the shuffle order.
        this.shuffleOrder_[index]--;
      } else if (windowIndex === indexToRemove) {
        // Recall index for removal after traversing.
        shuffleIndexToRemove = index;
      }
    });
    // Remove the shuffle order entry of the removed item.
    this.shuffleOrder_.splice(shuffleIndexToRemove, 1);
  });
  const removedItemsCount = initialQueueSize - this.queue_.length;
  if (removedItemsCount === 0) {
    return 0;
  }
  this.updateShuffleIndex_();
  this.buildUuidIndexMap_(lowestIndexRemoved);
  if (currentWindowRemoved) {
    if (this.queue_.length === 0) {
      this.playbackState_ = this.playbackState_ === PlaybackState.IDLE ?
          PlaybackState.IDLE :
          PlaybackState.ENDED;
      this.windowMediaItemInfo_ = DUMMY_MEDIA_ITEM_INFO;
      this.windowPeriodIndex_ = 0;
      this.videoElement_.currentTime = 0;
      this.uuidToPrepare_ = null;
      this.unregisterManifestResponseFilter_();
      this.unload_(/** reinitialiseMediaSource= */ true);
    } else if (this.windowIndex_ >= 0) {
      const windowIndexToPrepare = this.windowIndex_;
      this.windowIndex_ = INDEX_UNSET;
      this.seekToWindowInternal_(windowIndexToPrepare, undefined);
      return removedItemsCount;
    }
  }
  this.invalidate();
  return removedItemsCount;
};

/**
 * Move the queue item with the given id to the given position.
 *
 * @param {string} uuid The uuid of the queue item to move.
 * @param {number} to The position to move the item to.
 * @param {!Array<number>|undefined} shuffleOrder The new shuffle order.
 * @return {boolean} Whether the item has been moved.
 */
Player.prototype.moveQueueItem = function(uuid, to, shuffleOrder) {
  if (to < 0 || to >= this.queue_.length) {
    return false;
  }
  const windowIndex = this.queueUuidIndexMap_[uuid];
  if (windowIndex === undefined) {
    return false;
  }
  const itemMoved = this.moveInQueue_(windowIndex, to);
  if (itemMoved) {
    this.setShuffleOrder_(shuffleOrder);
    this.invalidate();
  }
  return itemMoved;
};

/**
 * Prepares the player at the current window index and position.
 *
 * The playback state immediately transitions to `BUFFERING`. If the queue
 * is empty the player transitions to `ENDED`.
 */
Player.prototype.prepare = function() {
  if (this.queue_.length === 0) {
    this.uuidToPrepare_ = null;
    this.playbackState_ = PlaybackState.ENDED;
    this.invalidate();
    return;
  }
  if (this.uuidToPrepare_) {
    this.windowIndex_ =
        this.queueUuidIndexMap_[this.uuidToPrepare_] || INDEX_UNSET;
    this.uuidToPrepare_ = null;
  }
  this.windowIndex_ = clamp(this.windowIndex_, 0, this.queue_.length - 1);
  this.prepare_(this.getCurrentPositionMs());
  this.invalidate();
};

/**
 * Stops the player.
 *
 * Calling this method causes the player to transition into `IDLE` state.
 * If `reset` is `true` the player is reset to the initial state of right
 * after construction. If `reset` is `false`, the media queue is preserved
 * and calling `prepare()` results in resuming the player state to what it
 * was before calling `#stop(false)`.
 *
 * @param {boolean} reset Whether the state should be reset.
 * @return {!Promise<undefined>} A promise which resolves after async unload
 *     tasks have finished.
 */
Player.prototype.stop = function(reset) {
  this.playbackState_ = PlaybackState.IDLE;
  this.playbackError_ = null;
  this.discontinuityReason_ = null;
  this.unregisterManifestResponseFilter_();
  this.uuidToPrepare_ = this.uuidToPrepare_ || (this.queue_[this.windowIndex_] ?
      this.queue_[this.windowIndex_].uuid :
      null);
  if (reset) {
    this.uuidToPrepare_ = null;
    this.queue_ = [];
    this.queueUuidIndexMap_ = {};
    this.uuidComparator_ = createUuidComparator(this.queueUuidIndexMap_);
    this.windowIndex_ = INDEX_UNSET;
    this.mediaItemInfoMap_ = {};
    this.windowMediaItemInfo_ = DUMMY_MEDIA_ITEM_INFO;
    this.windowPeriodIndex_ = 0;
    this.videoElement_.currentTime = 0;
    this.shuffleOrder_ = [];
    this.shuffleIndex_ = 0;
  }
  this.invalidate();
  return this.unload_(/** reinitialiseMediaSource= */ !reset);
};

/**
 * Resets player and media element.
 *
 * @private
 * @param {boolean} reinitialiseMediaSource Whether the media source should be
 *    reinitialized.
 * @return {!Promise<undefined>} A promise which resolves after async unload
 *     tasks have finished.
 */
Player.prototype.unload_ = function(reinitialiseMediaSource) {
  const playbackTypeToUnload = this.playbackType_;
  this.playbackType_ = PlaybackType.UNKNOWN;
  switch (playbackTypeToUnload) {
    case PlaybackType.VIDEO_ELEMENT:
      this.videoElement_.removeAttribute('src');
      this.videoElement_.load();
      return Promise.resolve();
    case PlaybackType.SHAKA_PLAYER:
      return new Promise((resolve, reject) => {
        this.shakaPlayer_.unload(reinitialiseMediaSource)
            .then(resolve)
            .catch(resolve);
      });
    default:
      return Promise.resolve();
  }
};

/**
 * Releases the current Shaka instance and create a new one.
 *
 * This function should only be called if the Shaka instance is out of order due
 * to https://github.com/google/shaka-player/issues/1785. It assumes the current
 * Shaka instance has fallen into a state in which promises returned by
 * `shakaPlayer.load` and `shakaPlayer.unload` do not resolve nor are they
 * rejected anymore.
 *
 * @private
 */
Player.prototype.replaceShaka_ = function() {
  // Remove all listeners.
  this.shakaPlayer_.removeEventListener(
      ShakaEvent.STREAMING, this.playbackStateListener_);
  this.shakaPlayer_.removeEventListener(
      ShakaEvent.TRACKS_CHANGED, this.playbackStateListener_);
  this.shakaPlayer_.removeEventListener(
      ShakaEvent.ERROR, this.shakaErrorHandler_);
  // Unregister response filter if any.
  this.unregisterManifestResponseFilter_();
  // Unload the old instance.
  this.shakaPlayer_.unload(false);
  // Reset video element.
  this.videoElement_.removeAttribute('src');
  this.videoElement_.load();
  // Create a new instance and add listeners.
  this.shakaPlayer_ = new ShakaPlayer(this.videoElement_);
  this.shakaPlayer_.addEventListener(
      ShakaEvent.STREAMING, this.playbackStateListener_);
  this.shakaPlayer_.addEventListener(
      ShakaEvent.TRACKS_CHANGED, this.playbackStateListener_);
  this.shakaPlayer_.addEventListener(ShakaEvent.ERROR, this.shakaErrorHandler_);
};

/**
 * Moves a queue item within the queue.
 *
 * @private
 * @param {number} from The initial position.
 * @param {number} to The position to move the item to.
 * @return {boolean} Whether the item has been moved.
 */
Player.prototype.moveInQueue_ = function(from, to) {
  if (from < 0 || to < 0
    || from >= this.queue_.length || to >= this.queue_.length
    || from === to) {
    return false;
  }
  this.queue_.splice(to, 0, this.queue_.splice(from, 1)[0]);
  this.buildUuidIndexMap_(Math.min(from, to));
  if (from === this.windowIndex_) {
    this.windowIndex_ = to;
  } else if (from > this.windowIndex_ && to <= this.windowIndex_) {
    this.windowIndex_++;
  } else if (from < this.windowIndex_ && to >= this.windowIndex_) {
    this.windowIndex_--;
  }
  return true;
};

/**
 * Shuffles the queue.
 *
 * @private
 */
Player.prototype.shuffle_ = function() {
  this.shuffleOrder_ = this.queue_.map((item, index) => index);
  googArray.shuffle(this.shuffleOrder_);
  this.updateShuffleIndex_();
};

/**
 * Sets the new shuffle order.
 *
 * @private
 * @param {!Array<number>|undefined} shuffleOrder The new shuffle order.
 */
Player.prototype.setShuffleOrder_ = function(shuffleOrder) {
  if (shuffleOrder && this.queue_.length === shuffleOrder.length) {
    this.shuffleOrder_ = shuffleOrder;
    this.updateShuffleIndex_();
  } else if (this.shuffleOrder_.length !== this.queue_.length) {
    this.shuffle_();
  }
};

/**
 * Updates the shuffle order to point to the current window index.
 *
 * @private
 */
Player.prototype.updateShuffleIndex_ = function() {
  this.shuffleIndex_ =
      this.shuffleOrder_.findIndex((idx) => idx === this.windowIndex_);
};

/**
 * Builds the `queueUuidIndexMap` using the uuid of a media item as the key and
 * the window index as the value of an entry.
 *
 * @private
 * @param {number} startPosition The window index to start updating at.
 */
Player.prototype.buildUuidIndexMap_ = function(startPosition) {
  for (let i = startPosition; i < this.queue_.length; i++) {
    this.queueUuidIndexMap_[this.queue_[i].uuid] = i;
  }
};

/**
 * Gets the default position of the current window.
 *
 * @private
 * @return {number} The default position of the current window.
 */
Player.prototype.getDefaultPosition_ = function() {
  return this.windowMediaItemInfo_.defaultStartPositionUs;
};

/**
 * Checks whether the given position is buffered.
 *
 * @private
 * @param {number} positionMs The position to check.
 * @return {boolean} true if the media data of the current position is buffered.
 */
Player.prototype.isBuffered_ = function(positionMs) {
  const ranges = this.videoElement_.buffered;
  for (let i = 0; i < ranges.length; i++) {
    const start = ranges.start(i) * 1000;
    const end = ranges.end(i) * 1000;
    if (start <= positionMs && positionMs <= end) {
      return true;
    }
  }
  return false;
};

/**
 * Seeks to the positionMs of the given window.
 *
 * To signal a user seek, callers are expected to set the discontinuity reason
 * to `DiscontinuityReason.SEEK` before calling this method. If not set this
 * method may set the `DiscontinuityReason.PERIOD_TRANSITION` in case the
 * `windowIndex` changes.
 *
 * @private
 * @param {number} windowIndex The non-negative index of the window to seek to.
 * @param {number|undefined} positionMs The position to seek to within the
 *     window. If undefined it seeks to the default position of the window.
 */
Player.prototype.seekToWindowInternal_ = function(windowIndex, positionMs) {
  const windowChanges = this.windowIndex_ !== windowIndex;
  // Update window index and position in any case.
  this.windowIndex_ = Math.max(0, windowIndex);
  this.updateShuffleIndex_();
  const seekPositionMs = this.getPosition_(positionMs, windowIndex);
  this.videoElement_.currentTime = seekPositionMs / 1000;

  // IDLE or ENDED with empty queue.
  if (this.playbackState_ === PlaybackState.IDLE || this.queue_.length === 0) {
    // Do nothing but report the change in window index and position.
    this.invalidate();
    return;
  }

  // Prepare for a seek to another window or when in ENDED state whilst the
  // queue is not empty but prepare has not been called yet.
  if (windowChanges || this.playbackType_ === PlaybackType.UNKNOWN) {
    // Reset and prepare.
    this.unregisterManifestResponseFilter_();
    this.discontinuityReason_ =
        this.discontinuityReason_ || DiscontinuityReason.PERIOD_TRANSITION;
    this.prepare_(seekPositionMs);
    this.invalidate();
    return;
  }

  // Sync playWhenReady with video element after ENDED state.
  if (this.playbackState_ === PlaybackState.ENDED && this.playWhenReady_) {
    this.videoElement_.play();
    return;
  }

  // A seek within the current window when READY or BUFFERING.
  this.playbackState_ = this.isBuffered_(seekPositionMs) ?
      PlaybackState.READY :
      PlaybackState.BUFFERING;
  this.invalidate();
};

/**
 * Prepares the player at the current window index and the given
 * `startPositionMs`.
 *
 * Calling this method resets the media item information, transitions to
 * 'BUFFERING', prepares either the plain video element for progressive
 * media, or the Shaka player for adaptive media.
 *
 * Media items are mapped by media type to a `PlaybackType`s in
 * `exoplayer.cast.constants.SupportedMediaTypes`. Unsupported mime types will
 * cause the player to transition to the `IDLE` state.
 *
 * Items in the queue are expected to have been validated with
 * `validation#validateMediaItem` or `validation#validateMediaItems`. If this is
 * not the case this method might throw an Assertion exception.
 *
 * @private
 * @param {number} startPositionMs The position at which to start playback.
 * @throws {!AssertionException} In case an unvalidated item can't be mapped to
 *     a supported playback type.
 */
Player.prototype.prepare_ = function(startPositionMs) {
  const mediaItem = this.queue_[this.windowIndex_];
  const windowUuid = this.queue_[this.windowIndex_].uuid;
  const mediaItemInfo = this.mediaItemInfoMap_[windowUuid];
  if (mediaItemInfo && !mediaItemInfo.isDynamic) {
    // Do reuse if not dynamic.
    this.windowMediaItemInfo_ = mediaItemInfo;
  } else {
    // Use the dummy info until manifest/data available.
    this.windowMediaItemInfo_ = DUMMY_MEDIA_ITEM_INFO;
    this.mediaItemInfoMap_[windowUuid] = DUMMY_MEDIA_ITEM_INFO;
  }
  this.windowPeriodIndex_ = 0;
  this.playbackType_ = getPlaybackType(mediaItem.mimeType);
  this.playbackState_ = PlaybackState.BUFFERING;
  const uri = mediaItem.media.uri;
  switch (this.playbackType_) {
    case PlaybackType.VIDEO_ELEMENT:
      this.videoElement_.currentTime = startPositionMs / 1000;
      this.shakaPlayer_.unload(false)
          .then(() => {
            this.setMediaElementSrc(uri);
            this.videoElement_.currentTime = startPositionMs / 1000;
          })
          .catch((error) => {
            // Let's still try. We actually don't need Shaka right now.
            this.setMediaElementSrc(uri);
            this.videoElement_.currentTime = startPositionMs / 1000;
            console.error('Shaka error while unloading', error);
          });
      break;
    case PlaybackType.SHAKA_PLAYER:
      this.shakaPlayer_.configure(
          this.configurationFactory_.createConfiguration(
              mediaItem, this.trackSelectionParameters_));
      this.shakaPlayer_.load(uri, startPositionMs / 1000).catch((error) => {
        const shakaError = /** @type {!ShakaError} */ (error);
        if (shakaError.severity !== ShakaError.Severity.RECOVERABLE &&
            shakaError.code !== ShakaError.Code.LOAD_INTERRUPTED) {
          this.fatalShakaError_(shakaError, 'loading failed for uri: ' + uri);
          this.invalidate();
        } else {
          console.error('Recoverable Shaka error while loading', shakaError);
        }
      });
      break;
    default:
      fail('unknown playback type for mime type: ' + mediaItem.mimeType);
  }
};

/**
 * Sets the uri to the `src` attribute of the media element in a safe way.
 *
 * @param {string} uri The uri to set as the value of the `src` attribute.
 */
Player.prototype.setMediaElementSrc = function(uri) {
  safedom.setVideoSrc(
      asserts.assertIsHTMLVideoElement(this.videoElement_), uri);
};

/**
 * Handles a fatal Shaka error by setting the playback error, transitioning to
 * state `IDLE` and setting the playback type to `UNKNOWN`. Player needs to be
 * reprepared after calling this method.
 *
 * @private
 * @param {!ShakaError} shakaError The error.
 * @param {string|undefined} customMessage A custom message.
 */
Player.prototype.fatalShakaError_ = function(shakaError, customMessage) {
  this.playbackState_ = PlaybackState.IDLE;
  this.playbackType_ = PlaybackType.UNKNOWN;
  this.uuidToPrepare_ = this.queue_[this.windowIndex_] ?
      this.queue_[this.windowIndex_].uuid :
      null;
  if (typeof shakaError.severity === 'undefined') {
    // Not a Shaka error. We need to assume the worst case.
    this.replaceShaka_();
    this.playbackError_ = /** @type {!PlayerError} */ ({
      message: ErrorMessages.UNKNOWN_FATAL_ERROR,
      code: -1,
      category: ErrorCategory.FATAL_SHAKA_ERROR,
    });
  } else {
    // A critical ShakaError. Can be recovered from by calling prepare.
    this.playbackError_ = /** @type {!PlayerError} */ ({
      message: customMessage || shakaError.message ||
          ErrorMessages.SHAKA_UNKNOWN_ERROR,
      code: shakaError.code,
      category: shakaError.category,
    });
  }
  console.error('caught shaka load error', shakaError);
};

/**
 * Gets the position to use. If `undefined` or `null` is passed as argument the
 * default start position of the media item info of the given windowIndex is
 * returned.
 *
 * @private
 * @param {?number|undefined} positionMs The position in milliseconds,
 *     `undefined` or `null`.
 * @param  {number} windowIndex The window index for which to evaluate the
 *     position.
 * @return {number} The position to use in milliseconds.
 */
Player.prototype.getPosition_ = function(positionMs, windowIndex) {
  if (positionMs !== undefined) {
    return Math.max(0, positionMs);
  }
  const windowUuid = assert(this.queue_[windowIndex]).uuid;
  const mediaItemInfo =
      this.mediaItemInfoMap_[windowUuid] || DUMMY_MEDIA_ITEM_INFO;
  return mediaItemInfo.defaultStartPositionUs;
};

/**
 * Refreshes the media item info of the current window.
 *
 * @private
 */
Player.prototype.updateWindowMediaItemInfo_ = function() {
  this.windowMediaItemInfo_ = this.buildMediaItemInfo_();
  if (this.windowMediaItemInfo_) {
    const mediaItem = this.queue_[this.windowIndex_];
    this.mediaItemInfoMap_[mediaItem.uuid] = this.windowMediaItemInfo_;
    this.evaluateAndSetCurrentPeriod_(this.windowMediaItemInfo_.periods);
  }
};

/**
 * Evaluates the current period and stores it in a member variable.
 *
 * @private
 * @param {!Array<!Period>} periods The periods of the current mediaItem.
 */
Player.prototype.evaluateAndSetCurrentPeriod_ = function(periods) {
  const positionUs = this.getCurrentPositionMs() * 1000;
  let positionInWindowUs = 0;
  periods.some((period, i) => {
    positionInWindowUs += period.durationUs;
    if (positionUs < positionInWindowUs) {
      this.windowPeriodIndex_ = i;
      return true;
    }
    return false;
  });
};

/**
 * Registers a response filter which is notified when a manifest has been
 * downloaded.
 *
 * @private
 */
Player.prototype.registerManifestResponseFilter_ = function() {
  if (this.isManifestFilterRegistered_) {
    return;
  }
  this.shakaPlayer_.getNetworkingEngine().registerResponseFilter(
      this.manifestResponseFilter_);
  this.isManifestFilterRegistered_ = true;
};

/**
 * Unregisters the manifest response filter.
 *
 * @private
 */
Player.prototype.unregisterManifestResponseFilter_ = function() {
  if (this.isManifestFilterRegistered_) {
    this.shakaPlayer_.getNetworkingEngine().unregisterResponseFilter(
        this.manifestResponseFilter_);
    this.isManifestFilterRegistered_ = false;
  }
};

/**
 * Builds a MediaItemInfo from the media element.
 *
 * @private
 * @return {!MediaItemInfo} A media item info.
 */
Player.prototype.buildMediaItemInfoFromElement_ = function() {
  const durationUs = this.videoElement_.duration * 1000 * 1000;
  return /** @type {!MediaItemInfo} */ ({
    isSeekable: !!this.videoElement_.seekable,
    isDynamic: false,
    positionInFirstPeriodUs: 0,
    defaultStartPositionUs: 0,
    windowDurationUs: durationUs,
    periods: [{
      id: 0,
      durationUs: durationUs,
    }],
  });
};

/**
 * Builds a MediaItemInfo from the manifest or null if no manifest is available.
 *
 * @private
 * @return {!MediaItemInfo}
 */
Player.prototype.buildMediaItemInfo_ = function() {
  const manifest = this.shakaPlayer_.getManifest();
  if (manifest === null) {
    return DUMMY_MEDIA_ITEM_INFO;
  }
  const timeline = manifest.presentationTimeline;
  const isDynamic = timeline.isLive();
  const windowStartUs = isDynamic ?
      timeline.getSeekRangeStart() * 1000 * 1000 :
      timeline.getSegmentAvailabilityStart() * 1000 * 1000;
  const windowDurationUs = isDynamic ?
      (timeline.getSeekRangeEnd() - timeline.getSeekRangeStart()) * 1000 *
          1000 :
      timeline.getDuration() * 1000 * 1000;
  const defaultStartPositionUs = isDynamic ?
      timeline.getSeekRangeEnd() * 1000 * 1000 :
      timeline.getSegmentAvailabilityStart() * 1000 * 1000;

  const periods = [];
  let previousStartTimeUs = 0;
  let positionInFirstPeriodUs = 0;
  manifest.periods.forEach((period, index) => {
    const startTimeUs = period.startTime * 1000 * 1000;
    periods.push({
      id: Math.floor(startTimeUs),
    });
    if (index > 0) {
      // calculate duration of previous period
      periods[index - 1].durationUs = startTimeUs - previousStartTimeUs;
      if (previousStartTimeUs <= windowStartUs && windowStartUs < startTimeUs) {
        positionInFirstPeriodUs = windowStartUs - previousStartTimeUs;
      }
    }
    previousStartTimeUs = startTimeUs;
  });
  // calculate duration of last period
  if (periods.length) {
    const lastPeriodDurationUs =
        isDynamic ? Infinity : windowDurationUs - previousStartTimeUs;
    periods.slice(-1)[0].durationUs = lastPeriodDurationUs;
    if (previousStartTimeUs <= windowStartUs) {
      positionInFirstPeriodUs = windowStartUs - previousStartTimeUs;
    }
  }
  return /** @type {!MediaItemInfo} */ ({
    windowDurationUs: Math.floor(windowDurationUs),
    defaultStartPositionUs: Math.floor(defaultStartPositionUs),
    isSeekable: this.videoElement_ ? !!this.videoElement_.seekable : false,
    positionInFirstPeriodUs: Math.floor(positionInFirstPeriodUs),
    isDynamic: isDynamic,
    periods: periods,
  });
};

/**
 * Builds the player state message.
 *
 * @private
 * @return {!PlayerState} The player state.
 */
Player.prototype.buildPlayerState_ = function() {
  const playerState = {
    playbackState: this.getPlaybackState(),
    playbackParameters: {
      speed: 1,
      pitch: 1,
      skipSilence: false,
    },
    playbackPosition: this.buildPlaybackPosition_(),
    playWhenReady: this.getPlayWhenReady(),
    windowIndex: this.getCurrentWindowIndex(),
    windowCount: this.queue_.length,
    audioTracks: this.getAudioTracks() || [],
    videoTracks: this.getVideoTracks(),
    repeatMode: this.repeatMode_,
    shuffleModeEnabled: this.shuffleModeEnabled_,
    mediaQueue: this.queue_.slice(),
    mediaItemsInfo: this.mediaItemInfoMap_,
    shuffleOrder: this.shuffleOrder_,
    sequenceNumber: -1,
  };
  if (this.playbackError_) {
    playerState.error = this.playbackError_;
    this.playbackError_ = null;
  }
  return playerState;
};

/**
 * Builds the playback position. Returns null if all properties of the playback
 * position are empty.
 *
 * @private
 * @return {?PlaybackPosition} The playback position.
 */
Player.prototype.buildPlaybackPosition_ = function() {
  if ((this.playbackState_ === PlaybackState.IDLE && !this.uuidToPrepare_) ||
      this.playbackState_ === PlaybackState.ENDED && this.queue_.length === 0) {
    this.discontinuityReason_ = null;
    return null;
  }
  /** @type {!PlaybackPosition} */
  const playbackPosition = {
    positionMs: this.getCurrentPositionMs(),
    uuid: this.uuidToPrepare_ || this.queue_[this.windowIndex_].uuid,
    periodId: this.windowMediaItemInfo_.periods[this.windowPeriodIndex_].id,
    discontinuityReason: null,
  };
  if (this.discontinuityReason_ !== null) {
    playbackPosition.discontinuityReason = this.discontinuityReason_;
    this.discontinuityReason_ = null;
  }
  return playbackPosition;
};

exports = Player;
exports.RepeatMode = RepeatMode;
exports.PlaybackState = PlaybackState;
exports.DiscontinuityReason = DiscontinuityReason;
exports.DUMMY_MEDIA_ITEM_INFO = DUMMY_MEDIA_ITEM_INFO;
