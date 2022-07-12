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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Pipeline to decode video samples, apply transformations on the raw samples, and re-encode them.
 */
/* package */ final class VideoTranscodingSamplePipeline implements SamplePipeline {
  private static final String TAG = "VideoTranscodingSP";

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
      ImmutableList<GlEffect> effects,
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
      effectsListBuilder.add(Presentation.createForHeight(transformationRequest.outputHeight));
    }

    encoderWrapper =
        new EncoderWrapper(
            encoderFactory,
            inputFormat,
            allowedOutputMimeTypes,
            transformationRequest,
            fallbackListener);

    boolean enableRequestSdrToneMapping = transformationRequest.enableRequestSdrToneMapping;
    // TODO(b/237674316): While HLG10 is correctly reported, HDR10 currently will be incorrectly
    //  processed as SDR, because the inputFormat.colorInfo reports the wrong value.
    boolean useHdr =
        transformationRequest.enableHdrEditing && ColorInfo.isHdr(inputFormat.colorInfo);
    if (useHdr && !encoderWrapper.supportsHdr()) {
      useHdr = false;
      enableRequestSdrToneMapping = true;
      encoderWrapper.signalFallbackToSdr();
    }

    try {
      frameProcessor =
          GlEffectsFrameProcessor.create(
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
                public void onFrameProcessingError(FrameProcessingException exception) {
                  asyncErrorListener.onTransformationException(
                      TransformationException.createForFrameProcessingException(
                          exception, TransformationException.ERROR_CODE_GL_PROCESSING_FAILED));
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
              streamOffsetUs,
              effectsListBuilder.build(),
              debugViewProvider,
              // HDR is only used if the MediaCodec encoder supports FEATURE_HdrEditing. This
              // implies that the OpenGL EXT_YUV_target extension is supported and hence the
              // GlEffectsFrameProcessor also supports HDR.
              useHdr);
    } catch (FrameProcessingException e) {
      throw TransformationException.createForFrameProcessingException(
          e, TransformationException.ERROR_CODE_GL_INIT_FAILED);
    }
    frameProcessor.setInputFrameInfo(
        new FrameInfo(decodedWidth, decodedHeight, inputFormat.pixelWidthHeightRatio));

    decoder =
        decoderFactory.createForVideoDecoding(
            inputFormat, frameProcessor.getInputSurface(), enableRequestSdrToneMapping);
    // TODO(b/236316454): Check in the decoder output format whether tone-mapping was actually
    //  applied and throw an exception if not.
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
      frameProcessor.signalEndOfInputStream();
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
   * Creates a fallback transformation request to execute, based on device-specific support.
   *
   * @param transformationRequest The requested transformation.
   * @param hasOutputFormatRotation Whether the input video will be rotated to landscape during
   *     processing, with {@link Format#rotationDegrees} of 90 added to the output format.
   * @param requestedFormat The requested format.
   * @param supportedFormat A format supported by the device.
   * @param fallbackToSdr Whether HDR editing was requested via the TransformationRequest or
   *     inferred from the input and tone-mapping to SDR was used instead due to lack of encoder
   *     capabilities.
   */
  @Pure
  private static TransformationRequest createFallbackTransformationRequest(
      TransformationRequest transformationRequest,
      boolean hasOutputFormatRotation,
      Format requestedFormat,
      Format supportedFormat,
      boolean fallbackToSdr) {
    // TODO(b/210591626): Also update bitrate etc. once encoder configuration and fallback are
    //  implemented.
    if (!fallbackToSdr
        && Util.areEqual(requestedFormat.sampleMimeType, supportedFormat.sampleMimeType)
        && (hasOutputFormatRotation
            ? requestedFormat.width == supportedFormat.width
            : requestedFormat.height == supportedFormat.height)) {
      return transformationRequest;
    }
    TransformationRequest.Builder transformationRequestBuilder = transformationRequest.buildUpon();
    if (fallbackToSdr) {
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

    private final Codec.EncoderFactory encoderFactory;
    private final Format inputFormat;
    private final List<String> allowedOutputMimeTypes;
    private final TransformationRequest transformationRequest;
    private final FallbackListener fallbackListener;
    private final HashSet<String> hdrMediaCodecNames;

    private @MonotonicNonNull SurfaceInfo encoderSurfaceInfo;

    private volatile @MonotonicNonNull Codec encoder;
    private volatile int outputRotationDegrees;
    private volatile boolean releaseEncoder;
    private boolean fallbackToSdr;

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

      hdrMediaCodecNames = new HashSet<>();
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
              .setSampleMimeType(
                  transformationRequest.videoMimeType != null
                      ? transformationRequest.videoMimeType
                      : inputFormat.sampleMimeType)
              .setColorInfo(fallbackToSdr ? null : inputFormat.colorInfo)
              .build();

      encoder =
          encoderFactory.createForVideoEncoding(requestedEncoderFormat, allowedOutputMimeTypes);
      if (!hdrMediaCodecNames.isEmpty() && !hdrMediaCodecNames.contains(encoder.getName())) {
        Log.d(
            TAG,
            "Selected encoder "
                + encoder.getName()
                + " does not report sufficient HDR capabilities");
      }

      Format encoderSupportedFormat = encoder.getConfigurationFormat();
      fallbackListener.onTransformationRequestFinalized(
          createFallbackTransformationRequest(
              transformationRequest,
              /* hasOutputFormatRotation= */ flipOrientation,
              requestedEncoderFormat,
              encoderSupportedFormat,
              fallbackToSdr));

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

    /**
     * Checks whether at least one MediaCodec encoder on the device has sufficient capabilities to
     * encode HDR (only checks support for HLG at this time).
     */
    public boolean supportsHdr() {
      if (Util.SDK_INT < 31) {
        return false;
      }

      // The only output MIME type that Transformer currently supports that can be used with HDR
      // is H265/HEVC. So we assume that the EncoderFactory will pick this if HDR is requested.
      String mimeType = MimeTypes.VIDEO_H265;

      List<MediaCodecInfo> mediaCodecInfos = EncoderSelector.DEFAULT.selectEncoderInfos(mimeType);
      for (int i = 0; i < mediaCodecInfos.size(); i++) {
        MediaCodecInfo mediaCodecInfo = mediaCodecInfos.get(i);
        if (EncoderUtil.isFeatureSupported(
            mediaCodecInfo, mimeType, MediaCodecInfo.CodecCapabilities.FEATURE_HdrEditing)) {
          for (MediaCodecInfo.CodecProfileLevel capabilities :
              mediaCodecInfo.getCapabilitiesForType(MimeTypes.VIDEO_H265).profileLevels) {
            // TODO(b/227624622): What profile to check depends on the HDR format. Once other
            //  formats besides HLG are supported, check the corresponding profiles here.
            if (capabilities.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10) {
              return hdrMediaCodecNames.add(mediaCodecInfo.getCanonicalName());
            }
          }
        }
      }
      return !hdrMediaCodecNames.isEmpty();
    }

    public void signalFallbackToSdr() {
      checkState(encoder == null, "Fallback to SDR is only allowed before encoder initialization");
      fallbackToSdr = true;
      hdrMediaCodecNames.clear();
    }
  }
}
