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

import static com.google.android.exoplayer2.demo.PlayerActivity.DRM_SCHEME_EXTRA;
import static com.google.android.exoplayer2.demo.PlayerActivity.EXTENSION_EXTRA;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.ProgressiveDownloadAction;
import com.google.android.exoplayer2.offline.ProgressiveDownloader;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.RepresentationKey;
import com.google.android.exoplayer2.source.dash.offline.DashDownloadAction;
import com.google.android.exoplayer2.source.dash.offline.DashDownloader;
import com.google.android.exoplayer2.source.hls.offline.HlsDownloadAction;
import com.google.android.exoplayer2.source.hls.offline.HlsDownloader;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.TrackKey;
import com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloadAction;
import com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloader;
import com.google.android.exoplayer2.util.ParcelableArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** An activity that downloads streams. */
public class DownloaderActivity extends Activity {

  public static final String PLAYER_INTENT = "player_intent";
  public static final String SAMPLE_NAME = "stream_name";

  private static final String TAG = "DownloaderActivity";

  private Intent playerIntent;

  @SuppressWarnings("rawtypes")
  private AsyncTask manifestDownloaderTask;

  private DownloadUtilMethods downloadUtilMethods;

  private ListView representationList;
  private ArrayAdapter<RepresentationItem> arrayAdapter;
  private AlertDialog cancelDialog;
  private String sampleName;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.downloader_activity);

    Intent intent = getIntent();
    playerIntent = intent.getParcelableExtra(PLAYER_INTENT);

    TextView streamName = findViewById(R.id.sample_name);
    sampleName = intent.getStringExtra(SAMPLE_NAME);
    streamName.setText(sampleName);

    arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice);
    representationList = findViewById(R.id.representation_list);
    representationList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    representationList.setAdapter(arrayAdapter);

    if (playerIntent.hasExtra(DRM_SCHEME_EXTRA)) {
      showToastAndFinish(R.string.not_supported_content_type);
    }

    Uri sampleUri = playerIntent.getData();
    DemoApplication application = (DemoApplication) getApplication();
    DownloaderConstructorHelper constructorHelper =
        new DownloaderConstructorHelper(
            application.getDownloadCache(), application.buildHttpDataSourceFactory(null));
    String extension = playerIntent.getStringExtra(EXTENSION_EXTRA);
    int type = Util.inferContentType(sampleUri, extension);
    switch (type) {
      case C.TYPE_DASH:
        downloadUtilMethods = new DashDownloadUtilMethods(sampleUri, constructorHelper);
        break;
      case C.TYPE_HLS:
        downloadUtilMethods = new HlsDownloadUtilMethods(sampleUri, constructorHelper);
        break;
      case C.TYPE_SS:
        downloadUtilMethods = new SsDownloadUtilMethods(sampleUri, constructorHelper);
        break;
      case C.TYPE_OTHER:
        downloadUtilMethods = new ProgressiveDownloadUtilMethods(sampleUri, constructorHelper);
        break;
      default:
        showToastAndFinish(R.string.not_supported_content_type);
        break;
    }

    updateRepresentationsList();
  }

  @Override
  protected void onPause() {
    stopAll();
    super.onPause();
  }

  @Override
  protected void onResume() {
    super.onResume();
    updateRepresentationsList();
  }

  // This method is referenced in the layout file
  public void onClick(View v) {
    // switch-case doesn't work as in some compile configurations id definitions aren't constant
    int id = v.getId();
    if (id == R.id.download_button) {
      startDownload();
    } else if (id == R.id.remove_all_button) {
      removeDownloaded();
    } else if (id == R.id.refresh) {
      updateRepresentationsList();
    } else if (id == R.id.play_button) {
      playDownloaded();
    }
  }

  private void startDownload() {
    ArrayList<Object> representationKeys = getSelectedRepresentationKeys(true);
    if (representationKeys == null) {
      return;
    }
    DownloadService.addDownloadAction(
        this,
        DemoDownloadService.class,
        downloadUtilMethods.getDownloadAction(sampleName, representationKeys));
  }

  private void removeDownloaded() {
    DownloadService.addDownloadAction(
        this, DemoDownloadService.class, downloadUtilMethods.getRemoveAction());
    showToastAndFinish(R.string.removing_all);
  }

  @SuppressWarnings("SuspiciousToArrayCall")
  private void playDownloaded() {
    ArrayList<Object> selectedRepresentationKeys = getSelectedRepresentationKeys(false);
    if (selectedRepresentationKeys.isEmpty()) {
      playerIntent.removeExtra(PlayerActivity.MANIFEST_FILTER_EXTRA);
    } else if (selectedRepresentationKeys.get(0) instanceof Parcelable) {
      Parcelable[] parcelables = new Parcelable[selectedRepresentationKeys.size()];
      selectedRepresentationKeys.toArray(parcelables);
      playerIntent.putExtra(
          PlayerActivity.MANIFEST_FILTER_EXTRA, new ParcelableArray<>(parcelables));
    } else {
      String[] strings = new String[selectedRepresentationKeys.size()];
      selectedRepresentationKeys.toArray(strings);
      playerIntent.putExtra(PlayerActivity.MANIFEST_FILTER_EXTRA, strings);
    }
    startActivity(playerIntent);
  }

  private void stopAll() {
    if (cancelDialog != null) {
      cancelDialog.dismiss();
    }

    if (manifestDownloaderTask != null) {
      manifestDownloaderTask.cancel(true);
      manifestDownloaderTask = null;
    }
  }

  private void updateRepresentationsList() {
    if (cancelDialog == null) {
      cancelDialog =
          new AlertDialog.Builder(this)
              .setMessage("Please wait")
              .setOnCancelListener(
                  new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                      stopAll();
                    }
                  })
              .create();
    }
    cancelDialog.setTitle("Updating representations");
    cancelDialog.show();
    manifestDownloaderTask = new ManifestDownloaderTask().execute();
  }

  private ArrayList<Object> getSelectedRepresentationKeys(boolean unselect) {
    SparseBooleanArray checked = representationList.getCheckedItemPositions();
    ArrayList<Object> representations = new ArrayList<>(checked.size());
    for (int i = 0; i < checked.size(); i++) {
      if (checked.valueAt(i)) {
        int position = checked.keyAt(i);
        RepresentationItem item =
            (RepresentationItem) representationList.getItemAtPosition(position);
        representations.add(item.key);
        if (unselect) {
          representationList.setItemChecked(position, false);
        }
      }
    }
    return representations;
  }

  private void showToastAndFinish(int resId) {
    showToast(resId);
    finish();
  }

  private void showToast(int resId) {
    Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_LONG).show();
  }

  private static final class RepresentationItem {
    public final Object key;
    public final String title;

    public RepresentationItem(Object key, String title) {
      this.key = key;
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  private final class ManifestDownloaderTask
      extends AsyncTask<Void, Void, List<RepresentationItem>> {

    @Override
    protected List<RepresentationItem> doInBackground(Void... ignore) {
      List<RepresentationItem> items;
      try {
        items = downloadUtilMethods.getRepresentationItems();
      } catch (IOException | InterruptedException e) {
        Log.e(TAG, "Getting representations failed", e);
        return null;
      }
      return items;
    }

    @Override
    protected void onPostExecute(List<RepresentationItem> items) {
      if (items == null) {
        showToastAndFinish(R.string.manifest_download_error);
        return;
      }
      arrayAdapter.clear();
      for (RepresentationItem representationItem : items) {
        arrayAdapter.add(representationItem);
      }
      stopAll();
    }
  }

  private abstract static class DownloadUtilMethods {

    protected final Uri manifestUri;
    protected final DownloaderConstructorHelper constructorHelper;

    public DownloadUtilMethods(Uri manifestUri, DownloaderConstructorHelper constructorHelper) {
      this.manifestUri = manifestUri;
      this.constructorHelper = constructorHelper;
    }

    public abstract List<RepresentationItem> getRepresentationItems()
        throws IOException, InterruptedException;

    public abstract DownloadAction getDownloadAction(
        String sampleName, ArrayList<Object> representationKeys);

    public abstract DownloadAction getRemoveAction();
  }

  private static final class DashDownloadUtilMethods extends DownloadUtilMethods {

    public DashDownloadUtilMethods(Uri manifestUri, DownloaderConstructorHelper constructorHelper) {
      super(manifestUri, constructorHelper);
    }

    @Override
    public List<RepresentationItem> getRepresentationItems()
        throws IOException, InterruptedException {
      DashDownloader downloader = new DashDownloader(manifestUri, constructorHelper);
      ArrayList<RepresentationItem> items = new ArrayList<>();
      for (RepresentationKey key : downloader.getAllRepresentationKeys()) {
        downloader.selectRepresentations(new RepresentationKey[] {key});
        try {
          downloader.init();
        } catch (IOException e) {
          continue;
        }
        Representation representation =
            downloader
                .getManifest()
                .getPeriod(key.periodIndex)
                .adaptationSets
                .get(key.adaptationSetIndex)
                .representations
                .get(key.representationIndex);
        String trackName = DemoUtil.buildTrackName(representation.format);
        int totalSegments = downloader.getTotalSegments();
        int downloadedRatio = 0;
        if (totalSegments != 0) {
          downloadedRatio = (downloader.getDownloadedSegments() * 100) / totalSegments;
        }
        String name = key.toString() + ' ' + trackName + ' ' + downloadedRatio + '%';
        items.add(new RepresentationItem(key, name));
      }
      return items;
    }

    @Override
    public DownloadAction getDownloadAction(
        String sampleName, ArrayList<Object> representationKeys) {
      StringBuilder sb = new StringBuilder(sampleName);
      RepresentationKey[] keys =
          representationKeys.toArray(new RepresentationKey[representationKeys.size()]);
      for (RepresentationKey representationKey : keys) {
        sb.append('-').append(representationKey);
      }
      return new DashDownloadAction(manifestUri, false, sb.toString(), keys);
    }

    @Override
    public DownloadAction getRemoveAction() {
      return new DashDownloadAction(manifestUri, true, null);
    }
  }

  private static final class HlsDownloadUtilMethods extends DownloadUtilMethods {

    public HlsDownloadUtilMethods(Uri manifestUri, DownloaderConstructorHelper constructorHelper) {
      super(manifestUri, constructorHelper);
    }

    @Override
    public List<RepresentationItem> getRepresentationItems()
        throws IOException, InterruptedException {
      HlsDownloader downloader = new HlsDownloader(manifestUri, constructorHelper);
      ArrayList<RepresentationItem> items = new ArrayList<>();
      for (String key : downloader.getAllRepresentationKeys()) {
        downloader.selectRepresentations(new String[] {key});
        try {
          downloader.init();
        } catch (IOException e) {
          continue;
        }
        int totalSegments = downloader.getTotalSegments();
        int downloadedRatio = 0;
        if (totalSegments != 0) {
          downloadedRatio = (downloader.getDownloadedSegments() * 100) / totalSegments;
        }
        String name = key + ' ' /*+ trackName + ' '*/ + downloadedRatio + '%';
        items.add(new RepresentationItem(key, name));
      }
      return items;
    }

    @Override
    public DownloadAction getDownloadAction(
        String sampleName, ArrayList<Object> representationKeys) {
      StringBuilder sb = new StringBuilder(sampleName);
      String[] keys = representationKeys.toArray(new String[representationKeys.size()]);
      for (String key : keys) {
        sb.append('-').append(key);
      }
      return new HlsDownloadAction(manifestUri, false, sb.toString(), keys);
    }

    @Override
    public DownloadAction getRemoveAction() {
      return new HlsDownloadAction(manifestUri, true, null);
    }
  }

  private static final class SsDownloadUtilMethods extends DownloadUtilMethods {

    public SsDownloadUtilMethods(Uri manifestUri, DownloaderConstructorHelper constructorHelper) {
      super(manifestUri, constructorHelper);
    }

    @Override
    public List<RepresentationItem> getRepresentationItems()
        throws IOException, InterruptedException {
      SsDownloader downloader = new SsDownloader(manifestUri, constructorHelper);
      ArrayList<RepresentationItem> items = new ArrayList<>();
      for (TrackKey key : downloader.getAllRepresentationKeys()) {
        downloader.selectRepresentations(new TrackKey[] {key});
        try {
          downloader.init();
        } catch (IOException e) {
          continue;
        }
        Format format =
            downloader.getManifest().streamElements[key.streamElementIndex].formats[key.trackIndex];
        String trackName = DemoUtil.buildTrackName(format);
        int totalSegments = downloader.getTotalSegments();
        int downloadedRatio = 0;
        if (totalSegments != 0) {
          downloadedRatio = (downloader.getDownloadedSegments() * 100) / totalSegments;
        }
        String name = key.toString() + ' ' + trackName + ' ' + downloadedRatio + '%';
        items.add(new RepresentationItem(key, name));
      }
      return items;
    }

    @Override
    public DownloadAction getDownloadAction(
        String sampleName, ArrayList<Object> representationKeys) {
      StringBuilder sb = new StringBuilder(sampleName);
      TrackKey[] keys = representationKeys.toArray(new TrackKey[representationKeys.size()]);
      for (TrackKey trackKey : keys) {
        sb.append('-').append(trackKey);
      }
      return new SsDownloadAction(manifestUri, false, sb.toString(), keys);
    }

    @Override
    public DownloadAction getRemoveAction() {
      return new SsDownloadAction(manifestUri, true, null);
    }
  }

  private static final class ProgressiveDownloadUtilMethods extends DownloadUtilMethods {

    public ProgressiveDownloadUtilMethods(
        Uri manifestUri, DownloaderConstructorHelper constructorHelper) {
      super(manifestUri, constructorHelper);
    }

    @Override
    public List<RepresentationItem> getRepresentationItems() {
      ProgressiveDownloader downloader =
          new ProgressiveDownloader(manifestUri.toString(), null, constructorHelper);
      ArrayList<RepresentationItem> items = new ArrayList<>();
      {
        downloader.init();
        int downloadedRatio = (int) downloader.getDownloadPercentage();
        String name = "track 1 - " + downloadedRatio + '%';
        items.add(new RepresentationItem(null, name));
      }
      return items;
    }

    @Override
    public DownloadAction getDownloadAction(
        String sampleName, ArrayList<Object> representationKeys) {
      return new ProgressiveDownloadAction(manifestUri.toString(), null, false, sampleName);
    }

    @Override
    public DownloadAction getRemoveAction() {
      return new ProgressiveDownloadAction(manifestUri.toString(), null, true, null);
    }
  }
}
