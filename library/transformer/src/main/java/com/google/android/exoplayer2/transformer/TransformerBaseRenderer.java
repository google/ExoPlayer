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
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
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
  protected final FallbackListener fallbackListener;

  protected boolean isRendererStarted;
  protected boolean muxerWrapperTrackAdded;
  protected boolean muxerWrapperTrackEnded;
  protected long streamOffsetUs;
  protected long streamStartPositionUs;
  protected @MonotonicNonNull SamplePipeline samplePipeline;

  public TransformerBaseRenderer(
      int trackType,
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      TransformationRequest transformationRequest,
      FallbackListener fallbackListener) {
    super(trackType);
    this.muxerWrapper = muxerWrapper;
    this.mediaClock = mediaClock;
    this.transformationRequest = transformationRequest;
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
    return muxerWrapperTrackEnded;
  }

  @Override
  public final void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    try {
      if (!isRendererStarted || isEnded() || !ensureConfigured()) {
        return;
      }

      while (feedMuxerFromPipeline() || samplePipeline.processData() || feedPipelineFromInput()) {}
    } catch (TransformationException e) {
      throw wrapTransformationException(e);
    } catch (Muxer.MuxerException e) {
      throw wrapTransformationException(
          TransformationException.createForMuxer(
              e, TransformationException.ERROR_CODE_MUXING_FAILED));
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

  @ForOverride
  @EnsuresNonNullIf(expression = "samplePipeline", result = true)
  protected abstract boolean ensureConfigured() throws TransformationException;

  @RequiresNonNull({"samplePipeline", "#1.data"})
  protected void maybeQueueSampleToPipeline(DecoderInputBuffer inputBuffer)
      throws TransformationException {
    samplePipeline.queueInputBuffer();
  }

  /**
   * Attempts to write sample pipeline output data to the muxer.
   *
   * @return Whether it may be possible to write more data immediately by calling this method again.
   * @throws Muxer.MuxerException If a muxing problem occurs.
   * @throws TransformationException If a {@link SamplePipeline} problem occurs.
   */
  @RequiresNonNull("samplePipeline")
  private boolean feedMuxerFromPipeline() throws Muxer.MuxerException, TransformationException {
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

    long samplePresentationTimeUs = samplePipelineOutputBuffer.timeUs - streamStartPositionUs;
    // TODO(b/204892224): Consider subtracting the first sample timestamp from the sample pipeline
    //  buffer from all samples so that they are guaranteed to start from zero in the output file.
    if (!muxerWrapper.writeSample(
        getTrackType(),
        checkStateNotNull(samplePipelineOutputBuffer.data),
        samplePipelineOutputBuffer.isKeyFrame(),
        samplePresentationTimeUs)) {
      return false;
    }
    samplePipeline.releaseOutputBuffer();
    return true;
  }

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

  /**
   * Returns an {@link ExoPlaybackException} wrapping the {@link TransformationException}.
   *
   * <p>This temporary wrapping is needed due to the dependence on ExoPlayer's BaseRenderer. {@link
   * Transformer} extracts the {@link TransformationException} from this {@link
   * ExoPlaybackException} again.
   */
  private ExoPlaybackException wrapTransformationException(
      TransformationException transformationException) {
    return ExoPlaybackException.createForRenderer(
        transformationException,
        "Transformer",
        getIndex(),
        /* rendererFormat= */ null,
        C.FORMAT_HANDLED,
        /* isRecoverable= */ false,
        PlaybackException.ERROR_CODE_UNSPECIFIED);
  }
}
