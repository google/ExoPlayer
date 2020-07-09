/*
 * Copyright 2019 The Android Open Source Project
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

package com.google.android.exoplayer2.ext.media2;

import android.content.res.AssetFileDescriptor;
import android.util.Log;
import androidx.media2.common.DataSourceCallback;
import java.io.IOException;
import java.io.InputStream;

/** A DataSourceCallback that reads from a byte array for use in tests. */
public class TestDataSourceCallback extends DataSourceCallback {
  private static final String TAG = "TestDataSourceCallback";

  private byte[] data;

  // Read an asset fd into a new byte array media item. Closes afd.
  public static TestDataSourceCallback fromAssetFd(AssetFileDescriptor afd) throws IOException {
    try {
      InputStream in = afd.createInputStream();
      int size = (int) afd.getDeclaredLength();
      byte[] data = new byte[size];
      int writeIndex = 0;
      int numRead;
      do {
        numRead = in.read(data, writeIndex, size - writeIndex);
        writeIndex += numRead;
      } while (numRead >= 0);
      return new TestDataSourceCallback(data);
    } finally {
      afd.close();
    }
  }

  public TestDataSourceCallback(byte[] data) {
    this.data = data;
  }

  @Override
  public synchronized int readAt(long position, byte[] buffer, int offset, int size) {
    // Clamp reads past the end of the source.
    if (position >= data.length) {
      return -1; // -1 indicates EOF
    }
    if (position + size > data.length) {
      size -= (position + size) - data.length;
    }
    System.arraycopy(data, (int) position, buffer, offset, size);
    return size;
  }

  @Override
  public synchronized long getSize() {
    Log.v(TAG, "getSize: " + data.length);
    return data.length;
  }

  // Note: it's fine to keep using this media item after closing it.
  @Override
  public synchronized void close() {
    Log.v(TAG, "close()");
  }
}
