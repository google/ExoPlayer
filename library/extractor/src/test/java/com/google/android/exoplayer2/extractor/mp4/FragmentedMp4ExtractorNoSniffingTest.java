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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.util.MimeTypes;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/**
 * Tests for {@link FragmentedMp4Extractor} that test behaviours where sniffing must not be tested.
 */
@RunWith(ParameterizedRobolectricTestRunner.class)
public class FragmentedMp4ExtractorNoSniffingTest {

  @Parameters(name = "{0}")
  public static List<Object[]> params() {
    return ExtractorAsserts.configsNoSniffing();
  }

  @Parameter public ExtractorAsserts.SimulationConfig simulationConfig;

  @Test
  public void sampleWithSideLoadedTrack() throws Exception {
    // Sideloaded tracks are generally used in Smooth Streaming, where the MP4 files do not contain
    // any ftyp box and are not sniffed.
    Track sideloadedTrack =
        new Track(
            /* id= */ 1,
            /* type= */ C.TRACK_TYPE_VIDEO,
            /* timescale= */ 30_000,
            /* movieTimescale= */ 1000,
            /* durationUs= */ C.TIME_UNSET,
            new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build(),
            /* sampleTransformation= */ Track.TRANSFORMATION_NONE,
            /* sampleDescriptionEncryptionBoxes= */ null,
            /* nalUnitLengthFieldLength= */ 4,
            /* editListDurations= */ null,
            /* editListMediaTimes= */ null);
    ExtractorAsserts.assertBehavior(
        () ->
            new FragmentedMp4Extractor(
                /* flags= */ 0, /* timestampAdjuster= */ null, sideloadedTrack),
        "media/mp4/sample_fragmented_sideloaded_track.mp4",
        simulationConfig);
  }
}
