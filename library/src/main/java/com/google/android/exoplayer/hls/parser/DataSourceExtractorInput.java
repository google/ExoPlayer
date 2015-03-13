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

import com.google.android.exoplayer.hls.parser.HlsExtractor.ExtractorInput;
import com.google.android.exoplayer.upstream.DataSource;

import java.io.IOException;

/**
 * An {@link ExtractorInput} that wraps a {@link DataSource}.
 */
public final class DataSourceExtractorInput implements ExtractorInput {

  private static final byte[] SCRATCH_SPACE = new byte[4096];

  private final DataSource dataSource;

  private long position;
  private boolean isEnded;

  /**
   * @param dataSource The wrapped {@link DataSource}.
   * @param position The initial position in the stream.
   */
  public DataSourceExtractorInput(DataSource dataSource, long position) {
    this.dataSource = dataSource;
    this.position = position;
  }

  @Override
  public int read(byte[] target, int offset, int length) throws IOException, InterruptedException {
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
    int bytesRead = dataSource.read(target, offset, length);
    if (bytesRead == -1) {
      isEnded = true;
      return -1;
    }
    position += bytesRead;
    return bytesRead;
  }

  @Override
  public boolean readFully(byte[] target, int offset, int length)
      throws IOException, InterruptedException {
    int remaining = length;
    while (remaining > 0) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      int bytesRead = dataSource.read(target, offset, remaining);
      if (bytesRead == -1) {
        isEnded = true;
        return false;
      }
      offset += bytesRead;
      remaining -= bytesRead;
    }
    position += length;
    return true;
  }

  @Override
  public boolean skipFully(int length) throws IOException, InterruptedException {
    int remaining = length;
    while (remaining > 0) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      int bytesRead = dataSource.read(SCRATCH_SPACE, 0, remaining);
      if (bytesRead == -1) {
        isEnded = true;
        return true;
      }
      remaining -= bytesRead;
    }
    position += length;
    return false;
  }

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public boolean isEnded() {
    return isEnded;
  }

}
