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

import com.google.android.exoplayer.upstream.Loader.Loadable;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Loads data from a {@link DataSource} into an in-memory {@link Allocation}. The loaded data
 * can be consumed by treating the instance as a non-blocking {@link NonBlockingInputStream}.
 */
public final class DataSourceStream implements Loadable, NonBlockingInputStream {

  /**
   * Thrown when an error is encountered trying to load data into a {@link DataSourceStream}.
   */
  public static class DataSourceStreamLoadException extends IOException {

    public DataSourceStreamLoadException(IOException cause) {
      super(cause);
    }

  }

  private final DataSource dataSource;
  private final DataSpec dataSpec;
  private final Allocator allocator;
  private final ReadHead readHead;

  private Allocation allocation;

  private volatile boolean loadCanceled;
  private volatile long loadPosition;
  private volatile long resolvedLength;

  private int writeFragmentIndex;
  private int writeFragmentOffset;
  private int writeFragmentRemainingLength;

  /**
   * @param dataSource The source from which the data should be loaded.
   * @param dataSpec Defines the data to be loaded. {@code dataSpec.length} must not exceed
   *     {@link Integer#MAX_VALUE}. If {@code dataSpec.length == DataSpec.LENGTH_UNBOUNDED} then
   *     the length resolved by {@code dataSource.open(dataSpec)} must not exceed
   *     {@link Integer#MAX_VALUE}.
   * @param allocator Used to obtain an {@link Allocation} for holding the data.
   */
  public DataSourceStream(DataSource dataSource, DataSpec dataSpec, Allocator allocator) {
    Assertions.checkState(dataSpec.length <= Integer.MAX_VALUE);
    this.dataSource = dataSource;
    this.dataSpec = dataSpec;
    this.allocator = allocator;
    resolvedLength = DataSpec.LENGTH_UNBOUNDED;
    readHead = new ReadHead();
  }

  /**
   * Resets the read position to the start of the data.
   */
  public void resetReadPosition() {
    readHead.reset();
  }

  /**
   * Returns the current read position for data being read out of the source.
   *
   * @return The current read position.
   */
  public long getReadPosition() {
    return readHead.position;
  }

  /**
   * Returns the number of bytes of data that have been loaded.
   *
   * @return The number of bytes of data that have been loaded.
   */
  public long getLoadPosition() {
    return loadPosition;
  }

  /**
   * Returns the length of the streamin bytes.
   *
   * @return The length of the stream in bytes, or {@value DataSpec#LENGTH_UNBOUNDED} if the length
   *     has yet to be determined.
   */
  public long getLength() {
    return resolvedLength != DataSpec.LENGTH_UNBOUNDED ? resolvedLength : dataSpec.length;
  }

  /**
   * Whether the stream has finished loading.
   *
   * @return True if the stream has finished loading. False otherwise.
   */
  public boolean isLoadFinished() {
    return resolvedLength != DataSpec.LENGTH_UNBOUNDED && loadPosition == resolvedLength;
  }

  /**
   * Returns a byte array containing the loaded data. If the data is partially loaded, this method
   * returns the portion of the data that has been loaded so far. If nothing has been loaded, null
   * is returned. This method does not use or update the current read position.
   * <p>
   * Note: The read methods provide a more efficient way of consuming the loaded data. Use this
   * method only when a freshly allocated byte[] containing all of the loaded data is required.
   *
   * @return The loaded data or null.
   */
  public final byte[] getLoadedData() {
    if (loadPosition == 0) {
      return null;
    }

    byte[] rawData = new byte[(int) loadPosition];
    read(null, rawData, 0, new ReadHead(), rawData.length);
    return rawData;
  }

  // {@link NonBlockingInputStream} implementation.

  @Override
  public long getAvailableByteCount() {
    return loadPosition - readHead.position;
  }

  @Override
  public boolean isEndOfStream() {
    return resolvedLength != DataSpec.LENGTH_UNBOUNDED && readHead.position == resolvedLength;
  }

  @Override
  public void close() {
    if (allocation != null) {
      allocation.release();
      allocation = null;
    }
  }

  @Override
  public int skip(int skipLength) {
    return read(null, null, 0, readHead, skipLength);
  }

  @Override
  public int read(ByteBuffer target1, int readLength) {
    return read(target1, null, 0, readHead, readLength);
  }

  @Override
  public int read(byte[] target, int offset, int readLength) {
    return read(null, target, offset, readHead, readLength);
  }

  /**
   * Reads data to either a target {@link ByteBuffer}, or to a target byte array at a specified
   * offset. The {@code readHead} is updated to reflect the read that was performed.
   */
  private int read(ByteBuffer target, byte[] targetArray, int targetArrayOffset,
      ReadHead readHead, int readLength) {
    if (readHead.position == dataSpec.length) {
      return -1;
    }
    int bytesToRead = (int) Math.min(loadPosition - readHead.position, readLength);
    if (bytesToRead == 0) {
      return 0;
    }
    if (readHead.position == 0) {
      readHead.fragmentIndex = 0;
      readHead.fragmentOffset = allocation.getFragmentOffset(0);
      readHead.fragmentRemaining = allocation.getFragmentLength(0);
    }
    int bytesRead = 0;
    byte[][] buffers = allocation.getBuffers();
    while (bytesRead < bytesToRead) {
      int bufferReadLength = Math.min(readHead.fragmentRemaining, bytesToRead - bytesRead);
      if (target != null) {
        target.put(buffers[readHead.fragmentIndex], readHead.fragmentOffset, bufferReadLength);
      } else if (targetArray != null) {
        System.arraycopy(buffers[readHead.fragmentIndex], readHead.fragmentOffset, targetArray,
            targetArrayOffset, bufferReadLength);
        targetArrayOffset += bufferReadLength;
      }
      readHead.position += bufferReadLength;
      bytesRead += bufferReadLength;
      readHead.fragmentOffset += bufferReadLength;
      readHead.fragmentRemaining -= bufferReadLength;
      if (readHead.fragmentRemaining == 0 && readHead.position < resolvedLength) {
        readHead.fragmentIndex++;
        readHead.fragmentOffset = allocation.getFragmentOffset(readHead.fragmentIndex);
        readHead.fragmentRemaining = allocation.getFragmentLength(readHead.fragmentIndex);
      }
    }

    return bytesRead;
  }

  // {@link Loadable} implementation.

  @Override
  public void cancelLoad() {
    loadCanceled = true;
  }

  @Override
  public boolean isLoadCanceled() {
    return loadCanceled;
  }

  @Override
  public void load() throws IOException, InterruptedException {
    if (loadCanceled || isLoadFinished()) {
      // The load was canceled, or is already complete.
      return;
    }
    try {
      DataSpec loadDataSpec;
      if (resolvedLength == DataSpec.LENGTH_UNBOUNDED) {
        loadDataSpec = dataSpec;
        resolvedLength = dataSource.open(loadDataSpec);
        if (resolvedLength > Integer.MAX_VALUE) {
          throw new DataSourceStreamLoadException(
              new UnexpectedLengthException(dataSpec.length, resolvedLength));
        }
      } else {
        loadDataSpec = new DataSpec(dataSpec.uri, dataSpec.position + loadPosition,
            resolvedLength - loadPosition, dataSpec.key);
        dataSource.open(loadDataSpec);
      }
      if (allocation == null) {
        allocation = allocator.allocate((int) resolvedLength);
      }
      if (loadPosition == 0) {
        writeFragmentIndex = 0;
        writeFragmentOffset = allocation.getFragmentOffset(0);
        writeFragmentRemainingLength = allocation.getFragmentLength(0);
      }

      int read = Integer.MAX_VALUE;
      byte[][] buffers = allocation.getBuffers();
      while (!loadCanceled && loadPosition < resolvedLength && read > 0) {
        if (Thread.interrupted()) {
          throw new InterruptedException();
        }
        int writeLength = (int) Math.min(writeFragmentRemainingLength,
            resolvedLength - loadPosition);
        read = dataSource.read(buffers[writeFragmentIndex], writeFragmentOffset, writeLength);
        if (read > 0) {
          loadPosition += read;
          writeFragmentOffset += read;
          writeFragmentRemainingLength -= read;
          if (writeFragmentRemainingLength == 0 && loadPosition < resolvedLength) {
            writeFragmentIndex++;
            writeFragmentOffset = allocation.getFragmentOffset(writeFragmentIndex);
            writeFragmentRemainingLength = allocation.getFragmentLength(writeFragmentIndex);
          }
        } else if (resolvedLength != loadPosition) {
          throw new DataSourceStreamLoadException(
              new UnexpectedLengthException(resolvedLength, loadPosition));
        }
      }
    } finally {
      Util.closeQuietly(dataSource);
    }
  }

  private static class ReadHead {

    private int position;
    private int fragmentIndex;
    private int fragmentOffset;
    private int fragmentRemaining;

    public void reset() {
      position = 0;
      fragmentIndex = 0;
      fragmentOffset = 0;
      fragmentRemaining = 0;
    }

  }

}
