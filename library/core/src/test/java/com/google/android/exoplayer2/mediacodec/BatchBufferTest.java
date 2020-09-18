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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.common.primitives.Bytes;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link BatchBuffer}. */
@RunWith(AndroidJUnit4.class)
public final class BatchBufferTest {

  /** Bigger than {@code BatchBuffer.BATCH_SIZE_BYTES} */
  private static final int BUFFER_SIZE_LARGER_THAN_BATCH_SIZE_BYTES = 6 * 1000 * 1024;
  /** Smaller than {@code BatchBuffer.BATCH_SIZE_BYTES} */
  private static final int BUFFER_SIZE_MUCH_SMALLER_THAN_BATCH_SIZE_BYTES = 100;

  private static final byte[] TEST_ACCESS_UNIT =
      TestUtil.buildTestData(BUFFER_SIZE_MUCH_SMALLER_THAN_BATCH_SIZE_BYTES);

  private final BatchBuffer batchBuffer = new BatchBuffer();

  @Test
  public void newBatchBuffer_isEmpty() {
    assertIsCleared(batchBuffer);
  }

  @Test
  public void clear_empty_isEmpty() {
    batchBuffer.clear();

    assertIsCleared(batchBuffer);
  }

  @Test
  public void clear_afterInsertingAccessUnit_isEmpty() {
    batchBuffer.commitNextAccessUnit();

    batchBuffer.clear();

    assertIsCleared(batchBuffer);
  }

  @Test
  public void commitNextAccessUnit_addsAccessUnit() {
    batchBuffer.commitNextAccessUnit();

    assertThat(batchBuffer.getAccessUnitCount()).isEqualTo(1);
  }

  @Test
  public void commitNextAccessUnit_untilFull_isFullAndNotEmpty() {
    fillBatchBuffer(batchBuffer);

    assertThat(batchBuffer.isEmpty()).isFalse();
    assertThat(batchBuffer.isFull()).isTrue();
  }

  @Test
  public void commitNextAccessUnit_whenFull_throws() {
    batchBuffer.setMaxAccessUnitCount(1);
    batchBuffer.commitNextAccessUnit();

    assertThrows(IllegalStateException.class, batchBuffer::commitNextAccessUnit);
  }

  @Test
  public void commitNextAccessUnit_whenAccessUnitIsDecodeOnly_isDecodeOnly() {
    batchBuffer.getNextAccessUnitBuffer().setFlags(C.BUFFER_FLAG_DECODE_ONLY);

    batchBuffer.commitNextAccessUnit();

    assertThat(batchBuffer.isDecodeOnly()).isTrue();
  }

  @Test
  public void commitNextAccessUnit_whenAccessUnitIsEndOfStream_isEndOfSteam() {
    batchBuffer.getNextAccessUnitBuffer().setFlags(C.BUFFER_FLAG_END_OF_STREAM);

    batchBuffer.commitNextAccessUnit();

    assertThat(batchBuffer.isEndOfStream()).isTrue();
  }

  @Test
  public void commitNextAccessUnit_whenAccessUnitIsKeyFrame_isKeyFrame() {
    batchBuffer.getNextAccessUnitBuffer().setFlags(C.BUFFER_FLAG_KEY_FRAME);

    batchBuffer.commitNextAccessUnit();

    assertThat(batchBuffer.isKeyFrame()).isTrue();
  }

  @Test
  public void commitNextAccessUnit_withData_dataIsCopiedInTheBatch() {
    batchBuffer.getNextAccessUnitBuffer().ensureSpaceForWrite(TEST_ACCESS_UNIT.length);
    batchBuffer.getNextAccessUnitBuffer().data.put(TEST_ACCESS_UNIT);

    batchBuffer.commitNextAccessUnit();
    batchBuffer.flip();

    assertThat(batchBuffer.getAccessUnitCount()).isEqualTo(1);
    assertThat(batchBuffer.data).isEqualTo(ByteBuffer.wrap(TEST_ACCESS_UNIT));
  }

  @Test
  public void commitNextAccessUnit_nextAccessUnit_isClear() {
    batchBuffer.getNextAccessUnitBuffer().ensureSpaceForWrite(TEST_ACCESS_UNIT.length);
    batchBuffer.getNextAccessUnitBuffer().data.put(TEST_ACCESS_UNIT);
    batchBuffer.getNextAccessUnitBuffer().setFlags(C.BUFFER_FLAG_KEY_FRAME);

    batchBuffer.commitNextAccessUnit();

    DecoderInputBuffer nextAccessUnit = batchBuffer.getNextAccessUnitBuffer();
    assertThat(nextAccessUnit.data).isNotNull();
    assertThat(nextAccessUnit.data.position()).isEqualTo(0);
    assertThat(nextAccessUnit.isKeyFrame()).isFalse();
  }

  @Test
  public void commitNextAccessUnit_twice_bothAccessUnitAreConcatenated() {
    // Commit TEST_ACCESS_UNIT
    batchBuffer.getNextAccessUnitBuffer().ensureSpaceForWrite(TEST_ACCESS_UNIT.length);
    batchBuffer.getNextAccessUnitBuffer().data.put(TEST_ACCESS_UNIT);
    batchBuffer.commitNextAccessUnit();
    // Commit TEST_ACCESS_UNIT again
    batchBuffer.getNextAccessUnitBuffer().ensureSpaceForWrite(TEST_ACCESS_UNIT.length);
    batchBuffer.getNextAccessUnitBuffer().data.put(TEST_ACCESS_UNIT);

    batchBuffer.commitNextAccessUnit();
    batchBuffer.flip();

    byte[] expected = Bytes.concat(TEST_ACCESS_UNIT, TEST_ACCESS_UNIT);
    assertThat(batchBuffer.data).isEqualTo(ByteBuffer.wrap(expected));
  }

  @Test
  public void commitNextAccessUnit_whenAccessUnitIsHugeAndBatchBufferNotEmpty_isMarkedPending() {
    batchBuffer.getNextAccessUnitBuffer().ensureSpaceForWrite(TEST_ACCESS_UNIT.length);
    batchBuffer.getNextAccessUnitBuffer().data.put(TEST_ACCESS_UNIT);
    batchBuffer.commitNextAccessUnit();
    byte[] hugeAccessUnit = TestUtil.buildTestData(BUFFER_SIZE_LARGER_THAN_BATCH_SIZE_BYTES);
    batchBuffer.getNextAccessUnitBuffer().ensureSpaceForWrite(hugeAccessUnit.length);
    batchBuffer.getNextAccessUnitBuffer().data.put(hugeAccessUnit);
    batchBuffer.commitNextAccessUnit();

    batchBuffer.batchWasConsumed();
    batchBuffer.flip();

    assertThat(batchBuffer.getAccessUnitCount()).isEqualTo(1);
    assertThat(batchBuffer.data).isEqualTo(ByteBuffer.wrap(hugeAccessUnit));
  }

  @Test
  public void batchWasConsumed_whenNotEmpty_isEmpty() {
    batchBuffer.commitNextAccessUnit();

    batchBuffer.batchWasConsumed();

    assertIsCleared(batchBuffer);
  }

  @Test
  public void batchWasConsumed_whenFull_isEmpty() {
    fillBatchBuffer(batchBuffer);

    batchBuffer.batchWasConsumed();

    assertIsCleared(batchBuffer);
  }

  @Test
  public void getMaxAccessUnitCount_whenSetToAPositiveValue_returnsIt() {
    batchBuffer.setMaxAccessUnitCount(20);

    assertThat(batchBuffer.getMaxAccessUnitCount()).isEqualTo(20);
  }

  @Test
  public void setMaxAccessUnitCount_whenSetToNegative_throws() {
    assertThrows(IllegalArgumentException.class, () -> batchBuffer.setMaxAccessUnitCount(-19));
  }

  @Test
  public void setMaxAccessUnitCount_whenSetToZero_throws() {
    assertThrows(IllegalArgumentException.class, () -> batchBuffer.setMaxAccessUnitCount(0));
  }

  @Test
  public void setMaxAccessUnitCount_whenSetToTheNumberOfAccessUnitInTheBatch_isFull() {
    batchBuffer.commitNextAccessUnit();

    batchBuffer.setMaxAccessUnitCount(1);

    assertThat(batchBuffer.isFull()).isTrue();
  }

  @Test
  public void batchWasConsumed_whenAccessUnitIsPending_pendingAccessUnitIsInTheBatch() {
    batchBuffer.commitNextAccessUnit();
    batchBuffer.getNextAccessUnitBuffer().setFlags(C.BUFFER_FLAG_DECODE_ONLY);
    batchBuffer.getNextAccessUnitBuffer().ensureSpaceForWrite(TEST_ACCESS_UNIT.length);
    batchBuffer.getNextAccessUnitBuffer().data.put(TEST_ACCESS_UNIT);
    batchBuffer.commitNextAccessUnit();

    batchBuffer.batchWasConsumed();
    batchBuffer.flip();

    assertThat(batchBuffer.getAccessUnitCount()).isEqualTo(1);
    assertThat(batchBuffer.isDecodeOnly()).isTrue();
    assertThat(batchBuffer.data).isEqualTo(ByteBuffer.wrap(TEST_ACCESS_UNIT));
  }

  private static void fillBatchBuffer(BatchBuffer batchBuffer) {
    int maxAccessUnit = batchBuffer.getMaxAccessUnitCount();
    while (!batchBuffer.isFull()) {
      assertThat(maxAccessUnit--).isNotEqualTo(0);
      batchBuffer.commitNextAccessUnit();
    }
  }

  private static void assertIsCleared(BatchBuffer batchBuffer) {
    assertThat(batchBuffer.getFirstAccessUnitTimeUs()).isEqualTo(C.TIME_UNSET);
    assertThat(batchBuffer.getLastAccessUnitTimeUs()).isEqualTo(C.TIME_UNSET);
    assertThat(batchBuffer.getAccessUnitCount()).isEqualTo(0);
    assertThat(batchBuffer.isEmpty()).isTrue();
    assertThat(batchBuffer.isFull()).isFalse();
  }
}
