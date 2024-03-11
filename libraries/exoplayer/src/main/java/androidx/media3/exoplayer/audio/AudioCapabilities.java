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
package androidx.media3.exoplayer.audio;

import static android.media.AudioFormat.CHANNEL_OUT_STEREO;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.lang.Math.max;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.provider.Settings.Global;
import android.util.Pair;
import android.util.SparseArray;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Represents the set of audio formats that a device is capable of playing. */
@UnstableApi
public final class AudioCapabilities {

  // TODO(internal b/283945513): Have separate default max channel counts in `AudioCapabilities`
  // for PCM and compressed audio.
  @VisibleForTesting /* package */ static final int DEFAULT_MAX_CHANNEL_COUNT = 10;
  @VisibleForTesting /* package */ static final int DEFAULT_SAMPLE_RATE_HZ = 48_000;

  /** The minimum audio capabilities supported by all devices. */
  public static final AudioCapabilities DEFAULT_AUDIO_CAPABILITIES =
      new AudioCapabilities(ImmutableList.of(AudioProfile.DEFAULT_AUDIO_PROFILE));

  /** Encodings supported when the device specifies external surround sound. */
  @SuppressLint("InlinedApi") // Compile-time access to integer constants defined in API 21.
  private static final ImmutableList<Integer> EXTERNAL_SURROUND_SOUND_ENCODINGS =
      ImmutableList.of(
          AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_E_AC3);

  /**
   * All surround sound encodings that a device may be capable of playing mapped to a maximum
   * channel count.
   */
  @VisibleForTesting /* package */
  static final ImmutableMap<Integer, Integer> ALL_SURROUND_ENCODINGS_AND_MAX_CHANNELS =
      new ImmutableMap.Builder<Integer, Integer>()
          .put(C.ENCODING_AC3, 6)
          .put(C.ENCODING_AC4, 6)
          .put(C.ENCODING_DTS, 6)
          .put(C.ENCODING_DTS_UHD_P2, 10)
          .put(C.ENCODING_E_AC3_JOC, 6)
          .put(C.ENCODING_E_AC3, 8)
          .put(C.ENCODING_DTS_HD, 8)
          .put(C.ENCODING_DOLBY_TRUEHD, 8)
          .buildOrThrow();

  /** Global settings key for devices that can specify external surround sound. */
  private static final String EXTERNAL_SURROUND_SOUND_KEY = "external_surround_sound_enabled";

  /**
   * Global setting key for devices that want to force the usage of {@link
   * #EXTERNAL_SURROUND_SOUND_KEY} over other signals like HDMI.
   */
  private static final String FORCE_EXTERNAL_SURROUND_SOUND_KEY =
      "use_external_surround_sound_flag";

  /**
   * @deprecated Use {@link #getCapabilities(Context, AudioAttributes, AudioDeviceInfo)} instead.
   */
  @Deprecated
  public static AudioCapabilities getCapabilities(Context context) {
    return getCapabilities(context, AudioAttributes.DEFAULT, /* routedDevice= */ null);
  }

  /**
   * Returns the current audio capabilities.
   *
   * @param context A context for obtaining the current audio capabilities.
   * @param audioAttributes The {@link AudioAttributes} to obtain capabilities for.
   * @param routedDevice The {@link AudioDeviceInfo} audio will be routed to if known, or null to
   *     assume the default route.
   * @return The current audio capabilities for the device.
   */
  public static AudioCapabilities getCapabilities(
      Context context, AudioAttributes audioAttributes, @Nullable AudioDeviceInfo routedDevice) {
    @Nullable
    AudioDeviceInfoApi23 routedDeviceApi23 =
        Util.SDK_INT >= 23 && routedDevice != null ? new AudioDeviceInfoApi23(routedDevice) : null;
    return getCapabilitiesInternal(context, audioAttributes, routedDeviceApi23);
  }

  @SuppressWarnings("InlinedApi")
  @SuppressLint("UnprotectedReceiver") // ACTION_HDMI_AUDIO_PLUG is protected since API 16
  /* package */ static AudioCapabilities getCapabilitiesInternal(
      Context context,
      AudioAttributes audioAttributes,
      @Nullable AudioDeviceInfoApi23 routedDevice) {
    Intent intent =
        context.registerReceiver(
            /* receiver= */ null, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
    return getCapabilitiesInternal(context, intent, audioAttributes, routedDevice);
  }

  @SuppressLint("InlinedApi")
  /* package */ static AudioCapabilities getCapabilitiesInternal(
      Context context,
      @Nullable Intent intent,
      AudioAttributes audioAttributes,
      @Nullable AudioDeviceInfoApi23 routedDevice) {
    AudioManager audioManager =
        (AudioManager) checkNotNull(context.getSystemService(Context.AUDIO_SERVICE));
    AudioDeviceInfoApi23 currentDevice =
        routedDevice != null
            ? routedDevice
            : Util.SDK_INT >= 33
                ? Api33.getDefaultRoutedDeviceForAttributes(audioManager, audioAttributes)
                : null;

    if (Util.SDK_INT >= 33 && (Util.isTv(context) || Util.isAutomotive(context))) {
      // TV or automotive devices generally shouldn't support audio offload for surround encodings,
      // so the encodings we get from AudioManager.getDirectProfilesForAttributes should include
      // the PCM encodings and surround encodings for passthrough mode.
      return Api33.getCapabilitiesInternalForDirectPlayback(audioManager, audioAttributes);
    }

    // If a connection to Bluetooth device is detected, we only return the minimum capabilities that
    // is supported by all the devices.
    if (Util.SDK_INT >= 23 && Api23.isBluetoothConnected(audioManager, currentDevice)) {
      return DEFAULT_AUDIO_CAPABILITIES;
    }

    ImmutableSet.Builder<Integer> supportedEncodings = new ImmutableSet.Builder<>();
    supportedEncodings.add(C.ENCODING_PCM_16BIT);

    // AudioTrack.isDirectPlaybackSupported returns true for encodings that are supported for audio
    // offload, as well as for encodings we want to list for passthrough mode. Therefore we only use
    // it on TV and automotive devices, which generally shouldn't support audio offload for surround
    // encodings.
    if (Util.SDK_INT >= 29 && (Util.isTv(context) || Util.isAutomotive(context))) {
      supportedEncodings.addAll(Api29.getDirectPlaybackSupportedEncodings(audioAttributes));
      return new AudioCapabilities(
          getAudioProfiles(Ints.toArray(supportedEncodings.build()), DEFAULT_MAX_CHANNEL_COUNT));
    }

    ContentResolver contentResolver = context.getContentResolver();
    boolean forceExternalSurroundSoundSetting =
        Global.getInt(contentResolver, FORCE_EXTERNAL_SURROUND_SOUND_KEY, 0) == 1;
    if ((forceExternalSurroundSoundSetting || deviceMaySetExternalSurroundSoundGlobalSetting())
        && Global.getInt(contentResolver, EXTERNAL_SURROUND_SOUND_KEY, 0) == 1) {
      supportedEncodings.addAll(EXTERNAL_SURROUND_SOUND_ENCODINGS);
    }

    if (intent != null
        && !forceExternalSurroundSoundSetting
        && intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1) {
      @Nullable int[] encodingsFromExtra = intent.getIntArrayExtra(AudioManager.EXTRA_ENCODINGS);
      if (encodingsFromExtra != null) {
        supportedEncodings.addAll(Ints.asList(encodingsFromExtra));
      }
      return new AudioCapabilities(
          getAudioProfiles(
              Ints.toArray(supportedEncodings.build()),
              intent.getIntExtra(
                  AudioManager.EXTRA_MAX_CHANNEL_COUNT,
                  /* defaultValue= */ DEFAULT_MAX_CHANNEL_COUNT)));
    }

    return new AudioCapabilities(
        getAudioProfiles(
            Ints.toArray(supportedEncodings.build()),
            /* maxChannelCount= */ DEFAULT_MAX_CHANNEL_COUNT));
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

  private final SparseArray<AudioProfile> encodingToAudioProfile;
  private final int maxChannelCount;

  /**
   * @deprecated Use {@link #getCapabilities(Context, AudioAttributes, AudioDeviceInfo)} instead.
   */
  @Deprecated
  public AudioCapabilities(@Nullable int[] supportedEncodings, int maxChannelCount) {
    this(getAudioProfiles(supportedEncodings, maxChannelCount));
  }

  private AudioCapabilities(List<AudioProfile> audioProfiles) {
    encodingToAudioProfile = new SparseArray<>();
    for (int i = 0; i < audioProfiles.size(); i++) {
      AudioProfile audioProfile = audioProfiles.get(i);
      encodingToAudioProfile.put(audioProfile.encoding, audioProfile);
    }
    int maxChannelCount = 0;
    for (int i = 0; i < encodingToAudioProfile.size(); i++) {
      maxChannelCount = max(maxChannelCount, encodingToAudioProfile.valueAt(i).maxChannelCount);
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
    return Util.contains(encodingToAudioProfile, encoding);
  }

  /** Returns the maximum number of channels the device can play at the same time. */
  public int getMaxChannelCount() {
    return maxChannelCount;
  }

  /**
   * @deprecated Use {@link #isPassthroughPlaybackSupported(Format, AudioAttributes)} instead.
   */
  @Deprecated
  public boolean isPassthroughPlaybackSupported(Format format) {
    return isPassthroughPlaybackSupported(format, AudioAttributes.DEFAULT);
  }

  /** Returns whether the device can do passthrough playback for {@code format}. */
  public boolean isPassthroughPlaybackSupported(Format format, AudioAttributes audioAttributes) {
    return getEncodingAndChannelConfigForPassthrough(format, audioAttributes) != null;
  }

  /**
   * @deprecated Use {@link #getEncodingAndChannelConfigForPassthrough(Format, AudioAttributes)}
   *     instead.
   */
  @Deprecated
  @Nullable
  public Pair<Integer, Integer> getEncodingAndChannelConfigForPassthrough(Format format) {
    return getEncodingAndChannelConfigForPassthrough(format, AudioAttributes.DEFAULT);
  }

  /**
   * Returns the encoding and channel config to use when configuring an {@link AudioTrack} in
   * passthrough mode for the specified {@link Format} and {@link AudioAttributes}. Returns {@code
   * null} if passthrough of the format is unsupported.
   *
   * @param format The {@link Format}.
   * @param audioAttributes The {@link AudioAttributes}.
   * @return The encoding and channel config to use, or {@code null} if passthrough of the format is
   *     unsupported.
   */
  @Nullable
  public Pair<Integer, Integer> getEncodingAndChannelConfigForPassthrough(
      Format format, AudioAttributes audioAttributes) {
    @C.Encoding
    int encoding = MimeTypes.getEncoding(checkNotNull(format.sampleMimeType), format.codecs);
    // Check that this is an encoding known to work for passthrough. This avoids trying to use
    // passthrough with an encoding where the device/app reports it's capable but it is untested or
    // known to be broken (for example AAC-LC).
    if (!ALL_SURROUND_ENCODINGS_AND_MAX_CHANNELS.containsKey(encoding)) {
      return null;
    }

    if (encoding == C.ENCODING_E_AC3_JOC && !supportsEncoding(C.ENCODING_E_AC3_JOC)) {
      // E-AC3 receivers support E-AC3 JOC streams (but decode only the base layer).
      encoding = C.ENCODING_E_AC3;
    } else if ((encoding == C.ENCODING_DTS_HD && !supportsEncoding(C.ENCODING_DTS_HD))
        || (encoding == C.ENCODING_DTS_UHD_P2 && !supportsEncoding(C.ENCODING_DTS_UHD_P2))) {
      // DTS receivers support DTS-HD streams (but decode only the core layer).
      encoding = C.ENCODING_DTS;
    }
    if (!supportsEncoding(encoding)) {
      return null;
    }

    AudioProfile audioProfile = checkNotNull(encodingToAudioProfile.get(encoding));
    int channelCount;
    if (format.channelCount == Format.NO_VALUE || encoding == C.ENCODING_E_AC3_JOC) {
      // In HLS chunkless preparation, the format channel count and sample rate may be unset. See
      // https://github.com/google/ExoPlayer/issues/10204 and b/222127949 for more details.
      // For E-AC3 JOC, the format is object based so the format channel count is arbitrary.
      int sampleRate =
          format.sampleRate != Format.NO_VALUE ? format.sampleRate : DEFAULT_SAMPLE_RATE_HZ;
      channelCount =
          audioProfile.getMaxSupportedChannelCountForPassthrough(sampleRate, audioAttributes);
    } else {
      channelCount = format.channelCount;
      if (format.sampleMimeType.equals(MimeTypes.AUDIO_DTS_X) && Util.SDK_INT < 33) {
        // Some DTS:X TVs reports ACTION_HDMI_AUDIO_PLUG.EXTRA_MAX_CHANNEL_COUNT as 8
        // instead of 10. See https://github.com/androidx/media/issues/396
        if (channelCount > 10) {
          return null;
        }
      } else if (!audioProfile.supportsChannelCount(channelCount)) {
        return null;
      }
    }
    int channelConfig = getChannelConfigForPassthrough(channelCount);
    if (channelConfig == AudioFormat.CHANNEL_INVALID) {
      return null;
    }
    return Pair.create(encoding, channelConfig);
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
    return Util.contentEquals(encodingToAudioProfile, audioCapabilities.encodingToAudioProfile)
        && maxChannelCount == audioCapabilities.maxChannelCount;
  }

  @Override
  public int hashCode() {
    return maxChannelCount + 31 * Util.contentHashCode(encodingToAudioProfile);
  }

  @Override
  public String toString() {
    return "AudioCapabilities[maxChannelCount="
        + maxChannelCount
        + ", audioProfiles="
        + encodingToAudioProfile
        + "]";
  }

  private static boolean deviceMaySetExternalSurroundSoundGlobalSetting() {
    return "Amazon".equals(Util.MANUFACTURER) || "Xiaomi".equals(Util.MANUFACTURER);
  }

  private static int getChannelConfigForPassthrough(int channelCount) {
    if (Util.SDK_INT <= 28) {
      // In passthrough mode the channel count used to configure the audio track doesn't affect how
      // the stream is handled, except that some devices do overly-strict channel configuration
      // checks. Therefore we override the channel count so that a known-working channel
      // configuration is chosen in all cases. See [Internal: b/29116190].
      if (channelCount == 7) {
        channelCount = 8;
      } else if (channelCount == 3 || channelCount == 4 || channelCount == 5) {
        channelCount = 6;
      }
    }

    // Workaround for Nexus Player not reporting support for mono passthrough. See
    // [Internal: b/34268671].
    if (Util.SDK_INT <= 26 && "fugu".equals(Util.DEVICE) && channelCount == 1) {
      channelCount = 2;
    }

    return Util.getAudioTrackChannelConfig(channelCount);
  }

  // Suppression needed for IntDef casting.
  @SuppressLint("WrongConstant")
  @RequiresApi(33)
  private static ImmutableList<AudioProfile> getAudioProfiles(
      List<android.media.AudioProfile> audioProfiles) {
    Map<Integer, Set<Integer>> formatToChannelMasks = new HashMap<>();
    // Enforce the support of stereo 16bit-PCM.
    formatToChannelMasks.put(C.ENCODING_PCM_16BIT, new HashSet<>(Ints.asList(CHANNEL_OUT_STEREO)));
    for (int i = 0; i < audioProfiles.size(); i++) {
      android.media.AudioProfile audioProfile = audioProfiles.get(i);
      if ((audioProfile.getEncapsulationType()
          == android.media.AudioProfile.AUDIO_ENCAPSULATION_TYPE_IEC61937)) {
        // Skip the IEC61937 encapsulation because we don't support it yet.
        continue;
      }
      int encoding = audioProfile.getFormat();
      if (!Util.isEncodingLinearPcm(encoding)
          && !ALL_SURROUND_ENCODINGS_AND_MAX_CHANNELS.containsKey(encoding)) {
        continue;
      }
      if (formatToChannelMasks.containsKey(encoding)) {
        checkNotNull(formatToChannelMasks.get(encoding))
            .addAll(Ints.asList(audioProfile.getChannelMasks()));
      } else {
        formatToChannelMasks.put(
            encoding, new HashSet<>(Ints.asList(audioProfile.getChannelMasks())));
      }
    }

    ImmutableList.Builder<AudioProfile> localAudioProfiles = ImmutableList.builder();
    for (Map.Entry<Integer, Set<Integer>> formatAndChannelMasks : formatToChannelMasks.entrySet()) {
      localAudioProfiles.add(
          new AudioProfile(formatAndChannelMasks.getKey(), formatAndChannelMasks.getValue()));
    }
    return localAudioProfiles.build();
  }

  private static ImmutableList<AudioProfile> getAudioProfiles(
      @Nullable int[] supportedEncodings, int maxChannelCount) {
    ImmutableList.Builder<AudioProfile> audioProfiles = ImmutableList.builder();
    if (supportedEncodings == null) {
      supportedEncodings = new int[0];
    }
    for (int i = 0; i < supportedEncodings.length; i++) {
      int encoding = supportedEncodings[i];
      audioProfiles.add(new AudioProfile(encoding, maxChannelCount));
    }
    return audioProfiles.build();
  }

  private static final class AudioProfile {

    public static final AudioProfile DEFAULT_AUDIO_PROFILE =
        (Util.SDK_INT >= 33)
            ? new AudioProfile(
                C.ENCODING_PCM_16BIT,
                getAllChannelMasksForMaxChannelCount(DEFAULT_MAX_CHANNEL_COUNT))
            : new AudioProfile(C.ENCODING_PCM_16BIT, DEFAULT_MAX_CHANNEL_COUNT);

    public final @C.Encoding int encoding;
    public final int maxChannelCount;
    @Nullable private final ImmutableSet<Integer> channelMasks;

    @RequiresApi(33)
    public AudioProfile(@C.Encoding int encoding, Set<Integer> channelMasks) {
      this.encoding = encoding;
      this.channelMasks = ImmutableSet.copyOf(channelMasks);
      int maxChannelCount = 0;
      for (int channelMask : this.channelMasks) {
        maxChannelCount = max(maxChannelCount, Integer.bitCount(channelMask));
      }
      this.maxChannelCount = maxChannelCount;
    }

    public AudioProfile(@C.Encoding int encoding, int maxChannelCount) {
      this.encoding = encoding;
      this.maxChannelCount = maxChannelCount;
      this.channelMasks = null;
    }

    public boolean supportsChannelCount(int channelCount) {
      if (channelMasks == null) {
        return channelCount <= maxChannelCount;
      }

      int channelMask = Util.getAudioTrackChannelConfig(channelCount);
      if (channelMask == AudioFormat.CHANNEL_INVALID) {
        return false;
      }
      return channelMasks.contains(channelMask);
    }

    public int getMaxSupportedChannelCountForPassthrough(
        int sampleRate, AudioAttributes audioAttributes) {
      if (channelMasks != null) {
        // We built the AudioProfile on API 33.
        return maxChannelCount;
      } else if (Util.SDK_INT >= 29) {
        return Api29.getMaxSupportedChannelCountForPassthrough(
            encoding, sampleRate, audioAttributes);
      }
      return checkNotNull(ALL_SURROUND_ENCODINGS_AND_MAX_CHANNELS.getOrDefault(encoding, 0));
    }

    private static ImmutableSet<Integer> getAllChannelMasksForMaxChannelCount(int maxChannelCount) {
      ImmutableSet.Builder<Integer> allChannelMasks = new ImmutableSet.Builder<>();
      for (int i = 1; i <= maxChannelCount; i++) {
        allChannelMasks.add(Util.getAudioTrackChannelConfig(i));
      }
      return allChannelMasks.build();
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof AudioProfile)) {
        return false;
      }
      AudioProfile audioProfile = (AudioProfile) other;
      return encoding == audioProfile.encoding
          && maxChannelCount == audioProfile.maxChannelCount
          && Util.areEqual(channelMasks, audioProfile.channelMasks);
    }

    @Override
    public int hashCode() {
      int result = encoding;
      result = 31 * result + maxChannelCount;
      result = 31 * result + (channelMasks == null ? 0 : channelMasks.hashCode());
      return result;
    }

    @Override
    public String toString() {
      return "AudioProfile[format="
          + encoding
          + ", maxChannelCount="
          + maxChannelCount
          + ", channelMasks="
          + channelMasks
          + "]";
    }
  }

  @RequiresApi(23)
  private static final class Api23 {
    private Api23() {}

    @DoNotInline
    public static boolean isBluetoothConnected(
        AudioManager audioManager, @Nullable AudioDeviceInfoApi23 currentDevice) {
      // Check the current device if known or all devices otherwise.
      AudioDeviceInfo[] audioDeviceInfos =
          currentDevice == null
              ? checkNotNull(audioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
              : new AudioDeviceInfo[] {currentDevice.audioDeviceInfo};
      ImmutableSet<Integer> allBluetoothDeviceTypesSet = getAllBluetoothDeviceTypes();
      for (AudioDeviceInfo audioDeviceInfo : audioDeviceInfos) {
        if (allBluetoothDeviceTypesSet.contains(audioDeviceInfo.getType())) {
          return true;
        }
      }
      return false;
    }

    /**
     * Returns all the possible bluetooth device types that can be returned by {@link
     * AudioDeviceInfo#getType()}.
     *
     * <p>The types {@link AudioDeviceInfo#TYPE_BLUETOOTH_A2DP} and {@link
     * AudioDeviceInfo#TYPE_BLUETOOTH_SCO} are included from API 23. And the types {@link
     * AudioDeviceInfo#TYPE_BLE_HEADSET} and {@link AudioDeviceInfo#TYPE_BLE_SPEAKER} are added from
     * API 31. And the type {@link AudioDeviceInfo#TYPE_BLE_BROADCAST} is added from API 33.
     */
    @DoNotInline
    private static ImmutableSet<Integer> getAllBluetoothDeviceTypes() {
      ImmutableSet.Builder<Integer> allBluetoothDeviceTypes =
          new ImmutableSet.Builder<Integer>()
              .add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
      if (Util.SDK_INT >= 31) {
        allBluetoothDeviceTypes.add(
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER);
      }
      if (Util.SDK_INT >= 33) {
        allBluetoothDeviceTypes.add(AudioDeviceInfo.TYPE_BLE_BROADCAST);
      }
      return allBluetoothDeviceTypes.build();
    }
  }

  @RequiresApi(29)
  private static final class Api29 {

    private Api29() {}

    @DoNotInline
    public static ImmutableList<Integer> getDirectPlaybackSupportedEncodings(
        AudioAttributes audioAttributes) {
      ImmutableList.Builder<Integer> supportedEncodingsListBuilder = ImmutableList.builder();
      for (int encoding : ALL_SURROUND_ENCODINGS_AND_MAX_CHANNELS.keySet()) {
        if (Util.SDK_INT < Util.getApiLevelThatAudioFormatIntroducedAudioEncoding(encoding)) {
          // Example: AudioFormat.ENCODING_DTS_UHD_P2 is supported only from API 34.
          continue;
        }
        if (AudioTrack.isDirectPlaybackSupported(
            new AudioFormat.Builder()
                .setChannelMask(CHANNEL_OUT_STEREO)
                .setEncoding(encoding)
                .setSampleRate(DEFAULT_SAMPLE_RATE_HZ)
                .build(),
            audioAttributes.getAudioAttributesV21().audioAttributes)) {
          supportedEncodingsListBuilder.add(encoding);
        }
      }
      supportedEncodingsListBuilder.add(AudioFormat.ENCODING_PCM_16BIT);
      return supportedEncodingsListBuilder.build();
    }

    /**
     * Returns the maximum number of channels supported for passthrough playback of audio in the
     * given format, or {@code 0} if the format is unsupported.
     */
    @DoNotInline
    public static int getMaxSupportedChannelCountForPassthrough(
        @C.Encoding int encoding, int sampleRate, AudioAttributes audioAttributes) {
      // TODO(internal b/234351617): Query supported channel masks directly once it's supported,
      // see also b/25994457.
      for (int channelCount = DEFAULT_MAX_CHANNEL_COUNT; channelCount > 0; channelCount--) {
        int channelConfig = Util.getAudioTrackChannelConfig(channelCount);
        if (channelConfig == AudioFormat.CHANNEL_INVALID) {
          continue;
        }
        AudioFormat audioFormat =
            new AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build();
        if (AudioTrack.isDirectPlaybackSupported(
            audioFormat, audioAttributes.getAudioAttributesV21().audioAttributes)) {
          return channelCount;
        }
      }
      return 0;
    }
  }

  @RequiresApi(33)
  private static final class Api33 {

    private Api33() {}

    @DoNotInline
    public static AudioCapabilities getCapabilitiesInternalForDirectPlayback(
        AudioManager audioManager, AudioAttributes audioAttributes) {
      List<android.media.AudioProfile> directAudioProfiles =
          audioManager.getDirectProfilesForAttributes(
              audioAttributes.getAudioAttributesV21().audioAttributes);
      return new AudioCapabilities(getAudioProfiles(directAudioProfiles));
    }

    @Nullable
    @DoNotInline
    public static AudioDeviceInfoApi23 getDefaultRoutedDeviceForAttributes(
        AudioManager audioManager, AudioAttributes audioAttributes) {
      List<AudioDeviceInfo> audioDevices;
      try {
        audioDevices =
            checkNotNull(audioManager)
                .getAudioDevicesForAttributes(
                    audioAttributes.getAudioAttributesV21().audioAttributes);
      } catch (RuntimeException e) {
        // Audio manager failed to retrieve devices.
        // TODO: b/306324391 - Remove once https://github.com/robolectric/robolectric/commit/442dff
        //  is released.
        return null;
      }
      if (audioDevices.isEmpty()) {
        // Can't find current device.
        return null;
      }
      // List only has more than one element if output devices are duplicated, so we assume the
      // first device in the list has all the information we need.
      return new AudioDeviceInfoApi23(audioDevices.get(0));
    }
  }
}
