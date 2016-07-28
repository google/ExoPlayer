/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.ui;

import android.content.Context;
import android.view.KeyEvent;
import android.widget.MediaController;

/**
 * An extension of {@link MediaController} with enhanced support for D-pad and media keys.
 */
public class KeyCompatibleMediaController extends MediaController {

  private MediaController.MediaPlayerControl playerControl;

  public KeyCompatibleMediaController(Context context) {
    super(context);
  }

  @Override
  public void setMediaPlayer(MediaController.MediaPlayerControl playerControl) {
    super.setMediaPlayer(playerControl);
    this.playerControl = playerControl;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    int keyCode = event.getKeyCode();
    if (playerControl.canSeekForward() && (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        playerControl.seekTo(playerControl.getCurrentPosition() + 15000); // milliseconds
        show();
      }
      return true;
    } else if (playerControl.canSeekBackward() && (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
        || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        playerControl.seekTo(playerControl.getCurrentPosition() - 5000); // milliseconds
        show();
      }
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

}
