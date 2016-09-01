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

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.Factory;
import com.google.android.exoplayer2.upstream.TransferListener;
import okhttp3.CacheControl;
import okhttp3.OkHttpClient;

/**
 * A {@link Factory} that produces {@link OkHttpDataSource}.
 */
public final class OkHttpDataSourceFactory implements Factory {

  private final OkHttpClient client;
  private final String userAgent;
  private final TransferListener<? super DataSource> transferListener;
  private final CacheControl cacheControl;

  public OkHttpDataSourceFactory(OkHttpClient client, String userAgent,
      TransferListener<? super DataSource> transferListener) {
    this(client, userAgent, transferListener, null);
  }

  public OkHttpDataSourceFactory(OkHttpClient client, String userAgent,
      TransferListener<? super DataSource> transferListener, CacheControl cacheControl) {
    this.client = client;
    this.userAgent = userAgent;
    this.transferListener = transferListener;
    this.cacheControl = cacheControl;
  }

  @Override
  public OkHttpDataSource createDataSource() {
    return new OkHttpDataSource(client, userAgent, null, transferListener, cacheControl);
  }

}
