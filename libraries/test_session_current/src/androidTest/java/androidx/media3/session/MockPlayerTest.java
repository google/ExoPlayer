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
    assertThat(player.playCalled).isTrue();
  }

  @Test
  public void pause() {
    player.pause();
    assertThat(player.pauseCalled).isTrue();
  }

  @Test
  public void prepare() {
    player.prepare();
    assertThat(player.prepareCalled).isTrue();
  }

  @Test
  public void stop() {
    player.stop();
    assertThat(player.stopCalled).isTrue();
  }

  @Test
  public void release() {
    player.release();
    assertThat(player.releaseCalled).isTrue();
  }

  @Test
  public void setPlayWhenReady() {
    boolean testPlayWhenReady = false;
    player.setPlayWhenReady(testPlayWhenReady);
    assertThat(player.setPlayWhenReadyCalled).isTrue();
  }

  @Test
  public void seekTo() {
    long pos = 1004L;
    player.seekTo(pos);
    assertThat(player.seekToCalled).isTrue();
    assertThat(player.seekPositionMs).isEqualTo(pos);
  }

  @Test
  public void seekBack() {
    player.seekBack();
    assertThat(player.seekBackCalled).isTrue();
  }

  @Test
  public void seekForward() {
    player.seekForward();
    assertThat(player.seekForwardCalled).isTrue();
  }

  @Test
  public void setPlaybackParameters() {
    PlaybackParameters playbackParameters = new PlaybackParameters(/* speed= */ 1.5f);
    player.setPlaybackParameters(playbackParameters);
    assertThat(player.setPlaybackParametersCalled).isTrue();
    assertThat(player.playbackParameters).isEqualTo(playbackParameters);
  }

  @Test
  public void setPlaybackSpeed() {
    float speed = 1.5f;
    player.setPlaybackSpeed(speed);
    assertThat(player.setPlaybackSpeedCalled).isTrue();
    assertThat(player.playbackParameters.speed).isEqualTo(speed);
  }

  @Test
  public void setMediaItem() {
    MediaItem mediaItem = MediaTestUtils.createMediaItem("setMediaItem");
    player.setMediaItem(mediaItem);
    assertThat(player.setMediaItemCalled).isTrue();
    assertThat(player.mediaItem).isSameInstanceAs(mediaItem);
  }

  @Test
  public void setMediaItem_withStartPosition() {
    MediaItem mediaItem = MediaTestUtils.createMediaItem("setMediaItem");
    long startPositionMs = 321L;
    player.setMediaItem(mediaItem, startPositionMs);
    assertThat(player.setMediaItemWithStartPositionCalled).isTrue();
    assertThat(player.startPositionMs).isEqualTo(startPositionMs);
    assertThat(player.mediaItem).isSameInstanceAs(mediaItem);
  }

  @Test
  public void setMediaItem_withResetPosition() {
    MediaItem mediaItem = MediaTestUtils.createMediaItem("setMediaItem");
    boolean resetPosition = true;
    player.setMediaItem(mediaItem, resetPosition);
    assertThat(player.setMediaItemWithResetPositionCalled).isTrue();
    assertThat(player.resetPosition).isEqualTo(resetPosition);
    assertThat(player.mediaItem).isEqualTo(mediaItem);
  }

  @Test
  public void setMediaItems() {
    List<MediaItem> list = MediaTestUtils.createMediaItems(/* size= */ 2);
    player.setMediaItems(list);
    assertThat(player.setMediaItemsCalled).isTrue();
    assertThat(player.mediaItems).isSameInstanceAs(list);
  }

  @Test
  public void setMediaItems_withResetPosition() {
    List<MediaItem> list = MediaTestUtils.createMediaItems(/* size= */ 2);
    boolean resetPosition = true;
    player.setMediaItems(list, resetPosition);
    assertThat(player.setMediaItemsWithResetPositionCalled).isTrue();
    assertThat(player.resetPosition).isEqualTo(resetPosition);
    assertThat(player.mediaItems).isSameInstanceAs(list);
  }

  @Test
  public void setMediaItems_withStartWindowIndex() {
    List<MediaItem> list = MediaTestUtils.createMediaItems(/* size= */ 2);
    int startWindowIndex = 3;
    long startPositionMs = 132L;
    player.setMediaItems(list, startWindowIndex, startPositionMs);
    assertThat(player.setMediaItemsWithStartIndexCalled).isTrue();
    assertThat(player.startMediaItemIndex).isEqualTo(startWindowIndex);
    assertThat(player.startPositionMs).isEqualTo(startPositionMs);
    assertThat(player.mediaItems).isSameInstanceAs(list);
  }

  @Test
  public void setMediaItems_withDuplicatedItems() {
    List<MediaItem> list = MediaTestUtils.createMediaItems(/* size= */ 4);
    list.set(2, list.get(1));
    player.setMediaItems(list);
    assertThat(player.setMediaItemsCalled).isTrue();
    assertThat(player.mediaItems).isSameInstanceAs(list);
  }

  @Test
  public void setPlaylistMetadata() {
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setTitle("title").build();

    player.setPlaylistMetadata(playlistMetadata);

    assertThat(player.setPlaylistMetadataCalled).isTrue();
    assertThat(player.playlistMetadata).isSameInstanceAs(playlistMetadata);
  }

  @Test
  public void addMediaItem() {
    MediaItem mediaItem = MediaTestUtils.createMediaItem("item");

    player.addMediaItem(mediaItem);

    assertThat(player.addMediaItemCalled).isTrue();
    assertThat(player.mediaItem).isSameInstanceAs(mediaItem);
  }

  @Test
  public void addMediaItem_withIndex() {
    int index = 1;
    MediaItem mediaItem = MediaTestUtils.createMediaItem("item");

    player.addMediaItem(index, mediaItem);

    assertThat(player.addMediaItemWithIndexCalled).isTrue();
    assertThat(player.index).isEqualTo(index);
    assertThat(player.mediaItem).isSameInstanceAs(mediaItem);
  }

  @Test
  public void addMediaItems() {
    int index = 1;
    int size = 2;
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(size);

    player.addMediaItems(index, mediaItems);

    assertThat(player.addMediaItemsWithIndexCalled).isTrue();
    assertThat(player.index).isEqualTo(index);
    assertThat(player.mediaItems).isSameInstanceAs(mediaItems);
  }

  @Test
  public void addMediaItems_withIndex() {
    int index = 1;
    int size = 2;
    List<MediaItem> mediaItems = MediaTestUtils.createMediaItems(size);

    player.addMediaItems(index, mediaItems);

    assertThat(player.addMediaItemsWithIndexCalled).isTrue();
    assertThat(player.index).isEqualTo(index);
    assertThat(player.mediaItems).isSameInstanceAs(mediaItems);
  }

  @Test
  public void removeMediaItem() {
    int index = 8;

    player.removeMediaItem(index);

    assertThat(player.removeMediaItemCalled).isTrue();
    assertThat(player.index).isEqualTo(index);
  }

  @Test
  public void removeMediaItems() {
    int fromIndex = 1;
    int toIndex = 3;

    player.removeMediaItems(fromIndex, toIndex);

    assertThat(player.removeMediaItemsCalled).isTrue();
    assertThat(player.fromIndex).isEqualTo(fromIndex);
    assertThat(player.toIndex).isEqualTo(toIndex);
  }

  @Test
  public void clearMediaItems() {
    player.clearMediaItems();

    assertThat(player.clearMediaItemsCalled).isTrue();
  }

  @Test
  public void moveMediaItem() {
    int index = 2;
    int newIndex = 3;

    player.moveMediaItem(index, newIndex);

    assertThat(player.moveMediaItemCalled).isTrue();
    assertThat(player.index).isEqualTo(index);
    assertThat(player.newIndex).isEqualTo(newIndex);
  }

  @Test
  public void moveMediaItems() {
    int fromIndex = 1;
    int toIndex = 2;
    int newIndex = 3;

    player.moveMediaItems(fromIndex, toIndex, newIndex);

    assertThat(player.moveMediaItemsCalled).isTrue();
    assertThat(player.fromIndex).isEqualTo(fromIndex);
    assertThat(player.toIndex).isEqualTo(toIndex);
    assertThat(player.newIndex).isEqualTo(newIndex);
  }

  @Test
  public void seekToPreviousWindow() {
    player.seekToPreviousWindow();
    assertThat(player.seekToPreviousMediaItemCalled).isTrue();
  }

  @Test
  public void seekToNextWindow() {
    player.seekToNextWindow();
    assertThat(player.seekToNextMediaItemCalled).isTrue();
  }

  @Test
  public void seekToPrevious() {
    player.seekToPrevious();
    assertThat(player.seekToPreviousCalled).isTrue();
  }

  @Test
  public void seekToNext() {
    player.seekToNext();
    assertThat(player.seekToNextCalled).isTrue();
  }

  @Test
  public void setShuffleModeEnabled() {
    boolean testShuffleModeEnabled = true;
    player.setShuffleModeEnabled(testShuffleModeEnabled);
    assertThat(player.setShuffleModeCalled).isTrue();
    assertThat(player.shuffleModeEnabled).isEqualTo(testShuffleModeEnabled);
  }

  @Test
  public void setRepeatMode() {
    int testRepeatMode = Player.REPEAT_MODE_ALL;
    player.setRepeatMode(testRepeatMode);
    assertThat(player.setRepeatModeCalled).isTrue();
    assertThat(player.repeatMode).isEqualTo(testRepeatMode);
  }

  @Test
  public void setVolume() {
    float testVolume = .123f;
    player.setVolume(testVolume);
    assertThat(player.setVolumeCalled).isTrue();
    assertThat(player.volume).isEqualTo(testVolume);
  }

  @Test
  public void setDeviceVolume() {
    int testVolume = 12;
    player.setDeviceVolume(testVolume);
    assertThat(player.setDeviceVolumeCalled).isTrue();
    assertThat(player.deviceVolume).isEqualTo(testVolume);
  }

  @Test
  public void increaseDeviceVolume() {
    player.increaseDeviceVolume();
    assertThat(player.increaseDeviceVolumeCalled).isTrue();
  }

  @Test
  public void decreaseDeviceVolume() {
    player.decreaseDeviceVolume();
    assertThat(player.decreaseDeviceVolumeCalled).isTrue();
  }

  @Test
  public void setDeviceMuted() {
    player.deviceMuted = false;
    player.setDeviceMuted(true);
    assertThat(player.setDeviceMutedCalled).isTrue();
    assertThat(player.deviceMuted).isTrue();
  }

  @Test
  public void setTrackSelectionParameters() {
    TrackSelectionParameters trackSelectionParameters =
        TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT.buildUpon().setMaxAudioBitrate(10).build();

    player.setTrackSelectionParameters(trackSelectionParameters);

    assertThat(player.setTrackSelectionParametersCalled).isTrue();
    assertThat(player.trackSelectionParameters).isSameInstanceAs(trackSelectionParameters);
  }
}
