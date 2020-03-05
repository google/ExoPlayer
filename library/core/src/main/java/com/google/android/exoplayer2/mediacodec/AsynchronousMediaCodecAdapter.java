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

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.util.Assertions;

/**
 * A {@link MediaCodecAdapter} that operates the {@link MediaCodec} in asynchronous mode.
 *
 * <p>The AsynchronousMediaCodecAdapter routes callbacks to the current thread's {@link Looper}
 * obtained via {@link Looper#myLooper()}
 */
@RequiresApi(21)
/* package */ final class AsynchronousMediaCodecAdapter implements MediaCodecAdapter {
  private final MediaCodecAsyncCallback mediaCodecAsyncCallback;
  private final Handler handler;
  private final MediaCodec codec;
  @Nullable private IllegalStateException internalException;
  private boolean flushing;
  private Runnable codecStartRunnable;

  /**
   * Create a new {@code AsynchronousMediaCodecAdapter}.
   *
   * @param codec The {@link MediaCodec} to wrap.
   */
  public AsynchronousMediaCodecAdapter(MediaCodec codec) {
    this(codec, Assertions.checkNotNull(Looper.myLooper()));
  }

  @VisibleForTesting
  /* package */ AsynchronousMediaCodecAdapter(MediaCodec codec, Looper looper) {
    mediaCodecAsyncCallback = new MediaCodecAsyncCallback();
    handler = new Handler(looper);
    this.codec = codec;
    this.codec.setCallback(mediaCodecAsyncCallback);
    codecStartRunnable = codec::start;
  }

  @Override
  public void start() {
    codecStartRunnable.run();
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
  public int dequeueInputBufferIndex() {
    if (flushing) {
      return MediaCodec.INFO_TRY_AGAIN_LATER;
    } else {
      maybeThrowException();
      return mediaCodecAsyncCallback.dequeueInputBufferIndex();
    }
  }

  @Override
  public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    if (flushing) {
      return MediaCodec.INFO_TRY_AGAIN_LATER;
    } else {
      maybeThrowException();
      return mediaCodecAsyncCallback.dequeueOutputBufferIndex(bufferInfo);
    }
  }

  @Override
  public MediaFormat getOutputFormat() {
    return mediaCodecAsyncCallback.getOutputFormat();
  }

  @Override
  public void flush() {
    clearPendingFlushState();
    flushing = true;
    codec.flush();
    handler.post(this::onCompleteFlush);
  }

  @Override
  public void shutdown() {
    clearPendingFlushState();
  }

  @VisibleForTesting
  /* package */ MediaCodec.Callback getMediaCodecCallback() {
    return mediaCodecAsyncCallback;
  }

  private void onCompleteFlush() {
    flushing = false;
    mediaCodecAsyncCallback.flush();
    try {
      codecStartRunnable.run();
    } catch (IllegalStateException e) {
      // Catch IllegalStateException directly so that we don't have to wrap it.
      internalException = e;
    } catch (Exception e) {
      internalException = new IllegalStateException(e);
    }
  }

  @VisibleForTesting
  /* package */ void setCodecStartRunnable(Runnable codecStartRunnable) {
    this.codecStartRunnable = codecStartRunnable;
  }

  private void maybeThrowException() throws IllegalStateException {
    maybeThrowInternalException();
    mediaCodecAsyncCallback.maybeThrowMediaCodecException();
  }

  private void maybeThrowInternalException() {
    if (internalException != null) {
      IllegalStateException e = internalException;
      internalException = null;
      throw e;
    }
  }

  /** Clear state related to pending flush events. */
  private void clearPendingFlushState() {
    handler.removeCallbacksAndMessages(null);
    internalException = null;
  }
}
