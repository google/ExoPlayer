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

import static com.google.android.exoplayer2.testutil.TestUtil.assertBufferInfosEqual;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Shadows;
import org.robolectric.annotation.LooperMode;

/** Unit tests for {@link AsynchronousMediaCodecAdapter}. */
@LooperMode(LEGACY)
@RunWith(AndroidJUnit4.class)
public class AsynchronousMediaCodecAdapterTest {
  private AsynchronousMediaCodecAdapter adapter;
  private MediaCodec codec;
  private HandlerThread handlerThread;
  private Looper looper;
  private MediaCodec.BufferInfo bufferInfo;

  @Before
  public void setUp() throws IOException {
    handlerThread = new HandlerThread("TestHandler");
    handlerThread.start();
    looper = handlerThread.getLooper();
    codec = MediaCodec.createByCodecName("h264");
    adapter = new AsynchronousMediaCodecAdapter(codec, looper);
    adapter.setCodecStartRunnable(() -> {});
    bufferInfo = new MediaCodec.BufferInfo();
  }

  @After
  public void tearDown() {
    adapter.shutdown();
    handlerThread.quit();
  }

  @Test
  public void dequeueInputBufferIndex_withoutInputBuffer_returnsTryAgainLater() {
    adapter.start();

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueInputBufferIndex_withInputBuffer_returnsInputBuffer() {
    adapter.start();
    adapter.getMediaCodecCallback().onInputBufferAvailable(codec, /* index=*/ 0);

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(0);
  }

  @Test
  public void dequeueInputBufferIndex_whileFlushing_returnsTryAgainLater() {
    adapter.start();
    adapter.getMediaCodecCallback().onInputBufferAvailable(codec, /* index=*/ 0);
    adapter.flush();
    adapter.getMediaCodecCallback().onInputBufferAvailable(codec, /* index=*/ 1);

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueInputBufferIndex_afterFlushCompletes_returnsNextInputBuffer() {
    adapter.start();
    Handler handler = new Handler(looper);
    handler.post(
        () -> adapter.getMediaCodecCallback().onInputBufferAvailable(codec, /* index=*/ 0));
    adapter.flush(); // enqueues a flush event on the looper
    handler.post(
        () -> adapter.getMediaCodecCallback().onInputBufferAvailable(codec, /* index=*/ 1));

    // Wait until all tasks have been handled.
    Shadows.shadowOf(looper).idle();
    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(1);
  }

  @Test
  public void dequeueInputBufferIndex_afterFlushCompletesWithError_throwsException() {
    AtomicInteger calls = new AtomicInteger(0);
    adapter.setCodecStartRunnable(
        () -> {
          if (calls.incrementAndGet() == 2) {
            throw new IllegalStateException();
          }
        });
    adapter.start();
    adapter.flush();

    // Wait until all tasks have been handled.
    Shadows.shadowOf(looper).idle();
    assertThrows(
        IllegalStateException.class,
        () -> {
          adapter.dequeueInputBufferIndex();
        });
  }

  @Test
  public void dequeueOutputBufferIndex_withoutOutputBuffer_returnsTryAgainLater() {
    adapter.start();

    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueOutputBufferIndex_withOutputBuffer_returnsOutputBuffer() {
    adapter.start();
    MediaCodec.BufferInfo outBufferInfo = new MediaCodec.BufferInfo();
    outBufferInfo.presentationTimeUs = 10;
    adapter.getMediaCodecCallback().onOutputBufferAvailable(codec, /* index=*/ 0, outBufferInfo);

    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo)).isEqualTo(0);
    assertBufferInfosEqual(bufferInfo, outBufferInfo);
  }

  @Test
  public void dequeueOutputBufferIndex_whileFlushing_returnsTryAgainLater() {
    adapter.start();
    adapter.getMediaCodecCallback().onOutputBufferAvailable(codec, /* index=*/ 0, bufferInfo);
    adapter.flush();
    adapter.getMediaCodecCallback().onOutputBufferAvailable(codec, /* index=*/ 1, bufferInfo);

    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueOutputBufferIndex_afterFlushCompletes_returnsNextOutputBuffer() {
    adapter.start();
    Handler handler = new Handler(looper);
    MediaCodec.BufferInfo info0 = new MediaCodec.BufferInfo();
    handler.post(
        () -> adapter.getMediaCodecCallback().onOutputBufferAvailable(codec, /* index=*/ 0, info0));
    adapter.flush(); // enqueues a flush event on the looper
    MediaCodec.BufferInfo info1 = new MediaCodec.BufferInfo();
    info1.presentationTimeUs = 1;
    handler.post(
        () -> adapter.getMediaCodecCallback().onOutputBufferAvailable(codec, /* index=*/ 1, info1));

    // Wait until all tasks have been handled.
    Shadows.shadowOf(looper).idle();
    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo)).isEqualTo(1);
    assertBufferInfosEqual(info1, bufferInfo);
  }

  @Test
  public void dequeueOutputBufferIndex_afterFlushCompletesWithError_throwsException() {
    AtomicInteger calls = new AtomicInteger(0);
    adapter.setCodecStartRunnable(
        () -> {
          if (calls.incrementAndGet() == 2) {
            throw new RuntimeException("codec#start() exception");
          }
        });
    adapter.start();
    adapter.flush();

    // Wait until all tasks have been handled.
    Shadows.shadowOf(looper).idle();
    assertThrows(IllegalStateException.class, () -> adapter.dequeueOutputBufferIndex(bufferInfo));
  }

  @Test
  public void getOutputFormat_withMultipleFormats_returnsFormatsInCorrectOrder() {
    adapter.start();
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
  public void getOutputFormat_afterFlush_returnsPreviousFormat() {
    adapter.start();
    MediaFormat format = new MediaFormat();
    adapter.getMediaCodecCallback().onOutputFormatChanged(codec, format);
    adapter.dequeueOutputBufferIndex(bufferInfo);
    adapter.flush();

    // Wait until all tasks have been handled.
    Shadows.shadowOf(looper).idle();
    assertThat(adapter.getOutputFormat()).isEqualTo(format);
  }

  @Test
  public void shutdown_withPendingFlush_cancelsFlush() {
    AtomicInteger onCodecStartCalled = new AtomicInteger(0);
    adapter.setCodecStartRunnable(() -> onCodecStartCalled.incrementAndGet());
    adapter.start();
    adapter.flush();
    adapter.shutdown();

    // Wait until all tasks have been handled.
    Shadows.shadowOf(looper).idle();
    assertThat(onCodecStartCalled.get()).isEqualTo(1);
  }
}
