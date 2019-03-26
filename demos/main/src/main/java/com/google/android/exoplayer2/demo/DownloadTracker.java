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
package com.google.android.exoplayer2.demo;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import android.widget.Toast;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.offline.ActionFile;
import com.google.android.exoplayer2.offline.DefaultDownloadIndex;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.DownloadState;
import com.google.android.exoplayer2.offline.DownloadStateCursor;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Tracks media that has been downloaded.
 *
 * <p>Tracked downloads are persisted using an {@link ActionFile}, however in a real application
 * it's expected that state will be stored directly in the application's media database, so that it
 * can be queried efficiently together with other information about the media.
 */
public class DownloadTracker implements DownloadManager.Listener {

  /** Listens for changes in the tracked downloads. */
  public interface Listener {

    /** Called when the tracked downloads changed. */
    void onDownloadsChanged();
  }

  private static final String TAG = "DownloadTracker";

  private final Context context;
  private final DataSource.Factory dataSourceFactory;
  private final CopyOnWriteArraySet<Listener> listeners;
  private final HashMap<Uri, DownloadState> downloadStates;
  private final DefaultDownloadIndex downloadIndex;

  @Nullable private StartDownloadDialogHelper startDownloadDialogHelper;

  public DownloadTracker(
      Context context, DataSource.Factory dataSourceFactory, DefaultDownloadIndex downloadIndex) {
    this.context = context.getApplicationContext();
    this.dataSourceFactory = dataSourceFactory;
    this.downloadIndex = downloadIndex;
    listeners = new CopyOnWriteArraySet<>();
    downloadStates = new HashMap<>();
    loadDownloads();
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public boolean isDownloaded(Uri uri) {
    DownloadState downloadState = downloadStates.get(uri);
    return downloadState != null && downloadState.state != DownloadState.STATE_FAILED;
  }

  @SuppressWarnings("unchecked")
  public List<StreamKey> getOfflineStreamKeys(Uri uri) {
    DownloadState downloadState = downloadStates.get(uri);
    return downloadState != null && downloadState.state != DownloadState.STATE_FAILED
        ? Arrays.asList(downloadState.streamKeys)
        : Collections.emptyList();
  }

  public void toggleDownload(
      FragmentManager fragmentManager,
      String name,
      Uri uri,
      String extension,
      RenderersFactory renderersFactory) {
    DownloadState downloadState = downloadStates.get(uri);
    if (downloadState != null) {
      DownloadService.startWithRemoveDownload(
          context, DemoDownloadService.class, downloadState.id, /* foreground= */ false);
    } else {
      if (startDownloadDialogHelper != null) {
        startDownloadDialogHelper.release();
      }
      startDownloadDialogHelper =
          new StartDownloadDialogHelper(
              fragmentManager, getDownloadHelper(uri, extension, renderersFactory), name);
    }
  }

  // DownloadManager.Listener

  @Override
  public void onDownloadStateChanged(DownloadManager downloadManager, DownloadState downloadState) {
    boolean downloaded = isDownloaded(downloadState.uri);
    if (downloadState.state == DownloadState.STATE_REMOVED) {
      downloadStates.remove(downloadState.uri);
    } else {
      downloadStates.put(downloadState.uri, downloadState);
    }
    if (downloaded != isDownloaded(downloadState.uri)) {
      for (Listener listener : listeners) {
        listener.onDownloadsChanged();
      }
    }
  }

  // Internal methods

  private void loadDownloads() {
    try {
      DownloadStateCursor loadedDownloadStates = downloadIndex.getDownloadStates();
      while (loadedDownloadStates.moveToNext()) {
        DownloadState downloadState = loadedDownloadStates.getDownloadState();
        downloadStates.put(downloadState.uri, downloadState);
      }
      loadedDownloadStates.close();
    } catch (IOException e) {
      Log.w(TAG, "Failed to query download states", e);
    }
  }

  private void startServiceWithAction(DownloadAction action) {
    DownloadService.startWithAction(
        context, DemoDownloadService.class, action, /* foreground= */ false);
  }

  private DownloadHelper getDownloadHelper(
      Uri uri, String extension, RenderersFactory renderersFactory) {
    int type = Util.inferContentType(uri, extension);
    switch (type) {
      case C.TYPE_DASH:
        return DownloadHelper.forDash(uri, dataSourceFactory, renderersFactory);
      case C.TYPE_SS:
        return DownloadHelper.forSmoothStreaming(uri, dataSourceFactory, renderersFactory);
      case C.TYPE_HLS:
        return DownloadHelper.forHls(uri, dataSourceFactory, renderersFactory);
      case C.TYPE_OTHER:
        return DownloadHelper.forProgressive(uri);
      default:
        throw new IllegalStateException("Unsupported type: " + type);
    }
  }

  private final class StartDownloadDialogHelper
      implements DownloadHelper.Callback,
          DialogInterface.OnClickListener,
          DialogInterface.OnDismissListener {

    private final FragmentManager fragmentManager;
    private final DownloadHelper downloadHelper;
    private final String name;

    private TrackSelectionDialog trackSelectionDialog;
    private MappedTrackInfo mappedTrackInfo;

    public StartDownloadDialogHelper(
        FragmentManager fragmentManager, DownloadHelper downloadHelper, String name) {
      this.fragmentManager = fragmentManager;
      this.downloadHelper = downloadHelper;
      this.name = name;
      downloadHelper.prepare(this);
    }

    public void release() {
      downloadHelper.release();
      if (trackSelectionDialog != null) {
        trackSelectionDialog.dismiss();
      }
      startDownloadDialogHelper = null;
    }

    // DownloadHelper.Callback implementation.

    @Override
    public void onPrepared(DownloadHelper helper) {
      if (helper.getPeriodCount() == 0) {
        Log.d(TAG, "No periods found. Downloading entire stream.");
        DownloadAction downloadAction = downloadHelper.getDownloadAction(Util.getUtf8Bytes(name));
        startServiceWithAction(downloadAction);
        downloadHelper.release();
        return;
      }
      mappedTrackInfo = downloadHelper.getMappedTrackInfo(/* periodIndex= */ 0);
      trackSelectionDialog = new TrackSelectionDialog();
      trackSelectionDialog.init(
          /* titleId= */ R.string.exo_download_description,
          mappedTrackInfo,
          /* initialSelection= */ DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS,
          /* allowAdaptiveSelections =*/ false,
          /* allowMultipleOverrides= */ true,
          /* onClickListener= */ this,
          /* onDismissListener= */ this);
      trackSelectionDialog.show(fragmentManager, /* tag= */ null);
    }

    @Override
    public void onPrepareError(DownloadHelper helper, IOException e) {
      Toast.makeText(
              context.getApplicationContext(), R.string.download_start_error, Toast.LENGTH_LONG)
          .show();
      Log.e(TAG, "Failed to start download", e);
    }

    // DialogInterface.OnClickListener implementation.

    @Override
    public void onClick(DialogInterface dialog, int which) {
      for (int periodIndex = 0; periodIndex < downloadHelper.getPeriodCount(); periodIndex++) {
        downloadHelper.clearTrackSelections(periodIndex);
        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
          if (!trackSelectionDialog.getIsDisabled(/* rendererIndex= */ i)) {
            downloadHelper.addTrackSelectionForSingleRenderer(
                periodIndex,
                /* rendererIndex= */ i,
                DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS,
                trackSelectionDialog.getOverrides(/* rendererIndex= */ i));
          }
        }
      }
      DownloadAction downloadAction = downloadHelper.getDownloadAction(Util.getUtf8Bytes(name));
      startServiceWithAction(downloadAction);
    }

    // DialogInterface.OnDismissListener implementation.

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
      trackSelectionDialog = null;
      downloadHelper.release();
    }
  }
}
