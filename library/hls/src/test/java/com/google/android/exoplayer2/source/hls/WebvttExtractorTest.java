/*
 * Copyright (C) 2018 The Android Open Source Project
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

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.io.EOFException;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link WebvttExtractor}. */
@RunWith(AndroidJUnit4.class)
public class WebvttExtractorTest {

  @Test
  public void sniff_sniffsWebvttHeaderWithTrailingSpace() throws IOException {
    byte[] data = new byte[] {'W', 'E', 'B', 'V', 'T', 'T', ' ', '\t'};
    assertThat(sniffData(data)).isTrue();
  }

  @Test
  public void sniff_discardsByteOrderMark() throws IOException {
    byte[] data =
        new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'W', 'E', 'B', 'V', 'T', 'T', '\n', ' '};
    assertThat(sniffData(data)).isTrue();
  }

  @Test
  public void sniff_failsForIncorrectBom() throws IOException {
    byte[] data =
        new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBB, 'W', 'E', 'B', 'V', 'T', 'T', '\n'};
    assertThat(sniffData(data)).isFalse();
  }

  @Test
  public void sniff_failsForIncompleteHeader() throws IOException {
    byte[] data = new byte[] {'W', 'E', 'B', 'V', 'T', '\n'};
    assertThat(sniffData(data)).isFalse();
  }

  @Test
  public void sniff_failsForIncorrectHeader() throws IOException {
    byte[] data =
        new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'W', 'e', 'B', 'V', 'T', 'T', '\n'};
    assertThat(sniffData(data)).isFalse();
  }

  @Test
  public void read_handlesLargeCueTimestamps() throws Exception {
    TimestampAdjuster timestampAdjuster = new TimestampAdjuster(/* firstSampleTimestampUs= */ 0);
    // Prime the TimestampAdjuster with a close-ish timestamp (5s before the first cue).
    timestampAdjuster.adjustTsTimestamp(384615190);
    WebvttExtractor extractor = new WebvttExtractor(/* language= */ null, timestampAdjuster);
    // We can't use ExtractorAsserts because WebvttExtractor doesn't fulfill the whole Extractor
    // interface (e.g. throws an exception from seek()).
    FakeExtractorOutput output =
        TestUtil.extractAllSamplesFromFile(
            extractor,
            ApplicationProvider.getApplicationContext(),
            "media/webvtt/with_x-timestamp-map_header");

    // The output has a ~5s sampleTime and a large, negative subsampleOffset because the cue
    // timestamps are ~10 days ahead of the PTS (due to wrapping) so the offset is used to ensure
    // they're rendered at the right time.
    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        output,
        "extractordumps/webvtt/with_x-timestamp-map_header.dump");
  }

  private static boolean sniffData(byte[] data) throws IOException {
    ExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();
    try {
      return new WebvttExtractor(/* language= */ null, new TimestampAdjuster(0)).sniff(input);
    } catch (EOFException e) {
      return false;
    }
  }
}
