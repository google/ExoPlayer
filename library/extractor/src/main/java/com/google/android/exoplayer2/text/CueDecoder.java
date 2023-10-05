/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.text;

import android.os.Bundle;
import android.os.Parcel;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.BundleableUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes data encoded by {@link CueEncoder}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class CueDecoder {

  /** Key under which the list of cues is saved in the {@link Bundle}. */
  /* package */ static final String BUNDLE_FIELD_CUES = "c";

  /** Key under which the duration is saved in the {@link Bundle}. */
  /* package */ static final String BUNDLE_FIELD_DURATION_US = "d";

  /**
   * Decodes a byte array into a {@link CuesWithTiming} instance.
   *
   * @param startTimeUs The value for {@link CuesWithTiming#startTimeUs} (this is not encoded in
   *     {@code bytes}).
   * @param bytes Byte array produced by {@link CueEncoder#encode(List, long)}
   * @return Decoded {@link CuesWithTiming} instance.
   */
  public CuesWithTiming decode(long startTimeUs, byte[] bytes) {
    return decode(startTimeUs, bytes, /* offset= */ 0, bytes.length);
  }

  /**
   * Decodes a byte array into a {@link CuesWithTiming} instance.
   *
   * @param startTimeUs The value for {@link CuesWithTiming#startTimeUs} (this is not encoded in
   *     {@code bytes}).
   * @param bytes Byte array containing data produced by {@link CueEncoder#encode(List, long)}
   * @param offset The start index of cue data in {@code bytes}.
   * @param length The length of cue data in {@code bytes}.
   * @return Decoded {@link CuesWithTiming} instance.
   */
  public CuesWithTiming decode(long startTimeUs, byte[] bytes, int offset, int length) {
    Parcel parcel = Parcel.obtain();
    parcel.unmarshall(bytes, offset, length);
    parcel.setDataPosition(0);
    Bundle bundle = parcel.readBundle(Bundle.class.getClassLoader());
    parcel.recycle();
    ArrayList<Bundle> bundledCues =
        Assertions.checkNotNull(bundle.getParcelableArrayList(BUNDLE_FIELD_CUES));
    return new CuesWithTiming(
        BundleableUtil.fromBundleList(Cue.CREATOR, bundledCues),
        startTimeUs,
        bundle.getLong(BUNDLE_FIELD_DURATION_US));
  }
}
