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

goog.module('exoplayer.cast.test');
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

  /** Tests the player initialisation */
  testPlayerInitialisation() {
    mocks.state().isSilent = true;
    const states = [];
    let stateCounter = 0;
    let currentState;
    player.addPlayerListener((playerState) => {
      states.push(playerState);
    });

    // Dump the initial state manually.
    player.invalidate();
    stateCounter++;
    assertEquals(stateCounter, states.length);
    currentState = states[stateCounter - 1];
    assertEquals(0, currentState.mediaQueue.length);
    assertEquals(0, currentState.windowIndex);
    assertNull(currentState.playbackPosition);

    // Seek with uuid to prepare with later
    const uuid = 'uuid1';
    player.seekToUuid(uuid, 30 * 1000);
    stateCounter++;
    assertEquals(stateCounter, states.length);
    currentState = states[stateCounter - 1];
    assertEquals(30 * 1000, player.getCurrentPositionMs());
    assertEquals(0, player.getCurrentWindowIndex());
    assertEquals(-1, player.windowIndex_);
    assertEquals(1, currentState.playbackPosition.periodId);
    assertEquals(uuid, currentState.playbackPosition.uuid);
    assertEquals(uuid, player.uuidToPrepare_);

    // Add a DASH media item.
    player.addQueueItems(0, util.queue.slice(0, 2));
    stateCounter++;
    assertEquals(stateCounter, states.length);
    currentState = states[stateCounter - 1];
    assertEquals('IDLE', currentState.playbackState);
    assertNotNull(currentState.playbackPosition);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, currentState.mediaQueue);

    // Prepare.
    player.prepare();
    stateCounter++;
    assertEquals(stateCounter, states.length);
    currentState = states[stateCounter - 1];
    assertEquals(2, currentState.mediaQueue.length);
    assertEquals('BUFFERING', currentState.playbackState);
    assertEquals(
        Player.DUMMY_MEDIA_ITEM_INFO, currentState.mediaItemsInfo[uuid]);
    assertNull(player.uuidToPrepare_);

    // The video element starts waiting.
    mocks.state().isSilent = false;
    mocks.notifyListeners('waiting');
    // Nothing happens, masked buffering state after preparing.
    assertEquals(stateCounter, states.length);

    // The manifest arrives.
    mocks.notifyListeners('streaming');
    stateCounter++;
    assertEquals(stateCounter, states.length);
    currentState = states[stateCounter - 1];
    assertEquals(2, currentState.mediaQueue.length);
    assertEquals('BUFFERING', currentState.playbackState);
    assertEquals(uuid, currentState.playbackPosition.uuid);
    assertEquals(0, currentState.playbackPosition.periodId);
    assertEquals(30 * 1000, currentState.playbackPosition.positionMs);
    // The dummy media item info has been replaced by the real one.
    assertEquals(20000000, currentState.mediaItemsInfo[uuid].windowDurationUs);
    assertEquals(0, currentState.mediaItemsInfo[uuid].defaultStartPositionUs);
    assertEquals(0, currentState.mediaItemsInfo[uuid].positionInFirstPeriodUs);
    assertTrue(currentState.mediaItemsInfo[uuid].isSeekable);
    assertFalse(currentState.mediaItemsInfo[uuid].isDynamic);

    // Tracks have initially changed.
    mocks.notifyListeners('trackschanged');
    // Nothing happens because the media item info remains the same.
    assertEquals(stateCounter, states.length);

    //  The video element reports the first frame rendered.
    mocks.notifyListeners('loadeddata');
    stateCounter++;
    assertEquals(stateCounter, states.length);
    currentState = states[stateCounter - 1];
    assertEquals(2, currentState.mediaQueue.length);
    assertEquals('READY', currentState.playbackState);
    assertEquals(uuid, currentState.playbackPosition.uuid);
    assertEquals(0, currentState.playbackPosition.periodId);
    assertEquals(30 * 1000, currentState.playbackPosition.positionMs);

    // Playback starts.
    mocks.notifyListeners('playing');
    // Nothing happens; we are ready already.
    assertEquals(stateCounter, states.length);

    // Add another queue item.
    player.addQueueItems(1, util.queue.slice(3, 4));
    stateCounter++;
    assertEquals(stateCounter, states.length);
    mocks.state().isSilent = true;
    // Seek to the next queue item.
    player.seekToWindow(1, 0);
    stateCounter++;
    assertEquals(stateCounter, states.length);
    currentState = states[stateCounter - 1];
    const uuid1 = currentState.mediaQueue[1].uuid;
    assertEquals(
        Player.DUMMY_MEDIA_ITEM_INFO, currentState.mediaItemsInfo[uuid1]);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, currentState.mediaQueue);

    // The video element starts waiting.
    mocks.state().isSilent = false;
    mocks.notifyListeners('waiting');
    // Nothing happens, masked buffering state after preparing.
    assertEquals(stateCounter, states.length);

    // The manifest arrives.
    mocks.notifyListeners('streaming');
    stateCounter++;
    assertEquals(stateCounter, states.length);
    currentState = states[stateCounter - 1];
    // The dummy media item info has been replaced by the real one.
    assertEquals(20000000, currentState.mediaItemsInfo[uuid].windowDurationUs);
    assertEquals(0, currentState.mediaItemsInfo[uuid].defaultStartPositionUs);
    assertEquals(0, currentState.mediaItemsInfo[uuid].positionInFirstPeriodUs);
    assertTrue(currentState.mediaItemsInfo[uuid].isSeekable);
    assertFalse(currentState.mediaItemsInfo[uuid].isDynamic);
  },

  /** Tests next and previous window when not yet prepared. */
  testNextPreviousWindow_notPrepared() {
    assertEquals(-1, player.getNextWindowIndex());
    assertEquals(-1, player.getPreviousWindowIndex());
    player.addQueueItems(0, util.queue.slice(0, 2));
    assertEquals(-1, player.getNextWindowIndex());
    assertEquals(-1, player.getPreviousWindowIndex());
  },

  /** Tests setting play when ready. */
  testPlayWhenReady() {
    player.addQueueItems(0, util.queue.slice(0, 3));
    let playWhenReady = false;
    player.addPlayerListener((state) => {
      playWhenReady = state.playWhenReady;
    });

    assertEquals(false, player.getPlayWhenReady());
    assertEquals(false, playWhenReady);

    player.setPlayWhenReady(true);
    assertEquals(true, player.getPlayWhenReady());
    assertEquals(true, playWhenReady);

    player.setPlayWhenReady(false);
    assertEquals(false, player.getPlayWhenReady());
    assertEquals(false, playWhenReady);
  },

  /** Tests seeking to another position in the actual window. */
  async testSeek_inWindow() {
    player.addQueueItems(0, util.queue.slice(0, 3));
    await player.seekToWindow(0, 1000);

    assertEquals(1, shakaFake.getMediaElement().currentTime);
    assertEquals(1000, player.getCurrentPositionMs());
    assertEquals(0, player.getCurrentWindowIndex());
  },

  /** Tests seeking to another window. */
  async testSeek_nextWindow() {
    player.addQueueItems(0, util.queue.slice(0, 3));
    await player.prepare();
    assertEquals(util.queue[0].media.uri, shakaFake.getMediaElement().src);
    assertEquals(-1, player.getPreviousWindowIndex());
    assertEquals(1, player.getNextWindowIndex());

    player.seekToWindow(1, 2000);
    assertEquals(0, player.getPreviousWindowIndex());
    assertEquals(2, player.getNextWindowIndex());
    assertEquals(2000, player.getCurrentPositionMs());
    assertEquals(1, player.getCurrentWindowIndex());
    assertEquals(util.queue[1].media.uri, mocks.state().loadedUri);
  },

  /** Tests the repeat mode 'none' */
  testRepeatMode_none() {
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    assertEquals(Player.RepeatMode.OFF, player.getRepeatMode());
    assertEquals(-1, player.getPreviousWindowIndex());
    assertEquals(1, player.getNextWindowIndex());

    player.seekToWindow(2, 0);
    assertEquals(1, player.getPreviousWindowIndex());
    assertEquals(-1, player.getNextWindowIndex());
  },

  /** Tests the repeat mode 'all'. */
  testRepeatMode_all() {
    let repeatMode;
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    player.addPlayerListener((state) => {
      repeatMode = state.repeatMode;
    });
    player.setRepeatMode(Player.RepeatMode.ALL);
    assertEquals(Player.RepeatMode.ALL, repeatMode);

    player.seekToWindow(0,0);
    assertEquals(2, player.getPreviousWindowIndex());
    assertEquals(1, player.getNextWindowIndex());

    player.seekToWindow(2, 0);
    assertEquals(1, player.getPreviousWindowIndex());
    assertEquals(0, player.getNextWindowIndex());
  },

  /**
   * Tests navigation within the queue when repeat mode and shuffle mode is on.
   */
  testRepeatMode_all_inShuffleMode() {
    const initialOrder = [2, 1, 0];
    let shuffleOrder;
    let windowIndex;
    player.addQueueItems(0, util.queue.slice(0, 3), initialOrder);
    player.prepare();
    player.addPlayerListener((state) => {
      shuffleOrder = state.shuffleOrder;
      windowIndex = state.windowIndex;
    });
    player.setRepeatMode(Player.RepeatMode.ALL);
    player.setShuffleModeEnabled(true);
    assertEquals(windowIndex, player.shuffleOrder_[player.shuffleIndex_]);
    assertArrayEquals(initialOrder, shuffleOrder);

    player.seekToWindow(shuffleOrder[2], 0);
    assertEquals(shuffleOrder[2], windowIndex);
    assertEquals(shuffleOrder[0], player.getNextWindowIndex());
    assertEquals(shuffleOrder[1], player.getPreviousWindowIndex());

    player.seekToWindow(shuffleOrder[0], 0);
    assertEquals(shuffleOrder[0], windowIndex);
  },

  /** Tests the repeat mode 'one' */
  testRepeatMode_one() {
    let repeatMode;
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    player.addPlayerListener((state) => {
      repeatMode = state.repeatMode;
    });
    player.setRepeatMode(Player.RepeatMode.ONE);
    assertEquals(Player.RepeatMode.ONE, repeatMode);
    assertEquals(0, player.getPreviousWindowIndex());
    assertEquals(0, player.getNextWindowIndex());

    player.seekToWindow(1, 0);
    assertEquals(1, player.getPreviousWindowIndex());
    assertEquals(1, player.getNextWindowIndex());

    player.setShuffleModeEnabled(true);
    assertEquals(1, player.getPreviousWindowIndex());
    assertEquals(1, player.getNextWindowIndex());
  },

  /** Tests building a media item info from the manifest. */
  testBuildMediaItemInfo_fromManifest() {
    let mediaItemInfos = null;
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.addPlayerListener((state) => {
      mediaItemInfos = state.mediaItemsInfo;
    });
    player.seekToWindow(1, 0);
    player.prepare();
    assertUndefined(mediaItemInfos['uuid0']);
    const mediaItemInfo = mediaItemInfos['uuid1'];
    assertNotUndefined(mediaItemInfo);
    assertFalse(mediaItemInfo.isDynamic);
    assertTrue(mediaItemInfo.isSeekable);
    assertEquals(0, mediaItemInfo.defaultStartPositionUs);
    assertEquals(20 * 1000 * 1000, mediaItemInfo.windowDurationUs);
    assertEquals(1, mediaItemInfo.periods.length);
    assertEquals(20 * 1000 * 1000, mediaItemInfo.periods[0].durationUs);
  },

  /** Tests building a media item info with multiple periods. */
  testBuildMediaItemInfo_fromManifest_multiPeriod() {
    let mediaItemInfos = null;
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.addPlayerListener((state) => {
      mediaItemInfos = state.mediaItemsInfo;
    });
    // Setting manifest properties to emulate a multiperiod stream manifest.
    mocks.state().getManifest().periods.push({startTime: 20});
    mocks.state().manifestState.windowDuration = 50;
    player.seekToWindow(1, 0);
    player.prepare();

    const mediaItemInfo = mediaItemInfos['uuid1'];
    assertNotUndefined(mediaItemInfo);
    assertFalse(mediaItemInfo.isDynamic);
    assertTrue(mediaItemInfo.isSeekable);
    assertEquals(0, mediaItemInfo.defaultStartPositionUs);
    assertEquals(50 * 1000 * 1000, mediaItemInfo.windowDurationUs);
    assertEquals(2, mediaItemInfo.periods.length);
    assertEquals(20 * 1000 * 1000, mediaItemInfo.periods[0].durationUs);
    assertEquals(30 * 1000 * 1000, mediaItemInfo.periods[1].durationUs);
  },

  /** Tests building a media item info from a live manifest. */
  testBuildMediaItemInfo_fromManifest_live() {
    let mediaItemInfos = null;
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.addPlayerListener((state) => {
      mediaItemInfos = state.mediaItemsInfo;
    });
    // Setting manifest properties to emulate a live stream manifest.
    mocks.state().manifestState.isLive = true;
    mocks.state().manifestState.windowDuration = 30;
    mocks.state().manifestState.delay = 10;
    mocks.state().getManifest().periods.push({startTime: 20});
    player.seekToWindow(1, 0);
    player.prepare();

    const mediaItemInfo = mediaItemInfos['uuid1'];
    assertNotUndefined(mediaItemInfo);
    assertTrue(mediaItemInfo.isDynamic);
    assertTrue(mediaItemInfo.isSeekable);
    assertEquals(20 * 1000 * 1000, mediaItemInfo.defaultStartPositionUs);
    assertEquals(20 * 1000 * 1000, mediaItemInfo.windowDurationUs);
    assertEquals(2, mediaItemInfo.periods.length);
    assertEquals(20 * 1000 * 1000, mediaItemInfo.periods[0].durationUs);
    assertEquals(Infinity, mediaItemInfo.periods[1].durationUs);
  },

  /** Tests whether the shaka request filter is set for life streams. */
  testRequestFilterIsSetAndRemovedForLive() {
    player.addQueueItems(0, util.queue.slice(0, 3));

    // Set manifest properties to emulate a live stream manifest.
    mocks.state().manifestState.isLive = true;
    mocks.state().manifestState.windowDuration = 30;
    mocks.state().manifestState.delay = 10;
    mocks.state().getManifest().periods.push({startTime: 20});

    assertNull(mocks.state().responseFilter);
    assertFalse(player.isManifestFilterRegistered_);
    player.seekToWindow(1, 0);
    player.prepare();
    assertNotNull(mocks.state().responseFilter);
    assertTrue(player.isManifestFilterRegistered_);

    // Set manifest properties to emulate a non-live stream */
    mocks.state().manifestState.isLive = false;
    mocks.state().manifestState.windowDuration = 20;
    mocks.state().manifestState.delay = 0;
    mocks.state().getManifest().periods.push({startTime: 20});

    player.seekToWindow(0, 0);
    assertNull(mocks.state().responseFilter);
    assertFalse(player.isManifestFilterRegistered_);
  },

  /** Tests whether the media info is removed when queue item is removed. */
  testRemoveMediaItemInfo() {
    let mediaItemInfos = null;
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.addPlayerListener((state) => {
      mediaItemInfos = state.mediaItemsInfo;
    });
    player.seekToWindow(1, 0);
    player.prepare();
    assertNotUndefined(mediaItemInfos['uuid1']);
    player.removeQueueItems(['uuid1']);
    assertUndefined(mediaItemInfos['uuid1']);
  },

  /** Tests shuffling. */
  testSetShuffeModeEnabled() {
    let shuffleModeEnabled = false;
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.addPlayerListener((state) => {
      shuffleModeEnabled = state.shuffleModeEnabled;
    });
    player.setShuffleModeEnabled(true);
    assertTrue(shuffleModeEnabled);

    player.setShuffleModeEnabled(false);
    assertFalse(shuffleModeEnabled);
  },

  /** Tests setting a new playback order. */
  async testSetShuffleOrder() {
    const defaultOrder = [0, 1, 2];
    let shuffleOrder;
    player.addPlayerListener((state) => {
      shuffleOrder = state.shuffleOrder;
    });
    await player.addQueueItems(0, util.queue.slice(0, 3), defaultOrder);
    assertArrayEquals(defaultOrder, shuffleOrder);

    player.setShuffleOrder_([2, 1, 0]);
    assertArrayEquals([2, 1, 0], player.shuffleOrder_);
  },

  /** Tests setting a new playback order with incorrect length. */
  async testSetShuffleOrder_incorrectLength() {
    const defaultOrder = [0, 1, 2];
    let shuffleOrder;
    player.addPlayerListener((state) => {
      shuffleOrder = state.shuffleOrder;
    });
    await player.addQueueItems(0, util.queue.slice(0, 3), defaultOrder);
    assertArrayEquals(defaultOrder, shuffleOrder);

    shuffleOrder = undefined;
    player.setShuffleOrder_([2, 1]);
    assertUndefined(shuffleOrder);
  },

  /** Tests falling into ENDED when prepared with empty queue. */
  testPrepare_withEmptyQueue() {
    player.seekToUuid('uuid1000', 1000);
    assertEquals('uuid1000', player.uuidToPrepare_);
    player.prepare();
    assertEquals('ENDED', player.getPlaybackState());
    assertNull(player.uuidToPrepare_);
    player.seekToUuid('uuid1000', 1000);
    assertNull(player.uuidToPrepare_);
  },
});
