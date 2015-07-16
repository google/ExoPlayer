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

import static com.google.android.exoplayer.extractor.webm.StreamBuilder.TEST_ENCRYPTION_KEY_ID;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.extractor.ChunkIndex;
import com.google.android.exoplayer.extractor.webm.StreamBuilder.ContentEncodingSettings;
import com.google.android.exoplayer.testutil.FakeExtractorOutput;
import com.google.android.exoplayer.testutil.FakeTrackOutput;
import com.google.android.exoplayer.testutil.TestUtil;
import com.google.android.exoplayer.util.MimeTypes;

import android.test.InstrumentationTestCase;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

/**
 * Tests for {@link WebmExtractor}.
 */
public final class WebmExtractorTest extends InstrumentationTestCase {

  private static final int DEFAULT_TIMECODE_SCALE = 1000000;
  private static final long TEST_DURATION_US = 9920000L;
  private static final int TEST_WIDTH = 1280;
  private static final int TEST_HEIGHT = 720;
  private static final int TEST_CHANNEL_COUNT = 1;
  private static final int TEST_SAMPLE_RATE = 48000;
  private static final int TEST_CODEC_DELAY = 6500000;
  private static final int TEST_SEEK_PRE_ROLL = 80000000;
  private static final String TEST_VORBIS_CODEC_PRIVATE = "webm/vorbis_codec_private";
  private static final int TEST_VORBIS_INFO_SIZE = 30;
  private static final int TEST_VORBIS_BOOKS_SIZE = 4140;
  private static final byte[] TEST_OPUS_CODEC_PRIVATE = new byte[] {0, 0};
  private static final int TEST_DEFAULT_DURATION_NS = 33 * 1000 * 1000;
  private static final byte[] TEST_H264_CODEC_PRIVATE = TestUtil.createByteArray(0x01, 0x4D,
      0x40, 0x1E, 0xFF, 0xE1, 0x00, 0x17, 0x67, 0x4D, 0x40, 0x1E, 0xE8, 0x80, 0x50, 0x17, 0xFC,
      0xB8, 0x08, 0x80, 0x00, 0x01, 0xF4, 0x80, 0x00, 0x75, 0x30, 0x07, 0x8B, 0x16, 0x89, 0x01,
      0x00, 0x04, 0x68, 0xEB, 0xEF, 0x20);

  private static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
  private static final UUID ZERO_UUID = new UUID(0, 0);
  private static final String WEBM_DOC_TYPE = "webm";
  private static final String MATROSKA_DOC_TYPE = "matroska";

  private WebmExtractor extractor;
  private FakeExtractorOutput extractorOutput;

  @Override
  public void setUp() {
    extractor = new WebmExtractor();
    extractorOutput = new FakeExtractorOutput();
    extractor.init(extractorOutput);
  }

  @Override
  public void tearDown() {
    extractor = null;
    extractorOutput = null;
  }

  public void testReadInitializationSegment() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testReadSegmentTwice() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .build(1);

    TestUtil.consumeTestData(extractor, data);
    extractor.seek();
    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareOpus() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addOpusTrack(TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertAudioFormat(MimeTypes.AUDIO_OPUS);
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareVorbis() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVorbisTrack(TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, getVorbisCodecPrivate())
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertAudioFormat(MimeTypes.AUDIO_VORBIS);
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareH264() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(MATROSKA_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addH264Track(TEST_WIDTH, TEST_HEIGHT, TEST_H264_CODEC_PRIVATE)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertH264VideoFormat();
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareTwoTracks() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .addOpusTrack(TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertEquals(2, extractorOutput.numberOfTracks);
    assertVp9VideoFormat();
    assertAudioFormat(MimeTypes.AUDIO_OPUS);
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareThreeTracks() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .addUnsupportedTrack()
        .addOpusTrack(TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    // Even though the input stream has 3 tracks, only 2 of them are supported and will be reported.
    assertEquals(2, extractorOutput.numberOfTracks);
    assertVp9VideoFormat();
    assertAudioFormat(MimeTypes.AUDIO_OPUS);
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareFourTracks() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .addVorbisTrack(TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, getVorbisCodecPrivate())
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .addOpusTrack(TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    // Even though the input stream has 4 supported tracks, only the first video and audio track
    // will be reported.
    assertEquals(2, extractorOutput.numberOfTracks);
    assertVp9VideoFormat();
    assertAudioFormat(MimeTypes.AUDIO_VORBIS);
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
  }

  public void testPrepareContentEncodingEncryption() throws IOException, InterruptedException {
    ContentEncodingSettings settings = new StreamBuilder.ContentEncodingSettings(0, 1, 5, 1);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, settings)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertIndex(new IndexPoint(0, 0, TEST_DURATION_US));
    DrmInitData drmInitData = extractorOutput.drmInitData;
    assertNotNull(drmInitData);
    android.test.MoreAsserts.assertEquals(TEST_ENCRYPTION_KEY_ID, drmInitData.get(WIDEVINE_UUID));
    android.test.MoreAsserts.assertEquals(TEST_ENCRYPTION_KEY_ID, drmInitData.get(ZERO_UUID));
  }

  public void testPrepareThreeCuePoints() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .build(3);

    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertIndex(
        new IndexPoint(0, 0, 10000),
        new IndexPoint(10000, 0, 10000),
        new IndexPoint(20000, 0, TEST_DURATION_US - 20000));
  }

  public void testPrepareCustomTimecodeScale() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(1000, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .build(3);

    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertIndex(
        new IndexPoint(0, 0, 10),
        new IndexPoint(10, 0, 10),
        new IndexPoint(20, 0, (TEST_DURATION_US / 1000) - 20));
  }

  public void testPrepareNoCuePoints() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .build(0);
    try {
      TestUtil.consumeTestData(extractor, data);
      fail();
    } catch (ParserException exception) {
      assertEquals("Invalid/missing cue points", exception.getMessage());
    }
  }

  public void testAcceptsWebmDocType() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .build(1);

    // No exception is thrown.
    TestUtil.consumeTestData(extractor, data);
  }

  public void testAcceptsMatroskaDocType() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(MATROSKA_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .build(1);

    // No exception is thrown.
    TestUtil.consumeTestData(extractor, data);
  }

  public void testPrepareInvalidDocType() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader("webB")
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .build(1);
    try {
      TestUtil.consumeTestData(extractor, data);
      fail();
    } catch (ParserException exception) {
      assertEquals("DocType webB not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidContentEncodingOrder() throws IOException, InterruptedException {
    ContentEncodingSettings settings = new ContentEncodingSettings(1, 1, 5, 1);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, settings)
        .build(1);
    try {
      TestUtil.consumeTestData(extractor, data);
      fail();
    } catch (ParserException exception) {
      assertEquals("ContentEncodingOrder 1 not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidContentEncodingScope() throws IOException, InterruptedException {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 0, 5, 1);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, settings)
        .build(1);
    try {
      TestUtil.consumeTestData(extractor, data);
      fail();
    } catch (ParserException exception) {
      assertEquals("ContentEncodingScope 0 not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidContentCompAlgo()
      throws IOException, InterruptedException {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 0, new byte[0]);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, settings)
        .build(1);
    try {
      TestUtil.consumeTestData(extractor, data);
      fail();
    } catch (ParserException exception) {
      assertEquals("ContentCompAlgo 0 not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidContentEncAlgo() throws IOException, InterruptedException {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 4, 1);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, settings)
        .build(1);
    try {
      TestUtil.consumeTestData(extractor, data);
      fail();
    } catch (ParserException exception) {
      assertEquals("ContentEncAlgo 4 not supported", exception.getMessage());
    }
  }

  public void testPrepareInvalidAESSettingsCipherMode() throws IOException, InterruptedException {
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 5, 0);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, settings)
        .build(1);
    try {
      TestUtil.consumeTestData(extractor, data);
      fail();
    } catch (ParserException exception) {
      assertEquals("AESSettingsCipherMode 0 not supported", exception.getMessage());
    }
  }

  public void testReadSampleKeyframe() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .addSimpleBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertSample(0, media, 0, true, false, null, getVideoOutput());
  }

  public void testReadSampleKeyframeStripped() throws IOException, InterruptedException {
    byte[] strippedBytes = new byte[] {-1, -1};
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 3, strippedBytes);
    byte[] sampleBytes = createFrameData(100);
    byte[] unstrippedSampleBytes = TestUtil.joinByteArrays(strippedBytes, sampleBytes);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, settings)
        .addSimpleBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, sampleBytes)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertSample(0, unstrippedSampleBytes, 0, true, false, null, getVideoOutput());
  }

  public void testReadSampleKeyframeManyBytesStripped() throws IOException, InterruptedException {
    byte[] strippedBytes = createFrameData(100);
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 3, strippedBytes);
    byte[] sampleBytes = createFrameData(5);
    byte[] unstrippedSampleBytes = TestUtil.joinByteArrays(strippedBytes, sampleBytes);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, settings)
        .addSimpleBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, sampleBytes)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertSample(0, unstrippedSampleBytes, 0, true, false, null, getVideoOutput());
  }

  public void testReadTwoTrackSamples() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .addOpusTrack(TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .addSimpleBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .addSimpleBlockMedia(2 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertEquals(2, extractorOutput.numberOfTracks);
    assertVp9VideoFormat();
    assertAudioFormat(MimeTypes.AUDIO_OPUS);
    assertSample(0, media, 0, true, false, null, getVideoOutput());
    assertSample(0, media, 0, true, false, null, getAudioOutput());
  }

  public void testReadTwoTrackSamplesWithSkippedTrack() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addUnsupportedTrack()
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .addOpusTrack(TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .addSimpleBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .addSimpleBlockMedia(2 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .addSimpleBlockMedia(17 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertEquals(2, extractorOutput.numberOfTracks);
    assertVp9VideoFormat();
    assertAudioFormat(MimeTypes.AUDIO_OPUS);
    assertSample(0, media, 0, true, false, null, getVideoOutput());
    assertSample(0, media, 0, true, false, null, getAudioOutput());
  }

  public void testReadBlock() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addOpusTrack(TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .addBlockMedia(2 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertAudioFormat(MimeTypes.AUDIO_OPUS);
    assertSample(0, media, 0, true, false, null, getAudioOutput());
  }

  public void testReadBlockNonKeyframe() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .addBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            false /* keyframe */, false /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertSample(0, media, 0, false, false, null, getVideoOutput());
  }

  public void testReadEncryptedFrame() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 5, 1);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, settings)
        .addSimpleBlockEncryptedMedia(1 /* trackNumber */, 0 /* clusterTimecode */,
            0 /* blockTimecode */, true /* keyframe */, false /* invisible */,
            true /* validSignalByte */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertSample(0, media, 0, true, false, TEST_ENCRYPTION_KEY_ID, getVideoOutput());
  }

  public void testReadEncryptedFrameWithInvalidSignalByte()
      throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 5, 1);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, settings)
        .addSimpleBlockEncryptedMedia(1 /* trackNumber */, 0 /* clusterTimecode */,
            0 /* blockTimecode */, true /* keyframe */, false /* invisible */,
            false /* validSignalByte */, media)
        .build(1);

    try {
      TestUtil.consumeTestData(extractor, data);
      fail();
    } catch (ParserException exception) {
      assertEquals("Extension bit is set in signal byte", exception.getMessage());
    }
  }

  public void testReadSampleInvisible() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .addSimpleBlockMedia(1 /* trackNumber */, 12 /* clusterTimecode */, 13 /* blockTimecode */,
            false /* keyframe */, true /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertSample(0, media, 25000, false, true, null, getVideoOutput());
  }

  public void testReadSampleCustomTimescale() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(1000, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .addSimpleBlockMedia(1 /* trackNumber */, 12 /* clusterTimecode */, 13 /* blockTimecode */,
            false /* keyframe */, false /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertSample(0, media, 25, false, false, null, getVideoOutput());
  }

  public void testReadSampleNegativeSimpleBlockTimecode() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addVp9Track(TEST_WIDTH, TEST_HEIGHT, null)
        .addSimpleBlockMedia(1 /* trackNumber */, 13 /* clusterTimecode */, -12 /* blockTimecode */,
            true /* keyframe */, true /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertVp9VideoFormat();
    assertSample(0, media, 1000, true, true, null, getVideoOutput());
  }

  public void testReadSampleWithFixedSizeLacing() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addOpusTrack(TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY, TEST_SEEK_PRE_ROLL,
            TEST_OPUS_CODEC_PRIVATE, TEST_DEFAULT_DURATION_NS)
        .addSimpleBlockMediaWithFixedSizeLacing(2 /* trackNumber */, 0 /* clusterTimecode */,
            0 /* blockTimecode */, 20, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertAudioFormat(MimeTypes.AUDIO_OPUS);
    for (int i = 0; i < 20; i++) {
      long expectedTimeUs = i * TEST_DEFAULT_DURATION_NS / 1000;
      assertSample(i, Arrays.copyOfRange(media, i * 5, i * 5 + 5), expectedTimeUs, true, false,
          null, getAudioOutput());
    }
  }

  public void testReadSampleWithXiphLacing() throws IOException, InterruptedException {
    byte[] media = createFrameData(300);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_US)
        .addOpusTrack(TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY, TEST_SEEK_PRE_ROLL,
            TEST_OPUS_CODEC_PRIVATE, TEST_DEFAULT_DURATION_NS)
        .addSimpleBlockMediaWithXiphLacing(2 /* trackNumber */, 0 /* clusterTimecode */,
            0 /* blockTimecode */, media, 256, 1, 243)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertAudioFormat(MimeTypes.AUDIO_OPUS);
    assertSample(0, Arrays.copyOfRange(media, 0, 256), 0 * TEST_DEFAULT_DURATION_NS / 1000, true,
        false, null, getAudioOutput());
    assertSample(1, Arrays.copyOfRange(media, 256, 257), 1 * TEST_DEFAULT_DURATION_NS / 1000, true,
        false, null, getAudioOutput());
    assertSample(2, Arrays.copyOfRange(media, 257, 300), 2 * TEST_DEFAULT_DURATION_NS / 1000, true,
        false, null, getAudioOutput());
  }

  private FakeTrackOutput getVideoOutput() {
    // In the sample data the video track has id 1.
    return extractorOutput.trackOutputs.get(1);
  }

  private FakeTrackOutput getAudioOutput() {
    // In the sample data the video track has id 2.
    return extractorOutput.trackOutputs.get(2);
  }

  private void assertVp9VideoFormat() {
    MediaFormat format = getVideoOutput().format;
    assertEquals(TEST_WIDTH, format.width);
    assertEquals(TEST_HEIGHT, format.height);
    assertEquals(MimeTypes.VIDEO_VP9, format.mimeType);
  }

  private void assertH264VideoFormat() {
    MediaFormat format = getVideoOutput().format;
    assertEquals(TEST_WIDTH, format.width);
    assertEquals(TEST_HEIGHT, format.height);
    assertEquals(MimeTypes.VIDEO_H264, format.mimeType);
  }

  private void assertAudioFormat(String expectedMimeType) {
    MediaFormat format = getAudioOutput().format;
    assertEquals(TEST_CHANNEL_COUNT, format.channelCount);
    assertEquals(TEST_SAMPLE_RATE, format.sampleRate);
    assertEquals(expectedMimeType, format.mimeType);
    if (MimeTypes.AUDIO_OPUS.equals(expectedMimeType)) {
      assertEquals(3, format.initializationData.size());
      android.test.MoreAsserts.assertEquals(TEST_OPUS_CODEC_PRIVATE,
          format.initializationData.get(0));
      assertEquals(TEST_CODEC_DELAY, ByteBuffer.wrap(format.initializationData.get(1)).getLong());
      assertEquals(TEST_SEEK_PRE_ROLL, ByteBuffer.wrap(format.initializationData.get(2)).getLong());
    } else if (MimeTypes.AUDIO_VORBIS.equals(expectedMimeType)) {
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

  private void assertSample(int index, byte[] expectedMedia, long timeUs, boolean keyframe,
      boolean invisible, byte[] encryptionKey, FakeTrackOutput output) {
    if (encryptionKey != null) {
      expectedMedia = TestUtil.joinByteArrays(
          new byte[] {(byte) StreamBuilder.TEST_INITIALIZATION_VECTOR.length},
          StreamBuilder.TEST_INITIALIZATION_VECTOR, expectedMedia);
    }
    int flags = 0;
    flags |= keyframe ? C.SAMPLE_FLAG_SYNC : 0;
    flags |= invisible ? C.SAMPLE_FLAG_DECODE_ONLY : 0;
    flags |= encryptionKey != null ? C.SAMPLE_FLAG_ENCRYPTED : 0;
    output.assertSample(index, expectedMedia, timeUs, flags, encryptionKey);
  }

  private byte[] getVorbisCodecPrivate() {
    byte[] codecPrivate = new byte[4207];
    try {
      getInstrumentation().getContext().getResources().getAssets().open(TEST_VORBIS_CODEC_PRIVATE)
          .read(codecPrivate);
    } catch (IOException e) {
      fail(); // should never happen
    }
    return codecPrivate;
  }

  private static byte[] createFrameData(int size) {
    byte[] data = new byte[size];
    for (int i = 0; i < size; i++) {
      data[i] = (byte) i;
    }
    return data;
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

}
