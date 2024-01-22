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

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/**
 * Receives broadcast events indicating changes to the device's audio capabilities, notifying a
 * {@link Listener} when audio capability changes occur.
 */
@UnstableApi
public final class AudioCapabilitiesReceiver {

  /** Listener notified when audio capabilities change. */
  public interface Listener {

    /**
     * Called when the audio capabilities change.
     *
     * @param audioCapabilities The current audio capabilities for the device.
     */
    void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities);
  }

  private final Context context;
  private final Listener listener;
  private final Handler handler;
  @Nullable private final AudioDeviceCallbackV23 audioDeviceCallback;
  @Nullable private final BroadcastReceiver hdmiAudioPlugBroadcastReceiver;
  @Nullable private final ExternalSurroundSoundSettingObserver externalSurroundSoundSettingObserver;

  @Nullable private AudioCapabilities audioCapabilities;
  @Nullable private AudioDeviceInfoApi23 routedDevice;
  private AudioAttributes audioAttributes;
  private boolean registered;

  /**
   * @deprecated Use {@link #AudioCapabilitiesReceiver(Context, Listener, AudioAttributes,
   *     AudioDeviceInfo)} instead.
   */
  @Deprecated
  public AudioCapabilitiesReceiver(Context context, Listener listener) {
    this(context, listener, AudioAttributes.DEFAULT, /* routedDevice= */ (AudioDeviceInfo) null);
  }

  /**
   * @param context A context for registering the receiver.
   * @param listener The listener to notify when audio capabilities change.
   * @param audioAttributes The {@link AudioAttributes}.
   * @param routedDevice The {@link AudioDeviceInfo} audio will be routed to if known, or null to
   *     assume the default route.
   */
  public AudioCapabilitiesReceiver(
      Context context,
      Listener listener,
      AudioAttributes audioAttributes,
      @Nullable AudioDeviceInfo routedDevice) {
    this(
        context,
        listener,
        audioAttributes,
        Util.SDK_INT >= 23 && routedDevice != null ? new AudioDeviceInfoApi23(routedDevice) : null);
  }

  /* package */ AudioCapabilitiesReceiver(
      Context context,
      Listener listener,
      AudioAttributes audioAttributes,
      @Nullable AudioDeviceInfoApi23 routedDevice) {
    context = context.getApplicationContext();
    this.context = context;
    this.listener = checkNotNull(listener);
    this.audioAttributes = audioAttributes;
    this.routedDevice = routedDevice;
    handler = Util.createHandlerForCurrentOrMainLooper();
    audioDeviceCallback = Util.SDK_INT >= 23 ? new AudioDeviceCallbackV23() : null;
    hdmiAudioPlugBroadcastReceiver =
        Util.SDK_INT >= 21 ? new HdmiAudioPlugBroadcastReceiver() : null;
    Uri externalSurroundSoundUri = AudioCapabilities.getExternalSurroundSoundGlobalSettingUri();
    externalSurroundSoundSettingObserver =
        externalSurroundSoundUri != null
            ? new ExternalSurroundSoundSettingObserver(
                handler, context.getContentResolver(), externalSurroundSoundUri)
            : null;
  }

  /**
   * Updates the {@link AudioAttributes} used by this instance.
   *
   * @param audioAttributes The {@link AudioAttributes}.
   */
  public void setAudioAttributes(AudioAttributes audioAttributes) {
    this.audioAttributes = audioAttributes;
    onNewAudioCapabilities(
        AudioCapabilities.getCapabilitiesInternal(context, audioAttributes, routedDevice));
  }

  /**
   * Updates the {@link AudioDeviceInfo} audio will be routed to.
   *
   * @param routedDevice The {@link AudioDeviceInfo} audio will be routed to if known, or null to
   *     assume the default route.
   */
  @RequiresApi(23)
  public void setRoutedDevice(@Nullable AudioDeviceInfo routedDevice) {
    if (Util.areEqual(
        routedDevice, this.routedDevice == null ? null : this.routedDevice.audioDeviceInfo)) {
      return;
    }
    this.routedDevice = routedDevice != null ? new AudioDeviceInfoApi23(routedDevice) : null;
    onNewAudioCapabilities(
        AudioCapabilities.getCapabilitiesInternal(context, audioAttributes, this.routedDevice));
  }

  /**
   * Registers the receiver, meaning it will notify the listener when audio capability changes
   * occur. The current audio capabilities will be returned. It is important to call {@link
   * #unregister} when the receiver is no longer required.
   *
   * @return The current audio capabilities for the device.
   */
  @SuppressWarnings("InlinedApi")
  public AudioCapabilities register() {
    if (registered) {
      return checkNotNull(audioCapabilities);
    }
    registered = true;
    if (externalSurroundSoundSettingObserver != null) {
      externalSurroundSoundSettingObserver.register();
    }
    if (Util.SDK_INT >= 23 && audioDeviceCallback != null) {
      Api23.registerAudioDeviceCallback(context, audioDeviceCallback, handler);
    }
    @Nullable Intent stickyIntent = null;
    if (hdmiAudioPlugBroadcastReceiver != null) {
      IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG);
      stickyIntent =
          context.registerReceiver(
              hdmiAudioPlugBroadcastReceiver,
              intentFilter,
              /* broadcastPermission= */ null,
              handler);
    }
    audioCapabilities =
        AudioCapabilities.getCapabilitiesInternal(
            context, stickyIntent, audioAttributes, routedDevice);
    return audioCapabilities;
  }

  /**
   * Unregisters the receiver, meaning it will no longer notify the listener when audio capability
   * changes occur.
   */
  public void unregister() {
    if (!registered) {
      return;
    }
    audioCapabilities = null;
    if (Util.SDK_INT >= 23 && audioDeviceCallback != null) {
      Api23.unregisterAudioDeviceCallback(context, audioDeviceCallback);
    }
    if (hdmiAudioPlugBroadcastReceiver != null) {
      context.unregisterReceiver(hdmiAudioPlugBroadcastReceiver);
    }
    if (externalSurroundSoundSettingObserver != null) {
      externalSurroundSoundSettingObserver.unregister();
    }
    registered = false;
  }

  private void onNewAudioCapabilities(AudioCapabilities newAudioCapabilities) {
    if (registered && !newAudioCapabilities.equals(audioCapabilities)) {
      audioCapabilities = newAudioCapabilities;
      listener.onAudioCapabilitiesChanged(newAudioCapabilities);
    }
  }

  private final class HdmiAudioPlugBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      if (!isInitialStickyBroadcast()) {
        onNewAudioCapabilities(
            AudioCapabilities.getCapabilitiesInternal(
                context, intent, audioAttributes, routedDevice));
      }
    }
  }

  private final class ExternalSurroundSoundSettingObserver extends ContentObserver {

    private final ContentResolver resolver;
    private final Uri settingUri;

    public ExternalSurroundSoundSettingObserver(
        Handler handler, ContentResolver resolver, Uri settingUri) {
      super(handler);
      this.resolver = resolver;
      this.settingUri = settingUri;
    }

    public void register() {
      resolver.registerContentObserver(settingUri, /* notifyForDescendants= */ false, this);
    }

    public void unregister() {
      resolver.unregisterContentObserver(this);
    }

    @Override
    public void onChange(boolean selfChange) {
      onNewAudioCapabilities(
          AudioCapabilities.getCapabilitiesInternal(context, audioAttributes, routedDevice));
    }
  }

  @RequiresApi(23)
  private final class AudioDeviceCallbackV23 extends AudioDeviceCallback {
    @Override
    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
      onNewAudioCapabilities(
          AudioCapabilities.getCapabilitiesInternal(context, audioAttributes, routedDevice));
    }

    @Override
    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
      if (Util.contains(removedDevices, routedDevice)) {
        routedDevice = null;
      }
      onNewAudioCapabilities(
          AudioCapabilities.getCapabilitiesInternal(context, audioAttributes, routedDevice));
    }
  }

  @RequiresApi(23)
  private static final class Api23 {

    @DoNotInline
    public static void registerAudioDeviceCallback(
        Context context, AudioDeviceCallback callback, Handler handler) {
      AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
      checkNotNull(audioManager).registerAudioDeviceCallback(callback, handler);
    }

    @DoNotInline
    public static void unregisterAudioDeviceCallback(
        Context context, AudioDeviceCallback callback) {
      AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
      checkNotNull(audioManager).unregisterAudioDeviceCallback(callback);
    }

    private Api23() {}
  }
}
