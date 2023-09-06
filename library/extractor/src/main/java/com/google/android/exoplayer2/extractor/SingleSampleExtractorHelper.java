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
package com.google.android.exoplayer2.extractor;

import static com.google.android.exoplayer2.C.BUFFER_FLAG_KEY_FRAME;
import static com.google.android.exoplayer2.extractor.Extractor.RESULT_CONTINUE;
import static com.google.android.exoplayer2.extractor.Extractor.RESULT_END_OF_INPUT;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Extracts data by loading all the bytes into one sample.
 *
 * <p>Used as a component in other extractors.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class SingleSampleExtractorHelper {

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
   * Returns whether the {@link ExtractorInput} has the given {@code fileSignature}.
   *
   * <p>@see Extractor#sniff(ExtractorInput)
   */
  public boolean sniff(ExtractorInput input, int fileSignature, int fileSignatureLength)
      throws IOException {
    ParsableByteArray scratch = new ParsableByteArray(fileSignatureLength);
    input.peekFully(scratch.getData(), /* offset= */ 0, fileSignatureLength);
    return scratch.readUnsignedShort() == fileSignature;
  }

  /**
   * See {@link Extractor#init(ExtractorOutput)}.
   *
   * <p>Outputs format with {@code containerMimeType}.
   */
  public void init(ExtractorOutput output, String containerMimeType) {
    extractorOutput = output;
    outputImageTrackAndSeekMap(containerMimeType);
  }

  /** See {@link Extractor#seek(long, long)}. */
  public void seek(long position) {
    if (position == 0 || state == STATE_READING) {
      state = STATE_READING;
      size = 0;
    }
  }

  /** See {@link Extractor#read(ExtractorInput, PositionHolder)}. */
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
  private void outputImageTrackAndSeekMap(String containerMimeType) {
    trackOutput = extractorOutput.track(IMAGE_TRACK_ID, C.TRACK_TYPE_IMAGE);
    trackOutput.format(
        new Format.Builder()
            .setContainerMimeType(containerMimeType)
            .setTileCountHorizontal(1)
            .setTileCountVertical(1)
            .build());
    extractorOutput.endTracks();
    extractorOutput.seekMap(new SingleSampleSeekMap(/* durationUs= */ C.TIME_UNSET));
    state = STATE_READING;
  }
}
