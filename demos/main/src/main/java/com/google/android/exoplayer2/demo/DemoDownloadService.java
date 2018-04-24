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
import com.google.android.exoplayer2.offline.DownloadManager.DownloadState;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.ProgressiveDownloadAction;
import com.google.android.exoplayer2.scheduler.PlatformScheduler;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.source.dash.offline.DashDownloadAction;
import com.google.android.exoplayer2.source.hls.offline.HlsDownloadAction;
import com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloadAction;
import com.google.android.exoplayer2.ui.DownloadNotificationUtil;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.NotificationUtil;

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
              /*maxSimultaneousDownloads=*/ 2,
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
    return new PlatformScheduler(
        getApplicationContext(), getRequirements(), JOB_ID, ACTION_INIT, getPackageName());
  }

  @Override
  protected Requirements getRequirements() {
    return new Requirements(Requirements.NETWORK_TYPE_UNMETERED, false, false);
  }

  @Override
  protected Notification getForegroundNotification(DownloadState[] downloadStates) {
    return DownloadNotificationUtil.createProgressNotification(
        downloadStates, this, R.drawable.exo_controls_play, CHANNEL_ID, null);
  }

  @Override
  protected void onStateChange(DownloadState downloadState) {
    int notificationId = FOREGROUND_NOTIFICATION_ID + 1 + downloadState.taskId;
    Notification downloadNotification =
        DownloadNotificationUtil.createDownloadFinishedNotification(
            downloadState,
            this,
            R.drawable.exo_controls_play,
            CHANNEL_ID,
            downloadState.downloadAction.data,
            new ErrorMessageProvider<Throwable>() {
              @Override
              public Pair<Integer, String> getErrorMessage(Throwable throwable) {
                return new Pair<>(0, throwable.getLocalizedMessage());
              }
            });
    NotificationUtil.setNotification(this, notificationId, downloadNotification);
  }
}
