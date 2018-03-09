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
package com.google.android.exoplayer2.ui;

import android.app.Notification;
import android.app.Notification.BigTextStyle;
import android.app.Notification.Builder;
import android.content.Context;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadState;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.Util;

/** Helper class to create notifications for downloads using {@link DownloadManager}. */
public final class DownloadNotificationUtil {

  private DownloadNotificationUtil() {}

  /**
   * Returns a notification for the given {@link DownloadState}, or null if no notification should
   * be displayed.
   *
   * @param downloadState State of the download.
   * @param context Used to access resources.
   * @param smallIcon A small icon for the notifications.
   * @param channelId The id of the notification channel to use. Only required for API level 26 and
   *     above.
   * @param message An optional message to display on the notification.
   * @param errorMessageProvider An optional {@link ErrorMessageProvider} for translating download
   *     errors into readable error messages. If not null and there is a download error then the
   *     error message is displayed instead of {@code message}.
   * @return A notification for the given {@link DownloadState}, or null if no notification should
   *     be displayed.
   */
  public static @Nullable Notification createNotification(
      DownloadState downloadState,
      Context context,
      int smallIcon,
      String channelId,
      @Nullable String message,
      @Nullable ErrorMessageProvider<Throwable> errorMessageProvider) {
    if (downloadState.downloadAction.isRemoveAction()
        || downloadState.state == DownloadState.STATE_CANCELED) {
      return null;
    }

    Builder notificationBuilder = new Builder(context);
    if (Util.SDK_INT >= 26) {
      notificationBuilder.setChannelId(channelId);
    }
    notificationBuilder.setSmallIcon(smallIcon);

    int titleStringId = getTitleStringId(downloadState);
    notificationBuilder.setContentTitle(context.getResources().getString(titleStringId));

    if (downloadState.state == DownloadState.STATE_STARTED) {
      notificationBuilder.setOngoing(true);
      float percentage = downloadState.downloadPercentage;
      boolean indeterminate = Float.isNaN(percentage);
      notificationBuilder.setProgress(100, indeterminate ? 0 : (int) percentage, indeterminate);
    }
    if (Util.SDK_INT >= 17) {
      // Hide timestamp on the notification while download progresses.
      notificationBuilder.setShowWhen(downloadState.state != DownloadState.STATE_STARTED);
    }

    if (downloadState.error != null && errorMessageProvider != null) {
      message = errorMessageProvider.getErrorMessage(downloadState.error).second;
    }
    if (message != null) {
      if (Util.SDK_INT >= 16) {
        notificationBuilder.setStyle(new BigTextStyle().bigText(message));
      } else {
        notificationBuilder.setContentText(message);
      }
    }
    return notificationBuilder.getNotification();
  }

  private static int getTitleStringId(DownloadState downloadState) {
    int titleStringId;
    switch (downloadState.state) {
      case DownloadState.STATE_QUEUED:
        titleStringId = R.string.exo_download_queued;
        break;
      case DownloadState.STATE_STARTED:
        titleStringId = R.string.exo_downloading;
        break;
      case DownloadState.STATE_ENDED:
        titleStringId = R.string.exo_download_completed;
        break;
      case DownloadState.STATE_ERROR:
        titleStringId = R.string.exo_download_failed;
        break;
      case DownloadState.STATE_CANCELED:
      default:
        // Never happens.
        throw new IllegalStateException();
    }
    return titleStringId;
  }
}
