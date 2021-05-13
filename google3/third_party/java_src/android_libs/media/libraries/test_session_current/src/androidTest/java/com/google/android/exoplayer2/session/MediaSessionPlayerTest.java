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

import static com.google.android.exoplayer2.session.vct.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.LONG_TIMEOUT_MS;
import static com.google.android.exoplayer2.session.vct.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.session.vct.common.HandlerThreadTestRule;
import com.google.android.exoplayer2.session.vct.common.MainLooperTestRule;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for the underlying {@link SessionPlayer} of {@link MediaSession}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionPlayerTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule
  public final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaSessionPlayerTest");

  @Rule public final RemoteControllerTestRule controllerTestRule = new RemoteControllerTestRule();

  private MediaSession session;
  private MockPlayer player;
  private RemoteMediaController controller;

  @Before
  public void setUp() throws Exception {
    player =
        new MockPlayer.Builder()
            .setLatchCount(1)
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .build();
    session =
        new MediaSession.Builder(ApplicationProvider.getApplicationContext(), player)
            .setSessionCallback(
                new MediaSession.SessionCallback() {
                  @Nullable
                  @Override
                  public MediaSession.ConnectResult onConnect(
                      MediaSession session, MediaSession.ControllerInfo controller) {
                    if (SUPPORT_APP_PACKAGE_NAME.equals(controller.getPackageName())) {
                      return super.onConnect(session, controller);
                    }
                    return null;
                  }
                })
            .build();

    // Create a default MediaController in client app.
    controller = controllerTestRule.createRemoteController(session.getToken());
  }

  @After
  public void cleanUp() {
    if (session != null) {
      session.release();
    }
  }

  @Test
  public void play_isCalledByController() throws Exception {
    controller.play();
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.playCalled).isTrue();
  }

  @Test
  public void pause_isCalledByController() throws Exception {
    controller.pause();
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.pauseCalled).isTrue();
  }

  @Test
  public void prepare_isCalledByController() throws Exception {
    controller.prepare();
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.prepareCalled).isTrue();
  }

  @Test
  public void stop_isCalledByController() throws Exception {
    controller.stop();
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.stopCalled).isTrue();
  }

  @Test
  public void setPlayWhenReady_isCalledByController() throws Exception {
    boolean testPlayWhenReady = true;
    controller.setPlayWhenReady(testPlayWhenReady);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.setPlayWhenReadyCalled).isTrue();
    assertThat(player.playWhenReady).isEqualTo(testPlayWhenReady);
  }

  @Test
  public void seekToDefaultPosition_isCalledByController() throws Exception {
    controller.seekToDefaultPosition();
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.seekToDefaultPositionCalled).isTrue();
  }

  @Test
  public void seekToDefaultPosition_withWindowIndex_isCalledByController() throws Exception {
    int windowIndex = 33;
    controller.seekToDefaultPosition(windowIndex);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.seekToDefaultPositionWithWindowIndexCalled).isTrue();
    assertThat(player.seekWindowIndex).isEqualTo(windowIndex);
  }

  @Test
  public void seekTo_isCalledByController() throws Exception {
    long seekPositionMs = 12125L;
    controller.seekTo(seekPositionMs);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.seekToCalled).isTrue();
    assertThat(player.seekPositionMs).isEqualTo(seekPositionMs);
  }

  @Test
  public void seekTo_withWindowIndex_isCalledByController() throws Exception {
    int windowIndex = 33;
    long seekPositionMs = 12125L;
    controller.seekTo(windowIndex, seekPositionMs);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.seekToWithWindowIndexCalled).isTrue();
    assertThat(player.seekWindowIndex).isEqualTo(windowIndex);
    assertThat(player.seekPositionMs).isEqualTo(seekPositionMs);
  }

  @Test
  public void setPlaybackSpeed_isCalledByController() throws Exception {
    float testSpeed = 1.5f;
    controller.setPlaybackSpeed(testSpeed);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.playbackParameters.speed).isEqualTo(testSpeed);
  }

  @Test
  public void setPlaybackParameters_isCalledByController() throws Exception {
    PlaybackParameters testPlaybackParameters =
        new PlaybackParameters(/* speed= */ 1.4f, /* pitch= */ 2.3f);
    controller.setPlaybackParameters(testPlaybackParameters);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.setPlaybackParametersCalled).isTrue();
    assertThat(player.playbackParameters).isEqualTo(testPlaybackParameters);
  }

  @Test
  public void setPlaybackParameters_withDefault_isCalledByController() throws Exception {
    controller.setPlaybackParameters(PlaybackParameters.DEFAULT);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.setPlaybackParametersCalled).isTrue();
    assertThat(player.playbackParameters).isEqualTo(PlaybackParameters.DEFAULT);
  }

  @Test
  public void setMediaItems_withResetPosition_isCalledByController() throws Exception {
    List<MediaItem> items = MediaTestUtils.createConvergedMediaItems(/* size= */ 2);

    controller.setMediaItems(items, /* resetPosition= */ true);

    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.setMediaItemsCalled).isTrue();
    assertThat(player.mediaItems).isEqualTo(items);
    assertThat(player.resetPosition).isTrue();
  }

  @Test
  public void setMediaItems_withoutResetPosition_isCalledByController() throws Exception {
    List<MediaItem> items = MediaTestUtils.createConvergedMediaItems(/* size= */ 2);

    controller.setMediaItems(items, /* resetPosition= */ false);

    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.setMediaItemsCalled).isTrue();
    assertThat(player.mediaItems).isEqualTo(items);
    assertThat(player.resetPosition).isFalse();
  }

  @Test
  public void setMediaItems_withStartWindowAndPosition_isCalledByController() throws Exception {
    List<MediaItem> items = MediaTestUtils.createConvergedMediaItems(/* size= */ 2);
    int startWindowIndex = 1;
    long startPositionMs = 1234;

    controller.setMediaItems(items, startWindowIndex, startPositionMs);

    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.setMediaItemsCalled).isTrue();
    assertThat(player.mediaItems).isEqualTo(items);
    assertThat(player.startWindowIndex).isEqualTo(startWindowIndex);
    assertThat(player.startPositionMs).isEqualTo(startPositionMs);
  }

  @Test
  public void setMediaItems_withDuplicatedItems_isCalledByController() throws Exception {
    int listSize = 4;
    List<MediaItem> list = MediaTestUtils.createConvergedMediaItems(listSize);
    list.set(2, list.get(1));
    controller.setMediaItems(list);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.setMediaItemsCalled).isTrue();
    assertThat(player.mediaItems.size()).isEqualTo(listSize);
    for (int i = 0; i < listSize; i++) {
      assertThat(player.mediaItems.get(i).mediaId).isEqualTo(list.get(i).mediaId);
    }
  }

  @Test
  public void setMediaItems_withLongPlaylist_isCalledByController() throws Exception {
    int listSize = 5000;
    // Make client app to generate a long list, and call setMediaItems() with it.
    controller.createAndSetFakeMediaItems(listSize);
    assertThat(player.countDownLatch.await(LONG_TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(player.setMediaItemsCalled).isTrue();
    assertThat(player.mediaItems).isNotNull();
    assertThat(player.mediaItems.size()).isEqualTo(listSize);
    for (int i = 0; i < listSize; i++) {
      // Each item's media ID will be same as its index.
      assertThat(player.mediaItems.get(i).mediaId).isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
  }

  @Test
  public void setPlaylistMetadata_isCalledByController() throws Exception {
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setTitle("title").build();

    controller.setPlaylistMetadata(playlistMetadata);

    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.setPlaylistMetadataCalled).isTrue();
    assertThat(player.playlistMetadata).isEqualTo(playlistMetadata);
  }

  @Test
  public void addMediaItems_isCalledByController() throws Exception {
    int index = 1;
    int size = 2;
    List<MediaItem> mediaItems = MediaTestUtils.createConvergedMediaItems(size);

    controller.addMediaItems(index, mediaItems);

    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.addMediaItemsCalled).isTrue();
    assertThat(player.index).isEqualTo(index);
    assertThat(player.mediaItems).isEqualTo(mediaItems);
  }

  @Test
  public void removeMediaItems_isCalledByController() throws Exception {
    int fromIndex = 1;
    int toIndex = 3;

    controller.removeMediaItems(fromIndex, toIndex);

    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.removeMediaItemsCalled).isTrue();
    assertThat(player.fromIndex).isEqualTo(fromIndex);
    assertThat(player.toIndex).isEqualTo(toIndex);
  }

  @Test
  public void moveMediaItems_isCalledByController() throws Exception {
    int fromIndex = 1;
    int toIndex = 2;
    int newIndex = 3;

    controller.moveMediaItems(fromIndex, toIndex, newIndex);

    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.moveMediaItemsCalled).isTrue();
    assertThat(player.fromIndex).isEqualTo(fromIndex);
    assertThat(player.toIndex).isEqualTo(toIndex);
    assertThat(player.newIndex).isEqualTo(newIndex);
  }

  @Test
  public void skipToPreviousItem_isCalledByController() throws Exception {
    controller.previous();
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.previousCalled).isTrue();
  }

  @Test
  public void skipToNextItem_isCalledByController() throws Exception {
    controller.next();
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.nextCalled).isTrue();
  }

  @Test
  public void setShuffleModeEnabled_isCalledByController() throws Exception {
    boolean testShuffleModeEnabled = true;
    controller.setShuffleModeEnabled(testShuffleModeEnabled);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(player.setShuffleModeCalled).isTrue();
    assertThat(player.shuffleModeEnabled).isEqualTo(testShuffleModeEnabled);
  }

  @Test
  public void setRepeatMode_isCalledByController() throws Exception {
    int testRepeatMode = Player.REPEAT_MODE_ALL;
    controller.setRepeatMode(testRepeatMode);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();

    assertThat(player.setRepeatModeCalled).isTrue();
    assertThat(player.repeatMode).isEqualTo(testRepeatMode);
  }

  @Test
  public void setVolume_isCalledByController() throws Exception {
    float testVolume = .123f;
    controller.setVolume(testVolume);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.setVolumeCalled).isTrue();
    assertThat(player.volume).isEqualTo(testVolume);
  }

  @Test
  public void setDeviceVolume_isCalledByController() throws Exception {
    changePlaybackTypeToRemote();

    int testVolume = 12;
    controller.setDeviceVolume(testVolume);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.setDeviceVolumeCalled).isTrue();
    assertThat(player.deviceVolume).isEqualTo(testVolume);
  }

  @Test
  public void increaseDeviceVolume_isCalledByController() throws Exception {
    changePlaybackTypeToRemote();

    controller.increaseDeviceVolume();

    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.increaseDeviceVolumeCalled).isTrue();
  }

  @Test
  public void decreaseDeviceVolume_isCalledByController() throws Exception {
    changePlaybackTypeToRemote();

    controller.decreaseDeviceVolume();

    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.decreaseDeviceVolumeCalled).isTrue();
  }

  @Test
  public void setDeviceMuted_isCalledByController() throws Exception {
    player.deviceMuted = false;
    controller.setDeviceMuted(true);
    assertThat(player.countDownLatch.await(TIMEOUT_MS, MILLISECONDS)).isTrue();
    assertThat(player.setDeviceMutedCalled).isTrue();
    assertThat(player.deviceMuted).isTrue();
  }

  private void changePlaybackTypeToRemote() throws Exception {
    threadTestRule
        .getHandler()
        .postAndSync(
            () -> {
              player.deviceInfo =
                  new DeviceInfo(
                      DeviceInfo.PLAYBACK_TYPE_REMOTE, /* minVolume= */ 0, /* maxVolume= */ 100);
              player.notifyDeviceInfoChanged();
            });
  }
}
