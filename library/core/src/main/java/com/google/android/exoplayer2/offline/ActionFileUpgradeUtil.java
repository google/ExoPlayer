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

import static com.google.android.exoplayer2.offline.Download.STATE_QUEUED;

import androidx.annotation.Nullable;
import java.io.File;
import java.io.IOException;

/** Utility class for upgrading legacy action files into {@link DefaultDownloadIndex}. */
public final class ActionFileUpgradeUtil {

  /** Provides download IDs during action file upgrade. */
  public interface DownloadIdProvider {

    /**
     * Returns a download id for given action.
     *
     * @param downloadAction The action for which an ID is required.
     * @return A corresponding download ID.
     */
    String getId(DownloadAction downloadAction);
  }

  private ActionFileUpgradeUtil() {}

  /**
   * Merges {@link DownloadAction DownloadActions} contained in a legacy action file into a {@link
   * DefaultDownloadIndex}, deleting the action file if the merge is successful or if {@code
   * deleteOnFailure} is {@code true}.
   *
   * <p>This method must not be called while the {@link DefaultDownloadIndex} is being used by a
   * {@link DownloadManager}.
   *
   * @param actionFilePath The action file path.
   * @param downloadIdProvider A download ID provider, or {@code null}. If {@code null} then ID of
   *     each download will be its custom cache key if one is specified, or else its URL.
   * @param downloadIndex The index into which the action will be merged.
   * @param deleteOnFailure Whether to delete the action file if the merge fails.
   * @throws IOException If an error occurs loading or merging the actions.
   */
  @SuppressWarnings("deprecation")
  public static void upgradeAndDelete(
      File actionFilePath,
      @Nullable DownloadIdProvider downloadIdProvider,
      DefaultDownloadIndex downloadIndex,
      boolean deleteOnFailure)
      throws IOException {
    ActionFile actionFile = new ActionFile(actionFilePath);
    if (actionFile.exists()) {
      boolean success = false;
      try {
        for (DownloadAction action : actionFile.load()) {
          if (downloadIdProvider != null) {
            action = action.copyWithId(downloadIdProvider.getId(action));
          }
          mergeAction(action, downloadIndex);
        }
        success = true;
      } finally {
        if (success || deleteOnFailure) {
          actionFile.delete();
        }
      }
    }
  }

  /**
   * Merges a {@link DownloadAction} into a {@link DefaultDownloadIndex}.
   *
   * @param action The action to be merged.
   * @param downloadIndex The index into which the action will be merged.
   * @throws IOException If an error occurs merging the action.
   */
  /* package */ static void mergeAction(DownloadAction action, DefaultDownloadIndex downloadIndex)
      throws IOException {
    Download download = downloadIndex.getDownload(action.id);
    if (download != null) {
      download = DownloadManager.mergeAction(download, action, download.manualStopReason);
    } else {
      long nowMs = System.currentTimeMillis();
      download =
          new Download(
              action,
              STATE_QUEUED,
              Download.FAILURE_REASON_NONE,
              Download.MANUAL_STOP_REASON_NONE,
              /* startTimeMs= */ nowMs,
              /* updateTimeMs= */ nowMs);
    }
    downloadIndex.putDownload(download);
  }
}
