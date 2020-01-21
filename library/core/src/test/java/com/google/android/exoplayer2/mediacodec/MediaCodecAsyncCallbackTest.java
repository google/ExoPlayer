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
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaFormat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link MediaCodecAsyncCallback}. */
@RunWith(AndroidJUnit4.class)
public class MediaCodecAsyncCallbackTest {

  private MediaCodecAsyncCallback mediaCodecAsyncCallback;
  private MediaCodec codec;

  @Before
  public void setUp() throws IOException {
    mediaCodecAsyncCallback = new MediaCodecAsyncCallback();
    codec = MediaCodec.createByCodecName("h264");
  }

  @Test
  public void dequeInputBufferIndex_afterCreation_returnsTryAgain() {
    assertThat(mediaCodecAsyncCallback.dequeueInputBufferIndex())
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeInputBufferIndex_returnsEnqueuedBuffers() {
    // Send two input buffers to the mediaCodecAsyncCallback.
    mediaCodecAsyncCallback.onInputBufferAvailable(codec, 0);
    mediaCodecAsyncCallback.onInputBufferAvailable(codec, 1);

    assertThat(mediaCodecAsyncCallback.dequeueInputBufferIndex()).isEqualTo(0);
    assertThat(mediaCodecAsyncCallback.dequeueInputBufferIndex()).isEqualTo(1);
    assertThat(mediaCodecAsyncCallback.dequeueInputBufferIndex())
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeInputBufferIndex_afterFlush_returnsTryAgain() {
    // Send two input buffers to the mediaCodecAsyncCallback and then flush().
    mediaCodecAsyncCallback.onInputBufferAvailable(codec, 0);
    mediaCodecAsyncCallback.onInputBufferAvailable(codec, 1);
    mediaCodecAsyncCallback.flush();

    assertThat(mediaCodecAsyncCallback.dequeueInputBufferIndex())
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeInputBufferIndex_afterFlushAndNewInputBuffer_returnsEnqueuedBuffer() {
    // Send two input buffers to the mediaCodecAsyncCallback, then flush(), then send
    // another input buffer.
    mediaCodecAsyncCallback.onInputBufferAvailable(codec, 0);
    mediaCodecAsyncCallback.onInputBufferAvailable(codec, 1);
    mediaCodecAsyncCallback.flush();
    mediaCodecAsyncCallback.onInputBufferAvailable(codec, 2);

    assertThat(mediaCodecAsyncCallback.dequeueInputBufferIndex()).isEqualTo(2);
  }

  @Test
  public void dequeOutputBufferIndex_afterCreation_returnsTryAgain() {
    MediaCodec.BufferInfo outBufferInfo = new MediaCodec.BufferInfo();
    assertThat(mediaCodecAsyncCallback.dequeueOutputBufferIndex(outBufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeOutputBufferIndex_returnsEnqueuedBuffers() {
    // Send two output buffers to the mediaCodecAsyncCallback.
    MediaCodec.BufferInfo bufferInfo1 = new MediaCodec.BufferInfo();
    mediaCodecAsyncCallback.onOutputBufferAvailable(codec, 0, bufferInfo1);

    MediaCodec.BufferInfo bufferInfo2 = new MediaCodec.BufferInfo();
    bufferInfo2.set(1, 1, 1, 1);
    mediaCodecAsyncCallback.onOutputBufferAvailable(codec, 1, bufferInfo2);

    MediaCodec.BufferInfo outBufferInfo = new MediaCodec.BufferInfo();

    assertThat(mediaCodecAsyncCallback.dequeueOutputBufferIndex(outBufferInfo)).isEqualTo(0);
    assertBufferInfosEqual(bufferInfo1, outBufferInfo);
    assertThat(mediaCodecAsyncCallback.dequeueOutputBufferIndex(outBufferInfo)).isEqualTo(1);
    assertBufferInfosEqual(bufferInfo2, outBufferInfo);
    assertThat(mediaCodecAsyncCallback.dequeueOutputBufferIndex(outBufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeOutputBufferIndex_afterFlush_returnsTryAgain() {
    // Send two output buffers to the mediaCodecAsyncCallback and then flush().
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    mediaCodecAsyncCallback.onOutputBufferAvailable(codec, 0, bufferInfo);
    mediaCodecAsyncCallback.onOutputBufferAvailable(codec, 1, bufferInfo);
    mediaCodecAsyncCallback.flush();
    MediaCodec.BufferInfo outBufferInfo = new MediaCodec.BufferInfo();

    assertThat(mediaCodecAsyncCallback.dequeueOutputBufferIndex(outBufferInfo))
        .isEqualTo(MediaCodec.INFO_TRY_AGAIN_LATER);
  }

  @Test
  public void dequeOutputBufferIndex_afterFlushAndNewOutputBuffers_returnsEnqueueBuffer() {
    // Send two output buffers to the mediaCodecAsyncCallback, then flush(), then send
    // another output buffer.
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    mediaCodecAsyncCallback.onOutputBufferAvailable(codec, 0, bufferInfo);
    mediaCodecAsyncCallback.onOutputBufferAvailable(codec, 1, bufferInfo);
    mediaCodecAsyncCallback.flush();
    mediaCodecAsyncCallback.onOutputBufferAvailable(codec, 2, bufferInfo);
    MediaCodec.BufferInfo outBufferInfo = new MediaCodec.BufferInfo();

    assertThat(mediaCodecAsyncCallback.dequeueOutputBufferIndex(outBufferInfo)).isEqualTo(2);
  }

  @Test
  public void getOutputFormat_onNewInstance_raisesException() {
    try {
      mediaCodecAsyncCallback.getOutputFormat();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void getOutputFormat_afterOnOutputFormatCalled_returnsFormat() {
    MediaFormat format = new MediaFormat();
    mediaCodecAsyncCallback.onOutputFormatChanged(codec, format);
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    assertThat(mediaCodecAsyncCallback.dequeueOutputBufferIndex(bufferInfo))
        .isEqualTo(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED);
    assertThat(mediaCodecAsyncCallback.getOutputFormat()).isEqualTo(format);
  }

  @Test
  public void getOutputFormat_afterFlush_raisesCurrentFormat() {
    MediaFormat format = new MediaFormat();
    mediaCodecAsyncCallback.onOutputFormatChanged(codec, format);
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    mediaCodecAsyncCallback.dequeueOutputBufferIndex(bufferInfo);
    mediaCodecAsyncCallback.flush();

    assertThat(mediaCodecAsyncCallback.getOutputFormat()).isEqualTo(format);
  }

  @Test
  public void maybeThrowExoPlaybackException_withoutErrorFromCodec_doesNotThrow() {
    mediaCodecAsyncCallback.maybeThrowMediaCodecException();
  }

  @Test
  public void maybeThrowExoPlaybackException_withErrorFromCodec_Throws() {
    IllegalStateException exception = new IllegalStateException();
    mediaCodecAsyncCallback.onMediaCodecError(exception);

    try {
      mediaCodecAsyncCallback.maybeThrowMediaCodecException();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void maybeThrowExoPlaybackException_doesNotThrowTwice() {
    IllegalStateException exception = new IllegalStateException();
    mediaCodecAsyncCallback.onMediaCodecError(exception);

    try {
      mediaCodecAsyncCallback.maybeThrowMediaCodecException();
      fail();
    } catch (IllegalStateException expected) {
    }

    mediaCodecAsyncCallback.maybeThrowMediaCodecException();
  }

  @Test
  public void maybeThrowExoPlaybackException_afterFlush_doesNotThrow() {
    IllegalStateException exception = new IllegalStateException();
    mediaCodecAsyncCallback.onMediaCodecError(exception);
    mediaCodecAsyncCallback.flush();

    mediaCodecAsyncCallback.maybeThrowMediaCodecException();
  }
}
