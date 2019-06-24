/**
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
 *
 * @fileoverview Unit tests for playback methods.
 */

goog.module('exoplayer.cast.test.shaka');
goog.setTestOnly();

const ConfigurationFactory = goog.require('exoplayer.cast.ConfigurationFactory');
const Player = goog.require('exoplayer.cast.Player');
const mocks = goog.require('exoplayer.cast.test.mocks');
const testSuite = goog.require('goog.testing.testSuite');
const util = goog.require('exoplayer.cast.test.util');

let player;
let shakaFake;

testSuite({
  setUp() {
    mocks.setUp();
    shakaFake = mocks.createShakaFake();
    player = new Player(shakaFake, new ConfigurationFactory());
  },

  /** Tests Shaka critical error handling on load. */
  async testShakaCriticalError_onload() {
    mocks.state().isSilent = true;
    mocks.state().setShakaThrowsOnLoad(true);
    let playerState;
    player.addPlayerListener((state) => {
      playerState = state;
    });
    player.addQueueItems(0, util.queue.slice(0, 2));
    player.seekToUuid('uuid1', 2000);
    player.setPlayWhenReady(true);
    // Calling prepare triggers a critical Shaka error.
    await player.prepare();
    // Assert player state after error.
    assertEquals('IDLE', playerState.playbackState);
    assertEquals(mocks.state().shakaError.category, playerState.error.category);
    assertEquals(mocks.state().shakaError.code, playerState.error.code);
    assertEquals(
        'loading failed for uri: http://example1.com',
        playerState.error.message);
    assertEquals(999, player.playbackType_);
    // Assert player properties are preserved.
    assertEquals(2000, player.getCurrentPositionMs());
    assertTrue(player.getPlayWhenReady());
    assertEquals(1, player.getCurrentWindowIndex());
    assertEquals(1, player.windowIndex_);
  },

  /** Tests Shaka critical error handling on unload. */
  async testShakaCriticalError_onunload() {
    mocks.state().isSilent = true;
    mocks.state().setShakaThrowsOnUnload(true);
    let playerState;
    player.addPlayerListener((state) => {
      playerState = state;
    });
    player.addQueueItems(0, util.queue.slice(0, 2));
    player.setPlayWhenReady(true);
    assertUndefined(player.videoElement_.src);
    // Calling prepare triggers a critical Shaka error.
    await player.prepare();
    // Assert player state after caught and ignored error.
    await assertEquals('BUFFERING', playerState.playbackState);
    assertEquals('http://example.com', player.videoElement_.src);
    assertEquals(1, player.playbackType_);
  },
});
