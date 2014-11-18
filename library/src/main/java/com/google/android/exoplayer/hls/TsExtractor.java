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
  /* package */ final long firstSampleTimestamp;

  private boolean prepared;

  /* package */ boolean pendingFirstSampleTimestampAdjustment;
  /* package */ long sampleTimestampOffsetUs;
  /* package */ long largestParsedTimestampUs;
  /* package */ boolean discardFromNextKeyframes;

  public TsExtractor(long firstSampleTimestamp, SamplePool samplePool) {
    this.firstSampleTimestamp = firstSampleTimestamp;
    this.samplePool = samplePool;
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
   * Flushes any pending or incomplete samples, returning them to the sample pool.
   */
  public void clear() {
    for (int i = 0; i < sampleQueues.size(); i++) {
      sampleQueues.valueAt(i).clear();
    }
  }

  /**
   * For each track, discards samples from the next key frame (inclusive).
   */
  public void discardFromNextKeyframes() {
    discardFromNextKeyframes = true;
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
    Sample sample = sampleQueues.valueAt(track).poll();
    if (sample == null) {
      return false;
    }
    convert(sample, out);
    samplePool.recycle(sample);
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
    return !sampleQueues.valueAt(track).isEmpty();
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

  private void convert(Sample in, SampleHolder out) {
    if (out.data == null || out.data.capacity() < in.size) {
      out.replaceBuffer(in.size);
    }
    if (out.data != null) {
      out.data.put(in.data, 0, in.size);
    }
    out.size = in.size;
    out.flags = in.flags;
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
            pesPayloadReader = new AdtsReader();
            break;
          case TS_STREAM_TYPE_H264:
            SeiReader seiReader = new SeiReader();
            sampleQueues.put(TS_STREAM_TYPE_EIA608, seiReader);
            pesPayloadReader = new H264Reader(seiReader);
            break;
          case TS_STREAM_TYPE_ID3:
            pesPayloadReader = new Id3Reader();
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
   * A collection of extracted samples.
   */
  private abstract class SampleQueue {

    private final ConcurrentLinkedQueue<Sample> queue;

    private MediaFormat mediaFormat;
    private boolean foundFirstKeyframe;
    private boolean foundLastKeyframe;

    protected SampleQueue() {
      this.queue = new ConcurrentLinkedQueue<Sample>();
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

    public void clear() {
      Sample toRecycle = queue.poll();
      while (toRecycle != null) {
        samplePool.recycle(toRecycle);
        toRecycle = queue.poll();
      }
    }

    public Sample poll() {
      return queue.poll();
    }

    public boolean isEmpty() {
      return queue.isEmpty();
    }

    /**
     * Creates a new Sample and adds it to the queue.
     *
     * @param buffer The buffer to read sample data.
     * @param sampleSize The size of the sample data.
     * @param sampleTimeUs The sample time stamp.
     */
    protected void addSample(BitArray buffer, int sampleSize, long sampleTimeUs, int flags) {
      Sample sample = samplePool.get();
      addToSample(sample, buffer, sampleSize);
      sample.flags = flags;
      sample.timeUs = sampleTimeUs;
      addSample(sample);
    }

    @SuppressLint("InlinedApi")
    protected void addSample(Sample sample) {
      boolean isKeyframe = (sample.flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
      if (isKeyframe) {
        if (!foundFirstKeyframe) {
          foundFirstKeyframe = true;
        }
        if (discardFromNextKeyframes) {
          foundLastKeyframe = true;
        }
      }
      adjustTimestamp(sample);
      if (foundFirstKeyframe && !foundLastKeyframe) {
        largestParsedTimestampUs = Math.max(largestParsedTimestampUs, sample.timeUs);
        queue.add(sample);
      } else {
        samplePool.recycle(sample);
      }
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

    public H264Reader(SeiReader seiReader) {
      this.seiReader = seiReader;
    }

    @Override
    public void clear() {
      super.clear();
      if (currentSample != null) {
        samplePool.recycle(currentSample);
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
        if (!hasMediaFormat() && (currentSample.flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
          parseMediaFormat(currentSample);
        }
        seiReader.read(currentSample.data, currentSample.size, pesTimeUs);
        addSample(currentSample);
      }
      currentSample = samplePool.get();
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
          currentSample.flags = MediaExtractor.SAMPLE_FLAG_SYNC;
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

      if (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 244
          || profileIdc == 44 || profileIdc == 83 || profileIdc == 86 || profileIdc == 118
          || profileIdc == 128 || profileIdc == 138) {
        int chromaFormatIdc = bitArray.readUnsignedExpGolombCodedInt();
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

      // Set the format.
      setMediaFormat(MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
          picWidthInMbs * 16, frameHeightInMbs * 16, null));
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

    public SeiReader() {
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
          addSample(seiBuffer, ccDataSize, pesTimeUs, MediaExtractor.SAMPLE_FLAG_SYNC);
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

    public AdtsReader() {
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

      addSample(adtsBuffer, frameSize, timeUs, MediaExtractor.SAMPLE_FLAG_SYNC);
      return true;
    }

    @Override
    public void clear() {
      super.clear();
      adtsBuffer.reset();
    }

  }

  /**
   * Parses ID3 data and extracts individual text information frames.
   */
  private class Id3Reader extends PesPayloadReader {

    public Id3Reader() {
      setMediaFormat(MediaFormat.createId3Format());
    }

    @SuppressLint("InlinedApi")
    @Override
    public void read(BitArray pesBuffer, int pesPayloadSize, long pesTimeUs) {
      addSample(pesBuffer, pesPayloadSize, pesTimeUs, MediaExtractor.SAMPLE_FLAG_SYNC);
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
    public int flags;
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
      flags = 0;
      size = 0;
      timeUs = 0;
    }

  }

}
