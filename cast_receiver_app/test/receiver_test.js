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
 * @fileoverview Unit tests for receiver.
 */

goog.module('exoplayer.cast.test.receiver');
goog.setTestOnly();

const ConfigurationFactory = goog.require('exoplayer.cast.ConfigurationFactory');
const MessageDispatcher = goog.require('exoplayer.cast.MessageDispatcher');
const Player = goog.require('exoplayer.cast.Player');
const Receiver = goog.require('exoplayer.cast.Receiver');
const mocks = goog.require('exoplayer.cast.test.mocks');
const testSuite = goog.require('goog.testing.testSuite');
const util = goog.require('exoplayer.cast.test.util');

/** @type {?Player|undefined} */
let player;
/** @type {!Array<!MediaItem>} */
let queue = [];
let shakaFake;
let castContextMock;

/**
 * Sends a message to the receiver under test.
 *
 * @param {!Object} message The message to send as json.
 */
const sendMessage = function(message) {
  mocks.state().customMessageListener({
    data: message,
    senderId: 'sender0',
  });
};

/**
 * Creates a valid media item with the suffix appended to each field.
 *
 * @param {string} suffix The suffix to append to the fields value.
 * @return {!Object} The media item.
 */
const createMediaItem = function(suffix) {
  return {
    uuid: 'uuid' + suffix,
    media: {uri: 'uri' + suffix},
    mimeType: 'application/dash+xml',
  };
};

let messageSequence = 0;

/**
 * Creates a message in the format sent bey the sender app.
 *
 * @param {string} method The name of the method.
 * @param {?Object} args The arguments.
 * @return {!Object} The message.
 */
const createMessage = function (method, args) {
  return {
    method: method,
    args: args,
    sequenceNumber: ++messageSequence,
  };
};

/**
 * Asserts the `playerState` is in the same state as just after creation of the
 * player.
 *
 * @param {!PlayerState} playerState The player state to assert.
 * @param {string} playbackState The expected playback state.
 */
const assertInitialState = function(playerState, playbackState) {
  assertEquals(playbackState, playerState.playbackState);
  // Assert the state is in initial state.
  assertArrayEquals([], queue);
  assertEquals(0, playerState.windowCount);
  assertEquals(0, playerState.windowIndex);
  assertUndefined(playerState.playbackError);
  assertNull(playerState.playbackPosition);
  // Assert player properties.
  assertEquals(0, player.getDurationMs());
  assertArrayEquals([], Object.entries(player.mediaItemInfoMap_));
  assertEquals(0, player.windowPeriodIndex_);
  assertEquals(999, player.playbackType_);
  assertEquals(0, player.getCurrentWindowIndex());
  assertEquals(Player.DUMMY_MEDIA_ITEM_INFO, player.windowMediaItemInfo_);
};


testSuite({
  setUp() {
    mocks.setUp();
    shakaFake = mocks.createShakaFake();
    castContextMock = mocks.createCastReceiverContextFake();
    player = new Player(shakaFake, new ConfigurationFactory());
    player.addPlayerListener((playerState) => {
      queue = playerState.mediaQueue;
    });
    const messageDispatcher = new MessageDispatcher(
        'urn:x-cast:com.google.exoplayer.cast', castContextMock);
    new Receiver(player, castContextMock, messageDispatcher);
  },

  tearDown() {
    queue = [];
  },

  /** Tests whether a status was sent to the sender on connect. */
  testNotifyClientConnected() {
    assertUndefined(mocks.state().outputMessages['sender0']);

    sendMessage(createMessage('player.onClientConnected', {}));
    const message = mocks.state().outputMessages['sender0'][0];
    assertEquals(messageSequence, message.sequenceNumber);
  },

  /**
   * Tests whether a custom message listener has been registered after
   * construction.
   */
  testCustomMessageListener() {
    assertTrue(goog.isFunction(mocks.state().customMessageListener));
  },

  /** Tests set playWhenReady. */
  testSetPlayWhenReady() {
    let playWhenReady;
    player.addPlayerListener((playerState) => {
      playWhenReady = playerState.playWhenReady;
    });

    sendMessage(createMessage(
        'player.setPlayWhenReady',
        { playWhenReady: true }
    ));
    assertTrue(playWhenReady);
    sendMessage(createMessage(
        'player.setPlayWhenReady',
        { playWhenReady: false }
    ));
    assertFalse(playWhenReady);
  },

  /** Tests setting repeat modes. */
  testSetRepeatMode() {
    let repeatMode;
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    player.addPlayerListener((playerState) => {
      repeatMode = playerState.repeatMode;
    });

    sendMessage(createMessage(
        'player.setRepeatMode',
        { repeatMode: Player.RepeatMode.ONE }
    ));
    assertEquals(Player.RepeatMode.ONE, repeatMode);
    assertEquals(0, player.getNextWindowIndex());
    assertEquals(0, player.getPreviousWindowIndex());

    sendMessage(createMessage(
        'player.setRepeatMode',
        { repeatMode: Player.RepeatMode.ALL }
    ));
    assertEquals(Player.RepeatMode.ALL, repeatMode);
    assertEquals(1, player.getNextWindowIndex());
    assertEquals(2, player.getPreviousWindowIndex());

    sendMessage(createMessage(
        'player.setRepeatMode',
        { repeatMode: Player.RepeatMode.OFF }
    ));
    assertEquals(Player.RepeatMode.OFF, repeatMode);
    assertEquals(1, player.getNextWindowIndex());
    assertTrue(player.getPreviousWindowIndex() < 0);
  },

  /** Tests setting an invalid repeat mode value. */
  testSetRepeatMode_invalid_noStateChange() {
    let repeatMode;
    player.addPlayerListener((playerState) => {
      repeatMode = playerState.repeatMode;
    });

    sendMessage(createMessage(
        'player.setRepeatMode',
        { repeatMode: "UNKNOWN" }
    ));
    assertEquals(Player.RepeatMode.OFF, player.repeatMode_);
    assertUndefined(repeatMode);
    player.invalidate();
    assertEquals(Player.RepeatMode.OFF, repeatMode);
  },

  /** Tests enabling and disabling shuffle mode. */
  testSetShuffleModeEnabled() {
    const enableMessage = createMessage('player.setShuffleModeEnabled', {
      shuffleModeEnabled: true,
    });
    const disableMessage = createMessage('player.setShuffleModeEnabled', {
      shuffleModeEnabled: false,
    });
    let shuffleModeEnabled;
    player.addPlayerListener((state) => {
      shuffleModeEnabled = state.shuffleModeEnabled;
    });
    assertFalse(player.shuffleModeEnabled_);
    sendMessage(enableMessage);
    assertTrue(shuffleModeEnabled);
    sendMessage(disableMessage);
    assertFalse(shuffleModeEnabled);
  },

  /** Tests adding a single media item to the queue. */
  testAddMediaItem_single() {
    const suffix = '0';
    const jsonMessage = createMessage('player.addItems', {
      index: 0,
      items: [
        createMediaItem(suffix),
      ],
      shuffleOrder: [0],
    });

    sendMessage(jsonMessage);
    assertEquals(1, queue.length);
    assertEquals('uuid0', queue[0].uuid);
    assertEquals('uri0', queue[0].media.uri);
    assertArrayEquals([0], player.shuffleOrder_);
  },

  /** Tests adding multiple media items to the queue. */
  testAddMediaItem_multiple() {
    const shuffleOrder = [0, 2, 1];
    const jsonMessage = createMessage('player.addItems', {
      index: 0,
      items: [
        createMediaItem('0'),
        createMediaItem('1'),
        createMediaItem('2'),
      ],
      shuffleOrder: shuffleOrder,
    });

    sendMessage(jsonMessage);
    assertArrayEquals(['uuid0', 'uuid1', 'uuid2'], queue.map((x) => x.uuid));
    assertArrayEquals(shuffleOrder, player.shuffleOrder_);
  },

  /** Tests adding a media item to end of the queue by omitting the index. */
  testAddMediaItem_noindex_addstoend() {
    const shuffleOrder = [1, 3, 2, 0];
    const jsonMessage = createMessage('player.addItems', {
      items: [createMediaItem('99')],
      shuffleOrder: shuffleOrder,
    });
    player.addQueueItems(0, util.queue.slice(0, 3), [0, 2, 1]);
    let queue = [];
    player.addPlayerListener((playerState) => {
      queue = playerState.mediaQueue;
    });
    sendMessage(jsonMessage);
    assertEquals(4, queue.length);
    assertEquals('uuid99', queue[3].uuid);
    assertArrayEquals(shuffleOrder, player.shuffleOrder_);
  },

  /** Tests adding items with a shuffle order of invalid length. */
  testAddMediaItems_invalidShuffleOrderLength() {
    const shuffleOrder = [1, 3, 2];
    const jsonMessage = createMessage('player.addItems', {
      items: [createMediaItem('99')],
      shuffleOrder: shuffleOrder,
    });
    player.addQueueItems(0, util.queue.slice(0, 3), [0, 2, 1]);
    let queue = [];
    player.addPlayerListener((playerState) => {
      queue = playerState.mediaQueue;
    });
    sendMessage(jsonMessage);
    assertEquals(4, queue.length);
    assertEquals('uuid99', queue[3].uuid);
    assertEquals(4, player.shuffleOrder_.length);
  },

  /** Tests inserting a media item to the queue. */
  testAddMediaItem_insert() {
    const index = 1;
    const shuffleOrder = [1, 0, 3, 2, 4];
    const firstInsertionMessage = createMessage('player.addItems', {
      index,
      items: [
        createMediaItem('99'),
        createMediaItem('100'),
      ],
      shuffleOrder,
    });
    const prepareMessage = createMessage('player.prepare', {});
    const secondInsertionMessage = createMessage('player.addItems', {
      index,
      items: [
        createMediaItem('199'),
        createMediaItem('1100'),
      ],
      shuffleOrder,
    });
    // fill with three items
    player.addQueueItems(0, util.queue.slice(0, 3), [0, 2, 1]);
    player.seekToUuid('uuid99', 0);

    sendMessage(firstInsertionMessage);
    // The window index does not change when IDLE.
    assertEquals(1, player.getCurrentWindowIndex());
    assertEquals(5, queue.length);
    assertArrayEquals(shuffleOrder, player.shuffleOrder_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);

    // Prepare sets the index by the uuid to which we seeked.
    sendMessage(prepareMessage);
    assertEquals(1, player.getCurrentWindowIndex());
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
    // Add two items at the current window index.
    sendMessage(secondInsertionMessage);
    // Current window index is adjusted.
    assertEquals(3, player.getCurrentWindowIndex());
    assertEquals(7, queue.length);
    assertEquals('uuid199', queue[index].uuid);
    assertEquals(7, player.shuffleOrder_.length);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests adding a media item with an index larger than the queue size. */
  testAddMediaItem_indexLargerThanQueueSize_addsToEnd() {
    const index = 4;
    const jsonMessage = createMessage('player.addItems', {
      index: index,
      items: [
        createMediaItem('99'),
        createMediaItem('100'),
      ],
      shuffleOrder: [],
    });
    player.addQueueItems(0, util.queue.slice(0, 3), [0, 2, 1]);

    assertArrayEquals(['uuid0', 'uuid1', 'uuid2'], queue.map((x) => x.uuid));
    sendMessage(jsonMessage);
    assertArrayEquals(['uuid0', 'uuid1', 'uuid2', 'uuid99', 'uuid100'],
        queue.map((x) => x.uuid));
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests removing an item from the queue. */
  testRemoveMediaItem() {
    const jsonMessage =
        createMessage('player.removeItems', {uuids: ['uuid1', 'uuid0']});
    player.addQueueItems(0, util.queue.slice(0, 3), [0, 2, 1]);
    assertArrayEquals(['uuid0', 'uuid1', 'uuid2'], queue.map((x) => x.uuid));

    sendMessage(jsonMessage);
    assertArrayEquals(['uuid2'], queue.map((x) => x.uuid));
    assertArrayEquals([0], player.shuffleOrder_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests removing the currently playing item from the queue. */
  async testRemoveMediaItem_currentItem() {
    const jsonMessage =
        createMessage('player.removeItems', {uuids: ['uuid1', 'uuid0']});
    player.addQueueItems(0, util.queue.slice(0, 3), [0, 2, 1]);
    player.seekToWindow(1, 0);
    player.prepare();

    await sendMessage(jsonMessage);
    assertArrayEquals(['uuid2'], queue.map((x) => x.uuid));
    assertEquals(0, player.getCurrentWindowIndex());
    assertEquals(util.queue[2].media.uri, shakaFake.getMediaElement().src);
    assertArrayEquals([0], player.shuffleOrder_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests removing items which affect the current window index. */
  async testRemoveMediaItem_affectsWindowIndex() {
    const jsonMessage =
        createMessage('player.removeItems', {uuids: ['uuid1', 'uuid0']});
    const currentUri = util.queue[4].media.uri;
    player.addQueueItems(0, util.queue.slice(0, 6), [3, 2, 1, 4, 0, 5]);
    player.prepare();
    await player.seekToWindow(4, 2000);
    assertEquals(currentUri, shakaFake.getMediaElement().src);

    sendMessage(jsonMessage);
    assertEquals(4, queue.length);
    assertEquals('uuid4', queue[player.getCurrentWindowIndex()].uuid);
    assertEquals(2, player.getCurrentWindowIndex());
    assertEquals(currentUri, shakaFake.getMediaElement().src);
    assertArrayEquals([1, 0, 2, 3], player.shuffleOrder_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests removing the last item of the queue. */
  testRemoveMediaItem_firstItem_windowIndexIsCorrect() {
    const jsonMessage =
        createMessage('player.removeItems', {uuids: ['uuid0']});
    player.addQueueItems(0, util.queue.slice(0, 3), [0, 2, 1]);
    player.seekToWindow(1, 0);

    sendMessage(jsonMessage);
    assertArrayEquals(['uuid1', 'uuid2'], queue.map((x) => x.uuid));
    assertEquals(0, player.getCurrentWindowIndex());
    assertArrayEquals([1, 0], player.shuffleOrder_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests removing the last item of the queue. */
  testRemoveMediaItem_lastItem_windowIndexIsCorrect() {
    const jsonMessage =
        createMessage('player.removeItems', {uuids: ['uuid2']});
    player.addQueueItems(0, util.queue.slice(0, 3), [0, 2, 1]);
    player.seekToWindow(2, 0);
    player.prepare();

    mocks.state().isSilent = true;
    const states = [];
    player.addPlayerListener((playerState) => {
      states.push(playerState);
    });
    sendMessage(jsonMessage);
    assertArrayEquals(['uuid0', 'uuid1'], queue.map((x) => x.uuid));
    assertEquals(1, player.getCurrentWindowIndex());
    assertArrayEquals([0, 1], player.shuffleOrder_);
    assertEquals(1, states.length);
    assertEquals(Player.PlaybackState.BUFFERING, states[0].playbackState);
    assertEquals(
        Player.DiscontinuityReason.PERIOD_TRANSITION,
        states[0].playbackPosition.discontinuityReason);
    assertEquals(
        Player.DUMMY_MEDIA_ITEM_INFO, states[0].mediaItemsInfo['uuid1']);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests removing items all items. */
  testRemoveMediaItem_removeAll() {
    const jsonMessage = createMessage('player.removeItems',
        {uuids: ['uuid1', 'uuid0', 'uuid2']});
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.seekToWindow(2, 2000);
    player.prepare();
    let playerState;
    player.addPlayerListener((state) => {
      playerState = state;
    });

    sendMessage(jsonMessage);
    assertInitialState(playerState, 'ENDED');
    assertEquals(0, player.getCurrentWindowIndex());
    assertArrayEquals([], player.shuffleOrder_);
    assertEquals(Player.PlaybackState.ENDED, player.getPlaybackState());
    util.assertUuidIndexMap(player.queueUuidIndexMap_, []);
  },

  /** Tests moving an item in the queue. */
  testMoveItem() {
    let shuffleOrder = [0, 2, 1];
    const jsonMessage = createMessage('player.moveItem', {
      uuid: 'uuid2',
      index: 0,
      shuffleOrder: shuffleOrder,
    });
    player.addQueueItems(0, util.queue.slice(0, 3));

    assertArrayEquals(['uuid0', 'uuid1', 'uuid2'], queue.map((x) => x.uuid));
    sendMessage(jsonMessage);
    assertArrayEquals(['uuid2', 'uuid0', 'uuid1'], queue.map((x) => x.uuid));
    assertArrayEquals(shuffleOrder, player.shuffleOrder_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests moving the currently playing item in the queue. */
  testMoveItem_currentWindowIndex() {
    let shuffleOrder = [0, 2, 1];
    const jsonMessage = createMessage('player.moveItem', {
      uuid: 'uuid2',
      index: 0,
      shuffleOrder: shuffleOrder,
    });
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    player.seekToUuid('uuid2', 0);
    assertEquals(2, player.getCurrentWindowIndex());

    assertArrayEquals(['uuid0', 'uuid1', 'uuid2'], queue.map((x) => x.uuid));
    sendMessage(jsonMessage);
    assertArrayEquals(['uuid2', 'uuid0', 'uuid1'], queue.map((x) => x.uuid));
    assertEquals(0, player.getCurrentWindowIndex());
    assertArrayEquals(shuffleOrder, player.shuffleOrder_);
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests moving an item from before to after the currently playing item. */
  testMoveItem_decreaseCurrentWindowIndex() {
    const jsonMessage = createMessage('player.moveItem', {
      uuid: 'uuid0',
      index: 5,
      shuffleOrder: [],
    });
    player.addQueueItems(0, util.queue.slice(0, 6));
    player.prepare();
    player.seekToWindow(2, 0);
    assertEquals(2, player.getCurrentWindowIndex());

    assertArrayEquals(['uuid0', 'uuid1', 'uuid2', 'uuid3', 'uuid4', 'uuid5'],
        queue.map((x) => x.uuid));
    sendMessage(jsonMessage);
    assertArrayEquals(['uuid1', 'uuid2', 'uuid3', 'uuid4', 'uuid5', 'uuid0'],
        queue.map((x) => x.uuid));
    assertEquals(1, player.getCurrentWindowIndex());
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests moving an item from after to before the currently playing item. */
  testMoveItem_increaseCurrentWindowIndex() {
    const jsonMessage = createMessage('player.moveItem', {
      uuid: 'uuid5',
      index: 0,
      shuffleOrder: [],
    });
    player.addQueueItems(0, util.queue.slice(0, 6));
    player.prepare();
    player.seekToWindow(2, 0);
    assertEquals(2, player.getCurrentWindowIndex());

    assertArrayEquals(['uuid0', 'uuid1', 'uuid2', 'uuid3', 'uuid4', 'uuid5'],
        queue.map((x) => x.uuid));
    sendMessage(jsonMessage);
    assertArrayEquals(['uuid5', 'uuid0', 'uuid1', 'uuid2', 'uuid3', 'uuid4'],
        queue.map((x) => x.uuid));
    assertEquals(3, player.getCurrentWindowIndex());
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests moving an item from after to the current window index.  */
  testMoveItem_toCurrentWindowIndex_fromAfter() {
    const jsonMessage = createMessage('player.moveItem', {
      uuid: 'uuid5',
      index: 2,
      shuffleOrder: [],
    });
    player.addQueueItems(0, util.queue.slice(0, 6));
    player.prepare();
    player.seekToWindow(2, 0);
    assertEquals(2, player.getCurrentWindowIndex());

    assertArrayEquals(['uuid0', 'uuid1', 'uuid2', 'uuid3', 'uuid4', 'uuid5'],
        queue.map((x) => x.uuid));
    sendMessage(jsonMessage);
    assertArrayEquals(['uuid0', 'uuid1', 'uuid5', 'uuid2', 'uuid3', 'uuid4'],
        queue.map((x) => x.uuid));
    assertEquals(3, player.getCurrentWindowIndex());
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests moving an item from before to the current window index.  */
  testMoveItem_toCurrentWindowIndex_fromBefore() {
    const jsonMessage = createMessage('player.moveItem', {
      uuid: 'uuid0',
      index: 2,
      shuffleOrder: [],
    });
    player.addQueueItems(0, util.queue.slice(0, 6));
    player.prepare();
    player.seekToWindow(2, 0);
    assertEquals(2, player.getCurrentWindowIndex());

    assertArrayEquals(['uuid0', 'uuid1', 'uuid2', 'uuid3', 'uuid4', 'uuid5'],
        queue.map((x) => x.uuid));
    sendMessage(jsonMessage);
    assertArrayEquals(['uuid1', 'uuid2', 'uuid0', 'uuid3', 'uuid4', 'uuid5'],
        queue.map((x) => x.uuid));
    assertEquals(1, player.getCurrentWindowIndex());
    util.assertUuidIndexMap(player.queueUuidIndexMap_, queue);
  },

  /** Tests seekTo. */
  testSeekTo() {
    const jsonMessage = createMessage('player.seekTo',
        {
          'uuid': 'uuid1',
          'positionMs': 2000
        });
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    sendMessage(jsonMessage);
    assertEquals(2000, player.getCurrentPositionMs());
    assertEquals(1, player.getCurrentWindowIndex());
  },

  /** Tests seekTo to unknown uuid. */
  testSeekTo_unknownUuid() {
    const jsonMessage = createMessage('player.seekTo',
        {
          'uuid': 'unknown',
        });
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    player.seekToWindow(1, 2000);
    assertEquals(2000, player.getCurrentPositionMs());
    assertEquals(1, player.getCurrentWindowIndex());

    sendMessage(jsonMessage);
    assertEquals(2000, player.getCurrentPositionMs());
    assertEquals(1, player.getCurrentWindowIndex());
  },

  /** Tests seekTo without position. */
  testSeekTo_noPosition_defaultsToZero() {
    const jsonMessage = createMessage('player.seekTo',
        {
          'uuid': 'uuid1',
        });
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    sendMessage(jsonMessage);
    assertEquals(0, player.getCurrentPositionMs());
    assertEquals(1, player.getCurrentWindowIndex());
  },

  /** Tests seekTo to negative position. */
  testSeekTo_negativePosition_defaultsToZero() {
    const jsonMessage = createMessage('player.seekTo',
        {
          'uuid': 'uuid2',
          'positionMs': -1,
        });
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    player.seekToWindow(1, 2000);
    assertEquals(2000, player.getCurrentPositionMs());
    assertEquals(1, player.getCurrentWindowIndex());

    sendMessage(jsonMessage);
    assertEquals(0, player.getCurrentPositionMs());
    assertEquals(2, player.getCurrentWindowIndex());
  },

  /** Tests whether validation is turned on. */
  testMediaItemValidation_isOn() {
    const index = 0;
    const mediaItem = createMediaItem('99');
    delete mediaItem.uuid;
    const jsonMessage = createMessage('player.addItems', {
      index: index,
      items: [mediaItem],
      shuffleOrder: [],
    });

    sendMessage(jsonMessage);
    assertEquals(0, queue.length);
  },

  /** Tests whether the state is sent to sender apps on state transition. */
  testPlayerStateIsSent_withCorrectSequenceNumber() {
    assertUndefined(mocks.state().outputMessages['sender0']);
    const playMessage =
        createMessage('player.setPlayWhenReady', {playWhenReady: true});
    sendMessage(playMessage);

    const playerState = mocks.state().outputMessages['sender0'][0];
    assertTrue(playerState.playWhenReady);
    assertEquals(playMessage.sequenceNumber, playerState.sequenceNumber);
  },

  /** Tests whether a connect of a sender app sends the current player state. */
  testSenderConnection() {
    const onSenderConnected = mocks.state().onSenderConnected;
    assertTrue(goog.isFunction(onSenderConnected));
    onSenderConnected({senderId: 'sender0'});

    const playerState = mocks.state().outputMessages['sender0'][0];
    assertEquals(Player.RepeatMode.OFF, playerState.repeatMode);
    assertEquals('IDLE', playerState.playbackState);
    assertArrayEquals([], playerState.mediaQueue);
    assertEquals(-1, playerState.sequenceNumber);
  },

  /** Tests whether a disconnect of a sender notifies the message dispatcher. */
  testSenderDisconnection_callsMessageDispatcher() {
    mocks.setUp();
    let notifiedSenderId;
    const myPlayer = new Player(mocks.createShakaFake());
    const myManagerFake = mocks.createCastReceiverContextFake();
    new Receiver(myPlayer, myManagerFake, {
      registerActionHandler() {},
      notifySenderDisconnected(senderId) {
        notifiedSenderId = senderId;
      },
    });

    const onSenderDisconnected = mocks.state().onSenderDisconnected;
    assertTrue(goog.isFunction(onSenderDisconnected));
    onSenderDisconnected({senderId: 'sender0'});
    assertEquals('sender0', notifiedSenderId);
  },

  /**
   * Tests whether the state right after creation of the player matches
   * expectations.
   */
  testInitialState() {
    mocks.state().isSilent = true;
    let playerState;
    player.addPlayerListener((state) => {
      playerState = state;
    });
    assertEquals(0, player.getCurrentPositionMs());
    // Dump a player state to the listener.
    player.invalidate();
    // Asserts the state just after creation.
    assertInitialState(playerState, 'IDLE');
  },

  /** Tests whether user properties can be changed when in IDLE state */
  testChangingUserPropertiesWhenIdle() {
    mocks.state().isSilent = true;
    const states = [];
    let counter = 0;
    player.addPlayerListener((state) => {
      states.push(state);
    });
    // Adding items when IDLE.
    player.addQueueItems(0, util.queue.slice(0, 3));
    counter++;
    assertEquals(counter, states.length);
    assertEquals(Player.PlaybackState.IDLE, states[counter - 1].playbackState);
    assertArrayEquals(
        ['uuid0', 'uuid1', 'uuid2'],
        states[counter - 1].mediaQueue.map((i) => i.uuid));

    // Set playWhenReady when IDLE.
    assertFalse(player.getPlayWhenReady());
    player.setPlayWhenReady(true);
    counter++;
    assertTrue(player.getPlayWhenReady());
    assertEquals(counter, states.length);
    assertEquals(Player.PlaybackState.IDLE, states[counter - 1].playbackState);

    // Seeking when IDLE.
    player.seekToUuid('uuid2', 1000);
    counter++;
    // Window index not set when idle.
    assertEquals(2, player.getCurrentWindowIndex());
    assertEquals(1000, player.getCurrentPositionMs());
    assertEquals(counter, states.length);
    assertEquals(Player.PlaybackState.IDLE, states[counter - 1].playbackState);
    // But window index is set when prepared.
    player.prepare();
    assertEquals(2, player.getCurrentWindowIndex());
  },

  /** Tests the state after calling prepare. */
  testPrepare() {
    mocks.state().isSilent = true;
    const states = [];
    let counter = 0;
    player.addPlayerListener((state) => {
      states.push(state);
    });
    const prepareMessage = createMessage('player.prepare', {});

    player.addQueueItems(0, util.queue.slice(0, 3));
    player.seekToWindow(1, 1000);
    counter += 2;

    // Sends prepare message.
    sendMessage(prepareMessage);
    counter++;
    assertEquals(counter, states.length);
    assertEquals('uuid1', states[counter - 1].playbackPosition.uuid);
    assertEquals(
        Player.PlaybackState.BUFFERING, states[counter - 1].playbackState);

    // Fakes Shaka events.
    mocks.state().isSilent = false;
    mocks.notifyListeners('streaming');
    mocks.notifyListeners('loadeddata');
    counter += 2;
    assertEquals(counter, states.length);
    assertEquals(Player.PlaybackState.READY, states[counter - 1].playbackState);
  },

  /** Tests stopping the player with `reset=true`. */
  testStop_resetTrue() {
    mocks.state().isSilent = true;
    let playerState;
    player.addPlayerListener((state) => {
      playerState = state;
    });
    const stopMessage = createMessage('player.stop', {reset: true});

    player.setRepeatMode(Player.RepeatMode.ALL);
    player.setShuffleModeEnabled(true);
    player.setPlayWhenReady(true);
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    mocks.state().isSilent = false;
    mocks.notifyListeners('loadeddata');
    assertArrayEquals(['uuid0', 'uuid1', 'uuid2'], queue.map((i) => i.uuid));
    assertEquals(0, playerState.windowIndex);
    assertNotEquals(Player.DUMMY_MEDIA_ITEM_INFO, player.windowMediaItemInfo_);
    assertEquals(1, player.playbackType_);
    // Stop the player.
    sendMessage(stopMessage);
    // Asserts the state looks the same as just after creation.
    assertInitialState(playerState, 'IDLE');
    assertNull(playerState.playbackPosition);
    // Assert player properties are preserved.
    assertTrue(playerState.shuffleModeEnabled);
    assertTrue(playerState.playWhenReady);
    assertEquals(Player.RepeatMode.ALL, playerState.repeatMode);
  },

  /** Tests stopping the player with `reset=false`. */
  testStop_resetFalse() {
    mocks.state().isSilent = true;
    let playerState;
    player.addPlayerListener((state) => {
      playerState = state;
    });
    const stopMessage = createMessage('player.stop', {reset: false});

    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    player.seekToUuid('uuid1', 1000);
    mocks.state().isSilent = false;
    mocks.notifyListeners('streaming');
    mocks.notifyListeners('trackschanged');
    mocks.notifyListeners('loadeddata');
    assertArrayEquals(['uuid0', 'uuid1', 'uuid2'], queue.map((i) => i.uuid));
    assertEquals(1, playerState.windowIndex);
    assertNotEquals(Player.DUMMY_MEDIA_ITEM_INFO, player.windowMediaItemInfo_);
    assertEquals(2, player.playbackType_);
    // Stop the player.
    sendMessage(stopMessage);
    assertEquals('IDLE', playerState.playbackState);
    assertUndefined(playerState.playbackError);
    // Assert the timeline is preserved.
    assertEquals(3, queue.length);
    assertEquals(3, playerState.windowCount);
    assertEquals(1, player.windowIndex_);
    assertEquals(1, playerState.windowIndex);
    // Assert the playback position is correct.
    assertEquals(1000, playerState.playbackPosition.positionMs);
    assertEquals('uuid1', playerState.playbackPosition.uuid);
    assertEquals(0, playerState.playbackPosition.periodId);
    assertNull(playerState.playbackPosition.discontinuityReason);
    assertEquals(1000, player.getCurrentPositionMs());
    // Assert player properties are preserved.
    assertEquals(20000, player.getDurationMs());
    assertEquals(2, Object.entries(player.mediaItemInfoMap_).length);
    assertEquals(0, player.windowPeriodIndex_);
    assertEquals(1, player.getCurrentWindowIndex());
    assertEquals(1, player.windowIndex_);
    assertNotEquals(Player.DUMMY_MEDIA_ITEM_INFO, player.windowMediaItemInfo_);
    assertEquals(999, player.playbackType_);
    assertEquals('uuid1', player.uuidToPrepare_);
  },

  /**
   * Tests the state after having removed the last item in the queue. This
   * resolves to the same state like calling `stop(true)` except that the state
   * is ENDED and the queue is naturally empty and hence the windowIndex is
   * unset.
   */
  testRemoveLastQueueItem() {
    mocks.state().isSilent = true;
    let playerState;
    player.addPlayerListener((state) => {
      playerState = state;
    });
    const removeAllItemsMessage = createMessage(
        'player.removeItems', {uuids: ['uuid0', 'uuid1', 'uuid2']});

    player.addQueueItems(0, util.queue.slice(0, 3));
    player.seekToWindow(0, 1000);
    player.prepare();
    mocks.state().isSilent = false;
    mocks.notifyListeners('loadeddata');
    // Remove all items.
    sendMessage(removeAllItemsMessage);
    // Assert the state after removal of all items.
    assertInitialState(playerState, 'ENDED');
  },

  /** Tests whether a player state is sent when no item is added. */
  testAddItem_noop() {
    mocks.state().isSilent = true;
    let playerStates = [];
    player.addPlayerListener((state) => {
      playerStates.push(state);
    });
    const noOpMessage = createMessage('player.addItems', {
      index: 0,
      items: [
        util.queue[0],
      ],
      shuffleOrder: [0],
    });
    player.addQueueItems(0, [util.queue[0]], []);
    player.prepare();
    assertEquals(2, playerStates.length);
    assertEquals(2, mocks.state().outputMessages['sender0'].length);
    sendMessage(noOpMessage);
    assertEquals(2, playerStates.length);
    assertEquals(3, mocks.state().outputMessages['sender0'].length);
  },

  /** Tests whether a player state is sent when no item is removed. */
  testRemoveItem_noop() {
    mocks.state().isSilent = true;
    let playerStates = [];
    player.addPlayerListener((state) => {
      playerStates.push(state);
    });
    const noOpMessage =
        createMessage('player.removeItems', {uuids: ['uuid00']});
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    assertEquals(2, playerStates.length);
    assertEquals(2, mocks.state().outputMessages['sender0'].length);
    sendMessage(noOpMessage);
    assertEquals(2, playerStates.length);
    assertEquals(3, mocks.state().outputMessages['sender0'].length);
  },

  /** Tests whether a player state is sent when item is not moved. */
  testMoveItem_noop() {
    mocks.state().isSilent = true;
    let playerStates = [];
    player.addPlayerListener((state) => {
      playerStates.push(state);
    });
    const noOpMessage = createMessage('player.moveItem', {
      uuid: 'uuid00',
      index: 0,
      shuffleOrder: [],
    });
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();
    assertEquals(2, playerStates.length);
    assertEquals(2, mocks.state().outputMessages['sender0'].length);
    sendMessage(noOpMessage);
    assertEquals(2, playerStates.length);
    assertEquals(3, mocks.state().outputMessages['sender0'].length);
  },

  /** Tests whether playback actions send a state when no-op */
  testNoOpPlaybackActionsSendPlayerState() {
    mocks.state().isSilent = true;
    let playerStates = [];
    let parsedMessage;
    player.addPlayerListener((state) => {
      playerStates.push(state);
    });
    player.addQueueItems(0, util.queue.slice(0, 3));
    player.prepare();

    const outputMessages = mocks.state().outputMessages['sender0'];
    const setupMessageCount = playerStates.length;
    let totalMessageCount = setupMessageCount;
    assertEquals(setupMessageCount, playerStates.length);
    assertEquals(totalMessageCount, outputMessages.length);

    const firstNoOpMessage = createMessage('player.setPlayWhenReady', {
      playWhenReady: false,
    });
    let expectedSequenceNumber = firstNoOpMessage.sequenceNumber;

    sendMessage(firstNoOpMessage);
    totalMessageCount++;
    assertEquals(setupMessageCount, playerStates.length);
    assertEquals(totalMessageCount, outputMessages.length);
    parsedMessage = outputMessages[totalMessageCount - 1];
    assertEquals(expectedSequenceNumber++, parsedMessage.sequenceNumber);

    sendMessage(createMessage('player.setRepeatMode', {
      repeatMode: 'OFF',
    }));
    totalMessageCount++;
    assertEquals(setupMessageCount, playerStates.length);
    assertEquals(totalMessageCount, outputMessages.length);
    parsedMessage = outputMessages[totalMessageCount - 1];
    assertEquals(expectedSequenceNumber++, parsedMessage.sequenceNumber);

    sendMessage(createMessage('player.setShuffleModeEnabled', {
      shuffleModeEnabled: false,
    }));
    totalMessageCount++;
    assertEquals(setupMessageCount, playerStates.length);
    assertEquals(totalMessageCount, outputMessages.length);
    parsedMessage = outputMessages[totalMessageCount - 1];
    assertEquals(expectedSequenceNumber++, parsedMessage.sequenceNumber);

    sendMessage(createMessage('player.seekTo', {
      uuid: 'not_existing',
      positionMs: 0,
    }));
    totalMessageCount++;
    assertEquals(setupMessageCount, playerStates.length);
    assertEquals(totalMessageCount, outputMessages.length);
    parsedMessage = outputMessages[totalMessageCount - 1];
    assertEquals(expectedSequenceNumber++, parsedMessage.sequenceNumber);
  },
});
