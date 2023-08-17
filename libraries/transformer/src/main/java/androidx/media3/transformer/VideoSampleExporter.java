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

import static androidx.media3.common.ColorInfo.SDR_BT709_LIMITED;
import static androidx.media3.common.ColorInfo.SRGB_BT709_FULL;
import static androidx.media3.common.ColorInfo.isTransferHdr;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.Composition.HDR_MODE_KEEP_HDR;
import static androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC;
import static androidx.media3.transformer.Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;
import static androidx.media3.transformer.EncoderUtil.getSupportedEncodersForHdrEditing;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.VideoGraph;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.effect.DebugTraceUtil;
import androidx.media3.effect.VideoCompositorSettings;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.ByteBuffer;
import java.util.List;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.dataflow.qual.Pure;

/** Processes, encodes and muxes raw video frames. */
/* package */ final class VideoSampleExporter extends SampleExporter {

  private static final String TAG = "VideoSampleExporter";
  private final TransformerVideoGraph videoGraph;
  private final EncoderWrapper encoderWrapper;
  private final DecoderInputBuffer encoderOutputBuffer;
  private final long initialTimestampOffsetUs;

  /**
   * The timestamp of the last buffer processed before {@linkplain
   * VideoFrameProcessor.Listener#onEnded() frame processing has ended}.
   */
  private volatile long finalFramePresentationTimeUs;

  private boolean hasMuxedTimestampZero;

  public VideoSampleExporter(
      Context context,
      Format firstInputFormat,
      TransformationRequest transformationRequest,
      VideoCompositorSettings videoCompositorSettings,
      List<Effect> compositionEffects,
      VideoFrameProcessor.Factory videoFrameProcessorFactory,
      Codec.EncoderFactory encoderFactory,
      MuxerWrapper muxerWrapper,
      Consumer<ExportException> errorConsumer,
      FallbackListener fallbackListener,
      DebugViewProvider debugViewProvider,
      long initialTimestampOffsetUs,
      boolean hasMultipleInputs)
      throws ExportException {
    // TODO(b/278259383) Consider delaying configuration of VideoSampleExporter to use the decoder
    //  output format instead of the extractor output format, to match AudioSampleExporter behavior.
    super(firstInputFormat, muxerWrapper);
    this.initialTimestampOffsetUs = initialTimestampOffsetUs;
    finalFramePresentationTimeUs = C.TIME_UNSET;

    ColorInfo decoderInputColor;
    if (firstInputFormat.colorInfo == null || !firstInputFormat.colorInfo.isDataSpaceValid()) {
      Log.d(TAG, "colorInfo is null or invalid. Defaulting to SDR_BT709_LIMITED.");
      decoderInputColor = ColorInfo.SDR_BT709_LIMITED;
    } else {
      decoderInputColor = firstInputFormat.colorInfo;
    }
    encoderWrapper =
        new EncoderWrapper(
            encoderFactory,
            firstInputFormat.buildUpon().setColorInfo(decoderInputColor).build(),
            muxerWrapper.getSupportedSampleMimeTypes(C.TRACK_TYPE_VIDEO),
            transformationRequest,
            fallbackListener);
    encoderOutputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);

    @Composition.HdrMode int hdrModeAfterFallback = encoderWrapper.getHdrModeAfterFallback();
    boolean isMediaCodecToneMapping =
        hdrModeAfterFallback == HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
            && ColorInfo.isTransferHdr(decoderInputColor);
    ColorInfo videoGraphInputColor =
        isMediaCodecToneMapping ? SDR_BT709_LIMITED : decoderInputColor;

    boolean isGlToneMapping =
        hdrModeAfterFallback == HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            && ColorInfo.isTransferHdr(decoderInputColor);
    ColorInfo videoGraphOutputColor;
    if (videoGraphInputColor.colorTransfer == C.COLOR_TRANSFER_SRGB) {
      // The sRGB color transfer is only used for images, so when an image gets transcoded into a
      // video, we use the SMPTE 170M transfer function for the resulting video.
      videoGraphOutputColor = SDR_BT709_LIMITED;
    } else if (isGlToneMapping) {
      // For consistency with the Android platform, OpenGL tone mapping outputs colors with
      // C.COLOR_TRANSFER_GAMMA_2_2 instead of C.COLOR_TRANSFER_SDR, and outputs this as
      // C.COLOR_TRANSFER_SDR to the encoder.
      videoGraphOutputColor =
          new ColorInfo.Builder()
              .setColorSpace(C.COLOR_SPACE_BT709)
              .setColorRange(C.COLOR_RANGE_LIMITED)
              .setColorTransfer(C.COLOR_TRANSFER_GAMMA_2_2)
              .build();
    } else {
      videoGraphOutputColor = videoGraphInputColor;
    }

    try {
      videoGraph =
          new VideoGraphWrapper(
              context,
              hasMultipleInputs
                  ? new TransformerMultipleInputVideoGraph.Factory()
                  : new TransformerSingleInputVideoGraph.Factory(videoFrameProcessorFactory),
              videoGraphInputColor,
              videoGraphOutputColor,
              errorConsumer,
              debugViewProvider,
              videoCompositorSettings,
              compositionEffects);
      videoGraph.initialize();
    } catch (VideoFrameProcessingException e) {
      throw ExportException.createForVideoFrameProcessingException(e);
    }
  }

  @Override
  public GraphInput getInput(EditedMediaItem editedMediaItem, Format format)
      throws ExportException {
    try {
      return videoGraph.createInput();
    } catch (VideoFrameProcessingException e) {
      throw ExportException.createForVideoFrameProcessingException(e);
    }
  }

  @Override
  public void release() {
    videoGraph.release();
    encoderWrapper.release();
  }

  @Override
  @Nullable
  protected Format getMuxerInputFormat() throws ExportException {
    return encoderWrapper.getOutputFormat();
  }

  @Override
  @Nullable
  protected DecoderInputBuffer getMuxerInputBuffer() throws ExportException {
    encoderOutputBuffer.data = encoderWrapper.getOutputBuffer();
    if (encoderOutputBuffer.data == null) {
      return null;
    }
    MediaCodec.BufferInfo bufferInfo = checkNotNull(encoderWrapper.getOutputBufferInfo());
    if (bufferInfo.presentationTimeUs == 0) {
      // Internal ref b/235045165: Some encoder incorrectly set a zero presentation time on the
      // penultimate buffer (before EOS), and sets the actual timestamp on the EOS buffer. Use the
      // last processed frame presentation time instead.
      if (videoGraph.hasProducedFrameWithTimestampZero() == hasMuxedTimestampZero
          && finalFramePresentationTimeUs != C.TIME_UNSET
          && bufferInfo.size > 0) {
        bufferInfo.presentationTimeUs = finalFramePresentationTimeUs;
      } else {
        hasMuxedTimestampZero = true;
      }
    }
    DebugTraceUtil.logEvent(
        DebugTraceUtil.EVENT_ENCODER_ENCODED_FRAME, bufferInfo.presentationTimeUs);
    encoderOutputBuffer.timeUs = bufferInfo.presentationTimeUs;
    encoderOutputBuffer.setFlags(bufferInfo.flags);
    return encoderOutputBuffer;
  }

  @Override
  protected void releaseMuxerInputBuffer() throws ExportException {
    encoderWrapper.releaseOutputBuffer(/* render= */ false);
  }

  @Override
  protected boolean isMuxerInputEnded() {
    return encoderWrapper.isEnded();
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
    /** MIME type to use for output video if the input type is not a video. */
    private static final String DEFAULT_OUTPUT_MIME_TYPE = MimeTypes.VIDEO_H265;

    private final Codec.EncoderFactory encoderFactory;
    private final Format inputFormat;
    private final List<String> muxerSupportedMimeTypes;
    private final TransformationRequest transformationRequest;
    private final FallbackListener fallbackListener;
    private final String requestedOutputMimeType;
    private final @Composition.HdrMode int hdrModeAfterFallback;

    private @MonotonicNonNull SurfaceInfo encoderSurfaceInfo;

    private volatile @MonotonicNonNull Codec encoder;
    private volatile int outputRotationDegrees;
    private volatile boolean releaseEncoder;

    public EncoderWrapper(
        Codec.EncoderFactory encoderFactory,
        Format inputFormat,
        List<String> muxerSupportedMimeTypes,
        TransformationRequest transformationRequest,
        FallbackListener fallbackListener) {
      checkArgument(inputFormat.colorInfo != null);
      this.encoderFactory = encoderFactory;
      this.inputFormat = inputFormat;
      this.muxerSupportedMimeTypes = muxerSupportedMimeTypes;
      this.transformationRequest = transformationRequest;
      this.fallbackListener = fallbackListener;
      Pair<String, Integer> outputMimeTypeAndHdrModeAfterFallback =
          getRequestedOutputMimeTypeAndHdrModeAfterFallback(inputFormat, transformationRequest);
      requestedOutputMimeType = outputMimeTypeAndHdrModeAfterFallback.first;
      hdrModeAfterFallback = outputMimeTypeAndHdrModeAfterFallback.second;
    }

    private static Pair<String, Integer> getRequestedOutputMimeTypeAndHdrModeAfterFallback(
        Format inputFormat, TransformationRequest transformationRequest) {
      String inputSampleMimeType = checkNotNull(inputFormat.sampleMimeType);
      String requestedOutputMimeType;
      if (transformationRequest.videoMimeType != null) {
        requestedOutputMimeType = transformationRequest.videoMimeType;
      } else if (MimeTypes.isImage(inputSampleMimeType)) {
        requestedOutputMimeType = DEFAULT_OUTPUT_MIME_TYPE;
      } else {
        requestedOutputMimeType = inputSampleMimeType;
      }

      // HdrMode fallback is only supported from HDR_MODE_KEEP_HDR to
      // HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL.
      @Composition.HdrMode int hdrMode = transformationRequest.hdrMode;
      if (hdrMode == HDR_MODE_KEEP_HDR && isTransferHdr(inputFormat.colorInfo)) {
        ImmutableList<MediaCodecInfo> hdrEncoders =
            getSupportedEncodersForHdrEditing(requestedOutputMimeType, inputFormat.colorInfo);
        if (hdrEncoders.isEmpty()) {
          @Nullable
          String alternativeMimeType = MediaCodecUtil.getAlternativeCodecMimeType(inputFormat);
          if (alternativeMimeType != null) {
            requestedOutputMimeType = alternativeMimeType;
            hdrEncoders =
                getSupportedEncodersForHdrEditing(alternativeMimeType, inputFormat.colorInfo);
          }
        }
        if (hdrEncoders.isEmpty()) {
          hdrMode = HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;
        }
      }

      return Pair.create(requestedOutputMimeType, hdrMode);
    }

    public @Composition.HdrMode int getHdrModeAfterFallback() {
      return hdrModeAfterFallback;
    }

    @Nullable
    public SurfaceInfo getSurfaceInfo(int requestedWidth, int requestedHeight)
        throws ExportException {
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
      if (requestedWidth < requestedHeight) {
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
          encoderFactory.createForVideoEncoding(
              requestedEncoderFormat
                  .buildUpon()
                  .setSampleMimeType(
                      findSupportedMimeTypeForEncoderAndMuxer(
                          requestedEncoderFormat, muxerSupportedMimeTypes))
                  .build());

      Format actualEncoderFormat = encoder.getConfigurationFormat();

      fallbackListener.onTransformationRequestFinalized(
          createSupportedTransformationRequest(
              transformationRequest,
              /* hasOutputFormatRotation= */ outputRotationDegrees != 0,
              requestedEncoderFormat,
              actualEncoderFormat,
              hdrModeAfterFallback));

      encoderSurfaceInfo =
          new SurfaceInfo(
              encoder.getInputSurface(),
              actualEncoderFormat.width,
              actualEncoderFormat.height,
              outputRotationDegrees);

      if (releaseEncoder) {
        encoder.release();
      }
      return encoderSurfaceInfo;
    }

    /** Returns the {@link ColorInfo} expected from the input surface. */
    private ColorInfo getSupportedInputColor() {
      boolean isInputToneMapped =
          isTransferHdr(inputFormat.colorInfo) && hdrModeAfterFallback != HDR_MODE_KEEP_HDR;
      if (isInputToneMapped) {
        // When tone-mapping HDR to SDR is enabled, assume we get BT.709 to avoid having the encoder
        // populate default color info, which depends on the resolution.
        return ColorInfo.SDR_BT709_LIMITED;
      }
      if (SRGB_BT709_FULL.equals(inputFormat.colorInfo)) {
        return ColorInfo.SDR_BT709_LIMITED;
      }
      return checkNotNull(inputFormat.colorInfo);
    }

    /**
     * Creates a {@link TransformationRequest}, based on an original {@code TransformationRequest}
     * and parameters specifying alterations to it that indicate device support.
     *
     * @param transformationRequest The requested transformation.
     * @param hasOutputFormatRotation Whether the input video will be rotated to landscape during
     *     processing, with {@link Format#rotationDegrees} of 90 added to the output format.
     * @param requestedFormat The requested format.
     * @param supportedFormat A format supported by the device.
     * @param supportedHdrMode A {@link Composition.HdrMode} supported by the device.
     * @return The created instance.
     */
    @Pure
    private static TransformationRequest createSupportedTransformationRequest(
        TransformationRequest transformationRequest,
        boolean hasOutputFormatRotation,
        Format requestedFormat,
        Format supportedFormat,
        @Composition.HdrMode int supportedHdrMode) {
      // TODO(b/259570024): Consider including bitrate in the revised fallback design.

      TransformationRequest.Builder supportedRequestBuilder = transformationRequest.buildUpon();
      if (transformationRequest.hdrMode != supportedHdrMode) {
        supportedRequestBuilder.setHdrMode(supportedHdrMode);
      }

      if (!Util.areEqual(requestedFormat.sampleMimeType, supportedFormat.sampleMimeType)) {
        supportedRequestBuilder.setVideoMimeType(supportedFormat.sampleMimeType);
      }

      if (hasOutputFormatRotation) {
        if (requestedFormat.width != supportedFormat.width) {
          supportedRequestBuilder.setResolution(/* outputHeight= */ supportedFormat.width);
        }
      } else if (requestedFormat.height != supportedFormat.height) {
        supportedRequestBuilder.setResolution(supportedFormat.height);
      }

      return supportedRequestBuilder.build();
    }

    public void signalEndOfInputStream() throws ExportException {
      if (encoder != null) {
        encoder.signalEndOfInputStream();
      }
    }

    @Nullable
    public Format getOutputFormat() throws ExportException {
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
    public ByteBuffer getOutputBuffer() throws ExportException {
      return encoder != null ? encoder.getOutputBuffer() : null;
    }

    @Nullable
    public MediaCodec.BufferInfo getOutputBufferInfo() throws ExportException {
      return encoder != null ? encoder.getOutputBufferInfo() : null;
    }

    public void releaseOutputBuffer(boolean render) throws ExportException {
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
  }

  private final class VideoGraphWrapper implements TransformerVideoGraph, VideoGraph.Listener {

    private final TransformerVideoGraph videoGraph;
    private final Consumer<ExportException> errorConsumer;

    public VideoGraphWrapper(
        Context context,
        TransformerVideoGraph.Factory videoGraphFactory,
        ColorInfo videoFrameProcessorInputColor,
        ColorInfo videoFrameProcessorOutputColor,
        Consumer<ExportException> errorConsumer,
        DebugViewProvider debugViewProvider,
        VideoCompositorSettings videoCompositorSettings,
        List<Effect> compositionEffects)
        throws VideoFrameProcessingException {
      this.errorConsumer = errorConsumer;
      // To satisfy the nullness checker by declaring an initialized this reference used in the
      // videoGraphFactory.create method
      @SuppressWarnings("nullness:assignment")
      @Initialized
      VideoGraphWrapper thisRef = this;
      videoGraph =
          videoGraphFactory.create(
              context,
              videoFrameProcessorInputColor,
              videoFrameProcessorOutputColor,
              debugViewProvider,
              /* listener= */ thisRef,
              /* listenerExecutor= */ MoreExecutors.directExecutor(),
              videoCompositorSettings,
              compositionEffects,
              initialTimestampOffsetUs);
    }

    @Override
    public void onOutputSizeChanged(int width, int height) {
      @Nullable SurfaceInfo surfaceInfo = null;
      try {
        surfaceInfo = encoderWrapper.getSurfaceInfo(width, height);
      } catch (ExportException e) {
        errorConsumer.accept(e);
      }
      setOutputSurfaceInfo(surfaceInfo);
    }

    @Override
    public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
      // Do nothing.
    }

    @Override
    public void onEnded(long finalFramePresentationTimeUs) {
      VideoSampleExporter.this.finalFramePresentationTimeUs = finalFramePresentationTimeUs;
      try {
        encoderWrapper.signalEndOfInputStream();
      } catch (ExportException e) {
        errorConsumer.accept(e);
      }
    }

    @Override
    public void onError(VideoFrameProcessingException e) {
      errorConsumer.accept(ExportException.createForVideoFrameProcessingException(e));
    }

    @Override
    public void initialize() throws VideoFrameProcessingException {
      videoGraph.initialize();
    }

    @Override
    public int registerInput() throws VideoFrameProcessingException {
      return videoGraph.registerInput();
    }

    @Override
    public VideoFrameProcessor getProcessor(int inputId) {
      return videoGraph.getProcessor(inputId);
    }

    @Override
    public GraphInput createInput() throws VideoFrameProcessingException {
      return videoGraph.createInput();
    }

    @Override
    public void setOutputSurfaceInfo(@Nullable SurfaceInfo outputSurfaceInfo) {
      videoGraph.setOutputSurfaceInfo(outputSurfaceInfo);
    }

    @Override
    public boolean hasProducedFrameWithTimestampZero() {
      return videoGraph.hasProducedFrameWithTimestampZero();
    }

    @Override
    public void release() {
      videoGraph.release();
    }
  }
}
