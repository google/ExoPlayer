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

import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.FAILURE_REASON_NONE;
import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.FAILURE_REASON_UNKNOWN;
import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.STATE_COMPLETED;
import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.STATE_FAILED;
import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.STATE_QUEUED;
import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.STATE_STARTED;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Manages multiple stream download and remove requests.
 *
 * <p>A download manager instance must be accessed only from the thread that created it, unless that
 * thread does not have a {@link Looper}. In that case, it must be accessed only from the
 * application's main thread. Registered listeners will be called on the same thread.
 */
public final class DownloadManager {

  /** Listener for {@link DownloadManager} events. */
  public interface Listener {
    /**
     * Called when all actions have been restored.
     *
     * @param downloadManager The reporting instance.
     */
    void onInitialized(DownloadManager downloadManager);
    /**
     * Called when the state of a download changes.
     *
     * @param downloadManager The reporting instance.
     * @param downloadState The state of the download.
     */
    void onDownloadStateChanged(DownloadManager downloadManager, DownloadState downloadState);

    /**
     * Called when there is no active download left.
     *
     * @param downloadManager The reporting instance.
     */
    void onIdle(DownloadManager downloadManager);
  }

  /** The default maximum number of simultaneous downloads. */
  public static final int DEFAULT_MAX_SIMULTANEOUS_DOWNLOADS = 1;
  /** The default minimum number of times a download must be retried before failing. */
  public static final int DEFAULT_MIN_RETRY_COUNT = 5;

  private static final String TAG = "DownloadManager";
  private static final boolean DEBUG = false;

  private final int maxActiveDownloads;
  private final int minRetryCount;
  private final ActionFile actionFile;
  private final DownloaderFactory downloaderFactory;
  private final ArrayList<Download> downloads;
  private final ArrayList<Download> activeDownloads;
  private final Handler handler;
  private final HandlerThread fileIOThread;
  private final Handler fileIOHandler;
  private final CopyOnWriteArraySet<Listener> listeners;
  private final ArrayDeque<DownloadAction> actionQueue;

  private boolean initialized;
  private boolean released;
  private boolean downloadsStopped;

  /**
   * Constructs a {@link DownloadManager}.
   *
   * @param actionFile The file in which active actions are saved.
   * @param downloaderFactory A factory for creating {@link Downloader}s.
   */
  public DownloadManager(File actionFile, DownloaderFactory downloaderFactory) {
    this(
        actionFile, downloaderFactory, DEFAULT_MAX_SIMULTANEOUS_DOWNLOADS, DEFAULT_MIN_RETRY_COUNT);
  }

  /**
   * Constructs a {@link DownloadManager}.
   *
   * @param actionFile The file in which active actions are saved.
   * @param downloaderFactory A factory for creating {@link Downloader}s.
   * @param maxSimultaneousDownloads The maximum number of simultaneous downloads.
   * @param minRetryCount The minimum number of times a download must be retried before failing.
   */
  public DownloadManager(
      File actionFile,
      DownloaderFactory downloaderFactory,
      int maxSimultaneousDownloads,
      int minRetryCount) {
    this.actionFile = new ActionFile(actionFile);
    this.downloaderFactory = downloaderFactory;
    this.maxActiveDownloads = maxSimultaneousDownloads;
    this.minRetryCount = minRetryCount;
    this.downloadsStopped = true;

    downloads = new ArrayList<>();
    activeDownloads = new ArrayList<>();

    Looper looper = Looper.myLooper();
    if (looper == null) {
      looper = Looper.getMainLooper();
    }
    handler = new Handler(looper);

    fileIOThread = new HandlerThread("DownloadManager file i/o");
    fileIOThread.start();
    fileIOHandler = new Handler(fileIOThread.getLooper());

    listeners = new CopyOnWriteArraySet<>();
    actionQueue = new ArrayDeque<>();

    loadActions();
    logd("Created");
  }

  /**
   * Adds a {@link Listener}.
   *
   * @param listener The listener to be added.
   */
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  /**
   * Removes a {@link Listener}.
   *
   * @param listener The listener to be removed.
   */
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  /** Starts the downloads. */
  public void startDownloads() {
    Assertions.checkState(!released);
    if (downloadsStopped) {
      downloadsStopped = false;
      maybeStartDownloads();
      logd("Downloads are started");
    }
  }

  /** Stops all of the downloads. Call {@link #startDownloads()} to restart downloads. */
  public void stopDownloads() {
    Assertions.checkState(!released);
    if (!downloadsStopped) {
      downloadsStopped = true;
      for (int i = 0; i < activeDownloads.size(); i++) {
        activeDownloads.get(i).stop();
      }
      logd("Downloads are stopping");
    }
  }

  /**
   * Handles the given action.
   *
   * @param action The action to be executed.
   */
  public void handleAction(DownloadAction action) {
    Assertions.checkState(!released);
    if (initialized) {
      addDownloadForAction(action);
      saveActions();
    } else {
      actionQueue.add(action);
    }
  }

  /** Returns the number of downloads. */
  public int getDownloadCount() {
    Assertions.checkState(!released);
    return downloads.size();
  }

  /**
   * Returns {@link DownloadState} for the given content id, or null if no such download exists.
   *
   * @param id The unique content id.
   * @return DownloadState for the given content id, or null if no such download exists.
   */
  @Nullable
  public DownloadState getDownloadState(String id) {
    Assertions.checkState(!released);
    for (int i = 0; i < downloads.size(); i++) {
      Download download = downloads.get(i);
      if (download.id.equals(id)) {
        return download.getDownloadState();
      }
    }
    return null;
  }

  /** Returns the states of all current downloads. */
  public DownloadState[] getAllDownloadStates() {
    Assertions.checkState(!released);
    DownloadState[] states = new DownloadState[downloads.size()];
    for (int i = 0; i < states.length; i++) {
      states[i] = downloads.get(i).getDownloadState();
    }
    return states;
  }

  /** Returns whether the manager has completed initialization. */
  public boolean isInitialized() {
    Assertions.checkState(!released);
    return initialized;
  }

  /** Returns whether there are no active downloads. */
  public boolean isIdle() {
    Assertions.checkState(!released);
    if (!initialized) {
      return false;
    }
    for (int i = 0; i < downloads.size(); i++) {
      if (downloads.get(i).isStarted()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Stops all of the downloads and releases resources. If the action file isn't up to date, waits
   * for the changes to be written. The manager must not be accessed after this method has been
   * called.
   */
  public void release() {
    if (released) {
      return;
    }
    released = true;
    for (int i = 0; i < downloads.size(); i++) {
      downloads.get(i).stop();
    }
    final ConditionVariable fileIOFinishedCondition = new ConditionVariable();
    fileIOHandler.post(fileIOFinishedCondition::open);
    fileIOFinishedCondition.block();
    fileIOThread.quit();
    logd("Released");
  }

  private void addDownloadForAction(DownloadAction action) {
    for (int i = 0; i < downloads.size(); i++) {
      Download download = downloads.get(i);
      if (download.action.isSameMedia(action)) {
        download.addAction(action);
        logd("Action is added to existing download", download);
        return;
      }
    }
    Download download = new Download(this, downloaderFactory, action, minRetryCount);
    downloads.add(download);
    logd("Download is added", download);
  }

  /**
   * Iterates through the download queue and starts any download if all of the following are true:
   *
   * <ul>
   *   <li>It hasn't started yet.
   *   <li>The maximum number of active downloads hasn't been reached.
   * </ul>
   */
  private void maybeStartDownloads() {
    if (!initialized || released) {
      return;
    }
    for (int i = 0; i < downloads.size(); i++) {
      maybeStartDownload(downloads.get(i));
    }
  }

  private boolean maybeStartDownload(Download download) {
    if (download.action.isRemoveAction) {
      return download.start();
    } else if (!downloadsStopped && activeDownloads.size() < maxActiveDownloads) {
      if (download.start()) {
        activeDownloads.add(download);
        return true;
      }
    }
    return false;
  }

  private void maybeNotifyListenersIdle() {
    if (!isIdle()) {
      return;
    }
    logd("Notify idle state");
    for (Listener listener : listeners) {
      listener.onIdle(this);
    }
  }

  private void onDownloadStateChange(Download download) {
    if (released) {
      return;
    }
    boolean stopped = !download.isStarted();
    if (stopped) {
      activeDownloads.remove(download);
    }
    notifyListenersDownloadStateChange(download);
    if (download.isFinished()) {
      downloads.remove(download);
      saveActions();
    }
    if (stopped) {
      maybeStartDownloads();
      maybeNotifyListenersIdle();
    }
  }

  private void notifyListenersDownloadStateChange(Download download) {
    logd("Download state is changed", download);
    DownloadState downloadState = download.getDownloadState();
    for (Listener listener : listeners) {
      listener.onDownloadStateChanged(this, downloadState);
    }
  }

  private void loadActions() {
    fileIOHandler.post(
        () -> {
          DownloadAction[] loadedActions;
          try {
            loadedActions = actionFile.load();
            logd("Action file is loaded.");
          } catch (Throwable e) {
            Log.e(TAG, "Action file loading failed.", e);
            loadedActions = new DownloadAction[0];
          }
          final DownloadAction[] actions = loadedActions;
          handler.post(
              () -> {
                if (released) {
                  return;
                }
                for (DownloadAction action : actions) {
                  addDownloadForAction(action);
                }
                logd("Downloads are created.");
                initialized = true;
                for (Listener listener : listeners) {
                  listener.onInitialized(DownloadManager.this);
                }
                if (!actionQueue.isEmpty()) {
                  while (!actionQueue.isEmpty()) {
                    addDownloadForAction(actionQueue.remove());
                  }
                  saveActions();
                }
              });
        });
  }

  private void saveActions() {
    if (released) {
      return;
    }
    ArrayList<DownloadAction> actions = new ArrayList<>(downloads.size());
    for (int i = 0; i < downloads.size(); i++) {
      actions.addAll(downloads.get(i).actionQueue);
    }
    final DownloadAction[] actionsArray = actions.toArray(new DownloadAction[0]);
    fileIOHandler.post(
        () -> {
          try {
            actionFile.store(actionsArray);
            logd("Actions persisted.");
          } catch (IOException e) {
            Log.e(TAG, "Persisting actions failed.", e);
          }
        });
  }

  private static void logd(String message) {
    if (DEBUG) {
      Log.d(TAG, message);
    }
  }

  private static void logd(String message, Download download) {
    logd(message + ": " + download);
  }

  /** Represents state of a download. */
  public static final class DownloadState {

    /**
     * Download states. One of {@link #STATE_QUEUED}, {@link #STATE_STARTED}, {@link
     * #STATE_COMPLETED} or {@link #STATE_FAILED}.
     *
     * <p>Transition diagram:
     *
     * <pre>
     * queued ↔ started ┬→ completed
     *                  └→ failed
     * </pre>
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_QUEUED, STATE_STARTED, STATE_COMPLETED, STATE_FAILED})
    public @interface State {}
    /** The download is waiting to be started. */
    public static final int STATE_QUEUED = 0;
    /** The download is currently started. */
    public static final int STATE_STARTED = 1;
    /** The download completed. */
    public static final int STATE_COMPLETED = 2;
    /** The download failed. */
    public static final int STATE_FAILED = 3;

    /** Failure reasons. Either {@link #FAILURE_REASON_NONE} or {@link #FAILURE_REASON_UNKNOWN}. */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({FAILURE_REASON_NONE, FAILURE_REASON_UNKNOWN})
    public @interface FailureReason {}
    /** The download isn't failed. */
    public static final int FAILURE_REASON_NONE = 0;
    /** The download is failed because of unknown reason. */
    public static final int FAILURE_REASON_UNKNOWN = 1;

    /** Returns the state string for the given state value. */
    public static String getStateString(@State int state) {
      switch (state) {
        case STATE_QUEUED:
          return "QUEUED";
        case STATE_STARTED:
          return "STARTED";
        case STATE_COMPLETED:
          return "COMPLETED";
        case STATE_FAILED:
          return "FAILED";
        default:
          throw new IllegalStateException();
      }
    }

    /** Returns the failure string for the given failure reason value. */
    public static String getFailureString(@FailureReason int failureReason) {
      switch (failureReason) {
        case FAILURE_REASON_NONE:
          return "NO_REASON";
        case FAILURE_REASON_UNKNOWN:
          return "UNKNOWN_REASON";
        default:
          throw new IllegalStateException();
      }
    }

    /** The unique content id. */
    public final String id;
    /** The action being executed. */
    public final DownloadAction action;
    /** The state of the download. */
    public final @State int state;
    /** The estimated download percentage, or {@link C#PERCENTAGE_UNSET} if unavailable. */
    public final float downloadPercentage;
    /** The total number of downloaded bytes. */
    public final long downloadedBytes;
    /** The total size of the media, or {@link C#LENGTH_UNSET} if unknown. */
    public final long totalBytes;
    /** The first time when download entry is created. */
    public final long startTimeMs;
    /** The last update time. */
    public final long updateTimeMs;

    /**
     * If {@link #state} is {@link #STATE_FAILED} then this is the cause, otherwise {@link
     * #FAILURE_REASON_NONE}.
     */
    @FailureReason public final int failureReason;

    private DownloadState(
        DownloadAction action,
        @State int state,
        float downloadPercentage,
        long downloadedBytes,
        long totalBytes,
        @FailureReason int failureReason,
        long startTimeMs) {
      Assertions.checkState(
          failureReason == FAILURE_REASON_NONE ? state != STATE_FAILED : state == STATE_FAILED);
      this.id = action.id;
      this.action = action;
      this.state = state;
      this.downloadPercentage = downloadPercentage;
      this.downloadedBytes = downloadedBytes;
      this.totalBytes = totalBytes;
      this.failureReason = failureReason;
      this.startTimeMs = startTimeMs;
      updateTimeMs = System.currentTimeMillis();
    }

  }

  private static final class Download {

    private final String id;
    private final DownloadManager downloadManager;
    private final DownloaderFactory downloaderFactory;
    private final int minRetryCount;
    private final long startTimeMs;
    private final ArrayDeque<DownloadAction> actionQueue;
    private DownloadAction action;
    /** The current state of the download. */
    @DownloadState.State private int state;

    @MonotonicNonNull private Downloader downloader;
    @MonotonicNonNull private DownloadThread downloadThread;
    @MonotonicNonNull @DownloadState.FailureReason private int failureReason;

    private Download(
        DownloadManager downloadManager,
        DownloaderFactory downloaderFactory,
        DownloadAction action,
        int minRetryCount) {
      this.id = action.id;
      this.downloadManager = downloadManager;
      this.downloaderFactory = downloaderFactory;
      this.action = action;
      this.minRetryCount = minRetryCount;
      this.startTimeMs = System.currentTimeMillis();
      state = STATE_QUEUED;
      actionQueue = new ArrayDeque<>();
      actionQueue.add(action);
      if (!downloadManager.maybeStartDownload(this)) {
        // If download is started, listeners are already notified about the started state. Otherwise
        // notify them here about the queued state.
        downloadManager.onDownloadStateChange(this);
      }
    }

    public void addAction(DownloadAction newAction) {
      Assertions.checkState(action.type.equals(newAction.type));
      actionQueue.add(newAction);
      DownloadAction updatedAction = DownloadActionUtil.mergeActions(actionQueue);
      if (action.equals(updatedAction)) {
        return;
      }
      if (state == STATE_STARTED) {
        stopDownloadThread();
      } else {
        Assertions.checkState(state == STATE_QUEUED);
        action = updatedAction;
        downloadManager.onDownloadStateChange(this);
      }
    }

    public DownloadState getDownloadState() {
      float downloadPercentage = C.PERCENTAGE_UNSET;
      long downloadedBytes = 0;
      long totalBytes = C.LENGTH_UNSET;
      if (downloader != null) {
        downloadPercentage = downloader.getDownloadPercentage();
        downloadedBytes = downloader.getDownloadedBytes();
        totalBytes = downloader.getTotalBytes();
      }
      return new DownloadState(
          action,
          state,
          downloadPercentage,
          downloadedBytes,
          totalBytes,
          failureReason,
          startTimeMs);
    }

    /** Returns whether the download is finished. */
    public boolean isFinished() {
      return state == STATE_FAILED || state == STATE_COMPLETED;
    }

    /** Returns whether the download is started. */
    public boolean isStarted() {
      return state == STATE_STARTED;
    }

    @Override
    public String toString() {
      return action.type
          + ' '
          + (action.isRemoveAction ? "remove" : "download")
          + ' '
          + DownloadState.getStateString(state);
    }

    public boolean start() {
      if (state != STATE_QUEUED) {
        return false;
      }
      state = STATE_STARTED;
      action = actionQueue.peek();
      downloader = downloaderFactory.createDownloader(action);
      downloadThread =
          new DownloadThread(
              this, downloader, action.isRemoveAction, minRetryCount, downloadManager.handler);
      downloadManager.onDownloadStateChange(this);
      return true;
    }

    public void stop() {
      if (state == STATE_STARTED) {
        stopDownloadThread();
      }
    }

    // Internal methods running on the main thread.

    private void stopDownloadThread() {
      Assertions.checkNotNull(downloadThread).cancel();
    }

    private void onDownloadThreadStopped(@Nullable Throwable finalError) {
      failureReason = FAILURE_REASON_NONE;
      if (!downloadThread.isCanceled) {
        if (finalError != null) {
          state = STATE_FAILED;
          failureReason = FAILURE_REASON_UNKNOWN;
        } else {
          actionQueue.remove();
          if (!actionQueue.isEmpty()) {
            // Don't continue running. Wait to be restarted by maybeStartDownloads().
            state = STATE_QUEUED;
            action = actionQueue.peek();
          } else {
            state = STATE_COMPLETED;
          }
        }
      } else {
        state = STATE_QUEUED;
      }
      downloadManager.onDownloadStateChange(this);
    }
  }

  private static class DownloadThread implements Runnable {

    private final Download download;
    private final Downloader downloader;
    private final boolean remove;
    private final int minRetryCount;
    private final Handler callbackHandler;
    private final Thread thread;
    private volatile boolean isCanceled;

    private DownloadThread(
        Download download,
        Downloader downloader,
        boolean remove,
        int minRetryCount,
        Handler callbackHandler) {
      this.download = download;
      this.downloader = downloader;
      this.remove = remove;
      this.minRetryCount = minRetryCount;
      this.callbackHandler = callbackHandler;
      thread = new Thread(this);
      thread.start();
    }

    public void cancel() {
      isCanceled = true;
      downloader.cancel();
      thread.interrupt();
    }

    // Methods running on download thread.

    @Override
    public void run() {
      logd("Download is started", download);
      Throwable error = null;
      try {
        if (remove) {
          downloader.remove();
        } else {
          int errorCount = 0;
          long errorPosition = C.LENGTH_UNSET;
          while (!isCanceled) {
            try {
              downloader.download();
              break;
            } catch (IOException e) {
              if (!isCanceled) {
                long downloadedBytes = downloader.getDownloadedBytes();
                if (downloadedBytes != errorPosition) {
                  logd("Reset error count. downloadedBytes = " + downloadedBytes, download);
                  errorPosition = downloadedBytes;
                  errorCount = 0;
                }
                if (++errorCount > minRetryCount) {
                  throw e;
                }
                logd("Download error. Retry " + errorCount, download);
                Thread.sleep(getRetryDelayMillis(errorCount));
              }
            }
          }
        }
      } catch (Throwable e) {
        error = e;
      }
      final Throwable finalError = error;
      callbackHandler.post(() -> download.onDownloadThreadStopped(isCanceled ? null : finalError));
    }

    private int getRetryDelayMillis(int errorCount) {
      return Math.min((errorCount - 1) * 1000, 5000);
    }
  }

}
