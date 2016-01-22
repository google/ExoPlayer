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
package com.google.android.exoplayer.extractor.mp3;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.MpegAudioHeader;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import java.io.EOFException;
import java.io.IOException;

/**
 * Extracts data from an MP3 file.
 */
public final class Mp3Extractor implements Extractor {

  /** The maximum number of bytes to search when synchronizing, before giving up. */
  private static final int MAX_SYNC_BYTES = 128 * 1024;
  /** The maximum number of bytes to read when sniffing, excluding the header, before giving up. */
  private static final int MAX_SNIFF_BYTES = 4 * 1024;

  /** Mask that includes the audio header values that must match between frames. */
  private static final int HEADER_MASK = 0xFFFE0C00;
  private static final int ID3_TAG = Util.getIntegerCodeForString("ID3");
  private static final int XING_HEADER = Util.getIntegerCodeForString("Xing");
  private static final int INFO_HEADER = Util.getIntegerCodeForString("Info");
  private static final int VBRI_HEADER = Util.getIntegerCodeForString("VBRI");

  private final long forcedFirstSampleTimestampUs;
  private final ParsableByteArray scratch;
  private final MpegAudioHeader synchronizedHeader;

  // Extractor outputs.
  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;

  private int synchronizedHeaderData;

  private Seeker seeker;
  private long basisTimeUs;
  private int samplesRead;
  private int sampleBytesRemaining;

  /**
   * Constructs a new {@link Mp3Extractor}.
   */
  public Mp3Extractor() {
    this(-1);
  }

  /**
   * Constructs a new {@link Mp3Extractor}.
   *
   * @param forcedFirstSampleTimestampUs A timestamp to force for the first sample, or -1 if forcing
   *     is not required.
   */
  public Mp3Extractor(long forcedFirstSampleTimestampUs) {
    this.forcedFirstSampleTimestampUs = forcedFirstSampleTimestampUs;
    scratch = new ParsableByteArray(4);
    synchronizedHeader = new MpegAudioHeader();
    basisTimeUs = -1;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    ParsableByteArray scratch = new ParsableByteArray(4);
    int startPosition = 0;
    while (true) {
      input.peekFully(scratch.data, 0, 3);
      scratch.setPosition(0);
      if (scratch.readUnsignedInt24() != ID3_TAG) {
        break;
      }
      input.advancePeekPosition(3);
      input.peekFully(scratch.data, 0, 4);
      int headerLength = ((scratch.data[0] & 0x7F) << 21) | ((scratch.data[1] & 0x7F) << 14)
          | ((scratch.data[2] & 0x7F) << 7) | (scratch.data[3] & 0x7F);
      input.advancePeekPosition(headerLength);
      startPosition += 3 + 3 + 4 + headerLength;
    }
    input.resetPeekPosition();
    input.advancePeekPosition(startPosition);

    // Try to find four consecutive valid MPEG audio frames.
    int headerPosition = startPosition;
    int validFrameCount = 0;
    int candidateSynchronizedHeaderData = 0;
    while (true) {
      if (headerPosition - startPosition >= MAX_SNIFF_BYTES) {
        return false;
      }

      input.peekFully(scratch.data, 0, 4);
      scratch.setPosition(0);
      int headerData = scratch.readInt();
      int frameSize;
      if ((candidateSynchronizedHeaderData != 0
          && (headerData & HEADER_MASK) != (candidateSynchronizedHeaderData & HEADER_MASK))
          || (frameSize = MpegAudioHeader.getFrameSize(headerData)) == -1) {
        validFrameCount = 0;
        candidateSynchronizedHeaderData = 0;

        // Try reading a header starting at the next byte.
        input.resetPeekPosition();
        input.advancePeekPosition(++headerPosition);
        continue;
      }

      if (validFrameCount == 0) {
        candidateSynchronizedHeaderData = headerData;
      }

      // The header was valid and matching (if appropriate). Check another or end synchronization.
      if (++validFrameCount == 4) {
        return true;
      }

      // Look for more headers.
      input.advancePeekPosition(frameSize - 4);
    }
  }

  @Override
  public void init(ExtractorOutput extractorOutput) {
    this.extractorOutput = extractorOutput;
    trackOutput = extractorOutput.track(0);
    extractorOutput.endTracks();
  }

  @Override
  public void seek() {
    synchronizedHeaderData = 0;
    samplesRead = 0;
    basisTimeUs = -1;
    sampleBytesRemaining = 0;
  }

  @Override
  public int read(ExtractorInput extractorInput, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    if (synchronizedHeaderData == 0
        && synchronizeCatchingEndOfInput(extractorInput) == RESULT_END_OF_INPUT) {
      return RESULT_END_OF_INPUT;
    }

    return readSample(extractorInput);
  }

  private int readSample(ExtractorInput extractorInput) throws IOException, InterruptedException {
    if (sampleBytesRemaining == 0) {
      long headerPosition = maybeResynchronize(extractorInput);
      if (headerPosition == RESULT_END_OF_INPUT) {
        return RESULT_END_OF_INPUT;
      }
      if (basisTimeUs == -1) {
        basisTimeUs = seeker.getTimeUs(extractorInput.getPosition());
        if (forcedFirstSampleTimestampUs != -1) {
          long embeddedFirstSampleTimestampUs = seeker.getTimeUs(0);
          basisTimeUs += forcedFirstSampleTimestampUs - embeddedFirstSampleTimestampUs;
        }
      }
      sampleBytesRemaining = synchronizedHeader.frameSize;
    }
    int bytesAppended = trackOutput.sampleData(extractorInput, sampleBytesRemaining, true);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      return RESULT_END_OF_INPUT;
    }
    sampleBytesRemaining -= bytesAppended;
    if (sampleBytesRemaining > 0) {
      return RESULT_CONTINUE;
    }
    long timeUs = basisTimeUs + (samplesRead * C.MICROS_PER_SECOND / synchronizedHeader.sampleRate);
    trackOutput.sampleMetadata(timeUs, C.SAMPLE_FLAG_SYNC, synchronizedHeader.frameSize, 0, null);
    samplesRead += synchronizedHeader.samplesPerFrame;
    sampleBytesRemaining = 0;
    return RESULT_CONTINUE;
  }

  /**
   * Attempts to read an MPEG audio header at the current offset, resynchronizing if necessary.
   */
  private long maybeResynchronize(ExtractorInput extractorInput)
      throws IOException, InterruptedException {
    extractorInput.resetPeekPosition();
    if (!extractorInput.peekFully(scratch.data, 0, 4, true)) {
      return RESULT_END_OF_INPUT;
    }

    scratch.setPosition(0);
    int sampleHeaderData = scratch.readInt();
    if ((sampleHeaderData & HEADER_MASK) == (synchronizedHeaderData & HEADER_MASK)) {
      int frameSize = MpegAudioHeader.getFrameSize(sampleHeaderData);
      if (frameSize != -1) {
        MpegAudioHeader.populateHeader(sampleHeaderData, synchronizedHeader);
        return RESULT_CONTINUE;
      }
    }

    synchronizedHeaderData = 0;
    extractorInput.skipFully(1);
    return synchronizeCatchingEndOfInput(extractorInput);
  }

  private long synchronizeCatchingEndOfInput(ExtractorInput extractorInput)
      throws IOException, InterruptedException {
    // An EOFException will be raised if any peek operation was partially satisfied. If a seek
    // operation resulted in reading from within the last frame, we may try to peek past the end of
    // the file in a partially-satisfied read operation, so we need to catch the exception.
    try {
      return synchronize(extractorInput);
    } catch (EOFException e) {
      return RESULT_END_OF_INPUT;
    }
  }

  private long synchronize(ExtractorInput extractorInput) throws IOException, InterruptedException {
    // TODO: Deduplicate with sniff().
    extractorInput.resetPeekPosition();
    long startPosition = extractorInput.getPosition();
    if (startPosition == 0) {
      // Skip any ID3 headers at the start of the file.
      while (true) {
        extractorInput.peekFully(scratch.data, 0, 3);
        scratch.setPosition(0);
        if (scratch.readUnsignedInt24() != ID3_TAG) {
          break;
        }
        extractorInput.skipFully(3 + 2 + 1); // "ID3", version, flags
        extractorInput.readFully(scratch.data, 0, 4);
        int headerLength = ((scratch.data[0] & 0x7F) << 21) | ((scratch.data[1] & 0x7F) << 14)
            | ((scratch.data[2] & 0x7F) << 7) | (scratch.data[3] & 0x7F);
        extractorInput.skipFully(headerLength);
        startPosition = extractorInput.getPosition();
      }
      extractorInput.resetPeekPosition();
    }

    // Try to find four consecutive valid MPEG audio frames.
    long headerPosition = startPosition;
    int validFrameCount = 0;
    int candidateSynchronizedHeaderData = 0;
    while (true) {
      if (headerPosition - startPosition >= MAX_SYNC_BYTES) {
        throw new ParserException("Searched too many bytes while resynchronizing.");
      }

      if (!extractorInput.peekFully(scratch.data, 0, 4, true)) {
        return RESULT_END_OF_INPUT;
      }

      scratch.setPosition(0);
      int headerData = scratch.readInt();
      int frameSize;
      if ((candidateSynchronizedHeaderData != 0
          && (headerData & HEADER_MASK) != (candidateSynchronizedHeaderData & HEADER_MASK))
          || (frameSize = MpegAudioHeader.getFrameSize(headerData)) == -1) {
        validFrameCount = 0;
        candidateSynchronizedHeaderData = 0;

        // Try reading a header starting at the next byte.
        extractorInput.skipFully(1);
        headerPosition++;
        continue;
      }

      if (validFrameCount == 0) {
        MpegAudioHeader.populateHeader(headerData, synchronizedHeader);
        candidateSynchronizedHeaderData = headerData;
      }

      // The header was valid and matching (if appropriate). Check another or end synchronization.
      validFrameCount++;
      if (validFrameCount == 4) {
        break;
      }

      // Look for more headers.
      extractorInput.advancePeekPosition(frameSize - 4);
    }

    // The read position is now synchronized.
    extractorInput.resetPeekPosition();
    synchronizedHeaderData = candidateSynchronizedHeaderData;
    if (seeker == null) {
      setupSeeker(extractorInput, headerPosition);
      extractorOutput.seekMap(seeker);
      trackOutput.format(MediaFormat.createAudioFormat(null, synchronizedHeader.mimeType,
          MediaFormat.NO_VALUE, MpegAudioHeader.MAX_FRAME_SIZE_BYTES, seeker.getDurationUs(),
          synchronizedHeader.channels, synchronizedHeader.sampleRate, null, null));
    }

    return headerPosition;
  }

  /**
   * Sets {@link #seeker} to seek using metadata read from {@code extractorInput}, which should
   * provide data from the start of the first frame in the stream. On returning, the input's
   * position will be set to the start of the first frame of audio.
   *
   * @param extractorInput The {@link ExtractorInput} from which to read.
   * @param headerPosition The position (byte offset) of the synchronized header in the stream.
   * @throws IOException Thrown if there was an error reading from the stream. Not expected if the
   *     next two frames were already peeked during synchronization.
   * @throws InterruptedException Thrown if reading from the stream was interrupted. Not expected if
   *     the next two frames were already peeked during synchronization.
   */
  private void setupSeeker(ExtractorInput extractorInput, long headerPosition)
      throws IOException, InterruptedException {
    // Try to set up seeking based on a Xing or VBRI header.
    ParsableByteArray frame = new ParsableByteArray(synchronizedHeader.frameSize);
    extractorInput.peekFully(frame.data, 0, synchronizedHeader.frameSize);
    if (parseSeekerFrame(frame, headerPosition, extractorInput.getLength())) {
      extractorInput.skipFully(synchronizedHeader.frameSize);
      if (seeker != null) {
        return;
      }

      // If there was a header but it was not usable, synchronize to the next frame so we don't use
      // an invalid bitrate for CBR seeking. Peeking is guaranteed to succeed if the frame was
      // already read during synchronization.
      headerPosition += synchronizedHeader.frameSize;
      extractorInput.peekFully(scratch.data, 0, 4);
      scratch.setPosition(0);
      MpegAudioHeader.populateHeader(scratch.readInt(), synchronizedHeader);
    }
    seeker = new ConstantBitrateSeeker(headerPosition, synchronizedHeader.bitrate * 1000,
        extractorInput.getLength());
  }

  /**
   * Tries to read seeking metadata from the given {@code frame}. If there is no seeking metadata,
   * returns {@code false} and sets {@link #seeker} to null. If seeking metadata is present and
   * unusable, returns {@code true} and sets {@link #seeker} to null. Otherwise, returns
   * {@code true} and assigns {@link #seeker}.
   */
  private boolean parseSeekerFrame(ParsableByteArray frame, long headerPosition, long inputLength) {
    seeker = null;

    // Check if there is a XING header.
    int xingBase;
    if ((synchronizedHeader.version & 1) == 1) {
      // MPEG 1.
      if (synchronizedHeader.channels != 1) {
        xingBase = 32;
      } else {
        xingBase = 17;
      }
    } else {
      // MPEG 2 or 2.5.
      if (synchronizedHeader.channels != 1) {
        xingBase = 17;
      } else {
        xingBase = 9;
      }
    }
    frame.setPosition(4 + xingBase);
    int headerData = frame.readInt();
    if (headerData == XING_HEADER || headerData == INFO_HEADER) {
      seeker = XingSeeker.create(synchronizedHeader, frame, headerPosition, inputLength);
      return true;
    }

    // Check if there is a VBRI header.
    frame.setPosition(36); // MPEG audio header (4 bytes) + 32 bytes.
    headerData = frame.readInt();
    if (headerData == VBRI_HEADER) {
      seeker = VbriSeeker.create(synchronizedHeader, frame, headerPosition);
      return true;
    }

    // Neither header is present.
    return false;
  }

  /**
   * {@link SeekMap} that also allows mapping from position (byte offset) back to time, which can be
   * used to work out the new sample basis timestamp after seeking and resynchronization.
   */
  /* package */ interface Seeker extends SeekMap {

    /**
     * Maps a position (byte offset) to a corresponding sample timestamp.
     *
     * @param position A seek position (byte offset) relative to the start of the stream.
     * @return The corresponding timestamp of the next sample to be read, in microseconds.
     */
    long getTimeUs(long position);

    /** Returns the duration of the source, in microseconds. */
    long getDurationUs();

  }

}
