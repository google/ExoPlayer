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
public class AudioFormatTest {
  final byte[] CODEC_PRIVATE = {0x11, (byte) 0x90};

  @Test
  public void getters_givenAacStreamFormat() throws IOException {
    final StreamFormatBox streamFormatBox = DataHelper.getAacStreamFormat();
    final AudioFormat audioFormat = streamFormatBox.getAudioFormat();
    Assert.assertEquals(MimeTypes.AUDIO_AAC, audioFormat.getMimeType());
    Assert.assertEquals(2, audioFormat.getChannels());
    Assert.assertEquals(0xff, audioFormat.getFormatTag()); // AAC
    Assert.assertEquals(48000, audioFormat.getSamplesPerSecond());
    Assert.assertEquals(0, audioFormat.getBitsPerSample()); //Not meaningful for AAC
    Assert.assertArrayEquals(CODEC_PRIVATE, audioFormat.getCodecData());
    Assert.assertEquals(MimeTypes.AUDIO_AAC, audioFormat.getMimeType());
    Assert.assertEquals(20034, audioFormat.getAvgBytesPerSec());
  }
}
