/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.source.hls.HlsTrackMetadataEntry.VariantInfo;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test for {@link HlsTrackMetadataEntry}. */
@RunWith(AndroidJUnit4.class)
public class HlsTrackMetadataEntryTest {

  @Test
  public void variantInfo_parcelRoundTrip_isEqual() {
    VariantInfo variantInfoToParcel =
        new VariantInfo(
            /* averageBitrate= */ 1024,
            /* peakBitrate= */ 2048,
            "videoGroupId",
            "audioGroupId",
            "subtitleGroupId",
            "captionGroupId");

    Parcel parcel = Parcel.obtain();
    variantInfoToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    VariantInfo variantInfoFromParcel = VariantInfo.CREATOR.createFromParcel(parcel);
    assertThat(variantInfoFromParcel).isEqualTo(variantInfoToParcel);

    parcel.recycle();
  }
}
