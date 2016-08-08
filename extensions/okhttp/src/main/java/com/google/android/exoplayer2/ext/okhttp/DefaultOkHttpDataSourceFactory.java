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
package com.google.android.exoplayer2.ext.okhttp;

import android.content.Context;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSource.Factory;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;

/**
 * A {@link Factory} that produces {@link DefaultDataSource} instances that delegate to
 * {@link OkHttpDataSource}s for non-file/asset/content URIs.
 */
public final class DefaultOkHttpDataSourceFactory implements Factory {

  private final Context context;
  private final OkHttpClient client;
  private final String userAgent;
  private final TransferListener<? super DataSource> transferListener;
  private final CacheControl cacheControl;

  public DefaultOkHttpDataSourceFactory(Context context, OkHttpClient client, String userAgent,
      TransferListener<? super DataSource> transferListener) {
    this(context, client, userAgent, transferListener, null);
  }

  public DefaultOkHttpDataSourceFactory(Context context, OkHttpClient client, String userAgent,
      TransferListener<? super DataSource> transferListener, CacheControl cacheControl) {
    this.context = context.getApplicationContext();
    this.client = client;
    this.userAgent = userAgent;
    this.transferListener = transferListener;
    this.cacheControl = cacheControl;
  }

  @Override
  public DefaultDataSource createDataSource() {
    DataSource httpDataSource = new OkHttpDataSource(client, userAgent, null, transferListener,
        cacheControl);
    return new DefaultDataSource(context, transferListener, httpDataSource);
  }

}
