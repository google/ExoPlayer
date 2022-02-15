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

  static final int XVID = 0x44495658; // XVID

  private static final HashMap<Integer, String> STREAM_MAP = new HashMap<>();

  static {
    //Although other types are technically supported, AVI is almost exclusively MP4V and MJPEG
    final String mimeType = MimeTypes.VIDEO_MP4V;

    //I've never seen an Android devices that actually supports MP42
    STREAM_MAP.put(0x3234504d, MimeTypes.VIDEO_MP42); // MP42
    //Samsung seems to support the rare MP43.
    STREAM_MAP.put(0x3334504d, MimeTypes.VIDEO_MP43); // MP43
    STREAM_MAP.put(0x34363248, MimeTypes.VIDEO_H264); // H264
    STREAM_MAP.put(0x31637661, MimeTypes.VIDEO_H264); // avc1
    STREAM_MAP.put(0x31435641, MimeTypes.VIDEO_H264); // AVC1
    STREAM_MAP.put(0x44495633, mimeType); // 3VID
    STREAM_MAP.put(0x78766964, mimeType); // divx
    STREAM_MAP.put(0x58564944, mimeType); // DIVX
    STREAM_MAP.put(0x30355844, mimeType); // DX50
    STREAM_MAP.put(0x34504d46, mimeType); // FMP4
    STREAM_MAP.put(0x64697678, mimeType); // xvid
    STREAM_MAP.put(XVID, mimeType); // XVID
    STREAM_MAP.put(0x47504a4d, MimeTypes.VIDEO_MJPEG); // MJPG
    STREAM_MAP.put(0x67706a6d, MimeTypes.VIDEO_MJPEG); // mjpg
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
