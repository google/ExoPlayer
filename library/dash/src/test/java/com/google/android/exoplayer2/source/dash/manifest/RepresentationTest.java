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
package com.google.android.exoplayer2.source.dash.manifest;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link Representation}. */
@RunWith(AndroidJUnit4.class)
public final class RepresentationTest {

  @Test
  public void getFirstAvailableSegmentNum_multiSegmentRepresentationWithUnboundedTemplate() {
    long periodStartUnixTimeUs = 123_000_000_000_000L;
    SegmentBase.SegmentTemplate segmentTemplate =
        new SegmentBase.SegmentTemplate(
            /* initialization= */ null,
            /* timescale= */ 1000,
            /* presentationTimeOffset= */ 0,
            /* startNumber= */ 42,
            /* endNumber= */ C.INDEX_UNSET,
            /* duration= */ 2000,
            /* segmentTimeline= */ null,
            /* availabilityTimeOffsetUs= */ 500_000,
            /* initializationTemplate= */ null,
            /* mediaTemplate= */ null);
    Representation.MultiSegmentRepresentation representation =
        new Representation.MultiSegmentRepresentation(
            /* revisionId= */ 0,
            new Format.Builder().build(),
            /* baseUrl= */ "https://baseUrl/",
            segmentTemplate,
            /* inbandEventStreams= */ null,
            /* periodStartUnixTimeMs= */ C.usToMs(periodStartUnixTimeUs),
            /* timeShiftBufferDepthMs= */ 6_000);

    assertThat(
            representation.getFirstAvailableSegmentNum(
                /* periodDurationUs= */ C.TIME_UNSET,
                /* nowUnixTimeUs= */ periodStartUnixTimeUs - 10_000_000))
        .isEqualTo(42);
    assertThat(
            representation.getFirstAvailableSegmentNum(
                /* periodDurationUs= */ C.TIME_UNSET, /* nowUnixTimeUs= */ periodStartUnixTimeUs))
        .isEqualTo(42);
    assertThat(
            representation.getFirstAvailableSegmentNum(
                /* periodDurationUs= */ C.TIME_UNSET,
                /* nowUnixTimeUs= */ periodStartUnixTimeUs + 7_999_999))
        .isEqualTo(42);
    assertThat(
            representation.getFirstAvailableSegmentNum(
                /* periodDurationUs= */ C.TIME_UNSET,
                /* nowUnixTimeUs= */ periodStartUnixTimeUs + 8_000_000))
        .isEqualTo(43);
    assertThat(
            representation.getFirstAvailableSegmentNum(
                /* periodDurationUs= */ C.TIME_UNSET,
                /* nowUnixTimeUs= */ periodStartUnixTimeUs + 9_999_999))
        .isEqualTo(43);
    assertThat(
            representation.getFirstAvailableSegmentNum(
                /* periodDurationUs= */ C.TIME_UNSET,
                /* nowUnixTimeUs= */ periodStartUnixTimeUs + 10_000_000))
        .isEqualTo(44);
  }

  @Test
  public void getAvailableSegmentCount_multiSegmentRepresentationWithUnboundedTemplate() {
    long periodStartUnixTimeUs = 123_000_000_000_000L;
    SegmentBase.SegmentTemplate segmentTemplate =
        new SegmentBase.SegmentTemplate(
            /* initialization= */ null,
            /* timescale= */ 1000,
            /* presentationTimeOffset= */ 0,
            /* startNumber= */ 42,
            /* endNumber= */ C.INDEX_UNSET,
            /* duration= */ 2000,
            /* segmentTimeline= */ null,
            /* availabilityTimeOffsetUs= */ 500_000,
            /* initializationTemplate= */ null,
            /* mediaTemplate= */ null);
    Representation.MultiSegmentRepresentation representation =
        new Representation.MultiSegmentRepresentation(
            /* revisionId= */ 0,
            new Format.Builder().build(),
            /* baseUrl= */ "https://baseUrl/",
            segmentTemplate,
            /* inbandEventStreams= */ null,
            /* periodStartUnixTimeMs= */ C.usToMs(periodStartUnixTimeUs),
            /* timeShiftBufferDepthMs= */ 6_000);

    assertThat(
            representation.getAvailableSegmentCount(
                /* periodDurationUs= */ C.TIME_UNSET,
                /* nowUnixTimeUs= */ periodStartUnixTimeUs - 10_000_000))
        .isEqualTo(0);
    assertThat(
            representation.getAvailableSegmentCount(
                /* periodDurationUs= */ C.TIME_UNSET, /* nowUnixTimeUs= */ periodStartUnixTimeUs))
        .isEqualTo(0);
    assertThat(
            representation.getAvailableSegmentCount(
                /* periodDurationUs= */ C.TIME_UNSET,
                /* nowUnixTimeUs= */ periodStartUnixTimeUs + 1_499_999))
        .isEqualTo(0);
    assertThat(
            representation.getAvailableSegmentCount(
                /* periodDurationUs= */ C.TIME_UNSET,
                /* nowUnixTimeUs= */ periodStartUnixTimeUs + 1_500_000))
        .isEqualTo(1);
    assertThat(
            representation.getAvailableSegmentCount(
                /* periodDurationUs= */ C.TIME_UNSET,
                /* nowUnixTimeUs= */ periodStartUnixTimeUs + 7_499_999))
        .isEqualTo(3);
    assertThat(
            representation.getAvailableSegmentCount(
                /* periodDurationUs= */ C.TIME_UNSET,
                /* nowUnixTimeUs= */ periodStartUnixTimeUs + 7_500_000))
        .isEqualTo(4);
    assertThat(
            representation.getAvailableSegmentCount(
                /* periodDurationUs= */ C.TIME_UNSET,
                /* nowUnixTimeUs= */ periodStartUnixTimeUs + 7_999_999))
        .isEqualTo(4);
    assertThat(
            representation.getAvailableSegmentCount(
                /* periodDurationUs= */ C.TIME_UNSET,
                /* nowUnixTimeUs= */ periodStartUnixTimeUs + 8_000_000))
        .isEqualTo(3);
  }
}
