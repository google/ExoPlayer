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
import static com.google.android.exoplayer2.util.Util.SDK_INT;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaFormat;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Pipeline to decode video samples, apply transformations on the raw samples, and re-encode them.
 */
/* package */ final class VideoSamplePipeline implements SamplePipeline {

  private static final String TAG = "VideoSamplePipeline";

  private final int outputRotationDegrees;
  private final DecoderInputBuffer decoderInputBuffer;
  private final Codec decoder;

  private final Codec encoder;
  private final DecoderInputBuffer encoderOutputBuffer;

  private @MonotonicNonNull FrameEditor frameEditor;

  private boolean waitingForFrameEditorInput;

  public VideoSamplePipeline(
      Context context,
      Format inputFormat,
      TransformationRequest transformationRequest,
      Codec.EncoderFactory encoderFactory,
      Codec.DecoderFactory decoderFactory,
      Transformer.DebugViewProvider debugViewProvider)
      throws TransformationException {
    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderOutputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);

    // Scale width and height to desired transformationRequest.outputHeight, preserving aspect
    // ratio.
    // TODO(internal b/209781577): Think about which edge length should be set for portrait videos.
    float inputAspectRatio = (float) inputFormat.width / inputFormat.height;
    int outputWidth = inputFormat.width;
    int outputHeight = inputFormat.height;
    if (transformationRequest.outputHeight != C.LENGTH_UNSET
        && transformationRequest.outputHeight != inputFormat.height) {
      outputWidth = Math.round(inputAspectRatio * transformationRequest.outputHeight);
      outputHeight = transformationRequest.outputHeight;
    }

    if (inputFormat.height > inputFormat.width) {
      // The encoder may not support encoding in portrait orientation, so the decoded video is
      // rotated to landscape orientation and a rotation is added back later to the output format.
      outputRotationDegrees = (inputFormat.rotationDegrees + 90) % 360;
      int temp = outputWidth;
      outputWidth = outputHeight;
      outputHeight = temp;
    } else {
      outputRotationDegrees = inputFormat.rotationDegrees;
    }
    if ((inputFormat.rotationDegrees % 180) != 0) {
      inputAspectRatio = 1.0f / inputAspectRatio;
    }

    // Scale frames by input aspect ratio, to account for FrameEditor normalized device coordinates
    // (-1 to 1) and preserve frame relative dimensions during transformations (ex. rotations).
    transformationRequest.transformationMatrix.preScale(inputAspectRatio, 1);
    transformationRequest.transformationMatrix.postScale(1.0f / inputAspectRatio, 1);

    // The decoder rotates videos to their intended display orientation. The frameEditor rotates
    // them back for improved encoder compatibility.
    // TODO(b/201293185): After fragment shader transformations are implemented, put
    // postRotate in a later vertex shader.
    transformationRequest.transformationMatrix.postRotate(outputRotationDegrees);

    encoder =
        encoderFactory.createForVideoEncoding(
            new Format.Builder()
                .setWidth(outputWidth)
                .setHeight(outputHeight)
                .setRotationDegrees(0)
                .setSampleMimeType(
                    transformationRequest.videoMimeType != null
                        ? transformationRequest.videoMimeType
                        : inputFormat.sampleMimeType)
                .build());
    if (inputFormat.height != outputHeight
        || inputFormat.width != outputWidth
        || !transformationRequest.transformationMatrix.isIdentity()) {
      frameEditor =
          FrameEditor.create(
              context,
              outputWidth,
              outputHeight,
              inputFormat.pixelWidthHeightRatio,
              transformationRequest.transformationMatrix,
              /* outputSurface= */ checkNotNull(encoder.getInputSurface()),
              debugViewProvider);
    }
    decoder =
        decoderFactory.createForVideoDecoding(
            inputFormat,
            frameEditor == null
                ? checkNotNull(encoder.getInputSurface())
                : frameEditor.getInputSurface());
  }

  @Override
  @Nullable
  public DecoderInputBuffer dequeueInputBuffer() {
    return decoder.maybeDequeueInputBuffer(decoderInputBuffer) ? decoderInputBuffer : null;
  }

  @Override
  public void queueInputBuffer() {
    decoder.queueInputBuffer(decoderInputBuffer);
  }

  @Override
  public boolean processData() throws TransformationException {
    if (decoder.isEnded()) {
      return false;
    }

    if (SDK_INT >= 29) {
      return processDataV29();
    } else {
      return processDataDefault();
    }
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
  private boolean processDataV29() throws TransformationException {
    if (frameEditor != null) {
      while (frameEditor.hasInputData()) {
        // Processes as much frames in one invocation: FrameEditor's output surface will block
        // FrameEditor when it's full. There will be no frame drop, or FrameEditor's output surface
        // growing out of bound.
        frameEditor.processData();
      }
    }

    while (decoder.getOutputBufferInfo() != null) {
      decoder.releaseOutputBuffer(/* render= */ true);
    }

    if (decoder.isEnded()) {
      // TODO(b/208986865): Handle possible last frame drop.
      encoder.signalEndOfInputStream();
      return false;
    }

    return frameEditor != null && frameEditor.hasInputData();
  }

  /** Processes input data. */
  private boolean processDataDefault() throws TransformationException {
    if (frameEditor != null) {
      if (frameEditor.hasInputData()) {
        waitingForFrameEditorInput = false;
        frameEditor.processData();
        return true;
      }
      if (waitingForFrameEditorInput) {
        return false;
      }
    }

    boolean decoderHasOutputBuffer = decoder.getOutputBufferInfo() != null;
    if (decoderHasOutputBuffer) {
      decoder.releaseOutputBuffer(/* render= */ true);
      waitingForFrameEditorInput = frameEditor != null;
    }
    if (decoder.isEnded()) {
      encoder.signalEndOfInputStream();
      return false;
    }
    return decoderHasOutputBuffer && !waitingForFrameEditorInput;
  }

  @Override
  @Nullable
  public Format getOutputFormat() {
    Format format = encoder.getOutputFormat();
    return format == null
        ? null
        : format.buildUpon().setRotationDegrees(outputRotationDegrees).build();
  }

  @Override
  @Nullable
  public DecoderInputBuffer getOutputBuffer() {
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
  public void releaseOutputBuffer() {
    encoder.releaseOutputBuffer();
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
}
