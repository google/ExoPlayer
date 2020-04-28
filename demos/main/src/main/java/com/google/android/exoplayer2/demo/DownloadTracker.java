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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadCursor;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadIndex;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/** Tracks media that has been downloaded. */
public class DownloadTracker {

  /** Listens for changes in the tracked downloads. */
  public interface Listener {

    /** Called when the tracked downloads changed. */
    void onDownloadsChanged();
  }

  private static final String TAG = "DownloadTracker";

  private final Context context;
  private final DataSource.Factory dataSourceFactory;
  private final CopyOnWriteArraySet<Listener> listeners;
  private final HashMap<Uri, Download> downloads;
  private final DownloadIndex downloadIndex;
  private final DefaultTrackSelector.Parameters trackSelectorParameters;

  @Nullable private StartDownloadDialogHelper startDownloadDialogHelper;

  public DownloadTracker(
      Context context, DataSource.Factory dataSourceFactory, DownloadManager downloadManager) {
    this.context = context.getApplicationContext();
    this.dataSourceFactory = dataSourceFactory;
    listeners = new CopyOnWriteArraySet<>();
    downloads = new HashMap<>();
    downloadIndex = downloadManager.getDownloadIndex();
    trackSelectorParameters = DownloadHelper.getDefaultTrackSelectorParameters(context);
    downloadManager.addListener(new DownloadManagerListener());
    loadDownloads();
  }

  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public boolean isDownloaded(MediaItem mediaItem) {
    Download download = downloads.get(checkNotNull(mediaItem.playbackProperties).sourceUri);
    return download != null && download.state != Download.STATE_FAILED;
  }

  public DownloadRequest getDownloadRequest(Uri uri) {
    Download download = downloads.get(uri);
    return download != null && download.state != Download.STATE_FAILED ? download.request : null;
  }

  public void toggleDownload(
      FragmentManager fragmentManager, MediaItem mediaItem, RenderersFactory renderersFactory) {
    Download download = downloads.get(checkNotNull(mediaItem.playbackProperties).sourceUri);
    if (download != null) {
      DownloadService.sendRemoveDownload(
          context, DemoDownloadService.class, download.request.id, /* foreground= */ false);
    } else {
      if (startDownloadDialogHelper != null) {
        startDownloadDialogHelper.release();
      }
      startDownloadDialogHelper =
          new StartDownloadDialogHelper(
              fragmentManager, getDownloadHelper(mediaItem, renderersFactory), mediaItem);
    }
  }

  private void loadDownloads() {
    try (DownloadCursor loadedDownloads = downloadIndex.getDownloads()) {
      while (loadedDownloads.moveToNext()) {
        Download download = loadedDownloads.getDownload();
        downloads.put(download.request.uri, download);
      }
    } catch (IOException e) {
      Log.w(TAG, "Failed to query downloads", e);
    }
  }

  private DownloadHelper getDownloadHelper(MediaItem mediaItem, RenderersFactory renderersFactory) {
    MediaItem.PlaybackProperties playbackProperties = checkNotNull(mediaItem.playbackProperties);
    @C.ContentType
    int type =
        Util.inferContentTypeWithMimeType(
            playbackProperties.sourceUri, playbackProperties.mimeType);
    switch (type) {
      case C.TYPE_DASH:
        return DownloadHelper.forDash(
            context, playbackProperties.sourceUri, dataSourceFactory, renderersFactory);
      case C.TYPE_SS:
        return DownloadHelper.forSmoothStreaming(
            context, playbackProperties.sourceUri, dataSourceFactory, renderersFactory);
      case C.TYPE_HLS:
        return DownloadHelper.forHls(
            context, playbackProperties.sourceUri, dataSourceFactory, renderersFactory);
      case C.TYPE_OTHER:
        return DownloadHelper.forProgressive(context, playbackProperties.sourceUri);
      default:
        throw new IllegalStateException("Unsupported type: " + type);
    }
  }

  private class DownloadManagerListener implements DownloadManager.Listener {

    @Override
    public void onDownloadChanged(
        @NonNull DownloadManager downloadManager, @NonNull Download download) {
      downloads.put(download.request.uri, download);
      for (Listener listener : listeners) {
        listener.onDownloadsChanged();
      }
    }

    @Override
    public void onDownloadRemoved(
        @NonNull DownloadManager downloadManager, @NonNull Download download) {
      downloads.remove(download.request.uri);
      for (Listener listener : listeners) {
        listener.onDownloadsChanged();
      }
    }
  }

  private final class StartDownloadDialogHelper
      implements DownloadHelper.Callback,
          DialogInterface.OnClickListener,
          DialogInterface.OnDismissListener {

    private final FragmentManager fragmentManager;
    private final DownloadHelper downloadHelper;
    private final MediaItem mediaItem;

    private TrackSelectionDialog trackSelectionDialog;
    private MappedTrackInfo mappedTrackInfo;

    public StartDownloadDialogHelper(
        FragmentManager fragmentManager, DownloadHelper downloadHelper, MediaItem mediaItem) {
      this.fragmentManager = fragmentManager;
      this.downloadHelper = downloadHelper;
      this.mediaItem = mediaItem;
      downloadHelper.prepare(this);
    }

    public void release() {
      downloadHelper.release();
      if (trackSelectionDialog != null) {
        trackSelectionDialog.dismiss();
      }
    }

    // DownloadHelper.Callback implementation.

    @Override
    public void onPrepared(@NonNull DownloadHelper helper) {
      if (helper.getPeriodCount() == 0) {
        Log.d(TAG, "No periods found. Downloading entire stream.");
        startDownload();
        downloadHelper.release();
        return;
      }
      mappedTrackInfo = downloadHelper.getMappedTrackInfo(/* periodIndex= */ 0);
      if (!TrackSelectionDialog.willHaveContent(mappedTrackInfo)) {
        Log.d(TAG, "No dialog content. Downloading entire stream.");
        startDownload();
        downloadHelper.release();
        return;
      }
      trackSelectionDialog =
          TrackSelectionDialog.createForMappedTrackInfoAndParameters(
              /* titleId= */ R.string.exo_download_description,
              mappedTrackInfo,
              trackSelectorParameters,
              /* allowAdaptiveSelections =*/ false,
              /* allowMultipleOverrides= */ true,
              /* onClickListener= */ this,
              /* onDismissListener= */ this);
      trackSelectionDialog.show(fragmentManager, /* tag= */ null);
    }

    @Override
    public void onPrepareError(@NonNull DownloadHelper helper, @NonNull IOException e) {
      Toast.makeText(context, R.string.download_start_error, Toast.LENGTH_LONG).show();
      Log.e(
          TAG,
          e instanceof DownloadHelper.LiveContentUnsupportedException
              ? "Downloading live content unsupported"
              : "Failed to start download",
          e);
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
                trackSelectorParameters,
                trackSelectionDialog.getOverrides(/* rendererIndex= */ i));
          }
        }
      }
      DownloadRequest downloadRequest = buildDownloadRequest();
      if (downloadRequest.streamKeys.isEmpty()) {
        // All tracks were deselected in the dialog. Don't start the download.
        return;
      }
      startDownload(downloadRequest);
    }

    // DialogInterface.OnDismissListener implementation.

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
      trackSelectionDialog = null;
      downloadHelper.release();
    }

    // Internal methods.

    private void startDownload() {
      startDownload(buildDownloadRequest());
    }

    private void startDownload(DownloadRequest downloadRequest) {
      DownloadService.sendAddDownload(
          context, DemoDownloadService.class, downloadRequest, /* foreground= */ false);
    }

    private DownloadRequest buildDownloadRequest() {
      return downloadHelper.getDownloadRequest(
          Util.getUtf8Bytes(checkNotNull(mediaItem.mediaMetadata.title)));
    }
  }
}
