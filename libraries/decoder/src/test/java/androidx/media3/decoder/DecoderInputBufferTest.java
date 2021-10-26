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
package androidx.media3.decoder;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link DecoderInputBuffer} */
@RunWith(AndroidJUnit4.class)
public class DecoderInputBufferTest {

  @Test
  public void ensureSpaceForWrite_replacementModeDisabled_doesNothingIfResizeNotNeeded() {
    DecoderInputBuffer buffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    ByteBuffer data = ByteBuffer.allocate(32);
    buffer.data = data;
    buffer.ensureSpaceForWrite(32);
    assertThat(buffer.data).isSameInstanceAs(data);
  }

  @Test
  public void ensureSpaceForWrite_replacementModeDisabled_failsIfResizeNeeded() {
    DecoderInputBuffer buffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    buffer.data = ByteBuffer.allocate(16);
    assertThrows(IllegalStateException.class, () -> buffer.ensureSpaceForWrite(32));
  }

  @Test
  public void ensureSpaceForWrite_usesPaddingSize() {
    DecoderInputBuffer buffer =
        new DecoderInputBuffer(
            DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL, /* paddingSize= */ 16);
    buffer.data = ByteBuffer.allocate(32);
    buffer.ensureSpaceForWrite(32);
    assertThat(buffer.data.capacity()).isEqualTo(32 + 16);
  }

  @Test
  public void ensureSpaceForWrite_usesPosition() {
    DecoderInputBuffer buffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    buffer.data = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7});
    buffer.data.position(4);
    buffer.ensureSpaceForWrite(12);
    // The new capacity should be the current position (4) + the required space (12).
    assertThat(buffer.data.capacity()).isEqualTo(4 + 12);
    // The current position should have been retained.
    assertThat(buffer.data.position()).isEqualTo(4);
    // Data should have been copied up to the current position.
    byte[] expectedData = Arrays.copyOf(new byte[] {0, 1, 2, 3}, 16);
    assertThat(buffer.data.array()).isEqualTo(expectedData);
  }

  @Test
  public void ensureSpaceForWrite_copiesByteOrder() {
    DecoderInputBuffer buffer =
        new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
    buffer.data = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
    buffer.ensureSpaceForWrite(16);
    assertThat(buffer.data.order()).isEqualTo(ByteOrder.LITTLE_ENDIAN);
    buffer.data = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
    buffer.ensureSpaceForWrite(16);
    assertThat(buffer.data.order()).isEqualTo(ByteOrder.BIG_ENDIAN);
  }
}
