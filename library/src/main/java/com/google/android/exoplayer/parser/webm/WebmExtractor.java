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
import com.google.android.exoplayer.parser.Extractor;
import com.google.android.exoplayer.parser.SegmentIndex;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;
import com.google.android.exoplayer.util.LongArray;
import com.google.android.exoplayer.util.MimeTypes;

import android.annotation.TargetApi;
import android.media.MediaExtractor;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * An extractor to facilitate data retrieval from the WebM container format.
 *
 * <p>WebM is a subset of the EBML elements defined for Matroska. More information about EBML and
 * Matroska is available <a href="http://www.matroska.org/technical/specs/index.html">here</a>.
 * More info about WebM is <a href="http://www.webmproject.org/code/specs/container/">here</a>.
 */
@TargetApi(16)
public final class WebmExtractor implements Extractor {

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

  private static final int READ_TERMINATING_RESULTS = RESULT_NEED_MORE_DATA | RESULT_END_OF_STREAM
      | RESULT_READ_SAMPLE | RESULT_NEED_SAMPLE_HOLDER;

  private final EbmlReader reader;
  private final byte[] simpleBlockTimecodeAndFlags = new byte[3];

  private SampleHolder sampleHolder;
  private int readResults;

  private long segmentStartOffsetBytes = UNKNOWN;
  private long segmentEndOffsetBytes = UNKNOWN;
  private long timecodeScale = 1000000L;
  private long durationUs = UNKNOWN;
  private int pixelWidth = UNKNOWN;
  private int pixelHeight = UNKNOWN;
  private long cuesSizeBytes = UNKNOWN;
  private long clusterTimecodeUs = UNKNOWN;
  private long simpleBlockTimecodeUs = UNKNOWN;
  private MediaFormat format;
  private SegmentIndex cues;
  private LongArray cueTimesUs;
  private LongArray cueClusterPositions;

  public WebmExtractor() {
    this(new DefaultEbmlReader());
  }

  /* package */ WebmExtractor(EbmlReader reader) {
    this.reader = reader;
    this.reader.setEventHandler(new InnerEbmlEventHandler());
  }

  @Override
  public int read(NonBlockingInputStream inputStream, SampleHolder sampleHolder) {
    this.sampleHolder = sampleHolder;
    this.readResults = 0;
    while ((readResults & READ_TERMINATING_RESULTS) == 0) {
      int ebmlReadResult = reader.read(inputStream);
      if (ebmlReadResult == EbmlReader.READ_RESULT_NEED_MORE_DATA) {
        readResults |= WebmExtractor.RESULT_NEED_MORE_DATA;
      } else if (ebmlReadResult == EbmlReader.READ_RESULT_END_OF_STREAM) {
        readResults |= WebmExtractor.RESULT_END_OF_STREAM;
      }
    }
    this.sampleHolder = null;
    return readResults;
  }

  @Override
  public boolean seekTo(long seekTimeUs, boolean allowNoop) {
    if (allowNoop
        && cues != null
        && clusterTimecodeUs != UNKNOWN
        && simpleBlockTimecodeUs != UNKNOWN
        && seekTimeUs >= simpleBlockTimecodeUs) {
      int clusterIndex = Arrays.binarySearch(cues.timesUs, clusterTimecodeUs);
      if (clusterIndex >= 0 && seekTimeUs < clusterTimecodeUs + cues.durationsUs[clusterIndex]) {
        return false;
      }
    }
    clusterTimecodeUs = UNKNOWN;
    simpleBlockTimecodeUs = UNKNOWN;
    reader.reset();
    return true;
  }

  @Override
  public SegmentIndex getIndex() {
    return cues;
  }

  @Override
  public boolean hasRelativeIndexOffsets() {
    return false;
  }

  @Override
  public MediaFormat getFormat() {
    return format;
  }

  @Override
  public Map<UUID, byte[]> getPsshInfo() {
    // TODO: Parse pssh data from Webm streams.
    return null;
  }

  /* package */ int getElementType(int id) {
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

  /* package */ boolean onMasterElementStart(
      int id, long elementOffsetBytes, int headerSizeBytes, long contentsSizeBytes) {
    switch (id) {
      case ID_SEGMENT:
        if (segmentStartOffsetBytes != UNKNOWN || segmentEndOffsetBytes != UNKNOWN) {
          throw new IllegalStateException("Multiple Segment elements not supported");
        }
        segmentStartOffsetBytes = elementOffsetBytes + headerSizeBytes;
        segmentEndOffsetBytes = elementOffsetBytes + headerSizeBytes + contentsSizeBytes;
        break;
      case ID_CUES:
        cuesSizeBytes = headerSizeBytes + contentsSizeBytes;
        cueTimesUs = new LongArray();
        cueClusterPositions = new LongArray();
        break;
      default:
        // pass
    }
    return true;
  }

  /* package */ boolean onMasterElementEnd(int id) {
    switch (id) {
      case ID_CUES:
        buildCues();
        return false;
      case ID_VIDEO:
        buildFormat();
        return true;
      default:
        return true;
    }
  }

  /* package */ boolean onIntegerElement(int id, long value) {
    switch (id) {
      case ID_EBML_READ_VERSION:
        // Validate that EBMLReadVersion is supported. This extractor only supports v1.
        if (value != 1) {
          throw new IllegalArgumentException("EBMLReadVersion " + value + " not supported");
        }
        break;
      case ID_DOC_TYPE_READ_VERSION:
        // Validate that DocTypeReadVersion is supported. This extractor only supports up to v2.
        if (value < 1 || value > 2) {
          throw new IllegalArgumentException("DocTypeReadVersion " + value + " not supported");
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
      default:
        // pass
    }
    return true;
  }

  /* package */ boolean onFloatElement(int id, double value) {
    if (id == ID_DURATION) {
      durationUs = scaleTimecodeToUs((long) value);
    }
    return true;
  }

  /* package */ boolean onStringElement(int id, String value) {
    switch (id) {
      case ID_DOC_TYPE:
        // Validate that DocType is supported. This extractor only supports "webm".
        if (!DOC_TYPE_WEBM.equals(value)) {
          throw new IllegalArgumentException("DocType " + value + " not supported");
        }
        break;
      case ID_CODEC_ID:
        // Validate that CodecID is supported. This extractor only supports "V_VP9".
        if (!CODEC_ID_VP9.equals(value)) {
          throw new IllegalArgumentException("CodecID " + value + " not supported");
        }
        break;
      default:
        // pass
    }
    return true;
  }

  /* package */ boolean onBinaryElement(
      int id, long elementOffsetBytes, int headerSizeBytes, int contentsSizeBytes,
      NonBlockingInputStream inputStream) {
    if (id == ID_SIMPLE_BLOCK) {
      // Please refer to http://www.matroska.org/technical/specs/index.html#simpleblock_structure
      // for info about how data is organized in a SimpleBlock element.

      // If we don't have a sample holder then don't consume the data.
      if (sampleHolder == null) {
        readResults |= RESULT_NEED_SAMPLE_HOLDER;
        return false;
      }

      // Value of trackNumber is not used but needs to be read.
      reader.readVarint(inputStream);

      // Next three bytes have timecode and flags.
      reader.readBytes(inputStream, simpleBlockTimecodeAndFlags, 3);

      // First two bytes of the three are the relative timecode.
      int timecode =
          (simpleBlockTimecodeAndFlags[0] << 8) | (simpleBlockTimecodeAndFlags[1] & 0xff);
      long timecodeUs = scaleTimecodeToUs(timecode);

      // Last byte of the three has some flags and the lacing value.
      boolean keyframe = (simpleBlockTimecodeAndFlags[2] & 0x80) == 0x80;
      boolean invisible = (simpleBlockTimecodeAndFlags[2] & 0x08) == 0x08;
      int lacing = (simpleBlockTimecodeAndFlags[2] & 0x06) >> 1;

      // Validate lacing and set info into sample holder.
      switch (lacing) {
        case LACING_NONE:
          long elementEndOffsetBytes = elementOffsetBytes + headerSizeBytes + contentsSizeBytes;
          simpleBlockTimecodeUs = clusterTimecodeUs + timecodeUs;
          sampleHolder.flags = keyframe ? MediaExtractor.SAMPLE_FLAG_SYNC : 0;
          sampleHolder.decodeOnly = invisible;
          sampleHolder.timeUs = clusterTimecodeUs + timecodeUs;
          sampleHolder.size = (int) (elementEndOffsetBytes - reader.getBytesRead());
          break;
        case LACING_EBML:
        case LACING_FIXED:
        case LACING_XIPH:
        default:
          throw new IllegalStateException("Lacing mode " + lacing + " not supported");
      }

      ByteBuffer outputData = sampleHolder.data;
      if (sampleHolder.allowDataBufferReplacement
          && (sampleHolder.data == null || sampleHolder.data.capacity() < sampleHolder.size)) {
        outputData = ByteBuffer.allocate(sampleHolder.size);
        sampleHolder.data = outputData;
      }

      if (outputData == null) {
        reader.skipBytes(inputStream, sampleHolder.size);
        sampleHolder.size = 0;
      } else {
        reader.readBytes(inputStream, outputData, sampleHolder.size);
      }
      readResults |= RESULT_READ_SAMPLE;
    }
    return true;
  }

  private long scaleTimecodeToUs(long unscaledTimecode) {
    return TimeUnit.NANOSECONDS.toMicros(unscaledTimecode * timecodeScale);
  }

  /**
   * Build a video {@link MediaFormat} containing recently gathered Video information, if needed.
   *
   * <p>Replaces the previous {@link #format} only if video width/height have changed.
   * {@link #format} is guaranteed to not be null after calling this method. In
   * the event that it can't be built, an {@link IllegalStateException} will be thrown.
   */
  private void buildFormat() {
    if (pixelWidth != UNKNOWN && pixelHeight != UNKNOWN
        && (format == null || format.width != pixelWidth || format.height != pixelHeight)) {
      format = MediaFormat.createVideoFormat(
          MimeTypes.VIDEO_VP9, MediaFormat.NO_VALUE, pixelWidth, pixelHeight, null);
      readResults |= RESULT_READ_INIT;
    } else if (format == null) {
      throw new IllegalStateException("Unable to build format");
    }
  }

  /**
   * Build a {@link SegmentIndex} containing recently gathered Cues information.
   *
   * <p>{@link #cues} is guaranteed to not be null after calling this method. In
   * the event that it can't be built, an {@link IllegalStateException} will be thrown.
   */
  private void buildCues() {
    if (segmentStartOffsetBytes == UNKNOWN) {
      throw new IllegalStateException("Segment start/end offsets unknown");
    } else if (durationUs == UNKNOWN) {
      throw new IllegalStateException("Duration unknown");
    } else if (cuesSizeBytes == UNKNOWN) {
      throw new IllegalStateException("Cues size unknown");
    } else if (cueTimesUs == null || cueClusterPositions == null
        || cueTimesUs.size() == 0 || cueTimesUs.size() != cueClusterPositions.size()) {
      throw new IllegalStateException("Invalid/missing cue points");
    }
    int cuePointsSize = cueTimesUs.size();
    int[] sizes = new int[cuePointsSize];
    long[] offsets = new long[cuePointsSize];
    long[] durationsUs = new long[cuePointsSize];
    long[] timesUs = new long[cuePointsSize];
    for (int i = 0; i < cuePointsSize; i++) {
      timesUs[i] = cueTimesUs.get(i);
      offsets[i] = segmentStartOffsetBytes + cueClusterPositions.get(i);
    }
    for (int i = 0; i < cuePointsSize - 1; i++) {
      sizes[i] = (int) (offsets[i + 1] - offsets[i]);
      durationsUs[i] = timesUs[i + 1] - timesUs[i];
    }
    sizes[cuePointsSize - 1] = (int) (segmentEndOffsetBytes - offsets[cuePointsSize - 1]);
    durationsUs[cuePointsSize - 1] = durationUs - timesUs[cuePointsSize - 1];
    cues = new SegmentIndex((int) cuesSizeBytes, sizes, offsets, durationsUs, timesUs);
    cueTimesUs = null;
    cueClusterPositions = null;
    readResults |= RESULT_READ_INDEX;
  }

  /**
   * Passes events through to {@link WebmExtractor} as
   * callbacks from {@link EbmlReader} are received.
   */
  private final class InnerEbmlEventHandler implements EbmlEventHandler {

    @Override
    public int getElementType(int id) {
      return WebmExtractor.this.getElementType(id);
    }

    @Override
    public void onMasterElementStart(
        int id, long elementOffsetBytes, int headerSizeBytes, long contentsSizeBytes) {
      WebmExtractor.this.onMasterElementStart(
          id, elementOffsetBytes, headerSizeBytes, contentsSizeBytes);
    }

    @Override
    public void onMasterElementEnd(int id) {
      WebmExtractor.this.onMasterElementEnd(id);
    }

    @Override
    public void onIntegerElement(int id, long value) {
      WebmExtractor.this.onIntegerElement(id, value);
    }

    @Override
    public void onFloatElement(int id, double value) {
      WebmExtractor.this.onFloatElement(id, value);
    }

    @Override
    public void onStringElement(int id, String value) {
      WebmExtractor.this.onStringElement(id, value);
    }

    @Override
    public boolean onBinaryElement(
        int id, long elementOffsetBytes, int headerSizeBytes, int contentsSizeBytes,
        NonBlockingInputStream inputStream) {
      return WebmExtractor.this.onBinaryElement(
          id, elementOffsetBytes, headerSizeBytes, contentsSizeBytes, inputStream);
    }

  }

}
