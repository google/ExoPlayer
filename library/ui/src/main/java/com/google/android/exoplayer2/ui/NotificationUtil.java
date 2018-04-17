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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;

/** Utility methods for displaying {@link android.app.Notification}s. */
public final class NotificationUtil {

  private NotificationUtil() {}

  /**
   * Creates a notification channel that notifications can be posted to. See {@link
   * NotificationChannel} and {@link
   * NotificationManager#createNotificationChannel(NotificationChannel)} for details.
   *
   * @param context A {@link Context} to retrieve {@link NotificationManager}.
   * @param id The id of the channel. Must be unique per package. The value may be truncated if it
   *     is too long.
   * @param name The user visible name of the channel. You can rename this channel when the system
   *     locale changes by listening for the {@link Intent#ACTION_LOCALE_CHANGED} broadcast. The
   *     recommended maximum length is 40 characters; the value may be truncated if it is too long.
   * @param importance The importance of the channel. This controls how interruptive notifications
   *     posted to this channel are.
   */
  public static void createNotificationChannel(
      Context context, String id, int name, int importance) {
    if (Util.SDK_INT >= 26) {
      NotificationManager notificationManager =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationChannel mChannel =
          new NotificationChannel(id, context.getString(name), importance);
      notificationManager.createNotificationChannel(mChannel);
    }
  }

  /**
   * Post a notification to be shown in the status bar. If a notification with the same id has
   * already been posted by your application and has not yet been canceled, it will be replaced by
   * the updated information. If {@code notification} is null, then cancels a previously shown
   * notification.
   *
   * @param context A {@link Context} to retrieve {@link NotificationManager}.
   * @param id An identifier for this notification unique within your application.
   * @param notification A {@link Notification} object describing what to show the user. If null,
   *     then cancels a previously shown notification.
   */
  public static void setNotification(Context context, int id, @Nullable Notification notification) {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (notification != null) {
      notificationManager.notify(id, notification);
    } else {
      notificationManager.cancel(id);
    }
  }
}
