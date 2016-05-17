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
package com.google.android.exoplayer.extractor.wav;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.Format;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.MimeTypes;

import java.io.IOException;

/** {@link Extractor} to extract samples from a WAV byte stream. */
public final class WavExtractor implements Extractor, SeekMap {

  /** Arbitrary maximum input size of 32KB, which is ~170ms of 16-bit stereo PCM audio at 48KHz. */
  private static final int MAX_INPUT_SIZE = 32 * 1024;

  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;
  private WavHeader wavHeader;
  private int bytesPerFrame;
  private int pendingBytes;

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    return WavHeaderReader.peek(input) != null;
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = output.track(0);
    wavHeader = null;
    output.endTracks();
  }

  @Override
  public void seek() {
    pendingBytes = 0;
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    if (wavHeader == null) {
      wavHeader = WavHeaderReader.peek(input);
      if (wavHeader == null) {
        // Should only happen if the media wasn't sniffed.
        throw new ParserException("Unsupported or unrecognized wav header.");
      }
      Format format = Format.createAudioSampleFormat(null, MimeTypes.AUDIO_RAW,
          wavHeader.getBitrate(), MAX_INPUT_SIZE, wavHeader.getNumChannels(),
          wavHeader.getSampleRateHz(), wavHeader.getEncoding(), null, null, 0, null);
      trackOutput.format(format);
      bytesPerFrame = wavHeader.getBytesPerFrame();
    }

    if (!wavHeader.hasDataBounds()) {
      WavHeaderReader.skipToData(input, wavHeader);
      extractorOutput.seekMap(this);
    }

    int bytesAppended = trackOutput.sampleData(input, MAX_INPUT_SIZE - pendingBytes, true);
    if (bytesAppended != RESULT_END_OF_INPUT) {
      pendingBytes += bytesAppended;
    }

    // Samples must consist of a whole number of frames.
    int pendingFrames = pendingBytes / bytesPerFrame;
    if (pendingFrames > 0) {
      long timeUs = wavHeader.getTimeUs(input.getPosition() - pendingBytes);
      int size = pendingFrames * bytesPerFrame;
      pendingBytes -= size;
      trackOutput.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, size, pendingBytes, null);
    }

    return bytesAppended == RESULT_END_OF_INPUT ? RESULT_END_OF_INPUT : RESULT_CONTINUE;
  }

  // SeekMap implementation.

  @Override
  public long getDurationUs() {
    return wavHeader.getDurationUs();
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getPosition(long timeUs) {
    return wavHeader.getPosition(timeUs);
  }
}
