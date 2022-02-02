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

/**
 * Most of this is covered by the PicOrderClockTest
 */
public class LinearClockTest {
  @Test
  public void advance() {
    final LinearClock linearClock = new LinearClock(1_000L, 10);
    linearClock.setIndex(2);
    Assert.assertEquals(200, linearClock.getUs());
    linearClock.advance();
    Assert.assertEquals(300, linearClock.getUs());
  }
}
