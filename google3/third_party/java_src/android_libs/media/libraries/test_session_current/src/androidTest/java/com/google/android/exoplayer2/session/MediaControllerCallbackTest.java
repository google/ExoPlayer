/*
 * Copyright 2018 The Android Open Source Project
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

package com.google.android.exoplayer2.session;

import static com.google.android.exoplayer2.Player.COMMAND_PLAY_PAUSE;
import static com.google.android.exoplayer2.Player.COMMAND_SET_REPEAT_MODE;
import static com.google.android.exoplayer2.Player.EVENT_REPEAT_MODE_CHANGED;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ONE;
import static com.google.android.exoplayer2.Player.STATE_BUFFERING;
import static com.google.android.exoplayer2.session.MediaUtils.createPlayerCommandsWith;
import static com.google.android.exoplayer2.session.MediaUtils.createPlayerCommandsWithAllCommands;
import static com.google.android.exoplayer2.session.SessionResult.RESULT_SUCCESS;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.DEFAULT_TEST_NAME;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.MOCK_MEDIA2_LIBRARY_SERVICE;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.MOCK_MEDIA2_SESSION_SERVICE;
import static com.google.android.exoplayer2.session.vct.common.MediaSessionConstants.TEST_CONTROLLER_CALLBACK_SESSION_REJECTS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.LONG_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.Commands;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.PlayWhenReadyChangeReason;
import com.google.android.exoplayer2.Player.PositionInfo;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.Player.State;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.session.RemoteMediaSession.RemoteMockPlayer;
import com.google.android.exoplayer2.session.SessionPlayer.PlayerCallback;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import com.google.android.exoplayer2.util.ExoFlags;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

/**
 * Tests for {@link MediaController.ControllerCallback}. It also tests {@link
 * SessionPlayer.PlayerCallback} passed by {@link MediaController#addListener}.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerCallbackTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private static final int EVENT_ON_EVENTS = C.INDEX_UNSET;

  private final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaControllerCallbackTest");
  final MediaControllerTestRule controllerTestRule = new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  Context context;
  RemoteMediaSession remoteSession;
  private MediaController controller;

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
  public void connection_sessionAccepts() throws Exception {
    // createController() uses controller callback to wait until the controller becomes
    // available.
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    assertThat(controller).isNotNull();
  }

  @Test
  public void connection_sessionRejects() throws Exception {
    RemoteMediaSession session = createRemoteMediaSession(TEST_CONTROLLER_CALLBACK_SESSION_REJECTS);
    try {
      MediaController controller =
          controllerTestRule.createController(
              session.getToken(), /* waitForConnect= */ false, null, null);
      assertThat(controller).isNotNull();
      controllerTestRule.waitForConnect(controller, /* expected= */ false);
      controllerTestRule.waitForDisconnect(controller, /* expected= */ true);
    } finally {
      session.cleanUp();
    }
  }

  @Test
  public void connection_toSessionService() throws Exception {
    SessionToken token = new SessionToken(context, MOCK_MEDIA2_SESSION_SERVICE);
    MediaController controller = controllerTestRule.createController(token);
    assertThat(controller).isNotNull();
  }

  @Test
  public void connection_toLibraryService() throws Exception {
    SessionToken token = new SessionToken(context, MOCK_MEDIA2_LIBRARY_SERVICE);
    MediaController controller = controllerTestRule.createController(token);
    assertThat(controller).isNotNull();
  }

  @Test
  public void connection_sessionClosed() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());

    remoteSession.release();
    controllerTestRule.waitForDisconnect(controller, true);
  }

  @Test
  public void connection_controllerClosed() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());

    threadTestRule.getHandler().postAndSync(controller::release);
    controllerTestRule.waitForDisconnect(controller, true);
  }

  @Test
  @LargeTest
  public void noInteractionAfterSessionClose_session() throws Exception {
    SessionToken token = remoteSession.getToken();
    controller = controllerTestRule.createController(token);
    testControllerAfterSessionIsClosed(DEFAULT_TEST_NAME);
  }

  @Test
  @LargeTest
  public void noInteractionAfterControllerClose_session() throws Exception {
    SessionToken token = remoteSession.getToken();
    controller = controllerTestRule.createController(token);

    threadTestRule.getHandler().postAndSync(controller::release);
    // release is done immediately for session.
    testNoInteraction();

    // Test whether the controller is notified about later release of the session or
    // re-creation.
    testControllerAfterSessionIsClosed(DEFAULT_TEST_NAME);
  }

  @Test
  @LargeTest
  public void connection_withLongPlaylist() throws Exception {
    int windowCount = 5_000;
    remoteSession.getMockPlayer().createAndSetFakeTimeline(windowCount);

    CountDownLatch latch = new CountDownLatch(1);
    MediaController controller =
        new MediaController.Builder(context)
            .setSessionToken(remoteSession.getToken())
            .setControllerCallback(
                new MediaController.ControllerCallback() {
                  @Override
                  public void onConnected(MediaController controller) {
                    latch.countDown();
                  }
                })
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();

    assertThat(latch.await(LONG_TIMEOUT_MS, MILLISECONDS)).isTrue();
    Timeline timeline = threadTestRule.getHandler().postAndSync(controller::getCurrentTimeline);
    assertThat(timeline.getWindowCount()).isEqualTo(windowCount);
    Timeline.Window window = new Timeline.Window();
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      assertThat(timeline.getWindow(i, window).mediaItem.mediaId)
          .isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
  }

  @Test
  public void onPlayerError_isNotified() throws Exception {
    ExoPlaybackException testPlayerError = ExoPlaybackException.createForRemote("test exception");

    AtomicReference<ExoPlaybackException> playerErrorRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    controller.addListener(
        new PlayerCallback() {
          @Override
          public void onPlayerError(ExoPlaybackException playerError) {
            playerErrorRef.set(playerError);
            latch.countDown();
          }
        });

    remoteSession.getMockPlayer().notifyPlayerError(testPlayerError);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(TestUtils.equals(playerErrorRef.get(), testPlayerError)).isTrue();
  }

  @Test
  public void setPlayer_notifiesChangedValues() throws Exception {
    @State int testState = STATE_BUFFERING;
    Timeline testTimeline = MediaTestUtils.createTimeline(/* windowCount= */ 3);
    MediaMetadata testPlaylistMetadata = new MediaMetadata.Builder().setTitle("title").build();
    AudioAttributes testAudioAttributes =
        MediaUtils.convertToAudioAttributes(
            new AudioAttributesCompat.Builder()
                .setLegacyStreamType(AudioManager.STREAM_RING)
                .build());
    boolean testShuffleModeEnabled = true;
    @RepeatMode int testRepeatMode = REPEAT_MODE_ALL;
    int testCurrentAdGroupIndex = 33;
    int testCurrentAdIndexInAdGroup = 11;

    AtomicInteger stateRef = new AtomicInteger();
    AtomicReference<Timeline> timelineRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> playlistMetadataRef = new AtomicReference<>();
    AtomicReference<AudioAttributes> audioAttributesRef = new AtomicReference<>();
    AtomicInteger currentAdGroupIndexRef = new AtomicInteger();
    AtomicInteger currentAdIndexInAdGroupRef = new AtomicInteger();
    AtomicBoolean shuffleModeEnabledRef = new AtomicBoolean();
    AtomicInteger repeatModeRef = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(7);
    controller = controllerTestRule.createController(remoteSession.getToken());
    controller.addListener(
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onAudioAttributesChanged(@NonNull AudioAttributes attributes) {
            audioAttributesRef.set(attributes);
            latch.countDown();
          }

          @Override
          public void onPlaybackStateChanged(@State int state) {
            stateRef.set(state);
            latch.countDown();
          }

          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineRef.set(timeline);
            latch.countDown();
          }

          @Override
          public void onPlaylistMetadataChanged(MediaMetadata playlistMetadata) {
            playlistMetadataRef.set(playlistMetadata);
            latch.countDown();
          }

          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            currentAdGroupIndexRef.set(newPosition.adGroupIndex);
            currentAdIndexInAdGroupRef.set(newPosition.adIndexInAdGroup);
            latch.countDown();
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            shuffleModeEnabledRef.set(shuffleModeEnabled);
            latch.countDown();
          }

          @Override
          public void onRepeatModeChanged(@RepeatMode int repeatMode) {
            repeatModeRef.set(repeatMode);
            latch.countDown();
          }
        });

    // TODO(b/149713425): Stop setting MediaItem as current media item.
    //                    Currently it's necessary for trigger position discontinuity.
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackState(testState)
            .setAudioAttributes(testAudioAttributes)
            .setTimeline(testTimeline)
            .setPlaylistMetadata(testPlaylistMetadata)
            .setCurrentMediaItem(MediaTestUtils.createConvergedMediaItem("mediaId"))
            .setShuffleModeEnabled(testShuffleModeEnabled)
            .setRepeatMode(testRepeatMode)
            .setCurrentAdGroupIndex(testCurrentAdGroupIndex)
            .setCurrentAdIndexInAdGroup(testCurrentAdIndexInAdGroup)
            .build();

    remoteSession.setPlayer(playerConfig);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(stateRef.get()).isEqualTo(testState);
    MediaTestUtils.assertMediaIdEquals(testTimeline, timelineRef.get());
    assertThat(playlistMetadataRef.get()).isEqualTo(testPlaylistMetadata);
    assertThat(audioAttributesRef.get()).isEqualTo(testAudioAttributes);
    assertThat(currentAdGroupIndexRef.get()).isEqualTo(testCurrentAdGroupIndex);
    assertThat(currentAdIndexInAdGroupRef.get()).isEqualTo(testCurrentAdIndexInAdGroup);
    assertThat(shuffleModeEnabledRef.get()).isEqualTo(testShuffleModeEnabled);
    assertThat(repeatModeRef.get()).isEqualTo(testRepeatMode);
  }

  @Test
  public void setPlayer_updatesGetters() throws Exception {
    long testCurrentPositionMs = 11;
    long testContentPositionMs = 33;
    long testDurationMs = 200;
    long testBufferedPositionMs = 100;
    int testBufferedPercentage = 50;
    long testTotalBufferedDurationMs = 120;
    long testCurrentLiveOffsetMs = 10;
    long testContentDurationMs = 300;
    long testContentBufferedPositionMs = 240;
    boolean testIsPlayingAd = true;
    int testCurrentAdGroupIndex = 2;
    int testCurrentAdIndexInAdGroup = 6;
    int testWindowIndex = 1;
    int testPeriodIndex = 2;

    controller = controllerTestRule.createController(remoteSession.getToken());

    CountDownLatch latch = new CountDownLatch(1);
    AtomicLong currentPositionMsRef = new AtomicLong();
    AtomicLong contentPositionMsRef = new AtomicLong();
    AtomicLong durationMsRef = new AtomicLong();
    AtomicLong bufferedPositionMsRef = new AtomicLong();
    AtomicInteger bufferedPercentageRef = new AtomicInteger();
    AtomicLong totalBufferedDurationMsRef = new AtomicLong();
    AtomicLong currentLiveOffsetMsRef = new AtomicLong();
    AtomicLong contentDurationMsRef = new AtomicLong();
    AtomicLong contentBufferedPositionMsRef = new AtomicLong();
    AtomicBoolean isPlayingAdRef = new AtomicBoolean();
    AtomicInteger currentAdGroupIndexRef = new AtomicInteger();
    AtomicInteger currentAdIndexInAdGroupRef = new AtomicInteger();
    AtomicInteger currentWindowIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    controller.addListener(
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            currentPositionMsRef.set(controller.getCurrentPosition());
            contentPositionMsRef.set(controller.getContentPosition());
            durationMsRef.set(controller.getDuration());
            bufferedPositionMsRef.set(controller.getBufferedPosition());
            bufferedPercentageRef.set(controller.getBufferedPercentage());
            totalBufferedDurationMsRef.set(controller.getTotalBufferedDuration());
            currentLiveOffsetMsRef.set(controller.getCurrentLiveOffset());
            contentDurationMsRef.set(controller.getContentDuration());
            contentBufferedPositionMsRef.set(controller.getContentBufferedPosition());
            isPlayingAdRef.set(controller.isPlayingAd());
            currentAdGroupIndexRef.set(controller.getCurrentAdGroupIndex());
            currentAdIndexInAdGroupRef.set(controller.getCurrentAdIndexInAdGroup());
            currentWindowIndexRef.set(controller.getCurrentWindowIndex());
            currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
            latch.countDown();
          }
        });

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackState(Player.STATE_READY)
            .setCurrentPosition(testCurrentPositionMs)
            .setContentPosition(testContentPositionMs)
            .setDuration(testDurationMs)
            .setBufferedPosition(testBufferedPositionMs)
            .setBufferedPercentage(testBufferedPercentage)
            .setTotalBufferedDuration(testTotalBufferedDurationMs)
            .setCurrentLiveOffset(testCurrentLiveOffsetMs)
            .setContentDuration(testContentDurationMs)
            .setContentBufferedPosition(testContentBufferedPositionMs)
            .setIsPlayingAd(testIsPlayingAd)
            .setCurrentAdGroupIndex(testCurrentAdGroupIndex)
            .setCurrentAdIndexInAdGroup(testCurrentAdIndexInAdGroup)
            .setCurrentWindowIndex(testWindowIndex)
            .setCurrentPeriodIndex(testPeriodIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(currentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(contentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(durationMsRef.get()).isEqualTo(testDurationMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentDurationMsRef.get()).isEqualTo(testContentDurationMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(isPlayingAdRef.get()).isEqualTo(testIsPlayingAd);
    assertThat(currentAdGroupIndexRef.get()).isEqualTo(testCurrentAdGroupIndex);
    assertThat(currentAdIndexInAdGroupRef.get()).isEqualTo(testCurrentAdIndexInAdGroup);
    assertThat(currentWindowIndexRef.get()).isEqualTo(testWindowIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testPeriodIndex);
  }

  @Test
  public void onMediaItemTransition() throws Exception {
    int currentIndex = 0;
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 5);
    remoteSession.getMockPlayer().setTimeline(timeline);
    remoteSession.getMockPlayer().setCurrentWindowIndex(currentIndex);
    remoteSession
        .getMockPlayer()
        .notifyMediaItemTransition(
            currentIndex, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);

    AtomicReference<MediaItem> mediaItemFromParam = new AtomicReference<>();
    AtomicReference<MediaItem> mediaItemFromGetter = new AtomicReference<>();
    AtomicInteger reasonRef = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(1);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    controller.addListener(
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            mediaItemFromParam.set(mediaItem);
            mediaItemFromGetter.set(controller.getCurrentMediaItem());
            reasonRef.set(reason);
            latch.countDown();
          }
        });

    int testIndex = 3;
    int testReason = Player.MEDIA_ITEM_TRANSITION_REASON_SEEK;
    remoteSession.getMockPlayer().setCurrentWindowIndex(testIndex);
    remoteSession.getMockPlayer().notifyMediaItemTransition(testIndex, testReason);
    Timeline.Window window = new Timeline.Window();
    MediaItem currentMediaItem = timeline.getWindow(testIndex, window).mediaItem;

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaItemFromParam.get()).isEqualTo(currentMediaItem);
    assertThat(mediaItemFromGetter.get()).isEqualTo(currentMediaItem);
    assertThat(reasonRef.get()).isEqualTo(testReason);
  }

  @Test
  public void onMediaItemTransition_withNullMediaIteam() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 1);
    remoteSession.getMockPlayer().setTimeline(timeline);
    remoteSession.getMockPlayer().setCurrentWindowIndex(0);
    remoteSession
        .getMockPlayer()
        .notifyMediaItemTransition(0, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);

    AtomicReference<MediaItem> mediaItemRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    controller.addListener(
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            mediaItemRef.set(mediaItem);
            latch.countDown();
          }
        });

    remoteSession.getMockPlayer().setTimeline(Timeline.EMPTY);
    remoteSession
        .getMockPlayer()
        .notifyMediaItemTransition(
            C.INDEX_UNSET, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaItemRef.get()).isNull();
  }

  /** This also tests {@link MediaController#getPlaybackParameters()}. */
  @Test
  public void onPlaybackParametersChanged_isNotified() throws Exception {
    PlaybackParameters testPlaybackParameters =
        new PlaybackParameters(/* speed= */ 3.2f, /* pitch= */ 2.1f);
    remoteSession.getMockPlayer().setPlaybackParameters(PlaybackParameters.DEFAULT);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PlaybackParameters> playbackParametersRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            playbackParametersRef.set(playbackParameters);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().notifyPlaybackParametersChanged(testPlaybackParameters);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackParametersRef.get()).isEqualTo(testPlaybackParameters);
  }

  @Test
  public void onPlaybackParametersChanged_updatesGetters() throws Exception {
    PlaybackParameters testPlaybackParameters =
        new PlaybackParameters(/* speed= */ 3.2f, /* pitch= */ 2.1f);
    long testCurrentPositionMs = 11;
    long testContentPositionMs = 33;
    long testBufferedPositionMs = 100;
    int testBufferedPercentage = 50;
    long testTotalBufferedDurationMs = 120;
    long testCurrentLiveOffsetMs = 10;
    long testContentBufferedPositionMs = 240;

    controller = controllerTestRule.createController(remoteSession.getToken());

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PlaybackParameters> playbackParametersRef = new AtomicReference<>();
    AtomicLong currentPositionMsRef = new AtomicLong();
    AtomicLong contentPositionMsRef = new AtomicLong();
    AtomicLong durationMsRef = new AtomicLong();
    AtomicLong bufferedPositionMsRef = new AtomicLong();
    AtomicInteger bufferedPercentageRef = new AtomicInteger();
    AtomicLong totalBufferedDurationMsRef = new AtomicLong();
    AtomicLong currentLiveOffsetMsRef = new AtomicLong();
    AtomicLong contentDurationMsRef = new AtomicLong();
    AtomicLong contentBufferedPositionMsRef = new AtomicLong();
    AtomicBoolean isPlayingAdRef = new AtomicBoolean();
    AtomicInteger currentAdGroupIndexRef = new AtomicInteger();
    AtomicInteger currentAdIndexInAdGroupRef = new AtomicInteger();
    controller.addListener(
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            playbackParametersRef.set(controller.getPlaybackParameters());
            currentPositionMsRef.set(controller.getCurrentPosition());
            contentPositionMsRef.set(controller.getContentPosition());
            bufferedPositionMsRef.set(controller.getBufferedPosition());
            bufferedPercentageRef.set(controller.getBufferedPercentage());
            totalBufferedDurationMsRef.set(controller.getTotalBufferedDuration());
            currentLiveOffsetMsRef.set(controller.getCurrentLiveOffset());
            contentBufferedPositionMsRef.set(controller.getContentBufferedPosition());
            latch.countDown();
          }
        });

    remoteSession.getMockPlayer().setCurrentPosition(testCurrentPositionMs);
    remoteSession.getMockPlayer().setContentPosition(testContentPositionMs);
    remoteSession.getMockPlayer().setBufferedPosition(testBufferedPositionMs);
    remoteSession.getMockPlayer().setBufferedPercentage(testBufferedPercentage);
    remoteSession.getMockPlayer().setTotalBufferedDuration(testTotalBufferedDurationMs);
    remoteSession.getMockPlayer().setCurrentLiveOffset(testCurrentLiveOffsetMs);
    remoteSession.getMockPlayer().setContentBufferedPosition(testContentBufferedPositionMs);
    remoteSession.getMockPlayer().notifyPlaybackParametersChanged(testPlaybackParameters);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackParametersRef.get()).isEqualTo(testPlaybackParameters);
    assertThat(currentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(contentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
  }

  /** This also tests {@link MediaController#getCurrentTimeline()}. */
  @Test
  public void onTimelineChanged() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    AtomicInteger reasonRef = new AtomicInteger();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            reasonRef.set(reason);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 2);
    @Player.TimelineChangeReason int reason = Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE;
    remoteSession.getMockPlayer().setTimeline(timeline);
    remoteSession.getMockPlayer().notifyTimelineChanged(reason);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertMediaIdEquals(timeline, timelineFromParamRef.get());
    MediaTestUtils.assertMediaIdEquals(timeline, timelineFromGetterRef.get());
    assertThat(reasonRef.get()).isEqualTo(reason);
  }

  @Test
  @LargeTest
  public void onTimelineChanged_withLongPlaylist() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<Timeline> timelineRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineRef.set(timeline);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    int windowCount = 5_000;
    remoteSession.getMockPlayer().createAndSetFakeTimeline(windowCount);
    remoteSession
        .getMockPlayer()
        .notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    assertThat(latch.await(LONG_TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(timelineRef.get().getWindowCount()).isEqualTo(windowCount);
    Timeline.Window window = new Timeline.Window();
    for (int i = 0; i < windowCount; i++) {
      assertThat(timelineRef.get().getWindow(i, window).mediaItem.mediaId)
          .isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
  }

  @Test
  public void onTimelineChanged_withEmptyTimeline() throws Exception {
    remoteSession.getMockPlayer().createAndSetFakeTimeline(/* windowCount= */ 1);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Timeline> timelineRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineRef.set(timeline);
            latch.countDown();
          }
        };
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    controller.addListener(callback);

    remoteSession.getMockPlayer().setTimeline(Timeline.EMPTY);
    remoteSession
        .getMockPlayer()
        .notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(timelineRef.get().getWindowCount()).isEqualTo(0);
    assertThat(timelineRef.get().getPeriodCount()).isEqualTo(0);
  }

  /** This also tests {@link MediaController#getPlaylistMetadata()}. */
  @Test
  public void onPlaylistMetadataChanged() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<MediaMetadata> metadataFromParamRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> metadataFromGetterRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistMetadataChanged(MediaMetadata metadata) {
            metadataFromParamRef.set(metadata);
            metadataFromGetterRef.set(controller.getPlaylistMetadata());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setTitle("title").build();
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    player.setPlaylistMetadata(playlistMetadata);
    player.notifyPlaylistMetadataChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(metadataFromParamRef.get()).isEqualTo(playlistMetadata);
    assertThat(metadataFromGetterRef.get()).isEqualTo(playlistMetadata);
  }

  /** This also tests {@link MediaController#getShuffleModeEnabled()}. */
  @Test
  public void onShuffleModeEnabledChanged() throws Exception {
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    Timeline timeline =
        new PlaylistTimeline(
            MediaTestUtils.createConvergedMediaItems(/* size= */ 3),
            /* shuffledIndices= */ new int[] {0, 2, 1});
    player.setTimeline(timeline);
    player.notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    player.setCurrentWindowIndex(2);
    player.setShuffleModeEnabled(false);
    player.notifyShuffleModeEnabledChanged();

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean shuffleModeEnabledFromParamRef = new AtomicBoolean();
    AtomicBoolean shuffleModeEnabledFromGetterRef = new AtomicBoolean();
    AtomicInteger previousIndexRef = new AtomicInteger();
    AtomicInteger nextIndexRef = new AtomicInteger();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            shuffleModeEnabledFromParamRef.set(shuffleModeEnabled);
            shuffleModeEnabledFromGetterRef.set(controller.getShuffleModeEnabled());
            previousIndexRef.set(controller.getPreviousWindowIndex());
            nextIndexRef.set(controller.getNextWindowIndex());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    player.setShuffleModeEnabled(true);
    player.notifyShuffleModeEnabledChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(shuffleModeEnabledFromParamRef.get()).isTrue();
    assertThat(shuffleModeEnabledFromGetterRef.get()).isTrue();
    assertThat(previousIndexRef.get()).isEqualTo(0);
    assertThat(nextIndexRef.get()).isEqualTo(1);
  }

  /** This also tests {@link MediaController#getRepeatMode()}. */
  @Test
  public void onRepeatModeChanged() throws Exception {
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 3);
    player.setTimeline(timeline);
    player.notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    player.setCurrentWindowIndex(2);
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    player.notifyRepeatModeChanged();

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger repeatModeFromParamRef = new AtomicInteger();
    AtomicInteger repeatModeFromGetterRef = new AtomicInteger();
    AtomicInteger previousIndexRef = new AtomicInteger();
    AtomicInteger nextIndexRef = new AtomicInteger();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onRepeatModeChanged(@RepeatMode int repeatMode) {
            repeatModeFromParamRef.set(repeatMode);
            repeatModeFromGetterRef.set(controller.getRepeatMode());
            previousIndexRef.set(controller.getPreviousWindowIndex());
            nextIndexRef.set(controller.getNextWindowIndex());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    int testRepeatMode = REPEAT_MODE_ALL;
    player.setRepeatMode(testRepeatMode);
    player.notifyRepeatModeChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(repeatModeFromParamRef.get()).isEqualTo(testRepeatMode);
    assertThat(repeatModeFromGetterRef.get()).isEqualTo(testRepeatMode);
    assertThat(previousIndexRef.get()).isEqualTo(1);
    assertThat(nextIndexRef.get()).isEqualTo(0);
  }

  @Test
  public void onPlayWhenReadyChanged_isNotified() throws Exception {
    boolean testPlayWhenReady = true;
    @Player.PlayWhenReadyChangeReason
    int testReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
    @Player.PlaybackSuppressionReason
    int testSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE;
    remoteSession
        .getMockPlayer()
        .setPlayWhenReady(false, Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicBoolean playWhenReadyRef = new AtomicBoolean();
    AtomicInteger playWhenReadyReasonRef = new AtomicInteger();
    AtomicInteger playbackSuppressionReasonRef = new AtomicInteger();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            playWhenReadyRef.set(playWhenReady);
            playWhenReadyReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onPlaybackSuppressionReasonChanged(int playbackSuppressionReason) {
            playbackSuppressionReasonRef.set(playbackSuppressionReason);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().notifyPlayWhenReadyChanged(testPlayWhenReady, testReason);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playWhenReadyRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playWhenReadyReasonRef.get()).isEqualTo(testReason);
    assertThat(playbackSuppressionReasonRef.get()).isEqualTo(testSuppressionReason);
  }

  @Test
  public void onPlayWhenReadyChanged_updatesGetters() throws Exception {
    boolean testPlayWhenReady = true;
    @Player.PlaybackSuppressionReason int testReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE;
    remoteSession
        .getMockPlayer()
        .setPlayWhenReady(false, Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    long testCurrentPositionMs = 11;
    long testContentPositionMs = 33;
    long testBufferedPositionMs = 100;
    int testBufferedPercentage = 50;
    long testTotalBufferedDurationMs = 120;
    long testCurrentLiveOffsetMs = 10;
    long testContentBufferedPositionMs = 240;

    controller = controllerTestRule.createController(remoteSession.getToken());

    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean playWhenReadyRef = new AtomicBoolean();
    AtomicInteger playbackSuppressionReasonRef = new AtomicInteger();
    AtomicLong currentPositionMsRef = new AtomicLong();
    AtomicLong contentPositionMsRef = new AtomicLong();
    AtomicLong bufferedPositionMsRef = new AtomicLong();
    AtomicInteger bufferedPercentageRef = new AtomicInteger();
    AtomicLong totalBufferedDurationMsRef = new AtomicLong();
    AtomicLong currentLiveOffsetMsRef = new AtomicLong();
    AtomicLong contentBufferedPositionMsRef = new AtomicLong();
    controller.addListener(
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @PlayWhenReadyChangeReason int reason) {
            playWhenReadyRef.set(controller.getPlayWhenReady());
            playbackSuppressionReasonRef.set(controller.getPlaybackSuppressionReason());
            currentPositionMsRef.set(controller.getCurrentPosition());
            contentPositionMsRef.set(controller.getContentPosition());
            bufferedPositionMsRef.set(controller.getBufferedPosition());
            bufferedPercentageRef.set(controller.getBufferedPercentage());
            totalBufferedDurationMsRef.set(controller.getTotalBufferedDuration());
            currentLiveOffsetMsRef.set(controller.getCurrentLiveOffset());
            contentBufferedPositionMsRef.set(controller.getContentBufferedPosition());
            latch.countDown();
          }
        });

    remoteSession.getMockPlayer().setCurrentPosition(testCurrentPositionMs);
    remoteSession.getMockPlayer().setContentPosition(testContentPositionMs);
    remoteSession.getMockPlayer().setBufferedPosition(testBufferedPositionMs);
    remoteSession.getMockPlayer().setBufferedPercentage(testBufferedPercentage);
    remoteSession.getMockPlayer().setTotalBufferedDuration(testTotalBufferedDurationMs);
    remoteSession.getMockPlayer().setCurrentLiveOffset(testCurrentLiveOffsetMs);
    remoteSession.getMockPlayer().setContentBufferedPosition(testContentBufferedPositionMs);
    remoteSession.getMockPlayer().notifyPlayWhenReadyChanged(testPlayWhenReady, testReason);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playWhenReadyRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playbackSuppressionReasonRef.get()).isEqualTo(testReason);
    assertThat(currentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
  }

  @Test
  public void onPlaybackSuppressionReasonChanged_isNotified() throws Exception {
    boolean testPlayWhenReady = true;
    @Player.PlaybackSuppressionReason
    int testReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS;
    remoteSession
        .getMockPlayer()
        .setPlayWhenReady(testPlayWhenReady, Player.PLAYBACK_SUPPRESSION_REASON_NONE);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger playbackSuppressionReasonRef = new AtomicInteger();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackSuppressionReasonChanged(int reason) {
            playbackSuppressionReasonRef.set(reason);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().notifyPlayWhenReadyChanged(testPlayWhenReady, testReason);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackSuppressionReasonRef.get()).isEqualTo(testReason);
  }

  @Test
  public void onPlaybackSuppressionReasonChanged_updatesGetters() throws Exception {
    boolean testPlayWhenReady = true;
    @Player.PlaybackSuppressionReason
    int testReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS;
    long testCurrentPositionMs = 11;
    long testContentPositionMs = 33;
    long testBufferedPositionMs = 100;
    int testBufferedPercentage = 50;
    long testTotalBufferedDurationMs = 120;
    long testCurrentLiveOffsetMs = 10;
    long testContentBufferedPositionMs = 240;
    remoteSession
        .getMockPlayer()
        .setPlayWhenReady(testPlayWhenReady, Player.PLAYBACK_SUPPRESSION_REASON_NONE);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger playbackSuppressionReasonRef = new AtomicInteger();
    AtomicLong currentPositionMsRef = new AtomicLong();
    AtomicLong contentPositionMsRef = new AtomicLong();
    AtomicLong bufferedPositionMsRef = new AtomicLong();
    AtomicInteger bufferedPercentageRef = new AtomicInteger();
    AtomicLong totalBufferedDurationMsRef = new AtomicLong();
    AtomicLong currentLiveOffsetMsRef = new AtomicLong();
    AtomicLong contentBufferedPositionMsRef = new AtomicLong();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackSuppressionReasonChanged(int reason) {
            playbackSuppressionReasonRef.set(controller.getPlaybackSuppressionReason());
            currentPositionMsRef.set(controller.getCurrentPosition());
            contentPositionMsRef.set(controller.getContentPosition());
            bufferedPositionMsRef.set(controller.getBufferedPosition());
            bufferedPercentageRef.set(controller.getBufferedPercentage());
            totalBufferedDurationMsRef.set(controller.getTotalBufferedDuration());
            currentLiveOffsetMsRef.set(controller.getCurrentLiveOffset());
            contentBufferedPositionMsRef.set(controller.getContentBufferedPosition());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().setCurrentPosition(testCurrentPositionMs);
    remoteSession.getMockPlayer().setContentPosition(testContentPositionMs);
    remoteSession.getMockPlayer().setBufferedPosition(testBufferedPositionMs);
    remoteSession.getMockPlayer().setBufferedPercentage(testBufferedPercentage);
    remoteSession.getMockPlayer().setTotalBufferedDuration(testTotalBufferedDurationMs);
    remoteSession.getMockPlayer().setCurrentLiveOffset(testCurrentLiveOffsetMs);
    remoteSession.getMockPlayer().setContentBufferedPosition(testContentBufferedPositionMs);
    remoteSession.getMockPlayer().notifyPlayWhenReadyChanged(testPlayWhenReady, testReason);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackSuppressionReasonRef.get()).isEqualTo(testReason);
    assertThat(currentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(contentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
  }

  @Test
  public void onPlaybackStateChanged_isNotified() throws Exception {
    @Player.State int testPlaybackState = STATE_BUFFERING;
    remoteSession.getMockPlayer().notifyPlaybackStateChanged(Player.STATE_IDLE);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger playbackStateRef = new AtomicInteger();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackStateChanged(int reason) {
            playbackStateRef.set(reason);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().notifyPlaybackStateChanged(testPlaybackState);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateRef.get()).isEqualTo(testPlaybackState);
  }

  @Test
  public void onPlaybackStateChanged_updatesGetters() throws Exception {
    @Player.State int testPlaybackState = STATE_BUFFERING;
    long testCurrentPositionMs = 11;
    long testContentPositionMs = 33;
    long testBufferedPositionMs = 100;
    int testBufferedPercentage = 50;
    long testTotalBufferedDurationMs = 120;
    long testCurrentLiveOffsetMs = 10;
    long testContentBufferedPositionMs = 240;
    remoteSession.getMockPlayer().notifyPlaybackStateChanged(Player.STATE_IDLE);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger playbackStateRef = new AtomicInteger();
    AtomicLong currentPositionMsRef = new AtomicLong();
    AtomicLong contentPositionMsRef = new AtomicLong();
    AtomicLong bufferedPositionMsRef = new AtomicLong();
    AtomicInteger bufferedPercentageRef = new AtomicInteger();
    AtomicLong totalBufferedDurationMsRef = new AtomicLong();
    AtomicLong currentLiveOffsetMsRef = new AtomicLong();
    AtomicLong contentBufferedPositionMsRef = new AtomicLong();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackStateChanged(int reason) {
            playbackStateRef.set(controller.getPlaybackState());
            currentPositionMsRef.set(controller.getCurrentPosition());
            contentPositionMsRef.set(controller.getContentPosition());
            bufferedPositionMsRef.set(controller.getBufferedPosition());
            bufferedPercentageRef.set(controller.getBufferedPercentage());
            totalBufferedDurationMsRef.set(controller.getTotalBufferedDuration());
            currentLiveOffsetMsRef.set(controller.getCurrentLiveOffset());
            contentBufferedPositionMsRef.set(controller.getContentBufferedPosition());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().setCurrentPosition(testCurrentPositionMs);
    remoteSession.getMockPlayer().setContentPosition(testContentPositionMs);
    remoteSession.getMockPlayer().setBufferedPosition(testBufferedPositionMs);
    remoteSession.getMockPlayer().setBufferedPercentage(testBufferedPercentage);
    remoteSession.getMockPlayer().setTotalBufferedDuration(testTotalBufferedDurationMs);
    remoteSession.getMockPlayer().setCurrentLiveOffset(testCurrentLiveOffsetMs);
    remoteSession.getMockPlayer().setContentBufferedPosition(testContentBufferedPositionMs);
    remoteSession.getMockPlayer().notifyPlaybackStateChanged(testPlaybackState);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateRef.get()).isEqualTo(testPlaybackState);
    assertThat(currentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(contentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
  }

  @Test
  public void onIsPlayingChanged_isNotified() throws Exception {
    boolean testIsPlaying = true;
    remoteSession.getMockPlayer().notifyIsPlayingChanged(false);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean isPlayingRef = new AtomicBoolean();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onIsPlayingChanged(boolean isPlaying) {
            isPlayingRef.set(isPlaying);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().notifyIsPlayingChanged(testIsPlaying);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(isPlayingRef.get()).isEqualTo(testIsPlaying);
  }

  @Test
  public void onIsPlayingChanged_updatesGetters() throws Exception {
    boolean testIsPlaying = true;
    long testCurrentPositionMs = 11;
    long testContentPositionMs = 33;
    long testBufferedPositionMs = 100;
    int testBufferedPercentage = 50;
    long testTotalBufferedDurationMs = 120;
    long testCurrentLiveOffsetMs = 10;
    long testContentBufferedPositionMs = 240;
    remoteSession.getMockPlayer().notifyIsPlayingChanged(false);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    threadTestRule.getHandler().postAndSync(() -> controller.setTimeDiffMs(/* timeDiff= */ 0L));

    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean isPlayingRef = new AtomicBoolean();
    AtomicLong currentPositionMsRef = new AtomicLong();
    AtomicLong contentPositionMsRef = new AtomicLong();
    AtomicLong bufferedPositionMsRef = new AtomicLong();
    AtomicInteger bufferedPercentageRef = new AtomicInteger();
    AtomicLong totalBufferedDurationMsRef = new AtomicLong();
    AtomicLong currentLiveOffsetMsRef = new AtomicLong();
    AtomicLong contentBufferedPositionMsRef = new AtomicLong();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onIsPlayingChanged(boolean isPlaying) {
            isPlayingRef.set(controller.isPlaying());
            currentPositionMsRef.set(controller.getCurrentPosition());
            contentPositionMsRef.set(controller.getContentPosition());
            bufferedPositionMsRef.set(controller.getBufferedPosition());
            bufferedPercentageRef.set(controller.getBufferedPercentage());
            totalBufferedDurationMsRef.set(controller.getTotalBufferedDuration());
            currentLiveOffsetMsRef.set(controller.getCurrentLiveOffset());
            contentBufferedPositionMsRef.set(controller.getContentBufferedPosition());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().setCurrentPosition(testCurrentPositionMs);
    remoteSession.getMockPlayer().setContentPosition(testContentPositionMs);
    remoteSession.getMockPlayer().setBufferedPosition(testBufferedPositionMs);
    remoteSession.getMockPlayer().setBufferedPercentage(testBufferedPercentage);
    remoteSession.getMockPlayer().setTotalBufferedDuration(testTotalBufferedDurationMs);
    remoteSession.getMockPlayer().setCurrentLiveOffset(testCurrentLiveOffsetMs);
    remoteSession.getMockPlayer().setContentBufferedPosition(testContentBufferedPositionMs);
    remoteSession.getMockPlayer().notifyIsPlayingChanged(testIsPlaying);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(isPlayingRef.get()).isEqualTo(testIsPlaying);
    assertThat(currentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(contentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
  }

  @Test
  public void onIsLoadingChanged_isNotified() throws Exception {
    boolean testIsLoading = true;
    remoteSession.getMockPlayer().notifyIsLoadingChanged(false);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean isLoadingFromParamRef = new AtomicBoolean();
    AtomicBoolean isLoadingFromGetterRef = new AtomicBoolean();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onIsLoadingChanged(boolean isLoading) {
            isLoadingFromParamRef.set(isLoading);
            isLoadingFromGetterRef.set(controller.isLoading());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().notifyIsLoadingChanged(testIsLoading);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(isLoadingFromParamRef.get()).isEqualTo(testIsLoading);
    assertThat(isLoadingFromGetterRef.get()).isEqualTo(testIsLoading);
  }

  @Test
  public void onPositionDiscontinuity_isNotified() throws Exception {
    PositionInfo testOldPosition =
        new PositionInfo(
            /* windowUid= */ null,
            /* windowIndex= */ 2,
            /* periodUid= */ null,
            /* periodIndex= */ C.INDEX_UNSET,
            /* positionMs= */ 300L,
            /* contentPositionMs= */ 200L,
            /* adGroupIndex= */ 33,
            /* adIndexInAdGroup= */ 2);
    PositionInfo testNewPosition =
        new PositionInfo(
            /* windowUid= */ null,
            /* windowIndex= */ 3,
            /* periodUid= */ null,
            /* periodIndex= */ C.INDEX_UNSET,
            /* positionMs= */ 0L,
            /* contentPositionMs= */ 0L,
            /* adGroupIndex= */ C.INDEX_UNSET,
            /* adIndexInAdGroup= */ C.INDEX_UNSET);
    @DiscontinuityReason int testReason = Player.DISCONTINUITY_REASON_INTERNAL;

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PositionInfo> oldPositionRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionRef = new AtomicReference<>();
    AtomicInteger positionDiscontinuityReasonRef = new AtomicInteger();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            oldPositionRef.set(oldPosition);
            newPositionRef.set(newPosition);
            positionDiscontinuityReasonRef.set(reason);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession
        .getMockPlayer()
        .notifyPositionDiscontinuity(testOldPosition, testNewPosition, testReason);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(positionDiscontinuityReasonRef.get()).isEqualTo(testReason);
    assertThat(oldPositionRef.get()).isEqualTo(testOldPosition);
    assertThat(newPositionRef.get()).isEqualTo(testNewPosition);
  }

  @Test
  public void onPositionDiscontinuity_updatesGetters() throws Exception {
    long testCurrentPositionMs = 11;
    long testContentPositionMs = 33;
    long testDurationMs = 200;
    long testBufferedPositionMs = 100;
    int testBufferedPercentage = 50;
    long testTotalBufferedDurationMs = 120;
    long testCurrentLiveOffsetMs = 10;
    long testContentDurationMs = 300;
    long testContentBufferedPositionMs = 240;
    boolean testIsPlayingAd = true;
    int testCurrentAdGroupIndex = 33;
    int testCurrentAdIndexInAdGroup = 11;
    PositionInfo newPositionInfo =
        new PositionInfo(
            /* windowUid= */ null,
            /* windowIndex= */ C.INDEX_UNSET,
            /* periodUid= */ null,
            /* periodIndex= */ C.INDEX_UNSET,
            testCurrentPositionMs,
            testContentPositionMs,
            testCurrentAdGroupIndex,
            testCurrentAdIndexInAdGroup);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicLong currentPositionMsRef = new AtomicLong();
    AtomicLong contentPositionMsRef = new AtomicLong();
    AtomicLong durationMsRef = new AtomicLong();
    AtomicLong bufferedPositionMsRef = new AtomicLong();
    AtomicInteger bufferedPercentageRef = new AtomicInteger();
    AtomicLong totalBufferedDurationMsRef = new AtomicLong();
    AtomicLong currentLiveOffsetMsRef = new AtomicLong();
    AtomicLong contentDurationMsRef = new AtomicLong();
    AtomicLong contentBufferedPositionMsRef = new AtomicLong();
    AtomicBoolean isPlayingAdRef = new AtomicBoolean();
    AtomicInteger currentAdGroupIndexRef = new AtomicInteger();
    AtomicInteger currentAdIndexInAdGroupRef = new AtomicInteger();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            currentPositionMsRef.set(controller.getCurrentPosition());
            contentPositionMsRef.set(controller.getContentPosition());
            durationMsRef.set(controller.getDuration());
            bufferedPositionMsRef.set(controller.getBufferedPosition());
            bufferedPercentageRef.set(controller.getBufferedPercentage());
            totalBufferedDurationMsRef.set(controller.getTotalBufferedDuration());
            currentLiveOffsetMsRef.set(controller.getCurrentLiveOffset());
            contentDurationMsRef.set(controller.getContentDuration());
            contentBufferedPositionMsRef.set(controller.getContentBufferedPosition());
            isPlayingAdRef.set(controller.isPlayingAd());
            currentAdGroupIndexRef.set(controller.getCurrentAdGroupIndex());
            currentAdIndexInAdGroupRef.set(controller.getCurrentAdIndexInAdGroup());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    RemoteMockPlayer remoteMockPlayer = remoteSession.getMockPlayer();
    remoteMockPlayer.setCurrentPosition(testCurrentPositionMs);
    remoteMockPlayer.setContentPosition(testContentPositionMs);
    remoteMockPlayer.setDuration(testDurationMs);
    remoteMockPlayer.setBufferedPosition(testBufferedPositionMs);
    remoteMockPlayer.setBufferedPercentage(testBufferedPercentage);
    remoteMockPlayer.setTotalBufferedDuration(testTotalBufferedDurationMs);
    remoteMockPlayer.setCurrentLiveOffset(testCurrentLiveOffsetMs);
    remoteMockPlayer.setContentDuration(testContentDurationMs);
    remoteMockPlayer.setContentBufferedPosition(testContentBufferedPositionMs);
    remoteMockPlayer.setIsPlayingAd(testIsPlayingAd);
    remoteMockPlayer.setCurrentAdGroupIndex(testCurrentAdGroupIndex);
    remoteMockPlayer.setCurrentAdIndexInAdGroup(testCurrentAdIndexInAdGroup);
    remoteMockPlayer.notifyPositionDiscontinuity(
        /* oldPositionInfo= */ SessionPositionInfo.DEFAULT_POSITION_INFO,
        newPositionInfo,
        Player.DISCONTINUITY_REASON_INTERNAL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(currentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(contentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(durationMsRef.get()).isEqualTo(testDurationMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentDurationMsRef.get()).isEqualTo(testContentDurationMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(isPlayingAdRef.get()).isEqualTo(testIsPlayingAd);
    assertThat(currentAdGroupIndexRef.get()).isEqualTo(testCurrentAdGroupIndex);
    assertThat(currentAdIndexInAdGroupRef.get()).isEqualTo(testCurrentAdIndexInAdGroup);
  }

  /** This also tests {@link MediaController#getAvailableSessionCommands()}. */
  @Test
  public void onAvailableSessionCommandsChanged() throws Exception {
    SessionCommands commands =
        new SessionCommands.Builder()
            .add(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING))
            .build();

    CountDownLatch latch = new CountDownLatch(1);
    MediaController.ControllerCallback callback =
        new MediaController.ControllerCallback() {
          @Override
          public void onAvailableSessionCommandsChanged(
              MediaController controller, SessionCommands commandsOut) {
            assertThat(commandsOut).isEqualTo(commands);
            latch.countDown();
          }
        };

    MediaController controller =
        controllerTestRule.createController(remoteSession.getToken(), true, null, callback);
    remoteSession.setAvailableCommands(commands, Player.Commands.EMPTY);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controller.getAvailableSessionCommands()).isEqualTo(commands);
  }

  /** This also tests {@link MediaController#getAvailableCommands()}. */
  @Test
  public void onAvailableCommandsChanged_isCalledByPlayerChange() throws Exception {
    Commands commandsWithAllCommands = createPlayerCommandsWithAllCommands();
    Commands commandsWithSetRepeat = createPlayerCommandsWith(COMMAND_SET_REPEAT_MODE);

    remoteSession.getMockPlayer().notifyAvailableCommandsChanged(commandsWithAllCommands);
    MediaController controller =
        controllerTestRule.createController(remoteSession.getToken(), true, null, null);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Commands> availableCommandsRef = new AtomicReference<>();
    AtomicReference<Commands> availableCommandsFromGetterRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onAvailableCommandsChanged(Commands availableCommands) {
            availableCommandsRef.set(availableCommands);
            availableCommandsFromGetterRef.set(controller.getAvailableCommands());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().notifyAvailableCommandsChanged(commandsWithSetRepeat);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(availableCommandsRef.get()).isEqualTo(commandsWithSetRepeat);
    assertThat(availableCommandsFromGetterRef.get()).isEqualTo(commandsWithSetRepeat);
  }

  /** This also tests {@link MediaController#getAvailableCommands()}. */
  @Test
  public void onAvailableCommandsChanged_isCalledBySessionChange() throws Exception {
    Commands commandsWithAllCommands = createPlayerCommandsWithAllCommands();
    Commands commandsWithSetRepeat = createPlayerCommandsWith(COMMAND_SET_REPEAT_MODE);

    remoteSession.getMockPlayer().notifyAvailableCommandsChanged(commandsWithAllCommands);
    MediaController controller =
        controllerTestRule.createController(remoteSession.getToken(), true, null, null);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Commands> availableCommandsRef = new AtomicReference<>();
    AtomicReference<Commands> availableCommandsFromGetterRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onAvailableCommandsChanged(Commands availableCommands) {
            availableCommandsRef.set(availableCommands);
            availableCommandsFromGetterRef.set(controller.getAvailableCommands());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.setAvailableCommands(SessionCommands.EMPTY, commandsWithSetRepeat);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(availableCommandsRef.get()).isEqualTo(commandsWithSetRepeat);
    assertThat(availableCommandsFromGetterRef.get()).isEqualTo(commandsWithSetRepeat);
  }

  @Test
  public void onCustomCommand() throws Exception {
    String testCommandAction = "test_action";
    SessionCommand testCommand = new SessionCommand(testCommandAction, null);
    Bundle testArgs = TestUtils.createTestBundle();

    CountDownLatch latch = new CountDownLatch(2);
    MediaController.ControllerCallback callback =
        new MediaController.ControllerCallback() {
          @Override
          @NonNull
          public ListenableFuture<SessionResult> onCustomCommand(
              @NonNull MediaController controller, @NonNull SessionCommand command, Bundle args) {
            assertThat(command).isEqualTo(testCommand);
            assertThat(TestUtils.equals(testArgs, args)).isTrue();
            latch.countDown();
            return new SessionResult(RESULT_SUCCESS).asFuture();
          }
        };
    controllerTestRule.createController(
        remoteSession.getToken(),
        /* waitForConnect= */ true,
        /* connectionHints= */ null,
        callback);

    // TODO(jaewan): Test with multiple controllers
    remoteSession.broadcastCustomCommand(testCommand, testArgs);

    // TODO(jaewan): Test receivers as well.
    remoteSession.sendCustomCommand(testCommand, testArgs);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onCustomLayoutChanged() throws Exception {
    List<CommandButton> buttons = new ArrayList<>();

    CommandButton button =
        new CommandButton.Builder()
            .setPlayerCommand(COMMAND_PLAY_PAUSE)
            .setDisplayName("button")
            .build();
    buttons.add(button);

    CountDownLatch latch = new CountDownLatch(1);
    MediaController.ControllerCallback callback =
        new MediaController.ControllerCallback() {
          @Override
          @NonNull
          public ListenableFuture<SessionResult> onSetCustomLayout(
              @NonNull MediaController controller, @NonNull List<CommandButton> layout) {
            assertThat(layout).hasSize(buttons.size());
            for (int i = 0; i < layout.size(); i++) {
              assertThat(layout.get(i).playerCommand).isEqualTo(buttons.get(i).playerCommand);
              assertThat(layout.get(i).displayName.toString())
                  .isEqualTo(buttons.get(i).displayName.toString());
            }
            latch.countDown();
            return new SessionResult(RESULT_SUCCESS).asFuture();
          }
        };
    controllerTestRule.createController(
        remoteSession.getToken(),
        /* waitForConnect= */ true,
        /* connectionHints= */ null,
        callback);
    remoteSession.setCustomLayout(buttons);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void onVideoSizeChanged() throws Exception {
    VideoSize testVideoSize =
        new VideoSize(
            /* width= */ 100,
            /* height= */ 42,
            /* unappliedRotationDegrees= */ 90,
            /* pixelWidthHeightRatio= */ 1.2f);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<VideoSize> videoSizeFromParamRef = new AtomicReference<>();
    AtomicReference<VideoSize> videoSizeFromGetterRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
            videoSizeFromParamRef.set(videoSize);
            videoSizeFromGetterRef.set(controller.getVideoSize());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().notifyVideoSizeChanged(testVideoSize);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(videoSizeFromParamRef.get()).isEqualTo(testVideoSize);
    assertThat(videoSizeFromGetterRef.get()).isEqualTo(testVideoSize);
  }

  @Test
  public void onAudioAttributesChanged_isCalledAndUpdatesGetter() throws Exception {
    AudioAttributes testAttributes =
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_MOVIE)
            .build();

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<AudioAttributes> attributesRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onAudioAttributesChanged(@NonNull AudioAttributes attributes) {
            if (testAttributes.equals(attributes)) {
              attributesRef.set(controller.getAudioAttributes());
              latch.countDown();
            }
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().notifyAudioAttributesChanged(testAttributes);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(attributesRef.get()).isEqualTo(testAttributes);
  }

  @Test
  public void onDeviceInfoChanged_isCalledByPlayerChange() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<DeviceInfo> deviceInfoFromParamRef = new AtomicReference<>();
    AtomicReference<DeviceInfo> deviceInfoFromGetterRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onDeviceInfoChanged(@NonNull DeviceInfo deviceInfo) {
            deviceInfoFromParamRef.set(deviceInfo);
            deviceInfoFromGetterRef.set(controller.getDeviceInfo());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    DeviceInfo deviceInfo =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_REMOTE, /* minVolume= */ 0, /* maxVolume= */ 100);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceInfo(deviceInfo).build();
    remoteSession.setPlayer(playerConfig);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceInfoFromParamRef.get()).isEqualTo(deviceInfo);
    assertThat(deviceInfoFromGetterRef.get()).isEqualTo(deviceInfo);
  }

  @Test
  public void onDeviceInfoChanged_isCalledByDeviceInfoChange() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<DeviceInfo> deviceInfoFromParamRef = new AtomicReference<>();
    AtomicReference<DeviceInfo> deviceInfoFromGetterRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onDeviceInfoChanged(@NonNull DeviceInfo deviceInfo) {
            deviceInfoFromParamRef.set(deviceInfo);
            deviceInfoFromGetterRef.set(controller.getDeviceInfo());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    DeviceInfo deviceInfo =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_REMOTE, /* minVolume= */ 1, /* maxVolume= */ 23);
    remoteSession.getMockPlayer().notifyDeviceInfoChanged(deviceInfo);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceInfoFromParamRef.get()).isEqualTo(deviceInfo);
    assertThat(deviceInfoFromGetterRef.get()).isEqualTo(deviceInfo);
  }

  @Test
  public void onDeviceVolumeChanged_isCalledByDeviceVolumeChange() throws Exception {
    DeviceInfo deviceInfo =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_REMOTE, /* minVolume= */ 0, /* maxVolume= */ 100);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(23)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicInteger deviceVolumeFromParamRef = new AtomicInteger();
    AtomicInteger deviceVolumeFromGetterRef = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeFromParamRef.set(volume);
            deviceVolumeFromGetterRef.set(controller.getDeviceVolume());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    int targetVolume = 45;
    remoteSession.getMockPlayer().notifyDeviceVolumeChanged(targetVolume, /* muted= */ false);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceVolumeFromParamRef.get()).isEqualTo(targetVolume);
    assertThat(deviceVolumeFromGetterRef.get()).isEqualTo(targetVolume);
  }

  @Test
  public void onDeviceVolumeChanged_isCalledByDeviceMutedChange() throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceMuted(false).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicBoolean deviceMutedFromParamRef = new AtomicBoolean();
    AtomicBoolean deviceMutedFromGetterRef = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceMutedFromParamRef.set(muted);
            deviceMutedFromGetterRef.set(controller.isDeviceMuted());
            latch.countDown();
          }
        };
    controller.addListener(callback);

    remoteSession.getMockPlayer().notifyDeviceVolumeChanged(/* volume= */ 0, /* muted= */ true);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceMutedFromParamRef.get()).isTrue();
    assertThat(deviceMutedFromGetterRef.get()).isTrue();
  }

  @Test
  public void onEvents_whenOnRepeatModeChanges_isCalledAfterOtherCallbacks() throws Exception {
    Player.Events testEvents =
        new Player.Events(new ExoFlags.Builder().add(EVENT_REPEAT_MODE_CHANGED).build());
    CopyOnWriteArrayList<Integer> callbackEventCodes = new CopyOnWriteArrayList<>();

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
            callbackEventCodes.add(EVENT_REPEAT_MODE_CHANGED);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            callbackEventCodes.add(EVENT_ON_EVENTS);
            eventsRef.set(events);
            latch.countDown();
          }
        };
    controller.addListener(callback);
    remoteSession.getMockPlayer().setRepeatMode(REPEAT_MODE_ONE);
    remoteSession.getMockPlayer().notifyRepeatModeChanged();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(callbackEventCodes).containsExactly(EVENT_REPEAT_MODE_CHANGED, EVENT_ON_EVENTS);
    assertThat(eventsRef.get()).isEqualTo(testEvents);
  }

  // TODO(b/144387281): Move this into a separate test class for state masking.
  @Test
  public void setPlayWhenReady_stateMasking() throws Exception {
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
    AtomicBoolean playWhenReadyRef = new AtomicBoolean();
    AtomicInteger playbackSuppressionReasonRef = new AtomicInteger();
    AtomicBoolean isPlayingRef = new AtomicBoolean();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
            playWhenReadyRef.set(playWhenReady);
            latch.countDown();
          }

          @Override
          public void onPlaybackSuppressionReasonChanged(int playbackSuppressionReason) {
            playbackSuppressionReasonRef.set(playbackSuppressionReason);
            latch.countDown();
          }

          @Override
          public void onIsPlayingChanged(boolean isPlaying) {
            isPlayingRef.set(isPlaying);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setPlayWhenReady(testPlayWhenReady);
              assertThat(controller.getPlayWhenReady()).isEqualTo(testPlayWhenReady);
              assertThat(controller.getPlaybackSuppressionReason()).isEqualTo(testReason);
              assertThat(controller.isPlaying()).isEqualTo(testIsPlaying);
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playWhenReadyRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playbackSuppressionReasonRef.get()).isEqualTo(testReason);
    assertThat(isPlayingRef.get()).isEqualTo(testIsPlaying);
    assertThat(onEventsRef.get().contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)).isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED))
        .isTrue();
    assertThat(onEventsRef.get().contains(Player.EVENT_IS_PLAYING_CHANGED)).isTrue();
  }

  // TODO(b/144387281): Move this into a separate test class for state masking.
  @Test
  public void setShuffleModeEnabled_stateMasking() throws Exception {
    boolean testShuffleModeEnabled = true;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setShuffleModeEnabled(false).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicBoolean shuffleModeEnabledRef = new AtomicBoolean();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            shuffleModeEnabledRef.set(shuffleModeEnabled);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setShuffleModeEnabled(testShuffleModeEnabled);
              assertThat(controller.getShuffleModeEnabled()).isEqualTo(testShuffleModeEnabled);
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(shuffleModeEnabledRef.get()).isEqualTo(testShuffleModeEnabled);
    assertThat(onEventsRef.get().contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)).isTrue();
  }

  // TODO(b/144387281): Move this into a separate test class for state masking.
  @Test
  public void setRepeatMode_stateMasking() throws Exception {
    int testRepeatMode = REPEAT_MODE_ALL;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setRepeatMode(REPEAT_MODE_ONE).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger repeatModeRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onRepeatModeChanged(int repeatMode) {
            repeatModeRef.set(repeatMode);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            onEventsRef.set(events);
            latch.countDown();
          }
        };
    controller.addListener(callback);

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setRepeatMode(testRepeatMode);
              assertThat(controller.getRepeatMode()).isEqualTo(testRepeatMode);
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(repeatModeRef.get()).isEqualTo(testRepeatMode);
    assertThat(onEventsRef.get().contains(Player.EVENT_REPEAT_MODE_CHANGED)).isTrue();
  }

  private void testControllerAfterSessionIsClosed(@NonNull String id) throws Exception {
    // This cause session service to be died.
    remoteSession.release();
    controllerTestRule.waitForDisconnect(controller, true);
    testNoInteraction();

    // Ensure that the controller cannot use newly create session with the same ID.
    // Recreated session has different session stub, so previously created controller
    // shouldn't be available.
    remoteSession = createRemoteMediaSession(id);
    testNoInteraction();
  }

  // Test that session and controller doesn't interact.
  // Note that this method can be called after the session is died, so session may not have
  // valid player.
  private void testNoInteraction() throws Exception {
    // TODO: check that calls from the controller to session shouldn't be delivered.

    // Calls from the session to controller shouldn't be delivered.
    CountDownLatch latch = new CountDownLatch(1);
    controllerTestRule.setRunnableForOnCustomCommand(
        controller,
        new Runnable() {
          @Override
          public void run() {
            latch.countDown();
          }
        });
    SessionCommand customCommand = new SessionCommand("testNoInteraction", null);

    remoteSession.broadcastCustomCommand(customCommand, null);

    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
    controllerTestRule.setRunnableForOnCustomCommand(controller, null);
  }

  private RemoteMediaSession createRemoteMediaSession(@NonNull String id) throws RemoteException {
    return new RemoteMediaSession(id, context, /* tokenExtras= */ null);
  }
}
