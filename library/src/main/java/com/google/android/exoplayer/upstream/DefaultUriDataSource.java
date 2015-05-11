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
public final class DefaultUriDataSource implements UriDataSource {

  private static final String FILE_URI_SCHEME = "file";

  private final UriDataSource fileDataSource;
  private final UriDataSource httpDataSource;

  /**
   * {@code null} if no data source is open. Otherwise, equal to {@link #fileDataSource} if the open
   * data source is a file, or {@link #httpDataSource} otherwise.
   */
  private UriDataSource dataSource;

  /**
   * Constructs a new data source that delegates to a {@link FileDataSource} for file URIs and a
   * {@link DefaultHttpDataSource} for other URIs.
   * <p>
   * The constructed instance will not follow cross-protocol redirects (i.e. redirects from HTTP to
   * HTTPS or vice versa) when fetching remote data. Cross-protocol redirects can be enabled by
   * using the {@link #DefaultUriDataSource(String, TransferListener, boolean)} constructor and
   * passing {@code true} as the final argument.
   *
   * @param userAgent The User-Agent string that should be used when requesting remote data.
   * @param transferListener An optional listener.
   */
  public DefaultUriDataSource(String userAgent, TransferListener transferListener) {
    this(userAgent, transferListener, false);
  }

  /**
   * Constructs a new data source that delegates to a {@link FileDataSource} for file URIs and a
   * {@link DefaultHttpDataSource} for other URIs.
   *
   * @param userAgent The User-Agent string that should be used when requesting remote data.
   * @param transferListener An optional listener.
   * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
   *     to HTTPS and vice versa) are enabled when fetching remote data..
   */
  public DefaultUriDataSource(String userAgent, TransferListener transferListener,
      boolean allowCrossProtocolRedirects) {
    this(new FileDataSource(transferListener),
        new DefaultHttpDataSource(userAgent, null, transferListener,
            DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
            DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, allowCrossProtocolRedirects));
  }

  /**
   * Constructs a new data source using {@code fileDataSource} for file URIs, and
   * {@code httpDataSource} for non-file URIs.
   *
   * @param fileDataSource {@link UriDataSource} to use for file URIs.
   * @param httpDataSource {@link UriDataSource} to use for non-file URIs.
   */
  public DefaultUriDataSource(UriDataSource fileDataSource, UriDataSource httpDataSource) {
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
  public String getUri() {
    return dataSource == null ? null : dataSource.getUri();
  }

  @Override
  public void close() throws IOException {
    if (dataSource != null) {
      dataSource.close();
      dataSource = null;
    }
  }

}
