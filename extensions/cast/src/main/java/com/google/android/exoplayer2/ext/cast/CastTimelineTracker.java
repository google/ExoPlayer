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

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Creates {@link CastTimeline}s from cast receiver app media status.
 *
 * <p>This class keeps track of the duration reported by the current item to fill any missing
 * durations in the media queue items [See internal: b/65152553].
 */
/* package */ final class CastTimelineTracker {

  private final HashMap<String, Long> contentIdToDurationUsMap;
  private final HashSet<String> scratchContentIdSet;

  public CastTimelineTracker() {
    contentIdToDurationUsMap = new HashMap<>();
    scratchContentIdSet = new HashSet<>();
  }

  /**
   * Returns a {@link CastTimeline} that represent the given {@code status}.
   *
   * @param status The Cast media status.
   * @return A {@link CastTimeline} that represent the given {@code status}.
   */
  public CastTimeline getCastTimeline(MediaStatus status) {
    MediaInfo mediaInfo = status.getMediaInfo();
    List<MediaQueueItem> items = status.getQueueItems();
    removeUnusedDurationEntries(items);

    if (mediaInfo != null) {
      String contentId = mediaInfo.getContentId();
      long durationUs = CastUtils.getStreamDurationUs(mediaInfo);
      contentIdToDurationUsMap.put(contentId, durationUs);
    }
    return new CastTimeline(items, contentIdToDurationUsMap);
  }

  private void removeUnusedDurationEntries(List<MediaQueueItem> items) {
    scratchContentIdSet.clear();
    for (MediaQueueItem item : items) {
      scratchContentIdSet.add(item.getMedia().getContentId());
    }
    contentIdToDurationUsMap.keySet().retainAll(scratchContentIdSet);
  }
}
