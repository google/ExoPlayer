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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import android.media.MediaCodec;
import android.util.Size;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Pipeline to decode video samples, apply transformations on the raw samples, and re-encode them.
 */
/* package */ final class VideoTranscodingSamplePipeline implements SamplePipeline {
  private final int outputRotationDegrees;
  private final int maxPendingFrameCount;

  private final DecoderInputBuffer decoderInputBuffer;
  private final Codec decoder;
  private final ArrayList<Long> decodeOnlyPresentationTimestamps;

  private final FrameProcessorChain frameProcessorChain;

  private final Codec encoder;
  private final DecoderInputBuffer encoderOutputBuffer;

  private boolean signaledEndOfStreamToEncoder;

  public VideoTranscodingSamplePipeline(
      Context context,
      Format inputFormat,
      long streamOffsetUs,
      TransformationRequest transformationRequest,
      ImmutableList<GlEffect> effects,
      Codec.DecoderFactory decoderFactory,
      Codec.EncoderFactory encoderFactory,
      List<String> allowedOutputMimeTypes,
      FallbackListener fallbackListener,
      FrameProcessorChain.Listener frameProcessorChainListener,
      Transformer.DebugViewProvider debugViewProvider)
      throws TransformationException {
    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderOutputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    decodeOnlyPresentationTimestamps = new ArrayList<>();

    // The decoder rotates encoded frames for display by inputFormat.rotationDegrees.
    int decodedWidth =
        (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.width : inputFormat.height;
    int decodedHeight =
        (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.height : inputFormat.width;

    ImmutableList.Builder<GlEffect> effectsListBuilder =
        new ImmutableList.Builder<GlEffect>().addAll(effects);
    if (transformationRequest.scaleX != 1f
        || transformationRequest.scaleY != 1f
        || transformationRequest.rotationDegrees != 0f) {
      effectsListBuilder.add(
          new ScaleToFitTransformation.Builder()
              .setScale(transformationRequest.scaleX, transformationRequest.scaleY)
              .setRotationDegrees(transformationRequest.rotationDegrees)
              .build());
    }
    if (transformationRequest.outputHeight != C.LENGTH_UNSET) {
      effectsListBuilder.add(
          new Presentation.Builder().setResolution(transformationRequest.outputHeight).build());
    }
    EncoderCompatibilityTransformation encoderCompatibilityTransformation =
        new EncoderCompatibilityTransformation();
    effectsListBuilder.add(encoderCompatibilityTransformation);
    try {
      frameProcessorChain =
          FrameProcessorChain.create(
              context,
              frameProcessorChainListener,
              inputFormat.pixelWidthHeightRatio,
              /* inputWidth= */ decodedWidth,
              /* inputHeight= */ decodedHeight,
              streamOffsetUs,
              effectsListBuilder.build(),
              transformationRequest.enableHdrEditing);
    } catch (FrameProcessingException e) {
      throw TransformationException.createForFrameProcessorChain(
          e, TransformationException.ERROR_CODE_GL_INIT_FAILED);
    }
    Size requestedEncoderSize = frameProcessorChain.getOutputSize();
    outputRotationDegrees = encoderCompatibilityTransformation.getOutputRotationDegrees();

    Format requestedEncoderFormat =
        new Format.Builder()
            .setWidth(requestedEncoderSize.getWidth())
            .setHeight(requestedEncoderSize.getHeight())
            .setRotationDegrees(0)
            .setFrameRate(inputFormat.frameRate)
            .setSampleMimeType(
                transformationRequest.videoMimeType != null
                    ? transformationRequest.videoMimeType
                    : inputFormat.sampleMimeType)
            .build();

    encoder = encoderFactory.createForVideoEncoding(requestedEncoderFormat, allowedOutputMimeTypes);
    Format encoderSupportedFormat = encoder.getConfigurationFormat();
    fallbackListener.onTransformationRequestFinalized(
        createFallbackTransformationRequest(
            transformationRequest,
            /* hasOutputFormatRotation= */ outputRotationDegrees == 0,
            requestedEncoderFormat,
            encoderSupportedFormat));

    frameProcessorChain.setOutputSurface(
        /* outputSurface= */ encoder.getInputSurface(),
        /* outputWidth= */ encoderSupportedFormat.width,
        /* outputHeight= */ encoderSupportedFormat.height,
        debugViewProvider.getDebugPreviewSurfaceView(
            encoderSupportedFormat.width, encoderSupportedFormat.height));

    decoder =
        decoderFactory.createForVideoDecoding(
            inputFormat,
            frameProcessorChain.getInputSurface(),
            transformationRequest.enableRequestSdrToneMapping);
    maxPendingFrameCount = decoder.getMaxPendingFrameCount();
  }

  @Override
  @Nullable
  public DecoderInputBuffer dequeueInputBuffer() throws TransformationException {
    return decoder.maybeDequeueInputBuffer(decoderInputBuffer) ? decoderInputBuffer : null;
  }

  @Override
  public void queueInputBuffer() throws TransformationException {
    if (decoderInputBuffer.isDecodeOnly()) {
      decodeOnlyPresentationTimestamps.add(decoderInputBuffer.timeUs);
    }
    decoder.queueInputBuffer(decoderInputBuffer);
  }

  @Override
  public boolean processData() throws TransformationException {
    if (frameProcessorChain.isEnded()) {
      if (!signaledEndOfStreamToEncoder) {
        encoder.signalEndOfInputStream();
        signaledEndOfStreamToEncoder = true;
      }
      return false;
    }
    if (decoder.isEnded()) {
      return false;
    }

    boolean processedData = false;
    while (maybeProcessDecoderOutput()) {
      processedData = true;
    }
    if (decoder.isEnded()) {
      frameProcessorChain.signalEndOfInputStream();
    }
    // If the decoder produced output, signal that it may be possible to process data again.
    return processedData;
  }

  @Override
  @Nullable
  public Format getOutputFormat() throws TransformationException {
    @Nullable Format format = encoder.getOutputFormat();
    return format == null
        ? null
        : format.buildUpon().setRotationDegrees(outputRotationDegrees).build();
  }

  @Override
  @Nullable
  public DecoderInputBuffer getOutputBuffer() throws TransformationException {
    encoderOutputBuffer.data = encoder.getOutputBuffer();
    if (encoderOutputBuffer.data == null) {
      return null;
    }
    MediaCodec.BufferInfo bufferInfo = checkNotNull(encoder.getOutputBufferInfo());
    encoderOutputBuffer.timeUs = bufferInfo.presentationTimeUs;
    encoderOutputBuffer.setFlags(bufferInfo.flags);
    return encoderOutputBuffer;
  }

  @Override
  public void releaseOutputBuffer() throws TransformationException {
    encoder.releaseOutputBuffer(/* render= */ false);
  }

  @Override
  public boolean isEnded() {
    return encoder.isEnded();
  }

  @Override
  public void release() {
    frameProcessorChain.release();
    decoder.release();
    encoder.release();
  }

  /**
   * Creates a fallback transformation request to execute, based on device-specific support.
   *
   * @param transformationRequest The requested transformation.
   * @param hasOutputFormatRotation Whether the input video will be rotated to landscape during
   *     processing, with {@link Format#rotationDegrees} of 90 added to the output format.
   * @param requestedFormat The requested format.
   * @param supportedFormat A format supported by the device.
   */
  @Pure
  private static TransformationRequest createFallbackTransformationRequest(
      TransformationRequest transformationRequest,
      boolean hasOutputFormatRotation,
      Format requestedFormat,
      Format supportedFormat) {
    // TODO(b/210591626): Also update bitrate etc. once encoder configuration and fallback are
    // implemented.
    if (Util.areEqual(requestedFormat.sampleMimeType, supportedFormat.sampleMimeType)
        && (hasOutputFormatRotation
            ? requestedFormat.width == supportedFormat.width
            : requestedFormat.height == supportedFormat.height)) {
      return transformationRequest;
    }
    return transformationRequest
        .buildUpon()
        .setVideoMimeType(supportedFormat.sampleMimeType)
        .setResolution(hasOutputFormatRotation ? requestedFormat.width : requestedFormat.height)
        .build();
  }

  /**
   * Feeds at most one decoder output frame to the next step of the pipeline.
   *
   * @return Whether a frame was processed.
   * @throws TransformationException If a problem occurs while processing the frame.
   */
  private boolean maybeProcessDecoderOutput() throws TransformationException {
    @Nullable MediaCodec.BufferInfo decoderOutputBufferInfo = decoder.getOutputBufferInfo();
    if (decoderOutputBufferInfo == null) {
      return false;
    }

    if (isDecodeOnlyBuffer(decoderOutputBufferInfo.presentationTimeUs)) {
      decoder.releaseOutputBuffer(/* render= */ false);
      return true;
    }

    if (maxPendingFrameCount != Codec.UNLIMITED_PENDING_FRAME_COUNT
        && frameProcessorChain.getPendingFrameCount() == maxPendingFrameCount) {
      return false;
    }

    frameProcessorChain.registerInputFrame();
    decoder.releaseOutputBuffer(/* render= */ true);
    return true;
  }

  private boolean isDecodeOnlyBuffer(long presentationTimeUs) {
    // We avoid using decodeOnlyPresentationTimestamps.remove(presentationTimeUs) because it would
    // box presentationTimeUs, creating a Long object that would need to be garbage collected.
    int size = decodeOnlyPresentationTimestamps.size();
    for (int i = 0; i < size; i++) {
      if (decodeOnlyPresentationTimestamps.get(i) == presentationTimeUs) {
        decodeOnlyPresentationTimestamps.remove(i);
        return true;
      }
    }
    return false;
  }
}
