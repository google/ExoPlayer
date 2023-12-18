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

package androidx.media3.exoplayer.mediacodec;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.view.Surface;
import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.TraceUtil;
import androidx.media3.common.util.Util;
import androidx.media3.decoder.CryptoInfo;
import com.google.common.base.Supplier;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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

    private boolean enableSynchronousBufferQueueingWithAsyncCryptoFlag;

    /**
     * Creates an factory for {@link AsynchronousMediaCodecAdapter} instances.
     *
     * @param trackType One of {@link C#TRACK_TYPE_AUDIO} or {@link C#TRACK_TYPE_VIDEO}. Used for
     *     labelling the internal thread accordingly.
     */
    public Factory(@C.TrackType int trackType) {
      this(
          /* callbackThreadSupplier= */ () ->
              new HandlerThread(createCallbackThreadLabel(trackType)),
          /* queueingThreadSupplier= */ () ->
              new HandlerThread(createQueueingThreadLabel(trackType)));
    }

    @VisibleForTesting
    /* package */ Factory(
        Supplier<HandlerThread> callbackThreadSupplier,
        Supplier<HandlerThread> queueingThreadSupplier) {
      this.callbackThreadSupplier = callbackThreadSupplier;
      this.queueingThreadSupplier = queueingThreadSupplier;
      enableSynchronousBufferQueueingWithAsyncCryptoFlag = true;
    }

    /**
     * Sets whether to enable {@link MediaCodec#CONFIGURE_FLAG_USE_CRYPTO_ASYNC} on API 34 and
     * above.
     *
     * <p>This method is experimental. Its default value may change, or it may be renamed or removed
     * in a future release.
     */
    public void experimentalSetAsyncCryptoFlagEnabled(boolean enableAsyncCryptoFlag) {
      enableSynchronousBufferQueueingWithAsyncCryptoFlag = enableAsyncCryptoFlag;
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
        int flags = configuration.flags;
        MediaCodecBufferEnqueuer bufferEnqueuer;
        if (enableSynchronousBufferQueueingWithAsyncCryptoFlag
            && useSynchronousBufferQueueingWithAsyncCryptoFlag(configuration.format)) {
          bufferEnqueuer = new SynchronousMediaCodecBufferEnqueuer(codec);
          flags |= MediaCodec.CONFIGURE_FLAG_USE_CRYPTO_ASYNC;
        } else {
          bufferEnqueuer =
              new AsynchronousMediaCodecBufferEnqueuer(codec, queueingThreadSupplier.get());
        }
        codecAdapter =
            new AsynchronousMediaCodecAdapter(codec, callbackThreadSupplier.get(), bufferEnqueuer);
        TraceUtil.endSection();
        codecAdapter.initialize(
            configuration.mediaFormat, configuration.surface, configuration.crypto, flags);
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

    @ChecksSdkIntAtLeast(api = 34)
    private static boolean useSynchronousBufferQueueingWithAsyncCryptoFlag(Format format) {
      if (Util.SDK_INT < 34) {
        return false;
      }
      // TODO: b/316565675 - Remove restriction to video once MediaCodec supports
      //  CONFIGURE_FLAG_USE_CRYPTO_ASYNC for audio too
      return MimeTypes.isVideo(format.sampleMimeType);
    }
  }

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({STATE_CREATED, STATE_INITIALIZED, STATE_SHUT_DOWN})
  private @interface State {}

  private static final int STATE_CREATED = 0;
  private static final int STATE_INITIALIZED = 1;
  private static final int STATE_SHUT_DOWN = 2;

  private final MediaCodec codec;
  private final AsynchronousMediaCodecCallback asynchronousMediaCodecCallback;
  private final MediaCodecBufferEnqueuer bufferEnqueuer;
  private boolean codecReleased;
  private @State int state;

  private AsynchronousMediaCodecAdapter(
      MediaCodec codec, HandlerThread callbackThread, MediaCodecBufferEnqueuer bufferEnqueuer) {
    this.codec = codec;
    this.asynchronousMediaCodecCallback = new AsynchronousMediaCodecCallback(callbackThread);
    this.bufferEnqueuer = bufferEnqueuer;
    this.state = STATE_CREATED;
  }

  private void initialize(
      @Nullable MediaFormat mediaFormat,
      @Nullable Surface surface,
      @Nullable MediaCrypto crypto,
      int flags) {
    asynchronousMediaCodecCallback.initialize(codec);
    TraceUtil.beginSection("configureCodec");
    codec.configure(mediaFormat, surface, crypto, flags);
    TraceUtil.endSection();
    bufferEnqueuer.start();
    TraceUtil.beginSection("startCodec");
    codec.start();
    TraceUtil.endSection();
    state = STATE_INITIALIZED;
  }

  @Override
  public boolean needsReconfiguration() {
    return false;
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
    bufferEnqueuer.maybeThrowException();
    return asynchronousMediaCodecCallback.dequeueInputBufferIndex();
  }

  @Override
  public int dequeueOutputBufferIndex(MediaCodec.BufferInfo bufferInfo) {
    bufferEnqueuer.maybeThrowException();
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
    // 1. Flush the bufferEnqueuer to stop queueing input buffers.
    // 2. Flush the codec to stop producing available input/output buffers.
    // 3. Flush the callback so that in-flight callbacks are discarded.
    // 4. Start the codec. The asynchronous callback will drop pending callbacks and we can start
    //    the codec now.
    bufferEnqueuer.flush();
    codec.flush();
    asynchronousMediaCodecCallback.flush();
    codec.start();
  }

  @Override
  public void release() {
    try {
      if (state == STATE_INITIALIZED) {
        bufferEnqueuer.shutdown();
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
    codec.setOnFrameRenderedListener(
        (codec, presentationTimeUs, nanoTime) ->
            listener.onFrameRendered(
                AsynchronousMediaCodecAdapter.this, presentationTimeUs, nanoTime),
        handler);
  }

  @Override
  public void setOutputSurface(Surface surface) {
    codec.setOutputSurface(surface);
  }

  @Override
  public void setParameters(Bundle params) {
    bufferEnqueuer.setParameters(params);
  }

  @Override
  public void setVideoScalingMode(@C.VideoScalingMode int scalingMode) {
    codec.setVideoScalingMode(scalingMode);
  }

  @Override
  @RequiresApi(26)
  public PersistableBundle getMetrics() {
    return codec.getMetrics();
  }

  @VisibleForTesting
  /* package */ void onError(MediaCodec.CodecException error) {
    asynchronousMediaCodecCallback.onError(codec, error);
  }

  @VisibleForTesting
  /* package */ void onOutputFormatChanged(MediaFormat format) {
    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, format);
  }

  private static String createCallbackThreadLabel(@C.TrackType int trackType) {
    return createThreadLabel(trackType, /* prefix= */ "ExoPlayer:MediaCodecAsyncAdapter:");
  }

  private static String createQueueingThreadLabel(@C.TrackType int trackType) {
    return createThreadLabel(trackType, /* prefix= */ "ExoPlayer:MediaCodecQueueingThread:");
  }

  private static String createThreadLabel(@C.TrackType int trackType, String prefix) {
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
