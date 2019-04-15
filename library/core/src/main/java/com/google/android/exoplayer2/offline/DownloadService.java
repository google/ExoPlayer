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

import static com.google.android.exoplayer2.offline.Download.MANUAL_STOP_REASON_NONE;

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
import java.util.List;

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
   *   <li>{@link #KEY_MANUAL_STOP_REASON} - An initial manual stop reason for the download. If
   *       omitted {@link Download#MANUAL_STOP_REASON_NONE} is used.
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_ADD = "com.google.android.exoplayer.downloadService.action.ADD";

  /**
   * Starts all downloads except those that are manually stopped (i.e. have a non-zero {@link
   * Download#manualStopReason}). Extras:
   *
   * <ul>
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_START =
      "com.google.android.exoplayer.downloadService.action.START";

  /**
   * Stops all downloads. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_STOP =
      "com.google.android.exoplayer.downloadService.action.STOP";

  /**
   * Sets the manual stop reason for one or all downloads. To clear the manual stop reason, pass
   * {@link Download#MANUAL_STOP_REASON_NONE}. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_CONTENT_ID} - The content id of a single download to update with the manual
   *       stop reason. If omitted, all downloads will be updated.
   *   <li>{@link #KEY_MANUAL_STOP_REASON} - An application provided reason for stopping the
   *       download or downloads, or {@link Download#MANUAL_STOP_REASON_NONE} to clear the manual
   *       stop reason.
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_SET_MANUAL_STOP_REASON =
      "com.google.android.exoplayer.downloadService.action.SET_MANUAL_STOP_REASON";

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

  /**
   * Key for the manual stop reason in {@link #ACTION_SET_MANUAL_STOP_REASON} and {@link
   * #ACTION_ADD} intents.
   */
  public static final String KEY_MANUAL_STOP_REASON = "manual_stop_reason";

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
  private boolean isDestroyed;

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
    return buildAddActionIntent(
        context, clazz, downloadAction, MANUAL_STOP_REASON_NONE, foreground);
  }

  /**
   * Builds an {@link Intent} for adding an action to be executed by the service.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param downloadAction The action to be executed.
   * @param manualStopReason An initial manual stop reason for the download, or {@link
   *     Download#MANUAL_STOP_REASON_NONE} if the download should be started.
   * @param foreground Whether this intent will be used to start the service in the foreground.
   * @return Created Intent.
   */
  public static Intent buildAddActionIntent(
      Context context,
      Class<? extends DownloadService> clazz,
      DownloadAction downloadAction,
      int manualStopReason,
      boolean foreground) {
    return getIntent(context, clazz, ACTION_ADD)
        .putExtra(KEY_DOWNLOAD_ACTION, downloadAction)
        .putExtra(KEY_MANUAL_STOP_REASON, manualStopReason)
        .putExtra(KEY_FOREGROUND, foreground);
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
   * Builds an {@link Intent} for setting the manual stop reason for one or all downloads. To clear
   * the manual stop reason, pass {@link Download#MANUAL_STOP_REASON_NONE}.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param id The content id, or {@code null} to set the manual stop reason for all downloads.
   * @param manualStopReason An application defined stop reason.
   * @return Created Intent.
   */
  public static Intent buildSetManualStopReasonIntent(
      Context context,
      Class<? extends DownloadService> clazz,
      @Nullable String id,
      int manualStopReason) {
    return getIntent(context, clazz, ACTION_STOP)
        .putExtra(KEY_CONTENT_ID, id)
        .putExtra(KEY_MANUAL_STOP_REASON, manualStopReason);
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
      DownloadManager downloadManager = getDownloadManager();
      downloadManager.startDownloads();
      downloadManagerHelper =
          new DownloadManagerHelper(
              getApplicationContext(), downloadManager, getScheduler(), clazz);
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
          Log.e(TAG, "Ignored ADD: Missing download_action extra");
        } else {
          int manualStopReason =
              intent.getIntExtra(KEY_MANUAL_STOP_REASON, Download.MANUAL_STOP_REASON_NONE);
          downloadManager.addDownload(downloadAction, manualStopReason);
        }
        break;
      case ACTION_START:
        downloadManager.startDownloads();
        break;
      case ACTION_STOP:
        downloadManager.stopDownloads();
        break;
      case ACTION_SET_MANUAL_STOP_REASON:
        if (!intent.hasExtra(KEY_MANUAL_STOP_REASON)) {
          Log.e(TAG, "Ignored SET_MANUAL_STOP_REASON: Missing manual_stop_reason extra");
        } else {
          String contentId = intent.getStringExtra(KEY_CONTENT_ID);
          int manualStopReason =
              intent.getIntExtra(KEY_MANUAL_STOP_REASON, Download.MANUAL_STOP_REASON_NONE);
          downloadManager.setManualStopReason(contentId, manualStopReason);
        }
        break;
      case ACTION_REMOVE:
        String contentId = intent.getStringExtra(KEY_CONTENT_ID);
        if (contentId == null) {
          Log.e(TAG, "Ignored REMOVE: Missing content_id extra");
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
    isDestroyed = true;
    DownloadManagerHelper downloadManagerHelper = downloadManagerListeners.get(getClass());
    boolean unschedule = !downloadManager.isWaitingForRequirements();
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
   * with an empty {@code downloads} array. The returned notification is used to satisfy system
   * requirements for foreground services.
   *
   * <p>Download services that do not wish to run in the foreground should be created by setting the
   * {@code foregroundNotificationId} constructor argument to {@link
   * #FOREGROUND_NOTIFICATION_ID_NONE}. This method will not be called in this case, meaning it can
   * be implemented to throw {@link UnsupportedOperationException}.
   *
   * @param downloads The current downloads.
   * @return The foreground notification to display.
   */
  protected abstract Notification getForegroundNotification(List<Download> downloads);

  /**
   * Invalidates the current foreground notification and causes {@link
   * #getForegroundNotification(List)} to be invoked again if the service isn't stopped.
   */
  protected final void invalidateForegroundNotification() {
    if (foregroundNotificationUpdater != null && !isDestroyed) {
      foregroundNotificationUpdater.invalidate();
    }
  }

  /**
   * Called when the state of a download changes. The default implementation is a no-op.
   *
   * @param download The new state of the download.
   */
  protected void onDownloadChanged(Download download) {
    // Do nothing.
  }

  /**
   * Called when a download is removed. The default implementation is a no-op.
   *
   * @param download The last state of the download before it was removed.
   */
  protected void onDownloadRemoved(Download download) {
    // Do nothing.
  }

  private void notifyDownloadChange(Download download) {
    onDownloadChanged(download);
    if (foregroundNotificationUpdater != null) {
      if (download.state == Download.STATE_DOWNLOADING
          || download.state == Download.STATE_REMOVING
          || download.state == Download.STATE_RESTARTING) {
        foregroundNotificationUpdater.startPeriodicUpdates();
      } else {
        foregroundNotificationUpdater.invalidate();
      }
    }
  }

  private void notifyDownloadRemoved(Download download) {
    onDownloadRemoved(download);
    if (foregroundNotificationUpdater != null) {
      foregroundNotificationUpdater.invalidate();
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

  private final class ForegroundNotificationUpdater {

    private final int notificationId;
    private final long updateInterval;
    private final Handler handler;
    private final Runnable updateRunnable;

    private boolean periodicUpdatesStarted;
    private boolean notificationDisplayed;

    public ForegroundNotificationUpdater(int notificationId, long updateInterval) {
      this.notificationId = notificationId;
      this.updateInterval = updateInterval;
      this.handler = new Handler(Looper.getMainLooper());
      this.updateRunnable = this::update;
    }

    public void startPeriodicUpdates() {
      periodicUpdatesStarted = true;
      update();
    }

    public void stopPeriodicUpdates() {
      periodicUpdatesStarted = false;
      handler.removeCallbacks(updateRunnable);
    }

    public void showNotificationIfNotAlready() {
      if (!notificationDisplayed) {
        update();
      }
    }

    public void invalidate() {
      if (notificationDisplayed) {
        update();
      }
    }

    private void update() {
      List<Download> downloads = downloadManager.getCurrentDownloads();
      startForeground(notificationId, getForegroundNotification(downloads));
      notificationDisplayed = true;
      if (periodicUpdatesStarted) {
        handler.removeCallbacks(updateRunnable);
        handler.postDelayed(updateRunnable, updateInterval);
      }
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
    public void onDownloadChanged(DownloadManager downloadManager, Download download) {
      if (downloadService != null) {
        downloadService.notifyDownloadChange(download);
      }
    }

    @Override
    public void onDownloadRemoved(DownloadManager downloadManager, Download download) {
      if (downloadService != null) {
        downloadService.notifyDownloadRemoved(download);
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
