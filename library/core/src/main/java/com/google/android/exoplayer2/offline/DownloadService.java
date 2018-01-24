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
package com.google.android.exoplayer2.offline;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.CallSuper;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadState;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.util.scheduler.Requirements;
import com.google.android.exoplayer2.util.scheduler.RequirementsWatcher;
import com.google.android.exoplayer2.util.scheduler.Scheduler;
import java.io.IOException;

/**
 * A {@link Service} that downloads streams in the background.
 *
 * <p>To start the service, create an instance of one of the subclasses of {@link DownloadAction}
 * and call {@link #addDownloadAction(Context, Class, DownloadAction)} with it.
 */
public abstract class DownloadService extends Service implements DownloadManager.DownloadListener {

  /** Use this action to initialize {@link DownloadManager}. */
  public static final String ACTION_INIT =
      "com.google.android.exoplayer.downloadService.action.INIT";

  /** Use this action to add a {@link DownloadAction} to {@link DownloadManager} action queue. */
  public static final String ACTION_ADD = "com.google.android.exoplayer.downloadService.action.ADD";

  /** Use this action to make {@link DownloadManager} stop download tasks. */
  private static final String ACTION_STOP =
      "com.google.android.exoplayer.downloadService.action.STOP";

  /** Use this action to make {@link DownloadManager} start download tasks. */
  private static final String ACTION_START =
      "com.google.android.exoplayer.downloadService.action.START";

  /** A {@link DownloadAction} to be executed. */
  public static final String DOWNLOAD_ACTION = "DownloadAction";

  /** Default progress update interval in milliseconds. */
  public static final long DEFAULT_PROGRESS_UPDATE_INTERVAL_MILLIS = 1000;

  private static final String TAG = "DownloadService";
  private static final boolean DEBUG = false;

  // Keep requirementsWatcher and scheduler alive beyond DownloadService life span (until the app is
  // killed) because it may take long time for Scheduler to start the service.
  private static RequirementsWatcher requirementsWatcher;
  private static Scheduler scheduler;

  private final int notificationIdOffset;
  private final long progressUpdateIntervalMillis;

  private DownloadManager downloadManager;
  private ProgressUpdater progressUpdater;
  private int lastStartId;

  /** @param notificationIdOffset Value to offset notification ids. Must be greater than 0. */
  protected DownloadService(int notificationIdOffset) {
    this(notificationIdOffset, DEFAULT_PROGRESS_UPDATE_INTERVAL_MILLIS);
  }

  /**
   * @param notificationIdOffset Value to offset notification ids. Must be greater than 0.
   * @param progressUpdateIntervalMillis {@link #onProgressUpdate(DownloadState[])} is called using
   *     this interval. If it's {@link C#TIME_UNSET}, then {@link
   *     #onProgressUpdate(DownloadState[])} isn't called.
   */
  protected DownloadService(int notificationIdOffset, long progressUpdateIntervalMillis) {
    this.notificationIdOffset = notificationIdOffset;
    this.progressUpdateIntervalMillis = progressUpdateIntervalMillis;
  }

  /**
   * Creates an {@link Intent} to be used to start this service and adds the {@link DownloadAction}
   * to the {@link DownloadManager}.
   *
   * @param context A {@link Context} of the application calling this service.
   * @param clazz Class object of DownloadService or subclass.
   * @param downloadAction A {@link DownloadAction} to be executed.
   * @return Created Intent.
   */
  public static Intent createAddDownloadActionIntent(
      Context context, Class<? extends DownloadService> clazz, DownloadAction downloadAction) {
    return new Intent(context, clazz)
        .setAction(ACTION_ADD)
        .putExtra(DOWNLOAD_ACTION, downloadAction.toByteArray());
  }

  /**
   * Adds a {@link DownloadAction} to the {@link DownloadManager}. This will start the download
   * service if it was not running.
   *
   * @param context A {@link Context} of the application calling this service.
   * @param clazz Class object of DownloadService or subclass.
   * @param downloadAction A {@link DownloadAction} to be executed.
   * @see #createAddDownloadActionIntent(Context, Class, DownloadAction)
   */
  public static void addDownloadAction(
      Context context, Class<? extends DownloadService> clazz, DownloadAction downloadAction) {
    context.startService(createAddDownloadActionIntent(context, clazz, downloadAction));
  }

  @Override
  public void onCreate() {
    logd("onCreate");
    downloadManager = getDownloadManager();
    downloadManager.addListener(this);

    if (requirementsWatcher == null) {
      Requirements requirements = getRequirements();
      if (requirements != null) {
        scheduler = getScheduler();
        RequirementsListener listener =
            new RequirementsListener(getApplicationContext(), getClass(), scheduler);
        requirementsWatcher =
            new RequirementsWatcher(getApplicationContext(), listener, requirements);
        requirementsWatcher.start();
      }
    }

    progressUpdater = new ProgressUpdater(this, progressUpdateIntervalMillis);
  }

  @Override
  public void onDestroy() {
    logd("onDestroy");
    progressUpdater.stop();
    downloadManager.removeListener(this);
    if (downloadManager.getTaskCount() == 0) {
      if (requirementsWatcher != null) {
        requirementsWatcher.stop();
        requirementsWatcher = null;
      }
      if (scheduler != null) {
        scheduler.cancel();
        scheduler = null;
      }
    }
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    this.lastStartId = startId;
    String intentAction = intent != null ? intent.getAction() : null;
    if (intentAction == null) {
      intentAction = ACTION_INIT;
    }
    logd("onStartCommand action: " + intentAction + " startId: " + startId);
    switch (intentAction) {
      case ACTION_INIT:
        // Do nothing. DownloadManager and RequirementsWatcher is initialized. If there are download
        // or remove tasks loaded from file, they will start if the requirements are met.
        break;
      case ACTION_ADD:
        byte[] actionData = intent.getByteArrayExtra(DOWNLOAD_ACTION);
        if (actionData == null) {
          onCommandError(intent, new IllegalArgumentException("DownloadAction is missing."));
        } else {
          try {
            onNewTask(intent, downloadManager.handleAction(actionData));
          } catch (IOException e) {
            onCommandError(intent, e);
          }
        }
        break;
      case ACTION_STOP:
        downloadManager.stopDownloads();
        break;
      case ACTION_START:
        downloadManager.startDownloads();
        break;
      default:
        onCommandError(intent, new IllegalArgumentException("Unknown action: " + intentAction));
        break;
    }
    if (downloadManager.isIdle()) {
      onIdle(null);
    }
    return START_STICKY;
  }

  /**
   * Returns a {@link DownloadManager} to be used to downloaded content. Called only once in the
   * life cycle of the service.
   */
  protected abstract DownloadManager getDownloadManager();

  /**
   * Returns a {@link Scheduler} which contains a job to initialize {@link DownloadService} when the
   * requirements are met, or null. If not null, scheduler is used to start downloads even when the
   * app isn't running.
   */
  protected abstract @Nullable Scheduler getScheduler();

  /** Returns requirements for downloads to take place, or null. */
  protected abstract @Nullable Requirements getRequirements();

  /** Called on error in start command. */
  protected void onCommandError(Intent intent, Exception error) {
    // Do nothing.
  }

  /** Called when a new task is added to the {@link DownloadManager}. */
  protected void onNewTask(Intent intent, int taskId) {
    // Do nothing.
  }

  /** Returns a notification channelId. See {@link NotificationChannel}. */
  protected abstract String getNotificationChannelId();

  /**
   * Helper method which calls {@link #startForeground(int, Notification)} with {@code
   * notificationIdOffset} and {@code foregroundNotification}.
   */
  public void startForeground(Notification foregroundNotification) {
    // logd("start foreground");
    startForeground(notificationIdOffset, foregroundNotification);
  }

  /**
   * Sets/replaces or cancels the notification for the given id.
   *
   * @param id A unique id for the notification. This value is offset by {@code
   *     notificationIdOffset}.
   * @param notification If not null, it's showed, replacing any previous notification. Otherwise
   *     any previous notification is canceled.
   */
  public void setNotification(int id, @Nullable Notification notification) {
    NotificationManager notificationManager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    if (notification != null) {
      notificationManager.notify(notificationIdOffset + 1 + id, notification);
    } else {
      notificationManager.cancel(notificationIdOffset + 1 + id);
    }
  }

  /**
   * Override this method to get notified.
   *
   * <p>{@inheritDoc}
   */
  @CallSuper
  @Override
  public void onStateChange(DownloadManager downloadManager, DownloadState downloadState) {
    if (downloadState.state == DownloadState.STATE_STARTED) {
      progressUpdater.start();
    }
  }

  /**
   * Override this method to get notified.
   *
   * <p>{@inheritDoc}
   */
  @CallSuper
  @Override
  public void onIdle(DownloadManager downloadManager) {
    // Make sure startForeground is called before stopping.
    if (Util.SDK_INT >= 26) {
      Builder notificationBuilder = new Builder(this, getNotificationChannelId());
      Notification foregroundNotification = notificationBuilder.build();
      startForeground(foregroundNotification);
    }
    boolean stopSelfResult = stopSelfResult(lastStartId);
    logd("stopSelf(" + lastStartId + ") result: " + stopSelfResult);
  }

  /** Override this method to get notified on every second while there are active downloads. */
  protected void onProgressUpdate(DownloadState[] activeDownloadTasks) {
    // Do nothing.
  }

  private void logd(String message) {
    if (DEBUG) {
      Log.d(TAG, message);
    }
  }

  private static final class ProgressUpdater implements Runnable {

    private final DownloadService downloadService;
    private final long progressUpdateIntervalMillis;
    private final Handler handler;
    private boolean stopped;

    public ProgressUpdater(DownloadService downloadService, long progressUpdateIntervalMillis) {
      this.downloadService = downloadService;
      this.progressUpdateIntervalMillis = progressUpdateIntervalMillis;
      this.handler = new Handler(Looper.getMainLooper());
      stopped = true;
    }

    @Override
    public void run() {
      DownloadState[] activeDownloadTasks =
          downloadService.downloadManager.getActiveDownloadStates();
      if (activeDownloadTasks.length > 0) {
        downloadService.onProgressUpdate(activeDownloadTasks);
        if (progressUpdateIntervalMillis != C.TIME_UNSET) {
          handler.postDelayed(this, progressUpdateIntervalMillis);
        }
      } else {
        stop();
      }
    }

    public void stop() {
      stopped = true;
      handler.removeCallbacks(this);
    }

    public void start() {
      if (stopped) {
        stopped = false;
        if (progressUpdateIntervalMillis != C.TIME_UNSET) {
          handler.post(this);
        }
      }
    }

  }

  private static final class RequirementsListener implements RequirementsWatcher.Listener {

    private final Context context;
    private final Class<? extends DownloadService> serviceClass;
    private final Scheduler scheduler;

    private RequirementsListener(
        Context context, Class<? extends DownloadService> serviceClass, Scheduler scheduler) {
      this.context = context;
      this.serviceClass = serviceClass;
      this.scheduler = scheduler;
    }

    @Override
    public void requirementsMet(RequirementsWatcher requirementsWatcher) {
      startServiceWithAction(DownloadService.ACTION_START);
      if (scheduler != null) {
        scheduler.cancel();
      }
    }

    @Override
    public void requirementsNotMet(RequirementsWatcher requirementsWatcher) {
      startServiceWithAction(DownloadService.ACTION_STOP);
      if (scheduler != null) {
        if (!scheduler.schedule()) {
          Log.e(TAG, "Scheduling downloads failed.");
        }
      }
    }

    private void startServiceWithAction(String action) {
      Intent intent = new Intent(context, serviceClass).setAction(action);
      if (Util.SDK_INT >= 26) {
        context.startForegroundService(intent);
      } else {
        context.startService(intent);
      }
    }
  }
}
