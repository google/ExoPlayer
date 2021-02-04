/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.google.android.exoplayer2.mediacodec;

import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;

/**
 * A {@link MediaCodecAdapter} that operates the underlying {@link MediaCodec} in synchronous mode.
 */
public final class SynchronousMediaCodecAdapter implements MediaCodecAdapter {

  /** A factory for {@link SynchronousMediaCodecAdapter} instances. */
  public static final class Factory implements MediaCodecAdapter.Factory {
    @Override
    public MediaCodecAdapter createAdapter(MediaCodec codec) {
      return new SynchronousMediaCodecAdapter(codec);
    }
  }

  private final MediaCodec codec;
  @Nullable private ByteBuffer[] inputByteBuffers;
  @Nullable private ByteBuffer[] outputByteBuffers;

  private SynchronousMediaCodecAdapter(MediaCodec mediaCodec) {
    this.codec = mediaCodec;
  }

  @Override
  public void configure(
      @Nullable MediaFormat mediaFormat,
      @Nullable Surface surface,
      @Nullable MediaCrypto crypto,
      int flags) {
    codec.configure(mediaFormat, surface, crypto, flags);
  }

  @Override
  public void start() {
    codec.start();
    if (Util.SDK_INT < 21) {
      inputByteBuffers = codec.getInputBuffers();
      outputByteBuffers = codec.getOutputBuffers();
    }
  }

  @Override
  public int dequeueInputBufferIndex() {
    return codec.dequeueInputBuffer(0);
  }

  @Override
  public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    int index;
    do {
      index = codec.dequeueOutputBuffer(bufferInfo, 0);
      if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED && Util.SDK_INT < 21) {
        outputByteBuffers = codec.getOutputBuffers();
      }
    } while (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED);

    return index;
  }

  @Override
  public MediaFormat getOutputFormat() {
    return codec.getOutputFormat();
  }

  @Override
  @Nullable
  public ByteBuffer getInputBuffer(int index) {
    if (Util.SDK_INT >= 21) {
      return codec.getInputBuffer(index);
    } else {
      return castNonNull(inputByteBuffers)[index];
    }
  }

  @Override
  @Nullable
  public ByteBuffer getOutputBuffer(int index) {
    if (Util.SDK_INT >= 21) {
      return codec.getOutputBuffer(index);
    } else {
      return castNonNull(outputByteBuffers)[index];
    }
  }

  @Override
  public void queueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flags) {
    codec.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
  }

  @Override
  public void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
    codec.queueSecureInputBuffer(
        index, offset, info.getFrameworkCryptoInfo(), presentationTimeUs, flags);
  }

  @Override
  public void releaseOutputBuffer(int index, boolean render) {
    codec.releaseOutputBuffer(index, render);
  }

  @Override
  @RequiresApi(21)
  public void releaseOutputBuffer(int index, long renderTimeStampNs) {
    codec.releaseOutputBuffer(index, renderTimeStampNs);
  }

  @Override
  public void flush() {
    codec.flush();
  }

  @Override
  public void release() {
    inputByteBuffers = null;
    outputByteBuffers = null;
    codec.release();
  }

  @Override
  @RequiresApi(23)
  public void setOnFrameRenderedListener(OnFrameRenderedListener listener, Handler handler) {
    codec.setOnFrameRenderedListener(
        (codec, presentationTimeUs, nanoTime) ->
            listener.onFrameRendered(
                SynchronousMediaCodecAdapter.this, presentationTimeUs, nanoTime),
        handler);
  }

  @Override
  @RequiresApi(23)
  public void setOutputSurface(Surface surface) {
    codec.setOutputSurface(surface);
  }

  @Override
  @RequiresApi(19)
  public void setParameters(Bundle params) {
    codec.setParameters(params);
  }

  @Override
  public void setVideoScalingMode(@C.VideoScalingMode int scalingMode) {
    codec.setVideoScalingMode(scalingMode);
  }
}
