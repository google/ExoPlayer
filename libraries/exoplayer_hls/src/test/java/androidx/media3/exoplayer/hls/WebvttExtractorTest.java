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
package androidx.media3.exoplayer.hls;

import static com.google.common.truth.Truth.assertThat;

import androidx.media3.common.util.TimestampAdjuster;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.SubtitleParser;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeExtractorInput;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
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
    WebvttExtractor extractor =
        new WebvttExtractor(
            /* language= */ null,
            timestampAdjuster,
            SubtitleParser.Factory.UNSUPPORTED,
            /* parseSubtitlesDuringExtraction= */ false);
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

  @Test
  public void read_handlesLargeCueTimestamps_withSubtitleParsingDuringExtraction()
      throws Exception {
    TimestampAdjuster timestampAdjuster = new TimestampAdjuster(/* firstSampleTimestampUs= */ 0);
    // Prime the TimestampAdjuster with a close-ish timestamp (5s before the first cue).
    timestampAdjuster.adjustTsTimestamp(384615190);
    WebvttExtractor extractor =
        new WebvttExtractor(
            /* language= */ null,
            timestampAdjuster,
            new DefaultSubtitleParserFactory(),
            /* parseSubtitlesDuringExtraction= */ true);

    FakeExtractorOutput output =
        TestUtil.extractAllSamplesFromFile(
            extractor,
            ApplicationProvider.getApplicationContext(),
            "media/webvtt/with_x-timestamp-map_header");

    // There are 2 cues in the file which are fed into 2 different samples during extraction
    // This is different to the parsing-during-decoding flow where the whole file becomes a sample
    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        output,
        "extractordumps/webvtt/with_x-timestamp-map_header_parsed_during_extraction.dump");
  }

  private static boolean sniffData(byte[] data) throws IOException {
    ExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();
    try {
      return new WebvttExtractor(
              /* language= */ null,
              new TimestampAdjuster(0),
              SubtitleParser.Factory.UNSUPPORTED,
              /* parseSubtitlesDuringExtraction= */ false)
          .sniff(input);
    } catch (EOFException e) {
      return false;
    }
  }
}
