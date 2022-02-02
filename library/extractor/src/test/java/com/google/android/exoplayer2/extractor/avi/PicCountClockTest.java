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

import org.junit.Assert;
import org.junit.Test;

public class PicCountClockTest {
  @Test
  public void us_givenTwoStepsForward() {
    final PicCountClock picCountClock = new PicCountClock(10_000L, 100);
    picCountClock.setMaxPicCount(16*2, 2);
    picCountClock.setPicCount(2*2);
    Assert.assertEquals(2*100, picCountClock.getUs());
  }

  @Test
  public void us_givenThreeStepsBackwards() {
    final PicCountClock picCountClock = new PicCountClock(10_000L, 100);
    picCountClock.setMaxPicCount(16*2, 2);
    picCountClock.setPicCount(4*2); // 400ms
    Assert.assertEquals(400, picCountClock.getUs());
    picCountClock.setPicCount(1*2);
    Assert.assertEquals(1*100, picCountClock.getUs());
  }

  @Test
  public void setIndex_given3Chunks() {
    final PicCountClock picCountClock = new PicCountClock(10_000L, 100);
    picCountClock.setIndex(3);
    Assert.assertEquals(3*100, picCountClock.getUs());
  }

  @Test
  public void us_giveWrapBackwards() {
    final PicCountClock picCountClock = new PicCountClock(10_000L, 100);
    picCountClock.setMaxPicCount(16*2, 2);
    //Need to walk up no faster than maxPicCount / 2
    picCountClock.setPicCount(7*2);
    picCountClock.setPicCount(11*2);
    picCountClock.setPicCount(15*2);
    picCountClock.setPicCount(1*2);
    Assert.assertEquals(17*100, picCountClock.getUs());
    picCountClock.setPicCount(14*2);
    Assert.assertEquals(14*100, picCountClock.getUs());
  }
}
