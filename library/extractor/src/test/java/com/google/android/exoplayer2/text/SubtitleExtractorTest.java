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
package com.google.android.exoplayer2.text;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.text.webvtt.WebvttDecoder;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.util.List;
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

  @Test
  public void extractor_outputsCues() throws Exception {
    CueDecoder decoder = new CueDecoder();
    FakeExtractorOutput output = new FakeExtractorOutput();
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(Util.getUtf8Bytes(TEST_DATA))
            .setSimulatePartialReads(true)
            .build();
    SubtitleExtractor extractor =
        new SubtitleExtractor(
            new WebvttDecoder(),
            new Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build());
    extractor.init(output);

    while (extractor.read(input, null) != Extractor.RESULT_END_OF_INPUT) {}

    FakeTrackOutput trackOutput = output.trackOutputs.get(0);
    assertThat(trackOutput.lastFormat.sampleMimeType).isEqualTo(MimeTypes.TEXT_EXOPLAYER_CUES);
    assertThat(trackOutput.lastFormat.codecs).isEqualTo(MimeTypes.TEXT_VTT);
    assertThat(trackOutput.getSampleCount()).isEqualTo(6);
    // Check sample timestamps.
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0L);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(1_234_000L);
    assertThat(trackOutput.getSampleTimeUs(2)).isEqualTo(2_345_000L);
    assertThat(trackOutput.getSampleTimeUs(3)).isEqualTo(2_600_000L);
    assertThat(trackOutput.getSampleTimeUs(4)).isEqualTo(3_456_000L);
    assertThat(trackOutput.getSampleTimeUs(5)).isEqualTo(4_567_000L);
    // Check sample content.
    List<Cue> cues0 = decoder.decode(trackOutput.getSampleData(0));
    assertThat(cues0).hasSize(1);
    assertThat(cues0.get(0).text.toString()).isEqualTo("This is the first subtitle.");
    List<Cue> cues1 = decoder.decode(trackOutput.getSampleData(1));
    assertThat(cues1).isEmpty();
    List<Cue> cues2 = decoder.decode(trackOutput.getSampleData(2));
    assertThat(cues2).hasSize(1);
    assertThat(cues2.get(0).text.toString()).isEqualTo("This is the second subtitle.");
    List<Cue> cues3 = decoder.decode(trackOutput.getSampleData(3));
    assertThat(cues3).hasSize(2);
    assertThat(cues3.get(0).text.toString()).isEqualTo("This is the second subtitle.");
    assertThat(cues3.get(1).text.toString()).isEqualTo("This is the third subtitle.");
    List<Cue> cues4 = decoder.decode(trackOutput.getSampleData(4));
    assertThat(cues4).hasSize(1);
    assertThat(cues4.get(0).text.toString()).isEqualTo("This is the third subtitle.");
    List<Cue> cues5 = decoder.decode(trackOutput.getSampleData(5));
    assertThat(cues5).isEmpty();
  }

  @Test
  public void extractor_seekAfterExtracting_outputsCues() throws Exception {
    CueDecoder decoder = new CueDecoder();
    FakeExtractorOutput output = new FakeExtractorOutput();
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(Util.getUtf8Bytes(TEST_DATA))
            .setSimulatePartialReads(true)
            .build();
    SubtitleExtractor extractor =
        new SubtitleExtractor(
            new WebvttDecoder(),
            new Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build());
    extractor.init(output);
    FakeTrackOutput trackOutput = output.trackOutputs.get(0);

    while (extractor.read(input, null) != Extractor.RESULT_END_OF_INPUT) {}
    extractor.seek((int) output.seekMap.getSeekPoints(2_445_000L).first.position, 2_445_000L);
    input.setPosition((int) output.seekMap.getSeekPoints(2_445_000L).first.position);
    trackOutput.clear();
    while (extractor.read(input, null) != Extractor.RESULT_END_OF_INPUT) {}

    assertThat(trackOutput.lastFormat.sampleMimeType).isEqualTo(MimeTypes.TEXT_EXOPLAYER_CUES);
    assertThat(trackOutput.lastFormat.codecs).isEqualTo(MimeTypes.TEXT_VTT);
    assertThat(trackOutput.getSampleCount()).isEqualTo(4);
    // Check sample timestamps.
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(2_345_000L);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(2_600_000L);
    assertThat(trackOutput.getSampleTimeUs(2)).isEqualTo(3_456_000L);
    assertThat(trackOutput.getSampleTimeUs(3)).isEqualTo(4_567_000L);
    // Check sample content.
    List<Cue> cues0 = decoder.decode(trackOutput.getSampleData(0));
    assertThat(cues0).hasSize(1);
    assertThat(cues0.get(0).text.toString()).isEqualTo("This is the second subtitle.");
    List<Cue> cues1 = decoder.decode(trackOutput.getSampleData(1));
    assertThat(cues1).hasSize(2);
    assertThat(cues1.get(0).text.toString()).isEqualTo("This is the second subtitle.");
    assertThat(cues1.get(1).text.toString()).isEqualTo("This is the third subtitle.");
    List<Cue> cues2 = decoder.decode(trackOutput.getSampleData(2));
    assertThat(cues2).hasSize(1);
    assertThat(cues2.get(0).text.toString()).isEqualTo("This is the third subtitle.");
    List<Cue> cues3 = decoder.decode(trackOutput.getSampleData(3));
    assertThat(cues3).isEmpty();
  }

  @Test
  public void extractor_seekBetweenReads_outputsCues() throws Exception {
    CueDecoder decoder = new CueDecoder();
    FakeExtractorOutput output = new FakeExtractorOutput();
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(Util.getUtf8Bytes(TEST_DATA))
            .setSimulatePartialReads(true)
            .build();
    SubtitleExtractor extractor =
        new SubtitleExtractor(
            new WebvttDecoder(),
            new Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build());
    extractor.init(output);
    FakeTrackOutput trackOutput = output.trackOutputs.get(0);

    assertThat(extractor.read(input, null)).isNotEqualTo(Extractor.RESULT_END_OF_INPUT);
    extractor.seek((int) output.seekMap.getSeekPoints(2_345_000L).first.position, 2_345_000L);
    input.setPosition((int) output.seekMap.getSeekPoints(2_345_000L).first.position);
    trackOutput.clear();
    while (extractor.read(input, null) != Extractor.RESULT_END_OF_INPUT) {}

    assertThat(trackOutput.lastFormat.sampleMimeType).isEqualTo(MimeTypes.TEXT_EXOPLAYER_CUES);
    assertThat(trackOutput.lastFormat.codecs).isEqualTo(MimeTypes.TEXT_VTT);
    assertThat(trackOutput.getSampleCount()).isEqualTo(4);
    // Check sample timestamps.
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(2_345_000L);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(2_600_000L);
    assertThat(trackOutput.getSampleTimeUs(2)).isEqualTo(3_456_000L);
    assertThat(trackOutput.getSampleTimeUs(3)).isEqualTo(4_567_000L);
    // Check sample content.
    List<Cue> cues0 = decoder.decode(trackOutput.getSampleData(0));
    assertThat(cues0).hasSize(1);
    assertThat(cues0.get(0).text.toString()).isEqualTo("This is the second subtitle.");
    List<Cue> cues1 = decoder.decode(trackOutput.getSampleData(1));
    assertThat(cues1).hasSize(2);
    assertThat(cues1.get(0).text.toString()).isEqualTo("This is the second subtitle.");
    assertThat(cues1.get(1).text.toString()).isEqualTo("This is the third subtitle.");
    List<Cue> cues2 = decoder.decode(trackOutput.getSampleData(2));
    assertThat(cues2).hasSize(1);
    assertThat(cues2.get(0).text.toString()).isEqualTo("This is the third subtitle.");
    List<Cue> cues3 = decoder.decode(trackOutput.getSampleData(3));
    assertThat(cues3).isEmpty();
  }

  @Test
  public void read_withoutInit_fails() {
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(new byte[0]).build();
    SubtitleExtractor extractor =
        new SubtitleExtractor(new WebvttDecoder(), new Format.Builder().build());

    assertThrows(IllegalStateException.class, () -> extractor.read(input, null));
  }

  @Test
  public void read_afterRelease_fails() {
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(new byte[0]).build();
    SubtitleExtractor extractor =
        new SubtitleExtractor(new WebvttDecoder(), new Format.Builder().build());
    FakeExtractorOutput output = new FakeExtractorOutput();

    extractor.init(output);
    extractor.release();

    assertThrows(IllegalStateException.class, () -> extractor.read(input, null));
  }

  @Test
  public void seek_withoutInit_fails() {
    SubtitleExtractor extractor =
        new SubtitleExtractor(new WebvttDecoder(), new Format.Builder().build());

    assertThrows(IllegalStateException.class, () -> extractor.seek(0, 0));
  }

  @Test
  public void seek_afterRelease_fails() {
    SubtitleExtractor extractor =
        new SubtitleExtractor(new WebvttDecoder(), new Format.Builder().build());
    FakeExtractorOutput output = new FakeExtractorOutput();

    extractor.init(output);
    extractor.release();

    assertThrows(IllegalStateException.class, () -> extractor.seek(0, 0));
  }

  @Test
  public void released_calledTwice() {
    SubtitleExtractor extractor =
        new SubtitleExtractor(new WebvttDecoder(), new Format.Builder().build());
    FakeExtractorOutput output = new FakeExtractorOutput();

    extractor.init(output);
    extractor.release();
    extractor.release();
    // Calling realease() twice does not throw an exception.
  }
}
