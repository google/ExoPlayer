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
 *
 * @fileoverview Unit tests for the playback info view.
 */

goog.module('exoplayer.cast.test.PlaybackInfoView');
goog.setTestOnly();

const PlaybackInfoView = goog.require('exoplayer.cast.PlaybackInfoView');
const Player = goog.require('exoplayer.cast.Player');
const testSuite = goog.require('goog.testing.testSuite');

/** The state of the player mock */
let mockState;

/**
 * Initializes the state of the mock. Needs to be called in the setUp method of
 * the unit test.
 */
const setUpMockState = function() {
  mockState = {
    playWhenReady: false,
    currentPositionMs: 1000,
    durationMs: 10 * 1000,
    playbackState: 'READY',
    discontinuityReason: undefined,
    listeners: [],
    currentMediaItem: {
      mimeType: 'video/*',
    },
  };
};

/** Notifies registered listeners with the current player state. */
const notifyListeners = function() {
  if (!mockState) {
    console.warn(
        'mock state not initialized. Did you call setUp ' +
        'when setting up the test case?');
  }
  mockState.listeners.forEach((listener) => {
    listener({
      playWhenReady: mockState.playWhenReady,
      playbackState: mockState.playbackState,
      playbackPosition: {
        currentPositionMs: mockState.currentPositionMs,
        discontinuityReason: mockState.discontinuityReason,
      },
    });
  });
};

/**
 * Creates a sufficient mock of the Player.
 *
 * @return {!Player}
 */
const createPlayerMock = function() {
  return /** @type {!Player} */ ({
    addPlayerListener: (listener) => {
      mockState.listeners.push(listener);
    },
    getPlayWhenReady: () => mockState.playWhenReady,
    getPlaybackState: () => mockState.playbackState,
    getCurrentPositionMs: () => mockState.currentPositionMs,
    getDurationMs: () => mockState.durationMs,
    getCurrentMediaItem: () => mockState.currentMediaItem,
  });
};

/** Inserts the DOM structure the playback info view needs. */
const insertComponentDom = function() {
  const container = appendChild(document.body, 'div', 'container-id');
  appendChild(container, 'div', 'exo_elapsed_time');
  appendChild(container, 'div', 'exo_elapsed_time_label');
  appendChild(container, 'div', 'exo_duration_label');
};

/**
 * Creates and appends a child to the parent element.
 *
 * @param {!Element} parent The parent element.
 * @param {string} tagName The tag name of the child element.
 * @param {string} id The id of the child element.
 * @return {!Element} The appended child element.
 */
const appendChild = function(parent, tagName, id) {
  const child = document.createElement(tagName);
  child.id = id;
  parent.appendChild(child);
  return child;
};

/** Removes the inserted elements from the DOM again. */
const removeComponentDom = function() {
  const container = document.getElementById('container-id');
  if (container) {
    container.parentNode.removeChild(container);
  }
};

let playbackInfoView;

testSuite({
  setUp() {
    insertComponentDom();
    setUpMockState();
    playbackInfoView = new PlaybackInfoView(
        createPlayerMock(), /** containerId= */ 'container-id');
    playbackInfoView.setShowTimeoutMs(1);
  },

  tearDown() {
    removeComponentDom();
  },

  /** Tests setting the show timeout. */
  testSetShowTimeout() {
    assertEquals(1, playbackInfoView.showTimeoutMs_);
    playbackInfoView.setShowTimeoutMs(10);
    assertEquals(10, playbackInfoView.showTimeoutMs_);
  },

  /** Tests rendering the duration to the DOM. */
  testRenderDuration() {
    const el = document.getElementById('exo_duration_label');
    assertEquals('00:10', el.firstChild.firstChild.nodeValue);
    mockState.durationMs = 35 * 1000;
    notifyListeners();
    assertEquals('00:35', el.firstChild.firstChild.nodeValue);

    mockState.durationMs =
        (12 * 60 * 60 * 1000) + (20 * 60 * 1000) + (13 * 1000);
    notifyListeners();
    assertEquals('12:20:13', el.firstChild.firstChild.nodeValue);

    mockState.durationMs = -1000;
    notifyListeners();
    assertNull(el.nodeValue);
  },

  /** Tests rendering the playback position to the DOM. */
  testRenderPlaybackPosition() {
    const el = document.getElementById('exo_elapsed_time_label');
    assertEquals('00:01', el.firstChild.firstChild.nodeValue);
    mockState.currentPositionMs = 2000;
    notifyListeners();
    assertEquals('00:02', el.firstChild.firstChild.nodeValue);

    mockState.currentPositionMs =
        (12 * 60 * 60 * 1000) + (20 * 60 * 1000) + (13 * 1000);
    notifyListeners();
    assertEquals('12:20:13', el.firstChild.firstChild.nodeValue);

    mockState.currentPositionMs = -1000;
    notifyListeners();
    assertNull(el.nodeValue);

    mockState.currentPositionMs = 0;
    notifyListeners();
    assertEquals('00:00', el.firstChild.firstChild.nodeValue);
  },

  /** Tests rendering the timebar width reflects position and duration. */
  testRenderTimebar() {
    const el = document.getElementById('exo_elapsed_time');
    assertEquals('10%', el.style.width);

    mockState.currentPositionMs = 0;
    notifyListeners();
    assertEquals('0px', el.style.width);

    mockState.currentPositionMs = 5 * 1000;
    notifyListeners();
    assertEquals('50%', el.style.width);

    mockState.currentPositionMs = mockState.durationMs * 2;
    notifyListeners();
    assertEquals('100%', el.style.width);

    mockState.currentPositionMs = -1;
    notifyListeners();
    assertEquals('0px', el.style.width);
  },

  /** Tests whether the update timeout is set and removed. */
  testUpdateTimeout_setAndRemoved() {
    assertFalse(playbackInfoView.updateTimeout_.isOngoing());

    mockState.playWhenReady = true;
    notifyListeners();
    assertTrue(playbackInfoView.updateTimeout_.isOngoing());

    mockState.playWhenReady = false;
    notifyListeners();
    assertFalse(playbackInfoView.updateTimeout_.isOngoing());
  },

  /** Tests whether the show timeout is set when playback starts. */
  testHideTimeout_setAndRemoved() {
    assertFalse(playbackInfoView.hideTimeout_.isOngoing());

    mockState.playWhenReady = true;
    notifyListeners();
    assertNotUndefined(playbackInfoView.hideTimeout_);
    assertTrue(playbackInfoView.hideTimeout_.isOngoing());

    mockState.playWhenReady = false;
    notifyListeners();
    assertFalse(playbackInfoView.hideTimeout_.isOngoing());
  },

  /** Test whether the view switches to always on for audio media. */
  testAlwaysOnForAudio() {
    playbackInfoView.setShowTimeoutMs(50);
    assertEquals(50, playbackInfoView.showTimeoutMs_);
    // The player transitions from video to audio stream.
    mockState.discontinuityReason = 'PERIOD_TRANSITION';
    mockState.currentMediaItem.mimeType = 'audio/*';
    notifyListeners();
    assertEquals(0, playbackInfoView.showTimeoutMs_);

    mockState.discontinuityReason = 'PERIOD_TRANSITION';
    mockState.currentMediaItem.mimeType = 'video/*';
    notifyListeners();
    assertEquals(50, playbackInfoView.showTimeoutMs_);
  },

});
