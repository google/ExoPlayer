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
package com.google.android.exoplayer2.session;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
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
  public void setMediaItems() {
    List<MediaItem> list = MediaTestUtils.createConvergedMediaItems(/* size= */ 2);
    player.setMediaItems(list);
    assertThat(player.setMediaItemsCalled).isTrue();
    assertThat(player.mediaItems).isEqualTo(list);
  }

  @Test
  public void setMediaItems_withDuplicatedItems() {
    List<MediaItem> list = MediaTestUtils.createConvergedMediaItems(/* size= */ 4);
    list.set(2, list.get(1));
    player.setMediaItems(list);
    assertThat(player.setMediaItemsCalled).isTrue();
    assertThat(player.mediaItems).isEqualTo(list);
  }

  @Test
  public void setPlaylistMetadata() {
    MediaMetadata playlistMetadata = new MediaMetadata.Builder().setTitle("title").build();

    player.setPlaylistMetadata(playlistMetadata);

    assertThat(player.setPlaylistMetadataCalled).isTrue();
    assertThat(player.playlistMetadata).isSameInstanceAs(playlistMetadata);
  }

  @Test
  public void addMediaItems() {
    int index = 1;
    int size = 2;
    List<MediaItem> mediaItems = MediaTestUtils.createConvergedMediaItems(size);

    player.addMediaItems(index, mediaItems);

    assertThat(player.addMediaItemsCalled).isTrue();
    assertThat(player.index).isEqualTo(index);
    assertThat(player.mediaItems).isSameInstanceAs(mediaItems);
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
  public void skipToPreviousItem() {
    player.previous();
    assertThat(player.previousCalled).isTrue();
  }

  @Test
  public void skipToNextItem() {
    player.next();
    assertThat(player.nextCalled).isTrue();
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
}
