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
package com.google.android.exoplayer2.extractor.amr;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Unit test for {@link AmrExtractor} narrow-band AMR file. */
@RunWith(RobolectricTestRunner.class)
public final class AmrExtractorSeekTest {

  private static final Random random = new Random(1234L);

  private static final String NARROW_BAND_AMR_FILE = "amr/sample_nb.amr";
  private static final int NARROW_BAND_FILE_DURATION_US = 4_360_000;

  private static final String WIDE_BAND_AMR_FILE = "amr/sample_wb.amr";
  private static final int WIDE_BAND_FILE_DURATION_US = 3_380_000;

  private FakeTrackOutput expectedTrackOutput;
  private DefaultDataSource dataSource;
  private PositionHolder positionHolder;

  private long totalInputLength;

  @Before
  public void setUp() {
    dataSource =
        new DefaultDataSourceFactory(RuntimeEnvironment.application, "UserAgent")
            .createDataSource();
    positionHolder = new PositionHolder();
  }

  @Test
  public void testAmrExtractorReads_returnSeekableSeekMap_forNarrowBandAmr()
      throws IOException, InterruptedException {
    String fileName = NARROW_BAND_AMR_FILE;
    expectedTrackOutput =
        extractAllSamplesFromFileToExpectedOutput(RuntimeEnvironment.application, fileName);
    totalInputLength = readInputLength(fileName);

    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);
    SeekMap seekMap = extractSeekMap(extractor, new FakeExtractorOutput(), fileName);

    assertThat(seekMap).isNotNull();
    assertThat(seekMap.getDurationUs()).isEqualTo(NARROW_BAND_FILE_DURATION_US);
    assertThat(seekMap.isSeekable()).isTrue();
  }

  @Test
  public void testSeeking_handlesSeekingToPositionInFile_extractsCorrectFrame_forNarrowBandAmr()
      throws IOException, InterruptedException {
    String fileName = NARROW_BAND_AMR_FILE;
    expectedTrackOutput =
        extractAllSamplesFromFileToExpectedOutput(RuntimeEnvironment.application, fileName);
    totalInputLength = readInputLength(fileName);

    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput, fileName);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = 980_000;
    int extractedFrameIndex =
        seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput, fileName);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testSeeking_handlesSeekToEoF_extractsLastFrame_forNarrowBandAmr()
      throws IOException, InterruptedException {
    String fileName = NARROW_BAND_AMR_FILE;
    expectedTrackOutput =
        extractAllSamplesFromFileToExpectedOutput(RuntimeEnvironment.application, fileName);
    totalInputLength = readInputLength(fileName);
    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput, fileName);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = seekMap.getDurationUs();

    int extractedFrameIndex =
        seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput, fileName);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testSeeking_handlesSeekingBackward_extractsCorrectFrames_forNarrowBandAmr()
      throws IOException, InterruptedException {
    String fileName = NARROW_BAND_AMR_FILE;
    expectedTrackOutput =
        extractAllSamplesFromFileToExpectedOutput(RuntimeEnvironment.application, fileName);
    totalInputLength = readInputLength(fileName);
    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput, fileName);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long firstSeekTimeUs = 980_000;
    seekToTimeUs(extractor, seekMap, firstSeekTimeUs, trackOutput, fileName);

    long targetSeekTimeUs = 0;
    int extractedFrameIndex =
        seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput, fileName);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testSeeking_handlesSeekingForward_extractsCorrectFrames_forNarrowBandAmr()
      throws IOException, InterruptedException {
    String fileName = NARROW_BAND_AMR_FILE;
    expectedTrackOutput =
        extractAllSamplesFromFileToExpectedOutput(RuntimeEnvironment.application, fileName);
    totalInputLength = readInputLength(fileName);
    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput, fileName);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long firstSeekTimeUs = 980_000;
    seekToTimeUs(extractor, seekMap, firstSeekTimeUs, trackOutput, fileName);

    long targetSeekTimeUs = 1_200_000;
    int extractedFrameIndex =
        seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput, fileName);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testSeeking_handlesRandomSeeks_extractsCorrectFrames_forNarrowBandAmr()
      throws IOException, InterruptedException {
    String fileName = NARROW_BAND_AMR_FILE;
    expectedTrackOutput =
        extractAllSamplesFromFileToExpectedOutput(RuntimeEnvironment.application, fileName);
    totalInputLength = readInputLength(fileName);
    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput, fileName);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long numSeek = 100;
    for (long i = 0; i < numSeek; i++) {
      long targetSeekTimeUs = random.nextInt(NARROW_BAND_FILE_DURATION_US + 1);
      int extractedFrameIndex =
          seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput, fileName);

      assertThat(extractedFrameIndex).isNotEqualTo(-1);
      assertFirstFrameAfterSeekContainTargetSeekTime(
          trackOutput, targetSeekTimeUs, extractedFrameIndex);
    }
  }

  @Test
  public void testAmrExtractorReads_returnSeekableSeekMap_forWideBandAmr()
      throws IOException, InterruptedException {
    String fileName = WIDE_BAND_AMR_FILE;
    expectedTrackOutput =
        extractAllSamplesFromFileToExpectedOutput(RuntimeEnvironment.application, fileName);
    totalInputLength = readInputLength(fileName);

    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);
    SeekMap seekMap = extractSeekMap(extractor, new FakeExtractorOutput(), fileName);

    assertThat(seekMap).isNotNull();
    assertThat(seekMap.getDurationUs()).isEqualTo(WIDE_BAND_FILE_DURATION_US);
    assertThat(seekMap.isSeekable()).isTrue();
  }

  @Test
  public void testSeeking_handlesSeekingToPositionInFile_extractsCorrectFrame_forWideBandAmr()
      throws IOException, InterruptedException {
    String fileName = WIDE_BAND_AMR_FILE;
    expectedTrackOutput =
        extractAllSamplesFromFileToExpectedOutput(RuntimeEnvironment.application, fileName);
    totalInputLength = readInputLength(fileName);

    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput, fileName);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = 980_000;
    int extractedFrameIndex =
        seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput, fileName);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testSeeking_handlesSeekToEoF_extractsLastFrame_forWideBandAmr()
      throws IOException, InterruptedException {
    String fileName = WIDE_BAND_AMR_FILE;
    expectedTrackOutput =
        extractAllSamplesFromFileToExpectedOutput(RuntimeEnvironment.application, fileName);
    totalInputLength = readInputLength(fileName);
    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput, fileName);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = seekMap.getDurationUs();

    int extractedFrameIndex =
        seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput, fileName);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testSeeking_handlesSeekingBackward_extractsCorrectFrames_forWideBandAmr()
      throws IOException, InterruptedException {
    String fileName = WIDE_BAND_AMR_FILE;
    expectedTrackOutput =
        extractAllSamplesFromFileToExpectedOutput(RuntimeEnvironment.application, fileName);
    totalInputLength = readInputLength(fileName);
    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput, fileName);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long firstSeekTimeUs = 980_000;
    seekToTimeUs(extractor, seekMap, firstSeekTimeUs, trackOutput, fileName);

    long targetSeekTimeUs = 0;
    int extractedFrameIndex =
        seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput, fileName);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testSeeking_handlesSeekingForward_extractsCorrectFrames_forWideBandAmr()
      throws IOException, InterruptedException {
    String fileName = WIDE_BAND_AMR_FILE;
    expectedTrackOutput =
        extractAllSamplesFromFileToExpectedOutput(RuntimeEnvironment.application, fileName);
    totalInputLength = readInputLength(fileName);
    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput, fileName);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long firstSeekTimeUs = 980_000;
    seekToTimeUs(extractor, seekMap, firstSeekTimeUs, trackOutput, fileName);

    long targetSeekTimeUs = 1_200_000;
    int extractedFrameIndex =
        seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput, fileName);

    assertThat(extractedFrameIndex).isNotEqualTo(-1);
    assertFirstFrameAfterSeekContainTargetSeekTime(
        trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void testSeeking_handlesRandomSeeks_extractsCorrectFrames_forWideBandAmr()
      throws IOException, InterruptedException {
    String fileName = WIDE_BAND_AMR_FILE;
    expectedTrackOutput =
        extractAllSamplesFromFileToExpectedOutput(RuntimeEnvironment.application, fileName);
    totalInputLength = readInputLength(fileName);
    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);

    FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
    SeekMap seekMap = extractSeekMap(extractor, extractorOutput, fileName);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long numSeek = 100;
    for (long i = 0; i < numSeek; i++) {
      long targetSeekTimeUs = random.nextInt(NARROW_BAND_FILE_DURATION_US + 1);
      int extractedFrameIndex =
          seekToTimeUs(extractor, seekMap, targetSeekTimeUs, trackOutput, fileName);

      assertThat(extractedFrameIndex).isNotEqualTo(-1);
      assertFirstFrameAfterSeekContainTargetSeekTime(
          trackOutput, targetSeekTimeUs, extractedFrameIndex);
    }
  }

  // Internal methods

  private static String assetPathForFile(String fileName) {
    return "asset:///" + fileName;
  }

  private long readInputLength(String fileName) throws IOException {
    DataSpec dataSpec =
        new DataSpec(
            Uri.parse(assetPathForFile(fileName)),
            /* absoluteStreamPosition= */ 0,
            /* length= */ C.LENGTH_UNSET,
            /* key= */ null);
    long totalInputLength = dataSource.open(dataSpec);
    Util.closeQuietly(dataSource);
    return totalInputLength;
  }

  /**
   * Seeks to the given seek time and keeps reading from input until we can extract at least one
   * frame from the seek position, or until end-of-input is reached.
   *
   * @return The index of the first extracted frame written to the given {@code trackOutput} after
   *     the seek is completed, or -1 if the seek is completed without any extracted frame.
   */
  private int seekToTimeUs(
      AmrExtractor amrExtractor,
      SeekMap seekMap,
      long seekTimeUs,
      FakeTrackOutput trackOutput,
      String fileName)
      throws IOException, InterruptedException {
    int numSampleBeforeSeek = trackOutput.getSampleCount();
    SeekMap.SeekPoints seekPoints = seekMap.getSeekPoints(seekTimeUs);

    long initialSeekLoadPosition = seekPoints.first.position;
    amrExtractor.seek(initialSeekLoadPosition, seekTimeUs);

    positionHolder.position = C.POSITION_UNSET;
    ExtractorInput extractorInput =
        getExtractorInputFromPosition(initialSeekLoadPosition, fileName);
    int extractorReadResult = Extractor.RESULT_CONTINUE;
    while (true) {
      try {
        // Keep reading until we can read at least one frame after seek
        while (extractorReadResult == Extractor.RESULT_CONTINUE
            && trackOutput.getSampleCount() == numSampleBeforeSeek) {
          extractorReadResult = amrExtractor.read(extractorInput, positionHolder);
        }
      } finally {
        Util.closeQuietly(dataSource);
      }

      if (extractorReadResult == Extractor.RESULT_SEEK) {
        extractorInput = getExtractorInputFromPosition(positionHolder.position, fileName);
        extractorReadResult = Extractor.RESULT_CONTINUE;
      } else if (extractorReadResult == Extractor.RESULT_END_OF_INPUT) {
        return -1;
      } else if (trackOutput.getSampleCount() > numSampleBeforeSeek) {
        // First index after seek = num sample before seek.
        return numSampleBeforeSeek;
      }
    }
  }

  private @Nullable SeekMap extractSeekMap(
      AmrExtractor extractor, FakeExtractorOutput output, String fileName)
      throws IOException, InterruptedException {
    try {
      ExtractorInput input = getExtractorInputFromPosition(/* position= */ 0, fileName);
      extractor.init(output);
      while (output.seekMap == null) {
        extractor.read(input, positionHolder);
      }
    } finally {
      Util.closeQuietly(dataSource);
    }
    return output.seekMap;
  }

  private void assertFirstFrameAfterSeekContainTargetSeekTime(
      FakeTrackOutput trackOutput, long seekTimeUs, int firstFrameIndexAfterSeek) {
    int expectedSampleIndex = findTargetFrameInExpectedOutput(seekTimeUs);
    // Assert that after seeking, the first sample frame written to output contains the sample
    // at seek time.
    trackOutput.assertSample(
        firstFrameIndexAfterSeek,
        expectedTrackOutput.getSampleData(expectedSampleIndex),
        expectedTrackOutput.getSampleTimeUs(expectedSampleIndex),
        expectedTrackOutput.getSampleFlags(expectedSampleIndex),
        expectedTrackOutput.getSampleCryptoData(expectedSampleIndex));
  }

  private int findTargetFrameInExpectedOutput(long seekTimeUs) {
    List<Long> sampleTimes = expectedTrackOutput.getSampleTimesUs();
    for (int i = 0; i < sampleTimes.size() - 1; i++) {
      long currentSampleTime = sampleTimes.get(i);
      long nextSampleTime = sampleTimes.get(i + 1);
      if (currentSampleTime <= seekTimeUs && nextSampleTime > seekTimeUs) {
        return i;
      }
    }
    return sampleTimes.size() - 1;
  }

  private ExtractorInput getExtractorInputFromPosition(long position, String fileName)
      throws IOException {
    DataSpec dataSpec =
        new DataSpec(
            Uri.parse(assetPathForFile(fileName)), position, totalInputLength, /* key= */ null);
    dataSource.open(dataSpec);
    return new DefaultExtractorInput(dataSource, position, totalInputLength);
  }

  private FakeTrackOutput extractAllSamplesFromFileToExpectedOutput(
      Context context, String fileName) throws IOException, InterruptedException {
    byte[] data = TestUtil.getByteArray(context, fileName);

    AmrExtractor extractor = new AmrExtractor(AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING);
    FakeExtractorOutput expectedOutput = new FakeExtractorOutput();
    extractor.init(expectedOutput);
    FakeExtractorInput input = new FakeExtractorInput.Builder().setData(data).build();

    while (extractor.read(input, new PositionHolder()) != Extractor.RESULT_END_OF_INPUT) {}
    return expectedOutput.trackOutputs.get(0);
  }
}
