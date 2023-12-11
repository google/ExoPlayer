/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.session;

import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.getEventsAsList;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.annotation.Nullable;
import androidx.core.util.Predicate;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.SimpleBasePlayer;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Consumer;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession.ConnectionResult;
import androidx.media3.session.MediaSession.ConnectionResult.AcceptedResultBuilder;
import androidx.media3.test.session.R;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that {@link MediaControllerCompat} receives the expected {@link
 * PlaybackStateCompat.Actions} when connected to a {@link MediaSession}.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerCompatPlaybackStateCompatActionsWithMediaSessionTest {

  private static final String TAG = "MCCPSActionWithMS3";

  @Rule public final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);

  @Test
  public void playerWithCommandPlayPause_actionsPlayAndPauseAndPlayPauseAdvertised()
      throws Exception {
    Player player =
        createPlayerWithAvailableCommand(createDefaultPlayer(), Player.COMMAND_PLAY_PAUSE);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    long actions =
        getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions();

    assertThat(actions & PlaybackStateCompat.ACTION_PLAY_PAUSE).isNotEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PLAY).isNotEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PAUSE).isNotEqualTo(0);

    CountDownLatch latch = new CountDownLatch(2);
    List<Boolean> receivedPlayWhenReady = new ArrayList<>();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
            receivedPlayWhenReady.add(playWhenReady);
            latch.countDown();
          }
        };
    player.addListener(listener);

    controllerCompat.getTransportControls().play();
    controllerCompat.getTransportControls().pause();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedPlayWhenReady).containsExactly(true, false).inOrder();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithoutCommandPlayPause_actionsPlayAndPauseAndPlayPauseNotAdvertised()
      throws Exception {
    Player player =
        createPlayerWithExcludedCommand(createDefaultPlayer(), Player.COMMAND_PLAY_PAUSE);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    long actions =
        getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions();

    assertThat(actions & PlaybackStateCompat.ACTION_PLAY_PAUSE).isEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PLAY).isEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PAUSE).isEqualTo(0);

    AtomicInteger playWhenReadyCalled = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
            playWhenReadyCalled.incrementAndGet();
          }

          @Override
          public void onPlaybackStateChanged(@Player.State int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
              latch.countDown();
            }
          }
        };
    player.addListener(listener);

    // play() & pause() should be a no-op
    controllerCompat.getTransportControls().play();
    controllerCompat.getTransportControls().pause();
    // prepare() should transition the player to STATE_ENDED
    controllerCompat.getTransportControls().prepare();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playWhenReadyCalled.get()).isEqualTo(0);

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithCommandPrepare_actionPrepareAdvertised() throws Exception {
    Player player = createPlayerWithAvailableCommand(createDefaultPlayer(), Player.COMMAND_PREPARE);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_PREPARE)
        .isNotEqualTo(0);

    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(@Player.State int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
              latch.countDown();
            }
          }
        };
    player.addListener(listener);

    // prepare() should transition the player to STATE_ENDED.
    controllerCompat.getTransportControls().prepare();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithoutCommandPrepare_actionPrepareNotAdvertised() throws Exception {
    Player player = createPlayerWithExcludedCommand(createDefaultPlayer(), Player.COMMAND_PREPARE);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_PREPARE)
        .isEqualTo(0);

    AtomicInteger playbackStateChanges = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackStateChanged(@Player.State int playbackState) {
            playbackStateChanges.incrementAndGet();
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            latch.countDown();
          }
        };
    player.addListener(listener);

    // prepare() should be no-op
    controllerCompat.getTransportControls().prepare();
    controllerCompat.getTransportControls().setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateChanges.get()).isEqualTo(0);

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithCommandSeekBack_actionRewindAdvertised() throws Exception {
    Player player =
        createPlayerWithAvailableCommand(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItem(
                      MediaItem.fromUri("asset://media/wav/sample.wav"),
                      /* startPositionMs= */ 500);
                  createdPlayer.prepare();
                }),
            Player.COMMAND_SEEK_BACK);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_REWIND)
        .isNotEqualTo(0);

    AtomicInteger discontinuityReason = new AtomicInteger(-1);
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              Player.PositionInfo oldPosition,
              Player.PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            discontinuityReason.set(reason);
            latch.countDown();
          }
        };
    player.addListener(listener);

    controllerCompat.getTransportControls().rewind();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(discontinuityReason.get()).isEqualTo(Player.DISCONTINUITY_REASON_SEEK);

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithoutCommandSeekBack_actionRewindNotAdvertised() throws Exception {
    Player player =
        createPlayerWithExcludedCommand(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItem(
                      MediaItem.fromUri("asset://media/wav/sample.wav"),
                      /* startPositionMs= */ 500);
                  createdPlayer.prepare();
                }),
            Player.COMMAND_SEEK_BACK);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_REWIND)
        .isEqualTo(0);

    AtomicBoolean receivedOnPositionDiscontinuity = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              Player.PositionInfo oldPosition,
              Player.PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            receivedOnPositionDiscontinuity.set(true);
          }

          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
            latch.countDown();
          }
        };
    player.addListener(listener);

    // rewind() should be no-op.
    controllerCompat.getTransportControls().rewind();
    controllerCompat.getTransportControls().play();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedOnPositionDiscontinuity.get()).isFalse();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithCommandSeekForward_actionFastForwardAdvertised() throws Exception {
    Player player =
        createPlayerWithAvailableCommand(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItem(MediaItem.fromUri("asset://media/wav/sample.wav"));
                  createdPlayer.prepare();
                }),
            Player.COMMAND_SEEK_FORWARD);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_FAST_FORWARD)
        .isNotEqualTo(0);

    AtomicInteger discontinuityReason = new AtomicInteger(-1);
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              Player.PositionInfo oldPosition,
              Player.PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            discontinuityReason.set(reason);
            latch.countDown();
          }
        };
    player.addListener(listener);

    controllerCompat.getTransportControls().fastForward();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(discontinuityReason.get()).isEqualTo(Player.DISCONTINUITY_REASON_SEEK);

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithoutCommandSeekForward_actionFastForwardNotAdvertised() throws Exception {
    Player player =
        createPlayerWithExcludedCommand(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItem(MediaItem.fromUri("asset://media/wav/sample.wav"));
                  createdPlayer.prepare();
                }),
            Player.COMMAND_SEEK_FORWARD);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_FAST_FORWARD)
        .isEqualTo(0);

    AtomicBoolean receivedOnPositionDiscontinuity = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              Player.PositionInfo oldPosition,
              Player.PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            receivedOnPositionDiscontinuity.set(true);
          }

          @Override
          public void onPlayWhenReadyChanged(
              boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
            latch.countDown();
          }
        };
    player.addListener(listener);

    // fastForward() should be no-op
    controllerCompat.getTransportControls().fastForward();
    controllerCompat.getTransportControls().play();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedOnPositionDiscontinuity.get()).isFalse();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithCommandSeekInCurrentMediaItem_actionSeekToAdvertised() throws Exception {
    Player player =
        createPlayerWithAvailableCommand(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItem(MediaItem.fromUri("asset://media/wav/sample.wav"));
                  createdPlayer.prepare();
                }),
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SEEK_TO)
        .isNotEqualTo(0);

    AtomicInteger discontinuityReason = new AtomicInteger(-1);
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              Player.PositionInfo oldPosition,
              Player.PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            discontinuityReason.set(reason);
            latch.countDown();
          }
        };
    player.addListener(listener);

    controllerCompat.getTransportControls().seekTo(100);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(discontinuityReason.get()).isEqualTo(Player.DISCONTINUITY_REASON_SEEK);

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithoutCommandSeekInCurrentMediaItem_actionSeekToNotAdvertised()
      throws Exception {
    Player player =
        createPlayerWithExcludedCommand(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItem(MediaItem.fromUri("asset://media/wav/sample.wav"));
                  createdPlayer.prepare();
                }),
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SEEK_TO)
        .isEqualTo(0);

    AtomicBoolean receiovedOnPositionDiscontinuity = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPositionDiscontinuity(
              Player.PositionInfo oldPosition,
              Player.PositionInfo newPosition,
              @Player.DiscontinuityReason int reason) {
            receiovedOnPositionDiscontinuity.set(true);
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            latch.countDown();
          }
        };
    player.addListener(listener);

    // seekTo() should be no-op.
    controllerCompat.getTransportControls().seekTo(100);
    controllerCompat.getTransportControls().setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receiovedOnPositionDiscontinuity.get()).isFalse();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithCommandSeekToMediaItem_actionSkipToQueueItemAdvertised() throws Exception {
    Player player =
        createPlayerWithAvailableCommand(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItems(
                      ImmutableList.of(
                          MediaItem.fromUri("asset://media/wav/sample.wav"),
                          MediaItem.fromUri("asset://media/wav/sample_rf64.wav")));
                  createdPlayer.prepare();
                }),
            Player.COMMAND_SEEK_TO_MEDIA_ITEM);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM)
        .isNotEqualTo(0);

    AtomicInteger mediaItemTransitionReason = new AtomicInteger(-1);
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            mediaItemTransitionReason.set(reason);
            latch.countDown();
          }
        };
    player.addListener(listener);

    controllerCompat.getTransportControls().skipToNext();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaItemTransitionReason.get()).isEqualTo(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithoutCommandSeekToMediaItem_actionSkipToQueueItemNotAdvertised()
      throws Exception {
    Player player =
        createPlayerWithExcludedCommand(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItems(
                      ImmutableList.of(
                          MediaItem.fromUri("asset://media/wav/sample.wav"),
                          MediaItem.fromUri("asset://media/wav/sample_rf64.wav")));
                  createdPlayer.prepare();
                }),
            Player.COMMAND_SEEK_TO_MEDIA_ITEM);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM)
        .isEqualTo(0);

    AtomicBoolean receivedOnMediaItemTransition = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            receivedOnMediaItemTransition.set(true);
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            latch.countDown();
          }
        };
    player.addListener(listener);

    // skipToQueueItem() should be no-op.
    controllerCompat.getTransportControls().skipToQueueItem(1);
    controllerCompat.getTransportControls().setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedOnMediaItemTransition.get()).isFalse();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void
      playerWithCommandSeekToNext_withoutCommandSeeKToNextMediaItem_actionSkipToNextAdvertised()
          throws Exception {
    Player player =
        createPlayerWithCommands(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItems(
                      ImmutableList.of(
                          MediaItem.fromUri("asset://media/wav/sample.wav"),
                          MediaItem.fromUri("asset://media/wav/sample_rf64.wav")));
                  createdPlayer.prepare();
                }),
            /* availableCommands= */ new Player.Commands.Builder()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .build(),
            /* excludedCommands= */ new Player.Commands.Builder()
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .build());
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        .isNotEqualTo(0);

    AtomicInteger mediaItemTransitionReason = new AtomicInteger(-1);
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            mediaItemTransitionReason.set(reason);
            latch.countDown();
          }
        };
    player.addListener(listener);

    controllerCompat.getTransportControls().skipToNext();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaItemTransitionReason.get()).isEqualTo(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void
      playerWithCommandSeekToNextMediaItem_withoutCommandSeekToNext_actionSkipToNextAdvertised()
          throws Exception {
    Player player =
        createPlayerWithCommands(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItems(
                      ImmutableList.of(
                          MediaItem.fromUri("asset://media/wav/sample.wav"),
                          MediaItem.fromUri("asset://media/wav/sample_rf64.wav")));
                  createdPlayer.prepare();
                }),
            /* availableCommands= */ new Player.Commands.Builder()
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .build(),
            /* excludedCommands= */ new Player.Commands.Builder()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .build());
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        .isNotEqualTo(0);

    AtomicInteger mediaItemTransitionReason = new AtomicInteger(-1);
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            mediaItemTransitionReason.set(reason);
            latch.countDown();
          }
        };
    player.addListener(listener);

    controllerCompat.getTransportControls().skipToNext();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaItemTransitionReason.get()).isEqualTo(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void
      playerWithoutCommandSeekToNextAndCommandSeekToNextMediaItem_actionSkipToNextNotAdvertised()
          throws Exception {
    Player player =
        createPlayerWithCommands(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItems(
                      ImmutableList.of(
                          MediaItem.fromUri("asset://media/wav/sample.wav"),
                          MediaItem.fromUri("asset://media/wav/sample_rf64.wav")));
                  createdPlayer.prepare();
                }),
            /* availableCommands= */ Player.Commands.EMPTY,
            /* excludedCommands= */ new Player.Commands.Builder()
                .addAll(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT)
                .build());
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        .isEqualTo(0);

    AtomicBoolean receivedOnMediaItemTransition = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            receivedOnMediaItemTransition.set(true);
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            latch.countDown();
          }
        };
    player.addListener(listener);

    // skipToNext() should be no-op.
    controllerCompat.getTransportControls().skipToNext();
    controllerCompat.getTransportControls().setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedOnMediaItemTransition.get()).isFalse();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void
      playerWithCommandSeekToPrevious_withoutCommandSeekToPreviousMediaItem_actionSkipToPreviousAdvertised()
          throws Exception {
    Player player =
        createPlayerWithCommands(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItems(
                      ImmutableList.of(
                          MediaItem.fromUri("asset://media/wav/sample.wav"),
                          MediaItem.fromUri("asset://media/wav/sample_rf64.wav")),
                      /* startIndex= */ 1,
                      /* startPositionMs= */ C.TIME_UNSET);
                  createdPlayer.prepare();
                }),
            /* availableCommands= */ new Player.Commands.Builder()
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build(),
            /* excludedCommands= */ new Player.Commands.Builder()
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build());
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        .isNotEqualTo(0);

    AtomicInteger mediaItemTransitionReason = new AtomicInteger(-1);
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            mediaItemTransitionReason.set(reason);
            latch.countDown();
          }
        };
    player.addListener(listener);

    controllerCompat.getTransportControls().skipToPrevious();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaItemTransitionReason.get()).isEqualTo(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void
      playerWithCommandSeekToPreviousMediaItem_withoutCommandSeekToPrevious_actionSkipToPreviousAdvertised()
          throws Exception {
    Player player =
        createPlayerWithCommands(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItems(
                      ImmutableList.of(
                          MediaItem.fromUri("asset://media/wav/sample.wav"),
                          MediaItem.fromUri("asset://media/wav/sample_rf64.wav")),
                      /* startIndex= */ 1,
                      /* startPositionMs= */ C.TIME_UNSET);
                  createdPlayer.prepare();
                }),
            /* availableCommands= */ new Player.Commands.Builder()
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build(),
            /* excludedCommands= */ new Player.Commands.Builder()
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build());
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        .isNotEqualTo(0);

    AtomicInteger mediaItemTransitionReason = new AtomicInteger(-1);
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            mediaItemTransitionReason.set(reason);
            latch.countDown();
          }
        };
    player.addListener(listener);

    controllerCompat.getTransportControls().skipToPrevious();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(mediaItemTransitionReason.get()).isEqualTo(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void
      playerWithoutCommandSeekToPreviousAndCommandSeekToPreviousMediaItem_actionSkipToPreviousNotAdvertised()
          throws Exception {
    Player player =
        createPlayerWithCommands(
            createPlayer(
                /* onPostCreationTask= */ createdPlayer -> {
                  createdPlayer.setMediaItems(
                      ImmutableList.of(
                          MediaItem.fromUri("asset://media/wav/sample.wav"),
                          MediaItem.fromUri("asset://media/wav/sample_rf64.wav")),
                      /* startIndex= */ 1,
                      /* startPositionMs= */ C.TIME_UNSET);
                  createdPlayer.prepare();
                }),
            /* availableCommands= */ Player.Commands.EMPTY,
            /* excludedCommands= */ new Player.Commands.Builder()
                .addAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build());
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        .isEqualTo(0);

    AtomicBoolean receivedOnMediaItemTransition = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onMediaItemTransition(
              @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
            receivedOnMediaItemTransition.set(true);
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            latch.countDown();
          }
        };
    player.addListener(listener);

    // skipToPrevious() should be no-op.
    controllerCompat.getTransportControls().skipToPrevious();
    controllerCompat.getTransportControls().setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedOnMediaItemTransition.get()).isFalse();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithCommandSetMediaItem_actionsPlayFromXAndPrepareFromXAdvertised()
      throws Exception {
    Player player =
        createPlayerWithAvailableCommand(createDefaultPlayer(), Player.COMMAND_SET_MEDIA_ITEM);
    MediaSession mediaSession =
        createMediaSession(
            player,
            new MediaSession.Callback() {
              @Override
              public ListenableFuture<List<MediaItem>> onAddMediaItems(
                  MediaSession mediaSession,
                  MediaSession.ControllerInfo controller,
                  List<MediaItem> mediaItems) {
                return Futures.immediateFuture(
                    ImmutableList.of(MediaItem.fromUri("asset://media/wav/sample.wav")));
              }
            });
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    long actions =
        getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions();

    assertThat(actions & PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID).isNotEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH).isNotEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PLAY_FROM_URI).isNotEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID).isNotEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH).isNotEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PREPARE_FROM_URI).isNotEqualTo(0);

    ConditionVariable conditionVariable = new ConditionVariable();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            conditionVariable.open();
          }
        };
    player.addListener(listener);

    controllerCompat.getTransportControls().playFromMediaId(/* mediaId= */ "mediaId", Bundle.EMPTY);
    assertThat(conditionVariable.block(TIMEOUT_MS)).isTrue();

    conditionVariable.close();
    controllerCompat
        .getTransportControls()
        .playFromUri(Uri.parse("https://example.invalid"), Bundle.EMPTY);
    assertThat(conditionVariable.block(TIMEOUT_MS)).isTrue();

    conditionVariable.close();
    controllerCompat.getTransportControls().playFromSearch(/* query= */ "search", Bundle.EMPTY);
    assertThat(conditionVariable.block(TIMEOUT_MS)).isTrue();

    conditionVariable.close();
    controllerCompat
        .getTransportControls()
        .prepareFromMediaId(/* mediaId= */ "mediaId", Bundle.EMPTY);
    assertThat(conditionVariable.block(TIMEOUT_MS)).isTrue();

    conditionVariable.close();
    controllerCompat
        .getTransportControls()
        .prepareFromUri(Uri.parse("https://example.invalid"), Bundle.EMPTY);
    assertThat(conditionVariable.block(TIMEOUT_MS)).isTrue();

    conditionVariable.close();
    controllerCompat.getTransportControls().prepareFromSearch(/* query= */ "search", Bundle.EMPTY);
    assertThat(conditionVariable.block(TIMEOUT_MS)).isTrue();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithoutCommandSetMediaItem_actionsPlayFromXAndPrepareFromXNotAdvertised()
      throws Exception {
    Player player =
        createPlayerWithExcludedCommand(createDefaultPlayer(), Player.COMMAND_SET_MEDIA_ITEM);
    MediaSession mediaSession =
        createMediaSession(
            player,
            new MediaSession.Callback() {
              @Override
              public ListenableFuture<List<MediaItem>> onAddMediaItems(
                  MediaSession mediaSession,
                  MediaSession.ControllerInfo controller,
                  List<MediaItem> mediaItems) {
                return Futures.immediateFuture(
                    ImmutableList.of(MediaItem.fromUri("asset://media/wav/sample.wav")));
              }
            });
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    long actions =
        getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions();

    assertThat(actions & PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID).isEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH).isEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PLAY_FROM_URI).isEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID).isEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH).isEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_PREPARE_FROM_URI).isEqualTo(0);

    AtomicBoolean receivedOnTimelineChanged = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(Timeline timeline, int reason) {
            receivedOnTimelineChanged.set(true);
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            latch.countDown();
          }
        };
    player.addListener(listener);

    // prepareFrom and playFrom methods should be no-op.
    MediaControllerCompat.TransportControls transportControls =
        controllerCompat.getTransportControls();
    transportControls.prepareFromMediaId(/* mediaId= */ "mediaId", Bundle.EMPTY);
    transportControls.prepareFromSearch(/* query= */ "search", Bundle.EMPTY);
    transportControls.prepareFromUri(Uri.parse("https://example.invalid"), Bundle.EMPTY);
    transportControls.playFromMediaId(/* mediaId= */ "mediaId", Bundle.EMPTY);
    transportControls.playFromSearch(/* query= */ "search", Bundle.EMPTY);
    transportControls.playFromUri(Uri.parse("https://example.invalid"), Bundle.EMPTY);
    transportControls.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedOnTimelineChanged.get()).isFalse();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithCommandSetRepeatMode_actionSetRepeatModeAdvertised() throws Exception {
    Player player =
        createPlayerWithAvailableCommand(createDefaultPlayer(), Player.COMMAND_SET_REPEAT_MODE);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SET_REPEAT_MODE)
        .isNotEqualTo(0);

    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(int repeatMode) {
            latch.countDown();
          }
        };
    player.addListener(listener);

    controllerCompat.getTransportControls().setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithoutCommandSetRepeatMode_actionSetRepeatModeNotAdvertised()
      throws Exception {
    Player player =
        createPlayerWithExcludedCommand(createDefaultPlayer(), Player.COMMAND_SET_REPEAT_MODE);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SET_REPEAT_MODE)
        .isEqualTo(0);

    AtomicBoolean repeatModeChanged = new AtomicBoolean();
    CountDownLatch latch = new CountDownLatch(1);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(int repeatMode) {
            repeatModeChanged.set(true);
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            latch.countDown();
          }
        };
    player.addListener(listener);

    // setRepeatMode() should be no-op
    controllerCompat.getTransportControls().setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL);
    controllerCompat.getTransportControls().setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(repeatModeChanged.get()).isFalse();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithCommandSetSpeedAndPitch_actionSetPlaybackSpeedAdvertised()
      throws Exception {
    Player player =
        createPlayerWithAvailableCommand(createDefaultPlayer(), Player.COMMAND_SET_SPEED_AND_PITCH);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED)
        .isNotEqualTo(0);

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
    player.addListener(listener);

    controllerCompat.getTransportControls().setPlaybackSpeed(0.5f);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackParametersRef.get().speed).isEqualTo(0.5f);

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithoutCommandSetSpeedAndPitch_actionSetPlaybackSpeedNotAdvertised()
      throws Exception {
    Player player =
        createPlayerWithExcludedCommand(createDefaultPlayer(), Player.COMMAND_SET_SPEED_AND_PITCH);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(
            getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions()
                & PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED)
        .isEqualTo(0);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean receivedPlaybackParameters = new AtomicBoolean();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            receivedPlaybackParameters.set(true);
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            latch.countDown();
          }
        };
    player.addListener(listener);

    // setPlaybackSpeed() should be no-op.
    controllerCompat.getTransportControls().setPlaybackSpeed(0.5f);
    controllerCompat.getTransportControls().setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedPlaybackParameters.get()).isFalse();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithCommandSetShuffleMode_actionSetShuffleModeAdvertised() throws Exception {
    Player player =
        createPlayerWithAvailableCommand(createDefaultPlayer(), Player.COMMAND_SET_SHUFFLE_MODE);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    long actions =
        getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions();

    assertThat(actions & PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE).isNotEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE_ENABLED).isNotEqualTo(0);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean receivedShuffleModeEnabled = new AtomicBoolean();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            receivedShuffleModeEnabled.set(shuffleModeEnabled);
            latch.countDown();
          }
        };
    player.addListener(listener);

    controllerCompat.getTransportControls().setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedShuffleModeEnabled.get()).isTrue();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithoutCommandSetShuffleMode_actionSetShuffleModeNotAdvertised()
      throws Exception {
    Player player =
        createPlayerWithExcludedCommand(createDefaultPlayer(), Player.COMMAND_SET_SHUFFLE_MODE);
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    long actions =
        getFirstPlaybackState(controllerCompat, threadTestRule.getHandler()).getActions();

    assertThat(actions & PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE).isEqualTo(0);
    assertThat(actions & PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE_ENABLED).isEqualTo(0);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean receivedShuffleModeEnabled = new AtomicBoolean();
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            receivedShuffleModeEnabled.set(shuffleModeEnabled);
          }

          @Override
          public void onRepeatModeChanged(int repeatMode) {
            latch.countDown();
          }
        };
    player.addListener(listener);

    // setShuffleMode() should be no-op
    controllerCompat.getTransportControls().setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);
    controllerCompat.getTransportControls().setRepeatMode(PlaybackStateCompat.REPEAT_MODE_ALL);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedShuffleModeEnabled.get()).isFalse();

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithCommandChangeMediaItems_flagHandleQueueIsAdvertised() throws Exception {
    Player player =
        createPlayerWithAvailableCommand(createDefaultPlayer(), Player.COMMAND_CHANGE_MEDIA_ITEMS);
    MediaSession mediaSession =
        createMediaSession(
            player,
            new MediaSession.Callback() {
              @Override
              public ListenableFuture<List<MediaItem>> onAddMediaItems(
                  MediaSession mediaSession,
                  MediaSession.ControllerInfo controller,
                  List<MediaItem> mediaItems) {
                return Futures.immediateFuture(
                    ImmutableList.of(MediaItem.fromUri("asset://media/wav/sample.wav")));
              }
            });
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    // Wait until a playback state is sent to the controller.
    getFirstPlaybackState(controllerCompat, threadTestRule.getHandler());
    assertThat(controllerCompat.getFlags() & MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS)
        .isNotEqualTo(0);

    ArrayList<Timeline> receivedTimelines = new ArrayList<>();
    ArrayList<Integer> receivedTimelineReasons = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(2);
    Player.Listener listener =
        new Player.Listener() {
          @Override
          public void onTimelineChanged(
              Timeline timeline, @Player.TimelineChangeReason int reason) {
            receivedTimelines.add(timeline);
            receivedTimelineReasons.add(reason);
            latch.countDown();
          }
        };
    player.addListener(listener);

    controllerCompat.addQueueItem(
        new MediaDescriptionCompat.Builder().setMediaId("mediaId").build());
    controllerCompat.addQueueItem(
        new MediaDescriptionCompat.Builder().setMediaId("mediaId").build(), /* index= */ 0);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(receivedTimelines).hasSize(2);
    assertThat(receivedTimelines.get(0).getWindowCount()).isEqualTo(1);
    assertThat(receivedTimelines.get(1).getWindowCount()).isEqualTo(2);
    assertThat(receivedTimelineReasons)
        .containsExactly(
            Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED,
            Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithoutCommandChangeMediaItems_flagHandleQueueNotAdvertised() throws Exception {
    Player player =
        createPlayerWithExcludedCommand(createDefaultPlayer(), Player.COMMAND_CHANGE_MEDIA_ITEMS);
    MediaSession mediaSession =
        createMediaSession(
            player,
            new MediaSession.Callback() {
              @Override
              public ListenableFuture<List<MediaItem>> onAddMediaItems(
                  MediaSession mediaSession,
                  MediaSession.ControllerInfo controller,
                  List<MediaItem> mediaItems) {
                return Futures.immediateFuture(
                    ImmutableList.of(MediaItem.fromUri("asset://media/wav/sample.wav")));
              }
            });
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    // Wait until a playback state is sent to the controller.
    getFirstPlaybackState(controllerCompat, threadTestRule.getHandler());
    assertThat(controllerCompat.getFlags() & MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS)
        .isEqualTo(0);
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            controllerCompat.addQueueItem(
                new MediaDescriptionCompat.Builder().setMediaId("mediaId").build()));
    assertThrows(
        UnsupportedOperationException.class,
        () ->
            controllerCompat.addQueueItem(
                new MediaDescriptionCompat.Builder().setMediaId("mediaId").build(),
                /* index= */ 0));

    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerChangesAvailableCommands_actionsAreUpdated() throws Exception {
    // TODO(b/261158047): Add COMMAND_RELEASE to the available commands so that we can release the
    //  player.
    ControllingCommandsPlayer player =
        new ControllingCommandsPlayer(
            Player.Commands.EMPTY, threadTestRule.getHandler().getLooper());
    MediaSession mediaSession = createMediaSession(player);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);
    LinkedBlockingDeque<PlaybackStateCompat> receivedPlaybackStateCompats =
        new LinkedBlockingDeque<>();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            receivedPlaybackStateCompats.add(state);
          }
        };
    controllerCompat.registerCallback(callback, threadTestRule.getHandler());

    ArrayList<Player.Events> receivedEvents = new ArrayList<>();
    ConditionVariable eventsArrived = new ConditionVariable();
    player.addListener(
        new Player.Listener() {
          @Override
          public void onEvents(Player player, Player.Events events) {
            receivedEvents.add(events);
            eventsArrived.open();
          }
        });
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              player.setAvailableCommands(
                  new Player.Commands.Builder().add(Player.COMMAND_PREPARE).build());
            });

    assertThat(eventsArrived.block(TIMEOUT_MS)).isTrue();
    assertThat(getEventsAsList(receivedEvents.get(0)))
        .containsExactly(Player.EVENT_AVAILABLE_COMMANDS_CHANGED);
    assertThat(
            waitUntilPlaybackStateArrived(
                receivedPlaybackStateCompats,
                /* predicate= */ playbackStateCompat ->
                    (playbackStateCompat.getActions() & PlaybackStateCompat.ACTION_PREPARE) != 0))
        .isTrue();

    eventsArrived.open();
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              player.setAvailableCommands(Player.Commands.EMPTY);
            });

    assertThat(eventsArrived.block(TIMEOUT_MS)).isTrue();
    assertThat(
            waitUntilPlaybackStateArrived(
                receivedPlaybackStateCompats,
                /* predicate= */ playbackStateCompat ->
                    (playbackStateCompat.getActions() & PlaybackStateCompat.ACTION_PREPARE) == 0))
        .isTrue();
    assertThat(getEventsAsList(receivedEvents.get(1)))
        .containsExactly(Player.EVENT_AVAILABLE_COMMANDS_CHANGED);

    mediaSession.release();
    // This player is instantiated to use the threadTestRule, so it's released on that thread.
    threadTestRule.getHandler().postAndSync(player::release);
  }

  @Test
  public void
      playerWithCustomLayout_sessionBuiltWithCustomLayout_customActionsInInitialPlaybackState()
          throws Exception {
    Player player = createDefaultPlayer();
    Bundle extras1 = new Bundle();
    extras1.putString("key1", "value1");
    Bundle extras2 = new Bundle();
    extras1.putString("key2", "value2");
    SessionCommand command1 = new SessionCommand("command1", extras1);
    SessionCommand command2 = new SessionCommand("command2", extras2);
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder()
                .setDisplayName("button1")
                .setIconResId(R.drawable.media3_notification_play)
                .setSessionCommand(command1)
                .build(),
            new CommandButton.Builder()
                .setDisplayName("button2")
                .setIconResId(R.drawable.media3_notification_pause)
                .setSessionCommand(command2)
                .build());
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ConnectionResult onConnect(
              MediaSession session, MediaSession.ControllerInfo controller) {
            return new AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(command1).build())
                .build();
          }
        };
    MediaSession mediaSession = createMediaSession(player, callback, customLayout);
    connectMediaNotificationController(mediaSession);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);

    assertThat(LegacyConversions.convertToCustomLayout(controllerCompat.getPlaybackState()))
        .containsExactly(customLayout.get(0).copyWithIsEnabled(true));
    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void playerWithCustomLayout_setCustomLayout_playbackStateChangedWithCustomActionsChanged()
      throws Exception {
    Player player = createDefaultPlayer();
    Bundle extras1 = new Bundle();
    extras1.putString("key1", "value1");
    Bundle extras2 = new Bundle();
    extras1.putString("key2", "value2");
    SessionCommand command1 = new SessionCommand("command1", extras1);
    SessionCommand command2 = new SessionCommand("command2", extras2);
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder()
                .setDisplayName("button1")
                .setIconResId(R.drawable.media3_notification_play)
                .setSessionCommand(command1)
                .build(),
            new CommandButton.Builder()
                .setDisplayName("button2")
                .setIconResId(R.drawable.media3_notification_pause)
                .setSessionCommand(command2)
                .build());
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ConnectionResult onConnect(
              MediaSession session, MediaSession.ControllerInfo controller) {
            return new ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(command1).build())
                .build();
          }
        };
    MediaSession mediaSession = createMediaSession(player, callback);
    connectMediaNotificationController(mediaSession);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);
    ImmutableList<CommandButton> initialCustomLayout =
        LegacyConversions.convertToCustomLayout(controllerCompat.getPlaybackState());
    AtomicReference<List<CommandButton>> reportedCustomLayout = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    controllerCompat.registerCallback(
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            reportedCustomLayout.set(LegacyConversions.convertToCustomLayout(state));
            latch.countDown();
          }
        },
        threadTestRule.getHandler());

    getInstrumentation().runOnMainSync(() -> mediaSession.setCustomLayout(customLayout));

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(initialCustomLayout).isEmpty();
    assertThat(reportedCustomLayout.get())
        .containsExactly(customLayout.get(0).copyWithIsEnabled(true));
    mediaSession.release();
    releasePlayer(player);
  }

  @Test
  public void
      playerWithCustomLayout_setCustomLayoutForMediaNotificationController_playbackStateChangedWithCustomActionsChanged()
          throws Exception {
    Player player = createDefaultPlayer();
    Bundle extras1 = new Bundle();
    extras1.putString("key1", "value1");
    Bundle extras2 = new Bundle();
    extras1.putString("key2", "value2");
    SessionCommand command1 = new SessionCommand("command1", extras1);
    SessionCommand command2 = new SessionCommand("command2", extras2);
    ImmutableList<CommandButton> customLayout =
        ImmutableList.of(
            new CommandButton.Builder()
                .setDisplayName("button1")
                .setIconResId(R.drawable.media3_notification_play)
                .setSessionCommand(command1)
                .build(),
            new CommandButton.Builder()
                .setDisplayName("button2")
                .setIconResId(R.drawable.media3_notification_pause)
                .setSessionCommand(command2)
                .build());
    MediaSession.Callback callback =
        new MediaSession.Callback() {
          @Override
          public ConnectionResult onConnect(
              MediaSession session, MediaSession.ControllerInfo controller) {
            return new ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(command1).build())
                .build();
          }
        };
    MediaSession mediaSession = createMediaSession(player, callback);
    connectMediaNotificationController(mediaSession);
    MediaControllerCompat controllerCompat = createMediaControllerCompat(mediaSession);
    ImmutableList<CommandButton> initialCustomLayout =
        LegacyConversions.convertToCustomLayout(controllerCompat.getPlaybackState());
    AtomicReference<List<CommandButton>> reportedCustomLayout = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    controllerCompat.registerCallback(
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            reportedCustomLayout.set(LegacyConversions.convertToCustomLayout(state));
            latch.countDown();
          }
        },
        threadTestRule.getHandler());

    getInstrumentation()
        .runOnMainSync(
            () ->
                mediaSession.setCustomLayout(
                    mediaSession.getMediaNotificationControllerInfo(), customLayout));

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(initialCustomLayout).isEmpty();
    assertThat(reportedCustomLayout.get())
        .containsExactly(customLayout.get(0).copyWithIsEnabled(true));
    mediaSession.release();
    releasePlayer(player);
  }

  /**
   * Connect a controller that mimics the media notification controller that is connected by {@link
   * MediaNotificationManager} when the session is running in the service.
   */
  private void connectMediaNotificationController(MediaSession mediaSession)
      throws InterruptedException {
    CountDownLatch connectionLatch = new CountDownLatch(1);
    Bundle connectionHints = new Bundle();
    connectionHints.putBoolean(MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true);
    ListenableFuture<MediaController> mediaNotificationControllerFuture =
        new MediaController.Builder(
                ApplicationProvider.getApplicationContext(), mediaSession.getToken())
            .setConnectionHints(connectionHints)
            .buildAsync();
    mediaNotificationControllerFuture.addListener(
        connectionLatch::countDown, MoreExecutors.directExecutor());
    assertThat(connectionLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  private PlaybackStateCompat getFirstPlaybackState(
      MediaControllerCompat mediaControllerCompat, Handler handler) throws InterruptedException {
    LinkedBlockingDeque<PlaybackStateCompat> playbackStateCompats = new LinkedBlockingDeque<>();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateCompats.add(state);
          }
        };
    mediaControllerCompat.registerCallback(callback, handler);
    PlaybackStateCompat playbackStateCompat = playbackStateCompats.take();
    mediaControllerCompat.unregisterCallback(callback);
    return playbackStateCompat;
  }

  /**
   * Creates a default {@link ExoPlayer} instance on the main thread. Use {@link
   * #releasePlayer(Player)} to release the returned instance on the main thread.
   */
  private static Player createDefaultPlayer() {
    return createPlayer(/* onPostCreationTask= */ player -> {});
  }

  /**
   * Creates a player on the main thread. After the player is created, {@code onPostCreationTask} is
   * called from the main thread to set any initial state on the player.
   */
  private static Player createPlayer(Consumer<Player> onPostCreationTask) {
    AtomicReference<Player> playerRef = new AtomicReference<>();
    getInstrumentation()
        .runOnMainSync(
            () -> {
              ExoPlayer exoPlayer =
                  new ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build();
              onPostCreationTask.accept(exoPlayer);
              playerRef.set(exoPlayer);
            });
    return playerRef.get();
  }

  private static MediaSession createMediaSession(Player player) {
    return createMediaSession(player, /* callback= */ null);
  }

  private static MediaSession createMediaSession(
      Player player, @Nullable MediaSession.Callback callback) {
    return createMediaSession(player, callback, /* customLayout= */ ImmutableList.of());
  }

  private static MediaSession createMediaSession(
      Player player, @Nullable MediaSession.Callback callback, List<CommandButton> customLayout) {
    MediaSession.Builder session =
        new MediaSession.Builder(ApplicationProvider.getApplicationContext(), player)
            .setCustomLayout(customLayout);
    if (callback != null) {
      session.setCallback(callback);
    }
    return session.build();
  }

  private static MediaControllerCompat createMediaControllerCompat(MediaSession mediaSession) {
    return new MediaControllerCompat(
        ApplicationProvider.getApplicationContext(),
        mediaSession.getSessionCompat().getSessionToken());
  }

  /** Releases the {@code player} on the main thread. */
  private static void releasePlayer(Player player) {
    getInstrumentation().runOnMainSync(player::release);
  }

  /**
   * Returns an {@link Player} where {@code availableCommand} is always included in the {@linkplain
   * Player#getAvailableCommands() available commands}.
   */
  private static Player createPlayerWithAvailableCommand(
      Player player, @Player.Command int availableCommand) {
    return createPlayerWithCommands(
        player, new Player.Commands.Builder().add(availableCommand).build(), Player.Commands.EMPTY);
  }

  /**
   * Returns a {@link Player} where {@code excludedCommand} is always excluded from the {@linkplain
   * Player#getAvailableCommands() available commands}.
   */
  private static Player createPlayerWithExcludedCommand(
      Player player, @Player.Command int excludedCommand) {
    return createPlayerWithCommands(
        player, Player.Commands.EMPTY, new Player.Commands.Builder().add(excludedCommand).build());
  }

  private static boolean waitUntilPlaybackStateArrived(
      LinkedBlockingDeque<PlaybackStateCompat> playbackStateCompats,
      Predicate<PlaybackStateCompat> predicate)
      throws InterruptedException {
    while (true) {
      @Nullable
      PlaybackStateCompat playbackStateCompat = playbackStateCompats.poll(TIMEOUT_MS, MILLISECONDS);
      if (playbackStateCompat == null) {
        return false;
      } else if (predicate.test(playbackStateCompat)) {
        return true;
      }
    }
  }

  /**
   * Returns an {@link Player} where {@code availableCommands} are always included and {@code
   * excludedCommands} are always excluded from the {@linkplain Player#getAvailableCommands()
   * available commands}.
   */
  private static Player createPlayerWithCommands(
      Player player, Player.Commands availableCommands, Player.Commands excludedCommands) {
    return new ForwardingPlayer(player) {
      @Override
      public Commands getAvailableCommands() {
        Commands.Builder commands =
            super.getAvailableCommands().buildUpon().addAll(availableCommands);
        for (int i = 0; i < excludedCommands.size(); i++) {
          commands.remove(excludedCommands.get(i));
        }
        return commands.build();
      }

      @Override
      public boolean isCommandAvailable(int command) {
        return getAvailableCommands().contains(command);
      }
    };
  }

  private static class ControllingCommandsPlayer extends SimpleBasePlayer {

    private Commands availableCommands;

    public ControllingCommandsPlayer(Commands availableCommands, Looper applicationLooper) {
      super(applicationLooper);
      this.availableCommands = availableCommands;
    }

    public void setAvailableCommands(Commands availableCommands) {
      this.availableCommands = availableCommands;
      invalidateState();
    }

    @Override
    protected State getState() {
      return new State.Builder().setAvailableCommands(availableCommands).build();
    }

    @Override
    protected ListenableFuture<?> handleRelease() {
      return Futures.immediateVoidFuture();
    }
  }
}
