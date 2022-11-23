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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.errorprone.annotations.ForOverride;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/* package */ abstract class TransformerBaseRenderer extends BaseRenderer {

  protected final MuxerWrapper muxerWrapper;
  protected final TransformerMediaClock mediaClock;
  protected final TransformationRequest transformationRequest;
  protected final Transformer.AsyncErrorListener asyncErrorListener;
  protected final FallbackListener fallbackListener;

  private boolean isTransformationRunning;
  protected long streamOffsetUs;
  protected long streamStartPositionUs;
  protected @MonotonicNonNull SamplePipeline samplePipeline;

  public TransformerBaseRenderer(
      int trackType,
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      TransformationRequest transformationRequest,
      Transformer.AsyncErrorListener asyncErrorListener,
      FallbackListener fallbackListener) {
    super(trackType);
    this.muxerWrapper = muxerWrapper;
    this.mediaClock = mediaClock;
    this.transformationRequest = transformationRequest;
    this.asyncErrorListener = asyncErrorListener;
    this.fallbackListener = fallbackListener;
  }

  /**
   * Returns whether the renderer supports the track type of the given format.
   *
   * @param format The format.
   * @return The {@link Capabilities} for this format.
   */
  @Override
  public final @Capabilities int supportsFormat(Format format) {
    return RendererCapabilities.create(
        MimeTypes.getTrackType(format.sampleMimeType) == getTrackType()
            ? C.FORMAT_HANDLED
            : C.FORMAT_UNSUPPORTED_TYPE);
  }

  @Override
  public final MediaClock getMediaClock() {
    return mediaClock;
  }

  @Override
  public final boolean isReady() {
    return isSourceReady();
  }

  @Override
  public final boolean isEnded() {
    return samplePipeline != null && samplePipeline.isEnded();
  }

  @Override
  public final void render(long positionUs, long elapsedRealtimeUs) {
    try {
      if (!isTransformationRunning || isEnded() || !ensureConfigured()) {
        return;
      }

      while (samplePipeline.processData() || feedPipelineFromInput()) {}
    } catch (TransformationException e) {
      isTransformationRunning = false;
      asyncErrorListener.onTransformationException(e);
    }
  }

  @Override
  protected final void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs) {
    this.streamOffsetUs = offsetUs;
    this.streamStartPositionUs = startPositionUs;
  }

  @Override
  protected final void onEnabled(boolean joining, boolean mayRenderStartOfStream) {
    muxerWrapper.registerTrack();
    fallbackListener.registerTrack();
    mediaClock.updateTimeForTrackType(getTrackType(), 0L);
  }

  @Override
  protected final void onStarted() {
    isTransformationRunning = true;
  }

  @Override
  protected final void onStopped() {
    isTransformationRunning = false;
  }

  @Override
  protected final void onReset() {
    if (samplePipeline != null) {
      samplePipeline.release();
    }
  }

  @ForOverride
  @EnsuresNonNullIf(expression = "samplePipeline", result = true)
  protected abstract boolean ensureConfigured() throws TransformationException;

  /**
   * Attempts to read input data and pass the input data to the sample pipeline.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
   * @throws TransformationException If a {@link SamplePipeline} problem occurs.
   */
  @RequiresNonNull("samplePipeline")
  private boolean feedPipelineFromInput() throws TransformationException {
    @Nullable DecoderInputBuffer samplePipelineInputBuffer = samplePipeline.dequeueInputBuffer();
    if (samplePipelineInputBuffer == null) {
      return false;
    }

    @ReadDataResult
    int result = readSource(getFormatHolder(), samplePipelineInputBuffer, /* readFlags= */ 0);
    switch (result) {
      case C.RESULT_BUFFER_READ:
        samplePipelineInputBuffer.flip();
        if (samplePipelineInputBuffer.isEndOfStream()) {
          samplePipeline.queueInputBuffer();
          return false;
        }
        mediaClock.updateTimeForTrackType(getTrackType(), samplePipelineInputBuffer.timeUs);
        samplePipeline.queueInputBuffer();
        return true;
      case C.RESULT_FORMAT_READ:
        throw new IllegalStateException("Format changes are not supported.");
      case C.RESULT_NOTHING_READ:
      default:
        return false;
    }
  }
}
