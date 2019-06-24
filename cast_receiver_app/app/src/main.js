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

goog.module('exoplayer.cast.app');

const ConfigurationFactory = goog.require('exoplayer.cast.ConfigurationFactory');
const MessageDispatcher = goog.require('exoplayer.cast.MessageDispatcher');
const PlaybackInfoView = goog.require('exoplayer.cast.PlaybackInfoView');
const Player = goog.require('exoplayer.cast.Player');
const Receiver = goog.require('exoplayer.cast.Receiver');
const ShakaPlayer = goog.require('shaka.Player');
const SimpleTextDisplayer = goog.require('shaka.text.SimpleTextDisplayer');
const installAll = goog.require('shaka.polyfill.installAll');

/**
 * The ExoPlayer namespace for messages sent and received via cast message bus.
 */
const MESSAGE_NAMESPACE_EXOPLAYER = 'urn:x-cast:com.google.exoplayer.cast';

// installs all polyfills for the Shaka player
installAll();
/** @type {?HTMLMediaElement} */
const videoElement =
    /** @type {?HTMLMediaElement} */ (document.getElementById('exo_video'));
if (videoElement !== null) {
  // Workaround for https://github.com/google/shaka-player/issues/1819
  // TODO(bachinger) Remove line when better fix available.
  new SimpleTextDisplayer(videoElement);
  /** @type {!cast.framework.CastReceiverContext} */
  const castReceiverContext = cast.framework.CastReceiverContext.getInstance();
  const shakaPlayer = new ShakaPlayer(/** @type {!HTMLMediaElement} */
                                      (videoElement));
  const player = new Player(shakaPlayer, new ConfigurationFactory());
  new PlaybackInfoView(player, 'exo_playback_info');
  if (castReceiverContext !== null) {
    const messageDispatcher =
        new MessageDispatcher(MESSAGE_NAMESPACE_EXOPLAYER, castReceiverContext);
    new Receiver(player, castReceiverContext, messageDispatcher);
  }
  // expose player for debugging purposes.
  window['player'] = player;
}
