/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.ext.vp9;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.extractor.webm.WebmExtractor;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.Util;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.test.InstrumentationTestCase;

/**
 * Playback tests using {@link LibvpxVideoTrackRenderer}.
 */
public class VpxPlaybackTest extends InstrumentationTestCase {

  private static final String BEAR_URI = "asset:///bear-vp9.webm";
  private static final String BEAR_ODD_DIMENSIONS_URI = "asset:///bear-vp9-odd-dimensions.webm";
  private static final String INVALID_BITSTREAM_URI = "asset:///invalid-bitstream.webm";

  public void testBasicPlayback() throws ExoPlaybackException {
    playUri(BEAR_URI);
  }

  public void testOddDimensionsPlayback() throws ExoPlaybackException {
    playUri(BEAR_ODD_DIMENSIONS_URI);
  }

  public void testInvalidBitstream() {
    try {
      playUri(INVALID_BITSTREAM_URI);
      fail();
    } catch (Exception e) {
      assertNotNull(e.getCause());
      assertTrue(e.getCause() instanceof VpxDecoderException);
    }
  }

  private void playUri(String uri) throws ExoPlaybackException {
    TestPlaybackThread thread = new TestPlaybackThread(Uri.parse(uri),
        getInstrumentation().getContext());
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException e) {
      fail(); // Should never happen.
    }
    if (thread.playbackException != null) {
      throw thread.playbackException;
    }
  }

  private static class TestPlaybackThread extends Thread implements ExoPlayer.Listener {

    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 16;

    private final Context context;
    private final Uri uri;

    private ExoPlayer player;
    private ExoPlaybackException playbackException;

    public TestPlaybackThread(Uri uri, Context context) {
      this.uri = uri;
      this.context = context;
    }

    @Override
    public void run() {
      Looper.prepare();
      player = ExoPlayer.Factory.newInstance(1);
      player.addListener(this);
      ExtractorSampleSource  sampleSource = new ExtractorSampleSource(
          uri, new DefaultUriDataSource(context, Util.getUserAgent(context, "ExoPlayerExtVP9Test")),
          new DefaultAllocator(BUFFER_SEGMENT_SIZE), BUFFER_SEGMENT_SIZE * BUFFER_SEGMENT_COUNT,
          new WebmExtractor());
      LibvpxVideoTrackRenderer videoRenderer = new LibvpxVideoTrackRenderer(sampleSource, true);
      player.sendMessage(videoRenderer, LibvpxVideoTrackRenderer.MSG_SET_OUTPUT_BUFFER_RENDERER,
          new VpxVideoSurfaceView(context));
      player.prepare(videoRenderer);
      player.setPlayWhenReady(true);
      Looper.loop();
    }

    @Override
    public void onPlayWhenReadyCommitted () {
      // Do nothing.
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      playbackException = error;
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      if (playbackState == ExoPlayer.STATE_ENDED
          || (playbackState == ExoPlayer.STATE_IDLE && playbackException != null)) {
        releasePlayerAndQuitLooper();
      }
    }

    private void releasePlayerAndQuitLooper() {
      player.release();
      Looper.myLooper().quit();
    }

  }

}
