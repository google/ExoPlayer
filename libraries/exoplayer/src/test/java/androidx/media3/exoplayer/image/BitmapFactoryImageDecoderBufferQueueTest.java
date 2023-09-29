/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.image;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link BitmapFactoryImageDecoder} ensuring the buffer queue system operates
 * correctly.
 */
@RunWith(AndroidJUnit4.class)
public class BitmapFactoryImageDecoderBufferQueueTest {

  private static final long TIMEOUT_MS = 5 * C.MICROS_PER_SECOND;

  private BitmapFactoryImageDecoder fakeImageDecoder;
  private Bitmap decodedBitmap1;
  private Bitmap decodedBitmap2;

  public int decodeCallCount;

  @Before
  public void setUp() throws Exception {
    decodeCallCount = 0;
    decodedBitmap1 = Bitmap.createBitmap(/* width= */ 1, /* height= */ 1, Bitmap.Config.ARGB_8888);
    decodedBitmap2 = Bitmap.createBitmap(/* width= */ 2, /* height= */ 2, Bitmap.Config.ARGB_8888);
    fakeImageDecoder =
        new BitmapFactoryImageDecoder.Factory(
                (data, length) -> ++decodeCallCount == 1 ? decodedBitmap1 : decodedBitmap2)
            .createImageDecoder();
  }

  @After
  public void tearDown() throws Exception {
    fakeImageDecoder.release();
  }

  @Test
  public void decodeIndirectly_returnBitmapAtTheCorrectTimestamp() throws Exception {
    DecoderInputBuffer inputBuffer = checkNotNull(fakeImageDecoder.dequeueInputBuffer());
    inputBuffer.timeUs = 2 * C.MILLIS_PER_SECOND;
    inputBuffer.data = ByteBuffer.wrap(new byte[1]);
    fakeImageDecoder.queueInputBuffer(inputBuffer);
    ImageOutputBuffer outputBuffer = getDecodedOutput();

    assertThat(outputBuffer.timeUs).isEqualTo(inputBuffer.timeUs);
    assertThat(outputBuffer.bitmap).isEqualTo(decodedBitmap1);
  }

  @Test
  public void decodeIndirectlyTwice_returnsSecondBitmapAtTheCorrectTimestamp() throws Exception {
    DecoderInputBuffer inputBuffer1 = checkNotNull(fakeImageDecoder.dequeueInputBuffer());
    inputBuffer1.timeUs = 0;
    inputBuffer1.data = ByteBuffer.wrap(new byte[1]);
    fakeImageDecoder.queueInputBuffer(inputBuffer1);
    checkNotNull(getDecodedOutput()).release();
    DecoderInputBuffer inputBuffer2 = checkNotNull(fakeImageDecoder.dequeueInputBuffer());
    inputBuffer2.timeUs = C.MICROS_PER_SECOND;
    inputBuffer2.data = ByteBuffer.wrap(new byte[1]);
    fakeImageDecoder.queueInputBuffer(inputBuffer2);

    ImageOutputBuffer outputBuffer2 = checkNotNull(getDecodedOutput());

    assertThat(outputBuffer2.timeUs).isEqualTo(inputBuffer2.timeUs);
    assertThat(outputBuffer2.bitmap).isEqualTo(decodedBitmap2);
  }

  // Polling to see whether the output is available yet since the decode thread doesn't finish
  // decoding immediately.
  private ImageOutputBuffer getDecodedOutput() throws Exception {
    @Nullable ImageOutputBuffer outputBuffer;
    // Use System.currentTimeMillis() to calculate the wait duration more accurately.
    long deadlineMs = System.currentTimeMillis() + TIMEOUT_MS;
    long remainingMs = TIMEOUT_MS;
    while (remainingMs > 0) {
      outputBuffer = fakeImageDecoder.dequeueOutputBuffer();
      if (outputBuffer != null) {
        return outputBuffer;
      }
      Thread.sleep(/* millis= */ 5);
      remainingMs = deadlineMs - System.currentTimeMillis();
    }
    throw new TimeoutException();
  }
}
