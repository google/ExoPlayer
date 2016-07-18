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

import com.google.android.exoplayer2.upstream.DataSource.Factory;

import android.content.Context;

/**
 * A {@link Factory} that produces {@link DefaultDataSource} instances that delegate to
 * {@link DefaultHttpDataSource}s for non-file/asset/content URIs.
 */
public final class DefaultDataSourceFactory implements Factory {

  private final Context context;
  private final String userAgent;
  private final TransferListener transferListener;
  private final boolean allowCrossProtocolRedirects;

  public DefaultDataSourceFactory(Context context, String userAgent) {
    this(context, userAgent, null);
  }

  public DefaultDataSourceFactory(Context context, String userAgent,
      TransferListener transferListener) {
    this(context, userAgent, transferListener, false);
  }

  public DefaultDataSourceFactory(Context context, String userAgent,
      TransferListener transferListener, boolean allowCrossProtocolRedirects) {
    this.context = context.getApplicationContext();
    this.userAgent = userAgent;
    this.transferListener = transferListener;
    this.allowCrossProtocolRedirects = allowCrossProtocolRedirects;
  }

  @Override
  public DefaultDataSource createDataSource() {
    return new DefaultDataSource(context, transferListener, userAgent, allowCrossProtocolRedirects);
  }

}
