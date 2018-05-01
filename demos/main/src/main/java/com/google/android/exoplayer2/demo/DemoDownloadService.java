/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.demo;

import android.app.Notification;
import android.util.Pair;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadManager.TaskState;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.ProgressiveDownloadAction;
import com.google.android.exoplayer2.scheduler.PlatformScheduler;
import com.google.android.exoplayer2.source.dash.offline.DashDownloadAction;
import com.google.android.exoplayer2.source.hls.offline.HlsDownloadAction;
import com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloadAction;
import com.google.android.exoplayer2.ui.DownloadNotificationUtil;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.exoplayer2.util.Util;

/** A service for downloading media. */
public class DemoDownloadService extends DownloadService {

  private static final String CHANNEL_ID = "download_channel";
  private static final int JOB_ID = 1;
  private static final int FOREGROUND_NOTIFICATION_ID = 1;

  private static DownloadManager downloadManager;

  public DemoDownloadService() {
    super(
        FOREGROUND_NOTIFICATION_ID,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        CHANNEL_ID,
        R.string.exo_download_notification_channel_name);
  }

  @Override
  protected DownloadManager getDownloadManager() {
    if (downloadManager == null) {
      DemoApplication application = (DemoApplication) getApplication();
      DownloaderConstructorHelper constructorHelper =
          new DownloaderConstructorHelper(
              application.getDownloadCache(), application.buildHttpDataSourceFactory(null));
      downloadManager =
          new DownloadManager(
              constructorHelper,
              /* maxSimultaneousDownloads= */ 2,
              DownloadManager.DEFAULT_MIN_RETRY_COUNT,
              application.getDownloadActionFile(),
              DashDownloadAction.DESERIALIZER,
              HlsDownloadAction.DESERIALIZER,
              SsDownloadAction.DESERIALIZER,
              ProgressiveDownloadAction.DESERIALIZER);
    }
    return downloadManager;
  }

  @Override
  protected PlatformScheduler getScheduler() {
    return Util.SDK_INT >= 21 ? new PlatformScheduler(this, JOB_ID) : null;
  }

  @Override
  protected Notification getForegroundNotification(TaskState[] taskStates) {
    return DownloadNotificationUtil.createProgressNotification(
        taskStates,
        /* context= */ this,
        R.drawable.exo_controls_play,
        CHANNEL_ID,
        /* contentIntent= */ null,
        /* message= */ null);
  }

  @Override
  protected void onTaskStateChanged(TaskState taskState) {
    int notificationId = FOREGROUND_NOTIFICATION_ID + 1 + taskState.taskId;
    Notification downloadNotification =
        DownloadNotificationUtil.createDownloadFinishedNotification(
            taskState,
            /* context= */ this,
            R.drawable.exo_controls_play,
            CHANNEL_ID,
            /* contentIntent= */ null,
            taskState.action.data,
            new ErrorMessageProvider<Throwable>() {
              @Override
              public Pair<Integer, String> getErrorMessage(Throwable throwable) {
                return new Pair<>(0, throwable.getLocalizedMessage());
              }
            });
    NotificationUtil.setNotification(/* context= */ this, notificationId, downloadNotification);
  }
}
