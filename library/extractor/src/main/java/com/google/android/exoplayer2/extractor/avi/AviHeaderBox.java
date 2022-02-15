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

import androidx.annotation.VisibleForTesting;
import java.nio.ByteBuffer;

/**
 * Wrapper around the AVIMAINHEADER structure
 */
public class AviHeaderBox extends ResidentBox {
  static final int LEN = 0x38;
  static final int AVIF_HASINDEX = 0x10;
  private static final int AVIF_MUSTUSEINDEX = 0x20;
  static final int AVIH = 0x68697661; // avih

  AviHeaderBox(int type, int size, ByteBuffer byteBuffer) {
    super(type, size, byteBuffer);
  }

  int getMicroSecPerFrame() {
    return byteBuffer.getInt(0);
  }

  //4 = dwMaxBytesPerSec
  //8 = dwPaddingGranularity - Always 0, but should be 2

  public boolean hasIndex() {
    return (getFlags() & AVIF_HASINDEX) == AVIF_HASINDEX;
  }

  public boolean mustUseIndex() {
    return (getFlags() & AVIF_MUSTUSEINDEX) == AVIF_MUSTUSEINDEX;
  }

  int getFlags() {
    return byteBuffer.getInt(12);
  }

  int getTotalFrames() {
    return byteBuffer.getInt(16);
  }

  // 20 - dwInitialFrames

  int getStreams() {
    return byteBuffer.getInt(24);
  }

  // 28 - dwSuggestedBufferSize
  // 32 - dwWidth
  // 36 - dwHeight

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  void setFlags(int flags) {
    byteBuffer.putInt(12, flags);
  }
}
