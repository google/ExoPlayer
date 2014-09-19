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

import android.net.Uri;
import android.os.AsyncTask;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * An {@link AsyncTask} for loading and parsing media manifests.
 *
 * @param <T> The type of the manifest being parsed.
 */
public class ManifestFetcher<T> extends AsyncTask<String, Void, T> {

  /**
   * Invoked with the result of a manifest fetch.
   *
   * @param <T> The type of the manifest being parsed.
   */
  public interface ManifestCallback<T> {

    /**
     * Invoked from {@link #onPostExecute(Object)} with the parsed manifest.
     *
     * @param contentId The content id of the media.
     * @param manifest The parsed manifest.
     */
    void onManifest(String contentId, T manifest);

    /**
     * Invoked from {@link #onPostExecute(Object)} if an error occurred.
     *
     * @param contentId The content id of the media.
     * @param e The error.
     */
    void onManifestError(String contentId, Exception e);

  }

  public static final int DEFAULT_HTTP_TIMEOUT_MILLIS = 8000;

  private final ManifestParser<T> parser;
  private final ManifestCallback<T> callback;
  private final int timeoutMillis;

  private volatile String contentId;
  private volatile Exception exception;

  /**
   * @param callback The callback to provide with the parsed manifest (or error).
   */
  public ManifestFetcher(ManifestParser<T> parser, ManifestCallback<T> callback) {
    this(parser, callback, DEFAULT_HTTP_TIMEOUT_MILLIS);
  }

  /**
   * @param parser Parses the manifest from the loaded data.
   * @param callback The callback to provide with the parsed manifest (or error).
   * @param timeoutMillis The timeout in milliseconds for the connection used to load the data.
   */
  public ManifestFetcher(ManifestParser<T> parser, ManifestCallback<T> callback,
      int timeoutMillis) {
    this.parser = parser;
    this.callback = callback;
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  protected final T doInBackground(String... data) {
    try {
      contentId = data.length > 1 ? data[1] : null;
      String urlString = data[0];
      String inputEncoding = null;
      InputStream inputStream = null;
      try {
        Uri baseUri = Util.parseBaseUri(urlString);
        HttpURLConnection connection = configureHttpConnection(new URL(urlString));
        inputStream = connection.getInputStream();
        inputEncoding = connection.getContentEncoding();
        return parser.parse(inputStream, inputEncoding, contentId, baseUri);
      } finally {
        if (inputStream != null) {
          inputStream.close();
        }
      }
    } catch (Exception e) {
      exception = e;
      return null;
    }
  }

  @Override
  protected final void onPostExecute(T manifest) {
    if (exception != null) {
      callback.onManifestError(contentId, exception);
    } else {
      callback.onManifest(contentId, manifest);
    }
  }

  private HttpURLConnection configureHttpConnection(URL url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(timeoutMillis);
    connection.setReadTimeout(timeoutMillis);
    connection.setDoOutput(false);
    connection.connect();
    return connection;
  }

}
