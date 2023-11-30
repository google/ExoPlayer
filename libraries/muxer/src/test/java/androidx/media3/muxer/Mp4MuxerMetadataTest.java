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

import android.content.Context;
import android.media.MediaCodec.BufferInfo;
import android.util.Pair;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.muxer.Mp4Muxer.TrackToken;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.DumpableMp4Box;
import androidx.media3.test.utils.FakeExtractorOutput;
import androidx.media3.test.utils.TestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

/** Tests for metadata written by {@link Mp4Muxer}. */
@RunWith(AndroidJUnit4.class)
public class Mp4MuxerMetadataTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  // Input files.
  private static final String XMP_SAMPLE_DATA = "media/xmp/sample_datetime_xmp.xmp";

  private final Context context = ApplicationProvider.getApplicationContext();
  private final Pair<ByteBuffer, BufferInfo> sampleAndSampleInfo =
      MuxerTestUtil.getFakeSampleAndSampleInfo(/* presentationTimeUs= */ 0L);

  @Test
  public void writeMp4File_orientationNotSet_setsOrientationTo0() throws Exception {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();

    try {
      muxer.setModificationTime(/* timestampMs= */ 5_000_000L);
      TrackToken token = muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      muxer.writeSampleData(token, sampleAndSampleInfo.first, sampleAndSampleInfo.second);
    } finally {
      muxer.close();
    }

    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(new Mp4Extractor(), outputFilePath);
    // No rotationDegrees field in output dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        MuxerTestUtil.getExpectedDumpFilePath("mp4_with_0_orientation.mp4"));
  }

  @Test
  public void writeMp4File_setOrientationTo90_setsOrientationTo90() throws Exception {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();

    try {
      muxer.setModificationTime(/* timestampMs= */ 5_000_000L);
      TrackToken token = muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      muxer.writeSampleData(token, sampleAndSampleInfo.first, sampleAndSampleInfo.second);

      muxer.setOrientation(90);
    } finally {
      muxer.close();
    }

    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(new Mp4Extractor(), outputFilePath);
    // rotationDegrees = 90 in the output dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        MuxerTestUtil.getExpectedDumpFilePath("mp4_with_90_orientation.mp4"));
  }

  @Test
  public void writeMp4File_setOrientationTo180_setsOrientationTo180() throws Exception {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();

    try {
      muxer.setModificationTime(/* timestampMs= */ 5_000_000L);
      TrackToken token = muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      muxer.writeSampleData(token, sampleAndSampleInfo.first, sampleAndSampleInfo.second);

      muxer.setOrientation(180);
    } finally {
      muxer.close();
    }

    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(new Mp4Extractor(), outputFilePath);
    // rotationDegrees = 180 in the output dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        MuxerTestUtil.getExpectedDumpFilePath("mp4_with_180_orientation.mp4"));
  }

  @Test
  public void writeMp4File_setOrientationTo270_setsOrientationTo270() throws Exception {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();

    try {
      muxer.setModificationTime(/* timestampMs= */ 5_000_000L);
      TrackToken token = muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      muxer.writeSampleData(token, sampleAndSampleInfo.first, sampleAndSampleInfo.second);

      muxer.setOrientation(270);
    } finally {
      muxer.close();
    }

    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(new Mp4Extractor(), outputFilePath);
    // rotationDegrees = 270 in the output dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        MuxerTestUtil.getExpectedDumpFilePath("mp4_with_270_orientation.mp4"));
  }

  @Test
  public void writeMp4File_setLocation_setsSameLocation() throws Exception {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();

    try {
      muxer.setModificationTime(/* timestampMs= */ 5_000_000L);
      TrackToken token = muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      muxer.writeSampleData(token, sampleAndSampleInfo.first, sampleAndSampleInfo.second);
      muxer.setLocation(33.0f, -120f);
    } finally {
      muxer.close();
    }

    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(new Mp4Extractor(), outputFilePath);
    // Xyz data in track metadata dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        MuxerTestUtil.getExpectedDumpFilePath("mp4_with_location.mp4"));
  }

  @Test
  public void writeMp4File_locationNotSet_setsLocationToNull() throws Exception {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();

    try {
      muxer.setModificationTime(/* timestampMs= */ 5_000_000L);
      TrackToken token = muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      muxer.writeSampleData(token, sampleAndSampleInfo.first, sampleAndSampleInfo.second);
    } finally {
      muxer.close();
    }

    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(new Mp4Extractor(), outputFilePath);
    // No xyz data in track metadata dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        MuxerTestUtil.getExpectedDumpFilePath("mp4_with_null_location.mp4"));
  }

  @Test
  public void writeMp4File_setFrameRate_setsSameFrameRate() throws Exception {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();

    try {
      muxer.setModificationTime(/* timestampMs= */ 5_000_000L);
      muxer.setCaptureFps(120.0f);
      TrackToken token = muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      muxer.writeSampleData(token, sampleAndSampleInfo.first, sampleAndSampleInfo.second);
    } finally {
      muxer.close();
    }

    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(new Mp4Extractor(), outputFilePath);
    // android.capture.fps data in the track metadata dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        MuxerTestUtil.getExpectedDumpFilePath("mp4_with_frame_rate.mp4"));
  }

  @Test
  public void writeMp4File_addStringMetadata_matchesExpected() throws Exception {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();

    try {
      muxer.setModificationTime(/* timestampMs= */ 5_000_000L);
      muxer.addMetadata("SomeStringKey", "Some Random String");
      TrackToken token = muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      muxer.writeSampleData(token, sampleAndSampleInfo.first, sampleAndSampleInfo.second);
    } finally {
      muxer.close();
    }

    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(new Mp4Extractor(), outputFilePath);
    // Added string metadata should be present in the track metadata dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        MuxerTestUtil.getExpectedDumpFilePath("mp4_with_string_metadata.mp4"));
  }

  @Test
  public void writeMp4File_addFloatMetadata_matchesExpected() throws Exception {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();

    try {
      muxer.setModificationTime(/* timestampMs= */ 5_000_000L);
      muxer.addMetadata("SomeStringKey", 10.0f);
      TrackToken token = muxer.addTrack(/* sortKey= */ 0, FAKE_VIDEO_FORMAT);
      muxer.writeSampleData(token, sampleAndSampleInfo.first, sampleAndSampleInfo.second);
    } finally {
      muxer.close();
    }

    FakeExtractorOutput fakeExtractorOutput =
        TestUtil.extractAllSamplesFromFilePath(new Mp4Extractor(), outputFilePath);
    // Added float metadata should be present in the track metadata dump.
    DumpFileAsserts.assertOutput(
        context,
        fakeExtractorOutput,
        MuxerTestUtil.getExpectedDumpFilePath("mp4_with_float_metadata.mp4"));
  }

  @Test
  public void writeMp4File_addXmp_matchesExpected() throws Exception {
    String outputFilePath = temporaryFolder.newFile().getPath();
    Mp4Muxer muxer = new Mp4Muxer.Builder(new FileOutputStream(outputFilePath)).build();

    try {
      muxer.setModificationTime(/* timestampMs= */ 5_000_000L);
      Context context = ApplicationProvider.getApplicationContext();
      byte[] xmpBytes = TestUtil.getByteArray(context, XMP_SAMPLE_DATA);
      ByteBuffer xmp = ByteBuffer.wrap(xmpBytes);
      muxer.addXmp(xmp);
      xmp.rewind();
      TrackToken token = muxer.addTrack(0, FAKE_VIDEO_FORMAT);
      muxer.writeSampleData(token, sampleAndSampleInfo.first, sampleAndSampleInfo.second);
    } finally {
      muxer.close();
    }

    // TODO: b/288544833 - Use FakeExtractorOutput once it starts dumping uuid box.
    DumpableMp4Box dumpableBox =
        new DumpableMp4Box(ByteBuffer.wrap(TestUtil.getByteArrayFromFilePath(outputFilePath)));
    // The uuid box should be present in the output MP4.
    DumpFileAsserts.assertOutput(
        context, dumpableBox, MuxerTestUtil.getExpectedDumpFilePath("mp4_with_xmp.mp4"));
  }
}
