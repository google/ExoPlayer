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
package com.google.android.exoplayer2.ext.flac;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.FlacStreamInfo;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Facilitates the extraction of data from the FLAC container format.
 */
public final class FlacExtractor implements Extractor {

  /**
   * Factory that returns one extractor which is a {@link FlacExtractor}.
   */
  public static final ExtractorsFactory FACTORY = new ExtractorsFactory() {

    @Override
    public Extractor[] createExtractors() {
      return new Extractor[] {new FlacExtractor()};
    }

  };

  /**
   * FLAC signature: first 4 is the signature word, second 4 is the sizeof STREAMINFO. 0x22 is the
   * mandatory STREAMINFO.
   */
  private static final byte[] FLAC_SIGNATURE = {'f', 'L', 'a', 'C', 0, 0, 0, 0x22};

  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;

  private FlacDecoderJni decoderJni;

  private boolean metadataParsed;

  private ParsableByteArray outputBuffer;
  private ByteBuffer outputByteBuffer;

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = extractorOutput.track(0, C.TRACK_TYPE_AUDIO);
    extractorOutput.endTracks();
    try {
      decoderJni = new FlacDecoderJni();
    } catch (FlacDecoderException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    byte[] header = new byte[FLAC_SIGNATURE.length];
    input.peekFully(header, 0, FLAC_SIGNATURE.length);
    return Arrays.equals(header, FLAC_SIGNATURE);
  }

  @Override
  public int read(final ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    decoderJni.setData(input);

    if (!metadataParsed) {
      final FlacStreamInfo streamInfo;
      try {
        streamInfo = decoderJni.decodeMetadata();
        if (streamInfo == null) {
          throw new IOException("Metadata decoding failed");
        }
      } catch (IOException e) {
        decoderJni.reset(0);
        input.setRetryPosition(0, e);
        throw e; // never executes
      }
      metadataParsed = true;

      extractorOutput.seekMap(new SeekMap() {
        final boolean isSeekable = decoderJni.getSeekPosition(0) != -1;
        final long durationUs = streamInfo.durationUs();

        @Override
        public boolean isSeekable() {
          return isSeekable;
        }

        @Override
        public long getPosition(long timeUs) {
          return isSeekable ? decoderJni.getSeekPosition(timeUs) : 0;
        }

        @Override
        public long getDurationUs() {
          return durationUs;
        }

      });

      Format mediaFormat = Format.createAudioSampleFormat(null, MimeTypes.AUDIO_RAW, null,
          streamInfo.bitRate(), Format.NO_VALUE, streamInfo.channels, streamInfo.sampleRate,
          C.ENCODING_PCM_16BIT, null, null, 0, null);
      trackOutput.format(mediaFormat);

      outputBuffer = new ParsableByteArray(streamInfo.maxDecodedFrameSize());
      outputByteBuffer = ByteBuffer.wrap(outputBuffer.data);
    }

    outputBuffer.reset();
    long lastDecodePosition = decoderJni.getDecodePosition();
    int size;
    try {
      size = decoderJni.decodeSample(outputByteBuffer);
    } catch (IOException e) {
      if (lastDecodePosition >= 0) {
        decoderJni.reset(lastDecodePosition);
        input.setRetryPosition(lastDecodePosition, e);
      }
      throw e;
    }
    if (size <= 0) {
      return RESULT_END_OF_INPUT;
    }
    trackOutput.sampleData(outputBuffer, size);
    trackOutput.sampleMetadata(decoderJni.getLastSampleTimestamp(), C.BUFFER_FLAG_KEY_FRAME, size,
        0, null);

    return decoderJni.isEndOfData() ? RESULT_END_OF_INPUT : RESULT_CONTINUE;
  }

  @Override
  public void seek(long position, long timeUs) {
    if (position == 0) {
      metadataParsed = false;
    }
    if (decoderJni != null) {
      decoderJni.reset(position);
    }
  }

  @Override
  public void release() {
    if (decoderJni != null) {
      decoderJni.release();
      decoderJni = null;
    }
  }

}
