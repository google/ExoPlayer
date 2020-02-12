/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.util.ConditionVariable;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link MediaCodecInputBufferEnqueuer} that defers queueing operations on a background thread.
 *
 * <p>The implementation of this class assumes that its public methods will be called from the same
 * thread.
 */
@RequiresApi(23)
class AsynchronousMediaCodecBufferEnqueuer implements MediaCodecInputBufferEnqueuer {

  private static final int MSG_QUEUE_INPUT_BUFFER = 0;
  private static final int MSG_QUEUE_SECURE_INPUT_BUFFER = 1;
  private static final int MSG_FLUSH = 2;

  @GuardedBy("MESSAGE_PARAMS_INSTANCE_POOL")
  private static final ArrayDeque<MessageParams> MESSAGE_PARAMS_INSTANCE_POOL = new ArrayDeque<>();

  private final MediaCodec codec;
  private final HandlerThread handlerThread;
  private @MonotonicNonNull Handler handler;
  private final AtomicReference<@NullableType RuntimeException> pendingRuntimeException;
  private final ConditionVariable conditionVariable;
  private boolean started;

  /**
   * Creates a new instance that submits input buffers on the specified {@link MediaCodec}.
   *
   * @param codec The {@link MediaCodec} to submit input buffers to.
   * @param trackType The type of stream (used for debug logs).
   */
  public AsynchronousMediaCodecBufferEnqueuer(MediaCodec codec, int trackType) {
    this(
        codec,
        new HandlerThread(createThreadLabel(trackType)),
        /* conditionVariable= */ new ConditionVariable());
  }

  @VisibleForTesting
  /* package */ AsynchronousMediaCodecBufferEnqueuer(
      MediaCodec codec, HandlerThread handlerThread, ConditionVariable conditionVariable) {
    this.codec = codec;
    this.handlerThread = handlerThread;
    this.conditionVariable = conditionVariable;
    pendingRuntimeException = new AtomicReference<>();
  }

  @Override
  public void start() {
    if (!started) {
      handlerThread.start();
      handler =
          new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
              doHandleMessage(msg);
            }
          };
      started = true;
    }
  }

  @Override
  public void queueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flags) {
    maybeThrowException();
    MessageParams messageParams = getMessageParams();
    messageParams.setQueueParams(index, offset, size, presentationTimeUs, flags);
    Message message =
        Util.castNonNull(handler).obtainMessage(MSG_QUEUE_INPUT_BUFFER, messageParams);
    message.sendToTarget();
  }

  @Override
  public void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
    maybeThrowException();
    MessageParams messageParams = getMessageParams();
    messageParams.setQueueParams(index, offset, /* size= */ 0, presentationTimeUs, flags);
    info.copyTo(messageParams.cryptoInfo);
    Message message =
        Util.castNonNull(handler).obtainMessage(MSG_QUEUE_SECURE_INPUT_BUFFER, messageParams);
    message.sendToTarget();
  }

  @Override
  public void flush() {
    if (started) {
      try {
        flushHandlerThread();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // The playback thread should not be interrupted. Raising this as an
        // IllegalStateException.
        throw new IllegalStateException(e);
      }
    }
  }

  @Override
  public void shutdown() {
    if (started) {
      flush();
      handlerThread.quit();
    }
    started = false;
  }

  private void doHandleMessage(Message msg) {
    MessageParams params = null;
    switch (msg.what) {
      case MSG_QUEUE_INPUT_BUFFER:
        params = (MessageParams) msg.obj;
        doQueueInputBuffer(
            params.index, params.offset, params.size, params.presentationTimeUs, params.flags);
        break;
      case MSG_QUEUE_SECURE_INPUT_BUFFER:
        params = (MessageParams) msg.obj;
        doQueueSecureInputBuffer(
            params.index,
            params.offset,
            params.cryptoInfo,
            params.presentationTimeUs,
            params.flags);
        break;
      case MSG_FLUSH:
        conditionVariable.open();
        break;
      default:
        setPendingRuntimeException(new IllegalStateException(String.valueOf(msg.what)));
    }
    if (params != null) {
      recycleMessageParams(params);
    }
  }

  private void maybeThrowException() {
    RuntimeException exception = pendingRuntimeException.getAndSet(null);
    if (exception != null) {
      throw exception;
    }
  }

  /**
   * Empties all tasks enqueued on the {@link #handlerThread} via the {@link #handler}. This method
   * blocks until the {@link #handlerThread} is idle.
   */
  private void flushHandlerThread() throws InterruptedException {
    Handler handler = Util.castNonNull(this.handler);
    handler.removeCallbacksAndMessages(null);
    conditionVariable.close();
    handler.obtainMessage(MSG_FLUSH).sendToTarget();
    conditionVariable.block();
    // Check if any exceptions happened during the last queueing action.
    maybeThrowException();
  }

  // Called from the handler thread

  @VisibleForTesting
  /* package */ void setPendingRuntimeException(RuntimeException exception) {
    pendingRuntimeException.set(exception);
  }

  private void doQueueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flag) {
    try {
      codec.queueInputBuffer(index, offset, size, presentationTimeUs, flag);
    } catch (RuntimeException e) {
      setPendingRuntimeException(e);
    }
  }

  private void doQueueSecureInputBuffer(
      int index, int offset, MediaCodec.CryptoInfo info, long presentationTimeUs, int flags) {
    try {
      codec.queueSecureInputBuffer(index, offset, info, presentationTimeUs, flags);
    } catch (RuntimeException e) {
      setPendingRuntimeException(e);
    }
  }

  @VisibleForTesting
  /* package */ static int getInstancePoolSize() {
    synchronized (MESSAGE_PARAMS_INSTANCE_POOL) {
      return MESSAGE_PARAMS_INSTANCE_POOL.size();
    }
  }

  private static MessageParams getMessageParams() {
    synchronized (MESSAGE_PARAMS_INSTANCE_POOL) {
      if (MESSAGE_PARAMS_INSTANCE_POOL.isEmpty()) {
        return new MessageParams();
      } else {
        return MESSAGE_PARAMS_INSTANCE_POOL.removeFirst();
      }
    }
  }

  private static void recycleMessageParams(MessageParams params) {
    synchronized (MESSAGE_PARAMS_INSTANCE_POOL) {
      MESSAGE_PARAMS_INSTANCE_POOL.add(params);
    }
  }

  /** Parameters for queue input buffer and queue secure input buffer tasks. */
  private static class MessageParams {
    public int index;
    public int offset;
    public int size;
    public final MediaCodec.CryptoInfo cryptoInfo;
    public long presentationTimeUs;
    public int flags;

    MessageParams() {
      cryptoInfo = new MediaCodec.CryptoInfo();
    }

    /** Convenience method for setting the queueing parameters. */
    public void setQueueParams(
        int index, int offset, int size, long presentationTimeUs, int flags) {
      this.index = index;
      this.offset = offset;
      this.size = size;
      this.presentationTimeUs = presentationTimeUs;
      this.flags = flags;
    }
  }

  private static String createThreadLabel(int trackType) {
    StringBuilder labelBuilder = new StringBuilder("MediaCodecInputBufferEnqueuer:");
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
