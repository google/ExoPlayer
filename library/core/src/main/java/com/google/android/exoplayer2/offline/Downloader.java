/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.io.IOException;

/**
 * An interface for stream downloaders.
 */
public interface Downloader {

  /**
   * Listener notified when download progresses.
   * <p>
   * No guarantees are made about the thread or threads on which the listener is called, but it is
   * guaranteed that listener methods will be called in a serial fashion (i.e. one at a time) and in
   * the same order as events occurred.
   */
  interface ProgressListener {
    /**
     * Called during the download. Calling intervals depend on the {@link Downloader}
     * implementation.
     *
     * @param downloader The reporting instance.
     * @param downloadPercentage The download percentage. This value can be an estimation.
     * @param downloadedBytes Total number of downloaded bytes.
     * @see #download(ProgressListener)
     */
    void onDownloadProgress(Downloader downloader, float downloadPercentage, long downloadedBytes);
  }

  /**
   * Initializes the downloader.
   *
   * @throws DownloadException Thrown if the media cannot be downloaded.
   * @throws InterruptedException If the thread has been interrupted.
   * @throws IOException Thrown when there is an io error while reading from cache.
   * @see #getDownloadedBytes()
   * @see #getDownloadPercentage()
   */
  void init() throws InterruptedException, IOException;

  /**
   * Downloads the media.
   *
   * @param listener If not null, called during download.
   * @throws DownloadException Thrown if the media cannot be downloaded.
   * @throws InterruptedException If the thread has been interrupted.
   * @throws IOException Thrown when there is an io error while downloading.
   */
  void download(@Nullable ProgressListener listener)
      throws InterruptedException, IOException;

  /**
   * Removes all of the downloaded data of the media.
   *
   * @throws InterruptedException Thrown if the thread was interrupted.
   */
  void remove() throws InterruptedException;

  /**
   * Returns the total number of downloaded bytes, or {@link C#LENGTH_UNSET} if it hasn't been
   * calculated yet.
   *
   * @see #init()
   */
  long getDownloadedBytes();

  /**
   * Returns the download percentage, or {@link Float#NaN} if it can't be calculated yet. This
   * value can be an estimation.
   *
   * @see #init()
   */
  float getDownloadPercentage();

}
