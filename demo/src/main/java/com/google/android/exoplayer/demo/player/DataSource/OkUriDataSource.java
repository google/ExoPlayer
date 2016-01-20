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
package com.google.android.exoplayer.demo.player.DataSource;

import android.content.Context;
import android.text.TextUtils;

import com.facebook.stetho.okhttp.StethoInterceptor;
import com.google.android.exoplayer.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer.upstream.AssetDataSource;
import com.google.android.exoplayer.upstream.ContentDataSource;
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer.upstream.FileDataSource;
import com.google.android.exoplayer.upstream.HttpDataSource;
import com.google.android.exoplayer.upstream.TransferListener;
import com.google.android.exoplayer.upstream.UriDataSource;
import com.google.android.exoplayer.util.Assertions;
import com.squareup.okhttp.CacheControl;
import com.squareup.okhttp.OkHttpClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * A {@link OkUriDataSource} that supports multiple URI schemes. The supported schemes are:
 *
 * <ul>
 * <li>http(s): For fetching data over HTTP and HTTPS (e.g. https://www.something.com/media.mp4).
 * <li>file: For fetching data from a local file (e.g. file:///path/to/media/media.mp4, or just
 *     /path/to/media/media.mp4 because the implementation assumes that a URI without a scheme is a
 *     local file URI).
 * <li>asset: For fetching data from an asset in the application's apk (e.g. asset:///media.mp4).
 * <li>content: For fetching data from a content URI (e.g. content://authority/path/123).
 * </ul>
 */
public final class OkUriDataSource implements UriDataSource {

  private static final String SCHEME_FILE = "file";
  private static final String SCHEME_ASSET = "asset";
  private static final String SCHEME_CONTENT = "content";

  private final UriDataSource httpDataSource;
  private final UriDataSource fileDataSource;
  private final UriDataSource assetDataSource;
  private final UriDataSource contentDataSource;
  private static final OkHttpClient sOkHttpClient = new OkHttpClient();

  /**
   * {@code null} if no data source is open. Otherwise, equal to {@link #fileDataSource} if the open
   * data source is a file, or {@link #httpDataSource} otherwise.
   */
  private UriDataSource dataSource;

  /**
   * Constructs a new instance.
   * <p>
   * The constructed instance will not follow cross-protocol redirects (i.e. redirects from HTTP to
   * HTTPS or vice versa) when fetching remote data. Cross-protocol redirects can be enabled by
   * using {@link #OkUriDataSource(Context, TransferListener, String, boolean)} and passing
   * {@code true} as the final argument.
   *
   * @param context A context.
   * @param userAgent The User-Agent string that should be used when requesting remote data.
   */
  public OkUriDataSource(Context context, String userAgent) {
    this(context, null, userAgent, true);
  }

  /**
   * Constructs a new instance.
   * <p>
   * The constructed instance will not follow cross-protocol redirects (i.e. redirects from HTTP to
   * HTTPS or vice versa) when fetching remote data. Cross-protocol redirects can be enabled by
   * using {@link #OkUriDataSource(Context, TransferListener, String, boolean)} and passing
   * {@code true} as the final argument.
   *
   * @param context A context.
   * @param listener An optional {@link TransferListener}.
   * @param userAgent The User-Agent string that should be used when requesting remote data.
   */
  public OkUriDataSource(Context context, TransferListener listener, String userAgent) {
    this(context, listener, userAgent, true);
  }

  /**
   * Constructs a new instance, optionally configured to follow cross-protocol redirects.
   *
   * @param context A context.
   * @param listener An optional {@link TransferListener}.
   * @param userAgent The User-Agent string that should be used when requesting remote data.
   * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
   *     to HTTPS and vice versa) are enabled when fetching remote data..
   */
  public OkUriDataSource(Context context, TransferListener listener, String userAgent,
                         boolean allowCrossProtocolRedirects) {
    this(context, listener,
        new OkHttpDataSource(getDefaultOkHttpClient(allowCrossProtocolRedirects),
                userAgent,
                null,
                listener,
                new CacheControl.Builder().noCache().build()));
  }

  private static OkHttpClient getDefaultOkHttpClient(boolean allowCrossProtocolRedirects) {
    OkHttpClient okHttpClient = sOkHttpClient.clone();
    StethoInterceptor stethoInterceptor = new StethoInterceptor();
    okHttpClient.networkInterceptors().add(stethoInterceptor);
    okHttpClient.setConnectTimeout(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    okHttpClient.setReadTimeout(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    okHttpClient.setFollowSslRedirects(allowCrossProtocolRedirects);
    return okHttpClient;
  }

  /**
   * Constructs a new instance, using a provided {@link HttpDataSource} for fetching remote data.
   *
   * @param context A context.
   * @param listener An optional {@link TransferListener}.
   * @param httpDataSource {@link UriDataSource} to use for non-file URIs.
   */
  public OkUriDataSource(Context context, TransferListener listener,
                         UriDataSource httpDataSource) {
    this.httpDataSource = Assertions.checkNotNull(httpDataSource);
    this.fileDataSource = new FileDataSource(listener);
    this.assetDataSource = new AssetDataSource(context, listener);
    this.contentDataSource = new ContentDataSource(context, listener);
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    Assertions.checkState(dataSource == null);
    // Choose the correct source for the scheme.
    String scheme = dataSpec.uri.getScheme();
    if (SCHEME_FILE.equals(scheme) || TextUtils.isEmpty(scheme)) {
      if (dataSpec.uri.getPath().startsWith("/android_asset/")) {
        dataSource = assetDataSource;
      } else {
        dataSource = fileDataSource;
      }
    } else if (SCHEME_ASSET.equals(scheme)) {
      dataSource = assetDataSource;
    } else if (SCHEME_CONTENT.equals(scheme)) {
      dataSource = contentDataSource;
    } else {
      dataSource = httpDataSource;
    }
    // Open the source and return.
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
      try {
        dataSource.close();
      } finally {
        dataSource = null;
      }
    }
  }

}
