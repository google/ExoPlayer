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
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.testutil.FakeDataSet.FakeData;
import com.google.android.exoplayer2.testutil.FakeDataSet.FakeData.Segment;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;

/**
 * A fake {@link DataSource} capable of simulating various scenarios. It uses a {@link FakeDataSet}
 * instance which determines the response to data access calls.
 */
public class FakeDataSource implements DataSource {

  /**
   * Factory to create a {@link FakeDataSource}.
   */
  public static class Factory implements DataSource.Factory {

    protected final TransferListener<? super FakeDataSource> transferListener;
    protected FakeDataSet fakeDataSet;

    public Factory(@Nullable TransferListener<? super FakeDataSource> transferListener) {
      this.transferListener = transferListener;
    }

    public final Factory setFakeDataSet(FakeDataSet fakeDataSet) {
      this.fakeDataSet = fakeDataSet;
      return this;
    }

    @Override
    public DataSource createDataSource() {
      return new FakeDataSource(fakeDataSet, transferListener);
    }

  }

  private final FakeDataSet fakeDataSet;
  private final TransferListener<? super FakeDataSource> transferListener;
  private final ArrayList<DataSpec> openedDataSpecs;

  private Uri uri;
  private boolean opened;
  private FakeData fakeData;
  private int currentSegmentIndex;
  private long bytesRemaining;

  public FakeDataSource() {
    this(new FakeDataSet());
  }

  public FakeDataSource(FakeDataSet fakeDataSet) {
    this(fakeDataSet, null);
  }

  public FakeDataSource(FakeDataSet fakeDataSet,
      @Nullable TransferListener<? super FakeDataSource> transferListener) {
    Assertions.checkNotNull(fakeDataSet);
    this.fakeDataSet = fakeDataSet;
    this.transferListener = transferListener;
    this.openedDataSpecs = new ArrayList<>();
  }

  public final FakeDataSet getDataSet() {
    return fakeDataSet;
  }

  @Override
  public final long open(DataSpec dataSpec) throws IOException {
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
    for (Segment segment : fakeData.getSegments()) {
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
    for (Segment segment : fakeData.getSegments()) {
      segment.bytesRead =
          (int) Math.min(Math.max(0, dataSpec.position - scannedLength), segment.length);
      scannedLength += segment.length;
      findingCurrentSegmentIndex &= segment.isErrorSegment() ? segment.exceptionCleared
          : (!segment.isActionSegment() && segment.bytesRead == segment.length);
      if (findingCurrentSegmentIndex) {
        currentSegmentIndex++;
      }
    }
    if (transferListener != null) {
      transferListener.onTransferStart(this, dataSpec);
    }
    // Configure bytesRemaining, and return.
    if (dataSpec.length == C.LENGTH_UNSET) {
      bytesRemaining = totalLength - dataSpec.position;
      return fakeData.isSimulatingUnknownLength() ? C.LENGTH_UNSET : bytesRemaining;
    } else {
      bytesRemaining = dataSpec.length;
      return bytesRemaining;
    }
  }

  @Override
  public final int read(byte[] buffer, int offset, int readLength) throws IOException {
    Assertions.checkState(opened);
    while (true) {
      if (currentSegmentIndex == fakeData.getSegments().size() || bytesRemaining == 0) {
        return C.RESULT_END_OF_INPUT;
      }
      Segment current = fakeData.getSegments().get(currentSegmentIndex);
      if (current.isErrorSegment()) {
        if (!current.exceptionCleared) {
          current.exceptionThrown = true;
          throw (IOException) current.exception.fillInStackTrace();
        } else {
          currentSegmentIndex++;
        }
      } else if (current.isActionSegment()) {
        currentSegmentIndex++;
        current.action.run();
      } else {
        // Read at most bytesRemaining.
        readLength = (int) Math.min(readLength, bytesRemaining);
        // Do not allow crossing of the segment boundary.
        readLength = Math.min(readLength, current.length - current.bytesRead);
        // Perform the read and return.
        if (current.data != null) {
          System.arraycopy(current.data, current.bytesRead, buffer, offset, readLength);
        }
        onDataRead(readLength);
        if (transferListener != null) {
          transferListener.onBytesTransferred(this, readLength);
        }
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
  public final Uri getUri() {
    return uri;
  }

  @Override
  public final void close() throws IOException {
    Assertions.checkState(opened);
    opened = false;
    uri = null;
    if (fakeData != null && currentSegmentIndex < fakeData.getSegments().size()) {
      Segment current = fakeData.getSegments().get(currentSegmentIndex);
      if (current.isErrorSegment() && current.exceptionThrown) {
        current.exceptionCleared = true;
      }
    }
    if (transferListener != null) {
      transferListener.onTransferEnd(this);
    }
    fakeData = null;
  }

  /**
   * Returns the {@link DataSpec} instances passed to {@link #open(DataSpec)} since the last call to
   * this method.
   */
  public final DataSpec[] getAndClearOpenedDataSpecs() {
    DataSpec[] dataSpecs = new DataSpec[openedDataSpecs.size()];
    openedDataSpecs.toArray(dataSpecs);
    openedDataSpecs.clear();
    return dataSpecs;
  }

  protected void onDataRead(int bytesRead) {
    // Do nothing. Can be overridden.
  }

}
