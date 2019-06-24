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
 * @fileoverview Unit tests for queue manipulations.
 */

goog.module('exoplayer.cast.test.queue');
goog.setTestOnly();

const ConfigurationFactory = goog.require('exoplayer.cast.ConfigurationFactory');
const Player = goog.require('exoplayer.cast.Player');
const mocks = goog.require('exoplayer.cast.test.mocks');
const testSuite = goog.require('goog.testing.testSuite');
const util = goog.require('exoplayer.cast.test.util');

let player;

testSuite({
  setUp() {
    mocks.setUp();
    player = new Player(mocks.createShakaFake(), new ConfigurationFactory());
  },

  /** Tests adding queue items. */
  testAddQueueItem() {
    let queue = [];
    player.addPlayerListener((state) => {
      queue = state.mediaQueue;
    });
    assertEquals(0, queue.length);
    player.addQueueItems(0, util.queue.slice(0, 3));
    assertEquals(util.queue[0].media.uri, queue[0].media.uri);
    assertEquals(util.queue[1].media.uri, queue[1].media.uri);
    assertEquals(util.queue[2].media.uri, queue[2].media.uri);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests that duplicate queue items are ignored. */
  testAddDuplicateQueueItem() {
    let queue = [];
    player.addPlayerListener((state) => {
      queue = state.mediaQueue;
    });
    assertEquals(0, queue.length);
    // Insert three items.
    player.addQueueItems(0, util.queue.slice(0, 3));
    // Insert two of which the first is a duplicate.
    player.addQueueItems(1, util.queue.slice(2, 4));
    assertEquals(4, queue.length);
    assertArrayEquals(
        ['uuid0', 'uuid3', 'uuid1', 'uuid2'], queue.slice().map((i) => i.uuid));
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests moving queue items. */
  testMoveQueueItem() {
    const shuffleOrder = [0, 2, 1];
    let queue = [];
    player.addPlayerListener((state) => {
      queue = state.mediaQueue;
    });
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.moveQueueItem('uuid0', 1, shuffleOrder);
    assertEquals(util.queue[1].media.uri, queue[0].media.uri);
    assertEquals(util.queue[0].media.uri, queue[1].media.uri);
    assertEquals(util.queue[2].media.uri, queue[2].media.uri);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);

    queue = undefined;
    // invalid to index
    player.moveQueueItem('uuid0', 11, [0, 1, 2]);
    assertTrue(typeof queue === 'undefined');
    assertArrayEquals(shuffleOrder, player.shuffleOrder_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, player.queue_);
    // negative to index
    player.moveQueueItem('uuid0', -11, shuffleOrder);
    assertTrue(typeof queue === 'undefined');
    assertArrayEquals(shuffleOrder, player.shuffleOrder_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, player.queue_);
    // unknown uuid
    player.moveQueueItem('unknown', 1, shuffleOrder);
    assertTrue(typeof queue === 'undefined');
    assertArrayEquals(shuffleOrder, player.shuffleOrder_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, player.queue_);
  },

  /** Tests removing queue items. */
  testRemoveQueueItems() {
    let queue = [];
    player.addPlayerListener((state) => {
      queue = state.mediaQueue;
    });
    player.addQueueItems(0, util.queue.slice(0, 3), [0, 2, 1]);
    player.prepare();
    player.seekToWindow(1, 0);
    assertEquals(1, player.getCurrentWindowIndex());
    util.assertUuidIndexMap(player.queueUuidIndexMap_, player.queue_);

    // Remove the first item.
    player.removeQueueItems(['uuid0']);
    assertEquals(2, queue.length);
    assertEquals(util.queue[1].media.uri, queue[0].media.uri);
    assertEquals(util.queue[2].media.uri, queue[1].media.uri);
    assertEquals(0, player.getCurrentWindowIndex());
    assertArrayEquals([1,0], player.shuffleOrder_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, player.queue_);

    // Calling stop without reseting preserves the queue.
    player.stop(false);
    assertEquals('uuid1', player.uuidToPrepare_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, player.queue_);

    // Remove the item at the end of the queue.
    player.removeQueueItems(['uuid2']);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, player.queue_);

    // Remove the last remaining item in the queue.
    player.removeQueueItems(['uuid1']);
    assertEquals(0, queue.length);
    assertEquals('IDLE', player.getPlaybackState());
    assertEquals(0, player.getCurrentWindowIndex());
    assertArrayEquals([], player.shuffleOrder_);
    assertNull(player.uuidToPrepare_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, player.queue_);
  },

  /** Tests removing multiple unordered queue items at once. */
  testRemoveQueueItems_multiple() {
    let queue = [];
    player.addPlayerListener((state) => {
      queue = state.mediaQueue;
    });
    player.addQueueItems(0, util.queue.slice(0, 6), []);
    player.prepare();

    assertEquals(6, queue.length);
    player.removeQueueItems(['uuid1', 'uuid5', 'uuid3']);
    assertArrayEquals(['uuid0', 'uuid2', 'uuid4'], queue.map((i) => i.uuid));
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests whether stopping with reset=true resets queue and uuidToIndexMap */
  testStop_resetTrue() {
    let queue = [];
    player.addPlayerListener((state) => {
      queue = state.mediaQueue;
    });
    player.addQueueItems(0, util.queue.slice(0, 3), [0, 2, 1]);
    player.prepare();
    player.stop(true);
    assertEquals(0, player.queue_.length);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },
});
