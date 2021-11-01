/*
 * Copyright 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.media2;

import android.annotation.SuppressLint;
import androidx.media.AudioAttributesCompat;
import androidx.media2.common.SessionPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.audio.AudioAttributes;

/** Utility methods for translating between the media2 and ExoPlayer APIs. */
/* package */ final class Utils {

  /** Returns ExoPlayer audio attributes for the given audio attributes. */
  @SuppressLint("WrongConstant") // AudioAttributesCompat.AttributeUsage is equal to C.AudioUsage
  public static AudioAttributes getAudioAttributes(AudioAttributesCompat audioAttributesCompat) {
    return new AudioAttributes.Builder()
        .setContentType(audioAttributesCompat.getContentType())
        .setFlags(audioAttributesCompat.getFlags())
        .setUsage(audioAttributesCompat.getUsage())
        .build();
  }

  /** Returns audio attributes for the given ExoPlayer audio attributes. */
  public static AudioAttributesCompat getAudioAttributesCompat(AudioAttributes audioAttributes) {
    return new AudioAttributesCompat.Builder()
        .setContentType(audioAttributes.contentType)
        .setFlags(audioAttributes.flags)
        .setUsage(audioAttributes.usage)
        .build();
  }

  /** Returns the SimpleExoPlayer's shuffle mode for the given shuffle mode. */
  public static boolean getExoPlayerShuffleMode(int shuffleMode) {
    switch (shuffleMode) {
      case SessionPlayer.SHUFFLE_MODE_ALL:
      case SessionPlayer.SHUFFLE_MODE_GROUP:
        return true;
      case SessionPlayer.SHUFFLE_MODE_NONE:
        return false;
      default:
        throw new IllegalArgumentException();
    }
  }

  /** Returns the shuffle mode for the given ExoPlayer's shuffle mode */
  public static int getShuffleMode(boolean exoPlayerShuffleMode) {
    return exoPlayerShuffleMode ? SessionPlayer.SHUFFLE_MODE_ALL : SessionPlayer.SHUFFLE_MODE_NONE;
  }

  /** Returns the ExoPlayer's repeat mode for the given repeat mode. */
  @Player.RepeatMode
  public static int getExoPlayerRepeatMode(int repeatMode) {
    switch (repeatMode) {
      case SessionPlayer.REPEAT_MODE_ALL:
      case SessionPlayer.REPEAT_MODE_GROUP:
        return Player.REPEAT_MODE_ALL;
      case SessionPlayer.REPEAT_MODE_ONE:
        return Player.REPEAT_MODE_ONE;
      case SessionPlayer.REPEAT_MODE_NONE:
        return Player.REPEAT_MODE_OFF;
      default:
        throw new IllegalArgumentException();
    }
  }

  /** Returns the repeat mode for the given SimpleExoPlayer's repeat mode. */
  public static int getRepeatMode(@Player.RepeatMode int exoPlayerRepeatMode) {
    switch (exoPlayerRepeatMode) {
      case Player.REPEAT_MODE_ALL:
        return SessionPlayer.REPEAT_MODE_ALL;
      case Player.REPEAT_MODE_ONE:
        return SessionPlayer.REPEAT_MODE_ONE;
      case Player.REPEAT_MODE_OFF:
        return SessionPlayer.REPEAT_MODE_NONE;
      default:
        throw new IllegalArgumentException();
    }
  }

  private Utils() {
    // Prevent instantiation.
  }
}
