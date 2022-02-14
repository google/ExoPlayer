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

import com.google.android.exoplayer2.extractor.SeekMap;
import org.junit.Assert;
import org.junit.Test;

public class AviSeekMapTest {

  @Test
  public void getFrames_givenExactSeekPointMatch() {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    final long position = aviSeekMap.getKeyFrameOffsets(DataHelper.AUDIO_ID) + aviSeekMap.seekOffset;
    final int secs = 4;
    final ChunkHandler[] chunkHandlers = new ChunkHandler[]{DataHelper.getVideoChunkHandler(secs),
        DataHelper.getAudioChunkHandler(secs)};

    int[] indexes = aviSeekMap.getIndexes(position);
    for (int i=0;i<chunkHandlers.length;i++) {
      Assert.assertEquals(aviSeekMap.getSeekIndexes(i)[1], indexes[i]);
    }
  }

  @Test
  public void setFrames_givenBadPosition() {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();

    try {
      aviSeekMap.getIndexes(1L);
      Assert.fail();
    } catch (IllegalArgumentException e) {
      //Intentionally blank
    }
  }

  @Test
  public void getSeekPoints_givenNonKeyFrameUs() {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    //Time before the 1st keyFrame
    final long videoUsPerChunk = aviSeekMap.getVideoUsPerChunk();
    final long us = aviSeekMap.getSeekIndexes(DataHelper.VIDEO_ID)[1] * videoUsPerChunk - 100L;

    final SeekMap.SeekPoints seekPoints = aviSeekMap.getSeekPoints(us);
    Assert.assertEquals(aviSeekMap.getSeekIndexes(DataHelper.VIDEO_ID)[0] * videoUsPerChunk,
        seekPoints.first.timeUs);
    Assert.assertEquals(aviSeekMap.getSeekIndexes(DataHelper.VIDEO_ID)[1] * videoUsPerChunk,
        seekPoints.second.timeUs);
  }

  @Test
  public void getFirstSeekIndex_atZeroIndex() {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    Assert.assertEquals(0, aviSeekMap.getFirstSeekIndex(-1));
  }
}
