/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.SampleQueue;
import com.google.android.exoplayer2.source.TrackGroup;
import java.io.IOException;

/**
 * Thrown when it is not possible to map a {@link TrackGroup} to a {@link SampleQueue}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class SampleQueueMappingException extends IOException {

  /**
   * @param mimeType The MIME type of the track group whose mapping failed.
   */
  public SampleQueueMappingException(@Nullable String mimeType) {
    super("Unable to bind a sample queue to TrackGroup with MIME type " + mimeType + ".");
  }
}
