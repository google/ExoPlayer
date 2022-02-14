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
import com.google.android.exoplayer2.util.MimeTypes;
import java.nio.ByteBuffer;
import java.util.HashMap;

/**
 * Wrapper around the BITMAPINFOHEADER structure
 */
public class VideoFormat {

  static final int XVID = 'X' | ('V' << 8) | ('I' << 16) | ('D' << 24);

  private static final HashMap<Integer, String> STREAM_MAP = new HashMap<>();

  static {
    //Although other types are technically supported, AVI is almost exclusively MP4V and MJPEG
    final String mimeType = MimeTypes.VIDEO_MP4V;

    //I've never seen an Android devices that actually supports MP42
    STREAM_MAP.put('M' | ('P' << 8) | ('4' << 16) | ('2' << 24), MimeTypes.VIDEO_MP42);
    //Samsung seems to support the rare MP43.
    STREAM_MAP.put('M' | ('P' << 8) | ('4' << 16) | ('3' << 24), MimeTypes.VIDEO_MP43);
    STREAM_MAP.put('H' | ('2' << 8) | ('6' << 16) | ('4' << 24), MimeTypes.VIDEO_H264);
    STREAM_MAP.put('a' | ('v' << 8) | ('c' << 16) | ('1' << 24), MimeTypes.VIDEO_H264);
    STREAM_MAP.put('A' | ('V' << 8) | ('C' << 16) | ('1' << 24), MimeTypes.VIDEO_H264);
    STREAM_MAP.put('3' | ('V' << 8) | ('I' << 16) | ('D' << 24), mimeType);
    STREAM_MAP.put('d' | ('i' << 8) | ('v' << 16) | ('x' << 24), mimeType);
    STREAM_MAP.put('D' | ('I' << 8) | ('V' << 16) | ('X' << 24), mimeType);
    STREAM_MAP.put('D' | ('X' << 8) | ('5' << 16) | ('0' << 24), mimeType);
    STREAM_MAP.put('F' | ('M' << 8) | ('P' << 16) | ('4' << 24), mimeType);
    STREAM_MAP.put('x' | ('v' << 8) | ('i' << 16) | ('d' << 24), mimeType);
    STREAM_MAP.put(XVID, mimeType);
    STREAM_MAP.put('M' | ('J' << 8) | ('P' << 16) | ('G' << 24), MimeTypes.VIDEO_MJPEG);
    STREAM_MAP.put('m' | ('j' << 8) | ('p' << 16) | ('g' << 24), MimeTypes.VIDEO_MJPEG);
  }

  private final ByteBuffer byteBuffer;

  public VideoFormat(final ByteBuffer byteBuffer) {
    this.byteBuffer = byteBuffer;
  }

  // 0 - biSize - (uint)

  public int getWidth() {
    return byteBuffer.getInt(4);
  }
  public int getHeight() {
    return byteBuffer.getInt(8);
  }
  // 12 - biPlanes
  // 14 - biBitCount
  public int getCompression() {
    return byteBuffer.getInt(16);
  }

  public String getMimeType() {
    return STREAM_MAP.get(getCompression());
  }

  @VisibleForTesting
  public void setWidth(final int width) {
    byteBuffer.putInt(4, width);
  }

  @VisibleForTesting
  public void setHeight(final int height) {
    byteBuffer.putInt(8, height);
  }

  @VisibleForTesting
  public void setCompression(final int compression) {
    byteBuffer.putInt(16, compression);
  }
}
