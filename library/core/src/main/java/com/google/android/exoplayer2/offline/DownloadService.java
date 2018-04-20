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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadState;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.RequirementsWatcher;
import com.google.android.exoplayer2.scheduler.Scheduler;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * A {@link Service} that downloads streams in the background.
 *
 * <p>To start the service, create an instance of one of the subclasses of {@link DownloadAction}
 * and call {@link #addDownloadAction(Context, Class, DownloadAction)} with it.
 */
public abstract class DownloadService extends Service {

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

  /** Default foreground notification update interval in milliseconds. */
  public static final long DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL = 1000;

  private static final String TAG = "DownloadService";
  private static final boolean DEBUG = false;

  // Keep requirementsWatcher and scheduler alive beyond DownloadService life span (until the app is
  // killed) because it may take long time for Scheduler to start the service.
  private static RequirementsWatcher requirementsWatcher;
  private static Scheduler scheduler;

  private final ForegroundNotificationUpdater foregroundNotificationUpdater;

  private DownloadManager downloadManager;
  private DownloadListener downloadListener;
  private int lastStartId;

  /**
   * Creates a DownloadService with {@link #DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL}.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, must not
   *     be 0.
   */
  protected DownloadService(int foregroundNotificationId) {
    this(foregroundNotificationId, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL);
  }

  /**
   * Creates a DownloadService.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, must not
   *     be 0.
   * @param foregroundNotificationUpdateInterval The maximum interval to update foreground
   *     notification, in milliseconds.
   */
  protected DownloadService(
      int foregroundNotificationId, long foregroundNotificationUpdateInterval) {
    foregroundNotificationUpdater =
        new ForegroundNotificationUpdater(
            foregroundNotificationId, foregroundNotificationUpdateInterval);
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
    downloadListener = new DownloadListener();
    downloadManager.addListener(downloadListener);

    if (requirementsWatcher == null) {
      Requirements requirements = getRequirements();
      if (requirements != null) {
        scheduler = getScheduler();
        RequirementsListener listener =
            new RequirementsListener(getApplicationContext(), getClass(), scheduler);
        requirementsWatcher =
            new RequirementsWatcher(getApplicationContext(), listener, requirements);
        requirementsWatcher.start();
      } else {
        downloadManager.startDownloads();
      }
    }
  }

  @Override
  public void onDestroy() {
    logd("onDestroy");
    foregroundNotificationUpdater.stopPeriodicUpdates();
    downloadManager.removeListener(downloadListener);
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
          onCommandError(new IllegalArgumentException("DownloadAction is missing."));
        } else {
          try {
            downloadManager.handleAction(actionData);
          } catch (IOException e) {
            onCommandError(e);
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
        onCommandError(new IllegalArgumentException("Unknown action: " + intentAction));
        break;
    }
    if (downloadManager.isIdle()) {
      stop();
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

  /**
   * Returns a notification to be displayed when this service running in the foreground.
   *
   * <p>This method is called when there is a download task state change and periodically while
   * there is an active download. Update interval can be set using {@link #DownloadService(int,
   * long)}.
   *
   * <p>On API level 26 and above, it may be also called just before the service stops with an empty
   * {@code downloadStates} array, returned notification is used to satisfy system requirements for
   * foreground services.
   *
   * @param downloadStates DownloadState for all tasks.
   * @return A notification to be displayed when this service running in the foreground.
   */
  protected abstract Notification getForegroundNotification(DownloadState[] downloadStates);

  /** Called when the download state changes. */
  protected void onStateChange(DownloadState downloadState) {
    // Do nothing.
  }

  private void onCommandError(Exception error) {
    Log.e(TAG, "Command error", error);
  }

  private void stop() {
    foregroundNotificationUpdater.stopPeriodicUpdates();
    // Make sure startForeground is called before stopping.
    // Workaround for [Internal: b/69424260]
    if (Util.SDK_INT >= 26) {
      foregroundNotificationUpdater.showNotificationIfNotAlready();
    }
    boolean stopSelfResult = stopSelfResult(lastStartId);
    logd("stopSelf(" + lastStartId + ") result: " + stopSelfResult);
  }

  private void logd(String message) {
    if (DEBUG) {
      Log.d(TAG, message);
    }
  }

  private final class DownloadListener implements DownloadManager.DownloadListener {
    @Override
    public void onStateChange(DownloadManager downloadManager, DownloadState downloadState) {
      DownloadService.this.onStateChange(downloadState);
      if (downloadState.state == DownloadState.STATE_STARTED) {
        foregroundNotificationUpdater.startPeriodicUpdates();
      } else {
        foregroundNotificationUpdater.update();
      }
    }

    @Override
    public final void onIdle(DownloadManager downloadManager) {
      stop();
    }
  }

  private final class ForegroundNotificationUpdater implements Runnable {

    private final int notificationId;
    private final long updateInterval;
    private final Handler handler;

    private boolean periodicUpdatesStarted;
    private boolean notificationDisplayed;

    public ForegroundNotificationUpdater(int notificationId, long updateInterval) {
      this.notificationId = notificationId;
      this.updateInterval = updateInterval;
      this.handler = new Handler(Looper.getMainLooper());
    }

    public void startPeriodicUpdates() {
      periodicUpdatesStarted = true;
      update();
    }

    public void stopPeriodicUpdates() {
      periodicUpdatesStarted = false;
      handler.removeCallbacks(this);
    }

    public void update() {
      DownloadState[] downloadStates = downloadManager.getDownloadStates();
      startForeground(notificationId, getForegroundNotification(downloadStates));
      notificationDisplayed = true;
      if (periodicUpdatesStarted) {
        handler.removeCallbacks(this);
        handler.postDelayed(this, updateInterval);
      }
    }

    public void showNotificationIfNotAlready() {
      if (!notificationDisplayed) {
        update();
      }
    }

    @Override
    public void run() {
      update();
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
