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
package com.google.android.exoplayer.ext.flac;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.extractor.Extractor;
import com.google.android.exoplayer.extractor.ExtractorInput;
import com.google.android.exoplayer.extractor.ExtractorOutput;
import com.google.android.exoplayer.extractor.PositionHolder;
import com.google.android.exoplayer.extractor.SeekMap;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.FlacStreamInfo;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParsableByteArray;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Facilitates the extraction of data from the FLAC container format.
 */
public final class FlacExtractor implements Extractor {
  /**
   * FLAC signature: first 4 is the signature word, second 4 is the sizeof STREAMINFO. 0x22 is the
   * mandatory STREAMINFO.
   */
  private static final byte[] FLAC_SIGNATURE = {'f', 'L', 'a', 'C', 0, 0, 0, 0x22};

  private ExtractorOutput output;
  private TrackOutput trackOutput;

  private FlacJni decoder;

  private boolean metadataParsed;

  private ParsableByteArray outputBuffer;
  private ByteBuffer outputByteBuffer;

  @Override
  public void init(ExtractorOutput output) {
    this.output = output;
    this.trackOutput = output.track(0);
    output.endTracks();

    try {
      decoder = new FlacJni();
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
    decoder.setData(input);

    if (!metadataParsed) {
      FlacStreamInfo streamInfo = decoder.decodeMetadata();
      if (streamInfo == null) {
        throw new IOException("Metadata decoding failed");
      }
      metadataParsed = true;

      output.seekMap(new SeekMap() {
        final boolean isSeekable = decoder.getSeekPosition(0) != -1;

        @Override
        public boolean isSeekable() {
          return isSeekable;
        }

        @Override
        public long getPosition(long timeUs) {
          return isSeekable ? decoder.getSeekPosition(timeUs) : 0;
        }
      });

      MediaFormat mediaFormat = MediaFormat.createAudioFormat(null, MimeTypes.AUDIO_RAW,
              streamInfo.bitRate(), MediaFormat.NO_VALUE, streamInfo.durationUs(),
              streamInfo.channels, streamInfo.sampleRate, null, null, C.ENCODING_PCM_16BIT);
      trackOutput.format(mediaFormat);

      outputBuffer = new ParsableByteArray(streamInfo.maxDecodedFrameSize());
      outputByteBuffer = ByteBuffer.wrap(outputBuffer.data);
    }

    outputBuffer.reset();
    int size = decoder.decodeSample(outputByteBuffer);
    if (size <= 0) {
      return RESULT_END_OF_INPUT;
    }
    trackOutput.sampleData(outputBuffer, size);

    trackOutput.sampleMetadata(decoder.getLastSampleTimestamp(), C.SAMPLE_FLAG_SYNC, size, 0, null);

    return decoder.isEndOfData() ? RESULT_END_OF_INPUT : RESULT_CONTINUE;
  }

  @Override
  public void seek() {
    decoder.flush();
  }

  @Override
  public void release() {
    decoder.release();
    decoder = null;
  }

}
