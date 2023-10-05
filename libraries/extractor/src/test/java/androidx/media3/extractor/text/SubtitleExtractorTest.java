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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.text.webvtt.WebvttParser;
import androidx.media3.test.utils.FakeExtractorInput;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.primitives.Ints;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link SubtitleExtractor}. */
@RunWith(AndroidJUnit4.class)
public class SubtitleExtractorTest {
  private static final String TEST_DATA =
      "WEBVTT\n"
          + "\n"
          + "00:00.000 --> 00:01.234\n"
          + "This is the first subtitle.\n"
          + "\n"
          + "00:02.345 --> 00:03.456\n"
          + "This is the second subtitle.\n"
          + "\n"
          + "00:02.600 --> 00:04.567\n"
          + "This is the third subtitle.\n";

  private CueDecoder decoder;

  @Before
  public void createDecoder() {
    decoder = new CueDecoder();
  }

  @Test
  public void extractor_outputsCues() throws Exception {
    FakeExtractorOutput output = new FakeExtractorOutput();
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(Util.getUtf8Bytes(TEST_DATA))
            .setSimulatePartialReads(true)
            .build();
    SubtitleExtractor extractor =
        new SubtitleExtractor(
            new WebvttParser(), new Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build());
    extractor.init(output);

    while (extractor.read(input, null) != Extractor.RESULT_END_OF_INPUT) {}

    FakeTrackOutput trackOutput = output.trackOutputs.get(0);
    assertThat(trackOutput.lastFormat.sampleMimeType).isEqualTo(MimeTypes.APPLICATION_MEDIA3_CUES);
    assertThat(trackOutput.lastFormat.codecs).isEqualTo(MimeTypes.TEXT_VTT);
    assertThat(trackOutput.getSampleCount()).isEqualTo(4);
    CuesWithTiming cues0 = decodeSample(trackOutput, 0);
    assertThat(cues0.startTimeUs).isEqualTo(0);
    assertThat(cues0.durationUs).isEqualTo(1_234_000);
    assertThat(cues0.endTimeUs).isEqualTo(1_234_000);
    assertThat(cues0.cues).hasSize(1);
    assertThat(cues0.cues.get(0).text.toString()).isEqualTo("This is the first subtitle.");
    CuesWithTiming cues1 = decodeSample(trackOutput, 1);
    assertThat(cues1.startTimeUs).isEqualTo(2_345_000);
    assertThat(cues1.durationUs).isEqualTo(2_600_000 - 2_345_000);
    assertThat(cues1.endTimeUs).isEqualTo(2_600_000);
    assertThat(cues1.cues).hasSize(1);
    assertThat(cues1.cues.get(0).text.toString()).isEqualTo("This is the second subtitle.");
    CuesWithTiming cues2 = decodeSample(trackOutput, 2);
    assertThat(cues2.startTimeUs).isEqualTo(2_600_000);
    assertThat(cues2.durationUs).isEqualTo(3_456_000 - 2_600_000);
    assertThat(cues2.endTimeUs).isEqualTo(3_456_000);
    assertThat(cues2.cues).hasSize(2);
    assertThat(cues2.cues.get(0).text.toString()).isEqualTo("This is the second subtitle.");
    assertThat(cues2.cues.get(1).text.toString()).isEqualTo("This is the third subtitle.");
    CuesWithTiming cues3 = decodeSample(trackOutput, 3);
    assertThat(cues3.startTimeUs).isEqualTo(3_456_000);
    assertThat(cues3.durationUs).isEqualTo(4_567_000 - 3_456_000);
    assertThat(cues3.endTimeUs).isEqualTo(4_567_000);
    assertThat(cues3.cues).hasSize(1);
    assertThat(cues3.cues.get(0).text.toString()).isEqualTo("This is the third subtitle.");
  }

  @Test
  public void extractor_seekAfterExtracting_outputsCues() throws Exception {
    FakeExtractorOutput output = new FakeExtractorOutput();
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(Util.getUtf8Bytes(TEST_DATA))
            .setSimulatePartialReads(true)
            .build();
    SubtitleExtractor extractor =
        new SubtitleExtractor(
            new WebvttParser(), new Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build());
    extractor.init(output);
    FakeTrackOutput trackOutput = output.trackOutputs.get(0);

    while (extractor.read(input, null) != Extractor.RESULT_END_OF_INPUT) {}
    extractor.seek(output.seekMap.getSeekPoints(2_445_000L).first.position, 2_445_000L);
    input.setPosition(Ints.checkedCast(output.seekMap.getSeekPoints(2_445_000L).first.position));
    trackOutput.clear();
    while (extractor.read(input, null) != Extractor.RESULT_END_OF_INPUT) {}

    assertThat(trackOutput.lastFormat.sampleMimeType).isEqualTo(MimeTypes.APPLICATION_MEDIA3_CUES);
    assertThat(trackOutput.lastFormat.codecs).isEqualTo(MimeTypes.TEXT_VTT);
    assertThat(trackOutput.getSampleCount()).isEqualTo(3);
    CuesWithTiming cues0 = decodeSample(trackOutput, 0);
    assertThat(cues0.startTimeUs).isEqualTo(2_345_000L);
    assertThat(cues0.durationUs).isEqualTo(2_600_000 - 2_345_000L);
    assertThat(cues0.endTimeUs).isEqualTo(2_600_000);
    assertThat(cues0.cues).hasSize(1);
    assertThat(cues0.cues.get(0).text.toString()).isEqualTo("This is the second subtitle.");
    CuesWithTiming cues1 = decodeSample(trackOutput, 1);
    assertThat(cues1.startTimeUs).isEqualTo(2_600_000);
    assertThat(cues1.durationUs).isEqualTo(3_456_000 - 2_600_000);
    assertThat(cues1.endTimeUs).isEqualTo(3_456_000);
    assertThat(cues1.cues).hasSize(2);
    assertThat(cues1.cues.get(0).text.toString()).isEqualTo("This is the second subtitle.");
    assertThat(cues1.cues.get(1).text.toString()).isEqualTo("This is the third subtitle.");
    CuesWithTiming cues2 = decodeSample(trackOutput, 2);
    assertThat(cues2.startTimeUs).isEqualTo(3_456_000);
    assertThat(cues2.durationUs).isEqualTo(4_567_000L - 3_456_000);
    assertThat(cues2.endTimeUs).isEqualTo(4_567_000L);
    assertThat(cues2.cues).hasSize(1);
    assertThat(cues2.cues.get(0).text.toString()).isEqualTo("This is the third subtitle.");
  }

  @Test
  public void extractor_seekBetweenReads_outputsCues() throws Exception {
    FakeExtractorOutput output = new FakeExtractorOutput();
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(Util.getUtf8Bytes(TEST_DATA))
            .setSimulatePartialReads(true)
            .build();
    SubtitleExtractor extractor =
        new SubtitleExtractor(
            new WebvttParser(), new Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build());
    extractor.init(output);
    FakeTrackOutput trackOutput = output.trackOutputs.get(0);

    assertThat(extractor.read(input, null)).isNotEqualTo(Extractor.RESULT_END_OF_INPUT);
    extractor.seek(output.seekMap.getSeekPoints(2_345_000L).first.position, 2_345_000L);
    input.setPosition(Ints.checkedCast(output.seekMap.getSeekPoints(2_345_000L).first.position));
    trackOutput.clear();
    while (extractor.read(input, null) != Extractor.RESULT_END_OF_INPUT) {}

    assertThat(trackOutput.lastFormat.sampleMimeType).isEqualTo(MimeTypes.APPLICATION_MEDIA3_CUES);
    assertThat(trackOutput.lastFormat.codecs).isEqualTo(MimeTypes.TEXT_VTT);
    assertThat(trackOutput.getSampleCount()).isEqualTo(3);
    CuesWithTiming cues0 = decodeSample(trackOutput, 0);
    assertThat(cues0.startTimeUs).isEqualTo(2_345_000L);
    assertThat(cues0.durationUs).isEqualTo(2_600_000 - 2_345_000L);
    assertThat(cues0.endTimeUs).isEqualTo(2_600_000);
    assertThat(cues0.cues).hasSize(1);
    assertThat(cues0.cues.get(0).text.toString()).isEqualTo("This is the second subtitle.");
    CuesWithTiming cues1 = decodeSample(trackOutput, 1);
    assertThat(cues1.startTimeUs).isEqualTo(2_600_000);
    assertThat(cues1.durationUs).isEqualTo(3_456_000 - 2_600_000);
    assertThat(cues1.endTimeUs).isEqualTo(3_456_000);
    assertThat(cues1.cues).hasSize(2);
    assertThat(cues1.cues.get(0).text.toString()).isEqualTo("This is the second subtitle.");
    assertThat(cues1.cues.get(1).text.toString()).isEqualTo("This is the third subtitle.");
    CuesWithTiming cues2 = decodeSample(trackOutput, 2);
    assertThat(cues2.startTimeUs).isEqualTo(3_456_000);
    assertThat(cues2.durationUs).isEqualTo(4_567_000L - 3_456_000);
    assertThat(cues2.endTimeUs).isEqualTo(4_567_000L);
    assertThat(cues2.cues).hasSize(1);
    assertThat(cues2.cues.get(0).text.toString()).isEqualTo("This is the third subtitle.");
  }

  @Test
  public void read_withoutInit_fails() {
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(new byte[0]).build();
    SubtitleExtractor extractor =
        new SubtitleExtractor(new WebvttParser(), new Format.Builder().build());

    assertThrows(IllegalStateException.class, () -> extractor.read(input, null));
  }

  @Test
  public void read_afterRelease_fails() {
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(new byte[0]).build();
    SubtitleExtractor extractor =
        new SubtitleExtractor(new WebvttParser(), new Format.Builder().build());
    FakeExtractorOutput output = new FakeExtractorOutput();

    extractor.init(output);
    extractor.release();

    assertThrows(IllegalStateException.class, () -> extractor.read(input, null));
  }

  @Test
  public void seek_withoutInit_fails() {
    SubtitleExtractor extractor =
        new SubtitleExtractor(new WebvttParser(), new Format.Builder().build());

    assertThrows(IllegalStateException.class, () -> extractor.seek(0, 0));
  }

  @Test
  public void seek_afterRelease_fails() {
    SubtitleExtractor extractor =
        new SubtitleExtractor(new WebvttParser(), new Format.Builder().build());
    FakeExtractorOutput output = new FakeExtractorOutput();

    extractor.init(output);
    extractor.release();

    assertThrows(IllegalStateException.class, () -> extractor.seek(0, 0));
  }

  @Test
  public void released_calledTwice() {
    SubtitleExtractor extractor =
        new SubtitleExtractor(new WebvttParser(), new Format.Builder().build());
    FakeExtractorOutput output = new FakeExtractorOutput();

    extractor.init(output);
    extractor.release();
    extractor.release();
    // Calling realease() twice does not throw an exception.
  }

  private CuesWithTiming decodeSample(FakeTrackOutput trackOutput, int sampleIndex) {
    return decoder.decode(
        trackOutput.getSampleTimeUs(sampleIndex), trackOutput.getSampleData(sampleIndex));
  }
}
