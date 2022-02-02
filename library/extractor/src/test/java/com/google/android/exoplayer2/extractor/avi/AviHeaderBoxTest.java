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

public class AviHeaderBoxTest {

  @Test
  public void getters() {
    final AviHeaderBox aviHeaderBox = DataHelper.createAviHeaderBox();
    Assert.assertEquals(DataHelper.VIDEO_US, aviHeaderBox.getMicroSecPerFrame());
    Assert.assertTrue(aviHeaderBox.hasIndex());
    Assert.assertFalse(aviHeaderBox.mustUseIndex());
    Assert.assertEquals(5 * DataHelper.FPS, aviHeaderBox.getTotalFrames());
    Assert.assertEquals(2, aviHeaderBox.getStreams());
  }

}
