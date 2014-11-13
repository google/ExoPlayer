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
package com.google.android.exoplayer.parser.ts;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.CodecSpecificDataUtil;
import com.google.android.exoplayer.util.MimeTypes;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Queue;

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

  private final BitsArray tsPacketBuffer;
  private final SparseArray<PesPayloadReader> pesPayloadReaders; // Indexed by streamType
  private final SparseArray<TsPayloadReader> tsPayloadReaders; // Indexed by pid
  private final SamplePool samplePool;

  private boolean prepared;

  /* package */ boolean pendingFirstSampleTimestampAdjustment;
  /* package */ long firstSampleTimestamp;
  /* package */ long sampleTimestampOffsetUs;
  /* package */ long largestParsedTimestampUs;
  /* package */ boolean discardFromNextKeyframes;

  public TsExtractor(long firstSampleTimestamp, SamplePool samplePool) {
    this.firstSampleTimestamp = firstSampleTimestamp;
    this.samplePool = samplePool;
    pendingFirstSampleTimestampAdjustment = true;
    tsPacketBuffer = new BitsArray();
    pesPayloadReaders = new SparseArray<PesPayloadReader>();
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
    return pesPayloadReaders.size();
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
    return pesPayloadReaders.valueAt(track).getMediaFormat();
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
    for (int i = 0; i < pesPayloadReaders.size(); i++) {
      pesPayloadReaders.valueAt(i).clear();
    }
  }

  /**
   * For each track, whether to discard samples from the next keyframe (inclusive).
   */
  public void discardFromNextKeyframes() {
    discardFromNextKeyframes = true;
  }

  /**
   * Consumes data from a {@link NonBlockingInputStream}.
   * <p>
   * The read terminates if the end of the input stream is reached, if insufficient data is
   * available to read a sample, or if the extractor has consumed up to the specified target
   * timestamp.
   *
   * @param inputStream The input stream from which data should be read.
   * @param targetTimestampUs A target timestamp to consume up to.
   * @return True if the target timestamp was reached. False otherwise.
   */
  public boolean consumeUntil(NonBlockingInputStream inputStream, long targetTimestampUs) {
    while (largestParsedTimestampUs < targetTimestampUs && readTSPacket(inputStream) != -1) {
      // Carry on.
    }
    if (!prepared) {
      prepared = checkPrepared();
    }
    return largestParsedTimestampUs >= targetTimestampUs;
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
    Queue<Sample> queue = pesPayloadReaders.valueAt(track).sampleQueue;
    if (queue.isEmpty()) {
      return false;
    }
    Sample sample = queue.remove();
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
    for (int i = 0; i < pesPayloadReaders.size(); i++) {
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
    return !pesPayloadReaders.valueAt(track).sampleQueue.isEmpty();
  }

  private boolean checkPrepared() {
    int pesPayloadReaderCount = pesPayloadReaders.size();
    if (pesPayloadReaderCount == 0) {
      return false;
    }
    for (int i = 0; i < pesPayloadReaderCount; i++) {
      if (!pesPayloadReaders.valueAt(i).hasMediaFormat()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Read a single TS packet.
   */
  private int readTSPacket(NonBlockingInputStream inputStream) {
    // Read entire single TS packet.
    if (inputStream.getAvailableByteCount() < TS_PACKET_SIZE) {
      return -1;
    }

    tsPacketBuffer.reset();
    tsPacketBuffer.append(inputStream, TS_PACKET_SIZE);

    // Parse TS header.
    // Check sync byte.
    int syncByte = tsPacketBuffer.readUnsignedByte();
    if (syncByte != TS_SYNC_BYTE) {
      return 0;
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
      if (payloadReader == null) {
        return 0;
      }
      payloadReader.read(tsPacketBuffer, payloadUnitStartIndicator);
    }
    return 0;
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

    public abstract void read(BitsArray tsBuffer, boolean payloadUnitStartIndicator);

  }

  /**
   * Parses Program Association Table data.
   */
  private class PatReader extends TsPayloadReader {

    @Override
    public void read(BitsArray tsBuffer, boolean payloadUnitStartIndicator) {
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
    public void read(BitsArray tsBuffer, boolean payloadUnitStartIndicator) {
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

      // Read descriptors.
      readDescriptors(tsBuffer, programInfoLength);

      int entriesSize = sectionLength - 9 /* size of the rest of the fields before descriptors */
          - programInfoLength - 4 /* CRC size */;
      while (entriesSize > 0) {
        int streamType = tsBuffer.readBits(8);
        tsBuffer.skipBits(3);
        int elementaryPid = tsBuffer.readBits(13);
        tsBuffer.skipBits(4);
        int esInfoLength = tsBuffer.readBits(12);

        readDescriptors(tsBuffer, esInfoLength);
        entriesSize -= esInfoLength + 5;

        if (pesPayloadReaders.get(streamType) != null) {
          continue;
        }

        PesPayloadReader pesPayloadReader = null;
        switch (streamType) {
          case TS_STREAM_TYPE_AAC:
            pesPayloadReader = new AdtsReader();
            break;
          case TS_STREAM_TYPE_H264:
            pesPayloadReader = new H264Reader();
            break;
          case TS_STREAM_TYPE_ID3:
            pesPayloadReader = new Id3Reader();
            break;
        }

        if (pesPayloadReader != null) {
          pesPayloadReaders.put(streamType, pesPayloadReader);
          tsPayloadReaders.put(elementaryPid, new PesReader(pesPayloadReader));
        }
      }

      // Skip CRC_32.
    }

    private void readDescriptors(BitsArray tsBuffer, int descriptorsSize) {
      while (descriptorsSize > 0) {
        // Skip tag.
        tsBuffer.skipBits(8);
        int descriptorsLength = tsBuffer.readBits(8);
        if (descriptorsLength > 0) {
          // Skip entire descriptor data.
          tsBuffer.skipBytes(descriptorsLength);
        }
        descriptorsSize -= descriptorsLength + 2; //        descriptorsSize -= descriptorsSize + 2; if you subtract it with descriptor size you will only skip the first descriptor.

      }
    }

  }

  /**
   * Parses PES packet data and extracts samples.
   */
  private class PesReader extends TsPayloadReader {

    // Reusable buffer for incomplete PES data.
    private final BitsArray pesBuffer;
    // Parses PES payload and extracts individual samples.
    private final PesPayloadReader pesPayloadReader;

    private int packetLength;

    public PesReader(PesPayloadReader pesPayloadReader) {
      this.pesPayloadReader = pesPayloadReader;
      this.packetLength = -1;
      pesBuffer = new BitsArray();
    }

    @Override
    public void read(BitsArray tsBuffer, boolean payloadUnitStartIndicator) {
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
   * Extracts individual samples from continuous byte stream.
   */
  private abstract class PesPayloadReader {

    public final Queue<Sample> sampleQueue;

    private MediaFormat mediaFormat;
    private boolean foundFirstKeyframe;
    private boolean foundLastKeyframe;

    protected PesPayloadReader() {
      this.sampleQueue = new LinkedList<Sample>();
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

    public abstract void read(BitsArray pesBuffer, int pesPayloadSize, long pesTimeUs);

    public void clear() {
      while (!sampleQueue.isEmpty()) {
        samplePool.recycle(sampleQueue.remove());
      }
    }

    /**
     * Creates a new Sample and adds it to the queue.
     *
     * @param buffer The buffer to read sample data.
     * @param sampleSize The size of the sample data.
     * @param sampleTimeUs The sample time stamp.
     */
    protected void addSample(BitsArray buffer, int sampleSize, long sampleTimeUs, int flags) {
      Sample sample = samplePool.get();
      addToSample(sample, buffer, sampleSize);
      sample.flags = flags;
      sample.timeUs = sampleTimeUs;
      addSample(sample);
    }

    @SuppressLint("InlinedApi")
    protected void addSample(Sample sample) {
      boolean isKeyframe = (sample.flags & MediaCodec.BUFFER_FLAG_SYNC_FRAME) != 0;
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
        sampleQueue.add(sample);
      } else {
        samplePool.recycle(sample);
      }
    }

    protected void addToSample(Sample sample, BitsArray buffer, int size) {
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
   * Parses a continuous H264 byte stream and extracts individual frames.
   */
  private class H264Reader extends PesPayloadReader {

    // IDR picture.
    private static final int NAL_UNIT_TYPE_IDR = 5;
    // Access unit delimiter.
    private static final int NAL_UNIT_TYPE_AUD = 9;

    // Used to store uncompleted sample data.
    private Sample currentSample;

    public H264Reader() {
      // TODO: Parse the format from the stream.
      setMediaFormat(MediaFormat.createVideoFormat(MimeTypes.VIDEO_H264, MediaFormat.NO_VALUE,
          1920, 1080, null));
    }

    @Override
    public void read(BitsArray pesBuffer, int pesPayloadSize, long pesTimeUs) {
      // Read leftover frame data from previous PES packet.
      pesPayloadSize -= readOneH264Frame(pesBuffer, true);

      if (pesBuffer.bytesLeft() <= 0 || pesPayloadSize <= 0) {
        return;
      }

      // Single PES packet should contain only one new H.264 frame.
      if (currentSample != null) {
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
    private int readOneH264Frame(BitsArray pesBuffer, boolean remainderOnly) {
      int offset = remainderOnly ? 0 : 3;
      int audStart = pesBuffer.findNextNalUnit(NAL_UNIT_TYPE_AUD, offset);
      int idrStart = pesBuffer.findNextNalUnit(NAL_UNIT_TYPE_IDR, offset);
      if (audStart > 0) {
        if (currentSample != null) {
          addToSample(currentSample, pesBuffer, audStart);
          if (idrStart < audStart) {
            currentSample.flags = MediaExtractor.SAMPLE_FLAG_SYNC;
          }
        } else {
          pesBuffer.skipBytes(audStart);
        }
        return audStart;
      }
      return 0;
    }

    @Override
    public void clear() {
      super.clear();
      if (currentSample != null) {
        samplePool.recycle(currentSample);
        currentSample = null;
      }
    }
  }

  /**
   * Parses a continuous ADTS byte stream and extracts individual frames.
   */
  private class AdtsReader extends PesPayloadReader {

    private final BitsArray adtsBuffer;
    private long timeUs;

    public AdtsReader() {
      adtsBuffer = new BitsArray();
    }

    @Override
    public void read(BitsArray pesBuffer, int pesPayloadSize, long pesTimeUs) {
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
    public void read(BitsArray pesBuffer, int pesPayloadSize, long pesTimeUs) {
      addSample(pesBuffer, pesPayloadSize, pesTimeUs, MediaExtractor.SAMPLE_FLAG_SYNC);
    }

  }

  /**
   * A pool from which the extractor can obtain sample objects for internal use.
   */
  public static class SamplePool {

    private static final int DEFAULT_BUFFER_SEGMENT_SIZE = 64 * 1024;

    private final ArrayList<Sample> samples;

    public SamplePool() {
      samples = new ArrayList<Sample>();
    }

    /* package */ Sample get() {
      if (samples.isEmpty()) {
        return new Sample(DEFAULT_BUFFER_SEGMENT_SIZE);
      }
      return samples.remove(samples.size() - 1);
    }

    /* package */ void recycle(Sample sample) {
      sample.reset();
      samples.add(sample);
    }

  }

  /**
   * Simplified version of SampleHolder for internal buffering.
   */
  private static class Sample {

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
