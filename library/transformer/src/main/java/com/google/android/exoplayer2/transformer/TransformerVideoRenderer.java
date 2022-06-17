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
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/* package */ final class TransformerVideoRenderer extends TransformerBaseRenderer {

  private static final String TAG = "TVideoRenderer";

  private final Context context;
  private final boolean clippingStartsAtKeyFrame;
  private final ImmutableList<GlEffect> effects;
  private final Codec.EncoderFactory encoderFactory;
  private final Codec.DecoderFactory decoderFactory;
  private final FrameProcessorChain.Listener frameProcessorChainListener;
  private final Transformer.DebugViewProvider debugViewProvider;
  private final DecoderInputBuffer decoderInputBuffer;

  private @MonotonicNonNull SefSlowMotionFlattener sefSlowMotionFlattener;

  public TransformerVideoRenderer(
      Context context,
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      TransformationRequest transformationRequest,
      boolean clippingStartsAtKeyFrame,
      ImmutableList<GlEffect> effects,
      Codec.EncoderFactory encoderFactory,
      Codec.DecoderFactory decoderFactory,
      FallbackListener fallbackListener,
      FrameProcessorChain.Listener frameProcessorChainListener,
      Transformer.DebugViewProvider debugViewProvider) {
    super(C.TRACK_TYPE_VIDEO, muxerWrapper, mediaClock, transformationRequest, fallbackListener);
    this.context = context;
    this.clippingStartsAtKeyFrame = clippingStartsAtKeyFrame;
    this.effects = effects;
    this.encoderFactory = encoderFactory;
    this.decoderFactory = decoderFactory;
    this.frameProcessorChainListener = frameProcessorChainListener;
    this.debugViewProvider = debugViewProvider;
    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
  }

  @Override
  public String getName() {
    return TAG;
  }

  /** Attempts to read the input format and to initialize the {@link SamplePipeline}. */
  @Override
  protected boolean ensureConfigured() throws TransformationException {
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
    if (shouldPassthrough(inputFormat)) {
      samplePipeline =
          new PassthroughSamplePipeline(inputFormat, transformationRequest, fallbackListener);
    } else {
      samplePipeline =
          new VideoTranscodingSamplePipeline(
              context,
              inputFormat,
              streamOffsetUs,
              transformationRequest,
              effects,
              decoderFactory,
              encoderFactory,
              muxerWrapper.getSupportedSampleMimeTypes(getTrackType()),
              fallbackListener,
              frameProcessorChainListener,
              debugViewProvider);
    }
    if (transformationRequest.flattenForSlowMotion) {
      sefSlowMotionFlattener = new SefSlowMotionFlattener(inputFormat);
    }
    return true;
  }

  private boolean shouldPassthrough(Format inputFormat) {
    if ((streamStartPositionUs - streamOffsetUs) != 0 && !clippingStartsAtKeyFrame) {
      return false;
    }
    if (encoderFactory.videoNeedsEncoding()) {
      return false;
    }
    if (transformationRequest.enableRequestSdrToneMapping) {
      return false;
    }
    if (transformationRequest.enableHdrEditing) {
      return false;
    }
    if (transformationRequest.videoMimeType != null
        && !transformationRequest.videoMimeType.equals(inputFormat.sampleMimeType)) {
      return false;
    }
    if (transformationRequest.videoMimeType == null
        && !muxerWrapper.supportsSampleMimeType(inputFormat.sampleMimeType)) {
      return false;
    }
    if (inputFormat.pixelWidthHeightRatio != 1f) {
      return false;
    }
    if (transformationRequest.rotationDegrees != 0f) {
      return false;
    }
    if (transformationRequest.scaleX != 1f) {
      return false;
    }
    if (transformationRequest.scaleY != 1f) {
      return false;
    }
    if (transformationRequest.outputHeight != C.LENGTH_UNSET
        && transformationRequest.outputHeight != inputFormat.height) {
      return false;
    }
    if (!effects.isEmpty()) {
      return false;
    }
    return true;
  }

  /**
   * Queues the input buffer to the sample pipeline unless it should be dropped because of slow
   * motion flattening.
   *
   * @param inputBuffer The {@link DecoderInputBuffer}.
   * @throws TransformationException If a {@link SamplePipeline} problem occurs.
   */
  @Override
  @RequiresNonNull({"samplePipeline", "#1.data"})
  protected void maybeQueueSampleToPipeline(DecoderInputBuffer inputBuffer)
      throws TransformationException {
    if (sefSlowMotionFlattener == null) {
      samplePipeline.queueInputBuffer();
      return;
    }

    ByteBuffer data = inputBuffer.data;
    long presentationTimeUs = inputBuffer.timeUs - streamOffsetUs;
    boolean shouldDropSample =
        sefSlowMotionFlattener.dropOrTransformSample(data, presentationTimeUs);
    inputBuffer.timeUs = streamOffsetUs + sefSlowMotionFlattener.getSamplePresentationTimeUs();
    if (shouldDropSample) {
      data.clear();
    } else {
      samplePipeline.queueInputBuffer();
    }
  }
}
