/*
 * Copyright 2021 The Android Open Source Project
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

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

@RequiresApi(18)
/* package */ final class TransformerTranscodingVideoRenderer extends TransformerBaseRenderer {

  private static final String TAG = "TransformerTranscodingVideoRenderer";

  private final Context context;
  private final DecoderInputBuffer decoderInputBuffer;

  private @MonotonicNonNull SamplePipeline samplePipeline;
  private boolean muxerWrapperTrackAdded;
  private boolean muxerWrapperTrackEnded;

  public TransformerTranscodingVideoRenderer(
      Context context,
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      Transformation transformation) {
    super(C.TRACK_TYPE_VIDEO, muxerWrapper, mediaClock, transformation);
    this.context = context;
    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public boolean isEnded() {
    return muxerWrapperTrackEnded;
  }

  @Override
  protected void onReset() {
    if (samplePipeline != null) {
      samplePipeline.release();
    }
    muxerWrapperTrackAdded = false;
    muxerWrapperTrackEnded = false;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (!isRendererStarted || isEnded() || !ensureRendererConfigured()) {
      return;
    }

    while (feedMuxerFromPipeline() || samplePipeline.processData() || feedPipelineFromInput()) {}
  }

  /** Attempts to read the input format and to initialize the sample pipeline. */
  @EnsuresNonNullIf(expression = "samplePipeline", result = true)
  private boolean ensureRendererConfigured() throws ExoPlaybackException {
    if (samplePipeline != null) {
      return true;
    }
    FormatHolder formatHolder = getFormatHolder();
    @ReadDataResult
    int result = readSource(formatHolder, decoderInputBuffer, /* readFlags= */ FLAG_REQUIRE_FORMAT);
    if (result != C.RESULT_FORMAT_READ) {
      return false;
    }
    Format decoderInputFormat = checkNotNull(formatHolder.format);
    if (transformation.videoMimeType != null
        && !transformation.videoMimeType.equals(decoderInputFormat.sampleMimeType)) {
      samplePipeline =
          new VideoSamplePipeline(context, decoderInputFormat, transformation, getIndex());
    } else {
      samplePipeline = new PassthroughSamplePipeline(decoderInputFormat);
    }
    return true;
  }

  /**
   * Attempts to write sample pipeline output data to the muxer, and returns whether it may be
   * possible to write more data immediately by calling this method again.
   */
  @RequiresNonNull("samplePipeline")
  private boolean feedMuxerFromPipeline() {
    if (!muxerWrapperTrackAdded) {
      @Nullable Format samplePipelineOutputFormat = samplePipeline.getOutputFormat();
      if (samplePipelineOutputFormat == null) {
        return false;
      }
      muxerWrapperTrackAdded = true;
      muxerWrapper.addTrackFormat(samplePipelineOutputFormat);
    }

    if (samplePipeline.isEnded()) {
      muxerWrapper.endTrack(getTrackType());
      muxerWrapperTrackEnded = true;
      return false;
    }

    @Nullable DecoderInputBuffer samplePipelineOutputBuffer = samplePipeline.getOutputBuffer();
    if (samplePipelineOutputBuffer == null) {
      return false;
    }

    if (!muxerWrapper.writeSample(
        getTrackType(),
        samplePipelineOutputBuffer.data,
        samplePipelineOutputBuffer.isKeyFrame(),
        samplePipelineOutputBuffer.timeUs)) {
      return false;
    }
    samplePipeline.releaseOutputBuffer();
    return true;
  }

  /**
   * Attempts to pass input data to the sample pipeline, and returns whether it may be possible to
   * pass more data immediately by calling this method again.
   */
  @RequiresNonNull("samplePipeline")
  private boolean feedPipelineFromInput() {
    @Nullable DecoderInputBuffer samplePipelineInputBuffer = samplePipeline.dequeueInputBuffer();
    if (samplePipelineInputBuffer == null) {
      return false;
    }

    @ReadDataResult
    int result = readSource(getFormatHolder(), samplePipelineInputBuffer, /* readFlags= */ 0);
    switch (result) {
      case C.RESULT_BUFFER_READ:
        mediaClock.updateTimeForTrackType(getTrackType(), samplePipelineInputBuffer.timeUs);
        samplePipelineInputBuffer.timeUs -= streamOffsetUs;
        samplePipelineInputBuffer.flip();
        samplePipeline.queueInputBuffer();
        return !samplePipelineInputBuffer.isEndOfStream();
      case C.RESULT_FORMAT_READ:
        throw new IllegalStateException("Format changes are not supported.");
      case C.RESULT_NOTHING_READ:
      default:
        return false;
    }
  }
}
