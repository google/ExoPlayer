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
package androidx.media3.decoder.flac;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.extractor.SeekMap;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.FakeTrackOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Seeking tests for {@link FlacExtractor}. */
@RunWith(AndroidJUnit4.class)
public final class FlacExtractorSeekTest {

  private static final String TEST_FILE_SEEK_TABLE = "media/flac/bear.flac";
  private static final String TEST_FILE_BINARY_SEARCH = "media/flac/bear_one_metadata_block.flac";
  private static final String TEST_FILE_UNSEEKABLE =
      "media/flac/bear_no_seek_table_no_num_samples.flac";
  private static final int DURATION_US = 2_741_000;

  private final FlacExtractor extractor = new FlacExtractor();
  private final FakeExtractorOutput extractorOutput = new FakeExtractorOutput();
  private final DefaultDataSource dataSource =
      new DefaultDataSource.Factory(ApplicationProvider.getApplicationContext()).createDataSource();

  @Test
  public void flacExtractorReads_seekTable_returnSeekableSeekMap() throws IOException {
    Uri fileUri = TestUtil.buildAssetUri(TEST_FILE_SEEK_TABLE);

    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);

    assertThat(seekMap).isNotNull();
    assertThat(seekMap.getDurationUs()).isEqualTo(DURATION_US);
    assertThat(seekMap.isSeekable()).isTrue();
  }

  @Test
  public void seeking_seekTable_handlesSeekToZero() throws IOException {
    String fileName = TEST_FILE_SEEK_TABLE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = 0;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    assertFirstFrameAfterSeekPrecedesTargetSeekTime(
        fileName, trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void seeking_seekTable_handlesSeekToEoF() throws IOException {
    String fileName = TEST_FILE_SEEK_TABLE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = seekMap.getDurationUs();
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    assertFirstFrameAfterSeekPrecedesTargetSeekTime(
        fileName, trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void seeking_seekTable_handlesSeekingBackward() throws IOException {
    String fileName = TEST_FILE_SEEK_TABLE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long firstSeekTimeUs = 1_234_000;
    TestUtil.seekToTimeUs(extractor, seekMap, firstSeekTimeUs, dataSource, trackOutput, fileUri);
    long targetSeekTimeUs = 987_000;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    assertFirstFrameAfterSeekPrecedesTargetSeekTime(
        fileName, trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void seeking_seekTable_handlesSeekingForward() throws IOException {
    String fileName = TEST_FILE_SEEK_TABLE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long firstSeekTimeUs = 987_000;
    TestUtil.seekToTimeUs(extractor, seekMap, firstSeekTimeUs, dataSource, trackOutput, fileUri);
    long targetSeekTimeUs = 1_234_000;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    assertFirstFrameAfterSeekPrecedesTargetSeekTime(
        fileName, trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void flacExtractorReads_binarySearch_returnSeekableSeekMap() throws IOException {
    Uri fileUri = TestUtil.buildAssetUri(TEST_FILE_BINARY_SEARCH);

    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);

    assertThat(seekMap).isNotNull();
    assertThat(seekMap.getDurationUs()).isEqualTo(DURATION_US);
    assertThat(seekMap.isSeekable()).isTrue();
  }

  @Test
  public void seeking_binarySearch_handlesSeekToZero() throws IOException {
    String fileName = TEST_FILE_BINARY_SEARCH;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = 0;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    assertFirstFrameAfterSeekContainsTargetSeekTime(
        fileName, trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void seeking_binarySearch_handlesSeekToEoF() throws IOException {
    String fileName = TEST_FILE_BINARY_SEARCH;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = seekMap.getDurationUs();
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    assertFirstFrameAfterSeekContainsTargetSeekTime(
        fileName, trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void seeking_binarySearch_handlesSeekingBackward() throws IOException {
    String fileName = TEST_FILE_BINARY_SEARCH;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long firstSeekTimeUs = 1_234_000;
    TestUtil.seekToTimeUs(extractor, seekMap, firstSeekTimeUs, dataSource, trackOutput, fileUri);
    long targetSeekTimeUs = 987_00;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    assertFirstFrameAfterSeekContainsTargetSeekTime(
        fileName, trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void seeking_binarySearch_handlesSeekingForward() throws IOException {
    String fileName = TEST_FILE_BINARY_SEARCH;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long firstSeekTimeUs = 987_000;
    TestUtil.seekToTimeUs(extractor, seekMap, firstSeekTimeUs, dataSource, trackOutput, fileUri);
    long targetSeekTimeUs = 1_234_000;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    assertFirstFrameAfterSeekContainsTargetSeekTime(
        fileName, trackOutput, targetSeekTimeUs, extractedFrameIndex);
  }

  @Test
  public void flacExtractorReads_unseekable_returnUnseekableSeekMap() throws IOException {
    Uri fileUri = TestUtil.buildAssetUri(TEST_FILE_UNSEEKABLE);

    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);

    assertThat(seekMap).isNotNull();
    assertThat(seekMap.getDurationUs()).isEqualTo(C.TIME_UNSET);
    assertThat(seekMap.isSeekable()).isFalse();
  }

  private static void assertFirstFrameAfterSeekContainsTargetSeekTime(
      String fileName,
      FakeTrackOutput trackOutput,
      long targetSeekTimeUs,
      int firstFrameIndexAfterSeek)
      throws IOException {
    FakeTrackOutput expectedTrackOutput = getExpectedTrackOutput(fileName);
    int expectedFrameIndex = getFrameIndex(expectedTrackOutput, targetSeekTimeUs);

    trackOutput.assertSample(
        firstFrameIndexAfterSeek,
        expectedTrackOutput.getSampleData(expectedFrameIndex),
        expectedTrackOutput.getSampleTimeUs(expectedFrameIndex),
        expectedTrackOutput.getSampleFlags(expectedFrameIndex),
        expectedTrackOutput.getSampleCryptoData(expectedFrameIndex));
  }

  private static void assertFirstFrameAfterSeekPrecedesTargetSeekTime(
      String fileName,
      FakeTrackOutput trackOutput,
      long targetSeekTimeUs,
      int firstFrameIndexAfterSeek)
      throws IOException {
    FakeTrackOutput expectedTrackOutput = getExpectedTrackOutput(fileName);
    int maxFrameIndex = getFrameIndex(expectedTrackOutput, targetSeekTimeUs);

    long firstFrameAfterSeekTimeUs = trackOutput.getSampleTimeUs(firstFrameIndexAfterSeek);
    assertThat(firstFrameAfterSeekTimeUs).isAtMost(targetSeekTimeUs);

    boolean frameFound = false;
    for (int i = maxFrameIndex; i >= 0; i--) {
      if (firstFrameAfterSeekTimeUs == expectedTrackOutput.getSampleTimeUs(i)) {
        trackOutput.assertSample(
            firstFrameIndexAfterSeek,
            expectedTrackOutput.getSampleData(i),
            expectedTrackOutput.getSampleTimeUs(i),
            expectedTrackOutput.getSampleFlags(i),
            expectedTrackOutput.getSampleCryptoData(i));
        frameFound = true;
        break;
      }
    }

    assertThat(frameFound).isTrue();
  }

  private static FakeTrackOutput getExpectedTrackOutput(String fileName) throws IOException {
    return TestUtil.extractAllSamplesFromFile(
            new FlacExtractor(), ApplicationProvider.getApplicationContext(), fileName)
        .trackOutputs
        .get(0);
  }

  private static int getFrameIndex(FakeTrackOutput expectedTrackOutput, long targetSeekTimeUs) {
    List<Long> frameTimes = expectedTrackOutput.getSampleTimesUs();
    return Util.binarySearchFloor(
        frameTimes, targetSeekTimeUs, /* inclusive= */ true, /* stayInBounds= */ false);
  }
}
