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

/**
 * A data source that fetches data from a local or remote {@link DataSpec}.
 */
public final class UriDataSource implements DataSource {

  private static final String FILE_URI_SCHEME = "file";

  private final DataSource fileDataSource;
  private final DataSource httpDataSource;

  /**
   * {@code null} if no data source is open. Otherwise, equal to {@link #fileDataSource} if the open
   * data source is a file, or {@link #httpDataSource} otherwise.
   */
  private DataSource dataSource;

  /**
   * Constructs a new data source that delegates to a {@link FileDataSource} for file URIs and an
   * {@link HttpDataSource} for other URIs.
   *
   * @param userAgent The User-Agent string that should be used when requesting remote data.
   * @param transferListener An optional listener.
   */
  public UriDataSource(String userAgent, TransferListener transferListener) {
    this(new FileDataSource(transferListener),
        new HttpDataSource(userAgent, null, transferListener));
  }

  /**
   * Constructs a new data source using {@code fileDataSource} for file URIs, and
   * {@code httpDataSource} for non-file URIs.
   *
   * @param fileDataSource {@link DataSource} to use for file URIs.
   * @param httpDataSource {@link DataSource} to use for non-file URIs.
   */
  public UriDataSource(DataSource fileDataSource, DataSource httpDataSource) {
    this.fileDataSource = Assertions.checkNotNull(fileDataSource);
    this.httpDataSource = Assertions.checkNotNull(httpDataSource);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    Assertions.checkState(dataSource == null);
    dataSource = FILE_URI_SCHEME.equals(dataSpec.uri.getScheme()) ? fileDataSource : httpDataSource;
    return dataSource.open(dataSpec);
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    return dataSource.read(buffer, offset, readLength);
  }

  @Override
  public void close() throws IOException {
    if (dataSource != null) {
      dataSource.close();
      dataSource = null;
    }
  }

}
