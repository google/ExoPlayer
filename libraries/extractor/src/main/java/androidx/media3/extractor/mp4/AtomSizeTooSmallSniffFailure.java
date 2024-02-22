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
package androidx.media3.extractor.mp4;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.SniffFailure;

/**
 * A {@link SniffFailure} indicating an atom declares a size that is too small for the header fields
 * that must present for the given type.
 */
@UnstableApi
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
