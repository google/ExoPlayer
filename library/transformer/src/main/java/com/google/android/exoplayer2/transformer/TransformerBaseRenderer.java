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

import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.MimeTypes;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

@RequiresApi(18)
/* package */ abstract class TransformerBaseRenderer extends BaseRenderer {

  protected final MuxerWrapper muxerWrapper;
  protected final TransformerMediaClock mediaClock;
  protected final Transformation transformation;

  protected boolean isRendererStarted;
  protected boolean muxerWrapperTrackAdded;
  protected boolean muxerWrapperTrackEnded;
  protected long streamOffsetUs;
  protected @MonotonicNonNull SamplePipeline samplePipeline;

  public TransformerBaseRenderer(
      int trackType,
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      Transformation transformation) {
    super(trackType);
    this.muxerWrapper = muxerWrapper;
    this.mediaClock = mediaClock;
    this.transformation = transformation;
  }

  @Override
  @C.FormatSupport
  public final int supportsFormat(Format format) {
    @Nullable String sampleMimeType = format.sampleMimeType;
    if (MimeTypes.getTrackType(sampleMimeType) != getTrackType()) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    } else if ((MimeTypes.isAudio(sampleMimeType)
            && muxerWrapper.supportsSampleMimeType(
                transformation.audioMimeType == null
                    ? sampleMimeType
                    : transformation.audioMimeType))
        || (MimeTypes.isVideo(sampleMimeType)
            && muxerWrapper.supportsSampleMimeType(
                transformation.videoMimeType == null
                    ? sampleMimeType
                    : transformation.videoMimeType))) {
      return RendererCapabilities.create(C.FORMAT_HANDLED);
    } else {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
    }
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
    return muxerWrapperTrackEnded;
  }

  @Override
  public final void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (!isRendererStarted || isEnded() || !ensureConfigured()) {
      return;
    }

    while (feedMuxerFromPipeline() || samplePipeline.processData() || feedPipelineFromInput()) {}
  }

  @Override
  protected final void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs) {
    this.streamOffsetUs = offsetUs;
  }

  @Override
  protected final void onEnabled(boolean joining, boolean mayRenderStartOfStream) {
    muxerWrapper.registerTrack();
    mediaClock.updateTimeForTrackType(getTrackType(), 0L);
  }

  @Override
  protected final void onStarted() {
    isRendererStarted = true;
  }

  @Override
  protected final void onStopped() {
    isRendererStarted = false;
  }

  @Override
  protected final void onReset() {
    if (samplePipeline != null) {
      samplePipeline.release();
    }
    muxerWrapperTrackAdded = false;
    muxerWrapperTrackEnded = false;
  }

  @EnsuresNonNullIf(expression = "samplePipeline", result = true)
  protected abstract boolean ensureConfigured() throws ExoPlaybackException;

  @RequiresNonNull({"samplePipeline", "#1.data"})
  protected void maybeQueueSampleToPipeline(DecoderInputBuffer inputBuffer) {
    samplePipeline.queueInputBuffer();
  }

  /**
   * Attempts to write sample pipeline output data to the muxer.
   *
   * @return Whether it may be possible to write more data immediately by calling this method again.
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
        checkStateNotNull(samplePipelineOutputBuffer.data),
        samplePipelineOutputBuffer.isKeyFrame(),
        samplePipelineOutputBuffer.timeUs)) {
      return false;
    }
    samplePipeline.releaseOutputBuffer();
    return true;
  }

  /**
   * Attempts to read input data and pass the input data to the sample pipeline.
   *
   * @return Whether it may be possible to read more data immediately by calling this method again.
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
        if (samplePipelineInputBuffer.isEndOfStream()) {
          samplePipeline.queueInputBuffer();
          return false;
        }
        mediaClock.updateTimeForTrackType(getTrackType(), samplePipelineInputBuffer.timeUs);
        samplePipelineInputBuffer.timeUs -= streamOffsetUs;
        samplePipelineInputBuffer.flip();
        checkStateNotNull(samplePipelineInputBuffer.data);
        maybeQueueSampleToPipeline(samplePipelineInputBuffer);
        return true;
      case C.RESULT_FORMAT_READ:
        throw new IllegalStateException("Format changes are not supported.");
      case C.RESULT_NOTHING_READ:
      default:
        return false;
    }
  }
}
