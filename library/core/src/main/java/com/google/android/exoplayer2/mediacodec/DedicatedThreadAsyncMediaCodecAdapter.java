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
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link MediaCodecAdapter} that operates the underlying {@link MediaCodec} in asynchronous mode
 * and routes {@link MediaCodec.Callback} callbacks on a dedicated thread that is managed
 * internally.
 *
 * <p>This adapter supports queueing input buffers asynchronously.
 */
@RequiresApi(23)
/* package */ final class DedicatedThreadAsyncMediaCodecAdapter extends MediaCodec.Callback
    implements MediaCodecAdapter {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_CREATED, STATE_STARTED, STATE_SHUT_DOWN})
  private @interface State {}

  private static final int STATE_CREATED = 0;
  private static final int STATE_STARTED = 1;
  private static final int STATE_SHUT_DOWN = 2;

  private final MediaCodecAsyncCallback mediaCodecAsyncCallback;
  private final MediaCodec codec;
  private final HandlerThread handlerThread;
  private @MonotonicNonNull Handler handler;
  private long pendingFlushCount;
  private @State int state;
  private Runnable codecStartRunnable;
  private final MediaCodecInputBufferEnqueuer bufferEnqueuer;
  @Nullable private IllegalStateException internalException;

  /**
   * Creates an instance that wraps the specified {@link MediaCodec}. Instances created with this
   * constructor will queue input buffers to the {@link MediaCodec} synchronously.
   *
   * @param codec The {@link MediaCodec} to wrap.
   * @param trackType One of {@link C#TRACK_TYPE_AUDIO} or {@link C#TRACK_TYPE_VIDEO}. Used for
   *     labelling the internal thread accordingly.
   */
  /* package */ DedicatedThreadAsyncMediaCodecAdapter(MediaCodec codec, int trackType) {
    this(
        codec,
        /* enableAsynchronousQueueing= */ false,
        trackType,
        new HandlerThread(createThreadLabel(trackType)));
  }

  /**
   * Creates an instance that wraps the specified {@link MediaCodec}.
   *
   * @param codec The {@link MediaCodec} to wrap.
   * @param enableAsynchronousQueueing Whether input buffers will be queued asynchronously.
   * @param trackType One of {@link C#TRACK_TYPE_AUDIO} or {@link C#TRACK_TYPE_VIDEO}. Used for
   *     labelling the internal thread accordingly.
   */
  /* package */ DedicatedThreadAsyncMediaCodecAdapter(
      MediaCodec codec, boolean enableAsynchronousQueueing, int trackType) {
    this(
        codec,
        enableAsynchronousQueueing,
        trackType,
        new HandlerThread(createThreadLabel(trackType)));
  }

  @VisibleForTesting
  /* package */ DedicatedThreadAsyncMediaCodecAdapter(
      MediaCodec codec,
      boolean enableAsynchronousQueueing,
      int trackType,
      HandlerThread handlerThread) {
    mediaCodecAsyncCallback = new MediaCodecAsyncCallback();
    this.codec = codec;
    this.handlerThread = handlerThread;
    state = STATE_CREATED;
    codecStartRunnable = codec::start;
    if (enableAsynchronousQueueing) {
      bufferEnqueuer = new AsynchronousMediaCodecBufferEnqueuer(codec, trackType);
    } else {
      bufferEnqueuer = new SynchronousMediaCodecBufferEnqueuer(this.codec);
    }
  }

  @Override
  public synchronized void start() {
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
    codec.setCallback(this, handler);
    bufferEnqueuer.start();
    codecStartRunnable.run();
    state = STATE_STARTED;
  }

  @Override
  public void queueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flags) {
    // This method does not need to be synchronized because it does not interact with the
    // mediaCodecAsyncCallback.
    bufferEnqueuer.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
  }

  @Override
  public void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
    // This method does not need to be synchronized because it does not interact with the
    // mediaCodecAsyncCallback.
    bufferEnqueuer.queueSecureInputBuffer(index, offset, info, presentationTimeUs, flags);
  }

  @Override
  public synchronized int dequeueInputBufferIndex() {
    if (isFlushing()) {
      return MediaCodec.INFO_TRY_AGAIN_LATER;
    } else {
      maybeThrowException();
      return mediaCodecAsyncCallback.dequeueInputBufferIndex();
    }
  }

  @Override
  public synchronized int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    if (isFlushing()) {
      return MediaCodec.INFO_TRY_AGAIN_LATER;
    } else {
      maybeThrowException();
      return mediaCodecAsyncCallback.dequeueOutputBufferIndex(bufferInfo);
    }
  }

  @Override
  public synchronized MediaFormat getOutputFormat() {
    return mediaCodecAsyncCallback.getOutputFormat();
  }

  @Override
  public synchronized void flush() {
    bufferEnqueuer.flush();
    codec.flush();
    ++pendingFlushCount;
    Util.castNonNull(handler).post(this::onFlushCompleted);
  }

  @Override
  public synchronized void shutdown() {
    if (state == STATE_STARTED) {
      bufferEnqueuer.shutdown();
      handlerThread.quit();
      mediaCodecAsyncCallback.flush();
    }
    state = STATE_SHUT_DOWN;
  }

  @Override
  public synchronized void onInputBufferAvailable(MediaCodec codec, int index) {
    mediaCodecAsyncCallback.onInputBufferAvailable(codec, index);
  }

  @Override
  public synchronized void onOutputBufferAvailable(
      MediaCodec codec, int index, MediaCodec.BufferInfo info) {
    mediaCodecAsyncCallback.onOutputBufferAvailable(codec, index, info);
  }

  @Override
  public synchronized void onError(MediaCodec codec, MediaCodec.CodecException e) {
    mediaCodecAsyncCallback.onError(codec, e);
  }

  @Override
  public synchronized void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
    mediaCodecAsyncCallback.onOutputFormatChanged(codec, format);
  }

  @VisibleForTesting
  /* package */ void onMediaCodecError(IllegalStateException e) {
    mediaCodecAsyncCallback.onMediaCodecError(e);
  }

  @VisibleForTesting
  /* package */ void setCodecStartRunnable(Runnable codecStartRunnable) {
    this.codecStartRunnable = codecStartRunnable;
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
      codecStartRunnable.run();
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
    StringBuilder labelBuilder = new StringBuilder("ExoPlayer:MediaCodecAsyncAdapter:");
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
