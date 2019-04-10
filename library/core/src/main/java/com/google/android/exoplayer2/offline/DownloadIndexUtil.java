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

/** {@link DownloadIndex} related utility methods. */
public final class DownloadIndexUtil {

  /** An interface to provide custom download ids during ActionFile upgrade. */
  public interface DownloadIdProvider {

    /**
     * Returns a custom download id for given action.
     *
     * @param downloadAction The action which is an id requested for.
     * @return A custom download id for given action.
     */
    String getId(DownloadAction downloadAction);
  }

  private DownloadIndexUtil() {}

  /**
   * Merges {@link DownloadAction DownloadActions} contained in an {@link ActionFile} into a {@link
   * DownloadIndex}.
   *
   * <p>This method must not be called while the {@link DownloadIndex} is being used by a {@link
   * DownloadManager}.
   *
   * @param actionFile The action file.
   * @param downloadIdProvider A custom download id provider, or {@code null}.
   * @param downloadIndex The index into which the action will be merged.
   * @throws IOException If an error occurs loading or merging the actions.
   */
  public static void mergeActionFile(
      ActionFile actionFile,
      @Nullable DownloadIdProvider downloadIdProvider,
      DefaultDownloadIndex downloadIndex)
      throws IOException {
    for (DownloadAction action : actionFile.load()) {
      if (downloadIdProvider != null) {
        action = action.copyWithId(downloadIdProvider.getId(action));
      }
      mergeAction(action, downloadIndex);
    }
  }

  /**
   * Merges a {@link DownloadAction} into a {@link DownloadIndexUtil}.
   *
   * @param action The action to be merged.
   * @param downloadIndex The index into which the action will be merged.
   * @throws IOException If an error occurs merging the action.
   */
  /* package */ static void mergeAction(DownloadAction action, DefaultDownloadIndex downloadIndex)
      throws IOException {
    Download download = downloadIndex.getDownload(action.id);
    if (download != null) {
      download = download.copyWithMergedAction(action, /* canStart= */ true);
    } else {
      download = new Download(action);
    }
    downloadIndex.putDownload(download);
  }
}
