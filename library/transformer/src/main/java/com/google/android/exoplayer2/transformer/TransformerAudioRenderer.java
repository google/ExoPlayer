/*
 * Copyright 2020 The Android Open Source Project
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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.mp4.SlowMotionData;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;

/* package */ final class TransformerAudioRenderer extends TransformerBaseRenderer {

  private static final String TAG = "TAudioRenderer";

  private final Codec.EncoderFactory encoderFactory;
  private final Codec.DecoderFactory decoderFactory;
  private final DecoderInputBuffer decoderInputBuffer;

  public TransformerAudioRenderer(
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      TransformationRequest transformationRequest,
      Codec.EncoderFactory encoderFactory,
      Codec.DecoderFactory decoderFactory,
      Transformer.AsyncErrorListener asyncErrorListener,
      FallbackListener fallbackListener) {
    super(
        C.TRACK_TYPE_AUDIO,
        muxerWrapper,
        mediaClock,
        transformationRequest,
        asyncErrorListener,
        fallbackListener);
    this.encoderFactory = encoderFactory;
    this.decoderFactory = decoderFactory;
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
          new PassthroughSamplePipeline(
              inputFormat,
              streamOffsetUs,
              streamStartPositionUs,
              transformationRequest,
              muxerWrapper,
              fallbackListener);
    } else {
      samplePipeline =
          new AudioTranscodingSamplePipeline(
              inputFormat,
              streamOffsetUs,
              streamStartPositionUs,
              transformationRequest,
              decoderFactory,
              encoderFactory,
              muxerWrapper,
              fallbackListener);
    }
    return true;
  }

  private boolean shouldPassthrough(Format inputFormat) {
    if (encoderFactory.audioNeedsEncoding()) {
      return false;
    }
    if (transformationRequest.audioMimeType != null
        && !transformationRequest.audioMimeType.equals(inputFormat.sampleMimeType)) {
      return false;
    }
    if (transformationRequest.audioMimeType == null
        && !muxerWrapper.supportsSampleMimeType(inputFormat.sampleMimeType)) {
      return false;
    }
    if (transformationRequest.flattenForSlowMotion && isSlowMotion(inputFormat)) {
      return false;
    }
    return true;
  }

  private static boolean isSlowMotion(Format format) {
    @Nullable Metadata metadata = format.metadata;
    if (metadata == null) {
      return false;
    }
    for (int i = 0; i < metadata.length(); i++) {
      if (metadata.get(i) instanceof SlowMotionData) {
        return true;
      }
    }
    return false;
  }
}
