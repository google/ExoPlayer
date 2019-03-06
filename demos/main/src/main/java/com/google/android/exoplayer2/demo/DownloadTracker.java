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
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import android.widget.Toast;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.offline.ActionFile;
import com.google.android.exoplayer2.offline.DefaultDownloadIndex;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadIndex;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.DownloadState;
import com.google.android.exoplayer2.offline.DownloadStateCursor;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
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
  private final HashMap<Uri, DownloadState> trackedDownloadStates;
  private final DownloadIndex downloadIndex;
  private final Handler indexHandler;

  @Nullable private StartDownloadDialogHelper startDownloadDialogHelper;

  public DownloadTracker(
      Context context, DataSource.Factory dataSourceFactory, DefaultDownloadIndex downloadIndex) {
    this.context = context.getApplicationContext();
    this.dataSourceFactory = dataSourceFactory;
    this.downloadIndex = downloadIndex;
    listeners = new CopyOnWriteArraySet<>();
    trackedDownloadStates = new HashMap<>();
    HandlerThread indexThread = new HandlerThread("DownloadTracker");
    indexThread.start();
    indexHandler = new Handler(indexThread.getLooper());
    loadTrackedActions();
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public boolean isDownloaded(Uri uri) {
    return trackedDownloadStates.containsKey(uri);
  }

  @SuppressWarnings("unchecked")
  public List<StreamKey> getOfflineStreamKeys(Uri uri) {
    if (!trackedDownloadStates.containsKey(uri)) {
      return Collections.emptyList();
    }
    return Arrays.asList(trackedDownloadStates.get(uri).streamKeys);
  }

  public void toggleDownload(
      FragmentManager fragmentManager,
      String name,
      Uri uri,
      String extension,
      RenderersFactory renderersFactory) {
    if (isDownloaded(uri)) {
      DownloadAction removeAction =
          getDownloadHelper(uri, extension, renderersFactory).getRemoveAction();
      startServiceWithAction(removeAction);
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
  public void onInitialized(DownloadManager downloadManager) {
    // Do nothing.
  }

  @Override
  public void onDownloadStateChanged(DownloadManager downloadManager, DownloadState downloadState) {
    if (downloadState.state == DownloadState.STATE_REMOVED
        || downloadState.state == DownloadState.STATE_FAILED) {
      // A download has been removed, or has failed. Stop tracking it.
      if (trackedDownloadStates.remove(downloadState.uri) != null) {
        handleTrackedDownloadStateChanged(downloadState);
      }
    }
  }

  @Override
  public void onIdle(DownloadManager downloadManager) {
    // Do nothing.
  }

  @Override
  public void onRequirementsStateChanged(
      DownloadManager downloadManager,
      Requirements requirements,
      @Requirements.RequirementFlags int notMetRequirements) {
    // Do nothing.
  }

  // Internal methods

  private void loadTrackedActions() {
    try {
      DownloadStateCursor downloadStates = downloadIndex.getDownloadStates();
      while (downloadStates.moveToNext()) {
        DownloadState downloadState = downloadStates.getDownloadState();
        trackedDownloadStates.put(downloadState.uri, downloadState);
      }
      downloadStates.close();
    } catch (IOException e) {
      Log.w(TAG, "Failed to query download states", e);
    }
  }

  private void handleTrackedDownloadStateChanged(DownloadState downloadState) {
    for (Listener listener : listeners) {
      listener.onDownloadsChanged();
    }
    indexHandler.post(
        () -> {
          try {
            if (downloadState.state == DownloadState.STATE_REMOVED) {
              downloadIndex.removeDownloadState(downloadState.id);
            } else {
              downloadIndex.putDownloadState(downloadState);
            }
          } catch (IOException e) {
            // TODO: This whole method is going away in cr/232854678.
          }
        });
  }

  private void startDownload(DownloadAction action) {
    if (trackedDownloadStates.containsKey(action.uri)) {
      // This content is already being downloaded. Do nothing.
      return;
    }
    DownloadState downloadState = new DownloadState(action);
    trackedDownloadStates.put(downloadState.uri, downloadState);
    handleTrackedDownloadStateChanged(downloadState);
    startServiceWithAction(action);
  }

  private void startServiceWithAction(DownloadAction action) {
    DownloadService.startWithAction(context, DemoDownloadService.class, action, false);
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
        startDownload(downloadAction);
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
      DefaultTrackSelector.ParametersBuilder builder =
          DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS.buildUpon();
      for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
        builder.setRendererDisabled(/* rendererIndex= */ i, /* disabled= */ true);
      }
      for (int i = 0; i < downloadHelper.getPeriodCount(); i++) {
        downloadHelper.clearTrackSelections(/* periodIndex = */ i);
      }
      for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
        if (trackSelectionDialog.getIsDisabled(/* rendererIndex= */ i)) {
          continue;
        }
        builder.setRendererDisabled(/* rendererIndex= */ i, /* disabled= */ false);
        List<SelectionOverride> overrides =
            trackSelectionDialog.getOverrides(/* rendererIndex= */ i);
        if (overrides.isEmpty()) {
          for (int j = 0; j < downloadHelper.getPeriodCount(); j++) {
            downloadHelper.addTrackSelection(/* periodIndex = */ j, builder.build());
          }
        } else {
          TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(/* rendererIndex= */ i);
          for (int overrideIndex = 0; overrideIndex < overrides.size(); overrideIndex++) {
            builder.setSelectionOverride(
                /* rendererIndex= */ i, trackGroupArray, overrides.get(overrideIndex));
            for (int j = 0; j < downloadHelper.getPeriodCount(); j++) {
              downloadHelper.addTrackSelection(/* periodIndex = */ j, builder.build());
            }
          }
          builder.clearSelectionOverrides();
        }
        builder.setRendererDisabled(/* rendererIndex= */ i, /* disabled= */ true);
      }
      DownloadAction downloadAction = downloadHelper.getDownloadAction(Util.getUtf8Bytes(name));
      startDownload(downloadAction);
    }

    // DialogInterface.OnDismissListener implementation.

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
      trackSelectionDialog = null;
      downloadHelper.release();
    }
  }
}
