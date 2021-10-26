/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.exoplayer;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.drm.DrmSession;

/** Holds a {@link Format}. */
@UnstableApi
public final class FormatHolder {

  /** An accompanying context for decrypting samples in the format. */
  @Nullable public DrmSession drmSession;

  /** The held {@link Format}. */
  @Nullable public Format format;

  /** Clears the holder. */
  public void clear() {
    drmSession = null;
    format = null;
  }
}
