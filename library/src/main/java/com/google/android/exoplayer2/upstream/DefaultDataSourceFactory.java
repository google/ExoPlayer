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

import android.content.Context;
import com.google.android.exoplayer2.upstream.DataSource.Factory;

/**
 * A {@link Factory} that produces {@link DefaultDataSource} instances that delegate to
 * {@link DefaultHttpDataSource}s for non-file/asset/content URIs.
 */
public final class DefaultDataSourceFactory implements Factory {

  /**
   * The default connection timeout, in milliseconds.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS =
      DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS;
  /**
   * The default read timeout, in milliseconds.
   */
  public static final int DEFAULT_READ_TIMEOUT_MILLIS =
      DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS;

  private final Context context;
  private final String userAgent;
  private final TransferListener<? super DataSource> listener;
  private final int connectTimeoutMillis;
  private final int readTimeoutMillis;
  private final boolean allowCrossProtocolRedirects;

  /**
   * @param context A context.
   * @param userAgent The User-Agent string that should be used.
   */
  public DefaultDataSourceFactory(Context context, String userAgent) {
    this(context, userAgent, null);
  }

  /**
   * @param context A context.
   * @param userAgent The User-Agent string that should be used.
   * @param listener An optional listener.
   */
  public DefaultDataSourceFactory(Context context, String userAgent,
      TransferListener<? super DataSource> listener) {
    this(context, userAgent, listener, false);
  }

  /**
   * @param context A context.
   * @param userAgent The User-Agent string that should be used.
   * @param listener An optional listener.
   * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
   *     to HTTPS and vice versa) are enabled.
   */
  public DefaultDataSourceFactory(Context context, String userAgent,
      TransferListener<? super DataSource> listener, boolean allowCrossProtocolRedirects) {
    this(context, userAgent, listener, DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS,
        allowCrossProtocolRedirects);
  }

  /**
   * @param context A context.
   * @param userAgent The User-Agent string that should be used.
   * @param listener An optional listener.
   * @param connectTimeoutMillis The connection timeout that should be used when requesting remote
   *     data, in milliseconds. A timeout of zero is interpreted as an infinite timeout.
   * @param readTimeoutMillis The read timeout that should be used when requesting remote data,
   *     in milliseconds. A timeout of zero is interpreted as an infinite timeout.
   * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
   *     to HTTPS and vice versa) are enabled.
   */
  public DefaultDataSourceFactory(Context context, String userAgent,
      TransferListener<? super DataSource> listener, int connectTimeoutMillis,
      int readTimeoutMillis, boolean allowCrossProtocolRedirects) {
    this.context = context.getApplicationContext();
    this.userAgent = userAgent;
    this.listener = listener;
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
    this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
  }

  @Override
  public DefaultDataSource createDataSource() {
    return new DefaultDataSource(context, listener, userAgent, connectTimeoutMillis,
        readTimeoutMillis, allowCrossProtocolRedirects);
  }

}
