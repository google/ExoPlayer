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

import static com.google.android.exoplayer2.demo.PlayerActivity.EXTENSION_EXTRA;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
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
import com.google.android.exoplayer2.source.hls.playlist.RenditionKey;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.TrackKey;
import com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloadAction;
import com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloader;
import com.google.android.exoplayer2.util.ParcelableArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** An activity for downloading media. */
public class DownloadActivity extends Activity {

  public static final String PLAYER_INTENT = "player_intent";
  public static final String SAMPLE_NAME = "sample_name";

  private Intent playerIntent;
  private String sampleName;

  @SuppressWarnings("rawtypes")
  private AsyncTask manifestDownloaderTask;

  private DownloadUtilMethods downloadUtilMethods;
  private ListView representationList;
  private ArrayAdapter<RepresentationItem> arrayAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.downloader_activity);

    Intent intent = getIntent();
    playerIntent = intent.getParcelableExtra(PLAYER_INTENT);
    Uri sampleUri = playerIntent.getData();
    sampleName = intent.getStringExtra(SAMPLE_NAME);
    getActionBar().setTitle(sampleName);

    arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice);
    representationList = findViewById(R.id.representation_list);
    representationList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    representationList.setAdapter(arrayAdapter);

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
      case C.TYPE_SS:
        downloadUtilMethods = new SsDownloadUtilMethods(sampleUri, constructorHelper);
        break;
      case C.TYPE_HLS:
        downloadUtilMethods = new HlsDownloadUtilMethods(sampleUri, constructorHelper);
        break;
      case C.TYPE_OTHER:
        downloadUtilMethods = new ProgressiveDownloadUtilMethods(sampleUri, constructorHelper);
        break;
      default:
        throw new IllegalStateException("Unsupported type: " + type);
    }

    updateRepresentationsList();
  }

  @Override
  protected void onStart() {
    super.onStart();
    updateRepresentationsList();
  }

  @Override
  protected void onStop() {
    if (manifestDownloaderTask != null) {
      manifestDownloaderTask.cancel(true);
      manifestDownloaderTask = null;
    }
    super.onStop();
  }

  // This method is referenced in the layout file
  public void onClick(View v) {
    // switch-case doesn't work as in some compile configurations id definitions aren't constant
    int id = v.getId();
    if (id == R.id.download_button) {
      startDownload();
    } else if (id == R.id.remove_all_button) {
      removeDownload();
    } else if (id == R.id.refresh_button) {
      updateRepresentationsList();
    } else if (id == R.id.play_button) {
      playDownload();
    }
  }

  private void startDownload() {
    ArrayList<Object> representationKeys = getSelectedRepresentationKeys();
    if (!representationKeys.isEmpty()) {
      DownloadService.addDownloadAction(
          this,
          DemoDownloadService.class,
          downloadUtilMethods.getDownloadAction(sampleName, representationKeys));
    }
  }

  private void removeDownload() {
    DownloadService.addDownloadAction(
        this, DemoDownloadService.class, downloadUtilMethods.getRemoveAction());
    for (int i = 0; i < representationList.getChildCount(); i++) {
      representationList.setItemChecked(i, false);
    }
  }

  @SuppressWarnings("SuspiciousToArrayCall")
  private void playDownload() {
    ArrayList<Object> selectedRepresentationKeys = getSelectedRepresentationKeys();
    if (selectedRepresentationKeys.isEmpty()) {
      playerIntent.removeExtra(PlayerActivity.MANIFEST_FILTER_EXTRA);
    } else {
      Parcelable[] parcelables = new Parcelable[selectedRepresentationKeys.size()];
      selectedRepresentationKeys.toArray(parcelables);
      playerIntent.putExtra(
          PlayerActivity.MANIFEST_FILTER_EXTRA, new ParcelableArray<>(parcelables));
    }
    startActivity(playerIntent);
  }

  private void updateRepresentationsList() {
    if (manifestDownloaderTask != null) {
      manifestDownloaderTask.cancel(true);
    }
    manifestDownloaderTask = new ManifestDownloaderTask().execute();
  }

  private ArrayList<Object> getSelectedRepresentationKeys() {
    SparseBooleanArray checked = representationList.getCheckedItemPositions();
    ArrayList<Object> representations = new ArrayList<>(checked.size());
    for (int i = 0; i < checked.size(); i++) {
      if (checked.valueAt(i)) {
        int position = checked.keyAt(i);
        RepresentationItem item =
            (RepresentationItem) representationList.getItemAtPosition(position);
        representations.add(item.key);
      }
    }
    return representations;
  }

  private static final class RepresentationItem {

    public final Parcelable key;
    public final String title;
    public final int percentDownloaded;

    public RepresentationItem(Parcelable key, String title, float percentDownloaded) {
      this.key = key;
      this.title = title;
      this.percentDownloaded = (int) percentDownloaded;
    }

    @Override
    public String toString() {
      return title + " (" + percentDownloaded + "%)";
    }
  }

  private final class ManifestDownloaderTask
      extends AsyncTask<Void, Void, List<RepresentationItem>> {

    @Override
    protected List<RepresentationItem> doInBackground(Void... ignore) {
      try {
        return downloadUtilMethods.getRepresentationItems();
      } catch (IOException | InterruptedException e) {
        return null;
      }
    }

    @Override
    protected void onPostExecute(List<RepresentationItem> items) {
      if (items == null) {
        Toast.makeText(
                getApplicationContext(), R.string.download_manifest_load_error, Toast.LENGTH_LONG)
            .show();
        return;
      }
      arrayAdapter.clear();
      for (RepresentationItem representationItem : items) {
        arrayAdapter.add(representationItem);
      }
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
        items.add(new RepresentationItem(key, trackName, downloader.getDownloadPercentage()));
      }
      return items;
    }

    @Override
    public DownloadAction getDownloadAction(
        String sampleName, ArrayList<Object> representationKeys) {
      RepresentationKey[] keys =
          representationKeys.toArray(new RepresentationKey[representationKeys.size()]);
      return new DashDownloadAction(manifestUri, false, sampleName, keys);
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
      for (RenditionKey key : downloader.getAllRepresentationKeys()) {
        downloader.selectRepresentations(new RenditionKey[] {key});
        try {
          downloader.init();
        } catch (IOException e) {
          continue;
        }
        items.add(new RepresentationItem(key, key.url, downloader.getDownloadPercentage()));
      }
      return items;
    }

    @Override
    public DownloadAction getDownloadAction(
        String sampleName, ArrayList<Object> representationKeys) {
      RenditionKey[] keys = representationKeys.toArray(new RenditionKey[representationKeys.size()]);
      return new HlsDownloadAction(manifestUri, false, sampleName, keys);
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
        items.add(new RepresentationItem(key, trackName, downloader.getDownloadPercentage()));
      }
      return items;
    }

    @Override
    public DownloadAction getDownloadAction(
        String sampleName, ArrayList<Object> representationKeys) {
      TrackKey[] keys = representationKeys.toArray(new TrackKey[representationKeys.size()]);
      return new SsDownloadAction(manifestUri, false, sampleName, keys);
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
          new ProgressiveDownloader(manifestUri, null, constructorHelper);
      ArrayList<RepresentationItem> items = new ArrayList<>();
      {
        downloader.init();
        items.add(new RepresentationItem(null, "Stream", downloader.getDownloadPercentage()));
      }
      return items;
    }

    @Override
    public DownloadAction getDownloadAction(
        String sampleName, ArrayList<Object> representationKeys) {
      return new ProgressiveDownloadAction(manifestUri, null, false, sampleName);
    }

    @Override
    public DownloadAction getRemoveAction() {
      return new ProgressiveDownloadAction(manifestUri, null, true, null);
    }
  }
}
