/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeShuffleOrder;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Playlist}. */
@RunWith(AndroidJUnit4.class)
public class PlaylistTest {

  private static final int PLAYLIST_SIZE = 4;

  private Playlist playlist;

  @Before
  public void setUp() {
    playlist = new Playlist(mock(Playlist.PlaylistInfoRefreshListener.class));
  }

  @Test
  public void testEmptyPlaylist_expectConstantTimelineInstanceEMPTY() {
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 0);
    List<Playlist.MediaSourceHolder> fakeHolders = createFakeHolders();

    Timeline timeline = playlist.setMediaSources(fakeHolders, shuffleOrder);
    assertNotSame(timeline, Timeline.EMPTY);

    // Remove all media sources.
    timeline =
        playlist.removeMediaSourceRange(
            /* fromIndex= */ 0, /* toIndex= */ timeline.getWindowCount(), shuffleOrder);
    assertSame(timeline, Timeline.EMPTY);

    timeline = playlist.setMediaSources(fakeHolders, shuffleOrder);
    assertNotSame(timeline, Timeline.EMPTY);
    // Clear.
    timeline = playlist.clear(shuffleOrder);
    assertSame(timeline, Timeline.EMPTY);
  }

  @Test
  public void testPrepareAndReprepareAfterRelease_expectSourcePreparationAfterPlaylistPrepare() {
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    playlist.setMediaSources(
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, mockMediaSource1, mockMediaSource2),
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 2));
    // Verify prepare is called once on prepare.
    verify(mockMediaSource1, times(0))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());
    verify(mockMediaSource2, times(0))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());

    playlist.prepare(/* mediaTransferListener= */ null);
    assertThat(playlist.isPrepared()).isTrue();
    // Verify prepare is called once on prepare.
    verify(mockMediaSource1, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());
    verify(mockMediaSource2, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());

    playlist.release();
    playlist.prepare(/* mediaTransferListener= */ null);
    // Verify prepare is called a second time on re-prepare.
    verify(mockMediaSource1, times(2))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());
    verify(mockMediaSource2, times(2))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());
  }

  @Test
  public void testSetMediaSources_playlistUnprepared_notUsingLazyPreparation() {
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 2);
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    List<Playlist.MediaSourceHolder> mediaSources =
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, mockMediaSource1, mockMediaSource2);
    Timeline timeline = playlist.setMediaSources(mediaSources, shuffleOrder);

    assertThat(timeline.getWindowCount()).isEqualTo(2);
    assertThat(playlist.getSize()).isEqualTo(2);

    // Assert holder offsets have been set properly
    for (int i = 0; i < mediaSources.size(); i++) {
      Playlist.MediaSourceHolder mediaSourceHolder = mediaSources.get(i);
      assertThat(mediaSourceHolder.isRemoved).isFalse();
      assertThat(mediaSourceHolder.firstWindowIndexInChild).isEqualTo(i);
    }

    // Set media items again. The second holder is re-used.
    List<Playlist.MediaSourceHolder> moreMediaSources =
        createFakeHoldersWithSources(/* useLazyPreparation= */ false, mock(MediaSource.class));
    moreMediaSources.add(mediaSources.get(1));
    timeline = playlist.setMediaSources(moreMediaSources, shuffleOrder);

    assertThat(playlist.getSize()).isEqualTo(2);
    assertThat(timeline.getWindowCount()).isEqualTo(2);
    for (int i = 0; i < moreMediaSources.size(); i++) {
      Playlist.MediaSourceHolder mediaSourceHolder = moreMediaSources.get(i);
      assertThat(mediaSourceHolder.isRemoved).isFalse();
      assertThat(mediaSourceHolder.firstWindowIndexInChild).isEqualTo(i);
    }
    // Expect removed holders and sources to be removed without releasing.
    verify(mockMediaSource1, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    assertThat(mediaSources.get(0).isRemoved).isTrue();
    // Expect re-used holder and source not to be removed.
    verify(mockMediaSource2, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    assertThat(mediaSources.get(1).isRemoved).isFalse();
  }

  @Test
  public void testSetMediaSources_playlistPrepared_notUsingLazyPreparation() {
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 2);
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    List<Playlist.MediaSourceHolder> mediaSources =
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, mockMediaSource1, mockMediaSource2);

    playlist.prepare(/* mediaTransferListener= */ null);
    playlist.setMediaSources(mediaSources, shuffleOrder);

    // Verify sources are prepared.
    verify(mockMediaSource1, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());
    verify(mockMediaSource2, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());

    // Set media items again. The second holder is re-used.
    List<Playlist.MediaSourceHolder> moreMediaSources =
        createFakeHoldersWithSources(/* useLazyPreparation= */ false, mock(MediaSource.class));
    moreMediaSources.add(mediaSources.get(1));
    playlist.setMediaSources(moreMediaSources, shuffleOrder);

    // Expect removed holders and sources to be removed and released.
    verify(mockMediaSource1, times(1)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    assertThat(mediaSources.get(0).isRemoved).isTrue();
    // Expect re-used holder and source not to be removed but released.
    verify(mockMediaSource2, times(1)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    assertThat(mediaSources.get(1).isRemoved).isFalse();
    verify(mockMediaSource2, times(2))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());
  }

  @Test
  public void testAddMediaSources_playlistUnprepared_notUsingLazyPreparation_expectUnprepared() {
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    List<Playlist.MediaSourceHolder> mediaSources =
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, mockMediaSource1, mockMediaSource2);
    playlist.addMediaSources(/* index= */ 0, mediaSources, new ShuffleOrder.DefaultShuffleOrder(2));

    assertThat(playlist.getSize()).isEqualTo(2);
    // Verify lazy initialization does not call prepare on sources.
    verify(mockMediaSource1, times(0))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());
    verify(mockMediaSource2, times(0))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());

    for (int i = 0; i < mediaSources.size(); i++) {
      assertThat(mediaSources.get(i).firstWindowIndexInChild).isEqualTo(i);
      assertThat(mediaSources.get(i).isRemoved).isFalse();
    }

    // Add for more sources in between.
    List<Playlist.MediaSourceHolder> moreMediaSources = createFakeHolders();
    playlist.addMediaSources(
        /* index= */ 1, moreMediaSources, new ShuffleOrder.DefaultShuffleOrder(/* length= */ 3));

    assertThat(mediaSources.get(0).firstWindowIndexInChild).isEqualTo(0);
    assertThat(moreMediaSources.get(0).firstWindowIndexInChild).isEqualTo(1);
    assertThat(moreMediaSources.get(3).firstWindowIndexInChild).isEqualTo(4);
    assertThat(mediaSources.get(1).firstWindowIndexInChild).isEqualTo(5);
  }

  @Test
  public void testAddMediaSources_playlistPrepared_notUsingLazyPreparation_expectPrepared() {
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    playlist.prepare(/* mediaTransferListener= */ null);
    playlist.addMediaSources(
        /* index= */ 0,
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, mockMediaSource1, mockMediaSource2),
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 2));

    // Verify prepare is called on sources when added.
    verify(mockMediaSource1, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());
    verify(mockMediaSource2, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());
  }

  @Test
  public void testMoveMediaSources() {
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 4);
    List<Playlist.MediaSourceHolder> holders = createFakeHolders();
    playlist.addMediaSources(/* index= */ 0, holders, shuffleOrder);

    assertDefaultFirstWindowInChildIndexOrder(holders);
    playlist.moveMediaSource(/* currentIndex= */ 0, /* newIndex= */ 3, shuffleOrder);
    assertFirstWindowInChildIndices(holders, 3, 0, 1, 2);
    playlist.moveMediaSource(/* currentIndex= */ 3, /* newIndex= */ 0, shuffleOrder);
    assertDefaultFirstWindowInChildIndexOrder(holders);

    playlist.moveMediaSourceRange(
        /* fromIndex= */ 0, /* toIndex= */ 2, /* newFromIndex= */ 2, shuffleOrder);
    assertFirstWindowInChildIndices(holders, 2, 3, 0, 1);
    playlist.moveMediaSourceRange(
        /* fromIndex= */ 2, /* toIndex= */ 4, /* newFromIndex= */ 0, shuffleOrder);
    assertDefaultFirstWindowInChildIndexOrder(holders);

    playlist.moveMediaSourceRange(
        /* fromIndex= */ 0, /* toIndex= */ 2, /* newFromIndex= */ 2, shuffleOrder);
    assertFirstWindowInChildIndices(holders, 2, 3, 0, 1);
    playlist.moveMediaSourceRange(
        /* fromIndex= */ 2, /* toIndex= */ 3, /* newFromIndex= */ 0, shuffleOrder);
    assertFirstWindowInChildIndices(holders, 0, 3, 1, 2);
    playlist.moveMediaSourceRange(
        /* fromIndex= */ 3, /* toIndex= */ 4, /* newFromIndex= */ 1, shuffleOrder);
    assertDefaultFirstWindowInChildIndexOrder(holders);

    // No-ops.
    playlist.moveMediaSourceRange(
        /* fromIndex= */ 0, /* toIndex= */ 4, /* newFromIndex= */ 0, shuffleOrder);
    assertDefaultFirstWindowInChildIndexOrder(holders);
    playlist.moveMediaSourceRange(
        /* fromIndex= */ 0, /* toIndex= */ 0, /* newFromIndex= */ 3, shuffleOrder);
    assertDefaultFirstWindowInChildIndexOrder(holders);
  }

  @Test
  public void testRemoveMediaSources_whenUnprepared_expectNoRelease() {
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    MediaSource mockMediaSource3 = mock(MediaSource.class);
    MediaSource mockMediaSource4 = mock(MediaSource.class);
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 4);

    List<Playlist.MediaSourceHolder> holders =
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false,
            mockMediaSource1,
            mockMediaSource2,
            mockMediaSource3,
            mockMediaSource4);
    playlist.addMediaSources(/* index= */ 0, holders, shuffleOrder);
    playlist.removeMediaSourceRange(/* fromIndex= */ 1, /* toIndex= */ 3, shuffleOrder);

    assertThat(playlist.getSize()).isEqualTo(2);
    Playlist.MediaSourceHolder removedHolder1 = holders.remove(1);
    Playlist.MediaSourceHolder removedHolder2 = holders.remove(1);

    assertDefaultFirstWindowInChildIndexOrder(holders);
    assertThat(removedHolder1.isRemoved).isTrue();
    assertThat(removedHolder2.isRemoved).isTrue();
    verify(mockMediaSource1, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    verify(mockMediaSource2, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    verify(mockMediaSource3, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    verify(mockMediaSource4, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
  }

  @Test
  public void testRemoveMediaSources_whenPrepared_expectRelease() {
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    MediaSource mockMediaSource3 = mock(MediaSource.class);
    MediaSource mockMediaSource4 = mock(MediaSource.class);
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 4);

    List<Playlist.MediaSourceHolder> holders =
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false,
            mockMediaSource1,
            mockMediaSource2,
            mockMediaSource3,
            mockMediaSource4);
    playlist.prepare(/* mediaTransferListener */ null);
    playlist.addMediaSources(/* index= */ 0, holders, shuffleOrder);
    playlist.removeMediaSourceRange(/* fromIndex= */ 1, /* toIndex= */ 3, shuffleOrder);

    assertThat(playlist.getSize()).isEqualTo(2);
    holders.remove(2);
    holders.remove(1);

    assertDefaultFirstWindowInChildIndexOrder(holders);
    verify(mockMediaSource1, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    verify(mockMediaSource2, times(1)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    verify(mockMediaSource3, times(1)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    verify(mockMediaSource4, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
  }

  @Test
  public void testRelease_playlistUnprepared_expectSourcesNotReleased() {
    MediaSource mockMediaSource = mock(MediaSource.class);
    Playlist.MediaSourceHolder mediaSourceHolder =
        new Playlist.MediaSourceHolder(mockMediaSource, /* useLazyPreparation= */ false);

    playlist.setMediaSources(
        Collections.singletonList(mediaSourceHolder),
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 1));
    verify(mockMediaSource, times(0))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());
    playlist.release();
    verify(mockMediaSource, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    assertThat(mediaSourceHolder.isRemoved).isFalse();
  }

  @Test
  public void testRelease_playlistPrepared_expectSourcesReleasedNotRemoved() {
    MediaSource mockMediaSource = mock(MediaSource.class);
    Playlist.MediaSourceHolder mediaSourceHolder =
        new Playlist.MediaSourceHolder(mockMediaSource, /* useLazyPreparation= */ false);

    playlist.prepare(/* mediaTransferListener= */ null);
    playlist.setMediaSources(
        Collections.singletonList(mediaSourceHolder),
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 1));
    verify(mockMediaSource, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull());
    playlist.release();
    verify(mockMediaSource, times(1)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    assertThat(mediaSourceHolder.isRemoved).isFalse();
  }

  @Test
  public void testClearPlaylist_expectSourcesReleasedAndRemoved() {
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 4);
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    List<Playlist.MediaSourceHolder> holders =
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, mockMediaSource1, mockMediaSource2);
    playlist.setMediaSources(holders, shuffleOrder);
    playlist.prepare(/* mediaTransferListener= */ null);

    Timeline timeline = playlist.clear(shuffleOrder);
    assertThat(timeline.isEmpty()).isTrue();
    assertThat(holders.get(0).isRemoved).isTrue();
    assertThat(holders.get(1).isRemoved).isTrue();
    verify(mockMediaSource1, times(1)).releaseSource(any());
    verify(mockMediaSource2, times(1)).releaseSource(any());
  }

  @Test
  public void testSetMediaSources_expectTimelineUsesCustomShuffleOrder() {
    Timeline timeline =
        playlist.setMediaSources(createFakeHolders(), new FakeShuffleOrder(/* length=*/ 4));
    assertTimelineUsesFakeShuffleOrder(timeline);
  }

  @Test
  public void testAddMediaSources_expectTimelineUsesCustomShuffleOrder() {
    Timeline timeline =
        playlist.addMediaSources(
            /* index= */ 0, createFakeHolders(), new FakeShuffleOrder(PLAYLIST_SIZE));
    assertTimelineUsesFakeShuffleOrder(timeline);
  }

  @Test
  public void testMoveMediaSources_expectTimelineUsesCustomShuffleOrder() {
    ShuffleOrder shuffleOrder = new ShuffleOrder.DefaultShuffleOrder(/* length= */ PLAYLIST_SIZE);
    playlist.addMediaSources(/* index= */ 0, createFakeHolders(), shuffleOrder);
    Timeline timeline =
        playlist.moveMediaSource(
            /* currentIndex= */ 0, /* newIndex= */ 1, new FakeShuffleOrder(PLAYLIST_SIZE));
    assertTimelineUsesFakeShuffleOrder(timeline);
  }

  @Test
  public void testMoveMediaSourceRange_expectTimelineUsesCustomShuffleOrder() {
    ShuffleOrder shuffleOrder = new ShuffleOrder.DefaultShuffleOrder(/* length= */ PLAYLIST_SIZE);
    playlist.addMediaSources(/* index= */ 0, createFakeHolders(), shuffleOrder);
    Timeline timeline =
        playlist.moveMediaSourceRange(
            /* fromIndex= */ 0,
            /* toIndex= */ 2,
            /* newFromIndex= */ 2,
            new FakeShuffleOrder(PLAYLIST_SIZE));
    assertTimelineUsesFakeShuffleOrder(timeline);
  }

  @Test
  public void testRemoveMediaSourceRange_expectTimelineUsesCustomShuffleOrder() {
    ShuffleOrder shuffleOrder = new ShuffleOrder.DefaultShuffleOrder(/* length= */ PLAYLIST_SIZE);
    playlist.addMediaSources(/* index= */ 0, createFakeHolders(), shuffleOrder);
    Timeline timeline =
        playlist.removeMediaSourceRange(
            /* fromIndex= */ 0, /* toIndex= */ 2, new FakeShuffleOrder(/* length= */ 2));
    assertTimelineUsesFakeShuffleOrder(timeline);
  }

  @Test
  public void testSetShuffleOrder_expectTimelineUsesCustomShuffleOrder() {
    playlist.setMediaSources(
        createFakeHolders(), new ShuffleOrder.DefaultShuffleOrder(/* length= */ PLAYLIST_SIZE));
    assertTimelineUsesFakeShuffleOrder(
        playlist.setShuffleOrder(new FakeShuffleOrder(PLAYLIST_SIZE)));
  }

  // Internal methods.

  private static void assertTimelineUsesFakeShuffleOrder(Timeline timeline) {
    assertThat(
            timeline.getNextWindowIndex(
                /* windowIndex= */ 0, Player.REPEAT_MODE_OFF, /* shuffleModeEnabled= */ true))
        .isEqualTo(-1);
    assertThat(
            timeline.getPreviousWindowIndex(
                /* windowIndex= */ timeline.getWindowCount() - 1,
                Player.REPEAT_MODE_OFF,
                /* shuffleModeEnabled= */ true))
        .isEqualTo(-1);
  }

  private static void assertDefaultFirstWindowInChildIndexOrder(
      List<Playlist.MediaSourceHolder> holders) {
    int[] indices = new int[holders.size()];
    for (int i = 0; i < indices.length; i++) {
      indices[i] = i;
    }
    assertFirstWindowInChildIndices(holders, indices);
  }

  private static void assertFirstWindowInChildIndices(
      List<Playlist.MediaSourceHolder> holders, int... firstWindowInChildIndices) {
    assertThat(holders).hasSize(firstWindowInChildIndices.length);
    for (int i = 0; i < holders.size(); i++) {
      assertThat(holders.get(i).firstWindowIndexInChild).isEqualTo(firstWindowInChildIndices[i]);
    }
  }

  private static List<Playlist.MediaSourceHolder> createFakeHolders() {
    MediaSource fakeMediaSource = new FakeMediaSource(new FakeTimeline(1));
    List<Playlist.MediaSourceHolder> holders = new ArrayList<>();
    for (int i = 0; i < PLAYLIST_SIZE; i++) {
      holders.add(new Playlist.MediaSourceHolder(fakeMediaSource, /* useLazyPreparation= */ true));
    }
    return holders;
  }

  private static List<Playlist.MediaSourceHolder> createFakeHoldersWithSources(
      boolean useLazyPreparation, MediaSource... sources) {
    List<Playlist.MediaSourceHolder> holders = new ArrayList<>();
    for (MediaSource mediaSource : sources) {
      holders.add(
          new Playlist.MediaSourceHolder(
              mediaSource, /* useLazyPreparation= */ useLazyPreparation));
    }
    return holders;
  }
}
