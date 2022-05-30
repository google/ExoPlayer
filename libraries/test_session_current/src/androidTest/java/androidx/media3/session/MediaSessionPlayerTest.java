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

import static androidx.media3.test.session.common.CommonConstants.SUPPORT_APP_PACKAGE_NAME;
import static androidx.media3.test.session.common.TestUtils.TIMEOUT_MS;
import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.test.session.common.HandlerThreadTestRule;
import androidx.media3.test.session.common.MainLooperTestRule;
import androidx.media3.test.session.common.TestUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for the underlying {@link Player} of {@link MediaSession}. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MediaSessionPlayerTest {

  @ClassRule public static MainLooperTestRule mainLooperTestRule = new MainLooperTestRule();

  @Rule
  public final HandlerThreadTestRule threadTestRule =
      new HandlerThreadTestRule("MediaSessionPlayerTest");

  @Rule
  public final RemoteControllerTestRule remoteControllerTestRule = new RemoteControllerTestRule();

  private MediaSession session;
  private MockPlayer player;
  private RemoteMediaController controller;

  @Before
  public void setUp() throws Exception {
    player =
        new MockPlayer.Builder()
            .setApplicationLooper(threadTestRule.getHandler().getLooper())
            .setMediaItems(/* itemCount= */ 5)
            .build();
    session =
        new MediaSession.Builder(ApplicationProvider.getApplicationContext(), player)
            .setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, MediaSession.ControllerInfo controller) {
                    if (SUPPORT_APP_PACKAGE_NAME.equals(controller.getPackageName())) {
                      return MediaSession.Callback.super.onConnect(session, controller);
                    }
                    return MediaSession.ConnectionResult.reject();
                  }
                })
            .build();

    // Create a default MediaController in client app.
    controller = remoteControllerTestRule.createRemoteController(session.getToken());
  }

  @After
  public void tearDown() throws Exception {
    controller.release();
    session.release();
  }

  @Test
  public void play() throws Exception {
    controller.play();

    player.awaitMethodCalled(MockPlayer.METHOD_PLAY, TIMEOUT_MS);
  }

  @Test
  public void pause() throws Exception {
    controller.pause();

    player.awaitMethodCalled(MockPlayer.METHOD_PAUSE, TIMEOUT_MS);
  }

  @Test
  public void prepare() throws Exception {
    controller.prepare();

    player.awaitMethodCalled(MockPlayer.METHOD_PREPARE, TIMEOUT_MS);
  }

  @Test
  public void stop() throws Exception {
    controller.stop();

    player.awaitMethodCalled(MockPlayer.METHOD_STOP, TIMEOUT_MS);
  }

  @Test
  public void setPlayWhenReady() throws Exception {
    boolean testPlayWhenReady = true;

    controller.setPlayWhenReady(testPlayWhenReady);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_PLAY_WHEN_READY, TIMEOUT_MS);
    assertThat(player.playWhenReady).isEqualTo(testPlayWhenReady);
  }

  @Test
  public void seekToDefaultPosition() throws Exception {
    controller.seekToDefaultPosition();

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_DEFAULT_POSITION, TIMEOUT_MS);
  }

  @Test
  public void seekToDefaultPosition_withMediaItemIndex() throws Exception {
    int mediaItemIndex = 3;

    controller.seekToDefaultPosition(mediaItemIndex);

    player.awaitMethodCalled(
        MockPlayer.METHOD_SEEK_TO_DEFAULT_POSITION_WITH_MEDIA_ITEM_INDEX, TIMEOUT_MS);
    assertThat(player.seekMediaItemIndex).isEqualTo(mediaItemIndex);
  }

  @Test
  public void seekTo() throws Exception {
    long seekPositionMs = 12125L;
    controller.seekTo(seekPositionMs);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO, TIMEOUT_MS);
    assertThat(player.seekPositionMs).isEqualTo(seekPositionMs);
  }

  @Test
  public void seekTo_withMediaItemIndex() throws Exception {
    int mediaItemIndex = 3;
    long seekPositionMs = 12125L;

    controller.seekTo(mediaItemIndex, seekPositionMs);

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_WITH_MEDIA_ITEM_INDEX, TIMEOUT_MS);
    assertThat(player.seekMediaItemIndex).isEqualTo(mediaItemIndex);
    assertThat(player.seekPositionMs).isEqualTo(seekPositionMs);
  }

  @Test
  public void setPlaybackSpeed() throws Exception {
    float testSpeed = 1.5f;

    controller.setPlaybackSpeed(testSpeed);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_PLAYBACK_SPEED, TIMEOUT_MS);
    assertThat(player.playbackParameters.speed).isEqualTo(testSpeed);
  }

  @Test
  public void setPlaybackParameters() throws Exception {
    PlaybackParameters testPlaybackParameters =
        new PlaybackParameters(/* speed= */ 1.4f, /* pitch= */ 2.3f);

    controller.setPlaybackParameters(testPlaybackParameters);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_PLAYBACK_PARAMETERS, TIMEOUT_MS);
    assertThat(player.playbackParameters).isEqualTo(testPlaybackParameters);
  }

  @Test
  public void setMediaItem() throws Exception {
    MediaItem item = MediaTestUtils.createMediaItem("setMediaItem");
    long startPositionMs = 333L;
    boolean resetPosition = true;
    player.startPositionMs = startPositionMs;
    player.resetPosition = resetPosition;

    controller.setMediaItem(item);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEM, TIMEOUT_MS);
    assertThat(player.mediaItems).containsExactly(item);
    assertThat(player.startPositionMs).isEqualTo(startPositionMs);
    assertThat(player.resetPosition).isEqualTo(resetPosition);
  }

  @Test
  public void setMediaItem_withStartPosition() throws Exception {
    MediaItem item = MediaTestUtils.createMediaItem("setMediaItem_withStartPosition");
    long startPositionMs = 333L;
    boolean resetPosition = true;
    player.startPositionMs = startPositionMs;
    player.resetPosition = resetPosition;

    controller.setMediaItem(item);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEM, TIMEOUT_MS);
    assertThat(player.mediaItems).containsExactly(item);
    assertThat(player.startPositionMs).isEqualTo(startPositionMs);
    assertThat(player.resetPosition).isEqualTo(resetPosition);
  }

  @Test
  public void setMediaItem_withResetPosition() throws Exception {
    MediaItem item = MediaTestUtils.createMediaItem("setMediaItem_withResetPosition");
    long startPositionMs = 333L;
    boolean resetPosition = true;
    player.startPositionMs = startPositionMs;
    player.resetPosition = resetPosition;

    controller.setMediaItem(item);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEM, TIMEOUT_MS);
    assertThat(player.mediaItems).containsExactly(item);
    assertThat(player.startPositionMs).isEqualTo(startPositionMs);
    assertThat(player.resetPosition).isEqualTo(resetPosition);
  }

  @Test
  public void setMediaItems() throws Exception {
    List<MediaItem> items = MediaTestUtils.createMediaItems(/* size= */ 2);

    controller.setMediaItems(items);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS, TIMEOUT_MS);
    assertThat(player.mediaItems).isEqualTo(items);
    assertThat(player.resetPosition).isFalse();
  }

  @Test
  public void setMediaItems_withResetPosition() throws Exception {
    List<MediaItem> items = MediaTestUtils.createMediaItems(/* size= */ 2);

    controller.setMediaItems(items, /* resetPosition= */ true);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION, TIMEOUT_MS);
    assertThat(player.mediaItems).isEqualTo(items);
    assertThat(player.resetPosition).isTrue();
  }

  @Test
  public void setMediaItems_withStartMediaItemIndex() throws Exception {
    List<MediaItem> items = MediaTestUtils.createMediaItems(/* size= */ 2);
    int startMediaItemIndex = 1;
    long startPositionMs = 1234;

    controller.setMediaItems(items, startMediaItemIndex, startPositionMs);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX, TIMEOUT_MS);
    assertThat(player.mediaItems).isEqualTo(items);
    assertThat(player.startMediaItemIndex).isEqualTo(startMediaItemIndex);
    assertThat(player.startPositionMs).isEqualTo(startPositionMs);
  }

  @Test
  public void setMediaItems_withDuplicatedItems() throws Exception {
    int listSize = 4;
    List<MediaItem> list = MediaTestUtils.createMediaItems(listSize);
    list.set(2, list.get(1));

    controller.setMediaItems(list);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS, TIMEOUT_MS);
    assertThat(player.mediaItems.size()).isEqualTo(listSize);
    for (int i = 0; i < listSize; i++) {
      assertThat(player.mediaItems.get(i).mediaId).isEqualTo(list.get(i).mediaId);
    }
  }

  @Test
  public void setMediaItems_withLongPlaylist() throws Exception {
    int listSize = 5000;
    // Make client app to generate a long list, and call setMediaItems() with it.
    controller.createAndSetFakeMediaItems(listSize);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS, TIMEOUT_MS);
    assertThat(player.mediaItems).isNotNull();
    assertThat(player.mediaItems.size()).isEqualTo(listSize);
    for (int i = 0; i < listSize; i++) {
      // Each item's media ID will be same as its index.
      assertThat(player.mediaItems.get(i).mediaId).isEqualTo(TestUtils.getMediaIdInFakeTimeline(i));
    }
  }

  @Test
  public void setPlaylistMetadata() throws Exception {
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setTitle("title").build();

    controller.setPlaylistMetadata(playlistMetadata);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_PLAYLIST_METADATA, TIMEOUT_MS);
    assertThat(player.playlistMetadata).isEqualTo(playlistMetadata);
  }

  @Test
  public void addMediaItem() throws Exception {
    MediaItem mediaItem = MediaTestUtils.createMediaItem("addMediaItem");

    controller.addMediaItem(mediaItem);

    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEM, TIMEOUT_MS);
    assertThat(player.mediaItems).hasSize(6);
  }

  @Test
  public void addMediaItem_withIndex() throws Exception {
    int index = 2;
    MediaItem mediaItem = MediaTestUtils.createMediaItem("addMediaItem_withIndex");

    controller.addMediaItem(index, mediaItem);

    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEM_WITH_INDEX, TIMEOUT_MS);
    assertThat(player.index).isEqualTo(index);
    assertThat(player.mediaItems).hasSize(6);
  }

  @Test
  public void addMediaItems() throws Exception {
    int size = 2;
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(size);

    controller.addMediaItems(mediaItems);

    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEMS, TIMEOUT_MS);
    assertThat(player.mediaItems).hasSize(7);
  }

  @Test
  public void addMediaItems_withIndex() throws Exception {
    int index = 0;
    int size = 2;
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(size);

    controller.addMediaItems(index, mediaItems);

    player.awaitMethodCalled(MockPlayer.METHOD_ADD_MEDIA_ITEMS_WITH_INDEX, TIMEOUT_MS);
    assertThat(player.index).isEqualTo(index);
    assertThat(player.mediaItems).hasSize(7);
  }

  @Test
  public void removeMediaItem() throws Exception {
    int index = 3;

    controller.removeMediaItem(index);

    player.awaitMethodCalled(MockPlayer.METHOD_REMOVE_MEDIA_ITEM, TIMEOUT_MS);
    assertThat(player.index).isEqualTo(index);
  }

  @Test
  public void removeMediaItems() throws Exception {
    int fromIndex = 0;
    int toIndex = 3;

    controller.removeMediaItems(fromIndex, toIndex);

    player.awaitMethodCalled(MockPlayer.METHOD_REMOVE_MEDIA_ITEMS, TIMEOUT_MS);
    assertThat(player.fromIndex).isEqualTo(fromIndex);
    assertThat(player.toIndex).isEqualTo(toIndex);
  }

  @Test
  public void clearMediaItems() throws Exception {
    controller.clearMediaItems();

    player.awaitMethodCalled(MockPlayer.METHOD_CLEAR_MEDIA_ITEMS, TIMEOUT_MS);
  }

  @Test
  public void moveMediaItem() throws Exception {
    int index = 4;
    int newIndex = 1;

    controller.moveMediaItem(index, newIndex);

    player.awaitMethodCalled(MockPlayer.METHOD_MOVE_MEDIA_ITEM, TIMEOUT_MS);
    assertThat(player.index).isEqualTo(index);
    assertThat(player.newIndex).isEqualTo(newIndex);
  }

  @Test
  public void moveMediaItems() throws Exception {
    int fromIndex = 0;
    int toIndex = 2;
    int newIndex = 1;

    controller.moveMediaItems(fromIndex, toIndex, newIndex);

    player.awaitMethodCalled(MockPlayer.METHOD_MOVE_MEDIA_ITEMS, TIMEOUT_MS);
    assertThat(player.fromIndex).isEqualTo(fromIndex);
    assertThat(player.toIndex).isEqualTo(toIndex);
    assertThat(player.newIndex).isEqualTo(newIndex);
  }

  @Test
  public void seekToPreviousMediaItem() throws Exception {
    controller.seekToPreviousMediaItem();

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_PREVIOUS_MEDIA_ITEM, TIMEOUT_MS);
  }

  @Test
  public void seekToNextMediaItem() throws Exception {
    controller.seekToNextMediaItem();

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_NEXT_MEDIA_ITEM, TIMEOUT_MS);
  }

  @Test
  public void seekToPrevious() throws Exception {
    controller.seekToPrevious();

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_PREVIOUS, TIMEOUT_MS);
  }

  @Test
  public void seekToNext() throws Exception {
    controller.seekToNext();

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_TO_NEXT, TIMEOUT_MS);
  }

  @Test
  public void setShuffleModeEnabled() throws Exception {
    boolean testShuffleModeEnabled = true;

    controller.setShuffleModeEnabled(testShuffleModeEnabled);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_SHUFFLE_MODE, TIMEOUT_MS);
    assertThat(player.shuffleModeEnabled).isEqualTo(testShuffleModeEnabled);
  }

  @Test
  public void setRepeatMode() throws Exception {
    int testRepeatMode = Player.REPEAT_MODE_ALL;

    controller.setRepeatMode(testRepeatMode);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_REPEAT_MODE, TIMEOUT_MS);
    assertThat(player.repeatMode).isEqualTo(testRepeatMode);
  }

  @Test
  public void setVolume() throws Exception {
    float testVolume = .123f;

    controller.setVolume(testVolume);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_VOLUME, TIMEOUT_MS);
    assertThat(player.volume).isEqualTo(testVolume);
  }

  @Test
  public void setDeviceVolume() throws Exception {
    changePlaybackTypeToRemote();
    int testVolume = 12;

    controller.setDeviceVolume(testVolume);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_DEVICE_VOLUME, TIMEOUT_MS);
    assertThat(player.deviceVolume).isEqualTo(testVolume);
  }

  @Test
  public void increaseDeviceVolume() throws Exception {
    changePlaybackTypeToRemote();

    controller.increaseDeviceVolume();

    player.awaitMethodCalled(MockPlayer.METHOD_INCREASE_DEVICE_VOLUME, TIMEOUT_MS);
  }

  @Test
  public void decreaseDeviceVolume() throws Exception {
    changePlaybackTypeToRemote();

    controller.decreaseDeviceVolume();

    player.awaitMethodCalled(MockPlayer.METHOD_DECREASE_DEVICE_VOLUME, TIMEOUT_MS);
  }

  @Test
  public void setDeviceMuted() throws Exception {
    player.deviceMuted = false;

    controller.setDeviceMuted(true);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_DEVICE_MUTED, TIMEOUT_MS);
    assertThat(player.deviceMuted).isTrue();
  }

  @Test
  public void seekBack() throws Exception {
    controller.seekBack();

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_BACK, TIMEOUT_MS);
  }

  @Test
  public void seekForward() throws Exception {
    controller.seekForward();

    player.awaitMethodCalled(MockPlayer.METHOD_SEEK_FORWARD, TIMEOUT_MS);
  }

  @Test
  public void setTrackSelectionParameters() throws Exception {
    TrackSelectionParameters trackSelectionParameters =
        TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon().setMaxAudioBitrate(10).build();

    controller.setTrackSelectionParameters(trackSelectionParameters);

    player.awaitMethodCalled(MockPlayer.METHOD_SET_TRACK_SELECTION_PARAMETERS, TIMEOUT_MS);
    assertThat(player.trackSelectionParameters).isEqualTo(trackSelectionParameters);
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
