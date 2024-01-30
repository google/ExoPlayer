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
package androidx.media3.transformer;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;

/** A listener for {@link MediaItem} changes in the {@linkplain SampleExporter sample exporters}. */
/* package */ interface OnMediaItemChangedListener {

  /**
   * Called when the {@link MediaItem} whose samples are passed to the {@link SampleExporter}
   * changes.
   *
   * @param editedMediaItem The {@link MediaItem} with the transformations to apply to it.
   * @param durationUs The duration of the {@link MediaItem}, in microseconds.
   * @param decodedFormat The {@link Format} decoded from the {@link MediaItem} track, which
   *     represents the samples output from the {@link SampleExporter}. {@code null} if no such
   *     track was decoded.
   * @param isLast Whether the {@link MediaItem} is the last one passed to the {@link
   *     SampleExporter}.
   */
  void onMediaItemChanged(
      EditedMediaItem editedMediaItem,
      long durationUs,
      @Nullable Format decodedFormat,
      boolean isLast);
}
