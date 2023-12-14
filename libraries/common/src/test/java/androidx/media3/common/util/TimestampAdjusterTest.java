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
package androidx.media3.common.util;

import static androidx.media3.common.util.TimestampAdjuster.MODE_NO_OFFSET;
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

  @Test
  public void
      adjustTsTimestamp_closeToWraparoundFollowedBySlightlySmallerValue_doesNotAssumeWraparound() {
    // Init timestamp with a non-zero wraparound (multiple of 33-bit) and close to the next one.
    TimestampAdjuster adjuster =
        new TimestampAdjuster(TimestampAdjuster.ptsToUs(3 * 0x200000000L - 90_000));

    long firstAdjustedTimestampUs = adjuster.adjustTsTimestamp(0x200000000L - 90_000);
    long secondAdjustedTimestampUs = adjuster.adjustTsTimestamp(0x200000000L - 180_000);

    assertThat(secondAdjustedTimestampUs).isEqualTo(firstAdjustedTimestampUs - 1_000_000);
  }

  @Test
  public void
      adjustTsTimestamp_closeToWraparoundFollowedBySlightlyLargerValue_doesNotAssumeWraparound() {
    // Init timestamp with a non-zero wraparound (multiple of 33-bit) and close to the next one.
    TimestampAdjuster adjuster =
        new TimestampAdjuster(TimestampAdjuster.ptsToUs(3 * 0x200000000L - 90_000));

    long firstAdjustedTimestampUs = adjuster.adjustTsTimestamp(0x200000000L - 90_000);
    long secondAdjustedTimestampUs = adjuster.adjustTsTimestamp(0x200000000L - 45_000);

    assertThat(secondAdjustedTimestampUs).isEqualTo(firstAdjustedTimestampUs + 500_000);
  }

  @Test
  public void adjustTsTimestamp_closeToWraparoundFollowedByMuchSmallerValue_assumesWraparound() {
    // Init timestamp with a non-zero wraparound (multiple of 33-bit) and close to the next one.
    TimestampAdjuster adjuster =
        new TimestampAdjuster(TimestampAdjuster.ptsToUs(3 * 0x200000000L - 90_000));

    long firstAdjustedTimestampUs = adjuster.adjustTsTimestamp(0x200000000L - 90_000);
    long secondAdjustedTimestampUs = adjuster.adjustTsTimestamp(90_000);

    assertThat(secondAdjustedTimestampUs).isEqualTo(firstAdjustedTimestampUs + 2_000_000);
  }

  @Test
  public void
      adjustTsTimestamp_justBeyondWraparoundFollowedBySlightlySmallerValue_doesNotAssumeWraparound() {
    // Init timestamp with a non-zero wraparound (multiple of 33-bit), just beyond the last one.
    TimestampAdjuster adjuster =
        new TimestampAdjuster(TimestampAdjuster.ptsToUs(3 * 0x200000000L + 90_000));

    long firstAdjustedTimestampUs = adjuster.adjustTsTimestamp(90_000);
    long secondAdjustedTimestampUs = adjuster.adjustTsTimestamp(45_000);

    assertThat(secondAdjustedTimestampUs).isEqualTo(firstAdjustedTimestampUs - 500_000);
  }

  @Test
  public void
      adjustTsTimestamp_justBeyondWraparoundFollowedBySlightlyLargerValue_doesNotAssumeWraparound() {
    // Init timestamp with a non-zero wraparound (multiple of 33-bit), just beyond the last one.
    TimestampAdjuster adjuster =
        new TimestampAdjuster(TimestampAdjuster.ptsToUs(3 * 0x200000000L + 90_000));

    long firstAdjustedTimestampUs = adjuster.adjustTsTimestamp(90_000);
    long secondAdjustedTimestampUs = adjuster.adjustTsTimestamp(180_000);

    assertThat(secondAdjustedTimestampUs).isEqualTo(firstAdjustedTimestampUs + 1_000_000);
  }

  @Test
  public void adjustTsTimestamp_justBeyondWraparoundFollowedByMuchLargerValue_assumesWraparound() {
    // Init timestamp with a non-zero wraparound (multiple of 33-bit), just beyond the last one.
    TimestampAdjuster adjuster =
        new TimestampAdjuster(TimestampAdjuster.ptsToUs(3 * 0x200000000L + 90_000));

    long firstAdjustedTimestampUs = adjuster.adjustTsTimestamp(90_000);
    long secondAdjustedTimestampUs = adjuster.adjustTsTimestamp(0x200000000L - 90_000);

    assertThat(secondAdjustedTimestampUs).isEqualTo(firstAdjustedTimestampUs - 2_000_000);
  }

  @Test
  public void
      adjustTsTimestampGreaterThanPreviousTimestamp_closeToWraparoundFollowedBySlightlySmallerValue_assumesWraparound() {
    // Init timestamp with a non-zero wraparound (multiple of 33-bit) and close to the next one.
    TimestampAdjuster adjuster =
        new TimestampAdjuster(TimestampAdjuster.ptsToUs(3 * 0x200000000L - 90_000));

    long firstAdjustedTimestampUs = adjuster.adjustTsTimestamp(0x200000000L - 90_000);
    long secondAdjustedTimestampUs =
        adjuster.adjustTsTimestampGreaterThanPreviousTimestamp(0x200000000L - 180_000);

    assertThat(secondAdjustedTimestampUs - firstAdjustedTimestampUs).isGreaterThan(0x100000000L);
  }

  @Test
  public void
      adjustTsTimestampGreaterThanPreviousTimestamp_closeToWraparoundFollowedBySlightlyLargerValue_doesNotAssumeWraparound() {
    // Init timestamp with a non-zero wraparound (multiple of 33-bit) and close to the next one.
    TimestampAdjuster adjuster =
        new TimestampAdjuster(TimestampAdjuster.ptsToUs(3 * 0x200000000L - 90_000));

    long firstAdjustedTimestampUs = adjuster.adjustTsTimestamp(0x200000000L - 90_000);
    long secondAdjustedTimestampUs =
        adjuster.adjustTsTimestampGreaterThanPreviousTimestamp(0x200000000L - 45_000);

    assertThat(secondAdjustedTimestampUs).isEqualTo(firstAdjustedTimestampUs + 500_000);
  }

  @Test
  public void
      adjustTsTimestampGreaterThanPreviousTimestamp_closeToWraparoundFollowedByMuchSmallerValue_assumesWraparound() {
    // Init timestamp with a non-zero wraparound (multiple of 33-bit) and close to the next one.
    TimestampAdjuster adjuster =
        new TimestampAdjuster(TimestampAdjuster.ptsToUs(3 * 0x200000000L - 90_000));

    long firstAdjustedTimestampUs = adjuster.adjustTsTimestamp(0x200000000L - 90_000);
    long secondAdjustedTimestampUs = adjuster.adjustTsTimestampGreaterThanPreviousTimestamp(90_000);

    assertThat(secondAdjustedTimestampUs).isEqualTo(firstAdjustedTimestampUs + 2_000_000);
  }

  @Test
  public void
      adjustTsTimestampGreaterThanPreviousTimestamp_justBeyondWraparoundFollowedBySlightlySmallerValue_assumesWraparound() {
    // Init timestamp with a non-zero wraparound (multiple of 33-bit), just beyond the last one.
    TimestampAdjuster adjuster =
        new TimestampAdjuster(TimestampAdjuster.ptsToUs(3 * 0x200000000L + 90_000));

    long firstAdjustedTimestampUs = adjuster.adjustTsTimestamp(90_000);
    long secondAdjustedTimestampUs = adjuster.adjustTsTimestampGreaterThanPreviousTimestamp(45_000);

    assertThat(secondAdjustedTimestampUs - firstAdjustedTimestampUs).isGreaterThan(0x100000000L);
  }

  @Test
  public void
      adjustTsTimestampGreaterThanPreviousTimestamp_justBeyondWraparoundFollowedBySlightlyLargerValue_doesNotAssumeWraparound() {
    // Init timestamp with a non-zero wraparound (multiple of 33-bit), just beyond the last one.
    TimestampAdjuster adjuster =
        new TimestampAdjuster(TimestampAdjuster.ptsToUs(3 * 0x200000000L + 90_000));

    long firstAdjustedTimestampUs = adjuster.adjustTsTimestamp(90_000);
    long secondAdjustedTimestampUs =
        adjuster.adjustTsTimestampGreaterThanPreviousTimestamp(180_000);

    assertThat(secondAdjustedTimestampUs).isEqualTo(firstAdjustedTimestampUs + 1_000_000);
  }

  @Test
  public void
      adjustTsTimestampGreaterThanPreviousTimestamp_justBeyondWraparoundFollowedByMuchLargerValue_doesNotAssumeWraparound() {
    // Init timestamp with a non-zero wraparound (multiple of 33-bit), just beyond the last one.
    TimestampAdjuster adjuster =
        new TimestampAdjuster(TimestampAdjuster.ptsToUs(3 * 0x200000000L + 90_000));

    long firstAdjustedTimestampUs = adjuster.adjustTsTimestamp(90_000);
    long secondAdjustedTimestampUs =
        adjuster.adjustTsTimestampGreaterThanPreviousTimestamp(0x200000000L - 90_000);

    assertThat(secondAdjustedTimestampUs - firstAdjustedTimestampUs).isGreaterThan(0x100000000L);
  }
}
