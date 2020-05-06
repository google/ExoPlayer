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
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.util.IntArrayQueue;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A {@link MediaCodecAdapter} that operates the underlying {@link MediaCodec} in asynchronous mode
 * and routes {@link MediaCodec.Callback} callbacks on a dedicated thread that is managed
 * internally.
 *
 * <p>The main difference of this class compared to the {@link
 * DedicatedThreadAsyncMediaCodecAdapter} is that its internal implementation applies finer-grained
 * locking. The {@link DedicatedThreadAsyncMediaCodecAdapter} uses a single lock to synchronize
 * access, whereas this class uses a different lock to access the available input and available
 * output buffer indexes returned from the {@link MediaCodec}. This class assumes that the {@link
 * MediaCodecAdapter} methods will be accessed by the playback thread and the {@link
 * MediaCodec.Callback} methods will be accessed by the internal thread. This class is
 * <strong>NOT</strong> generally thread-safe in the sense that its public methods cannot be called
 * by any thread.
 */
@RequiresApi(23)
/* package */ final class MultiLockAsyncMediaCodecAdapter extends MediaCodec.Callback
    implements MediaCodecAdapter {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_CREATED, STATE_STARTED, STATE_SHUT_DOWN})
  private @interface State {}

  private static final int STATE_CREATED = 0;
  private static final int STATE_STARTED = 1;
  private static final int STATE_SHUT_DOWN = 2;

  private final MediaCodec codec;
  private final Object inputBufferLock;
  private final Object outputBufferLock;
  private final Object objectStateLock;

  @GuardedBy("inputBufferLock")
  private final IntArrayQueue availableInputBuffers;

  @GuardedBy("outputBufferLock")
  private final IntArrayQueue availableOutputBuffers;

  @GuardedBy("outputBufferLock")
  private final ArrayDeque<MediaCodec.BufferInfo> bufferInfos;

  @GuardedBy("outputBufferLock")
  private final ArrayDeque<MediaFormat> formats;

  @GuardedBy("objectStateLock")
  private @MonotonicNonNull MediaFormat currentFormat;

  @GuardedBy("objectStateLock")
  private long pendingFlush;

  @GuardedBy("objectStateLock")
  @Nullable
  private IllegalStateException codecException;

  private final HandlerThread handlerThread;
  private @MonotonicNonNull Handler handler;
  private Runnable codecStartRunnable;
  private final MediaCodecInputBufferEnqueuer bufferEnqueuer;

  @GuardedBy("objectStateLock")
  @State
  private int state;

  /**
   * Creates a new instance that wraps the specified {@link MediaCodec}. An instance created with
   * this constructor will queue input buffers synchronously.
   *
   * @param codec The {@link MediaCodec} to wrap.
   * @param trackType One of {@link C#TRACK_TYPE_AUDIO} or {@link C#TRACK_TYPE_VIDEO}. Used for
   *     labelling the internal thread accordingly.
   */
  /* package */ MultiLockAsyncMediaCodecAdapter(MediaCodec codec, int trackType) {
    this(
        codec,
        /* enableAsynchronousQueueing= */ false,
        trackType,
        new HandlerThread(createThreadLabel(trackType)));
  }

  /**
   * Creates a new instance that wraps the specified {@link MediaCodec}.
   *
   * @param codec The {@link MediaCodec} to wrap.
   * @param enableAsynchronousQueueing Whether input buffers will be queued asynchronously.
   * @param trackType One of {@link C#TRACK_TYPE_AUDIO} or {@link C#TRACK_TYPE_VIDEO}. Used for
   *     labelling the internal thread accordingly.
   */
  /* package */ MultiLockAsyncMediaCodecAdapter(
      MediaCodec codec, boolean enableAsynchronousQueueing, int trackType) {
    this(
        codec,
        enableAsynchronousQueueing,
        trackType,
        new HandlerThread(createThreadLabel(trackType)));
  }

  @VisibleForTesting
  /* package */ MultiLockAsyncMediaCodecAdapter(
      MediaCodec codec,
      boolean enableAsynchronousQueueing,
      int trackType,
      HandlerThread handlerThread) {
    this.codec = codec;
    inputBufferLock = new Object();
    outputBufferLock = new Object();
    objectStateLock = new Object();
    availableInputBuffers = new IntArrayQueue();
    availableOutputBuffers = new IntArrayQueue();
    bufferInfos = new ArrayDeque<>();
    formats = new ArrayDeque<>();
    codecException = null;
    this.handlerThread = handlerThread;
    codecStartRunnable = codec::start;
    if (enableAsynchronousQueueing) {
      bufferEnqueuer = new AsynchronousMediaCodecBufferEnqueuer(codec, trackType);
    } else {
      bufferEnqueuer = new SynchronousMediaCodecBufferEnqueuer(codec);
    }
    state = STATE_CREATED;
  }

  @Override
  public void start() {
    synchronized (objectStateLock) {
      handlerThread.start();
      handler = new Handler(handlerThread.getLooper());
      codec.setCallback(this, handler);
      bufferEnqueuer.start();
      codecStartRunnable.run();
      state = STATE_STARTED;
    }
  }

  @Override
  public int dequeueInputBufferIndex() {
    synchronized (objectStateLock) {
      if (isFlushing()) {
        return MediaCodec.INFO_TRY_AGAIN_LATER;
      } else {
        maybeThrowException();
        return dequeueAvailableInputBufferIndex();
      }
    }
  }

  @Override
  public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    synchronized (objectStateLock) {
      if (isFlushing()) {
        return MediaCodec.INFO_TRY_AGAIN_LATER;
      } else {
        maybeThrowException();
        return dequeueAvailableOutputBufferIndex(bufferInfo);
      }
    }
  }

  @Override
  public MediaFormat getOutputFormat() {
    synchronized (objectStateLock) {
      if (currentFormat == null) {
        throw new IllegalStateException();
      }

      return currentFormat;
    }
  }

  @Override
  public void queueInputBuffer(
      int index, int offset, int size, long presentationTimeUs, int flags) {
    // This method does not need to be synchronized because it is not interacting with
    // MediaCodec.Callback and dequeueing buffers operations.
    bufferEnqueuer.queueInputBuffer(index, offset, size, presentationTimeUs, flags);
  }

  @Override
  public void queueSecureInputBuffer(
      int index, int offset, CryptoInfo info, long presentationTimeUs, int flags) {
    // This method does not need to be synchronized because it is not interacting with
    // MediaCodec.Callback and dequeueing buffers operations.
    bufferEnqueuer.queueSecureInputBuffer(index, offset, info, presentationTimeUs, flags);
  }

  @Override
  public void flush() {
    synchronized (objectStateLock) {
      bufferEnqueuer.flush();
      codec.flush();
      pendingFlush++;
      Util.castNonNull(handler).post(this::onFlushComplete);
    }
  }

  @Override
  public void shutdown() {
    synchronized (objectStateLock) {
      if (state == STATE_STARTED) {
        bufferEnqueuer.shutdown();
        handlerThread.quit();
      }
      state = STATE_SHUT_DOWN;
    }
  }

  @VisibleForTesting
  /* package */ void setCodecStartRunnable(Runnable codecStartRunnable) {
    this.codecStartRunnable = codecStartRunnable;
  }

  private int dequeueAvailableInputBufferIndex() {
    synchronized (inputBufferLock) {
      return availableInputBuffers.isEmpty()
          ? MediaCodec.INFO_TRY_AGAIN_LATER
          : availableInputBuffers.remove();
    }
  }

  @GuardedBy("objectStateLock")
  private int dequeueAvailableOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    int bufferIndex;
    synchronized (outputBufferLock) {
      if (availableOutputBuffers.isEmpty()) {
        bufferIndex = MediaCodec.INFO_TRY_AGAIN_LATER;
      } else {
        bufferIndex = availableOutputBuffers.remove();
        if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          currentFormat = formats.remove();
        } else if (bufferIndex >= 0) {
          MediaCodec.BufferInfo outBufferInfo = bufferInfos.remove();
          bufferInfo.set(
              outBufferInfo.offset,
              outBufferInfo.size,
              outBufferInfo.presentationTimeUs,
              outBufferInfo.flags);
        }
      }
    }
    return bufferIndex;
  }

  @GuardedBy("objectStateLock")
  private boolean isFlushing() {
    return pendingFlush > 0;
  }

  @GuardedBy("objectStateLock")
  private void maybeThrowException() {
    @Nullable IllegalStateException exception = codecException;
    if (exception != null) {
      codecException = null;
      throw exception;
    }
  }

  // Called by the internal thread.

  @Override
  public void onInputBufferAvailable(MediaCodec codec, int index) {
    synchronized (inputBufferLock) {
      availableInputBuffers.add(index);
    }
  }

  @Override
  public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
    synchronized (outputBufferLock) {
      availableOutputBuffers.add(index);
      bufferInfos.add(info);
    }
  }

  @Override
  public void onError(MediaCodec codec, MediaCodec.CodecException e) {
    onMediaCodecError(e);
  }

  @Override
  public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
    synchronized (outputBufferLock) {
      availableOutputBuffers.add(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
      formats.add(format);
    }
  }

  @VisibleForTesting
  /* package */ void onMediaCodecError(IllegalStateException e) {
    synchronized (objectStateLock) {
      codecException = e;
    }
  }

  private void onFlushComplete() {
    synchronized (objectStateLock) {
      if (state == STATE_SHUT_DOWN) {
        return;
      }

      --pendingFlush;
      if (pendingFlush > 0) {
        // Another flush() has been called.
        return;
      } else if (pendingFlush < 0) {
        // This should never happen.
        codecException = new IllegalStateException();
        return;
      }

      clearAvailableInput();
      clearAvailableOutput();
      codecException = null;
      try {
        codecStartRunnable.run();
      } catch (IllegalStateException e) {
        codecException = e;
      } catch (Exception e) {
        codecException = new IllegalStateException(e);
      }
    }
  }

  private void clearAvailableInput() {
    synchronized (inputBufferLock) {
      availableInputBuffers.clear();
    }
  }

  private void clearAvailableOutput() {
    synchronized (outputBufferLock) {
      availableOutputBuffers.clear();
      bufferInfos.clear();
      formats.clear();
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
