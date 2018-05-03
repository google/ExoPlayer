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
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
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
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
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
  private DownloadHelper<? extends Parcelable> downloadHelper;
  private ListView representationList;
  private ArrayAdapter<String> arrayAdapter;

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
        downloadHelper = new DashDownloadHelper(sampleUri, manifestDataSourceFactory);
        break;
      case C.TYPE_SS:
        downloadHelper = new SsDownloadHelper(sampleUri, manifestDataSourceFactory);
        break;
      case C.TYPE_HLS:
        downloadHelper = new HlsDownloadHelper(sampleUri, manifestDataSourceFactory);
        break;
      case C.TYPE_OTHER:
        downloadHelper = new ProgressiveDownloadHelper(sampleUri);
        break;
      default:
        throw new IllegalStateException("Unsupported type: " + type);
    }

    new Thread() {
      @Override
      public void run() {
        try {
          downloadHelper.init();
          runOnUiThread(
              new Runnable() {
                @Override
                public void run() {
                  onInitialized();
                }
              });
        } catch (IOException e) {
          runOnUiThread(
              new Runnable() {
                @Override
                public void run() {
                  onInitializationError();
                }
              });
        }
      }
    }.start();
  }

  // This method is referenced in the layout file
  public void onClick(View v) {
    // switch-case doesn't work as in some compile configurations id definitions aren't constant
    int id = v.getId();
    if (id == R.id.download_button) {
      startDownload();
    } else if (id == R.id.remove_all_button) {
      removeDownload();
    } else if (id == R.id.play_button) {
      playDownload();
    }
  }

  private void onInitialized() {
    for (int i = 0; i < downloadHelper.getTrackCount(); i++) {
      arrayAdapter.add(trackNameProvider.getTrackName(downloadHelper.getTrackFormat(i)));
    }
  }

  private void onInitializationError() {
    Toast.makeText(
            getApplicationContext(), R.string.download_manifest_load_error, Toast.LENGTH_LONG)
        .show();
  }

  private void startDownload() {
    int[] selectedTrackIndices = getSelectedTrackIndices();
    if (selectedTrackIndices.length > 0) {
      DownloadService.addDownloadAction(
          this,
          DemoDownloadService.class,
          downloadHelper.getDownloadAction(
              /* isRemoveAction= */ false, sampleName, selectedTrackIndices));
    }
  }

  private void removeDownload() {
    DownloadService.addDownloadAction(
        this,
        DemoDownloadService.class,
        downloadHelper.getDownloadAction(/* isRemoveAction= */ true, sampleName));
    for (int i = 0; i < representationList.getChildCount(); i++) {
      representationList.setItemChecked(i, false);
    }
  }

  private void playDownload() {
    int[] selectedTrackIndices = getSelectedTrackIndices();
    List<? extends Parcelable> keys = downloadHelper.getTrackKeys(selectedTrackIndices);
    if (keys.isEmpty()) {
      playerIntent.removeExtra(PlayerActivity.MANIFEST_FILTER_EXTRA);
    } else {
      Parcelable[] keysArray = keys.toArray(new Parcelable[selectedTrackIndices.length]);
      playerIntent.putExtra(PlayerActivity.MANIFEST_FILTER_EXTRA, new ParcelableArray<>(keysArray));
    }
    startActivity(playerIntent);
  }

  private int[] getSelectedTrackIndices() {
    ArrayList<Integer> checkedIndices = new ArrayList<>();
    for (int i = 0; i < representationList.getChildCount(); i++) {
      if (representationList.isItemChecked(i)) {
        checkedIndices.add(i);
      }
    }
    return Util.toArray(checkedIndices);
  }

  private abstract static class DownloadHelper<K extends Parcelable> {

    protected static final Format DUMMY_FORMAT =
        Format.createContainerFormat(null, null, null, null, Format.NO_VALUE, 0, null);

    protected final Uri uri;
    protected final DataSource.Factory dataSourceFactory;
    protected final List<Format> trackFormats;
    protected final List<K> trackKeys;

    public DownloadHelper(Uri uri, DataSource.Factory dataSourceFactory) {
      this.uri = uri;
      this.dataSourceFactory = dataSourceFactory;
      trackFormats = new ArrayList<>();
      trackKeys = new ArrayList<>();
    }

    public abstract void init() throws IOException;

    public int getTrackCount() {
      return trackFormats.size();
    }

    public Format getTrackFormat(int trackIndex) {
      return trackFormats.get(trackIndex);
    }

    public List<K> getTrackKeys(int... trackIndices) {
      if (trackFormats.size() == 1 && trackFormats.get(0) == DUMMY_FORMAT) {
        return Collections.emptyList();
      }
      List<K> keys = new ArrayList<>(trackIndices.length);
      for (int trackIndex : trackIndices) {
        keys.add(trackKeys.get(trackIndex));
      }
      return keys;
    }

    public abstract DownloadAction getDownloadAction(
        boolean isRemoveAction, String sampleName, int... trackIndices);
  }

  private static final class DashDownloadHelper extends DownloadHelper<RepresentationKey> {

    public DashDownloadHelper(Uri uri, DataSource.Factory dataSourceFactory) {
      super(uri, dataSourceFactory);
    }

    @Override
    public void init() throws IOException {
      DataSource dataSource = dataSourceFactory.createDataSource();
      DashManifest manifest = ParsingLoadable.load(dataSource, new DashManifestParser(), uri);

      for (int periodIndex = 0; periodIndex < manifest.getPeriodCount(); periodIndex++) {
        List<AdaptationSet> adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
        for (int adaptationIndex = 0; adaptationIndex < adaptationSets.size(); adaptationIndex++) {
          List<Representation> representations =
              adaptationSets.get(adaptationIndex).representations;
          int representationsCount = representations.size();
          for (int i = 0; i < representationsCount; i++) {
            trackFormats.add(representations.get(i).format);
            trackKeys.add(new RepresentationKey(periodIndex, adaptationIndex, i));
          }
        }
      }
    }

    @Override
    public DownloadAction getDownloadAction(
        boolean isRemoveAction, String sampleName, int... trackIndices) {
      return new DashDownloadAction(uri, isRemoveAction, sampleName, getTrackKeys(trackIndices));
    }
  }

  private static final class HlsDownloadHelper extends DownloadHelper<RenditionKey> {

    public HlsDownloadHelper(Uri uri, DataSource.Factory dataSourceFactory) {
      super(uri, dataSourceFactory);
    }

    @Override
    public void init() throws IOException {
      DataSource dataSource = dataSourceFactory.createDataSource();
      HlsPlaylist<?> playlist = ParsingLoadable.load(dataSource, new HlsPlaylistParser(), uri);

      if (playlist instanceof HlsMediaPlaylist) {
        trackFormats.add(DUMMY_FORMAT);
      } else {
        HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) playlist;
        addRepresentationItems(masterPlaylist.variants, RenditionKey.GROUP_VARIANTS);
        addRepresentationItems(masterPlaylist.audios, RenditionKey.GROUP_AUDIOS);
        addRepresentationItems(masterPlaylist.subtitles, RenditionKey.GROUP_SUBTITLES);
      }
    }

    private void addRepresentationItems(List<HlsUrl> renditions, int renditionGroup) {
      for (int i = 0; i < renditions.size(); i++) {
        trackFormats.add(renditions.get(i).format);
        trackKeys.add(new RenditionKey(renditionGroup, i));
      }
    }

    @Override
    public DownloadAction getDownloadAction(
        boolean isRemoveAction, String sampleName, int... trackIndices) {
      return new HlsDownloadAction(uri, isRemoveAction, sampleName, getTrackKeys(trackIndices));
    }
  }

  private static final class SsDownloadHelper extends DownloadHelper<TrackKey> {

    public SsDownloadHelper(Uri uri, DataSource.Factory dataSourceFactory) {
      super(uri, dataSourceFactory);
    }

    @Override
    public void init() throws IOException {
      DataSource dataSource = dataSourceFactory.createDataSource();
      SsManifest manifest = ParsingLoadable.load(dataSource, new SsManifestParser(), uri);

      for (int i = 0; i < manifest.streamElements.length; i++) {
        SsManifest.StreamElement streamElement = manifest.streamElements[i];
        for (int j = 0; j < streamElement.formats.length; j++) {
          trackFormats.add(streamElement.formats[j]);
          trackKeys.add(new TrackKey(i, j));
        }
      }
    }

    @Override
    public DownloadAction getDownloadAction(
        boolean isRemoveAction, String sampleName, int... trackIndices) {
      return new SsDownloadAction(uri, isRemoveAction, sampleName, getTrackKeys(trackIndices));
    }
  }

  private static final class ProgressiveDownloadHelper extends DownloadHelper<Parcelable> {

    public ProgressiveDownloadHelper(Uri uri) {
      super(uri, null);
    }

    @Override
    public void init() {
      trackFormats.add(DUMMY_FORMAT);
    }

    @Override
    public DownloadAction getDownloadAction(
        boolean isRemoveAction, String sampleName, int... trackIndices) {
      return new ProgressiveDownloadAction(
          uri, isRemoveAction, sampleName, /* customCacheKey= */ null);
    }
  }
}
