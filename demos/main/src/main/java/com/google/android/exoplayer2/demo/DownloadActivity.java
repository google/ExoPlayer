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
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.ProgressiveDownloadAction;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.RepresentationKey;
import com.google.android.exoplayer2.source.dash.offline.DashDownloadAction;
import com.google.android.exoplayer2.source.hls.offline.HlsDownloadAction;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.source.hls.playlist.RenditionKey;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifestParser;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.TrackKey;
import com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloadAction;
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider;
import com.google.android.exoplayer2.ui.TrackNameProvider;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.ParcelableArray;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An activity for downloading media. */
public class DownloadActivity extends Activity {

  public static final String PLAYER_INTENT = "player_intent";
  public static final String SAMPLE_NAME = "sample_name";

  private Intent playerIntent;
  private String sampleName;

  private TrackNameProvider trackNameProvider;
  private AsyncTask manifestDownloaderTask;

  private DownloadUtilMethods downloadUtilMethods;
  private ListView representationList;
  private ArrayAdapter<RepresentationItem> arrayAdapter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.downloader_activity);
    trackNameProvider = new DefaultTrackNameProvider(getResources());

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
    DataSource.Factory manifestDataSourceFactory =
        application.buildDataSourceFactory(/* listener= */ null);
    String extension = playerIntent.getStringExtra(EXTENSION_EXTRA);
    int type = Util.inferContentType(sampleUri, extension);
    switch (type) {
      case C.TYPE_DASH:
        downloadUtilMethods = new DashDownloadUtilMethods(sampleUri, manifestDataSourceFactory);
        break;
      case C.TYPE_SS:
        downloadUtilMethods = new SsDownloadUtilMethods(sampleUri, manifestDataSourceFactory);
        break;
      case C.TYPE_HLS:
        downloadUtilMethods = new HlsDownloadUtilMethods(sampleUri, manifestDataSourceFactory);
        break;
      case C.TYPE_OTHER:
        downloadUtilMethods = new ProgressiveDownloadUtilMethods(sampleUri);
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
          downloadUtilMethods.getDownloadAction(
              /* isRemoveAction= */ false, sampleName, representationKeys));
    }
  }

  private void removeDownload() {
    DownloadService.addDownloadAction(
        this,
        DemoDownloadService.class,
        downloadUtilMethods.getDownloadAction(
            /* isRemoveAction= */ true, sampleName, Collections.emptyList()));
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

    public RepresentationItem(Parcelable key, String title) {
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
      try {
        return downloadUtilMethods.loadRepresentationItems(trackNameProvider);
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

    public DownloadUtilMethods(Uri manifestUri) {
      this.manifestUri = manifestUri;
    }

    public abstract List<RepresentationItem> loadRepresentationItems(
        TrackNameProvider trackNameProvider) throws IOException, InterruptedException;

    public abstract DownloadAction getDownloadAction(
        boolean isRemoveAction, String sampleName, List<Object> representationKeys);
  }

  private static final class DashDownloadUtilMethods extends DownloadUtilMethods {

    private final DataSource.Factory manifestDataSourceFactory;

    public DashDownloadUtilMethods(Uri manifestUri, DataSource.Factory manifestDataSourceFactory) {
      super(manifestUri);
      this.manifestDataSourceFactory = manifestDataSourceFactory;
    }

    @Override
    public List<RepresentationItem> loadRepresentationItems(TrackNameProvider trackNameProvider)
        throws IOException, InterruptedException {
      DataSource dataSource = manifestDataSourceFactory.createDataSource();
      DashManifest manifest =
          ParsingLoadable.load(dataSource, new DashManifestParser(), manifestUri);

      ArrayList<RepresentationItem> items = new ArrayList<>();
      for (int periodIndex = 0; periodIndex < manifest.getPeriodCount(); periodIndex++) {
        List<AdaptationSet> adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
        for (int adaptationIndex = 0; adaptationIndex < adaptationSets.size(); adaptationIndex++) {
          List<Representation> representations =
              adaptationSets.get(adaptationIndex).representations;
          int representationsCount = representations.size();
          for (int i = 0; i < representationsCount; i++) {
            RepresentationKey key = new RepresentationKey(periodIndex, adaptationIndex, i);
            String trackName = trackNameProvider.getTrackName(representations.get(i).format);
            items.add(new RepresentationItem(key, trackName));
          }
        }
      }
      return items;
    }

    @Override
    public DownloadAction getDownloadAction(
        boolean isRemoveAction, String sampleName, List<Object> representationKeys) {
      RepresentationKey[] keys =
          representationKeys.toArray(new RepresentationKey[representationKeys.size()]);
      return new DashDownloadAction(isRemoveAction, sampleName, manifestUri, keys);
    }

  }

  private static final class HlsDownloadUtilMethods extends DownloadUtilMethods {

    private final DataSource.Factory manifestDataSourceFactory;

    public HlsDownloadUtilMethods(Uri manifestUri, DataSource.Factory manifestDataSourceFactory) {
      super(manifestUri);
      this.manifestDataSourceFactory = manifestDataSourceFactory;
    }

    @Override
    public List<RepresentationItem> loadRepresentationItems(TrackNameProvider trackNameProvider)
        throws IOException, InterruptedException {
      DataSource dataSource = manifestDataSourceFactory.createDataSource();
      HlsPlaylist<?> playlist =
          ParsingLoadable.load(dataSource, new HlsPlaylistParser(), manifestUri);

      ArrayList<RepresentationItem> items = new ArrayList<>();
      if (playlist instanceof HlsMediaPlaylist) {
        items.add(new RepresentationItem(null, "Stream"));
      } else {
        HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) playlist;
        ArrayList<HlsMasterPlaylist.HlsUrl> hlsUrls = new ArrayList<>();
        hlsUrls.addAll(masterPlaylist.variants);
        hlsUrls.addAll(masterPlaylist.audios);
        hlsUrls.addAll(masterPlaylist.subtitles);
        for (HlsMasterPlaylist.HlsUrl hlsUrl : hlsUrls) {
          items.add(new RepresentationItem(new RenditionKey(hlsUrl.url), hlsUrl.url));
        }
      }
      return items;
    }

    @Override
    public DownloadAction getDownloadAction(
        boolean isRemoveAction, String sampleName, List<Object> representationKeys) {
      RenditionKey[] keys = representationKeys.toArray(new RenditionKey[representationKeys.size()]);
      return new HlsDownloadAction(isRemoveAction, sampleName, manifestUri, keys);
    }
  }

  private static final class SsDownloadUtilMethods extends DownloadUtilMethods {

    private final DataSource.Factory manifestDataSourceFactory;

    public SsDownloadUtilMethods(Uri manifestUri, DataSource.Factory manifestDataSourceFactory) {
      super(manifestUri);
      this.manifestDataSourceFactory = manifestDataSourceFactory;
    }

    @Override
    public List<RepresentationItem> loadRepresentationItems(TrackNameProvider trackNameProvider)
        throws IOException, InterruptedException {
      DataSource dataSource = manifestDataSourceFactory.createDataSource();
      SsManifest manifest = ParsingLoadable.load(dataSource, new SsManifestParser(), manifestUri);

      ArrayList<RepresentationItem> items = new ArrayList<>();
      for (int i = 0; i < manifest.streamElements.length; i++) {
        SsManifest.StreamElement streamElement = manifest.streamElements[i];
        for (int j = 0; j < streamElement.formats.length; j++) {
          TrackKey key = new TrackKey(i, j);
          String trackName = trackNameProvider.getTrackName(streamElement.formats[j]);
          items.add(new RepresentationItem(key, trackName));
        }
      }
      return items;
    }

    @Override
    public DownloadAction getDownloadAction(
        boolean isRemoveAction, String sampleName, List<Object> representationKeys) {
      TrackKey[] keys = representationKeys.toArray(new TrackKey[representationKeys.size()]);
      return new SsDownloadAction(isRemoveAction, sampleName, manifestUri, keys);
    }
  }

  private static final class ProgressiveDownloadUtilMethods extends DownloadUtilMethods {

    public ProgressiveDownloadUtilMethods(Uri manifestUri) {
      super(manifestUri);
    }

    @Override
    public List<RepresentationItem> loadRepresentationItems(TrackNameProvider trackNameProvider) {
      return Collections.singletonList(new RepresentationItem(null, "Stream"));
    }

    @Override
    public DownloadAction getDownloadAction(
        boolean isRemoveAction, String sampleName, List<Object> representationKeys) {
      return new ProgressiveDownloadAction(
          isRemoveAction, sampleName, manifestUri, /* customCacheKey= */ null);
    }
  }
}
