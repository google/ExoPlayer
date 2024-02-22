/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.extractor.mp4;

import com.google.android.exoplayer2.extractor.SniffFailure;

/**
 * {@link SniffFailure} indicating the file's fragmented flag is incompatible with this {@link
 * com.google.android.exoplayer2.extractor.Extractor}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class IncorrectFragmentationSniffFailure implements SniffFailure {

  public static final IncorrectFragmentationSniffFailure FILE_FRAGMENTED =
      new IncorrectFragmentationSniffFailure(/* fileIsFragmented= */ true);

  public static final IncorrectFragmentationSniffFailure FILE_NOT_FRAGMENTED =
      new IncorrectFragmentationSniffFailure(/* fileIsFragmented= */ false);

  public final boolean fileIsFragmented;

  private IncorrectFragmentationSniffFailure(boolean fileIsFragmented) {
    this.fileIsFragmented = fileIsFragmented;
  }
}
