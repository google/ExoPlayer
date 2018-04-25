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

import com.google.android.exoplayer2.C;
import java.io.IOException;

/**
 * An interface for stream downloaders.
 */
public interface Downloader {

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
   * @throws DownloadException Thrown if the media cannot be downloaded.
   * @throws InterruptedException If the thread has been interrupted.
   * @throws IOException Thrown when there is an io error while downloading.
   */
  void download() throws InterruptedException, IOException;

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
