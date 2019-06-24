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
 */

goog.module('exoplayer.cast.Receiver');

const MessageDispatcher = goog.require('exoplayer.cast.MessageDispatcher');
const Player = goog.require('exoplayer.cast.Player');
const validation = goog.require('exoplayer.cast.validation');

/**
 * The Receiver receives messages from a message bus and delegates to
 * the player.
 *
 * @constructor
 * @param {!Player} player The player.
 * @param {!cast.framework.CastReceiverContext} context The cast receiver
 *     context.
 * @param {!MessageDispatcher} messageDispatcher The message dispatcher to use.
 */
const Receiver = function(player, context, messageDispatcher) {
  addPlayerActions(messageDispatcher, player);
  addQueueActions(messageDispatcher, player);
  player.addPlayerListener((playerState) => {
    messageDispatcher.broadcast(playerState);
  });

  context.addEventListener(
      cast.framework.system.EventType.SENDER_CONNECTED, (event) => {
        messageDispatcher.send(event.senderId, player.getPlayerState());
      });

  context.addEventListener(
      cast.framework.system.EventType.SENDER_DISCONNECTED, (event) => {
        messageDispatcher.notifySenderDisconnected(event.senderId);
        if (event.reason ===
                cast.framework.system.DisconnectReason.REQUESTED_BY_SENDER &&
            context.getSenders().length === 0) {
          window.close();
        }
      });

  // Start the cast receiver context.
  context.start();
};

/**
 * Registers action handlers for playback messages sent by the sender app.
 *
 * @param {!MessageDispatcher} messageDispatcher The dispatcher.
 * @param {!Player} player The player.
 */
const addPlayerActions = function(messageDispatcher, player) {
  messageDispatcher.registerActionHandler(
      'player.setPlayWhenReady', [['playWhenReady', 'boolean']],
      (args, senderSequence, senderId, callback) => {
        const playWhenReady = args['playWhenReady'];
        callback(
            !player.setPlayWhenReady(playWhenReady) ?
                player.getPlayerState() :
                null);
      });
  messageDispatcher.registerActionHandler(
      'player.seekTo',
      [
        ['uuid', 'string'],
        ['positionMs', '?number'],
      ],
      (args, senderSequence, senderId, callback) => {
        callback(
            !player.seekToUuid(args['uuid'], args['positionMs']) ?
                player.getPlayerState() :
                null);
      });
  messageDispatcher.registerActionHandler(
      'player.setRepeatMode', [['repeatMode', 'RepeatMode']],
      (args, senderSequence, senderId, callback) => {
        callback(
            !player.setRepeatMode(args['repeatMode']) ?
                player.getPlayerState() :
                null);
      });
  messageDispatcher.registerActionHandler(
      'player.setShuffleModeEnabled', [['shuffleModeEnabled', 'boolean']],
      (args, senderSequence, senderId, callback) => {
        callback(
            !player.setShuffleModeEnabled(args['shuffleModeEnabled']) ?
                player.getPlayerState() :
                null);
      });
  messageDispatcher.registerActionHandler(
      'player.onClientConnected', [],
      (args, senderSequence, senderId, callback) => {
        callback(player.getPlayerState());
      });
  messageDispatcher.registerActionHandler(
      'player.stop', [['reset', 'boolean']],
      (args, senderSequence, senderId, callback) => {
        player.stop(args['reset']).then(() => {
          callback(null);
        });
      });
  messageDispatcher.registerActionHandler(
      'player.prepare', [], (args, senderSequence, senderId, callback) => {
        player.prepare();
        callback(null);
      });
  messageDispatcher.registerActionHandler(
      'player.setTrackSelectionParameters',
      [
        ['preferredAudioLanguage', 'string'],
        ['preferredTextLanguage', 'string'],
        ['disabledTextTrackSelectionFlags', 'Array'],
        ['selectUndeterminedTextLanguage', 'boolean'],
      ],
      (args, senderSequence, senderId, callback) => {
        const trackSelectionParameters =
            /** @type {!TrackSelectionParameters} */ ({
              preferredAudioLanguage: args['preferredAudioLanguage'],
              preferredTextLanguage: args['preferredTextLanguage'],
              disabledTextTrackSelectionFlags:
                  args['disabledTextTrackSelectionFlags'],
              selectUndeterminedTextLanguage:
                  args['selectUndeterminedTextLanguage'],
            });
        callback(
            !player.setTrackSelectionParameters(trackSelectionParameters) ?
                player.getPlayerState() :
                null);
      });
};

/**
 * Registers action handlers for queue management messages sent by the sender
 * app.
 *
 * @param {!MessageDispatcher} messageDispatcher The dispatcher.
 * @param {!Player} player The player.
 */
const addQueueActions =
    function (messageDispatcher, player) {
  messageDispatcher.registerActionHandler(
    'player.addItems',
    [
      ['index', '?number'],
      ['items', 'Array'],
      ['shuffleOrder', 'Array'],
    ],
    (args, senderSequence, senderId, callback) => {
      const mediaItems = args['items'];
      const index = args['index'] || player.getQueueSize();
      let addedItemCount;
      if (validation.validateMediaItems(mediaItems)) {
        addedItemCount =
            player.addQueueItems(index, mediaItems, args['shuffleOrder']);
      }
      callback(addedItemCount === 0 ? player.getPlayerState() : null);
    });
  messageDispatcher.registerActionHandler(
      'player.removeItems', [['uuids', 'Array']],
      (args, senderSequence, senderId, callback) => {
        const removedItemsCount = player.removeQueueItems(args['uuids']);
        callback(removedItemsCount === 0 ? player.getPlayerState() : null);
      });
  messageDispatcher.registerActionHandler(
      'player.moveItem',
      [
        ['uuid', 'string'],
        ['index', 'number'],
        ['shuffleOrder', 'Array'],
      ],
      (args, senderSequence, senderId, callback) => {
        const hasMoved = player.moveQueueItem(
            args['uuid'], args['index'], args['shuffleOrder']);
        callback(!hasMoved ? player.getPlayerState() : null);
      });
};

exports = Receiver;
