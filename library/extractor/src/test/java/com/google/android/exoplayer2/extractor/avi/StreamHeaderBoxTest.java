/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StreamHeaderBoxTest {
  private static float FPS24 = 24000f/1001f;
  private static final long US_SAMPLE24FPS = (long)(1_000_000L / FPS24);

  @Test
  public void getters_givenXvidStreamHeader() throws IOException {
    final StreamHeaderBox streamHeaderBox = DataHelper.getVidsStreamHeader();

    Assert.assertTrue(streamHeaderBox.isVideo());
    Assert.assertFalse(streamHeaderBox.isAudio());
    Assert.assertEquals(StreamHeaderBox.VIDS, streamHeaderBox.getSteamType());
    Assert.assertEquals(0, streamHeaderBox.getInitialFrames());
    Assert.assertEquals(FPS24, streamHeaderBox.getFrameRate(), 0.1);
    Assert.assertEquals(9 * DataHelper.FPS, streamHeaderBox.getLength());
    Assert.assertEquals(128 * 1024, streamHeaderBox.getSuggestedBufferSize());
    Assert.assertTrue(streamHeaderBox.toString().startsWith("scale=" + streamHeaderBox.getScale()));
  }
}
