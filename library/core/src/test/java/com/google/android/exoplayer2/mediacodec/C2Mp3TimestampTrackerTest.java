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

import static com.google.android.exoplayer2.testutil.TestUtil.createByteArray;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link C2Mp3TimestampTracker}. */
@RunWith(AndroidJUnit4.class)
public final class C2Mp3TimestampTrackerTest {

  private static final Format FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.AUDIO_MPEG)
          .setChannelCount(2)
          .setSampleRate(44_100)
          .build();

  private C2Mp3TimestampTracker timestampTracker;
  private DecoderInputBuffer buffer;
  private DecoderInputBuffer invalidBuffer;

  @Before
  public void setUp() {
    timestampTracker = new C2Mp3TimestampTracker();
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    buffer.data = ByteBuffer.wrap(createByteArray(0xFF, 0xFB, 0xE8, 0x3C));
    buffer.timeUs = 100_000;
    invalidBuffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    invalidBuffer.data = ByteBuffer.wrap(createByteArray(0, 0, 0, 0));
    invalidBuffer.timeUs = 120_000;
  }

  @Test
  public void handleBuffers_outputsCorrectTimestamps() {
    List<Long> presentationTimesUs = new ArrayList<>();
    presentationTimesUs.add(timestampTracker.updateAndGetPresentationTimeUs(FORMAT, buffer));
    presentationTimesUs.add(timestampTracker.updateAndGetPresentationTimeUs(FORMAT, buffer));
    presentationTimesUs.add(timestampTracker.updateAndGetPresentationTimeUs(FORMAT, buffer));
    presentationTimesUs.add(timestampTracker.getLastOutputBufferPresentationTimeUs(FORMAT));

    assertThat(presentationTimesUs).containsExactly(100_000L, 114_126L, 140_249L, 166_371L);
  }

  @Test
  public void handleBuffersWithReset_resetsTimestamps() {
    List<Long> presentationTimesUs = new ArrayList<>();
    presentationTimesUs.add(timestampTracker.updateAndGetPresentationTimeUs(FORMAT, buffer));
    presentationTimesUs.add(timestampTracker.updateAndGetPresentationTimeUs(FORMAT, buffer));
    timestampTracker.reset();
    presentationTimesUs.add(timestampTracker.updateAndGetPresentationTimeUs(FORMAT, buffer));
    presentationTimesUs.add(timestampTracker.getLastOutputBufferPresentationTimeUs(FORMAT));

    assertThat(presentationTimesUs).containsExactly(100_000L, 114_126L, 100_000L, 114_126L);
  }

  @Test
  public void handleInvalidBuffer_stopsUpdatingTimestamps() {
    List<Long> presentationTimesUs = new ArrayList<>();
    presentationTimesUs.add(timestampTracker.updateAndGetPresentationTimeUs(FORMAT, buffer));
    presentationTimesUs.add(timestampTracker.updateAndGetPresentationTimeUs(FORMAT, buffer));
    presentationTimesUs.add(timestampTracker.updateAndGetPresentationTimeUs(FORMAT, invalidBuffer));
    presentationTimesUs.add(timestampTracker.getLastOutputBufferPresentationTimeUs(FORMAT));

    assertThat(presentationTimesUs).containsExactly(100_000L, 114_126L, 120_000L, 120_000L);
  }

  @Test
  public void firstTimestamp_matchesBuffer() {
    assertThat(timestampTracker.updateAndGetPresentationTimeUs(FORMAT, buffer))
        .isEqualTo(buffer.timeUs);
    timestampTracker.reset();
    assertThat(timestampTracker.updateAndGetPresentationTimeUs(FORMAT, invalidBuffer))
        .isEqualTo(invalidBuffer.timeUs);
  }
}
