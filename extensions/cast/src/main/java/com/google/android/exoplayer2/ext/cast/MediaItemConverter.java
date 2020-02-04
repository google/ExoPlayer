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
package com.google.android.exoplayer2.ext.cast;

import com.google.android.gms.cast.MediaQueueItem;

/** Converts between {@link MediaItem} and the Cast SDK's {@link MediaQueueItem}. */
public interface MediaItemConverter {

  /**
   * Converts a {@link MediaItem} to a {@link MediaQueueItem}.
   *
   * @param mediaItem The {@link MediaItem}.
   * @return An equivalent {@link MediaQueueItem}.
   */
  MediaQueueItem toMediaQueueItem(MediaItem mediaItem);

  /**
   * Converts a {@link MediaQueueItem} to a {@link MediaItem}.
   *
   * @param mediaQueueItem The {@link MediaQueueItem}.
   * @return The equivalent {@link MediaItem}.
   */
  MediaItem toMediaItem(MediaQueueItem mediaQueueItem);
}
