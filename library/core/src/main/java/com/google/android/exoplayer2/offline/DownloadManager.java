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
import static com.google.android.exoplayer2.offline.DownloadState.STATE_REMOVING;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_RESTARTING;
import static com.google.android.exoplayer2.offline.DownloadState.STATE_STOPPED;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.database.DatabaseIOException;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.RequirementsWatcher;
import com.google.android.exoplayer2.upstream.cache.CacheUtil.CachingCounters;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
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
     * Called when a download is removed.
     *
     * @param downloadManager The reporting instance.
     * @param downloadState The last state of the download before it was removed.
     */
    default void onDownloadRemoved(DownloadManager downloadManager, DownloadState downloadState) {}

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
  public static final Requirements DEFAULT_REQUIREMENTS = new Requirements(Requirements.NETWORK);

  // Messages posted to the main handler.
  private static final int MSG_INITIALIZED = 0;
  private static final int MSG_PROCESSED = 1;
  private static final int MSG_DOWNLOAD_STATE_CHANGED = 2;
  private static final int MSG_DOWNLOAD_REMOVED = 3;

  // Messages posted to the background handler.
  private static final int MSG_INITIALIZE = 0;
  private static final int MSG_ADD_DOWNLOAD = 1;
  private static final int MSG_REMOVE_DOWNLOAD = 2;
  private static final int MSG_SET_MANUAL_STOP_REASON = 3;
  private static final int MSG_SET_NOT_MET_REQUIREMENTS = 4;
  private static final int MSG_DOWNLOAD_THREAD_STOPPED = 5;
  private static final int MSG_RELEASE = 6;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    START_THREAD_SUCCEEDED,
    START_THREAD_WAIT_REMOVAL_TO_FINISH,
    START_THREAD_WAIT_DOWNLOAD_CANCELLATION,
    START_THREAD_TOO_MANY_DOWNLOADS
  })
  private @interface StartThreadResults {}

  private static final int START_THREAD_SUCCEEDED = 0;
  private static final int START_THREAD_WAIT_REMOVAL_TO_FINISH = 1;
  private static final int START_THREAD_WAIT_DOWNLOAD_CANCELLATION = 2;
  private static final int START_THREAD_TOO_MANY_DOWNLOADS = 3;

  private static final String TAG = "DownloadManager";
  private static final boolean DEBUG = false;

  private final int maxSimultaneousDownloads;
  private final int minRetryCount;
  private final Context context;
  private final DefaultDownloadIndex downloadIndex;
  private final DownloaderFactory downloaderFactory;
  private final Handler mainHandler;
  private final HandlerThread internalThread;
  private final Handler internalHandler;
  private final RequirementsWatcher.Listener requirementsListener;
  private final Object releaseLock;

  // Collections that are accessed on the main thread.
  private final CopyOnWriteArraySet<Listener> listeners;
  private final ArrayList<DownloadState> downloadStates;

  // Collections that are accessed on the internal thread.
  private final ArrayList<Download> downloads;
  private final HashMap<Download, DownloadThread> activeDownloads;

  // Mutable fields that are accessed on the main thread.
  private int pendingMessages;
  private int activeDownloadCount;
  private boolean initialized;
  private boolean released;
  private RequirementsWatcher requirementsWatcher;

  // Mutable fields that are accessed on the internal thread.
  @Requirements.RequirementFlags private int notMetRequirements;
  private int manualStopReason;
  private int simultaneousDownloads;

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
    downloadStates = new ArrayList<>();
    activeDownloads = new HashMap<>();
    listeners = new CopyOnWriteArraySet<>();
    releaseLock = new Object();

    requirementsListener = this::onRequirementsStateChanged;

    mainHandler = new Handler(Util.getLooper(), this::handleMainMessage);
    internalThread = new HandlerThread("DownloadManager file i/o");
    internalThread.start();
    internalHandler = new Handler(internalThread.getLooper(), this::handleInternalMessage);

    requirementsWatcher = new RequirementsWatcher(context, requirementsListener, requirements);
    int notMetRequirements = requirementsWatcher.start();

    pendingMessages = 1;
    internalHandler
        .obtainMessage(MSG_INITIALIZE, notMetRequirements, /* unused */ 0)
        .sendToTarget();
  }

  /** Returns whether the manager has completed initialization. */
  public boolean isInitialized() {
    return initialized;
  }

  /** Returns whether there are no active downloads. */
  public boolean isIdle() {
    return activeDownloadCount == 0 && pendingMessages == 0;
  }

  /** Returns the used {@link DownloadIndex}. */
  public DownloadIndex getDownloadIndex() {
    return downloadIndex;
  }

  /** Returns the number of downloads. */
  public int getDownloadCount() {
    return downloadStates.size();
  }

  /** Returns the states of all current downloads. */
  public DownloadState[] getAllDownloadStates() {
    return downloadStates.toArray(new DownloadState[0]);
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
   * Sets the requirements needed to be met to start downloads.
   *
   * @param requirements Need to be met to start downloads.
   */
  public void setRequirements(Requirements requirements) {
    if (requirements.equals(requirementsWatcher.getRequirements())) {
      return;
    }
    requirementsWatcher.stop();
    requirementsWatcher = new RequirementsWatcher(context, requirementsListener, requirements);
    int notMetRequirements = requirementsWatcher.start();
    onRequirementsStateChanged(requirementsWatcher, notMetRequirements);
  }

  /**
   * Clears manual stop reason of all downloads. Downloads are started if the requirements are met.
   */
  public void startDownloads() {
    postSetManualStopReason(/* id= */ null, MANUAL_STOP_REASON_NONE);
  }

  /** Signals all downloads to stop. Call {@link #startDownloads()} to let them to be started. */
  public void stopDownloads() {
    stopDownloads(MANUAL_STOP_REASON_UNDEFINED);
  }

  /**
   * Sets a manual stop reason for all downloads.
   *
   * @param manualStopReason An application defined stop reason. Value {@value
   *     DownloadState#MANUAL_STOP_REASON_NONE} is not allowed and value {@value
   *     DownloadState#MANUAL_STOP_REASON_UNDEFINED} is reserved for {@link
   *     DownloadState#MANUAL_STOP_REASON_UNDEFINED}.
   */
  public void stopDownloads(int manualStopReason) {
    Assertions.checkArgument(manualStopReason != MANUAL_STOP_REASON_NONE);
    postSetManualStopReason(/* id= */ null, manualStopReason);
  }

  /**
   * Clears manual stop reason of the download with the {@code id}. Download is started if the
   * requirements are met.
   *
   * @param id The unique content id of the download to be started.
   */
  public void startDownload(String id) {
    postSetManualStopReason(id, MANUAL_STOP_REASON_NONE);
  }

  /**
   * Signals the download with the {@code id} to stop. Call {@link #startDownload(String)} to let it
   * to be started.
   *
   * @param id The unique content id of the download to be stopped.
   */
  public void stopDownload(String id) {
    stopDownload(id, MANUAL_STOP_REASON_UNDEFINED);
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
    postSetManualStopReason(id, manualStopReason);
  }

  /**
   * Adds a download defined by the given action.
   *
   * @param action The download action.
   */
  public void addDownload(DownloadAction action) {
    pendingMessages++;
    internalHandler.obtainMessage(MSG_ADD_DOWNLOAD, action).sendToTarget();
  }

  /**
   * Cancels the download with the {@code id} and removes all downloaded data.
   *
   * @param id The unique content id of the download to be started.
   */
  public void removeDownload(String id) {
    pendingMessages++;
    internalHandler.obtainMessage(MSG_REMOVE_DOWNLOAD, id).sendToTarget();
  }

  /**
   * Stops all of the downloads and releases resources. If the action file isn't up to date, waits
   * for the changes to be written. The manager must not be accessed after this method has been
   * called.
   */
  public void release() {
    synchronized (releaseLock) {
      if (released) {
        return;
      }
      internalHandler.sendEmptyMessage(MSG_RELEASE);
      boolean wasInterrupted = false;
      while (!released) {
        try {
          releaseLock.wait();
        } catch (InterruptedException e) {
          wasInterrupted = true;
        }
      }
      if (wasInterrupted) {
        // Restore the interrupted status.
        Thread.currentThread().interrupt();
      }
      mainHandler.removeCallbacksAndMessages(/* token= */ null);
      // Reset state.
      pendingMessages = 0;
      activeDownloadCount = 0;
      initialized = false;
      downloadStates.clear();
    }
  }

  private void postSetManualStopReason(@Nullable String id, int manualStopReason) {
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_SET_MANUAL_STOP_REASON, manualStopReason, /* unused */ 0, id)
        .sendToTarget();
  }

  private void onRequirementsStateChanged(
      RequirementsWatcher requirementsWatcher,
      @Requirements.RequirementFlags int notMetRequirements) {
    Requirements requirements = requirementsWatcher.getRequirements();
    for (Listener listener : listeners) {
      listener.onRequirementsStateChanged(this, requirements, notMetRequirements);
    }
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_SET_NOT_MET_REQUIREMENTS, notMetRequirements, /* unused */ 0)
        .sendToTarget();
  }

  // Main thread message handling.

  @SuppressWarnings("unchecked")
  private boolean handleMainMessage(Message message) {
    switch (message.what) {
      case MSG_INITIALIZED:
        List<DownloadState> downloadStates = (List<DownloadState>) message.obj;
        onInitialized(downloadStates);
        break;
      case MSG_DOWNLOAD_STATE_CHANGED:
        DownloadState state = (DownloadState) message.obj;
        onDownloadStateChanged(state);
        break;
      case MSG_DOWNLOAD_REMOVED:
        state = (DownloadState) message.obj;
        onDownloadRemoved(state);
        break;
      case MSG_PROCESSED:
        int processedMessageCount = message.arg1;
        int activeDownloadCount = message.arg2;
        onMessageProcessed(processedMessageCount, activeDownloadCount);
        break;
      default:
        throw new IllegalStateException();
    }
    return true;
  }

  // TODO: Merge these three events into a single MSG_STATE_CHANGE that can carry all updates. This
  // allows updating idle at the same point as the downloads that can be queried changes.
  private void onInitialized(List<DownloadState> downloadStates) {
    initialized = true;
    this.downloadStates.addAll(downloadStates);
    for (Listener listener : listeners) {
      listener.onInitialized(DownloadManager.this);
    }
  }

  private void onDownloadStateChanged(DownloadState downloadState) {
    int downloadStateIndex = getDownloadStateIndex(downloadState.action.id);
    if (downloadState.state == STATE_COMPLETED || downloadState.state == STATE_FAILED) {
      if (downloadStateIndex != C.INDEX_UNSET) {
        downloadStates.remove(downloadStateIndex);
      }
    } else if (downloadStateIndex != C.INDEX_UNSET) {
      downloadStates.set(downloadStateIndex, downloadState);
    } else {
      downloadStates.add(downloadState);
    }
    for (Listener listener : listeners) {
      listener.onDownloadStateChanged(this, downloadState);
    }
  }

  private void onDownloadRemoved(DownloadState downloadState) {
    downloadStates.remove(getDownloadStateIndex(downloadState.action.id));
    for (Listener listener : listeners) {
      listener.onDownloadRemoved(this, downloadState);
    }
  }

  private void onMessageProcessed(int processedMessageCount, int activeDownloadCount) {
    this.pendingMessages -= processedMessageCount;
    this.activeDownloadCount = activeDownloadCount;
    if (isIdle()) {
      for (Listener listener : listeners) {
        listener.onIdle(this);
      }
    }
  }

  private int getDownloadStateIndex(String id) {
    for (int i = 0; i < downloadStates.size(); i++) {
      if (downloadStates.get(i).action.id.equals(id)) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  // Internal thread message handling.

  private boolean handleInternalMessage(Message message) {
    boolean processedExternalMessage = true;
    switch (message.what) {
      case MSG_INITIALIZE:
        int notMetRequirements = message.arg1;
        initializeInternal(notMetRequirements);
        break;
      case MSG_ADD_DOWNLOAD:
        DownloadAction action = (DownloadAction) message.obj;
        addDownloadInternal(action);
        break;
      case MSG_REMOVE_DOWNLOAD:
        String id = (String) message.obj;
        removeDownloadInternal(id);
        break;
      case MSG_SET_MANUAL_STOP_REASON:
        id = (String) message.obj;
        int manualStopReason = message.arg1;
        setManualStopReasonInternal(id, manualStopReason);
        break;
      case MSG_SET_NOT_MET_REQUIREMENTS:
        notMetRequirements = message.arg1;
        setNotMetRequirementsInternal(notMetRequirements);
        break;
      case MSG_DOWNLOAD_THREAD_STOPPED:
        DownloadThread downloadThread = (DownloadThread) message.obj;
        onDownloadThreadStoppedInternal(downloadThread);
        processedExternalMessage = false;
        break;
      case MSG_RELEASE:
        releaseInternal();
        return true; // Don't post back to mainHandler on release.
      default:
        throw new IllegalStateException();
    }
    mainHandler
        .obtainMessage(MSG_PROCESSED, processedExternalMessage ? 1 : 0, activeDownloads.size())
        .sendToTarget();
    return true;
  }

  private void setManualStopReasonInternal(@Nullable String id, int manualStopReason) {
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
    try {
      if (id != null) {
        downloadIndex.setManualStopReason(id, manualStopReason);
      } else {
        downloadIndex.setManualStopReason(manualStopReason);
      }
    } catch (DatabaseIOException e) {
      Log.e(TAG, "setManualStopReason failed", e);
    }
  }

  private void addDownloadInternal(DownloadAction action) {
    Download download = getDownload(action.id);
    if (download != null) {
      download.addAction(action);
      logd("Action is added to existing download", download);
    } else {
      DownloadState downloadState = loadDownloadState(action.id);
      if (downloadState == null) {
        downloadState = new DownloadState(action);
        logd("Download state is created for " + action.id);
      } else {
        downloadState =
            downloadState.copyWithMergedAction(action, /* canStart= */ notMetRequirements == 0);
        logd("Download state is loaded for " + action.id);
      }
      addDownloadForState(downloadState);
    }
  }

  private void removeDownloadInternal(String id) {
    Download download = getDownload(id);
    if (download != null) {
      download.remove();
    } else {
      DownloadState downloadState = loadDownloadState(id);
      if (downloadState != null) {
        addDownloadForState(downloadState.copyWithState(STATE_REMOVING));
      } else {
        logd("Can't remove download. No download with id: " + id);
      }
    }
  }

  private void onDownloadStateChangedInternal(Download download, DownloadState downloadState) {
    logd("Download state is changed", download);
    try {
      downloadIndex.putDownloadState(downloadState);
    } catch (DatabaseIOException e) {
      Log.e(TAG, "Failed to update index", e);
    }
    if (download.state == STATE_COMPLETED || download.state == STATE_FAILED) {
      downloads.remove(download);
    }
    mainHandler.obtainMessage(MSG_DOWNLOAD_STATE_CHANGED, downloadState).sendToTarget();
  }

  private void onDownloadRemovedInternal(Download download, DownloadState downloadState) {
    logd("Download is removed", download);
    try {
      downloadIndex.removeDownloadState(downloadState.action.id);
    } catch (DatabaseIOException e) {
      Log.e(TAG, "Failed to remove from index", e);
    }
    downloads.remove(download);
    mainHandler.obtainMessage(MSG_DOWNLOAD_REMOVED, downloadState).sendToTarget();
  }

  private void setNotMetRequirementsInternal(
      @Requirements.RequirementFlags int notMetRequirements) {
    if (this.notMetRequirements == notMetRequirements) {
      return;
    }
    this.notMetRequirements = notMetRequirements;
    logdFlags("Not met requirements are changed", notMetRequirements);
    for (int i = 0; i < downloads.size(); i++) {
      downloads.get(i).setNotMetRequirements(notMetRequirements);
    }
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

  private DownloadState loadDownloadState(String id) {
    try {
      return downloadIndex.getDownloadState(id);
    } catch (DatabaseIOException e) {
      Log.e(TAG, "loadDownload failed", e);
    }
    return null;
  }

  private void initializeInternal(int notMetRequirements) {
    this.notMetRequirements = notMetRequirements;
    ArrayList<DownloadState> loadedStates = new ArrayList<>();
    try (DownloadStateCursor cursor =
        downloadIndex.getDownloadStates(
            STATE_QUEUED, STATE_STOPPED, STATE_DOWNLOADING, STATE_REMOVING, STATE_RESTARTING)) {
      while (cursor.moveToNext()) {
        loadedStates.add(cursor.getDownloadState());
      }
      logd("Download states are loaded.");
    } catch (Throwable e) {
      Log.e(TAG, "Download state loading failed.", e);
      loadedStates.clear();
    }
    for (DownloadState downloadState : loadedStates) {
      addDownloadForState(downloadState);
    }
    logd("Downloads are created.");
    mainHandler.obtainMessage(MSG_INITIALIZED, loadedStates).sendToTarget();
    for (int i = 0; i < downloads.size(); i++) {
      downloads.get(i).start();
    }
  }

  private void addDownloadForState(DownloadState downloadState) {
    Download download = new Download(this, downloadState, notMetRequirements, manualStopReason);
    downloads.add(download);
    logd("Download is added", download);
    download.initialize();
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
    DownloadThread downloadThread = new DownloadThread(download);
    activeDownloads.put(download, downloadThread);
    download.setCounters(downloadThread.downloader.getCounters());
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

  private void releaseInternal() {
    for (Download download : activeDownloads.keySet()) {
      stopDownloadThread(download);
    }
    internalThread.quit();
    synchronized (releaseLock) {
      released = true;
      releaseLock.notifyAll();
    }
  }

  private void onDownloadThreadStoppedInternal(DownloadThread downloadThread) {
    Download download = downloadThread.download;
    logd("Download is stopped", download);
    activeDownloads.remove(download);
    boolean tryToStartDownloads = false;
    if (!downloadThread.isRemoveThread) {
      // If maxSimultaneousDownloads was hit, there might be a download waiting for a slot.
      tryToStartDownloads = simultaneousDownloads == maxSimultaneousDownloads;
      simultaneousDownloads--;
    }
    download.onDownloadThreadStopped(downloadThread.isCanceled, downloadThread.finalError);
    if (tryToStartDownloads) {
      for (int i = 0;
          simultaneousDownloads < maxSimultaneousDownloads && i < downloads.size();
          i++) {
        downloads.get(i).start();
      }
    }
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
    }

    private void initialize() {
      initialize(downloadState.state);
    }

    public String getId() {
      return downloadState.action.id;
    }

    public void addAction(DownloadAction newAction) {
      downloadState =
          downloadState.copyWithMergedAction(newAction, /* canStart= */ notMetRequirements == 0);
      initialize();
    }

    public void remove() {
      initialize(STATE_REMOVING);
    }

    public DownloadState getUpdatedDownloadState() {
      downloadState =
          new DownloadState(
              downloadState.action,
              state,
              state != STATE_FAILED ? FAILURE_REASON_NONE : failureReason,
              manualStopReason,
              downloadState.startTimeMs,
              /* updateTimeMs= */ System.currentTimeMillis(),
              downloadState.counters);
      return downloadState;
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

    public boolean isInRemoveState() {
      return state == STATE_REMOVING || state == STATE_RESTARTING;
    }

    public void setCounters(CachingCounters counters) {
      downloadState.setCounters(counters);
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
        downloadManager.onDownloadStateChangedInternal(this, getUpdatedDownloadState());
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
        downloadManager.onDownloadStateChangedInternal(this, getUpdatedDownloadState());
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
        downloadManager.onDownloadStateChangedInternal(this, getUpdatedDownloadState());
      }
    }

    private void onDownloadThreadStopped(boolean isCanceled, @Nullable Throwable error) {
      if (isIdle()) {
        return;
      }
      if (isCanceled) {
        downloadManager.startDownloadThread(this);
      } else if (state == STATE_REMOVING) {
        downloadManager.onDownloadRemovedInternal(this, getUpdatedDownloadState());
      } else if (state == STATE_RESTARTING) {
        initialize(STATE_QUEUED);
      } else { // STATE_DOWNLOADING
        if (error != null) {
          Log.e(TAG, "Download failed: " + downloadState.action.id, error);
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
    private Throwable finalError;

    private DownloadThread(Download download) {
      this.download = download;
      this.downloader = downloaderFactory.createDownloader(download.downloadState.action);
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
        finalError = e;
      }
      internalHandler.obtainMessage(MSG_DOWNLOAD_THREAD_STOPPED, this).sendToTarget();
    }

    private int getRetryDelayMillis(int errorCount) {
      return Math.min((errorCount - 1) * 1000, 5000);
    }
  }
}
