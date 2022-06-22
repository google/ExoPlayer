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
package androidx.media3.session;

import static androidx.media3.common.Player.COMMAND_SET_REPEAT_MODE;
import static androidx.media3.common.Player.EVENT_REPEAT_MODE_CHANGED;
import static androidx.media3.common.Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED;
import static androidx.media3.common.Player.REPEAT_MODE_ALL;
import static androidx.media3.common.Player.REPEAT_MODE_ONE;
import static androidx.media3.common.Player.STATE_BUFFERING;
import static androidx.media3.session.MediaTestUtils.createTimeline;
import static androidx.media3.session.MediaUtils.createPlayerCommandsWith;
import static androidx.media3.session.MediaUtils.createPlayerCommandsWithout;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_SESSION_SET_RATING;
import static androidx.media3.session.SessionResult.RESULT_SUCCESS;
import static androidx.media3.test.session.common.CommonConstants.DEFAULT_TEST_NAME;
import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA3_LIBRARY_SERVICE;
import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA3_SESSION_SERVICE;
import static androidx.media3.test.session.common.MediaSessionConstants.KEY_CONTROLLER;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_CONTROLLER_LISTENER_SESSION_REJECTS;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_WITH_CUSTOM_COMMANDS;
import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.SpannedString;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.FlagSet;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Commands;
import androidx.media3.common.Player.DiscontinuityReason;
import androidx.media3.common.Player.PlayWhenReadyChangeReason;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Player.State;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.session.RemoteMediaSession.RemoteMockPlayer;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

/**
 * Tests for {@link MediaController.Listener}. It also tests {@link Player.Listener} passed by
 * {@link MediaController#addListener}.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerListenerTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private static final int EVENT_ON_EVENTS = C.INDEX_UNSET;

  final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaControllerListenerTest");
  final MediaControllerTestRule controllerTestRule = new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  Context context;
  private RemoteMediaSession remoteSession;
  private List<RemoteMediaSession> sessions;
  private MediaController controller;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    sessions = new CopyOnWriteArrayList<>();
    remoteSession = createRemoteMediaSession(DEFAULT_TEST_NAME);
  }

  @After
  public void cleanUp() {
    for (RemoteMediaSession session : sessions) {
      session.cleanUp();
    }
    sessions.clear();
  }

  @Test
  public void connection_sessionAccepts() throws Exception {
    // createController() waits until the controller becomes available.
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    assertThat(controller).isNotNull();
  }

  @Test
  public void connection_sessionRejects() throws Exception {
    RemoteMediaSession session = createRemoteMediaSession(TEST_CONTROLLER_LISTENER_SESSION_REJECTS);
    try {
      ExecutionException thrown =
          assertThrows(
              ExecutionException.class,
              () -> controllerTestRule.createController(session.getToken()));
      assertThat(thrown).hasCauseThat().isInstanceOf(SecurityException.class);
    } finally {
      session.cleanUp();
    }
  }

  @Test
  public void connection_toSessionService() throws Exception {
    SessionToken token = new SessionToken(context, MOCK_MEDIA3_SESSION_SERVICE);
    MediaController controller = controllerTestRule.createController(token);
    assertThat(controller).isNotNull();
  }

  @Test
  public void connection_toLibraryService() throws Exception {
    SessionToken token = new SessionToken(context, MOCK_MEDIA3_LIBRARY_SERVICE);
    MediaController controller = controllerTestRule.createController(token);
    assertThat(controller).isNotNull();
  }

  @Test
  public void connection_toSessionWithCompatToken() throws Exception {
    MediaController controller =
        controllerTestRule.createController(remoteSession.getCompatToken());
    assertThat(controller).isNotNull();
  }

  @Test
  public void connection_toReleasedSession() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    SessionToken token = remoteSession.getToken();
    remoteSession.release();
    ListenableFuture<MediaController> controllerFuture =
        new MediaController.Builder(context, token)
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .buildAsync();
    controllerFuture.addListener(() -> latch.countDown(), threadTestRule.getHandler()::post);
    latch.await(TIMEOUT_MS, MILLISECONDS);
    Assert.assertThrows(
        ExecutionException.class, () -> controllerFuture.get(/* timeout= */ 0, MILLISECONDS));
  }

  @Test
  public void connection_sessionReleased() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    controllerTestRule.createController(
        remoteSession.getToken(),
        /* connectionHints= */ null,
        new MediaController.Listener() {
          @Override
          public void onDisconnected(MediaController controller) {
            latch.countDown();
          }
        });
    remoteSession.release();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void connection_controllerReleased() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaController controller =
        controllerTestRule.createController(
            remoteSession.getToken(),
            /* connectionHints= */ null,
            new MediaController.Listener() {
              @Override
              public void onDisconnected(MediaController controller) {
                latch.countDown();
              }
            });
    threadTestRule.getHandler().postAndSync(controller::release);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
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

    controllerTestRule.setTimeoutMs(LONG_TIMEOUT_MS);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());

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
    PlaybackException testPlayerError =
        new PlaybackException(
            /* message= */ "test exception",
            /* cause= */ null,
            PlaybackException.ERROR_CODE_REMOTE_ERROR);

    AtomicReference<PlaybackException> playerErrorRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                controller.addListener(
                    new Player.Listener() {
                      @Override
                      public void onPlayerError(PlaybackException error) {
                        playerErrorRef.set(error);
                        latch.countDown();
                      }
                    }));
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
    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                controller.addListener(
                    new Player.Listener() {
                      @Override
                      public void onAudioAttributesChanged(AudioAttributes attributes) {
                        audioAttributesRef.set(attributes);
                        latch.countDown();
                      }

                      @Override
                      public void onPlaybackStateChanged(@State int playbackState) {
                        stateRef.set(playbackState);
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
                          PositionInfo oldPosition,
                          PositionInfo newPosition,
                          @DiscontinuityReason int reason) {
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
                    }));
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackState(testState)
            .setAudioAttributes(testAudioAttributes)
            .setTimeline(testTimeline)
            .setPlaylistMetadata(testPlaylistMetadata)
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
    int testMediaItemIndex = 1;
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
    AtomicInteger currentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger currentPeriodIndexRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                controller.addListener(
                    new Player.Listener() {
                      @Override
                      public void onPositionDiscontinuity(
                          PositionInfo oldPosition,
                          PositionInfo newPosition,
                          @DiscontinuityReason int reason) {
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
                        currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
                        currentPeriodIndexRef.set(controller.getCurrentPeriodIndex());
                        latch.countDown();
                      }
                    }));
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
            .setCurrentMediaItemIndex(testMediaItemIndex)
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
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testPeriodIndex);
  }

  @Test
  public void onMediaItemTransition() throws Exception {
    int currentIndex = 0;
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 5);
    remoteSession.getMockPlayer().setTimeline(timeline);
    remoteSession
        .getMockPlayer()
        .notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    remoteSession.getMockPlayer().setCurrentMediaItemIndex(currentIndex);
    remoteSession
        .getMockPlayer()
        .notifyMediaItemTransition(
            currentIndex, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);

    AtomicReference<MediaItem> mediaItemFromParamRef = new AtomicReference<>();
    AtomicReference<MediaItem> mediaItemFromGetterRef = new AtomicReference<>();
    AtomicInteger reasonRef = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(1);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                controller.addListener(
                    new Player.Listener() {
                      @Override
                      public void onMediaItemTransition(
                          @Nullable MediaItem mediaItem,
                          @Player.MediaItemTransitionReason int reason) {
                        mediaItemFromParamRef.set(mediaItem);
                        mediaItemFromGetterRef.set(controller.getCurrentMediaItem());
                        reasonRef.set(reason);
                        latch.countDown();
                      }
                    }));
    int testIndex = 3;
    int testReason = Player.MEDIA_ITEM_TRANSITION_REASON_SEEK;
    remoteSession.getMockPlayer().setCurrentMediaItemIndex(testIndex);
    remoteSession.getMockPlayer().notifyMediaItemTransition(testIndex, testReason);
    Timeline.Window window = new Timeline.Window();
    MediaItem currentMediaItem = timeline.getWindow(testIndex, window).mediaItem;

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaItemFromParamRef.get()).isEqualTo(currentMediaItem);
    assertThat(mediaItemFromGetterRef.get()).isEqualTo(currentMediaItem);
    assertThat(reasonRef.get()).isEqualTo(testReason);
  }

  @Test
  public void onMediaItemTransition_withNullMediaItem() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 1);
    remoteSession.getMockPlayer().setTimeline(timeline);
    remoteSession.getMockPlayer().setCurrentMediaItemIndex(0);
    remoteSession
        .getMockPlayer()
        .notifyMediaItemTransition(0, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);

    AtomicReference<MediaItem> mediaItemRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                controller.addListener(
                    new Player.Listener() {
                      @Override
                      public void onMediaItemTransition(
                          @Nullable MediaItem mediaItem,
                          @Player.MediaItemTransitionReason int reason) {
                        mediaItemRef.set(mediaItem);
                        latch.countDown();
                      }
                    }));
    remoteSession.getMockPlayer().setTimeline(Timeline.EMPTY);
    remoteSession
        .getMockPlayer()
        .notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            playbackParametersRef.set(playbackParameters);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                controller.addListener(
                    new Player.Listener() {
                      @Override
                      public void onPlaybackParametersChanged(
                          PlaybackParameters playbackParameters) {
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
                    }));
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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            reasonRef.set(reason);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineRef.set(timeline);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineRef.set(timeline);
            latch.countDown();
          }
        };
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaylistMetadataChanged(MediaMetadata metadata) {
            metadataFromParamRef.set(metadata);
            metadataFromGetterRef.set(controller.getPlaylistMetadata());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setTitle("title").build();
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    player.setPlaylistMetadata(playlistMetadata);
    player.notifyPlaylistMetadataChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(metadataFromParamRef.get()).isEqualTo(playlistMetadata);
    assertThat(metadataFromGetterRef.get()).isEqualTo(playlistMetadata);
  }

  /** This also tests {@link MediaController#getTrackSelectionParameters()}. */
  @Test
  public void onTrackSelectionParametersChanged() throws Exception {
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    player.setTrackSelectionParameters(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<TrackSelectionParameters> parametersFromParamRef = new AtomicReference<>();
    AtomicReference<TrackSelectionParameters> parametersFromGetterRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTrackSelectionParametersChanged(TrackSelectionParameters parameters) {
            parametersFromParamRef.set(parameters);
            parametersFromGetterRef.set(controller.getTrackSelectionParameters());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    TrackSelectionParameters parameters =
        TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
            .buildUpon()
            .setMaxAudioBitrate(100)
            .build();
    player.notifyTrackSelectionParametersChanged(parameters);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(parametersFromParamRef.get()).isEqualTo(parameters);
    assertThat(parametersFromGetterRef.get()).isEqualTo(parameters);
  }

  /** This also tests {@link MediaController#getShuffleModeEnabled()}. */
  @Test
  public void onShuffleModeEnabledChanged() throws Exception {
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    Timeline timeline =
        new PlaylistTimeline(
            MediaTestUtils.createMediaItems(/* size= */ 3),
            /* shuffledIndices= */ new int[] {0, 2, 1});
    player.setTimeline(timeline);
    player.notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    player.setCurrentMediaItemIndex(2);
    player.setShuffleModeEnabled(false);
    player.notifyShuffleModeEnabledChanged();

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean shuffleModeEnabledFromParamRef = new AtomicBoolean();
    AtomicBoolean shuffleModeEnabledFromGetterRef = new AtomicBoolean();
    AtomicInteger previousIndexRef = new AtomicInteger();
    AtomicInteger nextIndexRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            shuffleModeEnabledFromParamRef.set(shuffleModeEnabled);
            shuffleModeEnabledFromGetterRef.set(controller.getShuffleModeEnabled());
            previousIndexRef.set(controller.getPreviousMediaItemIndex());
            nextIndexRef.set(controller.getNextMediaItemIndex());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    player.setCurrentMediaItemIndex(2);
    player.setRepeatMode(Player.REPEAT_MODE_OFF);
    player.notifyRepeatModeChanged();

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger repeatModeFromParamRef = new AtomicInteger();
    AtomicInteger repeatModeFromGetterRef = new AtomicInteger();
    AtomicInteger previousIndexRef = new AtomicInteger();
    AtomicInteger nextIndexRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(@RepeatMode int repeatMode) {
            repeatModeFromParamRef.set(repeatMode);
            repeatModeFromGetterRef.set(controller.getRepeatMode());
            previousIndexRef.set(controller.getPreviousMediaItemIndex());
            nextIndexRef.set(controller.getNextMediaItemIndex());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    int testRepeatMode = REPEAT_MODE_ALL;
    player.setRepeatMode(testRepeatMode);
    player.notifyRepeatModeChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(repeatModeFromParamRef.get()).isEqualTo(testRepeatMode);
    assertThat(repeatModeFromGetterRef.get()).isEqualTo(testRepeatMode);
    assertThat(previousIndexRef.get()).isEqualTo(1);
    assertThat(nextIndexRef.get()).isEqualTo(0);
  }

  /** This also tests {@link MediaController#getSeekBackIncrement()}. */
  @Test
  public void onSeekBackIncrementChanged() throws Exception {
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    player.notifySeekBackIncrementChanged(1_000);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicLong incrementFromParamRef = new AtomicLong();
    AtomicLong incrementFromGetterRef = new AtomicLong();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onSeekBackIncrementChanged(long seekBackIncrementMs) {
            incrementFromParamRef.set(seekBackIncrementMs);
            incrementFromGetterRef.set(controller.getSeekBackIncrement());
            latch.countDown();
          }
        };
    controller.addListener(listener);

    int testSeekBackIncrementMs = 2_000;
    player.notifySeekBackIncrementChanged(testSeekBackIncrementMs);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(incrementFromParamRef.get()).isEqualTo(testSeekBackIncrementMs);
    assertThat(incrementFromGetterRef.get()).isEqualTo(testSeekBackIncrementMs);
  }

  /** This also tests {@link MediaController#getSeekForwardIncrement()}. */
  @Test
  public void onSeekForwardIncrementChanged() throws Exception {
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    player.notifySeekForwardIncrementChanged(1_000);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicLong incrementFromParamRef = new AtomicLong();
    AtomicLong incrementFromGetterRef = new AtomicLong();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onSeekForwardIncrementChanged(long seekForwardIncrementMs) {
            incrementFromParamRef.set(seekForwardIncrementMs);
            incrementFromGetterRef.set(controller.getSeekForwardIncrement());
            latch.countDown();
          }
        };
    controller.addListener(listener);

    int testSeekForwardIncrementMs = 2_000;
    player.notifySeekForwardIncrementChanged(testSeekForwardIncrementMs);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(incrementFromParamRef.get()).isEqualTo(testSeekForwardIncrementMs);
    assertThat(incrementFromGetterRef.get()).isEqualTo(testSeekForwardIncrementMs);
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
    Player.Listener listener =
        new Player.Listener() {
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
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                controller.addListener(
                    new Player.Listener() {
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
                    }));
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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackSuppressionReasonChanged(int reason) {
            playbackSuppressionReasonRef.set(reason);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    Player.Listener listener =
        new Player.Listener() {
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
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
            playbackStateRef.set(playbackState);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
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
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onIsPlayingChanged(boolean isPlaying) {
            isPlayingRef.set(isPlaying);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    Player.Listener listener =
        new Player.Listener() {
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
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onIsLoadingChanged(boolean isLoading) {
            isLoadingFromParamRef.set(isLoading);
            isLoadingFromGetterRef.set(controller.isLoading());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
            /* mediaItemIndex= */ 2,
            new MediaItem.Builder().setMediaId("media-id-2").build(),
            /* periodUid= */ null,
            /* periodIndex= */ C.INDEX_UNSET,
            /* positionMs= */ 300L,
            /* contentPositionMs= */ 200L,
            /* adGroupIndex= */ 33,
            /* adIndexInAdGroup= */ 2);
    PositionInfo testNewPosition =
        new PositionInfo(
            /* windowUid= */ null,
            /* mediaItemIndex= */ 3,
            new MediaItem.Builder().setMediaId("media-id-3").build(),
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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            oldPositionRef.set(oldPosition);
            newPositionRef.set(newPosition);
            positionDiscontinuityReasonRef.set(reason);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
            /* mediaItemIndex= */ C.INDEX_UNSET,
            /* mediaItem= */ null,
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
    Player.Listener listener =
        new Player.Listener() {
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
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    AtomicReference<SessionCommands> sessionCommandsFromParamRef = new AtomicReference<>();
    AtomicReference<SessionCommands> sessionCommandsFromGetterRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaController.Listener listener =
        new MediaController.Listener() {
          @Override
          public void onAvailableSessionCommandsChanged(
              MediaController controller, SessionCommands commands) {
            sessionCommandsFromParamRef.set(commands);
            sessionCommandsFromGetterRef.set(controller.getAvailableSessionCommands());
            latch.countDown();
          }
        };
    controllerTestRule.createController(
        remoteSession.getToken(), /* connectionHints= */ null, listener);

    SessionCommands commands =
        new SessionCommands.Builder()
            .addAllSessionCommands()
            .remove(COMMAND_CODE_SESSION_SET_RATING)
            .build();
    remoteSession.setAvailableCommands(commands, Player.Commands.EMPTY);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(sessionCommandsFromParamRef.get()).isEqualTo(commands);
    assertThat(sessionCommandsFromGetterRef.get()).isEqualTo(commands);
  }

  /** This also tests {@link MediaController#getAvailableCommands()}. */
  @Test
  public void onAvailableCommandsChanged_isCalledByPlayerChange() throws Exception {
    Commands commandsWithAllCommands = new Player.Commands.Builder().addAllCommands().build();
    remoteSession.getMockPlayer().notifyAvailableCommandsChanged(commandsWithAllCommands);
    MediaController controller =
        controllerTestRule.createController(
            remoteSession.getToken(), /* connectionHints= */ null, /* listener= */ null);

    AtomicReference<Commands> availableCommandsFromParamRef = new AtomicReference<>();
    AtomicReference<Commands> availableCommandsFromGetterRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onAvailableCommandsChanged(Commands availableCommands) {
            availableCommandsFromParamRef.set(availableCommands);
            availableCommandsFromGetterRef.set(controller.getAvailableCommands());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    Commands commandsWithSetRepeat = createPlayerCommandsWith(COMMAND_SET_REPEAT_MODE);
    remoteSession.getMockPlayer().notifyAvailableCommandsChanged(commandsWithSetRepeat);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(availableCommandsFromParamRef.get()).isEqualTo(commandsWithSetRepeat);
    assertThat(availableCommandsFromGetterRef.get()).isEqualTo(commandsWithSetRepeat);
  }

  @Test
  public void onTimelineChanged_emptyMediaItemAndMediaMetadata_whenCommandUnavailableFromPlayer()
      throws Exception {
    int testMediaItemsSize = 2;
    List<MediaItem> testMediaItemList = MediaTestUtils.createMediaItems(testMediaItemsSize);
    Timeline testTimeline = new PlaylistTimeline(testMediaItemList);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setTimeline(testTimeline).build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> metadataFromGetterRef = new AtomicReference<>();
    AtomicReference<MediaItem> currentMediaItemGetterRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            timelineFromParamRef.set(timeline);
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            metadataFromGetterRef.set(controller.getMediaMetadata());
            currentMediaItemGetterRef.set(controller.getCurrentMediaItem());
            latch.countDown();
          }
        };
    controller.addListener(listener);

    Commands commandsWithoutGetTimeline = createPlayerCommandsWithout(Player.COMMAND_GET_TIMELINE);
    remoteSession.getMockPlayer().notifyAvailableCommandsChanged(commandsWithoutGetTimeline);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(timelineFromParamRef.get().getWindowCount()).isEqualTo(testMediaItemsSize);
    for (int i = 0; i < timelineFromParamRef.get().getWindowCount(); i++) {
      assertThat(
              timelineFromParamRef
                  .get()
                  .getWindow(/* windowIndex= */ i, new Timeline.Window())
                  .mediaItem)
          .isEqualTo(MediaItem.EMPTY);
    }
    assertThat(timelineFromGetterRef.get().getWindowCount()).isEqualTo(testMediaItemsSize);
    for (int i = 0; i < timelineFromGetterRef.get().getWindowCount(); i++) {
      assertThat(
              timelineFromGetterRef
                  .get()
                  .getWindow(/* windowIndex= */ i, new Timeline.Window())
                  .mediaItem)
          .isEqualTo(MediaItem.EMPTY);
    }
    assertThat(metadataFromGetterRef.get()).isEqualTo(MediaMetadata.EMPTY);
    assertThat(currentMediaItemGetterRef.get()).isEqualTo(MediaItem.EMPTY);
  }

  @Test
  public void onTimelineChanged_emptyMediaItemAndMediaMetadata_whenCommandUnavailableFromSession()
      throws Exception {
    int testMediaItemsSize = 2;
    List<MediaItem> testMediaItemList = MediaTestUtils.createMediaItems(testMediaItemsSize);
    Timeline testTimeline = new PlaylistTimeline(testMediaItemList);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setTimeline(testTimeline).build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> metadataFromGetterRef = new AtomicReference<>();
    AtomicReference<MediaItem> currentMediaItemGetterRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            timelineFromParamRef.set(timeline);
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            metadataFromGetterRef.set(controller.getMediaMetadata());
            currentMediaItemGetterRef.set(controller.getCurrentMediaItem());
            latch.countDown();
          }
        };
    controller.addListener(listener);

    Commands commandsWithoutGetTimeline = createPlayerCommandsWithout(Player.COMMAND_GET_TIMELINE);
    remoteSession.setAvailableCommands(SessionCommands.EMPTY, commandsWithoutGetTimeline);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(timelineFromParamRef.get().getWindowCount()).isEqualTo(testMediaItemsSize);
    for (int i = 0; i < timelineFromParamRef.get().getWindowCount(); i++) {
      assertThat(
              timelineFromParamRef
                  .get()
                  .getWindow(/* windowIndex= */ i, new Timeline.Window())
                  .mediaItem)
          .isEqualTo(MediaItem.EMPTY);
    }
    assertThat(timelineFromGetterRef.get().getWindowCount()).isEqualTo(testMediaItemsSize);
    for (int i = 0; i < timelineFromGetterRef.get().getWindowCount(); i++) {
      assertThat(
              timelineFromGetterRef
                  .get()
                  .getWindow(/* windowIndex= */ i, new Timeline.Window())
                  .mediaItem)
          .isEqualTo(MediaItem.EMPTY);
    }
    assertThat(metadataFromGetterRef.get()).isEqualTo(MediaMetadata.EMPTY);
    assertThat(currentMediaItemGetterRef.get()).isEqualTo(MediaItem.EMPTY);
  }

  /** This also tests {@link MediaController#getAvailableCommands()}. */
  @Test
  public void onAvailableCommandsChanged_isCalledBySessionChange() throws Exception {
    Commands commandsWithAllCommands = new Player.Commands.Builder().addAllCommands().build();
    remoteSession.getMockPlayer().notifyAvailableCommandsChanged(commandsWithAllCommands);
    MediaController controller =
        controllerTestRule.createController(
            remoteSession.getToken(), /* connectionHints= */ null, /* listener= */ null);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Commands> availableCommandsFromParamRef = new AtomicReference<>();
    AtomicReference<Commands> availableCommandsFromGetterRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onAvailableCommandsChanged(Commands availableCommands) {
            availableCommandsFromParamRef.set(availableCommands);
            availableCommandsFromGetterRef.set(controller.getAvailableCommands());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    Commands commandsWithSetRepeat = createPlayerCommandsWith(COMMAND_SET_REPEAT_MODE);
    remoteSession.setAvailableCommands(SessionCommands.EMPTY, commandsWithSetRepeat);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(availableCommandsFromParamRef.get()).isEqualTo(commandsWithSetRepeat);
    assertThat(availableCommandsFromGetterRef.get()).isEqualTo(commandsWithSetRepeat);
  }

  @Test
  public void onCustomCommand() throws Exception {
    String testCommandAction = "test_action";
    SessionCommand testCommand = new SessionCommand(testCommandAction, /* extras= */ Bundle.EMPTY);
    Bundle testArgs = TestUtils.createTestBundle();

    CountDownLatch latch = new CountDownLatch(2);
    MediaController.Listener listener =
        new MediaController.Listener() {
          @Override
          public ListenableFuture<SessionResult> onCustomCommand(
              MediaController controller, SessionCommand command, Bundle args) {
            assertThat(command).isEqualTo(testCommand);
            assertThat(TestUtils.equals(testArgs, args)).isTrue();
            latch.countDown();
            return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
          }
        };
    controllerTestRule.createController(
        remoteSession.getToken(), /* connectionHints= */ null, listener);

    // TODO(jaewan): Test with multiple controllers
    remoteSession.broadcastCustomCommand(testCommand, testArgs);

    // TODO(jaewan): Test receivers as well.
    remoteSession.sendCustomCommand(testCommand, testArgs);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void setCustomLayout_onSetCustomLayoutCalled() throws Exception {
    List<CommandButton> buttons = new ArrayList<>();
    Bundle extras1 = new Bundle();
    extras1.putString("key", "value-1");
    CommandButton button1 =
        new CommandButton.Builder()
            .setSessionCommand(new SessionCommand("action1", extras1))
            .setDisplayName("actionName1")
            .setIconResId(1)
            .build();
    Bundle extras2 = new Bundle();
    extras2.putString("key", "value-2");
    CommandButton button2 =
        new CommandButton.Builder()
            .setSessionCommand(new SessionCommand("action2", extras2))
            .setDisplayName("actionName2")
            .setIconResId(2)
            .build();
    buttons.add(button1);
    buttons.add(button2);
    CountDownLatch latch = new CountDownLatch(1);
    List<String> receivedActions = new ArrayList<>();
    List<String> receivedDisplayNames = new ArrayList<>();
    List<String> receivedBundleValues = new ArrayList<>();
    List<Integer> receivedIconResIds = new ArrayList<>();
    List<Integer> receivedCommandCodes = new ArrayList<>();
    MediaController.Listener listener =
        new MediaController.Listener() {
          @Override
          public ListenableFuture<SessionResult> onSetCustomLayout(
              MediaController controller, List<CommandButton> layout) {
            for (CommandButton button : layout) {
              receivedActions.add(button.sessionCommand.customAction);
              receivedDisplayNames.add(String.valueOf(button.displayName));
              receivedBundleValues.add(button.sessionCommand.customExtras.getString("key"));
              receivedCommandCodes.add(button.sessionCommand.commandCode);
              receivedIconResIds.add(button.iconResId);
            }
            latch.countDown();
            return Futures.immediateFuture(new SessionResult(RESULT_SUCCESS));
          }
        };
    RemoteMediaSession session = createRemoteMediaSession(TEST_WITH_CUSTOM_COMMANDS);
    controllerTestRule.createController(session.getToken(), /* connectionHints= */ null, listener);

    session.setCustomLayout(buttons);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedActions).containsExactly("action1", "action2").inOrder();
    assertThat(receivedCommandCodes)
        .containsExactly(SessionCommand.COMMAND_CODE_CUSTOM, SessionCommand.COMMAND_CODE_CUSTOM)
        .inOrder();
    assertThat(receivedDisplayNames).containsExactly("actionName1", "actionName2").inOrder();
    assertThat(receivedIconResIds).containsExactly(1, 2).inOrder();
    assertThat(receivedBundleValues).containsExactly("value-1", "value-2").inOrder();
  }

  @Test
  public void setSessionExtras_onExtrasChangedCalled() throws Exception {
    Bundle sessionExtras = TestUtils.createTestBundle();
    sessionExtras.putString("key-0", "value-0");
    CountDownLatch latch = new CountDownLatch(1);
    List<Bundle> receivedSessionExtras = new ArrayList<>();
    MediaController.Listener listener =
        new MediaController.Listener() {
          @Override
          public void onExtrasChanged(MediaController controller, Bundle extras) {
            receivedSessionExtras.add(extras);
            latch.countDown();
          }
        };
    controllerTestRule.createController(
        remoteSession.getToken(), /* connectionHints= */ null, listener);

    remoteSession.setSessionExtras(sessionExtras);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedSessionExtras).hasSize(1);
    assertThat(TestUtils.equals(receivedSessionExtras.get(0), sessionExtras)).isTrue();
  }

  @Test
  public void setSessionExtras_specificMedia3Controller_onExtrasChangedCalled() throws Exception {
    Bundle sessionExtras = TestUtils.createTestBundle();
    sessionExtras.putString("key-0", "value-0");
    CountDownLatch latch = new CountDownLatch(1);
    List<Bundle> receivedSessionExtras = new ArrayList<>();
    MediaController.Listener listener =
        new MediaController.Listener() {
          @Override
          public void onExtrasChanged(MediaController controller, Bundle extras) {
            receivedSessionExtras.add(extras);
            latch.countDown();
          }
        };
    Bundle connectionHints = new Bundle();
    connectionHints.putString(KEY_CONTROLLER, "controller_key_1");
    controllerTestRule.createController(remoteSession.getToken(), connectionHints, listener);

    remoteSession.setSessionExtras("controller_key_1", sessionExtras);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedSessionExtras).hasSize(1);
    assertThat(TestUtils.equals(receivedSessionExtras.get(0), sessionExtras)).isTrue();
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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onVideoSizeChanged(VideoSize videoSize) {
            videoSizeFromParamRef.set(videoSize);
            videoSizeFromGetterRef.set(controller.getVideoSize());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build();

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<AudioAttributes> attributesRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onAudioAttributesChanged(AudioAttributes attributes) {
            if (testAttributes.equals(attributes)) {
              attributesRef.set(controller.getAudioAttributes());
              latch.countDown();
            }
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().notifyAudioAttributesChanged(testAttributes);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(attributesRef.get()).isEqualTo(testAttributes);
  }

  @Test
  public void getCurrentCues_afterConnected() throws Exception {
    Cue testCue1 = new Cue.Builder().setText(SpannedString.valueOf("cue1")).build();
    Cue testCue2 = new Cue.Builder().setText(SpannedString.valueOf("cue2")).build();
    List<Cue> testCues = ImmutableList.of(testCue1, testCue2);

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setCurrentCues(new CueGroup(testCues, /* presentationTimeUs= */ 1_230_000))
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());

    assertThat(threadTestRule.getHandler().postAndSync(controller::getCurrentCues).cues)
        .isEqualTo(testCues);
  }

  @Test
  public void onCues_emptyList_whenCommandUnavailable() throws Exception {
    Cue testCue1 = new Cue.Builder().setText(SpannedString.valueOf("cue1")).build();
    Cue testCue2 = new Cue.Builder().setText(SpannedString.valueOf("cue2")).build();
    List<Cue> testCues = ImmutableList.of(testCue1, testCue2);

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setCurrentCues(new CueGroup(testCues, /* presentationTimeUs= */ 1_230_000))
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    List<Cue> cuesFromGetter = new ArrayList<>();
    List<Cue> cuesFromParam = new ArrayList<>();

    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onCues(CueGroup cueGroup) {
            cuesFromParam.clear();
            cuesFromParam.addAll(cueGroup.cues);
            cuesFromGetter.clear();
            cuesFromGetter.addAll(controller.getCurrentCues().cues);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    Commands commandsWithoutGetText = createPlayerCommandsWithout(Player.COMMAND_GET_TEXT);
    remoteSession.setAvailableCommands(SessionCommands.EMPTY, commandsWithoutGetText);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(cuesFromParam).isEqualTo(ImmutableList.of());
    assertThat(cuesFromGetter).isEqualTo(ImmutableList.of());
  }

  @Test
  public void onCues_isCalledByPlayerChange() throws Exception {
    Cue testCue1 = new Cue.Builder().setText(SpannedString.valueOf("cue1")).build();
    Cue testCue2 = new Cue.Builder().setText(SpannedString.valueOf("cue2")).build();
    List<Cue> testCues = ImmutableList.of(testCue1, testCue2);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    List<Cue> cuesFromParam = new ArrayList<>();
    List<Cue> cuesFromGetter = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onCues(CueGroup cueGroup) {
            cuesFromParam.addAll(cueGroup.cues);
            cuesFromGetter.addAll(controller.getCurrentCues().cues);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setCurrentCues(new CueGroup(testCues, /* presentationTimeUs= */ 1_230_000))
            .build();
    remoteSession.setPlayer(playerConfig);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(cuesFromParam).isEqualTo(testCues);
    assertThat(cuesFromGetter).isEqualTo(testCues);
  }

  @Test
  public void onCues_isCalledByCuesChange() throws Exception {
    Cue testCue1 = new Cue.Builder().setText(SpannedString.valueOf("cue1")).build();
    Cue testCue2 = new Cue.Builder().setText(SpannedString.valueOf("cue2")).build();
    List<Cue> testCues = ImmutableList.of(testCue1, testCue2);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    List<Cue> cuesFromParam = new ArrayList<>();
    List<Cue> cuesFromGetter = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onCues(CueGroup cueGroup) {
            cuesFromParam.addAll(cueGroup.cues);
            cuesFromGetter.addAll(controller.getCurrentCues().cues);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession
        .getMockPlayer()
        .notifyCuesChanged(new CueGroup(testCues, /* presentationTimeUs= */ 1_230_000));

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(cuesFromParam).isEqualTo(testCues);
    assertThat(cuesFromGetter).isEqualTo(testCues);
  }

  @Test
  public void onDeviceInfoChanged_isCalledByPlayerChange() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<DeviceInfo> deviceInfoFromParamRef = new AtomicReference<>();
    AtomicReference<DeviceInfo> deviceInfoFromGetterRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceInfoChanged(DeviceInfo deviceInfo) {
            deviceInfoFromParamRef.set(deviceInfo);
            deviceInfoFromGetterRef.set(controller.getDeviceInfo());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceInfoChanged(DeviceInfo deviceInfo) {
            deviceInfoFromParamRef.set(deviceInfo);
            deviceInfoFromGetterRef.set(controller.getDeviceInfo());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeFromParamRef.set(volume);
            deviceVolumeFromGetterRef.set(controller.getDeviceVolume());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

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
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceMutedFromParamRef.set(muted);
            deviceMutedFromGetterRef.set(controller.isDeviceMuted());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().notifyDeviceVolumeChanged(/* volume= */ 0, /* muted= */ true);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceMutedFromParamRef.get()).isTrue();
    assertThat(deviceMutedFromGetterRef.get()).isTrue();
  }

  @Test
  public void onMaxSeekToPreviousPositionChanged_isCalled() throws Exception {
    long testMaxSeekToPreviousPositionMs = 100L;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setMaxSeekToPreviousPositionMs(30L)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicLong maxSeekToPreviousPositionMsFromParamRef = new AtomicLong();
    AtomicLong maxSeekToPreviousPositionMsFromGetterRef = new AtomicLong();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    controller.addListener(
        new Player.Listener() {
          @Override
          public void onMaxSeekToPreviousPositionChanged(long maxSeekToPreviousPositionMs) {
            maxSeekToPreviousPositionMsFromParamRef.set(maxSeekToPreviousPositionMs);
            maxSeekToPreviousPositionMsFromGetterRef.set(controller.getMaxSeekToPreviousPosition());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            latch.countDown();
          }
        });

    remoteSession
        .getMockPlayer()
        .notifyMaxSeekToPreviousPositionChanged(testMaxSeekToPreviousPositionMs);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(maxSeekToPreviousPositionMsFromParamRef.get())
        .isEqualTo(testMaxSeekToPreviousPositionMs);
    assertThat(maxSeekToPreviousPositionMsFromGetterRef.get())
        .isEqualTo(testMaxSeekToPreviousPositionMs);
    assertThat(eventsRef.get().contains(Player.EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED))
        .isTrue();
  }

  @Test
  public void onEvents_whenOnRepeatModeChanges_isCalledAfterOtherMethods() throws Exception {
    Player.Events testEvents =
        new Player.Events(new FlagSet.Builder().add(EVENT_REPEAT_MODE_CHANGED).build());
    List<Integer> listenerEventCodes = new ArrayList<>();

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
            listenerEventCodes.add(EVENT_REPEAT_MODE_CHANGED);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            listenerEventCodes.add(EVENT_ON_EVENTS);
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    remoteSession.getMockPlayer().setRepeatMode(REPEAT_MODE_ONE);
    remoteSession.getMockPlayer().notifyRepeatModeChanged();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(listenerEventCodes).containsExactly(EVENT_REPEAT_MODE_CHANGED, EVENT_ON_EVENTS);
    assertThat(eventsRef.get()).isEqualTo(testEvents);
  }

  @Test
  public void onEvents_whenNewCommandIsCalledInsideListener_containsEventFromNewCommand()
      throws Exception {
    Player.Events testEvents =
        new Player.Events(
            new FlagSet.Builder()
                .addAll(EVENT_REPEAT_MODE_CHANGED, EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
                .build());
    List<Integer> listenerEventCodes = new ArrayList<>();

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
            controller.setShuffleModeEnabled(true);
            listenerEventCodes.add(EVENT_REPEAT_MODE_CHANGED);
            latch.countDown();
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            listenerEventCodes.add(EVENT_SHUFFLE_MODE_ENABLED_CHANGED);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            listenerEventCodes.add(EVENT_ON_EVENTS);
            eventsRef.set(events);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    remoteSession.getMockPlayer().setRepeatMode(REPEAT_MODE_ONE);
    remoteSession.getMockPlayer().notifyRepeatModeChanged();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(listenerEventCodes)
        .containsExactly(
            EVENT_REPEAT_MODE_CHANGED, EVENT_SHUFFLE_MODE_ENABLED_CHANGED, EVENT_ON_EVENTS);
    assertThat(eventsRef.get()).isEqualTo(testEvents);
  }

  @Test
  public void onEvents_whenNewCommandIsCalledInsideOnEvents_isCalledFromNewLooperIterationSet()
      throws Exception {
    Player.Events firstLooperIterationSetTestEvents =
        new Player.Events(new FlagSet.Builder().add(EVENT_REPEAT_MODE_CHANGED).build());
    Player.Events secondLooperIterationSetTestEvents =
        new Player.Events(new FlagSet.Builder().add(EVENT_SHUFFLE_MODE_ENABLED_CHANGED).build());
    List<Integer> listenerEventCodes = new ArrayList<>();

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(4);
    AtomicReference<Player.Events> firstLooperIterationSetEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> secondLooperIterationSetEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
            listenerEventCodes.add(EVENT_REPEAT_MODE_CHANGED);
            latch.countDown();
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            listenerEventCodes.add(EVENT_SHUFFLE_MODE_ENABLED_CHANGED);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            listenerEventCodes.add(EVENT_ON_EVENTS);
            if (!controller.getShuffleModeEnabled()) {
              controller.setShuffleModeEnabled(true);
              firstLooperIterationSetEventsRef.set(events);
            } else {
              secondLooperIterationSetEventsRef.set(events);
            }
            latch.countDown();
          }
        };
    controller.addListener(listener);

    remoteSession.getMockPlayer().setRepeatMode(REPEAT_MODE_ONE);
    remoteSession.getMockPlayer().notifyRepeatModeChanged();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(listenerEventCodes)
        .containsExactly(
            EVENT_REPEAT_MODE_CHANGED,
            EVENT_ON_EVENTS,
            EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
            EVENT_ON_EVENTS);
    assertThat(firstLooperIterationSetEventsRef.get()).isEqualTo(firstLooperIterationSetTestEvents);
    assertThat(secondLooperIterationSetEventsRef.get())
        .isEqualTo(secondLooperIterationSetTestEvents);
  }

  @Test
  public void onMediaMetadataChanged_isNotifiedAndUpdatesGetter() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaMetadata> mediaMetadataFromParamRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> mediaMetadataFromGetterRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            mediaMetadataFromParamRef.set(mediaMetadata);
            mediaMetadataFromGetterRef.set(controller.getMediaMetadata());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    MediaMetadata testMediaMetadata = new MediaMetadata.Builder().setTitle("title").build();
    remoteSession.getMockPlayer().notifyMediaMetadataChanged(testMediaMetadata);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaMetadataFromParamRef.get()).isEqualTo(testMediaMetadata);
    assertThat(mediaMetadataFromGetterRef.get()).isEqualTo(testMediaMetadata);
  }

  @Test
  public void onMediaMetadataChanged_isCalledByPlayerChange() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<MediaMetadata> mediaMetadataFromParamRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> mediaMetadataFromGetterRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            mediaMetadataFromParamRef.set(mediaMetadata);
            mediaMetadataFromGetterRef.set(controller.getMediaMetadata());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    MediaMetadata testMediaMetadata = new MediaMetadata.Builder().setTitle("title").build();
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setMediaMetadata(testMediaMetadata)
            .build();
    remoteSession.setPlayer(playerConfig);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaMetadataFromParamRef.get()).isEqualTo(testMediaMetadata);
    assertThat(mediaMetadataFromGetterRef.get()).isEqualTo(testMediaMetadata);
  }

  /**
   * Session sends updated values to controller in a single class object, which will exclude the
   * timeline value if it has not been changed. Tests that the timeline value in controller is
   * preserved even when session sends updated values without the timeline value.
   */
  @Test
  public void timelineIsPreserved_whenUnrelatedListenerMethodIsCalled() throws Exception {
    Timeline testTimeline = createTimeline(1);

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setTimeline(testTimeline).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            latch.countDown();
          }
        };
    controller.addListener(listener);

    MediaMetadata testMediaMetadata = new MediaMetadata.Builder().setTitle("title").build();
    remoteSession.getMockPlayer().notifyMediaMetadataChanged(testMediaMetadata);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertMediaIdEquals(testTimeline, timelineFromGetterRef.get());
  }

  @Test
  public void onRenderedFirstFrame_isNotified() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRenderedFirstFrame() {
            latch.countDown();
          }
        };
    controller.addListener(listener);

    remoteSession.getMockPlayer().notifyRenderedFirstFrame();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  private void testControllerAfterSessionIsClosed(String id) throws Exception {
    // This cause session service to be died.
    remoteSession.release();
    // controllerTestRule.waitForDisconnect(controller, true);
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
    SessionCommand customCommand =
        new SessionCommand("testNoInteraction", /* extras= */ Bundle.EMPTY);

    remoteSession.broadcastCustomCommand(customCommand, /* args= */ Bundle.EMPTY);

    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
    controllerTestRule.setRunnableForOnCustomCommand(controller, null);
  }

  private RemoteMediaSession createRemoteMediaSession(String id) throws RemoteException {
    RemoteMediaSession session = new RemoteMediaSession(id, context, /* tokenExtras= */ null);
    sessions.add(session);
    return session;
  }
}
