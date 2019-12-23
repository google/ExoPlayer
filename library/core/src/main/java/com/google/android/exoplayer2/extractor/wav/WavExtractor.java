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
package com.google.android.exoplayer2.extractor.wav;

import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.audio.WavUtil;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * Extracts data from WAV byte streams.
 */
public final class WavExtractor implements Extractor {

  /**
   * When outputting PCM data to a {@link TrackOutput}, we can choose how many frames are grouped
   * into each sample, and hence each sample's duration. This is the target number of samples to
   * output for each second of media, meaning that each sample will have a duration of ~100ms.
   */
  private static final int TARGET_SAMPLES_PER_SECOND = 10;

  /** Factory for {@link WavExtractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new WavExtractor()};

  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;
  private OutputWriter outputWriter;
  private int dataStartPosition;
  private long dataEndPosition;

  public WavExtractor() {
    dataStartPosition = C.POSITION_UNSET;
    dataEndPosition = C.POSITION_UNSET;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    return WavHeaderReader.peek(input) != null;
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = output.track(0, C.TRACK_TYPE_AUDIO);
    output.endTracks();
  }

  @Override
  public void seek(long position, long timeUs) {
    if (outputWriter != null) {
      outputWriter.reset(timeUs);
    }
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    if (outputWriter == null) {
      WavHeader header = WavHeaderReader.peek(input);
      if (header == null) {
        // Should only happen if the media wasn't sniffed.
        throw new ParserException("Unsupported or unrecognized wav header.");
      }

      @C.PcmEncoding
      int pcmEncoding = WavUtil.getPcmEncodingForType(header.formatType, header.bitsPerSample);
      if (pcmEncoding == C.ENCODING_INVALID) {
        throw new ParserException("Unsupported WAV format type: " + header.formatType);
      }
      outputWriter = new PcmOutputWriter(extractorOutput, trackOutput, header, pcmEncoding);
    }

    if (dataStartPosition == C.POSITION_UNSET) {
      Pair<Long, Long> dataBounds = WavHeaderReader.skipToData(input);
      dataStartPosition = dataBounds.first.intValue();
      dataEndPosition = dataBounds.second;
      outputWriter.init(dataStartPosition, dataEndPosition);
    } else if (input.getPosition() == 0) {
      input.skipFully(dataStartPosition);
    }

    Assertions.checkState(dataEndPosition != C.POSITION_UNSET);
    long bytesLeft = dataEndPosition - input.getPosition();
    return outputWriter.sampleData(input, bytesLeft) ? RESULT_END_OF_INPUT : RESULT_CONTINUE;
  }

  /** Writes to the extractor's output. */
  private interface OutputWriter {

    /**
     * Resets the writer.
     *
     * @param timeUs The new start position in microseconds.
     */
    void reset(long timeUs);

    /**
     * Initializes the writer.
     *
     * <p>Must be called once, before any calls to {@link #sampleData(ExtractorInput, long)}.
     *
     * @param dataStartPosition The byte position (inclusive) in the stream at which data starts.
     * @param dataEndPosition The end position (exclusive) in the stream at which data ends.
     * @throws ParserException If an error occurs initializing the writer.
     */
    void init(int dataStartPosition, long dataEndPosition) throws ParserException;

    /**
     * Consumes sample data from {@code input}, writing corresponding samples to the extractor's
     * output.
     *
     * <p>Must not be called until after {@link #init(int, long)} has been called.
     *
     * @param input The input from which to read.
     * @param bytesLeft The number of sample data bytes left to be read from the input.
     * @return Whether the end of the sample data has been reached.
     * @throws IOException If an error occurs reading from the input.
     * @throws InterruptedException If the thread has been interrupted.
     */
    boolean sampleData(ExtractorInput input, long bytesLeft)
        throws IOException, InterruptedException;
  }

  private static final class PcmOutputWriter implements OutputWriter {

    private final ExtractorOutput extractorOutput;
    private final TrackOutput trackOutput;
    private final WavHeader header;
    private final @C.PcmEncoding int pcmEncoding;
    private final int targetSampleSize;

    private long startTimeUs;
    private long outputFrameCount;
    private int pendingBytes;

    public PcmOutputWriter(
        ExtractorOutput extractorOutput,
        TrackOutput trackOutput,
        WavHeader header,
        @C.PcmEncoding int pcmEncoding) {
      this.extractorOutput = extractorOutput;
      this.trackOutput = trackOutput;
      this.header = header;
      this.pcmEncoding = pcmEncoding;
      // For PCM blocks correspond to single frames. This is validated in init(int, long).
      int bytesPerFrame = header.blockSize;
      targetSampleSize =
          Math.max(bytesPerFrame, header.frameRateHz * bytesPerFrame / TARGET_SAMPLES_PER_SECOND);
    }

    @Override
    public void reset(long timeUs) {
      startTimeUs = timeUs;
      outputFrameCount = 0;
      pendingBytes = 0;
    }

    @Override
    public void init(int dataStartPosition, long dataEndPosition) throws ParserException {
      // Validate the header.
      int bytesPerFrame = header.numChannels * header.bitsPerSample / 8;
      if (header.blockSize != bytesPerFrame) {
        throw new ParserException(
            "Expected block size: " + bytesPerFrame + "; got: " + header.blockSize);
      }

      // Output the seek map.
      extractorOutput.seekMap(
          new WavSeekMap(header, /* framesPerBlock= */ 1, dataStartPosition, dataEndPosition));

      // Output the format.
      Format format =
          Format.createAudioSampleFormat(
              /* id= */ null,
              MimeTypes.AUDIO_RAW,
              /* codecs= */ null,
              /* bitrate= */ header.averageBytesPerSecond * 8,
              targetSampleSize,
              header.numChannels,
              header.frameRateHz,
              pcmEncoding,
              /* initializationData= */ null,
              /* drmInitData= */ null,
              /* selectionFlags= */ 0,
              /* language= */ null);
      trackOutput.format(format);
    }

    @Override
    public boolean sampleData(ExtractorInput input, long bytesLeft)
        throws IOException, InterruptedException {
      // Write sample data until we've reached the target sample size, or the end of the data.
      boolean endOfSampleData = bytesLeft == 0;
      while (!endOfSampleData && pendingBytes < targetSampleSize) {
        int bytesToRead = (int) Math.min(targetSampleSize - pendingBytes, bytesLeft);
        int bytesAppended = trackOutput.sampleData(input, bytesToRead, true);
        if (bytesAppended == RESULT_END_OF_INPUT) {
          endOfSampleData = true;
        } else {
          pendingBytes += bytesAppended;
        }
      }

      // Write the corresponding sample metadata. Samples must be a whole number of frames. It's
      // possible pendingBytes is not a whole number of frames if the stream ended unexpectedly.
      int bytesPerFrame = header.blockSize;
      int pendingFrames = pendingBytes / bytesPerFrame;
      if (pendingFrames > 0) {
        long timeUs =
            startTimeUs
                + Util.scaleLargeTimestamp(
                    outputFrameCount, C.MICROS_PER_SECOND, header.frameRateHz);
        int size = pendingFrames * bytesPerFrame;
        int offset = pendingBytes - size;
        trackOutput.sampleMetadata(
            timeUs, C.BUFFER_FLAG_KEY_FRAME, size, offset, /* encryptionData= */ null);
        outputFrameCount += pendingFrames;
        pendingBytes = offset;
      }

      return endOfSampleData;
    }
  }
}
