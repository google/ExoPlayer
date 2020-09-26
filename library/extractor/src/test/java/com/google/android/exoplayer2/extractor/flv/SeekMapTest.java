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
package com.google.android.exoplayer2.extractor.flv;

import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static com.google.android.exoplayer2.testutil.TestUtil.extractAllSamplesFromFile;
import static com.google.android.exoplayer2.testutil.TestUtil.getExtractorInputFromPosition;
import static com.google.common.truth.Truth.assertThat;

/** Unit test for SeekMap in {@link FlvExtractor}. */
@RunWith(AndroidJUnit4.class)
public class SeekMapTest {

  // the test file was made by yamdi (https://github.com/ioppermann/yamdi)
  // yamdi -i media/flv/source.flv -o media/flv/sample-with-metadata.flv
  private static final String TEST_FILE_WITH_SEEK_TABLE = "media/flv/sample-with-metadata.flv";
  private static final long TEST_FILE_WITH_SEEK_TABLE_DURATION = 20_000_000;
  private static final int TAG_TYPE_VIDEO = 9; // from FlvExtractor

  private static final Random random = new Random(System.currentTimeMillis());

  private FlvExtractor extractor;
  private FakeExtractorOutput extractorOutput;
  private DefaultDataSource dataSource;

  @Before
  public void setUp() throws Exception {
    extractor = new FlvExtractor();
    extractorOutput = new FakeExtractorOutput();
    dataSource =
        new DefaultDataSourceFactory(ApplicationProvider.getApplicationContext())
            .createDataSource();
  }

  @Test
  public void flvExtractorReads_returnsSeekableSeekMap() throws Exception {
    Uri fileUri = TestUtil.buildAssetUri(TEST_FILE_WITH_SEEK_TABLE);

    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);

    assertThat(seekMap.isSeekable()).isTrue();
  }

  @Test
  public void flvExtractorReads_correctDuration() throws Exception {
    FakeExtractorOutput extractorOutput =
        extractAllSamplesFromFile(
            extractor, ApplicationProvider.getApplicationContext(), TEST_FILE_WITH_SEEK_TABLE);

    SeekMap seekMap = extractorOutput.seekMap;

    assertThat(seekMap.getDurationUs()).isEqualTo(TEST_FILE_WITH_SEEK_TABLE_DURATION);
  }

  @Test
  public void seeking_handlesSeekToZero() throws Exception {
    String fileName = TEST_FILE_WITH_SEEK_TABLE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(TAG_TYPE_VIDEO);

    long targetSeekTimeUs = 0;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    FakeTrackOutput expectedTrackOutput = getTrackOutput(fileName, TAG_TYPE_VIDEO);
    assertFirstFrameAfterSeekHasCorrectData(trackOutput, extractedFrameIndex, expectedTrackOutput);
  }

  @Test
  public void seeking_handlesSeekToEof() throws Exception {
    String fileName = TEST_FILE_WITH_SEEK_TABLE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = extractSeekMapAndFillFormat(
        extractor, extractorOutput, dataSource, fileUri, TAG_TYPE_VIDEO);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(TAG_TYPE_VIDEO);

    long targetSeekTimeUs = seekMap.getDurationUs();
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    FakeTrackOutput expectedTrackOutput = getTrackOutput(fileName, TAG_TYPE_VIDEO);
    assertFirstFrameAfterSeekHasCorrectData(trackOutput, extractedFrameIndex, expectedTrackOutput);
  }

  @Test
  public void seeking_handlesSeekingBackward() throws Exception {
    String fileName = TEST_FILE_WITH_SEEK_TABLE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);

    SeekMap seekMap = extractSeekMapAndFillFormat(
        extractor, extractorOutput, dataSource, fileUri, TAG_TYPE_VIDEO);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(TAG_TYPE_VIDEO);

    long firstSeekTimeUs = seekMap.getDurationUs() * 2 / 3;
    TestUtil.seekToTimeUs(extractor, seekMap, firstSeekTimeUs, dataSource, trackOutput, fileUri);
    long targetSeekTimeUs = seekMap.getDurationUs() / 3;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    FakeTrackOutput expectedTrackOutput = getTrackOutput(fileName, TAG_TYPE_VIDEO);
    assertFirstFrameAfterSeekHasCorrectData(trackOutput, extractedFrameIndex, expectedTrackOutput);
  }

  @Test
  public void seeking_handlesSeekingForward() throws Exception {
    String fileName = TEST_FILE_WITH_SEEK_TABLE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = extractSeekMapAndFillFormat(
        extractor, extractorOutput, dataSource, fileUri, TAG_TYPE_VIDEO);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(TAG_TYPE_VIDEO);

    long firstSeekTimeUs = seekMap.getDurationUs() / 3;
    TestUtil.seekToTimeUs(extractor, seekMap, firstSeekTimeUs, dataSource, trackOutput, fileUri);
    long targetSeekTimeUs = seekMap.getDurationUs() * 2 / 3;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    FakeTrackOutput expectedTrackOutput = getTrackOutput(fileName, TAG_TYPE_VIDEO);
    assertFirstFrameAfterSeekHasCorrectData(trackOutput, extractedFrameIndex, expectedTrackOutput);
  }

  @Test
  public void seeking_handlesRandomSeeks() throws IOException {
    String fileName = TEST_FILE_WITH_SEEK_TABLE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);

    SeekMap seekMap = extractSeekMapAndFillFormat
        (extractor, extractorOutput, dataSource, fileUri, TAG_TYPE_VIDEO);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(TAG_TYPE_VIDEO);
    FakeTrackOutput expectedTrackOutput = getTrackOutput(fileName, TAG_TYPE_VIDEO);

    long numSeek = 7;
    for (long i = 0; i < numSeek; i++) {
      long targetSeekTimeUs = random.nextInt((int)seekMap.getDurationUs() + 1);
      int extractedFrameIndex =
          TestUtil.seekToTimeUs(
              extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

      assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
      assertFirstFrameAfterSeekHasCorrectData(
          trackOutput, extractedFrameIndex, expectedTrackOutput);
    }
  }

  private static void assertFirstFrameAfterSeekHasCorrectData(
      FakeTrackOutput trackOutput, int firstFrameIndexAfterSeek, FakeTrackOutput expectedTrackOutput) {
    long foundTimeUs = trackOutput.getSampleTimeUs(firstFrameIndexAfterSeek);
    int foundFrameIndex = getFrameIndex(expectedTrackOutput, foundTimeUs);

    trackOutput.assertSample(
        firstFrameIndexAfterSeek,
        expectedTrackOutput.getSampleData(foundFrameIndex),
        expectedTrackOutput.getSampleTimeUs(foundFrameIndex),
        expectedTrackOutput.getSampleFlags(foundFrameIndex),
        expectedTrackOutput.getSampleCryptoData(foundFrameIndex));
  }

  private static FakeTrackOutput getTrackOutput(String fileName, int trackId) throws IOException {
    return extractAllSamplesFromFile(
            new FlvExtractor(),
            ApplicationProvider.getApplicationContext(),
            fileName)
        .trackOutputs
        .get(trackId);
  }

  private static int getFrameIndex(FakeTrackOutput trackOutput, long targetSeekTimeUs) {
    List<Long> frameTimes = trackOutput.getSampleTimesUs();
    return Util.binarySearchFloor(
        frameTimes, targetSeekTimeUs, /* inclusive= */ true, /* stayInBounds= */ false);
  }

  public static SeekMap extractSeekMapAndFillFormat(
      Extractor extractor, FakeExtractorOutput output, DataSource dataSource,
      Uri uri, int trackId)
      throws IOException {
    ExtractorInput input = getExtractorInputFromPosition(dataSource, /* position= */ 0, uri);
    extractor.init(output);
    PositionHolder positionHolder = new PositionHolder();
    int readResult = Extractor.RESULT_CONTINUE;
    while (true) {
      try {
        // Keep reading until we can get the seek map and the format
        while (readResult == Extractor.RESULT_CONTINUE
            && (output.seekMap == null
              || !output.tracksEnded
              || output.trackOutputs == null
              || output.trackOutputs.get(trackId) == null
              || output.trackOutputs.get(trackId).lastFormat == null)) {
          readResult = extractor.read(input, positionHolder);
        }
      } finally {
        Util.closeQuietly(dataSource);
      }

      if (readResult == Extractor.RESULT_SEEK) {
        input = getExtractorInputFromPosition(dataSource, positionHolder.position, uri);
        readResult = Extractor.RESULT_CONTINUE;
      } else if (readResult == Extractor.RESULT_END_OF_INPUT) {
        if (output.seekMap == null) {
          throw new IOException("EOF encountered without seekmap");
        }
        if (output.trackOutputs == null) {
          throw new IOException("EOF encountered without track");
        }
        if (output.trackOutputs.get(trackId) == null) {
          throw new IOException("EOF encountered without track with id " + trackId);
        }
        if (output.trackOutputs.get(trackId).lastFormat == null) {
          throw new IOException("EOF encountered without format");
        }
      }
      if (output.seekMap != null) {
        return output.seekMap;
      }
    }
  }
}
