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
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import androidx.annotation.GuardedBy;
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
/* package */ final class AsynchronousMediaCodecAdapter extends MediaCodec.Callback
    implements MediaCodecAdapter {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_CREATED, STATE_CONFIGURED, STATE_STARTED, STATE_SHUT_DOWN})
  private @interface State {}

  private static final int STATE_CREATED = 0;
  private static final int STATE_CONFIGURED = 1;
  private static final int STATE_STARTED = 2;
  private static final int STATE_SHUT_DOWN = 3;

  private final Object lock;

  @GuardedBy("lock")
  private final MediaCodecAsyncCallback mediaCodecAsyncCallback;

  private final MediaCodec codec;
  private final HandlerThread handlerThread;
  private @MonotonicNonNull Handler handler;

  @GuardedBy("lock")
  private long pendingFlushCount;

  private @State int state;
  private final MediaCodecInputBufferEnqueuer bufferEnqueuer;

  @GuardedBy("lock")
  @Nullable
  private IllegalStateException internalException;

  /**
   * Creates an instance that wraps the specified {@link MediaCodec}. Instances created with this
   * constructor will queue input buffers to the {@link MediaCodec} synchronously.
   *
   * @param codec The {@link MediaCodec} to wrap.
   * @param trackType One of {@link C#TRACK_TYPE_AUDIO} or {@link C#TRACK_TYPE_VIDEO}. Used for
   *     labelling the internal thread accordingly.
   */
  /* package */ AsynchronousMediaCodecAdapter(MediaCodec codec, int trackType) {
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
  /* package */ AsynchronousMediaCodecAdapter(
      MediaCodec codec, boolean enableAsynchronousQueueing, int trackType) {
    this(
        codec,
        enableAsynchronousQueueing,
        trackType,
        new HandlerThread(createThreadLabel(trackType)));
  }

  @VisibleForTesting
  /* package */ AsynchronousMediaCodecAdapter(
      MediaCodec codec,
      boolean enableAsynchronousQueueing,
      int trackType,
      HandlerThread handlerThread) {
    this.lock = new Object();
    this.mediaCodecAsyncCallback = new MediaCodecAsyncCallback();
    this.codec = codec;
    this.handlerThread = handlerThread;
    this.bufferEnqueuer =
        enableAsynchronousQueueing
            ? new AsynchronousMediaCodecBufferEnqueuer(codec, trackType)
            : new SynchronousMediaCodecBufferEnqueuer(this.codec);
    this.state = STATE_CREATED;
  }

  @Override
  public void configure(
      @Nullable MediaFormat mediaFormat,
      @Nullable Surface surface,
      @Nullable MediaCrypto crypto,
      int flags) {
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
    codec.setCallback(this, handler);
    codec.configure(mediaFormat, surface, crypto, flags);
    state = STATE_CONFIGURED;
  }

  @Override
  public void start() {
    bufferEnqueuer.start();
    codec.start();
    state = STATE_STARTED;
  }

  @Override
  public void queueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flags) {
    bufferEnqueuer.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
  }

  @Override
  public void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
    bufferEnqueuer.queueSecureInputBuffer(index, offset, info, presentationTimeUs, flags);
  }

  @Override
  public int dequeueInputBufferIndex() {
    synchronized (lock) {
      if (isFlushing()) {
        return MediaCodec.INFO_TRY_AGAIN_LATER;
      } else {
        maybeThrowException();
        return mediaCodecAsyncCallback.dequeueInputBufferIndex();
      }
    }
  }

  @Override
  public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    synchronized (lock) {
      if (isFlushing()) {
        return MediaCodec.INFO_TRY_AGAIN_LATER;
      } else {
        maybeThrowException();
        return mediaCodecAsyncCallback.dequeueOutputBufferIndex(bufferInfo);
      }
    }
  }

  @Override
  public MediaFormat getOutputFormat() {
    synchronized (lock) {
      return mediaCodecAsyncCallback.getOutputFormat();
    }
  }

  @Override
  public void flush() {
    synchronized (lock) {
      bufferEnqueuer.flush();
      codec.flush();
      ++pendingFlushCount;
      Util.castNonNull(handler).post(this::onFlushCompleted);
    }
  }

  @Override
  public void shutdown() {
    synchronized (lock) {
      if (state == STATE_STARTED) {
        bufferEnqueuer.shutdown();
      }
      if (state == STATE_CONFIGURED || state == STATE_STARTED) {
        handlerThread.quit();
        mediaCodecAsyncCallback.flush();
        // Leave the adapter in a flushing state so that
        // it will not dequeue anything.
        ++pendingFlushCount;
      }
      state = STATE_SHUT_DOWN;
    }
  }

  @Override
  public MediaCodec getCodec() {
    return codec;
  }

  // Called from the handler thread.

  @Override
  public void onInputBufferAvailable(MediaCodec codec, int index) {
    synchronized (lock) {
      mediaCodecAsyncCallback.onInputBufferAvailable(codec, index);
    }
  }

  @Override
  public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
    synchronized (lock) {
      mediaCodecAsyncCallback.onOutputBufferAvailable(codec, index, info);
    }
  }

  @Override
  public void onError(MediaCodec codec, MediaCodec.CodecException e) {
    synchronized (lock) {
      mediaCodecAsyncCallback.onError(codec, e);
    }
  }

  @Override
  public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
    synchronized (lock) {
      mediaCodecAsyncCallback.onOutputFormatChanged(codec, format);
    }
  }

  private void onFlushCompleted() {
    synchronized (lock) {
      onFlushCompletedSynchronized();
    }
  }

  @GuardedBy("lock")
  private void onFlushCompletedSynchronized() {
    if (state == STATE_SHUT_DOWN) {
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
      codec.start();
    } catch (IllegalStateException e) {
      internalException = e;
    } catch (Exception e) {
      internalException = new IllegalStateException(e);
    }
  }

  @GuardedBy("lock")
  private boolean isFlushing() {
    return pendingFlushCount > 0;
  }

  @GuardedBy("lock")
  private void maybeThrowException() {
    maybeThrowInternalException();
    mediaCodecAsyncCallback.maybeThrowMediaCodecException();
  }

  @GuardedBy("lock")
  private void maybeThrowInternalException() {
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
