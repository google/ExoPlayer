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
package com.google.android.exoplayer2.audio;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.provider.Settings.Global;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import java.util.Arrays;

/** Represents the set of audio formats that a device is capable of playing. */
public final class AudioCapabilities {

  private static final int DEFAULT_MAX_CHANNEL_COUNT = 8;
  private static final int DEFAULT_SAMPLE_RATE_HZ = 48_000;

  /** The minimum audio capabilities supported by all devices. */
  public static final AudioCapabilities DEFAULT_AUDIO_CAPABILITIES =
      new AudioCapabilities(new int[] {AudioFormat.ENCODING_PCM_16BIT}, DEFAULT_MAX_CHANNEL_COUNT);

  /** Audio capabilities when the device specifies external surround sound. */
  @SuppressWarnings("InlinedApi")
  private static final AudioCapabilities EXTERNAL_SURROUND_SOUND_CAPABILITIES =
      new AudioCapabilities(
          new int[] {
            AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_E_AC3
          },
          DEFAULT_MAX_CHANNEL_COUNT);

  /** Array of all surround sound encodings that a device may be capable of playing. */
  @SuppressWarnings("InlinedApi")
  private static final int[] ALL_SURROUND_ENCODINGS =
      new int[] {
        AudioFormat.ENCODING_AC3,
        AudioFormat.ENCODING_E_AC3,
        AudioFormat.ENCODING_E_AC3_JOC,
        AudioFormat.ENCODING_AC4,
        AudioFormat.ENCODING_DOLBY_TRUEHD,
        AudioFormat.ENCODING_DTS,
        AudioFormat.ENCODING_DTS_HD,
      };

  /** Global settings key for devices that can specify external surround sound. */
  private static final String EXTERNAL_SURROUND_SOUND_KEY = "external_surround_sound_enabled";

  /**
   * Returns the current audio capabilities for the device.
   *
   * @param context A context for obtaining the current audio capabilities.
   * @return The current audio capabilities for the device.
   */
  @SuppressWarnings("InlinedApi")
  public static AudioCapabilities getCapabilities(Context context) {
    Intent intent =
        context.registerReceiver(
            /* receiver= */ null, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
    return getCapabilities(context, intent);
  }

  @SuppressLint("InlinedApi")
  /* package */ static AudioCapabilities getCapabilities(Context context, @Nullable Intent intent) {
    if (deviceMaySetExternalSurroundSoundGlobalSetting()
        && Global.getInt(context.getContentResolver(), EXTERNAL_SURROUND_SOUND_KEY, 0) == 1) {
      return EXTERNAL_SURROUND_SOUND_CAPABILITIES;
    }
    // AudioTrack.isDirectPlaybackSupported returns true for encodings that are supported for audio
    // offload, as well as for encodings we want to list for passthrough mode. Therefore we only use
    // it on TV and automotive devices, which generally shouldn't support audio offload for surround
    // encodings.
    if (Util.SDK_INT >= 29 && (Util.isTv(context) || Util.isAutomotive(context))) {
      return new AudioCapabilities(
          Api29.getDirectPlaybackSupportedEncodings(), DEFAULT_MAX_CHANNEL_COUNT);
    }
    if (intent == null || intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 0) {
      return DEFAULT_AUDIO_CAPABILITIES;
    }
    return new AudioCapabilities(
        intent.getIntArrayExtra(AudioManager.EXTRA_ENCODINGS),
        intent.getIntExtra(
            AudioManager.EXTRA_MAX_CHANNEL_COUNT, /* defaultValue= */ DEFAULT_MAX_CHANNEL_COUNT));
  }

  /**
   * Returns the global settings {@link Uri} used by the device to specify external surround sound,
   * or null if the device does not support this functionality.
   */
  @Nullable
  /* package */ static Uri getExternalSurroundSoundGlobalSettingUri() {
    return deviceMaySetExternalSurroundSoundGlobalSetting()
        ? Global.getUriFor(EXTERNAL_SURROUND_SOUND_KEY)
        : null;
  }

  private final int[] supportedEncodings;
  private final int maxChannelCount;

  /**
   * Constructs new audio capabilities based on a set of supported encodings and a maximum channel
   * count.
   *
   * <p>Applications should generally call {@link #getCapabilities(Context)} to obtain an instance
   * based on the capabilities advertised by the platform, rather than calling this constructor.
   *
   * @param supportedEncodings Supported audio encodings from {@link android.media.AudioFormat}'s
   *     {@code ENCODING_*} constants. Passing {@code null} indicates that no encodings are
   *     supported.
   * @param maxChannelCount The maximum number of audio channels that can be played simultaneously.
   */
  public AudioCapabilities(@Nullable int[] supportedEncodings, int maxChannelCount) {
    if (supportedEncodings != null) {
      this.supportedEncodings = Arrays.copyOf(supportedEncodings, supportedEncodings.length);
      Arrays.sort(this.supportedEncodings);
    } else {
      this.supportedEncodings = new int[0];
    }
    this.maxChannelCount = maxChannelCount;
  }

  /**
   * Returns whether this device supports playback of the specified audio {@code encoding}.
   *
   * @param encoding One of {@link C.Encoding}'s {@code ENCODING_*} constants.
   * @return Whether this device supports playback the specified audio {@code encoding}.
   */
  public boolean supportsEncoding(@C.Encoding int encoding) {
    return Arrays.binarySearch(supportedEncodings, encoding) >= 0;
  }

  /** Returns the maximum number of channels the device can play at the same time. */
  public int getMaxChannelCount() {
    return maxChannelCount;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AudioCapabilities)) {
      return false;
    }
    AudioCapabilities audioCapabilities = (AudioCapabilities) other;
    return Arrays.equals(supportedEncodings, audioCapabilities.supportedEncodings)
        && maxChannelCount == audioCapabilities.maxChannelCount;
  }

  @Override
  public int hashCode() {
    return maxChannelCount + 31 * Arrays.hashCode(supportedEncodings);
  }

  @Override
  public String toString() {
    return "AudioCapabilities[maxChannelCount="
        + maxChannelCount
        + ", supportedEncodings="
        + Arrays.toString(supportedEncodings)
        + "]";
  }

  private static boolean deviceMaySetExternalSurroundSoundGlobalSetting() {
    return Util.SDK_INT >= 17
        && ("Amazon".equals(Util.MANUFACTURER) || "Xiaomi".equals(Util.MANUFACTURER));
  }

  @RequiresApi(29)
  private static final class Api29 {
    @DoNotInline
    public static int[] getDirectPlaybackSupportedEncodings() {
      ImmutableList.Builder<Integer> supportedEncodingsListBuilder = ImmutableList.builder();
      for (int encoding : ALL_SURROUND_ENCODINGS) {
        if (AudioTrack.isDirectPlaybackSupported(
            new AudioFormat.Builder()
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(encoding)
                .setSampleRate(DEFAULT_SAMPLE_RATE_HZ)
                .build(),
            new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                .setFlags(0)
                .build())) {
          supportedEncodingsListBuilder.add(encoding);
        }
      }
      supportedEncodingsListBuilder.add(AudioFormat.ENCODING_PCM_16BIT);
      return Ints.toArray(supportedEncodingsListBuilder.build());
    }
  }
}
