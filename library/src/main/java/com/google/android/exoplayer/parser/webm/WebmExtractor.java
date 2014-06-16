/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.parser.webm;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.parser.SegmentIndex;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.LongArray;
import com.google.android.exoplayer.util.MimeTypes;

import android.annotation.TargetApi;
import android.media.MediaExtractor;

import java.util.Arrays;

/**
 * Facilitates the extraction of data from the WebM container format with a
 * non-blocking, incremental parser based on {@link EbmlReader}.
 *
 * <p>WebM is a subset of the EBML elements defined for Matroska. More information about EBML and
 * Matroska is available <a href="http://www.matroska.org/technical/specs/index.html">here</a>.
 * More info about WebM is <a href="http://www.webmproject.org/code/specs/container/">here</a>.
 */
@TargetApi(16)
public final class WebmExtractor extends EbmlReader {

  private static final String DOC_TYPE_WEBM = "webm";
  private static final String CODEC_ID_VP9 = "V_VP9";
  private static final int UNKNOWN = -1;

  // Element IDs
  private static final int ID_EBML = 0x1A45DFA3;
  private static final int ID_EBML_READ_VERSION = 0x42F7;
  private static final int ID_DOC_TYPE = 0x4282;
  private static final int ID_DOC_TYPE_READ_VERSION = 0x4285;

  private static final int ID_SEGMENT = 0x18538067;

  private static final int ID_INFO = 0x1549A966;
  private static final int ID_TIMECODE_SCALE = 0x2AD7B1;
  private static final int ID_DURATION = 0x4489;

  private static final int ID_CLUSTER = 0x1F43B675;
  private static final int ID_TIME_CODE = 0xE7;
  private static final int ID_SIMPLE_BLOCK = 0xA3;

  private static final int ID_TRACKS = 0x1654AE6B;
  private static final int ID_TRACK_ENTRY = 0xAE;
  private static final int ID_CODEC_ID = 0x86;
  private static final int ID_VIDEO = 0xE0;
  private static final int ID_PIXEL_WIDTH = 0xB0;
  private static final int ID_PIXEL_HEIGHT = 0xBA;

  private static final int ID_CUES = 0x1C53BB6B;
  private static final int ID_CUE_POINT = 0xBB;
  private static final int ID_CUE_TIME = 0xB3;
  private static final int ID_CUE_TRACK_POSITIONS = 0xB7;
  private static final int ID_CUE_CLUSTER_POSITION = 0xF1;

  // SimpleBlock Lacing Values
  private static final int LACING_NONE = 0;
  private static final int LACING_XIPH = 1;
  private static final int LACING_FIXED = 2;
  private static final int LACING_EBML = 3;

  private final byte[] simpleBlockTimecodeAndFlags = new byte[3];

  private SampleHolder tempSampleHolder;
  private boolean sampleRead;

  private boolean prepared = false;
  private long segmentStartPosition = UNKNOWN;
  private long segmentEndPosition = UNKNOWN;
  private long timecodeScale = 1000000L;
  private long durationUs = UNKNOWN;
  private int pixelWidth = UNKNOWN;
  private int pixelHeight = UNKNOWN;
  private int cuesByteSize = UNKNOWN;
  private long clusterTimecodeUs = UNKNOWN;
  private long simpleBlockTimecodeUs = UNKNOWN;
  private MediaFormat format;
  private SegmentIndex cues;
  private LongArray cueTimesUs;
  private LongArray cueClusterPositions;

  public WebmExtractor() {
    cueTimesUs = new LongArray();
    cueClusterPositions = new LongArray();
  }

  /**
   * Whether the has parsed the cues and sample format from the stream.
   *
   * @return True if the extractor is prepared. False otherwise.
   */
  public boolean isPrepared() {
    return prepared;
  }

  /**
   * Consumes data from a {@link NonBlockingInputStream}.
   *
   * <p>If the return value is {@code false}, then a sample may have been partially read into
   * {@code sampleHolder}. Hence the same {@link SampleHolder} instance must be passed
   * in subsequent calls until the whole sample has been read.
   *
   * @param inputStream The input stream from which data should be read.
   * @param sampleHolder A {@link SampleHolder} into which the sample should be read.
   * @return {@code true} if a sample has been read into the sample holder, otherwise {@code false}.
   */
  public boolean read(NonBlockingInputStream inputStream, SampleHolder sampleHolder) {
    tempSampleHolder = sampleHolder;
    sampleRead = false;
    super.read(inputStream);
    tempSampleHolder = null;
    return sampleRead;
  }

  /**
   * Seeks to a position before or equal to the requested time.
   *
   * @param seekTimeUs The desired seek time in microseconds.
   * @param allowNoop Allow the seek operation to do nothing if the seek time is in the current
   *     segment, is equal to or greater than the time of the current sample, and if there does not
   *     exist a sync frame between these two times.
   * @return True if the operation resulted in a change of state. False if it was a no-op.
   */
  public boolean seekTo(long seekTimeUs, boolean allowNoop) {
    checkPrepared();
    if (allowNoop && simpleBlockTimecodeUs != UNKNOWN && seekTimeUs >= simpleBlockTimecodeUs) {
      final int clusterIndex = Arrays.binarySearch(cues.timesUs, clusterTimecodeUs);
      if (clusterIndex >= 0 && seekTimeUs < clusterTimecodeUs + cues.durationsUs[clusterIndex]) {
        return false;
      }
    }
    reset();
    return true;
  }

  /**
   * Returns the cues for the media stream.
   *
   * @return The cues in the form of a {@link SegmentIndex}, or null if the extractor is not yet
   *     prepared.
   */
  public SegmentIndex getCues() {
    checkPrepared();
    return cues;
  }

  /**
   * Returns the format of the samples contained within the media stream.
   *
   * @return The sample media format, or null if the extracted is not yet prepared.
   */
  public MediaFormat getFormat() {
    checkPrepared();
    return format;
  }

  @Override
  protected int getElementType(int id) {
    switch (id) {
      case ID_EBML:
      case ID_SEGMENT:
      case ID_INFO:
      case ID_CLUSTER:
      case ID_TRACKS:
      case ID_TRACK_ENTRY:
      case ID_VIDEO:
      case ID_CUES:
      case ID_CUE_POINT:
      case ID_CUE_TRACK_POSITIONS:
        return EbmlReader.TYPE_MASTER;
      case ID_EBML_READ_VERSION:
      case ID_DOC_TYPE_READ_VERSION:
      case ID_TIMECODE_SCALE:
      case ID_TIME_CODE:
      case ID_PIXEL_WIDTH:
      case ID_PIXEL_HEIGHT:
      case ID_CUE_TIME:
      case ID_CUE_CLUSTER_POSITION:
        return EbmlReader.TYPE_UNSIGNED_INT;
      case ID_DOC_TYPE:
      case ID_CODEC_ID:
        return EbmlReader.TYPE_STRING;
      case ID_SIMPLE_BLOCK:
        return EbmlReader.TYPE_BINARY;
      case ID_DURATION:
        return EbmlReader.TYPE_FLOAT;
      default:
        return EbmlReader.TYPE_UNKNOWN;
    }
  }

  @Override
  protected boolean onMasterElementStart(
      int id, long elementOffset, int headerSize, int contentsSize) {
    switch (id) {
      case ID_SEGMENT:
        if (segmentStartPosition != UNKNOWN || segmentEndPosition != UNKNOWN) {
          throw new IllegalStateException("Multiple Segment elements not supported");
        }
        segmentStartPosition = elementOffset + headerSize;
        segmentEndPosition = elementOffset + headerSize + contentsSize;
        break;
      case ID_CUES:
        cuesByteSize = headerSize + contentsSize;
        break;
    }
    return true;
  }

  @Override
  protected boolean onMasterElementEnd(int id) {
    switch (id) {
      case ID_CUES:
        finishPreparing();
        return false;
    }
    return true;
  }

  @Override
  protected boolean onIntegerElement(int id, long value) {
    switch (id) {
      case ID_EBML_READ_VERSION:
        // Validate that EBMLReadVersion is supported. This extractor only supports v1.
        if (value != 1) {
          throw new IllegalStateException("EBMLReadVersion " + value + " not supported");
        }
        break;
      case ID_DOC_TYPE_READ_VERSION:
        // Validate that DocTypeReadVersion is supported. This extractor only supports up to v2.
        if (value < 1 || value > 2) {
          throw new IllegalStateException("DocTypeReadVersion " + value + " not supported");
        }
        break;
      case ID_TIMECODE_SCALE:
        timecodeScale = value;
        break;
      case ID_PIXEL_WIDTH:
        pixelWidth = (int) value;
        break;
      case ID_PIXEL_HEIGHT:
        pixelHeight = (int) value;
        break;
      case ID_CUE_TIME:
        cueTimesUs.add(scaleTimecodeToUs(value));
        break;
      case ID_CUE_CLUSTER_POSITION:
        cueClusterPositions.add(value);
        break;
      case ID_TIME_CODE:
        clusterTimecodeUs = scaleTimecodeToUs(value);
        break;
    }
    return true;
  }

  @Override
  protected boolean onFloatElement(int id, double value) {
    switch (id) {
      case ID_DURATION:
        durationUs = scaleTimecodeToUs(value);
        break;
    }
    return true;
  }

  @Override
  protected boolean onStringElement(int id, String value) {
    switch (id) {
      case ID_DOC_TYPE:
        // Validate that DocType is supported. This extractor only supports "webm".
        if (!DOC_TYPE_WEBM.equals(value)) {
          throw new IllegalStateException("DocType " + value + " not supported");
        }
        break;
      case ID_CODEC_ID:
        // Validate that CodecID is supported. This extractor only supports "V_VP9".
        if (!CODEC_ID_VP9.equals(value)) {
          throw new IllegalStateException("CodecID " + value + " not supported");
        }
        break;
    }
    return true;
  }

  @Override
  protected boolean onBinaryElement(NonBlockingInputStream inputStream,
      int id, long elementOffset, int headerSize, int contentsSize) {
    switch (id) {
      case ID_SIMPLE_BLOCK:
        // Please refer to http://www.matroska.org/technical/specs/index.html#simpleblock_structure
        // for info about how data is organized in a SimpleBlock element.

        // Value of trackNumber is not used but needs to be read.
        readVarint(inputStream);

        // Next three bytes have timecode and flags.
        readBytes(inputStream, simpleBlockTimecodeAndFlags, 3);

        // First two bytes of the three are the relative timecode.
        final int timecode =
            (simpleBlockTimecodeAndFlags[0] << 8) | (simpleBlockTimecodeAndFlags[1] & 0xff);
        final long timecodeUs = scaleTimecodeToUs(timecode);

        // Last byte of the three has some flags and the lacing value.
        final boolean keyframe = (simpleBlockTimecodeAndFlags[2] & 0x80) == 0x80;
        final boolean invisible = (simpleBlockTimecodeAndFlags[2] & 0x08) == 0x08;
        final int lacing = (simpleBlockTimecodeAndFlags[2] & 0x06) >> 1;
        //final boolean discardable = (simpleBlockTimecodeAndFlags[2] & 0x01) == 0x01; // Not used.

        // Validate lacing and set info into sample holder.
        switch (lacing) {
          case LACING_NONE:
            final long elementEndOffset = elementOffset + headerSize + contentsSize;
            simpleBlockTimecodeUs = clusterTimecodeUs + timecodeUs;
            tempSampleHolder.flags = keyframe ? MediaExtractor.SAMPLE_FLAG_SYNC : 0;
            tempSampleHolder.decodeOnly = invisible;
            tempSampleHolder.timeUs = clusterTimecodeUs + timecodeUs;
            tempSampleHolder.size = (int) (elementEndOffset - getBytesRead());
            break;
          case LACING_EBML:
          case LACING_FIXED:
          case LACING_XIPH:
          default:
            throw new IllegalStateException("Lacing mode " + lacing + " not supported");
        }

        // Read video data into sample holder.
        readBytes(inputStream, tempSampleHolder.data, tempSampleHolder.size);
        sampleRead = true;
        return false;
      default:
        skipBytes(inputStream, contentsSize);
    }
    return true;
  }

  private long scaleTimecodeToUs(long unscaledTimecode) {
    return (unscaledTimecode * timecodeScale) / 1000L;
  }

  private long scaleTimecodeToUs(double unscaledTimecode) {
    return (long) ((unscaledTimecode * timecodeScale) / 1000.0);
  }

  private void checkPrepared() {
    if (!prepared) {
      throw new IllegalStateException("Parser not yet prepared");
    }
  }

  private void finishPreparing() {
    if (prepared
        || segmentStartPosition == UNKNOWN || segmentEndPosition == UNKNOWN
        || durationUs == UNKNOWN
        || pixelWidth == UNKNOWN || pixelHeight == UNKNOWN
        || cuesByteSize == UNKNOWN
        || cueTimesUs.size() == 0 || cueTimesUs.size() != cueClusterPositions.size()) {
      throw new IllegalStateException("Incorrect state in finishPreparing()");
    }

    format = MediaFormat.createVideoFormat(MimeTypes.VIDEO_VP9, MediaFormat.NO_VALUE, pixelWidth,
        pixelHeight, null);

    final int cuePointsSize = cueTimesUs.size();
    final int sizeBytes = cuesByteSize;
    final int[] sizes = new int[cuePointsSize];
    final long[] offsets = new long[cuePointsSize];
    final long[] durationsUs = new long[cuePointsSize];
    final long[] timesUs = new long[cuePointsSize];
    for (int i = 0; i < cuePointsSize; i++) {
      timesUs[i] = cueTimesUs.get(i);
      offsets[i] = segmentStartPosition + cueClusterPositions.get(i);
    }
    for (int i = 0; i < cuePointsSize - 1; i++) {
      sizes[i] = (int) (offsets[i + 1] - offsets[i]);
      durationsUs[i] = timesUs[i + 1] - timesUs[i];
    }
    sizes[cuePointsSize - 1] = (int) (segmentEndPosition - offsets[cuePointsSize - 1]);
    durationsUs[cuePointsSize - 1] = durationUs - timesUs[cuePointsSize - 1];
    cues = new SegmentIndex(sizeBytes, sizes, offsets, durationsUs, timesUs);
    cueTimesUs = null;
    cueClusterPositions = null;

    prepared = true;
  }

}
