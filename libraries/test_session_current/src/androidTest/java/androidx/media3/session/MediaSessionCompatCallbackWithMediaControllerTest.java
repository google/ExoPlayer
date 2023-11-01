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

import static android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS;
import static androidx.media3.test.session.common.TestUtils.VOLUME_CHANGE_TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.session.PlaybackStateCompat.RepeatMode;
import android.support.v4.media.session.PlaybackStateCompat.ShuffleMode;
import androidx.media.AudioManagerCompat;
import androidx.media.VolumeProviderCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Rating;
import androidx.media3.common.StarRating;
import androidx.media3.common.util.Util;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.MockActivity;
import androidx.media3.test.session.common.PollingCheck;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MediaSessionCompat.Callback} with {@link MediaController}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaSessionCompatCallbackWithMediaControllerTest {
  private static final String TAG = "MediaControllerTest";

  // The maximum time to wait for an operation.
  private static final long TIMEOUT_MS = 3000L;

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule public final HandlerThreadTestRule threadTestRule = new HandlerThreadTestRule(TAG);

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();

  private Context context;
  private MediaSessionCompat session;
  private MediaSessionCallback sessionCallback;
  private AudioManager audioManager;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    Intent sessionActivity = new Intent(context, MockActivity.class);
    // Create this test specific MediaSession to use our own Handler.
    PendingIntent intent =
        PendingIntent.getActivity(
            context, 0, sessionActivity, Util.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);

    sessionCallback = new MediaSessionCallback();
    session = new MediaSessionCompat(context, TAG + "Compat");
    session.setCallback(sessionCallback, threadTestRule.getHandler());
    session.setSessionActivity(intent);
    session.setActive(true);

    audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
  }

  @After
  public void cleanUp() {
    if (session != null) {
      session.release();
      session = null;
    }
  }

  private RemoteMediaController createControllerAndWaitConnection() throws Exception {
    SessionToken sessionToken =
        SessionToken.createSessionToken(context, session.getSessionToken()).get();
    return controllerTestRule.createRemoteController(sessionToken);
  }

  @Test
  public void play() throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(/* size= */ 2);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    session.setQueue(testQueue);
    session.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
    setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.play();
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onPlayCalledCount).isEqualTo(1);
  }

  @Test
  public void pause() throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(/* size= */ 2);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    session.setQueue(testQueue);
    session.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
    setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.pause();
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onPauseCalled).isEqualTo(true);
  }

  @Test
  public void prepare() throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(/* size= */ 2);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    session.setQueue(testQueue);
    session.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.prepare();

    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onPrepareCalled).isEqualTo(true);
  }

  @Test
  public void stop() throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(/* size= */ 2);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);
    session.setQueue(testQueue);
    session.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(/* count= */ 2);

    controller.prepare();
    controller.stop();

    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onPrepareCalled).isTrue();
    assertThat(sessionCallback.onStopCalled).isTrue();
  }

  @Test
  public void seekToDefaultPosition() throws Exception {
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.seekToDefaultPosition();
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSeekToCalled).isTrue();
    assertThat(sessionCallback.seekPosition).isEqualTo(0);
  }

  @Test
  public void seekToDefaultPosition_withMediaItemIndex() throws Exception {
    int testMediaItemIndex = 1;
    List<QueueItem> testQueue = MediaTestUtils.createQueueItems(/* size= */ 3);

    session.setQueue(testQueue);
    session.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
    RemoteMediaController controller = createControllerAndWaitConnection();

    sessionCallback.reset(2);

    controller.seekToDefaultPosition(testMediaItemIndex);
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSkipToQueueItemCalled).isTrue();
    assertThat(sessionCallback.queueItemId)
        .isEqualTo(testQueue.get(testMediaItemIndex).getQueueId());
    assertThat(sessionCallback.onSeekToCalled).isTrue();
    assertThat(sessionCallback.seekPosition).isEqualTo(0);
  }

  @Test
  public void seekToDefaultPosition_withFakeMediaItemIndex_seeksWithPosition() throws Exception {
    List<QueueItem> testQueue = MediaTestUtils.createQueueItems(/* size= */ 3);
    int fakeItemIndex = testQueue.size();
    MediaMetadataCompat testMetadata =
        new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "media_id")
            .build();

    session.setQueue(testQueue);
    session.setMetadata(testMetadata);
    RemoteMediaController controller = createControllerAndWaitConnection();

    sessionCallback.reset(1);

    controller.seekToDefaultPosition(fakeItemIndex);
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSkipToQueueItemCalled).isFalse();
    assertThat(sessionCallback.onSeekToCalled).isTrue();
    assertThat(sessionCallback.seekPosition).isEqualTo(0);
  }

  @Test
  public void seekTo() throws Exception {
    long testPositionMs = 12125L;

    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.seekTo(testPositionMs);
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSeekToCalled).isTrue();
    assertThat(sessionCallback.seekPosition).isEqualTo(testPositionMs);
  }

  @Test
  public void seekTo_withMediaItemIndex() throws Exception {
    int testMediaItemIndex = 1;
    long testPositionMs = 12L;

    List<QueueItem> testQueue = MediaTestUtils.createQueueItems(/* size= */ 3);

    session.setQueue(testQueue);
    session.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
    RemoteMediaController controller = createControllerAndWaitConnection();

    sessionCallback.reset(2);

    controller.seekTo(testMediaItemIndex, testPositionMs);
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSkipToQueueItemCalled).isTrue();
    assertThat(sessionCallback.queueItemId)
        .isEqualTo(testQueue.get(testMediaItemIndex).getQueueId());
    assertThat(sessionCallback.onSeekToCalled).isTrue();
    assertThat(sessionCallback.seekPosition).isEqualTo(testPositionMs);
  }

  @Test
  public void seekBack_notifiesOnRewind() throws Exception {
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.seekBack();

    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onRewindCalled).isTrue();
  }

  @Test
  public void seekForward_notifiesOnFastForward() throws Exception {
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.seekForward();

    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onFastForwardCalled).isTrue();
  }

  @Test
  public void setPlaybackSpeed_notifiesOnSetPlaybackSpeed() throws Exception {
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    float testSpeed = 2.0f;
    controller.setPlaybackSpeed(testSpeed);
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSetPlaybackSpeedCalled).isTrue();
    assertThat(sessionCallback.speed).isEqualTo(testSpeed);
  }

  @Test
  public void setPlaybackParameters_notifiesOnSetPlaybackSpeed() throws Exception {
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 1.2f);
    controller.setPlaybackParameters(playbackParameters);
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSetPlaybackSpeedCalled).isTrue();
    assertThat(sessionCallback.speed).isEqualTo(playbackParameters.speed);
  }

  @Test
  public void setPlaybackParameters_withDefault_notifiesOnSetPlaybackSpeedWithDefault()
      throws Exception {
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.setPlaybackParameters(PlaybackParameters.DEFAULT);
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSetPlaybackSpeedCalled).isTrue();
    assertThat(sessionCallback.speed).isEqualTo(PlaybackParameters.DEFAULT.speed);
  }

  @Test
  public void addMediaItems() throws Exception {
    int size = 2;
    List<MediaItem> testList = MediaTestUtils.createMediaItemsWithArtworkData(size);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);

    session.setQueue(testQueue);
    session.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
    setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(size);

    int testIndex = 1;
    controller.addMediaItems(testIndex, testList);

    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onAddQueueItemAtCalledCount).isEqualTo(size);
    for (int i = 0; i < size; i++) {
      assertThat(sessionCallback.queueIndices.get(i)).isEqualTo(testIndex + i);
      assertThat(sessionCallback.queueDescriptionListForAdd.get(i).getMediaId())
          .isEqualTo(testList.get(i).mediaId);
      assertThat(sessionCallback.queueDescriptionListForAdd.get(i).getIconBitmap()).isNotNull();
    }
  }

  @Test
  public void removeMediaItems() throws Exception {
    List<MediaItem> testList = MediaTestUtils.createMediaItems(/* size= */ 4);
    int fromIndex = 1;
    int toIndex = 3;
    int count = toIndex - fromIndex;

    session.setQueue(MediaTestUtils.convertToQueueItemsWithoutBitmap(testList));
    session.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
    setPlaybackState(PlaybackStateCompat.STATE_BUFFERING);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(count);

    controller.removeMediaItems(fromIndex, toIndex);

    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onRemoveQueueItemCalledCount).isEqualTo(count);
    for (int i = 0; i < count; i++) {
      assertThat(sessionCallback.queueDescriptionListForRemove.get(i).getMediaId())
          .isEqualTo(testList.get(fromIndex + i).mediaId);
    }
  }

  @Test
  public void seekToPreviousMediaItem() throws Exception {
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.seekToPreviousMediaItem();
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSkipToPreviousCalled).isTrue();
  }

  @Test
  public void seekToNextMediaItem() throws Exception {
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.seekToNextMediaItem();
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSkipToNextCalled).isTrue();
  }

  @Test
  public void setMediaItems_nonEmptyList_startFromFirstMediaItem() throws Exception {
    int size = 3;
    List<MediaItem> testList = MediaTestUtils.createMediaItemsWithArtworkData(size);

    session.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
    setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(size);

    controller.setMediaItems(testList);

    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onPlayFromMediaIdCalled).isTrue();
    assertThat(sessionCallback.mediaId).isEqualTo(testList.get(0).mediaId);
    for (int i = 0; i < size - 1; i++) {
      assertThat(sessionCallback.queueIndices.get(i)).isEqualTo(i);
      assertThat(sessionCallback.queueDescriptionListForAdd.get(i).getMediaId())
          .isEqualTo(testList.get(i + 1).mediaId);
      assertThat(sessionCallback.queueDescriptionListForAdd.get(i).getIconBitmap()).isNotNull();
    }
  }

  @Test
  public void setMediaItems_nonEmptyList_startFromNonFirstMediaItem() throws Exception {
    int size = 5;
    List<MediaItem> testList = MediaTestUtils.createMediaItemsWithArtworkData(size);

    session.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
    setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(size);
    int testStartIndex = 2;

    controller.setMediaItems(testList, testStartIndex, /* startPositionMs= */ C.TIME_UNSET);

    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onPlayFromMediaIdCalled).isTrue();
    assertThat(sessionCallback.mediaId).isEqualTo(testList.get(testStartIndex).mediaId);
    for (int i = 0; i < size - 1; i++) {
      assertThat(sessionCallback.queueIndices.get(i)).isEqualTo(i);
      int adjustedIndex = (i < testStartIndex) ? i : i + 1;
      assertThat(sessionCallback.queueDescriptionListForAdd.get(i).getMediaId())
          .isEqualTo(testList.get(adjustedIndex).mediaId);
      assertThat(sessionCallback.queueDescriptionListForAdd.get(i).getIconBitmap()).isNotNull();
    }
  }

  @Test
  public void setMediaItems_emptyList() throws Exception {
    int size = 3;
    List<MediaItem> testList = MediaTestUtils.createMediaItems(size);
    List<QueueItem> testQueue = MediaTestUtils.convertToQueueItemsWithoutBitmap(testList);

    session.setQueue(testQueue);
    session.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
    setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(size);

    controller.setMediaItems(ImmutableList.of());

    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    for (int i = 0; i < size; i++) {
      assertThat(sessionCallback.queueDescriptionListForRemove.get(i).getMediaId())
          .isEqualTo(testList.get(i).mediaId);
    }
  }

  @Test
  public void setShuffleMode() throws Exception {
    session.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.setShuffleModeEnabled(true);
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSetShuffleModeCalled).isTrue();
    assertThat(sessionCallback.shuffleMode).isEqualTo(PlaybackStateCompat.SHUFFLE_MODE_ALL);
  }

  @Test
  public void setRepeatMode() throws Exception {
    int testRepeatMode = Player.REPEAT_MODE_ALL;

    session.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.setRepeatMode(testRepeatMode);
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSetRepeatModeCalled).isTrue();
    assertThat(sessionCallback.repeatMode).isEqualTo(testRepeatMode);
  }

  @Test
  public void setDeviceVolume_forRemotePlayback_callsSetVolumeTo() throws Exception {
    int maxVolume = 100;
    int currentVolume = 23;
    int volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
    TestVolumeProvider volumeProvider =
        new TestVolumeProvider(volumeControlType, maxVolume, currentVolume);
    session.setPlaybackToRemote(volumeProvider);
    RemoteMediaController controller = createControllerAndWaitConnection();

    int targetVolume = 50;
    controller.setDeviceVolume(targetVolume);
    assertThat(volumeProvider.latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(volumeProvider.setVolumeToCalled).isTrue();
    assertThat(volumeProvider.volume).isEqualTo(targetVolume);
  }

  @Test
  public void increaseDeviceVolume_forRemotePlayback_callsAdjustVolumeWithDirectionRaise()
      throws Exception {
    int maxVolume = 100;
    int currentVolume = 23;
    int volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
    TestVolumeProvider volumeProvider =
        new TestVolumeProvider(volumeControlType, maxVolume, currentVolume);
    session.setPlaybackToRemote(volumeProvider);
    RemoteMediaController controller = createControllerAndWaitConnection();

    controller.increaseDeviceVolume();
    assertThat(volumeProvider.latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(volumeProvider.adjustVolumeCalled).isTrue();
    assertThat(volumeProvider.direction).isEqualTo(AudioManager.ADJUST_RAISE);
  }

  @Test
  public void decreaseDeviceVolume_forRemotePlayback_callsAdjustVolumeWithDirectionLower()
      throws Exception {
    int maxVolume = 100;
    int currentVolume = 23;
    int volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
    int volumeFlags = C.VOLUME_FLAG_SHOW_UI;
    TestVolumeProvider volumeProvider =
        new TestVolumeProvider(volumeControlType, maxVolume, currentVolume);
    session.setPlaybackToRemote(volumeProvider);
    RemoteMediaController controller = createControllerAndWaitConnection();

    controller.decreaseDeviceVolume(volumeFlags);
    assertThat(volumeProvider.latch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(volumeProvider.adjustVolumeCalled).isTrue();
    assertThat(volumeProvider.direction).isEqualTo(AudioManager.ADJUST_LOWER);
  }

  @Test
  public void setDeviceVolume_forLocalPlayback_setsStreamVolume() throws Exception {
    if (Util.SDK_INT >= 21 && audioManager.isVolumeFixed()) {
      // This test is not eligible for this device.
      return;
    }

    // STREAM_ALARM in order not to consider 'Do Not Disturb' or 'Volume limit'.
    int stream = AudioManager.STREAM_ALARM;
    int maxVolume = AudioManagerCompat.getStreamMaxVolume(audioManager, stream);
    int minVolume = AudioManagerCompat.getStreamMinVolume(audioManager, stream);
    if (maxVolume <= minVolume) {
      return;
    }
    int volumeFlags = C.VOLUME_FLAG_SHOW_UI | C.VOLUME_FLAG_VIBRATE;

    session.setPlaybackToLocal(stream);
    RemoteMediaController controller = createControllerAndWaitConnection();
    int originalVolume = audioManager.getStreamVolume(stream);
    int targetVolume = originalVolume == minVolume ? originalVolume + 1 : originalVolume - 1;

    controller.setDeviceVolume(targetVolume, volumeFlags);
    PollingCheck.waitFor(
        VOLUME_CHANGE_TIMEOUT_MS, () -> targetVolume == audioManager.getStreamVolume(stream));

    // Set back to original volume.
    audioManager.setStreamVolume(stream, originalVolume, /* flags= */ 0);
  }

  @Test
  public void increaseDeviceVolume_forLocalPlayback_increasesStreamVolume() throws Exception {
    if (Util.SDK_INT >= 21 && audioManager.isVolumeFixed()) {
      // This test is not eligible for this device.
      return;
    }

    // STREAM_ALARM in order not to consider 'Do Not Disturb' or 'Volume limit'.
    int stream = AudioManager.STREAM_ALARM;
    int maxVolume = AudioManagerCompat.getStreamMaxVolume(audioManager, stream);
    int minVolume = AudioManagerCompat.getStreamMinVolume(audioManager, stream);
    if (maxVolume <= minVolume) {
      return;
    }
    int volumeFlags = C.VOLUME_FLAG_SHOW_UI | C.VOLUME_FLAG_VIBRATE;

    session.setPlaybackToLocal(stream);
    RemoteMediaController controller = createControllerAndWaitConnection();
    int originalVolume = audioManager.getStreamVolume(stream);
    audioManager.setStreamVolume(stream, minVolume, /* flags= */ 0);
    int targetVolume = minVolume + 1;

    controller.increaseDeviceVolume(volumeFlags);
    PollingCheck.waitFor(
        VOLUME_CHANGE_TIMEOUT_MS, () -> targetVolume == audioManager.getStreamVolume(stream));

    // Set back to original volume.
    audioManager.setStreamVolume(stream, originalVolume, /* flags= */ 0);
  }

  @Test
  public void decreaseDeviceVolume_forLocalPlayback_decreasesStreamVolume() throws Exception {
    if (Util.SDK_INT >= 21 && audioManager.isVolumeFixed()) {
      // This test is not eligible for this device.
      return;
    }

    // STREAM_ALARM in order not to consider 'Do Not Disturb' or 'Volume limit'.
    int stream = AudioManager.STREAM_ALARM;
    int maxVolume = AudioManagerCompat.getStreamMaxVolume(audioManager, stream);
    int minVolume = AudioManagerCompat.getStreamMinVolume(audioManager, stream);
    if (maxVolume <= minVolume) {
      return;
    }
    int volumeFlags = C.VOLUME_FLAG_SHOW_UI | C.VOLUME_FLAG_VIBRATE;

    session.setPlaybackToLocal(stream);
    RemoteMediaController controller = createControllerAndWaitConnection();
    int originalVolume = audioManager.getStreamVolume(stream);
    audioManager.setStreamVolume(stream, maxVolume, /* flags= */ 0);
    int targetVolume = maxVolume - 1;

    controller.decreaseDeviceVolume(volumeFlags);
    PollingCheck.waitFor(
        VOLUME_CHANGE_TIMEOUT_MS, () -> targetVolume == audioManager.getStreamVolume(stream));

    // Set back to original volume.
    audioManager.setStreamVolume(stream, originalVolume, /* flags= */ 0);
  }

  @Test
  @SdkSuppress(minSdkVersion = 23)
  public void setDeviceMuted_mute_forLocalPlayback_mutesStreamVolume() throws Exception {
    if (audioManager.isVolumeFixed()) {
      // This test is not eligible for this device.
      return;
    }

    int stream = AudioManager.STREAM_MUSIC;
    int maxVolume = AudioManagerCompat.getStreamMaxVolume(audioManager, stream);
    int minVolume = AudioManagerCompat.getStreamMinVolume(audioManager, stream);
    if (maxVolume <= minVolume) {
      return;
    }
    int volumeFlags = C.VOLUME_FLAG_VIBRATE;

    session.setPlaybackToLocal(stream);
    RemoteMediaController controller = createControllerAndWaitConnection();
    boolean wasMuted = audioManager.isStreamMute(stream);
    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, /* flags= */ 0);

    controller.setDeviceMuted(true, volumeFlags);
    PollingCheck.waitFor(VOLUME_CHANGE_TIMEOUT_MS, () -> audioManager.isStreamMute(stream));

    // Set back to original mute state.
    audioManager.adjustStreamVolume(
        stream, wasMuted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, /* flags= */ 0);
  }

  @Test
  @SdkSuppress(minSdkVersion = 23)
  public void setDeviceMuted_unmute_forLocalPlayback_unmutesStreamVolume() throws Exception {
    if (audioManager.isVolumeFixed()) {
      // This test is not eligible for this device.
      return;
    }

    int stream = AudioManager.STREAM_MUSIC;
    int maxVolume = AudioManagerCompat.getStreamMaxVolume(audioManager, stream);
    int minVolume = AudioManagerCompat.getStreamMinVolume(audioManager, stream);
    if (maxVolume <= minVolume) {
      return;
    }
    session.setPlaybackToLocal(stream);
    RemoteMediaController controller = createControllerAndWaitConnection();
    boolean wasMuted = audioManager.isStreamMute(stream);
    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, /* flags= */ 0);

    controller.setDeviceMuted(false);
    PollingCheck.waitFor(VOLUME_CHANGE_TIMEOUT_MS, () -> !audioManager.isStreamMute(stream));

    // Set back to original mute state.
    audioManager.adjustStreamVolume(
        stream, wasMuted ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, /* flags= */ 0);
  }

  @Test
  public void sendCustomCommand() throws Exception {
    String command = "test_custom_command";
    Bundle testArgs = new Bundle();
    testArgs.putString("args", "test_args");
    SessionCommand testCommand = new SessionCommand(command, /* extras= */ Bundle.EMPTY);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    SessionResult result = controller.sendCustomCommand(testCommand, testArgs);
    assertThat(result.resultCode).isEqualTo(SessionResult.RESULT_SUCCESS);
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onCommandCalled).isTrue();
    assertThat(sessionCallback.command).isEqualTo(command);
    assertThat(TestUtils.equals(testArgs, sessionCallback.extras)).isTrue();
  }

  @Test
  public void setRatingWithMediaId() throws Exception {
    float ratingValue = 3.5f;
    Rating rating = new StarRating(5, ratingValue);
    String mediaId = "media_id";
    MediaMetadataCompat metadata =
        new MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId)
            .build();
    session.setMetadata(metadata);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.setRating(mediaId, rating);
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSetRatingCalled).isTrue();
    assertThat(LegacyConversions.convertToRating(sessionCallback.rating)).isEqualTo(rating);
  }

  @Test
  public void setRatingWithoutMediaId() throws Exception {
    float ratingValue = 3.5f;
    Rating rating = new StarRating(5, ratingValue);
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.setRating(rating);
    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSetRatingCalled).isTrue();
    assertThat(LegacyConversions.convertToRating(sessionCallback.rating)).isEqualTo(rating);
  }

  @Test
  public void seekToNext_callsOnSkipToNext() throws Exception {
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.seekToNext();

    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSkipToNextCalled).isTrue();
  }

  @Test
  public void seekToPrevious_callsOnSkipToPrevious() throws Exception {
    RemoteMediaController controller = createControllerAndWaitConnection();
    sessionCallback.reset(1);

    controller.seekToPrevious();

    assertThat(sessionCallback.await(TIMEOUT_MS)).isTrue();
    assertThat(sessionCallback.onSkipToPreviousCalled).isTrue();
  }

  private void setPlaybackState(int state) {
    long allActions =
        PlaybackStateCompat.ACTION_PLAY
            | PlaybackStateCompat.ACTION_PAUSE
            | PlaybackStateCompat.ACTION_PLAY_PAUSE
            | PlaybackStateCompat.ACTION_STOP
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            | PlaybackStateCompat.ACTION_FAST_FORWARD
            | PlaybackStateCompat.ACTION_REWIND;
    PlaybackStateCompat playbackState =
        new PlaybackStateCompat.Builder().setActions(allActions).setState(state, 0L, 0.0f).build();
    session.setPlaybackState(playbackState);
  }

  class TestVolumeProvider extends VolumeProviderCompat {
    CountDownLatch latch = new CountDownLatch(1);
    boolean setVolumeToCalled;
    boolean adjustVolumeCalled;
    int volume;
    int direction;

    TestVolumeProvider(int controlType, int maxVolume, int currentVolume) {
      super(controlType, maxVolume, currentVolume);
    }

    @Override
    public void onSetVolumeTo(int volume) {
      setVolumeToCalled = true;
      this.volume = volume;
      latch.countDown();
    }

    @Override
    public void onAdjustVolume(int direction) {
      adjustVolumeCalled = true;
      this.direction = direction;
      latch.countDown();
    }
  }

  private class MediaSessionCallback extends MediaSessionCompat.Callback {
    public CountDownLatch latch = new CountDownLatch(1);
    public long seekPosition;
    public float speed;
    public long queueItemId;
    public RatingCompat rating;
    public String mediaId;
    public String query;
    public Uri uri;
    public String action;
    public String command;
    public Bundle extras;
    public ResultReceiver commandCallback;
    public boolean captioningEnabled;
    @RepeatMode public int repeatMode;
    @ShuffleMode public int shuffleMode;
    public final List<Integer> queueIndices = new ArrayList<>();
    public final List<MediaDescriptionCompat> queueDescriptionListForAdd = new ArrayList<>();
    public final List<MediaDescriptionCompat> queueDescriptionListForRemove = new ArrayList<>();

    public int onPlayCalledCount;
    public boolean onPauseCalled;
    public boolean onStopCalled;
    public boolean onSkipToPreviousCalled;
    public boolean onSkipToNextCalled;
    public boolean onSeekToCalled;
    public boolean onFastForwardCalled;
    public boolean onRewindCalled;
    public boolean onSetPlaybackSpeedCalled;
    public boolean onSkipToQueueItemCalled;
    public boolean onSetRatingCalled;
    public boolean onPlayFromMediaIdCalled;
    public boolean onPlayFromSearchCalled;
    public boolean onPlayFromUriCalled;
    public boolean onCustomActionCalled;
    public boolean onCommandCalled;
    public boolean onPrepareCalled;
    public boolean onPrepareFromMediaIdCalled;
    public boolean onPrepareFromSearchCalled;
    public boolean onPrepareFromUriCalled;
    public boolean onSetCaptioningEnabledCalled;
    public boolean onSetRepeatModeCalled;
    public boolean onSetShuffleModeCalled;
    public boolean onAddQueueItemCalled;
    public int onAddQueueItemAtCalledCount;
    public int onRemoveQueueItemCalledCount;

    public void reset(int count) {
      latch = new CountDownLatch(count);
      seekPosition = -1;
      speed = -1.0f;
      queueItemId = -1;
      rating = null;
      mediaId = null;
      query = null;
      uri = null;
      action = null;
      extras = null;
      command = null;
      commandCallback = null;
      captioningEnabled = false;
      repeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;
      shuffleMode = PlaybackStateCompat.SHUFFLE_MODE_NONE;
      queueIndices.clear();
      queueDescriptionListForAdd.clear();
      queueDescriptionListForRemove.clear();

      onPlayCalledCount = 0;
      onPauseCalled = false;
      onStopCalled = false;
      onSkipToPreviousCalled = false;
      onSkipToNextCalled = false;
      onSkipToQueueItemCalled = false;
      onSeekToCalled = false;
      onFastForwardCalled = false;
      onRewindCalled = false;
      onSetPlaybackSpeedCalled = false;
      onSetRatingCalled = false;
      onPlayFromMediaIdCalled = false;
      onPlayFromSearchCalled = false;
      onPlayFromUriCalled = false;
      onCustomActionCalled = false;
      onCommandCalled = false;
      onPrepareCalled = false;
      onPrepareFromMediaIdCalled = false;
      onPrepareFromSearchCalled = false;
      onPrepareFromUriCalled = false;
      onSetCaptioningEnabledCalled = false;
      onSetRepeatModeCalled = false;
      onSetShuffleModeCalled = false;
      onAddQueueItemCalled = false;
      onAddQueueItemAtCalledCount = 0;
      onRemoveQueueItemCalledCount = 0;
    }

    public boolean await(long timeoutMs) {
      try {
        return latch.await(timeoutMs, MILLISECONDS);
      } catch (InterruptedException e) {
        return false;
      }
    }

    @Override
    public void onPlay() {
      onPlayCalledCount++;
      setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
      latch.countDown();
    }

    @Override
    public void onPause() {
      onPauseCalled = true;
      setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
      latch.countDown();
    }

    @Override
    public void onStop() {
      onStopCalled = true;
      setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
      latch.countDown();
    }

    @Override
    public void onSkipToPrevious() {
      onSkipToPreviousCalled = true;
      latch.countDown();
    }

    @Override
    public void onSkipToNext() {
      onSkipToNextCalled = true;
      latch.countDown();
    }

    @Override
    public void onSeekTo(long pos) {
      onSeekToCalled = true;
      seekPosition = pos;
      latch.countDown();
    }

    @Override
    public void onFastForward() {
      onFastForwardCalled = true;
      latch.countDown();
    }

    @Override
    public void onRewind() {
      onRewindCalled = true;
      latch.countDown();
    }

    @Override
    public void onSetPlaybackSpeed(float speed) {
      onSetPlaybackSpeedCalled = true;
      this.speed = speed;
      latch.countDown();
    }

    @Override
    public void onSetRating(RatingCompat rating) {
      onSetRatingCalled = true;
      this.rating = rating;
      latch.countDown();
    }

    @Override
    public void onPlayFromMediaId(String mediaId, Bundle extras) {
      onPlayFromMediaIdCalled = true;
      this.mediaId = mediaId;
      this.extras = extras;
      latch.countDown();
    }

    @Override
    public void onPlayFromSearch(String query, Bundle extras) {
      onPlayFromSearchCalled = true;
      this.query = query;
      this.extras = extras;
      latch.countDown();
    }

    @Override
    public void onPlayFromUri(Uri uri, Bundle extras) {
      onPlayFromUriCalled = true;
      this.uri = uri;
      this.extras = extras;
      latch.countDown();
    }

    @Override
    public void onCustomAction(String action, Bundle extras) {
      onCustomActionCalled = true;
      this.action = action;
      this.extras = extras;
      latch.countDown();
    }

    @Override
    public void onSkipToQueueItem(long id) {
      onSkipToQueueItemCalled = true;
      queueItemId = id;
      latch.countDown();
    }

    @Override
    public void onCommand(String command, Bundle extras, ResultReceiver cb) {
      onCommandCalled = true;
      this.command = command;
      this.extras = extras;
      commandCallback = cb;
      cb.send(SessionResult.RESULT_SUCCESS, /* resultData= */ null);
      latch.countDown();
    }

    @Override
    public void onPrepare() {
      onPrepareCalled = true;
      latch.countDown();
    }

    @Override
    public void onPrepareFromMediaId(String mediaId, Bundle extras) {
      onPrepareFromMediaIdCalled = true;
      this.mediaId = mediaId;
      this.extras = extras;
      latch.countDown();
    }

    @Override
    public void onPrepareFromSearch(String query, Bundle extras) {
      onPrepareFromSearchCalled = true;
      this.query = query;
      this.extras = extras;
      latch.countDown();
    }

    @Override
    public void onPrepareFromUri(Uri uri, Bundle extras) {
      onPrepareFromUriCalled = true;
      this.uri = uri;
      this.extras = extras;
      latch.countDown();
    }

    @Override
    public void onSetRepeatMode(@RepeatMode int repeatMode) {
      onSetRepeatModeCalled = true;
      this.repeatMode = repeatMode;
      session.setRepeatMode(repeatMode);
      latch.countDown();
    }

    @Override
    public void onAddQueueItem(MediaDescriptionCompat description) {
      onAddQueueItemCalled = true;
      queueDescriptionListForAdd.add(description);
      latch.countDown();
    }

    @Override
    public void onAddQueueItem(MediaDescriptionCompat description, int index) {
      onAddQueueItemAtCalledCount++;
      queueIndices.add(index);
      queueDescriptionListForAdd.add(description);
      latch.countDown();
    }

    @Override
    public void onRemoveQueueItem(MediaDescriptionCompat description) {
      onRemoveQueueItemCalledCount++;
      queueDescriptionListForRemove.add(description);
      latch.countDown();
    }

    @Override
    public void onSetCaptioningEnabled(boolean enabled) {
      onSetCaptioningEnabledCalled = true;
      captioningEnabled = enabled;
      latch.countDown();
    }

    @Override
    public void onSetShuffleMode(@ShuffleMode int shuffleMode) {
      onSetShuffleModeCalled = true;
      this.shuffleMode = shuffleMode;
      session.setShuffleMode(shuffleMode);
      latch.countDown();
    }
  }
}
