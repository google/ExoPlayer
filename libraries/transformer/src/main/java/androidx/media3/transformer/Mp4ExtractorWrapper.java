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
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A wrapper around an {@link Mp4Extractor} providing methods to extract MP4 metadata. */
/* package */ final class Mp4ExtractorWrapper {
  private final Context context;
  private final String filePath;
  private final Mp4Extractor mp4Extractor;
  private final ExtractorOutputImpl extractorOutput;
  private boolean initialized;

  /**
   * Creates an instance.
   *
   * @param context A {@link Context}.
   * @param filePath The file path of a valid MP4.
   */
  public Mp4ExtractorWrapper(Context context, String filePath) {
    this.context = context;
    this.filePath = filePath;
    mp4Extractor = new Mp4Extractor();
    extractorOutput = new ExtractorOutputImpl();
  }

  /**
   * Initializes the {@link Mp4ExtractorWrapper}.
   *
   * <p>This method must be called only once and it should be called before calling any other
   * method.
   */
  public void init() throws IOException {
    checkState(!initialized);

    DefaultDataSource dataSource =
        new DefaultDataSource(context, /* allowCrossProtocolRedirects= */ false);
    DataSpec dataSpec = new DataSpec.Builder().setUri(filePath).build();
    long length = dataSource.open(dataSpec);
    checkState(length != 0);
    DefaultExtractorInput extractorInput =
        new DefaultExtractorInput(dataSource, /* position= */ 0, length);
    checkState(mp4Extractor.sniff(extractorInput), "The MP4 file is invalid");

    mp4Extractor.init(extractorOutput);
    PositionHolder positionHolder = new PositionHolder();
    try {
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
      initialized = true;
    } finally {
      DataSourceUtil.closeQuietly(dataSource);
    }
  }

  /**
   * Returns the presentation timestamp (in microseconds) of the last sync sample or {@link
   * C#TIME_UNSET} if there is no video track.
   */
  public long getLastSyncSampleTimestampUs() {
    checkState(initialized);

    if (extractorOutput.videoTrackId == C.INDEX_UNSET) {
      return C.TIME_UNSET;
    }
    long durationUs = mp4Extractor.getDurationUs();
    checkState(durationUs != C.TIME_UNSET);
    SeekMap.SeekPoints seekPoints =
        mp4Extractor.getSeekPoints(durationUs, extractorOutput.videoTrackId);
    return seekPoints.first.timeUs;
  }

  private static final class ExtractorOutputImpl implements ExtractorOutput {
    public int videoTrackId;
    public boolean seekMapInitialized;

    private final Map<Integer, TrackOutput> trackTypeToTrackOutput;

    ExtractorOutputImpl() {
      videoTrackId = C.INDEX_UNSET;
      trackTypeToTrackOutput = new HashMap<>();
    }

    @Override
    public TrackOutput track(int id, @C.TrackType int type) {
      if (type == C.TRACK_TYPE_VIDEO) {
        videoTrackId = id;
      }

      @Nullable TrackOutput trackOutput = trackTypeToTrackOutput.get(type);
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

      private final byte[] byteArray;

      private TrackOutputImpl() {
        byteArray = new byte[FIXED_BYTE_ARRAY_SIZE];
      }

      @Override
      public void format(Format format) {}

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
