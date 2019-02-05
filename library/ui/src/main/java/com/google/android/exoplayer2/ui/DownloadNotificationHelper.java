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
import android.app.PendingIntent;
import android.content.Context;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.DownloadState;

/** Helper for creating download notifications. */
public final class DownloadNotificationHelper {

  private static final @StringRes int NULL_STRING_ID = 0;

  private final Context context;
  private final NotificationCompat.Builder notificationBuilder;

  /**
   * @param context A context.
   * @param channelId The id of the notification channel to use.
   */
  public DownloadNotificationHelper(Context context, String channelId) {
    context = context.getApplicationContext();
    this.context = context;
    this.notificationBuilder = new NotificationCompat.Builder(context, channelId);
  }

  /**
   * Returns a progress notification for the given download states.
   *
   * @param smallIcon A small icon for the notification.
   * @param contentIntent An optional content intent to send when the notification is clicked.
   * @param message An optional message to display on the notification.
   * @param downloadStates The download states.
   * @return The notification.
   */
  public Notification buildProgressNotification(
      @DrawableRes int smallIcon,
      @Nullable PendingIntent contentIntent,
      @Nullable String message,
      DownloadState[] downloadStates) {
    float totalPercentage = 0;
    int downloadTaskCount = 0;
    boolean allDownloadPercentagesUnknown = true;
    boolean haveDownloadedBytes = false;
    boolean haveDownloadTasks = false;
    boolean haveRemoveTasks = false;
    for (DownloadState downloadState : downloadStates) {
      if (downloadState.state == DownloadState.STATE_REMOVING
          || downloadState.state == DownloadState.STATE_RESTARTING
          || downloadState.state == DownloadState.STATE_REMOVED) {
        haveRemoveTasks = true;
        continue;
      }
      if (downloadState.state != DownloadState.STATE_DOWNLOADING
          && downloadState.state != DownloadState.STATE_COMPLETED) {
        continue;
      }
      haveDownloadTasks = true;
      if (downloadState.downloadPercentage != C.PERCENTAGE_UNSET) {
        allDownloadPercentagesUnknown = false;
        totalPercentage += downloadState.downloadPercentage;
      }
      haveDownloadedBytes |= downloadState.downloadedBytes > 0;
      downloadTaskCount++;
    }

    int titleStringId =
        haveDownloadTasks
            ? R.string.exo_download_downloading
            : (haveRemoveTasks ? R.string.exo_download_removing : NULL_STRING_ID);
    int progress = 0;
    boolean indeterminate = true;
    if (haveDownloadTasks) {
      progress = (int) (totalPercentage / downloadTaskCount);
      indeterminate = allDownloadPercentagesUnknown && haveDownloadedBytes;
    }
    return buildNotification(
        smallIcon,
        contentIntent,
        message,
        titleStringId,
        progress,
        indeterminate,
        /* ongoing= */ true,
        /* showWhen= */ false);
  }

  /**
   * Returns a notification for a completed download.
   *
   * @param smallIcon A small icon for the notifications.
   * @param contentIntent An optional content intent to send when the notification is clicked.
   * @param message An optional message to display on the notification.
   * @return The notification.
   */
  public Notification buildDownloadCompletedNotification(
      @DrawableRes int smallIcon, @Nullable PendingIntent contentIntent, @Nullable String message) {
    int titleStringId = R.string.exo_download_completed;
    return buildEndStateNotification(smallIcon, contentIntent, message, titleStringId);
  }

  /**
   * Returns a notification for a failed download.
   *
   * @param smallIcon A small icon for the notifications.
   * @param contentIntent An optional content intent to send when the notification is clicked.
   * @param message An optional message to display on the notification.
   * @return The notification.
   */
  public Notification buildDownloadFailedNotification(
      @DrawableRes int smallIcon, @Nullable PendingIntent contentIntent, @Nullable String message) {
    @StringRes int titleStringId = R.string.exo_download_failed;
    return buildEndStateNotification(smallIcon, contentIntent, message, titleStringId);
  }

  private Notification buildEndStateNotification(
      @DrawableRes int smallIcon,
      @Nullable PendingIntent contentIntent,
      @Nullable String message,
      @StringRes int titleStringId) {
    return buildNotification(
        smallIcon,
        contentIntent,
        message,
        titleStringId,
        /* progress= */ 0,
        /* indeterminateProgress= */ false,
        /* ongoing= */ false,
        /* showWhen= */ true);
  }

  private Notification buildNotification(
      @DrawableRes int smallIcon,
      @Nullable PendingIntent contentIntent,
      @Nullable String message,
      @StringRes int titleStringId,
      int progress,
      boolean indeterminateProgress,
      boolean ongoing,
      boolean showWhen) {
    notificationBuilder.setSmallIcon(smallIcon);
    notificationBuilder.setContentTitle(
        titleStringId == NULL_STRING_ID ? null : context.getResources().getString(titleStringId));
    notificationBuilder.setContentIntent(contentIntent);
    notificationBuilder.setStyle(
        message == null ? null : new NotificationCompat.BigTextStyle().bigText(message));
    notificationBuilder.setProgress(/* max= */ 100, progress, indeterminateProgress);
    notificationBuilder.setOngoing(ongoing);
    notificationBuilder.setShowWhen(showWhen);
    return notificationBuilder.build();
  }
}
