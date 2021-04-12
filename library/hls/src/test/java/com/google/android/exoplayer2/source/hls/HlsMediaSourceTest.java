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
package com.google.android.exoplayer2.source.hls;

import static com.google.android.exoplayer2.robolectric.RobolectricUtil.runMainLooperUntil;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import android.net.Uri;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.testutil.FakeDataSet;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.Util;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link HlsMediaSource}. */
@RunWith(AndroidJUnit4.class)
public class HlsMediaSourceTest {

  // Tests backwards compatibility
  @SuppressWarnings("deprecation")
  @Test
  public void factorySetTag_nullMediaItemTag_setsMediaItemTag() {
    Object tag = new Object();
    MediaItem mediaItem = MediaItem.fromUri("http://www.google.com");
    HlsMediaSource.Factory factory =
        new HlsMediaSource.Factory(mock(DataSource.Factory.class)).setTag(tag);

    MediaItem hlsMediaItem = factory.createMediaSource(mediaItem).getMediaItem();

    assertThat(hlsMediaItem.playbackProperties).isNotNull();
    assertThat(hlsMediaItem.playbackProperties.uri).isEqualTo(mediaItem.playbackProperties.uri);
    assertThat(hlsMediaItem.playbackProperties.tag).isEqualTo(tag);
  }

  // Tests backwards compatibility
  @SuppressWarnings("deprecation")
  @Test
  public void factorySetTag_nonNullMediaItemTag_doesNotOverrideMediaItemTag() {
    Object factoryTag = new Object();
    Object mediaItemTag = new Object();
    MediaItem mediaItem =
        new MediaItem.Builder().setUri("http://www.google.com").setTag(mediaItemTag).build();
    HlsMediaSource.Factory factory =
        new HlsMediaSource.Factory(mock(DataSource.Factory.class)).setTag(factoryTag);

    MediaItem hlsMediaItem = factory.createMediaSource(mediaItem).getMediaItem();

    assertThat(hlsMediaItem.playbackProperties).isNotNull();
    assertThat(hlsMediaItem.playbackProperties.uri).isEqualTo(mediaItem.playbackProperties.uri);
    assertThat(hlsMediaItem.playbackProperties.tag).isEqualTo(mediaItemTag);
  }

  // Tests backwards compatibility
  @SuppressWarnings("deprecation")
  @Test
  public void factorySetTag_setsDeprecatedMediaSourceTag() {
    Object tag = new Object();
    MediaItem mediaItem = MediaItem.fromUri("http://www.google.com");
    HlsMediaSource.Factory factory =
        new HlsMediaSource.Factory(mock(DataSource.Factory.class)).setTag(tag);

    @Nullable Object mediaSourceTag = factory.createMediaSource(mediaItem).getTag();

    assertThat(mediaSourceTag).isEqualTo(tag);
  }

  // Tests backwards compatibility
  @SuppressWarnings("deprecation")
  @Test
  public void factoryCreateMediaSource_setsDeprecatedMediaSourceTag() {
    Object tag = new Object();
    MediaItem mediaItem =
        new MediaItem.Builder().setUri("http://www.google.com").setTag(tag).build();
    HlsMediaSource.Factory factory =
        new HlsMediaSource.Factory(mock(DataSource.Factory.class)).setTag(new Object());

    @Nullable Object mediaSourceTag = factory.createMediaSource(mediaItem).getTag();

    assertThat(mediaSourceTag).isEqualTo(tag);
  }

  // Tests backwards compatibility
  @SuppressWarnings("deprecation")
  @Test
  public void factorySetStreamKeys_emptyMediaItemStreamKeys_setsMediaItemStreamKeys() {
    MediaItem mediaItem = MediaItem.fromUri("http://www.google.com");
    StreamKey streamKey = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 1);
    HlsMediaSource.Factory factory =
        new HlsMediaSource.Factory(mock(DataSource.Factory.class))
            .setStreamKeys(Collections.singletonList(streamKey));

    MediaItem hlsMediaItem = factory.createMediaSource(mediaItem).getMediaItem();

    assertThat(hlsMediaItem.playbackProperties).isNotNull();
    assertThat(hlsMediaItem.playbackProperties.uri).isEqualTo(mediaItem.playbackProperties.uri);
    assertThat(hlsMediaItem.playbackProperties.streamKeys).containsExactly(streamKey);
  }

  // Tests backwards compatibility
  @SuppressWarnings("deprecation")
  @Test
  public void factorySetStreamKeys_withMediaItemStreamKeys_doesNotOverrideMediaItemStreamKeys() {
    StreamKey mediaItemStreamKey = new StreamKey(/* groupIndex= */ 0, /* trackIndex= */ 1);
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("http://www.google.com")
            .setStreamKeys(Collections.singletonList(mediaItemStreamKey))
            .build();
    HlsMediaSource.Factory factory =
        new HlsMediaSource.Factory(mock(DataSource.Factory.class))
            .setStreamKeys(
                Collections.singletonList(new StreamKey(/* groupIndex= */ 1, /* trackIndex= */ 0)));

    MediaItem hlsMediaItem = factory.createMediaSource(mediaItem).getMediaItem();

    assertThat(hlsMediaItem.playbackProperties).isNotNull();
    assertThat(hlsMediaItem.playbackProperties.uri).isEqualTo(mediaItem.playbackProperties.uri);
    assertThat(hlsMediaItem.playbackProperties.streamKeys).containsExactly(mediaItemStreamKey);
  }

  @Test
  public void loadPlaylist_noTargetLiveOffsetDefined_fallbackToThreeTargetDuration()
      throws TimeoutException, ParserException {
    String playlistUri = "fake://foo.bar/media0/playlist.m3u8";
    // The playlist has a duration of 16 seconds but not hold back or part hold back.
    String playlist =
        "#EXTM3U\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-01T00:00:00.0+00:00\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence1.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence2.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence3.ts\n"
            + "#EXT-X-SERVER-CONTROL:CAN-SKIP-UNTIL=24";
    // The playlist finishes 1 second before the current time, therefore there's a live edge
    // offset of 1 second.
    SystemClock.setCurrentTimeMillis(Util.parseXsDateTime("2020-01-01T00:00:17.0+00:00"));
    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri, playlist);
    MediaItem mediaItem = MediaItem.fromUri(playlistUri);
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);

    Timeline timeline = prepareAndWaitForTimeline(mediaSource);

    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
    // The target live offset is picked from target duration (3 * 4 = 12 seconds) and then expressed
    // in relation to the live edge (12 + 1 seconds).
    assertThat(window.liveConfiguration.targetOffsetMs).isEqualTo(13000);
    assertThat(window.defaultPositionUs).isEqualTo(4000000);
  }

  @Test
  public void loadPlaylist_holdBackInPlaylist_targetLiveOffsetFromHoldBack()
      throws TimeoutException, ParserException {
    String playlistUri = "fake://foo.bar/media0/playlist.m3u8";
    // The playlist has a duration of 16 seconds and a hold back of 12 seconds.
    String playlist =
        "#EXTM3U\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-01T00:00:00.0+00:00\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence1.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence2.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence3.ts\n"
            + "#EXT-X-SERVER-CONTROL:HOLD-BACK=12";
    // The playlist finishes 1 second before the the current time, therefore there's a live edge
    // offset of 1 second.
    SystemClock.setCurrentTimeMillis(Util.parseXsDateTime("2020-01-01T00:00:17.0+00:00"));
    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri, playlist);
    MediaItem mediaItem = MediaItem.fromUri(playlistUri);
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);

    Timeline timeline = prepareAndWaitForTimeline(mediaSource);

    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
    // The target live offset is picked from hold back and then expressed in relation to the live
    // edge (+1 seconds).
    assertThat(window.liveConfiguration.targetOffsetMs).isEqualTo(13000);
    assertThat(window.defaultPositionUs).isEqualTo(4000000);
  }

  @Test
  public void
      loadPlaylist_partHoldBackWithoutPartInformationInPlaylist_targetLiveOffsetFromHoldBack()
          throws TimeoutException, ParserException {
    String playlistUri = "fake://foo.bar/media0/playlist.m3u8";
    // The playlist has a part hold back but not EXT-X-PART-INF. We should pick up the hold back.
    // The duration of the playlist is 16 seconds so that the defined hold back is within the live
    // window.
    String playlist =
        "#EXTM3U\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-01T00:00:00.0+00:00\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence1.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence2.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence3.ts\n"
            + "#EXT-X-SERVER-CONTROL:HOLD-BACK=12,PART-HOLD-BACK=3";
    // The playlist finishes 1 second before the the current time.
    SystemClock.setCurrentTimeMillis(Util.parseXsDateTime("2020-01-01T00:00:17.0+00:00"));
    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri, playlist);
    MediaItem mediaItem = MediaItem.fromUri(playlistUri);
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);

    Timeline timeline = prepareAndWaitForTimeline(mediaSource);

    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
    // The target live offset is picked from hold back and then expressed in relation to the live
    // edge (+1 seconds).
    assertThat(window.liveConfiguration.targetOffsetMs).isEqualTo(13000);
    assertThat(window.defaultPositionUs).isEqualTo(4000000);
  }

  @Test
  public void
      loadPlaylist_partHoldBackWithPartInformationInPlaylist_targetLiveOffsetFromPartHoldBack()
          throws TimeoutException, ParserException {
    String playlistUri = "fake://foo.bar/media0/playlist.m3u8";
    // The playlist has a duration of 4 seconds, part hold back and EXT-X-PART-INF defined.
    String playlist =
        "#EXTM3U\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-01T00:00:00.0+00:00\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXT-X-PART-INF:PART-TARGET=0.5\n"
            + "#EXT-X-SERVER-CONTROL:HOLD-BACK=12,PART-HOLD-BACK=3";
    // The playlist finishes 1 second before the current time.
    SystemClock.setCurrentTimeMillis(Util.parseXsDateTime("2020-01-01T00:00:05.0+00:00"));
    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri, playlist);
    MediaItem mediaItem = MediaItem.fromUri(playlistUri);
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);

    Timeline timeline = prepareAndWaitForTimeline(mediaSource);

    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
    // The target live offset is picked from part hold back and then expressed in relation to the
    // live edge (+1 seconds).
    assertThat(window.liveConfiguration.targetOffsetMs).isEqualTo(4000);
    assertThat(window.defaultPositionUs).isEqualTo(0);
  }

  @Test
  public void loadPlaylist_withPlaylistStartTime_targetLiveOffsetFromStartTime()
      throws TimeoutException, ParserException {
    String playlistUri = "fake://foo.bar/media0/playlist.m3u8";
    // The playlist has a duration of 16 seconds, and part hold back, hold back and start time
    // defined.
    String playlist =
        "#EXTM3U\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-01T00:00:00.0+00:00\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-START:TIME-OFFSET=-15"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence1.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence2.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence3.ts\n"
            + "#EXT-X-PART-INF:PART-TARGET=0.5\n"
            + "#EXT-X-SERVER-CONTROL:HOLD-BACK=12,PART-HOLD-BACK=3";
    // The playlist finishes 1 second before the current time.
    SystemClock.setCurrentTimeMillis(Util.parseXsDateTime("2020-01-01T00:00:17.0+00:00"));
    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri, playlist);
    MediaItem mediaItem = MediaItem.fromUri(playlistUri);
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);

    Timeline timeline = prepareAndWaitForTimeline(mediaSource);

    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
    // The target live offset is picked from start time and then expressed in relation to the live
    // edge (+1 seconds).
    assertThat(window.liveConfiguration.targetOffsetMs).isEqualTo(16000);
    assertThat(window.defaultPositionUs).isEqualTo(0);
  }

  @Test
  public void loadPlaylist_targetLiveOffsetInMediaItem_targetLiveOffsetPickedFromMediaItem()
      throws TimeoutException, ParserException {
    String playlistUri = "fake://foo.bar/media0/playlist.m3u8";
    // The playlist has a hold back of 12 seconds and a part hold back of 3 seconds.
    String playlist =
        "#EXTM3U\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-01T00:00:00.0+00:00\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXT-X-SERVER-CONTROL:HOLD-BACK=12,PART-HOLD-BACK=3";
    // The playlist finishes 1 second before the the current time. This should not affect the target
    // live offset set in the media item.
    SystemClock.setCurrentTimeMillis(Util.parseXsDateTime("2020-01-01T00:00:05.0+00:00"));
    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri, playlist);
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(playlistUri).setLiveTargetOffsetMs(1000).build();
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);

    Timeline timeline = prepareAndWaitForTimeline(mediaSource);

    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
    // The target live offset is picked from the media item and not adjusted.
    assertThat(window.liveConfiguration).isEqualTo(mediaItem.liveConfiguration);
    assertThat(window.defaultPositionUs).isEqualTo(0);
  }

  @Test
  public void loadPlaylist_targetLiveOffsetLargerThanLiveWindow_targetLiveOffsetIsWithinLiveWindow()
      throws TimeoutException, ParserException {
    String playlistUri = "fake://foo.bar/media0/playlist.m3u8";
    // The playlist has a duration of 8 seconds and a hold back of 12 seconds.
    String playlist =
        "#EXTM3U\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-01T00:00:00.0+00:00\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence1.ts\n"
            + "#EXT-X-SERVER-CONTROL:CAN-SKIP-UNTIL=24";
    // The playlist finishes 1 second before the live edge, therefore the live window duration is
    // 9 seconds (8 + 1).
    SystemClock.setCurrentTimeMillis(Util.parseXsDateTime("2020-01-01T00:00:09.0+00:00"));
    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri, playlist);
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(playlistUri).setLiveTargetOffsetMs(20_000).build();
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);

    Timeline timeline = prepareAndWaitForTimeline(mediaSource);

    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
    assertThat(mediaItem.liveConfiguration.targetOffsetMs)
        .isGreaterThan(C.usToMs(window.durationUs));
    assertThat(window.liveConfiguration.targetOffsetMs).isEqualTo(9000);
  }

  @Test
  public void
      loadPlaylist_withoutProgramDateTime_targetLiveOffsetFromPlaylistNotAdjustedToLiveEdge()
          throws TimeoutException {
    String playlistUri = "fake://foo.bar/media0/playlist.m3u8";
    // The playlist has a duration of 16 seconds and a hold back of 12 seconds.
    String playlist =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence1.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence2.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence3.ts\n"
            + "#EXT-X-SERVER-CONTROL:HOLD-BACK=12";
    // The playlist finishes 8 seconds before the current time.
    SystemClock.setCurrentTimeMillis(20000);
    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri, playlist);
    MediaItem mediaItem = new MediaItem.Builder().setUri(playlistUri).build();
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);

    Timeline timeline = prepareAndWaitForTimeline(mediaSource);

    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
    // The target live offset is not adjusted to the live edge because the list does not have
    // program date time.
    assertThat(window.liveConfiguration.targetOffsetMs).isEqualTo(12000);
    assertThat(window.defaultPositionUs).isEqualTo(4000000);
  }

  @Test
  public void refreshPlaylist_targetLiveOffsetRemainsInWindow()
      throws TimeoutException, IOException {
    String playlistUri1 = "fake://foo.bar/media0/playlist1.m3u8";
    // The playlist has a duration of 16 seconds and a hold back of 12 seconds.
    String playlist1 =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence1.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence2.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence3.ts\n"
            + "#EXT-X-SERVER-CONTROL:HOLD-BACK:12";
    // The second playlist defines a different hold back.
    String playlistUri2 = "fake://foo.bar/media0/playlist2.m3u8";
    String playlist2 =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:4\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence4.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence5.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence6.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence7.ts\n"
            + "#EXT-X-SERVER-CONTROL:HOLD-BACK:14";
    // The third playlist has a duration of 8 seconds.
    String playlistUri3 = "fake://foo.bar/media0/playlist3.m3u8";
    String playlist3 =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:4\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence8.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence9.ts\n"
            + "#EXTINF:4.00000,\n"
            + "#EXT-X-SERVER-CONTROL:HOLD-BACK:12";
    // The third playlist has a duration of 16 seconds but the target live offset should remain at
    // 8 seconds.
    String playlistUri4 = "fake://foo.bar/media0/playlist4.m3u8";
    String playlist4 =
        "#EXTM3U\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:4\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence10.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence11.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence12.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence13.ts\n"
            + "#EXTINF:4.00000,\n"
            + "#EXT-X-SERVER-CONTROL:HOLD-BACK:12";

    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri1, playlist1);
    MediaItem mediaItem = new MediaItem.Builder().setUri(playlistUri1).build();
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);
    HlsMediaPlaylist secondPlaylist = parseHlsMediaPlaylist(playlistUri2, playlist2);
    HlsMediaPlaylist thirdPlaylist = parseHlsMediaPlaylist(playlistUri3, playlist3);
    HlsMediaPlaylist fourthPlaylist = parseHlsMediaPlaylist(playlistUri4, playlist4);
    List<Timeline> timelines = new ArrayList<>();
    MediaSource.MediaSourceCaller mediaSourceCaller = (source, timeline) -> timelines.add(timeline);

    mediaSource.prepareSource(mediaSourceCaller, null);
    runMainLooperUntil(() -> timelines.size() == 1);
    mediaSource.onPrimaryPlaylistRefreshed(secondPlaylist);
    runMainLooperUntil(() -> timelines.size() == 2);
    mediaSource.onPrimaryPlaylistRefreshed(thirdPlaylist);
    runMainLooperUntil(() -> timelines.size() == 3);
    mediaSource.onPrimaryPlaylistRefreshed(fourthPlaylist);
    runMainLooperUntil(() -> timelines.size() == 4);

    Timeline.Window window = new Timeline.Window();
    assertThat(timelines.get(0).getWindow(0, window).liveConfiguration.targetOffsetMs)
        .isEqualTo(12000);
    assertThat(timelines.get(1).getWindow(0, window).liveConfiguration.targetOffsetMs)
        .isEqualTo(12000);
    assertThat(timelines.get(2).getWindow(0, window).liveConfiguration.targetOffsetMs)
        .isEqualTo(8000);
    assertThat(timelines.get(3).getWindow(0, window).liveConfiguration.targetOffsetMs)
        .isEqualTo(8000);
  }

  private static HlsMediaSource.Factory createHlsMediaSourceFactory(
      String playlistUri, String playlist) {
    FakeDataSet fakeDataSet = new FakeDataSet().setData(playlistUri, Util.getUtf8Bytes(playlist));
    return new HlsMediaSource.Factory(
            dataType -> new FakeDataSource.Factory().setFakeDataSet(fakeDataSet).createDataSource())
        .setElapsedRealTimeOffsetMs(0);
  }

  /** Prepares the media source and waits until the timeline is updated. */
  private static Timeline prepareAndWaitForTimeline(HlsMediaSource mediaSource)
      throws TimeoutException {
    AtomicReference<Timeline> receivedTimeline = new AtomicReference<>();
    mediaSource.prepareSource(
        (source, timeline) -> receivedTimeline.set(timeline), /* mediaTransferListener= */ null);
    runMainLooperUntil(() -> receivedTimeline.get() != null);
    return receivedTimeline.get();
  }

  private static HlsMediaPlaylist parseHlsMediaPlaylist(String playlistUri, String playlist)
      throws IOException {
    return (HlsMediaPlaylist)
        new HlsPlaylistParser()
            .parse(Uri.parse(playlistUri), new ByteArrayInputStream(Util.getUtf8Bytes(playlist)));
  }
}
