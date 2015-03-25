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

import android.annotation.TargetApi;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test for {@link MediaFormat}.
 */
public class MediaFormatTest extends TestCase {

  public void testConversionToFrameworkFormat() {
    if (Util.SDK_INT < 16) {
      // Test doesn't apply.
      return;
    }

    byte[] initData1 = new byte[] {1, 2, 3};
    byte[] initData2 = new byte[] {4, 5, 6};
    List<byte[]> initData = new ArrayList<byte[]>();
    initData.add(initData1);
    initData.add(initData2);

    testConversionToFrameworkFormatV16(
        MediaFormat.createVideoFormat("video/xyz", 102400, 1000L, 1280, 720, 1.5f, initData));
    testConversionToFrameworkFormatV16(
        MediaFormat.createAudioFormat("audio/xyz", 102400, 1000L, 5, 44100, initData));
  }

  @TargetApi(16)
  private void testConversionToFrameworkFormatV16(MediaFormat format) {
    // Convert to a framework MediaFormat and back again.
    MediaFormat convertedFormat = MediaFormat.createFromFrameworkMediaFormatV16(
        format.getFrameworkMediaFormatV16());
    // Assert that we end up with an equivalent object to the one we started with.
    assertEquals(format.hashCode(), convertedFormat.hashCode());
    assertEquals(format, convertedFormat);
  }

}
