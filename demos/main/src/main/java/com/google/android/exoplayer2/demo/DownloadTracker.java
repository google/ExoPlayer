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
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.FragmentManager;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.OfflineLicenseHelper;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadCursor;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadHelper.LiveContentUnsupportedException;
import com.google.android.exoplayer2.offline.DownloadIndex;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.upstream.HttpDataSource;
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
  private final HttpDataSource.Factory httpDataSourceFactory;
  private final CopyOnWriteArraySet<Listener> listeners;
  private final HashMap<Uri, Download> downloads;
  private final DownloadIndex downloadIndex;
  private final DefaultTrackSelector.Parameters trackSelectorParameters;

  @Nullable private StartDownloadDialogHelper startDownloadDialogHelper;

  public DownloadTracker(
      Context context,
      HttpDataSource.Factory httpDataSourceFactory,
      DownloadManager downloadManager) {
    this.context = context.getApplicationContext();
    this.httpDataSourceFactory = httpDataSourceFactory;
    listeners = new CopyOnWriteArraySet<>();
    downloads = new HashMap<>();
    downloadIndex = downloadManager.getDownloadIndex();
    trackSelectorParameters = DownloadHelper.getDefaultTrackSelectorParameters(context);
    downloadManager.addListener(new DownloadManagerListener());
    loadDownloads();
  }

  public void addListener(Listener listener) {
    checkNotNull(listener);
    listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  public boolean isDownloaded(MediaItem mediaItem) {
    @Nullable Download download = downloads.get(checkNotNull(mediaItem.playbackProperties).uri);
    return download != null && download.state != Download.STATE_FAILED;
  }

  @Nullable
  public DownloadRequest getDownloadRequest(Uri uri) {
    @Nullable Download download = downloads.get(uri);
    return download != null && download.state != Download.STATE_FAILED ? download.request : null;
  }

  public void toggleDownload(
      FragmentManager fragmentManager, MediaItem mediaItem, RenderersFactory renderersFactory) {
    @Nullable Download download = downloads.get(checkNotNull(mediaItem.playbackProperties).uri);
    if (download != null && download.state != Download.STATE_FAILED) {
      DownloadService.sendRemoveDownload(
          context, DemoDownloadService.class, download.request.id, /* foreground= */ false);
    } else {
      if (startDownloadDialogHelper != null) {
        startDownloadDialogHelper.release();
      }
      startDownloadDialogHelper =
          new StartDownloadDialogHelper(
              fragmentManager,
              DownloadHelper.forMediaItem(
                  context, mediaItem, renderersFactory, httpDataSourceFactory),
              mediaItem);
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

  private class DownloadManagerListener implements DownloadManager.Listener {

    @Override
    public void onDownloadChanged(
        @NonNull DownloadManager downloadManager,
        @NonNull Download download,
        @Nullable Exception finalException) {
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
    private WidevineOfflineLicenseFetchTask widevineOfflineLicenseFetchTask;
    @Nullable private byte[] keySetId;

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
      if (widevineOfflineLicenseFetchTask != null) {
        widevineOfflineLicenseFetchTask.cancel(false);
      }
    }

    // DownloadHelper.Callback implementation.

    @Override
    public void onPrepared(@NonNull DownloadHelper helper) {
      @Nullable Format format = getFirstFormatWithDrmInitData(helper);
      if (format == null) {
        onDownloadPrepared(helper);
        return;
      }

      // The content is DRM protected. We need to acquire an offline license.
      if (Util.SDK_INT < 18) {
        Toast.makeText(context, R.string.error_drm_unsupported_before_api_18, Toast.LENGTH_LONG)
            .show();
        Log.e(TAG, "Downloading DRM protected content is not supported on API versions below 18");
        return;
      }
      // TODO(internal b/163107948): Support cases where DrmInitData are not in the manifest.
      if (!hasSchemaData(format.drmInitData)) {
        Toast.makeText(context, R.string.download_start_error_offline_license, Toast.LENGTH_LONG)
            .show();
        Log.e(
            TAG,
            "Downloading content where DRM scheme data is not located in the manifest is not"
                + " supported");
        return;
      }
      widevineOfflineLicenseFetchTask =
          new WidevineOfflineLicenseFetchTask(
              format,
              mediaItem.playbackProperties.drmConfiguration,
              httpDataSourceFactory,
              /* dialogHelper= */ this,
              helper);
      widevineOfflineLicenseFetchTask.execute();
    }

    @Override
    public void onPrepareError(@NonNull DownloadHelper helper, @NonNull IOException e) {
      boolean isLiveContent = e instanceof LiveContentUnsupportedException;
      int toastStringId =
          isLiveContent ? R.string.download_live_unsupported : R.string.download_start_error;
      String logMessage =
          isLiveContent ? "Downloading live content unsupported" : "Failed to start download";
      Toast.makeText(context, toastStringId, Toast.LENGTH_LONG).show();
      Log.e(TAG, logMessage, e);
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

    /**
     * Returns the first {@link Format} with a non-null {@link Format#drmInitData} found in the
     * content's tracks, or null if none is found.
     */
    @Nullable
    private Format getFirstFormatWithDrmInitData(DownloadHelper helper) {
      for (int periodIndex = 0; periodIndex < helper.getPeriodCount(); periodIndex++) {
        MappedTrackInfo mappedTrackInfo = helper.getMappedTrackInfo(periodIndex);
        for (int rendererIndex = 0;
            rendererIndex < mappedTrackInfo.getRendererCount();
            rendererIndex++) {
          TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
          for (int trackGroupIndex = 0; trackGroupIndex < trackGroups.length; trackGroupIndex++) {
            TrackGroup trackGroup = trackGroups.get(trackGroupIndex);
            for (int formatIndex = 0; formatIndex < trackGroup.length; formatIndex++) {
              Format format = trackGroup.getFormat(formatIndex);
              if (format.drmInitData != null) {
                return format;
              }
            }
          }
        }
      }
      return null;
    }

    private void onOfflineLicenseFetched(DownloadHelper helper, byte[] keySetId) {
      this.keySetId = keySetId;
      onDownloadPrepared(helper);
    }

    private void onOfflineLicenseFetchedError(DrmSession.DrmSessionException e) {
      Toast.makeText(context, R.string.download_start_error_offline_license, Toast.LENGTH_LONG)
          .show();
      Log.e(TAG, "Failed to fetch offline DRM license", e);
    }

    private void onDownloadPrepared(DownloadHelper helper) {
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
              /* allowAdaptiveSelections= */ false,
              /* allowMultipleOverrides= */ true,
              /* onClickListener= */ this,
              /* onDismissListener= */ this);
      trackSelectionDialog.show(fragmentManager, /* tag= */ null);
    }

    /**
     * Returns whether any the {@link DrmInitData.SchemeData} contained in {@code drmInitData} has
     * non-null {@link DrmInitData.SchemeData#data}.
     */
    private boolean hasSchemaData(DrmInitData drmInitData) {
      for (int i = 0; i < drmInitData.schemeDataCount; i++) {
        if (drmInitData.get(i).hasData()) {
          return true;
        }
      }
      return false;
    }

    private void startDownload() {
      startDownload(buildDownloadRequest());
    }

    private void startDownload(DownloadRequest downloadRequest) {
      DownloadService.sendAddDownload(
          context, DemoDownloadService.class, downloadRequest, /* foreground= */ false);
    }

    private DownloadRequest buildDownloadRequest() {
      return downloadHelper
          .getDownloadRequest(
              Util.getUtf8Bytes(checkNotNull(mediaItem.mediaMetadata.title.toString())))
          .copyWithKeySetId(keySetId);
    }
  }

  /** Downloads a Widevine offline license in a background thread. */
  @RequiresApi(18)
  private static final class WidevineOfflineLicenseFetchTask extends AsyncTask<Void, Void, Void> {

    private final Format format;
    private final MediaItem.DrmConfiguration drmConfiguration;
    private final HttpDataSource.Factory httpDataSourceFactory;
    private final StartDownloadDialogHelper dialogHelper;
    private final DownloadHelper downloadHelper;

    @Nullable private byte[] keySetId;
    @Nullable private DrmSession.DrmSessionException drmSessionException;

    public WidevineOfflineLicenseFetchTask(
        Format format,
        MediaItem.DrmConfiguration drmConfiguration,
        HttpDataSource.Factory httpDataSourceFactory,
        StartDownloadDialogHelper dialogHelper,
        DownloadHelper downloadHelper) {
      this.format = format;
      this.drmConfiguration = drmConfiguration;
      this.httpDataSourceFactory = httpDataSourceFactory;
      this.dialogHelper = dialogHelper;
      this.downloadHelper = downloadHelper;
    }

    @Override
    protected Void doInBackground(Void... voids) {
      OfflineLicenseHelper offlineLicenseHelper =
          OfflineLicenseHelper.newWidevineInstance(
              drmConfiguration.licenseUri.toString(),
              drmConfiguration.forceDefaultLicenseUri,
              httpDataSourceFactory,
              drmConfiguration.requestHeaders,
              new DrmSessionEventListener.EventDispatcher());
      try {
        keySetId = offlineLicenseHelper.downloadLicense(format);
      } catch (DrmSession.DrmSessionException e) {
        drmSessionException = e;
      } finally {
        offlineLicenseHelper.release();
      }
      return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
      if (drmSessionException != null) {
        dialogHelper.onOfflineLicenseFetchedError(drmSessionException);
      } else {
        dialogHelper.onOfflineLicenseFetched(downloadHelper, checkStateNotNull(keySetId));
      }
    }
  }
}
