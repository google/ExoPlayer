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

import static com.google.android.exoplayer2.testutil.TestUtil.assertBufferInfosEqual;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.robolectric.Shadows.shadowOf;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

/** Unit tests for {@link AsynchronousMediaCodecCallback}. */
@RunWith(AndroidJUnit4.class)
public class AsynchronousMediaCodecCallbackTest {

  private AsynchronousMediaCodecCallback asynchronousMediaCodecCallback;
  private TestHandlerThread callbackThread;
  private MediaCodec codec;

  @Before
  public void setUp() throws IOException {
    callbackThread = new TestHandlerThread("TestCallbackThread");
    codec = MediaCodec.createByCodecName("h264");
    asynchronousMediaCodecCallback = new AsynchronousMediaCodecCallback(callbackThread);
    asynchronousMediaCodecCallback.initialize(codec);
  }

  @After
  public void tearDown() {
    codec.release();
    asynchronousMediaCodecCallback.shutdown();

    assertThat(callbackThread.hasQuit()).isTrue();
  }

  @Test
  public void dequeInputBufferIndex_afterCreation_returnsTryAgain() {
    assertThat(asynchronousMediaCodecCallback.dequeueInputBufferIndex())
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeInputBufferIndex_returnsEnqueuedBuffers() {
    // Send two input buffers to the callback.
    asynchronousMediaCodecCallback.onInputBufferAvailable(codec, 0);
    asynchronousMediaCodecCallback.onInputBufferAvailable(codec, 1);

    assertThat(asynchronousMediaCodecCallback.dequeueInputBufferIndex()).isEqualTo(0);
    assertThat(asynchronousMediaCodecCallback.dequeueInputBufferIndex()).isEqualTo(1);
    assertThat(asynchronousMediaCodecCallback.dequeueInputBufferIndex())
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeInputBufferIndex_withPendingFlush_returnsTryAgain() {
    AtomicBoolean beforeFlushCompletes = new AtomicBoolean();
    AtomicBoolean flushCompleted = new AtomicBoolean();
    Looper callbackThreadLooper = callbackThread.getLooper();
    Handler callbackHandler = new Handler(callbackThreadLooper);
    ShadowLooper shadowCallbackLooper = shadowOf(callbackThreadLooper);
    // Pause the callback thread so that flush() never completes.
    shadowCallbackLooper.pause();

    // Send two input buffers to the callback and then flush().
    asynchronousMediaCodecCallback.onInputBufferAvailable(codec, 0);
    asynchronousMediaCodecCallback.onInputBufferAvailable(codec, 1);
    callbackHandler.post(() -> beforeFlushCompletes.set(true));
    asynchronousMediaCodecCallback.flush();
    callbackHandler.post(() -> flushCompleted.set(true));
    while (!beforeFlushCompletes.get()) {
      shadowCallbackLooper.runOneTask();
    }

    assertThat(flushCompleted.get()).isFalse();
    assertThat(asynchronousMediaCodecCallback.dequeueInputBufferIndex())
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeInputBufferIndex_afterFlush_returnsTryAgain() {
    Looper callbackThreadLooper = callbackThread.getLooper();
    AtomicBoolean flushCompleted = new AtomicBoolean();

    // Send two input buffers to the callback and then flush().
    asynchronousMediaCodecCallback.onInputBufferAvailable(codec, 0);
    asynchronousMediaCodecCallback.onInputBufferAvailable(codec, 1);
    asynchronousMediaCodecCallback.flush();
    new Handler(callbackThreadLooper).post(() -> flushCompleted.set(true));
    // Progress the callback thread so that flush() completes.
    shadowOf(callbackThreadLooper).idle();

    assertThat(flushCompleted.get()).isTrue();
    assertThat(asynchronousMediaCodecCallback.dequeueInputBufferIndex())
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeInputBufferIndex_afterFlushAndNewInputBuffer_returnsEnqueuedBuffer() {
    Looper callbackThreadLooper = callbackThread.getLooper();
    AtomicBoolean flushCompleted = new AtomicBoolean();

    // Send two input buffers to the callback, then flush(), then send
    // another input buffer.
    asynchronousMediaCodecCallback.onInputBufferAvailable(codec, 0);
    asynchronousMediaCodecCallback.onInputBufferAvailable(codec, 1);
    asynchronousMediaCodecCallback.flush();
    new Handler(callbackThreadLooper).post(() -> flushCompleted.set(true));
    // Progress the callback thread to complete flush.
    shadowOf(callbackThread.getLooper()).idle();
    // Send another input buffer to the callback
    asynchronousMediaCodecCallback.onInputBufferAvailable(codec, 2);

    assertThat(flushCompleted.get()).isTrue();
    assertThat(asynchronousMediaCodecCallback.dequeueInputBufferIndex()).isEqualTo(2);
  }

  @Test
  public void dequeueInputBufferIndex_afterShutdown_returnsTryAgainLater() {
    asynchronousMediaCodecCallback.onInputBufferAvailable(codec, /* index= */ 1);

    asynchronousMediaCodecCallback.shutdown();

    assertThat(asynchronousMediaCodecCallback.dequeueInputBufferIndex())
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueInputBufferIndex_afterOnErrorCallback_throwsError() throws Exception {
    asynchronousMediaCodecCallback.onError(codec, createCodecException());

    assertThrows(
        MediaCodec.CodecException.class,
        () -> asynchronousMediaCodecCallback.dequeueInputBufferIndex());
  }

  @Test
  public void dequeOutputBufferIndex_afterCreation_returnsTryAgain() {
    MediaCodec.BufferInfo outBufferInfo = new MediaCodec.BufferInfo();
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outBufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeOutputBufferIndex_returnsEnqueuedBuffers() {
    // Send an output format and two output buffers to the callback.
    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, createMediaFormat("format0"));
    MediaCodec.BufferInfo bufferInfo1 = new MediaCodec.BufferInfo();
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, 0, bufferInfo1);
    MediaCodec.BufferInfo bufferInfo2 = new MediaCodec.BufferInfo();
    bufferInfo2.set(1, 1, 1, 1);
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, 1, bufferInfo2);

    MediaCodec.BufferInfo outBufferInfo = new MediaCodec.BufferInfo();
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outBufferInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    assertThat(asynchronousMediaCodecCallback.getOutputFormat().getString("name"))
        .isEqualTo("format0");
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outBufferInfo)).isEqualTo(0);
    assertBufferInfosEqual(bufferInfo1, outBufferInfo);
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outBufferInfo)).isEqualTo(1);
    assertBufferInfosEqual(bufferInfo2, outBufferInfo);
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outBufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeOutputBufferIndex_withPendingFlush_returnsTryAgain() {
    AtomicBoolean beforeFlushCompletes = new AtomicBoolean();
    AtomicBoolean flushCompleted = new AtomicBoolean();
    Looper callbackThreadLooper = callbackThread.getLooper();
    Handler callbackHandler = new Handler(callbackThreadLooper);
    ShadowLooper shadowCallbackLooper = shadowOf(callbackThreadLooper);
    // Pause the callback thread so that flush() never completes.
    shadowCallbackLooper.pause();

    // Send two output buffers to the callback and then flush().
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, 0, bufferInfo);
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, 1, bufferInfo);
    callbackHandler.post(() -> beforeFlushCompletes.set(true));
    asynchronousMediaCodecCallback.flush();
    callbackHandler.post(() -> flushCompleted.set(true));
    while (beforeFlushCompletes.get()) {
      shadowCallbackLooper.runOneTask();
    }

    assertThat(flushCompleted.get()).isFalse();
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(new MediaCodec.BufferInfo()))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeOutputBufferIndex_afterFlush_returnsTryAgain() {
    Looper callbackThreadLooper = callbackThread.getLooper();
    AtomicBoolean flushCompleted = new AtomicBoolean();

    // Send two output buffers to the callback and then flush().
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, 0, bufferInfo);
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, 1, bufferInfo);
    asynchronousMediaCodecCallback.flush();
    new Handler(callbackThreadLooper).post(() -> flushCompleted.set(true));
    // Progress the callback looper so that flush() completes.
    shadowOf(callbackThreadLooper).idle();

    assertThat(flushCompleted.get()).isTrue();
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(new MediaCodec.BufferInfo()))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeOutputBufferIndex_afterFlushAndNewOutputBuffers_returnsEnqueueBuffer() {
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    Looper callbackThreadLooper = callbackThread.getLooper();
    AtomicBoolean flushCompleted = new AtomicBoolean();

    // Send an output format and two output buffers to the callback, then flush(), then send
    // another output buffer.
    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, createMediaFormat("format0"));
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, 0, bufferInfo);
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, 1, bufferInfo);
    asynchronousMediaCodecCallback.flush();
    new Handler(callbackThreadLooper).post(() -> flushCompleted.set(true));
    // Progress the callback looper so that flush() completes.
    shadowOf(callbackThreadLooper).idle();
    // Emulate an output buffer is available.
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, 2, bufferInfo);
    MediaCodec.BufferInfo outBufferInfo = new MediaCodec.BufferInfo();

    assertThat(flushCompleted.get()).isTrue();
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outBufferInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    assertThat(asynchronousMediaCodecCallback.getOutputFormat().getString("name"))
        .isEqualTo("format0");
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outBufferInfo)).isEqualTo(2);
  }

  @Test
  public void dequeOutputBufferIndex_withPendingOutputFormat_returnsPendingOutputFormat() {
    MediaCodec.BufferInfo outBufferInfo = new MediaCodec.BufferInfo();
    Looper callbackThreadLooper = callbackThread.getLooper();
    AtomicBoolean flushCompleted = new AtomicBoolean();

    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, new MediaFormat());
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, /* index= */ 0, outBufferInfo);
    MediaFormat pendingMediaFormat = new MediaFormat();
    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, pendingMediaFormat);
    // flush() should not discard the last format.
    asynchronousMediaCodecCallback.flush();
    new Handler(callbackThreadLooper).post(() -> flushCompleted.set(true));
    // Progress the callback looper so that flush() completes.
    shadowOf(callbackThreadLooper).idle();
    // Right after flush(), we send an output buffer: the pending output format should be
    // dequeued first.
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, /* index= */ 1, outBufferInfo);

    assertThat(flushCompleted.get()).isTrue();
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outBufferInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    assertThat(asynchronousMediaCodecCallback.getOutputFormat()).isEqualTo(pendingMediaFormat);
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outBufferInfo)).isEqualTo(1);
  }

  @Test
  public void dequeOutputBufferIndex_withPendingOutputFormatAndNewFormat_returnsNewFormat() {
    MediaCodec.BufferInfo outBufferInfo = new MediaCodec.BufferInfo();
    Looper callbackThreadLooper = callbackThread.getLooper();
    AtomicBoolean flushCompleted = new AtomicBoolean();

    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, new MediaFormat());
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, /* index= */ 0, bufferInfo);
    MediaFormat pendingMediaFormat = new MediaFormat();
    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, pendingMediaFormat);
    // flush() should not discard the last format.
    asynchronousMediaCodecCallback.flush();
    new Handler(callbackThreadLooper).post(() -> flushCompleted.set(true));
    // Progress the callback looper so that flush() completes.
    shadowOf(callbackThreadLooper).idle();
    // The first callback after flush() is a new MediaFormat, it should overwrite the pending
    // format.
    MediaFormat newFormat = new MediaFormat();
    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, newFormat);
    asynchronousMediaCodecCallback.onOutputBufferAvailable(codec, /* index= */ 1, bufferInfo);

    assertThat(flushCompleted.get()).isTrue();
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outBufferInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    assertThat(asynchronousMediaCodecCallback.getOutputFormat()).isEqualTo(newFormat);
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outBufferInfo)).isEqualTo(1);
  }

  @Test
  public void dequeueOutputBufferIndex_afterShutdown_returnsTryAgainLater() {
    asynchronousMediaCodecCallback.onOutputBufferAvailable(
        codec, /* index= */ 1, new MediaCodec.BufferInfo());

    asynchronousMediaCodecCallback.shutdown();

    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(new MediaCodec.BufferInfo()))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueOutputBufferIndex_afterOnErrorCallback_throwsError() throws Exception {
    asynchronousMediaCodecCallback.onError(codec, createCodecException());

    assertThrows(
        MediaCodec.CodecException.class,
        () -> asynchronousMediaCodecCallback.dequeueOutputBufferIndex(new MediaCodec.BufferInfo()));
  }

  @Test
  public void getOutputFormat_onNewInstance_raisesException() {
    try {
      asynchronousMediaCodecCallback.getOutputFormat();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void getOutputFormat_afterOnOutputFormatCalled_returnsFormat() {
    MediaFormat format = new MediaFormat();
    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, format);
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    assertThat(asynchronousMediaCodecCallback.getOutputFormat()).isEqualTo(format);
  }

  @Test
  public void getOutputFormat_afterFlush_returnsCurrentFormat() {
    MediaFormat format = new MediaFormat();
    Looper callbackThreadLooper = callbackThread.getLooper();
    AtomicBoolean flushCompleted = new AtomicBoolean();

    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, format);
    asynchronousMediaCodecCallback.dequeueOutputBufferIndex(new MediaCodec.BufferInfo());
    asynchronousMediaCodecCallback.flush();
    new Handler(callbackThreadLooper).post(() -> flushCompleted.set(true));
    // Progress the callback looper so that flush() completes.
    shadowOf(callbackThreadLooper).idle();

    assertThat(flushCompleted.get()).isTrue();
    assertThat(asynchronousMediaCodecCallback.getOutputFormat()).isEqualTo(format);
  }

  @Test
  public void getOutputFormat_afterFlushWithPendingFormat_returnsPendingFormat() {
    MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
    AtomicBoolean flushCompleted = new AtomicBoolean();
    Looper callbackThreadLooper = callbackThread.getLooper();
    ShadowLooper shadowCallbackLooper = shadowOf(callbackThreadLooper);
    shadowCallbackLooper.pause();

    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, createMediaFormat("format0"));
    asynchronousMediaCodecCallback.onOutputBufferAvailable(
        codec, /* index= */ 0, new MediaCodec.BufferInfo());
    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, createMediaFormat("format1"));
    asynchronousMediaCodecCallback.onOutputBufferAvailable(
        codec, /* index= */ 1, new MediaCodec.BufferInfo());
    asynchronousMediaCodecCallback.flush();
    new Handler(callbackThreadLooper).post(() -> flushCompleted.set(true));
    // Progress the looper so that flush is completed
    shadowCallbackLooper.idle();
    // Enqueue an output buffer to make the pending format available.
    asynchronousMediaCodecCallback.onOutputBufferAvailable(
        codec, /* index= */ 2, new MediaCodec.BufferInfo());

    assertThat(flushCompleted.get()).isTrue();
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    assertThat(asynchronousMediaCodecCallback.getOutputFormat().getString("name"))
        .isEqualTo("format1");
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outInfo)).isEqualTo(2);
  }

  @Test
  public void
      getOutputFormat_withConsecutiveFlushAndPendingFormatFromFirstFlush_returnsPendingFormat() {
    MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
    AtomicInteger flushCompleted = new AtomicInteger();
    Handler callbackThreadHandler = new Handler(callbackThread.getLooper());
    ShadowLooper shadowCallbackLooper = shadowOf(callbackThread.getLooper());
    shadowCallbackLooper.pause();

    asynchronousMediaCodecCallback.onOutputFormatChanged(codec, createMediaFormat("format0"));
    asynchronousMediaCodecCallback.onOutputBufferAvailable(
        codec, /* index= */ 0, new MediaCodec.BufferInfo());
    // Flush and progress the looper so that flush is completed.
    asynchronousMediaCodecCallback.flush();
    callbackThreadHandler.post(flushCompleted::incrementAndGet);
    shadowCallbackLooper.idle();
    // Flush again, the pending format from the first flush should remain as pending.
    asynchronousMediaCodecCallback.flush();
    callbackThreadHandler.post(flushCompleted::incrementAndGet);
    shadowCallbackLooper.idle();
    asynchronousMediaCodecCallback.onOutputBufferAvailable(
        codec, /* index= */ 1, new MediaCodec.BufferInfo());

    assertThat(flushCompleted.get()).isEqualTo(2);
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    assertThat(asynchronousMediaCodecCallback.getOutputFormat().getString("name"))
        .isEqualTo("format0");
    assertThat(asynchronousMediaCodecCallback.dequeueOutputBufferIndex(outInfo)).isEqualTo(1);
  }

  @Test
  public void flush_withPendingError_resetsError() throws Exception {
    asynchronousMediaCodecCallback.onError(codec, createCodecException());
    // Calling flush should clear any pending error.
    asynchronousMediaCodecCallback.flush();

    assertThat(asynchronousMediaCodecCallback.dequeueInputBufferIndex())
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void shutdown_withPendingError_doesNotThrow() throws Exception {
    asynchronousMediaCodecCallback.onError(codec, createCodecException());

    // Calling shutdown() should not throw.
    asynchronousMediaCodecCallback.shutdown();
  }

  /** Reflectively create a {@link MediaCodec.CodecException}. */
  private static MediaCodec.CodecException createCodecException() throws Exception {
    Constructor<MediaCodec.CodecException> constructor =
        MediaCodec.CodecException.class.getDeclaredConstructor(
            Integer.TYPE, Integer.TYPE, String.class);
    return constructor.newInstance(
        /* errorCode= */ 0, /* actionCode= */ 0, /* detailMessage= */ "error from codec");
  }

  private static MediaFormat createMediaFormat(String name) {
    MediaFormat format = new MediaFormat();
    format.setString("name", name);
    return format;
  }

  private static class TestHandlerThread extends HandlerThread {
    private boolean quit;

    TestHandlerThread(String label) {
      super(label);
    }

    public boolean hasQuit() {
      return quit;
    }

    @Override
    public boolean quit() {
      quit = true;
      return super.quit();
    }
  }
}
