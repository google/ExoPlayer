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
package com.google.android.exoplayer2.offline;

import androidx.annotation.Nullable;
import java.io.IOException;

/** Persists {@link DownloadState}s. */
public interface DownloadIndex {

  /**
   * Returns the {@link DownloadState} with the given {@code id}, or null.
   *
   * @param id ID of a {@link DownloadState}.
   * @return The {@link DownloadState} with the given {@code id}, or null if a download state with
   *     this id doesn't exist.
   * @throws IOException If an error occurs reading the state.
   */
  @Nullable
  DownloadState getDownloadState(String id) throws IOException;

  /**
   * Returns a {@link DownloadStateCursor} to {@link DownloadState}s with the given {@code states}.
   *
   * @param states Returns only the {@link DownloadState}s with this states. If empty, returns all.
   * @return A cursor to {@link DownloadState}s with the given {@code states}.
   * @throws IOException If an error occurs reading the state.
   */
  DownloadStateCursor getDownloadStates(@DownloadState.State int... states) throws IOException;

  /**
   * Adds or replaces a {@link DownloadState}.
   *
   * @param downloadState The {@link DownloadState} to be added.
   * @throws IOException If an error occurs setting the state.
   */
  void putDownloadState(DownloadState downloadState) throws IOException;

  /**
   * Removes the {@link DownloadState} with the given {@code id}.
   *
   * @throws IOException If an error occurs removing the state.
   */
  void removeDownloadState(String id) throws IOException;
}
