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
package com.google.android.exoplayer.extractor.ts;

import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import java.io.IOException;

/**
 * Facilitates the extraction of data from the MPEG-2 TS container format.
 */
public final class TsExtractor implements Extractor {

  private static final String TAG = "TsExtractor";

  private static final int TS_PACKET_SIZE = 188;
  private static final int TS_SYNC_BYTE = 0x47; // First byte of each TS packet.
  private static final int TS_PAT_PID = 0;

  private static final int TS_STREAM_TYPE_MPA = 0x03;
  private static final int TS_STREAM_TYPE_MPA_LSF = 0x04;
  private static final int TS_STREAM_TYPE_AAC = 0x0F;
  private static final int TS_STREAM_TYPE_ATSC_AC3 = 0x81;
  private static final int TS_STREAM_TYPE_ATSC_E_AC3 = 0x87;
  private static final int TS_STREAM_TYPE_H264 = 0x1B;
  private static final int TS_STREAM_TYPE_H265 = 0x24;
  private static final int TS_STREAM_TYPE_ID3 = 0x15;
  private static final int TS_STREAM_TYPE_EIA608 = 0x100; // 0xFF + 1

  private final PtsTimestampAdjuster ptsTimestampAdjuster;
  private final ParsableByteArray tsPacketBuffer;
  private final ParsableBitArray tsScratch;
  private final boolean idrKeyframesOnly;
  /* package */ final SparseBooleanArray streamTypes;
  /* package */ final SparseArray<TsPayloadReader> tsPayloadReaders; // Indexed by pid

  // Accessed only by the loading thread.
  private ExtractorOutput output;
  /* package */ Id3Reader id3Reader;

  public TsExtractor() {
    this(new PtsTimestampAdjuster(0));
  }

  public TsExtractor(PtsTimestampAdjuster ptsTimestampAdjuster) {
    this(ptsTimestampAdjuster, true);
  }

  public TsExtractor(PtsTimestampAdjuster ptsTimestampAdjuster, boolean idrKeyframesOnly) {
    this.idrKeyframesOnly = idrKeyframesOnly;
    tsScratch = new ParsableBitArray(new byte[3]);
    tsPacketBuffer = new ParsableByteArray(TS_PACKET_SIZE);
    streamTypes = new SparseBooleanArray();
    tsPayloadReaders = new SparseArray<>();
    tsPayloadReaders.put(TS_PAT_PID, new PatReader());
    this.ptsTimestampAdjuster = ptsTimestampAdjuster;
  }

  // Extractor implementation.

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    byte[] scratch = new byte[1];
    for (int i = 0; i < 5; i++) {
      input.peekFully(scratch, 0, 1);
      if ((scratch[0] & 0xFF) != 0x47) {
        return false;
      }
      input.advancePeekPosition(TS_PACKET_SIZE - 1);
    }
    return true;
  }

  @Override
  public void init(ExtractorOutput output) {
    this.output = output;
    output.seekMap(SeekMap.UNSEEKABLE);
  }

  @Override
  public void seek() {
    ptsTimestampAdjuster.reset();
    for (int i = 0; i < tsPayloadReaders.size(); i++) {
      tsPayloadReaders.valueAt(i).seek();
    }
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    if (!input.readFully(tsPacketBuffer.data, 0, TS_PACKET_SIZE, true)) {
      return RESULT_END_OF_INPUT;
    }

    // Note: see ISO/IEC 13818-1, section 2.4.3.2 for detailed information on the format of
    // the header.
    tsPacketBuffer.setPosition(0);
    tsPacketBuffer.setLimit(TS_PACKET_SIZE);
    int syncByte = tsPacketBuffer.readUnsignedByte();
    if (syncByte != TS_SYNC_BYTE) {
      return RESULT_CONTINUE;
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
      tsPacketBuffer.skipBytes(adaptationFieldLength);
    }

    // Read the payload.
    if (payloadExists) {
      TsPayloadReader payloadReader = tsPayloadReaders.get(pid);
      if (payloadReader != null) {
        payloadReader.consume(tsPacketBuffer, payloadUnitStartIndicator, output);
      }
    }

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

    private final ParsableBitArray patScratch;

    public PatReader() {
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
      }

      data.readBytes(patScratch, 3);
      patScratch.skipBits(12); // table_id (8), section_syntax_indicator (1), '0' (1), reserved (2)
      int sectionLength = patScratch.readBits(12);
      // transport_stream_id (16), reserved (2), version_number (5), current_next_indicator (1),
      // section_number (8), last_section_number (8)
      data.skipBytes(5);

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
      }

      // Note: see ISO/IEC 13818-1, section 2.4.4.8 for detailed information on the format of
      // the header.
      data.readBytes(pmtScratch, 3);
      pmtScratch.skipBits(12); // table_id (8), section_syntax_indicator (1), '0' (1), reserved (2)
      int sectionLength = pmtScratch.readBits(12);

      // program_number (16), reserved (2), version_number (5), current_next_indicator (1),
      // section_number (8), last_section_number (8), reserved (3), PCR_PID (13)
      // Skip the rest of the PMT header.
      data.skipBytes(7);

      data.readBytes(pmtScratch, 2);
      pmtScratch.skipBits(4);
      int programInfoLength = pmtScratch.readBits(12);

      // Skip the descriptors.
      data.skipBytes(programInfoLength);

      if (id3Reader == null) {
        // Setup an ID3 track regardless of whether there's a corresponding entry, in case one
        // appears intermittently during playback. See b/20261500.
        id3Reader = new Id3Reader(output.track(TS_STREAM_TYPE_ID3));
      }

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
        data.skipBytes(esInfoLength);
        entriesSize -= esInfoLength + 5;

        if (streamTypes.get(streamType)) {
          continue;
        }

        // TODO: Detect and read DVB AC-3 streams with Ac3Reader.
        ElementaryStreamReader pesPayloadReader = null;
        switch (streamType) {
          case TS_STREAM_TYPE_MPA:
            pesPayloadReader = new MpegAudioReader(output.track(TS_STREAM_TYPE_MPA));
            break;
          case TS_STREAM_TYPE_MPA_LSF:
            pesPayloadReader = new MpegAudioReader(output.track(TS_STREAM_TYPE_MPA_LSF));
            break;
          case TS_STREAM_TYPE_AAC:
            pesPayloadReader = new AdtsReader(output.track(TS_STREAM_TYPE_AAC));
            break;
          case TS_STREAM_TYPE_ATSC_E_AC3:
          case TS_STREAM_TYPE_ATSC_AC3:
            pesPayloadReader = new Ac3Reader(output.track(streamType));
            break;
          case TS_STREAM_TYPE_H264:
            pesPayloadReader = new H264Reader(output.track(TS_STREAM_TYPE_H264),
                new SeiReader(output.track(TS_STREAM_TYPE_EIA608)), idrKeyframesOnly);
            break;
          case TS_STREAM_TYPE_H265:
            pesPayloadReader = new H265Reader(output.track(TS_STREAM_TYPE_H265),
                new SeiReader(output.track(TS_STREAM_TYPE_EIA608)));
            break;
          case TS_STREAM_TYPE_ID3:
            pesPayloadReader = id3Reader;
            break;
        }

        if (pesPayloadReader != null) {
          streamTypes.put(streamType, true);
          tsPayloadReaders.put(elementaryPid, new PesReader(pesPayloadReader));
        }
      }

      output.endTracks();
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
    private static final int MAX_HEADER_EXTENSION_SIZE = 10;
    private static final int PES_SCRATCH_SIZE = 10; // max(HEADER_SIZE, MAX_HEADER_EXTENSION_SIZE)

    private final ParsableBitArray pesScratch;
    private final ElementaryStreamReader pesPayloadReader;

    private int state;
    private int bytesRead;
    private boolean bodyStarted;

    private boolean ptsFlag;
    private boolean dtsFlag;
    private boolean seenFirstDts;
    private int extendedHeaderLength;
    private int payloadSize;
    private long timeUs;

    public PesReader(ElementaryStreamReader pesPayloadReader) {
      this.pesPayloadReader = pesPayloadReader;
      pesScratch = new ParsableBitArray(new byte[PES_SCRATCH_SIZE]);
      state = STATE_FINDING_HEADER;
    }

    @Override
    public void seek() {
      state = STATE_FINDING_HEADER;
      bytesRead = 0;
      bodyStarted = false;
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
      // First 8 bits are skipped: '10' (2), PES_scrambling_control (2), PES_priority (1),
      // data_alignment_indicator (1), copyright (1), original_or_copy (1)
      pesScratch.skipBits(8);
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
      timeUs = 0;
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
          ptsTimestampAdjuster.adjustTimestamp(dts);
          seenFirstDts = true;
        }
        timeUs = ptsTimestampAdjuster.adjustTimestamp(pts);
      }
    }

  }

}
