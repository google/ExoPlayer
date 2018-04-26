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
import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadState;
import com.google.android.exoplayer2.util.ErrorMessageProvider;

/** Helper class to create notifications for downloads using {@link DownloadManager}. */
public final class DownloadNotificationUtil {

  private static final int NULL_STRING_ID = 0;

  private DownloadNotificationUtil() {}

  /**
   * Returns a progress notification for the given {@link DownloadState}s.
   *
   * @param downloadStates States of the downloads.
   * @param context Used to access resources.
   * @param smallIcon A small icon for the notification.
   * @param channelId The id of the notification channel to use. Only required for API level 26 and
   *     above.
   * @param message An optional message to display on the notification.
   * @return A progress notification for the given {@link DownloadState}s.
   */
  public static @Nullable Notification createProgressNotification(
      DownloadState[] downloadStates,
      Context context,
      int smallIcon,
      String channelId,
      @Nullable String message) {
    float totalPercentage = 0;
    int downloadTaskCount = 0;
    boolean allDownloadPercentagesUnknown = true;
    boolean haveDownloadedBytes = false;
    for (DownloadState downloadState : downloadStates) {
      if (downloadState.downloadAction.isRemoveAction
          || downloadState.state != DownloadState.STATE_STARTED) {
        continue;
      }
      if (downloadState.downloadPercentage != C.PERCENTAGE_UNSET) {
        allDownloadPercentagesUnknown = false;
        totalPercentage += downloadState.downloadPercentage;
      }
      haveDownloadedBytes |= downloadState.downloadedBytes > 0;
      downloadTaskCount++;
    }

    boolean haveDownloadTasks = downloadTaskCount > 0;
    int titleStringId = haveDownloadTasks ? R.string.exo_download_downloading : NULL_STRING_ID;
    NotificationCompat.Builder notificationBuilder =
        createNotificationBuilder(context, smallIcon, channelId, message, titleStringId);

    int progress = haveDownloadTasks ? (int) (totalPercentage / downloadTaskCount) : 0;
    boolean indeterminate = allDownloadPercentagesUnknown && haveDownloadedBytes;
    notificationBuilder.setProgress(/* max= */ 100, progress, indeterminate);
    notificationBuilder.setOngoing(true);
    notificationBuilder.setShowWhen(false);
    return notificationBuilder.build();
  }

  /**
   * Returns a notification for a {@link DownloadState} which is in either {@link
   * DownloadState#STATE_ENDED} or {@link DownloadState#STATE_ERROR} states. Returns null if it's
   * some other state or it's state of a remove action.
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
   * @return A notification for a {@link DownloadState} which is in either {@link
   *     DownloadState#STATE_ENDED} or {@link DownloadState#STATE_ERROR} states. Returns null if
   *     it's some other state or it's state of a remove action.
   */
  public static @Nullable Notification createDownloadFinishedNotification(
      DownloadState downloadState,
      Context context,
      int smallIcon,
      String channelId,
      @Nullable String message,
      @Nullable ErrorMessageProvider<Throwable> errorMessageProvider) {
    if (downloadState.downloadAction.isRemoveAction
        || (downloadState.state != DownloadState.STATE_ENDED
            && downloadState.state != DownloadState.STATE_ERROR)) {
      return null;
    }
    if (downloadState.error != null && errorMessageProvider != null) {
      message = errorMessageProvider.getErrorMessage(downloadState.error).second;
    }
    int titleStringId =
        downloadState.state == DownloadState.STATE_ENDED
            ? R.string.exo_download_completed
            : R.string.exo_download_failed;
    NotificationCompat.Builder notificationBuilder =
        createNotificationBuilder(context, smallIcon, channelId, message, titleStringId);
    return notificationBuilder.build();
  }

  private static NotificationCompat.Builder createNotificationBuilder(
      Context context,
      int smallIcon,
      String channelId,
      @Nullable String message,
      int titleStringId) {
    NotificationCompat.Builder notificationBuilder =
        new NotificationCompat.Builder(context, channelId).setSmallIcon(smallIcon);
    if (titleStringId != NULL_STRING_ID) {
      notificationBuilder.setContentTitle(context.getResources().getString(titleStringId));
    }
    if (message != null) {
      notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(message));
    }
    return notificationBuilder;
  }
}
