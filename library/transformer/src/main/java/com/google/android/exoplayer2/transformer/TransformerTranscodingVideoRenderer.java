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

import android.media.MediaCodec;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream;
import java.io.IOException;
import java.nio.ByteBuffer;

@RequiresApi(23)
/* package */ final class TransformerTranscodingVideoRenderer extends TransformerBaseRenderer {

  private static final String TAG = "TransformerTranscodingVideoRenderer";

  private final DecoderInputBuffer decoderInputBuffer;
  /** The format the encoder is configured to output, may differ from the actual output format. */
  private final Format encoderConfigurationOutputFormat;

  private final Surface surface;

  @Nullable private MediaCodecAdapterWrapper decoder;
  @Nullable private MediaCodecAdapterWrapper encoder;
  /** Whether encoder's actual output format is obtained. */
  private boolean hasEncoderActualOutputFormat;

  private boolean muxerWrapperTrackEnded;

  public TransformerTranscodingVideoRenderer(
      MuxerWrapper muxerWrapper,
      TransformerMediaClock mediaClock,
      Transformation transformation,
      Format encoderConfigurationOutputFormat) {
    super(C.TRACK_TYPE_VIDEO, muxerWrapper, mediaClock, transformation);

    decoderInputBuffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT);
    surface = MediaCodec.createPersistentInputSurface();
    this.encoderConfigurationOutputFormat = encoderConfigurationOutputFormat;
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (!isRendererStarted || isEnded()) {
      return;
    }

    if (!ensureDecoderConfigured()) {
      return;
    }

    if (ensureEncoderConfigured()) {
      while (feedMuxerFromEncoder()) {}
      while (feedEncoderFromDecoder()) {}
    }
    while (feedDecoderFromInput()) {}
  }

  @Override
  public boolean isEnded() {
    return muxerWrapperTrackEnded;
  }

  @Override
  protected void onReset() {
    decoderInputBuffer.clear();
    decoderInputBuffer.data = null;
    surface.release();
    if (decoder != null) {
      decoder.release();
      decoder = null;
    }
    if (encoder != null) {
      encoder.release();
      encoder = null;
    }
    hasEncoderActualOutputFormat = false;
    muxerWrapperTrackEnded = false;
  }

  private boolean ensureDecoderConfigured() throws ExoPlaybackException {
    if (decoder != null) {
      return true;
    }

    FormatHolder formatHolder = getFormatHolder();
    @SampleStream.ReadDataResult
    int result =
        readSource(
            formatHolder, decoderInputBuffer, /* readFlags= */ SampleStream.FLAG_REQUIRE_FORMAT);
    if (result != C.RESULT_FORMAT_READ) {
      return false;
    }

    Format inputFormat = checkNotNull(formatHolder.format);
    try {
      decoder = MediaCodecAdapterWrapper.createForVideoDecoding(inputFormat, surface);
    } catch (IOException e) {
      throw createRendererException(
          e, formatHolder.format, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    }
    return true;
  }

  private boolean ensureEncoderConfigured() throws ExoPlaybackException {
    if (encoder != null) {
      return true;
    }

    try {
      encoder =
          MediaCodecAdapterWrapper.createForVideoEncoding(
              encoderConfigurationOutputFormat, surface);
    } catch (IOException e) {
      throw createRendererException(
          // TODO(claincly): should be "ENCODER_INIT_FAILED"
          e,
          checkNotNull(this.decoder).getOutputFormat(),
          PlaybackException.ERROR_CODE_DECODER_INIT_FAILED);
    }
    return true;
  }

  private boolean feedDecoderFromInput() {
    MediaCodecAdapterWrapper decoder = checkNotNull(this.decoder);
    if (!decoder.maybeDequeueInputBuffer(decoderInputBuffer)) {
      return false;
    }

    decoderInputBuffer.clear();
    @SampleStream.ReadDataResult
    int result = readSource(getFormatHolder(), decoderInputBuffer, /* readFlags= */ 0);

    switch (result) {
      case C.RESULT_FORMAT_READ:
        throw new IllegalStateException("Format changes are not supported.");
      case C.RESULT_BUFFER_READ:
        mediaClock.updateTimeForTrackType(getTrackType(), decoderInputBuffer.timeUs);
        ByteBuffer data = checkNotNull(decoderInputBuffer.data);
        data.flip();
        decoder.queueInputBuffer(decoderInputBuffer);
        return !decoderInputBuffer.isEndOfStream();
      case C.RESULT_NOTHING_READ:
      default:
        return false;
    }
  }

  private boolean feedEncoderFromDecoder() {
    MediaCodecAdapterWrapper decoder = checkNotNull(this.decoder);
    if (decoder.isEnded()) {
      return false;
    }
    // Rendering the decoder output queues input to the encoder because they share the same surface.
    return decoder.maybeDequeueRenderAndReleaseOutputBuffer();
  }

  private boolean feedMuxerFromEncoder() {
    MediaCodecAdapterWrapper encoder = checkNotNull(this.encoder);
    if (!hasEncoderActualOutputFormat) {
      @Nullable Format encoderOutputFormat = encoder.getOutputFormat();
      if (encoderOutputFormat == null) {
        return false;
      }
      hasEncoderActualOutputFormat = true;
      muxerWrapper.addTrackFormat(encoderOutputFormat);
    }

    // TODO(claincly) May have to use inputStreamBuffer.isEndOfStream result to call
    // decoder.signalEndOfInputStream().
    MediaCodecAdapterWrapper decoder = checkNotNull(this.decoder);
    if (decoder.isEnded()) {
      muxerWrapper.endTrack(getTrackType());
      muxerWrapperTrackEnded = true;
      return false;
    }

    @Nullable ByteBuffer encoderOutputBuffer = encoder.getOutputBuffer();
    if (encoderOutputBuffer == null) {
      return false;
    }

    MediaCodec.BufferInfo encoderOutputBufferInfo = checkNotNull(encoder.getOutputBufferInfo());
    if (!muxerWrapper.writeSample(
        getTrackType(),
        encoderOutputBuffer,
        /* isKeyFrame= */ (encoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) > 0,
        encoderOutputBufferInfo.presentationTimeUs)) {
      return false;
    }
    encoder.releaseOutputBuffer();
    return true;
  }
}
