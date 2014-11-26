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

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.metadata.Eia608Parser;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.BitArray;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;
import com.google.android.exoplayer.util.MimeTypes;

import android.annotation.SuppressLint;
import android.media.MediaExtractor;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

  private final BitArray tsPacketBuffer;
  private final SparseArray<SampleQueue> sampleQueues; // Indexed by streamType
  private final SparseArray<TsPayloadReader> tsPayloadReaders; // Indexed by pid
  private final SamplePool samplePool;
  private final boolean shouldSpliceIn;
  /* package */ final long firstSampleTimestamp;

  // Accessed only by the consuming thread.
  private boolean spliceConfigured;

  // Accessed only by the loading thread.
  /* package */ boolean pendingFirstSampleTimestampAdjustment;
  /* package */ long sampleTimestampOffsetUs;

  // Accessed by both the loading and consuming threads.
  private volatile boolean prepared;
  /* package */ volatile long largestParsedTimestampUs;

  public TsExtractor(long firstSampleTimestamp, SamplePool samplePool, boolean shouldSpliceIn) {
    this.firstSampleTimestamp = firstSampleTimestamp;
    this.samplePool = samplePool;
    this.shouldSpliceIn = shouldSpliceIn;
    pendingFirstSampleTimestampAdjustment = true;
    tsPacketBuffer = new BitArray();
    sampleQueues = new SparseArray<SampleQueue>();
    tsPayloadReaders = new SparseArray<TsPayloadReader>();
    tsPayloadReaders.put(TS_PAT_PID, new PatReader());
    largestParsedTimestampUs = Long.MIN_VALUE;
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
   * Whether samples are available for reading from {@link #getSample(int, SampleHolder)} for any
   * track.
   *
   * @return True if samples are available for reading from {@link #getSample(int, SampleHolder)}
   *     for any track. False otherwise.
   */
  public boolean hasSamples() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      if (hasSamples(i)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether samples are available for reading from {@link #getSample(int, SampleHolder)} for the
   * specified track.
   *
   * @return True if samples are available for reading from {@link #getSample(int, SampleHolder)}
   *     for the specified track. False otherwise.
   */
  public boolean hasSamples(int track) {
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
    int read = tsPacketBuffer.append(dataSource, TS_PACKET_SIZE - tsPacketBuffer.bytesLeft());
    if (read == -1) {
      return -1;
    }

    if (tsPacketBuffer.bytesLeft() != TS_PACKET_SIZE) {
      return read;
    }

    // Parse TS header.
    // Check sync byte.
    int syncByte = tsPacketBuffer.readUnsignedByte();
    if (syncByte != TS_SYNC_BYTE) {
      return read;
    }

    // Skip transportErrorIndicator.
    tsPacketBuffer.skipBits(1);
    boolean payloadUnitStartIndicator = tsPacketBuffer.readBit();
    // Skip transportPriority.
    tsPacketBuffer.skipBits(1);
    int pid = tsPacketBuffer.readBits(13);
    // Skip transport_scrambling_control.
    tsPacketBuffer.skipBits(2);
    boolean adaptationFieldExists = tsPacketBuffer.readBit();
    boolean payloadExists = tsPacketBuffer.readBit();
    // Skip continuityCounter.
    tsPacketBuffer.skipBits(4);

    // Read the adaptation field.
    if (adaptationFieldExists) {
      int adaptationFieldLength = tsPacketBuffer.readBits(8);
      tsPacketBuffer.skipBytes(adaptationFieldLength);
    }

    // Read Payload.
    if (payloadExists) {
      TsPayloadReader payloadReader = tsPayloadReaders.get(pid);
      if (payloadReader != null) {
        payloadReader.read(tsPacketBuffer, payloadUnitStartIndicator);
      }
    }

    if (!prepared) {
      prepared = checkPrepared();
    }

    tsPacketBuffer.reset();
    return read;
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
   * Parses payload data.
   */
  private abstract static class TsPayloadReader {

    public abstract void read(BitArray tsBuffer, boolean payloadUnitStartIndicator);

  }

  /**
   * Parses Program Association Table data.
   */
  private class PatReader extends TsPayloadReader {

    @Override
    public void read(BitArray tsBuffer, boolean payloadUnitStartIndicator) {
      // Skip pointer.
      if (payloadUnitStartIndicator) {
        int pointerField = tsBuffer.readBits(8);
        tsBuffer.skipBytes(pointerField);
      }

      // Skip PAT header.
      tsBuffer.skipBits(64); // 8+1+1+2+12+16+2+5+1+8+8

      // Only read the first program and take it.

      // Skip program_number.
      tsBuffer.skipBits(16 + 3);
      int pid = tsBuffer.readBits(13);

      // Pick the first program.
      if (tsPayloadReaders.get(pid) == null) {
        tsPayloadReaders.put(pid, new PmtReader());
      }

      // Skip other programs if exist.
      // Skip CRC_32.
    }

  }

  /**
   * Parses Program Map Table.
   */
  private class PmtReader extends TsPayloadReader {

    @Override
    public void read(BitArray tsBuffer, boolean payloadUnitStartIndicator) {
      // Skip pointer.
      if (payloadUnitStartIndicator) {
        int pointerField = tsBuffer.readBits(8);
        tsBuffer.skipBytes(pointerField);
      }

      // Skip table_id, section_syntax_indicator, etc.
      tsBuffer.skipBits(12); // 8+1+1+2
      int sectionLength = tsBuffer.readBits(12);
      // Skip the rest of the PMT header.
      tsBuffer.skipBits(60); // 16+2+5+1+8+8+3+13+4

      int programInfoLength = tsBuffer.readBits(12);
      // Skip the descriptors.
      tsBuffer.skipBytes(programInfoLength);

      int entriesSize = sectionLength - 9 /* size of the rest of the fields before descriptors */
          - programInfoLength - 4 /* CRC size */;
      while (entriesSize > 0) {
        int streamType = tsBuffer.readBits(8);
        tsBuffer.skipBits(3);
        int elementaryPid = tsBuffer.readBits(13);
        tsBuffer.skipBits(4);

        int esInfoLength = tsBuffer.readBits(12);
        // Skip the descriptors.
        tsBuffer.skipBytes(esInfoLength);
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

    // Reusable buffer for incomplete PES data.
    private final BitArray pesBuffer;
    // Parses PES payload and extracts individual samples.
    private final PesPayloadReader pesPayloadReader;

    private int packetLength;

    public PesReader(PesPayloadReader pesPayloadReader) {
      this.pesPayloadReader = pesPayloadReader;
      this.packetLength = -1;
      pesBuffer = new BitArray();
    }

    @Override
    public void read(BitArray tsBuffer, boolean payloadUnitStartIndicator) {
      if (payloadUnitStartIndicator && !pesBuffer.isEmpty()) {
        // We've encountered the start of the next packet, but haven't yet read the body. Read it.
        // Note that this should only happen if the packet length was unspecified.
        Assertions.checkState(packetLength == 0);
        readPacketBody();
      }

      pesBuffer.append(tsBuffer, tsBuffer.bytesLeft());

      if (packetLength == -1 && pesBuffer.bytesLeft() >= 6) {
        // We haven't read the start of the packet, but have enough data to do so.
        readPacketStart();
      }
      if (packetLength > 0 && pesBuffer.bytesLeft() >= packetLength) {
        // The packet length was specified and we now have the whole packet. Read it.
        readPacketBody();
      }
    }

    private void readPacketStart() {
      int startCodePrefix = pesBuffer.readBits(24);
      if (startCodePrefix != 0x000001) {
        // Error.
      }
      // TODO: Read and use stream_id.
      // Skip stream_id.
      pesBuffer.skipBits(8);
      packetLength = pesBuffer.readBits(16);
    }

    private void readPacketBody() {
      // Skip some fields/flags.
      // TODO: might need to use data_alignment_indicator.
      pesBuffer.skipBits(8); // 2+2+1+1+1+1
      boolean ptsFlag = pesBuffer.readBit();
      // Skip DTS flag.
      pesBuffer.skipBits(1);
      // Skip some fields/flags.
      pesBuffer.skipBits(6); // 1+1+1+1+1+1

      int headerDataLength = pesBuffer.readBits(8);
      if (headerDataLength == 0) {
        headerDataLength = pesBuffer.bytesLeft();
      }

      long timeUs = 0;
      if (ptsFlag) {
        // Skip prefix.
        pesBuffer.skipBits(4);
        long pts = pesBuffer.readBitsLong(3) << 30;
        pesBuffer.skipBits(1);
        pts |= pesBuffer.readBitsLong(15) << 15;
        pesBuffer.skipBits(1);
        pts |= pesBuffer.readBitsLong(15);
        pesBuffer.skipBits(1);
        timeUs = pts * 1000000 / 90000;
        // Skip the rest of the header.
        pesBuffer.skipBytes(headerDataLength - 5);
      } else {
        // Skip the rest of the header.
        pesBuffer.skipBytes(headerDataLength);
      }

      int payloadSize;
      if (packetLength == 0) {
        // If pesPacketLength is not specified read all available data.
        payloadSize = pesBuffer.bytesLeft();
      } else {
        payloadSize = packetLength - headerDataLength - 3;
      }

      pesPayloadReader.read(pesBuffer, payloadSize, timeUs);
      pesBuffer.reset();
      packetLength = -1;
    }

  }

  /**
   * A queue of extracted samples together with their corresponding {@link MediaFormat}.
   */
  private abstract class SampleQueue {

    @SuppressWarnings("hiding")
    private final SamplePool samplePool;
    private final ConcurrentLinkedQueue<Sample> internalQueue;

    // Accessed only by the consuming thread.
    private boolean readFirstFrame;
    private long lastReadTimeUs;
    private long spliceOutTimeUs;

    // Accessed by both the loading and consuming threads.
    private volatile MediaFormat mediaFormat;

    protected SampleQueue(SamplePool samplePool) {
      this.samplePool = samplePool;
      internalQueue = new ConcurrentLinkedQueue<Sample>();
      spliceOutTimeUs = Long.MIN_VALUE;
      lastReadTimeUs = Long.MIN_VALUE;
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
        internalQueue.remove();
        readFirstFrame = true;
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
      Sample head = internalQueue.peek();
      if (!readFirstFrame) {
        // Peeking discard of samples until we find a keyframe or run out of available samples.
        while (head != null && !head.isKeyframe) {
          recycle(head);
          internalQueue.remove();
          head = internalQueue.peek();
        }
      }
      if (head == null) {
        return null;
      }
      if (spliceOutTimeUs != Long.MIN_VALUE && head.timeUs >= spliceOutTimeUs) {
        // The sample is later than the time this queue is spliced out.
        recycle(head);
        internalQueue.remove();
        return null;
      }
      return head;
    }

    /**
     * Clears the queue.
     */
    public void release() {
      Sample toRecycle = internalQueue.poll();
      while (toRecycle != null) {
        recycle(toRecycle);
        toRecycle = internalQueue.poll();
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
      Sample nextSample = internalQueue.peek();
      if (nextSample != null) {
        firstPossibleSpliceTime = nextSample.timeUs;
      } else {
        firstPossibleSpliceTime = lastReadTimeUs + 1;
      }
      ConcurrentLinkedQueue<Sample> nextInternalQueue = nextQueue.internalQueue;
      Sample nextQueueSample = nextInternalQueue.peek();
      while (nextQueueSample != null
          && (nextQueueSample.timeUs < firstPossibleSpliceTime || !nextQueueSample.isKeyframe)) {
        // Discard samples from the next queue for as long as they are before the earliest possible
        // splice time, or not keyframes.
        nextQueue.internalQueue.remove();
        nextQueueSample = nextQueue.internalQueue.peek();
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
     * @return The sample.
     */
    protected Sample getSample() {
      return samplePool.get();
    }

    /**
     * Creates a new Sample and adds it to the queue.
     *
     * @param buffer The buffer to read sample data.
     * @param sampleSize The size of the sample data.
     * @param sampleTimeUs The sample time stamp.
     * @param isKeyframe True if the sample is a keyframe. False otherwise.
     */
    protected void addSample(BitArray buffer, int sampleSize, long sampleTimeUs,
        boolean isKeyframe) {
      Sample sample = getSample();
      addToSample(sample, buffer, sampleSize);
      sample.isKeyframe = isKeyframe;
      sample.timeUs = sampleTimeUs;
      addSample(sample);
    }

    protected void addSample(Sample sample) {
      adjustTimestamp(sample);
      largestParsedTimestampUs = Math.max(largestParsedTimestampUs, sample.timeUs);
      internalQueue.add(sample);
    }

    protected void addToSample(Sample sample, BitArray buffer, int size) {
      if (sample.data.length - sample.size < size) {
        sample.expand(size - sample.data.length + sample.size);
      }
      buffer.readBytes(sample.data, sample.size, size);
      sample.size += size;
    }

    private void adjustTimestamp(Sample sample) {
      if (pendingFirstSampleTimestampAdjustment) {
        sampleTimestampOffsetUs = firstSampleTimestamp - sample.timeUs;
        pendingFirstSampleTimestampAdjustment = false;
      }
      sample.timeUs += sampleTimestampOffsetUs;
    }

  }

  /**
   * Extracts individual samples from continuous byte stream.
   */
  private abstract class PesPayloadReader extends SampleQueue {

    protected PesPayloadReader(SamplePool samplePool) {
      super(samplePool);
    }

    public abstract void read(BitArray pesBuffer, int pesPayloadSize, long pesTimeUs);

  }

  /**
   * Parses a continuous H264 byte stream and extracts individual frames.
   */
  private class H264Reader extends PesPayloadReader {

    private static final int NAL_UNIT_TYPE_IDR = 5;
    private static final int NAL_UNIT_TYPE_AUD = 9;
    private static final int NAL_UNIT_TYPE_SPS = 7;

    public final SeiReader seiReader;

    // Used to store uncompleted sample data.
    private Sample currentSample;

    public H264Reader(SamplePool samplePool, SeiReader seiReader) {
      super(samplePool);
      this.seiReader = seiReader;
    }

    @Override
    public void release() {
      super.release();
      if (currentSample != null) {
        recycle(currentSample);
        currentSample = null;
      }
    }

    @Override
    public void read(BitArray pesBuffer, int pesPayloadSize, long pesTimeUs) {
      // Read leftover frame data from previous PES packet.
      pesPayloadSize -= readOneH264Frame(pesBuffer, true);

      if (pesBuffer.bytesLeft() <= 0 || pesPayloadSize <= 0) {
        return;
      }

      // Single PES packet should contain only one new H.264 frame.
      if (currentSample != null) {
        if (!hasMediaFormat() && currentSample.isKeyframe) {
          parseMediaFormat(currentSample);
        }
        seiReader.read(currentSample.data, currentSample.size, pesTimeUs);
        addSample(currentSample);
      }
      currentSample = getSample();
      pesPayloadSize -= readOneH264Frame(pesBuffer, false);
      currentSample.timeUs = pesTimeUs;

      if (pesPayloadSize > 0) {
        Log.e(TAG, "PES packet contains more frame data than expected");
      }
    }

    @SuppressLint("InlinedApi")
    private int readOneH264Frame(BitArray pesBuffer, boolean remainderOnly) {
      int offset = remainderOnly ? 0 : 3;
      int audStart = pesBuffer.findNextNalUnit(NAL_UNIT_TYPE_AUD, offset);
      if (currentSample != null) {
        int idrStart = pesBuffer.findNextNalUnit(NAL_UNIT_TYPE_IDR, offset);
        if (idrStart < audStart) {
          currentSample.isKeyframe = true;
        }
        addToSample(currentSample, pesBuffer, audStart);
      } else {
        pesBuffer.skipBytes(audStart);
      }
      return audStart;
    }

    private void parseMediaFormat(Sample sample) {
      BitArray bitArray = new BitArray(sample.data, sample.size);
      // Locate the SPS unit.
      int spsOffset = bitArray.findNextNalUnit(NAL_UNIT_TYPE_SPS, 0);
      if (spsOffset == bitArray.bytesLeft()) {
        return;
      }
      int nextNalOffset = bitArray.findNextNalUnit(-1, spsOffset + 3);

      // Unescape the SPS unit.
      byte[] unescapedSps = unescapeStream(bitArray.getData(), spsOffset, nextNalOffset);
      bitArray.reset(unescapedSps, unescapedSps.length);

      // Parse the SPS unit
      // Skip the NAL header.
      bitArray.skipBytes(4);
      int profileIdc = bitArray.readBits(8);
      // Skip 6 constraint bits, 2 reserved bits and level_idc.
      bitArray.skipBytes(2);
      // Skip seq_parameter_set_id.
      bitArray.readUnsignedExpGolombCodedInt();

      int chromaFormatIdc = 1; // Default is 4:2:0
      if (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 244
          || profileIdc == 44 || profileIdc == 83 || profileIdc == 86 || profileIdc == 118
          || profileIdc == 128 || profileIdc == 138) {
        chromaFormatIdc = bitArray.readUnsignedExpGolombCodedInt();
        if (chromaFormatIdc == 3) {
          // Skip separate_colour_plane_flag
          bitArray.skipBits(1);
        }
        // Skip bit_depth_luma_minus8
        bitArray.readUnsignedExpGolombCodedInt();
        // Skip bit_depth_chroma_minus8
        bitArray.readUnsignedExpGolombCodedInt();
        // Skip qpprime_y_zero_transform_bypass_flag
        bitArray.skipBits(1);
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
      // Skip log2_max_frame_num_minus4
      bitArray.readUnsignedExpGolombCodedInt();
      long picOrderCntType = bitArray.readUnsignedExpGolombCodedInt();
      if (picOrderCntType == 0) {
        // Skip log2_max_pic_order_cnt_lsb_minus4
        bitArray.readUnsignedExpGolombCodedInt();
      } else if (picOrderCntType == 1) {
        // Skip delta_pic_order_always_zero_flag
        bitArray.skipBits(1);
        // Skip offset_for_non_ref_pic
        bitArray.readSignedExpGolombCodedInt();
        // Skip offset_for_top_to_bottom_field
        bitArray.readSignedExpGolombCodedInt();
        long numRefFramesInPicOrderCntCycle = bitArray.readUnsignedExpGolombCodedInt();
        for (int i = 0; i < numRefFramesInPicOrderCntCycle; i++) {
          // Skip offset_for_ref_frame[i]
          bitArray.readUnsignedExpGolombCodedInt();
        }
      }
      // Skip max_num_ref_frames
      bitArray.readUnsignedExpGolombCodedInt();
      // Skip gaps_in_frame_num_value_allowed_flag
      bitArray.skipBits(1);
      int picWidthInMbs = bitArray.readUnsignedExpGolombCodedInt() + 1;
      int picHeightInMapUnits = bitArray.readUnsignedExpGolombCodedInt() + 1;
      boolean frameMbsOnlyFlag = bitArray.readBit();
      int frameHeightInMbs = (2 - (frameMbsOnlyFlag ? 1 : 0)) * picHeightInMapUnits;
      if (!frameMbsOnlyFlag) {
        // Skip mb_adaptive_frame_field_flag
        bitArray.skipBits(1);
      }
      // Skip direct_8x8_inference_flag
      bitArray.skipBits(1);
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
          frameWidth, frameHeight, null));
    }

    private void skipScalingList(BitArray bitArray, int size) {
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
   */
  private class SeiReader extends SampleQueue {

    // SEI data, used for Closed Captions.
    private static final int NAL_UNIT_TYPE_SEI = 6;

    private final BitArray seiBuffer;

    public SeiReader(SamplePool samplePool) {
      super(samplePool);
      setMediaFormat(MediaFormat.createEia608Format());
      seiBuffer = new BitArray();
    }

    @SuppressLint("InlinedApi")
    public void read(byte[] data, int size, long pesTimeUs) {
      seiBuffer.reset(data, size);
      while (seiBuffer.bytesLeft() > 0) {
        int seiStart = seiBuffer.findNextNalUnit(NAL_UNIT_TYPE_SEI, 0);
        if (seiStart == seiBuffer.bytesLeft()) {
          return;
        }
        seiBuffer.skipBytes(seiStart + 4);
        int ccDataSize = Eia608Parser.parseHeader(seiBuffer);
        if (ccDataSize > 0) {
          addSample(seiBuffer, ccDataSize, pesTimeUs, true);
        }
      }
    }

  }

  /**
   * Parses a continuous ADTS byte stream and extracts individual frames.
   */
  private class AdtsReader extends PesPayloadReader {

    private final BitArray adtsBuffer;
    private long timeUs;

    public AdtsReader(SamplePool samplePool) {
      super(samplePool);
      adtsBuffer = new BitArray();
    }

    @Override
    public void read(BitArray pesBuffer, int pesPayloadSize, long pesTimeUs) {
      boolean needToProcessLeftOvers = !adtsBuffer.isEmpty();
      adtsBuffer.append(pesBuffer, pesPayloadSize);
      // If there are leftovers from previous PES packet, process it with last calculated timeUs.
      if (needToProcessLeftOvers && !readOneAacFrame(timeUs)) {
        return;
      }
      int frameIndex = 0;
      do {
        long frameDuration = 0;
        // If frameIndex > 0, audioMediaFormat should be already parsed.
        // If frameIndex == 0, timeUs = pesTimeUs anyway.
        if (hasMediaFormat()) {
          frameDuration = 1000000L * 1024L / getMediaFormat().sampleRate;
        }
        timeUs = pesTimeUs + frameIndex * frameDuration;
        frameIndex++;
      } while(readOneAacFrame(timeUs));
    }

    @SuppressLint("InlinedApi")
    private boolean readOneAacFrame(long timeUs) {
      if (adtsBuffer.isEmpty()) {
        return false;
      }

      int offsetToSyncWord = adtsBuffer.findNextAdtsSyncWord();
      adtsBuffer.skipBytes(offsetToSyncWord);

      int adtsStartOffset = adtsBuffer.getByteOffset();

      if (adtsBuffer.bytesLeft() < 7) {
        adtsBuffer.setByteOffset(adtsStartOffset);
        adtsBuffer.clearReadData();
        return false;
      }

      adtsBuffer.skipBits(15);
      boolean hasCRC = !adtsBuffer.readBit();

      if (!hasMediaFormat()) {
        int audioObjectType = adtsBuffer.readBits(2) + 1;
        int sampleRateIndex = adtsBuffer.readBits(4);
        adtsBuffer.skipBits(1);
        int channelConfig = adtsBuffer.readBits(3);

        byte[] audioSpecificConfig = CodecSpecificDataUtil.buildAudioSpecificConfig(
            audioObjectType, sampleRateIndex, channelConfig);
        Pair<Integer, Integer> audioParams = CodecSpecificDataUtil.parseAudioSpecificConfig(
            audioSpecificConfig);

        MediaFormat mediaFormat = MediaFormat.createAudioFormat(MimeTypes.AUDIO_AAC,
            MediaFormat.NO_VALUE, audioParams.second, audioParams.first,
            Collections.singletonList(audioSpecificConfig));
        setMediaFormat(mediaFormat);
      } else {
        adtsBuffer.skipBits(10);
      }

      adtsBuffer.skipBits(4);
      int frameSize = adtsBuffer.readBits(13);
      adtsBuffer.skipBits(13);

      // Decrement frame size by ADTS header size and CRC.
      if (hasCRC) {
        // Skip CRC.
        adtsBuffer.skipBytes(2);
        frameSize -= 9;
      } else {
        frameSize -= 7;
      }

      if (frameSize > adtsBuffer.bytesLeft()) {
        adtsBuffer.setByteOffset(adtsStartOffset);
        adtsBuffer.clearReadData();
        return false;
      }

      addSample(adtsBuffer, frameSize, timeUs, true);
      return true;
    }

    @Override
    public void release() {
      super.release();
      adtsBuffer.reset();
    }

  }

  /**
   * Parses ID3 data and extracts individual text information frames.
   */
  private class Id3Reader extends PesPayloadReader {

    public Id3Reader(SamplePool samplePool) {
      super(samplePool);
      setMediaFormat(MediaFormat.createId3Format());
    }

    @SuppressLint("InlinedApi")
    @Override
    public void read(BitArray pesBuffer, int pesPayloadSize, long pesTimeUs) {
      addSample(pesBuffer, pesPayloadSize, pesTimeUs, true);
    }

  }

  /**
   * A pool from which the extractor can obtain sample objects for internal use.
   */
  public static class SamplePool {

    private static final int DEFAULT_BUFFER_SEGMENT_SIZE = 64 * 1024;

    private Sample firstInPool;

    /* package */ synchronized Sample get() {
      if (firstInPool == null) {
        return new Sample(DEFAULT_BUFFER_SEGMENT_SIZE);
      }
      Sample sample = firstInPool;
      firstInPool = sample.nextInPool;
      sample.nextInPool = null;
      return sample;
    }

    /* package */ synchronized void recycle(Sample sample) {
      sample.reset();
      sample.nextInPool = firstInPool;
      firstInPool = sample;
    }

  }

  /**
   * An internal variant of {@link SampleHolder} for internal pooling and buffering.
   */
  private static class Sample {

    public Sample nextInPool;

    public byte[] data;
    public boolean isKeyframe;
    public int size;
    public long timeUs;

    public Sample(int length) {
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
