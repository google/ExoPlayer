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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.extractor.SniffFailure;
import com.google.common.primitives.ImmutableIntArray;

/**
 * A {@link SniffFailure} indicating none of the brands declared in the {@code ftyp} box of the MP4
 * file are supported (see ISO 14496-12:2012 section 4.3).
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class UnsupportedBrandsSniffFailure implements SniffFailure {

  /** The {@code major_brand} from the {@code ftyp} box. */
  public final int majorBrand;

  /** The {@code compatible_brands} list from the {@code ftyp} box. */
  public final ImmutableIntArray compatibleBrands;

  public UnsupportedBrandsSniffFailure(int majorBrand, @Nullable int[] compatibleBrands) {
    this.majorBrand = majorBrand;
    this.compatibleBrands =
        compatibleBrands != null
            ? ImmutableIntArray.copyOf(compatibleBrands)
            : ImmutableIntArray.of();
  }
}
