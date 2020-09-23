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
package com.google.android.exoplayer2.extractor.mp3;

import static com.google.android.exoplayer2.extractor.mp3.Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING;
import static com.google.android.exoplayer2.testutil.TestUtil.extractAllSamplesFromFile;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link IndexSeeker}. */
@RunWith(AndroidJUnit4.class)
public class IndexSeekerTest {

  private static final String TEST_FILE_NO_SEEK_TABLE = "media/mp3/bear-vbr-no-seek-table.mp3";
  private static final int TEST_FILE_NO_SEEK_TABLE_DURATION = 2_808_000;

  private Mp3Extractor extractor;
  private FakeExtractorOutput extractorOutput;
  private DefaultDataSource dataSource;

  @Before
  public void setUp() throws Exception {
    extractor = new Mp3Extractor(FLAG_ENABLE_INDEX_SEEKING);
    extractorOutput = new FakeExtractorOutput();
    dataSource =
        new DefaultDataSourceFactory(ApplicationProvider.getApplicationContext())
            .createDataSource();
  }

  @Test
  public void mp3ExtractorReads_returnsSeekableSeekMap() throws Exception {
    Uri fileUri = TestUtil.buildAssetUri(TEST_FILE_NO_SEEK_TABLE);

    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);

    assertThat(seekMap.isSeekable()).isTrue();
  }

  @Test
  public void mp3ExtractorReads_correctsInexactDuration() throws Exception {
    FakeExtractorOutput extractorOutput =
        TestUtil.extractAllSamplesFromFile(
            extractor, ApplicationProvider.getApplicationContext(), TEST_FILE_NO_SEEK_TABLE);

    SeekMap seekMap = extractorOutput.seekMap;

    assertThat(seekMap.getDurationUs()).isEqualTo(TEST_FILE_NO_SEEK_TABLE_DURATION);
  }

  @Test
  public void seeking_handlesSeekToZero() throws Exception {
    String fileName = TEST_FILE_NO_SEEK_TABLE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = 0;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    assertFirstFrameAfterSeekIsWithinMinDifference(
        fileName, trackOutput, targetSeekTimeUs, extractedFrameIndex);
    assertFirstFrameAfterSeekHasCorrectData(fileName, trackOutput, extractedFrameIndex);
  }

  @Test
  public void seeking_handlesSeekToEof() throws Exception {
    String fileName = TEST_FILE_NO_SEEK_TABLE;
    Uri fileUri = TestUtil.buildAssetUri(fileName);
    SeekMap seekMap = TestUtil.extractSeekMap(extractor, extractorOutput, dataSource, fileUri);
    FakeTrackOutput trackOutput = extractorOutput.trackOutputs.get(0);

    long targetSeekTimeUs = TEST_FILE_NO_SEEK_TABLE_DURATION;
    int extractedFrameIndex =
        TestUtil.seekToTimeUs(
            extractor, seekMap, targetSeekTimeUs, dataSource, trackOutput, fileUri);

    assertThat(extractedFrameIndex).isNotEqualTo(C.INDEX_UNSET);
    assertFirstFrameAfterSeekIsWithinMinDifference(
        fileName, trackOutput, targetSeekTimeUs, extractedFrameIndex);
    assertFirstFrameAfterSeekHasCorrectData(fileName, trackOutput, extractedFrameIndex);
  }

  @Test
  public void seeking_handlesSeekingBackward() throws Exception {
    String fileName = TEST_FILE_NO_SEEK_TABLE;
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
    assertFirstFrameAfterSeekIsWithinMinDifference(
        fileName, trackOutput, targetSeekTimeUs, extractedFrameIndex);
    assertFirstFrameAfterSeekHasCorrectData(fileName, trackOutput, extractedFrameIndex);
  }

  @Test
  public void seeking_handlesSeekingForward() throws Exception {
    String fileName = TEST_FILE_NO_SEEK_TABLE;
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
    assertFirstFrameAfterSeekIsWithinMinDifference(
        fileName, trackOutput, targetSeekTimeUs, extractedFrameIndex);
    assertFirstFrameAfterSeekHasCorrectData(fileName, trackOutput, extractedFrameIndex);
  }

  private static void assertFirstFrameAfterSeekIsWithinMinDifference(
      String fileName,
      FakeTrackOutput trackOutput,
      long targetSeekTimeUs,
      int firstFrameIndexAfterSeek)
      throws IOException {
    FakeTrackOutput expectedTrackOutput = getExpectedTrackOutput(fileName);
    int exactFrameIndex = getFrameIndex(expectedTrackOutput, targetSeekTimeUs);
    long exactFrameTimeUs = expectedTrackOutput.getSampleTimeUs(exactFrameIndex);
    long foundTimeUs = trackOutput.getSampleTimeUs(firstFrameIndexAfterSeek);

    assertThat(exactFrameTimeUs - foundTimeUs).isAtMost(IndexSeeker.MIN_TIME_BETWEEN_POINTS_US);
  }

  private static void assertFirstFrameAfterSeekHasCorrectData(
      String fileName, FakeTrackOutput trackOutput, int firstFrameIndexAfterSeek)
      throws IOException {
    FakeTrackOutput expectedTrackOutput = getExpectedTrackOutput(fileName);
    long foundTimeUs = trackOutput.getSampleTimeUs(firstFrameIndexAfterSeek);
    int foundFrameIndex = getFrameIndex(expectedTrackOutput, foundTimeUs);

    trackOutput.assertSample(
        firstFrameIndexAfterSeek,
        expectedTrackOutput.getSampleData(foundFrameIndex),
        expectedTrackOutput.getSampleTimeUs(foundFrameIndex),
        expectedTrackOutput.getSampleFlags(foundFrameIndex),
        expectedTrackOutput.getSampleCryptoData(foundFrameIndex));
  }

  private static FakeTrackOutput getExpectedTrackOutput(String fileName) throws IOException {
    return extractAllSamplesFromFile(
            new Mp3Extractor(FLAG_ENABLE_INDEX_SEEKING),
            ApplicationProvider.getApplicationContext(),
            fileName)
        .trackOutputs
        .get(0);
  }

  private static int getFrameIndex(FakeTrackOutput trackOutput, long targetSeekTimeUs) {
    List<Long> frameTimes = trackOutput.getSampleTimesUs();
    return Util.binarySearchFloor(
        frameTimes, targetSeekTimeUs, /* inclusive= */ true, /* stayInBounds= */ false);
  }
}
