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

import static androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import androidx.annotation.DoNotInline;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.core.R;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.util.Util;
import java.util.List;

/** Helper for creating download notifications. */
public final class DownloadNotificationHelper {

  private static final @StringRes int NULL_STRING_ID = 0;

  private final NotificationCompat.Builder notificationBuilder;

  /**
   * @param context A context.
   * @param channelId The id of the notification channel to use.
   */
  public DownloadNotificationHelper(Context context, String channelId) {
    this.notificationBuilder =
        new NotificationCompat.Builder(context.getApplicationContext(), channelId);
  }

  /**
   * @deprecated Use {@link #buildProgressNotification(Context, int, PendingIntent, String, List,
   *     int)}.
   */
  @Deprecated
  public Notification buildProgressNotification(
      Context context,
      @DrawableRes int smallIcon,
      @Nullable PendingIntent contentIntent,
      @Nullable String message,
      List<Download> downloads) {
    return buildProgressNotification(
        context, smallIcon, contentIntent, message, downloads, /* notMetRequirements= */ 0);
  }

  /**
   * Returns a progress notification for the given downloads.
   *
   * @param context A context.
   * @param smallIcon A small icon for the notification.
   * @param contentIntent An optional content intent to send when the notification is clicked.
   * @param message An optional message to display on the notification.
   * @param downloads The downloads.
   * @param notMetRequirements Any requirements for downloads that are not currently met.
   * @return The notification.
   */
  public Notification buildProgressNotification(
      Context context,
      @DrawableRes int smallIcon,
      @Nullable PendingIntent contentIntent,
      @Nullable String message,
      List<Download> downloads,
      @Requirements.RequirementFlags int notMetRequirements) {
    float totalPercentage = 0;
    int downloadTaskCount = 0;
    boolean allDownloadPercentagesUnknown = true;
    boolean haveDownloadedBytes = false;
    boolean haveDownloadingTasks = false;
    boolean haveQueuedTasks = false;
    boolean haveRemovingTasks = false;
    for (int i = 0; i < downloads.size(); i++) {
      Download download = downloads.get(i);
      switch (download.state) {
        case Download.STATE_REMOVING:
          haveRemovingTasks = true;
          break;
        case Download.STATE_QUEUED:
          haveQueuedTasks = true;
          break;
        case Download.STATE_RESTARTING:
        case Download.STATE_DOWNLOADING:
          haveDownloadingTasks = true;
          float downloadPercentage = download.getPercentDownloaded();
          if (downloadPercentage != C.PERCENTAGE_UNSET) {
            allDownloadPercentagesUnknown = false;
            totalPercentage += downloadPercentage;
          }
          haveDownloadedBytes |= download.getBytesDownloaded() > 0;
          downloadTaskCount++;
          break;
          // Terminal states aren't expected, but if we encounter them we do nothing.
        case Download.STATE_STOPPED:
        case Download.STATE_COMPLETED:
        case Download.STATE_FAILED:
        default:
          break;
      }
    }

    int titleStringId;
    boolean showProgress = true;
    if (haveDownloadingTasks) {
      titleStringId = R.string.exo_download_downloading;
    } else if (haveQueuedTasks && notMetRequirements != 0) {
      showProgress = false;
      if ((notMetRequirements & Requirements.NETWORK_UNMETERED) != 0) {
        // Note: This assumes that "unmetered" == "WiFi", since it provides a clearer message that's
        // correct in the majority of cases.
        titleStringId = R.string.exo_download_paused_for_wifi;
      } else if ((notMetRequirements & Requirements.NETWORK) != 0) {
        titleStringId = R.string.exo_download_paused_for_network;
      } else {
        titleStringId = R.string.exo_download_paused;
      }
    } else if (haveRemovingTasks) {
      titleStringId = R.string.exo_download_removing;
    } else {
      // There are either no downloads, or all downloads are in terminal states.
      titleStringId = NULL_STRING_ID;
    }

    int maxProgress = 0;
    int currentProgress = 0;
    boolean indeterminateProgress = false;
    if (showProgress) {
      maxProgress = 100;
      if (haveDownloadingTasks) {
        currentProgress = (int) (totalPercentage / downloadTaskCount);
        indeterminateProgress = allDownloadPercentagesUnknown && haveDownloadedBytes;
      } else {
        indeterminateProgress = true;
      }
    }

    return buildNotification(
        context,
        smallIcon,
        contentIntent,
        message,
        titleStringId,
        maxProgress,
        currentProgress,
        indeterminateProgress,
        /* ongoing= */ true,
        /* showWhen= */ false);
  }

  /**
   * Returns a notification for a completed download.
   *
   * @param context A context.
   * @param smallIcon A small icon for the notifications.
   * @param contentIntent An optional content intent to send when the notification is clicked.
   * @param message An optional message to display on the notification.
   * @return The notification.
   */
  public Notification buildDownloadCompletedNotification(
      Context context,
      @DrawableRes int smallIcon,
      @Nullable PendingIntent contentIntent,
      @Nullable String message) {
    int titleStringId = R.string.exo_download_completed;
    return buildEndStateNotification(context, smallIcon, contentIntent, message, titleStringId);
  }

  /**
   * Returns a notification for a failed download.
   *
   * @param context A context.
   * @param smallIcon A small icon for the notifications.
   * @param contentIntent An optional content intent to send when the notification is clicked.
   * @param message An optional message to display on the notification.
   * @return The notification.
   */
  public Notification buildDownloadFailedNotification(
      Context context,
      @DrawableRes int smallIcon,
      @Nullable PendingIntent contentIntent,
      @Nullable String message) {
    @StringRes int titleStringId = R.string.exo_download_failed;
    return buildEndStateNotification(context, smallIcon, contentIntent, message, titleStringId);
  }

  private Notification buildEndStateNotification(
      Context context,
      @DrawableRes int smallIcon,
      @Nullable PendingIntent contentIntent,
      @Nullable String message,
      @StringRes int titleStringId) {
    return buildNotification(
        context,
        smallIcon,
        contentIntent,
        message,
        titleStringId,
        /* maxProgress= */ 0,
        /* currentProgress= */ 0,
        /* indeterminateProgress= */ false,
        /* ongoing= */ false,
        /* showWhen= */ true);
  }

  private Notification buildNotification(
      Context context,
      @DrawableRes int smallIcon,
      @Nullable PendingIntent contentIntent,
      @Nullable String message,
      @StringRes int titleStringId,
      int maxProgress,
      int currentProgress,
      boolean indeterminateProgress,
      boolean ongoing,
      boolean showWhen) {
    notificationBuilder.setSmallIcon(smallIcon);
    notificationBuilder.setContentTitle(
        titleStringId == NULL_STRING_ID ? null : context.getResources().getString(titleStringId));
    notificationBuilder.setContentIntent(contentIntent);
    notificationBuilder.setStyle(
        message == null ? null : new NotificationCompat.BigTextStyle().bigText(message));
    notificationBuilder.setProgress(maxProgress, currentProgress, indeterminateProgress);
    notificationBuilder.setOngoing(ongoing);
    notificationBuilder.setShowWhen(showWhen);
    if (Util.SDK_INT >= 31) {
      Api31.setForegroundServiceBehavior(notificationBuilder);
    }
    return notificationBuilder.build();
  }

  @RequiresApi(31)
  private static final class Api31 {
    @SuppressLint("WrongConstant") // TODO(b/254277605): remove lint suppression
    @DoNotInline
    public static void setForegroundServiceBehavior(
        NotificationCompat.Builder notificationBuilder) {
      notificationBuilder.setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE);
    }
  }
}
