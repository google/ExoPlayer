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

import static com.google.android.exoplayer2.decoder.DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT;
import static com.google.android.exoplayer2.mediacodec.BatchBuffer.DEFAULT_MAX_SAMPLE_COUNT;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import java.nio.ByteBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link BatchBuffer}. */
@RunWith(AndroidJUnit4.class)
public final class BatchBufferTest {

  private final DecoderInputBuffer sampleBuffer =
      new DecoderInputBuffer(BUFFER_REPLACEMENT_MODE_DIRECT);
  private final BatchBuffer batchBuffer = new BatchBuffer();

  @Test
  public void newBatchBuffer_isEmpty() {
    assertThat(batchBuffer.getSampleCount()).isEqualTo(0);
    assertThat(batchBuffer.hasSamples()).isFalse();
  }

  @Test
  public void appendSample() {
    initSampleBuffer();
    batchBuffer.append(sampleBuffer);

    assertThat(batchBuffer.getSampleCount()).isEqualTo(1);
    assertThat(batchBuffer.hasSamples()).isTrue();
  }

  @Test
  public void appendSample_thenClear_isEmpty() {
    initSampleBuffer();
    batchBuffer.append(sampleBuffer);
    batchBuffer.clear();

    assertThat(batchBuffer.getSampleCount()).isEqualTo(0);
    assertThat(batchBuffer.hasSamples()).isFalse();
  }

  @Test
  public void appendSample_updatesTimes() {
    initSampleBuffer(/* timeUs= */ 1234);
    batchBuffer.append(sampleBuffer);

    initSampleBuffer(/* timeUs= */ 5678);
    batchBuffer.append(sampleBuffer);

    assertThat(batchBuffer.timeUs).isEqualTo(1234);
    assertThat(batchBuffer.getFirstSampleTimeUs()).isEqualTo(1234);
    assertThat(batchBuffer.getLastSampleTimeUs()).isEqualTo(5678);
  }

  @Test
  public void appendSample_succeedsUntilDefaultMaxSampleCountReached_thenFails() {
    for (int i = 0; i < DEFAULT_MAX_SAMPLE_COUNT; i++) {
      initSampleBuffer(/* timeUs= */ i);
      assertThat(batchBuffer.append(sampleBuffer)).isTrue();
      assertThat(batchBuffer.getSampleCount()).isEqualTo(i + 1);
    }

    initSampleBuffer(/* timeUs= */ DEFAULT_MAX_SAMPLE_COUNT);
    assertThat(batchBuffer.append(sampleBuffer)).isFalse();
    assertThat(batchBuffer.getSampleCount()).isEqualTo(DEFAULT_MAX_SAMPLE_COUNT);
    assertThat(batchBuffer.getLastSampleTimeUs()).isEqualTo(DEFAULT_MAX_SAMPLE_COUNT - 1);
  }

  @Test
  public void appendSample_succeedsUntilCustomMaxSampleCountReached_thenFails() {
    int customMaxSampleCount = DEFAULT_MAX_SAMPLE_COUNT * 2;
    batchBuffer.setMaxSampleCount(customMaxSampleCount);
    for (int i = 0; i < customMaxSampleCount; i++) {
      initSampleBuffer(/* timeUs= */ i);
      assertThat(batchBuffer.append(sampleBuffer)).isTrue();
      assertThat(batchBuffer.getSampleCount()).isEqualTo(i + 1);
    }

    initSampleBuffer(/* timeUs= */ customMaxSampleCount);
    assertThat(batchBuffer.append(sampleBuffer)).isFalse();
    assertThat(batchBuffer.getSampleCount()).isEqualTo(customMaxSampleCount);
    assertThat(batchBuffer.getLastSampleTimeUs()).isEqualTo(customMaxSampleCount - 1);
  }

  @Test
  public void appendFirstSample_withDecodeOnlyFlag_setsDecodeOnlyFlag() {
    initSampleBuffer();
    sampleBuffer.setFlags(C.BUFFER_FLAG_DECODE_ONLY);
    batchBuffer.append(sampleBuffer);

    assertThat(batchBuffer.isDecodeOnly()).isTrue();
  }

  @Test
  public void appendSecondSample_toDecodeOnlyBuffer_withDecodeOnlyFlag_succeeds() {
    initSampleBuffer();
    sampleBuffer.setFlags(C.BUFFER_FLAG_DECODE_ONLY);
    batchBuffer.append(sampleBuffer);

    initSampleBuffer();
    sampleBuffer.setFlags(C.BUFFER_FLAG_DECODE_ONLY);

    assertThat(batchBuffer.append(sampleBuffer)).isTrue();
  }

  @Test
  public void appendSecondSample_toDecodeOnlyBuffer_withoutDecodeOnlyFlag_fails() {
    initSampleBuffer();
    sampleBuffer.setFlags(C.BUFFER_FLAG_DECODE_ONLY);
    batchBuffer.append(sampleBuffer);

    initSampleBuffer();

    assertThat(batchBuffer.append(sampleBuffer)).isFalse();
  }

  @Test
  public void appendSecondSample_toNonDecodeOnlyBuffer_withDecodeOnlyFlag_fails() {
    initSampleBuffer();
    batchBuffer.append(sampleBuffer);

    initSampleBuffer();
    sampleBuffer.setFlags(C.BUFFER_FLAG_DECODE_ONLY);

    assertThat(batchBuffer.append(sampleBuffer)).isFalse();
  }

  @Test
  public void appendSecondSample_withKeyframeFlag_setsKeyframeFlag() {
    initSampleBuffer();
    sampleBuffer.setFlags(C.BUFFER_FLAG_KEY_FRAME);
    batchBuffer.append(sampleBuffer);

    assertThat(batchBuffer.isKeyFrame()).isTrue();
  }

  @Test
  public void appendSecondSample_withKeyframeFlag_doesNotSetKeyframeFlag() {
    initSampleBuffer();
    batchBuffer.append(sampleBuffer);

    initSampleBuffer();
    sampleBuffer.setFlags(C.BUFFER_FLAG_KEY_FRAME);
    batchBuffer.append(sampleBuffer);

    assertThat(batchBuffer.isKeyFrame()).isFalse();
  }

  @Test
  public void appendSecondSample_doesNotClearKeyframeFlag() {
    initSampleBuffer();
    sampleBuffer.setFlags(C.BUFFER_FLAG_KEY_FRAME);
    batchBuffer.append(sampleBuffer);

    initSampleBuffer();
    batchBuffer.append(sampleBuffer);

    assertThat(batchBuffer.isKeyFrame()).isTrue();
  }

  @Test
  public void appendSample_withEndOfStreamFlag_throws() {
    sampleBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);

    assertThrows(IllegalArgumentException.class, () -> batchBuffer.append(sampleBuffer));
  }

  @Test
  public void appendSample_withEncryptedFlag_throws() {
    sampleBuffer.setFlags(C.BUFFER_FLAG_ENCRYPTED);

    assertThrows(IllegalArgumentException.class, () -> batchBuffer.append(sampleBuffer));
  }

  @Test
  public void appendSample_withSupplementalDataFlag_throws() {
    sampleBuffer.setFlags(C.BUFFER_FLAG_HAS_SUPPLEMENTAL_DATA);

    assertThrows(IllegalArgumentException.class, () -> batchBuffer.append(sampleBuffer));
  }

  @Test
  public void appendTwoSamples_batchesData() {
    initSampleBuffer(/* timeUs= */ 1234);
    batchBuffer.append(sampleBuffer);
    initSampleBuffer(/* timeUs= */ 5678);
    batchBuffer.append(sampleBuffer);
    batchBuffer.flip();

    ByteBuffer expected = ByteBuffer.allocate(Long.BYTES * 2);
    expected.putLong(1234);
    expected.putLong(5678);
    expected.flip();

    assertThat(batchBuffer.data).isEqualTo(expected);
  }

  @Test
  public void appendFirstSample_exceedingMaxSize_succeeds() {
    sampleBuffer.ensureSpaceForWrite(BatchBuffer.MAX_SIZE_BYTES + 1);
    sampleBuffer.data.position(BatchBuffer.MAX_SIZE_BYTES + 1);
    sampleBuffer.flip();
    assertThat(batchBuffer.append(sampleBuffer)).isTrue();
  }

  @Test
  public void appendSecondSample_exceedingMaxSize_fails() {
    initSampleBuffer();
    batchBuffer.append(sampleBuffer);

    int exceedsMaxSize = BatchBuffer.MAX_SIZE_BYTES - sampleBuffer.data.limit() + 1;
    sampleBuffer.clear();
    sampleBuffer.ensureSpaceForWrite(exceedsMaxSize);
    sampleBuffer.data.position(exceedsMaxSize);
    sampleBuffer.flip();
    assertThat(batchBuffer.append(sampleBuffer)).isFalse();
  }

  @Test
  public void appendSecondSample_equalsMaxSize_succeeds() {
    initSampleBuffer();
    batchBuffer.append(sampleBuffer);

    int exceedsMaxSize = BatchBuffer.MAX_SIZE_BYTES - sampleBuffer.data.limit();
    sampleBuffer.clear();
    sampleBuffer.ensureSpaceForWrite(exceedsMaxSize);
    sampleBuffer.data.position(exceedsMaxSize);
    sampleBuffer.flip();
    assertThat(batchBuffer.append(sampleBuffer)).isTrue();
  }

  private void initSampleBuffer() {
    initSampleBuffer(/* timeUs= */ 0);
  }

  private void initSampleBuffer(long timeUs) {
    sampleBuffer.clear();
    sampleBuffer.timeUs = timeUs;
    sampleBuffer.ensureSpaceForWrite(Long.BYTES);
    sampleBuffer.data.putLong(timeUs);
    sampleBuffer.flip();
  }
}
