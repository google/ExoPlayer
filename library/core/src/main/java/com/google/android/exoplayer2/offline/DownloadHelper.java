/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.offline;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.List;

/**
 * A helper for initializing and removing downloads.
 *
 * @param <T> The manifest type.
 */
public abstract class DownloadHelper<T> {

  /** A callback to be notified when the {@link DownloadHelper} is prepared. */
  public interface Callback {

    /**
     * Called when preparation completes.
     *
     * @param helper The reporting {@link DownloadHelper}.
     */
    void onPrepared(DownloadHelper helper);

    /**
     * Called when preparation fails.
     *
     * @param helper The reporting {@link DownloadHelper}.
     * @param e The error.
     */
    void onPrepareError(DownloadHelper helper, IOException e);
  }

  private final String downloadType;
  private final Uri uri;
  @Nullable private final String cacheKey;

  @Nullable private T manifest;
  @Nullable private TrackGroupArray[] trackGroupArrays;

  /**
   * Create download helper.
   *
   * @param downloadType A download type. This value will be used as {@link DownloadAction#type}.
   * @param uri A {@link Uri}.
   * @param cacheKey An optional cache key.
   */
  public DownloadHelper(String downloadType, Uri uri, @Nullable String cacheKey) {
    this.downloadType = downloadType;
    this.uri = uri;
    this.cacheKey = cacheKey;
  }

  /**
   * Initializes the helper for starting a download.
   *
   * @param callback A callback to be notified when preparation completes or fails. The callback
   *     will be invoked on the calling thread unless that thread does not have an associated {@link
   *     Looper}, in which case it will be called on the application's main thread.
   */
  public final void prepare(final Callback callback) {
    final Handler handler =
        new Handler(Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper());
    new Thread() {
      @Override
      public void run() {
        try {
          manifest = loadManifest(uri);
          trackGroupArrays = getTrackGroupArrays(manifest);
          handler.post(() -> callback.onPrepared(DownloadHelper.this));
        } catch (final IOException e) {
          handler.post(() -> callback.onPrepareError(DownloadHelper.this, e));
        }
      }
    }.start();
  }

  /** Returns the manifest. Must not be called until after preparation completes. */
  public final T getManifest() {
    Assertions.checkNotNull(manifest);
    return manifest;
  }

  /**
   * Returns the number of periods for which media is available. Must not be called until after
   * preparation completes.
   */
  public final int getPeriodCount() {
    Assertions.checkNotNull(trackGroupArrays);
    return trackGroupArrays.length;
  }

  /**
   * Returns the track groups for the given period. Must not be called until after preparation
   * completes.
   *
   * @param periodIndex The period index.
   * @return The track groups for the period. May be {@link TrackGroupArray#EMPTY} for single stream
   *     content.
   */
  public final TrackGroupArray getTrackGroups(int periodIndex) {
    Assertions.checkNotNull(trackGroupArrays);
    return trackGroupArrays[periodIndex];
  }

  /**
   * Builds a {@link DownloadAction} for downloading the specified tracks. Must not be called until
   * after preparation completes.
   *
   * @param data Application provided data to store in {@link DownloadAction#data}.
   * @param trackKeys The selected tracks. If empty, all streams will be downloaded.
   * @return The built {@link DownloadAction}.
   */
  public final DownloadAction getDownloadAction(@Nullable byte[] data, List<TrackKey> trackKeys) {
    return DownloadAction.createDownloadAction(
        downloadType, uri, toStreamKeys(trackKeys), cacheKey, data);
  }

  /**
   * Builds a {@link DownloadAction} for removing the media. May be called in any state.
   *
   * @return The built {@link DownloadAction}.
   */
  public final DownloadAction getRemoveAction() {
    return DownloadAction.createRemoveAction(downloadType, uri, cacheKey);
  }

  /**
   * Loads the manifest. This method is called on a background thread.
   *
   * @param uri The manifest uri.
   * @throws IOException If loading fails.
   */
  protected abstract T loadManifest(Uri uri) throws IOException;

  /**
   * Returns the track group arrays for each period in the manifest.
   *
   * @param manifest The manifest.
   * @return An array of {@link TrackGroupArray}s. One for each period in the manifest.
   */
  protected abstract TrackGroupArray[] getTrackGroupArrays(T manifest);

  /**
   * Converts a list of {@link TrackKey track keys} to {@link StreamKey stream keys}.
   *
   * @param trackKeys A list of track keys.
   * @return A corresponding list of stream keys.
   */
  protected abstract List<StreamKey> toStreamKeys(List<TrackKey> trackKeys);
}
