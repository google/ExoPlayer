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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.Parcel;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import junit.framework.TestCase;

/**
 * Unit test for {@link MediaFormat}.
 */
public final class MediaFormatTest extends TestCase {

  private static final List<byte[]> INIT_DATA;
  static {
    byte[] initData1 = new byte[] {1, 2, 3};
    byte[] initData2 = new byte[] {4, 5, 6};
    List<byte[]> initData = new ArrayList<>();
    initData.add(initData1);
    initData.add(initData2);
    INIT_DATA = Collections.unmodifiableList(initData);
  }

  public void testParcelable() {
    MediaFormat formatToParcel = new MediaFormat("id", MimeTypes.VIDEO_H264, 1024, 2048,
        C.UNKNOWN_TIME_US, 1920, 1080, 90, 2, 6, 44100, "und", MediaFormat.OFFSET_SAMPLE_RELATIVE,
        INIT_DATA, false, 5000, 5001, 5002, 5003, 5004);

    Parcel parcel = Parcel.obtain();
    formatToParcel.writeToParcel(parcel, 0);
    parcel.setDataPosition(0);

    MediaFormat formatFromParcel = MediaFormat.CREATOR.createFromParcel(parcel);
    assertEquals(formatToParcel, formatFromParcel);

    parcel.recycle();
  }

  public void testConversionToFrameworkFormat() {
    if (Util.SDK_INT < 16) {
      // Test doesn't apply.
      return;
    }

    testConversionToFrameworkFormatV16(MediaFormat.createVideoFormat(null, "video/xyz", 5000,
        102400, 1000L, 1280, 720, INIT_DATA));
    testConversionToFrameworkFormatV16(MediaFormat.createVideoFormat(null, "video/xyz", 5000,
        MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US, 1280, 720, null));
    testConversionToFrameworkFormatV16(MediaFormat.createAudioFormat(null, "audio/xyz", 500, 128,
        1000L, 5, 44100, INIT_DATA, null));
    testConversionToFrameworkFormatV16(MediaFormat.createAudioFormat(null, "audio/xyz", 500,
        MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US, 5, 44100, null, null));
    testConversionToFrameworkFormatV16(MediaFormat.createTextFormat(null, "text/xyz",
        MediaFormat.NO_VALUE, 1000L, "eng"));
    testConversionToFrameworkFormatV16(MediaFormat.createTextFormat(null, "text/xyz",
        MediaFormat.NO_VALUE, C.UNKNOWN_TIME_US, null));
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
