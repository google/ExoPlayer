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

import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;

/**
 * Notifies a listener when the audio playback capabilities change. Call {@link #register} to start
 * receiving notifications, and {@link #unregister} to stop.
 */
public final class AudioCapabilitiesReceiver {

  /** Listener notified when audio capabilities change. */
  public interface Listener {

    /** Called when the audio capabilities change. */
    void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities);

  }

  /** Default to stereo PCM on SDK <= 21 and when HDMI is unplugged. */
  private static final AudioCapabilities DEFAULT_AUDIO_CAPABILITIES =
      new AudioCapabilities(new int[] {AudioFormat.ENCODING_PCM_16BIT}, 2);

  private final Context context;
  private final Listener listener;
  private final BroadcastReceiver receiver;

  /**
   * Constructs a new audio capabilities receiver.
   *
   * @param context Application context for registering to receive broadcasts.
   * @param listener Listener to notify when audio capabilities change.
   */
  public AudioCapabilitiesReceiver(Context context, Listener listener) {
    this.context = Assertions.checkNotNull(context);
    this.listener = Assertions.checkNotNull(listener);
    this.receiver = Util.SDK_INT >= 21 ? new HdmiAudioPlugBroadcastReceiver() : null;
  }

  /**
   * Registers to notify the listener when audio capabilities change. The listener will immediately
   * receive the current audio capabilities. It is important to call {@link #unregister} so that
   * the listener can be garbage collected.
   */
  @TargetApi(21)
  public void register() {
    if (receiver != null) {
      context.registerReceiver(receiver, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
    }

    listener.onAudioCapabilitiesChanged(DEFAULT_AUDIO_CAPABILITIES);
  }

  /** Unregisters to stop notifying the listener when audio capabilities change. */
  public void unregister() {
    if (receiver != null) {
      context.unregisterReceiver(receiver);
    }
  }

  @TargetApi(21)
  private final class HdmiAudioPlugBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (!action.equals(AudioManager.ACTION_HDMI_AUDIO_PLUG)) {
        return;
      }

      listener.onAudioCapabilitiesChanged(
          new AudioCapabilities(intent.getIntArrayExtra(AudioManager.EXTRA_ENCODINGS),
              intent.getIntExtra(AudioManager.EXTRA_MAX_CHANNEL_COUNT, 0)));
    }

  }

}
