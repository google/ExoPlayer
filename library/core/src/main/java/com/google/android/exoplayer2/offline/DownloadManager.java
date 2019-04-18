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

import static com.google.android.exoplayer2.offline.Download.FAILURE_REASON_NONE;
import static com.google.android.exoplayer2.offline.Download.FAILURE_REASON_UNKNOWN;
import static com.google.android.exoplayer2.offline.Download.STATE_COMPLETED;
import static com.google.android.exoplayer2.offline.Download.STATE_DOWNLOADING;
import static com.google.android.exoplayer2.offline.Download.STATE_FAILED;
import static com.google.android.exoplayer2.offline.Download.STATE_QUEUED;
import static com.google.android.exoplayer2.offline.Download.STATE_REMOVING;
import static com.google.android.exoplayer2.offline.Download.STATE_RESTARTING;
import static com.google.android.exoplayer2.offline.Download.STATE_STOPPED;
import static com.google.android.exoplayer2.offline.Download.STOP_REASON_NONE;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.RequirementsWatcher;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Manages downloads.
 *
 * <p>Normally a download manager should be accessed via a {@link DownloadService}. When a download
 * manager is used directly instead, downloads will be initially paused and so must be resumed by
 * calling {@link #resumeDownloads()}.
 *
 * <p>A download manager instance must be accessed only from the thread that created it, unless that
 * thread does not have a {@link Looper}. In that case, it must be accessed only from the
 * application's main thread. Registered listeners will be called on the same thread.
 */
public final class DownloadManager {

  /** Listener for {@link DownloadManager} events. */
  public interface Listener {

    /**
     * Called when all downloads have been restored.
     *
     * @param downloadManager The reporting instance.
     */
    default void onInitialized(DownloadManager downloadManager) {}

    /**
     * Called when the state of a download changes.
     *
     * @param downloadManager The reporting instance.
     * @param download The state of the download.
     */
    default void onDownloadChanged(DownloadManager downloadManager, Download download) {}

    /**
     * Called when a download is removed.
     *
     * @param downloadManager The reporting instance.
     * @param download The last state of the download before it was removed.
     */
    default void onDownloadRemoved(DownloadManager downloadManager, Download download) {}

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

  /** The default maximum number of parallel downloads. */
  public static final int DEFAULT_MAX_PARALLEL_DOWNLOADS = 3;
  /** The default minimum number of times a download must be retried before failing. */
  public static final int DEFAULT_MIN_RETRY_COUNT = 5;
  /** The default requirement is that the device has network connectivity. */
  public static final Requirements DEFAULT_REQUIREMENTS = new Requirements(Requirements.NETWORK);

  // Messages posted to the main handler.
  private static final int MSG_INITIALIZED = 0;
  private static final int MSG_PROCESSED = 1;
  private static final int MSG_DOWNLOAD_CHANGED = 2;
  private static final int MSG_DOWNLOAD_REMOVED = 3;

  // Messages posted to the background handler.
  private static final int MSG_INITIALIZE = 0;
  private static final int MSG_SET_DOWNLOADS_RESUMED = 1;
  private static final int MSG_SET_NOT_MET_REQUIREMENTS = 2;
  private static final int MSG_SET_STOP_REASON = 3;
  private static final int MSG_ADD_DOWNLOAD = 4;
  private static final int MSG_REMOVE_DOWNLOAD = 5;
  private static final int MSG_DOWNLOAD_THREAD_STOPPED = 6;
  private static final int MSG_CONTENT_LENGTH_CHANGED = 7;
  private static final int MSG_RELEASE = 8;

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

  private final Context context;
  private final WritableDownloadIndex downloadIndex;
  private final DownloaderFactory downloaderFactory;
  private final Handler mainHandler;
  private final HandlerThread internalThread;
  private final Handler internalHandler;
  private final RequirementsWatcher.Listener requirementsListener;
  private final Object releaseLock;

  // Collections that are accessed on the main thread.
  private final CopyOnWriteArraySet<Listener> listeners;
  private final ArrayList<Download> downloads;

  // Collections that are accessed on the internal thread.
  private final ArrayList<DownloadInternal> downloadInternals;
  private final HashMap<String, DownloadThread> downloadThreads;

  // Mutable fields that are accessed on the main thread.
  private int pendingMessages;
  private int activeDownloadCount;
  private boolean initialized;
  private boolean released;
  private RequirementsWatcher requirementsWatcher;

  // Mutable fields that are accessed on the internal thread.
  @Requirements.RequirementFlags private int notMetRequirements;
  private boolean downloadsResumed;
  private int parallelDownloads;

  // TODO: Fix these to properly support changes at runtime.
  private volatile int maxParallelDownloads;
  private volatile int minRetryCount;

  /**
   * Constructs a {@link DownloadManager}.
   *
   * @param context Any context.
   * @param downloadIndex The download index used to hold the download information.
   * @param downloaderFactory A factory for creating {@link Downloader}s.
   */
  public DownloadManager(
      Context context, WritableDownloadIndex downloadIndex, DownloaderFactory downloaderFactory) {
    this.context = context.getApplicationContext();
    this.downloadIndex = downloadIndex;
    this.downloaderFactory = downloaderFactory;
    maxParallelDownloads = DEFAULT_MAX_PARALLEL_DOWNLOADS;
    minRetryCount = DEFAULT_MIN_RETRY_COUNT;

    downloadInternals = new ArrayList<>();
    downloads = new ArrayList<>();
    downloadThreads = new HashMap<>();
    listeners = new CopyOnWriteArraySet<>();
    releaseLock = new Object();

    requirementsListener = this::onRequirementsStateChanged;

    mainHandler = new Handler(Util.getLooper(), this::handleMainMessage);
    internalThread = new HandlerThread("DownloadManager file i/o");
    internalThread.start();
    internalHandler = new Handler(internalThread.getLooper(), this::handleInternalMessage);

    requirementsWatcher =
        new RequirementsWatcher(context, requirementsListener, DEFAULT_REQUIREMENTS);
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

  /**
   * Returns whether the manager is currently idle. The manager is idle if all downloads are in a
   * terminal state (i.e. completed or failed), or if no progress can be made (e.g. because the
   * download requirements are not met).
   */
  public boolean isIdle() {
    return activeDownloadCount == 0 && pendingMessages == 0;
  }

  /**
   * Returns whether this manager has one or more downloads that are not progressing for the sole
   * reason that the {@link #getRequirements() Requirements} are not met.
   */
  public boolean isWaitingForRequirements() {
    // TODO: Fix this to return the right thing.
    return !downloads.isEmpty();
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

  /** Returns the requirements needed to be met to start downloads. */
  public Requirements getRequirements() {
    return requirementsWatcher.getRequirements();
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
   * Sets the maximum number of parallel downloads.
   *
   * @param maxParallelDownloads The maximum number of parallel downloads.
   */
  // TODO: Fix to properly support changes at runtime.
  public void setMaxParallelDownloads(int maxParallelDownloads) {
    this.maxParallelDownloads = maxParallelDownloads;
  }

  /**
   * Sets the minimum number of times that a download will be retried. A download will fail if the
   * specified number of retries is exceeded without any progress being made.
   *
   * @param minRetryCount The minimum number of times that a download will be retried.
   */
  // TODO: Fix to properly support changes at runtime.
  public void setMinRetryCount(int minRetryCount) {
    this.minRetryCount = minRetryCount;
  }

  /** Returns the used {@link DownloadIndex}. */
  public DownloadIndex getDownloadIndex() {
    return downloadIndex;
  }

  /**
   * Returns current downloads. Downloads that are in terminal states (i.e. completed or failed) are
   * not included. To query all downloads including those in terminal states, use {@link
   * #getDownloadIndex()} instead.
   */
  public List<Download> getCurrentDownloads() {
    return Collections.unmodifiableList(new ArrayList<>(downloads));
  }

  /** Resumes all downloads except those that have a non-zero {@link Download#stopReason}. */
  public void resumeDownloads() {
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_SET_DOWNLOADS_RESUMED, /* downloadsResumed */ 1, /* unused */ 0)
        .sendToTarget();
  }

  /** Pauses all downloads. */
  public void pauseDownloads() {
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_SET_DOWNLOADS_RESUMED, /* downloadsResumed */ 0, /* unused */ 0)
        .sendToTarget();
  }

  /**
   * Sets the stop reason for one or all downloads. To clear the stop reason, pass {@link
   * Download#STOP_REASON_NONE}.
   *
   * @param id The content id of the download to update, or {@code null} to set the stop reason for
   *     all downloads.
   * @param stopReason The stop reason, or {@link Download#STOP_REASON_NONE}.
   */
  public void setStopReason(@Nullable String id, int stopReason) {
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_SET_STOP_REASON, stopReason, /* unused */ 0, id)
        .sendToTarget();
  }

  /**
   * Adds a download defined by the given request.
   *
   * @param request The download request.
   */
  public void addDownload(DownloadRequest request) {
    addDownload(request, Download.STOP_REASON_NONE);
  }

  /**
   * Adds a download defined by the given request and with the specified stop reason.
   *
   * @param request The download request.
   * @param stopReason An initial stop reason for the download, or {@link Download#STOP_REASON_NONE}
   *     if the download should be started.
   */
  public void addDownload(DownloadRequest request, int stopReason) {
    pendingMessages++;
    internalHandler
        .obtainMessage(MSG_ADD_DOWNLOAD, stopReason, /* unused */ 0, request)
        .sendToTarget();
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
   * Stops the downloads and releases resources. Waits until the downloads are persisted to the
   * download index. The manager must not be accessed after this method has been called.
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
      downloads.clear();
    }
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
        List<Download> downloads = (List<Download>) message.obj;
        onInitialized(downloads);
        break;
      case MSG_DOWNLOAD_CHANGED:
        Download state = (Download) message.obj;
        onDownloadChanged(state);
        break;
      case MSG_DOWNLOAD_REMOVED:
        state = (Download) message.obj;
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

  private void onInitialized(List<Download> downloads) {
    initialized = true;
    this.downloads.addAll(downloads);
    for (Listener listener : listeners) {
      listener.onInitialized(DownloadManager.this);
    }
  }

  private void onDownloadChanged(Download download) {
    int downloadIndex = getDownloadIndex(download.request.id);
    if (download.isTerminalState()) {
      if (downloadIndex != C.INDEX_UNSET) {
        downloads.remove(downloadIndex);
      }
    } else if (downloadIndex != C.INDEX_UNSET) {
      downloads.set(downloadIndex, download);
    } else {
      downloads.add(download);
    }
    for (Listener listener : listeners) {
      listener.onDownloadChanged(this, download);
    }
  }

  private void onDownloadRemoved(Download download) {
    downloads.remove(getDownloadIndex(download.request.id));
    for (Listener listener : listeners) {
      listener.onDownloadRemoved(this, download);
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

  private int getDownloadIndex(String id) {
    for (int i = 0; i < downloads.size(); i++) {
      if (downloads.get(i).request.id.equals(id)) {
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
      case MSG_SET_DOWNLOADS_RESUMED:
        boolean downloadsResumed = message.arg1 != 0;
        setDownloadsResumed(downloadsResumed);
        break;
      case MSG_SET_NOT_MET_REQUIREMENTS:
        notMetRequirements = message.arg1;
        setNotMetRequirementsInternal(notMetRequirements);
        break;
      case MSG_SET_STOP_REASON:
        String id = (String) message.obj;
        int stopReason = message.arg1;
        setStopReasonInternal(id, stopReason);
        break;
      case MSG_ADD_DOWNLOAD:
        DownloadRequest request = (DownloadRequest) message.obj;
        stopReason = message.arg1;
        addDownloadInternal(request, stopReason);
        break;
      case MSG_REMOVE_DOWNLOAD:
        id = (String) message.obj;
        removeDownloadInternal(id);
        break;
      case MSG_DOWNLOAD_THREAD_STOPPED:
        DownloadThread downloadThread = (DownloadThread) message.obj;
        onDownloadThreadStoppedInternal(downloadThread);
        processedExternalMessage = false; // This message is posted internally.
        break;
      case MSG_CONTENT_LENGTH_CHANGED:
        downloadThread = (DownloadThread) message.obj;
        onDownloadThreadContentLengthChangedInternal(downloadThread);
        processedExternalMessage = false; // This message is posted internally.
        break;
      case MSG_RELEASE:
        releaseInternal();
        return true; // Don't post back to mainHandler on release.
      default:
        throw new IllegalStateException();
    }
    mainHandler
        .obtainMessage(MSG_PROCESSED, processedExternalMessage ? 1 : 0, downloadThreads.size())
        .sendToTarget();
    return true;
  }

  private void initializeInternal(int notMetRequirements) {
    this.notMetRequirements = notMetRequirements;
    ArrayList<Download> loadedStates = new ArrayList<>();
    try (DownloadCursor cursor =
        downloadIndex.getDownloads(
            STATE_QUEUED, STATE_STOPPED, STATE_DOWNLOADING, STATE_REMOVING, STATE_RESTARTING)) {
      while (cursor.moveToNext()) {
        loadedStates.add(cursor.getDownload());
      }
      logd("Downloads are loaded.");
    } catch (Throwable e) {
      Log.e(TAG, "Download state loading failed.", e);
      loadedStates.clear();
    }
    for (Download download : loadedStates) {
      addDownloadForState(download);
    }
    logd("Downloads are created.");
    mainHandler.obtainMessage(MSG_INITIALIZED, loadedStates).sendToTarget();
    for (int i = 0; i < downloadInternals.size(); i++) {
      downloadInternals.get(i).start();
    }
  }

  private void setDownloadsResumed(boolean downloadsResumed) {
    if (this.downloadsResumed == downloadsResumed) {
      return;
    }
    this.downloadsResumed = downloadsResumed;
    for (int i = 0; i < downloadInternals.size(); i++) {
      downloadInternals.get(i).updateStopState();
    }
  }

  private void setNotMetRequirementsInternal(
      @Requirements.RequirementFlags int notMetRequirements) {
    if (this.notMetRequirements == notMetRequirements) {
      return;
    }
    this.notMetRequirements = notMetRequirements;
    logdFlags("Not met requirements are changed", notMetRequirements);
    for (int i = 0; i < downloadInternals.size(); i++) {
      downloadInternals.get(i).updateStopState();
    }
  }

  private void setStopReasonInternal(@Nullable String id, int stopReason) {
    if (id != null) {
      DownloadInternal downloadInternal = getDownload(id);
      if (downloadInternal != null) {
        logd("download stop reason is set to : " + stopReason, downloadInternal);
        downloadInternal.setStopReason(stopReason);
        return;
      }
    } else {
      for (int i = 0; i < downloadInternals.size(); i++) {
        downloadInternals.get(i).setStopReason(stopReason);
      }
    }
    try {
      if (id != null) {
        downloadIndex.setStopReason(id, stopReason);
      } else {
        downloadIndex.setStopReason(stopReason);
      }
    } catch (IOException e) {
      Log.e(TAG, "setStopReason failed", e);
    }
  }

  private void addDownloadInternal(DownloadRequest request, int stopReason) {
    DownloadInternal downloadInternal = getDownload(request.id);
    if (downloadInternal != null) {
      downloadInternal.addRequest(request, stopReason);
      logd("Request is added to existing download", downloadInternal);
    } else {
      Download download = loadDownload(request.id);
      if (download == null) {
        long nowMs = System.currentTimeMillis();
        download =
            new Download(
                request,
                stopReason != Download.STOP_REASON_NONE ? STATE_STOPPED : STATE_QUEUED,
                /* startTimeMs= */ nowMs,
                /* updateTimeMs= */ nowMs,
                /* contentLength= */ C.LENGTH_UNSET,
                stopReason,
                Download.FAILURE_REASON_NONE);
        logd("Download state is created for " + request.id);
      } else {
        download = mergeRequest(download, request, stopReason);
        logd("Download state is loaded for " + request.id);
      }
      addDownloadForState(download);
    }
  }

  private void removeDownloadInternal(String id) {
    DownloadInternal downloadInternal = getDownload(id);
    if (downloadInternal != null) {
      downloadInternal.remove();
    } else {
      Download download = loadDownload(id);
      if (download != null) {
        addDownloadForState(copyWithState(download, STATE_REMOVING));
      } else {
        logd("Can't remove download. No download with id: " + id);
      }
    }
  }

  private void onDownloadThreadStoppedInternal(DownloadThread downloadThread) {
    logd("Download is stopped", downloadThread.request);
    String downloadId = downloadThread.request.id;
    downloadThreads.remove(downloadId);
    boolean tryToStartDownloads = false;
    if (!downloadThread.isRemove) {
      // If maxParallelDownloads was hit, there might be a download waiting for a slot.
      tryToStartDownloads = parallelDownloads == maxParallelDownloads;
      parallelDownloads--;
    }
    getDownload(downloadId)
        .onDownloadThreadStopped(downloadThread.isCanceled, downloadThread.finalError);
    if (tryToStartDownloads) {
      for (int i = 0;
          parallelDownloads < maxParallelDownloads && i < downloadInternals.size();
          i++) {
        downloadInternals.get(i).start();
      }
    }
  }

  private void onDownloadThreadContentLengthChangedInternal(DownloadThread downloadThread) {
    String downloadId = downloadThread.request.id;
    getDownload(downloadId).setContentLength(downloadThread.contentLength);
  }

  private void releaseInternal() {
    for (DownloadThread downloadThread : downloadThreads.values()) {
      downloadThread.cancel(/* released= */ true);
    }
    downloadThreads.clear();
    downloadInternals.clear();
    internalThread.quit();
    synchronized (releaseLock) {
      released = true;
      releaseLock.notifyAll();
    }
  }

  private void onDownloadChangedInternal(DownloadInternal downloadInternal, Download download) {
    logd("Download state is changed", downloadInternal);
    try {
      downloadIndex.putDownload(download);
    } catch (IOException e) {
      Log.e(TAG, "Failed to update index", e);
    }
    if (downloadInternal.state == STATE_COMPLETED || downloadInternal.state == STATE_FAILED) {
      downloadInternals.remove(downloadInternal);
    }
    mainHandler.obtainMessage(MSG_DOWNLOAD_CHANGED, download).sendToTarget();
  }

  private void onDownloadRemovedInternal(DownloadInternal downloadInternal, Download download) {
    logd("Download is removed", downloadInternal);
    try {
      downloadIndex.removeDownload(download.request.id);
    } catch (IOException e) {
      Log.e(TAG, "Failed to remove from index", e);
    }
    downloadInternals.remove(downloadInternal);
    mainHandler.obtainMessage(MSG_DOWNLOAD_REMOVED, download).sendToTarget();
  }

  @StartThreadResults
  private int startDownloadThread(DownloadInternal downloadInternal) {
    DownloadRequest request = downloadInternal.download.request;
    String downloadId = request.id;
    if (downloadThreads.containsKey(downloadId)) {
      if (stopDownloadThreadInternal(downloadId)) {
        return START_THREAD_WAIT_DOWNLOAD_CANCELLATION;
      }
      return START_THREAD_WAIT_REMOVAL_TO_FINISH;
    }
    boolean isRemove = downloadInternal.isInRemoveState();
    if (!isRemove) {
      if (parallelDownloads == maxParallelDownloads) {
        return START_THREAD_TOO_MANY_DOWNLOADS;
      }
      parallelDownloads++;
    }
    Downloader downloader = downloaderFactory.createDownloader(request);
    DownloadProgress downloadProgress = downloadInternal.download.progress;
    DownloadThread downloadThread =
        new DownloadThread(
            request, downloader, downloadProgress, isRemove, minRetryCount, internalHandler);
    downloadThreads.put(downloadId, downloadThread);
    downloadThread.start();
    logd("Download is started", downloadInternal);
    return START_THREAD_SUCCEEDED;
  }

  private boolean stopDownloadThreadInternal(String downloadId) {
    DownloadThread downloadThread = downloadThreads.get(downloadId);
    if (downloadThread != null && !downloadThread.isRemove) {
      downloadThread.cancel(/* released= */ false);
      logd("Download is cancelled", downloadThread.request);
      return true;
    }
    return false;
  }

  @Nullable
  private DownloadInternal getDownload(String id) {
    for (int i = 0; i < downloadInternals.size(); i++) {
      DownloadInternal downloadInternal = downloadInternals.get(i);
      if (downloadInternal.download.request.id.equals(id)) {
        return downloadInternal;
      }
    }
    return null;
  }

  private Download loadDownload(String id) {
    try {
      return downloadIndex.getDownload(id);
    } catch (IOException e) {
      Log.e(TAG, "loadDownload failed", e);
    }
    return null;
  }

  private void addDownloadForState(Download download) {
    DownloadInternal downloadInternal = new DownloadInternal(this, download);
    downloadInternals.add(downloadInternal);
    logd("Download is added", downloadInternal);
    downloadInternal.initialize();
  }

  private boolean canStartDownloads() {
    return downloadsResumed && notMetRequirements == 0;
  }

  /* package */ static Download mergeRequest(
      Download download, DownloadRequest request, int stopReason) {
    @Download.State int state = download.state;
    if (state == STATE_REMOVING || state == STATE_RESTARTING) {
      state = STATE_RESTARTING;
    } else if (stopReason != STOP_REASON_NONE) {
      state = STATE_STOPPED;
    } else {
      state = STATE_QUEUED;
    }
    long nowMs = System.currentTimeMillis();
    long startTimeMs = download.isTerminalState() ? nowMs : download.startTimeMs;
    return new Download(
        download.request.copyWithMergedRequest(request),
        state,
        startTimeMs,
        /* updateTimeMs= */ nowMs,
        /* contentLength= */ C.LENGTH_UNSET,
        stopReason,
        FAILURE_REASON_NONE);
  }

  private static Download copyWithState(Download download, @Download.State int state) {
    return new Download(
        download.request,
        state,
        download.startTimeMs,
        /* updateTimeMs= */ System.currentTimeMillis(),
        download.contentLength,
        download.stopReason,
        FAILURE_REASON_NONE,
        download.progress);
  }

  private static void logd(String message) {
    if (DEBUG) {
      Log.d(TAG, message);
    }
  }

  private static void logd(String message, DownloadInternal downloadInternal) {
    logd(message, downloadInternal.download.request);
  }

  private static void logd(String message, DownloadRequest request) {
    if (DEBUG) {
      logd(message + ": " + request);
    }
  }

  private static void logdFlags(String message, int flags) {
    if (DEBUG) {
      logd(message + ": " + Integer.toBinaryString(flags));
    }
  }

  private static final class DownloadInternal {

    private final DownloadManager downloadManager;

    private Download download;

    // TODO: Get rid of these and use download directly.
    @Download.State private int state;
    private long contentLength;
    private int stopReason;
    @MonotonicNonNull @Download.FailureReason private int failureReason;

    private DownloadInternal(DownloadManager downloadManager, Download download) {
      this.downloadManager = downloadManager;
      this.download = download;
      state = download.state;
      contentLength = download.contentLength;
      stopReason = download.stopReason;
      failureReason = download.failureReason;
    }

    private void initialize() {
      initialize(download.state);
    }

    public void addRequest(DownloadRequest newRequest, int stopReason) {
      download = mergeRequest(download, newRequest, stopReason);
      initialize();
    }

    public void remove() {
      initialize(STATE_REMOVING);
    }

    public Download getUpdatedDownload() {
      download =
          new Download(
              download.request,
              state,
              download.startTimeMs,
              /* updateTimeMs= */ System.currentTimeMillis(),
              contentLength,
              stopReason,
              state != STATE_FAILED ? FAILURE_REASON_NONE : failureReason,
              download.progress);
      return download;
    }

    public boolean isIdle() {
      return state != STATE_DOWNLOADING && state != STATE_REMOVING && state != STATE_RESTARTING;
    }

    @Override
    public String toString() {
      return download.request.id + ' ' + Download.getStateString(state);
    }

    public void start() {
      if (state == STATE_QUEUED || state == STATE_DOWNLOADING) {
        startOrQueue();
      } else if (isInRemoveState()) {
        downloadManager.startDownloadThread(this);
      }
    }

    public void setStopReason(int stopReason) {
      this.stopReason = stopReason;
      updateStopState();
    }

    public boolean isInRemoveState() {
      return state == STATE_REMOVING || state == STATE_RESTARTING;
    }

    public void setContentLength(long contentLength) {
      if (this.contentLength == contentLength) {
        return;
      }
      this.contentLength = contentLength;
      downloadManager.onDownloadChangedInternal(this, getUpdatedDownload());
    }

    private void updateStopState() {
      Download oldDownload = download;
      if (canStart()) {
        if (state == STATE_STOPPED) {
          startOrQueue();
        }
      } else {
        if (state == STATE_DOWNLOADING || state == STATE_QUEUED) {
          downloadManager.stopDownloadThreadInternal(download.request.id);
          setState(STATE_STOPPED);
        }
      }
      if (oldDownload == download) {
        downloadManager.onDownloadChangedInternal(this, getUpdatedDownload());
      }
    }

    private void initialize(int initialState) {
      // Don't notify listeners with initial state until we make sure we don't switch to another
      // state immediately.
      state = initialState;
      if (isInRemoveState()) {
        downloadManager.startDownloadThread(this);
      } else if (canStart()) {
        startOrQueue();
      } else {
        setState(STATE_STOPPED);
      }
      if (state == initialState) {
        downloadManager.onDownloadChangedInternal(this, getUpdatedDownload());
      }
    }

    private boolean canStart() {
      return downloadManager.canStartDownloads() && stopReason == STOP_REASON_NONE;
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

    private void setState(@Download.State int newState) {
      if (state != newState) {
        state = newState;
        downloadManager.onDownloadChangedInternal(this, getUpdatedDownload());
      }
    }

    private void onDownloadThreadStopped(boolean isCanceled, @Nullable Throwable error) {
      if (isIdle()) {
        return;
      }
      if (isCanceled) {
        downloadManager.startDownloadThread(this);
      } else if (state == STATE_REMOVING) {
        downloadManager.onDownloadRemovedInternal(this, getUpdatedDownload());
      } else if (state == STATE_RESTARTING) {
        initialize(STATE_QUEUED);
      } else { // STATE_DOWNLOADING
        if (error != null) {
          Log.e(TAG, "Download failed: " + download.request.id, error);
          failureReason = FAILURE_REASON_UNKNOWN;
          setState(STATE_FAILED);
        } else {
          setState(STATE_COMPLETED);
        }
      }
    }
  }

  private static class DownloadThread extends Thread implements Downloader.ProgressListener {

    private final DownloadRequest request;
    private final Downloader downloader;
    private final DownloadProgress downloadProgress;
    private final boolean isRemove;
    private final int minRetryCount;

    private volatile Handler updateHandler;
    private volatile boolean isCanceled;
    private Throwable finalError;

    private long contentLength;

    private DownloadThread(
        DownloadRequest request,
        Downloader downloader,
        DownloadProgress downloadProgress,
        boolean isRemove,
        int minRetryCount,
        Handler updateHandler) {
      this.request = request;
      this.downloader = downloader;
      this.downloadProgress = downloadProgress;
      this.isRemove = isRemove;
      this.minRetryCount = minRetryCount;
      this.updateHandler = updateHandler;
      contentLength = C.LENGTH_UNSET;
    }

    public void cancel(boolean released) {
      if (released) {
        // Download threads are GC roots for as long as they're running. The time taken for
        // cancellation to complete depends on the implementation of the downloader being used. We
        // null the handler reference here so that it doesn't prevent garbage collection of the
        // download manager whilst cancellation is ongoing.
        updateHandler = null;
      }
      isCanceled = true;
      downloader.cancel();
      interrupt();
    }

    // Methods running on download thread.

    @Override
    public void run() {
      logd("Download started", request);
      try {
        if (isRemove) {
          downloader.remove();
        } else {
          int errorCount = 0;
          long errorPosition = C.LENGTH_UNSET;
          while (!isCanceled) {
            try {
              downloader.download(/* progressListener= */ this);
              break;
            } catch (IOException e) {
              if (!isCanceled) {
                long bytesDownloaded = downloadProgress.bytesDownloaded;
                if (bytesDownloaded != errorPosition) {
                  logd("Reset error count. bytesDownloaded = " + bytesDownloaded, request);
                  errorPosition = bytesDownloaded;
                  errorCount = 0;
                }
                if (++errorCount > minRetryCount) {
                  throw e;
                }
                logd("Download error. Retry " + errorCount, request);
                Thread.sleep(getRetryDelayMillis(errorCount));
              }
            }
          }
        }
      } catch (Throwable e) {
        finalError = e;
      }
      Handler updateHandler = this.updateHandler;
      if (updateHandler != null) {
        updateHandler.obtainMessage(MSG_DOWNLOAD_THREAD_STOPPED, this).sendToTarget();
      }
    }

    @Override
    public void onProgress(long contentLength, long bytesDownloaded, float percentDownloaded) {
      downloadProgress.bytesDownloaded = bytesDownloaded;
      downloadProgress.percentDownloaded = percentDownloaded;
      if (contentLength != this.contentLength) {
        this.contentLength = contentLength;
        Handler updateHandler = this.updateHandler;
        if (updateHandler != null) {
          updateHandler.obtainMessage(MSG_CONTENT_LENGTH_CHANGED, this).sendToTarget();
        }
      }
    }

    private static int getRetryDelayMillis(int errorCount) {
      return Math.min((errorCount - 1) * 1000, 5000);
    }
  }
}
