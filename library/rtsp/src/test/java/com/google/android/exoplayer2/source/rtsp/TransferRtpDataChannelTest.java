/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source.rtsp;

import static com.google.android.exoplayer2.testutil.TestUtil.buildTestData;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.primitives.Bytes;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link TransferRtpDataChannel}. */
@RunWith(AndroidJUnit4.class)
public class TransferRtpDataChannelTest {

  @Test
  public void read_withLargeEnoughBuffer_reads() throws Exception {
    byte[] randomBytes = buildTestData(20);
    byte[] buffer = new byte[40];
    TransferRtpDataChannel transferRtpDataChannel = new TransferRtpDataChannel();
    transferRtpDataChannel.write(randomBytes);

    transferRtpDataChannel.read(buffer, /* offset= */ 0, buffer.length);

    assertThat(Arrays.copyOfRange(buffer, /* from= */ 0, /* to= */ 20)).isEqualTo(randomBytes);
  }

  @Test
  public void read_withSmallBufferEnoughBuffer_readsThreeTimes() throws Exception {
    byte[] randomBytes = buildTestData(20);
    byte[] buffer = new byte[8];
    TransferRtpDataChannel transferRtpDataChannel = new TransferRtpDataChannel();
    transferRtpDataChannel.write(randomBytes);

    transferRtpDataChannel.read(buffer, /* offset= */ 0, buffer.length);
    assertThat(buffer).isEqualTo(Arrays.copyOfRange(randomBytes, /* from= */ 0, /* to= */ 8));
    transferRtpDataChannel.read(buffer, /* offset= */ 0, buffer.length);
    assertThat(buffer).isEqualTo(Arrays.copyOfRange(randomBytes, /* from= */ 8, /* to= */ 16));
    transferRtpDataChannel.read(buffer, /* offset= */ 0, /* length= */ 4);
    assertThat(Arrays.copyOfRange(buffer, /* from= */ 0, /* to= */ 4))
        .isEqualTo(Arrays.copyOfRange(randomBytes, /* from= */ 16, /* to= */ 20));
  }

  @Test
  public void read_withSmallBuffer_reads() throws Exception {
    byte[] randomBytes = buildTestData(40);
    byte[] buffer = new byte[20];
    TransferRtpDataChannel transferRtpDataChannel = new TransferRtpDataChannel();
    transferRtpDataChannel.write(randomBytes);

    transferRtpDataChannel.read(buffer, /* offset= */ 0, buffer.length);
    assertThat(buffer).isEqualTo(Arrays.copyOfRange(randomBytes, /* from= */ 0, /* to= */ 20));

    transferRtpDataChannel.read(buffer, /* offset= */ 0, buffer.length);
    assertThat(buffer).isEqualTo(Arrays.copyOfRange(randomBytes, /* from= */ 20, /* to= */ 40));
  }

  @Test
  public void read_withSmallAndModerateBufferAndSubsequentProducerWrite_reads() throws Exception {
    byte[] randomBytes1 = buildTestData(40);
    byte[] randomBytes2 = buildTestData(40);
    byte[] smallBuffer = new byte[20];
    byte[] bigBuffer = new byte[40];
    TransferRtpDataChannel transferRtpDataChannel = new TransferRtpDataChannel();
    transferRtpDataChannel.write(randomBytes1);

    transferRtpDataChannel.read(smallBuffer, /* offset= */ 0, smallBuffer.length);
    assertThat(smallBuffer)
        .isEqualTo(Arrays.copyOfRange(randomBytes1, /* from= */ 0, /* to= */ 20));

    transferRtpDataChannel.write(randomBytes2);

    // Read the remaining 20 bytes in randomBytes1, and 20 bytes from randomBytes2.
    transferRtpDataChannel.read(bigBuffer, /* offset= */ 0, bigBuffer.length);
    assertThat(bigBuffer)
        .isEqualTo(
            Bytes.concat(
                Arrays.copyOfRange(randomBytes1, /* from= */ 20, /* to= */ 40),
                Arrays.copyOfRange(randomBytes2, /* from= */ 0, /* to= */ 20)));

    // Read the remaining 20 bytes in randomBytes2.
    transferRtpDataChannel.read(smallBuffer, /* offset= */ 0, smallBuffer.length);
    assertThat(smallBuffer)
        .isEqualTo(Arrays.copyOfRange(randomBytes2, /* from= */ 20, /* to= */ 40));
  }

  @Test
  public void read_withSmallAndBigBufferWithPartialReadAndSubsequentProducerWrite_reads()
      throws Exception {
    byte[] randomBytes1 = buildTestData(40);
    byte[] randomBytes2 = buildTestData(40);
    byte[] smallBuffer = new byte[30];
    byte[] bigBuffer = new byte[30];
    TransferRtpDataChannel transferRtpDataChannel = new TransferRtpDataChannel();
    transferRtpDataChannel.write(randomBytes1);

    transferRtpDataChannel.read(smallBuffer, /* offset= */ 0, smallBuffer.length);
    assertThat(smallBuffer)
        .isEqualTo(Arrays.copyOfRange(randomBytes1, /* from= */ 0, /* to= */ 30));

    transferRtpDataChannel.write(randomBytes2);

    // Read 30 bytes to big buffer.
    transferRtpDataChannel.read(bigBuffer, /* offset= */ 0, bigBuffer.length);
    assertThat(bigBuffer)
        .isEqualTo(
            Bytes.concat(
                Arrays.copyOfRange(randomBytes1, /* from= */ 30, /* to= */ 40),
                Arrays.copyOfRange(randomBytes1, /* from= */ 0, /* to= */ 20)));

    // Read the remaining 20 bytes to big buffer.
    transferRtpDataChannel.read(bigBuffer, /* offset= */ 0, /* length= */ 20);
    assertThat(Arrays.copyOfRange(bigBuffer, /* from= */ 0, /* to= */ 20))
        .isEqualTo(Arrays.copyOfRange(randomBytes2, /* from= */ 20, /* to= */ 40));
  }

  @Test
  public void read_withSmallAndBigBufferAndSubsequentProducerWrite_reads() throws Exception {
    byte[] randomBytes1 = buildTestData(40);
    byte[] randomBytes2 = buildTestData(40);
    byte[] smallBuffer = new byte[20];
    byte[] bigBuffer = new byte[70];
    TransferRtpDataChannel transferRtpDataChannel = new TransferRtpDataChannel();
    transferRtpDataChannel.write(randomBytes1);

    transferRtpDataChannel.read(smallBuffer, /* offset= */ 0, smallBuffer.length);
    assertThat(smallBuffer)
        .isEqualTo(Arrays.copyOfRange(randomBytes1, /* from= */ 0, /* to= */ 20));

    transferRtpDataChannel.write(randomBytes2);

    transferRtpDataChannel.read(bigBuffer, /* offset= */ 0, bigBuffer.length);
    assertThat(Arrays.copyOfRange(bigBuffer, /* from= */ 0, /* to= */ 60))
        .isEqualTo(
            Bytes.concat(
                Arrays.copyOfRange(randomBytes1, /* from= */ 20, /* to= */ 40), randomBytes2));
  }
}
