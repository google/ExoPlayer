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
package com.google.android.exoplayer.chunk.parser.webm;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.chunk.parser.SegmentIndex;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.upstream.ByteArrayNonBlockingInputStream;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.MimeTypes;

import android.test.InstrumentationTestCase;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

public class WebmExtractorTest extends InstrumentationTestCase {

  private static final int INFO_ELEMENT_BYTE_SIZE = 31;
  private static final int TRACKS_ELEMENT_BYTE_SIZE = 48;
  private static final int CUES_ELEMENT_BYTE_SIZE = 12;
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
  // First 8 bytes of IV come from the container, last 8 bytes are always initialized to 0.
  private static final byte[] TEST_INITIALIZATION_VECTOR = {
      0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };

  private static final int ID_VP9 = 0;
  private static final int ID_OPUS = 1;
  private static final int ID_VORBIS = 2;

  private static final int EXPECTED_INIT_RESULT = WebmExtractor.RESULT_READ_INIT
      | WebmExtractor.RESULT_READ_INDEX | WebmExtractor.RESULT_END_OF_STREAM;
  private static final int EXPECTED_INIT_AND_SAMPLE_RESULT = WebmExtractor.RESULT_READ_INIT
      | WebmExtractor.RESULT_READ_INDEX | WebmExtractor.RESULT_READ_SAMPLE;

  private final WebmExtractor extractor = new WebmExtractor();
  private final SampleHolder sampleHolder =
      new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DISABLED);

  @Override
  public void setUp() {
    sampleHolder.data = ByteBuffer.allocate(1024);
  }

  public void testPrepare() throws ParserException {
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE, ID_VP9, null));
    assertEquals(EXPECTED_INIT_RESULT, extractor.read(testInputStream, sampleHolder));
    assertFormat();
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareOpus() throws ParserException {
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE, ID_OPUS, null));
    assertEquals(EXPECTED_INIT_RESULT, extractor.read(testInputStream, sampleHolder));
    assertAudioFormat(ID_OPUS);
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareVorbis() throws ParserException {
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE, ID_VORBIS, null));
    assertEquals(EXPECTED_INIT_RESULT, extractor.read(testInputStream, sampleHolder));
    assertAudioFormat(ID_VORBIS);
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareContentEncodingEncryption() throws ParserException {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 1, 5, 1);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE, ID_VP9, settings));
    assertEquals(EXPECTED_INIT_RESULT, extractor.read(testInputStream, sampleHolder));
    assertFormat();
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
    DrmInitData drmInitData = extractor.getDrmInitData();
    assertNotNull(drmInitData);
    android.test.MoreAsserts.assertEquals(TEST_ENCRYPTION_KEY_ID, drmInitData.get(WIDEVINE_UUID));
    android.test.MoreAsserts.assertEquals(TEST_ENCRYPTION_KEY_ID, drmInitData.get(ZERO_UUID));
  }

  public void testPrepareThreeCuePoints() throws ParserException {
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(3, 0, true, DEFAULT_TIMECODE_SCALE, ID_VP9, null));
    assertEquals(EXPECTED_INIT_RESULT, extractor.read(testInputStream, sampleHolder));
    assertFormat();
    assertIndex(
        new IndexPoint(0, 0, 10000),
        new IndexPoint(10000, 0, 10000),
        new IndexPoint(20000, 0, TEST_DURATION_US - 20000));
  }

  public void testPrepareCustomTimecodeScale() throws ParserException {
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(3, 0, true, 1000, ID_VP9, null));
    assertEquals(EXPECTED_INIT_RESULT, extractor.read(testInputStream, sampleHolder));
    assertFormat();
    assertIndex(
        new IndexPoint(0, 0, 10),
        new IndexPoint(10, 0, 10),
        new IndexPoint(20, 0, (TEST_DURATION_US / 1000) - 20));
  }

  public void testPrepareNoCuePoints() {
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(0, 0, true, DEFAULT_TIMECODE_SCALE, ID_VP9, null));
    try {
      extractor.read(testInputStream, sampleHolder);
      fail();
    } catch (ParserException exception) {
      assertEquals("Invalid/missing cue points", exception.getMessage());
    }
  }

  public void testPrepareInvalidDocType() {
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(1, 0, false, DEFAULT_TIMECODE_SCALE, ID_VP9, null));
    try {
      extractor.read(testInputStream, sampleHolder);
      fail();
    } catch (ParserException exception) {
      assertEquals("DocType webB not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidContentEncodingOrder() {
    ContentEncodingSettings settings = new ContentEncodingSettings(1, 1, 1, 5, 1);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE, ID_VP9, settings));
    try {
      extractor.read(testInputStream, sampleHolder);
      fail();
    } catch (ParserException exception) {
      assertEquals("ContentEncodingOrder 1 not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidContentEncodingScope() {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 0, 1, 5, 1);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE, ID_VP9, settings));
    try {
      extractor.read(testInputStream, sampleHolder);
      fail();
    } catch (ParserException exception) {
      assertEquals("ContentEncodingScope 0 not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidContentEncodingType() {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 0, 5, 1);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE, ID_VP9, settings));
    try {
      extractor.read(testInputStream, sampleHolder);
      fail();
    } catch (ParserException exception) {
      assertEquals("ContentEncodingType 0 not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidContentEncAlgo() {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 1, 4, 1);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE, ID_VP9, settings));
    try {
      extractor.read(testInputStream, sampleHolder);
      fail();
    } catch (ParserException exception) {
      assertEquals("ContentEncAlgo 4 not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidAESSettingsCipherMode() {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 1, 5, 0);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(
        createInitializationSegment(1, 0, true, DEFAULT_TIMECODE_SCALE, ID_VP9, settings));
    try {
      extractor.read(testInputStream, sampleHolder);
      fail();
    } catch (ParserException exception) {
      assertEquals("AESSettingsCipherMode 0 not supported", exception.getMessage());
    }
  }

  public void testReadSampleKeyframe() throws ParserException {
    MediaSegment mediaSegment = createMediaSegment(100, 0, 0, true, false, true, false, false);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE, ID_VP9, null),
        mediaSegment.clusterBytes);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(testInputData);
    assertEquals(EXPECTED_INIT_AND_SAMPLE_RESULT, extractor.read(testInputStream, sampleHolder));
    assertFormat();
    assertSample(mediaSegment, 0, true, false, false);
    assertEquals(WebmExtractor.RESULT_END_OF_STREAM, extractor.read(testInputStream, sampleHolder));
  }

  public void testReadBlock() throws ParserException {
    MediaSegment mediaSegment = createMediaSegment(100, 0, 0, true, false, false, false, false);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE, ID_OPUS, null),
        mediaSegment.clusterBytes);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(testInputData);
    assertEquals(EXPECTED_INIT_AND_SAMPLE_RESULT, extractor.read(testInputStream, sampleHolder));
    assertAudioFormat(ID_OPUS);
    assertSample(mediaSegment, 0, true, false, false);
    assertEquals(WebmExtractor.RESULT_END_OF_STREAM, extractor.read(testInputStream, sampleHolder));
  }

  public void testReadEncryptedFrame() throws ParserException {
    MediaSegment mediaSegment = createMediaSegment(100, 0, 0, true, false, true, true, true);
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 1, 5, 1);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE, ID_VP9, settings),
        mediaSegment.clusterBytes);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(testInputData);
    assertEquals(EXPECTED_INIT_AND_SAMPLE_RESULT, extractor.read(testInputStream, sampleHolder));
    assertFormat();
    assertSample(mediaSegment, 0, true, false, true);
    assertEquals(WebmExtractor.RESULT_END_OF_STREAM, extractor.read(testInputStream, sampleHolder));
  }

  public void testReadEncryptedFrameWithInvalidSignalByte() {
    MediaSegment mediaSegment = createMediaSegment(100, 0, 0, true, false, true, true, false);
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 1, 5, 1);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE, ID_VP9, settings),
        mediaSegment.clusterBytes);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(testInputData);
    try {
      extractor.read(testInputStream, sampleHolder);
      fail();
    } catch (ParserException exception) {
      assertEquals("Extension bit is set in signal byte", exception.getMessage());
    }
  }

  public void testReadSampleInvisible() throws ParserException {
    MediaSegment mediaSegment = createMediaSegment(100, 12, 13, false, true, true, false, false);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE, ID_VP9, null),
        mediaSegment.clusterBytes);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(testInputData);
    assertEquals(EXPECTED_INIT_AND_SAMPLE_RESULT, extractor.read(testInputStream, sampleHolder));
    assertFormat();
    assertSample(mediaSegment, 25000, false, true, false);
    assertEquals(WebmExtractor.RESULT_END_OF_STREAM, extractor.read(testInputStream, sampleHolder));
  }

  public void testReadSampleCustomTimescale() throws ParserException {
    MediaSegment mediaSegment = createMediaSegment(100, 12, 13, false, false, true, false, false);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, 1000, ID_VP9, null),
        mediaSegment.clusterBytes);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(testInputData);
    assertEquals(EXPECTED_INIT_AND_SAMPLE_RESULT, extractor.read(testInputStream, sampleHolder));
    assertFormat();
    assertSample(mediaSegment, 25, false, false, false);
    assertEquals(WebmExtractor.RESULT_END_OF_STREAM, extractor.read(testInputStream, sampleHolder));
  }

  public void testReadSampleNegativeSimpleBlockTimecode() throws ParserException {
    MediaSegment mediaSegment = createMediaSegment(100, 13, -12, true, true, true, false, false);
    byte[] testInputData = joinByteArrays(
        createInitializationSegment(
            1, mediaSegment.clusterBytes.length, true, DEFAULT_TIMECODE_SCALE, ID_VP9, null),
        mediaSegment.clusterBytes);
    NonBlockingInputStream testInputStream = new ByteArrayNonBlockingInputStream(testInputData);
    assertEquals(EXPECTED_INIT_AND_SAMPLE_RESULT, extractor.read(testInputStream, sampleHolder));
    assertFormat();
    assertSample(mediaSegment, 1000, true, true, false);
    assertEquals(WebmExtractor.RESULT_END_OF_STREAM, extractor.read(testInputStream, sampleHolder));
  }

  private void assertFormat() {
    MediaFormat format = extractor.getFormat();
    assertEquals(TEST_WIDTH, format.width);
    assertEquals(TEST_HEIGHT, format.height);
    assertEquals(MimeTypes.VIDEO_VP9, format.mimeType);
  }

  private void assertAudioFormat(int codecId) {
    MediaFormat format = extractor.getFormat();
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
    SegmentIndex index = extractor.getIndex();
    assertEquals(CUES_ELEMENT_BYTE_SIZE + CUE_POINT_ELEMENT_BYTE_SIZE * indexPoints.length,
        index.sizeBytes);
    assertEquals(indexPoints.length, index.length);
    for (int i = 0; i < indexPoints.length; i++) {
      IndexPoint indexPoint = indexPoints[i];
      assertEquals(indexPoint.timeUs, index.timesUs[i]);
      assertEquals(indexPoint.size, index.sizes[i]);
      assertEquals(indexPoint.durationUs, index.durationsUs[i]);
    }
  }

  private void assertSample(
      MediaSegment mediaSegment, int timeUs, boolean keyframe, boolean invisible,
      boolean encrypted) {
    assertTrue(Arrays.equals(
        mediaSegment.videoBytes, Arrays.copyOf(sampleHolder.data.array(), sampleHolder.size)));
    assertEquals(timeUs, sampleHolder.timeUs);
    assertEquals(keyframe, (sampleHolder.flags & C.SAMPLE_FLAG_SYNC) != 0);
    assertEquals(invisible, sampleHolder.decodeOnly);
    assertEquals(encrypted, (sampleHolder.flags & C.SAMPLE_FLAG_ENCRYPTED) != 0);
    if (encrypted) {
      android.test.MoreAsserts.assertEquals(TEST_INITIALIZATION_VECTOR, sampleHolder.cryptoInfo.iv);
      assertEquals(C.CRYPTO_MODE_AES_CTR, sampleHolder.cryptoInfo.mode);
      assertEquals(1, sampleHolder.cryptoInfo.numSubSamples);
      assertEquals(100, sampleHolder.cryptoInfo.numBytesOfEncryptedData[0]);
      assertEquals(0, sampleHolder.cryptoInfo.numBytesOfClearData[0]);
    }
  }

  private byte[] createInitializationSegment(
      int cuePoints, int mediaSegmentSize, boolean docTypeIsWebm, int timecodeScale,
      int codecId, ContentEncodingSettings contentEncodingSettings) {
    int initalizationSegmentSize = INFO_ELEMENT_BYTE_SIZE + TRACKS_ELEMENT_BYTE_SIZE
        + CUES_ELEMENT_BYTE_SIZE + CUE_POINT_ELEMENT_BYTE_SIZE * cuePoints;
    byte[] tracksElement = null;
    switch (codecId) {
      case ID_VP9:
        tracksElement = createTracksElementWithVideo(
            true, TEST_WIDTH, TEST_HEIGHT, contentEncodingSettings);
        break;
      case ID_OPUS:
        tracksElement = createTracksElementWithOpusAudio(TEST_CHANNEL_COUNT);
        break;
      case ID_VORBIS:
        tracksElement = createTracksElementWithVorbisAudio(TEST_CHANNEL_COUNT);
        break;
    }
    byte[] bytes = joinByteArrays(createEbmlElement(1, docTypeIsWebm, 2),
            createSegmentElement(initalizationSegmentSize + mediaSegmentSize),
            createInfoElement(timecodeScale),
            tracksElement,
            createCuesElement(CUE_POINT_ELEMENT_BYTE_SIZE * cuePoints));
    for (int i = 0; i < cuePoints; i++) {
      bytes = joinByteArrays(bytes, createCuePointElement(10 * i, initalizationSegmentSize));
    }
    return bytes;
  }

  private static MediaSegment createMediaSegment(int videoBytesLength, int clusterTimecode,
      int blockTimecode, boolean keyframe, boolean invisible, boolean simple,
      boolean encrypted, boolean validSignalByte) {
    byte[] videoBytes = createVideoBytes(videoBytesLength);
    byte[] blockBytes;
    if (simple) {
      blockBytes = createSimpleBlockElement(videoBytes.length, blockTimecode,
          keyframe, invisible, true, encrypted, validSignalByte);
    } else {
      blockBytes = createBlockElement(videoBytes.length, blockTimecode, invisible, true);
    }
    byte[] clusterBytes =
        createClusterElement(blockBytes.length + videoBytes.length, clusterTimecode);
    return new MediaSegment(joinByteArrays(clusterBytes, blockBytes, videoBytes), videoBytes);
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

  private static byte[] createTracksElementWithVideo(
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
          0x16, 0x54, 0xAE, 0x6B, // Tracks
          0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x48, // size=72
          0xAE, // TrackEntry
          0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x3F, // size=63
          0x86, // CodecID
          0x85, 0x56, 0x5F, 0x56, 0x50, codecIsVp9 ? 0x39 : 0x30, // size=5 value=V_VP9/0
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
          0x16, 0x54, 0xAE, 0x6B, // Tracks
          0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x24, // size=36
          0xAE, // TrackEntry
          0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1B, // size=27
          0x86, // CodecID
          0x85, 0x56, 0x5F, 0x56, 0x50, codecIsVp9 ? 0x39 : 0x30, // size=5 value=V_VP9/0
          0xE0, // Video
          0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x08, // size=8
          0xB0, // PixelWidth
          0x82, widthBytes[2], widthBytes[3], // size=2
          0xBA, // PixelHeight
          0x82, heightBytes[2], heightBytes[3]); // size=2
    }
  }

  private static byte[] createTracksElementWithOpusAudio(int channelCount) {
    byte[] channelCountBytes = getIntegerBytes(channelCount);
    return createByteArray(
        0x16, 0x54, 0xAE, 0x6B, // Tracks
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x39, // size=57
        0xAE, // TrackEntry
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, // size=48
        0x86, // CodecID
        0x86, 0x41, 0x5F, 0x4F, 0x50, 0x55, 0x53, // size=6 value=A_OPUS
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

  private byte[] createTracksElementWithVorbisAudio(int channelCount) {
    byte[] channelCountBytes = getIntegerBytes(channelCount);
    byte[] tracksElement = createByteArray(
        0x16, 0x54, 0xAE, 0x6B, // Tracks
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x9C, // size=4252
        0xAE, // TrackEntry
        0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10, 0x93, // size=4243 (36+4207)
        0x86, // CodecID
        0x88, 0x41, 0x5f, 0x56, 0x4f, 0x52, 0x42, 0x49, 0x53, // size=8 value=A_VORBIS
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

  private static byte[] createCuesElement(int size) {
    byte[] sizeBytes = getIntegerBytes(size);
    return createByteArray(
        0x1C, 0x53, 0xBB, 0x6B, // Cues
        0x01, 0x00, 0x00, 0x00, sizeBytes[0], sizeBytes[1], sizeBytes[2], sizeBytes[3]); // size=31
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
      boolean encrypted, boolean validSignalByte) {
    byte[] sizeBytes = getIntegerBytes(size + 4 + (encrypted ? 9 : 0));
    byte[] timeBytes = getIntegerBytes(timecode);
    byte flags = (byte)
        ((keyframe ? 0x80 : 0x00) | (invisible ? 0x08 : 0x00) | (noLacing ? 0x00 : 0x06));
    byte[] simpleBlock = createByteArray(
        0xA3, // SimpleBlock
        0x01, 0x00, 0x00, 0x00, sizeBytes[0], sizeBytes[1], sizeBytes[2], sizeBytes[3],
        0x81, // Track number value=1
        timeBytes[2], timeBytes[3], flags); // Timecode and flags
    if (encrypted) {
      simpleBlock = joinByteArrays(
          simpleBlock, createByteArray(validSignalByte ? 0x01 : 0x80),
          Arrays.copyOfRange(TEST_INITIALIZATION_VECTOR, 0, 8));
    }
    return simpleBlock;
  }

  private static byte[] createBlockElement(
      int size, int timecode, boolean invisible, boolean noLacing) {
    int blockSize = size + 4;
    byte[] blockSizeBytes = getIntegerBytes(blockSize);
    byte[] timeBytes = getIntegerBytes(timecode);
    int blockElementSize = 1 + 8 + blockSize; // id + size + length of data
    byte[] sizeBytes = getIntegerBytes(blockElementSize);
    byte flags = (byte) ((invisible ? 0x08 : 0x00) | (noLacing ? 0x00 : 0x06));
    return createByteArray(
        0xA0, // BlockGroup
        0x01, 0x00, 0x00, 0x00, sizeBytes[0], sizeBytes[1], sizeBytes[2], sizeBytes[3],
        0xA1, // Block
        0x01, 0x00, 0x00, 0x00,
        blockSizeBytes[0], blockSizeBytes[1], blockSizeBytes[2], blockSizeBytes[3],
        0x81, // Track number value=1
        timeBytes[2], timeBytes[3], flags); // Timecode and flags
  }

  private static byte[] createVideoBytes(int size) {
    byte[] videoBytes = new byte[size];
    for (int i = 0; i < size; i++) {
      videoBytes[i] = (byte) i;
    }
    return videoBytes;
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
    private final byte[] videoBytes;

    private MediaSegment(byte[] clusterBytes, byte[] videoBytes) {
      this.clusterBytes = clusterBytes;
      this.videoBytes = videoBytes;
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

  /** Used by {@link #createTracksElementWithVideo} to create a Track header with Encryption. */
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

}
