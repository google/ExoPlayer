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

import com.google.android.exoplayer.extractor.DummyTrackOutput;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.util.ParsableBitArray;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import java.io.IOException;

/**
 * Facilitates the extraction of data from the MPEG-2 TS container format.
 */
public final class PsExtractor implements Extractor {

  private static final String TAG = "PsExtractor";

  private static final int PACK_START_CODE = 0x000001BA;
  private static final int SYSTEM_HEADER_START_CODE = 0x000001BB;
  private static final int PACKET_START_CODE_PREFIX = 0x000001;
  private static final int MPEG_PROGRAM_END_CODE = 0x000001B9;
  // Read 1MB of data trying to find all of the PES streams, or just stop
  // after we found both audio & video.
  private static final long MAX_SEARCH_LENGTH = 1024*1024;

  public static final int PRIVATE_STREAM_1 = 0xBD;
  public static final int AUDIO_STREAM = 0xC0;
  public static final int AUDIO_STREAM_MASK = 0xE0;
  public static final int VIDEO_STREAM = 0xE0;
  public static final int VIDEO_STREAM_MASK = 0xF0;

  private final PtsTimestampAdjuster ptsTimestampAdjuster;
  private ParsableByteArray psPacketBuffer;
  private final ParsableBitArray psScratch;
  private boolean foundAllTracks;
  private boolean foundAudioTrack;
  private boolean foundVideoTrack;
  /* package */ final SparseArray<PsPayloadReader> psPayloadReaders; // Indexed by pid
  /* package */ final SparseBooleanArray streamTypes;

  // Accessed only by the loading thread.
  private ExtractorOutput output;

  public PsExtractor() {
    this(new PtsTimestampAdjuster(0));
  }

  public PsExtractor(PtsTimestampAdjuster ptsTimestampAdjuster) {
    this.ptsTimestampAdjuster = ptsTimestampAdjuster;
    psPacketBuffer = new ParsableByteArray(4096);
    psScratch = new ParsableBitArray(new byte[64]);
    psPayloadReaders = new SparseArray<>();
    streamTypes = new SparseBooleanArray();
  }

  // Extractor implementation.

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    byte[] scratch = new byte[4];
    input.peekFully(scratch, 0, 4);
    return PACK_START_CODE == (((scratch[0] & 0xFF) << 24) | ((scratch[1] & 0xFF) << 16) | ((scratch[2] & 0xFF) << 8) | (scratch[3] & 0xFF));
  }

  @Override
  public void init(ExtractorOutput output) {
    this.output = output;
    output.seekMap(SeekMap.UNSEEKABLE);
  }

  @Override
  public void seek() {
    ptsTimestampAdjuster.reset();
    for (int i = 0; i < psPayloadReaders.size(); i++) {
      psPayloadReaders.valueAt(i).seek();
    }
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    // First read and check for the PACK_START_CODE
    if (!input.readFully(psPacketBuffer.data, 0, 4, true)) {
      return RESULT_END_OF_INPUT;
    }

    psPacketBuffer.setPosition(0);
    int nextStartCode = psPacketBuffer.readInt();
    if (nextStartCode == MPEG_PROGRAM_END_CODE)
      return RESULT_END_OF_INPUT; // I've never actually seen one of these for real
    else if (nextStartCode != PACK_START_CODE) {
      Log.w(TAG, "Corrupt PS stream; missing PACK_START_CODE");
      return RESULT_CONTINUE;
    }

    // Now read the rest of the pack_header
    if (!input.readFully(psPacketBuffer.data, 0, 10, true)) {
      return RESULT_END_OF_INPUT;
    }

    // We only care about the pack_stuffing_length in here, skip the first 77 bits.
    psPacketBuffer.setPosition(0);
    psPacketBuffer.skipBytes(9);
    // Last 3 bits is the length
    int pack_stuffing_length = psPacketBuffer.readUnsignedByte() & 0x07;

    // Now skip the stuffing
    if (pack_stuffing_length > 0) {
      if (!input.skipFully(pack_stuffing_length, true)) {
        return RESULT_END_OF_INPUT;
      }
    }

    // Read the next header code to see if its for PES or the system_header
    if (!input.peekFully(psPacketBuffer.data, 0, 4, true)) {
      return RESULT_END_OF_INPUT;
    }
    psPacketBuffer.setPosition(0);
    nextStartCode = psPacketBuffer.readInt();
    boolean alreadyPeeked = true;
    if (nextStartCode == SYSTEM_HEADER_START_CODE) {
      // We just skip all this, but we need to get the length first
      if (!input.readFully(psPacketBuffer.data, 0, 6, true)) {
        return RESULT_END_OF_INPUT;
      }
      // Length is the next 2 bytes (after the 4 byte start code)
      psPacketBuffer.setPosition(4);
      int system_header_length = psPacketBuffer.readUnsignedShort();
      if (!input.skipFully(system_header_length, true)) {
        return RESULT_END_OF_INPUT;
      }
      alreadyPeeked = false;
    }

    // Now we consume packets as long as they start with the PES start code
    do {
      if (!alreadyPeeked && !input.peekFully(psPacketBuffer.data, 0, 4, true)) {
        return RESULT_END_OF_INPUT;
      }
      alreadyPeeked = false;
      psPacketBuffer.setPosition(0);
      int startCodePrefix = psPacketBuffer.readUnsignedInt24();
      // This really should be a valid packet_start_code_prefix
      if (startCodePrefix != PACKET_START_CODE_PREFIX) {
        Log.w(TAG, "Missing PACKET_START_CODE_PREFIX!!");
        break;
      }
      psPacketBuffer.setPosition(0);
      nextStartCode = psPacketBuffer.readInt();
      if (nextStartCode == PACK_START_CODE) {
        break; // we consumed our pack, we are done
      }
      else if (nextStartCode == MPEG_PROGRAM_END_CODE)
        return RESULT_END_OF_INPUT;

      // Now we should have a complete PES packet ready to consume, including
      // the header. The first 6 bytes have the start code(24), stream_id(8), and
      // the length (16).
      if (!input.readFully(psPacketBuffer.data, 0, 6, true)) {
        return RESULT_END_OF_INPUT;
      }
      psPacketBuffer.setPosition(3); // skip the start code we already verified
      int stream_id = psPacketBuffer.readUnsignedByte();
      // Check to see if we care about this stream_id or not
      // Check to see if we have this one in our map yet, and if not, then add it
      PsPayloadReader payloadReader = psPayloadReaders.get(stream_id);
      if (payloadReader == null) {
        if (stream_id == PRIVATE_STREAM_1 && !streamTypes.get(stream_id)) {
          // Private stream, used for AC3 audio
          // NOTE: This may need further parsing to determine if its DTS,
          // but that's likely only valid for DVDs
          psPayloadReaders.put(stream_id, payloadReader = new PesReader(new Ac3Reader(output.track(stream_id), false)));
          foundAudioTrack = true;
          streamTypes.put(stream_id, true);
          Log.w(TAG, "Setup payload reader for AC3");
        } else {
          // Now check for audio/video stream
          if ((stream_id & AUDIO_STREAM_MASK) == AUDIO_STREAM) {
            if (!streamTypes.get(AUDIO_STREAM)) {
              psPayloadReaders.put(stream_id, payloadReader = new PesReader(new MpegAudioReader(output.track(stream_id))));
              streamTypes.put(AUDIO_STREAM, true);
              foundAudioTrack = true;
              Log.w(TAG, "Setup payload reader for MP2");
            }
          } else if ((stream_id & VIDEO_STREAM_MASK) == VIDEO_STREAM) {
            if (!streamTypes.get(VIDEO_STREAM)) {
              psPayloadReaders.put(stream_id, payloadReader = new PesReader(new H262Reader(output.track(stream_id))));
              streamTypes.put(VIDEO_STREAM, true);
              foundVideoTrack = true;
              Log.w(TAG, "Setup payload reader for MPEG2Video");
            }
          }
        }
      }
      if (!foundAllTracks) {
        if ((foundAudioTrack && foundVideoTrack) || input.getPosition() > MAX_SEARCH_LENGTH) {
          foundAllTracks = true;
          output.endTracks();
          Log.w(TAG, "Signalled that all tracks were found");
        }
      }
      int payload_length = psPacketBuffer.readUnsignedShort();
      if (payloadReader == null) {
        // Just skip this data
        if (!input.skipFully(payload_length, true)) {
          return RESULT_END_OF_INPUT;
        }
      } else {
        if (psPacketBuffer.capacity() < payload_length) {
          // Reallocate for this and future packets
          psPacketBuffer = new ParsableByteArray(payload_length);
        }
        if (!input.readFully(psPacketBuffer.data, 0, payload_length, true)) {
          return RESULT_END_OF_INPUT;
        }
        psPacketBuffer.setPosition(0);
        psPacketBuffer.setLimit(payload_length);
        payloadReader.consume(psPacketBuffer, output);
        psPacketBuffer.setLimit(psPacketBuffer.capacity());
      }
    } while (true);

    return RESULT_CONTINUE;
  }

  // Internals.

  /**
   * Parses PS packet payload data.
   */
  private abstract static class PsPayloadReader {

    /**
     * Notifies the reader that a seek has occurred.
     * <p>
     * Following a call to this method, the data passed to the next invocation of
     * {@link #consume(ParsableByteArray, boolean, ExtractorOutput)} will not be a continuation of
     * the data that was previously passed. Hence the reader should reset any internal state.
     */
    public abstract void seek();

    /**
     * Consumes the payload of a PS packet.
     *
     * @param data The PES packet. The position will be set to the start of the payload.
     * @param output The output to which parsed data should be written.
     */
    public abstract void consume(ParsableByteArray data, ExtractorOutput output);

  }

  /**
   * Parses PES packet data and extracts samples.
   */
  private class PesReader extends PsPayloadReader {

    private static final int HEADER_SIZE = 9;
    private static final int MAX_HEADER_EXTENSION_SIZE = 10;
    private static final int PES_SCRATCH_SIZE = 64; // max(HEADER_SIZE, MAX_HEADER_EXTENSION_SIZE)

    private final ParsableBitArray pesScratch;
    private final ElementaryStreamReader pesPayloadReader;

    private boolean ptsFlag;
    private boolean dtsFlag;
    private boolean seenFirstDts;
    private int extendedHeaderLength;
    private long timeUs;

    public PesReader(ElementaryStreamReader pesPayloadReader) {
      this.pesPayloadReader = pesPayloadReader;
      pesScratch = new ParsableBitArray(new byte[PES_SCRATCH_SIZE]);
    }

    @Override
    public void seek() {
      seenFirstDts = false;
      pesPayloadReader.seek();
    }

    @Override
    public void consume(ParsableByteArray data, ExtractorOutput output) {
      data.readBytes(pesScratch.data, 0, 3);
      pesScratch.setPosition(0);
      parseHeader();
      //payloadSize = data.limit() - HEADER_SIZE - extendedHeaderLength;
      data.readBytes(pesScratch.data, 0, extendedHeaderLength);
      pesScratch.setPosition(0);
      parseHeaderExtension();
      // I don't think we need to exclude the stuffing_byte(s) because there's
      // no indicator of how they actually are supposed to be and I don't
      // think they ever show up in PS mode. The decoder should handle them
      // gracefully anyways.
      /*int readLength = data.bytesLeft();
      int padding = readLength - payloadSize;
      if (padding > 0) {
        readLength -= padding;
        data.setLimit(data.getPosition() + readLength);
      }*/
      pesPayloadReader.consume(data, timeUs, true);
      // We always have complete PES packets with program stream
      pesPayloadReader.packetFinished();
    }

    private void parseHeader() {
      // Note: see ISO/IEC 13818-1, section 2.4.3.6 for detailed information on the format of
      // the header.
      // First 8 bits are skipped: '10' (2), PES_scrambling_control (2), PES_priority (1),
      // data_alignment_indicator (1), copyright (1), original_or_copy (1)
      pesScratch.skipBits(8);
      ptsFlag = pesScratch.readBit();
      dtsFlag = pesScratch.readBit();
      // ESCR_flag (1), ES_rate_flag (1), DSM_trick_mode_flag (1),
      // additional_copy_info_flag (1), PES_CRC_flag (1), PES_extension_flag (1)
      pesScratch.skipBits(6);
      extendedHeaderLength = pesScratch.readBits(8);
    }

    private void parseHeaderExtension() {
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
