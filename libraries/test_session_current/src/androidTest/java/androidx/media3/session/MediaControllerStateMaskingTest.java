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

import static androidx.media3.common.DeviceInfo.PLAYBACK_TYPE_LOCAL;
import static androidx.media3.common.Player.REPEAT_MODE_ALL;
import static androidx.media3.common.Player.REPEAT_MODE_ONE;
import static androidx.media3.common.Player.STATE_ENDED;
import static androidx.media3.common.Player.STATE_READY;
import static androidx.media3.session.MediaTestUtils.createMediaItems;
import static androidx.media3.session.MediaTestUtils.createTimeline;
import static androidx.media3.test.session.common.CommonConstants.DEFAULT_TEST_NAME;
import static androidx.media3.test.session.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import androidx.annotation.Nullable;
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
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
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

/** Tests for state masking {@link MediaController} calls. */
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
    assertThat(onEventsRef.get().contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)).isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED))
        .isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_IS_PLAYING_CHANGED)).isTrue();
    assertThat(playWhenReadyFromGetterRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playbackSuppressionReasonFromGetterRef.get()).isEqualTo(testReason);
    assertThat(isPlayingFromGetterRef.get()).isEqualTo(testIsPlaying);
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
    assertThat(onEventsRef.get().contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)).isTrue();
    assertThat(shuffleModeEnabledFromGetterRef.get()).isEqualTo(testShuffleModeEnabled);
  }

  @Test
  public void setRepeatMode() throws Exception {
    int testRepeatMode = REPEAT_MODE_ALL;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setRepeatMode(REPEAT_MODE_ONE).build();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_REPEAT_MODE_CHANGED)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)).isTrue();
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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaylistMetadataChanged(MediaMetadata mediaMetadata) {
            playlistMetadataFromCallbackRef.set(mediaMetadata);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
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
  }

  @Test
  public void setVolume() throws Exception {
    float testVolume = 0.5f;
    Bundle playerConfig = new RemoteMediaSession.MockPlayerConfigBuilder().setVolume(0).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Float> volumeFromCallbackRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onVolumeChanged(float volume) {
            volumeFromCallbackRef.set(volume);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
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
  }

  @Test
  public void setDeviceVolume() throws Exception {
    int testDeviceVolume = 2;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceVolume(0).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger deviceVolumeFromCallbackRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeFromCallbackRef.set(volume);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger deviceVolumeFromGetterRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setDeviceVolume(testDeviceVolume);
              deviceVolumeFromGetterRef.set(controller.getDeviceVolume());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceVolumeFromCallbackRef.get()).isEqualTo(testDeviceVolume);
    assertThat(deviceVolumeFromGetterRef.get()).isEqualTo(testDeviceVolume);
  }

  @Test
  public void increaseDeviceVolume() throws Exception {
    int testDeviceVolume = 2;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceVolume(1)
            .setDeviceInfo(
                new DeviceInfo(PLAYBACK_TYPE_LOCAL, /* minVolume= */ 0, /* maxVolume= */ 2))
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger deviceVolumeFromCallbackRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeFromCallbackRef.set(volume);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger deviceVolumeFromGetterRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.increaseDeviceVolume();
              deviceVolumeFromGetterRef.set(controller.getDeviceVolume());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceVolumeFromCallbackRef.get()).isEqualTo(testDeviceVolume);
    assertThat(deviceVolumeFromGetterRef.get()).isEqualTo(testDeviceVolume);
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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeFromCallbackRef.set(volume);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicInteger deviceVolumeFromGetterRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.decreaseDeviceVolume();
              deviceVolumeFromGetterRef.set(controller.getDeviceVolume());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceVolumeFromCallbackRef.get()).isEqualTo(testDeviceVolume);
    assertThat(deviceVolumeFromGetterRef.get()).isEqualTo(testDeviceVolume);
  }

  @Test
  public void setDeviceMuted() throws Exception {
    boolean testDeviceMuted = true;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceMuted(false).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicBoolean deviceMutedFromCallbackRef = new AtomicBoolean();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceMutedFromCallbackRef.set(muted);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    AtomicBoolean deviceMutedFromGetterRef = new AtomicBoolean();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setDeviceMuted(testDeviceMuted);
              deviceMutedFromGetterRef.set(controller.isDeviceMuted());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceMutedFromCallbackRef.get()).isEqualTo(testDeviceMuted);
    assertThat(deviceMutedFromGetterRef.get()).isEqualTo(testDeviceMuted);
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
    AtomicReference<PlaybackException> playerErrorRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.prepare();
              playbackStateFromGetterRef.set(controller.getPlaybackState());
              playerErrorRef.set(controller.getPlayerError());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateFromCallbackRef.get()).isEqualTo(testPlaybackState);
    assertThat(onEventsRef.get().contains(Player.EVENT_PLAYBACK_STATE_CHANGED)).isTrue();
    assertThat(playbackStateFromGetterRef.get()).isEqualTo(testPlaybackState);
    assertThat(playerErrorRef.get()).isNull();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_MEDIA_ITEM_TRANSITION)).isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_MEDIA_ITEM_TRANSITION)).isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
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
              currentPositionRef.set((int) controller.getCurrentPosition());
              bufferedPositionRef.set((int) controller.getBufferedPosition());
              totalBufferedDurationRef.set((int) controller.getTotalBufferedDuration());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertThat(onEventsRef.get().contains(Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
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
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPositionRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(seekPosition);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPositionRef.set((int) controller.getCurrentPosition());
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
    AtomicInteger currentPositionRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(seekPosition);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPositionRef.set((int) controller.getCurrentPosition());
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
    int testPeriodIndex = initialPeriodIndex;
    long testSeekPositionMs = 3_000;
    long testPosition = testSeekPositionMs;
    long testBufferedPosition = testSeekPositionMs;
    long testTotalBufferedPosition = initialTotalBufferedPosition;

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
    AtomicInteger currentPositionRef = new AtomicInteger();
    AtomicInteger bufferedPositionRef = new AtomicInteger();
    AtomicInteger totalBufferedDurationRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(testMediaItemIndex, testSeekPositionMs);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
              currentPositionRef.set((int) controller.getCurrentPosition());
              bufferedPositionRef.set((int) controller.getBufferedPosition());
              totalBufferedDurationRef.set((int) controller.getTotalBufferedDuration());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testSeekPositionMs);
    assertThat(onEventsRef.get().contains(Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testPeriodIndex);
    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
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
    CountDownLatch latch = new CountDownLatch(1);
    AtomicLong oldPositionRef = new AtomicLong();
    AtomicLong newPositionRef = new AtomicLong();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            oldPositionRef.set(oldPosition.positionMs);
            newPositionRef.set(newPosition.positionMs);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    threadTestRule.getHandler().postAndSync(controller::seekBack);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(oldPositionRef.get()).isEqualTo(testCurrentPosition);
    assertThat(newPositionRef.get()).isEqualTo(testCurrentPosition - testSeekBackIncrement);
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
    CountDownLatch latch = new CountDownLatch(1);
    AtomicLong oldPositionRef = new AtomicLong();
    AtomicLong newPositionRef = new AtomicLong();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, int reason) {
            oldPositionRef.set(oldPosition.positionMs);
            newPositionRef.set(newPosition.positionMs);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    threadTestRule.getHandler().postAndSync(controller::seekForward);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(oldPositionRef.get()).isEqualTo(testCurrentPosition);
    assertThat(newPositionRef.get()).isEqualTo(testCurrentPosition + testSeekForwardIncrement);
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
              currentPositionRef.set((int) controller.getCurrentPosition());
              bufferedPositionRef.set((int) controller.getBufferedPosition());
              totalBufferedDurationRef.set((int) controller.getTotalBufferedDuration());
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
    assertThat(onEventsRef.get().contains(Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testPeriodIndex);
    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
    assertThat(totalBufferedDurationRef.get()).isEqualTo(testTotalBufferedDuration);
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
                  /* startMediaItemIndex= */ testMediaItemIndex,
                  /* startPositionMs= */ testPosition);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
              currentPositionRef.set((int) controller.getCurrentPosition());
              bufferedPositionRef.set((int) controller.getBufferedPosition());
              totalBufferedDurationRef.set((int) controller.getTotalBufferedDuration());
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
    assertThat(onEventsRef.get().contains(Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_TIMELINE_CHANGED)).isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_MEDIA_ITEM_TRANSITION)).isTrue();
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
    int testPeriodIndex = 0;
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
                  /* startMediaItemIndex= */ testMediaItemIndex,
                  /* startPositionMs= */ testPosition);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
              currentPositionRef.set((int) controller.getCurrentPosition());
              bufferedPositionRef.set((int) controller.getBufferedPosition());
              totalBufferedDurationRef.set((int) controller.getTotalBufferedDuration());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertThat(newTimelineRef.get().isEmpty()).isTrue();
    assertThat(newMediaItemRef.get()).isNull();
    assertThat(onEventsRef.get().contains(Player.EVENT_POSITION_DISCONTINUITY)).isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_TIMELINE_CHANGED)).isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_MEDIA_ITEM_TRANSITION)).isTrue();
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
  public void addMediaItems_toEmptyTimeline() throws Exception {
    int testMediaItemCount = 2;
    int testCurrentMediaItemIndex = 0;
    int testNextMediaItemIndex = 1;
    int testPreviousMediaItemIndex = C.INDEX_UNSET;
    int testCurrentPeriodIndex = 0;
    List<MediaItem> testMediaItems = createMediaItems(testMediaItemCount);
    MediaItem testMediaItem = testMediaItems.get(testCurrentPeriodIndex);

    Bundle playerConfig = new RemoteMediaSession.MockPlayerConfigBuilder().build();
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
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.addMediaItems(testMediaItems);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              nextMediaItemIndexRef.set(controller.getNextMediaItemIndex());
              previousMediaItemIndexRef.set(controller.getPreviousMediaItemIndex());
              currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertTimeline(
        newTimelineRef.get(),
        testMediaItemCount,
        testCurrentMediaItemIndex,
        /* testFirstPeriodIndex= */ testCurrentPeriodIndex,
        /* testLastPeriodIndex= */ testCurrentPeriodIndex);
    assertThat(onEventsRef.get().contains(Player.EVENT_TIMELINE_CHANGED)).isTrue();
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(nextMediaItemIndexRef.get()).isEqualTo(testNextMediaItemIndex);
    assertThat(previousMediaItemIndexRef.get()).isEqualTo(testPreviousMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testCurrentPeriodIndex);
    assertThat(newMediaItemRef.get()).isEqualTo(testMediaItem);
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
    assertThat(onEventsRef.get().contains(Player.EVENT_TIMELINE_CHANGED)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_TIMELINE_CHANGED)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_TIMELINE_CHANGED)).isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_MEDIA_ITEM_TRANSITION)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_TIMELINE_CHANGED)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_TIMELINE_CHANGED)).isTrue();
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testCurrentPeriodIndex);
  }

  @Test
  public void removeMediaItems_removeAllItems() throws Exception {
    int initialMediaItemIndex = 1;
    int initialPlaybackState = STATE_READY;
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
    int testPlaybackState = STATE_ENDED;
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
        /* ignored= */ C.INDEX_UNSET,
        /* ignored= */ C.INDEX_UNSET,
        /* ignored= */ C.INDEX_UNSET);
    assertThat(newMediaItemRef.get()).isNull();
    assertThat(newPlaybackStateRef.get()).isEqualTo(testPlaybackState);
    assertThat(onEventsRef.get().contains(Player.EVENT_TIMELINE_CHANGED)).isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_MEDIA_ITEM_TRANSITION)).isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_PLAYBACK_STATE_CHANGED)).isTrue();
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(nextMediaItemIndexRef.get()).isEqualTo(testNextMediaItemIndex);
    assertThat(previousMediaItemIndexRef.get()).isEqualTo(testPreviousMediaItemIndex);
    assertThat(newCurrentPositionRef.get()).isEqualTo(testCurrentPosition);
  }

  @Test
  public void removeMediaItems_removedTailIncludesCurrentItem_callsOnPlaybackStateChanged()
      throws Exception {
    int initialMediaItemIndex = 1;
    int initialPlaybackState = STATE_READY;
    int testFromIndex = 1;
    int testToIndex = 3;
    int testPlaybackState = STATE_ENDED;
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
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger newPlaybackStateRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
            newPlaybackStateRef.set(playbackState);
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
  }

  @Test
  public void removeMediaItems_removeCurrentItemWhenShuffledModeDisabled_masksIndices()
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
  public void removeMediaItems_removeCurrentItemWhenShuffledModeEnabled_masksIndices()
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
    assertThat(onEventsRef.get().contains(Player.EVENT_TIMELINE_CHANGED)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_TIMELINE_CHANGED)).isTrue();
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
    assertThat(onEventsRef.get().contains(Player.EVENT_PLAYBACK_STATE_CHANGED)).isTrue();
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
