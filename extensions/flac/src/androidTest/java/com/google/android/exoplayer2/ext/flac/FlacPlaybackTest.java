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
package com.google.android.exoplayer2.ext.flac;

import static org.junit.Assert.fail;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.testutil.CapturingAudioSink;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Playback tests using {@link LibflacAudioRenderer}. */
@RunWith(AndroidJUnit4.class)
public class FlacPlaybackTest {

  private static final String BEAR_FLAC_16BIT = "mka/bear-flac-16bit.mka";
  private static final String BEAR_FLAC_24BIT = "mka/bear-flac-24bit.mka";

  @Before
  public void setUp() {
    if (!FlacLibrary.isAvailable()) {
      fail("Flac library not available.");
    }
  }

  @Test
  public void test16BitPlayback() throws Exception {
    playAndAssertAudioSinkInput(BEAR_FLAC_16BIT);
  }

  @Test
  public void test24BitPlayback() throws Exception {
    playAndAssertAudioSinkInput(BEAR_FLAC_24BIT);
  }

  private static void playAndAssertAudioSinkInput(String fileName) throws Exception {
    CapturingAudioSink audioSink =
        new CapturingAudioSink(
            new DefaultAudioSink(/* audioCapabilities= */ null, new AudioProcessor[0]));

    TestPlaybackRunnable testPlaybackRunnable =
        new TestPlaybackRunnable(
            Uri.parse("asset:///media/" + fileName),
            ApplicationProvider.getApplicationContext(),
            audioSink);
    Thread thread = new Thread(testPlaybackRunnable);
    thread.start();
    thread.join();
    if (testPlaybackRunnable.playbackException != null) {
      throw testPlaybackRunnable.playbackException;
    }

    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        audioSink,
        "audiosinkdumps/" + fileName + ".audiosink.dump");
  }

  private static class TestPlaybackRunnable implements Player.Listener, Runnable {

    private final Context context;
    private final Uri uri;
    private final AudioSink audioSink;

    @Nullable private SimpleExoPlayer player;
    @Nullable private ExoPlaybackException playbackException;

    public TestPlaybackRunnable(Uri uri, Context context, AudioSink audioSink) {
      this.uri = uri;
      this.context = context;
      this.audioSink = audioSink;
    }

    @Override
    public void run() {
      Looper.prepare();
      RenderersFactory renderersFactory =
          (eventHandler,
              videoRendererEventListener,
              audioRendererEventListener,
              textRendererOutput,
              metadataRendererOutput) ->
              new Renderer[] {
                new LibflacAudioRenderer(eventHandler, audioRendererEventListener, audioSink)
              };
      player = new SimpleExoPlayer.Builder(context, renderersFactory).build();
      player.addListener(this);
      MediaSource mediaSource =
          new ProgressiveMediaSource.Factory(
                  new DefaultDataSourceFactory(context), MatroskaExtractor.FACTORY)
              .createMediaSource(MediaItem.fromUri(uri));
      player.setMediaSource(mediaSource);
      player.prepare();
      player.play();
      Looper.loop();
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
      playbackException = error;
    }

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
      if (playbackState == Player.STATE_ENDED
          || (playbackState == Player.STATE_IDLE && playbackException != null)) {
        player.release();
        Looper.myLooper().quit();
      }
    }
  }
}
