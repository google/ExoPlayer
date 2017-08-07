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
package com.google.android.exoplayer2.ext.opus;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.test.InstrumentationTestCase;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

/**
 * Playback tests using {@link LibopusAudioRenderer}.
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

  private static class TestPlaybackThread extends Thread implements Player.EventListener {

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
      LibopusAudioRenderer audioRenderer = new LibopusAudioRenderer();
      DefaultTrackSelector trackSelector = new DefaultTrackSelector();
      player = ExoPlayerFactory.newInstance(new Renderer[] {audioRenderer}, trackSelector);
      player.addListener(this);
      ExtractorMediaSource mediaSource = new ExtractorMediaSource(
          uri,
          new DefaultDataSourceFactory(context, "ExoPlayerExtOpusTest"),
          MatroskaExtractor.FACTORY,
          null,
          null);
      player.prepare(mediaSource);
      player.setPlayWhenReady(true);
      Looper.loop();
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
      // Do nothing.
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
      // Do nothing.
    }

    @Override
    public void onPositionDiscontinuity() {
      // Do nothing.
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      // Do nothing.
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
      // Do nothing.
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      playbackException = error;
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
      if (playbackState == Player.STATE_ENDED
          || (playbackState == Player.STATE_IDLE && playbackException != null)) {
        releasePlayerAndQuitLooper();
      }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
      // Do nothing.
    }

    private void releasePlayerAndQuitLooper() {
      player.release();
      Looper.myLooper().quit();
    }

  }

}
