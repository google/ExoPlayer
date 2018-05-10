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

import static com.google.android.exoplayer2.util.Util.getPcmEncoding;

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.Id3Peeker;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.util.FlacStreamInfo;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
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

  /** Flags controlling the behavior of the extractor. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
    flag = true,
    value = {FLAG_DISABLE_ID3_METADATA}
  )
  public @interface Flags {}

  /**
   * Flag to disable parsing of ID3 metadata. Can be set to save memory if ID3 metadata is not
   * required.
   */
  public static final int FLAG_DISABLE_ID3_METADATA = 1;

  /**
   * FLAC signature: first 4 is the signature word, second 4 is the sizeof STREAMINFO. 0x22 is the
   * mandatory STREAMINFO.
   */
  private static final byte[] FLAC_SIGNATURE = {'f', 'L', 'a', 'C', 0, 0, 0, 0x22};

  private final Id3Peeker id3Peeker;
  private final boolean isId3MetadataDisabled;

  private FlacDecoderJni decoderJni;

  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;

  private ParsableByteArray outputBuffer;
  private ByteBuffer outputByteBuffer;

  private Metadata id3Metadata;

  private boolean metadataParsed;

  /** Constructs an instance with flags = 0. */
  public FlacExtractor() {
    this(0);
  }

  /**
   * Constructs an instance.
   *
   * @param flags Flags that control the extractor's behavior.
   */
  public FlacExtractor(int flags) {
    id3Peeker = new Id3Peeker();
    isId3MetadataDisabled = (flags & FLAG_DISABLE_ID3_METADATA) != 0;
  }

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
    if (input.getPosition() == 0) {
      id3Metadata = peekId3Data(input);
    }
    return peekFlacSignature(input);
  }

  @Override
  public int read(final ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    if (input.getPosition() == 0 && !isId3MetadataDisabled && id3Metadata == null) {
      id3Metadata = peekId3Data(input);
    }

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

      boolean isSeekable = decoderJni.getSeekPosition(0) != -1;
      extractorOutput.seekMap(
          isSeekable
              ? new FlacSeekMap(streamInfo.durationUs(), decoderJni)
              : new SeekMap.Unseekable(streamInfo.durationUs(), 0));
      Format mediaFormat =
          Format.createAudioSampleFormat(
              /* id= */ null,
              MimeTypes.AUDIO_RAW,
              /* codecs= */ null,
              streamInfo.bitRate(),
              streamInfo.maxDecodedFrameSize(),
              streamInfo.channels,
              streamInfo.sampleRate,
              getPcmEncoding(streamInfo.bitsPerSample),
              /* encoderDelay= */ 0,
              /* encoderPadding= */ 0,
              /* initializationData= */ null,
              /* drmInitData= */ null,
              /* selectionFlags= */ 0,
              /* language= */ null,
              isId3MetadataDisabled ? null : id3Metadata);
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

  /**
   * Peeks ID3 tag data (if present) at the beginning of the input.
   *
   * @return The first ID3 tag decoded into a {@link Metadata} object. May be null if ID3 tag is not
   *     present in the input.
   */
  @Nullable
  private Metadata peekId3Data(ExtractorInput input) throws IOException, InterruptedException {
    input.resetPeekPosition();
    Id3Decoder.FramePredicate id3FramePredicate =
        isId3MetadataDisabled ? Id3Decoder.NO_FRAMES_PREDICATE : null;
    return id3Peeker.peekId3Data(input, id3FramePredicate);
  }

  /**
   * Peeks from the beginning of the input to see if {@link #FLAC_SIGNATURE} is present.
   *
   * @return Whether the input begins with {@link #FLAC_SIGNATURE}.
   */
  private boolean peekFlacSignature(ExtractorInput input) throws IOException, InterruptedException {
    byte[] header = new byte[FLAC_SIGNATURE.length];
    input.peekFully(header, 0, FLAC_SIGNATURE.length);
    return Arrays.equals(header, FLAC_SIGNATURE);
  }

  private static final class FlacSeekMap implements SeekMap {

    private final long durationUs;
    private final FlacDecoderJni decoderJni;

    public FlacSeekMap(long durationUs, FlacDecoderJni decoderJni) {
      this.durationUs = durationUs;
      this.decoderJni = decoderJni;
    }

    @Override
    public boolean isSeekable() {
      return true;
    }

    @Override
    public SeekPoints getSeekPoints(long timeUs) {
      // TODO: Access the seek table via JNI to return two seek points when appropriate.
      return new SeekPoints(new SeekPoint(timeUs, decoderJni.getSeekPosition(timeUs)));
    }

    @Override
    public long getDurationUs() {
      return durationUs;
    }
  }
}
