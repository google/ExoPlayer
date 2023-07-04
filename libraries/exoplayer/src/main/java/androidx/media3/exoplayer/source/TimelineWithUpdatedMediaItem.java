/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.source;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;

/** A {@link Timeline} that overrides the {@link MediaItem}. */
@UnstableApi
public final class TimelineWithUpdatedMediaItem extends ForwardingTimeline {

  private final MediaItem updatedMediaItem;

  /**
   * Creates the timeline.
   *
   * @param timeline The wrapped {@link Timeline}.
   * @param mediaItem The {@link MediaItem} that replaced the original one in {@code timeline}.
   */
  public TimelineWithUpdatedMediaItem(Timeline timeline, MediaItem mediaItem) {
    super(timeline);
    this.updatedMediaItem = mediaItem;
  }

  @SuppressWarnings("deprecation") // Setting deprecated field for backward compatibility.
  @Override
  public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
    super.getWindow(windowIndex, window, defaultPositionProjectionUs);
    window.mediaItem = updatedMediaItem;
    window.tag =
        updatedMediaItem.localConfiguration != null
            ? updatedMediaItem.localConfiguration.tag
            : null;
    return window;
  }
}
