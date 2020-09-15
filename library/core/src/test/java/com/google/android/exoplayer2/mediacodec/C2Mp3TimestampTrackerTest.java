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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link C2Mp3TimestampTracker}. */
@RunWith(AndroidJUnit4.class)
public final class C2Mp3TimestampTrackerTest {

  private static final Format AUDIO_MP3 =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.AUDIO_MPEG)
          .setChannelCount(2)
          .setSampleRate(44_100)
          .build();

  private DecoderInputBuffer buffer;
  private C2Mp3TimestampTracker timestampTracker;

  @Before
  public void setUp() {
    buffer = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DISABLED);
    timestampTracker = new C2Mp3TimestampTracker();
    buffer.data = ByteBuffer.wrap(new byte[] {-1, -5, -24, 60});
    buffer.timeUs = 100_000;
  }

  @Test
  public void whenUpdateCalledMultipleTimes_timestampsIncrease() {
    long first = timestampTracker.updateAndGetPresentationTimeUs(AUDIO_MP3, buffer);
    long second = timestampTracker.updateAndGetPresentationTimeUs(AUDIO_MP3, buffer);
    long third = timestampTracker.updateAndGetPresentationTimeUs(AUDIO_MP3, buffer);

    assertThat(second).isGreaterThan(first);
    assertThat(third).isGreaterThan(second);
  }

  @Test
  public void whenResetCalled_timestampsDecrease() {
    long first = timestampTracker.updateAndGetPresentationTimeUs(AUDIO_MP3, buffer);
    long second = timestampTracker.updateAndGetPresentationTimeUs(AUDIO_MP3, buffer);
    timestampTracker.reset();
    long third = timestampTracker.updateAndGetPresentationTimeUs(AUDIO_MP3, buffer);

    assertThat(second).isGreaterThan(first);
    assertThat(third).isLessThan(second);
  }

  @Test
  public void whenBufferTimeIsNotZero_firstSampleIsOffset() {
    long first = timestampTracker.updateAndGetPresentationTimeUs(AUDIO_MP3, buffer);

    assertThat(first).isEqualTo(buffer.timeUs);
  }
}
