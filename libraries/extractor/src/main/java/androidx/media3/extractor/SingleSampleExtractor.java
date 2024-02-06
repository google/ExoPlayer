/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.extractor;

import static androidx.media3.common.C.BUFFER_FLAG_KEY_FRAME;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.mp4.Mp4Extractor;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Extracts data by loading all the bytes into one sample. */
@UnstableApi
public final class SingleSampleExtractor implements Extractor {

  private final int fileSignature;
  private final int fileSignatureLength;
  private final String sampleMimeType;

  /** Parser states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({STATE_READING, STATE_ENDED})
  private @interface State {}

  private static final int STATE_READING = 1;
  private static final int STATE_ENDED = 2;

  /**
   * The identifier to use for the image track. Chosen to avoid colliding with track IDs used by
   * {@link Mp4Extractor} for motion photos.
   */
  public static final int IMAGE_TRACK_ID = 1024;

  private static final int FIXED_READ_LENGTH = 1024;

  private int size;
  private @State int state;
  private @MonotonicNonNull ExtractorOutput extractorOutput;
  private @MonotonicNonNull TrackOutput trackOutput;

  /**
   * Creates an instance.
   *
   * @param fileSignature The file signature used to {@link #sniff}, or {@link C#INDEX_UNSET} if the
   *     method won't be used.
   * @param fileSignatureLength The length of file signature, or {@link C#LENGTH_UNSET} if the
   *     {@link #sniff} method won't be used.
   * @param sampleMimeType The mime type of the sample.
   */
  public SingleSampleExtractor(int fileSignature, int fileSignatureLength, String sampleMimeType) {
    this.fileSignature = fileSignature;
    this.fileSignatureLength = fileSignatureLength;
    this.sampleMimeType = sampleMimeType;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    checkState(fileSignature != C.INDEX_UNSET && fileSignatureLength != C.LENGTH_UNSET);
    ParsableByteArray scratch = new ParsableByteArray(fileSignatureLength);
    input.peekFully(scratch.getData(), /* offset= */ 0, fileSignatureLength);
    return scratch.readUnsignedShort() == fileSignature;
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    outputImageTrackAndSeekMap(sampleMimeType);
  }

  @Override
  public @Extractor.ReadResult int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException {
    switch (state) {
      case STATE_READING:
        readSegment(input);
        return RESULT_CONTINUE;
      case STATE_ENDED:
        return RESULT_END_OF_INPUT;
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  public void seek(long position, long timeUs) {
    if (position == 0 || state == STATE_READING) {
      state = STATE_READING;
      size = 0;
    }
  }

  @Override
  public void release() {
    // Do Nothing
  }

  private void readSegment(ExtractorInput input) throws IOException {
    int result =
        checkNotNull(trackOutput).sampleData(input, FIXED_READ_LENGTH, /* allowEndOfInput= */ true);
    if (result == C.RESULT_END_OF_INPUT) {
      state = STATE_ENDED;
      @C.BufferFlags int flags = BUFFER_FLAG_KEY_FRAME;
      trackOutput.sampleMetadata(
          /* timeUs= */ 0, flags, size, /* offset= */ 0, /* cryptoData= */ null);
      size = 0;
    } else {
      size += result;
    }
  }

  @RequiresNonNull("this.extractorOutput")
  private void outputImageTrackAndSeekMap(String sampleMimeType) {
    trackOutput = extractorOutput.track(IMAGE_TRACK_ID, C.TRACK_TYPE_IMAGE);
    trackOutput.format(new Format.Builder().setSampleMimeType(sampleMimeType).build());
    extractorOutput.endTracks();
    extractorOutput.seekMap(new SingleSampleSeekMap(/* durationUs= */ C.TIME_UNSET));
    state = STATE_READING;
  }
}
