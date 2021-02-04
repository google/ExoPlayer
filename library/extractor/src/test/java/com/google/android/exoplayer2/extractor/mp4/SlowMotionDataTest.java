/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.mp4;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.metadata.mp4.SlowMotionData;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link SlowMotionData} */
@RunWith(AndroidJUnit4.class)
public class SlowMotionDataTest {

  @Test
  public void parcelable() {
    List<SlowMotionData.Segment> segments = new ArrayList<>();
    segments.add(
        new SlowMotionData.Segment(
            /* startTimeMs= */ 1000, /* endTimeMs= */ 2000, /* speedDivisor= */ 4));
    segments.add(
        new SlowMotionData.Segment(
            /* startTimeMs= */ 2600, /* endTimeMs= */ 4000, /* speedDivisor= */ 8));
    segments.add(
        new SlowMotionData.Segment(
            /* startTimeMs= */ 8765, /* endTimeMs= */ 12485, /* speedDivisor= */ 16));

    SlowMotionData slowMotionDataToParcel = new SlowMotionData(segments);
    Parcel parcel = Parcel.obtain();
    slowMotionDataToParcel.writeToParcel(parcel, /* flags= */ 0);
    parcel.setDataPosition(0);

    SlowMotionData slowMotionDataFromParcel = SlowMotionData.CREATOR.createFromParcel(parcel);
    assertThat(slowMotionDataFromParcel).isEqualTo(slowMotionDataToParcel);

    parcel.recycle();
  }

  @Test
  public void segment_parcelable() {
    SlowMotionData.Segment segmentToParcel =
        new SlowMotionData.Segment(
            /* startTimeMs= */ 1000, /* endTimeMs= */ 2000, /* speedDivisor= */ 4);

    Parcel parcel = Parcel.obtain();
    segmentToParcel.writeToParcel(parcel, /* flags= */ 0);
    parcel.setDataPosition(0);

    SlowMotionData.Segment segmentFromParcel =
        SlowMotionData.Segment.CREATOR.createFromParcel(parcel);
    assertThat(segmentFromParcel).isEqualTo(segmentToParcel);

    parcel.recycle();
  }
}
