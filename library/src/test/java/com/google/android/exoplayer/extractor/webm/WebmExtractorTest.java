/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.extractor.webm;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.extractor.ChunkIndex;
import com.google.android.exoplayer.extractor.DefaultExtractorInput;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.testutil.FakeDataSource;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.net.Uri;
import android.test.InstrumentationTestCase;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

/**
 * Tests for {@link WebmExtractor}.
 */
public class WebmExtractorTest extends InstrumentationTestCase {

  private static final int CUE_POINT_ELEMENT_BYTE_SIZE = 31;

  private static final int DEFAULT_TIMECODE_SCALE = 1000000;

  private static final long TEST_DURATION_US = 9920000L;
  private static final int TEST_WIDTH = 1280;
  private static final int TEST_HEIGHT = 720;
  private static final int TEST_CHANNEL_COUNT = 1;
  private static final int TEST_SAMPLE_RATE = 48000;
  private static final long TEST_CODEC_DELAY = 6500000;
  private static final long TEST_SEEK_PRE_ROLL = 80000000;
  private static final int TEST_OPUS_CODEC_PRIVATE_SIZE = 2;
  private static final String TEST_VORBIS_CODEC_PRIVATE = "webm/vorbis_codec_private";
  private static final int TEST_VORBIS_INFO_SIZE = 30;
  private static final int TEST_VORBIS_BOOKS_SIZE = 4140;
  private static final byte[] TEST_ENCRYPTION_KEY_ID = { 0x00, 0x01, 0x02, 0x03 };
  private static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
  private static final UUID ZERO_UUID = new UUID(0, 0);
  private static final byte[] TEST_INITIALIZATION_VECTOR = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07
  };

  private static final int ID_VP9 = 0;
  private static final int ID_OPUS = 1;
  private static final int ID_VORBIS = 2;
  private static final int ID_DUMMY = 3;

  private WebmExtractor extractor;
  private TestExtractorOutput extractorOutput;
  private TestTrackOutput audioOutput;
  private TestTrackOutput videoOutput;

  @Override
  public void setUp() {
    extractor = new WebmExtractor();
    extractorOutput = new TestExtractorOutput();
    audioOutput = new TestTrackOutput();
    videoOutput = new TestTrackOutput();
    extractor.init(extractorOutput);
  }

  @Override
  public void tearDown() {
    extractor = null;
    extractorOutput = null;
    audioOutput = null;
    videoOutput = null;
  }

  public void testReadInitializationSegment() throws IOException, InterruptedException {
    consume(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_VP9}, null));
    assertVideoFormat();
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareOpus() throws IOException, InterruptedException {
    consume(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_OPUS}, null));
    assertAudioFormat(ID_OPUS);
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareVorbis() throws IOException, InterruptedException {
    consume(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_VORBIS}, null));
    assertAudioFormat(ID_VORBIS);
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareTwoTracks() throws IOException, InterruptedException {
    consume(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_VP9, ID_OPUS}, null));
    assertEquals(2, extractorOutput.numberOfTracks);
    assertVideoFormat();
    assertAudioFormat(ID_OPUS);
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareThreeTracks() throws IOException, InterruptedException {
    consume(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_VP9, ID_DUMMY, ID_OPUS}, null));
    // Eventhough the input stream has 3 tracks, only 2 of them are supported and will be reported.
    assertEquals(2, extractorOutput.numberOfTracks);
    assertVideoFormat();
    assertAudioFormat(ID_OPUS);
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareFourTracks() throws IOException, InterruptedException {
    consume(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_VP9, ID_VORBIS, ID_VP9, ID_OPUS}, null));
    // Eventhough the input stream has 4 supported tracks, only the first video and audio track will
    // be reported.
    assertEquals(2, extractorOutput.numberOfTracks);
    assertVideoFormat();
    assertAudioFormat(ID_VORBIS);
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareContentEncodingEncryption() throws IOException, InterruptedException {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 1, 5, 1);
    consume(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_VP9}, settings));
    assertVideoFormat();
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
    DrmInitData drmInitData = extractorOutput.drmInitData;
    assertNotNull(drmInitData);
    android.test.MoreAsserts.assertEquals(TEST_ENCRYPTION_KEY_ID, drmInitData.get(WIDEVINE_UUID));
    android.test.MoreAsserts.assertEquals(TEST_ENCRYPTION_KEY_ID, drmInitData.get(ZERO_UUID));
  }

  public void testPrepareThreeCuePoints() throws IOException, InterruptedException {
    consume(
        createInitializationSegment(3, 0, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_VP9}, null));
    assertVideoFormat();
    assertIndex(
        new IndexPoint(0, 0, 10000),
        new IndexPoint(10000, 0, 10000),
        new IndexPoint(20000, 0, TEST_DURATION_US - 20000));
  }

  public void testPrepareCustomTimecodeScale() throws IOException, InterruptedException {
    consume(createInitializationSegment(3, 0, true, 1000, new int[] {ID_VP9}, null));
    assertVideoFormat();
    assertIndex(
        new IndexPoint(0, 0, 10),
        new IndexPoint(10, 0, 10),
        new IndexPoint(20, 0, (TEST_DURATION_US / 1000) - 20));
  }

  public void testPrepareNoCuePoints() throws IOException, InterruptedException {
    try {
      consume(
          createInitializationSegment(0, 0, true, DEFAULT_TIMECODE_SCALE,
              new int[] {ID_VP9}, null));
      fail();
    } catch (ParserException exception) {
      assertEquals("Invalid/missing cue points", exception.getMessage());
    }
  }

  public void testPrepareInvalidDocType() throws IOException, InterruptedException {
    try {
      consume(
          createInitializationSegment(1, 0, false, DEFAULT_TIMECODE_SCALE,
              new int[] {ID_VP9}, null));
      fail();
    } catch (ParserException exception) {
      assertEquals("DocType webB not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidContentEncodingOrder() throws IOException, InterruptedException {
    ContentEncodingSettings settings = new ContentEncodingSettings(1, 1, 1, 5, 1);
    try {
      consume(
          createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE,
              new int[] {ID_VP9}, settings));
      fail();
    } catch (ParserException exception) {
      assertEquals("ContentEncodingOrder 1 not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidContentEncodingScope() throws IOException, InterruptedException {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 0, 1, 5, 1);
    try {
      consume(
          createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE,
              new int[] {ID_VP9}, settings));
      fail();
    } catch (ParserException exception) {
      assertEquals("ContentEncodingScope 0 not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidContentEncodingType() throws IOException, InterruptedException {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 0, 5, 1);
    try {
      consume(
          createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE,
              new int[] {ID_VP9}, settings));
      fail();
    } catch (ParserException exception) {
      assertEquals("ContentEncodingType 0 not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidContentEncAlgo() throws IOException, InterruptedException {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 1, 4, 1);
    try {
      consume(
          createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE,
              new int[] {ID_VP9}, settings));
      fail();
    } catch (ParserException exception) {
      assertEquals("ContentEncAlgo 4 not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidAESSettingsCipherMode() throws IOException, InterruptedException {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 1, 5, 0);
    try {
      consume(
          createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE,
              new int[] {ID_VP9}, settings));
      fail();
    } catch (ParserException exception) {
      assertEquals("AESSettingsCipherMode 0 not supported", exception.getMessage());
    }
  }

  public void testReadSampleKeyframe() throws IOException, InterruptedException {
    MediaSegment mediaSegment =
        createMediaSegment(100, 0, 0, true, false, true, false, false, 1);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_VP9}, null),
        mediaSegment.clusterBytes);
    consume(testInputData);
    assertVideoFormat();
    assertSample(mediaSegment, 0, true, false, false, videoOutput);
  }

  public void testReadTwoTrackSamples() throws IOException, InterruptedException {
    MediaSegment mediaSegmentAudio =
        createMediaSegment(100, 0, 0, true, false, true, false, false, 2);
    MediaSegment mediaSegmentVideo =
        createMediaSegment(100, 0, 0, true, false, true, false, false, 1);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegmentAudio.clusterBytes.length + mediaSegmentVideo.clusterBytes.length,
            true, DEFAULT_TIMECODE_SCALE, new int[] {ID_VP9, ID_OPUS}, null),
        mediaSegmentVideo.clusterBytes, mediaSegmentAudio.clusterBytes);
    consume(testInputData);
    assertEquals(2, extractorOutput.numberOfTracks);
    assertVideoFormat();
    assertAudioFormat(ID_OPUS);
    assertSample(mediaSegmentVideo, 0, true, false, false, videoOutput);
    assertSample(mediaSegmentAudio, 0, true, false, false, audioOutput);
  }

  public void testReadTwoTrackSamplesWithSkippedTrack() throws IOException, InterruptedException {
    MediaSegment mediaSegmentAudio =
        createMediaSegment(100, 0, 0, true, false, true, false, false, 2);
    MediaSegment mediaSegmentVideo =
        createMediaSegment(100, 0, 0, true, false, true, false, false, 1);
    MediaSegment mediaSegmentDummy =
        createMediaSegment(100, 0, 0, true, false, true, false, false, 17);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1,
            mediaSegmentAudio.clusterBytes.length + mediaSegmentVideo.clusterBytes.length
                + mediaSegmentDummy.clusterBytes.length,
            true, DEFAULT_TIMECODE_SCALE, new int[] {ID_DUMMY, ID_VP9, ID_OPUS}, null),
        mediaSegmentVideo.clusterBytes,
        mediaSegmentDummy.clusterBytes,
        mediaSegmentAudio.clusterBytes);
    consume(testInputData);
    assertEquals(2, extractorOutput.numberOfTracks);
    assertVideoFormat();
    assertAudioFormat(ID_OPUS);
    assertSample(mediaSegmentVideo, 0, true, false, false, videoOutput);
    assertSample(mediaSegmentAudio, 0, true, false, false, audioOutput);
  }

  public void testReadBlock() throws IOException, InterruptedException {
    MediaSegment mediaSegment =
        createMediaSegment(100, 0, 0, true, false, false, false, false, 2);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_OPUS}, null),
        mediaSegment.clusterBytes);
    consume(testInputData);
    assertAudioFormat(ID_OPUS);
    assertSample(mediaSegment, 0, true, false, false, audioOutput);
  }

  public void testReadBlockNonKeyframe() throws IOException, InterruptedException {
    MediaSegment mediaSegment =
        createMediaSegment(100, 0, 0, false, false, false, false, false, 1);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE,
            new int[] { ID_VP9 }, null),
        mediaSegment.clusterBytes);
    consume(testInputData);
    assertVideoFormat();
    assertSample(mediaSegment, 0, false, false, false, videoOutput);
  }

  public void testReadEncryptedFrame() throws IOException, InterruptedException {
    MediaSegment mediaSegment = createMediaSegment(100, 0, 0, true, false, true, true, true, 1);
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 1, 5, 1);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_VP9}, settings),
        mediaSegment.clusterBytes);
    consume(testInputData);
    assertVideoFormat();
    assertSample(mediaSegment, 0, true, false, true, videoOutput);
  }

  public void testReadEncryptedFrameWithInvalidSignalByte()
      throws IOException, InterruptedException {
    MediaSegment mediaSegment = createMediaSegment(100, 0, 0, true, false, true, true, false, 1);
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 1, 5, 1);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_VP9}, settings),
        mediaSegment.clusterBytes);
    try {
      consume(testInputData);
      fail();
    } catch (ParserException exception) {
      assertEquals("Extension bit is set in signal byte", exception.getMessage());
    }
  }

  public void testReadSampleInvisible() throws IOException, InterruptedException {
    MediaSegment mediaSegment =
        createMediaSegment(100, 12, 13, false, true, true, false, false, 1);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_VP9}, null),
        mediaSegment.clusterBytes);
    consume(testInputData);
    assertVideoFormat();
    assertSample(mediaSegment, 25000, false, true, false, videoOutput);
  }

  public void testReadSampleCustomTimescale() throws IOException, InterruptedException {
    MediaSegment mediaSegment =
        createMediaSegment(100, 12, 13, false, false, true, false, false, 1);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, 1000, new int[] {ID_VP9}, null),
        mediaSegment.clusterBytes);
    consume(testInputData);
    assertVideoFormat();
    assertSample(mediaSegment, 25, false, false, false, videoOutput);
  }

  public void testReadSampleNegativeSimpleBlockTimecode() throws IOException, InterruptedException {
    MediaSegment mediaSegment =
        createMediaSegment(100, 13, -12, true, true, true, false, false, 1);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE,
            new int[] {ID_VP9}, null),
        mediaSegment.clusterBytes);
    consume(testInputData);
    assertVideoFormat();
    assertSample(mediaSegment, 1000, true, true, false, videoOutput);
  }

  private void consume(byte[] data) throws IOException, InterruptedException {
    ExtractorInput input = createTestInput(data);
    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult == Extractor.RESULT_CONTINUE) {
      readResult = extractor.read(input, null);
    }
    assertEquals(Extractor.RESULT_END_OF_INPUT, readResult);
  }

  private static ExtractorInput createTestInput(byte[] data) throws IOException {
    DataSource dataSource = new FakeDataSource.Builder().appendReadData(data).build();
    dataSource.open(new DataSpec(Uri.parse("http://www.google.com")));
    ExtractorInput input = new DefaultExtractorInput(dataSource, 0, C.LENGTH_UNBOUNDED);
    return input;
  }

  private void assertVideoFormat() {
    MediaFormat format = videoOutput.format;
    assertEquals(TEST_WIDTH, format.width);
    assertEquals(TEST_HEIGHT, format.height);
    assertEquals(MimeTypes.VIDEO_VP9, format.mimeType);
  }

  private void assertAudioFormat(int codecId) {
    MediaFormat format = audioOutput.format;
    assertEquals(TEST_CHANNEL_COUNT, format.channelCount);
    assertEquals(TEST_SAMPLE_RATE, format.sampleRate);
    if (codecId == ID_OPUS) {
      assertEquals(MimeTypes.AUDIO_OPUS, format.mimeType);
      assertEquals(3, format.initializationData.size());
      assertEquals(TEST_OPUS_CODEC_PRIVATE_SIZE, format.initializationData.get(0).length);
      assertEquals(TEST_CODEC_DELAY, ByteBuffer.wrap(format.initializationData.get(1)).getLong());
      assertEquals(TEST_SEEK_PRE_ROLL, ByteBuffer.wrap(format.initializationData.get(2)).getLong());
    } else if (codecId == ID_VORBIS) {
      assertEquals(MimeTypes.AUDIO_VORBIS, format.mimeType);
      assertEquals(2, format.initializationData.size());
      assertEquals(TEST_VORBIS_INFO_SIZE, format.initializationData.get(0).length);
      assertEquals(TEST_VORBIS_BOOKS_SIZE, format.initializationData.get(1).length);
    }
  }

  private void assertIndex(IndexPoint... indexPoints) {
    ChunkIndex index = (ChunkIndex) extractorOutput.seekMap;
    assertEquals(indexPoints.length, index.length);
    for (int i = 0; i < indexPoints.length; i++) {
      IndexPoint indexPoint = indexPoints[i];
      assertEquals(indexPoint.timeUs, index.timesUs[i]);
      assertEquals(indexPoint.size, index.sizes[i]);
      assertEquals(indexPoint.durationUs, index.durationsUs[i]);
    }
  }

  private void assertSample(MediaSegment mediaSegment, int timeUs, boolean keyframe,
      boolean invisible, boolean encrypted, TestTrackOutput output) {
    byte[] expectedOutput = mediaSegment.data;
    if (encrypted) {
      expectedOutput = joinByteArrays(new byte[] {(byte) TEST_INITIALIZATION_VECTOR.length},
          TEST_INITIALIZATION_VECTOR, expectedOutput);
    }
    assertTrue(Arrays.equals(expectedOutput, output.sampleData));
    assertEquals(timeUs, output.sampleTimeUs);
    assertEquals(keyframe, (output.sampleFlags & C.SAMPLE_FLAG_SYNC) != 0);
    assertEquals(invisible, (output.sampleFlags & C.SAMPLE_FLAG_DECODE_ONLY) != 0);
    assertEquals(encrypted, (output.sampleFlags & C.SAMPLE_FLAG_ENCRYPTED) != 0);
  }

  private byte[] createInitializationSegment(int cuePoints, int mediaSegmentSize,
      boolean docTypeIsWebm, int timecodeScale, int[] codecIds,
      ContentEncodingSettings contentEncodingSettings) {
    byte[] tracksElement = createTracksElement(codecIds, contentEncodingSettings);
    byte[] infoElement = createInfoElement(timecodeScale);
    byte[] cuesElement = createCuesElement(CUE_POINT_ELEMENT_BYTE_SIZE * cuePoints);
    int initalizationSegmentSize = infoElement.length + tracksElement.length
        + cuesElement.length + CUE_POINT_ELEMENT_BYTE_SIZE * cuePoints;
    byte[] segmentElement = createSegmentElement(initalizationSegmentSize + mediaSegmentSize);
    byte[] bytes = joinByteArrays(createEbmlElement(1, docTypeIsWebm, 2),
        segmentElement, infoElement, tracksElement, cuesElement);
    for (int i = 0; i < cuePoints; i++) {
      bytes = joinByteArrays(bytes, createCuePointElement(10 * i, initalizationSegmentSize));
    }
    return bytes;
  }

  private byte[] createTracksElement(int[] codecIds,
      ContentEncodingSettings contentEncodingSettings) {
    byte[] trackBytes = new byte[0];
    for (int codecId : codecIds) {
      switch (codecId) {
        case ID_VP9:
          trackBytes = joinByteArrays(trackBytes,
              createVideoTrackEntry(true, TEST_WIDTH, TEST_HEIGHT, contentEncodingSettings));
          break;
        case ID_OPUS:
          trackBytes = joinByteArrays(trackBytes, createOpusAudioTrackEntry(TEST_CHANNEL_COUNT));
          break;
        case ID_VORBIS:
          trackBytes = joinByteArrays(trackBytes, createVorbisAudioTrackEntry(TEST_CHANNEL_COUNT));
          break;
        case ID_DUMMY:
          trackBytes = joinByteArrays(trackBytes, createUnsupportedTrackEntry());
          break;
      }
    }
    byte[] tracksSize = getIntegerBytes(trackBytes.length);
    byte[] tracksHeader = createByteArray(
        0x16, 0x54, 0xAE, 0x6B, // Tracks
        0x01, 0x00, 0x00, 0x00, tracksSize[0], tracksSize[1], tracksSize[2], tracksSize[3]);
    return joinByteArrays(tracksHeader, trackBytes);
  }

  private static MediaSegment createMediaSegment(int dataLength, int clusterTimecode,
      int blockTimecode, boolean keyframe, boolean invisible, boolean simple,
      boolean encrypted, boolean validSignalByte, int trackNumber) {
    byte[] data = createFrameData(dataLength);
    byte[] blockBytes;
    if (simple) {
      blockBytes = createSimpleBlockElement(data.length, blockTimecode,
          keyframe, invisible, true, encrypted, validSignalByte, trackNumber);
    } else {
      blockBytes = createBlockElement(data.length, blockTimecode,
          keyframe, invisible, true, trackNumber);
    }
    byte[] clusterBytes =
        createClusterElement(blockBytes.length + data.length, clusterTimecode);
    return new MediaSegment(joinByteArrays(clusterBytes, blockBytes, data), data);
  }

  private static byte[] joinByteArrays(byte[]... byteArrays) {
    int length = 0;
    for (byte[] byteArray : byteArrays) {
      length += byteArray.length;
    }
    byte[] joined = new byte[length];
    length = 0;
    for (byte[] byteArray : byteArrays) {
      System.arraycopy(byteArray, 0, joined, length, byteArray.length);
      length += byteArray.length;
    }
    return joined;
  }

  private static byte[] createEbmlElement(
      int ebmlReadVersion, boolean docTypeIsWebm, int docTypeReadVersion) {
    return createByteArray(
        0x1A, 0x45, 0xDF, 0xA3, // EBML
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0F, // size=15
        0x42, 0xF7, // EBMLReadVersion
        0x81, ebmlReadVersion, // size=1
        0x42, 0x82, // DocType
        0x84, 0x77, 0x65, 0x62, docTypeIsWebm ? 0x6D : 0x42, // size=4 value=webm/B
        0x42, 0x85, // DocTypeReadVersion
        0x81, docTypeReadVersion); // size=1
  }

  private static byte[] createSegmentElement(int size) {
    byte[] sizeBytes = getIntegerBytes(size);
    return createByteArray(
        0x18, 0x53, 0x80, 0x67, // Segment
        0x01, 0x00, 0x00, 0x00, sizeBytes[0], sizeBytes[1], sizeBytes[2], sizeBytes[3]);
  }

  private static byte[] createInfoElement(int timecodeScale) {
    byte[] scaleBytes = getIntegerBytes(timecodeScale);
    return createByteArray(
        0x15, 0x49, 0xA9, 0x66, // Info
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x13, // size=19
        0x2A, 0xD7, 0xB1, // TimecodeScale
        0x84, scaleBytes[0], scaleBytes[1], scaleBytes[2], scaleBytes[3], // size=4
        0x44, 0x89, // Duration
        0x88, 0x40, 0xC3, 0x60, 0x00, 0x00, 0x00, 0x00, 0x00); // size=8 value=9920.0
  }

  private static byte[] createVideoTrackEntry(
      boolean codecIsVp9, int pixelWidth, int pixelHeight,
      ContentEncodingSettings contentEncodingSettings) {
    byte[] widthBytes = getIntegerBytes(pixelWidth);
    byte[] heightBytes = getIntegerBytes(pixelHeight);
    if (contentEncodingSettings != null) {
      byte[] orderBytes = getIntegerBytes(contentEncodingSettings.order);
      byte[] scopeBytes = getIntegerBytes(contentEncodingSettings.scope);
      byte[] typeBytes = getIntegerBytes(contentEncodingSettings.type);
      byte[] algorithmBytes = getIntegerBytes(contentEncodingSettings.algorithm);
      byte[] cipherModeBytes = getIntegerBytes(contentEncodingSettings.aesCipherMode);
      return createByteArray(
          0xAE, // TrackEntry
          0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x45, // size=69
          0x86, // CodecID
          0x85, 0x56, 0x5F, 0x56, 0x50, codecIsVp9 ? 0x39 : 0x30, // size=5 value=V_VP9/0
          0xD7, // TrackNumber
          0x81, 0x01, // size=1 value=1
          0x83, // TrackType
          0x81, 0x01, // size=1 value=1
          0x6D, 0x80, // ContentEncodings
          0xA4, // size=36
          0x62, 0x40, // ContentEncoding
          0xA1, // size=33
          0x50, 0x31, // ContentEncodingOrder
          0x81, orderBytes[3],
          0x50, 0x32, // ContentEncodingScope
          0x81, scopeBytes[3],
          0x50, 0x33, // ContentEncodingType
          0x81, typeBytes[3],
          0x50, 0x35, // ContentEncryption
          0x92, // size=18
          0x47, 0xE1, // ContentEncAlgo
          0x81, algorithmBytes[3],
          0x47, 0xE2, // ContentEncKeyID
          0x84, // size=4
          TEST_ENCRYPTION_KEY_ID[0], TEST_ENCRYPTION_KEY_ID[1],
          TEST_ENCRYPTION_KEY_ID[2], TEST_ENCRYPTION_KEY_ID[3], // value=binary
          0x47, 0xE7, // ContentEncAESSettings
          0x84, // size=4
          0x47, 0xE8, // AESSettingsCipherMode
          0x81, cipherModeBytes[3],
          0xE0, // Video
          0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x08, // size=8
          0xB0, // PixelWidth
          0x82, widthBytes[2], widthBytes[3], // size=2
          0xBA, // PixelHeight
          0x82, heightBytes[2], heightBytes[3]); // size=2
    } else {
      return createByteArray(
          0xAE, // TrackEntry
          0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1E, // size=30
          0x86, // CodecID
          0x85, 0x56, 0x5F, 0x56, 0x50, codecIsVp9 ? 0x39 : 0x30, // size=5 value=V_VP9/0
          0xD7, // TrackNumber
          0x81, 0x01, // size=1 value=1
          0x83, // TrackType
          0x81, 0x01, // size=1 value=1
          0xE0, // Video
          0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x08, // size=8
          0xB0, // PixelWidth
          0x82, widthBytes[2], widthBytes[3], // size=2
          0xBA, // PixelHeight
          0x82, heightBytes[2], heightBytes[3]); // size=2
    }
  }

  private static byte[] createOpusAudioTrackEntry(int channelCount) {
    byte[] channelCountBytes = getIntegerBytes(channelCount);
    return createByteArray(
        0xAE, // TrackEntry
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x36, // size=54
        0x86, // CodecID
        0x86, 0x41, 0x5F, 0x4F, 0x50, 0x55, 0x53, // size=6 value=A_OPUS
        0xD7, // TrackNumber
        0x81, 0x02, // size=1 value=2
        0x83, // TrackType
        0x81, 0x02, // size=1 value=2
        0x56, 0xAA, // CodecDelay
        0x83, 0x63, 0x2E, 0xA0, // size=3 value=6500000
        0x56, 0xBB, // SeekPreRoll
        0x84, 0x04, 0xC4, 0xB4, 0x00, // size=4 value=80000000
        0xE1, // Audio
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0D, // size=13
        0x9F, // Channels
        0x81, channelCountBytes[3], // size=1
        0xB5, // SamplingFrequency
        0x88, 0x40, 0xE7, 0x70, 0x00, 0x00, 0x00, 0x00, 0x00, // size=8 value=48000
        0x63, 0xA2, // CodecPrivate
        0x82, 0x00, 0x00); // size=2
  }

  private byte[] createVorbisAudioTrackEntry(int channelCount) {
    byte[] channelCountBytes = getIntegerBytes(channelCount);
    byte[] tracksElement = createByteArray(
        0xAE, // TrackEntry
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x99, // size=4249 (42+4207)
        0x86, // CodecID
        0x88, 0x41, 0x5f, 0x56, 0x4f, 0x52, 0x42, 0x49, 0x53, // size=8 value=A_VORBIS
        0xD7, // TrackNumber
        0x81, 0x02, // size=1 value=2
        0x83, // TrackType
        0x81, 0x02, // size=1 value=2
        0xE1, // Audio
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0D, // size=13
        0x9F, // Channels
        0x81, channelCountBytes[3], // size=1
        0xB5, // SamplingFrequency
        0x88, 0x40, 0xE7, 0x70, 0x00, 0x00, 0x00, 0x00, 0x00, // size=8 value=48000
        0x63, 0xA2, // CodecPrivate
        0x50, 0x6F); // size=4207
    byte[] codecPrivate = new byte[4207];
    try {
      getInstrumentation().getContext().getResources().getAssets().open(TEST_VORBIS_CODEC_PRIVATE)
          .read(codecPrivate);
    } catch (IOException e) {
      fail(); // should never happen
    }
    return joinByteArrays(tracksElement, codecPrivate);
  }

  private static byte[] createUnsupportedTrackEntry() {
    return createByteArray(
        0xAE, // TrackEntry
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x20, // size=32
        0x86, // CodecID
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x11, // size =17
        0x44, 0x5f, 0x57, 0x45, 0x42, 0x56, 0x54, 0x54,
        0x2f, 0x6d, 0x65, 0x74, 0x61, 0x64, 0x61, 0x74, 0x61, // value=D_WEBVTT/metadata
        0xD7, // TrackNumber
        0x81, 0x03, // size=1 value=3
        0x83, // TrackType
        0x81, 0x11); // size=1 value=11
  }

  private static byte[] createCuesElement(int size) {
    byte[] sizeBytes = getIntegerBytes(size);
    return createByteArray(
        0x1C, 0x53, 0xBB, 0x6B, // Cues
        0x01, 0x00, 0x00, 0x00, sizeBytes[0], sizeBytes[1], sizeBytes[2], sizeBytes[3]);
  }

  private static byte[] createCuePointElement(int cueTime, int cueClusterPosition) {
    byte[] positionBytes = getIntegerBytes(cueClusterPosition);
    return createByteArray(
        0xBB, // CuePoint
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x16, // size=22
        0xB3, // CueTime
        0x81, cueTime, // size=1
        0xB7, // CueTrackPositions
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0A, // size=10
        0xF1, // CueClusterPosition
        0x88, 0x00, 0x00, 0x00, 0x00, positionBytes[0], positionBytes[1],
        positionBytes[2], positionBytes[3]); // size=8
  }

  private static byte[] createClusterElement(int size, int timecode) {
    byte[] sizeBytes = getIntegerBytes(size);
    byte[] timeBytes = getIntegerBytes(timecode);
    return createByteArray(
        0x1F, 0x43, 0xB6, 0x75, // Cluster
        0x01, 0x00, 0x00, 0x00, sizeBytes[0], sizeBytes[1], sizeBytes[2], sizeBytes[3],
        0xE7, // Timecode
        0x84, timeBytes[0], timeBytes[1], timeBytes[2], timeBytes[3]); // size=4
  }

  private static byte[] createSimpleBlockElement(
      int size, int timecode, boolean keyframe, boolean invisible, boolean noLacing,
      boolean encrypted, boolean validSignalByte, int trackNumber) {
    byte[] sizeBytes = getIntegerBytes(size + 5 + (encrypted ? 9 : 0));
    byte[] timeBytes = getIntegerBytes(timecode);
    byte[] trackNumberBytes = getIntegerBytes(trackNumber);
    byte flags = (byte)
        ((keyframe ? 0x80 : 0x00) | (invisible ? 0x08 : 0x00) | (noLacing ? 0x00 : 0x06));
    byte[] simpleBlock = createByteArray(
        0xA3, // SimpleBlock
        0x01, 0x00, 0x00, 0x00, sizeBytes[0], sizeBytes[1], sizeBytes[2], sizeBytes[3],
        0x40, trackNumberBytes[3], // Track number size=2
        timeBytes[2], timeBytes[3], flags); // Timecode and flags
    if (encrypted) {
      simpleBlock = joinByteArrays(
          simpleBlock, createByteArray(validSignalByte ? 0x01 : 0x80),
          Arrays.copyOfRange(TEST_INITIALIZATION_VECTOR, 0, 8));
    }
    return simpleBlock;
  }

  private static byte[] createBlockElement(
      int size, int timecode, boolean keyframe, boolean invisible, boolean noLacing,
      int trackNumber) {
    int blockSize = size + 5;
    byte[] blockSizeBytes = getIntegerBytes(blockSize);
    byte[] timeBytes = getIntegerBytes(timecode);
    byte[] trackNumberBytes = getIntegerBytes(trackNumber);
    // Size of blockgroup = id + size + size of reference block + length of data.
    int blockGroupElementSize = 1 + 8 + (keyframe ? 0 : 3) +  blockSize;
    byte[] sizeBytes = getIntegerBytes(blockGroupElementSize);
    byte flags = (byte) ((invisible ? 0x08 : 0x00) | (noLacing ? 0x00 : 0x06));
    byte[] blockGroupHeader = createByteArray(
        0xA0, // BlockGroup
        0x01, 0x00, 0x00, 0x00, sizeBytes[0], sizeBytes[1], sizeBytes[2], sizeBytes[3]);
    byte[] referenceBlock = keyframe ? new byte[0] : createByteArray(
        0xFB, // ReferenceBlock
        0x81, 0x00); // size=1 value=0
    byte[] blockData = createByteArray(
        0xA1, // Block
        0x01, 0x00, 0x00, 0x00,
        blockSizeBytes[0], blockSizeBytes[1], blockSizeBytes[2], blockSizeBytes[3],
        0x40, trackNumberBytes[3], // Track number size=2
        timeBytes[2], timeBytes[3], flags); // Timecode and flags
    return joinByteArrays(blockGroupHeader, referenceBlock, blockData);
  }

  private static byte[] createFrameData(int size) {
    byte[] data = new byte[size];
    for (int i = 0; i < size; i++) {
      data[i] = (byte) i;
    }
    return data;
  }

  private static byte[] getIntegerBytes(int value) {
    return createByteArray(
        (value & 0xFF000000) >> 24,
        (value & 0x00FF0000) >> 16,
        (value & 0x0000FF00) >> 8,
        (value & 0x000000FF));
  }

  private static byte[] createByteArray(int... intArray) {
    byte[] byteArray = new byte[intArray.length];
    for (int i = 0; i < byteArray.length; i++) {
      byteArray[i] = (byte) intArray[i];
    }
    return byteArray;
  }

  /** Used by {@link #createMediaSegment} to return both cluster and video bytes together. */
  private static final class MediaSegment {

    private final byte[] clusterBytes;
    private final byte[] data;

    private MediaSegment(byte[] clusterBytes, byte[] data) {
      this.clusterBytes = clusterBytes;
      this.data = data;
    }

  }

  /** Used by {@link #assertIndex(IndexPoint...)} to validate index elements. */
  private static final class IndexPoint {

    private final long timeUs;
    private final int size;
    private final long durationUs;

    private IndexPoint(long timeUs, int size, long durationUs) {
      this.timeUs = timeUs;
      this.size = size;
      this.durationUs = durationUs;
    }

  }

  /** Used by {@link #createVideoTrackEntry} to create a Track header with Encryption. */
  private static final class ContentEncodingSettings {

    private final int order;
    private final int scope;
    private final int type;
    private final int algorithm;
    private final int aesCipherMode;

    private ContentEncodingSettings(int order, int scope, int type, int algorithm,
        int aesCipherMode) {
      this.order = order;
      this.scope = scope;
      this.type = type;
      this.algorithm = algorithm;
      this.aesCipherMode = aesCipherMode;
    }

  }

  /** Implements {@link ExtractorOutput} for test purposes. */
  public class TestExtractorOutput implements ExtractorOutput {

    public boolean tracksEnded;
    public SeekMap seekMap;
    public DrmInitData drmInitData;
    public int numberOfTracks;

    @Override
    public TrackOutput track(int trackId) {
      numberOfTracks++;
      // In the test samples, track number 1 is always video and track number 2 is always audio.
      return (trackId == 1) ? videoOutput : audioOutput;
    }

    @Override
    public void endTracks() {
      tracksEnded = true;
    }

    @Override
    public void seekMap(SeekMap seekMap) {
      this.seekMap = seekMap;
    }

    @Override
    public void drmInitData(DrmInitData drmInitData) {
      this.drmInitData = drmInitData;
    }

  }

  /** Implements {@link TrackOutput} for test purposes. */
  public static class TestTrackOutput implements TrackOutput {

    public MediaFormat format;
    private long sampleTimeUs;
    private int sampleFlags;
    private byte[] sampleData;

    @Override
    public void format(MediaFormat format) {
      this.format = format;
    }

    @Override
    public int sampleData(ExtractorInput input, int length) throws IOException,
        InterruptedException {
      byte[] newData = new byte[length];
      input.readFully(newData, 0, length);
      sampleData = sampleData == null ? newData : joinByteArrays(sampleData, newData);
      return length;
    }

    @Override
    public void sampleData(ParsableByteArray data, int length) {
      byte[] newData = new byte[length];
      data.readBytes(newData, 0, length);
      sampleData = sampleData == null ? newData : joinByteArrays(sampleData, newData);
    }

    @Override
    public void sampleMetadata(long timeUs, int flags, int size, int offset, byte[] encryptionKey) {
      this.sampleTimeUs = timeUs;
      this.sampleFlags = flags;
    }

  }

}
