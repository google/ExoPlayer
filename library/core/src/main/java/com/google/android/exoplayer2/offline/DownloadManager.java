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

import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.STATE_CANCELED;
import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.STATE_CANCELING;
import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.STATE_ENDED;
import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.STATE_ERROR;
import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.STATE_STARTED;
import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.STATE_STOPPING;
import static com.google.android.exoplayer2.offline.DownloadManager.DownloadState.STATE_WAITING;

import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.IntDef;
import android.util.Log;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.DownloadAction.Deserializer;
import com.google.android.exoplayer2.offline.DownloadManager.DownloadState.State;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages multiple stream download and remove requests.
 *
 * <p>By default downloads are stopped. Call {@link #startDownloads()} to start downloads.
 *
 * <p>WARNING: Methods of this class must be called only on the main thread of the application.
 */
public final class DownloadManager {

  /**
   * Listener for download events. Listener methods are called on the main thread of the
   * application.
   */
  public interface DownloadListener {
    /**
     * Called on download state change.
     *
     * @param downloadManager The reporting instance.
     * @param downloadState The download task.
     */
    void onStateChange(DownloadManager downloadManager, DownloadState downloadState);

    /**
     * Called when there is no active task left.
     *
     * @param downloadManager The reporting instance.
     */
    void onIdle(DownloadManager downloadManager);
  }

  private static final String TAG = "DownloadManager";
  private static final boolean DEBUG = false;

  private final DownloaderConstructorHelper downloaderConstructorHelper;
  private final int maxActiveDownloadTasks;
  private final int minRetryCount;
  private final ActionFile actionFile;
  private final DownloadAction.Deserializer[] deserializers;
  private final ArrayList<DownloadTask> tasks;
  private final ArrayList<DownloadTask> activeDownloadTasks;
  private final Handler handler;
  private final HandlerThread fileIOThread;
  private final Handler fileIOHandler;
  private final CopyOnWriteArraySet<DownloadListener> listeners;

  private int nextTaskId;
  private boolean actionFileLoadCompleted;
  private boolean released;
  private boolean downloadsStopped;

  /**
   * Constructs a {@link DownloadManager}.
   *
   * @param constructorHelper A {@link DownloaderConstructorHelper} to create {@link Downloader}s
   *     for downloading data.
   * @param maxActiveDownloadTasks Max number of download tasks to be started in parallel.
   * @param minRetryCount The minimum number of times the downloads must be retried before failing.
   * @param actionSaveFile File to save active actions.
   * @param deserializers Used to deserialize {@link DownloadAction}s.
   */
  public DownloadManager(
      DownloaderConstructorHelper constructorHelper,
      int maxActiveDownloadTasks,
      int minRetryCount,
      String actionSaveFile,
      Deserializer... deserializers) {
    this.downloaderConstructorHelper = constructorHelper;
    this.maxActiveDownloadTasks = maxActiveDownloadTasks;
    this.minRetryCount = minRetryCount;
    this.actionFile = new ActionFile(new File(actionSaveFile));
    this.deserializers = deserializers;
    this.downloadsStopped = true;

    tasks = new ArrayList<>();
    activeDownloadTasks = new ArrayList<>();

    Looper looper = Looper.myLooper();
    if (looper == null) {
      looper = Looper.getMainLooper();
    }
    handler = new Handler(looper);

    fileIOThread = new HandlerThread("DownloadManager file i/o");
    fileIOThread.start();
    fileIOHandler = new Handler(fileIOThread.getLooper());

    listeners = new CopyOnWriteArraySet<>();

    loadActions();
    logd("DownloadManager is created");
  }

  /**
   * Stops all of the tasks and releases resources. If the action file isn't up to date,
   * waits for the changes to be written.
   */
  public void release() {
    released = true;
    for (int i = 0; i < tasks.size(); i++) {
      tasks.get(i).stop();
    }
    final ConditionVariable fileIOFinishedCondition = new ConditionVariable();
    fileIOHandler.post(new Runnable() {
      @Override
      public void run() {
        fileIOFinishedCondition.open();
      }
    });
    fileIOFinishedCondition.block();
    fileIOThread.quit();
    logd("DownloadManager is released");
  }

  /** Stops all of the download tasks. Call {@link #startDownloads()} to restart tasks. */
  public void stopDownloads() {
    if (!downloadsStopped) {
      downloadsStopped = true;
      for (int i = 0; i < activeDownloadTasks.size(); i++) {
        activeDownloadTasks.get(i).stop();
      }
      logd("Downloads are stopping");
    }
  }

  /** Starts the download tasks. */
  public void startDownloads() {
    if (downloadsStopped) {
      downloadsStopped = false;
      maybeStartTasks();
      logd("Downloads are started");
    }
  }

  /**
   * Adds a {@link DownloadListener}.
   *
   * @param listener The listener to be added.
   */
  public void addListener(DownloadListener listener) {
    listeners.add(listener);
  }

  /**
   * Removes a {@link DownloadListener}.
   *
   * @param listener The listener to be removed.
   */
  public void removeListener(DownloadListener listener) {
    listeners.remove(listener);
  }

  /**
   * Deserializes one {@link DownloadAction} from {@code actionData} and calls {@link
   * #handleAction(DownloadAction)}.
   *
   * @param actionData Serialized {@link DownloadAction} data.
   * @return The task id.
   * @throws IOException If an error occurs during handling action.
   */
  public int handleAction(byte[] actionData) throws IOException {
    ByteArrayInputStream input = new ByteArrayInputStream(actionData);
    DownloadAction action = DownloadAction.deserializeFromStream(deserializers, input);
    return handleAction(action);
  }

  /**
   * Handles the given {@link DownloadAction}. A task is created and added to the task queue. If
   * it's a remove action then this method cancels any download tasks which works on the same media
   * immediately.
   *
   * @param downloadAction Action to be executed.
   * @return The task id.
   */
  public int handleAction(DownloadAction downloadAction) {
    DownloadTask downloadTask = createDownloadTask(downloadAction);
    saveActions();
    if (downloadsStopped && !downloadAction.isRemoveAction()) {
      logd("Can't start the task as downloads are stopped", downloadTask);
    } else {
      maybeStartTasks();
    }
    return downloadTask.id;
  }

  private DownloadTask createDownloadTask(DownloadAction downloadAction) {
    DownloadTask downloadTask = new DownloadTask(nextTaskId++, this, downloadAction, minRetryCount);
    tasks.add(downloadTask);
    logd("Task is added", downloadTask);
    notifyListenersTaskStateChange(downloadTask);
    return downloadTask;
  }

  /** Returns number of tasks. */
  public int getTaskCount() {
    return tasks.size();
  }

  /** Returns a {@link DownloadTask} for a task. */
  public DownloadState getDownloadState(int taskId) {
    for (int i = 0; i < tasks.size(); i++) {
      DownloadTask task = tasks.get(i);
      if (task.id == taskId) {
        return task.getDownloadState();
      }
    }
    return null;
  }

  /** Returns {@link DownloadState}s for all tasks. */
  public DownloadState[] getDownloadStates() {
    return getDownloadStates(tasks);
  }

  /** Returns an array of {@link DownloadState}s for active download tasks. */
  public DownloadState[] getActiveDownloadStates() {
    return getDownloadStates(activeDownloadTasks);
  }

  /** Returns whether there are no active tasks. */
  public boolean isIdle() {
    if (!actionFileLoadCompleted) {
      return false;
    }
    for (int i = 0; i < tasks.size(); i++) {
      if (tasks.get(i).isRunning()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Iterates through the task queue and starts any task if all of the following are true:
   *
   * <ul>
   *   <li>It hasn't started yet.
   *   <li>There are no preceding conflicting tasks.
   *   <li>If it's a download task then there are no preceding download tasks on hold and the
   *       maximum number of active downloads hasn't been reached.
   * </ul>
   *
   * If the task is a remove action then preceding conflicting tasks are canceled.
   */
  private void maybeStartTasks() {
    if (released) {
      return;
    }

    boolean skipDownloadActions = downloadsStopped
        || activeDownloadTasks.size() == maxActiveDownloadTasks;
    for (int i = 0; i < tasks.size(); i++) {
      DownloadTask downloadTask = tasks.get(i);
      if (!downloadTask.canStart()) {
        continue;
      }

      DownloadAction downloadAction = downloadTask.downloadAction;
      boolean removeAction = downloadAction.isRemoveAction();
      if (!removeAction && skipDownloadActions) {
        continue;
      }

      boolean canStartTask = true;
      for (int j = 0; j < i; j++) {
        DownloadTask task = tasks.get(j);
        if (task.downloadAction.isSameMedia(downloadAction)) {
          if (removeAction) {
            canStartTask = false;
            logd(downloadTask + " clashes with " + task);
            task.cancel();
            // Continue loop to cancel any other preceding clashing tasks.
          } else if (task.downloadAction.isRemoveAction()) {
            canStartTask = false;
            skipDownloadActions = true;
            break;
          }
        }
      }

      if (canStartTask) {
        downloadTask.start();
        if (!removeAction) {
          activeDownloadTasks.add(downloadTask);
          skipDownloadActions = activeDownloadTasks.size() == maxActiveDownloadTasks;
        }
      }
    }
  }

  private void maybeNotifyListenersIdle() {
    if (!isIdle()) {
      return;
    }
    logd("Notify idle state");
    for (DownloadListener listener : listeners) {
      listener.onIdle(this);
    }
  }

  private void onTaskStateChange(DownloadTask downloadTask) {
    if (released) {
      return;
    }
    logd("Task state is changed", downloadTask);
    boolean stopped = !downloadTask.isRunning();
    if (stopped) {
      activeDownloadTasks.remove(downloadTask);
    }
    notifyListenersTaskStateChange(downloadTask);
    if (downloadTask.isFinished()) {
      tasks.remove(downloadTask);
      saveActions();
    }
    if (stopped) {
      maybeStartTasks();
      maybeNotifyListenersIdle();
    }
  }

  private void notifyListenersTaskStateChange(DownloadTask downloadTask) {
    DownloadState downloadState = downloadTask.getDownloadState();
    for (DownloadListener listener : listeners) {
      listener.onStateChange(this, downloadState);
    }
  }

  private void loadActions() {
    fileIOHandler.post(
        new Runnable() {
          @Override
          public void run() {
            DownloadAction[] loadedActions;
            try {
              loadedActions = actionFile.load(DownloadManager.this.deserializers);
              logd("Action file is loaded.");
            } catch (Throwable e) {
              Log.e(TAG, "Action file loading failed.", e);
              loadedActions = new DownloadAction[0];
            }
            final DownloadAction[] actions = loadedActions;
            handler.post(
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      for (DownloadAction action : actions) {
                        createDownloadTask(action);
                      }
                      logd("Tasks are created.");
                      maybeStartTasks();
                    } finally {
                      actionFileLoadCompleted = true;
                      maybeNotifyListenersIdle();
                    }
                  }
                });
          }
        });
  }

  private void saveActions() {
    if (!actionFileLoadCompleted || released) {
      return;
    }
    final DownloadAction[] actions = new DownloadAction[tasks.size()];
    for (int i = 0; i < tasks.size(); i++) {
      actions[i] = tasks.get(i).downloadAction;
    }
    fileIOHandler.post(new Runnable() {
      @Override
      public void run() {
        try {
          actionFile.store(actions);
          logd("Actions persisted.");
        } catch (IOException e) {
          Log.e(TAG, "Persisting actions failed.", e);
        }
      }
    });
  }

  private void logd(String message) {
    if (DEBUG) {
      Log.d(TAG, message);
    }
  }

  private void logd(String message, DownloadTask task) {
    logd(message + ": " + task);
  }

  private static DownloadState[] getDownloadStates(ArrayList<DownloadTask> tasks) {
    DownloadState[] states = new DownloadState[tasks.size()];
    for (int i = 0; i < tasks.size(); i++) {
      DownloadTask task = tasks.get(i);
      states[i] = task.getDownloadState();
    }
    return states;
  }

  /** Represents state of a download task. */
  public static final class DownloadState {

    /**
     * Task states.
     *
     * <p>Transition map (vertical states are source states):
     * <pre>
     *           +-------+-------+-----+---------+--------+--------+-----+
     *           |waiting|started|ended|canceling|canceled|stopping|error|
     * +---------+-------+-------+-----+---------+--------+--------+-----+
     * |waiting  |       |   X   |     |    X    |        |        |     |
     * |started  |       |       |  X  |    X    |        |   X    |  X  |
     * |canceling|       |       |     |         |   X    |        |     |
     * |stopping |   X   |       |     |         |        |        |     |
     * +---------+-------+-------+-----+---------+--------+--------+-----+
     * </pre>
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_WAITING, STATE_STARTED, STATE_ENDED, STATE_CANCELING, STATE_CANCELED,
        STATE_STOPPING, STATE_ERROR})
    public @interface State {}
    /** The task is waiting to be started. */
    public static final int STATE_WAITING = 0;
    /** The task is currently started. */
    public static final int STATE_STARTED = 1;
    /** The task completed. */
    public static final int STATE_ENDED = 2;
    /** The task is about to be canceled. */
    public static final int STATE_CANCELING = 3;
    /** The task was canceled. */
    public static final int STATE_CANCELED = 4;
    /** The task is about to be stopped. */
    public static final int STATE_STOPPING = 5;
    /** The task failed. */
    public static final int STATE_ERROR = 6;

    /** Returns whether the task is running. */
    public static boolean isRunning(int state) {
      return state == STATE_STARTED || state == STATE_STOPPING || state == STATE_CANCELING;
    }

    /** Returns whether the task is finished. */
    public static boolean isFinished(int state) {
      return state == STATE_ERROR || state == STATE_ENDED || state == STATE_CANCELED;
    }

    /** Returns the state string for the given state value. */
    public static String getStateString(@State int state) {
      switch (state) {
        case STATE_WAITING:
          return "WAITING";
        case STATE_STARTED:
          return "STARTED";
        case STATE_ENDED:
          return "ENDED";
        case STATE_CANCELING:
          return "CANCELING";
        case STATE_CANCELED:
          return "CANCELED";
        case STATE_STOPPING:
          return "STOPPING";
        case STATE_ERROR:
          return "ERROR";
        default:
          throw new IllegalStateException();
      }
    }

    /** Unique id of the task. */
    public final int taskId;
    /** The {@link DownloadAction} which is being executed. */
    public final DownloadAction downloadAction;
    /** The state of the task. See {@link State}. */
    public final @State int state;
    /**
     * The download percentage, or {@link Float#NaN} if it can't be calculated or the task is for
     * removing.
     */
    public final float downloadPercentage;
    /**
     * The downloaded bytes, or {@link C#LENGTH_UNSET} if it hasn't been calculated yet or the task
     * is for removing.
     */
    public final long downloadedBytes;
    /** If {@link #state} is {@link #STATE_ERROR} then this is the cause, otherwise null. */
    public final Throwable error;

    private DownloadState(
        int taskId,
        DownloadAction downloadAction,
        int state,
        float downloadPercentage,
        long downloadedBytes,
        Throwable error) {
      this.taskId = taskId;
      this.downloadAction = downloadAction;
      this.state = state;
      this.downloadPercentage = downloadPercentage;
      this.downloadedBytes = downloadedBytes;
      this.error = error;
    }

    /** Returns whether the task is finished. */
    public boolean isFinished() {
      return isFinished(state);
    }

    /** Returns whether the task is running. */
    public boolean isRunning() {
      return isRunning(state);
    }
  }

  private static final class DownloadTask implements Runnable {

    private final int id;
    private final DownloadManager downloadManager;
    private final DownloadAction downloadAction;
    private final int minRetryCount;
    private volatile @State int currentState;
    private volatile Downloader downloader;
    private Thread thread;
    private Throwable error;

    private DownloadTask(
        int id, DownloadManager downloadManager, DownloadAction downloadAction, int minRetryCount) {
      this.id = id;
      this.downloadManager = downloadManager;
      this.downloadAction = downloadAction;
      this.currentState = STATE_WAITING;
      this.minRetryCount = minRetryCount;
    }

    public DownloadState getDownloadState() {
      return new DownloadState(
          id, downloadAction, currentState, getDownloadPercentage(), getDownloadedBytes(), error);
    }

    /** Returns the state of the task. */
    public @State int getState() {
      return currentState;
    }

    /** Returns whether the task is finished. */
    public boolean isFinished() {
      return DownloadState.isFinished(currentState);
    }

    /** Returns whether the task is running. */
    public boolean isRunning() {
      return DownloadState.isRunning(currentState);
    }

    /**
     * Returns the download percentage, or {@link Float#NaN} if it can't be calculated yet. This
     * value can be an estimation.
     */
    public float getDownloadPercentage() {
      return downloader != null ? downloader.getDownloadPercentage() : Float.NaN;
    }

    /**
     * Returns the total number of downloaded bytes, or {@link C#LENGTH_UNSET} if it hasn't been
     * calculated yet.
     */
    public long getDownloadedBytes() {
      return downloader != null ? downloader.getDownloadedBytes() : C.LENGTH_UNSET;
    }

    @Override
    public String toString() {
      if (!DEBUG) {
        return super.toString();
      }
      return downloadAction.getType()
          + ' '
          + (downloadAction.isRemoveAction() ? "remove" : "download")
          + ' '
          + downloadAction.getData()
          + ' '
          + DownloadState.getStateString(currentState);
    }

    private void start() {
      if (changeStateAndNotify(STATE_WAITING, STATE_STARTED)) {
        thread = new Thread(this);
        thread.start();
      }
    }

    private boolean canStart() {
      return currentState == STATE_WAITING;
    }

    private void cancel() {
      if (changeStateAndNotify(STATE_WAITING, STATE_CANCELING)) {
        downloadManager.handler.post(new Runnable() {
          @Override
          public void run() {
            changeStateAndNotify(STATE_CANCELING, STATE_CANCELED);
          }
        });
      } else if (changeStateAndNotify(STATE_STARTED, STATE_CANCELING)) {
        thread.interrupt();
      }
    }

    private void stop() {
      if (changeStateAndNotify(STATE_STARTED, STATE_STOPPING)) {
        downloadManager.logd("Stopping", this);
        thread.interrupt();
      }
    }

    private boolean changeStateAndNotify(@State int oldState, @State int newState) {
      return changeStateAndNotify(oldState, newState, null);
    }

    private boolean changeStateAndNotify(@State int oldState, @State int newState,
        Throwable error) {
      if (currentState != oldState) {
        return false;
      }
      currentState = newState;
      this.error = error;
      downloadManager.onTaskStateChange(DownloadTask.this);
      return true;
    }

    /* Methods running on download thread. */

    @Override
    public void run() {
      downloadManager.logd("Task is started", DownloadTask.this);
      Throwable error = null;
      try {
        downloader = downloadAction.createDownloader(downloadManager.downloaderConstructorHelper);
        if (downloadAction.isRemoveAction()) {
          downloader.remove();
        } else {
          int errorCount = 0;
          long errorPosition = C.LENGTH_UNSET;
          while (true) {
            try {
              downloader.download(null);
              break;
            } catch (IOException e) {
              long downloadedBytes = downloader.getDownloadedBytes();
              if (downloadedBytes != errorPosition) {
                downloadManager.logd(
                    "Reset error count. downloadedBytes = " + downloadedBytes, this);
                errorPosition = downloadedBytes;
                errorCount = 0;
              }
              if (currentState != STATE_STARTED || ++errorCount > minRetryCount) {
                throw e;
              }
              downloadManager.logd("Download error. Retry " + errorCount, this);
              Thread.sleep(getRetryDelayMillis(errorCount));
            }
          }
        }
      } catch (Throwable e){
        error = e;
      }
      final Throwable finalError = error;
      downloadManager.handler.post(new Runnable() {
        @Override
        public void run() {
          if (changeStateAndNotify(STATE_STARTED,
              finalError != null ? STATE_ERROR : STATE_ENDED, finalError)
              || changeStateAndNotify(STATE_CANCELING, STATE_CANCELED)
              || changeStateAndNotify(STATE_STOPPING, STATE_WAITING)) {
            return;
          }
          throw new IllegalStateException();
        }
      });
    }

    private int getRetryDelayMillis(int errorCount) {
      return Math.min((errorCount - 1) * 1000, 5000);
    }
  }

}
