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

import java.io.IOException;

/** An writable index of {@link Download Downloads}. */
public interface WritableDownloadIndex extends DownloadIndex {

  /**
   * Adds or replaces a {@link Download}.
   *
   * @param download The {@link Download} to be added.
   * @throws throws IOException If an error occurs setting the state.
   */
  void putDownload(Download download) throws IOException;

  /**
   * Removes the {@link Download} with the given {@code id}.
   *
   * @param id ID of a {@link Download}.
   * @throws throws IOException If an error occurs removing the state.
   */
  void removeDownload(String id) throws IOException;
  /**
   * Sets the stop reason of the downloads in a terminal state ({@link Download#STATE_COMPLETED},
   * {@link Download#STATE_FAILED}).
   *
   * @param stopReason The stop reason.
   * @throws throws IOException If an error occurs updating the state.
   */
  void setStopReason(int stopReason) throws IOException;

  /**
   * Sets the stop reason of the download with the given {@code id} in a terminal state ({@link
   * Download#STATE_COMPLETED}, {@link Download#STATE_FAILED}).
   *
   * <p>If there's no {@link Download} with the given {@code id} or it isn't in a terminal state,
   * then nothing happens.
   *
   * @param id ID of a {@link Download}.
   * @param stopReason The stop reason.
   * @throws throws IOException If an error occurs updating the state.
   */
  void setStopReason(String id, int stopReason) throws IOException;
}
