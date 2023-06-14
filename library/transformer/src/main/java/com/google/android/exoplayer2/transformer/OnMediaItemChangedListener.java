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
package com.google.android.exoplayer2.transformer;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;

/**
 * A listener for {@link MediaItem} changes in the {@linkplain SamplePipeline sample pipelines}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ interface OnMediaItemChangedListener {

  /**
   * Called when the {@link MediaItem} whose samples are passed to the {@link SamplePipeline}
   * changes.
   *
   * @param editedMediaItem The {@link MediaItem} with the transformations to apply to it.
   * @param durationUs The duration of the {@link MediaItem}, in microseconds.
   * @param trackFormat The {@link Format} extracted (and possibly decoded) from the {@link
   *     MediaItem} track, which represents the samples input to the {@link SamplePipeline}. {@code
   *     null} if no such track was extracted.
   * @param isLast Whether the {@link MediaItem} is the last one passed to the {@link
   *     SamplePipeline}.
   */
  void onMediaItemChanged(
      EditedMediaItem editedMediaItem,
      long durationUs,
      @Nullable Format trackFormat,
      boolean isLast);
}
