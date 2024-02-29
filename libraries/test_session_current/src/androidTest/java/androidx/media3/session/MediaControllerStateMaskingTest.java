/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.session;

import static androidx.media3.session.MediaTestUtils.createMediaItems;
import static androidx.media3.session.MediaTestUtils.createTimeline;
import static androidx.media3.test.session.common.CommonConstants.DEFAULT_TEST_NAME;
import static androidx.media3.test.session.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.getEventsAsList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Timeline;
import androidx.media3.common.Timeline.Period;
import androidx.media3.common.Timeline.Window;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/** Tests for state masking {@link MediaController} ({@link MediaControllerImplBase}) calls. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerStateMaskingTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaControllerStateMaskingTest");
  final MediaControllerTestRule controllerTestRule = new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  Context context;
  RemoteMediaSession remoteSession;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    remoteSession = createRemoteMediaSession(DEFAULT_TEST_NAME);
  }

  @After
  public void cleanUp() throws RemoteException {
    if (remoteSession != null) {
      remoteSession.cleanUp();
      remoteSession = null;
    }
  }

  @Test
  public void setPlayWhenReady() throws Exception {
    boolean testPlayWhenReady = true;
    @Player.PlaybackSuppressionReason int testReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE;
    boolean testIsPlaying = true;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(false)
            .setPlaybackSuppressionReason(
                Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(4);
    AtomicBoolean playWhenReadyFromCallbackRef = new AtomicBoolean();
    AtomicInteger playbackSuppressionReasonFromCallbackRef = new AtomicInteger();
    AtomicBoolean isPlayingFromCallbackRef = new AtomicBoolean();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
            playWhenReadyFromCallbackRef.set(playWhenReady);
            latch.countDown();
          }

          @Override
          public void onPlaybackSuppressionReasonChanged(int playbackSuppressionReason) {
            playbackSuppressionReasonFromCallbackRef.set(playbackSuppressionReason);
            latch.countDown();
          }

          @Override
          public void onIsPlayingChanged(boolean isPlaying) {
            isPlayingFromCallbackRef.set(isPlaying);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicBoolean playWhenReadyFromGetterRef = new AtomicBoolean();
    AtomicInteger playbackSuppressionReasonFromGetterRef = new AtomicInteger();
    AtomicBoolean isPlayingFromGetterRef = new AtomicBoolean();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setPlayWhenReady(testPlayWhenReady);
              playWhenReadyFromGetterRef.set(controller.getPlayWhenReady());
              playbackSuppressionReasonFromGetterRef.set(controller.getPlaybackSuppressionReason());
              isPlayingFromGetterRef.set(controller.isPlaying());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playWhenReadyFromCallbackRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playbackSuppressionReasonFromCallbackRef.get()).isEqualTo(testReason);
    assertThat(isPlayingFromCallbackRef.get()).isEqualTo(testIsPlaying);
    assertThat(TestUtils.getEventsAsList(onEventsRef.get()))
        .containsExactly(
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
            Player.EVENT_IS_PLAYING_CHANGED);
    assertThat(playWhenReadyFromGetterRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playbackSuppressionReasonFromGetterRef.get()).isEqualTo(testReason);
    assertThat(isPlayingFromGetterRef.get()).isEqualTo(testIsPlaying);
  }

  @Test
  public void setPlayWhenReady_forTrueWhenPlaybackSuppressed_shouldNotChangePlaybackSuppression()
      throws Exception {
    CountDownLatch eventCallsCountDownLatch = new CountDownLatch(1);
    AtomicReference<@Player.PlaybackSuppressionReason Integer> playbackSuppressionReasonChangedRef =
        new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    MediaController controller =
        getMediaControllerToTestPlaybackSuppression(
            /* initialPlayWhenReadyState= */ false,
            playbackSuppressionReasonChangedRef,
            eventsRef,
            eventCallsCountDownLatch);

    threadTestRule.getHandler().postAndSync(() -> controller.setPlayWhenReady(true));

    assertNoPlaybackSuppressionReasonChange(
        playbackSuppressionReasonChangedRef, eventsRef, eventCallsCountDownLatch);
  }

  @Test
  public void setPlayWhenReady_withFalseWhenPlaybackSuppressed_shouldNotChangePlaybackSuppression()
      throws Exception {
    CountDownLatch eventCallsCountDownLatch = new CountDownLatch(1);
    AtomicReference<@Player.PlaybackSuppressionReason Integer> playbackSuppressionReasonChangedRef =
        new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    MediaController controller =
        getMediaControllerToTestPlaybackSuppression(
            /* initialPlayWhenReadyState= */ true,
            playbackSuppressionReasonChangedRef,
            eventsRef,
            eventCallsCountDownLatch);

    threadTestRule.getHandler().postAndSync(() -> controller.setPlayWhenReady(false));

    assertNoPlaybackSuppressionReasonChange(
        playbackSuppressionReasonChangedRef, eventsRef, eventCallsCountDownLatch);
  }

  private MediaController getMediaControllerToTestPlaybackSuppression(
      boolean initialPlayWhenReadyState,
      AtomicReference<@Player.PlaybackSuppressionReason Integer>
          playbackSuppressionReasonChangedRef,
      AtomicReference<Player.Events> eventsRef,
      CountDownLatch countDownLatchForOnEventCalls)
      throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(initialPlayWhenReadyState)
            .setPlaybackSuppressionReason(
                Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                controller.addListener(
                    getPlayerListenerToCapturePlaybackSuppression(
                        playbackSuppressionReasonChangedRef,
                        eventsRef,
                        countDownLatchForOnEventCalls)));
    return controller;
  }

  private Player.Listener getPlayerListenerToCapturePlaybackSuppression(
      AtomicReference<@Player.PlaybackSuppressionReason Integer>
          playbackSuppressionReasonChangedRef,
      AtomicReference<Player.Events> eventsRef,
      CountDownLatch countDownLatchForOnEventCalls) {
    return new Player.Listener() {
      @Override
      public void onEvents(Player player, Player.Events events) {
        eventsRef.set(events);
        if (events.contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)) {
          playbackSuppressionReasonChangedRef.set(player.getPlaybackSuppressionReason());
        }
        countDownLatchForOnEventCalls.countDown();
      }
    };
  }

  private void assertNoPlaybackSuppressionReasonChange(
      AtomicReference<@Player.PlaybackSuppressionReason Integer>
          playbackSuppressionReasonChangedRef,
      AtomicReference<Player.Events> eventsRef,
      CountDownLatch countDownLatchForOnEventCalls)
      throws Exception {
    assertThat(countDownLatchForOnEventCalls.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackSuppressionReasonChangedRef.get()).isNull();
    assertThat(eventsRef.get().contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED))
        .isFalse();
  }

  @Test
  public void setShuffleModeEnabled() throws Exception {
    boolean testShuffleModeEnabled = true;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setShuffleModeEnabled(false).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicBoolean shuffleModeEnabledFromCallbackRef = new AtomicBoolean();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            shuffleModeEnabledFromCallbackRef.set(shuffleModeEnabled);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicBoolean shuffleModeEnabledFromGetterRef = new AtomicBoolean();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setShuffleModeEnabled(testShuffleModeEnabled);
              shuffleModeEnabledFromGetterRef.set(controller.getShuffleModeEnabled());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(shuffleModeEnabledFromCallbackRef.get()).isEqualTo(testShuffleModeEnabled);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED);
    assertThat(shuffleModeEnabledFromGetterRef.get()).isEqualTo(testShuffleModeEnabled);
  }

  @Test
  public void setRepeatMode() throws Exception {
    int testRepeatMode = Player.REPEAT_MODE_ALL;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setRepeatMode(Player.REPEAT_MODE_ONE)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger repeatModeFromCallbackRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(int repeatMode) {
            repeatModeFromCallbackRef.set(repeatMode);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger repeatModeFromGetterRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setRepeatMode(testRepeatMode);
              repeatModeFromGetterRef.set(controller.getRepeatMode());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(repeatModeFromCallbackRef.get()).isEqualTo(testRepeatMode);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_REPEAT_MODE_CHANGED);
    assertThat(repeatModeFromGetterRef.get()).isEqualTo(testRepeatMode);
  }

  @Test
  public void setPlaybackParameters() throws Exception {
    PlaybackParameters testPlaybackParameters = new PlaybackParameters(2f, 2f);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackParameters(PlaybackParameters.DEFAULT)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<PlaybackParameters> playbackParametersFromCallbackRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            playbackParametersFromCallbackRef.set(playbackParameters);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicReference<PlaybackParameters> playbackParametersFromGetterRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setPlaybackParameters(testPlaybackParameters);
              playbackParametersFromGetterRef.set(controller.getPlaybackParameters());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackParametersFromCallbackRef.get()).isEqualTo(testPlaybackParameters);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED);
    assertThat(playbackParametersFromGetterRef.get()).isEqualTo(testPlaybackParameters);
  }

  @Test
  public void setPlaybackSpeed() throws Exception {
    float testPlaybackSpeed = 2f;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackParameters(PlaybackParameters.DEFAULT)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<PlaybackParameters> playbackParametersFromCallbackRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            playbackParametersFromCallbackRef.set(playbackParameters);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicReference<PlaybackParameters> playbackParametersFromGetterRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setPlaybackSpeed(testPlaybackSpeed);
              playbackParametersFromGetterRef.set(controller.getPlaybackParameters());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackParametersFromCallbackRef.get().speed).isEqualTo(testPlaybackSpeed);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED);
    assertThat(playbackParametersFromGetterRef.get().speed).isEqualTo(testPlaybackSpeed);
  }

  @Test
  public void setPlaylistMetadata() throws Exception {
    MediaMetadata testPlaylistMetadata = new MediaMetadata.Builder().setTitle("test").build();
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaylistMetadata(MediaMetadata.EMPTY)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<MediaMetadata> playlistMetadataFromCallbackRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaylistMetadataChanged(MediaMetadata mediaMetadata) {
            playlistMetadataFromCallbackRef.set(mediaMetadata);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicReference<MediaMetadata> playlistMetadataFromGetterRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setPlaylistMetadata(testPlaylistMetadata);
              playlistMetadataFromGetterRef.set(controller.getPlaylistMetadata());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playlistMetadataFromCallbackRef.get()).isEqualTo(testPlaylistMetadata);
    assertThat(playlistMetadataFromGetterRef.get()).isEqualTo(testPlaylistMetadata);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_PLAYLIST_METADATA_CHANGED);
  }

  @Test
  public void setVolume() throws Exception {
    float testVolume = 0.5f;
    Bundle playerConfig = new RemoteMediaSession.MockPlayerConfigBuilder().setVolume(0).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Float> volumeFromCallbackRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onVolumeChanged(float volume) {
            volumeFromCallbackRef.set(volume);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicReference<Float> volumeFromGetterRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setVolume(testVolume);
              volumeFromGetterRef.set(controller.getVolume());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(volumeFromCallbackRef.get()).isEqualTo(testVolume);
    assertThat(volumeFromGetterRef.get()).isEqualTo(testVolume);
    assertThat(getEventsAsList(onEventsRef.get())).containsExactly(Player.EVENT_VOLUME_CHANGED);
  }

  @Test
  public void setDeviceVolume() throws Exception {
    int testDeviceVolume = 2;
    int volumeFlags = 0;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceVolume(0).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger deviceVolumeFromCallbackRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeFromCallbackRef.set(volume);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger deviceVolumeFromGetterRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setDeviceVolume(testDeviceVolume, volumeFlags);
              deviceVolumeFromGetterRef.set(controller.getDeviceVolume());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceVolumeFromCallbackRef.get()).isEqualTo(testDeviceVolume);
    assertThat(deviceVolumeFromGetterRef.get()).isEqualTo(testDeviceVolume);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_DEVICE_VOLUME_CHANGED);
  }

  @Test
  public void increaseDeviceVolume() throws Exception {
    int testDeviceVolume = 2;
    int volumeFlags = 0;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceVolume(1)
            .setDeviceInfo(
                new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_LOCAL).setMaxVolume(2).build())
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger deviceVolumeFromCallbackRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeFromCallbackRef.set(volume);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger deviceVolumeFromGetterRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.increaseDeviceVolume(volumeFlags);
              deviceVolumeFromGetterRef.set(controller.getDeviceVolume());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceVolumeFromCallbackRef.get()).isEqualTo(testDeviceVolume);
    assertThat(deviceVolumeFromGetterRef.get()).isEqualTo(testDeviceVolume);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_DEVICE_VOLUME_CHANGED);
  }

  @Test
  public void decreaseDeviceVolume() throws Exception {
    int testDeviceVolume = 2;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceVolume(3).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger deviceVolumeFromCallbackRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeFromCallbackRef.set(volume);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    int volumeFlags = 0;
    AtomicInteger deviceVolumeFromGetterRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.decreaseDeviceVolume(volumeFlags);
              deviceVolumeFromGetterRef.set(controller.getDeviceVolume());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceVolumeFromCallbackRef.get()).isEqualTo(testDeviceVolume);
    assertThat(deviceVolumeFromGetterRef.get()).isEqualTo(testDeviceVolume);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_DEVICE_VOLUME_CHANGED);
  }

  @Test
  public void setDeviceMuted() throws Exception {
    boolean testDeviceMuted = true;
    int volumeFlags = C.VOLUME_FLAG_VIBRATE;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceMuted(false).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicBoolean deviceMutedFromCallbackRef = new AtomicBoolean();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceMutedFromCallbackRef.set(muted);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicBoolean deviceMutedFromGetterRef = new AtomicBoolean();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setDeviceMuted(testDeviceMuted, volumeFlags);
              deviceMutedFromGetterRef.set(controller.isDeviceMuted());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceMutedFromCallbackRef.get()).isEqualTo(testDeviceMuted);
    assertThat(deviceMutedFromGetterRef.get()).isEqualTo(testDeviceMuted);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_DEVICE_VOLUME_CHANGED);
  }

  @Test
  public void setAudioAttributes() throws Exception {
    AudioAttributes originalAttrs =
        new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build();
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setAudioAttributes(originalAttrs).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<AudioAttributes> audioAttributesFromCallbackRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onAudioAttributesChanged(AudioAttributes audioAttributes) {
            audioAttributesFromCallbackRef.set(originalAttrs);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicReference<AudioAttributes> audioAttributesFromGetterRef = new AtomicReference<>();
    AudioAttributes newAttributes =
        new AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setAudioAttributes(newAttributes, /* handleAudioFocus= */ false);
              audioAttributesFromGetterRef.set(controller.getAudioAttributes());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(audioAttributesFromCallbackRef.get()).isEqualTo(originalAttrs);
    assertThat(audioAttributesFromGetterRef.get()).isEqualTo(newAttributes);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_AUDIO_ATTRIBUTES_CHANGED);
  }

  @Test
  public void prepare() throws Exception {
    int testPlaybackState = Player.STATE_ENDED;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackState(Player.STATE_IDLE)
            .setPlayerError(
                new PlaybackException(
                    /* message= */ "test",
                    /* cause= */ null,
                    PlaybackException.ERROR_CODE_REMOTE_ERROR))
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicInteger playbackStateFromCallbackRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    AtomicReference<PlaybackException> playerErrorFromCallbackRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
            playbackStateFromCallbackRef.set(playbackState);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }

          @Override
          public void onPlayerErrorChanged(@Nullable PlaybackException error) {
            playerErrorFromCallbackRef.set(error);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger playbackStateFromGetterRef = new AtomicInteger();
    AtomicReference<PlaybackException> playerErrorFromGetterRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.prepare();
              playbackStateFromGetterRef.set(controller.getPlaybackState());
              playerErrorFromGetterRef.set(controller.getPlayerError());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateFromCallbackRef.get()).isEqualTo(testPlaybackState);
    assertThat(playbackStateFromGetterRef.get()).isEqualTo(testPlaybackState);
    assertThat(playerErrorFromGetterRef.get()).isNull();
    assertThat(playerErrorFromCallbackRef.get()).isNull();
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAYER_ERROR);
  }

  @Test
  public void setTrackSelectionParameters() throws Exception {
    Context context = ApplicationProvider.getApplicationContext();
    remoteSession.setPlayer(new RemoteMediaSession.MockPlayerConfigBuilder().build());
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<TrackSelectionParameters> trackSelectionParametersCallbackRef =
        new AtomicReference<>();
    AtomicReference<TrackSelectionParameters> trackSelectionParametersGetterRef =
        new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTrackSelectionParametersChanged(TrackSelectionParameters parameters) {
            trackSelectionParametersCallbackRef.set(parameters);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setTrackSelectionParameters(
                  new TrackSelectionParameters.Builder(context).setMaxVideoBitrate(1234).build());
              trackSelectionParametersGetterRef.set(controller.getTrackSelectionParameters());
            });

    TrackSelectionParameters expectedParameters =
        new TrackSelectionParameters.Builder(context).setMaxVideoBitrate(1234).build();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(trackSelectionParametersCallbackRef.get()).isEqualTo(expectedParameters);
    assertThat(trackSelectionParametersGetterRef.get()).isEqualTo(expectedParameters);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED);
  }

  @Test
  public void seekToNextMediaItem() throws Exception {
    int initialMediaItemIndex = 1;
    String firstMediaId = "firstMediaId";
    String secondMediaId = "secondMediaId";
    String thirdMediaId = "thirdMediaId";
    String testCurrentMediaId = thirdMediaId;
    int testMediaItemIndex = 2;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(
                MediaTestUtils.createTimeline(
                    MediaTestUtils.createMediaItems(firstMediaId, secondMediaId, thirdMediaId)))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<MediaItem> newMediaItemRef = new AtomicReference<>();
    AtomicReference<PositionInfo> oldPositionInfoRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            newMediaItemRef.set(mediaItem);
            latch.countDown();
          }

          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            oldPositionInfoRef.set(oldPosition);
            newPositionInfoRef.set(newPosition);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekToNextMediaItem();
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newMediaItemRef.get().mediaId).isEqualTo(testCurrentMediaId);
    assertThat(oldPositionInfoRef.get().mediaItemIndex).isEqualTo(initialMediaItemIndex);
    assertThat(newPositionInfoRef.get().mediaItemIndex).isEqualTo(testMediaItemIndex);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
  }

  @Test
  public void seekToPreviousMediaItem() throws Exception {
    int initialMediaItemIndex = 1;
    String firstMediaId = "firstMediaId";
    String secondMediaId = "secondMediaId";
    String thirdMediaId = "thirdMediaId";
    String testCurrentMediaId = firstMediaId;
    int testMediaItemIndex = 0;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(
                MediaTestUtils.createTimeline(
                    MediaTestUtils.createMediaItems(firstMediaId, secondMediaId, thirdMediaId)))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<MediaItem> newMediaItemRef = new AtomicReference<>();
    AtomicReference<PositionInfo> oldPositionInfoRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            newMediaItemRef.set(mediaItem);
            latch.countDown();
          }

          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            oldPositionInfoRef.set(oldPosition);
            newPositionInfoRef.set(newPosition);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekToPreviousMediaItem();
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newMediaItemRef.get().mediaId).isEqualTo(testCurrentMediaId);
    assertThat(oldPositionInfoRef.get().mediaItemIndex).isEqualTo(initialMediaItemIndex);
    assertThat(newPositionInfoRef.get().mediaItemIndex).isEqualTo(testMediaItemIndex);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
  }

  @Test
  public void seekTo_forwardsInSamePeriod() throws Exception {
    long initialPosition = 8_000;
    long initialBufferedPosition = 9_200;
    long initialTotalBufferedDuration = 1_200;
    int testMediaItemIndex = 0;
    long testPosition = 9_000;
    long testBufferedPosition = initialBufferedPosition;
    long testTotalBufferedDuration = 200;
    Timeline testTimeline = createTimeline(1);
    MediaItem testCurrentMediaItem =
        testTimeline.getWindow(testMediaItemIndex, new Window()).mediaItem;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(testTimeline)
            .setCurrentMediaItemIndex(testMediaItemIndex)
            .setCurrentPeriodIndex(testMediaItemIndex)
            .setCurrentPosition(initialPosition)
            .setContentPosition(initialPosition)
            .setBufferedPosition(initialBufferedPosition)
            .setTotalBufferedDuration(initialTotalBufferedDuration)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            newPositionInfoRef.set(newPosition);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicLong currentPositionRef = new AtomicLong();
    AtomicLong bufferedPositionRef = new AtomicLong();
    AtomicLong totalBufferedDurationRef = new AtomicLong();
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(testPosition);
              currentPositionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
              totalBufferedDurationRef.set(controller.getTotalBufferedDuration());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_POSITION_DISCONTINUITY);
    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
    assertThat(totalBufferedDurationRef.get()).isEqualTo(testTotalBufferedDuration);
    assertThat(currentMediaItemRef.get()).isEqualTo(testCurrentMediaItem);
  }

  @Test
  public void seekTo_forwardsInSamePeriod_beyondBufferedData() throws Exception {
    long initialPosition = 8_000;
    long initialBufferedPosition = 9_200;
    long initialTotalBufferedDuration = 1_200;
    int testMediaItemIndex = 0;
    long testPosition = 9_200;
    long testBufferedPosition = initialBufferedPosition;
    long testTotalBufferedDuration = 0;
    Timeline testTimeline = createTimeline(3);
    MediaItem testCurrentMediaItem =
        testTimeline.getWindow(testMediaItemIndex, new Window()).mediaItem;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(testTimeline)
            .setCurrentMediaItemIndex(testMediaItemIndex)
            .setCurrentPeriodIndex(testMediaItemIndex)
            .setCurrentPosition(initialPosition)
            .setContentPosition(initialPosition)
            .setBufferedPosition(initialBufferedPosition)
            .setTotalBufferedDuration(initialTotalBufferedDuration)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            newPositionInfoRef.set(newPosition);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicLong currentPositionRef = new AtomicLong();
    AtomicLong bufferedPositionRef = new AtomicLong();
    AtomicLong totalBufferedDurationRef = new AtomicLong();
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(testPosition);
              currentPositionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
              totalBufferedDurationRef.set(controller.getTotalBufferedDuration());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_POSITION_DISCONTINUITY);
    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
    assertThat(totalBufferedDurationRef.get()).isEqualTo(testTotalBufferedDuration);
    assertThat(currentMediaItemRef.get()).isEqualTo(testCurrentMediaItem);
  }

  @Test
  public void seekTo_backwardsInSamePeriod() throws Exception {
    long initialPosition = 8_000;
    long initialBufferedPosition = 9_200;
    long initialTotalBufferedDuration = 1_200;
    int testMediaItemIndex = 0;
    long testPosition = 1_000;
    long testBufferedPosition = 1_000;
    long testTotalBufferedDuration = 0;
    Timeline testTimeline = createTimeline(1);
    MediaItem testCurrentMediaItem =
        testTimeline.getWindow(testMediaItemIndex, new Window()).mediaItem;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(testTimeline)
            .setCurrentMediaItemIndex(testMediaItemIndex)
            .setCurrentPeriodIndex(testMediaItemIndex)
            .setCurrentPosition(initialPosition)
            .setContentPosition(initialPosition)
            .setBufferedPosition(initialBufferedPosition)
            .setTotalBufferedDuration(initialTotalBufferedDuration)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            newPositionInfoRef.set(newPosition);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicLong currentPositionRef = new AtomicLong();
    AtomicLong bufferedPositionRef = new AtomicLong();
    AtomicLong totalBufferedDurationRef = new AtomicLong();
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(testPosition);
              currentPositionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
              totalBufferedDurationRef.set(controller.getTotalBufferedDuration());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_POSITION_DISCONTINUITY);
    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
    assertThat(totalBufferedDurationRef.get()).isEqualTo(testTotalBufferedDuration);
    assertThat(currentMediaItemRef.get()).isEqualTo(testCurrentMediaItem);
  }

  @Test
  public void seekTo_toDifferentPeriodInSameWindow() throws Exception {
    long defaultPeriodDuration = 10_000;
    int initialMediaItemIndex = 0;
    int initialPeriodIndex = 0;
    long initialPosition = 8_000;
    long initialBufferedPosition = 9_200;
    long initialTotalBufferedDuration = 1_200;
    int testPeriodIndex = 1;
    long testPosition = 16_000;
    long testBufferedPosition = 16_000;
    long testTotalBufferedDuration = 0;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(
                MediaTestUtils.createTimelineWithPeriodSizes(new int[] {3}, defaultPeriodDuration))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialPeriodIndex)
            .setCurrentPosition(initialPosition)
            .setContentPosition(initialPosition)
            .setBufferedPosition(initialBufferedPosition)
            .setTotalBufferedDuration(initialTotalBufferedDuration)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            newPositionInfoRef.set(newPosition);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    AtomicLong currentPositionRef = new AtomicLong();
    AtomicLong bufferedPositionRef = new AtomicLong();
    AtomicLong totalBufferedDurationRef = new AtomicLong();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(testPosition);
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
              currentPositionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
              totalBufferedDurationRef.set(controller.getTotalBufferedDuration());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_POSITION_DISCONTINUITY);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testPeriodIndex);
    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
    assertThat(totalBufferedDurationRef.get()).isEqualTo(testTotalBufferedDuration);
  }

  @Test
  public void seekTo_toDifferentPeriodInSameWindow_doesNotCallOnMediaItemTransition()
      throws Exception {
    long defaultPeriodDuration = 10_000;
    int initialMediaItemIndex = 0;
    int initialPeriodIndex = 0;
    long initialPosition = 8_000;
    long testPosition = 16_000;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(
                MediaTestUtils.createTimelineWithPeriodSizes(new int[] {3}, defaultPeriodDuration))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialPeriodIndex)
            .setCurrentPosition(initialPosition)
            .setContentPosition(initialPosition)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    threadTestRule.getHandler().postAndSync(() -> controller.seekTo(testPosition));

    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
  }

  @Test
  public void seekTo_toDifferentPeriodInDifferentWindow() throws Exception {
    int initialMediaItemIndex = 2;
    long initialPosition = 8_000;
    long initialBufferedPosition = 9_200;
    long initialTotalBufferedDuration = 1_200;
    int testMediaItemIndex = 0;
    int testPeriodIndex = 0;
    long testPosition = 1_000;
    long testBufferedPosition = 1_000;
    long testTotalBufferedDuration = 0;
    Timeline testTimeline = createTimeline(3);
    MediaItem testCurrentMediaItem =
        testTimeline.getWindow(testMediaItemIndex, new Window()).mediaItem;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(testTimeline)
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .setCurrentPosition(initialPosition)
            .setContentPosition(initialPosition)
            .setBufferedPosition(initialBufferedPosition)
            .setTotalBufferedDuration(initialTotalBufferedDuration)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            newPositionInfoRef.set(newPosition);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    AtomicLong currentPositionRef = new AtomicLong();
    AtomicLong bufferedPositionRef = new AtomicLong();
    AtomicLong totalBufferedDurationRef = new AtomicLong();
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(testMediaItemIndex, testPosition);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
              currentPositionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
              totalBufferedDurationRef.set(controller.getTotalBufferedDuration());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertThat(getEventsAsList(onEventsRef.get())).contains(Player.EVENT_POSITION_DISCONTINUITY);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testPeriodIndex);
    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
    assertThat(totalBufferedDurationRef.get()).isEqualTo(testTotalBufferedDuration);
    assertThat(currentMediaItemRef.get()).isEqualTo(testCurrentMediaItem);
  }

  @Test
  public void seekTo_whilePlayingAd_ignored() throws Exception {
    int initialMediaItemIndex = 0;
    int initialPosition = 0;
    int seekPosition = 3_000;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPosition(initialPosition)
            .setContentPosition(initialPosition)
            .setIsPlayingAd(/* isPlayingAd= */ true)
            .setCurrentAdGroupIndex(0)
            .setCurrentAdIndexInAdGroup(0)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicLong currentPositionRef = new AtomicLong();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(seekPosition);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPositionRef.set(controller.getCurrentPosition());
            });

    assertThat(currentMediaItemIndexRef.get()).isEqualTo(initialMediaItemIndex);
    assertThat(currentPositionRef.get()).isEqualTo(initialPosition);
  }

  @Test
  public void seekTo_samePosition_ignored() throws Exception {
    int initialMediaItemIndex = 0;
    int initialPosition = 3_000;
    int seekPosition = 3_000;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPosition(initialPosition)
            .setContentPosition(initialPosition)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicLong currentPositionRef = new AtomicLong();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(seekPosition);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPositionRef.set(controller.getCurrentPosition());
            });

    assertThat(currentMediaItemIndexRef.get()).isEqualTo(initialMediaItemIndex);
    assertThat(currentPositionRef.get()).isEqualTo(initialPosition);
  }

  @Test
  public void seekTo_withEmptyTimeline() throws Exception {
    int initialMediaItemIndex = 0;
    int initialPeriodIndex = 0;
    int initialPosition = 0;
    int initialBufferedPosition = 0;
    int initialTotalBufferedPosition = 0;
    int testMediaItemIndex = 3;
    int testPeriodIndex = 3;
    long testSeekPositionMs = 3_000;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialPeriodIndex)
            .setCurrentPosition(initialPosition)
            .setContentPosition(initialPosition)
            .setBufferedPosition(initialBufferedPosition)
            .setTotalBufferedDuration(initialTotalBufferedPosition)
            .setTimeline(Timeline.EMPTY)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            newPositionInfoRef.set(newPosition);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    AtomicLong currentPositionRef = new AtomicLong();
    AtomicLong bufferedPositionRef = new AtomicLong();
    AtomicLong totalBufferedDurationRef = new AtomicLong();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(testMediaItemIndex, testSeekPositionMs);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
              currentPositionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
              totalBufferedDurationRef.set(controller.getTotalBufferedDuration());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testSeekPositionMs);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_POSITION_DISCONTINUITY);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testPeriodIndex);
    assertThat(currentPositionRef.get()).isEqualTo(testSeekPositionMs);
    assertThat(bufferedPositionRef.get()).isEqualTo(testSeekPositionMs);
    assertThat(totalBufferedDurationRef.get()).isEqualTo(initialBufferedPosition);
  }

  @Test
  public void seekTo_withEmptyTimeline_doesNotCallOnMediaItemTransition() throws Exception {
    long testPosition = 16_000;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setTimeline(Timeline.EMPTY).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    threadTestRule.getHandler().postAndSync(() -> controller.seekTo(testPosition));

    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
  }

  @Test
  public void seekBack_seeksToOffsetBySeekBackIncrement() throws Exception {
    long testCurrentPosition = 10_000;
    long testSeekBackIncrement = 2_000;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setCurrentPosition(testCurrentPosition)
            .setSeekBackIncrement(testSeekBackIncrement)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicLong oldPositionRef = new AtomicLong();
    AtomicLong newPositionRef = new AtomicLong();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            oldPositionRef.set(oldPosition.positionMs);
            newPositionRef.set(newPosition.positionMs);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    threadTestRule.getHandler().postAndSync(controller::seekBack);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(oldPositionRef.get()).isEqualTo(testCurrentPosition);
    assertThat(newPositionRef.get()).isEqualTo(testCurrentPosition - testSeekBackIncrement);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_POSITION_DISCONTINUITY);
  }

  @Test
  public void seekForward_seeksToOffsetBySeekForwardIncrement() throws Exception {
    long testCurrentPosition = 10_000;
    long testSeekForwardIncrement = 2_000;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setCurrentPosition(testCurrentPosition)
            .setSeekForwardIncrement(testSeekForwardIncrement)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicLong oldPositionRef = new AtomicLong();
    AtomicLong newPositionRef = new AtomicLong();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            oldPositionRef.set(oldPosition.positionMs);
            newPositionRef.set(newPosition.positionMs);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    threadTestRule.getHandler().postAndSync(controller::seekForward);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(oldPositionRef.get()).isEqualTo(testCurrentPosition);
    assertThat(newPositionRef.get()).isEqualTo(testCurrentPosition + testSeekForwardIncrement);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_POSITION_DISCONTINUITY);
  }

  @Test
  public void setMediaItems_withResetPosition() throws Exception {
    int initialMediaItemIndex = 2;
    long initialPosition = 8_000;
    long initialBufferedPosition = 9_200;
    long initialTotalBufferedDuration = 1_200;
    int testMediaItemCount = 2;
    int testMediaItemIndex = 0;
    int testPeriodIndex = 0;
    long testPosition = 0;
    long testBufferedPosition = 0;
    long testTotalBufferedDuration = 0;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(MediaTestUtils.createTimeline(3))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .setCurrentPosition(initialPosition)
            .setContentPosition(initialPosition)
            .setBufferedPosition(initialBufferedPosition)
            .setTotalBufferedDuration(initialTotalBufferedDuration)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            newPositionInfoRef.set(newPosition);
            latch.countDown();
          }

          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicLong currentPositionRef = new AtomicLong();
    AtomicLong bufferedPositionRef = new AtomicLong();
    AtomicLong totalBufferedDurationRef = new AtomicLong();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setMediaItems(
                  createMediaItems(testMediaItemCount), /* resetPosition= */ true);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPositionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
              totalBufferedDurationRef.set(controller.getTotalBufferedDuration());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertTimeline(
        newTimelineRef.get(),
        testMediaItemCount,
        testMediaItemIndex,
        /* testFirstPeriodIndex= */ testPeriodIndex,
        /* testLastPeriodIndex= */ testPeriodIndex);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_POSITION_DISCONTINUITY);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testPeriodIndex);
    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
    assertThat(totalBufferedDurationRef.get()).isEqualTo(testTotalBufferedDuration);
  }

  @Test
  public void setMediaItems_toEmptyListAndResetPositionFalse_correctMasking() throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setCurrentMediaItemIndex(2)
            .setCurrentPeriodIndex(2)
            .setCurrentPosition(8000)
            .setContentPosition(8000)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());

    AtomicReference<Timeline> currentTimelineRef = new AtomicReference<>();
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicLong currentPositionRef = new AtomicLong();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setMediaItems(ImmutableList.of(), /* resetPosition= */ false);
              currentTimelineRef.set(controller.getCurrentTimeline());
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPositionRef.set(controller.getCurrentPosition());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
            });

    assertThat(currentPositionRef.get()).isEqualTo(8000);
    assertThat(currentTimelineRef.get().isEmpty()).isTrue();
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(2);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(2);
  }

  @Test
  public void setMediaItems_withStartMediaItemIndexAndStartPosition() throws Exception {
    int initialMediaItemIndex = 2;
    long initialPosition = 8_000;
    long initialBufferedPosition = 9_200;
    long initialTotalBufferedDuration = 1_200;
    String dummyMediaId = "dummyMediaId";
    String testMediaItemIndexMediaId = "testMediaItemIndexMediaId";
    int testMediaItemCount = 2;
    int testMediaItemIndex = 1;
    int testPeriodIndex = 1;
    long testPosition = 1_000;
    long testBufferedPosition = 1_000;
    long testTotalBufferedDuration = 0;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(MediaTestUtils.createTimeline(3))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .setCurrentPosition(initialPosition)
            .setContentPosition(initialPosition)
            .setBufferedPosition(initialBufferedPosition)
            .setTotalBufferedDuration(initialTotalBufferedDuration)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(4);
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<MediaItem> newMediaItemRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            newPositionInfoRef.set(newPosition);
            latch.countDown();
          }

          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            newMediaItemRef.set(mediaItem);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    AtomicLong currentPositionRef = new AtomicLong();
    AtomicLong bufferedPositionRef = new AtomicLong();
    AtomicLong totalBufferedDurationRef = new AtomicLong();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setMediaItems(
                  createMediaItems(dummyMediaId, testMediaItemIndexMediaId),
                  /* startIndex= */ testMediaItemIndex,
                  /* startPositionMs= */ testPosition);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
              currentPositionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
              totalBufferedDurationRef.set(controller.getTotalBufferedDuration());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertTimeline(
        newTimelineRef.get(),
        testMediaItemCount,
        testMediaItemIndex,
        /* testFirstPeriodIndex= */ testPeriodIndex,
        /* testLastPeriodIndex= */ testPeriodIndex);
    assertThat(newMediaItemRef.get().mediaId).isEqualTo(testMediaItemIndexMediaId);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testPeriodIndex);
    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
    assertThat(totalBufferedDurationRef.get()).isEqualTo(testTotalBufferedDuration);
  }

  @Test
  public void setMediaItems_withEmptyList() throws Exception {
    int initialMediaItemIndex = 2;
    long initialPosition = 8_000;
    long initialBufferedPosition = 9_200;
    long initialTotalBufferedDuration = 1_200;
    List<MediaItem> testMediaItemList = new ArrayList<>();
    int testMediaItemIndex = 1;
    int testPeriodIndex = 1;
    long testPosition = 1_000;
    long testBufferedPosition = 1_000;
    long testTotalBufferedDuration = 0;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(MediaTestUtils.createTimeline(3))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .setCurrentPosition(initialPosition)
            .setContentPosition(initialPosition)
            .setBufferedPosition(initialBufferedPosition)
            .setTotalBufferedDuration(initialTotalBufferedDuration)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(4);
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<MediaItem> newMediaItemRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            newPositionInfoRef.set(newPosition);
            latch.countDown();
          }

          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            newMediaItemRef.set(mediaItem);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    AtomicLong currentPositionRef = new AtomicLong();
    AtomicLong bufferedPositionRef = new AtomicLong();
    AtomicLong totalBufferedDurationRef = new AtomicLong();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setMediaItems(
                  testMediaItemList,
                  /* startIndex= */ testMediaItemIndex,
                  /* startPositionMs= */ testPosition);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
              currentPositionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
              totalBufferedDurationRef.set(controller.getTotalBufferedDuration());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertThat(newTimelineRef.get().isEmpty()).isTrue();
    assertThat(newMediaItemRef.get()).isNull();
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testPeriodIndex);
    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
    assertThat(totalBufferedDurationRef.get()).isEqualTo(testTotalBufferedDuration);
  }

  @Test
  public void setMediaItems_withEmptyListWhenTimelineIsEmpty_doesNotCallOnMediaItemTransition()
      throws Exception {
    List<MediaItem> testEmptyList = new ArrayList<>();

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setTimeline(Timeline.EMPTY).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    threadTestRule.getHandler().postAndSync(() -> controller.setMediaItems(testEmptyList));

    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
  }

  @Test
  public void addMediaItems_withIdleStateAndEmptyTimeline() throws Exception {
    int testMediaItemCount = 2;
    int testCurrentMediaItemIndex = 1;
    int testNextMediaItemIndex = C.INDEX_UNSET;
    int testPreviousMediaItemIndex = 0;
    int testCurrentPeriodIndex = 1;
    List<MediaItem> testMediaItems = createMediaItems(testMediaItemCount);
    MediaItem testMediaItem = testMediaItems.get(testCurrentPeriodIndex);

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackState(Player.STATE_IDLE)
            .setCurrentMediaItemIndex(1)
            .setCurrentPeriodIndex(1)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    AtomicReference<MediaItem> newMediaItemRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            newMediaItemRef.set(mediaItem);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger nextMediaItemIndexRef = new AtomicInteger();
    AtomicInteger previousMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    AtomicInteger playbackStateRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.addMediaItems(testMediaItems);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              nextMediaItemIndexRef.set(controller.getNextMediaItemIndex());
              previousMediaItemIndexRef.set(controller.getPreviousMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
              playbackStateRef.set(controller.getPlaybackState());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimeline(
        newTimelineRef.get(),
        testMediaItemCount,
        testCurrentMediaItemIndex,
        /* testFirstPeriodIndex= */ testCurrentPeriodIndex,
        /* testLastPeriodIndex= */ testCurrentPeriodIndex);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(nextMediaItemIndexRef.get()).isEqualTo(testNextMediaItemIndex);
    assertThat(previousMediaItemIndexRef.get()).isEqualTo(testPreviousMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testCurrentPeriodIndex);
    assertThat(newMediaItemRef.get()).isEqualTo(testMediaItem);
    assertThat(playbackStateRef.get()).isEqualTo(Player.STATE_IDLE);
  }

  @Test
  public void addMediaItems_withEndedStateAndEmptyTimeline() throws Exception {
    int testMediaItemCount = 2;
    int testCurrentMediaItemIndex = 1;
    int testNextMediaItemIndex = C.INDEX_UNSET;
    int testPreviousMediaItemIndex = 0;
    int testCurrentPeriodIndex = 1;
    List<MediaItem> testMediaItems = createMediaItems(testMediaItemCount);
    MediaItem testMediaItem = testMediaItems.get(testCurrentPeriodIndex);

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackState(Player.STATE_ENDED)
            .setCurrentMediaItemIndex(1)
            .setCurrentPeriodIndex(1)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    AtomicReference<MediaItem> newMediaItemRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            newMediaItemRef.set(mediaItem);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger nextMediaItemIndexRef = new AtomicInteger();
    AtomicInteger previousMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    AtomicInteger playbackStateRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.addMediaItems(testMediaItems);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              nextMediaItemIndexRef.set(controller.getNextMediaItemIndex());
              previousMediaItemIndexRef.set(controller.getPreviousMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
              playbackStateRef.set(controller.getPlaybackState());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimeline(
        newTimelineRef.get(),
        testMediaItemCount,
        testCurrentMediaItemIndex,
        /* testFirstPeriodIndex= */ testCurrentPeriodIndex,
        /* testLastPeriodIndex= */ testCurrentPeriodIndex);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_PLAYBACK_STATE_CHANGED);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(nextMediaItemIndexRef.get()).isEqualTo(testNextMediaItemIndex);
    assertThat(previousMediaItemIndexRef.get()).isEqualTo(testPreviousMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testCurrentPeriodIndex);
    assertThat(newMediaItemRef.get()).isEqualTo(testMediaItem);
    assertThat(playbackStateRef.get()).isEqualTo(Player.STATE_BUFFERING);
  }

  @Test
  public void addMediaItems_toEndOfTimeline() throws Exception {
    int initialMediaItemCount = 3;
    int initialMediaItemIndex = 2;
    int testMediaItemCount = 2;
    int testCurrentMediaItemIndex = initialMediaItemIndex;
    int testNextMediaItemIndex = testCurrentMediaItemIndex + 1;
    int testPreviousMediaItemIndex = testCurrentMediaItemIndex - 1;
    int testCurrentPeriodIndex = initialMediaItemIndex;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(MediaTestUtils.createTimeline(initialMediaItemCount))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger nextMediaItemIndexRef = new AtomicInteger();
    AtomicInteger previousMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.addMediaItems(createMediaItems(testMediaItemCount));
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              nextMediaItemIndexRef.set(controller.getNextMediaItemIndex());
              previousMediaItemIndexRef.set(controller.getPreviousMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimeline(
        newTimelineRef.get(),
        initialMediaItemCount + testMediaItemCount,
        testCurrentMediaItemIndex,
        /* testFirstPeriodIndex= */ testCurrentPeriodIndex,
        /* testLastPeriodIndex= */ testCurrentPeriodIndex);
    assertThat(getEventsAsList(onEventsRef.get())).containsExactly(Player.EVENT_TIMELINE_CHANGED);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(nextMediaItemIndexRef.get()).isEqualTo(testNextMediaItemIndex);
    assertThat(previousMediaItemIndexRef.get()).isEqualTo(testPreviousMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testCurrentPeriodIndex);
  }

  @Test
  public void addMediaItems_toTimelineWithSinglePeriodPerWindow() throws Exception {
    int initialMediaItemCount = 3;
    int initialMediaItemIndex = 1;
    int initialPeriodIndex = 1;
    int testMediaItemCount = 2;

    assertAddMediaItems(
        initialMediaItemCount,
        initialMediaItemIndex,
        initialPeriodIndex,
        /* initialTimeline= */ MediaTestUtils.createTimelineWithPeriodSizes(new int[] {1, 1, 1}),
        testMediaItemCount,
        /* testIndex= */ 1,
        /* testCurrentMediaItemIndex= */ initialMediaItemIndex + testMediaItemCount,
        /* testNextMediaItemIndex= */ initialMediaItemIndex + testMediaItemCount + 1,
        /* testPreviousMediaItemIndex= */ initialMediaItemIndex + testMediaItemCount - 1,
        /* testCurrentPeriodIndex= */ initialPeriodIndex + testMediaItemCount,
        /* testCurrentWindowFirstPeriodIndex= */ initialPeriodIndex + testMediaItemCount,
        /* testCurrentWindowLastPeriodIndex= */ initialPeriodIndex + testMediaItemCount);
  }

  @Test
  public void addMediaItems_toTimelineWithMultiplePeriodsPerWindow() throws Exception {
    int initialMediaItemCount = 3;
    int initialMediaItemIndex = 1;
    int initialWindowFirstPeriodIndex = 2;
    int initialWindowLastPeriodIndex = 4;
    int initialPeriodIndex = 3;
    int testMediaItemCount = 2;

    assertAddMediaItems(
        initialMediaItemCount,
        initialMediaItemIndex,
        initialPeriodIndex,
        /* initialTimeline= */ MediaTestUtils.createTimelineWithPeriodSizes(new int[] {2, 3, 2}),
        testMediaItemCount,
        /* testIndex= */ 1,
        /* testCurrentMediaItemIndex= */ initialMediaItemIndex + testMediaItemCount,
        /* testNextMediaItemIndex= */ initialMediaItemIndex + testMediaItemCount + 1,
        /* testPreviousMediaItemIndex= */ initialMediaItemIndex + testMediaItemCount - 1,
        /* testCurrentPeriodIndex= */ initialPeriodIndex + testMediaItemCount,
        /* testCurrentWindowFirstPeriodIndex= */ initialWindowFirstPeriodIndex + testMediaItemCount,
        /* testCurrentWindowLastPeriodIndex= */ initialWindowLastPeriodIndex + testMediaItemCount);
  }

  private void assertAddMediaItems(
      int initialMediaItemCount,
      int initialMediaItemIndex,
      int initialPeriodIndex,
      Timeline initialTimeline,
      int testMediaItemCount,
      int testIndex,
      int testCurrentMediaItemIndex,
      int testNextMediaItemIndex,
      int testPreviousMediaItemIndex,
      int testCurrentPeriodIndex,
      int testCurrentWindowFirstPeriodIndex,
      int testCurrentWindowLastPeriodIndex)
      throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(initialTimeline)
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialPeriodIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger nextMediaItemIndexRef = new AtomicInteger();
    AtomicInteger previousMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.addMediaItems(
                  /* index= */ testIndex, createMediaItems(testMediaItemCount));
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              nextMediaItemIndexRef.set(controller.getNextMediaItemIndex());
              previousMediaItemIndexRef.set(controller.getPreviousMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimeline(
        newTimelineRef.get(),
        initialMediaItemCount + testMediaItemCount,
        testCurrentMediaItemIndex,
        /* testFirstPeriodIndex= */ testCurrentWindowFirstPeriodIndex,
        /* testLastPeriodIndex= */ testCurrentWindowLastPeriodIndex);
    assertThat(getEventsAsList(onEventsRef.get())).containsExactly(Player.EVENT_TIMELINE_CHANGED);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(nextMediaItemIndexRef.get()).isEqualTo(testNextMediaItemIndex);
    assertThat(previousMediaItemIndexRef.get()).isEqualTo(testPreviousMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testCurrentPeriodIndex);
  }

  @Test
  public void removeMediaItems_currentItemRemoved() throws Exception {
    int initialMediaItemIndex = 1;
    String firstMediaId = "firstMediaId";
    String secondMediaId = "secondMediaId";
    String thirdMediaId = "thirdMediaId";
    String testCurrentMediaId = thirdMediaId;
    int testFromIndex = 1;
    int testToIndex = 2;
    int testMediaItemCount = 2;
    int testCurrentMediaItemIndex = testFromIndex;
    int testNextMediaItemIndex = C.INDEX_UNSET;
    int testPreviousMediaItemIndex = testCurrentMediaItemIndex - 1;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(
                MediaTestUtils.createTimeline(
                    createMediaItems(firstMediaId, secondMediaId, thirdMediaId)))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .setCurrentPosition(2000L)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(4);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<MediaItem> newMediaItemRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            newMediaItemRef.set(mediaItem);
            latch.countDown();
          }

          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            newPositionInfoRef.set(newPosition);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger nextMediaItemIndexRef = new AtomicInteger();
    AtomicInteger previousMediaItemIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.removeMediaItems(
                  /* fromIndex= */ testFromIndex, /* toIndex= */ testToIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              nextMediaItemIndexRef.set(controller.getNextMediaItemIndex());
              previousMediaItemIndexRef.set(controller.getPreviousMediaItemIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimeline(
        newTimelineRef.get(),
        testMediaItemCount,
        testCurrentMediaItemIndex,
        /* testFirstPeriodIndex= */ testCurrentMediaItemIndex,
        /* testLastPeriodIndex= */ testCurrentMediaItemIndex);
    assertThat(newMediaItemRef.get().mediaId).isEqualTo(testCurrentMediaId);
    assertThat(newPositionInfoRef.get().mediaItemIndex).isEqualTo(testCurrentMediaItemIndex);
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(0L);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_POSITION_DISCONTINUITY);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(nextMediaItemIndexRef.get()).isEqualTo(testNextMediaItemIndex);
    assertThat(previousMediaItemIndexRef.get()).isEqualTo(testPreviousMediaItemIndex);
  }

  @Test
  public void removeMediaItems_currentItemNotRemoved() throws Exception {
    int initialMediaItemIndex = 1;
    String firstMediaId = "firstMediaId";
    String secondMediaId = "secondMediaId";
    String thirdMediaId = "thirdMediaId";
    String testCurrentMediaId = secondMediaId;
    int testFromIndex = 2;
    int testToIndex = 3;
    int testMediaItemCount = 2;
    int testCurrentMediaItemIndex = initialMediaItemIndex;
    int testNextMediaItemIndex = C.INDEX_UNSET;
    int testPreviousMediaItemIndex = testCurrentMediaItemIndex - 1;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(
                MediaTestUtils.createTimeline(
                    createMediaItems(firstMediaId, secondMediaId, thirdMediaId)))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger nextMediaItemIndexRef = new AtomicInteger();
    AtomicInteger previousMediaItemIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.removeMediaItems(
                  /* fromIndex= */ testFromIndex, /* toIndex= */ testToIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              nextMediaItemIndexRef.set(controller.getNextMediaItemIndex());
              previousMediaItemIndexRef.set(controller.getPreviousMediaItemIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimeline(
        newTimelineRef.get(),
        testMediaItemCount,
        testCurrentMediaItemIndex,
        /* testFirstPeriodIndex= */ testCurrentMediaItemIndex,
        /* testLastPeriodIndex= */ testCurrentMediaItemIndex);
    Window window = new Window();
    assertThat(newTimelineRef.get().getWindow(testCurrentMediaItemIndex, window).mediaItem.mediaId)
        .isEqualTo(testCurrentMediaId);
    assertThat(getEventsAsList(onEventsRef.get())).containsExactly(Player.EVENT_TIMELINE_CHANGED);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(nextMediaItemIndexRef.get()).isEqualTo(testNextMediaItemIndex);
    assertThat(previousMediaItemIndexRef.get()).isEqualTo(testPreviousMediaItemIndex);
  }

  @Test
  public void removeMediaItems_removePreviousItemWithMultiplePeriods() throws Exception {
    int initialMediaItemIndex = 1;
    int initialWindowFirstPeriodIndex = 2;
    int initialWindowLastPeriodIndex = 4;
    int initialPeriodIndex = 3;
    int testFromIndex = 0;
    int testToIndex = 1;
    int testMediaItemCount = 2;
    int prevWindowPeriodSize = 2;
    int testCurrentMediaItemIndex = 0;
    int testCurrentPeriodIndex = initialPeriodIndex - prevWindowPeriodSize;
    int testCurrentWindowFirstPeriodIndex = initialWindowFirstPeriodIndex - prevWindowPeriodSize;
    int testCurrentWindowLastPeriodIndex = initialWindowLastPeriodIndex - prevWindowPeriodSize;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(
                MediaTestUtils.createTimelineWithPeriodSizes(
                    new int[] {prevWindowPeriodSize, 3, 2}))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialPeriodIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.removeMediaItems(
                  /* fromIndex= */ testFromIndex, /* toIndex= */ testToIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimeline(
        newTimelineRef.get(),
        testMediaItemCount,
        testCurrentMediaItemIndex,
        /* testFirstPeriodIndex= */ testCurrentWindowFirstPeriodIndex,
        /* testLastPeriodIndex= */ testCurrentWindowLastPeriodIndex);
    assertThat(getEventsAsList(onEventsRef.get())).containsExactly(Player.EVENT_TIMELINE_CHANGED);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testCurrentPeriodIndex);
  }

  @Test
  public void removeMediaItems_removeAllItems() throws Exception {
    int initialMediaItemIndex = 1;
    int initialPlaybackState = Player.STATE_READY;
    long initialCurrentPosition = 3_000;
    String firstMediaId = "firstMediaId";
    String secondMediaId = "secondMediaId";
    String thirdMediaId = "thirdMediaId";
    int testFromIndex = 0;
    int testToIndex = 3;
    int testMediaItemCount = 0;
    int testCurrentMediaItemIndex = 0;
    int testNextMediaItemIndex = C.INDEX_UNSET;
    int testPreviousMediaItemIndex = C.INDEX_UNSET;
    int testPlaybackState = Player.STATE_ENDED;
    long testCurrentPosition = 0;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(
                MediaTestUtils.createTimeline(
                    createMediaItems(firstMediaId, secondMediaId, thirdMediaId)))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .setCurrentPosition(initialCurrentPosition)
            .setPlaybackState(initialPlaybackState)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(4);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<MediaItem> newMediaItemRef = new AtomicReference<>();
    AtomicInteger newPlaybackStateRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            newMediaItemRef.set(mediaItem);
            latch.countDown();
          }

          @Override
          public void onPlaybackStateChanged(int playbackState) {
            newPlaybackStateRef.set(playbackState);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger nextMediaItemIndexRef = new AtomicInteger();
    AtomicInteger previousMediaItemIndexRef = new AtomicInteger();
    AtomicLong newCurrentPositionRef = new AtomicLong();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.removeMediaItems(
                  /* fromIndex= */ testFromIndex, /* toIndex= */ testToIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              nextMediaItemIndexRef.set(controller.getNextMediaItemIndex());
              previousMediaItemIndexRef.set(controller.getPreviousMediaItemIndex());
              newCurrentPositionRef.set(controller.getCurrentPosition());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimeline(
        newTimelineRef.get(),
        testMediaItemCount,
        /* ignored */ C.INDEX_UNSET,
        /* ignored */ C.INDEX_UNSET,
        /* ignored */ C.INDEX_UNSET);
    assertThat(newMediaItemRef.get()).isNull();
    assertThat(newPlaybackStateRef.get()).isEqualTo(testPlaybackState);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_POSITION_DISCONTINUITY);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(nextMediaItemIndexRef.get()).isEqualTo(testNextMediaItemIndex);
    assertThat(previousMediaItemIndexRef.get()).isEqualTo(testPreviousMediaItemIndex);
    assertThat(newCurrentPositionRef.get()).isEqualTo(testCurrentPosition);
  }

  @Test
  public void removeMediaItems_removedTailIncludesCurrentItem_callsOnPlaybackStateChanged()
      throws Exception {
    int initialMediaItemIndex = 1;
    int initialPlaybackState = Player.STATE_READY;
    int testFromIndex = 1;
    int testToIndex = 3;
    int testPlaybackState = Player.STATE_ENDED;
    Timeline testTimeline = createTimeline(createMediaItems(/* size= */ 3));

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(testTimeline)
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .setPlaybackState(initialPlaybackState)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger newPlaybackStateRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
            newPlaybackStateRef.set(playbackState);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.removeMediaItems(
                  /* fromIndex= */ testFromIndex, /* toIndex= */ testToIndex);
            });
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPlaybackStateRef.get()).isEqualTo(testPlaybackState);
    assertThat(getEventsAsList(onEventsRef.get())).contains(Player.EVENT_PLAYBACK_STATE_CHANGED);
  }

  @Test
  public void
      removeMediaItems_removeCurrentItemWhenShuffledModeDisabledFromMiddleToEnd_masksIndices()
          throws Exception {
    String firstMediaId = "firstMediaId";
    String secondMediaId = "secondMediaId";
    String thirdMediaId = "thirdMediaId";
    String fourthMediaId = "fourthMediaId";
    Timeline testTimeline =
        createTimeline(createMediaItems(firstMediaId, secondMediaId, thirdMediaId, fourthMediaId));

    // Remove from middle to end of the timeline.
    assertRemoveMediaItems(
        /* shuffleModeEnabled= */ false,
        /* initialMediaItemIndex= */ 1,
        /* testFromIndex= */ 1,
        /* testToIndex= */ 4,
        /* testCurrentMediaItemIndex= */ 0,
        /* testCurrentPeriodIndex= */ 0,
        /* testTimeline= */ testTimeline,
        /* testMediaId= */ firstMediaId);
  }

  @Test
  public void removeMediaItems_removeCurrentItemWhenShuffledModeDisabledFromTheMiddle_masksIndices()
      throws Exception {
    String firstMediaId = "firstMediaId";
    String secondMediaId = "secondMediaId";
    String thirdMediaId = "thirdMediaId";
    String fourthMediaId = "fourthMediaId";
    Timeline testTimeline =
        createTimeline(createMediaItems(firstMediaId, secondMediaId, thirdMediaId, fourthMediaId));

    // Remove middle of the timeline.
    assertRemoveMediaItems(
        /* shuffleModeEnabled= */ false,
        /* initialMediaItemIndex= */ 2,
        /* testFromIndex= */ 1,
        /* testToIndex= */ 3,
        /* testCurrentMediaItemIndex= */ 1,
        /* testCurrentPeriodIndex= */ 1,
        /* testTimeline= */ testTimeline,
        /* testMediaId= */ fourthMediaId);
  }

  @Test
  public void removeMediaItems_removeCurrentItemWhenShuffledModeEnabledWithNoNextItem_masksIndices()
      throws Exception {
    String firstMediaId = "firstMediaId";
    String secondMediaId = "secondMediaId";
    String thirdMediaId = "thirdMediaId";
    String fourthMediaId = "fourthMediaId";

    // No subsequent window index exists in the shuffled list--should default to first position in
    // timeline.
    assertRemoveMediaItems(
        /* shuffleModeEnabled= */ true,
        /* initialMediaItemIndex= */ 0,
        /* testFromIndex= */ 0,
        /* testToIndex= */ 1,
        /* testCurrentMediaItemIndex= */ 0,
        /* testCurrentPeriodIndex= */ 0,
        /* testTimeline= */ new PlaylistTimeline(
            createMediaItems(firstMediaId, secondMediaId, thirdMediaId, fourthMediaId),
            /* shuffledIndices= */ new int[] {1, 2, 3, 0}),
        /* testMediaId= */ secondMediaId);
  }

  @Test
  public void
      removeMediaItems_removeCurrentItemWhenShuffledModeEnabledWithNextItemAndChangedIndex_masksIndices()
          throws Exception {
    String firstMediaId = "firstMediaId";
    String secondMediaId = "secondMediaId";
    String thirdMediaId = "thirdMediaId";
    String fourthMediaId = "fourthMediaId";

    // Subsequent window index exists in the shuffled list after the fromIndex--current window index
    // should subtract size of removed items.
    assertRemoveMediaItems(
        /* shuffleModeEnabled= */ true,
        /* initialMediaItemIndex= */ 0,
        /* testFromIndex= */ 0,
        /* testToIndex= */ 1,
        /* testCurrentMediaItemIndex= */ 2,
        /* testCurrentPeriodIndex= */ 2,
        /* testTimeline= */ new PlaylistTimeline(
            createMediaItems(firstMediaId, secondMediaId, thirdMediaId, fourthMediaId),
            /* shuffledIndices= */ new int[] {0, 3, 1, 2}),
        /* testMediaId= */ fourthMediaId);
  }

  @Test
  public void
      removeMediaItems_removeCurrentItemWhenShuffledModeEnabledWithNextItemAndSameIndex_masksIndices()
          throws Exception {
    String firstMediaId = "firstMediaId";
    String secondMediaId = "secondMediaId";
    String thirdMediaId = "thirdMediaId";
    String fourthMediaId = "fourthMediaId";

    // Subsequent window index exists in the shuffled list before the fromIndex--current window
    // index should not subtract size of removed items.
    assertRemoveMediaItems(
        /* shuffleModeEnabled= */ true,
        /* initialMediaItemIndex= */ 3,
        /* testFromIndex= */ 3,
        /* testToIndex= */ 4,
        /* testCurrentMediaItemIndex= */ 2,
        /* testCurrentPeriodIndex= */ 2,
        /* testTimeline= */ new PlaylistTimeline(
            createMediaItems(firstMediaId, secondMediaId, thirdMediaId, fourthMediaId),
            /* shuffledIndices= */ new int[] {0, 1, 3, 2}),
        /* testMediaId= */ thirdMediaId);
  }

  private void assertRemoveMediaItems(
      boolean shuffleModeEnabled,
      int initialMediaItemIndex,
      int testFromIndex,
      int testToIndex,
      int testCurrentMediaItemIndex,
      int testCurrentPeriodIndex,
      Timeline testTimeline,
      String testMediaId)
      throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(testTimeline)
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .setShuffleModeEnabled(shuffleModeEnabled)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.removeMediaItems(
                  /* fromIndex= */ testFromIndex, /* toIndex= */ testToIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
            });
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testCurrentPeriodIndex);
    assertThat(currentMediaItemRef.get().mediaId).isEqualTo(testMediaId);
  }

  @Test
  public void moveMediaItems_moveAllMediaItems_ignored() throws Exception {
    int initialMediaItemCount = 2;
    int initialMediaItemIndex = 0;
    int testFromIndex = 0;
    int testToIndex = 2;
    int testNewIndex = 0;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(MediaTestUtils.createTimeline(initialMediaItemCount))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.moveMediaItems(testFromIndex, testToIndex, testNewIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
            });

    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
  }

  @Test
  public void moveMediaItems_callsOnTimelineChanged() throws Exception {
    int initialMediaItemCount = 3;
    String firstMediaId = "firstMediaId";
    String secondMediaId = "secondMediaId";
    String thirdMediaId = "thirdMediaId";
    int initialMediaItemIndex = 0;
    int testFromIndex = 1;
    int testToIndex = 2;
    int testNewIndex = 0;
    String testCurrentMediaId = firstMediaId;
    String testPrevMediaId = secondMediaId;
    String testNextMediaId = thirdMediaId;
    int testCurrentMediaItemIndex = 1;
    int testPrevMediaItemIndex = 0;
    int testNextMediaItemIndex = 2;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(
                MediaTestUtils.createTimeline(
                    createMediaItems(firstMediaId, secondMediaId, thirdMediaId)))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.moveMediaItems(testFromIndex, testToIndex, testNewIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimeline(
        newTimelineRef.get(),
        initialMediaItemCount,
        testCurrentMediaItemIndex,
        /* testFirstPeriodIndex= */ testCurrentMediaItemIndex,
        /* testLastPeriodIndex= */ testCurrentMediaItemIndex);
    assertThat(
            newTimelineRef
                .get()
                .getWindow(testCurrentMediaItemIndex, new Window())
                .mediaItem
                .mediaId)
        .isEqualTo(testCurrentMediaId);
    assertThat(
            newTimelineRef.get().getWindow(testPrevMediaItemIndex, new Window()).mediaItem.mediaId)
        .isEqualTo(testPrevMediaId);
    assertThat(
            newTimelineRef.get().getWindow(testNextMediaItemIndex, new Window()).mediaItem.mediaId)
        .isEqualTo(testNextMediaId);
    assertThat(getEventsAsList(onEventsRef.get())).containsExactly(Player.EVENT_TIMELINE_CHANGED);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
  }

  @Test
  public void moveMediaItems_moveCurrentItemBackOneWindow_whenPreviousWindowHasMultiplePeriods()
      throws Exception {
    int initialMediaItemIndex = 1;
    int initialPeriodIndex = 3;
    int prevWindowPeriodSize = 2;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(
                MediaTestUtils.createTimelineWithPeriodSizes(
                    new int[] {prevWindowPeriodSize, 3, 2}))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialPeriodIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    int initialWindowFirstPeriodIndex = 2;
    int initialWindowLastPeriodIndex = 4;
    assertMoveMediaItems_whenMovingBetweenWindowsWithMultiplePeriods(
        /* initialMediaItemCount= */ 3,
        /* testFromIndex= */ 1,
        /* testToIndex= */ 2,
        /* testNewIndex= */ initialMediaItemIndex - 1,
        /* testCurrentMediaItemIndex= */ initialMediaItemIndex - 1,
        /* testCurrentWindowFirstPeriodIndex= */ initialWindowFirstPeriodIndex
            - prevWindowPeriodSize,
        /* testCurrentWindowLastPeriodIndex= */ initialWindowLastPeriodIndex - prevWindowPeriodSize,
        /* testCurrentPeriodIndex= */ initialPeriodIndex - prevWindowPeriodSize);
  }

  @Test
  public void moveMediaItems_moveCurrentItemForwardOneWindow_whenNextWindowHasMultiplePeriods()
      throws Exception {
    int initialMediaItemIndex = 1;
    int initialPeriodIndex = 3;
    int nextWindowPeriodSize = 2;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(
                MediaTestUtils.createTimelineWithPeriodSizes(
                    new int[] {2, 3, nextWindowPeriodSize}))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialPeriodIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    int initialWindowFirstPeriodIndex = 2;
    int initialWindowLastPeriodIndex = 4;
    assertMoveMediaItems_whenMovingBetweenWindowsWithMultiplePeriods(
        /* initialMediaItemCount= */ 3,
        /* testFromIndex= */ 1,
        /* testToIndex= */ 2,
        /* testNewIndex= */ initialMediaItemIndex + 1,
        /* testCurrentMediaItemIndex= */ initialMediaItemIndex + 1,
        /* testCurrentWindowFirstPeriodIndex= */ initialWindowFirstPeriodIndex
            + nextWindowPeriodSize,
        /* testCurrentWindowLastPeriodIndex= */ initialWindowLastPeriodIndex + nextWindowPeriodSize,
        /* testCurrentPeriodIndex= */ initialPeriodIndex + nextWindowPeriodSize);
  }

  private void assertMoveMediaItems_whenMovingBetweenWindowsWithMultiplePeriods(
      int initialMediaItemCount,
      int testFromIndex,
      int testToIndex,
      int testNewIndex,
      int testCurrentMediaItemIndex,
      int testCurrentWindowFirstPeriodIndex,
      int testCurrentWindowLastPeriodIndex,
      int testCurrentPeriodIndex)
      throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.moveMediaItems(testFromIndex, testToIndex, testNewIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimeline(
        newTimelineRef.get(),
        initialMediaItemCount,
        testCurrentMediaItemIndex,
        /* testFirstPeriodIndex= */ testCurrentWindowFirstPeriodIndex,
        /* testLastPeriodIndex= */ testCurrentWindowLastPeriodIndex);
    assertThat(getEventsAsList(onEventsRef.get())).containsExactly(Player.EVENT_TIMELINE_CHANGED);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testCurrentPeriodIndex);
  }

  @Test
  public void moveMediaItems_moveCurrentItemBackOneWindow() throws Exception {
    assertMoveMediaItems(
        /* initialMediaItemCount= */ 5,
        /* initialMediaItemIndex= */ 1,
        /* testFromIndex= */ 1,
        /* testToIndex= */ 2,
        /* testNewIndex= */ 0,
        /* testCurrentMediaItemIndex= */ 0,
        /* testNextMediaItemIndex= */ 1,
        /* testPreviousMediaItemIndex= */ C.INDEX_UNSET);
  }

  @Test
  public void moveMediaItems_moveCurrentItemForwardOneWindow() throws Exception {
    assertMoveMediaItems(
        /* initialMediaItemCount= */ 5,
        /* initialMediaItemIndex= */ 1,
        /* testFromIndex= */ 1,
        /* testToIndex= */ 2,
        /* testNewIndex= */ 2,
        /* testCurrentMediaItemIndex= */ 2,
        /* testNextMediaItemIndex= */ 3,
        /* testPreviousMediaItemIndex= */ 1);
  }

  @Test
  public void moveMediaItems_moveNonCurrentItem_fromAfterCurrentItemToBefore() throws Exception {
    assertMoveMediaItems(
        /* initialMediaItemCount= */ 5,
        /* initialMediaItemIndex= */ 1,
        /* testFromIndex= */ 2,
        /* testToIndex= */ 4,
        /* testNewIndex= */ 1,
        /* testCurrentMediaItemIndex= */ 3,
        /* testNextMediaItemIndex= */ 4,
        /* testPreviousMediaItemIndex= */ 2);
  }

  @Test
  public void moveMediaItems_moveNonCurrentItem_fromBeforeCurrentItemToAfter() throws Exception {
    assertMoveMediaItems(
        /* initialMediaItemCount= */ 5,
        /* initialMediaItemIndex= */ 1,
        /* testFromIndex= */ 0,
        /* testToIndex= */ 1,
        /* testNewIndex= */ 2,
        /* testCurrentMediaItemIndex= */ 0,
        /* testNextMediaItemIndex= */ 1,
        /* testPreviousMediaItemIndex= */ C.INDEX_UNSET);
  }

  @Test
  public void incompatibleUpdatesDuringMasking_areOnlyReportedOnceAllPendingUpdatesAreResolved()
      throws Exception {
    // Test setup:
    //  1. Report a discontinuity from item 0 to item 1 in the session.
    //  2. Before (1) can be handled by the controller, remove item 1.
    // Expectation:
    //  - Session: State is updated to ENDED as the current item is removed.
    //  - Controller: Discontinuity is only reported after the state is fully resolved
    //     = The discontinuity is only reported once we also report the state change to ENDED.
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 2);
    remoteSession.getMockPlayer().setTimeline(timeline);
    remoteSession
        .getMockPlayer()
        .notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    remoteSession.getMockPlayer().setCurrentMediaItemIndex(0);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch positionDiscontinuityReported = new CountDownLatch(1);
    AtomicBoolean reportedStateChangeToEndedAtSameTimeAsDiscontinuity = new AtomicBoolean();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
              if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                  && player.getPlaybackState() == Player.STATE_ENDED) {
                reportedStateChangeToEndedAtSameTimeAsDiscontinuity.set(true);
              }
              positionDiscontinuityReported.countDown();
            }
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    // Step 1: Report a discontinuity from item 0 to item 1 in the session.
    PositionInfo oldPositionInfo =
        new PositionInfo(
            /* windowUid= */ timeline.getWindow(/* windowIndex= */ 0, new Window()).uid,
            /* mediaItemIndex= */ 0,
            MediaItem.EMPTY,
            /* periodUid= */ timeline.getPeriod(
                    /* periodIndex= */ 0, new Period(), /* setIds= */ true)
                .uid,
            /* periodIndex= */ 0,
            /* positionMs= */ 10_000,
            /* contentPositionMs= */ 10_000,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    PositionInfo newPositionInfo =
        new PositionInfo(
            /* windowUid= */ timeline.getWindow(/* windowIndex= */ 1, new Window()).uid,
            /* mediaItemIndex= */ 1,
            MediaItem.EMPTY,
            /* periodUid= */ timeline.getPeriod(
                    /* periodIndex= */ 1, new Period(), /* setIds= */ true)
                .uid,
            /* periodIndex= */ 1,
            /* positionMs= */ 0,
            /* contentPositionMs= */ 0,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    remoteSession.getMockPlayer().setCurrentMediaItemIndex(1);
    remoteSession
        .getMockPlayer()
        .notifyPositionDiscontinuity(
            oldPositionInfo, newPositionInfo, Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
    // Step 2: Before step 1 can be handled by the controller, remove item 1.
    threadTestRule.getHandler().postAndSync(() -> controller.removeMediaItem(/* index= */ 1));
    remoteSession.getMockPlayer().setCurrentMediaItemIndex(0);
    remoteSession.getMockPlayer().setTimeline(MediaTestUtils.createTimeline(/* windowCount= */ 1));
    remoteSession.getMockPlayer().notifyPlaybackStateChanged(Player.STATE_ENDED);
    remoteSession
        .getMockPlayer()
        .notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    assertThat(positionDiscontinuityReported.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(reportedStateChangeToEndedAtSameTimeAsDiscontinuity.get()).isTrue();
  }

  @Test
  public void seekTo_indexLargerThanPlaylist_isIgnored() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicInteger mediaItemIndexAfterSeek = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setMediaItem(MediaItem.fromUri("http://test"));

              controller.seekTo(/* mediaItemIndex= */ 1, /* positionMs= */ 1000);

              mediaItemIndexAfterSeek.set(controller.getCurrentMediaItemIndex());
            });

    assertThat(mediaItemIndexAfterSeek.get()).isEqualTo(0);
  }

  @Test
  public void addMediaItems_indexLargerThanPlaylist_addsToEndOfPlaylist() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    List<MediaItem> addedItems =
        ImmutableList.of(MediaItem.fromUri("http://new1"), MediaItem.fromUri("http://new2"));
    ArrayList<MediaItem> mediaItemsAfterAdd = new ArrayList<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setMediaItem(MediaItem.fromUri("http://test"));

              controller.addMediaItems(/* index= */ 5000, addedItems);

              for (int i = 0; i < controller.getMediaItemCount(); i++) {
                mediaItemsAfterAdd.add(controller.getMediaItemAt(i));
              }
            });

    assertThat(mediaItemsAfterAdd).hasSize(3);
    assertThat(mediaItemsAfterAdd.get(1)).isEqualTo(addedItems.get(0));
    assertThat(mediaItemsAfterAdd.get(2)).isEqualTo(addedItems.get(1));
  }

  @Test
  public void removeMediaItems_fromIndexLargerThanPlaylist_isIgnored() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicInteger mediaItemCountAfterRemove = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setMediaItems(
                  ImmutableList.of(
                      MediaItem.fromUri("http://item1"), MediaItem.fromUri("http://item2")));

              controller.removeMediaItems(/* fromIndex= */ 5000, /* toIndex= */ 6000);

              mediaItemCountAfterRemove.set(controller.getMediaItemCount());
            });

    assertThat(mediaItemCountAfterRemove.get()).isEqualTo(2);
  }

  @Test
  public void removeMediaItems_toIndexLargerThanPlaylist_removesUpToEndOfPlaylist()
      throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicInteger mediaItemCountAfterRemove = new AtomicInteger();
    AtomicReference<MediaItem> remainingItemAfterRemove = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setMediaItems(
                  ImmutableList.of(
                      MediaItem.fromUri("http://item1"), MediaItem.fromUri("http://item2")));

              controller.removeMediaItems(/* fromIndex= */ 1, /* toIndex= */ 6000);

              mediaItemCountAfterRemove.set(controller.getMediaItemCount());
              remainingItemAfterRemove.set(controller.getMediaItemAt(0));
            });

    assertThat(mediaItemCountAfterRemove.get()).isEqualTo(1);
    assertThat(remainingItemAfterRemove.get().localConfiguration.uri.toString())
        .isEqualTo("http://item1");
  }

  @Test
  public void moveMediaItems_fromIndexLargerThanPlaylist_isIgnored() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    List<MediaItem> items =
        ImmutableList.of(MediaItem.fromUri("http://item1"), MediaItem.fromUri("http://item2"));
    ArrayList<MediaItem> itemsAfterMove = new ArrayList<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setMediaItems(items);

              controller.moveMediaItems(
                  /* fromIndex= */ 5000, /* toIndex= */ 6000, /* newIndex= */ 0);

              for (int i = 0; i < controller.getMediaItemCount(); i++) {
                itemsAfterMove.add(controller.getMediaItemAt(i));
              }
            });

    assertThat(itemsAfterMove).isEqualTo(items);
  }

  @Test
  public void moveMediaItems_toIndexLargerThanPlaylist_movesItemsUpToEndOfPlaylist()
      throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    List<MediaItem> items =
        ImmutableList.of(MediaItem.fromUri("http://item1"), MediaItem.fromUri("http://item2"));
    ArrayList<MediaItem> itemsAfterMove = new ArrayList<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setMediaItems(items);

              controller.moveMediaItems(/* fromIndex= */ 1, /* toIndex= */ 6000, /* newIndex= */ 0);

              for (int i = 0; i < controller.getMediaItemCount(); i++) {
                itemsAfterMove.add(controller.getMediaItemAt(i));
              }
            });

    assertThat(itemsAfterMove).containsExactly(items.get(1), items.get(0)).inOrder();
  }

  @Test
  public void moveMediaItems_newIndexLargerThanPlaylist_movesItemsUpToEndOfPlaylist()
      throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    List<MediaItem> items =
        ImmutableList.of(MediaItem.fromUri("http://item1"), MediaItem.fromUri("http://item2"));
    ArrayList<MediaItem> itemsAfterMove = new ArrayList<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setMediaItems(items);

              controller.moveMediaItems(/* fromIndex= */ 0, /* toIndex= */ 1, /* newIndex= */ 5000);

              for (int i = 0; i < controller.getMediaItemCount(); i++) {
                itemsAfterMove.add(controller.getMediaItemAt(i));
              }
            });

    assertThat(itemsAfterMove).containsExactly(items.get(1), items.get(0)).inOrder();
  }

  @Test
  public void replaceMediaItems_notReplacingCurrentItem_correctMasking() throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(MediaTestUtils.createTimeline(3))
            .setCurrentMediaItemIndex(2)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItems(
                  /* fromIndex= */ 1, /* toIndex= */ 2, createMediaItems(2));
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newTimelineRef.get().getWindowCount()).isEqualTo(4);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(3);
    assertThat(getEventsAsList(onEventsRef.get())).containsExactly(Player.EVENT_TIMELINE_CHANGED);
  }

  @Test
  public void replaceMediaItems_replacingCurrentItem_correctMasking() throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(MediaTestUtils.createTimeline(3))
            .setCurrentMediaItemIndex(1)
            .setCurrentPosition(2000L)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }

          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition,
              PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            newPositionInfoRef.set(newPosition);
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItem(/* index= */ 1, createMediaItems(1).get(0));
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newTimelineRef.get().getWindowCount()).isEqualTo(3);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(1);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_MEDIA_ITEM_TRANSITION);
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(2000L);
  }

  @Test
  public void replaceMediaItems_replacingCurrentItemWithEmptyListAndSubsequentItem_correctMasking()
      throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(MediaTestUtils.createTimeline(3))
            .setCurrentMediaItemIndex(1)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItems(
                  /* fromIndex= */ 1, /* toIndex= */ 2, ImmutableList.of());
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newTimelineRef.get().getWindowCount()).isEqualTo(2);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(1);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_MEDIA_ITEM_TRANSITION);
  }

  @Test
  public void
      replaceMediaItems_replacingCurrentItemWithEmptyListAndNoSubsequentItem_correctMasking()
          throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(MediaTestUtils.createTimeline(2))
            .setCurrentMediaItemIndex(1)
            .setPlaybackState(Player.STATE_BUFFERING)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger playbackStateRef = new AtomicInteger();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItems(
                  /* fromIndex= */ 1, /* toIndex= */ 2, ImmutableList.of());
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              playbackStateRef.set(controller.getPlaybackState());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newTimelineRef.get().getWindowCount()).isEqualTo(1);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(0);
    assertThat(playbackStateRef.get()).isEqualTo(Player.STATE_ENDED);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_PLAYBACK_STATE_CHANGED);
  }

  @Test
  public void replaceMediaItems_fromPreparedEmpty_correctMasking() throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(Timeline.EMPTY)
            .setCurrentMediaItemIndex(1)
            .setPlaybackState(Player.STATE_ENDED)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger playbackStateRef = new AtomicInteger();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItems(
                  /* fromIndex= */ 0, /* toIndex= */ 0, createMediaItems(2));
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              playbackStateRef.set(controller.getPlaybackState());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newTimelineRef.get().getWindowCount()).isEqualTo(2);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(1);
    assertThat(playbackStateRef.get()).isEqualTo(Player.STATE_BUFFERING);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_PLAYBACK_STATE_CHANGED);
  }

  @Test
  public void replaceMediaItems_fromEmptyToEmpty_correctMasking() throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(Timeline.EMPTY)
            .setCurrentMediaItemIndex(1)
            .setPlaybackState(Player.STATE_ENDED)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger playbackStateRef = new AtomicInteger();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItems(
                  /* fromIndex= */ 0, /* toIndex= */ 0, ImmutableList.of());
              newTimelineRef.set(controller.getCurrentTimeline());
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              playbackStateRef.set(controller.getPlaybackState());
              latch.countDown();
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newTimelineRef.get().isEmpty()).isTrue();
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(1);
    assertThat(playbackStateRef.get()).isEqualTo(Player.STATE_ENDED);
  }

  @Test
  public void replaceMediaItems_withInvalidToIndex_correctMasking() throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(MediaTestUtils.createTimeline(3))
            .setCurrentMediaItemIndex(2)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> newTimelineRef = new AtomicReference<>();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            newTimelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItems(
                  /* fromIndex= */ 1, /* toIndex= */ 5000, createMediaItems(2));
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newTimelineRef.get().getWindowCount()).isEqualTo(3);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(1);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_POSITION_DISCONTINUITY);
  }

  private void assertMoveMediaItems(
      int initialMediaItemCount,
      int initialMediaItemIndex,
      int testFromIndex,
      int testToIndex,
      int testNewIndex,
      int testCurrentMediaItemIndex,
      int testNextMediaItemIndex,
      int testPreviousMediaItemIndex)
      throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(MediaTestUtils.createTimeline(initialMediaItemCount))
            .setCurrentMediaItemIndex(initialMediaItemIndex)
            .setCurrentPeriodIndex(initialMediaItemIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());

    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger nextMediaItemIndexRef = new AtomicInteger();
    AtomicInteger previousMediaItemIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.moveMediaItems(testFromIndex, testToIndex, testNewIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              nextMediaItemIndexRef.set(controller.getNextMediaItemIndex());
              previousMediaItemIndexRef.set(controller.getPreviousMediaItemIndex());
            });

    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(nextMediaItemIndexRef.get()).isEqualTo(testNextMediaItemIndex);
    assertThat(previousMediaItemIndexRef.get()).isEqualTo(testPreviousMediaItemIndex);
  }

  @Test
  public void stop() throws Exception {
    long duration = 6000L;
    long initialCurrentPosition = 3000L;
    long initialBufferedPosition = duration;
    int initialBufferedPercentage = 100;
    long initialTotalBufferedDuration = initialBufferedPosition - initialCurrentPosition;
    int testPlaybackState = Player.STATE_IDLE;
    long testCurrentPosition = 3000L;
    long testBufferedPosition = testCurrentPosition;
    int testBufferedPercentage = 50;
    long testTotalBufferedDuration = testBufferedPosition - testCurrentPosition;
    Timeline testTimeline = MediaTestUtils.createTimeline(3);
    PlaybackException testPlaybackException =
        new PlaybackException(
            /* message= */ "test", /* cause= */ null, PlaybackException.ERROR_CODE_REMOTE_ERROR);

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDuration(duration)
            .setPlaybackState(Player.STATE_READY)
            .setPlayerError(testPlaybackException)
            .setTimeline(testTimeline)
            .setCurrentPosition(initialCurrentPosition)
            .setBufferedPosition(initialBufferedPosition)
            .setBufferedPercentage(initialBufferedPercentage)
            .setTotalBufferedDuration(initialTotalBufferedDuration)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger playbackStateFromCallbackRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
            playbackStateFromCallbackRef.set(playbackState);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    AtomicInteger playbackStateFromGetterRef = new AtomicInteger();
    AtomicReference<PlaybackException> playerErrorFromGetterRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    AtomicLong currentPositionFromGetterRef = new AtomicLong();
    AtomicLong bufferedPositionFromGetterRef = new AtomicLong();
    AtomicInteger bufferedPercentageFromGetterRef = new AtomicInteger();
    AtomicLong totalBufferedDurationFromGetterRef = new AtomicLong();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.stop();
              playbackStateFromGetterRef.set(controller.getPlaybackState());
              playerErrorFromGetterRef.set(controller.getPlayerError());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
              currentPositionFromGetterRef.set(controller.getCurrentPosition());
              bufferedPositionFromGetterRef.set(controller.getBufferedPosition());
              bufferedPercentageFromGetterRef.set(controller.getBufferedPercentage());
              totalBufferedDurationFromGetterRef.set(controller.getTotalBufferedDuration());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateFromCallbackRef.get()).isEqualTo(testPlaybackState);
    assertThat(getEventsAsList(onEventsRef.get()))
        .containsExactly(Player.EVENT_PLAYBACK_STATE_CHANGED);
    assertThat(playbackStateFromGetterRef.get()).isEqualTo(testPlaybackState);
    assertThat(playerErrorFromGetterRef.get().errorInfoEquals(testPlaybackException)).isTrue();
    assertThat(timelineFromGetterRef.get().getWindowCount())
        .isEqualTo(testTimeline.getWindowCount());
    assertThat(currentPositionFromGetterRef.get()).isEqualTo(testCurrentPosition);
    assertThat(bufferedPositionFromGetterRef.get()).isEqualTo(testBufferedPosition);
    assertThat(bufferedPercentageFromGetterRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationFromGetterRef.get()).isEqualTo(testTotalBufferedDuration);
  }

  private RemoteMediaSession createRemoteMediaSession(String id) throws RemoteException {
    return new RemoteMediaSession(id, context, /* tokenExtras= */ null);
  }

  private void assertTimeline(
      Timeline timeline,
      int testMediaItemCount,
      int testMediaItemIndex,
      int testFirstPeriodIndex,
      int testLastPeriodIndex) {
    assertThat(timeline.getWindowCount()).isEqualTo(testMediaItemCount);
    if (testMediaItemCount > 0) {
      Window window = timeline.getWindow(testMediaItemIndex, new Window());
      assertThat(window.firstPeriodIndex).isEqualTo(testFirstPeriodIndex);
      assertThat(window.lastPeriodIndex).isEqualTo(testLastPeriodIndex);
      Period period = timeline.getPeriod(testFirstPeriodIndex, new Period());
      assertThat(period.windowIndex).isEqualTo(testMediaItemIndex);
      assertThat(period.windowIndex).isEqualTo(testMediaItemIndex);
    }
  }
}
