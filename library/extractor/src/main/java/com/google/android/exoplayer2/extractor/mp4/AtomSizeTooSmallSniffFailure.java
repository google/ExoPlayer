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
 * A {@link SniffFailure} indicating an atom declares a size that is too small for the header fields
 * that must present for the given type.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class AtomSizeTooSmallSniffFailure implements SniffFailure {
  public final int atomType;
  public final long atomSize;
  public final int minimumHeaderSize;

  public AtomSizeTooSmallSniffFailure(int atomType, long atomSize, int minimumHeaderSize) {
    this.atomType = atomType;
    this.atomSize = atomSize;
    this.minimumHeaderSize = minimumHeaderSize;
  }
}
