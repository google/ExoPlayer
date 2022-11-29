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

import static com.google.android.exoplayer2.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED;
import static com.google.android.exoplayer2.source.SampleStream.FLAG_REQUIRE_FORMAT;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.MimeTypes;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/* package */ final class ExoPlayerAssetLoaderRenderer extends BaseRenderer {

  private static final String TAG = "ExoPlayerAssetLoaderRenderer";

  private final TransformerMediaClock mediaClock;
  private final ExoPlayerAssetLoader.Listener assetLoaderListener;
  private final DecoderInputBuffer decoderInputBuffer;

  private boolean isTransformationRunning;
  private long streamStartPositionUs;
  private long streamOffsetUs;
  private @MonotonicNonNull SamplePipeline samplePipeline;

  public ExoPlayerAssetLoaderRenderer(
      int trackType,
      TransformerMediaClock mediaClock,
      ExoPlayerAssetLoader.Listener assetLoaderListener) {
    super(trackType);
    this.mediaClock = mediaClock;
    this.assetLoaderListener = assetLoaderListener;
    decoderInputBuffer = new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DISABLED);
  }

  @Override
  public String getName() {
    return TAG;
  }

  /**
   * Returns whether the renderer supports the track type of the given format.
   *
   * @param format The format.
   * @return The {@link Capabilities} for this format.
   */
  @Override
  public @Capabilities int supportsFormat(Format format) {
    return RendererCapabilities.create(
        MimeTypes.getTrackType(format.sampleMimeType) == getTrackType()
            ? C.FORMAT_HANDLED
            : C.FORMAT_UNSUPPORTED_TYPE);
  }

  @Override
  public MediaClock getMediaClock() {
    return mediaClock;
  }

  @Override
  public boolean isReady() {
    return isSourceReady();
  }

  @Override
  public boolean isEnded() {
    return samplePipeline != null && samplePipeline.isEnded();
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) {
    try {
      if (!isTransformationRunning || isEnded() || !ensureConfigured()) {
        return;
      }

      while (samplePipeline.processData() || feedPipelineFromInput()) {}
    } catch (TransformationException e) {
      isTransformationRunning = false;
      assetLoaderListener.onError(e);
    }
  }

  @Override
  protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs) {
    this.streamStartPositionUs = startPositionUs;
    this.streamOffsetUs = offsetUs;
  }

  @Override
  protected void onEnabled(boolean joining, boolean mayRenderStartOfStream) {
    assetLoaderListener.onTrackRegistered();
    mediaClock.updateTimeForTrackType(getTrackType(), 0L);
  }

  @Override
  protected void onStarted() {
    isTransformationRunning = true;
  }

  @Override
  protected void onStopped() {
    isTransformationRunning = false;
  }

  @Override
  protected void onReset() {
    if (samplePipeline != null) {
      samplePipeline.release();
    }
  }

  @EnsuresNonNullIf(expression = "samplePipeline", result = true)
  private boolean ensureConfigured() throws TransformationException {
    if (samplePipeline != null) {
      return true;
    }

    FormatHolder formatHolder = getFormatHolder();
    @ReadDataResult
    int result = readSource(formatHolder, decoderInputBuffer, /* readFlags= */ FLAG_REQUIRE_FORMAT);
    if (result != C.RESULT_FORMAT_READ) {
      return false;
    }
    Format inputFormat = checkNotNull(formatHolder.format);
    samplePipeline =
        assetLoaderListener.onTrackAdded(inputFormat, streamStartPositionUs, streamOffsetUs);
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
