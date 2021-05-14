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

package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import java.nio.ByteBuffer;

@RequiresApi(18)
/* package */ final class TransformerVideoRenderer extends TransformerBaseRenderer {

  private static final String TAG = "TransformerVideoRenderer";

  private final DecoderInputBuffer buffer;

  @Nullable private SampleTransformer sampleTransformer;

  private boolean formatRead;
  private boolean isBufferPending;
  private boolean isInputStreamEnded;

  public TransformerVideoRenderer(
      MuxerWrapper muxerWrapper, TransformerMediaClock mediaClock, Transformation transformation) {
    super(C.TRACK_TYPE_VIDEO, muxerWrapper, mediaClock, transformation);
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) {
    if (!isRendererStarted || isEnded()) {
      return;
    }

    if (!formatRead) {
      FormatHolder formatHolder = getFormatHolder();
      @ReadDataResult int result = readSource(formatHolder, buffer, FLAG_REQUIRE_FORMAT);
      if (result != C.RESULT_FORMAT_READ) {
        return;
      }
      Format format = checkNotNull(formatHolder.format);
      formatRead = true;
      if (transformation.flattenForSlowMotion) {
        sampleTransformer = new SefSlowMotionVideoSampleTransformer(format);
      }
      muxerWrapper.addTrackFormat(format);
    }

    while (true) {
      // Read sample.
      if (!isBufferPending && !readAndTransformBuffer()) {
        return;
      }
      // Write sample.
      isBufferPending =
          !muxerWrapper.writeSample(
              getTrackType(), buffer.data, buffer.isKeyFrame(), buffer.timeUs);
      if (isBufferPending) {
        return;
      }
    }
  }

  @Override
  public boolean isEnded() {
    return isInputStreamEnded;
  }

  /**
   * Checks whether a sample can be read and, if so, reads it, transforms it and writes the
   * resulting sample to the {@link #buffer}.
   *
   * <p>The buffer data can be set to null if the transformation applied discards the sample.
   *
   * @return Whether a sample has been read and transformed.
   */
  private boolean readAndTransformBuffer() {
    buffer.clear();
    @ReadDataResult int result = readSource(getFormatHolder(), buffer, /* readFlags= */ 0);
    if (result == C.RESULT_FORMAT_READ) {
      throw new IllegalStateException("Format changes are not supported.");
    } else if (result == C.RESULT_NOTHING_READ) {
      return false;
    }

    // Buffer read.

    if (buffer.isEndOfStream()) {
      isInputStreamEnded = true;
      muxerWrapper.endTrack(getTrackType());
      return false;
    }
    mediaClock.updateTimeForTrackType(getTrackType(), buffer.timeUs);
    ByteBuffer data = checkNotNull(buffer.data);
    data.flip();
    if (sampleTransformer != null) {
      sampleTransformer.transformSample(buffer);
    }
    return true;
  }
}
