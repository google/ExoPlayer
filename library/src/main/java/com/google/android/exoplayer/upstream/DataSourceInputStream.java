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

import com.google.android.exoplayer.util.Assertions;

import java.io.IOException;
import java.io.InputStream;

/**
 * Allows data corresponding to a given {@link DataSpec} to be read from a {@link DataSource} and
 * consumed as an {@link InputStream}.
 */
public class DataSourceInputStream extends InputStream {

  private final DataSource dataSource;
  private final DataSpec dataSpec;
  private final byte[] singleByteArray;

  private boolean opened = false;
  private boolean closed = false;

  /**
   * @param dataSource The {@link DataSource} from which the data should be read.
   * @param dataSpec The {@link DataSpec} defining the data to be read from {@code dataSource}.
   */
  public DataSourceInputStream(DataSource dataSource, DataSpec dataSpec) {
    this.dataSource = dataSource;
    this.dataSpec = dataSpec;
    singleByteArray = new byte[1];
  }

  @Override
  public int read() throws IOException {
    read(singleByteArray);
    return singleByteArray[0];
  }

  @Override
  public int read(byte[] buffer) throws IOException {
    return read(buffer, 0, buffer.length);
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    Assertions.checkState(!closed);
    checkOpened();
    return dataSource.read(buffer, offset, length);
  }

  @Override
  public long skip(long byteCount) throws IOException {
    Assertions.checkState(!closed);
    checkOpened();
    return super.skip(byteCount);
  }

  @Override
  public void close() throws IOException {
    if (!closed) {
      dataSource.close();
      closed = true;
    }
  }

  private void checkOpened() throws IOException {
    if (!opened) {
      dataSource.open(dataSpec);
      opened = true;
    }
  }

}
