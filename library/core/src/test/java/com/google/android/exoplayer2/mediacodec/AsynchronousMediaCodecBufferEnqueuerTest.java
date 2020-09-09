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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doAnswer;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.HandlerThread;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.util.ConditionVariable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link AsynchronousMediaCodecBufferEnqueuer}. */
@RunWith(AndroidJUnit4.class)
public class AsynchronousMediaCodecBufferEnqueuerTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  private MediaCodec codec;
  private AsynchronousMediaCodecBufferEnqueuer enqueuer;
  private TestHandlerThread handlerThread;
  @Mock private ConditionVariable mockConditionVariable;

  @Before
  public void setUp() throws IOException {
    codec = MediaCodec.createByCodecName("h264");
    codec.configure(new MediaFormat(), /* surface= */ null, /* crypto= */ null, /* flags= */ 0);
    codec.start();
    handlerThread = new TestHandlerThread("TestHandlerThread");
    enqueuer =
        new AsynchronousMediaCodecBufferEnqueuer(codec, handlerThread, mockConditionVariable);
  }

  @After
  public void tearDown() {
    enqueuer.shutdown();
    codec.stop();
    codec.release();
    assertThat(TestHandlerThread.INSTANCES_STARTED.get()).isEqualTo(0);
  }

  @Test
  public void queueInputBuffer_withPendingCryptoExceptionSet_throwsCryptoException() {
    enqueuer.setPendingRuntimeException(
        new MediaCodec.CryptoException(/* errorCode= */ 0, /* detailMessage= */ null));
    enqueuer.start();

    assertThrows(
        MediaCodec.CryptoException.class,
        () ->
            enqueuer.queueInputBuffer(
                /* index= */ 0,
                /* offset= */ 0,
                /* size= */ 0,
                /* presentationTimeUs= */ 0,
                /* flags= */ 0));
  }

  @Test
  public void queueInputBuffer_withPendingIllegalStateExceptionSet_throwsIllegalStateException() {
    enqueuer.start();
    enqueuer.setPendingRuntimeException(new IllegalStateException());
    assertThrows(
        IllegalStateException.class,
        () ->
            enqueuer.queueInputBuffer(
                /* index= */ 0,
                /* offset= */ 0,
                /* size= */ 0,
                /* presentationTimeUs= */ 0,
                /* flags= */ 0));
  }

  @Test
  public void queueSecureInputBuffer_withPendingCryptoException_throwsCryptoException() {
    enqueuer.setPendingRuntimeException(
        new MediaCodec.CryptoException(/* errorCode= */ 0, /* detailMessage= */ null));
    enqueuer.start();
    CryptoInfo info = createCryptoInfo();

    assertThrows(
        MediaCodec.CryptoException.class,
        () ->
            enqueuer.queueSecureInputBuffer(
                /* index= */ 0,
                /* offset= */ 0,
                /* info= */ info,
                /* presentationTimeUs= */ 0,
                /* flags= */ 0));
  }

  @Test
  public void queueSecureInputBuffer_codecThrewIllegalStateException_throwsIllegalStateException() {
    enqueuer.setPendingRuntimeException(new IllegalStateException());
    enqueuer.start();
    CryptoInfo info = createCryptoInfo();

    assertThrows(
        IllegalStateException.class,
        () ->
            enqueuer.queueSecureInputBuffer(
                /* index= */ 0,
                /* offset= */ 0,
                /* info= */ info,
                /* presentationTimeUs= */ 0,
                /* flags= */ 0));
  }

  @Test
  public void flush_withoutStart_works() {
    enqueuer.flush();
  }

  @Test
  public void flush_onInterruptedException_throwsIllegalStateException()
      throws InterruptedException {
    doAnswer(
            invocation -> {
              throw new InterruptedException();
            })
        .doNothing()
        .when(mockConditionVariable)
        .block();

    enqueuer.start();

    assertThrows(IllegalStateException.class, () -> enqueuer.flush());
  }

  @Test
  public void flush_multipleTimes_works() {
    enqueuer.start();

    enqueuer.flush();
    enqueuer.flush();
  }

  @Test
  public void shutdown_withoutStart_works() {
    enqueuer.shutdown();
  }

  @Test
  public void shutdown_multipleTimes_works() {
    enqueuer.start();

    enqueuer.shutdown();
    enqueuer.shutdown();
  }

  @Test
  public void shutdown_onInterruptedException_throwsIllegalStateException()
      throws InterruptedException {
    doAnswer(
            invocation -> {
              throw new InterruptedException();
            })
        .doNothing()
        .when(mockConditionVariable)
        .block();

    enqueuer.start();

    assertThrows(IllegalStateException.class, () -> enqueuer.shutdown());
  }

  private static class TestHandlerThread extends HandlerThread {
    private static final AtomicLong INSTANCES_STARTED = new AtomicLong(0);

    TestHandlerThread(String name) {
      super(name);
    }

    @Override
    public synchronized void start() {
      super.start();
      INSTANCES_STARTED.incrementAndGet();
    }

    @Override
    public boolean quit() {
      boolean quit = super.quit();
      if (quit) {
        INSTANCES_STARTED.decrementAndGet();
      }
      return quit;
    }
  }

  private static CryptoInfo createCryptoInfo() {
    CryptoInfo info = new CryptoInfo();
    int numSubSamples = 5;
    int[] numBytesOfClearData = new int[] {0, 1, 2, 3};
    int[] numBytesOfEncryptedData = new int[] {4, 5, 6, 7};
    byte[] key = new byte[] {0, 1, 2, 3};
    byte[] iv = new byte[] {4, 5, 6, 7};
    @C.CryptoMode int mode = C.CRYPTO_MODE_AES_CBC;
    int encryptedBlocks = 16;
    int clearBlocks = 8;
    info.set(
        numSubSamples,
        numBytesOfClearData,
        numBytesOfEncryptedData,
        key,
        iv,
        mode,
        encryptedBlocks,
        clearBlocks);
    return info;
  }
}
