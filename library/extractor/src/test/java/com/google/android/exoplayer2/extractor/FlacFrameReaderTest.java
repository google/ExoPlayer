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
package com.google.android.exoplayer2.extractor;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.extractor.FlacFrameReader.SampleNumberHolder;
import com.google.android.exoplayer2.extractor.FlacMetadataReader.FlacStreamMetadataHolder;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.FlacConstants;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link FlacFrameReader}.
 *
 * <p>Some expected results in these tests have been retrieved using the <a
 * href="https://xiph.org/flac/documentation_tools_flac.html">flac</a> command.
 */
@RunWith(AndroidJUnit4.class)
public class FlacFrameReaderTest {

  @Test
  public void checkAndReadFrameHeader_validData_updatesPosition() throws Exception {
    FlacStreamMetadataHolder streamMetadataHolder =
        new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null);
    ExtractorInput input =
        buildExtractorInputReadingFromFirstFrame(
            "flac/bear_one_metadata_block.flac", streamMetadataHolder);
    int frameStartMarker = FlacMetadataReader.getFrameStartMarker(input);
    ParsableByteArray scratch = new ParsableByteArray(FlacConstants.MAX_FRAME_HEADER_SIZE);
    input.read(scratch.data, 0, FlacConstants.MAX_FRAME_HEADER_SIZE);

    FlacFrameReader.checkAndReadFrameHeader(
        scratch,
        streamMetadataHolder.flacStreamMetadata,
        frameStartMarker,
        new SampleNumberHolder());

    assertThat(scratch.getPosition()).isEqualTo(FlacConstants.MIN_FRAME_HEADER_SIZE);
  }

  @Test
  public void checkAndReadFrameHeader_validData_isTrue() throws Exception {
    FlacStreamMetadataHolder streamMetadataHolder =
        new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null);
    ExtractorInput input =
        buildExtractorInputReadingFromFirstFrame(
            "flac/bear_one_metadata_block.flac", streamMetadataHolder);
    int frameStartMarker = FlacMetadataReader.getFrameStartMarker(input);
    ParsableByteArray scratch = new ParsableByteArray(FlacConstants.MAX_FRAME_HEADER_SIZE);
    input.read(scratch.data, 0, FlacConstants.MAX_FRAME_HEADER_SIZE);

    boolean result =
        FlacFrameReader.checkAndReadFrameHeader(
            scratch,
            streamMetadataHolder.flacStreamMetadata,
            frameStartMarker,
            new SampleNumberHolder());

    assertThat(result).isTrue();
  }

  @Test
  public void checkAndReadFrameHeader_validData_writesSampleNumber() throws Exception {
    FlacStreamMetadataHolder streamMetadataHolder =
        new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null);
    ExtractorInput input =
        buildExtractorInputReadingFromFirstFrame(
            "flac/bear_one_metadata_block.flac", streamMetadataHolder);
    int frameStartMarker = FlacMetadataReader.getFrameStartMarker(input);
    // Skip first frame.
    input.skip(5030);
    ParsableByteArray scratch = new ParsableByteArray(FlacConstants.MAX_FRAME_HEADER_SIZE);
    input.read(scratch.data, 0, FlacConstants.MAX_FRAME_HEADER_SIZE);
    SampleNumberHolder sampleNumberHolder = new SampleNumberHolder();

    FlacFrameReader.checkAndReadFrameHeader(
        scratch, streamMetadataHolder.flacStreamMetadata, frameStartMarker, sampleNumberHolder);

    assertThat(sampleNumberHolder.sampleNumber).isEqualTo(4096);
  }

  @Test
  public void checkAndReadFrameHeader_invalidData_isFalse() throws Exception {
    FlacStreamMetadataHolder streamMetadataHolder =
        new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null);
    ExtractorInput input =
        buildExtractorInputReadingFromFirstFrame(
            "flac/bear_one_metadata_block.flac", streamMetadataHolder);
    ParsableByteArray scratch = new ParsableByteArray(FlacConstants.MAX_FRAME_HEADER_SIZE);
    input.read(scratch.data, 0, FlacConstants.MAX_FRAME_HEADER_SIZE);

    // The first bytes of the frame are not equal to the frame start marker.
    boolean result =
        FlacFrameReader.checkAndReadFrameHeader(
            scratch,
            streamMetadataHolder.flacStreamMetadata,
            /* frameStartMarker= */ -1,
            new SampleNumberHolder());

    assertThat(result).isFalse();
  }

  @Test
  public void checkFrameHeaderFromPeek_validData_doesNotUpdatePositions() throws Exception {
    String file = "flac/bear_one_metadata_block.flac";
    FlacStreamMetadataHolder streamMetadataHolder =
        new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null);
    ExtractorInput input = buildExtractorInputReadingFromFirstFrame(file, streamMetadataHolder);
    int frameStartMarker = FlacMetadataReader.getFrameStartMarker(input);
    long peekPosition = input.getPosition();
    // Set read position to 0.
    input = buildExtractorInput(file);
    input.advancePeekPosition((int) peekPosition);

    FlacFrameReader.checkFrameHeaderFromPeek(
        input, streamMetadataHolder.flacStreamMetadata, frameStartMarker, new SampleNumberHolder());

    assertThat(input.getPosition()).isEqualTo(0);
    assertThat(input.getPeekPosition()).isEqualTo(peekPosition);
  }

  @Test
  public void checkFrameHeaderFromPeek_validData_isTrue() throws Exception {
    FlacStreamMetadataHolder streamMetadataHolder =
        new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null);
    ExtractorInput input =
        buildExtractorInputReadingFromFirstFrame(
            "flac/bear_one_metadata_block.flac", streamMetadataHolder);
    int frameStartMarker = FlacMetadataReader.getFrameStartMarker(input);

    boolean result =
        FlacFrameReader.checkFrameHeaderFromPeek(
            input,
            streamMetadataHolder.flacStreamMetadata,
            frameStartMarker,
            new SampleNumberHolder());

    assertThat(result).isTrue();
  }

  @Test
  public void checkFrameHeaderFromPeek_validData_writesSampleNumber() throws Exception {
    FlacStreamMetadataHolder streamMetadataHolder =
        new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null);
    ExtractorInput input =
        buildExtractorInputReadingFromFirstFrame(
            "flac/bear_one_metadata_block.flac", streamMetadataHolder);
    int frameStartMarker = FlacMetadataReader.getFrameStartMarker(input);
    // Skip first frame.
    input.skip(5030);
    SampleNumberHolder sampleNumberHolder = new SampleNumberHolder();

    FlacFrameReader.checkFrameHeaderFromPeek(
        input, streamMetadataHolder.flacStreamMetadata, frameStartMarker, sampleNumberHolder);

    assertThat(sampleNumberHolder.sampleNumber).isEqualTo(4096);
  }

  @Test
  public void checkFrameHeaderFromPeek_invalidData_isFalse() throws Exception {
    FlacStreamMetadataHolder streamMetadataHolder =
        new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null);
    ExtractorInput input =
        buildExtractorInputReadingFromFirstFrame(
            "flac/bear_one_metadata_block.flac", streamMetadataHolder);

    // The first bytes of the frame are not equal to the frame start marker.
    boolean result =
        FlacFrameReader.checkFrameHeaderFromPeek(
            input,
            streamMetadataHolder.flacStreamMetadata,
            /* frameStartMarker= */ -1,
            new SampleNumberHolder());

    assertThat(result).isFalse();
  }

  @Test
  public void checkFrameHeaderFromPeek_invalidData_doesNotUpdatePositions() throws Exception {
    String file = "flac/bear_one_metadata_block.flac";
    FlacStreamMetadataHolder streamMetadataHolder =
        new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null);
    ExtractorInput input = buildExtractorInputReadingFromFirstFrame(file, streamMetadataHolder);
    long peekPosition = input.getPosition();
    // Set read position to 0.
    input = buildExtractorInput(file);
    input.advancePeekPosition((int) peekPosition);

    // The first bytes of the frame are not equal to the frame start marker.
    FlacFrameReader.checkFrameHeaderFromPeek(
        input,
        streamMetadataHolder.flacStreamMetadata,
        /* frameStartMarker= */ -1,
        new SampleNumberHolder());

    assertThat(input.getPosition()).isEqualTo(0);
    assertThat(input.getPeekPosition()).isEqualTo(peekPosition);
  }

  @Test
  public void getFirstSampleNumber_doesNotUpdateReadPositionAndAlignsPeekPosition()
      throws Exception {
    FlacStreamMetadataHolder streamMetadataHolder =
        new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null);
    ExtractorInput input =
        buildExtractorInputReadingFromFirstFrame(
            "flac/bear_one_metadata_block.flac", streamMetadataHolder);
    long initialReadPosition = input.getPosition();
    // Advance peek position after block size bits.
    input.advancePeekPosition(FlacConstants.MAX_FRAME_HEADER_SIZE);

    FlacFrameReader.getFirstSampleNumber(input, streamMetadataHolder.flacStreamMetadata);

    assertThat(input.getPosition()).isEqualTo(initialReadPosition);
    assertThat(input.getPeekPosition()).isEqualTo(input.getPosition());
  }

  @Test
  public void getFirstSampleNumber_returnsSampleNumber() throws Exception {
    FlacStreamMetadataHolder streamMetadataHolder =
        new FlacStreamMetadataHolder(/* flacStreamMetadata= */ null);
    ExtractorInput input =
        buildExtractorInputReadingFromFirstFrame(
            "flac/bear_one_metadata_block.flac", streamMetadataHolder);
    // Skip first frame.
    input.skip(5030);

    long result =
        FlacFrameReader.getFirstSampleNumber(input, streamMetadataHolder.flacStreamMetadata);

    assertThat(result).isEqualTo(4096);
  }

  @Test
  public void readFrameBlockSizeSamplesFromKey_keyIs1_returnsCorrectBlockSize() {
    int result =
        FlacFrameReader.readFrameBlockSizeSamplesFromKey(
            new ParsableByteArray(/* limit= */ 0), /* blockSizeKey= */ 1);

    assertThat(result).isEqualTo(192);
  }

  @Test
  public void readFrameBlockSizeSamplesFromKey_keyBetween2and5_returnsCorrectBlockSize() {
    int result =
        FlacFrameReader.readFrameBlockSizeSamplesFromKey(
            new ParsableByteArray(/* limit= */ 0), /* blockSizeKey= */ 3);

    assertThat(result).isEqualTo(1152);
  }

  @Test
  public void readFrameBlockSizeSamplesFromKey_keyBetween6And7_returnsCorrectBlockSize()
      throws Exception {
    ExtractorInput input = buildExtractorInput("flac/bear_one_metadata_block.flac");
    // Skip to block size bits of last frame.
    input.skipFully(164033);
    ParsableByteArray scratch = new ParsableByteArray(2);
    input.readFully(scratch.data, 0, 2);

    int result = FlacFrameReader.readFrameBlockSizeSamplesFromKey(scratch, /* blockSizeKey= */ 7);

    assertThat(result).isEqualTo(496);
  }

  @Test
  public void readFrameBlockSizeSamplesFromKey_keyBetween8and15_returnsCorrectBlockSize() {
    int result =
        FlacFrameReader.readFrameBlockSizeSamplesFromKey(
            new ParsableByteArray(/* limit= */ 0), /* blockSizeKey= */ 11);

    assertThat(result).isEqualTo(2048);
  }

  @Test
  public void readFrameBlockSizeSamplesFromKey_invalidKey_returnsCorrectBlockSize() {
    int result =
        FlacFrameReader.readFrameBlockSizeSamplesFromKey(
            new ParsableByteArray(/* limit= */ 0), /* blockSizeKey= */ 25);

    assertThat(result).isEqualTo(-1);
  }

  private static ExtractorInput buildExtractorInput(String file) throws IOException {
    byte[] fileData = TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), file);
    return new FakeExtractorInput.Builder().setData(fileData).build();
  }

  private ExtractorInput buildExtractorInputReadingFromFirstFrame(
      String file, FlacStreamMetadataHolder streamMetadataHolder) throws IOException {
    ExtractorInput input = buildExtractorInput(file);

    input.skipFully(FlacConstants.STREAM_MARKER_SIZE);

    boolean lastMetadataBlock = false;
    while (!lastMetadataBlock) {
      lastMetadataBlock = FlacMetadataReader.readMetadataBlock(input, streamMetadataHolder);
    }

    return input;
  }
}
