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
package com.google.android.exoplayer2.ext.cronet;

import android.content.Context;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSource.Factory;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Predicate;
import java.util.concurrent.Executor;
import org.chromium.net.CronetEngine;

/**
 * A {@link Factory} that produces {@link DefaultDataSource} instances that delegate to
 * {@link CronetDataSource}s for non-file/asset/content URIs.
 */
public final class DefaultCronetDataSourceFactory implements Factory {

  /**
   * The default connection timeout, in milliseconds.
   */
  public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS =
      CronetDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS;
  /**
   * The default read timeout, in milliseconds.
   */
  public static final int DEFAULT_READ_TIMEOUT_MILLIS =
      CronetDataSource.DEFAULT_READ_TIMEOUT_MILLIS;

  private final Context context;
  private final CronetEngine cronetEngine;
  private final Executor executor;
  private final Predicate<String> contentTypePredicate;
  private final TransferListener transferListener;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final boolean resetTimeoutOnRedirects;

  public DefaultCronetDataSourceFactory(Context context, CronetEngine cronetEngine,
      Executor executor, Predicate<String> contentTypePredicate,
      TransferListener<? super DataSource> transferListener) {
    this(context, cronetEngine, executor, contentTypePredicate, transferListener,
        DEFAULT_CONNECT_TIMEOUT_MILLIS, DEFAULT_READ_TIMEOUT_MILLIS, false);
  }

  public DefaultCronetDataSourceFactory(Context context, CronetEngine cronetEngine,
      Executor executor, Predicate<String> contentTypePredicate,
      TransferListener<? super DataSource> transferListener, int connectTimeoutMs,
      int readTimeoutMs, boolean resetTimeoutOnRedirects) {
    this.context = context;
    this.cronetEngine = cronetEngine;
    this.executor = executor;
    this.contentTypePredicate = contentTypePredicate;
    this.transferListener = transferListener;
    this.connectTimeoutMs = connectTimeoutMs;
    this.readTimeoutMs = readTimeoutMs;
    this.resetTimeoutOnRedirects = resetTimeoutOnRedirects;
  }

  @Override
  public DefaultDataSource createDataSource() {
    DataSource cronetDataSource = new CronetDataSource(cronetEngine, executor, contentTypePredicate,
        transferListener, connectTimeoutMs, readTimeoutMs, resetTimeoutOnRedirects);
    return new DefaultDataSource(context, transferListener, cronetDataSource);
  }

}
