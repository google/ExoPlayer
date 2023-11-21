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

import static androidx.media3.session.MediaTestUtils.createTimeline;
import static androidx.media3.session.MediaUtils.createPlayerCommandsWith;
import static androidx.media3.session.MediaUtils.createPlayerCommandsWithout;
import static androidx.media3.test.session.common.CommonConstants.DEFAULT_TEST_NAME;
import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA3_LIBRARY_SERVICE;
import static androidx.media3.test.session.common.CommonConstants.MOCK_MEDIA3_SESSION_SERVICE;
import static androidx.media3.test.session.common.MediaSessionConstants.KEY_COMMAND_GET_TASKS_UNAVAILABLE;
import static androidx.media3.test.session.common.MediaSessionConstants.KEY_CONTROLLER;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_COMMAND_GET_TRACKS;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_CONTROLLER_LISTENER_SESSION_REJECTS;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_ON_VIDEO_SIZE_CHANGED;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_WITH_CUSTOM_COMMANDS;
import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.getEventsAsList;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertThrows;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.SpannedString;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.Commands;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.session.RemoteMediaSession.RemoteMockPlayer;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.SurfaceActivity;
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

  final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaControllerListenerTest");
  final MediaControllerTestRule controllerTestRule = new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  Context context;
  private RemoteMediaSession remoteSession;
  private List<RemoteMediaSession> sessions;

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
  public void connection_correctVersions() throws Exception {
    SessionToken token = new SessionToken(context, MOCK_MEDIA3_SESSION_SERVICE);

    MediaController controller = controllerTestRule.createController(token);

    assertThat(controller.getConnectedToken().getInterfaceVersion())
        .isEqualTo(MediaSessionStub.VERSION_INT);
    assertThat(controller.getConnectedToken().getSessionVersion())
        .isEqualTo(MediaLibraryInfo.VERSION_INT);
  }

  @Test
  @LargeTest
  public void noInteractionAfterSessionClose_session() throws Exception {
    SessionToken token = remoteSession.getToken();
    MediaController controller = controllerTestRule.createController(token);
    testControllerAfterSessionIsClosed(DEFAULT_TEST_NAME);
  }

  @Test
  @LargeTest
  public void noInteractionAfterControllerClose_session() throws Exception {
    SessionToken token = remoteSession.getToken();
    MediaController controller = controllerTestRule.createController(token);

    threadTestRule.getHandler().postAndSync(controller::release);
    // release is done immediately for session.
    assertNoInteraction(controller);

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
    AtomicReference<PlaybackException> playerErrorParamRef = new AtomicReference<>();
    AtomicReference<PlaybackException> playerErrorGetterRef = new AtomicReference<>();
    AtomicReference<PlaybackException> playerErrorOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                controller.addListener(
                    new Player.Listener() {
                      @Override
                      public void onPlayerError(PlaybackException error) {
                        playerErrorParamRef.set(error);
                        playerErrorGetterRef.set(controller.getPlayerError());
                        latch.countDown();
                      }

                      @Override
                      public void onEvents(Player player, Player.Events events) {
                        eventsRef.set(events);
                        playerErrorOnEventsRef.set(player.getPlayerError());
                        latch.countDown();
                      }
                    }));

    remoteSession.getMockPlayer().notifyPlayerError(testPlayerError);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(TestUtils.equals(playerErrorParamRef.get(), testPlayerError)).isTrue();
    assertThat(TestUtils.equals(playerErrorGetterRef.get(), testPlayerError)).isTrue();
    assertThat(TestUtils.equals(playerErrorOnEventsRef.get(), testPlayerError)).isTrue();
    assertThat(getEventsAsList(eventsRef.get())).containsExactly(Player.EVENT_PLAYER_ERROR);
  }

  @Test
  public void setPlayer_notifiesChangedValues() throws Exception {
    @Player.State int testState = Player.STATE_BUFFERING;
    Timeline testTimeline = MediaTestUtils.createTimeline(/* windowCount= */ 3);
    MediaMetadata testPlaylistMetadata = new MediaMetadata.Builder().setTitle("title").build();
    AudioAttributes testAudioAttributes =
        LegacyConversions.convertToAudioAttributes(
            new AudioAttributesCompat.Builder()
                .setLegacyStreamType(AudioManager.STREAM_RING)
                .build());
    boolean testShuffleModeEnabled = true;
    @Player.RepeatMode int testRepeatMode = Player.REPEAT_MODE_ALL;
    int testCurrentAdGroupIndex = 33;
    int testCurrentAdIndexInAdGroup = 11;
    Commands testCommands =
        new Commands.Builder().addAllCommands().remove(Player.COMMAND_STOP).build();
    AtomicInteger stateRef = new AtomicInteger();
    AtomicReference<Timeline> timelineRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> playlistMetadataRef = new AtomicReference<>();
    AtomicReference<AudioAttributes> audioAttributesRef = new AtomicReference<>();
    AtomicBoolean isPlayingAdRef = new AtomicBoolean();
    AtomicInteger currentAdGroupIndexRef = new AtomicInteger();
    AtomicInteger currentAdIndexInAdGroupRef = new AtomicInteger();
    AtomicBoolean shuffleModeEnabledRef = new AtomicBoolean();
    AtomicInteger repeatModeRef = new AtomicInteger();
    AtomicReference<Commands> commandsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(8);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                controller.addListener(
                    new Player.Listener() {
                      @Override
                      public void onAvailableCommandsChanged(Commands availableCommands) {
                        commandsRef.set(availableCommands);
                        latch.countDown();
                      }

                      @Override
                      public void onAudioAttributesChanged(AudioAttributes attributes) {
                        audioAttributesRef.set(attributes);
                        latch.countDown();
                      }

                      @Override
                      public void onPlaybackStateChanged(@Player.State int playbackState) {
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
                          @Player.DiscontinuityReason int reason) {
                        isPlayingAdRef.set(controller.isPlayingAd());
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
                      public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
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
            .setIsPlayingAd(true)
            .setCurrentAdGroupIndex(testCurrentAdGroupIndex)
            .setCurrentAdIndexInAdGroup(testCurrentAdIndexInAdGroup)
            .setAvailableCommands(testCommands)
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
    assertThat(commandsRef.get()).isEqualTo(testCommands);
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
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
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
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    AtomicLong onEventsCurrentPositionMsRef = new AtomicLong();
    AtomicLong onEventsContentPositionMsRef = new AtomicLong();
    AtomicLong onEventsDurationMsRef = new AtomicLong();
    AtomicLong onEventsBufferedPositionMsRef = new AtomicLong();
    AtomicInteger onEventsBufferedPercentageRef = new AtomicInteger();
    AtomicLong onEventsTotalBufferedDurationMsRef = new AtomicLong();
    AtomicLong onEventsCurrentLiveOffsetMsRef = new AtomicLong();
    AtomicLong onEventsContentDurationMsRef = new AtomicLong();
    AtomicLong onEventsContentBufferedPositionMsRef = new AtomicLong();
    AtomicBoolean onEventsIsPlayingAdRef = new AtomicBoolean();
    AtomicInteger onEventsCurrentAdGroupIndexRef = new AtomicInteger();
    AtomicInteger onEventsCurrentAdIndexInAdGroupRef = new AtomicInteger();
    AtomicInteger onEventsCurrentMediaItemIndexRef = new AtomicInteger();
    AtomicInteger onEventsCurrentPeriodIndexRef = new AtomicInteger();
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
                          @Player.DiscontinuityReason int reason) {
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

                      @Override
                      public void onEvents(Player player, Player.Events events) {
                        eventsRef.set(events);
                        onEventsCurrentPositionMsRef.set(player.getCurrentPosition());
                        onEventsContentPositionMsRef.set(player.getContentPosition());
                        onEventsDurationMsRef.set(player.getDuration());
                        onEventsBufferedPositionMsRef.set(player.getBufferedPosition());
                        onEventsBufferedPercentageRef.set(player.getBufferedPercentage());
                        onEventsTotalBufferedDurationMsRef.set(player.getTotalBufferedDuration());
                        onEventsCurrentLiveOffsetMsRef.set(player.getCurrentLiveOffset());
                        onEventsContentDurationMsRef.set(player.getContentDuration());
                        onEventsContentBufferedPositionMsRef.set(
                            player.getContentBufferedPosition());
                        onEventsIsPlayingAdRef.set(player.isPlayingAd());
                        onEventsCurrentAdGroupIndexRef.set(player.getCurrentAdGroupIndex());
                        onEventsCurrentAdIndexInAdGroupRef.set(player.getCurrentAdIndexInAdGroup());
                        onEventsCurrentMediaItemIndexRef.set(player.getCurrentMediaItemIndex());
                        onEventsCurrentPeriodIndexRef.set(player.getCurrentPeriodIndex());
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
    assertThat(onEventsCurrentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(contentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(onEventsContentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(durationMsRef.get()).isEqualTo(testDurationMs);
    assertThat(onEventsDurationMsRef.get()).isEqualTo(testDurationMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(onEventsBufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(onEventsBufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(onEventsTotalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(onEventsCurrentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentDurationMsRef.get()).isEqualTo(testContentDurationMs);
    assertThat(onEventsContentDurationMsRef.get()).isEqualTo(testContentDurationMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(onEventsContentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(isPlayingAdRef.get()).isEqualTo(testIsPlayingAd);
    assertThat(onEventsIsPlayingAdRef.get()).isEqualTo(testIsPlayingAd);
    assertThat(currentAdGroupIndexRef.get()).isEqualTo(testCurrentAdGroupIndex);
    assertThat(onEventsCurrentAdGroupIndexRef.get()).isEqualTo(testCurrentAdGroupIndex);
    assertThat(currentAdIndexInAdGroupRef.get()).isEqualTo(testCurrentAdIndexInAdGroup);
    assertThat(onEventsCurrentAdIndexInAdGroupRef.get()).isEqualTo(testCurrentAdIndexInAdGroup);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
    assertThat(onEventsCurrentMediaItemIndexRef.get()).isEqualTo(testMediaItemIndex);
    assertThat(currentPeriodIndexRef.get()).isEqualTo(testPeriodIndex);
    assertThat(onEventsCurrentPeriodIndexRef.get()).isEqualTo(testPeriodIndex);
    assertThat(getEventsAsList(eventsRef.get())).contains(Player.EVENT_POSITION_DISCONTINUITY);
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
    AtomicReference<MediaItem> mediaItemFromOnEventsRef = new AtomicReference<>();
    AtomicInteger reasonRef = new AtomicInteger();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
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

                      @Override
                      public void onEvents(Player player, Player.Events events) {
                        eventsRef.set(events);
                        mediaItemFromOnEventsRef.set(player.getCurrentMediaItem());
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
    assertThat(mediaItemFromOnEventsRef.get()).isEqualTo(currentMediaItem);
    assertThat(reasonRef.get()).isEqualTo(testReason);
    assertThat(getEventsAsList(eventsRef.get())).contains(Player.EVENT_MEDIA_ITEM_TRANSITION);
  }

  @Test
  public void onMediaItemTransition_withNullMediaItem() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 1);
    remoteSession.getMockPlayer().setTimeline(timeline);
    remoteSession.getMockPlayer().setCurrentMediaItemIndex(0);
    remoteSession
        .getMockPlayer()
        .notifyMediaItemTransition(0, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
    AtomicReference<MediaItem> mediaItemFromParamRef = new AtomicReference<>();
    AtomicReference<MediaItem> mediaItemFromGetterRef = new AtomicReference<>();
    AtomicReference<MediaItem> mediaItemOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
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
                        latch.countDown();
                      }

                      @Override
                      public void onEvents(Player player, Player.Events events) {
                        eventsRef.set(events);
                        mediaItemOnEventsRef.set(player.getCurrentMediaItem());
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
    assertThat(mediaItemFromParamRef.get()).isNull();
    assertThat(mediaItemFromGetterRef.get()).isNull();
    assertThat(mediaItemOnEventsRef.get()).isNull();
    assertThat(getEventsAsList(eventsRef.get())).contains(Player.EVENT_MEDIA_ITEM_TRANSITION);
  }

  /** This also tests {@link MediaController#getPlaybackParameters()}. */
  @Test
  public void onPlaybackParametersChanged_isNotified() throws Exception {
    PlaybackParameters testPlaybackParameters =
        new PlaybackParameters(/* speed= */ 3.2f, /* pitch= */ 2.1f);
    remoteSession.getMockPlayer().setPlaybackParameters(PlaybackParameters.DEFAULT);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<PlaybackParameters> playbackParametersFromParamRef = new AtomicReference<>();
    AtomicReference<PlaybackParameters> playbackParametersFromGetterRef = new AtomicReference<>();
    AtomicReference<PlaybackParameters> playbackParametersFromOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            playbackParametersFromParamRef.set(playbackParameters);
            playbackParametersFromGetterRef.set(controller.getPlaybackParameters());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            playbackParametersFromOnEventsRef.set(controller.getPlaybackParameters());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().notifyPlaybackParametersChanged(testPlaybackParameters);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackParametersFromParamRef.get()).isEqualTo(testPlaybackParameters);
    assertThat(playbackParametersFromGetterRef.get()).isEqualTo(testPlaybackParameters);
    assertThat(playbackParametersFromOnEventsRef.get()).isEqualTo(testPlaybackParameters);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED);
  }

  @Test
  public void onPlaybackParametersChanged_updatesGetters() throws Exception {
    PlaybackParameters testPlaybackParameters =
        new PlaybackParameters(/* speed= */ 3.2f, /* pitch= */ 2.1f);
    long testCurrentPositionMs = 11;
    long testContentPositionMs = testCurrentPositionMs; // Not playing an ad
    long testBufferedPositionMs = 100;
    int testBufferedPercentage = 50;
    long testTotalBufferedDurationMs = 120;
    long testCurrentLiveOffsetMs = 10;
    long testContentBufferedPositionMs = 240;
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<PlaybackParameters> playbackParametersRef = new AtomicReference<>();
    AtomicLong currentPositionMsRef = new AtomicLong();
    AtomicLong contentPositionMsRef = new AtomicLong();
    AtomicLong bufferedPositionMsRef = new AtomicLong();
    AtomicInteger bufferedPercentageRef = new AtomicInteger();
    AtomicLong totalBufferedDurationMsRef = new AtomicLong();
    AtomicLong currentLiveOffsetMsRef = new AtomicLong();
    AtomicLong contentBufferedPositionMsRef = new AtomicLong();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    AtomicReference<PlaybackParameters> onEventsPlaybackParametersRef = new AtomicReference<>();
    AtomicLong onEventsCurrentPositionMsRef = new AtomicLong();
    AtomicLong onEventsContentPositionMsRef = new AtomicLong();
    AtomicLong onEventsBufferedPositionMsRef = new AtomicLong();
    AtomicInteger onEventsBufferedPercentageRef = new AtomicInteger();
    AtomicLong onEventsTotalBufferedDurationMsRef = new AtomicLong();
    AtomicLong onEventsCurrentLiveOffsetMsRef = new AtomicLong();
    AtomicLong onEventsContentBufferedPositionMsRef = new AtomicLong();
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

                      @Override
                      public void onEvents(Player player, Player.Events events) {
                        eventsRef.set(events);
                        onEventsPlaybackParametersRef.set(player.getPlaybackParameters());
                        onEventsCurrentPositionMsRef.set(player.getCurrentPosition());
                        onEventsContentPositionMsRef.set(player.getContentPosition());
                        onEventsBufferedPositionMsRef.set(player.getBufferedPosition());
                        onEventsBufferedPercentageRef.set(player.getBufferedPercentage());
                        onEventsTotalBufferedDurationMsRef.set(player.getTotalBufferedDuration());
                        onEventsCurrentLiveOffsetMsRef.set(player.getCurrentLiveOffset());
                        onEventsContentBufferedPositionMsRef.set(
                            player.getContentBufferedPosition());
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
    assertThat(onEventsPlaybackParametersRef.get()).isEqualTo(testPlaybackParameters);
    assertThat(currentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(onEventsCurrentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(contentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(onEventsContentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(onEventsBufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(onEventsBufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(onEventsTotalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(onEventsCurrentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(onEventsContentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED);
  }

  /** This also tests {@link MediaController#getCurrentTimeline()}. */
  @Test
  public void onTimelineChanged() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromOnEventsRef = new AtomicReference<>();
    AtomicInteger reasonRef = new AtomicInteger();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
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

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            timelineFromOnEventsRef.set(player.getCurrentTimeline());
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
    MediaTestUtils.assertMediaIdEquals(timeline, timelineFromOnEventsRef.get());
    assertThat(reasonRef.get()).isEqualTo(reason);
    assertThat(getEventsAsList(eventsRef.get())).contains(Player.EVENT_TIMELINE_CHANGED);
  }

  @Test
  @LargeTest
  public void onTimelineChanged_withLongPlaylist() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            timelineFromOnEventsRef.set(player.getCurrentTimeline());
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
    assertThat(timelineFromParamRef.get().getWindowCount()).isEqualTo(windowCount);
    assertThat(timelineFromGetterRef.get().getWindowCount()).isEqualTo(windowCount);
    assertThat(timelineFromOnEventsRef.get().getWindowCount()).isEqualTo(windowCount);
    Timeline.Window window = new Timeline.Window();
    for (int i = 0; i < windowCount; i++) {
      assertThat(timelineFromParamRef.get().getWindow(i, window).mediaItem.mediaId)
          .isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
      assertThat(timelineFromGetterRef.get().getWindow(i, window).mediaItem.mediaId)
          .isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
      assertThat(timelineFromOnEventsRef.get().getWindow(i, window).mediaItem.mediaId)
          .isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
    assertThat(getEventsAsList(eventsRef.get())).contains(Player.EVENT_TIMELINE_CHANGED);
  }

  @Test
  public void onTimelineChanged_withEmptyTimeline() throws Exception {
    remoteSession.getMockPlayer().createAndSetFakeTimeline(/* windowCount= */ 1);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            timelineFromOnEventsRef.set(player.getCurrentTimeline());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().setTimeline(Timeline.EMPTY);
    remoteSession
        .getMockPlayer()
        .notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(timelineFromParamRef.get().getWindowCount()).isEqualTo(0);
    assertThat(timelineFromParamRef.get().getPeriodCount()).isEqualTo(0);
    assertThat(timelineFromGetterRef.get().getWindowCount()).isEqualTo(0);
    assertThat(timelineFromGetterRef.get().getPeriodCount()).isEqualTo(0);
    assertThat(timelineFromOnEventsRef.get().getWindowCount()).isEqualTo(0);
    assertThat(timelineFromOnEventsRef.get().getPeriodCount()).isEqualTo(0);
    assertThat(getEventsAsList(eventsRef.get())).contains(Player.EVENT_TIMELINE_CHANGED);
  }

  /** This also tests {@link MediaController#getPlaylistMetadata()}. */
  @Test
  public void onPlaylistMetadataChanged() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<MediaMetadata> metadataFromParamRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> metadataFromGetterRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> metadataFromOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaylistMetadataChanged(MediaMetadata metadata) {
            metadataFromParamRef.set(metadata);
            metadataFromGetterRef.set(controller.getPlaylistMetadata());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            metadataFromOnEventsRef.set(player.getPlaylistMetadata());
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
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_PLAYLIST_METADATA_CHANGED);
  }

  /** This also tests {@link MediaController#getTrackSelectionParameters()}. */
  @Test
  public void onTrackSelectionParametersChanged() throws Exception {
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    player.setTrackSelectionParameters(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<TrackSelectionParameters> parametersFromParamRef = new AtomicReference<>();
    AtomicReference<TrackSelectionParameters> parametersFromGetterRef = new AtomicReference<>();
    AtomicReference<TrackSelectionParameters> parametersFromOnEventsrRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTrackSelectionParametersChanged(TrackSelectionParameters parameters) {
            parametersFromParamRef.set(parameters);
            parametersFromGetterRef.set(controller.getTrackSelectionParameters());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            parametersFromOnEventsrRef.set(controller.getTrackSelectionParameters());
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
    assertThat(parametersFromOnEventsrRef.get()).isEqualTo(parameters);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED);
  }

  @Test
  public void onTracksChanged() throws Exception {
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    ImmutableList<Tracks.Group> trackGroups =
        ImmutableList.of(
            new Tracks.Group(
                new TrackGroup(new Format.Builder().setChannelCount(2).build()),
                /* adaptiveSupported= */ false,
                /* trackSupport= */ new int[1],
                /* trackSelected= */ new boolean[1]),
            new Tracks.Group(
                new TrackGroup(new Format.Builder().setHeight(1024).build()),
                /* adaptiveSupported= */ false,
                /* trackSupport= */ new int[1],
                /* trackSelected= */ new boolean[1]));
    Tracks currentTracks = new Tracks(trackGroups);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<Tracks> changedCurrentTracksFromParamRef = new AtomicReference<>();
    AtomicReference<Tracks> changedCurrentTracksFromGetterRef = new AtomicReference<>();
    List<Tracks> changedCurrentTracksFromOnEvents = new ArrayList<>();
    List<Player.Events> capturedEvents = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTracksChanged(Tracks currentTracks) {
            changedCurrentTracksFromParamRef.set(currentTracks);
            changedCurrentTracksFromGetterRef.set(controller.getCurrentTracks());
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            capturedEvents.add(events);
            changedCurrentTracksFromOnEvents.add(player.getCurrentTracks());
            latch.countDown();
          }
        };
    AtomicReference<Tracks> initialCurrentTracksRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              initialCurrentTracksRef.set(controller.getCurrentTracks());
              controller.addListener(listener);
            });

    player.notifyTracksChanged(currentTracks);
    player.notifyIsLoadingChanged(true);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(initialCurrentTracksRef.get()).isEqualTo(Tracks.EMPTY);
    assertThat(changedCurrentTracksFromParamRef.get().getGroups()).hasSize(2);
    assertThat(changedCurrentTracksFromGetterRef.get())
        .isEqualTo(changedCurrentTracksFromParamRef.get());
    assertThat(capturedEvents).hasSize(2);
    assertThat(getEventsAsList(capturedEvents.get(0))).containsExactly(Player.EVENT_TRACKS_CHANGED);
    assertThat(getEventsAsList(capturedEvents.get(1)))
        .containsExactly(Player.EVENT_IS_LOADING_CHANGED);
    assertThat(changedCurrentTracksFromOnEvents).hasSize(2);
    assertThat(changedCurrentTracksFromOnEvents.get(0).getGroups()).hasSize(2);
    assertThat(changedCurrentTracksFromOnEvents.get(1).getGroups()).hasSize(2);
    // Assert that an equal instance is not re-sent over the binder.
    assertThat(changedCurrentTracksFromOnEvents.get(0))
        .isSameInstanceAs(changedCurrentTracksFromOnEvents.get(1));
  }

  @Test
  public void getCurrentTracks_commandGetTracksUnavailable_currentTracksEmpty() throws Exception {
    RemoteMediaSession remoteSession = createRemoteMediaSession(TEST_COMMAND_GET_TRACKS);
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    CountDownLatch latch = new CountDownLatch(2);
    // A controller with the COMMAND_GET_TRACKS unavailable.
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(KEY_COMMAND_GET_TASKS_UNAVAILABLE, true);
    MediaController controller =
        controllerTestRule.createController(
            remoteSession.getToken(), connectionHints, /* listener= */ null);
    List<Tracks> capturedCurrentTracks = new ArrayList<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            capturedCurrentTracks.add(controller.getCurrentTracks());
            latch.countDown();
          }
        };
    // A controller with the COMMAND_GET_TRACKS available.
    MediaController controllerWithCommandAvailable =
        controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<Tracks> capturedCurrentTracksWithCommandAvailable = new AtomicReference<>();
    Player.Listener listenerWithCommandAvailable =
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            capturedCurrentTracksWithCommandAvailable.set(player.getCurrentTracks());
            latch.countDown();
          }
        };
    AtomicReference<Tracks> initialCurrentTracks = new AtomicReference<>();
    AtomicReference<Tracks> initialCurrentTracksWithCommandAvailable = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              initialCurrentTracks.set(controller.getCurrentTracks());
              initialCurrentTracksWithCommandAvailable.set(
                  controllerWithCommandAvailable.getCurrentTracks());
              controller.addListener(listener);
              controllerWithCommandAvailable.addListener(listenerWithCommandAvailable);
            });

    player.notifyIsLoadingChanged(true);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(initialCurrentTracks.get()).isEqualTo(Tracks.EMPTY);
    assertThat(capturedCurrentTracks).containsExactly(Tracks.EMPTY);
    assertThat(initialCurrentTracksWithCommandAvailable.get().getGroups()).hasSize(1);
    assertThat(capturedCurrentTracksWithCommandAvailable.get().getGroups()).hasSize(1);
    // Assert that an equal instance is not re-sent over the binder.
    assertThat(initialCurrentTracksWithCommandAvailable.get())
        .isSameInstanceAs(capturedCurrentTracksWithCommandAvailable.get());
  }

  @Test
  public void getCurrentTracks_commandGetTracksBecomesUnavailable_tracksResetToEmpty()
      throws Exception {
    RemoteMediaSession remoteSession = createRemoteMediaSession(TEST_COMMAND_GET_TRACKS);
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    CountDownLatch latch = new CountDownLatch(2);
    // A controller with the COMMAND_GET_TRACKS available.
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    List<Tracks> capturedCurrentTracks = new ArrayList<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onAvailableCommandsChanged(Commands availableCommands) {
            capturedCurrentTracks.add(controller.getCurrentTracks());
            latch.countDown();
          }

          @Override
          public void onTracksChanged(Tracks tracks) {
            // The track change as a result of the available command change is notified second.
            capturedCurrentTracks.add(controller.getCurrentTracks());
            latch.countDown();
          }
        };
    AtomicReference<Commands> availableCommands = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              availableCommands.set(controller.getAvailableCommands());
              controller.addListener(listener);
            });

    player.notifyAvailableCommandsChanged(
        availableCommands.get().buildUpon().remove(Player.COMMAND_GET_TRACKS).build());

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(capturedCurrentTracks).hasSize(2);
    assertThat(capturedCurrentTracks.get(0).getGroups()).hasSize(1);
    assertThat(capturedCurrentTracks.get(1)).isEqualTo(Tracks.EMPTY);
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
    CountDownLatch latch = new CountDownLatch(2);
    AtomicBoolean shuffleModeEnabledFromParamRef = new AtomicBoolean();
    AtomicBoolean shuffleModeEnabledFromGetterRef = new AtomicBoolean();
    AtomicInteger previousIndexRef = new AtomicInteger();
    AtomicInteger nextIndexRef = new AtomicInteger();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    AtomicBoolean onEventsShuffleModeEnabledRef = new AtomicBoolean();
    AtomicInteger onEventsPreviousIndexRef = new AtomicInteger();
    AtomicInteger onEventsNextIndexRef = new AtomicInteger();
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

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            onEventsShuffleModeEnabledRef.set(player.getShuffleModeEnabled());
            onEventsPreviousIndexRef.set(player.getPreviousMediaItemIndex());
            onEventsNextIndexRef.set(player.getNextMediaItemIndex());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    player.setShuffleModeEnabled(true);
    player.notifyShuffleModeEnabledChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(shuffleModeEnabledFromParamRef.get()).isTrue();
    assertThat(shuffleModeEnabledFromGetterRef.get()).isTrue();
    assertThat(onEventsShuffleModeEnabledRef.get()).isTrue();
    assertThat(previousIndexRef.get()).isEqualTo(0);
    assertThat(onEventsPreviousIndexRef.get()).isEqualTo(0);
    assertThat(nextIndexRef.get()).isEqualTo(1);
    assertThat(onEventsNextIndexRef.get()).isEqualTo(1);
    assertThat(getEventsAsList(eventsRef.get()))
        .contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED);
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
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger repeatModeFromParamRef = new AtomicInteger();
    AtomicInteger repeatModeFromGetterRef = new AtomicInteger();
    AtomicInteger previousIndexRef = new AtomicInteger();
    AtomicInteger nextIndexRef = new AtomicInteger();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    AtomicInteger onEventsRepeatModeRef = new AtomicInteger();
    AtomicInteger onEventsPreviousIndexRef = new AtomicInteger();
    AtomicInteger onEventsNextIndexRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
            repeatModeFromParamRef.set(repeatMode);
            repeatModeFromGetterRef.set(controller.getRepeatMode());
            previousIndexRef.set(controller.getPreviousMediaItemIndex());
            nextIndexRef.set(controller.getNextMediaItemIndex());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            onEventsRepeatModeRef.set(player.getRepeatMode());
            onEventsPreviousIndexRef.set(player.getPreviousMediaItemIndex());
            onEventsNextIndexRef.set(player.getNextMediaItemIndex());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    int testRepeatMode = Player.REPEAT_MODE_ALL;

    player.setRepeatMode(testRepeatMode);
    player.notifyRepeatModeChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(repeatModeFromParamRef.get()).isEqualTo(testRepeatMode);
    assertThat(repeatModeFromGetterRef.get()).isEqualTo(testRepeatMode);
    assertThat(onEventsRepeatModeRef.get()).isEqualTo(testRepeatMode);
    assertThat(previousIndexRef.get()).isEqualTo(1);
    assertThat(onEventsPreviousIndexRef.get()).isEqualTo(1);
    assertThat(nextIndexRef.get()).isEqualTo(0);
    assertThat(onEventsNextIndexRef.get()).isEqualTo(0);
    assertThat(getEventsAsList(eventsRef.get())).contains(Player.EVENT_REPEAT_MODE_CHANGED);
  }

  /** This also tests {@link MediaController#getSeekBackIncrement()}. */
  @Test
  public void onSeekBackIncrementChanged() throws Exception {
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    player.notifySeekBackIncrementChanged(1_000);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicLong incrementFromParamRef = new AtomicLong();
    AtomicLong incrementFromGetterRef = new AtomicLong();
    AtomicLong incrementFromOnEventsRef = new AtomicLong();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onSeekBackIncrementChanged(long seekBackIncrementMs) {
            incrementFromParamRef.set(seekBackIncrementMs);
            incrementFromGetterRef.set(controller.getSeekBackIncrement());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            incrementFromOnEventsRef.set(player.getSeekBackIncrement());
            latch.countDown();
          }
        };
    controller.addListener(listener);

    int testSeekBackIncrementMs = 2_000;
    player.notifySeekBackIncrementChanged(testSeekBackIncrementMs);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(incrementFromParamRef.get()).isEqualTo(testSeekBackIncrementMs);
    assertThat(incrementFromGetterRef.get()).isEqualTo(testSeekBackIncrementMs);
    assertThat(incrementFromOnEventsRef.get()).isEqualTo(testSeekBackIncrementMs);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_SEEK_BACK_INCREMENT_CHANGED);
  }

  /** This also tests {@link MediaController#getSeekForwardIncrement()}. */
  @Test
  public void onSeekForwardIncrementChanged() throws Exception {
    RemoteMediaSession.RemoteMockPlayer player = remoteSession.getMockPlayer();
    player.notifySeekForwardIncrementChanged(1_000);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicLong incrementFromParamRef = new AtomicLong();
    AtomicLong incrementFromGetterRef = new AtomicLong();
    AtomicLong incrementFromOnEventsRef = new AtomicLong();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onSeekForwardIncrementChanged(long seekForwardIncrementMs) {
            incrementFromParamRef.set(seekForwardIncrementMs);
            incrementFromGetterRef.set(controller.getSeekForwardIncrement());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            incrementFromOnEventsRef.set(player.getSeekForwardIncrement());
            latch.countDown();
          }
        };
    controller.addListener(listener);

    int testSeekForwardIncrementMs = 2_000;
    player.notifySeekForwardIncrementChanged(testSeekForwardIncrementMs);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(incrementFromParamRef.get()).isEqualTo(testSeekForwardIncrementMs);
    assertThat(incrementFromGetterRef.get()).isEqualTo(testSeekForwardIncrementMs);
    assertThat(incrementFromOnEventsRef.get()).isEqualTo(testSeekForwardIncrementMs);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_SEEK_FORWARD_INCREMENT_CHANGED);
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
    CountDownLatch latch = new CountDownLatch(3);
    AtomicBoolean playWhenReadyParamRef = new AtomicBoolean();
    AtomicBoolean playWhenReadyGetterRef = new AtomicBoolean();
    AtomicInteger playWhenReadyReasonParamRef = new AtomicInteger();
    AtomicInteger playbackSuppressionReasonParamRef = new AtomicInteger();
    AtomicInteger playbackSuppressionReasonGetterRef = new AtomicInteger();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    AtomicBoolean onEventsPlayWhenReadyRef = new AtomicBoolean();
    AtomicInteger onEventsPlaybackSuppressionReasonRef = new AtomicInteger();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            playWhenReadyParamRef.set(playWhenReady);
            playWhenReadyGetterRef.set(controller.getPlayWhenReady());
            playWhenReadyReasonParamRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onPlaybackSuppressionReasonChanged(int playbackSuppressionReason) {
            playbackSuppressionReasonParamRef.set(playbackSuppressionReason);
            playbackSuppressionReasonGetterRef.set(controller.getPlaybackSuppressionReason());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            onEventsPlayWhenReadyRef.set(player.getPlayWhenReady());
            onEventsPlaybackSuppressionReasonRef.set(player.getPlaybackSuppressionReason());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().notifyPlayWhenReadyChanged(testPlayWhenReady, testReason);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playWhenReadyParamRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playWhenReadyGetterRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(onEventsPlayWhenReadyRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playWhenReadyReasonParamRef.get()).isEqualTo(testReason);
    assertThat(playbackSuppressionReasonParamRef.get()).isEqualTo(testSuppressionReason);
    assertThat(playbackSuppressionReasonGetterRef.get()).isEqualTo(testSuppressionReason);
    assertThat(onEventsPlaybackSuppressionReasonRef.get()).isEqualTo(testSuppressionReason);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(
            Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED);
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
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicBoolean playWhenReadyRef = new AtomicBoolean();
    AtomicInteger playbackSuppressionReasonRef = new AtomicInteger();
    AtomicLong currentPositionMsRef = new AtomicLong();
    AtomicLong contentPositionMsRef = new AtomicLong();
    AtomicLong bufferedPositionMsRef = new AtomicLong();
    AtomicInteger bufferedPercentageRef = new AtomicInteger();
    AtomicLong totalBufferedDurationMsRef = new AtomicLong();
    AtomicLong currentLiveOffsetMsRef = new AtomicLong();
    AtomicLong contentBufferedPositionMsRef = new AtomicLong();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    AtomicBoolean onEventsPlayWhenReadyRef = new AtomicBoolean();
    AtomicInteger onEventsPlaybackSuppressionReasonRef = new AtomicInteger();
    AtomicLong onEventsCurrentPositionMsRef = new AtomicLong();
    AtomicLong onEventsContentPositionMsRef = new AtomicLong();
    AtomicLong onEventsBufferedPositionMsRef = new AtomicLong();
    AtomicInteger onEventsBufferedPercentageRef = new AtomicInteger();
    AtomicLong onEventsTotalBufferedDurationMsRef = new AtomicLong();
    AtomicLong onEventsCurrentLiveOffsetMsRef = new AtomicLong();
    AtomicLong onEventsContentBufferedPositionMsRef = new AtomicLong();
    threadTestRule
        .getHandler()
        .postAndSync(
            () ->
                controller.addListener(
                    new Player.Listener() {
                      @Override
                      public void onPlayWhenReadyChanged(
                          boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
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

                      @Override
                      public void onEvents(Player player, Player.Events events) {
                        eventsRef.set(events);
                        onEventsPlayWhenReadyRef.set(player.getPlayWhenReady());
                        onEventsPlaybackSuppressionReasonRef.set(
                            player.getPlaybackSuppressionReason());
                        onEventsCurrentPositionMsRef.set(player.getCurrentPosition());
                        onEventsContentPositionMsRef.set(player.getContentPosition());
                        onEventsBufferedPositionMsRef.set(player.getBufferedPosition());
                        onEventsBufferedPercentageRef.set(player.getBufferedPercentage());
                        onEventsTotalBufferedDurationMsRef.set(player.getTotalBufferedDuration());
                        onEventsCurrentLiveOffsetMsRef.set(player.getCurrentLiveOffset());
                        onEventsContentBufferedPositionMsRef.set(
                            player.getContentBufferedPosition());
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
    assertThat(onEventsPlayWhenReadyRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(playbackSuppressionReasonRef.get()).isEqualTo(testReason);
    assertThat(onEventsPlaybackSuppressionReasonRef.get()).isEqualTo(testReason);
    assertThat(currentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(onEventsCurrentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(onEventsBufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(onEventsBufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(onEventsTotalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(onEventsCurrentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(onEventsContentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(getEventsAsList(eventsRef.get())).contains(Player.EVENT_PLAY_WHEN_READY_CHANGED);
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
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger playbackSuppressionReasonParamRef = new AtomicInteger();
    AtomicInteger playbackSuppressionReasonGetterRef = new AtomicInteger();
    AtomicInteger playbackSuppressionReasonOnEventsRef = new AtomicInteger();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackSuppressionReasonChanged(int reason) {
            playbackSuppressionReasonParamRef.set(reason);
            playbackSuppressionReasonGetterRef.set(controller.getPlaybackSuppressionReason());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            playbackSuppressionReasonOnEventsRef.set(player.getPlaybackSuppressionReason());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().notifyPlayWhenReadyChanged(testPlayWhenReady, testReason);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackSuppressionReasonParamRef.get()).isEqualTo(testReason);
    assertThat(playbackSuppressionReasonGetterRef.get()).isEqualTo(testReason);
    assertThat(playbackSuppressionReasonOnEventsRef.get()).isEqualTo(testReason);
    assertThat(getEventsAsList(eventsRef.get()))
        .contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED);
  }

  @Test
  public void onPlaybackSuppressionReasonChanged_updatesGetters() throws Exception {
    boolean testPlayWhenReady = true;
    @Player.PlaybackSuppressionReason
    int testReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS;
    long testCurrentPositionMs = 11;
    long testContentPositionMs = testCurrentPositionMs; // Not playing an ad
    long testBufferedPositionMs = 100;
    int testBufferedPercentage = 50;
    long testTotalBufferedDurationMs = 120;
    long testCurrentLiveOffsetMs = 10;
    long testContentBufferedPositionMs = 240;
    remoteSession
        .getMockPlayer()
        .setPlayWhenReady(testPlayWhenReady, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger playbackSuppressionReasonRef = new AtomicInteger();
    AtomicLong currentPositionMsRef = new AtomicLong();
    AtomicLong contentPositionMsRef = new AtomicLong();
    AtomicLong bufferedPositionMsRef = new AtomicLong();
    AtomicInteger bufferedPercentageRef = new AtomicInteger();
    AtomicLong totalBufferedDurationMsRef = new AtomicLong();
    AtomicLong currentLiveOffsetMsRef = new AtomicLong();
    AtomicLong contentBufferedPositionMsRef = new AtomicLong();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    AtomicInteger onEventsPlaybackSuppressionReasonRef = new AtomicInteger();
    AtomicLong onEventsCurrentPositionMsRef = new AtomicLong();
    AtomicLong onEventsContentPositionMsRef = new AtomicLong();
    AtomicLong onEventsBufferedPositionMsRef = new AtomicLong();
    AtomicInteger onEventsBufferedPercentageRef = new AtomicInteger();
    AtomicLong onEventsTotalBufferedDurationMsRef = new AtomicLong();
    AtomicLong onEventsCurrentLiveOffsetMsRef = new AtomicLong();
    AtomicLong onEventsContentBufferedPositionMsRef = new AtomicLong();
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

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            onEventsPlaybackSuppressionReasonRef.set(player.getPlaybackSuppressionReason());
            onEventsCurrentPositionMsRef.set(player.getCurrentPosition());
            onEventsContentPositionMsRef.set(player.getContentPosition());
            onEventsBufferedPositionMsRef.set(player.getBufferedPosition());
            onEventsBufferedPercentageRef.set(player.getBufferedPercentage());
            onEventsTotalBufferedDurationMsRef.set(player.getTotalBufferedDuration());
            onEventsCurrentLiveOffsetMsRef.set(player.getCurrentLiveOffset());
            onEventsContentBufferedPositionMsRef.set(player.getContentBufferedPosition());
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
    assertThat(onEventsPlaybackSuppressionReasonRef.get()).isEqualTo(testReason);
    assertThat(currentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(onEventsCurrentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(contentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(onEventsContentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(onEventsBufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(onEventsBufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(onEventsTotalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(onEventsCurrentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(onEventsContentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(getEventsAsList(eventsRef.get()))
        .contains(Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED);
  }

  @Test
  public void onPlaybackStateChanged_isNotified() throws Exception {
    @Player.State int testPlaybackState = Player.EVENT_PLAYER_ERROR;
    remoteSession.getMockPlayer().notifyPlaybackStateChanged(Player.STATE_IDLE);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger playbackStateParamRef = new AtomicInteger();
    AtomicInteger playbackStateGetterRef = new AtomicInteger();
    AtomicInteger playbackStateOnEventsRef = new AtomicInteger();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
            playbackStateParamRef.set(playbackState);
            playbackStateGetterRef.set(controller.getPlaybackState());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            playbackStateOnEventsRef.set(player.getPlaybackState());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().notifyPlaybackStateChanged(testPlaybackState);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateParamRef.get()).isEqualTo(testPlaybackState);
    assertThat(playbackStateGetterRef.get()).isEqualTo(testPlaybackState);
    assertThat(playbackStateOnEventsRef.get()).isEqualTo(testPlaybackState);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_PLAYBACK_STATE_CHANGED);
  }

  @Test
  public void onPlaybackStateChanged_updatesGetters() throws Exception {
    @Player.State int testPlaybackState = Player.EVENT_PLAYER_ERROR;
    long testCurrentPositionMs = 11;
    long testContentPositionMs = testCurrentPositionMs; // Not playing an ad
    long testBufferedPositionMs = 100;
    int testBufferedPercentage = 50;
    long testTotalBufferedDurationMs = 120;
    long testCurrentLiveOffsetMs = 10;
    long testContentBufferedPositionMs = 240;
    remoteSession.getMockPlayer().notifyPlaybackStateChanged(Player.STATE_IDLE);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicInteger playbackStateParamRef = new AtomicInteger();
    AtomicInteger playbackStateGetterRef = new AtomicInteger();
    AtomicLong currentPositionMsRef = new AtomicLong();
    AtomicLong contentPositionMsRef = new AtomicLong();
    AtomicLong bufferedPositionMsRef = new AtomicLong();
    AtomicInteger bufferedPercentageRef = new AtomicInteger();
    AtomicLong totalBufferedDurationMsRef = new AtomicLong();
    AtomicLong currentLiveOffsetMsRef = new AtomicLong();
    AtomicLong contentBufferedPositionMsRef = new AtomicLong();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    AtomicInteger onEventsPlaybackStateRef = new AtomicInteger();
    AtomicLong onEventsCurrentPositionMsRef = new AtomicLong();
    AtomicLong onEventsContentPositionMsRef = new AtomicLong();
    AtomicLong onEventsBufferedPositionMsRef = new AtomicLong();
    AtomicInteger onEventsBufferedPercentageRef = new AtomicInteger();
    AtomicLong onEventsTotalBufferedDurationMsRef = new AtomicLong();
    AtomicLong onEventsCurrentLiveOffsetMsRef = new AtomicLong();
    AtomicLong onEventsContentBufferedPositionMsRef = new AtomicLong();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(int playbackState) {
            playbackStateParamRef.set(playbackState);
            playbackStateGetterRef.set(controller.getPlaybackState());
            currentPositionMsRef.set(controller.getCurrentPosition());
            contentPositionMsRef.set(controller.getContentPosition());
            bufferedPositionMsRef.set(controller.getBufferedPosition());
            bufferedPercentageRef.set(controller.getBufferedPercentage());
            totalBufferedDurationMsRef.set(controller.getTotalBufferedDuration());
            currentLiveOffsetMsRef.set(controller.getCurrentLiveOffset());
            contentBufferedPositionMsRef.set(controller.getContentBufferedPosition());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            onEventsPlaybackStateRef.set(player.getPlaybackState());
            onEventsCurrentPositionMsRef.set(player.getCurrentPosition());
            onEventsContentPositionMsRef.set(player.getContentPosition());
            onEventsBufferedPositionMsRef.set(player.getBufferedPosition());
            onEventsBufferedPercentageRef.set(player.getBufferedPercentage());
            onEventsTotalBufferedDurationMsRef.set(player.getTotalBufferedDuration());
            onEventsCurrentLiveOffsetMsRef.set(player.getCurrentLiveOffset());
            onEventsContentBufferedPositionMsRef.set(player.getContentBufferedPosition());
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
    assertThat(playbackStateParamRef.get()).isEqualTo(testPlaybackState);
    assertThat(playbackStateGetterRef.get()).isEqualTo(testPlaybackState);
    assertThat(onEventsPlaybackStateRef.get()).isEqualTo(testPlaybackState);
    assertThat(currentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(onEventsCurrentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(contentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(onEventsContentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(onEventsBufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(onEventsBufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(onEventsTotalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(onEventsCurrentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(onEventsContentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_PLAYBACK_STATE_CHANGED);
  }

  @Test
  public void onIsPlayingChanged_isNotified() throws Exception {
    remoteSession
        .getMockPlayer()
        .setPlayWhenReady(/* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicBoolean isPlayingGetterRef = new AtomicBoolean();
    AtomicBoolean isPlayingParamRef = new AtomicBoolean();
    AtomicBoolean isPlayingOnEventsRef = new AtomicBoolean();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onIsPlayingChanged(boolean isPlaying) {
            isPlayingGetterRef.set(controller.isPlaying());
            isPlayingParamRef.set(isPlaying);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            isPlayingOnEventsRef.set(player.isPlaying());
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().notifyPlaybackStateChanged(Player.STATE_READY);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(isPlayingParamRef.get()).isTrue();
    assertThat(isPlayingGetterRef.get()).isTrue();
    assertThat(isPlayingOnEventsRef.get()).isTrue();
    assertThat(getEventsAsList(eventsRef.get())).contains(Player.EVENT_IS_PLAYING_CHANGED);
  }

  @Test
  public void onIsPlayingChanged_updatesGetters() throws Exception {
    long testCurrentPositionMs = 11;
    long testContentPositionMs = testCurrentPositionMs; // Not playing an ad
    long testBufferedPositionMs = 100;
    int testBufferedPercentage = 50;
    long testTotalBufferedDurationMs = 120;
    long testCurrentLiveOffsetMs = 10;
    long testContentBufferedPositionMs = 240;
    remoteSession
        .getMockPlayer()
        .setPlayWhenReady(/* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    threadTestRule.getHandler().postAndSync(() -> controller.setTimeDiffMs(/* timeDiffMs= */ 0L));
    CountDownLatch latch = new CountDownLatch(2);
    AtomicBoolean isPlayingRef = new AtomicBoolean();
    AtomicLong currentPositionMsRef = new AtomicLong();
    AtomicLong contentPositionMsRef = new AtomicLong();
    AtomicLong bufferedPositionMsRef = new AtomicLong();
    AtomicInteger bufferedPercentageRef = new AtomicInteger();
    AtomicLong totalBufferedDurationMsRef = new AtomicLong();
    AtomicLong currentLiveOffsetMsRef = new AtomicLong();
    AtomicLong contentBufferedPositionMsRef = new AtomicLong();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
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

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
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
    remoteSession.getMockPlayer().notifyPlaybackStateChanged(Player.STATE_READY);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(isPlayingRef.get()).isEqualTo(true);
    assertThat(currentPositionMsRef.get()).isEqualTo(testCurrentPositionMs);
    assertThat(contentPositionMsRef.get()).isEqualTo(testContentPositionMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(testBufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(testTotalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(testCurrentLiveOffsetMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(testContentBufferedPositionMs);
    assertThat(getEventsAsList(eventsRef.get())).contains(Player.EVENT_IS_PLAYING_CHANGED);
  }

  @Test
  public void onIsLoadingChanged_isNotified() throws Exception {
    boolean testIsLoading = true;
    remoteSession.getMockPlayer().notifyIsLoadingChanged(false);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicBoolean isLoadingFromParamRef = new AtomicBoolean();
    AtomicBoolean isLoadingFromGetterRef = new AtomicBoolean();
    AtomicBoolean isLoadingOnEventsRef = new AtomicBoolean();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onIsLoadingChanged(boolean isLoading) {
            isLoadingFromParamRef.set(isLoading);
            isLoadingFromGetterRef.set(controller.isLoading());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            isLoadingOnEventsRef.set(player.isLoading());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().notifyIsLoadingChanged(testIsLoading);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(isLoadingFromParamRef.get()).isEqualTo(testIsLoading);
    assertThat(isLoadingFromGetterRef.get()).isEqualTo(testIsLoading);
    assertThat(isLoadingOnEventsRef.get()).isEqualTo(testIsLoading);
    assertThat(getEventsAsList(eventsRef.get())).containsExactly(Player.EVENT_IS_LOADING_CHANGED);
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
    @Player.DiscontinuityReason int testReason = Player.DISCONTINUITY_REASON_INTERNAL;
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<PositionInfo> oldPositionRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionRef = new AtomicReference<>();
    AtomicInteger positionDiscontinuityReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition,
              PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            oldPositionRef.set(oldPosition);
            newPositionRef.set(newPosition);
            positionDiscontinuityReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
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
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_POSITION_DISCONTINUITY);
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
    CountDownLatch latch = new CountDownLatch(2);
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
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition,
              PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
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

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
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
        /* oldPosition= */ SessionPositionInfo.DEFAULT_POSITION_INFO,
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
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_POSITION_DISCONTINUITY);
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
    MediaController controller =
        controllerTestRule.createController(
            remoteSession.getToken(), /* connectionHints= */ null, listener);

    SessionCommands commands =
        new SessionCommands.Builder()
            .addAllSessionCommands()
            .remove(SessionCommand.COMMAND_CODE_SESSION_SET_RATING)
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
    AtomicReference<Commands> availableCommandsOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onAvailableCommandsChanged(Commands availableCommands) {
            availableCommandsFromParamRef.set(availableCommands);
            availableCommandsFromGetterRef.set(controller.getAvailableCommands());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            availableCommandsOnEventsRef.set(player.getAvailableCommands());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    Commands commandsWithSetRepeat = createPlayerCommandsWith(Player.COMMAND_SET_REPEAT_MODE);
    remoteSession.getMockPlayer().notifyAvailableCommandsChanged(commandsWithSetRepeat);

    Commands expectedCommands =
        new Commands.Builder()
            .addAll(Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_RELEASE)
            .build();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(availableCommandsFromParamRef.get()).isEqualTo(expectedCommands);
    assertThat(availableCommandsFromGetterRef.get()).isEqualTo(expectedCommands);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_AVAILABLE_COMMANDS_CHANGED);
  }

  @Test
  public void onTimelineChanged_playerCommandUnavailable_reducesTimelineToOneItem()
      throws Exception {
    int testMediaItemsSize = 2;
    List<MediaItem> testMediaItemList = MediaTestUtils.createMediaItems(testMediaItemsSize);
    Timeline testTimeline = new PlaylistTimeline(testMediaItemList);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setTimeline(testTimeline).build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    AtomicReference<Boolean> isCurrentMediaItemNullRef = new AtomicReference<>();
    List<Player.Events> eventsList = new ArrayList<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            timelineFromParamRef.set(timeline);
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            isCurrentMediaItemNullRef.set(controller.getCurrentMediaItem() == null);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            // onEvents is called twice.
            eventsList.add(events);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    Commands commandsWithoutGetTimeline = createPlayerCommandsWithout(Player.COMMAND_GET_TIMELINE);
    remoteSession.getMockPlayer().notifyAvailableCommandsChanged(commandsWithoutGetTimeline);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(timelineFromParamRef.get().getWindowCount()).isEqualTo(1);
    assertThat(timelineFromGetterRef.get().getWindowCount()).isEqualTo(1);
    assertThat(isCurrentMediaItemNullRef.get()).isFalse();
    assertThat(eventsList).hasSize(2);
    assertThat(getEventsAsList(eventsList.get(0)))
        .containsExactly(Player.EVENT_AVAILABLE_COMMANDS_CHANGED);
    assertThat(getEventsAsList(eventsList.get(1))).containsExactly(Player.EVENT_TIMELINE_CHANGED);
  }

  @Test
  public void onTimelineChanged_sessionCommandUnavailable_reducesTimelineToOneItem()
      throws Exception {
    int testMediaItemsSize = 2;
    List<MediaItem> testMediaItemList = MediaTestUtils.createMediaItems(testMediaItemsSize);
    Timeline testTimeline = new PlaylistTimeline(testMediaItemList);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setTimeline(testTimeline).build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    AtomicReference<Boolean> isCurrentMediaItemNullRef = new AtomicReference<>();
    List<Player.Events> eventsList = new ArrayList<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            timelineFromParamRef.set(timeline);
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            isCurrentMediaItemNullRef.set(controller.getCurrentMediaItem() == null);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            // onEvents is called twice.
            eventsList.add(events);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    Commands commandsWithoutGetTimeline = createPlayerCommandsWithout(Player.COMMAND_GET_TIMELINE);
    remoteSession.setAvailableCommands(SessionCommands.EMPTY, commandsWithoutGetTimeline);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(timelineFromParamRef.get().getWindowCount()).isEqualTo(1);
    assertThat(timelineFromGetterRef.get().getWindowCount()).isEqualTo(1);
    assertThat(isCurrentMediaItemNullRef.get()).isFalse();
    assertThat(eventsList).hasSize(2);
    assertThat(getEventsAsList(eventsList.get(0)))
        .containsExactly(Player.EVENT_AVAILABLE_COMMANDS_CHANGED);
    assertThat(getEventsAsList(eventsList.get(1))).containsExactly(Player.EVENT_TIMELINE_CHANGED);
  }

  /** This also tests {@link MediaController#getAvailableCommands()}. */
  @Test
  public void onAvailableCommandsChanged_isCalledBySessionChange() throws Exception {
    Commands commandsWithAllCommands = new Player.Commands.Builder().addAllCommands().build();
    remoteSession.getMockPlayer().notifyAvailableCommandsChanged(commandsWithAllCommands);
    MediaController controller =
        controllerTestRule.createController(
            remoteSession.getToken(), /* connectionHints= */ null, /* listener= */ null);
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Commands> availableCommandsFromParamRef = new AtomicReference<>();
    AtomicReference<Commands> availableCommandsFromGetterRef = new AtomicReference<>();
    AtomicReference<Commands> availableCommandsFromOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onAvailableCommandsChanged(Commands availableCommands) {
            availableCommandsFromParamRef.set(availableCommands);
            availableCommandsFromGetterRef.set(controller.getAvailableCommands());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            availableCommandsFromOnEventsRef.set(player.getAvailableCommands());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    Commands commandsWithSetRepeat = createPlayerCommandsWith(Player.COMMAND_SET_REPEAT_MODE);
    remoteSession.setAvailableCommands(SessionCommands.EMPTY, commandsWithSetRepeat);

    Commands expectedCommands =
        new Commands.Builder()
            .addAll(Player.COMMAND_SET_REPEAT_MODE, Player.COMMAND_RELEASE)
            .build();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(availableCommandsFromParamRef.get()).isEqualTo(expectedCommands);
    assertThat(availableCommandsFromGetterRef.get()).isEqualTo(expectedCommands);
    assertThat(availableCommandsFromOnEventsRef.get()).isEqualTo(expectedCommands);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_AVAILABLE_COMMANDS_CHANGED);
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
            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
          }
        };
    MediaController controller =
        controllerTestRule.createController(
            remoteSession.getToken(), /* connectionHints= */ null, listener);

    // TODO(b/245724167): Test with multiple controllers
    remoteSession.broadcastCustomCommand(testCommand, testArgs);

    // TODO(b/245724167): Test receivers as well.
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
            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
          }
        };
    RemoteMediaSession session = createRemoteMediaSession(TEST_WITH_CUSTOM_COMMANDS);
    MediaController controller =
        controllerTestRule.createController(
            session.getToken(), /* connectionHints= */ null, listener);

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
    List<Bundle> getterSessionExtras = new ArrayList<>();
    MediaController.Listener listener =
        new MediaController.Listener() {
          @Override
          public void onExtrasChanged(MediaController controller, Bundle extras) {
            receivedSessionExtras.add(extras);
            getterSessionExtras.add(controller.getSessionExtras());
            latch.countDown();
          }
        };
    MediaController controller =
        controllerTestRule.createController(
            remoteSession.getToken(), /* connectionHints= */ null, listener);

    remoteSession.setSessionExtras(sessionExtras);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedSessionExtras).hasSize(1);
    assertThat(TestUtils.equals(receivedSessionExtras.get(0), sessionExtras)).isTrue();
    assertThat(getterSessionExtras).hasSize(1);
    assertThat(TestUtils.equals(getterSessionExtras.get(0), sessionExtras)).isTrue();
  }

  @Test
  public void setSessionExtras_specificMedia3Controller_onExtrasChangedCalled() throws Exception {
    Bundle sessionExtras = TestUtils.createTestBundle();
    sessionExtras.putString("key-0", "value-0");
    CountDownLatch latch = new CountDownLatch(1);
    List<Bundle> receivedSessionExtras = new ArrayList<>();
    List<Bundle> getterSessionExtras = new ArrayList<>();
    MediaController.Listener listener =
        new MediaController.Listener() {
          @Override
          public void onExtrasChanged(MediaController controller, Bundle extras) {
            receivedSessionExtras.add(extras);
            getterSessionExtras.add(controller.getSessionExtras());
            latch.countDown();
          }
        };
    Bundle connectionHints = new Bundle();
    connectionHints.putString(KEY_CONTROLLER, "controller_key_1");
    MediaController controller =
        controllerTestRule.createController(remoteSession.getToken(), connectionHints, listener);

    remoteSession.setSessionExtras("controller_key_1", sessionExtras);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedSessionExtras).hasSize(1);
    assertThat(TestUtils.equals(receivedSessionExtras.get(0), sessionExtras)).isTrue();
    assertThat(TestUtils.equals(getterSessionExtras.get(0), sessionExtras)).isTrue();
  }

  @Test
  public void setSessionActivity_onSessionActivityChangedCalled() throws Exception {
    Intent intent = new Intent(context, SurfaceActivity.class);
    PendingIntent sessionActivity =
        PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    CountDownLatch latch = new CountDownLatch(1);
    List<PendingIntent> receivedSessionActivities = new ArrayList<>();
    MediaController.Listener listener =
        new MediaController.Listener() {
          @Override
          public void onSessionActivityChanged(
              MediaController controller, PendingIntent sessionActivity) {
            receivedSessionActivities.add(sessionActivity);
            latch.countDown();
          }
        };
    MediaController controller =
        controllerTestRule.createController(
            remoteSession.getToken(), /* connectionHints= */ null, listener);
    assertThat(controller.getSessionActivity()).isNull();

    remoteSession.setSessionActivity(sessionActivity);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controller.getSessionActivity()).isEqualTo(sessionActivity);
    assertThat(receivedSessionActivities).containsExactly(sessionActivity);
  }

  @Test
  public void onVideoSizeChanged() throws Exception {
    VideoSize defaultVideoSize = MediaTestUtils.createDefaultVideoSize();
    RemoteMediaSession session = createRemoteMediaSession(TEST_ON_VIDEO_SIZE_CHANGED);
    MediaController controller = controllerTestRule.createController(session.getToken());
    List<VideoSize> videoSizeFromGetterList = new ArrayList<>();
    List<VideoSize> videoSizeFromParamList = new ArrayList<>();
    List<Player.Events> eventsList = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(6);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onVideoSizeChanged(VideoSize videoSize) {
            videoSizeFromParamList.add(videoSize);
            videoSizeFromGetterList.add(controller.getVideoSize());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsList.add(events);
            latch.countDown();
          }
        };
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.addListener(listener);
              // Verify initial controller state.
              assertThat(controller.getVideoSize()).isEqualTo(defaultVideoSize);
            });

    session.getMockPlayer().notifyVideoSizeChanged(VideoSize.UNKNOWN);
    session.getMockPlayer().notifyVideoSizeChanged(defaultVideoSize);
    session.getMockPlayer().notifyVideoSizeChanged(defaultVideoSize);
    session.getMockPlayer().notifyVideoSizeChanged(VideoSize.UNKNOWN);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(videoSizeFromParamList)
        .containsExactly(VideoSize.UNKNOWN, defaultVideoSize, VideoSize.UNKNOWN)
        .inOrder();
    assertThat(videoSizeFromGetterList)
        .containsExactly(VideoSize.UNKNOWN, defaultVideoSize, VideoSize.UNKNOWN)
        .inOrder();
    assertThat(eventsList).hasSize(3);
    assertThat(getEventsAsList(eventsList.get(0))).containsExactly(Player.EVENT_VIDEO_SIZE_CHANGED);
    assertThat(getEventsAsList(eventsList.get(1))).containsExactly(Player.EVENT_VIDEO_SIZE_CHANGED);
    assertThat(getEventsAsList(eventsList.get(2))).containsExactly(Player.EVENT_VIDEO_SIZE_CHANGED);
  }

  @Test
  public void onAudioAttributesChanged_isCalledAndUpdatesGetter() throws Exception {
    AudioAttributes testAttributes =
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build();
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<AudioAttributes> attributesFromGetterRef = new AtomicReference<>();
    AtomicReference<AudioAttributes> attributesFromParamRef = new AtomicReference<>();
    AtomicReference<AudioAttributes> attributesFromOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onAudioAttributesChanged(AudioAttributes attributes) {
            attributesFromParamRef.set(attributes);
            attributesFromGetterRef.set(controller.getAudioAttributes());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            attributesFromOnEventsRef.set(player.getAudioAttributes());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().notifyAudioAttributesChanged(testAttributes);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(attributesFromParamRef.get()).isEqualTo(testAttributes);
    assertThat(attributesFromGetterRef.get()).isEqualTo(testAttributes);
    assertThat(attributesFromOnEventsRef.get()).isEqualTo(testAttributes);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_AUDIO_ATTRIBUTES_CHANGED);
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
    CountDownLatch latch = new CountDownLatch(3);
    List<Cue> cuesFromGetter = new ArrayList<>();
    List<Cue> cuesFromParam = new ArrayList<>();
    List<CueGroup> onEventsCues = new ArrayList<>();
    List<Player.Events> eventsList = new ArrayList<>();
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

          @Override
          public void onEvents(Player player, Player.Events events) {
            // onEvents is called twice.
            eventsList.add(events);
            onEventsCues.add(player.getCurrentCues());
            latch.countDown();
          }
        };
    controller.addListener(listener);

    Commands commandsWithoutGetText = createPlayerCommandsWithout(Player.COMMAND_GET_TEXT);
    remoteSession.setAvailableCommands(SessionCommands.EMPTY, commandsWithoutGetText);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(cuesFromParam).isEqualTo(ImmutableList.of());
    assertThat(cuesFromGetter).isEqualTo(ImmutableList.of());
    assertThat(onEventsCues).hasSize(2);
    assertThat(onEventsCues.get(1).cues).hasSize(0);
    assertThat(getEventsAsList(eventsList.get(0)))
        .containsExactly(Player.EVENT_AVAILABLE_COMMANDS_CHANGED);
    assertThat(getEventsAsList(eventsList.get(1))).containsExactly(Player.EVENT_CUES);
  }

  @Test
  public void onCues_isCalledByPlayerChange() throws Exception {
    Cue testCue1 = new Cue.Builder().setText(SpannedString.valueOf("cue1")).build();
    Cue testCue2 = new Cue.Builder().setText(SpannedString.valueOf("cue2")).build();
    List<Cue> testCues = ImmutableList.of(testCue1, testCue2);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    List<Cue> cuesFromParam = new ArrayList<>();
    List<Cue> cuesFromGetter = new ArrayList<>();
    List<Cue> cuesFromOnEvents = new ArrayList<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onCues(CueGroup cueGroup) {
            cuesFromParam.addAll(cueGroup.cues);
            cuesFromGetter.addAll(controller.getCurrentCues().cues);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            cuesFromOnEvents.addAll(player.getCurrentCues().cues);
            eventsRef.set(events);
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
    assertThat(cuesFromOnEvents).isEqualTo(testCues);
    assertThat(getEventsAsList(eventsRef.get())).containsExactly(Player.EVENT_CUES);
  }

  @Test
  public void onCues_isCalledByCuesChange() throws Exception {
    Cue testCue1 = new Cue.Builder().setText(SpannedString.valueOf("cue1")).build();
    Cue testCue2 = new Cue.Builder().setText(SpannedString.valueOf("cue2")).build();
    List<Cue> testCues = ImmutableList.of(testCue1, testCue2);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    List<Cue> cuesFromParam = new ArrayList<>();
    List<Cue> cuesFromGetter = new ArrayList<>();
    List<Cue> cuesFromOnEvents = new ArrayList<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onCues(CueGroup cueGroup) {
            cuesFromParam.addAll(cueGroup.cues);
            cuesFromGetter.addAll(controller.getCurrentCues().cues);
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            cuesFromOnEvents.addAll(player.getCurrentCues().cues);
            eventsRef.set(events);
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
    assertThat(cuesFromOnEvents).isEqualTo(testCues);
    assertThat(getEventsAsList(eventsRef.get())).containsExactly(Player.EVENT_CUES);
  }

  @Test
  public void onDeviceInfoChanged_isCalledByPlayerChange() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<DeviceInfo> deviceInfoFromParamRef = new AtomicReference<>();
    AtomicReference<DeviceInfo> deviceInfoFromGetterRef = new AtomicReference<>();
    AtomicReference<DeviceInfo> deviceInfoFromOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceInfoChanged(DeviceInfo deviceInfo) {
            deviceInfoFromParamRef.set(deviceInfo);
            deviceInfoFromGetterRef.set(controller.getDeviceInfo());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            deviceInfoFromOnEventsRef.set(player.getDeviceInfo());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    DeviceInfo deviceInfo =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(100).build();
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceInfo(deviceInfo).build();
    remoteSession.setPlayer(playerConfig);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceInfoFromParamRef.get()).isEqualTo(deviceInfo);
    assertThat(deviceInfoFromGetterRef.get()).isEqualTo(deviceInfo);
    assertThat(deviceInfoFromOnEventsRef.get()).isEqualTo(deviceInfo);
    assertThat(getEventsAsList(eventsRef.get())).containsExactly(Player.EVENT_DEVICE_INFO_CHANGED);
  }

  @Test
  public void onDeviceInfoChanged_isCalledByDeviceInfoChange() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<DeviceInfo> deviceInfoFromParamRef = new AtomicReference<>();
    AtomicReference<DeviceInfo> deviceInfoFromGetterRef = new AtomicReference<>();
    AtomicReference<DeviceInfo> deviceInfoFromOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceInfoChanged(DeviceInfo deviceInfo) {
            deviceInfoFromParamRef.set(deviceInfo);
            deviceInfoFromGetterRef.set(controller.getDeviceInfo());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            deviceInfoFromOnEventsRef.set(player.getDeviceInfo());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    DeviceInfo deviceInfo =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(23).build();
    remoteSession.getMockPlayer().notifyDeviceInfoChanged(deviceInfo);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceInfoFromParamRef.get()).isEqualTo(deviceInfo);
    assertThat(deviceInfoFromGetterRef.get()).isEqualTo(deviceInfo);
    assertThat(deviceInfoFromOnEventsRef.get()).isEqualTo(deviceInfo);
    assertThat(getEventsAsList(eventsRef.get())).containsExactly(Player.EVENT_DEVICE_INFO_CHANGED);
  }

  @Test
  public void onDeviceVolumeChanged_isCalledByDeviceVolumeChange() throws Exception {
    DeviceInfo deviceInfo =
        new DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(100).build();
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(23)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicInteger deviceVolumeFromParamRef = new AtomicInteger();
    AtomicInteger deviceVolumeFromGetterRef = new AtomicInteger();
    AtomicInteger deviceVolumeFromOnEventsRef = new AtomicInteger();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeFromParamRef.set(volume);
            deviceVolumeFromGetterRef.set(controller.getDeviceVolume());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            deviceVolumeFromOnEventsRef.set(player.getDeviceVolume());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    int targetVolume = 45;
    remoteSession.getMockPlayer().setDeviceVolume(targetVolume, /* flags= */ 0);
    remoteSession.getMockPlayer().notifyDeviceVolumeChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceVolumeFromParamRef.get()).isEqualTo(targetVolume);
    assertThat(deviceVolumeFromGetterRef.get()).isEqualTo(targetVolume);
    assertThat(deviceVolumeFromOnEventsRef.get()).isEqualTo(targetVolume);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_DEVICE_VOLUME_CHANGED);
  }

  @Test
  public void onDeviceVolumeChanged_isCalledByDeviceMutedChange() throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceMuted(false).build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicBoolean deviceMutedFromParamRef = new AtomicBoolean();
    AtomicBoolean deviceMutedFromGetterRef = new AtomicBoolean();
    AtomicBoolean deviceMutedFromOnEventsRef = new AtomicBoolean();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceMutedFromParamRef.set(muted);
            deviceMutedFromGetterRef.set(controller.isDeviceMuted());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            deviceMutedFromOnEventsRef.set(player.isDeviceMuted());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().setDeviceMuted(/* muted= */ true, /* flags= */ 0);
    remoteSession.getMockPlayer().notifyDeviceVolumeChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceMutedFromParamRef.get()).isTrue();
    assertThat(deviceMutedFromGetterRef.get()).isTrue();
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_DEVICE_VOLUME_CHANGED);
  }

  @Test
  public void onDeviceVolumeChanged_isCalledByDecreaseDeviceVolume() throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceVolume(10).build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int volumeFlags = C.VOLUME_FLAG_VIBRATE;
    AtomicInteger deviceVolumeFromParamRef = new AtomicInteger();
    AtomicInteger deviceVolumeFromGetterRef = new AtomicInteger();
    AtomicInteger deviceVolumeFromOnEventsRef = new AtomicInteger();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeFromParamRef.set(volume);
            deviceVolumeFromGetterRef.set(controller.getDeviceVolume());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            deviceVolumeFromOnEventsRef.set(player.getDeviceVolume());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().decreaseDeviceVolume(volumeFlags);
    remoteSession.getMockPlayer().notifyDeviceVolumeChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceVolumeFromParamRef.get()).isEqualTo(9);
    assertThat(deviceVolumeFromGetterRef.get()).isEqualTo(9);
    assertThat(deviceVolumeFromOnEventsRef.get()).isEqualTo(9);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_DEVICE_VOLUME_CHANGED);
  }

  @Test
  public void onDeviceVolumeChanged_isCalledByIncreaseDeviceVolume() throws Exception {
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceVolume(10).build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int volumeFlags = C.VOLUME_FLAG_VIBRATE;
    AtomicInteger deviceVolumeFromParamRef = new AtomicInteger();
    AtomicInteger deviceVolumeFromGetterRef = new AtomicInteger();
    AtomicInteger deviceVolumeFromOnEventsRef = new AtomicInteger();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onDeviceVolumeChanged(int volume, boolean muted) {
            deviceVolumeFromParamRef.set(volume);
            deviceVolumeFromGetterRef.set(controller.getDeviceVolume());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            deviceVolumeFromOnEventsRef.set(player.getDeviceVolume());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().increaseDeviceVolume(volumeFlags);
    remoteSession.getMockPlayer().notifyDeviceVolumeChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(deviceVolumeFromParamRef.get()).isEqualTo(11);
    assertThat(deviceVolumeFromGetterRef.get()).isEqualTo(11);
    assertThat(deviceVolumeFromOnEventsRef.get()).isEqualTo(11);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_DEVICE_VOLUME_CHANGED);
  }

  @Test
  public void onVolumeChanged() throws Exception {
    Bundle playerConfig = new RemoteMediaSession.MockPlayerConfigBuilder().build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<Float> volumeFromParamRef = new AtomicReference<>();
    AtomicReference<Float> volumeFromGetterRef = new AtomicReference<>();
    AtomicReference<Float> volumeFromOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onVolumeChanged(float volume) {
            volumeFromParamRef.set(volume);
            volumeFromGetterRef.set(controller.getVolume());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            volumeFromOnEventsRef.set(player.getVolume());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    remoteSession.getMockPlayer().setVolume(0.5f);
    remoteSession.getMockPlayer().notifyVolumeChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(volumeFromParamRef.get()).isEqualTo(0.5f);
    assertThat(volumeFromGetterRef.get()).isEqualTo(0.5f);
    assertThat(volumeFromOnEventsRef.get()).isEqualTo(0.5f);
    assertThat(getEventsAsList(eventsRef.get())).containsExactly(Player.EVENT_VOLUME_CHANGED);
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
    AtomicLong maxSeekToPreviousPositionMsFromOnEventsRef = new AtomicLong();
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
            maxSeekToPreviousPositionMsFromOnEventsRef.set(player.getMaxSeekToPreviousPosition());
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
    assertThat(maxSeekToPreviousPositionMsFromOnEventsRef.get())
        .isEqualTo(testMaxSeekToPreviousPositionMs);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED);
  }

  @Test
  public void onEvents_whenNewCommandIsCalledInsideListener_containsEventFromNewCommand()
      throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(@Player.RepeatMode int repeatMode) {
            controller.setShuffleModeEnabled(true);
            latch.countDown();
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    remoteSession.getMockPlayer().setRepeatMode(Player.REPEAT_MODE_ONE);
    remoteSession.getMockPlayer().notifyRepeatModeChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(getEventsAsList(eventsRef.get()))
        .containsAtLeast(
            Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED);
  }

  @Test
  public void onEvents_whenNewCommandIsCalledInsideOnEvents_isCalledFromNewLooperIterationSet()
      throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(4);
    List<Player.Events> eventsList = new ArrayList<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(int repeatMode) {
            latch.countDown();
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            // onEvents is called twice.
            eventsList.add(events);
            controller.setShuffleModeEnabled(true);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    remoteSession.getMockPlayer().setRepeatMode(Player.REPEAT_MODE_ONE);
    remoteSession.getMockPlayer().notifyRepeatModeChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(eventsList).hasSize(2);
    assertThat(getEventsAsList(eventsList.get(0)))
        .containsExactly(Player.EVENT_REPEAT_MODE_CHANGED);
    assertThat(getEventsAsList(eventsList.get(1)))
        .containsExactly(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED);
  }

  @Test
  public void onMediaMetadataChanged_isNotifiedAndUpdatesGetter() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<MediaMetadata> mediaMetadataFromParamRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> mediaMetadataFromGetterRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> mediaMetadataFromOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            mediaMetadataFromParamRef.set(mediaMetadata);
            mediaMetadataFromGetterRef.set(controller.getMediaMetadata());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            mediaMetadataFromOnEventsRef.set(player.getMediaMetadata());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));

    MediaMetadata testMediaMetadata = new MediaMetadata.Builder().setTitle("title").build();
    remoteSession.getMockPlayer().notifyMediaMetadataChanged(testMediaMetadata);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaMetadataFromParamRef.get()).isEqualTo(testMediaMetadata);
    assertThat(mediaMetadataFromGetterRef.get()).isEqualTo(testMediaMetadata);
    assertThat(mediaMetadataFromOnEventsRef.get()).isEqualTo(testMediaMetadata);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_MEDIA_METADATA_CHANGED);
  }

  @Test
  public void onMediaMetadataChanged_isCalledByPlayerChange() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<MediaMetadata> mediaMetadataFromParamRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> mediaMetadataFromGetterRef = new AtomicReference<>();
    AtomicReference<MediaMetadata> mediaMetadataFromOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            mediaMetadataFromParamRef.set(mediaMetadata);
            mediaMetadataFromGetterRef.set(controller.getMediaMetadata());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            mediaMetadataFromOnEventsRef.set(player.getMediaMetadata());
            eventsRef.set(events);
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
    assertThat(mediaMetadataFromOnEventsRef.get()).isEqualTo(testMediaMetadata);
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_MEDIA_METADATA_CHANGED);
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
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromOnEventsRef = new AtomicReference<>();
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            timelineFromGetterRef.set(controller.getCurrentTimeline());
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            timelineFromOnEventsRef.set(player.getCurrentTimeline());
            eventsRef.set(events);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    MediaMetadata testMediaMetadata = new MediaMetadata.Builder().setTitle("title").build();
    remoteSession.getMockPlayer().notifyMediaMetadataChanged(testMediaMetadata);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(getEventsAsList(eventsRef.get()))
        .containsExactly(Player.EVENT_MEDIA_METADATA_CHANGED);
    MediaTestUtils.assertMediaIdEquals(testTimeline, timelineFromGetterRef.get());
    MediaTestUtils.assertMediaIdEquals(testTimeline, timelineFromOnEventsRef.get());
  }

  @Test
  public void onRenderedFirstFrame_isNotified() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AtomicReference<Player.Events> eventsRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRenderedFirstFrame() {
            latch.countDown();
          }

          @Override
          public void onEvents(Player player, Player.Events events) {
            eventsRef.set(events);
            latch.countDown();
          }
        };
    controller.addListener(listener);

    remoteSession.getMockPlayer().notifyRenderedFirstFrame();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(getEventsAsList(eventsRef.get())).containsExactly(Player.EVENT_RENDERED_FIRST_FRAME);
  }

  @Test
  public void recursiveChangesFromListeners_reportConsistentValuesForAllListeners()
      throws Exception {
    // We add two listeners to the controller. The first stops the player as soon as it's ready and
    // both record the state change events they receive.
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    List<Integer> listener1States = new ArrayList<>();
    List<Integer> listener2States = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(4);
    Player.Listener listener1 =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(@Player.State int playbackState) {
            listener1States.add(playbackState);
            if (playbackState == Player.STATE_READY) {
              controller.stop();
            }
            latch.countDown();
          }
        };
    Player.Listener listener2 =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(@Player.State int playbackState) {
            listener2States.add(playbackState);
            latch.countDown();
          }
        };
    controller.addListener(listener1);
    controller.addListener(listener2);

    remoteSession.getMockPlayer().notifyPlaybackStateChanged(Player.STATE_READY);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(listener1States).containsExactly(Player.STATE_READY, Player.STATE_IDLE).inOrder();
    assertThat(listener2States).containsExactly(Player.STATE_READY, Player.STATE_IDLE).inOrder();
  }

  private void testControllerAfterSessionIsClosed(String id) throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    // This causes the session service to die.
    remoteSession.release();
    // controllerTestRule.waitForDisconnect(controller, true);
    assertNoInteraction(controller);

    // Ensure that the controller cannot use newly create session with the same ID.
    // Recreated session has different session stub, so previously created controller
    // shouldn't be available.
    remoteSession = createRemoteMediaSession(id);
    assertNoInteraction(controller);
  }

  /**
   * Asserts that {@link #remoteSession} and {@code controller} don't interact.
   *
   * <p>Note that this method can be called after the session is died, so {@link #remoteSession} may
   * not have valid player.
   */
  private void assertNoInteraction(MediaController controller) throws Exception {
    // TODO: check that calls from the controller to session shouldn't be delivered.

    // Calls from the session to controller shouldn't be delivered.
    CountDownLatch latch = new CountDownLatch(1);
    controllerTestRule.setRunnableForOnCustomCommand(
        controller, /* runnable= */ () -> latch.countDown());
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
