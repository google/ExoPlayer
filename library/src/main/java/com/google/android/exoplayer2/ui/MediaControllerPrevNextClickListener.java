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

import android.view.View;
import android.view.View.OnClickListener;
import com.google.android.exoplayer2.ExoPlayer;

/**
 * An {@link OnClickListener} that can be passed to
 * {@link android.widget.MediaController#setPrevNextListeners(OnClickListener, OnClickListener)} to
 * make the controller's "previous" and "next" buttons visible and seek to the previous and next
 * periods in the timeline of the media being played.
 */
public class MediaControllerPrevNextClickListener implements OnClickListener {

  /**
   * If a previous button is clicked the player is seeked to the start of the previous period if the
   * playback position in the current period is less than or equal to this constant (and if a
   * previous period exists). Else the player is seeked to the start of the current period.
   */
  private static final long MAX_POSITION_FOR_SEEK_TO_PREVIOUS_PERIOD = 3000;

  private final ExoPlayer player;
  private final boolean isNext;

  /**
   * @param player The player to operate on.
   * @param isNext True if this instance if for the "next" button. False for "previous".
   */
  public MediaControllerPrevNextClickListener(ExoPlayer player, boolean isNext) {
    this.player = player;
    this.isNext = isNext;
  }

  @Override
  public void onClick(View v) {
    int currentPeriodIndex = player.getCurrentPeriodIndex();
    if (isNext) {
      if (currentPeriodIndex < player.getCurrentTimeline().getPeriodCount() - 1) {
        player.seekTo(currentPeriodIndex + 1, 0);
      }
    } else {
      if (currentPeriodIndex > 0
          && player.getCurrentPosition() <= MAX_POSITION_FOR_SEEK_TO_PREVIOUS_PERIOD) {
        player.seekTo(currentPeriodIndex - 1, 0);
      } else {
        player.seekTo(currentPeriodIndex, 0);
      }
    }
  }

}
