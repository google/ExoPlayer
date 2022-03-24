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
import static androidx.media3.common.util.Util.SDK_INT;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Size;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.Format;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.List;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Pipeline to decode video samples, apply transformations on the raw samples, and re-encode them.
 */
/* package */ final class VideoTranscodingSamplePipeline implements SamplePipeline {

  private final int outputRotationDegrees;
  private final DecoderInputBuffer decoderInputBuffer;
  private final Codec decoder;

  private final FrameProcessorChain frameProcessorChain;

  private final Codec encoder;
  private final DecoderInputBuffer encoderOutputBuffer;

  private boolean signaledEndOfStreamToEncoder;

  public VideoTranscodingSamplePipeline(
      Context context,
      Format inputFormat,
      TransformationRequest transformationRequest,
      Codec.DecoderFactory decoderFactory,
      Codec.EncoderFactory encoderFactory,
      List<String> allowedOutputMimeTypes,
      FallbackListener fallbackListener,
      Transformer.DebugViewProvider debugViewProvider)
      throws TransformationException {
    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderOutputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);

    // The decoder rotates encoded frames for display by inputFormat.rotationDegrees.
    int decodedWidth =
        (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.width : inputFormat.height;
    int decodedHeight =
        (inputFormat.rotationDegrees % 180 == 0) ? inputFormat.height : inputFormat.width;

    // TODO(b/213190310): Don't create a ScaleToFitFrameProcessor if scale and rotation are unset.
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor.Builder(context)
            .setScale(transformationRequest.scaleX, transformationRequest.scaleY)
            .setRotationDegrees(transformationRequest.rotationDegrees)
            .build();
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(context)
            .setResolution(transformationRequest.outputHeight)
            .build();
    // TODO(b/214975934): Allow a list of frame processors to be passed into the sample pipeline.
    ImmutableList<GlFrameProcessor> frameProcessors =
        ImmutableList.of(scaleToFitFrameProcessor, presentationFrameProcessor);
    List<Size> frameProcessorSizes =
        FrameProcessorChain.configureSizes(decodedWidth, decodedHeight, frameProcessors);
    Size requestedEncoderSize = Iterables.getLast(frameProcessorSizes);
    // TODO(b/213190310): Move output rotation configuration to PresentationFrameProcessor.
    outputRotationDegrees = presentationFrameProcessor.getOutputRotationDegrees();

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

    // TODO(b/218488308): Allow the final GlFrameProcessor to be re-configured if its output size
    //  has to change due to encoder fallback or append another GlFrameProcessor.
    frameProcessorSizes.set(
        frameProcessorSizes.size() - 1,
        new Size(encoderSupportedFormat.width, encoderSupportedFormat.height));
    frameProcessorChain =
        FrameProcessorChain.create(
            context,
            inputFormat.pixelWidthHeightRatio,
            frameProcessors,
            frameProcessorSizes,
            /* outputSurface= */ encoder.getInputSurface(),
            transformationRequest.enableHdrEditing,
            debugViewProvider);

    decoder =
        decoderFactory.createForVideoDecoding(
            inputFormat, frameProcessorChain.createInputSurface());
  }

  @Override
  @Nullable
  public DecoderInputBuffer dequeueInputBuffer() throws TransformationException {
    return decoder.maybeDequeueInputBuffer(decoderInputBuffer) ? decoderInputBuffer : null;
  }

  @Override
  public void queueInputBuffer() throws TransformationException {
    decoder.queueInputBuffer(decoderInputBuffer);
  }

  @Override
  public boolean processData() throws TransformationException {
    frameProcessorChain.getAndRethrowBackgroundExceptions();
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

    boolean canProcessMoreDataImmediately = false;
    if (SDK_INT >= 29
        && !(("samsung".equals(Util.MANUFACTURER) || "OnePlus".equals(Util.MANUFACTURER))
            && SDK_INT < 31)) {
      // TODO(b/213455700): Fix Samsung and OnePlus devices filling the decoder in processDataV29().
      processDataV29();
    } else {
      canProcessMoreDataImmediately = processDataDefault();
    }
    if (decoder.isEnded()) {
      frameProcessorChain.signalEndOfInputStream();
    }
    return canProcessMoreDataImmediately;
  }

  /**
   * Processes input data from API 29.
   *
   * <p>In this method the decoder could decode multiple frames in one invocation; as compared to
   * {@link #processDataDefault()}, in which one frame is decoded in each invocation. Consequently,
   * if {@link FrameProcessorChain} processes frames slower than the decoder, decoded frames are
   * queued up in the decoder's output surface.
   *
   * <p>Prior to API 29, decoders may drop frames to keep their output surface from growing out of
   * bound; while after API 29, the {@link MediaFormat#KEY_ALLOW_FRAME_DROP} key prevents frame
   * dropping even when the surface is full. As dropping random frames is not acceptable in {@code
   * Transformer}, using this method requires API level 29 or higher.
   */
  @RequiresApi(29)
  private void processDataV29() throws TransformationException {
    while (maybeProcessDecoderOutput()) {}
  }

  /**
   * Processes at most one input frame and returns whether a frame was processed.
   *
   * <p>Only renders decoder output to the {@link FrameProcessorChain}'s input surface if the {@link
   * FrameProcessorChain} has finished processing the previous frame.
   */
  private boolean processDataDefault() throws TransformationException {
    // TODO(b/214975934): Check whether this can be converted to a while-loop like processDataV29.
    if (frameProcessorChain.hasPendingFrames()) {
      return false;
    }
    return maybeProcessDecoderOutput();
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
    if (decoder.getOutputBufferInfo() == null) {
      return false;
    }

    frameProcessorChain.registerInputFrame();
    decoder.releaseOutputBuffer(/* render= */ true);
    return true;
  }
}
