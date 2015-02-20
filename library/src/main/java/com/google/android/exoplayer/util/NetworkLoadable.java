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
package com.google.android.exoplayer.util;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.upstream.Loader.Loadable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * A {@link Loadable} for loading an object over the network.
 *
 * @param <T> The type of the object being loaded.
 */
public abstract class NetworkLoadable<T> implements Loadable {

  public static final int DEFAULT_TIMEOUT_MILLIS = 10000;

  private final String url;
  private final String userAgent;
  private final int timeoutMillis;

  private volatile T result;
  private volatile boolean isCanceled;

  /**
   * @param url The url from which the object should be loaded.
   * @param userAgent The user agent to use when requesting the object.
   */
  public NetworkLoadable(String url, String userAgent) {
    this(url, userAgent, DEFAULT_TIMEOUT_MILLIS);
  }

  /**
   * @param url The url from which the object should be loaded.
   * @param userAgent The user agent to use when requesting the object.
   * @param timeoutMillis The desired http timeout in milliseconds.
   */
  public NetworkLoadable(String url, String userAgent, int timeoutMillis) {
    this.url = url;
    this.userAgent = userAgent;
    this.timeoutMillis = timeoutMillis;
  }

  /**
   * Returns the loaded object, or null if an object has not been loaded.
   */
  public final T getResult() {
    return result;
  }

  @Override
  public final void cancelLoad() {
    // We don't actually cancel anything, but we need to record the cancellation so that
    // isLoadCanceled can return the correct value.
    isCanceled = true;
  }

  @Override
  public final boolean isLoadCanceled() {
    return isCanceled;
  }

  @Override
  public final void load() throws IOException, InterruptedException {
    String inputEncoding;
    InputStream inputStream = null;
    try {
      URLConnection connection = configureConnection(new URL(url));
      inputStream = connection.getInputStream();
      inputEncoding = connection.getContentEncoding();
      result = parse(connection.getURL().toString(), inputStream, inputEncoding);
    } finally {
      if (inputStream != null) {
        inputStream.close();
      }
    }
  }

  /**
   * Parses the raw data into an object.
   *
   * @param connectionUrl The url, after any redirection has taken place.
   * @param inputStream An {@link InputStream} from which the raw data can be read.
   * @param inputEncoding The encoding of the raw data, if available.
   * @return The parsed object.
   * @throws ParserException If an error occurs parsing the data.
   * @throws IOException If an error occurs reading data from the stream.
   */
  protected abstract T parse(String connectionUrl, InputStream inputStream, String inputEncoding)
      throws ParserException, IOException;

  private URLConnection configureConnection(URL url) throws IOException {
    URLConnection connection = url.openConnection();
    connection.setConnectTimeout(timeoutMillis);
    connection.setReadTimeout(timeoutMillis);
    connection.setDoOutput(false);
    connection.setRequestProperty("User-Agent", userAgent);
    connection.connect();
    return connection;
  }

}
