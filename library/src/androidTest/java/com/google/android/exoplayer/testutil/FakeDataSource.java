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
package com.google.android.exoplayer.testutil;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.Assertions;

import java.io.IOException;
import java.util.ArrayList;

/**
 * A fake {@link DataSource} capable of simulating various scenarios.
 * <p>
 * The data that will be read from the source can be constructed by calling
 * {@link Builder#appendReadData(byte[])}. Calls to {@link #read(byte[], int, int)} will not span
 * the boundaries between arrays passed to successive calls, and hence the boundaries control the
 * positions at which read requests to the source may only be partially satisfied.
 * <p>
 * Errors can be inserted by calling {@link Builder#appendReadError(IOException)}. An inserted error
 * will be thrown from the first call to {@link #read(byte[], int, int)} that attempts to read from
 * the corresponding position, and from all subsequent calls to {@link #read(byte[], int, int)}
 * until the source is closed. If the source is closed and re-opened having encountered an error,
 * that error will not be thrown again.
 */
public final class FakeDataSource implements DataSource {

  private final ArrayList<Segment> segments;

  private final boolean simulateUnknownLength;
  private final long totalLength;

  private boolean opened;
  private int currentSegmentIndex;
  private long bytesRemaining;

  private FakeDataSource(boolean simulateUnknownLength, ArrayList<Segment> segments) {
    this.simulateUnknownLength = simulateUnknownLength;
    this.segments = segments;
    long totalLength = 0;
    for (Segment segment : segments) {
      totalLength += segment.length;
    }
    this.totalLength = totalLength;
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    Assertions.checkState(!opened);
    // DataSpec requires a matching close call even if open fails.
    opened = true;
    // If the source knows that the request is unsatisfiable then fail.
    if (dataSpec.position >= totalLength) {
      throw new IOException("Unsatisfiable position");
    } else if (dataSpec.length != C.LENGTH_UNBOUNDED
        && dataSpec.position + dataSpec.length >= totalLength) {
      throw new IOException("Unsatisfiable range");
    }
    // Scan through the segments, configuring them for the current read.
    boolean findingCurrentSegmentIndex = true;
    currentSegmentIndex = 0;
    int scannedLength = 0;
    for (Segment segment : segments) {
      segment.bytesRead =
          (int) Math.min(Math.max(0, dataSpec.position - scannedLength), segment.length);
      scannedLength += segment.length;
      findingCurrentSegmentIndex &= segment.isErrorSegment() ? segment.exceptionCleared
          : segment.bytesRead == segment.length;
      if (findingCurrentSegmentIndex) {
        currentSegmentIndex++;
      }
    }
    // Configure bytesRemaining, and return.
    if (dataSpec.length == C.LENGTH_UNBOUNDED) {
      bytesRemaining = totalLength - dataSpec.position;
      return simulateUnknownLength ? C.LENGTH_UNBOUNDED : bytesRemaining;
    } else {
      bytesRemaining = dataSpec.length;
      return bytesRemaining;
    }
  }

  @Override
  public void close() throws IOException {
    Assertions.checkState(opened);
    opened = false;
    if (currentSegmentIndex < segments.size()) {
      Segment current = segments.get(currentSegmentIndex);
      if (current.isErrorSegment() && current.exceptionThrown) {
        current.exceptionCleared = true;
      }
    }
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    Assertions.checkState(opened);
    while (true) {
      if (currentSegmentIndex == segments.size() || bytesRemaining == 0) {
        return -1;
      }
      Segment current = segments.get(currentSegmentIndex);
      if (current.exception != null) {
        if (!current.exceptionCleared) {
          current.exceptionThrown = true;
          throw current.exception;
        } else {
          currentSegmentIndex++;
        }
      } else {
        // Read at most bytesRemaining.
        readLength = (int) Math.min(readLength, bytesRemaining);
        // Do not allow crossing of the segment boundary.
        readLength = Math.min(readLength, current.length - current.bytesRead);
        // Perform the read and return.
        System.arraycopy(current.data, current.bytesRead, buffer, offset, readLength);
        bytesRemaining -= readLength;
        current.bytesRead += readLength;
        if (current.bytesRead == current.length) {
          currentSegmentIndex++;
        }
        return readLength;
      }
    }
  }

  private static class Segment {

    public final IOException exception;
    public final byte[] data;
    public final int length;

    private boolean exceptionThrown;
    private boolean exceptionCleared;
    private int bytesRead;

    public Segment(byte[] data, IOException exception) {
      this.data = data;
      this.exception = exception;
      length = data != null ? data.length : 0;
    }

    public boolean isErrorSegment() {
      return exception != null;
    }

  }

  /**
   * Builder of {@link FakeDataSource} instances.
   */
  public static final class Builder {

    private final ArrayList<Segment> segments;
    private boolean simulateUnknownLength;

    public Builder() {
      segments = new ArrayList<>();
    }

    /**
     * When set, {@link FakeDataSource#open(DataSpec)} will behave as though the source is unable to
     * determine the length of the underlying data. Hence the return value will always be equal to
     * the {@link DataSpec#length} of the argument, including the case where the length is equal to
     * {@link C#LENGTH_UNBOUNDED}.
     */
    public Builder setSimulateUnknownLength(boolean simulateUnknownLength) {
      this.simulateUnknownLength = simulateUnknownLength;
      return this;
    }

    /**
     * Appends to the underlying data.
     */
    public Builder appendReadData(byte[] data) {
      Assertions.checkState(data != null && data.length > 0);
      segments.add(new Segment(data, null));
      return this;
    }

    /**
     * Appends an error in the underlying data.
     */
    public Builder appendReadError(IOException exception) {
      segments.add(new Segment(null, exception));
      return this;
    }

    public FakeDataSource build() {
      return new FakeDataSource(simulateUnknownLength, segments);
    }

  }

}
