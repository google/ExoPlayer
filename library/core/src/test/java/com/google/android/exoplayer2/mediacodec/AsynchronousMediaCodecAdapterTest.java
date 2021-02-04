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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.robolectric.Shadows.shadowOf;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.HandlerThread;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.lang.reflect.Constructor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowLooper;

/** Unit tests for {@link AsynchronousMediaCodecAdapter}. */
@RunWith(AndroidJUnit4.class)
public class AsynchronousMediaCodecAdapterTest {
  private AsynchronousMediaCodecAdapter adapter;
  private MediaCodec codec;
  private HandlerThread callbackThread;
  private HandlerThread queueingThread;
  private MediaCodec.BufferInfo bufferInfo;

  @Before
  public void setUp() throws IOException {
    codec = MediaCodec.createByCodecName("h264");
    callbackThread = new HandlerThread("TestCallbackThread");
    queueingThread = new HandlerThread("TestQueueingThread");
    adapter =
        new AsynchronousMediaCodecAdapter.Factory(
                /* callbackThreadSupplier= */ () -> callbackThread,
                /* queueingThreadSupplier= */ () -> queueingThread,
                /* forceQueueingSynchronizationWorkaround= */ false,
                /* synchronizeCodecInteractionsWithQueueing= */ false)
            .createAdapter(codec);
    bufferInfo = new MediaCodec.BufferInfo();
  }

  @After
  public void tearDown() {
    adapter.release();
  }

  @Test
  public void dequeueInputBufferIndex_withoutInputBuffer_returnsTryAgainLater() {
    adapter.configure(
        createMediaFormat("format"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    // After adapter.start(), the ShadowMediaCodec offers one input buffer. We pause the looper so
    // that the buffer is not propagated to the adapter.
    shadowOf(callbackThread.getLooper()).pause();
    adapter.start();

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueInputBufferIndex_withInputBuffer_returnsInputBuffer() {
    adapter.configure(
        createMediaFormat("format"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers input buffer 0. We advance the looper to make sure
    // and messages have been propagated to the adapter.
    shadowOf(callbackThread.getLooper()).idle();

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(0);
  }



  @Test
  public void dequeueInputBufferIndex_withMediaCodecError_throwsException() throws Exception {
    adapter.configure(
        createMediaFormat("format"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    // Pause the looper so that we interact with the adapter from this thread only.
    shadowOf(callbackThread.getLooper()).pause();
    adapter.start();

    // Set an error directly on the adapter (not through the looper).
    adapter.onError(createCodecException());

    assertThrows(IllegalStateException.class, () -> adapter.dequeueInputBufferIndex());
  }

  @Test
  public void dequeueInputBufferIndex_afterShutdown_returnsTryAgainLater() {
    adapter.configure(
        createMediaFormat("format"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers input buffer 0, which is available only if we
    // progress the adapter's looper. We progress the looper so that we call shutdown() on a
    // non-empty adapter.
    shadowOf(callbackThread.getLooper()).idle();

    adapter.release();

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueOutputBufferIndex_withoutOutputBuffer_returnsTryAgainLater() {
    adapter.configure(
        createMediaFormat("format"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);

    adapter.start();
    // After start(), the ShadowMediaCodec offers an output format change. We progress the looper
    // so that the format change is propagated to the adapter.
    shadowOf(callbackThread.getLooper()).idle();

    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    // Assert that output buffer is available.
    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueOutputBufferIndex_withOutputBuffer_returnsOutputBuffer() {
    adapter.configure(
        createMediaFormat("format"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers input buffer 0, which is available only if we
    // progress the adapter's looper.
    ShadowLooper callbackShadowLooper = shadowOf(callbackThread.getLooper());
    callbackShadowLooper.idle();

    int index = adapter.dequeueInputBufferIndex();
    adapter.queueInputBuffer(index, 0, 0, 0, 0);
    // Progress the queueuing looper first so the asynchronous enqueuer submits the input buffer,
    // the ShadowMediaCodec processes the input buffer and produces an output buffer. Then, progress
    // the callback looper so that the available output buffer callback is handled and the output
    // buffer reaches the adapter.
    shadowOf(queueingThread.getLooper()).idle();
    callbackShadowLooper.idle();

    // The ShadowMediaCodec will first offer an output format and then the output buffer.
    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    // Assert it's the ShadowMediaCodec's output format
    assertThat(adapter.getOutputFormat().getByteBuffer("csd-0")).isNotNull();
    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo)).isEqualTo(index);
  }

  @Test
  public void dequeueOutputBufferIndex_withMediaCodecError_throwsException() throws Exception {
    // Pause the looper so that we interact with the adapter from this thread only.
    adapter.configure(
        createMediaFormat("format"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    shadowOf(callbackThread.getLooper()).pause();
    adapter.start();

    // Set an error directly on the adapter.
    adapter.onError(createCodecException());

    assertThrows(IllegalStateException.class, () -> adapter.dequeueOutputBufferIndex(bufferInfo));
  }

  @Test
  public void dequeueOutputBufferIndex_afterShutdown_returnsTryAgainLater() {
    adapter.configure(
        createMediaFormat("format"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers input buffer 0, which is available only if we
    // progress the adapter's looper.
    ShadowLooper shadowLooper = shadowOf(callbackThread.getLooper());
    shadowLooper.idle();

    int index = adapter.dequeueInputBufferIndex();
    adapter.queueInputBuffer(index, 0, 0, 0, 0);
    // Progress the looper so that the ShadowMediaCodec processes the input buffer.
    shadowLooper.idle();
    adapter.release();

    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void getOutputFormat_withoutFormatReceived_throwsException() {
    adapter.configure(
        createMediaFormat("format"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    // After start() the ShadowMediaCodec offers an output format change. Pause the looper so that
    // the format change is not propagated to the adapter.
    shadowOf(callbackThread.getLooper()).pause();
    adapter.start();

    assertThrows(IllegalStateException.class, () -> adapter.getOutputFormat());
  }

  @Test
  public void getOutputFormat_withMultipleFormats_returnsCorrectFormat() {
    adapter.configure(
        createMediaFormat("format"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers an output format, which is available only if we
    // progress the adapter's looper.
    shadowOf(callbackThread.getLooper()).idle();

    // Add another format on the adapter.
    adapter.onOutputFormatChanged(createMediaFormat("format2"));

    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    // The first format is the ShadowMediaCodec's output format.
    assertThat(adapter.getOutputFormat().getByteBuffer("csd-0")).isNotNull();
    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    // The 2nd format is the format we enqueued 'manually' above.
    assertThat(adapter.getOutputFormat().getString("name")).isEqualTo("format2");
    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void getOutputFormat_afterFlush_returnsPreviousFormat() {
    adapter.configure(
        createMediaFormat("format"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers an output format, which is available only if we
    // progress the adapter's looper.
    ShadowLooper shadowLooper = shadowOf(callbackThread.getLooper());
    shadowLooper.idle();

    adapter.dequeueOutputBufferIndex(bufferInfo);
    MediaFormat outputFormat = adapter.getOutputFormat();
    // Flush the adapter and progress the looper so that flush is completed.
    adapter.flush();
    shadowLooper.idle();

    assertThat(adapter.getOutputFormat()).isEqualTo(outputFormat);
  }

  private static MediaFormat createMediaFormat(String name) {
    MediaFormat format = new MediaFormat();
    format.setString("name", name);
    return format;
  }

  /** Reflectively create a {@link MediaCodec.CodecException}. */
  private static MediaCodec.CodecException createCodecException() throws Exception {
    Constructor<MediaCodec.CodecException> constructor =
        MediaCodec.CodecException.class.getDeclaredConstructor(
            Integer.TYPE, Integer.TYPE, String.class);
    return constructor.newInstance(
        /* errorCode= */ 0, /* actionCode= */ 0, /* detailMessage= */ "error from codec");
  }
}
