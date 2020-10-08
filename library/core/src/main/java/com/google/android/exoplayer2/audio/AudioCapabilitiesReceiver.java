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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import androidx.mediarouter.media.MediaRouter.Callback;
import androidx.mediarouter.media.MediaRouter.ProviderInfo;
import androidx.mediarouter.media.MediaRouter.RouteInfo;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

/**
 * Receives broadcast events indicating changes to the device's audio capabilities, notifying a
 * {@link Listener} when audio capability changes occur.
 */
public final class AudioCapabilitiesReceiver {

  /**
   * Listener notified when audio capabilities change.
   */
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
  @Nullable private final BroadcastReceiver receiver;
  @Nullable private final ExternalSurroundSoundSettingObserver externalSurroundSoundSettingObserver;

  @Nullable /* package */ AudioCapabilities audioCapabilities;
  private boolean registered;

  Intent stickyIntent = null;
  private MediaRouter mediaRouter;
  private MediaRouteSelector mSelector;

  /**
   * @param context A context for registering the receiver.
   * @param listener The listener to notify when audio capabilities change.
   */
  public AudioCapabilitiesReceiver(Context context, Listener listener) {
    context = context.getApplicationContext();
    this.context = context;
    this.listener = Assertions.checkNotNull(listener);
    handler = Util.createHandlerForCurrentOrMainLooper();
    receiver = Util.SDK_INT >= 21 ? new HdmiAudioPlugBroadcastReceiver() : null;
    Uri externalSurroundSoundUri = AudioCapabilities.getExternalSurroundSoundGlobalSettingUri();
    externalSurroundSoundSettingObserver =
        externalSurroundSoundUri != null
            ? new ExternalSurroundSoundSettingObserver(
                handler, context.getContentResolver(), externalSurroundSoundUri)
            : null;

    mediaRouter = MediaRouter.getInstance(context);
    mSelector = new MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .build();
  }

  /**
   * Registers the receiver, meaning it will notify the listener when audio capability changes
   * occur. The current audio capabilities will be returned. It is important to call
   * {@link #unregister} when the receiver is no longer required.
   *
   * @return The current audio capabilities for the device.
   */
  @SuppressWarnings("InlinedApi")
  public AudioCapabilities register() {
    if (registered) {
      return Assertions.checkNotNull(audioCapabilities);
    }
    registered = true;
    if (externalSurroundSoundSettingObserver != null) {
      externalSurroundSoundSettingObserver.register();
    }
    if (receiver != null) {
      IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG);
      stickyIntent =
          context.registerReceiver(
              receiver, intentFilter, /* broadcastPermission= */ null, handler);
    }
    audioCapabilities = AudioCapabilities.getCapabilities(context, stickyIntent);

    mediaRouter.addCallback(mSelector, mediaRouterCallback,
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);

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
    if (receiver != null) {
      context.unregisterReceiver(receiver);
    }
    if (externalSurroundSoundSettingObserver != null) {
      externalSurroundSoundSettingObserver.unregister();
    }

    mediaRouter.removeCallback(mediaRouterCallback);

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
        onNewAudioCapabilities(AudioCapabilities.getCapabilities(context, intent));
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
      onNewAudioCapabilities(AudioCapabilities.getCapabilities(context));
    }
  }

  // Define the Callback object and its method to detect the Bluetooth speaker connenction
  private final MediaRouter.Callback mediaRouterCallback =
          new MediaRouter.Callback() {
            @Override
            public void onRouteChanged(MediaRouter router, RouteInfo route) {
            }

            @Override
            public void onRouteSelected(MediaRouter router, RouteInfo route) {
              if (route.getDeviceType() == 3) {
                AudioCapabilities.setExternalPcmOnlySoundset(true);
                onNewAudioCapabilities(AudioCapabilities.getCapabilities(context, null));
              }
            }

            @Override
            public void onRouteUnselected(MediaRouter router, RouteInfo route, int reason) {
              if (route.getDeviceType() == 3) {
                AudioCapabilities.setExternalPcmOnlySoundset(false);
                onNewAudioCapabilities(AudioCapabilities.getCapabilities(context, stickyIntent));
              }
            }
          };
}
