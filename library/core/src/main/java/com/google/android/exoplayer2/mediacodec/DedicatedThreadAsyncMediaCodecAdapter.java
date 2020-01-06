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
import android.os.HandlerThread;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link MediaCodecAdapter} that operates the underlying {@link MediaCodec} in asynchronous mode
 * and routes {@link MediaCodec.Callback} callbacks on a dedicated Thread that is managed
 * internally.
 *
 * <p>After creating an instance, you need to call {@link #start()} to start the internal Thread.
 */
@RequiresApi(23)
/* package */ final class DedicatedThreadAsyncMediaCodecAdapter extends MediaCodec.Callback
    implements MediaCodecAdapter {

  @IntDef({STATE_CREATED, STATE_STARTED, STATE_SHUT_DOWN})
  private @interface State {}

  private static final int STATE_CREATED = 0;
  private static final int STATE_STARTED = 1;
  private static final int STATE_SHUT_DOWN = 2;

  private final MediaCodecAsyncCallback mediaCodecAsyncCallback;
  private final MediaCodec codec;
  private final HandlerThread handlerThread;
  @MonotonicNonNull private Handler handler;
  private long pendingFlushCount;
  private @State int state;
  private Runnable onCodecStart;
  @Nullable private IllegalStateException internalException;

  /**
   * Creates an instance that wraps the specified {@link MediaCodec}.
   *
   * @param codec The {@link MediaCodec} to wrap.
   * @param trackType One of {@link C#TRACK_TYPE_AUDIO} or {@link C#TRACK_TYPE_VIDEO}. Used for
   *     labelling the internal Thread accordingly.
   * @throws IllegalArgumentException If {@code trackType} is not one of {@link C#TRACK_TYPE_AUDIO}
   *     or {@link C#TRACK_TYPE_VIDEO}.
   */
  /* package */ DedicatedThreadAsyncMediaCodecAdapter(MediaCodec codec, int trackType) {
    this(codec, new HandlerThread(createThreadLabel(trackType)));
  }

  @VisibleForTesting
  /* package */ DedicatedThreadAsyncMediaCodecAdapter(
      MediaCodec codec, HandlerThread handlerThread) {
    mediaCodecAsyncCallback = new MediaCodecAsyncCallback();
    this.codec = codec;
    this.handlerThread = handlerThread;
    state = STATE_CREATED;
    onCodecStart = codec::start;
  }

  /**
   * Starts the operation of the instance.
   *
   * <p>After a call to this method, make sure to call {@link #shutdown()} to terminate the internal
   * Thread. You can only call this method once during the lifetime of this instance; calling this
   * method again will throw an {@link IllegalStateException}.
   *
   * @throws IllegalStateException If this method has been called already.
   */
  public synchronized void start() {
    Assertions.checkState(state == STATE_CREATED);

    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
    codec.setCallback(this, handler);
    state = STATE_STARTED;
  }

  @Override
  public synchronized int dequeueInputBufferIndex() {
    Assertions.checkState(state == STATE_STARTED);

    if (isFlushing()) {
      return MediaCodec.INFO_TRY_AGAIN_LATER;
    } else {
      maybeThrowException();
      return mediaCodecAsyncCallback.dequeueInputBufferIndex();
    }
  }

  @Override
  public synchronized int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    Assertions.checkState(state == STATE_STARTED);

    if (isFlushing()) {
      return MediaCodec.INFO_TRY_AGAIN_LATER;
    } else {
      maybeThrowException();
      return mediaCodecAsyncCallback.dequeueOutputBufferIndex(bufferInfo);
    }
  }

  @Override
  public synchronized MediaFormat getOutputFormat() {
    Assertions.checkState(state == STATE_STARTED);

    return mediaCodecAsyncCallback.getOutputFormat();
  }

  @Override
  public synchronized void flush() {
    Assertions.checkState(state == STATE_STARTED);

    codec.flush();
    ++pendingFlushCount;
    Util.castNonNull(handler).post(this::onFlushCompleted);
  }

  @Override
  public synchronized void shutdown() {
    if (state == STATE_STARTED) {
      handlerThread.quit();
      mediaCodecAsyncCallback.flush();
    }

    state = STATE_SHUT_DOWN;
  }

  @Override
  public synchronized void onInputBufferAvailable(@NonNull MediaCodec codec, int index) {
    mediaCodecAsyncCallback.onInputBufferAvailable(codec, index);
  }

  @Override
  public synchronized void onOutputBufferAvailable(
      @NonNull MediaCodec codec, int index, @NonNull MediaCodec.BufferInfo info) {
    mediaCodecAsyncCallback.onOutputBufferAvailable(codec, index, info);
  }

  @Override
  public synchronized void onError(
      @NonNull MediaCodec codec, @NonNull MediaCodec.CodecException e) {
    mediaCodecAsyncCallback.onError(codec, e);
  }

  @Override
  public synchronized void onOutputFormatChanged(
      @NonNull MediaCodec codec, @NonNull MediaFormat format) {
    mediaCodecAsyncCallback.onOutputFormatChanged(codec, format);
  }

  @VisibleForTesting
  /* package */ void onMediaCodecError(IllegalStateException e) {
    mediaCodecAsyncCallback.onMediaCodecError(e);
  }

  @VisibleForTesting
  /* package */ void setOnCodecStart(Runnable onCodecStart) {
    this.onCodecStart = onCodecStart;
  }

  private synchronized void onFlushCompleted() {
    if (state != STATE_STARTED) {
      // The adapter has been shutdown.
      return;
    }

    --pendingFlushCount;
    if (pendingFlushCount > 0) {
      // Another flush() has been called.
      return;
    } else if (pendingFlushCount < 0) {
      // This should never happen.
      internalException = new IllegalStateException();
      return;
    }

    mediaCodecAsyncCallback.flush();
    try {
      onCodecStart.run();
    } catch (IllegalStateException e) {
      internalException = e;
    } catch (Exception e) {
      internalException = new IllegalStateException(e);
    }
  }

  private synchronized boolean isFlushing() {
    return pendingFlushCount > 0;
  }

  private synchronized void maybeThrowException() {
    maybeThrowInternalException();
    mediaCodecAsyncCallback.maybeThrowMediaCodecException();
  }

  private synchronized void maybeThrowInternalException() {
    if (internalException != null) {
      IllegalStateException e = internalException;
      internalException = null;
      throw e;
    }
  }

  private static String createThreadLabel(int trackType) {
    StringBuilder labelBuilder = new StringBuilder("MediaCodecAsyncAdapter:");
    if (trackType == C.TRACK_TYPE_AUDIO) {
      labelBuilder.append("Audio");
    } else if (trackType == C.TRACK_TYPE_VIDEO) {
      labelBuilder.append("Video");
    } else {
      labelBuilder.append("Unknown(").append(trackType).append(")");
    }
    return labelBuilder.toString();
  }
}
