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

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link MockPlayer}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MockPlayerTest {

  private MockPlayer player;

  @Before
  public void setUp() {
    player = new MockPlayer.Builder().build();
  }

  @Test
  public void play() {
    player.play();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PLAY)).isTrue();
  }

  @Test
  public void pause() {
    player.pause();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PAUSE)).isTrue();
  }

  @Test
  public void prepare() {
    player.prepare();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_PREPARE)).isTrue();
  }

  @Test
  public void stop() {
    player.stop();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_STOP)).isTrue();
  }

  @Test
  public void release() {
    player.release();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_RELEASE)).isTrue();
  }

  @Test
  public void setPlayWhenReady() {
    boolean testPlayWhenReady = false;

    player.setPlayWhenReady(testPlayWhenReady);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_PLAY_WHEN_READY)).isTrue();
  }

  @Test
  public void seekTo() {
    long pos = 1004L;

    player.seekTo(pos);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SEEK_TO)).isTrue();
    assertThat(player.seekPositionMs).isEqualTo(pos);
  }

  @Test
  public void seekBack() {
    player.seekBack();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SEEK_BACK)).isTrue();
  }

  @Test
  public void seekForward() {
    player.seekForward();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SEEK_FORWARD)).isTrue();
  }

  @Test
  public void setPlaybackParameters() {
    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 1.5f);

    player.setPlaybackParameters(playbackParameters);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_PLAYBACK_PARAMETERS)).isTrue();
    assertThat(player.playbackParameters).isEqualTo(playbackParameters);
  }

  @Test
  public void setPlaybackSpeed() {
    float speed = 1.5f;

    player.setPlaybackSpeed(speed);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_PLAYBACK_SPEED)).isTrue();
    assertThat(player.playbackParameters.speed).isEqualTo(speed);
  }

  @Test
  public void setMediaItem() {
    MediaItem mediaItem = MediaTestUtils.createMediaItem("setMediaItem");

    player.setMediaItem(mediaItem);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEM)).isTrue();
    assertThat(player.mediaItems).containsExactly(mediaItem);
  }

  @Test
  public void setMediaItem_withStartPosition() {
    MediaItem mediaItem = MediaTestUtils.createMediaItem("setMediaItem");
    long startPositionMs = 321L;

    player.setMediaItem(mediaItem, startPositionMs);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEM_WITH_START_POSITION))
        .isTrue();
    assertThat(player.startPositionMs).isEqualTo(startPositionMs);
    assertThat(player.mediaItems).containsExactly(mediaItem);
  }

  @Test
  public void setMediaItem_withResetPosition() {
    MediaItem mediaItem = MediaTestUtils.createMediaItem("setMediaItem");
    boolean resetPosition = true;

    player.setMediaItem(mediaItem, resetPosition);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEM_WITH_RESET_POSITION))
        .isTrue();
    assertThat(player.resetPosition).isEqualTo(resetPosition);
    assertThat(player.mediaItems).containsExactly(mediaItem);
  }

  @Test
  public void setMediaItems() {
    List<MediaItem> list = MediaTestUtils.createMediaItems(/* size= */ 2);

    player.setMediaItems(list);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS)).isTrue();
    assertThat(player.mediaItems).containsExactlyElementsIn(list).inOrder();
  }

  @Test
  public void setMediaItems_withResetPosition() {
    List<MediaItem> list = MediaTestUtils.createMediaItems(/* size= */ 2);
    boolean resetPosition = true;

    player.setMediaItems(list, resetPosition);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_RESET_POSITION))
        .isTrue();
    assertThat(player.resetPosition).isEqualTo(resetPosition);
    assertThat(player.mediaItems).containsExactlyElementsIn(list).inOrder();
  }

  @Test
  public void setMediaItems_withStartWindowIndex() {
    List<MediaItem> list = MediaTestUtils.createMediaItems(/* size= */ 2);
    int startWindowIndex = 3;
    long startPositionMs = 132L;

    player.setMediaItems(list, startWindowIndex, startPositionMs);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS_WITH_START_INDEX))
        .isTrue();
    assertThat(player.startMediaItemIndex).isEqualTo(startWindowIndex);
    assertThat(player.startPositionMs).isEqualTo(startPositionMs);
    assertThat(player.mediaItems).containsExactlyElementsIn(list).inOrder();
  }

  @Test
  public void setMediaItems_withDuplicatedItems() {
    List<MediaItem> list = MediaTestUtils.createMediaItems(/* size= */ 4);
    list.set(2, list.get(1));

    player.setMediaItems(list);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_MEDIA_ITEMS)).isTrue();
    assertThat(player.mediaItems).containsExactlyElementsIn(list).inOrder();
  }

  @Test
  public void setPlaylistMetadata() {
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setTitle("title").build();

    player.setPlaylistMetadata(playlistMetadata);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_PLAYLIST_METADATA)).isTrue();
    assertThat(player.playlistMetadata).isSameInstanceAs(playlistMetadata);
  }

  @Test
  public void addMediaItem() {
    MediaItem existingItem = MediaTestUtils.createMediaItem("existing");
    MediaItem mediaItem = MediaTestUtils.createMediaItem("item");
    player.setMediaItem(existingItem);

    player.addMediaItem(mediaItem);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_ADD_MEDIA_ITEM)).isTrue();
    assertThat(player.mediaItems).containsExactly(existingItem, mediaItem).inOrder();
  }

  @Test
  public void addMediaItem_withIndex() {
    int index = 1;
    List<MediaItem> existingItems = MediaTestUtils.createMediaItems(/* size= */ 2);
    MediaItem mediaItem = MediaTestUtils.createMediaItem("item");
    player.setMediaItems(existingItems);

    player.addMediaItem(index, mediaItem);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_ADD_MEDIA_ITEM_WITH_INDEX)).isTrue();
    assertThat(player.index).isEqualTo(index);
    assertThat(player.mediaItems)
        .containsExactly(existingItems.get(0), mediaItem, existingItems.get(1))
        .inOrder();
  }

  @Test
  public void addMediaItems() {
    int size = 4;
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(size);
    player.setMediaItems(mediaItems.subList(/* fromIndex= */ 0, /* toIndex= */ 2));

    player.addMediaItems(mediaItems.subList(/* fromIndex= */ 2, /* toIndex= */ 4));

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_ADD_MEDIA_ITEMS)).isTrue();
    assertThat(player.mediaItems).containsExactlyElementsIn(mediaItems).inOrder();
  }

  @Test
  public void addMediaItems_withIndex() {
    int index = 1;
    int size = 4;
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(size);
    player.setMediaItems(mediaItems.subList(/* fromIndex= */ 0, /* toIndex= */ 2));

    player.addMediaItems(index, mediaItems.subList(/* fromIndex= */ 2, /* toIndex= */ 4));

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_ADD_MEDIA_ITEMS_WITH_INDEX)).isTrue();
    assertThat(player.index).isEqualTo(index);
    assertThat(player.mediaItems)
        .containsExactly(mediaItems.get(0), mediaItems.get(2), mediaItems.get(3), mediaItems.get(1))
        .inOrder();
  }

  @Test
  public void removeMediaItem() {
    int index = 3;
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 5);
    player.addMediaItems(mediaItems);

    player.removeMediaItem(index);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_REMOVE_MEDIA_ITEM)).isTrue();
    assertThat(player.index).isEqualTo(index);
    assertThat(player.mediaItems)
        .containsExactly(mediaItems.get(0), mediaItems.get(1), mediaItems.get(2), mediaItems.get(4))
        .inOrder();
  }

  @Test
  public void removeMediaItems() {
    int fromIndex = 1;
    int toIndex = 3;
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 5);
    player.addMediaItems(mediaItems);

    player.removeMediaItems(fromIndex, toIndex);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_REMOVE_MEDIA_ITEMS)).isTrue();
    assertThat(player.fromIndex).isEqualTo(fromIndex);
    assertThat(player.toIndex).isEqualTo(toIndex);
    assertThat(player.mediaItems)
        .containsExactly(mediaItems.get(0), mediaItems.get(3), mediaItems.get(4))
        .inOrder();
  }

  @Test
  public void clearMediaItems() {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 5);
    player.addMediaItems(mediaItems);

    player.clearMediaItems();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_CLEAR_MEDIA_ITEMS)).isTrue();
    assertThat(player.mediaItems).isEmpty();
  }

  @Test
  public void moveMediaItem() {
    int index = 2;
    int newIndex = 3;
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 5);
    player.addMediaItems(mediaItems);

    player.moveMediaItem(index, newIndex);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_MOVE_MEDIA_ITEM)).isTrue();
    assertThat(player.index).isEqualTo(index);
    assertThat(player.newIndex).isEqualTo(newIndex);
    assertThat(player.mediaItems)
        .containsExactly(
            mediaItems.get(0),
            mediaItems.get(1),
            mediaItems.get(3),
            mediaItems.get(2),
            mediaItems.get(4))
        .inOrder();
  }

  @Test
  public void moveMediaItems() {
    int fromIndex = 1;
    int toIndex = 3;
    int newIndex = 3;
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 5);
    player.addMediaItems(mediaItems);

    player.moveMediaItems(fromIndex, toIndex, newIndex);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_MOVE_MEDIA_ITEMS)).isTrue();
    assertThat(player.fromIndex).isEqualTo(fromIndex);
    assertThat(player.toIndex).isEqualTo(toIndex);
    assertThat(player.newIndex).isEqualTo(newIndex);
    assertThat(player.mediaItems)
        .containsExactly(
            mediaItems.get(0),
            mediaItems.get(3),
            mediaItems.get(4),
            mediaItems.get(1),
            mediaItems.get(2))
        .inOrder();
  }

  @Test
  public void replaceMediaItem() {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);
    player.addMediaItems(mediaItems);
    MediaItem mediaItem = MediaTestUtils.createMediaItem("item");

    player.replaceMediaItem(/* index= */ 1, mediaItem);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_REPLACE_MEDIA_ITEM)).isTrue();
    assertThat(player.index).isEqualTo(1);
    assertThat(player.mediaItems).containsExactly(mediaItems.get(0), mediaItem, mediaItems.get(2));
  }

  @Test
  public void replaceMediaItems() {
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(/* size= */ 4);
    player.addMediaItems(mediaItems);
    List<MediaItem> newMediaItems = MediaTestUtils.createMediaItems(/* size= */ 3);

    player.replaceMediaItems(/* fromIndex= */ 1, /* toIndex= */ 3, newMediaItems);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_REPLACE_MEDIA_ITEMS)).isTrue();
    assertThat(player.fromIndex).isEqualTo(1);
    assertThat(player.toIndex).isEqualTo(3);
    assertThat(player.mediaItems)
        .containsExactly(
            mediaItems.get(0),
            newMediaItems.get(0),
            newMediaItems.get(1),
            newMediaItems.get(2),
            mediaItems.get(3));
  }

  @Test
  public void seekToPreviousMediaItem() {
    player.seekToPreviousMediaItem();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SEEK_TO_PREVIOUS_MEDIA_ITEM)).isTrue();
  }

  @Test
  public void seekToNextMediaItem() {
    player.seekToNextMediaItem();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SEEK_TO_NEXT_MEDIA_ITEM)).isTrue();
  }

  @Test
  public void seekToPrevious() {
    player.seekToPrevious();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SEEK_TO_PREVIOUS)).isTrue();
  }

  @Test
  public void seekToNext() {
    player.seekToNext();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SEEK_TO_NEXT)).isTrue();
  }

  @Test
  public void setShuffleModeEnabled() {
    boolean testShuffleModeEnabled = true;

    player.setShuffleModeEnabled(testShuffleModeEnabled);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_SHUFFLE_MODE)).isTrue();
    assertThat(player.shuffleModeEnabled).isEqualTo(testShuffleModeEnabled);
  }

  @Test
  public void setRepeatMode() {
    int testRepeatMode = Player.REPEAT_MODE_ALL;

    player.setRepeatMode(testRepeatMode);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_REPEAT_MODE)).isTrue();
    assertThat(player.repeatMode).isEqualTo(testRepeatMode);
  }

  @Test
  public void setVolume() {
    float testVolume = .123f;

    player.setVolume(testVolume);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_VOLUME)).isTrue();
    assertThat(player.volume).isEqualTo(testVolume);
  }

  @Test
  public void setDeviceVolume() {
    int testVolume = 12;

    player.setDeviceVolume(testVolume);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_DEVICE_VOLUME)).isTrue();
    assertThat(player.deviceVolume).isEqualTo(testVolume);
  }

  @Test
  public void setDeviceVolumeWithFlags() {
    int testVolume = 12;
    int testVolumeFlags = C.VOLUME_FLAG_VIBRATE;

    player.setDeviceVolume(testVolume, testVolumeFlags);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_DEVICE_VOLUME_WITH_FLAGS)).isTrue();
    assertThat(player.deviceVolume).isEqualTo(testVolume);
  }

  @Test
  public void increaseDeviceVolume() {
    player.increaseDeviceVolume();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_INCREASE_DEVICE_VOLUME)).isTrue();
  }

  @Test
  public void increaseDeviceVolumeWithFlags() {
    int testVolumeFlags = C.VOLUME_FLAG_VIBRATE | C.VOLUME_FLAG_PLAY_SOUND;
    player.increaseDeviceVolume(testVolumeFlags);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_INCREASE_DEVICE_VOLUME_WITH_FLAGS))
        .isTrue();
  }

  @Test
  public void decreaseDeviceVolume() {
    player.decreaseDeviceVolume();

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_DECREASE_DEVICE_VOLUME)).isTrue();
  }

  @Test
  public void decreaseDeviceVolumeWithFlags() {
    int testVolumeFlags = C.VOLUME_FLAG_SHOW_UI;
    player.decreaseDeviceVolume(testVolumeFlags);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_DECREASE_DEVICE_VOLUME_WITH_FLAGS))
        .isTrue();
  }

  @Test
  public void setDeviceMuted() {
    player.deviceMuted = false;

    player.setDeviceMuted(true);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_DEVICE_MUTED)).isTrue();
    assertThat(player.deviceMuted).isTrue();
  }

  @Test
  public void setDeviceMutedWithFlags() {
    player.deviceMuted = false;
    int testVolumeFlags = 0;

    player.setDeviceMuted(true, testVolumeFlags);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_DEVICE_MUTED_WITH_FLAGS)).isTrue();
    assertThat(player.deviceMuted).isTrue();
  }

  @Test
  public void setTrackSelectionParameters() {
    TrackSelectionParameters trackSelectionParameters =
        TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon().setMaxAudioBitrate(10).build();

    player.setTrackSelectionParameters(trackSelectionParameters);

    assertThat(player.hasMethodBeenCalled(MockPlayer.METHOD_SET_TRACK_SELECTION_PARAMETERS))
        .isTrue();
    assertThat(player.trackSelectionParameters).isSameInstanceAs(trackSelectionParameters);
  }
}
