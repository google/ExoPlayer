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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.ByteArrayDataSource;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import junit.framework.TestCase;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Tests for {@link Mp4Extractor}.
 */
@TargetApi(16)
public class Mp4ExtractorTest extends TestCase {

  /** String of hexadecimal bytes containing the video stsd payload from an AVC video. */
  private static final byte[] VIDEO_STSD_PAYLOAD = getByteArray(
      "00000000000000010000009961766331000000000000000100000000000000000000000000000000050002"
      + "d00048000000480000000000000001000000000000000000000000000000000000000000000000000000"
      + "00000000000018ffff0000002f617663430164001fffe100186764001facb402802dd808800000030080"
      + "00001e078c195001000468ee3cb000000014627472740000e35c0042a61000216cb8");
  private static final byte[] VIDEO_HDLR_PAYLOAD = getByteArray("000000000000000076696465");
  private static final byte[] VIDEO_MDHD_PAYLOAD =
      getByteArray("0000000000000000cf6c48890000001e00001c8a55c40000");
  private static final int TIMESCALE = 30;
  private static final int VIDEO_WIDTH = 1280;
  private static final int VIDEO_HEIGHT = 720;

  /** String of hexadecimal bytes containing the video stsd payload for an mp4v track. */
  private static final byte[] VIDEO_STSD_MP4V_PAYLOAD = getByteArray(
      "0000000000000001000000A36D703476000000000000000100000000000000000000000000000000014000"
      + "B40048000000480000000000000001000000000000000000000000000000000000000000000000000000"
      + "00000000000018FFFF0000004D6573647300000000033F00000004372011001A400004CF280002F11805"
      + "28000001B001000001B58913000001000000012000C48D8800F50A04169463000001B2476F6F676C6506"
      + "0102");
  private static final int VIDEO_MP4V_WIDTH = 320;
  private static final int VIDEO_MP4V_HEIGHT = 180;

  /** String of hexadecimal bytes containing the audio stsd payload from an AAC track. */
  private static final byte[] AUDIO_STSD_PAYLOAD = getByteArray(
      "0000000000000001000000596d703461000000000000000100000000000000000001001000000000ac4400"
      + "000000003565736473000000000327000000041f401500023e00024bc000023280051012080000000000"
      + "000000000000000000060102");
  private static final byte[] AUDIO_HDLR_PAYLOAD = getByteArray("0000000000000000736f756e");
  private static final byte[] AUDIO_MDHD_PAYLOAD =
      getByteArray("00000000cf6c4889cf6c488a0000ac4400a3e40055c40000");

  /** String of hexadecimal bytes containing an mvhd payload from an AVC/AAC video. */
  private static final byte[] MVHD_PAYLOAD = getByteArray(
      "00000000cf6c4888cf6c48880000025800023ad40001000001000000000000000000000000010000000000"
      + "000000000000000000000100000000000000000000000000004000000000000000000000000000000000"
      + "000000000000000000000000000003");

  /** String of hexadecimal bytes containing a tkhd payload with an unknown duration. */
  private static final byte[] TKHD_PAYLOAD =
      getByteArray("0000000000000000000000000000000000000000FFFFFFFF");

  /** Video frame timestamps in time units. */
  private static final int[] SAMPLE_TIMESTAMPS = {0, 2, 3, 5, 6, 7};
  /** Video frame sizes in bytes, including a very large sample. */
  private static final int[] SAMPLE_SIZES = {100, 20, 20, 44, 100, 1 * 1024 * 1024};
  /** Indices of key-frames. */
  private static final int[] SYNCHRONIZATION_SAMPLE_INDICES = {0, 4, 5};
  /** Indices of video frame chunk offsets. */
  private static final int[] CHUNK_OFFSETS = {1080, 2000, 3000, 4000};
  /** Numbers of video frames in each chunk. */
  private static final int[] SAMPLES_IN_CHUNK = {2, 2, 1, 1};
  /** The mdat box must be large enough to avoid reading chunk sample data out of bounds. */
  private static final int MDAT_SIZE = 10 * 1024 * 1024;
  /** Fake HTTP URI that can't be opened. */
  private static final Uri FAKE_URI = Uri.parse("http://");
  /** Empty byte array. */
  private static final byte[] EMPTY = new byte[0];

  public void testParsesValidMp4File() throws Exception {
    // Given an extractor with an AVC/AAC file
    Mp4ExtractorWrapper extractor =
        prepareSampleExtractor(getFakeDataSource(true /* includeStss */, false /* mp4vFormat */));

    // The MIME type and metadata are set correctly.
    assertEquals(MimeTypes.VIDEO_H264, extractor.mediaFormats[0].mimeType);
    assertEquals(MimeTypes.AUDIO_AAC, extractor.mediaFormats[1].mimeType);

    assertEquals(VIDEO_WIDTH, extractor.selectedTrackMediaFormat.width);
    assertEquals(VIDEO_HEIGHT, extractor.selectedTrackMediaFormat.height);
  }

  public void testParsesValidMp4vFile() throws Exception {
    // Given an extractor with an mp4v file
    Mp4ExtractorWrapper extractor =
        prepareSampleExtractor(getFakeDataSource(true /* includeStss */, true /* mp4vFormat */));

    // The MIME type and metadata are set correctly.
    assertEquals(MimeTypes.VIDEO_MP4V, extractor.selectedTrackMediaFormat.mimeType);
    assertEquals(VIDEO_MP4V_WIDTH, extractor.selectedTrackMediaFormat.width);
    assertEquals(VIDEO_MP4V_HEIGHT, extractor.selectedTrackMediaFormat.height);
  }

  public void testSampleTimestampsMatch() throws Exception {
    // Given an extractor
    Mp4ExtractorWrapper extractor =
        prepareSampleExtractor(getFakeDataSource(true /* includeStss */, false /* mp4vFormat */));

    // The timestamps are set correctly.
    SampleHolder sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
    for (int i = 0; i < SAMPLE_TIMESTAMPS.length; i++) {
      extractor.readSample(0, sampleHolder);
      assertEquals(getVideoTimestampUs(SAMPLE_TIMESTAMPS[i]), sampleHolder.timeUs);
    }
    assertEquals(SampleSource.END_OF_STREAM, extractor.readSample(0, sampleHolder));
  }

  public void testSeekToStart() throws Exception {
    // When seeking to the start
    int timestampTimeUnits = SAMPLE_TIMESTAMPS[0];
    long sampleTimestampUs =
        getTimestampUsResultingFromSeek(getVideoTimestampUs(timestampTimeUnits));

    // The timestamp is at the start.
    assertEquals(getVideoTimestampUs(timestampTimeUnits), sampleTimestampUs);
  }

  public void testSeekToEnd() throws Exception {
    // When seeking to the end
    int timestampTimeUnits = SAMPLE_TIMESTAMPS[SAMPLE_TIMESTAMPS.length - 1];
    long sampleTimestampUs =
        getTimestampUsResultingFromSeek(getVideoTimestampUs(timestampTimeUnits));

    // The timestamp is at the end.
    assertEquals(getVideoTimestampUs(timestampTimeUnits), sampleTimestampUs);
  }

  public void testSeekToNearStart() throws Exception {
    // When seeking to just after the start
    int timestampTimeUnits = SAMPLE_TIMESTAMPS[0];
    long sampleTimestampUs =
        getTimestampUsResultingFromSeek(getVideoTimestampUs(timestampTimeUnits) + 1);

    // The timestamp is at the start.
    assertEquals(getVideoTimestampUs(timestampTimeUnits), sampleTimestampUs);
  }

  public void testSeekToBeforeLastSynchronizationSample() throws Exception {
    // When seeking to just after the start
    long sampleTimestampUs =
        getTimestampUsResultingFromSeek(getVideoTimestampUs(SAMPLE_TIMESTAMPS[4]) - 1);

    // The timestamp is at the start.
    assertEquals(getVideoTimestampUs(SAMPLE_TIMESTAMPS[0]), sampleTimestampUs);
  }

  public void testAllSamplesAreSynchronizationSamplesWhenStssIsMissing() throws Exception {
    // Given an extractor without an stss box
    Mp4ExtractorWrapper extractor =
        prepareSampleExtractor(getFakeDataSource(false /* includeStss */, false /* mp4vFormat */));
    // All samples are synchronization samples.
    SampleHolder sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
    int sampleIndex = 0;
    while (true) {
      int result = extractor.readSample(0, sampleHolder);
      if (result == SampleSource.SAMPLE_READ) {
        assertTrue(sampleHolder.isSyncFrame());
        sampleHolder.clearData();
        sampleIndex++;
      } else if (result == SampleSource.END_OF_STREAM) {
        break;
      }
    }
    assertTrue(sampleIndex == SAMPLE_SIZES.length);
  }

  public void testReadAllSamplesSucceeds() throws Exception {
    // Given an extractor
    Mp4ExtractorWrapper extractor =
        prepareSampleExtractor(getFakeDataSource(true /* includeStss */, false /* mp4vFormat */));

    // The sample sizes are set correctly.
    SampleHolder sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
    int sampleIndex = 0;
    while (true) {
      int result = extractor.readSample(0, sampleHolder);
      if (result == SampleSource.SAMPLE_READ) {
        assertEquals(SAMPLE_SIZES[sampleIndex], sampleHolder.size);
        sampleHolder.clearData();
        sampleIndex++;
      } else if (result == SampleSource.END_OF_STREAM) {
        break;
      }
    }
    assertEquals(SAMPLE_SIZES.length, sampleIndex);
  }

  /** Returns the sample time read after seeking to {@code timestampTimeUnits}. */
  private static long getTimestampUsResultingFromSeek(long timestampTimeUnits) throws Exception {
    Mp4ExtractorWrapper extractor =
        prepareSampleExtractor(getFakeDataSource(true /* includeStss */, false /* mp4vFormat */));

    extractor.seekTo(timestampTimeUnits);

    SampleHolder sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
    while (true) {
      int result = extractor.readSample(0, sampleHolder);
      if (result == SampleSource.SAMPLE_READ) {
        return sampleHolder.timeUs;
      } else if (result == SampleSource.END_OF_STREAM) {
        return -1;
      }
    }
  }

  private static Mp4ExtractorWrapper prepareSampleExtractor(DataSource dataSource)
      throws Exception {
    Mp4ExtractorWrapper extractor = new Mp4ExtractorWrapper(dataSource);
    extractor.prepare();
    return extractor;
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
    List<Integer> samplesInChunkChangeIndices = new ArrayList<Integer>();
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
    byte[] result = new byte[4 + 4 + 4 * SYNCHRONIZATION_SAMPLE_INDICES.length];
    ByteBuffer buffer = ByteBuffer.wrap(result);
    buffer.putInt(0); // Version (skipped)
    buffer.putInt(SYNCHRONIZATION_SAMPLE_INDICES.length);
    for (int synchronizationSampleIndex : SYNCHRONIZATION_SAMPLE_INDICES) {
      buffer.putInt(synchronizationSampleIndex + 1);
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

  private static byte[] getMdat(int mdatOffset) {
    ByteBuffer mdat = ByteBuffer.allocate(MDAT_SIZE);
    int sampleIndex = 0;
    for (int chunk = 0; chunk < CHUNK_OFFSETS.length; chunk++) {
      int sampleOffset = CHUNK_OFFSETS[chunk];
      for (int sample = 0; sample < SAMPLES_IN_CHUNK[chunk]; sample++) {
        int sampleSize = SAMPLE_SIZES[sampleIndex++];
        mdat.putInt(sampleOffset - mdatOffset, sampleSize);
        sampleOffset += sampleSize;
      }
    }
    return mdat.array();
  }

  private static final DataSource getFakeDataSource(boolean includeStss, boolean mp4vFormat) {
    return new ByteArrayDataSource(includeStss
        ? getTestMp4File(mp4vFormat) : getTestMp4FileWithoutSynchronizationData(mp4vFormat));
  }

  /** Gets a valid MP4 file with audio/video tracks and synchronization data. */
  private static byte[] getTestMp4File(boolean mp4vFormat) {
    return Mp4Atom.serialize(
        atom(Atom.TYPE_ftyp, EMPTY),
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
        atom(Atom.TYPE_mdat, getMdat(mp4vFormat ? 1048 : 1038)));
  }

  /** Gets a valid MP4 file with audio/video tracks and without a synchronization table. */
  private static byte[] getTestMp4FileWithoutSynchronizationData(boolean mp4vFormat) {
    return Mp4Atom.serialize(
        atom(Atom.TYPE_ftyp, EMPTY),
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
        atom(Atom.TYPE_mdat, getMdat(mp4vFormat ? 992 : 982)));
  }

  private static Mp4Atom atom(int type, Mp4Atom... containedMp4Atoms) {
    return new Mp4Atom(type, containedMp4Atoms);
  }

  private static Mp4Atom atom(int type, byte[] payload) {
    return new Mp4Atom(type, payload);
  }

  private static byte[] getByteArray(String hexBytes) {
    byte[] result = new byte[hexBytes.length() / 2];
    for (int i = 0; i < result.length; i++) {
      result[i] = (byte) ((Character.digit(hexBytes.charAt(i * 2), 16) << 4)
          + Character.digit(hexBytes.charAt(i * 2 + 1), 16));
    }
    return result;
  }

  /** MP4 atom that can be serialized as a byte array. */
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

  /**
   * Creates a {@link Mp4Extractor} on a separate thread with a looper, so that it can use a handler
   * for loading, and provides blocking operations like {@link #seekTo} and {@link #readSample}.
   */
  private static final class Mp4ExtractorWrapper extends Thread {

    private static final int MSG_PREPARE = 0;
    private static final int MSG_SEEK_TO = 1;
    private static final int MSG_READ_SAMPLE = 2;
    private final DataSource dataSource;

    // Written by the handler's thread and read by the main thread.
    public volatile MediaFormat[] mediaFormats;
    public volatile MediaFormat selectedTrackMediaFormat;
    private volatile Handler handler;
    private volatile int readSampleResult;
    private volatile Exception exception;
    private volatile CountDownLatch pendingOperationLatch;

    public Mp4ExtractorWrapper(DataSource dataSource) {
      super("Mp4ExtractorTest");
      this.dataSource = Assertions.checkNotNull(dataSource);
      pendingOperationLatch = new CountDownLatch(1);
      start();
    }

    public void prepare() throws Exception {
      // Block until the handler has been created.
      pendingOperationLatch.await();

      // Block until the extractor has been prepared.
      pendingOperationLatch = new CountDownLatch(1);
      handler.sendEmptyMessage(MSG_PREPARE);
      pendingOperationLatch.await();
      if (exception != null) {
        throw exception;
      }
    }

    public void seekTo(long timestampUs) {
      handler.obtainMessage(MSG_SEEK_TO, timestampUs).sendToTarget();
    }

    public int readSample(int trackIndex, SampleHolder sampleHolder) throws Exception {
      // Block until the extractor has completed readSample.
      pendingOperationLatch = new CountDownLatch(1);
      handler.obtainMessage(MSG_READ_SAMPLE, trackIndex, 0, sampleHolder).sendToTarget();
      pendingOperationLatch.await();
      if (exception != null) {
        throw exception;
      }
      return readSampleResult;
    }

    @SuppressLint("HandlerLeak")
    @Override
    public void run() {
      final ExtractorSampleSource source = new ExtractorSampleSource(FAKE_URI, dataSource,
          new Mp4Extractor(), 1, 2 * 1024 * 1024);
      Looper.prepare();
      handler = new Handler() {

        @Override
        public void handleMessage(Message message) {
          try {
            switch (message.what) {
              case MSG_PREPARE:
                if (!source.prepare()) {
                  sendEmptyMessage(MSG_PREPARE);
                } else {
                  // Select the video track and get its metadata.
                  mediaFormats = new MediaFormat[source.getTrackCount()];
                  MediaFormatHolder mediaFormatHolder = new MediaFormatHolder();
                  for (int track = 0; track < source.getTrackCount(); track++) {
                    source.enable(track, 0);
                    source.readData(track, 0, mediaFormatHolder, null, false);
                    MediaFormat mediaFormat = mediaFormatHolder.format;
                    mediaFormats[track] = mediaFormat;
                    if (MimeTypes.isVideo(mediaFormat.mimeType)) {
                      selectedTrackMediaFormat = mediaFormat;
                    } else {
                      source.disable(track);
                    }
                  }
                  pendingOperationLatch.countDown();
                }
                break;
              case MSG_SEEK_TO:
                long timestampUs = (Long) message.obj;
                source.seekToUs(timestampUs);
                break;
              case MSG_READ_SAMPLE:
                int trackIndex = message.arg1;
                SampleHolder sampleHolder = (SampleHolder) message.obj;
                sampleHolder.clearData();
                readSampleResult = source.readData(trackIndex, 0, null, sampleHolder, false);
                if (readSampleResult == SampleSource.NOTHING_READ) {
                  Message.obtain(message).sendToTarget();
                  return;
                }
                pendingOperationLatch.countDown();
                break;
            }
          } catch (Exception e) {
            exception = e;
            pendingOperationLatch.countDown();
          }
        }
      };

      // Unblock waiting for the handler.
      pendingOperationLatch.countDown();

      Looper.loop();
    }

  }

}
