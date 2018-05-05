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
import com.google.android.exoplayer2.offline.DownloadAction;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.ProgressiveDownloadHelper;
import com.google.android.exoplayer2.offline.SegmentDownloadAction;
import com.google.android.exoplayer2.offline.TrackKey;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.offline.DashDownloadHelper;
import com.google.android.exoplayer2.source.hls.offline.HlsDownloadHelper;
import com.google.android.exoplayer2.source.smoothstreaming.offline.SsDownloadHelper;
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider;
import com.google.android.exoplayer2.ui.TrackNameProvider;
import com.google.android.exoplayer2.upstream.DataSource;
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

  private TrackNameProvider trackNameProvider;
  private DownloadHelper downloadHelper;
  private ListView trackList;
  private ArrayAdapter<String> arrayAdapter;
  private ArrayList<TrackKey> trackKeys;

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
    trackList = findViewById(R.id.representation_list);
    trackList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    trackList.setAdapter(arrayAdapter);
    trackKeys = new ArrayList<>();

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

    downloadHelper.prepare(
        new DownloadHelper.Callback() {
          @Override
          public void onPrepared(DownloadHelper helper) {
            DownloadActivity.this.onPrepared();
          }

          @Override
          public void onPrepareError(DownloadHelper helper, IOException e) {
            DownloadActivity.this.onPrepareError();
          }
        });
  }

  private void onPrepared() {
    for (int i = 0; i < downloadHelper.getPeriodCount(); i++) {
      TrackGroupArray trackGroups = downloadHelper.getTrackGroups(i);
      for (int j = 0; j < trackGroups.length; j++) {
        TrackGroup trackGroup = trackGroups.get(j);
        for (int k = 0; k < trackGroup.length; k++) {
          arrayAdapter.add(trackNameProvider.getTrackName(trackGroup.getFormat(k)));
          trackKeys.add(new TrackKey(i, j, k));
        }
      }
    }
  }

  private void onPrepareError() {
    Toast.makeText(
            getApplicationContext(), R.string.download_manifest_load_error, Toast.LENGTH_LONG)
        .show();
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

  private void startDownload() {
    List<TrackKey> selectedTrackKeys = getSelectedTrackKeys();
    if (trackKeys.isEmpty() || !selectedTrackKeys.isEmpty()) {
      DownloadService.addDownloadAction(
          this,
          DemoDownloadService.class,
          downloadHelper.getDownloadAction(Util.getUtf8Bytes(sampleName), selectedTrackKeys));
    }
  }

  private void removeDownload() {
    DownloadService.addDownloadAction(
        this,
        DemoDownloadService.class,
        downloadHelper.getRemoveAction(Util.getUtf8Bytes(sampleName)));
    for (int i = 0; i < trackList.getChildCount(); i++) {
      trackList.setItemChecked(i, false);
    }
  }

  private void playDownload() {
    DownloadAction action = downloadHelper.getDownloadAction(null, getSelectedTrackKeys());
    List<? extends ParcelableArray> keys = null;
    if (action instanceof SegmentDownloadAction) {
      keys = ((SegmentDownloadAction) action).keys;
    }
    if (keys.isEmpty()) {
      playerIntent.removeExtra(PlayerActivity.MANIFEST_FILTER_EXTRA);
    } else {
      playerIntent.putExtra(
          PlayerActivity.MANIFEST_FILTER_EXTRA,
          new ParcelableArray(keys.toArray(new Parcelable[0])));
    }
    startActivity(playerIntent);
  }

  private List<TrackKey> getSelectedTrackKeys() {
    ArrayList<TrackKey> selectedTrackKeys = new ArrayList<>();
    for (int i = 0; i < trackList.getChildCount(); i++) {
      if (trackList.isItemChecked(i)) {
        selectedTrackKeys.add(trackKeys.get(i));
      }
    }
    return selectedTrackKeys;
  }
}
