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
package com.google.android.exoplayer2.metadata;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link SimpleMetadataDecoder}. */
@RunWith(AndroidJUnit4.class)
public class SimpleMetadataDecoderTest {

  @Test
  public void decode_nullDataInputBuffer_throwsNullPointerException() {
    TestSimpleMetadataDecoder decoder = new TestSimpleMetadataDecoder();
    MetadataInputBuffer nullDataInputBuffer = new MetadataInputBuffer();
    nullDataInputBuffer.data = null;

    assertThrows(NullPointerException.class, () -> decoder.decode(nullDataInputBuffer));
    assertThat(decoder.decodeWasCalled).isFalse();
  }

  @Test
  public void decode_directDataInputBuffer_throwsIllegalArgumentException() {
    TestSimpleMetadataDecoder decoder = new TestSimpleMetadataDecoder();
    MetadataInputBuffer directDataInputBuffer = new MetadataInputBuffer();
    directDataInputBuffer.data = ByteBuffer.allocateDirect(8);

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(directDataInputBuffer));
    assertThat(decoder.decodeWasCalled).isFalse();
  }

  @Test
  public void decode_nonZeroPositionDataInputBuffer_throwsIllegalArgumentException() {
    TestSimpleMetadataDecoder decoder = new TestSimpleMetadataDecoder();
    MetadataInputBuffer nonZeroPositionDataInputBuffer = new MetadataInputBuffer();
    nonZeroPositionDataInputBuffer.data = ByteBuffer.wrap(new byte[8]);
    nonZeroPositionDataInputBuffer.data.position(1);

    assertThrows(
        IllegalArgumentException.class, () -> decoder.decode(nonZeroPositionDataInputBuffer));
    assertThat(decoder.decodeWasCalled).isFalse();
  }

  @Test
  public void decode_nonZeroOffsetDataInputBuffer_throwsIllegalArgumentException() {
    TestSimpleMetadataDecoder decoder = new TestSimpleMetadataDecoder();
    MetadataInputBuffer directDataInputBuffer = new MetadataInputBuffer();
    directDataInputBuffer.data = ByteBuffer.wrap(new byte[8], /* offset= */ 4, /* length= */ 4);

    assertThrows(IllegalArgumentException.class, () -> decoder.decode(directDataInputBuffer));
    assertThat(decoder.decodeWasCalled).isFalse();
  }

  @Test
  public void decode_decodeOnlyBuffer_notPassedToDecodeInternal() {
    TestSimpleMetadataDecoder decoder = new TestSimpleMetadataDecoder();
    MetadataInputBuffer decodeOnlyBuffer = new MetadataInputBuffer();
    decodeOnlyBuffer.data = ByteBuffer.wrap(new byte[8]);
    decodeOnlyBuffer.setFlags(C.BUFFER_FLAG_DECODE_ONLY);

    assertThat(decoder.decode(decodeOnlyBuffer)).isNull();
    assertThat(decoder.decodeWasCalled).isFalse();
  }

  @Test
  public void decode_returnsDecodeInternalResult() {
    TestSimpleMetadataDecoder decoder = new TestSimpleMetadataDecoder();
    MetadataInputBuffer buffer = new MetadataInputBuffer();
    buffer.data = ByteBuffer.wrap(new byte[8]);

    assertThat(decoder.decode(buffer)).isSameInstanceAs(decoder.result);
    assertThat(decoder.decodeWasCalled).isTrue();
  }

  private static final class TestSimpleMetadataDecoder extends SimpleMetadataDecoder {

    public final Metadata result;

    public boolean decodeWasCalled;

    public TestSimpleMetadataDecoder() {
      result = new Metadata();
    }

    @Nullable
    @Override
    protected Metadata decode(MetadataInputBuffer inputBuffer, ByteBuffer buffer) {
      decodeWasCalled = true;
      return result;
    }
  }
}
