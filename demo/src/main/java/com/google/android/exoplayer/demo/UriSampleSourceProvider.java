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
import com.google.android.exoplayer.SimpleExoPlayer;
import com.google.android.exoplayer.dash.DashSampleSource;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingSampleSource;
import com.google.android.exoplayer.upstream.DataSourceFactory;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;

/**
 * Provides {@link SampleSource}s to play back media loaded from one or more URI/URIs.
 */
public final class UriSampleSourceProvider implements SampleSourceProvider {

  public static final int UNKNOWN_TYPE = -1;

  private final SimpleExoPlayer player;
  private final DataSourceFactory dataSourceFactory;
  private final Uri[] uris;
  private final String overrideExtension;
  private final int type;
  private final Handler handler;
  private final EventLogger eventLogger;

  /**
   * Constructs a source provider for {@link SampleSource} to play back media at the specified
   * URI, using the specified type.
   *
   * @param player The demo player, which will listen to source events.
   * @param dataSourceFactory A data source factory.
   * @param uri The URI to play back.
   * @param type A {@code PlayerActivity.TYPE_*} constant specifying the type of the source, or
   *     {@link #UNKNOWN_TYPE}, in which case it is inferred from the URI's extension.
   * @param overrideExtension An overriding file extension used when inferring the source's type,
   *     or {@code null}.
   * @param handler A handler to use for logging events.
   * @param eventLogger An event logger.
   */
  public UriSampleSourceProvider(SimpleExoPlayer player, DataSourceFactory dataSourceFactory,
      Uri uri, int type, String overrideExtension, Handler handler, EventLogger eventLogger) {
    this.player = player;
    this.dataSourceFactory = dataSourceFactory;
    this.overrideExtension = overrideExtension;
    this.type = type;
    this.handler = handler;
    this.eventLogger = eventLogger;

    uris = new Uri[] {uri};
  }

  /**
   * Constructs a source provider for {@link SampleSource}s to play back media at one or more
   * {@link Uri}s. The content type of each URI is inferred based on its last path segment.
   *
   * @param player The demo player, which will listen to source events.
   * @param dataSourceFactory A data source factory.
   * @param uris The URIs to play back.
   * @param handler A handler to use for logging events.
   * @param eventLogger An event logger.
   */
  public UriSampleSourceProvider(SimpleExoPlayer player, DataSourceFactory dataSourceFactory,
      Uri[] uris, Handler handler, EventLogger eventLogger) {
    this.player = player;
    this.dataSourceFactory = dataSourceFactory;
    this.uris = uris;
    this.handler = handler;
    this.eventLogger = eventLogger;

    overrideExtension = null;
    type = UNKNOWN_TYPE;
  }

  @Override
  public int getSourceCount() {
    return uris.length;
  }

  @Override
  public SampleSource createSource(int index) {
    Uri uri = uris[index];
    int type = this.type == UNKNOWN_TYPE ? inferContentType(uri, overrideExtension) : this.type;
    switch (type) {
      case Util.TYPE_SS:
        return new SmoothStreamingSampleSource(uri, dataSourceFactory, player.getBandwidthMeter(),
            handler, eventLogger);
      case Util.TYPE_DASH:
        return new DashSampleSource(uri, dataSourceFactory, player.getBandwidthMeter(), handler,
            eventLogger);
      case Util.TYPE_HLS:
        return new HlsSampleSource(uri, dataSourceFactory, player.getBandwidthMeter(), handler,
            eventLogger);
      case Util.TYPE_OTHER:
        return new ExtractorSampleSource(uri, dataSourceFactory, player.getBandwidthMeter(),
            ExtractorSampleSource.newDefaultExtractors(), handler, eventLogger, 0);
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
