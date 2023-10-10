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

import static androidx.media3.test.utils.robolectric.TestPlayerRunHelper.runUntilPlayWhenReady;
import static androidx.media3.test.utils.robolectric.TestPlayerRunHelper.runUntilPlaybackState;
import static androidx.test.ext.truth.content.IntentSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.stream;
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
import androidx.media3.common.FlagSet;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Player.PlayWhenReadyChangeReason;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.TestExoPlayerBuilder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.ArrayList;
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
  private static final long TEST_TIME_OUT_MS = Duration.ofMinutes(10).toMillis();

  private ShadowPackageManager shadowPackageManager;
  private ShadowApplication shadowApplication;
  private Player testPlayer;

  @Before
  public void setUp() {
    testPlayer =
        new TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setSuppressPlaybackOnUnsuitableOutput(true)
            .build();
    shadowApplication = shadowOf((Application) ApplicationProvider.getApplicationContext());
    shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
  }

  @After
  public void afterTest() {
    testPlayer.release();
  }

  /**
   * Test end-to-end flow from launch of output switcher to playback getting resumed when the
   * playback is suppressed and then unsuppressed.
   */
  @Test
  public void playbackSuppressionFollowedByResolution_shouldLaunchOSwAndChangePlayerStateToPlaying()
      throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    registerFakeActivity(
        OUTPUT_SWITCHER_INTENT_ACTION_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME,
        FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME,
        ApplicationInfo.FLAG_SYSTEM);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();
    List<Boolean> playWhenReadyChangeSequence = new ArrayList<>();
    testPlayer.addListener(
        new Player.Listener() {
          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @PlayWhenReadyChangeReason int reason) {
            playWhenReadyChangeSequence.add(playWhenReady);
          }
        });

    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);
    addConnectedAudioOutput(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, /* notifyAudioDeviceCallbacks= */ true);
    runUntilPlayWhenReady(testPlayer, /* expectedPlayWhenReady= */ true);

    Intent intentTriggered = shadowApplication.getNextStartedActivity();
    assertThat(intentTriggered).isNotNull();
    assertThat(intentTriggered).hasAction(OUTPUT_SWITCHER_INTENT_ACTION_NAME);
    assertThat(intentTriggered)
        .hasComponent(
            FAKE_SYSTEM_OUTPUT_SWITCHER_PACKAGE_NAME, FAKE_SYSTEM_OUTPUT_SWITCHER_CLASS_NAME);
    assertThat(playWhenReadyChangeSequence).containsExactly(true, false, true);
    assertThat(testPlayer.isPlaying()).isTrue();
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
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
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
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
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
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
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
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
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
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
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
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
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
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
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
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();

    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    assertThat(shadowApplication.getNextStartedActivity()).isNull();
  }

  /**
   * Test for no launch of any system media output switching dialog app when playback is suppressed
   * due to removal of all suitable audio outputs in mid of an ongoing playback.
   */
  @Test
  public void
      playbackSuppressionDuringOngoingPlayback_shouldOnlyPauseButNotLaunchEitherOSwOrBTSettings()
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
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();
    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    removeConnectedAudioOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
    runUntilPlayWhenReady(testPlayer, /* expectedPlayWhenReady= */ false);

    assertThat(shadowApplication.getNextStartedActivity()).isNull();
    assertThat(testPlayer.isPlaying()).isFalse();
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
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();
    AtomicBoolean isPlaybackPaused = new AtomicBoolean(false);
    testPlayer.addListener(
        new Player.Listener() {
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
   * Test for automatic resumption of the ongoing playback when it is transferred from one suitable
   * device to another within set time out.
   */
  @Test
  public void
      transferOnGoingPlaybackFromOneSuitableDeviceToAnotherWithinSetTimeOut_shouldContinuePlayback()
          throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    setupConnectedAudioOutput(
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();
    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    removeConnectedAudioOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
    runUntilPlayWhenReady(testPlayer, /* expectedPlayWhenReady= */ false);

    addConnectedAudioOutput(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, /* notifyAudioDeviceCallbacks= */ true);
    runUntilPlayWhenReady(testPlayer, /* expectedPlayWhenReady= */ true);

    assertThat(testPlayer.isPlaying()).isTrue();
  }

  /**
   * Test for automatic pause of the ongoing playback when it is transferred from one suitable
   * device to another and the time difference between switching is more than default time out
   */
  @Test
  public void
      transferOnGoingPlaybackFromOneSuitableDeviceToAnotherAfterTimeOut_shouldNotContinuePlayback()
          throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    setupConnectedAudioOutput(
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
    FakeClock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext(),
            WearUnsuitableOutputPlaybackSuppressionResolverListener
                .DEFAULT_PLAYBACK_SUPPRESSION_AUTO_RESUME_TIMEOUT_MS,
            fakeClock));
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();
    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    removeConnectedAudioOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
    runUntilPlayWhenReady(testPlayer, /* expectedPlayWhenReady= */ false);

    fakeClock.advanceTime(
        WearUnsuitableOutputPlaybackSuppressionResolverListener
                .DEFAULT_PLAYBACK_SUPPRESSION_AUTO_RESUME_TIMEOUT_MS
            * 2);

    addConnectedAudioOutput(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, /* notifyAudioDeviceCallbacks= */ true);
    runUntilPlayWhenReady(testPlayer, /* expectedPlayWhenReady= */ false);

    assertThat(testPlayer.isPlaying()).isFalse();
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
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext()));
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();
    AtomicBoolean isPlaybackPaused = new AtomicBoolean(false);
    testPlayer.addListener(
        new Player.Listener() {
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

  /**
   * Test to ensure player is not playing when the playback suppression due to unsuitable output is
   * removed after the default timeout.
   */
  @Test
  public void
      playbackSuppressionChangeToNoneAfterDefaultTimeout_shouldNotChangePlaybackStateToPlaying()
          throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    FakeClock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext(),
            WearUnsuitableOutputPlaybackSuppressionResolverListener
                .DEFAULT_PLAYBACK_SUPPRESSION_AUTO_RESUME_TIMEOUT_MS,
            fakeClock));
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();
    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    fakeClock.advanceTime(
        WearUnsuitableOutputPlaybackSuppressionResolverListener
                .DEFAULT_PLAYBACK_SUPPRESSION_AUTO_RESUME_TIMEOUT_MS
            * 2);
    addConnectedAudioOutput(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, /* notifyAudioDeviceCallbacks= */ true);
    runUntilPlayWhenReady(testPlayer, /* expectedPlayWhenReady= */ false);

    assertThat(testPlayer.isPlaying()).isFalse();
  }

  /**
   * Test to ensure player is playing when the playback suppression due to unsuitable output is
   * removed within the set timeout.
   */
  @Test
  public void playbackSuppressionChangeToNoneWithinSetTimeout_shouldChangePlaybackStateToPlaying()
      throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    FakeClock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext(), TEST_TIME_OUT_MS, fakeClock));
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();
    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    fakeClock.advanceTime(TEST_TIME_OUT_MS / 2);
    addConnectedAudioOutput(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, /* notifyAudioDeviceCallbacks= */ true);
    runUntilPlayWhenReady(testPlayer, /* expectedPlayWhenReady= */ true);

    assertThat(testPlayer.isPlaying()).isTrue();
  }

  /**
   * Test to ensure player is not playing when the playback suppression due to unsuitable output is
   * removed after the set timeout.
   */
  @Test
  public void
      playbackSuppressionChangeToNoneAfterSetTimeout_shouldNotChangeFinalPlaybackStateToPlaying()
          throws TimeoutException {
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, /* supported= */ true);
    setupConnectedAudioOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
    FakeClock fakeClock = new FakeClock(/* isAutoAdvancing= */ true);
    testPlayer.addListener(
        new WearUnsuitableOutputPlaybackSuppressionResolverListener(
            ApplicationProvider.getApplicationContext(), TEST_TIME_OUT_MS, fakeClock));
    testPlayer.setMediaItem(
        MediaItem.fromUri("asset:///media/mp4/sample_with_increasing_timestamps_360p.mp4"));
    testPlayer.prepare();
    testPlayer.play();
    runUntilPlaybackState(testPlayer, Player.STATE_READY);

    fakeClock.advanceTime(TEST_TIME_OUT_MS * 2);
    addConnectedAudioOutput(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, /* notifyAudioDeviceCallbacks= */ true);
    runUntilPlayWhenReady(testPlayer, /* expectedPlayWhenReady= */ false);

    assertThat(testPlayer.isPlaying()).isFalse();
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

  private void addConnectedAudioOutput(int deviceTypes, boolean notifyAudioDeviceCallbacks) {
    ShadowAudioManager shadowAudioManager =
        shadowOf(ApplicationProvider.getApplicationContext().getSystemService(AudioManager.class));
    shadowAudioManager.addOutputDevice(
        AudioDeviceInfoBuilder.newBuilder().setType(deviceTypes).build(),
        notifyAudioDeviceCallbacks);
  }

  private void removeConnectedAudioOutput(int deviceType) {
    ShadowAudioManager shadowAudioManager =
        shadowOf(ApplicationProvider.getApplicationContext().getSystemService(AudioManager.class));
    stream(shadowAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
        .filter(audioDeviceInfo -> deviceType == audioDeviceInfo.getType())
        .findFirst()
        .ifPresent(
            filteredAudioDeviceInfo ->
                shadowAudioManager.removeOutputDevice(
                    filteredAudioDeviceInfo, /* notifyAudioDeviceCallbacks= */ true));
  }
}
