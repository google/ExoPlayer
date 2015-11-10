/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer;

import com.google.android.exoplayer.util.Util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;

import junit.framework.TestCase;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit test for {@link MediaFormat}.
 */
public final class MediaFormatTest extends TestCase {

  public void testConversionToFrameworkFormat() {
    if (Util.SDK_INT < 16) {
      // Test doesn't apply.
      return;
    }

    byte[] initData1 = new byte[] {1, 2, 3};
    byte[] initData2 = new byte[] {4, 5, 6};
    List<byte[]> initData = new ArrayList<>();
    initData.add(initData1);
    initData.add(initData2);

    testConversionToFrameworkFormatV16(MediaFormat.createVideoFormat(
        null, "video/xyz", 5000, 102400, 1000L, 1280, 720, initData));
    testConversionToFrameworkFormatV16(MediaFormat.createVideoFormat(
        null, "video/xyz", 5000, MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US, 1280, 720, null));
    testConversionToFrameworkFormatV16(MediaFormat.createAudioFormat(
        null, "audio/xyz", 500, 128, 1000L, 5, 44100, initData, null));
    testConversionToFrameworkFormatV16(MediaFormat.createAudioFormat(
        null, "audio/xyz", 500, MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US, 5, 44100, null, null));
    testConversionToFrameworkFormatV16(
        MediaFormat.createTextFormat(null, "text/xyz", MediaFormat.NO_VALUE, 1000L, "eng"));
    testConversionToFrameworkFormatV16(
        MediaFormat.createTextFormat(null, "text/xyz", MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US,
        null));
  }

  @SuppressLint("InlinedApi")
  @TargetApi(16)
  private static void testConversionToFrameworkFormatV16(MediaFormat in) {
    android.media.MediaFormat out = in.getFrameworkMediaFormatV16();
    assertEquals(in.mimeType, out.getString(android.media.MediaFormat.KEY_MIME));
    assertOptionalV16(out, android.media.MediaFormat.KEY_LANGUAGE, in.language);
    assertOptionalV16(out, android.media.MediaFormat.KEY_MAX_INPUT_SIZE, in.maxInputSize);
    assertOptionalV16(out, android.media.MediaFormat.KEY_WIDTH, in.width);
    assertOptionalV16(out, android.media.MediaFormat.KEY_HEIGHT, in.height);
    assertOptionalV16(out, android.media.MediaFormat.KEY_CHANNEL_COUNT, in.channelCount);
    assertOptionalV16(out, android.media.MediaFormat.KEY_SAMPLE_RATE, in.sampleRate);
    assertOptionalV16(out, android.media.MediaFormat.KEY_MAX_WIDTH, in.maxWidth);
    assertOptionalV16(out, android.media.MediaFormat.KEY_MAX_HEIGHT, in.maxHeight);
    for (int i = 0; i < in.initializationData.size(); i++) {
      byte[] originalData = in.initializationData.get(i);
      ByteBuffer frameworkBuffer = out.getByteBuffer("csd-" + i);
      byte[] frameworkData = Arrays.copyOf(frameworkBuffer.array(), frameworkBuffer.limit());
      assertTrue(Arrays.equals(originalData, frameworkData));
    }
    if (in.durationUs == C.UNKNOWN_TIME_US) {
      assertFalse(out.containsKey(android.media.MediaFormat.KEY_DURATION));
    } else {
      assertEquals(in.durationUs, out.getLong(android.media.MediaFormat.KEY_DURATION));
    }
  }

  @TargetApi(16)
  private static void assertOptionalV16(android.media.MediaFormat format, String key,
      String value) {
    if (value == null) {
      assertFalse(format.containsKey(key));
    } else {
      assertEquals(value, format.getString(key));
    }
  }

  @TargetApi(16)
  private static void assertOptionalV16(android.media.MediaFormat format, String key,
      int value) {
    if (value == MediaFormat.NO_VALUE) {
      assertFalse(format.containsKey(key));
    } else {
      assertEquals(value, format.getInteger(key));
    }
  }

}
