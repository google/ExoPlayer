/*
 * Copyright (C) 2023 The Android Open Source Project
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
package androidx.media3.ui;

import static androidx.media3.common.util.Assertions.checkArgument;
import static java.util.concurrent.TimeUnit.MINUTES;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Events;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.SystemClock;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import java.util.List;

/**
 * A {@link Player.Listener} that launches a system dialog in response to {@link
 * Player#PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT} to allow the user to connect a
 * suitable audio output. Also, it auto-resumes the playback when the playback suppression reason is
 * changed from {@link Player#PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT} to {@link
 * Player#PLAYBACK_SUPPRESSION_REASON_NONE}.
 *
 * <p>This listener only reacts to {@link
 * Player#PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT} on Wear OS devices, while being no-op
 * for non-Wear OS devices.
 *
 * <p>The system dialog will be the <a
 * href="https://developer.android.com/media/routing#output-switcher">Media Output Switcher</a> if
 * it is available on the device, or otherwise the Bluetooth settings screen.
 *
 * <p>This implementation also pauses playback when launching the system dialog. The underlying
 * {@link Player} implementation (e.g. ExoPlayer) is expected to resume playback automatically when
 * a suitable audio device is connected by the user.
 */
@UnstableApi
public final class WearUnsuitableOutputPlaybackSuppressionResolverListener
    implements Player.Listener {

  /** Output switcher intent action for the Wear OS. */
  private static final String OUTPUT_SWITCHER_INTENT_ACTION_NAME =
      "com.android.settings.panel.action.MEDIA_OUTPUT";

  /** A package name key for output switcher intent in the Wear OS. */
  private static final String EXTRA_OUTPUT_SWITCHER_PACKAGE_NAME =
      "com.android.settings.panel.extra.PACKAGE_NAME";

  /**
   * Extra in the Bluetooth Activity intent to control whether the fragment should close when a
   * device connects.
   */
  private static final String EXTRA_BLUETOOTH_SETTINGS_CLOSE_ON_CONNECT = "EXTRA_CLOSE_ON_CONNECT";

  /**
   * Extra in the Bluetooth Activity intent to indicate that the user only wants to connect or
   * disconnect, not forget paired devices or do any other device management.
   */
  private static final String EXTRA_BLUETOOTH_SETTINGS_CONNECTION_ONLY = "EXTRA_CONNECTION_ONLY";

  /**
   * Extra in the Bluetooth Activity intent to specify the type of filtering that needs to be be
   * applied to the device list.
   */
  private static final String EXTRA_BLUETOOTH_SETTINGS_FILTER_TYPE =
      "android.bluetooth.devicepicker.extra.FILTER_TYPE";

  /**
   * The value for the {@link #EXTRA_BLUETOOTH_SETTINGS_FILTER_TYPE} in the Bluetooth intent to show
   * BT devices that support AUDIO profiles
   */
  private static final int FILTER_TYPE_AUDIO = 1;

  /**
   * The default timeout for auto-resume of suppressed playback when the playback suppression reason
   * as {@link Player#PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT} is removed, in
   * milliseconds.
   */
  public static final long DEFAULT_PLAYBACK_SUPPRESSION_AUTO_RESUME_TIMEOUT_MS =
      MINUTES.toMillis(5);

  private final Context applicationContext;
  private final long autoResumeTimeoutAfterUnsuitableOutputSuppressionMs;
  private final Clock clock;

  private long unsuitableOutputPlaybackSuppressionStartRealtimeMs;

  /**
   * Creates a new instance.
   *
   * <p>See {@link #WearUnsuitableOutputPlaybackSuppressionResolverListener(Context, long)} for more
   * details. The auto-resume timeout defaults to {@link
   * #DEFAULT_PLAYBACK_SUPPRESSION_AUTO_RESUME_TIMEOUT_MS}.
   *
   * @param context Any context.
   */
  public WearUnsuitableOutputPlaybackSuppressionResolverListener(Context context) {
    this(context, DEFAULT_PLAYBACK_SUPPRESSION_AUTO_RESUME_TIMEOUT_MS);
  }

  /**
   * Creates a new instance.
   *
   * @param context Any context.
   * @param autoResumeTimeoutMs Duration in milliseconds after the playback suppression during which
   *     playback will be resumed automatically if the playback suppression reason is changed from
   *     {@link Player#PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT} to {@link
   *     Player#PLAYBACK_SUPPRESSION_REASON_NONE}. Calling with {@code autoResumeTimeoutMs = 0} will
   *     cause playback to never resume automatically.
   */
  public WearUnsuitableOutputPlaybackSuppressionResolverListener(
      Context context, @IntRange(from = 0) long autoResumeTimeoutMs) {
    this(context, autoResumeTimeoutMs, SystemClock.DEFAULT);
  }

  @VisibleForTesting
  /* package */ WearUnsuitableOutputPlaybackSuppressionResolverListener(
      Context context, @IntRange(from = 0) long autoResumeTimeoutMs, Clock clock) {
    checkArgument(autoResumeTimeoutMs >= 0);
    applicationContext = context.getApplicationContext();
    autoResumeTimeoutAfterUnsuitableOutputSuppressionMs = autoResumeTimeoutMs;
    this.clock = clock;
    unsuitableOutputPlaybackSuppressionStartRealtimeMs = C.TIME_UNSET;
  }

  @Override
  public void onEvents(Player player, Events events) {
    if (!Util.isWear(applicationContext)) {
      return;
    }
    if ((events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)
            || events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED))
        && player.getPlayWhenReady()
        && player.getPlaybackSuppressionReason()
            == Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT) {
      player.pause();
      unsuitableOutputPlaybackSuppressionStartRealtimeMs = clock.elapsedRealtime();
      if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
        launchSystemMediaOutputSwitcherUi(applicationContext);
      }
    } else if (events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)
        && player.getPlaybackSuppressionReason() == Player.PLAYBACK_SUPPRESSION_REASON_NONE
        && unsuitableOutputPlaybackSuppressionStartRealtimeMs != C.TIME_UNSET
        && (clock.elapsedRealtime() - unsuitableOutputPlaybackSuppressionStartRealtimeMs
            < autoResumeTimeoutAfterUnsuitableOutputSuppressionMs)) {
      unsuitableOutputPlaybackSuppressionStartRealtimeMs = C.TIME_UNSET;
      player.play();
    }
  }

  /**
   * Launches the system media output switcher app if it is available on the device, or otherwise
   * the Bluetooth settings screen.
   */
  private static void launchSystemMediaOutputSwitcherUi(Context context) {
    Intent outputSwitcherLaunchIntent =
        new Intent(OUTPUT_SWITCHER_INTENT_ACTION_NAME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_OUTPUT_SWITCHER_PACKAGE_NAME, context.getPackageName());
    ComponentName outputSwitcherSystemComponentName =
        getSystemOrSystemUpdatedAppComponent(context, outputSwitcherLaunchIntent);
    if (outputSwitcherSystemComponentName != null) {
      outputSwitcherLaunchIntent.setComponent(outputSwitcherSystemComponentName);
      context.startActivity(outputSwitcherLaunchIntent);
    } else {
      Intent bluetoothSettingsLaunchIntent =
          new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
              .putExtra(EXTRA_BLUETOOTH_SETTINGS_CLOSE_ON_CONNECT, true)
              .putExtra(EXTRA_BLUETOOTH_SETTINGS_CONNECTION_ONLY, true)
              .putExtra(EXTRA_BLUETOOTH_SETTINGS_FILTER_TYPE, FILTER_TYPE_AUDIO);
      ComponentName bluetoothSettingsSystemComponentName =
          getSystemOrSystemUpdatedAppComponent(context, bluetoothSettingsLaunchIntent);
      if (bluetoothSettingsSystemComponentName != null) {
        bluetoothSettingsLaunchIntent.setComponent(bluetoothSettingsSystemComponentName);
        context.startActivity(bluetoothSettingsLaunchIntent);
      }
    }
  }

  /**
   * Returns {@link ComponentName} of system or updated system app's activity resolved from the
   * {@link Intent} passed to it.
   */
  private static @Nullable ComponentName getSystemOrSystemUpdatedAppComponent(
      Context context, Intent intent) {
    PackageManager packageManager = context.getPackageManager();
    List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, /* flags= */ 0);
    for (ResolveInfo resolveInfo : resolveInfos) {
      ActivityInfo activityInfo = resolveInfo.activityInfo;
      if (activityInfo == null || activityInfo.applicationInfo == null) {
        continue;
      }
      ApplicationInfo appInfo = activityInfo.applicationInfo;
      int systemAndUpdatedSystemAppFlags =
          ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
      if ((systemAndUpdatedSystemAppFlags & appInfo.flags) != 0) {
        return new ComponentName(activityInfo.packageName, activityInfo.name);
      }
    }
    return null;
  }
}
