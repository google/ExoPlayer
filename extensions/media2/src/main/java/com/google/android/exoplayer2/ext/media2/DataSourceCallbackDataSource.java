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

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media2.common.DataSourceCallback;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import java.io.EOFException;
import java.io.IOException;

/** An ExoPlayer {@link DataSource} for reading from a {@link DataSourceCallback}. */
/* package */ final class DataSourceCallbackDataSource extends BaseDataSource {

  /**
   * Returns a factory for {@link DataSourceCallbackDataSource}s.
   *
   * @return A factory for data sources that read from the data source callback.
   */
  public static DataSource.Factory getFactory(DataSourceCallback dataSourceCallback) {
    Assertions.checkNotNull(dataSourceCallback);
    return () -> new DataSourceCallbackDataSource(dataSourceCallback);
  }

  private final DataSourceCallback dataSourceCallback;

  @Nullable private Uri uri;
  private long position;
  private long bytesRemaining;
  private boolean opened;

  public DataSourceCallbackDataSource(DataSourceCallback dataSourceCallback) {
    super(/* isNetwork= */ false);
    this.dataSourceCallback = Assertions.checkNotNull(dataSourceCallback);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    uri = dataSpec.uri;
    position = dataSpec.position;
    transferInitializing(dataSpec);
    long dataSourceCallbackSize = dataSourceCallback.getSize();
    if (dataSpec.length != C.LENGTH_UNSET) {
      bytesRemaining = dataSpec.length;
    } else if (dataSourceCallbackSize != -1) {
      bytesRemaining = dataSourceCallbackSize - position;
    } else {
      bytesRemaining = C.LENGTH_UNSET;
    }
    opened = true;
    transferStarted(dataSpec);
    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    if (readLength == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }
    int bytesToRead =
        bytesRemaining == C.LENGTH_UNSET ? readLength : (int) Math.min(bytesRemaining, readLength);
    int bytesRead = dataSourceCallback.readAt(position, buffer, offset, bytesToRead);
    if (bytesRead == -1) {
      if (bytesRemaining != C.LENGTH_UNSET) {
        throw new EOFException();
      }
      return C.RESULT_END_OF_INPUT;
    }
    position += bytesRead;
    if (bytesRemaining != C.LENGTH_UNSET) {
      bytesRemaining -= bytesRead;
    }
    bytesTransferred(bytesRead);
    return bytesRead;
  }

  @Override
  @Nullable
  public Uri getUri() {
    return uri;
  }

  @Override
  public void close() {
    uri = null;
    if (opened) {
      opened = false;
      transferEnded();
    }
  }
}
