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
goog.module('exoplayer.cast.PlayerControls');

const Player = goog.require('exoplayer.cast.Player');

/**
 * A simple UI to control the player.
 *
 */
class PlayerControls {
  /**
   * @param {!Player} player The player.
   * @param {string} containerId The id of the container element.
   */
  constructor(player, containerId) {
    /** @const @private {!Player} */
    this.player_ = player;
    /** @const @private {?Element} */
    this.root_ = document.getElementById(containerId);
    /** @const @private {?Element} */
    this.playButton_ = this.root_.querySelector('#button_play');
    /** @const @private {?Element} */
    this.pauseButton_ = this.root_.querySelector('#button_pause');
    /** @const @private {?Element} */
    this.previousButton_ = this.root_.querySelector('#button_previous');
    /** @const @private {?Element} */
    this.nextButton_ = this.root_.querySelector('#button_next');

    const previous = () => {
      const index = player.getPreviousWindowIndex();
      if (index !== -1) {
        player.seekToWindow(index, 0);
      }
    };
    const next = () => {
      const index = player.getNextWindowIndex();
      if (index !== -1) {
        player.seekToWindow(index, 0);
      }
    };
    const rewind = () => {
      player.seekToWindow(
          player.getCurrentWindowIndex(),
          player.getCurrentPositionMs() - 15000);
    };
    const fastForward = () => {
      player.seekToWindow(
          player.getCurrentWindowIndex(),
          player.getCurrentPositionMs() + 30000);
    };
    const actions = {
      'pwr_1': (ev) => player.setPlayWhenReady(true),
      'pwr_0': (ev) => player.setPlayWhenReady(false),
      'rewind': rewind,
      'fastforward': fastForward,
      'previous': previous,
      'next': next,
      'prepare': (ev) => player.prepare(),
      'stop': (ev) => player.stop(true),
      'remove_queue_item': (ev) => {
        player.removeQueueItems([ev.target.dataset.id]);
      },
    };
    /**
     * @param {!Event} ev The key event.
     * @return {boolean} true if the key event has been handled.
     */
    const keyListener = (ev) => {
      const key = /** @type {!KeyboardEvent} */ (ev).key;
      switch (key) {
        case 'ArrowUp':
        case 'k':
          previous();
          ev.preventDefault();
          return true;
        case 'ArrowDown':
        case 'j':
          next();
          ev.preventDefault();
          return true;
        case 'ArrowLeft':
        case 'h':
          rewind();
          ev.preventDefault();
          return true;
        case 'ArrowRight':
        case 'l':
          fastForward();
          ev.preventDefault();
          return true;
        case ' ':
        case 'p':
          player.setPlayWhenReady(!player.getPlayWhenReady());
          ev.preventDefault();
          return true;
      }
      return false;
    };
    document.addEventListener('keydown', keyListener);
    this.root_.addEventListener('click', function(ev) {
      const method = ev.target['dataset']['method'];
      if (actions[method]) {
        actions[method](ev);
      }
      return true;
    });
    player.addPlayerListener((playerState) => this.updateUi(playerState));
    player.invalidate();
    this.setVisible_(true);
  }

  /**
   * Syncs the ui with the player state.
   *
   * @param {!PlayerState} playerState The state of the player to be reflected
   *     by the UI.
   */
  updateUi(playerState) {
    if (playerState.playWhenReady) {
      this.playButton_.style.display = 'none';
      this.pauseButton_.style.display = 'inline-block';
    } else {
      this.playButton_.style.display = 'inline-block';
      this.pauseButton_.style.display = 'none';
    }
    if (this.player_.getNextWindowIndex() === -1) {
      this.nextButton_.style.visibility = 'hidden';
    } else {
      this.nextButton_.style.visibility = 'visible';
    }
    if (this.player_.getPreviousWindowIndex() === -1) {
      this.previousButton_.style.visibility = 'hidden';
    } else {
      this.previousButton_.style.visibility = 'visible';
    }
  }

  /**
   * @private
   * @param {boolean} visible If `true` thie controls are shown. If `false` the
   *     controls are hidden.
   */
  setVisible_(visible) {
    if (this.root_) {
      this.root_.style.display = visible ? 'block' : 'none';
    }
  }
}

exports = PlayerControls;
