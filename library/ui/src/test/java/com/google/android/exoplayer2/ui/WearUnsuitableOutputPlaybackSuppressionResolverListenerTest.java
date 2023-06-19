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
package com.google.android.exoplayer2.ui;

import static androidx.test.ext.truth.content.IntentSubject.assertThat;
import static com.google.android.exoplayer2.robolectric.TestPlayerRunHelper.runUntilPlaybackState;
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Looper;
import android.provider.Settings;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.Listener;
import com.google.android.exoplayer2.testutil.TestExoPlayerBuilder;
import com.google.android.exoplayer2.util.FlagSet;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.AudioDeviceInfoBuilder;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowAudioManager;
import org.robolectric.shadows.ShadowPackageManager;

/** Tests for the {@link WearUnsuitableOutputPlaybackSuppressionResolverListener}. */
@RunWith(AndroidJUnit4.class)
public class WearUnsuitableOutputPlaybackSuppressionResolverListenerTest {

  private static final String OUTPUT_SWITCHER_INTENT_ACTION_NAME =
      "com.android.settings.panel.action.MEDIA_OUTPUT";
  private static final String FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME = "com.fake.outputswitcher";
  private static final String FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME =
      "com.fake.outputswitcher.OutputSwitcherActivity";
  private static final String FAKE_SYSTEM_BT_SETTINGS_PACKAGE_NAME = "com.fake.btsettings";
  private static final String FAKE_SYSTEM_BT_SETTINGS_CLASS_NAME =
      "com.fake.btsettings.BluetoothSettingsActivity";

  private ShadowPackageManager shadowPackageManager;
  private ShadowApplication shadowApplication;
  private Player testPlayer;

  @Before
  public void setUp() {
    testPlayer =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setSuppressPlaybackOnUnsuitableOutput(true)
            .build();
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
    shadowApplication = shadowOf((Application) ApplicationProvider.getApplicationContext());
    shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
  }

  @After
  public void afterTest() {
    testPlayer.release();
  }

  /**
   * Test for the launch of system Output Switcher app when playback is suppressed due to unsuitable
   * output and the system Output Switcher is present on the device.
   */
  @Test
  public void
      playEventWithPlaybackSuppressionWhenSystemOutputSwitcherPresent_shouldLaunchOutputSwitcher()
          throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    registerFakeActivity(
        OUTPUT_SWITCHER_INTENT_ACTION_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME,
        ApplicationInfo.FLAG_SYSTEM);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();

    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    Intent intentTriggered = shadowApplication.getNextStartedActivity();
    assertThat(intentTriggered).isNotNull();
    assertThat(intentTriggered).hasAction(OUTPUT_SWITCHER_INTENT_ACTION_NAME);
    assertThat(intentTriggered)
        .hasComponent(
            FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME, FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME);
  }

  /**
   * Test for the launch of system updated Output Switcher app when playback is suppressed due to
   * unsuitable output and the system updated Output Switcher is present on the device.
   */
  @Test
  public void playEventWithPlaybackSuppressionWhenUpdatedSystemOSwPresent_shouldLaunchOSw()
      throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    registerFakeActivity(
        OUTPUT_SWITCHER_INTENT_ACTION_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME,
        ApplicationInfo.FLAG_UPDATED_SYSTEM_APP);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();

    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    Intent intentTriggered = shadowApplication.getNextStartedActivity();
    assertThat(intentTriggered).isNotNull();
    assertThat(intentTriggered).hasAction(OUTPUT_SWITCHER_INTENT_ACTION_NAME);
    assertThat(intentTriggered)
        .hasComponent(
            FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME, FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME);
  }

  /**
   * Test for the launch of system Output Switcher app when playback is suppressed due to unsuitable
   * output and both the system as well as user installed Output Switcher are present on the device.
   */
  @Test
  public void
      playbackSuppressionWhenBothSystemAndUserInstalledOutputSwitcherPresent_shouldLaunchSystemOSw()
          throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    registerFakeActivity(
        OUTPUT_SWITCHER_INTENT_ACTION_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME,
        ApplicationInfo.FLAG_SYSTEM);
    registerFakeActivity(
        OUTPUT_SWITCHER_INTENT_ACTION_NAME,
        "com.fake.userinstalled.outputswitcher",
        "com.fake.userinstalled.outputswitcher.OutputSwitcherActivity",
        /* applicationFlags= */ 0);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();

    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    Intent intentTriggered = shadowApplication.getNextStartedActivity();
    assertThat(intentTriggered).isNotNull();
    assertThat(intentTriggered).hasAction(OUTPUT_SWITCHER_INTENT_ACTION_NAME);
    assertThat(intentTriggered)
        .hasComponent(
            FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME, FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME);
  }

  /**
   * Test for no launch of system Output Switcher app when running on non-Wear OS device with
   * playback suppression conditions and the system Output Switcher present on the device.
   */
  @Test
  public void
      playEventWithPlaybackSuppressionConditionsOnNonWearOSDevice_shouldNotLaunchOutputSwitcher()
          throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    registerFakeActivity(
        OUTPUT_SWITCHER_INTENT_ACTION_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME,
        ApplicationInfo.FLAG_SYSTEM);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();
    testPlayer.play();
    shadowApplication.clearNextStartedActivities();
    // Clear the system feature for "watch" to test on the non-Wear OS devices.
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ false);
    Player.Listener testPlayerListener =
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext());
    testPlayer.addListener(testPlayerListener);
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    testPlayerListener.onEvents(
        testPlayer,
        new Player.Events(new FlagSet.Builder().add(Player.EVENT_PLAY_WHEN_READY_CHANGED).build()));
    shadowOf(Looper.getMainLooper()).idle();

    Intent activityIntentTriggered = shadowApplication.getNextStartedActivity();
    assertThat(activityIntentTriggered).isNull();
    List<Intent> broadcastIntents = shadowApplication.getBroadcastIntents();
    assertThat(broadcastIntents).isEmpty();
  }

  /**
   * Test for the launch of Bluetooth Settings app when playback is suppressed due to unsuitable
   * output with the system Bluetooth Settings app present while the system Output Switcher app is
   * not present on the device.
   */
  @Test
  public void
      playEventWithPlaybackSuppressionWhenOnlySystemBTSettingsPresent_shouldLaunchBTSettings()
          throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    registerFakeActivity(
        Settings.ACTION_BLUETOOTH_SETTINGS,
        FAKE_SYSTEM_BT_SETTINGS_PACKAGE_NAME,
        FAKE_SYSTEM_BT_SETTINGS_CLASS_NAME,
        ApplicationInfo.FLAG_SYSTEM);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();

    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    Intent intentTriggered = shadowApplication.getNextStartedActivity();
    assertThat(intentTriggered).isNotNull();
    assertThat(intentTriggered).hasAction(Settings.ACTION_BLUETOOTH_SETTINGS);
    assertThat(intentTriggered)
        .hasComponent(FAKE_SYSTEM_BT_SETTINGS_PACKAGE_NAME, FAKE_SYSTEM_BT_SETTINGS_CLASS_NAME);
  }

  /**
   * Test for the launch of Bluetooth Settings app when playback is suppressed due to unsuitable
   * output with the updated system Bluetooth Settings app present while the Output Switcher app is
   * not present on the device.
   */
  @Test
  public void playbackSuppressionWhenOnlyUpdatedSystemBTSettingsPresent_shouldLaunchBTSettings()
      throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    registerFakeActivity(
        Settings.ACTION_BLUETOOTH_SETTINGS,
        FAKE_SYSTEM_BT_SETTINGS_PACKAGE_NAME,
        FAKE_SYSTEM_BT_SETTINGS_CLASS_NAME,
        ApplicationInfo.FLAG_SYSTEM);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();

    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    Intent intentTriggered = shadowApplication.getNextStartedActivity();
    assertThat(intentTriggered).isNotNull();
    assertThat(intentTriggered).hasAction(Settings.ACTION_BLUETOOTH_SETTINGS);
    assertThat(intentTriggered)
        .hasComponent(FAKE_SYSTEM_BT_SETTINGS_PACKAGE_NAME, FAKE_SYSTEM_BT_SETTINGS_CLASS_NAME);
  }

  /**
   * Test for the launch of Output Switcher app when playback is suppressed due to unsuitable output
   * and both Output Switcher as well as the Bluetooth settings are present on the device.
   */
  @Test
  public void playEventWithPlaybackSuppressionWhenOSwAndBTSettingsBothPresent_shouldLaunchOSw()
      throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    registerFakeActivity(
        OUTPUT_SWITCHER_INTENT_ACTION_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME,
        ApplicationInfo.FLAG_SYSTEM);
    registerFakeActivity(
        Settings.ACTION_BLUETOOTH_SETTINGS,
        FAKE_SYSTEM_BT_SETTINGS_PACKAGE_NAME,
        FAKE_SYSTEM_BT_SETTINGS_CLASS_NAME,
        ApplicationInfo.FLAG_SYSTEM);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();

    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    Intent intentTriggered = shadowApplication.getNextStartedActivity();
    assertThat(intentTriggered).isNotNull();
    assertThat(intentTriggered).hasAction(OUTPUT_SWITCHER_INTENT_ACTION_NAME);
    assertThat(intentTriggered)
        .hasComponent(
            FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME, FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME);
  }

  /**
   * Test for no launch of the non-system and non-system updated Output Switcher app when playback
   * is suppressed due to unsuitable output.
   */
  @Test
  public void
      playbackSuppressionWhenOnlyUserInstalledOSwAndBTSettingsPresent_shouldNotLaunchAnyApp()
          throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    registerFakeActivity(
        OUTPUT_SWITCHER_INTENT_ACTION_NAME,
        "com.fake.userinstalled.outputswitcher",
        "com.fake.userinstalled.outputswitcher.OutputSwitcherActivity",
        /* applicationFlags= */ 0);
    registerFakeActivity(
        Settings.ACTION_BLUETOOTH_SETTINGS,
        "com.fake.userinstalled.btsettings",
        "com.fake.userinstalled.btsettings.BluetoothSettingsActivity",
        /* applicationFlags= */ 0);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();

    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    Intent intentTriggered = shadowApplication.getNextStartedActivity();
    assertThat(intentTriggered).isNull();
  }

  /**
   * Test for no launch of any system media output switching dialog app when playback is not
   * suppressed due to unsuitable output.
   */
  @Test
  public void playEventWithoutPlaybackSuppression_shouldNotLaunchEitherOSwOrBTSettings()
      throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    registerFakeActivity(
        OUTPUT_SWITCHER_INTENT_ACTION_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME,
        ApplicationInfo.FLAG_SYSTEM);
    registerFakeActivity(
        Settings.ACTION_BLUETOOTH_SETTINGS,
        FAKE_SYSTEM_BT_SETTINGS_PACKAGE_NAME,
        FAKE_SYSTEM_BT_SETTINGS_CLASS_NAME,
        ApplicationInfo.FLAG_SYSTEM);
    setupConnectedAudioOutput(
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();

    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    assertThat(shadowApplication.getNextStartedActivity()).isNull();
  }

  /** Test for pause on the Player when the playback is suppressed due to unsuitable output. */
  @Test
  public void playEventWithSuppressedPlaybackCondition_shouldCallPauseOnPlayer()
      throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    registerFakeActivity(
        OUTPUT_SWITCHER_INTENT_ACTION_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME,
        ApplicationInfo.FLAG_SYSTEM);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();
    AtomicBoolean isPlaybackPaused = new AtomicBoolean(false);
    testPlayer.addListener(
        new Listener() {
          @Override
          public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (!playWhenReady) {
              isPlaybackPaused.set(true);
            }
          }
        });

    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    assertThat(isPlaybackPaused.get()).isTrue();
  }

  /**
   * Test for no pause on the Player when the playback is not suppressed due to unsuitable output.
   */
  @Test
  public void playEventWithoutSuppressedPlaybackCondition_shouldNotCallPauseOnPlayer()
      throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    registerFakeActivity(
        OUTPUT_SWITCHER_INTENT_ACTION_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME,
        ApplicationInfo.FLAG_SYSTEM);
    setupConnectedAudioOutput(
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();
    AtomicBoolean isPlaybackPaused = new AtomicBoolean(false);
    testPlayer.addListener(
        new Listener() {
          @Override
          public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (!playWhenReady) {
              isPlaybackPaused.set(true);
            }
          }
        });

    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    assertThat(isPlaybackPaused.get()).isFalse();
  }

  private void registerFakeActivity(
      String fakeActionName, String fakePackageName, String fakeClassName, int applicationFlags) {
    ComponentName fakeComponentName = new ComponentName(fakePackageName, fakeClassName);

    ApplicationInfo systemAppInfo = new ApplicationInfo();
    systemAppInfo.flags |= applicationFlags;

    ActivityInfo fakeActivityInfo = new ActivityInfo();
    fakeActivityInfo.applicationInfo = systemAppInfo;
    fakeActivityInfo.name = fakeComponentName.getClassName();
    fakeActivityInfo.packageName = fakeComponentName.getPackageName();

    shadowPackageManager.addOrUpdateActivity(fakeActivityInfo);
    shadowPackageManager.addIntentFilterForActivity(
        fakeComponentName, new IntentFilter(fakeActionName));
  }

  private void setupConnectedAudioOutput(int... deviceTypes) {
    ShadowAudioManager shadowAudioManager =
        shadowOf(ApplicationProvider.getApplicationContext().getSystemService(AudioManager.class));
    ImmutableList.Builder<AudioDeviceInfo> deviceListBuilder = ImmutableList.builder();
    for (int deviceType : deviceTypes) {
      deviceListBuilder.add(AudioDeviceInfoBuilder.newBuilder().setType(deviceType).build());
    }
    shadowAudioManager.setOutputDevices(deviceListBuilder.build());
  }
}
