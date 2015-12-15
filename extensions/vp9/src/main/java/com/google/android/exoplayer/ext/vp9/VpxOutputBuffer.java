/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.google.android.exoplayer.ext.vp9;

import java.nio.ByteBuffer;

/**
 * OutputBuffer for storing the video frame.
 */
public final class VpxOutputBuffer {

  public static final int COLORSPACE_UNKNOWN = 0;
  public static final int COLORSPACE_BT601 = 1;
  public static final int COLORSPACE_BT709 = 2;

  /**
   * RGB buffer for RGB mode.
   */
  public ByteBuffer data;
  public long timestampUs;
  public int width;
  public int height;
  public int flags;
  /**
   * YUV planes for YUV mode.
   */
  public ByteBuffer[] yuvPlanes;
  public int[] yuvStrides;
  public int mode;
  public int colorspace;

  /**
   * This method is called from C++ through JNI after decoding is done. It will resize the
   * buffer based on the given dimensions.
   */
  public void initForRgbFrame(int width, int height) {
    this.width = width;
    this.height = height;
    int minimumRgbSize = width * height * 2;
    if (data == null || data.capacity() < minimumRgbSize) {
      data = ByteBuffer.allocateDirect(minimumRgbSize);
      yuvPlanes = null;
    }
    data.position(0);
    data.limit(minimumRgbSize);
  }

  /**
   * This method is called from C++ through JNI after decoding is done. It will resize the
   * buffer based on the given stride.
   */
  public void initForYuvFrame(int width, int height, int yStride, int uvStride, int colorspace) {
    this.width = width;
    this.height = height;
    this.colorspace = colorspace;
    int yLength = yStride * height;
    int uvLength = uvStride * ((height + 1) / 2);
    int minimumYuvSize = yLength + (uvLength * 2);
    if (data == null || data.capacity() < minimumYuvSize) {
      data = ByteBuffer.allocateDirect(minimumYuvSize);
    }
    data.limit(minimumYuvSize);
    if (yuvPlanes == null) {
      yuvPlanes = new ByteBuffer[3];
    }
    // Rewrapping has to be done on every frame since the stride might have changed.
    data.position(0);
    yuvPlanes[0] = data.slice();
    yuvPlanes[0].limit(yLength);
    data.position(yLength);
    yuvPlanes[1] = data.slice();
    yuvPlanes[1].limit(uvLength);
    data.position(yLength + uvLength);
    yuvPlanes[2] = data.slice();
    yuvPlanes[2].limit(uvLength);
    if (yuvStrides == null) {
      yuvStrides = new int[3];
    }
    yuvStrides[0] = yStride;
    yuvStrides[1] = uvStride;
    yuvStrides[2] = uvStride;
  }

}
