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
import android.media.MediaFormat;

import junit.framework.TestCase;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit test for {@link Format}.
 */
public final class FormatTest extends TestCase {

  public void testConversionToFrameworkMediaFormat() {
    if (Util.SDK_INT < 16) {
      // Test doesn't apply.
      return;
    }

    byte[] initData1 = new byte[] {1, 2, 3};
    byte[] initData2 = new byte[] {4, 5, 6};
    List<byte[]> initData = new ArrayList<>();
    initData.add(initData1);
    initData.add(initData2);

    testConversionToFrameworkMediaFormatV16(Format.createVideoSampleFormat(
        null, "video/xyz", 5000, 102400, 1280, 720, 30, initData));
    testConversionToFrameworkMediaFormatV16(Format.createVideoSampleFormat(
        null, "video/xyz", 5000, Format.NO_VALUE, 1280, 720, 30, null));
    testConversionToFrameworkMediaFormatV16(Format.createAudioSampleFormat(
        null, "audio/xyz", 500, 128, 5, 44100, initData, null));
    testConversionToFrameworkMediaFormatV16(Format.createAudioSampleFormat(
        null, "audio/xyz", 500, Format.NO_VALUE, 5, 44100, null, null));
    testConversionToFrameworkMediaFormatV16(
        Format.createTextSampleFormat(null, "text/xyz", Format.NO_VALUE, "eng"));
    testConversionToFrameworkMediaFormatV16(
        Format.createTextSampleFormat(null, "text/xyz", Format.NO_VALUE, null));
  }

  @SuppressLint("InlinedApi")
  @TargetApi(16)
  private static void testConversionToFrameworkMediaFormatV16(Format in) {
    MediaFormat out = in.getFrameworkMediaFormatV16();
    assertEquals(in.sampleMimeType, out.getString(MediaFormat.KEY_MIME));
    assertOptionalV16(out, MediaFormat.KEY_LANGUAGE, in.language);
    assertOptionalV16(out, MediaFormat.KEY_MAX_INPUT_SIZE, in.maxInputSize);
    assertOptionalV16(out, MediaFormat.KEY_WIDTH, in.width);
    assertOptionalV16(out, MediaFormat.KEY_HEIGHT, in.height);
    assertOptionalV16(out, MediaFormat.KEY_CHANNEL_COUNT, in.channelCount);
    assertOptionalV16(out, MediaFormat.KEY_SAMPLE_RATE, in.sampleRate);
    assertOptionalV16(out, MediaFormat.KEY_FRAME_RATE, in.frameRate);

    for (int i = 0; i < in.initializationData.size(); i++) {
      byte[] originalData = in.initializationData.get(i);
      ByteBuffer frameworkBuffer = out.getByteBuffer("csd-" + i);
      byte[] frameworkData = Arrays.copyOf(frameworkBuffer.array(), frameworkBuffer.limit());
      assertTrue(Arrays.equals(originalData, frameworkData));
    }
  }

  @TargetApi(16)
  private static void assertOptionalV16(MediaFormat format, String key, String value) {
    if (value == null) {
      assertFalse(format.containsKey(key));
    } else {
      assertEquals(value, format.getString(key));
    }
  }

  @TargetApi(16)
  private static void assertOptionalV16(MediaFormat format, String key, int value) {
    if (value == Format.NO_VALUE) {
      assertFalse(format.containsKey(key));
    } else {
      assertEquals(value, format.getInteger(key));
    }
  }

  @TargetApi(16)
  private static void assertOptionalV16(MediaFormat format, String key, float value) {
    if (value == Format.NO_VALUE) {
      assertFalse(format.containsKey(key));
    } else {
      assertEquals(value, format.getFloat(key));
    }
  }

}
