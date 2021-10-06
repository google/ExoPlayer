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
package com.google.android.exoplayer2.ext.cast;

import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TimelineAsserts;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.media.MediaQueue;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

/** Tests for {@link CastTimelineTracker}. */
@RunWith(AndroidJUnit4.class)
public class CastTimelineTrackerTest {

  private static final long DURATION_2_MS = 2000;
  private static final long DURATION_3_MS = 3000;
  private static final long DURATION_4_MS = 4000;
  private static final long DURATION_5_MS = 5000;

  /** Tests that duration of the current media info is correctly propagated to the timeline. */
  @Test
  public void getCastTimelinePersistsDuration() {
    CastTimelineTracker tracker = new CastTimelineTracker();

    RemoteMediaClient remoteMediaClient =
        mockRemoteMediaClient(
            /* itemIds= */ new int[] {1, 2, 3, 4, 5},
            /* currentItemId= */ 2,
            /* currentDurationMs= */ DURATION_2_MS);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(remoteMediaClient),
        C.TIME_UNSET,
        C.msToUs(DURATION_2_MS),
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
        C.msToUs(DURATION_2_MS),
        C.msToUs(DURATION_3_MS));

    remoteMediaClient =
        mockRemoteMediaClient(
            /* itemIds= */ new int[] {1, 3},
            /* currentItemId= */ 3,
            /* currentDurationMs= */ DURATION_3_MS);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(remoteMediaClient), C.TIME_UNSET, C.msToUs(DURATION_3_MS));

    remoteMediaClient =
        mockRemoteMediaClient(
            /* itemIds= */ new int[] {1, 2, 3, 4, 5},
            /* currentItemId= */ 4,
            /* currentDurationMs= */ DURATION_4_MS);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(remoteMediaClient),
        C.TIME_UNSET,
        C.TIME_UNSET,
        C.msToUs(DURATION_3_MS),
        C.msToUs(DURATION_4_MS),
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
        C.msToUs(DURATION_3_MS),
        C.msToUs(DURATION_4_MS),
        C.msToUs(DURATION_5_MS));
  }

  private static RemoteMediaClient mockRemoteMediaClient(
      int[] itemIds, int currentItemId, long currentDurationMs) {
    RemoteMediaClient remoteMediaClient = Mockito.mock(RemoteMediaClient.class);
    MediaStatus status = Mockito.mock(MediaStatus.class);
    when(status.getQueueItems()).thenReturn(createMediaQueueItems(itemIds));
    when(remoteMediaClient.getMediaStatus()).thenReturn(status);
    when(status.getMediaInfo()).thenReturn(getMediaInfo(currentDurationMs));
    when(status.getCurrentItemId()).thenReturn(currentItemId);
    MediaQueue mediaQueue = mockMediaQueue(itemIds);
    when(remoteMediaClient.getMediaQueue()).thenReturn(mediaQueue);
    return remoteMediaClient;
  }

  private static MediaQueue mockMediaQueue(int[] itemIds) {
    MediaQueue mediaQueue = Mockito.mock(MediaQueue.class);
    when(mediaQueue.getItemIds()).thenReturn(itemIds);
    return mediaQueue;
  }

  private static List<MediaQueueItem> createMediaQueueItems(int[] itemIds) {
    ArrayList<MediaQueueItem> items = new ArrayList<>(itemIds.length);
    for (int itemId : itemIds) {
      MediaInfo mediaInfo = new MediaInfo.Builder(String.valueOf(itemId)).build();
      MediaQueueItem item = new MediaQueueItem.Builder(mediaInfo).setItemId(itemId).build();
      items.add(item);
    }
    return items;
  }

  private static MediaInfo getMediaInfo(long durationMs) {
    return new MediaInfo.Builder(/*contentId= */ "")
        .setStreamDuration(durationMs)
        .setContentType(MimeTypes.APPLICATION_MP4)
        .setStreamType(MediaInfo.STREAM_TYPE_NONE)
        .build();
  }
}
