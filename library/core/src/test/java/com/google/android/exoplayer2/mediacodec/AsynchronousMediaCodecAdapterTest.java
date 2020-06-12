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
import static org.robolectric.annotation.LooperMode.Mode.PAUSED;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.HandlerThread;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.LooperMode;

/** Unit tests for {@link AsynchronousMediaCodecAdapter}. */
@LooperMode(PAUSED)
@RunWith(AndroidJUnit4.class)
public class AsynchronousMediaCodecAdapterTest {
  private AsynchronousMediaCodecAdapter adapter;
  private MediaCodec codec;
  private TestHandlerThread handlerThread;
  private MediaCodec.BufferInfo bufferInfo;

  @Before
  public void setUp() throws IOException {
    codec = MediaCodec.createByCodecName("h264");
    handlerThread = new TestHandlerThread("TestHandlerThread");
    adapter =
        new AsynchronousMediaCodecAdapter(
            codec,
            /* enableAsynchronousQueueing= */ false,
            /* trackType= */ C.TRACK_TYPE_VIDEO,
            handlerThread);
    bufferInfo = new MediaCodec.BufferInfo();
  }

  @After
  public void tearDown() {
    adapter.shutdown();

    assertThat(handlerThread.hasQuit()).isTrue();
  }

  @Test
  public void dequeueInputBufferIndex_withoutInputBuffer_returnsTryAgainLater() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();

    // After start(), the ShadowMediaCodec offers one input buffer, which is available only if we
    // progress the adapter's looper. We don't progress the looper so that the buffer is not
    // available.

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueInputBufferIndex_withInputBuffer_returnsInputBuffer() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);

    adapter.start();
    // After start(), the ShadowMediaCodec offers input buffer 0, which is available only if we
    // progress the adapter's looper.
    shadowOf(adapter.getLooper()).idle();

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(0);
  }

  @Test
  public void dequeueInputBufferIndex_withPendingFlush_returnsTryAgainLater() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers input buffer 0, which is available only if we
    // progress the adapter's looper.
    shadowOf(adapter.getLooper()).idle();

    // Flush enqueues a task in the looper, but we won't progress the looper to leave flush()
    // in a pending state.
    adapter.flush();

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueInputBufferIndex_withFlushCompletedAndInputBuffer_returnsInputBuffer() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers input buffer 0, which is available only if we
    // progress the adapter's looper.
    shadowOf(adapter.getLooper()).idle();

    adapter.flush();
    // Progress the looper to complete flush(): the adapter should call codec.start(), triggering
    // the ShadowMediaCodec to offer input buffer 0.
    shadowOf(adapter.getLooper()).idle();

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(0);
  }

  @Test
  public void dequeueInputBufferIndex_withMediaCodecError_throwsException() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();

    // Set an error directly on the adapter (not through the looper).
    adapter.onMediaCodecError(new IllegalStateException("error from codec"));

    assertThrows(IllegalStateException.class, () -> adapter.dequeueInputBufferIndex());
  }

  @Test
  public void dequeueInputBufferIndex_afterShutdown_returnsTryAgainLater() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers input buffer 0, which is available only if we
    // progress the adapter's looper.
    shadowOf(adapter.getLooper()).idle();

    adapter.shutdown();

    assertThat(adapter.dequeueInputBufferIndex()).isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueOutputBufferIndex_withoutOutputBuffer_returnsTryAgainLater() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);

    adapter.start();
    // After start(), the ShadowMediaCodec offers an output format change.
    shadowOf(adapter.getLooper()).idle();

    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    // Assert that output buffer is available.
    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueOutputBufferIndex_withOutputBuffer_returnsOutputBuffer() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers input buffer 0, which is available only if we
    // progress the adapter's looper.
    shadowOf(adapter.getLooper()).idle();

    int index = adapter.dequeueInputBufferIndex();
    adapter.queueInputBuffer(index, 0, 0, 0, 0);
    // Progress the looper so that the ShadowMediaCodec processes the input buffer.
    shadowOf(adapter.getLooper()).idle();

    // The ShadowMediaCodec will first offer an output format and then the output buffer.
    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    // Assert it's the ShadowMediaCodec's output format
    assertThat(adapter.getOutputFormat().getByteBuffer("csd-0")).isNotNull();
    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo)).isEqualTo(index);
  }

  @Test
  public void dequeueOutputBufferIndex_withPendingFlush_returnsTryAgainLater() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers input buffer 0, which is available only if we
    // progress the adapter's looper.
    shadowOf(adapter.getLooper()).idle();

    // Flush enqueues a task in the looper, but we won't progress the looper to leave flush()
    // in a pending state.
    adapter.flush();

    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeueOutputBufferIndex_withMediaCodecError_throwsException() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();

    // Set an error directly on the adapter.
    adapter.onMediaCodecError(new IllegalStateException("error from codec"));

    assertThrows(IllegalStateException.class, () -> adapter.dequeueOutputBufferIndex(bufferInfo));
  }

  @Test
  public void dequeueOutputBufferIndex_afterShutdown_returnsTryAgainLater() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers input buffer 0, which is available only if we
    // progress the adapter's looper.
    shadowOf(adapter.getLooper()).idle();

    int index = adapter.dequeueInputBufferIndex();
    adapter.queueInputBuffer(index, 0, 0, 0, 0);
    // Progress the looper so that the ShadowMediaCodec processes the input buffer.
    shadowOf(adapter.getLooper()).idle();
    adapter.shutdown();

    assertThat(adapter.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void getOutputFormat_withoutFormatReceived_throwsException() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();

    assertThrows(IllegalStateException.class, () -> adapter.getOutputFormat());
  }

  @Test
  public void getOutputFormat_withMultipleFormats_returnsCorrectFormat() {
    adapter.configure(
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers an output format, which is available only if we
    // progress the adapter's looper.
    shadowOf(adapter.getLooper()).idle();

    // Add another format directly on the adapter.
    adapter.onOutputFormatChanged(codec, createMediaFormat("format2"));

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
        createMediaFormat("foo"), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    adapter.start();
    // After start(), the ShadowMediaCodec offers an output format, which is available only if we
    // progress the adapter's looper.
    shadowOf(adapter.getLooper()).idle();

    adapter.dequeueOutputBufferIndex(bufferInfo);
    MediaFormat outputFormat = adapter.getOutputFormat();
    // Flush the adapter and progress the looper so that flush is completed.
    adapter.flush();
    shadowOf(adapter.getLooper()).idle();

    assertThat(adapter.getOutputFormat()).isEqualTo(outputFormat);
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
