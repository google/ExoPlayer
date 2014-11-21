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

import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.Loader.Loadable;

import android.os.Looper;
import android.os.SystemClock;
import android.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.CancellationException;

/**
 * Performs both single and repeated loads of media manfifests.
 *
 * @param <T> The type of manifest.
 */
public class ManifestFetcher<T> implements Loader.Callback {

  /**
   * Callback for the result of a single load.
   *
   * @param <T> The type of manifest.
   */
  public interface ManifestCallback<T> {

    /**
     * Invoked when the load has successfully completed.
     *
     * @param contentId The content id of the media.
     * @param manifest The loaded manifest.
     */
    void onManifest(String contentId, T manifest);

    /**
     * Invoked when the load has failed.
     *
     * @param contentId The content id of the media.
     * @param e The cause of the failure.
     */
    void onManifestError(String contentId, IOException e);

  }

  /* package */ final ManifestParser<T> parser;
  /* package */ final String manifestUrl;
  /* package */ final String contentId;
  /* package */ final String userAgent;

  private int enabledCount;
  private Loader loader;
  private ManifestLoadable currentLoadable;

  private int loadExceptionCount;
  private long loadExceptionTimestamp;
  private IOException loadException;

  private volatile T manifest;
  private volatile long manifestLoadTimestamp;

  /**
   * @param parser A parser to parse the loaded manifest data.
   * @param contentId The content id of the content being loaded. May be null.
   * @param manifestUrl The manifest location.
   * @param userAgent The User-Agent string that should be used.
   */
  public ManifestFetcher(ManifestParser<T> parser, String contentId, String manifestUrl,
      String userAgent) {
    this.parser = parser;
    this.contentId = contentId;
    this.manifestUrl = manifestUrl;
    this.userAgent = userAgent;
  }

  /**
   * Performs a single manifest load.
   *
   * @param callbackLooper The looper associated with the thread on which the callback should be
   *     invoked.
   * @param callback The callback to receive the result.
   */
  public void singleLoad(Looper callbackLooper, final ManifestCallback<T> callback) {
    SingleFetchHelper fetchHelper = new SingleFetchHelper(callbackLooper, callback);
    fetchHelper.startLoading();
  }

  /**
   * Gets a {@link Pair} containing the most recently loaded manifest together with the timestamp
   * at which the load completed.
   *
   * @return The most recently loaded manifest and the timestamp at which the load completed, or
   *     null if no manifest has loaded.
   */
  public T getManifest() {
    return manifest;
  }

  /**
   * Gets the value of {@link SystemClock#elapsedRealtime()} when the last load completed.
   *
   * @return The value of {@link SystemClock#elapsedRealtime()} when the last load completed.
   */
  public long getManifestLoadTimestamp() {
    return manifestLoadTimestamp;
  }

  /**
   * Gets the error that affected the most recent attempt to load the manifest, or null if the
   * most recent attempt was successful.
   *
   * @return The error, or null if the most recent attempt was successful.
   */
  public IOException getError() {
    if (loadExceptionCount <= 1) {
      // Don't report an exception until at least 1 retry attempt has been made.
      return null;
    }
    return loadException;
  }

  /**
   * Enables refresh functionality.
   */
  public void enable() {
    if (enabledCount++ == 0) {
      loadExceptionCount = 0;
      loadException = null;
    }
  }

  /**
   * Disables refresh functionality.
   */
  public void disable() {
    if (--enabledCount == 0) {
      if (loader != null) {
        loader.release();
        loader = null;
      }
    }
  }

  /**
   * Should be invoked repeatedly by callers who require an updated manifest.
   */
  public void requestRefresh() {
    if (loadException != null && SystemClock.elapsedRealtime()
        < (loadExceptionTimestamp + getRetryDelayMillis(loadExceptionCount))) {
      // The previous load failed, and it's too soon to try again.
      return;
    }
    if (loader == null) {
      loader = new Loader("manifestLoader");
    }
    if (!loader.isLoading()) {
      currentLoadable = new ManifestLoadable();
      loader.startLoading(currentLoadable, this);
    }
  }

  @Override
  public void onLoadCompleted(Loadable loadable) {
    if (currentLoadable != loadable) {
      // Stale event.
      return;
    }

    manifest = currentLoadable.result;
    manifestLoadTimestamp = SystemClock.elapsedRealtime();
    loadExceptionCount = 0;
    loadException = null;
  }

  @Override
  public void onLoadCanceled(Loadable loadable) {
    // Do nothing.
  }

  @Override
  public void onLoadError(Loadable loadable, IOException exception) {
    if (currentLoadable != loadable) {
      // Stale event.
      return;
    }

    loadExceptionCount++;
    loadExceptionTimestamp = SystemClock.elapsedRealtime();
    loadException = new IOException(exception);
  }

  private long getRetryDelayMillis(long errorCount) {
    return Math.min((errorCount - 1) * 1000, 5000);
  }

  private class SingleFetchHelper implements Loader.Callback {

    private final Looper callbackLooper;
    private final ManifestCallback<T> wrappedCallback;
    private final Loader singleUseLoader;
    private final ManifestLoadable singleUseLoadable;

    public SingleFetchHelper(Looper callbackLooper, ManifestCallback<T> wrappedCallback) {
      this.callbackLooper = callbackLooper;
      this.wrappedCallback = wrappedCallback;
      singleUseLoader = new Loader("manifestLoader:single");
      singleUseLoadable = new ManifestLoadable();
    }

    public void startLoading() {
      singleUseLoader.startLoading(callbackLooper, singleUseLoadable, this);
    }

    @Override
    public void onLoadCompleted(Loadable loadable) {
      try {
        manifest = singleUseLoadable.result;
        manifestLoadTimestamp = SystemClock.elapsedRealtime();
        wrappedCallback.onManifest(contentId, singleUseLoadable.result);
      } finally {
        releaseLoader();
      }
    }

    @Override
    public void onLoadCanceled(Loadable loadable) {
      // This shouldn't ever happen, but handle it anyway.
      try {
        IOException exception = new IOException("Load cancelled", new CancellationException());
        wrappedCallback.onManifestError(contentId, exception);
      } finally {
        releaseLoader();
      }
    }

    @Override
    public void onLoadError(Loadable loadable, IOException exception) {
      try {
        wrappedCallback.onManifestError(contentId, exception);
      } finally {
        releaseLoader();
      }
    }

    private void releaseLoader() {
      singleUseLoader.release();
    }

  }

  private class ManifestLoadable implements Loadable {

    private static final int TIMEOUT_MILLIS = 10000;

    /* package */ volatile T result;
    private volatile boolean isCanceled;

    @Override
    public void cancelLoad() {
      // We don't actually cancel anything, but we need to record the cancellation so that
      // isLoadCanceled can return the correct value.
      isCanceled = true;
    }

    @Override
    public boolean isLoadCanceled() {
      return isCanceled;
    }

    @Override
    public void load() throws IOException, InterruptedException {
      String inputEncoding;
      InputStream inputStream = null;
      try {
        URLConnection connection = configureConnection(new URL(manifestUrl));
        inputStream = connection.getInputStream();
        inputEncoding = connection.getContentEncoding();
        result = parser.parse(inputStream, inputEncoding, contentId,
            Util.parseBaseUri(connection.getURL().toString()));
      } finally {
        if (inputStream != null) {
          inputStream.close();
        }
      }
    }

    private URLConnection configureConnection(URL url) throws IOException {
      URLConnection connection = url.openConnection();
      connection.setConnectTimeout(TIMEOUT_MILLIS);
      connection.setReadTimeout(TIMEOUT_MILLIS);
      connection.setDoOutput(false);
      connection.setRequestProperty("User-Agent", userAgent);
      connection.connect();
      return connection;
    }

  }

}
