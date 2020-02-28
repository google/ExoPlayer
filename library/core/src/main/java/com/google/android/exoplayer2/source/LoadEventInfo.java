/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import android.net.Uri;
import android.os.SystemClock;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.util.List;
import java.util.Map;

/** {@link MediaSource} load event information. */
public final class LoadEventInfo {

  /** Defines the requested data. */
  public final DataSpec dataSpec;
  /**
   * The {@link Uri} from which data is being read. The uri will be identical to the one in {@link
   * #dataSpec}.uri unless redirection has occurred. If redirection has occurred, this is the uri
   * after redirection.
   */
  public final Uri uri;
  /** The response headers associated with the load, or an empty map if unavailable. */
  public final Map<String, List<String>> responseHeaders;
  /** The value of {@link SystemClock#elapsedRealtime} at the time of the load event. */
  public final long elapsedRealtimeMs;
  /** The duration of the load up to the event time. */
  public final long loadDurationMs;
  /** The number of bytes that were loaded up to the event time. */
  public final long bytesLoaded;

  /**
   * Creates load event info.
   *
   * @param dataSpec Defines the requested data.
   * @param uri The {@link Uri} from which data is being read. The uri must be identical to the one
   *     in {@code dataSpec.uri} unless redirection has occurred. If redirection has occurred, this
   *     is the uri after redirection.
   * @param responseHeaders The response headers associated with the load, or an empty map if
   *     unavailable.
   * @param elapsedRealtimeMs The value of {@link SystemClock#elapsedRealtime} at the time of the
   *     load event.
   * @param loadDurationMs The duration of the load up to the event time.
   * @param bytesLoaded The number of bytes that were loaded up to the event time. For compressed
   *     network responses, this is the decompressed size.
   */
  public LoadEventInfo(
      DataSpec dataSpec,
      Uri uri,
      Map<String, List<String>> responseHeaders,
      long elapsedRealtimeMs,
      long loadDurationMs,
      long bytesLoaded) {
    this.dataSpec = dataSpec;
    this.uri = uri;
    this.responseHeaders = responseHeaders;
    this.elapsedRealtimeMs = elapsedRealtimeMs;
    this.loadDurationMs = loadDurationMs;
    this.bytesLoaded = bytesLoaded;
  }
}
