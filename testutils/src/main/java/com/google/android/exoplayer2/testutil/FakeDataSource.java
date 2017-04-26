/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.testutil;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * A fake {@link DataSource} capable of simulating various scenarios. It uses a {@link FakeDataSet}
 * instance which determines the response to data access calls.
 *
 * <p>Multiple fake data can be defined by {@link FakeDataSet#setData(String, byte[])} and {@link
 * FakeDataSet#newData(String)} methods. It's also possible to define a default data by {@link
 * FakeDataSet#newDefaultData()}.
 *
 * <p>{@link FakeDataSet#newData(String)} and {@link FakeDataSet#newDefaultData()} return a {@link
 * FakeData} instance which can be used to define specific results during {@link #read(byte[], int,
 * int)} calls.
 *
 * <p>The data that will be read from the source can be constructed by calling {@link
 * FakeData#appendReadData(byte[])} Calls to {@link #read(byte[], int, int)} will not span the
 * boundaries between arrays passed to successive calls, and hence the boundaries control the
 * positions at which read requests to the source may only be partially satisfied.
 *
 * <p>Errors can be inserted by calling {@link FakeData#appendReadError(IOException)}. An inserted
 * error will be thrown from the first call to {@link #read(byte[], int, int)} that attempts to read
 * from the corresponding position, and from all subsequent calls to {@link #read(byte[], int, int)}
 * until the source is closed. If the source is closed and re-opened having encountered an error,
 * that error will not be thrown again.
 *
 * <p>Example usage:
 *
 * <pre>
 *   // Create a FakeDataSource then add default data and two FakeData
 *   // "test_file" throws an IOException when tried to be read until closed and reopened.
 *   FakeDataSource fakeDataSource = new FakeDataSource();
 *   fakeDataSource.getDataSet()
 *       .newDefaultData()
 *         .appendReadData(defaultData)
 *         .endData()
 *       .setData("http:///1", data1)
 *       .newData("test_file")
 *         .appendReadError(new IOException())
 *         .appendReadData(data2);
 *    // No need to call endData at the end
 * </pre>
 */
public final class FakeDataSource implements DataSource {

  private final FakeDataSet fakeDataSet;
  private final ArrayList<DataSpec> openedDataSpecs;

  private Uri uri;
  private boolean opened;
  private FakeData fakeData;
  private int currentSegmentIndex;
  private long bytesRemaining;

  public static Factory newFactory(final FakeDataSet fakeDataSet) {
    return new Factory() {
      @Override
      public DataSource createDataSource() {
        return new FakeDataSource(fakeDataSet);
      }
    };
  }

  public FakeDataSource() {
    this(new FakeDataSet());
  }

  public FakeDataSource(FakeDataSet fakeDataSet) {
    this.fakeDataSet = fakeDataSet;
    this.openedDataSpecs = new ArrayList<>();
  }

  public FakeDataSet getDataSet() {
    return fakeDataSet;
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    Assertions.checkState(!opened);
    // DataSpec requires a matching close call even if open fails.
    opened = true;
    uri = dataSpec.uri;
    openedDataSpecs.add(dataSpec);

    fakeData = fakeDataSet.getData(uri.toString());
    if (fakeData == null) {
      throw new IOException("Data not found: " + dataSpec.uri);
    }

    long totalLength = 0;
    for (Segment segment : fakeData.segments) {
      totalLength += segment.length;
    }

    if (totalLength == 0) {
      throw new IOException("Data is empty: " + dataSpec.uri);
    }

    // If the source knows that the request is unsatisfiable then fail.
    if (dataSpec.position >= totalLength || (dataSpec.length != C.LENGTH_UNSET
        && (dataSpec.position + dataSpec.length > totalLength))) {
      throw new DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE);
    }
    // Scan through the segments, configuring them for the current read.
    boolean findingCurrentSegmentIndex = true;
    currentSegmentIndex = 0;
    int scannedLength = 0;
    for (Segment segment : fakeData.segments) {
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
    if (dataSpec.length == C.LENGTH_UNSET) {
      bytesRemaining = totalLength - dataSpec.position;
      return fakeData.simulateUnknownLength ? C.LENGTH_UNSET : bytesRemaining;
    } else {
      bytesRemaining = dataSpec.length;
      return bytesRemaining;
    }
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    Assertions.checkState(opened);
    while (true) {
      if (currentSegmentIndex == fakeData.segments.size() || bytesRemaining == 0) {
        return C.RESULT_END_OF_INPUT;
      }
      Segment current = fakeData.segments.get(currentSegmentIndex);
      if (current.isErrorSegment()) {
        if (!current.exceptionCleared) {
          current.exceptionThrown = true;
          throw (IOException) current.exception.fillInStackTrace();
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

  @Override
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws IOException {
    Assertions.checkState(opened);
    opened = false;
    uri = null;
    if (currentSegmentIndex < fakeData.segments.size()) {
      Segment current = fakeData.segments.get(currentSegmentIndex);
      if (current.isErrorSegment() && current.exceptionThrown) {
        current.exceptionCleared = true;
      }
    }
    fakeData = null;
  }

  /**
   * Returns the {@link DataSpec} instances passed to {@link #open(DataSpec)} since the last call to
   * this method.
   */
  public DataSpec[] getAndClearOpenedDataSpecs() {
    DataSpec[] dataSpecs = new DataSpec[openedDataSpecs.size()];
    openedDataSpecs.toArray(dataSpecs);
    openedDataSpecs.clear();
    return dataSpecs;
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

  /** Container of fake data to be served by a {@link FakeDataSource}. */
  public static final class FakeData {

    /** Uri of the data or null if this is the default FakeData. */
    public final String uri;
    private final ArrayList<Segment> segments;
    private final FakeDataSet dataSet;
    private boolean simulateUnknownLength;

    private FakeData(FakeDataSet dataSet, String uri) {
      this.uri = uri;
      this.segments = new ArrayList<>();
      this.dataSet = dataSet;
    }

    /** Returns the {@link FakeDataSet} this FakeData belongs to. */
    public FakeDataSet endData() {
      return dataSet;
    }

    /**
     * When set, {@link FakeDataSource#open(DataSpec)} will behave as though the source is unable to
     * determine the length of the underlying data. Hence the return value will always be equal to
     * the {@link DataSpec#length} of the argument, including the case where the length is equal to
     * {@link C#LENGTH_UNSET}.
     */
    public FakeData setSimulateUnknownLength(boolean simulateUnknownLength) {
      this.simulateUnknownLength = simulateUnknownLength;
      return this;
    }

    /**
     * Appends to the underlying data.
     */
    public FakeData appendReadData(byte[] data) {
      Assertions.checkState(data != null && data.length > 0);
      segments.add(new Segment(data, null));
      return this;
    }

    /**
     * Appends an error in the underlying data.
     */
    public FakeData appendReadError(IOException exception) {
      segments.add(new Segment(null, exception));
      return this;
    }

    /** Returns the whole data added by {@link #appendReadData(byte[])}. */
    public byte[] getData() {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      for (Segment segment : segments) {
        if (segment.data != null) {
          try {
            outputStream.write(segment.data);
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        }
      }
      return outputStream.toByteArray();
    }
  }

  /** A set of {@link FakeData} instances. */
  public static final class FakeDataSet {

    private final HashMap<String, FakeData> dataMap;
    private FakeData defaultData;

    public FakeDataSet() {
      dataMap = new HashMap<>();
    }

    public FakeData newDefaultData() {
      defaultData = new FakeData(this, null);
      return defaultData;
    }

    public FakeData newData(String uri) {
      FakeData data = new FakeData(this, uri);
      dataMap.put(uri, data);
      return data;
    }

    public FakeDataSet setData(String uri, byte[] data) {
      return newData(uri).appendReadData(data).endData();
    }

    public FakeData getData(String uri) {
      FakeData data = dataMap.get(uri);
      return data != null ? data : defaultData;
    }

    public ArrayList<FakeData> getAllData() {
      ArrayList<FakeData> fakeDatas = new ArrayList<>(dataMap.values());
      if (defaultData != null) {
        fakeDatas.add(defaultData);
      }
      return fakeDatas;
    }
  }

}
