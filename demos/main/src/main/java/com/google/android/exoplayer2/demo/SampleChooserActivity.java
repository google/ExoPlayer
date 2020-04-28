/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.JsonReader;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** An activity for selecting from a list of media samples. */
public class SampleChooserActivity extends AppCompatActivity
    implements DownloadTracker.Listener, OnChildClickListener {

  private static final String TAG = "SampleChooserActivity";
  private static final String GROUP_POSITION_PREFERENCE_KEY = "SAMPLE_CHOOSER_GROUP_POSITION";
  private static final String CHILD_POSITION_PREFERENCE_KEY = "SAMPLE_CHOOSER_CHILD_POSITION";

  private String[] uris;
  private boolean useExtensionRenderers;
  private DownloadTracker downloadTracker;
  private SampleAdapter sampleAdapter;
  private MenuItem preferExtensionDecodersMenuItem;
  private MenuItem randomAbrMenuItem;
  private MenuItem tunnelingMenuItem;
  private ExpandableListView sampleListView;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.sample_chooser_activity);
    sampleAdapter = new SampleAdapter();
    sampleListView = findViewById(R.id.sample_list);

    sampleListView.setAdapter(sampleAdapter);
    sampleListView.setOnChildClickListener(this);

    Intent intent = getIntent();
    String dataUri = intent.getDataString();
    if (dataUri != null) {
      uris = new String[] {dataUri};
    } else {
      ArrayList<String> uriList = new ArrayList<>();
      AssetManager assetManager = getAssets();
      try {
        for (String asset : assetManager.list("")) {
          if (asset.endsWith(".exolist.json")) {
            uriList.add("asset:///" + asset);
          }
        }
      } catch (IOException e) {
        Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
            .show();
      }
      uris = new String[uriList.size()];
      uriList.toArray(uris);
      Arrays.sort(uris);
    }

    DemoApplication application = (DemoApplication) getApplication();
    useExtensionRenderers = application.useExtensionRenderers();
    downloadTracker = application.getDownloadTracker();
    loadSample();

    // Start the download service if it should be running but it's not currently.
    // Starting the service in the foreground causes notification flicker if there is no scheduled
    // action. Starting it in the background throws an exception if the app is in the background too
    // (e.g. if device screen is locked).
    try {
      DownloadService.start(this, DemoDownloadService.class);
    } catch (IllegalStateException e) {
      DownloadService.startForeground(this, DemoDownloadService.class);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.sample_chooser_menu, menu);
    preferExtensionDecodersMenuItem = menu.findItem(R.id.prefer_extension_decoders);
    preferExtensionDecodersMenuItem.setVisible(useExtensionRenderers);
    randomAbrMenuItem = menu.findItem(R.id.random_abr);
    tunnelingMenuItem = menu.findItem(R.id.tunneling);
    if (Util.SDK_INT < 21) {
      tunnelingMenuItem.setEnabled(false);
    }
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    item.setChecked(!item.isChecked());
    return true;
  }

  @Override
  public void onStart() {
    super.onStart();
    downloadTracker.addListener(this);
    sampleAdapter.notifyDataSetChanged();
  }

  @Override
  public void onStop() {
    downloadTracker.removeListener(this);
    super.onStop();
  }

  @Override
  public void onDownloadsChanged() {
    sampleAdapter.notifyDataSetChanged();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (grantResults.length == 0) {
      // Empty results are triggered if a permission is requested while another request was already
      // pending and can be safely ignored in this case.
      return;
    }
    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      loadSample();
    } else {
      Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
          .show();
      finish();
    }
  }

  private void loadSample() {
    checkNotNull(uris);

    for (int i = 0; i < uris.length; i++) {
      Uri uri = Uri.parse(uris[i]);
      if (Util.maybeRequestReadExternalStoragePermission(this, uri)) {
        return;
      }
    }

    SampleListLoader loaderTask = new SampleListLoader();
    loaderTask.execute(uris);
  }

  private void onPlaylistGroups(final List<PlaylistGroup> groups, boolean sawError) {
    if (sawError) {
      Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
          .show();
    }
    sampleAdapter.setPlaylistGroups(groups);

    SharedPreferences preferences = getPreferences(MODE_PRIVATE);

    int groupPosition = -1;
    int childPosition = -1;
    try {
      groupPosition = preferences.getInt(GROUP_POSITION_PREFERENCE_KEY, /* defValue= */ -1);
      childPosition = preferences.getInt(CHILD_POSITION_PREFERENCE_KEY, /* defValue= */ -1);
    } catch (ClassCastException e) {
      Log.w(TAG, "Saved position is not an int. Will not restore position.", e);
    }
    if (groupPosition != -1 && childPosition != -1) {
      sampleListView.expandGroup(groupPosition); // shouldExpandGroup does not work without this.
      sampleListView.setSelectedChild(groupPosition, childPosition, /* shouldExpandGroup= */ true);
    }
  }

  @Override
  public boolean onChildClick(
      ExpandableListView parent, View view, int groupPosition, int childPosition, long id) {
    // Save the selected item first to be able to restore it if the tested code crashes.
    SharedPreferences.Editor prefEditor = getPreferences(MODE_PRIVATE).edit();
    prefEditor.putInt(GROUP_POSITION_PREFERENCE_KEY, groupPosition);
    prefEditor.putInt(CHILD_POSITION_PREFERENCE_KEY, childPosition);
    prefEditor.apply();

    PlaylistHolder playlistHolder = (PlaylistHolder) view.getTag();
    Intent intent = new Intent(this, PlayerActivity.class);
    intent.putExtra(
        IntentUtil.PREFER_EXTENSION_DECODERS_EXTRA,
        isNonNullAndChecked(preferExtensionDecodersMenuItem));
    String abrAlgorithm =
        isNonNullAndChecked(randomAbrMenuItem)
            ? IntentUtil.ABR_ALGORITHM_RANDOM
            : IntentUtil.ABR_ALGORITHM_DEFAULT;
    intent.putExtra(IntentUtil.ABR_ALGORITHM_EXTRA, abrAlgorithm);
    intent.putExtra(IntentUtil.TUNNELING_EXTRA, isNonNullAndChecked(tunnelingMenuItem));
    IntentUtil.addToIntent(playlistHolder.mediaItems, intent);
    startActivity(intent);
    return true;
  }

  private void onSampleDownloadButtonClicked(PlaylistHolder playlistHolder) {
    int downloadUnsupportedStringId = getDownloadUnsupportedStringId(playlistHolder);
    if (downloadUnsupportedStringId != 0) {
      Toast.makeText(getApplicationContext(), downloadUnsupportedStringId, Toast.LENGTH_LONG)
          .show();
    } else {
      RenderersFactory renderersFactory =
          ((DemoApplication) getApplication())
              .buildRenderersFactory(isNonNullAndChecked(preferExtensionDecodersMenuItem));
      downloadTracker.toggleDownload(
          getSupportFragmentManager(), playlistHolder.mediaItems.get(0), renderersFactory);
    }
  }

  private int getDownloadUnsupportedStringId(PlaylistHolder playlistHolder) {
    if (playlistHolder.mediaItems.size() > 1) {
      return R.string.download_playlist_unsupported;
    }
    MediaItem.PlaybackProperties playbackProperties =
        checkNotNull(playlistHolder.mediaItems.get(0).playbackProperties);
    if (playbackProperties.drmConfiguration != null) {
      return R.string.download_drm_unsupported;
    }
    if (((IntentUtil.Tag) checkNotNull(playbackProperties.tag)).isLive) {
      return R.string.download_live_unsupported;
    }
    if (playbackProperties.adTagUri != null) {
      return R.string.download_ads_unsupported;
    }
    String scheme = playbackProperties.sourceUri.getScheme();
    if (!("http".equals(scheme) || "https".equals(scheme))) {
      return R.string.download_scheme_unsupported;
    }
    return 0;
  }

  private static boolean isNonNullAndChecked(@Nullable MenuItem menuItem) {
    // Temporary workaround for layouts that do not inflate the options menu.
    return menuItem != null && menuItem.isChecked();
  }

  private final class SampleListLoader extends AsyncTask<String, Void, List<PlaylistGroup>> {

    private boolean sawError;

    @Override
    protected List<PlaylistGroup> doInBackground(String... uris) {
      List<PlaylistGroup> result = new ArrayList<>();
      Context context = getApplicationContext();
      String userAgent = Util.getUserAgent(context, "ExoPlayerDemo");
      DataSource dataSource =
          new DefaultDataSource(context, userAgent, /* allowCrossProtocolRedirects= */ false);
      for (String uri : uris) {
        DataSpec dataSpec = new DataSpec(Uri.parse(uri));
        InputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
        try {
          readPlaylistGroups(new JsonReader(new InputStreamReader(inputStream, "UTF-8")), result);
        } catch (Exception e) {
          Log.e(TAG, "Error loading sample list: " + uri, e);
          sawError = true;
        } finally {
          Util.closeQuietly(dataSource);
        }
      }
      return result;
    }

    @Override
    protected void onPostExecute(List<PlaylistGroup> result) {
      onPlaylistGroups(result, sawError);
    }

    private void readPlaylistGroups(JsonReader reader, List<PlaylistGroup> groups)
        throws IOException {
      reader.beginArray();
      while (reader.hasNext()) {
        readPlaylistGroup(reader, groups);
      }
      reader.endArray();
    }

    private void readPlaylistGroup(JsonReader reader, List<PlaylistGroup> groups)
        throws IOException {
      String groupName = "";
      ArrayList<PlaylistHolder> playlistHolders = new ArrayList<>();

      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        switch (name) {
          case "name":
            groupName = reader.nextString();
            break;
          case "samples":
            reader.beginArray();
            while (reader.hasNext()) {
              playlistHolders.add(readEntry(reader, false));
            }
            reader.endArray();
            break;
          case "_comment":
            reader.nextString(); // Ignore.
            break;
          default:
            throw new ParserException("Unsupported name: " + name);
        }
      }
      reader.endObject();

      PlaylistGroup group = getGroup(groupName, groups);
      group.playlists.addAll(playlistHolders);
    }

    private PlaylistHolder readEntry(JsonReader reader, boolean insidePlaylist) throws IOException {
      Uri uri = null;
      String extension = null;
      String title = null;
      boolean isLive = false;
      String sphericalStereoMode = null;
      ArrayList<PlaylistHolder> children = null;
      Uri subtitleUri = null;
      String subtitleMimeType = null;
      String subtitleLanguage = null;

      MediaItem.Builder mediaItem = new MediaItem.Builder();
      reader.beginObject();
      while (reader.hasNext()) {
        String name = reader.nextName();
        switch (name) {
          case "name":
            title = reader.nextString();
            break;
          case "uri":
            uri = Uri.parse(reader.nextString());
            break;
          case "extension":
            extension = reader.nextString();
            break;
          case "drm_scheme":
            mediaItem.setDrmUuid(Util.getDrmUuid(reader.nextString()));
            break;
          case "is_live":
            isLive = reader.nextBoolean();
            break;
          case "drm_license_url":
            mediaItem.setDrmLicenseUri(reader.nextString());
            break;
          case "drm_key_request_properties":
            Map<String, String> requestHeaders = new HashMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
              requestHeaders.put(reader.nextName(), reader.nextString());
            }
            reader.endObject();
            mediaItem.setDrmLicenseRequestHeaders(requestHeaders);
            break;
          case "drm_session_for_clear_types":
            HashSet<Integer> drmSessionForClearTypes = new HashSet<>();
            reader.beginArray();
            while (reader.hasNext()) {
              drmSessionForClearTypes.add(toTrackType(reader.nextString()));
            }
            reader.endArray();
            mediaItem.setDrmSessionForClearTypes(new ArrayList<>(drmSessionForClearTypes));
            break;
          case "drm_multi_session":
            mediaItem.setDrmMultiSession(reader.nextBoolean());
            break;
          case "playlist":
            Assertions.checkState(!insidePlaylist, "Invalid nesting of playlists");
            children = new ArrayList<>();
            reader.beginArray();
            while (reader.hasNext()) {
              children.add(readEntry(reader, /* insidePlaylist= */ true));
            }
            reader.endArray();
            break;
          case "ad_tag_uri":
            mediaItem.setAdTagUri(reader.nextString());
            break;
          case "spherical_stereo_mode":
            Assertions.checkState(
                !insidePlaylist, "Invalid attribute on nested item: spherical_stereo_mode");
            sphericalStereoMode = reader.nextString();
            break;
          case "subtitle_uri":
            subtitleUri = Uri.parse(reader.nextString());
            break;
          case "subtitle_mime_type":
            subtitleMimeType = reader.nextString();
            break;
          case "subtitle_language":
            subtitleLanguage = reader.nextString();
            break;
          default:
            throw new ParserException("Unsupported attribute name: " + name);
        }
      }
      reader.endObject();

      if (children != null) {
        List<MediaItem> mediaItems = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
          mediaItems.addAll(children.get(i).mediaItems);
        }
        return new PlaylistHolder(title, mediaItems);
      } else {
        mediaItem
            .setSourceUri(uri)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build())
            .setMimeType(IntentUtil.inferAdaptiveStreamMimeType(uri, extension))
            .setTag(new IntentUtil.Tag(isLive, sphericalStereoMode));
        if (subtitleUri != null) {
          MediaItem.Subtitle subtitle =
              new MediaItem.Subtitle(
                  subtitleUri,
                  checkNotNull(
                      subtitleMimeType, "subtitle_mime_type is required if subtitle_uri is set."),
                  subtitleLanguage);
          mediaItem.setSubtitles(Collections.singletonList(subtitle));
        }
        return new PlaylistHolder(title, Collections.singletonList(mediaItem.build()));
      }
    }

    private PlaylistGroup getGroup(String groupName, List<PlaylistGroup> groups) {
      for (int i = 0; i < groups.size(); i++) {
        if (Util.areEqual(groupName, groups.get(i).title)) {
          return groups.get(i);
        }
      }
      PlaylistGroup group = new PlaylistGroup(groupName);
      groups.add(group);
      return group;
    }

    private int toTrackType(String trackTypeString) {
      switch (Util.toLowerInvariant(trackTypeString)) {
        case "audio":
          return C.TRACK_TYPE_AUDIO;
        case "video":
          return C.TRACK_TYPE_VIDEO;
        default:
          throw new IllegalArgumentException("Invalid track type: " + trackTypeString);
      }
    }
  }

  private final class SampleAdapter extends BaseExpandableListAdapter implements OnClickListener {

    private List<PlaylistGroup> playlistGroups;

    public SampleAdapter() {
      playlistGroups = Collections.emptyList();
    }

    public void setPlaylistGroups(List<PlaylistGroup> playlistGroups) {
      this.playlistGroups = playlistGroups;
      notifyDataSetChanged();
    }

    @Override
    public PlaylistHolder getChild(int groupPosition, int childPosition) {
      return getGroup(groupPosition).playlists.get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
      return childPosition;
    }

    @Override
    public View getChildView(
        int groupPosition,
        int childPosition,
        boolean isLastChild,
        View convertView,
        ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        view = getLayoutInflater().inflate(R.layout.sample_list_item, parent, false);
        View downloadButton = view.findViewById(R.id.download_button);
        downloadButton.setOnClickListener(this);
        downloadButton.setFocusable(false);
      }
      initializeChildView(view, getChild(groupPosition, childPosition));
      return view;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
      return getGroup(groupPosition).playlists.size();
    }

    @Override
    public PlaylistGroup getGroup(int groupPosition) {
      return playlistGroups.get(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
      return groupPosition;
    }

    @Override
    public View getGroupView(
        int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
      View view = convertView;
      if (view == null) {
        view =
            getLayoutInflater()
                .inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
      }
      ((TextView) view).setText(getGroup(groupPosition).title);
      return view;
    }

    @Override
    public int getGroupCount() {
      return playlistGroups.size();
    }

    @Override
    public boolean hasStableIds() {
      return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
      return true;
    }

    @Override
    public void onClick(View view) {
      onSampleDownloadButtonClicked((PlaylistHolder) view.getTag());
    }

    private void initializeChildView(View view, PlaylistHolder playlistHolder) {
      view.setTag(playlistHolder);
      TextView sampleTitle = view.findViewById(R.id.sample_title);
      sampleTitle.setText(playlistHolder.title);

      boolean canDownload = getDownloadUnsupportedStringId(playlistHolder) == 0;
      boolean isDownloaded =
          canDownload && downloadTracker.isDownloaded(playlistHolder.mediaItems.get(0));
      ImageButton downloadButton = view.findViewById(R.id.download_button);
      downloadButton.setTag(playlistHolder);
      downloadButton.setColorFilter(
          canDownload ? (isDownloaded ? 0xFF42A5F5 : 0xFFBDBDBD) : 0xFF666666);
      downloadButton.setImageResource(
          isDownloaded ? R.drawable.ic_download_done : R.drawable.ic_download);
    }
  }

  private static final class PlaylistHolder {

    public final String title;
    public final List<MediaItem> mediaItems;

    private PlaylistHolder(String title, List<MediaItem> mediaItems) {
      Assertions.checkArgument(!mediaItems.isEmpty());
      this.title = title;
      this.mediaItems = Collections.unmodifiableList(new ArrayList<>(mediaItems));
    }
  }

  private static final class PlaylistGroup {

    public final String title;
    public final List<PlaylistHolder> playlists;

    public PlaylistGroup(String title) {
      this.title = title;
      this.playlists = new ArrayList<>();
    }
  }
}
