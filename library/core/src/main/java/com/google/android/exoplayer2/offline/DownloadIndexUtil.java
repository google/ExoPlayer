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

import android.support.annotation.Nullable;
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
   * Upgrades an {@link ActionFile} to {@link DownloadIndex}.
   *
   * <p>This method shouldn't be called while {@link DownloadIndex} is used by {@link
   * DownloadManager}.
   *
   * @param actionFile The action file to upgrade.
   * @param downloadIndex Actions are converted to {@link DownloadState}s and stored in this index.
   * @param downloadIdProvider A nullable custom download id provider.
   * @throws IOException If there is an error during loading actions.
   */
  public static void upgradeActionFile(
      ActionFile actionFile,
      DownloadIndex downloadIndex,
      @Nullable DownloadIdProvider downloadIdProvider)
      throws IOException {
    if (downloadIdProvider == null) {
      downloadIdProvider = downloadAction -> downloadAction.id;
    }
    for (DownloadAction action : actionFile.load()) {
      addAction(downloadIndex, downloadIdProvider.getId(action), action);
    }
  }

  /**
   * Converts a {@link DownloadAction} to {@link DownloadState} and stored in the given {@link
   * DownloadIndex}.
   *
   * <p>This method shouldn't be called while {@link DownloadIndex} is used by {@link
   * DownloadManager}.
   *
   * @param downloadIndex The action is converted to {@link DownloadState} and stored in this index.
   * @param id A nullable custom download id which overwrites {@link DownloadAction#id}.
   * @param action The action to be stored in {@link DownloadIndex}.
   */
  public static void addAction(
      DownloadIndex downloadIndex, @Nullable String id, DownloadAction action) {
    DownloadState downloadState = downloadIndex.getDownloadState(id != null ? id : action.id);
    if (downloadState != null) {
      downloadState = downloadState.mergeAction(action);
    } else {
      downloadState = new DownloadState(action);
    }
    downloadIndex.putDownloadState(downloadState);
  }
}
