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
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Pipeline to decode video samples, apply transformations on the raw samples, and re-encode them.
 */
/* package */ final class VideoSamplePipeline implements SamplePipeline {

  private static final String TAG = "VideoSamplePipeline";

  private final DecoderInputBuffer decoderInputBuffer;
  private final MediaCodecAdapterWrapper decoder;

  private final MediaCodecAdapterWrapper encoder;
  private final DecoderInputBuffer encoderOutputBuffer;

  private @MonotonicNonNull FrameEditor frameEditor;

  private boolean waitingForFrameEditorInput;

  public VideoSamplePipeline(
      Context context,
      Format inputFormat,
      Transformation transformation,
      int rendererIndex,
      Transformer.DebugViewProvider debugViewProvider)
      throws ExoPlaybackException {
    decoderInputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    encoderOutputBuffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);

    int outputWidth = inputFormat.width;
    int outputHeight = inputFormat.height;
    if (transformation.outputHeight != Format.NO_VALUE
        && transformation.outputHeight != inputFormat.height) {
      outputWidth = inputFormat.width * transformation.outputHeight / inputFormat.height;
      outputHeight = transformation.outputHeight;
    }

    try {
      encoder =
          MediaCodecAdapterWrapper.createForVideoEncoding(
              new Format.Builder()
                  .setWidth(outputWidth)
                  .setHeight(outputHeight)
                  .setSampleMimeType(
                      transformation.videoMimeType != null
                          ? transformation.videoMimeType
                          : inputFormat.sampleMimeType)
                  .build(),
              ImmutableMap.of());
    } catch (IOException e) {
      // TODO(internal b/192864511): Assign a specific error code.
      throw createRendererException(
          e, rendererIndex, inputFormat, PlaybackException.ERROR_CODE_UNSPECIFIED);
    }
    if (inputFormat.height != outputHeight || !transformation.transformationMatrix.isIdentity()) {
      frameEditor =
          FrameEditor.create(
              context,
              outputWidth,
              outputHeight,
              transformation.transformationMatrix,
              /* outputSurface= */ checkNotNull(encoder.getInputSurface()),
              debugViewProvider);
    }
    try {
      decoder =
          MediaCodecAdapterWrapper.createForVideoDecoding(
              inputFormat,
              frameEditor == null
                  ? checkNotNull(encoder.getInputSurface())
                  : frameEditor.getInputSurface());
    } catch (IOException e) {
      throw createRendererException(
          e, rendererIndex, inputFormat, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    }
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
  public boolean processData() {
    if (decoder.isEnded()) {
      return false;
    }

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
    return encoder.getOutputFormat();
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

  private static ExoPlaybackException createRendererException(
      Throwable cause, int rendererIndex, Format inputFormat, int errorCode) {
    return ExoPlaybackException.createForRenderer(
        cause,
        TAG,
        rendererIndex,
        inputFormat,
        /* rendererFormatSupport= */ C.FORMAT_HANDLED,
        /* isRecoverable= */ false,
        errorCode);
  }
}
