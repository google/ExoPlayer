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

import static com.google.android.exoplayer2.session.vct.common.CommonConstants.DEFAULT_TEST_NAME;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.android.exoplayer2.session.vct.common.MediaSessionConstants.TEST_GET_SESSION_ACTIVITY;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.LONG_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.HeartRating;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.Rating;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.session.MediaController.ControllerCallback;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.android.exoplayer2.session.vct.common.PollingCheck;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import com.google.android.exoplayer2.video.VideoSize;
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

/** Tests for {@link MediaController}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerTest {

  private static final String TAG = "MediaControllerTest";

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);
  final MediaControllerTestRule controllerTestRule = new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  private final List<RemoteMediaSession> remoteSessionList = new ArrayList<>();

  private Context context;
  private RemoteMediaSession remoteSession;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    remoteSession = createRemoteMediaSession(DEFAULT_TEST_NAME, null);
  }

  @After
  public void cleanUp() throws RemoteException {
    for (int i = 0; i < remoteSessionList.size(); i++) {
      RemoteMediaSession session = remoteSessionList.get(i);
      if (session != null) {
        session.cleanUp();
      }
    }
  }

  @Test
  public void builder() throws Exception {
    MediaController.Builder builder;

    try {
      builder = new MediaController.Builder(null);
      assertWithMessage("null context shouldn't be allowed").fail();
    } catch (NullPointerException e) {
      // expected. pass-through
    }

    try {
      builder = new MediaController.Builder(context);
      builder.setSessionToken(null);
      assertWithMessage("null token shouldn't be allowed").fail();
    } catch (NullPointerException e) {
      // expected. pass-through
    }

    try {
      builder = new MediaController.Builder(context);
      builder.setSessionCompatToken(null);
      assertWithMessage("null compat token shouldn't be allowed").fail();
    } catch (NullPointerException e) {
      // expected. pass-through
    }

    try {
      builder = new MediaController.Builder(context);
      builder.setControllerCallback(null);
      assertWithMessage("null callback shouldn't be allowed").fail();
    } catch (NullPointerException e) {
      // expected. pass-through
    }

    try {
      builder = new MediaController.Builder(context);
      builder.setApplicationLooper(null);
      assertWithMessage("null looper shouldn't be allowed").fail();
    } catch (NullPointerException e) {
      // expected. pass-through
    }

    MediaController controller =
        new MediaController.Builder(context)
            .setSessionToken(remoteSession.getToken())
            .setControllerCallback(new ControllerCallback() {})
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();
    threadTestRule.getHandler().postAndSync(controller::release);
  }

  @Test
  public void getSessionActivity() throws Exception {
    RemoteMediaSession session = createRemoteMediaSession(TEST_GET_SESSION_ACTIVITY, null);

    MediaController controller = controllerTestRule.createController(session.getToken());
    PendingIntent sessionActivity = controller.getSessionActivity();
    assertThat(sessionActivity).isNotNull();
    if (Build.VERSION.SDK_INT >= 17) {
      // PendingIntent#getCreatorPackage() is added in API 17.
      assertThat(sessionActivity.getCreatorPackage()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);

      // TODO: Add getPid/getUid in MediaControllerProviderService and compare them.
      // assertThat(sessionActivity.getCreatorUid()).isEqualTo(remoteSession.getUid());
    }
    session.cleanUp();
  }

  @Test
  public void getPackageName() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    assertThat(controller.getConnectedToken().getPackageName()).isEqualTo(SUPPORT_APP_PACKAGE_NAME);
  }

  @Test
  public void getTokenExtras() throws Exception {
    Bundle testTokenExtras = TestUtils.createTestBundle();
    RemoteMediaSession session = createRemoteMediaSession("testGetExtras", testTokenExtras);

    MediaController controller = controllerTestRule.createController(session.getToken());
    SessionToken connectedToken = controller.getConnectedToken();
    assertThat(connectedToken).isNotNull();
    assertThat(TestUtils.equals(testTokenExtras, connectedToken.getExtras())).isTrue();
  }

  @Test
  public void isConnected() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    assertThat(controller.isConnected()).isTrue();

    remoteSession.release();
    controllerTestRule.waitForDisconnect(controller, true);
    assertThat(controller.isConnected()).isFalse();
  }

  @Test
  public void close_beforeConnected() throws Exception {
    MediaController controller =
        controllerTestRule.createController(
            remoteSession.getToken(), /* waitForConnect= */ false, null, /* callback= */ null);
    threadTestRule.getHandler().postAndSync(controller::release);
  }

  @Test
  public void close_twice() throws Exception {
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    threadTestRule.getHandler().postAndSync(controller::release);
    threadTestRule.getHandler().postAndSync(controller::release);
  }

  @Test
  public void gettersAfterConnected() throws Exception {
    long currentPositionMs = 11;
    long contentPositionMs = 33;
    long durationMs = 200;
    long bufferedPositionMs = 100;
    int bufferedPercentage = 50;
    long totalBufferedDurationMs = 120;
    long currentLiveOffsetMs = 10;
    long contentDurationMs = 300;
    long contentBufferedPositionMs = 240;
    boolean isPlayingAd = true;
    int currentAdGroupIndex = 33;
    int currentAdIndexInAdGroup = 22;
    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 0.5f);
    boolean playWhenReady = true;
    @Player.PlaybackSuppressionReason
    int playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS;
    @Player.State int playbackState = Player.STATE_READY;
    boolean isPlaying = true;
    boolean isLoading = true;
    MediaItem currentMediaItem = MediaTestUtils.createConvergedMediaItem(/* mediaId= */ "current");
    boolean isShuffleModeEnabled = true;
    @RepeatMode int repeatMode = Player.REPEAT_MODE_ONE;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setCurrentPosition(currentPositionMs)
            .setContentPosition(contentPositionMs)
            .setDuration(durationMs)
            .setBufferedPosition(bufferedPositionMs)
            .setBufferedPercentage(bufferedPercentage)
            .setTotalBufferedDuration(totalBufferedDurationMs)
            .setCurrentLiveOffset(currentLiveOffsetMs)
            .setContentDuration(contentDurationMs)
            .setContentBufferedPosition(contentBufferedPositionMs)
            .setIsPlayingAd(isPlayingAd)
            .setCurrentAdGroupIndex(currentAdGroupIndex)
            .setCurrentAdIndexInAdGroup(currentAdIndexInAdGroup)
            .setPlaybackParameters(playbackParameters)
            .setCurrentMediaItem(currentMediaItem)
            .setPlayWhenReady(playWhenReady)
            .setPlaybackSuppressionReason(playbackSuppressionReason)
            .setPlaybackState(playbackState)
            .setIsPlaying(isPlaying)
            .setIsLoading(isLoading)
            .setShuffleModeEnabled(isShuffleModeEnabled)
            .setRepeatMode(repeatMode)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    threadTestRule.getHandler().postAndSync(() -> controller.setTimeDiffMs(0L));

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
    AtomicReference<PlaybackParameters> playbackParametersRef = new AtomicReference<>();
    AtomicReference<MediaItem> mediaItemRef = new AtomicReference<>();
    AtomicBoolean playWhenReadyRef = new AtomicBoolean();
    AtomicInteger playbackSuppressionReasonRef = new AtomicInteger();
    AtomicInteger playbackStateRef = new AtomicInteger();
    AtomicBoolean isPlayingRef = new AtomicBoolean();
    AtomicBoolean isLoadingRef = new AtomicBoolean();
    AtomicBoolean isShuffleModeEnabledRef = new AtomicBoolean();
    AtomicInteger repeatModeRef = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              currentPositionMsRef.set(controller.getCurrentPosition());
              contentPositionMsRef.set(controller.getContentPosition());
              durationMsRef.set(controller.getDuration());
              bufferedPositionMsRef.set(controller.getBufferedPosition());
              bufferedPercentageRef.set(controller.getBufferedPercentage());
              totalBufferedDurationMsRef.set(controller.getTotalBufferedDuration());
              currentLiveOffsetMsRef.set(controller.getCurrentLiveOffset());
              contentDurationMsRef.set(controller.getContentDuration());
              contentBufferedPositionMsRef.set(controller.getContentBufferedPosition());
              playbackParametersRef.set(controller.getPlaybackParameters());
              isPlayingAdRef.set(controller.isPlayingAd());
              currentAdGroupIndexRef.set(controller.getCurrentAdGroupIndex());
              currentAdIndexInAdGroupRef.set(controller.getCurrentAdIndexInAdGroup());
              mediaItemRef.set(controller.getCurrentMediaItem());
              playWhenReadyRef.set(controller.getPlayWhenReady());
              playbackSuppressionReasonRef.set(controller.getPlaybackSuppressionReason());
              playbackStateRef.set(controller.getPlaybackState());
              isPlayingRef.set(controller.isPlaying());
              isLoadingRef.set(controller.isLoading());
              isShuffleModeEnabledRef.set(controller.getShuffleModeEnabled());
              repeatModeRef.set(controller.getRepeatMode());
            });

    assertThat(currentPositionMsRef.get()).isEqualTo(currentPositionMs);
    assertThat(contentPositionMsRef.get()).isEqualTo(contentPositionMs);
    assertThat(durationMsRef.get()).isEqualTo(durationMs);
    assertThat(bufferedPositionMsRef.get()).isEqualTo(bufferedPositionMs);
    assertThat(bufferedPercentageRef.get()).isEqualTo(bufferedPercentage);
    assertThat(totalBufferedDurationMsRef.get()).isEqualTo(totalBufferedDurationMs);
    assertThat(currentLiveOffsetMsRef.get()).isEqualTo(currentLiveOffsetMs);
    assertThat(contentDurationMsRef.get()).isEqualTo(contentDurationMs);
    assertThat(contentBufferedPositionMsRef.get()).isEqualTo(contentBufferedPositionMs);
    assertThat(playbackParametersRef.get()).isEqualTo(playbackParameters);
    assertThat(isPlayingAdRef.get()).isEqualTo(isPlayingAd);
    assertThat(currentAdGroupIndexRef.get()).isEqualTo(currentAdGroupIndex);
    assertThat(currentAdIndexInAdGroupRef.get()).isEqualTo(currentAdIndexInAdGroup);
    MediaTestUtils.assertMediaIdEquals(currentMediaItem, mediaItemRef.get());
    assertThat(playWhenReadyRef.get()).isEqualTo(playWhenReady);
    assertThat(playbackSuppressionReasonRef.get()).isEqualTo(playbackSuppressionReason);
    assertThat(playbackStateRef.get()).isEqualTo(playbackState);
    assertThat(isPlayingRef.get()).isEqualTo(isPlaying);
    assertThat(isLoadingRef.get()).isEqualTo(isLoading);
    assertThat(isShuffleModeEnabledRef.get()).isEqualTo(isShuffleModeEnabled);
    assertThat(repeatModeRef.get()).isEqualTo(repeatMode);
  }

  @Test
  public void getPlayerError() throws Exception {
    ExoPlaybackException testPlayerError = ExoPlaybackException.createForRemote("test");

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setPlayerError(testPlayerError).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    ExoPlaybackException playerError =
        threadTestRule.getHandler().postAndSync(controller::getPlayerError);
    assertThat(TestUtils.equals(playerError, testPlayerError)).isTrue();
  }

  @Test
  public void getVideoSize_returnsVideoSizeOfPlayerInSession() throws Exception {
    VideoSize testVideoSize =
        new VideoSize(
            /* width= */ 100,
            /* height= */ 42,
            /* unappliedRotationDegrees= */ 90,
            /* pixelWidthHeightRatio= */ 1.2f);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setVideoSize(testVideoSize).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    VideoSize videoSize = threadTestRule.getHandler().postAndSync(controller::getVideoSize);
    assertThat(videoSize).isEqualTo(testVideoSize);
  }

  @Test
  public void futuresCompleted_AvailableCommandsChange() throws Exception {
    RemoteMediaSession session = remoteSession;
    MediaController controller = controllerTestRule.createController(session.getToken());

    SessionCommands.Builder builder = new SessionCommands.Builder();
    SessionCommand setRatingCommand =
        new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING);
    SessionCommand customCommand = new SessionCommand("custom", null);

    int trials = 100;
    CountDownLatch latch = new CountDownLatch(trials * 2);

    for (int trial = 0; trial < trials; trial++) {
      if (trial % 2 == 0) {
        builder.add(setRatingCommand);
        builder.add(customCommand);
      } else {
        builder.remove(setRatingCommand);
        builder.remove(customCommand);
      }
      session.setAvailableCommands(builder.build(), Player.Commands.EMPTY);

      String testMediaId = "testMediaId";
      Rating testRating = new HeartRating(/* hasHeart= */ true);
      controller.setRating(testMediaId, testRating).addListener(latch::countDown, Runnable::run);
      controller
          .sendCustomCommand(customCommand, null)
          .addListener(latch::countDown, Runnable::run);
    }

    assertWithMessage("All futures should be completed")
        .that(latch.await(LONG_TIMEOUT_MS, MILLISECONDS))
        .isTrue();
  }

  @Test
  public void getPlaylistMetadata_returnsPlaylistMetadataOfPlayerInSession() throws Exception {
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setTitle("title").build();
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaylistMetadata(playlistMetadata)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());

    assertThat(threadTestRule.getHandler().postAndSync(controller::getPlaylistMetadata))
        .isEqualTo(playlistMetadata);
  }

  @Test
  public void getAudioAttributes_returnsAudioAttributesOfPlayerInSession() throws Exception {
    AudioAttributes testAttributes =
        new AudioAttributes.Builder().setContentType(C.CONTENT_TYPE_MUSIC).build();

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setAudioAttributes(testAttributes).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    AudioAttributes attributes =
        threadTestRule.getHandler().postAndSync(controller::getAudioAttributes);
    assertThat(attributes).isEqualTo(testAttributes);
  }

  @Test
  public void getVolume_returnsVolumeOfPlayerInSession() throws Exception {
    float testVolume = .5f;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setVolume(testVolume).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    float volume = threadTestRule.getHandler().postAndSync(controller::getVolume);
    assertThat(volume).isEqualTo(testVolume);
  }

  @Test
  public void getCurrentWindowIndex() throws Exception {
    int testWindowIndex = 1;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setCurrentWindowIndex(testWindowIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int currentWindowIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentWindowIndex);

    assertThat(currentWindowIndex).isEqualTo(testWindowIndex);
  }

  @Test
  public void getCurrentPeriodIndex() throws Exception {
    int testPeriodIndex = 1;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setCurrentPeriodIndex(testPeriodIndex)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int currentPeriodIndex =
        threadTestRule.getHandler().postAndSync(controller::getCurrentPeriodIndex);

    assertThat(currentPeriodIndex).isEqualTo(testPeriodIndex);
  }

  @Test
  public void getPreviousWindowIndex() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 3);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(timeline)
            .setCurrentWindowIndex(1)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .setShuffleModeEnabled(false)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int previousWindowIndex =
        threadTestRule.getHandler().postAndSync(controller::getPreviousWindowIndex);

    assertThat(previousWindowIndex).isEqualTo(0);
  }

  @Test
  public void getPreviousWindowIndex_withRepeatModeOne() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 3);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(timeline)
            .setCurrentWindowIndex(1)
            .setRepeatMode(Player.REPEAT_MODE_ONE)
            .setShuffleModeEnabled(false)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int previousWindowIndex =
        threadTestRule.getHandler().postAndSync(controller::getPreviousWindowIndex);

    assertThat(previousWindowIndex).isEqualTo(0);
  }

  @Test
  public void getPreviousWindowIndex_atTheFirstWindow() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 3);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(timeline)
            .setCurrentWindowIndex(0)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .setShuffleModeEnabled(false)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int previousWindowIndex =
        threadTestRule.getHandler().postAndSync(controller::getPreviousWindowIndex);

    assertThat(previousWindowIndex).isEqualTo(C.INDEX_UNSET);
  }

  @Test
  public void getPreviousWindowIndex_atTheFirstWindowWithRepeatModeAll() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 3);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(timeline)
            .setCurrentWindowIndex(0)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .setShuffleModeEnabled(false)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int previousWindowIndex =
        threadTestRule.getHandler().postAndSync(controller::getPreviousWindowIndex);

    assertThat(previousWindowIndex).isEqualTo(2);
  }

  @Test
  public void getPreviousWindowIndex_withShuffleModeEnabled() throws Exception {
    Timeline timeline =
        new PlaylistTimeline(
            MediaTestUtils.createConvergedMediaItems(/* size= */ 3),
            /* shuffledIndices= */ new int[] {0, 2, 1});
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(timeline)
            .setCurrentWindowIndex(2)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .setShuffleModeEnabled(true)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int previousWindowIndex =
        threadTestRule.getHandler().postAndSync(controller::getPreviousWindowIndex);

    assertThat(previousWindowIndex).isEqualTo(0);
  }

  @Test
  public void getNextWindowIndex() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 3);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(timeline)
            .setCurrentWindowIndex(1)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .setShuffleModeEnabled(false)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int nextWindowIndex = threadTestRule.getHandler().postAndSync(controller::getNextWindowIndex);

    assertThat(nextWindowIndex).isEqualTo(2);
  }

  @Test
  public void getNextWindowIndex_withRepeatModeOne() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 3);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(timeline)
            .setCurrentWindowIndex(1)
            .setRepeatMode(Player.REPEAT_MODE_ONE)
            .setShuffleModeEnabled(false)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int nextWindowIndex = threadTestRule.getHandler().postAndSync(controller::getNextWindowIndex);

    assertThat(nextWindowIndex).isEqualTo(2);
  }

  @Test
  public void getNextWindowIndex_atTheLastWindow() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 3);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(timeline)
            .setCurrentWindowIndex(2)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .setShuffleModeEnabled(false)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int nextWindowIndex = threadTestRule.getHandler().postAndSync(controller::getNextWindowIndex);

    assertThat(nextWindowIndex).isEqualTo(C.INDEX_UNSET);
  }

  @Test
  public void getNextWindowIndex_atTheLastWindowWithRepeatModeAll() throws Exception {
    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 3);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(timeline)
            .setCurrentWindowIndex(2)
            .setRepeatMode(Player.REPEAT_MODE_ALL)
            .setShuffleModeEnabled(false)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int nextWindowIndex = threadTestRule.getHandler().postAndSync(controller::getNextWindowIndex);

    assertThat(nextWindowIndex).isEqualTo(0);
  }

  @Test
  public void getNextWindowIndex_withShuffleModeEnabled() throws Exception {
    Timeline timeline =
        new PlaylistTimeline(
            MediaTestUtils.createConvergedMediaItems(/* size= */ 3),
            /* shuffledIndices= */ new int[] {0, 2, 1});
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setTimeline(timeline)
            .setCurrentWindowIndex(2)
            .setRepeatMode(Player.REPEAT_MODE_OFF)
            .setShuffleModeEnabled(true)
            .build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int nextWindowIndex = threadTestRule.getHandler().postAndSync(controller::getNextWindowIndex);

    assertThat(nextWindowIndex).isEqualTo(1);
  }

  @Test
  public void getMediaItemCount() throws Exception {
    int windowCount = 3;
    Timeline timeline = MediaTestUtils.createTimeline(windowCount);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setTimeline(timeline).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    int mediaItemCount = threadTestRule.getHandler().postAndSync(controller::getMediaItemCount);

    assertThat(mediaItemCount).isEqualTo(windowCount);
  }

  @Test
  public void getMediaItemAt() throws Exception {
    int windowCount = 3;
    int windowIndex = 1;
    Timeline timeline = MediaTestUtils.createTimeline(windowCount);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setTimeline(timeline).build();
    remoteSession.setPlayer(playerConfig);

    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    MediaItem mediaItem =
        threadTestRule.getHandler().postAndSync(() -> controller.getMediaItemAt(windowIndex));

    assertThat(mediaItem)
        .isEqualTo(timeline.getWindow(windowIndex, new Timeline.Window()).mediaItem);
  }

  private RemoteMediaSession createRemoteMediaSession(String id, Bundle tokenExtras)
      throws Exception {
    RemoteMediaSession session = new RemoteMediaSession(id, context, tokenExtras);
    remoteSessionList.add(session);
    return session;
  }

  @Test
  public void getCurrentPosition_whenNotPlaying_doesNotAdvance() throws Exception {
    long testCurrentPositionMs = 100L;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setIsPlaying(false)
            .setCurrentPosition(testCurrentPositionMs)
            .setDuration(10_000L)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());

    long currentPositionMs =
        threadTestRule
            .getHandler()
            .postAndSync(
                () -> {
                  controller.setTimeDiffMs(50L);
                  return controller.getCurrentPosition();
                });

    assertThat(currentPositionMs).isEqualTo(testCurrentPositionMs);
  }

  @Test
  public void getCurrentPosition_whenPlaying_advances() throws Exception {
    long testCurrentPosition = 100L;
    PlaybackParameters testPlaybackParameters = new PlaybackParameters(/* speed= */ 2.0f);
    long testTimeDiff = 50L;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setIsPlaying(true)
            .setCurrentPosition(testCurrentPosition)
            .setDuration(10_000L)
            .setPlaybackParameters(testPlaybackParameters)
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());

    long currentPositionMs =
        threadTestRule
            .getHandler()
            .postAndSync(
                () -> {
                  controller.setTimeDiffMs(testTimeDiff);
                  return controller.getCurrentPosition();
                });

    long expectedCurrentPositionMs =
        testCurrentPosition + (long) (testTimeDiff * testPlaybackParameters.speed);
    assertThat(currentPositionMs).isEqualTo(expectedCurrentPositionMs);
  }

  @Test
  public void getContentPosition_whenPlayingAd_doesNotAdvance() throws Exception {
    long testContentPosition = 100L;
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setContentPosition(testContentPosition)
            .setDuration(10_000L)
            .setIsPlaying(true)
            .setIsPlayingAd(true)
            .setPlaybackParameters(new PlaybackParameters(/* speed= */ 2.0f))
            .build();
    remoteSession.setPlayer(playerConfig);
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());

    long contentPositionMs =
        threadTestRule
            .getHandler()
            .postAndSync(
                () -> {
                  controller.setTimeDiffMs(50L);
                  return controller.getContentPosition();
                });

    assertThat(contentPositionMs).isEqualTo(testContentPosition);
  }

  @Test
  public void getBufferedPosition_withPeriodicUpdate_updatedWithoutCallback() throws Exception {
    long testBufferedPosition = 999L;
    MediaController controller = controllerTestRule.createController(remoteSession.getToken());
    remoteSession.getMockPlayer().setBufferedPosition(testBufferedPosition);

    remoteSession.setSessionPositionUpdateDelayMs(0L);

    PollingCheck.waitFor(
        TIMEOUT_MS,
        () -> {
          long bufferedPosition =
              threadTestRule.getHandler().postAndSync(controller::getBufferedPosition);
          return bufferedPosition == testBufferedPosition;
        });
  }
}
