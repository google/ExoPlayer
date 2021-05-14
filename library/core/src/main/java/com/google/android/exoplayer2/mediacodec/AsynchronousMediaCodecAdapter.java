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
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.util.TraceUtil;
import com.google.common.base.Supplier;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

/**
 * A {@link MediaCodecAdapter} that operates the underlying {@link MediaCodec} in asynchronous mode,
 * routes {@link MediaCodec.Callback} callbacks on a dedicated thread that is managed internally,
 * and queues input buffers asynchronously.
 */
@RequiresApi(23)
/* package */ final class AsynchronousMediaCodecAdapter implements MediaCodecAdapter {

  /** A factory for {@link AsynchronousMediaCodecAdapter} instances. */
  public static final class Factory implements MediaCodecAdapter.Factory {
    private final Supplier<HandlerThread> callbackThreadSupplier;
    private final Supplier<HandlerThread> queueingThreadSupplier;
    private final boolean forceQueueingSynchronizationWorkaround;
    private final boolean synchronizeCodecInteractionsWithQueueing;

    /** Creates a factory for the specified {@code trackType}. */
    public Factory(int trackType) {
      this(
          trackType,
          /* forceQueueingSynchronizationWorkaround= */ false,
          /* synchronizeCodecInteractionsWithQueueing= */ false);
    }

    /**
     * Creates an factory for {@link AsynchronousMediaCodecAdapter} instances.
     *
     * @param trackType One of {@link C#TRACK_TYPE_AUDIO} or {@link C#TRACK_TYPE_VIDEO}. Used for
     *     labelling the internal thread accordingly.
     * @param forceQueueingSynchronizationWorkaround Whether the queueing synchronization workaround
     *     will be enabled by default or only for the predefined devices.
     * @param synchronizeCodecInteractionsWithQueueing Whether the adapter should synchronize {@link
     *     MediaCodec} interactions with asynchronous buffer queueing. When {@code true}, codec
     *     interactions will wait until all input buffers pending queueing wil be submitted to the
     *     {@link MediaCodec}.
     */
    public Factory(
        int trackType,
        boolean forceQueueingSynchronizationWorkaround,
        boolean synchronizeCodecInteractionsWithQueueing) {
      this(
          /* callbackThreadSupplier= */ () ->
              new HandlerThread(createCallbackThreadLabel(trackType)),
          /* queueingThreadSupplier= */ () ->
              new HandlerThread(createQueueingThreadLabel(trackType)),
          forceQueueingSynchronizationWorkaround,
          synchronizeCodecInteractionsWithQueueing);
    }

    @VisibleForTesting
    /* package */ Factory(
        Supplier<HandlerThread> callbackThreadSupplier,
        Supplier<HandlerThread> queueingThreadSupplier,
        boolean forceQueueingSynchronizationWorkaround,
        boolean synchronizeCodecInteractionsWithQueueing) {
      this.callbackThreadSupplier = callbackThreadSupplier;
      this.queueingThreadSupplier = queueingThreadSupplier;
      this.forceQueueingSynchronizationWorkaround = forceQueueingSynchronizationWorkaround;
      this.synchronizeCodecInteractionsWithQueueing = synchronizeCodecInteractionsWithQueueing;
    }

    @Override
    public AsynchronousMediaCodecAdapter createAdapter(Configuration configuration)
        throws IOException {
      String codecName = configuration.codecInfo.name;
      @Nullable AsynchronousMediaCodecAdapter codecAdapter = null;
      @Nullable MediaCodec codec = null;
      try {
        TraceUtil.beginSection("createCodec:" + codecName);
        codec = MediaCodec.createByCodecName(codecName);
        codecAdapter =
            new AsynchronousMediaCodecAdapter(
                codec,
                callbackThreadSupplier.get(),
                queueingThreadSupplier.get(),
                forceQueueingSynchronizationWorkaround,
                synchronizeCodecInteractionsWithQueueing);
        TraceUtil.endSection();
        TraceUtil.beginSection("configureCodec");
        codecAdapter.configure(
            configuration.mediaFormat,
            configuration.surface,
            configuration.crypto,
            configuration.flags);
        TraceUtil.endSection();
        TraceUtil.beginSection("startCodec");
        codecAdapter.start();
        TraceUtil.endSection();
        return codecAdapter;
      } catch (Exception e) {
        if (codecAdapter != null) {
          codecAdapter.release();
        } else if (codec != null) {
          codec.release();
        }
        throw e;
      }
    }
  }

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_CREATED, STATE_CONFIGURED, STATE_STARTED, STATE_SHUT_DOWN})
  private @interface State {}

  private static final int STATE_CREATED = 0;
  private static final int STATE_CONFIGURED = 1;
  private static final int STATE_STARTED = 2;
  private static final int STATE_SHUT_DOWN = 3;

  private final MediaCodec codec;
  private final AsynchronousMediaCodecCallback asynchronousMediaCodecCallback;
  private final AsynchronousMediaCodecBufferEnqueuer bufferEnqueuer;
  private final boolean synchronizeCodecInteractionsWithQueueing;
  private boolean codecReleased;
  @State private int state;

  private AsynchronousMediaCodecAdapter(
      MediaCodec codec,
      HandlerThread callbackThread,
      HandlerThread enqueueingThread,
      boolean forceQueueingSynchronizationWorkaround,
      boolean synchronizeCodecInteractionsWithQueueing) {
    this.codec = codec;
    this.asynchronousMediaCodecCallback = new AsynchronousMediaCodecCallback(callbackThread);
    this.bufferEnqueuer =
        new AsynchronousMediaCodecBufferEnqueuer(
            codec, enqueueingThread, forceQueueingSynchronizationWorkaround);
    this.synchronizeCodecInteractionsWithQueueing = synchronizeCodecInteractionsWithQueueing;
    this.state = STATE_CREATED;
  }

  private void configure(
      @Nullable MediaFormat mediaFormat,
      @Nullable Surface surface,
      @Nullable MediaCrypto crypto,
      int flags) {
    asynchronousMediaCodecCallback.initialize(codec);
    codec.configure(mediaFormat, surface, crypto, flags);
    state = STATE_CONFIGURED;
  }

  private void start() {
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
  public void releaseOutputBuffer(int index, boolean render) {
    codec.releaseOutputBuffer(index, render);
  }

  @Override
  public void releaseOutputBuffer(int index, long renderTimeStampNs) {
    codec.releaseOutputBuffer(index, renderTimeStampNs);
  }

  @Override
  public int dequeueInputBufferIndex() {
    return asynchronousMediaCodecCallback.dequeueInputBufferIndex();
  }

  @Override
  public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    return asynchronousMediaCodecCallback.dequeueOutputBufferIndex(bufferInfo);
  }

  @Override
  public MediaFormat getOutputFormat() {
    return asynchronousMediaCodecCallback.getOutputFormat();
  }

  @Override
  @Nullable
  public ByteBuffer getInputBuffer(int index) {
    return codec.getInputBuffer(index);
  }

  @Override
  @Nullable
  public ByteBuffer getOutputBuffer(int index) {
    return codec.getOutputBuffer(index);
  }

  @Override
  public void flush() {
    // The order of calls is important:
    // First, flush the bufferEnqueuer to stop queueing input buffers.
    // Second, flush the codec to stop producing available input/output buffers.
    // Third, flush the callback after flushing the codec so that in-flight callbacks are discarded.
    bufferEnqueuer.flush();
    codec.flush();
    // When flushAsync() is completed, start the codec again.
    asynchronousMediaCodecCallback.flushAsync(/* onFlushCompleted= */ codec::start);
  }

  @Override
  public void release() {
    try {
      if (state == STATE_STARTED) {
        bufferEnqueuer.shutdown();
      }
      if (state == STATE_CONFIGURED || state == STATE_STARTED) {
        asynchronousMediaCodecCallback.shutdown();
      }
      state = STATE_SHUT_DOWN;
    } finally {
      if (!codecReleased) {
        codec.release();
        codecReleased = true;
      }
    }
  }

  @Override
  public void setOnFrameRenderedListener(OnFrameRenderedListener listener, Handler handler) {
    maybeBlockOnQueueing();
    codec.setOnFrameRenderedListener(
        (codec, presentationTimeUs, nanoTime) ->
            listener.onFrameRendered(
                AsynchronousMediaCodecAdapter.this, presentationTimeUs, nanoTime),
        handler);
  }

  @Override
  public void setOutputSurface(Surface surface) {
    maybeBlockOnQueueing();
    codec.setOutputSurface(surface);
  }

  @Override
  public void setParameters(Bundle params) {
    maybeBlockOnQueueing();
    codec.setParameters(params);
  }

  @Override
  public void setVideoScalingMode(@C.VideoScalingMode int scalingMode) {
    maybeBlockOnQueueing();
    codec.setVideoScalingMode(scalingMode);
  }

  @VisibleForTesting
  /* package */ void onError(MediaCodec.CodecException error) {
    asynchronousMediaCodecCallback.onError(codec, error);
  }

  @VisibleForTesting
  /* package */ void onOutputFormatChanged(MediaFormat format) {
    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, format);
  }

  private void maybeBlockOnQueueing() {
    if (synchronizeCodecInteractionsWithQueueing) {
      try {
        bufferEnqueuer.waitUntilQueueingComplete();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // The playback thread should not be interrupted. Raising this as an
        // IllegalStateException.
        throw new IllegalStateException(e);
      }
    }
  }

  private static String createCallbackThreadLabel(int trackType) {
    return createThreadLabel(trackType, /* prefix= */ "ExoPlayer:MediaCodecAsyncAdapter:");
  }

  private static String createQueueingThreadLabel(int trackType) {
    return createThreadLabel(trackType, /* prefix= */ "ExoPlayer:MediaCodecQueueingThread:");
  }

  private static String createThreadLabel(int trackType, String prefix) {
    StringBuilder labelBuilder = new StringBuilder(prefix);
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
