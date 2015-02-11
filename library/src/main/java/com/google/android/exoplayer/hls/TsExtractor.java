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
package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.mp4.Mp4Util;
import com.google.android.exoplayer.text.eia608.Eia608Parser;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.annotation.SuppressLint;
import android.media.MediaExtractor;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Facilitates the extraction of data from the MPEG-2 TS container format.
 */
public final class TsExtractor {

  private static final String TAG = "TsExtractor";

  private static final int TS_PACKET_SIZE = 188;
  private static final int TS_SYNC_BYTE = 0x47; // First byte of each TS packet.
  private static final int TS_PAT_PID = 0;

  private static final int TS_STREAM_TYPE_AAC = 0x0F;
  private static final int TS_STREAM_TYPE_H264 = 0x1B;
  private static final int TS_STREAM_TYPE_ID3 = 0x15;
  private static final int TS_STREAM_TYPE_EIA608 = 0x100; // 0xFF + 1

  private static final long MAX_PTS = 0x1FFFFFFFFL;

  private final ParsableByteArray tsPacketBuffer;
  private final SparseArray<SampleQueue> sampleQueues; // Indexed by streamType
  private final SparseArray<TsPayloadReader> tsPayloadReaders; // Indexed by pid
  private final SamplePool samplePool;
  private final boolean shouldSpliceIn;
  private final long firstSampleTimestamp;
  private final ParsableBitArray tsScratch;

  // Accessed only by the consuming thread.
  private boolean spliceConfigured;

  // Accessed only by the loading thread.
  private int tsPacketBytesRead;
  private long timestampOffsetUs;
  private long lastPts;

  // Accessed by both the loading and consuming threads.
  private volatile boolean prepared;
  /* package */ volatile long largestParsedTimestampUs;

  public TsExtractor(long firstSampleTimestamp, SamplePool samplePool, boolean shouldSpliceIn) {
    this.firstSampleTimestamp = firstSampleTimestamp;
    this.samplePool = samplePool;
    this.shouldSpliceIn = shouldSpliceIn;
    tsScratch = new ParsableBitArray(new byte[3]);
    tsPacketBuffer = new ParsableByteArray(TS_PACKET_SIZE);
    sampleQueues = new SparseArray<SampleQueue>();
    tsPayloadReaders = new SparseArray<TsPayloadReader>();
    tsPayloadReaders.put(TS_PAT_PID, new PatReader());
    largestParsedTimestampUs = Long.MIN_VALUE;
    lastPts = Long.MIN_VALUE;
  }

  /**
   * Gets the number of available tracks.
   * <p>
   * This method should only be called after the extractor has been prepared.
   *
   * @return The number of available tracks.
   */
  public int getTrackCount() {
    Assertions.checkState(prepared);
    return sampleQueues.size();
  }

  /**
   * Gets the format of the specified track.
   * <p>
   * This method must only be called after the extractor has been prepared.
   *
   * @param track The track index.
   * @return The corresponding format.
   */
  public MediaFormat getFormat(int track) {
    Assertions.checkState(prepared);
    return sampleQueues.valueAt(track).getMediaFormat();
  }

  /**
   * Whether the extractor is prepared.
   *
   * @return True if the extractor is prepared. False otherwise.
   */
  public boolean isPrepared() {
    return prepared;
  }

  /**
   * Releases the extractor, recycling any pending or incomplete samples to the sample pool.
   * <p>
   * This method should not be called whilst {@link #read(DataSource)} is also being invoked.
   */
  public void release() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      sampleQueues.valueAt(i).release();
    }
  }

  /**
   * Attempts to configure a splice from this extractor to the next.
   * <p>
   * The splice is performed such that for each track the samples read from the next extractor
   * start with a keyframe, and continue from where the samples read from this extractor finish.
   * A successful splice may discard samples from either or both extractors.
   * <p>
   * Splice configuration may fail if the next extractor is not yet in a state that allows the
   * splice to be performed. Calling this method is a noop if the splice has already been
   * configured. Hence this method should be called repeatedly during the window within which a
   * splice can be performed.
   *
   * @param nextExtractor The extractor being spliced to.
   */
  public void configureSpliceTo(TsExtractor nextExtractor) {
    Assertions.checkState(prepared);
    if (spliceConfigured || !nextExtractor.shouldSpliceIn || !nextExtractor.isPrepared()) {
      // The splice is already configured, or the next extractor doesn't want to be spliced in, or
      // the next extractor isn't ready to be spliced in.
      return;
    }
    boolean spliceConfigured = true;
    for (int i = 0; i < sampleQueues.size(); i++) {
      spliceConfigured &= sampleQueues.valueAt(i).configureSpliceTo(
          nextExtractor.sampleQueues.valueAt(i));
    }
    this.spliceConfigured = spliceConfigured;
    return;
  }

  /**
   * Gets the largest timestamp of any sample parsed by the extractor.
   *
   * @return The largest timestamp, or {@link Long#MIN_VALUE} if no samples have been parsed.
   */
  public long getLargestSampleTimestamp() {
    return largestParsedTimestampUs;
  }

  /**
   * Gets the next sample for the specified track.
   *
   * @param track The track from which to read.
   * @param out A {@link SampleHolder} into which the next sample should be read.
   * @return True if a sample was read. False otherwise.
   */
  public boolean getSample(int track, SampleHolder out) {
    Assertions.checkState(prepared);
    SampleQueue sampleQueue = sampleQueues.valueAt(track);
    Sample sample = sampleQueue.poll();
    if (sample == null) {
      return false;
    }
    convert(sample, out);
    sampleQueue.recycle(sample);
    return true;
  }

  /**
   * Discards samples for the specified track up to the specified time.
   *
   * @param track The track from which samples should be discarded.
   * @param timeUs The time up to which samples should be discarded, in microseconds.
   */
  public void discardUntil(int track, long timeUs) {
    Assertions.checkState(prepared);
    sampleQueues.valueAt(track).discardUntil(timeUs);
  }

  /**
   * Whether samples are available for reading from {@link #getSample(int, SampleHolder)} for the
   * specified track.
   *
   * @return True if samples are available for reading from {@link #getSample(int, SampleHolder)}
   *     for the specified track. False otherwise.
   */
  public boolean hasSamples(int track) {
    Assertions.checkState(prepared);
    return sampleQueues.valueAt(track).peek() != null;
  }

  private boolean checkPrepared() {
    int pesPayloadReaderCount = sampleQueues.size();
    if (pesPayloadReaderCount == 0) {
      return false;
    }
    for (int i = 0; i < pesPayloadReaderCount; i++) {
      if (!sampleQueues.valueAt(i).hasMediaFormat()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Reads up to a single TS packet.
   *
   * @param dataSource The {@link DataSource} from which to read.
   * @throws IOException If an error occurred reading from the source.
   * @return The number of bytes read from the source.
   */
  public int read(DataSource dataSource) throws IOException {
    int bytesRead = dataSource.read(tsPacketBuffer.data, tsPacketBytesRead,
        TS_PACKET_SIZE - tsPacketBytesRead);
    if (bytesRead == -1) {
      return -1;
    }

    tsPacketBytesRead += bytesRead;
    if (tsPacketBytesRead < TS_PACKET_SIZE) {
      // We haven't read the whole packet yet.
      return bytesRead;
    }

    // Reset before reading the packet.
    tsPacketBytesRead = 0;
    tsPacketBuffer.setPosition(0);
    tsPacketBuffer.setLimit(TS_PACKET_SIZE);

    int syncByte = tsPacketBuffer.readUnsignedByte();
    if (syncByte != TS_SYNC_BYTE) {
      return bytesRead;
    }

    tsPacketBuffer.readBytes(tsScratch, 3);
    tsScratch.skipBits(1); // transport_error_indicator
    boolean payloadUnitStartIndicator = tsScratch.readBit();
    tsScratch.skipBits(1); // transport_priority
    int pid = tsScratch.readBits(13);
    tsScratch.skipBits(2); // transport_scrambling_control
    boolean adaptationFieldExists = tsScratch.readBit();
    boolean payloadExists = tsScratch.readBit();
    // Last 4 bits of scratch are skipped: continuity_counter

    // Skip the adaptation field.
    if (adaptationFieldExists) {
      int adaptationFieldLength = tsPacketBuffer.readUnsignedByte();
      tsPacketBuffer.skip(adaptationFieldLength);
    }

    // Read the payload.
    if (payloadExists) {
      TsPayloadReader payloadReader = tsPayloadReaders.get(pid);
      if (payloadReader != null) {
        payloadReader.consume(tsPacketBuffer, payloadUnitStartIndicator);
      }
    }

    if (!prepared) {
      prepared = checkPrepared();
    }

    return bytesRead;
  }

  @SuppressLint("InlinedApi")
  private void convert(Sample in, SampleHolder out) {
    if (out.data == null || out.data.capacity() < in.size) {
      out.replaceBuffer(in.size);
    }
    if (out.data != null) {
      out.data.put(in.data, 0, in.size);
    }
    out.size = in.size;
    out.flags = in.isKeyframe ? MediaExtractor.SAMPLE_FLAG_SYNC : 0;
    out.timeUs = in.timeUs;
  }

  /**
   * Adjusts a PTS value to the corresponding time in microseconds, accounting for PTS wraparound.
   *
   * @param pts The raw PTS value.
   * @return The corresponding time in microseconds.
   */
  /* package */ long ptsToTimeUs(long pts) {
    if (lastPts != Long.MIN_VALUE) {
      // The wrap count for the current PTS may be closestWrapCount or (closestWrapCount - 1),
      // and we need to snap to the one closest to lastPts.
      long closestWrapCount = (lastPts + (MAX_PTS / 2)) / MAX_PTS;
      long ptsWrapBelow = pts + (MAX_PTS * (closestWrapCount - 1));
      long ptsWrapAbove = pts + (MAX_PTS * closestWrapCount);
      pts = Math.abs(ptsWrapBelow - lastPts) < Math.abs(ptsWrapAbove - lastPts)
          ? ptsWrapBelow : ptsWrapAbove;
    }
    // Calculate the corresponding timestamp.
    long timeUs = (pts * C.MICROS_PER_SECOND) / 90000;
    // If we haven't done the initial timestamp adjustment, do it now.
    if (lastPts == Long.MIN_VALUE) {
      timestampOffsetUs = firstSampleTimestamp - timeUs;
    }
    // Record the adjusted PTS to adjust for wraparound next time.
    lastPts = pts;
    return timeUs + timestampOffsetUs;
  }

  /**
   * Parses TS packet payload data.
   */
  private abstract static class TsPayloadReader {

    public abstract void consume(ParsableByteArray data, boolean payloadUnitStartIndicator);

  }

  /**
   * Parses Program Association Table data.
   */
  private class PatReader extends TsPayloadReader {

    private final ParsableBitArray patScratch;

    public PatReader() {
      patScratch = new ParsableBitArray(new byte[4]);
    }

    @Override
    public void consume(ParsableByteArray data, boolean payloadUnitStartIndicator) {
      // Skip pointer.
      if (payloadUnitStartIndicator) {
        int pointerField = data.readUnsignedByte();
        data.skip(pointerField);
      }

      data.readBytes(patScratch, 3);
      patScratch.skipBits(12); // table_id (8), section_syntax_indicator (1), '0' (1), reserved (2)
      int sectionLength = patScratch.readBits(12);
      // transport_stream_id (16), reserved (2), version_number (5), current_next_indicator (1),
      // section_number (8), last_section_number (8)
      data.skip(5);

      int programCount = (sectionLength - 9) / 4;
      for (int i = 0; i < programCount; i++) {
        data.readBytes(patScratch, 4);
        patScratch.skipBits(19); // program_number (16), reserved (3)
        int pid = patScratch.readBits(13);
        tsPayloadReaders.put(pid, new PmtReader());
      }

      // Skip CRC_32.
    }

  }

  /**
   * Parses Program Map Table.
   */
  private class PmtReader extends TsPayloadReader {

    private final ParsableBitArray pmtScratch;

    public PmtReader() {
      pmtScratch = new ParsableBitArray(new byte[5]);
    }

    @Override
    public void consume(ParsableByteArray data, boolean payloadUnitStartIndicator) {
      // Skip pointer.
      if (payloadUnitStartIndicator) {
        int pointerField = data.readUnsignedByte();
        data.skip(pointerField);
      }

      data.readBytes(pmtScratch, 3);
      pmtScratch.skipBits(12); // table_id (8), section_syntax_indicator (1), '0' (1), reserved (2)
      int sectionLength = pmtScratch.readBits(12);

      // program_number (16), reserved (2), version_number (5), current_next_indicator (1),
      // section_number (8), last_section_number (8), reserved (3), PCR_PID (13)
      // Skip the rest of the PMT header.
      data.skip(7);

      data.readBytes(pmtScratch, 2);
      pmtScratch.skipBits(4);
      int programInfoLength = pmtScratch.readBits(12);

      // Skip the descriptors.
      data.skip(programInfoLength);

      int entriesSize = sectionLength - 9 /* Size of the rest of the fields before descriptors */
          - programInfoLength - 4 /* CRC size */;
      while (entriesSize > 0) {
        data.readBytes(pmtScratch, 5);
        int streamType = pmtScratch.readBits(8);
        pmtScratch.skipBits(3); // reserved
        int elementaryPid = pmtScratch.readBits(13);
        pmtScratch.skipBits(4); // reserved
        int esInfoLength = pmtScratch.readBits(12);

        // Skip the descriptors.
        data.skip(esInfoLength);
        entriesSize -= esInfoLength + 5;

        if (sampleQueues.get(streamType) != null) {
          continue;
        }

        PesPayloadReader pesPayloadReader = null;
        switch (streamType) {
          case TS_STREAM_TYPE_AAC:
            pesPayloadReader = new AdtsReader(samplePool);
            break;
          case TS_STREAM_TYPE_H264:
            SeiReader seiReader = new SeiReader(samplePool);
            sampleQueues.put(TS_STREAM_TYPE_EIA608, seiReader);
            pesPayloadReader = new H264Reader(samplePool, seiReader);
            break;
          case TS_STREAM_TYPE_ID3:
            pesPayloadReader = new Id3Reader(samplePool);
            break;
        }

        if (pesPayloadReader != null) {
          sampleQueues.put(streamType, pesPayloadReader);
          tsPayloadReaders.put(elementaryPid, new PesReader(pesPayloadReader));
        }
      }

      // Skip CRC_32.
    }

  }

  /**
   * Parses PES packet data and extracts samples.
   */
  private class PesReader extends TsPayloadReader {

    private static final int STATE_FINDING_HEADER = 0;
    private static final int STATE_READING_HEADER = 1;
    private static final int STATE_READING_HEADER_EXTENSION = 2;
    private static final int STATE_READING_BODY = 3;

    private static final int HEADER_SIZE = 9;
    private static final int MAX_HEADER_EXTENSION_SIZE = 5;

    private final ParsableBitArray pesScratch;
    private final PesPayloadReader pesPayloadReader;

    private int state;
    private int bytesRead;
    private boolean bodyStarted;

    private boolean ptsFlag;
    private int extendedHeaderLength;

    private int payloadSize;

    private long timeUs;

    public PesReader(PesPayloadReader pesPayloadReader) {
      this.pesPayloadReader = pesPayloadReader;
      pesScratch = new ParsableBitArray(new byte[HEADER_SIZE]);
      state = STATE_FINDING_HEADER;
    }

    @Override
    public void consume(ParsableByteArray data, boolean payloadUnitStartIndicator) {
      if (payloadUnitStartIndicator) {
        switch (state) {
          case STATE_FINDING_HEADER:
          case STATE_READING_HEADER:
            // Expected.
            break;
          case STATE_READING_HEADER_EXTENSION:
            Log.w(TAG, "Unexpected start indicator reading extended header");
            break;
          case STATE_READING_BODY:
            // If payloadSize == -1 then the length of the previous packet was unspecified, and so
            // we only know that it's finished now that we've seen the start of the next one. This
            // is expected. If payloadSize != -1, then the length of the previous packet was known,
            // but we didn't receive that amount of data. This is not expected.
            if (payloadSize != -1) {
              Log.w(TAG, "Unexpected start indicator: expected " + payloadSize + " more bytes");
            }
            // Either way, if the body was started, notify the reader that it has now finished.
            if (bodyStarted) {
              pesPayloadReader.packetFinished();
            }
            break;
        }
        setState(STATE_READING_HEADER);
      }

      while (data.bytesLeft() > 0) {
        switch (state) {
          case STATE_FINDING_HEADER:
            data.skip(data.bytesLeft());
            break;
          case STATE_READING_HEADER:
            if (continueRead(data, pesScratch.getData(), HEADER_SIZE)) {
              setState(parseHeader() ? STATE_READING_HEADER_EXTENSION : STATE_FINDING_HEADER);
            }
            break;
          case STATE_READING_HEADER_EXTENSION:
            int readLength = Math.min(MAX_HEADER_EXTENSION_SIZE, extendedHeaderLength);
            // Read as much of the extended header as we're interested in, and skip the rest.
            if (continueRead(data, pesScratch.getData(), readLength)
                && continueRead(data, null, extendedHeaderLength)) {
              parseHeaderExtension();
              bodyStarted = false;
              setState(STATE_READING_BODY);
            }
            break;
          case STATE_READING_BODY:
            readLength = data.bytesLeft();
            int padding = payloadSize == -1 ? 0 : readLength - payloadSize;
            if (padding > 0) {
              readLength -= padding;
              data.setLimit(data.getPosition() + readLength);
            }
            pesPayloadReader.consume(data, timeUs, !bodyStarted);
            bodyStarted = true;
            if (payloadSize != -1) {
              payloadSize -= readLength;
              if (payloadSize == 0) {
                pesPayloadReader.packetFinished();
                setState(STATE_READING_HEADER);
              }
            }
            break;
        }
      }
    }

    private void setState(int state) {
      this.state = state;
      bytesRead = 0;
    }

    /**
     * Continues a read from the provided {@code source} into a given {@code target}. It's assumed
     * that the data should be written into {@code target} starting from an offset of zero.
     *
     * @param source The source from which to read.
     * @param target The target into which data is to be read, or {@code null} to skip.
     * @param targetLength The target length of the read.
     * @return Whether the target length has been reached.
     */
    private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
      int bytesToRead = Math.min(source.bytesLeft(), targetLength - bytesRead);
      if (bytesToRead <= 0) {
        return true;
      } else if (target == null) {
        source.skip(bytesToRead);
      } else {
        source.readBytes(target, bytesRead, bytesToRead);
      }
      bytesRead += bytesToRead;
      return bytesRead == targetLength;
    }

    private boolean parseHeader() {
      pesScratch.setPosition(0);
      int startCodePrefix = pesScratch.readBits(24);
      if (startCodePrefix != 0x000001) {
        Log.w(TAG, "Unexpected start code prefix: " + startCodePrefix);
        payloadSize = -1;
        return false;
      }

      pesScratch.skipBits(8); // stream_id.
      int packetLength = pesScratch.readBits(16);
      // First 8 bits are skipped: '10' (2), PES_scrambling_control (2), PES_priority (1),
      // data_alignment_indicator (1), copyright (1), original_or_copy (1)
      pesScratch.skipBits(8);
      ptsFlag = pesScratch.readBit();
      // DTS_flag (1), ESCR_flag (1), ES_rate_flag (1), DSM_trick_mode_flag (1),
      // additional_copy_info_flag (1), PES_CRC_flag (1), PES_extension_flag (1)
      pesScratch.skipBits(7);
      extendedHeaderLength = pesScratch.readBits(8);

      if (packetLength == 0) {
        payloadSize = -1;
      } else {
        payloadSize = packetLength + 6 /* packetLength does not include the first 6 bytes */
            - HEADER_SIZE - extendedHeaderLength;
      }
      return true;
    }

    private void parseHeaderExtension() {
      pesScratch.setPosition(0);
      timeUs = 0;
      if (ptsFlag) {
        pesScratch.skipBits(4); // '0010'
        long pts = pesScratch.readBitsLong(3) << 30;
        pesScratch.skipBits(1); // marker_bit
        pts |= pesScratch.readBitsLong(15) << 15;
        pesScratch.skipBits(1); // marker_bit
        pts |= pesScratch.readBitsLong(15);
        pesScratch.skipBits(1); // marker_bit
        timeUs = ptsToTimeUs(pts);
      }
    }

  }

  /**
   * A queue of extracted samples together with their corresponding {@link MediaFormat}.
   */
  private abstract class SampleQueue {

    @SuppressWarnings("hiding")
    private final SamplePool samplePool;

    // Accessed only by the consuming thread.
    private boolean needKeyframe;
    private long lastReadTimeUs;
    private long spliceOutTimeUs;

    // Accessed by both the loading and consuming threads.
    private volatile MediaFormat mediaFormat;

    protected SampleQueue(SamplePool samplePool) {
      this.samplePool = samplePool;
      needKeyframe = true;
      lastReadTimeUs = Long.MIN_VALUE;
      spliceOutTimeUs = Long.MIN_VALUE;
    }

    public boolean hasMediaFormat() {
      return mediaFormat != null;
    }

    public MediaFormat getMediaFormat() {
      return mediaFormat;
    }

    protected void setMediaFormat(MediaFormat mediaFormat) {
      this.mediaFormat = mediaFormat;
    }

    /**
     * Removes and returns the next sample from the queue.
     * <p>
     * The first sample returned is guaranteed to be a keyframe, since any non-keyframe samples
     * queued prior to the first keyframe are discarded.
     *
     * @return The next sample from the queue, or null if a sample isn't available.
     */
    public Sample poll() {
      Sample head = peek();
      if (head != null) {
        internalPollSample();
        needKeyframe = false;
        lastReadTimeUs = head.timeUs;
      }
      return head;
    }

    /**
     * Like {@link #poll()}, except the returned sample is not removed from the queue.
     *
     * @return The next sample from the queue, or null if a sample isn't available.
     */
    public Sample peek() {
      Sample head = internalPeekSample();
      if (needKeyframe) {
        // Peeking discard of samples until we find a keyframe or run out of available samples.
        while (head != null && !head.isKeyframe) {
          recycle(head);
          internalPollSample();
          head = internalPeekSample();
        }
      }
      if (head == null) {
        return null;
      }
      if (spliceOutTimeUs != Long.MIN_VALUE && head.timeUs >= spliceOutTimeUs) {
        // The sample is later than the time this queue is spliced out.
        recycle(head);
        internalPollSample();
        return null;
      }
      return head;
    }

    /**
     * Discards samples from the queue up to the specified time.
     *
     * @param timeUs The time up to which samples should be discarded, in microseconds.
     */
    public void discardUntil(long timeUs) {
      Sample head = peek();
      while (head != null && head.timeUs < timeUs) {
        recycle(head);
        internalPollSample();
        head = internalPeekSample();
        // We're discarding at least one sample, so any subsequent read will need to start at
        // a keyframe.
        needKeyframe = true;
      }
      lastReadTimeUs = Long.MIN_VALUE;
    }

    /**
     * Clears the queue.
     */
    public void release() {
      Sample toRecycle = internalPollSample();
      while (toRecycle != null) {
        recycle(toRecycle);
        toRecycle = internalPollSample();
      }
    }

    /**
     * Recycles a sample.
     *
     * @param sample The sample to recycle.
     */
    public void recycle(Sample sample) {
      samplePool.recycle(sample);
    }

    /**
     * Attempts to configure a splice from this queue to the next.
     *
     * @param nextQueue The queue being spliced to.
     * @return Whether the splice was configured successfully.
     */
    public boolean configureSpliceTo(SampleQueue nextQueue) {
      if (spliceOutTimeUs != Long.MIN_VALUE) {
        // We've already configured the splice.
        return true;
      }
      long firstPossibleSpliceTime;
      Sample nextSample = internalPeekSample();
      if (nextSample != null) {
        firstPossibleSpliceTime = nextSample.timeUs;
      } else {
        firstPossibleSpliceTime = lastReadTimeUs + 1;
      }
      Sample nextQueueSample = nextQueue.internalPeekSample();
      while (nextQueueSample != null
          && (nextQueueSample.timeUs < firstPossibleSpliceTime || !nextQueueSample.isKeyframe)) {
        // Discard samples from the next queue for as long as they are before the earliest possible
        // splice time, or not keyframes.
        nextQueue.internalPollSample();
        nextQueueSample = nextQueue.internalPeekSample();
      }
      if (nextQueueSample != null) {
        // We've found a keyframe in the next queue that can serve as the splice point. Set the
        // splice point now.
        spliceOutTimeUs = nextQueueSample.timeUs;
        return true;
      }
      return false;
    }

    /**
     * Obtains a Sample object to use.
     *
     * @param type The type of the sample.
     * @return The sample.
     */
    protected Sample getSample(int type) {
      return samplePool.get(type);
    }

    /**
     * Creates a new Sample and adds it to the queue.
     *
     * @param type The type of the sample.
     * @param buffer The buffer to read sample data.
     * @param sampleSize The size of the sample data.
     * @param sampleTimeUs The sample time stamp.
     * @param isKeyframe True if the sample is a keyframe. False otherwise.
     */
    protected void addSample(int type, ParsableByteArray buffer, int sampleSize, long sampleTimeUs,
        boolean isKeyframe) {
      Sample sample = getSample(type);
      addToSample(sample, buffer, sampleSize);
      sample.isKeyframe = isKeyframe;
      sample.timeUs = sampleTimeUs;
      addSample(sample);
    }

    protected void addSample(Sample sample) {
      largestParsedTimestampUs = Math.max(largestParsedTimestampUs, sample.timeUs);
      internalQueueSample(sample);
    }

    protected void addToSample(Sample sample, ParsableByteArray buffer, int size) {
      if (sample.data.length - sample.size < size) {
        sample.expand(size - sample.data.length + sample.size);
      }
      buffer.readBytes(sample.data, sample.size, size);
      sample.size += size;
    }

    protected abstract Sample internalPeekSample();
    protected abstract Sample internalPollSample();
    protected abstract void internalQueueSample(Sample sample);

  }

  /**
   * Extracts individual samples from continuous byte stream, preserving original order.
   */
  private abstract class PesPayloadReader extends SampleQueue {

    private final ConcurrentLinkedQueue<Sample> internalQueue;

    protected PesPayloadReader(SamplePool samplePool) {
      super(samplePool);
      internalQueue = new ConcurrentLinkedQueue<Sample>();
    }

    @Override
    protected final Sample internalPeekSample() {
      return internalQueue.peek();
    }

    @Override
    protected final Sample internalPollSample() {
      return internalQueue.poll();
    }

    @Override
    protected final void internalQueueSample(Sample sample) {
      internalQueue.add(sample);
    }

    /**
     * Consumes (possibly partial) payload data.
     *
     * @param data The payload data to consume.
     * @param pesTimeUs The timestamp associated with the payload.
     * @param startOfPacket True if this is the first time this method is being called for the
     *     current packet. False otherwise.
     */
    public abstract void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket);

    /**
     * Invoked once all of the payload data for a packet has been passed to
     * {@link #consume(ParsableByteArray, long, boolean)}. The next call to
     * {@link #consume(ParsableByteArray, long, boolean)} will have {@code startOfPacket == true}.
     */
    public abstract void packetFinished();

  }

  /**
   * Parses a continuous H264 byte stream and extracts individual frames.
   */
  private class H264Reader extends PesPayloadReader {

    private static final int NAL_UNIT_TYPE_IDR = 5;
    private static final int NAL_UNIT_TYPE_SPS = 7;
    private static final int NAL_UNIT_TYPE_PPS = 8;
    private static final int NAL_UNIT_TYPE_AUD = 9;

    private final SeiReader seiReader;

    private Sample currentSample;

    public H264Reader(SamplePool samplePool, SeiReader seiReader) {
      super(samplePool);
      this.seiReader = seiReader;
    }

    @Override
    public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
      while (data.bytesLeft() > 0) {
        if (readToNextAudUnit(data, pesTimeUs)) {
          currentSample.isKeyframe = currentSample.size
              > Mp4Util.findNalUnit(currentSample.data, 0, currentSample.size, NAL_UNIT_TYPE_IDR);
          if (!hasMediaFormat() && currentSample.isKeyframe) {
            parseMediaFormat(currentSample);
          }
          seiReader.read(currentSample.data, currentSample.size, currentSample.timeUs);
          addSample(currentSample);
          currentSample = null;
        }
      }
    }

    @Override
    public void packetFinished() {
      // Do nothing.
    }

    @Override
    public void release() {
      super.release();
      if (currentSample != null) {
        recycle(currentSample);
        currentSample = null;
      }
    }

    /**
     * Reads data up to (but not including) the start of the next AUD unit.
     *
     * @param data The data to consume.
     * @param pesTimeUs The corresponding time.
     * @return True if the current sample is now complete. False otherwise.
     */
    private boolean readToNextAudUnit(ParsableByteArray data, long pesTimeUs) {
      int pesOffset = data.getPosition();
      int pesLimit = data.length();

      // TODO: We probably need to handle the case where the AUD start code was split across the
      // previous and current data buffers.
      int audOffset = Mp4Util.findNalUnit(data.data, pesOffset, pesLimit, NAL_UNIT_TYPE_AUD);
      int bytesToNextAud = audOffset - pesOffset;
      if (bytesToNextAud == 0) {
        if (currentSample == null) {
          currentSample = getSample(Sample.TYPE_VIDEO);
          currentSample.timeUs = pesTimeUs;
          addToSample(currentSample, data, 4);
          return false;
        } else {
          return true;
        }
      } else if (currentSample != null) {
        addToSample(currentSample, data, bytesToNextAud);
        return data.bytesLeft() > 0;
      } else {
        data.skip(bytesToNextAud);
        return false;
      }
    }

    private void parseMediaFormat(Sample sample) {
      byte[] sampleData = sample.data;
      int sampleSize = sample.size;
      // Locate the SPS and PPS units.
      int spsOffset = Mp4Util.findNalUnit(sampleData, 0, sampleSize, NAL_UNIT_TYPE_SPS);
      int ppsOffset = Mp4Util.findNalUnit(sampleData, 0, sampleSize, NAL_UNIT_TYPE_PPS);
      if (spsOffset == sampleSize || ppsOffset == sampleSize) {
        return;
      }
      // Determine the length of the units, and copy them to build the initialization data.
      int spsLength = Mp4Util.findNalUnit(sampleData, spsOffset + 3, sampleSize) - spsOffset;
      int ppsLength = Mp4Util.findNalUnit(sampleData, ppsOffset + 3, sampleSize) - ppsOffset;
      byte[] spsData = new byte[spsLength];
      byte[] ppsData = new byte[ppsLength];
      System.arraycopy(sampleData, spsOffset, spsData, 0, spsLength);
      System.arraycopy(sampleData, ppsOffset, ppsData, 0, ppsLength);
      List<byte[]> initializationData = new ArrayList<byte[]>();
      initializationData.add(spsData);
      initializationData.add(ppsData);

      // Unescape and then parse the SPS unit.
      byte[] unescapedSps = unescapeStream(spsData, 0, spsLength);
      ParsableBitArray bitArray = new ParsableBitArray(unescapedSps);
      bitArray.skipBits(32); // NAL header
      int profileIdc = bitArray.readBits(8);
      bitArray.skipBits(16); // constraint bits (6), reserved (2) and level_idc (8)
      bitArray.readUnsignedExpGolombCodedInt(); // seq_parameter_set_id

      int chromaFormatIdc = 1; // Default is 4:2:0
      if (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 244
          || profileIdc == 44 || profileIdc == 83 || profileIdc == 86 || profileIdc == 118
          || profileIdc == 128 || profileIdc == 138) {
        chromaFormatIdc = bitArray.readUnsignedExpGolombCodedInt();
        if (chromaFormatIdc == 3) {
          bitArray.skipBits(1); // separate_colour_plane_flag
        }
        bitArray.readUnsignedExpGolombCodedInt(); // bit_depth_luma_minus8
        bitArray.readUnsignedExpGolombCodedInt(); // bit_depth_chroma_minus8
        bitArray.skipBits(1); // qpprime_y_zero_transform_bypass_flag
        boolean seqScalingMatrixPresentFlag = bitArray.readBit();
        if (seqScalingMatrixPresentFlag) {
          int limit = (chromaFormatIdc != 3) ? 8 : 12;
          for (int i = 0; i < limit; i++) {
            boolean seqScalingListPresentFlag = bitArray.readBit();
            if (seqScalingListPresentFlag) {
              skipScalingList(bitArray, i < 6 ? 16 : 64);
            }
          }
        }
      }

      bitArray.readUnsignedExpGolombCodedInt(); // log2_max_frame_num_minus4
      long picOrderCntType = bitArray.readUnsignedExpGolombCodedInt();
      if (picOrderCntType == 0) {
        bitArray.readUnsignedExpGolombCodedInt(); // log2_max_pic_order_cnt_lsb_minus4
      } else if (picOrderCntType == 1) {
        bitArray.skipBits(1); // delta_pic_order_always_zero_flag
        bitArray.readSignedExpGolombCodedInt(); // offset_for_non_ref_pic
        bitArray.readSignedExpGolombCodedInt(); // offset_for_top_to_bottom_field
        long numRefFramesInPicOrderCntCycle = bitArray.readUnsignedExpGolombCodedInt();
        for (int i = 0; i < numRefFramesInPicOrderCntCycle; i++) {
          bitArray.readUnsignedExpGolombCodedInt(); // offset_for_ref_frame[i]
        }
      }
      bitArray.readUnsignedExpGolombCodedInt(); // max_num_ref_frames
      bitArray.skipBits(1); // gaps_in_frame_num_value_allowed_flag

      int picWidthInMbs = bitArray.readUnsignedExpGolombCodedInt() + 1;
      int picHeightInMapUnits = bitArray.readUnsignedExpGolombCodedInt() + 1;
      boolean frameMbsOnlyFlag = bitArray.readBit();
      int frameHeightInMbs = (2 - (frameMbsOnlyFlag ? 1 : 0)) * picHeightInMapUnits;
      if (!frameMbsOnlyFlag) {
        bitArray.skipBits(1); // mb_adaptive_frame_field_flag
      }

      bitArray.skipBits(1); // direct_8x8_inference_flag
      int frameWidth = picWidthInMbs * 16;
      int frameHeight = frameHeightInMbs * 16;
      boolean frameCroppingFlag = bitArray.readBit();
      if (frameCroppingFlag) {
        int frameCropLeftOffset = bitArray.readUnsignedExpGolombCodedInt();
        int frameCropRightOffset = bitArray.readUnsignedExpGolombCodedInt();
        int frameCropTopOffset = bitArray.readUnsignedExpGolombCodedInt();
        int frameCropBottomOffset = bitArray.readUnsignedExpGolombCodedInt();
        int cropUnitX, cropUnitY;
        if (chromaFormatIdc == 0) {
          cropUnitX = 1;
          cropUnitY = 2 - (frameMbsOnlyFlag ? 1 : 0);
        } else {
          int subWidthC = (chromaFormatIdc == 3) ? 1 : 2;
          int subHeightC = (chromaFormatIdc == 1) ? 2 : 1;
          cropUnitX = subWidthC;
          cropUnitY = subHeightC * (2 - (frameMbsOnlyFlag ? 1 : 0));
        }
        frameWidth -= (frameCropLeftOffset + frameCropRightOffset) * cropUnitX;
        frameHeight -= (frameCropTopOffset + frameCropBottomOffset) * cropUnitY;
      }

      // Set the format.
      setMediaFormat(MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
          frameWidth, frameHeight, initializationData));
    }

    private void skipScalingList(ParsableBitArray bitArray, int size) {
      int lastScale = 8;
      int nextScale = 8;
      for (int i = 0; i < size; i++) {
        if (nextScale != 0) {
          int deltaScale = bitArray.readSignedExpGolombCodedInt();
          nextScale = (lastScale + deltaScale + 256) % 256;
        }
        lastScale = (nextScale == 0) ? lastScale : nextScale;
      }
    }

    /**
     * Replaces occurrences of [0, 0, 3] with [0, 0].
     * <p>
     * See ISO/IEC 14496-10:2005(E) page 36 for more information.
     */
    private byte[] unescapeStream(byte[] data, int offset, int limit) {
      int position = offset;
      List<Integer> escapePositions = new ArrayList<Integer>();
      while (position < limit) {
        position = findNextUnescapeIndex(data, position, limit);
        if (position < limit) {
          escapePositions.add(position);
          position += 3;
        }
      }

      int escapeCount = escapePositions.size();
      int escapedPosition = offset; // The position being read from.
      int unescapedPosition = 0; // The position being written to.
      byte[] unescapedData = new byte[limit - offset - escapeCount];
      for (int i = 0; i < escapeCount; i++) {
        int nextEscapePosition = escapePositions.get(i);
        int copyLength = nextEscapePosition - escapedPosition;
        System.arraycopy(data, escapedPosition, unescapedData, unescapedPosition, copyLength);
        escapedPosition += copyLength + 3;
        unescapedPosition += copyLength + 2;
      }

      int remainingLength = unescapedData.length - unescapedPosition;
      System.arraycopy(data, escapedPosition, unescapedData, unescapedPosition, remainingLength);
      return unescapedData;
    }

    private int findNextUnescapeIndex(byte[] bytes, int offset, int limit) {
      for (int i = offset; i < limit - 2; i++) {
        if (bytes[i] == 0x00 && bytes[i + 1] == 0x00 && bytes[i + 2] == 0x03) {
          return i;
        }
      }
      return limit;
    }

  }

  /**
   * Parses a SEI data from H.264 frames and extracts samples with closed captions data.
   *
   * TODO: Technically, we shouldn't allow a sample to be read from the queue until we're sure that
   * a sample with an earlier timestamp won't be added to it.
   */
  private class SeiReader extends SampleQueue implements Comparator<Sample> {

    // SEI data, used for Closed Captions.
    private static final int NAL_UNIT_TYPE_SEI = 6;

    private final ParsableByteArray seiBuffer;
    private final TreeSet<Sample> internalQueue;

    public SeiReader(SamplePool samplePool) {
      super(samplePool);
      setMediaFormat(MediaFormat.createEia608Format());
      seiBuffer = new ParsableByteArray();
      internalQueue = new TreeSet<Sample>(this);
    }

    @SuppressLint("InlinedApi")
    public void read(byte[] data, int length, long pesTimeUs) {
      seiBuffer.reset(data, length);
      while (seiBuffer.bytesLeft() > 0) {
        int currentOffset = seiBuffer.getPosition();
        int seiOffset = Mp4Util.findNalUnit(data, currentOffset, length, NAL_UNIT_TYPE_SEI);
        if (seiOffset == length) {
          return;
        }
        seiBuffer.skip(seiOffset + 4 - currentOffset);
        int ccDataSize = Eia608Parser.parseHeader(seiBuffer);
        if (ccDataSize > 0) {
          addSample(Sample.TYPE_MISC, seiBuffer, ccDataSize, pesTimeUs, true);
        }
      }
    }

    @Override
    public int compare(Sample first, Sample second) {
      // Note - We don't expect samples to have identical timestamps.
      return first.timeUs <= second.timeUs ? -1 : 1;
    }

    @Override
    protected synchronized Sample internalPeekSample() {
      return internalQueue.isEmpty() ? null : internalQueue.first();
    }

    @Override
    protected synchronized Sample internalPollSample() {
      return internalQueue.pollFirst();
    }

    @Override
    protected synchronized void internalQueueSample(Sample sample) {
      internalQueue.add(sample);
    }

  }

  /**
   * Parses a continuous ADTS byte stream and extracts individual frames.
   */
  private class AdtsReader extends PesPayloadReader {

    private static final int STATE_FINDING_SYNC = 0;
    private static final int STATE_READING_HEADER = 1;
    private static final int STATE_READING_SAMPLE = 2;

    private static final int HEADER_SIZE = 5;
    private static final int CRC_SIZE = 2;

    private final ParsableBitArray adtsScratch;

    private int state;
    private int bytesRead;

    // Used to find the header.
    private boolean lastByteWasOxFF;
    private boolean hasCrc;

    // Parsed from the header.
    private long frameDurationUs;
    private int sampleSize;

    // Used when reading the samples.
    private long timeUs;
    private Sample currentSample;

    public AdtsReader(SamplePool samplePool) {
      super(samplePool);
      adtsScratch = new ParsableBitArray(new byte[HEADER_SIZE + CRC_SIZE]);
      state = STATE_FINDING_SYNC;
    }

    @Override
    public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
      if (startOfPacket) {
        timeUs = pesTimeUs;
      }
      while (data.bytesLeft() > 0) {
        switch (state) {
          case STATE_FINDING_SYNC:
            if (skipToNextSync(data)) {
              bytesRead = 0;
              state = STATE_READING_HEADER;
            }
            break;
          case STATE_READING_HEADER:
            int targetLength = hasCrc ? HEADER_SIZE + CRC_SIZE : HEADER_SIZE;
            if (continueRead(data, adtsScratch.getData(), targetLength)) {
              parseHeader();
              currentSample = getSample(Sample.TYPE_AUDIO);
              currentSample.timeUs = timeUs;
              currentSample.isKeyframe = true;
              bytesRead = 0;
              state = STATE_READING_SAMPLE;
            }
            break;
          case STATE_READING_SAMPLE:
            int bytesToRead = Math.min(data.bytesLeft(), sampleSize - bytesRead);
            addToSample(currentSample, data, bytesToRead);
            bytesRead += bytesToRead;
            if (bytesRead == sampleSize) {
              addSample(currentSample);
              currentSample = null;
              timeUs += frameDurationUs;
              bytesRead = 0;
              state = STATE_FINDING_SYNC;
            }
            break;
        }
      }
    }

    @Override
    public void packetFinished() {
      // Do nothing.
    }

    @Override
    public void release() {
      super.release();
      if (currentSample != null) {
        recycle(currentSample);
        currentSample = null;
      }
    }

    /**
     * Continues a read from the provided {@code source} into a given {@code target}. It's assumed
     * that the data should be written into {@code target} starting from an offset of zero.
     *
     * @param source The source from which to read.
     * @param target The target into which data is to be read.
     * @param targetLength The target length of the read.
     * @return Whether the target length was reached.
     */
    private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
      int bytesToRead = Math.min(source.bytesLeft(), targetLength - bytesRead);
      source.readBytes(target, bytesRead, bytesToRead);
      bytesRead += bytesToRead;
      return bytesRead == targetLength;
    }

    /**
     * Locates the next sync word, advancing the position to the byte that immediately follows it.
     * If a sync word was not located, the position is advanced to the limit.
     *
     * @param pesBuffer The buffer whose position should be advanced.
     * @return True if a sync word position was found. False otherwise.
     */
    private boolean skipToNextSync(ParsableByteArray pesBuffer) {
      byte[] adtsData = pesBuffer.data;
      int startOffset = pesBuffer.getPosition();
      int endOffset = pesBuffer.length();
      for (int i = startOffset; i < endOffset; i++) {
        boolean byteIsOxFF = (adtsData[i] & 0xFF) == 0xFF;
        boolean found = lastByteWasOxFF && !byteIsOxFF && (adtsData[i] & 0xF0) == 0xF0;
        lastByteWasOxFF = byteIsOxFF;
        if (found) {
          hasCrc = (adtsData[i] & 0x1) == 0;
          pesBuffer.setPosition(i + 1);
          return true;
        }
      }
      pesBuffer.setPosition(endOffset);
      return false;
    }

    /**
     * Parses the sample header.
     */
    private void parseHeader() {
      adtsScratch.setPosition(0);

      if (!hasMediaFormat()) {
        int audioObjectType = adtsScratch.readBits(2) + 1;
        int sampleRateIndex = adtsScratch.readBits(4);
        adtsScratch.skipBits(1);
        int channelConfig = adtsScratch.readBits(3);

        byte[] audioSpecificConfig = CodecSpecificDataUtil.buildAudioSpecificConfig(
            audioObjectType, sampleRateIndex, channelConfig);
        Pair<Integer, Integer> audioParams = CodecSpecificDataUtil.parseAudioSpecificConfig(
            audioSpecificConfig);

        MediaFormat mediaFormat = MediaFormat.createAudioFormat(MimeTypes.AUDIO_AAC,
            MediaFormat.NO_VALUE, audioParams.second, audioParams.first,
            Collections.singletonList(audioSpecificConfig));
        frameDurationUs = (C.MICROS_PER_SECOND * 1024L) / mediaFormat.sampleRate;
        setMediaFormat(mediaFormat);
      } else {
        adtsScratch.skipBits(10);
      }

      adtsScratch.skipBits(4);
      sampleSize = adtsScratch.readBits(13) - 2 /* the sync word */ - HEADER_SIZE;
      if (hasCrc) {
        sampleSize -= CRC_SIZE;
      }
    }

  }

  /**
   * Parses ID3 data and extracts individual text information frames.
   */
  private class Id3Reader extends PesPayloadReader {

    private Sample currentSample;

    public Id3Reader(SamplePool samplePool) {
      super(samplePool);
      setMediaFormat(MediaFormat.createId3Format());
    }

    @SuppressLint("InlinedApi")
    @Override
    public void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket) {
      if (startOfPacket) {
        currentSample = getSample(Sample.TYPE_MISC);
        currentSample.timeUs = pesTimeUs;
        currentSample.isKeyframe = true;
      }
      if (currentSample != null) {
        addToSample(currentSample, data, data.bytesLeft());
      }
    }

    @Override
    public void packetFinished() {
      addSample(currentSample);
      currentSample = null;
    }

    @Override
    public void release() {
      super.release();
      if (currentSample != null) {
        recycle(currentSample);
        currentSample = null;
      }
    }

  }

  /**
   * A pool from which the extractor can obtain sample objects for internal use.
   *
   * TODO: Over time the average size of a sample in the video pool will become larger, as the
   * proportion of samples in the pool that have at some point held a keyframe grows. Currently
   * this leads to inefficient memory usage, since samples large enough to hold keyframes end up
   * being used to hold non-keyframes. We need to fix this.
   */
  public static class SamplePool {

    private static final int[] DEFAULT_SAMPLE_SIZES;
    static {
      DEFAULT_SAMPLE_SIZES = new int[Sample.TYPE_COUNT];
      DEFAULT_SAMPLE_SIZES[Sample.TYPE_VIDEO] = 10 * 1024;
      DEFAULT_SAMPLE_SIZES[Sample.TYPE_AUDIO] = 512;
      DEFAULT_SAMPLE_SIZES[Sample.TYPE_MISC] = 512;
    }

    private final Sample[] pools;

    public SamplePool() {
      pools = new Sample[Sample.TYPE_COUNT];
    }

    /* package */ synchronized Sample get(int type) {
      if (pools[type] == null) {
        return new Sample(type, DEFAULT_SAMPLE_SIZES[type]);
      }
      Sample sample = pools[type];
      pools[type] = sample.nextInPool;
      sample.nextInPool = null;
      return sample;
    }

    /* package */ synchronized void recycle(Sample sample) {
      sample.reset();
      sample.nextInPool = pools[sample.type];
      pools[sample.type] = sample;
    }

  }

  /**
   * An internal variant of {@link SampleHolder} for internal pooling and buffering.
   */
  private static class Sample {

    public static final int TYPE_VIDEO = 0;
    public static final int TYPE_AUDIO = 1;
    public static final int TYPE_MISC = 2;
    public static final int TYPE_COUNT = 3;

    public final int type;
    public Sample nextInPool;

    public byte[] data;
    public boolean isKeyframe;
    public int size;
    public long timeUs;

    public Sample(int type, int length) {
      this.type = type;
      data = new byte[length];
    }

    public void expand(int length) {
      byte[] newBuffer = new byte[data.length + length];
      System.arraycopy(data, 0, newBuffer, 0, size);
      data = newBuffer;
    }

    public void reset() {
      isKeyframe = false;
      size = 0;
      timeUs = 0;
    }

  }

}
