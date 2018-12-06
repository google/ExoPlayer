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

import static com.google.android.exoplayer2.offline.DownloadManager.TaskState.STATE_CANCELED;
import static com.google.android.exoplayer2.offline.DownloadManager.TaskState.STATE_COMPLETED;
import static com.google.android.exoplayer2.offline.DownloadManager.TaskState.STATE_FAILED;
import static com.google.android.exoplayer2.offline.DownloadManager.TaskState.STATE_QUEUED;
import static com.google.android.exoplayer2.offline.DownloadManager.TaskState.STATE_STARTED;

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
import java.util.ArrayList;
import java.util.List;
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
     * Called when the state of a task changes.
     *
     * @param downloadManager The reporting instance.
     * @param taskState The state of the task.
     */
    void onTaskStateChanged(DownloadManager downloadManager, TaskState taskState);

    /**
     * Called when there is no active task left.
     *
     * @param downloadManager The reporting instance.
     */
    void onIdle(DownloadManager downloadManager);
  }

  /** The default maximum number of simultaneous download tasks. */
  public static final int DEFAULT_MAX_SIMULTANEOUS_DOWNLOADS = 1;
  /** The default minimum number of times a task must be retried before failing. */
  public static final int DEFAULT_MIN_RETRY_COUNT = 5;

  private static final String TAG = "DownloadManager";
  private static final boolean DEBUG = false;

  private final int maxActiveDownloadTasks;
  private final int minRetryCount;
  private final ActionFile actionFile;
  private final DownloaderFactory downloaderFactory;
  private final ArrayList<Task> tasks;
  private final ArrayList<Task> activeDownloadTasks;
  private final Handler handler;
  private final HandlerThread fileIOThread;
  private final Handler fileIOHandler;
  private final CopyOnWriteArraySet<Listener> listeners;

  private int nextTaskId;
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
   * @param maxSimultaneousDownloads The maximum number of simultaneous download tasks.
   * @param minRetryCount The minimum number of times a task must be retried before failing.
   */
  public DownloadManager(
      File actionFile,
      DownloaderFactory downloaderFactory,
      int maxSimultaneousDownloads,
      int minRetryCount) {
    this.actionFile = new ActionFile(actionFile);
    this.downloaderFactory = downloaderFactory;
    this.maxActiveDownloadTasks = maxSimultaneousDownloads;
    this.minRetryCount = minRetryCount;
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

  /** Starts the download tasks. */
  public void startDownloads() {
    Assertions.checkState(!released);
    if (downloadsStopped) {
      downloadsStopped = false;
      maybeStartTasks();
      logd("Downloads are started");
    }
  }

  /** Stops all of the download tasks. Call {@link #startDownloads()} to restart tasks. */
  public void stopDownloads() {
    Assertions.checkState(!released);
    if (!downloadsStopped) {
      downloadsStopped = true;
      for (int i = 0; i < activeDownloadTasks.size(); i++) {
        activeDownloadTasks.get(i).stop();
      }
      logd("Downloads are stopping");
    }
  }

  /**
   * Handles the given action. A task is created and added to the task queue. If it's a remove
   * action then any download tasks for the same media are immediately canceled.
   *
   * @param action The action to be executed.
   * @return The id of the newly created task.
   */
  public int handleAction(DownloadAction action) {
    Assertions.checkState(!released);
    Task task = addTaskForAction(action);
    if (initialized) {
      saveActions();
      maybeStartTasks();
      if (task.state == STATE_QUEUED) {
        // Task did not change out of its initial state, and so its initial state won't have been
        // reported to listeners. Do so now.
        notifyListenersTaskStateChange(task);
      }
    }
    return task.id;
  }

  /** Returns the number of tasks. */
  public int getTaskCount() {
    Assertions.checkState(!released);
    return tasks.size();
  }

  /** Returns the number of download tasks. */
  public int getDownloadCount() {
    int count = 0;
    for (int i = 0; i < tasks.size(); i++) {
      if (!tasks.get(i).action.isRemoveAction) {
        count++;
      }
    }
    return count;
  }

  /** Returns the state of a task, or null if no such task exists */
  public @Nullable TaskState getTaskState(int taskId) {
    Assertions.checkState(!released);
    for (int i = 0; i < tasks.size(); i++) {
      Task task = tasks.get(i);
      if (task.id == taskId) {
        return task.getTaskState();
      }
    }
    return null;
  }

  /** Returns the states of all current tasks. */
  public TaskState[] getAllTaskStates() {
    Assertions.checkState(!released);
    TaskState[] states = new TaskState[tasks.size()];
    for (int i = 0; i < states.length; i++) {
      states[i] = tasks.get(i).getTaskState();
    }
    return states;
  }

  /** Returns whether the manager has completed initialization. */
  public boolean isInitialized() {
    Assertions.checkState(!released);
    return initialized;
  }

  /** Returns whether there are no active tasks. */
  public boolean isIdle() {
    Assertions.checkState(!released);
    if (!initialized) {
      return false;
    }
    for (int i = 0; i < tasks.size(); i++) {
      if (tasks.get(i).isStarted()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Stops all of the tasks and releases resources. If the action file isn't up to date, waits for
   * the changes to be written. The manager must not be accessed after this method has been called.
   */
  public void release() {
    if (released) {
      return;
    }
    released = true;
    for (int i = 0; i < tasks.size(); i++) {
      tasks.get(i).stop();
    }
    final ConditionVariable fileIOFinishedCondition = new ConditionVariable();
    fileIOHandler.post(fileIOFinishedCondition::open);
    fileIOFinishedCondition.block();
    fileIOThread.quit();
    logd("Released");
  }

  private Task addTaskForAction(DownloadAction action) {
    Task task = new Task(nextTaskId++, this, downloaderFactory, action, minRetryCount);
    tasks.add(task);
    logd("Task is added", task);
    return task;
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
    if (!initialized || released) {
      return;
    }

    boolean skipDownloadActions = downloadsStopped
        || activeDownloadTasks.size() == maxActiveDownloadTasks;
    for (int i = 0; i < tasks.size(); i++) {
      Task task = tasks.get(i);
      if (!task.canStart()) {
        continue;
      }

      DownloadAction action = task.action;
      boolean isRemoveAction = action.isRemoveAction;
      if (!isRemoveAction && skipDownloadActions) {
        continue;
      }

      boolean canStartTask = true;
      for (int j = 0; j < i; j++) {
        Task otherTask = tasks.get(j);
        if (otherTask.action.isSameMedia(action)) {
          if (isRemoveAction) {
            canStartTask = false;
            logd(task + " clashes with " + otherTask);
            otherTask.cancel();
            // Continue loop to cancel any other preceding clashing tasks.
          } else if (otherTask.action.isRemoveAction) {
            canStartTask = false;
            skipDownloadActions = true;
            break;
          }
        }
      }

      if (canStartTask) {
        task.start();
        if (!isRemoveAction) {
          activeDownloadTasks.add(task);
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
    for (Listener listener : listeners) {
      listener.onIdle(this);
    }
  }

  private void onTaskStateChange(Task task) {
    if (released) {
      return;
    }
    boolean stopped = !task.isStarted();
    if (stopped) {
      activeDownloadTasks.remove(task);
    }
    notifyListenersTaskStateChange(task);
    if (task.isFinished()) {
      tasks.remove(task);
      saveActions();
    }
    if (stopped) {
      maybeStartTasks();
      maybeNotifyListenersIdle();
    }
  }

  private void notifyListenersTaskStateChange(Task task) {
    logd("Task state is changed", task);
    TaskState taskState = task.getTaskState();
    for (Listener listener : listeners) {
      listener.onTaskStateChanged(this, taskState);
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
                List<Task> pendingTasks = new ArrayList<>(tasks);
                tasks.clear();
                for (DownloadAction action : actions) {
                  addTaskForAction(action);
                }
                logd("Tasks are created.");
                initialized = true;
                for (Listener listener : listeners) {
                  listener.onInitialized(DownloadManager.this);
                }
                if (!pendingTasks.isEmpty()) {
                  tasks.addAll(pendingTasks);
                  saveActions();
                }
                maybeStartTasks();
                for (int i = 0; i < tasks.size(); i++) {
                  Task task = tasks.get(i);
                  if (task.state == STATE_QUEUED) {
                    // Task did not change out of its initial state, and so its initial state
                    // won't have been reported to listeners. Do so now.
                    notifyListenersTaskStateChange(task);
                  }
                }
              });
        });
  }

  private void saveActions() {
    if (released) {
      return;
    }
    final DownloadAction[] actions = new DownloadAction[tasks.size()];
    for (int i = 0; i < tasks.size(); i++) {
      actions[i] = tasks.get(i).action;
    }
    fileIOHandler.post(
        () -> {
          try {
            actionFile.store(actions);
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

  private static void logd(String message, Task task) {
    logd(message + ": " + task);
  }

  /** Represents state of a task. */
  public static final class TaskState {

    /**
     * Task states. One of {@link #STATE_QUEUED}, {@link #STATE_STARTED}, {@link #STATE_COMPLETED},
     * {@link #STATE_CANCELED} or {@link #STATE_FAILED}.
     *
     * <p>Transition diagram:
     *
     * <pre>
     *    ┌────────┬─────→ canceled
     * queued ↔ started ┬→ completed
     *                  └→ failed
     * </pre>
     */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_QUEUED, STATE_STARTED, STATE_COMPLETED, STATE_CANCELED, STATE_FAILED})
    public @interface State {}
    /** The task is waiting to be started. */
    public static final int STATE_QUEUED = 0;
    /** The task is currently started. */
    public static final int STATE_STARTED = 1;
    /** The task completed. */
    public static final int STATE_COMPLETED = 2;
    /** The task was canceled. */
    public static final int STATE_CANCELED = 3;
    /** The task failed. */
    public static final int STATE_FAILED = 4;

    /** Returns the state string for the given state value. */
    public static String getStateString(@State int state) {
      switch (state) {
        case STATE_QUEUED:
          return "QUEUED";
        case STATE_STARTED:
          return "STARTED";
        case STATE_COMPLETED:
          return "COMPLETED";
        case STATE_CANCELED:
          return "CANCELED";
        case STATE_FAILED:
          return "FAILED";
        default:
          throw new IllegalStateException();
      }
    }

    /** The unique task id. */
    public final int taskId;
    /** The action being executed. */
    public final DownloadAction action;
    /** The state of the task. */
    public final @State int state;

    /**
     * The estimated download percentage, or {@link C#PERCENTAGE_UNSET} if no estimate is available
     * or if this is a removal task.
     */
    public final float downloadPercentage;
    /** The total number of downloaded bytes. */
    public final long downloadedBytes;

    /** If {@link #state} is {@link #STATE_FAILED} then this is the cause, otherwise null. */
    @Nullable public final Throwable error;

    private TaskState(
        int taskId,
        DownloadAction action,
        @State int state,
        float downloadPercentage,
        long downloadedBytes,
        @Nullable Throwable error) {
      this.taskId = taskId;
      this.action = action;
      this.state = state;
      this.downloadPercentage = downloadPercentage;
      this.downloadedBytes = downloadedBytes;
      this.error = error;
    }

  }

  private static final class Task implements Runnable {

    /** Target states for the download thread. */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STATE_COMPLETED, STATE_QUEUED, STATE_CANCELED})
    public @interface TargetState {}

    private final int id;
    private final DownloadManager downloadManager;
    private final DownloaderFactory downloaderFactory;
    private final DownloadAction action;
    private final int minRetryCount;
    /** The current state of the task. */
    @TaskState.State private int state;
    /**
     * When started, this is the target state that the task will transition to when the download
     * thread stops.
     */
    @TargetState private volatile int targetState;

    @MonotonicNonNull private Downloader downloader;
    @MonotonicNonNull private Thread thread;
    @MonotonicNonNull private Throwable error;

    private Task(
        int id,
        DownloadManager downloadManager,
        DownloaderFactory downloaderFactory,
        DownloadAction action,
        int minRetryCount) {
      this.id = id;
      this.downloadManager = downloadManager;
      this.downloaderFactory = downloaderFactory;
      this.action = action;
      this.minRetryCount = minRetryCount;
      state = STATE_QUEUED;
      targetState = STATE_COMPLETED;
    }

    public TaskState getTaskState() {
      float downloadPercentage = C.PERCENTAGE_UNSET;
      long downloadedBytes = 0;
      if (downloader != null) {
        downloadPercentage = downloader.getDownloadPercentage();
        downloadedBytes = downloader.getDownloadedBytes();
      }
      return new TaskState(id, action, state, downloadPercentage, downloadedBytes, error);
    }

    /** Returns whether the task is finished. */
    public boolean isFinished() {
      return state == STATE_FAILED || state == STATE_COMPLETED || state == STATE_CANCELED;
    }

    /** Returns whether the task is started. */
    public boolean isStarted() {
      return state == STATE_STARTED;
    }

    @Override
    public String toString() {
      return action.type
          + ' '
          + (action.isRemoveAction ? "remove" : "download")
          + ' '
          + TaskState.getStateString(state)
          + ' '
          + TaskState.getStateString(targetState);
    }

    public boolean canStart() {
      return state == STATE_QUEUED;
    }

    public void start() {
      if (state == STATE_QUEUED) {
        state = STATE_STARTED;
        targetState = STATE_COMPLETED;
        downloadManager.onTaskStateChange(this);
        downloader = downloaderFactory.createDownloader(action);
        thread = new Thread(this);
        thread.start();
      }
    }

    public void cancel() {
      if (state == STATE_STARTED) {
        stopDownloadThread(STATE_CANCELED);
      } else if (state == STATE_QUEUED) {
        state = STATE_CANCELED;
        downloadManager.handler.post(() -> downloadManager.onTaskStateChange(this));
      }
    }

    public void stop() {
      if (state == STATE_STARTED && targetState == STATE_COMPLETED) {
        stopDownloadThread(STATE_QUEUED);
      }
    }

    // Internal methods running on the main thread.

    private void stopDownloadThread(@TargetState int targetState) {
      this.targetState = targetState;
      Assertions.checkNotNull(downloader).cancel();
      Assertions.checkNotNull(thread).interrupt();
    }

    private void onDownloadThreadStopped(@Nullable Throwable finalError) {
      @TaskState.State int finalState = targetState;
      if (targetState == STATE_COMPLETED && finalError != null) {
        finalState = STATE_FAILED;
      } else {
        finalError = null;
      }
      state = finalState;
      error = finalError;
      downloadManager.onTaskStateChange(this);
    }

    // Methods running on download thread.

    @Override
    public void run() {
      logd("Task is started", this);
      Throwable error = null;
      try {
        if (action.isRemoveAction) {
          downloader.remove();
        } else {
          int errorCount = 0;
          long errorPosition = C.LENGTH_UNSET;
          while (targetState == STATE_COMPLETED) {
            try {
              downloader.download();
              break;
            } catch (IOException e) {
              if (targetState == STATE_COMPLETED) {
                long downloadedBytes = downloader.getDownloadedBytes();
                if (downloadedBytes != errorPosition) {
                  logd("Reset error count. downloadedBytes = " + downloadedBytes, this);
                  errorPosition = downloadedBytes;
                  errorCount = 0;
                }
                if (++errorCount > minRetryCount) {
                  throw e;
                }
                logd("Download error. Retry " + errorCount, this);
                Thread.sleep(getRetryDelayMillis(errorCount));
              }
            }
          }
        }
      } catch (Throwable e) {
        error = e;
      }
      final Throwable finalError = error;
      downloadManager.handler.post(() -> onDownloadThreadStopped(finalError));
    }

    private int getRetryDelayMillis(int errorCount) {
      return Math.min((errorCount - 1) * 1000, 5000);
    }
  }

}
