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

import static androidx.media2.common.SessionPlayer.PLAYER_STATE_PAUSED;
import static androidx.media2.common.SessionPlayer.PLAYER_STATE_PLAYING;
import static androidx.media2.common.SessionPlayer.PlayerResult.RESULT_INFO_SKIPPED;
import static androidx.media2.common.SessionPlayer.PlayerResult.RESULT_SUCCESS;
import static com.google.android.exoplayer2.ext.media2.TestUtils.assertPlayerResult;
import static com.google.android.exoplayer2.ext.media2.TestUtils.assertPlayerResultSuccess;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media2.common.CallbackMediaItem;
import androidx.media2.common.DataSourceCallback;
import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.SessionPlayer;
import androidx.media2.common.SessionPlayer.PlayerResult;
import androidx.media2.common.UriMediaItem;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.media2.test.R;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests {@link SessionPlayerConnector}. */
@SuppressWarnings("FutureReturnValueIgnored")
@RunWith(AndroidJUnit4.class)
public class SessionPlayerConnectorTest {
  @Rule
  public final ActivityTestRule<MediaStubActivity> activityRule =
      new ActivityTestRule<>(MediaStubActivity.class);

  @Rule public final PlayerTestRule playerTestRule = new PlayerTestRule();

  private static final long PLAYLIST_CHANGE_WAIT_TIME_MS = 1_000;
  private static final long PLAYER_STATE_CHANGE_WAIT_TIME_MS = 5_000;
  private static final long PLAYBACK_COMPLETED_WAIT_TIME_MS = 20_000;
  private static final float FLOAT_TOLERANCE = .0001f;

  private Context context;
  private Resources resources;
  private Executor executor;
  private SessionPlayerConnector sessionPlayerConnector;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    resources = context.getResources();
    executor = playerTestRule.getExecutor();
    sessionPlayerConnector = playerTestRule.getSessionPlayerConnector();

    // Sets the surface to the player for manual check.
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              SimpleExoPlayer exoPlayer = playerTestRule.getSimpleExoPlayer();
              exoPlayer
                  .getVideoComponent()
                  .setVideoSurfaceHolder(activityRule.getActivity().getSurfaceHolder());
            });
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void play_onceWithAudioResource_changesPlayerStateToPlaying() throws Exception {
    TestUtils.loadResource(context, R.raw.testmp3_2, sessionPlayerConnector);

    AudioAttributesCompat attributes =
        new AudioAttributesCompat.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build();
    sessionPlayerConnector.setAudioAttributes(attributes);

    CountDownLatch onPlayingLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayerStateChanged(@NonNull SessionPlayer player, int playerState) {
            if (playerState == PLAYER_STATE_PLAYING) {
              onPlayingLatch.countDown();
            }
          }
        });

    sessionPlayerConnector.prepare();
    sessionPlayerConnector.play();
    assertThat(onPlayingLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
  }

  @Test
  @MediumTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void play_onceWithAudioResourceOnMainThread_notifiesOnPlayerStateChanged()
      throws Exception {
    CountDownLatch onPlayerStatePlayingLatch = new CountDownLatch(1);

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              try {
                TestUtils.loadResource(context, R.raw.testmp3_2, sessionPlayerConnector);
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
                    public void onPlayerStateChanged(
                        @NonNull SessionPlayer player, int playerState) {
                      if (playerState == PLAYER_STATE_PLAYING) {
                        onPlayerStatePlayingLatch.countDown();
                      }
                    }
                  });
              sessionPlayerConnector.prepare();
              sessionPlayerConnector.play();
            });
    assertThat(
            onPlayerStatePlayingLatch.await(
                PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void play_withCustomControlDispatcher_isSkipped() throws Exception {
    ControlDispatcher controlDispatcher =
        new DefaultControlDispatcher() {
          @Override
          public boolean dispatchSetPlayWhenReady(Player player, boolean playWhenReady) {
            return false;
          }
        };
    SimpleExoPlayer simpleExoPlayer = playerTestRule.getSimpleExoPlayer();
    ConcatenatingMediaSource concatenatingMediaSource = new ConcatenatingMediaSource();
    TimelinePlaylistManager timelinePlaylistManager =
        new TimelinePlaylistManager(context, concatenatingMediaSource);
    ConcatenatingMediaSourcePlaybackPreparer playbackPreparer =
        new ConcatenatingMediaSourcePlaybackPreparer(simpleExoPlayer, concatenatingMediaSource);

    try (SessionPlayerConnector player =
        new SessionPlayerConnector(
            simpleExoPlayer, timelinePlaylistManager, playbackPreparer, controlDispatcher)) {
      assertPlayerResult(player.play(), RESULT_INFO_SKIPPED);
    }
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void setMediaItem_withAudioResource_notifiesOnPlaybackCompleted() throws Exception {
    TestUtils.loadResource(context, R.raw.testmp3, sessionPlayerConnector);

    CountDownLatch onPlaybackCompletedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackCompleted(@NonNull SessionPlayer player) {
            onPlaybackCompletedLatch.countDown();
          }
        });
    sessionPlayerConnector.prepare();
    sessionPlayerConnector.play();

    // waiting to complete
    assertThat(
            onPlaybackCompletedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
    assertThat(sessionPlayerConnector.getPlayerState())
        .isEqualTo(SessionPlayer.PLAYER_STATE_PAUSED);
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void setMediaItem_withVideoResource_notifiesOnPlaybackCompleted() throws Exception {
    TestUtils.loadResource(context, R.raw.testvideo, sessionPlayerConnector);
    CountDownLatch onPlaybackCompletedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackCompleted(@NonNull SessionPlayer player) {
            onPlaybackCompletedLatch.countDown();
          }
        });
    sessionPlayerConnector.prepare();
    sessionPlayerConnector.play();

    // waiting to complete
    assertThat(
            onPlaybackCompletedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
    assertThat(sessionPlayerConnector.getPlayerState())
        .isEqualTo(SessionPlayer.PLAYER_STATE_PAUSED);
  }

  @Test
  @SmallTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void getDuration_whenIdleState_returnsUnknownTime() {
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(SessionPlayer.PLAYER_STATE_IDLE);
    assertThat(sessionPlayerConnector.getDuration()).isEqualTo(SessionPlayer.UNKNOWN_TIME);
  }

  @Test
  @MediumTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void getDuration_afterPrepared_returnsDuration() throws Exception {
    int expectedDuration = 5130;
    int tolerance = 50;

    TestUtils.loadResource(context, R.raw.testvideo, sessionPlayerConnector);

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    assertThat(sessionPlayerConnector.getPlayerState())
        .isEqualTo(SessionPlayer.PLAYER_STATE_PAUSED);
    assertThat((float) sessionPlayerConnector.getDuration())
        .isWithin(tolerance)
        .of(expectedDuration);
  }

  @Test
  @SmallTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void getCurrentPosition_whenIdleState_returnsUnknownTime() {
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(SessionPlayer.PLAYER_STATE_IDLE);
    assertThat(sessionPlayerConnector.getCurrentPosition()).isEqualTo(SessionPlayer.UNKNOWN_TIME);
  }

  @Test
  @SmallTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void getBufferedPosition_whenIdleState_returnsUnknownTime() {
    assertThat(sessionPlayerConnector.getPlayerState()).isEqualTo(SessionPlayer.PLAYER_STATE_IDLE);
    assertThat(sessionPlayerConnector.getBufferedPosition()).isEqualTo(SessionPlayer.UNKNOWN_TIME);
  }

  @Test
  @SmallTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
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
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void play_withDataSourceCallback_changesPlayerState() throws Exception {
    int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;

    TestDataSourceCallback dataSource =
        TestDataSourceCallback.fromAssetFd(resources.openRawResourceFd(resid));
    sessionPlayerConnector.setMediaItem(new CallbackMediaItem.Builder(dataSource).build());
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
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
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
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void setPlaybackSpeed_afterPlayback_remainsSame() throws Exception {
    int resId1 = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
    long start1 = 6_000;
    long end1 = 7_000;
    MediaItem mediaItem1 =
        new UriMediaItem.Builder(TestUtils.createResourceUri(context, resId1))
            .setStartPosition(start1)
            .setEndPosition(end1)
            .build();

    int resId2 = R.raw.testvideo;
    long start2 = 3_000;
    long end2 = 4_000;
    MediaItem mediaItem2 =
        new UriMediaItem.Builder(TestUtils.createResourceUri(context, resId2))
            .setStartPosition(start2)
            .setEndPosition(end2)
            .build();

    List<MediaItem> items = new ArrayList<>();
    items.add(mediaItem1);
    items.add(mediaItem2);
    sessionPlayerConnector.setPlaylist(items, null);

    CountDownLatch onPlaybackCompletedLatch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackCompleted(@NonNull SessionPlayer player) {
            onPlaybackCompletedLatch.countDown();
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    sessionPlayerConnector.prepare().get();

    sessionPlayerConnector.setPlaybackSpeed(2.0f);
    sessionPlayerConnector.play();

    assertThat(
            onPlaybackCompletedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
    assertThat(sessionPlayerConnector.getCurrentMediaItem()).isEqualTo(mediaItem2);
    assertThat(sessionPlayerConnector.getPlaybackSpeed()).isWithin(0.001f).of(2.0f);
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void seekTo_withSeriesOfSeek_succeeds() throws Exception {
    int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
    TestUtils.loadResource(context, resid, sessionPlayerConnector);

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    List<Long> testSeekPositions = Arrays.asList(3000L, 2000L, 1000L);
    for (long testSeekPosition : testSeekPositions) {
      assertPlayerResultSuccess(sessionPlayerConnector.seekTo(testSeekPosition));
      assertThat(sessionPlayerConnector.getCurrentPosition()).isEqualTo(testSeekPosition);
    }
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void seekTo_skipsUnnecessarySeek() throws Exception {
    int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
    TestDataSourceCallback source =
        TestDataSourceCallback.fromAssetFd(resources.openRawResourceFd(resid));
    CountDownLatch readAllowedLatch = new CountDownLatch(1);
    DataSourceCallback dataSource =
        new DataSourceCallback() {
          @Override
          public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
            try {
              assertThat(
                      readAllowedLatch.await(
                          PLAYBACK_COMPLETED_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
                  .isTrue();
            } catch (Exception e) {
              assertWithMessage("Unexpected exception %s", e).fail();
            }
            return source.readAt(position, buffer, offset, size);
          }

          @Override
          public long getSize() throws IOException {
            return source.getSize();
          }

          @Override
          public void close() throws IOException {
            source.close();
          }
        };

    sessionPlayerConnector.setMediaItem(new CallbackMediaItem.Builder(dataSource).build());

    // prepare() will be pending until readAllowed is countDowned.
    sessionPlayerConnector.prepare();

    AtomicLong seekPosition = new AtomicLong();
    long testFinalSeekToPosition = 1000;
    CountDownLatch onSeekCompletedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onSeekCompleted(@NonNull SessionPlayer player, long position) {
            // Do not assert here, because onSeekCompleted() can be called after the player is
            // closed.
            seekPosition.set(position);
            onSeekCompletedLatch.countDown();
          }
        });

    ListenableFuture<PlayerResult> seekFuture1 = sessionPlayerConnector.seekTo(3000);
    ListenableFuture<PlayerResult> seekFuture2 = sessionPlayerConnector.seekTo(2000);
    ListenableFuture<PlayerResult> seekFuture3 =
        sessionPlayerConnector.seekTo(testFinalSeekToPosition);

    readAllowedLatch.countDown();

    assertThat(seekFuture1.get().getResultCode()).isEqualTo(RESULT_INFO_SKIPPED);
    assertThat(seekFuture2.get().getResultCode()).isEqualTo(RESULT_INFO_SKIPPED);
    assertThat(seekFuture3.get().getResultCode()).isEqualTo(RESULT_SUCCESS);
    assertThat(onSeekCompletedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
    assertThat(seekPosition.get()).isEqualTo(testFinalSeekToPosition);
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void seekTo_whenUnderlyingPlayerAlsoSeeks_throwsNoException() throws Exception {
    int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
    TestUtils.loadResource(context, resid, sessionPlayerConnector);
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
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void seekTo_byUnderlyingPlayer_notifiesOnSeekCompleted() throws Exception {
    int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
    TestUtils.loadResource(context, resid, sessionPlayerConnector);
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    SimpleExoPlayer simpleExoPlayer = playerTestRule.getSimpleExoPlayer();
    long testSeekPosition = 1023;
    AtomicLong seekPosition = new AtomicLong();
    CountDownLatch onSeekCompletedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onSeekCompleted(@NonNull SessionPlayer player, long position) {
            // Do not assert here, because onSeekCompleted() can be called after the player is
            // closed.
            seekPosition.set(position);
            onSeekCompletedLatch.countDown();
          }
        });

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> simpleExoPlayer.seekTo(testSeekPosition));
    assertThat(onSeekCompletedLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
    assertThat(seekPosition.get()).isEqualTo(testSeekPosition);
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void getPlayerState_withCallingPrepareAndPlayAndPause_reflectsPlayerState()
      throws Throwable {
    TestUtils.loadResource(context, R.raw.testvideo, sessionPlayerConnector);
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
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void prepare_twice_finishes() throws Exception {
    TestUtils.loadResource(context, R.raw.testmp3, sessionPlayerConnector);
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    assertPlayerResult(sessionPlayerConnector.prepare(), RESULT_INFO_SKIPPED);
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void prepare_notifiesOnPlayerStateChanged() throws Throwable {
    TestUtils.loadResource(
        context,
        R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz,
        sessionPlayerConnector);

    CountDownLatch onPlayerStatePaused = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayerStateChanged(@NonNull SessionPlayer player, int state) {
            if (state == SessionPlayer.PLAYER_STATE_PAUSED) {
              onPlayerStatePaused.countDown();
            }
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    assertThat(onPlayerStatePaused.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void prepare_notifiesBufferingCompletedOnce() throws Throwable {
    TestUtils.loadResource(
        context,
        R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz,
        sessionPlayerConnector);

    CountDownLatch onBufferingCompletedLatch = new CountDownLatch(2);
    CopyOnWriteArrayList<Integer> bufferingStateChanges = new CopyOnWriteArrayList<>();
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onBufferingStateChanged(
              @NonNull SessionPlayer player, MediaItem item, int buffState) {
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
        .that(
            onBufferingCompletedLatch.await(
                PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
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
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void seekTo_whenPrepared_notifiesOnSeekCompleted() throws Throwable {
    long mp4DurationMs = 8_484L;
    TestUtils.loadResource(
        context,
        R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz,
        sessionPlayerConnector);

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    CountDownLatch onSeekCompletedLatch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onSeekCompleted(@NonNull SessionPlayer player, long position) {
            onSeekCompletedLatch.countDown();
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    sessionPlayerConnector.seekTo(mp4DurationMs >> 1);

    assertThat(onSeekCompletedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void setPlaybackSpeed_whenPrepared_notifiesOnPlaybackSpeedChanged() throws Throwable {
    TestUtils.loadResource(
        context,
        R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz,
        sessionPlayerConnector);

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    CountDownLatch onPlaybackSpeedChangedLatch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback callback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaybackSpeedChanged(@NonNull SessionPlayer player, float speed) {
            assertThat(speed).isWithin(FLOAT_TOLERANCE).of(0.5f);
            onPlaybackSpeedChangedLatch.countDown();
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, callback);

    sessionPlayerConnector.setPlaybackSpeed(0.5f);

    assertThat(
            onPlaybackSpeedChangedLatch.await(
                PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
  }

  @Test
  @SmallTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
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
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
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
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void close_throwsNoExceptionAndDoesNotCrash() throws Exception {
    TestUtils.loadResource(context, R.raw.testmp3_2, sessionPlayerConnector);
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
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void cancelReturnedFuture_withSeekTo_cancelsPendingCommand() throws Exception {
    CountDownLatch readRequestedLatch = new CountDownLatch(1);
    CountDownLatch readAllowedLatch = new CountDownLatch(1);
    // Need to wait from prepare() to counting down readAllowedLatch.
    DataSourceCallback dataSource =
        new DataSourceCallback() {
          TestDataSourceCallback testSource =
              TestDataSourceCallback.fromAssetFd(resources.openRawResourceFd(R.raw.testmp3));

          @Override
          public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
            readRequestedLatch.countDown();
            try {
              assertThat(
                      readAllowedLatch.await(
                          PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
                  .isTrue();
            } catch (Exception e) {
              assertWithMessage("Unexpected exception %s", e).fail();
            }
            return testSource.readAt(position, buffer, offset, size);
          }

          @Override
          public long getSize() throws IOException {
            return testSource.getSize();
          }

          @Override
          public void close() {
            testSource.close();
          }
        };
    assertPlayerResultSuccess(
        sessionPlayerConnector.setMediaItem(new CallbackMediaItem.Builder(dataSource).build()));

    // prepare() will be pending until readAllowed is countDowned.
    ListenableFuture<PlayerResult> prepareFuture = sessionPlayerConnector.prepare();
    ListenableFuture<PlayerResult> seekFuture = sessionPlayerConnector.seekTo(1000);

    assertThat(readRequestedLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();

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
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void setPlaylist_withNullPlaylist_throwsException() throws Exception {
    List<MediaItem> playlist = TestUtils.createPlaylist(context, 10);
    try {
      sessionPlayerConnector.setPlaylist(null, null);
      assertWithMessage("null playlist shouldn't be allowed").fail();
    } catch (Exception e) {
      // pass-through
    }
  }

  @Test
  @SmallTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
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
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void setPlaylist_setsPlaylistAndCurrentMediaItem() throws Exception {
    List<MediaItem> playlist = TestUtils.createPlaylist(context, 10);
    CountDownLatch onCurrentMediaItemChangedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor, new PlayerCallbackForPlaylist(playlist, onCurrentMediaItemChangedLatch));

    assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(playlist, null));
    assertThat(
            onCurrentMediaItemChangedLatch.await(
                PLAYLIST_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();

    assertThat(sessionPlayerConnector.getPlaylist()).isEqualTo(playlist);
    assertThat(sessionPlayerConnector.getCurrentMediaItem()).isEqualTo(playlist.get(0));
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void setPlaylist_calledOnlyOnce_notifiesPlaylistChangeOnlyOnce() throws Exception {
    List<MediaItem> playlist = TestUtils.createPlaylist(context, 10);
    CountDownLatch onPlaylistChangedLatch = new CountDownLatch(2);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistChanged(
              @NonNull SessionPlayer player,
              @Nullable List<MediaItem> list,
              @Nullable MediaMetadata metadata) {
            assertThat(list).isEqualTo(playlist);
            onPlaylistChangedLatch.countDown();
          }
        });

    sessionPlayerConnector.setPlaylist(playlist, /* metadata= */ null);
    sessionPlayerConnector.prepare();
    assertThat(onPlaylistChangedLatch.await(PLAYLIST_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isFalse();
    assertThat(onPlaylistChangedLatch.getCount()).isEqualTo(1);
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void addPlaylistItem_calledOnlyOnce_notifiesPlaylistChangeOnlyOnce() throws Exception {
    List<MediaItem> playlist = TestUtils.createPlaylist(context, 10);
    assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(playlist, /* metadata= */ null));
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    CountDownLatch onPlaylistChangedLatch = new CountDownLatch(2);
    int addIndex = 2;
    MediaItem newMediaItem = TestUtils.createMediaItem(context);
    playlist.add(addIndex, newMediaItem);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistChanged(
              @NonNull SessionPlayer player,
              @Nullable List<MediaItem> list,
              @Nullable MediaMetadata metadata) {
            assertThat(list).isEqualTo(playlist);
            onPlaylistChangedLatch.countDown();
          }
        });
    sessionPlayerConnector.addPlaylistItem(addIndex, newMediaItem);
    assertThat(onPlaylistChangedLatch.await(PLAYLIST_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isFalse();
    assertThat(onPlaylistChangedLatch.getCount()).isEqualTo(1);
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void removePlaylistItem_calledOnlyOnce_notifiesPlaylistChangeOnlyOnce() throws Exception {
    List<MediaItem> playlist = TestUtils.createPlaylist(context, 10);
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
              @NonNull SessionPlayer player,
              @Nullable List<MediaItem> list,
              @Nullable MediaMetadata metadata) {
            assertThat(list).isEqualTo(playlist);
            onPlaylistChangedLatch.countDown();
          }
        });
    sessionPlayerConnector.removePlaylistItem(removeIndex);
    assertThat(onPlaylistChangedLatch.await(PLAYLIST_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isFalse();
    assertThat(onPlaylistChangedLatch.getCount()).isEqualTo(1);
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void replacePlaylistItem_calledOnlyOnce_notifiesPlaylistChangeOnlyOnce() throws Exception {
    List<MediaItem> playlist = TestUtils.createPlaylist(context, 10);
    assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(playlist, /* metadata= */ null));
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    CountDownLatch onPlaylistChangedLatch = new CountDownLatch(2);
    int replaceIndex = 2;
    MediaItem newMediaItem = TestUtils.createMediaItem(context);
    playlist.set(replaceIndex, newMediaItem);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistChanged(
              @NonNull SessionPlayer player,
              @Nullable List<MediaItem> list,
              @Nullable MediaMetadata metadata) {
            assertThat(list).isEqualTo(playlist);
            onPlaylistChangedLatch.countDown();
          }
        });
    sessionPlayerConnector.replacePlaylistItem(replaceIndex, newMediaItem);
    assertThat(onPlaylistChangedLatch.await(PLAYLIST_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isFalse();
    assertThat(onPlaylistChangedLatch.getCount()).isEqualTo(1);
  }

  @Test
  @LargeTest
  @Ignore("setMediaItem() is currently implemented with setPlaylist(), so list isn't empty.")
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void setMediaItem_afterSettingPlaylist_notifiesOnPlaylistChangedWithNullList()
      throws Exception {
    List<MediaItem> playlist = TestUtils.createPlaylist(context, /* size= */ 10);
    CountDownLatch onPlaylistBecomesNullLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlaylistChanged(
              @NonNull SessionPlayer player,
              @Nullable List<MediaItem> list,
              @Nullable MediaMetadata metadata) {
            if (list == null) {
              onPlaylistBecomesNullLatch.countDown();
            }
          }
        });
    sessionPlayerConnector.setPlaylist(playlist, /* metadata= */ null);
    sessionPlayerConnector.setMediaItem(playlist.get(0));
    assertThat(
            onPlaylistBecomesNullLatch.await(PLAYLIST_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void setPlaylist_withPlaylist_notifiesOnCurrentMediaItemChanged() throws Exception {
    int listSize = 2;
    List<MediaItem> playlist = TestUtils.createPlaylist(context, listSize);

    CountDownLatch onCurrentMediaItemChangedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor, new PlayerCallbackForPlaylist(playlist, onCurrentMediaItemChangedLatch));

    assertPlayerResultSuccess(sessionPlayerConnector.setPlaylist(playlist, null));
    assertThat(sessionPlayerConnector.getCurrentMediaItemIndex()).isEqualTo(0);
    assertThat(
            onCurrentMediaItemChangedLatch.await(
                PLAYLIST_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void play_twice_finishes() throws Exception {
    TestUtils.loadResource(context, R.raw.testmp3, sessionPlayerConnector);
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    assertPlayerResultSuccess(sessionPlayerConnector.play());
    assertPlayerResult(sessionPlayerConnector.play(), RESULT_INFO_SKIPPED);
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void play_withPlaylist_notifiesOnCurrentMediaItemChangedAndOnPlaybackCompleted()
      throws Exception {
    List<MediaItem> playlist = new ArrayList<>();
    playlist.add(TestUtils.createMediaItem(context, R.raw.number1));
    playlist.add(TestUtils.createMediaItem(context, R.raw.number2));
    playlist.add(TestUtils.createMediaItem(context, R.raw.number3));

    CountDownLatch onPlaybackCompletedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          int currentMediaItemChangedCount = 0;

          @Override
          public void onCurrentMediaItemChanged(
              @NonNull SessionPlayer player, @NonNull MediaItem item) {
            assertThat(item).isEqualTo(player.getCurrentMediaItem());

            int currentIdx = player.getCurrentMediaItemIndex();
            int expectedCurrentIdx = currentMediaItemChangedCount++;
            assertThat(currentIdx).isEqualTo(expectedCurrentIdx);
            assertThat(item).isEqualTo(playlist.get(expectedCurrentIdx));
          }

          @Override
          public void onPlaybackCompleted(@NonNull SessionPlayer player) {
            onPlaybackCompletedLatch.countDown();
          }
        });

    assertThat(sessionPlayerConnector.setPlaylist(playlist, null)).isNotNull();
    assertThat(sessionPlayerConnector.prepare()).isNotNull();
    assertThat(sessionPlayerConnector.play()).isNotNull();

    assertThat(
            onPlaybackCompletedLatch.await(PLAYBACK_COMPLETED_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void play_byUnderlyingPlayer_notifiesOnPlayerStateChanges() throws Exception {
    TestUtils.loadResource(context, R.raw.testmp3_2, sessionPlayerConnector);
    SimpleExoPlayer simpleExoPlayer = playerTestRule.getSimpleExoPlayer();

    CountDownLatch onPlayingLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayerStateChanged(@NonNull SessionPlayer player, int playerState) {
            if (playerState == PLAYER_STATE_PLAYING) {
              onPlayingLatch.countDown();
            }
          }
        });

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> simpleExoPlayer.setPlayWhenReady(true));

    assertThat(onPlayingLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void pause_twice_finishes() throws Exception {
    TestUtils.loadResource(context, R.raw.testmp3, sessionPlayerConnector);
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());
    assertPlayerResultSuccess(sessionPlayerConnector.play());
    assertPlayerResultSuccess(sessionPlayerConnector.pause());
    assertPlayerResult(sessionPlayerConnector.pause(), RESULT_INFO_SKIPPED);
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void pause_byUnderlyingPlayer_notifiesOnPlayerStateChanges() throws Exception {
    TestUtils.loadResource(context, R.raw.testmp3_2, sessionPlayerConnector);
    SimpleExoPlayer simpleExoPlayer = playerTestRule.getSimpleExoPlayer();

    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    CountDownLatch onPausedLatch = new CountDownLatch(1);
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayerStateChanged(@NonNull SessionPlayer player, int playerState) {
            if (playerState == PLAYER_STATE_PAUSED) {
              onPausedLatch.countDown();
            }
          }
        });
    assertPlayerResultSuccess(sessionPlayerConnector.play());
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(() -> simpleExoPlayer.setPlayWhenReady(false));

    assertThat(onPausedLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void pause_byUnderlyingPlayerInListener_changesToPlayerStatePaused() throws Exception {
    TestUtils.loadResource(context, R.raw.testmp3_2, sessionPlayerConnector);
    SimpleExoPlayer simpleExoPlayer = playerTestRule.getSimpleExoPlayer();

    CountDownLatch playerStateChangesLatch = new CountDownLatch(3);
    CopyOnWriteArrayList<Integer> playerStateChanges = new CopyOnWriteArrayList<>();
    sessionPlayerConnector.registerPlayerCallback(
        executor,
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onPlayerStateChanged(@NonNull SessionPlayer player, int playerState) {
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
    assertThat(
            playerStateChangesLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
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
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void skipToNextAndPrevious_calledInARow_notifiesOnCurrentMediaItemChanged()
      throws Exception {
    List<MediaItem> playlist = new ArrayList<>();
    playlist.add(TestUtils.createMediaItem(context, R.raw.number1));
    playlist.add(TestUtils.createMediaItem(context, R.raw.number2));
    playlist.add(TestUtils.createMediaItem(context, R.raw.number3));
    assertThat(sessionPlayerConnector.setPlaylist(playlist, /* metadata= */ null)).isNotNull();

    // STEP 1: prepare()
    assertPlayerResultSuccess(sessionPlayerConnector.prepare());

    // STEP 2: skipToNextPlaylistItem()
    CountDownLatch onNextMediaItemLatch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback skipToNextTestCallback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onCurrentMediaItemChanged(
              @NonNull SessionPlayer player, @NonNull MediaItem item) {
            super.onCurrentMediaItemChanged(player, item);
            int expectedIndex = 1;
            assertThat(player.getCurrentMediaItemIndex()).isEqualTo(expectedIndex);
            assertThat(item).isEqualTo(player.getCurrentMediaItem());
            assertThat(item).isEqualTo(playlist.get(expectedIndex));
            onNextMediaItemLatch.countDown();
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, skipToNextTestCallback);
    assertPlayerResultSuccess(sessionPlayerConnector.skipToNextPlaylistItem());
    assertThat(onNextMediaItemLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
    sessionPlayerConnector.unregisterPlayerCallback(skipToNextTestCallback);

    // STEP 3: skipToPreviousPlaylistItem()
    CountDownLatch onPreviousMediaItemLatch = new CountDownLatch(1);
    SessionPlayer.PlayerCallback skipToPreviousTestCallback =
        new SessionPlayer.PlayerCallback() {
          @Override
          public void onCurrentMediaItemChanged(
              @NonNull SessionPlayer player, @NonNull MediaItem item) {
            super.onCurrentMediaItemChanged(player, item);
            int expectedIndex = 0;
            assertThat(player.getCurrentMediaItemIndex()).isEqualTo(expectedIndex);
            assertThat(item).isEqualTo(player.getCurrentMediaItem());
            assertThat(item).isEqualTo(playlist.get(expectedIndex));
            onPreviousMediaItemLatch.countDown();
          }
        };
    sessionPlayerConnector.registerPlayerCallback(executor, skipToPreviousTestCallback);
    assertPlayerResultSuccess(sessionPlayerConnector.skipToPreviousPlaylistItem());
    assertThat(
            onPreviousMediaItemLatch.await(PLAYER_STATE_CHANGE_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
        .isTrue();
    sessionPlayerConnector.unregisterPlayerCallback(skipToPreviousTestCallback);
  }

  @Test
  @LargeTest
  @SdkSuppress(minSdkVersion = Build.VERSION_CODES.KITKAT)
  public void setRepeatMode_withRepeatAll_continuesToPlayPlaylistWithoutBeingCompleted()
      throws Exception {
    List<MediaItem> playlist = new ArrayList<>();
    playlist.add(TestUtils.createMediaItem(context, R.raw.number1));
    playlist.add(TestUtils.createMediaItem(context, R.raw.number2));
    playlist.add(TestUtils.createMediaItem(context, R.raw.number3));
    int listSize = playlist.size();

    // Any value more than list size + 1, to see repeat mode with the recorded video.
    int expectedCurrentMediaItemChanges = listSize + 2;
    CountDownLatch onCurrentMediaItemChangedLatch =
        new CountDownLatch(expectedCurrentMediaItemChanges);
    CopyOnWriteArrayList<MediaItem> currentMediaItemChanges = new CopyOnWriteArrayList<>();
    PlayerCallbackForPlaylist callback =
        new PlayerCallbackForPlaylist(playlist, onCurrentMediaItemChangedLatch) {
          @Override
          public void onCurrentMediaItemChanged(
              @NonNull SessionPlayer player, @NonNull MediaItem item) {
            super.onCurrentMediaItemChanged(player, item);
            currentMediaItemChanges.add(item);
            onCurrentMediaItemChangedLatch.countDown();
          }

          @Override
          public void onPlaybackCompleted(@NonNull SessionPlayer player) {
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
        .that(
            onCurrentMediaItemChangedLatch.await(
                PLAYBACK_COMPLETED_WAIT_TIME_MS, TimeUnit.MILLISECONDS))
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
    TestUtils.loadResource(context, R.raw.testmp3, sessionPlayerConnector);

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

  private class PlayerCallbackForPlaylist extends SessionPlayer.PlayerCallback {
    private List<MediaItem> playlist;
    private CountDownLatch onCurrentMediaItemChangedLatch;

    PlayerCallbackForPlaylist(List<MediaItem> playlist, CountDownLatch latch) {
      this.playlist = playlist;
      onCurrentMediaItemChangedLatch = latch;
    }

    @Override
    public void onCurrentMediaItemChanged(@NonNull SessionPlayer player, @NonNull MediaItem item) {
      int currentIdx = playlist.indexOf(item);
      assertThat(sessionPlayerConnector.getCurrentMediaItemIndex()).isEqualTo(currentIdx);
      onCurrentMediaItemChangedLatch.countDown();
    }
  }
}
