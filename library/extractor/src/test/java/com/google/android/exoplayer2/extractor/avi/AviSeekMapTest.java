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

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.SeekMap;
import org.junit.Assert;
import org.junit.Test;

public class AviSeekMapTest {

  @Test
  public void setFrames_givenExactSeekPointMatch() {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    final long position = aviSeekMap.keyFrameOffsetsDiv2[1] * 2L + aviSeekMap.seekOffset;
    final int secs = 4;
    final ChunkHandler[] chunkHandlers = new ChunkHandler[]{DataHelper.getVideoChunkHandler(secs),
        DataHelper.getAudioChunkHandler(secs)};

    aviSeekMap.setFrames(position, C.MICROS_PER_SECOND, chunkHandlers);
    for (int i=0;i<chunkHandlers.length;i++) {
      Assert.assertEquals(aviSeekMap.seekIndexes[i][1], chunkHandlers[i].getClock().getIndex());
    }
  }

  @Test
  public void setFrames_givenBadPosition() {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    final ChunkHandler[] chunkHandlers = new ChunkHandler[2];

    try {
      aviSeekMap.setFrames(1L, C.MICROS_PER_SECOND, chunkHandlers);
      Assert.fail();
    } catch (IllegalArgumentException e) {
      //Intentionally blank
    }
  }

  @Test
  public void getSeekPoints_givenNonKeyFrameUs() {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    //Time before the 1st keyFrame
    final long us = aviSeekMap.seekIndexes[0][1] * aviSeekMap.videoUsPerChunk - 100L;

    final SeekMap.SeekPoints seekPoints = aviSeekMap.getSeekPoints(us);
    Assert.assertEquals(aviSeekMap.seekIndexes[0][0] * aviSeekMap.videoUsPerChunk,
        seekPoints.first.timeUs);
    Assert.assertEquals(aviSeekMap.seekIndexes[0][1] * aviSeekMap.videoUsPerChunk,
        seekPoints.second.timeUs);
  }

  @Test
  public void getFirstSeekIndex_atZeroIndex() {
    final AviSeekMap aviSeekMap = DataHelper.getAviSeekMap();
    Assert.assertEquals(0, aviSeekMap.getFirstSeekIndex(-1));
  }
}
