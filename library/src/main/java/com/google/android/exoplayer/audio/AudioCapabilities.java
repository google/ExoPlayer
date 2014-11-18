/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.audio;

import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.media.AudioFormat;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents the set of audio formats a device is capable of playing back.
 */
@TargetApi(21)
public final class AudioCapabilities {

  private final Set<Integer> supportedEncodings;
  private final int maxChannelCount;

  /**
   * Constructs new audio capabilities based on a set of supported encodings and a maximum channel
   * count.
   *
   * @param supportedEncodings Supported audio encodings from {@link android.media.AudioFormat}'s
   *     {@code ENCODING_*} constants.
   * @param maxChannelCount The maximum number of audio channels that can be played simultaneously.
   */
  public AudioCapabilities(int[] supportedEncodings, int maxChannelCount) {
    this.supportedEncodings = new HashSet<Integer>();
    if (supportedEncodings != null) {
      for (int i : supportedEncodings) {
        this.supportedEncodings.add(i);
      }
    }
    this.maxChannelCount = maxChannelCount;
  }

  /** Returns whether the device supports playback of AC-3. */
  public boolean supportsAc3() {
    return Util.SDK_INT >= 21 && supportedEncodings.contains(AudioFormat.ENCODING_AC3);
  }

  /** Returns whether the device supports playback of enhanced AC-3. */
  public boolean supportsEAc3() {
    return Util.SDK_INT >= 21 && supportedEncodings.contains(AudioFormat.ENCODING_E_AC3);
  }

  /** Returns whether the device supports playback of 16-bit PCM. */
  public boolean supportsPcm() {
    return supportedEncodings.contains(AudioFormat.ENCODING_PCM_16BIT);
  }

  /** Returns the maximum number of channels the device can play at the same time. */
  public int getMaxChannelCount() {
    return maxChannelCount;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AudioCapabilities)) {
      return false;
    }
    AudioCapabilities audioCapabilities = (AudioCapabilities) other;
    return supportedEncodings.equals(audioCapabilities.supportedEncodings)
        && maxChannelCount == audioCapabilities.maxChannelCount;
  }

  @Override
  public int hashCode() {
    return maxChannelCount + 31 * supportedEncodings.hashCode();
  }

  @Override
  public String toString() {
    return "AudioCapabilities[maxChannelCount=" + maxChannelCount
        + ", supportedEncodings=" + supportedEncodings + "]";
  }

}
