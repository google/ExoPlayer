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
import android.support.annotation.StringRes;
import android.util.Log;
import com.google.android.exoplayer2.offline.DownloadManager.TaskState;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.RequirementsWatcher;
import com.google.android.exoplayer2.scheduler.Scheduler;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.HashMap;

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

  /** Key for the {@link DownloadAction} in an {@link #ACTION_ADD} intent. */
  public static final String KEY_DOWNLOAD_ACTION = "download_action";

  /** Default foreground notification update interval in milliseconds. */
  public static final long DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL = 1000;

  private static final String TAG = "DownloadService";
  private static final boolean DEBUG = false;

  // Keep the requirements helper for each DownloadService as long as there are tasks (and the
  // process is running). This allows tasks to resume when there's no scheduler. It may also allow
  // tasks the resume more quickly than when relying on the scheduler alone.
  private static final HashMap<Class<? extends DownloadService>, RequirementsHelper>
      requirementsHelpers = new HashMap<>();

  private final ForegroundNotificationUpdater foregroundNotificationUpdater;
  private final @Nullable String channelId;
  private final @StringRes int channelName;

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
    this(
        foregroundNotificationId,
        foregroundNotificationUpdateInterval,
        /* channelId= */ null,
        /* channelName= */ 0);
  }

  /**
   * Creates a DownloadService.
   *
   * @param foregroundNotificationId The notification id for the foreground notification. Must not
   *     be 0.
   * @param foregroundNotificationUpdateInterval The maximum interval between updates to the
   *     foreground notification, in milliseconds.
   * @param channelId An id for a low priority notification channel to create, or {@code null} if
   *     the app will take care of creating a notification channel if needed. If specified, must be
   *     unique per package and the value may be truncated if it is too long.
   * @param channelName A string resource identifier for the user visible name of the channel, if
   *     {@code channelId} is specified. The recommended maximum length is 40 characters; the value
   *     may be truncated if it is too long.
   */
  protected DownloadService(
      int foregroundNotificationId,
      long foregroundNotificationUpdateInterval,
      @Nullable String channelId,
      @StringRes int channelName) {
    foregroundNotificationUpdater =
        new ForegroundNotificationUpdater(
            foregroundNotificationId, foregroundNotificationUpdateInterval);
    this.channelId = channelId;
    this.channelName = channelName;
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
        .putExtra(KEY_DOWNLOAD_ACTION, downloadAction.toByteArray());
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
    if (channelId != null) {
      NotificationUtil.createNotificationChannel(
          this, channelId, channelName, NotificationUtil.IMPORTANCE_LOW);
    }
    downloadManager = getDownloadManager();
    downloadListener = new DownloadListener();
    downloadManager.addListener(downloadListener);

    RequirementsHelper requirementsHelper;
    synchronized (requirementsHelpers) {
      Class<? extends DownloadService> clazz = getClass();
      requirementsHelper = requirementsHelpers.get(clazz);
      if (requirementsHelper == null) {
        requirementsHelper = new RequirementsHelper(this, getRequirements(), getScheduler(), clazz);
        requirementsHelpers.put(clazz, requirementsHelper);
      }
    }
    requirementsHelper.start();
  }

  @Override
  public void onDestroy() {
    logd("onDestroy");
    foregroundNotificationUpdater.stopPeriodicUpdates();
    downloadManager.removeListener(downloadListener);
    if (downloadManager.getTaskCount() == 0) {
      synchronized (requirementsHelpers) {
        RequirementsHelper requirementsHelper = requirementsHelpers.remove(getClass());
        if (requirementsHelper != null) {
          requirementsHelper.stop();
        }
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
        byte[] actionData = intent.getByteArrayExtra(KEY_DOWNLOAD_ACTION);
        if (actionData == null) {
          Log.e(TAG, "Ignoring ADD action with no action data");
        } else {
          try {
            downloadManager.handleAction(actionData);
          } catch (IOException e) {
            Log.e(TAG, "Failed to handle ADD action", e);
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
        Log.e(TAG, "Ignoring unrecognized action: " + intentAction);
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
   * Returns a {@link Scheduler} to restart the service when requirements allowing downloads to take
   * place are met. If {@code null}, the service will only be restarted if the process is still in
   * memory when the requirements are met.
   */
  protected abstract @Nullable Scheduler getScheduler();

  /**
   * Returns requirements for downloads to take place. By default the only requirement is that the
   * device has network connectivity.
   */
  protected Requirements getRequirements() {
    return new Requirements(Requirements.NETWORK_TYPE_ANY, false, false);
  }

  /**
   * Returns a notification to be displayed when this service running in the foreground.
   *
   * <p>This method is called when there is a task state change and periodically while there are
   * active tasks. The periodic update interval can be set using {@link #DownloadService(int,
   * long)}.
   *
   * <p>On API level 26 and above, this method may also be called just before the service stops,
   * with an empty {@code taskStates} array. The returned notification is used to satisfy system
   * requirements for foreground services.
   *
   * @param taskStates The states of all current tasks.
   * @return The foreground notification to display.
   */
  protected abstract Notification getForegroundNotification(TaskState[] taskStates);

  /**
   * Called when the state of a task changes.
   *
   * @param taskState The state of the task.
   */
  protected void onTaskStateChanged(TaskState taskState) {
    // Do nothing.
  }

  private void stop() {
    foregroundNotificationUpdater.stopPeriodicUpdates();
    // Make sure startForeground is called before stopping. Workaround for [Internal: b/69424260].
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
    public void onTaskStateChanged(DownloadManager downloadManager, TaskState taskState) {
      DownloadService.this.onTaskStateChanged(taskState);
      if (taskState.state == TaskState.STATE_STARTED) {
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
      TaskState[] taskStates = downloadManager.getAllTaskStates();
      startForeground(notificationId, getForegroundNotification(taskStates));
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

  private static final class RequirementsHelper implements RequirementsWatcher.Listener {

    private final Context context;
    private final Requirements requirements;
    private final @Nullable Scheduler scheduler;
    private final Class<? extends DownloadService> serviceClass;
    private final RequirementsWatcher requirementsWatcher;

    private RequirementsHelper(
        Context context,
        Requirements requirements,
        @Nullable Scheduler scheduler,
        Class<? extends DownloadService> serviceClass) {
      this.context = context;
      this.requirements = requirements;
      this.scheduler = scheduler;
      this.serviceClass = serviceClass;
      requirementsWatcher = new RequirementsWatcher(context, this, requirements);
    }

    public void start() {
      requirementsWatcher.start();
    }

    public void stop() {
      requirementsWatcher.stop();
      if (scheduler != null) {
        scheduler.cancel();
      }
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
        boolean success = scheduler.schedule(requirements, context.getPackageName(), ACTION_INIT);
        if (!success) {
          Log.e(TAG, "Scheduling downloads failed.");
        }
      }
    }

    private void startServiceWithAction(String action) {
      Intent intent = new Intent(context, serviceClass).setAction(action);
      Util.startForegroundService(context, intent);
    }
  }
}
