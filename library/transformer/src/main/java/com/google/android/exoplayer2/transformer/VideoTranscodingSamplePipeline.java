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
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.content.Context;
import android.media.MediaCodec;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.FrameProcessingException;
import androidx.media3.common.FrameProcessor;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.ScaleToFitTransformation;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Pipeline to decode video samples, apply transformations on the raw samples, and re-encode them.
 */
/* package */ final class VideoTranscodingSamplePipeline implements SamplePipeline {

  private final int maxPendingFrameCount;

  private final DecoderInputBuffer decoderInputBuffer;
  private final Codec decoder;
  private final ArrayList<Long> decodeOnlyPresentationTimestamps;

  private final FrameProcessor frameProcessor;

  private final EncoderWrapper encoderWrapper;
  private final DecoderInputBuffer encoderOutputBuffer;

  public VideoTranscodingSamplePipeline(
      Context context,
      Format inputFormat,
      long streamOffsetUs,
      TransformationRequest transformationRequest,
      ImmutableList<Effect> effects,
      FrameProcessor.Factory frameProcessorFactory,
      Codec.DecoderFactory decoderFactory,
      Codec.EncoderFactory encoderFactory,
      List<String> allowedOutputMimeTypes,
      FallbackListener fallbackListener,
      Transformer.AsyncErrorListener asyncErrorListener,
      DebugViewProvider debugViewProvider)
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

    ImmutableList.Builder<Effect> effectsListBuilder =
        new ImmutableList.Builder<Effect>().addAll(effects);
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
      effectsListBuilder.add(Presentation.createForHeight(transformationRequest.outputHeight));
    }

    encoderWrapper =
        new EncoderWrapper(
            encoderFactory,
            inputFormat,
            allowedOutputMimeTypes,
            transformationRequest,
            fallbackListener);

    try {
      frameProcessor =
          frameProcessorFactory.create(
              context,
              new FrameProcessor.Listener() {
                @Override
                public void onOutputSizeChanged(int width, int height) {
                  try {
                    checkNotNull(frameProcessor)
                        .setOutputSurfaceInfo(encoderWrapper.getSurfaceInfo(width, height));
                  } catch (TransformationException exception) {
                    asyncErrorListener.onTransformationException(exception);
                  }
                }

                @Override
                public void onOutputFrameAvailable(long presentationTimeNs) {
                  // Do nothing as frames are released automatically.
                }

                @Override
                public void onFrameProcessingError(FrameProcessingException exception) {
                  asyncErrorListener.onTransformationException(
                      TransformationException.createForFrameProcessingException(
                          exception, TransformationException.ERROR_CODE_FRAME_PROCESSING_FAILED));
                }

                @Override
                public void onFrameProcessingEnded() {
                  try {
                    encoderWrapper.signalEndOfInputStream();
                  } catch (TransformationException exception) {
                    asyncErrorListener.onTransformationException(exception);
                  }
                }
              },
              effectsListBuilder.build(),
              debugViewProvider,
              // HDR colors are only used if the MediaCodec encoder supports FEATURE_HdrEditing.
              // This implies that the OpenGL EXT_YUV_target extension is supported and hence the
              // default FrameProcessor, GlEffectsFrameProcessor, also supports HDR. Otherwise, tone
              // mapping is applied, which ensures the decoder outputs SDR output for an HDR input.
              encoderWrapper.getSupportedInputColor(),
              /* releaseFramesAutomatically= */ true);
    } catch (FrameProcessingException e) {
      throw TransformationException.createForFrameProcessingException(
          e, TransformationException.ERROR_CODE_FRAME_PROCESSING_FAILED);
    }
    frameProcessor.setInputFrameInfo(
        new FrameInfo(
            decodedWidth, decodedHeight, inputFormat.pixelWidthHeightRatio, streamOffsetUs));

    boolean isToneMappingRequired =
        ColorInfo.isTransferHdr(inputFormat.colorInfo)
            && !ColorInfo.isTransferHdr(encoderWrapper.getSupportedInputColor());
    decoder =
        decoderFactory.createForVideoDecoding(
            inputFormat, frameProcessor.getInputSurface(), isToneMappingRequired);
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
    if (decoder.isEnded()) {
      return false;
    }

    boolean processedData = false;
    while (maybeProcessDecoderOutput()) {
      processedData = true;
    }
    if (decoder.isEnded()) {
      frameProcessor.signalEndOfInput();
    }
    // If the decoder produced output, signal that it may be possible to process data again.
    return processedData;
  }

  @Override
  @Nullable
  public Format getOutputFormat() throws TransformationException {
    return encoderWrapper.getOutputFormat();
  }

  @Override
  @Nullable
  public DecoderInputBuffer getOutputBuffer() throws TransformationException {
    encoderOutputBuffer.data = encoderWrapper.getOutputBuffer();
    if (encoderOutputBuffer.data == null) {
      return null;
    }
    MediaCodec.BufferInfo bufferInfo = checkNotNull(encoderWrapper.getOutputBufferInfo());
    encoderOutputBuffer.timeUs = bufferInfo.presentationTimeUs;
    encoderOutputBuffer.setFlags(bufferInfo.flags);
    return encoderOutputBuffer;
  }

  @Override
  public void releaseOutputBuffer() throws TransformationException {
    encoderWrapper.releaseOutputBuffer(/* render= */ false);
  }

  @Override
  public boolean isEnded() {
    return encoderWrapper.isEnded();
  }

  @Override
  public void release() {
    frameProcessor.release();
    decoder.release();
    encoderWrapper.release();
  }

  /**
   * Creates a {@link TransformationRequest}, based on an original {@code TransformationRequest} and
   * parameters specifying alterations to it that indicate device support.
   *
   * @param transformationRequest The requested transformation.
   * @param hasOutputFormatRotation Whether the input video will be rotated to landscape during
   *     processing, with {@link Format#rotationDegrees} of 90 added to the output format.
   * @param requestedFormat The requested format.
   * @param supportedFormat A format supported by the device.
   * @param isToneMappedToSdr Whether tone mapping to SDR will be applied.
   * @return The created instance.
   */
  @Pure
  private static TransformationRequest createSupportedTransformationRequest(
      TransformationRequest transformationRequest,
      boolean hasOutputFormatRotation,
      Format requestedFormat,
      Format supportedFormat,
      boolean isToneMappedToSdr) {
    // TODO(b/210591626): Also update bitrate etc. once encoder configuration and fallback are
    //  implemented.
    if (transformationRequest.enableRequestSdrToneMapping == isToneMappedToSdr
        && Util.areEqual(requestedFormat.sampleMimeType, supportedFormat.sampleMimeType)
        && (hasOutputFormatRotation
            ? requestedFormat.width == supportedFormat.width
            : requestedFormat.height == supportedFormat.height)) {
      return transformationRequest;
    }
    TransformationRequest.Builder transformationRequestBuilder = transformationRequest.buildUpon();
    if (transformationRequest.enableRequestSdrToneMapping != isToneMappedToSdr) {
      checkState(isToneMappedToSdr);
      transformationRequestBuilder
          .setEnableRequestSdrToneMapping(true)
          .experimental_setEnableHdrEditing(false);
    }
    return transformationRequestBuilder
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
        && frameProcessor.getPendingInputFrameCount() == maxPendingFrameCount) {
      return false;
    }

    frameProcessor.registerInputFrame();
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

  /**
   * Wraps an {@linkplain Codec encoder} and provides its input {@link Surface}.
   *
   * <p>The encoder is created once the {@link Surface} is {@linkplain #getSurfaceInfo(int, int)
   * requested}. If it is {@linkplain #getSurfaceInfo(int, int) requested} again with different
   * dimensions, the same encoder is used and the provided dimensions stay fixed.
   */
  @VisibleForTesting
  /* package */ static final class EncoderWrapper {
    private static final String TAG = "EncoderWrapper";

    private final Codec.EncoderFactory encoderFactory;
    private final Format inputFormat;
    private final List<String> allowedOutputMimeTypes;
    private final TransformationRequest transformationRequest;
    private final FallbackListener fallbackListener;
    private final String requestedOutputMimeType;
    private final ImmutableList<String> supportedEncoderNamesForHdrEditing;

    private @MonotonicNonNull SurfaceInfo encoderSurfaceInfo;

    private volatile @MonotonicNonNull Codec encoder;
    private volatile int outputRotationDegrees;
    private volatile boolean releaseEncoder;

    public EncoderWrapper(
        Codec.EncoderFactory encoderFactory,
        Format inputFormat,
        List<String> allowedOutputMimeTypes,
        TransformationRequest transformationRequest,
        FallbackListener fallbackListener) {
      this.encoderFactory = encoderFactory;
      this.inputFormat = inputFormat;
      this.allowedOutputMimeTypes = allowedOutputMimeTypes;
      this.transformationRequest = transformationRequest;
      this.fallbackListener = fallbackListener;

      requestedOutputMimeType =
          transformationRequest.videoMimeType != null
              ? transformationRequest.videoMimeType
              : checkNotNull(inputFormat.sampleMimeType);
      supportedEncoderNamesForHdrEditing =
          EncoderUtil.getSupportedEncoderNamesForHdrEditing(
              requestedOutputMimeType, inputFormat.colorInfo);
    }

    /** Returns the {@link ColorInfo} expected from the input surface. */
    public ColorInfo getSupportedInputColor() {
      boolean isHdrEditingEnabled =
          transformationRequest.enableHdrEditing
              && !transformationRequest.enableRequestSdrToneMapping
              && !supportedEncoderNamesForHdrEditing.isEmpty();
      boolean isInputToneMapped =
          !isHdrEditingEnabled && ColorInfo.isTransferHdr(inputFormat.colorInfo);
      if (isInputToneMapped) {
        // When tone-mapping HDR to SDR is enabled, assume we get BT.709 to avoid having the encoder
        // populate default color info, which depends on the resolution.
        // TODO(b/237674316): Get the color info from the decoder output media format instead.
        return ColorInfo.SDR_BT709_LIMITED;
      }
      if (inputFormat.colorInfo == null) {
        Log.d(TAG, "colorInfo is null. Defaulting to SDR_BT709_LIMITED.");
        return ColorInfo.SDR_BT709_LIMITED;
      }
      return inputFormat.colorInfo;
    }

    @Nullable
    public SurfaceInfo getSurfaceInfo(int requestedWidth, int requestedHeight)
        throws TransformationException {
      if (releaseEncoder) {
        return null;
      }
      if (encoderSurfaceInfo != null) {
        return encoderSurfaceInfo;
      }

      // Encoders commonly support higher maximum widths than maximum heights. This may rotate the
      // frame before encoding, so the encoded frame's width >= height, and sets
      // rotationDegrees in the output Format to ensure the frame is displayed in the correct
      // orientation.
      boolean flipOrientation = requestedWidth < requestedHeight;
      if (flipOrientation) {
        int temp = requestedWidth;
        requestedWidth = requestedHeight;
        requestedHeight = temp;
        outputRotationDegrees = 90;
      }

      Format requestedEncoderFormat =
          new Format.Builder()
              .setWidth(requestedWidth)
              .setHeight(requestedHeight)
              .setRotationDegrees(0)
              .setFrameRate(inputFormat.frameRate)
              .setSampleMimeType(requestedOutputMimeType)
              .setColorInfo(getSupportedInputColor())
              .build();

      encoder =
          encoderFactory.createForVideoEncoding(requestedEncoderFormat, allowedOutputMimeTypes);

      Format encoderSupportedFormat = encoder.getConfigurationFormat();
      if (ColorInfo.isTransferHdr(requestedEncoderFormat.colorInfo)) {
        if (!requestedOutputMimeType.equals(encoderSupportedFormat.sampleMimeType)) {
          throw createEncodingException(
              new IllegalStateException("MIME type fallback unsupported with HDR editing"),
              encoderSupportedFormat);
        } else if (!supportedEncoderNamesForHdrEditing.contains(encoder.getName())) {
          throw createEncodingException(
              new IllegalStateException("Selected encoder doesn't support HDR editing"),
              encoderSupportedFormat);
        }
      }
      boolean isInputToneMapped =
          ColorInfo.isTransferHdr(inputFormat.colorInfo)
              && !ColorInfo.isTransferHdr(requestedEncoderFormat.colorInfo);
      fallbackListener.onTransformationRequestFinalized(
          createSupportedTransformationRequest(
              transformationRequest,
              /* hasOutputFormatRotation= */ flipOrientation,
              requestedEncoderFormat,
              encoderSupportedFormat,
              isInputToneMapped));

      encoderSurfaceInfo =
          new SurfaceInfo(
              encoder.getInputSurface(),
              encoderSupportedFormat.width,
              encoderSupportedFormat.height,
              outputRotationDegrees);

      if (releaseEncoder) {
        encoder.release();
      }
      return encoderSurfaceInfo;
    }

    public void signalEndOfInputStream() throws TransformationException {
      if (encoder != null) {
        encoder.signalEndOfInputStream();
      }
    }

    @Nullable
    public Format getOutputFormat() throws TransformationException {
      if (encoder == null) {
        return null;
      }
      @Nullable Format outputFormat = encoder.getOutputFormat();
      if (outputFormat != null && outputRotationDegrees != 0) {
        outputFormat = outputFormat.buildUpon().setRotationDegrees(outputRotationDegrees).build();
      }
      return outputFormat;
    }

    @Nullable
    public ByteBuffer getOutputBuffer() throws TransformationException {
      return encoder != null ? encoder.getOutputBuffer() : null;
    }

    @Nullable
    public MediaCodec.BufferInfo getOutputBufferInfo() throws TransformationException {
      return encoder != null ? encoder.getOutputBufferInfo() : null;
    }

    public void releaseOutputBuffer(boolean render) throws TransformationException {
      if (encoder != null) {
        encoder.releaseOutputBuffer(render);
      }
    }

    public boolean isEnded() {
      return encoder != null && encoder.isEnded();
    }

    public void release() {
      if (encoder != null) {
        encoder.release();
      }
      releaseEncoder = true;
    }

    private TransformationException createEncodingException(Exception cause, Format format) {
      return TransformationException.createForCodec(
          cause,
          /* isVideo= */ true,
          /* isDecoder= */ false,
          format,
          checkNotNull(encoder).getName(),
          TransformationException.ERROR_CODE_ENCODING_FAILED);
    }
  }
}
