package com.google.android.exoplayer2.ext.mediasession;
/*
 * Copyright (c) 2017 The Android Open Source Project
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

import android.content.Context;
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.util.RepeatModeUtil;

/**
 * Provides a custom action for toggling repeat actions.
 */
public final class RepeatModeActionProvider implements MediaSessionConnector.CustomActionProvider {

  private static final String ACTION_REPEAT_MODE = "ACTION_EXO_REPEAT_MODE";

  private final @RepeatModeUtil.RepeatToggleModes int repeatToggleModes;
  private final CharSequence repeatAllDescription;
  private final CharSequence repeatOneDescription;
  private final CharSequence repeatOffDescription;

  /**
   * Creates a new {@link RepeatModeActionProvider}.
   * <p>
   * This is equivalent to calling the two argument constructor with
   * {@code RepeatModeUtil#REPEAT_TOGGLE_MODE_ONE | RepeatModeUtil#REPEAT_TOGGLE_MODE_ALL}.
   *
   * @param context The context.
   */
  public RepeatModeActionProvider(Context context) {
    this(context, RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE | RepeatModeUtil.REPEAT_TOGGLE_MODE_ALL);
  }

  /**
   * Creates a new {@link RepeatModeActionProvider} for the given repeat toggle modes.
   *
   * @param context The context.
   * @param repeatToggleModes The toggle modes to enable.
   */
  public RepeatModeActionProvider(Context context,
      @RepeatModeUtil.RepeatToggleModes int repeatToggleModes) {
    this.repeatToggleModes = repeatToggleModes;
    repeatAllDescription = context.getString(R.string.exo_media_action_repeat_all_description);
    repeatOneDescription = context.getString(R.string.exo_media_action_repeat_one_description);
    repeatOffDescription = context.getString(R.string.exo_media_action_repeat_off_description);
  }

  @Override
  public void onCustomAction(SimpleExoPlayer player, String action, Bundle extras) {
    int mode = player.getRepeatMode();
    int proposedMode = RepeatModeUtil.getNextRepeatMode(mode, repeatToggleModes);
    if (mode != proposedMode) {
      player.setRepeatMode(proposedMode);
    }
  }

  @Override
  public PlaybackStateCompat.CustomAction getCustomAction(SimpleExoPlayer player) {
    CharSequence actionLabel;
    int iconResourceId;
    switch (player.getRepeatMode()) {
      case ExoPlayer.REPEAT_MODE_ONE:
        actionLabel = repeatOneDescription;
        iconResourceId = R.drawable.exo_media_action_repeat_one;
        break;
      case ExoPlayer.REPEAT_MODE_ALL:
        actionLabel = repeatAllDescription;
        iconResourceId = R.drawable.exo_media_action_repeat_all;
        break;
      case ExoPlayer.REPEAT_MODE_OFF:
      default:
        actionLabel = repeatOffDescription;
        iconResourceId = R.drawable.exo_media_action_repeat_off;
        break;
    }
    PlaybackStateCompat.CustomAction.Builder repeatBuilder = new PlaybackStateCompat.CustomAction
        .Builder(ACTION_REPEAT_MODE, actionLabel, iconResourceId);
    return repeatBuilder.build();
  }

}
