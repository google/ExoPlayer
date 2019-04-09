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
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.Scheduler;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.exoplayer2.util.Util;
import java.util.HashMap;

/** A {@link Service} for downloading media. */
public abstract class DownloadService extends Service {

  /**
   * Starts a download service without adding a new {@link DownloadAction}. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_INIT =
      "com.google.android.exoplayer.downloadService.action.INIT";

  /** Like {@link #ACTION_INIT}, but with {@link #KEY_FOREGROUND} implicitly set to true. */
  private static final String ACTION_RESTART =
      "com.google.android.exoplayer.downloadService.action.RESTART";

  /**
   * Adds a new download. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_DOWNLOAD_ACTION} - A {@link DownloadAction} defining the download to be
   *       added.
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_ADD = "com.google.android.exoplayer.downloadService.action.ADD";

  /**
   * Starts one or all of the current downloads. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_CONTENT_ID} - The content id of a single download to start. If omitted, all
   *       of the current downloads will be started.
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_START =
      "com.google.android.exoplayer.downloadService.action.START";

  /**
   * Stops one or all of the current downloads. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_CONTENT_ID} - The content id of a single download to stop. If omitted, all of
   *       the current downloads will be stopped.
   *   <li>{@link #KEY_STOP_REASON} - An application provided reason for stopping the download or
   *       downloads. Must not be {@link DownloadState#MANUAL_STOP_REASON_NONE}. If omitted, the
   *       stop reason will be {@link DownloadState#MANUAL_STOP_REASON_UNDEFINED}.
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_STOP =
      "com.google.android.exoplayer.downloadService.action.STOP";

  /**
   * Removes an existing download. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_CONTENT_ID} - The content id of a download to remove.
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_REMOVE =
      "com.google.android.exoplayer.downloadService.action.REMOVE";

  /**
   * Key for the {@code byte[]} representation of the {@link DownloadAction} in {@link #ACTION_ADD}
   * intents.
   */
  public static final String KEY_DOWNLOAD_ACTION = "download_action";

  /**
   * Key for the content id in {@link #ACTION_START}, {@link #ACTION_STOP} and {@link
   * #ACTION_REMOVE} intents.
   */
  public static final String KEY_CONTENT_ID = "content_id";

  /** Key for the stop reason in {@link #ACTION_STOP} intents. */
  public static final String KEY_STOP_REASON = "stop_reason";

  /**
   * Key for a boolean extra that can be set on any intent to indicate whether the service was
   * started in the foreground. If set, the service is guaranteed to call {@link
   * #startForeground(int, Notification)}.
   */
  public static final String KEY_FOREGROUND = "foreground";

  /** Invalid foreground notification id that can be used to run the service in the background. */
  public static final int FOREGROUND_NOTIFICATION_ID_NONE = 0;

  /** Default foreground notification update interval in milliseconds. */
  public static final long DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL = 1000;

  private static final String TAG = "DownloadService";
  private static final boolean DEBUG = false;

  // Keep DownloadManagerListeners for each DownloadService as long as there are downloads (and the
  // process is running). This allows DownloadService to restart when there's no scheduler.
  private static final HashMap<Class<? extends DownloadService>, DownloadManagerHelper>
      downloadManagerListeners = new HashMap<>();

  @Nullable private final ForegroundNotificationUpdater foregroundNotificationUpdater;
  @Nullable private final String channelId;
  @StringRes private final int channelNameResourceId;

  private DownloadManager downloadManager;
  private int lastStartId;
  private boolean startedInForeground;
  private boolean taskRemoved;

  /**
   * Creates a DownloadService.
   *
   * <p>If {@code foregroundNotificationId} is {@link #FOREGROUND_NOTIFICATION_ID_NONE} then the
   * service will only ever run in the background. No foreground notification will be displayed and
   * {@link #getScheduler()} will not be called.
   *
   * <p>If {@code foregroundNotificationId} is not {@link #FOREGROUND_NOTIFICATION_ID_NONE} then the
   * service will run in the foreground. The foreground notification will be updated at least as
   * often as the interval specified by {@link #DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL}.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, or {@link
   *     #FOREGROUND_NOTIFICATION_ID_NONE} if the service should only ever run in the background.
   */
  protected DownloadService(int foregroundNotificationId) {
    this(foregroundNotificationId, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL);
  }

  /**
   * Creates a DownloadService.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, or {@link
   *     #FOREGROUND_NOTIFICATION_ID_NONE} if the service should only ever run in the background.
   * @param foregroundNotificationUpdateInterval The maximum interval between updates to the
   *     foreground notification, in milliseconds. Ignored if {@code foregroundNotificationId} is
   *     {@link #FOREGROUND_NOTIFICATION_ID_NONE}.
   */
  protected DownloadService(
      int foregroundNotificationId, long foregroundNotificationUpdateInterval) {
    this(
        foregroundNotificationId,
        foregroundNotificationUpdateInterval,
        /* channelId= */ null,
        /* channelNameResourceId= */ 0);
  }

  /**
   * Creates a DownloadService.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, or {@link
   *     #FOREGROUND_NOTIFICATION_ID_NONE} if the service should only ever run in the background.
   * @param foregroundNotificationUpdateInterval The maximum interval between updates to the
   *     foreground notification, in milliseconds. Ignored if {@code foregroundNotificationId} is
   *     {@link #FOREGROUND_NOTIFICATION_ID_NONE}.
   * @param channelId An id for a low priority notification channel to create, or {@code null} if
   *     the app will take care of creating a notification channel if needed. If specified, must be
   *     unique per package. The value may be truncated if it's too long. Ignored if {@code
   *     foregroundNotificationId} is {@link #FOREGROUND_NOTIFICATION_ID_NONE}.
   * @param channelNameResourceId A string resource identifier for the user visible name of the
   *     channel, if {@code channelId} is specified. The recommended maximum length is 40
   *     characters. The value may be truncated if it is too long. Ignored if {@code
   *     foregroundNotificationId} is {@link #FOREGROUND_NOTIFICATION_ID_NONE}.
   */
  protected DownloadService(
      int foregroundNotificationId,
      long foregroundNotificationUpdateInterval,
      @Nullable String channelId,
      @StringRes int channelNameResourceId) {
    if (foregroundNotificationId == FOREGROUND_NOTIFICATION_ID_NONE) {
      this.foregroundNotificationUpdater = null;
      this.channelId = null;
      this.channelNameResourceId = 0;
    } else {
      this.foregroundNotificationUpdater =
          new ForegroundNotificationUpdater(
              foregroundNotificationId, foregroundNotificationUpdateInterval);
      this.channelId = channelId;
      this.channelNameResourceId = channelNameResourceId;
    }
  }

  /**
   * Builds an {@link Intent} for adding an action to be executed by the service.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param downloadAction The action to be executed.
   * @param foreground Whether this intent will be used to start the service in the foreground.
   * @return Created Intent.
   */
  public static Intent buildAddActionIntent(
      Context context,
      Class<? extends DownloadService> clazz,
      DownloadAction downloadAction,
      boolean foreground) {
    return getIntent(context, clazz, ACTION_ADD)
        .putExtra(KEY_DOWNLOAD_ACTION, downloadAction)
        .putExtra(KEY_FOREGROUND, foreground);
  }

  /**
   * Builds an {@link Intent} for stopping the download with the {@code id}. If {@code id} is null,
   * stops all downloads.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param id The content id, or null if all downloads should be stopped.
   * @param manualStopReason An application defined stop reason.
   * @return Created Intent.
   */
  public static Intent buildStopDownloadIntent(
      Context context,
      Class<? extends DownloadService> clazz,
      @Nullable String id,
      int manualStopReason) {
    return getIntent(context, clazz, ACTION_STOP)
        .putExtra(KEY_CONTENT_ID, id)
        .putExtra(KEY_STOP_REASON, manualStopReason);
  }

  /**
   * Builds an {@link Intent} for starting the download with the {@code id}. If {@code id} is null,
   * starts all downloads.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param id The content id, or null if all downloads should be started.
   * @return Created Intent.
   */
  public static Intent buildStartDownloadIntent(
      Context context, Class<? extends DownloadService> clazz, @Nullable String id) {
    return getIntent(context, clazz, ACTION_START).putExtra(KEY_CONTENT_ID, id);
  }

  /**
   * Builds an {@link Intent} for removing the download with the {@code id}.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param id The content id.
   * @param foreground Whether this intent will be used to start the service in the foreground.
   * @return Created Intent.
   */
  public static Intent buildRemoveDownloadIntent(
      Context context, Class<? extends DownloadService> clazz, String id, boolean foreground) {
    return getIntent(context, clazz, ACTION_REMOVE)
        .putExtra(KEY_CONTENT_ID, id)
        .putExtra(KEY_FOREGROUND, foreground);
  }

  /**
   * Starts the service, adding an action to be executed.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @param downloadAction The action to be executed.
   * @param foreground Whether the service is started in the foreground.
   */
  public static void startWithAction(
      Context context,
      Class<? extends DownloadService> clazz,
      DownloadAction downloadAction,
      boolean foreground) {
    Intent intent = buildAddActionIntent(context, clazz, downloadAction, foreground);
    if (foreground) {
      Util.startForegroundService(context, intent);
    } else {
      context.startService(intent);
    }
  }

  /**
   * Starts the service to remove a download.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @param id The content id.
   * @param foreground Whether the service is started in the foreground.
   */
  public static void startWithRemoveDownload(
      Context context, Class<? extends DownloadService> clazz, String id, boolean foreground) {
    Intent intent = buildRemoveDownloadIntent(context, clazz, id, foreground);
    if (foreground) {
      Util.startForegroundService(context, intent);
    } else {
      context.startService(intent);
    }
  }

  /**
   * Starts the service without adding a new action. If there are any not finished actions and the
   * requirements are met, the service resumes executing actions. Otherwise it stops immediately.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @see #startForeground(Context, Class)
   */
  public static void start(Context context, Class<? extends DownloadService> clazz) {
    context.startService(getIntent(context, clazz, ACTION_INIT));
  }

  /**
   * Starts the service in the foreground without adding a new action. If there are any not finished
   * actions and the requirements are met, the service resumes executing actions. Otherwise it stops
   * immediately.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @see #start(Context, Class)
   */
  public static void startForeground(Context context, Class<? extends DownloadService> clazz) {
    Intent intent = getIntent(context, clazz, ACTION_INIT).putExtra(KEY_FOREGROUND, true);
    Util.startForegroundService(context, intent);
  }

  @Override
  public void onCreate() {
    logd("onCreate");
    if (channelId != null) {
      NotificationUtil.createNotificationChannel(
          this, channelId, channelNameResourceId, NotificationUtil.IMPORTANCE_LOW);
    }
    Class<? extends DownloadService> clazz = getClass();
    DownloadManagerHelper downloadManagerHelper = downloadManagerListeners.get(clazz);
    if (downloadManagerHelper == null) {
      downloadManagerHelper =
          new DownloadManagerHelper(
              getApplicationContext(), getDownloadManager(), getScheduler(), clazz);
      downloadManagerListeners.put(clazz, downloadManagerHelper);
    }
    downloadManager = downloadManagerHelper.downloadManager;
    downloadManagerHelper.attachService(this);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    lastStartId = startId;
    taskRemoved = false;
    String intentAction = null;
    if (intent != null) {
      intentAction = intent.getAction();
      startedInForeground |=
          intent.getBooleanExtra(KEY_FOREGROUND, false) || ACTION_RESTART.equals(intentAction);
    }
    // intentAction is null if the service is restarted or no action is specified.
    if (intentAction == null) {
      intentAction = ACTION_INIT;
    }
    logd("onStartCommand action: " + intentAction + " startId: " + startId);
    switch (intentAction) {
      case ACTION_INIT:
      case ACTION_RESTART:
        // Do nothing.
        break;
      case ACTION_ADD:
        DownloadAction downloadAction = intent.getParcelableExtra(KEY_DOWNLOAD_ACTION);
        if (downloadAction == null) {
          Log.e(TAG, "Ignored ADD action: Missing download_action extra");
        } else {
          downloadManager.addDownload(downloadAction);
        }
        break;
      case ACTION_START:
        String contentId = intent.getStringExtra(KEY_CONTENT_ID);
        if (contentId == null) {
          downloadManager.startDownloads();
        } else {
          downloadManager.startDownload(contentId);
        }
        break;
      case ACTION_STOP:
        contentId = intent.getStringExtra(KEY_CONTENT_ID);
        int stopReason =
            intent.getIntExtra(KEY_STOP_REASON, DownloadState.MANUAL_STOP_REASON_UNDEFINED);
        if (contentId == null) {
          downloadManager.stopDownloads(stopReason);
        } else {
          downloadManager.stopDownload(contentId, stopReason);
        }
        break;
      case ACTION_REMOVE:
        contentId = intent.getStringExtra(KEY_CONTENT_ID);
        if (contentId == null) {
          Log.e(TAG, "Ignored REMOVE action: Missing content_id extra");
        } else {
          downloadManager.removeDownload(contentId);
        }
        break;
      default:
        Log.e(TAG, "Ignored unrecognized action: " + intentAction);
        break;
    }

    if (downloadManager.isIdle()) {
      stop();
    }
    return START_STICKY;
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    logd("onTaskRemoved rootIntent: " + rootIntent);
    taskRemoved = true;
  }

  @Override
  public void onDestroy() {
    logd("onDestroy");
    DownloadManagerHelper downloadManagerHelper = downloadManagerListeners.get(getClass());
    boolean unschedule = downloadManager.getDownloadCount() <= 0;
    downloadManagerHelper.detachService(this, unschedule);
    if (foregroundNotificationUpdater != null) {
      foregroundNotificationUpdater.stopPeriodicUpdates();
    }
  }

  /**
   * Throws {@link UnsupportedOperationException} because this service is not designed to be bound.
   */
  @Nullable
  @Override
  public final IBinder onBind(Intent intent) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns a {@link DownloadManager} to be used to downloaded content. Called only once in the
   * life cycle of the process.
   */
  protected abstract DownloadManager getDownloadManager();

  /**
   * Returns a {@link Scheduler} to restart the service when requirements allowing downloads to take
   * place are met. If {@code null}, the service will only be restarted if the process is still in
   * memory when the requirements are met.
   */
  protected abstract @Nullable Scheduler getScheduler();

  /**
   * Returns a notification to be displayed when this service running in the foreground. This method
   * is called when there is a download state change and periodically while there are active
   * downloads. The periodic update interval can be set using {@link #DownloadService(int, long)}.
   *
   * <p>On API level 26 and above, this method may also be called just before the service stops,
   * with an empty {@code downloadStates} array. The returned notification is used to satisfy system
   * requirements for foreground services.
   *
   * <p>Download services that do not wish to run in the foreground should be created by setting the
   * {@code foregroundNotificationId} constructor argument to {@link
   * #FOREGROUND_NOTIFICATION_ID_NONE}. This method will not be called in this case, meaning it can
   * be implemented to throw {@link UnsupportedOperationException}.
   *
   * @param downloadStates The states of all current downloads.
   * @return The foreground notification to display.
   */
  protected abstract Notification getForegroundNotification(DownloadState[] downloadStates);

  /**
   * Called when the state of a download changes. The default implementation is a no-op.
   *
   * @param downloadState The new state of the download.
   */
  protected void onDownloadStateChanged(DownloadState downloadState) {
    // Do nothing.
  }

  /**
   * Called when a download is removed. The default implementation is a no-op.
   *
   * @param downloadState The last state of the download before it was removed.
   */
  protected void onDownloadRemoved(DownloadState downloadState) {
    // Do nothing.
  }

  private void notifyDownloadStateChange(DownloadState downloadState) {
    onDownloadStateChanged(downloadState);
    if (foregroundNotificationUpdater != null) {
      if (downloadState.state == DownloadState.STATE_DOWNLOADING
          || downloadState.state == DownloadState.STATE_REMOVING
          || downloadState.state == DownloadState.STATE_RESTARTING) {
        foregroundNotificationUpdater.startPeriodicUpdates();
      } else {
        foregroundNotificationUpdater.update();
      }
    }
  }

  private void notifyDownloadRemoved(DownloadState downloadState) {
    onDownloadRemoved(downloadState);
    if (foregroundNotificationUpdater != null) {
      foregroundNotificationUpdater.update();
    }
  }

  private void stop() {
    if (foregroundNotificationUpdater != null) {
      foregroundNotificationUpdater.stopPeriodicUpdates();
      // Make sure startForeground is called before stopping. Workaround for [Internal: b/69424260].
      if (startedInForeground && Util.SDK_INT >= 26) {
        foregroundNotificationUpdater.showNotificationIfNotAlready();
      }
    }
    if (Util.SDK_INT < 28 && taskRemoved) { // See [Internal: b/74248644].
      stopSelf();
      logd("stopSelf()");
    } else {
      boolean stopSelfResult = stopSelfResult(lastStartId);
      logd("stopSelf(" + lastStartId + ") result: " + stopSelfResult);
    }
  }

  private void logd(String message) {
    if (DEBUG) {
      Log.d(TAG, message);
    }
  }

  private static Intent getIntent(
      Context context, Class<? extends DownloadService> clazz, String action) {
    return new Intent(context, clazz).setAction(action);
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
      DownloadState[] downloadStates = downloadManager.getAllDownloadStates();
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

  private static final class DownloadManagerHelper implements DownloadManager.Listener {

    private final Context context;
    private final DownloadManager downloadManager;
    @Nullable private final Scheduler scheduler;
    private final Class<? extends DownloadService> serviceClass;
    @Nullable private DownloadService downloadService;

    private DownloadManagerHelper(
        Context context,
        DownloadManager downloadManager,
        @Nullable Scheduler scheduler,
        Class<? extends DownloadService> serviceClass) {
      this.context = context;
      this.downloadManager = downloadManager;
      this.scheduler = scheduler;
      this.serviceClass = serviceClass;
      downloadManager.addListener(this);
      if (scheduler != null) {
        Requirements requirements = downloadManager.getRequirements();
        setSchedulerEnabled(/* enabled= */ !requirements.checkRequirements(context), requirements);
      }
    }

    public void attachService(DownloadService downloadService) {
      Assertions.checkState(this.downloadService == null);
      this.downloadService = downloadService;
    }

    public void detachService(DownloadService downloadService, boolean unschedule) {
      Assertions.checkState(this.downloadService == downloadService);
      this.downloadService = null;
      if (scheduler != null && unschedule) {
        scheduler.cancel();
      }
    }

    @Override
    public void onDownloadStateChanged(
        DownloadManager downloadManager, DownloadState downloadState) {
      if (downloadService != null) {
        downloadService.notifyDownloadStateChange(downloadState);
      }
    }

    @Override
    public void onDownloadRemoved(DownloadManager downloadManager, DownloadState downloadState) {
      if (downloadService != null) {
        downloadService.notifyDownloadRemoved(downloadState);
      }
    }

    @Override
    public final void onIdle(DownloadManager downloadManager) {
      if (downloadService != null) {
        downloadService.stop();
      }
    }

    @Override
    public void onRequirementsStateChanged(
        DownloadManager downloadManager,
        Requirements requirements,
        @Requirements.RequirementFlags int notMetRequirements) {
      boolean requirementsMet = notMetRequirements == 0;
      if (downloadService == null && requirementsMet) {
        try {
          Intent intent = getIntent(context, serviceClass, DownloadService.ACTION_INIT);
          context.startService(intent);
        } catch (IllegalStateException e) {
          /* startService fails if the app is in the background then don't stop the scheduler. */
          return;
        }
      }
      if (scheduler != null) {
        setSchedulerEnabled(/* enabled= */ !requirementsMet, requirements);
      }
    }

    private void setSchedulerEnabled(boolean enabled, Requirements requirements) {
      if (!enabled) {
        scheduler.cancel();
      } else {
        String servicePackage = context.getPackageName();
        boolean success = scheduler.schedule(requirements, servicePackage, ACTION_RESTART);
        if (!success) {
          Log.e(TAG, "Scheduling downloads failed.");
        }
      }
    }
  }
}
