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
import java.io.IOException;

/**
 * Extracts data from WAV byte streams.
 */
public final class WavExtractor implements Extractor {

  /** Arbitrary maximum sample size of 32KB, which is ~170ms of 16-bit stereo PCM audio at 48KHz. */
  private static final int MAX_SAMPLE_SIZE = 32 * 1024;

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
      outputWriter.reset();
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
    if (bytesLeft <= 0) {
      return Extractor.RESULT_END_OF_INPUT;
    }

    return outputWriter.sampleData(input, bytesLeft) ? RESULT_CONTINUE : RESULT_END_OF_INPUT;
  }

  /** Writes to the extractor's output. */
  private interface OutputWriter {

    /** Resets the writer. */
    void reset();

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
     * @return True if data was consumed. False if the end of the stream has been reached.
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

    private WavSeekMap seekMap;
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
    }

    @Override
    public void reset() {
      pendingBytes = 0;
    }

    @Override
    public void init(int dataStartPosition, long dataEndPosition) throws ParserException {
      // Validate the header.
      int expectedBytesPerFrame = header.numChannels * header.bitsPerSample / 8;
      if (header.blockAlign != expectedBytesPerFrame) {
        throw new ParserException(
            "Expected block alignment: " + expectedBytesPerFrame + "; got: " + header.blockAlign);
      }

      // Output the seek map.
      seekMap =
          new WavSeekMap(header, /* samplesPerBlock= */ 1, dataStartPosition, dataEndPosition);
      extractorOutput.seekMap(seekMap);

      // Output the format.
      Format format =
          Format.createAudioSampleFormat(
              /* id= */ null,
              MimeTypes.AUDIO_RAW,
              /* codecs= */ null,
              /* bitrate= */ header.averageBytesPerSecond * 8,
              MAX_SAMPLE_SIZE,
              header.numChannels,
              header.sampleRateHz,
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
      int maxBytesToRead = (int) Math.min(MAX_SAMPLE_SIZE - pendingBytes, bytesLeft);
      int numBytesAppended = trackOutput.sampleData(input, maxBytesToRead, true);
      boolean wereBytesAppended = numBytesAppended != RESULT_END_OF_INPUT;
      if (wereBytesAppended) {
        pendingBytes += numBytesAppended;
      }

      // blockAlign is the frame size, and samples must consist of a whole number of frames.
      int bytesPerFrame = header.blockAlign;
      int pendingFrames = pendingBytes / bytesPerFrame;
      if (pendingFrames > 0) {
        long timeUs = seekMap.getTimeUs(input.getPosition() - pendingBytes);
        int size = pendingFrames * bytesPerFrame;
        pendingBytes -= size;
        trackOutput.sampleMetadata(
            timeUs, C.BUFFER_FLAG_KEY_FRAME, size, pendingBytes, /* encryptionData= */ null);
      }

      return wereBytesAppended;
    }
  }
}
