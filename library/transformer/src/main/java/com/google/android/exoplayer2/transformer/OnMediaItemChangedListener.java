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

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;

/** A listener for {@link MediaItem} changes in the {@linkplain SamplePipeline sample pipelines}. */
/* package */ interface OnMediaItemChangedListener {

  /**
   * Called when the {@link MediaItem} whose samples are passed to the {@link SamplePipeline}
   * changes.
   *
   * <p>Can be called from any thread.
   *
   * @param editedMediaItem The {@link MediaItem} with the transformations to apply to it.
   * @param trackFormat The {@link Format} of the {@link EditedMediaItem} track corresponding to the
   *     {@link SamplePipeline}.
   * @param mediaItemOffsetUs The offset to add to the presentation timestamps of the {@link
   *     EditedMediaItem} samples received by the {@link SamplePipeline}, in microseconds.
   */
  void onMediaItemChanged(
      EditedMediaItem editedMediaItem, Format trackFormat, long mediaItemOffsetUs);
}
