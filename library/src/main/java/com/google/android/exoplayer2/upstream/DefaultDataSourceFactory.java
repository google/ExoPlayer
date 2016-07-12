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

  private final Context context;
  private final String userAgent;
  private final TransferListener listener;
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
  public DefaultDataSourceFactory(Context context, String userAgent, TransferListener listener) {
    this(context, userAgent, listener, false);
  }

  /**
   * @param context A context.
   * @param userAgent The User-Agent string that should be used.
   * @param listener An optional listener.
   * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
   *     to HTTPS and vice versa) are enabled.
   */
  public DefaultDataSourceFactory(Context context, String userAgent, TransferListener listener,
      boolean allowCrossProtocolRedirects) {
    this.context = context.getApplicationContext();
    this.userAgent = userAgent;
    this.listener = listener;
    this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
  }

  @Override
  public DefaultDataSource createDataSource() {
    return new DefaultDataSource(context, listener, userAgent, allowCrossProtocolRedirects);
  }

}
