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
package com.google.android.exoplayer.demo;

import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.SampleSourceProvider;
import com.google.android.exoplayer.dash.DashSampleSource;
import com.google.android.exoplayer.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingSampleSource;
import com.google.android.exoplayer.upstream.BandwidthMeter;
import com.google.android.exoplayer.upstream.DataSourceFactory;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;

/**
 * Provides a {@link SampleSource} to play back media loaded from a {@link Uri}.
 */
public final class UriSampleSourceProvider implements SampleSourceProvider {

  private final BandwidthMeter bandwidthMeter;
  private final DataSourceFactory dataSourceFactory;
  private final Uri uri;
  private final String overrideExtension;
  private final Handler handler;
  private final EventLogger eventLogger;

  /**
   * Constructs a source provider for {@link SampleSource} to play back media at the specified
   * URI, using the specified type.
   *
   * @param bandwidthMeter A bandwidth meter.
   * @param dataSourceFactory A data source factory.
   * @param uri The URI to play back.
   * @param overrideExtension An overriding file extension used when inferring the source's type,
   *     or {@code null}.
   * @param handler A handler to use for logging events.
   * @param eventLogger An event logger.
   */
  public UriSampleSourceProvider(BandwidthMeter bandwidthMeter, DataSourceFactory dataSourceFactory,
      Uri uri, String overrideExtension, Handler handler, EventLogger eventLogger) {
    this.bandwidthMeter = bandwidthMeter;
    this.dataSourceFactory = dataSourceFactory;
    this.uri = uri;
    this.overrideExtension = overrideExtension;
    this.handler = handler;
    this.eventLogger = eventLogger;
  }

  @Override
  public int getSourceCount() {
    return 1;
  }

  @Override
  public SampleSource createSource(int index) {
    int type = inferContentType(uri, overrideExtension);
    switch (type) {
      case Util.TYPE_SS:
        return new SmoothStreamingSampleSource(uri, dataSourceFactory, bandwidthMeter, handler,
            eventLogger);
      case Util.TYPE_DASH:
        return new DashSampleSource(uri, dataSourceFactory, bandwidthMeter, handler, eventLogger);
      case Util.TYPE_HLS:
        return new HlsSampleSource(uri, dataSourceFactory, bandwidthMeter, handler, eventLogger);
      case Util.TYPE_OTHER:
        return new ExtractorSampleSource(uri, dataSourceFactory, bandwidthMeter,
            new DefaultExtractorsFactory(), handler, eventLogger);
      default:
        throw new IllegalStateException("Unsupported type: " + type);
    }
  }

  /**
   * Makes a best guess to infer the type from a media {@link Uri} and an optional overriding file
   * extension.
   *
   * @param uri The {@link Uri} of the media.
   * @param fileExtension An overriding file extension.
   * @return The inferred type.
   */
  private static int inferContentType(Uri uri, String fileExtension) {
    String lastPathSegment = !TextUtils.isEmpty(fileExtension) ? "." + fileExtension
        : uri.getLastPathSegment();
    return Util.inferContentType(lastPathSegment);
  }

}
