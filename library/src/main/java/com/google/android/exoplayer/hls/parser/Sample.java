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
package com.google.android.exoplayer.hls.parser;

import com.google.android.exoplayer.SampleHolder;

/**
 * An internal variant of {@link SampleHolder} for internal pooling and buffering.
 */
/* package */ class Sample {

  public static final int TYPE_VIDEO = 0;
  public static final int TYPE_AUDIO = 1;
  public static final int TYPE_MISC = 2;
  public static final int TYPE_COUNT = 3;

  public final int type;
  public Sample nextInPool;

  public byte[] data;
  public boolean isKeyframe;
  public int size;
  public long timeUs;

  public Sample(int type, int length) {
    this.type = type;
    data = new byte[length];
  }

  public void expand(int length) {
    byte[] newBuffer = new byte[data.length + length];
    System.arraycopy(data, 0, newBuffer, 0, size);
    data = newBuffer;
  }

  public void reset() {
    isKeyframe = false;
    size = 0;
    timeUs = 0;
  }

}
