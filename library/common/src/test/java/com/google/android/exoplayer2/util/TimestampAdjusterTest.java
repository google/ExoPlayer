/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import static com.google.android.exoplayer2.util.TimestampAdjuster.MODE_NO_OFFSET;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link TimestampAdjuster}. */
@RunWith(AndroidJUnit4.class)
public class TimestampAdjusterTest {

  @Test
  public void adjustSampleTimestamp_fromZero() {
    TimestampAdjuster adjuster = new TimestampAdjuster(/* firstSampleTimestampUs= */ 0);
    long firstAdjustedTimestampUs = adjuster.adjustSampleTimestamp(/* timeUs= */ 2000);
    long secondAdjustedTimestampUs = adjuster.adjustSampleTimestamp(/* timeUs= */ 6000);

    assertThat(firstAdjustedTimestampUs).isEqualTo(0);
    assertThat(secondAdjustedTimestampUs).isEqualTo(4000);
  }

  @Test
  public void adjustSampleTimestamp_fromNonZero() {
    TimestampAdjuster adjuster = new TimestampAdjuster(/* firstSampleTimestampUs= */ 1000);
    long firstAdjustedTimestampUs = adjuster.adjustSampleTimestamp(/* timeUs= */ 2000);
    long secondAdjustedTimestampUs = adjuster.adjustSampleTimestamp(/* timeUs= */ 6000);

    assertThat(firstAdjustedTimestampUs).isEqualTo(1000);
    assertThat(secondAdjustedTimestampUs).isEqualTo(5000);
  }

  @Test
  public void adjustSampleTimestamp_noOffset() {
    TimestampAdjuster adjuster =
        new TimestampAdjuster(/* firstSampleTimestampUs= */ MODE_NO_OFFSET);
    long firstAdjustedTimestampUs = adjuster.adjustSampleTimestamp(/* timeUs= */ 2000);
    long secondAdjustedTimestampUs = adjuster.adjustSampleTimestamp(/* timeUs= */ 6000);

    assertThat(firstAdjustedTimestampUs).isEqualTo(2000);
    assertThat(secondAdjustedTimestampUs).isEqualTo(6000);
  }

  @Test
  public void adjustSampleTimestamp_afterResetToNoOffset() {
    TimestampAdjuster adjuster = new TimestampAdjuster(/* firstSampleTimestampUs= */ 0);
    // Let the adjuster establish an offset, to make sure that reset really clears it.
    adjuster.adjustSampleTimestamp(/* timeUs= */ 1000);
    adjuster.reset(/* firstSampleTimestampUs= */ MODE_NO_OFFSET);
    long firstAdjustedTimestampUs = adjuster.adjustSampleTimestamp(/* timeUs= */ 2000);
    long secondAdjustedTimestampUs = adjuster.adjustSampleTimestamp(/* timeUs= */ 6000);

    assertThat(firstAdjustedTimestampUs).isEqualTo(2000);
    assertThat(secondAdjustedTimestampUs).isEqualTo(6000);
  }

  @Test
  public void adjustSampleTimestamp_afterResetToDifferentStartTime() {
    TimestampAdjuster adjuster = new TimestampAdjuster(/* firstSampleTimestampUs= */ 0);
    // Let the adjuster establish an offset, to make sure that reset really clears it.
    adjuster.adjustSampleTimestamp(/* timeUs= */ 1000);
    adjuster.reset(/* firstSampleTimestampUs= */ 5000);
    long firstAdjustedTimestampUs = adjuster.adjustSampleTimestamp(/* timeUs= */ 2000);
    long secondAdjustedTimestampUs = adjuster.adjustSampleTimestamp(/* timeUs= */ 6000);

    assertThat(firstAdjustedTimestampUs).isEqualTo(5000);
    assertThat(secondAdjustedTimestampUs).isEqualTo(9000);
  }
}
