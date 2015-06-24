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
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;
import com.google.android.exoplayer.util.Util;

import java.io.EOFException;
import java.io.IOException;
import java.util.Collections;

/**
 * Extracts data from an MP3 file.
 */
public final class Mp3Extractor implements Extractor {

  /** The maximum number of bytes to search when synchronizing, before giving up. */
  private static final int MAX_BYTES_TO_SEARCH = 128 * 1024;

  /** Mask that includes the audio header values that must match between frames. */
  private static final int HEADER_MASK = 0xFFFE0C00;
  private static final int ID3_TAG = Util.getIntegerCodeForString("ID3");
  private static final String[] MIME_TYPE_BY_LAYER =
      new String[] {MimeTypes.AUDIO_MPEG_L1, MimeTypes.AUDIO_MPEG_L2, MimeTypes.AUDIO_MPEG};
  private static final int XING_HEADER = Util.getIntegerCodeForString("Xing");
  private static final int INFO_HEADER = Util.getIntegerCodeForString("Info");
  private static final int VBRI_HEADER = Util.getIntegerCodeForString("VBRI");

  /**
   * Theoretical maximum frame size for an MPEG audio stream, which occurs when playing a Layer 2
   * MPEG 2.5 audio stream at 16 kb/s (with padding). The size is 1152 sample/frame *
   * 160000 bit/s / (8000 sample/s * 8 bit/byte) + 1 padding byte/frame = 2881 byte/frame.
   * The next power of two size is 4 KiB.
   */
  private static final int MAX_FRAME_SIZE_BYTES = 4096;

  private final BufferingInput inputBuffer;
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

  /** Constructs a new {@link Mp3Extractor}. */
  public Mp3Extractor() {
    inputBuffer = new BufferingInput(MAX_FRAME_SIZE_BYTES * 3);
    scratch = new ParsableByteArray(4);
    synchronizedHeader = new MpegAudioHeader();
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
    inputBuffer.reset();
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
        basisTimeUs = seeker.getTimeUs(getPosition(extractorInput, inputBuffer));
      }
      sampleBytesRemaining = synchronizedHeader.frameSize;
    }

    long timeUs = basisTimeUs + (samplesRead * 1000000L / synchronizedHeader.sampleRate);

    // Start by draining any buffered bytes, then read directly from the extractor input.
    sampleBytesRemaining -= inputBuffer.drainToOutput(trackOutput, sampleBytesRemaining);
    if (sampleBytesRemaining > 0) {
      inputBuffer.mark();

      // Return if we still need more data.
      sampleBytesRemaining -= trackOutput.sampleData(extractorInput, sampleBytesRemaining);
      if (sampleBytesRemaining > 0) {
        return RESULT_CONTINUE;
      }
    }

    trackOutput.sampleMetadata(timeUs, C.SAMPLE_FLAG_SYNC, synchronizedHeader.frameSize, 0, null);
    samplesRead += synchronizedHeader.samplesPerFrame;
    sampleBytesRemaining = 0;
    return RESULT_CONTINUE;
  }

  /** Attempts to read an MPEG audio header at the current offset, resynchronizing if necessary. */
  private long maybeResynchronize(ExtractorInput extractorInput)
      throws IOException, InterruptedException {
    inputBuffer.mark();
    if (!inputBuffer.readAllowingEndOfInput(extractorInput, scratch.data, 0, 4)) {
      return RESULT_END_OF_INPUT;
    }
    inputBuffer.returnToMark();

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
    inputBuffer.skip(extractorInput, 1);
    return synchronizeCatchingEndOfInput(extractorInput);
  }

  private long synchronizeCatchingEndOfInput(ExtractorInput extractorInput)
      throws IOException, InterruptedException {
    // An EOFException will be raised if any read operation was partially satisfied. If a seek
    // operation resulted in reading from within the last frame, we may try to read past the end of
    // the file in a partially-satisfied read operation, so we need to catch the exception.
    try {
      return synchronize(extractorInput);
    } catch (EOFException e) {
      return RESULT_END_OF_INPUT;
    }
  }

  private long synchronize(ExtractorInput extractorInput) throws IOException, InterruptedException {
    if (extractorInput.getPosition() == 0) {
      // Before preparation completes, retrying loads from the start, so clear any buffered data.
      inputBuffer.reset();
    } else {
      // After preparation completes, retrying resumes loading from the old position, so return to
      // the start of buffered data to parse it again.
      inputBuffer.returnToMark();
    }

    long startPosition = getPosition(extractorInput, inputBuffer);

    // Skip any ID3 header at the start of the file.
    if (startPosition == 0) {
      inputBuffer.read(extractorInput, scratch.data, 0, 3);
      scratch.setPosition(0);
      if (scratch.readUnsignedInt24() == ID3_TAG) {
        extractorInput.skipFully(3);
        extractorInput.readFully(scratch.data, 0, 4);
        int headerLength = ((scratch.data[0] & 0x7F) << 21) | ((scratch.data[1] & 0x7F) << 14)
            | ((scratch.data[2] & 0x7F) << 7) | (scratch.data[3] & 0x7F);
        extractorInput.skipFully(headerLength);
        inputBuffer.reset();
        startPosition = getPosition(extractorInput, inputBuffer);
      } else {
        inputBuffer.returnToMark();
      }
    }

    // Try to find four consecutive valid MPEG audio frames.
    inputBuffer.mark();
    long headerPosition = startPosition;
    int validFrameCount = 0;
    int candidateSynchronizedHeaderData = 0;
    while (true) {
      if (headerPosition - startPosition >= MAX_BYTES_TO_SEARCH) {
        throw new ParserException("Searched too many bytes while resynchronizing.");
      }

      if (!inputBuffer.readAllowingEndOfInput(extractorInput, scratch.data, 0, 4)) {
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
        inputBuffer.returnToMark();
        inputBuffer.skip(extractorInput, 1);
        inputBuffer.mark();
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
      inputBuffer.skip(extractorInput, frameSize - 4);
    }

    // The input buffer read position is now synchronized.
    inputBuffer.returnToMark();
    synchronizedHeaderData = candidateSynchronizedHeaderData;
    if (seeker == null) {
      setupSeeker(extractorInput, headerPosition);
      extractorOutput.seekMap(seeker);
      trackOutput.format(MediaFormat.createAudioFormat(
          MIME_TYPE_BY_LAYER[synchronizedHeader.layerIndex], MAX_FRAME_SIZE_BYTES,
          seeker.getDurationUs(), synchronizedHeader.channels, synchronizedHeader.sampleRate,
          Collections.<byte[]>emptyList()));
    }

    return headerPosition;
  }

  /**
   * Sets {@link #seeker} to seek using metadata from {@link #inputBuffer}, which should have its
   * position set to the start of the first frame in the stream. On returning,
   * {@link #inputBuffer}'s position and mark will be set to the start of the first frame of audio.
   *
   * @param extractorInput Source of data for {@link #inputBuffer}.
   * @param headerPosition Position (byte offset) of the synchronized header in the stream.
   * @throws IOException Thrown if there was an error reading from the stream. Not expected if the
   *     next two frames were already read during synchronization.
   * @throws InterruptedException Thrown if reading from the stream was interrupted. Not expected if
   *     the next two frames were already read during synchronization.
   */
  private void setupSeeker(ExtractorInput extractorInput, long headerPosition)
      throws IOException, InterruptedException {
    // Try to set up seeking based on a XING or VBRI header.
    if (parseSeekerFrame(extractorInput, headerPosition, extractorInput.getLength())) {
      // Discard the parsed header so we start reading from the first audio frame.
      inputBuffer.mark();
      if (seeker != null) {
        return;
      }

      // If there was a header but it was not usable, synchronize to the next frame so we don't
      // use an invalid bitrate for CBR seeking. This read is guaranteed to succeed if the frame was
      // already read during synchronization.
      inputBuffer.read(extractorInput, scratch.data, 0, 4);
      scratch.setPosition(0);
      headerPosition += synchronizedHeader.frameSize;
      MpegAudioHeader.populateHeader(scratch.readInt(), synchronizedHeader);
    }

    inputBuffer.returnToMark();
    seeker = new ConstantBitrateSeeker(headerPosition, synchronizedHeader.bitrate * 1000,
        extractorInput.getLength());
  }

  /**
   * Consumes the frame at {@link #inputBuffer}'s current position, advancing it to the next frame.
   * The mark is not modified. {@link #seeker} will be assigned based on seeking metadata in the
   * frame. If there is no seeking metadata, returns {@code false} and sets {@link #seeker} to null.
   * If seeking metadata is present and unusable, returns {@code true} and sets {@link #seeker} to
   * null. Otherwise, returns {@code true} and assigns {@link #seeker}.
   */
  private boolean parseSeekerFrame(ExtractorInput extractorInput, long headerPosition,
      long inputLength) throws IOException, InterruptedException {
    // Read the first frame so it can be parsed for seeking metadata.
    inputBuffer.mark();
    seeker = null;
    ParsableByteArray frame =
        inputBuffer.getParsableByteArray(extractorInput, synchronizedHeader.frameSize);

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

  /** Returns the reading position of {@code bufferingInput} relative to the extractor's stream. */
  private static long getPosition(ExtractorInput extractorInput, BufferingInput bufferingInput) {
    return extractorInput.getPosition() - bufferingInput.getAvailableByteCount();
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
