/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests {@link ReusableBufferedOutputStream}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Config.TARGET_SDK, manifest = Config.NONE)
public final class ReusableBufferedOutputStreamTest {

  private static final byte[] TEST_DATA_1 = "test data 1".getBytes();
  private static final byte[] TEST_DATA_2 = "2 test data".getBytes();

  @Test
  public void testReset() throws Exception {
    ByteArrayOutputStream byteArrayOutputStream1 = new ByteArrayOutputStream(1000);
    ReusableBufferedOutputStream outputStream = new ReusableBufferedOutputStream(
        byteArrayOutputStream1, 1000);
    outputStream.write(TEST_DATA_1);
    outputStream.close();

    ByteArrayOutputStream byteArrayOutputStream2 = new ByteArrayOutputStream(1000);
    outputStream.reset(byteArrayOutputStream2);
    outputStream.write(TEST_DATA_2);
    outputStream.close();

    assertThat(byteArrayOutputStream1.toByteArray()).isEqualTo(TEST_DATA_1);
    assertThat(byteArrayOutputStream2.toByteArray()).isEqualTo(TEST_DATA_2);
  }

}
