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

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.BinarySearchSeeker.OutputFrameHolder;
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
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Facilitates the extraction of data from the FLAC container format.
 */
public final class FlacExtractor implements Extractor {

  /** Factory that returns one extractor which is a {@link FlacExtractor}. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new FlacExtractor()};

  /**
   * Flags controlling the behavior of the extractor. Possible flag value is {@link
   * #FLAG_DISABLE_ID3_METADATA}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {FLAG_DISABLE_ID3_METADATA})
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
  private final boolean id3MetadataDisabled;

  @Nullable private FlacDecoderJni decoderJni;
  @Nullable private ExtractorOutput extractorOutput;
  @Nullable private TrackOutput trackOutput;

  private boolean streamInfoDecoded;
  @Nullable private FlacStreamInfo streamInfo;
  @Nullable private ParsableByteArray outputBuffer;
  @Nullable private OutputFrameHolder outputFrameHolder;

  @Nullable private Metadata id3Metadata;
  @Nullable private FlacBinarySearchSeeker binarySearchSeeker;

  /** Constructs an instance with flags = 0. */
  public FlacExtractor() {
    this(/* flags= */ 0);
  }

  /**
   * Constructs an instance.
   *
   * @param flags Flags that control the extractor's behavior.
   */
  public FlacExtractor(int flags) {
    id3Peeker = new Id3Peeker();
    id3MetadataDisabled = (flags & FLAG_DISABLE_ID3_METADATA) != 0;
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
    if (input.getPosition() == 0 && !id3MetadataDisabled && id3Metadata == null) {
      id3Metadata = peekId3Data(input);
    }

    decoderJni.setData(input);
    try {
      decodeStreamInfo(input);

      if (binarySearchSeeker != null && binarySearchSeeker.isSeeking()) {
        return handlePendingSeek(input, seekPosition);
      }

      ByteBuffer outputByteBuffer = outputFrameHolder.byteBuffer;
      long lastDecodePosition = decoderJni.getDecodePosition();
      try {
        decoderJni.decodeSampleWithBacktrackPosition(outputByteBuffer, lastDecodePosition);
      } catch (FlacDecoderJni.FlacFrameDecodeException e) {
        throw new IOException("Cannot read frame at position " + lastDecodePosition, e);
      }
      int outputSize = outputByteBuffer.limit();
      if (outputSize == 0) {
        return RESULT_END_OF_INPUT;
      }

      outputSample(outputBuffer, outputSize, decoderJni.getLastFrameTimestamp());
      return decoderJni.isEndOfData() ? RESULT_END_OF_INPUT : RESULT_CONTINUE;
    } finally {
      decoderJni.clearData();
    }
  }

  @Override
  public void seek(long position, long timeUs) {
    if (position == 0) {
      streamInfoDecoded = false;
    }
    if (decoderJni != null) {
      decoderJni.reset(position);
    }
    if (binarySearchSeeker != null) {
      binarySearchSeeker.setSeekTargetUs(timeUs);
    }
  }

  @Override
  public void release() {
    binarySearchSeeker = null;
    if (decoderJni != null) {
      decoderJni.release();
      decoderJni = null;
    }
  }

  /**
   * Peeks ID3 tag data at the beginning of the input.
   *
   * @return The first ID3 tag {@link Metadata}, or null if an ID3 tag is not present in the input.
   */
  @Nullable
  private Metadata peekId3Data(ExtractorInput input) throws IOException, InterruptedException {
    input.resetPeekPosition();
    Id3Decoder.FramePredicate id3FramePredicate =
        id3MetadataDisabled ? Id3Decoder.NO_FRAMES_PREDICATE : null;
    return id3Peeker.peekId3Data(input, id3FramePredicate);
  }

  /**
   * Peeks from the beginning of the input to see if {@link #FLAC_SIGNATURE} is present.
   *
   * @return Whether the input begins with {@link #FLAC_SIGNATURE}.
   */
  private boolean peekFlacSignature(ExtractorInput input) throws IOException, InterruptedException {
    byte[] header = new byte[FLAC_SIGNATURE.length];
    input.peekFully(header, /* offset= */ 0, FLAC_SIGNATURE.length);
    return Arrays.equals(header, FLAC_SIGNATURE);
  }

  private void decodeStreamInfo(ExtractorInput input) throws InterruptedException, IOException {
    if (streamInfoDecoded) {
      return;
    }

    FlacStreamInfo streamInfo;
    try {
      streamInfo = decoderJni.decodeStreamInfo();
    } catch (IOException e) {
      decoderJni.reset(/* newPosition= */ 0);
      input.setRetryPosition(/* position= */ 0, e);
      throw e;
    }

    streamInfoDecoded = true;
    if (this.streamInfo == null) {
      this.streamInfo = streamInfo;
      outputSeekMap(streamInfo, input.getLength());
      outputFormat(streamInfo, id3MetadataDisabled ? null : id3Metadata);
      outputBuffer = new ParsableByteArray(streamInfo.maxDecodedFrameSize());
      outputFrameHolder = new OutputFrameHolder(ByteBuffer.wrap(outputBuffer.data));
    }
  }

  private int handlePendingSeek(ExtractorInput input, PositionHolder seekPosition)
      throws InterruptedException, IOException {
    int seekResult = binarySearchSeeker.handlePendingSeek(input, seekPosition, outputFrameHolder);
    ByteBuffer outputByteBuffer = outputFrameHolder.byteBuffer;
    if (seekResult == RESULT_CONTINUE && outputByteBuffer.limit() > 0) {
      outputSample(outputBuffer, outputByteBuffer.limit(), outputFrameHolder.timeUs);
    }
    return seekResult;
  }

  private void outputSeekMap(FlacStreamInfo streamInfo, long inputLength) {
    boolean hasSeekTable = decoderJni.getSeekPosition(/* timeUs= */ 0) != -1;
    SeekMap seekMap;
    if (hasSeekTable) {
      seekMap = new FlacSeekMap(streamInfo.durationUs(), decoderJni);
    } else if (inputLength != C.LENGTH_UNSET) {
      long firstFramePosition = decoderJni.getDecodePosition();
      binarySearchSeeker =
          new FlacBinarySearchSeeker(streamInfo, firstFramePosition, inputLength, decoderJni);
      seekMap = binarySearchSeeker.getSeekMap();
    } else {
      seekMap = new SeekMap.Unseekable(streamInfo.durationUs());
    }
    extractorOutput.seekMap(seekMap);
  }

  private void outputFormat(FlacStreamInfo streamInfo, Metadata metadata) {
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
            metadata);
    trackOutput.format(mediaFormat);
  }

  private void outputSample(ParsableByteArray sampleData, int size, long timeUs) {
    sampleData.setPosition(0);
    trackOutput.sampleData(sampleData, size);
    trackOutput.sampleMetadata(
        timeUs, C.BUFFER_FLAG_KEY_FRAME, size, /* offset= */ 0, /* encryptionData= */ null);
  }

  /** A {@link SeekMap} implementation using a SeekTable within the Flac stream. */
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
