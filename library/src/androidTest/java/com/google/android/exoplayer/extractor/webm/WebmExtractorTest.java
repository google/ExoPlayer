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

import android.test.InstrumentationTestCase;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.drm.DrmInitData;
import com.google.android.exoplayer.drm.DrmInitData.SchemeInitData;
import com.google.android.exoplayer.extractor.ChunkIndex;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.extractor.webm.StreamBuilder.ContentEncodingSettings;
import com.google.android.exoplayer.testutil.FakeExtractorOutput;
import com.google.android.exoplayer.testutil.FakeTrackOutput;
import com.google.android.exoplayer.testutil.TestUtil;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;

/**
 * Tests for {@link WebmExtractor}.
 */
public final class WebmExtractorTest extends InstrumentationTestCase {

  private static final int DEFAULT_TIMECODE_SCALE = 1000000;
  private static final long TEST_DURATION_TIMECODE = 9920L;
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
  private static final byte VIDEO_TRACK_NUMBER = 0x01;
  private static final byte AUDIO_TRACK_NUMBER = 0x02;
  private static final byte UNSUPPORTED_TRACK_NUMBER = 0x03;
  private static final byte SECOND_VIDEO_TRACK_NUMBER = 0x04;
  private static final byte SECOND_AUDIO_TRACK_NUMBER = 0x05;

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
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertIndex(DEFAULT_TIMECODE_SCALE, 1);
  }

  public void testReadSegmentTwice() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .build(1);

    TestUtil.consumeTestData(extractor, data);
    extractor.seek();
    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertIndex(DEFAULT_TIMECODE_SCALE, 1);
  }

  public void testPrepareOpus() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addOpusTrack(AUDIO_TRACK_NUMBER, TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertAudioFormat(AUDIO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE, MimeTypes.AUDIO_OPUS);
    assertIndex(DEFAULT_TIMECODE_SCALE, 1);
  }

  public void testPrepareVorbis() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVorbisTrack(AUDIO_TRACK_NUMBER, TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE,
            getVorbisCodecPrivate())
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertAudioFormat(AUDIO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE, MimeTypes.AUDIO_VORBIS);
    assertIndex(DEFAULT_TIMECODE_SCALE, 1);
  }

  public void testPrepareH264() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(MATROSKA_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addH264Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, TEST_H264_CODEC_PRIVATE)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertH264VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertIndex(DEFAULT_TIMECODE_SCALE, 1);
  }

  public void testPrepareTwoTracks() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .addOpusTrack(AUDIO_TRACK_NUMBER, TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertEquals(2, extractorOutput.numberOfTracks);
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertAudioFormat(AUDIO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE, MimeTypes.AUDIO_OPUS);
    assertIndex(DEFAULT_TIMECODE_SCALE, 1);
  }

  public void testPrepareThreeTracks() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .addUnsupportedTrack(UNSUPPORTED_TRACK_NUMBER)
        .addOpusTrack(AUDIO_TRACK_NUMBER, TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    // Even though the input stream has 3 tracks, only 2 of them are supported and will be reported.
    assertEquals(2, extractorOutput.numberOfTracks);
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertAudioFormat(AUDIO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE, MimeTypes.AUDIO_OPUS);
    assertIndex(DEFAULT_TIMECODE_SCALE, 1);
  }

  public void testPrepareFourTracks() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .addVorbisTrack(AUDIO_TRACK_NUMBER, TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE,
            getVorbisCodecPrivate())
        .addVp9Track(SECOND_VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .addOpusTrack(SECOND_AUDIO_TRACK_NUMBER, TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE,
            TEST_CODEC_DELAY, TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertEquals(4, extractorOutput.numberOfTracks);
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertAudioFormat(AUDIO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE, MimeTypes.AUDIO_VORBIS);
    assertVp9VideoFormat(SECOND_VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertAudioFormat(SECOND_AUDIO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE, MimeTypes.AUDIO_OPUS);
    assertIndex(DEFAULT_TIMECODE_SCALE, 1);
  }

  public void testPrepareContentEncodingEncryption() throws IOException, InterruptedException {
    ContentEncodingSettings settings = new StreamBuilder.ContentEncodingSettings(0, 1, 5, 1);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, settings)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertIndex(DEFAULT_TIMECODE_SCALE, 1);
    DrmInitData drmInitData = extractorOutput.drmInitData;
    assertNotNull(drmInitData);
    SchemeInitData widevineInitData = drmInitData.get(WIDEVINE_UUID);
    assertEquals(MimeTypes.VIDEO_WEBM, widevineInitData.mimeType);
    android.test.MoreAsserts.assertEquals(TEST_ENCRYPTION_KEY_ID, widevineInitData.data);
    SchemeInitData zeroInitData = drmInitData.get(ZERO_UUID);
    assertEquals(MimeTypes.VIDEO_WEBM, zeroInitData.mimeType);
    android.test.MoreAsserts.assertEquals(TEST_ENCRYPTION_KEY_ID, zeroInitData.data);
  }

  public void testPrepareThreeCuePoints() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .build(3);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertIndex(DEFAULT_TIMECODE_SCALE, 3);
  }

  public void testPrepareCustomTimecodeScaleBeforeDuration()
      throws IOException, InterruptedException {
    testPrepareTimecodeScale(1000, false, false);
  }

  public void testPrepareCustomTimecodeScaleAfterDuration()
      throws IOException, InterruptedException {
    testPrepareTimecodeScale(1000, false, true);
  }

  public void testPrepareImplicitDefaultTimecode()
      throws IOException, InterruptedException {
    testPrepareTimecodeScale(1000, false, true);
  }

  private void testPrepareTimecodeScale(int timecodeScale, boolean omitTimecodeScaleIfDefault,
      boolean afterDuration) throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(timecodeScale, TEST_DURATION_TIMECODE, omitTimecodeScaleIfDefault, afterDuration)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .build(3);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, timecodeScale);
    assertIndex(timecodeScale, 3);
  }

  public void testPrepareNoCuesElement() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .addSimpleBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .build(0);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertIndexUnseekable();
  }

  public void testAcceptsWebmDocType() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
  }

  public void testAcceptsMatroskaDocType() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader(MATROSKA_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
  }

  public void testPrepareInvalidDocType() throws IOException, InterruptedException {
    byte[] data = new StreamBuilder()
        .setHeader("webB")
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
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
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, settings)
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
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, settings)
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
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, settings)
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
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, settings)
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
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, settings)
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
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .addSimpleBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertSample(0, media, 0, true, false, null, getTrackOutput(VIDEO_TRACK_NUMBER));
  }

  public void testReadSampleKeyframeStripped() throws IOException, InterruptedException {
    byte[] strippedBytes = new byte[] {-1, -1};
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 3, strippedBytes);
    byte[] sampleBytes = createFrameData(100);
    byte[] unstrippedSampleBytes = TestUtil.joinByteArrays(strippedBytes, sampleBytes);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, settings)
        .addSimpleBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, sampleBytes)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertSample(0, unstrippedSampleBytes, 0, true, false, null,
        getTrackOutput(VIDEO_TRACK_NUMBER));
  }

  public void testReadSampleKeyframeManyBytesStripped() throws IOException, InterruptedException {
    byte[] strippedBytes = createFrameData(100);
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 3, strippedBytes);
    byte[] sampleBytes = createFrameData(5);
    byte[] unstrippedSampleBytes = TestUtil.joinByteArrays(strippedBytes, sampleBytes);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, settings)
        .addSimpleBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, sampleBytes)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertSample(0, unstrippedSampleBytes, 0, true, false, null,
        getTrackOutput(VIDEO_TRACK_NUMBER));
  }

  public void testReadTwoTrackSamples() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .addOpusTrack(AUDIO_TRACK_NUMBER, TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .addSimpleBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .addSimpleBlockMedia(2 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertEquals(2, extractorOutput.numberOfTracks);
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertAudioFormat(AUDIO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE, MimeTypes.AUDIO_OPUS);
    assertSample(0, media, 0, true, false, null, getTrackOutput(VIDEO_TRACK_NUMBER));
    assertSample(0, media, 0, true, false, null, getTrackOutput(AUDIO_TRACK_NUMBER));
  }

  public void testReadTwoTrackSamplesWithSkippedTrack() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addUnsupportedTrack(UNSUPPORTED_TRACK_NUMBER)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .addOpusTrack(AUDIO_TRACK_NUMBER, TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .addSimpleBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .addSimpleBlockMedia(2 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .addSimpleBlockMedia(17 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertEquals(2, extractorOutput.numberOfTracks);
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertAudioFormat(AUDIO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE, MimeTypes.AUDIO_OPUS);
    assertSample(0, media, 0, true, false, null, getTrackOutput(VIDEO_TRACK_NUMBER));
    assertSample(0, media, 0, true, false, null, getTrackOutput(AUDIO_TRACK_NUMBER));
  }

  public void testReadBlock() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addOpusTrack(AUDIO_TRACK_NUMBER, TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE)
        .addBlockMedia(2 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            true /* keyframe */, false /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertAudioFormat(AUDIO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE, MimeTypes.AUDIO_OPUS);
    assertSample(0, media, 0, true, false, null, getTrackOutput(AUDIO_TRACK_NUMBER));
  }

  public void testReadBlockNonKeyframe() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .addBlockMedia(1 /* trackNumber */, 0 /* clusterTimecode */, 0 /* blockTimecode */,
            false /* keyframe */, false /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertSample(0, media, 0, false, false, null, getTrackOutput(VIDEO_TRACK_NUMBER));
  }

  public void testReadEncryptedFrame() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 5, 1);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, settings)
        .addSimpleBlockEncryptedMedia(1 /* trackNumber */, 0 /* clusterTimecode */,
            0 /* blockTimecode */, true /* keyframe */, false /* invisible */,
            true /* validSignalByte */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertSample(0, media, 0, true, false, TEST_ENCRYPTION_KEY_ID,
        getTrackOutput(VIDEO_TRACK_NUMBER));
  }

  public void testReadEncryptedFrameWithInvalidSignalByte()
      throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    ContentEncodingSettings settings = new ContentEncodingSettings(0, 1, 5, 1);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, settings)
        .addSimpleBlockEncryptedMedia(1 /* trackNumber */, 0 /* clusterTimecode */,
            0 /* blockTimecode */, true /* keyframe */, false /* invisible */,
            false /* validSignalByte */, media)
        .build(1);

    try {
      TestUtil.consumeTestData(extractor, data);
      fail();
    } catch (ParserException exception) {
      assertTracksEnded();
      assertEquals("Extension bit is set in signal byte", exception.getMessage());
    }
  }

  public void testReadSampleInvisible() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .addSimpleBlockMedia(1 /* trackNumber */, 12 /* clusterTimecode */, 13 /* blockTimecode */,
            false /* keyframe */, true /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertSample(0, media, 25000, false, true, null, getTrackOutput(VIDEO_TRACK_NUMBER));
  }

  public void testReadSampleCustomTimecodeScale() throws IOException, InterruptedException {
    int timecodeScale = 1000;
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(timecodeScale, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .addSimpleBlockMedia(1 /* trackNumber */, 12 /* clusterTimecode */, 13 /* blockTimecode */,
            false /* keyframe */, false /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, timecodeScale);
    assertSample(0, media, 25, false, false, null, getTrackOutput(VIDEO_TRACK_NUMBER));
  }

  public void testReadSampleNegativeSimpleBlockTimecode() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addVp9Track(VIDEO_TRACK_NUMBER, TEST_WIDTH, TEST_HEIGHT, null)
        .addSimpleBlockMedia(1 /* trackNumber */, 13 /* clusterTimecode */, -12 /* blockTimecode */,
            true /* keyframe */, true /* invisible */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertVp9VideoFormat(VIDEO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE);
    assertSample(0, media, 1000, true, true, null, getTrackOutput(VIDEO_TRACK_NUMBER));
  }

  public void testReadSampleWithFixedSizeLacing() throws IOException, InterruptedException {
    byte[] media = createFrameData(100);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addOpusTrack(AUDIO_TRACK_NUMBER, TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE, TEST_DEFAULT_DURATION_NS)
        .addSimpleBlockMediaWithFixedSizeLacing(2 /* trackNumber */, 0 /* clusterTimecode */,
            0 /* blockTimecode */, 20 /* lacingFrameCount */, media)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertAudioFormat(AUDIO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE, MimeTypes.AUDIO_OPUS);
    for (int i = 0; i < 20; i++) {
      long expectedTimeUs = i * TEST_DEFAULT_DURATION_NS / 1000;
      assertSample(i, Arrays.copyOfRange(media, i * 5, i * 5 + 5), expectedTimeUs, true, false,
          null, getTrackOutput(AUDIO_TRACK_NUMBER));
    }
  }

  public void testReadSampleWithXiphLacing() throws IOException, InterruptedException {
    byte[] media = createFrameData(300);
    byte[] data = new StreamBuilder()
        .setHeader(WEBM_DOC_TYPE)
        .setInfo(DEFAULT_TIMECODE_SCALE, TEST_DURATION_TIMECODE)
        .addOpusTrack(AUDIO_TRACK_NUMBER, TEST_CHANNEL_COUNT, TEST_SAMPLE_RATE, TEST_CODEC_DELAY,
            TEST_SEEK_PRE_ROLL, TEST_OPUS_CODEC_PRIVATE, TEST_DEFAULT_DURATION_NS)
        .addSimpleBlockMediaWithXiphLacing(2 /* trackNumber */, 0 /* clusterTimecode */,
            0 /* blockTimecode */, media, 256, 1, 243)
        .build(1);

    TestUtil.consumeTestData(extractor, data);

    assertTracksEnded();
    assertAudioFormat(AUDIO_TRACK_NUMBER, DEFAULT_TIMECODE_SCALE, MimeTypes.AUDIO_OPUS);
    assertSample(0, Arrays.copyOfRange(media, 0, 256), 0 * TEST_DEFAULT_DURATION_NS / 1000, true,
        false, null, getTrackOutput(AUDIO_TRACK_NUMBER));
    assertSample(1, Arrays.copyOfRange(media, 256, 257), 1 * TEST_DEFAULT_DURATION_NS / 1000, true,
        false, null, getTrackOutput(AUDIO_TRACK_NUMBER));
    assertSample(2, Arrays.copyOfRange(media, 257, 300), 2 * TEST_DEFAULT_DURATION_NS / 1000, true,
        false, null, getTrackOutput(AUDIO_TRACK_NUMBER));
  }

  private FakeTrackOutput getTrackOutput(int trackNumber) {
    return extractorOutput.trackOutputs.get(trackNumber);
  }

  private void assertTracksEnded() {
    assertTrue(extractorOutput.tracksEnded);
  }

  private void assertVp9VideoFormat(int trackNumber, int timecodeScale) {
    MediaFormat format = getTrackOutput(trackNumber).format;
    assertEquals(Util.scaleLargeTimestamp(TEST_DURATION_TIMECODE, timecodeScale, 1000),
        format.durationUs);
    assertEquals(TEST_WIDTH, format.width);
    assertEquals(TEST_HEIGHT, format.height);
    assertEquals(MimeTypes.VIDEO_VP9, format.mimeType);
  }

  private void assertH264VideoFormat(int trackNumber, int timecodeScale) {
    MediaFormat format = getTrackOutput(trackNumber).format;
    assertEquals(Util.scaleLargeTimestamp(TEST_DURATION_TIMECODE, timecodeScale, 1000),
        format.durationUs);
    assertEquals(TEST_WIDTH, format.width);
    assertEquals(TEST_HEIGHT, format.height);
    assertEquals(MimeTypes.VIDEO_H264, format.mimeType);
  }

  private void assertAudioFormat(int trackNumber, int timecodeScale, String expectedMimeType) {
    MediaFormat format = getTrackOutput(trackNumber).format;
    assertEquals(Util.scaleLargeTimestamp(TEST_DURATION_TIMECODE, timecodeScale, 1000),
        format.durationUs);
    assertEquals(TEST_CHANNEL_COUNT, format.channelCount);
    assertEquals(TEST_SAMPLE_RATE, format.sampleRate);
    assertEquals(expectedMimeType, format.mimeType);
    if (MimeTypes.AUDIO_OPUS.equals(expectedMimeType)) {
      assertEquals(3, format.initializationData.size());
      android.test.MoreAsserts.assertEquals(TEST_OPUS_CODEC_PRIVATE,
          format.initializationData.get(0));
      assertEquals(TEST_CODEC_DELAY, ByteBuffer.wrap(format.initializationData.get(1))
          .order(ByteOrder.nativeOrder()).getLong());
      assertEquals(TEST_SEEK_PRE_ROLL, ByteBuffer.wrap(format.initializationData.get(2))
          .order(ByteOrder.nativeOrder()).getLong());
    } else if (MimeTypes.AUDIO_VORBIS.equals(expectedMimeType)) {
      assertEquals(2, format.initializationData.size());
      assertEquals(TEST_VORBIS_INFO_SIZE, format.initializationData.get(0).length);
      assertEquals(TEST_VORBIS_BOOKS_SIZE, format.initializationData.get(1).length);
    }
  }

  private void assertIndex(int timecodeScale, int cuePointCount) {
    ChunkIndex index = (ChunkIndex) extractorOutput.seekMap;
    assertEquals(cuePointCount, index.length);
    for (int i = 0; i < cuePointCount - 1; i++) {
      assertEquals(Util.scaleLargeTimestamp(10 * i, timecodeScale, 1000), index.timesUs[i]);
      assertEquals(Util.scaleLargeTimestamp(10, timecodeScale, 1000), index.durationsUs[i]);
      assertEquals(0, index.sizes[i]);
    }
    int lastIndex = cuePointCount - 1;
    long lastTimecode = 10 * lastIndex;
    long lastDurationTimecode = TEST_DURATION_TIMECODE - lastTimecode;
    assertEquals(Util.scaleLargeTimestamp(lastTimecode, timecodeScale, 1000),
        index.timesUs[lastIndex]);
    assertEquals(Util.scaleLargeTimestamp(lastDurationTimecode, timecodeScale, 1000),
        index.durationsUs[lastIndex]);
  }

  private void assertIndexUnseekable() {
    assertEquals(SeekMap.UNSEEKABLE, extractorOutput.seekMap);
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

}
