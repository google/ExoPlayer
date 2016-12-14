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
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.playbacktests.util.DecoderCountersUtil;
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
 * Tests MP4 playback using {@link ExoPlayer}.
 */
@ClosedSource(reason = "Not yet ready")
public final class Mp4PlaybackTest extends ActivityInstrumentationTestCase2<HostActivity> {

  private static final String SOURCE_URL = "http://redirector.c.youtube.com/videoplayback?id=604ed5"
      + "ce52eda7ee&itag=22&source=youtube&sparams=ip,ipbits,expire,source,id&ip=0.0.0.0&ipbits=0&"
      + "expire=19000000000&signature=513F28C7FDCBEC60A66C86C9A393556C99DC47FB.04C88036EEE12565A1ED"
      + "864A875A58F15D8B5300&key=ik0";
  private static final String VIDEO_TAG = "Video";

  private static final long TEST_TIMEOUT_MS = 15 * 60 * 1000;
  private static final float MAX_DROPPED_VIDEO_FRAME_FRACTION = 0.01f;
  private static final int EXPECTED_VIDEO_FRAME_COUNT = 14316;

  public Mp4PlaybackTest() {
    super(HostActivity.class);
  }

  public void testPlayback() {
    if (Util.SDK_INT < 16) {
      // Pass.
      return;
    }
    Mp4HostedTest test = new Mp4HostedTest(SOURCE_URL, true);
    getActivity().runTest(test, TEST_TIMEOUT_MS);
  }

  @TargetApi(16)
  private static class Mp4HostedTest extends ExoHostedTest {

    private final Uri uri;

    public Mp4HostedTest(String uriString, boolean fullPlaybackNoSeeking) {
      super("Mp4PlaybackTest", fullPlaybackNoSeeking);
      uri = Uri.parse(uriString);
    }

    @Override
    public MediaSource buildSource(HostActivity host, String userAgent,
        TransferListener<? super DataSource> mediaTransferListener) {
      DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(host, userAgent);
      return new ExtractorMediaSource(uri, dataSourceFactory, Mp4Extractor.FACTORY, null, null);
    }

    @Override
    public void assertPassed(DecoderCounters audioCounters, DecoderCounters videoCounters) {
      assertEquals(1, videoCounters.decoderInitCount);
      assertEquals(1, videoCounters.decoderReleaseCount);
      DecoderCountersUtil.assertSkippedOutputBufferCount(VIDEO_TAG, videoCounters, 0);

      // We allow one fewer output buffer due to the way that MediaCodecRenderer and the
      // underlying decoders handle the end of stream. This should be tightened up in the future.
      DecoderCountersUtil.assertTotalOutputBufferCount(VIDEO_TAG, videoCounters,
          EXPECTED_VIDEO_FRAME_COUNT - 1, EXPECTED_VIDEO_FRAME_COUNT);

      int droppedFrameLimit = (int) Math.ceil(MAX_DROPPED_VIDEO_FRAME_FRACTION
          * DecoderCountersUtil.getTotalOutputBuffers(videoCounters));
      DecoderCountersUtil.assertDroppedOutputBufferLimit(VIDEO_TAG, videoCounters,
          droppedFrameLimit);
    }

  }

}
