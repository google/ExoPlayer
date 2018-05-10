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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.TimelineAsserts;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link CastTimelineTracker}. */
@RunWith(RobolectricTestRunner.class)
public class CastTimelineTrackerTest {

  private static final long DURATION_1_MS = 1000;
  private static final long DURATION_2_MS = 2000;
  private static final long DURATION_3_MS = 3000;
  private static final long DURATION_4_MS = 4000;
  private static final long DURATION_5_MS = 5000;

  /** Tests that duration of the current media info is correctly propagated to the timeline. */
  @Test
  public void testGetCastTimeline() {
    MediaInfo mediaInfo;
    MediaStatus status =
        mockMediaStatus(
            new int[] {1, 2, 3},
            new String[] {"contentId1", "contentId2", "contentId3"},
            new long[] {DURATION_1_MS, MediaInfo.UNKNOWN_DURATION, MediaInfo.UNKNOWN_DURATION});

    CastTimelineTracker tracker = new CastTimelineTracker();
    mediaInfo = getMediaInfo("contentId1", DURATION_1_MS);
    Mockito.when(status.getMediaInfo()).thenReturn(mediaInfo);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(status), C.msToUs(DURATION_1_MS), C.TIME_UNSET, C.TIME_UNSET);

    mediaInfo = getMediaInfo("contentId3", DURATION_3_MS);
    Mockito.when(status.getMediaInfo()).thenReturn(mediaInfo);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(status),
        C.msToUs(DURATION_1_MS),
        C.TIME_UNSET,
        C.msToUs(DURATION_3_MS));

    mediaInfo = getMediaInfo("contentId2", DURATION_2_MS);
    Mockito.when(status.getMediaInfo()).thenReturn(mediaInfo);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(status),
        C.msToUs(DURATION_1_MS),
        C.msToUs(DURATION_2_MS),
        C.msToUs(DURATION_3_MS));

    MediaStatus newStatus =
        mockMediaStatus(
            new int[] {4, 1, 5, 3},
            new String[] {"contentId4", "contentId1", "contentId5", "contentId3"},
            new long[] {
              MediaInfo.UNKNOWN_DURATION,
              MediaInfo.UNKNOWN_DURATION,
              DURATION_5_MS,
              MediaInfo.UNKNOWN_DURATION
            });
    mediaInfo = getMediaInfo("contentId5", DURATION_5_MS);
    Mockito.when(newStatus.getMediaInfo()).thenReturn(mediaInfo);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(newStatus),
        C.TIME_UNSET,
        C.msToUs(DURATION_1_MS),
        C.msToUs(DURATION_5_MS),
        C.msToUs(DURATION_3_MS));

    mediaInfo = getMediaInfo("contentId3", DURATION_3_MS);
    Mockito.when(newStatus.getMediaInfo()).thenReturn(mediaInfo);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(newStatus),
        C.TIME_UNSET,
        C.msToUs(DURATION_1_MS),
        C.msToUs(DURATION_5_MS),
        C.msToUs(DURATION_3_MS));

    mediaInfo = getMediaInfo("contentId4", DURATION_4_MS);
    Mockito.when(newStatus.getMediaInfo()).thenReturn(mediaInfo);
    TimelineAsserts.assertPeriodDurations(
        tracker.getCastTimeline(newStatus),
        C.msToUs(DURATION_4_MS),
        C.msToUs(DURATION_1_MS),
        C.msToUs(DURATION_5_MS),
        C.msToUs(DURATION_3_MS));
  }

  private static MediaStatus mockMediaStatus(
      int[] itemIds, String[] contentIds, long[] durationsMs) {
    ArrayList<MediaQueueItem> items = new ArrayList<>();
    for (int i = 0; i < contentIds.length; i++) {
      MediaInfo mediaInfo = getMediaInfo(contentIds[i], durationsMs[i]);
      MediaQueueItem item = Mockito.mock(MediaQueueItem.class);
      Mockito.when(item.getMedia()).thenReturn(mediaInfo);
      Mockito.when(item.getItemId()).thenReturn(itemIds[i]);
      items.add(item);
    }
    MediaStatus status = Mockito.mock(MediaStatus.class);
    Mockito.when(status.getQueueItems()).thenReturn(items);
    return status;
  }

  private static MediaInfo getMediaInfo(String contentId, long durationMs) {
    return new MediaInfo.Builder(contentId)
        .setStreamDuration(durationMs)
        .setContentType(MimeTypes.APPLICATION_MP4)
        .setStreamType(MediaInfo.STREAM_TYPE_NONE)
        .build();
  }
}
