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
package com.google.android.exoplayer.upstream;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.util.Assertions;

import java.io.IOException;

/**
 * A {@link DataSource} for reading from a byte array.
 */
public class ByteArrayDataSource implements DataSource {

  private final byte[] data;
  private int readPosition;

  /**
   * @param data The data to be read.
   */
  public ByteArrayDataSource(byte[] data) {
    this.data = Assertions.checkNotNull(data);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    if (dataSpec.length == C.LENGTH_UNBOUNDED) {
      Assertions.checkArgument(dataSpec.position < data.length);
    } else {
      Assertions.checkArgument(dataSpec.position + dataSpec.length <= data.length);
    }
    readPosition = (int) dataSpec.position;
    return (dataSpec.length == C.LENGTH_UNBOUNDED) ? (data.length - dataSpec.position)
        : dataSpec.length;
  }

  @Override
  public void close() throws IOException {
    // Do nothing.
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    System.arraycopy(data, readPosition, buffer, offset, length);
    readPosition += length;
    return length;
  }
}

