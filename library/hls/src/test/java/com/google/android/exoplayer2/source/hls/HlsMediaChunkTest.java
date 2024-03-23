/*
 * Copyright 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParser;
import com.google.android.exoplayer2.testutil.FakeDataSource;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/** Unit tests for {@link HlsMediaChunk}. */
@RunWith(AndroidJUnit4.class)
public class HlsMediaChunkTest {

  private static final String PLAYLIST_INDEPENDENT_SEGMENTS =
      "media/m3u8/media_playlist_independent_segments";
  private static final String PLAYLIST_INDEPENDENT_PART =
      "media/m3u8/live_low_latency_segment_with_independent_part";

  private static final String PLAYLIST_IFRAME_2s =
      "media/m3u8/media_playlist_independent_2second_iframe";
  private static final String PLAYLIST_IFRAME_4s =
      "media/m3u8/media_playlist_independent_4second_iframe";

  private static final Uri PLAYLIST_URI = Uri.parse("http://example.com/");

  private static final String PLAYLIST_NON_INDEPENDENT_SEGMENTS =
      "media/m3u8/media_playlist";

  @Mock private HlsExtractorFactory mockExtractorFactory;
  @Mock private DataSource mockDataSource;
  private HlsMediaPlaylist playlist;


  private static final Format BASE_VIDEO_FORMAT =
      new Format.Builder()
          .setSampleMimeType(MimeTypes.VIDEO_H264)
          .setAverageBitrate(30_000)
          .setWidth(1280)
          .setHeight(720)
          .build();

  private static final Format IFRAME_FORMAT =
      BASE_VIDEO_FORMAT.buildUpon()
          .setRoleFlags(C.ROLE_FLAG_TRICK_PLAY)
          .build();
  @Before
  public void setUp() throws Exception {

    InputStream inputStream =
        TestUtil.getInputStream(
            ApplicationProvider.getApplicationContext(), PLAYLIST_INDEPENDENT_SEGMENTS);
    playlist = (HlsMediaPlaylist) new HlsPlaylistParser().parse(Uri.EMPTY, inputStream);

    mockDataSource = new FakeDataSource();
    mockExtractorFactory = new DefaultHlsExtractorFactory();
  }

  @Test
  public void test_shouldSpliceIn_isFalse_NoPrevious() {
    boolean result =
        HlsMediaChunk.shouldSpliceIn(null, Uri.EMPTY, playlist, BASE_VIDEO_FORMAT, null, 0);
    assertThat(result).isFalse();
  }

  @Test
  public void test_shouldSpliceIn_PreviousLoaded_SamePlaylist() {
    HlsChunkSource.SegmentBaseHolder segmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(playlist.segments.get(0), 0, 0);
    HlsMediaChunk previousChunk = createTestHlsMediaChunk(BASE_VIDEO_FORMAT, segmentBaseHolder, PLAYLIST_URI, true);
    previousChunk.setLoadCompleted();
    boolean result =
        HlsMediaChunk.shouldSpliceIn(previousChunk, PLAYLIST_URI, playlist, BASE_VIDEO_FORMAT, segmentBaseHolder, 0);

    assertThat(result).isFalse();
  }

  @Test
  public void test_shouldSpliceIn_NotIndependent_DifferentPlaylist() {
    HlsChunkSource.SegmentBaseHolder previousSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(playlist.segments.get(0), 0, 0);

    Uri variant1 = Uri.parse("http://example.com/variant1.m3u8");
    HlsMediaChunk previousChunk = createTestHlsMediaChunk(BASE_VIDEO_FORMAT, previousSegmentBaseHolder, variant1, true);

    Uri variant2 = Uri.parse("http://example.com/variant2.m3u8");
    HlsMediaPlaylist nonIndependentPlaylist = HlsTestUtils.getHlsMediaPlaylist(PLAYLIST_NON_INDEPENDENT_SEGMENTS, variant2);
    HlsChunkSource.SegmentBaseHolder nextSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(nonIndependentPlaylist.segments.get(0), 0, 0);

    // Switch to non-independent segment playlist requires splice-in, regardless if the prev and
    // next segments overlap or not

    assertThat(previousSegmentBaseHolder.segmentBase.relativeStartTimeUs).isEqualTo(0);   // inputs assertions
    assertThat(nextSegmentBaseHolder.segmentBase.relativeStartTimeUs + nextSegmentBaseHolder.segmentBase.durationUs).isGreaterThan(0);
    boolean result =
        HlsMediaChunk.shouldSpliceIn(previousChunk, variant2, nonIndependentPlaylist, BASE_VIDEO_FORMAT, nextSegmentBaseHolder, 0);
    assertThat(result).isTrue();

    nextSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(nonIndependentPlaylist.segments.get(1), 1, 0);
    result =
        HlsMediaChunk.shouldSpliceIn(previousChunk, variant2, nonIndependentPlaylist, BASE_VIDEO_FORMAT, nextSegmentBaseHolder, 0);
    assertThat(result).isTrue();
  }

  @Test
  public void test_shouldSpliceIn_Independent_DifferentPlaylist() {
    HlsChunkSource.SegmentBaseHolder previousSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(playlist.segments.get(0), 0, 0);

    Uri variant1 = Uri.parse("http://example.com/variant1.m3u8");
    HlsMediaChunk previousChunk = createTestHlsMediaChunk(BASE_VIDEO_FORMAT, previousSegmentBaseHolder, variant1, true);

    // NOTE, playlist change is checked by Uri match, so can use same playlist, mock change with URI
    Uri variant2 = Uri.parse("http://example.com/variant2.m3u8");
    HlsChunkSource.SegmentBaseHolder nextSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(playlist.segments.get(0), 0, 0);

    // Switch to inependent segment playlist requires splice-in, only if the start of the next segment is
    // less than the end of the previous

    assertThat(previousSegmentBaseHolder.segmentBase.relativeStartTimeUs).isEqualTo(0);
    assertThat(nextSegmentBaseHolder.segmentBase.relativeStartTimeUs + nextSegmentBaseHolder.segmentBase.durationUs).isGreaterThan(0);
    boolean result =
        HlsMediaChunk.shouldSpliceIn(previousChunk, variant2, playlist, BASE_VIDEO_FORMAT, nextSegmentBaseHolder, 0);
    assertThat(result).isTrue();

    nextSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(playlist.segments.get(1), 1, 0);
    result =
        HlsMediaChunk.shouldSpliceIn(previousChunk, variant2, playlist, BASE_VIDEO_FORMAT, nextSegmentBaseHolder, 0);
    assertThat(result).isFalse();
  }

  @Test
  public void test_shouldSpliceIn_SegmentParts() {
    HlsChunkSource.SegmentBaseHolder previousSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(playlist.segments.get(0), 0, 0);

    Uri variant1 = Uri.parse("http://example.com/variant1.m3u8");
    HlsMediaChunk previousChunk = createTestHlsMediaChunk(BASE_VIDEO_FORMAT, previousSegmentBaseHolder, variant1, true);
    previousChunk.setLoadCompleted();

    Uri variant2 = Uri.parse("http://example.com/variant2.m3u8");
    HlsMediaPlaylist hlsMediaPlaylist = HlsTestUtils.getHlsMediaPlaylist(PLAYLIST_INDEPENDENT_PART, variant2);

    // First Part checks segment level independent
    HlsChunkSource.SegmentBaseHolder nextSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(hlsMediaPlaylist.trailingParts.get(0), 0, 0);
    boolean result =
        HlsMediaChunk.shouldSpliceIn(previousChunk, variant2, hlsMediaPlaylist, BASE_VIDEO_FORMAT, nextSegmentBaseHolder, 0);

    assertThat(result).isFalse();

    // Additional Parts must be themselves independent, regardless of the independence of the playlist
    nextSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(hlsMediaPlaylist.trailingParts.get(1), 1, 1);
    result =
        HlsMediaChunk.shouldSpliceIn(previousChunk, variant2, hlsMediaPlaylist, BASE_VIDEO_FORMAT, nextSegmentBaseHolder, 0);
    assertThat(result).isFalse();

  }

  @Test
  public void test_shouldSpliceIn_IntraTrickPlay() {

    // Trick play to trick play track should never need a splice, even overlapping

    Uri variant4 = Uri.parse("http://example.com/iframe4.m3u8");
    HlsMediaPlaylist hlsMediaPlaylist4s = HlsTestUtils.getHlsMediaPlaylist(PLAYLIST_IFRAME_4s, variant4);
    HlsChunkSource.SegmentBaseHolder previousSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(hlsMediaPlaylist4s.segments.get(0), 0, 0);
    HlsMediaChunk previousChunk = createTestHlsMediaChunk(IFRAME_FORMAT, previousSegmentBaseHolder, variant4, true);

    Uri variant2 = Uri.parse("http://example.com/iframe2.m3u8");
    HlsMediaPlaylist hlsMediaPlaylist2s = HlsTestUtils.getHlsMediaPlaylist(PLAYLIST_IFRAME_2s, variant2);
    HlsChunkSource.SegmentBaseHolder nextSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(hlsMediaPlaylist2s.segments.get(1), 0, 0);
    boolean result =
        HlsMediaChunk.shouldSpliceIn(previousChunk, variant2, hlsMediaPlaylist2s, IFRAME_FORMAT, nextSegmentBaseHolder, 0);

    assertThat(result).isFalse();
  }

  @Test
  @Ignore
  public void test_shouldSpliceIn_NonTrickPlay_To_TrickPlay() {

    // Switch to a trick-play track never requires splice in, even if overlapping
    HlsChunkSource.SegmentBaseHolder previousSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(playlist.segments.get(0), 0, 0);
    HlsMediaChunk previousChunk = createTestHlsMediaChunk(BASE_VIDEO_FORMAT, previousSegmentBaseHolder, PLAYLIST_URI, true);

    Uri variant2 = Uri.parse("http://example.com/iframe2.m3u8");
    HlsMediaPlaylist hlsMediaPlaylist2s = HlsTestUtils.getHlsMediaPlaylist(PLAYLIST_IFRAME_2s, variant2);
    HlsChunkSource.SegmentBaseHolder nextSegmentBaseHolder =
        new HlsChunkSource.SegmentBaseHolder(hlsMediaPlaylist2s.segments.get(1), 0, 0);
    boolean result =
        HlsMediaChunk.shouldSpliceIn(previousChunk, variant2, hlsMediaPlaylist2s, IFRAME_FORMAT, nextSegmentBaseHolder, 0);

    assertThat(result).isFalse();
  }

  private HlsMediaChunk createTestHlsMediaChunk(
        Format selectedTrackFormat,
        HlsChunkSource.SegmentBaseHolder segmentBaseHolder,
        Uri selectedPlaylistUrl,
        boolean shouldSpliceIn) {
      return HlsMediaChunk.createInstance(
          mockExtractorFactory,
          mockDataSource,
          selectedTrackFormat,
          0,
          playlist,
          segmentBaseHolder,
          selectedPlaylistUrl,
          null,
          C.SELECTION_REASON_INITIAL,
          null,
          true,
          new TimestampAdjusterProvider(),
          null,
          null,
          null,
          shouldSpliceIn,
          new PlayerId());
  }
}
