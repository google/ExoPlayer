/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.jpeg;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.mp4.MotionPhotoMetadata;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Extracts JPEG image using the Exif format. */
public final class JpegExtractor implements Extractor {

  /** Parser states. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    STATE_READING_MARKER,
    STATE_READING_SEGMENT_LENGTH,
    STATE_READING_SEGMENT,
    STATE_SNIFFING_MOTION_PHOTO_VIDEO,
    STATE_ENDED,
  })
  private @interface State {}

  private static final int STATE_READING_MARKER = 0;
  private static final int STATE_READING_SEGMENT_LENGTH = 1;
  private static final int STATE_READING_SEGMENT = 2;
  private static final int STATE_SNIFFING_MOTION_PHOTO_VIDEO = 4;
  private static final int STATE_ENDED = 5;

  private static final int JPEG_EXIF_HEADER_LENGTH = 12;
  private static final long EXIF_HEADER = 0x45786966; // Exif
  private static final int MARKER_SOI = 0xFFD8; // Start of image marker
  private static final int MARKER_SOS = 0xFFDA; // Start of scan (image data) marker
  private static final int MARKER_APP1 = 0xFFE1; // Application data 1 marker
  private static final String HEADER_XMP_APP1 = "http://ns.adobe.com/xap/1.0/";

  private final ParsableByteArray scratch;

  private @MonotonicNonNull ExtractorOutput extractorOutput;

  @State private int state;
  private int marker;
  private int segmentLength;

  @Nullable private MotionPhotoMetadata motionPhotoMetadata;

  public JpegExtractor() {
    scratch = new ParsableByteArray(JPEG_EXIF_HEADER_LENGTH);
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    // See ITU-T.81 (1992) subsection B.1.1.3 and Exif version 2.2 (2002) subsection 4.5.4.
    input.peekFully(scratch.getData(), /* offset= */ 0, JPEG_EXIF_HEADER_LENGTH);
    if (scratch.readUnsignedShort() != MARKER_SOI || scratch.readUnsignedShort() != MARKER_APP1) {
      return false;
    }
    scratch.skipBytes(2); // Unused segment length
    return scratch.readUnsignedInt() == EXIF_HEADER && scratch.readUnsignedShort() == 0; // Exif\0\0
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
  }

  @Override
  @ReadResult
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    switch (state) {
      case STATE_READING_MARKER:
        readMarker(input);
        return RESULT_CONTINUE;
      case STATE_READING_SEGMENT_LENGTH:
        readSegmentLength(input);
        return RESULT_CONTINUE;
      case STATE_READING_SEGMENT:
        readSegment(input);
        return RESULT_CONTINUE;
      case STATE_SNIFFING_MOTION_PHOTO_VIDEO:
        if (input.getPosition() != checkNotNull(motionPhotoMetadata).videoStartPosition) {
          seekPosition.position = motionPhotoMetadata.videoStartPosition;
          return RESULT_SEEK;
        }
        sniffMotionPhotoVideo(input);
        return RESULT_CONTINUE;
      case STATE_ENDED:
        return RESULT_END_OF_INPUT;
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  public void seek(long position, long timeUs) {
    state = STATE_READING_MARKER;
  }

  @Override
  public void release() {
    // Do nothing.
  }

  private void readMarker(ExtractorInput input) throws IOException {
    scratch.reset(2);
    input.readFully(scratch.getData(), /* offset= */ 0, /* length= */ 2);
    marker = scratch.readUnsignedShort();
    if (marker == MARKER_SOS) { // Start of scan.
      if (motionPhotoMetadata != null) {
        state = STATE_SNIFFING_MOTION_PHOTO_VIDEO;
      } else {
        outputTracks();
        state = STATE_ENDED;
      }
    } else if ((marker < 0xFFD0 || marker > 0xFFD9) && marker != 0xFF01) {
      state = STATE_READING_SEGMENT_LENGTH;
    }
  }

  private void readSegmentLength(ExtractorInput input) throws IOException {
    scratch.reset(2);
    input.readFully(scratch.getData(), /* offset= */ 0, /* length= */ 2);
    segmentLength = scratch.readUnsignedShort() - 2;
    state = STATE_READING_SEGMENT;
  }

  private void readSegment(ExtractorInput input) throws IOException {
    if (marker == MARKER_APP1) {
      ParsableByteArray payload = new ParsableByteArray(segmentLength);
      input.readFully(payload.getData(), /* offset= */ 0, /* length= */ segmentLength);
      if (motionPhotoMetadata == null
          && HEADER_XMP_APP1.equals(payload.readNullTerminatedString())) {
        @Nullable String xmpString = payload.readNullTerminatedString();
        if (xmpString != null) {
          motionPhotoMetadata = getMotionPhotoMetadata(xmpString, input.getLength());
        }
      }
    } else {
      input.skipFully(segmentLength);
    }
    state = STATE_READING_MARKER;
  }

  private void sniffMotionPhotoVideo(ExtractorInput input) throws IOException {
    // Check if the file is truncated.
    boolean peekedData =
        input.peekFully(
            scratch.getData(), /* offset= */ 0, /* length= */ 1, /* allowEndOfInput= */ true);
    if (!peekedData) {
      outputTracks();
    } else {
      input.resetPeekPosition();
      long mp4StartPosition = input.getPosition();
      StartOffsetExtractorInput mp4ExtractorInput =
          new StartOffsetExtractorInput(input, mp4StartPosition);
      Mp4Extractor mp4Extractor = new Mp4Extractor();
      if (mp4Extractor.sniff(mp4ExtractorInput)) {
        outputTracks(checkNotNull(motionPhotoMetadata));
      } else {
        outputTracks();
      }
    }
    state = STATE_ENDED;
  }

  private void outputTracks(Metadata.Entry... metadataEntries) {
    TrackOutput imageTrackOutput =
        checkNotNull(extractorOutput).track(/* id= */ 0, C.TRACK_TYPE_IMAGE);
    imageTrackOutput.format(
        new Format.Builder().setMetadata(new Metadata(metadataEntries)).build());
    extractorOutput.endTracks();
    extractorOutput.seekMap(new SeekMap.Unseekable(/* durationUs= */ C.TIME_UNSET));
  }

  /**
   * Attempts to parse the specified XMP data describing the motion photo, returning the resulting
   * {@link MotionPhotoMetadata} or {@code null} if it wasn't possible to derive motion photo
   * metadata.
   *
   * @param xmpString A string of XML containing XMP motion photo metadata to attempt to parse.
   * @param inputLength The length of the input stream in bytes, or {@link C#LENGTH_UNSET} if
   *     unknown.
   * @return The {@link MotionPhotoMetadata}, or {@code null} if it wasn't possible to derive motion
   *     photo metadata.
   * @throws IOException If an error occurs parsing the XMP string.
   */
  @Nullable
  private static MotionPhotoMetadata getMotionPhotoMetadata(String xmpString, long inputLength)
      throws IOException {
    // Metadata defines offsets from the end of the stream, so we need the stream length to
    // determine start offsets.
    if (inputLength == C.LENGTH_UNSET) {
      return null;
    }

    // Motion photos have (at least) a primary image media item and a secondary video media item.
    @Nullable
    MotionPhotoDescription motionPhotoDescription =
        XmpMotionPhotoDescriptionParser.parse(xmpString);
    if (motionPhotoDescription == null) {
      return null;
    }
    return motionPhotoDescription.getMotionPhotoMetadata(inputLength);
  }
}
