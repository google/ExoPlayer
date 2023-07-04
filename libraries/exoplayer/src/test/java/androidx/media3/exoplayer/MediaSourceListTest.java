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
package androidx.media3.exoplayer;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.analytics.AnalyticsCollector;
import androidx.media3.exoplayer.analytics.DefaultAnalyticsCollector;
import androidx.media3.exoplayer.analytics.PlayerId;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ShuffleOrder;
import androidx.media3.test.utils.FakeMediaSource;
import androidx.media3.test.utils.FakeShuffleOrder;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link MediaSourceList}. */
@RunWith(AndroidJUnit4.class)
public class MediaSourceListTest {

  private static final int MEDIA_SOURCE_LIST_SIZE = 4;
  private static final MediaItem MINIMAL_MEDIA_ITEM =
      new MediaItem.Builder().setMediaId("").build();

  private MediaSourceList mediaSourceList;

  @Before
  public void setUp() {
    AnalyticsCollector analyticsCollector = new DefaultAnalyticsCollector(Clock.DEFAULT);
    analyticsCollector.setPlayer(
        new ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build(),
        Looper.getMainLooper());
    mediaSourceList =
        new MediaSourceList(
            mock(MediaSourceList.MediaSourceListInfoRefreshListener.class),
            analyticsCollector,
            Clock.DEFAULT.createHandler(Util.getCurrentOrMainLooper(), /* callback= */ null),
            PlayerId.UNSET);
  }

  @Test
  public void emptyMediaSourceList_expectConstantTimelineInstanceEMPTY() {
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 0);
    List<MediaSourceList.MediaSourceHolder> fakeHolders = createFakeHolders();

    Timeline timeline = mediaSourceList.setMediaSources(fakeHolders, shuffleOrder);
    assertNotSame(timeline, Timeline.EMPTY);

    // Remove all media sources.
    timeline =
        mediaSourceList.removeMediaSourceRange(
            /* fromIndex= */ 0, /* toIndex= */ timeline.getWindowCount(), shuffleOrder);
    assertSame(timeline, Timeline.EMPTY);

    timeline = mediaSourceList.setMediaSources(fakeHolders, shuffleOrder);
    assertNotSame(timeline, Timeline.EMPTY);
    // Clear.
    timeline = mediaSourceList.clear(shuffleOrder);
    assertSame(timeline, Timeline.EMPTY);
  }

  @Test
  public void prepareAndReprepareAfterRelease_expectSourcePreparationAfterMediaSourceListPrepare() {
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    when(mockMediaSource1.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    when(mockMediaSource2.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    mediaSourceList.setMediaSources(
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, mockMediaSource1, mockMediaSource2),
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 2));
    // Verify prepare is called once on prepare.
    verify(mockMediaSource1, times(0))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());
    verify(mockMediaSource2, times(0))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());

    mediaSourceList.prepare(/* mediaTransferListener= */ null);
    assertThat(mediaSourceList.isPrepared()).isTrue();
    // Verify prepare is called once on prepare.
    verify(mockMediaSource1, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());
    verify(mockMediaSource2, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());

    mediaSourceList.release();
    mediaSourceList.prepare(/* mediaTransferListener= */ null);
    // Verify prepare is called a second time on re-prepare.
    verify(mockMediaSource1, times(2))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());
    verify(mockMediaSource2, times(2))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());
  }

  @Test
  public void setMediaSources_mediaSourceListUnprepared_notUsingLazyPreparation() {
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 2);
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    when(mockMediaSource1.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    when(mockMediaSource2.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    List<MediaSourceList.MediaSourceHolder> mediaSources =
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, mockMediaSource1, mockMediaSource2);
    Timeline timeline = mediaSourceList.setMediaSources(mediaSources, shuffleOrder);

    assertThat(timeline.getWindowCount()).isEqualTo(2);
    assertThat(mediaSourceList.getSize()).isEqualTo(2);

    // Assert holder offsets have been set properly
    for (int i = 0; i < mediaSources.size(); i++) {
      MediaSourceList.MediaSourceHolder mediaSourceHolder = mediaSources.get(i);
      assertThat(mediaSourceHolder.isRemoved).isFalse();
      assertThat(mediaSourceHolder.firstWindowIndexInChild).isEqualTo(i);
    }

    // Set media items again. The second holder is re-used.
    MediaSource mockMediaSource3 = mock(MediaSource.class);
    when(mockMediaSource3.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    List<MediaSourceList.MediaSourceHolder> moreMediaSources =
        createFakeHoldersWithSources(/* useLazyPreparation= */ false, mockMediaSource3);
    moreMediaSources.add(mediaSources.get(1));
    timeline = mediaSourceList.setMediaSources(moreMediaSources, shuffleOrder);

    assertThat(mediaSourceList.getSize()).isEqualTo(2);
    assertThat(timeline.getWindowCount()).isEqualTo(2);
    for (int i = 0; i < moreMediaSources.size(); i++) {
      MediaSourceList.MediaSourceHolder mediaSourceHolder = moreMediaSources.get(i);
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
  public void setMediaSources_mediaSourceListPrepared_notUsingLazyPreparation() {
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 2);
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    when(mockMediaSource1.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    when(mockMediaSource2.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    List<MediaSourceList.MediaSourceHolder> mediaSources =
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, mockMediaSource1, mockMediaSource2);

    mediaSourceList.prepare(/* mediaTransferListener= */ null);
    mediaSourceList.setMediaSources(mediaSources, shuffleOrder);

    // Verify sources are prepared.
    verify(mockMediaSource1, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());
    verify(mockMediaSource2, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());

    // Set media items again. The second holder is re-used.
    MediaSource mockMediaSource3 = mock(MediaSource.class);
    when(mockMediaSource3.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    List<MediaSourceList.MediaSourceHolder> moreMediaSources =
        createFakeHoldersWithSources(/* useLazyPreparation= */ false, mockMediaSource3);
    moreMediaSources.add(mediaSources.get(1));
    mediaSourceList.setMediaSources(moreMediaSources, shuffleOrder);

    // Expect removed holders and sources to be removed and released.
    verify(mockMediaSource1, times(1)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    assertThat(mediaSources.get(0).isRemoved).isTrue();
    // Expect re-used holder and source not to be removed but released.
    verify(mockMediaSource2, times(1)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    assertThat(mediaSources.get(1).isRemoved).isFalse();
    verify(mockMediaSource2, times(2))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());
  }

  @Test
  public void addMediaSources_mediaSourceListUnprepared_notUsingLazyPreparation_expectUnprepared() {
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    when(mockMediaSource1.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    when(mockMediaSource2.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    List<MediaSourceList.MediaSourceHolder> mediaSources =
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, mockMediaSource1, mockMediaSource2);
    mediaSourceList.addMediaSources(
        /* index= */ 0, mediaSources, new ShuffleOrder.DefaultShuffleOrder(2));

    assertThat(mediaSourceList.getSize()).isEqualTo(2);
    // Verify lazy initialization does not call prepare on sources.
    verify(mockMediaSource1, times(0))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());
    verify(mockMediaSource2, times(0))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());

    for (int i = 0; i < mediaSources.size(); i++) {
      assertThat(mediaSources.get(i).firstWindowIndexInChild).isEqualTo(i);
      assertThat(mediaSources.get(i).isRemoved).isFalse();
    }

    // Add for more sources in between.
    List<MediaSourceList.MediaSourceHolder> moreMediaSources = createFakeHolders();
    mediaSourceList.addMediaSources(
        /* index= */ 1, moreMediaSources, new ShuffleOrder.DefaultShuffleOrder(/* length= */ 3));

    assertThat(mediaSources.get(0).firstWindowIndexInChild).isEqualTo(0);
    assertThat(moreMediaSources.get(0).firstWindowIndexInChild).isEqualTo(1);
    assertThat(moreMediaSources.get(3).firstWindowIndexInChild).isEqualTo(4);
    assertThat(mediaSources.get(1).firstWindowIndexInChild).isEqualTo(5);
  }

  @Test
  public void addMediaSources_mediaSourceListPrepared_notUsingLazyPreparation_expectPrepared() {
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    when(mockMediaSource1.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    when(mockMediaSource2.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    mediaSourceList.prepare(/* mediaTransferListener= */ null);
    mediaSourceList.addMediaSources(
        /* index= */ 0,
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, mockMediaSource1, mockMediaSource2),
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 2));

    // Verify prepare is called on sources when added.
    verify(mockMediaSource1, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());
    verify(mockMediaSource2, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());
  }

  @Test
  public void moveMediaSources() {
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 4);
    List<MediaSourceList.MediaSourceHolder> holders = createFakeHolders();
    mediaSourceList.addMediaSources(/* index= */ 0, holders, shuffleOrder);

    assertDefaultFirstWindowInChildIndexOrder(holders);
    mediaSourceList.moveMediaSource(/* currentIndex= */ 0, /* newIndex= */ 3, shuffleOrder);
    assertFirstWindowInChildIndices(holders, 3, 0, 1, 2);
    mediaSourceList.moveMediaSource(/* currentIndex= */ 3, /* newIndex= */ 0, shuffleOrder);
    assertDefaultFirstWindowInChildIndexOrder(holders);

    mediaSourceList.moveMediaSourceRange(
        /* fromIndex= */ 0, /* toIndex= */ 2, /* newFromIndex= */ 2, shuffleOrder);
    assertFirstWindowInChildIndices(holders, 2, 3, 0, 1);
    mediaSourceList.moveMediaSourceRange(
        /* fromIndex= */ 2, /* toIndex= */ 4, /* newFromIndex= */ 0, shuffleOrder);
    assertDefaultFirstWindowInChildIndexOrder(holders);

    mediaSourceList.moveMediaSourceRange(
        /* fromIndex= */ 0, /* toIndex= */ 2, /* newFromIndex= */ 2, shuffleOrder);
    assertFirstWindowInChildIndices(holders, 2, 3, 0, 1);
    mediaSourceList.moveMediaSourceRange(
        /* fromIndex= */ 2, /* toIndex= */ 3, /* newFromIndex= */ 0, shuffleOrder);
    assertFirstWindowInChildIndices(holders, 0, 3, 1, 2);
    mediaSourceList.moveMediaSourceRange(
        /* fromIndex= */ 3, /* toIndex= */ 4, /* newFromIndex= */ 1, shuffleOrder);
    assertDefaultFirstWindowInChildIndexOrder(holders);

    // No-ops.
    mediaSourceList.moveMediaSourceRange(
        /* fromIndex= */ 0, /* toIndex= */ 4, /* newFromIndex= */ 0, shuffleOrder);
    assertDefaultFirstWindowInChildIndexOrder(holders);
    mediaSourceList.moveMediaSourceRange(
        /* fromIndex= */ 0, /* toIndex= */ 0, /* newFromIndex= */ 3, shuffleOrder);
    assertDefaultFirstWindowInChildIndexOrder(holders);
  }

  @Test
  public void removeMediaSources_whenUnprepared_expectNoRelease() {
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    when(mockMediaSource1.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    when(mockMediaSource2.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSource mockMediaSource3 = mock(MediaSource.class);
    when(mockMediaSource3.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSource mockMediaSource4 = mock(MediaSource.class);
    when(mockMediaSource4.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 4);

    List<MediaSourceList.MediaSourceHolder> holders =
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false,
            mockMediaSource1,
            mockMediaSource2,
            mockMediaSource3,
            mockMediaSource4);
    mediaSourceList.addMediaSources(/* index= */ 0, holders, shuffleOrder);
    mediaSourceList.removeMediaSourceRange(/* fromIndex= */ 1, /* toIndex= */ 3, shuffleOrder);

    assertThat(mediaSourceList.getSize()).isEqualTo(2);
    MediaSourceList.MediaSourceHolder removedHolder1 = holders.remove(1);
    MediaSourceList.MediaSourceHolder removedHolder2 = holders.remove(1);

    assertDefaultFirstWindowInChildIndexOrder(holders);
    assertThat(removedHolder1.isRemoved).isTrue();
    assertThat(removedHolder2.isRemoved).isTrue();
    verify(mockMediaSource1, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    verify(mockMediaSource2, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    verify(mockMediaSource3, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    verify(mockMediaSource4, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
  }

  @Test
  public void removeMediaSources_whenPrepared_expectRelease() {
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    when(mockMediaSource1.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    when(mockMediaSource2.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSource mockMediaSource3 = mock(MediaSource.class);
    when(mockMediaSource3.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSource mockMediaSource4 = mock(MediaSource.class);
    when(mockMediaSource4.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 4);

    List<MediaSourceList.MediaSourceHolder> holders =
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false,
            mockMediaSource1,
            mockMediaSource2,
            mockMediaSource3,
            mockMediaSource4);
    mediaSourceList.prepare(/* mediaTransferListener= */ null);
    mediaSourceList.addMediaSources(/* index= */ 0, holders, shuffleOrder);
    mediaSourceList.removeMediaSourceRange(/* fromIndex= */ 1, /* toIndex= */ 3, shuffleOrder);

    assertThat(mediaSourceList.getSize()).isEqualTo(2);
    holders.remove(2);
    holders.remove(1);

    assertDefaultFirstWindowInChildIndexOrder(holders);
    verify(mockMediaSource1, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    verify(mockMediaSource2, times(1)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    verify(mockMediaSource3, times(1)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    verify(mockMediaSource4, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
  }

  @Test
  public void release_mediaSourceListUnprepared_expectSourcesNotReleased() {
    MediaSource mockMediaSource = mock(MediaSource.class);
    when(mockMediaSource.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSourceList.MediaSourceHolder mediaSourceHolder =
        new MediaSourceList.MediaSourceHolder(mockMediaSource, /* useLazyPreparation= */ false);

    mediaSourceList.setMediaSources(
        Collections.singletonList(mediaSourceHolder),
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 1));
    verify(mockMediaSource, times(0))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());
    mediaSourceList.release();
    verify(mockMediaSource, times(0)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    assertThat(mediaSourceHolder.isRemoved).isFalse();
  }

  @Test
  public void release_mediaSourceListPrepared_expectSourcesReleasedNotRemoved() {
    MediaSource mockMediaSource = mock(MediaSource.class);
    when(mockMediaSource.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSourceList.MediaSourceHolder mediaSourceHolder =
        new MediaSourceList.MediaSourceHolder(mockMediaSource, /* useLazyPreparation= */ false);

    mediaSourceList.prepare(/* mediaTransferListener= */ null);
    mediaSourceList.setMediaSources(
        Collections.singletonList(mediaSourceHolder),
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 1));
    verify(mockMediaSource, times(1))
        .prepareSource(
            any(MediaSource.MediaSourceCaller.class), /* mediaTransferListener= */ isNull(), any());
    mediaSourceList.release();
    verify(mockMediaSource, times(1)).releaseSource(any(MediaSource.MediaSourceCaller.class));
    assertThat(mediaSourceHolder.isRemoved).isFalse();
  }

  @Test
  public void clearMediaSourceList_expectSourcesReleasedAndRemoved() {
    ShuffleOrder.DefaultShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 4);
    MediaSource mockMediaSource1 = mock(MediaSource.class);
    when(mockMediaSource1.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    MediaSource mockMediaSource2 = mock(MediaSource.class);
    when(mockMediaSource2.getMediaItem()).thenReturn(MINIMAL_MEDIA_ITEM);
    List<MediaSourceList.MediaSourceHolder> holders =
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, mockMediaSource1, mockMediaSource2);
    mediaSourceList.setMediaSources(holders, shuffleOrder);
    mediaSourceList.prepare(/* mediaTransferListener= */ null);

    Timeline timeline = mediaSourceList.clear(shuffleOrder);
    assertThat(timeline.isEmpty()).isTrue();
    assertThat(holders.get(0).isRemoved).isTrue();
    assertThat(holders.get(1).isRemoved).isTrue();
    verify(mockMediaSource1, times(1)).releaseSource(any());
    verify(mockMediaSource2, times(1)).releaseSource(any());
  }

  @Test
  public void setMediaSources_expectTimelineUsesCustomShuffleOrder() {
    Timeline timeline =
        mediaSourceList.setMediaSources(createFakeHolders(), new FakeShuffleOrder(/* length= */ 4));
    assertTimelineUsesFakeShuffleOrder(timeline);
  }

  @Test
  public void addMediaSources_expectTimelineUsesCustomShuffleOrder() {
    Timeline timeline =
        mediaSourceList.addMediaSources(
            /* index= */ 0, createFakeHolders(), new FakeShuffleOrder(MEDIA_SOURCE_LIST_SIZE));
    assertTimelineUsesFakeShuffleOrder(timeline);
  }

  @Test
  public void moveMediaSources_expectTimelineUsesCustomShuffleOrder() {
    ShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ MEDIA_SOURCE_LIST_SIZE);
    mediaSourceList.addMediaSources(/* index= */ 0, createFakeHolders(), shuffleOrder);
    Timeline timeline =
        mediaSourceList.moveMediaSource(
            /* currentIndex= */ 0, /* newIndex= */ 1, new FakeShuffleOrder(MEDIA_SOURCE_LIST_SIZE));
    assertTimelineUsesFakeShuffleOrder(timeline);
  }

  @Test
  public void moveMediaSourceRange_expectTimelineUsesCustomShuffleOrder() {
    ShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ MEDIA_SOURCE_LIST_SIZE);
    mediaSourceList.addMediaSources(/* index= */ 0, createFakeHolders(), shuffleOrder);
    Timeline timeline =
        mediaSourceList.moveMediaSourceRange(
            /* fromIndex= */ 0,
            /* toIndex= */ 2,
            /* newFromIndex= */ 2,
            new FakeShuffleOrder(MEDIA_SOURCE_LIST_SIZE));
    assertTimelineUsesFakeShuffleOrder(timeline);
  }

  @Test
  public void removeMediaSourceRange_expectTimelineUsesCustomShuffleOrder() {
    ShuffleOrder shuffleOrder =
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ MEDIA_SOURCE_LIST_SIZE);
    mediaSourceList.addMediaSources(/* index= */ 0, createFakeHolders(), shuffleOrder);
    Timeline timeline =
        mediaSourceList.removeMediaSourceRange(
            /* fromIndex= */ 0, /* toIndex= */ 2, new FakeShuffleOrder(/* length= */ 2));
    assertTimelineUsesFakeShuffleOrder(timeline);
  }

  @Test
  public void setShuffleOrder_expectTimelineUsesCustomShuffleOrder() {
    mediaSourceList.setMediaSources(
        createFakeHolders(),
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ MEDIA_SOURCE_LIST_SIZE));
    assertTimelineUsesFakeShuffleOrder(
        mediaSourceList.setShuffleOrder(new FakeShuffleOrder(MEDIA_SOURCE_LIST_SIZE)));
  }

  @Test
  public void updateMediaSourcesWithMediaItems_updatesMediaItemsForPreparedAndPlaceholderSources() {
    FakeMediaSource unaffectedSource = new FakeMediaSource();
    FakeMediaSource preparedSource = new FakeMediaSource();
    preparedSource.setCanUpdateMediaItems(true);
    preparedSource.setAllowPreparation(true);
    FakeMediaSource unpreparedSource = new FakeMediaSource();
    unpreparedSource.setCanUpdateMediaItems(true);
    unpreparedSource.setAllowPreparation(false);
    mediaSourceList.setMediaSources(
        createFakeHoldersWithSources(
            /* useLazyPreparation= */ false, unaffectedSource, preparedSource, unpreparedSource),
        new ShuffleOrder.DefaultShuffleOrder(/* length= */ 3));
    mediaSourceList.prepare(/* mediaTransferListener= */ null);
    MediaItem unaffectedMediaItem = unaffectedSource.getMediaItem();
    MediaItem updatedItem1 = new MediaItem.Builder().setMediaId("1").build();
    MediaItem updatedItem2 = new MediaItem.Builder().setMediaId("2").build();

    Timeline timeline =
        mediaSourceList.updateMediaSourcesWithMediaItems(
            /* fromIndex= */ 1, /* toIndex= */ 3, ImmutableList.of(updatedItem1, updatedItem2));

    assertThat(timeline.getWindow(/* windowIndex= */ 0, new Timeline.Window()).mediaItem)
        .isEqualTo(unaffectedMediaItem);
    assertThat(timeline.getWindow(/* windowIndex= */ 1, new Timeline.Window()).mediaItem)
        .isEqualTo(updatedItem1);
    assertThat(timeline.getWindow(/* windowIndex= */ 1, new Timeline.Window()).isPlaceholder)
        .isFalse();
    assertThat(timeline.getWindow(/* windowIndex= */ 2, new Timeline.Window()).mediaItem)
        .isEqualTo(updatedItem2);
    assertThat(timeline.getWindow(/* windowIndex= */ 2, new Timeline.Window()).isPlaceholder)
        .isTrue();
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
      List<MediaSourceList.MediaSourceHolder> holders) {
    int[] indices = new int[holders.size()];
    for (int i = 0; i < indices.length; i++) {
      indices[i] = i;
    }
    assertFirstWindowInChildIndices(holders, indices);
  }

  private static void assertFirstWindowInChildIndices(
      List<MediaSourceList.MediaSourceHolder> holders, int... firstWindowInChildIndices) {
    assertThat(holders).hasSize(firstWindowInChildIndices.length);
    for (int i = 0; i < holders.size(); i++) {
      assertThat(holders.get(i).firstWindowIndexInChild).isEqualTo(firstWindowInChildIndices[i]);
    }
  }

  private static List<MediaSourceList.MediaSourceHolder> createFakeHolders() {
    List<MediaSourceList.MediaSourceHolder> holders = new ArrayList<>();
    for (int i = 0; i < MEDIA_SOURCE_LIST_SIZE; i++) {
      holders.add(
          new MediaSourceList.MediaSourceHolder(
              new FakeMediaSource(), /* useLazyPreparation= */ true));
    }
    return holders;
  }

  private static List<MediaSourceList.MediaSourceHolder> createFakeHoldersWithSources(
      boolean useLazyPreparation, MediaSource... sources) {
    List<MediaSourceList.MediaSourceHolder> holders = new ArrayList<>();
    for (MediaSource mediaSource : sources) {
      holders.add(
          new MediaSourceList.MediaSourceHolder(
              mediaSource, /* useLazyPreparation= */ useLazyPreparation));
    }
    return holders;
  }
}
