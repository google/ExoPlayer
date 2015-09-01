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
package com.google.android.exoplayer.playbacktests.util;

import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.upstream.UriLoadable;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;
import com.google.android.exoplayer.util.Util;

import android.content.Context;
import android.os.ConditionVariable;

import java.io.IOException;

/**
 * Utility methods for ExoPlayer playback tests.
 */
public final class TestUtil {

  private TestUtil() {}

  /**
   * Gets a suitable user agent string for ExoPlayer playback tests.
   *
   * @param context A context.
   * @return The user agent.
   */
  public static String getUserAgent(Context context) {
    return Util.getUserAgent(context, "ExoPlayerPlaybackTests");
  }

  /**
   * Loads a manifest.
   *
   * @param context A context.
   * @param url The manifest url.
   * @param parser A suitable parser for the manifest.
   * @return The parser manifest.
   * @throws IOException If an error occurs loading the manifest.
   */
  public static <T> T loadManifest(Context context, String url, UriLoadable.Parser<T> parser)
      throws IOException {
    String userAgent = getUserAgent(context);
    DefaultUriDataSource manifestDataSource = new DefaultUriDataSource(context, userAgent);
    ManifestFetcher<T> manifestFetcher = new ManifestFetcher<>(url, manifestDataSource, parser);
    SyncManifestCallback<T> callback = new SyncManifestCallback<>();
    manifestFetcher.singleLoad(context.getMainLooper(), callback);
    return callback.getResult();
  }

  /**
   * A {@link ManifestCallback} that provides a blocking {@link #getResult()} method for retrieving
   * the result.
   *
   * @param <T> The type of the manifest.
   */
  private static final class SyncManifestCallback<T> implements ManifestCallback<T> {

    private final ConditionVariable haveResultCondition;

    private T result;
    private IOException error;

    public SyncManifestCallback() {
      haveResultCondition = new ConditionVariable();
    }

    @Override
    public void onSingleManifest(T manifest) {
      result = manifest;
      haveResultCondition.open();

    }
    @Override
    public void onSingleManifestError(IOException e) {
      error = e;
      haveResultCondition.open();
    }

    /**
     * Blocks for the result.
     *
     * @return The loaded manifest.
     * @throws IOException If an error occurred loading the manifest.
     */
    public T getResult() throws IOException {
      haveResultCondition.block();
      if (error != null) {
        throw error;
      }
      return result;
    }

  }

}
