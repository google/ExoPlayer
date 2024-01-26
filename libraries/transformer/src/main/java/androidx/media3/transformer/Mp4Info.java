/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static java.lang.Math.min;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.datasource.DataSourceUtil;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.extractor.DefaultExtractorInput;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.text.SubtitleParser;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Provides some specific MP4 metadata about an mp4 file such as the duration, last sync sample
 * timestamp etc.
 */
/* package */ final class Mp4Info {
  /**
   * The duration (in microseconds) of the MP4 file or {@link C#TIME_UNSET} if the duration is
   * unknown.
   */
  public final long durationUs;

  /**
   * The presentation timestamp (in microseconds) of the last sync sample or {@link C#TIME_UNSET} if
   * there is no video track.
   */
  public final long lastSyncSampleTimestampUs;

  /**
   * The presentation timestamp (in microseconds) of the first sync sample at or after {@code
   * timeUs}, or {@link C#TIME_END_OF_SOURCE} if there are none. Set to {@link C#TIME_UNSET} if
   * there is no video track or if {@code timeUs} is {@link C#TIME_UNSET}.
   */
  public final long firstSyncSampleTimestampUsAfterTimeUs;

  /** The video {@link Format} or {@code null} if there is no video track. */
  public final @Nullable Format videoFormat;

  /** The audio {@link Format} or {@code null} if there is no audio track. */
  public final @Nullable Format audioFormat;

  private Mp4Info(
      long durationUs,
      long lastSyncSampleTimestampUs,
      long firstSyncSampleTimestampUsAfterTimeUs,
      @Nullable Format videoFormat,
      @Nullable Format audioFormat) {
    this.durationUs = durationUs;
    this.lastSyncSampleTimestampUs = lastSyncSampleTimestampUs;
    this.firstSyncSampleTimestampUsAfterTimeUs = firstSyncSampleTimestampUsAfterTimeUs;
    this.videoFormat = videoFormat;
    this.audioFormat = audioFormat;
  }

  /**
   * Extracts the MP4 metadata synchronously and returns {@link Mp4Info}.
   *
   * @param context A {@link Context}.
   * @param filePath The file path of a valid MP4.
   * @throws IOException If an error occurs during metadata extraction.
   */
  public static Mp4Info create(Context context, String filePath) throws IOException {
    return create(context, filePath, C.TIME_UNSET);
  }

  /**
   * Extracts the MP4 metadata synchronously and returns {@link Mp4Info}.
   *
   * @param context A {@link Context}.
   * @param filePath The file path of a valid MP4.
   * @param timeUs The time (in microseconds) used to calculate the {@link
   *     #firstSyncSampleTimestampUsAfterTimeUs}. {@link C#TIME_UNSET} if not needed.
   * @throws IOException If an error occurs during metadata extraction.
   */
  public static Mp4Info create(Context context, String filePath, long timeUs) throws IOException {
    Mp4Extractor mp4Extractor =
        new Mp4Extractor(
            SubtitleParser.Factory.UNSUPPORTED, Mp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA);
    ExtractorOutputImpl extractorOutput = new ExtractorOutputImpl();
    DefaultDataSource dataSource =
        new DefaultDataSource(context, /* allowCrossProtocolRedirects= */ false);
    DataSpec dataSpec = new DataSpec.Builder().setUri(filePath).build();
    try {
      long length = dataSource.open(dataSpec);
      checkState(length != 0);
      DefaultExtractorInput extractorInput =
          new DefaultExtractorInput(dataSource, /* position= */ 0, length);
      checkState(mp4Extractor.sniff(extractorInput), "The MP4 file is invalid");

      mp4Extractor.init(extractorOutput);
      PositionHolder positionHolder = new PositionHolder();
      while (!extractorOutput.seekMapInitialized) {
        @Extractor.ReadResult int result = mp4Extractor.read(extractorInput, positionHolder);
        if (result == Extractor.RESULT_SEEK) {
          dataSource.close();
          length =
              dataSource.open(
                  new DataSpec.Builder()
                      .setUri(filePath)
                      .setPosition(positionHolder.position)
                      .build());
          if (length != C.LENGTH_UNSET) {
            length += positionHolder.position;
          }
          extractorInput = new DefaultExtractorInput(dataSource, positionHolder.position, length);
        } else if (result == Extractor.RESULT_END_OF_INPUT && !extractorOutput.seekMapInitialized) {
          throw new IllegalStateException("The MP4 file is invalid");
        }
      }

      long durationUs = mp4Extractor.getDurationUs();
      long lastSyncSampleTimestampUs = C.TIME_UNSET;
      long firstSyncSampleTimestampUsAfterTimeUs = C.TIME_UNSET;
      @Nullable Format videoFormat = null;
      if (extractorOutput.videoTrackId != C.INDEX_UNSET) {
        ExtractorOutputImpl.TrackOutputImpl videoTrackOutput =
            checkNotNull(extractorOutput.trackTypeToTrackOutput.get(C.TRACK_TYPE_VIDEO));
        videoFormat = checkNotNull(videoTrackOutput.format);

        checkState(durationUs != C.TIME_UNSET);
        SeekMap.SeekPoints lastSyncSampleSeekPoints =
            mp4Extractor.getSeekPoints(durationUs, extractorOutput.videoTrackId);
        lastSyncSampleTimestampUs = lastSyncSampleSeekPoints.first.timeUs;

        if (timeUs != C.TIME_UNSET) {
          SeekMap.SeekPoints firstSyncSampleSeekPoints =
              mp4Extractor.getSeekPoints(timeUs, extractorOutput.videoTrackId);
          if (timeUs == firstSyncSampleSeekPoints.first.timeUs) {
            firstSyncSampleTimestampUsAfterTimeUs = firstSyncSampleSeekPoints.first.timeUs;
          } else if (timeUs <= firstSyncSampleSeekPoints.second.timeUs) {
            firstSyncSampleTimestampUsAfterTimeUs = firstSyncSampleSeekPoints.second.timeUs;
          } else { // There is no sync sample after timeUs
            firstSyncSampleTimestampUsAfterTimeUs = C.TIME_END_OF_SOURCE;
          }
        }
      }

      @Nullable Format audioFormat = null;
      if (extractorOutput.audioTrackId != C.INDEX_UNSET) {
        ExtractorOutputImpl.TrackOutputImpl audioTrackOutput =
            checkNotNull(extractorOutput.trackTypeToTrackOutput.get(C.TRACK_TYPE_AUDIO));
        audioFormat = checkNotNull(audioTrackOutput.format);
      }

      return new Mp4Info(
          durationUs,
          lastSyncSampleTimestampUs,
          firstSyncSampleTimestampUsAfterTimeUs,
          videoFormat,
          audioFormat);
    } finally {
      DataSourceUtil.closeQuietly(dataSource);
      mp4Extractor.release();
    }
  }

  private static final class ExtractorOutputImpl implements ExtractorOutput {
    public int videoTrackId;
    public int audioTrackId;
    public boolean seekMapInitialized;

    final Map<Integer, TrackOutputImpl> trackTypeToTrackOutput;

    public ExtractorOutputImpl() {
      videoTrackId = C.INDEX_UNSET;
      audioTrackId = C.INDEX_UNSET;
      trackTypeToTrackOutput = new HashMap<>();
    }

    @Override
    public TrackOutput track(int id, @C.TrackType int type) {
      if (type == C.TRACK_TYPE_VIDEO) {
        videoTrackId = id;
      } else if (type == C.TRACK_TYPE_AUDIO) {
        audioTrackId = id;
      }

      @Nullable TrackOutputImpl trackOutput = trackTypeToTrackOutput.get(type);
      if (trackOutput == null) {
        trackOutput = new TrackOutputImpl();
        trackTypeToTrackOutput.put(type, trackOutput);
      }
      return trackOutput;
    }

    @Override
    public void endTracks() {}

    @Override
    public void seekMap(SeekMap seekMap) {
      seekMapInitialized = true;
    }

    private static final class TrackOutputImpl implements TrackOutput {
      private static final int FIXED_BYTE_ARRAY_SIZE = 16_000;

      public @MonotonicNonNull Format format;

      private final byte[] byteArray;

      public TrackOutputImpl() {
        byteArray = new byte[FIXED_BYTE_ARRAY_SIZE];
      }

      @Override
      public void format(Format format) {
        this.format = format;
      }

      @Override
      public int sampleData(
          DataReader input, int length, boolean allowEndOfInput, @SampleDataPart int sampleDataPart)
          throws IOException {
        int remainingBytes = length;
        while (remainingBytes > 0) {
          int bytesToRead = min(remainingBytes, byteArray.length);
          int bytesRead = input.read(byteArray, /* offset= */ 0, bytesToRead);
          checkState(bytesRead != C.RESULT_END_OF_INPUT);
          remainingBytes -= bytesRead;
        }
        return length;
      }

      @Override
      public void sampleData(
          ParsableByteArray data, int length, @SampleDataPart int sampleDataPart) {
        while (length > 0) {
          int bytesToRead = min(length, byteArray.length);
          data.readBytes(byteArray, /* offset= */ 0, bytesToRead);
          length -= bytesToRead;
        }
      }

      @Override
      public void sampleMetadata(
          long timeUs,
          @C.BufferFlags int flags,
          int size,
          int offset,
          @Nullable CryptoData cryptoData) {}
    }
  }
}
