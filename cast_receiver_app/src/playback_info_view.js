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

goog.module('exoplayer.cast.PlaybackInfoView');

const Player = goog.require('exoplayer.cast.Player');
const Timeout = goog.require('exoplayer.cast.Timeout');
const dom = goog.require('goog.dom');

/** The default timeout for hiding the UI in milliseconds. */
const SHOW_TIMEOUT_MS = 5000;
/** The timeout for hiding the UI in audio only mode in milliseconds. */
const SHOW_TIMEOUT_MS_AUDIO = 0;
/** The timeout for updating the UI while being displayed. */
const UPDATE_TIMEOUT_MS = 1000;

/**
 * Formats a duration in milliseconds to a string in hh:mm:ss format.
 *
 * @param {number} durationMs The duration in milliseconds.
 * @return {string} The duration formatted as hh:mm:ss.
 */
const formatTimestampMsAsString = function (durationMs) {
  const hours = Math.floor(durationMs / 1000 / 60 / 60);
  const minutes = Math.floor((durationMs / 1000 / 60) % 60);
  const seconds = Math.floor((durationMs / 1000) % 60) % 60;
  let timeString = '';
  if (hours > 0) {
    timeString += hours + ':';
  }
  if (minutes < 10) {
    timeString += '0';
  }
  timeString += minutes + ":";
  if (seconds < 10) {
    timeString += '0';
  }
  timeString += seconds;
  return timeString;
};

/**
 * A view to display information about the current media item and playback
 * progress.
 *
 * @constructor
 * @param {!Player} player The player of which to display the
 *     playback info.
 * @param {string} viewId The id of the playback info view.
 */
const PlaybackInfoView = function (player, viewId) {
  /** @const @private {!Player} */
  this.player_ = player;
  /** @const @private {?Element} */
  this.container_ = document.getElementById(viewId);
  /** @const @private {?Element} */
  this.elapsedTimeBar_ = document.getElementById('exo_elapsed_time');
  /** @const @private {?Element} */
  this.elapsedTimeLabel_ = document.getElementById('exo_elapsed_time_label');
  /** @const @private {?Element} */
  this.durationLabel_ = document.getElementById('exo_duration_label');
  /** @const @private {!Timeout} */
  this.hideTimeout_ = new Timeout();
  /** @const @private {!Timeout} */
  this.updateTimeout_ = new Timeout();
  /** @private {boolean} */
  this.wasPlaying_ = player.getPlayWhenReady()
      && player.getPlaybackState() === Player.PlaybackState.READY;
  /** @private {number} */
  this.showTimeoutMs_ = SHOW_TIMEOUT_MS;
  /** @private {number} */
  this.showTimeoutMsVideo_ = this.showTimeoutMs_;

  if (this.wasPlaying_) {
    this.hideAfterTimeout();
  } else {
    this.show();
  }

  player.addPlayerListener((playerState) => {
    if (this.container_ === null) {
      return;
    }
    const playbackPosition = playerState.playbackPosition;
    const discontinuityReason =
        playbackPosition ? playbackPosition.discontinuityReason : null;
    if (discontinuityReason) {
      const currentMediaItem = player.getCurrentMediaItem();
      this.showTimeoutMs_ =
          currentMediaItem && currentMediaItem.mimeType === 'audio/*' ?
          SHOW_TIMEOUT_MS_AUDIO :
          this.showTimeoutMsVideo_;
    }
    const playWhenReady = playerState.playWhenReady;
    const state = playerState.playbackState;
    const isPlaying = playWhenReady && state === Player.PlaybackState.READY;
    const userSeekedInBufferedRange =
        discontinuityReason === Player.DiscontinuityReason.SEEK && isPlaying;
    if (!isPlaying) {
      this.show();
    } else if ((!this.wasPlaying_ && isPlaying) || userSeekedInBufferedRange) {
      this.hideAfterTimeout();
    }
    this.wasPlaying_ = isPlaying;
  });
};

/** Shows the player info view. */
PlaybackInfoView.prototype.show = function () {
  if (this.container_ != null) {
    this.hideTimeout_.cancel();
    this.updateUi_();
    this.container_.style.display = 'block';
    this.startUpdateTimeout_();
  }
};

/** Hides the player info view. */
PlaybackInfoView.prototype.hideAfterTimeout = function() {
  if (this.container_ === null) {
    return;
  }
  this.show();
  this.hideTimeout_.postDelayed(this.showTimeoutMs_).then(() => {
    this.container_.style.display = 'none';
    this.updateTimeout_.cancel();
  });
};

/**
 * Sets the playback info view timeout. The playback info view is automatically
 * hidden after this duration of time has elapsed without show() being called
 * again. When playing streams with content type 'audio/*' the view is always
 * displayed.
 *
 * @param {number} showTimeoutMs The duration in milliseconds. A non-positive
 *     value will cause the view to remain visible indefinitely.
 */
PlaybackInfoView.prototype.setShowTimeoutMs = function(showTimeoutMs) {
  this.showTimeoutMs_ = showTimeoutMs;
  this.showTimeoutMsVideo_ = showTimeoutMs;
};

/**
 * Updates all UI components.
 *
 * @private
 */
PlaybackInfoView.prototype.updateUi_ = function () {
  const elapsedTimeMs = this.player_.getCurrentPositionMs();
  const durationMs = this.player_.getDurationMs();
  if (this.elapsedTimeLabel_ !== null) {
    this.updateDuration_(this.elapsedTimeLabel_, elapsedTimeMs, false);
  }
  if (this.durationLabel_ !== null) {
    this.updateDuration_(this.durationLabel_, durationMs, true);
  }
  if (this.elapsedTimeBar_ !== null) {
    this.updateProgressBar_(elapsedTimeMs, durationMs);
  }
};

/**
 * Adjust the progress bar indicating the elapsed time relative to the duration.
 *
 * @private
 * @param {number} elapsedTimeMs The elapsed time in milliseconds.
 * @param {number} durationMs The duration in milliseconds.
 */
PlaybackInfoView.prototype.updateProgressBar_ =
    function(elapsedTimeMs, durationMs) {
  if (elapsedTimeMs <= 0 || durationMs <= 0) {
    this.elapsedTimeBar_.style.width = 0;
  } else {
    const widthPercentage = elapsedTimeMs / durationMs * 100;
    this.elapsedTimeBar_.style.width = Math.min(100, widthPercentage) + '%';
  }
};

/**
 * Updates the display value of the duration in the DOM formatted as hh:mm:ss.
 *
 * @private
 * @param {!Element} element The element to update.
 * @param {number} durationMs The duration in milliseconds.
 * @param {boolean} hideZero If true values of zero and below are not displayed.
 */
PlaybackInfoView.prototype.updateDuration_ =
    function (element, durationMs, hideZero) {
  while (element.firstChild) {
    element.removeChild(element.firstChild);
  }
  if (durationMs <= 0 && !hideZero) {
    element.appendChild(dom.createDom(dom.TagName.SPAN, {},
          formatTimestampMsAsString(0)));
  } else if (durationMs > 0) {
    element.appendChild(dom.createDom(dom.TagName.SPAN, {},
          formatTimestampMsAsString(durationMs)));
  }
};

/**
 * Starts a repeating timeout that updates the UI every UPDATE_TIMEOUT_MS
 * milliseconds.
 *
 * @private
 */
PlaybackInfoView.prototype.startUpdateTimeout_ = function() {
  this.updateTimeout_.cancel();
  if (!this.player_.getPlayWhenReady() ||
      this.player_.getPlaybackState() !== Player.PlaybackState.READY) {
    return;
  }
  this.updateTimeout_.postDelayed(UPDATE_TIMEOUT_MS).then(() => {
    this.updateUi_();
    this.startUpdateTimeout_();
  });
};

exports = PlaybackInfoView;
