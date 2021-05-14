/*
 * Copyright 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.media2;

import static androidx.media2.common.SessionPlayer.PLAYER_STATE_IDLE;
import static androidx.media2.common.SessionPlayer.PLAYER_STATE_PAUSED;
import static androidx.media2.common.SessionPlayer.PLAYER_STATE_PLAYING;
import static androidx.media2.common.SessionPlayer.PlayerResult.RESULT_INFO_SKIPPED;
import static androidx.media2.common.SessionPlayer.PlayerResult.RESULT_SUCCESS;
import static com.google.android.exoplayer2.ext.media2.TestUtils.assertPlayerResult;
import static com.google.android.exoplayer2.ext.media2.TestUtils.assertPlayerResultSuccess;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.media.AudioManager;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.SessionPlayer;
import androidx.media2.common.SessionPlayer.PlayerResult;
import androidx.media2.common.UriMediaItem;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.media2.test.R;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;
import com.google.android.exoplayer2.util.Util;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests {@link SessionPlayerConnector}. */
@SuppressWarnings("FutureReturnValueIgnored")
@RunWith(AndroidJUnit4.class)
public class SessionPlayerConnectorTest {

  @Rule public final PlayerTestRule playerTestRule = new PlayerTestRule();

  private static final long PLAYLIST_CHANGE_WAIT_TIME_MS = 1_000;
  private static final long PLAYER_STATE_CHANGE_WAIT_TIME_MS = 5_000;
  private static final long PLAYBACK_COMPLETED_WAIT_TIME_MS = 20_000;
  private static final float FLOAT_TOLERANCE = .0001f;

  private Context context;
  private Executor executor;
  private SessionPlayerConnector sessionPlayerConnector;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    executor = playerTestRule.getExecutor();
    sessionPlayerConnector = playerTestRule.getSessionPlayerConnector();
  }

  @Test
  @LargeTest
  public void play_onceWithAudioResource_changesPlayerStateToPlaying() throws Exception {
    TestUtils.loadResource(R.raw.audio, sessionPlayerConnector);

    AudioAttributesCompat attributes =
        new AudioAttributesCompat.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build();
    sessionPlayerConnector.setAudioAttributes(attributes);

    CountDownLatch onPlayingLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayerStateChanged(SessionPlayer player, int playerState) {
            if (playerState == PLAYER_STATE_PLAYING) {
              onPlayingLatch.countDown();
            }
          }
        });

    sessionPlayerConnector.prepare();
    sessionPlayerConnector.play();
    assertThat(onPlayingLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();
  }

  @Test
  @MediumTest
  public void play_onceWithAudioResourceOnMainThread_notifiesOnPlayerStateChanged()
      throws Exception {
    CountDownLatch onPlayerStatePlayingLatch = new CountDownLatch(1);

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              try {
                TestUtils.loadResource(R.raw.audio, sessionPlayerConnector);
              } catch (Exception e) {
                assertWithMessage(e.getMessage()).fail();
              }
              AudioAttributesCompat attributes =
                  new AudioAttributesCompat.Builder()
                      .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                      .build();
              sessionPlayerConnector.setAudioAttributes(attributes);

              sessionPlayerConnector.registerPlayerCallback(
                  executor,
                  new SessionPlayer.PlayerCallback() {
                    @Override
                    public void onPlayerStateChanged(SessionPlayer player, int playerState) {
                      if (playerState == PLAYER_STATE_PLAYING) {
                        onPlayerStatePlayingLatch.countDown();
                      }
                    }
                  });
              sessionPlayerConnector.prepare();
              sessionPlayerConnector.play();
            });
    assertThat(onPlayerStatePlayingLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS))
        .isTrue();
  }

  @Test
  @LargeTest
  public void play_withCustomControlDispatcher_isSkipped() throws Exception {
    if (Looper.myLooper() == null) {
      Looper.prepare();
    }

    ControlDispatcher controlDispatcher =
        new DefaultControlDispatcher() {
          @Override
          public boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady) {
            return false;
          }
        };
    SimpleExoPlayer simpleExoPlayer = null;
    SessionPlayerConnector playerConnector = null;
    try {
      simpleExoPlayer =
          new SimpleExoPlayer.Builder(context)
              .setLooper(Looper.myLooper())
              .build();
      playerConnector =
          new SessionPlayerConnector(simpleExoPlayer, new DefaultMediaItemConverter());
      playerConnector.setControlDispatcher(controlDispatcher);
      assertPlayerResult(playerConnector.play(), RESULT_INFO_SKIPPED);
    } finally {
      if (playerConnector != null) {
        playerConnector.close();
      }
      if (simpleExoPlayer != null) {
        simpleExoPlayer.release();
      }
    }
  }

  @Test
  @LargeTest
  public void setMediaItem_withAudioResource_notifiesOnPlaybackCompleted() throws Exception {
    TestUtils.loadResource(R.raw.audio, sessionPlayerConnector);

    CountDownLatch onPlaybackCompletedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackCompleted(SessionPlayer player) {
            onPlaybackCompletedLatch.countDown();
          }
        });
    sessionPlayerConnector.prepare();
    sessionPlayerConnector.play();

    // waiting to complete
    assertThat(onPlaybackCompletedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, MILLISECONDS))
        .isTrue();
    assertThat(sessionPlayerConnector.getPlayerState())
        .isEqualTo(SessionPlayer.PLAYER_STATE_PAUSED);
  }

  @Test
  @LargeTest
  public void setMediaItem_withVideoResource_notifiesOnPlaybackCompleted() throws Exception {
    TestUtils.loadResource(R.raw.video_desks, sessionPlayerConnector);
    CountDownLatch onPlaybackCompletedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackCompleted(SessionPlayer player) {
            onPlaybackCompletedLatch.countDown();
          }
        });
    sessionPlayerConnector.prepare();
    sessionPlayerConnector.play();

    // waiting to complete
    assertThat(onPlaybackCompletedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, MILLISECONDS))
        .isTrue();
    assertThat(sessionPlayerConnector.getPlayerState())
        .isEqualTo(SessionPlayer.PLAYER_STATE_PAUSED);
  }

  @Test
  @SmallTest
  public void getDuration_whenIdleState_returnsUnknownTime() {
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(SessionPlayer.PLAYER_STATE_IDLE);
    assertThat(sessionPlayerConnector.getDuration()).isEqualTo(SessionPlayer.UNKNOWN_TIME);
  }

  @Test
  @MediumTest
  public void getDuration_afterPrepared_returnsDuration() throws Exception {
    TestUtils.loadResource(R.raw.video_desks, sessionPlayerConnector);

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    assertThat(sessionPlayerConnector.getPlayerState())
        .isEqualTo(SessionPlayer.PLAYER_STATE_PAUSED);
    assertThat((float) sessionPlayerConnector.getDuration()).isWithin(50).of(5130);
  }

  @Test
  @SmallTest
  public void getCurrentPosition_whenIdleState_returnsDefaultPosition() {
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(SessionPlayer.PLAYER_STATE_IDLE);
    assertThat(sessionPlayerConnector.getCurrentPosition()).isEqualTo(0);
  }

  @Test
  @SmallTest
  public void getBufferedPosition_whenIdleState_returnsDefaultPosition() {
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(SessionPlayer.PLAYER_STATE_IDLE);
    assertThat(sessionPlayerConnector.getBufferedPosition()).isEqualTo(0);
  }

  @Test
  @SmallTest
  public void getPlaybackSpeed_whenIdleState_throwsNoException() {
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(SessionPlayer.PLAYER_STATE_IDLE);
    try {
      sessionPlayerConnector.getPlaybackSpeed();
    } catch (Exception e) {
      assertWithMessage(e.getMessage()).fail();
    }
  }

  @Test
  @LargeTest
  public void play_withDataSourceCallback_changesPlayerState() throws Exception {
    sessionPlayerConnector.setMediaItem(TestUtils.createMediaItem(R.raw.video_big_buck_bunny));
    sessionPlayerConnector.prepare();
    assertPlayerResultSuccess(sessionPlayerConnector.play());
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(PLAYER_STATE_PLAYING);

    // Test pause and restart.
    assertPlayerResultSuccess(sessionPlayerConnector.pause());
    assertThat(sessionPlayerConnector.getPlayerState()).isNotEqualTo(PLAYER_STATE_PLAYING);

    assertPlayerResultSuccess(sessionPlayerConnector.play());
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(PLAYER_STATE_PLAYING);
  }

  @Test
  @SmallTest
  public void setMediaItem_withNullMediaItem_throwsException() {
    try {
      sessionPlayerConnector.setMediaItem(null);
      assertWithMessage("Null media item should be rejected").fail();
    } catch (NullPointerException e) {
      // Expected exception
    }
  }

  @Test
  @LargeTest
  public void setPlaybackSpeed_afterPlayback_remainsSame() throws Exception {
    int resId1 = R.raw.video_big_buck_bunny;
    MediaItem mediaItem1 =
        new UriMediaItem.Builder(RawResourceDataSource.buildRawResourceUri(resId1))
            .setStartPosition(6_000)
            .setEndPosition(7_000)
            .build();

    MediaItem mediaItem2 =
        new UriMediaItem.Builder(RawResourceDataSource.buildRawResourceUri(resId1))
            .setStartPosition(3_000)
            .setEndPosition(4_000)
            .build();

    List<MediaItem> items = new ArrayList<>();
    items.add(mediaItem1);
    items.add(mediaItem2);
    sessionPlayerConnector.setPlaylist(items, null);

    CountDownLatch onPlaybackCompletedLatch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackCompleted(SessionPlayer player) {
            onPlaybackCompletedLatch.countDown();
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    sessionPlayerConnector.prepare().get();

    sessionPlayerConnector.setPlaybackSpeed(2.0f);
    sessionPlayerConnector.play();

    assertThat(onPlaybackCompletedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, MILLISECONDS))
        .isTrue();
    assertThat(sessionPlayerConnector.getCurrentMediaItem()).isEqualTo(mediaItem2);
    assertThat(sessionPlayerConnector.getPlaybackSpeed()).isWithin(0.001f).of(2.0f);
  }

  @Test
  @LargeTest
  public void seekTo_withSeriesOfSeek_succeeds() throws Exception {
    TestUtils.loadResource(R.raw.video_big_buck_bunny, sessionPlayerConnector);

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    List<Long> testSeekPositions = Arrays.asList(3000L, 2000L, 1000L);
    for (long testSeekPosition : testSeekPositions) {
      assertPlayerResultSuccess(sessionPlayerConnector.seekTo(testSeekPosition));
      assertThat(sessionPlayerConnector.getCurrentPosition()).isEqualTo(testSeekPosition);
    }
  }

  @Test
  @LargeTest
  public void seekTo_skipsUnnecessarySeek() throws Exception {
    CountDownLatch readAllowedLatch = new CountDownLatch(1);
    playerTestRule.setDataSourceInstrumentation(
        dataSpec -> {
          try {
            assertThat(readAllowedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, MILLISECONDS))
                .isTrue();
          } catch (Exception e) {
            assertWithMessage("Unexpected exception %s", e).fail();
          }
        });

    sessionPlayerConnector.setMediaItem(TestUtils.createMediaItem(R.raw.video_big_buck_bunny));

    // prepare() will be pending until readAllowed is countDowned.
    sessionPlayerConnector.prepare();

    CopyOnWriteArrayList<Long> positionChanges = new CopyOnWriteArrayList<>();
    long testIntermediateSeekToPosition1 = 3000;
    long testIntermediateSeekToPosition2 = 2000;
    long testFinalSeekToPosition = 1000;
    CountDownLatch onSeekCompletedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onSeekCompleted(SessionPlayer player, long position) {
            // Do not assert here, because onSeekCompleted() can be called after the player is
            // closed.
            positionChanges.add(position);
            if (position == testFinalSeekToPosition) {
              onSeekCompletedLatch.countDown();
            }
          }
        });

    ListenableFuture<PlayerResult> seekFuture1 =
        sessionPlayerConnector.seekTo(testIntermediateSeekToPosition1);
    ListenableFuture<PlayerResult> seekFuture2 =
        sessionPlayerConnector.seekTo(testIntermediateSeekToPosition2);
    ListenableFuture<PlayerResult> seekFuture3 =
        sessionPlayerConnector.seekTo(testFinalSeekToPosition);

    readAllowedLatch.countDown();

    assertThat(seekFuture1.get().getResultCode()).isEqualTo(RESULT_INFO_SKIPPED);
    assertThat(seekFuture2.get().getResultCode()).isEqualTo(RESULT_INFO_SKIPPED);
    assertThat(seekFuture3.get().getResultCode()).isEqualTo(RESULT_SUCCESS);
    assertThat(onSeekCompletedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, MILLISECONDS)).isTrue();
    assertThat(positionChanges)
        .containsNoneOf(testIntermediateSeekToPosition1, testIntermediateSeekToPosition2);
    assertThat(positionChanges).contains(testFinalSeekToPosition);
  }

  @Test
  @LargeTest
  public void seekTo_whenUnderlyingPlayerAlsoSeeks_throwsNoException() throws Exception {
    TestUtils.loadResource(R.raw.video_big_buck_bunny, sessionPlayerConnector);
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    SimpleExoPlayer simpleExoPlayer = playerTestRule.getSimpleExoPlayer();

    List<ListenableFuture<PlayerResult>> futures = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      futures.add(sessionPlayerConnector.seekTo(4123));
      InstrumentationRegistry.getInstrumentation()
          .runOnMainSync(() -> simpleExoPlayer.seekTo(1243));
    }

    for (ListenableFuture<PlayerResult> future : futures) {
      assertThat(future.get().getResultCode())
          .isAnyOf(PlayerResult.RESULT_INFO_SKIPPED, PlayerResult.RESULT_SUCCESS);
    }
  }

  @Test
  @LargeTest
  public void seekTo_byUnderlyingPlayer_notifiesOnSeekCompleted() throws Exception {
    TestUtils.loadResource(R.raw.video_big_buck_bunny, sessionPlayerConnector);
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    SimpleExoPlayer simpleExoPlayer = playerTestRule.getSimpleExoPlayer();
    long testSeekPosition = 1023;
    AtomicLong seekPosition = new AtomicLong();
    CountDownLatch onSeekCompletedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onSeekCompleted(SessionPlayer player, long position) {
            // Do not assert here, because onSeekCompleted() can be called after the player is
            // closed.
            seekPosition.set(position);
            onSeekCompletedLatch.countDown();
          }
        });

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> simpleExoPlayer.seekTo(testSeekPosition));
    assertThat(onSeekCompletedLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();
    assertThat(seekPosition.get()).isEqualTo(testSeekPosition);
  }

  @Test
  @LargeTest
  public void getPlayerState_withCallingPrepareAndPlayAndPause_reflectsPlayerState()
      throws Throwable {
    TestUtils.loadResource(R.raw.video_desks, sessionPlayerConnector);
    assertThat(sessionPlayerConnector.getBufferingState())
        .isEqualTo(SessionPlayer.BUFFERING_STATE_UNKNOWN);
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(SessionPlayer.PLAYER_STATE_IDLE);

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    assertThat(sessionPlayerConnector.getBufferingState())
        .isAnyOf(
            SessionPlayer.BUFFERING_STATE_BUFFERING_AND_PLAYABLE,
            SessionPlayer.BUFFERING_STATE_COMPLETE);
    assertThat(sessionPlayerConnector.getPlayerState())
        .isEqualTo(SessionPlayer.PLAYER_STATE_PAUSED);

    assertPlayerResultSuccess(sessionPlayerConnector.play());

    assertThat(sessionPlayerConnector.getBufferingState())
        .isAnyOf(
            SessionPlayer.BUFFERING_STATE_BUFFERING_AND_PLAYABLE,
            SessionPlayer.BUFFERING_STATE_COMPLETE);
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(PLAYER_STATE_PLAYING);

    assertPlayerResultSuccess(sessionPlayerConnector.pause());

    assertThat(sessionPlayerConnector.getBufferingState())
        .isAnyOf(
            SessionPlayer.BUFFERING_STATE_BUFFERING_AND_PLAYABLE,
            SessionPlayer.BUFFERING_STATE_COMPLETE);
    assertThat(sessionPlayerConnector.getPlayerState())
        .isEqualTo(SessionPlayer.PLAYER_STATE_PAUSED);
  }

  @Test
  @LargeTest
  public void prepare_twice_finishes() throws Exception {
    TestUtils.loadResource(R.raw.audio, sessionPlayerConnector);
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    assertPlayerResult(sessionPlayerConnector.prepare(), RESULT_INFO_SKIPPED);
  }

  @Test
  @LargeTest
  public void prepare_notifiesOnPlayerStateChanged() throws Throwable {
    TestUtils.loadResource(R.raw.video_big_buck_bunny, sessionPlayerConnector);

    CountDownLatch onPlayerStatePaused = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayerStateChanged(SessionPlayer player, int state) {
            if (state == SessionPlayer.PLAYER_STATE_PAUSED) {
              onPlayerStatePaused.countDown();
            }
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    assertThat(onPlayerStatePaused.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();
  }

  @Test
  @LargeTest
  public void prepare_notifiesBufferingCompletedOnce() throws Throwable {
    TestUtils.loadResource(R.raw.video_big_buck_bunny, sessionPlayerConnector);

    CountDownLatch onBufferingCompletedLatch = new CountDownLatch(2);
    CopyOnWriteArrayList<Integer> bufferingStateChanges = new CopyOnWriteArrayList<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onBufferingStateChanged(
              SessionPlayer player, @Nullable MediaItem item, int buffState) {
            bufferingStateChanges.add(buffState);
            if (buffState == SessionPlayer.BUFFERING_STATE_COMPLETE) {
              onBufferingCompletedLatch.countDown();
            }
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    assertWithMessage(
            "Expected BUFFERING_STATE_COMPLETE only once. Full changes are %s",
            bufferingStateChanges)
        .that(onBufferingCompletedLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS))
        .isFalse();
    assertThat(bufferingStateChanges).isNotEmpty();
    int lastIndex = bufferingStateChanges.size() - 1;
    assertWithMessage(
            "Didn't end with BUFFERING_STATE_COMPLETE. Full changes are %s", bufferingStateChanges)
        .that(bufferingStateChanges.get(lastIndex))
        .isEqualTo(SessionPlayer.BUFFERING_STATE_COMPLETE);
  }

  @Test
  @LargeTest
  public void seekTo_whenPrepared_notifiesOnSeekCompleted() throws Throwable {
    long mp4DurationMs = 8_484L;
    TestUtils.loadResource(R.raw.video_big_buck_bunny, sessionPlayerConnector);

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    CountDownLatch onSeekCompletedLatch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onSeekCompleted(SessionPlayer player, long position) {
            onSeekCompletedLatch.countDown();
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    sessionPlayerConnector.seekTo(mp4DurationMs >> 1);

    assertThat(onSeekCompletedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, MILLISECONDS)).isTrue();
  }

  @Test
  @LargeTest
  public void setPlaybackSpeed_whenPrepared_notifiesOnPlaybackSpeedChanged() throws Throwable {
    TestUtils.loadResource(R.raw.video_big_buck_bunny, sessionPlayerConnector);

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    CountDownLatch onPlaybackSpeedChangedLatch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackSpeedChanged(SessionPlayer player, float speed) {
            assertThat(speed).isWithin(FLOAT_TOLERANCE).of(0.5f);
            onPlaybackSpeedChangedLatch.countDown();
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    sessionPlayerConnector.setPlaybackSpeed(0.5f);

    assertThat(onPlaybackSpeedChangedLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS))
        .isTrue();
  }

  @Test
  @SmallTest
  public void setPlaybackSpeed_withZeroSpeed_throwsException() {
    try {
      sessionPlayerConnector.setPlaybackSpeed(0.0f);
      assertWithMessage("zero playback speed shouldn't be allowed").fail();
    } catch (IllegalArgumentException e) {
      // expected. pass-through.
    }
  }

  @Test
  @SmallTest
  public void setPlaybackSpeed_withNegativeSpeed_throwsException() {
    try {
      sessionPlayerConnector.setPlaybackSpeed(-1.0f);
      assertWithMessage("negative playback speed isn't supported").fail();
    } catch (IllegalArgumentException e) {
      // expected. pass-through.
    }
  }

  @Test
  @LargeTest
  public void close_throwsNoExceptionAndDoesNotCrash() throws Exception {
    TestUtils.loadResource(R.raw.audio, sessionPlayerConnector);
    AudioAttributesCompat attributes =
        new AudioAttributesCompat.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build();
    sessionPlayerConnector.setAudioAttributes(attributes);
    sessionPlayerConnector.prepare();
    sessionPlayerConnector.play();
    sessionPlayerConnector.close();

    // Set the player to null so we don't try to close it again in tearDown().
    sessionPlayerConnector = null;

    // Tests whether the notification from the player after the close() doesn't crash.
    Thread.sleep(PLAYER_STATE_CHANGE_WAIT_TIME_MS);
  }

  @Test
  @LargeTest
  public void cancelReturnedFuture_withSeekTo_cancelsPendingCommand() throws Exception {
    CountDownLatch readRequestedLatch = new CountDownLatch(1);
    CountDownLatch readAllowedLatch = new CountDownLatch(1);
    // Need to wait from prepare() to counting down readAllowedLatch.
    playerTestRule.setDataSourceInstrumentation(
        dataSpec -> {
          readRequestedLatch.countDown();
          try {
            assertThat(readAllowedLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS))
                .isTrue();
          } catch (Exception e) {
            assertWithMessage("Unexpected exception %s", e).fail();
          }
        });
    assertPlayerResultSuccess(
        sessionPlayerConnector.setMediaItem(TestUtils.createMediaItem(R.raw.audio)));

    // prepare() will be pending until readAllowed is countDowned.
    ListenableFuture<PlayerResult> prepareFuture = sessionPlayerConnector.prepare();
    ListenableFuture<PlayerResult> seekFuture = sessionPlayerConnector.seekTo(1000);

    assertThat(readRequestedLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();

    // Cancel the pending commands while preparation is on hold.
    seekFuture.cancel(false);

    // Make the on-going prepare operation resumed and finished.
    readAllowedLatch.countDown();
    assertPlayerResultSuccess(prepareFuture);

    // Check whether the canceled seek() didn't happened.
    // Checking seekFuture.get() will be useless because it always throws CancellationException due
    // to the CallbackToFuture implementation.
    Thread.sleep(PLAYER_STATE_CHANGE_WAIT_TIME_MS);
    assertThat(sessionPlayerConnector.getCurrentPosition()).isEqualTo(0);
  }

  @Test
  @SmallTest
  public void setPlaylist_withNullPlaylist_throwsException() throws Exception {
    try {
      sessionPlayerConnector.setPlaylist(null, null);
      assertWithMessage("null playlist shouldn't be allowed").fail();
    } catch (Exception e) {
      // pass-through
    }
  }

  @Test
  @SmallTest
  public void setPlaylist_withPlaylistContainingNullItem_throwsException() {
    try {
      List<MediaItem> list = new ArrayList<>();
      list.add(null);
      sessionPlayerConnector.setPlaylist(list, null);
      assertWithMessage("playlist with null item shouldn't be allowed").fail();
    } catch (Exception e) {
      // pass-through
    }
  }

  @Test
  @LargeTest
  public void setPlaylist_setsPlaylistAndCurrentMediaItem() throws Exception {
    List<MediaItem> playlist = TestUtils.createPlaylist(10);
    PlayerCallbackForPlaylist callback = new PlayerCallbackForPlaylist(playlist, 1);
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(playlist, null));
    assertThat(callback.await(PLAYLIST_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();

    assertThat(sessionPlayerConnector.getPlaylist()).isEqualTo(playlist);
    assertThat(sessionPlayerConnector.getCurrentMediaItem()).isEqualTo(playlist.get(0));
  }

  @Test
  @LargeTest
  public void setPlaylistAndRemoveAllPlaylistItem_playerStateBecomesIdle() throws Exception {
    List<MediaItem> playlist = new ArrayList<>();
    playlist.add(TestUtils.createMediaItem(R.raw.video_1));
    PlayerCallbackForPlaylist callback =
        new PlayerCallbackForPlaylist(playlist, 2) {
          @Override
          public void onPlayerStateChanged(SessionPlayer player, int playerState) {
            countDown();
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(playlist, null));
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    assertThat(callback.await(PLAYLIST_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(PLAYER_STATE_PAUSED);

    callback.resetLatch(1);
    assertPlayerResultSuccess(sessionPlayerConnector.removePlaylistItem(0));
    assertThat(callback.await(PLAYLIST_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(PLAYER_STATE_IDLE);
  }

  @Test
  @LargeTest
  public void setPlaylist_calledOnlyOnce_notifiesPlaylistChangeOnlyOnce() throws Exception {
    List<MediaItem> playlist = TestUtils.createPlaylist(10);
    CountDownLatch onPlaylistChangedLatch = new CountDownLatch(2);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistChanged(
              SessionPlayer player,
              @Nullable List<MediaItem> list,
              @Nullable MediaMetadata metadata) {
            assertThat(list).isEqualTo(playlist);
            onPlaylistChangedLatch.countDown();
          }
        });

    sessionPlayerConnector.setPlaylist(playlist, /* metadata= */ null);
    sessionPlayerConnector.prepare();
    assertThat(onPlaylistChangedLatch.await(PLAYLIST_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isFalse();
    assertThat(onPlaylistChangedLatch.getCount()).isEqualTo(1);
  }

  @Test
  @LargeTest
  public void setPlaylist_byUnderlyingPlayerBeforePrepare_notifiesOnPlaylistChanged()
      throws Exception {
    List<MediaItem> playlistToExoPlayer = TestUtils.createPlaylist(4);
    DefaultMediaItemConverter converter = new DefaultMediaItemConverter();
    List<com.google.android.exoplayer2.MediaItem> exoMediaItems = new ArrayList<>();
    for (MediaItem mediaItem : playlistToExoPlayer) {
      exoMediaItems.add(converter.convertToExoPlayerMediaItem(mediaItem));
    }

    CountDownLatch onPlaylistChangedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistChanged(
              SessionPlayer player,
              @Nullable List<MediaItem> list,
              @Nullable MediaMetadata metadata) {
            if (Util.areEqual(list, playlistToExoPlayer)) {
              onPlaylistChangedLatch.countDown();
            }
          }
        });
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> playerTestRule.getSimpleExoPlayer().setMediaItems(exoMediaItems));
    assertThat(onPlaylistChangedLatch.await(PLAYLIST_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();
  }

  @Test
  @LargeTest
  public void setPlaylist_byUnderlyingPlayerAfterPrepare_notifiesOnPlaylistChanged()
      throws Exception {
    List<MediaItem> playlistToSessionPlayer = TestUtils.createPlaylist(2);
    List<MediaItem> playlistToExoPlayer = TestUtils.createPlaylist(4);
    DefaultMediaItemConverter converter = new DefaultMediaItemConverter();
    List<com.google.android.exoplayer2.MediaItem> exoMediaItems = new ArrayList<>();
    for (MediaItem mediaItem : playlistToExoPlayer) {
      exoMediaItems.add(converter.convertToExoPlayerMediaItem(mediaItem));
    }

    CountDownLatch onPlaylistChangedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistChanged(
              SessionPlayer player,
              @Nullable List<MediaItem> list,
              @Nullable MediaMetadata metadata) {
            if (Util.areEqual(list, playlistToExoPlayer)) {
              onPlaylistChangedLatch.countDown();
            }
          }
        });
    sessionPlayerConnector.prepare();
    sessionPlayerConnector.setPlaylist(playlistToSessionPlayer, /* metadata= */ null);
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> playerTestRule.getSimpleExoPlayer().setMediaItems(exoMediaItems));
    assertThat(onPlaylistChangedLatch.await(PLAYLIST_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();
  }

  @Test
  @LargeTest
  public void addPlaylistItem_calledOnlyOnce_notifiesPlaylistChangeOnlyOnce() throws Exception {
    List<MediaItem> playlist = TestUtils.createPlaylist(10);
    assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(playlist, /* metadata= */ null));
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    CountDownLatch onPlaylistChangedLatch = new CountDownLatch(2);
    int addIndex = 2;
    MediaItem newMediaItem = TestUtils.createMediaItem();
    playlist.add(addIndex, newMediaItem);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistChanged(
              SessionPlayer player,
              @Nullable List<MediaItem> list,
              @Nullable MediaMetadata metadata) {
            assertThat(list).isEqualTo(playlist);
            onPlaylistChangedLatch.countDown();
          }
        });
    sessionPlayerConnector.addPlaylistItem(addIndex, newMediaItem);
    assertThat(onPlaylistChangedLatch.await(PLAYLIST_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isFalse();
    assertThat(onPlaylistChangedLatch.getCount()).isEqualTo(1);
  }

  @Test
  @LargeTest
  public void removePlaylistItem_calledOnlyOnce_notifiesPlaylistChangeOnlyOnce() throws Exception {
    List<MediaItem> playlist = TestUtils.createPlaylist(10);
    assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(playlist, /* metadata= */ null));
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    CountDownLatch onPlaylistChangedLatch = new CountDownLatch(2);
    int removeIndex = 3;
    playlist.remove(removeIndex);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistChanged(
              SessionPlayer player,
              @Nullable List<MediaItem> list,
              @Nullable MediaMetadata metadata) {
            assertThat(list).isEqualTo(playlist);
            onPlaylistChangedLatch.countDown();
          }
        });
    sessionPlayerConnector.removePlaylistItem(removeIndex);
    assertThat(onPlaylistChangedLatch.await(PLAYLIST_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isFalse();
    assertThat(onPlaylistChangedLatch.getCount()).isEqualTo(1);
  }

  @Test
  @LargeTest
  public void movePlaylistItem_calledOnlyOnce_notifiesPlaylistChangeOnlyOnce() throws Exception {
    List<MediaItem> playlist = new ArrayList<>();
    playlist.add(TestUtils.createMediaItem(R.raw.video_1));
    playlist.add(TestUtils.createMediaItem(R.raw.video_2));
    playlist.add(TestUtils.createMediaItem(R.raw.video_3));
    assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(playlist, /* metadata= */ null));
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    CountDownLatch onPlaylistChangedLatch = new CountDownLatch(2);
    int moveFromIndex = 0;
    int moveToIndex = 2;
    playlist.add(moveToIndex, playlist.remove(moveFromIndex));
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistChanged(
              SessionPlayer player,
              @Nullable List<MediaItem> list,
              @Nullable MediaMetadata metadata) {
            assertThat(list).isEqualTo(playlist);
            onPlaylistChangedLatch.countDown();
          }
        });
    sessionPlayerConnector.movePlaylistItem(moveFromIndex, moveToIndex);
    assertThat(onPlaylistChangedLatch.await(PLAYLIST_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isFalse();
    assertThat(onPlaylistChangedLatch.getCount()).isEqualTo(1);
  }

  // TODO(b/168860979): De-flake and re-enable.
  @Ignore
  @Test
  @LargeTest
  public void replacePlaylistItem_calledOnlyOnce_notifiesPlaylistChangeOnlyOnce() throws Exception {
    List<MediaItem> playlist = TestUtils.createPlaylist(10);
    assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(playlist, /* metadata= */ null));
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    CountDownLatch onPlaylistChangedLatch = new CountDownLatch(2);
    int replaceIndex = 2;
    MediaItem newMediaItem = TestUtils.createMediaItem(R.raw.video_big_buck_bunny);
    playlist.set(replaceIndex, newMediaItem);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistChanged(
              SessionPlayer player,
              @Nullable List<MediaItem> list,
              @Nullable MediaMetadata metadata) {
            assertThat(list).isEqualTo(playlist);
            onPlaylistChangedLatch.countDown();
          }
        });
    sessionPlayerConnector.replacePlaylistItem(replaceIndex, newMediaItem);
    assertThat(onPlaylistChangedLatch.await(PLAYLIST_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isFalse();
    assertThat(onPlaylistChangedLatch.getCount()).isEqualTo(1);
  }

  @Test
  @LargeTest
  public void setPlaylist_withPlaylist_notifiesOnCurrentMediaItemChanged() throws Exception {
    int listSize = 2;
    List<MediaItem> playlist = TestUtils.createPlaylist(listSize);

    PlayerCallbackForPlaylist callback = new PlayerCallbackForPlaylist(playlist, 1);
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(playlist, null));
    assertThat(sessionPlayerConnector.getCurrentMediaItemIndex()).isEqualTo(0);
    assertThat(callback.await(PLAYLIST_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();
  }

  @Test
  @LargeTest
  public void play_twice_finishes() throws Exception {
    TestUtils.loadResource(R.raw.audio, sessionPlayerConnector);
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    assertPlayerResultSuccess(sessionPlayerConnector.play());
    assertPlayerResult(sessionPlayerConnector.play(), RESULT_INFO_SKIPPED);
  }

  @Test
  @LargeTest
  public void play_withPlaylist_notifiesOnCurrentMediaItemChangedAndOnPlaybackCompleted()
      throws Exception {
    List<MediaItem> playlist = new ArrayList<>();
    playlist.add(TestUtils.createMediaItem(R.raw.video_1));
    playlist.add(TestUtils.createMediaItem(R.raw.video_2));
    playlist.add(TestUtils.createMediaItem(R.raw.video_3));

    CountDownLatch onPlaybackCompletedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          int currentMediaItemChangedCount = 0;

          @Override
          public void onCurrentMediaItemChanged(SessionPlayer player, MediaItem item) {
            assertThat(item).isEqualTo(player.getCurrentMediaItem());

            int expectedCurrentIndex = currentMediaItemChangedCount++;
            assertThat(player.getCurrentMediaItemIndex()).isEqualTo(expectedCurrentIndex);
            assertThat(item).isEqualTo(playlist.get(expectedCurrentIndex));
          }

          @Override
          public void onPlaybackCompleted(SessionPlayer player) {
            onPlaybackCompletedLatch.countDown();
          }
        });

    assertThat(sessionPlayerConnector.setPlaylist(playlist, null)).isNotNull();
    assertThat(sessionPlayerConnector.prepare()).isNotNull();
    assertThat(sessionPlayerConnector.play()).isNotNull();

    assertThat(onPlaybackCompletedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, MILLISECONDS))
        .isTrue();
  }

  @Test
  @LargeTest
  public void play_byUnderlyingPlayer_notifiesOnPlayerStateChanges() throws Exception {
    TestUtils.loadResource(R.raw.audio, sessionPlayerConnector);
    SimpleExoPlayer simpleExoPlayer = playerTestRule.getSimpleExoPlayer();

    CountDownLatch onPlayingLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayerStateChanged(SessionPlayer player, int playerState) {
            if (playerState == PLAYER_STATE_PLAYING) {
              onPlayingLatch.countDown();
            }
          }
        });

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> simpleExoPlayer.setPlayWhenReady(true));

    assertThat(onPlayingLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();
  }

  @Test
  @LargeTest
  public void pause_twice_finishes() throws Exception {
    TestUtils.loadResource(R.raw.audio, sessionPlayerConnector);
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    assertPlayerResultSuccess(sessionPlayerConnector.play());
    assertPlayerResultSuccess(sessionPlayerConnector.pause());
    assertPlayerResult(sessionPlayerConnector.pause(), RESULT_INFO_SKIPPED);
  }

  @Test
  @LargeTest
  public void pause_byUnderlyingPlayer_notifiesOnPlayerStateChanges() throws Exception {
    TestUtils.loadResource(R.raw.audio, sessionPlayerConnector);
    SimpleExoPlayer simpleExoPlayer = playerTestRule.getSimpleExoPlayer();

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    CountDownLatch onPausedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayerStateChanged(SessionPlayer player, int playerState) {
            if (playerState == PLAYER_STATE_PAUSED) {
              onPausedLatch.countDown();
            }
          }
        });
    assertPlayerResultSuccess(sessionPlayerConnector.play());
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> simpleExoPlayer.setPlayWhenReady(false));

    assertThat(onPausedLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();
  }

  @Test
  @LargeTest
  public void pause_byUnderlyingPlayerInListener_changesToPlayerStatePaused() throws Exception {
    TestUtils.loadResource(R.raw.audio, sessionPlayerConnector);
    SimpleExoPlayer simpleExoPlayer = playerTestRule.getSimpleExoPlayer();

    CountDownLatch playerStateChangesLatch = new CountDownLatch(3);
    CopyOnWriteArrayList<Integer> playerStateChanges = new CopyOnWriteArrayList<>();
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayerStateChanged(SessionPlayer player, int playerState) {
            playerStateChanges.add(playerState);
            playerStateChangesLatch.countDown();
          }
        });

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () ->
                simpleExoPlayer.addListener(
                    new Player.EventListener() {
                      @Override
                      public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                        if (playWhenReady) {
                          simpleExoPlayer.setPlayWhenReady(false);
                        }
                      }
                    }));

    assertPlayerResultSuccess(sessionPlayerConnector.play());
    assertThat(playerStateChangesLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS))
        .isTrue();
    assertThat(playerStateChanges)
        .containsExactly(
            PLAYER_STATE_PAUSED, // After prepare()
            PLAYER_STATE_PLAYING, // After play()
            PLAYER_STATE_PAUSED) // After setPlayWhenREady(false)
        .inOrder();
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(PLAYER_STATE_PAUSED);
  }

  @Test
  @LargeTest
  public void skipToNextAndPrevious_calledInARow_notifiesOnCurrentMediaItemChanged()
      throws Exception {
    List<MediaItem> playlist = new ArrayList<>();
    playlist.add(TestUtils.createMediaItem(R.raw.video_1));
    playlist.add(TestUtils.createMediaItem(R.raw.video_2));
    playlist.add(TestUtils.createMediaItem(R.raw.video_3));
    assertThat(sessionPlayerConnector.setPlaylist(playlist, /* metadata= */ null)).isNotNull();

    // STEP 1: prepare()
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    // STEP 2: skipToNextPlaylistItem()
    CountDownLatch onNextMediaItemLatch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback skipToNextTestCallback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onCurrentMediaItemChanged(SessionPlayer player, MediaItem item) {
            super.onCurrentMediaItemChanged(player, item);
            assertThat(player.getCurrentMediaItemIndex()).isEqualTo(1);
            assertThat(item).isEqualTo(player.getCurrentMediaItem());
            assertThat(item).isEqualTo(playlist.get(1));
            onNextMediaItemLatch.countDown();
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, skipToNextTestCallback);
    assertPlayerResultSuccess(sessionPlayerConnector.skipToNextPlaylistItem());
    assertThat(onNextMediaItemLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS)).isTrue();
    sessionPlayerConnector.unregisterPlayerCallback(skipToNextTestCallback);

    // STEP 3: skipToPreviousPlaylistItem()
    CountDownLatch onPreviousMediaItemLatch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback skipToPreviousTestCallback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onCurrentMediaItemChanged(SessionPlayer player, MediaItem item) {
            super.onCurrentMediaItemChanged(player, item);
            assertThat(player.getCurrentMediaItemIndex()).isEqualTo(0);
            assertThat(item).isEqualTo(player.getCurrentMediaItem());
            assertThat(item).isEqualTo(playlist.get(0));
            onPreviousMediaItemLatch.countDown();
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, skipToPreviousTestCallback);
    assertPlayerResultSuccess(sessionPlayerConnector.skipToPreviousPlaylistItem());
    assertThat(onPreviousMediaItemLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, MILLISECONDS))
        .isTrue();
    sessionPlayerConnector.unregisterPlayerCallback(skipToPreviousTestCallback);
  }

  @Test
  @LargeTest
  public void setRepeatMode_withRepeatAll_continuesToPlayPlaylistWithoutBeingCompleted()
      throws Exception {
    List<MediaItem> playlist = new ArrayList<>();
    playlist.add(TestUtils.createMediaItem(R.raw.video_1));
    playlist.add(TestUtils.createMediaItem(R.raw.video_2));
    playlist.add(TestUtils.createMediaItem(R.raw.video_3));
    int listSize = playlist.size();

    // Any value more than list size + 1, to see repeat mode with the recorded video.
    CopyOnWriteArrayList<MediaItem> currentMediaItemChanges = new CopyOnWriteArrayList<>();
    PlayerCallbackForPlaylist callback =
        new PlayerCallbackForPlaylist(playlist, listSize + 2) {
          @Override
          public void onCurrentMediaItemChanged(SessionPlayer player, MediaItem item) {
            super.onCurrentMediaItemChanged(player, item);
            currentMediaItemChanges.add(item);
            countDown();
          }

          @Override
          public void onPlaybackCompleted(SessionPlayer player) {
            assertWithMessage(
                    "Playback shouldn't be completed, Actual changes were %s",
                    currentMediaItemChanges)
                .fail();
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    assertThat(sessionPlayerConnector.setPlaylist(playlist, null)).isNotNull();
    assertThat(sessionPlayerConnector.prepare()).isNotNull();
    assertThat(sessionPlayerConnector.setRepeatMode(SessionPlayer.REPEAT_MODE_ALL)).isNotNull();
    assertThat(sessionPlayerConnector.play()).isNotNull();

    assertWithMessage(
            "Current media item didn't change as expected. Actual changes were %s",
            currentMediaItemChanges)
        .that(callback.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, MILLISECONDS))
        .isTrue();

    int expectedMediaItemIndex = 0;
    for (MediaItem mediaItemInPlaybackOrder : currentMediaItemChanges) {
      assertWithMessage(
              "Unexpected media item for %sth playback. Actual changes were %s",
              expectedMediaItemIndex, currentMediaItemChanges)
          .that(mediaItemInPlaybackOrder)
          .isEqualTo(playlist.get(expectedMediaItemIndex));
      expectedMediaItemIndex = (expectedMediaItemIndex + 1) % listSize;
    }
  }

  @Test
  @LargeTest
  public void getPlayerState_withPrepareAndPlayAndPause_changesAsExpected() throws Exception {
    TestUtils.loadResource(R.raw.audio, sessionPlayerConnector);

    AudioAttributesCompat attributes =
        new AudioAttributesCompat.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build();
    sessionPlayerConnector.setAudioAttributes(attributes);
    sessionPlayerConnector.setRepeatMode(SessionPlayer.REPEAT_MODE_ALL);

    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(SessionPlayer.PLAYER_STATE_IDLE);
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    assertThat(sessionPlayerConnector.getPlayerState())
        .isEqualTo(SessionPlayer.PLAYER_STATE_PAUSED);
    assertPlayerResultSuccess(sessionPlayerConnector.play());
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(PLAYER_STATE_PLAYING);
  }

  @Test
  @LargeTest
  public void getPlaylist_returnsPlaylistInUnderlyingPlayer() {
    List<MediaItem> playlistToExoPlayer = TestUtils.createPlaylist(4);
    DefaultMediaItemConverter converter = new DefaultMediaItemConverter();
    List<com.google.android.exoplayer2.MediaItem> exoMediaItems = new ArrayList<>();
    for (MediaItem mediaItem : playlistToExoPlayer) {
      exoMediaItems.add(converter.convertToExoPlayerMediaItem(mediaItem));
    }

    AtomicReference<List<MediaItem>> playlistFromSessionPlayer = new AtomicReference<>();
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              SimpleExoPlayer simpleExoPlayer = playerTestRule.getSimpleExoPlayer();
              simpleExoPlayer.setMediaItems(exoMediaItems);

              try (SessionPlayerConnector sessionPlayer =
                  new SessionPlayerConnector(simpleExoPlayer)) {
                List<MediaItem> playlist = sessionPlayer.getPlaylist();
                playlistFromSessionPlayer.set(playlist);
              }
            });
    assertThat(playlistFromSessionPlayer.get()).isEqualTo(playlistToExoPlayer);
  }

  private class PlayerCallbackForPlaylist extends SessionPlayer.PlayerCallback {
    private final List<MediaItem> playlist;
    private CountDownLatch onCurrentMediaItemChangedLatch;

    PlayerCallbackForPlaylist(List<MediaItem> playlist, int count) {
      this.playlist = playlist;
      onCurrentMediaItemChangedLatch = new CountDownLatch(count);
    }

    @Override
    public void onCurrentMediaItemChanged(SessionPlayer player, MediaItem item) {
      int currentIndex = playlist.indexOf(item);
      assertThat(sessionPlayerConnector.getCurrentMediaItemIndex()).isEqualTo(currentIndex);
      onCurrentMediaItemChangedLatch.countDown();
    }

    public void resetLatch(int count) {
      onCurrentMediaItemChangedLatch = new CountDownLatch(count);
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
      return onCurrentMediaItemChangedLatch.await(timeout, unit);
    }

    public void countDown() {
      onCurrentMediaItemChangedLatch.countDown();
    }
  }
}
