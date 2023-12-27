/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.muxer;

import static androidx.media3.muxer.MuxerTestUtil.FAKE_VIDEO_FORMAT;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.media.MediaCodec.BufferInfo;
import android.util.Pair;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.muxer.Mp4Muxer.TrackToken;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** End to end tests for {@link Mp4Muxer}. */
@RunWith(AndroidJUnit4.class)
public class Mp4MuxerEndToEndTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void createMp4File_addTrackAndMetadataButNoSamples_createsEmptyFile() throws IOException {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer mp4Muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();

    try {
      mp4Muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      mp4Muxer.setOrientation(90);
      mp4Muxer.addMetadata("key", "value");
    } finally {
      mp4Muxer.close();
    }

    byte[] outputFileBytes = TestUtil.getByteArrayFromFilePath(outputFilePath);
    assertThat(outputFileBytes).isEmpty();
  }

  @Test
  public void createMp4File_withSameTracksOffset_matchesExpected() throws IOException {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer mp4Muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();
    mp4Muxer.setModificationTime(/* timestampMs= */ 500_000_000L);
    Pair<ByteBuffer, BufferInfo> track1Sample1 =
        MuxerTestUtil.getFakeSampleAndSampleInfo(/* presentationTimeUs= */ 100L);
    Pair<ByteBuffer, BufferInfo> track1Sample2 =
        MuxerTestUtil.getFakeSampleAndSampleInfo(/* presentationTimeUs= */ 200L);
    Pair<ByteBuffer, BufferInfo> track2Sample1 =
        MuxerTestUtil.getFakeSampleAndSampleInfo(/* presentationTimeUs= */ 100L);
    Pair<ByteBuffer, BufferInfo> track2Sample2 =
        MuxerTestUtil.getFakeSampleAndSampleInfo(/* presentationTimeUs= */ 300L);

    try {
      TrackToken track1 = mp4Muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      mp4Muxer.writeSampleData(track1, track1Sample1.first, track1Sample1.second);
      mp4Muxer.writeSampleData(track1, track1Sample2.first, track1Sample2.second);

      // Add same track again but with different samples.
      TrackToken track2 = mp4Muxer.addTrack(/* sortKey= */ 1, FAKE_VIDEO_FORMAT);
      mp4Muxer.writeSampleData(track2, track2Sample1.first, track2Sample1.second);
      mp4Muxer.writeSampleData(track2, track2Sample2.first, track2Sample2.second);
    } finally {
      mp4Muxer.close();
    }

    // Presentation timestamps in dump file are:
    // Track 1 Sample 1 = 0L
    // Track 1 Sample 2 = 100L
    // Track 2 Sample 1 = 0L
    // Track 2 Sample 2 = 200L
    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(new Mp4Extractor(), outputFilePath);
    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        fakeExtractorOutput,
        MuxerTestUtil.getExpectedDumpFilePath("mp4_with_same_tracks_offset.mp4"));
  }

  @Test
  public void createMp4File_withDifferentTracksOffset_matchesExpected() throws IOException {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer mp4Muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();
    mp4Muxer.setModificationTime(/* timestampMs= */ 500_000_000L);
    Pair<ByteBuffer, BufferInfo> track1Sample1 =
        MuxerTestUtil.getFakeSampleAndSampleInfo(/* presentationTimeUs= */ 0L);
    Pair<ByteBuffer, BufferInfo> track1Sample2 =
        MuxerTestUtil.getFakeSampleAndSampleInfo(/* presentationTimeUs= */ 100L);
    Pair<ByteBuffer, BufferInfo> track2Sample1 =
        MuxerTestUtil.getFakeSampleAndSampleInfo(/* presentationTimeUs= */ 100L);
    Pair<ByteBuffer, BufferInfo> track2Sample2 =
        MuxerTestUtil.getFakeSampleAndSampleInfo(/* presentationTimeUs= */ 200L);

    try {
      TrackToken track1 = mp4Muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      mp4Muxer.writeSampleData(track1, track1Sample1.first, track1Sample1.second);
      mp4Muxer.writeSampleData(track1, track1Sample2.first, track1Sample2.second);

      // Add same track again but with different samples.
      TrackToken track2 = mp4Muxer.addTrack(/* sortKey= */ 1, FAKE_VIDEO_FORMAT);
      mp4Muxer.writeSampleData(track2, track2Sample1.first, track2Sample1.second);
      mp4Muxer.writeSampleData(track2, track2Sample2.first, track2Sample2.second);
    } finally {
      mp4Muxer.close();
    }

    // The presentation time of second track's first sample is forcefully changed to 0L.
    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(new Mp4Extractor(), outputFilePath);
    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        fakeExtractorOutput,
        MuxerTestUtil.getExpectedDumpFilePath("mp4_with_different_tracks_offset.mp4"));
  }

  @Test
  public void writeSampleData_withOutOfOrderSampleTimestamps_throws() throws IOException {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer mp4Muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();
    Pair<ByteBuffer, BufferInfo> track1Sample1 =
        MuxerTestUtil.getFakeSampleAndSampleInfo(/* presentationTimeUs= */ 0L);
    Pair<ByteBuffer, BufferInfo> track1Sample2 =
        MuxerTestUtil.getFakeSampleAndSampleInfo(/* presentationTimeUs= */ 2000L);
    Pair<ByteBuffer, BufferInfo> track1Sample3 =
        MuxerTestUtil.getFakeSampleAndSampleInfo(/* presentationTimeUs= */ 1000L);
    try {
      TrackToken track1 = mp4Muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      mp4Muxer.writeSampleData(track1, track1Sample1.first, track1Sample1.second);
      mp4Muxer.writeSampleData(track1, track1Sample2.first, track1Sample2.second);

      assertThrows(
          IllegalArgumentException.class,
          () -> mp4Muxer.writeSampleData(track1, track1Sample3.first, track1Sample3.second));
    } finally {
      mp4Muxer.close();
    }
  }
}
