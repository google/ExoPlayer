/*
 * Copyright (C) 2018 The Android Open Source Project
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
package androidx.media3.cast;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Timeline.Window;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.TimelineAsserts;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link CastTimelineTracker}. */
@RunWith(AndroidJUnit4.class)
public class CastTimelineTrackerTest {

  private static final long DURATION_2_MS = 2000;
  private static final long DURATION_3_MS = 3000;
  private static final long DURATION_4_MS = 4000;
  private static final long DURATION_5_MS = 5000;

  private MediaItemConverter mediaItemConverter;
  private CastTimelineTracker castTimelineTracker;

  @Before
  public void init() {
    mediaItemConverter = new DefaultMediaItemConverter();
    castTimelineTracker = new CastTimelineTracker(mediaItemConverter);
  }

  /** Tests that duration of the current media info is correctly propagated to the timeline. */
  @Test
  public void getCastTimelinePersistsDuration() {
    CastTimelineTracker tracker = new CastTimelineTracker(new DefaultMediaItemConverter());

    RemoteMediaClient remoteMediaClient =
        mockRemoteMediaClient(
            /* itemIds= */ new int[] {1, 2, 3, 4, 5},
            /* currentItemId= */ 2,
            /* currentDurationMs= */ DURATION_2_MS);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(remoteMediaClient),
        C.TIME_UNSET,
        Util.msToUs(DURATION_2_MS),
        C.TIME_UNSET,
        C.TIME_UNSET,
        C.TIME_UNSET);

    remoteMediaClient =
        mockRemoteMediaClient(
            /* itemIds= */ new int[] {1, 2, 3},
            /* currentItemId= */ 3,
            /* currentDurationMs= */ DURATION_3_MS);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(remoteMediaClient),
        C.TIME_UNSET,
        Util.msToUs(DURATION_2_MS),
        Util.msToUs(DURATION_3_MS));

    remoteMediaClient =
        mockRemoteMediaClient(
            /* itemIds= */ new int[] {1, 3},
            /* currentItemId= */ 3,
            /* currentDurationMs= */ DURATION_3_MS);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(remoteMediaClient), C.TIME_UNSET, Util.msToUs(DURATION_3_MS));

    remoteMediaClient =
        mockRemoteMediaClient(
            /* itemIds= */ new int[] {1, 2, 3, 4, 5},
            /* currentItemId= */ 4,
            /* currentDurationMs= */ DURATION_4_MS);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(remoteMediaClient),
        C.TIME_UNSET,
        C.TIME_UNSET,
        Util.msToUs(DURATION_3_MS),
        Util.msToUs(DURATION_4_MS),
        C.TIME_UNSET);

    remoteMediaClient =
        mockRemoteMediaClient(
            /* itemIds= */ new int[] {1, 2, 3, 4, 5},
            /* currentItemId= */ 5,
            /* currentDurationMs= */ DURATION_5_MS);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(remoteMediaClient),
        C.TIME_UNSET,
        C.TIME_UNSET,
        Util.msToUs(DURATION_3_MS),
        Util.msToUs(DURATION_4_MS),
        Util.msToUs(DURATION_5_MS));
  }

  @Test
  public void getCastTimeline_onMediaItemsSet_correctMediaItemsInTimeline() {
    RemoteMediaClient mockRemoteMediaClient = mock(RemoteMediaClient.class);
    MediaQueue mockMediaQueue = mock(MediaQueue.class);
    MediaStatus mockMediaStatus = mock(MediaStatus.class);
    ImmutableList<MediaItem> playlistMediaItems =
        ImmutableList.of(createMediaItem(0), createMediaItem(1));
    MediaQueueItem[] playlistMediaQueueItems =
        new MediaQueueItem[] {
          createMediaQueueItem(playlistMediaItems.get(0), 0),
          createMediaQueueItem(playlistMediaItems.get(1), 1)
        };
    castTimelineTracker.onMediaItemsSet(playlistMediaItems, playlistMediaQueueItems);
    // Mock remote media client state after adding two items.
    when(mockRemoteMediaClient.getMediaQueue()).thenReturn(mockMediaQueue);
    when(mockMediaQueue.getItemIds()).thenReturn(new int[] {0, 1});
    when(mockRemoteMediaClient.getMediaStatus()).thenReturn(mockMediaStatus);
    when(mockMediaStatus.getCurrentItemId()).thenReturn(0);
    when(mockMediaStatus.getMediaInfo()).thenReturn(playlistMediaQueueItems[0].getMedia());
    when(mockMediaStatus.getQueueItems()).thenReturn(Arrays.asList(playlistMediaQueueItems));

    CastTimeline castTimeline = castTimelineTracker.getCastTimeline(mockRemoteMediaClient);

    assertThat(castTimeline.getWindowCount()).isEqualTo(2);
    assertThat(castTimeline.getWindow(/* windowIndex= */ 0, new Window()).mediaItem)
        .isEqualTo(playlistMediaItems.get(0));
    assertThat(castTimeline.getWindow(/* windowIndex= */ 1, new Window()).mediaItem)
        .isEqualTo(playlistMediaItems.get(1));

    MediaItem thirdMediaItem = createMediaItem(2);
    MediaQueueItem thirdMediaQueueItem = createMediaQueueItem(thirdMediaItem, 2);
    castTimelineTracker.onMediaItemsSet(
        ImmutableList.of(thirdMediaItem), new MediaQueueItem[] {thirdMediaQueueItem});
    // Mock remote media client state after a single item overrides the previous playlist.
    when(mockMediaQueue.getItemIds()).thenReturn(new int[] {2});
    when(mockMediaStatus.getCurrentItemId()).thenReturn(2);
    when(mockMediaStatus.getMediaInfo()).thenReturn(thirdMediaQueueItem.getMedia());
    when(mockMediaStatus.getQueueItems()).thenReturn(ImmutableList.of(thirdMediaQueueItem));

    castTimeline = castTimelineTracker.getCastTimeline(mockRemoteMediaClient);

    assertThat(castTimeline.getWindowCount()).isEqualTo(1);
    assertThat(castTimeline.getWindow(/* windowIndex= */ 0, new Window()).mediaItem)
        .isEqualTo(thirdMediaItem);
  }

  @Test
  public void getCastTimeline_onMediaItemsAdded_correctMediaItemsInTimeline() {
    RemoteMediaClient mockRemoteMediaClient = mock(RemoteMediaClient.class);
    MediaQueue mockMediaQueue = mock(MediaQueue.class);
    MediaStatus mockMediaStatus = mock(MediaStatus.class);
    ImmutableList<MediaItem> playlistMediaItems =
        ImmutableList.of(createMediaItem(0), createMediaItem(1));
    MediaQueueItem[] playlistQueueItems =
        new MediaQueueItem[] {
          createMediaQueueItem(playlistMediaItems.get(0), /* uid= */ 0),
          createMediaQueueItem(playlistMediaItems.get(1), /* uid= */ 1)
        };
    ImmutableList<MediaItem> secondPlaylistMediaItems =
        new ImmutableList.Builder<MediaItem>()
            .addAll(playlistMediaItems)
            .add(createMediaItem(2))
            .build();
    castTimelineTracker.onMediaItemsAdded(playlistMediaItems, playlistQueueItems);
    when(mockRemoteMediaClient.getMediaQueue()).thenReturn(mockMediaQueue);
    when(mockRemoteMediaClient.getMediaStatus()).thenReturn(mockMediaStatus);
    // Mock remote media client state after two items have been added.
    when(mockMediaQueue.getItemIds()).thenReturn(new int[] {0, 1});
    when(mockMediaStatus.getCurrentItemId()).thenReturn(0);
    when(mockMediaStatus.getMediaInfo()).thenReturn(playlistQueueItems[0].getMedia());
    when(mockMediaStatus.getQueueItems()).thenReturn(Arrays.asList(playlistQueueItems));

    CastTimeline castTimeline = castTimelineTracker.getCastTimeline(mockRemoteMediaClient);

    assertThat(castTimeline.getWindowCount()).isEqualTo(2);
    assertThat(castTimeline.getWindow(/* windowIndex= */ 0, new Window()).mediaItem)
        .isEqualTo(playlistMediaItems.get(0));
    assertThat(castTimeline.getWindow(/* windowIndex= */ 1, new Window()).mediaItem)
        .isEqualTo(playlistMediaItems.get(1));

    // Mock remote media client state after adding a third item.
    List<MediaQueueItem> playlistThreeQueueItems =
        new ArrayList<>(Arrays.asList(playlistQueueItems));
    playlistThreeQueueItems.add(createMediaQueueItem(secondPlaylistMediaItems.get(2), 2));
    castTimelineTracker.onMediaItemsAdded(
        secondPlaylistMediaItems, playlistThreeQueueItems.toArray(new MediaQueueItem[0]));
    when(mockMediaQueue.getItemIds()).thenReturn(new int[] {0, 1, 2});
    when(mockMediaStatus.getQueueItems()).thenReturn(playlistThreeQueueItems);

    castTimeline = castTimelineTracker.getCastTimeline(mockRemoteMediaClient);

    assertThat(castTimeline.getWindowCount()).isEqualTo(3);
    assertThat(castTimeline.getWindow(/* windowIndex= */ 0, new Window()).mediaItem)
        .isEqualTo(secondPlaylistMediaItems.get(0));
    assertThat(castTimeline.getWindow(/* windowIndex= */ 1, new Window()).mediaItem)
        .isEqualTo(secondPlaylistMediaItems.get(1));
    assertThat(castTimeline.getWindow(/* windowIndex= */ 2, new Window()).mediaItem)
        .isEqualTo(secondPlaylistMediaItems.get(2));
  }

  @Test
  public void getCastTimeline_itemsRemoved_correctMediaItemsInTimelineAndMapCleanedUp() {
    RemoteMediaClient mockRemoteMediaClient = mock(RemoteMediaClient.class);
    MediaQueue mockMediaQueue = mock(MediaQueue.class);
    MediaStatus mockMediaStatus = mock(MediaStatus.class);
    ImmutableList<MediaItem> playlistMediaItems =
        ImmutableList.of(createMediaItem(0), createMediaItem(1));
    MediaQueueItem[] initialPlaylistTwoQueueItems =
        new MediaQueueItem[] {
          createMediaQueueItem(playlistMediaItems.get(0), 0),
          createMediaQueueItem(playlistMediaItems.get(1), 1)
        };
    castTimelineTracker.onMediaItemsSet(playlistMediaItems, initialPlaylistTwoQueueItems);
    when(mockRemoteMediaClient.getMediaQueue()).thenReturn(mockMediaQueue);
    when(mockRemoteMediaClient.getMediaStatus()).thenReturn(mockMediaStatus);
    // Mock remote media client state with two items in the queue.
    when(mockMediaQueue.getItemIds()).thenReturn(new int[] {0, 1});
    when(mockMediaStatus.getCurrentItemId()).thenReturn(0);
    when(mockMediaStatus.getMediaInfo()).thenReturn(initialPlaylistTwoQueueItems[0].getMedia());
    when(mockMediaStatus.getQueueItems()).thenReturn(Arrays.asList(initialPlaylistTwoQueueItems));

    CastTimeline castTimeline = castTimelineTracker.getCastTimeline(mockRemoteMediaClient);

    assertThat(castTimeline.getWindowCount()).isEqualTo(2);
    assertThat(castTimelineTracker.mediaItemsByContentId).hasSize(2);

    // Mock remote media client state after the first item has been removed.
    when(mockMediaQueue.getItemIds()).thenReturn(new int[] {1});
    when(mockMediaStatus.getCurrentItemId()).thenReturn(1);
    when(mockMediaStatus.getMediaInfo()).thenReturn(initialPlaylistTwoQueueItems[1].getMedia());
    when(mockMediaStatus.getQueueItems())
        .thenReturn(ImmutableList.of(initialPlaylistTwoQueueItems[1]));

    castTimeline = castTimelineTracker.getCastTimeline(mockRemoteMediaClient);

    assertThat(castTimeline.getWindowCount()).isEqualTo(1);
    assertThat(castTimeline.getWindow(/* windowIndex= */ 0, new Window()).mediaItem)
        .isEqualTo(playlistMediaItems.get(1));
    // Assert that the removed item has been removed from the content ID map.
    assertThat(castTimelineTracker.mediaItemsByContentId).hasSize(1);

    // Mock remote media client state for empty queue.
    when(mockRemoteMediaClient.getMediaStatus()).thenReturn(null);
    when(mockMediaQueue.getItemIds()).thenReturn(new int[0]);
    when(mockMediaStatus.getCurrentItemId()).thenReturn(MediaQueueItem.INVALID_ITEM_ID);
    when(mockMediaStatus.getMediaInfo()).thenReturn(null);
    when(mockMediaStatus.getQueueItems()).thenReturn(ImmutableList.of());

    castTimeline = castTimelineTracker.getCastTimeline(mockRemoteMediaClient);

    assertThat(castTimeline.getWindowCount()).isEqualTo(0);
    // Queue is not emptied when remote media client is empty. See [Internal ref: b/128825216].
    assertThat(castTimelineTracker.mediaItemsByContentId).hasSize(1);
  }

  @Test
  public void getCastTimeline_mediaStatusIsNull_returnsEmptyTimeline() {
    RemoteMediaClient mockRemoteMediaClient = mock(RemoteMediaClient.class);
    MediaQueue mediaQueue = mock(MediaQueue.class);
    when(mockRemoteMediaClient.getMediaQueue()).thenReturn(mediaQueue);
    when(mediaQueue.getItemIds()).thenReturn(new int[0]);
    when(mockRemoteMediaClient.getMediaStatus()).thenReturn(null);

    assertThat(castTimelineTracker.getCastTimeline(mockRemoteMediaClient).isEmpty()).isTrue();
  }

  @Test
  public void getCastTimeline_mediaInfoIsNull_returnsEmptyTimeline() {
    RemoteMediaClient mockRemoteMediaClient = mock(RemoteMediaClient.class);
    MediaQueue mediaQueue = mock(MediaQueue.class);
    when(mockRemoteMediaClient.getMediaQueue()).thenReturn(mediaQueue);
    when(mediaQueue.getItemIds()).thenReturn(new int[0]);
    MediaStatus mediaStatus = mock(MediaStatus.class);
    when(mockRemoteMediaClient.getMediaStatus()).thenReturn(mediaStatus);
    when(mediaStatus.getMediaInfo()).thenReturn(null);

    assertThat(castTimelineTracker.getCastTimeline(mockRemoteMediaClient).isEmpty()).isTrue();
  }

  private MediaItem createMediaItem(int uid) {
    return new MediaItem.Builder()
        .setUri("http://www.google.com/" + uid)
        .setMimeType(MimeTypes.AUDIO_MPEG)
        .setTag(uid)
        .build();
  }

  private MediaQueueItem createMediaQueueItem(MediaItem mediaItem, int uid) {
    return new MediaQueueItem.Builder(mediaItemConverter.toMediaQueueItem(mediaItem))
        .setItemId(uid)
        .build();
  }

  private static RemoteMediaClient mockRemoteMediaClient(
      int[] itemIds, int currentItemId, long currentDurationMs) {
    RemoteMediaClient remoteMediaClient = mock(RemoteMediaClient.class);
    MediaStatus status = mock(MediaStatus.class);
    when(status.getQueueItems()).thenReturn(Collections.emptyList());
    when(remoteMediaClient.getMediaStatus()).thenReturn(status);
    when(status.getMediaInfo()).thenReturn(getMediaInfo(currentDurationMs));
    when(status.getCurrentItemId()).thenReturn(currentItemId);
    MediaQueue mediaQueue = mockMediaQueue(itemIds);
    when(remoteMediaClient.getMediaQueue()).thenReturn(mediaQueue);
    return remoteMediaClient;
  }

  private static MediaQueue mockMediaQueue(int[] itemIds) {
    MediaQueue mediaQueue = mock(MediaQueue.class);
    when(mediaQueue.getItemIds()).thenReturn(itemIds);
    return mediaQueue;
  }

  private static MediaInfo getMediaInfo(long durationMs) {
    return new MediaInfo.Builder(/* contentId= */ "")
        .setStreamDuration(durationMs)
        .setContentType(MimeTypes.APPLICATION_MP4)
        .setStreamType(MediaInfo.STREAM_TYPE_NONE)
        .build();
  }
}
