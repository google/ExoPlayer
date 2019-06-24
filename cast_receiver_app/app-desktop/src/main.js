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

goog.module('exoplayer.cast.debug');

const ConfigurationFactory = goog.require('exoplayer.cast.ConfigurationFactory');
const PlaybackInfoView = goog.require('exoplayer.cast.PlaybackInfoView');
const Player = goog.require('exoplayer.cast.Player');
const PlayerControls = goog.require('exoplayer.cast.PlayerControls');
const ShakaPlayer = goog.require('shaka.Player');
const SimpleTextDisplayer = goog.require('shaka.text.SimpleTextDisplayer');
const installAll = goog.require('shaka.polyfill.installAll');
const util = goog.require('exoplayer.cast.util');

/** @type {!Array<!MediaItem>} */
let queue = [];
/** @type {number} */
let uuidCounter = 1;

// install all polyfills for the Shaka player
installAll();

/**
 * Listens for player state changes and logs the state to the console.
 *
 * @param {!PlayerState} playerState The player state.
 */
const playerListener = function(playerState) {
  util.log(['playerState: ', playerState.playbackPosition, playerState]);
  queue = playerState.mediaQueue;
  highlightCurrentItem(
      playerState.playbackPosition && playerState.playbackPosition.uuid ?
          playerState.playbackPosition.uuid :
          '');
  if (playerState.playWhenReady && playerState.playbackState === 'READY') {
    document.body.classList.add('playing');
  } else {
    document.body.classList.remove('playing');
  }
  if (playerState.playbackState === 'IDLE' && queue.length === 0) {
    // Stop has been called or player not yet prepared.
    resetSampleList();
  }
};

/**
 * Highlights the currently playing item in the samples list.
 *
 * @param {string} uuid
 */
const highlightCurrentItem = function(uuid) {
  const actions = /** @type {!NodeList<!HTMLElement>} */ (
      document.querySelectorAll('#media-actions .action'));
  for (let action of actions) {
    if (action.dataset['uuid'] === uuid) {
      action.classList.add('prepared');
    } else {
      action.classList.remove('prepared');
    }
  }
};

/**
 * Makes sure all items reflect being removed from the timeline.
 */
const resetSampleList = function() {
  const actions = /** @type {!NodeList<!HTMLElement>} */ (
      document.querySelectorAll('#media-actions .action'));
  for (let action of actions) {
    action.classList.remove('prepared');
    delete action.dataset['uuid'];
  }
};

/**
 * If the arguments provide a valid media item it is added to the player.
 *
 * @param {!MediaItem} item The media item.
 * @return {string} The uuid which has been created for the item before adding.
 */
const addQueueItem = function(item) {
  if (!(item.media && item.media.uri && item.mimeType)) {
    throw Error('insufficient arguments to add a queue item');
  }
  item.uuid = 'uuid-' + uuidCounter++;
  player.addQueueItems(queue.length, [item], /* playbackOrder= */ undefined);
  return item.uuid;
};

/**
 * An event listener which listens for actions.
 *
 * @param {!Event} ev The DOM event.
 */
const handleAction = (ev) => {
  let target = ev.target;
  while (target !== document.body && !target.dataset['action']) {
    target = target.parentNode;
  }
  if (!target || !target.dataset['action']) {
    return;
  }
  switch (target.dataset['action']) {
    case 'player.addItems':
      if (target.dataset['uuid']) {
        player.removeQueueItems([target.dataset['uuid']]);
        delete target.dataset['uuid'];
      } else {
        const uuid = addQueueItem(/** @type {!MediaItem} */
                                  (JSON.parse(target.dataset['item'])));
        target.dataset['uuid'] = uuid;
      }
      break;
  }
};

/**
 * Appends samples to the list of media item actions.
 *
 * @param {!Array<!MediaItem>} mediaItems The samples to add.
 */
const appendSamples = function(mediaItems) {
  const samplesList = document.getElementById('media-actions');
  mediaItems.forEach((item) => {
    const div = /** @type {!HTMLElement} */ (document.createElement('div'));
    div.classList.add('action', 'button');
    div.dataset['action'] = 'player.addItems';
    div.dataset['item'] = JSON.stringify(item);
    div.appendChild(document.createTextNode(item.title));
    const marker = document.createElement('span');
    marker.classList.add('queue-marker');
    div.appendChild(marker);
    samplesList.appendChild(div);
  });
};

/** @type {!HTMLMediaElement} */
const mediaElement =
    /** @type {!HTMLMediaElement} */ (document.getElementById('video'));
// Workaround for https://github.com/google/shaka-player/issues/1819
// TODO(bachinger) Remove line when better fix available.
new SimpleTextDisplayer(mediaElement);
/** @type {!ShakaPlayer} */
const shakaPlayer = new ShakaPlayer(mediaElement);
/** @type {!Player} */
const player = new Player(shakaPlayer, new ConfigurationFactory());
new PlayerControls(player, 'exo_controls');
new PlaybackInfoView(player, 'exo_playback_info');

// register listeners
document.body.addEventListener('click', handleAction);
player.addPlayerListener(playerListener);

// expose the player for debugging purposes.
window['player'] = player;

exports.appendSamples = appendSamples;
