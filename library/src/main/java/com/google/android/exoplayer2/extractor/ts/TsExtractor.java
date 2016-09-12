/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.ts;

import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.DummyTrackOutput;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * Facilitates the extraction of data from the MPEG-2 TS container format.
 */
public final class TsExtractor implements Extractor {

  /**
   * Factory for {@link TsExtractor} instances.
   */
  public static final ExtractorsFactory FACTORY = new ExtractorsFactory() {

    @Override
    public Extractor[] createExtractors() {
      return new Extractor[] {new TsExtractor()};
    }

  };

  public static final int WORKAROUND_ALLOW_NON_IDR_KEYFRAMES = 1;
  public static final int WORKAROUND_IGNORE_AAC_STREAM = 2;
  public static final int WORKAROUND_IGNORE_H264_STREAM = 4;
  public static final int WORKAROUND_DETECT_ACCESS_UNITS = 8;
  public static final int WORKAROUND_MAP_BY_TYPE = 16;

  private static final String TAG = "TsExtractor";

  private static final int TS_PACKET_SIZE = 188;
  private static final int TS_SYNC_BYTE = 0x47; // First byte of each TS packet.
  private static final int TS_PAT_PID = 0;

  private static final int TS_STREAM_TYPE_MPA = 0x03;
  private static final int TS_STREAM_TYPE_MPA_LSF = 0x04;
  private static final int TS_STREAM_TYPE_AAC = 0x0F;
  private static final int TS_STREAM_TYPE_AC3 = 0x81;
  private static final int TS_STREAM_TYPE_DTS = 0x8A;
  private static final int TS_STREAM_TYPE_HDMV_DTS = 0x82;
  private static final int TS_STREAM_TYPE_E_AC3 = 0x87;
  private static final int TS_STREAM_TYPE_H262 = 0x02;
  private static final int TS_STREAM_TYPE_H264 = 0x1B;
  private static final int TS_STREAM_TYPE_H265 = 0x24;
  private static final int TS_STREAM_TYPE_ID3 = 0x15;
  private static final int BASE_EMBEDDED_TRACK_ID = 0x2000; // 0xFF + 1

  private static final long AC3_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("AC-3");
  private static final long E_AC3_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("EAC3");
  private static final long HEVC_FORMAT_IDENTIFIER = Util.getIntegerCodeForString("HEVC");

  private static final int BUFFER_PACKET_COUNT = 5; // Should be at least 2
  private static final int BUFFER_SIZE = TS_PACKET_SIZE * BUFFER_PACKET_COUNT;

  private final TimestampAdjuster timestampAdjuster;
  private final int workaroundFlags;
  private final ParsableByteArray tsPacketBuffer;
  private final ParsableBitArray tsScratch;
  private final SparseIntArray continuityCounters;
  /* package */ final SparseArray<TsPayloadReader> tsPayloadReaders; // Indexed by pid
  /* package */ final SparseBooleanArray trackIds;

  // Accessed only by the loading thread.
  private ExtractorOutput output;
  private int nextEmbeddedTrackId;
  /* package */ Id3Reader id3Reader;

  public TsExtractor() {
    this(new TimestampAdjuster(0));
  }

  public TsExtractor(TimestampAdjuster timestampAdjuster) {
    this(timestampAdjuster, 0);
  }

  public TsExtractor(TimestampAdjuster timestampAdjuster, int workaroundFlags) {
    this.timestampAdjuster = timestampAdjuster;
    this.workaroundFlags = workaroundFlags;
    tsPacketBuffer = new ParsableByteArray(BUFFER_SIZE);
    tsScratch = new ParsableBitArray(new byte[3]);
    tsPayloadReaders = new SparseArray<>();
    tsPayloadReaders.put(TS_PAT_PID, new PatReader());
    trackIds = new SparseBooleanArray();
    nextEmbeddedTrackId = BASE_EMBEDDED_TRACK_ID;
    continuityCounters = new SparseIntArray();
  }

  // Extractor implementation.

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    byte[] buffer = tsPacketBuffer.data;
    input.peekFully(buffer, 0, BUFFER_SIZE);
    for (int j = 0; j < TS_PACKET_SIZE; j++) {
      for (int i = 0; true; i++) {
        if (i == BUFFER_PACKET_COUNT) {
          input.skipFully(j);
          return true;
        }
        if (buffer[j + i * TS_PACKET_SIZE] != TS_SYNC_BYTE) {
          break;
        }
      }
    }
    return false;
  }

  @Override
  public void init(ExtractorOutput output) {
    this.output = output;
    output.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
  }

  @Override
  public void seek(long position) {
    timestampAdjuster.reset();
    for (int i = 0; i < tsPayloadReaders.size(); i++) {
      tsPayloadReaders.valueAt(i).seek();
    }
    tsPacketBuffer.reset();
    continuityCounters.clear();
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    byte[] data = tsPacketBuffer.data;
    // Shift bytes to the start of the buffer if there isn't enough space left at the end
    if (BUFFER_SIZE - tsPacketBuffer.getPosition() < TS_PACKET_SIZE) {
      int bytesLeft = tsPacketBuffer.bytesLeft();
      if (bytesLeft > 0) {
        System.arraycopy(data, tsPacketBuffer.getPosition(), data, 0, bytesLeft);
      }
      tsPacketBuffer.reset(data, bytesLeft);
    }
    // Read more bytes until there is at least one packet size
    while (tsPacketBuffer.bytesLeft() < TS_PACKET_SIZE) {
      int limit = tsPacketBuffer.limit();
      int read = input.read(data, limit, BUFFER_SIZE - limit);
      if (read == C.RESULT_END_OF_INPUT) {
        return RESULT_END_OF_INPUT;
      }
      tsPacketBuffer.setLimit(limit + read);
    }

    // Note: see ISO/IEC 13818-1, section 2.4.3.2 for detailed information on the format of
    // the header.
    final int limit = tsPacketBuffer.limit();
    int position = tsPacketBuffer.getPosition();
    while (position < limit && data[position] != TS_SYNC_BYTE) {
      position++;
    }
    tsPacketBuffer.setPosition(position);

    int endOfPacket = position + TS_PACKET_SIZE;
    if (endOfPacket > limit) {
      return RESULT_CONTINUE;
    }

    tsPacketBuffer.skipBytes(1);
    tsPacketBuffer.readBytes(tsScratch, 3);
    if (tsScratch.readBit()) { // transport_error_indicator
      // There are uncorrectable errors in this packet.
      tsPacketBuffer.setPosition(endOfPacket);
      return RESULT_CONTINUE;
    }
    boolean payloadUnitStartIndicator = tsScratch.readBit();
    tsScratch.skipBits(1); // transport_priority
    int pid = tsScratch.readBits(13);
    tsScratch.skipBits(2); // transport_scrambling_control
    boolean adaptationFieldExists = tsScratch.readBit();
    boolean payloadExists = tsScratch.readBit();
    boolean discontinuityFound = false;
    int continuityCounter = tsScratch.readBits(4);
    int previousCounter = continuityCounters.get(pid, continuityCounter - 1);
    continuityCounters.put(pid, continuityCounter);
    if (previousCounter == continuityCounter) {
      // Duplicate packet found.
      tsPacketBuffer.setPosition(endOfPacket);
      return RESULT_CONTINUE;
    } else if (continuityCounter != (previousCounter + 1) % 16) {
      discontinuityFound = true;
    }

    // Skip the adaptation field.
    if (adaptationFieldExists) {
      int adaptationFieldLength = tsPacketBuffer.readUnsignedByte();
      tsPacketBuffer.skipBytes(adaptationFieldLength);
    }

    // Read the payload.
    if (payloadExists) {
      TsPayloadReader payloadReader = tsPayloadReaders.get(pid);
      if (payloadReader != null) {
        if (discontinuityFound) {
          payloadReader.seek();
        }
        tsPacketBuffer.setLimit(endOfPacket);
        payloadReader.consume(tsPacketBuffer, payloadUnitStartIndicator, output);
        Assertions.checkState(tsPacketBuffer.getPosition() <= endOfPacket);
        tsPacketBuffer.setLimit(limit);
      }
    }

    tsPacketBuffer.setPosition(endOfPacket);
    return RESULT_CONTINUE;
  }

  // Internals.

  /**
   * Parses TS packet payload data.
   */
  private abstract static class TsPayloadReader {

    /**
     * Notifies the reader that a seek has occurred.
     * <p>
     * Following a call to this method, the data passed to the next invocation of
     * {@link #consume(ParsableByteArray, boolean, ExtractorOutput)} will not be a continuation of
     * the data that was previously passed. Hence the reader should reset any internal state.
     */
    public abstract void seek();

    /**
     * Consumes the payload of a TS packet.
     *
     * @param data The TS packet. The position will be set to the start of the payload.
     * @param payloadUnitStartIndicator Whether payloadUnitStartIndicator was set on the TS packet.
     * @param output The output to which parsed data should be written.
     */
    public abstract void consume(ParsableByteArray data, boolean payloadUnitStartIndicator,
        ExtractorOutput output);

  }

  /**
   * Parses Program Association Table data.
   */
  private class PatReader extends TsPayloadReader {

    private final ParsableByteArray sectionData;
    private final ParsableBitArray patScratch;

    private int sectionLength;
    private int sectionBytesRead;
    private int crc;

    public PatReader() {
      sectionData = new ParsableByteArray();
      patScratch = new ParsableBitArray(new byte[4]);
    }

    @Override
    public void seek() {
      // Do nothing.
    }

    @Override
    public void consume(ParsableByteArray data, boolean payloadUnitStartIndicator,
        ExtractorOutput output) {
      // Skip pointer.
      if (payloadUnitStartIndicator) {
        int pointerField = data.readUnsignedByte();
        data.skipBytes(pointerField);

        // Note: see ISO/IEC 13818-1, section 2.4.4.3 for detailed information on the format of
        // the header.
        data.readBytes(patScratch, 3);
        patScratch.skipBits(12); // table_id (8), section_syntax_indicator (1), 0 (1), reserved (2)
        sectionLength = patScratch.readBits(12);
        sectionBytesRead = 0;
        crc = Util.crc(patScratch.data, 0, 3, 0xFFFFFFFF);

        sectionData.reset(sectionLength);
      }

      int bytesToRead = Math.min(data.bytesLeft(), sectionLength - sectionBytesRead);
      data.readBytes(sectionData.data, sectionBytesRead, bytesToRead);
      sectionBytesRead += bytesToRead;
      if (sectionBytesRead < sectionLength) {
        // Not yet fully read.
        return;
      }

      if (Util.crc(sectionData.data, 0, sectionLength, crc) != 0) {
        // CRC Invalid. The section gets discarded.
        return;
      }

      // transport_stream_id (16), reserved (2), version_number (5), current_next_indicator (1),
      // section_number (8), last_section_number (8)
      sectionData.skipBytes(5);

      int programCount = (sectionLength - 9) / 4;
      for (int i = 0; i < programCount; i++) {
        sectionData.readBytes(patScratch, 4);
        int programNumber = patScratch.readBits(16);
        patScratch.skipBits(3); // reserved (3)
        if (programNumber == 0) {
          patScratch.skipBits(13); // network_PID (13)
        } else {
          int pid = patScratch.readBits(13);
          tsPayloadReaders.put(pid, new PmtReader());
        }
      }
    }

  }

  /**
   * Parses Program Map Table.
   */
  private class PmtReader extends TsPayloadReader {

    private static final int TS_PMT_DESC_REGISTRATION = 0x05;
    private static final int TS_PMT_DESC_ISO639_LANG = 0x0A;
    private static final int TS_PMT_DESC_AC3 = 0x6A;
    private static final int TS_PMT_DESC_EAC3 = 0x7A;
    private static final int TS_PMT_DESC_DTS = 0x7B;

    private final ParsableBitArray pmtScratch;
    private final ParsableByteArray sectionData;

    private int sectionLength;
    private int sectionBytesRead;
    private int crc;

    public PmtReader() {
      pmtScratch = new ParsableBitArray(new byte[5]);
      sectionData = new ParsableByteArray();
    }

    @Override
    public void seek() {
      // Do nothing.
    }

    @Override
    public void consume(ParsableByteArray data, boolean payloadUnitStartIndicator,
        ExtractorOutput output) {
      if (payloadUnitStartIndicator) {
        // Skip pointer.
        int pointerField = data.readUnsignedByte();
        data.skipBytes(pointerField);

        // Note: see ISO/IEC 13818-1, section 2.4.4.8 for detailed information on the format of
        // the header.
        data.readBytes(pmtScratch, 3);
        pmtScratch.skipBits(12); // table_id (8), section_syntax_indicator (1), 0 (1), reserved (2)
        sectionLength = pmtScratch.readBits(12);
        sectionBytesRead = 0;
        crc = Util.crc(pmtScratch.data, 0, 3, 0xFFFFFFFF);

        sectionData.reset(sectionLength);
      }

      int bytesToRead = Math.min(data.bytesLeft(), sectionLength - sectionBytesRead);
      data.readBytes(sectionData.data, sectionBytesRead, bytesToRead);
      sectionBytesRead += bytesToRead;
      if (sectionBytesRead < sectionLength) {
        // Not yet fully read.
        return;
      }

      if (Util.crc(sectionData.data, 0, sectionLength, crc) != 0) {
        // CRC Invalid. The section gets discarded.
        return;
      }

      // program_number (16), reserved (2), version_number (5), current_next_indicator (1),
      // section_number (8), last_section_number (8), reserved (3), PCR_PID (13)
      // Skip the rest of the PMT header.
      sectionData.skipBytes(7);

      // Read program_info_length.
      sectionData.readBytes(pmtScratch, 2);
      pmtScratch.skipBits(4);
      int programInfoLength = pmtScratch.readBits(12);

      // Skip the descriptors.
      sectionData.skipBytes(programInfoLength);

      if ((workaroundFlags & WORKAROUND_MAP_BY_TYPE) != 0 && id3Reader == null) {
        // Setup an ID3 track regardless of whether there's a corresponding entry, in case one
        // appears intermittently during playback. See b/20261500.
        id3Reader = new Id3Reader(output.track(TS_STREAM_TYPE_ID3));
      }

      int remainingEntriesLength = sectionLength - 9 /* Length of fields before descriptors */
          - programInfoLength - 4 /* CRC length */;
      while (remainingEntriesLength > 0) {
        sectionData.readBytes(pmtScratch, 5);
        int streamType = pmtScratch.readBits(8);
        pmtScratch.skipBits(3); // reserved
        int elementaryPid = pmtScratch.readBits(13);
        pmtScratch.skipBits(4); // reserved
        int esInfoLength = pmtScratch.readBits(12); // ES_info_length.
        EsInfo esInfo = readEsInfo(sectionData, esInfoLength);
        if (streamType == 0x06) {
          streamType = esInfo.streamType;
        }
        remainingEntriesLength -= esInfoLength + 5;
        int trackId = (workaroundFlags & WORKAROUND_MAP_BY_TYPE) != 0 ? streamType : elementaryPid;
        if (trackIds.get(trackId)) {
          continue;
        }
        ElementaryStreamReader pesPayloadReader;
        switch (streamType) {
          case TS_STREAM_TYPE_MPA:
            pesPayloadReader = new MpegAudioReader(output.track(trackId), esInfo.language);
            break;
          case TS_STREAM_TYPE_MPA_LSF:
            pesPayloadReader = new MpegAudioReader(output.track(trackId), esInfo.language);
            break;
          case TS_STREAM_TYPE_AAC:
            pesPayloadReader = (workaroundFlags & WORKAROUND_IGNORE_AAC_STREAM) != 0 ? null
                : new AdtsReader(output.track(trackId), new DummyTrackOutput(), esInfo.language);
            break;
          case TS_STREAM_TYPE_AC3:
          case TS_STREAM_TYPE_E_AC3:
            pesPayloadReader = new Ac3Reader(output.track(trackId), esInfo.language);
            break;
          case TS_STREAM_TYPE_DTS:
          case TS_STREAM_TYPE_HDMV_DTS:
            pesPayloadReader = new DtsReader(output.track(trackId), esInfo.language);
            break;
          case TS_STREAM_TYPE_H262:
            pesPayloadReader = new H262Reader(output.track(trackId));
            break;
          case TS_STREAM_TYPE_H264:
            pesPayloadReader = (workaroundFlags & WORKAROUND_IGNORE_H264_STREAM) != 0 ? null
                : new H264Reader(output.track(trackId),
                    new SeiReader(output.track(nextEmbeddedTrackId++)),
                    (workaroundFlags & WORKAROUND_ALLOW_NON_IDR_KEYFRAMES) != 0,
                    (workaroundFlags & WORKAROUND_DETECT_ACCESS_UNITS) != 0);
            break;
          case TS_STREAM_TYPE_H265:
            pesPayloadReader = new H265Reader(output.track(trackId),
                new SeiReader(output.track(nextEmbeddedTrackId++)));
            break;
          case TS_STREAM_TYPE_ID3:
            if ((workaroundFlags & WORKAROUND_MAP_BY_TYPE) != 0) {
              pesPayloadReader = id3Reader;
            } else {
              pesPayloadReader = new Id3Reader(output.track(nextEmbeddedTrackId++));
            }
            break;
          default:
            pesPayloadReader = null;
            break;
        }

        if (pesPayloadReader != null) {
          trackIds.put(trackId, true);
          tsPayloadReaders.put(elementaryPid,
              new PesReader(pesPayloadReader, timestampAdjuster));
        }
      }

      output.endTracks();
    }

    /**
     * Returns the stream info read from the available descriptors, or -1 if no
     * descriptors are present. Sets {@code data}'s position to the end of the descriptors.
     *
     * @param data A buffer with its position set to the start of the first descriptor.
     * @param length The length of descriptors to read from the current position in {@code data}.
     * @return The stream info read from the available descriptors, or -1 if no
     *     descriptors are present.
     */
    private EsInfo readEsInfo(ParsableByteArray data, int length) {
      int descriptorsEndPosition = data.getPosition() + length;
      int streamType = -1;
      int audioType = -1;
      String language = null;
      while (data.getPosition() < descriptorsEndPosition) {
        int descriptorTag = data.readUnsignedByte();
        int descriptorLength = data.readUnsignedByte();
        int positionOfNextDescriptor = data.getPosition() + descriptorLength;
        if (descriptorTag == TS_PMT_DESC_REGISTRATION) { // registration_descriptor
          long formatIdentifier = data.readUnsignedInt();
          if (formatIdentifier == AC3_FORMAT_IDENTIFIER) {
            streamType = TS_STREAM_TYPE_AC3;
          } else if (formatIdentifier == E_AC3_FORMAT_IDENTIFIER) {
            streamType = TS_STREAM_TYPE_E_AC3;
          } else if (formatIdentifier == HEVC_FORMAT_IDENTIFIER) {
            streamType = TS_STREAM_TYPE_H265;
          }
        } else if (descriptorTag == TS_PMT_DESC_AC3) { // AC-3_descriptor in DVB (ETSI EN 300 468)
          streamType = TS_STREAM_TYPE_AC3;
        } else if (descriptorTag == TS_PMT_DESC_EAC3) { // enhanced_AC-3_descriptor
          streamType = TS_STREAM_TYPE_E_AC3;
        } else if (descriptorTag == TS_PMT_DESC_DTS) { // DTS_descriptor
          streamType = TS_STREAM_TYPE_DTS;
        } else if (descriptorTag == TS_PMT_DESC_ISO639_LANG) {
          language = new String(data.data, data.getPosition(), 3).trim();
          audioType = data.data[data.getPosition() + 3];
        }
        // Skip unused bytes of current descriptor.
        data.skipBytes(positionOfNextDescriptor - data.getPosition());
      }
      data.setPosition(descriptorsEndPosition);
      return new EsInfo(streamType, audioType, language);
    }

    private final class EsInfo {

      final int streamType;
      final int audioType;
      final String language;

      public EsInfo(int streamType, int audioType, String language) {
        this.streamType = streamType;
        this.audioType = audioType;
        this.language = language;
      }

    }

  }

  /**
   * Parses PES packet data and extracts samples.
   */
  private static final class PesReader extends TsPayloadReader {

    private static final int STATE_FINDING_HEADER = 0;
    private static final int STATE_READING_HEADER = 1;
    private static final int STATE_READING_HEADER_EXTENSION = 2;
    private static final int STATE_READING_BODY = 3;

    private static final int HEADER_SIZE = 9;
    private static final int MAX_HEADER_EXTENSION_SIZE = 10;
    private static final int PES_SCRATCH_SIZE = 10; // max(HEADER_SIZE, MAX_HEADER_EXTENSION_SIZE)

    private final ElementaryStreamReader pesPayloadReader;
    private final TimestampAdjuster timestampAdjuster;
    private final ParsableBitArray pesScratch;

    private int state;
    private int bytesRead;

    private boolean ptsFlag;
    private boolean dtsFlag;
    private boolean seenFirstDts;
    private int extendedHeaderLength;
    private int payloadSize;
    private boolean dataAlignmentIndicator;
    private long timeUs;

    public PesReader(ElementaryStreamReader pesPayloadReader,
        TimestampAdjuster timestampAdjuster) {
      this.pesPayloadReader = pesPayloadReader;
      this.timestampAdjuster = timestampAdjuster;
      pesScratch = new ParsableBitArray(new byte[PES_SCRATCH_SIZE]);
      state = STATE_FINDING_HEADER;
    }

    @Override
    public void seek() {
      state = STATE_FINDING_HEADER;
      bytesRead = 0;
      seenFirstDts = false;
      pesPayloadReader.seek();
    }

    @Override
    public void consume(ParsableByteArray data, boolean payloadUnitStartIndicator,
        ExtractorOutput output) {
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
            // Either way, notify the reader that it has now finished.
            pesPayloadReader.packetFinished();
            break;
        }
        setState(STATE_READING_HEADER);
      }

      while (data.bytesLeft() > 0) {
        switch (state) {
          case STATE_FINDING_HEADER:
            data.skipBytes(data.bytesLeft());
            break;
          case STATE_READING_HEADER:
            if (continueRead(data, pesScratch.data, HEADER_SIZE)) {
              setState(parseHeader() ? STATE_READING_HEADER_EXTENSION : STATE_FINDING_HEADER);
            }
            break;
          case STATE_READING_HEADER_EXTENSION:
            int readLength = Math.min(MAX_HEADER_EXTENSION_SIZE, extendedHeaderLength);
            // Read as much of the extended header as we're interested in, and skip the rest.
            if (continueRead(data, pesScratch.data, readLength)
                && continueRead(data, null, extendedHeaderLength)) {
              parseHeaderExtension();
              pesPayloadReader.packetStarted(timeUs, dataAlignmentIndicator);
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
            pesPayloadReader.consume(data);
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
        source.skipBytes(bytesToRead);
      } else {
        source.readBytes(target, bytesRead, bytesToRead);
      }
      bytesRead += bytesToRead;
      return bytesRead == targetLength;
    }

    private boolean parseHeader() {
      // Note: see ISO/IEC 13818-1, section 2.4.3.6 for detailed information on the format of
      // the header.
      pesScratch.setPosition(0);
      int startCodePrefix = pesScratch.readBits(24);
      if (startCodePrefix != 0x000001) {
        Log.w(TAG, "Unexpected start code prefix: " + startCodePrefix);
        payloadSize = -1;
        return false;
      }

      pesScratch.skipBits(8); // stream_id.
      int packetLength = pesScratch.readBits(16);
      pesScratch.skipBits(5); // '10' (2), PES_scrambling_control (2), PES_priority (1)
      dataAlignmentIndicator = pesScratch.readBit();
      pesScratch.skipBits(2); // copyright (1), original_or_copy (1)
      ptsFlag = pesScratch.readBit();
      dtsFlag = pesScratch.readBit();
      // ESCR_flag (1), ES_rate_flag (1), DSM_trick_mode_flag (1),
      // additional_copy_info_flag (1), PES_CRC_flag (1), PES_extension_flag (1)
      pesScratch.skipBits(6);
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
      timeUs = C.TIME_UNSET;
      if (ptsFlag) {
        pesScratch.skipBits(4); // '0010' or '0011'
        long pts = (long) pesScratch.readBits(3) << 30;
        pesScratch.skipBits(1); // marker_bit
        pts |= pesScratch.readBits(15) << 15;
        pesScratch.skipBits(1); // marker_bit
        pts |= pesScratch.readBits(15);
        pesScratch.skipBits(1); // marker_bit
        if (!seenFirstDts && dtsFlag) {
          pesScratch.skipBits(4); // '0011'
          long dts = (long) pesScratch.readBits(3) << 30;
          pesScratch.skipBits(1); // marker_bit
          dts |= pesScratch.readBits(15) << 15;
          pesScratch.skipBits(1); // marker_bit
          dts |= pesScratch.readBits(15);
          pesScratch.skipBits(1); // marker_bit
          // Subsequent PES packets may have earlier presentation timestamps than this one, but they
          // should all be greater than or equal to this packet's decode timestamp. We feed the
          // decode timestamp to the adjuster here so that in the case that this is the first to be
          // fed, the adjuster will be able to compute an offset to apply such that the adjusted
          // presentation timestamps of all future packets are non-negative.
          timestampAdjuster.adjustTsTimestamp(dts);
          seenFirstDts = true;
        }
        timeUs = timestampAdjuster.adjustTsTimestamp(pts);
      }
    }

  }

}
