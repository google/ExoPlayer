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

import com.google.android.exoplayer2.upstream.DataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Predicate;

import android.content.Context;

import okhttp3.CacheControl;
import okhttp3.OkHttpClient;

/**
 * A {@link DataSourceFactory} that produces {@link DefaultDataSource} instances that delegate to
 * {@link OkHttpDataSource}s for non-file/asset/content URIs.
 */
public final class DefaultOkHttpDataSourceFactory implements DataSourceFactory {

  private final Context context;
  private final OkHttpClient client;
  private final String userAgent;
  private final Predicate<String> contentTypePredicate;
  private final CacheControl cacheControl;

  public DefaultOkHttpDataSourceFactory(Context context, OkHttpClient client, String userAgent,
      Predicate<String> contentTypePredicate) {
    this(context, client, userAgent, contentTypePredicate, null);
  }

  public DefaultOkHttpDataSourceFactory(Context context, OkHttpClient client, String userAgent,
      Predicate<String> contentTypePredicate, CacheControl cacheControl) {
    this.context = context.getApplicationContext();
    this.client = client;
    this.userAgent = userAgent;
    this.contentTypePredicate = contentTypePredicate;
    this.cacheControl = cacheControl;
  }

  @Override
  public DefaultDataSource createDataSource() {
    return createDataSource(null);
  }

  @Override
  public DefaultDataSource createDataSource(TransferListener listener) {
    return new DefaultDataSource(context, listener,
        new OkHttpDataSource(client, userAgent, contentTypePredicate, listener, cacheControl));
  }

}
