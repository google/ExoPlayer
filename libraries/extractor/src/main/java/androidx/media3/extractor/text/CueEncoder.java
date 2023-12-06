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
package androidx.media3.extractor.text;

import android.os.Bundle;
import android.os.Parcel;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.BundleCollectionUtil;
import androidx.media3.common.util.UnstableApi;
import java.util.ArrayList;
import java.util.List;

/** Encodes data that can be decoded by {@link CueDecoder}. */
@UnstableApi
public final class CueEncoder {

  /**
   * Encodes a {@link Cue} list and duration to a byte array that can be decoded by {@link
   * CueDecoder#decode}.
   *
   * @param cues Cues to be encoded.
   * @param durationUs Duration to be encoded, in microseconds.
   * @return The serialized byte array.
   */
  public byte[] encode(List<Cue> cues, long durationUs) {
    ArrayList<Bundle> bundledCues =
        BundleCollectionUtil.toBundleArrayList(cues, Cue::toSerializableBundle);
    Bundle allCuesBundle = new Bundle();
    allCuesBundle.putParcelableArrayList(CueDecoder.BUNDLE_FIELD_CUES, bundledCues);
    allCuesBundle.putLong(CueDecoder.BUNDLE_FIELD_DURATION_US, durationUs);
    Parcel parcel = Parcel.obtain();
    parcel.writeBundle(allCuesBundle);
    byte[] bytes = parcel.marshall();
    parcel.recycle();

    return bytes;
  }
}
