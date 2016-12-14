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
package com.google.android.exoplayer2.playbacktests.hls;

import android.annotation.TargetApi;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.playbacktests.util.ExoHostedTest;
import com.google.android.exoplayer2.playbacktests.util.HostActivity;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ClosedSource;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * Tests HLS playbacks using {@link ExoPlayer}.
 */
@ClosedSource(reason = "Streams are internal")
public final class HlsTest extends ActivityInstrumentationTestCase2<HostActivity> {

  private static final String TAG = "HlsTest";
  private static final String BASE_URL = "https://storage.googleapis.com/"
      + "exoplayer-test-media-internal-63834241aced7884c2544af1a3452e01/hls/bipbop/";
  private static final long TIMEOUT_MS = 3 * 60 * 1000;

  public HlsTest() {
    super(HostActivity.class);
  }

  /**
   * Tests playback for two variants with all segments available.
   */
  public void testAllSegmentsAvailable() throws IOException {
    testPlaybackForPath("bipbop-all-200.m3u8");
  }

  /**
   * Tests playback for a single variant with all segments available.
   */
  public void testSingleGearAllSegmentsAvailable() throws IOException {
    testPlaybackForPath("gear1/prog_index.m3u8");
  }

  /**
   * Tests playback for two variants where the first has an unavailable playlist. Playback should
   * succeed using the second variant.
   */
  public void testGear1PlaylistMissing() throws IOException {
    testPlaybackForPath("bipbop-gear1-playlist-404.m3u8");
  }

  /**
   * Tests playback for two variants where the second has an unavailable playlist. Playback should
   * succeed using the first variant.
   */
  public void testGear2PlaylistMissing() throws IOException {
    testPlaybackForPath("bipbop-gear2-playlist-404.m3u8");
  }

  /**
   * Tests playback for two variants where the first has a missing first segment. Playback should
   * succeed using the first segment from the second variant.
   */
  public void testGear1Seg1Missing() throws IOException {
    testPlaybackForPath("bipbop-gear1-seg1-404.m3u8");
  }

  /**
   * Tests playback for two variants where the second has a missing first segment. Playback should
   * succeed using the first segment from the first variant.
   */
  public void testGear2Seg1Missing() throws IOException {
    testPlaybackForPath("bipbop-gear2-seg1-404.m3u8");
  }

  /**
   * Tests playback for two variants where the first has a missing second segment. Playback should
   * succeed using the second segment from the second variant.
   */
  public void testGear1Seg2Missing() throws IOException {
    testPlaybackForPath("bipbop-gear1-seg2-404.m3u8");
  }

  /**
   * Tests playback for two variants where the second has a missing second segment. Playback should
   * succeed using the second segment from the first variant.
   */
  public void testGear2Seg2Missing() throws IOException {
    testPlaybackForPath("bipbop-gear2-seg2-404.m3u8");
  }

  /**
   * Tests playback for two variants where the first has a missing sixth segment. Playback should
   * succeed using the sixth segment from the second variant.
   */
  public void testGear1Seg6Missing() throws IOException {
    testPlaybackForPath("bipbop-gear1-seg6-404.m3u8");
  }

  /**
   * Tests playback for two variants where the second has a missing sixth segment. Playback should
   * succeed using the sixth segment from the first variant.
   */
  public void testGear2Seg6Missing() throws IOException {
    testPlaybackForPath("bipbop-gear2-seg6-404.m3u8");
  }

  /**
   * Tests playback of a single variant with a missing sixth segment. Playback should fail, however
   * should not do so until playback reaches the missing segment at 60 seconds.
   */
  public void testSingleGearSeg6Missing() throws IOException {
    testPlaybackForPath("gear1/prog_index-seg6-404.m3u8", 60000);
  }

  private void testPlaybackForPath(String path) throws IOException {
    testPlaybackForPath(path, C.TIME_UNSET);
  }

  private void testPlaybackForPath(String path, long expectedFailureTimeMs) throws IOException {
    if (Util.SDK_INT < 16) {
      // Pass.
      return;
    }
    HlsHostedTest test = new HlsHostedTest(Uri.parse(BASE_URL + path), expectedFailureTimeMs);
    getActivity().runTest(test, TIMEOUT_MS);
  }

  @TargetApi(16)
  private static class HlsHostedTest extends ExoHostedTest {

    private final Uri playlistUri;

    public HlsHostedTest(Uri playlistUri, long expectedFailureTimeMs) {
      super(TAG, expectedFailureTimeMs == C.TIME_UNSET
          ? ExoHostedTest.EXPECTED_PLAYING_TIME_MEDIA_DURATION_MS : expectedFailureTimeMs,
          expectedFailureTimeMs == C.TIME_UNSET);
      this.playlistUri = Assertions.checkNotNull(playlistUri);
    }

    @Override
    public MediaSource buildSource(HostActivity host, String userAgent,
        TransferListener<? super DataSource> mediaTransferListener) {
      DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(host, userAgent,
          mediaTransferListener);
      return new HlsMediaSource(playlistUri, dataSourceFactory, null, null);
    }

  }

}
