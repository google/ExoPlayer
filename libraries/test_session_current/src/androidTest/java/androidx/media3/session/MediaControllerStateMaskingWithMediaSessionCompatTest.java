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

import static androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK;
import static androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED;
import static androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION;
import static androidx.media3.common.Player.EVENT_PLAYBACK_PARAMETERS_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAY_WHEN_READY_CHANGED;
import static androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY;
import static androidx.media3.common.Player.EVENT_REPEAT_MODE_CHANGED;
import static androidx.media3.common.Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED;
import static androidx.media3.common.Player.EVENT_TIMELINE_CHANGED;
import static androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK;
import static androidx.media3.common.Player.REPEAT_MODE_ALL;
import static androidx.media3.common.Player.STATE_BUFFERING;
import static androidx.media3.common.Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED;
import static androidx.media3.test.session.common.TestUtils.NO_RESPONSE_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.RemoteException;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.annotation.Nullable;
import androidx.media3.common.FlagSet;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.DiscontinuityReason;
import androidx.media3.common.Player.Events;
import androidx.media3.common.Player.MediaItemTransitionReason;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Player.State;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
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

/** Tests for state masking {@link MediaController} ({@link MediaControllerImplLegacy}) calls. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerStateMaskingWithMediaSessionCompatTest {

  private static final String TAG = "MCwMSC";

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  private final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaControllerStateMaskingTest");
  private final MediaControllerTestRule controllerTestRule =
      new MediaControllerTestRule(threadTestRule);

  @Rule
  public final TestRule chain = RuleChain.outerRule(threadTestRule).around(controllerTestRule);

  private Context context;
  private RemoteMediaSessionCompat session;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    session = new RemoteMediaSessionCompat(TAG, context);
    session.setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);
  }

  @After
  public void cleanUp() throws RemoteException {
    if (session != null) {
      session.cleanUp();
      session = null;
    }
  }

  @Test
  public void setPlayWhenReady() throws Exception {
    boolean testPlayWhenReady = true;
    boolean testIsPlaying = true;
    Events testEvents =
        new Events(
            new FlagSet.Builder()
                .addAll(EVENT_PLAY_WHEN_READY_CHANGED, EVENT_IS_PLAYING_CHANGED)
                .build());

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PAUSED, /* position= */ 0, /* playbackSpeed= */ 1.0f)
            .build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicBoolean playWhenReadyFromCallbackRef = new AtomicBoolean();
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
    AtomicBoolean isPlayingFromGetterRef = new AtomicBoolean();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setPlayWhenReady(testPlayWhenReady);
              playWhenReadyFromGetterRef.set(controller.getPlayWhenReady());
              isPlayingFromGetterRef.set(controller.isPlaying());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playWhenReadyFromCallbackRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(isPlayingFromCallbackRef.get()).isEqualTo(testIsPlaying);
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(playWhenReadyFromGetterRef.get()).isEqualTo(testPlayWhenReady);
    assertThat(isPlayingFromGetterRef.get()).isEqualTo(testIsPlaying);
  }

  @Test
  public void setShuffleModeEnabled() throws Exception {
    boolean testShuffleModeEnabled = true;
    Events testEvents =
        new Events(new FlagSet.Builder().addAll(EVENT_SHUFFLE_MODE_ENABLED_CHANGED).build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
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
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(shuffleModeEnabledFromGetterRef.get()).isEqualTo(testShuffleModeEnabled);
  }

  @Test
  public void setRepeatMode() throws Exception {
    int testRepeatMode = REPEAT_MODE_ALL;
    Events testEvents = new Events(new FlagSet.Builder().addAll(EVENT_REPEAT_MODE_CHANGED).build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
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
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(repeatModeFromGetterRef.get()).isEqualTo(testRepeatMode);
  }

  @Test
  public void setPlaybackParameters() throws Exception {
    PlaybackParameters testPlaybackParameters = new PlaybackParameters(2f, 2f);
    Events testEvents =
        new Events(new FlagSet.Builder().addAll(EVENT_PLAYBACK_PARAMETERS_CHANGED).build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
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
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(playbackParametersFromGetterRef.get()).isEqualTo(testPlaybackParameters);
  }

  @Test
  public void prepare() throws Exception {
    int testPlaybackState = Player.STATE_ENDED;
    Events testEvents =
        new Events(new FlagSet.Builder().addAll(EVENT_PLAYBACK_STATE_CHANGED).build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
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
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(playbackStateFromGetterRef.get()).isEqualTo(testPlaybackState);
    assertThat(playerErrorRef.get()).isNull();
  }

  @Test
  public void stop() throws Exception {
    long duration = 6000L;
    long activeQueueItemId = 0;
    long initialCurrentPosition = 3000L;
    long initialBufferedPosition = duration;
    int testPlaybackState = Player.STATE_IDLE;
    long testCurrentPosition = 3000L;
    long testBufferedPosition = testCurrentPosition;
    int testBufferedPercentage = 50;
    long testTotalBufferedDuration = testBufferedPosition - testCurrentPosition;
    List<QueueItem> testQueue = MediaTestUtils.createQueueItems(3);
    Events testEvents =
        new Events(new FlagSet.Builder().addAll(EVENT_PLAYBACK_STATE_CHANGED).build());
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setActiveQueueItemId(activeQueueItemId)
            .setBufferedPosition(initialBufferedPosition)
            .setState(
                PlaybackStateCompat.STATE_PAUSED,
                /* position= */ initialCurrentPosition,
                /* playbackSpeed= */ 1.0f)
            .build());
    session.setMetadata(
        new MediaMetadataCompat.Builder()
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .build());
    session.setQueue(testQueue);

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
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
              timelineFromGetterRef.set(controller.getCurrentTimeline());
              currentPositionFromGetterRef.set(controller.getCurrentPosition());
              bufferedPositionFromGetterRef.set(controller.getBufferedPosition());
              bufferedPercentageFromGetterRef.set(controller.getBufferedPercentage());
              totalBufferedDurationFromGetterRef.set(controller.getTotalBufferedDuration());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateFromCallbackRef.get()).isEqualTo(testPlaybackState);
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(playbackStateFromGetterRef.get()).isEqualTo(testPlaybackState);
    assertThat(timelineFromGetterRef.get().getWindowCount()).isEqualTo(testQueue.size());
    assertThat(currentPositionFromGetterRef.get()).isEqualTo(testCurrentPosition);
    assertThat(bufferedPositionFromGetterRef.get()).isEqualTo(testBufferedPosition);
    assertThat(bufferedPercentageFromGetterRef.get()).isEqualTo(testBufferedPercentage);
    assertThat(totalBufferedDurationFromGetterRef.get()).isEqualTo(testTotalBufferedDuration);
  }

  @Test
  public void seekTo_withNewMediaItemIndex() throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(3);
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long initialPosition = 8_000;
    long initialBufferedPosition = 9_200;
    int initialIndex = 0;
    long testPosition = 9_000;
    long testBufferedPosition = 0;
    int testIndex = 1;
    Events testEvents =
        new Events(
            new FlagSet.Builder()
                .addAll(
                    EVENT_POSITION_DISCONTINUITY,
                    EVENT_PLAYBACK_STATE_CHANGED,
                    EVENT_MEDIA_ITEM_TRANSITION)
                .build());

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, initialPosition, /* playbackSpeed= */ 1.0f)
            .setBufferedPosition(initialBufferedPosition)
            .setActiveQueueItemId(queue.get(initialIndex).getQueueId())
            .build());
    session.setQueue(queue);

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(4);
    AtomicReference<PositionInfo> oldPositionInfoRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicInteger discontinuityReasonRef = new AtomicInteger();
    AtomicInteger playbackStateRef = new AtomicInteger();
    AtomicReference<MediaItem> mediaItemRef = new AtomicReference<>();
    AtomicInteger mediaItemTransitionReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            oldPositionInfoRef.set(oldPosition);
            newPositionInfoRef.set(newPosition);
            discontinuityReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onPlaybackStateChanged(@State int playbackState) {
            playbackStateRef.set(playbackState);
            latch.countDown();
          }

          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @MediaItemTransitionReason int reason) {
            mediaItemRef.set(mediaItem);
            mediaItemTransitionReasonRef.set(reason);
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
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(testIndex, testPosition);
              currentPositionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(oldPositionInfoRef.get().positionMs).isEqualTo(initialPosition);
    assertThat(oldPositionInfoRef.get().mediaItemIndex).isEqualTo(initialIndex);
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertThat(newPositionInfoRef.get().mediaItemIndex).isEqualTo(testIndex);
    assertThat(discontinuityReasonRef.get()).isEqualTo(DISCONTINUITY_REASON_SEEK);
    assertThat(playbackStateRef.get()).isEqualTo(STATE_BUFFERING);
    assertThat(mediaItemRef.get()).isEqualTo(mediaItems.get(testIndex));
    assertThat(mediaItemTransitionReasonRef.get()).isEqualTo(MEDIA_ITEM_TRANSITION_REASON_SEEK);
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
  }

  @Test
  public void seekTo_whilePlayingAd_ignored() throws Exception {
    long testPositionMs = 500L;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PAUSED, /* position= */ 0, /* playbackSpeed= */ 1.0f)
            .build());
    session.setMetadata(
        new MediaMetadataCompat.Builder()
            .putLong(MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT, /* value= */ 1)
            .build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            latch.countDown();
          }
        };
    threadTestRule.getHandler().postAndSync(() -> controller.addListener(listener));
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(/* positionMs= */ testPositionMs);
            });

    assertThat(latch.await(NO_RESPONSE_TIMEOUT_MS, MILLISECONDS)).isFalse();
  }

  @Test
  public void seekTo_seekBackwardWithinSameMediaItem_resetsBufferedPosition() throws Exception {
    long initialPosition = 8_000L;
    long initialBufferedPosition = 9_200L;
    int initialIndex = 0;
    long testPosition = 7_000L;
    long testBufferedPosition = testPosition;
    long testTotalBufferedDuration = testBufferedPosition - testPosition;
    Events testEvents =
        new Events(
            new FlagSet.Builder()
                .addAll(EVENT_POSITION_DISCONTINUITY, EVENT_PLAYBACK_STATE_CHANGED)
                .build());

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, initialPosition, /* playbackSpeed= */ 1.0f)
            .setBufferedPosition(initialBufferedPosition)
            .build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<PositionInfo> oldPositionInfoRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicInteger discontinuityReasonRef = new AtomicInteger();
    AtomicInteger playbackStateRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            oldPositionInfoRef.set(oldPosition);
            newPositionInfoRef.set(newPosition);
            discontinuityReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onPlaybackStateChanged(@State int playbackState) {
            playbackStateRef.set(playbackState);
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
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(testPosition);
              currentPositionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
              totalBufferedDurationRef.set(controller.getTotalBufferedDuration());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(oldPositionInfoRef.get().positionMs).isEqualTo(initialPosition);
    assertThat(oldPositionInfoRef.get().mediaItemIndex).isEqualTo(initialIndex);
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertThat(newPositionInfoRef.get().mediaItemIndex).isEqualTo(initialIndex);
    assertThat(discontinuityReasonRef.get()).isEqualTo(DISCONTINUITY_REASON_SEEK);
    assertThat(playbackStateRef.get()).isEqualTo(STATE_BUFFERING);
    assertThat(onEventsRef.get()).isEqualTo(testEvents);

    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
    assertThat(totalBufferedDurationRef.get()).isEqualTo(testTotalBufferedDuration);
  }

  @Test
  public void seekTo_seekForwardWithinSameMediaItem_keepsTheBufferedPosition() throws Exception {
    long initialPosition = 8_000L;
    long initialBufferedPosition = 9_200L;
    int initialIndex = 0;
    long testPosition = 9_000L;
    long testBufferedPosition = initialBufferedPosition;
    long testTotalBufferedDuration = testBufferedPosition - testPosition;
    Events testEvents =
        new Events(
            new FlagSet.Builder()
                .addAll(EVENT_POSITION_DISCONTINUITY, EVENT_PLAYBACK_STATE_CHANGED)
                .build());

    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, initialPosition, /* playbackSpeed= */ 1.0f)
            .setBufferedPosition(initialBufferedPosition)
            .build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<PositionInfo> oldPositionInfoRef = new AtomicReference<>();
    AtomicReference<PositionInfo> newPositionInfoRef = new AtomicReference<>();
    AtomicInteger discontinuityReasonRef = new AtomicInteger();
    AtomicInteger playbackStateRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
            oldPositionInfoRef.set(oldPosition);
            newPositionInfoRef.set(newPosition);
            discontinuityReasonRef.set(reason);
            latch.countDown();
          }

          @Override
          public void onPlaybackStateChanged(@State int playbackState) {
            playbackStateRef.set(playbackState);
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
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.seekTo(testPosition);
              currentPositionRef.set(controller.getCurrentPosition());
              bufferedPositionRef.set(controller.getBufferedPosition());
              totalBufferedDurationRef.set(controller.getTotalBufferedDuration());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(oldPositionInfoRef.get().positionMs).isEqualTo(initialPosition);
    assertThat(oldPositionInfoRef.get().mediaItemIndex).isEqualTo(initialIndex);
    assertThat(newPositionInfoRef.get().positionMs).isEqualTo(testPosition);
    assertThat(newPositionInfoRef.get().mediaItemIndex).isEqualTo(initialIndex);
    assertThat(discontinuityReasonRef.get()).isEqualTo(DISCONTINUITY_REASON_SEEK);
    assertThat(playbackStateRef.get()).isEqualTo(STATE_BUFFERING);
    assertThat(onEventsRef.get()).isEqualTo(testEvents);

    assertThat(currentPositionRef.get()).isEqualTo(testPosition);
    assertThat(bufferedPositionRef.get()).isEqualTo(testBufferedPosition);
    assertThat(totalBufferedDurationRef.get()).isEqualTo(testTotalBufferedDuration);
  }

  @Test
  public void addMediaItems() throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems("a", "b", "c");
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long testPosition = 200L;
    int testCurrentMediaItemIndex = 1;
    MediaItem testCurrentMediaItem = mediaItems.get(testCurrentMediaItemIndex);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPosition, /* playbackSpeed= */ 1.0f)
            .setActiveQueueItemId(queue.get(testCurrentMediaItemIndex).getQueueId())
            .build());
    session.setQueue(queue);
    List<MediaItem> newMediaItems = MediaTestUtils.createMediaItems("A", "B");
    int testAddIndex = 2;
    List<MediaItem> testMediaItems = new ArrayList<>();
    testMediaItems.addAll(mediaItems);
    testMediaItems.addAll(testAddIndex, newMediaItems);
    Events testEvents = new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.addMediaItems(testAddIndex, newMediaItems);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), testMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(currentMediaItemRef.get()).isEqualTo(testCurrentMediaItem);
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), testMediaItems);
  }

  @Test
  public void addMediaItems_beforeCurrentMediaItemIndex_shiftsCurrentMediaItemIndex()
      throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems("a", "b", "c");
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long testPosition = 200L;
    int initialMediaItemIndex = 2;
    MediaItem testCurrentMediaItem = mediaItems.get(initialMediaItemIndex);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPosition, /* playbackSpeed= */ 1.0f)
            .setActiveQueueItemId(queue.get(initialMediaItemIndex).getQueueId())
            .build());
    session.setQueue(queue);
    List<MediaItem> newMediaItems = MediaTestUtils.createMediaItems("A", "B");
    int testAddIndex = 1;
    List<MediaItem> testMediaItems = new ArrayList<>();
    testMediaItems.addAll(mediaItems);
    testMediaItems.addAll(testAddIndex, newMediaItems);
    int testCurrentMediaItemIndex = testMediaItems.indexOf(testCurrentMediaItem);
    Events testEvents = new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.addMediaItems(testAddIndex, newMediaItems);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), testMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(currentMediaItemRef.get()).isEqualTo(testCurrentMediaItem);
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), testMediaItems);
  }

  @Test
  public void removeMediaItems() throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(5);
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long testPosition = 200L;
    int testCurrentMediaItemIndex = 0;
    MediaItem testCurrentMediaItem = mediaItems.get(testCurrentMediaItemIndex);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPosition, /* playbackSpeed= */ 1.0f)
            .setActiveQueueItemId(queue.get(testCurrentMediaItemIndex).getQueueId())
            .build());
    session.setQueue(queue);
    int fromIndex = 1;
    int toIndex = 3;
    List<MediaItem> testMediaItems = new ArrayList<>(mediaItems.subList(0, fromIndex));
    testMediaItems.addAll(mediaItems.subList(toIndex, mediaItems.size()));
    Events testEvents = new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.removeMediaItems(fromIndex, toIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), testMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(currentMediaItemRef.get()).isEqualTo(testCurrentMediaItem);
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), testMediaItems);
  }

  @Test
  public void removeMediaItems_beforeCurrentMediaItemIndex_shiftsCurrentMediaItemIndex()
      throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(5);
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long testPosition = 200L;
    int initialMediaItemIndex = 4;
    MediaItem testCurrentMediaItem = mediaItems.get(initialMediaItemIndex);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPosition, /* playbackSpeed= */ 1.0f)
            .setActiveQueueItemId(queue.get(initialMediaItemIndex).getQueueId())
            .build());
    session.setQueue(queue);
    int testFromIndex = 1;
    int testToIndex = 3;
    List<MediaItem> testMediaItems = new ArrayList<>(mediaItems.subList(0, testFromIndex));
    testMediaItems.addAll(mediaItems.subList(testToIndex, mediaItems.size()));
    int testCurrentMediaItemIndex = testMediaItems.indexOf(testCurrentMediaItem);
    Events testEvents = new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.removeMediaItems(testFromIndex, testToIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), testMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(currentMediaItemRef.get()).isEqualTo(testCurrentMediaItem);
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), testMediaItems);
  }

  @Test
  public void removeMediaItems_includeCurrentMediaItem_movesCurrentItem() throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(5);
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long testPosition = 200L;
    int initialMediaItemIndex = 2;
    MediaItem testCurrentMediaItem = mediaItems.get(initialMediaItemIndex);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPosition, /* playbackSpeed= */ 1.0f)
            .setActiveQueueItemId(queue.get(initialMediaItemIndex).getQueueId())
            .build());
    session.setQueue(queue);
    int testFromIndex = 1;
    int testToIndex = 3;
    List<MediaItem> testMediaItems = new ArrayList<>(mediaItems.subList(0, testFromIndex));
    testMediaItems.addAll(mediaItems.subList(testToIndex, mediaItems.size()));
    int testCurrentMediaItemIndex = testFromIndex;
    Events testEvents = new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.removeMediaItems(testFromIndex, testToIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), testMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), testMediaItems);
  }

  @Test
  public void moveMediaItems() throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(5);
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long testPosition = 200L;
    int testCurrentMediaItemIndex = 0;
    MediaItem testCurrentMediaItem = mediaItems.get(testCurrentMediaItemIndex);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPosition, /* playbackSpeed= */ 1.0f)
            .setActiveQueueItemId(queue.get(testCurrentMediaItemIndex).getQueueId())
            .build());
    session.setQueue(queue);
    int testFromIndex = 1;
    int testToIndex = 3;
    int testNewIndex = 2;
    List<MediaItem> testMediaItems = new ArrayList<>(mediaItems);
    Util.moveItems(testMediaItems, testFromIndex, testToIndex, testNewIndex);
    Events testEvents = new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.moveMediaItems(testFromIndex, testToIndex, testNewIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), testMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    assertThat(currentMediaItemRef.get()).isEqualTo(testCurrentMediaItem);
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), testMediaItems);
  }

  @Test
  public void moveMediaItems_withMovingCurrentMediaItem_changesCurrentItem() throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(5);
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long testPosition = 200L;
    int initialCurrentMediaItemIndex = 1;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPosition, /* playbackSpeed= */ 1.0f)
            .setActiveQueueItemId(queue.get(initialCurrentMediaItemIndex).getQueueId())
            .build());
    session.setQueue(queue);
    int testFromIndex = 1;
    int testToIndex = 3;
    int testNewIndex = 2;
    List<MediaItem> testMediaItems = new ArrayList<>(mediaItems);
    Util.moveItems(testMediaItems, testFromIndex, testToIndex, testNewIndex);
    Events testEvents = new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());
    // The item at testToIndex becomes current media item after removed,
    // and it remains as current media item when removed items are inserted back.
    int testCurrentMediaItemIndex = testMediaItems.indexOf(mediaItems.get(testToIndex));

    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.moveMediaItems(testFromIndex, testToIndex, testNewIndex);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), testMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(testEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(testCurrentMediaItemIndex);
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), testMediaItems);
  }

  @Test
  public void seekTo_indexLargerThanPlaylist_isIgnored() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    AtomicInteger mediaItemIndexAfterSeek = new AtomicInteger();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.setMediaItem(MediaItem.fromUri("http://test"));

              controller.seekTo(/* windowIndex= */ 1, /* positionMs= */ 1000);

              mediaItemIndexAfterSeek.set(controller.getCurrentMediaItemIndex());
            });

    assertThat(mediaItemIndexAfterSeek.get()).isEqualTo(0);
  }

  @Test
  public void addMediaItems_indexLargerThanPlaylist_addsToEndOfPlaylist() throws Exception {
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
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
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
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
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
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
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
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
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
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
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
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
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems("a", "b", "c");
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long testPosition = 200L;
    int initialMediaItemIndex = 2;
    MediaItem testCurrentMediaItem = mediaItems.get(initialMediaItemIndex);
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPosition, /* playbackSpeed= */ 1.0f)
            .setActiveQueueItemId(queue.get(initialMediaItemIndex).getQueueId())
            .build());
    session.setQueue(queue);
    List<MediaItem> newMediaItems = MediaTestUtils.createMediaItems("A", "B");
    List<MediaItem> expectedMediaItems = new ArrayList<>();
    expectedMediaItems.add(mediaItems.get(0));
    expectedMediaItems.addAll(newMediaItems);
    expectedMediaItems.add(mediaItems.get(2));
    Events expectedEvents =
        new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(3); // 2x onTimelineChanged + onEvents
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItems(/* fromIndex= */ 1, /* toIndex= */ 2, newMediaItems);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), expectedMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(expectedEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(3);
    assertThat(currentMediaItemRef.get()).isEqualTo(testCurrentMediaItem);
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), expectedMediaItems);
  }

  @Test
  public void replaceMediaItems_replacingCurrentItem_correctMasking() throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems("a", "b", "c");
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long testPosition = 200L;
    int initialMediaItemIndex = 1;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPosition, /* playbackSpeed= */ 1.0f)
            .setActiveQueueItemId(queue.get(initialMediaItemIndex).getQueueId())
            .build());
    session.setQueue(queue);
    List<MediaItem> newMediaItems = MediaTestUtils.createMediaItems("A", "B");
    List<MediaItem> expectedMediaItems = new ArrayList<>();
    expectedMediaItems.add(mediaItems.get(0));
    expectedMediaItems.addAll(newMediaItems);
    expectedMediaItems.add(mediaItems.get(2));
    Events expectedEvents =
        new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(3); // 2x onTimelineChanged + onEvents
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItems(/* fromIndex= */ 1, /* toIndex= */ 2, newMediaItems);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), expectedMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(expectedEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(1);
    assertThat(currentMediaItemRef.get()).isEqualTo(newMediaItems.get(0));
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), expectedMediaItems);
  }

  @Test
  public void replaceMediaItems_replacingCurrentItemWithEmptyListAndSubsequentItem_correctMasking()
      throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems("a", "b", "c");
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long testPosition = 200L;
    int initialMediaItemIndex = 1;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPosition, /* playbackSpeed= */ 1.0f)
            .setActiveQueueItemId(queue.get(initialMediaItemIndex).getQueueId())
            .build());
    session.setQueue(queue);
    List<MediaItem> expectedMediaItems = new ArrayList<>();
    expectedMediaItems.add(mediaItems.get(0));
    expectedMediaItems.add(mediaItems.get(2));
    Events expectedEvents =
        new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItems(
                  /* fromIndex= */ 1, /* toIndex= */ 2, ImmutableList.of());
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), expectedMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(expectedEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(1);
    assertThat(currentMediaItemRef.get()).isEqualTo(expectedMediaItems.get(1));
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), expectedMediaItems);
  }

  @Test
  public void
      replaceMediaItems_replacingCurrentItemWithEmptyListAndNoSubsequentItem_correctMasking()
          throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems("a", "b");
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long testPosition = 200L;
    int initialMediaItemIndex = 1;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPosition, /* playbackSpeed= */ 1.0f)
            .setActiveQueueItemId(queue.get(initialMediaItemIndex).getQueueId())
            .build());
    session.setQueue(queue);
    List<MediaItem> expectedMediaItems = new ArrayList<>();
    expectedMediaItems.add(mediaItems.get(0));
    Events expectedEvents =
        new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItems(
                  /* fromIndex= */ 1, /* toIndex= */ 2, ImmutableList.of());
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), expectedMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(expectedEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(0);
    assertThat(currentMediaItemRef.get()).isEqualTo(expectedMediaItems.get(0));
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), expectedMediaItems);
  }

  @Test
  public void replaceMediaItems_fromEmpty_correctMasking() throws Exception {
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_STOPPED, /* position= */ 0, /* playbackSpeed= */ 1.0f)
            .build());
    session.setQueue(ImmutableList.of());
    List<MediaItem> newMediaItems = MediaTestUtils.createMediaItems("A", "B");
    Events expectedEvents =
        new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItems(/* fromIndex= */ 0, /* toIndex= */ 0, newMediaItems);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), newMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(expectedEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(0);
    assertThat(currentMediaItemRef.get()).isEqualTo(newMediaItems.get(0));
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), newMediaItems);
  }

  @Test
  public void replaceMediaItems_withInvalidToIndex_correctMasking() throws Exception {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems("a", "b", "c");
    List<QueueItem> queue = MediaTestUtils.convertToQueueItemsWithoutBitmap(mediaItems);
    long testPosition = 200L;
    int initialMediaItemIndex = 1;
    session.setPlaybackState(
        new PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_PAUSED, testPosition, /* playbackSpeed= */ 1.0f)
            .setActiveQueueItemId(queue.get(initialMediaItemIndex).getQueueId())
            .build());
    session.setQueue(queue);
    List<MediaItem> newMediaItems = MediaTestUtils.createMediaItems("A", "B");
    List<MediaItem> expectedMediaItems = new ArrayList<>();
    expectedMediaItems.add(mediaItems.get(0));
    expectedMediaItems.addAll(newMediaItems);
    Events expectedEvents =
        new Events(new FlagSet.Builder().addAll(EVENT_TIMELINE_CHANGED).build());
    MediaController controller = controllerTestRule.createController(session.getSessionToken());
    CountDownLatch latch = new CountDownLatch(3);
    AtomicReference<Timeline> timelineFromParamRef = new AtomicReference<>();
    AtomicInteger timelineChangeReasonRef = new AtomicInteger();
    AtomicReference<Player.Events> onEventsRef = new AtomicReference<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            timelineFromParamRef.set(timeline);
            timelineChangeReasonRef.set(reason);
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
    AtomicReference<MediaItem> currentMediaItemRef = new AtomicReference<>();
    AtomicReference<Timeline> timelineFromGetterRef = new AtomicReference<>();

    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              controller.replaceMediaItems(/* fromIndex= */ 1, /* toIndex= */ 5000, newMediaItems);
              currentMediaItemIndexRef.set(controller.getCurrentMediaItemIndex());
              currentMediaItemRef.set(controller.getCurrentMediaItem());
              timelineFromGetterRef.set(controller.getCurrentTimeline());
            });

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaTestUtils.assertTimelineContains(timelineFromParamRef.get(), expectedMediaItems);
    assertThat(timelineChangeReasonRef.get()).isEqualTo(TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
    assertThat(onEventsRef.get()).isEqualTo(expectedEvents);
    assertThat(currentMediaItemIndexRef.get()).isEqualTo(1);
    assertThat(currentMediaItemRef.get()).isEqualTo(newMediaItems.get(0));
    MediaTestUtils.assertTimelineContains(timelineFromGetterRef.get(), expectedMediaItems);
  }
}
