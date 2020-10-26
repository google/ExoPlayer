/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls.playlist;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.robolectric.RobolectricUtil;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.ByteArrayDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link DefaultHlsPlaylistTracker}. */
@RunWith(AndroidJUnit4.class)
public class DefaultHlsPlaylistTrackerTest {

  private static final String SAMPLE_M3U8_LIVE_MASTER = "media/m3u8/live_low_latency_master";
  private static final String SAMPLE_M3U8_LIVE_MASTER_MEDIA_URI_WITH_PARAM =
      "media/m3u8/live_low_latency_master_media_uri_with_param";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL =
      "media/m3u8/live_low_latency_media_can_skip_until";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_DATERANGES =
      "media/m3u8/live_low_latency_media_can_skip_dateranges";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_SKIPPED =
      "media/m3u8/live_low_latency_media_can_skip_skipped";
  private static final String
      SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_SKIPPED_MEDIA_SEQUENCE_NO_OVERLAPPING =
          "media/m3u8/live_low_latency_media_can_skip_skipped_media_sequence_no_overlapping";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_NOT_SKIP =
      "media/m3u8/live_low_latency_media_can_not_skip";
  private static final String SAMPLE_M3U8_LIVE_MEDIA_CAN_NOT_SKIP_NEXT =
      "media/m3u8/live_low_latency_media_can_not_skip_next";

  @Test
  public void start_playlistCanNotSkip_requestsFullUpdate() throws IOException, TimeoutException {

    Uri masterPlaylistUri = Uri.parse("fake://foo.bar/master.m3u8");
    Queue<DataSource> dataSourceQueue = new ArrayDeque<>();
    dataSourceQueue.add(new ByteArrayDataSource(getBytes(SAMPLE_M3U8_LIVE_MASTER)));
    dataSourceQueue.add(
        new DataSourceList(
            new ByteArrayDataSource(getBytes(SAMPLE_M3U8_LIVE_MEDIA_CAN_NOT_SKIP)),
            new ByteArrayDataSource(getBytes(SAMPLE_M3U8_LIVE_MEDIA_CAN_NOT_SKIP_NEXT))));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            /* dataSourceFactory= */ dataSourceQueue::remove,
            masterPlaylistUri,
            /* awaitedMediaPlaylistCount= */ 2);

    HlsMediaPlaylist firstFullPlaylist = mediaPlaylists.get(0);
    assertThat(firstFullPlaylist.mediaSequence).isEqualTo(10);
    assertThat(firstFullPlaylist.segments.get(0).url).isEqualTo("fileSequence10.ts");
    assertThat(firstFullPlaylist.segments.get(5).url).isEqualTo("fileSequence15.ts");
    assertThat(firstFullPlaylist.segments).hasSize(6);
    HlsMediaPlaylist secondFullPlaylist = mediaPlaylists.get(1);
    assertThat(secondFullPlaylist.mediaSequence).isEqualTo(11);
    assertThat(secondFullPlaylist.skippedSegmentCount).isEqualTo(0);
    assertThat(secondFullPlaylist.segments.get(0).url).isEqualTo("fileSequence11.ts");
    assertThat(secondFullPlaylist.segments.get(5).url).isEqualTo("fileSequence16.ts");
    assertThat(secondFullPlaylist.segments).hasSize(6);
    assertThat(secondFullPlaylist.segments).containsNoneIn(firstFullPlaylist.segments);
  }

  @Test
  public void start_playlistCanSkip_requestsDeltaUpdateAndExpandsSkippedSegments()
      throws IOException, TimeoutException {
    Uri masterPlaylistUri = Uri.parse("fake://foo.bar/master.m3u8");
    Uri mediaPlaylistUri = Uri.parse("fake://foo.bar/media0/playlist.m3u8");
    Uri mediaPlaylistSkippedUri = Uri.parse(mediaPlaylistUri + "?_HLS_skip=YES");
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(masterPlaylistUri, getBytes(SAMPLE_M3U8_LIVE_MASTER))
            .setData(mediaPlaylistUri, getBytes(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL))
            .setData(mediaPlaylistSkippedUri, getBytes(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_SKIPPED));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new FakeDataSource.Factory().setFakeDataSet(fakeDataSet),
            masterPlaylistUri,
            /* awaitedMediaPlaylistCount= */ 2);

    HlsMediaPlaylist initialPlaylistWithAllSegments = mediaPlaylists.get(0);
    assertThat(initialPlaylistWithAllSegments.mediaSequence).isEqualTo(10);
    assertThat(initialPlaylistWithAllSegments.segments).hasSize(6);
    HlsMediaPlaylist mergedPlaylist = mediaPlaylists.get(1);
    assertThat(mergedPlaylist.mediaSequence).isEqualTo(11);
    assertThat(mergedPlaylist.skippedSegmentCount).isEqualTo(0);
    assertThat(mergedPlaylist.segments).hasSize(6);
    // First 2 segments of the merged playlist need to be copied from the previous playlist.
    assertThat(mergedPlaylist.segments.subList(0, 2))
        .containsExactlyElementsIn(initialPlaylistWithAllSegments.segments.subList(1, 3))
        .inOrder();
    assertThat(mergedPlaylist.segments.get(2).url)
        .isEqualTo(initialPlaylistWithAllSegments.segments.get(3).url);
  }

  @Test
  public void start_playlistCanSkip_missingSegments_correctedMediaSequence()
      throws IOException, TimeoutException {
    Uri masterPlaylistUri = Uri.parse("fake://foo.bar/master.m3u8");
    Uri mediaPlaylistUri = Uri.parse("fake://foo.bar/media0/playlist.m3u8");
    Uri mediaPlaylistSkippedUri = Uri.parse(mediaPlaylistUri + "?_HLS_skip=YES");
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(masterPlaylistUri, getBytes(SAMPLE_M3U8_LIVE_MASTER))
            .setData(mediaPlaylistUri, getBytes(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL))
            .setData(
                mediaPlaylistSkippedUri,
                getBytes(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_SKIPPED_MEDIA_SEQUENCE_NO_OVERLAPPING));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new FakeDataSource.Factory().setFakeDataSet(fakeDataSet),
            masterPlaylistUri,
            /* awaitedMediaPlaylistCount= */ 2);

    HlsMediaPlaylist initialPlaylistWithAllSegments = mediaPlaylists.get(0);
    assertThat(initialPlaylistWithAllSegments.mediaSequence).isEqualTo(10);
    assertThat(initialPlaylistWithAllSegments.segments).hasSize(6);
    HlsMediaPlaylist mergedPlaylist = mediaPlaylists.get(1);
    assertThat(mergedPlaylist.mediaSequence).isEqualTo(22);
    assertThat(mergedPlaylist.skippedSegmentCount).isEqualTo(0);
    assertThat(mergedPlaylist.segments).hasSize(4);
  }

  @Test
  public void start_playlistCanSkipDataRanges_requestsDeltaUpdateV2()
      throws IOException, TimeoutException {
    Uri masterPlaylistUri = Uri.parse("fake://foo.bar/master.m3u8");
    Uri mediaPlaylistUri = Uri.parse("fake://foo.bar/media0/playlist.m3u8");
    // Expect _HLS_skip parameter with value v2.
    Uri mediaPlaylistSkippedUri = Uri.parse(mediaPlaylistUri + "?_HLS_skip=v2");
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(masterPlaylistUri, getBytes(SAMPLE_M3U8_LIVE_MASTER))
            .setData(mediaPlaylistUri, getBytes(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_DATERANGES))
            .setData(mediaPlaylistSkippedUri, getBytes(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_SKIPPED));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new FakeDataSource.Factory().setFakeDataSet(fakeDataSet),
            masterPlaylistUri,
            /* awaitedMediaPlaylistCount= */ 2);

    // Finding the media sequence of the second playlist request asserts that the second request has
    // been made with the correct uri parameter appended.
    assertThat(mediaPlaylists.get(1).mediaSequence).isEqualTo(11);
  }

  @Test
  public void start_playlistCanSkipAndUriWithParams_preservesOriginalParams()
      throws IOException, TimeoutException {
    Uri masterPlaylistUri = Uri.parse("fake://foo.bar/master.m3u8");
    Uri mediaPlaylistUri = Uri.parse("fake://foo.bar/media0/playlist.m3u8?param1=1&param2=2");
    // Expect _HLS_skip parameter appended with an ampersand.
    Uri mediaPlaylistSkippedUri = Uri.parse(mediaPlaylistUri + "&_HLS_skip=YES");
    FakeDataSet fakeDataSet =
        new FakeDataSet()
            .setData(masterPlaylistUri, getBytes(SAMPLE_M3U8_LIVE_MASTER_MEDIA_URI_WITH_PARAM))
            .setData(mediaPlaylistUri, getBytes(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_UNTIL))
            .setData(mediaPlaylistSkippedUri, getBytes(SAMPLE_M3U8_LIVE_MEDIA_CAN_SKIP_SKIPPED));

    List<HlsMediaPlaylist> mediaPlaylists =
        runPlaylistTrackerAndCollectMediaPlaylists(
            new FakeDataSource.Factory().setFakeDataSet(fakeDataSet),
            masterPlaylistUri,
            /* awaitedMediaPlaylistCount= */ 2);

    // Finding the media sequence of the second playlist request asserts that the second request has
    // been made with the original uri parameters preserved and the additional param concatenated
    // correctly.
    assertThat(mediaPlaylists.get(1).mediaSequence).isEqualTo(11);
  }

  private static List<HlsMediaPlaylist> runPlaylistTrackerAndCollectMediaPlaylists(
      DataSource.Factory dataSourceFactory, Uri masterPlaylistUri, int awaitedMediaPlaylistCount)
      throws TimeoutException {

    DefaultHlsPlaylistTracker defaultHlsPlaylistTracker =
        new DefaultHlsPlaylistTracker(
            dataType -> dataSourceFactory.createDataSource(),
            new DefaultLoadErrorHandlingPolicy(),
            new DefaultHlsPlaylistParserFactory());

    List<HlsMediaPlaylist> mediaPlaylists = new ArrayList<>();
    AtomicInteger playlistCounter = new AtomicInteger();
    defaultHlsPlaylistTracker.start(
        masterPlaylistUri,
        new MediaSourceEventListener.EventDispatcher(),
        mediaPlaylist -> {
          mediaPlaylists.add(mediaPlaylist);
          playlistCounter.addAndGet(1);
        });

    RobolectricUtil.runMainLooperUntil(() -> playlistCounter.get() == awaitedMediaPlaylistCount);

    defaultHlsPlaylistTracker.stop();
    return mediaPlaylists;
  }

  private static byte[] getBytes(String filename) throws IOException {
    return TestUtil.getByteArray(ApplicationProvider.getApplicationContext(), filename);
  }

  private static final class DataSourceList implements DataSource {

    private final DataSource[] dataSources;

    private DataSource delegate;
    private int index;

    /**
     * Creates an instance.
     *
     * @param dataSources The data sources to delegate to.
     */
    public DataSourceList(DataSource... dataSources) {
      checkArgument(dataSources.length > 0);
      this.dataSources = dataSources;
      delegate = dataSources[index++];
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
      for (DataSource dataSource : dataSources) {
        dataSource.addTransferListener(transferListener);
      }
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
      checkState(index <= dataSources.length);
      return delegate.open(dataSpec);
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
      return delegate.read(buffer, offset, readLength);
    }

    @Override
    @Nullable
    public Uri getUri() {
      return delegate.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
      return delegate.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
      if (index < dataSources.length) {
        delegate = dataSources[index];
      }
      index++;
    }
  }
}
