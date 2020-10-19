/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.ext.media2;

import com.google.android.exoplayer2.MediaItem;

/**
 * Converts between {@link androidx.media2.common.MediaItem Media2 MediaItem} and {@link MediaItem
 * ExoPlayer MediaItem}.
 */
public interface MediaItemConverter {
  /**
   * Converts a {@link androidx.media2.common.MediaItem Media2 MediaItem} to an {@link MediaItem
   * ExoPlayer MediaItem}.
   */
  MediaItem convertToExoPlayerMediaItem(androidx.media2.common.MediaItem media2MediaItem);

  /**
   * Converts an {@link MediaItem ExoPlayer MediaItem} to a {@link androidx.media2.common.MediaItem
   * Media2 MediaItem}.
   */
  androidx.media2.common.MediaItem convertToMedia2MediaItem(MediaItem exoPlayerMediaItem);
}
