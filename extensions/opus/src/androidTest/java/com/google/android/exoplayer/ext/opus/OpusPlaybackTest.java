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
package com.google.android.exoplayer.ext.opus;

import com.google.android.exoplayer.DefaultTrackSelectionPolicy;
import com.google.android.exoplayer.DefaultTrackSelector;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.ExoPlayerFactory;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer.upstream.DefaultDataSourceFactory;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.test.InstrumentationTestCase;

/**
 * Playback tests using {@link LibopusAudioTrackRenderer}.
 */
public class OpusPlaybackTest extends InstrumentationTestCase {

  private static final String BEAR_OPUS_URI = "asset:///bear-opus.webm";

  public void testBasicPlayback() throws ExoPlaybackException {
    playUri(BEAR_OPUS_URI);
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

  private static class TestPlaybackThread extends Thread implements ExoPlayer.EventListener {

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
      LibopusAudioTrackRenderer audioRenderer = new LibopusAudioTrackRenderer();
      DefaultTrackSelector trackSelector = new DefaultTrackSelector(
          new DefaultTrackSelectionPolicy(), null);
      player = ExoPlayerFactory.newInstance(new TrackRenderer[] {audioRenderer}, trackSelector);
      player.addListener(this);
      ExtractorSampleSource sampleSource = new ExtractorSampleSource(
          uri,
          new DefaultDataSourceFactory(context, "ExoPlayerExtOpusTest"),
          null,
          new MatroskaExtractor.Factory(),
          null,
          null);
      player.setSource(sampleSource);
      player.setPlayWhenReady(true);
      Looper.loop();
    }

    @Override
    public void onPlayWhenReadyCommitted () {
      // Do nothing.
    }

    @Override
    public void onPositionDiscontinuity(int sourceIndex, long positionMs) {
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
