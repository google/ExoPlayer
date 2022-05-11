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

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_USER_RATING;
import static androidx.media3.common.Player.STATE_ENDED;
import static androidx.media3.common.Player.STATE_READY;
import static androidx.media3.test.session.common.TestUtils.LONG_TIMEOUT_MS;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import androidx.media.AudioAttributesCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.HeartRating;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Player.State;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.PollingCheck;
import androidx.media3.test.session.common.TestHandler;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaControllerCompat.Callback} with {@link MediaSession}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaControllerCompatCallbackWithMediaSessionTest {

  private static final String TAG = "MCCCallbackTestWithMS2";
  private static final float EPSILON = 1e-6f;

  @Rule public final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);

  private Context context;
  private TestHandler handler;
  private RemoteMediaSession session;
  private MediaControllerCompat controllerCompat;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    handler = threadTestRule.getHandler();
    session = new RemoteMediaSession(TAG, context, null);
    controllerCompat = new MediaControllerCompat(context, session.getCompatToken());
  }

  @After
  public void cleanUp() throws Exception {
    session.release();
  }

  @Test
  public void gettersAfterConnected() throws Exception {
    @State int testState = STATE_READY;
    int testBufferingPosition = 1500;
    float testSpeed = 1.5f;
    int testItemIndex = 0;
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    testMediaItems.set(
        testItemIndex,
        new MediaItem.Builder()
            .setMediaId(testMediaItems.get(testItemIndex).mediaId)
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setUserRating(new HeartRating(/* isHeart= */ true))
                    .build())
            .build());
    Timeline testTimeline = new PlaylistTimeline(testMediaItems);
    String testPlaylistTitle = "testPlaylistTitle";
    MediaMetadata testPlaylistMetadata =
        new MediaMetadata.Builder().setTitle(testPlaylistTitle).build();
    boolean testShuffleModeEnabled = true;
    @RepeatMode int testRepeatMode = Player.REPEAT_MODE_ONE;

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackState(testState)
            .setBufferedPosition(testBufferingPosition)
            .setPlaybackParameters(new PlaybackParameters(testSpeed))
            .setTimeline(testTimeline)
            .setPlaylistMetadata(testPlaylistMetadata)
            .setCurrentMediaItemIndex(testItemIndex)
            .setShuffleModeEnabled(testShuffleModeEnabled)
            .setRepeatMode(testRepeatMode)
            .build();
    session.setPlayer(playerConfig);

    MediaControllerCompat controller = new MediaControllerCompat(context, session.getCompatToken());
    CountDownLatch latch = new CountDownLatch(1);
    controller.registerCallback(
        new MediaControllerCompat.Callback() {
          @Override
          public void onSessionReady() {
            latch.countDown();
          }
        },
        handler);

    assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(
            MediaUtils.convertToPlaybackState(
                controller.getPlaybackState(),
                controller.getMetadata(),
                /* timeDiffMs= */ C.TIME_UNSET))
        .isEqualTo(testState);
    assertThat(controller.getPlaybackState().getBufferedPosition())
        .isEqualTo(testBufferingPosition);
    assertThat(controller.getPlaybackState().getPlaybackSpeed()).isWithin(EPSILON).of(testSpeed);

    assertThat(controller.getMetadata().getString(METADATA_KEY_MEDIA_ID))
        .isEqualTo(testMediaItems.get(testItemIndex).mediaId);
    assertThat(controller.getRatingType()).isEqualTo(RatingCompat.RATING_HEART);

    List<QueueItem> queue = controller.getQueue();
    assertThat(queue).isNotNull();
    assertThat(queue).hasSize(testTimeline.getWindowCount());
    for (int i = 0; i < testTimeline.getWindowCount(); i++) {
      assertThat(queue.get(i).getDescription().getMediaId())
          .isEqualTo(testMediaItems.get(i).mediaId);
    }
    assertThat(testPlaylistTitle).isEqualTo(controller.getQueueTitle().toString());
    assertThat(PlaybackStateCompat.SHUFFLE_MODE_ALL).isEqualTo(controller.getShuffleMode());
    assertThat(PlaybackStateCompat.REPEAT_MODE_ONE).isEqualTo(controller.getRepeatMode());
  }

  @Test
  public void getError_withPlayerErrorAfterConnected_returnsError() throws Exception {
    PlaybackException testPlayerError =
        new PlaybackException(
            /* messaage= */ "testremote",
            /* cause= */ null,
            PlaybackException.ERROR_CODE_REMOTE_ERROR);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setPlayerError(testPlayerError).build();
    session.setPlayer(playerConfig);

    MediaControllerCompat controller = new MediaControllerCompat(context, session.getCompatToken());
    CountDownLatch latch = new CountDownLatch(1);
    controller.registerCallback(
        new MediaControllerCompat.Callback() {
          @Override
          public void onSessionReady() {
            latch.countDown();
          }
        },
        handler);

    assertThat(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    assertPlaybackStateCompatErrorEquals(controller.getPlaybackState(), testPlayerError);
  }

  @Test
  public void playerError_notified() throws Exception {
    PlaybackException testPlayerError =
        new PlaybackException(
            /* messaage= */ "player error",
            /* cause= */ null,
            PlaybackException.ERROR_CODE_UNSPECIFIED);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PlaybackStateCompat> playbackStateCompatRef = new AtomicReference<>();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateCompatRef.set(state);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().notifyPlayerError(testPlayerError);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    PlaybackStateCompat state = playbackStateCompatRef.get();
    assertPlaybackStateCompatErrorEquals(state, testPlayerError);
  }

  @Test
  public void repeatModeChange() throws Exception {
    @PlaybackStateCompat.RepeatMode int testRepeatMode = PlaybackStateCompat.REPEAT_MODE_ALL;

    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger repeatModeRef = new AtomicInteger();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onRepeatModeChanged(@PlaybackStateCompat.RepeatMode int repeatMode) {
            repeatModeRef.set(repeatMode);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().setRepeatMode(Player.REPEAT_MODE_ALL);
    session.getMockPlayer().notifyRepeatModeChanged();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(repeatModeRef.get()).isEqualTo(testRepeatMode);
    assertThat(controllerCompat.getRepeatMode()).isEqualTo(testRepeatMode);
  }

  @Test
  public void shuffleModeChange() throws Exception {
    @PlaybackStateCompat.ShuffleMode
    int testShuffleModeEnabled = PlaybackStateCompat.SHUFFLE_MODE_ALL;

    CountDownLatch latch = new CountDownLatch(1);
    AtomicInteger shuffleModeRef = new AtomicInteger();
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onShuffleModeChanged(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
            shuffleModeRef.set(shuffleMode);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().setShuffleModeEnabled(/* shuffleModeEnabled= */ true);
    session.getMockPlayer().notifyShuffleModeEnabledChanged();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(shuffleModeRef.get()).isEqualTo(testShuffleModeEnabled);
    assertThat(controllerCompat.getShuffleMode()).isEqualTo(testShuffleModeEnabled);
  }

  @Test
  public void release() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onSessionDestroyed() {
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.release();
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
  }

  @Test
  public void setPlayer_isNotified() throws Exception {
    @State int testState = STATE_READY;
    boolean testPlayWhenReady = true;
    long testDurationMs = 200;
    long testCurrentPositionMs = 11;
    long testBufferedPositionMs = 100;
    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 1.5f);
    int testItemIndex = 0;
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    testMediaItems.set(
        testItemIndex,
        new MediaItem.Builder()
            .setMediaId(testMediaItems.get(testItemIndex).mediaId)
            .setMediaMetadata(
                new MediaMetadata.Builder()
                    .setUserRating(new HeartRating(/* isHeart= */ true))
                    .build())
            .build());
    Timeline testTimeline = new PlaylistTimeline(testMediaItems);
    String testPlaylistTitle = "testPlaylistTitle";
    MediaMetadata testPlaylistMetadata =
        new MediaMetadata.Builder().setTitle(testPlaylistTitle).build();
    boolean testShuffleModeEnabled = true;
    @RepeatMode int testRepeatMode = Player.REPEAT_MODE_ONE;

    AtomicReference<PlaybackStateCompat> playbackStateRef = new AtomicReference<>();
    AtomicReference<MediaMetadataCompat> metadataRef = new AtomicReference<>();
    AtomicReference<CharSequence> queueTitleRef = new AtomicReference<>();
    AtomicInteger shuffleModeRef = new AtomicInteger();
    AtomicInteger repeatModeRef = new AtomicInteger();
    CountDownLatch latchForPlaybackState = new CountDownLatch(1);
    CountDownLatch latchForMetadata = new CountDownLatch(1);
    CountDownLatch latchForQueue = new CountDownLatch(2);
    CountDownLatch latchForShuffleMode = new CountDownLatch(1);
    CountDownLatch latchForRepeatMode = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateRef.set(state);
            latchForPlaybackState.countDown();
          }

          @Override
          public void onMetadataChanged(MediaMetadataCompat metadata) {
            metadataRef.set(metadata);
            latchForMetadata.countDown();
          }

          @Override
          public void onQueueChanged(List<QueueItem> queue) {
            latchForQueue.countDown();
          }

          @Override
          public void onQueueTitleChanged(CharSequence title) {
            queueTitleRef.set(title);
            latchForQueue.countDown();
          }

          @Override
          public void onRepeatModeChanged(int repeatMode) {
            repeatModeRef.set(repeatMode);
            latchForRepeatMode.countDown();
          }

          @Override
          public void onShuffleModeChanged(int shuffleMode) {
            shuffleModeRef.set(shuffleMode);
            latchForShuffleMode.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setPlaybackState(testState)
            .setPlayWhenReady(testPlayWhenReady)
            .setCurrentPosition(testCurrentPositionMs)
            .setBufferedPosition(testBufferedPositionMs)
            .setDuration(testDurationMs)
            .setPlaybackParameters(playbackParameters)
            .setTimeline(testTimeline)
            .setPlaylistMetadata(testPlaylistMetadata)
            .setCurrentMediaItemIndex(testItemIndex)
            .setShuffleModeEnabled(testShuffleModeEnabled)
            .setRepeatMode(testRepeatMode)
            .build();
    session.setPlayer(playerConfig);

    assertThat(latchForPlaybackState.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateRef.get().getBufferedPosition()).isEqualTo(testBufferedPositionMs);
    assertThat(playbackStateRef.get().getPosition()).isEqualTo(testCurrentPositionMs);
    assertThat(playbackStateRef.get().getPlaybackSpeed())
        .isWithin(EPSILON)
        .of(playbackParameters.speed);

    assertThat(latchForMetadata.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(metadataRef.get().getString(METADATA_KEY_MEDIA_ID))
        .isEqualTo(testMediaItems.get(testItemIndex).mediaId);
    assertThat(metadataRef.get().getLong(METADATA_KEY_DURATION)).isEqualTo(testDurationMs);
    @PlaybackStateCompat.State
    int playbackStateFromControllerCompat =
        MediaUtils.convertToPlaybackState(
            playbackStateRef.get(), metadataRef.get(), /* timeDiffMs= */ C.TIME_UNSET);
    assertThat(playbackStateFromControllerCompat).isEqualTo(testState);
    assertThat(metadataRef.get().getRating(METADATA_KEY_USER_RATING).hasHeart()).isTrue();

    assertThat(latchForQueue.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    List<QueueItem> queue = controllerCompat.getQueue();
    assertThat(queue).hasSize(testTimeline.getWindowCount());
    for (int i = 0; i < testTimeline.getWindowCount(); i++) {
      assertThat(queue.get(i).getDescription().getMediaId())
          .isEqualTo(testMediaItems.get(i).mediaId);
    }
    assertThat(queueTitleRef.get().toString()).isEqualTo(testPlaylistTitle);

    assertThat(latchForShuffleMode.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(shuffleModeRef.get()).isEqualTo(PlaybackStateCompat.SHUFFLE_MODE_ALL);
    assertThat(latchForRepeatMode.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    assertThat(repeatModeRef.get()).isEqualTo(PlaybackStateCompat.REPEAT_MODE_ONE);
  }

  @Test
  public void setPlayer_playbackTypeChangedToRemote() throws Exception {
    DeviceInfo deviceInfo =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_REMOTE, /* minVolume= */ 0, /* maxVolume= */ 25);
    int legacyPlaybackType = MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE;
    int deviceVolume = 10;

    CountDownLatch playbackInfoNotified = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
            if (info.getPlaybackType() == legacyPlaybackType
                && info.getMaxVolume() == deviceInfo.maxVolume
                && info.getCurrentVolume() == deviceVolume) {
              playbackInfoNotified.countDown();
            }
          }
        };
    controllerCompat.registerCallback(callback, handler);

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(deviceVolume)
            .build();
    session.setPlayer(playerConfig);

    assertThat(playbackInfoNotified.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    MediaControllerCompat.PlaybackInfo info = controllerCompat.getPlaybackInfo();
    assertThat(info.getPlaybackType()).isEqualTo(legacyPlaybackType);
    assertThat(info.getMaxVolume()).isEqualTo(deviceInfo.maxVolume);
    assertThat(info.getCurrentVolume()).isEqualTo(deviceVolume);
  }

  @Test
  public void setPlayer_playbackTypeChangedToLocal() throws Exception {
    DeviceInfo deviceInfo =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_REMOTE, /* minVolume= */ 0, /* maxVolume= */ 10);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder().setDeviceInfo(deviceInfo).build();
    session.setPlayer(playerConfig);

    DeviceInfo deviceInfoToUpdate =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_LOCAL, /* minVolume= */ 0, /* maxVolume= */ 10);
    int legacyPlaybackTypeToUpdate = MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_LOCAL;
    int legacyStream = AudioManager.STREAM_RING;
    AudioAttributesCompat attrsCompat =
        new AudioAttributesCompat.Builder().setLegacyStreamType(legacyStream).build();
    AudioAttributes attrs = MediaUtils.convertToAudioAttributes(attrsCompat);

    CountDownLatch playbackInfoNotified = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
            if (info.getPlaybackType() == legacyPlaybackTypeToUpdate
                && info.getAudioAttributes().getLegacyStreamType() == legacyStream) {
              playbackInfoNotified.countDown();
            }
          }
        };
    controllerCompat.registerCallback(callback, handler);

    Bundle playerConfigToUpdate =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfoToUpdate)
            .setAudioAttributes(attrs)
            .build();
    session.setPlayer(playerConfigToUpdate);

    // In API 21 and 22, onAudioInfoChanged is not called when playback is changed to local.
    if (Util.SDK_INT == 21 || Util.SDK_INT == 22) {
      PollingCheck.waitFor(
          TIMEOUT_MS,
          () -> {
            MediaControllerCompat.PlaybackInfo info = controllerCompat.getPlaybackInfo();
            return info.getPlaybackType() == legacyPlaybackTypeToUpdate
                && info.getAudioAttributes().getLegacyStreamType() == legacyStream;
          });
    } else {
      assertThat(playbackInfoNotified.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
      MediaControllerCompat.PlaybackInfo info = controllerCompat.getPlaybackInfo();
      assertThat(info.getPlaybackType()).isEqualTo(legacyPlaybackTypeToUpdate);
      assertThat(info.getAudioAttributes().getLegacyStreamType()).isEqualTo(legacyStream);
    }
  }

  @Test
  public void setPlayer_playbackTypeNotChanged_local() throws Exception {
    DeviceInfo deviceInfo =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_LOCAL, /* minVolume= */ 0, /* maxVolume= */ 10);
    int legacyPlaybackType = MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_LOCAL;
    int legacyStream = AudioManager.STREAM_RING;
    AudioAttributesCompat attrsCompat =
        new AudioAttributesCompat.Builder().setLegacyStreamType(legacyStream).build();
    AudioAttributes attrs = MediaUtils.convertToAudioAttributes(attrsCompat);

    CountDownLatch playbackInfoNotified = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
            if (info.getPlaybackType() == legacyPlaybackType
                && info.getAudioAttributes().getLegacyStreamType() == legacyStream) {
              playbackInfoNotified.countDown();
            }
          }
        };
    controllerCompat.registerCallback(callback, handler);

    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfo)
            .setAudioAttributes(attrs)
            .build();
    session.setPlayer(playerConfig);

    // In API 21+, onAudioInfoChanged() is not called when playbackType is not changed.
    if (Util.SDK_INT >= 21) {
      PollingCheck.waitFor(
          TIMEOUT_MS,
          () -> {
            MediaControllerCompat.PlaybackInfo info = controllerCompat.getPlaybackInfo();
            return info.getPlaybackType() == legacyPlaybackType
                && info.getAudioAttributes().getLegacyStreamType() == legacyStream;
          });
    } else {
      assertThat(playbackInfoNotified.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
      MediaControllerCompat.PlaybackInfo info = controllerCompat.getPlaybackInfo();
      assertThat(info.getPlaybackType()).isEqualTo(legacyPlaybackType);
      assertThat(info.getAudioAttributes().getLegacyStreamType()).isEqualTo(legacyStream);
    }
  }

  @Test
  public void setPlayer_playbackTypeNotChanged_remote() throws Exception {
    DeviceInfo deviceInfo =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_REMOTE, /* minVolume= */ 0, /* maxVolume= */ 10);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(1)
            .build();
    session.setPlayer(playerConfig);

    DeviceInfo deviceInfoToUpdate =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_REMOTE, /* minVolume= */ 0, /* maxVolume= */ 25);
    int legacyPlaybackTypeToUpdate = MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE;
    int deviceVolumeToUpdate = 10;

    CountDownLatch playbackInfoNotified = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
            if (info.getPlaybackType() == legacyPlaybackTypeToUpdate
                && info.getMaxVolume() == deviceInfoToUpdate.maxVolume
                && info.getCurrentVolume() == deviceVolumeToUpdate) {
              playbackInfoNotified.countDown();
            }
          }
        };
    controllerCompat.registerCallback(callback, handler);

    Bundle playerConfigToUpdate =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfoToUpdate)
            .setDeviceVolume(deviceVolumeToUpdate)
            .build();
    session.setPlayer(playerConfigToUpdate);

    // In API 21+, onAudioInfoChanged() is not called when playbackType is not changed.
    if (Util.SDK_INT >= 21) {
      PollingCheck.waitFor(
          TIMEOUT_MS,
          () -> {
            MediaControllerCompat.PlaybackInfo info = controllerCompat.getPlaybackInfo();
            return info.getPlaybackType() == legacyPlaybackTypeToUpdate
                && info.getMaxVolume() == deviceInfoToUpdate.maxVolume
                && info.getCurrentVolume() == deviceVolumeToUpdate;
          });
    } else {
      assertThat(playbackInfoNotified.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
      MediaControllerCompat.PlaybackInfo info = controllerCompat.getPlaybackInfo();
      assertThat(info.getPlaybackType()).isEqualTo(legacyPlaybackTypeToUpdate);
      assertThat(info.getMaxVolume()).isEqualTo(deviceInfoToUpdate.maxVolume);
      assertThat(info.getCurrentVolume()).isEqualTo(deviceVolumeToUpdate);
    }
  }

  @Test
  public void onPlaybackParametersChanged_notifiesPlaybackStateCompatChanges() throws Exception {
    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 1.5f);

    AtomicReference<PlaybackStateCompat> playbackStateRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateRef.set(state);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().notifyPlaybackParametersChanged(playbackParameters);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateRef.get().getPlaybackSpeed())
        .isWithin(EPSILON)
        .of(playbackParameters.speed);
    assertThat(controllerCompat.getPlaybackState().getPlaybackSpeed())
        .isWithin(EPSILON)
        .of(playbackParameters.speed);
  }

  @Test
  public void playbackStateChange_playWhenReadyBecomesFalseWhenReady_notifiesPaused()
      throws Exception {
    session
        .getMockPlayer()
        .setPlayWhenReady(
            /* playWhenReady= */ true,
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    session.getMockPlayer().notifyPlaybackStateChanged(STATE_READY);
    session.getMockPlayer().notifyIsPlayingChanged(false);

    AtomicReference<PlaybackStateCompat> playbackStateCompatRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat playbackStateCompat) {
            playbackStateCompatRef.set(playbackStateCompat);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session
        .getMockPlayer()
        .notifyPlayWhenReadyChanged(
            /* playWhenReady= */ false, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(playbackStateCompatRef.get().getState()).isEqualTo(PlaybackStateCompat.STATE_PAUSED);
  }

  @Test
  public void playbackStateChange_playWhenReadyBecomesTrueWhenBuffering_notifiesBuffering()
      throws Exception {
    session
        .getMockPlayer()
        .setPlayWhenReady(/* playWhenReady= */ false, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    session.getMockPlayer().notifyPlaybackStateChanged(Player.STATE_BUFFERING);
    session.getMockPlayer().notifyIsPlayingChanged(false);

    AtomicReference<PlaybackStateCompat> playbackStateCompatRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat playbackStateCompat) {
            playbackStateCompatRef.set(playbackStateCompat);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);
    session
        .getMockPlayer()
        .notifyPlayWhenReadyChanged(
            /* playWhenReady= */ true, Player.PLAYBACK_SUPPRESSION_REASON_NONE);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(playbackStateCompatRef.get().getState())
        .isEqualTo(PlaybackStateCompat.STATE_BUFFERING);
  }

  @Test
  public void playbackStateChange_playbackStateBecomesEnded_notifiesPaused() throws Exception {
    session
        .getMockPlayer()
        .setPlayWhenReady(
            /* playWhenReady= */ true,
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS);
    session.getMockPlayer().notifyPlaybackStateChanged(STATE_READY);
    session.getMockPlayer().notifyIsPlayingChanged(false);

    AtomicReference<PlaybackStateCompat> playbackStateCompatRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat playbackStateCompat) {
            playbackStateCompatRef.set(playbackStateCompat);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().notifyPlaybackStateChanged(STATE_ENDED);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(playbackStateCompatRef.get().getState()).isEqualTo(PlaybackStateCompat.STATE_PAUSED);
  }

  @Test
  public void playbackStateChange_isPlayingBecomesTrue_notifiesPlaying() throws Exception {
    session.getMockPlayer().notifyIsPlayingChanged(false);

    AtomicReference<PlaybackStateCompat> playbackStateCompatRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat playbackStateCompat) {
            playbackStateCompatRef.set(playbackStateCompat);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().notifyIsPlayingChanged(true);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(playbackStateCompatRef.get().getState())
        .isEqualTo(PlaybackStateCompat.STATE_PLAYING);
  }

  @Test
  public void playbackStateChange_positionDiscontinuityNotifies_updatesPosition() throws Exception {
    long testSeekPosition = 1300;

    AtomicReference<PlaybackStateCompat> playbackStateRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateRef.set(state);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().setCurrentPosition(testSeekPosition);
    session
        .getMockPlayer()
        .notifyPositionDiscontinuity(
            /* oldPosition= */ SessionPositionInfo.DEFAULT_POSITION_INFO,
            /* newPosition= */ SessionPositionInfo.DEFAULT_POSITION_INFO,
            Player.DISCONTINUITY_REASON_SEEK);
    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateRef.get().getPosition()).isEqualTo(testSeekPosition);
    assertThat(controllerCompat.getPlaybackState().getPosition()).isEqualTo(testSeekPosition);
  }

  @Test
  public void customLayoutChanged_updatesPlaybackStateCompat() throws Exception {

    AtomicReference<PlaybackStateCompat> playbackStateRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateRef.set(state);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    List<CommandButton> customLayout = new ArrayList<>();
    Bundle customCommandBundle1 = new Bundle();
    customCommandBundle1.putString("customKey1", "customValue1");
    customLayout.add(
        new CommandButton.Builder()
            .setDisplayName("customCommandName1")
            .setIconResId(1)
            .setSessionCommand(new SessionCommand("customCommandAction1", customCommandBundle1))
            .build());
    Bundle customCommandBundle2 = new Bundle();
    customCommandBundle2.putString("customKey2", "customValue2");
    customLayout.add(
        new CommandButton.Builder()
            .setDisplayName("customCommandName2")
            .setIconResId(2)
            .setSessionCommand(new SessionCommand("customCommandAction2", customCommandBundle2))
            .build());

    session.setCustomLayout(customLayout);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    List<PlaybackStateCompat.CustomAction> customActions =
        playbackStateRef.get().getCustomActions();
    assertThat(customActions).hasSize(2);
    assertThat(customActions.get(0).getAction()).isEqualTo("customCommandAction1");
    assertThat(customActions.get(0).getName()).isEqualTo("customCommandName1");
    assertThat(customActions.get(0).getIcon()).isEqualTo(1);
    assertThat(TestUtils.equals(customActions.get(0).getExtras(), customCommandBundle1)).isTrue();
    assertThat(customActions.get(1).getAction()).isEqualTo("customCommandAction2");
    assertThat(customActions.get(1).getName()).isEqualTo("customCommandName2");
    assertThat(customActions.get(1).getIcon()).isEqualTo(2);
    assertThat(TestUtils.equals(customActions.get(1).getExtras(), customCommandBundle2)).isTrue();
  }

  @Test
  public void currentMediaItemChange() throws Exception {
    int testItemIndex = 3;
    long testPosition = 1234;
    String testDisplayTitle = "displayTitle";
    List<MediaItem> testMediaItems = MediaTestUtils.createMediaItems(/* size= */ 5);
    testMediaItems.set(
        testItemIndex,
        new MediaItem.Builder()
            .setMediaId(testMediaItems.get(testItemIndex).mediaId)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(testDisplayTitle).build())
            .build());
    Timeline timeline = new PlaylistTimeline(testMediaItems);
    session.getMockPlayer().setTimeline(timeline);

    AtomicReference<MediaMetadataCompat> metadataRef = new AtomicReference<>();
    AtomicReference<PlaybackStateCompat> playbackStateRef = new AtomicReference<>();
    CountDownLatch latchForMetadata = new CountDownLatch(1);
    CountDownLatch latchForPlaybackState = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onMetadataChanged(MediaMetadataCompat metadata) {
            metadataRef.set(metadata);
            latchForMetadata.countDown();
          }

          @Override
          public void onPlaybackStateChanged(PlaybackStateCompat state) {
            playbackStateRef.set(state);
            latchForPlaybackState.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().setCurrentMediaItemIndex(testItemIndex);
    session.getMockPlayer().setCurrentPosition(testPosition);
    session
        .getMockPlayer()
        .notifyMediaItemTransition(testItemIndex, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK);

    assertThat(latchForMetadata.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(metadataRef.get().getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
        .isEqualTo(testDisplayTitle);
    assertThat(
            controllerCompat
                .getMetadata()
                .getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
        .isEqualTo(testDisplayTitle);
    assertThat(latchForPlaybackState.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(playbackStateRef.get().getPosition()).isEqualTo(testPosition);
    assertThat(controllerCompat.getPlaybackState().getPosition()).isEqualTo(testPosition);
    assertThat(playbackStateRef.get().getActiveQueueItemId())
        .isEqualTo(MediaUtils.convertToQueueItemId(testItemIndex));
    assertThat(controllerCompat.getPlaybackState().getActiveQueueItemId())
        .isEqualTo(MediaUtils.convertToQueueItemId(testItemIndex));
  }

  @Test
  public void playlistChange() throws Exception {
    AtomicReference<List<QueueItem>> queueRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onQueueChanged(List<QueueItem> queue) {
            queueRef.set(queue);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    Timeline timeline = MediaTestUtils.createTimeline(/* windowCount= */ 5);
    session.getMockPlayer().setTimeline(timeline);
    session.getMockPlayer().notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    List<QueueItem> queueFromParam = queueRef.get();
    List<QueueItem> queueFromGetter = controllerCompat.getQueue();
    assertThat(queueFromParam).hasSize(timeline.getWindowCount());
    assertThat(queueFromGetter).hasSize(timeline.getWindowCount());
    Timeline.Window window = new Timeline.Window();
    for (int i = 0; i < timeline.getWindowCount(); i++) {
      assertThat(queueFromParam.get(i).getDescription().getMediaId())
          .isEqualTo(timeline.getWindow(i, window).mediaItem.mediaId);
      assertThat(queueFromGetter.get(i).getDescription().getMediaId())
          .isEqualTo(timeline.getWindow(i, window).mediaItem.mediaId);
    }
  }

  @Test
  public void playlistChange_longList() throws Exception {
    AtomicReference<List<QueueItem>> queueRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onQueueChanged(List<QueueItem> queue) {
            queueRef.set(queue);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    int listSize = 5_000;
    session.getMockPlayer().createAndSetFakeTimeline(listSize);
    session.getMockPlayer().notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    assertThat(latch.await(LONG_TIMEOUT_MS, MILLISECONDS)).isTrue();
    List<QueueItem> queueFromParam = queueRef.get();
    List<QueueItem> queueFromGetter = controllerCompat.getQueue();
    if (Util.SDK_INT >= 21) {
      assertThat(queueFromParam).hasSize(listSize);
      assertThat(queueFromGetter).hasSize(listSize);
    } else {
      // Below API 21, only the initial part of the playlist is sent to the
      // MediaControllerCompat when the list is too long.
      assertThat(queueFromParam.size() < listSize).isTrue();
      assertThat(queueFromGetter).hasSize(queueFromParam.size());
    }
    for (int i = 0; i < queueFromParam.size(); i++) {
      assertThat(queueFromParam.get(i).getDescription().getMediaId())
          .isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
      assertThat(queueFromGetter.get(i).getDescription().getMediaId())
          .isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
  }

  @Test
  public void playlistChange_withMetadata() throws Exception {
    AtomicReference<List<QueueItem>> queueRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onQueueChanged(List<QueueItem> queue) {
            queueRef.set(queue);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    MediaItem mediaItem =
        new MediaItem.Builder()
            .setMediaId("mediaItem_withSampleMediaMetadata")
            .setMediaMetadata(MediaTestUtils.createMediaMetadata())
            .build();
    Timeline timeline = new PlaylistTimeline(ImmutableList.of(mediaItem));
    session.getMockPlayer().setTimeline(timeline);
    session.getMockPlayer().notifyTimelineChanged(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    List<QueueItem> queueFromParam = queueRef.get();
    assertThat(queueFromParam).hasSize(1);
    MediaDescriptionCompat description = queueFromParam.get(0).getDescription();
    assertThat(description.getMediaId()).isEqualTo(mediaItem.mediaId);
    assertThat(TextUtils.equals(description.getTitle(), mediaItem.mediaMetadata.title)).isTrue();
    assertThat(TextUtils.equals(description.getSubtitle(), mediaItem.mediaMetadata.subtitle))
        .isTrue();
    assertThat(TextUtils.equals(description.getDescription(), mediaItem.mediaMetadata.description))
        .isTrue();
    assertThat(description.getIconUri()).isEqualTo(mediaItem.mediaMetadata.artworkUri);
    assertThat(description.getMediaUri()).isEqualTo(mediaItem.mediaMetadata.mediaUri);
    assertThat(TestUtils.equals(description.getExtras(), mediaItem.mediaMetadata.extras)).isTrue();
  }

  @Test
  public void playlistMetadataChange() throws Exception {
    AtomicReference<CharSequence> queueTitleRef = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onQueueTitleChanged(CharSequence title) {
            queueTitleRef.set(title);
            latch.countDown();
          }
        };
    controllerCompat.registerCallback(callback, handler);

    String playlistTitle = "playlistTitle";
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setTitle(playlistTitle).build();
    session.getMockPlayer().setPlaylistMetadata(playlistMetadata);
    session.getMockPlayer().notifyPlaylistMetadataChanged();

    assertThat(latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(queueTitleRef.get().toString()).isEqualTo(playlistTitle);
  }

  @Test
  public void onAudioInfoChanged_isCalledByVolumeChange() throws Exception {
    DeviceInfo deviceInfo =
        new DeviceInfo(DeviceInfo.PLAYBACK_TYPE_REMOTE, /* minVolume= */ 0, /* maxVolume= */ 10);
    Bundle playerConfig =
        new RemoteMediaSession.MockPlayerConfigBuilder()
            .setDeviceInfo(deviceInfo)
            .setDeviceVolume(1)
            .build();
    session.setPlayer(playerConfig);

    int targetVolume = 3;
    CountDownLatch targetVolumeNotified = new CountDownLatch(1);
    MediaControllerCompat.Callback callback =
        new MediaControllerCompat.Callback() {
          @Override
          public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
            if (info.getCurrentVolume() == targetVolume) {
              targetVolumeNotified.countDown();
            }
          }
        };
    controllerCompat.registerCallback(callback, handler);

    session.getMockPlayer().notifyDeviceVolumeChanged(targetVolume, /* muted= */ false);

    assertThat(targetVolumeNotified.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(controllerCompat.getPlaybackInfo().getCurrentVolume()).isEqualTo(targetVolume);
  }

  private static void assertPlaybackStateCompatErrorEquals(
      PlaybackStateCompat state, PlaybackException playerError) {
    assertThat(state.getState()).isEqualTo(PlaybackStateCompat.STATE_ERROR);
    assertThat(state.getErrorCode()).isEqualTo(PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR);
    assertThat(state.getErrorMessage()).isEqualTo(playerError.getMessage());
  }
}
