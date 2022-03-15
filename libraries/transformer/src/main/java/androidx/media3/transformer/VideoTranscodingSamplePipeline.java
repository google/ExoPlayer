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
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Matrix;
import android.media.MediaCodec;
import android.media.MediaFormat;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.DecoderInputBuffer;
import java.util.List;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Pipeline to decode video samples, apply transformations on the raw samples, and re-encode them.
 */
/* package */ final class VideoTranscodingSamplePipeline implements SamplePipeline {

  private final int outputRotationDegrees;
  private final DecoderInputBuffer decoderInputBuffer;
  private final Codec decoder;

  @Nullable private final FrameEditor frameEditor;

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
    float decodedAspectRatio = (float) decodedWidth / decodedHeight;

    Matrix transformationMatrix = new Matrix(transformationRequest.transformationMatrix);

    int outputWidth = decodedWidth;
    int outputHeight = decodedHeight;
    if (!transformationMatrix.isIdentity()) {
      // Scale frames by decodedAspectRatio, to account for FrameEditor's normalized device
      // coordinates (NDC) (a square from -1 to 1 for both x and y) and preserve rectangular display
      // of input pixels during transformations (ex. rotations). With scaling, transformationMatrix
      // operations operate on a rectangle for x from -decodedAspectRatio to decodedAspectRatio, and
      // y from -1 to 1.
      transformationMatrix.preScale(/* sx= */ decodedAspectRatio, /* sy= */ 1f);
      transformationMatrix.postScale(/* sx= */ 1f / decodedAspectRatio, /* sy= */ 1f);

      float[][] transformOnNdcPoints = {{-1, -1, 0, 1}, {-1, 1, 0, 1}, {1, -1, 0, 1}, {1, 1, 0, 1}};
      float xMin = Float.MAX_VALUE;
      float xMax = Float.MIN_VALUE;
      float yMin = Float.MAX_VALUE;
      float yMax = Float.MIN_VALUE;
      for (float[] transformOnNdcPoint : transformOnNdcPoints) {
        transformationMatrix.mapPoints(transformOnNdcPoint);
        xMin = min(xMin, transformOnNdcPoint[0]);
        xMax = max(xMax, transformOnNdcPoint[0]);
        yMin = min(yMin, transformOnNdcPoint[1]);
        yMax = max(yMax, transformOnNdcPoint[1]);
      }

      float xCenter = (xMax + xMin) / 2f;
      float yCenter = (yMax + yMin) / 2f;
      transformationMatrix.postTranslate(-xCenter, -yCenter);

      float ndcWidthAndHeight = 2f; // Length from -1 to 1.
      float xScale = (xMax - xMin) / ndcWidthAndHeight;
      float yScale = (yMax - yMin) / ndcWidthAndHeight;
      transformationMatrix.postScale(1f / xScale, 1f / yScale);
      outputWidth = Math.round(decodedWidth * xScale);
      outputHeight = Math.round(decodedHeight * yScale);
    }
    // Scale width and height to desired transformationRequest.outputHeight, preserving
    // aspect ratio.
    if (transformationRequest.outputHeight != C.LENGTH_UNSET
        && transformationRequest.outputHeight != outputHeight) {
      outputWidth =
          Math.round((float) transformationRequest.outputHeight * outputWidth / outputHeight);
      outputHeight = transformationRequest.outputHeight;
    }

    // Encoders commonly support higher maximum widths than maximum heights. Rotate the decoded
    // video before encoding, so the encoded video's width >= height, and set outputRotationDegrees
    // to ensure the video is displayed in the correct orientation.
    int requestedEncoderWidth;
    int requestedEncoderHeight;
    boolean swapEncodingDimensions = outputHeight > outputWidth;
    if (swapEncodingDimensions) {
      outputRotationDegrees = 90;
      requestedEncoderWidth = outputHeight;
      requestedEncoderHeight = outputWidth;
      // TODO(b/201293185): After fragment shader transformations are implemented, put
      // postRotate in a later vertex shader.
      transformationMatrix.postRotate(outputRotationDegrees);
    } else {
      outputRotationDegrees = 0;
      requestedEncoderWidth = outputWidth;
      requestedEncoderHeight = outputHeight;
    }

    Format requestedEncoderFormat =
        new Format.Builder()
            .setWidth(requestedEncoderWidth)
            .setHeight(requestedEncoderHeight)
            .setRotationDegrees(0)
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
            /* hasOutputFormatRotation= */ swapEncodingDimensions,
            requestedEncoderFormat,
            encoderSupportedFormat));

    if (transformationRequest.enableHdrEditing
        || inputFormat.height != encoderSupportedFormat.height
        || inputFormat.width != encoderSupportedFormat.width
        || !transformationMatrix.isIdentity()) {
      frameEditor =
          FrameEditor.create(
              context,
              encoderSupportedFormat.width,
              encoderSupportedFormat.height,
              inputFormat.pixelWidthHeightRatio,
              new TransformationFrameProcessor(context, transformationMatrix),
              /* outputSurface= */ encoder.getInputSurface(),
              transformationRequest.enableHdrEditing,
              debugViewProvider);
    } else {
      frameEditor = null;
    }

    decoder =
        decoderFactory.createForVideoDecoding(
            inputFormat,
            frameEditor == null ? encoder.getInputSurface() : frameEditor.createInputSurface());
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
    if (frameEditor != null) {
      frameEditor.getAndRethrowBackgroundExceptions();
      if (frameEditor.isEnded()) {
        if (!signaledEndOfStreamToEncoder) {
          encoder.signalEndOfInputStream();
          signaledEndOfStreamToEncoder = true;
        }
        return false;
      }
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
      if (frameEditor != null) {
        frameEditor.signalEndOfInputStream();
      } else {
        encoder.signalEndOfInputStream();
        signaledEndOfStreamToEncoder = true;
        return false;
      }
    }
    return canProcessMoreDataImmediately;
  }

  /**
   * Processes input data from API 29.
   *
   * <p>In this method the decoder could decode multiple frames in one invocation; as compared to
   * {@link #processDataDefault()}, in which one frame is decoded in each invocation. Consequently,
   * if {@link FrameEditor} processes frames slower than the decoder, decoded frames are queued up
   * in the decoder's output surface.
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
   * <p>Only renders decoder output to the {@link FrameEditor}'s input surface if the {@link
   * FrameEditor} has finished processing the previous frame.
   */
  private boolean processDataDefault() throws TransformationException {
    // TODO(b/214975934): Check whether this can be converted to a while-loop like processDataV29.
    if (frameEditor != null && frameEditor.hasPendingFrames()) {
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
    if (frameEditor != null) {
      frameEditor.release();
    }
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

    if (frameEditor != null) {
      frameEditor.registerInputFrame();
    }
    decoder.releaseOutputBuffer(/* render= */ true);
    return true;
  }
}
