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

import static com.google.android.exoplayer2.offline.DownloadState.FAILURE_REASON_NONE;
import static com.google.android.exoplayer2.offline.DownloadState.FAILURE_REASON_UNKNOWN;
import static com.google.android.exoplayer2.offline.DownloadState.MANUAL_STOP_REASON_NONE;
import static com.google.android.exoplayer2.offline.DownloadState.MANUAL_STOP_REASON_UNDEFINED;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_COMPLETED;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_DOWNLOADING;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_FAILED;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_QUEUED;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_REMOVED;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_REMOVING;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_RESTARTING;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_STOPPED;

import android.content.Context;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.database.DatabaseIOException;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.RequirementsWatcher;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
    default void onInitialized(DownloadManager downloadManager) {}

    /**
     * Called when the state of a download changes.
     *
     * @param downloadManager The reporting instance.
     * @param downloadState The state of the download.
     */
    default void onDownloadStateChanged(
        DownloadManager downloadManager, DownloadState downloadState) {}

    /**
     * Called when there is no active download left.
     *
     * @param downloadManager The reporting instance.
     */
    default void onIdle(DownloadManager downloadManager) {}

    /**
     * Called when the download requirements state changed.
     *
     * @param downloadManager The reporting instance.
     * @param requirements Requirements needed to be met to start downloads.
     * @param notMetRequirements {@link Requirements.RequirementFlags RequirementFlags} that are not
     *     met, or 0.
     */
    default void onRequirementsStateChanged(
        DownloadManager downloadManager,
        Requirements requirements,
        @Requirements.RequirementFlags int notMetRequirements) {}
  }

  /** The default maximum number of simultaneous downloads. */
  public static final int DEFAULT_MAX_SIMULTANEOUS_DOWNLOADS = 1;
  /** The default minimum number of times a download must be retried before failing. */
  public static final int DEFAULT_MIN_RETRY_COUNT = 5;
  /** The default requirement is that the device has network connectivity. */
  public static final Requirements DEFAULT_REQUIREMENTS =
      new Requirements(Requirements.NETWORK_TYPE_ANY, false, false);

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    START_THREAD_SUCCEEDED,
    START_THREAD_WAIT_REMOVAL_TO_FINISH,
    START_THREAD_WAIT_DOWNLOAD_CANCELLATION,
    START_THREAD_TOO_MANY_DOWNLOADS,
    START_THREAD_NOT_ALLOWED
  })
  private @interface StartThreadResults {}

  private static final int START_THREAD_SUCCEEDED = 0;
  private static final int START_THREAD_WAIT_REMOVAL_TO_FINISH = 1;
  private static final int START_THREAD_WAIT_DOWNLOAD_CANCELLATION = 2;
  private static final int START_THREAD_TOO_MANY_DOWNLOADS = 3;
  private static final int START_THREAD_NOT_ALLOWED = 4;

  private static final String TAG = "DownloadManager";
  private static final boolean DEBUG = false;

  private final int maxSimultaneousDownloads;
  private final int minRetryCount;
  private final Context context;
  private final DefaultDownloadIndex downloadIndex;
  private final DownloaderFactory downloaderFactory;
  private final ArrayList<Download> downloads;
  private final HashMap<Download, DownloadThread> activeDownloads;
  private final Handler handler;
  private final HandlerThread fileIOThread;
  private final Handler fileIOHandler;
  private final CopyOnWriteArraySet<Listener> listeners;
  private final ArrayDeque<DownloadUpdater> dowloadUpdateQueue;

  private boolean initialized;
  private boolean released;
  @Requirements.RequirementFlags private int notMetRequirements;
  private int manualStopReason;
  private RequirementsWatcher requirementsWatcher;
  private int simultaneousDownloads;
  private boolean loadingDownload;

  /**
   * Constructs a {@link DownloadManager}.
   *
   * @param context Any context.
   * @param databaseProvider Used to create a {@link DownloadIndex} which holds download states.
   * @param downloaderFactory A factory for creating {@link Downloader}s.
   */
  public DownloadManager(
      Context context, DatabaseProvider databaseProvider, DownloaderFactory downloaderFactory) {
    this(
        context,
        databaseProvider,
        downloaderFactory,
        DEFAULT_MAX_SIMULTANEOUS_DOWNLOADS,
        DEFAULT_MIN_RETRY_COUNT,
        DEFAULT_REQUIREMENTS);
  }

  /**
   * Constructs a {@link DownloadManager}.
   *
   * @param context Any context.
   * @param databaseProvider Used to create a {@link DownloadIndex} which holds download states.
   * @param downloaderFactory A factory for creating {@link Downloader}s.
   * @param maxSimultaneousDownloads The maximum number of simultaneous downloads.
   * @param minRetryCount The minimum number of times a download must be retried before failing.
   * @param requirements The requirements needed to be met to start downloads.
   */
  public DownloadManager(
      Context context,
      DatabaseProvider databaseProvider,
      DownloaderFactory downloaderFactory,
      int maxSimultaneousDownloads,
      int minRetryCount,
      Requirements requirements) {
    this(
        context,
        new DefaultDownloadIndex(databaseProvider),
        downloaderFactory,
        maxSimultaneousDownloads,
        minRetryCount,
        requirements);
  }

  /**
   * Constructs a {@link DownloadManager}.
   *
   * @param context Any context.
   * @param downloadIndex The {@link DefaultDownloadIndex} which holds download states.
   * @param downloaderFactory A factory for creating {@link Downloader}s.
   * @param maxSimultaneousDownloads The maximum number of simultaneous downloads.
   * @param minRetryCount The minimum number of times a download must be retried before failing.
   * @param requirements The requirements needed to be met to start downloads.
   */
  public DownloadManager(
      Context context,
      DefaultDownloadIndex downloadIndex,
      DownloaderFactory downloaderFactory,
      int maxSimultaneousDownloads,
      int minRetryCount,
      Requirements requirements) {
    this.context = context.getApplicationContext();
    this.downloadIndex = downloadIndex;
    this.downloaderFactory = downloaderFactory;
    this.maxSimultaneousDownloads = maxSimultaneousDownloads;
    this.minRetryCount = minRetryCount;

    manualStopReason = MANUAL_STOP_REASON_UNDEFINED;
    downloads = new ArrayList<>();
    activeDownloads = new HashMap<>();

    Looper looper = Looper.myLooper();
    if (looper == null) {
      looper = Looper.getMainLooper();
    }
    handler = new Handler(looper);

    fileIOThread = new HandlerThread("DownloadManager file i/o");
    fileIOThread.start();
    fileIOHandler = new Handler(fileIOThread.getLooper());

    listeners = new CopyOnWriteArraySet<>();
    dowloadUpdateQueue = new ArrayDeque<>();

    setNotMetRequirements(watchRequirements(requirements));
    loadDownloads();
    logd("Created");
  }

  /** Returns the used {@link DownloadIndex}. */
  public DownloadIndex getDownloadIndex() {
    return downloadIndex;
  }

  /**
   * Sets the requirements needed to be met to start downloads.
   *
   * @param requirements Need to be met to start downloads.
   */
  public void setRequirements(Requirements requirements) {
    Assertions.checkState(!released);
    if (requirements.equals(requirementsWatcher.getRequirements())) {
      return;
    }
    requirementsWatcher.stop();
    onRequirementsStateChanged(watchRequirements(requirements));
  }

  /** Returns the requirements needed to be met to start downloads. */
  public Requirements getRequirements() {
    return requirementsWatcher.getRequirements();
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

  /**
   * Clears manual stop reason of all downloads. Downloads are started if the requirements are met.
   */
  public void startDownloads() {
    logd("manual stop is cancelled");
    setManualStopReason(/* id= */ null, MANUAL_STOP_REASON_NONE);
  }

  /** Signals all downloads to stop. Call {@link #startDownloads()} to let them to be started. */
  public void stopDownloads() {
    stopDownloads(/* manualStopReason= */ MANUAL_STOP_REASON_UNDEFINED);
  }

  /**
   * Signals all downloads to stop. Call {@link #startDownloads()} to let them to be started.
   *
   * @param manualStopReason An application defined stop reason. Value {@value
   *     DownloadState#MANUAL_STOP_REASON_NONE} is not allowed and value {@value
   *     DownloadState#MANUAL_STOP_REASON_UNDEFINED} is reserved for {@link
   *     DownloadState#MANUAL_STOP_REASON_UNDEFINED}.
   */
  public void stopDownloads(int manualStopReason) {
    Assertions.checkArgument(manualStopReason != MANUAL_STOP_REASON_NONE);
    logd("downloads are stopped manually");
    setManualStopReason(/* id= */ null, manualStopReason);
  }

  /**
   * Clears manual stop reason of the download with the {@code id}. Download is started if the
   * requirements are met.
   *
   * @param id The unique content id of the download to be started.
   */
  public void startDownload(String id) {
    setManualStopReason(id, MANUAL_STOP_REASON_NONE);
  }

  /**
   * Signals the download with the {@code id} to stop. Call {@link #startDownload(String)} to let it
   * to be started.
   *
   * @param id The unique content id of the download to be stopped.
   */
  public void stopDownload(String id) {
    stopDownload(id, /* manualStopReason= */ MANUAL_STOP_REASON_UNDEFINED);
  }

  /**
   * Signals the download with the {@code id} to stop. Call {@link #startDownload(String)} to let it
   * to be started.
   *
   * @param id The unique content id of the download to be stopped.
   * @param manualStopReason An application defined stop reason. Value {@value
   *     DownloadState#MANUAL_STOP_REASON_NONE} is not allowed and value {@value
   *     DownloadState#MANUAL_STOP_REASON_UNDEFINED} is reserved for {@link
   *     DownloadState#MANUAL_STOP_REASON_UNDEFINED}.
   */
  public void stopDownload(String id, int manualStopReason) {
    Assertions.checkArgument(manualStopReason != MANUAL_STOP_REASON_NONE);
    setManualStopReason(id, manualStopReason);
  }

  /**
   * Adds a download defined by the given action.
   *
   * @param action The download action.
   */
  public void addDownload(DownloadAction action) {
    Assertions.checkState(!released);
    dowloadUpdateQueue.add(
        new DownloadUpdater(action.id) {
          @Override
          void onExisting(Download download) {
            download.addAction(action);
            logd("Action is added to existing download", download);
          }

          @Override
          DownloadState onLoad(@Nullable DownloadState downloadState) {
            DownloadState state;
            if (downloadState == null) {
              state = new DownloadState(action);
              logd("Download state is created for " + id);
            } else {
              state = downloadState.mergeAction(action);
              logd("Download state is loaded for " + id);
            }
            return state;
          }
        });
    if (initialized) {
      processDownloadUpdateQueue();
    }
  }

  /**
   * Cancels the download with the {@code id} and removes all downloaded data.
   *
   * @param id The unique content id of the download to be started.
   */
  public void removeDownload(String id) {
    Assertions.checkState(!released);
    dowloadUpdateQueue.add(
        new DownloadUpdater(id) {
          @Override
          void onExisting(Download download) {
            download.remove();
          }

          @Override
          DownloadState onLoad(@Nullable DownloadState downloadState) {
            if (downloadState != null) {
              downloadState = downloadState.setRemoveState();
            } else {
              logd("Can't remove download. No download with id: " + id);
            }
            return downloadState;
          }
        });
    if (initialized) {
      processDownloadUpdateQueue();
    }
  }

  /** Returns the number of downloads. */
  public int getDownloadCount() {
    Assertions.checkState(!released);
    return downloads.size();
  }

  /** Returns the states of all current downloads. */
  public DownloadState[] getAllDownloadStates() {
    Assertions.checkState(!released);
    DownloadState[] states = new DownloadState[downloads.size()];
    for (int i = 0; i < states.length; i++) {
      states[i] = downloads.get(i).getUpdatedDownloadState();
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
    return initialized
        && activeDownloads.isEmpty()
        && dowloadUpdateQueue.isEmpty()
        && !loadingDownload;
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
    stopAllDownloadThreads();
    if (requirementsWatcher != null) {
      requirementsWatcher.stop();
    }
    final ConditionVariable fileIOFinishedCondition = new ConditionVariable();
    fileIOHandler.post(fileIOFinishedCondition::open);
    fileIOFinishedCondition.block();
    fileIOThread.quit();
    logd("Released");
  }

  private void setManualStopReason(@Nullable String id, int manualStopReason) {
    if (id != null) {
      Download download = getDownload(id);
      if (download != null) {
        logd("download manual stop reason is set to : " + manualStopReason, download);
        download.setManualStopReason(manualStopReason);
        return;
      }
    } else {
      this.manualStopReason = manualStopReason;
      for (int i = 0; i < downloads.size(); i++) {
        downloads.get(i).setManualStopReason(manualStopReason);
      }
    }
    fileIOHandler.post(
        () -> {
          try {
            if (id != null) {
              downloadIndex.setManualStopReason(id, manualStopReason);
            } else {
              downloadIndex.setManualStopReason(manualStopReason);
            }
          } catch (DatabaseIOException e) {
            Log.e(TAG, "setManualStopReason failed", e);
          }
        });
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

  private void onDownloadStateChange(Download download, DownloadState downloadState) {
    if (released) {
      return;
    }
    logd("Download state is changed", download);
    updateDownloadIndex(downloadState);
    for (Listener listener : listeners) {
      listener.onDownloadStateChanged(this, downloadState);
    }
    if (download.isFinished()) {
      downloads.remove(download);
    }
  }

  private void onRequirementsStateChanged(@Requirements.RequirementFlags int notMetRequirements) {
    Requirements requirements = requirementsWatcher.getRequirements();
    for (Listener listener : listeners) {
      listener.onRequirementsStateChanged(DownloadManager.this, requirements, notMetRequirements);
    }
    setNotMetRequirements(notMetRequirements);
  }

  private void setNotMetRequirements(@Requirements.RequirementFlags int notMetRequirements) {
    this.notMetRequirements = notMetRequirements;
    logdFlags("Not met requirements are changed", notMetRequirements);
    for (int i = 0; i < downloads.size(); i++) {
      downloads.get(i).setNotMetRequirements(notMetRequirements);
    }
  }

  @Requirements.RequirementFlags
  private int watchRequirements(Requirements requirements) {
    RequirementsWatcher.Listener listener =
        (requirementsWatcher, notMetRequirements) -> onRequirementsStateChanged(notMetRequirements);
    requirementsWatcher = new RequirementsWatcher(context, listener, requirements);
    return requirementsWatcher.start();
  }

  @Nullable
  private Download getDownload(String id) {
    for (int i = 0; i < downloads.size(); i++) {
      Download download = downloads.get(i);
      if (download.getId().equals(id)) {
        return download;
      }
    }
    return null;
  }

  private void processDownloadUpdateQueue() {
    while (!loadingDownload && !dowloadUpdateQueue.isEmpty()) {
      DownloadUpdater downloadUpdater = dowloadUpdateQueue.remove();
      Download download = getDownload(downloadUpdater.id);
      if (download != null) {
        downloadUpdater.onExisting(download);
      } else {
        loadDownload(downloadUpdater);
      }
    }
  }

  private void loadDownload(DownloadUpdater callback) {
    loadingDownload = true;
    fileIOHandler.post(
        () -> {
          DownloadState downloadState = null;
          try {
            downloadState = downloadIndex.getDownloadState(callback.id);
          } catch (DatabaseIOException e) {
            Log.e(TAG, "loadDownload failed", e);
          }
          DownloadState finalDownloadState = downloadState;
          handler.post(
              () -> {
                loadingDownload = false;
                if (!released) {
                  DownloadState state = callback.onLoad(finalDownloadState);
                  if (state != null) {
                    addDownloadForState(state);
                  }
                  processDownloadUpdateQueue();
                }
              });
        });
  }

  private void loadDownloads() {
    fileIOHandler.post(
        () -> {
          DownloadState[] loadedStates;
          try {
            DownloadStateCursor cursor =
                downloadIndex.getDownloadStates(
                    STATE_QUEUED,
                    STATE_STOPPED,
                    STATE_DOWNLOADING,
                    STATE_REMOVING,
                    STATE_RESTARTING);
            loadedStates = new DownloadState[cursor.getCount()];
            for (int i = 0, length = loadedStates.length; i < length; i++) {
              cursor.moveToNext();
              loadedStates[i] = cursor.getDownloadState();
            }
            logd("Download states are loaded.");
          } catch (Throwable e) {
            Log.e(TAG, "Download state loading failed.", e);
            loadedStates = new DownloadState[0];
          }
          final DownloadState[] states = loadedStates;
          handler.post(
              () -> {
                if (released) {
                  return;
                }
                for (DownloadState downloadState : states) {
                  addDownloadForState(downloadState);
                }
                logd("Downloads are created.");
                initialized = true;
                processDownloadUpdateQueue();
                for (Listener listener : listeners) {
                  listener.onInitialized(DownloadManager.this);
                }
                for (int i = 0; i < downloads.size(); i++) {
                  downloads.get(i).start();
                }
              });
        });
  }

  private void addDownloadForState(DownloadState downloadState) {
    Download download = new Download(this, downloadState, notMetRequirements, manualStopReason);
    downloads.add(download);
    logd("Download is added", download);
  }

  private void updateDownloadIndex(DownloadState downloadState) {
    fileIOHandler.post(
        () -> {
          if (released) {
            return;
          }
          try {
            if (downloadState.state == DownloadState.STATE_REMOVED) {
              downloadIndex.removeDownloadState(downloadState.id);
            } else {
              downloadIndex.putDownloadState(downloadState);
            }
          } catch (DatabaseIOException e) {
            Log.e(TAG, "updateDownloadIndex failed", e);
          }
        });
  }

  private static void logd(String message) {
    if (DEBUG) {
      Log.d(TAG, message);
    }
  }

  private static void logd(String message, Download download) {
    if (DEBUG) {
      logd(message + ": " + download);
    }
  }

  private static void logdFlags(String message, int flags) {
    if (DEBUG) {
      logd(message + ": " + Integer.toBinaryString(flags));
    }
  }

  @StartThreadResults
  private int startDownloadThread(Download download) {
    if (!initialized || released) {
      return START_THREAD_NOT_ALLOWED;
    }
    if (activeDownloads.containsKey(download)) {
      if (stopDownloadThread(download)) {
        return START_THREAD_WAIT_DOWNLOAD_CANCELLATION;
      }
      return START_THREAD_WAIT_REMOVAL_TO_FINISH;
    }
    if (!download.isInRemoveState()) {
      if (simultaneousDownloads == maxSimultaneousDownloads) {
        return START_THREAD_TOO_MANY_DOWNLOADS;
      }
      simultaneousDownloads++;
    }
    activeDownloads.put(download, new DownloadThread(download));
    logd("Download is started", download);
    return START_THREAD_SUCCEEDED;
  }

  private boolean stopDownloadThread(Download download) {
    DownloadThread downloadThread = activeDownloads.get(download);
    if (downloadThread != null && !downloadThread.isRemoveThread) {
      downloadThread.cancel();
      logd("Download is cancelled", download);
      return true;
    }
    return false;
  }

  private void stopAllDownloadThreads() {
    for (Download download : activeDownloads.keySet()) {
      stopDownloadThread(download);
    }
  }

  private void onDownloadThreadStopped(DownloadThread downloadThread, Throwable finalError) {
    if (released) {
      return;
    }
    Download download = downloadThread.download;
    logd("Download is stopped", download);
    activeDownloads.remove(download);
    boolean tryToStartDownloads = false;
    if (!downloadThread.isRemoveThread) {
      // If maxSimultaneousDownloads was hit, there might be a download waiting for a slot.
      tryToStartDownloads = simultaneousDownloads == maxSimultaneousDownloads;
      simultaneousDownloads--;
    }
    download.onDownloadThreadStopped(downloadThread.isCanceled, finalError);
    if (tryToStartDownloads) {
      for (int i = 0;
          simultaneousDownloads < maxSimultaneousDownloads && i < downloads.size();
          i++) {
        downloads.get(i).start();
      }
    }
    maybeNotifyListenersIdle();
  }

  @Nullable
  private Downloader getDownloader(Download download) {
    DownloadThread downloadThread = activeDownloads.get(download);
    if (downloadThread != null) {
      return downloadThread.downloader;
    }
    return null;
  }

  private static final class Download {
    private final DownloadManager downloadManager;

    private DownloadState downloadState;
    @DownloadState.State private int state;
    @MonotonicNonNull @DownloadState.FailureReason private int failureReason;
    @Requirements.RequirementFlags private int notMetRequirements;
    private int manualStopReason;

    private Download(
        DownloadManager downloadManager,
        DownloadState downloadState,
        @Requirements.RequirementFlags int notMetRequirements,
        int manualStopReason) {
      this.downloadManager = downloadManager;
      this.downloadState = downloadState;
      this.notMetRequirements = notMetRequirements;
      this.manualStopReason = manualStopReason;

      initialize(downloadState.state);
    }

    public String getId() {
      return downloadState.id;
    }

    public void addAction(DownloadAction newAction) {
      Assertions.checkArgument(getId().equals(newAction.id));
      if (!downloadState.type.equals(newAction.type)) {
        String format = "Action type (%s) doesn't match existing download type (%s)";
        Log.e(TAG, String.format(format, newAction.type, downloadState.type));
      }
      downloadState = downloadState.mergeAction(newAction);
      initialize(downloadState.state);
    }

    public void remove() {
      initialize(STATE_REMOVING);
    }

    public DownloadState getUpdatedDownloadState() {
      float downloadPercentage = C.PERCENTAGE_UNSET;
      long downloadedBytes = 0;
      long totalBytes = C.LENGTH_UNSET;
      Downloader downloader = downloadManager.getDownloader(this);
      if (downloader != null) {
        downloadPercentage = downloader.getDownloadPercentage();
        downloadedBytes = downloader.getDownloadedBytes();
        totalBytes = downloader.getTotalBytes();
      }
      downloadState =
          new DownloadState(
              downloadState.id,
              downloadState.type,
              downloadState.uri,
              downloadState.cacheKey,
              state,
              downloadPercentage,
              downloadedBytes,
              totalBytes,
              state != STATE_FAILED ? FAILURE_REASON_NONE : failureReason,
              notMetRequirements,
              manualStopReason,
              downloadState.startTimeMs,
              /* updateTimeMs= */ System.currentTimeMillis(),
              downloadState.streamKeys,
              downloadState.customMetadata);
      return downloadState;
    }

    public boolean isFinished() {
      return state == STATE_FAILED || state == STATE_COMPLETED || state == STATE_REMOVED;
    }

    public boolean isIdle() {
      return state != STATE_DOWNLOADING && state != STATE_REMOVING && state != STATE_RESTARTING;
    }

    @Override
    public String toString() {
      return getId() + ' ' + DownloadState.getStateString(state);
    }

    public void start() {
      if (state == STATE_QUEUED || state == STATE_DOWNLOADING) {
        startOrQueue();
      } else if (isInRemoveState()) {
        downloadManager.startDownloadThread(this);
      }
    }

    public void setNotMetRequirements(@Requirements.RequirementFlags int notMetRequirements) {
      this.notMetRequirements = notMetRequirements;
      updateStopState();
    }

    public void setManualStopReason(int manualStopReason) {
      this.manualStopReason = manualStopReason;
      updateStopState();
    }

    public DownloadAction getAction() {
      Assertions.checkState(state != STATE_REMOVED);
      return DownloadAction.createDownloadAction(
          downloadState.type,
          downloadState.uri,
          Arrays.asList(downloadState.streamKeys),
          downloadState.cacheKey,
          downloadState.customMetadata);
    }

    public boolean isInRemoveState() {
      return state == STATE_REMOVING || state == STATE_RESTARTING;
    }

    private void updateStopState() {
      DownloadState oldDownloadState = downloadState;
      if (canStart()) {
        if (state == STATE_STOPPED) {
          startOrQueue();
        }
      } else {
        if (state == STATE_DOWNLOADING || state == STATE_QUEUED) {
          downloadManager.stopDownloadThread(this);
          setState(STATE_STOPPED);
        }
      }
      if (oldDownloadState == downloadState) {
        downloadManager.onDownloadStateChange(this, getUpdatedDownloadState());
      }
    }

    private void initialize(int initialState) {
      // Don't notify listeners with initial state until we make sure we don't switch to
      // another state immediately.
      state = initialState;
      if (isInRemoveState()) {
        downloadManager.startDownloadThread(this);
      } else if (canStart()) {
        startOrQueue();
      } else {
        setState(STATE_STOPPED);
      }
      if (state == initialState) {
        downloadManager.onDownloadStateChange(this, getUpdatedDownloadState());
      }
    }

    private boolean canStart() {
      return manualStopReason == MANUAL_STOP_REASON_NONE && notMetRequirements == 0;
    }

    private void startOrQueue() {
      Assertions.checkState(!isInRemoveState());
      @StartThreadResults int result = downloadManager.startDownloadThread(this);
      Assertions.checkState(result != START_THREAD_WAIT_REMOVAL_TO_FINISH);
      if (result == START_THREAD_SUCCEEDED || result == START_THREAD_WAIT_DOWNLOAD_CANCELLATION) {
        setState(STATE_DOWNLOADING);
      } else {
        setState(STATE_QUEUED);
      }
    }

    private void setState(@DownloadState.State int newState) {
      if (state != newState) {
        state = newState;
        downloadManager.onDownloadStateChange(this, getUpdatedDownloadState());
      }
    }

    private void onDownloadThreadStopped(boolean isCanceled, @Nullable Throwable error) {
      if (isIdle()) {
        return;
      }
      if (isCanceled) {
        downloadManager.startDownloadThread(this);
      } else if (state == STATE_RESTARTING) {
        initialize(STATE_QUEUED);
      } else if (state == STATE_REMOVING) {
        setState(STATE_REMOVED);
      } else { // STATE_DOWNLOADING
        if (error != null) {
          Log.e(TAG, "Download failed: " + downloadState.id, error);
          failureReason = FAILURE_REASON_UNKNOWN;
          setState(STATE_FAILED);
        } else {
          setState(STATE_COMPLETED);
        }
      }
    }

  }

  private class DownloadThread extends Thread {

    private final Download download;
    private final Downloader downloader;
    private final boolean isRemoveThread;
    private volatile boolean isCanceled;

    private DownloadThread(Download download) {
      this.download = download;
      this.downloader = downloaderFactory.createDownloader(download.getAction());
      this.isRemoveThread = download.isInRemoveState();
      start();
    }

    public void cancel() {
      isCanceled = true;
      downloader.cancel();
      interrupt();
    }

    // Methods running on download thread.

    @Override
    public void run() {
      logd("Download started", download);
      Throwable error = null;
      try {
        if (isRemoveThread) {
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
      handler.post(() -> onDownloadThreadStopped(this, finalError));
    }

    private int getRetryDelayMillis(int errorCount) {
      return Math.min((errorCount - 1) * 1000, 5000);
    }
  }

  private abstract static class DownloadUpdater {
    final String id;

    private DownloadUpdater(String id) {
      this.id = id;
    }

    abstract void onExisting(Download download);

    abstract DownloadState onLoad(@Nullable DownloadState downloadState);
  }
}
