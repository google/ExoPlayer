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

import static com.google.android.exoplayer2.transformer.EncoderUtil.getSupportedEncodersForHdrEditing;
import static com.google.android.exoplayer2.transformer.TransformationRequest.HDR_MODE_KEEP_HDR;
import static com.google.android.exoplayer2.transformer.TransformationRequest.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC;
import static com.google.android.exoplayer2.transformer.TransformationRequest.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.VideoFrameProcessor.INPUT_TYPE_BITMAP;
import static com.google.android.exoplayer2.util.VideoFrameProcessor.INPUT_TYPE_SURFACE;
import static com.google.android.exoplayer2.util.VideoFrameProcessor.INPUT_TYPE_TEXTURE_ID;
import static com.google.android.exoplayer2.video.ColorInfo.SDR_BT709_LIMITED;
import static com.google.android.exoplayer2.video.ColorInfo.SRGB_BT709_FULL;
import static com.google.android.exoplayer2.video.ColorInfo.isTransferHdr;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.util.Pair;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.effect.DebugTraceUtil;
import com.google.android.exoplayer2.effect.Presentation;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.util.Consumer;
import com.google.android.exoplayer2.util.DebugViewProvider;
import com.google.android.exoplayer2.util.Effect;
import com.google.android.exoplayer2.util.FrameInfo;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.OnInputFrameProcessedListener;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.util.SurfaceInfo;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;
import com.google.android.exoplayer2.util.VideoFrameProcessor;
import com.google.android.exoplayer2.video.ColorInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Pipeline to process, re-encode and mux raw video frames.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class VideoSamplePipeline extends SamplePipeline {

  private static final String TAG = "VideoSamplePipeline";
  private final AtomicLong mediaItemOffsetUs;
  private final VideoFrameProcessor videoFrameProcessor;
  private final ColorInfo videoFrameProcessorInputColor;
  private final EncoderWrapper encoderWrapper;
  private final DecoderInputBuffer encoderOutputBuffer;

  private volatile boolean encoderExpectsTimestampZero;
  /**
   * The timestamp of the last buffer processed before {@linkplain
   * VideoFrameProcessor.Listener#onEnded() frame processing has ended}.
   */
  private volatile long finalFramePresentationTimeUs;

  public VideoSamplePipeline(
      Context context,
      Format firstInputFormat,
      TransformationRequest transformationRequest,
      ImmutableList<Effect> effects,
      @Nullable Presentation presentation,
      VideoFrameProcessor.Factory videoFrameProcessorFactory,
      Codec.EncoderFactory encoderFactory,
      MuxerWrapper muxerWrapper,
      Consumer<ExportException> errorConsumer,
      FallbackListener fallbackListener,
      DebugViewProvider debugViewProvider)
      throws ExportException {
    // TODO(b/262693177) Add tests for input format change.
    // TODO(b/278259383) Consider delaying configuration of VideoSamplePipeline to use the decoder
    // output format instead of the extractor output format, to match AudioSamplePipeline behavior.
    super(firstInputFormat, muxerWrapper);

    mediaItemOffsetUs = new AtomicLong();
    finalFramePresentationTimeUs = C.TIME_UNSET;

    encoderOutputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);

    ColorInfo decoderInputColor;
    if (firstInputFormat.colorInfo == null || !firstInputFormat.colorInfo.isValid()) {
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

    boolean isMediaCodecToneMapping =
        encoderWrapper.getHdrModeAfterFallback() == HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
            && ColorInfo.isTransferHdr(decoderInputColor);
    videoFrameProcessorInputColor = isMediaCodecToneMapping ? SDR_BT709_LIMITED : decoderInputColor;

    boolean isGlToneMapping =
        ColorInfo.isTransferHdr(decoderInputColor)
            && transformationRequest.hdrMode == HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL;
    ColorInfo videoFrameProcessorOutputColor;
    if (videoFrameProcessorInputColor.colorTransfer == C.COLOR_TRANSFER_SRGB) {
      // The sRGB color transfer is only used for images, so when an image gets transcoded into a
      // video, we use the SMPTE 170M transfer function for the resulting video.
      videoFrameProcessorOutputColor = SDR_BT709_LIMITED;
    } else if (isGlToneMapping) {
      // For consistency with the Android platform, OpenGL tone mapping outputs colors with
      // C.COLOR_TRANSFER_GAMMA_2_2 instead of C.COLOR_TRANSFER_SDR, and outputs this as
      // C.COLOR_TRANSFER_SDR to the encoder.
      videoFrameProcessorOutputColor =
          new ColorInfo.Builder()
              .setColorSpace(C.COLOR_SPACE_BT709)
              .setColorRange(C.COLOR_RANGE_LIMITED)
              .setColorTransfer(C.COLOR_TRANSFER_GAMMA_2_2)
              .build();
    } else {
      videoFrameProcessorOutputColor = videoFrameProcessorInputColor;
    }

    List<Effect> effectsWithPresentation = new ArrayList<>(effects);
    if (presentation != null) {
      effectsWithPresentation.add(presentation);
    }
    try {
      videoFrameProcessor =
          videoFrameProcessorFactory.create(
              context,
              effectsWithPresentation,
              debugViewProvider,
              videoFrameProcessorInputColor,
              videoFrameProcessorOutputColor,
              /* renderFramesAutomatically= */ true,
              MoreExecutors.directExecutor(),
              new VideoFrameProcessor.Listener() {
                private long lastProcessedFramePresentationTimeUs;

                @Override
                public void onOutputSizeChanged(int width, int height) {
                  try {
                    checkNotNull(videoFrameProcessor)
                        .setOutputSurfaceInfo(encoderWrapper.getSurfaceInfo(width, height));
                  } catch (ExportException exception) {
                    errorConsumer.accept(exception);
                  }
                }

                @Override
                public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
                  // Frames are rendered automatically.
                  if (presentationTimeUs == 0) {
                    encoderExpectsTimestampZero = true;
                  }
                  lastProcessedFramePresentationTimeUs = presentationTimeUs;
                }

                @Override
                public void onError(VideoFrameProcessingException exception) {
                  errorConsumer.accept(
                      ExportException.createForVideoFrameProcessingException(exception));
                }

                @Override
                public void onEnded() {
                  VideoSamplePipeline.this.finalFramePresentationTimeUs =
                      lastProcessedFramePresentationTimeUs;
                  try {
                    encoderWrapper.signalEndOfInputStream();
                  } catch (ExportException exception) {
                    errorConsumer.accept(exception);
                  }
                }
              });
    } catch (VideoFrameProcessingException e) {
      throw ExportException.createForVideoFrameProcessingException(e);
    }
  }

  @Override
  public void onMediaItemChanged(
      EditedMediaItem editedMediaItem,
      long durationUs,
      @Nullable Format trackFormat,
      boolean isLast) {
    if (trackFormat != null) {
      Size decodedSize = getDecodedSize(trackFormat);
      videoFrameProcessor.registerInputStream(
          getInputType(checkNotNull(trackFormat.sampleMimeType)));
      videoFrameProcessor.setInputFrameInfo(
          new FrameInfo.Builder(decodedSize.getWidth(), decodedSize.getHeight())
              .setPixelWidthHeightRatio(trackFormat.pixelWidthHeightRatio)
              .setOffsetToAddUs(mediaItemOffsetUs.get())
              .build());
    }
    mediaItemOffsetUs.addAndGet(durationUs);
  }

  @Override
  public boolean queueInputBitmap(Bitmap inputBitmap, long durationUs, int frameRate) {
    videoFrameProcessor.queueInputBitmap(inputBitmap, durationUs, frameRate);
    return true;
  }

  @Override
  public void setOnInputFrameProcessedListener(OnInputFrameProcessedListener listener) {
    videoFrameProcessor.setOnInputFrameProcessedListener(listener);
  }

  @Override
  public boolean queueInputTexture(int texId, long presentationTimeUs) {
    videoFrameProcessor.queueInputTexture(texId, presentationTimeUs);
    return true;
  }

  @Override
  public Surface getInputSurface() {
    return videoFrameProcessor.getInputSurface();
  }

  @Override
  public ColorInfo getExpectedInputColorInfo() {
    return videoFrameProcessorInputColor;
  }

  @Override
  public boolean registerVideoFrame(long presentationTimeUs) {
    videoFrameProcessor.registerInputFrame();
    return true;
  }

  @Override
  public int getPendingVideoFrameCount() {
    return videoFrameProcessor.getPendingInputFrameCount();
  }

  @Override
  public void signalEndOfVideoInput() {
    videoFrameProcessor.signalEndOfInput();
  }

  @Override
  public void release() {
    videoFrameProcessor.release();
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
      if (encoderExpectsTimestampZero) {
        encoderExpectsTimestampZero = false;
      } else if (finalFramePresentationTimeUs != C.TIME_UNSET && bufferInfo.size > 0) {
        bufferInfo.presentationTimeUs = finalFramePresentationTimeUs;
      }
    }
    DebugTraceUtil.recordEncodedFrame();
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

  private static @VideoFrameProcessor.InputType int getInputType(String sampleMimeType) {
    if (MimeTypes.isImage(sampleMimeType)) {
      return INPUT_TYPE_BITMAP;
    }
    if (sampleMimeType.equals(MimeTypes.VIDEO_RAW)) {
      return INPUT_TYPE_TEXTURE_ID;
    }
    if (MimeTypes.isVideo(sampleMimeType)) {
      return INPUT_TYPE_SURFACE;
    }
    throw new IllegalArgumentException("MIME type not supported " + sampleMimeType);
  }

  private static Size getDecodedSize(Format format) {
    // The decoder rotates encoded frames for display by firstInputFormat.rotationDegrees.
    int decodedWidth = (format.rotationDegrees % 180 == 0) ? format.width : format.height;
    int decodedHeight = (format.rotationDegrees % 180 == 0) ? format.height : format.width;
    return new Size(decodedWidth, decodedHeight);
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
    private final @TransformationRequest.HdrMode int hdrModeAfterFallback;

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
      // HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC.
      @TransformationRequest.HdrMode int hdrMode = transformationRequest.hdrMode;
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
          hdrMode = HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC;
        }
      }

      return Pair.create(requestedOutputMimeType, hdrMode);
    }

    public @TransformationRequest.HdrMode int getHdrModeAfterFallback() {
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
     * @param supportedHdrMode A {@link TransformationRequest.HdrMode} supported by the device.
     * @return The created instance.
     */
    @Pure
    private static TransformationRequest createSupportedTransformationRequest(
        TransformationRequest transformationRequest,
        boolean hasOutputFormatRotation,
        Format requestedFormat,
        Format supportedFormat,
        @TransformationRequest.HdrMode int supportedHdrMode) {
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
}
