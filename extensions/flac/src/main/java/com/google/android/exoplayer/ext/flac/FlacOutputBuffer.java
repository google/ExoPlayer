/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer.ext.flac;

import com.google.android.exoplayer.util.extensions.OutputBuffer;

import java.nio.ByteBuffer;

/**
 * Buffer for {@link FlacDecoder} output.
 */
public final class FlacOutputBuffer extends OutputBuffer {

  private final FlacDecoder owner;

  public ByteBuffer data;

  /* package */ FlacOutputBuffer(FlacDecoder owner) {
    this.owner = owner;
  }

  /* package */ void init(int size) {
    if (data == null || data.capacity() < size) {
      data = ByteBuffer.allocateDirect(size);
    }
    data.position(0);
    data.limit(size);
  }

  @Override
  public void reset() {
    super.reset();
    if (data != null) {
      data.clear();
    }
  }

  @Override
  public void release() {
    owner.releaseOutputBuffer(this);
  }

}
