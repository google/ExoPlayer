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
package com.google.android.exoplayer.extractor;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.upstream.DataSource;

import java.io.EOFException;
import java.io.IOException;

/**
 * An {@link ExtractorInput} that wraps a {@link DataSource}.
 */
public final class DefaultExtractorInput implements ExtractorInput {

  private static final byte[] SCRATCH_SPACE = new byte[4096];

  private final DataSource dataSource;

  private long position;
  private long length;

  /**
   * @param dataSource The wrapped {@link DataSource}.
   * @param position The initial position in the stream.
   * @param length The length of the stream, or {@link C#LENGTH_UNBOUNDED} if it is unknown.
   */
  public DefaultExtractorInput(DataSource dataSource, long position, long length) {
    this.dataSource = dataSource;
    this.position = position;
    this.length = length;
  }

  @Override
  public int read(byte[] target, int offset, int length) throws IOException, InterruptedException {
    if (Thread.interrupted()) {
      throw new InterruptedException();
    }
    int bytesRead = dataSource.read(target, offset, length);
    if (bytesRead == C.RESULT_END_OF_INPUT) {
      return C.RESULT_END_OF_INPUT;
    }
    position += bytesRead;
    return bytesRead;
  }

  @Override
  public boolean readFully(byte[] target, int offset, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    int remaining = length;
    while (remaining > 0) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      int bytesRead = dataSource.read(target, offset, remaining);
      if (bytesRead == C.RESULT_END_OF_INPUT) {
        if (allowEndOfInput && remaining == length) {
          return false;
        }
        throw new EOFException();
      }
      offset += bytesRead;
      remaining -= bytesRead;
    }
    position += length;
    return true;
  }

  @Override
  public void readFully(byte[] target, int offset, int length)
      throws IOException, InterruptedException {
    readFully(target, offset, length, false);
  }

  @Override
  public void skipFully(int length) throws IOException, InterruptedException {
    int remaining = length;
    while (remaining > 0) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      int bytesRead = dataSource.read(SCRATCH_SPACE, 0, Math.min(SCRATCH_SPACE.length, remaining));
      if (bytesRead == C.RESULT_END_OF_INPUT) {
        throw new EOFException();
      }
      remaining -= bytesRead;
    }
    position += length;
  }

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public long getLength() {
    return length;
  }

}
