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
package com.google.android.exoplayer2.playbacktests;

import android.annotation.TargetApi;
import android.net.Uri;
import android.test.ActivityInstrumentationTestCase2;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.playbacktests.util.ActionSchedule;
import com.google.android.exoplayer2.playbacktests.util.ExoHostedTest;
import com.google.android.exoplayer2.playbacktests.util.HostActivity;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.ClosedSource;
import com.google.android.exoplayer2.util.Util;

/**
 * Tests MP3 playback using {@link ExoPlayer}.
 */
@ClosedSource(reason = "Not yet ready")
public final class Mp3PlaybackTest extends ActivityInstrumentationTestCase2<HostActivity> {

  private static final String TAG = "Mp3PlaybackTest";
  private static final String URL = "http://storage.googleapis.com/exoplayer-test-media-0/play.mp3";

  private static final long TEST_TIMEOUT_MS = 2 * 60 * 1000;

  public Mp3PlaybackTest() {
    super(HostActivity.class);
  }

  public void testPlayback() {
    if (Util.SDK_INT < 16) {
      // Pass.
      return;
    }
    Mp3HostedTest test = new Mp3HostedTest(URL, true);
    getActivity().runTest(test, TEST_TIMEOUT_MS);
  }

  public void testPlaybackWithSeeking() {
    if (Util.SDK_INT < 16) {
      // Pass.
      return;
    }
    Mp3HostedTest test = new Mp3HostedTest(URL, false);
    ActionSchedule schedule = new ActionSchedule.Builder(TAG)
       .delay(5000).seek(30000)
       .delay(5000).seek(0)
       .delay(5000).seek(30000)
       .delay(5000).stop()
       .build();
    test.setSchedule(schedule);
    getActivity().runTest(test, TEST_TIMEOUT_MS);
  }

  @TargetApi(16)
  private static class Mp3HostedTest extends ExoHostedTest {

    private final Uri uri;

    public Mp3HostedTest(String uriString, boolean fullPlaybackNoSeeking) {
      super("Mp3PlaybackTest", fullPlaybackNoSeeking);
      uri = Uri.parse(uriString);
    }

    @Override
    public MediaSource buildSource(HostActivity host, String userAgent,
        TransferListener<? super DataSource> mediaTransferListener) {
      DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(host, userAgent,
          mediaTransferListener);
      return new ExtractorMediaSource(uri, dataSourceFactory, Mp3Extractor.FACTORY, null, null);
    }

  }

}
