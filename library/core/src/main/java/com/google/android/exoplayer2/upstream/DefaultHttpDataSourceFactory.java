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
package com.google.android.exoplayer2.upstream;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.upstream.HttpDataSource.BaseFactory;

/** @deprecated Use {@link DefaultHttpDataSource.Factory} instead. */
@Deprecated
public final class DefaultHttpDataSourceFactory extends BaseFactory {

  @Nullable private final String userAgent;
  @Nullable private final TransferListener listener;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  private final boolean allowCrossProtocolRedirects;

  /**
   * Creates an instance. Sets {@link DefaultHttpDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the
   * connection timeout, {@link DefaultHttpDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read
   * timeout and disables cross-protocol redirects.
   */
  public DefaultHttpDataSourceFactory() {
    this(/* userAgent= */ null);
  }

  /**
   * Creates an instance. Sets {@link DefaultHttpDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the
   * connection timeout, {@link DefaultHttpDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read
   * timeout and disables cross-protocol redirects.
   *
   * @param userAgent The user agent that will be used, or {@code null} to use the default user
   *     agent of the underlying platform.
   */
  public DefaultHttpDataSourceFactory(@Nullable String userAgent) {
    this(userAgent, null);
  }

  /**
   * Creates an instance. Sets {@link DefaultHttpDataSource#DEFAULT_CONNECT_TIMEOUT_MILLIS} as the
   * connection timeout, {@link DefaultHttpDataSource#DEFAULT_READ_TIMEOUT_MILLIS} as the read
   * timeout and disables cross-protocol redirects.
   *
   * @param userAgent The user agent that will be used, or {@code null} to use the default user
   *     agent of the underlying platform.
   * @param listener An optional listener.
   * @see #DefaultHttpDataSourceFactory(String, TransferListener, int, int, boolean)
   */
  public DefaultHttpDataSourceFactory(
      @Nullable String userAgent, @Nullable TransferListener listener) {
    this(userAgent, listener, DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
        DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, false);
  }

  /**
   * @param userAgent The user agent that will be used, or {@code null} to use the default user
   *     agent of the underlying platform.
   * @param connectTimeoutMillis The connection timeout that should be used when requesting remote
   *     data, in milliseconds. A timeout of zero is interpreted as an infinite timeout.
   * @param readTimeoutMillis The read timeout that should be used when requesting remote data, in
   *     milliseconds. A timeout of zero is interpreted as an infinite timeout.
   * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
   *     to HTTPS and vice versa) are enabled.
   */
  public DefaultHttpDataSourceFactory(
      @Nullable String userAgent,
      int connectTimeoutMillis,
      int readTimeoutMillis,
      boolean allowCrossProtocolRedirects) {
    this(
        userAgent,
        /* listener= */ null,
        connectTimeoutMillis,
        readTimeoutMillis,
        allowCrossProtocolRedirects);
  }

  /**
   * @param userAgent The user agent that will be used, or {@code null} to use the default user
   *     agent of the underlying platform.
   * @param listener An optional listener.
   * @param connectTimeoutMillis The connection timeout that should be used when requesting remote
   *     data, in milliseconds. A timeout of zero is interpreted as an infinite timeout.
   * @param readTimeoutMillis The read timeout that should be used when requesting remote data, in
   *     milliseconds. A timeout of zero is interpreted as an infinite timeout.
   * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
   *     to HTTPS and vice versa) are enabled.
   */
  public DefaultHttpDataSourceFactory(
      @Nullable String userAgent,
      @Nullable TransferListener listener,
      int connectTimeoutMillis,
      int readTimeoutMillis,
      boolean allowCrossProtocolRedirects) {
    this.userAgent = userAgent;
    this.listener = listener;
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
    this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
  }

  // Calls deprecated constructor.
  @SuppressWarnings("deprecation")
  @Override
  protected DefaultHttpDataSource createDataSourceInternal(
      HttpDataSource.RequestProperties defaultRequestProperties) {
    DefaultHttpDataSource dataSource =
        new DefaultHttpDataSource(
            userAgent,
            connectTimeoutMillis,
            readTimeoutMillis,
            allowCrossProtocolRedirects,
            defaultRequestProperties);
    if (listener != null) {
      dataSource.addTransferListener(listener);
    }
    return dataSource;
  }
}
