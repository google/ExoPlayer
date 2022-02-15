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
import java.nio.ByteBuffer;

/**
 * Wrapper around the AVISTREAMHEADER structure
 */
public class StreamHeaderBox extends ResidentBox {
  public static final int STRH = 0x68727473; // strh

  //Audio Stream
  static final int AUDS = 0x73647561; // auds

  //Videos Stream
  static final int VIDS = 0x73646976; // vids

  StreamHeaderBox(int type, int size, ByteBuffer byteBuffer) {
    super(type, size, byteBuffer);
  }

  public boolean isAudio() {
    return getSteamType() == AUDS;
  }

  public boolean isVideo() {
    return getSteamType() == VIDS;
  }

  public float getFrameRate() {
    return getRate() / (float)getScale();
  }

  public long getDurationUs() {
    return C.MICROS_PER_SECOND * getScale() * getLength() / getRate();
  }

  public int getSteamType() {
    return byteBuffer.getInt(0);
  }
  //4 - fourCC
  //8 - dwFlags
  //12 - wPriority
  //14 - wLanguage
  public int getInitialFrames() {
    return byteBuffer.getInt(16);
  }
  public int getScale() {
    return byteBuffer.getInt(20);
  }
  public int getRate() {
    return byteBuffer.getInt(24);
  }
  //28 - dwStart - doesn't seem to ever be set
  public int getLength() {
    return byteBuffer.getInt(32);
  }

  public int getSuggestedBufferSize() {
    return byteBuffer.getInt(36);
  }
  //40 - dwQuality
  //44 - dwSampleSize

  public String toString() {
    return "scale=" + getScale() + " rate=" + getRate() + " length=" + getLength() + " us=" + getDurationUs();
  }
}
