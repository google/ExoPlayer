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
package com.google.android.exoplayer.extractor.mp4;

import android.annotation.TargetApi;
import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.testutil.FakeExtractorOutput;
import com.google.android.exoplayer.testutil.FakeTrackOutput;
import com.google.android.exoplayer.testutil.TestUtil;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;

/**
 * Tests for {@link Mp4Extractor}.
 */
@TargetApi(16)
public final class Mp4ExtractorTest extends TestCase {

  /** String of hexadecimal bytes containing the video stsd payload from an AVC video. */
  private static final byte[] VIDEO_STSD_PAYLOAD = Util.getBytesFromHexString(
      "00000000000000010000009961766331000000000000000100000000000000000000000000000000050002d00048"
      + "000000480000000000000001000000000000000000000000000000000000000000000000000000000000000000"
      + "18ffff0000002f617663430164001fffe100186764001facb402802dd80880000003008000001e078c19500100"
      + "0468ee3cb000000014627472740000e35c0042a61000216cb8");
  private static final byte[] VIDEO_HDLR_PAYLOAD = Util.getBytesFromHexString(
      "000000000000000076696465");
  private static final byte[] VIDEO_MDHD_PAYLOAD = Util.getBytesFromHexString(
      "0000000000000000cf6c48890000001e00001c8a55c40000");
  private static final int TIMESCALE = 30;
  private static final int VIDEO_WIDTH = 1280;
  private static final int VIDEO_HEIGHT = 720;

  /** String of hexadecimal bytes containing the video stsd payload for an mp4v track. */
  private static final byte[] VIDEO_STSD_MP4V_PAYLOAD = Util.getBytesFromHexString(
      "0000000000000001000000A36D703476000000000000000100000000000000000000000000000000014000B40048"
      + "000000480000000000000001000000000000000000000000000000000000000000000000000000000000000000"
      + "18FFFF0000004D6573647300000000033F00000004372011001A400004CF280002F1180528000001B001000001"
      + "B58913000001000000012000C48D8800F50A04169463000001B2476F6F676C65060102");
  private static final int VIDEO_MP4V_WIDTH = 320;
  private static final int VIDEO_MP4V_HEIGHT = 180;

  /** String of hexadecimal bytes containing the audio stsd payload from an AAC track. */
  private static final byte[] AUDIO_STSD_PAYLOAD = Util.getBytesFromHexString(
      "0000000000000001000000596d703461000000000000000100000000000000000001001000000000ac4400000000"
      + "003565736473000000000327000000041f401500023e00024bc000023280051012080000000000000000000000"
      + "000000060102");
  private static final byte[] AUDIO_HDLR_PAYLOAD = Util.getBytesFromHexString(
      "0000000000000000736f756e");
  private static final byte[] AUDIO_MDHD_PAYLOAD = Util.getBytesFromHexString(
      "00000000cf6c4889cf6c488a0000ac4400a3e40055c40000");

  /** String of hexadecimal bytes for an ftyp payload with major_brand mp41 and minor_version 0. **/
  private static final byte[] FTYP_PAYLOAD = Util.getBytesFromHexString("6d70343100000000");

  /** String of hexadecimal bytes containing an mvhd payload from an AVC/AAC video. */
  private static final byte[] MVHD_PAYLOAD = Util.getBytesFromHexString(
      "00000000cf6c4888cf6c48880000025800023ad40001000001000000000000000000000000010000000000000000"
      + "000000000000000100000000000000000000000000004000000000000000000000000000000000000000000000"
      + "000000000000000003");

  /** String of hexadecimal bytes containing a tkhd payload with an unknown duration. */
  private static final byte[] TKHD_PAYLOAD = Util.getBytesFromHexString(
      "00000007D1F0C7BFD1F0C7BF0000000000000000FFFFFFFF00000000000000000000000000000000000100000000"
      + "0000000000000000000000010000000000000000000000000000400000000780000004380000");

  /** Video frame timestamps in time units. */
  private static final int[] SAMPLE_TIMESTAMPS = {0, 2, 3, 5, 6, 7};
  /** Video frame sizes in bytes, including a very large sample. */
  private static final int[] SAMPLE_SIZES = {100, 20, 20, 44, 100, 1 * 1024 * 1024};
  /** Indices of key-frames. */
  private static final boolean[] SAMPLE_IS_SYNC = {true, false, false, false, true, true};
  /** Indices of video frame chunk offsets. */
  private static final int[] CHUNK_OFFSETS = {1208, 2128, 3128, 4128};
  /** Numbers of video frames in each chunk. */
  private static final int[] SAMPLES_IN_CHUNK = {2, 2, 1, 1};
  /** The mdat box must be large enough to avoid reading chunk sample data out of bounds. */
  private static final int MDAT_SIZE = 10 * 1024 * 1024;
  /** Empty byte array. */
  private static final byte[] EMPTY = new byte[0];

  private Mp4Extractor extractor;
  private FakeExtractorOutput extractorOutput;

  @Override
  public void setUp() {
    extractor = new Mp4Extractor();
    extractorOutput = new FakeExtractorOutput();
    extractor.init(extractorOutput);
  }

  @Override
  public void tearDown() {
    extractor = null;
    extractorOutput = null;
  }

  public void testParsesValidMp4File() throws Exception {
    TestUtil.consumeTestData(extractor,
        getTestInputData(true /* includeStss */, false /* mp4vFormat */));

    // The seek map is correct.
    assertSeekMap(extractorOutput.seekMap, true);

    // The video and audio formats are set correctly.
    assertEquals(2, extractorOutput.trackOutputs.size());
    MediaFormat videoFormat = extractorOutput.trackOutputs.get(0).format;
    MediaFormat audioFormat = extractorOutput.trackOutputs.get(1).format;
    assertEquals(MimeTypes.VIDEO_H264, videoFormat.mimeType);
    assertEquals(VIDEO_WIDTH, videoFormat.width);
    assertEquals(VIDEO_HEIGHT, videoFormat.height);
    assertEquals(MimeTypes.AUDIO_AAC, audioFormat.mimeType);

    // The timestamps and sizes are set correctly.
    FakeTrackOutput videoTrackOutput = extractorOutput.trackOutputs.get(0);
    videoTrackOutput.assertSampleCount(SAMPLE_TIMESTAMPS.length);
    for (int i = 0; i < SAMPLE_TIMESTAMPS.length; i++) {
      byte[] sampleData = getOutputSampleData(i, true);
      int sampleFlags = SAMPLE_IS_SYNC[i] ? C.SAMPLE_FLAG_SYNC : 0;
      long sampleTimestampUs = getVideoTimestampUs(SAMPLE_TIMESTAMPS[i]);
      videoTrackOutput.assertSample(i, sampleData, sampleTimestampUs, sampleFlags, null);
    }
  }

  public void testParsesValidMp4FileWithoutStss() throws Exception {
    TestUtil.consumeTestData(extractor,
        getTestInputData(false /* includeStss */, false /* mp4vFormat */));

    // The seek map is correct.
    assertSeekMap(extractorOutput.seekMap, false);

    // The timestamps and sizes are set correctly, and all samples are synchronization samples.
    FakeTrackOutput videoTrackOutput = extractorOutput.trackOutputs.get(0);
    videoTrackOutput.assertSampleCount(SAMPLE_TIMESTAMPS.length);
    for (int i = 0; i < SAMPLE_TIMESTAMPS.length; i++) {
      byte[] sampleData = getOutputSampleData(i, true);
      int sampleFlags = C.SAMPLE_FLAG_SYNC;
      long sampleTimestampUs = getVideoTimestampUs(SAMPLE_TIMESTAMPS[i]);
      videoTrackOutput.assertSample(i, sampleData, sampleTimestampUs, sampleFlags, null);
    }
  }

  public void testParsesValidMp4vFile() throws Exception {
    TestUtil.consumeTestData(extractor,
        getTestInputData(true /* includeStss */, true /* mp4vFormat */));

    // The seek map is correct.
    assertSeekMap(extractorOutput.seekMap, true);

    // The video and audio formats are set correctly.
    assertEquals(2, extractorOutput.trackOutputs.size());
    MediaFormat videoFormat = extractorOutput.trackOutputs.get(0).format;
    MediaFormat audioFormat = extractorOutput.trackOutputs.get(1).format;
    assertEquals(MimeTypes.VIDEO_MP4V, videoFormat.mimeType);
    assertEquals(VIDEO_MP4V_WIDTH, videoFormat.width);
    assertEquals(VIDEO_MP4V_HEIGHT, videoFormat.height);
    assertEquals(MimeTypes.AUDIO_AAC, audioFormat.mimeType);

    // The timestamps and sizes are set correctly.
    FakeTrackOutput videoTrackOutput = extractorOutput.trackOutputs.get(0);
    videoTrackOutput.assertSampleCount(SAMPLE_TIMESTAMPS.length);
    for (int i = 0; i < SAMPLE_TIMESTAMPS.length; i++) {
      byte[] sampleData = getOutputSampleData(i, false);
      int sampleFlags = SAMPLE_IS_SYNC[i] ? C.SAMPLE_FLAG_SYNC : 0;
      long sampleTimestampUs = getVideoTimestampUs(SAMPLE_TIMESTAMPS[i]);
      videoTrackOutput.assertSample(i, sampleData, sampleTimestampUs, sampleFlags, null);
    }
  }

  private static void assertSeekMap(SeekMap seekMap, boolean haveStss) {
    assertNotNull(seekMap);
    int expectedSeekPosition = getSampleOffset(0);
    for (int i = 0; i < SAMPLE_TIMESTAMPS.length; i++) {
      // Seek to just before the current sample.
      long seekPositionUs = getVideoTimestampUs(SAMPLE_TIMESTAMPS[i]) - 1;
      assertEquals(expectedSeekPosition, seekMap.getPosition(seekPositionUs));
      // If the current sample is a sync sample, the expected seek position will change.
      if (SAMPLE_IS_SYNC[i] || !haveStss) {
        expectedSeekPosition = getSampleOffset(i);
      }
      // Seek to the current sample.
      seekPositionUs = getVideoTimestampUs(SAMPLE_TIMESTAMPS[i]);
      assertEquals(expectedSeekPosition, seekMap.getPosition(seekPositionUs));
      // Seek to just after the current sample.
      seekPositionUs = getVideoTimestampUs(SAMPLE_TIMESTAMPS[i]) + 1;
      assertEquals(expectedSeekPosition, seekMap.getPosition(seekPositionUs));
    }
  }

  /** Returns a video timestamp in microseconds corresponding to {@code timeUnits}. */
  private static long getVideoTimestampUs(int timeUnits) {
    return Util.scaleLargeTimestamp(timeUnits, C.MICROS_PER_SECOND, TIMESCALE);
  }

  private static byte[] getStco() {
    byte[] result = new byte[4 + 4 + 4 * CHUNK_OFFSETS.length];
    ByteBuffer buffer = ByteBuffer.wrap(result);
    buffer.putInt(0); // Version (skipped)
    buffer.putInt(CHUNK_OFFSETS.length);
    for (int chunkOffset : CHUNK_OFFSETS) {
      buffer.putInt(chunkOffset);
    }
    return result;
  }

  private static byte[] getStsc() {
    int samplesPerChunk = -1;
    List<Integer> samplesInChunkChangeIndices = new ArrayList<>();
    for (int i = 0; i < SAMPLES_IN_CHUNK.length; i++) {
      if (SAMPLES_IN_CHUNK[i] != samplesPerChunk) {
        samplesInChunkChangeIndices.add(i);
        samplesPerChunk = SAMPLES_IN_CHUNK[i];
      }
    }

    byte[] result = new byte[4 + 4 + 3 * 4 * samplesInChunkChangeIndices.size()];
    ByteBuffer buffer = ByteBuffer.wrap(result);
    buffer.putInt(0); // Version (skipped)
    buffer.putInt(samplesInChunkChangeIndices.size());
    for (int index : samplesInChunkChangeIndices) {
      buffer.putInt(index + 1);
      buffer.putInt(SAMPLES_IN_CHUNK[index]);
      buffer.putInt(0); // Sample description index (skipped)
    }
    return result;
  }

  private static byte[] getStsz() {
    byte[] result = new byte[4 + 4 + 4 + 4 * SAMPLE_SIZES.length];
    ByteBuffer buffer = ByteBuffer.wrap(result);
    buffer.putInt(0); // Version (skipped)
    buffer.putInt(0); // No fixed sample size.
    buffer.putInt(SAMPLE_SIZES.length);
    for (int size : SAMPLE_SIZES) {
      buffer.putInt(size);
    }
    return result;
  }

  private static byte[] getStss() {
    int synchronizationSampleCount = 0;
    for (int i = 0; i < SAMPLE_IS_SYNC.length; i++) {
      if (SAMPLE_IS_SYNC[i]) {
        synchronizationSampleCount++;
      }
    }
    byte[] result = new byte[4 + 4 + 4 * synchronizationSampleCount];
    ByteBuffer buffer = ByteBuffer.wrap(result);
    buffer.putInt(0); // Version (skipped)
    buffer.putInt(synchronizationSampleCount);
    for (int i = 0; i < SAMPLE_IS_SYNC.length; i++) {
      if (SAMPLE_IS_SYNC[i]) {
        buffer.putInt(i + 1);
      }
    }
    return result;
  }

  private static byte[] getStts() {
    int sampleTimestampDeltaChanges = 0;
    int currentSampleTimestampDelta = -1;
    for (int i = 1; i < SAMPLE_TIMESTAMPS.length; i++) {
      int timestampDelta = SAMPLE_TIMESTAMPS[i] - SAMPLE_TIMESTAMPS[i - 1];
      if (timestampDelta != currentSampleTimestampDelta) {
        sampleTimestampDeltaChanges++;
        currentSampleTimestampDelta = timestampDelta;
      }
    }

    byte[] result = new byte[4 + 4 + 2 * 4 * sampleTimestampDeltaChanges];
    ByteBuffer buffer = ByteBuffer.wrap(result);
    buffer.putInt(0); // Version (skipped);
    buffer.putInt(sampleTimestampDeltaChanges);
    int lastTimestampDeltaChangeIndex = 1;
    currentSampleTimestampDelta = SAMPLE_TIMESTAMPS[1] - SAMPLE_TIMESTAMPS[0];
    for (int i = 2; i < SAMPLE_TIMESTAMPS.length; i++) {
      int timestampDelta = SAMPLE_TIMESTAMPS[i] - SAMPLE_TIMESTAMPS[i - 1];
      if (timestampDelta != currentSampleTimestampDelta) {
        buffer.putInt(i - lastTimestampDeltaChangeIndex);
        lastTimestampDeltaChangeIndex = i;
        buffer.putInt(currentSampleTimestampDelta);
        currentSampleTimestampDelta = timestampDelta;
      }
    }
    // The last sample also has a duration, so the number of entries is the number of samples.
    buffer.putInt(SAMPLE_TIMESTAMPS.length - lastTimestampDeltaChangeIndex + 1);
    buffer.putInt(currentSampleTimestampDelta);
    return result;
  }

  private static byte[] getMdat(int mdatOffset, boolean isH264) {
    ByteBuffer mdat = ByteBuffer.allocate(MDAT_SIZE);
    int sampleIndex = 0;
    for (int chunk = 0; chunk < CHUNK_OFFSETS.length; chunk++) {
      mdat.position(CHUNK_OFFSETS[chunk] - mdatOffset);
      for (int sample = 0; sample < SAMPLES_IN_CHUNK[chunk]; sample++) {
        mdat.put(getInputSampleData(sampleIndex++, isH264));
      }
    }
    return mdat.array();
  }

  private static byte[] getInputSampleData(int index, boolean isH264) {
    ByteBuffer sample = ByteBuffer.allocate(SAMPLE_SIZES[index]);
    for (int i = 0; i < SAMPLE_SIZES[index]; i++) {
      sample.put((byte) i);
    }
    if (isH264) {
      // First four bytes should specify the remaining length of the sample. This assumes that the
      // sample consists of a single length delimited NAL unit.
      sample.position(0);
      sample.putInt(SAMPLE_SIZES[index] - 4);
    }
    return sample.array();
  }

  private static byte[] getOutputSampleData(int index, boolean isH264) {
    byte[] sampleData = getInputSampleData(index, isH264);
    if (isH264) {
      // The output sample should begin with a NAL start code.
      sampleData[0] = 0;
      sampleData[1] = 0;
      sampleData[2] = 0;
      sampleData[3] = 1;
    }
    return sampleData;
  }

  private static int getSampleOffset(int index) {
    int sampleCount = 0;
    int chunkIndex = 0;
    int samplesLeftInChunk = SAMPLES_IN_CHUNK[chunkIndex];
    int offsetInChunk = 0;
    while (sampleCount < index) {
      offsetInChunk += SAMPLE_SIZES[sampleCount++];
      samplesLeftInChunk--;
      if (samplesLeftInChunk == 0) {
        chunkIndex++;
        samplesLeftInChunk = SAMPLES_IN_CHUNK[chunkIndex];
        offsetInChunk = 0;
      }
    }
    return CHUNK_OFFSETS[chunkIndex] + offsetInChunk;
  }

  private static final byte[] getTestInputData(boolean includeStss, boolean mp4vFormat) {
    return includeStss ? getTestMp4File(mp4vFormat)
        : getTestMp4FileWithoutSynchronizationData(mp4vFormat);
  }

  /** Gets a valid MP4 file with audio/video tracks and synchronization data. */
  private static byte[] getTestMp4File(boolean mp4vFormat) {
    return Mp4Atom.serialize(
        atom(Atom.TYPE_ftyp, FTYP_PAYLOAD),
        atom(Atom.TYPE_moov,
            atom(Atom.TYPE_mvhd, MVHD_PAYLOAD),
            atom(Atom.TYPE_trak,
                atom(Atom.TYPE_tkhd, TKHD_PAYLOAD),
                atom(Atom.TYPE_mdia,
                    atom(Atom.TYPE_mdhd, VIDEO_MDHD_PAYLOAD),
                    atom(Atom.TYPE_hdlr, VIDEO_HDLR_PAYLOAD),
                    atom(Atom.TYPE_minf,
                        atom(Atom.TYPE_vmhd, EMPTY),
                        atom(Atom.TYPE_stbl,
                            atom(Atom.TYPE_stsd,
                                mp4vFormat ? VIDEO_STSD_MP4V_PAYLOAD : VIDEO_STSD_PAYLOAD),
                            atom(Atom.TYPE_stts, getStts()),
                            atom(Atom.TYPE_stss, getStss()),
                            atom(Atom.TYPE_stsc, getStsc()),
                            atom(Atom.TYPE_stsz, getStsz()),
                            atom(Atom.TYPE_stco, getStco()))))),
            atom(Atom.TYPE_trak,
                atom(Atom.TYPE_tkhd, TKHD_PAYLOAD),
                atom(Atom.TYPE_mdia,
                    atom(Atom.TYPE_mdhd, AUDIO_MDHD_PAYLOAD),
                    atom(Atom.TYPE_hdlr, AUDIO_HDLR_PAYLOAD),
                    atom(Atom.TYPE_minf,
                        atom(Atom.TYPE_vmhd, EMPTY),
                        atom(Atom.TYPE_stbl,
                            atom(Atom.TYPE_stsd, AUDIO_STSD_PAYLOAD),
                            atom(Atom.TYPE_stts, getStts()),
                            atom(Atom.TYPE_stss, getStss()),
                            atom(Atom.TYPE_stsc, getStsc()),
                            atom(Atom.TYPE_stsz, getStsz()),
                            atom(Atom.TYPE_stco, getStco())))))),
        atom(Atom.TYPE_mdat, getMdat(mp4vFormat ? 1176 : 1166, !mp4vFormat)));
  }

  /** Gets a valid MP4 file with audio/video tracks and without a synchronization table. */
  private static byte[] getTestMp4FileWithoutSynchronizationData(boolean mp4vFormat) {
    return Mp4Atom.serialize(
        atom(Atom.TYPE_ftyp, FTYP_PAYLOAD),
        atom(Atom.TYPE_moov,
            atom(Atom.TYPE_mvhd, MVHD_PAYLOAD),
            atom(Atom.TYPE_trak,
                atom(Atom.TYPE_tkhd, TKHD_PAYLOAD),
                atom(Atom.TYPE_mdia,
                    atom(Atom.TYPE_mdhd, VIDEO_MDHD_PAYLOAD),
                    atom(Atom.TYPE_hdlr, VIDEO_HDLR_PAYLOAD),
                    atom(Atom.TYPE_minf,
                        atom(Atom.TYPE_vmhd, EMPTY),
                        atom(Atom.TYPE_stbl,
                            atom(Atom.TYPE_stsd,
                                mp4vFormat ? VIDEO_STSD_MP4V_PAYLOAD : VIDEO_STSD_PAYLOAD),
                            atom(Atom.TYPE_stts, getStts()),
                            atom(Atom.TYPE_stsc, getStsc()),
                            atom(Atom.TYPE_stsz, getStsz()),
                            atom(Atom.TYPE_stco, getStco()))))),
            atom(Atom.TYPE_trak,
                atom(Atom.TYPE_tkhd, TKHD_PAYLOAD),
                atom(Atom.TYPE_mdia,
                    atom(Atom.TYPE_mdhd, AUDIO_MDHD_PAYLOAD),
                    atom(Atom.TYPE_hdlr, AUDIO_HDLR_PAYLOAD),
                    atom(Atom.TYPE_minf,
                        atom(Atom.TYPE_vmhd, EMPTY),
                        atom(Atom.TYPE_stbl,
                            atom(Atom.TYPE_stsd, AUDIO_STSD_PAYLOAD),
                            atom(Atom.TYPE_stts, getStts()),
                            atom(Atom.TYPE_stsc, getStsc()),
                            atom(Atom.TYPE_stsz, getStsz()),
                            atom(Atom.TYPE_stco, getStco())))))),
        atom(Atom.TYPE_mdat, getMdat(mp4vFormat ? 1120 : 1110, !mp4vFormat)));
  }

  private static Mp4Atom atom(int type, Mp4Atom... containedMp4Atoms) {
    return new Mp4Atom(type, containedMp4Atoms);
  }

  private static Mp4Atom atom(int type, byte[] payload) {
    return new Mp4Atom(type, payload);
  }

  /**
   * MP4 atom that can be serialized as a byte array.
   */
  private static final class Mp4Atom {

    public static byte[] serialize(Mp4Atom... atoms) {
      int size = 0;
      for (Mp4Atom atom : atoms) {
        size += atom.getSize();
      }
      ByteBuffer buffer = ByteBuffer.allocate(size);
      for (Mp4Atom atom : atoms) {
        atom.getData(buffer);
      }
      return buffer.array();
    }

    private static final int HEADER_SIZE = 8;

    private final int type;
    private final Mp4Atom[] containedMp4Atoms;
    private final byte[] payload;

    private Mp4Atom(int type, Mp4Atom... containedMp4Atoms) {
      this.type = type;
      this.containedMp4Atoms = containedMp4Atoms;
      payload = null;
    }

    private Mp4Atom(int type, byte[] payload) {
      this.type = type;
      this.payload = payload;
      containedMp4Atoms = null;
    }

    private int getSize() {
      int size = HEADER_SIZE;
      if (payload != null) {
        size += payload.length;
      } else {
        for (Mp4Atom atom : containedMp4Atoms) {
          size += atom.getSize();
        }
      }
      return size;
    }

    private void getData(ByteBuffer byteBuffer) {
      byteBuffer.putInt(getSize());
      byteBuffer.putInt(type);

      if (payload != null) {
        byteBuffer.put(payload);
      } else {
        for (Mp4Atom atom : containedMp4Atoms) {
          atom.getData(byteBuffer);
        }
      }
    }

  }

}
