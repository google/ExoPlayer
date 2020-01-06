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

import static com.google.android.exoplayer2.mediacodec.MediaCodecTestUtils.areEqual;
import static com.google.android.exoplayer2.mediacodec.MediaCodecTestUtils.waitUntilAllEventsAreExecuted;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AsynchronousMediaCodecAdapter}. */
@RunWith(AndroidJUnit4.class)
public class AsynchronousMediaCodecAdapterTest {
  private AsynchronousMediaCodecAdapter adapter;
  private MediaCodec codec;
  private HandlerThread handlerThread;
  private Looper looper;
  private MediaCodec.BufferInfo bufferInfo;

  @Before
  public void setup() throws IOException {
    handlerThread = new HandlerThread("TestHandlerThread");
    handlerThread.start();
    looper = handlerThread.getLooper();
    codec = MediaCodec.createByCodecName("h264");
    adapter = new AsynchronousMediaCodecAdapter(codec, looper);
    bufferInfo = new MediaCodec.BufferInfo();
  }

  @After
  public void tearDown() {
    handlerThread.quit();
  }

  @Test
  public void dequeueInputBufferIndex_withoutInputBuffer_returnsTryAgainLater() {
    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueInputBufferIndex_withInputBuffer_returnsInputBuffer() {
    adapter.getMediaCodecCallback().onInputBufferAvailable(codec, /* index=*/ 0);

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(0);
  }

  @Test
  public void dequeueInputBufferIndex_whileFlushing_returnsTryAgainLater() {
    adapter.getMediaCodecCallback().onInputBufferAvailable(codec, /* index=*/ 0);
    adapter.flush();
    adapter.getMediaCodecCallback().onInputBufferAvailable(codec, /* index=*/ 1);

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueInputBufferIndex_afterFlushCompletes_returnsNextInputBuffer()
      throws InterruptedException {
    // Disable calling codec.start() after flush() completes to avoid receiving buffers from the
    // shadow codec impl
    adapter.setOnCodecStart(() -> {});
    Handler handler = new Handler(looper);
    handler.post(
        () -> adapter.getMediaCodecCallback().onInputBufferAvailable(codec, /* index=*/ 0));
    adapter.flush(); // enqueues a flush event on the looper
    handler.post(
        () -> adapter.getMediaCodecCallback().onInputBufferAvailable(codec, /* index=*/ 1));

    assertThat(waitUntilAllEventsAreExecuted(looper, /* time= */ 5, TimeUnit.SECONDS)).isTrue();
    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(1);
  }

  @Test
  public void dequeueInputBufferIndex_afterFlushCompletesWithError_throwsException()
      throws InterruptedException {
    adapter.setOnCodecStart(
        () -> {
          throw new IllegalStateException("codec#start() exception");
        });
    adapter.flush();

    assertThat(waitUntilAllEventsAreExecuted(looper, /* time= */ 5, TimeUnit.SECONDS)).isTrue();
    try {
      adapter.dequeueInputBufferIndex();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void dequeueOutputBufferIndex_withoutOutputBuffer_returnsTryAgainLater() {
    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueOutputBufferIndex_withOutputBuffer_returnsOutputBuffer() {
    MediaCodec.BufferInfo outBufferInfo = new MediaCodec.BufferInfo();
    outBufferInfo.presentationTimeUs = 10;
    adapter.getMediaCodecCallback().onOutputBufferAvailable(codec, /* index=*/ 0, outBufferInfo);

    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo)).isEqualTo(0);
    assertThat(areEqual(bufferInfo, outBufferInfo)).isTrue();
  }

  @Test
  public void dequeueOutputBufferIndex_whileFlushing_returnsTryAgainLater() {
    adapter.getMediaCodecCallback().onOutputBufferAvailable(codec, /* index=*/ 0, bufferInfo);
    adapter.flush();
    adapter.getMediaCodecCallback().onOutputBufferAvailable(codec, /* index=*/ 1, bufferInfo);

    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueOutputBufferIndex_afterFlushCompletes_returnsNextOutputBuffer()
      throws InterruptedException {
    // Disable calling codec.start() after flush() completes to avoid receiving buffers from the
    // shadow codec impl
    adapter.setOnCodecStart(() -> {});
    Handler handler = new Handler(looper);
    MediaCodec.BufferInfo info0 = new MediaCodec.BufferInfo();
    handler.post(
        () -> adapter.getMediaCodecCallback().onOutputBufferAvailable(codec, /* index=*/ 0, info0));
    adapter.flush(); // enqueues a flush event on the looper
    MediaCodec.BufferInfo info1 = new MediaCodec.BufferInfo();
    info1.presentationTimeUs = 1;
    handler.post(
        () -> adapter.getMediaCodecCallback().onOutputBufferAvailable(codec, /* index=*/ 1, info1));

    assertThat(waitUntilAllEventsAreExecuted(looper, /* time= */ 5, TimeUnit.SECONDS)).isTrue();
    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo)).isEqualTo(1);
    assertThat(areEqual(bufferInfo, info1)).isTrue();
  }

  @Test
  public void dequeueOutputBufferIndex_afterFlushCompletesWithError_throwsException()
      throws InterruptedException {
    adapter.setOnCodecStart(
        () -> {
          throw new RuntimeException("codec#start() exception");
        });
    adapter.flush();

    assertThat(waitUntilAllEventsAreExecuted(looper, /* time= */ 5, TimeUnit.SECONDS)).isTrue();
    try {
      adapter.dequeueOutputBufferIndex(bufferInfo);
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void getOutputFormat_withoutFormat_throwsException() {
    try {
      adapter.getOutputFormat();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void getOutputFormat_withMultipleFormats_returnsFormatsInCorrectOrder() {
    MediaFormat[] formats = new MediaFormat[10];
    MediaCodec.Callback mediaCodecCallback = adapter.getMediaCodecCallback();
    for (int i = 0; i < formats.length; i++) {
      formats[i] = new MediaFormat();
      mediaCodecCallback.onOutputFormatChanged(codec, formats[i]);
    }

    for (MediaFormat format : formats) {
      assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
          .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
      assertThat(adapter.getOutputFormat()).isEqualTo(format);
      // Call it again to ensure same format is returned
      assertThat(adapter.getOutputFormat()).isEqualTo(format);
    }
    // Obtain next output buffer
    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
    // Format should remain as is
    assertThat(adapter.getOutputFormat()).isEqualTo(formats[formats.length - 1]);
  }

  @Test
  public void getOutputFormat_afterFlush_returnsPreviousFormat() throws InterruptedException {
    MediaFormat format = new MediaFormat();
    adapter.getMediaCodecCallback().onOutputFormatChanged(codec, format);
    adapter.dequeueOutputBufferIndex(bufferInfo);
    adapter.flush();

    assertThat(waitUntilAllEventsAreExecuted(looper, /* time= */ 5, TimeUnit.SECONDS)).isTrue();
    assertThat(adapter.getOutputFormat()).isEqualTo(format);
  }

  @Test
  public void shutdown_withPendingFlush_cancelsFlush() throws InterruptedException {
    AtomicBoolean onCodecStartCalled = new AtomicBoolean(false);
    Runnable onCodecStart = () -> onCodecStartCalled.set(true);
    adapter.setOnCodecStart(onCodecStart);
    adapter.flush();
    adapter.shutdown();

    assertThat(waitUntilAllEventsAreExecuted(looper, /* time= */ 5, TimeUnit.SECONDS)).isTrue();
    assertThat(onCodecStartCalled.get()).isFalse();
  }
}
