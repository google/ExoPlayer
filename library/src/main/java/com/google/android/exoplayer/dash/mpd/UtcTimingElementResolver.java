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
package com.google.android.exoplayer.dash.mpd;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.Loader.Loadable;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.NetworkLoadable;
import com.google.android.exoplayer.util.Util;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/**
 * Resolves a {@link UtcTimingElement}.
 */
public class UtcTimingElementResolver implements Loader.Callback {

  /**
   * Callback for timing element resolution.
   */
  public interface UtcTimingCallback {

    /**
     * Invoked when the element has been resolved.
     *
     * @param utcTiming The element that was resolved.
     * @param elapsedRealtimeOffset The offset between the resolved UTC time and
     *     {@link SystemClock#elapsedRealtime()} in milliseconds, specified as the UTC time minus
     *     the local elapsed time.
     */
    void onTimestampResolved(UtcTimingElement utcTiming, long elapsedRealtimeOffset);

    /**
     * Invoked when the element was not successfully resolved.
     *
     * @param utcTiming The element that was not resolved.
     * @param e The cause of the failure.
     */
    void onTimestampError(UtcTimingElement utcTiming, IOException e);
  }

  private static final int TYPE_XS = 0;
  private static final int TYPE_ISO = 1;

  private final String userAgent;
  private final UtcTimingElement timingElement;
  private final long timingElementElapsedRealtime;
  private final UtcTimingCallback callback;

  private Loader singleUseLoader;
  private HttpTimestampLoadable singleUseLoadable;

  /**
   * Resolves a {@link UtcTimingElement}.
   *
   * @param userAgent A user agent to use should network requests be necessary.
   * @param timingElement The element to resolve.
   * @param timingElementElapsedRealtime The {@link SystemClock#elapsedRealtime()} timestamp at
   *     which the element was obtained. Used if the element contains a timestamp directly.
   * @param callback The callback to invoke on resolution or failure.
   */
  public static void resolveTimingElement(String userAgent, UtcTimingElement timingElement,
      long timingElementElapsedRealtime, UtcTimingCallback callback) {
    UtcTimingElementResolver resolver = new UtcTimingElementResolver(userAgent, timingElement,
        timingElementElapsedRealtime, callback);
    resolver.resolve();
  }

  private UtcTimingElementResolver(String userAgent, UtcTimingElement timingElement,
      long timingElementElapsedRealtime, UtcTimingCallback callback) {
    this.userAgent = userAgent;
    this.timingElement = Assertions.checkNotNull(timingElement);
    this.timingElementElapsedRealtime = timingElementElapsedRealtime;
    this.callback = Assertions.checkNotNull(callback);
  }

  private void resolve() {
    String scheme = timingElement.schemeIdUri;
    if (Util.areEqual(scheme, "urn:mpeg:dash:utc:direct:2012")) {
      resolveDirect();
    } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:http-iso:2014")) {
      resolveHttp(TYPE_ISO);
    } else if (Util.areEqual(scheme, "urn:mpeg:dash:utc:http-xsdate:2012")
        || Util.areEqual(scheme, "urn:mpeg:dash:utc:http-xsdate:2014")) {
      resolveHttp(TYPE_XS);
    } else {
      // Unsupported scheme.
      callback.onTimestampError(timingElement, new IOException("Unsupported utc timing scheme"));
    }
  }

  private void resolveDirect() {
    try {
      long utcTimestamp = Util.parseXsDateTime(timingElement.value);
      long elapsedRealtimeOffset = utcTimestamp - timingElementElapsedRealtime;
      callback.onTimestampResolved(timingElement, elapsedRealtimeOffset);
    } catch (ParseException e) {
      callback.onTimestampError(timingElement, new ParserException(e));
    }
  }

  private void resolveHttp(int type) {
    singleUseLoader = new Loader("utctiming");
    singleUseLoadable = new HttpTimestampLoadable(timingElement.value, userAgent, type);
    singleUseLoader.startLoading(singleUseLoadable, this);
  }

  @Override
  public void onLoadCanceled(Loadable loadable) {
    onLoadError(loadable, new IOException("Load cancelled", new CancellationException()));
  }

  @Override
  public void onLoadCompleted(Loadable loadable) {
    releaseLoader();
    long elapsedRealtimeOffset = singleUseLoadable.getResult() - SystemClock.elapsedRealtime();
    callback.onTimestampResolved(timingElement, elapsedRealtimeOffset);
  }

  @Override
  public void onLoadError(Loadable loadable, IOException exception) {
    releaseLoader();
    callback.onTimestampError(timingElement, exception);
  }

  private void releaseLoader() {
    singleUseLoader.release();
  }

  private static class HttpTimestampLoadable extends NetworkLoadable<Long> {

    private final int type;

    public HttpTimestampLoadable(String url, String userAgent, int type) {
      super(url, userAgent);
      this.type = type;
    }

    @Override
    protected Long parse(String connectionUrl, InputStream inputStream, String inputEncoding)
        throws ParserException, IOException {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      String firstLine = reader.readLine();
      try {
        switch (type) {
          case TYPE_XS:
            return Util.parseXsDateTime(firstLine);
          case TYPE_ISO:
            // TODO: It may be necessary to handle timestamp offsets from UTC.
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            return format.parse(firstLine).getTime();
          default:
            // Never happens.
            throw new RuntimeException();
        }
      } catch (ParseException e) {
        throw new ParserException(e);
      }
    }

  }

}
