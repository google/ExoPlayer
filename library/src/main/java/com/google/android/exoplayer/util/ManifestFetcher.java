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

import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.Loader;
import com.google.android.exoplayer.upstream.UriLoadable;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Pair;

import java.io.IOException;

/**
 * Loads and refreshes a media manifest.
 *
 * @param <T> The type of manifest.
 */
public class ManifestFetcher<T> implements Loader.Callback<UriLoadable<T>> {

  /**
   * Thrown when an error occurs trying to fetch a manifest.
   */
  public static final class ManifestIOException extends IOException{
    public ManifestIOException(Throwable cause) { super(cause); }

  }

  /**
   * Interface definition for a callback to be notified of {@link ManifestFetcher} events.
   */
  public interface EventListener {

    void onManifestRefreshStarted();

    void onManifestRefreshed();

    void onManifestError(IOException e);

  }

  /**
   * Interface for manifests that are able to specify that subsequent loads should use a different
   * URI.
   */
  public interface RedirectingManifest {

    /**
     * Returns the {@link Uri} from which subsequent manifests should be requested, or null to
     * continue using the current {@link Uri}.
     */
    Uri getNextManifestUri();

  }

  private final Loader loader;
  private final UriLoadable.Parser<T> parser;
  private final DataSource dataSource;
  private final Handler eventHandler;
  private final EventListener eventListener;

  private volatile Uri manifestUri;

  private long currentLoadStartTimestamp;

  private volatile T manifest;
  private volatile long manifestLoadStartTimestamp;
  private volatile long manifestLoadCompleteTimestamp;

  /**
   * @param manifestUri The manifest {@link Uri}.
   * @param dataSource The {@link DataSource} to use when loading the manifest.
   * @param parser A parser to parse the loaded manifest data.
   */
  public ManifestFetcher(Uri manifestUri, DataSource dataSource, UriLoadable.Parser<T> parser) {
    this(manifestUri, dataSource, parser, null, null);
  }

  /**
   * @param manifestUri The manifest {@link Uri}.
   * @param dataSource The {@link DataSource} to use when loading the manifest.
   * @param parser A parser to parse the loaded manifest data.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public ManifestFetcher(Uri manifestUri, DataSource dataSource, UriLoadable.Parser<T> parser,
      Handler eventHandler, EventListener eventListener) {
    this.parser = parser;
    this.manifestUri = manifestUri;
    this.dataSource = dataSource;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    loader = new Loader("Loader:ManifestFetcher", 1);
  }

  /**
   * Updates the manifest {@link Uri}.
   *
   * @param manifestUri The manifest {@link Uri}.
   */
  public void updateManifestUri(Uri manifestUri) {
    this.manifestUri = manifestUri;
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
   * Gets the value of {@link SystemClock#elapsedRealtime()} when the last completed load started.
   *
   * @return The value of {@link SystemClock#elapsedRealtime()} when the last completed load
   *     started.
   */
  public long getManifestLoadStartTimestamp() {
    return manifestLoadStartTimestamp;
  }

  /**
   * Gets the value of {@link SystemClock#elapsedRealtime()} when the last load completed.
   *
   * @return The value of {@link SystemClock#elapsedRealtime()} when the last load completed.
   */
  public long getManifestLoadCompleteTimestamp() {
    return manifestLoadCompleteTimestamp;
  }

  /**
   * Throws the error that affected the most recent attempt to load the manifest. Does nothing if
   * the most recent attempt was successful.
   *
   * @throws ManifestIOException The error that affected the most recent attempt to load the
   *     manifest.
   */
  public void maybeThrowError() throws ManifestIOException {
    if (loader != null) {
      try {
        loader.maybeThrowError();
      } catch (IOException e) {
        throw new ManifestIOException(e);
      }
    }
  }

  /**
   * Should be invoked repeatedly by callers who require an updated manifest.
   */
  public void requestRefresh() {
    if (loader.isLoading()) {
      return;
    }
    currentLoadStartTimestamp = SystemClock.elapsedRealtime();
    loader.startLoading(new UriLoadable<>(manifestUri, dataSource, parser), this);
    notifyManifestRefreshStarted();
  }

  /**
   * Releases the fetcher.
   * <p>
   * This method should be called when the fetcher is no longer required.
   */
  public void release() {
    loader.release();
  }

  // Loadable.Callback implementation.

  @Override
  public void onLoadCompleted(UriLoadable<T> loadable, long elapsedMs) {
    manifest = loadable.getResult();
    manifestLoadStartTimestamp = currentLoadStartTimestamp;
    manifestLoadCompleteTimestamp = SystemClock.elapsedRealtime();
    if (manifest instanceof RedirectingManifest) {
      RedirectingManifest redirectingManifest = (RedirectingManifest) manifest;
      Uri nextUri = redirectingManifest.getNextManifestUri();
      if (nextUri != null) {
        manifestUri = nextUri;
      }
    }
    notifyManifestRefreshed();
  }

  @Override
  public void onLoadCanceled(UriLoadable<T> loadable, long elapsedMs) {
    // Do nothing.
  }

  @Override
  public int onLoadError(UriLoadable<T> loadable, long elapsedMs, IOException exception) {
    notifyManifestError(new ManifestIOException(exception));
    return Loader.RETRY;
  }

  // Private methods.

  private void notifyManifestRefreshStarted() {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onManifestRefreshStarted();
        }
      });
    }
  }

  private void notifyManifestRefreshed() {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onManifestRefreshed();
        }
      });
    }
  }

  private void notifyManifestError(final IOException e) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onManifestError(e);
        }
      });
    }
  }

}
